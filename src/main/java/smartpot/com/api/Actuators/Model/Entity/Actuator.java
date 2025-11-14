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

    @NotNull(message = "El registro debe estar asociado a un cultivo")
    @Field("crop")
    private ObjectId crop;

    @NotNull(message = "El tipo de actuador no puede ser nulo")
    @Field("type")
    private ActuatorType type;
}
