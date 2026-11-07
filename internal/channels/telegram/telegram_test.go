package telegram

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/channels"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/config"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/notifications"
	"github.com/labstack/echo/v4"
)

func TestEscape(t *testing.T) {
	cases := map[string]string{
		"sin nada":      "sin nada",
		"a & b":         "a &amp; b",
		"<b>x</b>":      "&lt;b&gt;x&lt;/b&gt;",
		"1 < 2 > 0 & 3": "1 &lt; 2 &gt; 0 &amp; 3",
	}
	for in, want := range cases {
		if got := escape(in); got != want {
			t.Errorf("escape(%q) = %q, se esperaba %q", in, got, want)
		}
	}
}

func TestFormat(t *testing.T) {
	msg := channels.ChannelMessage{Type: notifications.TypeAlert, Title: "Calor <alto>", Body: "sube & sube"}
	want := "⚠️ <b>Calor &lt;alto&gt;</b>\nsube &amp; sube"
	if got := format(msg); got != want {
		t.Errorf("format = %q, se esperaba %q", got, want)
	}
	// Un tipo sin icono propio cae en la planta por defecto.
	other := channels.ChannelMessage{Type: notifications.TypeInfo, Title: "Hola", Body: "mundo"}
	if got := format(other); !strings.HasPrefix(got, "🌱 <b>Hola</b>") {
		t.Errorf("format por defecto = %q", got)
	}
}

func serveWebhook(wc *WebhookController, secret, body string) int {
	e := echo.New()
	wc.Register(e.Group("/api/v1"))
	req := httptest.NewRequest(http.MethodPost, "/api/v1/channels/telegram/webhook", strings.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	if secret != "" {
		req.Header.Set("X-Telegram-Bot-Api-Secret-Token", secret)
	}
	w := httptest.NewRecorder()
	e.ServeHTTP(w, req)
	return w.Code
}

func TestWebhookReceive(t *testing.T) {
	enabled := config.Telegram{BotToken: "T", BotUsername: "bot", Mode: "webhook", WebhookSecret: "s3cr3t"}
	disabled := config.Telegram{Mode: "webhook", WebhookSecret: "s3cr3t"}
	polling := config.Telegram{BotToken: "T", BotUsername: "bot", Mode: "polling", WebhookSecret: "s3cr3t"}

	cases := []struct {
		name   string
		cfg    config.Telegram
		secret string
		body   string
		want   int
	}{
		{"secreto correcto", enabled, "s3cr3t", `{}`, http.StatusOK},
		{"wrong secret", enabled, "otro", `{}`, http.StatusUnauthorized},
		{"without secret", enabled, "", `{}`, http.StatusUnauthorized},
		{"bot not configured", disabled, "s3cr3t", `{}`, http.StatusNotFound},
		{"modo sondeo", polling, "s3cr3t", `{}`, http.StatusNotFound},
		{"invalid body", enabled, "s3cr3t", `{roto`, http.StatusBadRequest},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			// El bot es nil: en estos casos no se procesa ningún mensaje real
			// (el secreto bloquea o el cuerpo va vacío), y HandleSafely se recupera.
			wc := NewWebhookController(c.cfg, NewListener(c.cfg, NewClient(c.cfg), nil))
			if code := serveWebhook(wc, c.secret, c.body); code != c.want {
				t.Errorf("status %d, expected %d", code, c.want)
			}
		})
	}
}
