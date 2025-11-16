# ADR-004: Factor User Fields from Domain Entities

**Status**: Accepted
**Date**: 2025-11-02
**Decision Makers**: Development Team
**Related ADRs**: ADR-003 (Meaningful Identifiers in Public APIs)

## Context

The BATbern platform has a `company-user-management-service` that manages user profiles with comprehensive user information including personal details, company affiliation, bio, profile picture, preferences, and settings. Users can have multiple roles: ORGANIZER, SPEAKER, PARTNER, and ATTENDEE.

As we design domain-specific services (Speaker Coordination, Attendee Experience, Partner Coordination), we face a fundamental architectural question: **Should domain entities (Speaker, Attendee, Partner) duplicate user profile fields or reference the User entity?**

### Current State

**User Entity** (company-user-management-service) contains:
- Personal identity: `firstName`, `lastName`, `email`, `username`
- Company affiliation: `companyId`
- Profile: `bio` (TEXT), `profilePictureUrl`, `profilePictureS3Key`
- Preferences: theme, language, notifications
- Settings: privacy, visibility, timezone
- Roles: Set of role assignments (ORGANIZER, SPEAKER, PARTNER, ATTENDEE)

**Documented Domain Entities** (from data architecture docs):

**Speaker Entity** currently documented with:

- ⚠️ `email`, `firstName`, `lastName` - **DUPLICATES User**
- ⚠️ `profile.shortBio` - **DUPLICATES User.bio**
- ⚠️ `profile.profilePhotoUrl` - **DUPLICATES User.profilePictureUrl**
- ⚠️ `position` - **Redundant field**
- ✅ `expertiseAreas`, `speakingTopics`, `speakingHistory` - **Domain-specific**
- ✅ `availability`, `workflowState` - **Domain-specific**

**Attendee Entity** currently documented with:
- ⚠️ `email`, `firstName`, `lastName` - **DUPLICATES User**
- ⚠️ `position` - **Redundant field**
- ✅ `eventRegistrations`, `engagementHistory` - **Domain-specific**
- ✅ `newsletterSubscription`, `contentPreferences`, `gdprConsent` - **Domain-specific**

### Problem Statement

Duplicating user profile fields across domain entities creates multiple problems:

1. **Data Inconsistency Risk**:
   - User updates email in User service, but Speaker entity has stale email
   - Profile picture updated via User API, but Speaker API returns old photo
   - Bio changed, but multiple domain entities have different versions

2. **Violation of Single Source of Truth (SSOT)**:
   - Which bio is correct: User.bio or Speaker.detailedBio?
   - User changes name, must propagate to 4+ domain entities
   - Synchronization complexity and potential race conditions

3. **Storage Inefficiency**:
   - Same data stored in 5+ tables (users + 4 domain entities)
   - Profile pictures referenced 5+ times
   - Email addresses duplicated across entities

4. **API Confusion**:
   - Should clients call `/users/{username}` or `/speakers/{username}` to get speaker email?
   - Update bio via User API or Speaker API?
   - Two APIs could return conflicting data

5. **Business Logic Duplication**:
   - Email validation in multiple services
   - Profile picture upload logic duplicated
   - Name formatting rules scattered across services

### Explored Alternatives

#### Alternative 1: Duplicate Fields with Event-Driven Sync

**Description**: Keep duplicated fields in domain entities. Use domain events to synchronize changes from User service to domain services.

**Example**:
```java
// Speaker entity has its own email, firstName, lastName
@Entity
public class Speaker {
    private UUID id;
    private String email;       // Duplicated
    private String firstName;   // Duplicated
    private String lastName;    // Duplicated
    // ... domain-specific fields
}

// Listen for UserUpdatedEvent and sync changes
@EventListener
public void onUserUpdated(UserUpdatedEvent event) {
    speakerRepository.findByUserId(event.getUserId())
        .ifPresent(speaker -> {
            speaker.setEmail(event.getEmail());
            speaker.setFirstName(event.getFirstName());
            // ...
        });
}
```

**Rejected Because**:
- Eventually consistent (temporary data inconsistency)
- Complex synchronization logic in every domain service
- Event ordering issues (what if events arrive out of order?)
- Increased storage and maintenance burden
- Still doesn't solve "which is the source of truth?" question

#### Alternative 2: Read-Through Cache Pattern

**Description**: Domain entities reference User by ID. Services fetch User data on-demand and cache it.

**Rejected Because**:
- Adds caching complexity to every domain service
- Cache invalidation is hard (distributed cache coordination)
- N+1 query problem for lists (e.g., list of speakers)
- Doesn't reduce API calls (still need to fetch user data)

#### Alternative 3: GraphQL Federation

**Description**: Use GraphQL to federate User and domain entities at query time.

**Rejected Because**:
- Requires GraphQL adoption (not current tech stack)
- Adds significant architectural complexity
- Still requires domain entities to store user IDs
- Doesn't solve underlying data model question

## Decision

We have decided to implement **User Entity as Single Source of Truth with Foreign Key References**:

### Principle: Domain Entities Reference User, Never Duplicate

**CRITICAL RULE (ADR-003 Microservice Isolation)**: Domain entities in different services MUST reference User by **username (meaningful ID)**, NOT by userId UUID.

All domain entities (Speaker, Attendee, Partner) will:
1. **Store username (meaningful ID)** for cross-service references to User entity
2. **NEVER store userId UUID** when User is in a different service
3. **Expose username in APIs** (public API layer per ADR-003)
4. **Never duplicate user profile fields** (email, name, bio, photo)
5. **Only store domain-specific fields** (expertiseAreas, engagementHistory, etc.)
6. **Use UUID PKs** only for entities owned by this service

### Database Layer: Meaningful ID Reference (ADR-003 Compliant)

**CRITICAL**: Domain entities in separate microservices MUST store username (meaningful ID), NOT userId UUID.

```sql
-- ✅ CORRECT: Speaker entity in speaker-coordination-service
CREATE TABLE speakers (
    id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,  -- ADR-003: Meaningful ID, NOT userId UUID
    availability VARCHAR(20),                -- Domain-specific
    workflow_state VARCHAR(50),              -- Domain-specific
    expertise_areas TEXT[],                  -- Domain-specific
    speaking_topics TEXT[],                  -- Domain-specific
    speaking_history JSONB,                  -- Domain-specific
    -- NO email, firstName, lastName, bio, photo
    -- NO FOREIGN KEY to users.id (different service)
);

CREATE UNIQUE INDEX idx_speakers_username ON speakers(username);

-- ❌ WRONG: Don't use userId UUID for cross-service references
CREATE TABLE speakers (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,  -- ❌ WRONG - UUID reference to different service
    CONSTRAINT fk_speaker_user FOREIGN KEY (user_id) REFERENCES users(id)  -- ❌ WRONG - cross-service FK
);
```

**Key Design Decisions**:
- `username` is UNIQUE (one-to-one relationship: one user = one speaker profile)
- NO database foreign key to users table (different service, ADR-003)
- User data enriched via HTTP call to User Service API
- No duplicated user fields in speaker table
- Domain-specific fields only

### API Layer: Username-Based Endpoints with HTTP Enrichment

Following ADR-003, APIs use **username** as the public identifier and **enrich domain entity data with User data via HTTP calls**:

```http
GET /api/v1/speakers/{username}
```

**Response DTO** (combines User + Speaker via HTTP enrichment):
```json
{
  "username": "john.doe",
  "email": "john.doe@example.com",          // From User Service HTTP call
  "firstName": "John",                       // From User Service HTTP call
  "lastName": "Doe",                         // From User Service HTTP call
  "company": "GoogleZH",                     // From User Service HTTP call
  "bio": "Experienced architect...",         // From User Service HTTP call
  "profilePictureUrl": "https://cdn...",     // From User Service HTTP call
  "availability": "available",               // From Speaker table
  "workflowState": "ready",                  // From Speaker table
  "expertiseAreas": ["Security", "Cloud"],   // From Speaker table
  "speakingTopics": ["Blockchain"]           // From Speaker table
}
```

### Service Layer: HTTP Client Integration (ADR-003 Compliant)

**CRITICAL**: Cross-service data access MUST use HTTP clients, NOT JPQL joins.

```java
// ❌ WRONG: Don't use JPQL joins across services
@Query("""
    SELECT new SpeakerResponse(u.username, u.email, ...)
    FROM Speaker s
    INNER JOIN User u ON s.userId = u.id  -- ❌ Cross-service join
    """)

// ✅ CORRECT: Use HTTP client for cross-service data
@Service
public class SpeakerService {
    private final SpeakerRepository speakerRepository;
    private final UserServiceClient userServiceClient;

    @Transactional(readOnly = true)
    public SpeakerResponse getSpeaker(String username) {
        // 1. Get speaker from OWN database
        Speaker speaker = speakerRepository.findByUsername(username)
            .orElseThrow(() -> new SpeakerNotFoundException(username));

        // 2. Enrich with User data via HTTP call
        UserResponse user = userServiceClient.getUser(username);

        // 3. Combine into response DTO
        return SpeakerResponse.builder()
            .username(user.getUsername())
            .email(user.getEmail())                    // From HTTP call
            .firstName(user.getFirstName())            // From HTTP call
            .lastName(user.getLastName())              // From HTTP call
            .bio(user.getBio())                        // From HTTP call
            .profilePictureUrl(user.getProfilePictureUrl())  // From HTTP call
            .availability(speaker.getAvailability())   // From Speaker table
            .workflowState(speaker.getWorkflowState()) // From Speaker table
            .expertiseAreas(speaker.getExpertiseAreas())  // From Speaker table
            .build();
    }
}

// HTTP Client with JWT token propagation and caching
@Component
public class UserServiceClient {
    @Cacheable(value = "users", key = "#username")
    public UserResponse getUser(String username) {
        HttpHeaders headers = createHeadersWithJwtToken();
        HttpEntity<Void> request = new HttpEntity<>(headers);

        return restTemplate.exchange(
            userServiceUrl + "/api/v1/users/" + username,
            HttpMethod.GET,
            request,
            UserResponse.class
        ).getBody();
    }

    private HttpHeaders createHeadersWithJwtToken() {
        // Extract JWT from SecurityContext and propagate
        // See ADR-003 for full implementation
    }
}
```

**Repository Pattern** (simple, no cross-service joins):
```java
public interface SpeakerRepository extends JpaRepository<Speaker, UUID> {
    Optional<Speaker> findByUsername(String username);  // Uses username, not userId
}
```

**Benefits**:
- Microservice isolation (no cross-service database dependencies)
- Service can be deployed independently
- Clear service boundaries
- HTTP caching reduces latency (15-minute TTL)
- JWT token propagation for authentication

### Domain Entity Design Patterns

#### Speaker Entity (Refactored - ADR-003 Compliant)

**REMOVED Fields** (now in User):
- ❌ `email`, `firstName`, `lastName`
- ❌ `bio` (use User.bio as single source)
- ❌ `profilePhotoUrl` (use User.profilePictureUrl)
- ❌ `position` (removed entirely as redundant)

**KEPT Fields** (domain-specific):
- ✅ `id` (UUID primary key)
- ✅ `username` (VARCHAR - meaningful ID, NOT userId UUID)
- ✅ `availability` (available, busy, unavailable)
- ✅ `workflowState` (open, contacted, ready, confirmed, cancelled)
- ✅ `expertiseAreas` (array of expertise domains)
- ✅ `speakingTopics` (array of topics)
- ✅ `speakingHistory` (JSONB - past speaking engagements)
- ✅ `linkedInUrl`, `twitterHandle` (speaker-specific social)
- ✅ `certifications`, `languages` (speaker-specific)
- ✅ `slotPreferences` (for speaker coordination workflow)

#### Attendee Entity (Refactored - ADR-003 Compliant)

**REMOVED Fields** (now in User):
- ❌ `email`, `firstName`, `lastName`
- ❌ `position` (removed entirely)

**KEPT Fields** (domain-specific):
- ✅ `id` (UUID primary key)
- ✅ `username` (VARCHAR - meaningful ID, NOT userId UUID)
- ✅ `eventRegistrations` (many-to-many with events)
- ✅ `engagementHistory` (session attendance, interactions)
- ✅ `newsletterSubscription` (boolean + preferences)
- ✅ `contentPreferences` (topics of interest - different from User.preferences)
- ✅ `gdprConsent` (legal: consent timestamp, IP address)

#### Partner Entity (Refactored - ADR-003 Compliant)

Partners are **company-centric** (not person-centric):

```java
// ✅ CORRECT: Partner entity in partner-coordination-service
@Entity
public class Partner {
    private UUID id;  // ✅ UUID PK for Partner (owned by this service)
    private String companyName;  // ✅ Meaningful ID, NOT companyId UUID
    private PartnershipLevel level;
    private LocalDate startDate;
    private LocalDate endDate;
    private boolean isActive;
    // ...
}

// ✅ CORRECT: PartnerContact entity
@Entity
public class PartnerContact {
    private UUID id;
    private UUID partnerId;   // ✅ OK - Partner is in THIS service
    private String username;  // ✅ Meaningful ID, NOT userId UUID
    private ContactRole role; // Primary, Billing, Technical
    private boolean isPrimary;
    // NO: private UUID userId;  ❌ WRONG
}

// ❌ WRONG: Don't use UUIDs for cross-service references
@Entity
public class Partner {
    private UUID id;
    private UUID companyId;  // ❌ WRONG - UUID reference to different service
}
```

**Key Decision**: PartnerContact references User by `username` (meaningful ID), NOT `userId` UUID (ADR-003 microservice isolation).

### Cross-Service Communication (ADR-003 Microservice Isolation)

**CRITICAL**: Domain services access User information via **HTTP API calls**, NOT database joins.

When domain services need user information:

1. **Synchronous HTTP calls** (primary pattern):
   - Use UserServiceClient with JWT token propagation
   - Cache responses (15-minute TTL with Caffeine)
   - Handle errors: HttpClientErrorException, HttpServerErrorException, ResourceAccessException

2. **Asynchronous domain events** (optional optimization):
   - Listen to User domain events for cache warming
   - NOT for maintaining duplicated data

**Example - Speaker Service accesses User data**:
```java
// ✅ CORRECT: HTTP-based access
@Service
public class SpeakerService {
    private final SpeakerRepository speakerRepository;
    private final UserServiceClient userServiceClient;

    public SpeakerResponse getSpeaker(String username) {
        // 1. Get Speaker from OWN database
        Speaker speaker = speakerRepository.findByUsername(username)
            .orElseThrow(() -> new SpeakerNotFoundException(username));

        // 2. Get User data via HTTP call (with caching)
        UserResponse user = userServiceClient.getUser(username);

        // 3. Combine into response
        return buildSpeakerResponse(speaker, user);
    }
}

// ❌ WRONG: Don't use database joins across services
@Query("""
    SELECT new SpeakerResponse(...)
    FROM Speaker s
    INNER JOIN User u ON s.userId = u.id  -- ❌ Cross-service join
    """)
```

### User Role Management

User roles remain in User service:

```java
@Entity
public class User {
    // ...
    @ElementCollection
    @CollectionTable(name = "role_assignments")
    @Enumerated(EnumType.STRING)
    private Set<UserRole> roles; // ORGANIZER, SPEAKER, PARTNER, ATTENDEE
}
```

**Important**: Having `roles = [SPEAKER]` does NOT automatically create a Speaker entity.

**Workflow**:
1. User registers → User entity created with `roles = [ATTENDEE]` (default)
2. Organizer promotes user to Speaker → `roles += SPEAKER`
3. Speaker Coordination Service creates Speaker entity → `Speaker(userId = user.id)` created
4. Now user has both User profile + Speaker profile

### API Update Patterns

**Reading Data** (GET requests):
- Always join User + domain entity
- Return flattened DTO to API consumers
- Example: `GET /speakers/{username}` returns User fields + Speaker fields

**Updating User Fields** (PUT/PATCH):
- User profile fields (name, bio, photo) → `/users/me` endpoint
- Domain-specific fields → `/speakers/me` endpoint

**Example**:
```http
# Update bio (applies to all roles)
PATCH /api/v1/users/me
{
  "bio": "Updated bio visible in Speaker, Attendee, Partner contexts"
}

# Update speaker-specific field
PATCH /api/v1/speakers/me
{
  "availability": "busy",
  "expertiseAreas": ["Security", "Cloud", "AI"]
}
```

## Consequences

### Positive

1. **Single Source of Truth**:
   - User profile fields have exactly one storage location
   - No synchronization logic needed
   - No data inconsistency risk

2. **Storage Efficiency**:
   - Email, name, bio, photo stored once (not 5+ times)
   - Reduced database storage (estimated 40% reduction in duplicated data)

3. **Simplified Business Logic**:
   - Email validation in one place
   - Profile picture upload in one service
   - Name formatting rules centralized

4. **Clear API Boundaries**:
   - User API for user profile fields
   - Domain APIs for domain-specific fields
   - No confusion about which API to call

5. **Referential Integrity**:
   - Database foreign keys enforce consistency
   - Cascade deletes prevent orphaned records
   - Type-safe relationships

6. **Performance Benefits**:
   - JPQL joins are efficient (index-backed)
   - Single query returns all data (no N+1)
   - Caching simpler (cache complete DTOs)

7. **Aligns with ADR-003**:
   - Internal: UUID foreign keys (performant joins)
   - Public APIs: username identifiers (user-friendly)
   - Consistent pattern across all services

### Negative

1. **Join Complexity**:
   - Queries require joins (User + domain entity)
   - More complex JPQL queries
   - **Mitigation**: Use constructor projections (established pattern)

2. **Service Dependencies**:
   - Domain services depend on User service schema
   - Schema changes in User entity affect domain services
   - **Mitigation**: User entity is stable, managed by shared-kernel

3. **Migration Complexity**:
   - If data already exists with duplicated fields, migration required
   - **Status**: Not applicable (entities not yet implemented)

4. **Cannot Have Domain-Specific Variations**:
   - Speaker cannot have different bio from User
   - Cannot have speaker-specific email
   - **Mitigation**: This is intentional design (SSOT principle)

5. **Orphan Prevention Required**:
   - Deleting User cascades to Speaker/Attendee/Partner
   - Must handle cleanup carefully
   - **Mitigation**: ON DELETE CASCADE handles this automatically

### Technical Debt

**None Created**: This refactoring eliminates technical debt by enforcing SSOT principle from the start.

## Implementation Guidelines

### For New Domain Services (ADR-003 Compliant)

When creating a new domain service (e.g., Speaker Coordination Service):

1. **Entity Design** (stores username, NOT userId UUID):
   ```java
   @Entity
   @Table(name = "speakers")
   public class Speaker {
       @Id
       @GeneratedValue(strategy = GenerationType.AUTO)
       private UUID id;

       @Column(name = "username", nullable = false, unique = true, length = 100)
       private String username;  // ✅ Meaningful ID, NOT userId UUID

       // Domain-specific fields only
       @Enumerated(EnumType.STRING)
       private Availability availability;

       // NO email, firstName, lastName, bio, photo
       // NO userId UUID
   }
   ```

2. **Repository Pattern** (simple, no cross-service joins):
   ```java
   public interface SpeakerRepository extends JpaRepository<Speaker, UUID> {
       Optional<Speaker> findByUsername(String username);  // Simple query
   }
   ```

3. **Service Pattern** (HTTP-based enrichment):
   ```java
   @Service
   public class SpeakerService {
       private final SpeakerRepository speakerRepository;
       private final UserServiceClient userServiceClient;

       public SpeakerResponse getSpeaker(String username) {
           Speaker speaker = speakerRepository.findByUsername(username)
               .orElseThrow(() -> new SpeakerNotFoundException(username));

           UserResponse user = userServiceClient.getUser(username);

           return SpeakerResponse.builder()
               .username(user.getUsername())
               .email(user.getEmail())           // From HTTP call
               .firstName(user.getFirstName())   // From HTTP call
               .availability(speaker.getAvailability())  // From Speaker table
               .build();
       }
   }
   ```

4. **DTO Design**:
   ```java
   // Response DTO combines User + Speaker (via HTTP enrichment)
   public record SpeakerResponse(
       String username,      // From User (HTTP)
       String email,         // From User (HTTP)
       String firstName,     // From User (HTTP)
       String bio,           // From User (HTTP)
       String availability,  // From Speaker table
       List<String> topics   // From Speaker table
   ) {}
   ```

5. **Controller Pattern**:
   ```java
   @GetMapping("/speakers/{username}")
   public SpeakerResponse getSpeaker(@PathVariable String username) {
       return speakerService.getSpeaker(username);  // Service handles HTTP enrichment
   }
   ```

### Database Migration Template (ADR-003 Compliant)

For each domain entity:

```sql
-- V1__Create_speakers_table.sql
CREATE TABLE speakers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username VARCHAR(100) NOT NULL UNIQUE,  -- ✅ Meaningful ID, NOT user_id UUID

    -- Domain-specific fields
    availability VARCHAR(20),
    workflow_state VARCHAR(50),
    expertise_areas TEXT[],

    -- NO: email, first_name, last_name, bio, photo
    -- NO: user_id UUID (cross-service reference)
    -- NO: FOREIGN KEY to users.id (different service)

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_speakers_username ON speakers(username);
CREATE INDEX idx_speakers_availability ON speakers(availability);
CREATE INDEX idx_speakers_workflow_state ON speakers(workflow_state);

-- ❌ WRONG: Don't do this for cross-service references
CREATE TABLE speakers (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,  -- ❌ WRONG
    CONSTRAINT fk_speaker_user FOREIGN KEY (user_id) REFERENCES users(id)  -- ❌ WRONG
);
```

## Related Documents

- **ADR-003**: Meaningful Identifiers in Public APIs (dual-identifier pattern)
- **Architecture**: `docs/architecture/03-data-architecture.md` (will be updated)
- **API Design**: `docs/architecture/04-api-*.md` (will be updated)
- **OpenAPI Specs**:
  - `docs/api/users-api.openapi.yml` (already correct)
  - `docs/api/speakers-api.openapi.yml` (will be updated)
  - `docs/api/attendees-api.openapi.yml` (will be created)
  - `docs/api/partners-api.openapi.yml` (will be updated)

## Implementation Status

### Current Phase: Documentation Refactoring

- [ ] ADR-004 created (this document)
- [ ] Data architecture doc updated
- [ ] API architecture docs updated
- [ ] OpenAPI specs updated

### Future Phase: Code Implementation

- [ ] Speaker entity implementation (speaker-coordination-service)
- [ ] Attendee entity implementation (attendee-experience-service)
- [ ] Partner entity implementation (partner-coordination-service)
- [ ] Integration tests with PostgreSQL (Testcontainers)
- [ ] Bruno E2E tests for domain APIs

## Alignment with Domain-Driven Design

This decision aligns with DDD principles:

1. **Bounded Contexts**:
   - User context: Identity, authentication, profile
   - Speaker context: Speaking workflow, expertise, availability
   - Attendee context: Event registrations, engagement
   - Clear boundaries with no duplication

2. **Aggregates**:
   - User is the aggregate root for profile data
   - Speaker is an aggregate root for speaker workflow data
   - References between aggregates use IDs (userId)

3. **Shared Kernel**:
   - User entity types published to all services
   - Consistent ID patterns (ADR-003)
   - Shared slug generation service

4. **Anti-Corruption Layer**:
   - DTOs prevent domain entities from leaking across boundaries
   - API layer translates between internal (UUID) and public (username) identifiers

## Evolution: API-Based Access Pattern (2025-11-06)

### Context

The original ADR recommended **JPQL joins at the database layer** to combine User and domain entity data. However, during implementation of Story 1.15a.1b (Session-User Many-to-Many), we encountered cross-service JPA entity scanning issues that caused test failures.

### Problem

When event-management-service included User entity in `@EntityScan`, it triggered Hibernate schema validation expecting the `user_profiles` table to exist in the event-management-service test database, causing 101 test failures with "Schema-validation: missing table [user_profiles]".

### Solution: API-Based User Data Retrieval

Instead of direct database joins, we implemented **API-based access** to User Management Service:

```java
// OLD APPROACH (Database Join):
@Query("""
    SELECT new SessionSpeakerResponse(
        u.username, u.firstName, u.lastName, ...
    )
    FROM SessionUser su
    INNER JOIN User u ON su.userId = u.id
    WHERE su.sessionId = :sessionId
    """)
List<SessionSpeakerResponse> findBySessionId(@Param("sessionId") UUID sessionId);

// NEW APPROACH (API-Based):
@Service
public class SessionUserService {
    private final UserApiClient userApiClient;

    public SessionSpeakerResponse assignSpeakerToSession(...) {
        // Validate user exists via API
        UserProfileDTO user = userApiClient.getUserByUsername(username);

        // Create SessionUser with both userId (FK) and username (API lookup)
        SessionUser sessionUser = SessionUser.builder()
            .userId(user.getId())      // Foreign key (backward compatibility)
            .username(username)         // For API lookups
            .build();
    }
}
```

### Implementation Details

**1. REST Client Infrastructure:**
- `UserApiClient` interface with `RestTemplate` implementation
- JWT token propagation via `SecurityContext` for service-to-service auth
- 15-minute Caffeine cache (expected 80-90% hit rate)
- Comprehensive exception handling (`UserNotFoundException`, `UserServiceException`)

**2. Database Schema Evolution:**
```sql
-- Added username column to support API lookups
ALTER TABLE session_users
ADD COLUMN username VARCHAR(100);

-- Maintain userId for backward compatibility
-- Both userId (FK) and username coexist during transition
```

**3. Configuration:**
```yaml
# application.yml
user-service:
  base-url: ${USER_SERVICE_URL:http://localhost:8081}

# Environment variable
USER_SERVICE_URL=http://company-user-management-service:8080
```

### Trade-offs

**Advantages:**
- ✅ **Service Isolation**: event-management-service doesn't need User entity in classpath
- ✅ **Test Independence**: Integration tests don't require user_profiles table
- ✅ **Clear Service Boundaries**: API-first communication pattern
- ✅ **Caching**: 15-minute cache reduces API calls
- ✅ **Type Safety**: DTOs enforce API contract

**Disadvantages:**
- ⚠️ **Network Latency**: API calls slower than database joins (mitigated by caching)
- ⚠️ **Service Dependency**: event-management-service requires User Management Service availability
- ⚠️ **Fail-Fast**: No graceful degradation if User Management Service is down

### When to Use Each Pattern

**Use API-Based Access When:**
- Services deployed independently (microservices architecture)
- Service boundaries must be strictly enforced
- Test isolation is critical
- Services use different databases or deployment models

**Use Database Joins When:**
- Services share same database (monolithic or modular monolith)
- Performance is critical (high-frequency queries)
- Services are tightly coupled by design
- Strong consistency required

### Current Status

**event-management-service** uses API-based access for SessionUser → User relationship:
- ✅ Implemented: `UserApiClient` with caching
- ✅ Tests passing: 12/12 unit tests, 9/11 integration tests (2 disabled for API Gateway context)
- ✅ Migration complete: Cross-service entity scanning removed

**Future services** should evaluate both patterns based on deployment architecture and choose the approach that best fits their needs while maintaining the core ADR-004 principle: **never duplicate user profile fields**.

## Revision History

| Date | Version | Changes | Author |
|------|---------|---------|--------|
| 2025-11-02 | 1.0 | Initial ADR creation | Winston (Architect Agent) |
| 2025-11-06 | 1.1 | Added API-based access evolution section | Claude Code (Story 1.15a.1b) |
| 2025-11-08 | 1.2 | **CRITICAL CLARIFICATION**: Domain entities MUST store username (meaningful ID), NOT userId UUID for cross-service references. Updated all examples to use HTTP-based access with meaningful IDs (ADR-003 microservice isolation). | Claude Code |

---

**This ADR establishes the foundational principle: Domain entities reference User by meaningful ID (username) and never duplicate user profile fields. Implementation MUST use HTTP-based access for cross-service communication (ADR-003 microservices pattern).**
