package cache

import (
	"context"
	"errors"
	"strconv"
	"testing"
	"time"
)

// newLocalStore crea un Store solo-memoria con reloj controlable para las pruebas.
func newLocalStore() (*Store, func(time.Duration)) {
	now := time.Now()
	s := NewStore(nil)
	s.now = func() time.Time { return now }
	advance := func(d time.Duration) { now = now.Add(d) }
	return s, advance
}

func TestLocalIncrement(t *testing.T) {
	s, advance := newLocalStore()
	for want := int64(1); want <= 3; want++ {
		if got := s.Increment("c", time.Minute); got != want {
			t.Fatalf("Increment = %d, expected %d", got, want)
		}
	}
	advance(time.Minute) // vence la ventana
	if got := s.Increment("c", time.Minute); got != 1 {
		t.Errorf("after expiration, Increment = %d, expected 1", got)
	}
}

func TestLocalSetIfAbsent(t *testing.T) {
	s, advance := newLocalStore()
	if !s.SetIfAbsent("lock", time.Minute) {
		t.Fatal("first time should set key")
	}
	if s.SetIfAbsent("lock", time.Minute) {
		t.Error("second time should not set key")
	}
	advance(time.Minute)
	if !s.SetIfAbsent("lock", time.Minute) {
		t.Error("after expiration should set key again")
	}
}

func TestLocalPutGetTake(t *testing.T) {
	s, advance := newLocalStore()
	if _, ok := s.Get("k"); ok {
		t.Error("non-existent key should not exist")
	}
	s.Put("k", "v", time.Minute)
	if v, ok := s.Get("k"); !ok || v != "v" {
		t.Errorf("Get = %q, %v", v, ok)
	}
	if v, ok := s.Take("k"); !ok || v != "v" {
		t.Errorf("Take = %q, %v", v, ok)
	}
	if _, ok := s.Get("k"); ok {
		t.Error("Take should delete key")
	}

	s.Put("exp", "v", time.Minute)
	advance(time.Minute)
	if _, ok := s.Get("exp"); ok {
		t.Error("expired key should not be read")
	}
}

func TestPurgeExpired(t *testing.T) {
	s, advance := newLocalStore()
	s.Put("a", "1", time.Minute)
	s.Put("b", "2", 10*time.Minute)
	advance(2 * time.Minute)
	s.PurgeExpired()
	if _, ok := s.Get("a"); ok {
		t.Error("key a should be purged")
	}
	if _, ok := s.Get("b"); !ok {
		t.Error("b no debía purgarse")
	}
}

func TestAvailableNil(t *testing.T) {
	if NewStore(nil).Available() {
		t.Error("sin backend, Available debía ser false")
	}
}

// fakeBackend es un adaptador de prueba del puerto Backend: demuestra que el
// Store funciona contra cualquier backend que cumpla el puerto, sin conocerlo.
type fakeBackend struct {
	data map[string]string
	fail bool
}

var errDown = errors.New("backend caído")

func (f *fakeBackend) Incr(_ context.Context, key string, _ time.Duration) (int64, error) {
	if f.fail {
		return 0, errDown
	}
	n, _ := strconv.ParseInt(f.data[key], 10, 64)
	n++
	f.data[key] = strconv.FormatInt(n, 10)
	return n, nil
}

func (f *fakeBackend) SetNX(_ context.Context, key, value string, _ time.Duration) (bool, error) {
	if f.fail {
		return false, errDown
	}
	if _, ok := f.data[key]; ok {
		return false, nil
	}
	f.data[key] = value
	return true, nil
}

func (f *fakeBackend) Get(_ context.Context, key string) (string, error) {
	if f.fail {
		return "", errDown
	}
	if v, ok := f.data[key]; ok {
		return v, nil
	}
	return "", ErrMiss
}

func (f *fakeBackend) Set(_ context.Context, key, value string, _ time.Duration) error {
	if f.fail {
		return errDown
	}
	f.data[key] = value
	return nil
}

func (f *fakeBackend) GetDel(_ context.Context, key string) (string, error) {
	if f.fail {
		return "", errDown
	}
	v, ok := f.data[key]
	if !ok {
		return "", ErrMiss
	}
	delete(f.data, key)
	return v, nil
}

func (f *fakeBackend) Del(_ context.Context, keys ...string) error {
	if f.fail {
		return errDown
	}
	for _, k := range keys {
		delete(f.data, k)
	}
	return nil
}

func (f *fakeBackend) Ping(_ context.Context) error {
	if f.fail {
		return errDown
	}
	return nil
}

func (f *fakeBackend) Close() error { return nil }

func TestStoreUsesBackend(t *testing.T) {
	fb := &fakeBackend{data: map[string]string{}}
	s := NewStore(fb)

	s.Put("k", "v", time.Minute)
	if fb.data["smartpot:k"] != "v" {
		t.Errorf("Put debía escribir en el backend con prefijo, backend=%v", fb.data)
	}
	if v, ok := s.Get("k"); !ok || v != "v" {
		t.Errorf("Get desde el backend = %q, %v", v, ok)
	}
	if got := s.Increment("c", time.Minute); got != 1 {
		t.Errorf("Increment en backend = %d, se esperaba 1", got)
	}
	if !s.Available() {
		t.Error("con un backend sano, Available debía ser true")
	}
	if v, ok := s.Take("k"); !ok || v != "v" {
		t.Errorf("Take = %q, %v", v, ok)
	}
	if _, ok := s.Get("k"); ok {
		t.Error("Take debía borrar la llave del backend")
	}
	s.Put("del_me", "123", time.Minute)
	s.Delete("del_me")
	if _, ok := s.Get("del_me"); ok {
		t.Error("Delete debía borrar la llave")
	}
}

func TestStoreFallsBackToLocalOnBackendError(t *testing.T) {
	fb := &fakeBackend{data: map[string]string{}, fail: true}
	s := NewStore(fb)

	// El backend falla: el contador cae a memoria local y empieza en 1.
	if got := s.Increment("c", time.Minute); got != 1 {
		t.Errorf("con backend caído, Increment = %d, se esperaba 1 (local)", got)
	}
	if s.Available() {
		t.Error("con el backend caído, Available debía ser false")
	}
	// Put/Get siguen funcionando contra la memoria local.
	s.Put("k", "v", time.Minute)
	if v, ok := s.Get("k"); !ok || v != "v" {
		t.Errorf("respaldo local Get = %q, %v", v, ok)
	}
	if len(fb.data) != 0 {
		t.Errorf("nada debía quedar en el backend caído, backend=%v", fb.data)
	}
}
