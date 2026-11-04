// Package actuators gestiona las salidas de cada cultivo (bomba, luz UV,
// ventilador, humidificador y dosificadores) y su estado confirmado.
package actuators

import (
	"encoding/json"
	"fmt"
	"strings"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/validation"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

// Type es la clase de actuador.
type Type string

const (
	WaterPump     Type = "WATER_PUMP"
	UVLight       Type = "UV_LIGHT"
	Fan           Type = "FAN"
	Humidifier    Type = "HUMIDIFIER"
	NutrientDoser Type = "NUTRIENT_DOSER"
	PhDoser       Type = "PH_DOSER"
)

// AllTypes son los seis tipos en orden (los de un cultivo virtual).
var AllTypes = []Type{WaterPump, UVLight, Fan, Humidifier, NutrientDoser, PhDoser}

// DefaultRealTypes son los tres actuadores de un cultivo real.
var DefaultRealTypes = []Type{WaterPump, UVLight, Fan}

type meta struct {
	subject  string
	feminine bool
}

var typeMeta = map[Type]meta{
	WaterPump:     {"Water pump", true},
	UVLight:       {"UV light", true},
	Fan:           {"Fan", false},
	Humidifier:    {"Humidifier", false},
	NutrientDoser: {"Nutrient doser", false},
	PhDoser:       {"pH doser", false},
}

// AlreadyIn arma el mensaje «Water pump is already on» / «Fan is already off».
func (t Type) AlreadyIn(on bool) string {
	m := typeMeta[t]
	state := "off"
	if on {
		state = "on"
	}
	return m.subject + " is already " + state
}

// UnmarshalJSON rechaza un tipo de actuador desconocido.
func (t *Type) UnmarshalJSON(b []byte) error {
	var s string
	if err := json.Unmarshal(b, &s); err != nil {
		return err
	}
	if _, ok := typeMeta[Type(s)]; !ok {
		return fmt.Errorf("invalid actuator type: %q", s)
	}
	*t = Type(s)
	return nil
}

// ParseType interpreta un tipo de actuador sin distinguir mayúsculas; el
// segundo valor indica si es válido.
func ParseType(value string) (Type, bool) {
	t := Type(strings.ToUpper(strings.TrimSpace(value)))
	_, ok := typeMeta[t]
	return t, ok
}

// Actuator es el documento de la colección "actuators".
type Actuator struct {
	ID            primitive.ObjectID `bson:"_id,omitempty"`
	CropID        primitive.ObjectID `bson:"cropId"`
	Type          Type               `bson:"type"`
	Active        bool               `bson:"active"`
	RunningUntil  *time.Time         `bson:"runningUntil,omitempty"`
	LastChangedAt *time.Time         `bson:"lastChangedAt,omitempty"`
	CreatedAt     time.Time          `bson:"createdAt"`
}

// IsRunning indica si el actuador está encendido ahora (sin límite o por una
// orden con duración que aún no termina).
func (a *Actuator) IsRunning(now time.Time) bool {
	return a.Active || (a.RunningUntil != nil && a.RunningUntil.After(now))
}

// ActuatorRequest agrega un actuador a un cultivo.
type ActuatorRequest struct {
	Type *Type `json:"type"`
}

// Validate exige el tipo.
func (r ActuatorRequest) Validate() validation.Fields {
	return validation.New().
		Required("type", r.Type != nil, "Actuator type is required").
		Result()
}

// ActuatorResponse es la vista pública de un actuador.
type ActuatorResponse struct {
	ID            string     `json:"id"`
	CropID        string     `json:"cropId"`
	Type          string     `json:"type"`
	Active        bool       `json:"active"`
	Running       bool       `json:"running"`
	RunningUntil  *time.Time `json:"runningUntil,omitempty"`
	LastChangedAt *time.Time `json:"lastChangedAt,omitempty"`
}

// ToResponse mapea el actuador a su vista pública.
func ToResponse(a *Actuator, now time.Time) ActuatorResponse {
	running := a.IsRunning(now)
	var runningUntil *time.Time
	if running && !a.Active {
		runningUntil = a.RunningUntil
	}
	return ActuatorResponse{
		ID:            a.ID.Hex(),
		CropID:        a.CropID.Hex(),
		Type:          string(a.Type),
		Active:        a.Active,
		Running:       running,
		RunningUntil:  runningUntil,
		LastChangedAt: a.LastChangedAt,
	}
}
