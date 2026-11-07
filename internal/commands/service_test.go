package commands

import (
	"context"
	"errors"
	"sync"
	"testing"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/actuators"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/crops"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/notifications"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

// fakeCommandRepo imita las transiciones condicionales de PostgreSQL.
type fakeCommandRepo struct {
	mu        sync.Mutex
	commands  map[string]*Command
	insertErr error
	stale     []Command
}

func newFakeCommandRepo() *fakeCommandRepo {
	return &fakeCommandRepo{commands: make(map[string]*Command)}
}

func (r *fakeCommandRepo) Insert(_ context.Context, c *Command) error {
	if r.insertErr != nil {
		return r.insertErr
	}
	r.mu.Lock()
	defer r.mu.Unlock()
	if c.ID.IsZero() {
		c.ID = primitive.NewObjectID()
	}
	copy := *c
	r.commands[c.ID.Hex()] = &copy
	return nil
}

func (r *fakeCommandRepo) MarkSent(_ context.Context, id primitive.ObjectID, at time.Time) (bool, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	c, ok := r.commands[id.Hex()]
	if !ok || c.Status != StatusPending {
		return false, nil
	}
	c.Status = StatusSent
	c.SentAt = &at
	return true, nil
}

func (r *fakeCommandRepo) Complete(_ context.Context, id primitive.ObjectID, status Status, message string, at time.Time) (*Command, bool, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	c, ok := r.commands[id.Hex()]
	if !ok || (c.Status != StatusPending && c.Status != StatusSent) {
		return nil, false, nil
	}
	c.Status = status
	c.Message = message
	c.CompletedAt = &at
	copy := *c
	return &copy, true, nil
}

func (r *fakeCommandRepo) Expire(_ context.Context, id primitive.ObjectID, message string, at time.Time) (*Command, bool, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	c, ok := r.commands[id.Hex()]
	if !ok || (c.Status != StatusPending && c.Status != StatusSent) {
		return nil, false, nil
	}
	c.Status = StatusExpired
	c.Message = message
	c.CompletedAt = &at
	copy := *c
	return &copy, true, nil
}

func (r *fakeCommandRepo) Save(_ context.Context, c *Command) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	copy := *c
	r.commands[c.ID.Hex()] = &copy
	return nil
}

func (r *fakeCommandRepo) ListByCrop(context.Context, primitive.ObjectID, int) ([]Command, error) {
	return nil, nil
}
func (r *fakeCommandRepo) ListByCrops(context.Context, []primitive.ObjectID, int) ([]Command, error) {
	return nil, nil
}
func (r *fakeCommandRepo) CountByCropsSince(context.Context, []primitive.ObjectID, time.Time) (int64, error) {
	return 0, nil
}
func (r *fakeCommandRepo) FindByIDAndCrop(_ context.Context, id, cropID primitive.ObjectID) (*Command, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	c, ok := r.commands[id.Hex()]
	if !ok || c.CropID != cropID {
		return nil, nil
	}
	copy := *c
	return &copy, nil
}
func (r *fakeCommandRepo) FindStaleSent(context.Context, time.Time) ([]Command, error) {
	return r.stale, nil
}
func (r *fakeCommandRepo) ExistsInFlight(context.Context, primitive.ObjectID) (bool, error) {
	return false, nil
}
func (r *fakeCommandRepo) DeleteByCrop(context.Context, primitive.ObjectID) error { return nil }
func (r *fakeCommandRepo) DeleteOlderThan(context.Context, time.Time) (int64, error) {
	return 0, nil
}

type fakeCrops struct {
	crop *crops.Crop
	err  error
}

func (f fakeCrops) GetOwned(context.Context, string, string) (*crops.Crop, error) {
	return f.crop, f.err
}
func (f fakeCrops) Find(context.Context, string) (*crops.Crop, error) { return f.crop, f.err }
func (f fakeCrops) List(context.Context, string) ([]crops.Crop, error) {
	if f.crop == nil {
		return nil, f.err
	}
	return []crops.Crop{*f.crop}, f.err
}

type fakeActuators struct {
	actuator *actuators.Actuator
	err      error
	updates  int
}

func (f *fakeActuators) GetForCrop(context.Context, string, string) (*actuators.Actuator, error) {
	return f.actuator, f.err
}
func (f *fakeActuators) FindByType(context.Context, string, actuators.Type) (*actuators.Actuator, error) {
	return f.actuator, f.err
}
func (f *fakeActuators) UpdateState(context.Context, string, bool, *time.Time) { f.updates++ }

type fakeNotifier struct{ calls int }

func (f *fakeNotifier) NotifyOnce(context.Context, string, time.Duration, string, string,
	notifications.Type, string, string) (bool, error) {
	f.calls++
	return true, nil
}

type fakePublisher struct {
	enabled bool
	sent    int
	ok      bool
}

func (f *fakePublisher) IsEnabled() bool { return f.enabled }
func (f *fakePublisher) PublishCommand(string, string) bool {
	f.sent++
	return f.ok
}

func testCommand() (*Command, *actuators.Actuator, *crops.Crop) {
	crop := &crops.Crop{ID: primitive.NewObjectID(), OwnerID: primitive.NewObjectID(), Name: "Lettuce"}
	actuator := &actuators.Actuator{ID: primitive.NewObjectID(), CropID: crop.ID, Type: actuators.WaterPump}
	cmd := &Command{ID: primitive.NewObjectID(), CropID: crop.ID, ActuatorID: actuator.ID,
		ActuatorType: actuators.WaterPump, Action: Activate, Status: StatusPending, Source: SourceUser}
	return cmd, actuator, crop
}

func TestRequestMapsInFlightIndexViolationToConflict(t *testing.T) {
	repo := newFakeCommandRepo()
	repo.insertErr = ErrInFlight
	_, actuator, crop := testCommand()
	action := Activate
	svc := NewService(repo, fakeCrops{crop: crop}, &fakeActuators{actuator: actuator}, &fakeNotifier{},
		time.Now, time.Minute)
	svc.SetPublisher(&fakePublisher{enabled: true, ok: true})

	_, err := svc.Request(context.Background(), crop.OwnerID.Hex(), crop.HexID(),
		CommandRequest{ActuatorID: actuator.ID.Hex(), Action: &action})
	var apiErr *apperr.Error
	if !errors.As(err, &apiErr) || apiErr.Status != 409 {
		t.Fatalf("expected 409 Conflict, got %v", err)
	}
}

func TestDispatchDoesNotOverwriteFastACK(t *testing.T) {
	repo := newFakeCommandRepo()
	cmd, actuator, crop := testCommand()
	repo.commands[cmd.ID.Hex()] = cmd
	notifier := &fakeNotifier{}
	actuatorsSvc := &fakeActuators{actuator: actuator}
	svc := NewService(repo, fakeCrops{crop: crop}, actuatorsSvc, notifier, time.Now, time.Minute)
	publisher := &fakePublisher{enabled: true, ok: true}
	svc.SetPublisher(publisher)

	// El dispositivo confirma antes de que dispatch termine de publicar.
	svc.Acknowledge(context.Background(), crop.HexID(), AckMessage{ID: cmd.ID.Hex(), Status: "EXECUTED"})
	sent, err := svc.dispatch(context.Background(), cmd)
	if err != nil {
		t.Fatalf("dispatch: %v", err)
	}
	if sent == nil || sent.Status != StatusExecuted {
		t.Fatalf("dispatch overwrote the terminal state: %+v", sent)
	}
	if sent.SentAt != nil {
		t.Fatal("dispatch must not write sent_at over an already completed command")
	}
	if actuatorsSvc.updates != 1 {
		t.Fatalf("actuator state should be updated exactly once, got %d", actuatorsSvc.updates)
	}
}

func TestExpireStaleNeverOverwritesTerminalState(t *testing.T) {
	repo := newFakeCommandRepo()
	cmd, actuator, crop := testCommand()
	cmd.Status = StatusExecuted
	repo.commands[cmd.ID.Hex()] = cmd
	repo.stale = []Command{*cmd}
	notifier := &fakeNotifier{}
	svc := NewService(repo, fakeCrops{crop: crop}, &fakeActuators{actuator: actuator}, notifier,
		time.Now, time.Minute)

	svc.ExpireStale(context.Background())
	if got := repo.commands[cmd.ID.Hex()].Status; got != StatusExecuted {
		t.Fatalf("terminal state was overwritten: %s", got)
	}
	if notifier.calls != 0 {
		t.Fatalf("a command already closed must not notify, got %d", notifier.calls)
	}
}

func TestExpireStaleClosesPendingAndNotifies(t *testing.T) {
	repo := newFakeCommandRepo()
	cmd, actuator, crop := testCommand()
	cmd.Status = StatusPending
	repo.commands[cmd.ID.Hex()] = cmd
	repo.stale = []Command{*cmd}
	notifier := &fakeNotifier{}
	svc := NewService(repo, fakeCrops{crop: crop}, &fakeActuators{actuator: actuator}, notifier,
		time.Now, time.Minute)

	svc.ExpireStale(context.Background())
	got := repo.commands[cmd.ID.Hex()]
	if got.Status != StatusExpired || got.CompletedAt == nil {
		t.Fatalf("PENDING command should expire and free the actuator: %+v", got)
	}
	if notifier.calls != 1 {
		t.Fatalf("expiration should notify once, got %d", notifier.calls)
	}
}

func TestRequestBulkReportsInFlightAsSkipped(t *testing.T) {
	repo := newFakeCommandRepo()
	repo.insertErr = ErrInFlight
	_, actuator, crop := testCommand()
	action := Activate
	svc := NewService(repo, fakeCrops{crop: crop}, &fakeActuators{actuator: actuator}, &fakeNotifier{},
		time.Now, time.Minute)
	svc.SetPublisher(&fakePublisher{enabled: true, ok: true})

	res, err := svc.RequestBulk(context.Background(), crop.OwnerID.Hex(),
		BulkCommandRequest{CropIds: []string{crop.HexID()}, ActuatorType: &actuator.Type, Action: &action})
	if err != nil {
		t.Fatalf("bulk request: %v", err)
	}
	if res.Skipped != 1 || res.Results[0].Status != "SKIPPED" {
		t.Fatalf("in-flight actuator should be skipped, got %+v", res)
	}
}

func TestAcknowledgeIgnoresCommandFromAnotherCrop(t *testing.T) {
	repo := newFakeCommandRepo()
	cmd, actuator, crop := testCommand()
	repo.commands[cmd.ID.Hex()] = cmd
	actuatorsSvc := &fakeActuators{actuator: actuator}
	notifier := &fakeNotifier{}
	svc := NewService(repo, fakeCrops{crop: crop}, actuatorsSvc, notifier, time.Now, time.Minute)

	otherCrop := primitive.NewObjectID().Hex()
	svc.Acknowledge(context.Background(), otherCrop, AckMessage{ID: cmd.ID.Hex(), Status: "EXECUTED"})

	if got := repo.commands[cmd.ID.Hex()].Status; got != StatusPending {
		t.Fatalf("an ACK from another crop must not change the command: %s", got)
	}
	if actuatorsSvc.updates != 0 {
		t.Fatalf("an ACK from another crop must not change the actuator: %d updates", actuatorsSvc.updates)
	}
	if notifier.calls != 0 {
		t.Fatalf("an ACK from another crop must not notify: %d notices", notifier.calls)
	}
}
