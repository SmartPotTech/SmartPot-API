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

    @Query("{ 'name' : { $regex: ?0, $options: 'i' } }")
    List<User> findByName(String name);

    @Query("{ 'lastname' : { $regex: ?0, $options: 'i' } }")
    List<User> findByLastname(String lastname);

    @Query("{ 'name' : { $regex: ?0, $options: 'i' }, 'lastname' : { $regex: ?1, $options: 'i' } }")
    List<User> findByFullName(String name, String lastname);

    @Query("{ 'email' : ?0 }")
    List<User> findByEmail(String email);

    @Query("{ 'role' : ?0 }")
    List<User> findByRole(String role);

    void deleteUserById(ObjectId id);
}
