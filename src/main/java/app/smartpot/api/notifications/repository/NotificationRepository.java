package app.smartpot.api.notifications.repository;

import app.smartpot.api.notifications.model.entity.Notification;
import app.smartpot.api.notifications.model.entity.NotificationType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    List<Notification> findByUserIdAndReadFalseOrderByCreatedAtDesc(String userId, Pageable pageable);

    List<Notification> findByUserIdAndReadFalse(String userId);

    long countByUserIdAndReadFalse(String userId);

    Optional<Notification> findByIdAndUserId(String id, String userId);

    void deleteByUserId(String userId);

    void deleteByCropId(String cropId);

    List<Notification> findByCropIdAndCreatedAtAfterOrderByCreatedAtAsc(String cropId, Instant after);

    long countByCropIdAndTypeAndCreatedAtAfter(String cropId, NotificationType type, Instant after);
}
