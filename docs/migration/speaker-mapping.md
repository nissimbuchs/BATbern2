# Speaker Mapping Documentation

**Story**: 3.1.2 - Domain Mapping & Schema Transformation Design
**Generated**: 2025-11-19
**Author**: Dev Agent (James)

## Overview

This document specifies the mapping rules for transforming legacy speaker data from `sessions.json` to User and Speaker entities. This mapping follows ADR-004 (Reference Patterns) where bio and profile picture are stored in User, NOT in Speaker.

**CRITICAL DEPENDENCY**: Company entities from Task 1 MUST exist before creating User entities. User.companyId references Company.name.

## Source Data

**File**: `apps/BATspa-old/src/api/sessions.json`
**Format**: Array of session objects, each with optional `referenten[]` array
**Total Sessions**: 302 sessions
**Unique Speakers**: 269 speakers (after deduplication)
**Speaker Data Coverage**:
- 267 speakers with bios (99%)
- 223 speakers with portraits (83%)

## Target Schema

### User Entity (Company User Management Service)

**Table**: `user_profiles`
**Schema Source**: `services/company-user-management-service/src/main/resources/db/migration/V4__Create_user_profiles_table.sql`

```sql
CREATE TABLE user_profiles (
  id UUID PRIMARY KEY,                      -- Internal database key
  username VARCHAR(100) NOT NULL UNIQUE,    -- Public API identifier
  first_name VARCHAR(100),
  last_name VARCHAR(100),
  email VARCHAR(255),
  bio TEXT,                                 -- Single source of truth (ADR-004)
  company_id VARCHAR(12),                   -- FK to companies.name (NOT UUID)
  profile_picture_url VARCHAR(2048),        -- CloudFront CDN URL
  profile_picture_s3_key VARCHAR(500),      -- S3 storage key
  profile_picture_file_id VARCHAR(100),     -- File identifier (UUID)
  CONSTRAINT chk_company_id_format CHECK (company_id ~ '^[a-zA-Z0-9]{1,12}$')
);
```

### Speaker Entity (Speaker Coordination Service)

**Table**: `speakers`
**Schema Source**: `services/speaker-coordination-service` (ADR-004 pattern)

```sql
CREATE TABLE speakers (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL,                    -- FK to user_profiles.id (cross-service)
  availability VARCHAR(50),                 -- 'available', 'limited', 'unavailable'
  workflow_state VARCHAR(50),               -- 'open', 'in_review', 'approved', 'archived'
  expertise_areas JSONB,                    -- Array of expertise topics
  speaking_topics JSONB                     -- Array of speaking topics
  -- NOTE: bio, profile_picture_url, company_id stored in User (NOT here)
);
```

## Mapping Rules

### 1. Speaker Extraction and Deduplication

**Source**: `sessions.json` → `referenten[]` arrays across all sessions
**Strategy**: Extract unique speakers based on name

**Process**:
1. Iterate through all 302 sessions
2. For each session with `referenten` array, extract speakers
3. Deduplicate by speaker name (assume name uniquely identifies speaker)
4. Result: 269 unique speakers

**Example**:
```json
// Session 1
{
  "referenten": [
    { "name": "Thomas Goetz, Die Mobiliar", "bio": "...", "company": "mobiliar", "portrait": "thomas.jpg" }
  ]
}

// Session 2 (same speaker)
{
  "referenten": [
    { "name": "Thomas Goetz, Die Mobiliar", "bio": "...", "company": "mobiliar", "portrait": "thomas.jpg" }
  ]
}

// Result: Only 1 User and 1 Speaker entity created for Thomas Goetz
```

### 2. Name Parsing

**Source**: `referenten[].name` field
**Format**: `"FirstName LastName, Company"`
**Target**: `User.firstName`, `User.lastName`

**Parsing Rules**:
- Split by comma to separate name from company
- Split name by space
- First word = firstName
- Remaining words = lastName (joined)

**Examples**:

| Legacy Name | firstName | lastName | companyDisplayName |
|-------------|-----------|----------|-------------------|
| `"Thomas Goetz, Die Mobiliar"` | `"Thomas"` | `"Goetz"` | `"Die Mobiliar"` |
| `"Jean-Pierre Dubois, Swisscom"` | `"Jean-Pierre"` | `"Dubois"` | `"Swisscom"` |
| `"Anna von Mueller, SBB"` | `"Anna"` | `"von Mueller"` | `"SBB"` |
| `"Thomas Goetz"` | `"Thomas"` | `"Goetz"` | `undefined` |

### 3. Username Generation

**Target**: `User.username` (VARCHAR(100) NOT NULL UNIQUE)
**Pattern**: `firstname.lastname` (lowercase, alphanumeric + dot)

**Generation Rules**:
1. Normalize first name: lowercase, remove accents/special chars
2. Normalize last name: same normalization
3. Combine with dot: `{first}.{last}`
4. Truncate to 100 chars if needed

**Normalization**:
- Convert to lowercase
- Decompose accented characters (NFD normalization)
- Remove diacritics (ä → a, ü → u, etc.)
- Remove all non-alphanumeric characters
- Keep only [a-z0-9]

**Examples**:

| Name | Username |
|------|----------|
| Thomas Goetz | `thomas.goetz` |
| Jean-Pierre Müller | `jeanpierre.muller` |
| Anna von Mueller | `anna.vonmueller` |

**Duplicate Handling**: If username collision occurs during migration, append number: `thomas.goetz2`, `thomas.goetz3`

### 4. Bio Mapping (ADR-004)

**Source**: `referenten[].bio` field
**Target**: `User.bio` (TEXT), NOT `Speaker.bio`

**Rule**: Bio is a user attribute, not domain-specific. Store in User entity per ADR-004.

**Rationale**: A user's biography is part of their profile, not specific to their speaker role.

**Example**:
```typescript
// ✅ Correct
const user: User = {
  ...
  bio: "Experienced software architect with 15 years in cloud computing."
};

const speaker: Speaker = {
  userId: user.id
  // bio NOT included here
};

// ❌ Wrong
const speaker: Speaker = {
  userId: user.id,
  bio: "..." // Don't duplicate bio in Speaker
};
```

### 5. Company ID Mapping (Story 1.16.2 + FK Constraint)

**Source**: `referenten[].company` field
**Target**: `User.companyId` (VARCHAR(12) FK to companies.name)

**Constraint**: `CHECK (company_id ~ '^[a-zA-Z0-9]{1,12}$')`

**Rule**: User.companyId references Company.name (meaningful ID, NOT UUID)

**Validation**: Before creating User, verify Company with given name exists (from Task 1)

**Examples**:
- `company: "mobiliar"` → `User.companyId = "mobiliar"` ✅ (8 chars, exists)
- `company: "sbb"` → `User.companyId = "sbb"` ✅ (3 chars, exists)
- `company: "nonexistent"` → ⚠️ Warning logged, use as-is or create placeholder

### 6. Profile Picture Mapping (ProfilePictureService Pattern)

**Source**: `referenten[].portrait` field (filename)
**Target**:
- `User.profilePictureS3Key` (VARCHAR(500))
- `User.profilePictureUrl` (VARCHAR(2048))
- `User.profilePictureFileId` (VARCHAR(100))

**S3 Key Pattern**: `profile-pictures/{year}/{username}/profile-{fileId}.{ext}`
**Reference**: ProfilePictureService.java line 161

**Generation Process**:
1. Generate unique file ID (UUID)
2. Extract file extension from original filename
3. Generate S3 key using pattern
4. Generate CloudFront URL from S3 key

**Example**:
```typescript
// Input
{
  portrait: "thomas.goetz.jpg",
  name: "Thomas Goetz, Die Mobiliar"
}

// Generated
{
  username: "thomas.goetz",
  profilePictureFileId: "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  profilePictureS3Key: "profile-pictures/2025/thomas.goetz/profile-a1b2c3d4-e5f6-7890-abcd-ef1234567890.jpg",
  profilePictureUrl: "https://cdn.batbern.ch/profile-pictures/2025/thomas.goetz/profile-a1b2c3d4-e5f6-7890-abcd-ef1234567890.jpg"
}
```

### 7. Speaker Entity Creation (ADR-004)

**Target**: Speaker entity with userId FK

**Default Values** for Historical Speakers:
- `availability` = `'available'` - Historical speakers assumed available
- `workflowState` = `'open'` - Default state for migrated speakers
- `expertiseAreas` = `[]` - Empty array, can be backfilled
- `speakingTopics` = `[]` - Empty array, can be backfilled

**Relationship**: `Speaker.userId` → `User.id` (UUID FK, cross-service)

**Example**:
```typescript
const speaker: Speaker = {
  id: "b2c3d4e5-f6a7-8901-bcde-f12345678901",
  userId: "a1b2c3d4-e5f6-7890-abcd-ef1234567890", // FK to User.id
  availability: "available",
  workflowState: "open",
  expertiseAreas: [],
  speakingTopics: []
};
```

## Schema Compliance

### Constraints Validated

✅ **User.username VARCHAR(100) UNIQUE**: Max 100 chars, unique
✅ **User.companyId VARCHAR(12)**: Max 12 chars, alphanumeric, FK to Company.name
✅ **User.bio TEXT**: No length limit (TEXT type)
✅ **Speaker.userId UUID**: FK to user_profiles.id (cross-service)
✅ **ADR-004**: bio, profilePictureUrl NOT duplicated in Speaker
✅ **Story 1.16.2**: username is public API identifier

### Foreign Key Relationships

1. **User → Company**: `user_profiles.company_id` → `companies.name` (VARCHAR(12), meaningful ID)
2. **Speaker → User**: `speakers.user_id` → `user_profiles.id` (UUID, cross-service)

**Migration Order**:
1. Create Company entities (Task 1) ✅
2. Create User entities (this task) ← **MUST happen after Task 1**
3. Create Speaker entities (this task)
4. Create SessionUser junctions (requires both User and Session)

## Implementation

**Module**: `apps/migration-analysis/src/mappers/speaker-mapper.ts`
**Tests**: `apps/migration-analysis/src/__tests__/speaker-mapper.test.ts`
**Test Results**: 24 tests passing ✅

### Key Functions

- `extractUniqueSpeakers(sessions)`: Extract 269 unique speakers from 302 sessions
- `parseSpeakerName(name)`: Parse "FirstName LastName, Company" format
- `generateUsername(firstName, lastName)`: Generate username with normalization
- `mapSpeakerToUser(legacySpeaker, companies)`: Map to User entity (includes bio, portrait)
- `mapSpeakerToSpeaker(userId)`: Map to Speaker entity (domain-specific fields only)
- `validateCompanyExists(companyId, companies)`: Validate FK before creating User

## Example Mapping

**Input** (sessions.json):
```json
{
  "referenten": [
    {
      "name": "Thomas Goetz, Die Mobiliar",
      "bio": "Experienced software architect with 15 years in cloud computing.",
      "company": "mobiliar",
      "portrait": "thomas.goetz.jpg"
    }
  ]
}
```

**Output** (User + Speaker entities):
```typescript
// User entity
{
  id: "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  username: "thomas.goetz",
  firstName: "Thomas",
  lastName: "Goetz",
  bio: "Experienced software architect with 15 years in cloud computing.",
  companyId: "mobiliar", // FK to Company.name
  profilePictureUrl: "https://cdn.batbern.ch/profile-pictures/2025/thomas.goetz/profile-{uuid}.jpg",
  profilePictureS3Key: "profile-pictures/2025/thomas.goetz/profile-{uuid}.jpg",
  profilePictureFileId: "{uuid}"
}

// Speaker entity
{
  id: "b2c3d4e5-f6a7-8901-bcde-f12345678901",
  userId: "a1b2c3d4-e5f6-7890-abcd-ef1234567890", // FK to User.id
  availability: "available",
  workflowState: "open",
  expertiseAreas: [],
  speakingTopics: []
}
```

## Edge Cases Handled

1. **Speakers without portraits**: profilePictureUrl/S3Key/FileId = undefined
2. **Speakers without company**: companyId may be null (if schema allows)
3. **Duplicate speaker names**: Deduplicated, only one User/Speaker created
4. **Special characters in names**: Normalized to alphanumeric in username
5. **Multi-word names**: Properly handled (Jean-Pierre, von Mueller)
6. **Company not found**: Warning logged, User created with original companyId

## References

- **ADR-004**: Reference pattern (no field duplication between User and Speaker)
- **Story 1.16.2**: Meaningful IDs (username as public identifier)
- **ProfilePictureService**: S3 key pattern (ProfilePictureService.java:161)
- **Flyway Migrations**: V4__Create_user_profiles_table.sql (source of truth)
