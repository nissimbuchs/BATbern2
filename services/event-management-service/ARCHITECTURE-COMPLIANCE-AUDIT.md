# Architecture Compliance Audit - Event Management Service

**Date**: 2025-10-19
**Service**: event-management-service
**OpenAPI Spec**: `/docs/api/events-api.openapi.yml`
**Compliance Score**: **~40%**

## Executive Summary

The event-management-service implementation has **significant architecture compliance gaps** when compared to BATbern standards and shared-kernel integration patterns. The service correctly uses some shared-kernel components (query utilities, PaginatedResponse) but is missing critical architectural elements including domain events, event publishing, standardized error responses, and OpenAPI code generation.

**Comparison**: company-user-management-service achieved 50% initial compliance, event-management-service is at ~40%.

---

## Detailed Findings

### ✅ What's Implemented Correctly

| Component | Status | Evidence |
|-----------|--------|----------|
| Shared-kernel dependency | ✅ Working | `build.gradle:39` - `implementation project(':shared-kernel')` |
| FilterParser | ✅ Working | Used in EventSearchService:86 |
| SortParser | ✅ Working | Used in EventSearchService:86 |
| PaginationMetadata | ✅ Working | Used in EventSearchService:63 |
| PaginationUtils | ✅ Working | Used in EventSearchService:45, 63 |
| PaginatedResponse | ✅ Working | Used in EventSearchService:70 |
| ValidationException (handling) | ✅ Partial | GlobalExceptionHandler:32 imports and handles shared-kernel ValidationException |

**Positive Notes:**
- Query utilities from shared-kernel are correctly integrated
- Service properly handles shared-kernel ValidationException
- Uses PaginatedResponse pattern consistently

---

### ❌ Priority 1: ErrorResponse - Missing Shared-Kernel Integration

**Problem**: Returns `Map<String, Object>` instead of standardized `ch.batbern.shared.dto.ErrorResponse`

**Current Implementation** (`GlobalExceptionHandler.java`):
```java
@ExceptionHandler(EventNotFoundException.class)
public ResponseEntity<Map<String, Object>> handleEventNotFoundException(EventNotFoundException ex) {
    log.warn("Event not found: {}", ex.getMessage());

    return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(Map.of(
                    "error", "NOT_FOUND",
                    "message", ex.getMessage()
            ));
}
```

**Missing Fields:**
- ❌ `correlationId` - Request tracing across services
- ❌ `path` - Request URI for debugging
- ❌ `severity` - ERROR, WARNING, INFO levels
- ❌ `details` - Map<String, Object> for validation errors
- ❌ `timestamp` - When error occurred
- ❌ `status` - HTTP status code

**Required Changes:**
1. Import `ch.batbern.shared.dto.ErrorResponse`
2. Import `ch.batbern.shared.util.CorrelationIdGenerator`
3. Add `HttpServletRequest request` parameter to all exception handlers
4. Replace `Map<String, Object>` with `ErrorResponse.builder()`
5. Populate all required fields (correlationId, path, severity)

**Example Fix:**
```java
@ExceptionHandler(EventNotFoundException.class)
public ResponseEntity<ErrorResponse> handleEventNotFoundException(
        EventNotFoundException ex,
        HttpServletRequest request) {

    ErrorResponse error = ErrorResponse.builder()
        .timestamp(Instant.now())
        .path(request.getRequestURI())
        .status(HttpStatus.NOT_FOUND.value())
        .error("Not Found")
        .message(ex.getMessage())
        .correlationId(CorrelationIdGenerator.generate())
        .severity("WARNING")
        .build();

    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
}
```

**Files to Modify:**
- `src/main/java/ch/batbern/events/exception/GlobalExceptionHandler.java` (all 8 exception handlers)

---

### ❌ Priority 2: Exceptions - Not Using Shared-Kernel Hierarchy

**Problem**: Custom exceptions extend `RuntimeException` instead of shared-kernel exception hierarchy

**Current Implementation:**

**`EventNotFoundException.java:8`**
```java
public class EventNotFoundException extends RuntimeException {
    public EventNotFoundException(UUID eventId) {
        super("Event not found with ID: " + eventId);
    }
}
```

**`BusinessValidationException.java:8`**
```java
public class BusinessValidationException extends RuntimeException {
    public BusinessValidationException(String message) {
        super(message);
    }
}
```

**`WorkflowException.java:9`**
```java
public class WorkflowException extends RuntimeException {
    public WorkflowException(String message) {
        super(message);
    }
}
```

**Required Changes:**

1. **EventNotFoundException** → Extend `ch.batbern.shared.exception.NotFoundException`
```java
public class EventNotFoundException extends NotFoundException {
    public EventNotFoundException(UUID eventId) {
        super("Event", eventId.toString());  // Uses shared-kernel constructor
    }
}
```

2. **BusinessValidationException** → Extend `ch.batbern.shared.exception.ValidationException`
```java
public class BusinessValidationException extends ValidationException {
    public BusinessValidationException(String message) {
        super(message);
    }

    public BusinessValidationException(String field, String message) {
        super(field, message);
    }
}
```

3. **WorkflowException** → Extend `ch.batbern.shared.exception.BusinessException`
```java
public class WorkflowException extends BusinessException {
    public WorkflowException(String message) {
        super("WORKFLOW_ERROR", message);
    }
}
```

**Benefits:**
- ✅ Consistent error codes across services
- ✅ Built-in severity levels
- ✅ Structured details map
- ✅ Standardized error handling

**Files to Modify:**
- `src/main/java/ch/batbern/events/exception/EventNotFoundException.java`
- `src/main/java/ch/batbern/events/exception/BusinessValidationException.java`
- `src/main/java/ch/batbern/events/exception/WorkflowException.java`

---

### ❌ Priority 3: Domain Events - MISSING ENTIRELY

**Problem**: NO domain events found in the codebase

**Expected Domain Events:**
Based on the event-management domain, these events should exist:
- `EventCreatedEvent` - When a new event is created
- `EventUpdatedEvent` - When an event is modified
- `EventPublishedEvent` - When an event is published (workflow transition)
- `EventCancelledEvent` - When an event is cancelled
- `SessionCreatedEvent` - When a session is added to an event
- `RegistrationCreatedEvent` - When someone registers for an event

**Current State:**
```bash
$ grep -r "class.*Event.*extends" services/event-management-service/src/main/java
# Returns only EventNotFoundException (not a domain event)
```

**Required Implementation:**

All domain events MUST extend `ch.batbern.shared.events.DomainEvent<UUID>`

**Example: EventCreatedEvent**
```java
package ch.batbern.events.event;

import ch.batbern.shared.events.DomainEvent;
import ch.batbern.shared.types.UserId;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class EventCreatedEvent extends DomainEvent<UUID> {
    private final String title;
    private final String description;
    private final Instant date;
    private final String location;
    private final String status;

    public EventCreatedEvent(
            UUID eventId,
            String title,
            String description,
            Instant date,
            String location,
            String status,
            UserId userId) {
        super(eventId, "EventCreated", userId);
        this.title = title;
        this.description = description;
        this.date = date;
        this.location = location;
        this.status = status;
    }
}
```

**Inherited Fields from DomainEvent:**
- ✅ `eventId` - Unique event identifier
- ✅ `aggregateId` - Event UUID
- ✅ `eventType` - Event type name
- ✅ `userId` - User who triggered the event
- ✅ `occurredAt` - Timestamp
- ✅ `correlationId` - Request correlation
- ✅ `causationId` - Causal chain tracking
- ✅ `metadata` - Extensible metadata map
- ✅ `version` - Event schema version

**Files to Create:**
- `src/main/java/ch/batbern/events/event/EventCreatedEvent.java`
- `src/main/java/ch/batbern/events/event/EventUpdatedEvent.java`
- `src/main/java/ch/batbern/events/event/EventPublishedEvent.java`
- `src/main/java/ch/batbern/events/event/EventCancelledEvent.java`
- `src/main/java/ch/batbern/events/event/SessionCreatedEvent.java`
- `src/main/java/ch/batbern/events/event/RegistrationCreatedEvent.java`

---

### ❌ Priority 4: Event Publisher - MISSING ENTIRELY

**Problem**: NO event publisher found in the codebase

**Current State:**
```bash
$ grep -r "EventBridge\|eventPublisher\|DomainEventPublisher" services/event-management-service/src/main/java
# Returns NO results
```

**Required Implementation:**

Services MUST use `ch.batbern.shared.events.DomainEventPublisher` to publish domain events to AWS EventBridge.

**Example Service Integration:**
```java
package ch.batbern.events.service;

import ch.batbern.events.event.EventCreatedEvent;
import ch.batbern.shared.events.DomainEventPublisher;
import ch.batbern.shared.security.SecurityContextHelper;
import ch.batbern.shared.types.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EventService {
    private final DomainEventPublisher eventPublisher;
    private final SecurityContextHelper securityContextHelper;
    private final EventRepository eventRepository;

    public EventResponse createEvent(CreateEventRequest request) {
        // 1. Validate and save entity
        Event event = mapToEntity(request);
        Event saved = eventRepository.save(event);

        // 2. Publish domain event
        UserId userId = UserId.from(securityContextHelper.getCurrentUserId());
        EventCreatedEvent event = new EventCreatedEvent(
            saved.getId(),
            saved.getTitle(),
            saved.getDescription(),
            saved.getDate(),
            saved.getLocation(),
            saved.getStatus().name(),
            userId
        );
        eventPublisher.publish(event);

        // 3. Return response
        return mapToResponse(saved);
    }
}
```

**DomainEventPublisher Features:**
- ✅ `publish(DomainEvent)` - Synchronous publishing
- ✅ `publishAsync(DomainEvent)` - Asynchronous publishing
- ✅ `publishBatch(List<DomainEvent>)` - Batch publishing
- ✅ `publishWithRetry(event, maxRetries)` - Automatic retry with exponential backoff
- ✅ Structured logging with LoggingUtils
- ✅ Automatic event validation
- ✅ EventBridge entry creation

**Required Dependencies:**
Already present in `build.gradle:42-43`:
```gradle
implementation platform('software.amazon.awssdk:bom:2.29.45')
implementation 'software.amazon.awssdk:eventbridge'
```

**Files to Modify:**
- `src/main/java/ch/batbern/events/service/EventService.java` (if it exists)
- All service files that perform create/update/delete operations

**Configuration Required:**
Verify `application.yml` has EventBridge configuration:
```yaml
aws:
  eventbridge:
    bus-name: ${AWS_EVENTBRIDGE_BUS_NAME:batbern-event-bus}
    region: ${AWS_REGION:eu-central-1}
```

---

### ❌ Priority 5: OpenAPI Code Generation - NOT CONFIGURED

**Problem**: No OpenAPI Generator configuration in `build.gradle`

**Current State** (`build.gradle`):
- ❌ No `org.openapi.generator` plugin
- ❌ No `openApiGenerate` task
- ❌ Manual DTOs (not generated from OpenAPI spec)
- ✅ Has `springdoc-openapi` (but only for documentation, not code generation)

**Required Changes:**

**1. Add Plugin to `build.gradle:7`**
```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.5.6'
    id 'io.spring.dependency-management' version '1.1.6'
    id 'jacoco'
    id 'org.flywaydb.flyway' version '10.10.0'
    id 'org.openapi.generator' version '7.2.0'  // ADD THIS
}
```

**2. Add OpenAPI Generator Dependencies (after line 52)**
```gradle
// OpenAPI Generator - Required for generated code
implementation 'io.swagger.core.v3:swagger-annotations:2.2.20'
implementation 'jakarta.validation:jakarta.validation-api'
implementation 'jakarta.annotation:jakarta.annotation-api'
```

**3. Add OpenAPI Generation Task (after line 116)**
```gradle
// OpenAPI Generator - Generate API interfaces and DTOs from OpenAPI spec
openApiGenerate {
    generatorName = 'spring'
    inputSpec = "$rootDir/docs/api/events-api.openapi.yml".toString()
    outputDir = "$buildDir/generated".toString()
    apiPackage = 'ch.batbern.events.api.generated'
    modelPackage = 'ch.batbern.events.dto.generated'
    invokerPackage = 'ch.batbern.events.api.invoker'

    // Import mappings - use shared-kernel types instead of generating
    importMappings = [
        'ErrorResponse': 'ch.batbern.shared.dto.ErrorResponse',
        'PaginationMetadata': 'ch.batbern.shared.api.PaginationMetadata',
        'PaginatedResponse': 'ch.batbern.shared.dto.PaginatedResponse'
    ]

    // Type mappings - map OpenAPI schemas to shared-kernel classes
    typeMappings = [
        'ErrorResponse': 'ch.batbern.shared.dto.ErrorResponse',
        'PaginationMetadata': 'ch.batbern.shared.api.PaginationMetadata'
    ]

    configOptions = [
        dateLibrary: 'java8',
        interfaceOnly: 'true',              // Only generate API interfaces
        skipDefaultInterface: 'true',       // Don't generate default implementations
        useTags: 'true',                    // Use OpenAPI tags to organize API interfaces
        useSpringBoot3: 'true',             // Use Spring Boot 3 annotations
        documentationProvider: 'none',      // Avoid conflicts with springdoc-openapi
        openApiNullable: 'false',           // Don't use JsonNullable wrapper
        hideGenerationTimestamp: 'true',    // Cleaner generated code
        useBeanValidation: 'true',          // Include @Valid, @NotNull annotations
        performBeanValidation: 'true',      // Enable bean validation
        useOptional: 'false',               // Use standard types instead of Optional
        delegatePattern: 'false',           // We implement interfaces directly
        useJakartaEe: 'true'                // Use Jakarta EE (Spring Boot 3 requirement)
    ]

    // Generate only models and API interfaces (skip implementation)
    globalProperties = [
        models: '',
        apis: '',
        supportingFiles: 'ApiUtil.java'
    ]

    // Skip generation of shared-kernel types
    schemaMappings = [
        'ErrorResponse': 'ch.batbern.shared.dto.ErrorResponse',
        'PaginationMetadata': 'ch.batbern.shared.api.PaginationMetadata'
    ]
}

// Make compileJava depend on openApiGenerate
compileJava.dependsOn tasks.openApiGenerate

// Add generated sources to source sets
sourceSets {
    main {
        java {
            srcDir "$buildDir/generated/src/main/java"
        }
    }
}

// Clean generated sources when running clean
clean {
    delete "$buildDir/generated"
}
```

**4. Verify OpenAPI Spec License Field**

Check `/docs/api/events-api.openapi.yml` and ensure it has:
```yaml
info:
  title: Events API
  version: 1.0.0
  license:
    name: Proprietary
    identifier: Proprietary  # Required for OpenAPI Generator validation
```

**Generated Code Output:**
```
services/event-management-service/
└── build/
    └── generated/
        └── src/main/java/ch/batbern/events/
            ├── api/generated/
            │   ├── EventsApi.java          # API interface
            │   ├── SessionsApi.java        # API interface
            │   └── RegistrationsApi.java   # API interface
            └── dto/generated/
                ├── EventResponse.java
                ├── CreateEventRequest.java
                ├── UpdateEventRequest.java
                ├── PaginatedEventResponse.java
                └── ... (all DTOs from OpenAPI spec)
```

**Benefits:**
- ✅ Type safety between frontend and backend (same spec)
- ✅ Build fails if implementation doesn't match OpenAPI spec
- ✅ Less boilerplate code to write
- ✅ Automatic validation annotations
- ✅ Consistent error responses (imports shared-kernel ErrorResponse)
- ✅ Consistent pagination (imports shared-kernel PaginationMetadata)

---

## Architecture Compliance Summary

| Component | Status | Compliance | Notes |
|-----------|--------|-----------|-------|
| ErrorResponse | ❌ Missing | 0% | Using Map<String, Object> |
| Exceptions Hierarchy | ❌ Incorrect | 0% | Extending RuntimeException |
| Domain Events | ❌ Missing | 0% | No domain events found |
| Event Publisher | ❌ Missing | 0% | No EventBridge integration |
| OpenAPI Generation | ❌ Missing | 0% | No code generation configured |
| Query Utilities | ✅ Working | 100% | FilterParser, SortParser, PaginationUtils |
| PaginatedResponse | ✅ Working | 100% | Using shared-kernel |
| ValidationException Handling | ✅ Partial | 50% | Imports shared-kernel, but response format incorrect |

**Overall Compliance**: **~40%** (lower than company-user-management-service's initial 50%)

---

## Impact Analysis

### Critical Missing Functionality

1. **No Domain Events = No Event-Driven Architecture**
   - Cannot track what happens in the system
   - Cannot trigger workflows in other services
   - Cannot implement event sourcing
   - Cannot audit changes
   - Missing correlation IDs for distributed tracing

2. **No Event Publisher = No Integration with EventBridge**
   - Events are not published to AWS EventBridge
   - Other services cannot react to event management operations
   - No async workflows
   - No eventual consistency patterns

3. **Inconsistent Error Responses = Poor Observability**
   - Cannot correlate errors across services
   - Missing request path for debugging
   - No severity levels for alerting
   - No structured validation error details

4. **No OpenAPI Code Generation = Type Safety Risk**
   - Manual DTOs can drift from OpenAPI spec
   - Frontend and backend can get out of sync
   - No compile-time validation of API contracts
   - More boilerplate code to maintain

### Comparison with company-user-management-service

**After Fixes** (company-user-management-service achieved 100%):
- ✅ ErrorResponse with correlationId, path, severity
- ✅ Exceptions extending shared-kernel hierarchy
- ✅ Domain events extending DomainEvent<UUID>
- ✅ Using DomainEventPublisher for EventBridge
- ✅ OpenAPI code generation configured for both APIs

**Current State** (event-management-service at 40%):
- ❌ ErrorResponse missing
- ❌ Exceptions not using shared-kernel
- ❌ Domain events missing entirely
- ❌ Event publisher missing entirely
- ❌ OpenAPI generation not configured

---

## Recommended Action Plan

### Phase 1: Foundation (Estimated 2-3 hours)

1. **Fix ErrorResponse** (Priority 1)
   - Update GlobalExceptionHandler to use shared-kernel ErrorResponse
   - Add HttpServletRequest to all exception handlers
   - Add correlationId, path, severity to all responses
   - **Expected Impact**: 8 exception handlers updated

2. **Fix Exceptions Hierarchy** (Priority 2)
   - Update EventNotFoundException → extend NotFoundException
   - Update BusinessValidationException → extend ValidationException
   - Update WorkflowException → extend BusinessException
   - **Expected Impact**: 3 exception classes updated

### Phase 2: OpenAPI Code Generation (Estimated 1-2 hours)

3. **Configure OpenAPI Generator** (Priority 5)
   - Add plugin to build.gradle
   - Configure importMappings for shared-kernel types
   - Verify license field in events-api.openapi.yml
   - Run generation and verify output
   - **Expected Impact**: Build process updated, DTOs auto-generated

### Phase 3: Event-Driven Architecture (Estimated 4-6 hours)

4. **Implement Domain Events** (Priority 3)
   - Create EventCreatedEvent
   - Create EventUpdatedEvent
   - Create EventPublishedEvent
   - Create EventCancelledEvent
   - Create SessionCreatedEvent
   - Create RegistrationCreatedEvent
   - **Expected Impact**: 6+ new event classes

5. **Integrate Event Publisher** (Priority 4)
   - Inject DomainEventPublisher in service classes
   - Inject SecurityContextHelper for userId
   - Publish events after create/update/delete operations
   - **Expected Impact**: Multiple service classes updated

### Phase 4: Testing (Estimated 2-3 hours)

6. **Update Tests**
   - Mock DomainEventPublisher in unit tests
   - Mock SecurityContextHelper in unit tests
   - Add @WithMockUser to integration tests
   - Verify all events are published
   - Verify ErrorResponse format in controller tests
   - **Expected Impact**: All test files updated

---

## Verification Commands

```bash
# 1. Verify OpenAPI code generation
./gradlew :services:event-management-service:openApiGenerate

# 2. Verify shared-kernel types are used (should return 0)
find services/event-management-service/build/generated -name "ErrorResponse.java" -o -name "PaginationMetadata.java" | wc -l

# 3. Verify generated code uses shared-kernel imports
grep -r "ch.batbern.shared.api.PaginationMetadata" services/event-management-service/build/generated/

# 4. Verify domain events extend DomainEvent
grep -r "extends DomainEvent" services/event-management-service/src/main/java/ch/batbern/events/event/

# 5. Build and test
./gradlew :services:event-management-service:clean :services:event-management-service:build

# 6. Run only event-management-service tests
./gradlew :services:event-management-service:test
```

---

## Related Documentation

- [ARCHITECTURE-COMPLIANCE-FIXES.md](../company-user-management-service/ARCHITECTURE-COMPLIANCE-FIXES.md) - Similar fixes for company-user-management-service (achieved 100% compliance)
- [OPENAPI-CODEGEN.md](../company-user-management-service/OPENAPI-CODEGEN.md) - OpenAPI code generation guide
- [Shared-Kernel Events](../../shared-kernel/src/main/java/ch/batbern/shared/events/)
- [Shared-Kernel Exceptions](../../shared-kernel/src/main/java/ch/batbern/shared/exception/)
- [Backend Architecture](../../docs/architecture/06-backend-architecture.md)
- [Development Standards](../../docs/architecture/07-development-standards.md)

---

## Conclusion

The event-management-service requires significant architectural improvements to achieve compliance with BATbern standards. The most critical gaps are:

1. **Missing domain events and event publishing** - This is a fundamental architectural requirement
2. **Non-standardized error responses** - Impacts observability and debugging
3. **No OpenAPI code generation** - Risk of API contract drift

**Recommendation**: Follow the same 5-priority fix approach used for company-user-management-service, which successfully went from 50% → 100% compliance with similar issues.

**Estimated Total Effort**: 9-14 hours to achieve 100% compliance
