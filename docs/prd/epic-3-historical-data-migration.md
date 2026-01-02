# Epic 3: Historical Data Migration

## Status
🔄 **IN PROGRESS** - Phase 2 Nearly Complete (85%)

**Last Updated:** 2025-12-25

**Progress Summary:**
- ✅ Phase 1: Data Analysis & Mapping - COMPLETE
- 🔄 Phase 2: Migration Implementation - 85% COMPLETE
- ⏳ Phase 3: Validation & Testing - PENDING

**Completed Batch Imports:**
- ✅ Companies (via frontend batch import modal)
- ✅ Speakers (via frontend batch import modal)
- ✅ Events (via frontend batch import modal)
- ✅ Sessions (via frontend batch import modal)
- ⏳ Participants/Attendees (in progress - see Story 3.2 below)

## Epic Overview

**Epic Goal**: Migrate 20+ years of historical BATbern event data from the existing Angular website to the new platform with 100% data integrity, preserving continuity and making the valuable content archive accessible.

**Deliverable**: Complete migration of 54+ historical events, speaker profiles, presentations, and company data from JSON files to PostgreSQL microservices with S3 storage for files.

**Architecture Context**:
- **Migration Tool**: Frontend batch import modals (changed from original Spring Batch approach)
- **Source**: Existing Angular website data (JSON/CSV files, presentations, images)
- **Targets**: Event Management, Speaker Coordination, Company Management Services
- **Storage**: AWS S3 for presentations, photos, and media with CDN
- **Rationale for Frontend Approach**: Simpler, reusable, consistent with existing patterns, better user control

**Duration**: 3 weeks (Weeks 19-21)

**Prerequisites**: Epic 2 complete (all entity CRUD APIs operational and ready to receive data)

---

## Story 3.1: Historical Data Migration Service
**(Formerly Story 1.15)**

**User Story:**
As a **platform stakeholder**, I want all 20+ years of historical BATbern event data migrated accurately, so that the new platform maintains continuity and preserves our valuable content archive.

**Architecture Integration:**
- **Migration Tool**: Dedicated Spring Boot application with Spring Batch
- **Source Data**: Existing Angular application data (JSON, files, images)
- **Target Services**:
  - Event Management Service (events, sessions, timelines)
  - Speaker Coordination Service (speaker profiles, bios, photos)
  - Company Management Service (company affiliations, partner status)
  - Attendee Experience Service (content metadata for search)
- **File Storage**: AWS S3 for all presentations, photos, and documents
- **Validation**: PostgreSQL-based validation reports and integrity checks

**Migration Scope:**
- **54+ Historical Events** from ~2000-2024
- **500+ Speaker Profiles** with photos and CVs
- **1000+ Presentations** (PDF, PPTX files)
- **Company Relationships** and partner status history
- **Photo Galleries** for events
- **Event Metadata** (topics, locations, dates, attendance)

---

### Phase 1: Data Analysis & Mapping (Week 18, Days 1-2)

**Acceptance Criteria:**

**Data Inventory:**
1. **Complete Audit**: Catalog all existing data sources (JSON structure, file locations, image paths)
2. **Data Quality Assessment**: Identify missing data, inconsistencies, duplicates, format issues
3. **Volume Metrics**: Document exact counts (events, speakers, presentations, files, images)
4. **Sample Validation**: Manually verify sample data for correctness

**Domain Mapping:**
5. **Event Mapping**: Map legacy event JSON to Event Management Service schema
6. **Speaker Mapping**: Map speaker profiles to Speaker Coordination Service schema
7. **Company Mapping**: Map company data to Company Management Service schema
8. **File Mapping**: Map file paths to S3 bucket structure and CDN URLs
9. **Relationship Mapping**: Define foreign key relationships across services

**Migration Strategy:**
10. **Incremental Approach**: Define batch processing strategy (events by year, parallel processing)
11. **Rollback Plan**: Document rollback procedures for failed migrations
12. **Validation Strategy**: Define validation rules and acceptance thresholds
13. **Error Handling**: Plan for partial failures, retries, and manual intervention

**Deliverables:**
- [ ] Data inventory spreadsheet with counts and quality assessment
- [ ] Mapping documentation (legacy → new schema for each entity)
- [ ] Migration strategy document with timelines and risk mitigation
- [ ] Sample data validation report (10 events manually verified)

**Estimated Duration:** 2 days

---

### Phase 2: Migration Implementation (Actual: Frontend Batch Import Approach)

**Implementation Approach:**
Instead of Spring Batch, the team implemented **frontend batch import modals** for better user control and consistency with existing patterns.

**Acceptance Criteria:**

**Company Data Migration:** ✅ COMPLETE
1. ✅ **Company Batch Import Modal**: Upload JSON file with company data
2. ✅ **Company Profiles**: Create/update company records in Company Management Service
3. ✅ **Logo Migration**: Upload company logos to S3 with presigned URLs
4. ✅ **Duplicate Detection**: Check existing companies by name before import
5. ✅ **Progress Tracking**: Real-time progress bar with success/error counts

**Speaker Data Migration:** ✅ COMPLETE
6. ✅ **Speaker Batch Import Modal**: Upload JSON file with speaker data
7. ✅ **Speaker Profile Migration**: Create/update user profiles with SPEAKER role
8. ✅ **Photo Migration**: Upload speaker photos to S3 via presigned URLs
9. ✅ **Change Detection**: Identify and update only changed fields
10. ✅ **Company Relationships**: Link speakers to companies by companyId

**Event Data Migration:** ✅ COMPLETE
11. ✅ **Event Batch Import Modal**: Upload JSON file with event data
12. ✅ **Event Migration**: Create/update 54+ historical events in Event Management Service
13. ✅ **Field Selection**: Selectable fields for partial updates (title, description, topic, date, venue, organizer)
14. ✅ **Topic Assignment**: Automatic topic assignment based on event category
15. ✅ **Archived Status**: All historical events marked with `workflowState: ARCHIVED`

**Session Data Migration:** ✅ COMPLETE
16. ✅ **Session Batch Import Modal**: Upload JSON file with session data
17. ✅ **Session Migration**: Create event sessions with speaker assignments
18. ✅ **Batch Processing**: Group sessions by event for efficient API calls
19. ✅ **Speaker References**: Link sessions to speakers via speakerId
20. ✅ **Metadata Migration**: Preserve abstracts, PDFs, and session details

**Participant Data Migration:** ⏳ IN PROGRESS (Story 3.2)
21. ⏳ **Participant Batch Import Modal**: Upload CSV file with participant attendance data
22. ⏳ **User Creation**: Create users with ATTENDEE role (idempotent get-or-create)
23. ⏳ **Event Registrations**: Create registrations for all events attended (status: attended)
24. ⏳ **Batch Registration API**: Backend endpoint for efficient bulk registration creation
25. ⏳ **Synthetic Emails**: Generate emails for participants without email addresses

**Common Features Across All Batch Imports:**
- React dropzone for file upload (JSON/CSV)
- Preview table showing items to import with status indicators
- Duplicate detection and handling (skip or update)
- Sequential processing with progress tracking
- Result summary (success, updated, failed, skipped counts)
- Error export for failed items
- React Query cache invalidation after import

**Deliverables:**
- ✅ Company batch import modal (`CompanyBatchImportModal.tsx`)
- ✅ Speaker batch import modal (`SpeakerBatchImportModal.tsx`)
- ✅ Event batch import modal (`EventBatchImportModal.tsx`)
- ✅ Session batch import modal (`SessionBatchImportModal.tsx`)
- ⏳ Participant batch import modal (`ParticipantBatchImportModal.tsx`)
- ⏳ Batch registration API endpoint (`POST /events/batch_registrations`)
- ⏳ Batch import pattern template (`docs/templates/frontend/batch-import-pattern.md`)

**Actual Duration:** ~6 weeks (distributed across development)

---

### Phase 3: Data Integrity & Validation (Week 19-20, Days 11-15)

**Acceptance Criteria:**

**Referential Integrity:**
1. **Foreign Key Validation**: Verify all event-speaker, speaker-company relationships
2. **Orphan Detection**: Identify orphaned records (speakers without events, etc.)
3. **Duplicate Detection**: Find and resolve duplicate entities
4. **Missing Data Reports**: Generate reports of missing optional data

**File Migration Validation:**
5. **File Count Verification**: Confirm all files migrated (source count = target count)
6. **File Integrity Check**: SHA-256 checksum validation for all files
7. **CDN Access Validation**: Verify all CDN URLs are accessible
8. **S3 Bucket Organization**: Confirm proper S3 key structure and lifecycle policies

**Search Index Building:**
9. **PostgreSQL Full-Text Indexes**: Build search indexes for presentations and events
10. **Speaker Search Index**: Build speaker search indexes
11. **Company Search Index**: Build company autocomplete indexes
12. **Search Validation**: Verify search returns accurate results for sample queries

**Data Validation Reports:**
13. **Migration Success Rate**: Generate report showing success/failure counts per entity type
14. **Data Quality Report**: Document data quality issues found and resolved
15. **Referential Integrity Report**: Confirm 100% referential integrity
16. **Performance Benchmarks**: Confirm migration met <4 hour requirement

**Deliverables:**
- [ ] Data integrity validation report (100% pass rate required)
- [ ] File migration report (all files accounted for)
- [ ] Search index validation report
- [ ] Performance report (migration timing and optimization)

**Estimated Duration:** 5 days (1 week)

---

## Story 3.2: Participant/Attendee Batch Import

**User Story:**
As an **organizer**, I want to import historical event participation data from a CSV file, so that all past attendees are properly recorded in the system with their event attendance history.

**Business Context:**
- **Data Source**: `/apps/BATspa-old/src/api/anmeldungen.csv` - 2,307 participants, 57 events
- **Goal**: Complete the historical data migration by adding participant attendance records
- **Value**: Preserve 20+ years of BATbern attendance history for analytics and attendee tracking

**Acceptance Criteria:**

**CSV Upload & Parsing:**
1. Organizer can upload CSV file via drag-and-drop interface
2. System validates CSV structure (62 columns: 5 metadata + 57 event participation flags)
3. System shows preview table with participant details and event count
4. CSV parser handles edge cases (BOM character, German characters, missing fields)

**User Creation:**
5. Create users with role ATTENDEE if they don't exist (lookup by email)
6. Use existing users if email already exists (idempotent get-or-create)
7. Generate synthetic emails for participants without email: `firstname.lastname@batbern.ch`
8. Convert German characters in names (ä→ae, ö→oe, ü→ue, ß→ss) for email generation
9. Anonymous users created with `cognitoSync=false` (ADR-005 compliance)

**Event Registration Creation:**
10. Create event registrations for all events participant attended (column value = "1")
11. Event codes formatted as `BATbern{N}` where N is the column number (1-57)
12. Historical registrations marked with status `attended`
13. Skip participants with no events attended (all columns empty)
14. Handle duplicate registrations gracefully (skip if already exists)

**Progress & Feedback:**
15. Display progress bar showing participants processed
16. Show real-time statistics (success, partial, failed, skipped counts)
17. Export list of failed participants for manual review
18. Complete full import in under 10 minutes

**Technical Implementation:**

**Frontend Components:**
- `ParticipantBatchImportModal.tsx` - Modal UI with CSV dropzone
- `useParticipantBatchImport.ts` - Business logic hook
- `participantImport.types.ts` - TypeScript interfaces
- `participantImportUtils.ts` - CSV parsing with Papa Parse library

**Backend Batch API (Proposed):**

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

**Benefits of Batch API**:
- **3x reduction in API calls**: One batch call per participant instead of N individual calls
- **Transactional safety**: All-or-nothing per participant (rollback on failure)
- **Better error handling**: Detailed per-registration results with partial success tracking
- **Faster import**: 8-12 minutes (down from 25-40 minutes without batch)
- **Idempotent operation**: Get-or-create user, skip duplicate registrations

**Implementation Files:**
- Frontend: `ParticipantBatchImportModal.tsx`, `useParticipantBatchImport.ts`, utils
- Backend: `BatchRegistrationController.java`, `BatchRegistrationService.java`, DTOs
- API spec: Update `events-api.openapi.yml` with batch registration endpoint

**Data Volume:**
- Participants: 2,307
- Total registrations: ~11,535-23,070 (avg 5-10 events per participant)
- API calls: ~4,600 (with batch endpoint) vs ~25,400 (without)

**Deliverables:**
- [ ] CSV parser handling 62-column format with Papa Parse
- [ ] Batch registration API endpoint in Event Management Service
- [ ] Frontend batch import modal following existing patterns
- [ ] Integration tests for batch registration API
- [ ] E2E test for full participant import flow
- [ ] Template extraction: `docs/templates/frontend/batch-import-pattern.md`
- [ ] All 2,307 participants imported with 100% data integrity

**Success Metrics:**
- [ ] All participants imported successfully
- [ ] ~11,500-23,000 event registrations created
- [ ] Import completes in under 10 minutes
- [ ] 100% data integrity (all CSV rows accounted for)
- [ ] Export available for any failed imports

**Estimated Duration:** 2-3 days

---

### Migration Monitoring & Error Handling

**Real-Time Monitoring:**
1. **Progress Dashboard**: Spring Batch Admin dashboard showing job execution status
2. **ETA Calculations**: Real-time estimates for migration completion
3. **Error Tracking**: Real-time error log with failure reasons
4. **Resource Monitoring**: CPU, memory, network usage during migration

**Error Handling:**
5. **Retry Mechanisms**: Automatic retry for transient failures (3 attempts)
6. **Skip Policy**: Skip records with validation errors, log for manual review
7. **Manual Intervention**: Process for manually correcting and re-running failed records
8. **Rollback Procedures**: Scripts to rollback partially migrated data

**Performance Optimization:**
9. **Batch Size Tuning**: Optimize chunk sizes for performance (100-500 records)
10. **Parallel Processing**: Run independent jobs in parallel (events, speakers, companies)
11. **Database Connection Pooling**: Configure adequate connection pools
12. **S3 Multipart Upload**: Use multipart upload for files >10MB

**Deliverables:**
- [ ] Migration dashboard accessible via web interface
- [ ] Error log with detailed failure reasons
- [ ] Retry and rollback scripts tested and documented
- [ ] Performance optimization report

---

## Epic 3 Success Metrics

**Data Migration Success (End of Week 20):**
- ✅ 100% of 54+ historical events migrated successfully
- ✅ 100% of speaker profiles migrated with photos/CVs
- ✅ 100% of presentations migrated to S3 with CDN URLs
- ✅ 100% referential integrity validation pass rate
- ✅ All company relationships established correctly
- ✅ Search indexes built and verified functional

**Technical Performance:**
- **Migration Time**: Complete migration in <4 hours total runtime
- **Data Integrity**: 100% validation pass rate (zero data loss)
- **File Upload Speed**: >10MB/s average for S3 uploads
- **Error Rate**: <1% requiring manual intervention
- **Search Performance**: Migrated content searchable in <500ms

**Business Value:**
- **Content Continuity**: 20+ years of BATbern history preserved
- **Public Website Ready**: Historical content available for browsing (Epic 4)
- **Search Functional**: Attendees can discover historical presentations
- **Speaker Profiles**: Complete speaker history accessible
- **Archive Complete**: Platform ready for production launch

**Quality Gates:**
- [ ] Zero data loss (all source data accounted for)
- [ ] 100% file integrity (checksums match)
- [ ] All CDN URLs accessible and functional
- [ ] Search returns accurate results for all content types
- [ ] No orphaned records or broken relationships
- [ ] Performance benchmarks met (<4 hour migration)

---

## Dependencies & Prerequisites

**Required Before Starting Epic 3:**
- ✅ Epic 2 complete (all entity CRUD APIs operational)
- ✅ Company Management Service API ready
- ✅ Event Management Service API ready
- ✅ Speaker Coordination Service API ready
- ✅ S3 buckets configured with CDN integration
- ✅ PostgreSQL databases ready for data import
- ✅ Caffeine in-memory caches configured for search

**Enables Following Epics:**
- **Epic 4**: Public Website (requires migrated events and content for display)
- **Epic 5**: Enhanced Organizer Workflows (historical data provides context)
- **Epic 6**: Speaker Portal (speaker history shows past participation)

---

## Risk Management

**Technical Risks:**
- **Risk**: Large file uploads may timeout or fail
  - **Mitigation**: Use multipart upload, implement retry logic, monitor progress
- **Risk**: Data quality issues may require extensive cleanup
  - **Mitigation**: Phase 1 analysis identifies issues early, manual cleanup process
- **Risk**: Referential integrity violations due to inconsistent legacy data
  - **Mitigation**: Fuzzy matching for speakers/companies, manual review queue

**Schedule Risks:**
- **Risk**: Migration taking longer than 3 weeks
  - **Mitigation**: Prioritize event and speaker data first, defer non-critical media
  - **Mitigation**: Parallel processing and performance optimization
- **Risk**: Data quality issues requiring extensive manual intervention
  - **Mitigation**: Accept some data gaps, document for post-launch cleanup

---

## Transition to Epic 4

**Epic 3 Exit Criteria:**
- [ ] All migration batch jobs completed successfully
- [ ] Data integrity validation 100% pass rate
- [ ] All files accessible via CDN URLs
- [ ] Search indexes operational and tested
- [ ] Migration documentation complete
- [ ] Rollback procedures tested

**Handoff to Epic 4 (Public Website):**
- Historical events available via Event Management API
- Event archive searchable via full-text search
- Presentations downloadable via S3/CDN
- Speaker profiles complete with photos
- Platform ready for public website launch

---

## Notes & References

**Original Story Location:**
- Full story details: `docs/prd/epic-1-foundation-stories.md` Story 1.15 (archived)
- Individual story file: `docs/stories/1.15-*.md`

**Data Sources:**
- Existing Angular website data location: [(../../apps/BATspa-old)]
- JSON schema documentation: [(../../apps/BATspa-old/src/api)]
- Sample data for testing: [(../../apps/BATspa-old/src/api)]

**Epic Reorganization Context:**
- Date: 2025-10-12
- Reason: Isolate data migration as dedicated epic after CRUD foundation (Epic 2)
- Dependencies: Requires Epic 2 complete before migration can begin
- Enables: Public website (Epic 4) requires migrated historical data

---

*This epic was created as part of the epic reorganization on 2025-10-12 to prioritize data migration immediately after entity CRUD foundation.*
