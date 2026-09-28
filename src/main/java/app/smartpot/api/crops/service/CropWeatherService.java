package app.smartpot.api.crops.service;

import app.smartpot.api.cache.CacheStore;
import app.smartpot.api.crops.model.entity.Crop;
import app.smartpot.api.crops.model.entity.Placement;
import app.smartpot.api.virtualdevices.model.dto.SimulatorPot;
import app.smartpot.api.virtualdevices.service.SimulatorClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Clima actual del lugar de un cultivo, real o virtual. Lo consulta el simulador, que es el único componente con
 * salida al servicio de clima, y se guarda 10 minutos por lugar; si falla, no se vuelve a intentar en 2 minutos.
 * Sirve para ilustrar el cultivo y para que el asistente compare los sensores con el clima de afuera.
 */
@Slf4j
@Service
public class CropWeatherService {

    static final Duration TTL = Duration.ofMinutes(10);
    static final Duration RETRY_AFTER = Duration.ofMinutes(2);
    private static final String NONE = "none";

    private final SimulatorClient simulator;
    private final CacheStore cacheStore;
    private final JsonMapper jsonMapper;

    public CropWeatherService(SimulatorClient simulator, CacheStore cacheStore, JsonMapper jsonMapper) {
        this.simulator = simulator;
        this.cacheStore = cacheStore;
        this.jsonMapper = jsonMapper;
    }

    public Optional<SimulatorPot.Weather> current(Crop crop) {
        Placement placement = crop.getPlacement();
        if (placement == null || placement.location() == null || !simulator.isAvailable()) {
            return Optional.empty();
        }
        Placement.Location location = placement.location();
        String key = String.format(Locale.ROOT, "weather:%.2f:%.2f", location.latitude(), location.longitude());
        Optional<String> cached = cacheStore.get(key);
        if (cached.isPresent()) {
            return NONE.equals(cached.get()) ? Optional.empty() : read(cached.get());
        }
        Optional<SimulatorPot.Weather> weather = simulator.weather(location.latitude(), location.longitude());
        if (weather.isPresent()) {
            cacheStore.put(key, jsonMapper.writeValueAsString(weather.get()), TTL);
        } else {
            cacheStore.put(key, NONE, RETRY_AFTER);
        }
        return weather;
    }

    private Optional<SimulatorPot.Weather> read(String json) {
        try {
            return Optional.of(jsonMapper.readValue(json, SimulatorPot.Weather.class));
        } catch (JacksonException ex) {
            log.debug("Clima en caché ilegible: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
