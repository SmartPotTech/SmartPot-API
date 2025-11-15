package app.smartpot.api.mail.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import app.smartpot.api.mail.model.entity.EmailDetails;

@Repository
public interface EmailRepository extends MongoRepository<EmailDetails, String> {

}
