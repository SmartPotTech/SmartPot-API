package httpserver

import (
	"bytes"
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
	return validation.New().NotBlank("name", p.Name, "el nombre no puede estar vacío").Result()
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
		t.Fatalf("error deserializando respuesta JSON: %v", err)
	}
	if resp.Status != http.StatusNotFound {
		t.Errorf("status esperado 404, obtenido %d", resp.Status)
	}
	if resp.Message != "La ruta solicitada no existe" {
		t.Errorf("mensaje inesperado: %s", resp.Message)
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
		t.Fatalf("error deserializando respuesta JSON: %v", err)
	}
	if resp.Status != http.StatusMethodNotAllowed {
		t.Errorf("status esperado 405, obtenido %d", resp.Status)
	}
	if resp.Message != "Método no permitido para esta ruta" {
		t.Errorf("mensaje inesperado: %s", resp.Message)
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
		t.Errorf("Referrer-Policy incorrecta: %s", rec.Header().Get("Referrer-Policy"))
	}
	if rec.Header().Get("X-Frame-Options") != "DENY" {
		t.Errorf("X-Frame-Options incorrecto: %s", rec.Header().Get("X-Frame-Options"))
	}
	if rec.Header().Get("X-Content-Type-Options") != "nosniff" {
		t.Errorf("X-Content-Type-Options incorrecto: %s", rec.Header().Get("X-Content-Type-Options"))
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
		t.Fatalf("se esperaba status 500, se obtuvo %d", rec.Code)
	}
	var resp apperr.Response
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatalf("error deserializando respuesta JSON: %v", err)
	}
	if !strings.Contains(resp.Message, "error inesperado") {
		t.Errorf("mensaje inesperado: %s", resp.Message)
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
		t.Fatalf("se esperaba status 400 para JSON inválido, se obtuvo %d", recBadJSON.Code)
	}

	// Validación fallida vía echo.Validator + HTTPErrorHandler centralizado
	reqInvalid := httptest.NewRequest(http.MethodPost, "/bind", bytes.NewBufferString(`{"name":""}`))
	recInvalid := httptest.NewRecorder()
	e.ServeHTTP(recInvalid, reqInvalid)
	if recInvalid.Code != http.StatusBadRequest {
		t.Fatalf("se esperaba status 400 para validación fallida, se obtuvo %d", recInvalid.Code)
	}
	var valResp apperr.Response
	_ = json.Unmarshal(recInvalid.Body.Bytes(), &valResp)
	if valResp.Fields["name"] == "" {
		t.Errorf("se esperaba campo 'name' en errores de validación")
	}

	// Éxito
	reqValid := httptest.NewRequest(http.MethodPost, "/bind", bytes.NewBufferString(`{"name":"test"}`))
	recValid := httptest.NewRecorder()
	e.ServeHTTP(recValid, reqValid)
	if recValid.Code != http.StatusOK {
		t.Fatalf("se esperaba status 200, se obtuvo %d", recValid.Code)
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
			t.Fatalf("identidad tipada inválida: %+v", rc.Identity())
		}
		if rc.Trace().RequestID != "trace-abc-123" || rc.Trace().StartedAt.IsZero() {
			t.Fatalf("traza de telemetría inválida: %+v", rc.Trace())
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
		t.Fatalf("parámetros mal extraídos: crop=%q actuator=%q", capturedCrop, capturedActuator)
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
		t.Fatal("un flujo de eventos no debe llevar ETag")
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
		{"sin proxy se ignora la cabecera", Options{}, "10.0.0.2:5000", "1.2.3.4", "10.0.0.2"},
		{"proxy privado de confianza", Options{TrustProxy: true}, "10.0.0.2:5000", "1.2.3.4", "1.2.3.4"},
		{"par público no es de confianza", Options{TrustProxy: true}, "8.8.8.8:5000", "1.2.3.4", "8.8.8.8"},
		{"rango explícito", Options{TrustProxy: true, TrustedProxies: []string{"172.18.0.0/16"}}, "172.18.0.5:80", "5.6.7.8", "5.6.7.8"},
		{"fuera del rango explícito", Options{TrustProxy: true, TrustedProxies: []string{"172.18.0.0/16"}}, "10.0.0.2:80", "5.6.7.8", "10.0.0.2"},
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
