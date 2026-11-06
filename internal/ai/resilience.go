package ai

import (
	"context"
	"crypto/rand"
	"encoding/binary"
	"errors"
	"fmt"
	"net"
	"net/http"
	"sync"
	"sync/atomic"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
)

// CircuitState representa los estados posibles del interruptor de circuito.
type CircuitState int32

const (
	StateClosed CircuitState = iota
	StateHalfOpen
	StateOpen
)

func (s CircuitState) String() string {
	switch s {
	case StateClosed:
		return "CLOSED"
	case StateHalfOpen:
		return "HALF-OPEN"
	case StateOpen:
		return "OPEN"
	default:
		return "UNKNOWN"
	}
}

// CircuitBreaker protege la comunicación hacia SmartPot-AI contra fallos en cascada.
type CircuitBreaker struct {
	mu           sync.Mutex
	state        CircuitState
	failures     int
	threshold    int
	cooldown     time.Duration
	lastFailure  time.Time
	halfOpenBusy atomic.Bool
	clock        func() time.Time
}

// NewCircuitBreaker crea un CircuitBreaker con umbral de fallos consecutivos y tiempo de enfriamiento.
func NewCircuitBreaker(threshold int, cooldown time.Duration, clock func() time.Time) *CircuitBreaker {
	if threshold <= 0 {
		threshold = 5
	}
	if cooldown <= 0 {
		cooldown = 10 * time.Second
	}
	if clock == nil {
		clock = time.Now
	}
	return &CircuitBreaker{
		threshold: threshold,
		cooldown:  cooldown,
		clock:     clock,
	}
}

// State devuelve el estado actual del circuito.
func (cb *CircuitBreaker) State() CircuitState {
	cb.mu.Lock()
	defer cb.mu.Unlock()
	cb.checkState(cb.clock())
	return cb.state
}

func (cb *CircuitBreaker) checkState(now time.Time) {
	if cb.state == StateOpen && now.After(cb.lastFailure.Add(cb.cooldown)) {
		cb.state = StateHalfOpen
		cb.halfOpenBusy.Store(false)
	}
}

// Execute ejecuta la función fn si el circuito lo permite; si está abierto, falla rápido.
func (cb *CircuitBreaker) Execute(fn func() error) error {
	now := cb.clock()
	cb.mu.Lock()
	cb.checkState(now)

	if cb.state == StateOpen {
		cb.mu.Unlock()
		return apperr.Unavailable(unavailableMsg)
	}

	if cb.state == StateHalfOpen {
		if cb.halfOpenBusy.Swap(true) {
			cb.mu.Unlock()
			return apperr.Unavailable(unavailableMsg)
		}
	}
	cb.mu.Unlock()

	err := fn()

	cb.mu.Lock()
	defer cb.mu.Unlock()
	now = cb.clock()

	if err != nil {
		// Un 4xx es culpa del cliente, no una caída del transporte: se propaga
		// sin abrir el circuito y, en HALF-OPEN, confirma que la IA está viva.
		var apiErr *apperr.Error
		if errors.As(err, &apiErr) && apiErr.Status >= 400 && apiErr.Status < 500 {
			if cb.state == StateHalfOpen {
				cb.state = StateClosed
				cb.failures = 0
			}
			cb.halfOpenBusy.Store(false)
			return err
		}
		cb.failures++
		cb.lastFailure = now
		cb.halfOpenBusy.Store(false)
		// El circuito abre exactamente al alcanzar el umbral (5 fallos
		// consecutivos por defecto), no con el primero. En HALF-OPEN un fallo
		// vuelve a abrir de inmediato.
		if cb.state == StateHalfOpen || cb.failures >= cb.threshold {
			cb.state = StateOpen
		}
		return err
	}

	// Éxito: restablece el circuito
	cb.failures = 0
	cb.state = StateClosed
	cb.halfOpenBusy.Store(false)
	return nil
}

// RetryWithBackoff ejecuta una operación con reintentos exponenciales y jitter aleatorio ante fallos transitorios.
func RetryWithBackoff(ctx context.Context, attempts int, baseDelay time.Duration, isRetryable func(error) bool, fn func() error) error {
	if attempts <= 1 {
		return fn()
	}

	var err error
	for i := 0; i < attempts; i++ {
		err = fn()
		if err == nil {
			return nil
		}
		if !isRetryable(err) || i == attempts-1 {
			return err
		}

		// Backoff exponencial con jitter: base * 2^i + jitter
		backoff := baseDelay * (1 << i)
		jitter := time.Duration(secureRandomInt64(int64(baseDelay / 2)))
		sleepDuration := backoff + jitter

		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-time.After(sleepDuration):
		}
	}
	return err
}

func isTransientError(err error) bool {
	if err == nil {
		return false
	}
	var netErr net.Error
	if errors.As(err, &netErr) && (netErr.Timeout() || errors.Is(err, context.DeadlineExceeded)) {
		return true
	}
	var appErr *apperr.Error
	if errors.As(err, &appErr) {
		return appErr.Status == http.StatusBadGateway ||
			appErr.Status == http.StatusServiceUnavailable ||
			appErr.Status == http.StatusGatewayTimeout
	}
	return false
}

func secureRandomInt64(max int64) int64 {
	if max <= 0 {
		return 0
	}
	var b [8]byte
	_, _ = rand.Read(b[:])
	n := int64(binary.BigEndian.Uint64(b[:]))
	if n < 0 {
		n = -n
	}
	return n % max
}

type singleflightCall struct {
	wg  sync.WaitGroup
	val any
	err error
}

// Singleflight suprime llamadas concurrentes duplicadas para la misma clave, evitando Cache Stampede.
type Singleflight struct {
	mu sync.Mutex
	m  map[string]*singleflightCall
}

// NewSingleflight crea un nuevo grupo Singleflight.
func NewSingleflight() *Singleflight {
	return &Singleflight{m: make(map[string]*singleflightCall)}
}

// Do ejecuta fn únicamente una vez para una clave dada mientras esté en curso.
// La limpieza va en defer: un pánico en fn no deja el WaitGroup ni la entrada
// del mapa bloqueados para siempre.
func (g *Singleflight) Do(key string, fn func() (any, error)) (any, error) {
	g.mu.Lock()
	if c, ok := g.m[key]; ok {
		g.mu.Unlock()
		c.wg.Wait()
		return c.val, c.err
	}
	c := new(singleflightCall)
	c.wg.Add(1)
	g.m[key] = c
	g.mu.Unlock()

	func() {
		defer func() {
			if r := recover(); r != nil {
				c.err = fmt.Errorf("singleflight panic: %v", r)
			}
			c.wg.Done()
			g.mu.Lock()
			delete(g.m, key)
			g.mu.Unlock()
		}()
		c.val, c.err = fn()
	}()
	return c.val, c.err
}
