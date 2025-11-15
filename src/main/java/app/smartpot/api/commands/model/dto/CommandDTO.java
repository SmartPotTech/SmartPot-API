package app.smartpot.api.commands.model.dto;

import lombok.Data;
import app.smartpot.api.commands.model.entity.CommandStatus;

import java.io.Serializable;

@Data
public class CommandDTO implements Serializable {
    private String id;
    private String commandType;
    private String  actuator;
    private CommandStatus status;
    private String dateCreated;
    private String dateExecuted;
    private String response;
    private String crop;
}
