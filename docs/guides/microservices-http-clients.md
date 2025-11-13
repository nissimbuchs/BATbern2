# Microservices HTTP Client Integration Guide

**Pattern**: ADR-003 Microservices Isolation with HTTP Communication
**Architecture Decision**: [ADR-003: Meaningful Identifiers in Public APIs](../architecture/ADR-003-meaningful-identifiers-public-apis.md)
**Last Updated**: 2025-01-08

---

## Table of Contents

- [Overview](#overview)
- [Microservices Isolation Rules](#microservices-isolation-rules)
- [HTTP Client Implementation](#http-client-implementation)
- [JWT Token Propagation](#jwt-token-propagation)
- [Caching Strategy](#caching-strategy)
- [Error Handling](#error-handling)
- [Testing Patterns](#testing-patterns)
- [Configuration](#configuration)
- [Related Documentation](#related-documentation)

---

## Overview

BATbern microservices follow strict isolation boundaries. Services **NEVER** access each other's databases directly. Instead, they communicate via HTTP APIs with meaningful identifiers.

**Key Principles**:
- ✅ Store meaningful IDs (companyName, username) for cross-service references
- ✅ Access other services via HTTP APIs
- ✅ Propagate JWT tokens for authentication
- ✅ Cache HTTP responses (15-minute TTL)
- ✅ Handle failures gracefully
- ❌ NO database foreign keys across services
- ❌ NO JPQL joins across services

---

## Microservices Isolation Rules

### Rule 1: Own Entities Use UUID PKs

```java
// ✅ CORRECT - UUID primary key for entities owned by THIS service
@Entity
@Table(name = "partners")
public class Partner {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;  // ✅ UUID PK for THIS service's entity

    @Column(name = "company_name", nullable = false, unique = true, length = 12)
    private String companyName;  // ✅ Meaningful ID for cross-service reference
}
```

### Rule 2: Cross-Service References Use Meaningful IDs (NOT UUIDs)

```java
// ✅ CORRECT - Store companyName, NOT companyId UUID
@Entity
public class Partner {
    private UUID id;
    private String companyName;  // ✅ Meaningful ID
    // NO: private UUID companyId;  ❌ WRONG
}

// ✅ CORRECT - Store username, NOT userId UUID
@Entity
public class PartnerContact {
    private UUID id;
    private UUID partnerId;  // ✅ Within-service FK OK
    private String username;  // ✅ Meaningful ID
    // NO: private UUID userId;  ❌ WRONG
}
```

### Rule 3: NO Database Foreign Keys Across Services

```sql
-- ✅ CORRECT - NO foreign key to companies table
CREATE TABLE partners (
    id UUID PRIMARY KEY,
    company_name VARCHAR(12) NOT NULL UNIQUE,  -- ✅ Meaningful ID
    -- NO FOREIGN KEY to companies.id
);

-- ❌ WRONG - foreign key across service boundary
CREATE TABLE partners (
    id UUID PRIMARY KEY,
    company_id UUID,  -- ❌ WRONG
    FOREIGN KEY (company_id) REFERENCES companies(id)  -- ❌ WRONG - violates microservices boundary
);
```

### Rule 4: Cross-Service Data Access via HTTP APIs

```java
// ✅ CORRECT - HTTP client for cross-service communication
@Service
public class PartnerService {
    private final CompanyServiceClient companyServiceClient;  // HTTP client

    public PartnerResponse getPartner(String companyName) {
        Partner partner = partnerRepository.findByCompanyName(companyName)
            .orElseThrow(() -> new NotFoundException("Partner not found"));

        // HTTP call to Company Service
        CompanyResponse company = companyServiceClient.getCompany(companyName);

        return mapToResponse(partner, company);
    }
}

// ❌ WRONG - JPQL join across service boundary
@Query("""
    SELECT new PartnerResponse(...)
    FROM Partner p
    INNER JOIN Company c ON p.companyId = c.id  -- ❌ WRONG - cross-service join
    """)
```

### Rule 5: Within-Service Relationships Can Use UUID FKs

```sql
-- ✅ CORRECT - Within-service FK OK
CREATE TABLE partner_contacts (
    id UUID PRIMARY KEY,
    partner_id UUID NOT NULL REFERENCES partners(id),  -- ✅ Same service
    username VARCHAR(100) NOT NULL,  -- ✅ Cross-service: meaningful ID
    UNIQUE(partner_id, username)
);
```

---

## HTTP Client Implementation

### Basic HTTP Client Pattern

**CompanyServiceClient** - Access Company Service via HTTP:

```java
package ch.batbern.partners.client;

import ch.batbern.partners.dto.CompanyResponse;
import ch.batbern.partners.exception.CompanyNotFoundException;
import ch.batbern.partners.exception.CompanyServiceException;
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
public class CompanyServiceClient {

    private final RestTemplate restTemplate;
    private final String companyServiceUrl;

    public CompanyServiceClient(
            RestTemplate restTemplate,
            @Value("${company-service.base-url}") String companyServiceUrl) {
        this.restTemplate = restTemplate;
        this.companyServiceUrl = companyServiceUrl;
    }

    /**
     * Get company by meaningful ID (companyName).
     * Cached for 15 minutes to reduce HTTP calls.
     */
    @Cacheable(value = "companyApiCache", key = "#companyName")
    public CompanyResponse getCompany(String companyName) {
        String url = companyServiceUrl + "/api/v1/companies/" + companyName;
        HttpHeaders headers = createHeadersWithJwtToken();
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            return restTemplate.exchange(
                url, HttpMethod.GET, request, CompanyResponse.class
            ).getBody();
        } catch (HttpClientErrorException.NotFound e) {
            // Company doesn't exist - throw domain exception
            throw new CompanyNotFoundException("Company not found: " + companyName);
        } catch (HttpServerErrorException e) {
            // Company service 5xx error - log and wrap
            log.error("Company service 5xx error for {}: {}", companyName, e.getMessage());
            throw new CompanyServiceException("Company service error", e);
        } catch (ResourceAccessException e) {
            // Network error (connection timeout, DNS failure, etc.)
            log.error("Company service network error for {}: {}", companyName, e.getMessage());
            throw new CompanyServiceException("Company service unavailable", e);
        }
    }

    /**
     * Extract JWT token from SecurityContext and add to request headers.
     * This propagates the user's authentication to the downstream service.
     */
    private HttpHeaders createHeadersWithJwtToken() {
        HttpHeaders headers = new HttpHeaders();
        try {
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

**UserServiceClient** - Access User Service via HTTP:

```java
package ch.batbern.partners.client;

import ch.batbern.partners.dto.UserResponse;
import ch.batbern.partners.exception.UserNotFoundException;
import ch.batbern.partners.exception.UserServiceException;
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
public class UserServiceClient {

    private final RestTemplate restTemplate;
    private final String userServiceUrl;

    public UserServiceClient(
            RestTemplate restTemplate,
            @Value("${user-service.base-url}") String userServiceUrl) {
        this.restTemplate = restTemplate;
        this.userServiceUrl = userServiceUrl;
    }

    /**
     * Get user by meaningful ID (username).
     * Cached for 15 minutes to reduce HTTP calls.
     */
    @Cacheable(value = "userApiCache", key = "#username")
    public UserResponse getUser(String username) {
        String url = userServiceUrl + "/api/v1/users/" + username;
        HttpHeaders headers = createHeadersWithJwtToken();
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            return restTemplate.exchange(
                url, HttpMethod.GET, request, UserResponse.class
            ).getBody();
        } catch (HttpClientErrorException.NotFound e) {
            throw new UserNotFoundException("User not found: " + username);
        } catch (HttpServerErrorException e) {
            log.error("User service 5xx error for {}: {}", username, e.getMessage());
            throw new UserServiceException("User service error", e);
        } catch (ResourceAccessException e) {
            log.error("User service network error for {}: {}", username, e.getMessage());
            throw new UserServiceException("User service unavailable", e);
        }
    }

    /**
     * Extract JWT token from SecurityContext and add to request headers.
     */
    private HttpHeaders createHeadersWithJwtToken() {
        HttpHeaders headers = new HttpHeaders();
        try {
            Object principal = SecurityContextHolder.getContext()
                    .getAuthentication()
                    .getPrincipal();

            if (principal instanceof Jwt jwt) {
                String token = jwt.getTokenValue();
                headers.set("Authorization", "Bearer " + token);
            }
        } catch (Exception e) {
            log.warn("Failed to extract JWT token: {}", e.getMessage());
        }
        return headers;
    }
}
```

### RestTemplate Bean Configuration

```java
package ch.batbern.partners.config;

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

---

## JWT Token Propagation

### Why Token Propagation?

When Service A calls Service B on behalf of a user:
1. Service A has the user's JWT token (from incoming request)
2. Service A must pass this token to Service B
3. Service B validates the token and enforces authorization

**Without token propagation**: Service B doesn't know who the user is!

### How Token Propagation Works

```
User Request
    │
    │ JWT: eyJhbGc...
    ▼
┌─────────────────┐
│  Partner Service │
│  (Service A)     │
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
│  Company Service │
│  (Service B)     │
│                  │
│  Validates JWT   │  ◄── Spring Security validates token
│  Enforces authz  │
└──────────────────┘
```

### Implementation Pattern

```java
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
```

### Usage in HTTP Call

```java
public CompanyResponse getCompany(String companyName) {
    String url = companyServiceUrl + "/api/v1/companies/" + companyName;

    // Create headers with JWT token
    HttpHeaders headers = createHeadersWithJwtToken();
    HttpEntity<Void> request = new HttpEntity<>(headers);

    // Token is automatically included in the request
    return restTemplate.exchange(
        url, HttpMethod.GET, request, CompanyResponse.class
    ).getBody();
}
```

---

## Caching Strategy

### Why Cache HTTP Responses?

- **Performance**: Reduce latency (avoid network round-trip)
- **Reliability**: Service still works if dependency is slow
- **Cost**: Reduce number of HTTP calls

### Caffeine Cache Configuration

```java
package ch.batbern.partners.config;

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
            "partners",         // Partner entity cache
            "companyApiCache",  // Company Service HTTP response cache
            "userApiCache"      // User Service HTTP response cache
        );
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(1000)                      // Max 1000 entries per cache
            .expireAfterWrite(15, TimeUnit.MINUTES) // 15-minute TTL
            .recordStats());                        // Enable cache statistics
        return cacheManager;
    }
}
```

### Usage with @Cacheable

```java
@Cacheable(value = "companyApiCache", key = "#companyName")
public CompanyResponse getCompany(String companyName) {
    // This method is only called if cache miss
    // On cache hit, cached value is returned without executing method
    return restTemplate.exchange(...).getBody();
}
```

### Cache Key Strategy

- Use meaningful IDs as cache keys (companyName, username)
- **NOT** UUIDs (UUIDs don't work across services)

```java
// ✅ CORRECT - meaningful ID as cache key
@Cacheable(value = "companyApiCache", key = "#companyName")
public CompanyResponse getCompany(String companyName) { ... }

// ❌ WRONG - UUID as cache key (UUID is internal to Company Service)
@Cacheable(value = "companyApiCache", key = "#companyId")
public CompanyResponse getCompany(UUID companyId) { ... }
```

---

## Error Handling

### Three Categories of Errors

1. **Client Errors (4xx)**: Request is invalid
   - 404 Not Found → Throw domain exception (NotFoundException)
   - 400 Bad Request → Throw validation exception

2. **Server Errors (5xx)**: Downstream service failure
   - 500 Internal Server Error → Log and wrap in ServiceException
   - 503 Service Unavailable → Log and wrap in ServiceException

3. **Network Errors**: Connection failure
   - Connection timeout → Throw ServiceException
   - DNS failure → Throw ServiceException

### Error Handling Pattern

```java
try {
    return restTemplate.exchange(url, HttpMethod.GET, request, CompanyResponse.class).getBody();

} catch (HttpClientErrorException.NotFound e) {
    // 404 - Company doesn't exist (expected case)
    throw new CompanyNotFoundException("Company not found: " + companyName);

} catch (HttpServerErrorException e) {
    // 5xx - Company service error (unexpected, but recoverable)
    log.error("Company service 5xx error for {}: {}", companyName, e.getMessage());
    throw new CompanyServiceException("Company service error", e);

} catch (ResourceAccessException e) {
    // Network error (connection timeout, DNS failure, etc.)
    log.error("Company service network error for {}: {}", companyName, e.getMessage());
    throw new CompanyServiceException("Company service unavailable", e);
}
```

### Custom Exception Classes

```java
// Domain exception for "Company not found"
public class CompanyNotFoundException extends NotFoundException {
    public CompanyNotFoundException(String message) {
        super(message);
    }
}

// Service exception for Company Service failures
public class CompanyServiceException extends ServiceException {
    public CompanyServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

These exceptions extend shared-kernel exceptions:
- `ch.batbern.shared.exception.NotFoundException`
- `ch.batbern.shared.exception.ServiceException`

---

## Testing Patterns

### Unit Tests with @MockBean

Use Spring `@MockBean` to mock HTTP clients (NOT WireMock):

```java
package ch.batbern.partners.service;

import ch.batbern.partners.client.CompanyServiceClient;
import ch.batbern.partners.client.UserServiceClient;
import ch.batbern.partners.dto.CompanyResponse;
import ch.batbern.partners.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class PartnerServiceTest {

    @Autowired
    private PartnerService partnerService;

    @MockBean
    private CompanyServiceClient companyServiceClient;

    @MockBean
    private UserServiceClient userServiceClient;

    @Test
    void should_enrichPartnerWithCompanyData_when_includesCompany() {
        // Given
        String companyName = "GoogleZH";
        CompanyResponse companyResponse = CompanyResponse.builder()
            .name("GoogleZH")
            .displayName("Google Zurich")
            .build();

        when(companyServiceClient.getCompany(companyName))
            .thenReturn(companyResponse);

        // When
        PartnerResponse response = partnerService.getPartner(companyName, Set.of("company"));

        // Then
        assertThat(response.getCompany()).isNotNull();
        assertThat(response.getCompany().getDisplayName()).isEqualTo("Google Zurich");
        verify(companyServiceClient).getCompany(companyName);
    }

    @Test
    void should_enrichContactWithUserData_when_addingContact() {
        // Given
        String username = "john.doe";
        UserResponse userResponse = UserResponse.builder()
            .username("john.doe")
            .email("john.doe@example.com")
            .firstName("John")
            .lastName("Doe")
            .build();

        when(userServiceClient.getUser(username))
            .thenReturn(userResponse);

        // When
        PartnerContactResponse response = partnerService.addContact(partnerId, username, ContactRole.PRIMARY);

        // Then
        assertThat(response.getEmail()).isEqualTo("john.doe@example.com");
        assertThat(response.getFirstName()).isEqualTo("John");
        verify(userServiceClient).getUser(username);
    }

    @Test
    void should_throwException_when_companyNotFound() {
        // Given
        when(companyServiceClient.getCompany("NonExistent"))
            .thenThrow(new CompanyNotFoundException("Company not found"));

        // When/Then
        assertThatThrownBy(() -> partnerService.validateCompany("NonExistent"))
            .isInstanceOf(CompanyNotFoundException.class);
    }
}
```

### Integration Tests with TestConfiguration

```java
@TestConfiguration
public class HttpClientTestConfig {

    @Bean
    @Primary  // Override production bean
    public CompanyServiceClient companyServiceClient() {
        return mock(CompanyServiceClient.class);
    }

    @Bean
    @Primary
    public UserServiceClient userServiceClient() {
        return mock(UserServiceClient.class);
    }
}
```

---

## Configuration

### application.yml

```yaml
# HTTP Service URLs for Microservices Communication (ADR-003)
# Service Connect DNS: http://{service-name}:8080
company-service:
  base-url: ${COMPANY_USER_MANAGEMENT_SERVICE_URL:http://company-user-management:8080}

user-service:
  base-url: ${COMPANY_USER_MANAGEMENT_SERVICE_URL:http://company-user-management:8080}

# Spring Cache (Caffeine)
spring:
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=1000,expireAfterWrite=15m
```

### Environment Variables

**Local Development**:
```bash
export COMPANY_USER_MANAGEMENT_SERVICE_URL=http://localhost:8081
```

**Staging/Production** (ECS Service Connect - set by infrastructure CDK):
```bash
export COMPANY_USER_MANAGEMENT_SERVICE_URL=http://company-user-management:8080
```

**Note**: Both Company Service and User Service are part of the `company-user-management-service` microservice, so they share the same base URL.

---

## Related Documentation

### Architecture Decisions

- **[ADR-003: Meaningful Identifiers in Public APIs](../architecture/ADR-003-meaningful-identifiers-public-apis.md)** - Microservices isolation rules
- **[ADR-004: Factor User Fields from Domain Entities](../architecture/ADR-004-factor-user-fields-from-domain-entities.md)** - HTTP enrichment pattern
- **[ADR-006: OpenAPI Contract-First Code Generation](../architecture/ADR-006-openapi-contract-first-code-generation.md)** - API contracts

### Related Guides

- **[OpenAPI Code Generation Guide](./openapi-code-generation.md)** - Generate DTOs for HTTP responses
- **[Service Foundation Pattern](./service-foundation-pattern.md)** - Service structure and layers

### Implementation Examples

- **Event Management Service** → User Service (enrichment pattern)
- **Partner Coordination Service** → Company Service + User Service (Story 2.7)
- **Speaker Coordination Service** → User Service (future)

---

**Last Updated**: 2025-01-08 (Story 2.7 - aligned with ECS Service Connect)
**Maintained By**: Development Team
