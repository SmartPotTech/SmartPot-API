package connections

import (
	"net/http"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/httpserver"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/security"
	"github.com/labstack/echo/v4"
)

// Handler expone las rutas de conexiones. Solo se administran con la sesión de la PWA: el guard rechaza estas
// rutas cuando la petición llega con un token de conexión.
type Handler struct {
	svc *Service
}

// NewHandler crea el controlador de conexiones.
func NewHandler(svc *Service) *Handler {
	return &Handler{svc: svc}
}

// Register monta las rutas bajo el grupo autenticado /api/v1.
func (h *Handler) Register(g *echo.Group) {
	g.GET("/connections", h.list)
	g.POST("/connections", h.create)
	g.DELETE("/connections/:id", h.revoke)
}

func (h *Handler) list(c echo.Context) error {
	items, err := h.svc.List(c.Request().Context(), security.CurrentUserID(c))
	if err != nil {
		return err
	}
	out := make([]Response, 0, len(items))
	for _, t := range items {
		out = append(out, ToResponse(t))
	}
	return c.JSON(http.StatusOK, out)
}

func (h *Handler) create(c echo.Context) error {
	var req CreateRequest
	if err := httpserver.Bind(c, &req); err != nil {
		return err
	}
	created, err := h.svc.Create(c.Request().Context(), security.CurrentUserID(c), req)
	if err != nil {
		return err
	}
	// El token solo viaja en esta respuesta: que ningún intermediario la guarde.
	c.Response().Header().Set("Cache-Control", "no-store")
	return c.JSON(http.StatusCreated, created)
}

func (h *Handler) revoke(c echo.Context) error {
	if err := h.svc.Revoke(c.Request().Context(), security.CurrentUserID(c), c.Param("id")); err != nil {
		return err
	}
	return c.NoContent(http.StatusNoContent)
}
