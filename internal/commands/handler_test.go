package commands

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
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/security"
	"github.com/labstack/echo/v4"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

const commandsHandlerSecret = "0123456789abcdef0123456789abcdef"

func newCommandsEngine(t *testing.T, repo Repository, crops Crops, actuatorsSvc Actuators,
	publisher Publisher) (*echo.Echo, *security.TokenService) {
	t.Helper()
	svc := NewService(repo, crops, actuatorsSvc, &fakeNotifier{}, time.Now, time.Minute)
	if publisher != nil {
		svc.SetPublisher(publisher)
	}
	tokens := security.NewTokenService(commandsHandlerSecret, time.Hour)
	logger := slog.New(slog.NewTextHandler(io.Discard, nil))
	e := httpserver.New(logger)
	v1 := e.Group("/api/v1")
	authed := v1.Group("")
	authed.Use(tokens.Guard())
	NewHandler(svc).Register(authed)
	return e, tokens
}

func commandsRequest(t *testing.T, e *echo.Echo, tokens *security.TokenService,
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

func TestCommandsHandlerRequiresSession(t *testing.T) {
	e, _ := newCommandsEngine(t, newFakeCommandRepo(), fakeCrops{}, &fakeActuators{}, &fakePublisher{enabled: true, ok: true})
	req := httptest.NewRequest(http.MethodGet, "/api/v1/commands", nil)
	rec := httptest.NewRecorder()
	e.ServeHTTP(rec, req)
	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("GET without session = %d, want 401: %s", rec.Code, rec.Body.String())
	}
}

func TestCommandsHandlerSendValidation(t *testing.T) {
	_, actuator, crop := testCommand()
	e, tokens := newCommandsEngine(t, newFakeCommandRepo(), fakeCrops{crop: crop}, &fakeActuators{actuator: actuator},
		&fakePublisher{enabled: true, ok: true})
	target := "/api/v1/crops/" + crop.HexID() + "/commands"
	userID := crop.OwnerID.Hex()

	for _, body := range []string{
		`{"action":"ACTIVATE"}`,
		`{"actuatorId":"` + actuator.ID.Hex() + `"}`,
		`{"actuatorId":"` + actuator.ID.Hex() + `","action":"BOGUS"}`,
		`{"actuatorId":"` + actuator.ID.Hex() + `","action":"ACTIVATE","durationSeconds":0}`,
		`not-json`,
	} {
		rec := commandsRequest(t, e, tokens, http.MethodPost, target, body, userID)
		if rec.Code != http.StatusBadRequest {
			t.Fatalf("POST %s = %d, want 400: %s", body, rec.Code, rec.Body.String())
		}
	}
}

func TestCommandsHandlerSendAccepted(t *testing.T) {
	repo := newFakeCommandRepo()
	_, actuator, crop := testCommand()
	publisher := &fakePublisher{enabled: true, ok: true}
	e, tokens := newCommandsEngine(t, repo, fakeCrops{crop: crop}, &fakeActuators{actuator: actuator}, publisher)

	body := `{"actuatorId":"` + actuator.ID.Hex() + `","action":"ACTIVATE","durationSeconds":30}`
	rec := commandsRequest(t, e, tokens, http.MethodPost, "/api/v1/crops/"+crop.HexID()+"/commands", body, crop.OwnerID.Hex())
	if rec.Code != http.StatusAccepted {
		t.Fatalf("POST command = %d, want 202: %s", rec.Code, rec.Body.String())
	}
	var res CommandResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &res); err != nil {
		t.Fatalf("invalid JSON: %v", err)
	}
	if res.Status != string(StatusSent) || res.CropID != crop.HexID() || res.Action != string(Activate) {
		t.Fatalf("unexpected command: %+v", res)
	}
	if publisher.sent != 1 {
		t.Fatalf("the command was not published: %d", publisher.sent)
	}
}

func TestCommandsHandlerSendConflict(t *testing.T) {
	repo := newFakeCommandRepo()
	repo.insertErr = ErrInFlight
	_, actuator, crop := testCommand()
	e, tokens := newCommandsEngine(t, repo, fakeCrops{crop: crop}, &fakeActuators{actuator: actuator},
		&fakePublisher{enabled: true, ok: true})

	body := `{"actuatorId":"` + actuator.ID.Hex() + `","action":"ACTIVATE"}`
	rec := commandsRequest(t, e, tokens, http.MethodPost, "/api/v1/crops/"+crop.HexID()+"/commands", body, crop.OwnerID.Hex())
	if rec.Code != http.StatusConflict {
		t.Fatalf("in-flight command = %d, want 409: %s", rec.Code, rec.Body.String())
	}
}

func TestCommandsHandlerSendForeignCrop(t *testing.T) {
	_, actuator, crop := testCommand()
	e, tokens := newCommandsEngine(t, newFakeCommandRepo(),
		fakeCrops{crop: crop, err: apperr.NotFound("Crop does not exist")},
		&fakeActuators{actuator: actuator}, &fakePublisher{enabled: true, ok: true})

	body := `{"actuatorId":"` + actuator.ID.Hex() + `","action":"ACTIVATE"}`
	rec := commandsRequest(t, e, tokens, http.MethodPost, "/api/v1/crops/"+crop.HexID()+"/commands", body,
		primitive.NewObjectID().Hex())
	if rec.Code != http.StatusNotFound {
		t.Fatalf("foreign crop = %d, want 404: %s", rec.Code, rec.Body.String())
	}
}

func TestCommandsHandlerList(t *testing.T) {
	_, actuator, crop := testCommand()
	e, tokens := newCommandsEngine(t, newFakeCommandRepo(), fakeCrops{crop: crop}, &fakeActuators{actuator: actuator},
		&fakePublisher{enabled: true, ok: true})
	userID := crop.OwnerID.Hex()

	rec := commandsRequest(t, e, tokens, http.MethodGet, "/api/v1/crops/"+crop.HexID()+"/commands?limit=abc", "", userID)
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("invalid limit = %d, want 400: %s", rec.Code, rec.Body.String())
	}

	for _, target := range []string{"/api/v1/crops/" + crop.HexID() + "/commands", "/api/v1/commands"} {
		rec = commandsRequest(t, e, tokens, http.MethodGet, target, "", userID)
		if rec.Code != http.StatusOK {
			t.Fatalf("GET %s = %d: %s", target, rec.Code, rec.Body.String())
		}
		if body := strings.TrimSpace(rec.Body.String()); body != "[]" {
			t.Fatalf("empty history must serialize as []: %q", body)
		}
	}
}
