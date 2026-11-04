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
	{"GET", "/health", "Salud", "Estado del servicio", true},

	{"POST", "/api/v1/auth/register", "Autenticación", "Crear una cuenta", true},
	{"POST", "/api/v1/auth/login", "Autenticación", "Iniciar sesión", true},
	{"POST", "/api/v1/auth/logout", "Autenticación", "Cerrar sesión", true},
	{"GET", "/api/v1/auth/session", "Autenticación", "Ver sesión actual", false},
	{"POST", "/api/v1/auth/password/forgot", "Autenticación", "Solicitar restablecer la contraseña", true},
	{"POST", "/api/v1/auth/password/reset", "Autenticación", "Restablecer la contraseña con el enlace", true},

	{"GET", "/api/v1/crop-profiles", "IA", "Perfiles de cultivo recomendados", true},
	{"POST", "/api/v1/channels/telegram/webhook", "Canales", "Webhook del bot de Telegram", true},

	{"GET", "/api/v1/users/me", "Cuenta", "Ver mi perfil", false},
	{"PUT", "/api/v1/users/me", "Cuenta", "Actualizar mi perfil", false},
	{"PUT", "/api/v1/users/me/password", "Cuenta", "Cambiar mi contraseña", false},
	{"DELETE", "/api/v1/users/me", "Cuenta", "Eliminar mi cuenta", false},

	{"GET", "/api/v1/crops", "Cultivos", "Listar mis cultivos", false},
	{"POST", "/api/v1/crops", "Cultivos", "Crear un cultivo", false},
	{"PUT", "/api/v1/crops/automation", "Cultivos", "Automatización en bloque", false},
	{"GET", "/api/v1/crops/{cropId}", "Cultivos", "Ver un cultivo", false},
	{"PUT", "/api/v1/crops/{cropId}", "Cultivos", "Actualizar un cultivo", false},
	{"DELETE", "/api/v1/crops/{cropId}", "Cultivos", "Eliminar un cultivo", false},
	{"PUT", "/api/v1/crops/{cropId}/automation", "Cultivos", "Activar o pausar el automático", false},
	{"GET", "/api/v1/crops/{cropId}/weather", "Cultivos", "Clima del lugar del cultivo", false},
	{"GET", "/api/v1/crops/{cropId}/device", "Cultivos", "Datos del dispositivo", false},
	{"POST", "/api/v1/crops/{cropId}/device/key", "Cultivos", "Rotar la clave del dispositivo", false},

	{"GET", "/api/v1/crops/{cropId}/readings", "Lecturas", "Listar lecturas", false},
	{"GET", "/api/v1/crops/{cropId}/readings/latest", "Lecturas", "Última lectura", false},
	{"GET", "/api/v1/crops/{cropId}/readings/summary", "Lecturas", "Resumen de lecturas", false},
	{"GET", "/api/v1/crops/{cropId}/readings/export", "Lecturas", "Exportar lecturas (CSV)", false},
	{"POST", "/api/v1/crops/{cropId}/readings", "Lecturas", "Registrar una lectura", false},

	{"GET", "/api/v1/crops/{cropId}/actuators", "Actuadores", "Listar actuadores", false},
	{"POST", "/api/v1/crops/{cropId}/actuators", "Actuadores", "Agregar un actuador", false},
	{"DELETE", "/api/v1/crops/{cropId}/actuators/{actuatorId}", "Actuadores", "Quitar un actuador", false},

	{"GET", "/api/v1/crops/{cropId}/commands", "Comandos", "Listar comandos del cultivo", false},
	{"POST", "/api/v1/crops/{cropId}/commands", "Comandos", "Enviar un comando", false},
	{"GET", "/api/v1/commands", "Comandos", "Historial de comandos", false},
	{"POST", "/api/v1/commands/bulk", "Comandos", "Enviar comandos en bloque", false},

	{"GET", "/api/v1/crops/{cropId}/insights", "IA", "Análisis del cultivo por la IA", false},
	{"GET", "/api/v1/ai/learning", "IA", "Estado del aprendizaje continuo", false},

	{"GET", "/api/v1/overview", "Panel", "Panel general", false},
	{"GET", "/api/v1/overview/series", "Panel", "Series del panel", false},
	{"GET", "/api/v1/overview/fleet", "Panel", "Análisis de la flota", false},

	{"GET", "/api/v1/notifications", "Notificaciones", "Listar notificaciones", false},
	{"GET", "/api/v1/notifications/unread-count", "Notificaciones", "Notificaciones sin leer", false},
	{"PUT", "/api/v1/notifications/{id}/read", "Notificaciones", "Marcar como leída", false},
	{"PUT", "/api/v1/notifications/read-all", "Notificaciones", "Marcar todas como leídas", false},
	{"DELETE", "/api/v1/notifications/{id}", "Notificaciones", "Eliminar una notificación", false},

	{"GET", "/api/v1/connections", "Conexiones", "Listar las conexiones (tokens spt_ para clientes MCP)", false},
	{"POST", "/api/v1/connections", "Conexiones", "Crear una conexión (el token solo se devuelve aquí)", false},
	{"DELETE", "/api/v1/connections/{id}", "Conexiones", "Revocar una conexión", false},

	{"GET", "/api/v1/crops/{cropId}/virtual-device", "Dispositivo virtual", "Ver el dispositivo virtual", false},
	{"PUT", "/api/v1/crops/{cropId}/virtual-device", "Dispositivo virtual", "Configurar el dispositivo virtual", false},
	{"DELETE", "/api/v1/crops/{cropId}/virtual-device", "Dispositivo virtual", "Pausar el dispositivo virtual", false},
	{"GET", "/api/v1/virtual-devices/places", "Dispositivo virtual", "Buscar lugares", false},

	{"GET", "/api/v1/channels", "Canales", "Canales y vínculos de la cuenta", false},
	{"POST", "/api/v1/channels/{type}/link", "Canales", "Generar código de vinculación", false},
	{"PUT", "/api/v1/channels/links/{id}", "Canales", "Actualizar un vínculo", false},
	{"POST", "/api/v1/channels/links/{id}/test", "Canales", "Enviar un mensaje de prueba", false},
	{"DELETE", "/api/v1/channels/links/{id}", "Canales", "Desvincular un canal", false},
	{"GET", "/api/v1/crops/{cropId}/channels", "Canales", "Avisos del cultivo por canal", false},
	{"PUT", "/api/v1/crops/{cropId}/channels/{type}", "Canales", "Configurar los avisos del cultivo", false},
	{"POST", "/api/v1/crops/{cropId}/channels/{type}/recipients", "Canales", "Compartir avisos con un chat", false},
	{"DELETE", "/api/v1/crops/{cropId}/channels/{type}/recipients/{recipientId}", "Canales", "Dejar de compartir", false},
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
			"description": "API de SmartPot para monitorear y automatizar cultivos hidropónicos: cuentas, " +
				"cultivos, telemetría, actuadores, comandos por MQTT y asistente de IA.",
			"contact": echo.Map{"name": "SmartPot Tech", "url": "https://github.com/SmartPotTech"},
			"license": echo.Map{"name": "MIT", "url": "https://opensource.org/licenses/MIT"},
		},
		"servers":  []echo.Map{{"url": publicAPIURL, "description": "Servidor de la API"}},
		"security": []echo.Map{{"bearer": []string{}}},
		"paths":    paths,
		"components": echo.Map{
			"securitySchemes": echo.Map{
				"bearer": echo.Map{
					"type":         "http",
					"scheme":       "bearer",
					"bearerFormat": "JWT",
					"description":  "Token recibido en /api/v1/auth/login o /api/v1/auth/register",
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
			"200": echo.Map{"description": "Operación correcta"},
			"400": errorRef("Solicitud inválida"),
			"401": errorRef("No autenticado"),
			"403": errorRef("Sin permiso"),
			"404": errorRef("No encontrado"),
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
