package app.smartpot.api.crops.model.dto;

import app.smartpot.api.crops.model.entity.Placement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/**
 * Lugar del cultivo; cualquiera de sus partes puede quedar sin definir.
 */
public record PlacementRequest(Placement.Setting setting, Placement.Exposure exposure,
                               @Valid LocationRequest location) {

    public Placement toPlacement() {
        return new Placement(setting, exposure, location == null ? null
                : new Placement.Location(location.name().trim(), location.latitude(), location.longitude()));
    }

    public record LocationRequest(
            @NotBlank(message = "Indica el nombre del lugar") @Size(max = 120) String name,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude
    ) {
    }
}
