package app.smartpot.api.ai.service;

import app.smartpot.api.actuators.model.entity.Actuator;
import app.smartpot.api.actuators.service.ActuatorService;
import app.smartpot.api.ai.config.AiProperties;
import app.smartpot.api.ai.model.dto.InsightRequest;
import app.smartpot.api.ai.model.dto.InsightResponse;
import app.smartpot.api.crops.model.entity.Crop;
import app.smartpot.api.crops.model.entity.CropHealth;
import app.smartpot.api.crops.service.CropService;
import app.smartpot.api.exception.ApiException;
import app.smartpot.api.readings.model.entity.Measures;
import app.smartpot.api.readings.model.entity.Reading;
import app.smartpot.api.readings.service.ReadingService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

@Service
public class InsightService {

    private final AiClient aiClient;
    private final CropService cropService;
    private final ReadingService readingService;
    private final ActuatorService actuatorService;
    private final Clock clock;
    private final int historySize;
    private final ZoneId timezone;

    public InsightService(AiClient aiClient, CropService cropService, ReadingService readingService,
                          ActuatorService actuatorService, Clock clock, AiProperties properties) {
        this.aiClient = aiClient;
        this.cropService = cropService;
        this.readingService = readingService;
        this.actuatorService = actuatorService;
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
        List<Measures> history = readingService.recent(crop.getId(), historySize).reversed().stream()
                .map(Reading::getMeasures)
                .toList();
        List<String> actuators = actuatorService.listForCrop(crop.getId()).stream()
                .map(Actuator::getType)
                .map(Enum::name)
                .toList();
        Instant now = clock.instant();
        Instant measuredAt = latest.getMeasuredAt() != null ? latest.getMeasuredAt() : now;
        InsightRequest request = new InsightRequest(crop.getType().name(), latest.getMeasures(), history, actuators,
                measuredAt.atZone(timezone).getHour());
        InsightResponse response = aiClient.insights(request).withEvaluatedAt(now);
        if (response.health() != null) {
            cropService.updateHealth(crop.getId(), new CropHealth(response.health().index(),
                    response.health().level(), response.health().label(), now));
        }
        return response;
    }
}
