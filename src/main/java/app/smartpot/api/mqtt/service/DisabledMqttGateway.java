package app.smartpot.api.mqtt.service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DisabledMqttGateway implements MqttGateway {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public boolean isConnected() {
        return false;
    }

    @Override
    public boolean publish(String topic, String payload, int qos, boolean retained) {
        log.debug("MQTT deshabilitado; no se publica en {}", topic);
        return false;
    }
}
