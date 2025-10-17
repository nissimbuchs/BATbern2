package ch.batbern.companyuser.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI configuration for Company-User Management Service
 * AC4: OpenAPI documentation setup
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name:company-user-management-service}")
    private String applicationName;

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("BATbern Company-User Management API")
                        .version("1.0.0")
                        .description("""
                                REST API for managing companies and users in the BATbern platform.

                                ## Features
                                - Company CRUD operations
                                - Company search with autocomplete (Caffeine-cached)
                                - Company logo management via S3 presigned URLs
                                - Swiss UID validation
                                - Company verification workflow
                                - Domain event publishing via EventBridge

                                ## Authentication
                                All endpoints require JWT authentication via AWS Cognito.
                                Include the Bearer token in the Authorization header.

                                ## Roles
                                - **ORGANIZER**: Full access (create, update, delete, verify companies)
                                - **SPEAKER**: Can create and update companies
                                - **PARTNER**: Can create and update companies
                                - **ATTENDEE**: Read-only access
                                """)
                        .contact(new Contact()
                                .name("BATbern Development Team")
                                .email("dev@batbern.ch")
                                .url("https://batbern.ch"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://batbern.ch/license")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local development server"),
                        new Server()
                                .url("https://api-staging.batbern.ch")
                                .description("Staging environment"),
                        new Server()
                                .url("https://api.batbern.ch")
                                .description("Production environment")
                ))
                .schemaRequirement("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT token from AWS Cognito"));
    }
}
