package app.smartpot.api.notifications.model.entity;

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
@Document(collection = "notifications")
@CompoundIndex(name = "user_created", def = "{'userId': 1, 'createdAt': -1}")
public class Notification {

    @Id
    private String id;

    @Field(targetType = FieldType.OBJECT_ID)
    private String userId;

    @Field(targetType = FieldType.OBJECT_ID)
    private String cropId;

    private NotificationType type;

    private String title;

    private String message;

    private boolean read;

    @Indexed(expireAfter = "90d")
    private Instant createdAt;
}
