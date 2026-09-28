package app.smartpot.api.crops.model.entity;

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
@Document(collection = "crops")
@CompoundIndex(name = "owner_created", def = "{'ownerId': 1, 'createdAt': -1}")
public class Crop {

    @Id
    private String id;

    @Field(targetType = FieldType.OBJECT_ID)
    private String ownerId;

    private String name;

    private CropType type;

    /**
     * Real o virtual; se fija al crear el cultivo y no cambia.
     */
    private CropKind kind;

    private CropForm form;

    private Placement placement;

    private boolean automationEnabled;

    private Device device;

    private CropHealth health;

    private Instant createdAt;

    private Instant updatedAt;

    /**
     * Los cultivos anteriores a este campo son reales.
     */
    public boolean isVirtual() {
        return kind == CropKind.VIRTUAL;
    }
}
