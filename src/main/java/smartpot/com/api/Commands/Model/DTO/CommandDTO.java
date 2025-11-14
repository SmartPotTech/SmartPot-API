package smartpot.com.api.Commands.Model.DTO;

import lombok.Data;
import smartpot.com.api.Commands.Model.Entity.CommandStatus;

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
