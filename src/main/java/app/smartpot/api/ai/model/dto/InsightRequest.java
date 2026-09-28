package app.smartpot.api.ai.model.dto;

import app.smartpot.api.readings.model.entity.Measures;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Lo que recibe el servicio de IA: la lectura actual, el historial reciente (del más viejo al más nuevo),
 * los actuadores disponibles para que el agente solo proponga acciones ejecutables, la hora local de la lectura
 * (0–23) para respetar el fotoperiodo y, si se conocen, el lugar del cultivo y el clima de afuera.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InsightRequest(String cropType, Measures measures, List<HistoryPoint> history, List<String> actuators,
                             Integer localHour, Place placement, Outside weather) {

    public InsightRequest(String cropType, Measures measures, List<HistoryPoint> history, List<String> actuators,
                          Integer localHour) {
        this(cropType, measures, history, actuators, localHour, null, null);
    }

    /** setting: INDOOR u OUTDOOR; exposure: FULL_SUN, PARTIAL_SUN o SHADE. Cualquiera puede faltar. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Place(String setting, String exposure) {
    }

    /** Clima actual del lugar del cultivo. */
    public record Outside(double temperature, double humidity, double precipitation, double radiation, boolean isDay,
                          String condition) {
    }
}
