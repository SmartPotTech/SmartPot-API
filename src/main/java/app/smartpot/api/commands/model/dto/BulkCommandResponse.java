package app.smartpot.api.commands.model.dto;

import java.util.List;

/**
 * Resultado por cultivo: SENT (enviado al dispositivo), FAILED (el broker no respondió)
 * o SKIPPED (no tiene ese actuador o ya hay un comando en curso).
 */
public record BulkCommandResponse(int sent, int skipped, int failed, List<Result> results) {

    public static BulkCommandResponse of(List<Result> results) {
        int sent = (int) results.stream().filter(r -> "SENT".equals(r.status())).count();
        int skipped = (int) results.stream().filter(r -> "SKIPPED".equals(r.status())).count();
        return new BulkCommandResponse(sent, skipped, results.size() - sent - skipped, results);
    }

    public record Result(String cropId, String cropName, String status, String commandId, String message) {
    }
}
