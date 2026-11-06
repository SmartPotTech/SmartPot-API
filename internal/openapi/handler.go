package openapi

import (
	"net/http"

	"github.com/labstack/echo/v4"
)

// swaggerUIVersion fija la versión de la interfaz de Swagger servida desde el CDN.
const swaggerUIVersion = "5.17.14"

// Register monta la documentación: el JSON OpenAPI en /v3/api-docs y la interfaz
// de Swagger en /docs, ambos en la raíz (no bajo /api/v1), igual que springdoc.
// Si API_DOCS_ENABLED es falso, no se monta ninguna de las dos.
func Register(engine *echo.Echo, publicAPIURL string, enabled bool) {
	if !enabled {
		return
	}
	doc := Document(publicAPIURL)
	engine.GET("/v3/api-docs", func(c echo.Context) error { return c.JSON(http.StatusOK, doc) })
	engine.GET("/docs", func(c echo.Context) error {
		return c.HTML(http.StatusOK, swaggerUIPage)
	})
}

// swaggerUIPage es la página que carga la interfaz de Swagger apuntando al JSON.
const swaggerUIPage = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>SmartPot API · Documentation</title>
  <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/swagger-ui/` + swaggerUIVersion + `/swagger-ui.min.css">
  <style>body{margin:0;background:#F2F7F4}</style>
</head>
<body>
  <div id="swagger-ui"></div>
  <script src="https://cdnjs.cloudflare.com/ajax/libs/swagger-ui/` + swaggerUIVersion + `/swagger-ui-bundle.min.js"></script>
  <script src="https://cdnjs.cloudflare.com/ajax/libs/swagger-ui/` + swaggerUIVersion + `/swagger-ui-standalone-preset.min.js"></script>
  <script>
    window.onload = function () {
      window.ui = SwaggerUIBundle({
        url: "/v3/api-docs",
        dom_id: "#swagger-ui",
        deepLinking: true,
        displayRequestDuration: true,
        operationsSorter: "method",
        tagsSorter: "alpha",
        presets: [SwaggerUIBundle.presets.apis, SwaggerUIStandalonePreset],
        layout: "StandaloneLayout"
      });
    };
  </script>
</body>
</html>`
