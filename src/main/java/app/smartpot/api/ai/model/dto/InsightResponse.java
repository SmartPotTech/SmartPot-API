package app.smartpot.api.ai.model.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Evaluación del asistente: índice difuso de salud, diagnóstico por variable del sistema experto,
 * conclusiones encadenadas, predicciones de los modelos, pronósticos de tendencia, lo aprendido de las
 * lecturas reales y acciones propuestas por el agente.
 */
public record InsightResponse(
        String cropType,
        Health health,
        List<Diagnosis> diagnosis,
        List<Conclusion> conclusions,
        List<Prediction> predictions,
        List<Action> actions,
        List<Forecast> forecasts,
        Learning learning,
        String summary,
        Instant evaluatedAt
) {

    public InsightResponse withEvaluatedAt(Instant instant) {
        return new InsightResponse(cropType, health, diagnosis, conclusions, predictions, actions, forecasts, learning,
                summary, instant);
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

    /**
     * Aprendizaje continuo: source LEARNED si la especie ya tiene modelos entrenados con lecturas reales,
     * BASE si todavía no; state es el estado de operación (K-Means) y anomaly el Isolation Forest de la especie.
     */
    public record Learning(String source, int readings, Instant trainedAt, String message,
                           List<LearnedPrediction> predictions, LearnedMoisture moisture, OperatingState state,
                           LearnedAnomaly anomaly) {
    }

    public record LearnedPrediction(String name, String label, double probability, String model, String metric,
                                    Double score) {
    }

    public record LearnedMoisture(double expectedIn1h, String model, Double mae) {
    }

    public record OperatingState(String label, String description, double share) {
    }

    public record LearnedAnomaly(double score, boolean unusual) {
    }
}
