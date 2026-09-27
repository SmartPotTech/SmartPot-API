package app.smartpot.api.channels.model.dto;

import app.smartpot.api.notifications.model.entity.NotificationType;
import jakarta.validation.constraints.Size;

import java.util.Set;

/** Campos opcionales: solo cambia lo que llega. */
public record ChannelLinkUpdateRequest(Boolean enabled, @Size(max = 5) Set<NotificationType> events) {
}
