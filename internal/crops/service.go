package crops

import (
	"context"
	"crypto/rand"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"strings"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/notifications"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/events"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/geo"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/objectid"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/security"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/virtualdevices"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

// MaxCropsPerUser es el máximo de cultivos por cuenta.
const MaxCropsPerUser = 20

const (
	notFound           = "Crop does not exist"
	virtualHasNoDevice = "A virtual crop does not use credentials: SmartPot simulates it for you"
)

// DeviceProvisioner aprovisiona la cuenta del dispositivo en el broker (lo
// implementa el paquete mqtt).
type DeviceProvisioner interface {
	Provision(ctx context.Context, cropID, key string, rotate bool) error
	Deprovision(ctx context.Context, cropID string) error
}

// ActuatorSeeder crea los actuadores por defecto de un cultivo (lo implementa el
// paquete actuators).
type ActuatorSeeder interface {
	SeedDefaults(ctx context.Context, cropID string, virtual bool) error
}

// Notifier envía los avisos de desconexión con enfriamiento.
type Notifier interface {
	NotifyOnce(ctx context.Context, cooldownKey string, cooldown time.Duration,
		userID, cropID string, t notifications.Type, title, message string) (bool, error)
}

// capabilitiesStore es una extensión opcional del repositorio: el adaptador de
// PostgreSQL la implementa, pero no se añade a Repository para no romper dobles.
type capabilitiesStore interface {
	UpdateDeviceCapabilities(ctx context.Context, id primitive.ObjectID, capabilities json.RawMessage, now time.Time) error
}

// capabilitiesStaleStore marca obsoletas las capacidades sin exigir que existan.
type capabilitiesStaleStore interface {
	MarkDeviceCapabilitiesStale(ctx context.Context, id primitive.ObjectID, now time.Time) error
}

// MQTTPublic son los datos públicos del broker que se entregan al dispositivo.
type MQTTPublic struct {
	Host         string
	Port         int
	TLS          bool
	WebSocketURL string
	TopicPrefix  string
}

// CreatedCrop es el resultado de crear un cultivo.
type CreatedCrop struct {
	Crop        *Crop
	Credentials *DeviceCredentialsResponse
}

// Service implementa la lógica de los cultivos.
type Service struct {
	repo                   Repository
	encryptor              *security.Encryptor
	mqttPublic             MQTTPublic
	provisioner            DeviceProvisioner
	actuators              ActuatorSeeder
	notifier               Notifier
	bus                    *events.Bus
	clock                  func() time.Time
	maxCropsPerUser        int
	maxVirtualCropsPerUser int
}

// NewService crea el servicio de cultivos.
func NewService(repo Repository, encryptor *security.Encryptor, mqttPublic MQTTPublic,
	provisioner DeviceProvisioner, actuators ActuatorSeeder, notifier Notifier, bus *events.Bus,
	clock func() time.Time) *Service {
	return &Service{
		repo: repo, encryptor: encryptor, mqttPublic: mqttPublic,
		provisioner: provisioner, actuators: actuators, notifier: notifier, bus: bus, clock: clock,
	}
}

// SetProvisioner fija el aprovisionador real del broker (gateway MQTT).
func (s *Service) SetProvisioner(p DeviceProvisioner) {
	s.provisioner = p
}

// SetActuatorSeeder fija el sembrador de actuadores real.
func (s *Service) SetActuatorSeeder(a ActuatorSeeder) {
	s.actuators = a
}

// SetQuotas configura los límites máximos por cuenta.
func (s *Service) SetQuotas(maxCrops, maxVirtual int) {
	if maxCrops > 0 {
		s.maxCropsPerUser = maxCrops
	}
	if maxVirtual > 0 {
		s.maxVirtualCropsPerUser = maxVirtual
	}
}

func (s *Service) maxCrops() int {
	if s.maxCropsPerUser > 0 {
		return s.maxCropsPerUser
	}
	return MaxCropsPerUser
}

func (s *Service) maxVirtual() int {
	if s.maxVirtualCropsPerUser > 0 {
		return s.maxVirtualCropsPerUser
	}
	return virtualdevices.MaxPerAccount
}

// Create crea un cultivo real o virtual con sus actuadores y cuenta en el broker.
func (s *Service) Create(ctx context.Context, ownerID string, req CropRequest) (CreatedCrop, error) {
	ownerOID, ok := objectid.Parse(ownerID)
	if !ok {
		return CreatedCrop{}, apperr.Unauthorized("Your session is invalid or has expired. Please log in again")
	}
	now := s.clock()
	key := newDeviceKey()
	cipher, err := s.encryptor.Encrypt(key)
	if err != nil {
		return CreatedCrop{}, err
	}
	kind := KindReal
	if req.Kind != nil {
		kind = *req.Kind
	}
	form := FormPot
	if req.Form != nil {
		form = *req.Form
	}
	rotatedAt := now
	crop := &Crop{
		OwnerID:   ownerOID,
		Name:      strings.TrimSpace(req.Name),
		Type:      *req.Type,
		Kind:      kind,
		Form:      form,
		Placement: req.Placement.ToPlacement(),
		Device:    &Device{KeyCiphertext: cipher, KeyRotatedAt: &rotatedAt},
		CreatedAt: now,
		UpdatedAt: now,
	}
	// El cupo se comprueba y se inserta en una sola transacción con bloqueo por
	// dueño: las ráfagas concurrentes no pueden superar el límite de cultivos ni de virtuales.
	maxCrops := s.maxCrops()
	maxVirtual := s.maxVirtual()
	if err := s.repo.InsertWithQuota(ctx, crop, maxCrops, maxVirtual); err != nil {
		switch {
		case errors.Is(err, ErrCropLimit):
			return CreatedCrop{}, apperr.BadRequest(fmt.Sprintf("You reached the maximum of %d crops per account", maxCrops))
		case errors.Is(err, ErrVirtualCropLimit):
			if maxVirtual == 5 {
				return CreatedCrop{}, apperr.Conflict("You can have up to 5 virtual crops")
			}
			return CreatedCrop{}, apperr.Conflict(fmt.Sprintf("You can have up to %d virtual crops", maxVirtual))
		default:
			return CreatedCrop{}, err
		}
	}
	if err := s.actuators.SeedDefaults(ctx, crop.HexID(), crop.IsVirtual()); err != nil {
		return CreatedCrop{}, err
	}
	// La cuenta en el broker (incluso la de un virtual) es best-effort: Paho reintenta.
	if err := s.provisioner.Provision(ctx, crop.HexID(), key, false); err != nil {
		slog.Warn("could not provision device in broker", "cropId", crop.HexID(), "err", err)
	}
	slog.Info("crop created", "kind", kind, "cropId", crop.HexID())

	var creds *DeviceCredentialsResponse
	if !crop.IsVirtual() {
		c := s.credentials(crop, key)
		creds = &c
	}
	return CreatedCrop{Crop: crop, Credentials: creds}, nil
}

// List devuelve los cultivos del dueño.
func (s *Service) List(ctx context.Context, ownerID string) ([]Crop, error) {
	ownerOID, ok := objectid.Parse(ownerID)
	if !ok {
		return []Crop{}, nil
	}
	return s.repo.ListByOwner(ctx, ownerOID)
}

// GetOwned devuelve un cultivo comprobando el dueño (404 si no existe o es de otro).
func (s *Service) GetOwned(ctx context.Context, ownerID, cropID string) (*Crop, error) {
	if err := objectid.Require(cropID, notFound); err != nil {
		return nil, err
	}
	ownerOID, _ := objectid.Parse(ownerID)
	idOID, _ := objectid.Parse(cropID)
	crop, err := s.repo.FindByIDAndOwner(ctx, idOID, ownerOID)
	if err != nil {
		return nil, err
	}
	if crop == nil {
		return nil, apperr.NotFound(notFound)
	}
	return crop, nil
}

// Find busca un cultivo por id, sin comprobar dueño (nil si no existe).
func (s *Service) Find(ctx context.Context, cropID string) (*Crop, error) {
	oid, ok := objectid.Parse(cropID)
	if !ok {
		return nil, nil
	}
	return s.repo.FindByID(ctx, oid)
}

// Update cambia nombre, especie, forma y lugar. El tipo (real/virtual) no cambia.
func (s *Service) Update(ctx context.Context, ownerID, cropID string, req CropRequest) (*Crop, error) {
	crop, err := s.GetOwned(ctx, ownerID, cropID)
	if err != nil {
		return nil, err
	}
	if req.Kind != nil && *req.Kind != crop.KindOrDefault() {
		return nil, apperr.BadRequest("A crop cannot transition between real and virtual: create a new one")
	}
	crop.Name = strings.TrimSpace(req.Name)
	crop.Type = *req.Type
	if req.Form != nil {
		crop.Form = *req.Form
	}
	placement := crop.Placement
	if req.Placement != nil {
		placement = req.Placement.ToPlacement()
	}
	moved := !geo.Equal(placement, crop.Placement)
	crop.Placement = placement
	crop.Health = nil
	crop.UpdatedAt = s.clock()
	if err := s.repo.Save(ctx, crop); err != nil {
		return nil, err
	}
	if moved {
		s.bus.Publish(PlacementChangedEvent{CropID: crop.HexID()})
	}
	return crop, nil
}

// SetLocation cambia solo la ubicación del cultivo (la simulación la comparte).
func (s *Service) SetLocation(ctx context.Context, crop *Crop, location geo.Location) (*Crop, error) {
	var placement geo.Placement
	if crop.Placement == nil {
		setting := geo.Outdoor
		exposure := geo.FullSun
		placement = geo.Placement{Setting: &setting, Exposure: &exposure, Location: &location}
	} else {
		placement = crop.Placement.WithLocation(&location)
	}
	if geo.Equal(&placement, crop.Placement) {
		return crop, nil
	}
	crop.Placement = &placement
	if err := s.repo.UpdatePlacement(ctx, crop.ID, &placement, s.clock()); err != nil {
		return nil, err
	}
	return crop, nil
}

// SetAutomation activa o desactiva el modo automático de un cultivo.
func (s *Service) SetAutomation(ctx context.Context, ownerID, cropID string, enabled bool) (*Crop, error) {
	crop, err := s.GetOwned(ctx, ownerID, cropID)
	if err != nil {
		return nil, err
	}
	crop.AutomationEnabled = enabled
	crop.UpdatedAt = s.clock()
	if err := s.repo.Save(ctx, crop); err != nil {
		return nil, err
	}
	return crop, nil
}

// SetAutomationBulk aplica el modo automático a varios cultivos; sin ids, a todos.
func (s *Service) SetAutomationBulk(ctx context.Context, ownerID string, cropIDs []string, enabled bool) ([]Crop, error) {
	var crops []Crop
	if len(cropIDs) == 0 {
		list, err := s.List(ctx, ownerID)
		if err != nil {
			return nil, err
		}
		crops = list
	} else {
		seen := make(map[string]bool)
		for _, id := range cropIDs {
			if seen[id] {
				continue
			}
			seen[id] = true
			crop, err := s.GetOwned(ctx, ownerID, id)
			if err != nil {
				return nil, err
			}
			crops = append(crops, *crop)
		}
	}
	now := s.clock()
	for i := range crops {
		crops[i].AutomationEnabled = enabled
		crops[i].UpdatedAt = now
		if err := s.repo.Save(ctx, &crops[i]); err != nil {
			return nil, err
		}
	}
	return crops, nil
}

// DeviceInfo devuelve los datos de conexión del dispositivo (sin la clave).
func (s *Service) DeviceInfo(ctx context.Context, ownerID, cropID string) (DeviceCredentialsResponse, error) {
	crop, err := s.getOwnedReal(ctx, ownerID, cropID)
	if err != nil {
		return DeviceCredentialsResponse{}, err
	}
	return s.credentials(crop, ""), nil
}

// RotateDeviceKey genera una nueva clave y desconecta la sesión anterior.
func (s *Service) RotateDeviceKey(ctx context.Context, ownerID, cropID string) (DeviceCredentialsResponse, error) {
	crop, err := s.getOwnedReal(ctx, ownerID, cropID)
	if err != nil {
		return DeviceCredentialsResponse{}, err
	}
	key := newDeviceKey()
	cipher, err := s.encryptor.Encrypt(key)
	if err != nil {
		return DeviceCredentialsResponse{}, err
	}
	now := s.clock()
	if crop.Device == nil {
		crop.Device = &Device{}
	}
	crop.Device.KeyCiphertext = cipher
	crop.Device.KeyRotatedAt = &now
	crop.UpdatedAt = now
	if err := s.repo.UpdateDeviceKey(ctx, crop.ID, cipher, now, now); err != nil {
		return DeviceCredentialsResponse{}, err
	}
	// La caché de autenticación del broker se invalida ANTES de cortar sesiones:
	// así ninguna reconexión puede validar la clave anterior.
	s.bus.Publish(DeviceKeyRotatedEvent{CropID: crop.HexID()})
	if err := s.provisioner.Provision(ctx, crop.HexID(), key, true); err != nil {
		slog.Warn("could not re-provision device", "cropId", crop.HexID(), "err", err)
	}
	return s.credentials(crop, key), nil
}

// DeviceKey devuelve la clave en claro, para los servicios internos (el simulador) y
// para autenticar al dispositivo cuando el broker lo consulta.
func (s *Service) DeviceKey(crop *Crop) (string, bool) {
	if crop.Device == nil || crop.Device.KeyCiphertext == "" {
		return "", false
	}
	key, err := s.encryptor.Decrypt(crop.Device.KeyCiphertext)
	if err != nil {
		return "", false
	}
	return key, true
}

// Delete borra un cultivo del dueño con todos sus datos.
func (s *Service) Delete(ctx context.Context, ownerID, cropID string) error {
	crop, err := s.GetOwned(ctx, ownerID, cropID)
	if err != nil {
		return err
	}
	return s.deleteCascade(ctx, crop)
}

// PlacementBackfill es el lugar que se completa en un cultivo anterior.
type PlacementBackfill struct {
	CropID    string
	Placement geo.Placement
}

// Backfill completa el tipo, la forma y el lugar de los cultivos anteriores a
// estos campos. Es idempotente y corre al arrancar.
func (s *Service) Backfill(ctx context.Context, virtualCropIDs []string, placements []PlacementBackfill) error {
	virtualOIDs := make([]primitive.ObjectID, 0, len(virtualCropIDs))
	for _, id := range virtualCropIDs {
		if oid, ok := objectid.Parse(id); ok {
			virtualOIDs = append(virtualOIDs, oid)
		}
	}
	if err := s.repo.BackfillKind(ctx, virtualOIDs, KindVirtual); err != nil {
		return err
	}
	if err := s.repo.BackfillKindAll(ctx, KindReal); err != nil {
		return err
	}
	if err := s.repo.BackfillFormAll(ctx, FormPot); err != nil {
		return err
	}
	for _, p := range placements {
		if oid, ok := objectid.Parse(p.CropID); ok {
			if err := s.repo.BackfillPlacement(ctx, oid, p.Placement); err != nil {
				return err
			}
		}
	}
	return nil
}

// DeleteAllOwnedBy borra todos los cultivos de una cuenta (borrado en cascada).
func (s *Service) DeleteAllOwnedBy(ctx context.Context, ownerID string) error {
	crops, err := s.List(ctx, ownerID)
	if err != nil {
		return err
	}
	for i := range crops {
		if err := s.deleteCascade(ctx, &crops[i]); err != nil {
			return err
		}
	}
	return nil
}

// MarkDeviceSeen actualiza el estado del dispositivo (implementa readings.Crops).
func (s *Service) MarkDeviceSeen(ctx context.Context, cropID string, online *bool) {
	oid, ok := objectid.Parse(cropID)
	if !ok {
		return
	}
	if err := s.repo.MarkDeviceSeen(ctx, oid, online, s.clock()); err != nil {
		slog.Warn("could not update device status", "cropId", cropID, "err", err)
	}
}

// UpdateDeviceCapabilities persiste la declaración de hardware validada del dispositivo.
func (s *Service) UpdateDeviceCapabilities(ctx context.Context, cropID string, capabilities json.RawMessage) {
	oid, ok := objectid.Parse(cropID)
	if !ok {
		return
	}
	store, ok := s.repo.(capabilitiesStore)
	if !ok {
		slog.Warn("capabilities are not supported by the repository", "cropId", cropID)
		return
	}
	if err := store.UpdateDeviceCapabilities(ctx, oid, capabilities, s.clock()); err != nil {
		slog.Warn("could not update device capabilities", "cropId", cropID, "err", err)
	}
}

// UpdateDeviceStatus marca el dispositivo en línea o no y avisa al desconectarse.
// Si el estado no cambió no se escribe ni se notifica: una ráfaga de mensajes de
// estado del mismo cultivo no satura el pool de PostgreSQL (C-07).
func (s *Service) UpdateDeviceStatus(ctx context.Context, cropID string, online bool) {
	crop, err := s.Find(ctx, cropID)
	if err != nil || crop == nil {
		return
	}
	if !online {
		// El LWT debe marcar obsoletas las capacidades aunque "online" ya fuera false:
		// el dispositivo pudo cambiar de hardware mientras estaba desconectado.
		s.markDeviceCapabilitiesStale(ctx, cropID)
	}
	if crop.Device != nil && crop.Device.Online == online {
		return
	}
	s.MarkDeviceSeen(ctx, cropID, &online)
	if !online {
		_, _ = s.notifier.NotifyOnce(ctx, "offline:"+cropID, time.Hour, crop.OwnerID.Hex(), cropID,
			notifications.TypeDevice, "Crop disconnected",
			"«"+crop.Name+"» lost connection with SmartPot.")
	}
}

// markDeviceCapabilitiesStale marca obsoletas las capacidades si el repositorio lo admite.
func (s *Service) markDeviceCapabilitiesStale(ctx context.Context, cropID string) {
	oid, ok := objectid.Parse(cropID)
	if !ok {
		return
	}
	store, ok := s.repo.(capabilitiesStaleStore)
	if !ok {
		return
	}
	if err := store.MarkDeviceCapabilitiesStale(ctx, oid, s.clock()); err != nil {
		slog.Warn("could not mark device capabilities as stale", "cropId", cropID, "err", err)
	}
}

// UpdateHealth guarda la última evaluación de salud del cultivo.
func (s *Service) UpdateHealth(ctx context.Context, cropID string, health CropHealth) {
	oid, ok := objectid.Parse(cropID)
	if !ok {
		return
	}
	if err := s.repo.UpdateHealth(ctx, oid, &health); err != nil {
		slog.Warn("could not update crop health", "cropId", cropID, "err", err)
	}
}

// Exists indica si el cultivo existe (implementa readings.Crops). Un error de
// base de datos se devuelve como error para no confundirlo con "no existe".
func (s *Service) Exists(ctx context.Context, cropID string) (bool, error) {
	crop, err := s.Find(ctx, cropID)
	if err != nil {
		return false, err
	}
	return crop != nil, nil
}

// RequireOwned comprueba el dueño del cultivo (implementa readings.Crops).
func (s *Service) RequireOwned(ctx context.Context, ownerID, cropID string) error {
	_, err := s.GetOwned(ctx, ownerID, cropID)
	return err
}

// RequireOwnedVirtual comprueba que el cultivo pertenezca al dueño y sea virtual (implementa readings.Crops).
func (s *Service) RequireOwnedVirtual(ctx context.Context, ownerID, cropID string) error {
	crop, err := s.GetOwned(ctx, ownerID, cropID)
	if err != nil {
		return err
	}
	if !crop.IsVirtual() {
		return apperr.BadRequest("Manual readings are only allowed for virtual crops")
	}
	return nil
}

func (s *Service) getOwnedReal(ctx context.Context, ownerID, cropID string) (*Crop, error) {
	crop, err := s.GetOwned(ctx, ownerID, cropID)
	if err != nil {
		return nil, err
	}
	if crop.IsVirtual() {
		return nil, apperr.BadRequest(virtualHasNoDevice)
	}
	return crop, nil
}

func (s *Service) deleteCascade(ctx context.Context, crop *Crop) error {
	if err := s.repo.Delete(ctx, crop.ID); err != nil {
		return err
	}
	// El evento invalida la caché de autenticación antes de cortar las sesiones:
	// una reconexión con la clave vieja no puede sobrevivir al apagado.
	s.bus.Publish(DeletedEvent{CropID: crop.HexID(), OwnerID: crop.OwnerID.Hex()})
	if err := s.provisioner.Deprovision(ctx, crop.HexID()); err != nil {
		slog.Warn("could not deprovision device from broker", "cropId", crop.HexID(), "err", err)
	}
	slog.Info("crop deleted", "cropId", crop.HexID())
	return nil
}

func (s *Service) credentials(crop *Crop, key string) DeviceCredentialsResponse {
	id := crop.HexID()
	var rotatedAt *time.Time
	if crop.Device != nil {
		rotatedAt = crop.Device.KeyRotatedAt
	}
	prefix := s.mqttPublic.TopicPrefix
	return DeviceCredentialsResponse{
		Host:         s.mqttPublic.Host,
		Port:         s.mqttPublic.Port,
		TLS:          s.mqttPublic.TLS,
		WebsocketURL: s.mqttPublic.WebSocketURL,
		Username:     id,
		Key:          key,
		Topics: DeviceTopics{
			Telemetry:  prefix + "/" + id + "/telemetry",
			Commands:   prefix + "/" + id + "/commands",
			CommandAck: prefix + "/" + id + "/commands/ack",
			Status:     prefix + "/" + id + "/status",
		},
		KeyRotatedAt: rotatedAt,
	}
}

func newDeviceKey() string {
	b := make([]byte, 24)
	_, _ = rand.Read(b)
	return base64.RawURLEncoding.EncodeToString(b)
}
