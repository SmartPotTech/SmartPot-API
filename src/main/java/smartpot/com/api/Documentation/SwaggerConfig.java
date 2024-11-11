package smartpot.com.api.Documentation;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API de SmartPot")
                        .version("v1")
                        .description("Documentación de la API REST de SmartPot")
                        .termsOfService("Terms of Service")
                        .license(new License().name("Apache 2.0").url("http://www.apache.org/licenses/LICENSE-2.0.html"))
                        .contact(new Contact().name("Soporte SmartPot").email("soporte@smartpot.com").url("https://www.smartpot.com/contacto"))
                );
    }
}