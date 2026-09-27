package app.smartpot.api.virtualdevices.service;

import app.smartpot.api.crops.model.entity.Crop;
import app.smartpot.api.crops.model.event.CropDeletedEvent;
import app.smartpot.api.crops.model.event.DeviceKeyRotatedEvent;
import app.smartpot.api.crops.service.CropService;
import app.smartpot.api.exception.ApiException;
import app.smartpot.api.readings.model.entity.Measures;
import app.smartpot.api.virtualdevices.model.dto.PlaceResponse;
import app.smartpot.api.virtualdevices.model.dto.SimulatorPot;
import app.smartpot.api.virtualdevices.model.dto.SimulatorPotRequest;
import app.smartpot.api.virtualdevices.model.dto.VirtualDeviceRequest;
import app.smartpot.api.virtualdevices.model.dto.VirtualDeviceResponse;
import app.smartpot.api.virtualdevices.model.entity.VirtualDevice;
import app.smartpot.api.virtualdevices.model.entity.VirtualLocation;
import app.smartpot.api.virtualdevices.model.entity.VirtualMode;
import app.smartpot.api.virtualdevices.repository.VirtualDeviceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Macetas virtuales: la configuración vive en Mongo y el simulador la ejecuta con la clave real de la maceta,
 * que la API descifra solo para entregársela por la red interna. La PWA nunca habla con el simulador.
 */
@Slf4j
@Service
public class VirtualDeviceService {

    public static final int MAX_PER_ACCOUNT = 5;
    static final int DEFAULT_INTERVAL = 30;

    private final VirtualDeviceRepository repository;
    private final CropService cropService;
    private final SimulatorClient simulator;
    private final Clock clock;

    public VirtualDeviceService(VirtualDeviceRepository repository, CropService cropService, SimulatorClient simulator,
                                Clock clock) {
        this.repository = repository;
        this.cropService = cropService;
        this.simulator = simulator;
        this.clock = clock;
    }

    public VirtualDeviceResponse get(String ownerId, String cropId) {
        Crop crop = cropService.getOwned(ownerId, cropId);
        return repository.findByCropId(crop.getId())
                .map(config -> VirtualDeviceResponse.of(config, simulator.get(crop.getId()).orElse(null),
                        simulator.isAvailable()))
                .orElseGet(() -> VirtualDeviceResponse.inactive(crop.getId(), simulator.isAvailable()));
    }

    public VirtualDeviceResponse configure(String ownerId, String cropId, VirtualDeviceRequest request) {
        Crop crop = cropService.getOwned(ownerId, cropId);
        if (!simulator.isAvailable()) {
            throw ApiException.unavailable("Las macetas virtuales no están habilitadas en este servidor");
        }
        if (request.mode() == VirtualMode.WEATHER && request.location() == null) {
            throw ApiException.badRequest("Elige una ubicación para que la maceta siga su clima");
        }
        Optional<VirtualDevice> existing = repository.findByCropId(crop.getId());
        if (existing.isEmpty() && repository.countByOwnerId(ownerId) >= MAX_PER_ACCOUNT) {
            throw ApiException.badRequest("Puedes tener hasta " + MAX_PER_ACCOUNT + " macetas virtuales a la vez");
        }
        Instant now = clock.instant();
        VirtualDevice config = existing.orElseGet(() -> VirtualDevice.builder()
                .cropId(crop.getId())
                .ownerId(ownerId)
                .createdAt(now)
                .build());
        config.setMode(request.mode());
        if (request.manual() != null) {
            config.setManual(merge(config.getManual(), request.manual()));
        }
        if (request.location() != null) {
            config.setLocation(new VirtualLocation(request.location().name().trim(), request.location().latitude(),
                    request.location().longitude()));
        }
        config.setIntervalSeconds(request.intervalSeconds() != null ? request.intervalSeconds()
                : config.getIntervalSeconds() > 0 ? config.getIntervalSeconds() : DEFAULT_INTERVAL);
        config.setUpdatedAt(now);
        SimulatorPot live = push(crop, config);
        VirtualDevice saved = repository.save(config);
        log.info("Maceta virtual del cultivo {} en modo {}", crop.getId(), config.getMode());
        return VirtualDeviceResponse.of(saved, live, true);
    }

    public void stop(String ownerId, String cropId) {
        Crop crop = cropService.getOwned(ownerId, cropId);
        repository.deleteByCropId(crop.getId());
        simulator.delete(crop.getId());
    }

    public List<PlaceResponse> places(String query) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.length() < 2 || trimmed.length() > 80) {
            throw ApiException.badRequest("Escribe entre 2 y 80 caracteres para buscar un lugar");
        }
        return simulator.places(trimmed);
    }

    /** Vuelve a crear en el simulador las macetas configuradas que falten y retira las que ya no existen. */
    @Scheduled(fixedDelayString = "${smartpot.simulator.reconcile-interval:PT1M}", initialDelay = 20_000)
    public void reconcile() {
        if (!simulator.isAvailable()) {
            return;
        }
        try {
            Set<String> running = simulator.list().stream()
                    .filter(SimulatorPot::managed)
                    .map(SimulatorPot::cropId)
                    .collect(Collectors.toSet());
            List<VirtualDevice> configs = repository.findAll();
            Set<String> configured = configs.stream().map(VirtualDevice::getCropId).collect(Collectors.toSet());
            for (VirtualDevice config : configs) {
                if (!running.contains(config.getCropId())) {
                    cropService.find(config.getCropId()).ifPresentOrElse(
                            crop -> pushQuietly(crop, config),
                            () -> repository.deleteByCropId(config.getCropId()));
                }
            }
            running.stream().filter(id -> !configured.contains(id)).forEach(simulator::delete);
        } catch (ApiException ex) {
            log.debug("Reconciliación de macetas virtuales omitida: {}", ex.getMessage());
        }
    }

    @Async
    @EventListener
    public void onCropDeleted(CropDeletedEvent event) {
        repository.deleteByCropId(event.cropId());
        simulator.delete(event.cropId());
    }

    @Async
    @EventListener
    public void onKeyRotated(DeviceKeyRotatedEvent event) {
        repository.findByCropId(event.cropId()).ifPresent(config ->
                cropService.find(event.cropId()).ifPresent(crop -> pushQuietly(crop, config)));
    }

    private void pushQuietly(Crop crop, VirtualDevice config) {
        try {
            push(crop, config);
        } catch (ApiException ex) {
            log.warn("No se pudo recrear la maceta virtual {}: {}", crop.getId(), ex.getMessage());
        }
    }

    private SimulatorPot push(Crop crop, VirtualDevice config) {
        String key = cropService.deviceKey(crop).orElseThrow(() -> ApiException.conflict(
                "La maceta no tiene clave: rótala en la pestaña Dispositivo y vuelve a intentarlo"));
        VirtualLocation location = config.getLocation();
        return simulator.put(crop.getId(), new SimulatorPotRequest(key, crop.getType().name(),
                config.getMode().name(), values(config.getManual()),
                location == null ? null : new SimulatorPot.Location(location.name(), location.latitude(),
                        location.longitude()),
                config.getIntervalSeconds()));
    }

    static Measures merge(Measures current, VirtualDeviceRequest.ManualValues update) {
        Measures base = current == null ? new Measures() : current;
        return Measures.builder()
                .temperature(first(update.temperature(), base.getTemperature()))
                .humidity(first(update.humidity(), base.getHumidity()))
                .brightness(first(update.brightness(), base.getBrightness()))
                .ph(first(update.ph(), base.getPh()))
                .tds(first(update.tds(), base.getTds()))
                .atmosphere(first(update.atmosphere(), base.getAtmosphere()))
                .soilMoisture(first(update.soilMoisture(), base.getSoilMoisture()))
                .build();
    }

    static Map<String, Double> values(Measures measures) {
        if (measures == null) {
            return null;
        }
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("temperature", measures.getTemperature());
        values.put("humidity", measures.getHumidity());
        values.put("brightness", measures.getBrightness());
        values.put("ph", measures.getPh());
        values.put("tds", measures.getTds());
        values.put("atmosphere", measures.getAtmosphere());
        values.put("soilMoisture", measures.getSoilMoisture());
        values.values().removeIf(Objects::isNull);
        return values.isEmpty() ? null : values;
    }

    private static Double first(Double preferred, Double fallback) {
        return preferred != null ? preferred : fallback;
    }
}
