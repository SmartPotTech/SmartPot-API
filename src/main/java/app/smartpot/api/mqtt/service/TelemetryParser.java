package app.smartpot.api.mqtt.service;

import app.smartpot.api.mqtt.model.CommandAckMessage;
import app.smartpot.api.readings.model.entity.Measures;
import app.smartpot.api.readings.validator.MeasureRanges;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class TelemetryParser {

    private static final int MAX_PAYLOAD = 4_096;

    private final JsonMapper jsonMapper;

    public TelemetryParser(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * Lee la telemetría de la maceta. Acepta números o textos numéricos e ignora campos desconocidos.
     */
    public Measures parseTelemetry(String payload) {
        JsonNode root = readObject(payload);
        Measures measures = Measures.builder()
                .temperature(number(root, "temperature"))
                .humidity(number(root, "humidity"))
                .brightness(number(root, "brightness"))
                .ph(number(root, "ph"))
                .tds(number(root, "tds"))
                .atmosphere(number(root, "atmosphere"))
                .soilMoisture(number(root, "soilMoisture"))
                .build();
        MeasureRanges.validate(measures);
        return measures;
    }

    public CommandAckMessage parseAck(String payload) {
        JsonNode root = readObject(payload);
        String status = root.path("status").asString("").toUpperCase();
        if (!status.equals("EXECUTED") && !status.equals("FAILED")) {
            throw new IllegalArgumentException("Estado de ACK desconocido: " + status);
        }
        return new CommandAckMessage(root.path("id").asString(""), status, root.path("message").asString(null));
    }

    private JsonNode readObject(String payload) {
        if (payload == null || payload.isBlank() || payload.length() > MAX_PAYLOAD) {
            throw new IllegalArgumentException("Mensaje vacío o demasiado grande");
        }
        try {
            JsonNode root = jsonMapper.readTree(payload);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("El mensaje debe ser un objeto JSON");
            }
            return root;
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("El mensaje no es un JSON válido", ex);
        }
    }

    private static Double number(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.doubleValue();
        }
        if (node.isString()) {
            try {
                return Double.parseDouble(node.asString().trim());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("El campo " + field + " no es numérico");
            }
        }
        throw new IllegalArgumentException("El campo " + field + " no es numérico");
    }
}
