package ch.batbern.gateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3.1 configuration for API Gateway.
 *
 * Provides comprehensive API documentation with:
 * - API information (title, version, description)
 * - Contact information
 * - License information
 * - Server configuration
 *
 * Access documentation at:
 * - Swagger UI: /swagger-ui.html or /swagger-ui/index.html
 * - OpenAPI JSON: /v3/api-docs
 * - OpenAPI YAML: /v3/api-docs.yaml
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name:api-gateway}")
    private String applicationName;

    @Value("${api.version:1.0.0}")
    private String apiVersion;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("BATbern API Gateway")
                        .version(apiVersion)
                        .description("""
                                Unified API Gateway for BATbern Event Management Platform

                                This API provides consolidated access to all BATbern services with:
                                - Standardized query parameters (filter, sort, pagination, fields, include)
                                - Consistent error responses with correlation IDs
                                - API versioning via URL path (/api/v1, /api/v2, etc.)
                                - Comprehensive request validation
                                - Automatic pagination metadata

                                ## Query Parameters

                                All collection endpoints support:
                                - `filter`: JSON filter syntax (e.g., {"status":"published","votes":{"$gte":10}})
                                - `sort`: Sort specification (e.g., "-votes,+createdAt" for descending votes, ascending date)
                                - `page`: Page number (1-indexed, default: 1)
                                - `limit`: Items per page (default: 20, max: 100)
                                - `fields`: Comma-separated field list for sparse fieldsets (e.g., "id,title,status")
                                - `include`: Comma-separated relations to expand (e.g., "author,comments")

                                ## Response Format

                                Collections return:
                                ```json
                                {
                                  "data": [...],
                                  "pagination": {
                                    "page": 1,
                                    "limit": 20,
                                    "total": 145,
                                    "totalPages": 8,
                                    "hasNext": true,
                                    "hasPrev": false
                                  }
                                }
                                ```

                                Errors return:
                                ```json
                                {
                                  "timestamp": "2024-10-04T10:00:00Z",
                                  "path": "/api/v1/events",
                                  "status": 400,
                                  "error": "ERR_VALIDATION",
                                  "message": "Request validation failed",
                                  "correlationId": "abc-123-def",
                                  "severity": "MEDIUM",
                                  "details": {...}
                                }
                                ```
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
                                .url("http://localhost:8080")
                                .description("Local development server"),
                        new Server()
                                .url("https://api.staging.batbern.ch")
                                .description("Staging environment"),
                        new Server()
                                .url("https://api.batbern.ch")
                                .description("Production environment")
                ));
    }
}
