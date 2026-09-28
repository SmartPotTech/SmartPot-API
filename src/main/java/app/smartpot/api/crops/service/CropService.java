package app.smartpot.api.crops.service;

import app.smartpot.api.actuators.model.entity.Actuator;
import app.smartpot.api.actuators.model.entity.ActuatorType;
import app.smartpot.api.actuators.repository.ActuatorRepository;
import app.smartpot.api.commands.repository.CommandRepository;
import app.smartpot.api.crops.model.dto.CropRequest;
import app.smartpot.api.crops.model.dto.DeviceCredentialsResponse;
import app.smartpot.api.crops.model.entity.*;
import app.smartpot.api.crops.model.event.CropDeletedEvent;
import app.smartpot.api.crops.model.event.CropPlacementChangedEvent;
import app.smartpot.api.crops.model.event.DeviceKeyRotatedEvent;
import app.smartpot.api.crops.repository.CropRepository;
import app.smartpot.api.exception.ApiException;
import app.smartpot.api.exception.ObjectIds;
import app.smartpot.api.mqtt.config.MqttProperties;
import app.smartpot.api.mqtt.service.DeviceProvisioner;
import app.smartpot.api.mqtt.service.MqttTopicResolver;
import app.smartpot.api.notifications.model.entity.NotificationType;
import app.smartpot.api.notifications.service.NotificationService;
import app.smartpot.api.readings.repository.ReadingRepository;
import app.smartpot.api.security.service.EncryptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
public class CropService {

    public static final int MAX_CROPS_PER_USER = 20;
    private static final String NOT_FOUND = "El cultivo no existe";
    private static final List<ActuatorType> DEFAULT_ACTUATORS =
            List.of(ActuatorType.WATER_PUMP, ActuatorType.UV_LIGHT, ActuatorType.FAN);
    private static final String VIRTUAL_HAS_NO_DEVICE =
            "Un cultivo virtual no usa credenciales: SmartPot lo simula por ti";

    private final CropRepository cropRepository;
    private final ReadingRepository readingRepository;
    private final ActuatorRepository actuatorRepository;
    private final CommandRepository commandRepository;
    private final NotificationService notificationService;
    private final DeviceProvisioner deviceProvisioner;
    private final EncryptionService encryptionService;
    private final MqttTopicResolver topics;
    private final MqttProperties mqttProperties;
    private final MongoTemplate mongoTemplate;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public CropService(CropRepository cropRepository, ReadingRepository readingRepository,
                       ActuatorRepository actuatorRepository, CommandRepository commandRepository,
                       NotificationService notificationService, DeviceProvisioner deviceProvisioner,
                       EncryptionService encryptionService, MqttTopicResolver topics, MqttProperties mqttProperties,
                       MongoTemplate mongoTemplate, ApplicationEventPublisher publisher, Clock clock) {
        this.cropRepository = cropRepository;
        this.readingRepository = readingRepository;
        this.actuatorRepository = actuatorRepository;
        this.commandRepository = commandRepository;
        this.notificationService = notificationService;
        this.deviceProvisioner = deviceProvisioner;
        this.encryptionService = encryptionService;
        this.topics = topics;
        this.mqttProperties = mqttProperties;
        this.mongoTemplate = mongoTemplate;
        this.publisher = publisher;
        this.clock = clock;
    }

    private static CropKind kindOf(Crop crop) {
        return crop.getKind() == null ? CropKind.REAL : crop.getKind();
    }

    private static Crop requireReal(Crop crop) {
        if (crop.isVirtual()) {
            throw ApiException.badRequest(VIRTUAL_HAS_NO_DEVICE);
        }
        return crop;
    }

    public CreatedCrop create(String ownerId, CropRequest request) {
        if (cropRepository.countByOwnerId(ownerId) >= MAX_CROPS_PER_USER) {
            throw ApiException.badRequest("Alcanzaste el máximo de " + MAX_CROPS_PER_USER + " cultivos por cuenta");
        }
        Instant now = clock.instant();
        String key = newDeviceKey();
        CropKind kind = request.kind() != null ? request.kind() : CropKind.REAL;
        Crop crop = cropRepository.save(Crop.builder()
                .ownerId(ownerId)
                .name(request.name().trim())
                .type(request.type())
                .kind(kind)
                .form(request.form() != null ? request.form() : CropForm.POT)
                .placement(request.placement() == null ? null : request.placement().toPlacement())
                .automationEnabled(false)
                .device(Device.builder().keyCiphertext(encryptionService.encrypt(key)).keyRotatedAt(now).build())
                .createdAt(now)
                .updatedAt(now)
                .build());
        // El simulador maneja los seis actuadores; el firmware, tres (se pueden agregar más).
        List<ActuatorType> actuators = crop.isVirtual() ? List.of(ActuatorType.values()) : DEFAULT_ACTUATORS;
        actuators.forEach(type -> actuatorRepository.save(Actuator.builder()
                .cropId(crop.getId()).type(type).active(false).createdAt(now).build()));
        // Un cultivo virtual también tiene cuenta en el broker: el simulador publica con ella.
        deviceProvisioner.provision(crop.getId(), key, false);
        log.info("Cultivo {} {} creado", kind == CropKind.VIRTUAL ? "virtual" : "real", crop.getId());
        return new CreatedCrop(crop, crop.isVirtual() ? null : credentials(crop, key));
    }

    public List<Crop> list(String ownerId) {
        return cropRepository.findByOwnerIdOrderByCreatedAtAsc(ownerId);
    }

    public Crop getOwned(String ownerId, String cropId) {
        ObjectIds.require(cropId, NOT_FOUND);
        return cropRepository.findByIdAndOwnerId(cropId, ownerId).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    }

    public Optional<Crop> find(String cropId) {
        return ObjectIds.isValid(cropId) ? cropRepository.findById(cropId) : Optional.empty();
    }

    public Crop update(String ownerId, String cropId, CropRequest request) {
        Crop crop = getOwned(ownerId, cropId);
        if (request.kind() != null && request.kind() != kindOf(crop)) {
            throw ApiException.badRequest("Un cultivo no puede pasar de real a virtual ni al revés: crea uno nuevo");
        }
        crop.setName(request.name().trim());
        crop.setType(request.type());
        if (request.form() != null) {
            crop.setForm(request.form());
        }
        Placement placement = request.placement() == null ? crop.getPlacement() : request.placement().toPlacement();
        boolean moved = !Objects.equals(placement, crop.getPlacement());
        crop.setPlacement(placement);
        crop.setHealth(null);
        crop.setUpdatedAt(clock.instant());
        Crop saved = cropRepository.save(crop);
        if (moved) {
            publisher.publishEvent(new CropPlacementChangedEvent(saved.getId()));
        }
        return saved;
    }

    /**
     * Cambia solo el lugar (la simulación de un cultivo virtual lo elige al seguir un clima). Sin lugar previo, el
     * cultivo queda al aire libre a pleno sol, que es como se simulaba antes de existir el lugar.
     */
    public Crop setLocation(Crop crop, Placement.Location location) {
        Placement current = crop.getPlacement();
        Placement placement = current == null
                ? new Placement(Placement.Setting.OUTDOOR, Placement.Exposure.FULL_SUN, location)
                : current.withLocation(location);
        if (placement.equals(current)) {
            return crop;
        }
        crop.setPlacement(placement);
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(crop.getId())),
                new Update().set("placement", placement).set("updatedAt", clock.instant()), Crop.class);
        return crop;
    }

    public Crop setAutomation(String ownerId, String cropId, boolean enabled) {
        Crop crop = getOwned(ownerId, cropId);
        crop.setAutomationEnabled(enabled);
        crop.setUpdatedAt(clock.instant());
        return cropRepository.save(crop);
    }

    /**
     * Modo automático para varios cultivos a la vez; sin ids, para todos los de la cuenta.
     */
    public List<Crop> setAutomation(String ownerId, List<String> cropIds, boolean enabled) {
        List<Crop> crops = cropIds == null || cropIds.isEmpty()
                ? list(ownerId)
                : cropIds.stream().distinct().map(id -> getOwned(ownerId, id)).toList();
        Instant now = clock.instant();
        crops.forEach(crop -> {
            crop.setAutomationEnabled(enabled);
            crop.setUpdatedAt(now);
        });
        return cropRepository.saveAll(crops);
    }

    public DeviceCredentialsResponse deviceInfo(String ownerId, String cropId) {
        Crop crop = requireReal(getOwned(ownerId, cropId));
        return credentials(crop, null);
    }

    public DeviceCredentialsResponse rotateDeviceKey(String ownerId, String cropId) {
        Crop crop = requireReal(getOwned(ownerId, cropId));
        String key = newDeviceKey();
        Device device = crop.getDevice() == null ? new Device() : crop.getDevice();
        device.setKeyCiphertext(encryptionService.encrypt(key));
        device.setKeyRotatedAt(clock.instant());
        crop.setDevice(device);
        crop.setUpdatedAt(clock.instant());
        cropRepository.save(crop);
        deviceProvisioner.provision(crop.getId(), key, true);
        publisher.publishEvent(new DeviceKeyRotatedEvent(crop.getId()));
        return credentials(crop, key);
    }

    /**
     * Clave del dispositivo en claro, solo para servicios internos que actúan como él (simulador).
     */
    public Optional<String> deviceKey(Crop crop) {
        Device device = crop.getDevice();
        if (device == null || device.getKeyCiphertext() == null) {
            return Optional.empty();
        }
        return Optional.of(encryptionService.decrypt(device.getKeyCiphertext()));
    }

    public void delete(String ownerId, String cropId) {
        deleteCascade(getOwned(ownerId, cropId));
    }

    public void deleteAllOwnedBy(String ownerId) {
        cropRepository.findByOwnerIdOrderByCreatedAtAsc(ownerId).forEach(this::deleteCascade);
    }

    /**
     * Actualiza solo el estado del dispositivo para no pisar cambios hechos en paralelo desde la web.
     */
    public void markDeviceSeen(String cropId, Boolean online) {
        Update update = new Update().set("device.lastSeenAt", clock.instant());
        if (online != null) {
            update.set("device.online", online);
        }
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(cropId)), update, Crop.class);
    }

    public void updateDeviceStatus(String cropId, boolean online) {
        find(cropId).ifPresent(crop -> {
            markDeviceSeen(cropId, online);
            if (!online) {
                notificationService.notifyOnce("offline:" + cropId, Duration.ofHours(1), crop.getOwnerId(), cropId,
                        NotificationType.DEVICE, "Cultivo desconectado",
                        "«" + crop.getName() + "» perdió la conexión con SmartPot.");
            }
        });
    }

    public void updateHealth(String cropId, CropHealth health) {
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(cropId)),
                new Update().set("health", health), Crop.class);
    }

    private void deleteCascade(Crop crop) {
        String cropId = crop.getId();
        readingRepository.deleteByCropId(cropId);
        commandRepository.deleteByCropId(cropId);
        actuatorRepository.deleteByCropId(cropId);
        notificationService.deleteAllForCrop(cropId);
        cropRepository.delete(crop);
        deviceProvisioner.deprovision(cropId);
        publisher.publishEvent(new CropDeletedEvent(cropId, crop.getOwnerId()));
        log.info("Cultivo {} eliminado con sus lecturas, comandos y actuadores", cropId);
    }

    private DeviceCredentialsResponse credentials(Crop crop, String key) {
        String cropId = crop.getId();
        return new DeviceCredentialsResponse(
                mqttProperties.publicHost(),
                mqttProperties.publicPort(),
                mqttProperties.publicTls(),
                mqttProperties.websocketUrl(),
                cropId,
                key,
                new DeviceCredentialsResponse.Topics(topics.telemetry(cropId), topics.commands(cropId),
                        topics.commandAck(cropId), topics.status(cropId)),
                crop.getDevice() == null ? null : crop.getDevice().getKeyRotatedAt());
    }

    private String newDeviceKey() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record CreatedCrop(Crop crop, DeviceCredentialsResponse credentials) {
    }
}
