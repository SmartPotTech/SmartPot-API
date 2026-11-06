package auth

import (
	"net/http"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/httpserver"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/security"
	"github.com/labstack/echo/v4"
)

// Handler expone las rutas de autenticación.
type Handler struct {
	svc *Service
}

// NewHandler crea el controlador de autenticación.
func NewHandler(svc *Service) *Handler {
	return &Handler{svc: svc}
}

// Register monta las rutas públicas bajo el grupo /api/v1.
func (h *Handler) Register(g *echo.Group) {
	a := g.Group("/auth")
	a.POST("/register", h.register)
	a.POST("/login", h.login)
	a.POST("/logout", h.logout)
	a.POST("/password/forgot", h.forgot)
	a.POST("/password/reset", h.reset)
}

// RegisterAuthed monta la consulta de sesión activa bajo el grupo autenticado /api/v1.
func (h *Handler) RegisterAuthed(g *echo.Group) {
	g.GET("/auth/session", h.session)
}

func (h *Handler) register(c echo.Context) error {
	var req RegisterRequest
	if err := httpserver.Bind(c, &req); err != nil {
		return err
	}
	resp, err := h.svc.Register(c.Request().Context(), req)
	if err != nil {
		return err
	}
	h.svc.SetSessionCookies(c, resp, true)
	return c.JSON(http.StatusCreated, resp)
}

func (h *Handler) login(c echo.Context) error {
	var req LoginRequest
	if err := httpserver.Bind(c, &req); err != nil {
		return err
	}
	resp, err := h.svc.Login(c.Request().Context(), req)
	if err != nil {
		return err
	}
	h.svc.SetSessionCookies(c, resp, req.RememberMe())
	return c.JSON(http.StatusOK, resp)
}

func (h *Handler) logout(c echo.Context) error {
	h.svc.Logout(c)
	return c.NoContent(http.StatusNoContent)
}

func (h *Handler) session(c echo.Context) error {
	id := security.CurrentIdentity(c)
	resp, err := h.svc.CurrentSession(c.Request().Context(), id.UserID, id.ExpiresAt)
	if err != nil {
		return err
	}
	return c.JSON(http.StatusOK, resp)
}

func (h *Handler) forgot(c echo.Context) error {
	var req ForgotPasswordRequest
	if err := httpserver.Bind(c, &req); err != nil {
		return err
	}
	if err := h.svc.RequestPasswordReset(c.Request().Context(), req.Email); err != nil {
		return err
	}
	return c.NoContent(http.StatusAccepted)
}

func (h *Handler) reset(c echo.Context) error {
	var req ResetPasswordRequest
	if err := httpserver.Bind(c, &req); err != nil {
		return err
	}
	if err := h.svc.ResetPassword(c.Request().Context(), req); err != nil {
		return err
	}
	return c.NoContent(http.StatusNoContent)
}
