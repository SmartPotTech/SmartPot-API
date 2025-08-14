package smartpot.com.api.Documentation;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Value("${TITLE}")
    private String title;

    @Value("${VERSION}")
    private String version;

    @Value("${AUTHOR}")
    private String author;

    @Value("${DESCRIPTION}")
    private String description;


    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "BearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title(title)
                        .version("v" + version)
                        .description(description)
                        .termsOfService("https://github.com/SmarPotTech/SmartPot-API/blob/main/LICENSE")
                        .license(new License().name("MIT License").url("https://opensource.org/license/mit"))
                        .contact(new Contact().name(author).url("https://github.com/SmarPotTech").email("smartpottech@gmail.com"))
                )
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT"))
                );


    }
}