package com.cognilogistic.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Springdoc OpenAPI definition: Postman (and other clients) can import
 * {@code GET /v3/api-docs} as OpenAPI 3 JSON while the API is running.
 *
 * <p>Security scheme {@code bearer-jwt} matches {@code @SecurityRequirement(name = "bearer-jwt")}
 * on controllers. Public routes (login, OTP, legal, etc.) omit that annotation.
 */
@Configuration
public class OpenApiConfig {

    /** Name referenced by {@code @SecurityRequirement(name = ...)} on controllers. */
    public static final String BEARER_JWT = "bearer-jwt";

    @Bean
    public OpenAPI cogniLogisticOpenAPI() {
        String description = """
                CogniLogistic HTTP API (v1).

                **Response envelope:** Almost every JSON response is `ApiResponse<T>`:
                - Success: `{ "success": true, "data": { ... } }` — `error` is omitted.
                - Failure: `{ "success": false, "error": { "code": "...", "message": "..." } }` — `data` is omitted.
                Domain validation errors may include `error.details` (e.g. field map).

                **Authentication:** Send `Authorization: Bearer <access_token>` for protected routes.
                Obtain tokens from `POST /api/v1/auth/login`, `POST /api/v1/auth/setup-pin`,
                `POST /api/v1/auth/refresh`, or customer `POST .../verify-otp`. Operations that
                require JWT are marked with lock icon in Swagger UI / security in this spec.

                **Import into Postman:** With the API running, use *Import → Link* and paste
                `http://localhost:8080/v3/api-docs` (or your deployed host). Set collection
                variables if you replace the server URL below.
                """;

        return new OpenAPI()
                .info(new Info()
                        .title("CogniLogistic API")
                        .version("1.0.0")
                        .description(description)
                        .license(new License().name("Proprietary").url("https://cognilogistic.com")))
                .servers(List.of(new Server()
                        .url("http://localhost:8080")
                        .description("Change this in Postman after import if your API runs elsewhere.")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_JWT, new SecurityScheme()
                                .name(BEARER_JWT)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT access token from login, setup-pin, refresh, or portal verify-otp.")));
    }
}
