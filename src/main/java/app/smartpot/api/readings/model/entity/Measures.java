package app.smartpot.api.readings.model.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Valores de una lectura. Cada sensor es opcional: un dispositivo puede no tenerlos todos.
 * Unidades: °C, % de humedad relativa, lux, pH, ppm, hPa y % de humedad del sustrato.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Measures {

    private Double temperature;

    private Double humidity;

    private Double brightness;

    private Double ph;

    private Double tds;

    private Double atmosphere;

    private Double soilMoisture;

    public boolean isEmpty() {
        return temperature == null && humidity == null && brightness == null && ph == null
                && tds == null && atmosphere == null && soilMoisture == null;
    }
}
