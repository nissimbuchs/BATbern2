# Documentation Workflow E2E Tests

This directory contains E2E tests that capture comprehensive screenshots of the complete event management workflow for user guide documentation.

## Purpose

1. **Documentation**: Capture screenshots for user guide
2. **Validation**: Verify complete workflow works end-to-end
3. **Regression Testing**: Ensure UI changes don't break workflow

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

### Option 1: Run Complete Workflow

```bash
cd web-frontend

# Run all phases
npm run test:e2e:docs

# Run with UI for debugging
npm run test:e2e:docs:ui
```

### Option 2: Run Specific Phase

```bash
# Run only Phase A (Setup)
npx playwright test workflows/documentation/complete-event-workflow.spec.ts -g "Phase A"

# Run only Phase B (Outreach)
npx playwright test workflows/documentation/complete-event-workflow.spec.ts -g "Phase B"
```

### Option 3: Record Workflow First

Before implementing the test, record the actual workflow:

```bash
cd web-frontend
npx playwright codegen http://localhost:3000/organizer/events
```

Then:
1. Perform the complete workflow manually
2. Save the generated code
3. Extract UI selectors and actions
4. Adapt into test with screenshot capture

## Test Data Configuration

All test data is centralized in `test-data.config.ts`. To change test data:

1. Open `test-data.config.ts`
2. Modify event details, speaker candidates, presentations, etc.
3. Run tests with updated data

Example:
```typescript
// Change event type from EVENING to FULL_DAY
eventType: 'FULL_DAY'  // Will create event with 8-12 speakers

// Change topics
topics: [
  'Sustainable Building Materials',
  'Urban Planning Innovations',  // New topic
]
```

## Screenshot Output

Screenshots are automatically organized by workflow phase:

```
docs/user-guide/assets/screenshots/workflow/
├── phase-a-setup/
│   ├── 01-event-dashboard.png
│   ├── 02-create-event-button.png
│   ├── 03-event-form.png
│   └── ...
├── phase-b-outreach/
│   ├── 01-speaker-dashboard.png
│   └── ...
├── phase-c-quality/
├── phase-d-assignment/
├── phase-e-publishing/
└── phase-f-communication/
```

## Development Workflow

### Phase 1: Record Workflow (Complete)
✅ Test infrastructure created
✅ Helpers and page objects scaffolded
✅ Playwright configuration updated

### Phase 2: Record Actual Workflow (Next)
🔄 User performs workflow with Playwright Inspector
🔄 Extract screens and actions from recording
🔄 Validate with user

### Phase 3: Implement Tests
- Fill in page object methods from recording
- Add screenshot capture at each step
- Verify workflow state transitions

### Phase 4: Update User Guide
- Reference screenshots in markdown files
- Create screenshot index
- Verify documentation accuracy

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
