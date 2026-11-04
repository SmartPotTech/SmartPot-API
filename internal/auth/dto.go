// Package auth implementa el registro, el inicio de sesión y la recuperación de
// contraseña, emitiendo tokens JWT compatibles con la versión en Java.
package auth

import (
	"regexp"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/validation"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/security"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/users"
)

// personName admite letras (con tildes y ñ), espacios, apóstrofo y guion.
var personName = regexp.MustCompile(`^[\p{L}][\p{L} '\-]*$`)

// RegisterRequest crea una cuenta.
type RegisterRequest struct {
	Name     string `json:"name"`
	LastName string `json:"lastName"`
	Email    string `json:"email"`
	Password string `json:"password"`
}

// Validate aplica las mismas reglas y mensajes que la versión en Java.
func (r RegisterRequest) Validate() validation.Fields {
	return validation.New().
		NotBlank("name", r.Name, "Name is required").
		Size("name", r.Name, 2, 40, "Name must be between 2 and 40 characters").
		Pattern("name", r.Name, personName, "Name can only contain letters and spaces").
		NotBlank("lastName", r.LastName, "Last name is required").
		Size("lastName", r.LastName, 2, 60, "Last name must be between 2 and 60 characters").
		Pattern("lastName", r.LastName, personName, "Last name can only contain letters and spaces").
		NotBlank("email", r.Email, "Email is required").
		Email("email", r.Email, "Invalid email address").
		MaxSize("email", r.Email, 254, "Email is too long").
		NotBlank("password", r.Password, "Password is required").
		Result()
}

// LoginRequest inicia sesión.
type LoginRequest struct {
	Email    string `json:"email"`
	Password string `json:"password"`
	Remember *bool  `json:"remember,omitempty"`
}

// RememberMe indica si la cookie de sesión debe persistir hasta la expiración del JWT.
func (r LoginRequest) RememberMe() bool {
	return r.Remember == nil || *r.Remember
}

// Validate valida el inicio de sesión.
func (r LoginRequest) Validate() validation.Fields {
	return validation.New().
		NotBlank("email", r.Email, "Email is required").
		MaxSize("email", r.Email, 254, "Email is too long").
		NotBlank("password", r.Password, "Password is required").
		MaxSize("password", r.Password, 200, "Password is too long").
		Result()
}

// ForgotPasswordRequest solicita el enlace de recuperación.
type ForgotPasswordRequest struct {
	Email string `json:"email"`
}

// Validate valida la solicitud de recuperación.
func (r ForgotPasswordRequest) Validate() validation.Fields {
	return validation.New().
		NotBlank("email", r.Email, "Email is required").
		Email("email", r.Email, "Invalid email address").
		Result()
}

// ResetPasswordRequest restablece la contraseña con el enlace recibido.
type ResetPasswordRequest struct {
	Token    string `json:"token"`
	Password string `json:"password"`
}

// Validate valida el restablecimiento.
func (r ResetPasswordRequest) Validate() validation.Fields {
	return validation.New().
		NotBlank("token", r.Token, "Password reset link is invalid").
		MaxSize("token", r.Token, 200, "Password reset link is invalid").
		NotBlank("password", r.Password, "New password is required").
		Result()
}

// AuthResponse es la respuesta de registro, inicio de sesión y consulta de sesión.
// El token JWT viaja exclusivamente en la cookie HttpOnly smartpot_session.
type AuthResponse struct {
	Issued    security.IssuedToken `json:"-"`
	ExpiresAt time.Time            `json:"expiresAt"`
	User      users.UserResponse   `json:"user"`
}
