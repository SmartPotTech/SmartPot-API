package httpserver

import (
	"compress/gzip"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/labstack/echo/v4"
)

// largePayload devuelve un JSON por encima del mínimo de compresión, parecido a una serie de lecturas.
func largePayload() []map[string]any {
	items := make([]map[string]any, 200)
	for i := range items {
		items[i] = map[string]any{"measuredAt": "2026-11-06T12:00:00Z", "temperature": 18.7, "ph": 5.98, "tds": 694}
	}
	return items
}

func compressionServer() *echo.Echo {
	e := New(testLogger())
	e.GET("/readings", func(c echo.Context) error { return c.JSON(http.StatusOK, largePayload()) })
	e.GET("/small", func(c echo.Context) error { return c.JSON(http.StatusOK, map[string]string{"status": "UP"}) })
	e.GET("/crops/1/stream", func(c echo.Context) error {
		c.Response().Header().Set(echo.HeaderContentType, "text/event-stream; charset=utf-8")
		c.Response().WriteHeader(http.StatusOK)
		_, err := c.Response().Write([]byte(strings.Repeat("event: reading\ndata: {}\n\n", 100)))
		return err
	})
	return e
}

func get(e *echo.Echo, path string, headers map[string]string) *httptest.ResponseRecorder {
	req := httptest.NewRequest(http.MethodGet, path, nil)
	for key, value := range headers {
		req.Header.Set(key, value)
	}
	rec := httptest.NewRecorder()
	e.ServeHTTP(rec, req)
	return rec
}

func TestCompression_GzipsLargeJSONAndKeepsTheETag(t *testing.T) {
	e := compressionServer()
	plain := get(e, "/readings", nil)
	if plain.Header().Get(echo.HeaderContentEncoding) != "" {
		t.Fatalf("sin Accept-Encoding no se debe comprimir")
	}

	rec := get(e, "/readings", map[string]string{echo.HeaderAcceptEncoding: "gzip, deflate, br"})
	if rec.Code != http.StatusOK || rec.Header().Get(echo.HeaderContentEncoding) != "gzip" {
		t.Fatalf("se esperaba 200 con gzip, se obtuvo %d %q", rec.Code, rec.Header().Get(echo.HeaderContentEncoding))
	}
	if !strings.Contains(rec.Header().Get(echo.HeaderVary), echo.HeaderAcceptEncoding) {
		t.Fatalf("falta Vary: Accept-Encoding")
	}
	reader, err := gzip.NewReader(rec.Body)
	if err != nil {
		t.Fatalf("el cuerpo no es gzip: %v", err)
	}
	body, _ := io.ReadAll(reader)
	if string(body) != plain.Body.String() {
		t.Fatalf("el cuerpo descomprimido no coincide con el original")
	}
	if rec.Body.Len() >= plain.Body.Len()/2 {
		t.Fatalf("la compresión no redujo el tamaño: %d de %d bytes", rec.Body.Len(), plain.Body.Len())
	}
	// El ETag se calcula sobre el JSON: el mismo con y sin compresión.
	etag := rec.Header().Get("ETag")
	if etag == "" || etag != plain.Header().Get("ETag") {
		t.Fatalf("ETag distinto con compresión: %q frente a %q", etag, plain.Header().Get("ETag"))
	}

	notModified := get(e, "/readings", map[string]string{echo.HeaderAcceptEncoding: "gzip", "If-None-Match": etag})
	if notModified.Code != http.StatusNotModified || notModified.Body.Len() != 0 {
		t.Fatalf("se esperaba 304 sin cuerpo, se obtuvo %d con %d bytes", notModified.Code, notModified.Body.Len())
	}
	if notModified.Header().Get(echo.HeaderContentEncoding) != "" {
		t.Fatalf("un 304 sin cuerpo no debe declarar Content-Encoding")
	}
}

func TestCompression_SkipsSmallResponsesAndEventStreams(t *testing.T) {
	e := compressionServer()
	small := get(e, "/small", map[string]string{echo.HeaderAcceptEncoding: "gzip"})
	if small.Header().Get(echo.HeaderContentEncoding) != "" || !strings.Contains(small.Body.String(), "UP") {
		t.Fatalf("una respuesta pequeña debe viajar sin comprimir")
	}
	stream := get(e, "/crops/1/stream", map[string]string{echo.HeaderAcceptEncoding: "gzip", echo.HeaderAccept: "text/event-stream"})
	if stream.Header().Get(echo.HeaderContentEncoding) != "" || !strings.HasPrefix(stream.Body.String(), "event: reading") {
		t.Fatalf("el flujo SSE no debe comprimirse")
	}
}
