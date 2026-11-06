package commands

import (
	"context"
	"encoding/json"
	"errors"
	"log/slog"
	"strings"
	"time"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/actuators"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/crops"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/notifications"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/apperr"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/objectid"
	"go.mongodb.org/mongo-driver/bson/primitive"
)

const failureNoticeCooldown = 15 * time.Minute

// Publisher publica las órdenes en el broker (lo implementa el gateway MQTT).
type Publisher interface {
	IsEnabled() bool
	PublishCommand(cropID, payload string) bool
}

// Crops es lo que el servicio de comandos necesita de los cultivos.
type Crops interface {
	GetOwned(ctx context.Context, ownerID, cropID string) (*crops.Crop, error)
	Find(ctx context.Context, cropID string) (*crops.Crop, error)
	List(ctx context.Context, ownerID string) ([]crops.Crop, error)
}

// Actuators es lo que el servicio de comandos necesita de los actuadores.
type Actuators interface {
	GetForCrop(ctx context.Context, cropID, actuatorID string) (*actuators.Actuator, error)
	FindByType(ctx context.Context, cropID string, t actuators.Type) (*actuators.Actuator, error)
	UpdateState(ctx context.Context, actuatorID string, active bool, runningUntil *time.Time)
}

// Notifier envía los avisos de fallo con enfriamiento.
type Notifier interface {
	NotifyOnce(ctx context.Context, cooldownKey string, cooldown time.Duration,
		userID, cropID string, t notifications.Type, title, message string) (bool, error)
}

// Service implementa la lógica de comandos.
type Service struct {
	repo      Repository
	crops     Crops
	actuators Actuators
	notifier  Notifier
	publisher Publisher
	clock     func() time.Time
	timeout   time.Duration
}

// NewService crea el servicio de comandos. El publisher se fija luego con
// SetPublisher (el gateway MQTT se construye después).
func NewService(repo Repository, cropsSvc Crops, actuatorsSvc Actuators,
	notifier Notifier, clock func() time.Time, timeout time.Duration) *Service {
	return &Service{repo: repo, crops: cropsSvc, actuators: actuatorsSvc, notifier: notifier,
		publisher: disabledPublisher{}, clock: clock, timeout: timeout}
}

// SetPublisher fija el publicador real (el gateway MQTT).
func (s *Service) SetPublisher(p Publisher) {
	s.publisher = p
}

// Request crea y envía una orden a un actuador (202; la confirmación llega luego).
func (s *Service) Request(ctx context.Context, ownerID, cropID string, req CommandRequest) (*Command, error) {
	crop, err := s.crops.GetOwned(ctx, ownerID, cropID)
	if err != nil {
		return nil, err
	}
	actuator, err := s.actuators.GetForCrop(ctx, crop.HexID(), req.ActuatorID)
	if err != nil {
		return nil, err
	}
	if !s.publisher.IsEnabled() {
		return nil, apperr.Unavailable("Device communication is not enabled on this server")
	}
	inFlight, err := s.repo.ExistsInFlight(ctx, actuator.ID)
	if err != nil {
		return nil, err
	}
	if inFlight {
		return nil, apperr.Conflict("There is already a command in progress for this actuator: wait for confirmation")
	}
	if msg := s.unchanged(actuator, *req.Action, req.DurationSeconds); msg != "" {
		return nil, apperr.Conflict(msg)
	}
	cmd, err := s.create(ctx, crop, actuator, *req.Action, req.DurationSeconds, SourceUser, "")
	if errors.Is(err, ErrInFlight) {
		return nil, apperr.Conflict("There is already a command in progress for this actuator: wait for confirmation")
	}
	if err != nil {
		return nil, err
	}
	return s.dispatch(ctx, cmd)
}

// RequestFromAgent envía una orden decidida por el agente de IA. Se omite si el
// cultivo no tiene el actuador, ya hay uno en curso o no cambiaría nada.
func (s *Service) RequestFromAgent(ctx context.Context, crop *crops.Crop, t actuators.Type, action Action,
	durationSeconds *int, reason string) (*Command, bool) {
	actuator, err := s.actuators.FindByType(ctx, crop.HexID(), t)
	if err != nil || actuator == nil || !s.publisher.IsEnabled() {
		return nil, false
	}
	inFlight, err := s.repo.ExistsInFlight(ctx, actuator.ID)
	if err != nil || inFlight {
		return nil, false
	}
	running := actuator.IsRunning(s.clock())
	if (action == Deactivate) == running { // DEACTIVATE requiere que esté corriendo; ACTIVATE, que no
		return nil, false
	}
	cmd, err := s.create(ctx, crop, actuator, action, durationSeconds, SourceAgent, reason)
	if err != nil {
		return nil, false
	}
	sent, err := s.dispatch(ctx, cmd)
	if err != nil {
		return nil, false
	}
	return sent, true
}

// RequestBulk envía la misma orden a varios cultivos; cada uno se informa aparte.
func (s *Service) RequestBulk(ctx context.Context, ownerID string, req BulkCommandRequest) (BulkCommandResponse, error) {
	if !s.publisher.IsEnabled() {
		return BulkCommandResponse{}, apperr.Unavailable("Device communication is not enabled on this server")
	}
	cropsList, err := s.targetCrops(ctx, ownerID, req.CropIds)
	if err != nil {
		return BulkCommandResponse{}, err
	}
	results := make([]BulkResult, 0, len(cropsList))
	for i := range cropsList {
		crop := &cropsList[i]
		actuator, err := s.actuators.FindByType(ctx, crop.HexID(), *req.ActuatorType)
		if err != nil {
			return BulkCommandResponse{}, err
		}
		switch {
		case actuator == nil:
			results = append(results, skipped(crop, "The crop does not have this actuator"))
		default:
			inFlight, err := s.repo.ExistsInFlight(ctx, actuator.ID)
			if err != nil {
				return BulkCommandResponse{}, err
			}
			if inFlight {
				results = append(results, skipped(crop, "There is already a command in progress for this actuator"))
				continue
			}
			if msg := s.unchanged(actuator, *req.Action, req.DurationSeconds); msg != "" {
				results = append(results, skipped(crop, msg))
				continue
			}
			cmd, err := s.create(ctx, crop, actuator, *req.Action, req.DurationSeconds, SourceUser, "Bulk action")
			if errors.Is(err, ErrInFlight) {
				results = append(results, skipped(crop, "There is already a command in progress for this actuator"))
				continue
			}
			if err != nil {
				return BulkCommandResponse{}, err
			}
			cmd, err = s.dispatch(ctx, cmd)
			if err != nil {
				return BulkCommandResponse{}, err
			}
			status := "FAILED"
			if cmd.Status == StatusSent {
				status = "SENT"
			}
			results = append(results, BulkResult{CropID: crop.HexID(), CropName: crop.Name, Status: status,
				CommandID: cmd.ID.Hex(), Message: cmd.Message})
		}
	}
	return NewBulkResponse(results), nil
}

// ListForOwner devuelve los comandos de todos los cultivos de la cuenta.
func (s *Service) ListForOwner(ctx context.Context, ownerID string, limit int) ([]Command, error) {
	cropsList, err := s.crops.List(ctx, ownerID)
	if err != nil {
		return nil, err
	}
	if len(cropsList) == 0 {
		return []Command{}, nil
	}
	ids := make([]primitive.ObjectID, 0, len(cropsList))
	for i := range cropsList {
		ids = append(ids, cropsList[i].ID)
	}
	return s.repo.ListByCrops(ctx, ids, clamp(limit, 1, 200))
}

// List devuelve el historial de un cultivo.
func (s *Service) List(ctx context.Context, ownerID, cropID string, limit int) ([]Command, error) {
	if _, err := s.crops.GetOwned(ctx, ownerID, cropID); err != nil {
		return nil, err
	}
	oid, _ := objectid.Parse(cropID)
	return s.repo.ListByCrop(ctx, oid, clamp(limit, 1, 100))
}

// Acknowledge procesa la confirmación del dispositivo.
func (s *Service) Acknowledge(ctx context.Context, cropID string, ack AckMessage) {
	if !objectid.Valid(ack.ID) {
		return
	}
	cropOID, ok := objectid.Parse(cropID)
	if !ok {
		return
	}
	idOID, _ := objectid.Parse(ack.ID)
	// El ACK solo vale para un comando del cultivo que lo publicó.
	existing, err := s.repo.FindByIDAndCrop(ctx, idOID, cropOID)
	if err != nil || existing == nil {
		return
	}
	now := s.clock()
	status := StatusFailed
	if ack.Executed() {
		status = StatusExecuted
	}
	// Transición condicional: si el barrido ya expiró la orden, el ACK no la revive.
	cmd, won, err := s.repo.Complete(ctx, idOID, status, truncate(ack.Message), now)
	if err != nil || !won || cmd == nil {
		return
	}
	if status == StatusExecuted {
		on := cmd.Action == Activate
		var runningUntil *time.Time
		if on && cmd.DurationSeconds != nil {
			until := now.Add(time.Duration(*cmd.DurationSeconds) * time.Second)
			runningUntil = &until
		}
		s.actuators.UpdateState(ctx, cmd.ActuatorID.Hex(), on && cmd.DurationSeconds == nil, runningUntil)
	} else {
		s.notifyFailure(ctx, cmd, "The device could not execute the command: "+orDefault(ack.Message))
	}
}

// CountSince cuenta los comandos de varios cultivos desde un instante (panel general).
func (s *Service) CountSince(ctx context.Context, cropIDs []string, since time.Time) (int64, error) {
	oids := make([]primitive.ObjectID, 0, len(cropIDs))
	for _, id := range cropIDs {
		if oid, ok := objectid.Parse(id); ok {
			oids = append(oids, oid)
		}
	}
	return s.repo.CountByCropsSince(ctx, oids, since)
}

// DeleteByCrop borra los comandos de un cultivo (borrado en cascada por evento).
func (s *Service) DeleteByCrop(ctx context.Context, cropID string) error {
	oid, ok := objectid.Parse(cropID)
	if !ok {
		return nil
	}
	return s.repo.DeleteByCrop(ctx, oid)
}

// ExpireStale marca como EXPIRED los comandos sin confirmar a tiempo (ticker).
func (s *Service) ExpireStale(ctx context.Context) {
	limit := s.clock().Add(-s.timeout)
	stale, err := s.repo.FindStaleSent(ctx, limit)
	if err != nil {
		slog.Warn("could not check expired commands", "err", err)
		return
	}
	for i := range stale {
		candidate := &stale[i]
		now := s.clock()
		cmd, won, err := s.repo.Expire(ctx, candidate.ID,
			"The device did not confirm the command in time", now)
		if err != nil || !won || cmd == nil {
			// Otro proceso (o un ACK) ya cerró la orden: no se notifica dos veces.
			continue
		}
		s.notifyFailure(ctx, cmd, "The device did not respond to the command. Verify that it is connected.")
	}
}

func (s *Service) targetCrops(ctx context.Context, ownerID string, cropIDs []string) ([]crops.Crop, error) {
	if len(cropIDs) == 0 {
		return s.crops.List(ctx, ownerID)
	}
	seen := make(map[string]bool)
	var out []crops.Crop
	for _, id := range cropIDs {
		if seen[id] {
			continue
		}
		seen[id] = true
		crop, err := s.crops.GetOwned(ctx, ownerID, id)
		if err != nil {
			return nil, err
		}
		out = append(out, *crop)
	}
	return out, nil
}

// unchanged devuelve el motivo si la orden no cambiaría nada; "" si sí cambia.
func (s *Service) unchanged(actuator *actuators.Actuator, action Action, durationSeconds *int) string {
	now := s.clock()
	if action == Deactivate && !actuator.IsRunning(now) {
		return actuator.Type.AlreadyIn(false)
	}
	if action == Activate && durationSeconds == nil && actuator.Active {
		return actuator.Type.AlreadyIn(true)
	}
	return ""
}

func (s *Service) create(ctx context.Context, crop *crops.Crop, actuator *actuators.Actuator, action Action,
	durationSeconds *int, source Source, reason string) (*Command, error) {
	var duration *int
	if action == Activate {
		duration = durationSeconds
	}
	cmd := &Command{
		CropID:          crop.ID,
		ActuatorID:      actuator.ID,
		ActuatorType:    actuator.Type,
		Action:          action,
		DurationSeconds: duration,
		Status:          StatusPending,
		Source:          source,
		Reason:          reason,
		CreatedAt:       s.clock(),
	}
	if err := s.repo.Insert(ctx, cmd); err != nil {
		return nil, err
	}
	return cmd, nil
}

func (s *Service) dispatch(ctx context.Context, cmd *Command) (*Command, error) {
	payload, err := json.Marshal(CommandMessage{
		ID:              cmd.ID.Hex(),
		Actuator:        string(cmd.ActuatorType),
		Action:          string(cmd.Action),
		DurationSeconds: cmd.DurationSeconds,
	})
	if err != nil {
		return nil, err
	}
	now := s.clock()
	if s.publisher.PublishCommand(cmd.CropID.Hex(), string(payload)) {
		won, err := s.repo.MarkSent(ctx, cmd.ID, now)
		if err != nil {
			return nil, err
		}
		if !won {
			// Un ACK rápido ya cerró la orden: se devuelve el estado terminal real.
			return s.repo.FindByIDAndCrop(ctx, cmd.ID, cmd.CropID)
		}
		cmd.Status = StatusSent
		cmd.SentAt = &now
	} else {
		failed, won, err := s.repo.Complete(ctx, cmd.ID, StatusFailed,
			"The MQTT broker is not available at this moment", now)
		if err != nil {
			return nil, err
		}
		if !won {
			return s.repo.FindByIDAndCrop(ctx, cmd.ID, cmd.CropID)
		}
		cmd = failed
	}
	slog.Info("command dispatched", "action", cmd.Action, "actuator", cmd.ActuatorType,
		"cropId", cmd.CropID.Hex(), "status", cmd.Status)
	return cmd, nil
}

func (s *Service) notifyFailure(ctx context.Context, cmd *Command, message string) {
	crop, err := s.crops.Find(ctx, cmd.CropID.Hex())
	if err != nil || crop == nil {
		return
	}
	// La clave incluye el id del comando: dos órdenes expiradas en el mismo
	// cultivo generan dos avisos, no uno (R3c-bis).
	_, _ = s.notifier.NotifyOnce(ctx, "command-failure:"+crop.HexID()+":"+cmd.ID.Hex(), failureNoticeCooldown,
		crop.OwnerID.Hex(), crop.HexID(), notifications.TypeCommand, "Unexecuted command on "+crop.Name, message)
}

func skipped(crop *crops.Crop, reason string) BulkResult {
	return BulkResult{CropID: crop.HexID(), CropName: crop.Name, Status: "SKIPPED", Message: reason}
}

func truncate(value string) string {
	r := []rune(value)
	if len(r) <= 200 {
		return value
	}
	return string(r[:200])
}

func orDefault(value string) string {
	if strings.TrimSpace(value) == "" {
		return "without detail"
	}
	return truncate(value)
}

func clamp(v, lo, hi int) int {
	if v < lo {
		return lo
	}
	if v > hi {
		return hi
	}
	return v
}

// disabledPublisher es el publicador por defecto cuando MQTT no está cableado.
type disabledPublisher struct{}

func (disabledPublisher) IsEnabled() bool                    { return false }
func (disabledPublisher) PublishCommand(string, string) bool { return false }
