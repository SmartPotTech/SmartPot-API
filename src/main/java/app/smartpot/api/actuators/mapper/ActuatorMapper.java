package app.smartpot.api.actuators.mapper;

import app.smartpot.api.actuators.model.dto.ActuatorResponse;
import app.smartpot.api.actuators.model.entity.Actuator;

import java.time.Instant;

public final class ActuatorMapper {

    private ActuatorMapper() {
    }

    public static ActuatorResponse toResponse(Actuator actuator, Instant now) {
        boolean running = actuator.isRunning(now);
        return new ActuatorResponse(actuator.getId(), actuator.getCropId(), actuator.getType().name(),
                actuator.isActive(), running, running && !actuator.isActive() ? actuator.getRunningUntil() : null,
                actuator.getLastChangedAt());
    }
}
