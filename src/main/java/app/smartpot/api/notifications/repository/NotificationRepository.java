package app.smartpot.api.notifications.repository;

import app.smartpot.api.notifications.model.entity.Notification;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;


@Repository
public interface NotificationRepository extends MongoRepository<Notification, ObjectId> {

    @Query("{}")
    List<Notification> findAll();

    @Query("{ '_id' : ?0 }")
    Optional<Notification> findById(ObjectId id);

    @Query("{ 'user_id' : ?0  }")
    Optional<List<Notification>> findByUser(ObjectId id);

    @Query("{ 'user_id': ?0, 'type': ?1}")
    Optional<List<Notification>> findByUserAndType(ObjectId id, String type);

    @Query("{ 'user_id': ?0, 'date': ?1}")
    Optional<List<Notification>> findByUserAndDate(ObjectId id, String date);

    @Transactional
    @Query("{'_id': ?0}")
    Notification updateNotification(ObjectId id, Notification notification);

    @Transactional
    @Query("{'_id':  ?0}")
    Notification delete(ObjectId id);

}
