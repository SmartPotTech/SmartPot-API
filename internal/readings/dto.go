package readings

import (
	"fmt"
	"math"
	"time"
)

// Metrics son las variables en el orden en que se exportan y resumen.
var Metrics = []string{"temperature", "humidity", "brightness", "ph", "tds", "atmosphere", "soilMoisture"}

// ReadingResponse es la vista pública de una lectura.
type ReadingResponse struct {
	ID         string    `json:"id"`
	CropID     string    `json:"cropId"`
	MeasuredAt time.Time `json:"measuredAt"`
	Measures   Measures  `json:"measures"`
	Source     string    `json:"source,omitempty"`
}

// ToResponse mapea la lectura a su vista pública; nil devuelve nil.
func ToResponse(r *Reading) *ReadingResponse {
	if r == nil {
		return nil
	}
	return &ReadingResponse{
		ID:         r.ID.Hex(),
		CropID:     r.CropID.Hex(),
		MeasuredAt: r.MeasuredAt,
		Measures:   r.Measures,
		Source:     string(r.Source),
	}
}

// MetricSummary es el mínimo, promedio y máximo de una variable.
type MetricSummary struct {
	Min *float64 `json:"min,omitempty"`
	Avg *float64 `json:"avg,omitempty"`
	Max *float64 `json:"max,omitempty"`
}

// ReadingSummaryResponse resume la ventana pedida.
type ReadingSummaryResponse struct {
	From    time.Time                `json:"from"`
	To      time.Time                `json:"to"`
	Count   int64                    `json:"count"`
	Metrics map[string]MetricSummary `json:"metrics"`
}

// MeasuresRequest es una lectura manual.
type MeasuresRequest struct {
	Temperature  *float64 `json:"temperature"`
	Humidity     *float64 `json:"humidity"`
	Brightness   *float64 `json:"brightness"`
	Ph           *float64 `json:"ph"`
	Tds          *float64 `json:"tds"`
	Atmosphere   *float64 `json:"atmosphere"`
	SoilMoisture *float64 `json:"soilMoisture"`
}

// ToMeasures convierte la solicitud en valores.
func (r MeasuresRequest) ToMeasures() Measures {
	return Measures{
		Temperature:  r.Temperature,
		Humidity:     r.Humidity,
		Brightness:   r.Brightness,
		Ph:           r.Ph,
		Tds:          r.Tds,
		Atmosphere:   r.Atmosphere,
		SoilMoisture: r.SoilMoisture,
	}
}

// ValidateRanges comprueba los rangos físicos admitidos. Un valor fuera de rango
// suele ser un sensor dañado o mal calibrado. Devuelve un error con mensaje en inglés.
func ValidateRanges(m Measures) error {
	if m.IsEmpty() {
		return fmt.Errorf("Reading does not contain any measurement value")
	}
	checks := []struct {
		name     string
		value    *float64
		min, max float64
	}{
		{"temperature", m.Temperature, -20, 60},
		{"humidity", m.Humidity, 0, 100},
		{"brightness", m.Brightness, 0, 200000},
		{"pH", m.Ph, 0, 14},
		{"TDS", m.Tds, 0, 10000},
		{"atmosphere", m.Atmosphere, 300, 1100},
		{"soil moisture", m.SoilMoisture, 0, 100},
	}
	for _, c := range checks {
		if c.value == nil {
			continue
		}
		v := *c.value
		if math.IsNaN(v) || math.IsInf(v, 0) || v < c.min || v > c.max {
			return fmt.Errorf("Value for %s (%s) is out of range (%s to %s)",
				c.name, trimFloat(v), trimFloat(c.min), trimFloat(c.max))
		}
	}
	return nil
}

func trimFloat(v float64) string {
	return fmt.Sprintf("%g", v)
}
