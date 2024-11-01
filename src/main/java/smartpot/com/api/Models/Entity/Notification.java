package smartpot.com.api.Models.Entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serializable;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "notificaciones")
public class Notification implements Serializable {

    @Id
    @Field("id")
    private String id;

    @NotEmpty(message = "El mensaje no puede estar vacío")
    @Size(max = 250, message = "El mensaje no puede tener más de 250 caracteres")
    @Field("message")
    private String message;

    @NotEmpty(message = "El tipo no puede estar vacío")
    @Field("type")
    private String type;

    @NotNull(message = "La fecha no puede estar vacía")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    @Field("date")
    private Date date;

    @DBRef
    @NotNull(message = "La notificación debe ir dirigida a un usuario")
    @Field("user")
    private User user;
}
