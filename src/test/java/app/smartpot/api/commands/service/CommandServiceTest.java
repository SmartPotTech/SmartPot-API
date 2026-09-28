package app.smartpot.api.commands.service;

import app.smartpot.api.actuators.model.entity.Actuator;
import app.smartpot.api.actuators.model.entity.ActuatorType;
import app.smartpot.api.actuators.service.ActuatorService;
import app.smartpot.api.commands.model.dto.BulkCommandRequest;
import app.smartpot.api.commands.model.dto.BulkCommandResponse;
import app.smartpot.api.commands.model.dto.CommandRequest;
import app.smartpot.api.commands.model.entity.Command;
import app.smartpot.api.commands.model.entity.CommandAction;
import app.smartpot.api.commands.model.entity.CommandSource;
import app.smartpot.api.commands.model.entity.CommandStatus;
import app.smartpot.api.commands.repository.CommandRepository;
import app.smartpot.api.crops.model.entity.Crop;
import app.smartpot.api.crops.service.CropService;
import app.smartpot.api.exception.ApiException;
import app.smartpot.api.mqtt.model.CommandAckMessage;
import app.smartpot.api.mqtt.service.MqttGateway;
import app.smartpot.api.mqtt.service.MqttTopicResolver;
import app.smartpot.api.notifications.service.NotificationService;
import app.smartpot.api.support.TestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandServiceTest {

    private static final String OWNER = "6718f0a1b2c3d4e5f6a7b000";
    private static final String CROP = "6718f0a1b2c3d4e5f6a7b8c9";
    private static final String ACTUATOR = "6718f0a1b2c3d4e5f6a7b8d0";

    private final CommandRepository repository = mock(CommandRepository.class);
    private final CropService cropService = mock(CropService.class);
    private final ActuatorService actuatorService = mock(ActuatorService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final MqttGateway gateway = mock(MqttGateway.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-26T12:00:00Z"), ZoneOffset.UTC);
    private final Crop crop = Crop.builder().id(CROP).ownerId(OWNER).name("Lechugas").build();
    private final Actuator pump = Actuator.builder().id(ACTUATOR).cropId(CROP).type(ActuatorType.WATER_PUMP).build();
    private CommandService service;

    @BeforeEach
    void setUp() {
        service = new CommandService(repository, cropService, actuatorService, notificationService, gateway,
                new MqttTopicResolver(TestProperties.mqtt()), JsonMapper.builder().build(), clock, TestProperties.mqtt());
        when(repository.save(any(Command.class))).thenAnswer(invocation -> {
            Command command = invocation.getArgument(0);
            if (command.getId() == null) {
                command.setId("6718f0a1b2c3d4e5f6a7b8e1");
            }
            return command;
        });
        when(cropService.getOwned(OWNER, CROP)).thenReturn(crop);
        when(cropService.find(CROP)).thenReturn(Optional.of(crop));
        when(actuatorService.getForCrop(CROP, ACTUATOR)).thenReturn(pump);
        when(gateway.isEnabled()).thenReturn(true);
    }

    @Test
    void publishesTheCommandAndMarksItAsSent() {
        when(gateway.publish(anyString(), anyString(), anyInt(), anyBoolean())).thenReturn(true);

        Command command = service.request(OWNER, CROP, new CommandRequest(ACTUATOR, CommandAction.ACTIVATE, 30));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(gateway).publish(eq("smartpot/v1/" + CROP + "/commands"), payload.capture(), eq(1), eq(false));
        assertThat(payload.getValue()).contains("\"actuator\":\"WATER_PUMP\"", "\"action\":\"ACTIVATE\"",
                "\"durationSeconds\":30", command.getId());
        assertThat(command.getStatus()).isEqualTo(CommandStatus.SENT);
        assertThat(command.getSource()).isEqualTo(CommandSource.USER);
    }

    @Test
    void marksTheCommandAsFailedWhenTheBrokerIsDown() {
        when(gateway.publish(anyString(), anyString(), anyInt(), anyBoolean())).thenReturn(false);

        Command command = service.request(OWNER, CROP, new CommandRequest(ACTUATOR, CommandAction.ACTIVATE, null));

        assertThat(command.getStatus()).isEqualTo(CommandStatus.FAILED);
        assertThat(command.getMessage()).contains("broker");
    }

    @Test
    void acknowledgementCompletesTheCommandAndUpdatesTheActuator() {
        Command sent = Command.builder().id("6718f0a1b2c3d4e5f6a7b8e1").cropId(CROP).actuatorId(ACTUATOR)
                .actuatorType(ActuatorType.WATER_PUMP).action(CommandAction.ACTIVATE).status(CommandStatus.SENT)
                .source(CommandSource.USER).build();
        when(repository.findByIdAndCropId(sent.getId(), CROP)).thenReturn(Optional.of(sent));

        service.acknowledge(CROP, new CommandAckMessage(sent.getId(), "EXECUTED", "ok"));

        assertThat(sent.getStatus()).isEqualTo(CommandStatus.EXECUTED);
        verify(actuatorService).updateState(ACTUATOR, true, null);
    }

    @Test
    void aTimedCommandKeepsTheActuatorRunningUntilItEnds() {
        Command sent = Command.builder().id("6718f0a1b2c3d4e5f6a7b8e1").cropId(CROP).actuatorId(ACTUATOR)
                .actuatorType(ActuatorType.WATER_PUMP).action(CommandAction.ACTIVATE).durationSeconds(15)
                .status(CommandStatus.SENT).source(CommandSource.USER).build();
        when(repository.findByIdAndCropId(sent.getId(), CROP)).thenReturn(Optional.of(sent));

        service.acknowledge(CROP, new CommandAckMessage(sent.getId(), "EXECUTED", "ok"));

        verify(actuatorService).updateState(ACTUATOR, false, clock.instant().plusSeconds(15));
    }

    @Test
    void anActuatorThatIsAlreadyOffCannotBeTurnedOff() {
        assertThatThrownBy(() -> service.request(OWNER, CROP, new CommandRequest(ACTUATOR, CommandAction.DEACTIVATE,
                null))).isInstanceOf(ApiException.class).hasMessage("La bomba de agua ya está apagada");

        pump.setActive(true);
        assertThatThrownBy(() -> service.request(OWNER, CROP, new CommandRequest(ACTUATOR, CommandAction.ACTIVATE,
                null))).hasMessage("La bomba de agua ya está encendida");
        verify(gateway, never()).publish(anyString(), anyString(), anyInt(), anyBoolean());
    }

    @Test
    void aTimedRunCanBeTurnedOffAndACommandInFlightBlocksAnother() {
        when(gateway.publish(anyString(), anyString(), anyInt(), anyBoolean())).thenReturn(true);
        pump.setRunningUntil(clock.instant().plusSeconds(10));

        assertThat(service.request(OWNER, CROP, new CommandRequest(ACTUATOR, CommandAction.DEACTIVATE, null))
                .getStatus()).isEqualTo(CommandStatus.SENT);

        when(repository.existsByActuatorIdAndStatusIn(eq(ACTUATOR), any())).thenReturn(true);
        assertThatThrownBy(() -> service.request(OWNER, CROP, new CommandRequest(ACTUATOR, CommandAction.DEACTIVATE,
                null))).hasMessageContaining("orden en curso");
    }

    @Test
    void theAgentDoesNotRepeatWhatIsAlreadyTrue() {
        when(actuatorService.findByType(CROP, ActuatorType.WATER_PUMP)).thenReturn(Optional.of(pump));

        assertThat(service.requestFromAgent(crop, ActuatorType.WATER_PUMP, CommandAction.DEACTIVATE, null, "Noche"))
                .isEmpty();
        pump.setRunningUntil(clock.instant().plusSeconds(30));
        assertThat(service.requestFromAgent(crop, ActuatorType.WATER_PUMP, CommandAction.ACTIVATE, 30, "Seco"))
                .isEmpty();
        verify(gateway, never()).publish(anyString(), anyString(), anyInt(), anyBoolean());
    }

    @Test
    void repeatedAcknowledgementsAreIgnored() {
        Command done = Command.builder().id("6718f0a1b2c3d4e5f6a7b8e1").cropId(CROP).status(CommandStatus.EXECUTED).build();
        when(repository.findByIdAndCropId(done.getId(), CROP)).thenReturn(Optional.of(done));

        service.acknowledge(CROP, new CommandAckMessage(done.getId(), "FAILED", "tarde"));

        assertThat(done.getStatus()).isEqualTo(CommandStatus.EXECUTED);
        verify(repository, never()).save(done);
    }

    @Test
    void agentSkipsActuatorsWithACommandInFlight() {
        when(actuatorService.findByType(CROP, ActuatorType.WATER_PUMP)).thenReturn(Optional.of(pump));
        when(repository.existsByActuatorIdAndStatusIn(eq(ACTUATOR), any())).thenReturn(true);

        assertThat(service.requestFromAgent(crop, ActuatorType.WATER_PUMP, CommandAction.ACTIVATE, 30, "Sustrato seco"))
                .isEmpty();
        verify(gateway, never()).publish(anyString(), anyString(), anyInt(), anyBoolean());
    }

    @Test
    void expiresCommandsWithoutAcknowledgement() {
        Command stale = Command.builder().id("6718f0a1b2c3d4e5f6a7b8e2").cropId(CROP).status(CommandStatus.SENT)
                .sentAt(clock.instant().minusSeconds(600)).build();
        when(repository.findByStatusAndSentAtBefore(eq(CommandStatus.SENT), any())).thenReturn(List.of(stale));

        service.expireStale();

        assertThat(stale.getStatus()).isEqualTo(CommandStatus.EXPIRED);
        verify(notificationService).notifyOnce(anyString(), any(), eq(OWNER), eq(CROP), any(), anyString(), anyString());
    }

    @Test
    void bulkCommandsReportEachCropSeparately() {
        Crop tomato = Crop.builder().id("6718f0a1b2c3d4e5f6a7b8ca").ownerId(OWNER).name("Tomates").build();
        Crop basil = Crop.builder().id("6718f0a1b2c3d4e5f6a7b8cb").ownerId(OWNER).name("Albahaca").build();
        Actuator busyPump = Actuator.builder().id("6718f0a1b2c3d4e5f6a7b8d1").cropId(basil.getId())
                .type(ActuatorType.WATER_PUMP).build();
        when(cropService.list(OWNER)).thenReturn(List.of(crop, tomato, basil));
        when(actuatorService.findByType(CROP, ActuatorType.WATER_PUMP)).thenReturn(Optional.of(pump));
        when(actuatorService.findByType(tomato.getId(), ActuatorType.WATER_PUMP)).thenReturn(Optional.empty());
        when(actuatorService.findByType(basil.getId(), ActuatorType.WATER_PUMP)).thenReturn(Optional.of(busyPump));
        when(repository.existsByActuatorIdAndStatusIn(eq(busyPump.getId()), any())).thenReturn(true);
        when(gateway.publish(anyString(), anyString(), anyInt(), anyBoolean())).thenReturn(true);

        BulkCommandResponse response = service.requestBulk(OWNER,
                new BulkCommandRequest(null, ActuatorType.WATER_PUMP, CommandAction.ACTIVATE, 15));

        assertThat(response.sent()).isEqualTo(1);
        assertThat(response.skipped()).isEqualTo(2);
        assertThat(response.results()).extracting(BulkCommandResponse.Result::status)
                .containsExactly("SENT", "SKIPPED", "SKIPPED");
        assertThat(response.results().get(1).message()).isEqualTo("El cultivo no tiene este actuador");
    }

    @Test
    void bulkSkipsCropsWhereTheOrderChangesNothing() {
        when(cropService.list(OWNER)).thenReturn(List.of(crop));
        when(actuatorService.findByType(CROP, ActuatorType.WATER_PUMP)).thenReturn(Optional.of(pump));

        BulkCommandResponse response = service.requestBulk(OWNER,
                new BulkCommandRequest(null, ActuatorType.WATER_PUMP, CommandAction.DEACTIVATE, null));

        assertThat(response.results()).extracting(BulkCommandResponse.Result::message)
                .containsExactly("La bomba de agua ya está apagada");
        verify(gateway, never()).publish(anyString(), anyString(), anyInt(), anyBoolean());
    }

    @Test
    void bulkCommandsOnlyTouchTheCallersCrops() {
        when(cropService.getOwned(OWNER, "6718f0a1b2c3d4e5f6a7b999"))
                .thenThrow(ApiException.notFound("El cultivo no existe"));

        assertThatThrownBy(() -> service.requestBulk(OWNER, new BulkCommandRequest(
                        List.of("6718f0a1b2c3d4e5f6a7b999"), ActuatorType.FAN, CommandAction.DEACTIVATE, null)))
                .hasMessage("El cultivo no existe");
        verify(gateway, never()).publish(anyString(), anyString(), anyInt(), anyBoolean());
    }

    @Test
    void ownerHistoryIsEmptyWithoutCrops() {
        when(cropService.list(OWNER)).thenReturn(List.of());
        assertThat(service.listForOwner(OWNER, 50)).isEmpty();
    }
}
