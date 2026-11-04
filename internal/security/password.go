package security

import (
	"unicode"
	"unicode/utf8"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"golang.org/x/crypto/bcrypt"
)

// bcryptCost es el costo de BCrypt (12), el mismo de la versión en Java. Los
// hashes son interoperables: Go escribe el prefijo "$2a$", que es el de Spring.
const bcryptCost = 12

// HashPassword cifra la contraseña con BCrypt.
func HashPassword(plain string) (string, error) {
	hash, err := bcrypt.GenerateFromPassword([]byte(plain), bcryptCost)
	return string(hash), err
}

// PasswordMatches compara una contraseña con su hash BCrypt.
func PasswordMatches(plain, hash string) bool {
	return bcrypt.CompareHashAndPassword([]byte(hash), []byte(plain)) == nil
}

// ValidatePassword aplica la política: mínimo 8 caracteres, máximo 72 bytes
// (límite de BCrypt) y al menos una mayúscula, una minúscula y un número.
func ValidatePassword(password string) error {
	if utf8.RuneCountInString(password) < 8 {
		return apperr.BadRequest("Password must be at least 8 characters")
	}
	if len(password) > 72 {
		return apperr.BadRequest("Password is too long (maximum 72 bytes)")
	}
	var upper, lower, digit bool
	for _, r := range password {
		switch {
		case unicode.IsUpper(r):
			upper = true
		case unicode.IsLower(r):
			lower = true
		case unicode.IsDigit(r):
			digit = true
		}
	}
	if !upper || !lower || !digit {
		return apperr.BadRequest("Password must contain at least one uppercase letter, one lowercase letter, and one number")
	}
	return nil
}
