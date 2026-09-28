package app.smartpot.api.mqtt.service;

import app.smartpot.api.exception.ObjectIds;
import app.smartpot.api.mqtt.config.MqttProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Contrato de tópicos v1: {prefijo}/{cropId}/telemetry | commands | commands/ack | status.
 */
@Component
public class MqttTopicResolver {

    public static final String CONTROL_TOPIC = "$CONTROL/dynamic-security/v1";
    public static final String CONTROL_RESPONSE_TOPIC = CONTROL_TOPIC + "/response";

    private final String prefix;

    public MqttTopicResolver(MqttProperties properties) {
        this.prefix = properties.topicPrefix();
    }

    public String telemetry(String cropId) {
        return prefix + "/" + cropId + "/telemetry";
    }

    public String commands(String cropId) {
        return prefix + "/" + cropId + "/commands";
    }

    public String commandAck(String cropId) {
        return prefix + "/" + cropId + "/commands/ack";
    }

    public String status(String cropId) {
        return prefix + "/" + cropId + "/status";
    }

    public List<String> subscriptions() {
        return List.of(telemetry("+"), commandAck("+"), status("+"));
    }

    /**
     * Patrones para las ACL del rol de dispositivo: %u es el usuario MQTT, que es el id del cultivo.
     */
    public List<String> devicePublishPatterns() {
        return List.of(telemetry("%u"), commandAck("%u"), status("%u"));
    }

    public String deviceSubscribePattern() {
        return commands("%u");
    }

    public String serviceScope() {
        return prefix + "/#";
    }

    public Optional<ParsedTopic> parse(String topic) {
        if (topic == null || !topic.startsWith(prefix + "/")) {
            return Optional.empty();
        }
        String[] segments = topic.substring(prefix.length() + 1).split("/");
        if (segments.length < 2 || !ObjectIds.isValid(segments[0])) {
            return Optional.empty();
        }
        String cropId = segments[0];
        if (segments.length == 2 && "telemetry".equals(segments[1])) {
            return Optional.of(new ParsedTopic(cropId, Kind.TELEMETRY));
        }
        if (segments.length == 2 && "status".equals(segments[1])) {
            return Optional.of(new ParsedTopic(cropId, Kind.STATUS));
        }
        if (segments.length == 3 && "commands".equals(segments[1]) && "ack".equals(segments[2])) {
            return Optional.of(new ParsedTopic(cropId, Kind.COMMAND_ACK));
        }
        return Optional.empty();
    }

    public enum Kind {TELEMETRY, COMMAND_ACK, STATUS}

    public record ParsedTopic(String cropId, Kind kind) {
    }
}
