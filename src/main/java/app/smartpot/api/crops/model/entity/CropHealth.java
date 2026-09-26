package app.smartpot.api.crops.model.entity;

import java.time.Instant;

/**
 * Última evaluación del asistente de IA: índice difuso de 0 a 100 con su etiqueta.
 */
public record CropHealth(double index, String level, String label, Instant evaluatedAt) {
}
