# Partner Coordination Service

**Domain**: Partner relationship management and coordination for BATbern event platform
**Status**: Implementation in progress
**Version**: 1.0.0

---

## Overview

The Partner Coordination Service manages partnerships with companies sponsoring BATbern events. Partners receive benefits based on their partnership tier and can influence event content through topic voting and suggestions.

**Key Responsibilities**:
- Partner relationship management (bronze → strategic tiers)
- Multiple contact person management per partner
- Topic voting with weighted influence
- Topic suggestion submission and review
- Partnership benefits tracking
- Meeting coordination (future)

---

## Domain Model

### Core Entities

**Partner** (Company-Centric)
- Stores `companyName` (meaningful ID per ADR-003), NOT `companyId` UUID
- Partnership tier: bronze, silver, gold, platinum, strategic
- Partnership period with start/end dates
- Active/inactive status with business logic

**PartnerContact**
- Stores `username` (meaningful ID per ADR-003), NOT `userId` UUID
- Contact role: primary, billing, technical, marketing
- Links partner to user profiles
- Multiple contacts per partner allowed

**TopicVote**
- Links to topic in Event Management Service (no FK, just topic ID)
- Vote value (1-5 stars) with weighted influence based on partnership tier
- One vote per partner per topic

**TopicSuggestion**
- Partner-submitted topic proposals
- Status tracking: submitted → under_review → accepted/rejected
- Rationale for decisions

### Cross-Service Relationships

**NO database foreign keys across services** (ADR-003). All cross-service data access via HTTP:

| Entity | References | Method |
|--------|-----------|--------|
| Partner | Company (Company Service) | `CompanyServiceClient.getCompany(companyName)` |
| PartnerContact | User (User Service) | `UserServiceClient.getUser(username)` |
| TopicVote | Topic (Event Service) | `EventServiceClient.getTopic(topicId)` |

📖 See [Microservices HTTP Clients Guide](../../docs/guides/microservices-http-clients.md)

---

## Architecture Compliance

✅ **ADR-003: Meaningful Identifiers in Public APIs**
- Partner stores `companyName` (string), NOT `companyId` (UUID)
- PartnerContact stores `username` (string), NOT `userId` (UUID)
- NO cross-service database foreign keys
- HTTP enrichment for related data

✅ **ADR-004: Factor User Fields from Domain Entities**
- PartnerContact does NOT duplicate email, name, profilePictureUrl from User
- User data enriched via HTTP call when needed (`?include=user`)
- Single source of truth maintained

✅ **ADR-006: OpenAPI Contract-First Code Generation**
- API interfaces and DTOs generated from `docs/api/partners-api.openapi.yml`
- Controllers implement generated interfaces
- Frontend TypeScript types generated from same spec

📖 See [Service Foundation Pattern Guide](../../docs/guides/service-foundation-pattern.md)

---

## Project Structure

```
services/partner-coordination-service/
├── src/main/java/ch/batbern/partners/
│   ├── controller/              # REST controllers (implement generated APIs)
│   ├── service/                 # Business logic
│   ├── repository/              # JPA repositories
│   ├── domain/                  # JPA entities
│   ├── client/                  # HTTP clients (Company, User, Event services)
│   ├── exception/               # Custom exceptions
│   ├── config/                  # Spring configuration
│   └── events/                  # Domain events
├── src/main/resources/
│   ├── db/migration/            # Flyway migrations
│   │   ├── V1__Initial_baseline.sql
│   │   └── V2__create_partner_coordination_schema.sql
│   ├── application.yml          # Production config
│   └── application-test.properties  # Test config
├── src/test/java/               # Tests (extends AbstractIntegrationTest)
├── build.gradle                 # Dependencies + OpenAPI generation
├── Dockerfile                   # Multi-stage Docker build
├── OPENAPI-CODEGEN.md          # OpenAPI code generation quick reference
└── README.md                    # This file
```

---

## Quick Start

### Prerequisites

- Java 21
- Docker Desktop (for integration tests)
- PostgreSQL 15 (via Testcontainers in tests, AWS RDS in production)

### Build and Test

```bash
# From project root - build this service
./gradlew :services:partner-coordination-service:build

# Run tests (uses Testcontainers PostgreSQL)
./gradlew :services:partner-coordination-service:test

# Generate OpenAPI code only
./gradlew :services:partner-coordination-service:openApiGenerate

# Clean and rebuild
./gradlew :services:partner-coordination-service:clean build
```

### Run Locally

```bash
# Via Docker Compose (includes all dependencies)
cd /Users/nissim/dev/bat/BATbern-develop
make docker-up

# Or native development (more resource efficient)
make dev-native-up

# Service available at:
# http://localhost:8080/api/v1/partners
```

### Database Migrations

Migrations run automatically on service startup (Flyway enabled):

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
```

📖 See [Flyway Migration Guide](../../docs/guides/flyway-migration-guide.md)

---

## API Endpoints

### Partners

- `GET /api/v1/partners` - List partners (with filtering)
- `GET /api/v1/partners/{companyName}` - Get partner by company name
- `PUT /api/v1/partners/{companyName}` - Update partnership details

### Partner Contacts

- `GET /api/v1/partners/{companyName}/contacts` - List contacts
- `POST /api/v1/partners/{companyName}/contacts` - Add contact
- `DELETE /api/v1/partners/{companyName}/contacts/{username}` - Remove contact

### Topic Voting

- `GET /api/v1/partners/{companyName}/votes` - Get partner's votes
- `POST /api/v1/partners/{companyName}/votes` - Vote on topic
- `GET /api/v1/partners/{companyName}/suggestions` - Get suggestions
- `POST /api/v1/partners/{companyName}/suggestions` - Suggest topic

**Full API specification**: `docs/api/partners-api.openapi.yml`

---

## Testing

### Integration Tests

All integration tests extend `AbstractIntegrationTest` which provides:
- Singleton PostgreSQL Testcontainer (production parity)
- Flyway migrations run automatically
- `@Transactional` rollback after each test
- Mock HTTP clients for external services

```java
@SpringBootTest
@Transactional
class PartnerControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CompanyServiceClient companyServiceClient;

    @Test
    void should_getPartner_when_validCompanyName() throws Exception {
        // Given
        when(companyServiceClient.getCompany("GoogleZH"))
            .thenReturn(mockCompanyResponse());

        // When/Then
        mockMvc.perform(get("/api/v1/partners/GoogleZH?include=company")
                .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("GoogleZH"))
                .andExpect(jsonPath("$.company").exists());
    }
}
```

### Schema Validation Tests

Verify Flyway migrations match JPA entities:

```java
@Test
void should_havePartnersTable_when_migrationsRun() {
    String sql = "SELECT table_name FROM information_schema.tables " +
                 "WHERE table_schema = 'public' AND table_name = 'partners'";
    List<String> tables = jdbcTemplate.queryForList(sql, String.class);
    assertThat(tables).contains("partners");
}
```

### Running Tests

```bash
# All tests
./gradlew :services:partner-coordination-service:test

# Single test class
./gradlew :services:partner-coordination-service:test \
  --tests PartnerControllerIntegrationTest

# With coverage
./gradlew :services:partner-coordination-service:test jacocoTestReport
# Report: build/reports/jacoco/test/html/index.html
```

---

## Configuration

### Environment Variables

```bash
# Database (RDS in production, Testcontainers in tests)
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/batbern
SPRING_DATASOURCE_USERNAME=batbern
SPRING_DATASOURCE_PASSWORD=<secret>

# External services (Service Connect in production)
COMPANY_SERVICE_URL=http://company-user-management-service:8080
USER_SERVICE_URL=http://company-user-management-service:8080
EVENT_SERVICE_URL=http://event-management-service:8080

# AWS (EventBridge for domain events)
AWS_REGION=eu-central-1
EVENT_BUS_NAME=batbern-platform-events
```

### Production Deployment

Deployed to AWS ECS via CDK:

```bash
# Deploy to staging
cd infrastructure
npm run deploy:staging

# Deploy to production
npm run deploy:prod
```

---

## Related Documentation

### Implementation Guides

- 📖 [OpenAPI Code Generation Guide](../../docs/guides/openapi-code-generation.md) - Contract-first approach
- 📖 [Service Foundation Pattern](../../docs/guides/service-foundation-pattern.md) - Service structure
- 📖 [Microservices HTTP Clients](../../docs/guides/microservices-http-clients.md) - Cross-service communication
- 📖 [Flyway Migration Guide](../../docs/guides/flyway-migration-guide.md) - Database migrations

### Architecture Decisions

- 📖 [ADR-003: Meaningful Identifiers](../../docs/architecture/ADR-003-meaningful-identifiers-public-apis.md)
- 📖 [ADR-004: Factor User Fields](../../docs/architecture/ADR-004-factor-user-fields-from-domain-entities.md)
- 📖 [ADR-006: OpenAPI Code Generation](../../docs/architecture/ADR-006-openapi-contract-first-code-generation.md)

### API Specifications

- 📖 [Partners API OpenAPI Spec](../../docs/api/partners-api.openapi.yml)
- 📖 [OpenAPI Codegen Quick Reference](./OPENAPI-CODEGEN.md)

### Development Stories

- 📖 [Story 2.7: Partner Coordination Service Foundation](../../docs/stories/2.7.partner-coordination-service-foundation.md)

---

## Contributing

1. Follow TDD practices (Red-Green-Refactor)
2. Write integration tests using `AbstractIntegrationTest`
3. Maintain 85%+ code coverage
4. Update OpenAPI spec when changing APIs
5. Regenerate types after API changes
6. Follow conventional commits format

```bash
# Example workflow
./gradlew :services:partner-coordination-service:test  # Should fail (RED)
# Write implementation
./gradlew :services:partner-coordination-service:test  # Should pass (GREEN)
# Refactor while keeping tests green
```

---

**Last Updated**: 2025-01-08
**Maintained By**: Development Team
**Status**: Implementation in progress
