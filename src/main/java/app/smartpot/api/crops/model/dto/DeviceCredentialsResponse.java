package app.smartpot.api.crops.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Datos para configurar la maceta. La clave solo viaja al crear el cultivo o al rotarla.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeviceCredentialsResponse(
        String host,
        int port,
        boolean tls,
        String websocketUrl,
        String username,
        String key,
        Topics topics,
        Instant keyRotatedAt
) {

    public record Topics(String telemetry, String commands, String commandAck, String status) {
    }
}
