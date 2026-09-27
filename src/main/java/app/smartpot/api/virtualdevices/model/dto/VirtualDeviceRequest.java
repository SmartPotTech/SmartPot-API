package app.smartpot.api.virtualdevices.model.dto;

import app.smartpot.api.virtualdevices.model.entity.VirtualMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cómo se simula un cultivo virtual; manual y location son opcionales según el modo. */
public record VirtualDeviceRequest(
        @NotNull(message = "Elige el modo de la simulación")
        VirtualMode mode,
        @Valid ManualValues manual,
        @Valid LocationRequest location,
        @Min(value = 10, message = "El intervalo mínimo es de 10 segundos")
        @Max(value = 300, message = "El intervalo máximo es de 300 segundos")
        Integer intervalSeconds
) {

    /** Medidores del modo manual, en la escala de los sensores del dispositivo. */
    public record ManualValues(
            @DecimalMin("-20") @DecimalMax("60") Double temperature,
            @DecimalMin("0") @DecimalMax("100") Double humidity,
            @DecimalMin("0") @DecimalMax("2000") Double brightness,
            @DecimalMin("0") @DecimalMax("14") Double ph,
            @DecimalMin("0") @DecimalMax("3000") Double tds,
            @DecimalMin("300") @DecimalMax("1100") Double atmosphere,
            @DecimalMin("0") @DecimalMax("100") Double soilMoisture
    ) {
    }

    public record LocationRequest(
            @NotBlank(message = "Indica el nombre del lugar") @Size(max = 120) String name,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude
    ) {
    }
}
