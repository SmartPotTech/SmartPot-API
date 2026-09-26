package app.smartpot.api.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.ZoneId;

/**
 * Servicio interno de IA (sistema experto, lógica difusa y modelos de ML).
 * La zona horaria define la hora local que usa el asistente para reconocer el descanso nocturno.
 */
@ConfigurationProperties(prefix = "smartpot.ai")
public record AiProperties(
        boolean enabled,
        String baseUrl,
        String token,
        Duration timeout,
        Duration evaluationInterval,
        Duration automationCooldown,
        int historySize,
        ZoneId timezone
) {
}
