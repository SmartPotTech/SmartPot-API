package app.smartpot.api.security.config.headers;

import jakarta.servlet.http.HttpServletRequest;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Arrays;
import java.util.List;


@Component
public class CorsConfig implements CorsConfigurationSource {

    @Value("${http.header.cors.allowedOrigins}")
    private String allowedOrigins;

    @Override
    public CorsConfiguration getCorsConfiguration(@NonNull HttpServletRequest request) {
        CorsConfiguration config = new CorsConfiguration();
        List<String> originsList;
        if (allowedOrigins.contains(",")) {
            originsList = Arrays.asList(allowedOrigins.split(","));
        } else {
            originsList = List.of(allowedOrigins);
        }
        config.setAllowedOrigins(originsList);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH", "TRACE", "CONNECT"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        return config;
    }

}