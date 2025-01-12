package smartpot.com.api.Notifications.Service;

import smartpot.com.api.Notifications.Model.Entity.Notification;

import java.util.List;

public interface SNotificationI {
    List<Notification> findAll();

    List<Notification> findByUser(String id);

    List<Notification> findByUserAndType(String id, String type);

    List<Notification> findByUserAndDate(String id, String date);

    Notification updateNotification(String id, Notification notification);

    Notification delete(String id);

    Notification save(Notification notification);
}
