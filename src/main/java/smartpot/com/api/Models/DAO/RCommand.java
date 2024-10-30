package smartpot.com.api.Models.DAO;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import smartpot.com.api.Models.Entity.Command;

@Repository
public interface RCommand extends MongoRepository<Command, String> {
}
