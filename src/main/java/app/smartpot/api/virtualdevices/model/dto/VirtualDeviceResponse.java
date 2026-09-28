package app.smartpot.api.virtualdevices.model.dto;

import app.smartpot.api.readings.model.entity.Measures;
import app.smartpot.api.virtualdevices.model.entity.VirtualDevice;
import app.smartpot.api.virtualdevices.model.entity.VirtualLocation;
import app.smartpot.api.virtualdevices.model.entity.VirtualMode;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Simulación de un cultivo virtual. available indica si el servidor tiene simulador; active, si la simulación está
 * encendida (false en pausa); running, si el simulador la ejecuta ahora (tras un reinicio vuelve en menos de un
 * minuto).
 */
public record VirtualDeviceResponse(String cropId, boolean available, boolean active, boolean running,
                                    VirtualMode mode, Measures manual, VirtualLocation location,
                                    Integer intervalSeconds, boolean connected, Map<String, Double> lastReading,
                                    Instant lastPublishedAt, SimulatorPot.Weather weather, String weatherError,
                                    List<SimulatorPot.ActiveActuator> activeActuators,
                                    SimulatorPot.LastCommand lastCommand, Instant updatedAt) {

    public static VirtualDeviceResponse inactive(String cropId, boolean available) {
        return new VirtualDeviceResponse(cropId, available, false, false, null, null, null, null, false, null, null,
                null, null, List.of(), null, null);
    }

    /** location: la del cultivo, que la simulación comparte. */
    public static VirtualDeviceResponse of(VirtualDevice config, VirtualLocation location, SimulatorPot live,
                                           boolean available) {
        boolean running = live != null;
        return new VirtualDeviceResponse(config.getCropId(), available, config.isActive(), running, config.getMode(),
                config.getManual(), location, config.getIntervalSeconds(),
                running && live.connected(), running ? live.lastReading() : null,
                running ? live.lastPublishedAt() : null, running ? live.weather() : null,
                running ? live.weatherError() : null,
                running && live.activeActuators() != null ? live.activeActuators() : List.of(),
                running ? live.lastCommand() : null, config.getUpdatedAt());
    }
}
