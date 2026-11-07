// Package httpserver arma el motor Echo con el mismo middleware y las mismas
// respuestas de error que la cadena de seguridad y el manejador global de la
// versión en Java.
package httpserver

import (
	"log/slog"
	"net"

	"github.com/labstack/echo/v4"
)

// Options ajusta el motor. TrustProxy toma la IP del cliente de X-Forwarded-For solo si la conexión viene de un
// proxy de confianza: los rangos de TrustedProxies o, si está vacío, redes privadas, loopback y link-local.
type Options struct {
	TrustProxy     bool
	TrustedProxies []string
}

// New crea el motor Echo con el middleware base. Las rutas se registran aparte.
func New(logger *slog.Logger, opts ...Options) *echo.Echo {
	e := echo.New()
	e.HideBanner = true
	e.HidePort = true

	var opt Options
	if len(opts) > 0 {
		opt = opts[0]
	}
	// Sin proxy, la IP es la de la conexión: así nadie evade el límite de tasa con una cabecera falsa. Detrás de
	// nginx (u otro proxy) todas las conexiones llegan desde él y el límite sería uno solo para todos, por eso
	// se lee X-Forwarded-For desde la derecha, saltando los proxies de confianza.
	e.IPExtractor = ipExtractor(opt, logger)

	// Validador intercambiable desacoplado de bibliotecas externas de etiquetas
	e.Validator = NewValidator()

	// Manejador centralizado de errores: transforma errores de dominio, validación y HTTP en respuestas homogéneas
	e.HTTPErrorHandler = func(err error, c echo.Context) {
		RespondError(c, err)
	}

	// Cadena de middlewares por composición de funciones lineales
	e.Use(contextMiddleware(), requestLogger(logger), recovery(logger), securityHeaders(), compressionMiddleware(), etagMiddleware())

	return e
}

func ipExtractor(opt Options, logger *slog.Logger) echo.IPExtractor {
	if !opt.TrustProxy {
		return echo.ExtractIPDirect()
	}
	if len(opt.TrustedProxies) == 0 {
		return echo.ExtractIPFromXFFHeader(echo.TrustLoopback(true), echo.TrustLinkLocal(true), echo.TrustPrivateNet(true))
	}
	trust := []echo.TrustOption{echo.TrustLoopback(true), echo.TrustLinkLocal(false), echo.TrustPrivateNet(false)}
	for _, cidr := range opt.TrustedProxies {
		_, network, err := net.ParseCIDR(cidr)
		if err != nil {
			logger.Warn("invalid trusted proxy CIDR range; ignoring", "cidr", cidr)
			continue
		}
		trust = append(trust, echo.TrustIPRange(network))
	}
	return echo.ExtractIPFromXFFHeader(trust...)
}
