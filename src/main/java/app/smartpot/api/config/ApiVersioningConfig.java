package app.smartpot.api.config;

import app.smartpot.api.health.HealthController;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Todas las rutas de negocio viven bajo /api/v1; /health queda en la raíz para la infraestructura.
 */
@Configuration
public class ApiVersioningConfig implements WebMvcConfigurer {

    public static final String PREFIX = "/api/v1";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(PREFIX, type -> type.getPackageName().startsWith("app.smartpot.api")
                && type.isAnnotationPresent(RestController.class)
                && !HealthController.class.equals(type));
    }
}
