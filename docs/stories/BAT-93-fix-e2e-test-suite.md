# Story BAT-93: Fix E2E Test Suite - Align Tests with Current Implementation

**Linear Issue**: [BAT-93](https://linear.app/batbern/issue/BAT-93/fix-e2e-test-suite-align-tests-with-current-implementation)

## Status
In Progress

## Story

**As a** developer,
**I want** all E2E tests to pass and accurately reflect the current implementation,
**so that** we have confidence in our test suite and can catch real regressions.

## Overview

Fix all E2E tests to align with the current implementation. The application is working correctly in production - tests need to be adapted to match the actual behavior.

## Key Principles

1. **Implementation is Source of Truth**: The current frontend/backend implementation is correct and working. Tests must be adapted to match it, not vice versa.

2. **Use `data-testid` Attributes**: Replace language-dependent selectors (e.g., `:has-text("New Event")`) with `data-testid` selectors where possible. Add `data-testid` attributes to components if missing.

3. **Language-Independent Selectors**: Where `data-testid` isn't possible, use ARIA attributes (`role`, `aria-label`) or structural selectors that don't depend on UI text.

4. **Fix Test Logic**: Update test expectations to match actual API responses, component behavior, and MUI patterns (e.g., Select uses `role="button"`, not `role="combobox"`).

## Test Groups (15 tasks)

See Linear sub-tasks for detailed breakdown:

- ✅ **organizer/** - Event type selection (1/5 tests passing)
- 🔄 **accessibility/** - Layout, navigation, screen reader (3 tests)
- 🔄 **api-integration/** - API contracts, CORS (2 tests)
- 🔄 **auth/** - Password reset flows (2 tests)
- 🔄 **user-account/** - Profile, settings, photo upload (3 tests)
- 🔄 **workflows/company-management/** - Company CRUD (2 tests)
- 🔄 **workflows/documentation/** - Screenshot generation (3 tests)
- 🔄 **workflows/events/** - Event management flows (2 tests)
- 🔄 **workflows/participant-import/** - Batch import (1 test)
- 🔄 **workflows/partner-management/** - Partner CRUD (2 tests)
- 🔄 **workflows/progressive-publishing/** - Publishing workflow (1 test)
- 🔄 **workflows/slot-assignment/** - Slot assignment (1 test)
- 🔄 **workflows/user-management/** - User CRUD and roles (4 tests)
- 🔄 **workflows/user-sync/** - Cognito sync (3 tests)
- 🔄 **Root level tests** - Task assignment/creation (2 tests)

## Success Criteria

- All E2E tests pass on chromium and Mobile Safari
- Tests use `data-testid` selectors where possible
- Tests are resilient to language changes
- No false negatives (tests fail when app actually works)

## Related Work

- Branch: `refactor/fix-e2e-tests`
- Recent fixes: Route changes, port updates, language configuration, global auth setup

## Dev Agent Record

### Agent Model Used
Claude Sonnet 4.5

### Status
In Progress - BAT-94 (organizer tests) partially complete

### Current Session Progress (2026-01-17)

**BAT-94: Fix e2e/organizer/ tests (5 test files)**

**Completed:**
1. ✅ Fixed backend service startup issue (event-management-service missing COGNITO_USER_POOL_ID)
2. ✅ Added `data-testid` attributes to QuickActions component (5 buttons)
3. ✅ Added `data-testid` attributes to EventTypeSelector menu options (3 types)
4. ✅ Updated `speaker-brainstorming.spec.ts` helper function with language-independent selectors
5. ✅ Added proper wait conditions for dashboard/modal loading
6. ✅ Committed changes: `fd54642b`

**Components Modified:**
- `web-frontend/src/components/organizer/EventManagement/QuickActions.tsx`
- `web-frontend/src/components/organizer/EventTypeSelector/EventTypeSelector.tsx`
- `web-frontend/e2e/organizer/speaker-brainstorming.spec.ts`

**Pattern Established (Reusable):**
1. Identify language-dependent selectors: `grep ":has-text(" test-file.spec.ts`
2. Add `data-testid="descriptive-name"` to component
3. Replace `:has-text()` with `[data-testid="..."]` in test
4. Add wait conditions for dynamic content
5. Commit with clear message

**Remaining Work for BAT-94:**
- [ ] `speaker-outreach.spec.ts` (15 tests) - Apply same pattern
- [ ] `speaker-status-tracking.spec.ts` - Apply same pattern
- [ ] `topic-selection.spec.ts` - Apply same pattern
- [ ] `event-type-selection.spec.ts` (18 tests) - Cleanup remaining `:has-text()` selectors

**Important Notes:**
- Some tests may be intentionally RED phase (unimplemented features - speaker brainstorming/outreach)
- Focus on fixing language-dependency issues first
- Document which features are unimplemented vs. broken tests

**How to Continue:**
1. Check backend health: `curl 'http://localhost:8000/api/v1/events?page=1&limit=10'`
2. Pick next test file: `npx playwright test e2e/organizer/[file].spec.ts --max-failures=1`
3. Apply established pattern (see Linear comment for details)
4. Commit each file separately

### File List
- `docs/stories/BAT-93-fix-e2e-test-suite.md` (this file)
- `web-frontend/src/components/organizer/EventManagement/QuickActions.tsx`
- `web-frontend/src/components/organizer/EventTypeSelector/EventTypeSelector.tsx`
- `web-frontend/e2e/organizer/speaker-brainstorming.spec.ts`
- See Linear sub-tasks (BAT-94 through BAT-108) for remaining files

### Change Log
- 2026-01-17: BAT-94 session - Fixed backend startup, added data-testid to QuickActions/EventTypeSelector, updated speaker-brainstorming test helpers. Commit: fd54642b
- 2026-01-10: Story created with 15 sub-tasks for E2E test groups
