package users

import (
	"context"
	"strings"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/security"
)

// AccountDeleter borra los datos de un dominio al eliminar una cuenta. Cada
// dominio (cultivos, notificaciones, canales…) registra el suyo en el arranque.
type AccountDeleter func(ctx context.Context, userID string) error

// SessionRevoker invalida los JWT emitidos para un usuario (cambio de clave).
type SessionRevoker func(ctx context.Context, userID string) error

// Service implementa la lógica de la cuenta.
type Service struct {
	repo     Repository
	clock    func() time.Time
	deleters []AccountDeleter
	revokers []SessionRevoker
}

// NewService crea el servicio de usuarios.
func NewService(repo Repository, clock func() time.Time) *Service {
	return &Service{repo: repo, clock: clock}
}

// AddAccountDeleter registra un borrador de datos para el borrado en cascada.
func (s *Service) AddAccountDeleter(d AccountDeleter) {
	s.deleters = append(s.deleters, d)
}

// AddSessionRevoker registra el revocador de JWT al cambiar la contraseña.
func (s *Service) AddSessionRevoker(r SessionRevoker) {
	s.revokers = append(s.revokers, r)
}

// NormalizeEmail recorta y pasa a minúsculas el correo.
func NormalizeEmail(email string) string {
	return strings.ToLower(strings.TrimSpace(email))
}

// GetByID devuelve el usuario autenticado; si ya no existe, invita a reingresar.
func (s *Service) GetByID(ctx context.Context, userID string) (*User, error) {
	u, err := s.repo.FindByID(ctx, userID)
	if err != nil {
		return nil, err
	}
	if u == nil {
		return nil, apperr.Unauthorized("Your account no longer exists. Please log in again")
	}
	return u, nil
}

// UpdateProfile cambia el nombre y el apellido.
func (s *Service) UpdateProfile(ctx context.Context, userID string, req UpdateProfileRequest) (*User, error) {
	u, err := s.GetByID(ctx, userID)
	if err != nil {
		return nil, err
	}
	u.Name = strings.TrimSpace(req.Name)
	u.LastName = strings.TrimSpace(req.LastName)
	u.UpdatedAt = s.clock()
	if err := s.repo.Update(ctx, u); err != nil {
		return nil, err
	}
	return u, nil
}

// ChangePassword cambia la contraseña validando la actual y la política.
func (s *Service) ChangePassword(ctx context.Context, userID string, req ChangePasswordRequest) error {
	u, err := s.GetByID(ctx, userID)
	if err != nil {
		return err
	}
	if !security.PasswordMatches(req.CurrentPassword, u.PasswordHash) {
		return apperr.BadRequest("Current password is incorrect")
	}
	if err := security.ValidatePassword(req.NewPassword); err != nil {
		return err
	}
	if security.PasswordMatches(req.NewPassword, u.PasswordHash) {
		return apperr.BadRequest("New password must be different from the current password")
	}
	hash, err := security.HashPassword(req.NewPassword)
	if err != nil {
		return err
	}
	u.PasswordHash = hash
	u.UpdatedAt = s.clock()
	if err := s.repo.Update(ctx, u); err != nil {
		return err
	}
	for _, revoke := range s.revokers {
		if err := revoke(ctx, u.HexID()); err != nil {
			return err
		}
	}
	return nil
}

// DeleteAccount borra la cuenta con todos sus datos (cultivos, lecturas,
// comandos, notificaciones y canales) a través de los borradores registrados.
func (s *Service) DeleteAccount(ctx context.Context, userID string) error {
	u, err := s.GetByID(ctx, userID)
	if err != nil {
		return err
	}
	for _, del := range s.deleters {
		if err := del(ctx, userID); err != nil {
			return err
		}
	}
	// Un token de una cuenta borrada no debe seguir sirviendo durante 7 días.
	for _, revoke := range s.revokers {
		if err := revoke(ctx, u.HexID()); err != nil {
			return err
		}
	}
	return s.repo.Delete(ctx, u.ID)
}
