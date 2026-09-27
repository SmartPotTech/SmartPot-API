package app.smartpot.api.overview.service;

import app.smartpot.api.actuators.model.entity.Actuator;
import app.smartpot.api.actuators.model.entity.ActuatorType;
import app.smartpot.api.actuators.service.ActuatorService;
import app.smartpot.api.ai.config.AiProperties;
import app.smartpot.api.ai.model.dto.FleetRequest;
import app.smartpot.api.ai.model.dto.FleetResponse;
import app.smartpot.api.ai.service.AiClient;
import app.smartpot.api.commands.service.CommandService;
import app.smartpot.api.crops.model.entity.Crop;
import app.smartpot.api.crops.model.entity.CropHealth;
import app.smartpot.api.crops.model.entity.CropType;
import app.smartpot.api.crops.model.entity.Device;
import app.smartpot.api.crops.service.CropService;
import app.smartpot.api.exception.ApiException;
import app.smartpot.api.notifications.service.NotificationService;
import app.smartpot.api.overview.model.dto.MetricSeriesResponse;
import app.smartpot.api.overview.model.dto.OverviewResponse;
import app.smartpot.api.readings.model.entity.Measures;
import app.smartpot.api.readings.model.entity.Reading;
import app.smartpot.api.readings.repository.ReadingSeriesRepository;
import app.smartpot.api.readings.service.ReadingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OverviewServiceTest {

    private static final String OWNER = "6718f0a1b2c3d4e5f6a7b000";
    private static final String LETTUCE = "6718f0a1b2c3d4e5f6a7b8c1";
    private static final String TOMATO = "6718f0a1b2c3d4e5f6a7b8c2";
    private static final Instant NOW = Instant.parse("2026-09-26T17:00:00Z");

    private final CropService cropService = mock(CropService.class);
    private final ReadingService readingService = mock(ReadingService.class);
    private final ActuatorService actuatorService = mock(ActuatorService.class);
    private final CommandService commandService = mock(CommandService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final ReadingSeriesRepository seriesRepository = mock(ReadingSeriesRepository.class);
    private final AiClient aiClient = mock(AiClient.class);
    private OverviewService service;

    private final Crop lettuce = Crop.builder().id(LETTUCE).ownerId(OWNER).name("Lechugas").type(CropType.LETTUCE)
            .automationEnabled(true).device(Device.builder().online(true).build())
            .health(new CropHealth(92, "EXCELLENT", "Excelente", NOW)).createdAt(NOW).build();
    private final Crop tomato = Crop.builder().id(TOMATO).ownerId(OWNER).name("Tomates").type(CropType.TOMATO)
            .device(Device.builder().online(true).build())
            .health(new CropHealth(40, "POOR", "En riesgo", NOW)).createdAt(NOW).build();

    @BeforeEach
    void setUp() {
        service = new OverviewService(cropService, readingService, actuatorService, commandService, notificationService,
                seriesRepository, aiClient, Clock.fixed(NOW, ZoneOffset.UTC),
                new AiProperties(true, "http://ai", "t", Duration.ofSeconds(2), Duration.ofMinutes(5),
                        Duration.ofMinutes(10), 48, ZoneId.of("America/Bogota")));
        when(cropService.list(OWNER)).thenReturn(List.of(lettuce, tomato));
        when(readingService.latest(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void totalsSummarizeTheWholeAccount() {
        when(notificationService.unreadCount(OWNER)).thenReturn(3L);
        when(commandService.countSince(anyCollection(), eq(NOW.minus(Duration.ofHours(24))))).thenReturn(7L);

        OverviewResponse.Totals totals = service.overview(OWNER).totals();

        assertThat(totals.crops()).isEqualTo(2);
        assertThat(totals.online()).isEqualTo(2);
        assertThat(totals.automated()).isEqualTo(1);
        assertThat(totals.averageHealth()).isEqualTo(66.0);
        assertThat(totals.needsAttention()).isEqualTo(1);
        assertThat(totals.unreadAlerts()).isEqualTo(3);
        assertThat(totals.commandsLast24h()).isEqualTo(7);
    }

    @Test
    void seriesGroupBucketsByCrop() {
        when(seriesRepository.averages(anyCollection(), eq("temperature"), eq(NOW.minus(Duration.ofHours(24))), eq(30)))
                .thenReturn(List.of(new ReadingSeriesRepository.Bucket(TOMATO, NOW, 24.456),
                        new ReadingSeriesRepository.Bucket(LETTUCE, NOW, 18.2)));

        MetricSeriesResponse series = service.series(OWNER, "temperature", 24);

        assertThat(series.bucketMinutes()).isEqualTo(30);
        assertThat(series.series()).extracting(MetricSeriesResponse.CropSeries::name).containsExactly("Lechugas", "Tomates");
        assertThat(series.series().get(1).points().getFirst().value()).isEqualTo(24.46);
    }

    @Test
    void seriesRejectUnknownMetricsAndPeriods() {
        assertThatThrownBy(() -> service.series(OWNER, "password", 24)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.series(OWNER, "ph", 500)).isInstanceOf(ApiException.class);
    }

    @Test
    void bucketsKeepAboutFortyEightPointsPerSeries() {
        assertThat(OverviewService.bucketMinutes(6)).isEqualTo(10);
        assertThat(OverviewService.bucketMinutes(24)).isEqualTo(30);
        assertThat(OverviewService.bucketMinutes(168)).isEqualTo(180);
    }

    @Test
    void fleetSendsEveryCropWithItsLatestReadingActuatorsAndLocalHour() {
        when(readingService.latest(LETTUCE)).thenReturn(Optional.of(Reading.builder()
                .measures(Measures.builder().temperature(18.0).build()).build()));
        when(actuatorService.listForCrop(anyString()))
                .thenReturn(List.of(Actuator.builder().type(ActuatorType.WATER_PUMP).build()));
        when(aiClient.fleet(any())).thenReturn(new FleetResponse(70.0, List.of(), List.of(), List.of(), List.of(), "ok"));

        service.fleet(OWNER);

        ArgumentCaptor<FleetRequest> request = ArgumentCaptor.forClass(FleetRequest.class);
        verify(aiClient).fleet(request.capture());
        assertThat(request.getValue().localHour()).isEqualTo(12);
        assertThat(request.getValue().crops()).hasSize(2);
        assertThat(request.getValue().crops().getFirst().measures().getTemperature()).isEqualTo(18.0);
        assertThat(request.getValue().crops().getFirst().actuators()).containsExactly("WATER_PUMP");
        assertThat(request.getValue().crops().get(1).measures()).isNull();
    }

    @Test
    void fleetWithoutCropsDoesNotCallTheAssistant() {
        when(cropService.list(OWNER)).thenReturn(List.of());

        assertThat(service.fleet(OWNER).summary()).contains("primer cultivo");
        verify(aiClient, never()).fleet(any());
        verify(seriesRepository, never()).averages(anyCollection(), anyString(), any(), anyInt());
    }
}
