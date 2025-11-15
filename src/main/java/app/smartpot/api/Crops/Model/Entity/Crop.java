package app.smartpot.api.Crops.Model.Entity;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
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
     * TODO: Considerar usar un enum para los estados del cultivo (ej. GROWING, HARVESTED).
     * ? ¿Cómo manejar cambios de estado del cultivo en la aplicación?
     */

    @Id
    @Field("_id")
    private ObjectId id;

    @Field("status")
    private CropStatus cropStatus;

    @NotEmpty(message = "El tipo no puede estar vacío")
    @Field("type")
    private CropType cropType;

    /**
     * ! No se puede hacer referencia a los objetos, dado que obliga a usar la entidad completa, no solo el ObjectId.
     */
    //@DBRef
    @NotNull(message = "El cultivo debe pertenecer a un usuario")
    @Field("user")
    private ObjectId user;
}
