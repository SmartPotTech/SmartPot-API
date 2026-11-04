package overview

import (
	"context"
	"encoding/json"
	"fmt"
	"math"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/actuators"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/ai"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/cache"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/commands"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/crops"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/notifications"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/readings"
)

const attentionHealth = 70.0

var metricsSet = map[string]bool{
	"temperature": true, "humidity": true, "brightness": true, "ph": true,
	"tds": true, "soilMoisture": true, "atmosphere": true,
}

// Service arma el panel general de la cuenta.
type Service struct {
	crops         *crops.Service
	readings      *readings.Service
	actuators     *actuators.Service
	commands      *commands.Service
	notifications *notifications.Service
	ai            *ai.Client
	cache         cache.Cache
	clock         func() time.Time
	timezone      *time.Location
}

// NewService crea el servicio del panel general con caché inteligente de series.
func NewService(cropsSvc *crops.Service, readingsSvc *readings.Service, actuatorsSvc *actuators.Service,
	commandsSvc *commands.Service, notificationsSvc *notifications.Service, aiClient *ai.Client,
	store cache.Cache, clock func() time.Time, timezone *time.Location) *Service {
	return &Service{crops: cropsSvc, readings: readingsSvc, actuators: actuatorsSvc, commands: commandsSvc,
		notifications: notificationsSvc, ai: aiClient, cache: store, clock: clock, timezone: timezone}
}

// Overview devuelve los totales de la cuenta y cada cultivo con su última lectura.
func (s *Service) Overview(ctx context.Context, ownerID string) (OverviewResponse, error) {
	cropsList, err := s.crops.List(ctx, ownerID)
	if err != nil {
		return OverviewResponse{}, err
	}
	responses := make([]crops.CropResponse, 0, len(cropsList))
	cropIDs := make([]string, 0, len(cropsList))
	online, automated, attention, healthCount := 0, 0, 0, 0
	var healthSum float64
	for i := range cropsList {
		crop := &cropsList[i]
		latest, err := s.readings.Latest(ctx, crop.HexID())
		if err != nil {
			return OverviewResponse{}, err
		}
		responses = append(responses, crops.ToResponse(crop, latest))
		cropIDs = append(cropIDs, crop.HexID())
		isOnline := crop.Device != nil && crop.Device.Online
		if isOnline {
			online++
		}
		if crop.AutomationEnabled {
			automated++
		}
		if crop.Health != nil {
			healthSum += crop.Health.Index
			healthCount++
		}
		if !isOnline || (crop.Health != nil && crop.Health.Index < attentionHealth) {
			attention++
		}
	}
	var average *float64
	if healthCount > 0 {
		v := math.Round(healthSum/float64(healthCount)*10) / 10
		average = &v
	}
	commandsCount, err := s.commands.CountSince(ctx, cropIDs, s.clock().Add(-24*time.Hour))
	if err != nil {
		return OverviewResponse{}, err
	}
	unread, err := s.notifications.UnreadCount(ctx, ownerID)
	if err != nil {
		return OverviewResponse{}, err
	}
	return OverviewResponse{
		Totals: Totals{
			Crops:           len(cropsList),
			Online:          online,
			Automated:       automated,
			AverageHealth:   average,
			NeedsAttention:  attention,
			UnreadAlerts:    unread,
			CommandsLast24h: commandsCount,
		},
		Crops: responses,
	}, nil
}

// Series compara una variable entre cultivos con promedios por intervalo.
// Utiliza caché inteligente de Valkey con fallback local para latencia sub-milisegundo.
func (s *Service) Series(ctx context.Context, ownerID, metric string, hours int) (MetricSeriesResponse, error) {
	if !metricsSet[metric] {
		return MetricSeriesResponse{}, apperr.BadRequest("Variable desconocida: " + metric)
	}
	if hours < 1 || hours > 168 {
		return MetricSeriesResponse{}, apperr.BadRequest("Period must be between 1 and 168 hours")
	}

	cacheKey := fmt.Sprintf("series:%s:%s:%d", ownerID, metric, hours)
	if s.cache != nil {
		if raw, ok := s.cache.Get(cacheKey); ok {
			var cached MetricSeriesResponse
			if err := json.Unmarshal([]byte(raw), &cached); err == nil {
				return cached, nil
			}
		}
	}

	cropsList, err := s.crops.List(ctx, ownerID)
	if err != nil {
		return MetricSeriesResponse{}, err
	}
	bucket := bucketMinutes(hours)
	cropIDs := make([]string, 0, len(cropsList))
	for i := range cropsList {
		cropIDs = append(cropIDs, cropsList[i].HexID())
	}
	buckets, err := s.readings.SeriesAverages(ctx, cropIDs, metric, s.clock().Add(-time.Duration(hours)*time.Hour), bucket)
	if err != nil {
		return MetricSeriesResponse{}, err
	}
	pointsByID := make(map[string][]Point, len(cropsList))
	for _, id := range cropIDs {
		pointsByID[id] = []Point{}
	}
	for _, b := range buckets {
		pointsByID[b.CropID] = append(pointsByID[b.CropID], Point{Time: b.Time, Value: math.Round(b.Value*100) / 100})
	}
	series := make([]CropSeries, 0, len(cropsList))
	for i := range cropsList {
		crop := &cropsList[i]
		series = append(series, CropSeries{CropID: crop.HexID(), Name: crop.Name, Type: string(crop.Type), Points: pointsByID[crop.HexID()]})
	}
	res := MetricSeriesResponse{Metric: metric, Hours: hours, BucketMinutes: bucket, Series: series}
	if s.cache != nil {
		if b, err := json.Marshal(res); err == nil {
			s.cache.Put(cacheKey, string(b), 60*time.Second)
		}
	}
	return res, nil
}

// Fleet pide a la IA el análisis de todos los cultivos de la cuenta.
func (s *Service) Fleet(ctx context.Context, ownerID string) (*ai.FleetResponse, error) {
	cropsList, err := s.crops.List(ctx, ownerID)
	if err != nil {
		return nil, err
	}
	if len(cropsList) == 0 {
		return &ai.FleetResponse{
			Crops:        []ai.FleetCropResult{},
			SharedIssues: []ai.SharedIssue{},
			Groups:       []ai.Group{},
			Actions:      []ai.FleetAction{},
			Summary:      "Create your first crop so the assistant can compare it.",
		}, nil
	}
	input := make([]ai.FleetCrop, 0, len(cropsList))
	for i := range cropsList {
		crop := &cropsList[i]
		latest, err := s.readings.Latest(ctx, crop.HexID())
		if err != nil {
			return nil, err
		}
		var measures *readings.Measures
		if latest != nil {
			measures = &latest.Measures
		}
		actuatorsList, err := s.actuators.ListForCrop(ctx, crop.HexID())
		if err != nil {
			return nil, err
		}
		types := make([]string, 0, len(actuatorsList))
		for j := range actuatorsList {
			types = append(types, string(actuatorsList[j].Type))
		}
		input = append(input, ai.FleetCrop{ID: crop.HexID(), Name: crop.Name, CropType: string(crop.Type),
			Measures: measures, Actuators: types})
	}
	hour := s.clock().In(s.timezone).Hour()
	return s.ai.Fleet(ctx, ai.FleetRequest{Crops: input, LocalHour: &hour})
}

func bucketMinutes(hours int) int {
	switch {
	case hours <= 6:
		return 10
	case hours <= 24:
		return 30
	case hours <= 72:
		return 90
	default:
		return 180
	}
}
