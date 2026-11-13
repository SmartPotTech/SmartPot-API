package crops

import (
	"context"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/geo"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

// Repository es el puerto de almacenamiento de los cultivos: lo que el servicio necesita de la capa de datos, sin
// atarse a un motor concreto. Lo implementa el adaptador de PostgreSQL (internal/storage/postgres).
type Repository interface {
	ListByOwner(ctx context.Context, ownerID primitive.ObjectID) ([]Crop, error)
	FindByIDAndOwner(ctx context.Context, id, ownerID primitive.ObjectID) (*Crop, error)
	FindByID(ctx context.Context, id primitive.ObjectID) (*Crop, error)
	FindWithDeviceKey(ctx context.Context) ([]Crop, error)
	CountByOwner(ctx context.Context, ownerID primitive.ObjectID) (int64, error)
	Insert(ctx context.Context, crop *Crop) error
	// InsertWithQuota crea el cultivo dentro de una transacción con bloqueo asesor
	// por dueño, de modo que dos peticiones concurrentes no superan los cupos.
	InsertWithQuota(ctx context.Context, crop *Crop, maxTotal, maxVirtual int) error
	Save(ctx context.Context, crop *Crop) error
	// UpdateAutomation actualiza el modo automático de varios cultivos del dueño en una sentencia atómica.
	UpdateAutomation(ctx context.Context, ownerID primitive.ObjectID, ids []primitive.ObjectID, enabled bool, now time.Time) error
	// UpdateDeviceKey guarda solo la clave cifrada del dispositivo y su rotación.
	UpdateDeviceKey(ctx context.Context, id primitive.ObjectID, ciphertext string, rotatedAt, now time.Time) error
	Delete(ctx context.Context, id primitive.ObjectID) error
	UpdatePlacement(ctx context.Context, id primitive.ObjectID, placement *geo.Placement, now time.Time) error
	MarkDeviceSeen(ctx context.Context, id primitive.ObjectID, online *bool, now time.Time) error
	UpdateHealth(ctx context.Context, id primitive.ObjectID, health *CropHealth) error
	BackfillKind(ctx context.Context, ids []primitive.ObjectID, kind CropKind) error
	BackfillKindAll(ctx context.Context, kind CropKind) error
	BackfillFormAll(ctx context.Context, form CropForm) error
	BackfillPlacement(ctx context.Context, id primitive.ObjectID, placement geo.Placement) error
}
