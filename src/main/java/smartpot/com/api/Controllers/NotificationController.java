package smartpot.com.api.Controllers;

import org.bson.types.ObjectId;
import smartpot.com.api.Models.DAO.Service.SNotification;
import smartpot.com.api.Models.Entity.Notification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/Notificaciones")
public class NotificationController {


    @Autowired
    private SNotification serviceNotification;

    @GetMapping
    public List<Notification> getAllNotifications() {
        return serviceNotification.findAll();
    }

    @GetMapping("/{id}")
    public List<Notification> getNotificationByUser(@PathVariable String id) {
        return serviceNotification.findByUser(id);
    }

    @GetMapping("/{type}/{id}")
    public List<Notification> getNotifiacationByUserAndType(@PathVariable String type, @PathVariable String id) {
        return serviceNotification.findByUserAndType(id, type);
    }

    @PostMapping
    public Notification createNotification(@RequestBody Notification newNotification) {
        return serviceNotification.save(newNotification);
    }

    @PutMapping("/{id}")
    public Notification updateNotification(@PathVariable String id, @RequestBody Notification notificationDetails) {
        return serviceNotification.updateNotification(id, notificationDetails);
    }



    @DeleteMapping("/{id}")
    public String deleteNotification(@PathVariable String id) {
        serviceNotification.delete(id);
        return "eliminado";

    }

}
