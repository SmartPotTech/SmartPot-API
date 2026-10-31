package ai

import (
	"math"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/readings"
)

// withinDeadband indica si la lectura casi no cambió respecto de la última evaluada: en ese caso el agente no vuelve
// a consultar a la IA (la respuesta sería la misma). Un sensor que aparece o desaparece siempre cuenta como cambio.
// Umbrales agronómicos: 0,1 °C, 0,4 % HR, 0,03 pH, 15 ppm, 0,5 % de sustrato, 0,5 hPa y 5 % de la luz (mín. 20 lux).
func withinDeadband(prev, cur readings.Measures) bool {
	return near(prev.Temperature, cur.Temperature, 0.1) &&
		near(prev.Humidity, cur.Humidity, 0.4) &&
		near(prev.Ph, cur.Ph, 0.03) &&
		near(prev.Tds, cur.Tds, 15) &&
		near(prev.SoilMoisture, cur.SoilMoisture, 0.5) &&
		near(prev.Atmosphere, cur.Atmosphere, 0.5) &&
		near(prev.Brightness, cur.Brightness, brightnessTolerance(prev.Brightness))
}

func near(a, b *float64, tolerance float64) bool {
	if a == nil || b == nil {
		return a == nil && b == nil
	}
	return math.Abs(*a-*b) < tolerance
}

func brightnessTolerance(prev *float64) float64 {
	if prev == nil {
		return 20
	}
	return math.Max(20, math.Abs(*prev)*0.05)
}
