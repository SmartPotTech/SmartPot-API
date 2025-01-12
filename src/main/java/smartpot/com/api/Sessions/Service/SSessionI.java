package smartpot.com.api.Sessions.Service;

import smartpot.com.api.Sessions.Model.Entity.Session;

import java.util.List;

public interface SSessionI {
    Session getSessionById(String sessionId);

    List<Session> getSessionsByUser(String user);

    List<Session> getAllSessions();

    long countSessionsByUser(String user);

    Session createSession(Session newSession) throws Exception;

    void deleteSessionById(String sessionId);

    void deleteSessionByIdUser(String userId);

    List<Session> getSessionByUser(String userId);
}
