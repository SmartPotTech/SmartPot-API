package app.smartpot.api.ai.model.dto;

import java.util.List;

/**
 * Análisis de todos los cultivos: ranking por salud, problemas compartidos del entorno,
 * grupos por condiciones similares y acciones del agente reunidas por actuador.
 */
public record FleetResponse(
        Double averageHealth,
        List<CropResult> crops,
        List<SharedIssue> sharedIssues,
        List<Group> groups,
        List<Action> actions,
        String summary
) {

    public record CropResult(String id, String name, String cropType, Integer rank, InsightResponse.Health health,
                             List<String> issues) {
    }

    public record SharedIssue(String parameter, String status, List<String> cropIds, double share, String message) {
    }

    public record Group(String label, List<String> cropIds, String description) {
    }

    public record Action(String actuator, String action, Integer durationSeconds, List<String> cropIds, String reason) {
    }
}
