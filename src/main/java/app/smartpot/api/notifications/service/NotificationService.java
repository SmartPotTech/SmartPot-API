package app.smartpot.api.notifications.service;

import app.smartpot.api.notifications.model.entity.Notification;

import java.util.List;

public interface NotificationService {
    List<Notification> findAll();

    List<Notification> findByUser(String id);

    List<Notification> findByUserAndType(String id, String type);

    List<Notification> findByUserAndDate(String id, String date);

    Notification updateNotification(String id, Notification notification);

    Notification delete(String id);

    Notification save(Notification notification);
}
