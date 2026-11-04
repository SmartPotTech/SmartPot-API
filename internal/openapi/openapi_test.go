package openapi

import (
	"encoding/json"
	"strings"
	"testing"

	"github.com/labstack/echo/v4"
)

func TestDocumentMetadata(t *testing.T) {
	doc := Document("https://api.smartpot.app")
	if doc["openapi"] != "3.0.1" {
		t.Errorf("versión openapi = %v", doc["openapi"])
	}
	info := doc["info"].(echo.Map)
	if info["title"] != "SmartPot API" || info["version"] != "2.0.0" {
		t.Errorf("info = %v", info)
	}
	servers := doc["servers"].([]echo.Map)
	if len(servers) != 1 || servers[0]["url"] != "https://api.smartpot.app" {
		t.Errorf("servers = %v", servers)
	}
	comps := doc["components"].(echo.Map)
	bearer := comps["securitySchemes"].(echo.Map)["bearer"].(echo.Map)
	if bearer["scheme"] != "bearer" || bearer["bearerFormat"] != "JWT" {
		t.Errorf("esquema bearer = %v", bearer)
	}
	if _, ok := comps["schemas"].(echo.Map)["ErrorResponse"]; !ok {
		t.Error("falta el esquema ErrorResponse")
	}
}

// Todas las operaciones del catálogo deben aparecer exactamente una vez.
func TestDocumentRendersEveryRoute(t *testing.T) {
	paths := Document("http://x")["paths"].(echo.Map)
	count := 0
	for _, item := range paths {
		count += len(item.(echo.Map))
	}
	if count != len(routes) {
		t.Fatalf("operaciones renderizadas %d, se esperaban %d", count, len(routes))
	}
	for _, r := range routes {
		item, ok := paths[r.path].(echo.Map)
		if !ok {
			t.Fatalf("falta el camino %s", r.path)
		}
		if _, ok := item[strings.ToLower(r.method)]; !ok {
			t.Fatalf("falta %s %s", r.method, r.path)
		}
	}
}

// Las rutas públicas anulan el token con security: []; las demás lo heredan.
func TestSecurityOverrides(t *testing.T) {
	paths := Document("http://x")["paths"].(echo.Map)

	login := paths["/api/v1/auth/login"].(echo.Map)["post"].(echo.Map)
	sec, ok := login["security"].([]echo.Map)
	if !ok || len(sec) != 0 {
		t.Errorf("login debería tener security: [] (sin token), se obtuvo %v", login["security"])
	}

	list := paths["/api/v1/crops"].(echo.Map)["get"].(echo.Map)
	if _, has := list["security"]; has {
		t.Errorf("authenticated route must not set own security (inherits global): %v", list["security"])
	}
}

func TestPathParameters(t *testing.T) {
	paths := Document("http://x")["paths"].(echo.Map)
	op := paths["/api/v1/crops/{cropId}/channels/{type}/recipients/{recipientId}"].(echo.Map)["delete"].(echo.Map)
	params, ok := op["parameters"].([]echo.Map)
	if !ok || len(params) != 3 {
		t.Fatalf("expected 3 path parameters, got %v", op["parameters"])
	}
	want := map[string]bool{"cropId": true, "type": true, "recipientId": true}
	for _, p := range params {
		name := p["name"].(string)
		if !want[name] {
			t.Errorf("unexpected parameter %q", name)
		}
		if p["in"] != "path" || p["required"] != true {
			t.Errorf("parameter %q ill-defined: %v", name, p)
		}
	}
}

// El documento debe ser JSON válido (lo consume Swagger UI).
func TestDocumentIsValidJSON(t *testing.T) {
	if _, err := json.Marshal(Document("http://x")); err != nil {
		t.Fatalf("el documento no serializa a JSON: %v", err)
	}
}
