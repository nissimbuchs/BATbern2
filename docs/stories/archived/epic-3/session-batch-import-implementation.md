# Session Batch Import Implementation Plan

## Implementation Status

### ✅ Completed (All Phases)

#### Phase 1 & 2: Backend
- **Integration Tests** (7 test cases in `SessionBatchImportIntegrationTest.java`):
  - ✅ Import sessions with valid JSON
  - ✅ Skip duplicates (event_id + title)
  - ✅ Assign speakers by speakerId → username matching
  - ✅ Assign event organizer as moderator when no speakers
  - ✅ Calculate sequential 45-minute time slots
  - ✅ Handle missing speakers gracefully
  - ✅ Return 404 when event not found
  - **All 7 tests passing** ✅

- **DTOs Created**:
  - ✅ `BatchImportSessionRequest.java` (with @JsonProperty for "abstract" reserved keyword)
  - ✅ `BatchImportSessionResult.java`
  - ✅ `SessionImportDetail.java` (factory methods for success/skipped/failed)

- **Service Layer**:
  - ✅ `SessionBatchImportService.java` - Core business logic
    - Duplicate detection by (event_id, title)
    - Sequential 45-min time slot calculation
    - Speaker assignment (PRIMARY_SPEAKER, CO_SPEAKER, MODERATOR)
    - Graceful error handling for missing speakers

- **Controller**:
  - ✅ Added `POST /api/v1/events/{eventCode}/sessions/batch-import` endpoint in `SessionController.java`

- **Repository**:
  - ✅ Added `findByEventIdAndTitle()` method for duplicate detection

#### Phase 3: Frontend Components
- ✅ TypeScript types (`sessionImport.types.ts`)
- ✅ Parsing utils (`sessionImport.ts`)
- ✅ Custom hook (`useSessionBatchImport`)
- ✅ Modal component (`SessionBatchImportModal.tsx`)

#### Phase 4: Frontend Integration
- ✅ API client method (`sessionService.ts`)
- ✅ QuickActions button added
- ✅ Dashboard integration complete
- ✅ German/English translations added

#### Phase 5: Documentation & Specification
- ✅ OpenAPI specification updated with:
  - Batch import endpoint definition
  - Request/response schemas
  - Example payloads

### ⏳ Remaining
- **Phase 5**: Manual E2E test with real `sessions.json` (4433 lines) - Ready for user testing

### 📝 Implementation Notes
- **Java Reserved Keyword**: Used `@JsonProperty("abstract")` to map JSON "abstract" → Java field "sessionAbstract"
- **Slug Generation**: Split into two calls - `generateSessionSlug()` then `ensureUniqueSlug()` with lambda
- **EventType**: Used `EventType.EVENING` for 18:00 start time (not QUARTERLY_CONFERENCE)
- **Enum Storage**: `SpeakerRoleConverter` stores lowercase snake_case in DB (primary_speaker, moderator, etc.)
- **UserApiClient**: Mocked in tests to return UserResponse objects for known usernames

---

## Overview
Add batch import functionality for historical sessions to the Event Management Dashboard, following the existing EventBatchImportModal pattern. Import sessions from `/apps/BATspa-old/src/api/sessions.json` with duplicate detection and speaker assignment.

## Session Data Model & JSON Mapping

### Database Schema (sessions table)
```sql
CREATE TABLE sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    session_slug VARCHAR(200) UNIQUE NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    session_type VARCHAR(50) NOT NULL CHECK (session_type IN (
        'keynote', 'presentation', 'workshop', 'panel_discussion',
        'networking', 'break', 'lunch'
    )),
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    room VARCHAR(100),
    capacity INTEGER,
    language VARCHAR(10) DEFAULT 'de',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

### Database Schema (session_users table - for speaker assignments)
```sql
CREATE TABLE session_users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id UUID NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    username VARCHAR(100),
    speaker_role VARCHAR(50) NOT NULL CHECK (speaker_role IN (
        'primary_speaker', 'co_speaker', 'moderator', 'panelist'
    )),
    presentation_title VARCHAR(255),
    is_confirmed BOOLEAN DEFAULT FALSE,
    invited_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    confirmed_at TIMESTAMP WITH TIME ZONE,
    declined_at TIMESTAMP WITH TIME ZONE,
    decline_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT unique_session_user_username UNIQUE (session_id, username)
);
```

### JSON Structure (sessions.json)
```json
{
  "bat": 1,                    // Event number (links to BATbern{bat})
  "pdf": "BAT01_RTC_IBISDesktop.pdf",
  "title": "IBIS Desktop",
  "abstract": "Die RTC hat sich...",
  "authoren": "",              // Moderator names (sometimes empty)
  "referenten": [              // Array of speakers
    {
      "name": "Nissim J. Buchs, RTC AG",
      "bio": "Dr. Nissim J. Buchs ist...",
      "company": "rtc",
      "portrait": "nissim.buchs.jpg",
      "speakerId": "nissim.buchs"
    }
  ]
}
```

### Field Mapping (JSON → Database)

| sessions.json Field | Database Field | Transformation Logic |
|---------------------|----------------|----------------------|
| `title` | `title` | Direct copy (max 255 chars) |
| `abstract` | `description` | Direct copy as TEXT |
| `bat` | `event_id` | Lookup event by eventNumber = bat, get UUID |
| N/A | `session_type` | Default to `'presentation'` |
| N/A | `start_time` | Event date + sequential 45min slots (see Time Calculation) |
| N/A | `end_time` | start_time + 45 minutes |
| N/A | `room` | NULL (not in legacy data) |
| N/A | `capacity` | NULL (not in legacy data) |
| N/A | `language` | Default to `'de'` |
| N/A | `session_slug` | Auto-generated from title (kebab-case + UUID suffix) |
| `pdf` | Store in description | Append "PDF: {pdf}" to description for reference |

### Speaker Assignment Mapping (referenten → session_users)

| referenten Field | session_users Field | Transformation Logic |
|------------------|---------------------|----------------------|
| `speakerId` | `username` | Map speakerId → username in user_profiles |
| N/A | `speaker_role` | `'primary_speaker'` for first speaker, `'co_speaker'` for others |
| N/A | `is_confirmed` | Default to `false` (pending) |
| N/A | `user_id` | Lookup user_id from user_profiles by username |

**Special Cases:**
- **No speakers** (`referenten` is empty/null): Assign event's `organizerUsername` as `'moderator'`
- **Speaker not found**: Skip speaker assignment with warning (don't fail entire import)

### Time Calculation Logic

For each session in an event (ordered by import sequence):
```
1. Get event's date and startTime (e.g., 2024-12-15T18:00:00Z)
2. Calculate sequential slot:
   - Session 1: eventDate @ eventStartTime → eventStartTime + 45min
   - Session 2: eventStartTime + 45min → eventStartTime + 90min
   - Session 3: eventStartTime + 90min → eventStartTime + 135min
   - etc.
3. If event has no startTime, use default 09:00 AM on event date
```

**Example:**
- Event: BATbern142, date: 2024-12-15T18:00:00Z
- Session 1: 18:00-18:45
- Session 2: 18:45-19:30
- Session 3: 19:30-20:15

### Duplicate Detection Strategy

**Primary Key:** Check for existing session by `(event_id, title)`
- If exists: **SKIP** with status "Already exists"
- If not exists: **CREATE** new session

**Why not session_slug?** Slug is auto-generated, so we can't predict it from JSON data.

## Implementation Tasks

### 1. Backend: Session Batch Import API Endpoint

**New Endpoint:** `POST /api/v1/events/{eventCode}/sessions/batch-import`

**Location:** `/services/event-management-service/src/main/java/ch/batbern/events/controller/SessionController.java`

**Request DTO:**
```java
public class BatchImportSessionRequest {
    private String title;
    private String description;
    private String pdf;  // Legacy PDF filename
    private Integer bat; // Event number
    private List<LegacySpeaker> referenten;
    private String authoren; // Moderator names
}

public class LegacySpeaker {
    private String name;
    private String bio;
    private String company;
    private String portrait;
    private String speakerId;
}
```

**Response DTO:**
```java
public class BatchImportSessionResult {
    private int totalProcessed;
    private int successfullyCreated;
    private int skipped;
    private int failed;
    private List<SessionImportDetail> details;
}

public class SessionImportDetail {
    private String title;
    private String status; // "success" | "skipped" | "failed"
    private String message;
    private String sessionSlug; // null if failed
}
```

**Implementation Steps:**
1. Create `SessionBatchImportService.java` in service layer
2. For each session in request:
   - Lookup event by eventNumber (bat field)
   - Check duplicate by (event_id, title)
   - Calculate startTime/endTime using sequential slot logic
   - Create session entity
   - Assign speakers by matching speakerId → username
   - If no speakers, assign event organizer as moderator
3. Return BatchImportSessionResult with success/skip/fail counts

**Files to Create/Modify:**
- `/services/event-management-service/src/main/java/ch/batbern/events/controller/SessionController.java` (add endpoint)
- `/services/event-management-service/src/main/java/ch/batbern/events/service/SessionBatchImportService.java` (NEW)
- `/services/event-management-service/src/main/java/ch/batbern/events/dto/BatchImportSessionRequest.java` (NEW)
- `/services/event-management-service/src/main/java/ch/batbern/events/dto/BatchImportSessionResult.java` (NEW)

### 2. Frontend: Session Batch Import Modal

**New Component:** `SessionBatchImportModal.tsx`

**Location:** `/web-frontend/src/components/shared/Session/SessionBatchImportModal.tsx`

**Features:**
- File upload (sessions.json only)
- Parse JSON and create import candidates
- Preview table showing:
  - Event (BATbern{bat})
  - Session Title
  - Description preview (first 80 chars)
  - Speakers count
  - Import status (pending/success/error/skipped)
- Sequential import with progress tracking
- Final result summary

**Implementation Pattern:** Follow `EventBatchImportModal.tsx` structure

**No Field Selection:** Unlike events, sessions don't need field-level import/ignore toggles since all sessions are new imports.

**Files to Create:**
- `/web-frontend/src/components/shared/Session/SessionBatchImportModal.tsx` (NEW)
- `/web-frontend/src/hooks/useSessionBatchImport/useSessionBatchImport.ts` (NEW)
- `/web-frontend/src/hooks/useSessionBatchImport/index.ts` (NEW)
- `/web-frontend/src/utils/sessionImport.ts` (NEW - parsing logic)
- `/web-frontend/src/types/sessionImport.types.ts` (NEW - TypeScript types)

### 3. Frontend: Add Button to Event Management Dashboard

**Component:** `QuickActions.tsx`

**Location:** `/web-frontend/src/components/organizer/EventManagement/QuickActions.tsx`

**Changes:**
1. Add new prop: `onBatchImportSessions?: () => void`
2. Add new button after "Import Events" button:
   ```tsx
   <Button
     variant="outlined"
     fullWidth
     startIcon={<UploadFileIcon />}
     onClick={onBatchImportSessions}
   >
     {t('common:session.batchImport.button')}
   </Button>
   ```

**Dashboard Integration:** `/web-frontend/src/components/organizer/EventManagement/EventManagementDashboard.tsx`

**Changes:**
1. Add state: `const [isSessionBatchImportOpen, setIsSessionBatchImportOpen] = useState(false);`
2. Pass prop to QuickActions: `onBatchImportSessions={() => setIsSessionBatchImportOpen(true)}`
3. Render modal after EventBatchImportModal:
   ```tsx
   <SessionBatchImportModal
     open={isSessionBatchImportOpen}
     onClose={() => setIsSessionBatchImportOpen(false)}
     onImportComplete={(result) => {
       console.log('Session import complete:', result);
       // Optionally refresh session list
     }}
   />
   ```

**Files to Modify:**
- `/web-frontend/src/components/organizer/EventManagement/QuickActions.tsx`
- `/web-frontend/src/components/organizer/EventManagement/EventManagementDashboard.tsx`

### 4. Service Layer: Session Import Business Logic

**File:** `/services/event-management-service/src/main/java/ch/batbern/events/service/SessionBatchImportService.java`

**Key Methods:**
```java
public BatchImportSessionResult importSessions(String eventCode, List<BatchImportSessionRequest> requests);
private Session createSessionFromLegacy(Event event, BatchImportSessionRequest request, int sequenceIndex);
private void assignSpeakers(Session session, List<LegacySpeaker> referenten);
private Instant calculateStartTime(Event event, int sequenceIndex);
private String mapSpeakerIdToUsername(String speakerId);
```

**Speaker Matching Logic:**
1. For each speaker in `referenten`:
   - Query `user_profiles` table by `username = speakerId`
   - If found: Create `SessionUser` with `speaker_role = 'primary_speaker'` (first) or `'co_speaker'` (others)
   - If not found: Log warning, skip speaker assignment
2. If `referenten` is empty:
   - Get event's `organizerUsername`
   - Create `SessionUser` with `speaker_role = 'moderator'`

**Duplicate Detection:**
```java
Optional<Session> existing = sessionRepository.findByEventIdAndTitle(event.getId(), request.getTitle());
if (existing.isPresent()) {
    return SessionImportDetail.skipped("Session already exists");
}
```

**Files to Create:**
- `/services/event-management-service/src/main/java/ch/batbern/events/service/SessionBatchImportService.java` (NEW)

### 5. API Client: Frontend Session Service

**File:** `/web-frontend/src/services/sessionService.ts`

**New Method:**
```typescript
async batchImportSessions(
  eventCode: string,
  sessions: BatchImportSessionRequest[]
): Promise<BatchImportSessionResult> {
  const response = await apiClient.post(
    `/api/v1/events/${eventCode}/sessions/batch-import`,
    sessions
  );
  return response.data;
}
```

**Files to Create/Modify:**
- `/web-frontend/src/services/sessionService.ts` (create if doesn't exist, or modify)

### 6. Translations

**Files to Modify:**
- `/web-frontend/public/locales/de/common.json`
- `/web-frontend/public/locales/en/common.json`

**New Keys:**
```json
{
  "session": {
    "batchImport": {
      "button": "Sitzungen importieren",
      "title": "Sitzungen-Batch-Import",
      "dropzone": "sessions.json hier ablegen oder klicken",
      "dropzoneActive": "sessions.json hier ablegen",
      "dropzoneHint": "Nur sessions.json wird akzeptiert",
      "preview": "{{count}} Sitzungen zum Import",
      "progress": "{{current}} von {{total}} importiert",
      "complete": "Import abgeschlossen: {{success}} erfolgreich, {{skipped}} übersprungen, {{failed}} fehlgeschlagen",
      "noSessions": "Keine Sitzungen in der Datei gefunden",
      "importButtonWithCount": "{{count}} Sitzungen importieren",
      "cancelButton": "Abbrechen",
      "columns": {
        "event": "Event",
        "title": "Titel",
        "speakers": "Referenten",
        "status": "Status"
      },
      "status": {
        "pending": "Ausstehend",
        "importing": "Importiere...",
        "success": "Erfolgreich",
        "skipped": "Übersprungen",
        "error": "Fehler"
      },
      "errors": {
        "invalidFile": "Ungültige Datei. Nur sessions.json ist erlaubt.",
        "parseError": "Fehler beim Parsen: {{error}}"
      }
    }
  }
}
```

## Testing Strategy

### Backend Integration Tests

**File:** `/services/event-management-service/src/test/java/ch/batbern/events/controller/SessionBatchImportIntegrationTest.java`

**Test Cases:**
1. `should_importSessions_when_validJsonProvided()`
2. `should_skipDuplicates_when_sessionAlreadyExists()`
3. `should_assignSpeakers_when_referentenProvided()`
4. `should_assignModerator_when_noReferenten()`
5. `should_calculateSequentialTimes_when_multipleSessionsImported()`
6. `should_handleMissingSpeaker_when_usernameNotFound()`

### Frontend Component Tests

**File:** `/web-frontend/src/components/shared/Session/SessionBatchImportModal.test.tsx`

**Test Cases:**
1. `should render dropzone when modal is open`
2. `should parse sessions.json and display preview`
3. `should show progress during import`
4. `should display import results`
5. `should handle parse errors`

### E2E Tests (Optional)

**File:** `/web-frontend/e2e/session-batch-import.spec.ts`

**Test Cases:**
1. Upload sessions.json → See preview → Import → Verify success

## OpenAPI Specification Updates

**File:** `/docs/api/events-api.openapi.yml`

Add new endpoint definition:
```yaml
/events/{eventCode}/sessions/batch-import:
  post:
    tags:
      - Sessions
    summary: Batch import sessions from legacy JSON
    description: Import multiple sessions from sessions.json with speaker assignments
    operationId: batchImportSessions
    requestBody:
      required: true
      content:
        application/json:
          schema:
            type: array
            items:
              $ref: '#/components/schemas/BatchImportSessionRequest'
    responses:
      '200':
        description: Import completed
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/BatchImportSessionResult'
```

## Implementation Order (TDD Approach)

### Phase 1: Backend Foundation (Red → Green → Refactor)
1. **RED**: Write failing integration test for batch import endpoint
2. **GREEN**: Create endpoint, DTOs, service with minimal logic to pass test
3. **REFACTOR**: Extract duplicate detection, time calculation, speaker assignment

### Phase 2: Speaker Assignment Logic
1. **RED**: Write test for speaker matching by speakerId → username
2. **GREEN**: Implement speaker lookup and SessionUser creation
3. **REFACTOR**: Handle missing speakers gracefully

### Phase 3: Frontend Components
1. **RED**: Write component test for SessionBatchImportModal
2. **GREEN**: Create modal with file upload and preview
3. **REFACTOR**: Extract parsing logic to utils/sessionImport.ts

### Phase 4: Frontend Integration
1. Add button to QuickActions
2. Integrate modal with EventManagementDashboard
3. Add translations

### Phase 5: End-to-End Testing
1. Manual test with real sessions.json file
2. Verify duplicate detection works
3. Verify speaker assignments are correct
4. Verify sequential time slots are calculated properly

## Critical Files Summary

### Backend Files (Java/Spring Boot)
- `/services/event-management-service/src/main/java/ch/batbern/events/controller/SessionController.java` (add endpoint)
- `/services/event-management-service/src/main/java/ch/batbern/events/service/SessionBatchImportService.java` (NEW - main logic)
- `/services/event-management-service/src/main/java/ch/batbern/events/dto/BatchImportSessionRequest.java` (NEW)
- `/services/event-management-service/src/main/java/ch/batbern/events/dto/BatchImportSessionResult.java` (NEW)
- `/services/event-management-service/src/test/java/ch/batbern/events/controller/SessionBatchImportIntegrationTest.java` (NEW - tests)

### Frontend Files (React/TypeScript)
- `/web-frontend/src/components/shared/Session/SessionBatchImportModal.tsx` (NEW - main modal)
- `/web-frontend/src/hooks/useSessionBatchImport/useSessionBatchImport.ts` (NEW - import hook)
- `/web-frontend/src/utils/sessionImport.ts` (NEW - parsing logic)
- `/web-frontend/src/types/sessionImport.types.ts` (NEW - type definitions)
- `/web-frontend/src/components/organizer/EventManagement/QuickActions.tsx` (modify - add button)
- `/web-frontend/src/components/organizer/EventManagement/EventManagementDashboard.tsx` (modify - integrate modal)
- `/web-frontend/src/services/sessionService.ts` (create or modify - API client)

### Documentation Files
- `/docs/api/events-api.openapi.yml` (add batch import endpoint)
- `/web-frontend/public/locales/de/common.json` (add translations)
- `/web-frontend/public/locales/en/common.json` (add translations)

## Estimated Complexity
- **Backend**: Medium (6-8 hours) - Standard CRUD with business logic
- **Frontend**: Low-Medium (4-6 hours) - Following existing EventBatchImportModal pattern
- **Testing**: Medium (4-6 hours) - Integration tests for speaker matching logic
- **Total**: 14-20 hours

## Risk Mitigation
1. **Missing speakers**: Log warnings but don't fail import - allow manual assignment later
2. **Time conflicts**: Sequential slots may overlap with actual event schedule - document as known limitation
3. **Large files**: sessions.json is 4433 lines - implement client-side batching if performance issues arise
