package app.smartpot.api.crops.service;

import app.smartpot.api.actuators.model.entity.Actuator;
import app.smartpot.api.actuators.model.entity.ActuatorType;
import app.smartpot.api.actuators.repository.ActuatorRepository;
import app.smartpot.api.commands.repository.CommandRepository;
import app.smartpot.api.crops.model.dto.CropRequest;
import app.smartpot.api.crops.model.dto.DeviceCredentialsResponse;
import app.smartpot.api.crops.model.entity.Crop;
import app.smartpot.api.crops.model.entity.CropHealth;
import app.smartpot.api.crops.model.entity.Device;
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
import java.util.Optional;

@Slf4j
@Service
public class CropService {

    public static final int MAX_CROPS_PER_USER = 20;
    private static final String NOT_FOUND = "El cultivo no existe";
    private static final List<ActuatorType> DEFAULT_ACTUATORS =
            List.of(ActuatorType.WATER_PUMP, ActuatorType.UV_LIGHT, ActuatorType.FAN);

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
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public CropService(CropRepository cropRepository, ReadingRepository readingRepository,
                       ActuatorRepository actuatorRepository, CommandRepository commandRepository,
                       NotificationService notificationService, DeviceProvisioner deviceProvisioner,
                       EncryptionService encryptionService, MqttTopicResolver topics, MqttProperties mqttProperties,
                       MongoTemplate mongoTemplate, Clock clock) {
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
        this.clock = clock;
    }

    public record CreatedCrop(Crop crop, DeviceCredentialsResponse credentials) {
    }

    public CreatedCrop create(String ownerId, CropRequest request) {
        if (cropRepository.countByOwnerId(ownerId) >= MAX_CROPS_PER_USER) {
            throw ApiException.badRequest("Alcanzaste el máximo de " + MAX_CROPS_PER_USER + " cultivos por cuenta");
        }
        Instant now = clock.instant();
        String key = newDeviceKey();
        Crop crop = cropRepository.save(Crop.builder()
                .ownerId(ownerId)
                .name(request.name().trim())
                .type(request.type())
                .automationEnabled(false)
                .device(Device.builder().keyCiphertext(encryptionService.encrypt(key)).keyRotatedAt(now).build())
                .createdAt(now)
                .updatedAt(now)
                .build());
        DEFAULT_ACTUATORS.forEach(type -> actuatorRepository.save(Actuator.builder()
                .cropId(crop.getId()).type(type).active(false).createdAt(now).build()));
        deviceProvisioner.provision(crop.getId(), key, false);
        log.info("Cultivo {} creado", crop.getId());
        return new CreatedCrop(crop, credentials(crop, key));
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
        crop.setName(request.name().trim());
        crop.setType(request.type());
        crop.setHealth(null);
        crop.setUpdatedAt(clock.instant());
        return cropRepository.save(crop);
    }

    public Crop setAutomation(String ownerId, String cropId, boolean enabled) {
        Crop crop = getOwned(ownerId, cropId);
        crop.setAutomationEnabled(enabled);
        crop.setUpdatedAt(clock.instant());
        return cropRepository.save(crop);
    }

    public DeviceCredentialsResponse deviceInfo(String ownerId, String cropId) {
        Crop crop = getOwned(ownerId, cropId);
        return credentials(crop, null);
    }

    public DeviceCredentialsResponse rotateDeviceKey(String ownerId, String cropId) {
        Crop crop = getOwned(ownerId, cropId);
        String key = newDeviceKey();
        Device device = crop.getDevice() == null ? new Device() : crop.getDevice();
        device.setKeyCiphertext(encryptionService.encrypt(key));
        device.setKeyRotatedAt(clock.instant());
        crop.setDevice(device);
        crop.setUpdatedAt(clock.instant());
        cropRepository.save(crop);
        deviceProvisioner.provision(crop.getId(), key, true);
        return credentials(crop, key);
    }

    public void delete(String ownerId, String cropId) {
        deleteCascade(getOwned(ownerId, cropId));
    }

    public void deleteAllOwnedBy(String ownerId) {
        cropRepository.findByOwnerIdOrderByCreatedAtAsc(ownerId).forEach(this::deleteCascade);
    }

    /** Actualiza solo el estado del dispositivo para no pisar cambios hechos en paralelo desde la web. */
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
                        NotificationType.DEVICE, "Maceta desconectada",
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
}
