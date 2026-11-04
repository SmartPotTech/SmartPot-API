package users

import (
	"regexp"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/validation"
)

// personName admite letras (con tildes y ñ), espacios, apóstrofo y guion.
var personName = regexp.MustCompile(`^[\p{L}][\p{L} '\-]*$`)

// UserResponse es la vista pública de una cuenta.
type UserResponse struct {
	ID        string    `json:"id"`
	Name      string    `json:"name"`
	LastName  string    `json:"lastName"`
	Email     string    `json:"email"`
	Role      string    `json:"role"`
	CreatedAt time.Time `json:"createdAt"`
}

// ToResponse mapea la entidad a su vista pública.
func ToResponse(u *User) UserResponse {
	return UserResponse{
		ID:        u.HexID(),
		Name:      u.Name,
		LastName:  u.LastName,
		Email:     u.Email,
		Role:      string(u.Role),
		CreatedAt: u.CreatedAt,
	}
}

// UpdateProfileRequest actualiza el nombre y el apellido.
type UpdateProfileRequest struct {
	Name     string `json:"name"`
	LastName string `json:"lastName"`
}

// Validate aplica las mismas reglas y mensajes que la versión en Java.
func (r UpdateProfileRequest) Validate() validation.Fields {
	return validation.New().
		NotBlank("name", r.Name, "Name is required").
		Size("name", r.Name, 2, 40, "Name must be between 2 and 40 characters").
		Pattern("name", r.Name, personName, "Name can only contain letters and spaces").
		NotBlank("lastName", r.LastName, "Last name is required").
		Size("lastName", r.LastName, 2, 60, "Last name must be between 2 and 60 characters").
		Pattern("lastName", r.LastName, personName, "Last name can only contain letters and spaces").
		Result()
}

// ChangePasswordRequest cambia la contraseña de la cuenta.
type ChangePasswordRequest struct {
	CurrentPassword string `json:"currentPassword"`
	NewPassword     string `json:"newPassword"`
}

// Validate exige que ambos campos estén presentes (la política se aplica aparte).
func (r ChangePasswordRequest) Validate() validation.Fields {
	return validation.New().
		NotBlank("currentPassword", r.CurrentPassword, "Current password is required").
		NotBlank("newPassword", r.NewPassword, "New password is required").
		Result()
}
