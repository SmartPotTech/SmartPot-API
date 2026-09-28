package app.smartpot.api.channels.model.dto;

import app.smartpot.api.channels.model.entity.ChannelType;
import app.smartpot.api.channels.model.entity.CropChannel;
import app.smartpot.api.notifications.model.entity.NotificationType;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Avisos de un cultivo por un canal. available: el servidor ofrece el canal; linked: el dueño vinculó su chat. La PWA
 * solo muestra la sección del canal cuando ambos son true.
 */
public record CropChannelResponse(ChannelType type, String name, boolean available, boolean linked, boolean enabled,
                                  Set<NotificationType> events, CropChannel.Delivery delivery, int digestHours,
                                  String dailySummaryAt, List<RecipientResponse> recipients) {

    public record RecipientResponse(String id, String displayName, Instant addedAt) {
    }
}
