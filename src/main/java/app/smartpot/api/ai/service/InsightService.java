package app.smartpot.api.ai.service;

import app.smartpot.api.actuators.model.entity.Actuator;
import app.smartpot.api.actuators.service.ActuatorService;
import app.smartpot.api.ai.config.AiProperties;
import app.smartpot.api.ai.model.dto.HistoryPoint;
import app.smartpot.api.ai.model.dto.InsightRequest;
import app.smartpot.api.ai.model.dto.InsightResponse;
import app.smartpot.api.crops.model.entity.Crop;
import app.smartpot.api.crops.model.entity.CropHealth;
import app.smartpot.api.crops.model.entity.Placement;
import app.smartpot.api.crops.service.CropService;
import app.smartpot.api.crops.service.CropWeatherService;
import app.smartpot.api.exception.ApiException;
import app.smartpot.api.readings.model.entity.Reading;
import app.smartpot.api.readings.service.ReadingService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
public class InsightService {

    private final AiClient aiClient;
    private final CropService cropService;
    private final ReadingService readingService;
    private final ActuatorService actuatorService;
    private final CropWeatherService weatherService;
    private final Clock clock;
    /** Cuántas lecturas se revisan por cada punto enviado: el historial abarca más tiempo sin crecer. */
    static final int HISTORY_WINDOW_FACTOR = 8;

    private final int historySize;
    private final ZoneId timezone;

    public InsightService(AiClient aiClient, CropService cropService, ReadingService readingService,
                          ActuatorService actuatorService, CropWeatherService weatherService, Clock clock,
                          AiProperties properties) {
        this.aiClient = aiClient;
        this.cropService = cropService;
        this.readingService = readingService;
        this.actuatorService = actuatorService;
        this.weatherService = weatherService;
        this.clock = clock;
        this.historySize = properties.historySize();
        this.timezone = properties.timezone();
    }

    public InsightResponse forOwner(String ownerId, String cropId) {
        Crop crop = cropService.getOwned(ownerId, cropId);
        Reading latest = readingService.latest(cropId)
                .orElseThrow(() -> ApiException.notFound("Aún no hay lecturas para analizar en este cultivo"));
        return evaluate(crop, latest);
    }

    /** Consulta al servicio de IA y guarda el índice de salud en el cultivo. */
    public InsightResponse evaluate(Crop crop, Reading latest) {
        List<Reading> window = readingService.recent(crop.getId(), historySize * HISTORY_WINDOW_FACTOR).reversed();
        List<HistoryPoint> history = sample(window, historySize).stream().map(HistoryPoint::of).toList();
        List<String> actuators = actuatorService.listForCrop(crop.getId()).stream()
                .map(Actuator::getType)
                .map(Enum::name)
                .toList();
        Instant now = clock.instant();
        Instant measuredAt = latest.getMeasuredAt() != null ? latest.getMeasuredAt() : now;
        InsightRequest request = new InsightRequest(crop.getType().name(), latest.getMeasures(), history, actuators,
                measuredAt.atZone(timezone).getHour(), place(crop.getPlacement()), outside(crop));
        InsightResponse response = aiClient.insights(request).withEvaluatedAt(now);
        if (response.health() != null) {
            cropService.updateHealth(crop.getId(), new CropHealth(response.health().index(),
                    response.health().level(), response.health().label(), now));
        }
        return response;
    }

    private static InsightRequest.Place place(Placement placement) {
        if (placement == null || (placement.setting() == null && placement.exposure() == null)) {
            return null;
        }
        return new InsightRequest.Place(placement.setting() == null ? null : placement.setting().name(),
                placement.exposure() == null ? null : placement.exposure().name());
    }

    private InsightRequest.Outside outside(Crop crop) {
        return weatherService.current(crop)
                .map(w -> new InsightRequest.Outside(w.temperature(), w.humidity(), w.precipitation(), w.radiation(),
                        w.isDay(), w.condition()))
                .orElse(null);
    }

    /**
     * Toma hasta {@code size} lecturas repartidas en todo el periodo, incluidas la primera y la última,
     * para que el pronóstico vea la tendencia de varias horas y no solo los últimos minutos.
     */
    static <T> List<T> sample(List<T> items, int size) {
        if (items.size() <= size || size < 2) {
            return items;
        }
        List<T> sampled = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            sampled.add(items.get((int) Math.round(i * (items.size() - 1) / (double) (size - 1))));
        }
        return sampled;
    }
}
