package app.smartpot.api.mqtt.config;

import app.smartpot.api.mqtt.service.DisabledMqttGateway;
import app.smartpot.api.mqtt.service.MqttGateway;
import app.smartpot.api.mqtt.service.MqttTopicResolver;
import app.smartpot.api.mqtt.service.PahoMqttGateway;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class MqttConfig {

    @Bean
    public MqttGateway mqttGateway(MqttProperties properties, MqttTopicResolver topics,
                                   ApplicationEventPublisher publisher) throws MqttException {
        if (!properties.enabled()) {
            log.info("MQTT deshabilitado: la API no recibirá telemetría ni enviará comandos");
            return new DisabledMqttGateway();
        }
        return new PahoMqttGateway(properties, topics, publisher);
    }
}
