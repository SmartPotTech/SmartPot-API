package telegram

import (
	"log/slog"
	"sync"
	"sync/atomic"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/config"
)

const maxBackoff = time.Minute

// Listener recibe los mensajes del bot: en modo webhook registra la URL pública;
// en modo polling (local y demo) los consulta con sondeo largo en una goroutine.
type Listener struct {
	cfg      config.Telegram
	client   *Client
	bot      *Bot
	running  atomic.Bool
	stop     chan struct{}
	stopOnce sync.Once
}

// NewListener crea el receptor de mensajes.
func NewListener(cfg config.Telegram, client *Client, bot *Bot) *Listener {
	return &Listener{cfg: cfg, client: client, bot: bot, stop: make(chan struct{})}
}

func (l *Listener) enabled() bool { return l.cfg.BotToken != "" && l.cfg.BotUsername != "" }
func (l *Listener) webhook() bool { return l.cfg.Mode == "webhook" }

// Start configura el bot y arranca la recepción de mensajes.
func (l *Listener) Start() {
	l.running.Store(true)
	if !l.enabled() {
		return
	}
	if err := l.client.SetCommands(); err != nil {
		slog.Warn("Telegram did not respond when configuring bot", "err", err)
	}
	if l.webhook() {
		if err := l.client.SetWebhook(l.cfg.WebhookURL, l.cfg.WebhookSecret); err != nil {
			slog.Warn("Telegram: could not register webhook", "err", err)
		} else {
			slog.Info("Telegram: webhook registered", "url", l.cfg.WebhookURL)
		}
		return
	}
	_ = l.client.DeleteWebhook()
	go l.poll()
	slog.Info("Telegram: receiving messages via long polling")
}

// Stop detiene la recepción de mensajes.
func (l *Listener) Stop() {
	l.running.Store(false)
	l.stopOnce.Do(func() { close(l.stop) })
}

// HandleSafely atiende un mensaje sin dejar que un error tumbe la recepción.
func (l *Listener) HandleSafely(update Update) {
	defer func() {
		if r := recover(); r != nil {
			slog.Warn("could not handle Telegram message", "panic", r)
		}
	}()
	l.bot.Handle(update)
}

func (l *Listener) poll() {
	var offset int64
	backoff := 2 * time.Second
	for l.running.Load() {
		updates, err := l.client.GetUpdates(offset)
		if err != nil {
			if !l.sleep(backoff) {
				return
			}
			backoff *= 2
			if backoff > maxBackoff {
				backoff = maxBackoff
			}
			continue
		}
		for _, update := range updates {
			if update.UpdateID+1 > offset {
				offset = update.UpdateID + 1
			}
			l.HandleSafely(update)
		}
		backoff = 2 * time.Second
	}
}

func (l *Listener) sleep(d time.Duration) bool {
	select {
	case <-time.After(d):
		return true
	case <-l.stop:
		return false
	}
}
