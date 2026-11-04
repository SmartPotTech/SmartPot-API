package validation

import (
	"regexp"
	"testing"
)

var personName = regexp.MustCompile(`^[\p{L}][\p{L} '\-]*$`)

func TestFirstErrorPerFieldWins(t *testing.T) {
	// Nombre vacío: NotBlank va primero y debe ganar sobre Size y Pattern.
	v := New().
		NotBlank("name", "", "Name is required").
		Size("name", "", 2, 40, "Name must be between 2 and 40 characters").
		Pattern("name", "", personName, "El nombre solo puede tener letras y espacios")
	if got := v.Result()["name"]; got != "Name is required" {
		t.Errorf("name = %q, se esperaba el mensaje de NotBlank", got)
	}
}

func TestValidInputHasNoErrors(t *testing.T) {
	v := New().
		NotBlank("name", "María José", "x").
		Size("name", "María José", 2, 40, "x").
		Pattern("name", "María José", personName, "x").
		Email("email", "a@b.com", "y")
	if !v.OK() {
		t.Errorf("no debía haber errores: %v", v.Result())
	}
}

func TestChecks(t *testing.T) {
	if New().Email("email", "not-an-email", "mal").OK() {
		t.Error("an invalid email should fail")
	}
	if !New().Email("email", "", "mal").OK() {
		t.Error("an empty email should be valid (covered by NotBlank)")
	}
	if New().Pattern("name", "A1", personName, "mal").OK() {
		t.Error("un nombre con dígitos debía fallar el patrón")
	}
	if New().MaxSize("x", "abcdef", 3, "muy largo").OK() {
		t.Error("MaxSize debía fallar")
	}
}
