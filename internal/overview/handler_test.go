package overview

import (
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/ai"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/crops"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/httpserver"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/readings"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/security"
	"github.com/labstack/echo/v4"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

const overviewHandlerSecret = "0123456789abcdef0123456789abcdef"

func newOverviewEngine(t *testing.T, f *overviewFixture) (*echo.Echo, *security.TokenService) {
	t.Helper()
	tokens := security.NewTokenService(overviewHandlerSecret, time.Hour)
	logger := slog.New(slog.NewTextHandler(io.Discard, nil))
	e := httpserver.New(logger)
	v1 := e.Group("/api/v1")
	authed := v1.Group("")
	authed.Use(tokens.Guard())
	NewHandler(f.service).Register(authed)
	return e, tokens
}

func overviewRequest(t *testing.T, e *echo.Echo, tokens *security.TokenService,
	target, userID string) *httptest.ResponseRecorder {
	t.Helper()
	issued, err := tokens.Issue(userID, "user@smartpot.app", security.RoleUser)
	if err != nil {
		t.Fatal(err)
	}
	req := httptest.NewRequest(http.MethodGet, target, nil)
	req.AddCookie(&http.Cookie{Name: security.SessionCookieName, Value: issued.Value})
	csrf := tokens.CSRFToken(issued.Value)
	req.AddCookie(&http.Cookie{Name: security.CSRFCookieName, Value: csrf})
	req.Header.Set(security.CSRFHeaderName, csrf)
	rec := httptest.NewRecorder()
	e.ServeHTTP(rec, req)
	return rec
}

func TestOverviewHandlerRequiresSession(t *testing.T) {
	f := newOverviewFixture(nil)
	e, _ := newOverviewEngine(t, f)

	for _, target := range []string{"/api/v1/overview", "/api/v1/overview/series", "/api/v1/overview/fleet"} {
		req := httptest.NewRequest(http.MethodGet, target, nil)
		rec := httptest.NewRecorder()
		e.ServeHTTP(rec, req)
		if rec.Code != http.StatusUnauthorized {
			t.Fatalf("GET %s without session = %d, want 401: %s", target, rec.Code, rec.Body.String())
		}
	}
}

func TestOverviewHandler(t *testing.T) {
	f := newOverviewFixture(nil)
	owner := primitive.NewObjectID()
	first := newTestCrop(owner, "Tomato")
	first.Device = onlineDevice(true)
	first.Health = healthAt(80)
	first.AutomationEnabled = true
	f.cropsRepo.list = []crops.Crop{first}
	f.commandsRepo.count = 3
	f.notificationsRepo.unread = 2
	temperature := 21.0
	f.readingsRepo.latest[first.HexID()] = &readings.Reading{
		CropID: first.ID, MeasuredAt: overviewNow, Measures: readings.Measures{Temperature: &temperature},
	}

	e, tokens := newOverviewEngine(t, f)
	rec := overviewRequest(t, e, tokens, "/api/v1/overview", owner.Hex())
	if rec.Code != http.StatusOK {
		t.Fatalf("GET /overview = %d: %s", rec.Code, rec.Body.String())
	}
	var res OverviewResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &res); err != nil {
		t.Fatalf("invalid JSON: %v", err)
	}
	if res.Totals.Crops != 1 || res.Totals.Online != 1 || res.Totals.Automated != 1 {
		t.Fatalf("unexpected totals: %+v", res.Totals)
	}
	if res.Totals.AverageHealth == nil || *res.Totals.AverageHealth != 80 {
		t.Fatalf("unexpected average health: %v", res.Totals.AverageHealth)
	}
	if res.Totals.UnreadAlerts != 2 || res.Totals.CommandsLast24h != 3 {
		t.Fatalf("unexpected counters: %+v", res.Totals)
	}
	if len(res.Crops) != 1 || res.Crops[0].LatestReading == nil {
		t.Fatalf("latest reading was not included: %+v", res.Crops)
	}
}

func TestOverviewHandlerWithoutCrops(t *testing.T) {
	f := newOverviewFixture(nil)
	e, tokens := newOverviewEngine(t, f)

	rec := overviewRequest(t, e, tokens, "/api/v1/overview", primitive.NewObjectID().Hex())
	if rec.Code != http.StatusOK {
		t.Fatalf("GET /overview = %d: %s", rec.Code, rec.Body.String())
	}
	var raw map[string]any
	if err := json.Unmarshal(rec.Body.Bytes(), &raw); err != nil {
		t.Fatalf("invalid JSON: %v", err)
	}
	totals, _ := raw["totals"].(map[string]any)
	if _, present := totals["averageHealth"]; present {
		t.Fatalf("averageHealth must be omitted without crops: %v", totals)
	}
	if raw["crops"] == nil {
		t.Fatal("crops must always be a JSON list")
	}
}

func TestOverviewHandlerSeries(t *testing.T) {
	f := newOverviewFixture(nil)
	owner := primitive.NewObjectID()
	crop := newTestCrop(owner, "Tomato")
	f.cropsRepo.list = []crops.Crop{crop}
	f.readingsRepo.buckets = []readings.Bucket{{CropID: crop.HexID(), Time: overviewNow, Value: 24.567}}

	e, tokens := newOverviewEngine(t, f)
	rec := overviewRequest(t, e, tokens, "/api/v1/overview/series?metric=temperature&hours=6", owner.Hex())
	if rec.Code != http.StatusOK {
		t.Fatalf("GET /overview/series = %d: %s", rec.Code, rec.Body.String())
	}
	var res MetricSeriesResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &res); err != nil {
		t.Fatalf("invalid JSON: %v", err)
	}
	if res.Metric != "temperature" || res.Hours != 6 || res.BucketMinutes != 10 {
		t.Fatalf("unexpected series header: %+v", res)
	}
	if len(res.Series) != 1 || len(res.Series[0].Points) != 1 || res.Series[0].Points[0].Value != 24.57 {
		t.Fatalf("unexpected series: %+v", res.Series)
	}
}

func TestOverviewHandlerSeriesUsesDefaults(t *testing.T) {
	f := newOverviewFixture(nil)
	owner := primitive.NewObjectID()
	crop := newTestCrop(owner, "Tomato")
	f.cropsRepo.list = []crops.Crop{crop}

	e, tokens := newOverviewEngine(t, f)
	rec := overviewRequest(t, e, tokens, "/api/v1/overview/series", owner.Hex())
	if rec.Code != http.StatusOK {
		t.Fatalf("GET /overview/series = %d: %s", rec.Code, rec.Body.String())
	}
	var res MetricSeriesResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &res); err != nil {
		t.Fatalf("invalid JSON: %v", err)
	}
	if res.Metric != "temperature" || res.Hours != 24 || res.BucketMinutes != 30 {
		t.Fatalf("defaults were not applied: %+v", res)
	}
}

func TestOverviewHandlerSeriesRejectsInvalidParameters(t *testing.T) {
	f := newOverviewFixture(nil)
	e, tokens := newOverviewEngine(t, f)
	owner := primitive.NewObjectID().Hex()

	cases := []string{
		"/api/v1/overview/series?metric=pressure",
		"/api/v1/overview/series?metric=temperature&hours=not-a-number",
		"/api/v1/overview/series?hours=0",
		"/api/v1/overview/series?hours=169",
	}
	for _, target := range cases {
		rec := overviewRequest(t, e, tokens, target, owner)
		if rec.Code != http.StatusBadRequest {
			t.Fatalf("GET %s = %d, want 400: %s", target, rec.Code, rec.Body.String())
		}
	}
}

func TestOverviewHandlerFleetWithoutCrops(t *testing.T) {
	f := newOverviewFixture(nil)
	e, tokens := newOverviewEngine(t, f)

	rec := overviewRequest(t, e, tokens, "/api/v1/overview/fleet", primitive.NewObjectID().Hex())
	if rec.Code != http.StatusOK {
		t.Fatalf("GET /overview/fleet = %d: %s", rec.Code, rec.Body.String())
	}
	var res ai.FleetResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &res); err != nil {
		t.Fatalf("invalid JSON: %v", err)
	}
	if res.Summary == "" || res.Crops == nil || res.SharedIssues == nil || res.Groups == nil || res.Actions == nil {
		t.Fatalf("unexpected empty fleet: %+v", res)
	}
}
