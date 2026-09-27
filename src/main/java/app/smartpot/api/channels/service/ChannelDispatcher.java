package app.smartpot.api.channels.service;

import app.smartpot.api.channels.model.entity.ChannelLink;
import app.smartpot.api.config.SmartPotProperties;
import app.smartpot.api.notifications.model.entity.Notification;
import app.smartpot.api.notifications.model.event.NotificationCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Reenvía cada notificación de la PWA a los canales que la persona vinculó y a los tipos que eligió.
 * Corre aparte del flujo que la creó: un canal lento o caído no frena la lectura ni el comando.
 */
@Slf4j
@Component
public class ChannelDispatcher {

    private final ChannelService channelService;
    private final String webBaseUrl;

    public ChannelDispatcher(ChannelService channelService, SmartPotProperties properties) {
        this.channelService = channelService;
        this.webBaseUrl = properties.webBaseUrl().replaceAll("/+$", "");
    }

    @Async
    @EventListener
    public void onNotification(NotificationCreatedEvent event) {
        Notification notification = event.notification();
        for (ChannelLink link : channelService.activeLinks(notification.getUserId())) {
            if (link.getEvents() == null || !link.getEvents().contains(notification.getType())) {
                continue;
            }
            NotificationChannel channel = channelService.channel(link.getType());
            if (channel == null || !channel.isAvailable()) {
                continue;
            }
            String url = notification.getCropId() == null ? webBaseUrl + "/app"
                    : webBaseUrl + "/app/crops/" + notification.getCropId();
            try {
                channel.send(link, new ChannelMessage(notification.getType(), notification.getTitle(),
                        notification.getMessage(), url));
                channelService.delivered(link);
            } catch (ChannelDeliveryException ex) {
                log.warn("No se entregó la notificación por {}: {}", link.getType(), ex.getMessage());
                channelService.failed(link, ex.isPermanent());
            }
        }
    }
}
