package app.smartpot.api.virtualdevices.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Estado de un cultivo virtual tal como lo informa el simulador. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SimulatorPot(String cropId, String cropType, String mode, boolean managed, boolean connected,
                           double intervalSeconds, Map<String, Double> lastReading, Instant lastPublishedAt,
                           Map<String, Double> manual, Location location, Weather weather, String weatherError,
                           List<ActiveActuator> activeActuators, LastCommand lastCommand) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Location(String name, double latitude, double longitude) {
    }

    /** condition: CLEAR, MOSTLY_CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW o STORM. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Weather(double temperature, double humidity, double cloudCover, double radiation,
                          double precipitation, double pressure, double windSpeed,
                          @JsonProperty("isDay") boolean isDay, int code, String condition, String label,
                          String observedAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ActiveActuator(String actuator, Instant until) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LastCommand(String id, String status, String message, Instant at) {
    }
}
