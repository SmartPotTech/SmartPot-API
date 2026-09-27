package app.smartpot.api.channels.model.dto;

import app.smartpot.api.channels.model.entity.ChannelLink;
import app.smartpot.api.channels.model.entity.ChannelType;
import app.smartpot.api.notifications.model.entity.NotificationType;

import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;

public record ChannelLinkResponse(String id, ChannelType type, String displayName, boolean enabled,
                                  Set<NotificationType> events, Instant linkedAt, Instant lastDeliveredAt) {

    public static ChannelLinkResponse of(ChannelLink link) {
        return new ChannelLinkResponse(link.getId(), link.getType(), link.getDisplayName(), link.isEnabled(),
                link.getEvents() == null ? Set.of() : new TreeSet<>(link.getEvents()), link.getLinkedAt(),
                link.getLastDeliveredAt());
    }
}
