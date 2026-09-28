package app.smartpot.api.channels.service;

import app.smartpot.api.channels.model.entity.ChannelLink;
import app.smartpot.api.channels.model.entity.ChannelType;
import app.smartpot.api.channels.model.entity.CropChannel;
import app.smartpot.api.config.SmartPotProperties;
import app.smartpot.api.notifications.model.entity.Notification;
import app.smartpot.api.notifications.model.entity.NotificationType;
import app.smartpot.api.notifications.model.event.NotificationCreatedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * Reenvía cada notificación de la PWA a los canales externos. Las de un cultivo siguen lo que se eligió para ese
 * cultivo (qué avisar, si al instante o en resumen, y con quién se comparte); sin elección, lo del perfil. Corre aparte
 * del flujo que la creó: un canal lento o caído no frena la lectura ni el comando.
 */
@Component
public class ChannelDispatcher {

    private final ChannelService channelService;
    private final CropChannelService cropChannelService;
    private final ChannelSender sender;
    private final String webBaseUrl;

    public ChannelDispatcher(ChannelService channelService, CropChannelService cropChannelService, ChannelSender sender,
                             SmartPotProperties properties) {
        this.channelService = channelService;
        this.cropChannelService = cropChannelService;
        this.sender = sender;
        this.webBaseUrl = properties.webBaseUrl().replaceAll("/+$", "");
    }

    @Async
    @EventListener
    public void onNotification(NotificationCreatedEvent event) {
        Notification notification = event.notification();
        String url = notification.getCropId() == null ? webBaseUrl + "/app"
                : webBaseUrl + "/app/crops/" + notification.getCropId();
        ChannelMessage message = new ChannelMessage(notification.getType(), notification.getTitle(),
                notification.getMessage(), url);
        for (ChannelType type : ChannelType.values()) {
            channelService.available(type, false).ifPresent(channel -> dispatch(channel, notification, message));
        }
    }

    private void dispatch(NotificationChannel channel, Notification notification, ChannelMessage message) {
        ChannelType type = channel.type();
        Optional<ChannelLink> link = channelService.link(notification.getUserId(), type).filter(ChannelLink::isEnabled);
        Optional<CropChannel> crop = notification.getCropId() == null ? Optional.empty()
                : cropChannelService.find(notification.getCropId(), type);
        if (crop.isPresent() && (!crop.get().isEnabled() || crop.get().getDelivery() == CropChannel.Delivery.DIGEST)) {
            return;
        }
        Set<NotificationType> events = crop.map(CropChannel::getEvents)
                .or(() -> link.map(ChannelLink::getEvents))
                .orElse(Set.of());
        if (events == null || !events.contains(notification.getType())) {
            return;
        }
        link.ifPresent(owner -> sender.toOwner(channel, owner, message));
        crop.ifPresent(settings -> sender.toRecipients(channel, settings, message));
    }
}
