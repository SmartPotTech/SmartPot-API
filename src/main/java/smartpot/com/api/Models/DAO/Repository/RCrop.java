package smartpot.com.api.Models.DAO.Repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import smartpot.com.api.Models.Entity.Crop;
import smartpot.com.api.Models.Entity.User;
import java.util.List;

@Repository
public interface RCrop extends MongoRepository<Crop, String> {
    List<Crop> findByUser(User user);
    List<Crop> findByType(String type);
    long countByUser(User user);
}
