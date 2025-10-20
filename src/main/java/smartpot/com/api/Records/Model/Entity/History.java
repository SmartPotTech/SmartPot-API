package smartpot.com.api.Records.Model.Entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
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
@Document(collection = "registros")
public class History implements Serializable {

    /**
     * Representa un registro histórico en el sistema.
     * * Esta clase contiene información sobre un registro relacionado con un cultivo,
     * * incluyendo la fecha del registro y las medidas asociadas. Se utiliza en la
     * * colección "registros" de MongoDB.
     * TODO: Considerar establecer la fecha por defecto al crear un registro si no se proporciona.
     * ! Asegurarse de que las validaciones de medidas se ajusten a los rangos esperados (ej. pH).
     * ? ¿Qué hacer si las medidas están fuera de los rangos aceptables?
     */

    @Id
    @Field("_id")
    private ObjectId id;

    @NotNull(message = "La fecha no puede estar vacía")
    @PastOrPresent(message = "La fecha del registro debe ser anterior o igual a hoy")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    @Field("date")
    private Date date;

    @Field("measures")
    private Measures measures;

    //@DBRef
    @NotNull(message = "El registro debe estar asociado a un cultivo")
    @Field("crop")
    private ObjectId crop;
}
