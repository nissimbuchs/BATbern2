# Spring Boot Service Foundation Pattern

**Category**: Backend
**Used in Stories**: 2.2, 2.7, 2.8, 2.8.1, 2.8.2
**Last Updated**: 2025-01-20

---

## Overview

Standard DDD layered architecture for BATbern microservices. Use this pattern when creating a new Spring Boot service or adding new domain entities to existing services.

**Architecture**:
```
Controller → Service → Repository → Database
    ↓          ↓           ↓
   DTOs    Domain Logic   JPA Entities
```

---

## Prerequisites

- Java 21 LTS
- Spring Boot 3.x
- PostgreSQL 15+
- Flyway for migrations
- OpenAPI specification for the domain
- Shared-kernel dependency added to build.gradle

---

## Implementation Steps

### Step 1: Package Structure

Create standard package layout:

```
services/{domain-service}/
├── src/main/java/ch/batbern/{domain}/
│   ├── controller/              # REST API controllers
│   ├── service/                 # Business logic layer
│   ├── repository/              # Data access layer
│   ├── domain/                  # JPA entities
│   ├── dto/                     # Data Transfer Objects (generated)
│   ├── client/                  # HTTP clients for other services
│   ├── exception/               # Domain exceptions
│   ├── config/                  # Configuration classes
│   └── events/                  # Domain events
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/
└── src/test/java/ch/batbern/{domain}/
    └── AbstractIntegrationTest.java
```

### Step 2: JPA Entity with Meaningful ID

Create domain entity following ADR-003 (meaningful IDs for cross-service references):

```java
package ch.batbern.partners.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "partners")
public class Partner {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;  // UUID PK for THIS service's entity

    // ✅ ADR-003: Store meaningful ID (companyName), NOT UUID FK
    @Column(name = "company_name", nullable = false, unique = true, length = 12)
    private String companyName;

    @Enumerated(EnumType.STRING)
    @Column(name = "partnership_level", nullable = false, length = 50)
    private PartnershipTier partnershipLevel;

    @Column(name = "partnership_start_date", nullable = false)
    private LocalDate partnershipStartDate;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Transient method - business logic in entity
    @Transient
    public boolean isCurrentlyActive() {
        if (!isActive) return false;
        LocalDate now = LocalDate.now();
        return !now.isBefore(partnershipStartDate) &&
               (partnershipEndDate == null || !now.isAfter(partnershipEndDate));
    }

    // Getters and setters
}
```

**Enum Types**:
```java
public enum PartnershipTier {
    BRONZE, SILVER, GOLD, PLATINUM, STRATEGIC
}
```

### Step 3: Repository Interface

Create repository extending JpaRepository:

```java
package ch.batbern.partners.repository;

import ch.batbern.partners.domain.Partner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.*;

public interface PartnerRepository extends JpaRepository<Partner, UUID> {

    // Query methods (Spring Data generates SQL)
    Optional<Partner> findByCompanyName(String companyName);

    List<Partner> findByPartnershipLevel(PartnershipTier level);

    List<Partner> findByIsActiveTrue();

    boolean existsByCompanyName(String companyName);

    // Custom JPQL query
    @Query("SELECT p FROM Partner p " +
           "WHERE p.partnershipStartDate <= :date " +
           "AND (p.partnershipEndDate IS NULL OR p.partnershipEndDate >= :date)")
    List<Partner> findActivePartnersOnDate(@Param("date") LocalDate date);
}
```

### Step 4: Service Layer with HTTP Enrichment

Create service with business logic, transaction management, and domain events:

```java
package ch.batbern.partners.service;

import ch.batbern.partners.domain.Partner;
import ch.batbern.partners.repository.PartnerRepository;
import ch.batbern.partners.client.CompanyServiceClient;
import ch.batbern.shared.events.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

@Service
@Transactional
@RequiredArgsConstructor
public class PartnerService {

    private final PartnerRepository partnerRepository;
    private final CompanyServiceClient companyServiceClient;  // HTTP client
    private final DomainEventPublisher eventPublisher;        // Shared-kernel

    public PartnerResponse getPartner(String companyName, Set<String> includes) {
        Partner partner = partnerRepository.findByCompanyName(companyName)
            .orElseThrow(() -> new PartnerNotFoundException("Partner not found: " + companyName));

        return mapToResponse(partner, includes);
    }

    public PartnerResponse createPartner(CreatePartnerRequest request) {
        // Validate company exists via HTTP call
        CompanyResponse company = companyServiceClient.getCompany(request.getCompanyName());

        // Create domain entity
        Partner partner = new Partner();
        partner.setCompanyName(request.getCompanyName());
        partner.setPartnershipLevel(request.getPartnershipLevel());
        partner.setPartnershipStartDate(request.getStartDate());

        Partner saved = partnerRepository.save(partner);

        // Publish domain event
        eventPublisher.publishEvent(new PartnerCreatedEvent(
            saved.getId(),
            saved.getCompanyName()
        ));

        return mapToResponse(saved, null);
    }

    private PartnerResponse mapToResponse(Partner partner, Set<String> includes) {
        PartnerResponse response = new PartnerResponse();
        response.setCompanyName(partner.getCompanyName());
        // ... basic mappings

        // HTTP enrichment (ADR-004)
        if (includes != null && includes.contains("company")) {
            CompanyResponse company = companyServiceClient.getCompany(partner.getCompanyName());
            response.setCompany(company);
        }

        return response;
    }
}
```

### Step 5: Controller Layer

Create controller implementing generated OpenAPI interface:

```java
package ch.batbern.partners.controller;

import ch.batbern.partners.api.generated.PartnersApi;  // Generated from OpenAPI
import ch.batbern.partners.dto.generated.*;
import ch.batbern.partners.service.PartnerService;
import ch.batbern.shared.api.*;                        // Shared-kernel utilities
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import java.util.*;

@RestController
@RequiredArgsConstructor
public class PartnerController implements PartnersApi {  // Implement generated interface

    private final PartnerService partnerService;
    private final FilterParser filterParser;    // From shared-kernel
    private final SortParser sortParser;        // From shared-kernel
    private final IncludeParser includeParser;  // From shared-kernel

    @Override
    public ResponseEntity<List<PartnerResponse>> listPartners(
            Optional<String> filter,
            Optional<String> sort,
            Optional<Integer> page,
            Optional<Integer> limit,
            Optional<String> include) {

        // Parse query parameters using shared-kernel utilities
        FilterCriteria filterCriteria = filterParser.parse(filter.orElse(null));
        SortCriteria sortCriteria = sortParser.parse(sort.orElse(null));
        Set<String> includes = includeParser.parse(include.orElse(null));

        List<PartnerResponse> partners = partnerService.listPartners(
            filterCriteria, sortCriteria, page.orElse(1), limit.orElse(20), includes
        );

        return ResponseEntity.ok(partners);
    }

    @Override
    public ResponseEntity<PartnerResponse> getPartner(
            String companyName,
            Optional<String> include) {

        Set<String> includes = includeParser.parse(include.orElse(null));
        PartnerResponse partner = partnerService.getPartner(companyName, includes);

        return ResponseEntity.ok(partner);
    }
}
```

**Key Rules**:
- ✅ Implement generated API interfaces
- ✅ Delegate ALL business logic to service layer
- ❌ NO repository injection in controllers
- ❌ NO business logic in controllers

### Step 6: Exception Handling

Create global exception handler:

```java
package ch.batbern.partners.exception;

import ch.batbern.shared.dto.ErrorResponse;
import ch.batbern.shared.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
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

    // More exception handlers (ValidationException, BusinessException, etc.)
}
```

**Custom Domain Exceptions**:
```java
// Extend shared-kernel exceptions
public class PartnerNotFoundException extends NotFoundException {
    public PartnerNotFoundException(String message) {
        super(message);
    }
}
```

### Step 7: Domain Events

Create domain event:

```java
package ch.batbern.partners.events;

import ch.batbern.shared.events.DomainEvent;
import lombok.Getter;
import java.util.UUID;

@Getter
public class PartnerCreatedEvent extends DomainEvent<UUID> {
    private final String companyName;

    public PartnerCreatedEvent(UUID partnerId, String companyName) {
        super(partnerId);  // Aggregate root ID
        this.companyName = companyName;
    }
}
```

---

## Configuration Files

### application.yml

```yaml
spring:
  application:
    name: partner-coordination-service

  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/batbern}
    username: ${DATABASE_USERNAME:batbern}
    password: ${DATABASE_PASSWORD:changeme}
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: validate  # Flyway handles schema
    show-sql: false

  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

# HTTP Service URLs (ADR-003)
services:
  company-service:
    url: ${COMPANY_SERVICE_URL:http://localhost:8081}
  user-service:
    url: ${USER_SERVICE_URL:http://localhost:8082}

# Caffeine cache
spring:
  cache:
    type: caffeine
    cache-names:
      - companies
      - users
```

### application-test.properties

```properties
# Testcontainers PostgreSQL
spring.datasource.url=jdbc:tc:postgresql:15:///testdb
spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver

spring.flyway.enabled=true
spring.jpa.hibernate.ddl-auto=validate
```

---

## Testing

### AbstractIntegrationTest Base Class

```java
package ch.batbern.partners;

import ch.batbern.shared.test.AbstractIntegrationTest as SharedTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public abstract class AbstractIntegrationTest extends SharedTest {

    // Mock HTTP clients
    @MockBean
    protected CompanyServiceClient companyServiceClient;

    @MockBean
    protected UserServiceClient userServiceClient;

    // Base class provides PostgreSQL Testcontainer singleton
}
```

### Controller Integration Test

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PartnerControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void should_createPartner_when_validDataProvided() {
        // Given
        when(companyServiceClient.getCompany("GoogleZH"))
            .thenReturn(CompanyResponse.builder().name("GoogleZH").build());

        CreatePartnerRequest request = CreatePartnerRequest.builder()
            .companyName("GoogleZH")
            .partnershipLevel(PartnershipTier.GOLD)
            .startDate(LocalDate.now())
            .build();

        // When
        ResponseEntity<PartnerResponse> response = restTemplate.postForEntity(
            "/api/v1/partners", request, PartnerResponse.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getCompanyName()).isEqualTo("GoogleZH");
    }
}
```

---

## Common Pitfalls

- **Pitfall**: Injecting repositories directly in controllers
  **Solution**: Always inject service layer, never repositories in controllers

- **Pitfall**: Using H2 or in-memory DB for integration tests
  **Solution**: Use Testcontainers PostgreSQL for production parity

- **Pitfall**: Using UUID foreign keys for cross-service references
  **Solution**: Follow ADR-003 - use meaningful IDs (companyName, username)

- **Pitfall**: Forgetting to publish domain events
  **Solution**: Always publish events after state changes using DomainEventPublisher

- **Pitfall**: Not enriching responses with HTTP calls
  **Solution**: Use `?include=company` pattern for on-demand enrichment (ADR-004)

---

## Story-Specific Adaptations

### Different Entity Types

- **Events**: Add workflow state, publishing status
- **Partners**: Add partnership tier, engagement scores
- **Companies**: Add logo URL, company size, industry

### Different HTTP Clients

- Each service may call different microservices
- Mock all HTTP clients in AbstractIntegrationTest

### Different Query Patterns

- Add custom `@Query` methods for complex queries
- Use Spring Data specifications for dynamic queries

---

## Related Patterns

- See also: `jwt-propagation-pattern.md` - For HTTP client setup
- See also: `flyway-migration-pattern.md` - For database migrations
- See also: `integration-test-pattern.md` - For Testcontainers setup
- See also: `openapi-code-generation-pattern.md` - For API contract generation

---

## Detailed Reference

For comprehensive details, see: [docs/guides/service-foundation-pattern.md](../../guides/service-foundation-pattern.md)
