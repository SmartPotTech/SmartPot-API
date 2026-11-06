package telegram

import (
	"strings"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/channels"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/config"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/notifications"
)

var icons = map[notifications.Type]string{
	notifications.TypeAlert:   "⚠️",
	notifications.TypeDevice:  "📡",
	notifications.TypeAI:      "🤖",
	notifications.TypeCommand: "💧",
	notifications.TypeInfo:    "🌱",
}

// Channel es el canal de Telegram (implementa channels.NotificationChannel).
type Channel struct {
	cfg    config.Telegram
	client *Client
}

// NewChannel crea el canal de Telegram.
func NewChannel(cfg config.Telegram, client *Client) *Channel {
	return &Channel{cfg: cfg, client: client}
}

// Type devuelve el tipo de canal.
func (c *Channel) Type() channels.Type { return channels.Telegram }

// IsAvailable indica si el servidor tiene el bot configurado.
func (c *Channel) IsAvailable() bool {
	return c.cfg.BotToken != "" && c.cfg.BotUsername != ""
}

// DisplayName devuelve el nombre visible.
func (c *Channel) DisplayName() string { return "Telegram" }

// Handle devuelve el usuario del bot.
func (c *Channel) Handle() string { return "@" + c.username() }

// LinkURL arma el enlace que abre el bot con el código.
func (c *Channel) LinkURL(code string) string {
	return "https://t.me/" + c.username() + "?start=" + code
}

// Description describe el canal para la PWA.
func (c *Channel) Description() string {
	return "Receive alerts, digests, and assistant actions in a chat with the SmartPot bot."
}

// Requirements son las variables que el servidor necesita para ofrecer el canal.
func (c *Channel) Requirements() []string {
	return []string{"TELEGRAM_BOT_TOKEN", "TELEGRAM_BOT_USERNAME"}
}

// Send envía un mensaje al chat.
func (c *Channel) Send(address string, message channels.ChannelMessage) error {
	return c.client.SendMessage(address, format(message), "Open in SmartPot", message.URL)
}

func (c *Channel) username() string {
	return strings.TrimPrefix(c.cfg.BotUsername, "@")
}

func format(message channels.ChannelMessage) string {
	icon := icons[message.Type]
	if icon == "" {
		icon = "🌱"
	}
	return icon + " <b>" + escape(message.Title) + "</b>\n" + escape(message.Body)
}

// escape protege los caracteres que Telegram interpreta en HTML (& < >).
func escape(text string) string {
	text = strings.ReplaceAll(text, "&", "&amp;")
	text = strings.ReplaceAll(text, "<", "&lt;")
	return strings.ReplaceAll(text, ">", "&gt;")
}
