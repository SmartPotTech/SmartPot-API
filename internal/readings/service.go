package readings

import (
	"context"
	"encoding/json"
	"log/slog"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/cache"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/events"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/objectid"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

const exportLimit = 50000

// La última lectura de cada cultivo vive en la caché compartida (Valkey) para que todas las réplicas de la API
// respondan lo mismo; expira sola si el cultivo deja de enviar.
const (
	latestKeyPrefix = "reading:latest:"
	latestTTL       = 10 * time.Minute
)

var batchSlicePool = sync.Pool{
	New: func() any {
		b := make([]Reading, 0, 100)
		return &b
	},
}

// Crops es lo que el servicio de lecturas necesita de los cultivos, sin importar
// el paquete crops (lo implementa CropService).
type Crops interface {
	Exists(ctx context.Context, cropID string) bool
	RequireOwned(ctx context.Context, ownerID, cropID string) error
	MarkDeviceSeen(ctx context.Context, cropID string, online *bool)
}

// Service implementa la lógica de lecturas con soporte para ingesta por lotes de alto rendimiento.
type Service struct {
	repo        Repository
	crops       Crops
	cache       cache.Cache
	bus         *events.Bus
	clock       func() time.Time
	minInterval time.Duration
	maxPageSize int

	batchQueue  chan Reading
	batchActive atomic.Bool
	batchWg     sync.WaitGroup
	batchStop   chan struct{}

	streamMu sync.RWMutex
	streams  map[string]map[chan *Reading]struct{}
}

// NewService crea el servicio de lecturas.
func NewService(repo Repository, crops Crops, store cache.Cache, bus *events.Bus, clock func() time.Time,
	minInterval time.Duration, maxPageSize int) *Service {
	return &Service{
		repo:        repo,
		crops:       crops,
		cache:       store,
		bus:         bus,
		clock:       clock,
		minInterval: minInterval,
		maxPageSize: maxPageSize,
		streams:     make(map[string]map[chan *Reading]struct{}),
	}
}

// StartBatchWorker arranca el worker de micro-batching con ventana ultracorta (10ms) o umbral de 100 lecturas y reciclaje de memoria via sync.Pool.
func (s *Service) StartBatchWorker(ctx context.Context) {
	if s.batchActive.Swap(true) {
		return
	}
	s.batchQueue = make(chan Reading, 2048)
	s.batchStop = make(chan struct{})
	s.batchWg.Add(1)

	go func() {
		defer s.batchWg.Done()
		ticker := time.NewTicker(10 * time.Millisecond)
		defer ticker.Stop()

		batchPtr := batchSlicePool.Get().(*[]Reading)
		batch := (*batchPtr)[:0]
		defer func() {
			batch = batch[:0]
			batchSlicePool.Put(&batch)
		}()

		flush := func() {
			if len(batch) == 0 {
				return
			}
			flushCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
			saved := batch
			if err := s.repo.InsertBatch(flushCtx, batch); err != nil {
				// Si falla el lote completo, se guarda una por una para no perder las válidas.
				slog.Warn("falló la inserción en lote; se reintenta lectura por lectura", "count", len(batch), "err", err)
				saved = saved[:0:0]
				for i := range batch {
					if err := s.repo.Insert(flushCtx, &batch[i]); err != nil {
						slog.Error("lectura descartada al guardar", "cropId", batch[i].CropID.Hex(), "err", err)
						continue
					}
					saved = append(saved, batch[i])
				}
			}
			cancel()
			// Los eventos (agente, aprendizaje, avisos) salen solo con la lectura ya guardada. Sus manejadores pueden
			// tardar (consultan a la IA), así que corren aparte para no frenar el siguiente lote.
			for i := range saved {
				s.rememberLatest(&saved[i])
			}
			if len(saved) > 0 {
				events := append([]Reading(nil), saved...)
				go func() {
					for i := range events {
						s.bus.Publish(ReadingRecordedEvent{CropID: events[i].CropID.Hex(), Reading: events[i]})
					}
				}()
			}
			batch = batch[:0]
		}

		for {
			select {
			case <-ctx.Done():
				flush()
				return
			case <-s.batchStop:
				// Drena la cola antes de salir
				for {
					select {
					case r := <-s.batchQueue:
						batch = append(batch, r)
						if len(batch) >= 100 {
							flush()
						}
					default:
						flush()
						return
					}
				}
			case r := <-s.batchQueue:
				batch = append(batch, r)
				if len(batch) >= 100 {
					flush()
				}
			case <-ticker.C:
				flush()
			}
		}
	}()
}

// StopBatchWorker detiene el worker y vacía cualquier lote pendiente en base de datos.
func (s *Service) StopBatchWorker() {
	if !s.batchActive.Swap(false) {
		return
	}
	close(s.batchStop)
	s.batchWg.Wait()
}

// RecordFromDevice guarda la telemetría del dispositivo. Se descarta si el
// cultivo no existe o si llega más rápido que el intervalo mínimo.
func (s *Service) RecordFromDevice(ctx context.Context, cropID string, measures Measures) (*Reading, error) {
	if !s.crops.Exists(ctx, cropID) {
		slog.Debug("telemetría descartada: el cultivo no existe", "cropId", cropID)
		return nil, nil
	}
	online := true
	s.crops.MarkDeviceSeen(ctx, cropID, &online)
	if !s.cache.SetIfAbsent("reading:"+cropID, s.minInterval) {
		slog.Debug("telemetría descartada por frecuencia", "cropId", cropID)
		return nil, nil
	}
	reading, err := s.store(ctx, cropID, measures, SourceMQTT)
	if err != nil {
		slog.Debug("telemetría descartada por rango", "cropId", cropID, "err", err)
		return nil, nil
	}
	return reading, nil
}

// RecordManual guarda una lectura manual (dispositivos sin MQTT o pruebas).
func (s *Service) RecordManual(ctx context.Context, ownerID, cropID string, measures Measures) (*Reading, error) {
	if err := s.crops.RequireOwned(ctx, ownerID, cropID); err != nil {
		return nil, err
	}
	reading, err := s.store(ctx, cropID, measures, SourceHTTP)
	if err != nil {
		return nil, apperr.BadRequest(err.Error())
	}
	return reading, nil
}

// Latest devuelve la última lectura del cultivo (sin comprobar dueño): primero la caché compartida, si no, la base.
func (s *Service) Latest(ctx context.Context, cropID string) (*Reading, error) {
	oid, ok := objectid.Parse(cropID)
	if !ok {
		return nil, nil
	}
	if raw, hit := s.cache.Get(latestKeyPrefix + oid.Hex()); hit {
		var cached Reading
		if json.Unmarshal([]byte(raw), &cached) == nil {
			return &cached, nil
		}
	}
	reading, err := s.repo.Latest(ctx, oid)
	if err == nil && reading != nil {
		s.rememberLatest(reading)
	}
	return reading, err
}

func (s *Service) rememberLatest(r *Reading) {
	if raw, err := json.Marshal(r); err == nil {
		s.cache.Put(latestKeyPrefix+r.CropID.Hex(), string(raw), latestTTL)
	}
}

// recorded deja la lectura guardada como la última y avisa al resto del sistema.
func (s *Service) recorded(r *Reading) {
	s.rememberLatest(r)
	s.bus.Publish(ReadingRecordedEvent{CropID: r.CropID.Hex(), Reading: *r})
}

// LatestOwned devuelve la última lectura comprobando el dueño.
func (s *Service) LatestOwned(ctx context.Context, ownerID, cropID string) (*Reading, error) {
	if err := s.crops.RequireOwned(ctx, ownerID, cropID); err != nil {
		return nil, err
	}
	return s.Latest(ctx, cropID)
}

// Recent devuelve las últimas lecturas del cultivo, más recientes primero.
func (s *Service) Recent(ctx context.Context, cropID string, count int) ([]Reading, error) {
	oid, ok := objectid.Parse(cropID)
	if !ok {
		return nil, nil
	}
	return s.repo.Recent(ctx, oid, count)
}

// List devuelve las lecturas en orden cronológico; por defecto, las últimas 24 horas.
func (s *Service) List(ctx context.Context, ownerID, cropID string, from, to *time.Time, limit *int) ([]Reading, error) {
	if err := s.crops.RequireOwned(ctx, ownerID, cropID); err != nil {
		return nil, err
	}
	start, end, err := s.window(from, to)
	if err != nil {
		return nil, err
	}
	size := s.maxPageSize
	if limit != nil {
		size = clamp(*limit, 1, s.maxPageSize)
	}
	oid, _ := objectid.Parse(cropID)
	readings, err := s.repo.Between(ctx, oid, start, end, size)
	if err != nil {
		return nil, err
	}
	return reversed(readings), nil
}

// Summary calcula el resumen estadístico de la ventana pedida.
func (s *Service) Summary(ctx context.Context, ownerID, cropID string, hours int) (ReadingSummaryResponse, error) {
	if err := s.crops.RequireOwned(ctx, ownerID, cropID); err != nil {
		return ReadingSummaryResponse{}, err
	}
	to := s.clock()
	from := to.Add(-time.Duration(clamp(hours, 1, 24*90)) * time.Hour)
	oid, _ := objectid.Parse(cropID)
	count, metrics, err := s.repo.Summary(ctx, oid, from, to)
	if err != nil {
		return ReadingSummaryResponse{}, err
	}
	return ReadingSummaryResponse{From: from, To: to, Count: count, Metrics: metrics}, nil
}

// ExportCSV exporta hasta 50 000 lecturas del rango en CSV.
func (s *Service) ExportCSV(ctx context.Context, ownerID, cropID string, from, to *time.Time) (string, error) {
	if err := s.crops.RequireOwned(ctx, ownerID, cropID); err != nil {
		return "", err
	}
	start, end, err := s.window(from, to)
	if err != nil {
		return "", err
	}
	oid, _ := objectid.Parse(cropID)
	readings, err := s.repo.Between(ctx, oid, start, end, exportLimit)
	if err != nil {
		return "", err
	}
	readings = reversed(readings)
	var b strings.Builder
	b.WriteString("measuredAt," + strings.Join(Metrics, ",") + "\n")
	for _, r := range readings {
		m := r.Measures
		b.WriteString(r.MeasuredAt.UTC().Format(time.RFC3339Nano))
		for _, v := range []*float64{m.Temperature, m.Humidity, m.Brightness, m.Ph, m.Tds, m.Atmosphere, m.SoilMoisture} {
			b.WriteByte(',')
			b.WriteString(cell(v))
		}
		b.WriteByte('\n')
	}
	return b.String(), nil
}

// SeriesAverages calcula promedios por cultivo e intervalo (para el panel general).
func (s *Service) SeriesAverages(ctx context.Context, cropIDs []string, metric string, from time.Time, bucketMinutes int) ([]Bucket, error) {
	oids := make([]primitive.ObjectID, 0, len(cropIDs))
	for _, id := range cropIDs {
		if oid, ok := objectid.Parse(id); ok {
			oids = append(oids, oid)
		}
	}
	return s.repo.SeriesAverages(ctx, oids, metric, from, bucketMinutes)
}

// SubscribeStream se suscribe a las lecturas en tiempo real de un cultivo mediante un canal.
// Devuelve el canal de recepción y una función para desuscribirse limpiamente.
func (s *Service) SubscribeStream(cropID string) (<-chan *Reading, func()) {
	s.streamMu.Lock()
	defer s.streamMu.Unlock()
	ch := make(chan *Reading, 16)
	if s.streams[cropID] == nil {
		s.streams[cropID] = make(map[chan *Reading]struct{})
	}
	s.streams[cropID][ch] = struct{}{}

	unsubscribe := func() {
		s.streamMu.Lock()
		defer s.streamMu.Unlock()
		if subs, ok := s.streams[cropID]; ok {
			delete(subs, ch)
			if len(subs) == 0 {
				delete(s.streams, cropID)
			}
		}
		close(ch)
	}
	return ch, unsubscribe
}

func (s *Service) broadcastStream(cropID string, r *Reading) {
	s.streamMu.RLock()
	defer s.streamMu.RUnlock()
	subs := s.streams[cropID]
	for ch := range subs {
		select {
		case ch <- r:
		default:
			// descarta sin bloquear si el consumidor no procesa a tiempo
		}
	}
}

// DeleteByCrop borra las lecturas de un cultivo (borrado en cascada por evento).
func (s *Service) DeleteByCrop(ctx context.Context, cropID string) error {
	oid, ok := objectid.Parse(cropID)
	if !ok {
		return nil
	}
	s.cache.Delete(latestKeyPrefix + oid.Hex())
	return s.repo.DeleteByCrop(ctx, oid)
}

func (s *Service) store(ctx context.Context, cropID string, measures Measures, source Source) (*Reading, error) {
	if err := ValidateRanges(measures); err != nil {
		return nil, err
	}
	oid, ok := objectid.Parse(cropID)
	if !ok {
		return nil, apperr.NotFound("El cultivo no existe")
	}
	reading := &Reading{ID: primitive.NewObjectID(), CropID: oid, MeasuredAt: s.clock(), Measures: measures, Source: source}
	// El flujo en vivo (SSE) recibe la lectura validada de inmediato; la caché y los eventos, al quedar guardada.
	s.broadcastStream(oid.Hex(), reading)

	if source == SourceMQTT && s.batchActive.Load() {
		select {
		case s.batchQueue <- *reading:
			return reading, nil
		default:
			slog.Warn("cola de lote de lecturas saturada; guardando inmediatamente", "cropId", cropID)
		}
	}
	if err := s.repo.Insert(ctx, reading); err != nil {
		return nil, err
	}
	s.recorded(reading)
	return reading, nil
}

func (s *Service) window(from, to *time.Time) (time.Time, time.Time, error) {
	end := s.clock()
	if to != nil {
		end = *to
	}
	start := end.Add(-24 * time.Hour)
	if from != nil {
		start = *from
	}
	if start.After(end) {
		return time.Time{}, time.Time{}, apperr.BadRequest("La fecha inicial debe ser anterior a la final")
	}
	return start, end, nil
}

func reversed(in []Reading) []Reading {
	for i, j := 0, len(in)-1; i < j; i, j = i+1, j-1 {
		in[i], in[j] = in[j], in[i]
	}
	return in
}

func cell(v *float64) string {
	if v == nil {
		return ""
	}
	return strconv.FormatFloat(*v, 'g', -1, 64)
}

func clamp(v, lo, hi int) int {
	if v < lo {
		return lo
	}
	if v > hi {
		return hi
	}
	return v
}
