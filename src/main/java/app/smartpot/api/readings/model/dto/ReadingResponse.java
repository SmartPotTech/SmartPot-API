package app.smartpot.api.readings.model.dto;

import app.smartpot.api.readings.model.entity.Measures;

import java.time.Instant;

public record ReadingResponse(String id, String cropId, Instant measuredAt, Measures measures, String source) {
}
