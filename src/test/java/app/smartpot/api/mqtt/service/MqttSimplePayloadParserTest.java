package app.smartpot.api.mqtt.service;

import app.smartpot.api.mqtt.model.SimpleAckMessage;
import app.smartpot.api.mqtt.model.SimpleReadingMessage;
import app.smartpot.api.records.model.dto.MeasuresDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttSimplePayloadParserTest {
    private final MqttSimplePayloadParser parser = new MqttSimplePayloadParser();

    // ---- Command payloads (unchanged) ----

    @Test
    void validatesSimpleCommandPayload() {
        parser.validateCommandPayload("activate:water_pump");
        parser.validateCommandPayload("disable:lights");
        parser.validateCommandPayload("activate:humidifier");
    }

    @Test
    void rejectsInvalidCommandPayloads() {
        assertThrows(IllegalArgumentException.class, () -> parser.validateCommandPayload("activate"));
        assertThrows(IllegalArgumentException.class, () -> parser.validateCommandPayload("water_pump:activate"));
        assertThrows(IllegalArgumentException.class, () -> parser.validateCommandPayload("open:water_pump"));
    }

    // ---- ACK payloads (unchanged) ----

    @Test
    void parsesAckPayloads() {
        SimpleAckMessage executed = parser.parseAck("executed:ok");
        SimpleAckMessage failed = parser.parseAck("failed:pump_timeout");

        assertTrue(executed.successful());
        assertEquals("ok", executed.response());
        assertFalse(failed.successful());
        assertEquals("pump_timeout", failed.response());
    }

    // ---- Consolidated readings (ESP32 gateway) ----

    @Test
    void parsesConsolidatedReadingWithAllSensors() {
        String json = """
                {
                  "bme280": {"temp": 24.5, "hum": 60, "press": 1013.2},
                  "lux": {"lux": 700},
                  "ph": {"ph": 6.8},
                  "tds": {"tds": 410}
                }
                """;

        SimpleReadingMessage reading = parser.parseConsolidatedReading(json);
        MeasuresDTO measures = reading.measures();

        assertNotNull(measures);
        assertEquals("24.5", measures.getTemperature());
        assertEquals("60", measures.getHumidity());
        assertEquals("1013.2", measures.getAtmosphere());
        assertEquals("700", measures.getBrightness());
        assertEquals("6.8", measures.getPh());
        assertEquals("410", measures.getTds());
    }

    @Test
    void parsesConsolidatedReadingSkippingNullSensors() {
        String json = """
                {
                  "bme280": null,
                  "lux": {"lux": 700},
                  "ph": {"ph": 6.8},
                  "tds": {"tds": 410}
                }
                """;

        SimpleReadingMessage reading = parser.parseConsolidatedReading(json);
        MeasuresDTO measures = reading.measures();

        assertNotNull(measures);
        assertNull(measures.getTemperature());
        assertNull(measures.getHumidity());
        assertNull(measures.getAtmosphere());
        assertEquals("700", measures.getBrightness());
        assertEquals("6.8", measures.getPh());
        assertEquals("410", measures.getTds());
    }

    @Test
    void rejectsInvalidConsolidatedPayload() {
        assertThrows(IllegalArgumentException.class, () -> parser.parseConsolidatedReading("not-json"));
    }

    // ---- Individual sensor readings ----

    @Test
    void parsesIndividualBme280Reading() {
        String json = """
                {"temp": 24.5, "hum": 60, "press": 1013.2}
                """;
        SimpleReadingMessage reading = parser.parseReading(json, MqttTopicConstants.SENSOR_BME280);
        MeasuresDTO measures = reading.measures();

        assertEquals("24.5", measures.getTemperature());
        assertEquals("60", measures.getHumidity());
        assertEquals("1013.2", measures.getAtmosphere());
    }

    @Test
    void parsesIndividualLuxReading() {
        String json = """
                {"lux": 700}
                """;
        SimpleReadingMessage reading = parser.parseReading(json, MqttTopicConstants.SENSOR_LUX);
        MeasuresDTO measures = reading.measures();

        assertEquals("700", measures.getBrightness());
    }

    @Test
    void parsesIndividualPhReading() {
        String json = """
                {"ph": 6.8}
                """;
        SimpleReadingMessage reading = parser.parseReading(json, MqttTopicConstants.SENSOR_PH);
        MeasuresDTO measures = reading.measures();

        assertEquals("6.8", measures.getPh());
    }

    @Test
    void parsesIndividualTdsReading() {
        String json = """
                {"tds": 410}
                """;
        SimpleReadingMessage reading = parser.parseReading(json, MqttTopicConstants.SENSOR_TDS);
        MeasuresDTO measures = reading.measures();

        assertEquals("410", measures.getTds());
    }

    @Test
    void rejectsUnsupportedSensorType() {
        assertThrows(IllegalArgumentException.class, () -> parser.parseReading("{}", "unknown"));
    }
}
