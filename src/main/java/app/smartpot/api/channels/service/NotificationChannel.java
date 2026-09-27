package app.smartpot.api.channels.service;

import app.smartpot.api.channels.model.entity.ChannelLink;
import app.smartpot.api.channels.model.entity.ChannelType;

/**
 * Contrato de un canal externo de notificación. Para sumar un canal (WhatsApp, correo, Slack…) basta con
 * una nueva implementación registrada como bean y su valor en {@link ChannelType}.
 */
public interface NotificationChannel {

    ChannelType type();

    /** El servidor tiene el canal configurado (por ejemplo, el token del bot). */
    boolean isAvailable();

    /** Nombre visible y dato público para vincularse (usuario del bot), o null si no aplica. */
    String displayName();

    String handle();

    /** Enlace que abre el canal con el código de vinculación. */
    String linkUrl(String code);

    /** Envía el mensaje; lanza {@link ChannelDeliveryException} si el destino lo rechaza. */
    void send(ChannelLink link, ChannelMessage message);
}
