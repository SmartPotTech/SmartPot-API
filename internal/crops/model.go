// Package crops gestiona los cultivos reales y virtuales: su tipo y forma, el
// lugar, el modo automático y las credenciales del dispositivo. Es la capa que
// orquesta lecturas, dispositivos virtuales, MQTT, actuadores e IA a través de
// interfaces, para no acoplarse a sus implementaciones.
package crops

import (
	"encoding/json"
	"fmt"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/geo"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

// CropType es la especie del cultivo.
type CropType string

const (
	TypeTomato     CropType = "TOMATO"
	TypeLettuce    CropType = "LETTUCE"
	TypeStrawberry CropType = "STRAWBERRY"
	TypeBasil      CropType = "BASIL"
	TypeSpinach    CropType = "SPINACH"
	TypePepper     CropType = "PEPPER"
)

// CropKind es el origen de las lecturas, fijo al crear el cultivo.
type CropKind string

const (
	KindReal    CropKind = "REAL"
	KindVirtual CropKind = "VIRTUAL"
)

// CropForm es la forma del sistema hidropónico (solo cambia la ilustración).
type CropForm string

const (
	FormPot   CropForm = "POT"
	FormNFT   CropForm = "NFT"
	FormTower CropForm = "TOWER"
	FormRaft  CropForm = "RAFT"
)

// UnmarshalJSON rechaza un tipo desconocido, para que un valor inválido dé el
// mismo 400 ("JSON no válido") que la deserialización de enums en Java.
func (t *CropType) UnmarshalJSON(b []byte) error {
	s, err := unmarshalEnum(b)
	if err != nil {
		return err
	}
	switch CropType(s) {
	case TypeTomato, TypeLettuce, TypeStrawberry, TypeBasil, TypeSpinach, TypePepper:
		*t = CropType(s)
		return nil
	default:
		return fmt.Errorf("invalid crop type: %q", s)
	}
}

// UnmarshalJSON rechaza un tipo (real/virtual) desconocido.
func (k *CropKind) UnmarshalJSON(b []byte) error {
	s, err := unmarshalEnum(b)
	if err != nil {
		return err
	}
	switch CropKind(s) {
	case KindReal, KindVirtual:
		*k = CropKind(s)
		return nil
	default:
		return fmt.Errorf("invalid crop type: %q", s)
	}
}

// UnmarshalJSON rechaza una forma desconocida.
func (f *CropForm) UnmarshalJSON(b []byte) error {
	s, err := unmarshalEnum(b)
	if err != nil {
		return err
	}
	switch CropForm(s) {
	case FormPot, FormNFT, FormTower, FormRaft:
		*f = CropForm(s)
		return nil
	default:
		return fmt.Errorf("invalid crop form: %q", s)
	}
}

func unmarshalEnum(b []byte) (string, error) {
	var s string
	if err := json.Unmarshal(b, &s); err != nil {
		return "", err
	}
	return s, nil
}

// Device son las credenciales MQTT del dispositivo; la clave se guarda cifrada. Las etiquetas json fijan las
// claves del subdocumento JSONB en PostgreSQL (crops.device).
type Device struct {
	KeyCiphertext string     `json:"keyCiphertext,omitempty" bson:"keyCiphertext,omitempty"`
	KeyRotatedAt  *time.Time `json:"keyRotatedAt,omitempty" bson:"keyRotatedAt,omitempty"`
	Online        bool       `json:"online" bson:"online"`
	LastSeenAt    *time.Time `json:"lastSeenAt,omitempty" bson:"lastSeenAt,omitempty"`
}

// CropHealth es la última evaluación del asistente de IA (índice de 0 a 100).
type CropHealth struct {
	Index       float64   `json:"index" bson:"index"`
	Level       string    `json:"level" bson:"level"`
	Label       string    `json:"label" bson:"label"`
	EvaluatedAt time.Time `json:"evaluatedAt" bson:"evaluatedAt"`
}

// Crop es el documento de la colección "crops".
type Crop struct {
	ID                primitive.ObjectID `bson:"_id,omitempty"`
	OwnerID           primitive.ObjectID `bson:"ownerId"`
	Name              string             `bson:"name"`
	Type              CropType           `bson:"type"`
	Kind              CropKind           `bson:"kind,omitempty"`
	Form              CropForm           `bson:"form,omitempty"`
	Placement         *geo.Placement     `bson:"placement,omitempty"`
	AutomationEnabled bool               `bson:"automationEnabled"`
	Device            *Device            `bson:"device,omitempty"`
	Health            *CropHealth        `bson:"health,omitempty"`
	CreatedAt         time.Time          `bson:"createdAt"`
	UpdatedAt         time.Time          `bson:"updatedAt"`
}

// IsVirtual indica si el cultivo es virtual.
func (c *Crop) IsVirtual() bool {
	return c.Kind == KindVirtual
}

// HexID devuelve el id como cadena hexadecimal.
func (c *Crop) HexID() string {
	return c.ID.Hex()
}

// KindOrDefault devuelve el tipo del cultivo (REAL si no está fijado).
func (c *Crop) KindOrDefault() CropKind {
	if c.Kind == "" {
		return KindReal
	}
	return c.Kind
}

// FormOrDefault devuelve la forma del cultivo (POT si no está fijada).
func (c *Crop) FormOrDefault() CropForm {
	if c.Form == "" {
		return FormPot
	}
	return c.Form
}

// DeletedEvent anuncia que se borró un cultivo (borrado en cascada de sus datos).
type DeletedEvent struct {
	CropID  string
	OwnerID string
}

// PlacementChangedEvent anuncia que el lugar del cultivo cambió.
type PlacementChangedEvent struct {
	CropID string
}

// DeviceKeyRotatedEvent anuncia que se rotó la clave del dispositivo.
type DeviceKeyRotatedEvent struct {
	CropID string
}
