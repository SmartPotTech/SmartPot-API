package app.smartpot.api.sessions.Service;

import app.smartpot.api.sessions.model.entity.Session;

import java.util.List;

public interface SessionService {
    Session getSessionById(String sessionId);

    List<Session> getSessionsByUser(String user);

    List<Session> getAllSessions();

    long countSessionsByUser(String user);

    Session createSession(Session newSession) throws Exception;

    void deleteSessionById(String sessionId);

    void deleteSessionByIdUser(String userId);

    List<Session> getSessionByUser(String userId);
}
