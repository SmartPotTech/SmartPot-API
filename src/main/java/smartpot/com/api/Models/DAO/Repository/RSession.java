package smartpot.com.api.Models.DAO.Repository;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import smartpot.com.api.Models.Entity.Session;
import smartpot.com.api.Models.Entity.User;

import java.util.Date;
import java.util.List;

@Repository
public interface RSession extends MongoRepository<Session, ObjectId> {
    List<Session> findByUser(User user);
//    List<Session> findByRegistrationBetween(Date startDate, Date endDate);
    long countByUser(User user);
//    List<Session> findByRegistrationAfter(Date currentDate);
}
