package postgres

import (
	"context"
	"errors"
	"math"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/readings"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

// ReadingRepository implementa readings.Repository sobre la hipertabla de TimescaleDB (tabla readings).
type ReadingRepository struct {
	pool *pgxpool.Pool
}

// NewReadingRepository crea el adaptador de telemetría.
func NewReadingRepository(pool *pgxpool.Pool) *ReadingRepository {
	return &ReadingRepository{pool: pool}
}

// metricColumn traduce el nombre de una variable (como la expone la API) a su columna. Solo se aceptan las
// variables conocidas, para no inyectar nombres de columna en el SQL.
var metricColumn = map[string]string{
	"temperature":  "temperature",
	"humidity":     "humidity",
	"brightness":   "brightness",
	"ph":           "ph",
	"tds":          "tds",
	"atmosphere":   "atmosphere",
	"soilMoisture": "soil_moisture",
}

const readingColumns = "id, crop_id, measured_at, source, temperature, humidity, brightness, ph, tds, atmosphere, soil_moisture"

func scanReading(row pgx.Row) (*readings.Reading, error) {
	var (
		r          readings.Reading
		id, cropID string
		source     *string
	)
	m := &r.Measures
	if err := row.Scan(&id, &cropID, &r.MeasuredAt, &source,
		&m.Temperature, &m.Humidity, &m.Brightness, &m.Ph, &m.Tds, &m.Atmosphere, &m.SoilMoisture); err != nil {
		return nil, err
	}
	var err error
	if r.ID, err = parseOID(id); err != nil {
		return nil, err
	}
	if r.CropID, err = parseOID(cropID); err != nil {
		return nil, err
	}
	if source != nil {
		r.Source = readings.Source(*source)
	}
	return &r, nil
}

func (r *ReadingRepository) query(ctx context.Context, sql string, args ...any) ([]readings.Reading, error) {
	rows, err := r.pool.Query(ctx, sql, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := make([]readings.Reading, 0)
	for rows.Next() {
		reading, err := scanReading(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, *reading)
	}
	return out, rows.Err()
}

// Insert guarda una lectura y fija su id generado.
func (r *ReadingRepository) Insert(ctx context.Context, reading *readings.Reading) error {
	if reading.ID.IsZero() {
		reading.ID = primitive.NewObjectID()
	}
	m := reading.Measures
	_, err := r.pool.Exec(ctx,
		"INSERT INTO readings ("+readingColumns+") VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)",
		hexOf(reading.ID), hexOf(reading.CropID), reading.MeasuredAt, nullEnum(string(reading.Source)),
		m.Temperature, m.Humidity, m.Brightness, m.Ph, m.Tds, m.Atmosphere, m.SoilMoisture)
	return err
}

// InsertBatch guarda un conjunto de lecturas de telemetría usando pgx.CopyFrom (streaming binario) con fallback a pgx.Batch.
func (r *ReadingRepository) InsertBatch(ctx context.Context, batchReadings []readings.Reading) error {
	if len(batchReadings) == 0 {
		return nil
	}
	rows := make([][]any, len(batchReadings))
	for i := range batchReadings {
		rd := &batchReadings[i]
		if rd.ID.IsZero() {
			rd.ID = primitive.NewObjectID()
		}
		m := rd.Measures
		rows[i] = []any{
			hexOf(rd.ID), hexOf(rd.CropID), rd.MeasuredAt, nullEnum(string(rd.Source)),
			m.Temperature, m.Humidity, m.Brightness, m.Ph, m.Tds, m.Atmosphere, m.SoilMoisture,
		}
	}
	cols := []string{"id", "crop_id", "measured_at", "source", "temperature", "humidity", "brightness", "ph", "tds", "atmosphere", "soil_moisture"}
	if _, err := r.pool.CopyFrom(ctx, pgx.Identifier{"readings"}, cols, pgx.CopyFromRows(rows)); err == nil {
		return nil
	}

	batch := &pgx.Batch{}
	sql := "INSERT INTO readings (" + readingColumns + ") VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)"
	for i := range batchReadings {
		rd := &batchReadings[i]
		m := rd.Measures
		batch.Queue(sql,
			hexOf(rd.ID), hexOf(rd.CropID), rd.MeasuredAt, nullEnum(string(rd.Source)),
			m.Temperature, m.Humidity, m.Brightness, m.Ph, m.Tds, m.Atmosphere, m.SoilMoisture)
	}
	br := r.pool.SendBatch(ctx, batch)
	defer br.Close()
	for range batchReadings {
		if _, err := br.Exec(); err != nil {
			return err
		}
	}
	return nil
}

// Latest devuelve la última lectura del cultivo, o nil si no tiene.
func (r *ReadingRepository) Latest(ctx context.Context, cropID primitive.ObjectID) (*readings.Reading, error) {
	reading, err := scanReading(r.pool.QueryRow(ctx,
		"SELECT "+readingColumns+" FROM readings WHERE crop_id = $1 ORDER BY measured_at DESC LIMIT 1", hexOf(cropID)))
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, nil
	}
	return reading, err
}

// Recent devuelve las últimas lecturas del cultivo, más recientes primero.
func (r *ReadingRepository) Recent(ctx context.Context, cropID primitive.ObjectID, limit int) ([]readings.Reading, error) {
	return r.query(ctx,
		"SELECT "+readingColumns+" FROM readings WHERE crop_id = $1 ORDER BY measured_at DESC LIMIT $2", hexOf(cropID), limit)
}

// Between devuelve las lecturas del cultivo en el rango, más recientes primero.
func (r *ReadingRepository) Between(ctx context.Context, cropID primitive.ObjectID, from, to time.Time, limit int) ([]readings.Reading, error) {
	return r.query(ctx,
		"SELECT "+readingColumns+" FROM readings WHERE crop_id = $1 AND measured_at >= $2 AND measured_at <= $3 ORDER BY measured_at DESC LIMIT $4",
		hexOf(cropID), from, to, limit)
}

// DeleteByCrop borra todas las lecturas de un cultivo.
func (r *ReadingRepository) DeleteByCrop(ctx context.Context, cropID primitive.ObjectID) error {
	_, err := r.pool.Exec(ctx, "DELETE FROM readings WHERE crop_id = $1", hexOf(cropID))
	return err
}

// Summary calcula el conteo y el mínimo, promedio y máximo por variable en la ventana.
func (r *ReadingRepository) Summary(ctx context.Context, cropID primitive.ObjectID, from, to time.Time) (int64, map[string]readings.MetricSummary, error) {
	// count + (min, avg, max) de las 7 variables, en el orden de readings.Metrics.
	row := r.pool.QueryRow(ctx,
		`SELECT count(*),
		        min(temperature), avg(temperature), max(temperature),
		        min(humidity), avg(humidity), max(humidity),
		        min(brightness), avg(brightness), max(brightness),
		        min(ph), avg(ph), max(ph),
		        min(tds), avg(tds), max(tds),
		        min(atmosphere), avg(atmosphere), max(atmosphere),
		        min(soil_moisture), avg(soil_moisture), max(soil_moisture)
		 FROM readings WHERE crop_id = $1 AND measured_at >= $2 AND measured_at <= $3`,
		hexOf(cropID), from, to)

	var count int64
	stats := make([]*float64, 21)
	dest := []any{&count}
	for i := range stats {
		dest = append(dest, &stats[i])
	}
	if err := row.Scan(dest...); err != nil {
		return 0, nil, err
	}

	metrics := make(map[string]readings.MetricSummary)
	for i, name := range readings.Metrics {
		min, avg, max := stats[i*3], stats[i*3+1], stats[i*3+2]
		if avg == nil {
			continue
		}
		rounded := math.Round(*avg*100) / 100
		metrics[name] = readings.MetricSummary{Min: min, Avg: &rounded, Max: max}
	}
	return count, metrics, nil
}

// SeriesAverages calcula promedios por cultivo e intervalo enrutando inteligentemente hacia las vistas continuas.
func (r *ReadingRepository) SeriesAverages(ctx context.Context, cropIDs []primitive.ObjectID, metric string, from time.Time, bucketMinutes int) ([]readings.Bucket, error) {
	col, ok := metricColumn[metric]
	if !ok || len(cropIDs) == 0 {
		return nil, nil
	}
	hexes := make([]string, len(cropIDs))
	for i, id := range cropIDs {
		hexes[i] = id.Hex()
	}

	// Enrutamiento inteligente a vistas continuas materializadas
	var caggTable string
	if bucketMinutes >= 60 {
		caggTable = "readings_hourly"
	} else if bucketMinutes >= 15 {
		caggTable = "readings_15m"
	} else if bucketMinutes >= 1 {
		caggTable = "readings_1m"
	}

	if caggTable != "" {
		caggCol := "avg_" + col
		// Promedio ponderado por cantidad de lecturas: reagrupar promedios con avg() daría el mismo peso a un balde
		// de 3 lecturas que a uno de 60.
		sqlCagg := "SELECT crop_id, time_bucket(make_interval(mins => $2), bucket) AS b, " +
			"sum(" + caggCol + " * reading_count) / nullif(sum(reading_count), 0) " +
			"FROM " + caggTable + " WHERE crop_id = ANY($1) AND bucket >= $3 AND " + caggCol + " IS NOT NULL " +
			"GROUP BY crop_id, b ORDER BY b ASC"
		rows, err := r.pool.Query(ctx, sqlCagg, hexes, bucketMinutes, from)
		if err == nil {
			defer rows.Close()
			out := make([]readings.Bucket, 0)
			for rows.Next() {
				var b readings.Bucket
				if err := rows.Scan(&b.CropID, &b.Time, &b.Value); err == nil {
					out = append(out, b)
				}
			}
			if rows.Err() == nil && len(out) > 0 {
				return out, nil
			}
		}
	}

	// Fallback a la hipertabla readings
	sql := "SELECT crop_id, time_bucket(make_interval(mins => $2), measured_at) AS bucket, avg(" + col + ") " +
		"FROM readings WHERE crop_id = ANY($1) AND measured_at >= $3 AND " + col + " IS NOT NULL " +
		"GROUP BY crop_id, bucket ORDER BY bucket ASC"
	rows, err := r.pool.Query(ctx, sql, hexes, bucketMinutes, from)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := make([]readings.Bucket, 0)
	for rows.Next() {
		var b readings.Bucket
		if err := rows.Scan(&b.CropID, &b.Time, &b.Value); err != nil {
			return nil, err
		}
		out = append(out, b)
	}
	return out, rows.Err()
}
