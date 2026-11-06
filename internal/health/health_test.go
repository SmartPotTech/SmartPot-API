package health

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"

	"github.com/labstack/echo/v4"
)

func TestHealthChecker_UP(t *testing.T) {
	checker := Checker{
		Database:  func() bool { return true },
		Cache:     func() bool { return true },
		Broker:    func() (bool, bool) { return true, true },
		AI:        func() (bool, bool) { return true, true },
		Simulator: func() (bool, bool) { return true, true },
	}

	e := echo.New()
	e.GET("/health", checker.Handler())

	req := httptest.NewRequest(http.MethodGet, "/health", nil)
	rec := httptest.NewRecorder()
	e.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("se esperaba 200, obtenido %d", rec.Code)
	}
	var resp response
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatalf("error deserializando JSON: %v", err)
	}
	if resp.Status != "UP" || resp.Database != "UP" || resp.Cache != "UP" {
		t.Errorf("respuesta inesperada: %+v", resp)
	}
}

func TestHealthCheckerCachesProbesForBursts(t *testing.T) {
	var probes atomic.Int32
	checker := Checker{
		Database: func() bool { probes.Add(1); return true },
		CacheTTL: time.Minute,
	}
	e := echo.New()
	e.GET("/health", checker.Handler())

	for i := 0; i < 5; i++ {
		rec := httptest.NewRecorder()
		e.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/health", nil))
		if rec.Code != http.StatusOK {
			t.Fatalf("request %d: expected 200, got %d", i, rec.Code)
		}
	}
	if got := probes.Load(); got != 1 {
		t.Fatalf("a burst must reuse the cached probe; got %d probe calls", got)
	}
}

func TestHealthChecker_DatabaseDown(t *testing.T) {
	checker := Checker{
		Database: func() bool { return false },
	}

	e := echo.New()
	e.GET("/health", checker.Handler())

	req := httptest.NewRequest(http.MethodGet, "/health", nil)
	rec := httptest.NewRecorder()
	e.ServeHTTP(rec, req)

	if rec.Code != http.StatusServiceUnavailable {
		t.Fatalf("se esperaba 503 con base caída, obtenido %d", rec.Code)
	}
	var resp response
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatalf("error deserializando JSON: %v", err)
	}
	if resp.Status != "DOWN" || resp.Database != "DOWN" {
		t.Errorf("respuesta inesperada: %+v", resp)
	}
}
