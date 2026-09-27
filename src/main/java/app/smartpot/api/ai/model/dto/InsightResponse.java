package app.smartpot.api.ai.model.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Evaluación del asistente: índice difuso de salud, diagnóstico por variable del sistema experto,
 * conclusiones encadenadas, predicciones de los modelos, pronósticos de tendencia y acciones propuestas por el agente.
 */
public record InsightResponse(
        String cropType,
        Health health,
        List<Diagnosis> diagnosis,
        List<Conclusion> conclusions,
        List<Prediction> predictions,
        List<Action> actions,
        List<Forecast> forecasts,
        String summary,
        Instant evaluatedAt
) {

    public InsightResponse withEvaluatedAt(Instant instant) {
        return new InsightResponse(cropType, health, diagnosis, conclusions, predictions, actions, forecasts, summary,
                instant);
    }

    /** byParameter: salud de 0 a 100 de cada variable; explica de dónde sale el índice. */
    public record Health(double index, String level, String label, Map<String, Double> byParameter) {
    }

    public record Diagnosis(String parameter, Double value, String status, String severity, String message,
                            String recommendation) {
    }

    public record Conclusion(String rule, String title, String message, double certainty) {
    }

    public record Prediction(String name, String label, double probability, String model) {
    }

    public record Action(String actuator, String action, Integer durationSeconds, String reason) {
    }

    /** Tendencia de una variable: trend RISING, FALLING o STABLE; limit MIN o MAX si saldrá de su rango. */
    public record Forecast(String parameter, double current, double slopePerHour, double expectedIn3h,
                           String trend, Double hoursToLimit, String limit, double confidence, String message) {
    }
}
