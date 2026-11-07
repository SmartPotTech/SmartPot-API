package actuators

import (
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/httpserver"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/security"
	"github.com/labstack/echo/v4"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

const actuatorsHandlerSecret = "0123456789abcdef0123456789abcdef"

func newActuatorsEngine(t *testing.T, repo Repository) (*echo.Echo, *security.TokenService) {
	t.Helper()
	svc := NewService(repo, fakeCrops{}, func() time.Time {
		return time.Date(2026, 11, 6, 12, 0, 0, 0, time.UTC)
	})
	tokens := security.NewTokenService(actuatorsHandlerSecret, time.Hour)
	logger := slog.New(slog.NewTextHandler(io.Discard, nil))
	e := httpserver.New(logger)
	v1 := e.Group("/api/v1")
	authed := v1.Group("")
	authed.Use(tokens.Guard())
	NewHandler(svc, time.Now).Register(authed)
	return e, tokens
}

func actuatorsRequest(t *testing.T, e *echo.Echo, tokens *security.TokenService,
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

func TestActuatorsHandlerRequiresSession(t *testing.T) {
	e, _ := newActuatorsEngine(t, &fakeRepo{})
	req := httptest.NewRequest(http.MethodGet, "/api/v1/crops/"+primitive.NewObjectID().Hex()+"/actuators", nil)
	rec := httptest.NewRecorder()
	e.ServeHTTP(rec, req)
	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("GET without session = %d, want 401: %s", rec.Code, rec.Body.String())
	}
}

func TestActuatorsHandlerRejectsInvalidType(t *testing.T) {
	e, tokens := newActuatorsEngine(t, &fakeRepo{})
	cropID := primitive.NewObjectID().Hex()
	userID := primitive.NewObjectID().Hex()

	for _, body := range []string{`{"type":"BOGUS"}`, `{}`, `not-json`} {
		rec := actuatorsRequest(t, e, tokens, http.MethodPost, "/api/v1/crops/"+cropID+"/actuators", body, userID)
		if rec.Code != http.StatusBadRequest {
			t.Fatalf("POST %s = %d, want 400: %s", body, rec.Code, rec.Body.String())
		}
	}
}

func TestActuatorsHandlerAddAndDuplicate(t *testing.T) {
	repo := &fakeRepo{}
	e, tokens := newActuatorsEngine(t, repo)
	cropID := primitive.NewObjectID().Hex()
	userID := primitive.NewObjectID().Hex()

	rec := actuatorsRequest(t, e, tokens, http.MethodPost, "/api/v1/crops/"+cropID+"/actuators", `{"type":"WATER_PUMP"}`, userID)
	if rec.Code != http.StatusCreated {
		t.Fatalf("POST actuator = %d: %s", rec.Code, rec.Body.String())
	}
	if len(repo.inserted) != 1 || repo.inserted[0].Type != WaterPump {
		t.Fatalf("actuator was not inserted: %+v", repo.inserted)
	}

	repo.duplicate = true
	rec = actuatorsRequest(t, e, tokens, http.MethodPost, "/api/v1/crops/"+cropID+"/actuators", `{"type":"WATER_PUMP"}`, userID)
	if rec.Code != http.StatusConflict {
		t.Fatalf("duplicate actuator = %d, want 409: %s", rec.Code, rec.Body.String())
	}
}

func TestActuatorsHandlerRemoveMissing(t *testing.T) {
	e, tokens := newActuatorsEngine(t, &fakeRepo{})
	cropID := primitive.NewObjectID().Hex()
	userID := primitive.NewObjectID().Hex()

	rec := actuatorsRequest(t, e, tokens, http.MethodDelete,
		"/api/v1/crops/"+cropID+"/actuators/"+primitive.NewObjectID().Hex(), "", userID)
	if rec.Code != http.StatusNotFound {
		t.Fatalf("missing actuator = %d, want 404: %s", rec.Code, rec.Body.String())
	}
	rec = actuatorsRequest(t, e, tokens, http.MethodDelete,
		"/api/v1/crops/"+cropID+"/actuators/not-an-id", "", userID)
	if rec.Code != http.StatusNotFound {
		t.Fatalf("invalid actuator id = %d, want 404: %s", rec.Code, rec.Body.String())
	}
}

func TestActuatorsHandlerListIsEmptyArray(t *testing.T) {
	e, tokens := newActuatorsEngine(t, &fakeRepo{})
	rec := actuatorsRequest(t, e, tokens, http.MethodGet,
		"/api/v1/crops/"+primitive.NewObjectID().Hex()+"/actuators", "", primitive.NewObjectID().Hex())
	if rec.Code != http.StatusOK {
		t.Fatalf("GET actuators = %d: %s", rec.Code, rec.Body.String())
	}
	if body := strings.TrimSpace(rec.Body.String()); body != "[]" {
		t.Fatalf("empty list must serialize as []: %q", body)
	}
}
