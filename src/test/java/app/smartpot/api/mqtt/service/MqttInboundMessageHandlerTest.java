package app.smartpot.api.mqtt.service;

import app.smartpot.api.commands.model.dto.CommandDTO;
import app.smartpot.api.commands.service.CommandService;
import app.smartpot.api.mqtt.config.MqttProperties;
import app.smartpot.api.records.service.RecordService;
import org.junit.jupiter.api.Test;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.support.MessageBuilder;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MqttInboundMessageHandlerTest {

    private static MqttTopicResolver newResolver() {
        MqttProperties props = new MqttProperties();
        props.setTopicPrefix("smartpot");
        props.setTopicVersion("v1");
        return new MqttTopicResolver(props);
    }

    private static MqttInboundMessageHandler newHandler(RecordService recordService, CommandService commandService) {
        return new MqttInboundMessageHandler(
                newResolver(),
                new MqttSimplePayloadParser(),
                recordService,
                commandService
        );
    }

    // ---- Consolidated reading tests ----

    @Test
    void storesConsolidatedReading() throws Exception {
        RecordService recordService = mock(RecordService.class);
        CommandService commandService = mock(CommandService.class);
        MqttInboundMessageHandler handler = newHandler(recordService, commandService);

        handler.handle(MessageBuilder.withPayload("""
                        {"bme280":{"temp":24.5,"hum":60,"press":1013.2},
                         "lux":{"lux":700},"ph":{"ph":6.8},"tds":{"tds":410}}
                        """)
                .setHeader(MqttHeaders.RECEIVED_TOPIC,
                        "smartpot/v1/507f1f77bcf86cd799439012/sensors")
                .build());

        verify(recordService).Createhistory(argThat(record ->
                "507f1f77bcf86cd799439012".equals(record.getCrop())
                        && "24.5".equals(record.getMeasures().getTemperature())
                        && "60".equals(record.getMeasures().getHumidity())
                        && "1013.2".equals(record.getMeasures().getAtmosphere())
                        && "700".equals(record.getMeasures().getBrightness())
                        && "6.8".equals(record.getMeasures().getPh())
                        && "410".equals(record.getMeasures().getTds())
        ));
    }

    @Test
    void storesConsolidatedReadingSkippingNullSensor() throws Exception {
        RecordService recordService = mock(RecordService.class);
        CommandService commandService = mock(CommandService.class);
        MqttInboundMessageHandler handler = newHandler(recordService, commandService);

        handler.handle(MessageBuilder.withPayload("""
                        {"bme280":null,"lux":{"lux":700},"ph":{"ph":6.8},"tds":{"tds":410}}
                        """)
                .setHeader(MqttHeaders.RECEIVED_TOPIC,
                        "smartpot/v1/507f1f77bcf86cd799439012/sensors")
                .build());

        verify(recordService).Createhistory(argThat(record ->
                "507f1f77bcf86cd799439012".equals(record.getCrop())
                        && record.getMeasures().getTemperature() == null
                        && record.getMeasures().getHumidity() == null
                        && record.getMeasures().getAtmosphere() == null
                        && "700".equals(record.getMeasures().getBrightness())
        ));
    }

    @Test
    void storesIndividualBme280Reading() throws Exception {
        RecordService recordService = mock(RecordService.class);
        CommandService commandService = mock(CommandService.class);
        MqttInboundMessageHandler handler = newHandler(recordService, commandService);

        handler.handle(MessageBuilder.withPayload("""
                        {"temp":24.5,"hum":60,"press":1013.2}
                        """)
                .setHeader(MqttHeaders.RECEIVED_TOPIC,
                        "smartpot/v1/507f1f77bcf86cd799439012/sensors/bme280")
                .build());

        verify(recordService).Createhistory(argThat(record ->
                "507f1f77bcf86cd799439012".equals(record.getCrop())
                        && "24.5".equals(record.getMeasures().getTemperature())
                        && "60".equals(record.getMeasures().getHumidity())
                        && "1013.2".equals(record.getMeasures().getAtmosphere())
        ));
    }

    // ---- ACK tests (adapted to v1 topics) ----

    @Test
    void executesCommandWhenAckIsSuccessful() throws Exception {
        RecordService recordService = mock(RecordService.class);
        CommandService commandService = mock(CommandService.class);
        MqttInboundMessageHandler handler = newHandler(recordService, commandService);

        CommandDTO command = new CommandDTO();
        command.setId("507f1f77bcf86cd799439011");
        command.setCrop("507f1f77bcf86cd799439012");
        command.setActuator("507f1f77bcf86cd799439013");
        when(commandService.getCommandById(command.getId())).thenReturn(command);

        handler.handle(MessageBuilder.withPayload("executed:ok")
                .setHeader(MqttHeaders.RECEIVED_TOPIC,
                        "smartpot/v1/507f1f77bcf86cd799439012/actuators/507f1f77bcf86cd799439013/commands/507f1f77bcf86cd799439011/ack")
                .build());

        verify(commandService).executeCommand(command.getId(), "ok");
    }

    @Test
    void ignoresAckWhenTopicDoesNotMatchCommand() throws Exception {
        RecordService recordService = mock(RecordService.class);
        CommandService commandService = mock(CommandService.class);
        MqttInboundMessageHandler handler = newHandler(recordService, commandService);

        CommandDTO command = new CommandDTO();
        command.setId("507f1f77bcf86cd799439011");
        command.setCrop("507f1f77bcf86cd799439012");
        command.setActuator("507f1f77bcf86cd799439013");
        when(commandService.getCommandById(command.getId())).thenReturn(command);

        handler.handle(MessageBuilder.withPayload("executed:ok")
                .setHeader(MqttHeaders.RECEIVED_TOPIC,
                        "smartpot/v1/507f1f77bcf86cd799439012/actuators/507f1f77bcf86cd799439014/commands/507f1f77bcf86cd799439011/ack")
                .build());

        verify(commandService, never()).executeCommand(command.getId(), "ok");
        verify(commandService, never()).failCommand(command.getId(), "ok");
    }
}
