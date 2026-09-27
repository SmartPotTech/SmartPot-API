package app.smartpot.api.ai.service;

import app.smartpot.api.ai.config.AiLearningProperties;
import app.smartpot.api.ai.config.AiProperties;
import app.smartpot.api.ai.model.dto.LearningBatch;
import app.smartpot.api.ai.model.dto.LearningReading;
import app.smartpot.api.crops.model.entity.Crop;
import app.smartpot.api.crops.model.event.CropDeletedEvent;
import app.smartpot.api.exception.ApiException;
import app.smartpot.api.readings.model.ReadingRecordedEvent;
import app.smartpot.api.readings.model.entity.Reading;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lleva las lecturas reales al servicio de IA para el aprendizaje continuo; las de los cultivos virtuales son
 * sintéticas y no se envían. Se acumulan en memoria y se envían por lotes; si la IA no responde, el lote vuelve a la cola y se intenta en el próximo ciclo.
 * La cola tiene tope: ante una caída larga se descartan primero las lecturas más antiguas.
 */
@Slf4j
@Component
public class LearningFeed {

    static final int MAX_BATCH = 1000;
    static final int MAX_PENDING = 20_000;

    private final ConcurrentLinkedDeque<LearningReading> pending = new ConcurrentLinkedDeque<>();
    private final AtomicInteger size = new AtomicInteger();
    private final AiClient aiClient;
    private final boolean enabled;
    private final ZoneId timezone;

    public LearningFeed(AiClient aiClient, AiProperties aiProperties, AiLearningProperties properties) {
        this.aiClient = aiClient;
        this.enabled = aiClient.isEnabled() && properties.enabled();
        this.timezone = aiProperties.timezone();
    }

    @EventListener
    public void onReading(ReadingRecordedEvent event) {
        Crop crop = event.crop();
        if (!enabled || crop.isVirtual()) {
            return;
        }
        Reading reading = event.reading();
        pending.addLast(new LearningReading(crop.getId(), crop.getType().name(), reading.getMeasuredAt(),
                reading.getMeasuredAt().atZone(timezone).getHour(), reading.getMeasures()));
        if (size.incrementAndGet() > MAX_PENDING && pending.pollFirst() != null) {
            size.decrementAndGet();
        }
    }

    @Scheduled(fixedDelayString = "${smartpot.ai.learning.flush-interval:PT1M}",
            initialDelayString = "${smartpot.ai.learning.flush-interval:PT1M}")
    public void flush() {
        while (!pending.isEmpty()) {
            List<LearningReading> batch = drain();
            try {
                aiClient.learn(new LearningBatch(batch));
            } catch (ApiException ex) {
                for (int i = batch.size() - 1; i >= 0; i--) {
                    pending.addFirst(batch.get(i));
                }
                size.addAndGet(batch.size());
                log.debug("La IA no recibió {} lecturas; se reintenta en el próximo ciclo", batch.size());
                return;
            }
        }
    }

    @Async
    @EventListener
    public void onCropDeleted(CropDeletedEvent event) {
        if (pending.removeIf(reading -> reading.cropId().equals(event.cropId()))) {
            size.set(pending.size());
        }
        aiClient.forget(event.cropId());
    }

    int pendingCount() {
        return size.get();
    }

    private List<LearningReading> drain() {
        List<LearningReading> batch = new ArrayList<>(MAX_BATCH);
        LearningReading reading;
        while (batch.size() < MAX_BATCH && (reading = pending.pollFirst()) != null) {
            batch.add(reading);
        }
        size.addAndGet(-batch.size());
        return batch;
    }
}
