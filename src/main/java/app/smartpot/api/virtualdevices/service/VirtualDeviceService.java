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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Simulación de los cultivos virtuales: la configuración vive en Mongo y el simulador la ejecuta con la clave del
 * cultivo, que la API descifra solo para entregársela por la red interna. La PWA nunca habla con el simulador.
 * Un cultivo real nunca se simula: sus lecturas vienen de su propio dispositivo.
 */
@Slf4j
@Service
public class VirtualDeviceService {

    public static final int MAX_PER_ACCOUNT = 5;
    static final int DEFAULT_INTERVAL = 30;
    private static final VirtualDeviceRequest DEFAULT_SETUP = new VirtualDeviceRequest(VirtualMode.AUTO, null, null, null);

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

    /** Antes de crear un cultivo virtual: que haya simulador, cupo en la cuenta y una configuración válida. */
    public void checkCanCreate(String ownerId, VirtualDeviceRequest setup) {
        requireSimulator();
        if (repository.countByOwnerId(ownerId) >= MAX_PER_ACCOUNT) {
            throw ApiException.badRequest("Puedes tener hasta " + MAX_PER_ACCOUNT + " cultivos virtuales");
        }
        validate(setup != null ? setup : DEFAULT_SETUP);
    }

    /** Arranca la simulación de un cultivo virtual recién creado; si el simulador falla, la reconciliación reintenta. */
    public void startFor(Crop crop, VirtualDeviceRequest setup) {
        Instant now = clock.instant();
        VirtualDevice config = VirtualDevice.builder()
                .cropId(crop.getId())
                .ownerId(crop.getOwnerId())
                .createdAt(now)
                .build();
        apply(config, setup != null ? setup : DEFAULT_SETUP, now);
        repository.save(config);
        pushQuietly(crop, config);
        log.info("Simulación del cultivo virtual {} en modo {}", crop.getId(), config.getMode());
    }

    public VirtualDeviceResponse get(String ownerId, String cropId) {
        Crop crop = requireVirtual(cropService.getOwned(ownerId, cropId));
        return repository.findByCropId(crop.getId())
                .map(config -> VirtualDeviceResponse.of(config,
                        config.isActive() ? simulator.get(crop.getId()).orElse(null) : null, simulator.isAvailable()))
                .orElseGet(() -> VirtualDeviceResponse.inactive(crop.getId(), simulator.isAvailable()));
    }

    /** Cambia la simulación y la reanuda si estaba en pausa. */
    public VirtualDeviceResponse configure(String ownerId, String cropId, VirtualDeviceRequest request) {
        Crop crop = requireVirtual(cropService.getOwned(ownerId, cropId));
        requireSimulator();
        validate(request);
        Instant now = clock.instant();
        VirtualDevice config = repository.findByCropId(crop.getId()).orElseGet(() -> VirtualDevice.builder()
                .cropId(crop.getId())
                .ownerId(ownerId)
                .createdAt(now)
                .build());
        apply(config, request, now);
        SimulatorPot live = push(crop, config);
        VirtualDevice saved = repository.save(config);
        log.info("Simulación del cultivo virtual {} en modo {}", crop.getId(), config.getMode());
        return VirtualDeviceResponse.of(saved, live, true);
    }

    /** Pone la simulación en pausa: el cultivo deja de publicar, pero conserva su configuración. */
    public void pause(String ownerId, String cropId) {
        Crop crop = requireVirtual(cropService.getOwned(ownerId, cropId));
        repository.findByCropId(crop.getId()).ifPresent(config -> {
            config.setActive(false);
            config.setUpdatedAt(clock.instant());
            repository.save(config);
        });
        simulator.delete(crop.getId());
    }

    public List<PlaceResponse> places(String query) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.length() < 2 || trimmed.length() > 80) {
            throw ApiException.badRequest("Escribe entre 2 y 80 caracteres para buscar un lugar");
        }
        return simulator.places(trimmed);
    }

    /** Vuelve a crear en el simulador los cultivos activos que falten y retira los pausados o inexistentes. */
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
            List<VirtualDevice> active = repository.findAll().stream().filter(VirtualDevice::isActive).toList();
            Set<String> wanted = active.stream().map(VirtualDevice::getCropId).collect(Collectors.toSet());
            for (VirtualDevice config : active) {
                if (!running.contains(config.getCropId())) {
                    cropService.find(config.getCropId()).ifPresentOrElse(
                            crop -> pushQuietly(crop, config),
                            () -> repository.deleteByCropId(config.getCropId()));
                }
            }
            running.stream().filter(id -> !wanted.contains(id)).forEach(simulator::delete);
        } catch (ApiException ex) {
            log.debug("Reconciliación de cultivos virtuales omitida: {}", ex.getMessage());
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
        repository.findByCropId(event.cropId()).filter(VirtualDevice::isActive).ifPresent(config ->
                cropService.find(event.cropId()).ifPresent(crop -> pushQuietly(crop, config)));
    }

    private static Crop requireVirtual(Crop crop) {
        if (!crop.isVirtual()) {
            throw ApiException.badRequest("Este cultivo es real: sus lecturas llegan de su propio dispositivo");
        }
        return crop;
    }

    private void requireSimulator() {
        if (!simulator.isAvailable()) {
            throw ApiException.unavailable("Los cultivos virtuales no están habilitados en este servidor");
        }
    }

    private static void validate(VirtualDeviceRequest request) {
        if (request.mode() == VirtualMode.WEATHER && request.location() == null) {
            throw ApiException.badRequest("Elige una ubicación para que el cultivo siga su clima");
        }
    }

    private static void apply(VirtualDevice config, VirtualDeviceRequest request, Instant now) {
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
        config.setActive(true);
        config.setUpdatedAt(now);
    }

    private void pushQuietly(Crop crop, VirtualDevice config) {
        try {
            push(crop, config);
        } catch (ApiException ex) {
            log.warn("No se pudo iniciar la simulación del cultivo {}: {}", crop.getId(), ex.getMessage());
        }
    }

    private SimulatorPot push(Crop crop, VirtualDevice config) {
        String key = cropService.deviceKey(crop).orElseThrow(() -> ApiException.conflict(
                "El cultivo no tiene clave de dispositivo: créalo de nuevo"));
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
