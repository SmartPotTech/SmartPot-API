package crops

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/cache"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/httpserver"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/events"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/geo"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/readings"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/security"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/virtualdevices"
	"github.com/labstack/echo/v4"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

const cropsHandlerSecret = "0123456789abcdef0123456789abcdef"

var cropsNow = time.Date(2026, 11, 6, 12, 0, 0, 0, time.UTC)

// handlerCropsRepo imita el adaptador de PostgreSQL con varios cultivos.
type handlerCropsRepo struct {
	Repository

	mu        sync.Mutex
	items     map[string]*Crop
	order     []string
	quotaErr  error
	saveErr   error
	deleteErr error
}

func newHandlerCropsRepo(crops ...*Crop) *handlerCropsRepo {
	r := &handlerCropsRepo{items: map[string]*Crop{}}
	for _, crop := range crops {
		r.items[crop.HexID()] = crop
		r.order = append(r.order, crop.HexID())
	}
	return r
}

func (r *handlerCropsRepo) ListByOwner(_ context.Context, ownerID primitive.ObjectID) ([]Crop, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	out := make([]Crop, 0, len(r.order))
	for _, id := range r.order {
		crop := r.items[id]
		if crop != nil && crop.OwnerID == ownerID {
			out = append(out, *crop)
		}
	}
	return out, nil
}

func (r *handlerCropsRepo) FindByIDAndOwner(_ context.Context, id, ownerID primitive.ObjectID) (*Crop, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	crop := r.items[id.Hex()]
	if crop == nil || crop.OwnerID != ownerID {
		return nil, nil
	}
	copy := *crop
	return &copy, nil
}

func (r *handlerCropsRepo) FindByID(_ context.Context, id primitive.ObjectID) (*Crop, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	crop := r.items[id.Hex()]
	if crop == nil {
		return nil, nil
	}
	copy := *crop
	return &copy, nil
}

func (r *handlerCropsRepo) CountByOwner(context.Context, primitive.ObjectID) (int64, error) {
	return 0, nil
}

func (r *handlerCropsRepo) Insert(_ context.Context, crop *Crop) error {
	return r.store(crop)
}

func (r *handlerCropsRepo) InsertWithQuota(_ context.Context, crop *Crop, _, _ int) error {
	if r.quotaErr != nil {
		return r.quotaErr
	}
	return r.store(crop)
}

func (r *handlerCropsRepo) store(crop *Crop) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	if crop.ID.IsZero() {
		crop.ID = primitive.NewObjectID()
	}
	copy := *crop
	if _, exists := r.items[crop.HexID()]; !exists {
		r.order = append(r.order, crop.HexID())
	}
	r.items[crop.HexID()] = &copy
	return nil
}

func (r *handlerCropsRepo) Save(_ context.Context, crop *Crop) error {
	if r.saveErr != nil {
		return r.saveErr
	}
	r.mu.Lock()
	defer r.mu.Unlock()
	copy := *crop
	r.items[crop.HexID()] = &copy
	return nil
}

func (r *handlerCropsRepo) Delete(_ context.Context, id primitive.ObjectID) error {
	if r.deleteErr != nil {
		return r.deleteErr
	}
	r.mu.Lock()
	defer r.mu.Unlock()
	delete(r.items, id.Hex())
	return nil
}

func (r *handlerCropsRepo) UpdatePlacement(_ context.Context, id primitive.ObjectID, placement *geo.Placement, _ time.Time) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	if crop := r.items[id.Hex()]; crop != nil {
		crop.Placement = placement
	}
	return nil
}

func (r *handlerCropsRepo) MarkDeviceSeen(_ context.Context, id primitive.ObjectID, online *bool, _ time.Time) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	if crop := r.items[id.Hex()]; crop != nil && crop.Device != nil && online != nil {
		crop.Device.Online = *online
	}
	return nil
}

func (r *handlerCropsRepo) UpdateDeviceKey(context.Context, primitive.ObjectID, string, time.Time, time.Time) error {
	return nil
}

func (r *handlerCropsRepo) UpdateHealth(context.Context, primitive.ObjectID, *CropHealth) error {
	return nil
}
func (r *handlerCropsRepo) BackfillKind(context.Context, []primitive.ObjectID, CropKind) error {
	return nil
}
func (r *handlerCropsRepo) BackfillKindAll(context.Context, CropKind) error { return nil }
func (r *handlerCropsRepo) BackfillFormAll(context.Context, CropForm) error { return nil }
func (r *handlerCropsRepo) BackfillPlacement(context.Context, primitive.ObjectID, geo.Placement) error {
	return nil
}

func (r *handlerCropsRepo) stored(id string) *Crop {
	r.mu.Lock()
	defer r.mu.Unlock()
	return r.items[id]
}

type cropsHandlerReadingsRepo struct {
	readings.Repository
	latest map[string]*readings.Reading
}

func (r *cropsHandlerReadingsRepo) Latest(_ context.Context, cropID primitive.ObjectID) (*readings.Reading, error) {
	return r.latest[cropID.Hex()], nil
}

func (r *cropsHandlerReadingsRepo) LatestByCropIDs(_ context.Context, cropIDs []primitive.ObjectID) (map[string]*readings.Reading, error) {
	out := make(map[string]*readings.Reading, len(cropIDs))
	for _, id := range cropIDs {
		if reading, ok := r.latest[id.Hex()]; ok {
			out[id.Hex()] = reading
		}
	}
	return out, nil
}

type cropsReadingPerms struct{}

func (cropsReadingPerms) Exists(context.Context, string) (bool, error)              { return true, nil }
func (cropsReadingPerms) RequireOwned(context.Context, string, string) error        { return nil }
func (cropsReadingPerms) RequireOwnedVirtual(context.Context, string, string) error { return nil }
func (cropsReadingPerms) MarkDeviceSeen(context.Context, string, *bool)             {}

type handlerVirtual struct {
	checkErr   error
	startErr   error
	checkCalls int
	startCalls int
	startReq   *virtualdevices.VirtualDeviceRequest
}

func (v *handlerVirtual) CheckCanCreate(_ context.Context, _ string, _ *virtualdevices.VirtualDeviceRequest, _ *geo.Placement) error {
	v.checkCalls++
	return v.checkErr
}

func (v *handlerVirtual) StartFor(_ context.Context, _ string, req *virtualdevices.VirtualDeviceRequest) error {
	v.startCalls++
	v.startReq = req
	return v.startErr
}

type cropsEngine struct {
	engine   *echo.Echo
	tokens   *security.TokenService
	repo     *handlerCropsRepo
	virtual  *handlerVirtual
	readings *cropsHandlerReadingsRepo
}

func newCropsEngine(t *testing.T, repo *handlerCropsRepo, reader *cropsHandlerReadingsRepo,
	virtual *handlerVirtual, weather *WeatherService) *cropsEngine {
	t.Helper()
	key := base64.StdEncoding.EncodeToString([]byte("0123456789abcdef0123456789abcdef"))
	encryptor, err := security.NewEncryptor(key)
	if err != nil {
		t.Fatal(err)
	}
	clock := func() time.Time { return cropsNow }
	bus := events.NewBus()
	svc := NewService(repo, encryptor, MQTTPublic{Host: "localhost", Port: 1883}, &fakeProvisioner{},
		fakeSeeder{}, &fakeNotifier{}, bus, clock)
	readingSvc := readings.NewService(reader, cropsReadingPerms{}, cache.NewStore(nil), bus, clock, 0, 100)
	tokens := security.NewTokenService(cropsHandlerSecret, time.Hour)
	logger := slog.New(slog.NewTextHandler(io.Discard, nil))
	e := httpserver.New(logger)
	v1 := e.Group("/api/v1")
	authed := v1.Group("")
	authed.Use(tokens.Guard())
	NewHandler(svc, readingSvc, virtual, weather).Register(authed)
	return &cropsEngine{engine: e, tokens: tokens, repo: repo, virtual: virtual, readings: reader}
}

func cropsRequest(t *testing.T, e *cropsEngine, method, target, body, userID string) *httptest.ResponseRecorder {
	t.Helper()
	issued, err := e.tokens.Issue(userID, "user@smartpot.app", security.RoleUser)
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
	csrf := e.tokens.CSRFToken(issued.Value)
	req.AddCookie(&http.Cookie{Name: security.CSRFCookieName, Value: csrf})
	req.Header.Set(security.CSRFHeaderName, csrf)
	rec := httptest.NewRecorder()
	e.engine.ServeHTTP(rec, req)
	return rec
}

func defaultCropsEngine(t *testing.T, repo *handlerCropsRepo, reader *cropsHandlerReadingsRepo) *cropsEngine {
	t.Helper()
	return newCropsEngine(t, repo, reader, &handlerVirtual{},
		NewWeatherService(&fakeWeatherSource{available: false}, cache.NewStore(nil)))
}

func cropForTest(ownerID primitive.ObjectID, name string) *Crop {
	return &Crop{ID: primitive.NewObjectID(), OwnerID: ownerID, Name: name, Type: TypeLettuce,
		Kind: KindReal, Form: FormPot, CreatedAt: cropsNow, UpdatedAt: cropsNow}
}

func TestCropsHandlerRequiresSession(t *testing.T) {
	ownerID := primitive.NewObjectID()
	repo := newHandlerCropsRepo(cropForTest(ownerID, "Lettuce"))
	e := defaultCropsEngine(t, repo, &cropsHandlerReadingsRepo{latest: map[string]*readings.Reading{}})

	for _, target := range []string{"/api/v1/crops", "/api/v1/crops/" + repo.order[0]} {
		req := httptest.NewRequest(http.MethodGet, target, nil)
		rec := httptest.NewRecorder()
		e.engine.ServeHTTP(rec, req)
		if rec.Code != http.StatusUnauthorized {
			t.Fatalf("GET %s without session = %d, want 401: %s", target, rec.Code, rec.Body.String())
		}
	}
}

func TestCropsHandlerCreateReadListAndDelete(t *testing.T) {
	ownerID := primitive.NewObjectID()
	repo := newHandlerCropsRepo()
	reader := &cropsHandlerReadingsRepo{latest: map[string]*readings.Reading{}}
	e := defaultCropsEngine(t, repo, reader)
	userID := ownerID.Hex()

	rec := cropsRequest(t, e, http.MethodPost, "/api/v1/crops", `{"name":"Lettuce","type":"LETTUCE"}`, userID)
	if rec.Code != http.StatusCreated {
		t.Fatalf("POST /crops = %d: %s", rec.Code, rec.Body.String())
	}
	var created CropCreatedResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &created); err != nil {
		t.Fatalf("invalid JSON: %v", err)
	}
	if created.Crop.ID == "" || created.Device == nil || created.Device.Key == "" {
		t.Fatalf("real crop credentials were not returned: %+v", created)
	}
	if created.Device.Username != created.Crop.ID {
		t.Fatalf("device username = %q, want crop id", created.Device.Username)
	}
	stored := repo.stored(created.Crop.ID)
	if stored == nil || stored.Device == nil || stored.Device.KeyCiphertext == "" ||
		stored.Device.KeyCiphertext == created.Device.Key {
		t.Fatalf("the device key must be stored encrypted: %+v", stored)
	}

	temperature := 23.4
	reader.latest[created.Crop.ID] = &readings.Reading{CropID: stored.ID, MeasuredAt: cropsNow,
		Measures: readings.Measures{Temperature: &temperature}}

	rec = cropsRequest(t, e, http.MethodGet, "/api/v1/crops", "", userID)
	if rec.Code != http.StatusOK {
		t.Fatalf("GET /crops = %d: %s", rec.Code, rec.Body.String())
	}
	var list []CropResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &list); err != nil {
		t.Fatalf("invalid JSON: %v", err)
	}
	if len(list) != 1 || list[0].LatestReading == nil || list[0].LatestReading.Measures.Temperature == nil ||
		*list[0].LatestReading.Measures.Temperature != 23.4 {
		t.Fatalf("latest reading was not included: %+v", list)
	}

	rec = cropsRequest(t, e, http.MethodGet, "/api/v1/crops/"+created.Crop.ID, "", userID)
	if rec.Code != http.StatusOK {
		t.Fatalf("GET /crops/:id = %d: %s", rec.Code, rec.Body.String())
	}

	rec = cropsRequest(t, e, http.MethodDelete, "/api/v1/crops/"+created.Crop.ID, "", userID)
	if rec.Code != http.StatusNoContent {
		t.Fatalf("DELETE /crops/:id = %d: %s", rec.Code, rec.Body.String())
	}
	if repo.stored(created.Crop.ID) != nil {
		t.Fatal("the crop was not deleted")
	}
	rec = cropsRequest(t, e, http.MethodDelete, "/api/v1/crops/"+created.Crop.ID, "", userID)
	if rec.Code != http.StatusNotFound {
		t.Fatalf("second DELETE = %d, want 404: %s", rec.Code, rec.Body.String())
	}
}

func TestCropsHandlerCreateValidation(t *testing.T) {
	ownerID := primitive.NewObjectID()
	repo := newHandlerCropsRepo()
	e := defaultCropsEngine(t, repo, &cropsHandlerReadingsRepo{latest: map[string]*readings.Reading{}})
	userID := ownerID.Hex()

	cases := []string{
		`{"name":"Lettuce"}`,
		`{"name":"L","type":"LETTUCE"}`,
		`{"name":"Lettuce","type":"BANANA"}`,
		`not-json`,
	}
	for _, body := range cases {
		rec := cropsRequest(t, e, http.MethodPost, "/api/v1/crops", body, userID)
		if rec.Code != http.StatusBadRequest {
			t.Fatalf("POST %s = %d, want 400: %s", body, rec.Code, rec.Body.String())
		}
	}
}

func TestCropsHandlerCreateVirtualStartsSimulation(t *testing.T) {
	ownerID := primitive.NewObjectID()
	repo := newHandlerCropsRepo()
	reader := &cropsHandlerReadingsRepo{latest: map[string]*readings.Reading{}}
	virtual := &handlerVirtual{}
	e := newCropsEngine(t, repo, reader, virtual,
		NewWeatherService(&fakeWeatherSource{available: false}, cache.NewStore(nil)))
	userID := ownerID.Hex()

	rec := cropsRequest(t, e, http.MethodPost, "/api/v1/crops",
		`{"name":"Virtual lettuce","type":"LETTUCE","kind":"VIRTUAL","virtual":{"mode":"WEATHER","location":{"name":"Madrid","latitude":40.4,"longitude":-3.7}}}`, userID)
	if rec.Code != http.StatusCreated {
		t.Fatalf("POST virtual crop = %d: %s", rec.Code, rec.Body.String())
	}
	var created CropCreatedResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &created); err != nil {
		t.Fatalf("invalid JSON: %v", err)
	}
	if created.Device != nil {
		t.Fatalf("a virtual crop must not expose device credentials: %+v", created.Device)
	}
	if virtual.checkCalls != 1 || virtual.startCalls != 1 {
		t.Fatalf("virtual hooks were not called: check=%d start=%d", virtual.checkCalls, virtual.startCalls)
	}
	if virtual.startReq == nil || virtual.startReq.Mode == nil || *virtual.startReq.Mode != virtualdevices.ModeWeather {
		t.Fatalf("the simulation request was not forwarded: %+v", virtual.startReq)
	}
	if stored := repo.stored(created.Crop.ID); stored == nil || !stored.IsVirtual() {
		t.Fatalf("the virtual kind was not stored: %+v", stored)
	}

	// Un cultivo real no admite configuración de simulación.
	rec = cropsRequest(t, e, http.MethodPost, "/api/v1/crops",
		`{"name":"Real lettuce","type":"LETTUCE","virtual":{"mode":"AUTO"}}`, userID)
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("real crop with simulation = %d, want 400: %s", rec.Code, rec.Body.String())
	}

	// Si el simulador no está disponible, el error del gestor se propaga y no se crea nada.
	virtual.checkErr = apperr.Unavailable("Virtual crops are not enabled on this server")
	rect := cropsRequest(t, e, http.MethodPost, "/api/v1/crops",
		`{"name":"Another virtual","type":"LETTUCE","kind":"VIRTUAL","virtual":{"mode":"AUTO"}}`, userID)
	if rect.Code != http.StatusServiceUnavailable {
		t.Fatalf("unavailable simulator = %d, want 503: %s", rect.Code, rect.Body.String())
	}
}

func TestCropsHandlerQuotaError(t *testing.T) {
	ownerID := primitive.NewObjectID()
	repo := newHandlerCropsRepo()
	repo.quotaErr = ErrCropLimit
	e := defaultCropsEngine(t, repo, &cropsHandlerReadingsRepo{latest: map[string]*readings.Reading{}})

	rec := cropsRequest(t, e, http.MethodPost, "/api/v1/crops", `{"name":"Lettuce","type":"LETTUCE"}`, ownerID.Hex())
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("quota error = %d, want 400: %s", rec.Code, rec.Body.String())
	}
	if !strings.Contains(rec.Body.String(), "maximum of 20 crops") {
		t.Fatalf("unexpected quota message: %s", rec.Body.String())
	}
}

func TestCropsHandlerOwnershipAndUpdate(t *testing.T) {
	ownerID := primitive.NewObjectID()
	crop := cropForTest(ownerID, "Lettuce")
	repo := newHandlerCropsRepo(crop)
	e := defaultCropsEngine(t, repo, &cropsHandlerReadingsRepo{latest: map[string]*readings.Reading{}})
	foreignUser := primitive.NewObjectID().Hex()

	rec := cropsRequest(t, e, http.MethodGet, "/api/v1/crops/"+crop.HexID(), "", foreignUser)
	if rec.Code != http.StatusNotFound {
		t.Fatalf("foreign crop = %d, want 404: %s", rec.Code, rec.Body.String())
	}
	rec = cropsRequest(t, e, http.MethodGet, "/api/v1/crops/not-an-id", "", ownerID.Hex())
	if rec.Code != http.StatusNotFound {
		t.Fatalf("invalid id = %d, want 404: %s", rec.Code, rec.Body.String())
	}

	rec = cropsRequest(t, e, http.MethodPut, "/api/v1/crops/"+crop.HexID(),
		`{"name":"Renamed","type":"BASIL"}`, ownerID.Hex())
	if rec.Code != http.StatusOK {
		t.Fatalf("PUT /crops/:id = %d: %s", rec.Code, rec.Body.String())
	}
	var updated CropResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &updated); err != nil {
		t.Fatalf("invalid JSON: %v", err)
	}
	if updated.Name != "Renamed" || updated.Type != "BASIL" {
		t.Fatalf("crop was not updated: %+v", updated)
	}

	rec = cropsRequest(t, e, http.MethodPut, "/api/v1/crops/"+crop.HexID(),
		`{"name":"Renamed","type":"BASIL","kind":"VIRTUAL"}`, ownerID.Hex())
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("kind transition = %d, want 400: %s", rec.Code, rec.Body.String())
	}

	rec = cropsRequest(t, e, http.MethodPut, "/api/v1/crops/"+crop.HexID(),
		`{"name":"Renamed","type":"BASIL"}`, foreignUser)
	if rec.Code != http.StatusNotFound {
		t.Fatalf("foreign update = %d, want 404: %s", rec.Code, rec.Body.String())
	}
	rec = cropsRequest(t, e, http.MethodDelete, "/api/v1/crops/"+crop.HexID(), "", foreignUser)
	if rec.Code != http.StatusNotFound {
		t.Fatalf("foreign delete = %d, want 404: %s", rec.Code, rec.Body.String())
	}
}

func TestCropsHandlerListWithoutCropsIsEmptyList(t *testing.T) {
	repo := newHandlerCropsRepo()
	e := defaultCropsEngine(t, repo, &cropsHandlerReadingsRepo{latest: map[string]*readings.Reading{}})

	rec := cropsRequest(t, e, http.MethodGet, "/api/v1/crops", "", primitive.NewObjectID().Hex())
	if rec.Code != http.StatusOK {
		t.Fatalf("GET /crops = %d: %s", rec.Code, rec.Body.String())
	}
	if body := strings.TrimSpace(rec.Body.String()); body != "[]" {
		t.Fatalf("empty list must serialize as []: %q", body)
	}
}

func TestCropsHandlerWeather(t *testing.T) {
	ownerID := primitive.NewObjectID()
	crop := cropForTest(ownerID, "Lettuce")
	crop.Placement = &geo.Placement{Location: &geo.Location{Name: "Bogota", Latitude: 4.7, Longitude: -74.1}}
	repo := newHandlerCropsRepo(crop)
	source := &fakeWeatherSource{available: true, ok: true,
		weather: &virtualdevices.Weather{Temperature: 19.5, Condition: "CLOUDY"}}
	e := newCropsEngine(t, repo, &cropsHandlerReadingsRepo{latest: map[string]*readings.Reading{}}, &handlerVirtual{},
		NewWeatherService(source, cache.NewStore(nil)))

	rec := cropsRequest(t, e, http.MethodGet, "/api/v1/crops/"+crop.HexID()+"/weather", "", ownerID.Hex())
	if rec.Code != http.StatusOK {
		t.Fatalf("GET weather = %d: %s", rec.Code, rec.Body.String())
	}
	var weather virtualdevices.Weather
	if err := json.Unmarshal(rec.Body.Bytes(), &weather); err != nil {
		t.Fatalf("invalid JSON: %v", err)
	}
	if weather.Temperature != 19.5 || weather.Condition != "CLOUDY" {
		t.Fatalf("unexpected weather: %+v", weather)
	}

	noLocation := cropForTest(ownerID, "No location")
	repo.mu.Lock()
	repo.items[noLocation.HexID()] = noLocation
	repo.order = append(repo.order, noLocation.HexID())
	repo.mu.Unlock()
	rec = cropsRequest(t, e, http.MethodGet, "/api/v1/crops/"+noLocation.HexID()+"/weather", "", ownerID.Hex())
	if rec.Code != http.StatusNoContent {
		t.Fatalf("crop without location = %d, want 204: %s", rec.Code, rec.Body.String())
	}
}

func TestCropsHandlerUpdatePropagatesSaveError(t *testing.T) {
	ownerID := primitive.NewObjectID()
	crop := cropForTest(ownerID, "Lettuce")
	repo := newHandlerCropsRepo(crop)
	repo.saveErr = errors.New("database is paused")
	e := defaultCropsEngine(t, repo, &cropsHandlerReadingsRepo{latest: map[string]*readings.Reading{}})

	rec := cropsRequest(t, e, http.MethodPut, "/api/v1/crops/"+crop.HexID(),
		`{"name":"Renamed","type":"BASIL"}`, ownerID.Hex())
	if rec.Code != http.StatusInternalServerError {
		t.Fatalf("save error = %d, want 500: %s", rec.Code, rec.Body.String())
	}
}
