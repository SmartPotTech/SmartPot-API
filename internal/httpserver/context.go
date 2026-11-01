package httpserver

import (
	"strconv"
	"sync"
	"sync/atomic"
	"time"

	"github.com/labstack/echo/v4"
)

var requestCounter atomic.Uint64

var contextPool = sync.Pool{
	New: func() any {
		return &RequestContext{}
	},
}

// Identity agrupa la identidad del usuario autenticado y los metadatos de su
// credencial JWT con tipado estricto en tiempo de compilación.
type Identity struct {
	UserID        string
	Email         string
	Role          string
	IssuedAt      time.Time
	ExpiresAt     time.Time
	Authenticated bool
	// ConnectionID y Scope solo se llenan cuando la petición llega con un token de conexión (no con la sesión).
	ConnectionID string
	Scope        string
}

// TelemetryTrace almacena las trazas de telemetría de la petición HTTP en curso.
type TelemetryTrace struct {
	RequestID string
	StartedAt time.Time
	ClientIP  string
}

// RequestContext extiende la interfaz desacoplada echo.Context para inyectar
// identidades de usuario, credenciales JWT y trazas de telemetría con tipado
// estricto en tiempo de compilación, evitando mapas de cadenas (c.Set/c.Get)
// y conversiones de tipo inseguras (any) en tiempo de ejecución.
type RequestContext struct {
	echo.Context
	identity Identity
	trace    TelemetryTrace
}

func (c *RequestContext) reset(base echo.Context, trace TelemetryTrace) {
	c.Context = base
	c.identity = Identity{}
	c.trace = trace
}

// SetIdentity registra la identidad autenticada y las credenciales JWT en el contexto tipado.
func (c *RequestContext) SetIdentity(id Identity) {
	id.Authenticated = id.UserID != ""
	c.identity = id
}

// Identity devuelve la identidad autenticada del contexto actual.
func (c *RequestContext) Identity() Identity {
	return c.identity
}

// UserID devuelve el identificador del usuario autenticado (vacío si es anónimo).
func (c *RequestContext) UserID() string {
	return c.identity.UserID
}

// Email devuelve el correo electrónico del usuario autenticado.
func (c *RequestContext) Email() string {
	return c.identity.Email
}

// Role devuelve el rol del usuario autenticado.
func (c *RequestContext) Role() string {
	return c.identity.Role
}

// Trace devuelve la traza de telemetría de la solicitud actual.
func (c *RequestContext) Trace() TelemetryTrace {
	return c.trace
}

// FromContext obtiene el *RequestContext tipado a partir de cualquier echo.Context.
// Si un test unitario invoca un manejador directamente sin pasar por el middleware
// raíz, envuelve el contexto de forma transparente garantizando seguridad de tipos.
func FromContext(c echo.Context) *RequestContext {
	if rc, ok := c.(*RequestContext); ok {
		return rc
	}
	return &RequestContext{
		Context: c,
		trace: TelemetryTrace{
			StartedAt: time.Now(),
		},
	}
}

// contextMiddleware envuelve cada echo.Context en un *RequestContext reutilizado
// mediante sync.Pool (cero asignaciones dinámicas en el camino crítico).
func contextMiddleware() echo.MiddlewareFunc {
	return func(next echo.HandlerFunc) echo.HandlerFunc {
		return func(c echo.Context) error {
			reqID := c.Request().Header.Get(echo.HeaderXRequestID)
			if reqID == "" {
				reqID = strconv.FormatUint(requestCounter.Add(1), 36)
			}
			c.Response().Header().Set(echo.HeaderXRequestID, reqID)
			rc := contextPool.Get().(*RequestContext)
			rc.reset(c, TelemetryTrace{
				RequestID: reqID,
				StartedAt: time.Now(),
			})
			defer func() {
				rc.Context = nil
				rc.identity = Identity{}
				rc.trace = TelemetryTrace{}
				contextPool.Put(rc)
			}()
			return next(rc)
		}
	}
}
