package app.smartpot.api.ai.model.dto;

import app.smartpot.api.readings.model.entity.Measures;

import java.util.List;

/**
 * Todos los cultivos de una cuenta con su última lectura y sus actuadores, para el análisis de flota.
 */
public record FleetRequest(List<Crop> crops, Integer localHour) {

    public record Crop(String id, String name, String cropType, Measures measures, List<String> actuators) {
    }
}
