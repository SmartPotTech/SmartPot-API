package connections

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"strings"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/security"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

const (
	// MaxPerUser limita las conexiones vivas de una cuenta.
	MaxPerUser = 10
	// touchEvery evita escribir en la base en cada petición solo para actualizar el último uso.
	touchEvery = time.Minute
	// prefixLength es lo que se muestra del token para reconocerlo (spt_ + 8 caracteres).
	prefixLength = len(TokenPrefix) + 8
)

// Service crea, lista, revoca y verifica conexiones.
type Service struct {
	repo  Repository
	clock func() time.Time
}

// NewService crea el servicio de conexiones.
func NewService(repo Repository, clock func() time.Time) *Service {
	return &Service{repo: repo, clock: clock}
}

// Create genera una conexión y devuelve su token, que no se vuelve a mostrar.
func (s *Service) Create(ctx context.Context, userID string, req CreateRequest) (*CreatedResponse, error) {
	uid, err := primitive.ObjectIDFromHex(userID)
	if err != nil {
		return nil, apperr.Unauthorized("Inicia sesión para continuar")
	}
	count, err := s.repo.CountByUser(ctx, uid)
	if err != nil {
		return nil, err
	}
	if count >= MaxPerUser {
		return nil, apperr.Conflict("Llegaste al máximo de 10 conexiones; revoca alguna para crear otra")
	}
	raw, err := newToken()
	if err != nil {
		return nil, err
	}
	now := s.clock()
	t := AccessToken{
		ID:        primitive.NewObjectID(),
		UserID:    uid,
		Name:      strings.TrimSpace(req.Name),
		TokenHash: HashToken(raw),
		Prefix:    raw[:prefixLength],
		Scope:     req.Scope,
		CreatedAt: now,
	}
	if req.ExpiresInDays != nil {
		expires := now.Add(time.Duration(*req.ExpiresInDays) * 24 * time.Hour)
		t.ExpiresAt = &expires
	}
	if err := s.repo.Insert(ctx, &t); err != nil {
		return nil, err
	}
	return &CreatedResponse{Connection: ToResponse(t), Token: raw}, nil
}

// List devuelve las conexiones de la cuenta, de la más nueva a la más antigua.
func (s *Service) List(ctx context.Context, userID string) ([]AccessToken, error) {
	uid, err := primitive.ObjectIDFromHex(userID)
	if err != nil {
		return nil, apperr.Unauthorized("Inicia sesión para continuar")
	}
	return s.repo.ListByUser(ctx, uid)
}

// Revoke borra una conexión de la cuenta; desde ese momento su token deja de servir.
func (s *Service) Revoke(ctx context.Context, userID, id string) error {
	uid, err := primitive.ObjectIDFromHex(userID)
	if err != nil {
		return apperr.Unauthorized("Inicia sesión para continuar")
	}
	oid, err := primitive.ObjectIDFromHex(id)
	if err != nil {
		return apperr.NotFound("La conexión no existe")
	}
	n, err := s.repo.DeleteByIDAndUser(ctx, oid, uid)
	if err != nil {
		return err
	}
	if n == 0 {
		return apperr.NotFound("La conexión no existe")
	}
	return nil
}

// DeleteAllForUser borra las conexiones al eliminar la cuenta.
func (s *Service) DeleteAllForUser(ctx context.Context, userID string) error {
	uid, err := primitive.ObjectIDFromHex(userID)
	if err != nil {
		return nil
	}
	return s.repo.DeleteByUser(ctx, uid)
}

// AuthenticateConnection verifica un token de conexión y devuelve a quién representa y con qué alcance.
func (s *Service) AuthenticateConnection(ctx context.Context, raw string) (security.ConnectionPrincipal, error) {
	invalid := apperr.Unauthorized("El token de conexión no es válido o fue revocado")
	if !strings.HasPrefix(raw, TokenPrefix) || len(raw) < prefixLength {
		return security.ConnectionPrincipal{}, invalid
	}
	t, err := s.repo.FindByHash(ctx, HashToken(raw))
	if err != nil {
		return security.ConnectionPrincipal{}, err
	}
	now := s.clock()
	if t == nil || t.Expired(now) {
		return security.ConnectionPrincipal{}, invalid
	}
	if t.LastUsedAt == nil || now.Sub(*t.LastUsedAt) >= touchEvery {
		// El último uso es informativo: si falla la escritura, la petición sigue.
		_ = s.repo.TouchLastUsed(ctx, t.ID, now)
	}
	return security.ConnectionPrincipal{
		ConnectionID: t.ID.Hex(),
		UserID:       t.UserID.Hex(),
		Scope:        string(t.Scope),
	}, nil
}

// HashToken es el SHA-256 en hexadecimal: el token tiene 256 bits aleatorios, así que no hace falta sal.
func HashToken(raw string) string {
	sum := sha256.Sum256([]byte(raw))
	return hex.EncodeToString(sum[:])
}

func newToken() (string, error) {
	buf := make([]byte, 32)
	if _, err := rand.Read(buf); err != nil {
		return "", err
	}
	return TokenPrefix + base64.RawURLEncoding.EncodeToString(buf), nil
}
