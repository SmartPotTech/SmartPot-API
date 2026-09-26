package app.smartpot.api.mqtt.service;

public interface MqttGateway {

    boolean isEnabled();

    boolean isConnected();

    /**
     * Publica sin bloquear más de unos segundos. Devuelve false si el broker no aceptó el mensaje.
     */
    boolean publish(String topic, String payload, int qos, boolean retained);
}
