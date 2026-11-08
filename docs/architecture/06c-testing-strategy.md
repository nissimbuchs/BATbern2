# Testing Strategy and Production Parity

This document outlines the comprehensive testing strategy for the BATbern Event Management Platform, with a strong emphasis on production parity through Testcontainers PostgreSQL.

## Critical Testing Principle: Production Parity

**MANDATORY: All integration tests MUST use PostgreSQL via Testcontainers.**

The BATbern backend uses PostgreSQL-specific features (JSONB columns, PostgreSQL functions, etc.) that are not supported by H2 or other in-memory databases. Using H2 for tests creates **false confidence** and hides production issues.

## Testing Layers

```
┌─────────────────────────────────────────────────────────────┐
│ E2E Tests (Playwright)                                      │
│ Full user journeys across frontend and backend              │
└─────────────────────────────────────────────────────────────┘
                            │
┌─────────────────────────────────────────────────────────────┐
│ Integration Tests (Spring Boot + Testcontainers)           │
│ REST API endpoints + Database + PostgreSQL container        │
│ MANDATORY: Extend AbstractIntegrationTest                   │
└─────────────────────────────────────────────────────────────┘
                            │
┌─────────────────────────────────────────────────────────────┐
│ Unit Tests (JUnit Jupiter 6 + Mockito)                      │
│ Business logic in isolation (services, domain logic)        │
└─────────────────────────────────────────────────────────────┘
```

## AbstractIntegrationTest Base Class

**All integration tests must extend this base class.** It lives in the shared-kernel
**test fixtures** (`shared-kernel/src/testFixtures/java/ch/batbern/shared/test/AbstractIntegrationTest.java`);
services consume it via `testImplementation testFixtures(project(':shared-kernel'))`.
Current content:

```java
package ch.batbern.shared.test;

import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Abstract base class for integration tests using Testcontainers PostgreSQL.
 *
 * This ensures production parity by running tests against a real PostgreSQL database
 * instead of H2, which allows us to:
 * - Test PostgreSQL-specific features (JSONB, functions, etc.)
 * - Verify Flyway migrations work correctly
 * - Catch database-specific issues early
 *
 * The container is shared across all test classes for performance (manual singleton pattern).
 * We do NOT use @Testcontainers/@Container to avoid premature container shutdown.
 *
 * Usage (no class-level @Transactional on a write IT: the rollback skips the flush, so DB
 * constraints and triggers never run; see docs/architecture/coding-standards.md, "Test fidelity
 * rules"):
 * ```java
 * @Import(TestSecurityConfig.class) // If needed
 * public class MyServiceIntegrationTest extends AbstractIntegrationTest {
 *     // Test implementation
 * }
 * ```
 *
 * Architecture Reference: docs/architecture/06-backend-architecture.md (Production Parity Testing)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// Epic 13 (SB4): re-apply springSecurity() to the auto-configured MockMvc — SB4 dropped the
// auto-config that used to do this, which otherwise drops the @WithMockUser SecurityContext and
// makes every @PreAuthorize request return 403. Inherited by all integration tests.
@Import(MockMvcSecuritySetup.class)
public abstract class AbstractIntegrationTest {

    /**
     * Shared PostgreSQL container for all tests using manual singleton pattern.
     * The container is started once and reused across all test classes.
     * It will be automatically cleaned up by Testcontainers Ryuk when the JVM exits.
     *
     * PostgreSQL 16 Alpine is used to match production environment.
     */
    private static final PostgreSQLContainer<?> POSTGRES;

    static {
        @SuppressWarnings("resource") // Container managed by Testcontainers Ryuk lifecycle
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("testdb")
                .withUsername("test")
                .withPassword("test");
        POSTGRES = container;
        POSTGRES.start();
    }

    /**
     * Dynamically configure Spring datasource properties from the Testcontainers instance.
     * This overrides any static configuration in application-test.yml.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
```

Notes on Spring Boot 4 (Epic 13):

- The MockMvc and test-database annotations moved packages:
  `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` and
  `org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase`
  (the Spring Boot 3 `org.springframework.boot.test.autoconfigure.web.servlet` package is gone).
- `MockMvcSecuritySetup` (same fixtures package) re-applies `springSecurity()` to MockMvc,
  otherwise `@WithMockUser` is lost and every `@PreAuthorize` request returns 403.
- The container is a manual singleton started in a static block; no `@Testcontainers` /
  `@Container`, no `withReuse(true)`. Ryuk removes it when the JVM exits.
- JUnit: Jupiter **6** (6.0.3 from the Spring Boot 4.0.9 BOM; `api-gateway` pins 6.1.3).

## Test Configuration (application-test.properties)

**Excerpt from `services/event-management-service/src/test/resources/application-test.properties`** (other services use `application-test.properties` or `application-test.yml` with the same intent). EMS adds `classpath:db/testmigration` to `spring.flyway.locations`.

```properties
# PostgreSQL via Testcontainers (configured dynamically in AbstractIntegrationTest)
# Database connection properties are injected by @DynamicPropertySource
spring.datasource.driver-class-name=org.postgresql.Driver

# JPA Configuration - PostgreSQL dialect
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
# Let Flyway manage schema - do not use Hibernate auto-DDL
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.properties.hibernate.jdbc.lob.non_contextual_creation=true

# Enable Flyway for tests - use real migrations for production parity
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=0
spring.flyway.clean-disabled=false

# Disable caching for tests
spring.cache.type=none

# Logging
logging.level.ch.batbern=DEBUG
logging.level.org.springframework.web=DEBUG
logging.level.org.hibernate.SQL=DEBUG
```

## Integration Test Example

Illustrative (request fields simplified); see the `*IntegrationTest` classes in each service for real ones.

```java
package ch.batbern.events.controller;

import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Event Management API.
 *
 * Tests run against real PostgreSQL via Testcontainers.
 * Uses Flyway migrations for schema setup.
 * No class-level @Transactional: the rollback skips the flush, so DB constraints and triggers
 * never run (#1104). Clean up in @AfterEach instead (coding-standards.md, "Test fidelity rules").
 */
class EventControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void should_createEvent_when_validDataProvided() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "title": "BATbern 2024",
                        "eventNumber": 123,
                        "date": "2024-12-15T18:00:00Z",
                        "registrationDeadline": "2024-12-10T23:59:59Z",
                        "venueName": "Kornhausforum",
                        "venueAddress": "Kornhausplatz 18, 3011 Bern",
                        "venueCapacity": 200,
                        "organizerUsername": "john.doe",
                        "metadata": {"theme": "cloud-native"}
                    }
                    """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("BATbern 2024"))
                .andExpect(jsonPath("$.metadata.theme").value("cloud-native"));
    }

    @Test
    void should_returnBadRequest_when_jsonbFieldInvalid() throws Exception {
        // This test would PASS with H2 but FAIL in production
        // Testcontainers PostgreSQL catches this issue early
        mockMvc.perform(post("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "title": "BATbern 2024",
                        "eventNumber": 123,
                        "metadata": "invalid - not JSON"
                    }
                    """))
                .andExpect(status().isBadRequest());
    }
}
```

## Unit Test Example

```java
package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.dto.CreateEventRequest;
import ch.batbern.events.repository.EventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EventService business logic.
 *
 * Fast, isolated tests with mocked dependencies.
 */
@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private EventService eventService;

    @Test
    void should_createEvent_when_validRequestProvided() {
        // Given
        CreateEventRequest request = CreateEventRequest.builder()
            .title("BATbern 2024")
            .eventNumber(123)
            .build();

        Event savedEvent = Event.builder()
            .id(UUID.randomUUID())
            .title("BATbern 2024")
            .build();

        when(eventRepository.save(any(Event.class))).thenReturn(savedEvent);

        // When
        Event result = eventService.createEvent(request);

        // Then
        assertThat(result.getTitle()).isEqualTo("BATbern 2024");
        verify(eventRepository).save(any(Event.class));
    }

    @Test
    void should_throwException_when_duplicateEventNumber() {
        // Given
        CreateEventRequest request = CreateEventRequest.builder()
            .eventNumber(123)
            .build();

        when(eventRepository.existsByEventNumber(123)).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> eventService.createEvent(request))
            .isInstanceOf(DuplicateEventException.class)
            .hasMessageContaining("Event number already exists");
    }
}
```

## Test Data Builders

```java
package ch.batbern.events.test;

import ch.batbern.events.domain.Event;

import java.time.Instant;
import java.util.UUID;

/**
 * Test data builder for Event entities.
 * Provides fluent API for creating test data with sensible defaults.
 */
public class EventTestDataBuilder {

    private UUID id = UUID.randomUUID();
    private String title = "Test Event";
    private Integer eventNumber = 100;
    private Instant date = Instant.now().plusSeconds(86400 * 30);
    private Instant registrationDeadline = Instant.now().plusSeconds(86400 * 20);
    private String venueName = "Test Venue";
    private String venueAddress = "Test Address";
    private Integer venueCapacity = 100;
    private UUID organizerId = UUID.randomUUID();

    public EventTestDataBuilder withTitle(String title) {
        this.title = title;
        return this;
    }

    public EventTestDataBuilder withEventNumber(Integer eventNumber) {
        this.eventNumber = eventNumber;
        return this;
    }

    public Event build() {
        return Event.builder()
            .id(id)
            .title(title)
            .eventNumber(eventNumber)
            .date(date)
            .registrationDeadline(registrationDeadline)
            .venueName(venueName)
            .venueAddress(venueAddress)
            .venueCapacity(venueCapacity)
            .organizerId(organizerId)
            .status("DRAFT")
            .build();
    }
}
```

## Why Testcontainers PostgreSQL?

### Problems with H2 for Integration Tests

1. **JSONB Type** - H2 doesn't support PostgreSQL's JSONB column type
   - Tests pass with H2 but fail in production
   - False confidence in data persistence

2. **PostgreSQL Functions** - H2 doesn't support PostgreSQL-specific functions
   - `gen_random_uuid()`, `NOW()`, array operations
   - Migration compatibility issues

3. **Constraint Behavior** - Subtle differences in constraint enforcement
   - UNIQUE constraints on nullable columns
   - Foreign key cascade behavior

4. **SQL Dialect Differences** - Different SQL syntax support
   - Common table expressions (CTEs)
   - Window functions
   - Index types

### Benefits of Testcontainers PostgreSQL

1. **Production Parity** - Tests run against the exact database used in production
2. **Early Detection** - Catches PostgreSQL-specific issues during development
3. **Migration Testing** - Validates Flyway migrations work correctly
4. **Confidence** - What passes in tests will work in production

### Performance Considerations

- Container starts once per test suite (~5-10 seconds overhead)
- Manual singleton: one container per test JVM
- Subsequent tests reuse the same container
- Still faster than manual PostgreSQL setup

## Test Coverage Requirements

- **Unit Tests**: 90% coverage for business logic
- **Integration Tests**: 80% coverage for API endpoints
- **E2E Tests**: Critical user journeys only
- **Overall**: Minimum 85% line coverage

## Running Tests

### Run All Tests
```bash
./gradlew test
```

### Run Integration Tests Only
```bash
./gradlew test --tests "*IntegrationTest"
```

### Run One Service or One Class
```bash
./gradlew :services:event-management-service:test
./gradlew :api-gateway:test --tests CompanyControllerIntegrationTest
```
To skip the Testcontainers `*IntegrationTest` classes locally (CI never does), pass
`-PskipIntegration`, for example `./gradlew :services:event-management-service:test -PskipIntegration`.

### Generate Coverage Report
```bash
./gradlew test jacocoTestReport
open build/reports/jacoco/test/html/index.html
```

## Continuous Integration

The pipeline is `.github/workflows/build.yml` (Java 21). It runs on a push to `develop` or
`main`, on every pull request targeting `develop` (which also deploys to production, see
CLAUDE.md), and on manual dispatch. Read the workflow itself for the jobs; in short:
`build-shared-kernel` and `build-services` run the Gradle tests including Testcontainers,
`test-frontend` runs Vitest in three shards when `web-frontend/` changed, `build-infrastructure`
runs the CDK Jest tests under `infrastructure/test/unit` only, and `deploy-to-staging` calls `deploy-staging.yml`, which runs the
post-deploy layers below. `nightly-e2e.yml` runs the full Playwright `@gate` suite against
production every night, plus `infrastructure/test/integration` and `infrastructure/test/e2e`.

## Test Layers Beyond Unit and Integration (4-layer E2E framework)

| Layer | Where | Runs |
|---|---|---|
| 1. Shell checks | `scripts/ci/smoke-tests.sh`, `cors-validation-tests.sh`, `header-propagation-tests.sh`, `access-control-check.py` (every operation in `docs/api/*.openapi.yml` not on the public allowlist `access-control-public.txt` must answer an anonymous call with 401/403) | After each deploy, in `deploy-staging.yml` |
| 2. Bruno API contract tests | `bruno-tests/` (one folder per API), runner `scripts/ci/run-bruno-tests.sh` | `bruno-tests` job after each deploy; failure rolls back |
| 3. Playwright UI E2E | `web-frontend/e2e/` (projects `chromium` = organizer, `speaker`, `partner`), runner `scripts/ci/run-playwright-tests.sh` | `@smoke` after each deploy (after Bruno); full `@gate` nightly |
| 4. Infrastructure tests | `infrastructure/test/e2e/` (Jest against AWS; `monitoring/` holds `alert-scenarios.spec.ts` and `incident-response.spec.ts`); CDK unit tests in `infrastructure/test/unit` | `test/e2e` and `test/integration` nightly in `nightly-e2e.yml`; `test/unit` in `build.yml` |

Bruno `.bru` files accept prose only inside `docs { }`; a top-level `#` comment makes Bruno skip
the whole file silently (see CLAUDE.md). `scripts/ci/test-bruno-collection-guard.sh` (in the `verify-hooks` job of `build.yml`) fails a
PR when a `bruno-tests/` folder is neither run nor explicitly excluded by `run-bruno-tests.sh`.

### Test data markers and email send guards (ADR-015)

Production is the only shared environment, so no test may mail a real person. See
[ADR-015](./ADR-015-test-data-markers-and-email-send-guards.md). In short:

- Test data carries canonical markers: `BATPW-E2E` (Playwright titles), `BRUNO-TEST-` (Bruno
  event codes), `BRUNOTESTCO` (company names), `bruno.test` (usernames). Synthetic recipients use
  `@e2e.batbern.invalid`.
- `EmailService` (shared-kernel) refuses reserved recipient domains (`example.com`, `*.test`,
  `*.invalid`, `*.localhost`, `*.local`) and, on the real SES path, any subject or body that
  contains a marker. `NewsletterEmailService` refuses a marked event before the bulk loop.
- A spec that drives a real send is tagged `@sends-real-email`, never `@smoke` or `@gate`, and is
  restricted to `TEST_ENV=development`.

### Authorization guard test

Every service with controllers has an `EndpointAuthorizationGuardTest` (api-gateway, CUMS, EMS,
PCS), built on `EndpointAuthorizationGuard` in the shared-kernel test fixtures. It fails the build
for any POST/PUT/PATCH/DELETE handler without `@PreAuthorize` on the method or class, and checks
that `@EnableMethodSecurity` is active. Paths under `/internal/`, `/dev/` and `/error` are
excluded.

### Caching: local dev is not a cache test

event-management-service `CacheConfig` registers a `NoOpCacheManager` under the `local` profile
and the Caffeine cache manager under every other profile (`@Profile("!local")`), including the
`test` profile of the integration tests. A cache bug therefore cannot be reproduced on local dev;
it needs an integration test. An integration test annotated `@Transactional` rolls back, so a
test for stale-cache behaviour has to commit its writes.

## Related Documentation

- [Backend Architecture Overview](./06-backend-architecture.md)
- [Data Architecture, Database Schema](./03-data-architecture.md#database-schema)
