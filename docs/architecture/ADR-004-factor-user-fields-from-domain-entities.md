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

All domain entities (Speaker, Attendee, Partner) will:
1. **Reference User by UUID foreign key** (internal database layer)
2. **Expose username in APIs** (public API layer per ADR-003)
3. **Never duplicate user profile fields** (email, name, bio, photo)
4. **Only store domain-specific fields** (expertiseAreas, engagementHistory, etc.)

### Database Layer: Foreign Key to User

```sql
-- Speaker entity references User
CREATE TABLE speakers (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,  -- FK to users.id (one-to-one)
    availability VARCHAR(20),       -- Domain-specific
    workflow_state VARCHAR(50),     -- Domain-specific
    expertise_areas TEXT[],         -- Domain-specific
    speaking_topics TEXT[],         -- Domain-specific
    speaking_history JSONB,         -- Domain-specific
    -- NO email, firstName, lastName, bio, photo
    CONSTRAINT fk_speaker_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_speakers_user_id ON speakers(user_id);
```

**Key Design Decisions**:
- `user_id` is UNIQUE (one-to-one relationship: one user = one speaker profile)
- `ON DELETE CASCADE` - deleting user deletes speaker profile
- No duplicated user fields in speaker table
- Domain-specific fields only

### API Layer: Username-Based Endpoints with Joined Data

Following ADR-003, APIs use **username** as the public identifier but **join User + domain entity data** internally:

```http
GET /api/v1/speakers/{username}
```

**Response DTO** (combines User + Speaker):
```json
{
  "username": "john.doe",
  "email": "john.doe@example.com",          // From User
  "firstName": "John",                       // From User
  "lastName": "Doe",                         // From User
  "company": "GoogleZH",                     // From User.companyId
  "bio": "Experienced architect...",         // From User.bio
  "profilePictureUrl": "https://cdn...",     // From User.profilePictureUrl
  "availability": "available",               // From Speaker
  "workflowState": "ready",                  // From Speaker
  "expertiseAreas": ["Security", "Cloud"],   // From Speaker
  "speakingTopics": ["Blockchain"]           // From Speaker
}
```

### Repository Layer: JPQL Constructor Projections

To avoid N+1 queries, use **JPQL constructor projections** (as established in ADR-003):

```java
@Query("""
    SELECT new ch.batbern.speaker.dto.SpeakerResponse(
        u.username,
        u.email,
        u.firstName,
        u.lastName,
        u.bio,
        u.profilePictureUrl,
        c.name,
        s.availability,
        s.workflowState,
        s.expertiseAreas,
        s.speakingTopics
    )
    FROM Speaker s
    INNER JOIN User u ON s.userId = u.id
    LEFT JOIN Company c ON u.companyId = c.id
    WHERE u.username = :username
    """)
Optional<SpeakerResponse> findSpeakerByUsername(@Param("username") String username);
```

**Benefits**:
- Single database query (efficient join)
- Automatic FK resolution (user_id → User → username)
- Type-safe DTO construction
- No N+1 problem

### Domain Entity Design Patterns

#### Speaker Entity (Refactored)

**REMOVED Fields** (now in User):
- ❌ `email`, `firstName`, `lastName`
- ❌ `bio` (use User.bio as single source)
- ❌ `profilePhotoUrl` (use User.profilePictureUrl)
- ❌ `position` (removed entirely as redundant)

**KEPT Fields** (domain-specific):
- ✅ `id` (UUID primary key)
- ✅ `userId` (UUID FK to User - internal)
- ✅ `availability` (available, busy, unavailable)
- ✅ `workflowState` (open, contacted, ready, confirmed, cancelled)
- ✅ `expertiseAreas` (array of expertise domains)
- ✅ `speakingTopics` (array of topics)
- ✅ `speakingHistory` (JSONB - past speaking engagements)
- ✅ `linkedInUrl`, `twitterHandle` (speaker-specific social)
- ✅ `certifications`, `languages` (speaker-specific)
- ✅ `slotPreferences` (for speaker coordination workflow)

#### Attendee Entity (Refactored)

**REMOVED Fields** (now in User):
- ❌ `email`, `firstName`, `lastName`
- ❌ `position` (removed entirely)

**KEPT Fields** (domain-specific):
- ✅ `id` (UUID primary key)
- ✅ `userId` (UUID FK to User - internal)
- ✅ `eventRegistrations` (many-to-many with events)
- ✅ `engagementHistory` (session attendance, interactions)
- ✅ `newsletterSubscription` (boolean + preferences)
- ✅ `contentPreferences` (topics of interest - different from User.preferences)
- ✅ `gdprConsent` (legal: consent timestamp, IP address)

#### Partner Entity (Refactored)

Partners are **company-centric** (not person-centric):

```java
@Entity
public class Partner {
    private UUID id;
    private UUID companyId;  // FK to Company (primary relationship)
    private PartnershipLevel level;
    private LocalDate startDate;
    private LocalDate endDate;
    private boolean isActive;
    // ...
}

@Entity
public class PartnerContact {
    private UUID id;
    private UUID partnerId;   // FK to Partner
    private UUID userId;      // FK to User (NOT duplicating user fields)
    private ContactRole role; // Primary, Billing, Technical
    private boolean isPrimary;
}
```

**Key Decision**: PartnerContact references User by `userId` (no duplication).

### Cross-Service Communication

When domain services need user information:

1. **Synchronous**: Join at database layer (JPQL projections)
2. **Asynchronous**: Listen to User domain events for caching (optional optimization)

**Example - Speaker Service needs user data**:
```java
// Option 1: Join query (preferred for read operations)
speakerRepository.findSpeakerByUsername(username); // Returns joined DTO

// Option 2: Separate queries (if partial data needed)
User user = userRepository.findByUsername(username);
Speaker speaker = speakerRepository.findByUserId(user.getId());
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

### For New Domain Services

When creating a new domain service (e.g., Speaker Coordination Service):

1. **Entity Design**:
   ```java
   @Entity
   @Table(name = "speakers")
   public class Speaker {
       @Id
       private UUID id;

       @Column(name = "user_id", nullable = false, unique = true)
       private UUID userId;  // FK to users.id

       // Domain-specific fields only
       @Enumerated(EnumType.STRING)
       private Availability availability;

       // NO email, firstName, lastName, bio, photo
   }
   ```

2. **Repository Pattern**:
   ```java
   public interface SpeakerRepository extends JpaRepository<Speaker, UUID> {
       @Query("SELECT new SpeakerResponse(...) FROM Speaker s " +
              "INNER JOIN User u ON s.userId = u.id WHERE u.username = :username")
       Optional<SpeakerResponse> findByUsername(@Param("username") String username);
   }
   ```

3. **DTO Design**:
   ```java
   // Response DTO combines User + Speaker
   public record SpeakerResponse(
       String username,      // From User
       String email,         // From User
       String firstName,     // From User
       String bio,           // From User
       String availability,  // From Speaker
       List<String> topics   // From Speaker
   ) {}
   ```

4. **Controller Pattern**:
   ```java
   @GetMapping("/speakers/{username}")
   public SpeakerResponse getSpeaker(@PathVariable String username) {
       return speakerRepository.findByUsername(username)
           .orElseThrow(() -> new SpeakerNotFoundException(username));
   }
   ```

### Database Migration Template

For each domain entity:

```sql
-- V1__Create_speakers_table.sql
CREATE TABLE speakers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE,

    -- Domain-specific fields
    availability VARCHAR(20),
    workflow_state VARCHAR(50),
    expertise_areas TEXT[],

    -- NO: email, first_name, last_name, bio, photo

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_speaker_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_speakers_user_id ON speakers(user_id);
CREATE INDEX idx_speakers_availability ON speakers(availability);
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

---

**This ADR establishes the foundational principle: Domain entities reference User by foreign key and never duplicate user profile fields. Implementation can use either database joins or API-based access depending on deployment architecture and service boundary requirements.**
