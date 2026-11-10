// Package postgres es el adaptador de almacenamiento sobre PostgreSQL + TimescaleDB. Implementa los puertos
// (interfaces Repository) que declara cada paquete de dominio, de modo que los servicios dependen de la interfaz y
// no del motor de base de datos. Cambiar de motor (o de lenguaje) es escribir otro adaptador sin tocar el dominio:
// es la inversión de dependencias (SOLID-D) y la arquitectura hexagonal (puertos y adaptadores) con la API al mando.
//
// La conexión es perezosa: la API arranca aunque la base no responda todavía y /health informa el estado real,
// igual que con el adaptador anterior.
package postgres

import (
	"context"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

// Pool envuelve el pool de conexiones de pgx.
type Pool struct {
	Pool *pgxpool.Pool
}

// MaxPoolConns acota DATABASE_MAX_CONNS: más conexiones por instancia de la API no tiene sentido y el pool
// de pgx usa int32.
const MaxPoolConns = 1000

// Connect crea el pool a partir de la URL de PostgreSQL. No abre conexiones hasta la primera consulta.
func Connect(ctx context.Context, url string, maxConns int) (*Pool, error) {
	cfg, err := pgxpool.ParseConfig(url)
	if err != nil {
		return nil, fmt.Errorf("invalid DATABASE_URL: %w", err)
	}
	if maxConns > MaxPoolConns {
		return nil, fmt.Errorf("DATABASE_MAX_CONNS cannot exceed %d", MaxPoolConns)
	}
	if maxConns > 0 {
		cfg.MaxConns = int32(maxConns)
	}
	cfg.MaxConnIdleTime = 5 * time.Minute
	// La API aplica aislamiento estricto por owner_id en cada consulta de dominio.
	// Activar bypass_rls a nivel de conexión evita contaminar el estado de las conexiones reutilizadas en el pool.
	cfg.AfterConnect = func(ctx context.Context, conn *pgx.Conn) error {
		_, err := conn.Exec(ctx, "SET app.bypass_rls = 'on'")
		return err
	}
	pool, err := pgxpool.NewWithConfig(ctx, cfg)
	if err != nil {
		return nil, fmt.Errorf("could not create PostgreSQL pool: %w", err)
	}
	return &Pool{Pool: pool}, nil
}

// Ping comprueba que la base responde.
func (p *Pool) Ping(ctx context.Context) error {
	return p.Pool.Ping(ctx)
}

// Close cierra el pool.
func (p *Pool) Close() {
	p.Pool.Close()
}
