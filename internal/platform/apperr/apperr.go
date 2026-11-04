// Package apperr define los errores de negocio y la forma de la respuesta de
// error de la API, idéntica a la de la versión en Java (status, error, message,
// path, timestamp, fields) con mensajes en español.
package apperr

import (
	"net/http"
	"time"
)

// Error es un error de negocio con el código HTTP que le corresponde.
type Error struct {
	Status  int
	Message string
}

func (e *Error) Error() string { return e.Message }

// New crea un error con un código y mensaje arbitrarios.
func New(status int, message string) *Error {
	return &Error{Status: status, Message: message}
}

func NotFound(message string) *Error     { return New(http.StatusNotFound, message) }
func BadRequest(message string) *Error   { return New(http.StatusBadRequest, message) }
func Conflict(message string) *Error     { return New(http.StatusConflict, message) }
func Unauthorized(message string) *Error { return New(http.StatusUnauthorized, message) }
func Forbidden(message string) *Error    { return New(http.StatusForbidden, message) }
func Unavailable(message string) *Error  { return New(http.StatusServiceUnavailable, message) }
func Internal(message string) *Error     { return New(http.StatusInternalServerError, message) }

// ValidationError representa un fallo de validación de campos de entrada (400)
// que viaja como error tipado hasta el HTTPErrorHandler centralizado.
type ValidationError struct {
	Message string
	Fields  map[string]string
}

func (e *ValidationError) Error() string { return e.Message }

// Validation construye un error de validación 400 con el detalle por campo.
func Validation(fields map[string]string) *ValidationError {
	return &ValidationError{
		Message: "Invalid input data",
		Fields:  fields,
	}
}

// Response es el cuerpo JSON de un error. Jackson omite los campos vacíos
// (NON_EMPTY): aquí se replica con omitempty en path y fields.
type Response struct {
	Status    int               `json:"status"`
	Error     string            `json:"error"`
	Message   string            `json:"message"`
	Path      string            `json:"path,omitempty"`
	Timestamp time.Time         `json:"timestamp"`
	Fields    map[string]string `json:"fields,omitempty"`
}

// NewResponse arma la respuesta de error. El campo "error" usa la frase estándar
// del código HTTP (http.StatusText), equivalente a HttpStatus.getReasonPhrase().
func NewResponse(status int, message, path string) Response {
	return Response{
		Status:    status,
		Error:     http.StatusText(status),
		Message:   message,
		Path:      path,
		Timestamp: time.Now().UTC(),
	}
}

// NewValidationResponse arma la respuesta 400 con el detalle por campo.
func NewValidationResponse(message, path string, fields map[string]string) Response {
	r := NewResponse(http.StatusBadRequest, message, path)
	r.Fields = fields
	return r
}
