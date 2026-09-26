package app.smartpot.api.ai.service;

import app.smartpot.api.actuators.service.ActuatorService;
import app.smartpot.api.ai.config.AiProperties;
import app.smartpot.api.ai.model.dto.InsightRequest;
import app.smartpot.api.ai.model.dto.InsightResponse;
import app.smartpot.api.crops.model.entity.Crop;
import app.smartpot.api.crops.model.entity.CropType;
import app.smartpot.api.crops.service.CropService;
import app.smartpot.api.readings.model.entity.Measures;
import app.smartpot.api.readings.model.entity.Reading;
import app.smartpot.api.readings.service.ReadingService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InsightServiceTest {

    private static final String CROP = "6718f0a1b2c3d4e5f6a7b8c9";

    private final AiClient aiClient = mock(AiClient.class);
    private final ReadingService readingService = mock(ReadingService.class);
    private final ActuatorService actuatorService = mock(ActuatorService.class);
    private final InsightService service = new InsightService(aiClient, mock(CropService.class), readingService,
            actuatorService, Clock.fixed(Instant.parse("2026-09-26T12:00:00Z"), ZoneOffset.UTC),
            new AiProperties(true, "http://ai", "t", Duration.ofSeconds(2), Duration.ofMinutes(5),
                    Duration.ofMinutes(10), 48, ZoneId.of("America/Bogota")));

    @Test
    void sendsTheLocalHourOfTheReading() {
        when(readingService.recent(anyString(), anyInt())).thenReturn(List.of());
        when(actuatorService.listForCrop(anyString())).thenReturn(List.of());
        when(aiClient.insights(any())).thenReturn(new InsightResponse("TOMATO", null, List.of(), List.of(),
                List.of(), List.of(), "", null));
        Crop crop = Crop.builder().id(CROP).type(CropType.TOMATO).build();
        Reading reading = Reading.builder().cropId(CROP).measuredAt(Instant.parse("2026-09-26T03:30:00Z"))
                .measures(Measures.builder().brightness(20.0).build()).build();

        service.evaluate(crop, reading);

        ArgumentCaptor<InsightRequest> request = ArgumentCaptor.forClass(InsightRequest.class);
        verify(aiClient).insights(request.capture());
        assertThat(request.getValue().localHour()).isEqualTo(22);
        assertThat(request.getValue().cropType()).isEqualTo("TOMATO");
    }
}
