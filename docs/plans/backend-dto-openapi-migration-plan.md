# Backend DTO & OpenAPI Migration Plan

**Status**: In Progress
**Created**: 2025-01-22
**Last Updated**: 2025-12-22
**Related ADRs**: ADR-003 (Meaningful Identifiers), ADR-006 (OpenAPI Contract-First)

## Progress Summary

| Story | Status | Commits | Notes |
|-------|--------|---------|-------|
| Story 1: Topics API | 🔶 **IN PROGRESS** | `4141fe8`, `216a966` | Pending: switch to generated DTOs |
| Story 2: Speakers API | 🔲 Not Started | - | - |
| Story 3: Events API | 🔲 Not Started | - | - |
| Story 4: Companies API | 🔲 Not Started | - | - |
| Story 5: Users API | 🔲 Not Started | - | - |

## Standard Migration Sequence (Per Story)

Each story follows this **exact sequence**:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  Step 1: UPDATE OPENAPI SPEC (ADR-003)                                      │
│  - Replace UUIDs with meaningful identifiers (topicCode, eventCode, etc.)   │
│  - Update path parameters (/{id} → /{topicCode})                            │
│  - Add pattern validation (^[a-z0-9-]+$)                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│  Step 2: CONFIGURE CODE GENERATION                                          │
│  - Add openApiGenerate task to build.gradle                                 │
│  - Set generateBuilders: 'true'                                             │
│  - Configure modelPackage: 'ch.batbern.*.dto.generated.*'                   │
├─────────────────────────────────────────────────────────────────────────────┤
│  Step 3: RUN BUILD TO GENERATE DTOs                                         │
│  - ./gradlew :services:<service>:compileJava                                │
│  - Verify generated classes in build/generated-*/                           │
├─────────────────────────────────────────────────────────────────────────────┤
│  Step 4: CREATE MAPPER CLASS                                                │
│  - Create TopicMapper.java (or similar) for entity↔DTO conversion          │
│  - Use generated DTO builders                                               │
│  - Move business logic (colorZone, status) to mapper                        │
├─────────────────────────────────────────────────────────────────────────────┤
│  Step 5: UPDATE BACKEND TO USE GENERATED DTOs                               │
│  - Update imports: ch.batbern.*.dto.* → ch.batbern.*.dto.generated.*        │
│  - Update controller to use mapper + generated DTOs                         │
│  - Update service layer if needed                                           │
├─────────────────────────────────────────────────────────────────────────────┤
│  Step 6: DELETE MANUAL DTOs                                                 │
│  - Remove old manual DTO files                                              │
│  - Verify no remaining imports                                              │
├─────────────────────────────────────────────────────────────────────────────┤
│  Step 7: UPDATE ENTITY (if needed)                                          │
│  - Add meaningful ID field (topicCode, eventCode)                           │
│  - Create database migration                                                │
│  - Add repository methods                                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│  Step 8: REGENERATE FRONTEND TYPES                                          │
│  - npm run generate:api-types                                               │
│  - Fix TypeScript errors in components                                      │
├─────────────────────────────────────────────────────────────────────────────┤
│  Step 9: RUN TESTS & COMMIT                                                 │
│  - Backend: ./gradlew test                                                  │
│  - Frontend: npm test                                                       │
│  - Update Bruno API tests                                                   │
│  - Commit changes                                                           │
└─────────────────────────────────────────────────────────────────────────────┘
```

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

### Story 1: Topics API Migration 🔶 IN PROGRESS

**Status**: 🔶 **IN PROGRESS** - Steps 1-2, 7-8 complete; Steps 3-6 pending
**Scope**: Topics API domain only
**Actual Effort**: Medium
**Frontend Impact**: Medium

**Commits**:
- `4141fe8` - feat(api): migrate Topics API from UUID to topicCode (ADR-003)
- `216a966` - feat(topics): migrate backend to topicCode identifier (ADR-003)

**Progress by Step**:
| Step | Description | Status |
|------|-------------|--------|
| 1 | Update OpenAPI spec | ✅ Done |
| 2 | Configure code generation | ✅ Done |
| 3 | Run build to generate DTOs | 🔲 Pending (requires network) |
| 4 | Create TopicMapper class | 🔲 Pending |
| 5 | Update backend to use generated DTOs | 🔲 Pending |
| 6 | Delete manual DTOs | 🔲 Pending |
| 7 | Update entity (topicCode field) | ✅ Done |
| 8 | Regenerate frontend types | ✅ Done |
| 9 | Run tests & commit | 🔲 Pending |

#### 1.1 OpenAPI Spec Changes (`topics-api.openapi.yml`) ✅

| Current Field | Changed To | Status |
|---------------|-----------|--------|
| `Topic.id: uuid` | `Topic.topicCode: string` | ✅ Done |
| `SimilarityScore.topicId: uuid` | `SimilarityScore.topicCode: string` | ✅ Done |
| `SelectTopicForEventRequest.topicId: uuid` | `SelectTopicForEventRequest.topicCode: string` | ✅ Done |
| Path `/topics/{id}` | `/topics/{topicCode}` | ✅ Done (6 paths) |
| Pattern validation | `^[a-z0-9-]+$` | ✅ Added |

#### 1.2 Backend Changes ✅

1. ✅ **Added OpenAPI code generation** to `build.gradle`:
   ```gradle
   task openApiGenerateTopics(type: GenerateTask) {
       inputSpec = "$rootDir/docs/api/topics-api.openapi.yml"
       outputDir = "$buildDir/generated-topics"
       modelPackage = 'ch.batbern.events.dto.generated.topics'
       generateBuilders = 'true'  // As requested by user
   }
   ```

2. ✅ **Added `topicCode` field to Topic entity**:
   - New column: `topic_code VARCHAR(255) UNIQUE NOT NULL`
   - Auto-generated from title via `generateTopicCode(title)`
   - Database migration: `V24__add_topic_code_column.sql`

3. ✅ **Updated TopicRepository**:
   - Added `findByTopicCode(String topicCode)`
   - Added `existsByTopicCode(String topicCode)`
   - Added `findByTitleIgnoreCase(String title)`

4. ✅ **Updated TopicController** (all endpoints now use topicCode):
   - `GET /topics/{topicCode}` - getTopicByCode()
   - `PUT /topics/{topicCode}` - updateTopic()
   - `DELETE /topics/{topicCode}` - deleteTopic()
   - `PUT /topics/{topicCode}/override-staleness` - overrideStaleness()
   - `GET /topics/{topicCode}/similar` - getSimilarTopics()
   - `GET /topics/{topicCode}/usage-history` - getUsageHistory()

5. ✅ **Updated TopicService**:
   - Added `getTopicByCode()`, `updateTopicByCode()`, `deleteTopicByCode()`
   - Added `overrideStalenessByCode()`, `getSimilarTopicsByCode()`
   - Added `getUsageHistoryWithEventDetailsByCode()`
   - Refactored `enrichTopicsWithUsageHistory()` to take Topic entities

6. ✅ **Updated TopicResponse DTO**:
   - Changed `UUID id` → `String topicCode`
   - Changed `SimilarityScoreDto.topicId` → `SimilarityScoreDto.topicCode`
   - Note: Manual DTOs kept temporarily until build verification (network blocked)

#### 1.3 Frontend Changes ✅

1. ✅ Regenerated types: `npm run generate:api-types`
2. ✅ Updated components to use `topicCode`:
   - `CreateTopicModal.tsx`
   - `TopicBacklogManager.tsx`
   - `TopicDetailsPanel.tsx`
   - `TopicList.tsx`
   - `MultiTopicHeatMap.tsx`
   - `useTopics.ts`
   - `topicLookup.ts`
3. ✅ All 2777 frontend tests pass

#### 1.4 Acceptance Criteria

- [x] OpenAPI spec uses `topicCode` (string) not UUID
- [x] Backend OpenAPI code generation configured with `generateBuilders: 'true'`
- [x] Topic entity has `topicCode` field with auto-generation
- [x] Database migration adds `topic_code` column
- [x] All controller endpoints use `{topicCode}` path parameter
- [x] Frontend uses regenerated types
- [x] Frontend tests pass (2777 tests)
- [ ] Backend tests pass (blocked by network - Gradle download)
- [ ] Bruno API tests updated
- [ ] Manual DTOs deleted (pending build verification)

#### 1.5 Remaining Work (To Complete Locally)

**Step 3: Run build to generate DTOs**
```bash
./gradlew :services:event-management-service:compileJava
# Verify: ls services/event-management-service/build/generated-topics/src/main/java/ch/batbern/events/dto/generated/topics/
```

**Step 4: Create TopicMapper class**
```java
// services/event-management-service/src/main/java/ch/batbern/events/mapper/TopicMapper.java
@Component
public class TopicMapper {
    public Topic toDto(ch.batbern.events.domain.Topic entity) {
        return Topic.builder()
            .topicCode(entity.getTopicCode())
            .title(entity.getTitle())
            // ... use builders from generated DTO
            .build();
    }
}
```

**Step 5: Update backend imports**
- Change imports in TopicController, TopicService from `ch.batbern.events.dto.*` to `ch.batbern.events.dto.generated.topics.*`
- Use TopicMapper for entity↔DTO conversion

**Step 6: Delete manual DTOs**
```bash
rm services/event-management-service/src/main/java/ch/batbern/events/dto/TopicResponse.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/TopicRequest.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/TopicListResponse.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/TopicFilterRequest.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/OverrideStalenesRequest.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/TopicUsageHistoryResponse.java
rm services/event-management-service/src/main/java/ch/batbern/events/dto/TopicUsageHistoryWithEventDetails.java
```

**Step 9: Run tests**
```bash
./gradlew :services:event-management-service:test
cd web-frontend && npm test
```

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

## Appendix D: Field Reconciliation (OpenAPI vs Manual DTO)

**CRITICAL**: Before generating DTOs, the OpenAPI spec must be reconciled with manual DTOs.
The OpenAPI spec becomes the source of truth - any discrepancy must be resolved first.

### D.1 Topics API Reconciliation ✅ COMPLETED

**Status**: ✅ Reconciled and migrated (2025-12-22)

#### CreateTopicRequest

| Field | OpenAPI Spec | Manual DTO | Resolution |
|-------|--------------|------------|------------|
| `title` | ✅ string, required | ✅ string, @NotBlank | ✅ Aligned |
| `description` | ✅ string | ✅ string, max 5000 | ✅ Aligned |
| `category` | ✅ string, required | ✅ string, @NotBlank | ✅ Aligned |
| `keywords` | ~~string[]~~ | ❌ **MISSING** | ✅ **Removed from spec** (not implemented) |
| `relatedTopics` | ~~uuid[]~~ | ❌ **MISSING** | ✅ **Removed from spec** (not implemented) |

**Resolution**: Removed `keywords` and `relatedTopics` from OpenAPI spec during field reconciliation (commit `cd55b7d`). These can be re-added when the feature is implemented.

#### TopicResponse (Topic schema)

| Field | OpenAPI Spec | Manual DTO | Resolution |
|-------|--------------|------------|------------|
| `id` → `topicCode` | ~~uuid~~ → string | UUID → String | ✅ **Migrated to topicCode (ADR-003)** |
| `title` | string | String | ✅ Aligned |
| `description` | string | String | ✅ Aligned |
| `category` | string | String | ✅ Aligned |
| `createdDate` | date-time | LocalDateTime | ✅ Aligned |
| `lastUsedDate` | date-time | LocalDateTime | ✅ Aligned |
| `usageCount` | integer | Integer | ✅ Aligned |
| `stalenessScore` | integer | Integer | ✅ Aligned |
| `colorZone` | enum | String | ✅ Aligned (enum values match) |
| `status` | enum | String | ✅ Aligned (enum values match) |
| `similarityScores` | array | List<SimilarityScoreDto> | ✅ Aligned |
| `similarityScores[].topicId` → `topicCode` | ~~uuid~~ → string | UUID → String | ✅ **Migrated to topicCode (ADR-003)** |
| `active` | boolean | Boolean | ✅ Aligned |
| `createdAt` | date-time | LocalDateTime | ✅ Aligned |
| `updatedAt` | date-time | LocalDateTime | ✅ Aligned |
| `usageHistory` | array | List<TopicUsageHistoryResponse> | ✅ Aligned |

#### SelectTopicForEventRequest

| Field | OpenAPI Spec | Manual DTO | Resolution |
|-------|--------------|------------|------------|
| `topicId` → `topicCode` | ~~uuid~~ → string | N/A | ✅ **Migrated to topicCode (ADR-003)** |
| `justification` | string | N/A | ✅ Aligned |

#### Path Parameters

| Path | Changed From | Changed To | Status |
|------|--------------|------------|--------|
| `/topics/{id}` | `{id}: uuid` | `{topicCode}: string` | ✅ Done |
| `/topics/{id}/similar` | `{id}: uuid` | `{topicCode}: string` | ✅ Done |
| `/topics/{id}/usage-history` | `{id}: uuid` | `{topicCode}: string` | ✅ Done |
| `/topics/{id}/override-staleness` | `{id}: uuid` | `{topicCode}: string` | ✅ Done |

---

### D.2 Companies API Reconciliation

#### CreateCompanyRequest

| Field | OpenAPI Spec | Manual DTO | Decision Required |
|-------|--------------|------------|-------------------|
| `name` | string, pattern `^[A-Za-z0-9]+$`, required | String, @NotBlank | ⚠️ **DTO allows spaces, OpenAPI doesn't** |
| `displayName` | string, max 255 | String, max 255 | ✅ Aligned |
| `swissUID` | string, pattern | String, @Pattern | ✅ Aligned |
| `website` | uri, max 500 | String, max 500 | ✅ Aligned |
| `industry` | string, max 100 | String, max 100 | ✅ Aligned |
| `description` | string, **max 5000** | String, **max 2000** | ⚠️ **Mismatch: 5000 vs 2000** |
| `logoUploadId` | ❌ **MISSING** | ✅ String | ⚠️ **Add to OpenAPI spec** |

**Recommendation**:
- Add `logoUploadId` to OpenAPI spec (it's used in the workflow)
- Decide on `description` maxLength: recommend 5000 (more generous)
- Fix `name` pattern: OpenAPI is stricter (alphanumeric only)

#### UpdateCompanyRequest

| Field | OpenAPI Spec | Manual DTO | Decision Required |
|-------|--------------|------------|-------------------|
| Same fields as Create | ... | ... | Same issues |
| `logoUploadId` | ❌ **MISSING** | ✅ String | ⚠️ **Add to OpenAPI spec** |

---

### D.3 Speakers API Reconciliation

#### SpeakerStatusResponse

| Field | OpenAPI Spec | Manual DTO | Decision Required |
|-------|--------------|------------|-------------------|
| `speakerId` | uuid | UUID | ⚠️ **Change to speakerUsername (ADR-003/004)** |
| `eventCode` | string | String | ✅ Aligned |
| `status` | enum | String | ⚠️ **Verify enum values match** |
| `updatedAt` | date-time | LocalDateTime | ✅ Aligned |

#### SpeakerPoolResponse

| Field | OpenAPI Spec | Manual DTO | Decision Required |
|-------|--------------|------------|-------------------|
| `id` | uuid | UUID | ⚠️ **Remove or use composite key (ADR-003)** |
| `eventId` | uuid | UUID | ⚠️ **Change to eventCode (ADR-003)** |
| `speakerName` | string | String | ✅ Aligned |
| `company` | string | String | ✅ Aligned |
| `status` | enum | String | ⚠️ **Verify enum values match** |
| `sessionId` | uuid | UUID | ⚠️ **Change to sessionCode (ADR-003)** |
| `notes` | string | String | ✅ Aligned |

---

### D.4 Events API Reconciliation

#### EventResponse

| Field | OpenAPI Spec | Manual DTO | Decision Required |
|-------|--------------|------------|-------------------|
| `eventCode` | string | String | ✅ Aligned (already meaningful ID) |
| `topicId` | uuid | UUID | ⚠️ **Change to topicCode (ADR-003)** |
| `name` | string | String | ✅ Aligned |
| `description` | string | String | ✅ Aligned |
| `eventDate` | date | LocalDate | ✅ Aligned |
| `status` | enum | String/Enum | ⚠️ **Verify enum values match** |
| `workflowState` | enum | Enum | ✅ Aligned |
| ... | ... | ... | Continue verification |

---

## Appendix E: Pre-Migration Checklist

Before starting each story, complete this checklist:

### For Each API Domain:

- [ ] **Field-by-field comparison** completed (use Appendix D as template)
- [ ] **Decisions documented** for each discrepancy:
  - [ ] Missing fields: Add to OpenAPI or confirm not needed?
  - [ ] Extra fields in DTO: Add to OpenAPI?
  - [ ] Type mismatches: Which is correct?
  - [ ] Validation differences: Which constraints apply?
- [ ] **ADR-003 violations identified** and new meaningful IDs designed
- [ ] **Entity changes planned** (add new code fields if needed)
- [ ] **Frontend impact assessed** (which components use these fields?)

### OpenAPI Spec Updates (Before Generation):

- [ ] All UUIDs replaced with meaningful string IDs
- [ ] All missing fields from manual DTOs added
- [ ] All validation constraints aligned
- [ ] Enum values match between spec and implementation
- [ ] Path parameters updated (e.g., `/{id}` → `/{topicCode}`)

### After OpenAPI Updates:

- [ ] Regenerate frontend types and verify no TypeScript errors
- [ ] Regenerate backend DTOs and verify no compilation errors
- [ ] Update controllers to use new DTOs
- [ ] Update services to map entities to new DTOs
- [ ] Update tests to use new field names
