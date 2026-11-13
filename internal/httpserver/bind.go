package httpserver

import (
	"encoding/json"
	"errors"
	"io"
	"net/http"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/validation"
	"github.com/labstack/echo/v4"
)

const maxJSONBodyBytes = 1 << 20

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

// DecodeJSON decodifica un único objeto JSON en dst con límite de 1 MiB. Si el
// cuerpo excede el límite devuelve 413; si es inválido o trae datos extra, 400.
func DecodeJSON(c echo.Context, dst any) error {
	req := c.Request()
	if req.Body == nil {
		return apperr.BadRequest("Request body is not valid JSON")
	}
	body := http.MaxBytesReader(c.Response(), req.Body, maxJSONBodyBytes)
	dec := json.NewDecoder(body)
	if err := dec.Decode(dst); err != nil {
		var maxErr *http.MaxBytesError
		if errors.As(err, &maxErr) {
			return apperr.New(http.StatusRequestEntityTooLarge, "Request body exceeds maximum allowed size")
		}
		return apperr.BadRequest("Request body is not valid JSON")
	}
	var extra any
	if err := dec.Decode(&extra); !errors.Is(err, io.EOF) {
		var maxErr *http.MaxBytesError
		if errors.As(err, &maxErr) {
			return apperr.New(http.StatusRequestEntityTooLarge, "Request body exceeds maximum allowed size")
		}
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
