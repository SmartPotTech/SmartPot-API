package notifications

import (
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/httpserver"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/events"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/security"
	"github.com/labstack/echo/v4"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

const handlerTestSecret = "0123456789abcdef0123456789abcdef"

func newHandlerEngine(t *testing.T, repo Repository) (*echo.Echo, *security.TokenService) {
	t.Helper()
	logger := slog.New(slog.NewTextHandler(io.Discard, nil))
	svc := newTestService(repo, events.NewBus(), fixedClock)
	tokens := security.NewTokenService(handlerTestSecret, time.Hour)
	e := httpserver.New(logger)
	v1 := e.Group("/api/v1")
	authed := v1.Group("")
	authed.Use(tokens.Guard())
	NewHandler(svc).Register(authed)
	return e, tokens
}

func authenticatedRequest(t *testing.T, e *echo.Echo, tokens *security.TokenService,
	method, target, body, userID string) *httptest.ResponseRecorder {
	t.Helper()
	issued, err := tokens.Issue(userID, "user@smartpot.app", security.RoleUser)
	if err != nil {
		t.Fatal(err)
	}
	var reader io.Reader
	if body != "" {
		reader = strings.NewReader(body)
	}
	req := httptest.NewRequest(method, target, reader)
	if body != "" {
		req.Header.Set(echo.HeaderContentType, echo.MIMEApplicationJSON)
	}
	req.AddCookie(&http.Cookie{Name: security.SessionCookieName, Value: issued.Value})
	csrf := tokens.CSRFToken(issued.Value)
	req.AddCookie(&http.Cookie{Name: security.CSRFCookieName, Value: csrf})
	req.Header.Set(security.CSRFHeaderName, csrf)
	rec := httptest.NewRecorder()
	e.ServeHTTP(rec, req)
	return rec
}

func TestHandlerRequiresSession(t *testing.T) {
	e, _ := newHandlerEngine(t, &fakeRepo{})

	req := httptest.NewRequest(http.MethodGet, "/api/v1/notifications", nil)
	rec := httptest.NewRecorder()
	e.ServeHTTP(rec, req)
	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("GET without session = %d, want 401: %s", rec.Code, rec.Body.String())
	}
}

func TestHandlerRejectsWritesWithoutCSRF(t *testing.T) {
	repo := &fakeRepo{find: &Notification{ID: primitive.NewObjectID(), Type: TypeInfo}}
	e, tokens := newHandlerEngine(t, repo)

	issued, err := tokens.Issue(primitive.NewObjectID().Hex(), "user@smartpot.app", security.RoleUser)
	if err != nil {
		t.Fatal(err)
	}
	req := httptest.NewRequest(http.MethodPut, "/api/v1/notifications/"+repo.find.ID.Hex()+"/read", nil)
	req.AddCookie(&http.Cookie{Name: security.SessionCookieName, Value: issued.Value})
	rec := httptest.NewRecorder()
	e.ServeHTTP(rec, req)
	if rec.Code != http.StatusForbidden {
		t.Fatalf("PUT without CSRF = %d, want 403: %s", rec.Code, rec.Body.String())
	}
}

func TestHandlerList(t *testing.T) {
	userID := primitive.NewObjectID()
	repo := &fakeRepo{items: []Notification{
		{ID: primitive.NewObjectID(), UserID: userID, Type: TypeAlert, Title: "Unread"},
		{ID: primitive.NewObjectID(), UserID: userID, Type: TypeInfo, Title: "Read", Read: true},
	}}
	e, tokens := newHandlerEngine(t, repo)

	rec := authenticatedRequest(t, e, tokens, http.MethodGet, "/api/v1/notifications?unreadOnly=true&limit=0", "", userID.Hex())
	if rec.Code != http.StatusOK {
		t.Fatalf("GET /notifications = %d: %s", rec.Code, rec.Body.String())
	}
	var items []Response
	if err := json.Unmarshal(rec.Body.Bytes(), &items); err != nil {
		t.Fatalf("invalid JSON: %v", err)
	}
	if len(items) != 1 || items[0].Title != "Unread" {
		t.Fatalf("unreadOnly must filter read items: %+v", items)
	}
	if repo.listLimit != 1 {
		t.Fatalf("limit=0 must be clamped to 1, got %d", repo.listLimit)
	}
	if !repo.listUnreadOnly {
		t.Fatal("unreadOnly=true was not forwarded to the service")
	}

	rec = authenticatedRequest(t, e, tokens, http.MethodGet, "/api/v1/notifications?limit=500", "", userID.Hex())
	if rec.Code != http.StatusOK {
		t.Fatalf("GET /notifications = %d: %s", rec.Code, rec.Body.String())
	}
	if repo.listLimit != 100 {
		t.Fatalf("limit=500 must be clamped to 100, got %d", repo.listLimit)
	}
	if repo.listUnreadOnly {
		t.Fatal("unreadOnly must default to false")
	}
}

func TestHandlerListRejectsInvalidQuery(t *testing.T) {
	e, tokens := newHandlerEngine(t, &fakeRepo{})
	userID := primitive.NewObjectID().Hex()

	cases := []string{
		"/api/v1/notifications?unreadOnly=perhaps",
		"/api/v1/notifications?limit=not-a-number",
	}
	for _, target := range cases {
		rec := authenticatedRequest(t, e, tokens, http.MethodGet, target, "", userID)
		if rec.Code != http.StatusBadRequest {
			t.Fatalf("GET %s = %d, want 400: %s", target, rec.Code, rec.Body.String())
		}
	}
}

func TestHandlerUnreadCount(t *testing.T) {
	e, tokens := newHandlerEngine(t, &fakeRepo{countUnread: 4})

	rec := authenticatedRequest(t, e, tokens, http.MethodGet, "/api/v1/notifications/unread-count", "", primitive.NewObjectID().Hex())
	if rec.Code != http.StatusOK {
		t.Fatalf("GET /notifications/unread-count = %d: %s", rec.Code, rec.Body.String())
	}
	var body map[string]int64
	if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
		t.Fatalf("invalid JSON: %v", err)
	}
	if body["unread"] != 4 {
		t.Fatalf("unread = %d, want 4", body["unread"])
	}
}

func TestHandlerMarkRead(t *testing.T) {
	userID := primitive.NewObjectID()
	notification := &Notification{ID: primitive.NewObjectID(), UserID: userID, Type: TypeAlert, Title: "Alert"}
	repo := &fakeRepo{find: notification}
	e, tokens := newHandlerEngine(t, repo)

	rec := authenticatedRequest(t, e, tokens, http.MethodPut, "/api/v1/notifications/"+notification.ID.Hex()+"/read", "", userID.Hex())
	if rec.Code != http.StatusOK {
		t.Fatalf("PUT /notifications/:id/read = %d: %s", rec.Code, rec.Body.String())
	}
	var body Response
	if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
		t.Fatalf("invalid JSON: %v", err)
	}
	if !body.Read || body.ID != notification.ID.Hex() {
		t.Fatalf("unexpected response: %+v", body)
	}
}

func TestHandlerMarkReadReturnsNotFoundForForeignNotification(t *testing.T) {
	repo := &fakeRepo{find: nil}
	e, tokens := newHandlerEngine(t, repo)

	rec := authenticatedRequest(t, e, tokens, http.MethodPut,
		"/api/v1/notifications/"+primitive.NewObjectID().Hex()+"/read", "", primitive.NewObjectID().Hex())
	if rec.Code != http.StatusNotFound {
		t.Fatalf("foreign notification = %d, want 404: %s", rec.Code, rec.Body.String())
	}

	rec = authenticatedRequest(t, e, tokens, http.MethodPut, "/api/v1/notifications/not-an-id/read", "", primitive.NewObjectID().Hex())
	if rec.Code != http.StatusNotFound {
		t.Fatalf("invalid id = %d, want 404: %s", rec.Code, rec.Body.String())
	}
}

func TestHandlerMarkAllRead(t *testing.T) {
	repo := &fakeRepo{}
	e, tokens := newHandlerEngine(t, repo)

	rec := authenticatedRequest(t, e, tokens, http.MethodPut, "/api/v1/notifications/read-all", "", primitive.NewObjectID().Hex())
	if rec.Code != http.StatusNoContent {
		t.Fatalf("PUT /notifications/read-all = %d: %s", rec.Code, rec.Body.String())
	}
	if repo.markAllCalls != 1 {
		t.Fatalf("MarkAllRead calls = %d, want 1", repo.markAllCalls)
	}
}

func TestHandlerDelete(t *testing.T) {
	userID := primitive.NewObjectID()
	repo := &fakeRepo{deleteResult: 1}
	e, tokens := newHandlerEngine(t, repo)

	rec := authenticatedRequest(t, e, tokens, http.MethodDelete,
		"/api/v1/notifications/"+primitive.NewObjectID().Hex(), "", userID.Hex())
	if rec.Code != http.StatusNoContent {
		t.Fatalf("DELETE /notifications/:id = %d: %s", rec.Code, rec.Body.String())
	}

	// El repositorio no borra notificaciones de otra cuenta (BOLA) y el handler responde 404.
	repo.deleteResult = 0
	rec = authenticatedRequest(t, e, tokens, http.MethodDelete,
		"/api/v1/notifications/"+primitive.NewObjectID().Hex(), "", userID.Hex())
	if rec.Code != http.StatusNotFound {
		t.Fatalf("foreign notification = %d, want 404: %s", rec.Code, rec.Body.String())
	}

	rec = authenticatedRequest(t, e, tokens, http.MethodDelete, "/api/v1/notifications/not-an-id", "", userID.Hex())
	if rec.Code != http.StatusNotFound {
		t.Fatalf("invalid id = %d, want 404: %s", rec.Code, rec.Body.String())
	}
}
