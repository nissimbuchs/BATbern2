# Backend DTO & OpenAPI Migration Plan

**Status**: Draft
**Created**: 2025-01-22
**Related ADRs**: ADR-003 (Meaningful Identifiers), ADR-006 (OpenAPI Contract-First)

## Executive Summary

The backend currently uses **manual DTOs** instead of **generated DTOs** from OpenAPI specs, violating ADR-006. Additionally, both the OpenAPI specs and manual DTOs expose **UUIDs** in the API, violating ADR-003.

This plan outlines a coordinated migration approach that:
1. Fixes ADR-003 violations in OpenAPI specs
2. Migrates to generated DTOs (ADR-006 compliance)
3. Updates frontend to use new type definitions

## Current State Analysis

### Problem 1: Manual DTOs (ADR-006 Violation)

| Service | Generated DTOs | Manual DTOs | Status |
|---------|---------------|-------------|--------|
| company-user-management | Users API | Companies API | Partial |
| event-management | Some (EventType, Outreach) | Topics, Speakers, Events, Sessions | Partial |

### Problem 2: UUIDs in API (ADR-003 Violation)

**OpenAPI Specs with UUIDs:**
- `topics-api.openapi.yml`: 10 UUID fields
- `speakers-api.openapi.yml`: 9 UUID fields
- `events-api.openapi.yml`: 16 UUID fields
- `users-api.openapi.yml`: 1 UUID field (ActivityHistory - documented exception)

**Backend DTOs with UUIDs:**
- `TopicResponse.id`, `TopicResponse.SimilarityScoreDto.topicId`
- `SpeakerStatusResponse.speakerId`
- `SpeakerPoolResponse.id`, `eventId`, `sessionId`
- `EventResponse.topicId`

## Migration Strategy Analysis

### Option A: Fix ADR-003 First, Then Migrate to Generated DTOs
- **Pros**: Clean separation, smaller changes
- **Cons**: Double work (fix manual DTOs, then replace with generated)
- **Verdict**: ❌ Inefficient

### Option B: Migrate to Generated DTOs First, Then Fix ADR-003
- **Pros**: Get code generation working first
- **Cons**: Would need to keep UUIDs in OpenAPI temporarily, then change again
- **Verdict**: ❌ Creates technical debt

### Option C: Fix ADR-003 AND Migrate Together (Recommended)
- **Pros**: Single coordinated change per service, no rework
- **Cons**: Larger scope per story, requires frontend coordination
- **Verdict**: ✅ **Recommended**

### Recommendation

**Fix ADR-003 and migrate to generated DTOs simultaneously, service by service.**

Rationale:
1. OpenAPI spec is the single source of truth - fix it once, correctly
2. Generated DTOs will automatically have correct (meaningful) IDs
3. Frontend regenerates types from same fixed spec
4. Single coordinated change minimizes disruption
5. Can be done incrementally, one API domain at a time

## Implementation Stories

### Story 1: Topics API Migration (Highest Priority)

**Scope**: Topics API domain only
**Estimated Effort**: Medium
**Frontend Impact**: Medium

#### 1.1 OpenAPI Spec Changes (`topics-api.openapi.yml`)

| Current Field | Change To | Rationale |
|---------------|-----------|-----------|
| `Topic.id: uuid` | `Topic.id: string` (topicCode) | Use slug/code like `cloud-architecture-2024` |
| `SimilarityScore.topicId: uuid` | `SimilarityScore.topicId: string` | Reference by topicCode |
| Path `/topics/{id}` | `/topics/{topicCode}` | Meaningful URL |

#### 1.2 Backend Changes

1. Delete manual DTOs:
   - `TopicResponse.java`
   - `TopicRequest.java`
   - `TopicListResponse.java`
   - `TopicFilterRequest.java`
   - `OverrideStalenesRequest.java` (also fixes typo)
   - `TopicUsageHistoryResponse.java`

2. Add to `build.gradle`:
   ```gradle
   task openApiGenerateTopics(type: GenerateTask) {
       inputSpec = "$rootDir/docs/api/topics-api.openapi.yml"
       outputDir = "$buildDir/generated-topics"
       modelPackage = 'ch.batbern.events.dto.generated.topics'
       // ... config
   }
   ```

3. Update `TopicController.java` to use generated DTOs
4. Update `TopicService.java` to map entities to generated DTOs
5. Add `topicCode` field to `Topic` entity (if not exists)

#### 1.3 Frontend Changes

1. Regenerate types: `npm run generate:api-types:topics`
2. Update `TopicBacklogManager` components to use `topicCode` instead of `id`
3. Update API calls to use `/topics/{topicCode}`
4. Update tests

#### 1.4 Acceptance Criteria

- [ ] OpenAPI spec uses `topicCode` (string) not UUID
- [ ] Backend uses generated DTOs from `dto/generated/topics/`
- [ ] No manual Topic DTOs remain in `dto/` folder
- [ ] Frontend uses regenerated types
- [ ] All tests pass
- [ ] Bruno API tests updated

---

### Story 2: Speakers API Migration

**Scope**: Speaker status, pool, and outreach APIs
**Estimated Effort**: Large
**Frontend Impact**: Medium

#### 2.1 OpenAPI Spec Changes (`speakers-api.openapi.yml`)

| Current Field | Change To | Rationale |
|---------------|-----------|-----------|
| `SpeakerStatusResponse.speakerId: uuid` | `speakerUsername: string` | ADR-004: User is source of truth |
| `SpeakerPoolEntry.id: uuid` | Remove or use composite key | `eventCode + username` is unique |
| `SpeakerPoolEntry.eventId: uuid` | `eventCode: string` | Meaningful event identifier |
| `SpeakerPoolEntry.sessionId: uuid` | `sessionCode: string` | Meaningful session identifier |

#### 2.2 Backend Changes

1. Delete manual DTOs:
   - `SpeakerStatusResponse.java`
   - `SpeakerPoolResponse.java`
   - `StatusHistoryItem.java`
   - `StatusSummaryResponse.java`
   - `UpdateStatusRequest.java`
   - `AddSpeakerToPoolRequest.java`

2. Add OpenAPI generation task for speakers API
3. Update controllers and services
4. Ensure `SpeakerPool` entity uses `username` reference (ADR-004)

#### 2.3 Frontend Changes

1. Regenerate types: `npm run generate:api-types:speakers`
2. Update speaker management components
3. Update API calls

#### 2.4 Acceptance Criteria

- [ ] OpenAPI spec uses `username`, `eventCode`, `sessionCode`
- [ ] Backend uses generated DTOs
- [ ] No manual Speaker DTOs remain
- [ ] Frontend uses regenerated types
- [ ] All tests pass

---

### Story 3: Events API Migration

**Scope**: Event, Session, Registration APIs
**Estimated Effort**: Large
**Frontend Impact**: High (core functionality)

#### 3.1 OpenAPI Spec Changes (`events-api.openapi.yml`)

| Current Field | Change To | Rationale |
|---------------|-----------|-----------|
| `EventResponse.topicId: uuid` | `topicCode: string` | Reference by meaningful ID |
| `SessionResponse.id: uuid` | `sessionCode: string` | e.g., `batbern56-session-1` |
| `RegistrationResponse.id: uuid` | `registrationCode: string` | e.g., `REG-2024-001` |

#### 3.2 Backend Changes

1. Delete manual DTOs:
   - `EventResponse.java`
   - `CreateEventRequest.java`
   - `UpdateEventRequest.java`
   - `PatchEventRequest.java`
   - `SessionResponse.java`
   - `CreateSessionRequest.java`
   - `RegistrationResponse.java`
   - `WorkflowStatusDto.java`
   - etc.

2. Update Gradle to generate from events-api.openapi.yml
3. Add meaningful code fields to entities if needed

#### 3.3 Frontend Changes

1. Regenerate types: `npm run generate:api-types:events`
2. Update all event-related components
3. Major testing required

#### 3.4 Acceptance Criteria

- [ ] OpenAPI spec uses meaningful codes
- [ ] Backend uses generated DTOs
- [ ] Frontend uses regenerated types
- [ ] All tests pass

---

### Story 4: Companies API Migration (Lower Priority)

**Scope**: Company management API
**Estimated Effort**: Small
**Frontend Impact**: Low

#### 4.1 Analysis

The Companies API manual DTOs are **already ADR-003 compliant** (use `name` as identifier, no UUIDs exposed). The only issue is they are manual instead of generated.

#### 4.2 Backend Changes

1. Delete manual DTOs:
   - `CompanyResponse.java`
   - `CreateCompanyRequest.java`
   - `UpdateCompanyRequest.java`
   - `CompanySearchResponse.java`
   - `PaginatedCompanyResponse.java`
   - `UIDValidationResponse.java`

2. Verify `companies-api.openapi.yml` matches current DTO structure
3. Add OpenAPI generation task (already configured as `openApiGenerateCompanies`)
4. Update controllers and services to use generated DTOs

#### 4.3 Frontend Changes

1. Regenerate types (already configured)
2. Minimal changes expected (API structure unchanged)

#### 4.4 Acceptance Criteria

- [ ] Backend uses generated DTOs from `dto/generated/companies/`
- [ ] No manual Company DTOs remain
- [ ] All tests pass

---

### Story 5: Users API Cleanup (Optional)

**Scope**: Profile picture upload DTOs
**Estimated Effort**: Small
**Frontend Impact**: Low

The Users API is mostly using generated DTOs. Only a few manual DTOs remain:
- `ProfilePictureUploadRequest.java`
- `ProfilePictureUploadConfirmRequest.java`
- `PresignedUploadUrl.java`

These should be verified against the OpenAPI spec and migrated.

---

## Implementation Order

```
┌─────────────────────────────────────────────────────────────┐
│  Recommended Implementation Sequence                        │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. Story 4: Companies API    ─────► Low risk, practice run │
│     (ADR-003 already OK)                                    │
│                                                             │
│  2. Story 1: Topics API       ─────► Medium scope, isolated │
│     (ADR-003 fix needed)                                    │
│                                                             │
│  3. Story 2: Speakers API     ─────► Larger scope           │
│     (ADR-003 fix needed)                                    │
│                                                             │
│  4. Story 3: Events API       ─────► Largest, core APIs     │
│     (ADR-003 fix needed)                                    │
│                                                             │
│  5. Story 5: Users API        ─────► Cleanup remaining      │
│     (Minor cleanup)                                         │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## Risks and Mitigations

### Risk 1: Breaking API Changes
**Impact**: High
**Mitigation**:
- We are not in production yet, so breaking changes are acceptable
- Coordinate frontend and backend changes in same story
- Update Bruno API tests simultaneously

### Risk 2: Migration Complexity
**Impact**: Medium
**Mitigation**:
- Start with Companies API (smallest, already ADR-003 compliant)
- Learn and refine process before tackling larger APIs

### Risk 3: Generated Code Quality
**Impact**: Low
**Mitigation**:
- OpenAPI Generator is mature and well-tested
- Configuration already proven in existing generated DTOs
- Can customize generation with `configOptions`

### Risk 4: Entity-DTO Mapping Complexity
**Impact**: Medium
**Mitigation**:
- Create dedicated mapper classes (e.g., `TopicMapper.java`)
- Use consistent patterns across all services
- Document mapping patterns in service OPENAPI-CODEGEN.md

## Definition of Done (Per Story)

- [ ] OpenAPI spec updated (ADR-003 compliant, meaningful IDs)
- [ ] Backend uses generated DTOs only (no manual DTOs for that API)
- [ ] Frontend types regenerated and code updated
- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] Bruno API contract tests updated and pass
- [ ] No UUID fields exposed in API responses (except documented exceptions)
- [ ] OPENAPI-CODEGEN.md updated for the service

## Appendix A: UUID Exceptions (ADR-003)

Per ADR-003, some UUIDs are acceptable:
- **Internal audit/activity records**: `ActivityHistory.id` can remain UUID (not referenced cross-service)
- **File upload identifiers**: `fileId` in presigned URL flows (temporary, internal)

## Appendix B: Meaningful ID Patterns

| Entity | Meaningful ID Pattern | Example |
|--------|----------------------|---------|
| Event | `eventCode` | `batbern56` |
| Topic | `topicCode` or slug | `cloud-architecture-patterns` |
| Session | `sessionCode` | `batbern56-keynote-1` |
| Speaker | `username` | `john.doe` |
| Company | `name` (sanitized) | `SwissITSolutions` |
| Registration | `registrationCode` | `REG-2024-00123` |

## Appendix C: Files to Delete (Full List)

### company-user-management-service/src/main/java/ch/batbern/companyuser/dto/
- `CompanyResponse.java`
- `CreateCompanyRequest.java`
- `UpdateCompanyRequest.java`
- `CompanySearchResponse.java`
- `PaginatedCompanyResponse.java`
- `UIDValidationResponse.java`
- `CompanyLogo.java`
- `CompanyStatistics.java`
- `LogoUploadRequest.java`
- `LogoUploadConfirmRequest.java`
- `LogoUploadConfirmResponse.java`

### event-management-service/src/main/java/ch/batbern/events/dto/
- `TopicResponse.java`
- `TopicRequest.java`
- `TopicListResponse.java`
- `TopicFilterRequest.java`
- `OverrideStalenesRequest.java`
- `TopicUsageHistoryResponse.java`
- `TopicUsageHistoryWithEventDetails.java`
- `SpeakerStatusResponse.java`
- `SpeakerPoolResponse.java`
- `StatusHistoryItem.java`
- `StatusSummaryResponse.java`
- `UpdateStatusRequest.java`
- `AddSpeakerToPoolRequest.java`
- `EventResponse.java`
- `CreateEventRequest.java`
- `UpdateEventRequest.java`
- `PatchEventRequest.java`
- `SessionResponse.java`
- `CreateSessionRequest.java`
- `UpdateSessionRequest.java`
- `SessionSpeakerResponse.java`
- `RegistrationResponse.java`
- `CreateRegistrationResponse.java`
- `PatchRegistrationRequest.java`
- `BatchUpdateRequest.java`
- `BatchImportSessionRequest.java`
- `BatchImportSessionResult.java`
- `SessionImportDetail.java`
- `AssignSpeakerRequest.java`
- `SpeakerConfirmationRequest.java`
- `TransitionStateRequest.java`
- `WorkflowStatusDto.java`
