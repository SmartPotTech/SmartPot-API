package notifications

import (
	"context"
	"errors"
	"sync"
	"testing"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/cache"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/events"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

// fakeRepo guarda notificaciones en memoria y permite provocar fallos puntuales.
type fakeRepo struct {
	mu sync.Mutex

	items []Notification

	insertErr   error
	insertCalls int

	listUnreadOnly bool
	listLimit      int
	listCalls      int
	listErr        error

	countUnread    int64
	countUnreadErr error

	find      *Notification
	findErr   error
	findCalls int

	setReadErr   error
	setReadCalls []primitive.ObjectID

	markAllCalls int
	markAllErr   error

	deleteResult      int64
	deleteErr         error
	deleteCalls       int
	deleteByUserCalls int
	deleteByUserErr   error
	deleteByCropCalls int
	deleteByCropErr   error
}

func (r *fakeRepo) Insert(_ context.Context, n *Notification) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.insertCalls++
	if r.insertErr != nil {
		return r.insertErr
	}
	if n.ID.IsZero() {
		n.ID = primitive.NewObjectID()
	}
	r.items = append(r.items, *n)
	return nil
}

func (r *fakeRepo) List(_ context.Context, _ primitive.ObjectID, unreadOnly bool, limit int) ([]Notification, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.listCalls++
	r.listUnreadOnly = unreadOnly
	r.listLimit = limit
	if r.listErr != nil {
		return nil, r.listErr
	}
	out := make([]Notification, 0, len(r.items))
	for _, n := range r.items {
		if unreadOnly && n.Read {
			continue
		}
		out = append(out, n)
	}
	if len(out) > limit {
		out = out[:limit]
	}
	return out, nil
}

func (r *fakeRepo) CountUnread(context.Context, primitive.ObjectID) (int64, error) {
	return r.countUnread, r.countUnreadErr
}

func (r *fakeRepo) FindByIDAndUser(context.Context, primitive.ObjectID, primitive.ObjectID) (*Notification, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.findCalls++
	return r.find, r.findErr
}

func (r *fakeRepo) SetRead(_ context.Context, id primitive.ObjectID) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.setReadCalls = append(r.setReadCalls, id)
	return r.setReadErr
}

func (r *fakeRepo) MarkAllRead(context.Context, primitive.ObjectID) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.markAllCalls++
	return r.markAllErr
}

func (r *fakeRepo) DeleteByIDAndUser(context.Context, primitive.ObjectID, primitive.ObjectID) (int64, error) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.deleteCalls++
	return r.deleteResult, r.deleteErr
}

func (r *fakeRepo) DeleteByUser(context.Context, primitive.ObjectID) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.deleteByUserCalls++
	return r.deleteByUserErr
}

func (r *fakeRepo) DeleteByCrop(context.Context, primitive.ObjectID) error {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.deleteByCropCalls++
	return r.deleteByCropErr
}

func (r *fakeRepo) FindByCropCreatedAfter(context.Context, primitive.ObjectID, time.Time) ([]Notification, error) {
	return nil, nil
}

func (r *fakeRepo) CountByCropTypeCreatedAfter(context.Context, primitive.ObjectID, Type, time.Time) (int64, error) {
	return 0, nil
}

func (r *fakeRepo) DeleteOlderThan(context.Context, time.Time) (int64, error) { return 0, nil }

func newTestService(repo Repository, bus *events.Bus, clock func() time.Time) *Service {
	return NewService(repo, cache.NewStore(nil), bus, clock)
}

func fixedClock() time.Time {
	return time.Date(2026, 11, 6, 12, 0, 0, 0, time.UTC)
}

func TestNotifyCreatesNotificationAndPublishesEvent(t *testing.T) {
	repo := &fakeRepo{}
	bus := events.NewBus()
	published := make(chan CreatedEvent, 4)
	events.Subscribe(bus, func(e CreatedEvent) { published <- e })
	svc := newTestService(repo, bus, fixedClock)

	userID := primitive.NewObjectID().Hex()
	cropID := primitive.NewObjectID().Hex()
	created, err := svc.Notify(context.Background(), userID, cropID, TypeAlert, "Low humidity", "Humidity is below the range")
	if err != nil {
		t.Fatal(err)
	}
	if created.ID.IsZero() || len(repo.items) != 1 {
		t.Fatalf("notification was not stored: id=%v items=%d", created.ID, len(repo.items))
	}
	if created.CropID == nil || created.CropID.Hex() != cropID {
		t.Fatalf("optional crop id was not attached: %v", created.CropID)
	}
	if !created.CreatedAt.Equal(fixedClock()) {
		t.Fatalf("unexpected createdAt: %v", created.CreatedAt)
	}

	event := <-published
	if event.Notification.ID != created.ID || event.Notification.Type != TypeAlert {
		t.Fatalf("unexpected event: %+v", event)
	}

	// Sin cultivo la notificación sigue siendo válida y no lleva cropId.
	accountWide, err := svc.Notify(context.Background(), userID, "", TypeInfo, "Welcome", "Your account is ready")
	if err != nil {
		t.Fatal(err)
	}
	if accountWide.CropID != nil {
		t.Fatalf("account notification must not carry a crop id: %v", accountWide.CropID)
	}
	if got := <-published; got.Notification.ID != accountWide.ID {
		t.Fatalf("second event does not match: %+v", got)
	}
}

func TestNotifyRejectsInvalidUser(t *testing.T) {
	repo := &fakeRepo{}
	svc := newTestService(repo, events.NewBus(), fixedClock)

	if _, err := svc.Notify(context.Background(), "not-an-object-id", "", TypeInfo, "Title", "Message"); err == nil {
		t.Fatal("an invalid userId must be rejected")
	}
	if repo.insertCalls != 0 {
		t.Fatalf("invalid userId must not reach the repository: %d inserts", repo.insertCalls)
	}
}

func TestNotifyOnceRespectsCooldownAndReleasesKeyOnFailure(t *testing.T) {
	repo := &fakeRepo{}
	svc := newTestService(repo, events.NewBus(), fixedClock)
	userID := primitive.NewObjectID().Hex()

	first, err := svc.NotifyOnce(context.Background(), "device-offline:crop-1", time.Hour,
		userID, "", TypeDevice, "Device offline", "The device stopped reporting")
	if err != nil || !first {
		t.Fatalf("first call must create the notification: created=%v err=%v", first, err)
	}
	second, err := svc.NotifyOnce(context.Background(), "device-offline:crop-1", time.Hour,
		userID, "", TypeDevice, "Device offline", "The device stopped reporting")
	if err != nil || second {
		t.Fatalf("second call inside the cooldown must be skipped: created=%v err=%v", second, err)
	}
	if repo.insertCalls != 1 {
		t.Fatalf("cooldown must prevent a second INSERT, got %d", repo.insertCalls)
	}

	// Otra llave no comparte el enfriamiento.
	other, err := svc.NotifyOnce(context.Background(), "device-offline:crop-2", time.Hour,
		userID, "", TypeDevice, "Device offline", "The device stopped reporting")
	if err != nil || !other {
		t.Fatalf("a different key must not be blocked: created=%v err=%v", other, err)
	}
	if repo.insertCalls != 2 {
		t.Fatalf("expected two inserts, got %d", repo.insertCalls)
	}

	// Si el INSERT falla, la llave se libera para poder reintentar el aviso.
	repo.insertErr = errors.New("database is paused")
	failed, err := svc.NotifyOnce(context.Background(), "sensor-alert:crop-3", time.Hour,
		userID, "", TypeAlert, "Sensor alert", "Values are out of range")
	if err == nil || failed {
		t.Fatalf("a failed insert must surface the error: created=%v err=%v", failed, err)
	}
	repo.insertErr = nil
	retried, err := svc.NotifyOnce(context.Background(), "sensor-alert:crop-3", time.Hour,
		userID, "", TypeAlert, "Sensor alert", "Values are out of range")
	if err != nil || !retried {
		t.Fatalf("the cooldown key must be released after a failure: created=%v err=%v", retried, err)
	}
}

func TestListClampsLimitAndPassesUnreadOnly(t *testing.T) {
	cases := []struct {
		name       string
		unreadOnly bool
		limit      int
		wantLimit  int
	}{
		{"zero becomes one", false, 0, 1},
		{"negative becomes one", false, -5, 1},
		{"over one hundred becomes one hundred", true, 250, 100},
		{"requested limit is kept", true, 50, 50},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			repo := &fakeRepo{}
			svc := newTestService(repo, events.NewBus(), fixedClock)
			userID := primitive.NewObjectID().Hex()
			if _, err := svc.List(context.Background(), userID, tc.unreadOnly, tc.limit); err != nil {
				t.Fatal(err)
			}
			if repo.listLimit != tc.wantLimit {
				t.Fatalf("limit = %d, want %d", repo.listLimit, tc.wantLimit)
			}
			if repo.listUnreadOnly != tc.unreadOnly {
				t.Fatalf("unreadOnly = %v, want %v", repo.listUnreadOnly, tc.unreadOnly)
			}
		})
	}
}

func TestListWithInvalidUserReturnsEmptyList(t *testing.T) {
	repo := &fakeRepo{}
	svc := newTestService(repo, events.NewBus(), fixedClock)

	items, err := svc.List(context.Background(), "invalid", false, 10)
	if err != nil {
		t.Fatal(err)
	}
	if items == nil || len(items) != 0 {
		t.Fatalf("invalid user must receive an empty list, got %#v", items)
	}
	if repo.listCalls != 0 {
		t.Fatalf("invalid user must not query the repository: %d calls", repo.listCalls)
	}
}

func TestUnreadCount(t *testing.T) {
	repo := &fakeRepo{countUnread: 7}
	svc := newTestService(repo, events.NewBus(), fixedClock)

	count, err := svc.UnreadCount(context.Background(), primitive.NewObjectID().Hex())
	if err != nil || count != 7 {
		t.Fatalf("unexpected unread count: %d %v", count, err)
	}
	if count, err = svc.UnreadCount(context.Background(), "invalid"); err != nil || count != 0 {
		t.Fatalf("invalid user must count zero: %d %v", count, err)
	}
}

func TestMarkRead(t *testing.T) {
	userID := primitive.NewObjectID()
	notification := &Notification{ID: primitive.NewObjectID(), UserID: userID, Type: TypeAlert, Title: "Alert"}
	repo := &fakeRepo{find: notification}
	svc := newTestService(repo, events.NewBus(), fixedClock)

	read, err := svc.MarkRead(context.Background(), userID.Hex(), notification.ID.Hex())
	if err != nil {
		t.Fatal(err)
	}
	if !read.Read {
		t.Fatal("the returned notification must be marked as read")
	}
	if len(repo.setReadCalls) != 1 || repo.setReadCalls[0] != notification.ID {
		t.Fatalf("SetRead was not called with the notification id: %v", repo.setReadCalls)
	}
}

func TestMarkReadReturnsNotFound(t *testing.T) {
	repo := &fakeRepo{}
	svc := newTestService(repo, events.NewBus(), fixedClock)

	_, err := svc.MarkRead(context.Background(), primitive.NewObjectID().Hex(), primitive.NewObjectID().Hex())
	if !isStatus(err, 404) {
		t.Fatalf("a missing notification must be 404, got %v", err)
	}
	_, err = svc.MarkRead(context.Background(), primitive.NewObjectID().Hex(), "invalid")
	if !isStatus(err, 404) {
		t.Fatalf("an invalid id must be 404, got %v", err)
	}
}

func TestMarkAllRead(t *testing.T) {
	repo := &fakeRepo{}
	svc := newTestService(repo, events.NewBus(), fixedClock)

	if err := svc.MarkAllRead(context.Background(), primitive.NewObjectID().Hex()); err != nil {
		t.Fatal(err)
	}
	if repo.markAllCalls != 1 {
		t.Fatalf("MarkAllRead must reach the repository once, got %d", repo.markAllCalls)
	}
	if err := svc.MarkAllRead(context.Background(), "invalid"); err != nil {
		t.Fatal(err)
	}
	if repo.markAllCalls != 1 {
		t.Fatalf("invalid user must not reach the repository, got %d calls", repo.markAllCalls)
	}
}

func TestDeleteEnforcesOwnership(t *testing.T) {
	repo := &fakeRepo{deleteResult: 1}
	svc := newTestService(repo, events.NewBus(), fixedClock)

	if err := svc.Delete(context.Background(), primitive.NewObjectID().Hex(), primitive.NewObjectID().Hex()); err != nil {
		t.Fatal(err)
	}
	if repo.deleteCalls != 1 {
		t.Fatalf("expected one delete attempt, got %d", repo.deleteCalls)
	}

	// El repositorio devuelve 0 cuando la notificación es de otro usuario (BOLA).
	repo.deleteResult = 0
	err := svc.Delete(context.Background(), primitive.NewObjectID().Hex(), primitive.NewObjectID().Hex())
	if !isStatus(err, 404) {
		t.Fatalf("deleting another user's notification must be 404, got %v", err)
	}
}

func TestDeleteRejectsInvalidID(t *testing.T) {
	repo := &fakeRepo{deleteResult: 1}
	svc := newTestService(repo, events.NewBus(), fixedClock)

	if err := svc.Delete(context.Background(), primitive.NewObjectID().Hex(), "invalid"); !isStatus(err, 404) {
		t.Fatalf("an invalid id must be 404, got %v", err)
	}
	if repo.deleteCalls != 0 {
		t.Fatalf("invalid id must not reach the repository: %d calls", repo.deleteCalls)
	}
}

func TestDeleteAllForUserAndCrop(t *testing.T) {
	repo := &fakeRepo{}
	svc := newTestService(repo, events.NewBus(), fixedClock)

	if err := svc.DeleteAllForUser(context.Background(), primitive.NewObjectID().Hex()); err != nil {
		t.Fatal(err)
	}
	if repo.deleteByUserCalls != 1 {
		t.Fatalf("DeleteByUser calls = %d, want 1", repo.deleteByUserCalls)
	}
	if err := svc.DeleteAllForCrop(context.Background(), primitive.NewObjectID().Hex()); err != nil {
		t.Fatal(err)
	}
	if repo.deleteByCropCalls != 1 {
		t.Fatalf("DeleteByCrop calls = %d, want 1", repo.deleteByCropCalls)
	}

	// Ids inválidos no deben llegar a la base.
	if err := svc.DeleteAllForUser(context.Background(), "invalid"); err != nil {
		t.Fatal(err)
	}
	if err := svc.DeleteAllForCrop(context.Background(), "invalid"); err != nil {
		t.Fatal(err)
	}
	if repo.deleteByUserCalls != 1 || repo.deleteByCropCalls != 1 {
		t.Fatalf("invalid ids reached the repository: user=%d crop=%d", repo.deleteByUserCalls, repo.deleteByCropCalls)
	}
}

func isStatus(err error, status int) bool {
	var apiErr *apperr.Error
	return errors.As(err, &apiErr) && apiErr.Status == status
}
