# Documentation Workflow E2E Tests

**Status**: Complete ✅ | 77 Screenshots Captured | All Phases Implemented

This directory contains E2E tests that capture comprehensive screenshots of the complete event management workflow for user guide documentation.

## Purpose

1. **Documentation**: Capture 77 screenshots across 6 workflow phases for user guide
2. **Validation**: Verify complete workflow (CREATED → ARCHIVED) works end-to-end
3. **Regression Testing**: Ensure UI changes don't break organizer workflow

## Directory Structure

```
documentation/
├── README.md                           # This file
├── complete-event-workflow.spec.ts     # Main test spec
├── test-data.config.ts                 # Centralized test data
├── helpers/
│   ├── api-helpers.ts                  # API utilities (read-only + cleanup)
│   ├── screenshot-helpers.ts           # Screenshot capture utilities
│   └── cleanup-helpers.ts              # Test cleanup functions
└── page-objects/
    ├── EventWorkflowPage.ts            # Event workflow page object
    ├── SpeakerManagementPage.ts        # Speaker management page object
    ├── TopicSelectionPage.ts           # Topic selection page object
    └── PublishingPage.ts               # Publishing workflow page object
```

## Prerequisites

1. **Backend services running**:

   ```bash
   cd /Users/nissim/dev/bat/BATbern-feature
   make dev-native-up
   ```

2. **Frontend running**:

   ```bash
   cd web-frontend
   npm run dev
   ```

3. **Seed data loaded**: Companies, users, topics, speakers must exist in database

4. **Auth token available**:
   ```bash
   ./scripts/auth/get-token.sh staging your-email@example.com your-password
   export AUTH_TOKEN=$(cat .auth-token)
   ```

## Running the Tests

### Quick Start (Recommended)

Use the automated test runner that handles authentication and screenshot extraction:

```bash
cd web-frontend

# Run all phases A-E with auto-login
./run-phases-a-b-c-d-e-autologin.sh
```

This script:

- Automatically logs in using stored credentials
- Runs all 6 workflow phases sequentially
- Captures 77 screenshots with phase-prefixed naming (a-01-, b-02-, etc.)
- Outputs screenshots to `docs/user-guide/assets/screenshots/workflow/`

### Manual Test Execution

```bash
cd web-frontend

# Run all phases manually
npm run test:e2e:docs

# Run with UI for debugging
npm run test:e2e:docs:ui
```

### Run Specific Phases

```bash
# Run only Phase A (Setup)
npx playwright test workflows/documentation/complete-event-workflow.spec.ts -g "Phase A"

# Run only Phase B (Outreach)
npx playwright test workflows/documentation/complete-event-workflow.spec.ts -g "Phase B"

# Run Phase B.5 (Content Submission)
npx playwright test workflows/documentation/complete-event-workflow.spec.ts -g "Phase B.5"

# Run Phase C (Quality Review)
npx playwright test workflows/documentation/complete-event-workflow.spec.ts -g "Phase C"

# Run Phase D (Slot Assignment & Publishing)
npx playwright test workflows/documentation/complete-event-workflow.spec.ts -g "Phase D"

# Run Phase E (Archival)
npx playwright test workflows/documentation/complete-event-workflow.spec.ts -g "Phase E"
```

## Test Data Configuration

All test data is centralized in `test-data.config.ts`. To change test data:

1. Open `test-data.config.ts`
2. Modify event details, speaker candidates, presentations, etc.
3. Run tests with updated data

Example:

```typescript
// Change event type from EVENING to FULL_DAY
eventType: 'FULL_DAY'; // Will create event with 8-12 speakers

// Change topics
topics: [
  'Sustainable Building Materials',
  'Urban Planning Innovations', // New topic
];
```

## Screenshot Output

**Total Screenshots**: 77 screenshots across 6 workflow phases

Screenshots are automatically organized with phase-prefixed naming:

```
docs/user-guide/assets/screenshots/workflow/
├── phase-a-setup/ (~20 screenshots)
│   ├── a-01-event-dashboard.png
│   ├── a-02-event-creation-modal.png
│   ├── a-07-edit-modal-tasks-tab-initial.png
│   ├── a-13-task-list-my-tasks-filter.png
│   ├── a-16-topic-heatmap.png
│   ├── a-19-speaker-brainstorming-form.png
│   └── ...
├── phase-b-outreach/ (~15 screenshots)
│   ├── b-01-outreach-view-ready.png
│   ├── b-02-before-contact-speaker-1.png
│   ├── b-03-after-contact-speaker-1.png
│   ├── b-15-all-speakers-accepted.png
│   └── ...
├── phase-b5-content-submission/ (~6 screenshots)
│   ├── b5-01-publish-tab-before-topic.png
│   ├── b5-04-content-submission-drawer-1-opened.png
│   ├── b5-06-content-submitted-1.png
│   └── ...
├── phase-c-quality/ (~6 screenshots)
│   ├── c-01-publish-tab-before-speakers.png
│   ├── c-04-quality-review-1-opened.png
│   ├── c-05-content-approved-1.png
│   └── ...
├── phase-d-publishing/ (~10 screenshots)
│   ├── d-02-sessions-view-loaded.png
│   ├── d-03-slot-assignment-page-loaded.png
│   ├── d-05-auto-assign-modal-opened.png
│   ├── d-10-agenda-published.png
│   └── ...
└── phase-e-archival/ (~8 screenshots)
    ├── e-02-edit-modal-opened.png
    ├── e-04-status-changed-to-archived.png
    ├── e-06-override-checkbox-checked.png
    ├── e-08-archived-badge-visible.png
    └── ...
```

**Screenshot Index**: See `docs/user-guide/appendix/screenshot-index.md` for complete catalog

## Implementation Status

### ✅ Phase 1: Test Infrastructure Setup - COMPLETE

**Completed**: 2026-01-08

- Test infrastructure created
- Helpers and page objects implemented
- Playwright configuration updated
- Test runner script created

### ✅ Phase 2: Workflow Recording - COMPLETE

**Completed**: 2026-01-09

- All 6 workflow phases recorded
- UI selectors extracted
- Workflow validated

### ✅ Phase 3: Test Implementation - COMPLETE

**Completed**: 2026-01-09

- All phases implemented (A, B, B.5, C, D, E)
- 77 screenshots captured
- Phase-prefixed naming system implemented
- Test runner automated

### ✅ Phase 4: User Guide Integration - COMPLETE

**Completed**: 2026-01-09

- 24 high-value screenshots integrated into user guide
- 5 workflow phase documents updated
- Screenshot index created
- Documentation aligned with implementation

## Troubleshooting

### Authentication Failures

**Problem**: `AUTH_TOKEN environment variable is required`

**Solution**:

```bash
./scripts/auth/get-token.sh staging your-email@example.com
export AUTH_TOKEN=$(cat .auth-token)
```

### Seed Data Missing

**Problem**: Test fails because companies/topics don't exist

**Solution**:

- Ensure seed data is loaded in database
- Check `make dev-native-up` completed successfully
- Verify `/api/v1/companies` returns data

### Screenshots Not Captured

**Problem**: Screenshots directory empty

**Solution**:

- Check directory exists: `docs/user-guide/assets/screenshots/workflow/`
- Check test actually ran (not skipped)
- Review test output for errors

### Flaky Tests

**Problem**: Tests sometimes fail randomly

**Solution**:

- Increase timeouts in test
- Add explicit waits for elements
- Check for loading spinners before capturing screenshots

## Maintenance

### Updating Screenshots

After UI changes:

```bash
# Clean old screenshots
rm -rf docs/user-guide/assets/screenshots/workflow/*

# Re-run tests to capture new screenshots
npm run test:e2e:docs
```

### Updating Test Data

Edit `test-data.config.ts` and re-run tests.

### Adding New Phases

1. Add new phase to `complete-event-workflow.spec.ts`
2. Create screenshot directory: `docs/user-guide/assets/screenshots/workflow/phase-{name}/`
3. Add cleanup to `cleanupBeforeScreenshots()` call
4. Implement test case

## Contributing

When modifying these tests:

1. **UI-First**: Always use frontend UI for workflow actions (no API shortcuts)
2. **Centralized Data**: Put test data in `test-data.config.ts`, not in test files
3. **Clear Screenshots**: Use descriptive names (01-event-dashboard.png, not screenshot1.png)
4. **Document**: Update this README when adding new features
