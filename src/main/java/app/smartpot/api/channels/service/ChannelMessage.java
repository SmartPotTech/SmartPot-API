package app.smartpot.api.channels.service;

import app.smartpot.api.notifications.model.entity.NotificationType;

/** Mensaje independiente del canal: cada canal decide cómo darle formato. */
public record ChannelMessage(NotificationType type, String title, String body, String url) {
}
