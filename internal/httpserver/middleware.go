package httpserver

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"strings"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"github.com/labstack/echo/v4"
)

// requestLogger registra cada petición con su método, ruta, estado, latencia y
// traza de telemetría a partir del RequestContext tipado. Si el manejador
// devuelve un error explícito, delega primero en el HTTPErrorHandler centralizado
// para garantizar que el código de estado final quede fijado antes del registro.
func requestLogger(logger *slog.Logger) echo.MiddlewareFunc {
	return func(next echo.HandlerFunc) echo.HandlerFunc {
		return func(c echo.Context) error {
			rc := FromContext(c)
			start := rc.Trace().StartedAt
			if start.IsZero() {
				start = time.Now()
			}
			err := next(c)
			if err != nil {
				c.Error(err)
			}
			req := c.Request()
			res := c.Response()
			logger.Info("peticion",
				"requestId", rc.Trace().RequestID,
				"method", req.Method,
				"path", req.URL.Path,
				"status", res.Status,
				"ms", time.Since(start).Milliseconds(),
			)
			return nil
		}
	}
}

// recovery atrapa pánicos, los registra y responde un 500 en el formato de error.
func recovery(logger *slog.Logger) echo.MiddlewareFunc {
	return func(next echo.HandlerFunc) echo.HandlerFunc {
		return func(c echo.Context) error {
			defer func() {
				if r := recover(); r != nil {
					req := c.Request()
					rc := FromContext(c)
					logger.Error("panico no controlado",
						"requestId", rc.Trace().RequestID,
						"method", req.Method,
						"path", req.URL.Path,
						"panic", r,
					)
					if !c.Response().Committed {
						writeError(c, http.StatusInternalServerError,
							"Ocurrió un error inesperado. Intenta de nuevo más tarde")
					}
				}
			}()
			return next(c)
		}
	}
}

// securityHeaders fija las mismas cabeceras que la cadena de seguridad en Java.
func securityHeaders() echo.MiddlewareFunc {
	return func(next echo.HandlerFunc) echo.HandlerFunc {
		return func(c echo.Context) error {
			c.Response().Header().Set("Referrer-Policy", "no-referrer")
			c.Response().Header().Set("X-Frame-Options", "DENY")
			c.Response().Header().Set("X-Content-Type-Options", "nosniff")
			return next(c)
		}
	}
}

type etagBufferWriter struct {
	http.ResponseWriter
	buf    bytes.Buffer
	status int
}

func (w *etagBufferWriter) WriteHeader(code int) {
	w.status = code
}

func (w *etagBufferWriter) Write(b []byte) (int, error) {
	if w.status == 0 {
		w.status = http.StatusOK
	}
	return w.buf.Write(b)
}

// etagMiddleware calcula una etiqueta ETag débil (SHA-256 truncado a 128 bits)
// para respuestas JSON 200 de lecturas GET y responde 304 Not Modified sin
// transferir cuerpo cuando el cliente envía una cabecera If-None-Match coincidente.
func etagMiddleware() echo.MiddlewareFunc {
	return func(next echo.HandlerFunc) echo.HandlerFunc {
		return func(c echo.Context) error {
			req := c.Request()
			if req.Method != http.MethodGet || isEventStream(req) {
				return next(c)
			}
			res := c.Response()
			origWriter := res.Writer
			bw := &etagBufferWriter{ResponseWriter: origWriter}
			res.Writer = bw
			defer func() {
				res.Writer = origWriter
			}()

			err := next(c)
			if err != nil {
				res.Writer = origWriter
				if bw.status != 0 || bw.buf.Len() > 0 {
					origWriter.WriteHeader(bw.status)
					_, _ = origWriter.Write(bw.buf.Bytes())
				}
				return err
			}

			status := bw.status
			if status == 0 {
				status = res.Status
			}
			if status == 0 {
				status = http.StatusOK
			}
			body := bw.buf.Bytes()
			if status == http.StatusOK && len(body) > 0 && strings.HasPrefix(res.Header().Get(echo.HeaderContentType), echo.MIMEApplicationJSON) {
				sum := sha256.Sum256(body)
				etag := `W/"` + hex.EncodeToString(sum[:16]) + `"`
				res.Header().Set("ETag", etag)
				if res.Header().Get("Cache-Control") == "" {
					res.Header().Set("Cache-Control", "private, no-cache")
				}
				if match := strings.TrimSpace(req.Header.Get("If-None-Match")); match != "" && match == etag {
					res.Status = http.StatusNotModified
					res.Size = 0
					origWriter.WriteHeader(http.StatusNotModified)
					return nil
				}
			}

			origWriter.WriteHeader(status)
			if len(body) > 0 {
				_, _ = origWriter.Write(body)
			}
			return nil
		}
	}
}

// isEventStream indica si la petición abre un flujo de eventos (SSE): esas respuestas no se guardan en un búfer
// para calcular el ETag, porque deben llegar al cliente a medida que se escriben.
func isEventStream(req *http.Request) bool {
	return strings.Contains(req.Header.Get(echo.HeaderAccept), "text/event-stream") ||
		strings.HasSuffix(req.URL.Path, "/stream")
}

// RespondError es el HTTPErrorHandler centralizado: transforma errores de
// validación (*apperr.ValidationError), errores de dominio (*apperr.Error) y
// errores de protocolo (*echo.HTTPError) en respuestas HTTP homogéneas sin
// riesgo de escrituras duplicadas ni ejecuciones residuales.
func RespondError(c echo.Context, err error) {
	if err == nil || c.Response().Committed {
		return
	}
	var valErr *apperr.ValidationError
	if errors.As(err, &valErr) {
		_ = c.JSON(http.StatusBadRequest,
			apperr.NewValidationResponse(valErr.Message, c.Request().URL.Path, valErr.Fields))
		return
	}
	var apiErr *apperr.Error
	if errors.As(err, &apiErr) {
		writeError(c, apiErr.Status, apiErr.Message)
		return
	}
	var he *echo.HTTPError
	if errors.As(err, &he) {
		if he.Code == http.StatusNotFound {
			writeError(c, http.StatusNotFound, "La ruta solicitada no existe")
			return
		}
		if he.Code == http.StatusMethodNotAllowed {
			writeError(c, http.StatusMethodNotAllowed, "Método no permitido para esta ruta")
			return
		}
		msg := fmt.Sprintf("%v", he.Message)
		writeError(c, he.Code, msg)
		return
	}
	writeError(c, http.StatusInternalServerError,
		"Ocurrió un error inesperado. Intenta de nuevo más tarde")
}

func writeError(c echo.Context, status int, message string) {
	_ = c.JSON(status, apperr.NewResponse(status, message, c.Request().URL.Path))
}
