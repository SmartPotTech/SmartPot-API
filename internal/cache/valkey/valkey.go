// Package valkey es el adaptador del puerto cache.Backend sobre Valkey (el
// reemplazo libre de Redis) usando el cliente go-redis, que habla el mismo
// protocolo RESP (con soporte RESP3 y scripts Lua atómicos). Es el único lugar de la API
// que conoce al proveedor de caché: cambiarlo por otro sistema solo requiere otro adaptador
// que cumpla el puerto.
package valkey

import (
	"context"
	"errors"
	"os"
	"strings"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/cache"
	"github.com/redis/go-redis/v9"
)

// Script Lua atómico para incrementar y fijar el TTL de expiración en un único round-trip.
var incrWithTTLScript = redis.NewScript(`
	local current = redis.call('INCR', KEYS[1])
	if current == 1 then
		redis.call('PEXPIRE', KEYS[1], ARGV[1])
	end
	return current
`)

// Adapter implementa cache.Backend contra un servidor Valkey usando protocolo RESP3 y scripts Lua.
type Adapter struct {
	rdb *redis.Client
}

// New crea el adaptador con los tiempos de espera dados y protocolo nativo RESP3.
// Detecta automáticamente si existe un Unix Domain Socket (/var/run/smartpot/valkey.sock)
// o si la dirección especifica el esquema unix://, con degradación elegante a TCP.
func New(addr, username, password string, db int, timeout time.Duration) *Adapter {
	network := "tcp"
	targetAddr := addr
	if valkeySock := os.Getenv("VALKEY_SOCKET"); valkeySock != "" {
		network = "unix"
		targetAddr = valkeySock
	} else if strings.HasPrefix(addr, "unix://") {
		network = "unix"
		targetAddr = strings.TrimPrefix(addr, "unix://")
	} else if _, err := os.Stat("/var/run/smartpot/valkey.sock"); err == nil {
		network = "unix"
		targetAddr = "/var/run/smartpot/valkey.sock"
	}

	return &Adapter{
		rdb: redis.NewClient(&redis.Options{
			Network:      network,
			Addr:         targetAddr,
			Username:     username,
			Password:     password,
			DB:           db,
			Protocol:     3, // Valkey RESP3 nativo
			DialTimeout:  timeout,
			ReadTimeout:  timeout,
			WriteTimeout: timeout,
		}),
	}
}

// Incr incrementa el contador y fija su TTL atómicamente mediante un script Lua en un solo viaje de red.
func (a *Adapter) Incr(ctx context.Context, key string, ttl time.Duration) (int64, error) {
	val, err := incrWithTTLScript.Run(ctx, a.rdb, []string{key}, ttl.Milliseconds()).Int64()
	if err != nil {
		// Fallback defensivo ante entornos que restrinjan scripts Lua
		v, incrErr := a.rdb.Incr(ctx, key).Result()
		if incrErr != nil {
			return 0, incrErr
		}
		if v == 1 {
			a.rdb.Expire(ctx, key, ttl)
		}
		return v, nil
	}
	return val, nil
}

// SetNX fija la llave solo si no existe.
func (a *Adapter) SetNX(ctx context.Context, key, value string, ttl time.Duration) (bool, error) {
	return a.rdb.SetNX(ctx, key, value, ttl).Result()
}

// Get lee una llave; devuelve cache.ErrMiss si no existe.
func (a *Adapter) Get(ctx context.Context, key string) (string, error) {
	val, err := a.rdb.Get(ctx, key).Result()
	if errors.Is(err, redis.Nil) {
		return "", cache.ErrMiss
	}
	return val, err
}

// Set guarda una llave con su TTL.
func (a *Adapter) Set(ctx context.Context, key, value string, ttl time.Duration) error {
	return a.rdb.Set(ctx, key, value, ttl).Err()
}

// GetDel lee y borra en una sola operación; devuelve cache.ErrMiss si no existe.
func (a *Adapter) GetDel(ctx context.Context, key string) (string, error) {
	val, err := a.rdb.GetDel(ctx, key).Result()
	if errors.Is(err, redis.Nil) {
		return "", cache.ErrMiss
	}
	return val, err
}

// Del elimina una o más llaves del almacén Valkey.
func (a *Adapter) Del(ctx context.Context, keys ...string) error {
	if len(keys) == 0 {
		return nil
	}
	return a.rdb.Del(ctx, keys...).Err()
}

// Ping comprueba la conexión.
func (a *Adapter) Ping(ctx context.Context) error {
	return a.rdb.Ping(ctx).Err()
}

// Close cierra el cliente.
func (a *Adapter) Close() error {
	return a.rdb.Close()
}
