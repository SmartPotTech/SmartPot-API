// Package openapi arma el documento OpenAPI 3 de SmartPot-API y lo sirve en
// /v3/api-docs, con la interfaz de Swagger en /docs, igual que springdoc en la
// versión en Java (ambas rutas quedan en la raíz y se pueden apagar con
// API_DOCS_ENABLED).
package openapi

import (
	"strings"

	"github.com/labstack/echo/v4"
)

// route describe una operación para el documento.
type route struct {
	method  string
	path    string
	tag     string
	summary string
	public  bool // sin autenticación (anula el requisito global de token)
}

// routes es el catálogo de endpoints, con los mismos caminos que la versión en
// Java. Las rutas públicas no exigen token.
var routes = []route{
	{"GET", "/health", "Health", "Service health", true},

	{"POST", "/api/v1/auth/register", "Authentication", "Register account", true},
	{"POST", "/api/v1/auth/login", "Authentication", "Log in", true},
	{"POST", "/api/v1/auth/logout", "Authentication", "Log out", true},
	{"GET", "/api/v1/auth/session", "Authentication", "Get active session", false},
	{"POST", "/api/v1/auth/password/forgot", "Authentication", "Request password reset", true},
	{"POST", "/api/v1/auth/password/reset", "Authentication", "Reset password with link", true},

	{"GET", "/api/v1/crop-profiles", "AI", "Recommended crop profiles", true},
	{"POST", "/api/v1/channels/telegram/webhook", "Channels", "Telegram bot webhook", true},

	{"GET", "/api/v1/users/me", "Account", "Get profile", false},
	{"PUT", "/api/v1/users/me", "Account", "Update profile", false},
	{"PUT", "/api/v1/users/me/password", "Account", "Change password", false},
	{"DELETE", "/api/v1/users/me", "Account", "Delete account", false},

	{"GET", "/api/v1/crops", "Crops", "List crops", false},
	{"POST", "/api/v1/crops", "Crops", "Create crop", false},
	{"PUT", "/api/v1/crops/automation", "Crops", "Bulk automation", false},
	{"GET", "/api/v1/crops/{cropId}", "Crops", "Get crop", false},
	{"PUT", "/api/v1/crops/{cropId}", "Crops", "Update crop", false},
	{"DELETE", "/api/v1/crops/{cropId}", "Crops", "Delete crop", false},
	{"PUT", "/api/v1/crops/{cropId}/automation", "Crops", "Toggle crop automation", false},
	{"GET", "/api/v1/crops/{cropId}/weather", "Crops", "Crop location weather", false},
	{"GET", "/api/v1/crops/{cropId}/device", "Crops", "Device details", false},
	{"POST", "/api/v1/crops/{cropId}/device/key", "Crops", "Rotate device key", false},

	{"GET", "/api/v1/crops/{cropId}/readings", "Readings", "List readings", false},
	{"GET", "/api/v1/crops/{cropId}/readings/latest", "Readings", "Latest reading", false},
	{"GET", "/api/v1/crops/{cropId}/readings/summary", "Readings", "Readings summary", false},
	{"GET", "/api/v1/crops/{cropId}/readings/export", "Readings", "Export readings (CSV)", false},
	{"GET", "/api/v1/crops/{cropId}/stream", "Readings", "Live telemetry stream (SSE)", false},
	{"GET", "/api/v1/crops/{cropId}/readings/stream", "Readings", "Live readings stream (SSE)", false},
	{"POST", "/api/v1/crops/{cropId}/readings", "Readings", "Record reading", false},

	{"GET", "/api/v1/crops/{cropId}/actuators", "Actuators", "List actuators", false},
	{"POST", "/api/v1/crops/{cropId}/actuators", "Actuators", "Add actuator", false},
	{"DELETE", "/api/v1/crops/{cropId}/actuators/{actuatorId}", "Actuators", "Remove actuator", false},

	{"GET", "/api/v1/crops/{cropId}/commands", "Commands", "List crop commands", false},
	{"POST", "/api/v1/crops/{cropId}/commands", "Commands", "Send a command", false},
	{"GET", "/api/v1/commands", "Commands", "Command history", false},
	{"POST", "/api/v1/commands/bulk", "Commands", "Send bulk commands", false},

	{"GET", "/api/v1/crops/{cropId}/insights", "AI", "AI crop insight", false},
	{"GET", "/api/v1/ai/learning", "AI", "Continual learning status", false},

	{"GET", "/api/v1/overview", "Dashboard", "Dashboard overview", false},
	{"GET", "/api/v1/overview/series", "Dashboard", "Dashboard series", false},
	{"GET", "/api/v1/overview/fleet", "Dashboard", "Fleet analysis", false},

	{"GET", "/api/v1/notifications", "Notifications", "List notifications", false},
	{"GET", "/api/v1/notifications/unread-count", "Notifications", "Unread count", false},
	{"PUT", "/api/v1/notifications/{id}/read", "Notifications", "Mark as read", false},
	{"PUT", "/api/v1/notifications/read-all", "Notifications", "Mark all as read", false},
	{"DELETE", "/api/v1/notifications/{id}", "Notifications", "Delete notification", false},

	{"GET", "/api/v1/connections", "Connections", "List connections (spt_ tokens for MCP clients)", false},
	{"POST", "/api/v1/connections", "Connections", "Create connection (token returned once)", false},
	{"DELETE", "/api/v1/connections/{id}", "Connections", "Revoke connection", false},

	{"GET", "/api/v1/crops/{cropId}/virtual-device", "Virtual device", "View virtual device", false},
	{"PUT", "/api/v1/crops/{cropId}/virtual-device", "Virtual device", "Configure virtual device", false},
	{"DELETE", "/api/v1/crops/{cropId}/virtual-device", "Virtual device", "Pause virtual device", false},
	{"GET", "/api/v1/virtual-devices/places", "Virtual device", "Search places", false},

	{"GET", "/api/v1/channels", "Channels", "Account channels and links", false},
	{"POST", "/api/v1/channels/{type}/link", "Channels", "Generate link code", false},
	{"PUT", "/api/v1/channels/links/{id}", "Channels", "Update link", false},
	{"POST", "/api/v1/channels/links/{id}/test", "Channels", "Send test message", false},
	{"DELETE", "/api/v1/channels/links/{id}", "Channels", "Unlink channel", false},
	{"GET", "/api/v1/crops/{cropId}/channels", "Channels", "Crop channel alerts", false},
	{"PUT", "/api/v1/crops/{cropId}/channels/{type}", "Channels", "Configure crop channel alerts", false},
	{"POST", "/api/v1/crops/{cropId}/channels/{type}/recipients", "Channels", "Share alerts with a chat", false},
	{"DELETE", "/api/v1/crops/{cropId}/channels/{type}/recipients/{recipientId}", "Channels", "Stop sharing", false},
}

// Document arma el documento OpenAPI 3.0.1 completo.
func Document(publicAPIURL string) echo.Map {
	paths := echo.Map{}
	for _, r := range routes {
		item, ok := paths[r.path].(echo.Map)
		if !ok {
			item = echo.Map{}
			paths[r.path] = item
		}
		item[strings.ToLower(r.method)] = operation(r)
	}
	return echo.Map{
		"openapi": "3.0.1",
		"info": echo.Map{
			"title":   "SmartPot API",
			"version": "2.0.0",
			"description": "SmartPot API for monitoring and automating hydroponic crops: accounts, " +
				"crops, telemetry, actuators, MQTT commands, and AI assistant.",
			"contact": echo.Map{"name": "SmartPot Tech", "url": "https://github.com/SmartPotTech"},
			"license": echo.Map{"name": "MIT", "url": "https://opensource.org/licenses/MIT"},
		},
		"servers":  []echo.Map{{"url": publicAPIURL, "description": "API server"}},
		"security": []echo.Map{{"bearer": []string{}}},
		"paths":    paths,
		"components": echo.Map{
			"securitySchemes": echo.Map{
				"bearer": echo.Map{
					"type":         "http",
					"scheme":       "bearer",
					"bearerFormat": "JWT",
					"description":  "Token received in /api/v1/auth/login or /api/v1/auth/register, or spt_ connection token",
				},
			},
			"schemas": echo.Map{"ErrorResponse": errorSchema()},
		},
	}
}

func operation(r route) echo.Map {
	op := echo.Map{
		"tags":        []string{r.tag},
		"summary":     r.summary,
		"operationId": operationID(r),
		"responses": echo.Map{
			"200": echo.Map{"description": "Successful operation"},
			"400": errorRef("Invalid request"),
			"401": errorRef("Unauthenticated"),
			"403": errorRef("Forbidden"),
			"404": errorRef("Not found"),
		},
	}
	if params := pathParams(r.path); len(params) > 0 {
		op["parameters"] = params
	}
	if r.public {
		// Anula el requisito global de token para las rutas públicas.
		op["security"] = []echo.Map{}
	}
	return op
}

func errorRef(description string) echo.Map {
	return echo.Map{
		"description": description,
		"content":     echo.Map{"application/json": echo.Map{"schema": echo.Map{"$ref": "#/components/schemas/ErrorResponse"}}},
	}
}

func errorSchema() echo.Map {
	return echo.Map{
		"type": "object",
		"properties": echo.Map{
			"status":    echo.Map{"type": "integer", "format": "int32"},
			"error":     echo.Map{"type": "string"},
			"message":   echo.Map{"type": "string"},
			"path":      echo.Map{"type": "string"},
			"timestamp": echo.Map{"type": "string", "format": "date-time"},
			"fields": echo.Map{
				"type":                 "object",
				"additionalProperties": echo.Map{"type": "string"},
			},
		},
	}
}

// pathParams arma los parámetros de ruta a partir de los {segmentos} del camino.
func pathParams(path string) []echo.Map {
	var out []echo.Map
	for _, seg := range strings.Split(path, "/") {
		if len(seg) >= 2 && seg[0] == '{' && seg[len(seg)-1] == '}' {
			name := seg[1 : len(seg)-1]
			out = append(out, echo.Map{
				"name": name, "in": "path", "required": true,
				"schema": echo.Map{"type": "string"},
			})
		}
	}
	return out
}

// operationID arma un identificador estable a partir del método y el camino.
func operationID(r route) string {
	id := strings.ToLower(r.method)
	for _, seg := range strings.Split(r.path, "/") {
		if seg == "" {
			continue
		}
		seg = strings.ReplaceAll(seg, "{", "")
		seg = strings.ReplaceAll(seg, "}", "")
		id += capitalize(seg)
	}
	return id
}

func capitalize(s string) string {
	if s == "" {
		return s
	}
	return strings.ToUpper(s[:1]) + s[1:]
}
