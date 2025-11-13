# Service Foundation Pattern Guide

**Pattern**: DDD Layered Architecture for BATbern Microservices
**Last Updated**: 2025-01-08

---

## Table of Contents

- [Overview](#overview)
- [Package Structure](#package-structure)
- [Layer Responsibilities](#layer-responsibilities)
- [JPA Entity Patterns](#jpa-entity-patterns)
- [Repository Pattern](#repository-pattern)
- [Service Layer](#service-layer)
- [Controller Layer](#controller-layer)
- [Exception Handling](#exception-handling)
- [Domain Event Publishing](#domain-event-publishing)
- [Configuration Files](#configuration-files)
- [Testing Setup](#testing-setup)
- [Related Documentation](#related-documentation)

---

## Overview

All BATbern microservices follow a consistent DDD layered architecture:

```
Controller → Service → Repository → Database
    ↓          ↓           ↓
   DTOs    Domain Logic   JPA Entities
```

**Key Principles**:
- **Domain-Driven Design**: Rich domain entities with business logic
- **Clear Layer Separation**: Controllers delegate to services, services use repositories
- **No Repository in Controllers**: Controllers must NOT inject repositories directly
- **OpenAPI Contract-First**: Controllers implement generated interfaces
- **Shared-Kernel Integration**: Reuse common infrastructure

---

## Package Structure

### Standard Package Layout

```
services/{service-name}/
├── src/main/java/ch/batbern/{domain}/
│   ├── controller/              # REST API controllers
│   │   └── {Entity}Controller.java (implements generated API interface)
│   │
│   ├── service/                 # Business logic layer
│   │   ├── {Entity}Service.java
│   │   └── ...
│   │
│   ├── repository/              # Data access layer
│   │   ├── {Entity}Repository.java (extends JpaRepository)
│   │   └── ...
│   │
│   ├── domain/                  # JPA entities (aggregate roots, value objects)
│   │   ├── {Entity}.java
│   │   ├── {ValueObject}.java
│   │   └── ...
│   │
│   ├── dto/                     # Data Transfer Objects
│   │   ├── {Entity}Response.java (generated from OpenAPI)
│   │   ├── Create{Entity}Request.java (generated)
│   │   ├── Update{Entity}Request.java (generated)
│   │   └── {ExternalService}Response.java (from other services)
│   │
│   ├── client/                  # HTTP clients for other microservices
│   │   ├── {Service}Client.java
│   │   └── ...
│   │
│   ├── exception/               # Domain-specific exceptions
│   │   ├── {Entity}NotFoundException.java
│   │   ├── {Entity}AlreadyExistsException.java
│   │   └── GlobalExceptionHandler.java
│   │
│   ├── config/                  # Configuration classes
│   │   ├── CacheConfig.java
│   │   ├── HttpClientConfig.java
│   │   └── SecurityConfig.java
│   │
│   └── events/                  # Domain events
│       ├── {Entity}CreatedEvent.java
│       ├── {Entity}UpdatedEvent.java
│       └── ...
│
├── src/main/resources/
│   ├── application.yml          # Main configuration
│   ├── application-test.properties  # Test configuration
│   └── db/migration/            # Flyway migrations
│       ├── V1__Initial_baseline.sql
│       └── V2__create_{domain}_schema.sql
│
└── src/test/java/ch/batbern/{domain}/
    ├── controller/              # Controller integration tests
    ├── service/                 # Service unit tests
    ├── repository/              # Repository integration tests
    ├── client/                  # HTTP client tests
    └── AbstractIntegrationTest.java  # Test base class
```

---

## Layer Responsibilities

### Controller Layer

**Responsibility**: HTTP API endpoints, request/response transformation

```java
@RestController
@RequiredArgsConstructor
public class PartnerController implements PartnersApi {  // Generated interface

    private final PartnerService partnerService;         // Service layer
    private final IncludeParser includeParser;           // From shared-kernel

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

**Rules**:
- ✅ Implement generated API interfaces
- ✅ Parse query parameters (filter, sort, include) using shared-kernel utilities
- ✅ Delegate all business logic to service layer
- ❌ NO repository injection in controllers
- ❌ NO business logic in controllers

### Service Layer

**Responsibility**: Business logic, transaction management, domain event publishing

```java
@Service
@Transactional
@RequiredArgsConstructor
public class PartnerService {

    private final PartnerRepository partnerRepository;       // Repository
    private final CompanyServiceClient companyServiceClient; // HTTP client
    private final DomainEventPublisher eventPublisher;       // Shared-kernel

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
        // Map entity to DTO, handle includes (HTTP enrichment)
        PartnerResponse response = new PartnerResponse();
        response.setCompanyName(partner.getCompanyName());
        // ... more mappings

        if (includes != null && includes.contains("company")) {
            CompanyResponse company = companyServiceClient.getCompany(partner.getCompanyName());
            response.setCompany(company);
        }

        return response;
    }
}
```

**Rules**:
- ✅ Annotate with `@Service` and `@Transactional`
- ✅ Inject repositories and HTTP clients
- ✅ Publish domain events using shared-kernel DomainEventPublisher
- ✅ Throw domain exceptions (NotFoundException, ValidationException)
- ❌ NO HTTP client code in repositories

### Repository Layer

**Responsibility**: Data access, JPA queries

```java
public interface PartnerRepository extends JpaRepository<Partner, UUID> {

    Optional<Partner> findByCompanyName(String companyName);

    List<Partner> findByPartnershipLevel(PartnershipTier level);

    List<Partner> findByIsActive(Boolean isActive);

    @Query("SELECT p FROM Partner p WHERE p.partnershipStartDate <= :date AND (p.partnershipEndDate IS NULL OR p.partnershipEndDate >= :date)")
    List<Partner> findActivePartnersOnDate(@Param("date") LocalDate date);
}
```

**Rules**:
- ✅ Extend `JpaRepository<Entity, ID>`
- ✅ Use query methods following Spring Data naming conventions
- ✅ Use `@Query` for complex queries
- ❌ NO business logic in repositories
- ❌ NO HTTP clients in repositories

---

## JPA Entity Patterns

### Entity with Meaningful ID Cross-Reference

**Pattern**: Store meaningful IDs (companyName, username) for cross-service references

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
    private UUID id;  // ✅ UUID PK for THIS service's entity

    // ✅ ADR-003: Store meaningful ID (companyName), NOT UUID FK
    @Column(name = "company_name", nullable = false, unique = true, length = 12)
    private String companyName;

    @Enumerated(EnumType.STRING)
    @Column(name = "partnership_level", nullable = false, length = 50)
    private PartnershipTier partnershipLevel;

    @Column(name = "partnership_start_date", nullable = false)
    private LocalDate partnershipStartDate;

    @Column(name = "partnership_end_date")
    private LocalDate partnershipEndDate;

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

    // Transient method - business logic
    @Transient
    public boolean isCurrentlyActive() {
        if (!isActive) return false;
        LocalDate now = LocalDate.now();
        boolean afterStart = !now.isBefore(partnershipStartDate);
        boolean beforeEnd = partnershipEndDate == null || !now.isAfter(partnershipEndDate);
        return afterStart && beforeEnd;
    }

    // Getters and setters
    public UUID getId() { return id; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    // ... more getters/setters
}
```

### Entity with Within-Service FK

**Pattern**: UUID FKs are OK within the same service

```java
package ch.batbern.partners.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "partner_contacts",
    uniqueConstraints = @UniqueConstraint(columnNames = {"partner_id", "username"})
)
public class PartnerContact {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    // ✅ Within-service FK OK
    @Column(name = "partner_id", nullable = false)
    private UUID partnerId;

    // ✅ ADR-003: Store meaningful ID (username), NOT UUID FK to users table
    @Column(name = "username", nullable = false, length = 100)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "contact_role", nullable = false, length = 50)
    private ContactRole contactRole;

    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Getters and setters
}
```

### Enum Types

```java
public enum PartnershipTier {
    BRONZE,
    SILVER,
    GOLD,
    PLATINUM,
    STRATEGIC
}

public enum ContactRole {
    PRIMARY,
    BILLING,
    TECHNICAL,
    MARKETING
}
```

---

## Repository Pattern

### Standard Repository Interface

```java
package ch.batbern.partners.repository;

import ch.batbern.partners.domain.Partner;
import ch.batbern.partners.domain.PartnershipTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

---

## Service Layer

### Service with HTTP Enrichment

```java
@Service
@Transactional
@RequiredArgsConstructor
public class PartnerContactService {

    private final PartnerContactRepository contactRepository;
    private final UserServiceClient userServiceClient;  // HTTP client
    private final DomainEventPublisher eventPublisher;

    public PartnerContactResponse addContact(
            UUID partnerId,
            String username,
            ContactRole role,
            Boolean isPrimary) {

        // Validate user exists via HTTP call
        UserResponse user = userServiceClient.getUser(username);

        // Create contact entity
        PartnerContact contact = new PartnerContact();
        contact.setPartnerId(partnerId);
        contact.setUsername(username);
        contact.setContactRole(role);
        contact.setIsPrimary(isPrimary);

        PartnerContact saved = contactRepository.save(contact);

        // Publish event
        eventPublisher.publishEvent(new PartnerContactAddedEvent(
            saved.getId(),
            partnerId,
            username
        ));

        // Enrich with User data via HTTP
        return enrichContactWithUserData(saved);
    }

    private PartnerContactResponse enrichContactWithUserData(PartnerContact contact) {
        UserResponse user = userServiceClient.getUser(contact.getUsername());

        return PartnerContactResponse.builder()
            .username(user.getUsername())
            .email(user.getEmail())             // From User Service
            .firstName(user.getFirstName())     // From User Service
            .lastName(user.getLastName())       // From User Service
            .profilePictureUrl(user.getProfilePictureUrl())  // From User Service
            .contactRole(contact.getContactRole())  // From partner_contacts table
            .isPrimary(contact.getIsPrimary())      // From partner_contacts table
            .build();
    }
}
```

---

## Controller Layer

### Controller with Generated Interface

```java
package ch.batbern.partners.controller;

import ch.batbern.partners.api.generated.PartnersApi;  // Generated
import ch.batbern.partners.dto.generated.*;             // Generated
import ch.batbern.partners.service.PartnerService;
import ch.batbern.shared.api.*;                         // Shared-kernel
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@RestController
@RequiredArgsConstructor
public class PartnerController implements PartnersApi {  // Implement generated interface

    private final PartnerService partnerService;
    private final FilterParser filterParser;    // From shared-kernel
    private final SortParser sortParser;        // From shared-kernel
    private final IncludeParser includeParser;  // From shared-kernel

    @Override
    public ResponseEntity<List<PartnerResponse>> listPartners(
            Optional<String> partnershipLevel,
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
            partnershipLevel.orElse(null),
            filterCriteria,
            sortCriteria,
            page.orElse(1),
            limit.orElse(20),
            includes
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

---

## Exception Handling

### Global Exception Handler

```java
package ch.batbern.partners.exception;

import ch.batbern.shared.dto.ErrorResponse;  // From shared-kernel
import ch.batbern.shared.exception.*;        // From shared-kernel
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

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

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            ValidationException ex,
            HttpServletRequest request) {

        ErrorResponse error = ErrorResponse.builder()
            .timestamp(Instant.now())
            .path(request.getRequestURI())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("VALIDATION_ERROR")
            .message(ex.getMessage())
            .correlationId(RequestContext.getCurrentRequestId())
            .severity("WARNING")
            .details(ex.getDetails())
            .build();

        return ResponseEntity.badRequest().body(error);
    }

    // More exception handlers...
}
```

### Custom Domain Exceptions

```java
// Extend shared-kernel exceptions
public class PartnerNotFoundException extends NotFoundException {
    public PartnerNotFoundException(String message) {
        super(message);
    }
}

public class PartnerAlreadyExistsException extends BusinessException {
    public PartnerAlreadyExistsException(String message) {
        super(message);
    }
}
```

---

## Domain Event Publishing

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

**Publishing Events**:

```java
@Service
@Transactional
@RequiredArgsConstructor
public class PartnerService {

    private final DomainEventPublisher eventPublisher;  // From shared-kernel

    public PartnerResponse createPartner(CreatePartnerRequest request) {
        Partner saved = partnerRepository.save(partner);

        // Publish domain event
        eventPublisher.publishEvent(new PartnerCreatedEvent(
            saved.getId(),
            saved.getCompanyName()
        ));

        return mapToResponse(saved, null);
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
      ddl-auto: validate  # Flyway handles schema changes
    show-sql: false
    properties:
      hibernate:
        format_sql: true

  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

# HTTP Service URLs (ADR-003 microservices pattern)
services:
  company-service:
    url: ${COMPANY_SERVICE_URL:http://localhost:8081}
  user-service:
    url: ${USER_SERVICE_URL:http://localhost:8082}

# Cache configuration
spring:
  cache:
    type: caffeine
    cache-names:
      - companies
      - users
      - partners

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  metrics:
    tags:
      application: ${spring.application.name}
```

### application-test.properties

```properties
# Test database (Testcontainers provides PostgreSQL)
spring.datasource.url=jdbc:tc:postgresql:15:///testdb
spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver

# Flyway runs migrations in tests
spring.flyway.enabled=true
spring.flyway.clean-disabled=false

# JPA settings
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=true
```

---

## Testing Setup

### AbstractIntegrationTest Base Class

```java
package ch.batbern.partners;

import ch.batbern.shared.test.AbstractIntegrationTest as SharedAbstractIntegrationTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public abstract class AbstractIntegrationTest extends SharedAbstractIntegrationTest {

    // Mock HTTP clients for integration tests
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
            "/api/v1/partners",
            request,
            PartnerResponse.class
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getCompanyName()).isEqualTo("GoogleZH");
    }
}
```

---

## Related Documentation

### Architecture

- **[ADR-003: Meaningful Identifiers in Public APIs](../architecture/ADR-003-meaningful-identifiers-public-apis.md)**
- **[ADR-006: OpenAPI Contract-First Code Generation](../architecture/ADR-006-openapi-contract-first-code-generation.md)**
- **[Coding Standards](../architecture/coding-standards.md)**
- **[Backend Architecture](../architecture/06-backend-architecture.md)**

### Related Guides

- **[OpenAPI Code Generation Guide](./openapi-code-generation.md)**
- **[Microservices HTTP Clients Guide](./microservices-http-clients.md)**
- **[Flyway Migration Guide](./flyway-migration-guide.md)**

---

**Last Updated**: 2025-01-08
**Maintained By**: Development Team
