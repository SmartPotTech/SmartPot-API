package smartpot.com.api.Mail.Repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import smartpot.com.api.Mail.Model.Entity.EmailDetails;

@Repository
public interface EmailRepository extends MongoRepository<EmailDetails, String> {

}
