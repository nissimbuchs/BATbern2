# Relationship Mapping Documentation

**Story**: 3.1.2 - Domain Mapping & Schema Transformation Design
**Generated**: 2025-11-19
**Author**: Dev Agent (James)

## Overview

This document defines all foreign key relationships across BATbern microservices, distinguishing between same-service and cross-service relationships per ADR-004.

## Relationship Types

### Same-Service Relationships

Foreign keys within a single microservice (same database).

| Relationship | From | To | FK Column | FK Type |
|--------------|------|----|-----------| --------|
| Session → Event | sessions | events | event_id | UUID |

### Cross-Service Relationships

Foreign keys across microservices (different databases).

| Relationship | From Service | To Service | FK Column | FK Type |
|--------------|--------------|------------|-----------|---------|
| SessionUser → User | Event Management | Company User Management | username | VARCHAR(100) (meaningful ID, cross-service per ADR-003) |
| Speaker → User | Speaker Coordination | Company User Management | username | VARCHAR(100) (meaningful ID, cross-service per ADR-003) |
| User → Company | Company User Management | Company User Management | company_id | VARCHAR(12) (meaningful ID) |

## Detailed Relationship Specifications

### 1. Session → Event (Same Service)

**Services**: Event Management Service
**Direction**: Many sessions → One event
**Cardinality**: Many-to-One

**Foreign Key**:
- Column: `sessions.event_id`
- References: `events.id` (UUID)
- Constraint: `NOT NULL`, `ON DELETE CASCADE`

**Mapping Logic**:
```typescript
// Find event by BAT number from legacy data
const event = events.find(e => e.eventNumber === legacySession.bat);
session.eventId = event.id; // UUID FK
```

**Example**:
```typescript
{
  session: {
    id: "session-uuid-1",
    eventId: "event-uuid-56",  // FK to events.id
    title: "Cloud Security Best Practices"
  },
  event: {
    id: "event-uuid-56",
    eventCode: "BATbern56",
    eventNumber: 56
  }
}
```

### 2. SessionUser → User (Cross-Service)

**Services**: Event Management → Company User Management
**Direction**: Many session participants → One user
**Cardinality**: Many-to-One
**Pattern**: ADR-004 (Reference pattern, no field duplication)

**Foreign Key**:
- Column: `session_users.username`
- References: `user_profiles.username` (VARCHAR(100), meaningful ID, cross-service per ADR-003)
- Constraint: `NOT NULL`

**Mapping Logic**:
```typescript
// Find user by generated username
const username = generateUsername(speaker.firstName, speaker.lastName);
const user = users.find(u => u.username === username);
sessionUser.username = user.username; // Meaningful ID FK (cross-service per ADR-003)
```

**ADR-003 & ADR-004 Implications**:
- User fields (bio, email, firstName, lastName) stored in `user_profiles` table
- SessionUser does NOT duplicate these fields
- Access user data via HTTP API call using username (meaningful ID per ADR-003)

**Example**:
```typescript
{
  sessionUser: {
    id: "session-user-uuid-1",
    sessionId: "session-uuid-1",
    username: "thomas.goetz",  // FK to user_profiles.username (meaningful ID, cross-service per ADR-003)
    speakerRole: "primary_speaker"
  },
  user: {
    id: "user-uuid-123",  // Internal UUID, NOT exposed cross-service
    username: "thomas.goetz",
    firstName: "Thomas",
    lastName: "Goetz",
    bio: "Experienced architect"
  }
}
```

### 3. SessionUser → Session (Same Service)

**Services**: Event Management Service
**Direction**: Many participants → One session
**Cardinality**: Many-to-One

**Foreign Key**:
- Column: `session_users.session_id`
- References: `sessions.id` (UUID)
- Constraint: `NOT NULL`, `ON DELETE CASCADE`
- Unique Constraint: `UNIQUE (session_id, username)` - prevents duplicate speaker assignments

**Example**:
```typescript
{
  sessionUser: {
    id: "session-user-uuid-1",
    sessionId: "session-uuid-1",  // FK to sessions.id (within-service UUID)
    username: "thomas.goetz",     // FK to user_profiles.username (cross-service meaningful ID per ADR-003)
    speakerRole: "primary_speaker"
  }
}
```

### 4. Speaker → User (Cross-Service)

**Services**: Speaker Coordination → Company User Management
**Direction**: One speaker profile → One user
**Cardinality**: One-to-One
**Pattern**: ADR-004 (Reference pattern)

**Foreign Key**:
- Column: `speakers.username`
- References: `user_profiles.username` (VARCHAR(100), meaningful ID, cross-service per ADR-003)
- Constraint: `NOT NULL`, `UNIQUE` (one speaker per user)

**ADR-003 & ADR-004 Implications**:
- User fields (bio, profilePictureUrl, companyId) stored in `user_profiles`
- Speaker entity contains only domain-specific fields (availability, workflowState, expertiseAreas)
- Access user data via HTTP API call using username (meaningful ID per ADR-003)

**Mapping Logic**:
```typescript
// Create User first, then Speaker with username FK (meaningful ID)
const user = mapSpeakerToUser(legacySpeaker, companies);
const speaker = mapSpeakerToSpeaker(user.username); // Speaker.username = User.username (meaningful ID)
```

**Example**:
```typescript
{
  speaker: {
    id: "speaker-uuid-1",  // Internal UUID for Speaker entity (owned by this service)
    username: "thomas.goetz",  // FK to user_profiles.username (cross-service meaningful ID per ADR-003)
    availability: "available",
    workflowState: "open"
    // NOTE: bio, profilePictureUrl NOT here (in User per ADR-004)
  },
  user: {
    id: "user-uuid-123",  // Internal UUID, NOT exposed cross-service
    username: "thomas.goetz",
    bio: "Experienced architect",
    profilePictureUrl: "https://cdn.batbern.ch/..."
  }
}
```

### 5. User → Company (Cross-Service, Meaningful ID)

**Services**: Company User Management → Company User Management (same service)
**Direction**: Many users → One company
**Cardinality**: Many-to-One
**Pattern**: Story 1.16.2 (Meaningful IDs)

**Foreign Key**:
- Column: `user_profiles.company_id`
- References: `companies.name` (VARCHAR(12), meaningful ID **NOT** UUID)
- Constraint: `CHECK (company_id ~ '^[a-zA-Z0-9]{1,12}$')`

**Meaningful ID Pattern** (Story 1.16.2):
- Company.name is the public identifier: "mobiliar", "sbb", "swisscom"
- API URLs: `/api/companies/mobiliar` (not `/api/companies/{uuid}`)
- User.companyId references Company.name, not Company.id

**Mapping Logic**:
```typescript
// Normalize company name to max 12 chars
const companyId = normalizeCompanyName(legacySpeaker.company); // "mobiliar"
user.companyId = companyId; // FK to companies.name (meaningful ID)
```

**Example**:
```typescript
{
  user: {
    id: "user-uuid-123",
    username: "thomas.goetz",
    companyId: "mobiliar"  // FK to companies.name (NOT UUID)
  },
  company: {
    name: "mobiliar",  // PRIMARY KEY (meaningful ID)
    displayName: "Die Mobiliar Versicherungen"
  }
}
```

## Migration Order (Dependency Graph)

```
1. Company entities          (no dependencies)
   ↓
2. Event entities            (no dependencies)
   ↓
3. User entities             (requires: Company)
   ↓
4. Speaker entities          (requires: User)
   ↓
5. Session entities          (requires: Event)
   ↓
6. SessionUser entities      (requires: Session + User)
```

**Critical Path**:
- Companies MUST exist before Users (User.companyId → Company.name)
- Users MUST exist before Speakers (Speaker.username → User.username, meaningful ID per ADR-003)
- Events MUST exist before Sessions (Session.eventId → Event.id)
- Sessions + Users MUST exist before SessionUsers (SessionUser.username → User.username, meaningful ID per ADR-003)

## Lookup Functions

### Find Event by Event Number

```typescript
function findEventByEventNumber(eventNumber: number, events: Event[]): Event | undefined {
  return events.find(e => e.eventNumber === eventNumber);
}

// Usage during migration
const event = findEventByEventNumber(56, events); // BAT 56
session.eventId = event.id;
```

### Find User by Username

```typescript
function findUserByUsername(username: string, users: User[]): User | undefined {
  return users.find(u => u.username === username);
}

// Usage during migration
const user = findUserByUsername('thomas.goetz', users);
sessionUser.username = user.username; // Meaningful ID FK (per ADR-003)
```

## Relationship Validation

### Pre-Migration Checks

Before creating entities with FKs, validate referenced entities exist:

```typescript
// Validate Company exists before creating User
function validateCompanyExists(companyId: string, companies: Company[]): boolean {
  return companies.some(c => c.name === companyId);
}

if (!validateCompanyExists(user.companyId, companies)) {
  throw new Error(`Company ${user.companyId} does not exist`);
}
```

### Referential Integrity

**Same-Service** (database enforced):
- Session.eventId → Event.id (FK constraint in PostgreSQL)
- SessionUser.sessionId → Session.id (FK constraint)

**Cross-Service** (application enforced, meaningful IDs per ADR-003):
- SessionUser.username → User.username (must validate before insert)
- Speaker.username → User.username (must validate before insert)
- User.companyId → Company.name (must validate before insert)

## Implementation

**Module**: `apps/migration-analysis/src/mappers/relationship-mapper.ts`
**Tests**: `apps/migration-analysis/src/__tests__/relationship-mapper.test.ts`
**Test Results**: 16 tests passing ✅

### Key Functions

- `createSessionEventRelationship()`: Link session to event
- `createSessionUserRelationship()`: Link session participant to user (cross-service)
- `createSpeakerUserRelationship()`: Link speaker to user (cross-service, ADR-004)
- `createUserCompanyRelationship()`: Link user to company (meaningful ID)
- `buildRelationshipMap()`: Generate complete relationship map for migration

## References

- **ADR-003**: Meaningful Identifiers in Public APIs (cross-service references MUST use meaningful IDs, NOT UUIDs)
- **ADR-004**: Reference pattern (no field duplication across services)
- **Story 1.16.2**: Meaningful IDs (Company.name, Event.eventCode, User.username)
- **Flyway Migrations**: Schema source of truth for FK constraints
  - V2__Create_events_schema.sql (Session → Event)
  - V7__Add_session_users_junction_table.sql (SessionUser relationships)
  - V4__Create_user_profiles_table.sql (User → Company)
