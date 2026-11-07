package virtualdevices

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/config"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/geo"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

// fakeRepo guarda las configuraciones de simulación en memoria.
type fakeRepo struct {
	mu sync.Mutex

	configs map[string]*VirtualDevice
	saveErr error
	findErr error

	saveCalls       int
	deleteByCropIDs []primitive.ObjectID
}

func newFakeRepo() *fakeRepo {
	return &fakeRepo{configs: map[string]*VirtualDevice{}}
}

func (r *fakeRepo) FindByCropID(_ context.Context, cropID primitive.ObjectID) (*VirtualDevice, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	if r.findErr != nil {
		return nil, r.findErr
	}
	config, ok := r.configs[cropID.Hex()]
	if !ok {
		return nil, nil
	}
	copy := *config
	return &copy, nil
}

func (r *fakeRepo) CountByOwner(_ context.Context, ownerID primitive.ObjectID) (int64, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	var count int64
	for _, config := range r.configs {
		if config.OwnerID == ownerID {
			count++
		}
	}
	return count, nil
}

func (r *fakeRepo) FindAll(context.Context) ([]VirtualDevice, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	out := make([]VirtualDevice, 0, len(r.configs))
	for _, config := range r.configs {
		out = append(out, *config)
	}
	return out, nil
}

func (r *fakeRepo) Save(_ context.Context, config *VirtualDevice) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.saveCalls++
	if r.saveErr != nil {
		return r.saveErr
	}
	copy := *config
	r.configs[config.CropID.Hex()] = &copy
	return nil
}

func (r *fakeRepo) DeleteByCrop(_ context.Context, cropID primitive.ObjectID) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.deleteByCropIDs = append(r.deleteByCropIDs, cropID)
	delete(r.configs, cropID.Hex())
	return nil
}

func (r *fakeRepo) savedByHex(cropID string) *VirtualDevice {
	r.mu.Lock()
	defer r.mu.Unlock()
	return r.configs[cropID]
}

// fakeCrops implementa la vista de cultivos que consume el servicio.
type fakeCrops struct {
	mu               sync.Mutex
	byID             map[string]*CropView
	deviceKey        string
	hasKey           bool
	setLocationCalls int
}

func (c *fakeCrops) GetOwned(_ context.Context, ownerID, cropID string) (*CropView, error) {
	c.mu.Lock()
	defer c.mu.Unlock()
	crop, ok := c.byID[cropID]
	if !ok || crop.OwnerID != ownerID {
		return nil, apperr.NotFound("Crop does not exist")
	}
	copy := *crop
	return &copy, nil
}

func (c *fakeCrops) Find(_ context.Context, cropID string) (*CropView, bool) {
	c.mu.Lock()
	defer c.mu.Unlock()
	crop, ok := c.byID[cropID]
	if !ok {
		return nil, false
	}
	copy := *crop
	return &copy, true
}

func (c *fakeCrops) DeviceKey(context.Context, string) (string, bool) {
	return c.deviceKey, c.hasKey
}

func (c *fakeCrops) SetLocation(_ context.Context, cropID string, loc geo.Location) (*CropView, error) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.setLocationCalls++
	crop, ok := c.byID[cropID]
	if !ok {
		return nil, apperr.NotFound("Crop does not exist")
	}
	if crop.Placement == nil {
		crop.Placement = &geo.Placement{}
	}
	crop.Placement.Location = &loc
	copy := *crop
	return &copy, nil
}

type simCall struct {
	method string
	path   string
	auth   string
	body   []byte
}

// fakeSimulator imita la API interna del SmartPot-DataGenerator.
type fakeSimulator struct {
	mu sync.Mutex

	calls []simCall
	pots  map[string]SimulatorPot

	places       []PlaceResponse
	placesStatus int
	listStatus   int
	putStatus    int
	getStatus    int
	deleteStatus int
}

func newFakeSimulator() *fakeSimulator {
	return &fakeSimulator{pots: map[string]SimulatorPot{}}
}

func (s *fakeSimulator) serveHTTP(w http.ResponseWriter, r *http.Request) {
	body, _ := io.ReadAll(r.Body)
	s.mu.Lock()
	s.calls = append(s.calls, simCall{method: r.Method, path: r.URL.RequestURI(), auth: r.Header.Get("Authorization"), body: body})
	s.mu.Unlock()

	switch {
	case r.Method == http.MethodGet && r.URL.Path == "/health":
		w.WriteHeader(http.StatusOK)
	case r.Method == http.MethodGet && r.URL.Path == "/v1/pots":
		s.mu.Lock()
		status := s.listStatus
		out := make([]SimulatorPot, 0, len(s.pots))
		for _, pot := range s.pots {
			out = append(out, pot)
		}
		s.mu.Unlock()
		if status != 0 && status != http.StatusOK {
			w.WriteHeader(status)
			return
		}
		writeJSON(w, out)
	case r.Method == http.MethodGet && r.URL.Path == "/v1/places":
		s.mu.Lock()
		status, places := s.placesStatus, s.places
		s.mu.Unlock()
		if status != 0 && status != http.StatusOK {
			w.WriteHeader(status)
			return
		}
		writeJSON(w, places)
	case strings.HasPrefix(r.URL.Path, "/v1/pots/"):
		s.servePot(w, r, body)
	default:
		w.WriteHeader(http.StatusNotFound)
	}
}

func (s *fakeSimulator) servePot(w http.ResponseWriter, r *http.Request, body []byte) {
	cropID := strings.TrimPrefix(r.URL.Path, "/v1/pots/")
	switch r.Method {
	case http.MethodPut:
		s.mu.Lock()
		status := s.putStatus
		s.mu.Unlock()
		if status != 0 && status != http.StatusOK {
			w.WriteHeader(status)
			return
		}
		var req SimulatorPotRequest
		if err := json.Unmarshal(body, &req); err != nil {
			w.WriteHeader(http.StatusBadRequest)
			return
		}
		pot := SimulatorPot{CropID: cropID, CropType: req.CropType, Mode: req.Mode, Managed: true,
			Connected: true, IntervalSeconds: float64(req.IntervalSeconds), Manual: req.Manual}
		s.mu.Lock()
		s.pots[cropID] = pot
		s.mu.Unlock()
		writeJSON(w, pot)
	case http.MethodGet:
		s.mu.Lock()
		status := s.getStatus
		pot, ok := s.pots[cropID]
		s.mu.Unlock()
		if status != 0 && status != http.StatusOK {
			w.WriteHeader(status)
			return
		}
		if !ok {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		writeJSON(w, pot)
	case http.MethodDelete:
		s.mu.Lock()
		status := s.deleteStatus
		delete(s.pots, cropID)
		s.mu.Unlock()
		if status != 0 && status != http.StatusOK {
			w.WriteHeader(status)
			return
		}
		w.WriteHeader(http.StatusNoContent)
	default:
		w.WriteHeader(http.StatusMethodNotAllowed)
	}
}

func writeJSON(w http.ResponseWriter, value any) {
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(value)
}

func (s *fakeSimulator) snapshot() []simCall {
	s.mu.Lock()
	defer s.mu.Unlock()
	return append([]simCall(nil), s.calls...)
}

func (s *fakeSimulator) count(method, prefix string) int {
	count := 0
	for _, call := range s.snapshot() {
		if call.method == method && strings.HasPrefix(call.path, prefix) {
			count++
		}
	}
	return count
}

func testSimulator(t *testing.T) (*fakeSimulator, *SimulatorClient) {
	t.Helper()
	fake := newFakeSimulator()
	server := httptest.NewServer(http.HandlerFunc(fake.serveHTTP))
	t.Cleanup(server.Close)
	client := NewSimulatorClient(config.Simulator{
		Enabled: true,
		BaseURL: server.URL,
		Token:   "sim-token",
		Timeout: 2 * time.Second,
	})
	return fake, client
}

func fixedTime() time.Time {
	return time.Date(2026, 11, 6, 12, 0, 0, 0, time.UTC)
}

func virtualCrop(ownerID string) *CropView {
	return &CropView{ID: primitive.NewObjectID().Hex(), OwnerID: ownerID, Name: "Lettuce",
		Type: "LETTUCE", Virtual: true}
}

func cropFixture(ownerID string) (*CropView, *fakeCrops, *fakeRepo) {
	crop := virtualCrop(ownerID)
	crops := &fakeCrops{byID: map[string]*CropView{crop.ID: crop}, deviceKey: "device-key", hasKey: true}
	return crop, crops, newFakeRepo()
}

func TestStartForUsesDefaultModeAndPushesSimulation(t *testing.T) {
	ownerID := primitive.NewObjectID().Hex()
	crop, crops, repo := cropFixture(ownerID)
	fake, client := testSimulator(t)
	svc := NewService(repo, crops, client, fixedTime)

	if err := svc.StartFor(context.Background(), crop.ID, nil); err != nil {
		t.Fatal(err)
	}
	config := repo.savedByHex(crop.ID)
	if config == nil {
		t.Fatal("the simulation configuration was not saved")
	}
	if config.Mode != ModeAuto {
		t.Fatalf("mode = %q, want AUTO by default", config.Mode)
	}
	if config.IntervalSeconds != defaultInterval {
		t.Fatalf("interval = %d, want %d", config.IntervalSeconds, defaultInterval)
	}
	if !config.IsActive() {
		t.Fatal("a new simulation must start active")
	}
	if !config.CreatedAt.Equal(fixedTime()) || !config.UpdatedAt.Equal(fixedTime()) {
		t.Fatalf("unexpected timestamps: created=%v updated=%v", config.CreatedAt, config.UpdatedAt)
	}

	calls := fake.snapshot()
	if len(calls) != 1 {
		t.Fatalf("expected one simulator call, got %d: %+v", len(calls), calls)
	}
	call := calls[0]
	if call.method != http.MethodPut || call.path != "/v1/pots/"+crop.ID {
		t.Fatalf("unexpected simulator call: %s %s", call.method, call.path)
	}
	if call.auth != "Bearer sim-token" {
		t.Fatalf("missing simulator token: %q", call.auth)
	}
	var body SimulatorPotRequest
	if err := json.Unmarshal(call.body, &body); err != nil {
		t.Fatalf("invalid request body: %v", err)
	}
	if body.Key != "device-key" || body.CropType != "LETTUCE" || body.Mode != "AUTO" || body.IntervalSeconds != defaultInterval {
		t.Fatalf("unexpected simulator body: %+v", body)
	}
}

func TestStartForIgnoresMissingCrop(t *testing.T) {
	fake, client := testSimulator(t)
	repo := newFakeRepo()
	svc := NewService(repo, &fakeCrops{byID: map[string]*CropView{}}, client, fixedTime)

	if err := svc.StartFor(context.Background(), primitive.NewObjectID().Hex(), nil); err != nil {
		t.Fatalf("a crop that no longer exists must not fail the cascade: %v", err)
	}
	if repo.saveCalls != 0 || len(fake.snapshot()) != 0 {
		t.Fatalf("nothing should be saved or pushed: saves=%d calls=%d", repo.saveCalls, len(fake.snapshot()))
	}
}

func TestStartForPropagatesRepositoryError(t *testing.T) {
	ownerID := primitive.NewObjectID().Hex()
	crop, crops, repo := cropFixture(ownerID)
	repo.saveErr = errors.New("database is paused")
	_, client := testSimulator(t)
	svc := NewService(repo, crops, client, fixedTime)

	if err := svc.StartFor(context.Background(), crop.ID, nil); err == nil {
		t.Fatal("a failed save must be reported")
	}
}

func TestGetEnforcesOwnershipAndVirtualKind(t *testing.T) {
	ownerID := primitive.NewObjectID().Hex()
	crop, crops, repo := cropFixture(ownerID)
	_, client := testSimulator(t)
	svc := NewService(repo, crops, client, fixedTime)

	_, err := svc.Get(context.Background(), primitive.NewObjectID().Hex(), crop.ID)
	if !isStatus(err, http.StatusNotFound) {
		t.Fatalf("another user's crop must be 404, got %v", err)
	}

	real := virtualCrop(ownerID)
	real.Virtual = false
	crops.byID[real.ID] = real
	_, err = svc.Get(context.Background(), ownerID, real.ID)
	if !isStatus(err, http.StatusBadRequest) {
		t.Fatalf("a real crop must be 400, got %v", err)
	}
}

func TestGetWithoutConfigurationReturnsInactive(t *testing.T) {
	ownerID := primitive.NewObjectID().Hex()
	crop, crops, repo := cropFixture(ownerID)
	fake, client := testSimulator(t)
	svc := NewService(repo, crops, client, fixedTime)

	res, err := svc.Get(context.Background(), ownerID, crop.ID)
	if err != nil {
		t.Fatal(err)
	}
	if !res.Available || res.Active || res.Running {
		t.Fatalf("unexpected inactive response: %+v", res)
	}
	if res.ActiveActuators == nil || len(res.ActiveActuators) != 0 {
		t.Fatalf("activeActuators must be an empty list: %#v", res.ActiveActuators)
	}
	if len(fake.snapshot()) != 0 {
		t.Fatalf("an inactive crop must not query the simulator: %+v", fake.snapshot())
	}
}

func TestGetReturnsLiveSimulatorState(t *testing.T) {
	ownerID := primitive.NewObjectID().Hex()
	crop, crops, repo := cropFixture(ownerID)
	fake, client := testSimulator(t)
	repo.configs[crop.ID] = &VirtualDevice{CropID: mustObjectID(t, crop.ID), OwnerID: mustObjectID(t, ownerID),
		Mode: ModeAuto, IntervalSeconds: 45, CreatedAt: fixedTime()}
	fake.pots[crop.ID] = SimulatorPot{CropID: crop.ID, Managed: true, Connected: true,
		Mode: "AUTO", LastReading: map[string]float64{"temperature": 22.5}}
	svc := NewService(repo, crops, client, fixedTime)

	res, err := svc.Get(context.Background(), ownerID, crop.ID)
	if err != nil {
		t.Fatal(err)
	}
	if !res.Active || !res.Running || !res.Connected {
		t.Fatalf("expected an active live simulation: %+v", res)
	}
	if res.Mode == nil || *res.Mode != ModeAuto || res.IntervalSeconds == nil || *res.IntervalSeconds != 45 {
		t.Fatalf("configuration was not mapped: %+v", res)
	}
	if res.LastReading["temperature"] != 22.5 {
		t.Fatalf("live reading was not mapped: %+v", res.LastReading)
	}

	// Una configuración pausada no consulta el estado en vivo.
	inactive := false
	repo.configs[crop.ID].Active = &inactive
	fake.mu.Lock()
	fake.calls = nil
	fake.mu.Unlock()
	res, err = svc.Get(context.Background(), ownerID, crop.ID)
	if err != nil {
		t.Fatal(err)
	}
	if res.Active || res.Running {
		t.Fatalf("paused simulation must not be running: %+v", res)
	}
	if len(fake.snapshot()) != 0 {
		t.Fatalf("paused simulation must not query the simulator: %+v", fake.snapshot())
	}
}

func TestConfigureSupportsAllModes(t *testing.T) {
	ownerID := primitive.NewObjectID().Hex()
	crop, crops, repo := cropFixture(ownerID)
	fake, client := testSimulator(t)
	svc := NewService(repo, crops, client, fixedTime)

	auto := ModeAuto
	res, err := svc.Configure(context.Background(), ownerID, crop.ID, VirtualDeviceRequest{Mode: &auto})
	if err != nil {
		t.Fatal(err)
	}
	if res.Mode == nil || *res.Mode != ModeAuto {
		t.Fatalf("AUTO was not applied: %+v", res.Mode)
	}

	manual := ModeManual
	temperature := 21.5
	humidity := 65.0
	res, err = svc.Configure(context.Background(), ownerID, crop.ID, VirtualDeviceRequest{
		Mode:   &manual,
		Manual: &ManualValues{Temperature: &temperature, Humidity: &humidity},
	})
	if err != nil {
		t.Fatal(err)
	}
	if res.Mode == nil || *res.Mode != ModeManual {
		t.Fatalf("MANUAL was not applied: %+v", res.Mode)
	}
	config := repo.savedByHex(crop.ID)
	if config.Manual == nil || config.Manual.Temperature == nil || *config.Manual.Temperature != 21.5 {
		t.Fatalf("manual values were not persisted: %+v", config.Manual)
	}

	weather := ModeWeather
	name := "Madrid"
	latitude, longitude := 40.4168, -3.7038
	res, err = svc.Configure(context.Background(), ownerID, crop.ID, VirtualDeviceRequest{
		Mode:     &weather,
		Location: &LocationInput{Name: name, Latitude: &latitude, Longitude: &longitude},
	})
	if err != nil {
		t.Fatal(err)
	}
	if res.Mode == nil || *res.Mode != ModeWeather {
		t.Fatalf("WEATHER was not applied: %+v", res.Mode)
	}
	if res.Location == nil || res.Location.Name != name {
		t.Fatalf("location was not applied: %+v", res.Location)
	}
	if crops.setLocationCalls != 1 {
		t.Fatalf("SetLocation calls = %d, want 1", crops.setLocationCalls)
	}
	if fake.count(http.MethodPut, "/v1/pots/") != 3 {
		t.Fatalf("every configure must push to the simulator: %+v", fake.snapshot())
	}
}

func TestConfigureRejectsWeatherWithoutLocation(t *testing.T) {
	ownerID := primitive.NewObjectID().Hex()
	crop, crops, repo := cropFixture(ownerID)
	fake, client := testSimulator(t)
	svc := NewService(repo, crops, client, fixedTime)

	weather := ModeWeather
	_, err := svc.Configure(context.Background(), ownerID, crop.ID, VirtualDeviceRequest{Mode: &weather})
	if !isStatus(err, http.StatusBadRequest) {
		t.Fatalf("WEATHER without location must be 400, got %v", err)
	}
	if repo.saveCalls != 0 || len(fake.snapshot()) != 0 {
		t.Fatalf("invalid configuration must not be persisted or pushed")
	}
}

func TestConfigureRequiresSimulator(t *testing.T) {
	ownerID := primitive.NewObjectID().Hex()
	crop, crops, repo := cropFixture(ownerID)
	client := NewSimulatorClient(config.Simulator{Enabled: false})
	svc := NewService(repo, crops, client, fixedTime)

	auto := ModeAuto
	_, err := svc.Configure(context.Background(), ownerID, crop.ID, VirtualDeviceRequest{Mode: &auto})
	if !isStatus(err, http.StatusServiceUnavailable) {
		t.Fatalf("without simulator Configure must be 503, got %v", err)
	}
}

func TestConfigurePropagatesSimulatorAndSaveErrors(t *testing.T) {
	ownerID := primitive.NewObjectID().Hex()
	crop, crops, repo := cropFixture(ownerID)
	fake, client := testSimulator(t)
	svc := NewService(repo, crops, client, fixedTime)
	auto := ModeAuto

	fake.putStatus = http.StatusBadRequest
	_, err := svc.Configure(context.Background(), ownerID, crop.ID, VirtualDeviceRequest{Mode: &auto})
	if !isStatus(err, http.StatusBadRequest) {
		t.Fatalf("a rejected simulation must be 400, got %v", err)
	}
	if repo.saveCalls != 0 {
		t.Fatal("a rejected simulation must not be persisted")
	}

	fake.putStatus = http.StatusOK
	repo.saveErr = errors.New("database is paused")
	_, err = svc.Configure(context.Background(), ownerID, crop.ID, VirtualDeviceRequest{Mode: &auto})
	if err == nil {
		t.Fatal("a failed save must be reported")
	}
}

func TestPauseMarksInactiveAndIsIdempotent(t *testing.T) {
	ownerID := primitive.NewObjectID().Hex()
	crop, crops, repo := cropFixture(ownerID)
	active := true
	repo.configs[crop.ID] = &VirtualDevice{CropID: mustObjectID(t, crop.ID), OwnerID: mustObjectID(t, ownerID),
		Mode: ModeAuto, IntervalSeconds: 30, Active: &active, CreatedAt: fixedTime()}
	fake, client := testSimulator(t)
	fake.pots[crop.ID] = SimulatorPot{CropID: crop.ID, Managed: true}
	svc := NewService(repo, crops, client, fixedTime)

	if err := svc.Pause(context.Background(), ownerID, crop.ID); err != nil {
		t.Fatal(err)
	}
	config := repo.savedByHex(crop.ID)
	if config == nil || config.IsActive() {
		t.Fatalf("the configuration must be paused: %+v", config)
	}
	if repo.saveCalls != 1 || fake.count(http.MethodDelete, "/v1/pots/") != 1 {
		t.Fatalf("pause must save once and delete once: saves=%d deletes=%d",
			repo.saveCalls, fake.count(http.MethodDelete, "/v1/pots/"))
	}

	// La segunda pausa deja la configuración igual y vuelve a limpiar el simulador.
	if err := svc.Pause(context.Background(), ownerID, crop.ID); err != nil {
		t.Fatalf("pause must be idempotent: %v", err)
	}
	if repo.saveCalls != 2 {
		t.Fatalf("second pause must persist the paused state again, got %d calls", repo.saveCalls)
	}
	if config := repo.savedByHex(crop.ID); config == nil || config.IsActive() {
		t.Fatalf("the configuration must stay paused: %+v", config)
	}
	if fake.count(http.MethodDelete, "/v1/pots/") != 2 {
		t.Fatalf("second pause must still clear the simulator, got %d deletes", fake.count(http.MethodDelete, "/v1/pots/"))
	}
}

func TestPauseRejectsForeignCrop(t *testing.T) {
	ownerID := primitive.NewObjectID().Hex()
	crop, crops, repo := cropFixture(ownerID)
	_, client := testSimulator(t)
	svc := NewService(repo, crops, client, fixedTime)

	err := svc.Pause(context.Background(), primitive.NewObjectID().Hex(), crop.ID)
	if !isStatus(err, http.StatusNotFound) {
		t.Fatalf("another user's crop must be 404, got %v", err)
	}
}

func TestPlacesSearchesTrimmedQuery(t *testing.T) {
	fake, client := testSimulator(t)
	fake.places = []PlaceResponse{{Name: "Madrid", Latitude: 40.4, Longitude: -3.7, Country: "Spain"}}
	svc := NewService(newFakeRepo(), &fakeCrops{}, client, fixedTime)

	places, err := svc.Places(context.Background(), "  Madrid  ")
	if err != nil {
		t.Fatal(err)
	}
	if len(places) != 1 || places[0].Name != "Madrid" {
		t.Fatalf("unexpected places: %+v", places)
	}
	calls := fake.snapshot()
	if len(calls) != 1 || calls[0].method != http.MethodGet || calls[0].path != "/v1/places?q=Madrid" {
		t.Fatalf("unexpected simulator call: %+v", calls)
	}
	if calls[0].auth != "Bearer sim-token" {
		t.Fatalf("missing simulator token: %q", calls[0].auth)
	}
}

func TestPlacesValidationAndSimulatorErrors(t *testing.T) {
	fake, client := testSimulator(t)
	svc := NewService(newFakeRepo(), &fakeCrops{}, client, fixedTime)

	for _, query := range []string{"", "a", strings.Repeat("x", 81)} {
		if _, err := svc.Places(context.Background(), query); !isStatus(err, http.StatusBadRequest) {
			t.Fatalf("query %q must be 400, got %v", query, err)
		}
	}
	if len(fake.snapshot()) != 0 {
		t.Fatalf("invalid queries must not reach the simulator: %+v", fake.snapshot())
	}

	fake.placesStatus = http.StatusInternalServerError
	if _, err := svc.Places(context.Background(), "Madrid"); !isStatus(err, http.StatusServiceUnavailable) {
		t.Fatalf("a simulator failure must be 503, got %v", err)
	}

	fake.placesStatus = http.StatusOK
	fake.places = []PlaceResponse{}
	places, err := svc.Places(context.Background(), "Nowhere")
	if err != nil {
		t.Fatal(err)
	}
	if places == nil || len(places) != 0 {
		t.Fatalf("an empty result must be an empty slice: %#v", places)
	}
}

func TestCheckCanCreate(t *testing.T) {
	_, client := testSimulator(t)
	svc := NewService(newFakeRepo(), &fakeCrops{}, client, fixedTime)

	auto := ModeAuto
	if err := svc.CheckCanCreate(context.Background(), primitive.NewObjectID().Hex(),
		&VirtualDeviceRequest{Mode: &auto}, nil); err != nil {
		t.Fatalf("a valid AUTO configuration must be accepted: %v", err)
	}

	weather := ModeWeather
	if err := svc.CheckCanCreate(context.Background(), primitive.NewObjectID().Hex(),
		&VirtualDeviceRequest{Mode: &weather}, nil); !isStatus(err, http.StatusBadRequest) {
		t.Fatalf("WEATHER without location must be 400, got %v", err)
	}

	placed := &geo.Placement{Location: &geo.Location{Name: "Madrid", Latitude: 40.4, Longitude: -3.7}}
	if err := svc.CheckCanCreate(context.Background(), primitive.NewObjectID().Hex(),
		&VirtualDeviceRequest{Mode: &weather}, placed); err != nil {
		t.Fatalf("WEATHER with a crop location must be accepted: %v", err)
	}

	disabled := NewService(newFakeRepo(), &fakeCrops{}, NewSimulatorClient(config.Simulator{Enabled: false}), fixedTime)
	err := disabled.CheckCanCreate(context.Background(), primitive.NewObjectID().Hex(), &VirtualDeviceRequest{Mode: &auto}, nil)
	if !isStatus(err, http.StatusServiceUnavailable) {
		t.Fatalf("without simulator CheckCanCreate must be 503, got %v", err)
	}

	if MaxPerAccount != 5 {
		t.Fatalf("MaxPerAccount = %d, want 5", MaxPerAccount)
	}
}

func TestReconcileRestoresActiveAndRemovesPausedSimulations(t *testing.T) {
	ownerID := primitive.NewObjectID().Hex()
	activeCrop := virtualCrop(ownerID)
	runningCrop := virtualCrop(ownerID)
	pausedCrop := virtualCrop(ownerID)
	deletedCrop := virtualCrop(ownerID)

	crops := &fakeCrops{byID: map[string]*CropView{
		activeCrop.ID: activeCrop, runningCrop.ID: runningCrop, pausedCrop.ID: pausedCrop,
	}, deviceKey: "device-key", hasKey: true}
	repo := newFakeRepo()
	active := true
	inactive := false
	repo.configs[activeCrop.ID] = &VirtualDevice{CropID: mustObjectID(t, activeCrop.ID), OwnerID: mustObjectID(t, ownerID),
		Mode: ModeAuto, IntervalSeconds: 30, Active: &active}
	repo.configs[runningCrop.ID] = &VirtualDevice{CropID: mustObjectID(t, runningCrop.ID), OwnerID: mustObjectID(t, ownerID),
		Mode: ModeAuto, IntervalSeconds: 30, Active: &active}
	repo.configs[pausedCrop.ID] = &VirtualDevice{CropID: mustObjectID(t, pausedCrop.ID), OwnerID: mustObjectID(t, ownerID),
		Mode: ModeAuto, IntervalSeconds: 30, Active: &inactive}
	repo.configs[deletedCrop.ID] = &VirtualDevice{CropID: mustObjectID(t, deletedCrop.ID), OwnerID: mustObjectID(t, ownerID),
		Mode: ModeAuto, IntervalSeconds: 30, Active: &active}

	fake, client := testSimulator(t)
	fake.pots[runningCrop.ID] = SimulatorPot{CropID: runningCrop.ID, Managed: true}
	fake.pots[pausedCrop.ID] = SimulatorPot{CropID: pausedCrop.ID, Managed: true}
	svc := NewService(repo, crops, client, fixedTime)

	svc.Reconcile(context.Background())

	calls := fake.snapshot()
	puts := map[string]bool{}
	deletes := map[string]bool{}
	var listed bool
	for _, call := range calls {
		switch call.method {
		case http.MethodGet:
			if call.path == "/v1/pots" {
				listed = true
			}
		case http.MethodPut:
			puts[strings.TrimPrefix(call.path, "/v1/pots/")] = true
		case http.MethodDelete:
			deletes[strings.TrimPrefix(call.path, "/v1/pots/")] = true
		}
	}
	if !listed {
		t.Fatal("reconcile must list the simulator pots first")
	}
	if !puts[activeCrop.ID] {
		t.Fatalf("active crop without a running simulation must be pushed: %+v", calls)
	}
	if puts[runningCrop.ID] {
		t.Fatal("an already running simulation must not be pushed again")
	}
	if !deletes[pausedCrop.ID] {
		t.Fatalf("a paused simulation still running must be removed: %+v", calls)
	}
	if puts[deletedCrop.ID] {
		t.Fatal("a configuration whose crop disappeared must not be pushed")
	}
	if len(repo.deleteByCropIDs) != 1 || repo.deleteByCropIDs[0].Hex() != deletedCrop.ID {
		t.Fatalf("the orphan configuration must be deleted: %v", repo.deleteByCropIDs)
	}
}

func TestReconcileSkipsWhenSimulatorIsDown(t *testing.T) {
	repo := newFakeRepo()
	fake, client := testSimulator(t)
	fake.listStatus = http.StatusInternalServerError
	svc := NewService(repo, &fakeCrops{}, client, fixedTime)

	svc.Reconcile(context.Background())
	if fake.count(http.MethodPut, "/v1/pots") != 0 {
		t.Fatalf("reconcile must abort when the list fails: %+v", fake.snapshot())
	}
}

func mustObjectID(t *testing.T, hex string) primitive.ObjectID {
	t.Helper()
	oid, err := primitive.ObjectIDFromHex(hex)
	if err != nil {
		t.Fatal(err)
	}
	return oid
}

func isStatus(err error, status int) bool {
	var apiErr *apperr.Error
	return errors.As(err, &apiErr) && apiErr.Status == status
}
