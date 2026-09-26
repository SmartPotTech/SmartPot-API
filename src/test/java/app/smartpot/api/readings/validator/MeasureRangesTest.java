package app.smartpot.api.readings.validator;

import app.smartpot.api.readings.model.entity.Measures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MeasureRangesTest {

    @Test
    void acceptsARealisticReading() {
        Measures measures = Measures.builder().temperature(22.0).humidity(65.0).brightness(800.0).ph(6.0)
                .tds(700.0).atmosphere(1012.0).soilMoisture(55.0).build();

        assertThatCode(() -> MeasureRanges.validate(measures)).doesNotThrowAnyException();
    }

    @Test
    void rejectsEmptyReadings() {
        assertThatThrownBy(() -> MeasureRanges.validate(new Measures())).hasMessageContaining("ningún valor");
    }

    @Test
    void rejectsImpossibleValues() {
        assertThatThrownBy(() -> MeasureRanges.validate(Measures.builder().humidity(120.0).build()))
                .hasMessageContaining("humedad");
        assertThatThrownBy(() -> MeasureRanges.validate(Measures.builder().temperature(Double.NaN).build()))
                .hasMessageContaining("temperatura");
    }
}
