package security

import (
	"context"
	"crypto/subtle"
	"net/http"
	"strings"
	"time"

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

// SetSessionCookies escribe la cookie HttpOnly de sesión, la cookie CSRF y la cabecera X-CSRF-Token.
func (s *TokenService) SetSessionCookies(c echo.Context, issued IssuedToken, remember bool) {
	secure := isSecureRequest(c)
	domain := cookieDomain(c)
	csrf := s.CSRFToken(issued.Value)
	sessionCookie := &http.Cookie{
		Name:     SessionCookieName,
		Value:    issued.Value,
		Path:     "/",
		Domain:   domain,
		HttpOnly: true,
		Secure:   secure,
		SameSite: http.SameSiteLaxMode,
	}
	csrfCookie := &http.Cookie{
		Name:     CSRFCookieName,
		Value:    csrf,
		Path:     "/",
		Domain:   domain,
		HttpOnly: false,
		Secure:   secure,
		SameSite: http.SameSiteLaxMode,
	}
	if remember {
		exp := issued.ExpiresAt.UTC()
		maxAge := int(exp.Sub(s.now().UTC()).Seconds())
		if maxAge < 1 {
			maxAge = 1
		}
		sessionCookie.Expires = exp
		sessionCookie.MaxAge = maxAge
		csrfCookie.Expires = exp
		csrfCookie.MaxAge = maxAge
	}
	c.SetCookie(sessionCookie)
	c.SetCookie(csrfCookie)
	c.Response().Header().Set(CSRFHeaderName, csrf)
}

// ClearSessionCookies elimina las cookies de sesión y CSRF en el navegador.
func ClearSessionCookies(c echo.Context) {
	secure := isSecureRequest(c)
	domain := cookieDomain(c)
	expired := time.Unix(0, 0).UTC()
	c.SetCookie(&http.Cookie{
		Name:     SessionCookieName,
		Value:    "",
		Path:     "/",
		Domain:   domain,
		Expires:  expired,
		MaxAge:   -1,
		HttpOnly: true,
		Secure:   secure,
		SameSite: http.SameSiteLaxMode,
	})
	c.SetCookie(&http.Cookie{
		Name:     CSRFCookieName,
		Value:    "",
		Path:     "/",
		Domain:   domain,
		Expires:  expired,
		MaxAge:   -1,
		HttpOnly: false,
		Secure:   secure,
		SameSite: http.SameSiteLaxMode,
	})
}

func isSecureRequest(c echo.Context) bool {
	return c.Scheme() == "https" || strings.EqualFold(c.Request().Header.Get("X-Forwarded-Proto"), "https")
}

func cookieDomain(c echo.Context) string {
	host := c.Request().Host
	if idx := strings.IndexByte(host, ':'); idx != -1 {
		host = host[:idx]
	}
	if host == "smartpot.app" || strings.HasSuffix(host, ".smartpot.app") {
		return "smartpot.app"
	}
	return ""
}

// Guard devuelve el middleware que exige una sesión válida en cookie HttpOnly (con verificación CSRF en escrituras)
// o un token Bearer (para conexiones personales spt_) e inyecta la identidad tipada en el RequestContext.
func (s *TokenService) Guard() echo.MiddlewareFunc {
	return func(next echo.HandlerFunc) echo.HandlerFunc {
		return func(c echo.Context) error {
			req := c.Request()
			header := req.Header.Get("Authorization")
			var raw string
			fromCookie := false
			if header != "" {
				if !strings.HasPrefix(header, "Bearer ") {
					return apperr.Unauthorized("Please log in to continue")
				}
				raw = strings.TrimSpace(strings.TrimPrefix(header, "Bearer "))
				if s.connections != nil && strings.HasPrefix(raw, connectionTokenPrefix) {
					return s.guardConnection(c, raw, next)
				}
			} else {
				cookie, err := c.Cookie(SessionCookieName)
				if err != nil || strings.TrimSpace(cookie.Value) == "" {
					return apperr.Unauthorized("Please log in to continue")
				}
				raw = strings.TrimSpace(cookie.Value)
				fromCookie = true
			}
			claims, err := s.Parse(raw)
			if err != nil {
				return apperr.Unauthorized("Your session is invalid or has expired. Please log in again")
			}
			if fromCookie {
				expectedCSRF := s.CSRFToken(raw)
				c.Response().Header().Set(CSRFHeaderName, expectedCSRF)
				if isUnsafeMethod(req.Method) {
					if err := verifyCSRF(c, expectedCSRF); err != nil {
						return err
					}
				} else if csrfCookie, err := c.Cookie(CSRFCookieName); err != nil || csrfCookie.Value != expectedCSRF {
					c.SetCookie(&http.Cookie{
						Name:     CSRFCookieName,
						Value:    expectedCSRF,
						Path:     "/",
						Domain:   cookieDomain(c),
						HttpOnly: false,
						Secure:   isSecureRequest(c),
						SameSite: http.SameSiteLaxMode,
					})
				}
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

func isUnsafeMethod(method string) bool {
	return method != http.MethodGet && method != http.MethodHead && method != http.MethodOptions
}

func verifyCSRF(c echo.Context, expected string) error {
	csrfCookie, err := c.Cookie(CSRFCookieName)
	if err != nil || strings.TrimSpace(csrfCookie.Value) == "" {
		return apperr.Forbidden("Request rejected by CSRF protection")
	}
	cookieVal := strings.TrimSpace(csrfCookie.Value)
	headerVal := strings.TrimSpace(c.Request().Header.Get(CSRFHeaderName))
	if headerVal == "" {
		return apperr.Forbidden("Request rejected by CSRF protection")
	}
	if subtle.ConstantTimeCompare([]byte(cookieVal), []byte(headerVal)) != 1 ||
		subtle.ConstantTimeCompare([]byte(headerVal), []byte(expected)) != 1 {
		return apperr.Forbidden("Request rejected by CSRF protection")
	}
	return nil
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
		return apperr.Forbidden("Connections can only be managed from the application")
	case strings.HasPrefix(path, "/api/v1/users/me") && !read:
		return apperr.Forbidden("Account details can only be changed from the application")
	case strings.HasPrefix(path, "/api/v1/channels") && !read:
		return apperr.Forbidden("Notification channels can only be configured from the application")
	case strings.HasPrefix(path, "/api/v1/crops/") && hasSegment(path, "channels"):
		// Los canales por cultivo exponen datos de otras cuentas y no forman
		// parte del alcance de una conexión spt_ (M-09).
		return apperr.Forbidden("Crop notification channels can only be managed from the application")
	case hasSegment(path, "device"):
		return apperr.Forbidden("Device credentials can only be retrieved from the application")
	case scope != "CONTROL" && !read:
		return apperr.Forbidden("This connection is read-only")
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
				return apperr.Forbidden("You do not have permission to perform this action")
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
