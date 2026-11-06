// Package commands gestiona las órdenes a los actuadores: su ciclo de vida
// (PENDING → SENT → EXECUTED | FAILED | EXPIRED), el envío por MQTT, la
// confirmación del dispositivo y las órdenes en bloque.
package commands

import (
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/actuators"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/validation"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

// ErrInFlight indica que el actuador ya tiene una orden en curso. El índice único parcial de PostgreSQL es la
// autoridad; el servicio lo traduce a 409 Conflict (o a SKIPPED en las órdenes en bloque).
var ErrInFlight = errors.New("command already in flight for this actuator")

// Action es la acción sobre el actuador.
type Action string

const (
	Activate   Action = "ACTIVATE"
	Deactivate Action = "DEACTIVATE"
)

// UnmarshalJSON rechaza una acción desconocida.
func (a *Action) UnmarshalJSON(b []byte) error {
	var s string
	if err := json.Unmarshal(b, &s); err != nil {
		return err
	}
	switch Action(s) {
	case Activate, Deactivate:
		*a = Action(s)
		return nil
	default:
		return fmt.Errorf("invalid action: %q", s)
	}
}

// ParseAction interpreta una acción sin distinguir mayúsculas; el segundo valor
// indica si es válida.
func ParseAction(value string) (Action, bool) {
	a := Action(strings.ToUpper(strings.TrimSpace(value)))
	switch a {
	case Activate, Deactivate:
		return a, true
	default:
		return "", false
	}
}

// Source es el origen del comando.
type Source string

const (
	SourceUser  Source = "USER"
	SourceAgent Source = "AGENT"
)

// Status es el estado del comando.
type Status string

const (
	StatusPending  Status = "PENDING"
	StatusSent     Status = "SENT"
	StatusExecuted Status = "EXECUTED"
	StatusFailed   Status = "FAILED"
	StatusExpired  Status = "EXPIRED"
)

// Command es el documento de la colección "commands".
type Command struct {
	ID              primitive.ObjectID `bson:"_id,omitempty"`
	CropID          primitive.ObjectID `bson:"cropId"`
	ActuatorID      primitive.ObjectID `bson:"actuatorId"`
	ActuatorType    actuators.Type     `bson:"actuatorType"`
	Action          Action             `bson:"action"`
	DurationSeconds *int               `bson:"durationSeconds,omitempty"`
	Status          Status             `bson:"status"`
	Source          Source             `bson:"source"`
	Reason          string             `bson:"reason,omitempty"`
	Message         string             `bson:"message,omitempty"`
	CreatedAt       time.Time          `bson:"createdAt"`
	SentAt          *time.Time         `bson:"sentAt,omitempty"`
	CompletedAt     *time.Time         `bson:"completedAt,omitempty"`
}

// CommandMessage es la orden que recibe el dispositivo en .../commands. Omite nulos.
type CommandMessage struct {
	ID              string `json:"id"`
	Actuator        string `json:"actuator"`
	Action          string `json:"action"`
	DurationSeconds *int   `json:"durationSeconds,omitempty"`
}

// AckMessage es la confirmación del dispositivo en .../commands/ack.
type AckMessage struct {
	ID      string `json:"id"`
	Status  string `json:"status"`
	Message string `json:"message"`
}

// Executed indica si el dispositivo ejecutó el comando.
func (a AckMessage) Executed() bool {
	return a.Status == "EXECUTED" || a.Status == "executed"
}

// CommandRequest es una orden a un actuador.
type CommandRequest struct {
	ActuatorID      string  `json:"actuatorId"`
	Action          *Action `json:"action"`
	DurationSeconds *int    `json:"durationSeconds"`
}

// Validate aplica las reglas de la versión en Java.
func (r CommandRequest) Validate() validation.Fields {
	v := validation.New().
		NotBlank("actuatorId", r.ActuatorID, "Actuator is required").
		Required("action", r.Action != nil, "Action is required")
	validateDuration(v, r.DurationSeconds)
	return v.Result()
}

// BulkCommandRequest es la misma orden para varios cultivos.
type BulkCommandRequest struct {
	CropIds         []string        `json:"cropIds"`
	ActuatorType    *actuators.Type `json:"actuatorType"`
	Action          *Action         `json:"action"`
	DurationSeconds *int            `json:"durationSeconds"`
}

// Validate aplica las reglas de la versión en Java.
func (r BulkCommandRequest) Validate() validation.Fields {
	v := validation.New()
	if len(r.CropIds) > 20 {
		v.Add("cropIds", "Select at most 20 crops")
	}
	v.Required("actuatorType", r.ActuatorType != nil, "Actuator type is required").
		Required("action", r.Action != nil, "Action is required")
	validateDuration(v, r.DurationSeconds)
	return v.Result()
}

func validateDuration(v *validation.Builder, seconds *int) {
	if seconds == nil {
		return
	}
	if *seconds < 1 {
		v.Add("durationSeconds", "Minimum duration is 1 second")
	} else if *seconds > 3600 {
		v.Add("durationSeconds", "Maximum duration is 1 hour")
	}
}

// CommandResponse es la vista pública de un comando.
type CommandResponse struct {
	ID              string     `json:"id"`
	CropID          string     `json:"cropId"`
	ActuatorID      string     `json:"actuatorId"`
	ActuatorType    string     `json:"actuatorType"`
	Action          string     `json:"action"`
	DurationSeconds *int       `json:"durationSeconds,omitempty"`
	Status          string     `json:"status"`
	Source          string     `json:"source"`
	Reason          string     `json:"reason,omitempty"`
	Message         string     `json:"message,omitempty"`
	CreatedAt       time.Time  `json:"createdAt"`
	SentAt          *time.Time `json:"sentAt,omitempty"`
	CompletedAt     *time.Time `json:"completedAt,omitempty"`
}

// ToResponse mapea el comando a su vista pública.
func ToResponse(c *Command) CommandResponse {
	return CommandResponse{
		ID:              c.ID.Hex(),
		CropID:          c.CropID.Hex(),
		ActuatorID:      c.ActuatorID.Hex(),
		ActuatorType:    string(c.ActuatorType),
		Action:          string(c.Action),
		DurationSeconds: c.DurationSeconds,
		Status:          string(c.Status),
		Source:          string(c.Source),
		Reason:          c.Reason,
		Message:         c.Message,
		CreatedAt:       c.CreatedAt,
		SentAt:          c.SentAt,
		CompletedAt:     c.CompletedAt,
	}
}

// BulkResult es el resultado de una orden en bloque para un cultivo.
type BulkResult struct {
	CropID    string `json:"cropId"`
	CropName  string `json:"cropName"`
	Status    string `json:"status"`
	CommandID string `json:"commandId,omitempty"`
	Message   string `json:"message,omitempty"`
}

// BulkCommandResponse resume el resultado por cultivo.
type BulkCommandResponse struct {
	Sent    int          `json:"sent"`
	Skipped int          `json:"skipped"`
	Failed  int          `json:"failed"`
	Results []BulkResult `json:"results"`
}

// NewBulkResponse calcula los totales a partir de los resultados.
func NewBulkResponse(results []BulkResult) BulkCommandResponse {
	sent, skipped := 0, 0
	for _, r := range results {
		switch r.Status {
		case "SENT":
			sent++
		case "SKIPPED":
			skipped++
		}
	}
	return BulkCommandResponse{Sent: sent, Skipped: skipped, Failed: len(results) - sent - skipped, Results: results}
}
