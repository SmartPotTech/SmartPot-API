package app.smartpot.api.ai.service;

import app.smartpot.api.ai.config.AiLearningProperties;
import app.smartpot.api.ai.config.AiProperties;
import app.smartpot.api.ai.model.dto.LearningBatch;
import app.smartpot.api.crops.model.entity.Crop;
import app.smartpot.api.crops.model.entity.CropKind;
import app.smartpot.api.crops.model.entity.CropType;
import app.smartpot.api.crops.model.event.CropDeletedEvent;
import app.smartpot.api.exception.ApiException;
import app.smartpot.api.readings.model.ReadingRecordedEvent;
import app.smartpot.api.readings.model.entity.Measures;
import app.smartpot.api.readings.model.entity.Reading;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LearningFeedTest {

    private static final String CROP = "6718f0a1b2c3d4e5f6a7b8c9";
    private static final String OTHER = "6718f0a1b2c3d4e5f6a7b8ca";

    private final AiClient aiClient = mock(AiClient.class);
    private LearningFeed feed;

    private static ReadingRecordedEvent reading(String cropId, int minute) {
        return reading(cropId, minute, CropKind.REAL);
    }

    private static ReadingRecordedEvent reading(String cropId, int minute, CropKind kind) {
        Crop crop = Crop.builder().id(cropId).type(CropType.LETTUCE).kind(kind).build();
        return new ReadingRecordedEvent(crop, Reading.builder().cropId(cropId)
                .measuredAt(Instant.parse("2026-09-27T17:00:00Z").plusSeconds(minute * 60L))
                .measures(Measures.builder().soilMoisture(64.0).build()).build());
    }

    @BeforeEach
    void setUp() {
        when(aiClient.isEnabled()).thenReturn(true);
        AiProperties ai = new AiProperties(true, "http://ai", "t", Duration.ofSeconds(2), Duration.ofMinutes(5),
                Duration.ofMinutes(10), 48, ZoneId.of("America/Bogota"));
        feed = new LearningFeed(aiClient, ai, new AiLearningProperties(true, Duration.ofSeconds(10)));
    }

    @Test
    void sendsReadingsInBatchesWithTheLocalHour() {
        feed.onReading(reading(CROP, 0));
        feed.onReading(reading(CROP, 1));

        feed.flush();

        ArgumentCaptor<LearningBatch> batch = ArgumentCaptor.forClass(LearningBatch.class);
        verify(aiClient).learn(batch.capture());
        assertThat(batch.getValue().readings()).hasSize(2);
        assertThat(batch.getValue().readings().getFirst().localHour()).isEqualTo(12);
        assertThat(batch.getValue().readings().getFirst().cropType()).isEqualTo("LETTUCE");
        assertThat(feed.pendingCount()).isZero();
    }

    @Test
    void keepsTheReadingsWhenTheAiIsDown() {
        doThrow(ApiException.unavailable("caída")).when(aiClient).learn(any());
        feed.onReading(reading(CROP, 0));

        feed.flush();

        assertThat(feed.pendingCount()).isEqualTo(1);
        feed.flush();
        verify(aiClient, times(2)).learn(any());
    }

    @Test
    void largeQueuesGoOutInSeveralBatches() {
        for (int i = 0; i < LearningFeed.MAX_BATCH + 5; i++) {
            feed.onReading(reading(CROP, i));
        }
        feed.flush();
        verify(aiClient, times(2)).learn(any());
    }

    @Test
    void deletedCropsAreForgotten() {
        feed.onReading(reading(CROP, 0));
        feed.onReading(reading(OTHER, 0));

        feed.onCropDeleted(new CropDeletedEvent(CROP, "dueño"));

        assertThat(feed.pendingCount()).isEqualTo(1);
        verify(aiClient).forget(CROP);
    }

    @Test
    void virtualCropsDoNotTeachTheModels() {
        feed.onReading(reading(OTHER, 0, CropKind.VIRTUAL));
        feed.onReading(reading(CROP, 0));
        feed.flush();

        ArgumentCaptor<LearningBatch> sent = ArgumentCaptor.forClass(LearningBatch.class);
        verify(aiClient).learn(sent.capture());
        assertThat(sent.getValue().readings()).extracting("cropId").containsExactly(CROP);
    }

    @Test
    void staysQuietWhenLearningIsOff() {
        feed = new LearningFeed(aiClient, new AiProperties(true, "http://ai", "t", Duration.ofSeconds(2),
                Duration.ofMinutes(5), Duration.ofMinutes(10), 48, ZoneId.of("UTC")),
                new AiLearningProperties(false, null));
        feed.onReading(reading(CROP, 0));
        feed.flush();
        verify(aiClient, never()).learn(any());
    }
}
