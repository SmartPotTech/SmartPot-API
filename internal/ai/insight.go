package ai

import (
	"context"
	"encoding/json"
	"fmt"
	"math"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/actuators"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/cache"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/crops"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/geo"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/readings"
	"golang.org/x/sync/singleflight"
)

// historyWindowFactor: cuántas lecturas se revisan por cada punto enviado.
const historyWindowFactor = 8

// InsightService consulta al servicio de IA y guarda el índice de salud.
type InsightService struct {
	client    *Client
	crops     *crops.Service
	readings  *readings.Service
	actuators *actuators.Service
	weather   *crops.WeatherService
	cache     cache.Cache
	flight    singleflight.Group
	clock     func() time.Time
	history   int
	timezone  *time.Location
}

// NewInsightService crea el servicio de evaluación con soporte de caché inteligente y singleflight.
func NewInsightService(client *Client, cropsSvc *crops.Service, readingsSvc *readings.Service,
	actuatorsSvc *actuators.Service, weather *crops.WeatherService, store cache.Cache, clock func() time.Time,
	historySize int, timezone *time.Location) *InsightService {
	return &InsightService{client: client, crops: cropsSvc, readings: readingsSvc, actuators: actuatorsSvc,
		weather: weather, cache: store, clock: clock, history: historySize, timezone: timezone}
}

// ForOwner evalúa el cultivo del dueño sobre su última lectura.
func (s *InsightService) ForOwner(ctx context.Context, ownerID, cropID string) (*InsightResponse, error) {
	return s.forOwner(ctx, ownerID, cropID, true)
}

// ForOwnerReadOnly evalúa sin persistir el índice de salud. La usa un token de
// conexión con alcance READ: leer un diagnóstico no debe escribir en la base.
func (s *InsightService) ForOwnerReadOnly(ctx context.Context, ownerID, cropID string) (*InsightResponse, error) {
	return s.forOwner(ctx, ownerID, cropID, false)
}

func (s *InsightService) forOwner(ctx context.Context, ownerID, cropID string, persist bool) (*InsightResponse, error) {
	crop, err := s.crops.GetOwned(ctx, ownerID, cropID)
	if err != nil {
		return nil, err
	}
	latest, err := s.readings.Latest(ctx, cropID)
	if err != nil {
		return nil, err
	}
	if latest == nil {
		return nil, apperr.NotFound("No readings to analyze yet for this crop")
	}
	return s.evaluate(ctx, crop, *latest, persist)
}

// Evaluate consulta a la IA y guarda el índice de salud en el cultivo.
func (s *InsightService) Evaluate(ctx context.Context, crop *crops.Crop, latest readings.Reading) (*InsightResponse, error) {
	return s.evaluate(ctx, crop, latest, true)
}

// evaluate consulta a la IA con caché inteligente de Valkey y singleflight. La
// clave del vuelo incluye la versión de la lectura y del cultivo: una petición
// posterior a un PUT /crops/{id} no hereda el diagnóstico viejo (R5b).
func (s *InsightService) evaluate(ctx context.Context, crop *crops.Crop, latest readings.Reading, persist bool) (*InsightResponse, error) {
	cacheKey := insightCacheKey(crop, latest)
	if !persist {
		// La respuesta de una conexión READ no comparte caché con la que
		// persiste el índice de salud del cultivo.
		cacheKey += ":read-only"
	}
	if s.cache != nil {
		if raw, ok := s.cache.Get(cacheKey); ok {
			var cached InsightResponse
			if err := json.Unmarshal([]byte(raw), &cached); err == nil {
				return &cached, nil
			}
		}
	}

	flightKey := insightFlightKey(cacheKey)
	v, err, _ := s.flight.Do(flightKey, func() (any, error) {
		if s.cache != nil {
			if raw, ok := s.cache.Get(cacheKey); ok {
				var cached InsightResponse
				if err := json.Unmarshal([]byte(raw), &cached); err == nil {
					return &cached, nil
				}
			}
		}

		window, err := s.readings.Recent(ctx, crop.HexID(), s.history*historyWindowFactor)
		if err != nil {
			return nil, err
		}
		reverse(window)
		history := make([]HistoryPoint, 0, s.history)
		for _, r := range sample(window, s.history) {
			history = append(history, historyPointOf(r))
		}
		actuatorsList, err := s.actuators.ListForCrop(ctx, crop.HexID())
		if err != nil {
			return nil, err
		}
		types := make([]string, 0, len(actuatorsList))
		for i := range actuatorsList {
			types = append(types, string(actuatorsList[i].Type))
		}

		now := s.clock()
		measuredAt := latest.MeasuredAt
		if measuredAt.IsZero() {
			measuredAt = now
		}
		localHour := measuredAt.In(s.timezone).Hour()
		req := InsightRequest{
			CropType:  string(crop.Type),
			Measures:  latest.Measures,
			History:   history,
			Actuators: types,
			LocalHour: &localHour,
			Placement: place(crop.Placement),
			Weather:   s.outside(ctx, crop),
		}
		resp, err := s.client.Insights(ctx, req)
		if err != nil {
			return nil, err
		}
		resp.EvaluatedAt = now
		if persist && resp.Health != nil {
			s.crops.UpdateHealth(ctx, crop.HexID(), crops.CropHealth{
				Index: resp.Health.Index, Level: resp.Health.Level, Label: resp.Health.Label, EvaluatedAt: now,
			})
		}
		if s.cache != nil {
			if b, err := json.Marshal(resp); err == nil {
				s.cache.Put(cacheKey, string(b), 60*time.Second)
			}
		}
		return resp, nil
	})
	if err != nil {
		return nil, err
	}
	return v.(*InsightResponse), nil
}

// insightCacheKey incluye la última modificación del cultivo y la lectura: si
// cambia su lugar, especie o forma, el diagnóstico anterior deja de valer.
func insightCacheKey(crop *crops.Crop, latest readings.Reading) string {
	return fmt.Sprintf("insights:%s:%s:%d:%d", crop.HexID(), latest.ID.Hex(), latest.MeasuredAt.Unix(),
		crop.UpdatedAt.UnixNano())
}

// insightFlightKey versiona el singleflight con la misma llave de caché
// (lectura, cultivo y versión), cerrando la carrera R5b.
func insightFlightKey(cacheKey string) string {
	return "flight:" + cacheKey
}

func (s *InsightService) outside(ctx context.Context, crop *crops.Crop) *Outside {
	w, ok := s.weather.Current(ctx, crop)
	if !ok {
		return nil
	}
	return &Outside{
		Temperature:   w.Temperature,
		Humidity:      w.Humidity,
		Precipitation: w.Precipitation,
		Radiation:     w.Radiation,
		IsDay:         w.IsDay,
		Condition:     w.Condition,
	}
}

func place(p *geo.Placement) *Place {
	if p == nil || (p.Setting == nil && p.Exposure == nil) {
		return nil
	}
	out := &Place{}
	if p.Setting != nil {
		out.Setting = string(*p.Setting)
	}
	if p.Exposure != nil {
		out.Exposure = string(*p.Exposure)
	}
	return out
}

// sample toma hasta size elementos repartidos en todo el periodo, incluidos el
// primero y el último, para que el pronóstico vea la tendencia de varias horas.
func sample[T any](items []T, size int) []T {
	if len(items) <= size || size < 2 {
		return items
	}
	out := make([]T, 0, size)
	for i := 0; i < size; i++ {
		idx := int(math.Round(float64(i) * float64(len(items)-1) / float64(size-1)))
		out = append(out, items[idx])
	}
	return out
}

func reverse(r []readings.Reading) {
	for i, j := 0, len(r)-1; i < j; i, j = i+1, j-1 {
		r[i], r[j] = r[j], r[i]
	}
}
