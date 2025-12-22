# ADR-007: Unified User Profile for Anonymous and Authenticated Users

**Status**: Accepted
**Date**: 2025-11-08
**Decision Makers**: Development Team

## Context

BATbern needs to support anonymous event registration (users can register for events without creating a Cognito account). This ADR documents how anonymous users are modeled in the database.

## Decision

**Use a unified `user_profiles` table for BOTH anonymous and authenticated users**, distinguishing them via a nullable `cognito_user_id` field.

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
  attendee_username VARCHAR(100) NOT NULL -- References user_profiles.username (cross-service)
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
5. **Cross-Service Reference**: `registrations.attendee_username` references user (ADR-004 pattern)

## Rationale

### 1. Simpler Account Linking (3 lines vs 20+)

```java
// On account creation, just set the cognito_user_id
UserProfile profile = userProfileRepo.findByEmail(email);
profile.setCognitoUserId(cognitoUserId);
profile.setClaimedAt(Instant.now());
userProfileRepo.save(profile);
// All past registrations automatically linked via attendee_username
```

### 2. Company Analytics with Referential Integrity

```sql
-- Clean company analysis (no string matching)
SELECT c.name, COUNT(*) AS registrations
FROM registrations r
JOIN user_profiles u ON r.attendee_username = u.username
JOIN companies c ON u.company_id = c.id
GROUP BY c.name;
```

### 3. Single Source of Truth

- User data in ONE place (`user_profiles`)
- GDPR deletion is single DELETE
- Profile updates propagate to all registrations automatically

## Implementation

### Migrations

**V4**: Create `user_profiles` table with NOT NULL `cognito_user_id`
**V11**: Make `cognito_user_id` nullable for anonymous users

```sql
-- V11__Make_cognito_id_nullable_for_anonymous_users.sql
ALTER TABLE user_profiles ALTER COLUMN cognito_user_id DROP NOT NULL;

-- Ensure must have either cognito_id OR email
ALTER TABLE user_profiles ADD CONSTRAINT check_user_identity
    CHECK ((cognito_user_id IS NOT NULL) OR (email IS NOT NULL));

-- Optimize anonymous user lookups
CREATE INDEX idx_user_profiles_email_anonymous
    ON user_profiles(email) WHERE cognito_user_id IS NULL;
```

### Anonymous Registration Flow

```java
// 1. Find or create user_profile (anonymous)
UserProfile profile = userProfileService.findOrCreateAnonymous(
    request.getEmail(),
    request.getFirstName(),
    request.getLastName(),
    request.getCompany()
);

// 2. Create event_registration
EventRegistration registration = new EventRegistration();
registration.setEventId(event.getId());
registration.setAttendeeUsername(profile.getUsername());  // Cross-service reference
registration.setConfirmationCode(generateConfirmationCode());
registration.setStatus("confirmed");
registrationRepo.save(registration);

// 3. Send confirmation email with QR code
emailService.sendRegistrationConfirmation(profile.getEmail(), registration.getConfirmationCode());
```

### Account Claiming Flow

```java
@EventListener
public void onUserCreated(UserCreatedEvent event) {
    // Find existing anonymous profile with this email
    Optional<UserProfile> existingProfile = userProfileRepo
        .findByEmailAndCognitoUserIdIsNull(event.getEmail());

    if (existingProfile.isPresent()) {
        // Claim existing profile - all registrations automatically linked
        UserProfile profile = existingProfile.get();
        profile.setCognitoUserId(event.getCognitoUserId());
        profile.setClaimedAt(Instant.now());
        userProfileRepo.save(profile);
    } else {
        // Create new profile for authenticated user
        UserProfile newProfile = createAuthenticatedUserProfile(event);
        userProfileRepo.save(newProfile);
    }
}
```

## Consequences

### Positive

1. **Simple Implementation**: Account linking is 3 lines of code
2. **Better Analytics**: Company analysis uses FK joins
3. **Referential Integrity**: Company as FK ensures data quality
4. **Single Source of Truth**: All user data in `user_profiles`
5. **Cross-Service Pattern**: Follows ADR-004 (no duplicated user fields in registrations)

### Negative

1. **User Table Growth**: `user_profiles` contains anonymous users who may never create accounts
   - **Mitigation**: Acceptable for MVP. Can archive old anonymous users after events end.

2. **Username Conflicts**: Must handle `john.doe`, `john.doe.2`, etc.
   - **Mitigation**: Implemented in username generation service

## Related Documents

- **ADR-003**: Meaningful Identifiers (username instead of UUID in APIs)
- **ADR-004**: Factor User Fields from Domain Entities (registrations reference username only)
- **Data Architecture**: `docs/architecture/03-data-architecture.md`
- **Migrations**: `services/company-user-management-service/src/main/resources/db/migration/`

---

**This ADR documents the foundational decision for anonymous event registration in the BATbern platform.**
