package crops

import (
	"context"
	"net/http"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/httpserver"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/geo"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/readings"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/security"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/virtualdevices"
	"github.com/labstack/echo/v4"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

// VirtualDeviceManager arranca y valida la simulación de los cultivos virtuales
// (lo implementa el paquete virtualdevices).
type VirtualDeviceManager interface {
	CheckCanCreate(ctx context.Context, ownerID string, req *virtualdevices.VirtualDeviceRequest, placement *geo.Placement) error
	StartFor(ctx context.Context, cropID string, req *virtualdevices.VirtualDeviceRequest) error
}

// Handler expone las rutas de cultivos.
type Handler struct {
	crops    *Service
	readings *readings.Service
	virtual  VirtualDeviceManager
	weather  *WeatherService
}

// NewHandler crea el controlador de cultivos.
func NewHandler(crops *Service, readingsSvc *readings.Service, virtual VirtualDeviceManager, weather *WeatherService) *Handler {
	return &Handler{crops: crops, readings: readingsSvc, virtual: virtual, weather: weather}
}

// Register monta las rutas bajo el grupo autenticado /api/v1.
func (h *Handler) Register(g *echo.Group) {
	g.GET("/crops", h.list)
	g.POST("/crops", h.create)
	g.PUT("/crops/automation", h.bulkAutomation)
	g.GET("/crops/:cropId", h.get)
	g.PUT("/crops/:cropId", h.update)
	g.DELETE("/crops/:cropId", h.delete)
	g.PUT("/crops/:cropId/automation", h.automation)
	g.GET("/crops/:cropId/weather", h.weatherOf)
	g.GET("/crops/:cropId/device", h.device)
	g.POST("/crops/:cropId/device/key", h.rotateKey)
}

func (h *Handler) list(c echo.Context) error {
	cropsList, err := h.crops.List(c.Request().Context(), security.CurrentUserID(c))
	if err != nil {
		return err
	}
	ids := make([]primitive.ObjectID, 0, len(cropsList))
	for i := range cropsList {
		ids = append(ids, cropsList[i].ID)
	}
	latest, err := h.readings.LatestByCropIDs(c.Request().Context(), ids)
	if err != nil {
		return err
	}
	out := make([]CropResponse, 0, len(cropsList))
	for i := range cropsList {
		out = append(out, ToResponse(&cropsList[i], latest[cropsList[i].HexID()]))
	}
	return c.JSON(http.StatusOK, out)
}

func (h *Handler) create(c echo.Context) error {
	var req CropRequest
	if err := httpserver.Bind(c, &req); err != nil {
		return err
	}
	ownerID := security.CurrentUserID(c)
	virtual := req.Kind != nil && *req.Kind == KindVirtual
	if virtual {
		if err := h.virtual.CheckCanCreate(c.Request().Context(), ownerID, req.Virtual, req.Placement.ToPlacement()); err != nil {
			return err
		}
	} else if req.Virtual != nil {
		return apperr.BadRequest("Only virtual crops have simulation")
	}
	created, err := h.crops.Create(c.Request().Context(), ownerID, req)
	if err != nil {
		return err
	}
	if virtual {
		if err := h.virtual.StartFor(c.Request().Context(), created.Crop.HexID(), req.Virtual); err != nil {
			return err
		}
	}
	if created.Credentials != nil {
		// Una credencial de dispositivo nunca debe quedar en cachés intermedias.
		c.Response().Header().Set("Cache-Control", "no-store")
	}
	return c.JSON(http.StatusCreated, CropCreatedResponse{Crop: ToResponse(created.Crop, nil), Device: created.Credentials})
}

func (h *Handler) get(c echo.Context) error {
	crop, err := h.crops.GetOwned(c.Request().Context(), security.CurrentUserID(c), c.Param("cropId"))
	if err != nil {
		return err
	}
	return c.JSON(http.StatusOK, h.toResponse(c.Request().Context(), crop))
}

func (h *Handler) update(c echo.Context) error {
	var req CropRequest
	if err := httpserver.Bind(c, &req); err != nil {
		return err
	}
	crop, err := h.crops.Update(c.Request().Context(), security.CurrentUserID(c), c.Param("cropId"), req)
	if err != nil {
		return err
	}
	return c.JSON(http.StatusOK, h.toResponse(c.Request().Context(), crop))
}

func (h *Handler) delete(c echo.Context) error {
	if err := h.crops.Delete(c.Request().Context(), security.CurrentUserID(c), c.Param("cropId")); err != nil {
		return err
	}
	return c.NoContent(http.StatusNoContent)
}

func (h *Handler) bulkAutomation(c echo.Context) error {
	var req BulkAutomationRequest
	if err := httpserver.Bind(c, &req); err != nil {
		return err
	}
	crops, err := h.crops.SetAutomationBulk(c.Request().Context(), security.CurrentUserID(c), req.CropIds, *req.Enabled)
	if err != nil {
		return err
	}
	out := make([]CropResponse, 0, len(crops))
	for i := range crops {
		out = append(out, h.toResponse(c.Request().Context(), &crops[i]))
	}
	return c.JSON(http.StatusOK, out)
}

func (h *Handler) automation(c echo.Context) error {
	var req AutomationRequest
	if err := httpserver.Bind(c, &req); err != nil {
		return err
	}
	crop, err := h.crops.SetAutomation(c.Request().Context(), security.CurrentUserID(c), c.Param("cropId"), *req.Enabled)
	if err != nil {
		return err
	}
	return c.JSON(http.StatusOK, h.toResponse(c.Request().Context(), crop))
}

func (h *Handler) weatherOf(c echo.Context) error {
	crop, err := h.crops.GetOwned(c.Request().Context(), security.CurrentUserID(c), c.Param("cropId"))
	if err != nil {
		return err
	}
	weather, ok := h.weather.Current(c.Request().Context(), crop)
	if !ok {
		return c.NoContent(http.StatusNoContent)
	}
	return c.JSON(http.StatusOK, weather)
}

func (h *Handler) device(c echo.Context) error {
	creds, err := h.crops.DeviceInfo(c.Request().Context(), security.CurrentUserID(c), c.Param("cropId"))
	if err != nil {
		return err
	}
	c.Response().Header().Set("Cache-Control", "no-store")
	return c.JSON(http.StatusOK, creds)
}

func (h *Handler) rotateKey(c echo.Context) error {
	creds, err := h.crops.RotateDeviceKey(c.Request().Context(), security.CurrentUserID(c), c.Param("cropId"))
	if err != nil {
		return err
	}
	c.Response().Header().Set("Cache-Control", "no-store")
	return c.JSON(http.StatusOK, creds)
}

func (h *Handler) toResponse(ctx context.Context, crop *Crop) CropResponse {
	latest, _ := h.readings.Latest(ctx, crop.HexID())
	return ToResponse(crop, latest)
}
