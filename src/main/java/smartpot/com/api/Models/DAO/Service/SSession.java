package smartpot.com.api.Models.DAO.Service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import smartpot.com.api.Models.DAO.Repository.RSession;
import smartpot.com.api.Models.Entity.Session;
import smartpot.com.api.Models.Entity.User;

import java.util.Date;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Service
public class SSession {
    @Autowired
    private RSession repositorySession;

    public List<Session> getSessionsByUser(User user) {
        return repositorySession.findByUser(user);
    }

   /* public List<Session> getSessionsInRange(Date startDate, Date endDate) {
        return repositorySession.findByRegistrationBetween(startDate, endDate);
    }*/

    public long countSessionsByUser(User user) {
        return repositorySession.countByUser(user);
    }
/*
    public List<Session> getFutureSessions(Date currentDate) {
        return repositorySession.findByRegistrationAfter(currentDate);
    }*/

    public Session createSession(Session newSession) {
        return repositorySession.save(newSession);
    }
}
