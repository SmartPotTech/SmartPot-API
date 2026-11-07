//go:build integration

package postgres

import (
	"context"
	"testing"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/auth"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

func TestIntegrationPasswordResetTokens(t *testing.T) {
	pool := integrationPool(t)
	ctx := context.Background()
	repo := NewTokenRepository(pool)
	userID := seedUser(t, pool)
	now := time.Now().UTC().Truncate(time.Microsecond)

	active := &auth.PasswordResetToken{
		TokenHash: "active-" + primitive.NewObjectID().Hex(),
		UserID:    userID,
		ExpiresAt: now.Add(time.Hour),
		CreatedAt: now,
	}
	if err := repo.Insert(ctx, active); err != nil {
		t.Fatalf("insert active token: %v", err)
	}
	if active.ID.IsZero() {
		t.Fatal("Insert must assign the generated id")
	}
	t.Cleanup(func() {
		_, _ = pool.Exec(context.Background(), "DELETE FROM password_reset_tokens WHERE id = $1", active.ID.Hex())
	})

	found, err := repo.FindByTokenHash(ctx, active.TokenHash)
	if err != nil {
		t.Fatalf("find token: %v", err)
	}
	if found == nil || found.ID != active.ID || found.UserID != userID {
		t.Fatalf("unexpected token: %+v", found)
	}
	if !found.ExpiresAt.Equal(active.ExpiresAt) || !found.CreatedAt.Equal(now) {
		t.Fatalf("timestamps were not preserved: %+v", found)
	}

	if missing, err := repo.FindByTokenHash(ctx, "missing-"+primitive.NewObjectID().Hex()); err != nil || missing != nil {
		t.Fatalf("missing hash must return nil: %+v %v", missing, err)
	}

	// Un token sin created_at se guarda como NULL y se lee con la fecha cero.
	withoutCreatedAt := &auth.PasswordResetToken{
		TokenHash: "no-created-" + primitive.NewObjectID().Hex(),
		UserID:    userID,
		ExpiresAt: now.Add(time.Hour),
	}
	if err := repo.Insert(ctx, withoutCreatedAt); err != nil {
		t.Fatalf("insert token without created_at: %v", err)
	}
	t.Cleanup(func() {
		_, _ = pool.Exec(context.Background(), "DELETE FROM password_reset_tokens WHERE id = $1", withoutCreatedAt.ID.Hex())
	})
	found, err = repo.FindByTokenHash(ctx, withoutCreatedAt.TokenHash)
	if err != nil || found == nil {
		t.Fatalf("find token without created_at: %+v %v", found, err)
	}
	if !found.CreatedAt.IsZero() {
		t.Fatalf("NULL created_at must be read as the zero time: %v", found.CreatedAt)
	}

	expired := &auth.PasswordResetToken{
		TokenHash: "expired-" + primitive.NewObjectID().Hex(),
		UserID:    userID,
		ExpiresAt: now.Add(-time.Hour),
		CreatedAt: now,
	}
	if err := repo.Insert(ctx, expired); err != nil {
		t.Fatalf("insert expired token: %v", err)
	}
	deleted, err := repo.DeleteExpired(ctx, now)
	if err != nil || deleted < 1 {
		t.Fatalf("DeleteExpired: deleted=%d err=%v", deleted, err)
	}
	if found, _ := repo.FindByTokenHash(ctx, expired.TokenHash); found != nil {
		t.Fatalf("expired token was not deleted: %+v", found)
	}
	if found, _ := repo.FindByTokenHash(ctx, active.TokenHash); found == nil {
		t.Fatal("DeleteExpired must not delete valid tokens")
	}

	if err := repo.DeleteByUserID(ctx, userID.Hex()); err != nil {
		t.Fatalf("DeleteByUserID: %v", err)
	}
	if found, _ := repo.FindByTokenHash(ctx, active.TokenHash); found != nil {
		t.Fatalf("tokens were not deleted for the user: %+v", found)
	}
	if err := repo.DeleteByUserID(ctx, "invalid-id"); err != nil {
		t.Fatalf("an invalid user id must be ignored: %v", err)
	}
}
