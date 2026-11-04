package readings

import (
	"context"
	"errors"
	"sync"
	"testing"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/cache"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/events"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

type fakeCrops struct{}

func (fakeCrops) Exists(context.Context, string) bool                { return true }
func (fakeCrops) RequireOwned(context.Context, string, string) error { return nil }
func (fakeCrops) MarkDeviceSeen(context.Context, string, *bool)      {}

type fakeRepo struct {
	mu         sync.Mutex
	saved      []Reading
	failBatch  bool
	rejectTemp float64
	latestHit  int
}

func (r *fakeRepo) Insert(_ context.Context, reading *Reading) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	if t := reading.Measures.Temperature; t != nil && *t == r.rejectTemp {
		return errors.New("invalid row")
	}
	r.saved = append(r.saved, *reading)
	return nil
}

func (r *fakeRepo) InsertBatch(_ context.Context, batch []Reading) error {
	if r.failBatch {
		return errors.New("lote rechazado")
	}
	r.mu.Lock()
	defer r.mu.Unlock()
	r.saved = append(r.saved, batch...)
	return nil
}

func (r *fakeRepo) Latest(context.Context, primitive.ObjectID) (*Reading, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.latestHit++
	if len(r.saved) == 0 {
		return nil, nil
	}
	last := r.saved[len(r.saved)-1]
	return &last, nil
}

func (r *fakeRepo) Recent(context.Context, primitive.ObjectID, int) ([]Reading, error) {
	return nil, nil
}
func (r *fakeRepo) Between(context.Context, primitive.ObjectID, time.Time, time.Time, int) ([]Reading, error) {
	return nil, nil
}
func (r *fakeRepo) DeleteByCrop(context.Context, primitive.ObjectID) error { return nil }
func (r *fakeRepo) Summary(context.Context, primitive.ObjectID, time.Time, time.Time) (int64, map[string]MetricSummary, error) {
	return 0, nil, nil
}
func (r *fakeRepo) SeriesAverages(context.Context, []primitive.ObjectID, string, time.Time, int) ([]Bucket, error) {
	return nil, nil
}

func temp(v float64) Measures { return Measures{Temperature: &v} }

func TestLatestComesFromTheSharedCacheAfterSaving(t *testing.T) {
	repo := &fakeRepo{}
	svc := NewService(repo, fakeCrops{}, cache.NewStore(nil), events.NewBus(), time.Now, 0, 100)
	crop := primitive.NewObjectID().Hex()
	if _, err := svc.RecordManual(context.Background(), "owner", crop, temp(21.5)); err != nil {
		t.Fatal(err)
	}
	got, err := svc.Latest(context.Background(), crop)
	if err != nil || got == nil || *got.Measures.Temperature != 21.5 {
		t.Fatalf("última lectura inesperada: %+v %v", got, err)
	}
	if repo.latestHit != 0 {
		t.Fatal("la última lectura debía salir de la caché, no de la base")
	}
}

func TestBatchPublishesOnlySavedReadings(t *testing.T) {
	repo := &fakeRepo{failBatch: true, rejectTemp: 22}
	bus := events.NewBus()
	published := make(chan Reading, 4)
	events.Subscribe(bus, func(e ReadingRecordedEvent) { published <- e.Reading })
	svc := NewService(repo, fakeCrops{}, cache.NewStore(nil), bus, time.Now, 0, 100)
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	svc.StartBatchWorker(ctx)

	good, _ := svc.store(ctx, primitive.NewObjectID().Hex(), temp(20), SourceMQTT)
	if _, err := svc.store(ctx, primitive.NewObjectID().Hex(), temp(22), SourceMQTT); err != nil {
		t.Fatal(err)
	}

	select {
	case r := <-published:
		if r.ID != good.ID {
			t.Fatalf("se publicó una lectura que no quedó guardada: %s", r.ID.Hex())
		}
	case <-time.After(2 * time.Second):
		t.Fatal("la lectura guardada no se publicó")
	}
	select {
	case r := <-published:
		t.Fatalf("no debía publicarse la lectura rechazada: %s", r.ID.Hex())
	case <-time.After(200 * time.Millisecond):
	}
	svc.StopBatchWorker()
}
