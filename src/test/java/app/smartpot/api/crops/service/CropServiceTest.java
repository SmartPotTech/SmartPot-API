package app.smartpot.api.crops.service;

import app.smartpot.api.actuators.model.entity.Actuator;
import app.smartpot.api.actuators.repository.ActuatorRepository;
import app.smartpot.api.commands.repository.CommandRepository;
import app.smartpot.api.crops.model.dto.CropRequest;
import app.smartpot.api.crops.model.entity.Crop;
import app.smartpot.api.crops.model.entity.CropType;
import app.smartpot.api.crops.model.event.CropDeletedEvent;
import app.smartpot.api.crops.model.event.DeviceKeyRotatedEvent;
import app.smartpot.api.crops.repository.CropRepository;
import app.smartpot.api.exception.ApiException;
import app.smartpot.api.mqtt.service.DeviceProvisioner;
import app.smartpot.api.mqtt.service.MqttTopicResolver;
import app.smartpot.api.notifications.service.NotificationService;
import app.smartpot.api.readings.repository.ReadingRepository;
import app.smartpot.api.security.service.EncryptionService;
import app.smartpot.api.support.TestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CropServiceTest {

    private static final String OWNER = "6718f0a1b2c3d4e5f6a7b000";
    private static final String CROP = "6718f0a1b2c3d4e5f6a7b8c9";

    private final CropRepository cropRepository = mock(CropRepository.class);
    private final ReadingRepository readingRepository = mock(ReadingRepository.class);
    private final ActuatorRepository actuatorRepository = mock(ActuatorRepository.class);
    private final CommandRepository commandRepository = mock(CommandRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final DeviceProvisioner provisioner = mock(DeviceProvisioner.class);
    private final EncryptionService encryptionService = new EncryptionService(TestProperties.smartPot());
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private CropService service;

    @BeforeEach
    void setUp() {
        service = new CropService(cropRepository, readingRepository, actuatorRepository, commandRepository,
                notificationService, provisioner, encryptionService, new MqttTopicResolver(TestProperties.mqtt()),
                TestProperties.mqtt(), mock(MongoTemplate.class), publisher, Clock.systemUTC());
        when(cropRepository.save(any(Crop.class))).thenAnswer(invocation -> {
            Crop crop = invocation.getArgument(0);
            if (crop.getId() == null) {
                crop.setId(CROP);
            }
            return crop;
        });
    }

    @Test
    void createsTheCropWithDefaultActuatorsAndAnEncryptedDeviceKey() {
        CropService.CreatedCrop created = service.create(OWNER, new CropRequest("Lechugas", CropType.LETTUCE));

        String key = created.credentials().key();
        assertThat(key).hasSizeGreaterThanOrEqualTo(30);
        assertThat(created.credentials().username()).isEqualTo(CROP);
        assertThat(created.credentials().topics().telemetry()).isEqualTo("smartpot/v1/" + CROP + "/telemetry");
        assertThat(created.crop().getDevice().getKeyCiphertext()).isNotEqualTo(key);
        assertThat(encryptionService.decrypt(created.crop().getDevice().getKeyCiphertext())).isEqualTo(key);
        verify(actuatorRepository, times(3)).save(any(Actuator.class));
        verify(provisioner).provision(CROP, key, false);
    }

    @Test
    void limitsTheNumberOfCropsPerAccount() {
        when(cropRepository.countByOwnerId(OWNER)).thenReturn((long) CropService.MAX_CROPS_PER_USER);

        assertThatThrownBy(() -> service.create(OWNER, new CropRequest("Otra", CropType.BASIL)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("máximo");
    }

    @Test
    void hidesCropsFromOtherOwnersAsNotFound() {
        when(cropRepository.findByIdAndOwnerId(CROP, "otro")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOwned("otro", CROP))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.getOwned(OWNER, "no-es-un-id"))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void rotatingTheKeyReprovisionsAndKicksTheDevice() {
        Crop crop = Crop.builder().id(CROP).ownerId(OWNER).name("Tomates").type(CropType.TOMATO).build();
        when(cropRepository.findByIdAndOwnerId(CROP, OWNER)).thenReturn(Optional.of(crop));

        String key = service.rotateDeviceKey(OWNER, CROP).key();

        verify(provisioner).provision(CROP, key, true);
        assertThat(encryptionService.decrypt(crop.getDevice().getKeyCiphertext())).isEqualTo(key);
        assertThat(service.deviceKey(crop)).contains(key);
        verify(publisher).publishEvent(new DeviceKeyRotatedEvent(CROP));
    }

    @Test
    void deletingACropRemovesItsDataAndDeviceAccount() {
        Crop crop = Crop.builder().id(CROP).ownerId(OWNER).name("Tomates").type(CropType.TOMATO).build();
        when(cropRepository.findByIdAndOwnerId(CROP, OWNER)).thenReturn(Optional.of(crop));

        service.delete(OWNER, CROP);

        verify(readingRepository).deleteByCropId(CROP);
        verify(commandRepository).deleteByCropId(CROP);
        verify(actuatorRepository).deleteByCropId(CROP);
        verify(notificationService).deleteAllForCrop(CROP);
        verify(provisioner).deprovision(CROP);
        verify(publisher).publishEvent(new CropDeletedEvent(CROP, OWNER));
    }

    @Test
    void offlineStatusNotifiesTheOwner() {
        Crop crop = Crop.builder().id(CROP).ownerId(OWNER).name("Tomates").build();
        when(cropRepository.findById(CROP)).thenReturn(Optional.of(crop));

        service.updateDeviceStatus(CROP, false);

        verify(notificationService).notifyOnce(eq("offline:" + CROP), any(), eq(OWNER), eq(CROP), any(), anyString(),
                anyString());
    }
}
