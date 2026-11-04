package security

import (
	"fmt"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/cache"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"github.com/labstack/echo/v4"
)

// RateLimiter aplica una ventana fija de un minuto por IP. Las rutas de
// autenticación tienen un cupo menor. OPTIONS y /health quedan exentos.
type RateLimiter struct {
	store        cache.Cache
	generalLimit int
	authLimit    int
	now          func() time.Time
}

// NewRateLimiter crea el limitador con los cupos general y de autenticación.
func NewRateLimiter(store cache.Cache, generalLimit, authLimit int) *RateLimiter {
	return &RateLimiter{store: store, generalLimit: generalLimit, authLimit: authLimit, now: time.Now}
}

// Middleware devuelve el middleware Echo del limitador.
func (r *RateLimiter) Middleware() echo.MiddlewareFunc {
	return func(next echo.HandlerFunc) echo.HandlerFunc {
		return func(c echo.Context) error {
			path := c.Request().URL.Path
			if c.Request().Method == http.MethodOptions || path == "/health" {
				return next(c)
			}
			authRoute := strings.HasPrefix(path, "/api/v1/auth/") && path != "/api/v1/auth/logout" && path != "/api/v1/auth/session"
			limit := r.generalLimit
			scope := "all:"
			if authRoute {
				limit = r.authLimit
				scope = "auth:"
			}
			epoch := r.now().Unix()
			ip := c.RealIP()
			key := fmt.Sprintf("rate:%s%s:%d", scope, ip, epoch/60)

			if r.store.Increment(key, time.Minute) > int64(limit) {
				retryAfter := 60 - epoch%60
				c.Response().Header().Set("Retry-After", strconv.FormatInt(retryAfter, 10))
				return apperr.New(http.StatusTooManyRequests,
					fmt.Sprintf("Too many requests. Please try again in %d seconds", retryAfter))
			}
			return next(c)
		}
	}
}
