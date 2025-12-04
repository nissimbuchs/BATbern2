# Schema Mapping Specification

**Story**: 3.1.2 - Domain Mapping & Schema Transformation Design
**Generated**: 2025-11-19
**Author**: Dev Agent (James)
**Version**: 1.0

## Executive Summary

This document provides a comprehensive specification for mapping legacy BATbern Angular application data to the new microservices architecture. It consolidates all mapping rules, transformation logic, and validation requirements.

**Migration Scope**:
- 70 companies (65 after deduplication)
- 60 events (BAT 1-60)
- 302 sessions/presentations
- 269 unique speakers
- 258 PDF documents
- 578 images
- Total storage: 596.9 MB

**Test Coverage**: 97 tests passing across all mappers ✅

## Document Structure

This specification is organized into five mapping domains, each with detailed documentation:

1. **[Company Mapping](./company-mapping.md)** - Company entities and logos
2. **[Event Mapping](./event-mapping.md)** - Events with German date parsing
3. **[Speaker Mapping](./speaker-mapping.md)** - User and Speaker entities
4. **[File Mapping](./s3-structure.md)** - S3 bucket structure and CDN URLs
5. **[Relationship Mapping](./relationship-mapping.md)** - Foreign key relationships

## Quick Reference: Field-by-Field Mapping

### Company (70 entities → 65 after dedup)

| Legacy Field | Source | Target Field | Target Type | Transformation |
|--------------|--------|--------------|-------------|----------------|
| `id` | companies.json | `Company.name` | VARCHAR(255) | Normalize to max 12 chars, alphanumeric |
| `displayName` | companies.json | `Company.displayName` | VARCHAR(255) | Direct mapping |
| `url` | companies.json | `Company.website` | VARCHAR(500) | Direct mapping |
| `logo` / `logoUrl` | companies.json | `Company.logoS3Key` | VARCHAR(500) | Generate: `logos/{year}/companies/{name}/logo-{fileId}.{ext}` |
| Generated | - | `Company.logoUrl` | VARCHAR(1000) | CloudFront URL from S3 key |
| Generated | - | `Company.logoFileId` | VARCHAR(100) | UUID v4 |
| Default | - | `Company.isVerified` | BOOLEAN | `false` (all migrated companies unverified) |

### Event (60 entities)

| Legacy Field | Source | Target Field | Target Type | Transformation |
|--------------|--------|--------------|-------------|----------------|
| Generated | - | `Event.id` | UUID | Generate UUID v4 |
| `bat` | topics.json | `Event.eventCode` | VARCHAR(50) | Generate: `"BATbern" + bat` |
| `bat` | topics.json | `Event.eventNumber` | INTEGER | Direct mapping |
| `topic` | topics.json | `Event.title` | VARCHAR(255) | Truncate to 255 chars if needed |
| `datum` | topics.json | `Event.eventDate` | TIMESTAMP WITH TIME ZONE | Parse German date formats |
| `eventType` | topics.json | `Event.eventType` | VARCHAR(50) | "Abend-BAT" → "evening", etc. |
| Default | - | `Event.status` | VARCHAR(50) | `"archived"` (all historical) |
| Default | - | `Event.workflowState` | VARCHAR(50) | `"published"` (all historical) |

### User (269 entities)

| Legacy Field | Source | Target Field | Target Type | Transformation |
|--------------|--------|--------------|-------------|----------------|
| Generated | - | `User.id` | UUID | Generate UUID v4 |
| `name` | referenten[] | `User.username` | VARCHAR(100) | Generate: `firstname.lastname` (normalized) |
| `name` (parsed) | referenten[] | `User.firstName` | VARCHAR(100) | Parse "FirstName LastName, Company" |
| `name` (parsed) | referenten[] | `User.lastName` | VARCHAR(100) | Parse "FirstName LastName, Company" |
| `bio` | referenten[] | `User.bio` | TEXT | Direct mapping (ADR-004) |
| `company` | referenten[] | `User.companyId` | VARCHAR(12) | Normalized company name (FK to Company.name) |
| `portrait` | referenten[] | `User.profilePictureS3Key` | VARCHAR(500) | Generate: `profile-pictures/{year}/{username}/profile-{fileId}.{ext}` |
| Generated | - | `User.profilePictureUrl` | VARCHAR(2048) | CloudFront URL from S3 key |
| Generated | - | `User.profilePictureFileId` | VARCHAR(100) | UUID v4 |

### Speaker (269 entities)

| Legacy Field | Source | Target Field | Target Type | Transformation |
|--------------|--------|--------------|-------------|----------------|
| Generated | - | `Speaker.id` | UUID | Generate UUID v4 |
| User.id (FK) | - | `Speaker.userId` | UUID | FK to User.id (cross-service, ADR-004) |
| Default | - | `Speaker.availability` | VARCHAR(50) | `"available"` (default for historical) |
| Default | - | `Speaker.workflowState` | VARCHAR(50) | `"open"` (default for historical) |
| Default | - | `Speaker.expertiseAreas` | JSONB | `[]` (empty array) |
| Default | - | `Speaker.speakingTopics` | JSONB | `[]` (empty array) |

### Session (302 entities)

| Legacy Field | Source | Target Field | Target Type | Transformation |
|--------------|--------|--------------|-------------|----------------|
| Generated | - | `Session.id` | UUID | Generate UUID v4 |
| `bat` → Event.id | sessions.json | `Session.eventId` | UUID | Lookup Event by BAT number, use Event.id |
| `title` | sessions.json | `Session.title` | VARCHAR(255) | Direct mapping |
| `abstract` | sessions.json | `Session.description` | TEXT | Direct mapping (optional field) |
| Default | - | `Session.sessionType` | VARCHAR(50) | `"presentation"` (default) |

### SessionUser (Junction - many entities)

| Legacy Field | Source | Target Field | Target Type | Transformation |
|--------------|--------|--------------|-------------|----------------|
| Generated | - | `SessionUser.id` | UUID | Generate UUID v4 |
| Session.id (FK) | - | `SessionUser.sessionId` | UUID | FK to Session.id |
| User.id (FK) | referenten[] | `SessionUser.userId` | UUID | Lookup User by name, use User.id (cross-service) |
| Default | - | `SessionUser.speakerRole` | VARCHAR(50) | `"primary_speaker"` (default for historical) |
| Default | - | `SessionUser.isConfirmed` | BOOLEAN | `true` (all historical confirmed) |

## Data Type Conversions

### String Truncation

| Target Field | Max Length | Action if Exceeded |
|--------------|------------|-------------------|
| Company.name | 12 chars | Truncate + normalize to alphanumeric |
| Company.displayName | 255 chars | Truncate |
| Event.title | 255 chars | Truncate |
| User.username | 100 chars | Truncate normalized version |

### Date Parsing (German Formats)

**Supported Formats**:
1. `"DD. Month YY, HH:mmh - HH:mmh"` → Parse with 2-digit year conversion
2. `"Day, DD. Month YYYY, HH:mm - HH:mm Uhr"` → Parse with full year
3. `"YYYY-MM-DD"` → ISO date format

**German Months**: Januar, Februar, März, April, Mai, Juni, Juli, August, September, Oktober, November, Dezember

**Year Conversion** (2-digit):
- 00-30 → 2000-2030
- 31-99 → 1931-1999

### Enum Mappings

**Event Type**:
- `"Abend-BAT"` → `"evening"`
- `"Ganztag-BAT"` → `"full_day"`
- `"Halb-BAT"` → `"afternoon"`

**Speaker Role**:
- Default: `"primary_speaker"`
- Available: `"co_speaker"`, `"moderator"`, `"panelist"`

## Default Values

### For Historical Data

| Field | Default Value | Rationale |
|-------|--------------|-----------|
| Event.status | `"archived"` | All historical events completed |
| Event.workflowState | `"published"` | All content was published |
| Speaker.availability | `"available"` | Assume available unless noted |
| Speaker.workflowState | `"open"` | Default state for migrated data |
| SessionUser.speakerRole | `"primary_speaker"` | Most common role |
| SessionUser.isConfirmed | `true` | All historical sessions confirmed |
| Company.isVerified | `false` | Require manual verification |

## S3 Bucket Structure

**Bucket Name**: `batbern-development-company-logos` (development)

**Directory Structure**:
```
├── logos/{year}/companies/{companyName}/logo-{fileId}.{ext}
├── profile-pictures/{year}/{username}/profile-{fileId}.{ext}
├── presentations/{eventNumber}/{filename}
└── photos/events/{eventNumber}/{filename}
```

**CDN Base URL**: `https://cdn.batbern.ch/`

## Foreign Key Relationships

### Dependency Order (Critical)

```
1. Company (no dependencies)
2. Event (no dependencies)
3. User (requires: Company)
4. Speaker (requires: User)
5. Session (requires: Event)
6. SessionUser (requires: Session + User)
```

### FK Mappings

| From | To | FK Column | FK Type | Service Boundary |
|------|-----|-----------|---------|-----------------|
| Session | Event | event_id | UUID | Same service |
| SessionUser | Session | session_id | UUID | Same service |
| SessionUser | User | user_id | UUID | **Cross-service** |
| Speaker | User | user_id | UUID | **Cross-service** |
| User | Company | company_id | VARCHAR(12) | Same service (meaningful ID) |

## Validation Requirements

### Pre-Migration Validation

**Company Existence** (before creating Users):
```typescript
if (!validateCompanyExists(user.companyId, companies)) {
  throw new Error(`Company ${user.companyId} not found`);
}
```

**Event Existence** (before creating Sessions):
```typescript
const event = findEventByEventNumber(session.bat, events);
if (!event) {
  throw new Error(`Event ${session.bat} not found`);
}
```

### Schema Constraint Validation

| Constraint | Check | Action if Failed |
|------------|-------|-----------------|
| Company.name VARCHAR(12) | Length ≤ 12, alphanumeric | Truncate + normalize |
| User.username VARCHAR(100) | Length ≤ 100 | Truncate |
| User.companyId pattern | Matches `^[a-zA-Z0-9]{1,12}$` | Normalize |
| Event.eventCode VARCHAR(50) | Length ≤ 50 | Truncate |

### Uniqueness Constraints

| Field | Constraint | Migration Strategy |
|-------|-----------|-------------------|
| Company.name | UNIQUE | Use normalized ID, skip duplicates |
| Event.eventNumber | UNIQUE | Direct 1:1 mapping from BAT number |
| User.username | UNIQUE | Append number if collision (`.goetz2`) |
| SessionUser (session_id, user_id) | UNIQUE | Prevent duplicate assignments |

## Implementation Reference

### Mapper Modules

| Module | Purpose | Tests | Status |
|--------|---------|-------|--------|
| `company-mapper.ts` | Company + logo mapping | 17 tests | ✅ Passing |
| `event-mapper.ts` | Event + German date parsing | 15 tests | ✅ Passing |
| `speaker-mapper.ts` | User + Speaker (ADR-004) | 24 tests | ✅ Passing |
| `file-mapper.ts` | S3 keys + CDN URLs | 24 tests | ✅ Passing |
| `relationship-mapper.ts` | FK relationships | 16 tests | ✅ Passing |

**Total**: 96 tests passing ✅

### Test Coverage by Acceptance Criteria

| AC | Description | Tests | Status |
|----|-------------|-------|--------|
| AC1 | Company Mapping | 17 | ✅ |
| AC2 | Event Mapping | 15 | ✅ |
| AC3 | Speaker Mapping | 24 | ✅ |
| AC4 | File Mapping | 24 | ✅ |
| AC5 | Relationship Mapping | 16 | ✅ |

## Migration Execution Order

1. **Load Source Data**: Read JSON files from `apps/BATspa-old/src/api/`
2. **Create Companies**: 65 Company entities (skip 5 duplicates)
3. **Create Events**: 60 Event entities with parsed dates
4. **Create Users**: 269 User entities from speakers
5. **Create Speakers**: 269 Speaker entities linked to Users
6. **Create Sessions**: 302 Session entities linked to Events
7. **Create SessionUsers**: Many SessionUser junctions linking Sessions to Users
8. **Upload Files**: Upload all files to S3 with generated keys

## Architecture Compliance

### ADR-004: Reference Pattern

✅ **User fields NOT duplicated**:
- Speaker entity does NOT have: bio, profilePictureUrl, companyId
- SessionUser entity does NOT have: firstName, lastName, email, bio

✅ **Access via FK**:
- Speaker.userId → User (access bio, profilePicture)
- SessionUser.userId → User (access firstName, lastName)

### Story 1.16.2: Meaningful IDs

✅ **Public identifiers**:
- Company.name = `"mobiliar"` (NOT UUID)
- Event.eventCode = `"BATbern56"` (NOT UUID)
- User.username = `"thomas.goetz"` (NOT UUID)

✅ **API URLs**:
- `/api/companies/mobiliar`
- `/api/events/BATbern56`
- `/api/users/thomas.goetz`

### Schema Source of Truth

✅ **Flyway migrations validated**:
- Company: `V3__Create_companies_schema.sql`
- User: `V4__Create_user_profiles_table.sql`
- Event/Session: `V2__Create_events_schema.sql`
- SessionUser: `V7__Add_session_users_junction_table.sql`

## References

- **Detailed Mappings**: See individual mapping documents in `docs/migration/`
- **Implementation**: `apps/migration-analysis/src/mappers/`
- **Tests**: `apps/migration-analysis/src/__tests__/`
- **Test Results**: All 96 tests passing ✅
