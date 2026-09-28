package app.smartpot.api.crops.model.dto;

import app.smartpot.api.crops.model.entity.Placement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Lugar del cultivo; cualquiera de sus partes puede quedar sin definir. */
public record PlacementRequest(Placement.Setting setting, Placement.Exposure exposure, @Valid LocationRequest location) {

    public record LocationRequest(
            @NotBlank(message = "Indica el nombre del lugar") @Size(max = 120) String name,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude
    ) {
    }

    public Placement toPlacement() {
        return new Placement(setting, exposure, location == null ? null
                : new Placement.Location(location.name().trim(), location.latitude(), location.longitude()));
    }
}
