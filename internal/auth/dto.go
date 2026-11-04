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
		NotBlank("name", r.Name, "El nombre es obligatorio").
		Size("name", r.Name, 2, 40, "El nombre debe tener entre 2 y 40 caracteres").
		Pattern("name", r.Name, personName, "El nombre solo puede tener letras y espacios").
		NotBlank("lastName", r.LastName, "El apellido es obligatorio").
		Size("lastName", r.LastName, 2, 60, "El apellido debe tener entre 2 y 60 caracteres").
		Pattern("lastName", r.LastName, personName, "El apellido solo puede tener letras y espacios").
		NotBlank("email", r.Email, "El correo es obligatorio").
		Email("email", r.Email, "El correo no es válido").
		MaxSize("email", r.Email, 254, "El correo es demasiado largo").
		NotBlank("password", r.Password, "La contraseña es obligatoria").
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
		NotBlank("email", r.Email, "El correo es obligatorio").
		MaxSize("email", r.Email, 254, "El correo es demasiado largo").
		NotBlank("password", r.Password, "La contraseña es obligatoria").
		MaxSize("password", r.Password, 200, "La contraseña es demasiado larga").
		Result()
}

// ForgotPasswordRequest solicita el enlace de recuperación.
type ForgotPasswordRequest struct {
	Email string `json:"email"`
}

// Validate valida la solicitud de recuperación.
func (r ForgotPasswordRequest) Validate() validation.Fields {
	return validation.New().
		NotBlank("email", r.Email, "El correo es obligatorio").
		Email("email", r.Email, "El correo no es válido").
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
		NotBlank("token", r.Token, "El enlace de recuperación no es válido").
		MaxSize("token", r.Token, 200, "El enlace de recuperación no es válido").
		NotBlank("password", r.Password, "La nueva contraseña es obligatoria").
		Result()
}

// AuthResponse es la respuesta de registro, inicio de sesión y consulta de sesión.
// El token JWT viaja exclusivamente en la cookie HttpOnly smartpot_session.
type AuthResponse struct {
	Issued    security.IssuedToken `json:"-"`
	ExpiresAt time.Time            `json:"expiresAt"`
	User      users.UserResponse   `json:"user"`
}
