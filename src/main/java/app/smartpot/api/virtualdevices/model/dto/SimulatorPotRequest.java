package app.smartpot.api.virtualdevices.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/** Lo que el simulador necesita para actuar como el dispositivo del cultivo, clave incluida. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SimulatorPotRequest(String key, String cropType, String mode, Map<String, Double> manual,
                                  SimulatorPot.Location location, int intervalSeconds, String setting,
                                  String exposure) {
}
