package app.smartpot.api.readings.model;

import app.smartpot.api.crops.model.entity.Crop;
import app.smartpot.api.readings.model.entity.Reading;

public record ReadingRecordedEvent(Crop crop, Reading reading) {
}
