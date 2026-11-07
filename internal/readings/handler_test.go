package readings

import (
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
	"github.com/SmartPotTech/SmartPot-API-Go/internal/httpserver"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/events"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/security"
	"github.com/labstack/echo/v4"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

const readingsHandlerSecret = "0123456789abcdef0123456789abcdef"

var readingsNow = time.Date(2026, 11, 6, 12, 0, 0, 0, time.UTC)

type handlerRepo struct {
	Repository

	between      []Reading
	betweenErr   error
	betweenFrom  time.Time
	betweenTo    time.Time
	betweenLimit int

	summaryCount   int64
	summaryMetrics map[string]MetricSummary
	summaryErr     error
	summaryFrom    time.Time
	summaryTo      time.Time
}

func (r *handlerRepo) Between(_ context.Context, _ primitive.ObjectID, from, to time.Time, limit int) ([]Reading, error) {
	r.betweenFrom, r.betweenTo, r.betweenLimit = from, to, limit
	return r.between, r.betweenErr
}

func (r *handlerRepo) Latest(context.Context, primitive.ObjectID) (*Reading, error) { return nil, nil }

func (r *handlerRepo) Summary(_ context.Context, _ primitive.ObjectID, from, to time.Time) (int64, map[string]MetricSummary, error) {
	r.summaryFrom, r.summaryTo = from, to
	return r.summaryCount, r.summaryMetrics, r.summaryErr
}

type handlerCrops struct{ requireErr error }

func (handlerCrops) Exists(context.Context, string) (bool, error) { return true, nil }
func (c handlerCrops) RequireOwned(context.Context, string, string) error {
	return c.requireErr
}
func (handlerCrops) MarkDeviceSeen(context.Context, string, *bool) {}

func newReadingsEngine(t *testing.T, repo Repository, crops Crops) (*echo.Echo, *security.TokenService) {
	t.Helper()
	svc := NewService(repo, crops, cache.NewStore(nil), events.NewBus(), func() time.Time { return readingsNow }, 0, 100)
	tokens := security.NewTokenService(readingsHandlerSecret, time.Hour)
	logger := slog.New(slog.NewTextHandler(io.Discard, nil))
	e := httpserver.New(logger)
	v1 := e.Group("/api/v1")
	authed := v1.Group("")
	authed.Use(tokens.Guard())
	NewHandler(svc).Register(authed)
	return e, tokens
}

func readingsRequest(t *testing.T, e *echo.Echo, tokens *security.TokenService,
	method, target, userID string) *httptest.ResponseRecorder {
	t.Helper()
	issued, err := tokens.Issue(userID, "user@smartpot.app", security.RoleUser)
	if err != nil {
		t.Fatal(err)
	}
	req := httptest.NewRequest(method, target, nil)
	req.AddCookie(&http.Cookie{Name: security.SessionCookieName, Value: issued.Value})
	csrf := tokens.CSRFToken(issued.Value)
	req.AddCookie(&http.Cookie{Name: security.CSRFCookieName, Value: csrf})
	req.Header.Set(security.CSRFHeaderName, csrf)
	rec := httptest.NewRecorder()
	e.ServeHTTP(rec, req)
	return rec
}

func TestReadingsExportCSV(t *testing.T) {
	cropID := primitive.NewObjectID()
	newer := Reading{ID: primitive.NewObjectID(), CropID: cropID, MeasuredAt: readingsNow,
		Measures: Measures{Temperature: floatPtr(22.5), Humidity: floatPtr(60)}}
	older := Reading{ID: primitive.NewObjectID(), CropID: cropID, MeasuredAt: readingsNow.Add(-time.Hour),
		Measures: Measures{Temperature: floatPtr(20)}}
	repo := &handlerRepo{between: []Reading{newer, older}}
	e, tokens := newReadingsEngine(t, repo, handlerCrops{})

	rec := readingsRequest(t, e, tokens, http.MethodGet, "/api/v1/crops/"+cropID.Hex()+"/readings/export", primitive.NewObjectID().Hex())
	if rec.Code != http.StatusOK {
		t.Fatalf("GET export = %d: %s", rec.Code, rec.Body.String())
	}
	if contentType := rec.Header().Get("Content-Type"); !strings.HasPrefix(contentType, "text/csv") {
		t.Fatalf("content type = %q, want text/csv", contentType)
	}
	disposition := rec.Header().Get("Content-Disposition")
	if !strings.Contains(disposition, cropID.Hex()+".csv") {
		t.Fatalf("content disposition = %q", disposition)
	}
	lines := strings.Split(strings.TrimSpace(rec.Body.String()), "\n")
	if len(lines) != 3 {
		t.Fatalf("CSV lines = %d, want 3: %q", len(lines), rec.Body.String())
	}
	if lines[0] != "measuredAt,temperature,humidity,brightness,ph,tds,atmosphere,soilMoisture" {
		t.Fatalf("unexpected CSV header: %q", lines[0])
	}
	if !strings.HasPrefix(lines[1], older.MeasuredAt.UTC().Format(time.RFC3339Nano)+",20,") {
		t.Fatalf("readings must be chronological: %q", lines[1])
	}
	if !strings.HasPrefix(lines[2], newer.MeasuredAt.UTC().Format(time.RFC3339Nano)+",22.5,60,") {
		t.Fatalf("unexpected newest row: %q", lines[2])
	}
	if repo.betweenLimit != exportLimit {
		t.Fatalf("export limit = %d, want %d", repo.betweenLimit, exportLimit)
	}
}

func TestReadingsExportValidatesWindowAndOwnership(t *testing.T) {
	repo := &handlerRepo{}
	e, tokens := newReadingsEngine(t, repo, handlerCrops{})
	cropID := primitive.NewObjectID().Hex()
	userID := primitive.NewObjectID().Hex()

	rec := readingsRequest(t, e, tokens, http.MethodGet,
		"/api/v1/crops/"+cropID+"/readings/export?from=not-a-date", userID)
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("invalid from = %d, want 400: %s", rec.Code, rec.Body.String())
	}
	rec = readingsRequest(t, e, tokens, http.MethodGet,
		"/api/v1/crops/"+cropID+"/readings/export?from=2026-11-06T12:00:00Z&to=2026-11-06T11:00:00Z", userID)
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("start after end = %d, want 400: %s", rec.Code, rec.Body.String())
	}

	foreignEngine, foreignTokens := newReadingsEngine(t, repo, handlerCrops{requireErr: apperr.NotFound("Crop does not exist")})
	rec = readingsRequest(t, foreignEngine, foreignTokens, http.MethodGet,
		"/api/v1/crops/"+cropID+"/readings/export", userID)
	if rec.Code != http.StatusNotFound {
		t.Fatalf("foreign crop = %d, want 404: %s", rec.Code, rec.Body.String())
	}
}

func TestReadingsExportRequiresSession(t *testing.T) {
	e, _ := newReadingsEngine(t, &handlerRepo{}, handlerCrops{})
	req := httptest.NewRequest(http.MethodGet, "/api/v1/crops/"+primitive.NewObjectID().Hex()+"/readings/export", nil)
	rec := httptest.NewRecorder()
	e.ServeHTTP(rec, req)
	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("export without session = %d, want 401: %s", rec.Code, rec.Body.String())
	}
}

func TestReadingsSummary(t *testing.T) {
	minimum, average, maximum := 18.0, 21.5, 25.0
	repo := &handlerRepo{summaryCount: 12, summaryMetrics: map[string]MetricSummary{
		"temperature": {Min: &minimum, Avg: &average, Max: &maximum},
	}}
	e, tokens := newReadingsEngine(t, repo, handlerCrops{})
	cropID := primitive.NewObjectID().Hex()

	rec := readingsRequest(t, e, tokens, http.MethodGet,
		"/api/v1/crops/"+cropID+"/readings/summary?hours=24", primitive.NewObjectID().Hex())
	if rec.Code != http.StatusOK {
		t.Fatalf("GET summary = %d: %s", rec.Code, rec.Body.String())
	}
	var res ReadingSummaryResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &res); err != nil {
		t.Fatalf("invalid JSON: %v", err)
	}
	if res.Count != 12 {
		t.Fatalf("count = %d, want 12", res.Count)
	}
	if metric, ok := res.Metrics["temperature"]; !ok || metric.Avg == nil || *metric.Avg != 21.5 {
		t.Fatalf("unexpected metrics: %+v", res.Metrics)
	}
	if !res.To.Equal(readingsNow) || !res.From.Equal(readingsNow.Add(-24*time.Hour)) {
		t.Fatalf("unexpected window: %v - %v", res.From, res.To)
	}
	if !repo.summaryFrom.Equal(readingsNow.Add(-24*time.Hour)) || !repo.summaryTo.Equal(readingsNow) {
		t.Fatalf("repository window = %v - %v", repo.summaryFrom, repo.summaryTo)
	}
}

func TestReadingsSummaryRejectsInvalidHours(t *testing.T) {
	e, tokens := newReadingsEngine(t, &handlerRepo{}, handlerCrops{})
	cropID := primitive.NewObjectID().Hex()

	rec := readingsRequest(t, e, tokens, http.MethodGet,
		"/api/v1/crops/"+cropID+"/readings/summary?hours=not-a-number", primitive.NewObjectID().Hex())
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("invalid hours = %d, want 400: %s", rec.Code, rec.Body.String())
	}

	foreignEngine, foreignTokens := newReadingsEngine(t, &handlerRepo{}, handlerCrops{requireErr: apperr.NotFound("Crop does not exist")})
	rec = readingsRequest(t, foreignEngine, foreignTokens, http.MethodGet,
		"/api/v1/crops/"+cropID+"/readings/summary?hours=24", primitive.NewObjectID().Hex())
	if rec.Code != http.StatusNotFound {
		t.Fatalf("foreign crop = %d, want 404: %s", rec.Code, rec.Body.String())
	}
}

func TestReadingsLatestWithoutReadingsIsNotFound(t *testing.T) {
	repo := &handlerRepo{}
	e, tokens := newReadingsEngine(t, repo, handlerCrops{})
	cropID := primitive.NewObjectID().Hex()

	rec := readingsRequest(t, e, tokens, http.MethodGet,
		"/api/v1/crops/"+cropID+"/readings/latest", primitive.NewObjectID().Hex())
	if rec.Code != http.StatusNotFound {
		t.Fatalf("missing latest reading = %d, want 404: %s", rec.Code, rec.Body.String())
	}
}

func floatPtr(v float64) *float64 { return &v }
