# OpenAPI Code Generation - Hybrid Contract-First Approach

This service uses the **hybrid contract-first approach** with OpenAPI Generator to generate API interfaces and DTOs from the OpenAPI specification while maintaining compliance with BATbern architecture standards.

## Architecture Compliance

This setup ensures full compliance with:
- ✅ **[Coding Standards](../../docs/architecture/coding-standards.md)** - DDD patterns, repository pattern, service layer
- ✅ **[Backend Architecture](../../docs/architecture/06-backend-architecture.md)** - Layered architecture, error handling, security
- ✅ **[Shared-Kernel](../../shared-kernel)** - Reuses common types, exceptions, and utilities

## What Gets Generated

### ✅ Generated from OpenAPI Spec

**1. API Interfaces** (in `ch.batbern.companyuser.api.generated/`)
- `UserManagementApi.java` - User CRUD operations
- `UserSearchApi.java` - Search and autocomplete
- `UserPreferencesApi.java` - Preferences management
- `UserSettingsApi.java` - Settings management
- `RoleManagementApi.java` - Role assignment
- `ActivityHistoryApi.java` - Activity tracking
- `ProfilePictureApi.java` - Picture upload
- `GdprComplianceApi.java` - GDPR deletion
- `DomainIntegrationApi.java` - Get-or-create endpoint

**2. Domain-Specific DTOs** (in `ch.batbern.companyuser.dto.generated/`)
- `UserResponse.java`
- `UpdateUserRequest.java`
- `GetOrCreateUserRequest.java`
- `GetOrCreateUserResponse.java`
- `UserPreferences.java`
- `UserSettings.java`
- `UserSearchResponse.java`
- `PaginatedUserResponse.java` (uses shared-kernel PaginationMetadata)
- `ActivityHistory.java`
- `Company.java`
- And more...

### ✅ Reused from Shared-Kernel

**Common Types** (NOT generated - imported from shared-kernel):
- `ch.batbern.shared.dto.ErrorResponse` - Standard error response
- `ch.batbern.shared.api.PaginationMetadata` - Pagination metadata
- `ch.batbern.shared.dto.PaginatedResponse<T>` - Generic paginated response

**Query Utilities:**
- `ch.batbern.shared.api.FilterParser` - MongoDB-style JSON filters
- `ch.batbern.shared.api.SortParser` - Sort string parsing
- `ch.batbern.shared.api.IncludeParser` - Resource expansion
- `ch.batbern.shared.api.FieldSelector` - Sparse fieldsets
- `ch.batbern.shared.api.PaginationUtils` - Pagination helpers

**Exception Hierarchy:**
- `ch.batbern.shared.exception.BATbernException` - Base exception
- `ch.batbern.shared.exception.ValidationException`
- `ch.batbern.shared.exception.NotFoundException`
- `ch.batbern.shared.exception.ServiceException`

**Event Infrastructure:**
- `ch.batbern.shared.events.DomainEvent` - Base event class
- `ch.batbern.shared.events.DomainEventPublisher` - EventBridge integration

## Generated Code Location

```
services/company-user-management-service/
└── build/
    └── generated/
        └── src/main/java/
            └── ch/batbern/companyuser/
                ├── api/generated/          # API Interfaces
                └── dto/generated/          # DTOs
```

**Important**:
- Generated code is in `build/generated/`
- It's created during compilation
- Should **NOT** be committed to Git (automatically gitignored)
- Regenerated on every build

## How to Regenerate

### Automatic (Preferred)

Code is automatically regenerated when you compile:

```bash
# From project root
./gradlew :services:company-user-management-service:build

# Or just the service
cd services/company-user-management-service
../../gradlew build
```

### Manual Generation Only

If you want to just generate without compiling:

```bash
./gradlew :services:company-user-management-service:openApiGenerate
```

### Clean and Regenerate

```bash
./gradlew :services:company-user-management-service:clean :services:company-user-management-service:openApiGenerate
```

## Implementation Pattern

### Step 1: Controller Implements Generated Interface

```java
package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.api.generated.UserManagementApi;
import ch.batbern.companyuser.dto.generated.*;
import ch.batbern.companyuser.service.UserService;
import ch.batbern.shared.api.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class UserController implements UserManagementApi {

    private final UserService userService;
    private final IncludeParser includeParser;  // From shared-kernel

    @Override
    public ResponseEntity<UserResponse> getCurrentUser(Optional<String> include) {
        // Parse includes using shared-kernel utility
        Set<String> includes = includeParser.parse(include.orElse(null));

        // Call service with parsed includes
        UserResponse user = userService.getCurrentUser(includes);

        return ResponseEntity.ok(user);
    }

    @Override
    public ResponseEntity<UserResponse> updateCurrentUser(UpdateUserRequest request) {
        UserResponse updated = userService.updateCurrentUser(request);
        return ResponseEntity.ok(updated);
    }

    @Override
    public ResponseEntity<PaginatedUserResponse> listUsers(
            Optional<String> filter,
            Optional<String> sort,
            Optional<Integer> page,
            Optional<Integer> limit,
            Optional<String> fields,
            Optional<String> include,
            Optional<String> role,
            Optional<UUID> company) {

        // Parse using shared-kernel utilities
        FilterCriteria filterCriteria = FilterParser.parse(filter.orElse(null));
        SortCriteria sortCriteria = SortParser.parse(sort.orElse(null));
        Set<String> includes = includeParser.parse(include.orElse(null));

        PaginatedUserResponse response = userService.listUsers(
            filterCriteria,
            sortCriteria,
            page.orElse(1),
            limit.orElse(20),
            includes
        );

        return ResponseEntity.ok()
            .header("X-Cache-Status", "MISS")  // Add cache status
            .body(response);
    }
}
```

### Step 2: Service Layer Uses Generated DTOs + Shared-Kernel

```java
package ch.batbern.companyuser.service;

import ch.batbern.companyuser.dto.generated.*;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.shared.api.*;
import ch.batbern.shared.events.DomainEventPublisher;
import ch.batbern.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final DomainEventPublisher eventPublisher;  // From shared-kernel
    private final CompanyService companyService;

    public UserResponse getCurrentUser(Set<String> includes) {
        UUID userId = getCurrentUserId();
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found: " + userId));

        return mapToResponse(user, includes);
    }

    public UserResponse updateCurrentUser(UpdateUserRequest request) {
        UUID userId = getCurrentUserId();
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found: " + userId));

        // Update fields from generated DTO
        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }

        User saved = userRepository.save(user);

        // Publish domain event using shared-kernel
        eventPublisher.publishEvent(new UserUpdatedEvent(
            saved.getId(),
            saved.getEmail()
        ));

        return mapToResponse(saved, null);
    }

    public PaginatedUserResponse listUsers(
            FilterCriteria filter,
            SortCriteria sort,
            int page,
            int limit,
            Set<String> includes) {

        // Build JPA Specification from FilterCriteria (shared-kernel)
        Specification<User> spec = FilterSpecificationBuilder.build(filter);

        // Build Pageable from sort and pagination (shared-kernel)
        Pageable pageable = PaginationUtils.toPageable(page, limit, sort);

        Page<User> userPage = userRepository.findAll(spec, pageable);

        // Map to generated DTO
        List<UserResponse> users = userPage.getContent().stream()
            .map(user -> mapToResponse(user, includes))
            .collect(Collectors.toList());

        // Build response with shared-kernel PaginationMetadata
        PaginatedUserResponse response = new PaginatedUserResponse();
        response.setData(users);
        response.setPagination(PaginationMetadata.from(userPage));  // From shared-kernel

        return response;
    }

    private UserResponse mapToResponse(User user, Set<String> includes) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setEmail(user.getEmail());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setBio(user.getBio());
        response.setCompanyId(user.getCompanyId());
        response.setRoles(user.getRoles().stream()
            .map(role -> UserResponse.RolesEnum.fromValue(role.name()))
            .collect(Collectors.toList()));
        response.setIsActive(user.isActive());
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());
        response.setLastLoginAt(user.getLastLoginAt());

        // Handle resource expansion (includes)
        if (includes != null) {
            if (includes.contains("company") && user.getCompanyId() != null) {
                Company company = companyService.getCompanyById(user.getCompanyId());
                response.setCompany(company);
            }
            if (includes.contains("preferences")) {
                response.setPreferences(user.getPreferences());
            }
            if (includes.contains("settings")) {
                response.setSettings(user.getSettings());
            }
        }

        return response;
    }

    private UUID getCurrentUserId() {
        // Get from security context
        return SecurityContextHelper.getCurrentUser().getUserId();
    }
}
```

### Step 3: Exception Handling with Shared-Kernel

```java
package ch.batbern.companyuser.exception;

import ch.batbern.shared.dto.ErrorResponse;
import ch.batbern.shared.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Instant;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BATbernException.class)  // From shared-kernel
    public ResponseEntity<ErrorResponse> handleBATbernException(
            BATbernException ex,
            HttpServletRequest request) {

        // Use shared-kernel ErrorResponse
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(Instant.now())
            .path(request.getRequestURI())
            .status(HttpStatus.BAD_REQUEST.value())
            .error(ex.getClass().getSimpleName())
            .message(ex.getMessage())
            .correlationId(RequestContext.getCurrentRequestId())
            .severity("ERROR")
            .build();

        return ResponseEntity.badRequest().body(error);
    }

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

    @ExceptionHandler(ValidationException.class)  // From shared-kernel
    public ResponseEntity<ErrorResponse> handleValidationException(
            ValidationException ex,
            HttpServletRequest request) {

        ErrorResponse error = ErrorResponse.builder()
            .timestamp(Instant.now())
            .path(request.getRequestURI())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("VALIDATION_ERROR")
            .message(ex.getMessage())
            .correlationId(RequestContext.getCurrentRequestId())
            .severity("WARNING")
            .details(ex.getDetails())
            .build();

        return ResponseEntity.badRequest().body(error);
    }
}
```

## Benefits of This Approach

✅ **Type Safety**: DTOs exactly match your OpenAPI spec
✅ **Frontend Consistency**: Backend DTOs match frontend TypeScript types (both generated from same spec)
✅ **Less Boilerplate**: No manual DTO creation
✅ **Architecture Compliance**: Uses shared-kernel for common infrastructure
✅ **Validation**: Build fails if implementation doesn't match spec
✅ **Control**: You write controllers manually for custom business logic
✅ **Documentation**: Swagger UI generated automatically from OpenAPI spec
✅ **Maintainability**: Single source of truth (OpenAPI spec)

## What NOT to Do

❌ **Don't modify generated code** - it will be overwritten on next build
❌ **Don't commit generated code** - it's in `build/` directory (gitignored)
❌ **Don't create DTOs manually** - use the generated ones
❌ **Don't bypass shared-kernel** - reuse common infrastructure
❌ **Don't duplicate ErrorResponse or PaginationMetadata** - use shared-kernel versions

## Updating the API

1. **Update** `docs/api/users-api.openapi.yml`
2. **Rebuild** the service: `./gradlew :services:company-user-management-service:build`
3. **Fix** any compilation errors in your controllers (type safety at work!)
4. **Write tests** (TDD: tests first, then implementation)
5. **Test** your implementation

## IDE Integration

### IntelliJ IDEA

1. After running `./gradlew openApiGenerate`, IntelliJ should auto-detect the generated sources
2. If not, right-click `build/generated/src/main/java` → "Mark Directory as" → "Generated Sources Root"
3. Enjoy full autocomplete and type checking

### VS Code

Generated sources are automatically included via the Gradle source set configuration.

## Configuration Details

See `build.gradle` for the complete OpenAPI Generator configuration:

**Key Mappings:**
```gradle
importMappings = [
    'ErrorResponse': 'ch.batbern.shared.dto.ErrorResponse',
    'PaginationMetadata': 'ch.batbern.shared.api.PaginationMetadata'
]

typeMappings = [
    'ErrorResponse': 'ch.batbern.shared.dto.ErrorResponse',
    'PaginationMetadata': 'ch.batbern.shared.api.PaginationMetadata'
]

schemaMappings = [
    'ErrorResponse': 'ch.batbern.shared.dto.ErrorResponse',
    'PaginationMetadata': 'ch.batbern.shared.api.PaginationMetadata'
]
```

**Key Settings:**
- `interfaceOnly: true` - Only generate interfaces, we write implementations
- `useTags: true` - Organize APIs by OpenAPI tags
- `useSpringBoot3: true` - Use Spring Boot 3 annotations
- `useBeanValidation: true` - Include @Valid, @NotNull annotations
- `useJakartaEe: true` - Use Jakarta EE (Spring Boot 3 requirement)

## Testing with Generated Types

```java
@SpringBootTest
@AutoConfigureMockMvc
class UserControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void should_getCurrentUser_when_authenticated() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.email").exists())
                .andExpect(jsonPath("$.firstName").exists())
                .andExpect(jsonPath("$.lastName").exists());
    }

    @Test
    void should_expandCompany_when_includeCompanyProvided() throws Exception {
        mockMvc.perform(get("/api/v1/users/me?include=company")
                .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.company").exists())
                .andExpect(jsonPath("$.company.id").exists())
                .andExpect(jsonPath("$.company.name").exists());
    }
}
```

## Architecture Compliance Checklist

- ✅ Controllers implement generated interfaces
- ✅ Services use generated DTOs for request/response
- ✅ Domain entities separate from DTOs
- ✅ Shared-kernel used for ErrorResponse
- ✅ Shared-kernel used for PaginationMetadata
- ✅ Shared-kernel utilities for filtering/sorting/pagination
- ✅ Shared-kernel exceptions (BATbernException hierarchy)
- ✅ Shared-kernel event publishing (DomainEventPublisher)
- ✅ TDD workflow: write tests first, implement interfaces
- ✅ Repository pattern for data access
- ✅ Service layer for business logic

## Summary

This hybrid contract-first approach gives you:

1. **Single Source of Truth**: OpenAPI spec drives both frontend and backend
2. **Type Safety**: Generated code ensures API contract compliance
3. **Architecture Compliance**: Integrates seamlessly with shared-kernel infrastructure
4. **Developer Experience**: Less boilerplate, better IDE support
5. **Maintainability**: API changes automatically propagate to implementation

The key is using the generated interfaces and DTOs while leveraging shared-kernel for common infrastructure - giving you the best of both worlds!
