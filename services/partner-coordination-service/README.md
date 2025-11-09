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
- `PATCH /api/v1/partners/{companyName}` - Update partnership details
- `POST /api/v1/partners` - Create new partnership (organizer-only)
- `DELETE /api/v1/partners/{companyName}` - Deactivate partnership (soft delete)

### Partner Contacts

- `GET /api/v1/partners/{companyName}/contacts` - List contacts (HTTP enriched with User data)
- `POST /api/v1/partners/{companyName}/contacts` - Add contact
- `DELETE /api/v1/partners/{companyName}/contacts/{username}` - Remove contact

### Topic Voting

- `GET /api/v1/partners/{companyName}/votes` - Get partner's votes
- `POST /api/v1/partners/{companyName}/votes` - Vote on topic (weighted by tier)
- `GET /api/v1/partners/{companyName}/suggestions` - Get suggestions
- `POST /api/v1/partners/{companyName}/suggestions` - Suggest topic

**Full API specification**: `docs/api/partners-api.openapi.yml`

---

## API Usage Examples

### ADR-003: Meaningful Identifiers

All Partner APIs use **meaningful identifiers** (companyName, username) instead of UUIDs in URLs:

```bash
# ✅ Correct - using meaningful ID
GET /api/v1/partners/GoogleZH

# ❌ Wrong - using UUID
GET /api/v1/partners/550e8400-e29b-41d4-a716-446655440000
```

### HTTP Enrichment Pattern (ADR-004)

Partner responses can be enriched with related data via HTTP calls using the `?include` parameter:

#### Example 1: Get Partner with Company Data

```bash
# Request
GET /api/v1/partners/GoogleZH?include=company
Authorization: Bearer {jwt-token}

# Response (enriched via HTTP call to Company Service)
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "companyName": "GoogleZH",
  "partnershipLevel": "gold",
  "partnershipStartDate": "2024-01-01",
  "partnershipEndDate": null,
  "isActive": true,
  "company": {
    "companyName": "GoogleZH",
    "displayName": "Google Zurich",
    "logoUrl": "https://cdn.batbern.ch/logos/GoogleZH.png"
  }
}
```

**Implementation** (HTTP enrichment in service layer):

```java
@Service
@RequiredArgsConstructor
public class PartnerService {
    private final PartnerRepository partnerRepository;
    private final CompanyServiceClient companyServiceClient;  // HTTP client

    public PartnerResponse getPartner(String companyName, Set<String> includes) {
        Partner partner = partnerRepository.findByCompanyName(companyName)
            .orElseThrow(() -> new NotFoundException("Partner not found"));

        PartnerResponse response = mapToResponse(partner);

        // HTTP enrichment per ADR-004
        if (includes.contains("company")) {
            CompanyResponse company = companyServiceClient.getCompany(companyName);
            response.setCompany(company);  // Enrich response
        }

        return response;
    }
}
```

#### Example 2: List Partner Contacts with User Data

```bash
# Request
GET /api/v1/partners/GoogleZH/contacts
Authorization: Bearer {jwt-token}

# Response (User data enriched via HTTP calls to User Service)
[
  {
    "id": "123e4567-e89b-12d3-a456-426614174000",
    "username": "john.doe",
    "contactRole": "primary",
    "isPrimary": true,
    "email": "john.doe@google.com",        // From User Service
    "firstName": "John",                    // From User Service
    "lastName": "Doe",                      // From User Service
    "profilePictureUrl": "https://..."      // From User Service
  }
]
```

**Implementation** (HTTP enrichment for each contact):

```java
@Service
@RequiredArgsConstructor
public class PartnerContactService {
    private final PartnerContactRepository contactRepository;
    private final UserServiceClient userServiceClient;  // HTTP client

    public List<PartnerContactResponse> getPartnerContacts(String companyName) {
        List<PartnerContact> contacts = contactRepository.findByPartner_CompanyName(companyName);

        return contacts.stream()
            .map(contact -> {
                PartnerContactResponse response = mapToResponse(contact);

                // HTTP enrichment: fetch User data per ADR-004
                try {
                    UserResponse user = userServiceClient.getUser(contact.getUsername());
                    response.setEmail(user.getEmail());
                    response.setFirstName(user.getFirstName());
                    response.setLastName(user.getLastName());
                    response.setProfilePictureUrl(user.getProfilePictureUrl());
                } catch (UserNotFoundException e) {
                    log.warn("User not found: {}", contact.getUsername());
                }

                return response;
            })
            .toList();
    }
}
```

#### Example 3: Add Partner Contact (Validates User via HTTP)

```bash
# Request
POST /api/v1/partners/GoogleZH/contacts
Authorization: Bearer {jwt-token}
Content-Type: application/json

{
  "username": "jane.smith",
  "contactRole": "billing",
  "isPrimary": false
}

# Response (User data enriched)
{
  "id": "456e7890-e89b-12d3-a456-426614174111",
  "username": "jane.smith",
  "contactRole": "billing",
  "isPrimary": false,
  "email": "jane.smith@google.com",
  "firstName": "Jane",
  "lastName": "Smith",
  "profilePictureUrl": "https://..."
}
```

**Implementation** (HTTP validation before adding):

```java
public PartnerContactResponse addPartnerContact(String companyName, AddPartnerContactRequest request) {
    // 1. Validate user exists via HTTP call to User Service
    UserResponse user = userServiceClient.getUser(request.getUsername());
    // Throws UserNotFoundException if user doesn't exist (HTTP 404 → API returns 404)

    // 2. Create contact entity (stores username, NOT userId UUID)
    Partner partner = partnerRepository.findByCompanyName(companyName)
        .orElseThrow(() -> new NotFoundException("Partner not found"));

    PartnerContact contact = PartnerContact.builder()
        .partner(partner)
        .username(request.getUsername())  // ✅ Meaningful ID (ADR-003)
        .contactRole(request.getContactRole())
        .isPrimary(request.getIsPrimary())
        .build();

    contactRepository.save(contact);

    // 3. Return enriched response
    PartnerContactResponse response = mapToResponse(contact);
    response.setEmail(user.getEmail());
    response.setFirstName(user.getFirstName());
    response.setLastName(user.getLastName());
    response.setProfilePictureUrl(user.getProfilePictureUrl());

    return response;
}
```

#### Example 4: Cast Vote with Weighted Influence

```bash
# Request
POST /api/v1/partners/GoogleZH/votes
Authorization: Bearer {jwt-token}
Content-Type: application/json

{
  "topicId": "789e0123-e89b-12d3-a456-426614174222",
  "voteValue": 5
}

# Response (vote weight calculated based on partnership tier)
{
  "topicId": "789e0123-e89b-12d3-a456-426614174222",
  "voteValue": 5,
  "voteWeight": 3,       // GOLD tier = 3x weight
  "votedAt": "2024-12-15T10:30:00Z"
}
```

**Implementation** (vote weight calculation):

```java
public TopicVote castVote(String companyName, UUID topicId, int voteValue) {
    Partner partner = partnerRepository.findByCompanyName(companyName)
        .orElseThrow(() -> new NotFoundException("Partner not found"));

    // Calculate vote weight based on partnership tier
    int voteWeight = calculateVoteWeight(partner.getPartnershipLevel());
    // BRONZE=1, SILVER=2, GOLD=3, PLATINUM=4, STRATEGIC=5

    TopicVote vote = TopicVote.builder()
        .partner(partner)
        .topicId(topicId)
        .voteValue(voteValue)
        .voteWeight(voteWeight)
        .votedAt(Instant.now())
        .build();

    return voteRepository.save(vote);
}

private int calculateVoteWeight(PartnershipLevel level) {
    return switch (level) {
        case BRONZE -> 1;
        case SILVER -> 2;
        case GOLD -> 3;
        case PLATINUM -> 4;
        case STRATEGIC -> 5;
    };
}
```

### JWT Token Propagation

All HTTP calls to external services propagate the JWT token from the request:

```java
@Service
@RequiredArgsConstructor
public class CompanyServiceClient {
    private final RestTemplate restTemplate;

    @Cacheable(value = "companies", key = "#companyName")
    public CompanyResponse getCompany(String companyName) {
        String url = companyServiceUrl + "/api/v1/companies/" + companyName;

        // Propagate JWT token from SecurityContext
        HttpHeaders headers = createHeadersWithJwtToken();
        HttpEntity<?> entity = new HttpEntity<>(headers);

        return restTemplate.exchange(url, HttpMethod.GET, entity, CompanyResponse.class)
            .getBody();
    }

    private HttpHeaders createHeadersWithJwtToken() {
        HttpHeaders headers = new HttpHeaders();

        // Extract JWT from SecurityContext
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getCredentials() instanceof String token) {
            headers.set("Authorization", "Bearer " + token);
        }

        return headers;
    }
}
```

### Caching HTTP Responses

HTTP responses are cached to reduce latency and external service load:

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new CaffeineCacheManager("partners", "companies", "users");
    }

    @Bean
    public Caffeine<Object, Object> caffeineConfig() {
        return Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)  // 10-minute TTL
            .maximumSize(1000)
            .recordStats();
    }
}
```

📖 See [Microservices HTTP Clients Guide](../../docs/guides/microservices-http-clients.md) for complete implementation details

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
