package smartpot.com.api.Controllers;

import smartpot.com.api.Models.DAO.RNotification;
import smartpot.com.api.Models.Entity.Notification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/Notificaciones")
public class NotificationController {

    @Autowired
    private RNotification repositoryNotification;

    @GetMapping
    public List<Notification> getAllNotifications() {
        return repositoryNotification.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Notification> getNotification(@PathVariable String id) {
        return repositoryNotification.findById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new RuntimeException("Notificación con id " + id + " no encontrada"));
    }

    @PostMapping
    public Notification createNotification(@RequestBody Notification newNotification) {
        return repositoryNotification.save(newNotification);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Notification> updateNotification(@PathVariable String id, @RequestBody Notification notificationDetails) {
        return repositoryNotification.findById(id)
                .map(notification -> {
                    notification.setMessage(notificationDetails.getMessage());
                    notification.setType(notificationDetails.getType());
                    notification.setUser(notificationDetails.getUser());
                    // Actualiza la fecha a la actual
                    notification.setDate(new java.util.Date());
                    Notification updatedNotification = repositoryNotification.save(notification);
                    return ResponseEntity.ok(updatedNotification);
                })
                .orElseThrow(() -> new RuntimeException("Notificación con id " + id + " no encontrada"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotification(@PathVariable String id) {
        return repositoryNotification.findById(id)
                .map(notification -> {
                    repositoryNotification.delete(notification);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElseThrow(() -> new RuntimeException("Notificación con id " + id + " no encontrada"));
    }
}
