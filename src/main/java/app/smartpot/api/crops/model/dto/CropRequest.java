package app.smartpot.api.crops.model.dto;

import app.smartpot.api.crops.model.entity.CropType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CropRequest(
        @NotBlank(message = "El nombre del cultivo es obligatorio")
        @Size(min = 2, max = 60, message = "El nombre debe tener entre 2 y 60 caracteres")
        String name,

        @NotNull(message = "El tipo de cultivo es obligatorio")
        CropType type
) {
}
