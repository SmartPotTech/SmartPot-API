package app.smartpot.api.ai.model.dto;

import app.smartpot.api.readings.model.entity.Measures;

import java.util.List;

/**
 * Lo que recibe el servicio de IA: la lectura actual, el historial reciente (del más viejo al más nuevo),
 * los actuadores disponibles para que el agente solo proponga acciones ejecutables
 * y la hora local de la lectura (0–23) para respetar el fotoperiodo.
 */
public record InsightRequest(String cropType, Measures measures, List<Measures> history, List<String> actuators,
                             Integer localHour) {
}
