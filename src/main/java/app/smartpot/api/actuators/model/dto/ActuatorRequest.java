package app.smartpot.api.actuators.model.dto;

import app.smartpot.api.actuators.model.entity.ActuatorType;
import jakarta.validation.constraints.NotNull;

public record ActuatorRequest(
        @NotNull(message = "El tipo de actuador es obligatorio")
        ActuatorType type
) {
}
