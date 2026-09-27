package app.smartpot.api.ai.model.dto;

import java.util.List;

public record LearningBatch(List<LearningReading> readings) {
}
