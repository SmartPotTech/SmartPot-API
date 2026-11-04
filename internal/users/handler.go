package users

import (
	"net/http"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/httpserver"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/security"
	"github.com/labstack/echo/v4"
)

// Handler expone las rutas del perfil de la cuenta.
type Handler struct {
	svc *Service
}

// NewHandler crea el controlador de usuarios.
func NewHandler(svc *Service) *Handler {
	return &Handler{svc: svc}
}

// Register monta las rutas bajo el grupo autenticado /api/v1.
func (h *Handler) Register(g *echo.Group) {
	g.GET("/users/me", h.me)
	g.PUT("/users/me", h.update)
	g.PUT("/users/me/password", h.changePassword)
	g.DELETE("/users/me", h.delete)
}

func (h *Handler) me(c echo.Context) error {
	u, err := h.svc.GetByID(c.Request().Context(), security.CurrentUserID(c))
	if err != nil {
		return err
	}
	return c.JSON(http.StatusOK, ToResponse(u))
}

func (h *Handler) update(c echo.Context) error {
	var req UpdateProfileRequest
	if err := httpserver.Bind(c, &req); err != nil {
		return err
	}
	u, err := h.svc.UpdateProfile(c.Request().Context(), security.CurrentUserID(c), req)
	if err != nil {
		return err
	}
	return c.JSON(http.StatusOK, ToResponse(u))
}

func (h *Handler) changePassword(c echo.Context) error {
	var req ChangePasswordRequest
	if err := httpserver.Bind(c, &req); err != nil {
		return err
	}
	if err := h.svc.ChangePassword(c.Request().Context(), security.CurrentUserID(c), req); err != nil {
		return err
	}
	return c.NoContent(http.StatusNoContent)
}

func (h *Handler) delete(c echo.Context) error {
	if err := h.svc.DeleteAccount(c.Request().Context(), security.CurrentUserID(c)); err != nil {
		return err
	}
	security.ClearSessionCookies(c)
	return c.NoContent(http.StatusNoContent)
}
