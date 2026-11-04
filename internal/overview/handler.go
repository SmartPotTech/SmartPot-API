package overview

import (
	"net/http"
	"strconv"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/security"
	"github.com/labstack/echo/v4"
)

// Handler expone las rutas del panel general.
type Handler struct {
	svc *Service
}

// NewHandler crea el controlador del panel general.
func NewHandler(svc *Service) *Handler {
	return &Handler{svc: svc}
}

// Register monta las rutas bajo el grupo autenticado /api/v1.
func (h *Handler) Register(g *echo.Group) {
	g.GET("/overview", h.overview)
	g.GET("/overview/series", h.series)
	g.GET("/overview/fleet", h.fleet)
}

func (h *Handler) overview(c echo.Context) error {
	res, err := h.svc.Overview(c.Request().Context(), security.CurrentUserID(c))
	if err != nil {
		return err
	}
	return c.JSON(http.StatusOK, res)
}

func (h *Handler) series(c echo.Context) error {
	metric := c.QueryParam("metric")
	if metric == "" {
		metric = "temperature"
	}
	hours := 24
	if raw := c.QueryParam("hours"); raw != "" {
		n, err := strconv.Atoi(raw)
		if err != nil {
			return apperr.BadRequest("Invalid or missing parameter")
		}
		hours = n
	}
	res, err := h.svc.Series(c.Request().Context(), security.CurrentUserID(c), metric, hours)
	if err != nil {
		return err
	}
	return c.JSON(http.StatusOK, res)
}

func (h *Handler) fleet(c echo.Context) error {
	res, err := h.svc.Fleet(c.Request().Context(), security.CurrentUserID(c))
	if err != nil {
		return err
	}
	return c.JSON(http.StatusOK, res)
}
