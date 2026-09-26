package app.smartpot.api.security.config.headers;

import app.smartpot.api.config.SmartPotProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

@Component
public class CorsConfig extends UrlBasedCorsConfigurationSource {

    public CorsConfig(SmartPotProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.security().corsAllowedOrigins().stream()
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setExposedHeaders(List.of("Retry-After"));
        config.setAllowCredentials(false);
        config.setMaxAge(Duration.ofHours(1));
        registerCorsConfiguration("/**", config);
    }
}
