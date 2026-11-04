// Package geo contiene el lugar de un cultivo (bajo techo o al aire libre, la
// exposición al sol y la ubicación). Es un paquete hoja que comparten los
// dominios de cultivos y de dispositivos virtuales sin crear ciclos de importación.
package geo

import (
	"encoding/json"
	"fmt"
)

// Setting indica si el cultivo está bajo techo o al aire libre.
type Setting string

const (
	Indoor  Setting = "INDOOR"
	Outdoor Setting = "OUTDOOR"
)

// UnmarshalJSON rechaza un valor desconocido (mismo 400 que los enums de Java).
func (s *Setting) UnmarshalJSON(b []byte) error {
	var v string
	if err := json.Unmarshal(b, &v); err != nil {
		return err
	}
	switch Setting(v) {
	case Indoor, Outdoor:
		*s = Setting(v)
		return nil
	default:
		return fmt.Errorf("invalid location setting: %q", v)
	}
}

// Exposure indica cuánto sol recibe el cultivo.
type Exposure string

const (
	FullSun    Exposure = "FULL_SUN"
	PartialSun Exposure = "PARTIAL_SUN"
	Shade      Exposure = "SHADE"
)

// UnmarshalJSON rechaza un valor desconocido.
func (e *Exposure) UnmarshalJSON(b []byte) error {
	var v string
	if err := json.Unmarshal(b, &v); err != nil {
		return err
	}
	switch Exposure(v) {
	case FullSun, PartialSun, Shade:
		*e = Exposure(v)
		return nil
	default:
		return fmt.Errorf("invalid exposure: %q", v)
	}
}

// Location es un lugar con nombre y coordenadas.
type Location struct {
	Name      string  `json:"name" bson:"name"`
	Latitude  float64 `json:"latitude" bson:"latitude"`
	Longitude float64 `json:"longitude" bson:"longitude"`
}

// Placement es el lugar del cultivo; todas sus partes son opcionales.
type Placement struct {
	Setting  *Setting  `json:"setting,omitempty" bson:"setting,omitempty"`
	Exposure *Exposure `json:"exposure,omitempty" bson:"exposure,omitempty"`
	Location *Location `json:"location,omitempty" bson:"location,omitempty"`
}

// WithLocation devuelve una copia con otra ubicación, conservando setting y exposure.
func (p Placement) WithLocation(loc *Location) Placement {
	p.Location = loc
	return p
}

// Equal compara dos lugares (nil incluido) por valor, como los records de Java.
func Equal(a, b *Placement) bool {
	if a == nil || b == nil {
		return a == b
	}
	if !ptrEq(stringOf(a.Setting), stringOf(b.Setting)) || !ptrEq(stringOf(a.Exposure), stringOf(b.Exposure)) {
		return false
	}
	return locationEqual(a.Location, b.Location)
}

func locationEqual(a, b *Location) bool {
	if a == nil || b == nil {
		return a == b
	}
	return *a == *b
}

func stringOf[T ~string](v *T) *string {
	if v == nil {
		return nil
	}
	s := string(*v)
	return &s
}

func ptrEq(a, b *string) bool {
	if a == nil || b == nil {
		return a == b
	}
	return *a == *b
}
