package smartpot.com.api.Models.DAO.Repository;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import smartpot.com.api.Models.Entity.History;

import java.util.List;

@Repository
public interface RHistory extends MongoRepository<History, ObjectId> {

    @Query("{ 'crop' : ?0 }")
    public List<History> getHistoriesByCrop(ObjectId cropId);

}
