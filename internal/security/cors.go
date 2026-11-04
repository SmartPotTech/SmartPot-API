package security

import (
	"time"

	"github.com/labstack/echo/v4"
	"github.com/labstack/echo/v4/middleware"
)

// CORS configura los orígenes permitidos, métodos, cabeceras (incluido el token CSRF)
// y el envío de credenciales para la cookie HttpOnly de sesión.
func CORS(allowedOrigins []string) echo.MiddlewareFunc {
	return middleware.CORSWithConfig(middleware.CORSConfig{
		AllowOrigins:     allowedOrigins,
		AllowMethods:     []string{"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"},
		AllowHeaders:     []string{"Authorization", "Content-Type", "Accept", "If-None-Match", CSRFHeaderName},
		ExposeHeaders:    []string{"Retry-After", "ETag", CSRFHeaderName},
		AllowCredentials: true,
		MaxAge:           int(time.Hour / time.Second),
	})
}
