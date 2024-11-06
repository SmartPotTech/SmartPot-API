package smartpot.com.api.Models.DAO.Repository;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import smartpot.com.api.Models.Entity.Crop;
import smartpot.com.api.Models.Entity.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface RCrop extends MongoRepository<Crop, ObjectId> {

    @Query("{ '_id' : ?0 }")
    Optional<Crop> findById(ObjectId id);

    @Query("{ 'type' : ?0 }")
    List<Crop> findByType(String type);

    @Query("{ 'status' : ?0 }")
    List<Crop> findByStatus(String status);

    void deleteById(ObjectId id);
    @Transactional
    @Query("{ '_id' : ?0 }")
    Crop updateUser(ObjectId id, Crop crop);



}
