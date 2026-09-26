package app.smartpot.api.commands.model.entity;

import app.smartpot.api.actuators.model.entity.ActuatorType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "commands")
@CompoundIndex(name = "crop_created", def = "{'cropId': 1, 'createdAt': -1}")
@CompoundIndex(name = "status_sent", def = "{'status': 1, 'sentAt': 1}")
public class Command {

    @Id
    private String id;

    @Field(targetType = FieldType.OBJECT_ID)
    private String cropId;

    @Field(targetType = FieldType.OBJECT_ID)
    private String actuatorId;

    private ActuatorType actuatorType;

    private CommandAction action;

    private Integer durationSeconds;

    private CommandStatus status;

    private CommandSource source;

    private String reason;

    private String message;

    @Indexed(expireAfter = "180d")
    private Instant createdAt;

    private Instant sentAt;

    private Instant completedAt;
}
