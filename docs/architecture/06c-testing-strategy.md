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
│ Unit Tests (JUnit 5 + Mockito)                             │
│ Business logic in isolation (services, domain logic)        │
└─────────────────────────────────────────────────────────────┘
```

## AbstractIntegrationTest Base Class

**All integration tests must extend this base class:**

```java
package ch.batbern.shared.test;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Abstract base class for integration tests using Testcontainers PostgreSQL.
 *
 * This ensures production parity by running tests against a real PostgreSQL database
 * instead of H2, which allows us to:
 * - Test PostgreSQL-specific features (JSONB, functions, etc.)
 * - Verify Flyway migrations work correctly
 * - Catch database-specific issues early
 *
 * The container is shared across all test classes for performance (singleton pattern).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    /**
     * Shared PostgreSQL container for all tests.
     * Using singleton pattern with reuse=true for optimal performance.
     */
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    /**
     * Dynamically configure Spring datasource properties from the Testcontainers instance.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
```

## Test Configuration (application-test.properties)

**Required configuration for test profile:**

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

```java
package ch.batbern.events.controller;

import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Event Management API.
 *
 * Tests run against real PostgreSQL via Testcontainers.
 * Uses Flyway migrations for schema setup.
 */
@Transactional
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
                        "organizerId": "550e8400-e29b-41d4-a716-446655440000",
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
- Singleton pattern with `withReuse(true)` optimizes performance
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

### Run Unit Tests Only
```bash
./gradlew test --tests "*Test" --exclude-tests "*IntegrationTest"
```

### Generate Coverage Report
```bash
./gradlew test jacocoTestReport
open build/reports/jacoco/test/html/index.html
```

## Continuous Integration

Tests run automatically on:
- Every pull request
- Every commit to `main` branch
- Nightly builds (full test suite + coverage report)

### GitHub Actions Configuration

```yaml
name: Backend Tests

on:
  pull_request:
  push:
    branches: [main, develop]

jobs:
  test:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Cache Gradle packages
        uses: actions/cache@v3
        with:
          path: ~/.gradle/caches
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}

      - name: Run tests with Testcontainers
        run: ./gradlew test jacocoTestReport

      - name: Upload coverage to Codecov
        uses: codecov/codecov-action@v3
        with:
          files: ./build/reports/jacoco/test/jacocoTestReport.xml

      - name: Publish test results
        uses: EnricoMi/publish-unit-test-result-action@v2
        if: always()
        with:
          files: build/test-results/**/*.xml
```

## Related Documentation

- [Backend Architecture Overview](./06-backend-architecture.md)
- [Database Schema and Migrations](./04-database-architecture.md)
