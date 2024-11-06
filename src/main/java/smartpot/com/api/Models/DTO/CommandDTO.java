package smartpot.com.api.Models.DTO;

import lombok.Data;

@Data
public class CommandDTO {
    private String commandType;
    private String status;
    private String dateCreated;
    private String dateExecuted;
    private String response;
    private String crop;
}
