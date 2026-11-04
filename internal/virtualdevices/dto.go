package virtualdevices

import (
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/validation"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/readings"
)

// VirtualDeviceRequest indica cómo se simula un cultivo virtual.
type VirtualDeviceRequest struct {
	Mode            *Mode          `json:"mode"`
	Manual          *ManualValues  `json:"manual"`
	Location        *LocationInput `json:"location"`
	IntervalSeconds *int           `json:"intervalSeconds"`
}

// ManualValues son los medidores del modo manual, en la escala del dispositivo.
type ManualValues struct {
	Temperature  *float64 `json:"temperature"`
	Humidity     *float64 `json:"humidity"`
	Brightness   *float64 `json:"brightness"`
	Ph           *float64 `json:"ph"`
	Tds          *float64 `json:"tds"`
	Atmosphere   *float64 `json:"atmosphere"`
	SoilMoisture *float64 `json:"soilMoisture"`
}

// LocationInput es la ubicación que llega en la solicitud.
type LocationInput struct {
	Name      string   `json:"name"`
	Latitude  *float64 `json:"latitude"`
	Longitude *float64 `json:"longitude"`
}

// Validate aplica las mismas reglas y mensajes que la versión en Java.
func (r VirtualDeviceRequest) Validate() validation.Fields {
	v := validation.New().Required("mode", r.Mode != nil, "Choose the simulation mode")
	if r.IntervalSeconds != nil {
		if *r.IntervalSeconds < 10 {
			v.Add("intervalSeconds", "Minimum interval is 10 seconds")
		} else if *r.IntervalSeconds > 300 {
			v.Add("intervalSeconds", "Maximum interval is 300 seconds")
		}
	}
	if m := r.Manual; m != nil {
		v.DecimalRange("manual.temperature", m.Temperature, -20, 60, "Value out of range").
			DecimalRange("manual.humidity", m.Humidity, 0, 100, "Value out of range").
			DecimalRange("manual.brightness", m.Brightness, 0, 2000, "Value out of range").
			DecimalRange("manual.ph", m.Ph, 0, 14, "Value out of range").
			DecimalRange("manual.tds", m.Tds, 0, 3000, "Value out of range").
			DecimalRange("manual.atmosphere", m.Atmosphere, 300, 1100, "Value out of range").
			DecimalRange("manual.soilMoisture", m.SoilMoisture, 0, 100, "Value out of range")
	}
	if loc := r.Location; loc != nil {
		v.NotBlank("location.name", loc.Name, "Location name is required").
			MaxSize("location.name", loc.Name, 120, "Location name is too long").
			Required("location.latitude", loc.Latitude != nil, "Latitude is required").
			Required("location.longitude", loc.Longitude != nil, "Longitude is required").
			DecimalRange("location.latitude", loc.Latitude, -90, 90, "Latitude is out of range").
			DecimalRange("location.longitude", loc.Longitude, -180, 180, "Longitude is out of range")
	}
	return v.Result()
}

// SimulatorPot es el estado de un cultivo virtual tal como lo informa el simulador.
type SimulatorPot struct {
	CropID          string             `json:"cropId"`
	CropType        string             `json:"cropType"`
	Mode            string             `json:"mode"`
	Managed         bool               `json:"managed"`
	Connected       bool               `json:"connected"`
	IntervalSeconds float64            `json:"intervalSeconds"`
	LastReading     map[string]float64 `json:"lastReading"`
	LastPublishedAt *time.Time         `json:"lastPublishedAt"`
	Manual          map[string]float64 `json:"manual"`
	Location        *SimLocation       `json:"location"`
	Weather         *Weather           `json:"weather"`
	WeatherError    string             `json:"weatherError"`
	ActiveActuators []ActiveActuator   `json:"activeActuators"`
	LastCommand     *LastCommand       `json:"lastCommand"`
}

// SimLocation es la ubicación informada por el simulador.
type SimLocation struct {
	Name      string  `json:"name"`
	Latitude  float64 `json:"latitude"`
	Longitude float64 `json:"longitude"`
}

// Weather es el clima actual del lugar.
// condition: CLEAR, MOSTLY_CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW o STORM.
type Weather struct {
	Temperature   float64 `json:"temperature"`
	Humidity      float64 `json:"humidity"`
	CloudCover    float64 `json:"cloudCover"`
	Radiation     float64 `json:"radiation"`
	Precipitation float64 `json:"precipitation"`
	Pressure      float64 `json:"pressure"`
	WindSpeed     float64 `json:"windSpeed"`
	IsDay         bool    `json:"isDay"`
	Code          int     `json:"code"`
	Condition     string  `json:"condition"`
	Label         string  `json:"label"`
	ObservedAt    string  `json:"observedAt"`
}

// ActiveActuator es un actuador encendido en la simulación.
type ActiveActuator struct {
	Actuator string     `json:"actuator"`
	Until    *time.Time `json:"until"`
}

// LastCommand es el último comando que procesó la simulación.
type LastCommand struct {
	ID      string     `json:"id"`
	Status  string     `json:"status"`
	Message string     `json:"message"`
	At      *time.Time `json:"at"`
}

// PlaceResponse es un lugar sugerido para el modo clima.
type PlaceResponse struct {
	Name      string  `json:"name"`
	Latitude  float64 `json:"latitude"`
	Longitude float64 `json:"longitude"`
	Country   string  `json:"country"`
	Region    string  `json:"region"`
}

// SimulatorPotRequest es lo que el simulador necesita para actuar como el
// dispositivo del cultivo, clave incluida. Omite los campos nulos.
type SimulatorPotRequest struct {
	Key             string             `json:"key,omitempty"`
	CropType        string             `json:"cropType,omitempty"`
	Mode            string             `json:"mode,omitempty"`
	Manual          map[string]float64 `json:"manual,omitempty"`
	Location        *SimLocation       `json:"location,omitempty"`
	IntervalSeconds int                `json:"intervalSeconds"`
	Setting         string             `json:"setting,omitempty"`
	Exposure        string             `json:"exposure,omitempty"`
}

// VirtualDeviceResponse es la vista pública de la simulación de un cultivo.
type VirtualDeviceResponse struct {
	CropID          string             `json:"cropId"`
	Available       bool               `json:"available"`
	Active          bool               `json:"active"`
	Running         bool               `json:"running"`
	Mode            *Mode              `json:"mode,omitempty"`
	Manual          *readings.Measures `json:"manual,omitempty"`
	Location        *Location          `json:"location,omitempty"`
	IntervalSeconds *int               `json:"intervalSeconds,omitempty"`
	Connected       bool               `json:"connected"`
	LastReading     map[string]float64 `json:"lastReading,omitempty"`
	LastPublishedAt *time.Time         `json:"lastPublishedAt,omitempty"`
	Weather         *Weather           `json:"weather,omitempty"`
	WeatherError    string             `json:"weatherError,omitempty"`
	ActiveActuators []ActiveActuator   `json:"activeActuators"`
	LastCommand     *LastCommand       `json:"lastCommand,omitempty"`
	UpdatedAt       *time.Time         `json:"updatedAt,omitempty"`
}
