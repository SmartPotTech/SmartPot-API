package crops

import (
	"encoding/json"
	"strings"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/geo"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/validation"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/readings"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/virtualdevices"
)

// CropRequest crea o edita un cultivo.
type CropRequest struct {
	Name      string                               `json:"name"`
	Type      *CropType                            `json:"type"`
	Kind      *CropKind                            `json:"kind"`
	Form      *CropForm                            `json:"form"`
	Virtual   *virtualdevices.VirtualDeviceRequest `json:"virtual"`
	Placement *PlacementRequest                    `json:"placement"`
}

// Validate aplica las mismas reglas y mensajes que la versión en Java.
func (r CropRequest) Validate() validation.Fields {
	v := validation.New().
		NotBlank("name", r.Name, "Crop name is required").
		Size("name", r.Name, 2, 60, "Crop name must be between 2 and 60 characters").
		Required("type", r.Type != nil, "Crop type is required")
	if r.Virtual != nil {
		for field, msg := range r.Virtual.Validate() {
			v.Add("virtual."+field, msg)
		}
	}
	if r.Placement != nil {
		r.Placement.validate(v)
	}
	return v.Result()
}

// PlacementRequest es el lugar del cultivo en la solicitud.
type PlacementRequest struct {
	Setting  *geo.Setting              `json:"setting"`
	Exposure *geo.Exposure             `json:"exposure"`
	Location *PlacementLocationRequest `json:"location"`
}

// PlacementLocationRequest es la ubicación en la solicitud.
type PlacementLocationRequest struct {
	Name      string   `json:"name"`
	Latitude  *float64 `json:"latitude"`
	Longitude *float64 `json:"longitude"`
}

// ToPlacement convierte la solicitud en el lugar del cultivo (nil si no hay).
func (r *PlacementRequest) ToPlacement() *geo.Placement {
	if r == nil {
		return nil
	}
	var loc *geo.Location
	if r.Location != nil {
		loc = &geo.Location{
			Name:      strings.TrimSpace(r.Location.Name),
			Latitude:  derefFloat(r.Location.Latitude),
			Longitude: derefFloat(r.Location.Longitude),
		}
	}
	return &geo.Placement{Setting: r.Setting, Exposure: r.Exposure, Location: loc}
}

func (r *PlacementRequest) validate(v *validation.Builder) {
	if r.Location == nil {
		return
	}
	v.NotBlank("placement.location.name", r.Location.Name, "Location name is required").
		MaxSize("placement.location.name", r.Location.Name, 120, "Location name is too long").
		Required("placement.location.latitude", r.Location.Latitude != nil, "Latitude is required").
		Required("placement.location.longitude", r.Location.Longitude != nil, "Longitude is required").
		DecimalRange("placement.location.latitude", r.Location.Latitude, -90, 90, "Latitude is out of range").
		DecimalRange("placement.location.longitude", r.Location.Longitude, -180, 180, "Longitude is out of range")
}

// AutomationRequest activa o desactiva el modo automático de un cultivo.
type AutomationRequest struct {
	Enabled *bool `json:"enabled"`
}

// Validate exige el campo enabled.
func (r AutomationRequest) Validate() validation.Fields {
	return validation.New().
		Required("enabled", r.Enabled != nil, "Indicate whether automation remains active").
		Result()
}

// BulkAutomationRequest activa o desactiva el modo automático en varios cultivos.
type BulkAutomationRequest struct {
	CropIds []string `json:"cropIds"`
	Enabled *bool    `json:"enabled"`
}

// Validate valida la operación en bloque.
func (r BulkAutomationRequest) Validate() validation.Fields {
	v := validation.New()
	if len(r.CropIds) > 20 {
		v.Add("cropIds", "Select at most 20 crops")
	}
	v.Required("enabled", r.Enabled != nil, "Indicate whether automation remains active")
	return v.Result()
}

// DeviceStatus es el estado del dispositivo dentro de la vista del cultivo.
type DeviceStatus struct {
	Online                bool            `json:"online"`
	LastSeenAt            *time.Time      `json:"lastSeenAt,omitempty"`
	KeyRotatedAt          *time.Time      `json:"keyRotatedAt,omitempty"`
	Capabilities          json.RawMessage `json:"capabilities,omitempty"`
	CapabilitiesUpdatedAt *time.Time      `json:"capabilitiesUpdatedAt,omitempty"`
	CapabilitiesStale     bool            `json:"capabilitiesStale"`
}

// CropResponse es la vista pública de un cultivo.
type CropResponse struct {
	ID                string                    `json:"id"`
	Name              string                    `json:"name"`
	Type              string                    `json:"type"`
	Kind              string                    `json:"kind"`
	Form              string                    `json:"form"`
	Placement         *geo.Placement            `json:"placement,omitempty"`
	AutomationEnabled bool                      `json:"automationEnabled"`
	Device            DeviceStatus              `json:"device"`
	Health            *CropHealth               `json:"health,omitempty"`
	LatestReading     *readings.ReadingResponse `json:"latestReading,omitempty"`
	CreatedAt         time.Time                 `json:"createdAt"`
}

// CropCreatedResponse es la respuesta al crear un cultivo.
type CropCreatedResponse struct {
	Crop   CropResponse               `json:"crop"`
	Device *DeviceCredentialsResponse `json:"device,omitempty"`
}

// DeviceTopics son los tópicos MQTT del dispositivo.
type DeviceTopics struct {
	Telemetry  string `json:"telemetry"`
	Commands   string `json:"commands"`
	CommandAck string `json:"commandAck"`
	Status     string `json:"status"`
}

// DeviceCredentialsResponse son los datos para configurar el dispositivo. La
// clave solo viaja al crear el cultivo o al rotarla (omitida si no).
type DeviceCredentialsResponse struct {
	Host         string       `json:"host"`
	Port         int          `json:"port"`
	TLS          bool         `json:"tls"`
	WebsocketURL string       `json:"websocketUrl"`
	Username     string       `json:"username"`
	Key          string       `json:"key,omitempty"`
	Topics       DeviceTopics `json:"topics"`
	KeyRotatedAt *time.Time   `json:"keyRotatedAt,omitempty"`
}

// ToResponse mapea el cultivo y su última lectura a la vista pública.
func ToResponse(crop *Crop, latest *readings.Reading) CropResponse {
	device := DeviceStatus{}
	if crop.Device != nil {
		device = DeviceStatus{
			Online:                crop.Device.Online,
			LastSeenAt:            crop.Device.LastSeenAt,
			KeyRotatedAt:          crop.Device.KeyRotatedAt,
			Capabilities:          crop.Device.Capabilities,
			CapabilitiesUpdatedAt: crop.Device.CapabilitiesUpdatedAt,
			CapabilitiesStale:     crop.Device.CapabilitiesStale,
		}
	}
	return CropResponse{
		ID:                crop.HexID(),
		Name:              crop.Name,
		Type:              string(crop.Type),
		Kind:              string(crop.KindOrDefault()),
		Form:              string(crop.FormOrDefault()),
		Placement:         crop.Placement,
		AutomationEnabled: crop.AutomationEnabled,
		Device:            device,
		Health:            crop.Health,
		LatestReading:     readings.ToResponse(latest),
		CreatedAt:         crop.CreatedAt,
	}
}

func derefFloat(v *float64) float64 {
	if v == nil {
		return 0
	}
	return *v
}
