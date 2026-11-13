// Package health expone /health para balanceadores y despliegues. Solo la base
// de datos hace fallar el chequeo (503); sin broker, caché, IA o simulador la
// API sigue atendiendo con funciones reducidas.
package health

import (
	"net/http"
	"sync"
	"time"

	"github.com/labstack/echo/v4"
)

// Checker reúne las sondas de cada dependencia. Los componentes que todavía no
// existen se inyectan como nil y se informan como DOWN o DISABLED.
type Checker struct {
	Database  func() bool // true = UP (controla el 503)
	Broker    func() (enabled, up bool)
	Cache     func() bool // true = UP
	AI        func() (enabled, healthy bool)
	Simulator func() (available, healthy bool)
	// CacheTTL evita que una ráfaga pública dispare sondas pesadas en cada
	// llamada; 5 s por defecto.
	CacheTTL time.Duration
}

type response struct {
	Status    string `json:"status"`
	Database  string `json:"database"`
	Broker    string `json:"broker"`
	Cache     string `json:"cache"`
	AI        string `json:"ai"`
	Simulator string `json:"simulator"`
}

// Handler devuelve el manejador Echo de /health con el resultado cacheado.
func (h Checker) Handler() echo.HandlerFunc {
	ttl := h.CacheTTL
	if ttl <= 0 {
		ttl = 5 * time.Second
	}
	var mu sync.Mutex
	var cached response
	var cachedStatus int
	var cachedAt time.Time

	return func(c echo.Context) error {
		mu.Lock()
		defer mu.Unlock()
		if !cachedAt.IsZero() && time.Since(cachedAt) < ttl {
			return c.JSON(cachedStatus, cached)
		}

		var (
			database bool
			broker   string
			cache    string
			ai       string
			sim      string
			wg       sync.WaitGroup
		)
		wg.Add(5)
		go func() { defer wg.Done(); database = call(h.Database) }()
		go func() { defer wg.Done(); broker = gated(h.Broker) }()
		go func() { defer wg.Done(); cache = upDown(call(h.Cache)) }()
		go func() { defer wg.Done(); ai = gated(h.AI) }()
		go func() { defer wg.Done(); sim = gated(h.Simulator) }()
		wg.Wait()

		body := response{
			Status:    upDown(database),
			Database:  upDown(database),
			Broker:    broker,
			Cache:     cache,
			AI:        ai,
			Simulator: sim,
		}

		status := http.StatusOK
		if !database {
			status = http.StatusServiceUnavailable
		}
		cached, cachedStatus, cachedAt = body, status, time.Now()
		return c.JSON(status, body)
	}
}

func call(probe func() bool) bool {
	return probe != nil && probe()
}

// Gated resuelve el estado de un componente opcional: DISABLED si no está
// habilitado/disponible, sino UP o DOWN según su salud.
func gated(probe func() (bool, bool)) string {
	if probe == nil {
		return "DISABLED"
	}
	enabled, up := probe()
	if !enabled {
		return "DISABLED"
	}
	return upDown(up)
}

func upDown(up bool) string {
	if up {
		return "UP"
	}
	return "DOWN"
}
