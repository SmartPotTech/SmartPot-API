package smartpot.com.api.Controllers;

import smartpot.com.api.Models.DAO.Repository.RSession;
import smartpot.com.api.Models.Entity.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/Sesiones")
public class SessionController {

    private final RSession repositorySession;

    @Autowired
    public SessionController(RSession repositorySession) {
        this.repositorySession = repositorySession;
    }

    @GetMapping
    public List<Session> getAllSessions() {
        return repositorySession.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Session> getSession(@PathVariable String id) {
        return repositorySession.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

 /*   @PostMapping
    public ResponseEntity<Session> createSession(@RequestBody Session newSession) {
        newSession.setRegistration(new Date()); // Asigna la fecha actual
        Session savedSession = repositorySession.save(newSession);
        return ResponseEntity.ok(savedSession);
    }*/

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
}
