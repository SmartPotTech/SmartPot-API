package app.smartpot.api.crops.model.dto;

import jakarta.validation.constraints.NotNull;

public record AutomationRequest(
        @NotNull(message = "Indica si la automatización queda activa")
        Boolean enabled
) {
}
