package overview

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/actuators"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/ai"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/cache"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/commands"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/config"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/crops"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/notifications"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/events"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/readings"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

var overviewNow = time.Date(2026, 11, 6, 15, 30, 0, 0, time.UTC)

// Los dobles embeben el puerto y solo implementan los métodos que usa el panel.
type fakeCropsRepo struct {
	crops.Repository
	list    []crops.Crop
	listErr error
	owner   primitive.ObjectID
	calls   int
}

func (r *fakeCropsRepo) ListByOwner(_ context.Context, owner primitive.ObjectID) ([]crops.Crop, error) {
	r.calls++
	r.owner = owner
	return r.list, r.listErr
}

type fakeReadingsRepo struct {
	readings.Repository
	latest map[string]*readings.Reading

	buckets    []readings.Bucket
	seriesErr  error
	latestHits int
	seriesHits int
	metric     string
	bucket     int
	from       time.Time
	ids        []primitive.ObjectID
}

func (r *fakeReadingsRepo) LatestByCropIDs(_ context.Context, cropIDs []primitive.ObjectID) (map[string]*readings.Reading, error) {
	r.latestHits++
	out := make(map[string]*readings.Reading, len(cropIDs))
	for _, id := range cropIDs {
		if reading, ok := r.latest[id.Hex()]; ok {
			out[id.Hex()] = reading
		}
	}
	return out, nil
}

func (r *fakeReadingsRepo) SeriesAverages(_ context.Context, cropIDs []primitive.ObjectID, metric string,
	from time.Time, bucket int) ([]readings.Bucket, error) {
	r.seriesHits++
	r.ids, r.metric, r.from, r.bucket = cropIDs, metric, from, bucket
	return r.buckets, r.seriesErr
}

type fakeActuatorsRepo struct {
	actuators.Repository
	byCrop    map[string][]actuators.Actuator
	hits      int
	batchHits int
}

func (r *fakeActuatorsRepo) ListByCrop(_ context.Context, cropID primitive.ObjectID) ([]actuators.Actuator, error) {
	r.hits++
	return r.byCrop[cropID.Hex()], nil
}

func (r *fakeActuatorsRepo) ListByCrops(_ context.Context, cropIDs []primitive.ObjectID) (map[string][]actuators.Actuator, error) {
	r.batchHits++
	out := make(map[string][]actuators.Actuator, len(cropIDs))
	for _, id := range cropIDs {
		if items, ok := r.byCrop[id.Hex()]; ok {
			out[id.Hex()] = items
		}
	}
	return out, nil
}

type fakeCommandsRepo struct {
	commands.Repository
	count    int64
	countErr error
	hits     int
	since    time.Time
	ids      []primitive.ObjectID
}

func (r *fakeCommandsRepo) CountByCropsSince(_ context.Context, cropIDs []primitive.ObjectID, since time.Time) (int64, error) {
	r.hits++
	r.ids, r.since = cropIDs, since
	return r.count, r.countErr
}

type fakeNotificationsRepo struct {
	notifications.Repository
	unread    int64
	unreadErr error
	hits      int
}

func (r *fakeNotificationsRepo) CountUnread(context.Context, primitive.ObjectID) (int64, error) {
	r.hits++
	return r.unread, r.unreadErr
}

type overviewFixture struct {
	service           *Service
	cropsRepo         *fakeCropsRepo
	readingsRepo      *fakeReadingsRepo
	actuatorsRepo     *fakeActuatorsRepo
	commandsRepo      *fakeCommandsRepo
	notificationsRepo *fakeNotificationsRepo
}

func newOverviewFixture(aiClient *ai.Client) *overviewFixture {
	clock := func() time.Time { return overviewNow }
	f := &overviewFixture{
		cropsRepo:         &fakeCropsRepo{},
		readingsRepo:      &fakeReadingsRepo{latest: map[string]*readings.Reading{}},
		actuatorsRepo:     &fakeActuatorsRepo{byCrop: map[string][]actuators.Actuator{}},
		commandsRepo:      &fakeCommandsRepo{},
		notificationsRepo: &fakeNotificationsRepo{},
	}
	cropsService := crops.NewService(f.cropsRepo, nil, crops.MQTTPublic{}, nil, nil, nil, events.NewBus(), clock)
	readingsService := readings.NewService(f.readingsRepo, nil, cache.NewStore(nil), events.NewBus(), clock, 0, 100)
	actuatorsService := actuators.NewService(f.actuatorsRepo, nil, clock)
	commandsService := commands.NewService(f.commandsRepo, nil, nil, nil, clock, time.Minute)
	notificationsService := notifications.NewService(f.notificationsRepo, cache.NewStore(nil), events.NewBus(), clock)
	if aiClient == nil {
		aiClient = ai.NewClient(config.AI{Enabled: false}, nil, clock)
	}
	f.service = NewService(cropsService, readingsService, actuatorsService, commandsService,
		notificationsService, aiClient, cache.NewStore(nil), clock, time.UTC)
	return f
}

func newTestCrop(owner primitive.ObjectID, name string) crops.Crop {
	return crops.Crop{
		ID:        primitive.NewObjectID(),
		OwnerID:   owner,
		Name:      name,
		Type:      crops.TypeLettuce,
		Kind:      crops.KindVirtual,
		Form:      crops.FormPot,
		CreatedAt: overviewNow,
		UpdatedAt: overviewNow,
	}
}

func onlineDevice(online bool) *crops.Device {
	return &crops.Device{Online: online}
}

func healthAt(index float64) *crops.CropHealth {
	return &crops.CropHealth{Index: index, Level: "GOOD", Label: "Healthy", EvaluatedAt: overviewNow}
}

func TestOverviewWithNoCrops(t *testing.T) {
	f := newOverviewFixture(nil)
	owner := primitive.NewObjectID()

	res, err := f.service.Overview(context.Background(), owner.Hex())
	if err != nil {
		t.Fatal(err)
	}
	if res.Totals.Crops != 0 || res.Totals.Online != 0 || res.Totals.Automated != 0 ||
		res.Totals.NeedsAttention != 0 || res.Totals.UnreadAlerts != 0 || res.Totals.CommandsLast24h != 0 {
		t.Fatalf("unexpected totals without crops: %+v", res.Totals)
	}
	if res.Totals.AverageHealth != nil {
		t.Fatalf("average health must be absent without crops: %v", *res.Totals.AverageHealth)
	}
	if res.Crops == nil || len(res.Crops) != 0 {
		t.Fatalf("crops must be an empty list: %#v", res.Crops)
	}
	if f.cropsRepo.owner != owner {
		t.Fatalf("the owner filter was not forwarded: %s", f.cropsRepo.owner.Hex())
	}
	if !f.commandsRepo.since.Equal(overviewNow.Add(-24 * time.Hour)) {
		t.Fatalf("commands window = %v, want %v", f.commandsRepo.since, overviewNow.Add(-24*time.Hour))
	}
}

func TestOverviewCountsCropsWithoutReadings(t *testing.T) {
	f := newOverviewFixture(nil)
	owner := primitive.NewObjectID()

	online := newTestCrop(owner, "Lettuce")
	online.Device = onlineDevice(true)
	online.Health = healthAt(90)
	online.AutomationEnabled = true

	offline := newTestCrop(owner, "Basil")
	offline.Device = onlineDevice(false)
	offline.Health = healthAt(60)

	unplugged := newTestCrop(owner, "Spinach")
	unplugged.Device = nil

	f.cropsRepo.list = []crops.Crop{online, offline, unplugged}
	f.commandsRepo.count = 5
	f.notificationsRepo.unread = 2

	res, err := f.service.Overview(context.Background(), owner.Hex())
	if err != nil {
		t.Fatal(err)
	}
	if res.Totals.Crops != 3 || res.Totals.Online != 1 || res.Totals.Automated != 1 {
		t.Fatalf("unexpected totals: %+v", res.Totals)
	}
	if res.Totals.NeedsAttention != 2 {
		t.Fatalf("offline crops must need attention: %+v", res.Totals)
	}
	if res.Totals.AverageHealth == nil || *res.Totals.AverageHealth != 75 {
		t.Fatalf("average health = %v, want 75", res.Totals.AverageHealth)
	}
	if res.Totals.UnreadAlerts != 2 || res.Totals.CommandsLast24h != 5 {
		t.Fatalf("unexpected counters: %+v", res.Totals)
	}
	if len(res.Crops) != 3 {
		t.Fatalf("crops = %d, want 3", len(res.Crops))
	}
	for _, crop := range res.Crops {
		if crop.LatestReading != nil {
			t.Fatalf("crop without readings must not expose one: %+v", crop.LatestReading)
		}
	}
	if f.readingsRepo.latestHits != 1 {
		t.Fatalf("latest readings must be fetched once, got %d", f.readingsRepo.latestHits)
	}
}

func TestOverviewRoundsHealthAndAttachesLatestReadings(t *testing.T) {
	f := newOverviewFixture(nil)
	owner := primitive.NewObjectID()

	first := newTestCrop(owner, "Tomato")
	first.Device = onlineDevice(true)
	first.Health = healthAt(70.54)
	first.AutomationEnabled = true

	second := newTestCrop(owner, "Pepper")
	second.Device = onlineDevice(true)
	second.Health = healthAt(80.06)

	noHealth := newTestCrop(owner, "Strawberry")
	noHealth.Device = onlineDevice(true)

	f.cropsRepo.list = []crops.Crop{first, second, noHealth}
	f.commandsRepo.count = 9
	f.notificationsRepo.unread = 1
	temperature := 22.75
	f.readingsRepo.latest[first.HexID()] = &readings.Reading{
		ID: primitive.NewObjectID(), CropID: first.ID, MeasuredAt: overviewNow.Add(-time.Minute),
		Measures: readings.Measures{Temperature: &temperature},
	}

	res, err := f.service.Overview(context.Background(), owner.Hex())
	if err != nil {
		t.Fatal(err)
	}
	if res.Totals.AverageHealth == nil || *res.Totals.AverageHealth != 75.3 {
		t.Fatalf("average health = %v, want 75.3", res.Totals.AverageHealth)
	}
	if res.Totals.NeedsAttention != 0 {
		t.Fatalf("all online crops with good health must be fine: %+v", res.Totals)
	}
	if res.Crops[0].LatestReading == nil || res.Crops[0].LatestReading.Measures.Temperature == nil ||
		*res.Crops[0].LatestReading.Measures.Temperature != 22.75 {
		t.Fatalf("latest reading was not attached: %+v", res.Crops[0].LatestReading)
	}
	if res.Crops[1].LatestReading != nil || res.Crops[2].LatestReading != nil {
		t.Fatal("crops without a latest reading must not expose one")
	}
	if len(f.commandsRepo.ids) != 3 {
		t.Fatalf("command count must cover every crop, got %d ids", len(f.commandsRepo.ids))
	}
}

func TestOverviewPropagatesServiceErrors(t *testing.T) {
	f := newOverviewFixture(nil)
	owner := primitive.NewObjectID()

	f.cropsRepo.listErr = errors.New("database is paused")
	if _, err := f.service.Overview(context.Background(), owner.Hex()); err == nil {
		t.Fatal("a crops query error must be reported")
	}

	f.cropsRepo.listErr = nil
	f.commandsRepo.countErr = errors.New("database is paused")
	if _, err := f.service.Overview(context.Background(), owner.Hex()); err == nil {
		t.Fatal("a command count error must be reported")
	}

	f.commandsRepo.countErr = nil
	f.notificationsRepo.unreadErr = errors.New("database is paused")
	if _, err := f.service.Overview(context.Background(), owner.Hex()); err == nil {
		t.Fatal("an unread count error must be reported")
	}
}

func TestSeriesUsesWindowSpecificBuckets(t *testing.T) {
	cases := []struct {
		hours  int
		bucket int
	}{
		{6, 10},
		{24, 30},
		{168, 180},
	}
	for _, tc := range cases {
		t.Run(time.Duration(tc.hours).String(), func(t *testing.T) {
			f := newOverviewFixture(nil)
			owner := primitive.NewObjectID()
			first := newTestCrop(owner, "Tomato")
			second := newTestCrop(owner, "Basil")
			f.cropsRepo.list = []crops.Crop{first, second}

			t1 := overviewNow.Add(-2 * time.Hour)
			t2 := overviewNow.Add(-time.Hour)
			f.readingsRepo.buckets = []readings.Bucket{
				{CropID: first.HexID(), Time: t1, Value: 21.456},
				{CropID: second.HexID(), Time: t1, Value: 30},
				{CropID: first.HexID(), Time: t2, Value: 19.999},
			}

			res, err := f.service.Series(context.Background(), owner.Hex(), "temperature", tc.hours)
			if err != nil {
				t.Fatal(err)
			}
			if res.Metric != "temperature" || res.Hours != tc.hours || res.BucketMinutes != tc.bucket {
				t.Fatalf("unexpected series header: %+v", res)
			}
			if len(res.Series) != 2 {
				t.Fatalf("series = %d, want 2", len(res.Series))
			}
			if res.Series[0].CropID != first.HexID() || len(res.Series[0].Points) != 2 {
				t.Fatalf("unexpected first series: %+v", res.Series[0])
			}
			if res.Series[0].Points[0].Value != 21.46 || res.Series[0].Points[1].Value != 20 {
				t.Fatalf("points were not rounded: %+v", res.Series[0].Points)
			}
			if res.Series[1].CropID != second.HexID() || len(res.Series[1].Points) != 1 {
				t.Fatalf("unexpected second series: %+v", res.Series[1])
			}
			if f.readingsRepo.bucket != tc.bucket || f.readingsRepo.metric != "temperature" {
				t.Fatalf("unexpected repository arguments: metric=%q bucket=%d",
					f.readingsRepo.metric, f.readingsRepo.bucket)
			}
			if !f.readingsRepo.from.Equal(overviewNow.Add(-time.Duration(tc.hours) * time.Hour)) {
				t.Fatalf("from = %v, want %v", f.readingsRepo.from,
					overviewNow.Add(-time.Duration(tc.hours)*time.Hour))
			}
			if len(f.readingsRepo.ids) != 2 {
				t.Fatalf("series must query every crop, got %d ids", len(f.readingsRepo.ids))
			}
		})
	}
}

func TestSeriesCachesRepeatedQueries(t *testing.T) {
	f := newOverviewFixture(nil)
	owner := primitive.NewObjectID()
	crop := newTestCrop(owner, "Tomato")
	f.cropsRepo.list = []crops.Crop{crop}
	f.readingsRepo.buckets = []readings.Bucket{{CropID: crop.HexID(), Time: overviewNow, Value: 20}}

	if _, err := f.service.Series(context.Background(), owner.Hex(), "temperature", 24); err != nil {
		t.Fatal(err)
	}
	if _, err := f.service.Series(context.Background(), owner.Hex(), "temperature", 24); err != nil {
		t.Fatal(err)
	}
	if f.readingsRepo.seriesHits != 1 || f.cropsRepo.calls != 1 {
		t.Fatalf("the second query must come from the cache: readings=%d crops=%d",
			f.readingsRepo.seriesHits, f.cropsRepo.calls)
	}
}

func TestSeriesRejectsInvalidMetricAndWindow(t *testing.T) {
	f := newOverviewFixture(nil)
	owner := primitive.NewObjectID()

	for _, metric := range []string{"pressure", "Temperature", ""} {
		_, err := f.service.Series(context.Background(), owner.Hex(), metric, 24)
		if !isOverviewStatus(err, http.StatusBadRequest) {
			t.Fatalf("metric %q must be 400, got %v", metric, err)
		}
	}
	for _, hours := range []int{0, -1, 169} {
		_, err := f.service.Series(context.Background(), owner.Hex(), "temperature", hours)
		if !isOverviewStatus(err, http.StatusBadRequest) {
			t.Fatalf("window %d must be 400, got %v", hours, err)
		}
	}
	if f.readingsRepo.seriesHits != 0 || f.cropsRepo.calls != 0 {
		t.Fatalf("invalid queries must not reach the repository: readings=%d crops=%d",
			f.readingsRepo.seriesHits, f.cropsRepo.calls)
	}
}

func TestSeriesWithNoCropsReturnsEmptySeries(t *testing.T) {
	f := newOverviewFixture(nil)
	owner := primitive.NewObjectID()

	res, err := f.service.Series(context.Background(), owner.Hex(), "humidity", 6)
	if err != nil {
		t.Fatal(err)
	}
	if res.Series == nil || len(res.Series) != 0 {
		t.Fatalf("series must be an empty list: %#v", res.Series)
	}
	if res.BucketMinutes != 10 {
		t.Fatalf("bucket = %d, want 10", res.BucketMinutes)
	}
}

func TestFleetWithoutCropsDoesNotCallAI(t *testing.T) {
	f := newOverviewFixture(nil)
	owner := primitive.NewObjectID()

	res, err := f.service.Fleet(context.Background(), owner.Hex())
	if err != nil {
		t.Fatalf("an empty fleet must not call the disabled AI: %v", err)
	}
	if res.Summary == "" {
		t.Fatal("the empty fleet must explain how to start")
	}
	if res.Crops == nil || res.SharedIssues == nil || res.Groups == nil || res.Actions == nil {
		t.Fatalf("empty fleet lists must not be nil: %+v", res)
	}
	if len(res.Crops) != 0 || len(res.SharedIssues) != 0 || len(res.Groups) != 0 || len(res.Actions) != 0 {
		t.Fatalf("unexpected empty fleet content: %+v", res)
	}
	if res.AverageHealth != nil {
		t.Fatalf("empty fleet must not report an average: %v", *res.AverageHealth)
	}
}

func TestFleetBuildsAIInputWithReadingsAndActuators(t *testing.T) {
	var got ai.FleetRequest
	var gotPath, gotAuth string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotPath, gotAuth = r.URL.Path, r.Header.Get("Authorization")
		body, _ := io.ReadAll(r.Body)
		if err := json.Unmarshal(body, &got); err != nil {
			t.Errorf("invalid AI request: %v", err)
		}
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(ai.FleetResponse{
			AverageHealth: floatPtr(82.5),
			Crops:         []ai.FleetCropResult{{ID: "crop-1", Name: "Tomato", Rank: intPtr(1)}},
			SharedIssues:  []ai.SharedIssue{},
			Groups:        []ai.Group{},
			Actions:       []ai.FleetAction{},
			Summary:       "One crop needs less water.",
		})
	}))
	defer server.Close()

	clock := func() time.Time { return overviewNow }
	aiClient := ai.NewClient(config.AI{Enabled: true, BaseURL: server.URL, Token: "ai-token", Timeout: 2 * time.Second},
		nil, clock)
	f := newOverviewFixture(aiClient)
	owner := primitive.NewObjectID()

	first := newTestCrop(owner, "Tomato")
	first.Device = onlineDevice(true)
	second := newTestCrop(owner, "Basil")
	second.Device = onlineDevice(true)
	f.cropsRepo.list = []crops.Crop{first, second}
	temperature := 23.5
	f.readingsRepo.latest[first.HexID()] = &readings.Reading{CropID: first.ID, Measures: readings.Measures{Temperature: &temperature}}
	f.actuatorsRepo.byCrop[first.HexID()] = []actuators.Actuator{{ID: primitive.NewObjectID(), CropID: first.ID, Type: actuators.WaterPump}}
	f.actuatorsRepo.byCrop[second.HexID()] = []actuators.Actuator{{ID: primitive.NewObjectID(), CropID: second.ID, Type: actuators.Fan}}

	res, err := f.service.Fleet(context.Background(), owner.Hex())
	if err != nil {
		t.Fatal(err)
	}
	if gotPath != "/v1/fleet" || gotAuth != "Bearer ai-token" {
		t.Fatalf("unexpected AI call: path=%q auth=%q", gotPath, gotAuth)
	}
	if len(got.Crops) != 2 {
		t.Fatalf("AI input crops = %d, want 2", len(got.Crops))
	}
	if got.Crops[0].ID != first.HexID() || got.Crops[0].Measures == nil || got.Crops[0].Measures.Temperature == nil ||
		*got.Crops[0].Measures.Temperature != 23.5 {
		t.Fatalf("latest reading was not sent: %+v", got.Crops[0])
	}
	if len(got.Crops[0].Actuators) != 1 || got.Crops[0].Actuators[0] != "WATER_PUMP" {
		t.Fatalf("actuators were not sent: %+v", got.Crops[0].Actuators)
	}
	if got.Crops[1].Measures != nil {
		t.Fatalf("a crop without readings must omit measures: %+v", got.Crops[1])
	}
	if got.LocalHour == nil || *got.LocalHour != 15 {
		t.Fatalf("local hour = %v, want 15", got.LocalHour)
	}
	if res.AverageHealth == nil || *res.AverageHealth != 82.5 || len(res.Crops) != 1 || res.Summary == "" {
		t.Fatalf("unexpected fleet response: %+v", res)
	}
	if f.actuatorsRepo.batchHits != 1 || f.actuatorsRepo.hits != 0 {
		t.Fatalf("expected single batched actuator query (batch=%d, perCrop=%d)", f.actuatorsRepo.batchHits, f.actuatorsRepo.hits)
	}
}

func TestFleetPropagatesAIError(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
		_, _ = w.Write([]byte(`{"message":"trainer is down"}`))
	}))
	defer server.Close()

	clock := func() time.Time { return overviewNow }
	aiClient := ai.NewClient(config.AI{Enabled: true, BaseURL: server.URL, Token: "ai-token", Timeout: 2 * time.Second},
		nil, clock)
	f := newOverviewFixture(aiClient)
	owner := primitive.NewObjectID()
	f.cropsRepo.list = []crops.Crop{newTestCrop(owner, "Tomato")}

	_, err := f.service.Fleet(context.Background(), owner.Hex())
	if !isOverviewStatus(err, http.StatusInternalServerError) {
		t.Fatalf("an AI failure must be propagated, got %v", err)
	}
}

func isOverviewStatus(err error, status int) bool {
	var apiErr *apperr.Error
	return errors.As(err, &apiErr) && apiErr.Status == status
}

func floatPtr(v float64) *float64 { return &v }
func intPtr(v int) *int           { return &v }
