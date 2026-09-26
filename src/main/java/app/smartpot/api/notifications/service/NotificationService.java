package app.smartpot.api.notifications.service;

import app.smartpot.api.cache.CacheStore;
import app.smartpot.api.exception.ApiException;
import app.smartpot.api.exception.ObjectIds;
import app.smartpot.api.notifications.model.entity.Notification;
import app.smartpot.api.notifications.model.entity.NotificationType;
import app.smartpot.api.notifications.repository.NotificationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

@Service
public class NotificationService {

    private static final String NOT_FOUND = "La notificación no existe";

    private final NotificationRepository repository;
    private final CacheStore cacheStore;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, CacheStore cacheStore, Clock clock) {
        this.repository = repository;
        this.cacheStore = cacheStore;
        this.clock = clock;
    }

    public Notification notify(String userId, String cropId, NotificationType type, String title, String message) {
        return repository.save(Notification.builder()
                .userId(userId)
                .cropId(cropId)
                .type(type)
                .title(title)
                .message(message)
                .read(false)
                .createdAt(clock.instant())
                .build());
    }

    /**
     * Crea la notificación solo si no se envió otra con la misma llave durante el enfriamiento.
     */
    public boolean notifyOnce(String cooldownKey, Duration cooldown, String userId, String cropId,
                              NotificationType type, String title, String message) {
        if (!cacheStore.setIfAbsent("notify:" + cooldownKey, cooldown)) {
            return false;
        }
        notify(userId, cropId, type, title, message);
        return true;
    }

    public List<Notification> list(String userId, boolean unreadOnly, int limit) {
        PageRequest page = PageRequest.of(0, Math.clamp(limit, 1, 100));
        return unreadOnly
                ? repository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId, page)
                : repository.findByUserIdOrderByCreatedAtDesc(userId, page);
    }

    public long unreadCount(String userId) {
        return repository.countByUserIdAndReadFalse(userId);
    }

    public Notification markRead(String userId, String notificationId) {
        Notification notification = find(userId, notificationId);
        notification.setRead(true);
        return repository.save(notification);
    }

    public void markAllRead(String userId) {
        List<Notification> unread = repository.findByUserIdAndReadFalse(userId);
        unread.forEach(notification -> notification.setRead(true));
        repository.saveAll(unread);
    }

    public void delete(String userId, String notificationId) {
        repository.delete(find(userId, notificationId));
    }

    public void deleteAllForUser(String userId) {
        repository.deleteByUserId(userId);
    }

    public void deleteAllForCrop(String cropId) {
        repository.deleteByCropId(cropId);
    }

    private Notification find(String userId, String notificationId) {
        ObjectIds.require(notificationId, NOT_FOUND);
        return repository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    }
}
