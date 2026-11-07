package actuators

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

type fakeRepo struct {
	duplicate bool
	inserted  []Actuator
}

func (r *fakeRepo) ListByCrop(context.Context, primitive.ObjectID) ([]Actuator, error) {
	return nil, nil
}
func (r *fakeRepo) FindByIDAndCrop(context.Context, primitive.ObjectID, primitive.ObjectID) (*Actuator, error) {
	return nil, nil
}
func (r *fakeRepo) FindByCropAndType(context.Context, primitive.ObjectID, Type) (*Actuator, error) {
	return nil, nil
}
func (r *fakeRepo) ExistsByCropAndType(context.Context, primitive.ObjectID, Type) (bool, error) {
	return false, nil
}
func (r *fakeRepo) Insert(_ context.Context, a *Actuator) error {
	if r.duplicate {
		return ErrDuplicate
	}
	r.inserted = append(r.inserted, *a)
	return nil
}
func (r *fakeRepo) UpdateState(context.Context, primitive.ObjectID, bool, *time.Time, time.Time) error {
	return nil
}
func (r *fakeRepo) Delete(context.Context, primitive.ObjectID) error       { return nil }
func (r *fakeRepo) DeleteByCrop(context.Context, primitive.ObjectID) error { return nil }

type fakeCrops struct{ err error }

func (f fakeCrops) RequireOwned(context.Context, string, string) error { return f.err }

func TestAddMapsDuplicateIndexToConflict(t *testing.T) {
	svc := NewService(&fakeRepo{duplicate: true}, fakeCrops{}, time.Now)
	_, err := svc.Add(context.Background(), primitive.NewObjectID().Hex(),
		primitive.NewObjectID().Hex(), WaterPump)
	var apiErr *apperr.Error
	if !errors.As(err, &apiErr) || apiErr.Status != 409 {
		t.Fatalf("expected 409 Conflict for duplicate actuator, got %v", err)
	}
}

func TestSeedDefaultsCreatesRealAndVirtualActuators(t *testing.T) {
	for _, tc := range []struct {
		virtual bool
		want    int
	}{{false, len(DefaultRealTypes)}, {true, len(AllTypes)}} {
		repo := &fakeRepo{}
		svc := NewService(repo, fakeCrops{}, time.Now)
		if err := svc.SeedDefaults(context.Background(), primitive.NewObjectID().Hex(), tc.virtual); err != nil {
			t.Fatal(err)
		}
		if len(repo.inserted) != tc.want {
			t.Fatalf("virtual=%v: expected %d actuators, got %d", tc.virtual, tc.want, len(repo.inserted))
		}
	}
}

func TestGetForCropRejectsInvalidAndMissingActuator(t *testing.T) {
	svc := NewService(&fakeRepo{}, fakeCrops{}, time.Now)
	cropID := primitive.NewObjectID().Hex()

	if _, err := svc.GetForCrop(context.Background(), cropID, "invalid"); !isActuatorStatus(err, 404) {
		t.Fatalf("an invalid actuator id must be 404, got %v", err)
	}
	if _, err := svc.GetForCrop(context.Background(), cropID, primitive.NewObjectID().Hex()); !isActuatorStatus(err, 404) {
		t.Fatalf("a missing actuator must be 404, got %v", err)
	}
}

func TestRemoveRequiresOwnedCrop(t *testing.T) {
	ownerErr := apperr.NotFound("Crop does not exist")
	svc := NewService(&fakeRepo{}, fakeCrops{err: ownerErr}, time.Now)

	err := svc.Remove(context.Background(), primitive.NewObjectID().Hex(),
		primitive.NewObjectID().Hex(), primitive.NewObjectID().Hex())
	if !errors.Is(err, ownerErr) {
		t.Fatalf("the crop ownership error must be propagated, got %v", err)
	}
}

func TestToResponseMapsRunningState(t *testing.T) {
	now := time.Date(2026, 11, 6, 12, 0, 0, 0, time.UTC)
	future := now.Add(time.Minute)
	past := now.Add(-time.Minute)

	cases := []struct {
		name         string
		actuator     Actuator
		wantRunning  bool
		wantUntilSet bool
	}{
		{"active without limit", Actuator{Active: true}, true, false},
		{"timed command still running", Actuator{Active: false, RunningUntil: &future}, true, true},
		{"timed command finished", Actuator{Active: false, RunningUntil: &past}, false, false},
		{"inactive", Actuator{Active: false}, false, false},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			tc.actuator.ID = primitive.NewObjectID()
			tc.actuator.CropID = primitive.NewObjectID()
			tc.actuator.Type = WaterPump
			res := ToResponse(&tc.actuator, now)
			if res.Running != tc.wantRunning {
				t.Fatalf("running = %v, want %v", res.Running, tc.wantRunning)
			}
			if (res.RunningUntil != nil) != tc.wantUntilSet {
				t.Fatalf("runningUntil present = %v, want %v", res.RunningUntil != nil, tc.wantUntilSet)
			}
		})
	}
}

func isActuatorStatus(err error, status int) bool {
	var apiErr *apperr.Error
	return errors.As(err, &apiErr) && apiErr.Status == status
}
