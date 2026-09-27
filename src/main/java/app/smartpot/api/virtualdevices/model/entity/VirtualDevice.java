package app.smartpot.api.virtualdevices.model.entity;

import app.smartpot.api.readings.model.entity.Measures;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;

/** Configuración de la maceta virtual de un cultivo; el simulador la ejecuta y la API la vuelve a crear si falta. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "virtual_devices")
public class VirtualDevice {

    @Id
    private String id;

    @Indexed(unique = true)
    @Field(targetType = FieldType.OBJECT_ID)
    private String cropId;

    @Indexed
    @Field(targetType = FieldType.OBJECT_ID)
    private String ownerId;

    private VirtualMode mode;

    private Measures manual;

    private VirtualLocation location;

    private int intervalSeconds;

    private Instant createdAt;

    private Instant updatedAt;
}
