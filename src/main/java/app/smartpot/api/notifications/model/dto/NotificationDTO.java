package app.smartpot.api.notifications.model.dto;

import lombok.Data;

@Data
public class NotificationDTO {
    private String id;
    private String message;
    private String type;
    private String date;
    private String user;
}
