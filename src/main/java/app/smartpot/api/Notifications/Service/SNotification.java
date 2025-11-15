package app.smartpot.api.Notifications.Service;

import lombok.Builder;
import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import app.smartpot.api.Exception.ApiException;
import app.smartpot.api.Exception.ApiResponse;
import app.smartpot.api.Notifications.Model.Entity.Notification;
import app.smartpot.api.Notifications.Repository.RNotification;

import java.util.List;


@Data
@Builder
@Service
public class SNotification implements SNotificationI {

    private final RNotification repositoryNotification;

    @Autowired
    public SNotification(RNotification repositoryNotification) {
        this.repositoryNotification = repositoryNotification;
    }

    @Override
    public List<Notification> findAll() {
        return repositoryNotification.findAll();
    }

    @Override
    public List<Notification> findByUser(String id) {
        if (!ObjectId.isValid(id)) {
            throw new ApiException(new ApiResponse(
                    "Las notificaciones con user id '" + id + "' no es válida. Asegúrate de que tiene 24 caracteres y solo incluye dígitos hexadecimales (0-9, a-f, A-F).",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }
        return repositoryNotification.findByUser(new ObjectId(id))
                .orElseThrow(() -> new ApiException(
                        new ApiResponse("La notificación con id '" + id + "' no fue encontrada.",
                                HttpStatus.NOT_FOUND.value())
                ));
    }

    @Override
    public List<Notification> findByUserAndType(String id, String type) {
        if (!ObjectId.isValid(id)) {
            throw new ApiException(new ApiResponse(
                    " con user id '" + id + "' no es válida. Asegúrate de que tiene 24 caracteres y solo incluye dígitos hexadecimales (0-9, a-f, A-F).",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }
        return repositoryNotification.findByUserAndType(new ObjectId(id), type)
                .orElseThrow(() -> new ApiException(
                        new ApiResponse("No se pudo encontrar notificaciones.",
                                HttpStatus.NOT_FOUND.value())
                ));
    }

    @Override
    public List<Notification> findByUserAndDate(String id, String date) {
        if (!ObjectId.isValid(id)) {
            throw new ApiException(new ApiResponse(
                    "Las notificaciones con user id '" + id + "' no es válida. Asegúrate de que tiene 24 caracteres y solo incluye dígitos hexadecimales (0-9, a-f, A-F).",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }
        return repositoryNotification.findByUserAndDate(new ObjectId(id), date)
                .orElseThrow(() -> new ApiException(
                        new ApiResponse("La notificación con id '" + id + "' no fue encontrada.",
                                HttpStatus.NOT_FOUND.value())
                ));
    }

    @Override
    public Notification updateNotification(String id, Notification notification) {
        if (!ObjectId.isValid(id)) {
            throw new ApiException(new ApiResponse(
                    "Las notificaciones con user id '" + id + "' no es válida. Asegúrate de que tiene 24 caracteres y solo incluye dígitos hexadecimales (0-9, a-f, A-F).",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }
        return repositoryNotification.updateNotification(new ObjectId(id), notification);
    }

    @Override
    public Notification delete(String id) {
        if (!ObjectId.isValid(id)) {
            throw new ApiException(new ApiResponse(
                    "La notificaciones con id '" + id + "' no es válida. Asegúrate de que tiene 24 caracteres y solo incluye dígitos hexadecimales (0-9, a-f, A-F).",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }
        return repositoryNotification.delete(new ObjectId(id));
    }

    @Override
    public Notification save(Notification notification) {
        return repositoryNotification.save(notification);
    }
}
