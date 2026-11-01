package security

import (
	"context"
	"net/http"
	"strings"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/httpserver"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"github.com/labstack/echo/v4"
)

const (
	// RoleAdmin y RoleUser son los roles de la cuenta.
	RoleAdmin = "ADMIN"
	RoleUser  = "USER"
)

// ConnectionPrincipal es a quién representa un token de conexión y con qué alcance.
type ConnectionPrincipal struct {
	ConnectionID string
	UserID       string
	Scope        string
}

// ConnectionAuthenticator verifica tokens de conexión (lo implementa el paquete connections; la interfaz vive
// aquí para no crear un ciclo de imports).
type ConnectionAuthenticator interface {
	AuthenticateConnection(ctx context.Context, raw string) (ConnectionPrincipal, error)
}

// connectionTokenPrefix distingue un token de conexión de un JWT de sesión.
const connectionTokenPrefix = "spt_"

// UseConnections habilita que el guard acepte tokens de conexión además de la sesión.
func (s *TokenService) UseConnections(a ConnectionAuthenticator) {
	s.connections = a
}

// Guard devuelve el middleware que exige un token JWT válido (o un token de conexión, si está habilitado) e
// inyecta la identidad y las credenciales verificadas en el RequestContext tipado en tiempo de compilación (sin
// usar mapas de strings ni conversiones any).
func (s *TokenService) Guard() echo.MiddlewareFunc {
	return func(next echo.HandlerFunc) echo.HandlerFunc {
		return func(c echo.Context) error {
			header := c.Request().Header.Get("Authorization")
			if !strings.HasPrefix(header, "Bearer ") {
				return apperr.Unauthorized("Inicia sesión para continuar")
			}
			raw := strings.TrimSpace(strings.TrimPrefix(header, "Bearer "))
			if s.connections != nil && strings.HasPrefix(raw, connectionTokenPrefix) {
				return s.guardConnection(c, raw, next)
			}
			claims, err := s.Parse(raw)
			if err != nil {
				return apperr.Unauthorized("Tu sesión no es válida o expiró. Inicia sesión de nuevo")
			}
			rc := httpserver.FromContext(c)
			rc.SetIdentity(httpserver.Identity{
				UserID:        claims.UserID,
				Email:         claims.Email,
				Role:          claims.Role,
				IssuedAt:      claims.IssuedAt,
				ExpiresAt:     claims.ExpiresAt,
				Authenticated: true,
			})
			return next(rc)
		}
	}
}

func (s *TokenService) guardConnection(c echo.Context, raw string, next echo.HandlerFunc) error {
	p, err := s.connections.AuthenticateConnection(c.Request().Context(), raw)
	if err != nil {
		return err
	}
	if err := connectionAllowed(c.Request().Method, c.Request().URL.Path, p.Scope); err != nil {
		return err
	}
	rc := httpserver.FromContext(c)
	// Una conexión nunca obtiene el rol de administrador, aunque la cuenta lo tenga.
	rc.SetIdentity(httpserver.Identity{
		UserID:       p.UserID,
		Role:         RoleUser,
		ConnectionID: p.ConnectionID,
		Scope:        p.Scope,
	})
	return next(rc)
}

// connectionAllowed acota lo que puede hacer un token de conexión: la cuenta, las conexiones, los canales de
// aviso y las credenciales de los dispositivos solo se manejan desde la PWA, y el alcance de lectura no escribe.
func connectionAllowed(method, path, scope string) error {
	read := method == http.MethodGet || method == http.MethodHead || method == http.MethodOptions
	switch {
	case strings.HasPrefix(path, "/api/v1/connections"):
		return apperr.Forbidden("Las conexiones solo se administran desde la aplicación")
	case strings.HasPrefix(path, "/api/v1/users/me") && !read:
		return apperr.Forbidden("Los datos de la cuenta solo se cambian desde la aplicación")
	case strings.HasPrefix(path, "/api/v1/channels") && !read:
		return apperr.Forbidden("Los canales de aviso solo se configuran desde la aplicación")
	case hasSegment(path, "device"):
		return apperr.Forbidden("Las credenciales del dispositivo solo se consultan desde la aplicación")
	case scope != "CONTROL" && !read:
		return apperr.Forbidden("Esta conexión es de solo lectura")
	}
	return nil
}

func hasSegment(path, segment string) bool {
	for _, part := range strings.Split(path, "/") {
		if part == segment {
			return true
		}
	}
	return false
}

// RequireRole exige un rol concreto; responde 403 como la versión en Java.
func RequireRole(role string) echo.MiddlewareFunc {
	return func(next echo.HandlerFunc) echo.HandlerFunc {
		return func(c echo.Context) error {
			if CurrentRole(c) != role {
				return apperr.Forbidden("No tienes permiso para realizar esta acción")
			}
			return next(c)
		}
	}
}

// CurrentIdentity devuelve la estructura tipada con la identidad y credenciales JWT del usuario.
func CurrentIdentity(c echo.Context) httpserver.Identity {
	return httpserver.FromContext(c).Identity()
}

// CurrentUserID devuelve el id del usuario autenticado (vacío si no hay).
func CurrentUserID(c echo.Context) string {
	return httpserver.FromContext(c).UserID()
}

// CurrentEmail devuelve el correo del usuario autenticado.
func CurrentEmail(c echo.Context) string {
	return httpserver.FromContext(c).Email()
}

// CurrentRole devuelve el rol del usuario autenticado.
func CurrentRole(c echo.Context) string {
	return httpserver.FromContext(c).Role()
}
