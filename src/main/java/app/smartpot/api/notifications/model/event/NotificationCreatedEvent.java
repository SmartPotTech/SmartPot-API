package app.smartpot.api.notifications.model.event;

import app.smartpot.api.notifications.model.entity.Notification;

/**
 * Se creó una notificación en la PWA; los canales externos (Telegram…) la reenvían si la persona lo pidió.
 */
public record NotificationCreatedEvent(Notification notification) {
}
