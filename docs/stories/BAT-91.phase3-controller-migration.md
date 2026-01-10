# Story: Phase 3 - Controller Migration

**Linear Issue**: [BAT-91](https://linear.app/batbern/issue/BAT-91) ← **PRIMARY SOURCE**

**Story File**: This file contains **ONLY** dev implementation notes

---

## ⚠️ IMPORTANT: Story Content Location

This file contains **ONLY** dev implementation notes. For story content, see Linear:

- **User Story**: [Linear description](https://linear.app/batbern/issue/BAT-91)
- **Acceptance Criteria**: [Linear issue](https://linear.app/batbern/issue/BAT-91) (see ACs section)
- **Tasks/Subtasks**: [Linear task checklists](https://linear.app/batbern/issue/BAT-91)
- **QA Results**: [Linear comments](https://linear.app/batbern/issue/BAT-91)
- **Status**: [Linear workflow state](https://linear.app/batbern/issue/BAT-91)

---

## Dev Agent Record

### Agent Model Used
{To be filled by dev agent}

### Template References

**Implementation Patterns to Use**:
- Controller patterns from existing migrated controllers (if any)
- Service injection pattern

**Existing Code References**:
- Phase 2: EventService, SessionService (now returning typed DTOs)
- Phase 1: Generated DTOs (EventResponse, SessionResponse, etc.)

### Test Implementation Details (HOW to test)

**CRITICAL**: All backend integration tests MUST use PostgreSQL via Testcontainers. NEVER use H2.

#### Test File Locations (Exact Paths)

**Backend Tests**:
- Integration: `services/event-management-service/src/test/integration/ch/batbern/events/controller/EventControllerIntegrationTest.java`
- Integration: `services/event-management-service/src/test/integration/ch/batbern/events/controller/SessionControllerIntegrationTest.java`
- Integration: `services/event-management-service/src/test/integration/ch/batbern/events/controller/EventWorkflowControllerIntegrationTest.java`
- Integration: (similar for other 4 controllers)

#### Test Data & Mocks Configuration

**Test Containers (MANDATORY)**:
- PostgreSQL 16 Alpine via Testcontainers
- Extend AbstractIntegrationTest
- Use @SpringBootTest with @AutoConfigureMockMvc

**Test Pattern**:
```java
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EventControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void should_returnEventResponse_when_getEventEndpointCalled() throws Exception {
        // Arrange: seed test data

        // Act & Assert
        mockMvc.perform(get("/api/v1/events/{eventCode}", "BATbern142"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.eventCode").value("BATbern142"))
            .andExpect(jsonPath("$.title").exists());
    }
}
```

### Story-Specific Implementation

#### Controller Migration Pattern (Applied to all 7 controllers)

**Step 1: Update Imports**
```java
// REMOVE manual DTO imports:
// import ch.batbern.events.dto.EventResponse;
// import ch.batbern.events.dto.CreateEventRequest;

// ADD generated DTO imports:
import ch.batbern.events.dto.generated.events.EventResponse;
import ch.batbern.events.dto.generated.events.EventDetailResponse;
import ch.batbern.events.dto.generated.events.CreateEventRequest;
import ch.batbern.events.dto.generated.events.UpdateEventRequest;
import ch.batbern.events.dto.generated.events.PatchEventRequest;
import ch.batbern.events.dto.generated.events.SessionResponse;
```

**Step 2: Update Method Signatures**
```java
// BEFORE:
public ResponseEntity<Map<String, Object>> getEvent(
    @PathVariable String eventCode,
    @RequestParam(required = false) String include
) {
    Map<String, Object> response = eventSearchService.getEvent(eventCode, parseIncludes(include));
    return ResponseEntity.ok(response);
}

// AFTER:
public ResponseEntity<EventDetailResponse> getEvent(
    @PathVariable String eventCode,
    @RequestParam(required = false) String include
) {
    Set<String> includes = parseIncludes(include);
    EventDetailResponse response = eventService.getEventWithIncludes(eventCode, includes);
    return ResponseEntity.ok(response);
}
```

**Step 3: Delete Manual Response Building**
```java
// DELETE these methods from controller:
// - buildBasicEventResponse()
// - applyResourceExpansions()

// KEEP these (cross-cutting concerns):
// - Domain event publishing
// - Cache management (@CacheEvict, @Cacheable)
// - parseIncludes() helper method
```

#### Task 3.1: EventController Migration

**File to Modify**:
`services/event-management-service/src/main/java/ch/batbern/events/controller/EventController.java`

**10 Endpoints to Migrate**:
1. `GET /api/v1/events/{eventCode}` → Returns `EventDetailResponse`
2. `POST /api/v1/events` → Returns `EventResponse`
3. `PUT /api/v1/events/{eventCode}` → Returns `EventResponse`
4. `PATCH /api/v1/events/{eventCode}` → Returns `EventResponse`
5. `DELETE /api/v1/events/{eventCode}` → Returns `void`
6. `GET /api/v1/events` → Returns `List<EventResponse>`
7. `POST /api/v1/events/{eventCode}/theme-image` → Returns `EventResponse`
8. (Additional endpoints as per OpenAPI spec)

**Example Migration**:
```java
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private final ApplicationEventPublisher eventPublisher; // KEEP
    private final CacheManager cacheManager; // KEEP

    @GetMapping("/{eventCode}")
    @Cacheable(value = "events", key = "#eventCode") // KEEP
    public ResponseEntity<EventDetailResponse> getEvent(
        @PathVariable String eventCode,
        @RequestParam(required = false) String include
    ) {
        Set<String> includes = parseIncludes(include);
        EventDetailResponse response = eventService.getEventWithIncludes(eventCode, includes);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody CreateEventRequest request) {
        EventResponse response = eventService.createEvent(request);

        // KEEP domain event publishing
        eventPublisher.publishEvent(new EventCreatedEvent(response.getEventCode()));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Helper methods (KEEP)
    private Set<String> parseIncludes(String include) {
        if (include == null || include.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(include.split(","))
            .map(String::trim)
            .collect(Collectors.toSet());
    }
}
```

#### Task 3.2-3.7: Other Controllers

**Migration Approach**: Same pattern as EventController
- Update imports
- Update method signatures
- Call service layer (already returns DTOs from Phase 2)
- Remove manual DTO building
- Keep cross-cutting concerns (events, cache)

**Controllers**:
1. SessionController (6 endpoints)
2. EventWorkflowController (5 endpoints)
3. SessionSpeakerController (4 endpoints)
4. PublishingEngineController (8 endpoints)
5. EventTaskController (6 endpoints)
6. TaskTemplateController (4 endpoints)

#### Integration Test Migration Pattern

**BEFORE**:
```java
@Test
void should_getEvent_when_validEventCode() {
    ResponseEntity<Map<String, Object>> response = restTemplate.getForEntity(
        "/api/v1/events/BATbern142",
        Map.class
    );

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().get("eventCode")).isEqualTo("BATbern142");
}
```

**AFTER**:
```java
@Test
void should_returnEventResponse_when_getEventEndpointCalled() throws Exception {
    mockMvc.perform(get("/api/v1/events/{eventCode}", "BATbern142"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.eventCode").value("BATbern142"))
        .andExpect(jsonPath("$.title").exists())
        .andExpect(jsonPath("$.date").exists());
}

// OR with RestTemplate:
@Test
void should_returnEventResponse_when_getEventEndpointCalled() {
    ResponseEntity<EventDetailResponse> response = restTemplate.getForEntity(
        "/api/v1/events/BATbern142",
        EventDetailResponse.class
    );

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getEventCode()).isEqualTo("BATbern142");
    assertThat(response.getBody().getTitle()).isNotBlank();
}
```

### API Contracts (OpenAPI Excerpts)

```yaml
# From docs/api/events-api.openapi.yml
# All endpoints already defined - controllers must match OpenAPI contract

paths:
  /api/v1/events/{eventCode}:
    get:
      operationId: getEvent
      responses:
        '200':
          description: Event details
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/EventDetailResponse'

  /api/v1/events:
    post:
      operationId: createEvent
      requestBody:
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CreateEventRequest'
      responses:
        '201':
          description: Event created
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/EventResponse'
```

### Database Schema (SQL)

No database changes in Phase 3 - API layer only.

### Implementation Approach
{To be filled by dev agent during implementation}

### Debug Log
See: `.ai/debug-log.md#BAT-91` for detailed implementation debugging

### Completion Notes
{To be filled by dev agent}

### File List
**Created**:
- None (only modifications)

**Modified**:
- EventController.java + EventControllerIntegrationTest.java
- SessionController.java + SessionControllerIntegrationTest.java
- EventWorkflowController.java + EventWorkflowControllerIntegrationTest.java
- SessionSpeakerController.java + SessionSpeakerControllerIntegrationTest.java
- PublishingEngineController.java + PublishingEngineControllerIntegrationTest.java
- EventTaskController.java + EventTaskControllerIntegrationTest.java
- TaskTemplateController.java + TaskTemplateControllerIntegrationTest.java

**Deleted**:
- buildBasicEventResponse() method (from EventController)
- applyResourceExpansions() method (from EventController)
- Similar manual DTO building methods from other controllers

### Change Log
- {date}: {change}

### Deployment Notes
Ready for deployment after Phase 3 completion. All endpoints now use typed DTOs.

### Status
Backlog
