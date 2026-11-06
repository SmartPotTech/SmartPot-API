package mqtt

import (
	"context"
	"log/slog"
	"strings"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/commands"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/crops"
	"github.com/SmartPotTech/SmartPot-API-Go/internal/readings"
)

// MessageHandler enruta cada mensaje del broker según su tópico. Un mensaje
// inválido se registra y se descarta: nunca debe tumbar la suscripción.
type MessageHandler struct {
	topics   *TopicResolver
	readings *readings.Service
	commands *commands.Service
	crops    *crops.Service
	log      *slog.Logger
}

// NewMessageHandler crea el enrutador de mensajes.
func NewMessageHandler(topics *TopicResolver, readingsSvc *readings.Service, commandsSvc *commands.Service,
	cropsSvc *crops.Service, logger *slog.Logger) *MessageHandler {
	return &MessageHandler{topics: topics, readings: readingsSvc, commands: commandsSvc, crops: cropsSvc,
		log: logger}
}

// Route procesa un mensaje entrante. Corre en una goroutine por mensaje.
func (h *MessageHandler) Route(topic, payload string) {
	defer func() {
		if r := recover(); r != nil {
			h.log.Warn("message dropped after panic", "topic", topic, "panic", r)
		}
	}()

	parsed, ok := h.topics.Parse(topic)
	if !ok {
		h.log.Debug("MQTT topic ignored", "topic", topic)
		return
	}
	ctx := context.Background()
	switch parsed.Kind {
	case KindTelemetry:
		measures, err := ParseTelemetry(payload)
		if err != nil {
			h.log.Warn("telemetry dropped", "topic", topic, "err", err)
			return
		}
		if _, err := h.readings.RecordFromDevice(ctx, parsed.CropID, measures); err != nil {
			h.log.Warn("could not store device telemetry", "cropId", parsed.CropID, "err", err)
		}
	case KindCommandAck:
		ack, err := ParseAck(payload)
		if err != nil {
			h.log.Warn("command ACK dropped", "topic", topic, "err", err)
			return
		}
		h.commands.Acknowledge(ctx, parsed.CropID, ack)
	case KindStatus:
		online := strings.EqualFold(strings.TrimSpace(payload), "online")
		h.crops.UpdateDeviceStatus(ctx, parsed.CropID, online)
	case KindCapabilities:
		caps, err := ParseCapabilities(payload)
		if err != nil {
			h.log.Warn("device capabilities dropped", "cropId", parsed.CropID, "err", err)
			return
		}
		h.crops.UpdateDeviceCapabilities(ctx, parsed.CropID, caps)
		h.log.Info("device capabilities accepted", "cropId", parsed.CropID, "bytes", len(payload))
	}
}
