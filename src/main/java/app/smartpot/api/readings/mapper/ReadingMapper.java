package app.smartpot.api.readings.mapper;

import app.smartpot.api.readings.model.dto.ReadingResponse;
import app.smartpot.api.readings.model.entity.Reading;

public final class ReadingMapper {

    private ReadingMapper() {
    }

    public static ReadingResponse toResponse(Reading reading) {
        if (reading == null) {
            return null;
        }
        return new ReadingResponse(reading.getId(), reading.getCropId(), reading.getMeasuredAt(), reading.getMeasures(),
                reading.getSource() == null ? null : reading.getSource().name());
    }
}
