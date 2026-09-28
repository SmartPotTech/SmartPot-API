package app.smartpot.api.channels.service;

/**
 * El canal no pudo entregar el mensaje. permanent indica que reintentar no sirve (chat bloqueado o borrado).
 */
public class ChannelDeliveryException extends RuntimeException {

    private final boolean permanent;

    public ChannelDeliveryException(String message, boolean permanent) {
        super(message);
        this.permanent = permanent;
    }

    public boolean isPermanent() {
        return permanent;
    }
}
