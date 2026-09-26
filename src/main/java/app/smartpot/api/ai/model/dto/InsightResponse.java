package app.smartpot.api.ai.model.dto;

import java.time.Instant;
import java.util.List;

/**
 * Evaluación del asistente: índice difuso de salud, diagnóstico por variable del sistema experto,
 * conclusiones encadenadas, predicciones de los modelos y acciones propuestas por el agente.
 */
public record InsightResponse(
        String cropType,
        Health health,
        List<Diagnosis> diagnosis,
        List<Conclusion> conclusions,
        List<Prediction> predictions,
        List<Action> actions,
        String summary,
        Instant evaluatedAt
) {

    public InsightResponse withEvaluatedAt(Instant instant) {
        return new InsightResponse(cropType, health, diagnosis, conclusions, predictions, actions, summary, instant);
    }

    public record Health(double index, String level, String label) {
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
}
