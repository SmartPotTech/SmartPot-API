package commands

import (
	"context"
	"time"

	"go.mongodb.org/mongo-driver/bson/primitive"
)

// Repository es el puerto de almacenamiento de las órdenes: lo que el servicio necesita de la capa de datos. Lo
// implementa el adaptador de PostgreSQL (internal/storage/postgres).
type Repository interface {
	Insert(ctx context.Context, c *Command) error
	// MarkSent hace la transición PENDING -> SENT solo si la orden sigue pendiente; el segundo valor indica si ganó.
	MarkSent(ctx context.Context, id primitive.ObjectID, at time.Time) (bool, error)
	// Complete cierra la orden de forma condicional (solo si sigue PENDING o SENT) y devuelve la fila resultante.
	Complete(ctx context.Context, id primitive.ObjectID, status Status, message string, at time.Time) (*Command, bool, error)
	// Expire cierra una orden sin confirmar de forma condicional y devuelve la fila resultante.
	Expire(ctx context.Context, id primitive.ObjectID, message string, at time.Time) (*Command, bool, error)
	Save(ctx context.Context, c *Command) error
	ListByCrop(ctx context.Context, cropID primitive.ObjectID, limit int) ([]Command, error)
	ListByCrops(ctx context.Context, cropIDs []primitive.ObjectID, limit int) ([]Command, error)
	CountByCropsSince(ctx context.Context, cropIDs []primitive.ObjectID, since time.Time) (int64, error)
	FindByIDAndCrop(ctx context.Context, id, cropID primitive.ObjectID) (*Command, error)
	FindStaleSent(ctx context.Context, before time.Time) ([]Command, error)
	ExistsInFlight(ctx context.Context, actuatorID primitive.ObjectID) (bool, error)
	DeleteByCrop(ctx context.Context, cropID primitive.ObjectID) error
	// DeleteOlderThan borra las órdenes anteriores al corte (reemplaza al índice TTL anterior).
	DeleteOlderThan(ctx context.Context, cutoff time.Time) (int64, error)
}
