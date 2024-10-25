package smarpot.com.api.Models.DAO;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import smarpot.com.api.Models.Entity.Session;

@Repository
public interface RSession extends MongoRepository<Session, String> {
}
