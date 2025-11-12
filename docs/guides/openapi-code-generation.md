# OpenAPI Code Generation Guide

**Pattern**: Hybrid Contract-First Approach
**Architecture Decision**: [ADR-006: OpenAPI Contract-First Code Generation](../architecture/ADR-006-openapi-contract-first-code-generation.md)
**Last Updated**: 2025-11-09

---

## Table of Contents

- [Overview](#overview)
- [Philosophy](#philosophy)
- [Backend: Generated Interfaces + Manual Implementation](#backend-generated-interfaces--manual-implementation)
  - [What Gets Generated](#what-gets-generated)
  - [Gradle Configuration](#gradle-configuration)
  - [Implementation Pattern](#implementation-pattern)
- [Service-to-Service Communication: Generated Client DTOs](#service-to-service-communication-generated-client-dtos)
  - [Pattern: Generate DTOs Only, Manual HTTP Client](#pattern-generate-dtos-only-manual-http-client)
  - [Gradle Configuration for Client DTOs](#gradle-configuration-for-client-dtos)
  - [Manual HTTP Client Implementation](#manual-http-client-implementation)
  - [Key Benefits of This Pattern](#key-benefits-of-this-pattern)
  - [Testing with Generated Client DTOs](#testing-with-generated-client-dtos)
  - [When to Use This Pattern](#when-to-use-this-pattern)
- [Frontend: Generated TypeScript Types](#frontend-generated-typescript-types)
- [Shared-Kernel Integration](#shared-kernel-integration)
- [Benefits](#benefits)
- [Anti-Patterns](#anti-patterns)
- [Related Documentation](#related-documentation)

---

## Overview

BATbern uses OpenAPI specifications as the **single source of truth** for API contracts. Both frontend and backend generate code from the same OpenAPI specs, ensuring type safety and contract compliance across the stack.

**Key Principle**: OpenAPI spec → Generated code → Manual implementation

This guide explains the hybrid contract-first pattern used across all BATbern microservices.

---

## Philosophy

### Single Source of Truth

```
                    OpenAPI Specification
                  (docs/api/*.openapi.yml)
                           │
          ┌────────────────┼────────────────┐
          │                                 │
          ▼                                 ▼
    Backend Code Gen                 Frontend Code Gen
    (Java Interfaces                 (TypeScript Types
     & DTOs)                          & Paths)
          │                                 │
          ▼                                 ▼
    Manual Implementation             Service Layer
    (Controllers, Services)           (API Clients)
```

**Benefits**:
- ✅ Frontend types match backend DTOs (guaranteed)
- ✅ Build fails if implementation doesn't match spec
- ✅ Documentation always current
- ✅ API design happens before implementation

### Hybrid Approach

We generate **interfaces and DTOs** but write **implementations manually**:

- **Generated**: API interfaces (`UsersApi.java`), DTOs (`UserResponse.java`)
- **Manual**: Controllers (implement generated interfaces), Services, Repositories, Business logic

This gives us **type safety** while maintaining **control over implementation**.

---

## Backend: Generated Interfaces + Manual Implementation

### What Gets Generated

For each OpenAPI specification (e.g., `docs/api/users-api.openapi.yml`):

```
build/generated/src/main/java/
└── ch/batbern/{service}/
    ├── api/generated/          # API Interfaces
    │   ├── UsersApi.java       # GET /users, POST /users, etc.
    │   ├── CompaniesApi.java   # Company endpoints
    │   └── ...
    └── dto/generated/          # Data Transfer Objects
        ├── UserResponse.java
        ├── CreateUserRequest.java
        ├── UpdateUserRequest.java
        ├── PaginatedUserResponse.java
        └── ...
```

**Location**: `build/generated/` (NOT committed to Git, regenerated on every build)

### What Gets Imported from Shared-Kernel

These types are **imported, NOT generated**:

```java
import ch.batbern.shared.dto.ErrorResponse;
import ch.batbern.shared.api.PaginationMetadata;
import ch.batbern.shared.dto.PaginatedResponse;
```

This ensures consistency across all services.

### Gradle Configuration

Add to `services/{your-service}/build.gradle`:

```gradle
plugins {
    id 'org.springframework.boot' version '3.2.0'
    id 'io.spring.dependency-management' version '1.1.4'
    id 'java'
    id 'org.openapi.generator' version '7.2.0'  // OpenAPI Generator plugin
}

dependencies {
    implementation project(':shared-kernel')

    // Spring Boot dependencies
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'

    // For generated code
    implementation 'io.swagger.core.v3:swagger-annotations:2.2.20'
    implementation 'org.openapitools:jackson-databind-nullable:0.2.6'

    // Other dependencies...
}

openApiGenerate {
    generatorName = "spring"
    inputSpec = "$rootDir/docs/api/{your-service}-api.openapi.yml".toString()
    outputDir = "$buildDir/generated".toString()
    apiPackage = "ch.batbern.{yourservice}.api.generated"
    modelPackage = "ch.batbern.{yourservice}.dto.generated"

    // Import shared-kernel types (NOT generated)
    importMappings = [
        'ErrorResponse': 'ch.batbern.shared.dto.ErrorResponse',
        'PaginationMetadata': 'ch.batbern.shared.api.PaginationMetadata',
        'PaginatedResponse': 'ch.batbern.shared.dto.PaginatedResponse'
    ]

    typeMappings = [
        'ErrorResponse': 'ch.batbern.shared.dto.ErrorResponse',
        'PaginationMetadata': 'ch.batbern.shared.api.PaginationMetadata'
    ]

    schemaMappings = [
        'ErrorResponse': 'ch.batbern.shared.dto.ErrorResponse',
        'PaginationMetadata': 'ch.batbern.shared.api.PaginationMetadata'
    ]

    configOptions = [
        interfaceOnly: "true",              // Only generate interfaces, not implementations
        useTags: "true",                    // Organize APIs by OpenAPI tags
        useSpringBoot3: "true",             // Use Spring Boot 3 annotations
        useBeanValidation: "true",          // Include @Valid, @NotNull annotations
        useJakartaEe: "true",               // Use Jakarta EE (Spring Boot 3 requirement)
        dateLibrary: "java8",               // Use java.time.* types
        skipDefaultInterface: "true"        // No default methods in interfaces
    ]
}

// Add generated sources to compilation
sourceSets {
    main {
        java {
            srcDirs += "$buildDir/generated/src/main/java"
        }
    }
}

// Generate before compilation
compileJava.dependsOn tasks.openApiGenerate

// Clean generated code when running clean
clean {
    delete "$buildDir/generated"
}
```

### How to Regenerate

```bash
# Automatic (preferred) - runs during build
./gradlew :services:{your-service}:build

# Manual generation only
./gradlew :services:{your-service}:openApiGenerate

# Clean and regenerate
./gradlew :services:{your-service}:clean openApiGenerate
```

### Implementation Pattern

#### Step 1: Controller Implements Generated Interface

```java
package ch.batbern.yourservice.controller;

import ch.batbern.yourservice.api.generated.UsersApi;  // Generated interface
import ch.batbern.yourservice.dto.generated.*;          // Generated DTOs
import ch.batbern.yourservice.service.UserService;
import ch.batbern.shared.api.*;                         // Shared-kernel utilities
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequiredArgsConstructor
public class UserController implements UsersApi {  // Implement generated interface

    private final UserService userService;
    private final IncludeParser includeParser;  // From shared-kernel
    private final FilterParser filterParser;    // From shared-kernel
    private final SortParser sortParser;        // From shared-kernel

    @Override  // Method signature enforced by generated interface
    public ResponseEntity<UserResponse> getCurrentUser(Optional<String> include) {
        Set<String> includes = includeParser.parse(include.orElse(null));
        UserResponse user = userService.getCurrentUser(includes);
        return ResponseEntity.ok(user);
    }

    @Override
    public ResponseEntity<UserResponse> updateCurrentUser(UpdateUserRequest request) {
        // Use generated DTO - type safety guaranteed
        UserResponse updated = userService.updateCurrentUser(request);
        return ResponseEntity.ok(updated);
    }

    @Override
    public ResponseEntity<PaginatedUserResponse> listUsers(
            Optional<String> filter,
            Optional<String> sort,
            Optional<Integer> page,
            Optional<Integer> limit,
            Optional<String> include) {

        // Parse using shared-kernel utilities
        FilterCriteria filterCriteria = filterParser.parse(filter.orElse(null));
        SortCriteria sortCriteria = sortParser.parse(sort.orElse(null));
        Set<String> includes = includeParser.parse(include.orElse(null));

        PaginatedUserResponse response = userService.listUsers(
            filterCriteria,
            sortCriteria,
            page.orElse(1),
            limit.orElse(20),
            includes
        );

        return ResponseEntity.ok(response);
    }
}
```

**Key Points**:
- `implements UsersApi` - Generated interface enforces method signatures
- Type safety: Can't return wrong DTO or accept wrong request type
- Compilation fails if OpenAPI spec changes and controller doesn't match

#### Step 2: Service Layer Uses Generated DTOs

```java
package ch.batbern.yourservice.service;

import ch.batbern.yourservice.dto.generated.*;  // Generated DTOs
import ch.batbern.yourservice.domain.User;
import ch.batbern.yourservice.repository.UserRepository;
import ch.batbern.shared.events.DomainEventPublisher;  // From shared-kernel
import ch.batbern.shared.exception.NotFoundException; // From shared-kernel
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final DomainEventPublisher eventPublisher;  // From shared-kernel

    public UserResponse getCurrentUser(Set<String> includes) {
        User user = getCurrentUserEntity();
        return mapToResponse(user, includes);  // Map to generated DTO
    }

    public UserResponse updateCurrentUser(UpdateUserRequest request) {
        // UpdateUserRequest is generated from OpenAPI spec
        User user = getCurrentUserEntity();

        // Update from generated DTO
        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }

        User saved = userRepository.save(user);

        // Publish event using shared-kernel
        eventPublisher.publishEvent(new UserUpdatedEvent(
            saved.getId(),
            saved.getEmail()
        ));

        return mapToResponse(saved, null);  // Return generated DTO
    }

    private UserResponse mapToResponse(User user, Set<String> includes) {
        // Map JPA entity to generated DTO
        UserResponse response = new UserResponse();  // Generated DTO
        response.setId(user.getId());
        response.setEmail(user.getEmail());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setRoles(user.getRoles().stream()
            .map(role -> UserResponse.RolesEnum.fromValue(role.name()))
            .collect(Collectors.toList()));
        response.setIsActive(user.isActive());
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());

        // Handle resource expansion (includes)
        if (includes != null && includes.contains("company")) {
            response.setCompany(companyServiceClient.getCompany(user.getCompanyId()));
        }

        return response;
    }

    private User getCurrentUserEntity() {
        UUID userId = SecurityContextHelper.getCurrentUser().getUserId();
        return userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found: " + userId));
    }
}
```

#### Step 3: Exception Handling with Shared-Kernel ErrorResponse

```java
package ch.batbern.yourservice.exception;

import ch.batbern.shared.dto.ErrorResponse;  // From shared-kernel
import ch.batbern.shared.exception.*;        // From shared-kernel
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Instant;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)  // From shared-kernel
    public ResponseEntity<ErrorResponse> handleNotFoundException(
            NotFoundException ex,
            HttpServletRequest request) {

        ErrorResponse error = ErrorResponse.builder()
            .timestamp(Instant.now())
            .path(request.getRequestURI())
            .status(HttpStatus.NOT_FOUND.value())
            .error("NOT_FOUND")
            .message(ex.getMessage())
            .correlationId(RequestContext.getCurrentRequestId())
            .severity("WARNING")
            .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    // More exception handlers...
}
```

---

## Service-to-Service Communication: Generated Client DTOs

### Pattern: Generate DTOs Only, Manual HTTP Client

When a service needs to call another service via HTTP (e.g., Partner Service calling User Service), we generate **DTOs only** from the upstream service's OpenAPI spec, then write manual HTTP client implementations.

**Why This Approach**:
- ✅ **Type Safety**: Generated DTOs guarantee compatibility with upstream API contract
- ✅ **DRY Principle**: No manual DTO creation, types come from source of truth
- ✅ **Full Control**: Manual HTTP clients allow custom caching, JWT propagation, error handling
- ✅ **Build-Time Validation**: Breaking changes in upstream API fail compilation immediately
- ✅ **No Runtime Overhead**: No heavyweight client libraries, just POJOs with Jackson

### Gradle Configuration for Client DTOs

Add separate generation tasks for each upstream service:

```gradle
// Generate DTOs from Company Service API (upstream dependency)
task generateCompanyClientDtos(type: org.openapitools.generator.gradle.plugin.tasks.GenerateTask) {
    generatorName = "java"
    library = "native"              // Use native java library (Jackson-compatible)
    inputSpec = "$rootDir/docs/api/companies-api.openapi.yml".toString()
    outputDir = "$buildDir/generated-client-company".toString()
    modelPackage = "ch.batbern.partners.client.company.dto"

    globalProperties = [
        models: "",                 // Generate models only
        apis: "false",              // Skip API client generation
        supportingFiles: "false"    // Skip supporting files (ApiClient, etc.)
    ]

    configOptions = [
        dateLibrary: "java8",
        useJakartaEe: "true",
        serializationLibrary: "jackson",
        openApiNullable: "false"
    ]
}

// Generate DTOs from User Service API (upstream dependency)
task generateUserClientDtos(type: org.openapitools.generator.gradle.plugin.tasks.GenerateTask) {
    generatorName = "java"
    library = "native"
    inputSpec = "$rootDir/docs/api/users-api.openapi.yml".toString()
    outputDir = "$buildDir/generated-client-user".toString()
    modelPackage = "ch.batbern.partners.client.user.dto"

    globalProperties = [
        models: "",
        apis: "false",
        supportingFiles: "false"
    ]

    configOptions = [
        dateLibrary: "java8",
        useJakartaEe: "true",
        serializationLibrary: "jackson",
        openApiNullable: "false"
    ]
}

// Add generated client DTOs to source sets
sourceSets {
    main {
        java {
            srcDirs += "$buildDir/generated/src/main/java"                    // Own API
            srcDirs += "$buildDir/generated-client-company/src/main/java"     // Company DTOs
            srcDirs += "$buildDir/generated-client-user/src/main/java"        // User DTOs
        }
    }
}

// Generate client DTOs before compilation
compileJava.dependsOn tasks.generateCompanyClientDtos
compileJava.dependsOn tasks.generateUserClientDtos
compileJava.dependsOn tasks.openApiGenerate
```

### Generated Client DTO Structure

```
build/
├── generated/                              # Own service API
│   └── src/main/java/ch/batbern/partners/
│       ├── api/generated/                  # Own API interfaces
│       └── dto/generated/                  # Own DTOs
│
├── generated-client-company/               # Company Service DTOs
│   └── src/main/java/ch/batbern/partners/client/company/dto/
│       ├── CompanyResponse.java            # Generated from companies-api.openapi.yml
│       ├── Logo.java
│       └── ...
│
└── generated-client-user/                  # User Service DTOs
    └── src/main/java/ch/batbern/partners/client/user/dto/
        ├── UserResponse.java               # Generated from users-api.openapi.yml
        └── ...
```

### Manual HTTP Client Implementation

**Step 1: Define Client Interface**

```java
package ch.batbern.partners.client;

import ch.batbern.partners.client.user.dto.UserResponse;  // Generated DTO

/**
 * HTTP client for User Service API.
 * Uses generated DTOs for type safety.
 */
public interface UserServiceClient {
    /**
     * Get user profile by username.
     * @throws UserNotFoundException if user not found (HTTP 404)
     * @throws UserServiceException if service unavailable
     */
    UserResponse getUserByUsername(String username);

    /**
     * Alias for clarity in business logic.
     */
    default UserResponse getUserProfile(String username) {
        return getUserByUsername(username);
    }
}
```

**Step 2: Implement with RestTemplate + Generated DTOs**

```java
package ch.batbern.partners.client.impl;

import ch.batbern.partners.client.UserServiceClient;
import ch.batbern.partners.client.user.dto.UserResponse;  // Generated DTO
import ch.batbern.partners.exception.UserNotFoundException;
import ch.batbern.partners.exception.UserServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class UserServiceClientImpl implements UserServiceClient {

    private final RestTemplate restTemplate;
    private final String userServiceBaseUrl;

    public UserServiceClientImpl(
            RestTemplate restTemplate,
            @Value("${user-service.base-url}") String userServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.userServiceBaseUrl = userServiceBaseUrl;
    }

    @Override
    @Cacheable(value = "userApiCache", key = "#username")  // 15min TTL
    public UserResponse getUserByUsername(String username) {
        log.debug("HTTP GET User Service: /api/v1/users/{}", username);

        String url = userServiceBaseUrl + "/api/v1/users/" + username;

        try {
            // Generated UserResponse DTO - guaranteed type safety
            return restTemplate.getForObject(url, UserResponse.class);

        } catch (HttpClientErrorException.NotFound e) {
            log.warn("User not found: {}", username);
            throw new UserNotFoundException("User not found: " + username);

        } catch (Exception e) {
            log.error("User Service API error for username {}: {}", username, e.getMessage());
            throw new UserServiceException("Failed to fetch user: " + username, e);
        }
    }
}
```

**Step 3: Configure RestTemplate with JWT Propagation**

```java
package ch.batbern.partners.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .interceptors(jwtPropagationInterceptor())
            .build();
    }

    /**
     * Propagate JWT token from current request to downstream service calls.
     * Critical for maintaining authentication context across services.
     */
    private ClientHttpRequestInterceptor jwtPropagationInterceptor() {
        return (request, body, execution) -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
                String token = jwt.getTokenValue();
                request.getHeaders().setBearerAuth(token);
            }

            return execution.execute(request, body);
        };
    }
}
```

**Step 4: Use in Service Layer with HTTP Enrichment**

```java
package ch.batbern.partners.service;

import ch.batbern.partners.client.UserServiceClient;
import ch.batbern.partners.client.user.dto.UserResponse;  // Generated DTO
import ch.batbern.partners.domain.PartnerContact;
import ch.batbern.partners.dto.generated.PartnerContactResponse;  // Own generated DTO
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PartnerContactService {

    private final UserServiceClient userServiceClient;

    /**
     * Enrich contact with User Service data (HTTP enrichment per ADR-004).
     * Uses generated UserResponse DTO for type safety.
     */
    private PartnerContactResponse enrichContactWithUserData(PartnerContact contact) {
        PartnerContactResponse response = new PartnerContactResponse();
        response.setUsername(contact.getUsername());
        response.setContactRole(contact.getContactRole());

        try {
            // Generated UserResponse DTO - compiler enforces correct field access
            UserResponse userProfile = userServiceClient.getUserProfile(contact.getUsername());

            // Type-safe access to generated DTO fields
            response.setEmail(userProfile.getEmail());
            response.setFirstName(userProfile.getFirstName());
            response.setLastName(userProfile.getLastName());

            // Handle type conversions (UserResponse.profilePictureUrl is URI)
            if (userProfile.getProfilePictureUrl() != null) {
                response.setProfilePictureUrl(userProfile.getProfilePictureUrl().toString());
            }

        } catch (Exception e) {
            log.warn("Failed to enrich contact {}: {}", contact.getUsername(), e.getMessage());
            // Graceful degradation - return response without enrichment
        }

        return response;
    }
}
```

### Key Benefits of This Pattern

✅ **Type Safety**: Generated DTOs from upstream API spec guarantee compatibility
```java
// ✅ Compiler enforces correct field names and types
UserResponse user = userServiceClient.getUserProfile("john.doe");
String email = user.getEmail();              // Correct field name
String firstName = user.getFirstName();      // Correct field name

// ❌ Would fail compilation if upstream API changes
String name = user.getName();                // Compilation error if field doesn't exist
```

✅ **Breaking Change Detection**: Build fails if upstream API changes incompatibly
```bash
# Example: Upstream service renames field from 'email' to 'emailAddress'
# Your service fails to compile immediately, not at runtime
./gradlew build
> Compilation error: cannot find symbol user.getEmail()
```

✅ **Full Control Over HTTP Logic**: Manual clients allow custom:
- **Caching**: `@Cacheable` with configurable TTL
- **JWT Propagation**: Request interceptors for authentication
- **Error Handling**: Service-specific exception mapping
- **Retry Logic**: Resilience4j integration
- **Circuit Breakers**: Fault tolerance patterns

✅ **No Manual DTO Maintenance**: DTOs regenerated automatically on every build

✅ **Documentation Clarity**: HTTP client interface documents API contract

### Alternative Approach: Full Client Generation (Not Recommended)

You could generate full API clients with `apis: "true"`, but this loses control:

```gradle
// ❌ NOT RECOMMENDED - generates full client with limited customization
task generateUserClient(type: org.openapitools.generator.gradle.plugin.tasks.GenerateTask) {
    generatorName = "java"
    library = "resttemplate"
    globalProperties = [
        apis: "true",          // Generates ApiClient, Configuration, etc.
        models: ""
    ]
    // Generated client is opinionated, harder to customize
}
```

**Why Manual HTTP Clients Are Better**:
- ❌ Generated clients often include heavyweight dependencies
- ❌ Less control over caching, JWT propagation, error handling
- ❌ Generated code harder to debug and customize
- ✅ DTOs-only approach gives type safety WITHOUT losing implementation control

### Testing with Generated Client DTOs

**Mock HTTP Client in Integration Tests**:

```java
@SpringBootTest
@AutoConfigureMockMvc
class PartnerContactControllerIntegrationTest {

    @MockBean
    private UserServiceClient userServiceClient;  // Mock HTTP client

    @Test
    void should_enrichContactWithUserData_when_httpCallSucceeds() throws Exception {
        // Mock User Service response using generated DTO
        UserResponse userProfile = new UserResponse();  // Generated DTO
        userProfile.setId("john.doe");
        userProfile.setEmail("john.doe@example.com");
        userProfile.setFirstName("John");
        userProfile.setLastName("Doe");
        userProfile.setProfilePictureUrl(URI.create("https://example.com/john.jpg"));

        when(userServiceClient.getUserProfile("john.doe")).thenReturn(userProfile);

        // When & Then - verify HTTP enrichment includes all User fields
        mockMvc.perform(get("/api/v1/partners/{companyName}/contacts", "GoogleZH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("john.doe@example.com"))
                .andExpect(jsonPath("$[0].firstName").value("John"));
    }
}
```

**Mock RestTemplate in HTTP Client Tests**:

```java
@RestClientTest(components = {
    UserServiceClientImpl.class,
    RestClientConfig.class
})
@TestPropertySource(properties = {
    "user-service.base-url=http://company-user-management:8080"
})
class UserServiceClientTest {

    @Autowired
    private UserServiceClient userServiceClient;

    @Autowired
    private MockRestServiceServer mockServer;

    @Test
    void should_callUserServiceAPI_when_enrichingContacts() {
        String username = "john.doe";
        String expectedUrl = "http://company-user-management:8080/api/v1/users/" + username;

        mockServer.expect(requestTo(expectedUrl))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                            {
                                "id": "john.doe",
                                "email": "john.doe@example.com",
                                "firstName": "John",
                                "lastName": "Doe"
                            }
                            """));

        // Generated UserResponse DTO
        UserResponse user = userServiceClient.getUserByUsername(username);

        assertThat(user.getId()).isEqualTo("john.doe");
        assertThat(user.getFirstName()).isEqualTo("John");
        mockServer.verify();
    }
}
```

### When to Use This Pattern

✅ **Use Generated Client DTOs When**:
- Service needs to call another BATbern microservice via HTTP
- Upstream service has an OpenAPI specification
- You need type safety for HTTP responses
- You want build-time validation of API contract compliance

❌ **Don't Use For**:
- External third-party APIs (no control over their OpenAPI specs)
- Internal service calls that use message queues (use domain events instead)
- Services in same bounded context (consider merging services instead)

### Real-World Example: Partner Service Dependencies

```
Partner Coordination Service
├── Own API (partners-api.openapi.yml)
│   └── Generates: PartnersApi, PartnerResponse, CreatePartnerRequest
│
├── Company Service Client (companies-api.openapi.yml)
│   └── Generates: CompanyResponse, Logo (in client.company.dto package)
│   └── Manual: CompanyServiceClient, CompanyServiceClientImpl
│
└── User Service Client (users-api.openapi.yml)
    └── Generates: UserResponse (in client.user.dto package)
    └── Manual: UserServiceClient, UserServiceClientImpl
```

**Benefits Realized**:
- 🔒 Type safety: Partner service compiles against Company and User API contracts
- 🚫 Breaking changes caught at build time, not production
- 🎯 Full control over caching (15min TTL), JWT propagation, error handling
- 📝 Zero manual DTO maintenance as upstream APIs evolve

---

## Frontend: Generated TypeScript Types

### Package.json Configuration

Add scripts to `web-frontend/package.json`:

```json
{
  "scripts": {
    "generate:api-types": "npm run generate:api-types:companies && npm run generate:api-types:users && npm run generate:api-types:events",
    "generate:api-types:companies": "openapi-typescript ../docs/api/companies-api.openapi.yml -o src/types/generated/company-api.types.ts && prettier --write src/types/generated/company-api.types.ts",
    "generate:api-types:users": "openapi-typescript ../docs/api/users-api.openapi.yml -o src/types/generated/user-api.types.ts && prettier --write src/types/generated/user-api.types.ts",
    "generate:api-types:events": "openapi-typescript ../docs/api/events-api.openapi.yml -o src/types/generated/events-api.types.ts && prettier --write src/types/generated/events-api.types.ts"
  },
  "devDependencies": {
    "openapi-typescript": "^7.10.1"
  }
}
```

### Generated TypeScript Types

Running `npm run generate:api-types` generates:

```typescript
// src/types/generated/user-api.types.ts (auto-generated)

export interface paths {
  "/api/v1/users/me": {
    get: {
      parameters: {
        query?: {
          include?: string;
        };
      };
      responses: {
        200: {
          content: {
            "application/json": components["schemas"]["UserResponse"];
          };
        };
        404: {
          content: {
            "application/json": components["schemas"]["ErrorResponse"];
          };
        };
      };
    };
    patch: {
      requestBody: {
        content: {
          "application/json": components["schemas"]["UpdateUserRequest"];
        };
      };
      responses: {
        200: {
          content: {
            "application/json": components["schemas"]["UserResponse"];
          };
        };
      };
    };
  };
}

export interface components {
  schemas: {
    UserResponse: {
      id: string;
      email: string;
      firstName?: string;
      lastName?: string;
      roles: ("ORGANIZER" | "SPEAKER" | "SPONSOR" | "ATTENDEE")[];
      isActive: boolean;
      createdAt: string;
      updatedAt: string;
    };
    UpdateUserRequest: {
      firstName?: string;
      lastName?: string;
      bio?: string;
    };
    ErrorResponse: {
      timestamp: string;
      path: string;
      status: number;
      error: string;
      message: string;
      correlationId?: string;
      severity?: "ERROR" | "WARNING" | "INFO";
    };
  };
}
```

### Frontend Service Layer

```typescript
// src/services/userService.ts
import type { components } from '@/types/generated/user-api.types';
import { apiClient } from '@/lib/apiClient';

type UserResponse = components['schemas']['UserResponse'];
type UpdateUserRequest = components['schemas']['UpdateUserRequest'];

export const userService = {
  async getCurrentUser(include?: string): Promise<UserResponse> {
    const response = await apiClient.get<UserResponse>('/api/v1/users/me', {
      params: { include }
    });
    return response.data;
  },

  async updateCurrentUser(request: UpdateUserRequest): Promise<UserResponse> {
    const response = await apiClient.patch<UserResponse>('/api/v1/users/me', request);
    return response.data;
  }
};
```

### React Component

```typescript
// src/components/UserProfile.tsx
import { useState } from 'react';
import { userService } from '@/services/userService';
import type { components } from '@/types/generated/user-api.types';

type UserResponse = components['schemas']['UserResponse'];
type UpdateUserRequest = components['schemas']['UpdateUserRequest'];

export const UserProfile: React.FC = () => {
  const [user, setUser] = useState<UserResponse | null>(null);

  const handleUpdate = async (data: UpdateUserRequest) => {
    const updated = await userService.updateCurrentUser(data);
    setUser(updated);
  };

  // Full type safety throughout!
};
```

---

## Shared-Kernel Integration

### Import Mappings

Configure OpenAPI Generator to use shared-kernel types instead of generating them:

```gradle
importMappings = [
    'ErrorResponse': 'ch.batbern.shared.dto.ErrorResponse',
    'PaginationMetadata': 'ch.batbern.shared.api.PaginationMetadata',
    'PaginatedResponse': 'ch.batbern.shared.dto.PaginatedResponse'
]
```

### OpenAPI Spec References

In your OpenAPI spec (`docs/api/{service}-api.openapi.yml`):

```yaml
components:
  schemas:
    ErrorResponse:
      # This will be imported from shared-kernel, not generated
      type: object
      description: "Standard error response from shared-kernel"

    PaginationMetadata:
      # This will be imported from shared-kernel, not generated
      type: object
      description: "Pagination metadata from shared-kernel"

    PaginatedUserResponse:
      type: object
      properties:
        data:
          type: array
          items:
            $ref: '#/components/schemas/UserResponse'
        pagination:
          $ref: '#/components/schemas/PaginationMetadata'  # Imported
```

---

## Benefits

✅ **Type Safety Across Stack**: Backend DTOs and frontend types guaranteed to match (same source)

✅ **Build-Time Contract Enforcement**: Compilation fails if implementation diverges from OpenAPI spec

✅ **Single Source of Truth**: OpenAPI spec drives both frontend and backend, eliminates drift

✅ **Reduced Boilerplate**: No manual DTO creation, interfaces auto-generated

✅ **Better API Design**: Designing OpenAPI spec first forces thinking about API contract before implementation

✅ **Improved Testing**: Bruno API contract tests can reference OpenAPI spec for validation

✅ **Frontend Efficiency**: Frontend developers know exact response shapes before backend is ready

✅ **Documentation Always Current**: OpenAPI spec is the implementation contract, can't become stale

✅ **IDE Support**: Full autocomplete and type checking for API clients and DTOs

✅ **Architecture Compliance**: Seamless integration with shared-kernel infrastructure

---

## Anti-Patterns

### ❌ Don't Modify Generated Code

Generated code is overwritten on every build. Never edit files in `build/generated/`.

```java
// ❌ WRONG - editing generated code
// build/generated/src/main/java/ch/batbern/users/api/generated/UsersApi.java
public interface UsersApi {
    ResponseEntity<UserResponse> getCurrentUser(Optional<String> include);
    // Adding custom method here - will be lost on next build!
}
```

**Solution**: Add custom methods to your controller, not the generated interface.

### ❌ Don't Commit Generated Code

Generated code belongs in `build/generated/` which should be gitignored.

```gitignore
# ✅ CORRECT
build/
.gradle/
```

### ❌ Don't Create DTOs Manually

Use the generated DTOs, don't create your own.

```java
// ❌ WRONG - manual DTO
public class UserResponseDTO {
    private String id;
    private String email;
    // Duplicating what OpenAPI already defines
}

// ✅ CORRECT - use generated DTO
import ch.batbern.users.dto.generated.UserResponse;
```

### ❌ Don't Duplicate Shared-Kernel Types

Use importMappings for common types.

```yaml
# ❌ WRONG - defining ErrorResponse in every OpenAPI spec
components:
  schemas:
    ErrorResponse:
      type: object
      properties:
        timestamp: string
        message: string
        # Full definition duplicated across specs

# ✅ CORRECT - reference in spec, import in code generation
components:
  schemas:
    ErrorResponse:
      type: object
      description: "Standard error response from shared-kernel"
      # Gradle importMappings handles the import
```

### ❌ Don't Use Code-First Approach

Generate OpenAPI spec from code instead of vice versa.

```java
// ❌ WRONG - code-first with annotations
@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    @GetMapping("/me")
    @Operation(summary = "Get current user")  // OpenAPI generated from this
    public UserResponse getCurrentUser() { ... }
}
```

**Why wrong**: OpenAPI spec becomes secondary, not source of truth. Frontend and backend can drift.

**Correct**: Write OpenAPI spec first, generate interfaces, implement them.

---

## Related Documentation

### Architecture Decisions

- **[ADR-006: OpenAPI Contract-First Code Generation](../architecture/ADR-006-openapi-contract-first-code-generation.md)** - Full architecture decision and rationale
- **[ADR-003: Meaningful Identifiers in Public APIs](../architecture/ADR-003-meaningful-identifiers-public-apis.md)** - Dual-identifier strategy
- **[ADR-004: Factor User Fields from Domain Entities](../architecture/ADR-004-factor-user-fields-from-domain-entities.md)** - HTTP enrichment pattern

### Implementation Examples

- **[company-user-management-service/OPENAPI-CODEGEN.md](../../services/company-user-management-service/OPENAPI-CODEGEN.md)** - Service-specific reference
- **[Coding Standards](../architecture/coding-standards.md)** - DDD patterns, TDD workflow
- **[Backend Architecture](../architecture/06-backend-architecture.md)** - Layered architecture overview

### OpenAPI Specifications

- `docs/api/users-api.openapi.yml` - User Management API
- `docs/api/companies-api.openapi.yml` - Company Management API
- `docs/api/events-api.openapi.yml` - Event Management API
- `docs/api/partners-api.openapi.yml` - Partner Coordination API

### Stories Using This Pattern

- **Story 1.14**: Company Management Service Foundation (own API generation)
- **Story 1.14-2**: User Management Service Foundation (own API generation)
- **Story 2.2**: Event Management Service Architecture Compliance (own API generation)
- **Story 2.7**: Partner Coordination Service Foundation (own API + client DTO generation from Company and User services)

### Tools

- **[OpenAPI Generator](https://openapi-generator.tech/)** - Backend code generation
- **[openapi-typescript](https://openapi-ts.dev/)** - Frontend type generation
- **[OpenAPI 3.1 Specification](https://swagger.io/specification/)** - Spec format reference

---

**Last Updated**: 2025-11-09
**Maintained By**: Development Team
