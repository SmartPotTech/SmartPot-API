package app.smartpot.api.commands.model.dto;

import app.smartpot.api.actuators.model.entity.ActuatorType;
import app.smartpot.api.commands.model.entity.CommandAction;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Misma orden para varios cultivos. Sin cropIds se aplica a todos los cultivos de la cuenta.
 */
public record BulkCommandRequest(
        @Size(max = 20, message = "Selecciona como máximo 20 cultivos")
        List<String> cropIds,

        @NotNull(message = "El tipo de actuador es obligatorio")
        ActuatorType actuatorType,

        @NotNull(message = "La acción es obligatoria")
        CommandAction action,

        @Min(value = 1, message = "La duración mínima es 1 segundo")
        @Max(value = 3600, message = "La duración máxima es 1 hora")
        Integer durationSeconds
) {
}
