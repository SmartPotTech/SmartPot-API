package smartpot.com.api.Controllers;

import smartpot.com.api.Models.DAO.Repository.RSession;
import smartpot.com.api.Models.DAO.Service.SSession;
import smartpot.com.api.Models.Entity.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/Sesiones")
public class SessionController {

    @Autowired
    private SSession session;

    @GetMapping
    public List<Session> getAllSessions() {
        return session.getAllSessions();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Session> getSession(@PathVariable String id) {
        Session session1 = session.getSessionById(id);
        if( session1 != null) {
            return ResponseEntity.ok(session1);
        }else{
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<Session> createSession(@RequestBody Session newSession) {
        return ResponseEntity.ok(session.createSession(newSession));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Session> deleteSession(@PathVariable String id) {
        if(session.getSessionById(id) != null) {
            session.deleteSessionById(id);
            return ResponseEntity.ok().build();
        }else{
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/User/{idUser}")
    public ResponseEntity<List<Session>> getSessionByUser(@PathVariable String idUser) {
        List<Session> session1 = session.getSessionByUser(idUser);
        if( session1 != null) {
            return ResponseEntity.ok(session1);
        }else{
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/deleteUser/{idUser}")
    public ResponseEntity<Session> deleteSessionByUser(@PathVariable String idUser) {
        if(session.getSessionByUser(idUser) != null) {
            session.deleteSessionByIdUser(idUser);
            return ResponseEntity.ok().build();
        }else{

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
