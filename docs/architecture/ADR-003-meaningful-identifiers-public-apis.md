# ADR-003: Meaningful Identifiers in Public APIs

**Status**: Accepted
**Date**: 2025-11-02
**Decision Makers**: Development Team
**Related Stories**:
- Story 1.16.1 (Meaningful IDs for Public-Facing URLs)
- Story 1.16.2 (Eliminate UUIDs from API)

## Context

The initial BATbern platform design used UUIDs as primary identifiers exposed in public APIs and URLs. While UUIDs provide excellent technical properties (uniqueness, immutability, non-sequential), they create several problems for user experience and developer experience:

### Problems with UUID-Based APIs

1. **Poor User Experience**:
   - URLs like `/events/f47ac10b-58cc-4372-a567-0e02b2c3d479` are not memorable or shareable
   - Users cannot type or verbally communicate URLs
   - No semantic meaning - impossible to distinguish `BATbern #56` from its UUID

2. **Developer Experience Issues**:
   - API responses filled with cryptic UUID strings
   - Difficult to debug and trace entities across systems
   - API documentation examples using UUIDs are confusing
   - Manual testing requires copy-pasting UUIDs constantly

3. **SEO and Marketing Impact**:
   - Search engines prefer semantic URLs
   - Marketing materials cannot use clean URLs
   - Event codes like "BATbern142" are brandable identifiers

4. **Business Requirements**:
   - Event organizers refer to events by number (e.g., "BATbern #142")
   - Companies have established short names (e.g., "GoogleZH", "UBS")
   - Users expect profile URLs like `/users/john.doe`, not UUID paths

### Explored Alternatives

1. **Keep UUIDs in APIs, Use Slugs Only in Frontend Routes**
   - **Rejected**: Requires frontend to maintain UUID/slug mappings
   - Creates two-tier identifier system (public vs internal)
   - Still exposes UUIDs in API responses

2. **Use Numeric Sequential IDs**
   - **Rejected for PKs**: Exposes business metrics (event count, user count)
   - Creates security risks (enumeration attacks)
   - Does not solve human-readability for companies/users

3. **Hybrid Approach: Database UUIDs + API Slugs**
   - **Selected**: Provides both technical benefits and UX benefits
   - Aligns with industry best practices (GitHub, Stripe, etc.)

## Decision

We have decided to implement a **dual-identifier strategy** where:

### Database Layer (Internal - UUIDs for Own Entities Only)

**Within-Service References (Same Microservice)**:
- **UUID primary keys** for all entities owned by this service
- **UUID foreign keys** ONLY for relationships within the same service's database
- **Performance characteristics** of UUID PKs preserved:
  - B-tree index efficiency
  - Immutable identifiers (safe for caching)
  - Distributed ID generation (no coordination needed)

**Cross-Service References (Different Microservices)**:
- **NEVER use UUID foreign keys** to entities in other services
- **ALWAYS store meaningful IDs** (companyName, username, eventCode) for cross-service references
- **NO database foreign key constraints** across service boundaries
- **Microservice isolation** - each service owns its own database schema

### API Layer (Public - Meaningful Identifiers)

- **Meaningful IDs exposed** in all public APIs and URLs:
  - Events: `eventCode` (e.g., "BATbern56")
  - Companies: `name` (e.g., "GoogleZH")
  - Users: `username` (e.g., "john.doe")
  - Sessions: `slug` (e.g., "blockchain-security-101")

- **Meaningful IDs are unique alternate keys**:
  - Database unique constraints ensure uniqueness
  - B-tree indexes for lookup performance
  - Meaningful IDs are immutable after creation

- **DTOs use only meaningful IDs**:
  ```json
  {
    "id": "BATbern56",
    "title": "Modern Architecture Symposium",
    "organizerId": "john.doe",
    "venueId": "UniBern"
  }
  ```

### Identifier Format Standards

#### Event Codes
- **Format**: `BATbern{eventNumber}`
- **Example**: `BATbern142`
- **Generation**: Auto-generated from sequential event number
- **Pattern**: `^BATbern[0-9]+$`
- **Rationale**: Matches existing business naming convention

#### Company Names
- **Format**: Alphanumeric, 1-12 characters
- **Example**: `GoogleZH`, `UBS`, `Swisscom`
- **Generation**: User-provided during company creation
- **Pattern**: `^[a-zA-Z0-9]{1,12}$`
- **Case**: Case-insensitive lookups (`GoogleZH` = `googlezh`)
- **Rationale**: Short, memorable, brandable identifiers

#### Usernames
- **Format**: `{firstname}.{lastname}[.{n}]`
- **Example**: `john.doe`, `peter.mueller.2`
- **Generation**: Auto-generated from user's name
- **Pattern**: `^[a-z]+\.[a-z]+(\.[0-9]+)?$`
- **German Characters**: `ä→ae`, `ö→oe`, `ü→ue`, `ß→ss`
- **Collision Handling**: Append `.2`, `.3`, etc. for duplicates
- **Rationale**: Professional, predictable, standard format

#### Session Slugs
- **Format**: Slugified session title
- **Example**: `blockchain-security-101`
- **Generation**: Auto-generated from session title
- **Scope**: Unique within event (same slug allowed in different events)
- **Pattern**: Lowercase, hyphens, no special characters
- **Rationale**: SEO-friendly, human-readable session URLs

### Implementation Architecture

```
┌─────────────────────────────────────────────────────────┐
│                      Frontend Layer                      │
│  URLs: /events/BATbern56, /users/john.doe              │
│  API Calls: GET /api/v1/events/BATbern56               │
└────────────────────┬────────────────────────────────────┘
                     │ Meaningful IDs only
                     │
┌────────────────────▼────────────────────────────────────┐
│                    API Gateway Layer                     │
│  Routing: /api/v1/events/{eventCode}                   │
│  JWT Validation, Rate Limiting                          │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│               Service Layer (Controllers)                │
│  @GetMapping("/events/{eventCode}")                     │
│  → eventService.findByEventCode(eventCode)              │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│             Repository Layer (JPA/JPQL)                  │
│  findByEventCode(String eventCode) → Event entity       │
│  Query: SELECT e FROM Event e WHERE e.eventCode = :code│
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│                  Database Layer (PostgreSQL)             │
│  events table:                                          │
│    - id UUID PRIMARY KEY (internal)                     │
│    - event_code VARCHAR(20) UNIQUE (public API)         │
│    - organizer_id UUID → users.id (internal FK)         │
│    - title, description, etc.                           │
│                                                          │
│  Index: CREATE UNIQUE INDEX ON events(event_code)       │
└─────────────────────────────────────────────────────────┘
```

### DTO Projection Strategy

To avoid N+1 queries when resolving foreign keys to meaningful IDs, we use **JPQL constructor projections**:

```java
@Query("""
    SELECT new ch.batbern.events.dto.EventResponse(
        e.eventCode,
        e.title,
        u.username
    )
    FROM Event e
    LEFT JOIN User u ON e.organizerId = u.id
    WHERE e.eventCode = :eventCode
    """)
Optional<EventResponse> findEventResponseByCode(@Param("eventCode") String eventCode);
```

**Benefits**:
- Single database query (no N+1 problem)
- Automatic FK resolution (UUID → username)
- Type-safe DTO construction
- Performant (index-backed joins)

### Microservice Isolation Rules

**CRITICAL: Services MUST store meaningful IDs for cross-service references, NOT UUIDs.**

#### Rule 1: Own Entities Use UUID PKs

```java
// ✅ CORRECT: Event entity in event-management-service
@Entity
@Table(name = "events")
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;  // ✅ UUID PK for entity owned by this service

    @Column(name = "event_code", nullable = false, unique = true)
    private String eventCode;  // ✅ Meaningful ID for public APIs
}
```

#### Rule 2: Cross-Service References Use Meaningful IDs (NOT UUIDs)

```java
// ✅ CORRECT: Partner entity in partner-coordination-service
@Entity
@Table(name = "partners")
public class Partner {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;  // ✅ UUID PK for Partner (owned by this service)

    // Cross-service reference to Company (different service)
    @Column(name = "company_name", nullable = false, unique = true, length = 12)
    private String companyName;  // ✅ Meaningful ID, NOT companyId UUID

    // NO: private UUID companyId;  ❌ WRONG - don't use UUID for other service's entity
}

// ✅ CORRECT: PartnerContact entity
@Entity
@Table(name = "partner_contacts")
public class PartnerContact {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "partner_id", nullable = false)
    private UUID partnerId;  // ✅ OK - Partner is in THIS service

    // Cross-service reference to User (different service)
    @Column(name = "username", nullable = false, length = 100)
    private String username;  // ✅ Meaningful ID, NOT userId UUID

    // NO: private UUID userId;  ❌ WRONG - don't use UUID for other service's entity
}
```

#### Rule 3: NO Database Foreign Keys Across Services

```sql
-- ✅ CORRECT: partner-coordination-service database schema
CREATE TABLE partners (
    id UUID PRIMARY KEY,
    company_name VARCHAR(12) NOT NULL UNIQUE,  -- ✅ Meaningful ID
    -- NO FOREIGN KEY to companies.id  ✅ Correct
);

CREATE TABLE partner_contacts (
    id UUID PRIMARY KEY,
    partner_id UUID NOT NULL REFERENCES partners(id),  -- ✅ OK - same service
    username VARCHAR(100) NOT NULL,  -- ✅ Meaningful ID
    -- NO FOREIGN KEY to users.id  ✅ Correct
);

-- ❌ WRONG: Don't do this
CREATE TABLE partners (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL,  -- ❌ WRONG - UUID reference
    FOREIGN KEY (company_id) REFERENCES companies(id)  -- ❌ WRONG - cross-service FK
);
```

#### Rule 4: Cross-Service Data Access via HTTP APIs

```java
// ✅ CORRECT: Partner service accesses Company data via HTTP
@Service
public class PartnerService {
    private final CompanyServiceClient companyServiceClient;
    private final UserServiceClient userServiceClient;

    @Cacheable(value = "partners", key = "#companyName")
    public PartnerResponse getPartner(String companyName) {
        // 1. Get partner from OWN database (uses companyName)
        Partner partner = partnerRepository.findByCompanyName(companyName)
            .orElseThrow(() -> new PartnerNotFoundException(companyName));

        // 2. Enrich with Company data via HTTP call
        CompanyResponse company = companyServiceClient.getCompany(companyName);

        return PartnerResponse.builder()
            .companyName(partner.getCompanyName())
            .companyDisplayName(company.getDisplayName())  // From HTTP call
            .partnershipLevel(partner.getPartnershipLevel())
            .build();
    }
}

// HTTP client with JWT token propagation
@Component
public class CompanyServiceClient {
    @Cacheable(value = "companies", key = "#companyName")
    public CompanyResponse getCompany(String companyName) {
        HttpHeaders headers = createHeadersWithJwtToken();
        HttpEntity<Void> request = new HttpEntity<>(headers);

        return restTemplate.exchange(
            companyServiceUrl + "/api/v1/companies/" + companyName,
            HttpMethod.GET,
            request,
            CompanyResponse.class
        ).getBody();
    }
}
```

#### Rule 5: Within-Service Relationships Can Use UUID FKs

```java
// ✅ CORRECT: TopicVote entity in partner-coordination-service
@Entity
@Table(name = "topic_votes")
public class TopicVote {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    // Within same service - UUID FK is OK
    @Column(name = "partner_id", nullable = false)
    private UUID partnerId;  // ✅ OK - Partner is in THIS service

    // Cross-service reference - use meaningful ID
    @Column(name = "topic_id", nullable = false)
    private String topicId;  // ✅ Meaningful ID to Event Management Service topic
    // NO: private UUID topicUuid;  ❌ WRONG
}
```

```sql
-- Database schema shows the pattern
CREATE TABLE topic_votes (
    id UUID PRIMARY KEY,
    partner_id UUID NOT NULL REFERENCES partners(id),  -- ✅ Within-service FK OK
    topic_id VARCHAR(100) NOT NULL,  -- ✅ Cross-service: meaningful ID
    -- NO FOREIGN KEY to topics.id  ✅ Correct
);
```

### Summary: UUID vs Meaningful ID Decision Tree

```
Does this field reference an entity?
├─ YES → Is the entity in THIS service's database?
│   ├─ YES → Use UUID FK ✅
│   │   Example: partner_id UUID REFERENCES partners(id)
│   └─ NO → Use Meaningful ID ✅
│       Example: company_name VARCHAR(12), username VARCHAR(100)
└─ NO → Use appropriate data type
    Example: vote_value INTEGER, description TEXT
```

### Shared Kernel Services

Created `SlugGenerationService` in shared-kernel for consistent slug generation:

```java
@Service
public class SlugGenerationService {

    public String generateUsername(String firstName, String lastName) {
        String first = slugifyName(firstName);
        String last = slugifyName(lastName);
        return first + "." + last;
    }

    public String ensureUniqueUsername(String base, Function<String, Boolean> exists) {
        if (!exists.apply(base)) return base;

        int suffix = 2;
        while (exists.apply(base + "." + suffix)) {
            suffix++;
        }
        return base + "." + suffix;
    }

    public String slugifySessionTitle(String title) {
        return title.toLowerCase()
            .replaceAll("ä", "ae")
            .replaceAll("ö", "oe")
            .replaceAll("ü", "ue")
            .replaceAll("ß", "ss")
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "");
    }
}
```

### Database Migrations

Each service required schema migrations to add meaningful ID columns:

**Event Management Service (V3, V4)**:
```sql
-- V3__Add_event_code_and_organizer_username.sql
ALTER TABLE events
ADD COLUMN event_code VARCHAR(50) UNIQUE NOT NULL;

ALTER TABLE events
ADD COLUMN organizer_username VARCHAR(100) NOT NULL;

CREATE UNIQUE INDEX idx_events_event_code ON events(event_code);
```

**Company-User Management Service (V3, V10)**:
```sql
-- V3__Create_companies_schema.sql (updated)
ALTER TABLE companies
ADD COLUMN name VARCHAR(12) UNIQUE NOT NULL;

ALTER TABLE companies
ADD COLUMN display_name VARCHAR(255) NOT NULL;

CREATE UNIQUE INDEX idx_companies_name_lower ON companies(LOWER(name));

-- V10__Create_logos_table.sql
-- Logos table references companies by name (not UUID FK)
ALTER TABLE logos
ADD COLUMN entity_name VARCHAR(255) NOT NULL;
```

## Consequences

### Positive

1. **Improved User Experience**:
   - Shareable URLs: `batbern.ch/events/BATbern56`
   - Memorable identifiers for marketing and communication
   - Professional appearance (`/users/john.doe` vs UUID)

2. **Better Developer Experience**:
   - Clean, readable API responses
   - Easy debugging and tracing
   - Intuitive API documentation
   - Faster manual testing (no UUID copy-paste)

3. **SEO Benefits**:
   - Semantic URLs improve search engine ranking
   - Event codes appear in search results
   - Better social media link previews

4. **Business Alignment**:
   - Event codes match physical conference naming (BATbern #142)
   - Company short names match existing branding
   - Username format matches professional standards

5. **Performance Maintained**:
   - UUID PKs preserve join performance
   - Unique indexes on meaningful IDs are fast (B-tree)
   - Single-query DTO projections avoid N+1

### Negative

1. **Schema Complexity**:
   - Dual identifier strategy adds columns
   - More indexes to maintain (storage overhead ~5-10%)
   - Migration complexity for existing data (mitigated: greenfield)

2. **API Backward Compatibility**:
   - Breaking change for existing API consumers
   - **Mitigation**: Accept both UUID and meaningful ID during transition
   - **Status**: Not applicable (greenfield system)

3. **Collision Handling Required**:
   - Username collisions need suffix logic (`.2`, `.3`)
   - Company name collisions require user intervention
   - **Mitigation**: Unique constraints + clear error messages

4. **ID Immutability Constraints**:
   - Meaningful IDs must be immutable (used in URLs, external refs)
   - Cannot rename companies or change usernames easily
   - **Mitigation**: Design UX to set meaningful IDs at creation time

5. **Cross-Service Coordination**:
   - User service must provide username to event service
   - Requires consistent slug generation across services
   - **Mitigation**: SlugGenerationService in shared-kernel

### Technical Debt

1. **ID Wrapper Removal**:
   - Removed `EventId`, `UserId`, `CompanyId` wrapper classes
   - Simplified domain events to use `String` directly
   - **Benefit**: Reduced complexity, cleaner DTOs
   - **Risk**: Type safety reduced (String vs EventId)
   - **Mitigation**: OpenAPI schemas enforce format validation

2. **No UUID in Domain Events**:
   - Domain events use meaningful IDs (`eventCode`, `username`)
   - Event consumers must handle meaningful ID lookups
   - **Risk**: Event processing slightly more complex
   - **Mitigation**: Index-backed lookups are fast

## Implementation Status

### Completed (Stories 1.16.1 + 1.16.2)

- [x] Shared Kernel: SlugGenerationService created
- [x] Shared Kernel: ID wrappers removed (EventId, UserId, etc.)
- [x] Event Management Service: eventCode implemented
- [x] Event Management Service: organizerUsername (String) replaces organizerId (UUID)
- [x] Company-User Management Service: company.name + display_name
- [x] Company-User Management Service: Logo service uses entity_name
- [x] Database migrations: All meaningful ID columns added
- [x] API Gateway: Routes accept meaningful IDs
- [x] Frontend: React Router uses meaningful IDs
- [x] OpenAPI specs: Updated with meaningful ID patterns
- [x] Bruno E2E tests: Use meaningful IDs

### Testing Coverage

**Integration Tests** (PostgreSQL via Testcontainers):
- Event repository: `findByEventCode()` tested
- Company repository: `findByNameIgnoreCase()` tested
- User service: Username generation + collision handling tested
- Session repository: Slug uniqueness within event tested

**E2E Tests** (Bruno):
- `GET /api/v1/events/{eventCode}`
- `GET /api/v1/companies/{companyName}`
- `POST /api/v1/files/presigned-url` (entity_name parameter)

**Frontend Tests** (Vitest):
- React Router with meaningful ID paths
- API service layer uses meaningful IDs
- 1277 passing tests (40 skipped for unimplemented backend features)

## Alignment with Industry Best Practices

This decision aligns with how leading platforms handle identifiers:

| Platform | Internal ID | Public API ID | Example |
|----------|-------------|---------------|---------|
| **GitHub** | Numeric DB ID | Repository slug | `github.com/facebook/react` |
| **Stripe** | Numeric DB ID | Prefixed string | `cus_1234567890` |
| **Slack** | UUID/Numeric | Channel name | `#general`, `@john.doe` |
| **Medium** | UUID | Article slug | `medium.com/@author/article-title-abc123` |
| **BATbern** | UUID | Meaningful ID | `batbern.ch/events/BATbern56` |

## Alternatives Considered and Rejected

### Alternative 1: Natural Keys as Primary Keys

**Description**: Use meaningful IDs as primary keys directly (no UUID PKs).

**Rejected Because**:
- Requires complex update cascade if ID changes (despite immutability goal)
- Distributed systems harder (need central ID generator)
- Larger index size (VARCHAR vs UUID)
- Foreign keys less efficient (string joins vs UUID joins)

### Alternative 2: Composite Keys

**Description**: Use `(tenant_id, sequential_id)` composite keys.

**Rejected Because**:
- Over-engineering for single-tenant system
- More complex foreign key relationships
- No tenant concept in BATbern (single organization)

### Alternative 3: Hashids/Short UUIDs

**Description**: Use shortened UUID variants (e.g., Base62 encoded).

**Rejected Because**:
- Still not human-meaningful (random characters)
- Does not match business requirements (event numbers, usernames)
- Adds encoding/decoding complexity

## Future Considerations

### API Versioning Strategy

If we need to support multiple ID formats in the future:

```
GET /api/v1/events/{eventCode}  # Current
GET /api/v2/events/{eventCode}  # Future (if breaking changes needed)
```

### Meaningful ID Evolution

As the system grows:
1. **Event codes**: Could evolve to include year (`BATbern2026-01`)
2. **Company names**: Could support vanity URLs (`/companies/google` → `/companies/GoogleZH`)
3. **Usernames**: Could add display names separate from login names

### Performance Monitoring

Monitor these metrics:
- Lookup performance: UUID vs meaningful ID queries
- Index size: Storage overhead of dual identifiers
- Cache hit rates: Meaningful ID → Entity mappings

## Related Documents

- **Architecture**: `docs/architecture/03-data-architecture.md` (updated with meaningful ID columns)
- **API Design**: `docs/architecture/04-api-design.md` (URL patterns)
- **Stories**:
  - `docs/stories/1.16.1-meaningful-ids-public-urls.md`
  - `docs/stories/1.16.2-eliminate-uuids-from-api.md`
- **OpenAPI Specs**:
  - `docs/api/events-api.openapi.yml`
  - `docs/api/companies-api.openapi.yml`
- **E2E Tests**:
  - `bruno-tests/events-api/*`
  - `bruno-tests/companies-api/*`
  - `bruno-tests/file-upload-api/*`

## Revision History

| Date | Version | Changes | Author |
|------|---------|---------|--------|
| 2025-11-02 | 1.0 | Initial ADR creation | Winston (Architect Agent) |
| 2025-11-08 | 1.1 | **CRITICAL CLARIFICATION**: Added comprehensive "Microservice Isolation Rules" section with 5 rules and decision tree. Made it crystal clear that cross-service references MUST use meaningful IDs (companyName, username, eventCode), NOT UUIDs. Updated database layer description to distinguish within-service vs. cross-service references. | Claude Code |

---

**This ADR documents a foundational architectural decision that affects all APIs, URLs, and external integrations in the BATbern platform. All microservices MUST follow the microservice isolation rules: use meaningful IDs for cross-service references, NEVER UUIDs.**
