package postgres

import (
	"context"
	"errors"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/actuators"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

// ActuatorRepository implementa actuators.Repository sobre PostgreSQL (tabla actuators).
type ActuatorRepository struct {
	pool *pgxpool.Pool
}

// NewActuatorRepository crea el adaptador de actuadores.
func NewActuatorRepository(pool *pgxpool.Pool) *ActuatorRepository {
	return &ActuatorRepository{pool: pool}
}

const actuatorColumns = "id, crop_id, type, active, running_until, last_changed_at, created_at"

func scanActuator(row pgx.Row) (*actuators.Actuator, error) {
	var (
		a               actuators.Actuator
		id, cropID, typ string
	)
	if err := row.Scan(&id, &cropID, &typ, &a.Active, &a.RunningUntil, &a.LastChangedAt, &a.CreatedAt); err != nil {
		return nil, err
	}
	var err error
	if a.ID, err = parseOID(id); err != nil {
		return nil, err
	}
	if a.CropID, err = parseOID(cropID); err != nil {
		return nil, err
	}
	a.Type = actuators.Type(typ)
	return &a, nil
}

func (r *ActuatorRepository) findOne(ctx context.Context, where string, args ...any) (*actuators.Actuator, error) {
	a, err := scanActuator(r.pool.QueryRow(ctx, "SELECT "+actuatorColumns+" FROM actuators WHERE "+where, args...))
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, nil
	}
	return a, err
}

// ListByCrop devuelve los actuadores del cultivo, más antiguos primero.
func (r *ActuatorRepository) ListByCrop(ctx context.Context, cropID primitive.ObjectID) ([]actuators.Actuator, error) {
	rows, err := r.pool.Query(ctx,
		"SELECT "+actuatorColumns+" FROM actuators WHERE crop_id = $1 ORDER BY created_at ASC", hexOf(cropID))
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := make([]actuators.Actuator, 0)
	for rows.Next() {
		a, err := scanActuator(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, *a)
	}
	return out, rows.Err()
}

// FindByIDAndCrop busca un actuador del cultivo.
func (r *ActuatorRepository) FindByIDAndCrop(ctx context.Context, id, cropID primitive.ObjectID) (*actuators.Actuator, error) {
	return r.findOne(ctx, "id = $1 AND crop_id = $2", hexOf(id), hexOf(cropID))
}

// FindByCropAndType busca un actuador del cultivo por tipo.
func (r *ActuatorRepository) FindByCropAndType(ctx context.Context, cropID primitive.ObjectID, t actuators.Type) (*actuators.Actuator, error) {
	return r.findOne(ctx, "crop_id = $1 AND type = $2", hexOf(cropID), string(t))
}

// ExistsByCropAndType indica si el cultivo ya tiene un actuador de ese tipo.
func (r *ActuatorRepository) ExistsByCropAndType(ctx context.Context, cropID primitive.ObjectID, t actuators.Type) (bool, error) {
	var exists bool
	err := r.pool.QueryRow(ctx,
		"SELECT EXISTS(SELECT 1 FROM actuators WHERE crop_id = $1 AND type = $2)", hexOf(cropID), string(t)).Scan(&exists)
	return exists, err
}

// Insert guarda un actuador nuevo y fija su id generado. La violación del índice
// actuators_crop_type se traduce a actuators.ErrDuplicate (409 en el servicio).
func (r *ActuatorRepository) Insert(ctx context.Context, a *actuators.Actuator) error {
	if a.ID.IsZero() {
		a.ID = primitive.NewObjectID()
	}
	_, err := r.pool.Exec(ctx,
		"INSERT INTO actuators ("+actuatorColumns+") VALUES ($1,$2,$3,$4,$5,$6,$7)",
		hexOf(a.ID), hexOf(a.CropID), string(a.Type), a.Active, a.RunningUntil, a.LastChangedAt, a.CreatedAt)
	var pgErr *pgconn.PgError
	if errors.As(err, &pgErr) && pgErr.Code == "23505" &&
		pgErr.ConstraintName == "actuators_crop_type" {
		return actuators.ErrDuplicate
	}
	return err
}

// UpdateState fija el estado confirmado del actuador (running_until nulo lo apaga del todo).
func (r *ActuatorRepository) UpdateState(ctx context.Context, id primitive.ObjectID, active bool, runningUntil *time.Time, now time.Time) error {
	_, err := r.pool.Exec(ctx,
		"UPDATE actuators SET active = $2, running_until = $3, last_changed_at = $4 WHERE id = $1",
		hexOf(id), active, runningUntil, now)
	return err
}

// Delete borra un actuador por id.
func (r *ActuatorRepository) Delete(ctx context.Context, id primitive.ObjectID) error {
	_, err := r.pool.Exec(ctx, "DELETE FROM actuators WHERE id = $1", hexOf(id))
	return err
}

// DeleteByCrop borra todos los actuadores de un cultivo.
func (r *ActuatorRepository) DeleteByCrop(ctx context.Context, cropID primitive.ObjectID) error {
	_, err := r.pool.Exec(ctx, "DELETE FROM actuators WHERE crop_id = $1", hexOf(cropID))
	return err
}
