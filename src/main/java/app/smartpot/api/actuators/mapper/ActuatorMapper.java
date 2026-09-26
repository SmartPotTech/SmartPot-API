package app.smartpot.api.actuators.mapper;

import app.smartpot.api.actuators.model.dto.ActuatorResponse;
import app.smartpot.api.actuators.model.entity.Actuator;

public final class ActuatorMapper {

    private ActuatorMapper() {
    }

    public static ActuatorResponse toResponse(Actuator actuator) {
        return new ActuatorResponse(actuator.getId(), actuator.getCropId(), actuator.getType().name(),
                actuator.isActive(), actuator.getLastChangedAt());
    }
}
