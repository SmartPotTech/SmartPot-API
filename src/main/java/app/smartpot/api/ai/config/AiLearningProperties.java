package app.smartpot.api.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Envío de las lecturas reales al servicio de IA para el aprendizaje continuo. Las lecturas se agrupan
 * en lotes y se envían cada {@code flushInterval}.
 */
@ConfigurationProperties(prefix = "smartpot.ai.learning")
public record AiLearningProperties(boolean enabled, Duration flushInterval) {

    public AiLearningProperties {
        flushInterval = flushInterval == null ? Duration.ofMinutes(1) : flushInterval;
    }
}
