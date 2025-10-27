package smartpot.com.api.Actuators.Model.Entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "actuadores")
public class Actuator {
    @Id
    @Field("_id")
    private ObjectId id;

    // Si no se pone acting date, se asumira que se inicia la ejecucion apenas llege la peticion
    @FutureOrPresent(message = "La fecha de actuacion debe ser hoy o en el futuro")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    @Field("actingDate")
    private Date actingDate;

    // segundos
    @NotNull(message = "El tiempo de actuacion no puede estar vacio")
    @Field("performanceTime")
    private int performanceTime;

    @NotNull(message = "El registro debe estar asociado a un cultivo")
    @Field("crop")
    private ObjectId crop;

    @NotNull(message = "El tipo de actuador no puede ser nulo")
    @Field("type")
    private ActuatorType type;
}
