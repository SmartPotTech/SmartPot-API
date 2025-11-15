package app.smartpot.api.notifications.controller;

import app.smartpot.api.notifications.model.entity.Notification;
import app.smartpot.api.notifications.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/Notificaciones")
public class NotificationController {

    /**
     * TODO: Implementar integración con Spring Mail para enviar notificaciones por correo electrónico.
     * <p>Usar la dependencia <code>spring-boot-starter-mail</code> para configurar el servicio de correo en la aplicación.</p>
     * <p>Se debe crear un servicio de correo que envíe mensajes de notificación a los usuarios cuando se realicen acciones importantes en la plataforma.</p>
     *
     * <h3>Pasos a seguir:</h3>
     * <ul>
     *     <li>Configurar las propiedades de SMTP en el archivo <code>application.properties</code> o <code>application.yml</code>, como el servidor de correo, puerto, usuario, y contraseña.</li>
     *     <li>Crear una clase <code>EmailService</code> que se encargue de enviar correos electrónicos.</li>
     *     <li>El servicio debe permitir enviar correos a una o más direcciones, con asunto y cuerpo personalizados.</li>
     *     <li>Considerar la posibilidad de enviar correos HTML o correos con archivos adjuntos.</li>
     *     <li>Implementar un sistema de plantillas para personalizar los correos electrónicos según el tipo de notificación.</li>
     * </ul>
     *
     * <h3> Preguntas a considerar:</h3>
     * <ul>
     *     <li><b>¿Qué tipo de notificaciones necesitan ser enviadas por correo electrónico?</b> Ejemplos: registro de usuario, cambios en el perfil, alertas, etc.</li>
     *     <li><b>¿Cómo manejaremos la seguridad de los datos</b> (por ejemplo, contraseñas, tokens) en los correos electrónicos?</li>
     *     <li><b>¿Cómo gestionaremos los errores de envío de correos electrónicos?</b> Considerar la implementación de un mecanismo de reintentos o notificación de fallos.</li>
     * </ul>
     *
     * <p>Este proceso debe garantizar que los correos electrónicos sean enviados de forma confiable y segura, cumpliendo con las políticas de privacidad y seguridad de la plataforma.</p>
     */


    private final NotificationService serviceNotification;

    @Autowired
    public NotificationController(NotificationService serviceNotification) {
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
    public List<Notification> getNotificationByUserAndType(@PathVariable String type, @PathVariable String id) {
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
