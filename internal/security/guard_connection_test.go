package security

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/httpserver"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"github.com/labstack/echo/v4"
)

type fakeConnections map[string]ConnectionPrincipal

func (f fakeConnections) AuthenticateConnection(_ context.Context, raw string) (ConnectionPrincipal, error) {
	if p, ok := f[raw]; ok {
		return p, nil
	}
	return ConnectionPrincipal{}, apperr.Unauthorized("El token de conexión no es válido o fue revocado")
}

func TestGuardConnectionTokens(t *testing.T) {
	svc := NewTokenService(testSecret, time.Hour)
	svc.UseConnections(fakeConnections{
		"spt_lectura": {ConnectionID: "c1", UserID: "user-1", Scope: "READ"},
		"spt_control": {ConnectionID: "c2", UserID: "user-1", Scope: "CONTROL"},
	})
	e := httpserver.New(discardLogger())
	grp := e.Group("/api/v1")
	grp.Use(svc.Guard())
	ok := func(c echo.Context) error {
		id := CurrentIdentity(c)
		if id.Role != RoleUser || id.ConnectionID == "" {
			t.Errorf("identidad de conexión incompleta: %+v", id)
		}
		return c.String(http.StatusOK, CurrentUserID(c))
	}
	grp.GET("/crops", ok)
	grp.POST("/crops/:id/commands", ok)
	grp.GET("/crops/:id/device", ok)
	grp.GET("/connections", ok)
	grp.PUT("/users/me", ok)
	grp.GET("/users/me", ok)
	grp.GET("/admin", ok, RequireRole(RoleAdmin))

	cases := []struct {
		name, method, path, token string
		want                      int
	}{
		{"token desconocido", http.MethodGet, "/api/v1/crops", "spt_otro", http.StatusUnauthorized},
		{"lectura consulta", http.MethodGet, "/api/v1/crops", "spt_lectura", http.StatusOK},
		{"lectura no acciona", http.MethodPost, "/api/v1/crops/abc/commands", "spt_lectura", http.StatusForbidden},
		{"control acciona", http.MethodPost, "/api/v1/crops/abc/commands", "spt_control", http.StatusOK},
		{"credenciales del dispositivo", http.MethodGet, "/api/v1/crops/abc/device", "spt_control", http.StatusForbidden},
		{"no administra conexiones", http.MethodGet, "/api/v1/connections", "spt_control", http.StatusForbidden},
		{"no cambia la cuenta", http.MethodPut, "/api/v1/users/me", "spt_control", http.StatusForbidden},
		{"sí lee la cuenta", http.MethodGet, "/api/v1/users/me", "spt_lectura", http.StatusOK},
		{"nunca es administrador", http.MethodGet, "/api/v1/admin", "spt_control", http.StatusForbidden},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			rec := httptest.NewRecorder()
			req := httptest.NewRequest(tc.method, tc.path, nil)
			req.Header.Set("Authorization", "Bearer "+tc.token)
			e.ServeHTTP(rec, req)
			if rec.Code != tc.want {
				t.Fatalf("código %d, se esperaba %d (%s)", rec.Code, tc.want, rec.Body.String())
			}
		})
	}
}

func TestGuardRejectsConnectionTokensWhenDisabled(t *testing.T) {
	svc := NewTokenService(testSecret, time.Hour)
	e := httpserver.New(discardLogger())
	grp := e.Group("/api/v1")
	grp.Use(svc.Guard())
	grp.GET("/crops", func(c echo.Context) error { return c.NoContent(http.StatusOK) })
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodGet, "/api/v1/crops", nil)
	req.Header.Set("Authorization", "Bearer spt_lo-que-sea")
	e.ServeHTTP(rec, req)
	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("código %d, se esperaba 401", rec.Code)
	}
}
