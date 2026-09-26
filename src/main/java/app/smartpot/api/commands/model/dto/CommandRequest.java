package app.smartpot.api.commands.model.dto;

import app.smartpot.api.commands.model.entity.CommandAction;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CommandRequest(
        @NotBlank(message = "El actuador es obligatorio")
        String actuatorId,

        @NotNull(message = "La acción es obligatoria")
        CommandAction action,

        @Min(value = 1, message = "La duración mínima es 1 segundo")
        @Max(value = 3600, message = "La duración máxima es 1 hora")
        Integer durationSeconds
) {
}
