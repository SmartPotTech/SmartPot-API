package security

import (
	"strings"
	"testing"

	"golang.org/x/crypto/bcrypt"
)

func TestValidatePassword(t *testing.T) {
	valid := []string{"Abcdef12", "Clave2026", "aA1xxxxx"}
	for _, p := range valid {
		if err := ValidatePassword(p); err != nil {
			t.Errorf("ValidatePassword(%q) debía pasar: %v", p, err)
		}
	}
	invalid := map[string]string{
		"corto1A":  "menos de 8",
		"abcdef12": "sin mayúscula",
		"ABCDEF12": "sin minúscula",
		"Abcdefgh": "sin número",
	}
	for p, reason := range invalid {
		if err := ValidatePassword(p); err == nil {
			t.Errorf("ValidatePassword(%q) debía fallar (%s)", p, reason)
		}
	}
	if err := ValidatePassword("Aa1" + strings.Repeat("x", 70)); err == nil {
		t.Error("password over 72 bytes should fail")
	}
}

// TestBCryptSpringVector verifica que Go lee un hash "$2a$12$" producido por
// jBCrypt (la base de BCryptPasswordEncoder de Spring). Vector del conjunto de
// pruebas de jBCrypt.
func TestBCryptSpringVector(t *testing.T) {
	const password = "abc"
	const hash = "$2a$12$EXRkfkdmXn2gzds2SSitu.MW9.gAVqa9eLS1//RYtYCmB1eLHg.9q"

	if cost, err := bcrypt.Cost([]byte(hash)); err != nil || cost != 12 {
		t.Fatalf("Cost = %d, err = %v; se esperaba 12", cost, err)
	}
	if !PasswordMatches(password, hash) {
		t.Error("PasswordMatches debía validar el hash de Spring")
	}
	if PasswordMatches("wrong-password", hash) {
		t.Error("PasswordMatches should not validate wrong password")
	}
}

func TestHashPasswordRoundTrip(t *testing.T) {
	hash, err := HashPassword("Abcdef12")
	if err != nil {
		t.Fatalf("HashPassword: %v", err)
	}
	if !strings.HasPrefix(hash, "$2a$12$") {
		t.Errorf("el hash debía empezar por $2a$12$: %q", hash)
	}
	if !PasswordMatches("Abcdef12", hash) {
		t.Error("PasswordMatches debía validar el hash recién creado")
	}
}
