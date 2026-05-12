package app.smartpot.api.mqtt.service;

import app.smartpot.api.actuators.model.entity.ActuatorType;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MqttCommandPayloadMapper {
    private static final Map<String, String> COMMAND_PAYLOADS = Map.of(
            "ACTIVATE_WATER_PUMP", "activate:water_pump",
            "DISABLE_WATER_PUMP", "disable:water_pump",
            "ACTIVATE_UV_LIGHT", "activate:lights",
            "DISABLE_UV_LIGHT", "disable:lights",
            "ACTIVATE_HUMIDIFIER", "activate:humidifier",
            "DISABLE_HUMIDIFIER", "disable:humidifier"
    );

    public String toPayload(String commandType, ActuatorType actuatorType) {
        String payload = COMMAND_PAYLOADS.get(commandType);
        if (payload == null) {
            throw new IllegalArgumentException("Unsupported MQTT command type: " + commandType);
        }

        validateActuatorMatchesPayload(payload, actuatorType);
        return payload;
    }

    private void validateActuatorMatchesPayload(String payload, ActuatorType actuatorType) {
        if (actuatorType == ActuatorType.WATER_PUMP && !payload.endsWith(":water_pump")) {
            throw new IllegalArgumentException("Command payload does not match actuator type WATER_PUMP");
        }
        if (actuatorType == ActuatorType.UV_LIGHT && !payload.endsWith(":lights")) {
            throw new IllegalArgumentException("Command payload does not match actuator type UV_LIGHT");
        }
        if (actuatorType == ActuatorType.HUMIDIFIER && !payload.endsWith(":humidifier")) {
            throw new IllegalArgumentException("Command payload does not match actuator type HUMIDIFIER");
        }
    }
}
