// Package cache define el puerto de la caché (arquitectura hexagonal) y un
// almacén resiliente con respaldo en memoria local cuando el backend remoto no
// responde. El backend concreto (Valkey, u otro sistema de caché) vive detrás
// del puerto de salida Backend, así que cambiarlo es transparente para el resto
// de la aplicación, que solo depende de la interfaz Cache. Es equivalente al
// CacheStore de la versión en Java (prefijo "smartpot:").
package cache

import (
	"context"
	"errors"
	"log/slog"
	"strconv"
	"sync"
	"time"
)

const (
	keyPrefix      = "smartpot:"
	backendBackoff = 30 * time.Second
	opTimeout      = 2 * time.Second
)

// ErrMiss lo devuelve un Backend cuando la llave no existe (no es una falla).
var ErrMiss = errors.New("cache: key not found")

// Cache es el puerto de entrada: lo que la aplicación necesita de la caché. Los
// servicios dependen de esta interfaz, nunca de un sistema de caché concreto.
type Cache interface {
	Increment(key string, ttl time.Duration) int64
	SetIfAbsent(key string, ttl time.Duration) bool
	Get(key string) (string, bool)
	Put(key, value string, ttl time.Duration)
	Take(key string) (string, bool)
	Delete(keys ...string)
	Available() bool
}

// Backend es el puerto de salida hacia el sistema de caché remoto. Un adaptador
// (por ejemplo internal/cache/valkey) lo implementa; este paquete no conoce al
// proveedor. Devuelve ErrMiss cuando una llave no existe.
type Backend interface {
	Incr(ctx context.Context, key string, ttl time.Duration) (int64, error)
	SetNX(ctx context.Context, key, value string, ttl time.Duration) (bool, error)
	Get(ctx context.Context, key string) (string, error)
	Set(ctx context.Context, key, value string, ttl time.Duration) error
	GetDel(ctx context.Context, key string) (string, error)
	Del(ctx context.Context, keys ...string) error
	Ping(ctx context.Context) error
	Close() error
}

// Store implementa el puerto Cache sobre un Backend remoto con respaldo local.
// Si backend es nil, siempre usa memoria.
type Store struct {
	backend Backend
	now     func() time.Time
	mu      sync.Mutex
	local   map[string]localEntry
	retryAt time.Time
}

type localEntry struct {
	value     string
	expiresAt time.Time
}

func (e localEntry) isExpired(now time.Time) bool { return !now.Before(e.expiresAt) }

// NewStore crea el almacén sobre el backend dado. backend puede ser nil (solo
// memoria local), útil en pruebas.
func NewStore(backend Backend) *Store {
	return &Store{backend: backend, now: time.Now, local: make(map[string]localEntry)}
}

// Increment incrementa el contador y fija su TTL la primera vez.
func (s *Store) Increment(key string, ttl time.Duration) int64 {
	full := keyPrefix + key
	if s.backendReady() {
		ctx, cancel := context.WithTimeout(context.Background(), opTimeout)
		val, err := s.backend.Incr(ctx, full, ttl)
		cancel()
		if err == nil {
			return val
		}
		s.markDown(err)
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	now := s.now()
	if e, ok := s.local[full]; ok && !e.isExpired(now) {
		n, _ := strconv.ParseInt(e.value, 10, 64)
		n++
		s.local[full] = localEntry{strconv.FormatInt(n, 10), e.expiresAt}
		return n
	}
	s.local[full] = localEntry{"1", now.Add(ttl)}
	return 1
}

// SetIfAbsent fija la llave solo si no existe. Útil para enfriamientos.
func (s *Store) SetIfAbsent(key string, ttl time.Duration) bool {
	full := keyPrefix + key
	if s.backendReady() {
		ctx, cancel := context.WithTimeout(context.Background(), opTimeout)
		ok, err := s.backend.SetNX(ctx, full, "1", ttl)
		cancel()
		if err == nil {
			return ok
		}
		s.markDown(err)
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	now := s.now()
	if e, ok := s.local[full]; ok && !e.isExpired(now) {
		return false
	}
	s.local[full] = localEntry{"1", now.Add(ttl)}
	return true
}

// Get lee una llave.
func (s *Store) Get(key string) (string, bool) {
	full := keyPrefix + key
	if s.backendReady() {
		ctx, cancel := context.WithTimeout(context.Background(), opTimeout)
		val, err := s.backend.Get(ctx, full)
		cancel()
		if err == nil {
			return val, true
		}
		if errors.Is(err, ErrMiss) {
			return "", false
		}
		s.markDown(err)
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	if e, ok := s.local[full]; ok && !e.isExpired(s.now()) {
		return e.value, true
	}
	return "", false
}

// Put guarda una llave con su TTL.
func (s *Store) Put(key, value string, ttl time.Duration) {
	full := keyPrefix + key
	if s.backendReady() {
		ctx, cancel := context.WithTimeout(context.Background(), opTimeout)
		err := s.backend.Set(ctx, full, value, ttl)
		cancel()
		if err == nil {
			return
		}
		s.markDown(err)
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	s.local[full] = localEntry{value, s.now().Add(ttl)}
}

// Take lee y borra en una sola operación: sirve para códigos de un solo uso.
func (s *Store) Take(key string) (string, bool) {
	full := keyPrefix + key
	if s.backendReady() {
		ctx, cancel := context.WithTimeout(context.Background(), opTimeout)
		val, err := s.backend.GetDel(ctx, full)
		cancel()
		if err == nil {
			return val, true
		}
		if errors.Is(err, ErrMiss) {
			return "", false
		}
		s.markDown(err)
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	e, ok := s.local[full]
	if !ok {
		return "", false
	}
	delete(s.local, full)
	if e.isExpired(s.now()) {
		return "", false
	}
	return e.value, true
}

// Delete elimina una o más llaves de la caché remota y local.
func (s *Store) Delete(keys ...string) {
	if len(keys) == 0 {
		return
	}
	fullKeys := make([]string, len(keys))
	for i, k := range keys {
		fullKeys[i] = keyPrefix + k
	}
	if s.backendReady() {
		ctx, cancel := context.WithTimeout(context.Background(), opTimeout)
		err := s.backend.Del(ctx, fullKeys...)
		cancel()
		if err != nil {
			s.markDown(err)
		}
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	for _, k := range fullKeys {
		delete(s.local, k)
	}
}

// Available hace ping al backend y sincroniza el backoff con el resultado: un
// ping correcto rehabilita las operaciones de inmediato (antes /health decía UP
// mientras el Store seguía en backoff, o tardaba 30 s en volver), y un fallo
// mantiene el estado DOWN real.
func (s *Store) Available() bool {
	if s.backend == nil {
		return false
	}
	ctx, cancel := context.WithTimeout(context.Background(), opTimeout)
	defer cancel()
	if err := s.backend.Ping(ctx); err != nil {
		s.markDown(err)
		return false
	}
	s.mu.Lock()
	s.retryAt = time.Time{}
	s.mu.Unlock()
	return true
}

// PurgeExpired limpia las entradas locales vencidas; se llama periódicamente.
func (s *Store) PurgeExpired() {
	s.mu.Lock()
	defer s.mu.Unlock()
	now := s.now()
	for k, e := range s.local {
		if e.isExpired(now) {
			delete(s.local, k)
		}
	}
}

// Close cierra el backend si existe.
func (s *Store) Close() error {
	if s.backend == nil {
		return nil
	}
	return s.backend.Close()
}

func (s *Store) backendReady() bool {
	if s.backend == nil {
		return false
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	return !s.now().Before(s.retryAt)
}

func (s *Store) markDown(err error) {
	s.mu.Lock()
	alreadyDown := !s.now().Before(s.retryAt)
	s.retryAt = s.now().Add(backendBackoff)
	s.mu.Unlock()
	if alreadyDown {
		return
	}
	slog.Warn("cache backend is unavailable; using local memory", "backoff_s", int(backendBackoff.Seconds()), "err", err)
}
