package app.smartpot.api.ai.model.dto;

import app.smartpot.api.readings.model.entity.Measures;
import app.smartpot.api.readings.model.entity.Reading;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Lectura del historial con su hora: la IA la usa para calcular tendencias y pronósticos.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HistoryPoint(
        Instant measuredAt,
        Double temperature,
        Double humidity,
        Double brightness,
        Double ph,
        Double tds,
        Double atmosphere,
        Double soilMoisture
) {

    public static HistoryPoint of(Reading reading) {
        Measures m = reading.getMeasures() != null ? reading.getMeasures() : new Measures();
        return new HistoryPoint(reading.getMeasuredAt(), m.getTemperature(), m.getHumidity(), m.getBrightness(),
                m.getPh(), m.getTds(), m.getAtmosphere(), m.getSoilMoisture());
    }
}
