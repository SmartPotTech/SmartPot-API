package auth

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"strings"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/mail"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/notifications"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/security"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/users"
	"github.com/labstack/echo/v4"
)

const (
	invalidCredentials = "Incorrect email or password"
	invalidReset       = "Password reset link is invalid or has expired"
)

// Service implementa el registro, el inicio de sesión y la recuperación.
type Service struct {
	users           users.Repository
	tokens          TokenRepository
	jwt             *security.TokenService
	mailer          *mail.Mailer
	notifier        *notifications.Service
	clock           func() time.Time
	resetExpiration time.Duration
	dummyHash       string
}

// NewService crea el servicio de autenticación.
func NewService(userRepo users.Repository, tokenRepo TokenRepository, jwt *security.TokenService,
	mailer *mail.Mailer, notifier *notifications.Service, clock func() time.Time, resetExpiration time.Duration) *Service {
	// Hash de relevo para que el login tarde lo mismo exista o no la cuenta.
	dummy, _ := security.HashPassword("smartpot-timing-guard")
	return &Service{
		users:           userRepo,
		tokens:          tokenRepo,
		jwt:             jwt,
		mailer:          mailer,
		notifier:        notifier,
		clock:           clock,
		resetExpiration: resetExpiration,
		dummyHash:       dummy,
	}
}

// Register crea una cuenta y devuelve un token listo para usar.
func (s *Service) Register(ctx context.Context, req RegisterRequest) (AuthResponse, error) {
	email := users.NormalizeEmail(req.Email)
	if err := security.ValidatePassword(req.Password); err != nil {
		return AuthResponse{}, err
	}
	exists, err := s.users.ExistsByEmail(ctx, email)
	if err != nil {
		return AuthResponse{}, err
	}
	if exists {
		return AuthResponse{}, apperr.Conflict("An account with this email already exists")
	}
	hash, err := security.HashPassword(req.Password)
	if err != nil {
		return AuthResponse{}, err
	}
	now := s.clock()
	u := &users.User{
		Name:         strings.TrimSpace(req.Name),
		LastName:     strings.TrimSpace(req.LastName),
		Email:        email,
		PasswordHash: hash,
		Role:         users.RoleUser,
		CreatedAt:    now,
		UpdatedAt:    now,
	}
	if err := s.users.Insert(ctx, u); err != nil {
		if users.IsDuplicate(err) {
			return AuthResponse{}, apperr.Conflict("An account with this email already exists")
		}
		return AuthResponse{}, err
	}
	if _, err := s.notifier.Notify(ctx, u.HexID(), "", notifications.TypeInfo, "Welcome to SmartPot!",
		"Create your first crop, real or virtual, to start monitoring it."); err != nil {
		return AuthResponse{}, err
	}
	s.mailer.SendWelcome(u.Name, u.Email)
	return s.authResponse(u)
}

// Login valida las credenciales y devuelve un token.
func (s *Service) Login(ctx context.Context, req LoginRequest) (AuthResponse, error) {
	email := users.NormalizeEmail(req.Email)
	u, err := s.users.FindByEmail(ctx, email)
	if err != nil {
		return AuthResponse{}, err
	}
	if u == nil {
		// Comparación de relevo para no filtrar si el correo existe.
		security.PasswordMatches(req.Password, s.dummyHash)
		return AuthResponse{}, apperr.Unauthorized(invalidCredentials)
	}
	if !security.PasswordMatches(req.Password, u.PasswordHash) {
		return AuthResponse{}, apperr.Unauthorized(invalidCredentials)
	}
	return s.authResponse(u)
}

// RequestPasswordReset genera y envía el enlace si la cuenta existe (siempre 202).
func (s *Service) RequestPasswordReset(ctx context.Context, rawEmail string) error {
	email := users.NormalizeEmail(rawEmail)
	u, err := s.users.FindByEmail(ctx, email)
	if err != nil {
		return err
	}
	if u == nil {
		return nil
	}
	token, err := randomToken()
	if err != nil {
		return err
	}
	now := s.clock()
	if err := s.tokens.DeleteByUserID(ctx, u.HexID()); err != nil {
		return err
	}
	if err := s.tokens.Insert(ctx, &PasswordResetToken{
		TokenHash: sha256Hex(token),
		UserID:    u.ID,
		CreatedAt: now,
		ExpiresAt: now.Add(s.resetExpiration),
	}); err != nil {
		return err
	}
	s.mailer.SendPasswordReset(u.Name, u.Email, token, s.resetExpiration)
	return nil
}

// ResetPassword cambia la contraseña con el enlace recibido por correo.
func (s *Service) ResetPassword(ctx context.Context, req ResetPasswordRequest) error {
	t, err := s.tokens.FindByTokenHash(ctx, sha256Hex(req.Token))
	if err != nil {
		return err
	}
	if t == nil || !t.ExpiresAt.After(s.clock()) {
		return apperr.BadRequest(invalidReset)
	}
	if err := security.ValidatePassword(req.Password); err != nil {
		return err
	}
	u, err := s.users.FindByID(ctx, t.UserID.Hex())
	if err != nil {
		return err
	}
	if u == nil {
		return apperr.BadRequest(invalidReset)
	}
	hash, err := security.HashPassword(req.Password)
	if err != nil {
		return err
	}
	u.PasswordHash = hash
	u.UpdatedAt = s.clock()
	if err := s.users.Update(ctx, u); err != nil {
		return err
	}
	if err := s.tokens.DeleteByUserID(ctx, u.HexID()); err != nil {
		return err
	}
	// Un reset invalida los JWT emitidos antes del cambio (H-04 de Audit_05).
	s.jwt.BumpUserVersion(u.HexID())
	if _, err := s.notifier.Notify(ctx, u.HexID(), "", notifications.TypeInfo, "Password updated",
		"Your password was updated using the recovery link."); err != nil {
		return err
	}
	return nil
}

// Logout revoca el token presentado (jti) y borra las cookies del navegador.
func (s *Service) Logout(c echo.Context) {
	raw := ""
	if header := c.Request().Header.Get("Authorization"); strings.HasPrefix(header, "Bearer ") {
		raw = strings.TrimSpace(strings.TrimPrefix(header, "Bearer "))
	} else if cookie, err := c.Cookie(security.SessionCookieName); err == nil {
		raw = strings.TrimSpace(cookie.Value)
	}
	s.jwt.Revoke(raw)
	security.ClearSessionCookies(c)
}

func (s *Service) authResponse(u *users.User) (AuthResponse, error) {
	issued, err := s.jwt.Issue(u.HexID(), u.Email, string(u.Role))
	if err != nil {
		return AuthResponse{}, err
	}
	return AuthResponse{Issued: issued, ExpiresAt: issued.ExpiresAt, User: users.ToResponse(u)}, nil
}

// SetSessionCookies escribe la cookie HttpOnly con el JWT y el token CSRF en la respuesta HTTP.
func (s *Service) SetSessionCookies(c echo.Context, resp AuthResponse, remember bool) {
	s.jwt.SetSessionCookies(c, resp.Issued, remember)
}

// CurrentSession devuelve los datos vigentes del usuario autenticado y la expiración de su sesión.
func (s *Service) CurrentSession(ctx context.Context, userID string, expiresAt time.Time) (AuthResponse, error) {
	u, err := s.users.FindByID(ctx, userID)
	if err != nil {
		return AuthResponse{}, err
	}
	if u == nil {
		return AuthResponse{}, apperr.Unauthorized("Your session is invalid or has expired. Please log in again")
	}
	return AuthResponse{ExpiresAt: expiresAt, User: users.ToResponse(u)}, nil
}

func randomToken() (string, error) {
	b := make([]byte, 32)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	return base64.RawURLEncoding.EncodeToString(b), nil
}

func sha256Hex(value string) string {
	sum := sha256.Sum256([]byte(value))
	return hex.EncodeToString(sum[:])
}
