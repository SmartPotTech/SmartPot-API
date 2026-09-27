package app.smartpot.api.virtualdevices.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Simulador interno de macetas (SmartPot-DataGenerator). La API le pide macetas virtuales en nombre de cada
 * cultivo y cada {@code reconcileInterval} vuelve a crear las que falten (por ejemplo, tras un reinicio).
 */
@ConfigurationProperties(prefix = "smartpot.simulator")
public record SimulatorProperties(boolean enabled, String baseUrl, String token, Duration timeout,
                                  Duration reconcileInterval) {

    public SimulatorProperties {
        timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
        reconcileInterval = reconcileInterval == null ? Duration.ofMinutes(1) : reconcileInterval;
    }

    public boolean available() {
        return enabled && baseUrl != null && !baseUrl.isBlank() && token != null && !token.isBlank();
    }
}
