package app.smartpot.api.config;

import app.smartpot.api.documentation.DocumentController;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ApiVersioningConfig implements WebMvcConfigurer {

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("api/v1", c ->
                c.getPackageName().startsWith("app.smartpot.api")
                        && c.isAnnotationPresent(RestController.class)
                        && !DocumentController.class.isAssignableFrom(c)
        );
    }
}
