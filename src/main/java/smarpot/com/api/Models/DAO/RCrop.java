package smarpot.com.api.Models.DAO;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import smarpot.com.api.Models.Entity.Crop;

@Repository
public interface RCrop extends MongoRepository<Crop, String> {
}
