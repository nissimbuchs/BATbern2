# Story: Phase 2 - Service Layer Migration

**Linear Issue**: [BAT-90](https://linear.app/batbern/issue/BAT-90) ← **PRIMARY SOURCE**

**Story File**: This file contains **ONLY** dev implementation notes

---

## ⚠️ IMPORTANT: Story Content Location

This file contains **ONLY** dev implementation notes. For story content, see Linear:

- **User Story**: [Linear description](https://linear.app/batbern/issue/BAT-90)
- **Acceptance Criteria**: [Linear issue](https://linear.app/batbern/issue/BAT-90) (see ACs section)
- **Tasks/Subtasks**: [Linear task checklists](https://linear.app/batbern/issue/BAT-90)
- **QA Results**: [Linear comments](https://linear.app/batbern/issue/BAT-90)
- **Status**: [Linear workflow state](https://linear.app/batbern/issue/BAT-90)

---

## Dev Agent Record

### Agent Model Used
{To be filled by dev agent}

### Template References

**Implementation Patterns to Use**:
- Pure Mapper Pattern: See `services/company-user-management-service/src/main/java/ch/batbern/company/mapper/TopicMapper.java`
- Service Layer Pattern: See existing EventService for structure

**Existing Code References**:
- TopicMapper (Pure Mapper example)
- Phase 1: TypeConversionUtil, ARCHITECTURE-PATTERNS.md

### Test Implementation Details (HOW to test)

**CRITICAL**: All backend integration tests MUST use PostgreSQL via Testcontainers. NEVER use H2.

#### Test File Locations (Exact Paths)

**Backend Tests**:
- Unit: `services/event-management-service/src/test/java/ch/batbern/events/mapper/EventMapperTest.java`
- Unit: `services/event-management-service/src/test/java/ch/batbern/events/mapper/SessionMapperTest.java`
- Unit: `services/event-management-service/src/test/java/ch/batbern/events/service/EventServiceTest.java`
- Unit: `services/event-management-service/src/test/java/ch/batbern/events/service/SessionServiceTest.java`

#### Test Data & Mocks Configuration

**Test Containers (MANDATORY)**:
- PostgreSQL 16 Alpine via Testcontainers for service integration tests
- Extend AbstractIntegrationTest for repository access

**Mock Services**:
- Mock EventRepository, SessionRepository in service unit tests
- Use Mockito for dependency mocking

### Story-Specific Implementation

#### Task 2.1: Create Pure Mappers

**EventMapper Path**:
`services/event-management-service/src/main/java/ch/batbern/events/mapper/EventMapper.java`

**EventMapper Implementation**:
```java
package ch.batbern.events.mapper;

import ch.batbern.events.domain.Event;
import ch.batbern.events.dto.generated.events.*;
import org.springframework.stereotype.Component;

@Component
public class EventMapper {

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

    public EventDetailResponse toDetailDto(Event entity) {
        if (entity == null) return null;

        EventDetailResponse dto = new EventDetailResponse();
        // Copy all EventResponse fields
        // ... (same as toDto above)

        // Optional fields left null - caller populates based on ?include=
        return dto;
    }

    public Event toEntity(CreateEventRequest request) {
        if (request == null) return null;

        Event entity = new Event();
        entity.setTitle(request.getTitle());
        entity.setEventNumber(request.getEventNumber());
        entity.setDate(TypeConversionUtil.toInstant(request.getDate()));
        // ... map all create fields
        return entity;
    }

    public void applyUpdateRequest(Event entity, UpdateEventRequest request) {
        if (entity == null || request == null) return;

        entity.setTitle(request.getTitle());
        entity.setDate(TypeConversionUtil.toInstant(request.getDate()));
        // ... update all fields
    }

    public void applyPatchRequest(Event entity, PatchEventRequest request) {
        if (entity == null || request == null) return;

        if (request.getTitle() != null) {
            entity.setTitle(request.getTitle());
        }
        // ... patch only non-null fields
    }
}
```

**SessionMapper Path**:
`services/event-management-service/src/main/java/ch/batbern/events/mapper/SessionMapper.java`

**SessionMapper Implementation**:
```java
package ch.batbern.events.mapper;

import ch.batbern.events.domain.Session;
import ch.batbern.events.dto.generated.events.*;
import org.springframework.stereotype.Component;

@Component
public class SessionMapper {

    public SessionResponse toDto(Session entity) {
        if (entity == null) return null;

        SessionResponse dto = new SessionResponse();
        dto.setSessionCode(entity.getSessionCode());
        dto.setTitle(entity.getTitle());
        dto.setStartTime(TypeConversionUtil.toOffsetDateTime(entity.getStartTime()));
        dto.setEndTime(TypeConversionUtil.toOffsetDateTime(entity.getEndTime()));
        // ... map all fields
        return dto;
    }

    public Session toEntity(CreateSessionRequest request) {
        if (request == null) return null;

        Session entity = new Session();
        entity.setTitle(request.getTitle());
        entity.setStartTime(TypeConversionUtil.toInstant(request.getStartTime()));
        // ... map all create fields
        return entity;
    }

    public void applyUpdateRequest(Session entity, UpdateSessionRequest request) {
        if (entity == null || request == null) return;

        entity.setTitle(request.getTitle());
        // ... update all fields
    }
}
```

#### Task 2.2: Migrate EventService

**File to Modify**:
`services/event-management-service/src/main/java/ch/batbern/events/service/EventService.java`

**Changes**:
```java
@Service
@Transactional
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final EventMapper eventMapper; // ADD
    private final SessionRepository sessionRepository;
    private final SessionMapper sessionMapper; // ADD
    private final VenueMapper venueMapper; // ADD (if exists)

    // BEFORE: public Map<String, Object> getEvent(String eventCode)
    // AFTER:
    public EventResponse getEvent(String eventCode) {
        Event event = eventRepository.findByEventCode(eventCode)
            .orElseThrow(() -> new EventNotFoundException(eventCode));
        return eventMapper.toDto(event);
    }

    // NEW METHOD for resource expansion
    public EventDetailResponse getEventWithIncludes(String eventCode, Set<String> includes) {
        Event event = eventRepository.findByEventCode(eventCode)
            .orElseThrow(() -> new EventNotFoundException(eventCode));

        EventDetailResponse response = eventMapper.toDetailDto(event);

        if (includes.contains("venue") && event.getVenue() != null) {
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

    // BEFORE: public Map<String, Object> createEvent(CreateEventRequest request)
    // AFTER:
    public EventResponse createEvent(CreateEventRequest request) {
        Event entity = eventMapper.toEntity(request);
        Event saved = eventRepository.save(entity);
        return eventMapper.toDto(saved);
    }

    // BEFORE: public Map<String, Object> updateEvent(...)
    // AFTER:
    public EventResponse updateEvent(String eventCode, UpdateEventRequest request) {
        Event entity = eventRepository.findByEventCode(eventCode)
            .orElseThrow(() -> new EventNotFoundException(eventCode));
        eventMapper.applyUpdateRequest(entity, request);
        Event saved = eventRepository.save(entity);
        return eventMapper.toDto(saved);
    }

    // DELETE: buildBasicEventResponse() method
    // DELETE: applyResourceExpansions() method (logic moved to getEventWithIncludes)
}
```

#### Task 2.3: Migrate SessionService

**File to Modify**:
`services/event-management-service/src/main/java/ch/batbern/events/service/SessionService.java`

**Changes**:
```java
@Service
@Transactional
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;
    private final SessionMapper sessionMapper; // ADD

    // BEFORE: private SessionResponse toSessionResponse(Session session)
    // AFTER: DELETE method, use sessionMapper.toDto()

    // Update all methods to use sessionMapper
    public SessionResponse getSession(String sessionCode) {
        Session session = sessionRepository.findBySessionCode(sessionCode)
            .orElseThrow(() -> new SessionNotFoundException(sessionCode));
        return sessionMapper.toDto(session);
    }

    public List<SessionResponse> findByEventCode(String eventCode) {
        return sessionRepository.findByEventCode(eventCode).stream()
            .map(sessionMapper::toDto)
            .toList();
    }
}
```

### API Contracts (OpenAPI Excerpts)

```yaml
# From docs/api/events-api.openapi.yml (after Phase 1 renaming)

paths:
  /api/v1/events/{eventCode}:
    get:
      parameters:
        - name: include
          in: query
          schema:
            type: string
          description: "Comma-separated: venue,sessions"
      responses:
        '200':
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/EventDetailResponse'

components:
  schemas:
    EventResponse:
      type: object
      required: [eventCode, title, date]
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

No database changes in Phase 2 - mapping layer only.

### Implementation Approach
{To be filled by dev agent during implementation}

### Debug Log
See: `.ai/debug-log.md#BAT-90` for detailed implementation debugging

### Completion Notes
{To be filled by dev agent}

### File List
**Created**:
- EventMapper.java
- EventMapperTest.java
- SessionMapper.java
- SessionMapperTest.java

**Modified**:
- EventService.java
- SessionService.java
- EventServiceTest.java
- SessionServiceTest.java
- RegistrationMapper.java (update schema names)

**Deleted**:
- buildBasicEventResponse() method
- toSessionResponse() method

### Change Log
- {date}: {change}

### Deployment Notes
No deployment required until Phase 3 (controllers not yet updated).

### Status
Backlog
