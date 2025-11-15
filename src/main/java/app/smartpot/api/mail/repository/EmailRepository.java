package app.smartpot.api.mail.repository;

import app.smartpot.api.mail.model.entity.EmailDetails;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailRepository extends MongoRepository<EmailDetails, String> {

}
