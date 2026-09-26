package app.smartpot.api.ai.service;

import app.smartpot.api.actuators.model.entity.ActuatorType;
import app.smartpot.api.ai.config.AiProperties;
import app.smartpot.api.ai.model.dto.InsightResponse;
import app.smartpot.api.cache.CacheStore;
import app.smartpot.api.commands.model.entity.Command;
import app.smartpot.api.commands.model.entity.CommandAction;
import app.smartpot.api.commands.service.CommandService;
import app.smartpot.api.crops.model.entity.Crop;
import app.smartpot.api.notifications.model.entity.NotificationType;
import app.smartpot.api.notifications.service.NotificationService;
import app.smartpot.api.readings.model.ReadingRecordedEvent;
import app.smartpot.api.readings.model.entity.Reading;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutomationAgentTest {

    private static final String CROP = "6718f0a1b2c3d4e5f6a7b8c9";

    private final InsightService insightService = mock(InsightService.class);
    private final CommandService commandService = mock(CommandService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final CacheStore cacheStore = mock(CacheStore.class);
    private final AiClient aiClient = mock(AiClient.class);
    private final Reading reading = Reading.builder().id("r1").cropId(CROP).build();
    private AutomationAgent agent;

    private final InsightResponse insight = new InsightResponse("LETTUCE",
            new InsightResponse.Health(38.0, "POOR", "En riesgo"),
            List.of(new InsightResponse.Diagnosis("soilMoisture", 18.0, "LOW", "CRITICAL",
                    "El sustrato está muy seco.", "Riega de inmediato.")),
            List.of(), List.of(),
            List.of(new InsightResponse.Action("WATER_PUMP", "ACTIVATE", 30, "Sustrato seco")),
            "Riega tu lechuga", null);

    @BeforeEach
    void setUp() {
        agent = new AutomationAgent(insightService, commandService, notificationService, cacheStore, aiClient,
                new AiProperties(true, "http://ai", "t", Duration.ofSeconds(2), Duration.ofMinutes(5),
                        Duration.ofMinutes(10), 48));
        when(aiClient.isEnabled()).thenReturn(true);
        when(cacheStore.setIfAbsent(anyString(), any())).thenReturn(true);
        when(insightService.evaluate(any(), any())).thenReturn(insight);
    }

    @Test
    void executesRecommendedActionsWhenAutomationIsOn() {
        Crop crop = crop(true);
        when(commandService.requestFromAgent(any(), any(), any(), any(), any())).thenReturn(Optional.of(new Command()));

        agent.onReading(new ReadingRecordedEvent(crop, reading));

        verify(commandService).requestFromAgent(crop, ActuatorType.WATER_PUMP, CommandAction.ACTIVATE, 30, "Sustrato seco");
    }

    @Test
    void onlyAdvisesWhenAutomationIsOff() {
        agent.onReading(new ReadingRecordedEvent(crop(false), reading));

        verify(commandService, never()).requestFromAgent(any(), any(), any(), any(), any());
        verify(notificationService).notifyOnce(startsWith("diagnosis:" + CROP), any(), eq("owner"), eq(CROP),
                eq(NotificationType.ALERT), anyString(), anyString());
    }

    @Test
    void respectsTheCooldownPerActuator() {
        when(cacheStore.setIfAbsent(startsWith("agent:"), any())).thenReturn(false);

        agent.onReading(new ReadingRecordedEvent(crop(true), reading));

        verify(commandService, never()).requestFromAgent(any(), any(), any(), any(), any());
    }

    @Test
    void skipsEvaluationInsideTheInterval() {
        when(cacheStore.setIfAbsent(startsWith("ai-eval:"), any())).thenReturn(false);

        agent.onReading(new ReadingRecordedEvent(crop(true), reading));

        verify(insightService, never()).evaluate(any(), any());
    }

    private static Crop crop(boolean automation) {
        return Crop.builder().id(CROP).ownerId("owner").name("Lechugas").automationEnabled(automation).build();
    }
}
