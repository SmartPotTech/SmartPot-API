package app.smartpot.api.mqtt.service;

import app.smartpot.api.mqtt.model.SimpleAckMessage;
import app.smartpot.api.mqtt.model.SimpleReadingMessage;
import app.smartpot.api.records.model.dto.MeasuresDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class MqttSimplePayloadParser {
    private static final Set<String> VALID_ACTIONS = Set.of("activate", "disable");
    private static final Set<String> VALID_DEVICES = Set.of("water_pump", "lights", "humidifier");
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void validateCommandPayload(String payload) {
        String[] parts = splitPair(payload, "command");
        if (!VALID_ACTIONS.contains(parts[0]) || !VALID_DEVICES.contains(parts[1])) {
            throw new IllegalArgumentException("Invalid command payload: " + payload);
        }
    }

    public SimpleAckMessage parseAck(String payload) {
        String[] parts = splitPair(payload, "ack");
        if ("executed".equals(parts[0]) && "ok".equals(parts[1])) {
            return new SimpleAckMessage(true, "ok");
        }
        if ("failed".equals(parts[0]) && !parts[1].isBlank()) {
            return new SimpleAckMessage(false, parts[1]);
        }
        throw new IllegalArgumentException("Invalid ACK payload: " + payload);
    }

    /**
     * Parsea un payload consolidado del gateway ESP32.
     * Cada sensor puede estar presente o ser null.
     * Formato esperado:
     * {
     *   "bme280": {"temp": 24.5, "hum": 60, "press": 1013.2},
     *   "lux":    {"lux": 700},
     *   "ph":     {"ph": 6.8},
     *   "tds":    {"tds": 410}
     * }
     */
    public SimpleReadingMessage parseConsolidatedReading(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);

            MeasuresDTO measures = new MeasuresDTO();

            if (root.hasNonNull(MqttTopicConstants.SENSOR_BME280)) {
                JsonNode bme280 = root.get(MqttTopicConstants.SENSOR_BME280);
                if (bme280.has("temp")) measures.setTemperature(nodeToString(bme280.get("temp")));
                if (bme280.has("hum")) measures.setHumidity(nodeToString(bme280.get("hum")));
                if (bme280.has("press")) measures.setAtmosphere(nodeToString(bme280.get("press")));
            }

            if (root.hasNonNull(MqttTopicConstants.SENSOR_LUX)) {
                JsonNode lux = root.get(MqttTopicConstants.SENSOR_LUX);
                if (lux.has("lux")) measures.setBrightness(nodeToString(lux.get("lux")));
            }

            if (root.hasNonNull(MqttTopicConstants.SENSOR_PH)) {
                JsonNode ph = root.get(MqttTopicConstants.SENSOR_PH);
                if (ph.has("ph")) measures.setPh(nodeToString(ph.get("ph")));
            }

            if (root.hasNonNull(MqttTopicConstants.SENSOR_TDS)) {
                JsonNode tds = root.get(MqttTopicConstants.SENSOR_TDS);
                if (tds.has("tds")) measures.setTds(nodeToString(tds.get("tds")));
            }

            return new SimpleReadingMessage(measures);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid consolidated reading payload: " + payload, e);
        }
    }

    /**
     * Parsea un payload de sensor individual (también JSON).
     * {
     *   "temp": 24.5, "hum": 60, "press": 1013.2   // bme280
     *   "lux": 700                                  // lux
     *   "ph": 6.8                                   // ph
     *   "tds": 410                                  // tds
     * }
     */
    public SimpleReadingMessage parseReading(String payload, String sensorType) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            MeasuresDTO measures = new MeasuresDTO();

            switch (sensorType) {
                case MqttTopicConstants.SENSOR_BME280 -> {
                    if (root.has("temp")) measures.setTemperature(nodeToString(root.get("temp")));
                    if (root.has("hum")) measures.setHumidity(nodeToString(root.get("hum")));
                    if (root.has("press")) measures.setAtmosphere(nodeToString(root.get("press")));
                }
                case MqttTopicConstants.SENSOR_LUX -> {
                    if (root.has("lux")) measures.setBrightness(nodeToString(root.get("lux")));
                }
                case MqttTopicConstants.SENSOR_PH -> {
                    if (root.has("ph")) measures.setPh(nodeToString(root.get("ph")));
                }
                case MqttTopicConstants.SENSOR_TDS -> {
                    if (root.has("tds")) measures.setTds(nodeToString(root.get("tds")));
                }
                default -> throw new IllegalArgumentException("Unsupported sensor type: " + sensorType);
            }

            return new SimpleReadingMessage(measures);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid reading payload for sensor " + sensorType + ": " + payload, e);
        }
    }

    private String nodeToString(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isNumber()) return node.asText();
        return node.asText();
    }

    private String[] splitPair(String payload, String payloadType) {
        String[] parts = payload.split(":", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("Invalid " + payloadType + " payload: " + payload);
        }
        return new String[]{parts[0].trim(), parts[1].trim()};
    }
}
