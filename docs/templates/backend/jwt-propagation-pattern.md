# JWT Token Propagation Pattern

**Category**: Backend - Microservices Communication
**Used in Stories**: 2.7 (Partner Coordination), 2.8.1 (Partner Directory)
**Last Updated**: 2025-01-20
**Source**: Extracted from `docs/guides/microservices-http-clients.md`

## Overview

When Service A calls Service B on behalf of a user, Service A must propagate the user's JWT token to Service B. This ensures proper authentication and authorization across microservice boundaries.

**Use this pattern when**:
- Making HTTP calls from one microservice to another
- The downstream service needs to know who the requesting user is
- You need to enforce authorization in the downstream service

## Prerequisites

- Spring Security with JWT authentication configured
- `RestTemplate` bean configured
- User's JWT token available in `SecurityContext` (from incoming request)

## Architecture

```
User Request
    │
    │ JWT: eyJhbGc...
    ▼
┌─────────────────┐
│  Service A       │
│  (Calling)       │
│                  │
│  SecurityContext │  ◄── Spring Security stores JWT here
│  └── Jwt token   │
└────────┬─────────┘
         │
         │ Extract token from SecurityContext
         │ Add "Authorization: Bearer eyJhbGc..."
         ▼
    HTTP Request
         │
         ▼
┌─────────────────┐
│  Service B       │
│  (Downstream)    │
│                  │
│  Validates JWT   │  ◄── Spring Security validates token
│  Enforces authz  │
└──────────────────┘
```

## Implementation Steps

### Step 1: Create HTTP Client with Token Propagation

```java
package ch.batbern.{service}.client;

import ch.batbern.{service}.dto.{TargetService}Response;
import ch.batbern.{service}.exception.{TargetService}NotFoundException;
import ch.batbern.{service}.exception.{TargetService}ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class {TargetService}Client {

    private final RestTemplate restTemplate;
    private final String {targetService}Url;

    public {TargetService}Client(
            RestTemplate restTemplate,
            @Value("${target-service.base-url}") String {targetService}Url) {
        this.restTemplate = restTemplate;
        this.{targetService}Url = {targetService}Url;
    }

    /**
     * Get entity by meaningful ID.
     * Cached for 15 minutes to reduce HTTP calls.
     * JWT token is automatically propagated to downstream service.
     */
    @Cacheable(value = "{targetService}ApiCache", key = "#entityId")
    public {TargetService}Response get{Entity}(String entityId) {
        String url = {targetService}Url + "/api/v1/{entities}/" + entityId;

        // Create headers with JWT token from SecurityContext
        HttpHeaders headers = createHeadersWithJwtToken();
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            return restTemplate.exchange(
                url, HttpMethod.GET, request, {TargetService}Response.class
            ).getBody();
        } catch (HttpClientErrorException.NotFound e) {
            // Entity doesn't exist - throw domain exception
            throw new {TargetService}NotFoundException("Entity not found: " + entityId);
        } catch (HttpServerErrorException e) {
            // Downstream service 5xx error - log and wrap
            log.error("Downstream service 5xx error for {}: {}", entityId, e.getMessage());
            throw new {TargetService}ServiceException("Downstream service error", e);
        } catch (ResourceAccessException e) {
            // Network error (connection timeout, DNS failure, etc.)
            log.error("Downstream service network error for {}: {}", entityId, e.getMessage());
            throw new {TargetService}ServiceException("Downstream service unavailable", e);
        }
    }

    /**
     * CRITICAL: Extract JWT token from SecurityContext and add to request headers.
     * This propagates the user's authentication to the downstream service.
     *
     * Without this, downstream service won't know who the user is!
     */
    private HttpHeaders createHeadersWithJwtToken() {
        HttpHeaders headers = new HttpHeaders();
        try {
            // Extract JWT from Spring Security context
            Object principal = SecurityContextHolder.getContext()
                    .getAuthentication()
                    .getPrincipal();

            if (principal instanceof Jwt jwt) {
                String token = jwt.getTokenValue();
                headers.set("Authorization", "Bearer " + token);
            }
        } catch (Exception e) {
            log.warn("Failed to extract JWT token: {}", e.getMessage());
            // Continue without token - downstream service will return 401 if needed
        }
        return headers;
    }
}
```

### Step 2: Configure RestTemplate Bean

```java
package ch.batbern.{service}.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class HttpClientConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))   // Connection timeout
            .setReadTimeout(Duration.ofSeconds(10))      // Read timeout
            .build();
    }
}
```

### Step 3: Configure Caffeine Cache for HTTP Responses

```java
package ch.batbern.{service}.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
            "{entities}",           // Entity cache (database queries)
            "{targetService}ApiCache"  // HTTP response cache
        );
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(1000)                      // Max 1000 entries per cache
            .expireAfterWrite(15, TimeUnit.MINUTES) // 15-minute TTL
            .recordStats());                        // Enable cache statistics
        return cacheManager;
    }
}
```

### Step 4: Add Configuration Properties

```yaml
# application.yml

# Target Service URL for HTTP Communication
target-service:
  base-url: ${TARGET_SERVICE_URL:http://{target-service}:8080}

# Spring Cache (Caffeine)
spring:
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=1000,expireAfterWrite=15m
```

### Step 5: Create Custom Exception Classes

```java
package ch.batbern.{service}.exception;

import ch.batbern.shared.exception.NotFoundException;

public class {TargetService}NotFoundException extends NotFoundException {
    public {TargetService}NotFoundException(String message) {
        super(message);
    }
}
```

```java
package ch.batbern.{service}.exception;

import ch.batbern.shared.exception.ServiceException;

public class {TargetService}ServiceException extends ServiceException {
    public {TargetService}ServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

These extend shared-kernel exceptions:
- `ch.batbern.shared.exception.NotFoundException` (404 errors)
- `ch.batbern.shared.exception.ServiceException` (5xx errors, network errors)

## Testing

### Unit Tests with @MockBean

```java
package ch.batbern.{service}.service;

import ch.batbern.{service}.client.{TargetService}Client;
import ch.batbern.{service}.dto.{TargetService}Response;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class {Service}Test {

    @Autowired
    private {Service} service;

    @MockBean
    private {TargetService}Client {targetService}Client;

    @Test
    void should_enrichEntityWithTargetData_when_includesTarget() {
        // Given
        String entityId = "ABC123";
        {TargetService}Response targetResponse = {TargetService}Response.builder()
            .id(entityId)
            .name("Test Entity")
            .build();

        when({targetService}Client.get{Entity}(entityId))
            .thenReturn(targetResponse);

        // When
        {Entity}Response response = service.get{Entity}(entityId, Set.of("target"));

        // Then
        assertThat(response.getTarget()).isNotNull();
        assertThat(response.getTarget().getName()).isEqualTo("Test Entity");
        verify({targetService}Client).get{Entity}(entityId);
    }

    @Test
    void should_throwException_when_targetNotFound() {
        // Given
        when({targetService}Client.get{Entity}("NonExistent"))
            .thenThrow(new {TargetService}NotFoundException("Entity not found"));

        // When/Then
        assertThatThrownBy(() -> service.validate{Entity}("NonExistent"))
            .isInstanceOf({TargetService}NotFoundException.class);
    }
}
```

### Integration Tests with TestConfiguration

```java
@TestConfiguration
public class HttpClientTestConfig {

    @Bean
    @Primary  // Override production bean
    public {TargetService}Client {targetService}Client() {
        return mock({TargetService}Client.class);
    }
}
```

## Common Pitfalls

### Pitfall 1: Forgetting to Propagate Token
**Problem**: Downstream service returns 401 Unauthorized
**Solution**: Always call `createHeadersWithJwtToken()` before making HTTP request

### Pitfall 2: Using UUIDs Instead of Meaningful IDs
**Problem**: Cache keys don't work across services
**Solution**: Use meaningful IDs (companyName, username) for both API paths and cache keys

### Pitfall 3: Not Handling Network Errors
**Problem**: Service crashes when downstream service is unavailable
**Solution**: Catch `ResourceAccessException` and wrap in custom `ServiceException`

### Pitfall 4: Caching Errors
**Problem**: 404 errors are cached, preventing retry
**Solution**: Only cache successful responses (don't use `@Cacheable` on methods that throw exceptions)

### Pitfall 5: Token Expiration
**Problem**: Cached responses outlive token validity
**Solution**: Keep cache TTL (15 minutes) shorter than token expiration (typically 1 hour)

## Environment Variables

### Local Development
```bash
export TARGET_SERVICE_URL=http://localhost:8081
```

### Staging/Production (ECS Service Connect)
```bash
# Set by infrastructure CDK - uses Service Connect DNS
export TARGET_SERVICE_URL=http://{target-service}:8080
```

## Story-Specific Adaptations

### Multi-Service Enrichment (Story 2.7)
If your service needs to call multiple downstream services:

```java
@Service
public class PartnerService {
    private final CompanyServiceClient companyServiceClient;
    private final UserServiceClient userServiceClient;

    public PartnerResponse getPartner(String companyName, Set<String> includes) {
        Partner partner = partnerRepository.findByCompanyName(companyName)
            .orElseThrow(() -> new NotFoundException("Partner not found"));

        PartnerResponse response = mapToResponse(partner);

        // Enrich with Company data if requested
        if (includes.contains("company")) {
            CompanyResponse company = companyServiceClient.getCompany(companyName);
            response.setCompany(company);
        }

        // Enrich contacts with User data if requested
        if (includes.contains("contacts")) {
            List<PartnerContact> contacts = partner.getContacts();
            for (PartnerContact contact : contacts) {
                UserResponse user = userServiceClient.getUser(contact.getUsername());
                // Merge user data into contact
            }
        }

        return response;
    }
}
```

### Conditional Enrichment
Use query parameters or request fields to control enrichment:

```java
// Example: GET /api/v1/partners/GoogleZH?includes=company,contacts
public PartnerResponse getPartner(
    @PathVariable String companyName,
    @RequestParam(defaultValue = "") Set<String> includes
) {
    return partnerService.getPartner(companyName, includes);
}
```

## Related Templates

- `spring-boot-service-foundation.md` - Service structure and layers
- `integration-test-pattern.md` - Testing HTTP clients with Testcontainers
- `flyway-migration-pattern.md` - Database schema for storing meaningful IDs

## References

- **Architecture**: ADR-003 Meaningful Identifiers in Public APIs
- **Architecture**: ADR-004 Factor User Fields from Domain Entities
- **Source Guide**: `docs/guides/microservices-http-clients.md`
- **Example**: Story 2.7 (Partner Coordination Service)
