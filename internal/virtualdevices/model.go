// Package virtualdevices gestiona la simulación de los cultivos virtuales a
// través del simulador (SmartPot-DataGenerator). Es un paquete hoja: depende de
// los cultivos mediante una interfaz propia, no del paquete crops.
package virtualdevices

import (
	"encoding/json"
	"fmt"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/readings"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

// Mode es cómo se simula un cultivo virtual.
type Mode string

const (
	ModeAuto    Mode = "AUTO"
	ModeManual  Mode = "MANUAL"
	ModeWeather Mode = "WEATHER"
)

// UnmarshalJSON rechaza un modo desconocido (mismo 400 que los enums de Java).
func (m *Mode) UnmarshalJSON(b []byte) error {
	var v string
	if err := json.Unmarshal(b, &v); err != nil {
		return err
	}
	switch Mode(v) {
	case ModeAuto, ModeManual, ModeWeather:
		*m = Mode(v)
		return nil
	default:
		return fmt.Errorf("invalid simulation mode: %q", v)
	}
}

// Location es la ubicación de una simulación.
type Location struct {
	Name      string  `json:"name" bson:"name"`
	Latitude  float64 `json:"latitude" bson:"latitude"`
	Longitude float64 `json:"longitude" bson:"longitude"`
}

// VirtualDevice es la configuración de simulación (colección "virtual_devices").
type VirtualDevice struct {
	ID              primitive.ObjectID `bson:"_id,omitempty"`
	CropID          primitive.ObjectID `bson:"cropId"`
	OwnerID         primitive.ObjectID `bson:"ownerId"`
	Mode            Mode               `bson:"mode"`
	Manual          *readings.Measures `bson:"manual,omitempty"`
	Location        *Location          `bson:"location,omitempty"`
	IntervalSeconds int                `bson:"intervalSeconds"`
	Active          *bool              `bson:"active,omitempty"`
	CreatedAt       time.Time          `bson:"createdAt"`
	UpdatedAt       time.Time          `bson:"updatedAt"`
}

// IsActive indica si la simulación está encendida (las anteriores al campo lo están).
func (v *VirtualDevice) IsActive() bool {
	return v.Active == nil || *v.Active
}
