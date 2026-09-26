package app.smartpot.api.actuators.model.dto;

import java.time.Instant;

public record ActuatorResponse(String id, String cropId, String type, boolean active, Instant lastChangedAt) {
}
