package httpserver

import (
	"github.com/labstack/echo/v4"
	"github.com/labstack/echo/v4/middleware"
)

// Respuestas más pequeñas que esto viajan sin comprimir: el encabezado de gzip y la CPU no compensan.
const compressionMinLength = 1024

// compressionMiddleware comprime con gzip las respuestas cuando el cliente lo acepta. La serie de lecturas de un
// cultivo (hasta 2000 puntos en JSON) es la respuesta más pesada de la API y se reduce a una fracción. Va por fuera
// del middleware de ETag, así que el ETag se calcula sobre el JSON sin comprimir y un 304 sigue saliendo sin cuerpo.
// Los flujos SSE quedan fuera porque cada evento debe llegar en cuanto se escribe.
func compressionMiddleware() echo.MiddlewareFunc {
	return middleware.GzipWithConfig(middleware.GzipConfig{
		Level:     5,
		MinLength: compressionMinLength,
		Skipper: func(c echo.Context) bool {
			return isEventStream(c.Request())
		},
	})
}
