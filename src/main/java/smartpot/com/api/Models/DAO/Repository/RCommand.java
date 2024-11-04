package smartpot.com.api.Models.DAO.Repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import smartpot.com.api.Models.Entity.Command;
import java.util.List;

@Repository
public interface RCommand extends MongoRepository<Command, String> {
    //List<Command> findByStatus(String status);
    //List<Command> findByCrop_Id(String cropId);
}
