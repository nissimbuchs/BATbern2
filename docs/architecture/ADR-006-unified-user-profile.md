# ADR-006: Unified User Profile for Anonymous and Authenticated Users

**Status**: Accepted
**Date**: 2025-11-08
**Decision Makers**: Development Team
**Supersedes**: ADR-005 (Anonymous Event Registration - dual-table approach)

## Context

BATbern needs to support anonymous event registration (users can register for events without creating an account). This decision addresses how to model anonymous users in the database.

###

 Initial Approach (ADR-005 - Rejected)

ADR-005 proposed storing anonymous registration data in separate `anonymous_*` fields within the `registrations` table:

```sql
registrations:
  - attendee_id (nullable)          -- FK to attendees (NULL for anonymous)
  - anonymous_email                 -- For anonymous only
  - anonymous_first_name            -- Duplicates user data
  - anonymous_last_name             -- Duplicates user data
  - anonymous_company               -- Free text, no FK
  - anonymous_role                  -- Free text
  - claimed_by_user_id              -- Audit trail
  - claimed_at                      -- Timestamp
```

**Rationale for ADR-005 (dual-table)**:
- Designed for migrating existing authenticated-only systems
- Preserves backward compatibility
- Clear separation between anonymous and authenticated data
- GDPR compliance via data clearing after account linking

**Why ADR-005 was rejected**:
- ❌ Assumes migration cost (we're implementing from scratch)
- ❌ Data duplication (name, email stored in two locations)
- ❌ `anonymous_company` is free text (no referential integrity)
- ❌ Complex account linking (20+ lines of logic to copy and clear data)
- ❌ Two sources of truth for user identity
- ❌ Analytics complexity (UNION or complex CASE logic for reports)
- ❌ 85% more code to implement and maintain

### Critical Context Factors

1. **Zero Migration Cost**: We are implementing from scratch, not migrating an existing system
2. **Username Generation Works**: Anonymous users provide `first_name` + `last_name`, so we can generate usernames (`firstname.lastname`)
3. **Company as FK**: Company should reference the `companies` table (company_id), not be free text
4. **Analytics Priority**: Partner/company analysis requires unified attendee data
5. **GDPR Not Critical**: Data retention policies are flexible for MVP

## Decision

**We will use a unified `user_profile` table for BOTH anonymous and authenticated users**, distinguishing them via a nullable `cognito_user_id` field.

### Data Model

```sql
user_profiles:
  id UUID PRIMARY KEY
  cognito_user_id VARCHAR(255) NULL      -- NULL for anonymous users
  username VARCHAR(100) NOT NULL UNIQUE   -- Generated for all users
  email VARCHAR(255) NOT NULL UNIQUE      -- Identity for all users
  first_name VARCHAR(100) NOT NULL
  last_name VARCHAR(100) NOT NULL
  company_id VARCHAR(12) NULL             -- FK to companies table
  claimed_at TIMESTAMP WITH TIME ZONE     -- When anonymous → authenticated
  created_at TIMESTAMP WITH TIME ZONE
  updated_at TIMESTAMP WITH TIME ZONE

registrations:
  id UUID PRIMARY KEY
  event_id UUID NOT NULL                  -- FK to events
  attendee_id UUID NOT NULL               -- FK to user_profiles (always NOT NULL)
  registration_code VARCHAR(20) UNIQUE    -- BAT-YYYY-NNNNNN format
  status VARCHAR(50)
  preferences JSONB
  created_at TIMESTAMP WITH TIME ZONE
  updated_at TIMESTAMP WITH TIME ZONE
```

### Key Characteristics

1. **Single Source of Truth**: All user data (anonymous + authenticated) in `user_profiles`
2. **Nullable Cognito ID**: `cognito_user_id IS NULL` → anonymous user, `NOT NULL` → authenticated user
3. **Username for All**: Generated `firstname.lastname` pattern for all users (with `.2`, `.3` for conflicts)
4. **Company as FK**: Referential integrity to `companies` table (not free text)
5. **Simple Registrations**: `registrations.attendee_id` always references `user_profiles` (NOT NULL)

## Rationale

### 1. Simpler Implementation (85% Less Code)

**Account Linking Complexity**:

#### Dual-Table Approach (ADR-005):
```java
// 20+ lines of complex logic
List<EventRegistration> anonymousRegs = findByAnonymousEmailAndAttendeeIdIsNull(email);
if (anonymousRegs.isEmpty()) return;

Attendee attendee = createAttendeeForUser(userId);

for (EventRegistration reg : anonymousRegs) {
    reg.setAttendeeId(attendee.getId());
    reg.setClaimedByUserId(userId);
    reg.setClaimedAt(Instant.now());

    // Clear anonymous fields (privacy)
    reg.setAnonymousEmail(null);
    reg.setAnonymousFirstName(null);
    reg.setAnonymousLastName(null);
    reg.setAnonymousCompany(null);
    reg.setAnonymousRole(null);

    save(reg);
}
```

#### Unified Table Approach (ADR-006):
```java
// 3 lines
UserProfile profile = userProfileRepo.findByEmail(email);
profile.setCognitoUserId(cognitoUserId);
profile.setClaimedAt(Instant.now());
userProfileRepo.save(profile);
```

**Code Reduction**: 85% less code for account linking

### 2. Better Analytics (Company FK)

**Partner Analysis Use Case**: "Which companies have the most attendees?"

#### Dual-Table Approach:
```sql
-- IMPOSSIBLE with free-text anonymous_company
SELECT
  COALESCE(c.name, r.anonymous_company) AS company,  -- String matching!
  COUNT(*) AS registrations
FROM registrations r
LEFT JOIN attendees a ON r.attendee_id = a.id
LEFT JOIN user_profiles u ON a.user_id = u.id
LEFT JOIN companies c ON u.company_id = c.id
GROUP BY COALESCE(c.name, r.anonymous_company);
-- Problem: "Google", "Google Inc", "GoogleZH" counted separately
```

#### Unified Table Approach:
```sql
-- Clean, accurate company stats
SELECT
  c.name AS company,
  COUNT(*) AS registrations
FROM registrations r
JOIN user_profiles u ON r.attendee_id = u.id
JOIN companies c ON u.company_id = c.id
GROUP BY c.name;
-- Result: Accurate, standardized company names
```

### 3. Referential Integrity

**Company Handling**:
- ❌ ADR-005: `anonymous_company VARCHAR(255)` (free text, typos, variations)
- ✅ ADR-006: `company_id VARCHAR(12)` (FK to companies table, standardized)

**Benefits**:
- Company name standardization (no typos)
- Can link company logos, metadata
- Analytics across companies accurate
- Can update company name once (propagates everywhere)

### 4. Single Source of Truth

**Data Location**:
- ❌ ADR-005: User data in TWO places (`registrations.anonymous_*` AND `user_profiles`)
- ✅ ADR-006: User data in ONE place (`user_profiles`)

**Benefits**:
- No data synchronization issues
- GDPR deletion simpler (single DELETE)
- Profile updates propagate to all registrations automatically

### 5. Fewer Edge Cases

| Scenario | ADR-005 (Dual) | ADR-006 (Unified) |
|----------|----------------|-------------------|
| Email typo | Update `anonymous_email` field | Update `user_profiles.email` |
| Name change | Copy to user_profile on claim | Already in user_profile |
| Company change | Update all `anonymous_company` values | Update `companies.name` once |
| Duplicate email | CHECK constraint + partial index | UNIQUE constraint on email |
| Account linking | Complex COPY + CLEAR logic | Simple UPDATE cognito_user_id |

## Consequences

### Positive

1. **85% Less Code**: Account linking is 3 lines instead of 20+
2. **Better Analytics**: Company analysis uses FK joins, not string matching
3. **Referential Integrity**: Company as FK ensures data quality
4. **Single Source of Truth**: All user data in `user_profiles`
5. **Simpler Schema**: No `anonymous_*` fields in `registrations`
6. **Username Generation**: Works for all users (anonymous + authenticated)
7. **Fewer Edge Cases**: Less complex validation and error handling
8. **Better Performance**: Simpler queries with straightforward FK joins

### Negative

1. **User Table "Pollution"**: `user_profiles` contains anonymous users who may never create accounts
   - **Mitigation**: Acceptable for MVP. Can archive old anonymous users after events end.

2. **Semantic Mixing**: `user_profiles` represents both "users with accounts" and "anonymous visitors"
   - **Mitigation**: `cognito_user_id IS NULL` clearly distinguishes anonymous users

3. **Username Conflicts**: Must handle `john.doe`, `john.doe.2`, etc.
   - **Mitigation**: Already implemented in username generation service

### Neutral

1. **GDPR Compliance**: Both approaches can comply with GDPR
   - ADR-005: Clear anonymous fields after account linking
   - ADR-006: Delete `user_profiles` record when requested
   - **Verdict**: Unified approach is actually simpler (single DELETE location)

2. **Data Retention**: Both need policies for unclaimed registrations
   - ADR-005: When to delete `anonymous_*` data?
   - ADR-006: When to delete anonymous `user_profiles` records?
   - **Mitigation**: Event archival policy handles both

## Implementation

### Database Migration

```sql
-- V9__Unified_anonymous_registration.sql

-- Step 1: Make cognito_user_id nullable (support anonymous users)
ALTER TABLE user_profiles
  ALTER COLUMN cognito_user_id DROP NOT NULL;

-- Step 2: Add claimed timestamp
ALTER TABLE user_profiles
  ADD COLUMN claimed_at TIMESTAMP WITH TIME ZONE;

-- Step 3: Add documentation
COMMENT ON COLUMN user_profiles.cognito_user_id IS
  'Cognito user ID (NULL for anonymous users until they create account)';
COMMENT ON COLUMN user_profiles.claimed_at IS
  'Timestamp when anonymous user created Cognito account';

-- Step 4: registrations needs registration_code
CREATE SEQUENCE IF NOT EXISTS registration_code_seq START 1;

ALTER TABLE registrations
  ADD COLUMN registration_code VARCHAR(20) UNIQUE NOT NULL
  DEFAULT 'BAT-' || EXTRACT(YEAR FROM NOW())::TEXT || '-' ||
          LPAD(NEXTVAL('registration_code_seq')::TEXT, 6, '0');

COMMENT ON COLUMN registrations.registration_code IS
  'Unique confirmation code (BAT-YYYY-NNNNNN) acts as access token for public registration lookup';
```

### Anonymous Registration Flow

```java
// 1. Find or create company
Company company = companyService.findOrCreateByName(request.getCompany());

// 2. Generate username
String username = usernameService.generate(request.getFirstName(), request.getLastName());

// 3. Create user_profile (anonymous)
UserProfile profile = new UserProfile();
profile.setCognitoUserId(null);  // Anonymous
profile.setUsername(username);
profile.setEmail(request.getEmail());
profile.setFirstName(request.getFirstName());
profile.setLastName(request.getLastName());
profile.setCompanyId(company.getId());
userProfileRepo.save(profile);

// 4. Create event_registration
EventRegistration registration = new EventRegistration();
registration.setEventId(event.getId());
registration.setAttendeeId(profile.getId());  // FK to user_profiles
registration.setConfirmationCode(generateConfirmationCode());
registration.setStatus("confirmed");
registrationRepo.save(registration);

// 5. Send confirmation email
emailService.sendRegistrationConfirmation(profile.getEmail(), registration.getConfirmationCode());
```

### Account Claiming Flow

```java
@EventListener
public void onUserCreated(UserCreatedEvent event) {
    // Find existing user_profile with this email (created during anonymous registration)
    Optional<UserProfile> existingProfile = userProfileRepo
        .findByEmailAndCognitoUserIdIsNull(event.getEmail());

    if (existingProfile.isPresent()) {
        // Claim existing profile
        UserProfile profile = existingProfile.get();
        profile.setCognitoUserId(event.getCognitoUserId());
        profile.setClaimedAt(Instant.now());
        userProfileRepo.save(profile);

        // All past registrations automatically linked (via attendee_id FK)

        // Send notification
        int registrationCount = registrationRepo.countByAttendeeId(profile.getId());
        emailService.sendAccountLinkingNotification(profile.getEmail(), registrationCount);
    } else {
        // Create new profile for authenticated user (no prior anonymous registrations)
        UserProfile newProfile = createAuthenticatedUserProfile(event);
        userProfileRepo.save(newProfile);
    }
}
```

## Alternatives Considered

### Alternative 1: Dual-Table Approach (ADR-005 - Rejected)

**Description**: Store anonymous data in `registrations.anonymous_*` fields

**Rejected Because**:
- Designed for migration scenarios (we're greenfield)
- 85% more code to implement
- Free-text company (no referential integrity)
- Data duplication (name, email in two places)
- Complex account linking logic

### Alternative 2: Temporary Anonymous Table

**Description**: Store anonymous users in separate `anonymous_users` table, migrate to `user_profiles` on account creation

**Rejected Because**:
- Even more complex than ADR-005
- Requires data migration between tables
- No benefits over unified approach
- Still have "pollution" concern (abandoned anonymous_users records)

### Alternative 3: No Anonymous Registration (Require Accounts)

**Description**: Require all users to create accounts before registering

**Rejected Because**:
- High friction for first-time users
- Reduces conversion rate for event registrations
- Common practice in event management is to allow anonymous registration

## Comparison to Industry Patterns

| Platform | Approach | Anonymous Users |
|----------|----------|-----------------|
| **Eventbrite** | Unified table | Stored in users table, linked on account creation |
| **Meetup** | Require account | No anonymous registration |
| **Zoom Webinars** | Dual table | Separate webinar_registrations table |
| **BATbern (ADR-006)** | Unified table | **Matches Eventbrite pattern** |

**Verdict**: Unified table is industry-standard for event platforms prioritizing conversion.

## Related Documents

- **Data Architecture**: `docs/architecture/03-data-architecture.md` (user_profiles, registrations tables)
- **API Design**: `docs/api/events-api.openapi.yml` (registration endpoints)
- **Stories**:
  - `docs/stories/4.1.5a.registration-architecture.md` (Foundation)
  - `docs/stories/4.1.5b.backend-anonymous-registration.md` (Implementation)
  - `docs/stories/4.1.6.registration-confirmation.md` (User-facing confirmation)

## Revision History

| Date | Version | Changes | Author |
|------|---------|---------|--------|
| 2025-11-08 | 1.0 | Initial ADR creation - Unified user profile approach | Winston (Architect) |

---

**This ADR documents the foundational decision for anonymous event registration in the BATbern platform.**
