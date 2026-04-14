package app.smartpot.api.security.config.headers;

import jakarta.servlet.http.HttpServletRequest;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


@Slf4j
@Component
public class CorsConfig implements CorsConfigurationSource {

    @Value("${http.header.cors.allowedOrigins}")
    private String allowedOrigins;

    @Value("${application.development:false}")
    private boolean developmentMode;

    /**
     * Orígenes de localhost permitidos durante el modo de desarrollo.
     * Esto facilita el desarrollo local con frontends en diferentes puertos.
     */
    private static final List<String> LOCALHOST_ORIGINS = List.of(
            "http://localhost:5173",
            "http://localhost:5174",
            "http://127.0.0.1:5173",
            "http://127.0.0.1:5174"
    );

    @Override
    public CorsConfiguration getCorsConfiguration(@NonNull HttpServletRequest request) {
        CorsConfiguration config = new CorsConfiguration();
        List<String> originsList;
        if (allowedOrigins.contains(",")) {
            originsList = new ArrayList<>(Arrays.asList(allowedOrigins.split(",")));
        } else {
            originsList = new ArrayList<>(List.of(allowedOrigins));
        }

        if (developmentMode) {
            log.info("[CORS] Development mode is ACTIVE - localhost origins are allowed");
            for (String localhostOrigin : LOCALHOST_ORIGINS) {
                if (!originsList.contains(localhostOrigin)) {
                    originsList.add(localhostOrigin);
                }
            }
        }

        config.setAllowedOrigins(originsList);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH", "TRACE", "CONNECT"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        return config;
    }

}