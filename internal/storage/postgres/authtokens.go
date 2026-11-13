package postgres

import (
	"context"
	"errors"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/auth"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

// TokenRepository implementa auth.TokenRepository sobre PostgreSQL (tabla password_reset_tokens).
type TokenRepository struct {
	pool *pgxpool.Pool
}

// NewTokenRepository crea el adaptador de tokens de recuperación.
func NewTokenRepository(pool *pgxpool.Pool) *TokenRepository {
	return &TokenRepository{pool: pool}
}

// FindByTokenHash busca un token por su hash, o nil si no existe.
func (r *TokenRepository) FindByTokenHash(ctx context.Context, hash string) (*auth.PasswordResetToken, error) {
	return r.scanTokenRow(r.pool.QueryRow(ctx,
		"SELECT id, token_hash, user_id, expires_at, created_at FROM password_reset_tokens WHERE token_hash = $1", hash))
}

// ConsumeByTokenHash borra y devuelve un token vigente de forma atómica para evitar uso doble concurrente.
func (r *TokenRepository) ConsumeByTokenHash(ctx context.Context, hash string, now time.Time) (*auth.PasswordResetToken, error) {
	return r.scanTokenRow(r.pool.QueryRow(ctx,
		"DELETE FROM password_reset_tokens WHERE token_hash = $1 AND expires_at > $2 "+
			"RETURNING id, token_hash, user_id, expires_at, created_at", hash, now))
}

func (r *TokenRepository) scanTokenRow(row pgx.Row) (*auth.PasswordResetToken, error) {
	var (
		t         auth.PasswordResetToken
		id, uid   string
		createdAt *time.Time
	)
	err := row.Scan(&id, &t.TokenHash, &uid, &t.ExpiresAt, &createdAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	if t.ID, err = parseOID(id); err != nil {
		return nil, err
	}
	if t.UserID, err = parseOID(uid); err != nil {
		return nil, err
	}
	if createdAt != nil {
		t.CreatedAt = *createdAt
	}
	return &t, nil
}

// Insert guarda un token nuevo y fija su id generado.
func (r *TokenRepository) Insert(ctx context.Context, t *auth.PasswordResetToken) error {
	if t.ID.IsZero() {
		t.ID = primitive.NewObjectID()
	}
	_, err := r.pool.Exec(ctx,
		"INSERT INTO password_reset_tokens (id, token_hash, user_id, expires_at, created_at) VALUES ($1,$2,$3,$4,$5)",
		hexOf(t.ID), t.TokenHash, hexOf(t.UserID), t.ExpiresAt, nullTime(t.CreatedAt))
	return err
}

// DeleteByUserID borra los tokens de un usuario (también en el borrado en cascada de la cuenta).
func (r *TokenRepository) DeleteByUserID(ctx context.Context, userID string) error {
	oid, err := parseOID(userID)
	if err != nil {
		return nil
	}
	_, err = r.pool.Exec(ctx, "DELETE FROM password_reset_tokens WHERE user_id = $1", oid.Hex())
	return err
}

// DeleteExpired borra los tokens vencidos.
func (r *TokenRepository) DeleteExpired(ctx context.Context, now time.Time) (int64, error) {
	tag, err := r.pool.Exec(ctx, "DELETE FROM password_reset_tokens WHERE expires_at <= $1", now)
	return tag.RowsAffected(), err
}
