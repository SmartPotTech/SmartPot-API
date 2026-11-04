// Package channels reenvía las notificaciones de la PWA a canales externos (hoy
// Telegram): vínculos de la cuenta, avisos por cultivo, resúmenes y compartir
// con otros chats. Cada canal implementa NotificationChannel.
package channels

import (
	"encoding/json"
	"fmt"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/notifications"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

// MaxRecipients es el máximo de chats con los que se comparte un cultivo.
const MaxRecipients = 10

// Type es un canal externo de notificación.
type Type string

const (
	Telegram Type = "TELEGRAM"
)

// AllTypes son los canales que ofrece SmartPot.
var AllTypes = []Type{Telegram}

// Delivery es cómo llegan los avisos de un cultivo.
type Delivery string

const (
	DeliveryInstant Delivery = "INSTANT"
	DeliveryDigest  Delivery = "DIGEST"
)

// UnmarshalJSON rechaza un modo de entrega desconocido.
func (d *Delivery) UnmarshalJSON(b []byte) error {
	var s string
	if err := json.Unmarshal(b, &s); err != nil {
		return err
	}
	switch Delivery(s) {
	case DeliveryInstant, DeliveryDigest:
		*d = Delivery(s)
		return nil
	default:
		return fmt.Errorf("invalid delivery mode: %q", s)
	}
}

// ChannelLink vincula una cuenta con un canal externo (colección "channel_links").
type ChannelLink struct {
	ID              primitive.ObjectID   `bson:"_id,omitempty"`
	UserID          primitive.ObjectID   `bson:"userId"`
	Type            Type                 `bson:"type"`
	Address         string               `bson:"address"`
	DisplayName     string               `bson:"displayName,omitempty"`
	Enabled         bool                 `bson:"enabled"`
	Events          []notifications.Type `bson:"events"`
	LinkedAt        *time.Time           `bson:"linkedAt,omitempty"`
	LastDeliveredAt *time.Time           `bson:"lastDeliveredAt,omitempty"`
	Failures        int                  `bson:"failures"`
}

// Recipient es un chat con el que se comparte un cultivo. Las etiquetas json fijan las claves del arreglo JSONB en
// PostgreSQL (crop_channels.recipients).
type Recipient struct {
	ID          string    `json:"id" bson:"id"`
	Address     string    `json:"address" bson:"address"`
	DisplayName string    `json:"displayName,omitempty" bson:"displayName,omitempty"`
	AddedAt     time.Time `json:"addedAt" bson:"addedAt"`
}

// CropChannel define qué avisa un cultivo por un canal y a quién (colección
// "crop_channels"). Sin este documento el cultivo avisa al instante lo del perfil.
type CropChannel struct {
	ID             primitive.ObjectID   `bson:"_id,omitempty"`
	CropID         primitive.ObjectID   `bson:"cropId"`
	OwnerID        primitive.ObjectID   `bson:"ownerId"`
	Type           Type                 `bson:"type"`
	Enabled        bool                 `bson:"enabled"`
	Events         []notifications.Type `bson:"events"`
	Delivery       Delivery             `bson:"delivery"`
	DigestHours    int                  `bson:"digestHours"`
	DailySummaryAt string               `bson:"dailySummaryAt,omitempty"`
	Recipients     []Recipient          `bson:"recipients"`
	LastDigestAt   *time.Time           `bson:"lastDigestAt,omitempty"`
	LastSummaryAt  *time.Time           `bson:"lastSummaryAt,omitempty"`
	UpdatedAt      *time.Time           `bson:"updatedAt,omitempty"`
}

// ChannelMessage es un mensaje independiente del canal; cada canal le da formato.
type ChannelMessage struct {
	Type  notifications.Type
	Title string
	Body  string
	URL   string
}

// DeliveryError indica que el canal no pudo entregar el mensaje. Permanent
// significa que reintentar no sirve (chat bloqueado o borrado).
type DeliveryError struct {
	Message   string
	Permanent bool
}

func (e *DeliveryError) Error() string { return e.Message }

// NotificationChannel es el contrato de un canal externo. Sumar un canal es una
// nueva implementación registrada al arrancar y un valor en Type.
type NotificationChannel interface {
	Type() Type
	IsAvailable() bool
	DisplayName() string
	Handle() string
	LinkURL(code string) string
	Description() string
	Requirements() []string
	Send(address string, message ChannelMessage) error
}

// containsType indica si la lista de eventos incluye el tipo dado.
func containsType(events []notifications.Type, t notifications.Type) bool {
	for _, e := range events {
		if e == t {
			return true
		}
	}
	return false
}
