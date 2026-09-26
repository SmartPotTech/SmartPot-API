package app.smartpot.api.commands.model.dto;

import java.time.Instant;

public record CommandResponse(
        String id,
        String cropId,
        String actuatorId,
        String actuatorType,
        String action,
        Integer durationSeconds,
        String status,
        String source,
        String reason,
        String message,
        Instant createdAt,
        Instant sentAt,
        Instant completedAt
) {
}
