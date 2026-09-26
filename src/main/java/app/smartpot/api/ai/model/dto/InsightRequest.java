package app.smartpot.api.ai.model.dto;

import app.smartpot.api.readings.model.entity.Measures;

import java.util.List;

/**
 * Lo que recibe el servicio de IA: la lectura actual, el historial reciente (del más viejo al más nuevo)
 * y los actuadores disponibles para que el agente solo proponga acciones ejecutables.
 */
public record InsightRequest(String cropType, Measures measures, List<Measures> history, List<String> actuators) {
}
