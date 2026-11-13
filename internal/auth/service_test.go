package auth

import (
	"bufio"
	"context"
	"encoding/base64"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"mime"
	"mime/multipart"
	"mime/quotedprintable"
	"net"
	netmail "net/mail"
	"net/textproto"
	"regexp"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/cache"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/config"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/httpserver"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/mail"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/notifications"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/events"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/security"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/users"
	"github.com/labstack/echo/v4"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

const (
	authTestWebBaseURL = "https://smartpot.app"
	authTestFrom       = "no-reply@smartpot.app"
	authTestSecret     = "0123456789abcdef0123456789abcdef"
	authTestPassword   = "Password2026"
)

// memTokenRepo guarda los tokens de recuperación en memoria, con el mismo
// contrato que el adaptador de PostgreSQL.
type memTokenRepo struct {
	mu      sync.Mutex
	tokens  map[string]PasswordResetToken
	inserts int
	deletes int
}

func newMemTokenRepo() *memTokenRepo {
	return &memTokenRepo{tokens: map[string]PasswordResetToken{}}
}

func (r *memTokenRepo) FindByTokenHash(_ context.Context, hash string) (*PasswordResetToken, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	t, ok := r.tokens[hash]
	if !ok {
		return nil, nil
	}
	return &t, nil
}

func (r *memTokenRepo) ConsumeByTokenHash(_ context.Context, hash string, now time.Time) (*PasswordResetToken, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	t, ok := r.tokens[hash]
	if !ok || !t.ExpiresAt.After(now) {
		return nil, nil
	}
	delete(r.tokens, hash)
	r.deletes++
	return &t, nil
}

func (r *memTokenRepo) Insert(_ context.Context, t *PasswordResetToken) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.tokens[t.TokenHash] = *t
	r.inserts++
	return nil
}

func (r *memTokenRepo) DeleteByUserID(_ context.Context, userID string) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	for hash, t := range r.tokens {
		if t.UserID.Hex() == userID {
			delete(r.tokens, hash)
			r.deletes++
		}
	}
	return nil
}

func (r *memTokenRepo) DeleteExpired(_ context.Context, now time.Time) (int64, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	var removed int64
	for hash, t := range r.tokens {
		if !t.ExpiresAt.After(now) {
			delete(r.tokens, hash)
			removed++
		}
	}
	return removed, nil
}

func (r *memTokenRepo) count() int {
	r.mu.Lock()
	defer r.mu.Unlock()
	return len(r.tokens)
}

func (r *memTokenRepo) tokenByHash(hash string) (PasswordResetToken, bool) {
	r.mu.Lock()
	defer r.mu.Unlock()
	t, ok := r.tokens[hash]
	return t, ok
}

// recordingNotifications cuenta las notificaciones creadas sin cambiar el resto
// del repositorio falso compartido con las pruebas del handler.
type recordingNotifications struct {
	fakeNotificationRepo
	inserted []notifications.Notification
}

func (r *recordingNotifications) Insert(_ context.Context, n *notifications.Notification) error {
	r.inserted = append(r.inserted, *n)
	return nil
}

// capturedMail es un mensaje recibido por el servidor SMTP de prueba.
type capturedMail struct {
	from     string
	to       []string
	data     string
	authLine string
}

type testSMTPServer struct {
	ln       net.Listener
	messages chan capturedMail
	accepted atomic.Int64
	mu       sync.Mutex
	closed   bool
}

func startTestSMTPServer(t *testing.T) *testSMTPServer {
	t.Helper()
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("listen on loopback: %v", err)
	}
	s := &testSMTPServer{ln: ln, messages: make(chan capturedMail, 16)}
	go s.serve()
	t.Cleanup(func() {
		s.mu.Lock()
		s.closed = true
		s.mu.Unlock()
		_ = ln.Close()
	})
	return s
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

func (s *testSMTPServer) nextWithSubject(t *testing.T, subject string) capturedMail {
	t.Helper()
	deadline := time.Now().Add(5 * time.Second)
	for time.Now().Before(deadline) {
		msg := s.next(t)
		header, err := netmail.ReadMessage(strings.NewReader(msg.data))
		if err != nil {
			t.Fatalf("read message: %v", err)
		}
		if header.Header.Get("Subject") == subject {
			return msg
		}
	}
	t.Fatalf("no message with subject %q", subject)
	return capturedMail{}
}

// decodeMessage devuelve los cuerpos text/plain y text/html ya decodificados.
func decodeMessage(t *testing.T, raw string) (plain, html string) {
	t.Helper()
	msg, err := netmail.ReadMessage(strings.NewReader(raw))
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

var resetTokenPattern = regexp.MustCompile(`reset-password\?token=([A-Za-z0-9_-]+)`)

func resetTokenFromMessage(t *testing.T, raw string) string {
	t.Helper()
	plain, html := decodeMessage(t, raw)
	match := resetTokenPattern.FindStringSubmatch(plain + "\n" + html)
	if len(match) != 2 {
		t.Fatalf("reset token not found in the message body")
	}
	return match[1]
}

type authFixture struct {
	service       *Service
	users         *fakeUserRepo
	tokens        *memTokenRepo
	notifications *recordingNotifications
	jwt           *security.TokenService
	smtp          *testSMTPServer
	now           time.Time
	passwordHash  string
}

func newAuthFixture(t *testing.T, withSMTP bool) *authFixture {
	t.Helper()
	now := time.Date(2026, 11, 6, 12, 0, 0, 0, time.UTC)
	logger := slog.New(slog.NewTextHandler(io.Discard, nil))
	hash, err := security.HashPassword(authTestPassword)
	if err != nil {
		t.Fatalf("hash seed password: %v", err)
	}

	f := &authFixture{
		users:         newFakeUserRepo(),
		tokens:        newMemTokenRepo(),
		notifications: &recordingNotifications{},
		jwt:           security.NewTokenService(authTestSecret, time.Hour),
		now:           now,
		passwordHash:  hash,
	}
	f.jwt.UseSessionStore(cache.NewStore(nil))
	clock := func() time.Time { return now }

	var mailer *mail.Mailer
	if withSMTP {
		f.smtp = startTestSMTPServer(t)
		mailer, err = mail.NewMailer(config.Mail{
			Enabled: true,
			Host:    "127.0.0.1",
			Port:    f.smtp.port(),
			From:    authTestFrom,
		}, authTestWebBaseURL, logger)
	} else {
		mailer, err = mail.NewMailer(config.Mail{Enabled: false}, authTestWebBaseURL, logger)
	}
	if err != nil {
		t.Fatalf("NewMailer: %v", err)
	}

	notificationService := notifications.NewService(f.notifications, cache.NewStore(nil), events.NewBus(), clock)
	f.service = NewService(f.users, f.tokens, f.jwt, mailer, notificationService, clock, 30*time.Minute)
	return f
}

func (f *authFixture) seedUser(t *testing.T, email string) *users.User {
	t.Helper()
	u := &users.User{
		ID:           primitive.NewObjectID(),
		Name:         "Ana",
		LastName:     "Demo",
		Email:        users.NormalizeEmail(email),
		PasswordHash: f.passwordHash,
		Role:         users.RoleUser,
		CreatedAt:    f.now,
		UpdatedAt:    f.now,
	}
	if err := f.users.Insert(context.Background(), u); err != nil {
		t.Fatalf("seed user: %v", err)
	}
	return u
}

func (f *authFixture) engine() *echo.Echo {
	logger := slog.New(slog.NewTextHandler(io.Discard, nil))
	e := httpserver.New(logger)
	v1 := e.Group("/api/v1")
	h := NewHandler(f.service)
	h.Register(v1)
	authed := v1.Group("")
	authed.Use(f.jwt.Guard())
	h.RegisterAuthed(authed)
	return e
}

func statusOf(err error) int {
	var apiErr *apperr.Error
	if errors.As(err, &apiErr) {
		return apiErr.Status
	}
	return 0
}

func TestRegisterCreatesUserAndWelcomeNotification(t *testing.T) {
	f := newAuthFixture(t, false)

	resp, err := f.service.Register(context.Background(), RegisterRequest{
		Name:     " Ana ",
		LastName: " Demo ",
		Email:    " ANA@SmartPot.APP ",
		Password: authTestPassword,
	})
	if err != nil {
		t.Fatalf("Register: %v", err)
	}

	if resp.User.Email != "ana@smartpot.app" {
		t.Errorf("Email = %q, want the normalized address", resp.User.Email)
	}
	if resp.User.Name != "Ana" || resp.User.LastName != "Demo" {
		t.Errorf("names must be trimmed: %+v", resp.User)
	}
	if resp.User.Role != string(users.RoleUser) {
		t.Errorf("Role = %q, want USER", resp.User.Role)
	}
	if len(resp.User.ID) != 24 {
		t.Errorf("ID = %q, want a 24-character ObjectId", resp.User.ID)
	}

	stored := f.users.byEmail["ana@smartpot.app"]
	if stored == nil {
		t.Fatal("the user was not persisted")
	}
	if stored.PasswordHash == authTestPassword {
		t.Fatal("the password must not be stored in plaintext")
	}
	if !security.PasswordMatches(authTestPassword, stored.PasswordHash) {
		t.Errorf("the stored BCrypt hash does not match the password")
	}

	if len(f.notifications.inserted) != 1 {
		t.Fatalf("welcome notifications = %d, want 1", len(f.notifications.inserted))
	}
	welcome := f.notifications.inserted[0]
	if welcome.Title != "Welcome to SmartPot!" || welcome.UserID.Hex() != stored.HexID() {
		t.Errorf("unexpected welcome notification: %+v", welcome)
	}

	claims, err := f.jwt.Parse(resp.Issued.Value)
	if err != nil {
		t.Fatalf("the registration token must be valid: %v", err)
	}
	if claims.UserID != stored.HexID() || claims.Email != stored.Email {
		t.Errorf("unexpected token claims: %+v", claims)
	}
}

func TestRegisterRejectsDuplicateEmail(t *testing.T) {
	f := newAuthFixture(t, false)
	f.seedUser(t, "ana@smartpot.app")

	_, err := f.service.Register(context.Background(), RegisterRequest{
		Name:     "Ana",
		LastName: "Demo",
		Email:    " ANA@smartpot.app ",
		Password: authTestPassword,
	})
	if got := statusOf(err); got != 409 {
		t.Fatalf("duplicate email status = %d, want 409 (%v)", got, err)
	}
	if len(f.users.byEmail) != 1 {
		t.Errorf("the duplicate must not be inserted: %d users", len(f.users.byEmail))
	}
	if len(f.notifications.inserted) != 0 {
		t.Errorf("a duplicate registration must not create notifications")
	}
}

func TestRegisterRejectsWeakPassword(t *testing.T) {
	f := newAuthFixture(t, false)

	_, err := f.service.Register(context.Background(), RegisterRequest{
		Name:     "Ana",
		LastName: "Demo",
		Email:    "ana@smartpot.app",
		Password: "weak",
	})
	if got := statusOf(err); got != 400 {
		t.Fatalf("weak password status = %d, want 400 (%v)", got, err)
	}
	if len(f.users.byEmail) != 0 || len(f.notifications.inserted) != 0 {
		t.Error("a rejected registration must not persist anything")
	}
}

func TestLoginSucceedsAndDoesNotLeakAccountExistence(t *testing.T) {
	f := newAuthFixture(t, false)
	f.seedUser(t, "ana@smartpot.app")

	resp, err := f.service.Login(context.Background(), LoginRequest{
		Email:    " ANA@smartpot.app ",
		Password: authTestPassword,
	})
	if err != nil {
		t.Fatalf("Login: %v", err)
	}
	if resp.User.Email != "ana@smartpot.app" {
		t.Errorf("Email = %q", resp.User.Email)
	}
	claims, err := f.jwt.Parse(resp.Issued.Value)
	if err != nil || claims.UserID != resp.User.ID {
		t.Fatalf("the login token must be valid: %v, %+v", err, claims)
	}

	_, wrongPassword := f.service.Login(context.Background(), LoginRequest{
		Email:    "ana@smartpot.app",
		Password: "WrongPassword2026",
	})
	_, unknownEmail := f.service.Login(context.Background(), LoginRequest{
		Email:    "nobody@smartpot.app",
		Password: authTestPassword,
	})
	if statusOf(wrongPassword) != 401 || statusOf(unknownEmail) != 401 {
		t.Fatalf("both failures must return 401: wrong=%v unknown=%v", wrongPassword, unknownEmail)
	}
	if wrongPassword.Error() != unknownEmail.Error() {
		t.Errorf("login failures must not reveal whether the account exists: %q != %q",
			wrongPassword.Error(), unknownEmail.Error())
	}
	if wrongPassword.Error() != invalidCredentials {
		t.Errorf("message = %q, want %q", wrongPassword.Error(), invalidCredentials)
	}
}

func TestRequestPasswordResetStoresOnlyTheHash(t *testing.T) {
	f := newAuthFixture(t, true)
	user := f.seedUser(t, "ana@smartpot.app")
	ctx := context.Background()

	// Un token viejo del mismo usuario debe desaparecer al emitir el nuevo.
	old := &PasswordResetToken{
		TokenHash: sha256Hex("old-token"),
		UserID:    user.ID,
		CreatedAt: f.now.Add(-time.Hour),
		ExpiresAt: f.now.Add(time.Hour),
	}
	if err := f.tokens.Insert(ctx, old); err != nil {
		t.Fatal(err)
	}

	if err := f.service.RequestPasswordReset(ctx, " ANA@smartpot.app "); err != nil {
		t.Fatalf("RequestPasswordReset: %v", err)
	}
	first := f.smtp.nextWithSubject(t, "Reset your SmartPot password")
	if len(first.to) != 1 || first.to[0] != "ana@smartpot.app" {
		t.Errorf("recipient = %v", first.to)
	}
	token := resetTokenFromMessage(t, first.data)
	if len(token) < 32 {
		t.Errorf("the reset token is too short: %q", token)
	}

	if got := f.tokens.count(); got != 1 {
		t.Fatalf("stored tokens = %d, want only the new one", got)
	}
	if _, ok := f.tokens.tokenByHash(sha256Hex("old-token")); ok {
		t.Error("the previous token must be deleted")
	}
	stored, ok := f.tokens.tokenByHash(sha256Hex(token))
	if !ok {
		t.Fatal("the SHA-256 of the token must be stored")
	}
	if stored.TokenHash == token {
		t.Error("the raw token must never be stored")
	}
	if stored.UserID != user.ID {
		t.Errorf("token user = %s, want %s", stored.UserID.Hex(), user.HexID())
	}
	if !stored.CreatedAt.Equal(f.now) {
		t.Errorf("CreatedAt = %v, want %v", stored.CreatedAt, f.now)
	}
	if want := f.now.Add(30 * time.Minute); !stored.ExpiresAt.Equal(want) {
		t.Errorf("ExpiresAt = %v, want %v", stored.ExpiresAt, want)
	}

	if err := f.service.RequestPasswordReset(ctx, "ana@smartpot.app"); err != nil {
		t.Fatalf("second RequestPasswordReset: %v", err)
	}
	second := f.smtp.nextWithSubject(t, "Reset your SmartPot password")
	secondToken := resetTokenFromMessage(t, second.data)
	if secondToken == token {
		t.Error("each request must generate a new random token")
	}
	if got := f.tokens.count(); got != 1 {
		t.Errorf("stored tokens after the second request = %d, want 1", got)
	}
	if _, ok := f.tokens.tokenByHash(sha256Hex(token)); ok {
		t.Error("the first token must be deleted by the second request")
	}
	if _, ok := f.tokens.tokenByHash(sha256Hex(secondToken)); !ok {
		t.Error("the second token hash must be stored")
	}
}

func TestRequestPasswordResetIgnoresUnknownEmail(t *testing.T) {
	f := newAuthFixture(t, true)

	if err := f.service.RequestPasswordReset(context.Background(), "nobody@smartpot.app"); err != nil {
		t.Fatalf("an unknown email must not fail: %v", err)
	}
	if got := f.tokens.count(); got != 0 {
		t.Errorf("stored tokens = %d, want 0", got)
	}
	time.Sleep(150 * time.Millisecond)
	if got := f.smtp.accepted.Load(); got != 0 {
		t.Errorf("an unknown email opened %d SMTP connections, want 0", got)
	}
	select {
	case msg := <-f.smtp.messages:
		t.Errorf("an unknown email must not send a message: %+v", msg)
	default:
	}
}

func TestResetPasswordConsumesTokenAndRevokesSessions(t *testing.T) {
	f := newAuthFixture(t, false)
	user := f.seedUser(t, "ana@smartpot.app")
	ctx := context.Background()

	raw := "reset-token-to-consume"
	if err := f.tokens.Insert(ctx, &PasswordResetToken{
		TokenHash: sha256Hex(raw),
		UserID:    user.ID,
		CreatedAt: f.now,
		ExpiresAt: f.now.Add(30 * time.Minute),
	}); err != nil {
		t.Fatal(err)
	}
	before, err := f.jwt.Issue(user.HexID(), user.Email, string(user.Role))
	if err != nil {
		t.Fatal(err)
	}
	if _, err := f.jwt.Parse(before.Value); err != nil {
		t.Fatalf("the pre-reset token must be valid: %v", err)
	}

	if err := f.service.ResetPassword(ctx, ResetPasswordRequest{
		Token:    raw,
		Password: "NewPassword2026",
	}); err != nil {
		t.Fatalf("ResetPassword: %v", err)
	}

	stored := f.users.byID[user.HexID()]
	if !security.PasswordMatches("NewPassword2026", stored.PasswordHash) {
		t.Error("the new password must work")
	}
	if security.PasswordMatches(authTestPassword, stored.PasswordHash) {
		t.Error("the old password must stop working")
	}
	if !stored.UpdatedAt.Equal(f.now) {
		t.Errorf("UpdatedAt = %v, want %v", stored.UpdatedAt, f.now)
	}
	if got := f.tokens.count(); got != 0 {
		t.Errorf("stored tokens after reset = %d, want 0", got)
	}

	err = f.service.ResetPassword(ctx, ResetPasswordRequest{Token: raw, Password: "AnotherPassword2026"})
	if got := statusOf(err); got != 400 {
		t.Errorf("reusing a consumed token = %d, want 400 (%v)", got, err)
	}

	if _, err := f.jwt.Parse(before.Value); err == nil {
		t.Error("a token issued before the reset must be rejected")
	}
	after, err := f.jwt.Issue(user.HexID(), user.Email, string(user.Role))
	if err != nil {
		t.Fatal(err)
	}
	if _, err := f.jwt.Parse(after.Value); err != nil {
		t.Errorf("a token issued after the reset must be valid: %v", err)
	}

	if len(f.notifications.inserted) != 1 || f.notifications.inserted[0].Title != "Password updated" {
		t.Errorf("a password notification is expected: %+v", f.notifications.inserted)
	}
}

func TestResetPasswordRejectsInvalidInput(t *testing.T) {
	f := newAuthFixture(t, false)
	user := f.seedUser(t, "ana@smartpot.app")
	ctx := context.Background()

	unknown := f.service.ResetPassword(ctx, ResetPasswordRequest{Token: "not-a-real-token", Password: "NewPassword2026"})
	if got := statusOf(unknown); got != 400 {
		t.Errorf("unknown token = %d, want 400 (%v)", got, unknown)
	}

	if err := f.tokens.Insert(ctx, &PasswordResetToken{
		TokenHash: sha256Hex("expired-token"),
		UserID:    user.ID,
		CreatedAt: f.now.Add(-time.Hour),
		ExpiresAt: f.now,
	}); err != nil {
		t.Fatal(err)
	}
	expired := f.service.ResetPassword(ctx, ResetPasswordRequest{Token: "expired-token", Password: "NewPassword2026"})
	if got := statusOf(expired); got != 400 {
		t.Errorf("expired token = %d, want 400 (%v)", got, expired)
	}

	if err := f.tokens.Insert(ctx, &PasswordResetToken{
		TokenHash: sha256Hex("valid-but-weak"),
		UserID:    user.ID,
		CreatedAt: f.now,
		ExpiresAt: f.now.Add(30 * time.Minute),
	}); err != nil {
		t.Fatal(err)
	}
	weak := f.service.ResetPassword(ctx, ResetPasswordRequest{Token: "valid-but-weak", Password: "weak"})
	if got := statusOf(weak); got != 400 {
		t.Errorf("weak password = %d, want 400 (%v)", got, weak)
	}
	if !security.PasswordMatches(authTestPassword, f.users.byID[user.HexID()].PasswordHash) {
		t.Error("a rejected reset must not change the password")
	}
	if got := f.tokens.count(); got != 2 {
		t.Errorf("rejected resets must not consume tokens: %d", got)
	}
}

func TestPasswordRecoveryCycle(t *testing.T) {
	f := newAuthFixture(t, true)
	ctx := context.Background()

	if _, err := f.service.Register(ctx, RegisterRequest{
		Name:     "Ana",
		LastName: "Demo",
		Email:    "ana@smartpot.app",
		Password: authTestPassword,
	}); err != nil {
		t.Fatalf("Register: %v", err)
	}
	welcome := f.smtp.nextWithSubject(t, "Welcome to SmartPot")
	if len(welcome.to) != 1 || welcome.to[0] != "ana@smartpot.app" {
		t.Errorf("welcome recipient = %v", welcome.to)
	}

	if _, err := f.service.Login(ctx, LoginRequest{Email: "ana@smartpot.app", Password: authTestPassword}); err != nil {
		t.Fatalf("Login with the registered password: %v", err)
	}
	if err := f.service.RequestPasswordReset(ctx, "ana@smartpot.app"); err != nil {
		t.Fatalf("RequestPasswordReset: %v", err)
	}
	message := f.smtp.nextWithSubject(t, "Reset your SmartPot password")
	raw := resetTokenFromMessage(t, message.data)

	if err := f.service.ResetPassword(ctx, ResetPasswordRequest{Token: raw, Password: "NewPassword2026"}); err != nil {
		t.Fatalf("ResetPassword: %v", err)
	}
	if _, err := f.service.Login(ctx, LoginRequest{Email: "ana@smartpot.app", Password: authTestPassword}); statusOf(err) != 401 {
		t.Errorf("the old password must fail with 401: %v", err)
	}
	if _, err := f.service.Login(ctx, LoginRequest{Email: "ana@smartpot.app", Password: "NewPassword2026"}); err != nil {
		t.Errorf("the new password must work: %v", err)
	}
}

func TestResetPasswordPreventsConcurrentTokenReplay(t *testing.T) {
	f := newAuthFixture(t, false)
	user := f.seedUser(t, "ana@smartpot.app")
	ctx := context.Background()

	raw := "one-shot-concurrent-reset"
	if err := f.tokens.Insert(ctx, &PasswordResetToken{
		TokenHash: sha256Hex(raw),
		UserID:    user.ID,
		CreatedAt: f.now,
		ExpiresAt: f.now.Add(30 * time.Minute),
	}); err != nil {
		t.Fatal(err)
	}

	const workers = 8
	start := make(chan struct{})
	errs := make(chan error, workers)
	var wg sync.WaitGroup
	for i := 0; i < workers; i++ {
		wg.Add(1)
		go func(idx int) {
			defer wg.Done()
			<-start
			errs <- f.service.ResetPassword(ctx, ResetPasswordRequest{
				Token:    raw,
				Password: fmt.Sprintf("Password%04d!", idx),
			})
		}(i)
	}
	close(start)
	wg.Wait()
	close(errs)

	successes := 0
	for err := range errs {
		if err == nil {
			successes++
		}
	}
	if successes != 1 {
		t.Fatalf("exactly 1 concurrent reset must succeed, got %d", successes)
	}
}
