package app.smartpot.api.crops.service;

import app.smartpot.api.cache.CacheStore;
import app.smartpot.api.crops.model.entity.Crop;
import app.smartpot.api.crops.model.entity.Placement;
import app.smartpot.api.virtualdevices.model.dto.SimulatorPot;
import app.smartpot.api.virtualdevices.service.SimulatorClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CropWeatherServiceTest {

    private static final SimulatorPot.Weather SUNNY = new SimulatorPot.Weather(27.5, 40, 5, 820, 0, 850, 6, true, 0,
            "CLEAR", "Despejado", "2026-09-28T12:00");

    private final SimulatorClient simulator = mock(SimulatorClient.class);
    private final CacheStore cacheStore = new CacheStore(null, Clock.systemUTC());
    private final CropWeatherService service = new CropWeatherService(simulator, cacheStore, JsonMapper.builder().build());

    @BeforeEach
    void setUp() {
        when(simulator.isAvailable()).thenReturn(true);
    }

    private static Crop at(Placement.Location location) {
        return Crop.builder().id("c1").placement(new Placement(Placement.Setting.OUTDOOR, null, location)).build();
    }

    @Test
    void cropsWithoutAPlaceHaveNoWeather() {
        assertThat(service.current(Crop.builder().id("c1").build())).isEmpty();
        assertThat(service.current(at(null))).isEmpty();
        verify(simulator, never()).weather(anyDouble(), anyDouble());
    }

    @Test
    void theWeatherOfAPlaceIsAskedOnceEveryTenMinutes() {
        when(simulator.weather(6.25, -75.56)).thenReturn(Optional.of(SUNNY));
        Crop crop = at(new Placement.Location("Medellín", 6.25, -75.56));

        assertThat(service.current(crop)).contains(SUNNY);
        assertThat(service.current(crop)).contains(SUNNY);

        verify(simulator, times(1)).weather(6.25, -75.56);
    }

    @Test
    void aFailureIsNotRetriedRightAway() {
        when(simulator.weather(4.71, -74.07)).thenReturn(Optional.empty());
        Crop crop = at(new Placement.Location("Bogotá", 4.71, -74.07));

        assertThat(service.current(crop)).isEmpty();
        assertThat(service.current(crop)).isEmpty();

        verify(simulator, times(1)).weather(4.71, -74.07);
    }
}
