package virtualdevices

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
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/geo"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/security"
	"github.com/labstack/echo/v4"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

const virtualHandlerSecret = "0123456789abcdef0123456789abcdef"

func newVirtualEngine(t *testing.T, ownerID string, crop *CropView, fake *fakeSimulator,
	client *SimulatorClient) (*echo.Echo, *security.TokenService, *fakeCrops, *fakeRepo) {
	t.Helper()
	crops := &fakeCrops{byID: map[string]*CropView{crop.ID: crop}, deviceKey: "device-key", hasKey: true}
	repo := newFakeRepo()
	svc := NewService(repo, crops, client, fixedTime)
	tokens := security.NewTokenService(virtualHandlerSecret, time.Hour)
	logger := slog.New(slog.NewTextHandler(io.Discard, nil))
	e := httpserver.New(logger)
	v1 := e.Group("/api/v1")
	authed := v1.Group("")
	authed.Use(tokens.Guard())
	NewHandler(svc).Register(authed)
	_ = fake
	return e, tokens, crops, repo
}

func virtualAuthedRequest(t *testing.T, e *echo.Echo, tokens *security.TokenService,
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

func TestVirtualHandlerRequiresSession(t *testing.T) {
	ownerID := primitive.NewObjectID().Hex()
	crop := virtualCrop(ownerID)
	fake, client := testSimulator(t)
	e, _, _, _ := newVirtualEngine(t, ownerID, crop, fake, client)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/crops/"+crop.ID+"/virtual-device", nil)
	rec := httptest.NewRecorder()
	e.ServeHTTP(rec, req)
	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("GET without session = %d, want 401: %s", rec.Code, rec.Body.String())
	}
}

func TestVirtualHandlerRejectsWritesWithoutCSRF(t *testing.T) {
	ownerID := primitive.NewObjectID().Hex()
	crop := virtualCrop(ownerID)
	fake, client := testSimulator(t)
	e, tokens, _, _ := newVirtualEngine(t, ownerID, crop, fake, client)

	issued, err := tokens.Issue(ownerID, "user@smartpot.app", security.RoleUser)
	if err != nil {
		t.Fatal(err)
	}
	req := httptest.NewRequest(http.MethodPut, "/api/v1/crops/"+crop.ID+"/virtual-device",
		strings.NewReader(`{"mode":"AUTO"}`))
	req.Header.Set(echo.HeaderContentType, echo.MIMEApplicationJSON)
	req.AddCookie(&http.Cookie{Name: security.SessionCookieName, Value: issued.Value})
	rec := httptest.NewRecorder()
	e.ServeHTTP(rec, req)
	if rec.Code != http.StatusForbidden {
		t.Fatalf("PUT without CSRF = %d, want 403: %s", rec.Code, rec.Body.String())
	}
}

func TestVirtualHandlerGetInactive(t *testing.T) {
	ownerID := primitive.NewObjectID().Hex()
	crop := virtualCrop(ownerID)
	fake, client := testSimulator(t)
	e, tokens, _, _ := newVirtualEngine(t, ownerID, crop, fake, client)

	rec := virtualAuthedRequest(t, e, tokens, http.MethodGet,
		"/api/v1/crops/"+crop.ID+"/virtual-device", "", ownerID)
	if rec.Code != http.StatusOK {
		t.Fatalf("GET virtual-device = %d: %s", rec.Code, rec.Body.String())
	}
	var body VirtualDeviceResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
		t.Fatalf("invalid JSON: %v", err)
	}
	if body.CropID != crop.ID || body.Active || !body.Available {
		t.Fatalf("unexpected inactive response: %+v", body)
	}
}

func TestVirtualHandlerGetForeignAndRealCrop(t *testing.T) {
	ownerID := primitive.NewObjectID().Hex()
	crop := virtualCrop(ownerID)
	fake, client := testSimulator(t)
	e, tokens, crops, _ := newVirtualEngine(t, ownerID, crop, fake, client)

	rec := virtualAuthedRequest(t, e, tokens, http.MethodGet,
		"/api/v1/crops/"+crop.ID+"/virtual-device", "", primitive.NewObjectID().Hex())
	if rec.Code != http.StatusNotFound {
		t.Fatalf("foreign crop = %d, want 404: %s", rec.Code, rec.Body.String())
	}

	real := virtualCrop(ownerID)
	real.Virtual = false
	crops.byID[real.ID] = real
	rec = virtualAuthedRequest(t, e, tokens, http.MethodGet,
		"/api/v1/crops/"+real.ID+"/virtual-device", "", ownerID)
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("real crop = %d, want 400: %s", rec.Code, rec.Body.String())
	}
}

func TestVirtualHandlerConfigure(t *testing.T) {
	ownerID := primitive.NewObjectID().Hex()
	crop := virtualCrop(ownerID)
	fake, client := testSimulator(t)
	e, tokens, _, repo := newVirtualEngine(t, ownerID, crop, fake, client)

	rec := virtualAuthedRequest(t, e, tokens, http.MethodPut,
		"/api/v1/crops/"+crop.ID+"/virtual-device", `{"mode":"MANUAL","manual":{"temperature":21.5}}`, ownerID)
	if rec.Code != http.StatusOK {
		t.Fatalf("PUT virtual-device = %d: %s", rec.Code, rec.Body.String())
	}
	var body VirtualDeviceResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
		t.Fatalf("invalid JSON: %v", err)
	}
	if body.Mode == nil || *body.Mode != ModeManual {
		t.Fatalf("unexpected mode: %+v", body.Mode)
	}
	config := repo.savedByHex(crop.ID)
	if config == nil || config.Mode != ModeManual || config.Manual == nil || *config.Manual.Temperature != 21.5 {
		t.Fatalf("configuration was not persisted: %+v", config)
	}
	if fake.count(http.MethodPut, "/v1/pots/") != 1 {
		t.Fatalf("configuration must be pushed to the simulator: %+v", fake.snapshot())
	}
}

func TestVirtualHandlerConfigureWeather(t *testing.T) {
	ownerID := primitive.NewObjectID().Hex()
	crop := virtualCrop(ownerID)
	fake, client := testSimulator(t)
	e, tokens, crops, _ := newVirtualEngine(t, ownerID, crop, fake, client)

	rec := virtualAuthedRequest(t, e, tokens, http.MethodPut, "/api/v1/crops/"+crop.ID+"/virtual-device",
		`{"mode":"WEATHER","location":{"name":"Madrid","latitude":40.4168,"longitude":-3.7038}}`, ownerID)
	if rec.Code != http.StatusOK {
		t.Fatalf("PUT WEATHER = %d: %s", rec.Code, rec.Body.String())
	}
	if crops.setLocationCalls != 1 {
		t.Fatalf("SetLocation calls = %d, want 1", crops.setLocationCalls)
	}
	var body VirtualDeviceResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
		t.Fatalf("invalid JSON: %v", err)
	}
	if body.Mode == nil || *body.Mode != ModeWeather || body.Location == nil || body.Location.Name != "Madrid" {
		t.Fatalf("unexpected weather response: %+v", body)
	}
}

func TestVirtualHandlerRejectsInvalidConfiguration(t *testing.T) {
	ownerID := primitive.NewObjectID().Hex()
	crop := virtualCrop(ownerID)
	fake, client := testSimulator(t)
	e, tokens, _, _ := newVirtualEngine(t, ownerID, crop, fake, client)

	cases := []struct {
		name string
		body string
	}{
		{"unknown mode", `{"mode":"BOGUS"}`},
		{"missing mode", `{"manual":{"temperature":20}}`},
		{"interval too short", `{"mode":"AUTO","intervalSeconds":5}`},
		{"weather without location", `{"mode":"WEATHER"}`},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			rec := virtualAuthedRequest(t, e, tokens, http.MethodPut,
				"/api/v1/crops/"+crop.ID+"/virtual-device", tc.body, ownerID)
			if rec.Code != http.StatusBadRequest {
				t.Fatalf("%s = %d, want 400: %s", tc.name, rec.Code, rec.Body.String())
			}
		})
	}
	if fake.count(http.MethodPut, "/v1/pots/") != 0 {
		t.Fatalf("invalid configurations must not reach the simulator: %+v", fake.snapshot())
	}
}

func TestVirtualHandlerPause(t *testing.T) {
	ownerID := primitive.NewObjectID().Hex()
	crop := virtualCrop(ownerID)
	fake, client := testSimulator(t)
	e, tokens, _, repo := newVirtualEngine(t, ownerID, crop, fake, client)

	rec := virtualAuthedRequest(t, e, tokens, http.MethodPut,
		"/api/v1/crops/"+crop.ID+"/virtual-device", `{"mode":"AUTO"}`, ownerID)
	if rec.Code != http.StatusOK {
		t.Fatalf("PUT virtual-device = %d: %s", rec.Code, rec.Body.String())
	}

	for i := 0; i < 2; i++ {
		rec = virtualAuthedRequest(t, e, tokens, http.MethodDelete,
			"/api/v1/crops/"+crop.ID+"/virtual-device", "", ownerID)
		if rec.Code != http.StatusNoContent {
			t.Fatalf("DELETE virtual-device (call %d) = %d: %s", i+1, rec.Code, rec.Body.String())
		}
	}
	config := repo.savedByHex(crop.ID)
	if config == nil || config.IsActive() {
		t.Fatalf("the simulation must remain paused: %+v", config)
	}
	if fake.count(http.MethodDelete, "/v1/pots/") != 2 {
		t.Fatalf("every DELETE must reach the simulator: %d calls", fake.count(http.MethodDelete, "/v1/pots/"))
	}
}

func TestVirtualHandlerPlaces(t *testing.T) {
	ownerID := primitive.NewObjectID().Hex()
	crop := virtualCrop(ownerID)
	fake, client := testSimulator(t)
	fake.places = []PlaceResponse{{Name: "Madrid", Country: "Spain", Latitude: 40.4, Longitude: -3.7}}
	e, tokens, _, _ := newVirtualEngine(t, ownerID, crop, fake, client)

	rec := virtualAuthedRequest(t, e, tokens, http.MethodGet, "/api/v1/virtual-devices/places?q=Madrid", "", ownerID)
	if rec.Code != http.StatusOK {
		t.Fatalf("GET places = %d: %s", rec.Code, rec.Body.String())
	}
	var places []PlaceResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &places); err != nil {
		t.Fatalf("invalid JSON: %v", err)
	}
	if len(places) != 1 || places[0].Name != "Madrid" {
		t.Fatalf("unexpected places: %+v", places)
	}

	for _, target := range []string{"/api/v1/virtual-devices/places", "/api/v1/virtual-devices/places?q=a"} {
		rec = virtualAuthedRequest(t, e, tokens, http.MethodGet, target, "", ownerID)
		if rec.Code != http.StatusBadRequest {
			t.Fatalf("GET %s = %d, want 400: %s", target, rec.Code, rec.Body.String())
		}
	}
}

func TestVirtualHandlerGetMapsCropLocation(t *testing.T) {
	ownerID := primitive.NewObjectID().Hex()
	crop := virtualCrop(ownerID)
	crop.Placement = &geo.Placement{Location: &geo.Location{Name: "Bogota", Latitude: 4.7, Longitude: -74.1}}
	fake, client := testSimulator(t)
	e, tokens, _, _ := newVirtualEngine(t, ownerID, crop, fake, client)

	rec := virtualAuthedRequest(t, e, tokens, http.MethodPut,
		"/api/v1/crops/"+crop.ID+"/virtual-device", `{"mode":"WEATHER"}`, ownerID)
	if rec.Code != http.StatusOK {
		t.Fatalf("WEATHER with the crop location = %d: %s", rec.Code, rec.Body.String())
	}
	var body VirtualDeviceResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
		t.Fatalf("invalid JSON: %v", err)
	}
	if body.Location == nil || body.Location.Name != "Bogota" {
		t.Fatalf("the crop location was not used: %+v", body.Location)
	}
}
