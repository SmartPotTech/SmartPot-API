package app.smartpot.api.actuators.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "actuators")
@CompoundIndex(name = "crop_type", def = "{'cropId': 1, 'type': 1}", unique = true)
public class Actuator {

    @Id
    private String id;

    @Field(targetType = FieldType.OBJECT_ID)
    private String cropId;

    private ActuatorType type;

    /** Encendido sin límite de tiempo, según la última confirmación del dispositivo. */
    private boolean active;

    /** Hasta cuándo sigue encendido por una orden con duración; null si no corre ninguna. */
    private Instant runningUntil;

    private Instant lastChangedAt;

    private Instant createdAt;

    /** Encendido ahora: sin límite o por una orden con duración que aún no termina. */
    public boolean isRunning(Instant now) {
        return active || (runningUntil != null && runningUntil.isAfter(now));
    }
}
