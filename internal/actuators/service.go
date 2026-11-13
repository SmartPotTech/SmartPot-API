package actuators

import (
	"context"
	"errors"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/objectid"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

const notFound = "Actuator does not exist in this crop"

// Crops es lo que el servicio de actuadores necesita de los cultivos.
type Crops interface {
	RequireOwned(ctx context.Context, ownerID, cropID string) error
}

// Service implementa la lógica de actuadores.
type Service struct {
	repo  Repository
	crops Crops
	clock func() time.Time
}

// NewService crea el servicio de actuadores.
func NewService(repo Repository, crops Crops, clock func() time.Time) *Service {
	return &Service{repo: repo, crops: crops, clock: clock}
}

// List devuelve los actuadores del cultivo (comprueba el dueño).
func (s *Service) List(ctx context.Context, ownerID, cropID string) ([]Actuator, error) {
	if err := s.crops.RequireOwned(ctx, ownerID, cropID); err != nil {
		return nil, err
	}
	return s.ListForCrop(ctx, cropID)
}

// ListForCrop devuelve los actuadores del cultivo (sin comprobar dueño).
func (s *Service) ListForCrop(ctx context.Context, cropID string) ([]Actuator, error) {
	oid, ok := objectid.Parse(cropID)
	if !ok {
		return nil, nil
	}
	return s.repo.ListByCrop(ctx, oid)
}

// ListForCrops devuelve los actuadores de varios cultivos en una sola consulta.
func (s *Service) ListForCrops(ctx context.Context, cropIDs []primitive.ObjectID) (map[string][]Actuator, error) {
	return s.repo.ListByCrops(ctx, cropIDs)
}

// Add agrega un actuador al cultivo (uno por tipo).
func (s *Service) Add(ctx context.Context, ownerID, cropID string, t Type) (*Actuator, error) {
	if err := s.crops.RequireOwned(ctx, ownerID, cropID); err != nil {
		return nil, err
	}
	oid, _ := objectid.Parse(cropID)
	exists, err := s.repo.ExistsByCropAndType(ctx, oid, t)
	if err != nil {
		return nil, err
	}
	if exists {
		return nil, apperr.Conflict("The crop already has an actuator of that type")
	}
	a := &Actuator{CropID: oid, Type: t, Active: false, CreatedAt: s.clock()}
	if err := s.repo.Insert(ctx, a); err != nil {
		if errors.Is(err, ErrDuplicate) {
			return nil, apperr.Conflict("The crop already has an actuator of that type")
		}
		return nil, err
	}
	return a, nil
}

// Remove quita un actuador del cultivo.
func (s *Service) Remove(ctx context.Context, ownerID, cropID, actuatorID string) error {
	if err := s.crops.RequireOwned(ctx, ownerID, cropID); err != nil {
		return err
	}
	a, err := s.GetForCrop(ctx, cropID, actuatorID)
	if err != nil {
		return err
	}
	return s.repo.Delete(ctx, a.ID)
}

// GetForCrop busca un actuador del cultivo.
func (s *Service) GetForCrop(ctx context.Context, cropID, actuatorID string) (*Actuator, error) {
	if err := objectid.Require(actuatorID, notFound); err != nil {
		return nil, err
	}
	cropOID, _ := objectid.Parse(cropID)
	idOID, _ := objectid.Parse(actuatorID)
	a, err := s.repo.FindByIDAndCrop(ctx, idOID, cropOID)
	if err != nil {
		return nil, err
	}
	if a == nil {
		return nil, apperr.NotFound(notFound)
	}
	return a, nil
}

// FindByType busca un actuador del cultivo por tipo (nil si no existe).
func (s *Service) FindByType(ctx context.Context, cropID string, t Type) (*Actuator, error) {
	oid, ok := objectid.Parse(cropID)
	if !ok {
		return nil, nil
	}
	return s.repo.FindByCropAndType(ctx, oid, t)
}

// UpdateState fija el estado confirmado del actuador (lo usa el ACK del comando).
func (s *Service) UpdateState(ctx context.Context, actuatorID string, active bool, runningUntil *time.Time) {
	oid, ok := objectid.Parse(actuatorID)
	if !ok {
		return
	}
	_ = s.repo.UpdateState(ctx, oid, active, runningUntil, s.clock())
}

// SeedDefaults crea los actuadores por defecto del cultivo (implementa
// crops.ActuatorSeeder): seis en un virtual, tres en un real.
func (s *Service) SeedDefaults(ctx context.Context, cropID string, virtual bool) error {
	oid, ok := objectid.Parse(cropID)
	if !ok {
		return nil
	}
	types := DefaultRealTypes
	if virtual {
		types = AllTypes
	}
	now := s.clock()
	for _, t := range types {
		a := &Actuator{CropID: oid, Type: t, Active: false, CreatedAt: now}
		if err := s.repo.Insert(ctx, a); err != nil {
			return err
		}
	}
	return nil
}

// DeleteByCrop borra los actuadores de un cultivo (borrado en cascada).
func (s *Service) DeleteByCrop(ctx context.Context, cropID string) error {
	oid, ok := objectid.Parse(cropID)
	if !ok {
		return nil
	}
	return s.repo.DeleteByCrop(ctx, oid)
}
