package com.zomato.backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the OpenAPI / Swagger UI documentation.
 *
 * After this, the Swagger UI at /swagger-ui.html will show:
 *  - An "Authorize" button that lets you paste a JWT and send it
 *    as "Authorization: Bearer <token>" on every request
 *  - API info (title, version, contact)
 *  - Server URL matching the running instance
 */
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title       = "Zomato Backend API",
        version     = "1.0.0",
        description = "REST API for the Zomato Backend Clone — Spring Boot + MySQL + Redis",
        contact     = @Contact(
            name  = "Abdus Rahman",
            email = "abdusrahman64@gmail.com"
        )
    ),
    servers = {
        @Server(url = "http://localhost:8080", description = "Local Development")
    }
)
@SecurityScheme(
    name        = "bearerAuth",           // referenced by @SecurityRequirement on controllers
    type        = SecuritySchemeType.HTTP,
    scheme      = "bearer",
    bearerFormat = "JWT",
    in          = SecuritySchemeIn.HEADER,
    description = "Paste your JWT token here (without the 'Bearer ' prefix)"
)
public class SwaggerConfig {
    // No beans needed — all configuration is via annotations above
}
