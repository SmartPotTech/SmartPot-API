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
