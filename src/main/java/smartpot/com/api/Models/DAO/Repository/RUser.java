package smartpot.com.api.Models.DAO.Repository;


import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import smartpot.com.api.Models.Entity.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RUser extends MongoRepository<User, ObjectId> {

    @Query("{ 'name' : ?0 }")
    List<User> findByName(String name);

    @Query("{ 'email' : ?0 }")
    List<User> findByEmail(String email);

    @Query("{ 'role' : ?0 }")
    List<User> findByRole(String role);

    @Transactional
    @Query("{ '_id' : ?0 }")
    void deleteUserById(ObjectId id);

    @Transactional
    @Query("{ '_id' : ?0 }")
    User updateUser(ObjectId  id, User user);
}
