package app.smartpot.api.crops.model.dto;

import app.smartpot.api.crops.model.entity.CropForm;
import app.smartpot.api.crops.model.entity.CropKind;
import app.smartpot.api.crops.model.entity.CropType;
import app.smartpot.api.virtualdevices.model.dto.VirtualDeviceRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CropRequest(
        @NotBlank(message = "El nombre del cultivo es obligatorio")
        @Size(min = 2, max = 60, message = "El nombre debe tener entre 2 y 60 caracteres")
        String name,

        @NotNull(message = "El tipo de cultivo es obligatorio")
        CropType type,
        /** Solo al crear: REAL si falta. Al editar, si llega debe coincidir con el del cultivo. */
        CropKind kind,
        /** Forma del sistema hidropónico: POT si falta al crear; al editar, si falta se conserva. */
        CropForm form,
        /** Solo para cultivos virtuales al crearlos: cómo arranca la simulación (AUTO si falta). */
        @Valid VirtualDeviceRequest virtual
) {

    public CropRequest(String name, CropType type) {
        this(name, type, null, null, null);
    }
}
