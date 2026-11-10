package readings

import (
	"context"
	"encoding/json"
	"fmt"
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
	latestKeyPrefix  = "reading:latest:"
	latestTTL        = 10 * time.Minute
	cropExistsPrefix = "crop:exists:"
	cropExistsTTL    = 30 * time.Second
	deviceSeenPrefix = "device:seen:"
	deviceSeenTTL    = 30 * time.Second
	maxRetryReadings = 20000
)

var batchSlicePool = sync.Pool{
	New: func() any {
		b := make([]Reading, 0, 100)
		return &b
	},
}

// Crops es lo que el servicio de lecturas necesita de los cultivos, sin importar
// el paquete crops (lo implementa CropService). Exists distingue "no existe" de
// "la base no responde" devolviendo el error.
type Crops interface {
	Exists(ctx context.Context, cropID string) (bool, error)
	RequireOwned(ctx context.Context, ownerID, cropID string) error
	RequireOwnedVirtual(ctx context.Context, ownerID, cropID string) error
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

	streamMu      sync.RWMutex
	streams       map[string]map[chan *Reading]struct{}
	streamsClosed bool
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

// StartBatchWorker arranca el worker de micro-batching con ventana ultracorta
// (10 ms) o umbral de 100 lecturas, reciclaje de memoria con sync.Pool y
// reintento con backoff cuando PostgreSQL se pausa. Una lectura aceptada no se
// descarta en silencio: queda en el buffer de reintento hasta que se guarda.
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
			// Se devuelve el puntero obtenido del pool, no una variable local.
			*batchPtr = batch[:0]
			batchSlicePool.Put(batchPtr)
		}()

		var retry []Reading
		retryDelay := 200 * time.Millisecond
		var retryAt time.Time

		publish := func(saved []Reading) {
			for i := range saved {
				s.rememberLatest(&saved[i])
			}
			if len(saved) == 0 {
				return
			}
			events := append([]Reading(nil), saved...)
			go func() {
				for i := range events {
					s.bus.Publish(ReadingRecordedEvent{CropID: events[i].CropID.Hex(), Reading: events[i]})
				}
			}()
		}

		flush := func(force bool) {
			if len(batch) == 0 && len(retry) == 0 {
				return
			}
			if !force && !retryAt.IsZero() && time.Now().Before(retryAt) {
				return
			}
			all := make([]Reading, 0, len(retry)+len(batch))
			all = append(all, retry...)
			all = append(all, batch...)
			retry, batch = retry[:0], batch[:0]

			flushCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
			err := s.repo.InsertBatch(flushCtx, all)
			cancel()
			if err == nil {
				retryDelay = 200 * time.Millisecond
				retryAt = time.Time{}
				publish(all)
				return
			}

			// El lote completo falló (base pausada o conexión perdida). Se
			// reintenta lectura por lectura con un subcontexto independiente:
			// el presupuesto agotado del lote no condena a las demás.
			slog.Warn("batch insert failed; retrying reading by reading", "count", len(all), "err", err)
			saved := make([]Reading, 0, len(all))
			failed := make([]Reading, 0)
			// Presupuesto acotado para el respaldo individual: si la base sigue
			// caída, no se puede tardar 5 s por cada lectura del búfer.
			budget := time.Now().Add(10 * time.Second)
			for i := range all {
				if time.Now().After(budget) {
					failed = append(failed, all[i:]...)
					break
				}
				itemCtx, itemCancel := context.WithTimeout(context.WithoutCancel(ctx), 5*time.Second)
				itemErr := s.repo.Insert(itemCtx, &all[i])
				itemCancel()
				if itemErr != nil {
					failed = append(failed, all[i])
					continue
				}
				saved = append(saved, all[i])
			}
			publish(saved)
			if len(failed) == 0 {
				retryDelay = 200 * time.Millisecond
				retryAt = time.Time{}
				return
			}
			if len(failed) > maxRetryReadings {
				dropped := len(failed) - maxRetryReadings
				failed = failed[dropped:]
				slog.Error("readings retry buffer full; dropping oldest", "dropped", dropped)
			}
			retry = failed
			retryDelay *= 2
			if retryDelay > 30*time.Second {
				retryDelay = 30 * time.Second
			}
			retryAt = time.Now().Add(retryDelay)
			slog.Error("readings kept for retry after database failure",
				"count", len(retry), "retryIn", retryDelay.String())
		}

		drain := func() {
			for {
				select {
				case r := <-s.batchQueue:
					batch = append(batch, r)
					if len(batch) >= 100 {
						flush(true)
					}
				default:
					flush(true)
					if len(retry) > 0 {
						slog.Error("readings could not be persisted before shutdown", "count", len(retry))
					}
					return
				}
			}
		}

		for {
			select {
			case <-ctx.Done():
				drain()
				return
			case <-s.batchStop:
				drain()
				return
			case r := <-s.batchQueue:
				batch = append(batch, r)
				if len(batch) >= 100 {
					flush(false)
				}
			case <-ticker.C:
				flush(false)
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

// RecordFromDevice guarda la telemetría del dispositivo. El rate gate va
// primero (sin tocar PostgreSQL), la existencia del cultivo se cachea y, si la
// base está pausada, la lectura se encola para reintento en vez de perderse.
func (s *Service) RecordFromDevice(ctx context.Context, cropID string, measures Measures) (*Reading, error) {
	oid, ok := objectid.Parse(cropID)
	if !ok {
		slog.Debug("telemetry dropped: invalid crop id", "cropId", cropID)
		return nil, nil
	}
	if err := ValidateRanges(measures); err != nil {
		slog.Debug("telemetry dropped due to out of range value", "cropId", cropID, "err", err)
		return nil, nil
	}
	if !s.cache.SetIfAbsent("reading:"+oid.Hex(), s.minInterval) {
		slog.Debug("telemetry dropped due to rate limit", "cropId", cropID)
		return nil, nil
	}

	exists, err := s.cropExists(ctx, oid.Hex())
	if err != nil {
		// La base no responde: no se puede afirmar que el cultivo no exista. La
		// lectura entra al worker de lotes (con reintento y backoff) y se emite
		// por SSE mientras tanto.
		reading := s.newReading(oid, measures, SourceMQTT)
		s.broadcastStream(oid.Hex(), reading)
		if !s.enqueue(*reading) {
			slog.Error("telemetry could not be queued while database is unavailable",
				"cropId", cropID, "err", err)
			return reading, fmt.Errorf("readings queue is full: %w", err)
		}
		slog.Warn("database unavailable; telemetry queued for retry", "cropId", cropID, "err", err)
		return reading, nil
	}
	if !exists {
		slog.Debug("telemetry dropped: crop does not exist", "cropId", cropID)
		return nil, nil
	}

	s.markDeviceSeen(oid.Hex(), true)
	return s.store(ctx, oid.Hex(), measures, SourceMQTT)
}

// cropExists cachea solo la existencia positiva: un cultivo recién creado nunca
// queda oculto por una entrada negativa.
func (s *Service) cropExists(ctx context.Context, cropID string) (bool, error) {
	key := cropExistsPrefix + cropID
	if cached, ok := s.cache.Get(key); ok && cached == "1" {
		return true, nil
	}
	exists, err := s.crops.Exists(ctx, cropID)
	if err != nil {
		return false, err
	}
	if exists {
		s.cache.Put(key, "1", cropExistsTTL)
	}
	return exists, nil
}

// markDeviceSeen limita la escritura de last_seen a una vez cada 30 s por
// cultivo, para que una ráfaga de telemetría no sature el pool (C-07).
func (s *Service) markDeviceSeen(cropID string, online bool) {
	if !s.cache.SetIfAbsent(deviceSeenPrefix+cropID, deviceSeenTTL) {
		return
	}
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	s.crops.MarkDeviceSeen(ctx, cropID, &online)
}

// enqueue entrega la lectura al worker de lotes; false si no está activo o la
// cola está llena (el llamador decide si la guarda en línea).
func (s *Service) enqueue(reading Reading) bool {
	if !s.batchActive.Load() {
		return false
	}
	select {
	case s.batchQueue <- reading:
		return true
	default:
		return false
	}
}

// RecordManual guarda una lectura manual (solo permitida en cultivos virtuales o pruebas).
func (s *Service) RecordManual(ctx context.Context, ownerID, cropID string, measures Measures) (*Reading, error) {
	if err := s.crops.RequireOwnedVirtual(ctx, ownerID, cropID); err != nil {
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

// LatestByCropIDs devuelve la última lectura de varios cultivos con una sola
// consulta SQL para los que no estén en Valkey: elimina el N+1 de /crops y del
// panel general.
func (s *Service) LatestByCropIDs(ctx context.Context, cropIDs []primitive.ObjectID) (map[string]*Reading, error) {
	out := make(map[string]*Reading, len(cropIDs))
	missing := make([]primitive.ObjectID, 0, len(cropIDs))
	for _, oid := range cropIDs {
		if raw, hit := s.cache.Get(latestKeyPrefix + oid.Hex()); hit {
			var cached Reading
			if json.Unmarshal([]byte(raw), &cached) == nil {
				out[oid.Hex()] = &cached
				continue
			}
		}
		missing = append(missing, oid)
	}
	if len(missing) == 0 {
		return out, nil
	}
	found, err := s.repo.LatestByCropIDs(ctx, missing)
	if err != nil {
		return nil, err
	}
	for id, reading := range found {
		out[id] = reading
		s.rememberLatest(reading)
	}
	return out, nil
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
	if s.streamsClosed {
		closed := make(chan *Reading)
		close(closed)
		return closed, func() {}
	}
	ch := make(chan *Reading, 16)
	if s.streams[cropID] == nil {
		s.streams[cropID] = make(map[chan *Reading]struct{})
	}
	s.streams[cropID][ch] = struct{}{}

	unsubscribe := func() {
		s.streamMu.Lock()
		defer s.streamMu.Unlock()
		subs, ok := s.streams[cropID]
		if !ok {
			return
		}
		if _, exists := subs[ch]; !exists {
			// CloseStreams ya cerró el canal durante el apagado.
			return
		}
		delete(subs, ch)
		if len(subs) == 0 {
			delete(s.streams, cropID)
		}
		close(ch)
	}
	return ch, unsubscribe
}

// CloseStreams cierra todos los flujos SSE activos. Se llama al recibir SIGTERM,
// antes de apagar el servidor HTTP, para que srv.Shutdown no espere 20 s.
func (s *Service) CloseStreams() {
	s.streamMu.Lock()
	defer s.streamMu.Unlock()
	s.streamsClosed = true
	for cropID, subs := range s.streams {
		for ch := range subs {
			close(ch)
		}
		delete(s.streams, cropID)
	}
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
	s.cache.Delete(latestKeyPrefix+oid.Hex(), cropExistsPrefix+oid.Hex(), deviceSeenPrefix+oid.Hex())
	return s.repo.DeleteByCrop(ctx, oid)
}

func (s *Service) newReading(cropID primitive.ObjectID, measures Measures, source Source) *Reading {
	return &Reading{ID: primitive.NewObjectID(), CropID: cropID, MeasuredAt: s.clock(), Measures: measures, Source: source}
}

func (s *Service) store(ctx context.Context, cropID string, measures Measures, source Source) (*Reading, error) {
	if err := ValidateRanges(measures); err != nil {
		return nil, err
	}
	oid, ok := objectid.Parse(cropID)
	if !ok {
		return nil, apperr.NotFound("Crop does not exist")
	}
	reading := s.newReading(oid, measures, source)
	// El flujo en vivo (SSE) recibe la lectura validada de inmediato; la caché y los eventos, al quedar guardada.
	s.broadcastStream(oid.Hex(), reading)

	if source == SourceMQTT && s.enqueue(*reading) {
		return reading, nil
	}
	if source == SourceMQTT {
		slog.Warn("readings batch queue saturated; persisting synchronously", "cropId", cropID)
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
		return time.Time{}, time.Time{}, apperr.BadRequest("Start date must be before end date")
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
