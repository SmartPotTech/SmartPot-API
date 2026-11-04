package security

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

const (
	// JWTIssuer identifica los tokens de la API; se valida al parsear.
	JWTIssuer  = "smartpot-api"
	roleClaim  = "role"
	emailClaim = "email"

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

// Issue firma un token para el usuario.
func (s *TokenService) Issue(userID, email, role string) (IssuedToken, error) {
	now := s.now().UTC().Truncate(time.Second)
	exp := now.Add(s.expiration)
	claims := jwt.MapClaims{
		"iss":      JWTIssuer,
		"sub":      userID,
		"iat":      now.Unix(),
		"exp":      exp.Unix(),
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
		IssuedAt:  iatTime,
		ExpiresAt: expTime,
	}, nil
}
