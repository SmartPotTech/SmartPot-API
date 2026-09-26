package app.smartpot.api.crops.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Credenciales MQTT de la maceta. La clave se guarda cifrada para poder reaprovisionar el broker.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Device {

    private String keyCiphertext;

    private Instant keyRotatedAt;

    private boolean online;

    private Instant lastSeenAt;
}
