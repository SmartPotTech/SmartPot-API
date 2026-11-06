// Package mqtt implementa el cliente del broker, el contrato de tópicos v1, la
// autenticación de dispositivos (que el broker EMQX delega en la API) y el corte
// de sesiones al rotar o borrar la clave de un cultivo.
package mqtt

import (
	"strings"

	"github.com/SmartPotTech/SmartPot-API-Go/internal/platform/objectid"
)

// Kind es el tipo de mensaje de un tópico del dispositivo.
type Kind int

const (
	KindTelemetry Kind = iota
	KindCommandAck
	KindStatus
	KindCapabilities
)

// ParsedTopic es el resultado de interpretar un tópico entrante.
type ParsedTopic struct {
	CropID string
	Kind   Kind
}

// TopicResolver arma e interpreta los tópicos v1: {prefijo}/{cropId}/telemetry |
// commands | commands/ack | status | capabilities.
type TopicResolver struct {
	prefix string
}

// NewTopicResolver crea el resolutor con el prefijo configurado.
func NewTopicResolver(prefix string) *TopicResolver {
	return &TopicResolver{prefix: prefix}
}

func (r *TopicResolver) Telemetry(cropID string) string {
	return r.prefix + "/" + cropID + "/telemetry"
}
func (r *TopicResolver) Commands(cropID string) string { return r.prefix + "/" + cropID + "/commands" }
func (r *TopicResolver) CommandAck(cropID string) string {
	return r.prefix + "/" + cropID + "/commands/ack"
}
func (r *TopicResolver) Status(cropID string) string { return r.prefix + "/" + cropID + "/status" }
func (r *TopicResolver) Capabilities(cropID string) string {
	return r.prefix + "/" + cropID + "/capabilities"
}

// Subscriptions son los tópicos que la API escucha de todos los cultivos.
func (r *TopicResolver) Subscriptions() []string {
	return []string{r.Telemetry("+"), r.CommandAck("+"), r.Status("+"), r.Capabilities("+")}
}

// SharedSubscriptions devuelve los tópicos usando el prefijo de suscripción compartida de EMQX ($share/{group}/{topic}).
// Permite que múltiples réplicas de SmartPot-API balanceen la telemetría sin duplicar eventos.
func (r *TopicResolver) SharedSubscriptions(group string) []string {
	if strings.TrimSpace(group) == "" || strings.EqualFold(group, "none") {
		return r.Subscriptions()
	}
	subs := r.Subscriptions()
	shared := make([]string, len(subs))
	prefix := "$share/" + strings.Trim(group, "/") + "/"
	for i, sub := range subs {
		shared[i] = prefix + sub
	}
	return shared
}

// Parse interpreta un tópico entrante; devuelve false si no es un tópico v1 válido.
func (r *TopicResolver) Parse(topic string) (ParsedTopic, bool) {
	if topic == "" || !strings.HasPrefix(topic, r.prefix+"/") {
		return ParsedTopic{}, false
	}
	segments := strings.Split(topic[len(r.prefix)+1:], "/")
	if len(segments) < 2 || !objectid.Valid(segments[0]) {
		return ParsedTopic{}, false
	}
	cropID := segments[0]
	switch {
	case len(segments) == 2 && segments[1] == "telemetry":
		return ParsedTopic{CropID: cropID, Kind: KindTelemetry}, true
	case len(segments) == 2 && segments[1] == "status":
		return ParsedTopic{CropID: cropID, Kind: KindStatus}, true
	case len(segments) == 2 && segments[1] == "capabilities":
		return ParsedTopic{CropID: cropID, Kind: KindCapabilities}, true
	case len(segments) == 3 && segments[1] == "commands" && segments[2] == "ack":
		return ParsedTopic{CropID: cropID, Kind: KindCommandAck}, true
	default:
		return ParsedTopic{}, false
	}
}
