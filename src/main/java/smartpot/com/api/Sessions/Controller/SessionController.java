package smartpot.com.api.Sessions.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import smartpot.com.api.Responses.ErrorResponse;
import smartpot.com.api.Sessions.Model.Entity.Session;
import smartpot.com.api.Sessions.Service.SSessionI;

import java.util.List;

@RestController
@RequestMapping("/Sessions")
public class SessionController {

    private final SSessionI session;

    @Autowired
    public SessionController(SSessionI session) {
        this.session = session;
    }

    @GetMapping
    public List<Session> getAllSessions() {
        return session.getAllSessions();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Session> getSession(@PathVariable String id) {
        Session session1 = session.getSessionById(id);
        if (session1 != null) {
            return ResponseEntity.ok(session1);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/Create")
    public ResponseEntity<?> createSession(@RequestBody Session newSession) {
        try {
            return new ResponseEntity<>(session.createSession(newSession), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(new ErrorResponse(e.getMessage(), HttpStatus.NOT_FOUND.value()), HttpStatus.NOT_FOUND);
        }
    }

    @DeleteMapping("/Delete/{id}")
    public ResponseEntity<Session> deleteSession(@PathVariable String id) {
        if (session.getSessionById(id) != null) {
            session.deleteSessionById(id);
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/user/{UserId}")
    public ResponseEntity<List<Session>> getSessionByUser(@PathVariable String UserId) {
        List<Session> session1 = session.getSessionByUser(UserId);
        if (session1 != null) {
            return ResponseEntity.ok(session1);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/deleteUser/{UserId}")
    public ResponseEntity<Session> deleteSessionByUser(@PathVariable String UserId) {
        if (session.getSessionByUser(UserId) != null) {
            session.deleteSessionByIdUser(UserId);
            return ResponseEntity.ok().build();
        } else {

            return ResponseEntity.notFound().build();
        }
    }

    /*
     */
/*
 *
    @Autowired
    public SessionController(RSession repositorySession) {
        this.repositorySession = repositorySession;
    }
 /*






    @PutMapping("/{id}")
    public ResponseEntity<Session> updateSession(@PathVariable String id, @RequestBody Session sessionDetails) {
        return repositorySession.findById(id)
                .map(session -> {

//                    session.setRegistration(sessionDetails.getRegistration());

                    
                    session.setUser(sessionDetails.getUser());
                    Session updatedSession = repositorySession.save(session);
                    return ResponseEntity.ok(updatedSession);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteSession(@PathVariable String id) {
        return repositorySession.findById(id)
                .map(session -> {
                    repositorySession.delete(session);
                    return ResponseEntity.ok().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
    */
}
