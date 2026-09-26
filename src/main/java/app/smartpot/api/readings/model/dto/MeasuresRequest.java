package app.smartpot.api.readings.model.dto;

import app.smartpot.api.readings.model.entity.Measures;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Lectura manual. Rangos válidos: temperatura -20 a 60 °C, humedad y sustrato 0 a 100 %, "
        + "luz 0 a 200000 lux, pH 0 a 14, TDS 0 a 10000 ppm, presión 300 a 1100 hPa")
public record MeasuresRequest(
        Double temperature,
        Double humidity,
        Double brightness,
        Double ph,
        Double tds,
        Double atmosphere,
        Double soilMoisture
) {

    public Measures toMeasures() {
        return new Measures(temperature, humidity, brightness, ph, tds, atmosphere, soilMoisture);
    }
}
