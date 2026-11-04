package notifications

import (
	"net/http"
	"strconv"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/security"
	"github.com/labstack/echo/v4"
)

// Handler expone las rutas de notificaciones.
type Handler struct {
	svc *Service
}

// NewHandler crea el controlador de notificaciones.
func NewHandler(svc *Service) *Handler {
	return &Handler{svc: svc}
}

// Register monta las rutas bajo el grupo autenticado /api/v1.
func (h *Handler) Register(g *echo.Group) {
	g.GET("/notifications", h.list)
	g.GET("/notifications/unread-count", h.unreadCount)
	g.PUT("/notifications/:id/read", h.markRead)
	g.PUT("/notifications/read-all", h.markAllRead)
	g.DELETE("/notifications/:id", h.delete)
}

func (h *Handler) list(c echo.Context) error {
	unreadOnly, ok := queryBool(c, "unreadOnly", false)
	if !ok {
		return apperr.BadRequest("Invalid or missing parameter")
	}
	limit, ok := queryInt(c, "limit", 50)
	if !ok {
		return apperr.BadRequest("Invalid or missing parameter")
	}
	items, err := h.svc.List(c.Request().Context(), security.CurrentUserID(c), unreadOnly, limit)
	if err != nil {
		return err
	}
	out := make([]Response, 0, len(items))
	for _, n := range items {
		out = append(out, ToResponse(n))
	}
	return c.JSON(http.StatusOK, out)
}

func (h *Handler) unreadCount(c echo.Context) error {
	n, err := h.svc.UnreadCount(c.Request().Context(), security.CurrentUserID(c))
	if err != nil {
		return err
	}
	return c.JSON(http.StatusOK, echo.Map{"unread": n})
}

func (h *Handler) markRead(c echo.Context) error {
	n, err := h.svc.MarkRead(c.Request().Context(), security.CurrentUserID(c), c.Param("id"))
	if err != nil {
		return err
	}
	return c.JSON(http.StatusOK, ToResponse(*n))
}

func (h *Handler) markAllRead(c echo.Context) error {
	if err := h.svc.MarkAllRead(c.Request().Context(), security.CurrentUserID(c)); err != nil {
		return err
	}
	return c.NoContent(http.StatusNoContent)
}

func (h *Handler) delete(c echo.Context) error {
	if err := h.svc.Delete(c.Request().Context(), security.CurrentUserID(c), c.Param("id")); err != nil {
		return err
	}
	return c.NoContent(http.StatusNoContent)
}

func queryBool(c echo.Context, key string, def bool) (bool, bool) {
	raw := c.QueryParam(key)
	if raw == "" {
		return def, true
	}
	v, err := strconv.ParseBool(raw)
	return v, err == nil
}

func queryInt(c echo.Context, key string, def int) (int, bool) {
	raw := c.QueryParam(key)
	if raw == "" {
		return def, true
	}
	v, err := strconv.Atoi(raw)
	return v, err == nil
}
