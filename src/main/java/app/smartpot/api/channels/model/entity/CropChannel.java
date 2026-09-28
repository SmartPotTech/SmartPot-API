package app.smartpot.api.channels.model.entity;

import app.smartpot.api.notifications.model.entity.NotificationType;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Qué avisa un cultivo por un canal externo y a quién: el chat de su dueño (si lo vinculó) y los chats con los que
 * lo comparte. Sin este documento el cultivo avisa al instante lo que el dueño eligió en su perfil.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "crop_channels")
@CompoundIndex(name = "crop_type", def = "{'cropId': 1, 'type': 1}", unique = true)
public class CropChannel {

    public static final int MAX_RECIPIENTS = 10;

    @Id
    private String id;

    @Field(targetType = FieldType.OBJECT_ID)
    private String cropId;

    @Field(targetType = FieldType.OBJECT_ID)
    private String ownerId;

    private ChannelType type;

    private boolean enabled;

    private Set<NotificationType> events;

    private Delivery delivery;

    /**
     * Cada cuántas horas llega el resumen cuando la entrega es DIGEST.
     */
    private int digestHours;

    /**
     * Hora local (HH:mm) del resumen diario, o null si no se quiere.
     */
    private String dailySummaryAt;

    @Builder.Default
    private List<Recipient> recipients = new ArrayList<>();

    private Instant lastDigestAt;

    private Instant lastSummaryAt;

    private Instant updatedAt;

    /**
     * INSTANT: cada aviso en cuanto ocurre; DIGEST: un resumen cada digestHours horas.
     */
    public enum Delivery {
        INSTANT, DIGEST
    }

    /**
     * Un chat con el que se comparte el cultivo: recibe sus avisos, pero no entra a la cuenta.
     */
    public record Recipient(String id, String address, String displayName, Instant addedAt) {
    }
}
