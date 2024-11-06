package smartpot.com.api.Models.DAO.Service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import smartpot.com.api.Models.DAO.Repository.RNotification;
import smartpot.com.api.Models.Entity.Notification;
import smartpot.com.api.Validation.Exception.ApiException;
import smartpot.com.api.Validation.Exception.ApiResponse;

import java.util.List;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Service
public class SNotification{

    @Autowired
    private RNotification repositoryNotification;

    public List<Notification> findAll() {
        return repositoryNotification.findAll();
    }


    public List<Notification> findByUser(String id) {
        if (!ObjectId.isValid(id)) {
            throw new ApiException(new ApiResponse(
                    "Las notificaciones con user id '"+ id +"' no es válida. Asegúrate de que tiene 24 caracteres y solo incluye dígitos hexadecimales (0-9, a-f, A-F).",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }
        return repositoryNotification.findByUser(new ObjectId(id))
                .orElseThrow(() -> new ApiException(
                new ApiResponse("La notificación con id '"+ id +"' no fue encontrada.",
                        HttpStatus.NOT_FOUND.value())
        ));
    }

    public List<Notification> findByUserAndType(String id, String type) {
        if (!ObjectId.isValid(id)) {
            throw new ApiException(new ApiResponse(
                    "Las notificaciones con user id '"+ id +"' no es válida. Asegúrate de que tiene 24 caracteres y solo incluye dígitos hexadecimales (0-9, a-f, A-F).",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }
        return repositoryNotification.findByUserAndType(new ObjectId(id), type)
                .orElseThrow(() -> new ApiException(
                        new ApiResponse("La notificación con id '"+ id +"' no fue encontrada.",
                                HttpStatus.NOT_FOUND.value())
                ));
    }

    public List<Notification> findByUserAndDate(String id, String date) {
        if (!ObjectId.isValid(id)) {
            throw new ApiException(new ApiResponse(
                    "Las notificaciones con user id '"+ id +"' no es válida. Asegúrate de que tiene 24 caracteres y solo incluye dígitos hexadecimales (0-9, a-f, A-F).",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }
        return repositoryNotification.findByUserAndType(new ObjectId(id), date)
                .orElseThrow(() -> new ApiException(
                        new ApiResponse("La notificación con id '"+ id +"' no fue encontrada.",
                                HttpStatus.NOT_FOUND.value())
                ));
    }

    public Notification updateNotification(String id, Notification notification) {
        if(!ObjectId.isValid(id)) {
            throw new ApiException(new ApiResponse(
                    "Las notificaciones con user id '"+ id +"' no es válida. Asegúrate de que tiene 24 caracteres y solo incluye dígitos hexadecimales (0-9, a-f, A-F).",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }
        return repositoryNotification.updateNotification(new ObjectId(id), notification);
    }


    public Notification delete(String id) {
        if(!ObjectId.isValid(id)) {
            throw new ApiException(new ApiResponse(
                    "La notificaciones con id '"+ id +"' no es válida. Asegúrate de que tiene 24 caracteres y solo incluye dígitos hexadecimales (0-9, a-f, A-F).",
                    HttpStatus.BAD_REQUEST.value()
            ));
        }
        return repositoryNotification.delete(new ObjectId(id));
    }

    public Notification save(Notification notification) {
        return repositoryNotification.save(notification);
    }
}
