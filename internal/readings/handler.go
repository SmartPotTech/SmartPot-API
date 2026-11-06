package readings

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/httpserver"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/security"
	"github.com/labstack/echo/v4"
)

// Handler expone las rutas de lecturas de un cultivo.
type Handler struct {
	svc *Service
}

// NewHandler crea el controlador de lecturas.
func NewHandler(svc *Service) *Handler {
	return &Handler{svc: svc}
}

// Register monta las rutas bajo el grupo autenticado /api/v1.
func (h *Handler) Register(g *echo.Group) {
	g.GET("/crops/:cropId/readings", h.list)
	g.GET("/crops/:cropId/readings/latest", h.latest)
	g.GET("/crops/:cropId/readings/summary", h.summary)
	g.GET("/crops/:cropId/readings/export", h.export)
	g.GET("/crops/:cropId/readings/stream", h.streamSSE)
	g.GET("/crops/:cropId/stream", h.streamSSE)
	g.POST("/crops/:cropId/readings", h.create)
}

func (h *Handler) list(c echo.Context) error {
	from, ok := queryTime(c, "from")
	if !ok {
		return apperr.BadRequest("Invalid or missing parameter")
	}
	to, ok := queryTime(c, "to")
	if !ok {
		return apperr.BadRequest("Invalid or missing parameter")
	}
	limit, ok := queryIntPtr(c, "limit")
	if !ok {
		return apperr.BadRequest("Invalid or missing parameter")
	}
	items, err := h.svc.List(c.Request().Context(), security.CurrentUserID(c), c.Param("cropId"), from, to, limit)
	if err != nil {
		return err
	}
	out := make([]*ReadingResponse, 0, len(items))
	for i := range items {
		out = append(out, ToResponse(&items[i]))
	}
	return c.JSON(http.StatusOK, out)
}

func (h *Handler) latest(c echo.Context) error {
	reading, err := h.svc.LatestOwned(c.Request().Context(), security.CurrentUserID(c), c.Param("cropId"))
	if err != nil {
		return err
	}
	if reading == nil {
		return apperr.NotFound("The crop has no readings yet")
	}
	return c.JSON(http.StatusOK, ToResponse(reading))
}

func (h *Handler) summary(c echo.Context) error {
	hours := 24
	if raw := c.QueryParam("hours"); raw != "" {
		n, err := strconv.Atoi(raw)
		if err != nil {
			return apperr.BadRequest("Invalid or missing parameter")
		}
		hours = n
	}
	res, err := h.svc.Summary(c.Request().Context(), security.CurrentUserID(c), c.Param("cropId"), hours)
	if err != nil {
		return err
	}
	return c.JSON(http.StatusOK, res)
}

func (h *Handler) export(c echo.Context) error {
	from, ok := queryTime(c, "from")
	if !ok {
		return apperr.BadRequest("Invalid or missing parameter")
	}
	to, ok := queryTime(c, "to")
	if !ok {
		return apperr.BadRequest("Invalid or missing parameter")
	}
	cropID := c.Param("cropId")
	csv, err := h.svc.ExportCSV(c.Request().Context(), security.CurrentUserID(c), cropID, from, to)
	if err != nil {
		return err
	}
	c.Response().Header().Set("Content-Disposition", fmt.Sprintf("attachment; filename=\"smartpot-%s.csv\"", cropID))
	return c.Blob(http.StatusOK, "text/csv; charset=utf-8", []byte(csv))
}

func (h *Handler) create(c echo.Context) error {
	var req MeasuresRequest
	if err := httpserver.DecodeJSON(c, &req); err != nil {
		return err
	}
	reading, err := h.svc.RecordManual(c.Request().Context(), security.CurrentUserID(c), c.Param("cropId"), req.ToMeasures())
	if err != nil {
		return err
	}
	return c.JSON(http.StatusCreated, ToResponse(reading))
}

func (h *Handler) streamSSE(c echo.Context) error {
	cropID := c.Param("cropId")
	userID := security.CurrentUserID(c)
	if err := h.svc.crops.RequireOwned(c.Request().Context(), userID, cropID); err != nil {
		return err
	}

	// Funciona detrás de cualquier nginx: X-Accel-Buffering apaga su búfer, no-transform evita que un proxy
	// comprima o retenga el flujo y, si el proxy habla HTTP/1.0 con la API, la respuesta va sin chunked hasta
	// que se cierra la conexión. Si el flujo se corta, la PWA vuelve a consultar con ETag.
	w := c.Response()
	w.Header().Set(echo.HeaderContentType, "text/event-stream; charset=utf-8")
	w.Header().Set(echo.HeaderCacheControl, "no-cache, no-transform")
	w.Header().Set("X-Accel-Buffering", "no")
	w.WriteHeader(http.StatusOK)

	ch, unsubscribe := h.svc.SubscribeStream(cropID)
	defer unsubscribe()

	flusher := http.NewResponseController(w.Writer)
	// retry fija en 5 s la reconexión del cliente; el comentario inicial abre el flujo en los proxies.
	if _, err := fmt.Fprint(w, "retry: 5000\n: connected\n\n"); err != nil {
		return nil
	}
	if err := flusher.Flush(); err != nil {
		return nil
	}

	ticker := time.NewTicker(15 * time.Second)
	defer ticker.Stop()

	ctx := c.Request().Context()
	for {
		select {
		case <-ctx.Done():
			return nil
		case r, ok := <-ch:
			if !ok {
				return nil
			}
			resp := ToResponse(r)
			data, err := json.Marshal(resp)
			if err != nil {
				continue
			}
			if _, err := fmt.Fprintf(w, "event: reading\ndata: %s\n\n", data); err != nil {
				return nil
			}
			flusher.Flush()
		case <-ticker.C:
			if _, err := fmt.Fprintf(w, ": keepalive\n\n"); err != nil {
				return nil
			}
			flusher.Flush()
		}
	}
}

func queryTime(c echo.Context, key string) (*time.Time, bool) {
	raw := c.QueryParam(key)
	if raw == "" {
		return nil, true
	}
	t, err := time.Parse(time.RFC3339, raw)
	if err != nil {
		return nil, false
	}
	return &t, true
}

func queryIntPtr(c echo.Context, key string) (*int, bool) {
	raw := c.QueryParam(key)
	if raw == "" {
		return nil, true
	}
	n, err := strconv.Atoi(raw)
	if err != nil {
		return nil, false
	}
	return &n, true
}
