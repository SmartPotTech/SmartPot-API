package mail

import (
	"bufio"
	"context"
	"encoding/base64"
	"fmt"
	"io"
	"log/slog"
	"mime"
	"mime/multipart"
	"mime/quotedprintable"
	"net"
	"net/mail"
	"net/textproto"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/config"
	gomail "github.com/wneessen/go-mail"
)

const (
	testWebBaseURL = "https://smartpot.app"
	testFrom       = "no-reply@smartpot.app"
)

// capturedMail es un mensaje recibido por el servidor SMTP de prueba.
type capturedMail struct {
	from     string
	to       []string
	data     string
	authLine string
}

// testSMTPServer implementa el diálogo mínimo de SMTP en loopback para no
// depender de Mailpit ni de la red externa.
type testSMTPServer struct {
	ln       net.Listener
	messages chan capturedMail
	accepted atomic.Int64
	mu       sync.Mutex
	closed   bool
}

var testServersByPort sync.Map

func startTestSMTPServer(t *testing.T) *testSMTPServer {
	t.Helper()
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("listen on loopback: %v", err)
	}
	s := &testSMTPServer{ln: ln, messages: make(chan capturedMail, 16)}
	testServersByPort.Store(s.port(), s)
	go s.serve()
	t.Cleanup(func() {
		testServersByPort.Delete(s.port())
		s.mu.Lock()
		s.closed = true
		s.mu.Unlock()
		_ = ln.Close()
	})
	return s
}

func (s *testSMTPServer) dial(ctx context.Context, network, _ string) (net.Conn, error) {
	var d net.Dialer
	if conn, err := d.DialContext(ctx, network, s.ln.Addr().String()); err == nil {
		return conn, nil
	}
	clientConn, serverConn := net.Pipe()
	s.accepted.Add(1)
	go s.handle(serverConn)
	return clientConn, nil
}

func (s *testSMTPServer) port() int {
	return s.ln.Addr().(*net.TCPAddr).Port
}

func (s *testSMTPServer) serve() {
	for {
		conn, err := s.ln.Accept()
		if err != nil {
			return
		}
		s.accepted.Add(1)
		go s.handle(conn)
	}
}

func (s *testSMTPServer) handle(conn net.Conn) {
	defer conn.Close()
	reader := bufio.NewReader(conn)
	writer := bufio.NewWriter(conn)
	reply := func(line string) {
		_, _ = fmt.Fprintf(writer, "%s\r\n", line)
		_ = writer.Flush()
	}

	reply("220 localhost ESMTP SmartPot test")
	var msg capturedMail
	for {
		line, err := reader.ReadString('\n')
		if err != nil {
			return
		}
		line = strings.TrimRight(line, "\r\n")
		upper := strings.ToUpper(line)
		switch {
		case strings.HasPrefix(upper, "EHLO"):
			reply("250-localhost")
			reply("250-AUTH PLAIN")
			reply("250-8BITMIME")
			reply("250 OK")
		case strings.HasPrefix(upper, "HELO"):
			reply("250 localhost")
		case strings.HasPrefix(upper, "AUTH PLAIN"):
			msg.authLine = line
			reply("235 2.7.0 Authentication successful")
		case strings.HasPrefix(upper, "MAIL FROM:"):
			msg.from = addressIn(line)
			reply("250 2.1.0 OK")
		case strings.HasPrefix(upper, "RCPT TO:"):
			msg.to = append(msg.to, addressIn(line))
			reply("250 2.1.5 OK")
		case strings.HasPrefix(upper, "DATA"):
			reply("354 End data with <CR><LF>.<CR><LF>")
			var body strings.Builder
			for {
				dataLine, err := reader.ReadString('\n')
				if err != nil {
					return
				}
				if strings.TrimRight(dataLine, "\r\n") == "." {
					break
				}
				// Deshace el dot-stuffing de SMTP.
				if strings.HasPrefix(dataLine, "..") {
					dataLine = dataLine[1:]
				}
				body.WriteString(dataLine)
			}
			msg.data = body.String()
			reply("250 2.0.0 OK queued")
			s.mu.Lock()
			if !s.closed {
				s.messages <- msg
			}
			s.mu.Unlock()
			msg = capturedMail{}
		case strings.HasPrefix(upper, "RSET"):
			reply("250 OK")
		case strings.HasPrefix(upper, "NOOP"):
			reply("250 OK")
		case strings.HasPrefix(upper, "QUIT"):
			reply("221 Bye")
			return
		default:
			reply("250 OK")
		}
	}
}

func addressIn(line string) string {
	start := strings.IndexByte(line, '<')
	end := strings.IndexByte(line, '>')
	if start == -1 || end <= start {
		return ""
	}
	return strings.TrimSpace(line[start+1 : end])
}

func (s *testSMTPServer) next(t *testing.T) capturedMail {
	t.Helper()
	select {
	case msg := <-s.messages:
		return msg
	case <-time.After(5 * time.Second):
		t.Fatal("timed out waiting for the SMTP message")
		return capturedMail{}
	}
}

// decodeMessage devuelve los cuerpos text/plain y text/html ya decodificados.
func decodeMessage(t *testing.T, raw string) (plain, html string) {
	t.Helper()
	msg, err := mail.ReadMessage(strings.NewReader(raw))
	if err != nil {
		t.Fatalf("read MIME message: %v", err)
	}

	var walk func(header textproto.MIMEHeader, body io.Reader)
	walk = func(header textproto.MIMEHeader, body io.Reader) {
		mediaType, params, err := mime.ParseMediaType(header.Get("Content-Type"))
		if err != nil {
			t.Fatalf("parse content type: %v", err)
		}
		if strings.HasPrefix(mediaType, "multipart/") {
			reader := multipart.NewReader(body, params["boundary"])
			for {
				part, err := reader.NextPart()
				if err == io.EOF {
					return
				}
				if err != nil {
					t.Fatalf("read MIME part: %v", err)
				}
				walk(part.Header, part)
			}
		}

		var content io.Reader = body
		switch strings.ToLower(header.Get("Content-Transfer-Encoding")) {
		case "quoted-printable":
			content = quotedprintable.NewReader(body)
		case "base64":
			content = base64.NewDecoder(base64.StdEncoding, body)
		}
		decoded, err := io.ReadAll(content)
		if err != nil {
			t.Fatalf("decode MIME body: %v", err)
		}
		switch {
		case strings.HasPrefix(mediaType, "text/html"):
			html += string(decoded)
		case strings.HasPrefix(mediaType, "text/plain"):
			plain += string(decoded)
		}
	}
	walk(textproto.MIMEHeader(msg.Header), msg.Body)
	return plain, html
}

func newTestMailer(t *testing.T, cfg config.Mail, baseURL string) *Mailer {
	t.Helper()
	logger := slog.New(slog.NewTextHandler(io.Discard, nil))
	m, err := NewMailer(cfg, baseURL, logger)
	if err != nil {
		t.Fatalf("NewMailer: %v", err)
	}
	if srv, ok := testServersByPort.Load(cfg.Port); ok && m.client != nil {
		if err := gomail.WithDialContextFunc(srv.(*testSMTPServer).dial)(m.client); err != nil {
			t.Fatalf("WithDialContextFunc: %v", err)
		}
	}
	return m
}

func TestWelcomeContent(t *testing.T) {
	c := welcome(`Ana <script>alert("x")</script>`, testWebBaseURL+"/login")

	if !strings.Contains(c.html, testWebBaseURL+"/login") {
		t.Errorf("HTML must contain the login link")
	}
	if !strings.Contains(c.text, testWebBaseURL+"/login") {
		t.Errorf("plain text must contain the login link")
	}
	if !strings.Contains(c.html, "Ana &lt;script&gt;") {
		t.Errorf("HTML must escape the user name: %s", c.html)
	}
	if strings.Contains(c.html, "<script>alert") {
		t.Errorf("HTML must not contain the raw script tag")
	}
	if !strings.Contains(c.text, `Ana <script>`) {
		t.Errorf("plain text must keep the original name")
	}
	if !strings.Contains(c.html, "Welcome to SmartPot") || !strings.Contains(c.text, "Hello Ana") {
		t.Errorf("welcome content is incomplete")
	}
	if !strings.Contains(c.html, "Your SmartPot account is ready") ||
		!strings.Contains(c.text, "Your SmartPot account is ready") {
		t.Errorf("both bodies must carry the same welcome message")
	}
	for _, placeholder := range []string{"{{", "%!", "<no value>"} {
		if strings.Contains(c.html, placeholder) || strings.Contains(c.text, placeholder) {
			t.Errorf("content contains an uninterpolated template marker %q", placeholder)
		}
	}
}

func TestPasswordResetContent(t *testing.T) {
	resetURL := testWebBaseURL + "/reset-password?token=token-123"
	c := passwordReset(`Ana <b>Dueña</b>`, resetURL, 30)

	if !strings.Contains(c.html, `href="`+resetURL+`"`) {
		t.Errorf("HTML must link to the exact reset URL: %s", c.html)
	}
	if !strings.Contains(c.text, resetURL) {
		t.Errorf("plain text must contain the exact reset URL")
	}
	if !strings.Contains(c.html, "Ana &lt;b&gt;Dueña&lt;/b&gt;") {
		t.Errorf("HTML must escape the user name")
	}
	if strings.Contains(c.html, "<b>Dueña</b>") {
		t.Errorf("HTML must not contain the raw markup from the name")
	}
	if !strings.Contains(c.text, "Ana <b>Dueña</b>") {
		t.Errorf("plain text must keep the original name")
	}
	if !strings.Contains(c.html, "expires in 30 minutes") || !strings.Contains(c.text, "expires in 30 minutes") {
		t.Errorf("both bodies must state the 30-minute validity")
	}
	if !strings.Contains(c.html, "can only be used once") {
		t.Errorf("HTML must warn that the link is single-use")
	}
	for _, placeholder := range []string{"{{", "%!", "<no value>"} {
		if strings.Contains(c.html, placeholder) || strings.Contains(c.text, placeholder) {
			t.Errorf("content contains an uninterpolated template marker %q", placeholder)
		}
	}
}

func TestSendWithoutAuth(t *testing.T) {
	server := startTestSMTPServer(t)
	m := newTestMailer(t, config.Mail{
		Enabled: true,
		Host:    "127.0.0.1",
		Port:    server.port(),
		From:    testFrom,
	}, testWebBaseURL)

	c := welcome("Ana", testWebBaseURL+"/login")
	if err := m.send("ana@smartpot.app", "Welcome to SmartPot", c); err != nil {
		t.Fatalf("send: %v", err)
	}

	msg := server.next(t)
	if msg.from != testFrom {
		t.Errorf("envelope sender = %q, want %q", msg.from, testFrom)
	}
	if len(msg.to) != 1 || msg.to[0] != "ana@smartpot.app" {
		t.Errorf("envelope recipients = %v, want ana@smartpot.app", msg.to)
	}
	if msg.authLine != "" {
		t.Errorf("AUTH must not be used when SMTPAuth is disabled, got %q", msg.authLine)
	}

	header, err := mail.ReadMessage(strings.NewReader(msg.data))
	if err != nil {
		t.Fatalf("read message: %v", err)
	}
	if got := header.Header.Get("Subject"); got != "Welcome to SmartPot" {
		t.Errorf("Subject = %q", got)
	}
	plain, html := decodeMessage(t, msg.data)
	if !strings.Contains(plain, testWebBaseURL+"/login") || !strings.Contains(html, testWebBaseURL+"/login") {
		t.Errorf("the MIME bodies must contain the login link")
	}
	if !strings.Contains(plain, "Hello Ana") || !strings.Contains(html, "Hello Ana") {
		t.Errorf("the MIME bodies must contain the recipient name")
	}
}

func TestSendWithAuth(t *testing.T) {
	server := startTestSMTPServer(t)
	m := newTestMailer(t, config.Mail{
		Enabled:  true,
		Host:     "127.0.0.1",
		Port:     server.port(),
		From:     testFrom,
		SMTPAuth: true,
		Username: "smartpot",
		Password: "smtp-secret",
	}, testWebBaseURL)

	c := passwordReset("Ana", testWebBaseURL+"/reset-password?token=abc", 30)
	if err := m.send("ana@smartpot.app", "Reset your SmartPot password", c); err != nil {
		t.Fatalf("send: %v", err)
	}

	msg := server.next(t)
	if !strings.HasPrefix(strings.ToUpper(msg.authLine), "AUTH PLAIN") {
		t.Errorf("expected AUTH PLAIN, got %q", msg.authLine)
	}
	plain, _ := decodeMessage(t, msg.data)
	if !strings.Contains(plain, "reset-password?token=abc") {
		t.Errorf("the message body must contain the reset link")
	}
}

func TestSendPasswordResetUsesConfiguredBaseURL(t *testing.T) {
	server := startTestSMTPServer(t)
	m := newTestMailer(t, config.Mail{
		Enabled: true,
		Host:    "127.0.0.1",
		Port:    server.port(),
		From:    testFrom,
	}, testWebBaseURL)

	m.SendPasswordReset("Ana", "ana@smartpot.app", "raw-token_123", 45*time.Minute)

	msg := server.next(t)
	header, err := mail.ReadMessage(strings.NewReader(msg.data))
	if err != nil {
		t.Fatalf("read message: %v", err)
	}
	if got := header.Header.Get("Subject"); got != "Reset your SmartPot password" {
		t.Errorf("Subject = %q", got)
	}
	if len(msg.to) != 1 || msg.to[0] != "ana@smartpot.app" {
		t.Errorf("envelope recipients = %v", msg.to)
	}
	wantURL := testWebBaseURL + "/reset-password?token=raw-token_123"
	plain, html := decodeMessage(t, msg.data)
	if !strings.Contains(plain, wantURL) || !strings.Contains(html, wantURL) {
		t.Errorf("both bodies must contain %q", wantURL)
	}
	if !strings.Contains(plain, "45 minutes") || !strings.Contains(html, "45 minutes") {
		t.Errorf("both bodies must contain the configured validity")
	}
}

func TestSendWhenDisabledDoesNotConnect(t *testing.T) {
	server := startTestSMTPServer(t)
	m := newTestMailer(t, config.Mail{
		Enabled: false,
		Host:    "127.0.0.1",
		Port:    server.port(),
		From:    testFrom,
	}, testWebBaseURL)

	if err := m.send("ana@smartpot.app", "Welcome to SmartPot", welcome("Ana", testWebBaseURL+"/login")); err != nil {
		t.Fatalf("disabled mailer must not fail: %v", err)
	}
	time.Sleep(100 * time.Millisecond)
	if got := server.accepted.Load(); got != 0 {
		t.Errorf("disabled mailer opened %d SMTP connections, want 0", got)
	}
	select {
	case msg := <-server.messages:
		t.Errorf("disabled mailer delivered a message: %+v", msg)
	default:
	}
}

func TestSendWithAuthOverNonLocalhostInternalHost(t *testing.T) {
	server := startTestSMTPServer(t)
	m := newTestMailer(t, config.Mail{
		Enabled:  true,
		Host:     "mail-smartpot",
		Port:     server.port(),
		From:     "SmartPot <no-reply@smartpot.app>",
		SMTPAuth: true,
		STARTTLS: false,
		Username: "smartpot",
		Password: "smtp-secret-password",
	}, testWebBaseURL+"/")

	m.SendPasswordReset("Ana", "ana@smartpot.app", "tok-non-localhost", 30*time.Minute)

	msg := server.next(t)
	expectedAuth := base64.StdEncoding.EncodeToString([]byte("\x00smartpot\x00smtp-secret-password"))
	if !strings.Contains(msg.authLine, expectedAuth) {
		t.Fatalf("expected AUTH PLAIN with %q, got %q", expectedAuth, msg.authLine)
	}
	plain, html := decodeMessage(t, msg.data)
	wantURL := testWebBaseURL + "/reset-password?token=tok-non-localhost"
	if !strings.Contains(plain, wantURL) || !strings.Contains(html, wantURL) {
		t.Fatalf("expected normalized reset URL %q in bodies", wantURL)
	}
}

func TestSendRejectsUnencryptedWhenSTARTTLSIsMandatory(t *testing.T) {
	server := startTestSMTPServer(t)
	m := newTestMailer(t, config.Mail{
		Enabled:  true,
		Host:     "mail-smartpot",
		Port:     server.port(),
		From:     testFrom,
		SMTPAuth: true,
		STARTTLS: true,
		Username: "smartpot",
		Password: "smtp-secret-password",
	}, testWebBaseURL)

	if err := m.send("ana@smartpot.app", "Welcome to SmartPot", welcome("Ana", testWebBaseURL+"/login")); err == nil {
		t.Fatal("expected error when STARTTLS is mandatory and server does not advertise STARTTLS")
	}
}
