package smartpot.com.api.Sessions.Repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import smartpot.com.api.Sessions.Model.Entity.Session;

import java.util.Date;
import java.util.List;

@Repository
public interface RSession extends MongoRepository<Session, String> {


    @Query("{ 'user' : ?0 }")
    List<Session> findByUser(String userId);


    @Query("{ 'registration' : { $gte: ?0, $lte: ?1 } }")
    List<Session> findByRegistrationBetween(Date startDate, Date endDate);


    @Query(value = "{ 'user' : ?0 }", count = true)
    long countByUser(String userId);


    @Query("{ 'registration' : { $gt: ?0 } }")
    List<Session> findByRegistrationAfter(Date currentDate);


    @Transactional
    @Query(value = "{ 'user' : ?0 }", delete = true)
    void deleteByUser(String userId);
}
