package app.smartpot.api.overview.service;

import app.smartpot.api.actuators.model.entity.Actuator;
import app.smartpot.api.actuators.service.ActuatorService;
import app.smartpot.api.ai.config.AiProperties;
import app.smartpot.api.ai.model.dto.FleetRequest;
import app.smartpot.api.ai.model.dto.FleetResponse;
import app.smartpot.api.ai.service.AiClient;
import app.smartpot.api.commands.service.CommandService;
import app.smartpot.api.crops.mapper.CropMapper;
import app.smartpot.api.crops.model.dto.CropResponse;
import app.smartpot.api.crops.model.entity.Crop;
import app.smartpot.api.crops.service.CropService;
import app.smartpot.api.exception.ApiException;
import app.smartpot.api.notifications.service.NotificationService;
import app.smartpot.api.overview.model.dto.MetricSeriesResponse;
import app.smartpot.api.overview.model.dto.OverviewResponse;
import app.smartpot.api.readings.model.entity.Reading;
import app.smartpot.api.readings.repository.ReadingSeriesRepository;
import app.smartpot.api.readings.service.ReadingService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Panel general: la cuenta completa en lugar de un cultivo. Totales, series comparables entre
 * cultivos y el análisis de flota del asistente de IA.
 */
@Service
public class OverviewService {

    static final Set<String> METRICS = Set.of("temperature", "humidity", "brightness", "ph", "tds", "soilMoisture",
            "atmosphere");
    static final double ATTENTION_HEALTH = 70;

    private final CropService cropService;
    private final ReadingService readingService;
    private final ActuatorService actuatorService;
    private final CommandService commandService;
    private final NotificationService notificationService;
    private final ReadingSeriesRepository seriesRepository;
    private final AiClient aiClient;
    private final Clock clock;
    private final ZoneId timezone;

    public OverviewService(CropService cropService, ReadingService readingService, ActuatorService actuatorService,
                           CommandService commandService, NotificationService notificationService,
                           ReadingSeriesRepository seriesRepository, AiClient aiClient, Clock clock,
                           AiProperties aiProperties) {
        this.cropService = cropService;
        this.readingService = readingService;
        this.actuatorService = actuatorService;
        this.commandService = commandService;
        this.notificationService = notificationService;
        this.seriesRepository = seriesRepository;
        this.aiClient = aiClient;
        this.clock = clock;
        this.timezone = aiProperties.timezone();
    }

    public OverviewResponse overview(String ownerId) {
        List<Crop> crops = cropService.list(ownerId);
        List<CropResponse> responses = crops.stream()
                .map(crop -> CropMapper.toResponse(crop, readingService.latest(crop.getId()).orElse(null)))
                .toList();

        int online = (int) crops.stream().filter(crop -> crop.getDevice() != null && crop.getDevice().isOnline()).count();
        int automated = (int) crops.stream().filter(Crop::isAutomationEnabled).count();
        List<Double> health = crops.stream().filter(crop -> crop.getHealth() != null)
                .map(crop -> crop.getHealth().index()).toList();
        Double average = health.isEmpty() ? null
                : Math.round(health.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 10) / 10.0;
        int attention = (int) crops.stream().filter(crop -> crop.getDevice() == null || !crop.getDevice().isOnline()
                || (crop.getHealth() != null && crop.getHealth().index() < ATTENTION_HEALTH)).count();
        long commands = commandService.countSince(crops.stream().map(Crop::getId).toList(),
                clock.instant().minus(Duration.ofHours(24)));

        return new OverviewResponse(new OverviewResponse.Totals(crops.size(), online, automated, average, attention,
                notificationService.unreadCount(ownerId), commands), responses);
    }

    public MetricSeriesResponse series(String ownerId, String metric, int hours) {
        if (!METRICS.contains(metric)) {
            throw ApiException.badRequest("Variable desconocida: " + metric);
        }
        if (hours < 1 || hours > 168) {
            throw ApiException.badRequest("El periodo debe estar entre 1 y 168 horas");
        }
        List<Crop> crops = cropService.list(ownerId);
        int bucket = bucketMinutes(hours);
        Map<String, List<MetricSeriesResponse.Point>> points = new LinkedHashMap<>();
        crops.forEach(crop -> points.put(crop.getId(), new ArrayList<>()));
        for (ReadingSeriesRepository.Bucket row : seriesRepository.averages(points.keySet(), metric,
                clock.instant().minus(Duration.ofHours(hours)), bucket)) {
            points.get(row.cropId()).add(new MetricSeriesResponse.Point(row.time(), Math.round(row.value() * 100) / 100.0));
        }
        List<MetricSeriesResponse.CropSeries> series = crops.stream()
                .map(crop -> new MetricSeriesResponse.CropSeries(crop.getId(), crop.getName(), crop.getType().name(),
                        points.get(crop.getId())))
                .toList();
        return new MetricSeriesResponse(metric, hours, bucket, series);
    }

    public FleetResponse fleet(String ownerId) {
        List<Crop> crops = cropService.list(ownerId);
        if (crops.isEmpty()) {
            return new FleetResponse(null, List.of(), List.of(), List.of(), List.of(),
                    "Crea tu primer cultivo para que el asistente pueda compararlo.");
        }
        List<FleetRequest.Crop> input = crops.stream().map(crop -> new FleetRequest.Crop(
                crop.getId(), crop.getName(), crop.getType().name(),
                readingService.latest(crop.getId()).map(Reading::getMeasures).orElse(null),
                actuatorService.listForCrop(crop.getId()).stream().map(Actuator::getType).map(Enum::name).toList()))
                .toList();
        int hour = clock.instant().atZone(timezone).getHour();
        return aiClient.fleet(new FleetRequest(input, hour));
    }

    /** Unos 48 puntos por serie: suficiente para ver la forma sin saturar el gráfico. */
    static int bucketMinutes(int hours) {
        if (hours <= 6) {
            return 10;
        }
        if (hours <= 24) {
            return 30;
        }
        if (hours <= 72) {
            return 90;
        }
        return 180;
    }
}
