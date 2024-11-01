package smartpot.com.api.Models.DAO.Repository;


import org.springframework.data.mongodb.repository.Query;
import smartpot.com.api.Models.Entity.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RUser extends MongoRepository<User, String> {
    @Query("{ 'email' : ?0 }")
    List<User> findByEmail(String email);

    @Query("{ 'role' : ?0 }")
    List<User> findByRole(String role);

    @Query("{ 'name' : ?0 }")
    List<User> findByName(String name);
}
