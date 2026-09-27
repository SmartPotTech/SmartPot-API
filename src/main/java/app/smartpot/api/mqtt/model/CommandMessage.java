package app.smartpot.api.mqtt.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Orden que recibe el dispositivo en {prefijo}/{cropId}/commands.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommandMessage(String id, String actuator, String action, Integer durationSeconds) {
}
