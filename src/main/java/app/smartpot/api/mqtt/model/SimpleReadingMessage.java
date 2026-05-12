package app.smartpot.api.mqtt.model;

import app.smartpot.api.records.model.dto.MeasuresDTO;

public record SimpleReadingMessage(MeasuresDTO measures) {

}
