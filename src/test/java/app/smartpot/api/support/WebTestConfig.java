package app.smartpot.api.support;

import app.smartpot.api.config.ClockConfig;
import app.smartpot.api.config.SmartPotProperties;
import app.smartpot.api.security.config.JsonErrorWriter;
import app.smartpot.api.security.config.SecurityConfiguration;
import app.smartpot.api.security.config.headers.CorsConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Cadena de seguridad real para las pruebas de controladores (@WebMvcTest no escanea @Configuration).
 */
@TestConfiguration
@EnableConfigurationProperties(SmartPotProperties.class)
@Import({SecurityConfiguration.class, JsonErrorWriter.class, CorsConfig.class, ClockConfig.class})
public class WebTestConfig {
}
