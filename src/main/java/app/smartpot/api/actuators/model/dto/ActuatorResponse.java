package app.smartpot.api.actuators.model.dto;

import java.time.Instant;

/** active: encendido sin límite; running: encendido ahora (también por una orden con duración, hasta runningUntil). */
public record ActuatorResponse(String id, String cropId, String type, boolean active, boolean running,
                               Instant runningUntil, Instant lastChangedAt) {
}
