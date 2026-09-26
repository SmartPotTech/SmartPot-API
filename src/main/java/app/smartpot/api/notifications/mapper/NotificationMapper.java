package app.smartpot.api.notifications.mapper;

import app.smartpot.api.notifications.model.dto.NotificationResponse;
import app.smartpot.api.notifications.model.entity.Notification;

public final class NotificationMapper {

    private NotificationMapper() {
    }

    public static NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getCropId(), notification.getType().name(),
                notification.getTitle(), notification.getMessage(), notification.isRead(), notification.getCreatedAt());
    }
}
