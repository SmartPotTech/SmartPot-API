package commands

import (
	"net/http"
	"strconv"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/httpserver"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/security"
	"github.com/labstack/echo/v4"
)

// Handler expone las rutas de comandos (por cultivo y el centro de acciones).
type Handler struct {
	svc *Service
}

// NewHandler crea el controlador de comandos.
func NewHandler(svc *Service) *Handler {
	return &Handler{svc: svc}
}

// Register monta las rutas bajo el grupo autenticado /api/v1.
func (h *Handler) Register(g *echo.Group) {
	g.GET("/crops/:cropId/commands", h.listForCrop)
	g.POST("/crops/:cropId/commands", h.send)
	g.GET("/commands", h.listForOwner)
	g.POST("/commands/bulk", h.bulk)
}

func (h *Handler) listForCrop(c echo.Context) error {
	limit, ok := queryInt(c, "limit", 30)
	if !ok {
		return apperr.BadRequest("Invalid or missing parameter")
	}
	items, err := h.svc.List(c.Request().Context(), security.CurrentUserID(c), c.Param("cropId"), limit)
	if err != nil {
		return err
	}
	return c.JSON(http.StatusOK, mapResponses(items))
}

func (h *Handler) send(c echo.Context) error {
	var req CommandRequest
	if err := httpserver.Bind(c, &req); err != nil {
		return err
	}
	cmd, err := h.svc.Request(c.Request().Context(), security.CurrentUserID(c), c.Param("cropId"), req)
	if err != nil {
		return err
	}
	return c.JSON(http.StatusAccepted, ToResponse(cmd))
}

func (h *Handler) listForOwner(c echo.Context) error {
	limit, ok := queryInt(c, "limit", 50)
	if !ok {
		return apperr.BadRequest("Invalid or missing parameter")
	}
	items, err := h.svc.ListForOwner(c.Request().Context(), security.CurrentUserID(c), limit)
	if err != nil {
		return err
	}
	return c.JSON(http.StatusOK, mapResponses(items))
}

func (h *Handler) bulk(c echo.Context) error {
	var req BulkCommandRequest
	if err := httpserver.Bind(c, &req); err != nil {
		return err
	}
	res, err := h.svc.RequestBulk(c.Request().Context(), security.CurrentUserID(c), req)
	if err != nil {
		return err
	}
	return c.JSON(http.StatusAccepted, res)
}

func mapResponses(items []Command) []CommandResponse {
	out := make([]CommandResponse, 0, len(items))
	for i := range items {
		out = append(out, ToResponse(&items[i]))
	}
	return out
}

func queryInt(c echo.Context, key string, def int) (int, bool) {
	raw := c.QueryParam(key)
	if raw == "" {
		return def, true
	}
	n, err := strconv.Atoi(raw)
	return n, err == nil
}
