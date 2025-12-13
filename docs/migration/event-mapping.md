# Event Mapping Documentation

**Story**: 3.1.2 - Domain Mapping & Schema Transformation Design
**Generated**: 2025-11-19
**Author**: Dev Agent (James)

## Overview

This document specifies the mapping rules for transforming legacy event/topic data from `topics.json` to the Event Management Service schema.

## Source Data

**File**: `apps/BATspa-old/src/api/topics.json`
**Format**: JSON array of event objects
**Total Events**: 60 events (BAT 1 - BAT 60)
**Event Types**:
- 44 Abend-BAT (Evening events)
- 14 Ganztag-BAT (Full-day events)
- 2 Halb-BAT (Half-day/Afternoon events)

## Target Schema

**Service**: Event Management Service
**Table**: `events`
**Schema Source**: `services/event-management-service/src/main/resources/db/migration/V2__Create_events_schema.sql`

### Event Entity Structure

```sql
CREATE TABLE events (
  id UUID PRIMARY KEY,                          -- Internal database key
  event_code VARCHAR(50) NOT NULL,              -- Public API identifier
  event_number INTEGER UNIQUE NOT NULL,         -- Sequential number (1-60)
  title VARCHAR(255) NOT NULL,                  -- Event topic/theme
  event_date TIMESTAMP WITH TIME ZONE NOT NULL, -- Parsed from German date string
  event_type VARCHAR(50),                       -- 'evening', 'full_day', 'afternoon'
  status VARCHAR(50),                           -- 'archived' for historical events
  workflow_state VARCHAR(50),                   -- 'published' for historical events
  organizer_id UUID                             -- To be set during migration
);
```

## Mapping Rules

### 1. Event Identifier (Meaningful ID)

**Source**: `topics.json` → `bat` field (integer)
**Target**:
- `Event.eventCode` (VARCHAR(50)) - Public API identifier
- `Event.eventNumber` (INTEGER UNIQUE) - Internal reference

**Rule**: Generate eventCode pattern: `"BATbern{bat}"`

**Examples**:
- `bat: 1` → `eventCode: "BATbern1"`, `eventNumber: 1`
- `bat: 56` → `eventCode: "BATbern56"`, `eventNumber: 56`
- `bat: 60` → `eventCode: "BATbern60"`, `eventNumber: 60`

**API URL Pattern**: `/api/events/BATbern56` (per Story 1.16.2)

### 2. Event Title

**Source**: `topics.json` → `topic` field
**Target**: `Event.title` (VARCHAR(255))

**Rule**: Direct mapping with truncation if > 255 chars

**Examples**:
- `"Cloud Security"` → `"Cloud Security"`
- Very long topic → Truncated to 255 chars

### 3. Event Date Parsing

**Source**: `topics.json` → `datum` field (German date string)
**Target**: `Event.eventDate` (TIMESTAMP WITH TIME ZONE)

**Challenge**: Multiple German date formats in legacy data

#### Supported Date Formats

**Format 1: Short year with time**
```
Pattern: "DD. Month YY, HH:mmh - HH:mmh"
Example: "24. Juni 05, 16:00h - 18:30h"
Output: 2005-06-24T16:00:00Z
```

**Format 2: Full year with time**
```
Pattern: "Day, DD. Month YYYY, HH:mm - HH:mm Uhr"
Example: "Donnerstag, 2. Mai 2024, 16:00 - 18:30 Uhr"
Output: 2024-05-02T16:00:00Z
```

**Format 3: ISO date**
```
Pattern: "YYYY-MM-DD"
Example: "2024-05-02"
Output: 2024-05-02T00:00:00Z
```

#### German Month Names

All month names are case-insensitive and support umlauts:

| German | English | Month Index |
|--------|---------|-------------|
| Januar | January | 0 |
| Februar | February | 1 |
| März (Maerz) | March | 2 |
| April | April | 3 |
| Mai | May | 4 |
| Juni | June | 5 |
| Juli | July | 6 |
| August | August | 7 |
| September | September | 8 |
| Oktober | October | 9 |
| November | November | 10 |
| Dezember | December | 11 |

**Note**: "März" with umlaut ä is supported via regex pattern `[a-zA-ZäöüÄÖÜß]+`

#### Year Conversion (2-digit to 4-digit)

For dates with 2-digit years (e.g., "05", "24"):
- Years 00-30 → 2000-2030 (assume 21st century)
- Years 31-99 → 1931-1999 (assume 20th century)

**Examples**:
- "05" → 2005
- "24" → 2024
- "95" → 1995

### 4. Event Type Mapping

**Source**: `topics.json` → `eventType` field
**Target**: `Event.eventType` (VARCHAR(50) with CHECK constraint)

**Mapping Table**:

| Legacy Value | Target Enum | Description |
|--------------|-------------|-------------|
| "Abend-BAT" | `evening` | Evening events (16:00-18:30) |
| "Ganztag-BAT" | `full_day` | Full-day events (09:00-17:00) |
| "Halb-BAT" | `afternoon` | Half-day/afternoon events |
| Unknown | `evening` | Default fallback |

### 5. Event Status for Historical Events

**Target**:
- `Event.status` (VARCHAR(50))
- `Event.workflowState` (VARCHAR(50))

**Rule**: All historical events (BAT 1-60) are completed

**Values**:
- `status = 'archived'` - Event is in the past, completed
- `workflowState = 'published'` - Event content was published to attendees

**Rationale**: Legacy events from 2005-2024 are historical records, not active events.

### 6. Event UUID Generation

**Target**: `Event.id` (UUID PRIMARY KEY)

**Rule**: Generate new UUID v4 for each event during migration

**Example**: `550e8400-e29b-41d4-a716-446655440000`

### 7. Organizer Assignment

**Target**: `Event.organizerId` (UUID)

**Strategy**: To be determined during migration
- Option A: Assign to default system organizer
- Option B: Map to historical organizer if identifiable
- Option C: Leave NULL initially, backfill later

## Schema Compliance

### Constraints Validated

✅ **Event.eventCode VARCHAR(50)**: All event codes ≤ 50 chars (max: "BATbern999" = 10 chars)
✅ **Event.title VARCHAR(255)**: Truncated if needed
✅ **Event.eventNumber INTEGER UNIQUE**: One-to-one mapping with BAT number
✅ **Event.status CHECK constraint**: Uses valid enum value `'archived'`
✅ **Event.workflowState CHECK constraint**: Uses valid enum value `'published'`
✅ **Event.eventType CHECK constraint**: Uses valid enum values

### Foreign Key Relationships

None at event level. Relationships handled by:
- **Session → Event**: `sessions.event_id` → `events.id` (UUID FK)

## Implementation

**Module**: `apps/migration-analysis/src/mappers/event-mapper.ts`
**Tests**: `apps/migration-analysis/src/__tests__/event-mapper.test.ts`
**Test Results**: 15 tests passing ✅

### Key Functions

- `parseGermanDate(dateString)`: Parse multiple German date formats
- `mapEventType(legacyType)`: Map event type to enum
- `mapEvent(legacyEvent)`: Map single event to target schema

## Example Mapping

**Input** (topics.json):
```json
{
  "bat": 56,
  "topic": "Cloud Security in der Praxis",
  "datum": "24. Juni 05, 16:00h - 18:30h",
  "eventType": "Abend-BAT"
}
```

**Output** (Event entity):
```typescript
{
  id: "550e8400-e29b-41d4-a716-446655440000",
  eventCode: "BATbern56",
  eventNumber: 56,
  title: "Cloud Security in der Praxis",
  eventDate: "2005-06-24T16:00:00.000Z",
  eventType: "evening",
  status: "archived",
  workflowState: "published"
}
```

## Migration Order

Events can be created independently, but must exist before:
1. Session entities (require `event_id` FK)
2. Session materials (linked to sessions)
3. SessionUser junctions (linked to sessions)

## Edge Cases Handled

1. **Future/Planned Events**: Legacy data includes `next` or `planned` flags (3-5% of events)
   - These are still mapped as `archived` since they're historical data

2. **Missing Time Information**: Some dates may only have date, no time
   - Default to 00:00:00 if time not provided

3. **Invalid Dates**: If date parsing fails completely
   - Throw error during migration for manual review
   - Prevents corrupted date data in target system

## References

- **Story 1.16.2**: Meaningful IDs pattern (eventCode as public identifier)
- **Event Management Service**: V2__Create_events_schema.sql (source of truth)
- **German Date Formats**: Based on actual data in topics.json
- **Event Types**: Aligned with Event Management Service enum values
