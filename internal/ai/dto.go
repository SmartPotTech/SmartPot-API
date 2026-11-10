// Package ai integra el asistente de IA: cliente HTTP del servicio, evaluación
// de salud (insights), agente de automatización reactivo y aprendizaje continuo.
package ai

import (
	"encoding/json"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/readings"
)

// InsightRequest es lo que recibe el servicio de IA. Omite los campos nulos.
type InsightRequest struct {
	CropType     string            `json:"cropType,omitempty"`
	Measures     readings.Measures `json:"measures"`
	History      []HistoryPoint    `json:"history,omitempty"`
	Actuators    []string          `json:"actuators,omitempty"`
	LocalHour    *int              `json:"localHour,omitempty"`
	Placement    *Place            `json:"placement,omitempty"`
	Weather      *Outside          `json:"weather,omitempty"`
	Capabilities json.RawMessage   `json:"capabilities,omitempty"`
}

// Place es el lugar del cultivo (setting/exposure), cualquiera puede faltar.
type Place struct {
	Setting  string `json:"setting,omitempty"`
	Exposure string `json:"exposure,omitempty"`
}

// Outside es el clima actual del lugar del cultivo.
type Outside struct {
	Temperature   float64 `json:"temperature"`
	Humidity      float64 `json:"humidity"`
	Precipitation float64 `json:"precipitation"`
	Radiation     float64 `json:"radiation"`
	IsDay         bool    `json:"isDay"`
	Condition     string  `json:"condition,omitempty"`
}

// HistoryPoint es una lectura del historial con su hora.
type HistoryPoint struct {
	MeasuredAt   time.Time `json:"measuredAt"`
	Temperature  *float64  `json:"temperature,omitempty"`
	Humidity     *float64  `json:"humidity,omitempty"`
	Brightness   *float64  `json:"brightness,omitempty"`
	Ph           *float64  `json:"ph,omitempty"`
	Tds          *float64  `json:"tds,omitempty"`
	Atmosphere   *float64  `json:"atmosphere,omitempty"`
	SoilMoisture *float64  `json:"soilMoisture,omitempty"`
}

func historyPointOf(r readings.Reading) HistoryPoint {
	m := r.Measures
	return HistoryPoint{
		MeasuredAt:   r.MeasuredAt,
		Temperature:  m.Temperature,
		Humidity:     m.Humidity,
		Brightness:   m.Brightness,
		Ph:           m.Ph,
		Tds:          m.Tds,
		Atmosphere:   m.Atmosphere,
		SoilMoisture: m.SoilMoisture,
	}
}

// InsightResponse es la evaluación del asistente.
type InsightResponse struct {
	CropType    string           `json:"cropType,omitempty"`
	Health      *Health          `json:"health,omitempty"`
	Diagnosis   []Diagnosis      `json:"diagnosis,omitempty"`
	Conclusions []Conclusion     `json:"conclusions,omitempty"`
	Predictions []Prediction     `json:"predictions,omitempty"`
	Actions     []Action         `json:"actions,omitempty"`
	Forecasts   []Forecast       `json:"forecasts,omitempty"`
	Learning    *Learning        `json:"learning,omitempty"`
	Placement   *PlacementAdvice `json:"placement,omitempty"`
	Summary     string           `json:"summary,omitempty"`
	EvaluatedAt time.Time        `json:"evaluatedAt"`
}

// Health es el índice difuso de salud con su desglose por variable.
type Health struct {
	Index       float64            `json:"index"`
	Level       string             `json:"level,omitempty"`
	Label       string             `json:"label,omitempty"`
	ByParameter map[string]float64 `json:"byParameter,omitempty"`
}

// Diagnosis es el diagnóstico de una variable del sistema experto.
type Diagnosis struct {
	Parameter      string   `json:"parameter,omitempty"`
	Value          *float64 `json:"value,omitempty"`
	Status         string   `json:"status,omitempty"`
	Severity       string   `json:"severity,omitempty"`
	Message        string   `json:"message,omitempty"`
	Recommendation string   `json:"recommendation,omitempty"`
}

// Conclusion es una conclusión encadenada del sistema experto.
type Conclusion struct {
	Rule      string  `json:"rule,omitempty"`
	Title     string  `json:"title,omitempty"`
	Message   string  `json:"message,omitempty"`
	Certainty float64 `json:"certainty"`
}

// Prediction es una predicción de los modelos.
type Prediction struct {
	Name        string  `json:"name,omitempty"`
	Label       string  `json:"label,omitempty"`
	Probability float64 `json:"probability"`
	Model       string  `json:"model,omitempty"`
}

// Action es una acción propuesta por el agente.
type Action struct {
	Actuator        string `json:"actuator,omitempty"`
	Action          string `json:"action,omitempty"`
	DurationSeconds *int   `json:"durationSeconds,omitempty"`
	Reason          string `json:"reason,omitempty"`
}

// Forecast es la tendencia pronosticada de una variable.
type Forecast struct {
	Parameter    string   `json:"parameter,omitempty"`
	Current      float64  `json:"current"`
	SlopePerHour float64  `json:"slopePerHour"`
	ExpectedIn3h float64  `json:"expectedIn3h"`
	Trend        string   `json:"trend,omitempty"`
	HoursToLimit *float64 `json:"hoursToLimit,omitempty"`
	Limit        string   `json:"limit,omitempty"`
	Confidence   float64  `json:"confidence"`
	Message      string   `json:"message,omitempty"`
}

// Learning es lo aprendido de las lecturas reales de la especie.
type Learning struct {
	Source      string              `json:"source,omitempty"`
	Readings    int                 `json:"readings"`
	TrainedAt   *time.Time          `json:"trainedAt,omitempty"`
	Message     string              `json:"message,omitempty"`
	Predictions []LearnedPrediction `json:"predictions,omitempty"`
	Moisture    *LearnedMoisture    `json:"moisture,omitempty"`
	State       *OperatingState     `json:"state,omitempty"`
	Anomaly     *LearnedAnomaly     `json:"anomaly,omitempty"`
}

// LearnedPrediction es una predicción de un modelo entrenado.
type LearnedPrediction struct {
	Name        string   `json:"name,omitempty"`
	Label       string   `json:"label,omitempty"`
	Probability float64  `json:"probability"`
	Model       string   `json:"model,omitempty"`
	Metric      string   `json:"metric,omitempty"`
	Score       *float64 `json:"score,omitempty"`
}

// LearnedMoisture es la humedad del sustrato esperada en 1 h.
type LearnedMoisture struct {
	ExpectedIn1h float64  `json:"expectedIn1h"`
	Model        string   `json:"model,omitempty"`
	Mae          *float64 `json:"mae,omitempty"`
}

// OperatingState es el estado de operación (K-Means).
type OperatingState struct {
	Label       string  `json:"label,omitempty"`
	Description string  `json:"description,omitempty"`
	Share       float64 `json:"share"`
}

// LearnedAnomaly es el detector de atípicos (Isolation Forest).
type LearnedAnomaly struct {
	Score   float64 `json:"score"`
	Unusual bool    `json:"unusual"`
}

// PlacementAdvice es el consejo de lugar de la IA.
type PlacementAdvice struct {
	Level         string `json:"level,omitempty"`
	Title         string `json:"title,omitempty"`
	Message       string `json:"message,omitempty"`
	LightNeed     string `json:"lightNeed,omitempty"`
	IdealSetting  string `json:"idealSetting,omitempty"`
	IdealExposure string `json:"idealExposure,omitempty"`
}

// FleetRequest es el análisis de todos los cultivos de una cuenta.
type FleetRequest struct {
	Crops     []FleetCrop `json:"crops"`
	LocalHour *int        `json:"localHour,omitempty"`
}

// FleetCrop es un cultivo con su última lectura y sus actuadores.
type FleetCrop struct {
	ID        string             `json:"id"`
	Name      string             `json:"name"`
	CropType  string             `json:"cropType"`
	Measures  *readings.Measures `json:"measures,omitempty"`
	Actuators []string           `json:"actuators"`
}

// FleetResponse es el análisis de flota. Las listas se serializan siempre (como
// [] cuando van vacías), igual que la versión en Java.
type FleetResponse struct {
	AverageHealth *float64          `json:"averageHealth,omitempty"`
	Crops         []FleetCropResult `json:"crops"`
	SharedIssues  []SharedIssue     `json:"sharedIssues"`
	Groups        []Group           `json:"groups"`
	Actions       []FleetAction     `json:"actions"`
	Summary       string            `json:"summary,omitempty"`
}

// FleetCropResult es un cultivo en el ranking de flota.
type FleetCropResult struct {
	ID       string   `json:"id"`
	Name     string   `json:"name"`
	CropType string   `json:"cropType,omitempty"`
	Rank     *int     `json:"rank,omitempty"`
	Health   *Health  `json:"health,omitempty"`
	Issues   []string `json:"issues,omitempty"`
}

// SharedIssue es un problema que comparten varios cultivos.
type SharedIssue struct {
	Parameter string   `json:"parameter,omitempty"`
	Status    string   `json:"status,omitempty"`
	CropIds   []string `json:"cropIds,omitempty"`
	Share     float64  `json:"share"`
	Message   string   `json:"message,omitempty"`
}

// Group es un grupo de cultivos con condiciones similares.
type Group struct {
	Label       string   `json:"label,omitempty"`
	CropIds     []string `json:"cropIds,omitempty"`
	Description string   `json:"description,omitempty"`
}

// FleetAction es una acción en bloque sugerida por la IA.
type FleetAction struct {
	Actuator        string   `json:"actuator,omitempty"`
	Action          string   `json:"action,omitempty"`
	DurationSeconds *int     `json:"durationSeconds,omitempty"`
	CropIds         []string `json:"cropIds,omitempty"`
	Reason          string   `json:"reason,omitempty"`
}

// LearningReading es una lectura real que la IA guarda para aprender.
type LearningReading struct {
	CropID     string            `json:"cropId"`
	CropType   string            `json:"cropType"`
	MeasuredAt time.Time         `json:"measuredAt"`
	LocalHour  *int              `json:"localHour,omitempty"`
	Measures   readings.Measures `json:"measures"`
}

// LearningBatch es un lote de lecturas para el aprendizaje.
type LearningBatch struct {
	Readings []LearningReading `json:"readings"`
}
