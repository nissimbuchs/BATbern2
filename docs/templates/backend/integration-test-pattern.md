# Integration Test Pattern

**Category**: Backend - Testing
**Used in Stories**: 2.7 (Partner Coordination), 2.8.1 (Partner Directory Backend), 5.5 (Speaker Content & Task System), All API development stories
**Last Updated**: 2025-12-24
**Source**: Extracted from `docs/architecture/coding-standards.md`

## Overview

Integration tests verify that your service works correctly with real dependencies (database, HTTP clients, Spring context). BATbern uses **Testcontainers with PostgreSQL** for integration tests to ensure production parity.

**Use this pattern when**:
- Testing REST API endpoints (controllers)
- Testing database operations (repositories with PostgreSQL-specific features)
- Testing service layer with real database
- Testing HTTP client integrations
- Verifying Spring Security configuration

**DON'T use in-memory databases** (H2, HSQLDB) for integration tests - they hide PostgreSQL-specific issues and create false confidence.

## Prerequisites

```gradle
// build.gradle
dependencies {
    // Spring Boot Test
    testImplementation 'org.springframework.boot:spring-boot-starter-test'

    // Testcontainers
    testImplementation 'org.testcontainers:testcontainers:1.19.3'
    testImplementation 'org.testcontainers:postgresql:1.19.3'
    testImplementation 'org.testcontainers:junit-jupiter:1.19.3'

    // MockMvc for controller testing
    testImplementation 'org.springframework.boot:spring-boot-starter-web'

    // AssertJ for fluent assertions
    testImplementation 'org.assertj:assertj-core:3.24.2'
}
```

**Docker Required**: Testcontainers requires Docker Desktop to be running

## Architecture: Why PostgreSQL via Testcontainers?

```
❌ In-Memory Database (H2)
┌─────────────────────┐
│  Integration Test   │
│  @DataJpaTest       │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│   H2 In-Memory DB   │  ← Different from production
│  - No JSONB         │  ← Missing PostgreSQL features
│  - No Arrays        │  ← Syntax differences
│  - No Functions     │  ← False confidence
└─────────────────────┘

✅ Real Database (Testcontainers PostgreSQL)
┌─────────────────────┐
│  Integration Test   │
│  @SpringBootTest    │
│  @Transactional     │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ PostgreSQL 16       │  ← Same as production
│ (Docker Container)  │  ← JSONB, arrays, functions
│ Flyway Migrations   │  ← Schema parity
└─────────────────────┘
```

**Benefits**:
- ✅ Production parity (same PostgreSQL version)
- ✅ Real Flyway migrations tested
- ✅ JSONB, arrays, and PostgreSQL-specific features work
- ✅ Catches schema issues before deployment
- ✅ Singleton container reused across tests (fast)

## Implementation Steps

### Step 1: Create AbstractIntegrationTest Base Class

```java
package ch.batbern.{service};

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests using Testcontainers PostgreSQL.
 *
 * CRITICAL: All integration tests MUST extend this class to ensure:
 * - Production parity (real PostgreSQL, not H2)
 * - Flyway migrations executed
 * - Real database features (JSONB, arrays, functions)
 *
 * Performance: PostgreSQL container is singleton (reused across all tests)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    /**
     * Singleton PostgreSQL container - reused across ALL test classes for performance.
     * Started once, stopped at JVM shutdown.
     */
    @Container
    static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);  // CRITICAL: Reuse container across test classes

    /**
     * Dynamically configure Spring Boot to use Testcontainers PostgreSQL.
     * Runs before Spring context starts.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
```

### Step 2: Configure Test Application Properties

```properties
# src/test/resources/application-test.properties

# PostgreSQL via Testcontainers (configured dynamically via @DynamicPropertySource)
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect

# CRITICAL: Validate schema against Flyway migrations
# This ensures JPA entities match database schema
spring.jpa.hibernate.ddl-auto=validate

# Enable Flyway migrations for production parity
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true
spring.flyway.clean-disabled=false

# Test logging
logging.level.ch.batbern=DEBUG
logging.level.org.springframework.web=DEBUG
logging.level.org.testcontainers=INFO

# Disable caching in tests
spring.cache.type=none
```

### Step 3: Write Controller Integration Tests

```java
package ch.batbern.{service}.controller;

import ch.batbern.{service}.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for {Entity} REST API.
 *
 * @Transactional: Each test runs in a transaction, rolled back after test completes.
 * This ensures test isolation (no data pollution between tests).
 */
@Transactional
class {Entity}ControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void should_createEntity_when_validDataProvided() throws Exception {
        // Given
        String requestBody = """
            {
                "name": "Test Entity",
                "description": "Test Description",
                "status": "ACTIVE"
            }
            """;

        // When/Then
        mockMvc.perform(post("/api/v1/entities")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name", is("Test Entity")))
                .andExpect(jsonPath("$.status", is("ACTIVE")));
    }

    @Test
    void should_return400_when_invalidDataProvided() throws Exception {
        // Given
        String invalidRequest = """
            {
                "name": "",
                "description": null
            }
            """;

        // When/Then
        mockMvc.perform(post("/api/v1/entities")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("validation")));
    }

    @Test
    void should_getEntity_when_entityExists() throws Exception {
        // Given - create entity first
        String createRequest = """
            {
                "name": "Test Entity",
                "description": "Test Description"
            }
            """;

        String entityId = mockMvc.perform(post("/api/v1/entities")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequest))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Extract ID from response (adjust based on your response format)
        String id = extractIdFromResponse(entityId);

        // When/Then
        mockMvc.perform(get("/api/v1/entities/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(id)))
                .andExpect(jsonPath("$.name", is("Test Entity")));
    }

    @Test
    void should_return404_when_entityNotFound() throws Exception {
        // When/Then
        mockMvc.perform(get("/api/v1/entities/nonexistent-id"))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_updateEntity_when_validDataProvided() throws Exception {
        // Given - create entity first
        String createRequest = """
            {
                "name": "Original Name",
                "description": "Original Description"
            }
            """;

        String entityId = createEntity(createRequest);

        String updateRequest = """
            {
                "name": "Updated Name",
                "description": "Updated Description"
            }
            """;

        // When/Then
        mockMvc.perform(put("/api/v1/entities/{id}", entityId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Updated Name")));
    }

    @Test
    void should_deleteEntity_when_entityExists() throws Exception {
        // Given - create entity first
        String createRequest = """
            {
                "name": "To Delete",
                "description": "Will be deleted"
            }
            """;

        String entityId = createEntity(createRequest);

        // When/Then
        mockMvc.perform(delete("/api/v1/entities/{id}", entityId))
                .andExpect(status().isNoContent());

        // Verify entity is deleted
        mockMvc.perform(get("/api/v1/entities/{id}", entityId))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_listEntities_when_entitiesExist() throws Exception {
        // Given - create multiple entities
        createEntity("""{"name": "Entity 1", "description": "Desc 1"}""");
        createEntity("""{"name": "Entity 2", "description": "Desc 2"}""");

        // When/Then
        mockMvc.perform(get("/api/v1/entities")
                .param("page", "0")
                .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.total", greaterThanOrEqualTo(2)));
    }

    // Helper methods
    private String createEntity(String requestBody) throws Exception {
        String response = mockMvc.perform(post("/api/v1/entities")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return extractIdFromResponse(response);
    }

    private String extractIdFromResponse(String jsonResponse) {
        // Parse JSON and extract ID
        // Implementation depends on your JSON library
        return "extracted-id";
    }
}
```

### Step 4: Write Repository Integration Tests

```java
package ch.batbern.{service}.repository;

import ch.batbern.{service}.AbstractIntegrationTest;
import ch.batbern.{service}.domain.{Entity};
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {Entity}Repository with real PostgreSQL.
 * Tests PostgreSQL-specific features (JSONB, arrays, functions).
 */
@Transactional
class {Entity}RepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private {Entity}Repository repository;

    @Test
    void should_saveAndRetrieveEntity_when_validEntityProvided() {
        // Given
        {Entity} entity = new {Entity}();
        entity.setName("Test Entity");
        entity.setDescription("Test Description");

        // When
        {Entity} saved = repository.save(entity);

        // Then
        assertThat(saved.getId()).isNotNull();

        Optional<{Entity}> retrieved = repository.findById(saved.getId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getName()).isEqualTo("Test Entity");
    }

    @Test
    void should_findByName_when_entityExists() {
        // Given
        {Entity} entity = new {Entity}();
        entity.setName("Unique Name");
        repository.save(entity);

        // When
        Optional<{Entity}> found = repository.findByName("Unique Name");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Unique Name");
    }

    @Test
    void should_deleteEntity_when_entityExists() {
        // Given
        {Entity} entity = new {Entity}();
        entity.setName("To Delete");
        {Entity} saved = repository.save(entity);

        // When
        repository.deleteById(saved.getId());

        // Then
        Optional<{Entity}> deleted = repository.findById(saved.getId());
        assertThat(deleted).isEmpty();
    }

    @Test
    void should_handlePostgreSQLJsonb_when_jsonDataProvided() {
        // Test PostgreSQL JSONB column type
        // This test would FAIL with H2 (no JSONB support)

        {Entity} entity = new {Entity}();
        entity.setName("JSON Entity");
        entity.setMetadata(Map.of("key1", "value1", "key2", "value2"));  // JSONB column

        {Entity} saved = repository.save(entity);

        Optional<{Entity}> retrieved = repository.findById(saved.getId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getMetadata())
            .containsEntry("key1", "value1")
            .containsEntry("key2", "value2");
    }
}
```

## Testing

### Run Integration Tests

```bash
# Run all integration tests
./gradlew test

# Run specific integration test class
./gradlew test --tests "*{Entity}ControllerIntegrationTest"

# Run with detailed output
./gradlew test --info

# IMPORTANT: Ensure Docker Desktop is running before tests
```

### Continuous Integration

```yaml
# .github/workflows/ci.yml
name: CI

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'

      - name: Run integration tests
        run: ./gradlew test
        # Testcontainers uses Docker in Docker on GitHub Actions
```

## Common Pitfalls

### Pitfall 1: Using H2 Instead of PostgreSQL
**Problem**: Tests pass but production fails due to PostgreSQL-specific features
**Solution**: ALWAYS use Testcontainers PostgreSQL, never H2

```java
// ❌ WRONG - using H2
@DataJpaTest  // Uses H2 by default
class EntityRepositoryTest {
    // Tests will pass but hide PostgreSQL issues
}

// ✅ CORRECT - using Testcontainers PostgreSQL
@Transactional
class EntityRepositoryIntegrationTest extends AbstractIntegrationTest {
    // Tests run against real PostgreSQL
}
```

### Pitfall 2: Not Using @Transactional
**Problem**: Test data pollutes other tests, tests fail randomly
**Solution**: Always use `@Transactional` on integration test classes

```java
// ✅ CORRECT - each test runs in rolled-back transaction
@Transactional
class EntityControllerIntegrationTest extends AbstractIntegrationTest {
    // Test data is rolled back after each test
}
```

### Pitfall 3: Forgetting Docker is Required
**Problem**: Tests fail with "Cannot connect to Docker daemon"
**Solution**: Ensure Docker Desktop is running before tests

```bash
# Check Docker is running
docker ps

# Start Docker Desktop if needed
# Then run tests
./gradlew test
```

### Pitfall 4: Not Reusing PostgreSQL Container
**Problem**: Each test class starts new PostgreSQL container (slow)
**Solution**: Use `withReuse(true)` on singleton container

```java
// ✅ CORRECT - singleton container reused across test classes
@Container
static final PostgreSQLContainer<?> postgres =
    new PostgreSQLContainer<>("postgres:16-alpine")
        .withReuse(true);  // Reuse across test classes
```

### Pitfall 5: Using spring.jpa.hibernate.ddl-auto=create
**Problem**: Flyway migrations not tested, schema drift not detected
**Solution**: Use `ddl-auto=validate` to ensure Flyway migrations match entities

```properties
# ✅ CORRECT - validate schema matches migrations
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
```

## Story-Specific Adaptations

### Testing HTTP Client Integration (Story 2.7)
```java
@Transactional
class PartnerServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PartnerService partnerService;

    @MockBean  // Mock HTTP client for integration tests
    private CompanyServiceClient companyServiceClient;

    @Test
    void should_enrichPartnerWithCompanyData_when_includesCompany() {
        // Given
        when(companyServiceClient.getCompany("GoogleZH"))
            .thenReturn(CompanyResponse.builder().name("GoogleZH").build());

        // When
        PartnerResponse response = partnerService.getPartner("GoogleZH", Set.of("company"));

        // Then
        assertThat(response.getCompany()).isNotNull();
    }
}
```

### Testing Security Configuration
```java
@Transactional
class SecurityIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void should_allowAccess_when_userHasRole() throws Exception {
        mockMvc.perform(get("/api/v1/entities"))
            .andExpect(status().isOk());
    }

    @Test
    void should_return401_when_noAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/entities"))
            .andExpect(status().isUnauthorized());
    }
}
```

### Testing Event-Driven Task Creation with Idempotency (Story 5.5)
```java
@Transactional
class EventTaskIdempotencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private EventWorkflowStateMachine eventWorkflowStateMachine;

    @Autowired
    private EventTaskRepository eventTaskRepository;

    @Autowired
    private TaskTemplateRepository taskTemplateRepository;

    @Test
    void should_createTasksOnlyOnce_when_eventPublishedMultipleTimes() {
        // Given: Event with task templates configured
        Event event = createEventWithTaskTemplates();
        String eventId = event.getId().toString();

        // When: Event transitions to TOPIC_SELECTION (triggers task creation)
        eventWorkflowStateMachine.transitionToState(
            eventId,
            EventWorkflowState.TOPIC_SELECTION,
            "organizer"
        );

        // Then: 4 tasks created (venue, partner meeting, moderator, newsletter)
        List<EventTask> tasksAfterFirst = eventTaskRepository.findByEventId(event.getId());
        assertThat(tasksAfterFirst).hasSize(4);

        // When: Event replayed (simulating retry or duplicate event)
        eventWorkflowStateMachine.transitionToState(
            eventId,
            EventWorkflowState.TOPIC_SELECTION,
            "organizer"
        );

        // Then: NO duplicate tasks created (idempotency check prevents)
        List<EventTask> tasksAfterSecond = eventTaskRepository.findByEventId(event.getId());
        assertThat(tasksAfterSecond).hasSize(4); // Still 4, not 8

        // Verify exact same task IDs (not new records)
        assertThat(tasksAfterFirst)
            .extracting(EventTask::getId)
            .containsExactlyInAnyOrder(
                tasksAfterSecond.stream()
                    .map(EventTask::getId)
                    .toArray(UUID[]::new)
            );
    }

    @Test
    void should_autoCreateTasks_when_eventTransitionsToTopicSelection() {
        // Given: Event in CREATED state
        Event event = createEvent();

        // When: Event transitions to TOPIC_SELECTION
        eventWorkflowStateMachine.transitionToState(
            event.getId(),
            EventWorkflowState.TOPIC_SELECTION,
            "organizer"
        );

        // Then: Tasks created for all matching templates
        List<EventTask> tasks = eventTaskRepository.findByEventId(event.getId());
        assertThat(tasks).hasSize(4);
        assertThat(tasks).extracting("taskName").containsExactlyInAnyOrder(
            "Venue Booking",
            "Partner Meeting Coordination",
            "Moderator Assignment",
            "Newsletter: Topic Announcement"
        );

        // And: Due dates calculated correctly
        EventTask venueTask = tasks.stream()
            .filter(t -> t.getTaskName().equals("Venue Booking"))
            .findFirst().orElseThrow();
        assertThat(venueTask.getDueDate())
            .isEqualTo(event.getEventDate().minusDays(90));
    }
}
```

### Testing Transaction Rollback with Multi-Step Operations (Story 5.5)
```java
@Transactional
class ContentSubmissionTransactionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SpeakerContentSubmissionService contentService;

    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SessionUserRepository sessionUserRepository;

    @Test
    void should_rollbackAllChanges_when_exceptionThrown() {
        // Given: Speaker in accepted state
        SpeakerPool speaker = createAcceptedSpeaker();
        String initialStatus = speaker.getStatus();

        // When: Content submission fails validation
        assertThrows(ContentSubmissionException.class, () ->
            contentService.submitContent(
                eventId,
                speaker.getId(),
                invalidRequest() // Missing required field
            )
        );

        // Then: All database changes rolled back
        SpeakerPool unchanged = speakerPoolRepository.findById(speaker.getId())
            .orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(initialStatus);
        assertThat(unchanged.getSessionId()).isNull();

        // And: No orphaned session records
        assertThat(sessionRepository.count()).isZero();

        // And: No orphaned session_users records
        assertThat(sessionUserRepository.count()).isZero();
    }

    @Test
    void should_submitContentAndCreateSessionLink_when_validDataProvided() {
        // Given: Accepted speaker in pool
        SpeakerPool speaker = createAcceptedSpeaker();

        // When: Content submitted
        SubmitContentRequest request = SubmitContentRequest.builder()
            .username("john.doe")
            .presentationTitle("Test Presentation")
            .presentationAbstract("This is a test abstract with lessons learned.")
            .build();

        SpeakerContentDto result = contentService.submitContent(
            eventId, speaker.getId(), request
        );

        // Then: Session created
        assertThat(result.getSessionId()).isNotNull();
        Session session = sessionRepository.findById(result.getSessionId())
            .orElseThrow();
        assertThat(session.getTitle()).isEqualTo("Test Presentation");

        // And: session_users link created
        List<SessionUser> sessionUsers = sessionUserRepository
            .findBySessionId(session.getId());
        assertThat(sessionUsers).hasSize(1);
        assertThat(sessionUsers.get(0).getUsername()).isEqualTo("john.doe");

        // And: speaker_pool updated atomically
        SpeakerPool updated = speakerPoolRepository.findById(speaker.getId())
            .orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("content_submitted");
        assertThat(updated.getSessionId()).isEqualTo(session.getId());
    }

    @Test
    void should_updateToConfirmed_when_bothQualityReviewedAndSlotAssigned() {
        // Given: Speaker with content submitted
        SpeakerPool speaker = createSpeakerWithContent();

        // When: Content approved (quality_reviewed)
        reviewService.approveContent(speaker.getId(), "moderator");

        // And: Slot assigned (start_time set)
        Session session = sessionRepository.findById(speaker.getSessionId())
            .orElseThrow();
        session.setStartTime(LocalDateTime.now().plusDays(30));
        sessionRepository.save(session);

        // Trigger confirmation check
        workflowService.checkAndUpdateToConfirmed(speaker);

        // Then: Speaker status updated to confirmed
        SpeakerPool updated = speakerPoolRepository.findById(speaker.getId())
            .orElseThrow();
        assertThat(updated.getStatus()).isEqualTo("confirmed");

        // And: session_users.is_confirmed updated
        SessionUser sessionUser = sessionUserRepository
            .findBySessionId(session.getId()).get(0);
        assertThat(sessionUser.isConfirmed()).isTrue();
    }
}
```

## Related Templates

- `spring-boot-service-foundation.md` - Service structure and layers
- `jwt-propagation-pattern.md` - Testing HTTP clients with JWT
- `flyway-migration-pattern.md` - Database migrations tested here

## References

- **Testcontainers Docs**: https://www.testcontainers.org/
- **Spring Boot Testing**: https://docs.spring.io/spring-boot/reference/testing/index.html
- **Source**: `docs/architecture/coding-standards.md` (lines 210-282)
