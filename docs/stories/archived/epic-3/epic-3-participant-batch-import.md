# Plan: Complete Historical Data Migration - Participant/Attendee Batch Import

**Status**: Planning Complete, Implementation Pending
**Created**: 2025-12-25
**Last Updated**: 2025-12-25

---

## Implementation Status

### ✅ Completed (2025-12-25)

1. **Epic 3 Documentation Updated**
   - Updated Phase 2 to reflect frontend batch import approach (not Spring Batch)
   - Added Story 3.2: Participant/Attendee Batch Import
   - Updated status from 33% → 85% complete
   - File: `/docs/prd/epic-3-historical-data-migration.md`

2. **Batch Import Template Extracted**
   - Created reusable template for all batch imports
   - 4-file architecture pattern (Modal, Hook, Types, Utils)
   - Common patterns, testing strategies, best practices
   - File: `/docs/templates/frontend/batch-import-pattern.md`

3. **User Decisions Captured**
   - CSV Parsing: Papa Parse library
   - Event Mapping: `BATbern{N}` format
   - Missing Emails: Generate synthetic `firstname.lastname@batbern.ch`
   - Registration Status: `attended` for historical data
   - Input Format: Accept CSV directly

4. **Backend API Designed**
   - Endpoint: `POST /events/batch_registrations`
   - Request/Response format defined
   - Benefits documented (3x fewer API calls, 8-12 min import)

### ⏳ Remaining Tasks

5. **Backend Batch Registration API Implementation**
   - [ ] Create DTOs (`BatchRegistrationRequest`, `BatchRegistrationResponse`)
   - [ ] Implement controller endpoint
   - [ ] Implement service method with transaction handling
   - [ ] Add integration tests
   - [ ] Update OpenAPI specification

6. **Frontend Participant Batch Import Implementation**
   - [ ] Install Papa Parse: `npm install papaparse @types/papaparse`
   - [ ] Create type definitions (`participantImport.types.ts`)
   - [ ] Create CSV parsing utilities (`participantImportUtils.ts`)
   - [ ] Create business logic hook (`useParticipantBatchImport.ts`)
   - [ ] Create modal component (`ParticipantBatchImportModal.tsx`)
   - [ ] Add integration tests
   - [ ] Add E2E tests

7. **Testing & Validation**
   - [ ] Test with actual `anmeldungen.csv` file (2,307 participants)
   - [ ] Verify all registrations created correctly
   - [ ] Validate data integrity (100% success rate)
   - [ ] Performance testing (confirm <10 min import time)

---

## Context

Epic 3 was originally planned as a Spring Batch migration, but the team implemented frontend batch import modals instead. They've successfully completed batch imports for:
1. ✅ Companies (`CompanyBatchImportModal.tsx`)
2. ✅ Speakers (`SpeakerBatchImportModal.tsx`)
3. ✅ Events (`EventBatchImportModal.tsx`)
4. ✅ Sessions (`SessionBatchImportModal.tsx`)

The final missing piece is importing participants/attendees from the historical data.

---

## Data Source

**File**: `/Users/nissim/dev/bat/BATbern-main/apps/BATspa-old/src/api/anmeldungen.csv`

**Structure**:
- **Rows**: 2,307 participants (2,308 total including header)
- **Columns**: 62 total
  - `Name`, `FirstName`, `LastName`, `BestMail`, `companyKey` (metadata)
  - Columns `1` through `57` (event participation flags)
  - If column value is "1", participant attended that event number

**Sample Data**:
```csv
Name,FirstName,LastName,BestMail,companyKey,1,2,3,...,57
A Röthlisberger,A,Röthlisberger,a.roethlisberger@alpiq.com,alpiq,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,1,,1,...
```

**Data Quality Issues**:
- Missing emails (~15% of participants)
- German characters (ä, ö, ü, ß) requiring conversion
- Placeholder names (e.g., "Adrian,Adrian,XX")
- BOM character at file start

---

## User Story (Story 3.2)

As an **organizer**, I want to import historical event participation data from a CSV file, so that all past attendees are properly recorded in the system with their event attendance history.

### Business Context

- **Data Source**: Historical CSV with 2,307 participants and 57 events
- **Goal**: Complete Epic 3 historical data migration
- **Value**: Preserve 20+ years of BATbern attendance records

### Acceptance Criteria

1. **CSV Upload**
   - [ ] Organizer can upload CSV file with participant data
   - [ ] System validates CSV structure (62 columns expected)
   - [ ] System shows preview of participants to be imported

2. **User Creation**
   - [ ] Create users with role ATTENDEE if they don't exist (by email)
   - [ ] Use existing users if email already exists (idempotent)
   - [ ] Generate synthetic emails for participants without email: `firstname.lastname@batbern.ch`
   - [ ] Handle German character conversion (ä→ae, ö→oe, ü→ue, ß→ss)

3. **Event Registration**
   - [ ] Create event registrations for all events participant attended
   - [ ] Event codes formatted as `BATbern{N}` (N = 1-57)
   - [ ] Historical registrations marked with status `attended`
   - [ ] Skip participants with no events attended

4. **Progress & Feedback**
   - [ ] Show progress during import (participants processed)
   - [ ] Display success/error/skipped counts
   - [ ] Export failed participants for manual review
   - [ ] Complete import in under 10 minutes

---

## Technical Design

### Backend Batch API

**Endpoint**: `POST /events/batch_registrations`

**Purpose**: Create multiple event registrations for a participant in a single transaction

**Request**:
```json
{
  "participantEmail": "adrian.buerki@centrisag.ch",
  "firstName": "Adrian",
  "lastName": "Bürki",
  "registrations": [
    { "eventCode": "BATbern17", "status": "attended" },
    { "eventCode": "BATbern25", "status": "attended" },
    { "eventCode": "BATbern31", "status": "attended" },
    { "eventCode": "BATbern32", "status": "attended" }
  ]
}
```

**Response**:
```json
{
  "username": "adrian.buerki",
  "totalRegistrations": 4,
  "successfulRegistrations": 4,
  "failedRegistrations": [],
  "errors": []
}
```

**Benefits**:
- 3x reduction in API calls (one batch call instead of N individual calls)
- Transactional safety (all-or-nothing per participant)
- Better error handling (partial success tracking)
- Faster import (8-12 min vs 25-40 min)

**Implementation Notes**:
- Idempotent operation (get-or-create user, skip duplicate registrations)
- Returns detailed results per registration
- Handles event code validation
- Creates user with `cognitoSync=false` (ADR-005)

### Frontend Implementation

Following existing batch import patterns (see `/docs/templates/frontend/batch-import-pattern.md`):

**4-File Architecture**:
1. `ParticipantBatchImportModal.tsx` - Modal UI with CSV dropzone
2. `useParticipantBatchImport.ts` - Business logic hook
3. `participantImport.types.ts` - TypeScript interfaces
4. `participantImportUtils.ts` - CSV parsing with Papa Parse

**Processing Flow**:
1. Parse CSV file (Papa Parse)
2. For each participant:
   - Call `POST /events/batch_registrations`
   - Track progress and errors
3. Invalidate React Query cache after import
4. Display result summary

---

## Data Volume & Performance

**Estimated Load**:
- Participants: 2,307
- Total registrations: ~11,535-23,070 (avg 5-10 events per participant)
- **With batch endpoint**: ~4,600 API calls → 8-12 min
- **Without batch endpoint**: ~25,400 API calls → 25-40 min

**Optimization Strategy**:
- Rate limiting: 10 requests/second
- Sequential processing with progress updates
- Batch endpoint reduces API calls by 3x

---

## Files to Create/Modify

### Backend (Event Management Service)

**New Files**:
- `dto/BatchRegistrationRequest.java` - Request DTO
- `dto/BatchRegistrationItem.java` - Single registration item
- `dto/BatchRegistrationResponse.java` - Response DTO
- `service/BatchRegistrationService.java` - Business logic (optional, or add to RegistrationService)
- `test/BatchRegistrationIntegrationTest.java` - Integration tests

**Modified Files**:
- `controller/EventController.java` - Add batch endpoint
- `docs/api/events-api.openapi.yml` - Add API spec

### Frontend

**New Files**:
- `components/organizer/UserManagement/ParticipantBatchImportModal.tsx`
- `hooks/useParticipantBatchImport/useParticipantBatchImport.ts`
- `types/participantImport.types.ts`
- `utils/participantImportUtils.ts`

**Dependencies**:
```bash
npm install papaparse @types/papaparse
```

---

## Testing Strategy

### Unit Tests
- CSV parsing with valid/invalid data
- Synthetic email generation
- German character conversion (ä→ae, ö→oe, ü→ue, ß→ss)
- Event participation extraction

### Integration Tests
- Batch registration API with PostgreSQL (Testcontainers)
- User creation (get-or-create idempotency)
- Duplicate registration handling
- Partial success scenarios

### E2E Tests
- Full participant import flow
- CSV upload → preview → import → result
- Verify database contains created users + registrations

### Manual Testing
- Test with actual `anmeldungen.csv`
- Verify 2,307 participants imported
- Confirm ~11,500-23,000 registrations created
- Validate <10 minute import time

---

## Success Metrics

- [ ] All 2,307 participants imported successfully
- [ ] ~11,500-23,000 event registrations created
- [ ] Import completes in under 10 minutes
- [ ] 100% data integrity (all CSV rows accounted for)
- [ ] Export available for any failed imports
- [ ] Story 3.2 acceptance criteria 100% complete
- [ ] Epic 3 status updated to 100% complete

---

## Next Steps for Implementation

1. **Backend First** (Recommended):
   - Implement batch registration API
   - Write integration tests
   - Test with Postman/Bruno
   - Update OpenAPI spec

2. **Frontend Second**:
   - Install Papa Parse
   - Create CSV parsing utilities (with tests)
   - Create batch import modal
   - Test with actual CSV file

3. **Integration Testing**:
   - Test full flow end-to-end
   - Performance testing with 2,307 participants
   - Data validation and integrity checks

4. **Documentation**:
   - Update Epic 3 status to 100%
   - Add usage guide for organizers
   - Document any edge cases found

---

## References

- **Epic 3**: `/docs/prd/epic-3-historical-data-migration.md`
- **Story 3.2**: Participant/Attendee Batch Import (in Epic 3)
- **Batch Import Template**: `/docs/templates/frontend/batch-import-pattern.md`
- **CSV Data**: `/apps/BATspa-old/src/api/anmeldungen.csv`
- **Existing Implementations**:
  - `web-frontend/src/components/shared/Company/CompanyBatchImportModal.tsx`
  - `web-frontend/src/components/organizer/UserManagement/SpeakerBatchImportModal.tsx`
  - `web-frontend/src/components/shared/Event/EventBatchImportModal.tsx`
  - `web-frontend/src/components/shared/Session/SessionBatchImportModal.tsx`

---

**Plan Owner**: Development Team
**Estimated Effort**: 2-3 days
**Priority**: High (blocks Epic 3 completion)
