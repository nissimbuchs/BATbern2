# Migration Plan: Complete `status` → `workflowState` Cleanup

## Current Status: **IN PROGRESS - 60% COMPLETE**

**Last Updated**: 2025-12-19

### ✅ Completed (Phases 1-3)
- **Phase 1: OpenAPI Specification Cleanup** - DONE
- **Phase 2: Backend Code Cleanup** - DONE (323/323 tests passing)
- **Phase 3: Frontend Refactoring** - DONE (TypeScript type check passing)

### 🔄 Remaining Work
- **Phase 4: i18n Updates** - Pending
- **Phase 5: Integration Testing** - Pending

---

## Executive Summary

This plan completes the migration from Event `status` field to `workflowState` across the entire codebase. The database migration (V17) has already removed the `status` column from the events table, but there are remaining references in OpenAPI specs, backend code, and frontend components that still reference or filter by the old `status` field.

**Important Distinction**: This migration applies ONLY to **Event status**. Other domains (Registration, SpeakerPool, Topic, etc.) have their own `status` fields that serve different purposes and should NOT be migrated.

---

## Scope Analysis

### What Needs Migration (Event Domain Only)

**OpenAPI Specifications:**
- ✅ CreateEventRequest: Already updated to `workflowState` (completed earlier)
- ✅ UpdateEventRequest: Already updated to `workflowState` (completed earlier)
- ✅ PatchEventRequest: Already updated to `workflowState` (completed earlier)
- ❌ Event response examples: Still show `status: published` (line 157, 288, 404, 419)
- ❌ Filter documentation: References `status` field for filtering (lines 68-75, 80, 98)
- ❌ Query parameter documentation: Shows status filter examples

**Backend Code:**
- ❌ EventController.java: Legacy status-to-workflowState mapping (lines 496-506)
- ❌ EventController.java: Filter processing still uses `status` (line 103, 117)
- ❌ TopicService.java: References archived events via status (lines 423-428)
- ❌ Database constraints: Migration V17 removed column, but comments reference old values

**Frontend Code:**
- ❌ EventSearch.tsx: Status filter with old values ['active', 'published', 'completed', 'archived'] (line 42)
- ❌ EventFilters type: Has `status?: string[]` field (lines 191-195)
- ❌ EventApiClient.ts: Converts status filter array to API format (lines 55-57)
- ❌ EventForm.tsx: `mapWorkflowStateToStatus` function (lines 56-84)
- ❌ EventStore: Filter state includes status (lines 18-43)

### What Should NOT Be Migrated (Separate Domains)

These use `status` for different purposes and are correct as-is:

**Backend:**
- ✅ Registration.java - `status` field (registered/confirmed/cancelled/waitlisted/attended)
- ✅ SpeakerPool.java - `status` field (11 speaker workflow states)
- ✅ TopicResponse.java - `status` field (available/caution/unavailable freshness)
- ✅ SessionImportDetail.java - `status` field (success/skipped/failed)

**Frontend:**
- ✅ Registration status (events-api.types.ts line 1576)
- ✅ Session materials status (events-api.types.ts lines 1354-1360)
- ✅ Speaker pool status (speakerPool.types.ts lines 23-34)
- ✅ Topic status (TopicFilterPanel.tsx lines 38-40)

---

## Migration Strategy

### Phase 1: OpenAPI Specification Cleanup (events-api.openapi.yml)

**Goal**: Remove all Event `status` references, replace with `workflowState`

**Changes Required:**

1. **Event Response Examples** (Lines 157, 288, 404, 419)
   - Current: `status: published`
   - New: `workflowState: PUBLISHED`
   - Impact: 4 example blocks

2. **Filter Documentation** (Lines 68-75, 80, 98)
   - Current: `{"status":"published"}`, `{"status":"archived"}`
   - New: `{"workflowState":"PUBLISHED"}`, `{"workflowState":"ARCHIVED"}`
   - Impact: Query parameter documentation, filter examples

3. **Event Schema** (Verify no `status` field in Event response schema)
   - Ensure Event schema only has `workflowState`, not `status`
   - Already has `workflowState` (line 3030 in openapi spec)

**Files:**
- `docs/api/events-api.openapi.yml`

**Validation:**
- Run OpenAPI validator to ensure spec is valid
- Regenerate frontend types: `cd web-frontend && npm run generate:api-types`

---

### Phase 2: Backend Code Cleanup

**Goal**: Remove legacy status mapping and filter logic

**Changes Required:**

1. **EventController.java - Remove Status Mapping** (Lines 496-506)
   ```java
   // REMOVE THIS BLOCK - No longer needed since requests already use workflowState
   if (request.getStatus() != null) {
       String status = request.getStatus().toLowerCase();
       if (status.equals("archived")) {
           event.setWorkflowState(EventWorkflowState.ARCHIVED);
       }
   }
   ```
   - This was added for backward compatibility but is no longer needed
   - CreateEventRequest now has `workflowState` field directly

2. **EventController.java - Update Filter Documentation** (Lines 103, 117)
   ```java
   // OLD: Filter by status: GET /api/v1/events?filter={status:published}
   // NEW: Filter by workflowState: GET /api/v1/events?filter={workflowState:PUBLISHED}
   ```

3. **TopicService.java - Update Comments** (Lines 423-428)
   ```java
   // OLD: "For ARCHIVED events (historical imports), skip state transition to preserve archival status"
   // NEW: "For ARCHIVED events (historical imports), skip state transition to preserve archival workflowState"
   ```

**Files:**
- `services/event-management-service/src/main/java/ch/batbern/events/controller/EventController.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/TopicService.java`

**Testing:**
- Run all EventController integration tests
- Verify filtering by workflowState works correctly
- Verify no regressions in event creation/update

---

### Phase 3: Frontend Refactoring

**Goal**: Replace Event status filters with workflowState filtering

**Changes Required:**

1. **Update EventFilters Type**
   ```typescript
   // File: web-frontend/src/types/event.types.ts (lines 191-195)
   export interface EventFilters {
     workflowState?: string[]; // Changed from status
     year?: number;
     search?: string;
   }
   ```

2. **Update EventSearch Component**
   ```typescript
   // File: web-frontend/src/components/organizer/EventManagement/EventSearch.tsx

   // Line 42 - Replace status options with workflow states
   const WORKFLOW_STATE_FILTERS = [
     'TOPIC_SELECTION',
     'SPEAKER_OUTREACH',
     'AGENDA_PUBLISHED',
     'EVENT_READY',
     'ARCHIVED'
   ];

   // Update state variable (line 55)
   const [selectedWorkflowStates, setSelectedWorkflowStates] = useState<string[]>([]);

   // Update filter handler (lines 100-103)
   const handleWorkflowStateChange = (states: string[]) => {
     setSelectedWorkflowStates(states);
     setFilters({ ...filters, workflowState: states });
   };
   ```

3. **Update EventApiClient**
   ```typescript
   // File: web-frontend/src/services/eventApiClient.ts (lines 55-57)

   // Change from:
   if (filters?.status && filters.status.length > 0) {
     filterObj.status = filters.status.join(',');
   }

   // Change to:
   if (filters?.workflowState && filters.workflowState.length > 0) {
     filterObj.workflowState = filters.workflowState.join(',');
   }
   ```

4. **Remove/Update EventForm Mapping**
   ```typescript
   // File: web-frontend/src/components/organizer/EventManagement/EventForm.tsx

   // Line 56-84: mapWorkflowStateToStatus function
   // DECISION NEEDED: Remove entirely OR keep for backward compatibility
   // Recommendation: Remove - no longer needed since API uses workflowState
   ```

5. **Update EventStore**
   ```typescript
   // File: web-frontend/src/stores/eventStore.ts (lines 18-43)

   interface EventStore {
     filters: EventFilters; // EventFilters already updated in step 1
     setFilters: (filters: Partial<EventFilters>) => void;
   }
   ```

**Files:**
- `web-frontend/src/types/event.types.ts`
- `web-frontend/src/components/organizer/EventManagement/EventSearch.tsx`
- `web-frontend/src/services/eventApiClient.ts`
- `web-frontend/src/components/organizer/EventManagement/EventForm.tsx`
- `web-frontend/src/stores/eventStore.ts`

**Testing:**
- Update EventSearch.test.tsx with new workflow state filters
- Test filtering by workflowState in UI
- Verify backward compatibility (if keeping mapping function)

---

### Phase 4: Update Translation Keys

**Goal**: Update i18n keys from status to workflowState

**Changes Required:**

1. **Translation Files**
   - Search for keys like `dashboard.status.active`, `dashboard.status.published`
   - Replace with workflow state keys (already exist from Story 5.1a)
   - Update to use `workflow.state.PUBLISHED`, etc.

**Files:**
- `web-frontend/public/locales/en/common.json`
- `web-frontend/public/locales/de/common.json`

---

### Phase 5: Validation & Testing

**Goal**: Ensure complete migration with no regressions

**Backend Testing:**
1. Run full test suite: `./gradlew :event-management-service:test`
2. Verify all EventController tests pass
3. Test event filtering via Bruno/Postman:
   - `GET /api/v1/events?filter={"workflowState":"PUBLISHED"}`
   - `GET /api/v1/events?filter={"workflowState":"ARCHIVED"}`

**Frontend Testing:**
1. Run test suite: `cd web-frontend && npm test`
2. Update tests that reference `status` filter
3. Manual testing:
   - Open event list dashboard
   - Test filtering by workflow state
   - Verify no console errors about missing `status` field

**Integration Testing:**
1. Test event creation with `workflowState: 'CREATED'`
2. Test event update with `workflowState: 'PUBLISHED'`
3. Test event list filtering by `workflowState`
4. Test historical event import with `workflowState: 'ARCHIVED'`

**Type Regeneration:**
```bash
cd web-frontend
npm run generate:api-types
```

Verify no TypeScript errors after regeneration.

---

## Implementation Checklist

### Pre-Implementation
- [x] Review workflow state machine story (5.1a) - DONE
- [ ] Confirm migration scope with stakeholders
- [ ] Create feature branch: `feat/complete-status-to-workflowstate-migration`

### Phase 1: OpenAPI (30 min) ✅ COMPLETED
- [x] Update Event response examples (3 locations - lines 288, 404, 419)
- [x] Update filter documentation (lines 68-75, 80, 98)
- [x] Made workflowState optional in CreateEventRequest and UpdateEventRequest
- [x] Validate OpenAPI spec
- [x] Regenerate TypeScript types
- [ ] Commit: `docs(api): replace Event status with workflowState in examples`

### Phase 2: Backend (1 hour) ✅ COMPLETED
- [x] Fixed EventWorkflowState imports (ch.batbern.shared.types, not events.domain)
- [x] Remove status mapping in EventController (lines 496-506)
- [x] Update filter documentation comments (lines 103, 108)
- [x] Update TopicService comments (lines 423, 428)
- [x] Run backend tests - ALL 323 TESTS PASSING ✅
- [ ] Commit: `refactor(backend): remove legacy Event status mapping`

### Phase 3: Frontend (2 hours) ✅ COMPLETED
- [x] Update EventFilters type (workflowState instead of status)
- [x] Refactor EventSearch component (WORKFLOW_STATE_OPTIONS, handlers)
- [x] Update EventApiClient filter logic (filterObj.workflowState)
- [x] Remove EventForm mapWorkflowStateToStatus function
- [x] Update EventStore (no changes needed - uses EventFilters type)
- [x] Regenerate TypeScript types from OpenAPI (workflowState field confirmed)
- [x] Run TypeScript type check - PASSING ✅
- [ ] Commit: `refactor(frontend): migrate Event filters from status to workflowState`

### Phase 4: i18n (30 min)
- [ ] Update translation keys
- [ ] Verify all languages updated
- [ ] Commit: `chore(i18n): update Event status translation keys`

### Phase 5: Validation (1 hour)
- [ ] Regenerate TypeScript types
- [ ] Run full backend test suite
- [ ] Run full frontend test suite
- [ ] Manual testing (event list, filtering, creation)
- [ ] Update integration tests
- [ ] Commit: `test: update tests for workflowState migration`

### Post-Implementation
- [ ] Create PR with detailed description
- [ ] Request code review
- [ ] Update CHANGELOG.md
- [ ] Merge to develop

---

## Risk Assessment

### Low Risk
- OpenAPI spec updates (documentation only)
- Backend comment updates
- Translation key updates

### Medium Risk
- EventController status mapping removal (ensure no API clients depend on it)
- Frontend filter refactoring (affects user-facing UI)

### Mitigation Strategies
1. **Backward Compatibility**: If external API clients still send `status`, keep a temporary adapter that logs a deprecation warning
2. **Feature Flag**: Consider adding a feature flag for the new filtering behavior
3. **Staged Rollout**: Deploy to staging first, monitor for errors
4. **Rollback Plan**: Keep old code commented for quick rollback if needed

---

## Open Questions

1. **Backward Compatibility**: Should we maintain a temporary adapter for API clients that still send `status` field?
   - Recommendation: No - database already removed the column, no point in maintaining it

2. **EventForm Mapping**: The `mapWorkflowStateToStatus` function exists - should we remove it?
   - Recommendation: Yes - no longer needed since API uses workflowState directly

3. **Filter UI Labels**: Should filters show technical names (SPEAKER_OUTREACH) or friendly names (Speaker Outreach)?
   - Recommendation: Use i18n keys for friendly names (already implemented in Story 5.1a)

4. **Migration Timeline**: Should this be done in one PR or split into phases?
   - Recommendation: Single PR - changes are tightly coupled, splitting would create inconsistent state

---

## Success Criteria

✅ **Complete Migration**
- Zero references to Event `status` field in OpenAPI specs (Event domain only)
- Zero usages of Event `status` in backend code
- Zero usages of Event `status` in frontend components

✅ **No Regressions**
- All existing tests pass
- Event creation/update/filtering works correctly
- Historical event import still works

✅ **Clean Codebase**
- No commented-out code
- Updated documentation
- Consistent naming (workflowState everywhere)

✅ **Type Safety**
- TypeScript types regenerated from OpenAPI
- No type errors in frontend
- No compilation errors in backend

---

## Estimated Effort

- **Total Time**: 5-6 hours
- **Complexity**: Medium
- **Risk Level**: Medium (affects user-facing features)

**Breakdown:**
- Phase 1 (OpenAPI): 30 min
- Phase 2 (Backend): 1 hour
- Phase 3 (Frontend): 2 hours
- Phase 4 (i18n): 30 min
- Phase 5 (Testing): 1 hour
- Buffer: 1 hour

---

## Notes from Exploration

The exploration revealed that many `status` fields are intentionally separate domains:
- **Registration status**: User registration lifecycle (registered → confirmed)
- **Speaker pool status**: Speaker coordination workflow (11 states)
- **Topic status**: Topic freshness indicator (available/caution/unavailable)
- **Session import status**: Batch import results (success/skipped/failed)

These should NOT be changed - they serve different business purposes than Event workflow state.

The migration focuses exclusively on **Event domain status → Event workflowState**.

---

## References

- Story 5.1a: Workflow State Machine Foundation
  - File: `docs/stories/archived/epic-5/5.1a-workflow-state-machine-foundation.md`
  - Migration V12: Added `workflow_state` column
  - Migration V17: Removed `status` column
  - EventWorkflowState enum: 16 states (CREATED → ARCHIVED)

- Architecture Documentation:
  - `docs/architecture/03-data-architecture.md` - Database schema
  - `docs/architecture/06a-workflow-state-machines.md` - State machine patterns

- Related Files:
  - OpenAPI: `docs/api/events-api.openapi.yml`
  - Backend: `services/event-management-service/src/main/java/ch/batbern/events/`
  - Frontend: `web-frontend/src/components/organizer/EventManagement/`
