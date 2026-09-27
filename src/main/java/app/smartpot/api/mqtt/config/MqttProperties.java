package app.smartpot.api.mqtt.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Conexión interna al broker y datos públicos que se le entregan al usuario para configurar su dispositivo.
 */
@ConfigurationProperties(prefix = "smartpot.mqtt")
public record MqttProperties(
        boolean enabled,
        String brokerUri,
        String clientId,
        String username,
        String password,
        String topicPrefix,
        String publicHost,
        int publicPort,
        boolean publicTls,
        String websocketUrl,
        Duration commandTimeout
) {
}
