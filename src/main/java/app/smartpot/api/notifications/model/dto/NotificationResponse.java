package app.smartpot.api.notifications.model.dto;

import java.time.Instant;

public record NotificationResponse(
        String id,
        String cropId,
        String type,
        String title,
        String message,
        boolean read,
        Instant createdAt
) {
}
