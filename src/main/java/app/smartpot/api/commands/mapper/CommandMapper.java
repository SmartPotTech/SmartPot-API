package app.smartpot.api.commands.mapper;

import app.smartpot.api.commands.model.dto.CommandResponse;
import app.smartpot.api.commands.model.entity.Command;

public final class CommandMapper {

    private CommandMapper() {
    }

    public static CommandResponse toResponse(Command command) {
        return new CommandResponse(command.getId(), command.getCropId(), command.getActuatorId(),
                command.getActuatorType().name(), command.getAction().name(), command.getDurationSeconds(),
                command.getStatus().name(), command.getSource().name(), command.getReason(), command.getMessage(),
                command.getCreatedAt(), command.getSentAt(), command.getCompletedAt());
    }
}
