# BAT-101: Event Management E2E Test Suite Status

## Overview
**Story**: Fix e2e/workflows/events/ tests (2 test files)
**Status**: In Progress - 7/34 tests passing
**Branch**: feature/BAT-93-fix-e2e-test-suite

## Progress Summary

### Tests Passing: 7/34 (20.6%)
- ✅ should_displayEventDashboard_when_navigated
- ✅ should_displayActiveEvents_when_eventsExist (after fixing workflow step count)
- ✅ should_displayProgressPercentage_when_workflowActive
- ✅ should_displayQuickActions_when_dashboardLoaded
- ✅ should_searchEventsByTitle_when_searchInputTyped
- ✅ should_displayTeamActivity_when_activityExists (after adding testids)
- ✅ should_displayCriticalTasks_when_tasksExist (after adding testids)

### Completed Fixes

#### 1. Workflow State Consolidation (Story 5.7)
- **Issue**: Tests expected 16 workflow steps, implementation has 9
- **Fix**: Updated test expectations from `/Step \d+\/16/` to `/Step \d+\/9/`
- **Files**: `event-management-frontend.spec.ts`

#### 2. TextField testid Placement
- **Issue**: testid on TextField wrapper instead of input element
- **Fix**: Changed from `data-testid` prop to `inputProps={{ 'data-testid': '...' }}`
- **Files**: `EventSearch.tsx`

#### 3. Missing data-testid Attributes
Added testids to:
- `EventManagementDashboard.tsx`: `critical-tasks-section`, `team-activity-section`
- `TaskWidget.tsx`: `critical-tasks-list`
- `TaskCard.tsx`: `critical-task-${task.id}`
- `TeamActivityFeed.tsx`: `team-activity-feed`
- `EventForm.tsx`: `create-event-modal`, `event-edit-modal`, `create-event-form`, `event-edit-form`, `save-create-event-button`

#### 4. Linting Fixes
- Fixed unused parameter warnings in `photo-upload.spec.ts`
- Removed unused imports

## Remaining Test Failures (27/34)

### Category 1: Implementation vs Test Expectations Mismatch

#### Edit Form Navigation (5 tests failing)
**Tests**:
- should_openEditForm_when_eventClicked
- should_prefillAllFields_when_editFormOpened
- should_enableAutoSave_when_fieldChanged
- should_displaySavingIndicator_when_autoSaveTriggered
- should_sendPartialUpdate_when_onlyTitleChanged

**Issue**: Tests expect clicking event card to open edit modal
**Reality**: Implementation navigates to detail page (`/organizer/events/${eventCode}`)
**Edit modal opens**: When clicking edit icon button on card hover

**Options**:
1. Update tests to click edit icon instead of card
2. Change implementation to open modal on card click (requires UX decision)

### Category 2: Unimplemented Features

#### Sort Dropdown (2 tests failing)
**Tests**:
- should_sortEventsByDate_when_sortSelected
- should_persistFiltersInURL_when_filtersChanged (partial)

**Issue**: Tests look for `data-testid="sort-dropdown"` that doesn't exist
**Reality**: EventSearch component has no sorting functionality

**Options**:
1. Skip tests until sorting is implemented
2. Implement sorting feature

#### Filter by Generic Status (3 tests failing)
**Tests**:
- should_filterEventsByStatus_when_filterSelected
- should_persistFiltersInURL_when_filtersChanged

**Issue**: Tests look for generic "Published" option
**Reality**: Filter uses specific workflow state names (CREATED, TOPIC_SELECTION, SPEAKER_IDENTIFICATION, etc.)

**Options**:
1. Update tests to use actual workflow state names
2. Add generic status mapping

### Category 3: Create Event Modal Tests (9 tests failing)

**Tests**:
- should_openCreateModal_when_newEventClicked
- should_validateRequiredFields_when_saveClicked
- should_validateEventDate_when_dateTooSoon
- should_validateRegistrationDeadline_when_deadlineTooClose
- should_allowSaveDraft_when_incompleteDataProvided
- should_createEvent_when_validDataProvided
- should_selectEventType_when_typeDropdownClicked
- should_selectVenue_when_venueDropdownClicked

**Status**: Modal now opens (testids added), but tests fail on:
- Form validation behaviors
- Field interactions
- Save functionality

**Likely Issues**:
- Translation keys don't match test expectations
- Form fields have different labels than tests expect
- Validation logic differences

### Category 4: API Tests (event-management-flow.spec.ts)

**Status**: All 19 API tests converted from RED to GREEN phase
- Updated API endpoints from localhost:8080 to localhost:8000
- Updated auth to use global-setup.ts
- Fixed event creation payload to match API schema

## Recommendations

### Immediate (BAT-101 Completion)
1. **Fix navigation mismatch**: Update 5 edit form tests to click edit icon instead of card
2. **Skip unimplemented features**: Mark sort tests as `.skip()` with TODO comments
3. **Update filter tests**: Use actual workflow state names instead of generic "Published"
4. **Debug create modal tests**: Check translation keys and form validation one by one

### Follow-up Stories
1. **Implement sorting**: Add sort dropdown to EventSearch (new story)
2. **UX decision**: Should card click open modal or navigate? (requires PM input)
3. **Test coverage**: Add tests for actual implemented behaviors (edit icon click, etc.)

## Commits Made
1. `fix(e2e): adapt event management tests from RED to GREEN phase`
2. `fix(e2e): add missing data-testid attributes and update workflow step count`
3. `fix(e2e): add data-testid to TaskCard for critical tasks test`
4. `fix(e2e): add data-testid attributes to EventForm modal and buttons`
5. `fix(e2e): correct testid naming for edit form (event-edit-form)`

## Files Modified
- `web-frontend/e2e/workflows/events/event-management-flow.spec.ts`
- `web-frontend/e2e/workflows/events/event-management-frontend.spec.ts`
- `web-frontend/e2e/user-account/photo-upload.spec.ts`
- `web-frontend/src/components/organizer/EventManagement/EventSearch.tsx`
- `web-frontend/src/components/organizer/EventManagement/EventManagementDashboard.tsx`
- `web-frontend/src/components/organizer/EventManagement/TeamActivityFeed.tsx`
- `web-frontend/src/components/organizer/EventManagement/EventForm.tsx`
- `web-frontend/src/components/organizer/EventManagement/EventCard.tsx`
- `web-frontend/src/components/organizer/Tasks/TaskWidget.tsx`
- `web-frontend/src/components/organizer/Tasks/TaskCard.tsx`

## Test Execution
```bash
# Run all event management tests
npx playwright test e2e/workflows/events/event-management-frontend.spec.ts

# Run specific test
npx playwright test e2e/workflows/events/event-management-frontend.spec.ts --grep "should_displayEventDashboard"

# Run API tests
npx playwright test e2e/workflows/events/event-management-flow.spec.ts
```
