// Package mail envía los correos de bienvenida y de recuperación de contraseña
// por SMTP (Mailpit en local). El envío es asíncrono y los fallos no cortan el
// flujo, igual que en la versión en Java.
package mail

import (
	"log/slog"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/config"
	gomail "github.com/wneessen/go-mail"
)

// Mailer envía correos con plantillas de SmartPot.
type Mailer struct {
	enabled    bool
	from       string
	webBaseURL string
	client     *gomail.Client
	log        *slog.Logger
}

// NewMailer crea el emisor. Si el correo está deshabilitado, no abre conexión.
func NewMailer(cfg config.Mail, webBaseURL string, logger *slog.Logger) (*Mailer, error) {
	m := &Mailer{enabled: cfg.Enabled, from: cfg.From, webBaseURL: webBaseURL, log: logger}
	if !cfg.Enabled {
		logger.Info("mail disabled")
		return m, nil
	}

	opts := []gomail.Option{
		gomail.WithPort(cfg.Port),
		gomail.WithTimeout(5 * time.Second),
	}
	if cfg.SMTPAuth {
		opts = append(opts,
			gomail.WithSMTPAuth(gomail.SMTPAuthPlain),
			gomail.WithUsername(cfg.Username),
			gomail.WithPassword(cfg.Password))
	}
	if cfg.STARTTLS {
		opts = append(opts, gomail.WithTLSPolicy(gomail.TLSMandatory))
	} else {
		opts = append(opts, gomail.WithTLSPolicy(gomail.NoTLS))
	}

	client, err := gomail.NewClient(cfg.Host, opts...)
	if err != nil {
		return nil, err
	}
	m.client = client
	return m, nil
}

// SendWelcome envía el correo de bienvenida.
func (m *Mailer) SendWelcome(name, email string) {
	m.sendAsync(email, "Welcome to SmartPot", welcome(name, m.webBaseURL+"/login"))
}

// SendPasswordReset envía el enlace de recuperación de contraseña.
func (m *Mailer) SendPasswordReset(name, email, token string, validity time.Duration) {
	resetURL := m.webBaseURL + "/reset-password?token=" + token
	m.sendAsync(email, "Reset your SmartPot password", passwordReset(name, resetURL, int64(validity.Minutes())))
}

func (m *Mailer) sendAsync(to, subject string, c content) {
	go func() {
		if err := m.send(to, subject, c); err != nil {
			m.log.Warn("failed to send email", "subject", subject, "err", err)
		}
	}()
}

func (m *Mailer) send(to, subject string, c content) error {
	if !m.enabled {
		m.log.Info("mail disabled; not sending", "subject", subject)
		return nil
	}
	msg := gomail.NewMsg()
	if err := msg.From(m.from); err != nil {
		return err
	}
	if err := msg.To(to); err != nil {
		return err
	}
	msg.Subject(subject)
	msg.SetBodyString(gomail.TypeTextPlain, c.text)
	msg.AddAlternativeString(gomail.TypeTextHTML, c.html)
	return m.client.DialAndSend(msg)
}
