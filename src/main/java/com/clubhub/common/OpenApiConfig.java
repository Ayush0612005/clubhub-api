package com.clubhub.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI at /swagger-ui.html: "Authorize" with an access token to try club endpoints. */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI clubhubOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ClubHub API")
                        .version("v1")
                        .description("""
                                Multi-tenant platform for college clubs. Log in, call POST /api/auth/switch-club \
                                for a club-scoped token, then use /api/club/** endpoints. Errors are RFC 9457 \
                                problem details."""))
                .components(new Components().addSecuritySchemes("bearer", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
