package smartpot.com.api.Models.DAO.Repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import smartpot.com.api.Models.Entity.Notification;

@Repository
public interface RNotification extends MongoRepository<Notification, String> {
}
