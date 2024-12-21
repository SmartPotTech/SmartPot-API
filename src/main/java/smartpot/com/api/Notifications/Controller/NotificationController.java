package smartpot.com.api.Notifications.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import smartpot.com.api.Notifications.Model.DAO.Service.SNotificationI;
import smartpot.com.api.Notifications.Model.Entity.Notification;

import java.util.List;

@RestController
@RequestMapping("/Notificaciones")
public class NotificationController {

    private final SNotificationI serviceNotification;

    @Autowired
    public NotificationController(SNotificationI serviceNotification) {
        this.serviceNotification = serviceNotification;
    }

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
