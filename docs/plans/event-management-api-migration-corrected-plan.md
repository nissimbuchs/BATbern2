# Event Management API OpenAPI Migration - Architectural Plan

**Status**: Planning Complete - Ready for Implementation
**Created**: 2026-01-04
**Architect**: Winston (Claude Code Architect Agent)
**Related Documents**:
- Original Plan: `/Users/nissim/dev/bat/BATbern-feature/docs/plans/backend-dto-openapi-migration-plan.md`
- Dev Findings: `/Users/nissim/dev/bat/BATbern-feature/docs/plans/event-management-api-migration-findings.md`
- Related Stories: BAT-19 (Events API Migration), BAT-87 (Controller Migration)
- Related ADRs: ADR-003 (Meaningful Identifiers), ADR-006 (OpenAPI Contract-First)

---

## Executive Summary

The Event Management Service OpenAPI migration (BAT-19, Tasks 1-9) left the codebase in a **partially migrated state** requiring architectural correction. This plan provides a **multi-phase incremental approach** to complete the migration while addressing root causes and establishing sustainable patterns.

**Key Decisions**:
1. ✅ **Root Cause Identified**: Naming conflicts stem from inconsistent OpenAPI schema naming (responses lack suffixes)
2. ✅ **Architectural Solution**: Apply Response/Request suffix pattern consistently across all OpenAPI schemas
3. ✅ **Migration Strategy**: Multi-phase incremental (4 phases, ~20-30 hours total)
4. ✅ **Resource Expansion**: Use wrapper DTOs with optional fields (contract-compliant, fully typed)

**Estimated Effort**: 20-30 hours (3-4 full work days)

---

## 🔍 Root Cause Analysis: Naming Conflicts

### The Problem

**Naming Collision Between Layers**:

```
Domain Layer:              DTO Layer (Generated):
ch.batbern.events.domain   ch.batbern.events.dto.generated.events
├── Event.java             ├── Event.java           ← CONFLICT!
├── Session.java           ├── Session.java         ← CONFLICT!
└── Registration.java      └── Registration.java    ← CONFLICT!
```

**Why This Happens**:

1. **OpenAPI schemas named after business concepts** (line 3092 in `events-api.openapi.yml`):
   ```yaml
   components:
     schemas:
       Event:           # ← Generates Event.java
       Session:         # ← Generates Session.java
       Registration:    # ← Generates Registration.java
   ```

2. **Domain entities use same names** (semantic correctness):
   ```java
   // Domain layer - business entities
   package ch.batbern.events.domain;
   public class Event { }      // JPA entity
   public class Session { }    // JPA entity
   public class Registration { } // JPA entity
   ```

3. **Java limitation**: Cannot use simple imports for both classes simultaneously
   ```java
   // This doesn't work:
   import ch.batbern.events.domain.Event;              // Domain entity
   import ch.batbern.events.dto.generated.events.Event; // Generated DTO - CONFLICT!

   // Forces verbose fully qualified names:
   ch.batbern.events.dto.generated.events.Event dto = ...
   ```

### Why Current OpenAPI Naming Is Inconsistent

**Existing Pattern Analysis**:

| DTO Type | Current Schema Name | Generated Class | Follows Convention? |
|----------|---------------------|-----------------|---------------------|
| Request DTOs | `CreateEventRequest` | `CreateEventRequest.java` | ✅ YES (has suffix) |
| Request DTOs | `UpdateEventRequest` | `UpdateEventRequest.java` | ✅ YES (has suffix) |
| Request DTOs | `PatchEventRequest` | `PatchEventRequest.java` | ✅ YES (has suffix) |
| **Response DTOs** | **`Event`** | **`Event.java`** | ❌ **NO (missing suffix)** |
| **Response DTOs** | **`Session`** | **`Session.java`** | ❌ **NO (missing suffix)** |
| **Response DTOs** | **`Registration`** | **`Registration.java`** | ❌ **NO (missing suffix)** |

**Observation**: Request DTOs follow REST naming conventions (suffixed), but response DTOs don't.

### Architectural Solution: Apply Consistent Naming Convention

**Recommended Pattern**: Use **Response/Request suffix** for all DTOs (REST industry standard)

**Schema Renaming**:

| Current Schema | Rename To | Rationale |
|----------------|-----------|-----------|
| `Event` | `EventResponse` | Consistent with `CreateEventRequest` pattern |
| `Session` | `SessionResponse` | Consistent with `CreateSessionRequest` pattern |
| `Registration` | `RegistrationResponse` | Consistent with `CreateRegistrationRequest` pattern |
| `EventDetail` | `EventDetailResponse` | Extended event response with relations |

**Benefits**:
1. ✅ **Eliminates naming conflicts** - `EventResponse.java` vs `Event.java` (domain entity)
2. ✅ **Follows REST conventions** - Common pattern in Spring Data REST, Spring Boot APIs
3. ✅ **Semantic correctness** - These ARE response DTOs, name reflects purpose
4. ✅ **Consistency** - Matches existing request DTO pattern
5. ✅ **No code verbosity** - Can use simple imports everywhere
6. ✅ **Industry alignment** - Standard pattern across Java REST APIs

**Not Recommended Alternatives**:
- ❌ **`Dto` suffix** (`EventDto`) - Not REST convention, less clear than Response/Request distinction
- ❌ **Fully qualified names** - Verbose, error-prone, poor developer experience
- ❌ **Keep as-is** - Maintains architectural inconsistency and import conflicts

---

## 🏗️ Multi-Phase Incremental Migration Plan

### Overview

**Total Duration**: 20-30 hours (3-4 work days)
**Approach**: Incremental, pausable, low-risk
**Phases**: 4 distinct phases with clear deliverables

```
┌──────────────────────────────────────────────────────────────────┐
│  Phase 1: Architectural Foundation (4-6 hours)                   │
│  └─ OpenAPI schema renaming, type utilities, service contracts   │
├──────────────────────────────────────────────────────────────────┤
│  Phase 2: Service Layer Migration (6-8 hours)                    │
│  └─ Migrate EventService, SessionService to return generated DTOs│
├──────────────────────────────────────────────────────────────────┤
│  Phase 3: Controller Migration (8-12 hours)                      │
│  └─ One controller at a time, incremental verification           │
├──────────────────────────────────────────────────────────────────┤
│  Phase 4: Cleanup & Validation (2-4 hours)                       │
│  └─ Delete manual DTOs, full test suite, documentation           │
└──────────────────────────────────────────────────────────────────┘
```

---

## Phase 1: Architectural Foundation (4-6 hours)

### 1.1 OpenAPI Schema Renaming (2 hours)

**Goal**: Fix naming inconsistencies in OpenAPI specification

**File**: `docs/api/events-api.openapi.yml`

**Changes Required**:

| Current Schema Name | Rename To | Lines (Approx) |
|---------------------|-----------|----------------|
| `Event` | `EventResponse` | ~3092 |
| `EventDetail` | `EventDetailResponse` | ~3205 |
| `Session` | `SessionResponse` | Search for `Session:` in components |
| `Registration` | `RegistrationResponse` | Search for `Registration:` in components |
| `Venue` | `VenueResponse` | ~3217 |
| `Speaker` | `SpeakerResponse` | ~3229 |

**Pattern**:
```yaml
# Before:
components:
  schemas:
    Event:
      type: object
      properties:
        eventCode:
          type: string

# After:
components:
  schemas:
    EventResponse:
      type: object
      properties:
        eventCode:
          type: string
```

**Search & Replace Strategy**:
1. Rename schema definitions in `components.schemas`
2. Update all `$ref` references throughout the spec
3. Verify no broken references (`$ref: '#/components/schemas/Event'` → `$ref: '#/components/schemas/EventResponse'`)

**Verification**:
```bash
# Count schema references before/after
grep -c "\$ref: '#/components/schemas/Event'" docs/api/events-api.openapi.yml
# Should be 0 after renaming (all should reference EventResponse)
```

### 1.2 Regenerate Backend DTOs (1 hour)

**Goal**: Generate new DTOs with corrected names

**Steps**:
```bash
# Clean previous generated code
rm -rf services/event-management-service/build/generated-events/

# Regenerate DTOs from updated OpenAPI spec
./gradlew :services:event-management-service:openApiGenerateEvents

# Verify generated classes
ls services/event-management-service/build/generated-events/src/main/java/ch/batbern/events/dto/generated/events/
# Expected: EventResponse.java, SessionResponse.java, RegistrationResponse.java, etc.
```

**Expected Generated DTOs** (partial list):
- `EventResponse.java`
- `EventDetailResponse.java`
- `SessionResponse.java`
- `RegistrationResponse.java`
- `VenueResponse.java`
- `SpeakerResponse.java`
- `CreateEventRequest.java` (unchanged)
- `UpdateEventRequest.java` (unchanged)
- `PatchEventRequest.java` (unchanged)

### 1.3 Create Type Conversion Utility (1 hour)

**Goal**: Centralized type conversions for common mapping operations

**File**: `services/event-management-service/src/main/java/ch/batbern/events/mapper/TypeConversionUtil.java` (new)

**Implementation**:
```java
package ch.batbern.events.mapper;

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Centralized type conversion utilities for DTO mapping.
 *
 * Handles common type conversions between domain entities and generated DTOs:
 * - Instant ↔ OffsetDateTime (JPA vs OpenAPI date-time)
 * - String ↔ URI (entity URLs vs OpenAPI uri format)
 *
 * All conversions handle null safely.
 */
public final class TypeConversionUtil {

    private TypeConversionUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== Date/Time Conversions ====================

    /**
     * Convert Instant (JPA entity) to OffsetDateTime (OpenAPI DTO).
     * Uses UTC timezone for consistency.
     */
    public static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant != null ? instant.atOffset(ZoneOffset.UTC) : null;
    }

    /**
     * Convert OffsetDateTime (OpenAPI DTO) to Instant (JPA entity).
     */
    public static Instant toInstant(OffsetDateTime offsetDateTime) {
        return offsetDateTime != null ? offsetDateTime.toInstant() : null;
    }

    /**
     * Convert LocalDateTime to OffsetDateTime (for backward compatibility).
     * Uses system default timezone.
     */
    public static OffsetDateTime localDateTimeToOffsetDateTime(java.time.LocalDateTime localDateTime) {
        return localDateTime != null
            ? localDateTime.atZone(ZoneId.systemDefault()).toOffsetDateTime()
            : null;
    }

    // ==================== URL/URI Conversions ====================

    /**
     * Convert String URL (JPA entity) to URI (OpenAPI DTO).
     * Returns null for null or empty strings.
     *
     * @throws IllegalArgumentException if string is not a valid URI
     */
    public static URI toURI(String uriString) {
        if (uriString == null || uriString.trim().isEmpty()) {
            return null;
        }
        try {
            return URI.create(uriString);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid URI: " + uriString, e);
        }
    }

    /**
     * Convert URI (OpenAPI DTO) to String (JPA entity).
     */
    public static String fromURI(URI uri) {
        return uri != null ? uri.toString() : null;
    }
}
```

**Unit Tests**: `TypeConversionUtilTest.java`
- Test null handling for all methods
- Test Instant ↔ OffsetDateTime round-trip
- Test URI creation with valid/invalid strings
- Test edge cases (empty strings, malformed URIs)

### 1.4 Define Service Layer Contract (1 hour)

**Goal**: Establish clear contract that all services must return generated DTOs

**Pattern Documentation**:

**services/event-management-service/ARCHITECTURE-PATTERNS.md** (new file):

```markdown
# Event Management Service - Architecture Patterns

## Service Layer Contract

**Rule**: All public service methods MUST return generated DTOs, never domain entities.

### Pattern:

```java
@Service
@Transactional
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final EventMapper eventMapper;

    // ✅ CORRECT: Returns generated DTO
    public EventResponse getEvent(String eventCode) {
        Event entity = eventRepository.findByEventCode(eventCode)
            .orElseThrow(() -> new EventNotFoundException(eventCode));
        return eventMapper.toDto(entity);
    }

    // ❌ WRONG: Returns domain entity
    public Event getEvent(String eventCode) {
        return eventRepository.findByEventCode(eventCode)
            .orElseThrow();
    }
}
```

### Resource Expansion Pattern (Optional Fields)

For endpoints with `?include=` parameter, use wrapper response DTOs with optional fields:

```java
// Service method signature
public EventDetailResponse getEventWithIncludes(String eventCode, Set<String> includes) {
    Event event = eventRepository.findByEventCode(eventCode)
        .orElseThrow(() -> new EventNotFoundException(eventCode));

    EventDetailResponse response = eventMapper.toDetailDto(event);

    // Conditionally populate optional fields based on includes
    if (includes.contains("venue")) {
        response.setVenue(venueMapper.toDto(event.getVenue()));
    }

    if (includes.contains("sessions")) {
        List<SessionResponse> sessions = sessionRepository.findByEventCode(eventCode).stream()
            .map(sessionMapper::toDto)
            .toList();
        response.setSessions(sessions);
    }

    return response;
}
```
```

---

## Phase 2: Service Layer Migration (6-8 hours)

### 2.1 Create Pure Mappers (2 hours)

**Goal**: Create mappers following TopicMapper pattern (Pure Mapper, no business logic)

#### EventMapper (NEW)

**File**: `services/event-management-service/src/main/java/ch/batbern/events/mapper/EventMapper.java`

**Methods Required**:
- `EventResponse toDto(Event entity)` - Basic mapping
- `EventDetailResponse toDetailDto(Event entity)` - With optional relations
- `Event toEntity(CreateEventRequest request)` - Create
- `void applyUpdateRequest(Event entity, UpdateEventRequest request)` - Update
- `void applyPatchRequest(Event entity, PatchEventRequest request)` - Patch

**Pattern** (following TopicMapper):
```java
@Component
public class EventMapper {

    /**
     * Pure mapper: Event entity → EventResponse DTO
     * No business logic, no repository dependencies.
     */
    public EventResponse toDto(Event entity) {
        if (entity == null) return null;

        EventResponse dto = new EventResponse();
        dto.setEventCode(entity.getEventCode());
        dto.setTitle(entity.getTitle());
        dto.setEventNumber(entity.getEventNumber());
        dto.setDate(TypeConversionUtil.toOffsetDateTime(entity.getDate()));
        dto.setRegistrationDeadline(TypeConversionUtil.toOffsetDateTime(entity.getRegistrationDeadline()));
        dto.setVenueName(entity.getVenueName());
        dto.setVenueAddress(entity.getVenueAddress());
        dto.setVenueCapacity(entity.getVenueCapacity());
        dto.setOrganizerUsername(entity.getOrganizerUsername());
        dto.setCurrentAttendeeCount(entity.getCurrentAttendeeCount());
        dto.setPublishedAt(TypeConversionUtil.toOffsetDateTime(entity.getPublishedAt()));
        dto.setMetadata(entity.getMetadata());
        dto.setDescription(entity.getDescription());
        dto.setCreatedAt(TypeConversionUtil.toOffsetDateTime(entity.getCreatedAt()));
        dto.setUpdatedAt(TypeConversionUtil.toOffsetDateTime(entity.getUpdatedAt()));
        dto.setThemeImageUrl(TypeConversionUtil.toURI(entity.getThemeImageUrl()));
        dto.setThemeImageUploadId(entity.getThemeImageUploadId());
        dto.setTopicCode(entity.getTopicCode());
        dto.setEventType(entity.getEventType());
        dto.setWorkflowState(entity.getWorkflowState());

        return dto;
    }

    /**
     * Event entity → EventDetailResponse DTO (with optional relations)
     * Caller must populate venue, sessions based on includes.
     */
    public EventDetailResponse toDetailDto(Event entity) {
        if (entity == null) return null;

        EventDetailResponse dto = new EventDetailResponse();
        // Copy all fields from EventResponse (EventDetailResponse extends EventResponse)
        // ... field mapping ...

        // Optional fields (venue, sessions) left null - caller populates
        return dto;
    }
}
```

#### SessionMapper (NEW)

**File**: `services/event-management-service/src/main/java/ch/batbern/events/mapper/SessionMapper.java`

**Methods Required**:
- `SessionResponse toDto(Session entity)`
- `Session toEntity(CreateSessionRequest request)`
- `void applyUpdateRequest(Session entity, UpdateSessionRequest request)`

#### RegistrationMapper (UPDATE if exists, or CREATE)

**File**: `services/event-management-service/src/main/java/ch/batbern/events/mapper/RegistrationMapper.java`

**Status**: May already exist from Task 6b - verify and update to use new `RegistrationResponse` schema

**Methods Required**:
- `RegistrationResponse toDto(Registration entity)`
- `Registration toEntity(CreateRegistrationRequest request)`
- `void applyPatchRequest(Registration entity, PatchRegistrationRequest request)`

### 2.2 Migrate EventService (2 hours)

**Goal**: Refactor EventService to return generated DTOs

**File**: `services/event-management-service/src/main/java/ch/batbern/events/service/EventService.java`

**Current State**: Returns `Map<String, Object>` or manual DTOs

**Target State**: Returns `EventResponse`, `EventDetailResponse`, etc.

**Key Changes**:

1. **Inject EventMapper**:
   ```java
   private final EventMapper eventMapper;
   ```

2. **Migrate CRUD methods**:
   ```java
   // Before:
   public Map<String, Object> getEvent(String eventCode) {
       Event event = eventRepository.findByEventCode(eventCode).orElseThrow();
       return buildBasicEventResponse(event); // Manual Map building
   }

   // After:
   public EventResponse getEvent(String eventCode) {
       Event event = eventRepository.findByEventCode(eventCode)
           .orElseThrow(() -> new EventNotFoundException(eventCode));
       return eventMapper.toDto(event);
   }
   ```

3. **Migrate methods with resource expansion**:
   ```java
   public EventDetailResponse getEventWithIncludes(String eventCode, Set<String> includes) {
       Event event = eventRepository.findByEventCode(eventCode)
           .orElseThrow(() -> new EventNotFoundException(eventCode));

       EventDetailResponse response = eventMapper.toDetailDto(event);

       // Populate optional fields based on includes
       if (includes.contains("venue")) {
           // Fetch and map venue
           response.setVenue(...);
       }

       if (includes.contains("sessions")) {
           List<SessionResponse> sessions = sessionRepository.findByEventCode(eventCode).stream()
               .map(sessionMapper::toDto)
               .toList();
           response.setSessions(sessions);
       }

       return response;
   }
   ```

4. **Extract business logic** (from controller to service):
   - `buildBasicEventResponse()` logic → `getEvent()` method
   - `associateThemeImage()` → Keep in service (already business logic)
   - `applyResourceExpansions()` → Integrate into `getEventWithIncludes()`

**Methods to Migrate** (estimated):
- `getEvent(String eventCode)` → Returns `EventResponse`
- `getEventWithIncludes(String eventCode, Set<String> includes)` → Returns `EventDetailResponse`
- `createEvent(CreateEventRequest request)` → Returns `EventResponse`
- `updateEvent(String eventCode, UpdateEventRequest request)` → Returns `EventResponse`
- `patchEvent(String eventCode, PatchEventRequest request)` → Returns `EventResponse`
- `listEvents(...)` → Returns `List<EventResponse>`

### 2.3 Migrate SessionService (2 hours)

**Goal**: Refactor SessionService to return generated DTOs

**File**: `services/event-management-service/src/main/java/ch/batbern/events/service/SessionService.java`

**Current State**: Returns manual `SessionResponse` DTOs

**Target State**: Returns generated `SessionResponse` DTOs (renamed from `Session` schema)

**Key Changes**:
- Replace `toSessionResponse()` manual DTO building with `sessionMapper.toDto()`
- Update all method signatures to return generated `SessionResponse`

### 2.4 Service Layer Testing (1 hour)

**Goal**: Verify service layer migrations compile and tests pass

**Steps**:
```bash
# Run service layer unit tests
./gradlew :services:event-management-service:test --tests "*Service*"

# Verify compilation
./gradlew :services:event-management-service:compileJava
```

**Expected Outcome**:
- ✅ All service methods return generated DTOs
- ✅ No `Map<String, Object>` returns in service layer
- ✅ All service tests pass

---

## Phase 3: Controller Migration (8-12 hours)

### 3.1 Controller Migration Priority Order

**Strategy**: One controller at a time, smallest to largest

1. **EventController** (Event CRUD endpoints) - 3-4 hours
   - 10 endpoints to migrate
   - Highest complexity (resource expansion, caching, domain events)
   - Core business functionality

2. **SessionController** - 2 hours
   - 6 session endpoints
   - Depends on SessionMapper and SessionService

3. **EventWorkflowController** - 2 hours
   - 5 workflow endpoints
   - State machine integration

4. **SessionSpeakerController** - 1.5 hours
   - 4 speaker endpoints

5. **PublishingEngineController** - 2 hours
   - 8 publishing endpoints

6. **EventTaskController** - 1.5 hours
   - 6 task endpoints

7. **TaskTemplateController** - 1 hour
   - 4 template endpoints

### 3.2 EventController Migration Pattern

**File**: `services/event-management-service/src/main/java/ch/batbern/events/controller/EventController.java`

**Migration Steps** (per endpoint):

1. **Update imports**:
   ```java
   // Remove manual DTO imports:
   // import ch.batbern.events.dto.EventResponse;
   // import ch.batbern.events.dto.CreateEventRequest;

   // Add generated DTO imports:
   import ch.batbern.events.dto.generated.events.EventResponse;
   import ch.batbern.events.dto.generated.events.EventDetailResponse;
   import ch.batbern.events.dto.generated.events.CreateEventRequest;
   import ch.batbern.events.dto.generated.events.UpdateEventRequest;
   import ch.batbern.events.dto.generated.events.PatchEventRequest;
   ```

2. **Update method signatures** (from `Map<String, Object>` to typed DTOs):
   ```java
   // Before:
   public ResponseEntity<Map<String, Object>> getEvent(
       @PathVariable String eventCode,
       @RequestParam(required = false) String include
   ) {
       Map<String, Object> response = eventSearchService.getEvent(eventCode, parseIncludes(include));
       return ResponseEntity.ok(response);
   }

   // After:
   public ResponseEntity<EventDetailResponse> getEvent(
       @PathVariable String eventCode,
       @RequestParam(required = false) String include
   ) {
       Set<String> includes = parseIncludes(include);
       EventDetailResponse response = eventService.getEventWithIncludes(eventCode, includes);
       return ResponseEntity.ok(response);
   }
   ```

3. **Remove manual response building**:
   - Delete `buildBasicEventResponse()` method (logic moved to service)
   - Delete `applyResourceExpansions()` method (logic in service layer)
   - Keep domain event publishing in controller (cross-cutting concern)
   - Keep cache management in controller (cross-cutting concern)

4. **Update integration tests**:
   - Change test assertions from `Map<String, Object>` to typed DTOs
   - Use generated DTO builders for request bodies
   - Update all field access from `map.get("field")` to `dto.getField()`

### 3.3 Integration Test Migration Pattern

**Example**: `EventControllerIntegrationTest.java`

**Before**:
```java
@Test
void should_getEvent_when_validEventCode() {
    // Arrange
    Map<String, Object> requestBody = new HashMap<>();
    requestBody.put("title", "Test Event");

    // Act
    ResponseEntity<Map<String, Object>> response = restTemplate.getForEntity(
        "/api/v1/events/BATbern142",
        Map.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("eventCode")).isEqualTo("BATbern142");
}
```

**After**:
```java
@Test
void should_getEvent_when_validEventCode() {
    // Arrange - no manual map needed

    // Act
    ResponseEntity<EventResponse> response = restTemplate.getForEntity(
        "/api/v1/events/BATbern142",
        EventResponse.class
    );

    // Assert
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getEventCode()).isEqualTo("BATbern142");
    assertThat(response.getBody().getTitle()).isNotBlank();
}
```

### 3.4 Per-Controller Testing

**After each controller migration**:

```bash
# Run controller integration tests
./gradlew :services:event-management-service:test --tests "*ControllerIntegrationTest"

# Verify compilation
./gradlew :services:event-management-service:compileJava

# Run quick smoke test
./gradlew :services:event-management-service:bootRun &
curl http://localhost:8082/api/v1/events | jq
```

**Acceptance Criteria** (per controller):
- ✅ All endpoints use generated DTOs (no `Map<String, Object>`)
- ✅ All integration tests pass
- ✅ No compilation errors
- ✅ Manual testing confirms functionality

---

## Phase 4: Cleanup & Validation (2-4 hours)

### 4.1 Delete Manual DTOs (1 hour)

**Goal**: Remove all manual DTOs that have been replaced by generated DTOs

**File Cleanup List** (from `services/event-management-service/src/main/java/ch/batbern/events/dto/`):

**Event DTOs** (DELETE):
- `EventResponse.java`
- `CreateEventRequest.java`
- `UpdateEventRequest.java`
- `PatchEventRequest.java`

**Session DTOs** (DELETE):
- `SessionResponse.java`
- `CreateSessionRequest.java`
- `UpdateSessionRequest.java`
- `SessionSpeakerResponse.java`

**Registration DTOs** (DELETE):
- `RegistrationResponse.java`
- `CreateRegistrationResponse.java`
- `PatchRegistrationRequest.java`

**Batch DTOs** (DELETE):
- `BatchUpdateRequest.java`
- `BatchImportSessionRequest.java`
- `BatchImportSessionResult.java`
- `SessionImportDetail.java`

**Workflow DTOs** (DELETE):
- `TransitionStateRequest.java`
- `WorkflowStatusDto.java`
- `AssignSpeakerRequest.java`
- `SpeakerConfirmationRequest.java`

**Publishing DTOs** (DELETE):
- `PublishingStatusResponse.java`
- `PublishPhaseResponse.java`
- `PublishPreviewResponse.java`
- `PublishValidationError.java`

**Task DTOs** (DELETE):
- `EventTaskResponse.java`
- `TaskTemplateResponse.java`
- `CreateEventTaskRequest.java`
- `CreateTaskTemplateRequest.java`
- `UpdateTaskTemplateRequest.java`

**Speaker Pool DTOs** (DELETE):
- `AddSpeakerToPoolRequest.java`
- `SpeakerPoolResponse.java`
- `SpeakerContentResponse.java`

**Exceptions** (KEEP - Internal DTOs, not part of API contract):
- `TopicFilterRequest.java` - Internal JSON parsing utility
- `TopicUsageHistoryWithEventDetails.java` - JPQL projection DTO (internal)

**Verification**:
```bash
# Count remaining manual DTOs
find services/event-management-service/src/main/java/ch/batbern/events/dto/ -name "*.java" | wc -l
# Should be ~2 (only exceptions above)

# Verify no imports of deleted DTOs
grep -r "import ch.batbern.events.dto.EventResponse" services/event-management-service/src/
# Should return 0 results
```

### 4.2 Frontend Type Regeneration (30 minutes)

**Goal**: Regenerate frontend TypeScript types from updated OpenAPI spec

**Steps**:
```bash
cd web-frontend

# Regenerate types from updated OpenAPI spec
npm run generate:api-types

# Verify generated types
ls src/types/api/events.types.ts

# Check for EventResponse (renamed from Event)
grep "export interface EventResponse" src/types/api/events.types.ts
```

**Frontend Updates Required**:
- Update service layer imports: `Event` → `EventResponse`
- Update component imports: `Session` → `SessionResponse`
- Update type annotations in hooks and components

**Estimated Frontend Changes**: Low (schema fields unchanged, only type names)

### 4.3 Full Test Suite Execution (1 hour)

**Goal**: Verify entire codebase compiles and all tests pass

**Backend Tests**:
```bash
# Full test suite with coverage
./gradlew :services:event-management-service:test

# Expected results:
# - Unit tests: ~50+ passing
# - Integration tests: ~30+ passing
# - Coverage: >80% for migrated code
```

**Frontend Tests**:
```bash
cd web-frontend
npm test

# Expected results:
# - All 2777 tests pass (from previous migration)
# - No new failures from type name changes
```

**Bruno API Contract Tests**:
```bash
./scripts/ci/run-bruno-tests.sh

# Update Bruno test assertions for renamed response types
# Expected: All API contract tests pass
```

### 4.4 Documentation Updates (30 minutes)

**Files to Update**:

1. **Backend Migration Plan**:
   - File: `docs/plans/backend-dto-openapi-migration-plan.md`
   - Update: Mark Story 3 (Events API) as COMPLETE
   - Add: Lessons learned section

2. **ADR-006**:
   - File: `docs/architecture/ADR-006-openapi-contract-first-code-generation.md`
   - Add: Section on Response/Request naming convention
   - Add: TypeConversionUtil pattern documentation

3. **Event Management Service README** (if exists):
   - Update: Architecture section with pure mapper pattern
   - Add: Reference to ARCHITECTURE-PATTERNS.md

4. **Story Documentation**:
   - File: `docs/stories/BAT-19.events-api-migration.md`
   - Update: Mark all tasks as complete
   - Add: Migration completion summary

### 4.5 Linear Issue Updates (15 minutes)

**Issues to Update**:

1. **BAT-19** (Events API Migration):
   - Status: Done
   - Add comment: Migration complete summary

2. **BAT-87** (Controller Migration):
   - Status: Done
   - Add comment: All 7 controllers migrated successfully

3. **BAT-88** (Delete Manual DTOs):
   - Status: Done
   - Add comment: 46 manual DTOs deleted, 2 internal exceptions kept

---

## Critical Files Reference

### OpenAPI Specification
- `docs/api/events-api.openapi.yml` - Primary API contract (Phase 1 changes)

### Domain Entities
- `services/event-management-service/src/main/java/ch/batbern/events/domain/Event.java`
- `services/event-management-service/src/main/java/ch/batbern/events/domain/Session.java`
- `services/event-management-service/src/main/java/ch/batbern/events/domain/Registration.java`

### Mappers (NEW in Phase 2)
- `services/event-management-service/src/main/java/ch/batbern/events/mapper/EventMapper.java`
- `services/event-management-service/src/main/java/ch/batbern/events/mapper/SessionMapper.java`
- `services/event-management-service/src/main/java/ch/batbern/events/mapper/RegistrationMapper.java`
- `services/event-management-service/src/main/java/ch/batbern/events/mapper/TypeConversionUtil.java`

### Services (UPDATE in Phase 2)
- `services/event-management-service/src/main/java/ch/batbern/events/service/EventService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/SessionService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/RegistrationService.java`

### Controllers (UPDATE in Phase 3)
- `services/event-management-service/src/main/java/ch/batbern/events/controller/EventController.java`
- `services/event-management-service/src/main/java/ch/batbern/events/controller/SessionController.java`
- `services/event-management-service/src/main/java/ch/batbern/events/controller/EventWorkflowController.java`
- `services/event-management-service/src/main/java/ch/batbern/events/controller/SessionSpeakerController.java`
- `services/event-management-service/src/main/java/ch/batbern/events/controller/PublishingEngineController.java`
- `services/event-management-service/src/main/java/ch/batbern/events/controller/EventTaskController.java`
- `services/event-management-service/src/main/java/ch/batbern/events/controller/TaskTemplateController.java`

### Build Configuration
- `services/event-management-service/build.gradle` - OpenAPI code generation tasks

### Generated DTOs (Output)
- `services/event-management-service/build/generated-events/src/main/java/ch/batbern/events/dto/generated/events/`

---

## Risk Mitigation & Quality Gates

### Phase Gates (Pause Points)

**After Phase 1**: MUST verify before proceeding to Phase 2
- ✅ OpenAPI spec validates (no broken $ref)
- ✅ Generated DTOs compile
- ✅ New DTO class names match expectations (EventResponse, etc.)

**After Phase 2**: MUST verify before proceeding to Phase 3
- ✅ All service methods return generated DTOs
- ✅ Service layer tests pass
- ✅ No `Map<String, Object>` returns in services

**After each Controller (Phase 3)**: MUST verify before next controller
- ✅ Controller compiles
- ✅ Integration tests pass for that controller
- ✅ Manual smoke test confirms functionality

**After Phase 4**: Final validation
- ✅ All manual DTOs deleted
- ✅ Full test suite passes (backend + frontend)
- ✅ Bruno API tests pass

### Rollback Strategy

**If Phase 1 fails**:
- Revert OpenAPI spec changes
- No code changes yet, safe to abandon

**If Phase 2 fails** (service layer):
- Revert service layer changes
- Keep TypeConversionUtil (useful utility)
- Keep mappers (reusable)

**If Phase 3 fails** (controller X):
- Revert only controller X changes
- Other controllers remain migrated
- System still functional (partial migration acceptable)

### Testing Strategy

**Unit Tests** (per component):
- Mappers: toDto(), toEntity() conversions
- TypeConversionUtil: All conversion methods
- Services: Business logic, DTO returns

**Integration Tests** (per controller):
- All endpoints with generated DTOs
- Resource expansion (`?include=`) functionality
- Error responses (404, 400, 422)
- Validation (@Valid annotations)

**Contract Tests** (Bruno):
- Request/response schema validation
- Field presence and types
- Error response formats

---

## Success Criteria

### Phase 1 Complete
- ✅ All OpenAPI response schemas use Response suffix
- ✅ DTOs regenerated with new class names
- ✅ TypeConversionUtil created with unit tests
- ✅ Build succeeds

### Phase 2 Complete
- ✅ EventMapper, SessionMapper, RegistrationMapper follow Pure Mapper pattern
- ✅ All service methods return generated DTOs
- ✅ No `Map<String, Object>` in service layer
- ✅ All service tests pass

### Phase 3 Complete
- ✅ All 7 controllers migrated to generated DTOs
- ✅ All integration tests pass
- ✅ No compilation errors
- ✅ Resource expansion (`?include=`) works correctly

### Phase 4 Complete
- ✅ 46 manual DTOs deleted
- ✅ Frontend types regenerated
- ✅ Full test suite passes (backend + frontend + Bruno)
- ✅ Documentation updated
- ✅ Linear issues updated

### Overall Migration Complete
- ✅ **Zero Map<String, Object>** in controllers or services
- ✅ **ADR-006 compliance**: All DTOs generated from OpenAPI
- ✅ **ADR-003 compliance**: Meaningful identifiers maintained
- ✅ **Naming consistency**: Response/Request suffix pattern applied
- ✅ **Type safety**: Full type coverage, no `any` or `Object`
- ✅ **Test coverage**: ≥80% for migrated code
- ✅ **No regressions**: All existing functionality preserved

---

## Estimated Effort Breakdown

| Phase | Tasks | Estimated Hours | Can Pause? |
|-------|-------|----------------|------------|
| **Phase 1** | OpenAPI renaming, regeneration, utilities | 4-6 hours | ✅ Yes |
| **Phase 2** | Service layer migration | 6-8 hours | ✅ Yes |
| **Phase 3** | 7 controllers migration | 8-12 hours | ✅ Yes (after each controller) |
| **Phase 4** | Cleanup, testing, documentation | 2-4 hours | ❌ No (final push) |
| **Total** | **End-to-End Migration** | **20-30 hours** | - |

**Recommended Schedule**:
- **Week 1, Day 1**: Phase 1 (4-6 hours) - Foundation complete
- **Week 1, Day 2-3**: Phase 2 (6-8 hours) - Service layer complete
- **Week 2, Day 1-2**: Phase 3 (8-12 hours) - Controllers migrated
- **Week 2, Day 3**: Phase 4 (2-4 hours) - Final validation and completion

---

## Alignment with Dev Agent Findings

### Agreements with Dev Agent Analysis

1. ✅ **Scope Estimate**: Confirmed 19-28 hours (dev agent) vs 20-30 hours (this plan)
2. ✅ **Multi-phase Approach**: Agreed - incremental is safer than big bang
3. ✅ **Service Layer First**: Agreed - complete services before controllers
4. ✅ **One Controller at a Time**: Agreed - reduces cascading failures
5. ✅ **Pure Mapper Pattern**: Agreed - follow TopicMapper pattern

### Differences from Dev Agent Recommendations

1. ❌ **DTO Naming Convention**:
   - Dev agent: Suggested generic `Dto` suffix
   - This plan: **Response/Request suffix** (REST convention, more specific)
   - Rationale: Aligns with existing request DTO patterns, semantically clearer

2. ✅ **Root Cause Analysis**:
   - Dev agent: Identified naming conflicts as blocker
   - This plan: **Deeper analysis** - identified inconsistent OpenAPI naming as root cause
   - Solution: Fix OpenAPI spec, not code workarounds

3. ✅ **Resource Expansion**:
   - Dev agent: Mentioned wrapper DTOs
   - This plan: **Detailed pattern** - EventDetailResponse with optional fields, service layer logic
   - Implementation: Fully contract-compliant, type-safe

---

## Next Steps

1. **Review this plan** with tech lead/senior developer
2. **Approve Phase 1 execution** (4-6 hours, low risk)
3. **Execute Phase 1**, verify phase gate criteria
4. **Review Phase 1 results** before committing to Phase 2
5. **Iterate through phases** with validation at each gate

**Recommendation**: Start with Phase 1 immediately - it's low-risk, reversible, and provides foundation for remaining phases.

---

**Document Status**: ✅ **READY FOR REVIEW & EXECUTION**
**Architect Recommendation**: **APPROVE - Proceed with Phase 1**
