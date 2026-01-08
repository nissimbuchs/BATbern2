# E2E Screenshot Workflow - Master Plan & Status

**Last Updated**: 2026-01-08 (Late Night Session - Shell Script Renamed for Phase C)
**Current Status**: Phase A + B Complete ✅ | Phase C Ready (Script Updated)

---

## 🎯 Project Goal

Create a comprehensive Playwright E2E test that executes the complete BATbern event management workflow (CREATED → ARCHIVED) while capturing 50-80 screenshots for user guide documentation.

---

## ✅ Completed Work

### 1. Infrastructure Setup (100% Complete)
- [x] Test file structure created
- [x] Screenshot helpers implemented
- [x] API helpers for cleanup
- [x] Test data configuration centralized
- [x] Page objects created (4 files)
- [x] Playwright config updated
- [x] Package.json scripts added

**Files Created** (12):
- `web-frontend/e2e/workflows/documentation/complete-event-workflow.spec.ts`
- `web-frontend/e2e/workflows/documentation/test-data.config.ts`
- `web-frontend/e2e/workflows/documentation/helpers/*.ts` (3 files)
- `web-frontend/e2e/workflows/documentation/page-objects/*.ts` (4 files)
- Test runner scripts

### 2. Workflow Recording & Analysis (100% Complete)
- [x] User workflow recorded in `docs/playwright-recording.ts`
- [x] Fixed drag-and-drop for kanban (lines 124-169)
- [x] Extracted all selectors and actions
- [x] Validated workflow covers 6 phases (A-F)

### 3. Authentication Migration (100% Complete) ✨ **NEW**

**CRITICAL FIXES** implemented in this session:

#### A. Token Injection Fixed
- **File**: `web-frontend/e2e/global-setup.ts`
- **Problem**: Was using same token for idToken, accessToken, and refreshToken
- **Fix**: Now loads all 3 tokens separately from `~/.batbern/development.json`
- **Result**: Authentication works correctly, no more 400 Cognito errors

#### B. Storage State Loading Fixed
- **File**: `web-frontend/playwright.config.ts`
- **Problem**: Conditional loading prevented storage state from being used
- **Fix**: Removed conditional, always use `.playwright-auth-state.json`
- **Result**: Browser loads with correct auth tokens

#### C. Test ID Migration (Language Independence) ✨ **MAJOR IMPROVEMENT**
Migrated from fragile German text selectors to stable `data-testid` attributes:

**Components Updated** (9 components):
1. `EventOverviewTab.tsx` - Added `select-topic-button`, `send-notification-button`, `preview-public-button`
2. `EventTypeSelector.tsx` - Already had `event-type-selector`
3. `TopicBacklogManager.tsx` - Added `view-mode-heatmap`, `view-mode-list`
4. `TopicDetailsPanel.tsx` - Added `confirm-topic-selection-button`
5. `SpeakerBrainstormingPanel.tsx` - Added 7 test IDs:
   - `speaker-name-field`
   - `speaker-company-field`
   - `speaker-expertise-field`
   - `speaker-organizer-select`
   - `speaker-notes-field`
   - `add-to-pool-button`
   - `proceed-to-outreach-button`
6. `OrganizerSelect.tsx` - Fixed to properly pass test ID to input element

**Page Objects Updated** (3 files):
- `EventWorkflowPage.ts` - Event type selection uses test IDs
- `TopicSelectionPage.ts` - Topic selection uses test IDs
- `SpeakerManagementPage.ts` - Speaker form uses test IDs

**Benefits**:
- 🌍 Language independent (works in German, English, any language)
- 🎯 More stable (no breaking when UI text changes)
- 🚀 Faster (direct element lookup)
- 📝 Self-documenting (test IDs describe purpose)

### 4. Phase A Implementation (100% Complete) ✅

**Completed Steps**:
1. ✅ Login with injected tokens
2. ✅ Navigate to dashboard
3. ✅ Click "Neue Veranstaltung" button
4. ✅ Fill event form (all fields)
5. ✅ Select event type using `data-testid="event-type-selector"`
6. ✅ Topic selection opens
7. ✅ Heatmap view works
8. ✅ Topic selected from heatmap
9. ✅ Topic confirmed
10. ✅ Speaker form visible
11. ✅ OrganizerSelect dropdown working (scrolling bug FIXED)
12. ✅ Event successfully created (validation working)

**Fixed Issues This Session**:
- ✅ OrganizerSelect infinite scrolling bug (memo dependency fix)
- ✅ Event creation validation (was actually working, master plan was outdated)

**Screenshots Captured**: 12+ (complete Phase A workflow)

---

## ✅ Recent Fixes (2026-01-08 Evening Session)

### OrganizerSelect Infinite Scrolling Bug (FIXED)

**Issue**: Dropdown menu scrolled up/down repeatedly when opened, making it unusable

**Root Cause**:
- React Query's `organizersData` returned new object reference on every render
- `useMemo` dependency on `organizersData` caused constant recalculation
- `menuItems` memo recalculated on every render
- MUI Select tried to scroll selected item into view on each render
- Created infinite scroll loop

**Fix Applied** (`OrganizerSelect.tsx`):
```typescript
// BEFORE (caused re-renders):
}, [organizersProp, organizersData]);

// AFTER (stable reference):
}, [organizersProp, organizersData?.data]);
```

**Files Modified**:
- `src/components/shared/OrganizerSelect/OrganizerSelect.tsx` (2 locations)

**Result**: ✅ Dropdown now works smoothly without scrolling issues

### Screenshot Quality Fixes (FIXED) ✨ **LATEST**

**Issue**: Phase A screenshots had multiple quality issues:
1. All screenshots appeared zoomed/oversized (text too large, logo distorted)
2. Screenshots 01-04 captured full scrollable page instead of viewport only
3. Screenshot 03 (modal) showed middle of form, not scrolled to top
4. Screenshots 09-11 had navbar in middle, needed scroll to top

**Root Causes**:
1. **Zoom Issue**: `devices['Desktop Chrome']` preset includes `deviceScaleFactor: 2` which wasn't being overridden correctly
2. **Viewport Size**: 1280x720 was too small for enterprise app, making UI elements appear cramped/large
3. **Full Page vs Viewport**: `fullPage: true` default captured entire scrollable page
4. **Modal Scroll**: Need to scroll `.MuiDialogContent-root` (the actual scrollable container), not the page
5. **Page Scroll**: Need to scroll window to top for proper alignment

**Fixes Applied**:

**1. Fixed Device Scale Factor** (`playwright.config.ts`):
```typescript
// BEFORE - spread included deviceScaleFactor: 2
use: {
  ...devices['Desktop Chrome'],
  deviceScaleFactor: 1,  // Override didn't work
}

// AFTER - manual config ensures deviceScaleFactor: 1
use: {
  browserName: 'chromium',
  viewport: { width: 1920, height: 1080 },  // Full HD
  deviceScaleFactor: 1,  // CRITICAL: Must be 1
  isMobile: false,
  hasTouch: false,
}
```

**2. Added Scroll Helpers** (`screenshot-helpers.ts`):
```typescript
// Scroll page to top
export async function scrollToTop(page: Page): Promise<void>

// Scroll modal content to top (targets .MuiDialogContent-root)
export async function scrollModalToTop(page: Page): Promise<void>

// Screenshot options
interface ScreenshotOptions {
  fullPage?: boolean;      // Capture full page (default: true)
  scrollToTop?: boolean;   // Scroll window to top
  scrollModal?: boolean;   // Scroll modal content to top
}
```

**3. Updated Screenshot Calls** (`complete-event-workflow.spec.ts`):
```typescript
// Screenshots 01-04: Viewport only
await capturer(page, 'event-dashboard', {
  scrollToTop: true,
  fullPage: false
});

// Screenshot 03: Modal scrolled to top
await capturer(page, 'event-form-filled', {
  scrollModal: true,
  fullPage: false,
  delay: 1000
});

// Screenshots 09-11: Page scrolled to top
await capturer(page, 'topic-selection-confirmed', {
  scrollToTop: true
});
```

**Files Modified** (3):
- `web-frontend/playwright.config.ts` - Fixed viewport (1920x1080) and deviceScaleFactor (1)
- `web-frontend/e2e/workflows/documentation/helpers/screenshot-helpers.ts` - Added scroll functions
- `web-frontend/e2e/workflows/documentation/complete-event-workflow.spec.ts` - Updated screenshot options

**Result**: ✅ All 12 Phase A screenshots now have:
- Normal scale (no zoom, proper text/logo size)
- Viewport-only capture for dashboard views (01-04)
- Modals scrolled to top showing form beginning (03)
- Pages scrolled to top with navbar at top (09-11)
- Full HD resolution (1920x1080) for professional appearance

---

## ⏳ Pending Work

### Phase A - ✅ COMPLETE (Ready for Phase B)
- [x] Fix event creation validation error
- [x] Verify event successfully created
- [x] Complete screenshot captures
- [x] Fix OrganizerSelect scrolling bug
- [x] Validate cleanup works

### Phases B-F - Implementation (IN PROGRESS)
- [x] **Phase B**: Speaker Outreach & Kanban (Lines 90-169) ✅ **COMPLETE**
  - Contact 5 interactions (4 speakers, Nissim contacted twice)
  - Track contact method and notes
  - Drag speakers CONTACTED → READY (4 speakers) ✅
  - Drag speakers READY → ACCEPTED (4 speakers) ✅
  - StatusChangeDialog confirmation after each drag ✅
  - Test IDs added for language independence ✅
  - Event code format fixed (BATbern prefix) ✅
  - ~15 screenshots captured

- [ ] **Phase B.5**: Content Submission (Lines 189-237) ⏳ **IN PROGRESS**
  - Switch to Sessions view
  - Go to Publish tab
  - Click "Publish Topic" button 🔑
  - Return to Speakers tab
  - For each speaker (Nissim, Balti, Andreas):
    - Select real speaker from database
    - Fill presentation title
    - Fill presentation abstract
    - Submit speaker content
  - ~10 screenshots

- [ ] **Phase C**: Quality Review (Lines 238-246)
  - Go to Publish tab
  - Click "Publish Speakers" button 🔑
  - Return to Speakers tab
  - Approve 3 presentations (Nissim, Balti, Andreas)
  - ~6 screenshots

- [ ] **Phase D**: Slot Assignment & Publish Agenda (Lines 247-254)
  - Manage slot assignments
  - Assign speakers to time slots
  - Publish agenda
  - 6-8 screenshots

- [ ] **Phase E**: Archival (Lines 255-261)
  - Edit event status to "Archiviert"
  - Override workflow validation
  - 3-4 screenshots

- [ ] **Phase F**: Cleanup (Lines 262-265)
  - Delete event
  - Verify cleanup
  - 2-3 screenshots

### User Guide Integration
- [ ] Add screenshots to workflow documentation
- [ ] Create screenshot index
- [ ] Update README

---

## 🚀 How to Run (Current State)

### Prerequisites
```bash
# 1. Start services
make dev-native-up

# 2. Start frontend
cd web-frontend && npm run dev

# 3. Get auth token
./scripts/auth/get-token.sh development nissim@buchs.be <password>
```

### Run Tests
```bash
cd web-frontend

# Set environment variables
export AUTH_TOKEN=$(jq -r '.idToken' ~/.batbern/development.json)

# Run Phase A + B + C tests
./run-phases-a-b-c-autologin.sh

# Or with UI mode for debugging
./run-phases-a-b-c-autologin.sh --ui
```

### Debug Event Creation Issue
```bash
# Check backend logs
tail -100 /tmp/batbern-1-api-gateway.log | grep -A 10 "422"

# Check error screenshot (when test hangs)
ls -lrt web-frontend/test-results/**/test-failed-*.png | tail -1

# View last test run output
tail -100 /tmp/phase-a-test-*.log
```

---

##  Key Technical Decisions

### 1. Test ID Strategy
- **Decision**: Use `data-testid` attributes instead of text/role selectors
- **Rationale**: Language-independent, stable, maintainable
- **Pattern**: `data-testid="{component}-{element}-{type}"` (e.g., `speaker-name-field`)

### 2. MUI TextField Test IDs
- **Challenge**: MUI TextField wraps `<input>` in `<div>`
- **Solution**: Use `inputProps={{ 'data-testid': '...' }}` to target actual input
- **Example**:
  ```tsx
  <TextField
    label="Name"
    inputProps={{ 'data-testid': 'speaker-name-field' }}
  />
  ```

### 3. MUI Select Test IDs
- **Challenge**: Select has both FormControl and hidden input
- **Solution**: Extract test-id from props, pass to Select inputProps only
- **Example**:
  ```tsx
  const { 'data-testid': testId, ...restProps } = formControlProps;
  <Select inputProps={{ 'data-testid': testId }} />
  ```

### 4. Authentication Approach
- **Decision**: Use Amplify V6 format with separate tokens
- **Implementation**: Global setup injects tokens into localStorage
- **Format**: `CognitoIdentityServiceProvider.{clientId}.{username}.{tokenType}`

### 5. UI-First Testing
- **Decision**: ALL workflow actions through frontend (no API shortcuts)
- **Rationale**: Test documents actual organizer workflow for user guide
- **Exception**: API only for cleanup and verification

---

## 📁 Project Structure

```
web-frontend/
├── e2e/
│   ├── global-setup.ts                      # ✅ FIXED: Token injection
│   └── workflows/documentation/
│       ├── complete-event-workflow.spec.ts  # Main test file
│       ├── test-data.config.ts             # Centralized test data
│       ├── helpers/
│       │   ├── api-helpers.ts              # Cleanup & verification
│       │   ├── screenshot-helpers.ts       # Screenshot capture
│       │   └── cleanup-helpers.ts          # Cleanup utilities
│       └── page-objects/
│           ├── EventWorkflowPage.ts        # ✅ UPDATED: Test IDs
│           ├── SpeakerManagementPage.ts    # ✅ UPDATED: Test IDs
│           ├── TopicSelectionPage.ts       # ✅ UPDATED: Test IDs
│           └── PublishingPage.ts
├── playwright.config.ts                     # ✅ FIXED: Storage state
├── package.json                             # Test scripts
└── run-phase-a-autologin.sh                # Test runner

src/components/
├── organizer/
│   ├── EventPage/
│   │   └── EventOverviewTab.tsx            # ✅ ADDED: Test IDs
│   ├── EventManagement/
│   │   └── EventForm.tsx
│   ├── EventTypeSelector/
│   │   └── EventTypeSelector.tsx           # ✅ ADDED: Test IDs
│   └── SpeakerBrainstormingPanel/
│       └── SpeakerBrainstormingPanel.tsx   # ✅ ADDED: Test IDs
├── TopicBacklogManager/
│   ├── TopicBacklogManager.tsx             # ✅ ADDED: Test IDs
│   └── TopicDetailsPanel.tsx               # ✅ ADDED: Test IDs
└── shared/
    └── OrganizerSelect/
        └── OrganizerSelect.tsx              # ✅ FIXED: Test ID handling
```

---

## 🐛 Known Issues

### 1. Event Creation Validation (RESOLVED ✅)
- **Status**: ✅ FIXED - Was working all along, master plan was outdated
- **Resolution**: Event creation validated and working correctly
- **Evidence**: Screenshot shows "Demo BATbern Event" successfully created

### 2. OrganizerSelect Scrolling Bug (RESOLVED ✅)
- **Status**: ✅ FIXED - Memo dependency corrected
- **Resolution**: Changed dependency from `organizersData` to `organizersData?.data`
- **Impact**: Dropdown now works smoothly without infinite scrolling

### 3. Test Hanging on Visibility Checks
- **Status**: 🟡 Workaround implemented
- **Cause**: `isVisible()` without timeout can hang
- **Fix**: Always use timeout: `isVisible({ timeout: 3000 })`

### 3. Modal Close Detection
- **Status**: 🟡 Improved with timeout
- **Approach**: Check if form field still visible to detect modal state
- **Fallback**: Capture error screenshot when modal doesn't close

---

## 📊 Progress Summary

| Phase | Status | Progress | Screenshots |
|-------|--------|----------|-------------|
| Infrastructure | ✅ Complete | 100% | N/A |
| Recording | ✅ Complete | 100% | N/A |
| Auth Migration | ✅ Complete | 100% | N/A |
| Test ID Migration | ✅ Complete | 100% | N/A |
| **Phase A** | ✅ Complete | 100% | 12/12 |
| **Phase B** | ✅ Complete | 100% | 11/11 |
| Phase B.5 | ⏳ In Progress | 0% | 0/10 |
| Phase C | ⏳ Ready | 0% | 0/6 |
| Phase D | ⏳ Ready | 0% | 0/8 |
| Phase E | ⏳ Ready | 0% | 0/4 |
| Phase F | ⏳ Ready | 0% | 0/3 |

**Overall**: ~50% complete (infrastructure + auth + Phase A + B complete, Phase B.5 in progress)

---

## 🎯 Next Session Priorities

### Priority 1: Implement Phase B.5 (Content Submission) ⏳ **IN PROGRESS**
1. Add test IDs to publish buttons and speaker content form
2. Review workflow (lines 189-237 in playwright-recording.ts)
3. Implement publish topic action
4. Implement speaker content submission (3 speakers)
5. Capture ~10 screenshots

### Priority 2: Implement Phase C (Quality Review)
1. Add test IDs to approve buttons
2. Review workflow (lines 238-246 in playwright-recording.ts)
3. Implement publish speakers action
4. Implement content approval (3 presentations)
5. Capture ~6 screenshots

---

## 📝 Session Notes

### 2026-01-08 Morning Session

**Major Achievements**:
1. ✅ Fixed critical authentication token bug
2. ✅ Migrated 9 components to use test IDs
3. ✅ Fixed MUI TextField/Select test ID issues
4. ✅ Test now reaches speaker brainstorming

**Lessons Learned**:
1. MUI components need `inputProps` for test IDs, not root `data-testid`
2. OrganizerSelect was adding test ID to both FormControl AND Select (duplicate)
3. Always use explicit timeouts on visibility checks
4. Event type selection needs data-value attribute matching

**Files Modified** (8):
- `global-setup.ts` - Token injection fix
- `playwright.config.ts` - Storage state loading
- `EventOverviewTab.tsx` - Test IDs
- `TopicBacklogManager.tsx` - Test IDs
- `TopicDetailsPanel.tsx` - Test IDs
- `SpeakerBrainstormingPanel.tsx` - Test IDs
- `OrganizerSelect.tsx` - Test ID handling (partial)
- 3 Page object files - Updated to use test IDs

### 2026-01-08 Evening Session ✅

**Critical Bug Fix**:
1. ✅ FIXED OrganizerSelect infinite scrolling bug
   - Root cause: `useMemo` dependency on unstable `organizersData` reference
   - Solution: Changed to `organizersData?.data` for stable reference
   - Result: Dropdown now works smoothly

**Discoveries**:
1. Event creation was actually working - master plan was outdated
2. Screenshot evidence shows "Demo BATbern Event" successfully created
3. Phase A is 100% complete and ready for Phase B

**Technical Insight**:
- React Query returns new object reference for `useQuery` result on every render
- Even if data unchanged, the wrapper object is new
- Must depend on `data` property, not the entire query result
- This pattern applies to all React Query hooks

**Files Modified** (2):
- `OrganizerSelect.tsx` - Fixed memo dependencies (2 locations)
- `E2E-WORKFLOW-MASTER-PLAN.md` - Updated status to Phase A complete

**Phase A Status**: ✅ 100% COMPLETE

### 2026-01-08 Late Night Session - Phase B Complete ✅

**Major Achievement**: Phase B (Speaker Outreach) fully implemented and tested

**Implementation Details**:
1. ✅ Added `speakerOutreach` configuration to test-data.config.ts
   - 5 contact interactions (Nissim contacted twice as in recording)
   - Each interaction has displayName, contactMethod, notes, speakerIndex

2. ✅ Implemented Phase B test in complete-event-workflow.spec.ts
   - Navigates to event outreach view
   - Contacts each speaker using speakerPage.contactSpeaker() method
   - Captures 11 screenshots (before/after each contact + final state)
   - Follows exact recording workflow (lines 90-123)

3. ✅ Updated test suite to use `.serial` mode
   - Ensures Phase B can access testEventCode from Phase A
   - Tests run in order and share state

4. ✅ Created run-phases-a-b-c-autologin.sh test runner
   - Renamed from run-phases-a-b-autologin.sh (2026-01-08 Late Night)
   - Uses regex `-g "Phase (A|B|C)"` to run all three phases
   - Updated output paths for Phase A, B, and C screenshots

**Files Modified** (4):
- `test-data.config.ts` - Added speakerOutreach array with 5 interactions
- `complete-event-workflow.spec.ts` - Implemented Phase B test, added .serial
- `run-phases-a-b-autologin.sh` - Updated runner for Phase A + B
- `E2E-WORKFLOW-MASTER-PLAN.md` - Updated status to Phase B complete

**Phase B Workflow Implemented**:
```
1. Navigate to event outreach view (speakers visible as cards)
2. For each speaker contact:
   - Click speaker card → opens contact dialog
   - Select contact method (Telefon/E-Mail/Persönlich)
   - Fill notes field
   - Click "Als kontaktiert markieren"
   - Click backdrop to close dialog
3. Verify all 5 contacts recorded
4. Capture 11 screenshots
```

**Screenshot Count**: 11 total
- 01: outreach-view-initial
- 02-11: before-contact-speaker-{1-5} + after-contact-speaker-{1-5}
- 11: all-speakers-contacted (final state)

**Next Steps**: Ready for Phase C (Kanban/Quality)

**Critical Bugs Fixed (3 total):**

1. ✅ **OrganizerSelect Infinite Scrolling** (ROOT CAUSE)
   - `useMemo` dependency on `t` (i18next) caused constant re-renders
   - `t` creates new function reference on every render
   - Fixed: Removed `t` from dependencies array

2. ✅ **OrganizerSelect Test ID Location**
   - `data-testid` was on hidden `<input>` via `inputProps`
   - Playwright couldn't click hidden element
   - Fixed: Moved to `<Select data-testid={testId}>`

3. ✅ **Speaker Form Loop Bug**
   - Test tried to click non-existent "Add Speakers" button
   - Form stays visible after adding speaker
   - Fixed: Removed button click from loop in `addMultipleSpeakers()`

**Files Modified** (3):
- `OrganizerSelect.tsx` - Fixed memo deps + test-id location
- `SpeakerManagementPage.ts` - Fixed MUI Select interaction + loop logic

**Test Status**: ✅ READY - All blocking bugs resolved
**Next**: Run `./run-phase-a-autologin.sh --ui` to verify complete Phase A

### 2026-01-08 Late Night Session Part 2 - Phase B Kanban Complete ✅

**Major Achievement**: Phase B Kanban drag-and-drop fully implemented

**Implementation Details**:

1. ✅ **Added Test IDs to Kanban Components**
   - `StatusChangeDialog.tsx` - Added test IDs:
     - `data-testid="status-change-dialog"` on Dialog
     - `data-testid="status-change-reason"` on reason TextField
     - `data-testid="status-change-cancel"` on cancel button
     - `data-testid="status-change-confirm"` on confirm button

   - `SpeakerStatusLanes.tsx` - Added test IDs:
     - `data-testid="status-lane-{status}"` on each lane Paper (e.g., `status-lane-ready`)
     - `data-testid="status-lane-heading-{status}"` on Typography headings

2. ✅ **Implemented Drag-and-Drop for Phase B**
   - Step 3: Drag 4 speakers from CONTACTED → READY
   - Step 4: Drag 4 speakers from READY → ACCEPTED
   - Manual mouse events using boundingBox (more reliable than dragTo with dnd-kit)
   - StatusChangeDialog confirmation after each drag
   - Comprehensive error handling and logging

3. ✅ **Fixed Event Code Format**
   - Changed from `BAT-{number}` to `BATbern{number}` format
   - Removed unnecessary URL conversions
   - Cleanup now works correctly

4. ✅ **Replaced Translated Text with Test IDs**
   - ❌ Before: `page.getByRole('heading', { name: /Ready|Bereit/i })`
   - ✅ After: `page.getByTestId('status-lane-ready')`
   - All kanban column references now language-independent

**Files Modified** (3):
- `StatusChangeDialog.tsx` - Added 4 test identifiers
- `SpeakerStatusLanes.tsx` - Added lane test identifiers
- `complete-event-workflow.spec.ts` - Implemented drag-drop with error handling

**Technical Details**:
- Drag-and-drop uses `page.mouse.move()`, `page.mouse.down()`, `page.mouse.up()`
- `boundingBox()` provides element coordinates for precise mouse movements
- Each drag waits for card visibility before attempting
- Modal confirmation uses test identifier for reliability
- Success/failure logging for each drag operation

**Phase B Screenshots**: ~15 captured
- Contact interactions (before/after each)
- Kanban states (after READY, after ACCEPTED)

**Phase B Status**: ✅ 100% COMPLETE
**Next**: Implement Phase C (Quality Control & Content Submission)

### 2026-01-08 Late Night Session Part 3 - Shell Script Renamed for Phase C ✅

**Script Preparation for Phase C**:

1. ✅ **Renamed Test Runner Script**
   - File: `run-phases-a-b-autologin.sh` → `run-phases-a-b-c-autologin.sh`
   - Updated regex pattern: `-g "Phase (A|B)"` → `-g "Phase (A|B|C)"`
   - Updated all console output to mention Phase C
   - Updated log filename: `/tmp/phase-a-b-test-run.log` → `/tmp/phase-a-b-c-test-run.log`
   - Added Phase C screenshot path: `phase-c-quality/`
   - Script is now ready to run all three phases

2. ✅ **Updated Master Plan Documentation**
   - Updated "How to Run" section with new script name
   - Updated "References" section with new script path
   - Updated session notes to document rename

**Files Modified** (2):
- `run-phases-a-b-autologin.sh` → `run-phases-a-b-c-autologin.sh` (renamed + updated)
- `E2E-WORKFLOW-MASTER-PLAN.md` - Updated documentation

**Result**: Test runner script is now ready for Phase C implementation
**Next Step**: Implement Phase C test code in `complete-event-workflow.spec.ts`

---

## 🔗 References

- **Workflow Recording**: `docs/playwright-recording.ts`
- **Test Spec**: `web-frontend/e2e/workflows/documentation/complete-event-workflow.spec.ts`
- **Test Runner**: `web-frontend/run-phases-a-b-c-autologin.sh`
- **Original Plan**: `docs/plans/e2e-screenshot-workflow-plan.md` (archived)

---

**Last Updated**: 2026-01-08 Late Night Session (Shell Script Renamed for Phase C)
**Next Action**: Implement Phase C (Quality Control & Content Assignment)
