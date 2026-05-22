package app.smartpot.api.mqtt.service;

import app.smartpot.api.commands.model.dto.CommandDTO;
import app.smartpot.api.commands.service.CommandService;
import app.smartpot.api.mqtt.model.SimpleAckMessage;
import app.smartpot.api.mqtt.model.SimpleReadingMessage;
import app.smartpot.api.records.model.dto.RecordDTO;
import app.smartpot.api.records.service.RecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "application.mqtt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MqttInboundMessageHandler {
    private final MqttTopicResolver mqttTopicResolver;
    private final MqttSimplePayloadParser payloadParser;
    private final RecordService recordService;
    private final CommandService commandService;

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handle(Message<?> message) {
        String topic = message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC, String.class);
        String payload = String.valueOf(message.getPayload());
        log.info("Processing MQTT topic {}, {}", topic, payload);

        try {
            if (topic != null && mqttTopicResolver.isSensorTopic(topic)) {
                String sensorType = mqttTopicResolver.sensorTypeFromReadingTopic(topic);
                if (sensorType == null || sensorType.isBlank()) {
                    // Consolidated reading (topic ends with /sensors directly)
                    handleConsolidatedReading(topic, payload);
                } else {
                    // Individual sensor reading (topic ends with /sensors/{sensorType})
                    handleIndividualReading(topic, payload, sensorType);
                }
                return;
            }
            if (topic != null && mqttTopicResolver.isAckTopic(topic)) {
                handleAck(topic, payload);
                return;
            }
            log.warn("Ignoring MQTT message from unsupported topic {}", topic);
        } catch (Exception e) {
            log.warn("Could not process MQTT message from topic {} with payload [{}]: {}", topic, payload, e.getMessage());
            log.debug("MQTT message processing failure", e);
        }
    }

    private void handleConsolidatedReading(String topic, String payload) throws Exception {
        String cropId = mqttTopicResolver.cropIdFromReadingTopic(topic);
        SimpleReadingMessage reading = payloadParser.parseConsolidatedReading(payload);
        storeReading(cropId, reading, topic, "consolidated");
    }

    private void handleIndividualReading(String topic, String payload, String sensorType) throws Exception {
        String cropId = mqttTopicResolver.cropIdFromReadingTopic(topic);
        SimpleReadingMessage reading = payloadParser.parseReading(payload, sensorType);
        storeReading(cropId, reading, topic, sensorType);
    }

    private void storeReading(String cropId, SimpleReadingMessage reading, String topic, String source) throws Exception {
        RecordDTO recordDTO = new RecordDTO();
        recordDTO.setCrop(cropId);
        recordDTO.setMeasures(reading.measures());
        recordService.Createhistory(recordDTO);
        log.info("Stored MQTT {} reading for crop {} from topic {}", source, cropId, topic);
    }

    private void handleAck(String topic, String payload) throws Exception {
        MqttTopicResolver.AckTopic ackTopic = mqttTopicResolver.ackTopicFrom(topic);
        SimpleAckMessage ack = payloadParser.parseAck(payload);
        CommandDTO command = commandService.getCommandById(ackTopic.commandId());

        if (!ackTopic.cropId().equals(command.getCrop()) || !ackTopic.actuatorId().equals(command.getActuator())) {
            throw new IllegalArgumentException("ACK topic does not match command crop or actuator");
        }

        if (ack.successful()) {
            commandService.executeCommand(ackTopic.commandId(), ack.response());
            log.info("MQTT command {} acknowledged as executed", ackTopic.commandId());
            return;
        }

        commandService.failCommand(ackTopic.commandId(), ack.response());
        log.info("MQTT command {} acknowledged as failed", ackTopic.commandId());
    }
}
