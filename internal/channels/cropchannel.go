package channels

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/cache"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/crops"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/notifications"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/objectid"
)

const (
	defaultDigestHours = 6
	sharePrefix        = "c_"
	recipientNotFound  = "That chat does not receive alerts for this crop"
)

// CropChannelService gestiona los avisos de cada cultivo por los canales externos.
type CropChannelService struct {
	repo     CropRepository
	channels *Service
	crops    *crops.Service
	cache    cache.Cache
	clock    func() time.Time
}

// NewCropChannelService crea el servicio de avisos por cultivo.
func NewCropChannelService(repo CropRepository, channels *Service, cropsSvc *crops.Service, store cache.Cache, clock func() time.Time) *CropChannelService {
	return &CropChannelService{repo: repo, channels: channels, crops: cropsSvc, cache: store, clock: clock}
}

// IsShareCode indica si un código es de compartir (no de vinculación).
func IsShareCode(code string) bool {
	return len(code) >= len(sharePrefix) && code[:len(sharePrefix)] == sharePrefix
}

// List devuelve los avisos del cultivo por cada canal.
func (s *CropChannelService) List(ctx context.Context, ownerID, cropID string) ([]CropChannelResponse, error) {
	crop, err := s.crops.GetOwned(ctx, ownerID, cropID)
	if err != nil {
		return nil, err
	}
	out := make([]CropChannelResponse, 0, len(AllTypes))
	for _, t := range AllTypes {
		settings, err := s.settings(ctx, crop, t)
		if err != nil {
			return nil, err
		}
		resp, err := s.response(ctx, crop, settings)
		if err != nil {
			return nil, err
		}
		out = append(out, resp)
	}
	return out, nil
}

// Update cambia los avisos del cultivo por un canal.
func (s *CropChannelService) Update(ctx context.Context, ownerID, cropID string, t Type, req CropChannelUpdateRequest) (CropChannelResponse, error) {
	crop, err := s.crops.GetOwned(ctx, ownerID, cropID)
	if err != nil {
		return CropChannelResponse{}, err
	}
	if _, err := s.channels.RequireAvailable(t); err != nil {
		return CropChannelResponse{}, err
	}
	settings, err := s.settings(ctx, crop, t)
	if err != nil {
		return CropChannelResponse{}, err
	}
	now := s.clock()
	if req.Enabled != nil {
		settings.Enabled = *req.Enabled
	}
	if req.Events != nil {
		settings.Events = append([]notifications.Type{}, (*req.Events)...)
	}
	if req.Delivery != nil && *req.Delivery != settings.Delivery {
		settings.Delivery = *req.Delivery
		settings.LastDigestAt = &now
	}
	if req.DigestHours != nil {
		settings.DigestHours = *req.DigestHours
	}
	if req.DailySummaryAt != nil {
		at := *req.DailySummaryAt
		if at != settings.DailySummaryAt {
			settings.DailySummaryAt = at
			settings.LastSummaryAt = &now
		}
	}
	settings.UpdatedAt = &now
	if err := s.repo.Save(ctx, settings); err != nil {
		return CropChannelResponse{}, err
	}
	return s.response(ctx, crop, settings)
}

// ShareCode genera un enlace de un solo uso para compartir los avisos con un chat.
func (s *CropChannelService) ShareCode(ctx context.Context, ownerID, cropID string, t Type) (LinkCodeResponse, error) {
	crop, err := s.crops.GetOwned(ctx, ownerID, cropID)
	if err != nil {
		return LinkCodeResponse{}, err
	}
	channel, err := s.channels.RequireAvailable(t)
	if err != nil {
		return LinkCodeResponse{}, err
	}
	settings, err := s.settings(ctx, crop, t)
	if err != nil {
		return LinkCodeResponse{}, err
	}
	if len(settings.Recipients) >= MaxRecipients {
		return LinkCodeResponse{}, apperr.BadRequest("You can share a crop with up to 10 chats")
	}
	code := sharePrefix + randomCode()
	s.cache.Put(shareKey(t, code), crop.HexID(), CodeTTL)
	return LinkCodeResponse{Type: t, Code: code, URL: channel.LinkURL(code), ExpiresAt: s.clock().Add(CodeTTL)}, nil
}

// AcceptShare completa la invitación desde el canal: el chat recibe los avisos.
func (s *CropChannelService) AcceptShare(ctx context.Context, t Type, code, address, displayName string) (*crops.Crop, bool) {
	if !IsShareCode(code) || len(code) > 64 {
		return nil, false
	}
	cropID, ok := s.cache.Take(shareKey(t, code))
	if !ok {
		return nil, false
	}
	crop, err := s.crops.Find(ctx, cropID)
	if err != nil || crop == nil {
		return nil, false
	}
	settings, err := s.settings(ctx, crop, t)
	if err != nil {
		return nil, false
	}
	recipients := make([]Recipient, 0, len(settings.Recipients))
	for _, r := range settings.Recipients {
		if r.Address != address {
			recipients = append(recipients, r)
		}
	}
	if len(recipients) < MaxRecipients {
		recipients = append(recipients, Recipient{ID: randomID(), Address: address, DisplayName: displayName, AddedAt: s.clock()})
		settings.Recipients = recipients
		now := s.clock()
		settings.UpdatedAt = &now
		_ = s.repo.Save(ctx, settings)
	}
	return crop, true
}

// RemoveRecipient deja de compartir los avisos con un chat.
func (s *CropChannelService) RemoveRecipient(ctx context.Context, ownerID, cropID string, t Type, recipientID string) error {
	crop, err := s.crops.GetOwned(ctx, ownerID, cropID)
	if err != nil {
		return err
	}
	oid, _ := objectid.Parse(crop.HexID())
	settings, err := s.repo.FindByCropAndType(ctx, oid, t)
	if err != nil {
		return err
	}
	if settings == nil {
		return apperr.NotFound(recipientNotFound)
	}
	kept := make([]Recipient, 0, len(settings.Recipients))
	removed := false
	for _, r := range settings.Recipients {
		if r.ID == recipientID {
			removed = true
			continue
		}
		kept = append(kept, r)
	}
	if !removed {
		return apperr.NotFound(recipientNotFound)
	}
	settings.Recipients = kept
	now := s.clock()
	settings.UpdatedAt = &now
	return s.repo.Save(ctx, settings)
}

// SharedWith devuelve los cultivos que se comparten con un chat.
func (s *CropChannelService) SharedWith(ctx context.Context, t Type, address string) ([]crops.Crop, error) {
	shared, err := s.repo.FindByTypeAndRecipientAddress(ctx, t, address)
	if err != nil {
		return nil, err
	}
	out := make([]crops.Crop, 0, len(shared))
	for i := range shared {
		crop, err := s.crops.Find(ctx, shared[i].CropID.Hex())
		if err == nil && crop != nil {
			out = append(out, *crop)
		}
	}
	return out, nil
}

// Leave hace que un chat deje de recibir los avisos de todos sus cultivos compartidos.
func (s *CropChannelService) Leave(ctx context.Context, t Type, address string) (int, error) {
	shared, err := s.repo.FindByTypeAndRecipientAddress(ctx, t, address)
	if err != nil {
		return 0, err
	}
	for i := range shared {
		s.RemoveAddress(ctx, &shared[i], address)
	}
	return len(shared), nil
}

// Find devuelve los avisos guardados de un cultivo por canal (nil si no hay).
func (s *CropChannelService) Find(ctx context.Context, cropID string, t Type) (*CropChannel, error) {
	oid, ok := objectid.Parse(cropID)
	if !ok {
		return nil, nil
	}
	return s.repo.FindByCropAndType(ctx, oid, t)
}

// RemoveAddress quita un chat de los destinatarios de un cultivo.
func (s *CropChannelService) RemoveAddress(ctx context.Context, settings *CropChannel, address string) {
	kept := make([]Recipient, 0, len(settings.Recipients))
	removed := false
	for _, r := range settings.Recipients {
		if r.Address == address {
			removed = true
			continue
		}
		kept = append(kept, r)
	}
	if removed {
		settings.Recipients = kept
		now := s.clock()
		settings.UpdatedAt = &now
		_ = s.repo.Save(ctx, settings)
	}
}

// DeleteAllForOwner borra los avisos de todos los cultivos de una cuenta.
func (s *CropChannelService) DeleteAllForOwner(ctx context.Context, ownerID string) error {
	oid, ok := objectid.Parse(ownerID)
	if !ok {
		return nil
	}
	return s.repo.DeleteByOwner(ctx, oid)
}

// OnCropDeleted borra los avisos de un cultivo eliminado.
func (s *CropChannelService) OnCropDeleted(cropID string) {
	if oid, ok := objectid.Parse(cropID); ok {
		_ = s.repo.DeleteByCrop(context.Background(), oid)
	}
}

// settings devuelve lo guardado o, si no hay, lo del perfil del dueño (al instante).
func (s *CropChannelService) settings(ctx context.Context, crop *crops.Crop, t Type) (*CropChannel, error) {
	oid, _ := objectid.Parse(crop.HexID())
	settings, err := s.repo.FindByCropAndType(ctx, oid, t)
	if err != nil {
		return nil, err
	}
	if settings != nil {
		return settings, nil
	}
	events := append([]notifications.Type{}, DefaultEvents...)
	if link, _ := s.channels.Link(ctx, crop.OwnerID.Hex(), t); link != nil && len(link.Events) > 0 {
		events = append([]notifications.Type{}, link.Events...)
	}
	return &CropChannel{
		CropID:      crop.ID,
		OwnerID:     crop.OwnerID,
		Type:        t,
		Enabled:     true,
		Events:      events,
		Delivery:    DeliveryInstant,
		DigestHours: defaultDigestHours,
		Recipients:  []Recipient{},
	}, nil
}

func (s *CropChannelService) response(ctx context.Context, crop *crops.Crop, settings *CropChannel) (CropChannelResponse, error) {
	_, available := s.channels.AvailableChannel(settings.Type)
	link, err := s.channels.Link(ctx, crop.OwnerID.Hex(), settings.Type)
	if err != nil {
		return CropChannelResponse{}, err
	}
	recipients := make([]RecipientResponse, 0, len(settings.Recipients))
	for _, r := range settings.Recipients {
		recipients = append(recipients, RecipientResponse{ID: r.ID, DisplayName: r.DisplayName, AddedAt: r.AddedAt})
	}
	events := sortedEvents(settings.Events)
	return CropChannelResponse{
		Type:           settings.Type,
		Name:           NameOf(settings.Type),
		Available:      available,
		Linked:         link != nil,
		Enabled:        settings.Enabled,
		Events:         events,
		Delivery:       settings.Delivery,
		DigestHours:    settings.DigestHours,
		DailySummaryAt: settings.DailySummaryAt,
		Recipients:     recipients,
	}, nil
}

func shareKey(t Type, code string) string {
	return "crop-share:" + lower(t) + ":" + code
}

func randomID() string {
	b := make([]byte, 16)
	_, _ = rand.Read(b)
	return hex.EncodeToString(b)
}
