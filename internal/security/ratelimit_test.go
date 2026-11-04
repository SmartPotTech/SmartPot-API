package security

import (
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/cache"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/httpserver"
	"github.com/labstack/echo/v4"
)

func discardLogger() *slog.Logger {
	return slog.New(slog.NewTextHandler(io.Discard, nil))
}

func TestRateLimiter(t *testing.T) {
	rl := NewRateLimiter(cache.NewStore(nil), 3, 2)
	fixed := time.Now()
	rl.now = func() time.Time { return fixed }

	e := httpserver.New(discardLogger())
	e.Use(rl.Middleware())
	e.GET("/api/v1/ping", func(c echo.Context) error { return c.String(http.StatusOK, "ok") })
	e.POST("/api/v1/auth/login", func(c echo.Context) error { return c.String(http.StatusOK, "ok") })

	send := func(method, path string) *httptest.ResponseRecorder {
		rec := httptest.NewRecorder()
		req := httptest.NewRequest(method, path, nil)
		req.RemoteAddr = "203.0.113.5:4444"
		req.Header.Set("X-Forwarded-For", "203.0.113.5")
		e.ServeHTTP(rec, req)
		return rec
	}

	// Cupo general = 3: la cuarta petición se rechaza.
	for i := 1; i <= 3; i++ {
		if rec := send(http.MethodGet, "/api/v1/ping"); rec.Code != http.StatusOK {
			t.Fatalf("request %d: code %d, expected 200", i, rec.Code)
		}
	}
	rec := send(http.MethodGet, "/api/v1/ping")
	if rec.Code != http.StatusTooManyRequests {
		t.Fatalf("fourth request: code %d, expected 429", rec.Code)
	}
	if rec.Header().Get("Retry-After") == "" {
		t.Error("missing Retry-After header")
	}
	if !strings.Contains(rec.Body.String(), "Too many requests") {
		t.Errorf("unexpected body: %s", rec.Body.String())
	}

	// Cupo de autenticación = 2: la tercera se rechaza (llave independiente).
	for i := 1; i <= 2; i++ {
		if rec := send(http.MethodPost, "/api/v1/auth/login"); rec.Code != http.StatusOK {
			t.Fatalf("auth %d: code %d, expected 200", i, rec.Code)
		}
	}
	if rec := send(http.MethodPost, "/api/v1/auth/login"); rec.Code != http.StatusTooManyRequests {
		t.Errorf("third auth: code %d, expected 429", rec.Code)
	}
}

func TestRateLimiter_SpoofedXForwardedForMitigation(t *testing.T) {
	// Cupo de autenticación = 2
	rl := NewRateLimiter(cache.NewStore(nil), 5, 2)
	fixed := time.Now()
	rl.now = func() time.Time { return fixed }

	e := httpserver.New(discardLogger())
	e.Use(rl.Middleware())
	e.POST("/api/v1/auth/login", func(c echo.Context) error { return c.String(http.StatusOK, "ok") })

	// El atacante realiza peticiones desde la misma conexión (RemoteAddr idéntica) pero
	// falsifica cabeceras X-Forwarded-For y X-Real-IP con direcciones IP distintas en cada intento.
	spoofedIPs := []string{
		"198.51.100.1",
		"198.51.100.2",
		"198.51.100.3",
	}

	for i, spoofedIP := range spoofedIPs {
		rec := httptest.NewRecorder()
		req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/login", nil)
		req.RemoteAddr = "203.0.113.50:54321" // Dirección real de la conexión
		req.Header.Set("X-Forwarded-For", spoofedIP)
		req.Header.Set("X-Real-IP", spoofedIP)
		e.ServeHTTP(rec, req)

		if i < 2 {
			if rec.Code != http.StatusOK {
				t.Fatalf("attempt %d with X-Forwarded-For %s: code %d, expected 200", i+1, spoofedIP, rec.Code)
			}
		} else {
			// El 3er intento debe ser bloqueado con 429 a pesar de la IP falsificada
			if rec.Code != http.StatusTooManyRequests {
				t.Fatalf("attempt %d with X-Forwarded-For %s was not blocked: code %d, expected 429 Too Many Requests (CVE-2026-118653)", i+1, spoofedIP, rec.Code)
			}
		}
	}
}
