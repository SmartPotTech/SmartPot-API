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

// Service implementa la lógica de la cuenta.
type Service struct {
	repo     Repository
	clock    func() time.Time
	deleters []AccountDeleter
}

// NewService crea el servicio de usuarios.
func NewService(repo Repository, clock func() time.Time) *Service {
	return &Service{repo: repo, clock: clock}
}

// AddAccountDeleter registra un borrador de datos para el borrado en cascada.
func (s *Service) AddAccountDeleter(d AccountDeleter) {
	s.deleters = append(s.deleters, d)
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
	hash, err := security.HashPassword(req.NewPassword)
	if err != nil {
		return err
	}
	u.PasswordHash = hash
	u.UpdatedAt = s.clock()
	return s.repo.Update(ctx, u)
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
	return s.repo.Delete(ctx, u.ID)
}
