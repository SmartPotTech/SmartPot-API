package virtualdevices

import (
	"context"
	"log/slog"
	"strings"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/geo"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/objectid"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/readings"
)

const (
	// MaxPerAccount es el máximo de cultivos virtuales por cuenta.
	MaxPerAccount   = 5
	defaultInterval = 30
)

// CropView es lo que el servicio necesita saber de un cultivo, sin acoplarse al
// paquete crops.
type CropView struct {
	ID        string
	OwnerID   string
	Name      string
	Type      string
	Virtual   bool
	Placement *geo.Placement
}

// Crops es lo que el servicio de dispositivos virtuales necesita de los cultivos
// (lo implementa un adaptador sobre CropService).
type Crops interface {
	GetOwned(ctx context.Context, ownerID, cropID string) (*CropView, error)
	Find(ctx context.Context, cropID string) (*CropView, bool)
	DeviceKey(ctx context.Context, cropID string) (string, bool)
	SetLocation(ctx context.Context, cropID string, loc geo.Location) (*CropView, error)
}

// Service implementa la lógica de los cultivos virtuales.
type Service struct {
	repo      Repository
	crops     Crops
	simulator *SimulatorClient
	clock     func() time.Time
}

// NewService crea el servicio de dispositivos virtuales.
func NewService(repo Repository, crops Crops, simulator *SimulatorClient, clock func() time.Time) *Service {
	return &Service{repo: repo, crops: crops, simulator: simulator, clock: clock}
}

// CheckCanCreate valida que haya simulador, cupo y una configuración válida
// antes de crear un cultivo virtual (implementa crops.VirtualDeviceManager).
func (s *Service) CheckCanCreate(ctx context.Context, ownerID string, req *VirtualDeviceRequest, placement *geo.Placement) error {
	if err := s.requireSimulator(); err != nil {
		return err
	}
	// El cupo de cultivos virtuales lo aplica crops.InsertWithQuota en la misma
	// transacción que crea el cultivo; aquí solo se valida la configuración.
	return validate(req, placement)
}

// StartFor arranca la simulación de un cultivo virtual recién creado
// (implementa crops.VirtualDeviceManager).
func (s *Service) StartFor(ctx context.Context, cropID string, req *VirtualDeviceRequest) error {
	crop, ok := s.crops.Find(ctx, cropID)
	if !ok {
		return nil
	}
	cropOID, _ := objectid.Parse(cropID)
	ownerOID, _ := objectid.Parse(crop.OwnerID)
	now := s.clock()
	config := &VirtualDevice{CropID: cropOID, OwnerID: ownerOID, CreatedAt: now}
	placed := s.apply(ctx, crop, config, req, now)
	if err := s.repo.Save(ctx, config); err != nil {
		return err
	}
	s.pushQuietly(ctx, placed, config)
	slog.Info("virtual crop simulation started", "cropId", cropID, "mode", config.Mode)
	return nil
}

// Get devuelve el estado de la simulación de un cultivo virtual.
func (s *Service) Get(ctx context.Context, ownerID, cropID string) (VirtualDeviceResponse, error) {
	crop, err := s.getOwnedVirtual(ctx, ownerID, cropID)
	if err != nil {
		return VirtualDeviceResponse{}, err
	}
	oid, _ := objectid.Parse(cropID)
	config, err := s.repo.FindByCropID(ctx, oid)
	if err != nil {
		return VirtualDeviceResponse{}, err
	}
	if config == nil {
		return inactiveResponse(cropID, s.simulator.IsAvailable()), nil
	}
	var live *SimulatorPot
	if config.IsActive() {
		live, _ = s.simulator.Get(ctx, cropID)
	}
	return responseOf(config, locationOf(crop, config), live, s.simulator.IsAvailable()), nil
}

// Configure cambia la simulación y la reanuda si estaba en pausa.
func (s *Service) Configure(ctx context.Context, ownerID, cropID string, req VirtualDeviceRequest) (VirtualDeviceResponse, error) {
	crop, err := s.getOwnedVirtual(ctx, ownerID, cropID)
	if err != nil {
		return VirtualDeviceResponse{}, err
	}
	if err := s.requireSimulator(); err != nil {
		return VirtualDeviceResponse{}, err
	}
	if err := validate(&req, crop.Placement); err != nil {
		return VirtualDeviceResponse{}, err
	}
	oid, _ := objectid.Parse(cropID)
	now := s.clock()
	config, err := s.repo.FindByCropID(ctx, oid)
	if err != nil {
		return VirtualDeviceResponse{}, err
	}
	if config == nil {
		ownerOID, _ := objectid.Parse(crop.OwnerID)
		config = &VirtualDevice{CropID: oid, OwnerID: ownerOID, CreatedAt: now}
	}
	placed := s.apply(ctx, crop, config, &req, now)
	live, err := s.push(ctx, placed, config)
	if err != nil {
		return VirtualDeviceResponse{}, err
	}
	if err := s.repo.Save(ctx, config); err != nil {
		return VirtualDeviceResponse{}, err
	}
	return responseOf(config, locationOf(placed, config), live, true), nil
}

// Pause pone la simulación en pausa, conservando su configuración.
func (s *Service) Pause(ctx context.Context, ownerID, cropID string) error {
	if _, err := s.getOwnedVirtual(ctx, ownerID, cropID); err != nil {
		return err
	}
	oid, _ := objectid.Parse(cropID)
	config, err := s.repo.FindByCropID(ctx, oid)
	if err != nil {
		return err
	}
	if config != nil {
		inactive := false
		config.Active = &inactive
		config.UpdatedAt = s.clock()
		if err := s.repo.Save(ctx, config); err != nil {
			return err
		}
	}
	s.simulator.Delete(ctx, cropID)
	return nil
}

// Places busca lugares para el modo clima.
func (s *Service) Places(ctx context.Context, query string) ([]PlaceResponse, error) {
	trimmed := strings.TrimSpace(query)
	if len(trimmed) < 2 || len(trimmed) > 80 {
		return nil, apperr.BadRequest("Enter between 2 and 80 characters to search for a location")
	}
	return s.simulator.Places(ctx, trimmed)
}

// Reconcile vuelve a crear las simulaciones activas que falten y retira las pausadas.
func (s *Service) Reconcile(ctx context.Context) {
	if !s.simulator.IsAvailable() {
		return
	}
	pots, err := s.simulator.List(ctx)
	if err != nil {
		slog.Debug("virtual crop reconciliation skipped", "err", err)
		return
	}
	running := make(map[string]bool)
	for _, p := range pots {
		if p.Managed {
			running[p.CropID] = true
		}
	}
	all, err := s.repo.FindAll(ctx)
	if err != nil {
		return
	}
	wanted := make(map[string]bool)
	for i := range all {
		config := &all[i]
		if !config.IsActive() {
			continue
		}
		cropID := config.CropID.Hex()
		wanted[cropID] = true
		if running[cropID] {
			continue
		}
		if crop, ok := s.crops.Find(ctx, cropID); ok {
			s.pushQuietly(ctx, crop, config)
		} else {
			_ = s.repo.DeleteByCrop(ctx, config.CropID)
		}
	}
	for id := range running {
		if !wanted[id] {
			s.simulator.Delete(ctx, id)
		}
	}
}

// OnCropDeleted retira la simulación de un cultivo eliminado.
func (s *Service) OnCropDeleted(cropID string) {
	ctx := context.Background()
	if oid, ok := objectid.Parse(cropID); ok {
		_ = s.repo.DeleteByCrop(ctx, oid)
	}
	s.simulator.Delete(ctx, cropID)
}

// OnPlacementChanged reenvía la simulación activa cuando cambia el lugar.
func (s *Service) OnPlacementChanged(cropID string) {
	s.repushActive(cropID)
}

// OnKeyRotated reenvía la simulación activa cuando se rota la clave.
func (s *Service) OnKeyRotated(cropID string) {
	s.repushActive(cropID)
}

func (s *Service) repushActive(cropID string) {
	ctx := context.Background()
	oid, ok := objectid.Parse(cropID)
	if !ok {
		return
	}
	config, err := s.repo.FindByCropID(ctx, oid)
	if err != nil || config == nil || !config.IsActive() {
		return
	}
	if crop, ok := s.crops.Find(ctx, cropID); ok {
		s.pushQuietly(ctx, crop, config)
	}
}

func (s *Service) getOwnedVirtual(ctx context.Context, ownerID, cropID string) (*CropView, error) {
	crop, err := s.crops.GetOwned(ctx, ownerID, cropID)
	if err != nil {
		return nil, err
	}
	if !crop.Virtual {
		return nil, apperr.BadRequest("This is a real crop: readings come from its physical device")
	}
	return crop, nil
}

func (s *Service) requireSimulator() error {
	if !s.simulator.IsAvailable() {
		return apperr.Unavailable("Virtual crops are not enabled on this server")
	}
	return nil
}

func (s *Service) apply(ctx context.Context, crop *CropView, config *VirtualDevice, req *VirtualDeviceRequest, now time.Time) *CropView {
	config.Mode = modeOf(req)
	if req != nil && req.Manual != nil {
		config.Manual = merge(config.Manual, req.Manual)
	}
	placed := crop
	if req != nil && req.Location != nil {
		loc := Location{Name: strings.TrimSpace(req.Location.Name), Latitude: deref(req.Location.Latitude), Longitude: deref(req.Location.Longitude)}
		config.Location = &loc
		if updated, err := s.crops.SetLocation(ctx, crop.ID, geo.Location{Name: loc.Name, Latitude: loc.Latitude, Longitude: loc.Longitude}); err == nil && updated != nil {
			placed = updated
		}
	}
	switch {
	case req != nil && req.IntervalSeconds != nil:
		config.IntervalSeconds = *req.IntervalSeconds
	case config.IntervalSeconds <= 0:
		config.IntervalSeconds = defaultInterval
	}
	active := true
	config.Active = &active
	config.UpdatedAt = now
	return placed
}

func (s *Service) pushQuietly(ctx context.Context, crop *CropView, config *VirtualDevice) {
	if _, err := s.push(ctx, crop, config); err != nil {
		slog.Warn("could not start crop simulation", "cropId", crop.ID, "err", err)
	}
}

func (s *Service) push(ctx context.Context, crop *CropView, config *VirtualDevice) (*SimulatorPot, error) {
	key, ok := s.crops.DeviceKey(ctx, crop.ID)
	if !ok {
		return nil, apperr.Conflict("The crop does not have a device key: please create it again")
	}
	location := locationOf(crop, config)
	var simLoc *SimLocation
	if location != nil {
		simLoc = &SimLocation{Name: location.Name, Latitude: location.Latitude, Longitude: location.Longitude}
	}
	setting, exposure := "", ""
	if crop.Placement != nil {
		if crop.Placement.Setting != nil {
			setting = string(*crop.Placement.Setting)
		}
		if crop.Placement.Exposure != nil {
			exposure = string(*crop.Placement.Exposure)
		}
	}
	return s.simulator.Put(ctx, crop.ID, SimulatorPotRequest{
		Key:             key,
		CropType:        crop.Type,
		Mode:            string(config.Mode),
		Manual:          values(config.Manual),
		Location:        simLoc,
		IntervalSeconds: config.IntervalSeconds,
		Setting:         setting,
		Exposure:        exposure,
	})
}

func validate(req *VirtualDeviceRequest, placement *geo.Placement) error {
	placed := placement != nil && placement.Location != nil
	if modeOf(req) == ModeWeather && (req == nil || req.Location == nil) && !placed {
		return apperr.BadRequest("Select a location so the crop follows its weather")
	}
	return nil
}

func modeOf(req *VirtualDeviceRequest) Mode {
	if req == nil || req.Mode == nil {
		return ModeAuto
	}
	return *req.Mode
}

func locationOf(crop *CropView, config *VirtualDevice) *Location {
	if crop.Placement != nil && crop.Placement.Location != nil {
		l := crop.Placement.Location
		return &Location{Name: l.Name, Latitude: l.Latitude, Longitude: l.Longitude}
	}
	return config.Location
}

func merge(current *readings.Measures, update *ManualValues) *readings.Measures {
	base := readings.Measures{}
	if current != nil {
		base = *current
	}
	return &readings.Measures{
		Temperature:  first(update.Temperature, base.Temperature),
		Humidity:     first(update.Humidity, base.Humidity),
		Brightness:   first(update.Brightness, base.Brightness),
		Ph:           first(update.Ph, base.Ph),
		Tds:          first(update.Tds, base.Tds),
		Atmosphere:   first(update.Atmosphere, base.Atmosphere),
		SoilMoisture: first(update.SoilMoisture, base.SoilMoisture),
	}
}

func values(m *readings.Measures) map[string]float64 {
	if m == nil {
		return nil
	}
	out := map[string]float64{}
	add := func(k string, v *float64) {
		if v != nil {
			out[k] = *v
		}
	}
	add("temperature", m.Temperature)
	add("humidity", m.Humidity)
	add("brightness", m.Brightness)
	add("ph", m.Ph)
	add("tds", m.Tds)
	add("atmosphere", m.Atmosphere)
	add("soilMoisture", m.SoilMoisture)
	if len(out) == 0 {
		return nil
	}
	return out
}

func first(preferred, fallback *float64) *float64 {
	if preferred != nil {
		return preferred
	}
	return fallback
}

func deref(v *float64) float64 {
	if v == nil {
		return 0
	}
	return *v
}

func inactiveResponse(cropID string, available bool) VirtualDeviceResponse {
	return VirtualDeviceResponse{CropID: cropID, Available: available, ActiveActuators: []ActiveActuator{}}
}

func responseOf(config *VirtualDevice, location *Location, live *SimulatorPot, available bool) VirtualDeviceResponse {
	mode := config.Mode
	interval := config.IntervalSeconds
	updatedAt := config.UpdatedAt
	resp := VirtualDeviceResponse{
		CropID:          config.CropID.Hex(),
		Available:       available,
		Active:          config.IsActive(),
		Running:         live != nil,
		Mode:            &mode,
		Manual:          config.Manual,
		Location:        location,
		IntervalSeconds: &interval,
		ActiveActuators: []ActiveActuator{},
		UpdatedAt:       &updatedAt,
	}
	if live != nil {
		resp.Connected = live.Connected
		resp.LastReading = live.LastReading
		resp.LastPublishedAt = live.LastPublishedAt
		resp.Weather = live.Weather
		resp.WeatherError = live.WeatherError
		if live.ActiveActuators != nil {
			resp.ActiveActuators = live.ActiveActuators
		}
		resp.LastCommand = live.LastCommand
	}
	return resp
}
