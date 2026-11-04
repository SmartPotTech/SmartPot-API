package channels

import (
	"regexp"
	"sort"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/notifications"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/validation"
)

var dailySummaryPattern = regexp.MustCompile(`^$|^([01]\d|2[0-3]):[0-5]\d$`)

// ChannelLinkResponse es la vista pública de un vínculo.
type ChannelLinkResponse struct {
	ID              string               `json:"id"`
	Type            Type                 `json:"type"`
	Address         string               `json:"address"`
	DisplayName     string               `json:"displayName,omitempty"`
	Enabled         bool                 `json:"enabled"`
	Events          []notifications.Type `json:"events"`
	LinkedAt        *time.Time           `json:"linkedAt,omitempty"`
	LastDeliveredAt *time.Time           `json:"lastDeliveredAt,omitempty"`
}

func linkResponseOf(link *ChannelLink) ChannelLinkResponse {
	return ChannelLinkResponse{
		ID:              link.ID.Hex(),
		Type:            link.Type,
		Address:         link.Address,
		DisplayName:     link.DisplayName,
		Enabled:         link.Enabled,
		Events:          sortedEvents(link.Events),
		LinkedAt:        link.LinkedAt,
		LastDeliveredAt: link.LastDeliveredAt,
	}
}

// ChannelOptionResponse es un canal que ofrece SmartPot y el vínculo de la cuenta.
type ChannelOptionResponse struct {
	Type         Type                 `json:"type"`
	Name         string               `json:"name"`
	Description  string               `json:"description,omitempty"`
	Available    bool                 `json:"available"`
	Handle       string               `json:"handle,omitempty"`
	Requirements []string             `json:"requirements"`
	Link         *ChannelLinkResponse `json:"link,omitempty"`
}

// LinkCodeResponse es un código de un solo uso para vincular o compartir.
type LinkCodeResponse struct {
	Type      Type      `json:"type"`
	Code      string    `json:"code"`
	URL       string    `json:"url"`
	ExpiresAt time.Time `json:"expiresAt"`
}

// ChannelLinkUpdateRequest cambia un vínculo; solo cambia lo que llega.
type ChannelLinkUpdateRequest struct {
	Enabled *bool                 `json:"enabled"`
	Events  *[]notifications.Type `json:"events"`
}

// Validate limita el número de tipos.
func (r ChannelLinkUpdateRequest) Validate() validation.Fields {
	v := validation.New()
	if r.Events != nil && len(*r.Events) > 5 {
		v.Add("events", "Select at most 5 event types")
	}
	return v.Result()
}

// RecipientResponse es un chat con el que se comparte un cultivo.
type RecipientResponse struct {
	ID          string    `json:"id"`
	DisplayName string    `json:"displayName,omitempty"`
	AddedAt     time.Time `json:"addedAt"`
}

// CropChannelResponse son los avisos de un cultivo por un canal.
type CropChannelResponse struct {
	Type           Type                 `json:"type"`
	Name           string               `json:"name"`
	Available      bool                 `json:"available"`
	Linked         bool                 `json:"linked"`
	Enabled        bool                 `json:"enabled"`
	Events         []notifications.Type `json:"events"`
	Delivery       Delivery             `json:"delivery"`
	DigestHours    int                  `json:"digestHours"`
	DailySummaryAt string               `json:"dailySummaryAt,omitempty"`
	Recipients     []RecipientResponse  `json:"recipients"`
}

// CropChannelUpdateRequest cambia los avisos de un cultivo; lo que falta se conserva.
type CropChannelUpdateRequest struct {
	Enabled        *bool                 `json:"enabled"`
	Events         *[]notifications.Type `json:"events"`
	Delivery       *Delivery             `json:"delivery"`
	DigestHours    *int                  `json:"digestHours"`
	DailySummaryAt *string               `json:"dailySummaryAt"`
}

// Validate aplica las reglas del resumen y la hora.
func (r CropChannelUpdateRequest) Validate() validation.Fields {
	v := validation.New()
	if r.DigestHours != nil {
		if *r.DigestHours < 1 {
			v.Add("digestHours", "Digest interval must be at least 1 hour")
		} else if *r.DigestHours > 24 {
			v.Add("digestHours", "Digest interval must be at most 24 hours")
		}
	}
	if r.DailySummaryAt != nil && !dailySummaryPattern.MatchString(*r.DailySummaryAt) {
		v.Add("dailySummaryAt", "Use HH:mm format, for example 07:30")
	}
	return v.Result()
}

// sortedEvents ordena los tipos por el ordinal del enum, igual que el TreeSet y
// el EnumSet de la versión en Java (no por orden alfabético).
func sortedEvents(events []notifications.Type) []notifications.Type {
	out := make([]notifications.Type, len(events))
	copy(out, events)
	sort.Slice(out, func(i, j int) bool { return notifications.Ordinal(out[i]) < notifications.Ordinal(out[j]) })
	return out
}
