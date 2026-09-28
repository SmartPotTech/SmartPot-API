package app.smartpot.api.ai.model.dto;

import app.smartpot.api.readings.model.entity.Measures;

import java.time.Instant;

/**
 * Lectura real que el servicio de IA guarda (con el id seudonimizado) para aprender.
 */
public record LearningReading(String cropId, String cropType, Instant measuredAt, Integer localHour,
                              Measures measures) {
}
