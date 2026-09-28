package app.smartpot.api.readings.service;

import app.smartpot.api.cache.CacheStore;
import app.smartpot.api.config.SmartPotProperties;
import app.smartpot.api.crops.model.entity.Crop;
import app.smartpot.api.crops.service.CropService;
import app.smartpot.api.exception.ApiException;
import app.smartpot.api.readings.model.ReadingRecordedEvent;
import app.smartpot.api.readings.model.dto.ReadingSummaryResponse;
import app.smartpot.api.readings.model.entity.Measures;
import app.smartpot.api.readings.model.entity.Reading;
import app.smartpot.api.readings.model.entity.ReadingSource;
import app.smartpot.api.readings.repository.ReadingRepository;
import app.smartpot.api.readings.validator.MeasureRanges;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class ReadingService {

    public static final List<String> METRICS =
            List.of("temperature", "humidity", "brightness", "ph", "tds", "atmosphere", "soilMoisture");
    private static final int EXPORT_LIMIT = 50_000;

    private final ReadingRepository readingRepository;
    private final CropService cropService;
    private final CacheStore cacheStore;
    private final MongoTemplate mongoTemplate;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;
    private final Duration minInterval;
    private final int maxPageSize;

    public ReadingService(ReadingRepository readingRepository, CropService cropService, CacheStore cacheStore,
                          MongoTemplate mongoTemplate, ApplicationEventPublisher publisher, Clock clock,
                          SmartPotProperties properties) {
        this.readingRepository = readingRepository;
        this.cropService = cropService;
        this.cacheStore = cacheStore;
        this.mongoTemplate = mongoTemplate;
        this.publisher = publisher;
        this.clock = clock;
        this.minInterval = properties.readings().minInterval();
        this.maxPageSize = properties.readings().maxPageSize();
    }

    private static Double number(Object value) {
        return value instanceof Number n ? n.doubleValue() : null;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static String cell(Double value) {
        return value == null ? "" : value.toString();
    }

    /**
     * Telemetría del dispositivo. Se descarta si el cultivo no existe o si llega más rápido que el intervalo mínimo.
     */
    public Optional<Reading> recordFromDevice(String cropId, Measures measures) {
        Optional<Crop> crop = cropService.find(cropId);
        if (crop.isEmpty()) {
            log.debug("Telemetría descartada: el cultivo {} no existe", cropId);
            return Optional.empty();
        }
        cropService.markDeviceSeen(cropId, true);
        if (!cacheStore.setIfAbsent("reading:" + cropId, minInterval)) {
            log.debug("Telemetría descartada por frecuencia para {}", cropId);
            return Optional.empty();
        }
        return Optional.of(store(crop.get(), measures, ReadingSource.MQTT));
    }

    public Reading recordManual(String ownerId, String cropId, Measures measures) {
        Crop crop = cropService.getOwned(ownerId, cropId);
        try {
            MeasureRanges.validate(measures);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(ex.getMessage());
        }
        return store(crop, measures, ReadingSource.HTTP);
    }

    public Optional<Reading> latest(String cropId) {
        return readingRepository.findFirstByCropIdOrderByMeasuredAtDesc(cropId);
    }

    public Optional<Reading> latestOwned(String ownerId, String cropId) {
        cropService.getOwned(ownerId, cropId);
        return latest(cropId);
    }

    public List<Reading> recent(String cropId, int count) {
        return readingRepository.findByCropIdOrderByMeasuredAtDesc(cropId, PageRequest.of(0, count));
    }

    /**
     * Lecturas en orden cronológico para graficar. Por defecto, las últimas 24 horas.
     */
    public List<Reading> list(String ownerId, String cropId, Instant from, Instant to, Integer limit) {
        cropService.getOwned(ownerId, cropId);
        Window window = window(from, to);
        int size = limit == null ? maxPageSize : Math.clamp(limit, 1, maxPageSize);
        List<Reading> readings = readingRepository.findByCropIdAndMeasuredAtBetweenOrderByMeasuredAtDesc(
                cropId, window.from(), window.to(), PageRequest.of(0, size));
        return readings.reversed();
    }

    public ReadingSummaryResponse summary(String ownerId, String cropId, int hours) {
        cropService.getOwned(ownerId, cropId);
        Instant to = clock.instant();
        Instant from = to.minus(Duration.ofHours(Math.clamp(hours, 1, 24 * 90)));
        GroupOperation group = Aggregation.group().count().as("count");
        for (String metric : METRICS) {
            String field = "measures." + metric;
            group = group.min(field).as(metric + "Min").avg(field).as(metric + "Avg").max(field).as(metric + "Max");
        }
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("cropId").is(new ObjectId(cropId)).and("measuredAt").gte(from).lte(to)),
                group);
        Document result = mongoTemplate.aggregate(aggregation, "readings", Document.class).getUniqueMappedResult();
        Map<String, ReadingSummaryResponse.MetricSummary> metrics = new LinkedHashMap<>();
        long count = 0;
        if (result != null) {
            count = ((Number) result.getOrDefault("count", 0)).longValue();
            for (String metric : METRICS) {
                Double avg = number(result.get(metric + "Avg"));
                if (avg != null) {
                    metrics.put(metric, new ReadingSummaryResponse.MetricSummary(
                            number(result.get(metric + "Min")), round(avg), number(result.get(metric + "Max"))));
                }
            }
        }
        return new ReadingSummaryResponse(from, to, count, metrics);
    }

    public String exportCsv(String ownerId, String cropId, Instant from, Instant to) {
        cropService.getOwned(ownerId, cropId);
        Window window = window(from, to);
        List<Reading> readings = readingRepository.findByCropIdAndMeasuredAtBetweenOrderByMeasuredAtDesc(
                cropId, window.from(), window.to(), PageRequest.of(0, EXPORT_LIMIT)).reversed();
        StringBuilder csv = new StringBuilder("measuredAt,").append(String.join(",", METRICS)).append('\n');
        for (Reading reading : readings) {
            Measures m = reading.getMeasures();
            csv.append(reading.getMeasuredAt()).append(',')
                    .append(cell(m.getTemperature())).append(',').append(cell(m.getHumidity())).append(',')
                    .append(cell(m.getBrightness())).append(',').append(cell(m.getPh())).append(',')
                    .append(cell(m.getTds())).append(',').append(cell(m.getAtmosphere())).append(',')
                    .append(cell(m.getSoilMoisture())).append('\n');
        }
        return csv.toString();
    }

    private Reading store(Crop crop, Measures measures, ReadingSource source) {
        MeasureRanges.validate(measures);
        Reading reading = readingRepository.save(Reading.builder()
                .cropId(crop.getId())
                .measuredAt(clock.instant())
                .measures(measures)
                .source(source)
                .build());
        publisher.publishEvent(new ReadingRecordedEvent(crop, reading));
        return reading;
    }

    private Window window(Instant from, Instant to) {
        Instant end = to == null ? clock.instant() : to;
        Instant start = from == null ? end.minus(Duration.ofHours(24)) : from;
        if (start.isAfter(end)) {
            throw ApiException.badRequest("La fecha inicial debe ser anterior a la final");
        }
        return new Window(start, end);
    }

    private record Window(Instant from, Instant to) {
    }
}
