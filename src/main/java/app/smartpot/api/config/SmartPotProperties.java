package app.smartpot.api.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "smartpot")
public record SmartPotProperties(
        @NotBlank String webBaseUrl,
        Security security,
        Readings readings,
        Mail mail
) {

    public record Security(
            @NotBlank String jwtSecret,
            Duration jwtExpiration,
            @NotBlank String aesKey,
            List<String> corsAllowedOrigins,
            int rateLimitPerMinute,
            int authRateLimitPerMinute,
            Duration passwordResetExpiration
    ) {
    }

    public record Readings(Duration minInterval, int maxPageSize) {
    }

    public record Mail(boolean enabled, @NotBlank String from) {
    }
}
