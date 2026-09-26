package app.smartpot.api.mqtt.service;

import app.smartpot.api.commands.service.CommandService;
import app.smartpot.api.crops.service.CropService;
import app.smartpot.api.mqtt.model.MqttMessageEvent;
import app.smartpot.api.readings.service.ReadingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Enruta cada mensaje del broker según su tópico. Un mensaje inválido se registra y se descarta:
 * nunca debe tumbar la suscripción.
 */
@Slf4j
@Component
public class MqttMessageHandler {

    private final MqttTopicResolver topics;
    private final TelemetryParser parser;
    private final ReadingService readingService;
    private final CommandService commandService;
    private final CropService cropService;
    private final DeviceProvisioner provisioner;

    public MqttMessageHandler(MqttTopicResolver topics, TelemetryParser parser, ReadingService readingService,
                              CommandService commandService, CropService cropService, DeviceProvisioner provisioner) {
        this.topics = topics;
        this.parser = parser;
        this.readingService = readingService;
        this.commandService = commandService;
        this.cropService = cropService;
        this.provisioner = provisioner;
    }

    @EventListener
    public void onMessage(MqttMessageEvent event) {
        String topic = event.topic();
        try {
            if (MqttTopicResolver.CONTROL_RESPONSE_TOPIC.equals(topic)) {
                provisioner.onControlResponse(event.payload());
                return;
            }
            topics.parse(topic).ifPresentOrElse(
                    parsed -> route(parsed, event.payload()),
                    () -> log.debug("Tópico ignorado: {}", topic));
        } catch (RuntimeException ex) {
            log.warn("Mensaje descartado de {}: {}", topic, ex.getMessage());
        }
    }

    private void route(MqttTopicResolver.ParsedTopic parsed, String payload) {
        switch (parsed.kind()) {
            case TELEMETRY -> readingService.recordFromDevice(parsed.cropId(), parser.parseTelemetry(payload));
            case COMMAND_ACK -> commandService.acknowledge(parsed.cropId(), parser.parseAck(payload));
            case STATUS -> cropService.updateDeviceStatus(parsed.cropId(), "online".equalsIgnoreCase(payload.trim()));
        }
    }
}
