package app.smartpot.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearer";

    @Bean
    public OpenAPI smartPotOpenApi(@Value("${smartpot.public-api-url}") String publicApiUrl) {
        return new OpenAPI()
                .info(new Info()
                        .title("SmartPot API")
                        .version("2.0.0")
                        .description("API de SmartPot para monitorear y automatizar cultivos hidropónicos: cuentas, "
                                + "cultivos, telemetría, actuadores, comandos por MQTT y asistente de IA.")
                        .contact(new Contact().name("SmartPot Tech").url("https://github.com/SmartPotTech"))
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
                .servers(List.of(new Server().url(publicApiUrl).description("Servidor de la API")))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Token recibido en /api/v1/auth/login o /api/v1/auth/register")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
