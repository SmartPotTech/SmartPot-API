package smartpot.com.api.Models.DAO;

import org.springframework.data.mongodb.repository.Query;
import smartpot.com.api.Models.Entity.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public interface RUser extends MongoRepository<User, String> {

    List<User> findByEmail(String email);
    List<User> findByRole(String role);
}
