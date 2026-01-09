# Story: Phase 1 - Architectural Foundation

**Linear Issue**: [BAT-89](https://linear.app/batbern/issue/BAT-89) ← **PRIMARY SOURCE**

**Story File**: This file contains **ONLY** dev implementation notes

---

## ⚠️ IMPORTANT: Story Content Location

This file contains **ONLY** dev implementation notes. For story content, see Linear:

- **User Story**: [Linear description](https://linear.app/batbern/issue/BAT-89)
- **Acceptance Criteria**: [Linear issue](https://linear.app/batbern/issue/BAT-89) (see ACs section)
- **Tasks/Subtasks**: [Linear task checklists](https://linear.app/batbern/issue/BAT-89)
- **QA Results**: [Linear comments](https://linear.app/batbern/issue/BAT-89)
- **Status**: [Linear workflow state](https://linear.app/batbern/issue/BAT-89)

---

## Dev Agent Record

### Agent Model Used
{To be filled by dev agent}

### Template References

**Implementation Patterns to Use**:
- No specific templates - foundational infrastructure work
- Follow existing OpenAPI generation patterns from other services

**Existing Code References**:
- TopicMapper pattern: `services/company-user-management-service/src/main/java/ch/batbern/company/mapper/TopicMapper.java`
- Similar type utilities may exist in other services for reference

### Test Implementation Details (HOW to test)

**CRITICAL**: All backend integration tests MUST use PostgreSQL via Testcontainers. NEVER use H2.

#### Test File Locations (Exact Paths)

**Backend Tests**:
- Unit: `services/event-management-service/src/test/java/ch/batbern/events/mapper/TypeConversionUtilTest.java`
- Integration: N/A for Phase 1 (utility testing only)

#### Test Data & Mocks Configuration

**Test Containers (MANDATORY)**:
- PostgreSQL 16 Alpine via Testcontainers (not needed for Phase 1)
- Phase 1 only requires unit tests for TypeConversionUtil

**Test Configuration**:
```properties
# Standard test configuration - no special requirements for Phase 1
```

### Story-Specific Implementation

#### Task 1.1: OpenAPI Schema Renaming

**Files to Modify**:
- `docs/api/events-api.openapi.yml`

**Search & Replace Strategy**:
```yaml
# Find schema definitions in components.schemas section (around line 3092)
# Pattern to find:
components:
  schemas:
    Event:              # Line ~3092
    EventDetail:        # Line ~3205
    Session:            # Search in components
    Registration:       # Search in components
    Venue:              # Line ~3217
    Speaker:            # Line ~3229

# Rename to:
    EventResponse:
    EventDetailResponse:
    SessionResponse:
    RegistrationResponse:
    VenueResponse:
    SpeakerResponse:

# Update all $ref references:
# Find: $ref: '#/components/schemas/Event'
# Replace: $ref: '#/components/schemas/EventResponse'
# (Repeat for all renamed schemas)
```

**Verification Commands**:
```bash
# Count references to old Event schema (should be 0 after)
grep -c "\$ref: '#/components/schemas/Event'" docs/api/events-api.openapi.yml

# Verify EventResponse exists
grep -c "EventResponse:" docs/api/events-api.openapi.yml

# Validate OpenAPI spec (if validator available)
npx @redocly/cli lint docs/api/events-api.openapi.yml
```

#### Task 1.2: Regenerate Backend DTOs

**Build Configuration**:
- `services/event-management-service/build.gradle` contains openApiGenerateEvents task

**Commands**:
```bash
# Clean previous generated code
rm -rf services/event-management-service/build/generated-events/

# Regenerate DTOs
./gradlew :services:event-management-service:openApiGenerateEvents

# Verify generated classes
ls -la services/event-management-service/build/generated-events/src/main/java/ch/batbern/events/dto/generated/events/

# Expected output: EventResponse.java, SessionResponse.java, etc.
```

**Generated Package**:
- Package: `ch.batbern.events.dto.generated.events`
- Output: `services/event-management-service/build/generated-events/src/main/java/ch/batbern/events/dto/generated/events/`

#### Task 1.3: Create Type Conversion Utility

**File Path**:
`services/event-management-service/src/main/java/ch/batbern/events/mapper/TypeConversionUtil.java`

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
 * Handles conversions between domain entities and generated DTOs.
 */
public final class TypeConversionUtil {

    private TypeConversionUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    // Date/Time Conversions
    public static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant != null ? instant.atOffset(ZoneOffset.UTC) : null;
    }

    public static Instant toInstant(OffsetDateTime offsetDateTime) {
        return offsetDateTime != null ? offsetDateTime.toInstant() : null;
    }

    public static OffsetDateTime localDateTimeToOffsetDateTime(java.time.LocalDateTime localDateTime) {
        return localDateTime != null
            ? localDateTime.atZone(ZoneId.systemDefault()).toOffsetDateTime()
            : null;
    }

    // URL/URI Conversions
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

    public static String fromURI(URI uri) {
        return uri != null ? uri.toString() : null;
    }
}
```

**Test File Path**:
`services/event-management-service/src/test/java/ch/batbern/events/mapper/TypeConversionUtilTest.java`

**Test Cases**:
```java
package ch.batbern.events.mapper;

import org.junit.jupiter.api.Test;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import static org.assertj.core.api.Assertions.*;

class TypeConversionUtilTest {

    @Test
    void should_convertInstantToOffsetDateTime_when_validInstantProvided() {
        Instant instant = Instant.parse("2024-01-15T10:30:00Z");
        OffsetDateTime result = TypeConversionUtil.toOffsetDateTime(instant);
        assertThat(result).isNotNull();
        assertThat(result.toInstant()).isEqualTo(instant);
    }

    @Test
    void should_returnNull_when_nullInstantProvided() {
        assertThat(TypeConversionUtil.toOffsetDateTime(null)).isNull();
        assertThat(TypeConversionUtil.toInstant(null)).isNull();
    }

    @Test
    void should_convertStringToURI_when_validURIProvided() {
        String uriString = "https://example.com/image.png";
        URI result = TypeConversionUtil.toURI(uriString);
        assertThat(result).isNotNull();
        assertThat(result.toString()).isEqualTo(uriString);
    }

    @Test
    void should_throwException_when_invalidURIProvided() {
        assertThatThrownBy(() -> TypeConversionUtil.toURI("not a valid uri://\\\\"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid URI");
    }

    @Test
    void should_returnNull_when_emptyOrNullURI() {
        assertThat(TypeConversionUtil.toURI(null)).isNull();
        assertThat(TypeConversionUtil.toURI("")).isNull();
        assertThat(TypeConversionUtil.toURI("   ")).isNull();
    }

    @Test
    void should_roundTripInstantOffsetDateTime() {
        Instant original = Instant.now();
        OffsetDateTime converted = TypeConversionUtil.toOffsetDateTime(original);
        Instant backToInstant = TypeConversionUtil.toInstant(converted);
        assertThat(backToInstant).isEqualTo(original);
    }
}
```

#### Task 1.4: Define Service Layer Contract

**File Path**:
`services/event-management-service/ARCHITECTURE-PATTERNS.md`

**Content Template**:
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

## Pure Mapper Pattern

Mappers MUST be pure functions with NO business logic or repository dependencies.

```java
@Component
public class EventMapper {

    public EventResponse toDto(Event entity) {
        if (entity == null) return null;

        EventResponse dto = new EventResponse();
        dto.setEventCode(entity.getEventCode());
        dto.setTitle(entity.getTitle());
        dto.setDate(TypeConversionUtil.toOffsetDateTime(entity.getDate()));
        // ... map all fields
        return dto;
    }
}
```

## Resource Expansion Pattern

For endpoints with `?include=` parameter, use wrapper response DTOs with optional fields:

```java
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

### API Contracts (OpenAPI Excerpts)

```yaml
# From docs/api/events-api.openapi.yml
# Key schemas after renaming:

components:
  schemas:
    EventResponse:
      type: object
      properties:
        eventCode: { type: string }
        title: { type: string }
        date: { type: string, format: date-time }
        # ... other fields

    EventDetailResponse:
      allOf:
        - $ref: '#/components/schemas/EventResponse'
        - type: object
          properties:
            venue:
              $ref: '#/components/schemas/VenueResponse'
            sessions:
              type: array
              items:
                $ref: '#/components/schemas/SessionResponse'
```

### Database Schema (SQL)

No database changes in Phase 1.

### Implementation Approach
{To be filled by dev agent during implementation}

### Debug Log
See: `.ai/debug-log.md#BAT-89` for detailed implementation debugging

### Completion Notes
{To be filled by dev agent}

### File List
**Created**:
- TypeConversionUtil.java
- TypeConversionUtilTest.java
- ARCHITECTURE-PATTERNS.md

**Modified**:
- docs/api/events-api.openapi.yml

**Deleted**:
- None

### Change Log
- {date}: {change}

### Deployment Notes
No deployment required - foundation only. Changes are build-time only until Phase 2.

### Status
Backlog
