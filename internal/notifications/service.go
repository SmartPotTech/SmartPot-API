package notifications

import (
	"context"
	"errors"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/cache"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/events"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/objectid"
)

const notFound = "Notification does not exist"

// Service implementa la lógica de notificaciones.
type Service struct {
	repo  Repository
	cache cache.Cache
	bus   *events.Bus
	clock func() time.Time
}

// NewService crea el servicio de notificaciones.
func NewService(repo Repository, store cache.Cache, bus *events.Bus, clock func() time.Time) *Service {
	return &Service{repo: repo, cache: store, bus: bus, clock: clock}
}

// Notify crea una notificación y publica el evento para los canales externos.
func (s *Service) Notify(ctx context.Context, userID, cropID string, t Type, title, message string) (*Notification, error) {
	userOID, ok := objectid.Parse(userID)
	if !ok {
		return nil, errors.New("invalid userId")
	}
	n := Notification{
		UserID:    userOID,
		Type:      t,
		Title:     title,
		Message:   message,
		Read:      false,
		CreatedAt: s.clock(),
	}
	if cid, ok := objectid.Parse(cropID); ok {
		n.CropID = &cid
	}
	if err := s.repo.Insert(ctx, &n); err != nil {
		return nil, err
	}
	s.bus.Publish(CreatedEvent{Notification: n})
	return &n, nil
}

// NotifyOnce crea la notificación solo si no se envió otra con la misma llave
// durante el enfriamiento.
func (s *Service) NotifyOnce(ctx context.Context, cooldownKey string, cooldown time.Duration,
	userID, cropID string, t Type, title, message string) (bool, error) {
	key := "notify:" + cooldownKey
	if !s.cache.SetIfAbsent(key, cooldown) {
		return false, nil
	}
	if _, err := s.Notify(ctx, userID, cropID, t, title, message); err != nil {
		// Si el INSERT falla, se libera el enfriamiento para no perder el aviso.
		s.cache.Delete(key)
		return false, err
	}
	return true, nil
}

// List devuelve las notificaciones del usuario (máximo 100).
func (s *Service) List(ctx context.Context, userID string, unreadOnly bool, limit int) ([]Notification, error) {
	oid, ok := objectid.Parse(userID)
	if !ok {
		return []Notification{}, nil
	}
	return s.repo.List(ctx, oid, unreadOnly, clamp(limit, 1, 100))
}

// UnreadCount cuenta las notificaciones sin leer.
func (s *Service) UnreadCount(ctx context.Context, userID string) (int64, error) {
	oid, ok := objectid.Parse(userID)
	if !ok {
		return 0, nil
	}
	return s.repo.CountUnread(ctx, oid)
}

// MarkRead marca una notificación como leída.
func (s *Service) MarkRead(ctx context.Context, userID, id string) (*Notification, error) {
	n, err := s.find(ctx, userID, id)
	if err != nil {
		return nil, err
	}
	if err := s.repo.SetRead(ctx, n.ID); err != nil {
		return nil, err
	}
	n.Read = true
	return n, nil
}

// MarkAllRead marca como leídas todas las del usuario.
func (s *Service) MarkAllRead(ctx context.Context, userID string) error {
	oid, ok := objectid.Parse(userID)
	if !ok {
		return nil
	}
	return s.repo.MarkAllRead(ctx, oid)
}

// Delete borra una notificación del usuario.
func (s *Service) Delete(ctx context.Context, userID, id string) error {
	if err := objectid.Require(id, notFound); err != nil {
		return err
	}
	userOID, _ := objectid.Parse(userID)
	idOID, _ := objectid.Parse(id)
	deleted, err := s.repo.DeleteByIDAndUser(ctx, idOID, userOID)
	if err != nil {
		return err
	}
	if deleted == 0 {
		return apperr.NotFound(notFound)
	}
	return nil
}

// DeleteAllForUser borra todas las notificaciones de la cuenta (borrado en cascada).
func (s *Service) DeleteAllForUser(ctx context.Context, userID string) error {
	oid, ok := objectid.Parse(userID)
	if !ok {
		return nil
	}
	return s.repo.DeleteByUser(ctx, oid)
}

// DeleteAllForCrop borra todas las notificaciones de un cultivo.
func (s *Service) DeleteAllForCrop(ctx context.Context, cropID string) error {
	oid, ok := objectid.Parse(cropID)
	if !ok {
		return nil
	}
	return s.repo.DeleteByCrop(ctx, oid)
}

func (s *Service) find(ctx context.Context, userID, id string) (*Notification, error) {
	if err := objectid.Require(id, notFound); err != nil {
		return nil, err
	}
	userOID, _ := objectid.Parse(userID)
	idOID, _ := objectid.Parse(id)
	n, err := s.repo.FindByIDAndUser(ctx, idOID, userOID)
	if err != nil {
		return nil, err
	}
	if n == nil {
		return nil, apperr.NotFound(notFound)
	}
	return n, nil
}

func clamp(v, lo, hi int) int {
	if v < lo {
		return lo
	}
	if v > hi {
		return hi
	}
	return v
}
