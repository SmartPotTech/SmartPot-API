package app.smartpot.api.virtualdevices.service;

import app.smartpot.api.crops.model.entity.Crop;
import app.smartpot.api.crops.model.entity.CropType;
import app.smartpot.api.crops.model.event.CropDeletedEvent;
import app.smartpot.api.crops.model.event.DeviceKeyRotatedEvent;
import app.smartpot.api.crops.service.CropService;
import app.smartpot.api.exception.ApiException;
import app.smartpot.api.readings.model.entity.Measures;
import app.smartpot.api.virtualdevices.model.dto.SimulatorPot;
import app.smartpot.api.virtualdevices.model.dto.SimulatorPotRequest;
import app.smartpot.api.virtualdevices.model.dto.VirtualDeviceRequest;
import app.smartpot.api.virtualdevices.model.dto.VirtualDeviceResponse;
import app.smartpot.api.virtualdevices.model.entity.VirtualDevice;
import app.smartpot.api.virtualdevices.model.entity.VirtualMode;
import app.smartpot.api.virtualdevices.repository.VirtualDeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VirtualDeviceServiceTest {

    private static final String OWNER = "6718f0a1b2c3d4e5f6a7b000";
    private static final String CROP = "6718f0a1b2c3d4e5f6a7b8c9";
    private static final String ORPHAN = "6718f0a1b2c3d4e5f6a7b8ca";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-27T12:00:00Z"), ZoneOffset.UTC);

    private final VirtualDeviceRepository repository = mock(VirtualDeviceRepository.class);
    private final CropService cropService = mock(CropService.class);
    private final SimulatorClient simulator = mock(SimulatorClient.class);
    private final Crop crop = Crop.builder().id(CROP).ownerId(OWNER).name("Lechugas").type(CropType.LETTUCE).build();
    private VirtualDeviceService service;

    @BeforeEach
    void setUp() {
        service = new VirtualDeviceService(repository, cropService, simulator, CLOCK);
        when(simulator.isAvailable()).thenReturn(true);
        when(cropService.getOwned(OWNER, CROP)).thenReturn(crop);
        when(cropService.find(CROP)).thenReturn(Optional.of(crop));
        when(cropService.deviceKey(crop)).thenReturn(Optional.of("clave-de-la-maceta-0001"));
        when(repository.findByCropId(CROP)).thenReturn(Optional.empty());
        when(repository.save(any(VirtualDevice.class))).thenAnswer(call -> call.getArgument(0));
        when(simulator.put(eq(CROP), any())).thenReturn(pot(CROP, true));
    }

    private static SimulatorPot pot(String cropId, boolean connected) {
        return new SimulatorPot(cropId, "LETTUCE", "WEATHER", true, connected, 30, Map.of("soilMoisture", 64.0),
                CLOCK.instant(), Map.of(), null, null, null, List.of(), null);
    }

    private static VirtualDeviceRequest weather() {
        return new VirtualDeviceRequest(VirtualMode.WEATHER, null,
                new VirtualDeviceRequest.LocationRequest("Medellín", 6.245, -75.5715), 20);
    }

    @Test
    void startsTheVirtualPotWithTheRealDeviceKey() {
        VirtualDeviceResponse response = service.configure(OWNER, CROP, weather());

        ArgumentCaptor<SimulatorPotRequest> sent = ArgumentCaptor.forClass(SimulatorPotRequest.class);
        verify(simulator).put(eq(CROP), sent.capture());
        assertThat(sent.getValue().key()).isEqualTo("clave-de-la-maceta-0001");
        assertThat(sent.getValue().cropType()).isEqualTo("LETTUCE");
        assertThat(sent.getValue().location().name()).isEqualTo("Medellín");
        assertThat(response.active()).isTrue();
        assertThat(response.running()).isTrue();
        assertThat(response.connected()).isTrue();
        assertThat(response.intervalSeconds()).isEqualTo(20);
    }

    @Test
    void weatherModeNeedsALocation() {
        assertThatThrownBy(() -> service.configure(OWNER, CROP,
                new VirtualDeviceRequest(VirtualMode.WEATHER, null, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("ubicación");
        verify(simulator, never()).put(any(), any());
    }

    @Test
    void manualGaugesKeepThePreviousValues() {
        VirtualDevice existing = VirtualDevice.builder().cropId(CROP).ownerId(OWNER).mode(VirtualMode.MANUAL)
                .manual(Measures.builder().ph(6.1).soilMoisture(70.0).build()).intervalSeconds(30).build();
        when(repository.findByCropId(CROP)).thenReturn(Optional.of(existing));

        service.configure(OWNER, CROP, new VirtualDeviceRequest(VirtualMode.MANUAL,
                new VirtualDeviceRequest.ManualValues(null, null, null, null, null, null, 35.0), null, null));

        ArgumentCaptor<SimulatorPotRequest> sent = ArgumentCaptor.forClass(SimulatorPotRequest.class);
        verify(simulator).put(eq(CROP), sent.capture());
        assertThat(sent.getValue().manual()).containsEntry("ph", 6.1).containsEntry("soilMoisture", 35.0);
        assertThat(sent.getValue().intervalSeconds()).isEqualTo(30);
    }

    @Test
    void limitsVirtualPotsPerAccount() {
        when(repository.countByOwnerId(OWNER)).thenReturn((long) VirtualDeviceService.MAX_PER_ACCOUNT);
        assertThatThrownBy(() -> service.configure(OWNER, CROP, weather()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("hasta 5");
    }

    @Test
    void foreignCropsAreNeverSimulated() {
        when(cropService.getOwned("otra-persona", CROP)).thenThrow(ApiException.notFound("El cultivo no existe"));
        assertThatThrownBy(() -> service.configure("otra-persona", CROP, weather())).isInstanceOf(ApiException.class);
        verify(simulator, never()).put(any(), any());
    }

    @Test
    void reconcileRecreatesMissingPotsAndRemovesOrphans() {
        VirtualDevice config = VirtualDevice.builder().cropId(CROP).ownerId(OWNER).mode(VirtualMode.AUTO)
                .intervalSeconds(30).build();
        when(repository.findAll()).thenReturn(List.of(config));
        when(simulator.list()).thenReturn(List.of(pot(ORPHAN, true)));

        service.reconcile();

        verify(simulator).put(eq(CROP), any());
        verify(simulator).delete(ORPHAN);
    }

    @Test
    void followsKeyRotationAndCropDeletion() {
        VirtualDevice config = VirtualDevice.builder().cropId(CROP).ownerId(OWNER).mode(VirtualMode.AUTO)
                .intervalSeconds(30).build();
        when(repository.findByCropId(CROP)).thenReturn(Optional.of(config));

        service.onKeyRotated(new DeviceKeyRotatedEvent(CROP));
        verify(simulator).put(eq(CROP), any());

        service.onCropDeleted(new CropDeletedEvent(CROP, OWNER));
        verify(repository).deleteByCropId(CROP);
        verify(simulator).delete(CROP);
    }

    @Test
    void reportsWhenTheServerHasNoSimulator() {
        when(simulator.isAvailable()).thenReturn(false);
        assertThat(service.get(OWNER, CROP).available()).isFalse();
        assertThatThrownBy(() -> service.configure(OWNER, CROP, weather())).isInstanceOf(ApiException.class);
    }
}
