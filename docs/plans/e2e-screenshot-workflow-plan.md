# Implementation Plan: E2E Workflow Test with Screenshot Capture for User Guide

**Status**: Phases 1-4 Complete ✅ | Phase 5 In Progress 🔄

**Last Updated**: 2026-01-09

---

## Current Status

### ✅ Phase 1: Test Infrastructure Setup - COMPLETE

**Completed**: 2026-01-08

**Files Created** (10):
1. ✅ `web-frontend/e2e/workflows/documentation/complete-event-workflow.spec.ts` - Main test spec
2. ✅ `web-frontend/e2e/workflows/documentation/test-data.config.ts` - Centralized test data
3. ✅ `web-frontend/e2e/workflows/documentation/helpers/api-helpers.ts` - API utilities (read-only)
4. ✅ `web-frontend/e2e/workflows/documentation/helpers/screenshot-helpers.ts` - Screenshot capture with phase prefixes
5. ✅ `web-frontend/e2e/workflows/documentation/helpers/cleanup-helpers.ts` - Cleanup utilities
6. ✅ `web-frontend/e2e/workflows/documentation/page-objects/EventWorkflowPage.ts` - Event POM
7. ✅ `web-frontend/e2e/workflows/documentation/page-objects/SpeakerManagementPage.ts` - Speaker POM
8. ✅ `web-frontend/e2e/workflows/documentation/page-objects/PublishingPage.ts` - Publishing POM
9. ✅ `web-frontend/e2e/workflows/documentation/page-objects/TopicSelectionPage.ts` - Topic POM
10. ✅ `web-frontend/e2e/workflows/documentation/README.md` - Documentation

**Files Modified** (2):
1. ✅ `web-frontend/playwright.config.ts` - Added documentation-screenshots project
2. ✅ `web-frontend/package.json` - Added `test:e2e:docs` and `test:e2e:docs:ui` scripts

### ✅ Phase 2: Workflow Discovery & Recording - COMPLETE

**Completed**: 2026-01-09

**Recording Sessions**:
1. ✅ Phase A: Event Setup (creation, task assignment, topic selection, speaker brainstorming)
2. ✅ Phase B: Speaker Outreach (Kanban board workflow)
3. ✅ Phase B.5: Content Submission (topic/speaker publishing, content forms)
4. ✅ Phase C: Quality Review (speaker publishing, quality review drawer)
5. ✅ Phase D: Slot Assignment & Publishing (sessions view, auto-assign, publish agenda)
6. ✅ Phase E: Archival (edit modal, status change, validation override)

**Output**: Complete workflow understanding documented in test spec

### ✅ Phase 3: Workflow Test Implementation - COMPLETE

**Completed**: 2026-01-09

**Test Phases Implemented**:
1. ✅ Phase A: Event Setup (~20 screenshots)
2. ✅ Phase B: Speaker Outreach (~15 screenshots)
3. ✅ Phase B.5: Content Submission (~6 screenshots)
4. ✅ Phase C: Quality Review (~6 screenshots)
5. ✅ Phase D: Slot Assignment & Publishing (~10 screenshots)
6. ✅ Phase E: Archival (~8 screenshots)

**Total Screenshots**: 77 screenshots captured across 6 phases

**Test Runner**: `run-phases-a-b-c-d-e-autologin.sh` - Automated test execution with screenshot extraction

**Screenshot Naming**: Phase-prefixed format (a-01-, b-02-, b5-01-, c-01-, d-01-, e-01-)

### ✅ Phase 4: User Guide Integration - COMPLETE

**Completed**: 2026-01-09

**Strategy**: Curated, value-added screenshots only (~24 of 77 total)

**Files Updated** (6):
1. ✅ `docs/user-guide/workflow/phase-a-setup.md` - Added 6 key screenshots
2. ✅ `docs/user-guide/workflow/phase-b-outreach.md` - Added 4 screenshots + 3 for Phase B.5
3. ✅ `docs/user-guide/workflow/phase-c-quality.md` - Added 3 screenshots
4. ✅ `docs/user-guide/workflow/phase-d-assignment.md` - Added 4 screenshots, updated to match implementation
5. ✅ `docs/user-guide/workflow/phase-e-publishing.md` - Restructured with archival section + 4 screenshots
6. ✅ `docs/user-guide/appendix/screenshot-index.md` - Complete screenshot catalog

**Documentation Updates**:
- Updated status tags from "planned" to "implemented" where applicable
- Replaced theoretical content with actual workflow steps
- Removed unimplemented features from main documentation
- Added practical troubleshooting based on actual implementation

### 🔄 Phase 5: Documentation & Polish - IN PROGRESS

**Status**: Final cleanup and documentation updates

**Remaining Tasks**:
1. ⏳ Update this plan document (in progress)
2. ⏳ Update `web-frontend/e2e/workflows/documentation/README.md`
3. ⏳ Final review and commit

---

## Overview

Create a comprehensive Playwright E2E test that executes the complete BATbern event management workflow (CREATED → ARCHIVED) while capturing 50-80 screenshots for user guide documentation. The test will cover three parallel workflows: 9-step event management, 11-step speaker management, and 4-step progressive publishing.

## User Decisions

Based on user clarification:
- **Test Type**: Integrated E2E test (validates functionality AND captures screenshots)
- **Screenshot Detail**: Comprehensive (every screen and major UI state, ~50-80 screenshots)
- **Test Data**: Use existing seed data for companies/speakers/topics/users; create fresh data for events/assignments/slots **via frontend UI**
- **Organization**: By workflow phase (Event Setup, Speaker Outreach, Publishing, etc.)
- **UI-First Approach**: All workflow actions performed through frontend (no API shortcuts for event creation/management)

## Implementation Phases

### Phase 1: Test Infrastructure Setup (1-2 days) ✅ COMPLETE

#### Files to Create

**1. Main Test File**
- Path: `/Users/nissim/dev/bat/BATbern-feature/web-frontend/e2e/workflows/documentation/complete-event-workflow.spec.ts`
- Purpose: Orchestrate complete workflow execution with screenshot capture
- Pattern: Follow existing test pattern from `e2e/workflows/events/event-management-flow.spec.ts`
- Structure: 6 test cases (one per workflow phase A-F)
- Status: ✅ Created (skeleton)

**2. API Helpers**
- Path: `/Users/nissim/dev/bat/BATbern-feature/web-frontend/e2e/workflows/documentation/helpers/api-helpers.ts`
- Purpose: **Read-only operations and cleanup ONLY** (not for creating workflow data)
- **CRITICAL**: All workflow actions must go through frontend UI (event creation, speaker assignment, etc.)
- Key functions:
  - `getSeedData(token)` - Fetches existing companies/speakers/topics/users (read-only)
  - `verifyEventState(token, eventCode, expectedState)` - Verify backend state after UI action
  - `cleanup(token, eventCode)` - Delete test event after test completion
- **NOT INCLUDED**: createTestEvent, assignSpeaker, advanceWorkflowState (these happen via frontend UI)
- Status: ✅ Created

**3. Screenshot Helpers**
- Path: `/Users/nissim/dev/bat/BATbern-feature/web-frontend/e2e/workflows/documentation/helpers/screenshot-helpers.ts`
- Purpose: Standardized screenshot capture with automatic organization
- Key functions:
  - `captureWorkflowScreenshot(page, options)` - Captures with retry logic
  - `captureModalDialog(page, options)` - Waits for modal, captures
  - `buildScreenshotPath(options)` - Constructs organized path
  - `createSequentialCapturer(phase, startSequence)` - Auto-numbered sequence
- Output: `/Users/nissim/dev/bat/BATbern-feature/docs/user-guide/assets/screenshots/workflow/{phase}/{sequence}-{name}.png`
- Status: ✅ Created

**4. Test Data Configuration**
- Path: `/Users/nissim/dev/bat/BATbern-feature/web-frontend/e2e/workflows/documentation/test-data.config.ts`
- Purpose: **Centralized test data** - all event details, speaker candidates, topics, etc. in one file
- Content includes: event details, topics, speaker candidates, presentations, screenshot settings
- Status: ✅ Created

**5. Page Objects**
- Path: `/Users/nissim/dev/bat/BATbern-feature/web-frontend/e2e/workflows/documentation/page-objects/`
- Files:
  - `EventWorkflowPage.ts` - Event dashboard, creation, navigation
  - `SpeakerManagementPage.ts` - Speaker brainstorming, outreach, status
  - `PublishingPage.ts` - Publishing controls, preview
  - `TopicSelectionPage.ts` - Topic backlog, heat map
- Purpose: Encapsulate selectors and interactions
- Status: ✅ Created (skeletons - will be populated after recording)

**6. Configuration Updates**
- `playwright.config.ts` - Added `documentation-screenshots` project with HD viewport (1920x1080)
- `package.json` - Added `test:e2e:docs` and `test:e2e:docs:ui` scripts
- Status: ✅ Complete

### Phase 2: Workflow Discovery & Recording (1-2 days) 🔄 IN PROGRESS

#### Discovery Approach

**Problem**: We don't know exactly which screens/steps are implemented vs. planned.

**Solution**: Record actual user workflow, then automate it.

**Process**:
1. **User performs workflow once** while Playwright Inspector records
2. **Extract screens and steps** from recording
3. **Validate with user** - confirm this is the actual workflow
4. **Automate based on recording** - convert to test with screenshots

#### Recording Setup

```bash
# Terminal 1: Start backend services
cd /Users/nissim/dev/bat/BATbern-feature
make dev-native-up

# Terminal 2: Start frontend
cd web-frontend
npm run dev

# Terminal 3: Start Playwright Inspector
cd web-frontend
npx playwright codegen http://localhost:3000/organizer/events
```

**User performs complete workflow**:
1. Navigate to event dashboard
2. Create event (fill all form fields)
3. Select topics (use heat map if available)
4. Add speaker candidates (brainstorming)
5. Continue through implemented phases (outreach, quality, assignment, publishing, archival)

**Playwright generates code automatically** - We adapt it to add screenshot capture

**Output**: Accurate list of:
- Which screens exist
- Which UI elements are available
- What the actual user flow is
- Where to capture screenshots

### Phase 3: Workflow Test Implementation (3-5 days) ⏳ PENDING

#### UI-First Testing Philosophy

**CRITICAL PRINCIPLE**: This test documents the **actual organizer workflow** for the user guide. Therefore:

✅ **ALL workflow actions go through frontend UI**:
- Event creation → Fill forms, click buttons
- Topic selection → Click topic cards, use heat map
- Speaker brainstorming → Add candidates via UI forms
- Outreach tracking → Click outreach buttons, update status dropdowns
- Content submission → Upload files via UI
- Slot assignment → Drag-and-drop sessions
- Publishing → Click publish buttons
- Archival → Click archive button

❌ **NO API shortcuts for workflow**:
- Don't use `POST /api/v1/events` to create events
- Don't use `PUT /api/v1/events/{id}/workflow` to advance states
- Don't use API to assign speakers or create sessions

✅ **API helpers ONLY for**:
- Reading seed data (companies, speakers, topics)
- Verifying backend state after UI actions (test assertions)
- Cleanup after test completes

#### Test Structure

**CRITICAL PRINCIPLE**: Test simulates real organizer workflow through frontend UI only.

Each test case follows pattern:
1. Navigate to workflow screen (UI navigation)
2. Wait for elements to load
3. Capture initial state screenshot
4. **Perform user actions through UI** (click buttons, fill forms, drag-and-drop)
5. Capture intermediate state screenshots
6. Verify state transition (via UI indicators AND optional API verification)
7. Capture completion screenshot

**Example - Phase A (Setup)**:
```typescript
import { testConfig } from './test-data.config';

// ✅ CORRECT: Use frontend UI with centralized config
await page.click('[data-testid="create-event-button"]');
await page.fill('[name="eventTitle"]', testConfig.event.title);
await page.selectOption('[name="eventType"]', testConfig.event.eventType);
await page.fill('[name="eventDate"]', testConfig.event.date);
await page.click('button[type="submit"]');

// ❌ WRONG: Don't use API shortcuts
// await apiRequest('/api/v1/events', token, { method: 'POST', ... });

// ❌ WRONG: Don't hardcode test data in test file
// await page.fill('[name="eventTitle"]', 'BATbern 2025');  // Use testConfig instead
```

#### Screenshot Capture Points (TBD - determined after recording)

**IMPORTANT**: Screenshot count and details will be determined by actual workflow recording.

**Estimated phases** (subject to validation):

**Phase A: Setup**
- Screens TBD after user recording
- Estimated: 15-20 screenshots

**Phase B: Outreach**
- Screens TBD after user recording
- Estimated: 10-15 screenshots
- Note: Content upload may not exist yet

**Phase C: Quality**
- Screens TBD after user recording
- Estimated: 5-10 screenshots
- Note: Verify if quality review is implemented

**Phase D: Slot Assignment**
- Screens TBD after user recording
- Estimated: 8-12 screenshots

**Phase E: Publishing**
- Screens TBD after user recording
- Estimated: 8-12 screenshots
- Note: Newsletter may not be implemented yet

**Phase F: Communication & Archival**
- Screens TBD after user recording
- Estimated: 5-10 screenshots
- Note: Verify what post-event features exist

**Total Estimated**: 50-80 screenshots (will be refined after recording)

#### Validation Process

Before implementing each phase:
1. User records workflow for that phase
2. Review generated Playwright code
3. Identify all UI screens/states in recording
4. Create screenshot capture plan for that phase
5. Implement test with screenshots
6. User validates screenshots match actual workflow

### Phase 4: User Guide Integration (2-3 days) ⏳ PENDING

#### Directory Structure

Screenshots automatically organized:
```
docs/user-guide/assets/screenshots/
├── workflow/
│   ├── phase-a-setup/
│   ├── phase-b-outreach/
│   ├── phase-c-quality/
│   ├── phase-d-assignment/
│   ├── phase-e-publishing/
│   └── phase-f-communication/
```

#### Files to Update

**Workflow Phase Documents** (6 files):
- `/Users/nissim/dev/bat/BATbern-feature/docs/user-guide/workflow/phase-a-setup.md`
- `/Users/nissim/dev/bat/BATbern-feature/docs/user-guide/workflow/phase-b-outreach.md`
- `/Users/nissim/dev/bat/BATbern-feature/docs/user-guide/workflow/phase-c-quality.md`
- `/Users/nissim/dev/bat/BATbern-feature/docs/user-guide/workflow/phase-d-assignment.md`
- `/Users/nissim/dev/bat/BATbern-feature/docs/user-guide/workflow/phase-e-publishing.md`
- `/Users/nissim/dev/bat/BATbern-feature/docs/user-guide/workflow/phase-f-communication.md`

**Integration Pattern**:
```markdown
## Step 1: Event Type Definition

Navigate to the event dashboard.

![Event Dashboard](../assets/screenshots/workflow/phase-a-setup/01-event-dashboard.png)

Click **Create Event** to open the form.

![Create Event Form](../assets/screenshots/workflow/phase-a-setup/02-event-creation-form.png)
```

**New File to Create**:
- Path: `/Users/nissim/dev/bat/BATbern-feature/docs/user-guide/appendix/screenshot-index.md`
- Purpose: Complete index of all screenshots for maintenance

### Phase 5: Documentation & Polish (1 day) ⏳ PENDING

#### Documentation Updates

**Files to Update**:
- This plan document (mark complete)
- `web-frontend/e2e/workflows/documentation/README.md` (add workflow details)
- Create screenshot index

#### Screenshot Maintenance Strategy

**When to Update**:
- After UI changes affecting workflow screens
- Before major releases
- Weekly automated run (CI/CD - optional)

**Version Control**:
```bash
git add docs/user-guide/assets/screenshots/
git commit -m "docs: update workflow screenshots for Phase A-F

- Captured 68 screenshots across 6 workflow phases
- Updated user guide with screenshot references
- Created screenshot index for maintenance

Test: web-frontend/e2e/workflows/documentation/complete-event-workflow.spec.ts"
```

---

## Execution Commands

### Running Tests

```bash
cd web-frontend

# Run all documentation tests
npm run test:e2e:docs

# Run with UI mode for debugging
npm run test:e2e:docs:ui

# Run specific phase
npx playwright test workflows/documentation/complete-event-workflow.spec.ts -g "Phase A"
```

### Recording Workflow

```bash
cd web-frontend
npx playwright codegen http://localhost:3000/organizer/events
```

---

## Success Criteria

**Test Completion**:
- ✅ All 6 workflow phases execute without errors
- ✅ 50-80 screenshots captured and organized
- ✅ No flaky test failures
- ✅ Test data cleaned up after execution

**Documentation Quality**:
- ✅ Screenshots clearly show UI states
- ✅ Naming convention consistent (sequence-description.png)
- ✅ User guide pages updated with screenshot references
- ✅ Screenshot index created and up-to-date

**Maintainability**:
- ✅ Helper functions reusable for future tests
- ✅ Page objects follow existing patterns
- ✅ Clear comments and documentation
- ✅ Easy to run locally and in CI/CD

---

## Implementation Timeline

**Total Effort**: 2-3 weeks (1 developer, part-time)

- ✅ **Day 1**: Phase 1 - Test infrastructure setup (COMPLETE)
- 🔄 **Day 2**: Phase 2 - User records workflow (IN PROGRESS - awaiting user)
- ⏳ **Day 3**: Phase 2 - Extract and validate workflow
- ⏳ **Days 4-8**: Phase 3 - Implement tests with screenshots
- ⏳ **Days 9-10**: Phase 4 - User guide integration
- ⏳ **Day 11**: Phase 5 - Documentation and polish

---

## Contact & Support

**Implementation by**: Claude (AI Agent)
**Project**: BATbern Event Management Platform
**Repository**: `/Users/nissim/dev/bat/BATbern-feature`
**Documentation**: `web-frontend/e2e/workflows/documentation/README.md`
