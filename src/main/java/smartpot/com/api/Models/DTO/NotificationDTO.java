package smartpot.com.api.Models.DTO;

import lombok.Data;

@Data
public class NotificationDTO {
    private String message;
    private String type;
    private String date;
    private String user;
}
