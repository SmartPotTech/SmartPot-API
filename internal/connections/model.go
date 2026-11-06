// Package connections gestiona los tokens de conexión personales: credenciales que una persona crea desde la PWA
// para que un cliente externo (un servidor MCP, un script) use la API en su nombre, con un alcance acotado y sin
// compartir su contraseña. Cada conexión se revoca por separado.
package connections

import (
	"encoding/json"
	"fmt"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/validation"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

// Scope es lo que una conexión puede hacer.
type Scope string

const (
	// ScopeRead solo consulta (GET).
	ScopeRead Scope = "READ"
	// ScopeControl además crea, edita, borra y acciona actuadores.
	ScopeControl Scope = "CONTROL"
)

// TokenPrefix identifica a simple vista un token de conexión (y lo distingue de un JWT de sesión).
const TokenPrefix = "spt_"

// UnmarshalJSON rechaza un alcance desconocido.
func (s *Scope) UnmarshalJSON(b []byte) error {
	var raw string
	if err := json.Unmarshal(b, &raw); err != nil {
		return err
	}
	switch Scope(raw) {
	case ScopeRead, ScopeControl:
		*s = Scope(raw)
		return nil
	default:
		return fmt.Errorf("invalid scope: %q", raw)
	}
}

// AccessToken es una conexión guardada. Del token solo se conserva el SHA-256 y un prefijo para reconocerlo.
type AccessToken struct {
	ID         primitive.ObjectID
	UserID     primitive.ObjectID
	Name       string
	TokenHash  string
	Prefix     string
	Scope      Scope
	CreatedAt  time.Time
	LastUsedAt *time.Time
	ExpiresAt  *time.Time
}

// Expired indica si la conexión venció en el instante dado.
func (t AccessToken) Expired(now time.Time) bool {
	return t.ExpiresAt != nil && !now.Before(*t.ExpiresAt)
}

// CreateRequest pide una conexión nueva.
type CreateRequest struct {
	Name          string `json:"name"`
	Scope         Scope  `json:"scope"`
	ExpiresInDays *int   `json:"expiresInDays"`
}

// Validate comprueba nombre, alcance y vigencia.
func (r CreateRequest) Validate() validation.Fields {
	v := validation.New()
	name := strings.TrimSpace(r.Name)
	if name == "" {
		v.Add("name", "Connection name is required")
	} else if utf8.RuneCountInString(name) > 60 {
		v.Add("name", "Connection name must be at most 60 characters")
	}
	if r.Scope != ScopeRead && r.Scope != ScopeControl {
		v.Add("scope", "Choose read-only or control")
	}
	if r.ExpiresInDays != nil && (*r.ExpiresInDays < 1 || *r.ExpiresInDays > 365) {
		v.Add("expiresInDays", "Expiration must be between 1 and 365 days")
	}
	return v.Result()
}

// Response es la vista pública de una conexión (nunca incluye el token).
type Response struct {
	ID         string     `json:"id"`
	Name       string     `json:"name"`
	Prefix     string     `json:"prefix"`
	Scope      Scope      `json:"scope"`
	CreatedAt  time.Time  `json:"createdAt"`
	LastUsedAt *time.Time `json:"lastUsedAt,omitempty"`
	ExpiresAt  *time.Time `json:"expiresAt,omitempty"`
}

// CreatedResponse devuelve la conexión recién creada con su token, que solo se muestra esta vez.
type CreatedResponse struct {
	Connection Response `json:"connection"`
	Token      string   `json:"token"`
}

// ToResponse mapea la entidad a su vista pública.
func ToResponse(t AccessToken) Response {
	return Response{
		ID:         t.ID.Hex(),
		Name:       t.Name,
		Prefix:     t.Prefix,
		Scope:      t.Scope,
		CreatedAt:  t.CreatedAt,
		LastUsedAt: t.LastUsedAt,
		ExpiresAt:  t.ExpiresAt,
	}
}
