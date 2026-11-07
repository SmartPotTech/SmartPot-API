package auth

import (
	"bytes"
	"context"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/cache"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/config"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/httpserver"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/mail"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/notifications"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/events"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/security"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/users"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

type fakeUserRepo struct {
	byEmail map[string]*users.User
	byID    map[string]*users.User
}

func newFakeUserRepo() *fakeUserRepo {
	return &fakeUserRepo{byEmail: map[string]*users.User{}, byID: map[string]*users.User{}}
}

func (r *fakeUserRepo) FindByEmail(_ context.Context, email string) (*users.User, error) {
	return r.byEmail[email], nil
}

func (r *fakeUserRepo) ExistsByEmail(_ context.Context, email string) (bool, error) {
	_, ok := r.byEmail[email]
	return ok, nil
}

func (r *fakeUserRepo) FindByID(_ context.Context, id string) (*users.User, error) {
	return r.byID[id], nil
}

func (r *fakeUserRepo) Insert(_ context.Context, u *users.User) error {
	if u.ID.IsZero() {
		u.ID = primitive.NewObjectID()
	}
	r.byEmail[u.Email] = u
	r.byID[u.HexID()] = u
	return nil
}

func (r *fakeUserRepo) Update(_ context.Context, u *users.User) error {
	r.byEmail[u.Email] = u
	r.byID[u.HexID()] = u
	return nil
}

func (r *fakeUserRepo) Delete(_ context.Context, id primitive.ObjectID) error {
	if u := r.byID[id.Hex()]; u != nil {
		delete(r.byEmail, u.Email)
		delete(r.byID, id.Hex())
	}
	return nil
}

type fakeTokenRepo struct{}

func (fakeTokenRepo) FindByTokenHash(context.Context, string) (*PasswordResetToken, error) {
	return nil, nil
}
func (fakeTokenRepo) Insert(context.Context, *PasswordResetToken) error { return nil }
func (fakeTokenRepo) DeleteByUserID(context.Context, string) error      { return nil }
func (fakeTokenRepo) DeleteExpired(context.Context, time.Time) (int64, error) {
	return 0, nil
}

type fakeNotificationRepo struct{}

func (fakeNotificationRepo) Insert(context.Context, *notifications.Notification) error { return nil }
func (fakeNotificationRepo) List(context.Context, primitive.ObjectID, bool, int) ([]notifications.Notification, error) {
	return nil, nil
}
func (fakeNotificationRepo) CountUnread(context.Context, primitive.ObjectID) (int64, error) {
	return 0, nil
}
func (fakeNotificationRepo) FindByIDAndUser(context.Context, primitive.ObjectID, primitive.ObjectID) (*notifications.Notification, error) {
	return nil, nil
}
func (fakeNotificationRepo) SetRead(context.Context, primitive.ObjectID) error     { return nil }
func (fakeNotificationRepo) MarkAllRead(context.Context, primitive.ObjectID) error { return nil }
func (fakeNotificationRepo) DeleteByIDAndUser(context.Context, primitive.ObjectID, primitive.ObjectID) (int64, error) {
	return 0, nil
}
func (fakeNotificationRepo) DeleteByUser(context.Context, primitive.ObjectID) error { return nil }
func (fakeNotificationRepo) DeleteByCrop(context.Context, primitive.ObjectID) error { return nil }
func (fakeNotificationRepo) FindByCropCreatedAfter(context.Context, primitive.ObjectID, time.Time) ([]notifications.Notification, error) {
	return nil, nil
}
func (fakeNotificationRepo) CountByCropTypeCreatedAfter(context.Context, primitive.ObjectID, notifications.Type, time.Time) (int64, error) {
	return 0, nil
}
func (fakeNotificationRepo) DeleteOlderThan(context.Context, time.Time) (int64, error) {
	return 0, nil
}

func TestAuthCookieLifecycle(t *testing.T) {
	logger := slog.New(slog.NewTextHandler(io.Discard, nil))
	clock := func() time.Time { return time.Now().UTC() }
	userRepo := newFakeUserRepo()
	jwtSvc := security.NewTokenService("0123456789abcdef0123456789abcdef", time.Hour)
	mailer, _ := mail.NewMailer(config.Mail{Enabled: false}, "http://localhost:5173", logger)
	notifSvc := notifications.NewService(fakeNotificationRepo{}, cache.NewStore(nil), events.NewBus(), clock)
	svc := NewService(userRepo, fakeTokenRepo{}, jwtSvc, mailer, notifSvc, clock, 30*time.Minute)

	e := httpserver.New(logger)
	v1 := e.Group("/api/v1")
	h := NewHandler(svc)
	h.Register(v1)
	authed := v1.Group("")
	authed.Use(jwtSvc.Guard())
	h.RegisterAuthed(authed)

	// 1. Registro emite cookie HttpOnly smartpot_session y cookie CSRF, y nunca expone "token" en JSON.
	regBody, _ := json.Marshal(map[string]any{
		"name":     "Ana",
		"lastName": "Demo",
		"email":    "ana@smartpot.app",
		"password": "Password2026",
	})
	regRec := httptest.NewRecorder()
	regReq := httptest.NewRequest(http.MethodPost, "/api/v1/auth/register", bytes.NewReader(regBody))
	regReq.Header.Set("Content-Type", "application/json")
	e.ServeHTTP(regRec, regReq)
	if regRec.Code != http.StatusCreated {
		t.Fatalf("registro = %d: %s", regRec.Code, regRec.Body.String())
	}
	var rawJSON map[string]any
	if err := json.Unmarshal(regRec.Body.Bytes(), &rawJSON); err != nil {
		t.Fatalf("invalid JSON: %v", err)
	}
	if _, hasToken := rawJSON["token"]; hasToken {
		t.Fatalf("registration JSON must not expose token field: %v", rawJSON)
	}

	// 2. Login con remember=false emite cookie de sesión (MaxAge == 0) y funciona en /api/v1/auth/session.
	loginBody, _ := json.Marshal(map[string]any{
		"email":    "ana@smartpot.app",
		"password": "Password2026",
		"remember": false,
	})
	loginRec := httptest.NewRecorder()
	loginReq := httptest.NewRequest(http.MethodPost, "/api/v1/auth/login", bytes.NewReader(loginBody))
	loginReq.Header.Set("Content-Type", "application/json")
	e.ServeHTTP(loginRec, loginReq)
	if loginRec.Code != http.StatusOK {
		t.Fatalf("login = %d: %s", loginRec.Code, loginRec.Body.String())
	}
	var sessionCookie *http.Cookie
	for _, ck := range loginRec.Result().Cookies() {
		if ck.Name == security.SessionCookieName {
			sessionCookie = ck
		}
	}
	if sessionCookie == nil || !sessionCookie.HttpOnly || sessionCookie.MaxAge != 0 {
		t.Fatalf("invalid session cookie with remember=false: %+v", sessionCookie)
	}

	sessRec := httptest.NewRecorder()
	sessReq := httptest.NewRequest(http.MethodGet, "/api/v1/auth/session", nil)
	sessReq.AddCookie(sessionCookie)
	e.ServeHTTP(sessRec, sessReq)
	if sessRec.Code != http.StatusOK {
		t.Fatalf("GET /api/v1/auth/session = %d: %s", sessRec.Code, sessRec.Body.String())
	}

	// 3. Logout limpia las cookies.
	logoutRec := httptest.NewRecorder()
	logoutReq := httptest.NewRequest(http.MethodPost, "/api/v1/auth/logout", nil)
	e.ServeHTTP(logoutRec, logoutReq)
	if logoutRec.Code != http.StatusNoContent {
		t.Fatalf("POST /api/v1/auth/logout = %d", logoutRec.Code)
	}
}

func TestAuthHandlerContracts(t *testing.T) {
	f := newAuthFixture(t, false)
	e := f.engine()
	post := func(path, body string) *httptest.ResponseRecorder {
		rec := httptest.NewRecorder()
		req := httptest.NewRequest(http.MethodPost, path, strings.NewReader(body))
		req.Header.Set("Content-Type", "application/json")
		e.ServeHTTP(rec, req)
		return rec
	}

	// 1. La validación del DTO responde 400 con el detalle del campo.
	invalid := post("/api/v1/auth/register", `{"name":"A","lastName":"Demo","email":"ana@smartpot.app","password":"Password2026"}`)
	if invalid.Code != http.StatusBadRequest {
		t.Fatalf("invalid register = %d, want 400: %s", invalid.Code, invalid.Body.String())
	}
	var validationBody struct {
		Fields map[string]string `json:"fields"`
	}
	if err := json.Unmarshal(invalid.Body.Bytes(), &validationBody); err != nil {
		t.Fatalf("invalid register body is not JSON: %v", err)
	}
	if _, ok := validationBody.Fields["name"]; !ok {
		t.Errorf("validation fields = %v, want a name error", validationBody.Fields)
	}

	// 2. El registro emite la cookie HttpOnly y la cookie CSRF de doble envío.
	registered := post("/api/v1/auth/register",
		`{"name":"Ana","lastName":"Demo","email":"ana@smartpot.app","password":"Password2026"}`)
	if registered.Code != http.StatusCreated {
		t.Fatalf("register = %d: %s", registered.Code, registered.Body.String())
	}
	var session, csrf *http.Cookie
	for _, ck := range registered.Result().Cookies() {
		switch ck.Name {
		case security.SessionCookieName:
			session = ck
		case security.CSRFCookieName:
			csrf = ck
		}
	}
	if session == nil || !session.HttpOnly {
		t.Fatalf("the session cookie must be HttpOnly: %+v", session)
	}
	if csrf == nil || csrf.HttpOnly {
		t.Fatalf("the CSRF cookie must be readable by the client: %+v", csrf)
	}
	if got, want := registered.Header().Get(security.CSRFHeaderName), csrf.Value; got != want {
		t.Errorf("X-CSRF-Token = %q, want the CSRF cookie value %q", got, want)
	}
	if want := f.jwt.CSRFToken(session.Value); csrf.Value != want {
		t.Errorf("the CSRF cookie is not bound to the session token")
	}

	// 3. Un correo ya registrado responde 409.
	duplicate := post("/api/v1/auth/register",
		`{"name":"Ana","lastName":"Demo","email":"ANA@smartpot.app","password":"Password2026"}`)
	if duplicate.Code != http.StatusConflict {
		t.Errorf("duplicate register = %d, want 409: %s", duplicate.Code, duplicate.Body.String())
	}

	// 4. Credenciales incorrectas y DTO inválido.
	wrong := post("/api/v1/auth/login", `{"email":"ana@smartpot.app","password":"WrongPassword2026"}`)
	if wrong.Code != http.StatusUnauthorized {
		t.Errorf("wrong password = %d, want 401", wrong.Code)
	}
	missing := post("/api/v1/auth/login", `{"email":"ana@smartpot.app"}`)
	if missing.Code != http.StatusBadRequest {
		t.Errorf("login without password = %d, want 400", missing.Code)
	}

	// 5. La recuperación responde 202 aunque la cuenta no exista.
	unknown := post("/api/v1/auth/password/forgot", `{"email":"nobody@smartpot.app"}`)
	if unknown.Code != http.StatusAccepted {
		t.Errorf("forgot for an unknown email = %d, want 202: %s", unknown.Code, unknown.Body.String())
	}
	known := post("/api/v1/auth/password/forgot", `{"email":"ANA@smartpot.app"}`)
	if known.Code != http.StatusAccepted {
		t.Errorf("forgot for a known email = %d, want 202: %s", known.Code, known.Body.String())
	}
	if got := f.tokens.count(); got != 1 {
		t.Errorf("stored reset tokens = %d, want 1", got)
	}

	// 6. El restablecimiento valida el token y la política de contraseña.
	u := f.users.byEmail["ana@smartpot.app"]
	if err := f.tokens.Insert(context.Background(), &PasswordResetToken{
		TokenHash: sha256Hex("handler-reset-token"),
		UserID:    u.ID,
		CreatedAt: f.now,
		ExpiresAt: f.now.Add(time.Hour),
	}); err != nil {
		t.Fatal(err)
	}
	weak := post("/api/v1/auth/password/reset", `{"token":"handler-reset-token","password":"weak"}`)
	if weak.Code != http.StatusBadRequest {
		t.Errorf("weak reset password = %d, want 400: %s", weak.Code, weak.Body.String())
	}
	missingPassword := post("/api/v1/auth/password/reset", `{"token":"handler-reset-token"}`)
	if missingPassword.Code != http.StatusBadRequest {
		t.Errorf("reset without password = %d, want 400", missingPassword.Code)
	}
	expired := post("/api/v1/auth/password/reset", `{"token":"unknown-token","password":"NewPassword2026"}`)
	if expired.Code != http.StatusBadRequest {
		t.Errorf("unknown reset token = %d, want 400", expired.Code)
	}
	valid := post("/api/v1/auth/password/reset", `{"token":"handler-reset-token","password":"NewPassword2026"}`)
	if valid.Code != http.StatusNoContent {
		t.Fatalf("reset = %d, want 204: %s", valid.Code, valid.Body.String())
	}
	reuse := post("/api/v1/auth/password/reset", `{"token":"handler-reset-token","password":"AnotherPassword2026"}`)
	if reuse.Code != http.StatusBadRequest {
		t.Errorf("reusing a reset token = %d, want 400", reuse.Code)
	}
	if _, err := f.service.Login(context.Background(), LoginRequest{
		Email:    "ana@smartpot.app",
		Password: "NewPassword2026",
	}); err != nil {
		t.Errorf("the new password must work after the reset: %v", err)
	}
}
