package connections

import (
	"context"
	"errors"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

type memRepo struct {
	mu      sync.Mutex
	items   map[primitive.ObjectID]AccessToken
	touches int
}

func newMemRepo() *memRepo { return &memRepo{items: map[primitive.ObjectID]AccessToken{}} }

func (m *memRepo) Insert(_ context.Context, t *AccessToken) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.items[t.ID] = *t
	return nil
}

func (m *memRepo) InsertWithQuota(_ context.Context, t *AccessToken, max int) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	var count int
	for _, item := range m.items {
		if item.UserID == t.UserID {
			count++
		}
	}
	if count >= max {
		return ErrConnectionLimit
	}
	m.items[t.ID] = *t
	return nil
}

func (m *memRepo) ListByUser(_ context.Context, uid primitive.ObjectID) ([]AccessToken, error) {
	var out []AccessToken
	for _, t := range m.items {
		if t.UserID == uid {
			out = append(out, t)
		}
	}
	return out, nil
}

func (m *memRepo) CountByUser(ctx context.Context, uid primitive.ObjectID) (int64, error) {
	l, _ := m.ListByUser(ctx, uid)
	return int64(len(l)), nil
}

func (m *memRepo) FindByHash(_ context.Context, hash string) (*AccessToken, error) {
	for _, t := range m.items {
		if t.TokenHash == hash {
			return &t, nil
		}
	}
	return nil, nil
}

func (m *memRepo) DeleteByIDAndUser(_ context.Context, id, uid primitive.ObjectID) (int64, error) {
	if t, ok := m.items[id]; ok && t.UserID == uid {
		delete(m.items, id)
		return 1, nil
	}
	return 0, nil
}

func (m *memRepo) DeleteByUser(_ context.Context, uid primitive.ObjectID) error {
	for id, t := range m.items {
		if t.UserID == uid {
			delete(m.items, id)
		}
	}
	return nil
}

func (m *memRepo) TouchLastUsed(_ context.Context, id primitive.ObjectID, at time.Time) error {
	t := m.items[id]
	t.LastUsedAt = &at
	m.items[id] = t
	m.touches++
	return nil
}

func status(err error) int {
	var ae *apperr.Error
	if errors.As(err, &ae) {
		return ae.Status
	}
	return 0
}

func TestCreateAuthenticateAndRevoke(t *testing.T) {
	now := time.Date(2026, 11, 1, 12, 0, 0, 0, time.UTC)
	repo := newMemRepo()
	svc := NewService(repo, func() time.Time { return now })
	user := primitive.NewObjectID().Hex()
	ctx := context.Background()

	created, err := svc.Create(ctx, user, CreateRequest{Name: " Asistente ", Scope: ScopeRead})
	if err != nil {
		t.Fatal(err)
	}
	if !strings.HasPrefix(created.Token, TokenPrefix) || len(created.Token) < 40 {
		t.Fatalf("unexpected token format: %q", created.Token)
	}
	if created.Connection.Name != "Asistente" || !strings.HasPrefix(created.Token, created.Connection.Prefix) {
		t.Fatalf("conexión mal armada: %+v", created.Connection)
	}
	for _, stored := range repo.items {
		if strings.Contains(stored.TokenHash, created.Token) {
			t.Fatal("token must not be stored in plaintext")
		}
	}

	p, err := svc.AuthenticateConnection(ctx, created.Token)
	if err != nil || p.UserID != user || p.Scope != "READ" {
		t.Fatalf("autenticación: %+v, %v", p, err)
	}
	if _, err := svc.AuthenticateConnection(ctx, created.Token); err != nil || repo.touches != 1 {
		t.Fatalf("el último uso se escribe a lo sumo una vez por minuto (escrituras: %d)", repo.touches)
	}
	if _, err := svc.AuthenticateConnection(ctx, "spt_inventado-pero-largo-de-sobra"); status(err) != 401 {
		t.Fatalf("unknown token should return 401, got %v", err)
	}

	if err := svc.Revoke(ctx, primitive.NewObjectID().Hex(), created.Connection.ID); status(err) != 404 {
		t.Fatalf("another account cannot revoke it: %v", err)
	}
	if err := svc.Revoke(ctx, user, created.Connection.ID); err != nil {
		t.Fatal(err)
	}
	if _, err := svc.AuthenticateConnection(ctx, created.Token); status(err) != 401 {
		t.Fatalf("a revoked connection should return 401, got %v", err)
	}
}

func TestExpiredConnectionAndLimit(t *testing.T) {
	now := time.Date(2026, 11, 1, 12, 0, 0, 0, time.UTC)
	clock := now
	repo := newMemRepo()
	svc := NewService(repo, func() time.Time { return clock })
	user := primitive.NewObjectID().Hex()
	ctx := context.Background()
	days := 1
	created, err := svc.Create(ctx, user, CreateRequest{Name: "Temporal", Scope: ScopeControl, ExpiresInDays: &days})
	if err != nil {
		t.Fatal(err)
	}
	clock = now.Add(25 * time.Hour)
	if _, err := svc.AuthenticateConnection(ctx, created.Token); status(err) != 401 {
		t.Fatalf("an expired connection should return 401, got %v", err)
	}
	for i := 1; i < MaxPerUser; i++ {
		if _, err := svc.Create(ctx, user, CreateRequest{Name: "Otra", Scope: ScopeRead}); err != nil {
			t.Fatal(err)
		}
	}
	if _, err := svc.Create(ctx, user, CreateRequest{Name: "Una más", Scope: ScopeRead}); status(err) != 409 {
		t.Fatalf("exceeding maximum should return 409, got %v", err)
	}
}

func TestConfigurableLimit(t *testing.T) {
	repo := newMemRepo()
	svc := NewService(repo, time.Now)
	svc.SetMaxPerUser(2)
	user := primitive.NewObjectID().Hex()
	ctx := context.Background()

	if _, err := svc.Create(ctx, user, CreateRequest{Name: "C1", Scope: ScopeRead}); err != nil {
		t.Fatal(err)
	}
	if _, err := svc.Create(ctx, user, CreateRequest{Name: "C2", Scope: ScopeRead}); err != nil {
		t.Fatal(err)
	}
	if _, err := svc.Create(ctx, user, CreateRequest{Name: "C3", Scope: ScopeRead}); status(err) != 409 {
		t.Fatalf("exceeding configured limit of 2 should return 409, got %v", err)
	}
}

func TestCreateRequestValidation(t *testing.T) {
	bad := 0
	cases := []CreateRequest{
		{Name: "", Scope: ScopeRead},
		{Name: strings.Repeat("a", 61), Scope: ScopeRead},
		{Name: "Ok", Scope: "ADMIN"},
		{Name: "Ok", Scope: ScopeRead, ExpiresInDays: &bad},
	}
	for _, c := range cases {
		if len(c.Validate()) == 0 {
			t.Errorf("se esperaba error de validación para %+v", c)
		}
	}
	if f := (CreateRequest{Name: "Asistente", Scope: ScopeControl}).Validate(); len(f) != 0 {
		t.Errorf("petición válida rechazada: %v", f)
	}
}

func TestCreateEnforcesQuotaAtomicallyUnderConcurrency(t *testing.T) {
	repo := newMemRepo()
	svc := NewService(repo, time.Now)
	svc.SetMaxPerUser(3)
	user := primitive.NewObjectID().Hex()

	const workers = 8
	errs := make(chan error, workers)
	var wg sync.WaitGroup
	for i := 0; i < workers; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			_, err := svc.Create(context.Background(), user, CreateRequest{Name: "Concurrent", Scope: ScopeRead})
			errs <- err
		}()
	}
	wg.Wait()
	close(errs)

	var okCount, conflictCount int
	for err := range errs {
		if err == nil {
			okCount++
		} else if status(err) == 409 {
			conflictCount++
		} else {
			t.Fatalf("unexpected error: %v", err)
		}
	}
	if okCount != 3 || conflictCount != workers-3 {
		t.Fatalf("expected 3 created and %d conflicts, got ok=%d conflict=%d", workers-3, okCount, conflictCount)
	}
}
