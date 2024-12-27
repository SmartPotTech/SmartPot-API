package smartpot.com.api.Notifications.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import smartpot.com.api.Notifications.Model.DAO.Service.SNotificationI;
import smartpot.com.api.Notifications.Model.Entity.Notification;

import java.util.List;

@RestController
@RequestMapping("/Notificaciones")
public class NotificationController {
    
    /**
     * TODO: Implementar integración con Spring Mail para enviar notificaciones por correo electrónico.
     * <p>Usar la dependencia `spring-boot-starter-mail` para configurar el servicio de correo en la aplicación.</p>
     * <p>Se debe crear un servicio de correo que envíe mensajes de notificación a los usuarios cuando se realicen acciones importantes en la plataforma.</p>
     *
     * Requisitos:
     * 1. Configurar las propiedades de SMTP en el archivo `application.properties` o `application.yml`, como el servidor de correo, puerto, usuario, y contraseña.
     * 2. Crear una clase `EmailService` que se encargue de enviar correos electrónicos.
     * 3. El servicio debe permitir enviar correos a una o más direcciones, con asunto y cuerpo personalizados.
     * 4. Considerar la posibilidad de enviar correos HTML o correos con archivos adjuntos.
     * 5. Implementar un sistema de plantillas para personalizar los correos electrónicos según el tipo de notificación.
     *
     * Preguntas:
     * - ¿Qué tipo de notificaciones necesitan ser enviadas por correo electrónico (registro de usuario, cambios en el perfil, alertas, etc.)?
     * - ¿Cómo manejaremos la seguridad de los datos (por ejemplo, contraseñas, tokens) en los correos electrónicos?
     * - ¿Cómo gestionaremos los errores de envío de correos electrónicos?
     */

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
