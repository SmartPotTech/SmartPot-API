package auth

import (
	"bytes"
	"context"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
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
		t.Fatalf("JSON inválido: %v", err)
	}
	if _, hasToken := rawJSON["token"]; hasToken {
		t.Fatalf("el JSON de registro no debe exponer el campo token: %v", rawJSON)
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
		t.Fatalf("cookie de sesión con remember=false inválida: %+v", sessionCookie)
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
