package smartpot.com.api.Models.DAO;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import smartpot.com.api.Models.Entity.History;

@Repository
public interface RHistory extends MongoRepository<History, String> {
}
