package connections

import (
	"context"
	"errors"
	"strings"
	"testing"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

type memRepo struct {
	items   map[primitive.ObjectID]AccessToken
	touches int
}

func newMemRepo() *memRepo { return &memRepo{items: map[primitive.ObjectID]AccessToken{}} }

func (m *memRepo) Insert(_ context.Context, t *AccessToken) error {
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
		t.Fatalf("token con formato inesperado: %q", created.Token)
	}
	if created.Connection.Name != "Asistente" || !strings.HasPrefix(created.Token, created.Connection.Prefix) {
		t.Fatalf("conexión mal armada: %+v", created.Connection)
	}
	for _, stored := range repo.items {
		if strings.Contains(stored.TokenHash, created.Token) {
			t.Fatal("el token no debe guardarse en claro")
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
		t.Fatalf("token desconocido debe dar 401, dio %v", err)
	}

	if err := svc.Revoke(ctx, primitive.NewObjectID().Hex(), created.Connection.ID); status(err) != 404 {
		t.Fatalf("otra cuenta no puede revocarla: %v", err)
	}
	if err := svc.Revoke(ctx, user, created.Connection.ID); err != nil {
		t.Fatal(err)
	}
	if _, err := svc.AuthenticateConnection(ctx, created.Token); status(err) != 401 {
		t.Fatalf("una conexión revocada debe dar 401, dio %v", err)
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
		t.Fatalf("una conexión vencida debe dar 401, dio %v", err)
	}
	for i := 1; i < MaxPerUser; i++ {
		if _, err := svc.Create(ctx, user, CreateRequest{Name: "Otra", Scope: ScopeRead}); err != nil {
			t.Fatal(err)
		}
	}
	if _, err := svc.Create(ctx, user, CreateRequest{Name: "Una más", Scope: ScopeRead}); status(err) != 409 {
		t.Fatalf("superar el máximo debe dar 409, dio %v", err)
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
