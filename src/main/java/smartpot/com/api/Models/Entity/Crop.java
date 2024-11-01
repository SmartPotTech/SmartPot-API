package smartpot.com.api.Models.Entity;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "cultivos")
public class Crop implements Serializable {

    /**
     * Representa un cultivo en el sistema.
     * * Esta clase contiene información sobre un cultivo, incluyendo su estado,
     * * tipo y el usuario al que pertenece. Se utiliza en la colección "cultivos"
     * * de MongoDB.
     */

    @Id
    @Field("id")
    private String id;

    @Field("status")
    private String status;

    @NotEmpty(message = "El tipo no puede estar vacío")
    @Field("type")
    private String type;

    @DBRef
    @NotNull(message = "El cultivo debe pertenecer a un usuario")
    @Field("user")
    private User user;
}
