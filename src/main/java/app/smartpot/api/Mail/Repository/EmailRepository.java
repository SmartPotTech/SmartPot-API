package app.smartpot.api.Mail.Repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import app.smartpot.api.Mail.Model.Entity.EmailDetails;

@Repository
public interface EmailRepository extends MongoRepository<EmailDetails, String> {

}
