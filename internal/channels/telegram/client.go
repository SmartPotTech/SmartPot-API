package telegram

import (
	"bytes"
	"context"
	"encoding/json"
	"io"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/channels"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/config"
)

// pollSeconds es la espera del sondeo largo.
const pollSeconds = 25

// Client es el cliente mínimo del Bot API de Telegram.
type Client struct {
	baseURL string
	http    *http.Client
	polling *http.Client
}

// NewClient crea el cliente del Bot API.
func NewClient(cfg config.Telegram) *Client {
	base := cfg.APIBaseURL + "/bot" + cfg.BotToken
	return &Client{
		baseURL: base,
		http:    &http.Client{Timeout: 10 * time.Second},
		polling: &http.Client{Timeout: (pollSeconds + 15) * time.Second},
	}
}

// SendMessage envía un mensaje con formato HTML; con url agrega el botón.
func (c *Client) SendMessage(chatID, html, buttonText, url string) error {
	body := map[string]any{
		"chat_id":              chatID,
		"text":                 html,
		"parse_mode":           "HTML",
		"link_preview_options": map[string]any{"is_disabled": true},
	}
	if strings.HasPrefix(url, "https://") {
		body["reply_markup"] = map[string]any{
			"inline_keyboard": [][]map[string]any{{{"text": buttonText, "url": url}}},
		}
	}
	status, respBody, err := c.call(c.http, "sendMessage", body)
	if err != nil {
		return &channels.DeliveryError{Message: "Telegram did not respond", Permanent: false}
	}
	if status < 200 || status >= 300 {
		permanent := status == http.StatusForbidden || strings.Contains(respBody, "chat not found")
		return &channels.DeliveryError{Message: "Telegram rejected the message (" + itoa(status) + ")", Permanent: permanent}
	}
	return nil
}

// GetUpdates consulta las novedades con sondeo largo.
func (c *Client) GetUpdates(offset int64) ([]Update, error) {
	body := map[string]any{"offset": offset, "timeout": pollSeconds, "allowed_updates": []string{"message"}}
	status, respBody, err := c.call(c.polling, "getUpdates", body)
	if err != nil || status < 200 || status >= 300 {
		if err == nil {
			err = &channels.DeliveryError{Message: "getUpdates " + itoa(status), Permanent: false}
		}
		return nil, err
	}
	var parsed updates
	if err := json.Unmarshal([]byte(respBody), &parsed); err != nil {
		return nil, err
	}
	return parsed.Result, nil
}

// SetWebhook registra la URL pública del webhook.
func (c *Client) SetWebhook(url, secret string) error {
	_, _, err := c.call(c.http, "setWebhook", map[string]any{
		"url": url, "secret_token": secret, "allowed_updates": []string{"message"},
	})
	return err
}

// DeleteWebhook borra el webhook (para usar sondeo).
func (c *Client) DeleteWebhook() error {
	_, _, err := c.call(c.http, "deleteWebhook", map[string]any{"drop_pending_updates": false})
	return err
}

// SetCommands fija el menú de comandos del bot.
func (c *Client) SetCommands() error {
	_, _, err := c.call(c.http, "setMyCommands", map[string]any{"commands": []map[string]string{
		{"command": "status", "description": "Status of your crops"},
		{"command": "unlink", "description": "Stop receiving alerts here"},
		{"command": "help", "description": "What I can do"},
	}})
	return err
}

func (c *Client) call(client *http.Client, method string, body map[string]any) (int, string, error) {
	data, err := json.Marshal(body)
	if err != nil {
		return 0, "", err
	}
	req, err := http.NewRequestWithContext(context.Background(), http.MethodPost, c.baseURL+"/"+method, bytes.NewReader(data))
	if err != nil {
		return 0, "", err
	}
	req.Header.Set("Content-Type", "application/json")
	resp, err := client.Do(req)
	if err != nil {
		return 0, "", err
	}
	defer resp.Body.Close()
	out, _ := io.ReadAll(resp.Body)
	return resp.StatusCode, string(out), nil
}

func itoa(n int) string {
	return strconv.Itoa(n)
}
