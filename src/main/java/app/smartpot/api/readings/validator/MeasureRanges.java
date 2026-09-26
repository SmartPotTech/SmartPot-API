package app.smartpot.api.readings.validator;

import app.smartpot.api.readings.model.entity.Measures;

/**
 * Rangos físicos admitidos. Un valor fuera de rango suele ser un sensor dañado o mal calibrado.
 */
public final class MeasureRanges {

    private MeasureRanges() {
    }

    public static void validate(Measures measures) {
        if (measures == null || measures.isEmpty()) {
            throw new IllegalArgumentException("La lectura no trae ningún valor");
        }
        check("temperatura", measures.getTemperature(), -20, 60);
        check("humedad", measures.getHumidity(), 0, 100);
        check("luz", measures.getBrightness(), 0, 200_000);
        check("pH", measures.getPh(), 0, 14);
        check("TDS", measures.getTds(), 0, 10_000);
        check("presión", measures.getAtmosphere(), 300, 1_100);
        check("humedad del sustrato", measures.getSoilMoisture(), 0, 100);
    }

    private static void check(String name, Double value, double min, double max) {
        if (value == null) {
            return;
        }
        if (value.isNaN() || value.isInfinite() || value < min || value > max) {
            throw new IllegalArgumentException("El valor de " + name + " (" + value + ") está fuera del rango "
                    + min + " a " + max);
        }
    }
}
