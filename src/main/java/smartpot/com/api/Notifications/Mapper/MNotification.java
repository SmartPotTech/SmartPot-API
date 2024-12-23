package smartpot.com.api.Notifications.Mapper;

import org.mapstruct.Mapper;
import smartpot.com.api.Notifications.Model.DTO.NotificationDTO;
import smartpot.com.api.Notifications.Model.Entity.Notification;

@Mapper(componentModel="spring")
public interface MNotification {
    public Notification toEntity(NotificationDTO notificationDTO);
    public NotificationDTO toDTO(Notification notification);
}
