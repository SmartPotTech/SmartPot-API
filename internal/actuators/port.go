package actuators

import (
	"context"
	"time"

	"go.mongodb.org/mongo-driver/bson/primitive"
)

// Repository es el puerto de almacenamiento de los actuadores: lo que el servicio necesita de la capa de datos. Lo
// implementa el adaptador de PostgreSQL (internal/storage/postgres).
type Repository interface {
	ListByCrop(ctx context.Context, cropID primitive.ObjectID) ([]Actuator, error)
	ListByCrops(ctx context.Context, cropIDs []primitive.ObjectID) (map[string][]Actuator, error)
	FindByIDAndCrop(ctx context.Context, id, cropID primitive.ObjectID) (*Actuator, error)
	FindByCropAndType(ctx context.Context, cropID primitive.ObjectID, t Type) (*Actuator, error)
	ExistsByCropAndType(ctx context.Context, cropID primitive.ObjectID, t Type) (bool, error)
	Insert(ctx context.Context, a *Actuator) error
	UpdateState(ctx context.Context, id primitive.ObjectID, active bool, runningUntil *time.Time, now time.Time) error
	Delete(ctx context.Context, id primitive.ObjectID) error
	DeleteByCrop(ctx context.Context, cropID primitive.ObjectID) error
}
