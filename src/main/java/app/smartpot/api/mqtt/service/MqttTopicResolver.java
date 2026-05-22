package app.smartpot.api.mqtt.service;

import app.smartpot.api.mqtt.config.MqttProperties;
import org.springframework.stereotype.Component;

@Component
public class MqttTopicResolver {
    private final MqttProperties mqttProperties;

    public MqttTopicResolver(MqttProperties mqttProperties) {
        this.mqttProperties = mqttProperties;
    }

    private String basePath() {
        return mqttProperties.getTopicPrefix() + "/" + mqttProperties.getTopicVersion();
    }

    // ---- Pattern builders for subscription ----

    public String sensorTopicPattern() {
        return basePath() + "/+/" + MqttTopicConstants.SEGMENT_SENSORS + "/" + MqttTopicConstants.WILDCARD_MULTI;
    }

    public String commandAckTopicPattern() {
        return basePath() + "/+/" + MqttTopicConstants.SEGMENT_ACTUATORS + "/+/"
                + MqttTopicConstants.SEGMENT_COMMANDS + "/+/" + MqttTopicConstants.SEGMENT_ACK;
    }

    // ---- Concrete topic builders ----

    public String commandTopic(String cropId, String actuatorId) {
        return basePath() + "/" + cropId + "/" + MqttTopicConstants.SEGMENT_ACTUATORS + "/"
                + actuatorId + "/" + MqttTopicConstants.SEGMENT_COMMANDS;
    }

    public String simpleCommandTopic(String cropId) {
        return basePath() + "/" + cropId + "/" + MqttTopicConstants.SEGMENT_COMMANDS;
    }
    // ---- Parsing from incoming topics ----

    public String cropIdFromReadingTopic(String topic) {
        String[] segments = splitTopic(topic);
        if (segments.length >= 4 && basePrefixMatches(segments)
                && MqttTopicConstants.SEGMENT_SENSORS.equals(segments[3])) {
            return segments[2];
        }
        throw new IllegalArgumentException("Invalid readings topic: " + topic);
    }

    public String sensorTypeFromReadingTopic(String topic) {
        String[] segments = splitTopic(topic);
        if (segments.length >= 4 && basePrefixMatches(segments)
                && MqttTopicConstants.SEGMENT_SENSORS.equals(segments[3])) {
            return segments.length >= 5 ? segments[4] : null;
        }
        throw new IllegalArgumentException("Invalid readings topic: " + topic);
    }

    public boolean isSensorTopic(String topic) {
        String[] segments = splitTopic(topic);
        return segments.length >= 4 && basePrefixMatches(segments)
                && MqttTopicConstants.SEGMENT_SENSORS.equals(segments[3]);
    }

    public boolean isAckTopic(String topic) {
        String[] segments = splitTopic(topic);
        return segments.length >= 8 && basePrefixMatches(segments)
                && MqttTopicConstants.SEGMENT_ACTUATORS.equals(segments[3])
                && MqttTopicConstants.SEGMENT_COMMANDS.equals(segments[5])
                && MqttTopicConstants.SEGMENT_ACK.equals(segments[7]);
    }

    public AckTopic ackTopicFrom(String topic) {
        String[] segments = splitTopic(topic);
        if (segments.length >= 8 && basePrefixMatches(segments)
                && MqttTopicConstants.SEGMENT_ACTUATORS.equals(segments[3])
                && MqttTopicConstants.SEGMENT_COMMANDS.equals(segments[5])
                && MqttTopicConstants.SEGMENT_ACK.equals(segments[7])) {
            return new AckTopic(segments[2], segments[4], segments[6]);
        }
        throw new IllegalArgumentException("Invalid ACK topic: " + topic);
    }

    private boolean basePrefixMatches(String[] segments) {
        return segments.length >= 2
                && mqttProperties.getTopicPrefix().equals(segments[0])
                && mqttProperties.getTopicVersion().equals(segments[1]);
    }

    private String[] splitTopic(String topic) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("MQTT topic cannot be empty");
        }
        return topic.split("/");
    }

    public record AckTopic(String cropId, String actuatorId, String commandId) {
    }
}
