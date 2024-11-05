package smartpot.com.api.Models.DAO;

import org.springframework.data.mongodb.repository.Query;
import smartpot.com.api.Models.Entity.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RUser extends MongoRepository<User, String> {

    @Query
    public User findByEmail(String email);

}