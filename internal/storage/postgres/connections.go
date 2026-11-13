package postgres

import (
	"context"
	"errors"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/connections"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

// AccessTokenRepository implementa connections.Repository sobre PostgreSQL (tabla access_tokens).
type AccessTokenRepository struct {
	pool *pgxpool.Pool
}

// NewAccessTokenRepository crea el adaptador de conexiones.
func NewAccessTokenRepository(pool *pgxpool.Pool) *AccessTokenRepository {
	return &AccessTokenRepository{pool: pool}
}

const accessTokenColumns = "id, user_id, name, token_hash, prefix, scope, created_at, last_used_at, expires_at"

func scanAccessToken(row pgx.Row) (*connections.AccessToken, error) {
	var (
		t          connections.AccessToken
		id, userID string
		scope      string
	)
	if err := row.Scan(&id, &userID, &t.Name, &t.TokenHash, &t.Prefix, &scope, &t.CreatedAt, &t.LastUsedAt,
		&t.ExpiresAt); err != nil {
		return nil, err
	}
	var err error
	if t.ID, err = parseOID(id); err != nil {
		return nil, err
	}
	if t.UserID, err = parseOID(userID); err != nil {
		return nil, err
	}
	t.Scope = connections.Scope(scope)
	return &t, nil
}

// Insert guarda una conexión nueva.
func (r *AccessTokenRepository) Insert(ctx context.Context, t *connections.AccessToken) error {
	_, err := r.pool.Exec(ctx,
		"INSERT INTO access_tokens ("+accessTokenColumns+") VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)",
		hexOf(t.ID), hexOf(t.UserID), t.Name, t.TokenHash, t.Prefix, string(t.Scope), t.CreatedAt, t.LastUsedAt,
		t.ExpiresAt)
	return err
}

// InsertWithQuota crea la conexión dentro de una transacción con bloqueo asesor
// por cuenta, evitando carreras al superar el cupo en peticiones concurrentes.
func (r *AccessTokenRepository) InsertWithQuota(ctx context.Context, t *connections.AccessToken, max int) error {
	tx, err := r.pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer func() { _ = tx.Rollback(ctx) }()

	if _, err := tx.Exec(ctx,
		"SELECT pg_advisory_xact_lock(hashtextextended($1, 0))", "connections:"+hexOf(t.UserID)); err != nil {
		return err
	}
	var count int64
	if err := tx.QueryRow(ctx, "SELECT count(*) FROM access_tokens WHERE user_id = $1", hexOf(t.UserID)).Scan(&count); err != nil {
		return err
	}
	if count >= int64(max) {
		return connections.ErrConnectionLimit
	}
	if _, err := tx.Exec(ctx,
		"INSERT INTO access_tokens ("+accessTokenColumns+") VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)",
		hexOf(t.ID), hexOf(t.UserID), t.Name, t.TokenHash, t.Prefix, string(t.Scope), t.CreatedAt, t.LastUsedAt,
		t.ExpiresAt); err != nil {
		return err
	}
	return tx.Commit(ctx)
}

// ListByUser devuelve las conexiones de una cuenta, de la más nueva a la más antigua.
func (r *AccessTokenRepository) ListByUser(ctx context.Context, userID primitive.ObjectID) ([]connections.AccessToken, error) {
	rows, err := r.pool.Query(ctx,
		"SELECT "+accessTokenColumns+" FROM access_tokens WHERE user_id = $1 ORDER BY created_at DESC", hexOf(userID))
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := make([]connections.AccessToken, 0)
	for rows.Next() {
		t, err := scanAccessToken(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, *t)
	}
	return out, rows.Err()
}

// CountByUser cuenta las conexiones de una cuenta.
func (r *AccessTokenRepository) CountByUser(ctx context.Context, userID primitive.ObjectID) (int64, error) {
	var n int64
	err := r.pool.QueryRow(ctx, "SELECT count(*) FROM access_tokens WHERE user_id = $1", hexOf(userID)).Scan(&n)
	return n, err
}

// FindByHash busca una conexión por el hash de su token, o nil si no existe.
func (r *AccessTokenRepository) FindByHash(ctx context.Context, hash string) (*connections.AccessToken, error) {
	t, err := scanAccessToken(r.pool.QueryRow(ctx,
		"SELECT "+accessTokenColumns+" FROM access_tokens WHERE token_hash = $1", hash))
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, nil
	}
	return t, err
}

// DeleteByIDAndUser revoca una conexión de la cuenta; devuelve cuántas filas borró.
func (r *AccessTokenRepository) DeleteByIDAndUser(ctx context.Context, id, userID primitive.ObjectID) (int64, error) {
	tag, err := r.pool.Exec(ctx, "DELETE FROM access_tokens WHERE id = $1 AND user_id = $2", hexOf(id), hexOf(userID))
	if err != nil {
		return 0, err
	}
	return tag.RowsAffected(), nil
}

// DeleteByUser borra todas las conexiones de una cuenta.
func (r *AccessTokenRepository) DeleteByUser(ctx context.Context, userID primitive.ObjectID) error {
	_, err := r.pool.Exec(ctx, "DELETE FROM access_tokens WHERE user_id = $1", hexOf(userID))
	return err
}

// TouchLastUsed registra el último uso de una conexión.
func (r *AccessTokenRepository) TouchLastUsed(ctx context.Context, id primitive.ObjectID, at time.Time) error {
	_, err := r.pool.Exec(ctx, "UPDATE access_tokens SET last_used_at = $2 WHERE id = $1", hexOf(id), at)
	return err
}
