package app.smartpot.api.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Servicio interno de IA (sistema experto, lógica difusa y modelos de ML).
 */
@ConfigurationProperties(prefix = "smartpot.ai")
public record AiProperties(
        boolean enabled,
        String baseUrl,
        String token,
        Duration timeout,
        Duration evaluationInterval,
        Duration automationCooldown,
        int historySize
) {
}
