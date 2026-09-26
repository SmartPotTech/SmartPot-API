package app.smartpot.api.mqtt.service;

import app.smartpot.api.mqtt.model.CommandAckMessage;
import app.smartpot.api.readings.model.entity.Measures;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelemetryParserTest {

    private final TelemetryParser parser = new TelemetryParser(JsonMapper.builder().build());

    @Test
    void readsNumbersAndNumericStrings() {
        Measures measures = parser.parseTelemetry(
                "{\"temperature\":24.5,\"humidity\":\"61\",\"ph\":6.2,\"tds\":820,\"extra\":true}");

        assertThat(measures.getTemperature()).isEqualTo(24.5);
        assertThat(measures.getHumidity()).isEqualTo(61.0);
        assertThat(measures.getPh()).isEqualTo(6.2);
        assertThat(measures.getTds()).isEqualTo(820.0);
        assertThat(measures.getBrightness()).isNull();
    }

    @Test
    void rejectsValuesOutsideThePhysicalRange() {
        assertThatThrownBy(() -> parser.parseTelemetry("{\"ph\":15}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pH");
    }

    @Test
    void rejectsInvalidPayloads() {
        assertThatThrownBy(() -> parser.parseTelemetry("no es json")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parseTelemetry("[1,2]")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parseTelemetry("{}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parseTelemetry("{\"temperature\":\"caliente\"}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readsCommandAcknowledgements() {
        CommandAckMessage ack = parser.parseAck("{\"id\":\"abc\",\"status\":\"executed\",\"message\":\"Bomba encendida\"}");

        assertThat(ack.id()).isEqualTo("abc");
        assertThat(ack.executed()).isTrue();
        assertThat(ack.message()).isEqualTo("Bomba encendida");
        assertThatThrownBy(() -> parser.parseAck("{\"id\":\"abc\",\"status\":\"MAYBE\"}"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
