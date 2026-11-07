package telegram

import (
	"crypto/subtle"
	"encoding/json"
	"net/http"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/config"
	"github.com/labstack/echo/v4"
)

// WebhookController es la entrada pública de los mensajes del bot en modo
// webhook. Telegram firma cada llamada con X-Telegram-Bot-Api-Secret-Token; sin
// el secreto correcto la llamada se rechaza con 401.
type WebhookController struct {
	cfg      config.Telegram
	listener *Listener
}

// NewWebhookController crea el controlador del webhook.
func NewWebhookController(cfg config.Telegram, listener *Listener) *WebhookController {
	return &WebhookController{cfg: cfg, listener: listener}
}

// Register monta la ruta pública del webhook bajo /api/v1.
func (c *WebhookController) Register(g *echo.Group) {
	g.POST("/channels/telegram/webhook", c.receive)
}

func (c *WebhookController) receive(ctx echo.Context) error {
	secret := ctx.Request().Header.Get("X-Telegram-Bot-Api-Secret-Token")
	if !c.enabled() || c.cfg.Mode != "webhook" {
		return ctx.NoContent(http.StatusNotFound)
	}
	if !c.matches(secret) {
		return ctx.NoContent(http.StatusUnauthorized)
	}
	var update Update
	if err := json.NewDecoder(ctx.Request().Body).Decode(&update); err != nil {
		return ctx.NoContent(http.StatusBadRequest)
	}
	c.listener.HandleSafely(update)
	return ctx.NoContent(http.StatusOK)
}

func (c *WebhookController) enabled() bool {
	return c.cfg.BotToken != "" && c.cfg.BotUsername != ""
}

func (c *WebhookController) matches(secret string) bool {
	expected := c.cfg.WebhookSecret
	if secret == "" || expected == "" {
		return false
	}
	return subtle.ConstantTimeCompare([]byte(expected), []byte(secret)) == 1
}
