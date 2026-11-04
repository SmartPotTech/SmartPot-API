package channels

import (
	"net/http"
	"strings"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/httpserver"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/security"
	"github.com/labstack/echo/v4"
)

// Handler expone las rutas de canales (vínculos de la cuenta y avisos por cultivo).
type Handler struct {
	channels     *Service
	cropChannels *CropChannelService
}

// NewHandler crea el controlador de canales.
func NewHandler(channels *Service, cropChannels *CropChannelService) *Handler {
	return &Handler{channels: channels, cropChannels: cropChannels}
}

// Register monta las rutas bajo el grupo autenticado /api/v1.
func (h *Handler) Register(g *echo.Group) {
	g.GET("/channels", h.overview)
	g.POST("/channels/:type/link", h.linkCode)
	g.PUT("/channels/links/:id", h.updateLink)
	g.POST("/channels/links/:id/test", h.test)
	g.DELETE("/channels/links/:id", h.unlink)

	g.GET("/crops/:cropId/channels", h.cropList)
	g.PUT("/crops/:cropId/channels/:type", h.cropUpdate)
	g.POST("/crops/:cropId/channels/:type/recipients", h.share)
	g.DELETE("/crops/:cropId/channels/:type/recipients/:recipientId", h.removeRecipient)
}

func (h *Handler) overview(c echo.Context) error {
	out, err := h.channels.Overview(c.Request().Context(), security.CurrentUserID(c))
	if err != nil {
		return err
	}
	return c.JSON(http.StatusOK, out)
}

func (h *Handler) linkCode(c echo.Context) error {
	t, err := parseType(c.Param("type"))
	if err != nil {
		return err
	}
	res, err := h.channels.CreateLinkCode(c.Request().Context(), security.CurrentUserID(c), t)
	if err != nil {
		return err
	}
	return c.JSON(http.StatusOK, res)
}

func (h *Handler) updateLink(c echo.Context) error {
	var req ChannelLinkUpdateRequest
	if err := httpserver.Bind(c, &req); err != nil {
		return err
	}
	res, err := h.channels.Update(c.Request().Context(), security.CurrentUserID(c), c.Param("id"), req)
	if err != nil {
		return err
	}
	return c.JSON(http.StatusOK, res)
}

func (h *Handler) test(c echo.Context) error {
	if err := h.channels.SendTest(c.Request().Context(), security.CurrentUserID(c), c.Param("id")); err != nil {
		return err
	}
	return c.NoContent(http.StatusNoContent)
}

func (h *Handler) unlink(c echo.Context) error {
	if err := h.channels.Unlink(c.Request().Context(), security.CurrentUserID(c), c.Param("id")); err != nil {
		return err
	}
	return c.NoContent(http.StatusNoContent)
}

func (h *Handler) cropList(c echo.Context) error {
	out, err := h.cropChannels.List(c.Request().Context(), security.CurrentUserID(c), c.Param("cropId"))
	if err != nil {
		return err
	}
	return c.JSON(http.StatusOK, out)
}

func (h *Handler) cropUpdate(c echo.Context) error {
	t, err := parseType(c.Param("type"))
	if err != nil {
		return err
	}
	var req CropChannelUpdateRequest
	if err := httpserver.Bind(c, &req); err != nil {
		return err
	}
	res, err := h.cropChannels.Update(c.Request().Context(), security.CurrentUserID(c), c.Param("cropId"), t, req)
	if err != nil {
		return err
	}
	return c.JSON(http.StatusOK, res)
}

func (h *Handler) share(c echo.Context) error {
	t, err := parseType(c.Param("type"))
	if err != nil {
		return err
	}
	res, err := h.cropChannels.ShareCode(c.Request().Context(), security.CurrentUserID(c), c.Param("cropId"), t)
	if err != nil {
		return err
	}
	return c.JSON(http.StatusOK, res)
}

func (h *Handler) removeRecipient(c echo.Context) error {
	t, err := parseType(c.Param("type"))
	if err != nil {
		return err
	}
	if err := h.cropChannels.RemoveRecipient(c.Request().Context(), security.CurrentUserID(c), c.Param("cropId"), t, c.Param("recipientId")); err != nil {
		return err
	}
	return c.NoContent(http.StatusNoContent)
}

func parseType(raw string) (Type, error) {
	for _, t := range AllTypes {
		if strings.EqualFold(string(t), raw) {
			return t, nil
		}
	}
	return "", apperr.NotFound("Channel does not exist")
}
