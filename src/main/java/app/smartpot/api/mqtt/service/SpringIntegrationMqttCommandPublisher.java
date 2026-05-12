package app.smartpot.api.mqtt.service;

import app.smartpot.api.actuators.model.entity.ActuatorType;
import app.smartpot.api.commands.model.dto.CommandDTO;
import app.smartpot.api.mqtt.config.MqttProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "application.mqtt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SpringIntegrationMqttCommandPublisher implements MqttCommandPublisher {
    private final MessageChannel mqttOutboundChannel;
    private final MqttProperties mqttProperties;
    private final MqttTopicResolver mqttTopicResolver;
    private final MqttCommandPayloadMapper commandPayloadMapper;
    private final MqttSimplePayloadParser payloadParser;

    public SpringIntegrationMqttCommandPublisher(
            @Qualifier("mqttOutboundChannel") MessageChannel mqttOutboundChannel,
            MqttProperties mqttProperties,
            MqttTopicResolver mqttTopicResolver,
            MqttCommandPayloadMapper commandPayloadMapper,
            MqttSimplePayloadParser payloadParser) {
        this.mqttOutboundChannel = mqttOutboundChannel;
        this.mqttProperties = mqttProperties;
        this.mqttTopicResolver = mqttTopicResolver;
        this.commandPayloadMapper = commandPayloadMapper;
        this.payloadParser = payloadParser;
    }

    @Override
    public void publish(CommandDTO command, ActuatorType actuatorType) {
        String topic = mqttTopicResolver.commandTopic(command.getCrop(), command.getActuator());
        String payload = commandPayloadMapper.toPayload(command.getCommandType(), actuatorType);
        payloadParser.validateCommandPayload(payload);

        Message<String> message = MessageBuilder.withPayload(payload)
                .setHeader(MqttHeaders.TOPIC, topic)
                .setHeader(MqttHeaders.QOS, mqttProperties.getCommandQos())
                .setHeader(MqttHeaders.RETAINED, false)
                .build();

        if (!mqttOutboundChannel.send(message)) {
            throw new IllegalStateException("Could not publish MQTT command " + command.getId());
        }
        log.info("Published MQTT command {} to topic {}", command.getId(), topic);
    }
}
