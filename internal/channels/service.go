package channels

import (
	"context"
	"crypto/rand"
	"encoding/base64"
	"log/slog"
	"net/http"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/cache"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/notifications"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/objectid"
)

// CodeTTL es la vigencia de un código de vinculación o de compartir.
const CodeTTL = 10 * time.Minute

const linkNotFound = "Channel is not linked"

// DefaultEvents son los tipos que un vínculo nuevo recibe por defecto.
var DefaultEvents = []notifications.Type{notifications.TypeAlert, notifications.TypeDevice, notifications.TypeAI}

var channelNames = map[Type]string{Telegram: "Telegram"}

// Service gestiona los vínculos de la cuenta con los canales externos.
type Service struct {
	repo     LinkRepository
	channels map[Type]NotificationChannel
	cache    cache.Cache
	clock    func() time.Time
}

// NewService crea el servicio de canales.
func NewService(repo LinkRepository, channels []NotificationChannel, store cache.Cache, clock func() time.Time) *Service {
	byType := make(map[Type]NotificationChannel, len(channels))
	for _, c := range channels {
		byType[c.Type()] = c
	}
	return &Service{repo: repo, channels: byType, cache: store, clock: clock}
}

// NameOf devuelve el nombre visible del canal.
func NameOf(t Type) string {
	if name, ok := channelNames[t]; ok {
		return name
	}
	return string(t)
}

// Overview lista los canales disponibles y los vínculos de la cuenta.
func (s *Service) Overview(ctx context.Context, userID string) ([]ChannelOptionResponse, error) {
	oid, ok := objectid.Parse(userID)
	if !ok {
		return []ChannelOptionResponse{}, nil
	}
	links, err := s.repo.FindByUser(ctx, oid)
	if err != nil {
		return nil, err
	}
	byType := make(map[Type]ChannelLink, len(links))
	for i := range links {
		byType[links[i].Type] = links[i]
	}
	out := make([]ChannelOptionResponse, 0, len(AllTypes))
	for _, t := range AllTypes {
		channel := s.channels[t]
		available := channel != nil && channel.IsAvailable()
		opt := ChannelOptionResponse{Type: t, Name: NameOf(t), Available: available, Requirements: []string{}}
		if channel != nil {
			opt.Description = channel.Description()
			if available {
				opt.Handle = channel.Handle()
			} else {
				opt.Requirements = channel.Requirements()
			}
		}
		if link, ok := byType[t]; ok {
			resp := linkResponseOf(&link)
			opt.Link = &resp
		}
		out = append(out, opt)
	}
	return out, nil
}

// CreateLinkCode genera un código de un solo uso para vincular un canal.
func (s *Service) CreateLinkCode(ctx context.Context, userID string, t Type) (LinkCodeResponse, error) {
	channel, err := s.RequireAvailable(t)
	if err != nil {
		return LinkCodeResponse{}, err
	}
	code := randomCode()
	s.cache.Put(codeKey(t, code), userID, CodeTTL)
	return LinkCodeResponse{Type: t, Code: code, URL: channel.LinkURL(code), ExpiresAt: s.clock().Add(CodeTTL)}, nil
}

// CompleteLink completa el vínculo desde el canal con el código.
func (s *Service) CompleteLink(ctx context.Context, t Type, code, address, displayName string) (*ChannelLink, bool) {
	if code == "" || len(code) > 64 {
		return nil, false
	}
	userID, ok := s.cache.Take(codeKey(t, code))
	if !ok {
		return nil, false
	}
	userOID, ok := objectid.Parse(userID)
	if !ok {
		return nil, false
	}
	if existing, _ := s.repo.FindByTypeAndAddress(ctx, t, address); existing != nil && existing.UserID != userOID {
		_ = s.repo.Delete(ctx, existing.ID)
	}
	link, err := s.repo.FindByUserAndType(ctx, userOID, t)
	if err != nil {
		return nil, false
	}
	if link == nil {
		link = &ChannelLink{UserID: userOID, Type: t, Events: append([]notifications.Type{}, DefaultEvents...)}
	}
	now := s.clock()
	link.Address = address
	link.DisplayName = displayName
	link.Enabled = true
	link.Failures = 0
	link.LinkedAt = &now
	if err := s.repo.Save(ctx, link); err != nil {
		return nil, false
	}
	slog.Info("channel linked", "type", t, "userId", userID)
	return link, true
}

// FindByAddress busca el vínculo de una dirección (lo usa el bot).
func (s *Service) FindByAddress(ctx context.Context, t Type, address string) (*ChannelLink, error) {
	return s.repo.FindByTypeAndAddress(ctx, t, address)
}

// Update activa/pausa el vínculo o cambia qué notificaciones recibe.
func (s *Service) Update(ctx context.Context, userID, linkID string, req ChannelLinkUpdateRequest) (ChannelLinkResponse, error) {
	link, err := s.find(ctx, userID, linkID)
	if err != nil {
		return ChannelLinkResponse{}, err
	}
	if req.Enabled != nil {
		link.Enabled = *req.Enabled
		if *req.Enabled {
			link.Failures = 0
		}
	}
	if req.Events != nil {
		link.Events = append([]notifications.Type{}, (*req.Events)...)
	}
	if err := s.repo.Save(ctx, link); err != nil {
		return ChannelLinkResponse{}, err
	}
	return linkResponseOf(link), nil
}

// SendTest envía un mensaje de prueba al vínculo.
func (s *Service) SendTest(ctx context.Context, userID, linkID string) error {
	link, err := s.find(ctx, userID, linkID)
	if err != nil {
		return err
	}
	channel, err := s.RequireAvailable(link.Type)
	if err != nil {
		return err
	}
	msg := ChannelMessage{Type: notifications.TypeInfo, Title: "SmartPot test notification",
		Body: "This is how crop alerts will reach you. You can configure alert preferences in your profile."}
	if err := channel.Send(link.Address, msg); err != nil {
		// El proveedor externo falló: es un 502, no un error del cliente.
		return apperr.New(http.StatusBadGateway, "Could not send test message: "+err.Error())
	}
	s.Delivered(ctx, link)
	return nil
}

// Unlink desvincula un canal de la cuenta.
func (s *Service) Unlink(ctx context.Context, userID, linkID string) error {
	link, err := s.find(ctx, userID, linkID)
	if err != nil {
		return err
	}
	return s.repo.Delete(ctx, link.ID)
}

// UnlinkAddress desvincula una dirección (lo usa el bot con /desvincular).
func (s *Service) UnlinkAddress(ctx context.Context, t Type, address string) (bool, error) {
	link, err := s.repo.FindByTypeAndAddress(ctx, t, address)
	if err != nil {
		return false, err
	}
	if link == nil {
		return false, nil
	}
	return true, s.repo.Delete(ctx, link.ID)
}

// DeleteAllForUser borra los vínculos de una cuenta (borrado en cascada).
func (s *Service) DeleteAllForUser(ctx context.Context, userID string) error {
	oid, ok := objectid.Parse(userID)
	if !ok {
		return nil
	}
	return s.repo.DeleteByUser(ctx, oid)
}

// Link devuelve el vínculo de la cuenta con un canal (nil si no hay).
func (s *Service) Link(ctx context.Context, userID string, t Type) (*ChannelLink, error) {
	oid, ok := objectid.Parse(userID)
	if !ok {
		return nil, nil
	}
	return s.repo.FindByUserAndType(ctx, oid, t)
}

// AvailableChannel devuelve el canal si el servidor lo tiene configurado.
func (s *Service) AvailableChannel(t Type) (NotificationChannel, bool) {
	channel := s.channels[t]
	if channel != nil && channel.IsAvailable() {
		return channel, true
	}
	return nil, false
}

// RequireAvailable devuelve el canal configurado o un error 503.
func (s *Service) RequireAvailable(t Type) (NotificationChannel, error) {
	if channel, ok := s.AvailableChannel(t); ok {
		return channel, nil
	}
	return nil, apperr.Unavailable("Channel " + NameOf(t) + " is not configured on this server")
}

// Delivered registra una entrega exitosa.
func (s *Service) Delivered(ctx context.Context, link *ChannelLink) {
	now := s.clock()
	link.LastDeliveredAt = &now
	link.Failures = 0
	_ = s.repo.Save(ctx, link)
}

// Failed registra un fallo; tras varios o uno permanente, pausa el vínculo.
func (s *Service) Failed(ctx context.Context, link *ChannelLink, permanent bool) {
	link.Failures++
	if permanent || link.Failures >= 5 {
		link.Enabled = false
		slog.Info("channel paused after failures", "type", link.Type, "failures", link.Failures)
	}
	_ = s.repo.Save(ctx, link)
}

func (s *Service) find(ctx context.Context, userID, linkID string) (*ChannelLink, error) {
	if err := objectid.Require(linkID, linkNotFound); err != nil {
		return nil, err
	}
	userOID, _ := objectid.Parse(userID)
	idOID, _ := objectid.Parse(linkID)
	link, err := s.repo.FindByIDAndUser(ctx, idOID, userOID)
	if err != nil {
		return nil, err
	}
	if link == nil {
		return nil, apperr.NotFound(linkNotFound)
	}
	return link, nil
}

func codeKey(t Type, code string) string {
	return "channel-link:" + lower(t) + ":" + code
}

func lower(t Type) string {
	b := []byte(t)
	for i := range b {
		if b[i] >= 'A' && b[i] <= 'Z' {
			b[i] += 'a' - 'A'
		}
	}
	return string(b)
}

func randomCode() string {
	b := make([]byte, 18)
	_, _ = rand.Read(b)
	return base64.RawURLEncoding.EncodeToString(b)
}
