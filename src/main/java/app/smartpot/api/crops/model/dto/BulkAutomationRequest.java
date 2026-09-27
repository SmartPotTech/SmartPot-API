package app.smartpot.api.crops.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Activa o desactiva el modo automático en varios cultivos. Sin cropIds se aplica a todos.
 */
public record BulkAutomationRequest(
        @Size(max = 20, message = "Selecciona como máximo 20 cultivos")
        List<String> cropIds,

        @NotNull(message = "Indica si la automatización queda activa")
        Boolean enabled
) {
}
