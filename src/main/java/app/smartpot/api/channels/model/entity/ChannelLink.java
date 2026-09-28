package app.smartpot.api.channels.model.entity;

import app.smartpot.api.notifications.model.entity.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.Instant;
import java.util.Set;

/**
 * Vínculo entre una cuenta y un canal externo (por ejemplo, un chat de Telegram). Una cuenta tiene como
 * máximo un vínculo por canal y una misma dirección (chat) pertenece a una sola cuenta.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "channel_links")
@CompoundIndexes({
        @CompoundIndex(name = "user_type", def = "{'userId': 1, 'type': 1}", unique = true),
        @CompoundIndex(name = "type_address", def = "{'type': 1, 'address': 1}", unique = true)
})
public class ChannelLink {

    @Id
    private String id;

    @Field(targetType = FieldType.OBJECT_ID)
    private String userId;

    private ChannelType type;

    /**
     * Identificador del destino en el canal: en Telegram, el id del chat.
     */
    private String address;

    private String displayName;

    private boolean enabled;

    private Set<NotificationType> events;

    private Instant linkedAt;

    private Instant lastDeliveredAt;

    private int failures;
}
