# BAT-14: Backend - Participant Batch Registration

**Linear**: [BAT-14](https://linear.app/batbern/issue/BAT-14)
**Status**: Blocked
**Epic**: Epic 3 - Historical Data Migration
**Project**: [Epic 3: Historical Data Migration](https://linear.app/batbern/project/epic-3-historical-data-migration-168670d74297)
**Created**: 2025-12-25

---

## Story

**As a** system,
**I want** to create multiple event registrations for a participant in a single transactional API call,
**so that** batch imports are efficient and maintain data integrity.

**Backend Focus:** This story implements the business logic and data layer that validates against the API contract defined in [BAT-12](https://linear.app/batbern/issue/BAT-12).

---

## Dependencies

**Blocked By:**
- ⚠️ [BAT-12](https://linear.app/batbern/issue/BAT-12) (API Contract) must be Done first
- Requires: OpenAPI specification
- Requires: Java DTOs generated from API contract
- Requires: Contract tests defined

**Blocks:**
- [BAT-15](https://linear.app/batbern/issue/BAT-15) (Integration) is BLOCKED until this story is Done

---

## Domain Context

### Primary Domain
**Domain**: Event Management Domain

**Service**: Event Management Service (`services/event-management-service`)

### Involved Services
- **Event Management Service** (primary) - Create event registrations
- **Company/User Management Service** (consumed) - Get-or-create users via internal API

### Cross-Domain Dependencies

**API Calls to Other Services:**
- `POST /internal/users` - Get-or-create user by email (Company/User Management Service)
- Returns: `{ userId, username, email, role }`

**EventBridge Events Published:** None (synchronous operation)

### API Contract Reference
- **Story**: [BAT-12](https://linear.app/batbern/issue/BAT-12)
- **OpenAPI Spec**: `docs/api/events-api.openapi.yml`
- **Endpoint**: `POST /api/v1/events/batch_registrations`
- **Generated DTOs**: `BatchRegistrationRequest`, `BatchRegistrationResponse`, `BatchRegistrationItem`, `FailedRegistration`

---

## Service Specifications

### Domain Model

**No New Aggregates** - Uses existing `Event` and `EventRegistration` aggregates.

**Behavioral Changes:**
- `EventRegistration` aggregate gains batch creation capability
- Idempotency: Skip creating duplicate registrations (same user + event)

### Service Layer

**New Service Methods** (in `RegistrationService`):

```java
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final EventRepository eventRepository;
    private final RegistrationRepository registrationRepository;
    private final UserServiceClient userServiceClient;

    /**
     * Create batch event registrations for a participant.
     *
     * - Creates user if doesn't exist (via UserServiceClient)
     * - Creates registrations for all events
     * - Skips duplicate registrations (idempotent)
     * - Returns detailed results with partial success support
     *
     * @param request Batch registration request
     * @return Batch registration response with results
     */
    @Transactional
    public BatchRegistrationResponse createBatchRegistrations(BatchRegistrationRequest request) {
        // 1. Get-or-create user
        UserDto user = userServiceClient.getOrCreateUser(
            request.getParticipantEmail(),
            request.getFirstName(),
            request.getLastName(),
            UserRole.ATTENDEE
        );

        // 2. Process each registration
        List<FailedRegistration> failedRegistrations = new ArrayList<>();
        int successCount = 0;

        for (BatchRegistrationItem item : request.getRegistrations()) {
            try {
                // Validate event exists
                Event event = eventRepository.findByCode(item.getEventCode())
                    .orElseThrow(() -> new EventNotFoundException(item.getEventCode()));

                // Check if registration already exists (idempotency)
                boolean exists = registrationRepository.existsByUserIdAndEventId(
                    user.getUserId(),
                    event.getId()
                );

                if (exists) {
                    // Skip duplicate
                    continue;
                }

                // Create registration
                EventRegistration registration = EventRegistration.create(
                    user.getUserId(),
                    event.getId(),
                    RegistrationStatus.fromString(item.getStatus())
                );

                registrationRepository.save(registration);
                successCount++;

            } catch (EventNotFoundException e) {
                failedRegistrations.add(new FailedRegistration(
                    item.getEventCode(),
                    "Event not found"
                ));
            } catch (Exception e) {
                failedRegistrations.add(new FailedRegistration(
                    item.getEventCode(),
                    e.getMessage()
                ));
            }
        }

        // 3. Build response
        return BatchRegistrationResponse.builder()
            .username(user.getUsername())
            .totalRegistrations(request.getRegistrations().size())
            .successfulRegistrations(successCount)
            .failedRegistrations(failedRegistrations)
            .errors(failedRegistrations.stream()
                .map(f -> String.format("Event %s: %s", f.getEventCode(), f.getReason()))
                .collect(Collectors.toList()))
            .build();
    }
}
```

### Repository Layer

**Existing Repositories** (no new repositories needed):

```java
public interface EventRepository extends JpaRepository<Event, UUID> {
    Optional<Event> findByCode(String code);
}

public interface RegistrationRepository extends JpaRepository<EventRegistration, UUID> {
    boolean existsByUserIdAndEventId(UUID userId, UUID eventId);
}
```

### Controller Layer

**New Controller Endpoint** (in `EventController`):

```java
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final RegistrationService registrationService;

    /**
     * Create batch event registrations for a participant.
     *
     * Requires ORGANIZER role.
     */
    @PostMapping("/batch_registrations")
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<BatchRegistrationResponse> createBatchRegistrations(
        @Valid @RequestBody BatchRegistrationRequest request
    ) {
        BatchRegistrationResponse response = registrationService.createBatchRegistrations(request);
        return ResponseEntity.ok(response);
    }
}
```

### User Service Client

**New Feign Client** (or HTTP Client):

```java
@FeignClient(name = "user-service", url = "${services.user-service.url}")
public interface UserServiceClient {

    /**
     * Get or create user by email.
     *
     * If user exists: returns existing user
     * If user doesn't exist: creates user with cognitoSync=false (ADR-005)
     */
    @PostMapping("/internal/users")
    UserDto getOrCreateUser(
        @RequestParam String email,
        @RequestParam String firstName,
        @RequestParam String lastName,
        @RequestParam UserRole role
    );
}
```

---

## Database Design

### Schema Changes

**No schema changes needed** - Uses existing `events` and `event_registrations` tables.

**Required Indexes** (verify exist):
```sql
-- events table
CREATE INDEX IF NOT EXISTS idx_events_code ON events(code);

-- event_registrations table
CREATE UNIQUE INDEX IF NOT EXISTS idx_registrations_user_event
    ON event_registrations(user_id, event_id);
```

**Notes:**
- Unique index `idx_registrations_user_event` provides idempotency (prevents duplicate registrations)
- Index `idx_events_code` optimizes event lookup by code

---

## Acceptance Criteria

1. **Batch Registration API**
   - [ ] Endpoint `POST /api/v1/events/batch_registrations` implemented
   - [ ] Request validation matches API contract
   - [ ] Response schema matches API contract
   - [ ] Requires ORGANIZER role (403 if unauthorized)

2. **User Creation**
   - [ ] Calls User Service to get-or-create user
   - [ ] Uses participant email for lookup
   - [ ] Creates users with role ATTENDEE
   - [ ] Creates users with `cognitoSync=false` (ADR-005)
   - [ ] Returns existing user if email already exists

3. **Registration Creation**
   - [ ] Creates registration for each event in request
   - [ ] Uses event code (e.g., "BATbern25") for lookup
   - [ ] Sets registration status from request ("attended" for historical)
   - [ ] Skips duplicate registrations (idempotent)
   - [ ] Returns detailed results (success/failed per registration)

4. **Error Handling**
   - [ ] Returns 400 if request validation fails
   - [ ] Returns 404 for non-existent events (in failedRegistrations)
   - [ ] Returns 500 only for unexpected server errors
   - [ ] Partial success handled correctly (some succeed, some fail)
   - [ ] Transaction rolls back only on critical errors

5. **Performance**
   - [ ] Handles batch of 100 registrations in <2 seconds
   - [ ] Uses database transaction for atomicity
   - [ ] Optimized queries (no N+1 problems)

---

## Tasks / Subtasks (TDD Workflow)

- [ ] Task 1: Generate DTOs from OpenAPI Spec
  - [ ] Run OpenAPI Generator for Java DTOs
  - [ ] Verify DTOs match API contract
  - [ ] Add DTOs to version control

- [ ] Task 2: Write Contract Tests (RED Phase)
  - [ ] Write failing test: `should_acceptValidRequest_when_allFieldsProvided`
  - [ ] Write failing test: `should_return200_when_batchRegistrationSucceeds`
  - [ ] Write failing test: `should_returnPartialSuccess_when_someRegistrationsFail`
  - [ ] Write failing test: `should_return400_when_invalidRequest`
  - [ ] Write failing test: `should_return403_when_notOrganizer`
  - [ ] Verify contract tests fail appropriately

- [ ] Task 3: Write Service Layer Tests (RED Phase)
  - [ ] Write failing test: `should_createUser_when_emailNotExists`
  - [ ] Write failing test: `should_useExistingUser_when_emailExists`
  - [ ] Write failing test: `should_createRegistrations_when_validEvents`
  - [ ] Write failing test: `should_skipDuplicates_when_registrationExists`
  - [ ] Write failing test: `should_returnFailedRegistrations_when_eventNotFound`
  - [ ] Verify service tests fail appropriately

- [ ] Task 4: Create User Service Client (GREEN Phase)
  - [ ] Create `UserServiceClient` interface
  - [ ] Implement Feign client configuration
  - [ ] Add service URL configuration in `application.yml`
  - [ ] Add circuit breaker (Resilience4j) for fault tolerance

- [ ] Task 5: Implement Service Layer (GREEN Phase)
  - [ ] Add `createBatchRegistrations` method to `RegistrationService`
  - [ ] Implement user get-or-create logic
  - [ ] Implement registration creation with idempotency check
  - [ ] Implement error handling and partial success logic
  - [ ] Add `@Transactional` annotation
  - [ ] Verify service tests pass

- [ ] Task 6: Implement Controller Layer (GREEN Phase)
  - [ ] Add `createBatchRegistrations` endpoint to `EventController`
  - [ ] Add `@PreAuthorize("hasRole('ORGANIZER')")` annotation
  - [ ] Map DTOs between API and service layer
  - [ ] Add controller-level validation
  - [ ] Verify contract tests pass

- [ ] Task 7: Integration Testing (GREEN Phase)
  - [ ] Write integration test with Testcontainers (PostgreSQL)
  - [ ] Mock User Service Client (WireMock)
  - [ ] Test full request/response flow
  - [ ] Test duplicate registration handling
  - [ ] Test partial success scenarios
  - [ ] Verify integration tests pass

- [ ] Task 8: Refactor (REFACTOR Phase)
  - [ ] Extract reusable logic
  - [ ] Improve error messages
  - [ ] Add code documentation
  - [ ] Verify all tests still pass

- [ ] Task 9: Performance Optimization
  - [ ] Add database indexes (if not exist)
  - [ ] Use batch fetching for events (if multiple lookups needed)
  - [ ] Load test with 100 registrations
  - [ ] Verify performance <2 seconds

---

## Dev Notes - Implementation Guide

### Service Structure

```
services/event-management-service/
├── src/main/java/ch/batbern/events/
│   ├── controller/
│   │   └── EventController.java (add endpoint)
│   ├── service/
│   │   └── RegistrationService.java (add method)
│   ├── client/
│   │   └── UserServiceClient.java (new)
│   ├── dto/
│   │   ├── BatchRegistrationRequest.java (generated)
│   │   ├── BatchRegistrationResponse.java (generated)
│   │   ├── BatchRegistrationItem.java (generated)
│   │   └── FailedRegistration.java (generated)
│   └── exception/
│       └── EventNotFoundException.java (existing)
└── src/test/
    ├── integration/
    │   └── BatchRegistrationIntegrationTest.java (new)
    └── contract/
        └── BatchRegistrationContractTest.java (new)
```

### OpenAPI Code Generation

**Command**:
```bash
cd services/event-management-service
./gradlew openApiGenerate
```

**Configuration** (`build.gradle`):
```gradle
openApiGenerate {
    generatorName = "spring"
    inputSpec = "$rootDir/docs/api/events-api.openapi.yml"
    outputDir = "$buildDir/generated"
    apiPackage = "ch.batbern.events.api"
    modelPackage = "ch.batbern.events.dto"
    configOptions = [
        dateLibrary: "java8",
        interfaceOnly: "true",
        useTags: "true"
    ]
}
```

### User Service Client Configuration

**application.yml**:
```yaml
services:
  user-service:
    url: ${USER_SERVICE_URL:http://localhost:8081}

resilience4j:
  circuitbreaker:
    instances:
      userService:
        registerHealthIndicator: true
        failureRateThreshold: 50
        waitDurationInOpenState: 10000
        permittedNumberOfCallsInHalfOpenState: 3
        slidingWindowSize: 10
```

### Integration Test Example

**File**: `src/test/integration/BatchRegistrationIntegrationTest.java`

```java
@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
class BatchRegistrationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @MockBean
    private UserServiceClient userServiceClient;

    @BeforeEach
    void setup() {
        // Create test events
        Event event1 = Event.create("BATbern25", "Event 25", ...);
        Event event2 = Event.create("BATbern31", "Event 31", ...);
        eventRepository.saveAll(List.of(event1, event2));

        // Mock user service client
        when(userServiceClient.getOrCreateUser(anyString(), anyString(), anyString(), any()))
            .thenReturn(new UserDto(UUID.randomUUID(), "test.user", "test@example.com", UserRole.ATTENDEE));
    }

    @Test
    void should_createBatchRegistrations_when_validRequestProvided() throws Exception {
        // Arrange
        BatchRegistrationRequest request = new BatchRegistrationRequest()
            .participantEmail("test@example.com")
            .firstName("Test")
            .lastName("User")
            .registrations(List.of(
                new BatchRegistrationItem().eventCode("BATbern25").status("attended"),
                new BatchRegistrationItem().eventCode("BATbern31").status("attended")
            ));

        // Act & Assert
        mockMvc.perform(post("/api/v1/events/batch_registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(request))
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").exists())
            .andExpect(jsonPath("$.totalRegistrations").value(2))
            .andExpect(jsonPath("$.successfulRegistrations").value(2))
            .andExpect(jsonPath("$.failedRegistrations").isEmpty());

        // Verify registrations created
        assertThat(registrationRepository.findAll()).hasSize(2);
    }

    @Test
    void should_skipDuplicates_when_registrationAlreadyExists() throws Exception {
        // Arrange
        UUID userId = UUID.randomUUID();
        when(userServiceClient.getOrCreateUser(anyString(), anyString(), anyString(), any()))
            .thenReturn(new UserDto(userId, "test.user", "test@example.com", UserRole.ATTENDEE));

        Event event = eventRepository.findByCode("BATbern25").orElseThrow();
        EventRegistration existing = EventRegistration.create(userId, event.getId(), RegistrationStatus.ATTENDED);
        registrationRepository.save(existing);

        BatchRegistrationRequest request = new BatchRegistrationRequest()
            .participantEmail("test@example.com")
            .firstName("Test")
            .lastName("User")
            .registrations(List.of(
                new BatchRegistrationItem().eventCode("BATbern25").status("attended")
            ));

        // Act & Assert
        mockMvc.perform(post("/api/v1/events/batch_registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(request))
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.successfulRegistrations").value(0)); // Skipped duplicate

        // Verify no new registrations created
        assertThat(registrationRepository.findAll()).hasSize(1);
    }

    @Test
    void should_returnPartialSuccess_when_someEventsMissing() throws Exception {
        // Arrange
        BatchRegistrationRequest request = new BatchRegistrationRequest()
            .participantEmail("test@example.com")
            .firstName("Test")
            .lastName("User")
            .registrations(List.of(
                new BatchRegistrationItem().eventCode("BATbern25").status("attended"),
                new BatchRegistrationItem().eventCode("BATbern99").status("attended") // Missing
            ));

        // Act & Assert
        mockMvc.perform(post("/api/v1/events/batch_registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(request))
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalRegistrations").value(2))
            .andExpect(jsonPath("$.successfulRegistrations").value(1))
            .andExpect(jsonPath("$.failedRegistrations").isArray())
            .andExpect(jsonPath("$.failedRegistrations[0].eventCode").value("BATbern99"))
            .andExpect(jsonPath("$.failedRegistrations[0].reason").value("Event not found"));
    }

    @Test
    void should_return403_when_notOrganizerRole() throws Exception {
        // Arrange
        BatchRegistrationRequest request = new BatchRegistrationRequest()
            .participantEmail("test@example.com")
            .firstName("Test")
            .lastName("User")
            .registrations(List.of());

        // Act & Assert
        mockMvc.perform(post("/api/v1/events/batch_registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(request))
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ATTENDEE")))) // Wrong role
            .andExpect(status().isForbidden());
    }
}
```

---

## Definition of Done Checklist

### Development Complete
- [ ] All tests written BEFORE implementation (TDD followed)
- [ ] All acceptance criteria have corresponding tests
- [ ] All acceptance criteria implemented
- [ ] Service layer tests passing (>90% coverage)
- [ ] Contract tests passing (validates OpenAPI spec)
- [ ] Integration tests passing
- [ ] Code follows DDD patterns
- [ ] Code follows Spring Boot best practices

### API Contract Validated
- [ ] Endpoint matches OpenAPI spec exactly
- [ ] Request validation matches contract
- [ ] Response schemas match contract
- [ ] Error responses match contract format
- [ ] Authentication enforced per contract (ORGANIZER role)
- [ ] Contract tests pass 100%

### Integration Ready
- [ ] Service deployed to dev environment
- [ ] Health check endpoint responding
- [ ] Metrics and logging configured
- [ ] Ready for frontend integration (Story 3.2.1d)
- [ ] API documented and accessible via Swagger UI

---

## Integration Notes for Story 3.2.1d

- **Service Endpoint**: `POST https://api-dev.batbern.ch/api/v1/events/batch_registrations`
- **Authentication**: Requires ORGANIZER role
- **Expected Performance**: <2 seconds for 100 registrations
- **Known Limitations**: User Service must be available (circuit breaker configured)

---

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2025-12-25 | 0.1 | Initial backend story creation | Bob (SM) |

---

## Dev Agent Record

_This section will be populated during implementation by the dev agent._

### Agent Model Used
_To be filled by dev agent_

### Implementation Approach
_To be filled by dev agent_

### File List
_To be filled by dev agent:_
- Created: [list of new files]
- Modified: [list of changed files]
- Deleted: [list of removed files]

### Performance Notes
_To be filled by dev agent:_
- Query performance
- Caching strategy implemented
- Known bottlenecks
- Optimization opportunities
