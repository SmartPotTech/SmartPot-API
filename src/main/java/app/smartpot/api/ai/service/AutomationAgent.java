package app.smartpot.api.ai.service;

import app.smartpot.api.actuators.model.entity.ActuatorType;
import app.smartpot.api.ai.config.AiProperties;
import app.smartpot.api.ai.model.dto.InsightResponse;
import app.smartpot.api.cache.CacheStore;
import app.smartpot.api.commands.model.entity.CommandAction;
import app.smartpot.api.commands.service.CommandService;
import app.smartpot.api.crops.model.entity.Crop;
import app.smartpot.api.exception.ApiException;
import app.smartpot.api.notifications.model.entity.NotificationType;
import app.smartpot.api.notifications.service.NotificationService;
import app.smartpot.api.readings.model.ReadingRecordedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;

/**
 * Agente reactivo: percibe cada lectura, la hace evaluar por el servicio de IA y, si el cultivo tiene
 * el modo automático, ejecuta las acciones propuestas respetando un enfriamiento por actuador.
 */
@Slf4j
@Component
public class AutomationAgent {

    private static final Duration AUTOMATIC_INTERVAL = Duration.ofSeconds(30);
    private static final Duration ALERT_COOLDOWN = Duration.ofHours(1);

    private final InsightService insightService;
    private final CommandService commandService;
    private final NotificationService notificationService;
    private final CacheStore cacheStore;
    private final AiClient aiClient;
    private final Duration evaluationInterval;
    private final Duration automationCooldown;

    public AutomationAgent(InsightService insightService, CommandService commandService,
                           NotificationService notificationService, CacheStore cacheStore, AiClient aiClient,
                           AiProperties properties) {
        this.insightService = insightService;
        this.commandService = commandService;
        this.notificationService = notificationService;
        this.cacheStore = cacheStore;
        this.aiClient = aiClient;
        this.evaluationInterval = properties.evaluationInterval();
        this.automationCooldown = properties.automationCooldown();
    }

    @Async
    @EventListener
    public void onReading(ReadingRecordedEvent event) {
        Crop crop = event.crop();
        if (!aiClient.isEnabled()) {
            return;
        }
        Duration interval = crop.isAutomationEnabled() ? AUTOMATIC_INTERVAL : evaluationInterval;
        if (!cacheStore.setIfAbsent("ai-eval:" + crop.getId(), interval)) {
            return;
        }
        try {
            InsightResponse insight = insightService.evaluate(crop, event.reading());
            alertCriticalFindings(crop, insight);
            if (crop.isAutomationEnabled()) {
                act(crop, insight);
            }
        } catch (ApiException ex) {
            log.debug("Evaluación omitida para {}: {}", crop.getId(), ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("El agente no pudo evaluar el cultivo {}: {}", crop.getId(), ex.getMessage());
        }
    }

    private void alertCriticalFindings(Crop crop, InsightResponse insight) {
        if (insight.diagnosis() == null) {
            return;
        }
        insight.diagnosis().stream()
                .filter(item -> "CRITICAL".equalsIgnoreCase(item.severity()))
                .forEach(item -> notificationService.notifyOnce("diagnosis:" + crop.getId() + ":" + item.parameter(),
                        ALERT_COOLDOWN, crop.getOwnerId(), crop.getId(), NotificationType.ALERT,
                        "Atención en " + crop.getName(), item.message() + " " + orEmpty(item.recommendation())));
    }

    private void act(Crop crop, InsightResponse insight) {
        if (insight.actions() == null) {
            return;
        }
        for (InsightResponse.Action action : insight.actions()) {
            ActuatorType type = parse(ActuatorType.class, action.actuator());
            CommandAction commandAction = parse(CommandAction.class, action.action());
            if (type == null || commandAction == null) {
                continue;
            }
            String cooldownKey = "agent:" + crop.getId() + ":" + type + ":" + commandAction;
            if (!cacheStore.setIfAbsent(cooldownKey, automationCooldown)) {
                continue;
            }
            commandService.requestFromAgent(crop, type, commandAction, action.durationSeconds(), action.reason())
                    .ifPresent(command -> notificationService.notifyOnce("agent:" + crop.getId(),
                            Duration.ofMinutes(30), crop.getOwnerId(), crop.getId(), NotificationType.AI,
                            "El asistente actuó en " + crop.getName(), action.reason()));
        }
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        if (value == null) {
            return null;
        }
        return Arrays.stream(type.getEnumConstants())
                .filter(constant -> constant.name().equalsIgnoreCase(value))
                .findFirst()
                .orElse(null);
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
