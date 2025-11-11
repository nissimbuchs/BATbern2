# ADR-005: Anonymous Event Registration

**Status**: Accepted
**Date**: 2025-11-08
**Decision Makers**: Development Team
**Related ADRs**: ADR-003 (Meaningful Identifiers), ADR-004 (Factor User Fields from Domain Entities)

## Context

The BATbern platform needs to support **public event registration** where visitors can register for events without creating a user account. This anonymous registration must later allow users to optionally create an account and link it to their existing registrations.

### Current State

**user_profiles table** (Company User Management Service):
```sql
CREATE TABLE user_profiles (
    id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    cognito_user_id VARCHAR(255) NOT NULL UNIQUE,  -- ⚠️ NOT NULL
    email VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    company_id VARCHAR(12),
    bio TEXT,
    -- ... other fields
);
```

**registrations table** (Event Management Service):
```sql
CREATE TABLE registrations (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(id),
    registration_code VARCHAR(100) UNIQUE NOT NULL,
    attendee_id UUID NOT NULL,                -- ⚠️ NOT NULL
    attendee_username VARCHAR(100) NOT NULL,
    attendee_name VARCHAR(255) NOT NULL,       -- ⚠️ DUPLICATED
    attendee_email VARCHAR(255) NOT NULL,      -- ⚠️ DUPLICATED
    status VARCHAR(50) NOT NULL,
    registration_date TIMESTAMP WITH TIME ZONE,
    UNIQUE(event_id, attendee_email)
);
```

### Problem Statement

The current architecture prevents anonymous event registration and creates data duplication:

1. **Cannot Create Anonymous Users**:
   - `user_profiles.cognito_user_id` is `NOT NULL`
   - Registration requires authenticated Cognito account
   - Public visitors cannot register without creating account first
   - Creates friction in the registration funnel

2. **Data Duplication Violates SSOT** (Single Source of Truth):
   - `attendee_name` and `attendee_email` duplicated in registrations table
   - Violates ADR-004 principle: domain entities should reference User, never duplicate
   - If user updates email in user_profiles, registration has stale email
   - Two places to maintain same data

3. **No Account Linking Mechanism**:
   - User registers anonymously as `john.doe@example.com`
   - Later creates Cognito account with same email
   - No way to link anonymous profile to authenticated account
   - User sees no registration history after login

4. **Inconsistent API Patterns**:
   - Stories 4.1.5 and 4.1.6 reference wrong endpoints (`/api/v1/registrations`)
   - Should follow ADR-003 pattern: `/api/v1/events/{eventCode}/registrations`
   - Wireframes don't align with story implementations

### Business Requirements

**Epic 4: Public Event Website** requires:
- ✅ Anonymous visitors can register for events without account
- ✅ Registration captures: name, email, company, role
- ✅ Registration is for **whole event**, not individual sessions
- ✅ Confirmation page with QR code, no login required
- ✅ Optional: Create account to manage all registrations in one place

### Explored Alternatives

#### Alternative 1: Separate anonymous_registrations Table

**Description**: Keep `user_profiles` for authenticated users only. Create separate `anonymous_registrations` table for public registrations.

```sql
CREATE TABLE anonymous_registrations (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    email VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    company VARCHAR(255),
    role VARCHAR(100),
    status VARCHAR(50),
    UNIQUE(event_id, email)
);
```

**Rejected Because**:
- Creates two separate registration systems (anonymous vs authenticated)
- Complex account linking (migrate data from anonymous_registrations to registrations)
- Queries for "all registrations" must UNION two tables
- Violates unified user model principle
- More complex to maintain

#### Alternative 2: Store Minimal Data in registrations Only

**Description**: Don't create user_profile for anonymous users. Store email/name directly in registrations table (current state).

**Rejected Because**:
- Violates ADR-004 (no duplication of user fields)
- No unified user model for anonymous attendees
- Cannot support future features requiring user profiles (newsletter, preferences)
- Data duplication creates consistency problems
- Anonymous users can't upgrade to full accounts without data migration

#### Alternative 3: Require Account Creation for Registration

**Description**: Force users to create Cognito account before registering for events.

**Rejected Because**:
- Creates friction in registration funnel (decreased conversion rate)
- Industry best practice: minimize steps to conversion
- Competitors allow anonymous registration
- Users should choose when to create account

## Decision

We have decided to implement **Unified User Model with Optional Cognito Account**:

### Principle: user_profiles as Single Source of Truth for All Users

All users (anonymous and authenticated) are represented in `user_profiles`:
1. **Make cognito_user_id NULLABLE** - allows anonymous users
2. **Remove duplicated fields from registrations** - follows ADR-004
3. **Auto-link on first Cognito login** - seamless account linking
4. **Cross-service API communication** - Event Service → User Management API

### Database Layer: Nullable Cognito ID

```sql
-- Updated user_profiles schema
CREATE TABLE user_profiles (
    id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    cognito_user_id VARCHAR(255) UNIQUE,  -- ✅ NULLABLE (for anonymous users)
    email VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    company_id VARCHAR(12),
    bio TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    -- Ensure data integrity: must have either cognito_id OR email
    CONSTRAINT check_user_identity
        CHECK ((cognito_user_id IS NOT NULL) OR (email IS NOT NULL))
);

-- Index for anonymous user lookups
CREATE INDEX idx_user_profiles_email_anonymous
    ON user_profiles(email)
    WHERE cognito_user_id IS NULL;
```

**Key Design Decisions**:
- `cognito_user_id` is NULLABLE (allows anonymous users)
- `email` remains UNIQUE (prevents duplicate registrations)
- CHECK constraint ensures user has either Cognito account OR email
- Partial index optimizes anonymous user lookups

### Registration Table: Reference Only (No Duplication)

Following ADR-004, remove all duplicated user fields:

```sql
-- Simplified registrations table
CREATE TABLE registrations (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(id),
    registration_code VARCHAR(100) UNIQUE NOT NULL,
    attendee_username VARCHAR(100) NOT NULL,  -- Reference to user_profiles
    status VARCHAR(50) NOT NULL,
    registration_date TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(event_id, attendee_username)
);

-- attendee_username is NOT a FK (cross-service boundary)
-- Event Service calls User Management API to resolve user details
COMMENT ON COLUMN registrations.attendee_username IS
    'Reference to user_profiles.username in Company User Management Service. ' ||
    'Use UserManagementClient API to fetch full user details (email, name, company).';
```

**Removed Fields** (violate ADR-004):
- ❌ `attendee_id` (UUID) - not needed, use username
- ❌ `attendee_name` - duplicates user_profiles.first_name + last_name
- ❌ `attendee_email` - duplicates user_profiles.email

**Why No Foreign Key?**
- user_profiles in Company User Management Service database
- registrations in Event Management Service database
- Different microservices, different databases
- Use **API-based access** (follows ADR-004 evolution pattern)

### User Profile Lifecycle

```
┌─────────────────────────────────────────────────────────────┐
│ ANONYMOUS USER                                              │
│                                                             │
│ user_profiles:                                              │
│  - id: uuid                                                 │
│  - username: "john.doe.anon.abc123"                        │
│  - cognito_user_id: NULL  ← Anonymous                      │
│  - email: "john.doe@example.com"                           │
│  - first_name: "John"                                      │
│  - last_name: "Doe"                                        │
│  - company_id: "GoogleZH"                                  │
└─────────────────────────────────────────────────────────────┘
                          │
                          │ User creates Cognito account
                          │ (same email: john.doe@example.com)
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ AUTHENTICATED USER (Auto-Linked)                           │
│                                                             │
│ user_profiles:                                              │
│  - id: uuid (SAME)                                         │
│  - username: "john.doe" ← Updated to remove .anon suffix   │
│  - cognito_user_id: "abc-123-def" ← Set on first login    │
│  - email: "john.doe@example.com" (SAME)                    │
│  - first_name: "John" (SAME)                               │
│  - last_name: "Doe" (SAME)                                 │
│  - company_id: "GoogleZH" (SAME)                           │
│                                                             │
│ ✅ All previous registrations automatically linked         │
│    (registrations.attendee_username updated)                │
└─────────────────────────────────────────────────────────────┘
```

### Cross-Service Architecture

Event Management Service communicates with User Management Service via API (reuses existing `UserManagementClient`):

```
┌─────────────────────────────────────────────────────────────┐
│ Event Management Service                                    │
│                                                             │
│  POST /api/v1/events/{eventCode}/registrations             │
│  ───────────────────────────────────────────────────────   │
│  RegistrationService:                                       │
│   1. Validate event exists                                 │
│   2. Call UserManagementClient.findOrCreateAnonymous()     │
│   3. Create registration (attendee_username = user.username)│
│   4. Send confirmation email                               │
│                                                             │
│  GET /api/v1/events/{eventCode}/registrations/{regCode}    │
│  ───────────────────────────────────────────────────────   │
│  RegistrationController:                                    │
│   1. Fetch registration by registration_code               │
│   2. Call UserManagementClient.getUserByUsername()         │
│   3. Enrich response with user details (email, name)       │
│   4. Return RegistrationDTO                                │
└─────────────────┬───────────────────────────────────────────┘
                  │
                  │ HTTP REST API
                  │ (existing UserManagementClient)
                  ▼
┌─────────────────────────────────────────────────────────────┐
│ Company User Management Service                             │
│                                                             │
│  POST /api/v1/users/anonymous                              │
│  ───────────────────────────────────────────────────────   │
│  UserService:                                               │
│   1. Check if email already exists                         │
│   2. If exists: return existing user                       │
│   3. If new: create user_profile (cognito_id=NULL)         │
│   4. Generate username: "firstname.lastname" or unique     │
│   5. Return UserProfileDTO                                 │
│                                                             │
│  POST /api/v1/users/{username}/link-account                │
│  ───────────────────────────────────────────────────────   │
│  UserService:                                               │
│   1. Find user_profile by username where cognito_id=NULL  │
│   2. Validate email matches Cognito account                │
│   3. Set cognito_user_id                                   │
│   4. Update username (remove .anon suffix if present)      │
│   5. Trigger account linked event                          │
└─────────────────────────────────────────────────────────────┘
```

### Anonymous Registration Flow (Story 4.1.5)

```
┌─────────────┐
│ Public User │
└──────┬──────┘
       │
       │ 1. Fill registration form
       │    (firstName, lastName, email, company, role)
       ▼
┌──────────────────────────────────────────────────────────┐
│ POST /api/v1/events/BATbern-142/registrations           │
│                                                          │
│ {                                                        │
│   "firstName": "John",                                  │
│   "lastName": "Doe",                                    │
│   "email": "john.doe@example.com",                      │
│   "company": "GoogleZH",                                │
│   "role": "Software Architect"                          │
│ }                                                        │
└───────────────────────┬──────────────────────────────────┘
                        │
                        │ 2. Event Service receives request
                        ▼
┌──────────────────────────────────────────────────────────┐
│ RegistrationService (Event Management Service)          │
│                                                          │
│  // Call User Management API                            │
│  UserProfileDTO user = userApiClient.findOrCreateAnonymous(│
│      firstName, lastName, email, company                │
│  );                                                      │
│                                                          │
│  // Create registration                                 │
│  Registration registration = Registration.builder()     │
│      .eventId(eventId)                                  │
│      .attendeeUsername(user.getUsername())              │
│      .registrationCode(generateCode())                  │
│      .status("CONFIRMED")                               │
│      .build();                                           │
└───────────────────────┬──────────────────────────────────┘
                        │
                        │ 3. Return confirmation
                        ▼
┌──────────────────────────────────────────────────────────┐
│ Response                                                 │
│ {                                                        │
│   "registrationCode": "BATbern-142-reg-abc123",         │
│   "attendeeUsername": "john.doe",                       │
│   "status": "CONFIRMED",                                │
│   "qrCode": "data:image/png;base64,...",                │
│   "calendarUrl": "/events/BATbern-142/calendar.ics?..."│
│ }                                                        │
└──────────────────────────────────────────────────────────┘
```

### Account Linking Flow (Story 4.1.6+)

**Trigger**: User clicks "Create Account to Manage Registrations" on confirmation page

```
┌─────────────┐
│ Public User │
└──────┬──────┘
       │
       │ 1. Click "Create Account"
       ▼
┌──────────────────────────────────────────────────────────┐
│ Redirect to /signup?email=john.doe@example.com          │
│ (Email pre-filled from registration)                    │
└───────────────────────┬──────────────────────────────────┘
                        │
                        │ 2. User completes signup
                        │    (password, confirm password)
                        ▼
┌──────────────────────────────────────────────────────────┐
│ Cognito User Pool                                        │
│  - Creates Cognito account                              │
│  - Email: john.doe@example.com                          │
│  - Cognito User ID: abc-123-def                         │
└───────────────────────┬──────────────────────────────────┘
                        │
                        │ 3. First login triggers post-auth Lambda
                        ▼
┌──────────────────────────────────────────────────────────┐
│ Post-Authentication Lambda Trigger                      │
│                                                          │
│  // Check if anonymous user_profile exists              │
│  UserProfile profile = userRepo.findByEmailAndCognitoNull(│
│      cognitoEvent.getEmail()                            │
│  );                                                      │
│                                                          │
│  if (profile != null) {                                 │
│      // Auto-link                                       │
│      profile.setCognitoUserId(cognitoEvent.getUserId());│
│      profile.setUsername(generateUsername(email));      │
│      userRepo.save(profile);                            │
│                                                          │
│      // Audit log                                       │
│      log.info("Linked anonymous profile {} to Cognito",│
│          profile.getId());                              │
│  }                                                       │
└───────────────────────┬──────────────────────────────────┘
                        │
                        │ 4. User now authenticated
                        ▼
┌──────────────────────────────────────────────────────────┐
│ User Dashboard                                           │
│  ✅ All previous anonymous registrations visible        │
│  ✅ Can manage profile                                  │
│  ✅ Receive personalized updates                        │
└──────────────────────────────────────────────────────────┘
```

### API Endpoints

Following ADR-003 (meaningful identifiers):

**Event Registration (Public Access)**:
```http
POST   /api/v1/events/{eventCode}/registrations
GET    /api/v1/events/{eventCode}/registrations/{registrationCode}
PATCH  /api/v1/events/{eventCode}/registrations/{registrationCode}
DELETE /api/v1/events/{eventCode}/registrations/{registrationCode}
GET    /api/v1/events/{eventCode}/registrations/{registrationCode}/qr
GET    /api/v1/events/{eventCode}/calendar.ics?registrationId={id}
```

**User Management (Internal/Private)**:
```http
POST /api/v1/users/anonymous          # Create anonymous user_profile
GET  /api/v1/users/{username}          # Fetch user profile
GET  /api/v1/users?usernames={list}    # Batch fetch (performance)
POST /api/v1/users/{username}/link-account  # Link to Cognito (future)
```

### Security Configuration

Registration endpoints are **public** (no authentication required):

```java
// SecurityConfig.java (API Gateway)
http
    .authorizeHttpRequests(auth -> auth
        .requestMatchers(POST, "/api/v1/events/{eventCode}/registrations").permitAll()
        .requestMatchers(GET, "/api/v1/events/{eventCode}/registrations/{regCode}").permitAll()
        .requestMatchers(GET, "/api/v1/events/{eventCode}/registrations/{regCode}/qr").permitAll()
        .requestMatchers(GET, "/api/v1/events/{eventCode}/calendar.ics").permitAll()
        .anyRequest().authenticated()
    );
```

**Security Notes**:
- Anonymous users can only view registrations with confirmation code (acts as secret)
- No authentication required for registration creation (conversion optimization)
- Confirmation code is sufficiently long/random to prevent guessing
- Rate limiting applied to prevent spam registrations

### Username Generation for Anonymous Users

**Pattern**: `firstname.lastname` or `firstname.lastname.N` if collision

```java
public String generateUsername(String firstName, String lastName, String email) {
    String baseUsername = String.format("%s.%s",
        firstName.toLowerCase().replaceAll("[^a-z0-9]", ""),
        lastName.toLowerCase().replaceAll("[^a-z0-9]", "")
    );

    String username = baseUsername;
    int counter = 1;

    while (userRepository.existsByUsername(username)) {
        username = baseUsername + "." + counter++;
    }

    return username;
}
```

**Examples**:
- `john.doe` (first registration)
- `john.doe.2` (second John Doe)
- `john.doe.3` (third John Doe)

**On Account Linking**:
- Anonymous username may have `.anon.{uuid}` suffix
- On Cognito linking, update to clean username: `john.doe`
- Update all registrations to use new username

## Consequences

### Positive

1. **Frictionless Registration**:
   - ✅ Users can register without creating account
   - ✅ Optimizes conversion funnel (fewer steps to registration)
   - ✅ Matches industry best practices

2. **Single Source of Truth**:
   - ✅ All users (anonymous + authenticated) in user_profiles
   - ✅ No data duplication (follows ADR-004)
   - ✅ Email, name, company stored once
   - ✅ Consistent user model across platform

3. **Seamless Account Linking**:
   - ✅ Auto-link on first Cognito login (email match)
   - ✅ All registrations automatically visible after account creation
   - ✅ No manual migration required
   - ✅ User doesn't lose registration history

4. **Clean Microservices Architecture**:
   - ✅ Event Service uses existing UserManagementClient
   - ✅ Reuses infrastructure from speaker integration
   - ✅ API-based communication (service independence)
   - ✅ Clear domain boundaries

5. **Scalable**:
   - ✅ Can cache user lookups (reduce API calls)
   - ✅ Batch endpoints prevent N+1 queries
   - ✅ Partial index optimizes anonymous user queries

6. **Future-Proof**:
   - ✅ Anonymous users can receive newsletters (have email)
   - ✅ Can track preferences even before account creation
   - ✅ Supports GDPR consent tracking for anonymous users

### Negative

1. **Cross-Service Dependency**:
   - ⚠️ Event Service requires User Management Service availability
   - ⚠️ Network latency for API calls (mitigated by caching)
   - **Mitigation**: Use existing UserManagementClient with 15-min cache (ADR-004)

2. **Database Migration Required**:
   - ⚠️ Must make cognito_user_id NULLABLE
   - ⚠️ Must remove duplicated fields from registrations
   - **Mitigation**: Migrations created in Story 4.1.5a, applied in Story 4.1.5

3. **Account Linking Complexity**:
   - ⚠️ Requires Cognito post-authentication Lambda trigger
   - ⚠️ Must handle edge cases (email mismatch, multiple anonymous profiles)
   - **Mitigation**: Email is UNIQUE, auto-link is one-time operation

4. **Username Collision Handling**:
   - ⚠️ Multiple "John Doe" requires suffix (john.doe.2, john.doe.3)
   - ⚠️ Usernames may look less clean for common names
   - **Mitigation**: Suffix only for duplicates, username can be changed after linking

5. **No Graceful Degradation**:
   - ⚠️ If User Management Service is down, registrations fail
   - **Mitigation**: Monitor service health, implement circuit breaker (future)

### Technical Debt

**None Created**: This architecture follows established patterns:
- ADR-003: Meaningful identifiers (eventCode, registrationCode)
- ADR-004: No duplication of user fields
- Existing UserManagementClient infrastructure reused

## Implementation Guidelines

### Phase 1: Database Migrations (Story 4.1.5a)

**Migration V9 - Shared Kernel (user_profiles)**:
```sql
-- V9__Make_cognito_id_nullable_for_anonymous_users.sql
ALTER TABLE user_profiles
    ALTER COLUMN cognito_user_id DROP NOT NULL;

ALTER TABLE user_profiles
    ADD CONSTRAINT check_user_identity
    CHECK ((cognito_user_id IS NOT NULL) OR (email IS NOT NULL));

CREATE INDEX idx_user_profiles_email_anonymous
    ON user_profiles(email) WHERE cognito_user_id IS NULL;

COMMENT ON COLUMN user_profiles.cognito_user_id IS
    'Cognito User ID from AWS Cognito User Pool. NULL for anonymous users who registered without creating an account.';
```

**Migration V9 - Event Management (registrations)**:
```sql
-- V9__Remove_denormalized_attendee_data.sql
ALTER TABLE registrations DROP COLUMN IF EXISTS attendee_name;
ALTER TABLE registrations DROP COLUMN IF EXISTS attendee_email;
ALTER TABLE registrations DROP COLUMN IF EXISTS attendee_id;

COMMENT ON COLUMN registrations.attendee_username IS
    'Reference to user_profiles.username in Company User Management Service. ' ||
    'Use UserManagementClient API to fetch full user details (email, name, company).';
```

### Phase 2: User Management Service API (Story 4.1.5a/4.1.5)

**New Endpoint: Create Anonymous User**:
```java
@PostMapping("/users/anonymous")
public UserProfileDTO createAnonymousUser(@RequestBody CreateAnonymousUserRequest request) {
    // Check if email already exists
    Optional<UserProfile> existing = userRepository.findByEmail(request.getEmail());
    if (existing.isPresent()) {
        return userMapper.toDTO(existing.get());
    }

    // Generate username
    String username = generateUsername(
        request.getFirstName(),
        request.getLastName(),
        request.getEmail()
    );

    // Create user_profile with cognito_id=NULL
    UserProfile profile = UserProfile.builder()
        .username(username)
        .cognitoUserId(null)  // Anonymous
        .email(request.getEmail())
        .firstName(request.getFirstName())
        .lastName(request.getLastName())
        .companyId(request.getCompanyId())
        .build();

    UserProfile saved = userRepository.save(profile);
    return userMapper.toDTO(saved);
}
```

### Phase 3: Event Management Service (Story 4.1.5)

**Reuse Existing UserManagementClient**:
```java
@Service
@RequiredArgsConstructor
public class RegistrationService {
    private final UserManagementClient userApiClient;  // ✅ Reuse existing client
    private final RegistrationRepository registrationRepository;
    private final EventRepository eventRepository;

    public RegistrationDTO createRegistration(String eventCode, CreateRegistrationRequest request) {
        // 1. Validate event exists
        Event event = eventRepository.findByEventCode(eventCode)
            .orElseThrow(() -> new EventNotFoundException(eventCode));

        // 2. Create or find anonymous user
        UserProfileDTO user = userApiClient.findOrCreateAnonymous(
            request.getFirstName(),
            request.getLastName(),
            request.getEmail(),
            request.getCompany()
        );

        // 3. Generate registration code
        String registrationCode = eventCode + "-reg-" + UUID.randomUUID().toString().substring(0, 8);

        // 4. Create registration (only attendee_username, no duplication)
        Registration registration = Registration.builder()
            .eventId(event.getId())
            .attendeeUsername(user.getUsername())
            .registrationCode(registrationCode)
            .status("CONFIRMED")
            .build();

        Registration saved = registrationRepository.save(registration);

        // 5. Enrich with user data for response
        return enrichRegistrationDTO(saved, user);
    }

    public RegistrationDTO getRegistration(String eventCode, String registrationCode) {
        // 1. Fetch registration
        Registration registration = registrationRepository
            .findByRegistrationCode(registrationCode)
            .orElseThrow(() -> new RegistrationNotFoundException(registrationCode));

        // 2. Fetch user details via API
        UserProfileDTO user = userApiClient.getUserByUsername(registration.getAttendeeUsername());

        // 3. Return enriched DTO
        return enrichRegistrationDTO(registration, user);
    }

    private RegistrationDTO enrichRegistrationDTO(Registration reg, UserProfileDTO user) {
        return RegistrationDTO.builder()
            .registrationCode(reg.getRegistrationCode())
            .attendeeUsername(reg.getAttendeeUsername())
            .attendeeEmail(user.getEmail())         // From User API
            .attendeeName(user.getFullName())       // From User API
            .company(user.getCompany())             // From User API
            .status(reg.getStatus())
            .registrationDate(reg.getRegistrationDate())
            .build();
    }
}
```

### Phase 4: Account Linking (Future Story)

**Cognito Post-Authentication Lambda Trigger**:
```javascript
// post-authentication-trigger.js
exports.handler = async (event) => {
    const email = event.request.userAttributes.email;
    const cognitoUserId = event.request.userAttributes.sub;

    // Call User Management Service API to link account
    await fetch(`${USER_SERVICE_URL}/api/v1/users/link-account`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, cognitoUserId })
    });

    return event;
};
```

**Link Account Endpoint**:
```java
@PostMapping("/users/link-account")
public UserProfileDTO linkAccount(@RequestBody LinkAccountRequest request) {
    // Find anonymous user_profile by email
    UserProfile profile = userRepository
        .findByEmailAndCognitoUserIdIsNull(request.getEmail())
        .orElseThrow(() -> new UserNotFoundException(request.getEmail()));

    // Set Cognito user ID
    profile.setCognitoUserId(request.getCognitoUserId());

    // Update username (remove .anon suffix if present)
    String cleanUsername = generateUsername(
        profile.getFirstName(),
        profile.getLastName(),
        profile.getEmail()
    );
    profile.setUsername(cleanUsername);

    UserProfile linked = userRepository.save(profile);

    // Publish domain event
    eventPublisher.publish(new AccountLinkedEvent(linked.getId(), request.getCognitoUserId()));

    return userMapper.toDTO(linked);
}
```

## Related Documents

- **ADR-003**: Meaningful Identifiers in Public APIs (eventCode, registrationCode)
- **ADR-004**: Factor User Fields from Domain Entities (no duplication principle)
- **Architecture**:
  - `docs/architecture/03-data-architecture.md` (updated in Story 4.1.5a)
  - `docs/architecture/04-api-event-management.md` (updated in Story 4.1.5a)
- **OpenAPI Specs**:
  - `docs/api/events-api.openapi.yml` (updated in Story 4.1.5a)
  - `docs/api/user-management-api.openapi.yml` (created in Story 4.1.5a)
- **Stories**:
  - `docs/stories/4.1.5a.architecture-consolidation-anonymous-registration.md` (this ADR created here)
  - `docs/stories/4.1.5.registration-wizard.md` (corrected in Story 4.1.5a)
  - `docs/stories/4.1.6.registration-confirmation.md` (corrected in Story 4.1.5a)

## Implementation Status

### Current Phase: Documentation (Story 4.1.5a)

- [x] ADR-005 created (this document)
- [ ] Data architecture doc updated
- [ ] API architecture docs updated
- [ ] OpenAPI specs updated
- [ ] Database migrations created (not applied)

### Future Phase: Code Implementation (Story 4.1.5)

- [ ] Apply database migrations
- [ ] User Management Service: anonymous user endpoints
- [ ] Event Management Service: registration flow using UserManagementClient
- [ ] Security configuration: permit registration endpoints
- [ ] Frontend: registration wizard (2-step flow)
- [ ] Integration tests with PostgreSQL
- [ ] Bruno E2E tests for anonymous registration

### Future Phase: Account Linking (Story TBD)

- [ ] Cognito post-authentication Lambda trigger
- [ ] Link account endpoint
- [ ] Frontend: "Create Account" CTA and flow
- [ ] Tests for account linking scenarios

## Revision History

| Date | Version | Changes | Author |
|------|---------|---------|--------|
| 2025-11-08 | 1.0 | Initial ADR creation for anonymous registration | Winston (Architect Agent) |

---

**This ADR establishes the foundational architecture for anonymous event registration with seamless account linking, following ADR-003 (meaningful identifiers) and ADR-004 (no user field duplication) principles.**
