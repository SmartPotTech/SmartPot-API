package virtualdevices

import (
	"net/http"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/httpserver"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/security"
	"github.com/labstack/echo/v4"
)

// Handler expone las rutas de los cultivos virtuales.
type Handler struct {
	svc *Service
}

// NewHandler crea el controlador de cultivos virtuales.
func NewHandler(svc *Service) *Handler {
	return &Handler{svc: svc}
}

// Register monta las rutas bajo el grupo autenticado /api/v1.
func (h *Handler) Register(g *echo.Group) {
	g.GET("/crops/:cropId/virtual-device", h.get)
	g.PUT("/crops/:cropId/virtual-device", h.configure)
	g.DELETE("/crops/:cropId/virtual-device", h.pause)
	g.GET("/virtual-devices/places", h.places)
}

func (h *Handler) get(c echo.Context) error {
	res, err := h.svc.Get(c.Request().Context(), security.CurrentUserID(c), c.Param("cropId"))
	if err != nil {
		return err
	}
	return c.JSON(http.StatusOK, res)
}

func (h *Handler) configure(c echo.Context) error {
	var req VirtualDeviceRequest
	if err := httpserver.Bind(c, &req); err != nil {
		return err
	}
	res, err := h.svc.Configure(c.Request().Context(), security.CurrentUserID(c), c.Param("cropId"), req)
	if err != nil {
		return err
	}
	return c.JSON(http.StatusOK, res)
}

func (h *Handler) pause(c echo.Context) error {
	if err := h.svc.Pause(c.Request().Context(), security.CurrentUserID(c), c.Param("cropId")); err != nil {
		return err
	}
	return c.NoContent(http.StatusNoContent)
}

func (h *Handler) places(c echo.Context) error {
	query := c.QueryParam("q")
	if query == "" {
		return apperr.BadRequest("Invalid or missing parameter")
	}
	places, err := h.svc.Places(c.Request().Context(), query)
	if err != nil {
		return err
	}
	return c.JSON(http.StatusOK, places)
}
