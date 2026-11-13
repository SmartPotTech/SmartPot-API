package security

import (
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"strconv"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/cache"
	"github.com/golang-jwt/jwt/v5"
)

const (
	// JWTIssuer identifica los tokens de la API; se valida al parsear.
	JWTIssuer  = "smartpot-api"
	roleClaim  = "role"
	emailClaim = "email"
	jtiClaim   = "jti"
	verClaim   = "ver"

	revokedKey        = "jwt:revoked:"
	userVersionKey    = "jwt:user-version:"
	sessionVersionTTL = 8 * 24 * time.Hour

	// SessionCookieName es la cookie HttpOnly que transporta el JWT de la sesión.
	SessionCookieName = "smartpot_session"
	// CSRFCookieName es la cookie de doble envío vinculada al JWT de la sesión.
	CSRFCookieName = "smartpot_csrf"
	// CSRFHeaderName es la cabecera que debe coincidir con CSRFCookieName en peticiones de escritura.
	CSRFHeaderName = "X-CSRF-Token"
)

// TokenService emite y valida tokens JWT HS256. Usa el mismo secreto, issuer y
// claims que la versión en Java, por lo que los tokens son interoperables.
type TokenService struct {
	secret     []byte
	expiration time.Duration
	now        func() time.Time
	// connections, si existe, permite que el guard acepte tokens de conexión.
	connections ConnectionAuthenticator
	// sessions, si existe, permite revocar un jti (logout) y desconocer tokens
	// emitidos antes de cambiar la contraseña (versión por usuario).
	sessions cache.Cache
}

// NewTokenService crea el servicio con el secreto y la vigencia del token.
func NewTokenService(secret string, expiration time.Duration) *TokenService {
	return &TokenService{secret: []byte(secret), expiration: expiration, now: time.Now}
}

// IssuedToken es el token firmado y su fecha de expiración.
type IssuedToken struct {
	Value     string
	ExpiresAt time.Time
}

// UseSessionStore habilita la revocación server-side con Valkey.
func (s *TokenService) UseSessionStore(store cache.Cache) {
	s.sessions = store
}

// Issue firma un token para el usuario con un jti único y la versión de sesión
// vigente. Si cambia la contraseña, la versión sube y los tokens anteriores dejan
// de valer aunque su firma y expiración sean correctas.
func (s *TokenService) Issue(userID, email, role string) (IssuedToken, error) {
	now := s.now().UTC().Truncate(time.Second)
	exp := now.Add(s.expiration)
	jti, err := randomHex(16)
	if err != nil {
		return IssuedToken{}, err
	}
	claims := jwt.MapClaims{
		"iss":      JWTIssuer,
		"sub":      userID,
		"iat":      now.Unix(),
		"exp":      exp.Unix(),
		jtiClaim:   jti,
		verClaim:   s.userVersion(userID),
		emailClaim: email,
		roleClaim:  role,
	}
	signed, err := jwt.NewWithClaims(jwt.SigningMethodHS256, claims).SignedString(s.secret)
	if err != nil {
		return IssuedToken{}, err
	}
	return IssuedToken{Value: signed, ExpiresAt: exp}, nil
}

// CSRFToken deriva el token CSRF vinculado con HMAC-SHA256 al JWT de la sesión.
func (s *TokenService) CSRFToken(sessionToken string) string {
	mac := hmac.New(sha256.New, s.secret)
	mac.Write([]byte("csrf:"))
	mac.Write([]byte(sessionToken))
	sum := mac.Sum(nil)
	return hex.EncodeToString(sum[:16])
}

// Claims son los datos que la API lee del token.
type Claims struct {
	UserID    string
	Email     string
	Role      string
	JTI       string
	Version   int
	IssuedAt  time.Time
	ExpiresAt time.Time
}

// Parse valida la firma, el algoritmo (HS256), el issuer y la expiración.
func (s *TokenService) Parse(tokenString string) (Claims, error) {
	parsed, err := jwt.Parse(tokenString, func(*jwt.Token) (any, error) {
		return s.secret, nil
	},
		jwt.WithValidMethods([]string{"HS256"}),
		jwt.WithIssuer(JWTIssuer),
		jwt.WithExpirationRequired(),
	)
	if err != nil || !parsed.Valid {
		return Claims{}, errors.New("invalid token")
	}
	claims, ok := parsed.Claims.(jwt.MapClaims)
	if !ok {
		return Claims{}, errors.New("invalid token")
	}
	sub, _ := claims["sub"].(string)
	if sub == "" {
		return Claims{}, errors.New("token without subject")
	}
	email, _ := claims[emailClaim].(string)
	role, _ := claims[roleClaim].(string)
	jti, _ := claims[jtiClaim].(string)
	version := 0
	if raw, ok := claims[verClaim].(float64); ok {
		version = int(raw)
	}
	// Revocación server-side: un jti cerrado en logout o una versión anterior a
	// un cambio de contraseña se rechaza con 401.
	if s.sessions != nil {
		if jti != "" {
			if _, revoked := s.sessions.Get(revokedKey + jti); revoked {
				return Claims{}, errors.New("token revoked")
			}
		}
		if version != s.userVersion(sub) {
			return Claims{}, errors.New("token version is no longer valid")
		}
	}
	var iatTime, expTime time.Time
	if iat, err := claims.GetIssuedAt(); err == nil && iat != nil {
		iatTime = iat.UTC()
	}
	if exp, err := claims.GetExpirationTime(); err == nil && exp != nil {
		expTime = exp.UTC()
	}
	return Claims{
		UserID:    sub,
		Email:     email,
		Role:      role,
		JTI:       jti,
		Version:   version,
		IssuedAt:  iatTime,
		ExpiresAt: expTime,
	}, nil
}

// Revoke invalida el token presentado hasta su expiración natural (logout).
func (s *TokenService) Revoke(tokenString string) {
	if s.sessions == nil || tokenString == "" {
		return
	}
	claims, err := s.Parse(tokenString)
	if err != nil || claims.JTI == "" {
		return
	}
	ttl := claims.ExpiresAt.Sub(s.now())
	if ttl <= 0 {
		return
	}
	s.sessions.Put(revokedKey+claims.JTI, "1", ttl)
}

// BumpUserVersion invalida todos los tokens emitidos para el usuario. Se llama
// al cambiar o restablecer la contraseña.
func (s *TokenService) BumpUserVersion(userID string) {
	if s.sessions == nil || userID == "" {
		return
	}
	next := s.userVersion(userID) + 1
	ttl := s.expiration + time.Hour
	if ttl < sessionVersionTTL {
		ttl = sessionVersionTTL
	}
	s.sessions.Put(userVersionKey+userID, strconv.Itoa(next), ttl)
}

// deletedUserVersion marca cuentas eliminadas para seguir rechazando sus tokens en caché.
const (
	deletedUserVersion = -1
	deletedUserValue   = "-1"
)

// ForgetUser marca como eliminada la versión de sesión de la cuenta para que los
// JWT ya emitidos sigan rechazándose hasta expirar sin conservar datos personales.
func (s *TokenService) ForgetUser(userID string) {
	if s.sessions == nil || userID == "" {
		return
	}
	ttl := sessionVersionTTL
	if s.expiration > ttl {
		ttl = s.expiration
	}
	s.sessions.Put(userVersionKey+userID, deletedUserValue, ttl)
}

func (s *TokenService) userVersion(userID string) int {
	if s.sessions == nil || userID == "" {
		return 0
	}
	raw, ok := s.sessions.Get(userVersionKey + userID)
	if !ok {
		return 0
	}
	if raw == deletedUserValue {
		return deletedUserVersion
	}
	version, err := strconv.Atoi(raw)
	if err != nil {
		return 0
	}
	return version
}

func randomHex(size int) (string, error) {
	buf := make([]byte, size)
	if _, err := rand.Read(buf); err != nil {
		return "", err
	}
	return hex.EncodeToString(buf), nil
}
