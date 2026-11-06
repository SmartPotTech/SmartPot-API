package readings

import (
	"context"
	"time"

	"go.mongodb.org/mongo-driver/bson/primitive"
)

// Repository es el puerto de almacenamiento de la telemetría: lo que el servicio necesita de la capa de datos. Lo
// implementa el adaptador de PostgreSQL + TimescaleDB (internal/storage/postgres), donde las lecturas viven en una
// hipertabla.
type Repository interface {
	Insert(ctx context.Context, reading *Reading) error
	InsertBatch(ctx context.Context, readings []Reading) error
	Latest(ctx context.Context, cropID primitive.ObjectID) (*Reading, error)
	// LatestByCropIDs devuelve la última lectura de cada cultivo en una sola consulta.
	LatestByCropIDs(ctx context.Context, cropIDs []primitive.ObjectID) (map[string]*Reading, error)
	Recent(ctx context.Context, cropID primitive.ObjectID, limit int) ([]Reading, error)
	Between(ctx context.Context, cropID primitive.ObjectID, from, to time.Time, limit int) ([]Reading, error)
	DeleteByCrop(ctx context.Context, cropID primitive.ObjectID) error
	Summary(ctx context.Context, cropID primitive.ObjectID, from, to time.Time) (int64, map[string]MetricSummary, error)
	SeriesAverages(ctx context.Context, cropIDs []primitive.ObjectID, metric string, from time.Time, bucketMinutes int) ([]Bucket, error)
}

// Bucket es el promedio de una variable de un cultivo en un intervalo de tiempo.
type Bucket struct {
	CropID string
	Time   time.Time
	Value  float64
}
