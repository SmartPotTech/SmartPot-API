package httpserver

import (
	"encoding/json"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/validation"
	"github.com/labstack/echo/v4"
)

// Validatable es una solicitud que sabe validarse. Devuelve el mapa de errores
// por campo (vacío si es válida).
type Validatable interface {
	Validate() validation.Fields
}

// Validator implementa echo.Validator desacoplando la validación de negocio
// de bibliotecas de etiquetas por reflexión acopladas al transporte HTTP.
type Validator struct{}

// NewValidator crea el validador por defecto del motor Echo.
func NewValidator() echo.Validator {
	return Validator{}
}

// Validate ejecuta la validación de dominio de un DTO y devuelve un
// *apperr.ValidationError tipado si hay campos inválidos.
func (Validator) Validate(i any) error {
	if v, ok := i.(Validatable); ok {
		if fields := v.Validate(); len(fields) > 0 {
			return apperr.Validation(fields)
		}
	}
	return nil
}

// DecodeJSON decodifica el cuerpo JSON en dst sin validarlo. Si el JSON es
// inválido devuelve un error de dominio 400 para el HTTPErrorHandler centralizado.
func DecodeJSON(c echo.Context, dst any) error {
	if err := json.NewDecoder(c.Request().Body).Decode(dst); err != nil {
		return apperr.BadRequest("Request body is not valid JSON")
	}
	return nil
}

// Bind decodifica el cuerpo JSON en req y lo valida a través de la interfaz
// intercambiable echo.Validator configurada en el servidor. Devuelve un error
// tipado (*apperr.Error o *apperr.ValidationError) que el manejador propaga con
// return err hacia el HTTPErrorHandler centralizado.
func Bind(c echo.Context, req any) error {
	if err := DecodeJSON(c, req); err != nil {
		return err
	}
	if c.Echo() != nil && c.Echo().Validator != nil {
		return c.Validate(req)
	}
	return Validator{}.Validate(req)
}
