# OpenAPI Code Generation Guide

**Pattern**: Hybrid Contract-First Approach
**Architecture Decision**: [ADR-006: OpenAPI Contract-First Code Generation](../architecture/ADR-006-openapi-contract-first-code-generation.md)
**Last Updated**: 2025-01-08

---

## Table of Contents

- [Overview](#overview)
- [Philosophy](#philosophy)
- [Backend: Generated Interfaces + Manual Implementation](#backend-generated-interfaces--manual-implementation)
  - [What Gets Generated](#what-gets-generated)
  - [Gradle Configuration](#gradle-configuration)
  - [Implementation Pattern](#implementation-pattern)
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

- **Story 1.14**: Company Management Service Foundation
- **Story 1.14-2**: User Management Service Foundation
- **Story 2.2**: Event Management Service Architecture Compliance
- **Story 2.7**: Partner Coordination Service Foundation

### Tools

- **[OpenAPI Generator](https://openapi-generator.tech/)** - Backend code generation
- **[openapi-typescript](https://openapi-ts.dev/)** - Frontend type generation
- **[OpenAPI 3.1 Specification](https://swagger.io/specification/)** - Spec format reference

---

**Last Updated**: 2025-01-08
**Maintained By**: Development Team
