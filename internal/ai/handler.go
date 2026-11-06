package ai

import (
	"net/http"
	"strconv"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/security"
	"github.com/labstack/echo/v4"
)

// Handler expone las rutas del asistente de IA.
type Handler struct {
	insights *InsightService
	client   *Client
}

// NewHandler crea el controlador de IA.
func NewHandler(insights *InsightService, client *Client) *Handler {
	return &Handler{insights: insights, client: client}
}

// RegisterAuthed monta las rutas autenticadas bajo /api/v1.
func (h *Handler) RegisterAuthed(g *echo.Group) {
	g.GET("/crops/:cropId/insights", h.insightsOf)
	g.GET("/ai/learning", h.learning)
}

// RegisterPublic monta las rutas públicas bajo /api/v1.
func (h *Handler) RegisterPublic(g *echo.Group) {
	g.GET("/crop-profiles", h.cropProfiles)
}

func (h *Handler) insightsOf(c echo.Context) error {
	ctx := c.Request().Context()
	ownerID := security.CurrentUserID(c)
	// Un token de conexión READ solo consulta: el índice de salud no se escribe.
	if security.CurrentIdentity(c).Scope == "READ" {
		resp, err := h.insights.ForOwnerReadOnly(ctx, ownerID, c.Param("cropId"))
		if err != nil {
			return err
		}
		return c.JSON(http.StatusOK, resp)
	}
	resp, err := h.insights.ForOwner(ctx, ownerID, c.Param("cropId"))
	if err != nil {
		return err
	}
	return c.JSON(http.StatusOK, resp)
}

func (h *Handler) learning(c echo.Context) error {
	body, err := h.client.LearningStatusJSON(c.Request().Context())
	if err != nil {
		return err
	}
	c.Response().Header().Set("Cache-Control", "private, max-age="+strconv.Itoa(int(learningTTL.Seconds())))
	return c.Blob(http.StatusOK, "application/json; charset=utf-8", []byte(body))
}

func (h *Handler) cropProfiles(c echo.Context) error {
	body, err := h.client.CropProfilesJSON(c.Request().Context())
	if err != nil {
		return err
	}
	c.Response().Header().Set("Cache-Control", "public, max-age=3600")
	return c.Blob(http.StatusOK, "application/json; charset=utf-8", []byte(body))
}
