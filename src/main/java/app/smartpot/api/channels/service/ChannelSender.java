package app.smartpot.api.channels.service;

import app.smartpot.api.channels.model.entity.ChannelLink;
import app.smartpot.api.channels.model.entity.CropChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Entrega un mensaje al chat del dueño y a los chats con los que comparte el cultivo. Registra entregas y fallos: el
 * vínculo del dueño se pausa tras varios fallos y un chat compartido que bloqueó al bot deja de recibir avisos.
 */
@Slf4j
@Component
public class ChannelSender {

    private final ChannelService channelService;
    private final CropChannelService cropChannelService;

    public ChannelSender(ChannelService channelService, CropChannelService cropChannelService) {
        this.channelService = channelService;
        this.cropChannelService = cropChannelService;
    }

    public void toOwner(NotificationChannel channel, ChannelLink link, ChannelMessage message) {
        try {
            channel.send(link.getAddress(), message);
            channelService.delivered(link);
        } catch (ChannelDeliveryException ex) {
            log.warn("No se entregó la notificación por {}: {}", link.getType(), ex.getMessage());
            channelService.failed(link, ex.isPermanent());
        }
    }

    public void toRecipients(NotificationChannel channel, CropChannel settings, ChannelMessage message) {
        for (CropChannel.Recipient recipient : List.copyOf(settings.getRecipients())) {
            try {
                channel.send(recipient.address(), message);
            } catch (ChannelDeliveryException ex) {
                log.warn("No se entregó un aviso compartido por {}: {}", settings.getType(), ex.getMessage());
                if (ex.isPermanent()) {
                    cropChannelService.removeAddress(settings, recipient.address());
                }
            }
        }
    }
}
