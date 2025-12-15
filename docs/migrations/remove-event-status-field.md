# Migration: Remove Event Status Field

**Version:** V17__remove_status_from_events.sql
**Date:** 2025-12-15
**Story:** Event Status Field Cleanup

## Summary

Removed legacy `status` field from events table. The `workflowState` field (16-step Epic 5 workflow) provides comprehensive state tracking with greater detail.

## Rationale

- Technical debt cleanup: `status` was legacy from initial implementation (V2 migration)
- `workflowState` supersedes `status` with more granular state tracking (16 states vs 10 states)
- Maintaining both fields created confusion and potential for out-of-sync states
- Simplifies codebase and reduces redundancy

## Breaking Changes

### API Response
- **Removed:** `Event.status` field from all endpoints
- **Replacement:** Use `Event.workflowState` field instead
- **Impact:** API clients expecting `status` field will receive undefined/null

### Query Parameters
- **Removed:** `?status=...` filter parameter
- **Replacement:** Use `?workflowState=...` instead
- **Impact:** Existing queries using status filter will need to be updated

### Database
- **Removed:** `events.status` column (VARCHAR(50))
- **Impact:** All existing status values permanently dropped (acceptable per product decision)

## Migration Mapping

| Old `status` Value | New `workflowState` Equivalent |
|--------------------|-------------------------------|
| planning | CREATED |
| topic_defined | TOPIC_SELECTION |
| speakers_invited | SPEAKER_OUTREACH |
| agenda_draft | CONTENT_COLLECTION |
| published | AGENDA_PUBLISHED |
| registration_open | NEWSLETTER_SENT |
| registration_closed | EVENT_READY |
| in_progress | EVENT_READY |
| completed | PARTNER_MEETING_COMPLETE |
| archived | ARCHIVED |

## Migration Impact

### Data Loss
- **Status:** All existing `status` values dropped
- **Decision:** Acceptable per product decision - `workflowState` is the source of truth going forward

### API Compatibility
- **Breaking Change:** Clients expecting `status` field will break
- **Mitigation:** Update API clients to use `workflowState` instead
- **Timeline:** Immediate - no deprecation period

### Frontend Changes
- **Components Updated:**
  - EventCard.tsx - now displays workflowState
  - EventForm.tsx - uses workflowState for default values
  - EventDetail.tsx - shows workflowState chip
  - EventDetailEdit.tsx - displays workflowState
- **Default Value:** Changed from `'planning'` to `'CREATED'`
- **Display:** WorkflowState values shown in UPPER_CASE format

### Backend Changes
- **Entity:** Event.java - removed `status` field (line 83-85)
- **DTOs:** Removed from EventResponse, CreateEventRequest, UpdateEventRequest, PatchEventRequest, BatchUpdateRequest
- **Controller:**
  - EventController.publishEvent() - now uses EventWorkflowStateMachine.transitionToState()
  - EventController.advanceWorkflow() - REMOVED (redundant with EventWorkflowController)
- **Tests:** EventControllerIntegrationTest.java - updated all assertions to use workflowState

## Rollback Procedure

If rollback is needed, execute the rollback SQL from V17 migration file:

```sql
ALTER TABLE events ADD COLUMN status VARCHAR(50) DEFAULT 'planning';

ALTER TABLE events ADD CONSTRAINT events_status_check
    CHECK (status IN (
        'planning', 'topic_defined', 'speakers_invited', 'agenda_draft',
        'published', 'registration_open', 'registration_closed',
        'in_progress', 'completed', 'archived'
    ));

CREATE INDEX idx_events_status ON events(status);
```

**Note:** Rollback will restore the column with default value 'planning' for all events. Historical status data cannot be recovered.

## Testing Checklist

- [x] Database migration runs successfully on PostgreSQL
- [x] OpenAPI spec validates successfully
- [x] TypeScript types generate without errors
- [x] Backend unit tests pass
- [ ] Backend integration tests pass (Testcontainers PostgreSQL) - **26 tests failing, see TASK-fix-remaining-status-field-tests.md**
- [x] Frontend types regenerated successfully
- [x] Frontend components updated
- [x] Bruno E2E tests updated and passing
- [ ] Manual testing completed (pending test fixes)
- [x] No grep results for `event.status` in codebase (except comments/docs)
- [x] Architecture documentation updated (removed EventStatus, updated Event schema)
- [ ] All EventControllerIntegrationTest tests pass (pending - filter parameters need updating)
- [ ] SchemaValidationTest passes (pending - schema validation)

## Deployment Notes

1. **Staging Deployment:**
   - Deploy backend first (includes migration)
   - Verify migration runs successfully
   - Deploy frontend
   - Run smoke tests

2. **Production Deployment:**
   - Standard deployment process
   - Migration will run automatically via Flyway
   - **WARNING:** Migration is irreversible - status data will be permanently lost

3. **Monitoring:**
   - Watch for 404/500 errors from API clients expecting status field
   - Monitor frontend error logs for undefined property access

## References

- **Migration File:** `services/event-management-service/src/main/resources/db/migration/V17__remove_status_from_events.sql`
- **Story 5.1a:** Workflow State Machine Foundation (introduced workflowState)
- **Epic 5:** Enhanced Organizer Workflows (16-step workflow)
- **Plan:** `/Users/nissim/.claude/plans/spicy-hugging-hoare.md`
- **Remaining Work:** See `docs/migrations/TASK-fix-remaining-status-field-tests.md` for test fixes needed

## Current Status (2025-12-15)

**Build:** ✅ SUCCESSFUL (Java + Frontend)
**Tests:** ⚠️ 26 failures in EventControllerIntegrationTest (filter parameters need updating)
**Deployment:** ⏸️ BLOCKED until tests pass

All code changes are complete and the application builds successfully. The remaining work is updating test filter parameters from `"status"` to `"workflowState"`. See task file above for step-by-step guide.
