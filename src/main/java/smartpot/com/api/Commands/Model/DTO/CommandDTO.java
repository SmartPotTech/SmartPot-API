package smartpot.com.api.Commands.Model.DTO;

import lombok.Data;

import java.io.Serializable;

@Data
public class CommandDTO implements Serializable {
    private String id;
    private String commandType;
    private String status;
    private String dateCreated;
    private String dateExecuted;
    private String response;
    private String crop;
}
