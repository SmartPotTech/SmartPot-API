package connections

import (
	"context"
	"time"

	"go.mongodb.org/mongo-driver/bson/primitive"
)

// Repository es el puerto de almacenamiento de las conexiones; lo implementa el adaptador de PostgreSQL.
type Repository interface {
	Insert(ctx context.Context, t *AccessToken) error
	ListByUser(ctx context.Context, userID primitive.ObjectID) ([]AccessToken, error)
	CountByUser(ctx context.Context, userID primitive.ObjectID) (int64, error)
	FindByHash(ctx context.Context, hash string) (*AccessToken, error)
	DeleteByIDAndUser(ctx context.Context, id, userID primitive.ObjectID) (int64, error)
	DeleteByUser(ctx context.Context, userID primitive.ObjectID) error
	TouchLastUsed(ctx context.Context, id primitive.ObjectID, at time.Time) error
}
