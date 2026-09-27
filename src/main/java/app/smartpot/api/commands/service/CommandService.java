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
import app.smartpot.api.exception.ObjectIds;
import app.smartpot.api.mqtt.config.MqttProperties;
import app.smartpot.api.mqtt.model.CommandAckMessage;
import app.smartpot.api.mqtt.model.CommandMessage;
import app.smartpot.api.mqtt.service.MqttGateway;
import app.smartpot.api.mqtt.service.MqttTopicResolver;
import app.smartpot.api.notifications.model.entity.NotificationType;
import app.smartpot.api.notifications.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class CommandService {

    private static final EnumSet<CommandStatus> IN_FLIGHT = EnumSet.of(CommandStatus.PENDING, CommandStatus.SENT);
    private static final Duration FAILURE_NOTICE_COOLDOWN = Duration.ofMinutes(15);

    private final CommandRepository repository;
    private final CropService cropService;
    private final ActuatorService actuatorService;
    private final NotificationService notificationService;
    private final MqttGateway gateway;
    private final MqttTopicResolver topics;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final Duration timeout;

    public CommandService(CommandRepository repository, CropService cropService, ActuatorService actuatorService,
                          NotificationService notificationService, MqttGateway gateway, MqttTopicResolver topics,
                          JsonMapper jsonMapper, Clock clock, MqttProperties mqttProperties) {
        this.repository = repository;
        this.cropService = cropService;
        this.actuatorService = actuatorService;
        this.notificationService = notificationService;
        this.gateway = gateway;
        this.topics = topics;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.timeout = mqttProperties.commandTimeout();
    }

    public Command request(String ownerId, String cropId, CommandRequest request) {
        Crop crop = cropService.getOwned(ownerId, cropId);
        Actuator actuator = actuatorService.getForCrop(crop.getId(), request.actuatorId());
        if (!gateway.isEnabled()) {
            throw ApiException.unavailable("La comunicación con las macetas no está habilitada en este servidor");
        }
        return dispatch(create(crop, actuator, request.action(), request.durationSeconds(), CommandSource.USER, null));
    }

    /** Comando decidido por el agente. Se omite si el cultivo no tiene ese actuador o ya hay uno en curso. */
    public Optional<Command> requestFromAgent(Crop crop, ActuatorType type, CommandAction action,
                                              Integer durationSeconds, String reason) {
        Optional<Actuator> actuator = actuatorService.findByType(crop.getId(), type);
        if (actuator.isEmpty() || !gateway.isEnabled()
                || repository.existsByActuatorIdAndStatusIn(actuator.get().getId(), IN_FLIGHT)) {
            return Optional.empty();
        }
        return Optional.of(dispatch(create(crop, actuator.get(), action, durationSeconds, CommandSource.AGENT, reason)));
    }

    /** Misma orden para varios cultivos; cada uno se informa por separado y ninguno detiene a los demás. */
    public BulkCommandResponse requestBulk(String ownerId, BulkCommandRequest request) {
        if (!gateway.isEnabled()) {
            throw ApiException.unavailable("La comunicación con las macetas no está habilitada en este servidor");
        }
        List<Crop> crops = request.cropIds() == null || request.cropIds().isEmpty()
                ? cropService.list(ownerId)
                : request.cropIds().stream().distinct().map(id -> cropService.getOwned(ownerId, id)).toList();
        List<BulkCommandResponse.Result> results = new ArrayList<>();
        for (Crop crop : crops) {
            Optional<Actuator> actuator = actuatorService.findByType(crop.getId(), request.actuatorType());
            if (actuator.isEmpty()) {
                results.add(skipped(crop, "El cultivo no tiene este actuador"));
            } else if (repository.existsByActuatorIdAndStatusIn(actuator.get().getId(), IN_FLIGHT)) {
                results.add(skipped(crop, "Ya hay un comando en curso para este actuador"));
            } else {
                Command command = dispatch(create(crop, actuator.get(), request.action(), request.durationSeconds(),
                        CommandSource.USER, "Acción en bloque"));
                results.add(new BulkCommandResponse.Result(crop.getId(), crop.getName(),
                        command.getStatus() == CommandStatus.SENT ? "SENT" : "FAILED", command.getId(),
                        command.getMessage()));
            }
        }
        return BulkCommandResponse.of(results);
    }

    /** Comandos de todos los cultivos de la cuenta, más recientes primero. */
    public List<Command> listForOwner(String ownerId, int limit) {
        List<String> cropIds = cropService.list(ownerId).stream().map(Crop::getId).toList();
        if (cropIds.isEmpty()) {
            return List.of();
        }
        return repository.findByCropIdInOrderByCreatedAtDesc(cropIds, PageRequest.of(0, Math.clamp(limit, 1, 200)));
    }

    public long countSince(Collection<String> cropIds, Instant since) {
        return cropIds.isEmpty() ? 0 : repository.countByCropIdInAndCreatedAtAfter(cropIds, since);
    }

    public List<Command> list(String ownerId, String cropId, int limit) {
        cropService.getOwned(ownerId, cropId);
        return repository.findByCropIdOrderByCreatedAtDesc(cropId, PageRequest.of(0, Math.clamp(limit, 1, 100)));
    }

    public void acknowledge(String cropId, CommandAckMessage ack) {
        if (ack == null || !ObjectIds.isValid(ack.id())) {
            return;
        }
        repository.findByIdAndCropId(ack.id(), cropId).ifPresent(command -> {
            if (!IN_FLIGHT.contains(command.getStatus())) {
                return;
            }
            command.setStatus(ack.executed() ? CommandStatus.EXECUTED : CommandStatus.FAILED);
            command.setMessage(truncate(ack.message()));
            command.setCompletedAt(clock.instant());
            repository.save(command);
            if (ack.executed()) {
                boolean active = command.getAction() == CommandAction.ACTIVATE && command.getDurationSeconds() == null;
                actuatorService.updateState(command.getActuatorId(), active);
            } else {
                notifyFailure(command, "La maceta no pudo ejecutar el comando: " + orDefault(ack.message()));
            }
        });
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void expireStale() {
        Instant limit = clock.instant().minus(timeout);
        for (Command command : repository.findByStatusAndSentAtBefore(CommandStatus.SENT, limit)) {
            command.setStatus(CommandStatus.EXPIRED);
            command.setMessage("La maceta no confirmó el comando a tiempo");
            command.setCompletedAt(clock.instant());
            repository.save(command);
            notifyFailure(command, "La maceta no respondió al comando. Revisa que esté conectada.");
        }
    }

    private Command create(Crop crop, Actuator actuator, CommandAction action, Integer durationSeconds,
                           CommandSource source, String reason) {
        return repository.save(Command.builder()
                .cropId(crop.getId())
                .actuatorId(actuator.getId())
                .actuatorType(actuator.getType())
                .action(action)
                .durationSeconds(action == CommandAction.ACTIVATE ? durationSeconds : null)
                .status(CommandStatus.PENDING)
                .source(source)
                .reason(reason)
                .createdAt(clock.instant())
                .build());
    }

    private Command dispatch(Command command) {
        CommandMessage message = new CommandMessage(command.getId(), command.getActuatorType().name(),
                command.getAction().name(), command.getDurationSeconds());
        boolean sent = gateway.publish(topics.commands(command.getCropId()), jsonMapper.writeValueAsString(message), 1, false);
        if (sent) {
            command.setStatus(CommandStatus.SENT);
            command.setSentAt(clock.instant());
        } else {
            command.setStatus(CommandStatus.FAILED);
            command.setMessage("El broker MQTT no está disponible en este momento");
            command.setCompletedAt(clock.instant());
        }
        log.info("Comando {} {} para {} ({})", command.getAction(), command.getActuatorType(), command.getCropId(),
                command.getStatus());
        return repository.save(command);
    }

    private void notifyFailure(Command command, String message) {
        cropService.find(command.getCropId()).ifPresent(crop -> notificationService.notifyOnce(
                "command-failure:" + crop.getId(), FAILURE_NOTICE_COOLDOWN, crop.getOwnerId(), crop.getId(),
                NotificationType.COMMAND, "Comando sin ejecutar en " + crop.getName(), message));
    }

    private static BulkCommandResponse.Result skipped(Crop crop, String reason) {
        return new BulkCommandResponse.Result(crop.getId(), crop.getName(), "SKIPPED", null, reason);
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 200 ? value : value.substring(0, 200);
    }

    private static String orDefault(String value) {
        return value == null || value.isBlank() ? "sin detalle" : truncate(value);
    }
}
