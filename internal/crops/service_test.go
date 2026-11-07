package crops

import (
	"context"
	"encoding/base64"
	"errors"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/notifications"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/events"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/geo"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/security"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

type fakeRepo struct {
	mu               sync.Mutex
	crop             *Crop
	findErr          error
	insertQuotaErr   error
	insertQuotaCalls int
	maxTotal         int
	maxVirtual       int
	markSeen         int
	deviceKeyUpdates int
	saveCalls        int
}

func (r *fakeRepo) ListByOwner(context.Context, primitive.ObjectID) ([]Crop, error) {
	if r.crop == nil {
		return nil, r.findErr
	}
	return []Crop{*r.crop}, r.findErr
}
func (r *fakeRepo) FindByIDAndOwner(context.Context, primitive.ObjectID, primitive.ObjectID) (*Crop, error) {
	return r.crop, r.findErr
}
func (r *fakeRepo) FindByID(context.Context, primitive.ObjectID) (*Crop, error) {
	return r.crop, r.findErr
}
func (r *fakeRepo) FindWithDeviceKey(context.Context) ([]Crop, error) { return nil, nil }
func (r *fakeRepo) CountByOwner(context.Context, primitive.ObjectID) (int64, error) {
	return 0, nil
}
func (r *fakeRepo) Insert(_ context.Context, crop *Crop) error {
	if crop.ID.IsZero() {
		crop.ID = primitive.NewObjectID()
	}
	return nil
}
func (r *fakeRepo) InsertWithQuota(_ context.Context, crop *Crop, maxTotal, maxVirtual int) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.insertQuotaCalls++
	r.maxTotal, r.maxVirtual = maxTotal, maxVirtual
	if r.insertQuotaErr != nil {
		return r.insertQuotaErr
	}
	if crop.ID.IsZero() {
		crop.ID = primitive.NewObjectID()
	}
	return nil
}
func (r *fakeRepo) Save(context.Context, *Crop) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.saveCalls++
	return nil
}
func (r *fakeRepo) UpdateDeviceKey(context.Context, primitive.ObjectID, string, time.Time, time.Time) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.deviceKeyUpdates++
	return nil
}
func (r *fakeRepo) Delete(context.Context, primitive.ObjectID) error { return nil }
func (r *fakeRepo) UpdatePlacement(context.Context, primitive.ObjectID, *geo.Placement, time.Time) error {
	return nil
}
func (r *fakeRepo) MarkDeviceSeen(context.Context, primitive.ObjectID, *bool, time.Time) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.markSeen++
	return nil
}
func (r *fakeRepo) UpdateHealth(context.Context, primitive.ObjectID, *CropHealth) error { return nil }
func (r *fakeRepo) BackfillKind(context.Context, []primitive.ObjectID, CropKind) error  { return nil }
func (r *fakeRepo) BackfillKindAll(context.Context, CropKind) error                     { return nil }
func (r *fakeRepo) BackfillFormAll(context.Context, CropForm) error                     { return nil }
func (r *fakeRepo) BackfillPlacement(context.Context, primitive.ObjectID, geo.Placement) error {
	return nil
}

type fakeSeeder struct{}

func (fakeSeeder) SeedDefaults(context.Context, string, bool) error { return nil }

type fakeProvisioner struct {
	mu    sync.Mutex
	order *[]string
}

func (p *fakeProvisioner) Provision(context.Context, string, string, bool) error {
	p.mu.Lock()
	defer p.mu.Unlock()
	if p.order != nil {
		*p.order = append(*p.order, "kick")
	}
	return nil
}
func (p *fakeProvisioner) Deprovision(context.Context, string) error { return nil }

type fakeNotifier struct{ calls int }

func (f *fakeNotifier) NotifyOnce(context.Context, string, time.Duration, string, string,
	notifications.Type, string, string) (bool, error) {
	f.calls++
	return true, nil
}

func testService(t *testing.T, repo Repository, notifier Notifier) (*Service, *events.Bus) {
	t.Helper()
	key := base64.StdEncoding.EncodeToString([]byte("0123456789abcdef0123456789abcdef"))
	encryptor, err := security.NewEncryptor(key)
	if err != nil {
		t.Fatal(err)
	}
	bus := events.NewBus()
	svc := NewService(repo, encryptor, MQTTPublic{}, &fakeProvisioner{}, fakeSeeder{}, notifier, bus, time.Now)
	return svc, bus
}

func cropRequest(kind CropKind) CropRequest {
	cropType := TypeLettuce
	return CropRequest{Name: "Lettuce", Type: &cropType, Kind: &kind}
}

func TestCreateMapsQuotaErrors(t *testing.T) {
	cases := []struct {
		name   string
		kind   CropKind
		err    error
		status int
		want   string
	}{
		{"total", KindReal, ErrCropLimit, 400, "maximum of 20 crops"},
		{"virtual", KindVirtual, ErrVirtualCropLimit, 409, "up to 5 virtual crops"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			repo := &fakeRepo{insertQuotaErr: tc.err}
			svc, _ := testService(t, repo, &fakeNotifier{})
			_, err := svc.Create(context.Background(), primitive.NewObjectID().Hex(), cropRequest(tc.kind))
			var apiErr *apperr.Error
			if !errors.As(err, &apiErr) || apiErr.Status != tc.status || !strings.Contains(apiErr.Message, tc.want) {
				t.Fatalf("expected %d %q, got %v", tc.status, tc.want, err)
			}
		})
	}
}

func TestCreateEnforcesAccountAndVirtualLimitsInOneTransaction(t *testing.T) {
	repo := &fakeRepo{}
	svc, _ := testService(t, repo, &fakeNotifier{})
	if _, err := svc.Create(context.Background(), primitive.NewObjectID().Hex(), cropRequest(KindVirtual)); err != nil {
		t.Fatal(err)
	}
	if repo.insertQuotaCalls != 1 || repo.maxTotal != MaxCropsPerUser || repo.maxVirtual != 5 {
		t.Fatalf("quota transaction called with total=%d virtual=%d calls=%d",
			repo.maxTotal, repo.maxVirtual, repo.insertQuotaCalls)
	}
}

func TestUpdateDeviceStatusSkipsUnchangedState(t *testing.T) {
	owner := primitive.NewObjectID()
	crop := &Crop{ID: primitive.NewObjectID(), OwnerID: owner, Name: "Lettuce",
		Device: &Device{Online: true}}
	repo := &fakeRepo{crop: crop}
	notifier := &fakeNotifier{}
	svc, _ := testService(t, repo, notifier)

	svc.UpdateDeviceStatus(context.Background(), crop.HexID(), true)
	if repo.markSeen != 0 || notifier.calls != 0 {
		t.Fatalf("unchanged ONLINE must not write or notify; seen=%d notices=%d", repo.markSeen, notifier.calls)
	}
	svc.UpdateDeviceStatus(context.Background(), crop.HexID(), false)
	if repo.markSeen != 1 || notifier.calls != 1 {
		t.Fatalf("state change must write once and notify once; seen=%d notices=%d", repo.markSeen, notifier.calls)
	}
}

func TestRotateDeviceKeyInvalidatesCacheBeforeKick(t *testing.T) {
	owner := primitive.NewObjectID()
	crop := &Crop{ID: primitive.NewObjectID(), OwnerID: owner, Name: "Lettuce",
		Type: TypeLettuce, Kind: KindReal, Device: &Device{KeyCiphertext: "old"}}
	repo := &fakeRepo{crop: crop}
	order := make([]string, 0, 2)
	key := base64.StdEncoding.EncodeToString([]byte("0123456789abcdef0123456789abcdef"))
	encryptor, err := security.NewEncryptor(key)
	if err != nil {
		t.Fatal(err)
	}
	bus := events.NewBus()
	events.Subscribe(bus, func(DeviceKeyRotatedEvent) {
		order = append(order, "invalidate")
	})
	svc := NewService(repo, encryptor, MQTTPublic{}, &fakeProvisioner{order: &order}, fakeSeeder{},
		&fakeNotifier{}, bus, time.Now)

	if _, err := svc.RotateDeviceKey(context.Background(), owner.Hex(), crop.HexID()); err != nil {
		t.Fatal(err)
	}
	if repo.deviceKeyUpdates != 1 || repo.saveCalls != 0 {
		t.Fatalf("rotation must use UpdateDeviceKey only; updates=%d saves=%d",
			repo.deviceKeyUpdates, repo.saveCalls)
	}
	if len(order) != 2 || order[0] != "invalidate" || order[1] != "kick" {
		t.Fatalf("cache must be invalidated before kicking sessions, got %v", order)
	}
}

func TestExistsPropagatesDatabaseError(t *testing.T) {
	repo := &fakeRepo{findErr: errors.New("database paused")}
	svc, _ := testService(t, repo, &fakeNotifier{})
	exists, err := svc.Exists(context.Background(), primitive.NewObjectID().Hex())
	if exists || err == nil {
		t.Fatalf("a database error must not be reported as a missing crop: exists=%v err=%v", exists, err)
	}
}
