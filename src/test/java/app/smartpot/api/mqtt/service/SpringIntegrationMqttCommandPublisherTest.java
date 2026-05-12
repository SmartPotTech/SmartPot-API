package app.smartpot.api.mqtt.service;

import app.smartpot.api.actuators.model.entity.ActuatorType;
import app.smartpot.api.commands.model.dto.CommandDTO;
import app.smartpot.api.mqtt.config.MqttProperties;
import org.junit.jupiter.api.Test;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SpringIntegrationMqttCommandPublisherTest {
    @Test
    void publishesCommandPayloadToActuatorTopic() {
        RecordingMessageChannel channel = new RecordingMessageChannel();
        MqttProperties properties = new MqttProperties();
        properties.setTopicPrefix("smartpot");
        properties.setTopicVersion("v1");
        properties.setCommandQos(1);

        SpringIntegrationMqttCommandPublisher publisher = new SpringIntegrationMqttCommandPublisher(
                channel,
                properties,
                new MqttTopicResolver(properties),
                new MqttCommandPayloadMapper(),
                new MqttSimplePayloadParser()
        );

        CommandDTO command = new CommandDTO();
        command.setId("507f1f77bcf86cd799439011");
        command.setCommandType("ACTIVATE_WATER_PUMP");
        command.setCrop("507f1f77bcf86cd799439012");
        command.setActuator("507f1f77bcf86cd799439013");

        publisher.publish(command, ActuatorType.WATER_PUMP);

        assertNotNull(channel.lastMessage);
        assertEquals("activate:water_pump", channel.lastMessage.getPayload());
        assertEquals("smartpot/v1/507f1f77bcf86cd799439012/actuators/507f1f77bcf86cd799439013/commands",
                channel.lastMessage.getHeaders().get(MqttHeaders.TOPIC));
        assertEquals(1, channel.lastMessage.getHeaders().get(MqttHeaders.QOS));
    }

    private static class RecordingMessageChannel implements MessageChannel {
        private Message<?> lastMessage;

        @Override
        public boolean send(Message<?> message, long timeout) {
            this.lastMessage = message;
            return true;
        }
    }
}
