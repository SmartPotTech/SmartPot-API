package postgres

import (
	"context"
	"strings"
	"testing"
)

// La URL es válida pero no se conecta: Connect no abre conexiones hasta la primera consulta.
const lazyURL = "postgres://smartpot:clave@127.0.0.1:1/smartpot?sslmode=disable"

func TestConnectRechazaPoolDesmedido(t *testing.T) {
	_, err := Connect(context.Background(), lazyURL, MaxPoolConns+1)
	if err == nil || !strings.Contains(err.Error(), "DATABASE_MAX_CONNS") {
		t.Fatalf("se esperaba el error de DATABASE_MAX_CONNS, llegó %v", err)
	}
}

func TestConnectAplicaElMaximoDeConexiones(t *testing.T) {
	pool, err := Connect(context.Background(), lazyURL, 7)
	if err != nil {
		t.Fatalf("Connect: %v", err)
	}
	defer pool.Pool.Close()
	if got := pool.Pool.Config().MaxConns; got != 7 {
		t.Fatalf("MaxConns = %d, se esperaba 7", got)
	}
}
