package httpserver

import (
	"bytes"
	"context"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/validation"
	"github.com/labstack/echo/v4"
)

func testLogger() *slog.Logger {
	return slog.New(slog.NewTextHandler(io.Discard, nil))
}

type testPayload struct {
	Name string `json:"name"`
}

func (p *testPayload) Validate() validation.Fields {
	return validation.New().NotBlank("name", p.Name, "name cannot be empty").Result()
}

func TestHTTPServer_NotFound(t *testing.T) {
	e := New(testLogger())
	req := httptest.NewRequest(http.MethodGet, "/ruta-inexistente", nil)
	rec := httptest.NewRecorder()
	e.ServeHTTP(rec, req)

	if rec.Code != http.StatusNotFound {
		t.Fatalf("se esperaba status 404, se obtuvo %d", rec.Code)
	}
	var resp apperr.Response
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatalf("error unmarshaling JSON response: %v", err)
	}
	if resp.Status != http.StatusNotFound {
		t.Errorf("status esperado 404, obtenido %d", resp.Status)
	}
	if resp.Message != "The requested route does not exist" {
		t.Errorf("unexpected message: %s", resp.Message)
	}
}

func TestHTTPServer_MethodNotAllowed(t *testing.T) {
	e := New(testLogger())
	e.GET("/ping", func(c echo.Context) error {
		return c.String(http.StatusOK, "pong")
	})

	req := httptest.NewRequest(http.MethodPost, "/ping", nil)
	rec := httptest.NewRecorder()
	e.ServeHTTP(rec, req)

	if rec.Code != http.StatusMethodNotAllowed {
		t.Fatalf("se esperaba status 405, se obtuvo %d", rec.Code)
	}
	var resp apperr.Response
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatalf("error unmarshaling JSON response: %v", err)
	}
	if resp.Status != http.StatusMethodNotAllowed {
		t.Errorf("status esperado 405, obtenido %d", resp.Status)
	}
	if resp.Message != "Method not allowed for this route" {
		t.Errorf("unexpected message: %s", resp.Message)
	}
}

func TestHTTPServer_SecurityHeaders(t *testing.T) {
	e := New(testLogger())
	e.GET("/secure", func(c echo.Context) error {
		return c.NoContent(http.StatusOK)
	})

	req := httptest.NewRequest(http.MethodGet, "/secure", nil)
	rec := httptest.NewRecorder()
	e.ServeHTTP(rec, req)

	if rec.Header().Get("Referrer-Policy") != "no-referrer" {
		t.Errorf("unexpected Referrer-Policy: %s", rec.Header().Get("Referrer-Policy"))
	}
	if rec.Header().Get("X-Frame-Options") != "DENY" {
		t.Errorf("unexpected X-Frame-Options: %s", rec.Header().Get("X-Frame-Options"))
	}
	if rec.Header().Get("X-Content-Type-Options") != "nosniff" {
		t.Errorf("unexpected X-Content-Type-Options: %s", rec.Header().Get("X-Content-Type-Options"))
	}
}

func TestHTTPServer_Recovery(t *testing.T) {
	e := New(testLogger())
	e.GET("/panic", func(c echo.Context) error {
		panic("error inesperado")
	})

	req := httptest.NewRequest(http.MethodGet, "/panic", nil)
	rec := httptest.NewRecorder()
	e.ServeHTTP(rec, req)

	if rec.Code != http.StatusInternalServerError {
		t.Fatalf("expected status 500, got %d", rec.Code)
	}
	var resp apperr.Response
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatalf("error unmarshaling JSON response: %v", err)
	}
	if !strings.Contains(resp.Message, "unexpected error") {
		t.Errorf("unexpected message: %s", resp.Message)
	}
}

func TestHTTPServer_ClientCancellationIsNotA500(t *testing.T) {
	e := New(testLogger())
	e.GET("/cancel", func(c echo.Context) error { return context.Canceled })

	req := httptest.NewRequest(http.MethodGet, "/cancel", nil)
	rec := httptest.NewRecorder()
	e.ServeHTTP(rec, req)

	if rec.Code != http.StatusNoContent {
		t.Fatalf("expected status 204 for a canceled client, got %d", rec.Code)
	}
}

func TestHTTPServer_DeadlineExceededIsA503(t *testing.T) {
	e := New(testLogger())
	e.GET("/deadline", func(c echo.Context) error { return context.DeadlineExceeded })

	req := httptest.NewRequest(http.MethodGet, "/deadline", nil)
	rec := httptest.NewRecorder()
	e.ServeHTTP(rec, req)

	if rec.Code != http.StatusServiceUnavailable {
		t.Fatalf("expected status 503 for an exhausted deadline, got %d", rec.Code)
	}
	var resp apperr.Response
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatalf("error unmarshaling JSON response: %v", err)
	}
	if !strings.Contains(resp.Message, "took too long") {
		t.Errorf("unexpected message: %s", resp.Message)
	}
}

func TestHTTPServer_BindAndExchangeableValidator(t *testing.T) {
	e := New(testLogger())
	e.POST("/bind", func(c echo.Context) error {
		var payload testPayload
		if err := Bind(c, &payload); err != nil {
			return err
		}
		return c.JSON(http.StatusOK, payload)
	})

	// JSON inválido
	reqBadJSON := httptest.NewRequest(http.MethodPost, "/bind", bytes.NewBufferString("{invalid"))
	recBadJSON := httptest.NewRecorder()
	e.ServeHTTP(recBadJSON, reqBadJSON)
	if recBadJSON.Code != http.StatusBadRequest {
		t.Fatalf("expected status 400 for invalid JSON, got %d", recBadJSON.Code)
	}

	// Validación fallida vía echo.Validator + HTTPErrorHandler centralizado
	reqInvalid := httptest.NewRequest(http.MethodPost, "/bind", bytes.NewBufferString(`{"name":""}`))
	recInvalid := httptest.NewRecorder()
	e.ServeHTTP(recInvalid, reqInvalid)
	if recInvalid.Code != http.StatusBadRequest {
		t.Fatalf("expected status 400 for failed validation, got %d", recInvalid.Code)
	}
	var valResp apperr.Response
	_ = json.Unmarshal(recInvalid.Body.Bytes(), &valResp)
	if valResp.Fields["name"] == "" {
		t.Errorf("expected field 'name' in validation errors")
	}

	// Éxito
	reqValid := httptest.NewRequest(http.MethodPost, "/bind", bytes.NewBufferString(`{"name":"test"}`))
	recValid := httptest.NewRecorder()
	e.ServeHTTP(recValid, reqValid)
	if recValid.Code != http.StatusOK {
		t.Fatalf("expected status 200, got %d", recValid.Code)
	}
}

func TestHTTPServer_TypedRequestContext(t *testing.T) {
	e := New(testLogger())
	exp := time.Now().Add(time.Hour).UTC().Truncate(time.Second)

	e.GET("/ctx", func(c echo.Context) error {
		rc := FromContext(c)
		rc.SetIdentity(Identity{
			UserID:    "usr-001",
			Email:     "demo@smartpot.app",
			Role:      "ADMIN",
			ExpiresAt: exp,
		})
		if !rc.Identity().Authenticated || rc.UserID() != "usr-001" || rc.Email() != "demo@smartpot.app" || rc.Role() != "ADMIN" {
			t.Fatalf("invalid typed identity: %+v", rc.Identity())
		}
		if rc.Trace().RequestID != "trace-abc-123" || rc.Trace().StartedAt.IsZero() {
			t.Fatalf("invalid telemetry trace: %+v", rc.Trace())
		}
		return c.NoContent(http.StatusOK)
	})

	req := httptest.NewRequest(http.MethodGet, "/ctx", nil)
	req.Header.Set(echo.HeaderXRequestID, "trace-abc-123")
	rec := httptest.NewRecorder()
	e.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("se esperaba 200, se obtuvo %d", rec.Code)
	}
}

func TestHTTPServer_RadixZeroAllocation(t *testing.T) {
	e := echo.New()
	var capturedCrop, capturedActuator string
	e.GET("/api/v1/crops/:cropId/actuators/:actuatorId", func(c echo.Context) error {
		capturedCrop = c.Param("cropId")
		capturedActuator = c.Param("actuatorId")
		return nil
	})

	req := httptest.NewRequest(http.MethodGet, "/api/v1/crops/66f5a1000000000000000101/actuators/66f5a1000000000000000201", nil)
	rec := httptest.NewRecorder()

	allocs := testing.AllocsPerRun(100, func() {
		e.ServeHTTP(rec, req)
	})
	if allocs != 0 {
		t.Fatalf("se esperaban 0 asignaciones dinámicas en el árbol Radix, se obtuvieron %v", allocs)
	}
	if capturedCrop != "66f5a1000000000000000101" || capturedActuator != "66f5a1000000000000000201" {
		t.Fatalf("misextracted parameters: crop=%q actuator=%q", capturedCrop, capturedActuator)
	}
}

func TestHTTPServer_ETagConditionalGET(t *testing.T) {
	e := New(testLogger())
	e.GET("/api/v1/crops", func(c echo.Context) error {
		return c.JSON(http.StatusOK, map[string]string{"name": "Albahaca"})
	})

	req1 := httptest.NewRequest(http.MethodGet, "/api/v1/crops", nil)
	rec1 := httptest.NewRecorder()
	e.ServeHTTP(rec1, req1)

	if rec1.Code != http.StatusOK {
		t.Fatalf("se esperaba 200 en la primera lectura, se obtuvo %d", rec1.Code)
	}
	etag := rec1.Header().Get("ETag")
	if !strings.HasPrefix(etag, `W/"`) {
		t.Fatalf("se esperaba cabecera ETag débil, se obtuvo %q", etag)
	}
	if rec1.Header().Get("Cache-Control") != "private, no-cache" {
		t.Errorf("Cache-Control inesperado: %q", rec1.Header().Get("Cache-Control"))
	}

	req2 := httptest.NewRequest(http.MethodGet, "/api/v1/crops", nil)
	req2.Header.Set("If-None-Match", etag)
	rec2 := httptest.NewRecorder()
	e.ServeHTTP(rec2, req2)

	if rec2.Code != http.StatusNotModified {
		t.Fatalf("se esperaba 304 Not Modified con If-None-Match coincidente, se obtuvo %d", rec2.Code)
	}
	if rec2.Body.Len() != 0 {
		t.Errorf("se esperaba cuerpo vacío en 304, se obtuvieron %d bytes", rec2.Body.Len())
	}
	if rec2.Header().Get("ETag") != etag {
		t.Errorf("se esperaba conservar ETag %q en 304, se obtuvo %q", etag, rec2.Header().Get("ETag"))
	}
}

func TestHTTPServer_EventStreamSkipsETagBuffer(t *testing.T) {
	e := New(testLogger())
	e.GET("/api/v1/crops/:cropId/stream", func(c echo.Context) error {
		c.Response().Header().Set(echo.HeaderContentType, "text/event-stream")
		c.Response().WriteHeader(http.StatusOK)
		if _, err := c.Response().Write([]byte("data: primera\n\n")); err != nil {
			return err
		}
		return http.NewResponseController(c.Response().Writer).Flush()
	})
	srv := httptest.NewServer(e)
	defer srv.Close()

	req, _ := http.NewRequest(http.MethodGet, srv.URL+"/api/v1/crops/abc/stream", nil)
	req.Header.Set(echo.HeaderAccept, "text/event-stream")
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("no se pudo abrir el flujo: %v", err)
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)
	if !strings.Contains(string(body), "data: primera") {
		t.Fatalf("el flujo no llegó al cliente: %q", body)
	}
	if resp.Header.Get("ETag") != "" {
		t.Fatal("event stream must not have ETag")
	}
}

func TestHTTPServer_ClientIPBehindTrustedProxy(t *testing.T) {
	cases := []struct {
		name   string
		opts   Options
		remote string
		xff    string
		want   string
	}{
		{"without proxy header is ignored", Options{}, "10.0.0.2:5000", "1.2.3.4", "10.0.0.2"},
		{"trusted private proxy", Options{TrustProxy: true}, "10.0.0.2:5000", "1.2.3.4", "1.2.3.4"},
		{"untrusted public peer", Options{TrustProxy: true}, "8.8.8.8:5000", "1.2.3.4", "8.8.8.8"},
		{"explicit range", Options{TrustProxy: true, TrustedProxies: []string{"172.18.0.0/16"}}, "172.18.0.5:80", "5.6.7.8", "5.6.7.8"},
		{"outside explicit range", Options{TrustProxy: true, TrustedProxies: []string{"172.18.0.0/16"}}, "10.0.0.2:80", "5.6.7.8", "10.0.0.2"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			e := New(testLogger(), tc.opts)
			req := httptest.NewRequest(http.MethodGet, "/", nil)
			req.RemoteAddr = tc.remote
			req.Header.Set(echo.HeaderXForwardedFor, tc.xff)
			c := e.NewContext(req, httptest.NewRecorder())
			if got := c.RealIP(); got != tc.want {
				t.Fatalf("IP del cliente %q, se esperaba %q", got, tc.want)
			}
		})
	}
}

// Echo ≤ 4.16.2 confía por defecto en X-Forwarded-For y X-Real-IP (GHSA-246p-cpwv-v3jq y GHSA-99jh-6h7p-pp36).
// La API fija su propio IPExtractor: ninguna de las dos cabeceras falsas cambia la IP que usa el límite de tasa.
func TestHTTPServer_SpoofedForwardingHeadersAreIgnored(t *testing.T) {
	cases := []struct {
		name   string
		opts   Options
		remote string
		want   string
	}{
		{"without proxy", Options{}, "203.0.113.9:4000", "203.0.113.9"},
		{"proxy enabled but public peer", Options{TrustProxy: true}, "203.0.113.9:4000", "203.0.113.9"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			e := New(testLogger(), tc.opts)
			req := httptest.NewRequest(http.MethodGet, "/", nil)
			req.RemoteAddr = tc.remote
			req.Header.Set(echo.HeaderXForwardedFor, "1.2.3.4")
			req.Header.Set(echo.HeaderXRealIP, "5.6.7.8")
			c := e.NewContext(req, httptest.NewRecorder())
			if got := c.RealIP(); got != tc.want {
				t.Fatalf("IP del cliente %q, se esperaba %q", got, tc.want)
			}
		})
	}
}
