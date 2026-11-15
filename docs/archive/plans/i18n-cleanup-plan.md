# i18n Cleanup Plan

**Branch:** `feature/multi-language-support`
**Created:** 2026-02-28
**Status:** Planned

## Overview

Four-phase cleanup of the web-frontend translation system, executed in dependency order:

| Phase | Goal | Scope |
|-------|------|-------|
| **1 — Deduplicate** | Consolidate cross-namespace duplicate values into `common` | ~169 cross-ns keys, 2 sub-tasks |
| **2 — Hardcoded text** | Replace hardcoded UI strings with `t()` calls | 78 prod files, 254 hits (excl. PrivacyPage, SupportPage) |
| **3 — Translation-independent tests** | Add `data-testid` anchors to components; refactor unit + E2E tests off text literals | 180 unit test files (~4,400 assertions); 32 E2E spec files (320 brittle patterns) |
| **4 — Unused key analysis** | Smart automated analysis of the 830 flagged unused keys | Scripted tooling, no manual review |

**Do not execute phases in parallel.** Phase 1 establishes canonical keys. Phase 2 finishes adding all keys. Phase 3 refactors tests to use stable selectors (which also makes Phase 4's unused-key scanner more accurate, because test-referenced keys are now visible to it). Phase 4 then deletes what remains unused.

---

## Test Impact — Critical Context

**Before touching any translation file or component, read this section.**

The test suite uses **real i18n with the actual EN locale files** (not mocks). `src/test/setup.ts` imports `src/i18n/config` and calls `i18n.changeLanguage('en')`. This means:

- **What tests see:** The fully rendered English text output (e.g., `"Cancel"`, `"Save"`)
- **Not what tests see:** Translation keys or namespace paths

### Current test exposure

| Layer | Files asserting on text | Total text assertions |
|-------|------------------------|-----------------------|
| Unit / component tests (Vitest) | 180 of 272 test files | ~4,400 |
| E2E tests (Playwright) | 19 of 50 spec files | ~96 |

The dominant patterns are `screen.getByText()`, `screen.getByRole(..., { name: })`, `getByPlaceholderText()`, `getByLabelText()`, and Playwright's `toContainText()`. All of these assert on the **rendered string value**, not on key names.

### Phase-by-phase blast radius

**Phase 1 (deduplication) — LOW test risk.**
Moving a key from `events:form.cancel` to `common:actions.cancel` does NOT change the rendered text (both resolve to `"Cancel"` in EN). Tests that do `getByText('Cancel')` will continue to pass. The only tests at risk are the 6 instances of `t_call_in_test` — tests that call `t()` directly to construct their expected values; those must be updated to reference the new key path.

**Phase 2 (hardcoded → t()) — MEDIUM test risk.**
Converting `<button>Save Changes</button>` to `<button>{t('events:form.saveChanges')}</button>` is safe **if and only if** the EN translation value is identical to the removed hardcoded string. If the value differs even in capitalisation or punctuation, `getByText('Save Changes')` will fail. Rule: the EN translation value for a new key must be a character-for-character copy of the string it replaces.

**Phase 3 (test refactor) — ZERO production risk, MEDIUM test effort.**
No production code changes. Only test files and component `data-testid` attributes are touched. The unit test suite will go green faster because assertions no longer depend on specific EN strings. E2E tests stop using `button:has-text('...')` and `text=/some label/i` patterns.

**Phase 4 (deleting unused keys) — NEAR-ZERO test risk.**
Deleting a key that is genuinely unused cannot affect tests — if the key were used in code, it would have been caught by the analysis. Doing Phase 3 first means any key referenced in tests is already visible to the scanner. Run the full test suite as a sanity check after deletion.

### Test fix workflow (apply in all phases)

When a test breaks after a translation change:

1. **Determine root cause** — is it a changed EN value, a removed key, or a renamed key?
2. **Fix the component first** if the component is wrong (wrong key, missing `useTranslation` namespace)
3. **Then fix the test** — update the expected string to match the new EN value, or switch the assertion to a more resilient selector:
   - Prefer `getByRole('button', { name: /cancel/i })` over `getByText('Cancel')` — role-based selectors survive EN value tweaks
   - Prefer `getByTestId` or `data-testid` for elements where text is incidental
4. **Never update a test to expect a translation key path** (e.g., `events:form.cancel`) — that means the component has a broken `useTranslation` setup
5. **Run the full unit test suite** after each file batch: `cd web-frontend && npm run test -- --run`

### High-risk test files to watch (most text assertions)

These files are most likely to need attention in Phase 2, since they test components that appear in the hardcoded-text list:

| Test file | Assertions | Related component |
|-----------|-----------|-------------------|
| `components/public/Registration/__tests__/RegistrationWizard.test.tsx` | 197 | Registration flow (Group A) |
| `pages/speaker-portal/__tests__/InvitationResponsePage.test.tsx` | 91 | Speaker portal (Group B) |
| `components/organizer/UserManagement/ParticipantBatchImportModal.test.tsx` | 74 | Organizer (Group C) |
| `components/organizer/PartnerManagement/__tests__/PartnerFilters.test.tsx` | 70 | Organizer (Group C) |
| `components/public/__tests__/FilterSidebar.test.tsx` | 61 | Public (Group D) |
| `components/public/Registration/__tests__/CompanyAutocomplete.test.tsx` | 63 | Registration flow |

---

## Phase 1 — Consolidate Duplicate Translations

**Goal:** Eliminate 169 cross-namespace duplicate values. All generic UI tokens (`Cancel`, `Save`, `Status`, `Delete`, …) must live exclusively in `common` and be referenced from other namespaces via `t('common:actions.cancel')`.

### Background

The analysis found:
- **302** unique values appearing in 2+ keys
- **169** of those span multiple namespaces (prime consolidation candidates)
- **89** cross-namespace duplicates already have a canonical key in `common` — callers just need to be pointed there
- **80** cross-namespace duplicates are NOT yet in `common` — need a new `common` key added first

### Sub-task 1.1 — Redirect callers to existing `common` keys

For each entry below, delete the duplicate key from the other namespace's JSON file and update every `t()` call in the source to use the `common:` key.

**Canonical `common` key → duplicate keys to remove:**

| `common` key (keep) | Duplicate keys to remove |
|---------------------|--------------------------|
| `common:actions.cancel` | `userManagement:actions.cancel`, `userManagement:batchImport.cancelButton`, `events:form.cancel`, `events:confirmations.cancel`, `events:common.cancel`, `events:slotAssignment.actions.cancel`, `partners:modal.actions.cancel`, `organizer:topicBacklog.dialogs.similar.cancel`, `organizer:topicBacklog.dialogs.override.cancel`, `organizer:topicBacklog.dialogs.delete.cancel`, `organizer:speakerStatus.cancelChange`, `events:eventPage.participantTable.deleteDialog.cancel`, `events:publishing.controls.scheduleDialog.cancel`, `events:publishing.versionControl.rollbackDialog.cancel` |
| `common:actions.save` | `userManagement:actions.save`, `events:form.save`, `events:common.save`, `partners:modal.actions.save`, `partners:portal.topics.organizer.save` |
| `common:actions.saving` | `userManagement:modal.editRoles.saving`, `userManagement:loading.save`, `events:autoSave.saving`, `partners:modal.actions.saving`, `partners:portal.topics.organizer.saving`, `organizer:topicBacklog.editModal.saving` |
| `common:actions.delete` | `userManagement:actions.delete`, `events:form.delete`, `events:common.delete`, `partners:modal.actions.delete`, `partners:portal.topics.delete`, `events:eventPage.participantTable.deleteDialog.confirm`, `events:speakers.deleteSession` |
| `common:actions.edit` | `userManagement:actions.edit`, `events:dashboard.actions.edit`, `events:common.edit`, `partners:portal.topics.edit`, `auth:register.step2.editButton` |
| `common:actions.close` | `userManagement:actions.close`, `events:form.close`, `events:common.close`, `events:slotAssignment.preferences.close`, `events:publishing.controls.newsletterDialog.close` |
| `common:actions.retry` | `userManagement:actions.retry`, `events:errors.retry`, `events:publishing.preview.retry`, `events:eventPage.participantList.actions.retry` |
| `common:company.errors.retry` *(alias above)* | see `actions.retry` row |
| `common:navigation.speakers` | `events:eventPage.tabs.speakers`, `events:eventPage.overview.speakers`, `events:dashboard.actions.speakers`, `events:publishing.timeline.speakers`, `events:publishing.controls.phase.speakers`, `events:metrics.speakers`, `events:public.speakers.title`, `events:sessionEdit.tabs.speakers`, `organizer:analytics.labels.speakers` |
| `common:navigation.events` | `events:navigation.events`, `userManagement:participantImport.columns.events`, `events:dashboard.eventWord_plural`, `organizer:topicBacklog.breadcrumbs.events`, `organizer:topicBacklog.heatMap.events` |
| `common:navigation.analytics` | `events:dashboard.actions.analytics`, `partners:card.analytics`, `partners:detail.tabs.analytics`, `partners:portal.nav.analytics`, `organizer:analytics.title` |
| `common:company.batchImport.columns.status` | `userManagement:filters.status.label`, `userManagement:table.headers.status`, `userManagement:batchImport.columns.status`, `userManagement:participantImport.columns.status`, `userManagement:userDetail.eventsTable.status`, `events:eventPage.participantTable.headers.status`, `events:form.status`, `events:filters.status`, `events:sorting.status`, `events:publishing.versionControl.status`, `partners:filters.status`, `partners:portal.topics.organizer.status`, `organizer:topicBacklog.filters.status` |
| `common:event.batchImport.columns.title` | `userManagement:userDetail.eventsTable.title`, `events:form.title`, `events:sorting.title`, `partners:detail.notesTab.titleLabel`, `partners:portal.topics.form.title`, `partners:portal.topics.organizer.col.title`, `organizer:topicBacklog.createModal.fields.title`, `organizer:topicBacklog.editModal.fields.title`, `organizer:analytics.labels.labelToggle.title` |
| `common:event.batchImport.columns.date` | `userManagement:userDetail.eventsTable.date`, `events:sorting.date`, `events:public.logistics.date`, `events:eventPage.newsletter.historyDate`, `partners:meetings.fields.date`, `partners:meetings.columns.date`, `partners:portal.analytics.table.date`, `partners:portal.topics.col.date`, `partners:portal.topics.organizer.col.date` |
| `common:role.speaker` | `userManagement:filters.role.speaker`, `userManagement:roles.speaker`, `events:speakers.speakerName`, `events:public.sessions.speaker`, `events:public.program.speaker`, `organizer:speakerOutreach.speakerName`, `organizer:qualityReview.speaker` |
| `common:speakerPortal.dashboard.actions` | `userManagement:table.headers.actions`, `events:eventPage.participantTable.headers.actions`, `events:publishing.versionControl.actions`, `events:criticalTasks.actions`, `partners:meetings.columns.actions`, `partners:portal.topics.col.actions`, `partners:portal.topics.organizer.col.actions` |
| `common:company.batchImport.status.pending` | `userManagement:participantImport.status.pending`, `events:eventPage.overview.pending`, `events:workflow.pending`, `events:speakers.materialsPending`, `events:metrics.pending`, `partners:card.meetingPending`, `organizer:tasks.pending` |
| `common:company.batchImport.status.error` | `userManagement:batchImport.status.error`, `events:accessibility.error`, `events:common.error`, `events:slotAssignment.conflicts.severity.error`, `admin:userSync.status.error` |
| `common:company.backToList` | `userManagement:back`, `events:actions.back`, `events:common.back`, `auth:register.step2.backButton` |
| `common:company.detail.tabs.overview` | `userManagement:userDetail.tabs.overview`, `events:eventPage.tabs.overview`, `partners:detail.tabs.overview`, `organizer:analytics.tabs.overview` |
| `common:archive.sort.label` | `userManagement:table.sortBy`, `partners:sort.label`, `organizer:topicBacklog.filters.sort`, `organizer:tasks.sortBy` |

> **Note:** Some common keys have awkward names (e.g., `common:company.batchImport.columns.status` for a generic "Status" label). As part of this sub-task, rename those common keys to a semantically generic path like `common:labels.status`, `common:labels.title`, `common:labels.date`, etc. — update all namespaces that already reference the old `common` key.

### Sub-task 1.2 — Add missing keys to `common` and consolidate

The following 80 values are duplicated across namespaces but have **no canonical `common` key** yet. Add each to `common.json`, then remove duplicates from other namespaces and update `t()` calls.

Top priority (highest duplication count):

| Suggested `common` key | Value | Currently in namespaces |
|------------------------|-------|-------------------------|
| `common:labels.company` | `"Company"` | events, organizer, partners, userManagement |
| `common:filters.status.confirmed` | `"Confirmed"` | events, organizer, userManagement |
| `common:labels.email` | `"Email"` | events, organizer, userManagement |
| `common:actions.confirm` | `"Confirm"` | events, userManagement |
| `common:labels.dueDate` | `"Due Date"` | events, organizer |
| `common:labels.emailAddress` | `"Email Address"` | auth, organizer |
| `common:actions.clearFilters` | `"Clear filters"` | events, userManagement |
| `common:filters.status.active` | `"Active"` | events, partners, userManagement |
| `common:filters.status.inactive` | `"Inactive"` | events, partners, userManagement |
| `common:filters.status.all` | `"All"` | events, organizer, partners, userManagement |

> Full list of 80 entries is in `/tmp/duplicates_report.txt`.

### Acceptance Criteria — Phase 1

- [ ] All 89 "already in common" duplicate keys removed from non-`common` namespaces, callers updated
- [ ] All 80 "not yet in common" values added to `common.json`, duplicates removed from other namespaces, callers updated
- [ ] All other locale files (`de`, `es`, `fr`, etc.) updated in sync — remove the same keys, add translations for the new `common` keys
- [ ] The 6 `t_call_in_test` usages in unit tests updated to reference the new canonical `common` key paths
- [ ] **Full unit test suite passes: `cd web-frontend && npm run test -- --run`** — 0 new failures vs baseline
- [ ] `npm run type-check` passes
- [ ] `npm run lint` passes
- [ ] Playwright smoke suite passes (at least `chromium` project)
- [ ] No runtime `i18next: missing key` warnings in browser console

---

## Phase 2 — Translate Hardcoded UI Text

**Goal:** Replace all hardcoded English strings in production `.tsx` files with `t()` calls.

**Excluded from this phase (intentionally kept in German for now):**
- `pages/public/PrivacyPage.tsx`
- `pages/public/SupportPage.tsx`

**Total scope (after exclusions):** 78 files, 254 hardcoded hits.

### Priority order

Work through the groups below in order — higher-visibility / user-facing first.

#### Group A — Public pages (41 hits, 4 files)

| File | Hits | Notes |
|------|------|-------|
| `pages/public/ConfirmRegistrationPage.tsx` | 17 | Registration flow — high visibility |
| `pages/public/RegistrationConfirmationPage.tsx` | 14 | High visibility |
| `pages/public/RegistrationSuccessPage.tsx` | 9 | High visibility |
| `pages/public/RegistrationPage.tsx` | 1 | |

#### Group B — Speaker portal (50 hits, 7 files)

| File | Hits | Notes |
|------|------|-------|
| `pages/speaker-portal/InvitationResponsePage.tsx` | 15 | Key speaker journey |
| `pages/speaker-portal/ContentSubmissionPage.tsx` | 12 | Key speaker journey |
| `pages/speaker-portal/ProfileUpdatePage.tsx` | 12 | |
| `pages/speaker-portal/SpeakerMagicLoginPage.tsx` | 1 | |
| `components/speaker-portal/ProfilePhotoUpload.tsx` | 5 | |
| `components/speaker-portal/PresentationUpload.tsx` | 5 | |

#### Group C — Organizer components (56 hits, ~20 files)

| File | Hits |
|------|------|
| `components/organizer/PartnerManagement/PartnerList.tsx` | 10 |
| `components/organizer/UserManagement/ParticipantBatchImportModal.tsx` | 7 |
| `components/organizer/PartnerManagement/PartnerDetailScreen.tsx` | 4 |
| `components/organizer/EventManagement/EventSearch.tsx` | 4 |
| `components/organizer/PartnerManagement/PartnerMeetingsTab.tsx` | 3 |
| `components/organizer/PartnerManagement/PartnerSettingsTab.tsx` | 3 |
| `components/Publishing/LivePreview/LivePreview.tsx` | 8 |
| `components/Publishing/PublishingTimeline/PublishingTimeline.tsx` | 1 |
| `components/Publishing/VersionControl/VersionControl.tsx` | 1 |
| *(remaining organizer files ≤2 hits each)* | |

#### Group D — Shared & user components (45 hits, ~20 files)

| File | Hits |
|------|------|
| `components/user/UserSettingsTab/UserSettingsTab.tsx` | 10 |
| `components/public/Registration/PersonalDetailsStep.tsx` | 8 |
| `components/user/UserProfileTab/UserProfileTab.tsx` | 5 |
| `components/shared/Event/EventBatchImportModal.tsx` | 5 |
| `components/user/ProfileHeader/ProfileHeader.tsx` | 4 |
| `components/shared/Company/CompanyDetailView.tsx` | 4 |
| `components/public/Registration/ConfirmRegistrationStep.tsx` | 4 |
| `components/TopicHeatMap/MultiTopicHeatMap.tsx` | 4 |
| `components/SlotAssignment/DragDropSlotAssignment/DragDropSlotAssignment.tsx` | 4 |
| `components/public/Registration/RegistrationWizard.tsx` | 3 |
| `components/public/Registration/CompanyAutocomplete.tsx` | 3 |
| `components/public/Event/CountdownTimer.tsx` | 3 |
| `components/shared/Company/CompanySearch.tsx` | 3 |
| `main.tsx`, `ErrorBoundary.tsx`, `components/shared/ErrorBoundary/ErrorBoundary.tsx` | 2+2+2 |
| *(remaining files 1-2 hits each)* | |

### Workflow per file

1. Open file, identify each hardcoded string
2. Check if a suitable key already exists in `common` (after Phase 1) or in the file's own namespace
3. If yes → use existing key. If no → add new key to the appropriate namespace
4. Replace hardcoded string with `t('namespace:key')` (or bare `t('key')` if `useTranslation` already declares the namespace)
5. **The EN translation value must be a character-for-character copy of the string being replaced** — same casing, same punctuation, same whitespace. This is what keeps co-located tests passing without touching them.
6. Add the same key to all other locale files (`de`, `es`, `fr`, `it`, `nl`, `fi`, `ja`, `rm`) — use English value as placeholder if translation not yet available, prefix with `[MISSING]` to make it findable
7. Prefer `common` namespace for generic labels (buttons, statuses, form labels)
8. **After each file (or small batch of files): run `cd web-frontend && npm run test -- --run` and fix any broken tests before continuing**

#### When a test breaks in Phase 2

Follow the test fix workflow from the Test Impact section above. The most common causes:

- **EN value differs from hardcoded string** → fix the EN translation value to match exactly
- **Test was asserting on placeholder/aria-label that is now translated** → update test assertion to match new EN value, or convert to `getByRole`
- **Component now needs `useTranslation` but the test doesn't provide i18n context** → unlikely (setup.ts initialises real i18n globally), but if it happens, wrap the render in `I18nextProvider`

### Acceptance Criteria — Phase 2

- [ ] 0 hardcoded user-visible strings in the 78 scoped files (re-run the analysis script)
- [ ] All new keys added to all locale files; missing translations prefixed `[MISSING]`
- [ ] **Full unit test suite passes: `cd web-frontend && npm run test -- --run`** — 0 new failures vs Phase 1 baseline
- [ ] **High-risk test files explicitly verified** (RegistrationWizard, InvitationResponsePage, ParticipantBatchImportModal, PartnerFilters, FilterSidebar, CompanyAutocomplete — see table in Test Impact section)
- [ ] `npm run type-check` passes
- [ ] `npm run lint` passes
- [ ] Playwright smoke suite passes (`chromium`, `speaker`, `partner` projects)
- [ ] No `i18next: missing key` warnings in browser console for EN locale

---

## Phase 3 — Translation-Independent Tests

**Goal:** Make the entire test suite (unit + E2E) resilient to translation changes. Tests must never hard-code English text strings. After this phase, renaming a translation value or moving a key to a different namespace cannot cause a test failure.

This phase has two parallel workstreams that can be done by different people simultaneously:
- **3A**: Add `data-testid` attributes to components (production code changes)
- **3B**: Refactor test assertions to use role-based or `data-testid` selectors (test-only changes)

3B is blocked on 3A for elements that need new `data-testid` anchors. Elements that already have a clear ARIA role (`button`, `heading`, `dialog`, `tab`, etc.) can be refactored in 3B immediately.

### Background — current state

| Test layer | Brittle patterns | Primary pattern |
|------------|-----------------|-----------------|
| Unit tests (Vitest) | ~4,400 assertions in 180 files | `screen.getByText('...')`, `screen.getByRole(..., { name: '...' })` with hardcoded EN strings |
| E2E tests (Playwright) | ~320 brittle patterns in 32 files | `locator('button:has-text("...")')`, `toContainText('...')`, `text=/regex/i` |

169 components already use `data-testid` — the pattern is established. The task is to extend it consistently and migrate assertions.

### Selector strategy (rule of thumb)

Apply these rules in order — use the first that applies:

| Priority | When to use | Example |
|----------|-------------|---------|
| 1 | Element has a unique semantic ARIA role + accessible name | `getByRole('button', { name: /save/i })` |
| 2 | Element is a form field with a label | `getByRole('textbox', { name: /email/i })` or `getByLabel(/email/i)` |
| 3 | Element has no distinctive role but is a key UI anchor | Add `data-testid="..."` to the component, then `getByTestId('...')` |
| 4 | Text content is the test subject itself (e.g. asserting an event title loaded) | `toContainText('BATbern57')` is acceptable — data, not UI chrome |

**Never use** `getByText` for button labels, headings, or form labels — these are translations. **Never use** `locator('button:has-text("...")')` — replace with `getByRole` or `getByTestId`.

### data-testid naming convention

```
[feature]-[component]-[element]

Examples:
  data-testid="event-list-create-btn"
  data-testid="speaker-invitation-accept-btn"
  data-testid="participant-table-row"
  data-testid="partner-filter-status-select"
  data-testid="slot-assignment-confirm-dialog"
  data-testid="user-detail-edit-roles-btn"
```

Rules:
- Kebab-case only
- Prefix with the feature/page area
- Suffix with element type for interactive elements (`-btn`, `-input`, `-select`, `-dialog`, `-tab`, `-panel`)
- Do NOT use `data-testid` on every element — only on elements that tests need to find and that have no clear role/label

### Sub-task 3A — Add data-testid to components

Focus on the components where E2E tests currently use `button:has-text(...)` or `text=/regex/i`. Highest priority:

| Component area | Brittle E2E patterns to fix | Action |
|----------------|----------------------------|--------|
| `speaker-onboarding-flow` | `button:has-text(...)` ×many | Add `data-testid` to all action buttons in speaker onboarding flow |
| `speaker-portal-response` | `text=/Invalid Link/i`, `text=/Link Expired/i` | Add `data-testid="invitation-error-invalid"`, `data-testid="invitation-error-expired"` to error states |
| `organizer/speaker-outreach` | `button:has-text(...)` ×many, `locator('h2')` | Add testids to action buttons; add `data-testid="section-heading"` or use `getByRole('heading')` |
| `slot-assignment-workflow` | `button:has-text(...)` ×4 | Add testids to Confirm/Cancel buttons in slot assignment dialog |
| `user-deletion` | `button:has-text(...)`, `locator('tbody tr')` | Add `data-testid="user-table-row"`, testids to delete confirmation buttons |
| `event-type-selection` | `button:has-text(...)`, `locator('h1')` | Use `getByRole('heading', { level: 1 })` for h1; add testids to type selection buttons |
| `speaker-status-tracking` | `locator('h2')` ×many, `button:has-text(...)` | Use `getByRole('heading')`; add testids to status change buttons |
| `registration-flow` | `locator('button, a')`, `locator('button')` | Add testids to wizard navigation buttons (Next, Back, Submit) |
| `archive-filtering` | `button:has-text(...)`, `locator('label')` | Add testids to filter controls |

Also add `data-testid` to these high-usage unit test targets (most-referenced in 4,400 assertions):
- Dialog confirm/cancel button pairs
- Table action buttons (edit, delete, view)
- Tab navigation items
- Status badge elements
- Pagination controls

### Sub-task 3B — Refactor test assertions

#### Unit tests (Vitest)

Replace in order of prevalence:

```ts
// ❌ Before — brittle text literal
screen.getByText('Cancel')
screen.getByRole('button', { name: 'Save Changes' })
screen.getByPlaceholderText('Enter email address')
screen.getByLabelText('Status')

// ✅ After — role-based (when role + name is distinctive)
screen.getByRole('button', { name: /cancel/i })
screen.getByRole('button', { name: /save/i })
screen.getByRole('textbox', { name: /email/i })
screen.getByRole('combobox', { name: /status/i })

// ✅ After — testid (when role alone is not distinctive)
screen.getByTestId('participant-import-cancel-btn')
screen.getByTestId('user-edit-roles-save-btn')
```

Note on `name` matching: use **regex with case-insensitive flag** (`/cancel/i`) instead of exact strings. This survives minor EN value tweaks (e.g., "Cancel" → "Cancel action").

#### E2E tests (Playwright)

```ts
// ❌ Before — brittle
page.locator('button:has-text("Accept Invitation")')
page.locator('text=/Invalid Link/i')
expect(page.locator('h1')).toContainText('Speaker Brainstorming')

// ✅ After — stable
page.getByTestId('invitation-accept-btn')
page.getByTestId('invitation-error-invalid')
page.getByRole('heading', { name: /speaker brainstorm/i })
```

Special cases noted in current E2E tests:
- `text=/Archiviert|Archived/i` — multi-language regex fallback: replace with `getByTestId('event-status-archived')`
- `text=/error|fehler|ungültig/i` — error state with German fallback: replace with `getByTestId('form-error-message')` or `getByRole('alert')`
- `text=BATbern57` — asserting on event data (not UI chrome): **keep as-is**, this is correct
- `locator('tbody tr')` — structural: replace with `getByTestId('user-table-row')` or `getByRole('row')`

### High-priority E2E files (most brittle patterns)

| Spec file | Brittle patterns | Priority |
|-----------|-----------------|----------|
| `e2e/speaker-onboarding-flow.spec.ts` | 48 | P1 |
| `e2e/speaker-portal-response.spec.ts` | 42 | P1 |
| `e2e/organizer/speaker-outreach.spec.ts` | 32 | P1 |
| `e2e/workflows/progressive-publishing/progressive-publishing-workflow.spec.ts` | 24 | P1 |
| `e2e/registration-flow.spec.ts` | 19 | P1 |
| `e2e/organizer/event-type-selection.spec.ts` | 16 | P2 |
| `e2e/organizer/speaker-status-tracking.spec.ts` | 14 | P2 |
| `e2e/organizer/speaker-brainstorming.spec.ts` | 12 | P2 |
| `e2e/workflows/user-management/user-list-search.spec.ts` | 12 | P2 |
| `e2e/workflows/slot-assignment/slot-assignment-workflow.spec.ts` | 10 | P2 |
| `e2e/workflows/user-management/user-deletion.spec.ts` | 10 | P2 |
| `e2e/organizer/speaker-invitation.spec.ts` | 10 | P2 |

### Acceptance Criteria — Phase 3

- [ ] Zero `button:has-text('...')` patterns remaining in any E2E spec file
- [ ] Zero `text=/translated string/i` locators remaining (except for data-content assertions like event titles)
- [ ] Zero `locator('h1')` / `locator('h2')` / `locator('label')` / `locator('tbody tr')` remaining — replaced with `getByRole` or `getByTestId`
- [ ] Zero `getByText('...')` calls with EN UI labels in unit tests — replaced with `getByRole(..., { name: /regex/i })` or `getByTestId`
- [ ] All new `data-testid` attributes follow the `[feature]-[component]-[element]` convention
- [ ] **Full unit test suite passes: `cd web-frontend && npm run test -- --run`**
- [ ] **Full Playwright suite passes: `npx playwright test`** (all three projects: `chromium`, `speaker`, `partner`)
- [ ] No `data-testid` added to elements that already have a unique ARIA role — reviewable via PR

---

## Phase 4 — Smarter Unused Key Analysis

**Goal:** Reduce the 830 "potentially unused" keys to an actionable short list, without manual review of all 830.

The Phase 1 analysis was conservative — a key is flagged as "unused" if its bare string doesn't appear in any `t('...')` call. But i18next allows **dynamic key construction** (e.g., `` t(`status.${state}`) ``) which the scanner cannot see. Phase 4 must account for this. Because Phase 3 has already migrated tests to use `t()` or `getByTestId`, the scanner now also sees keys referenced from tests — giving a more accurate picture of what is truly unused.

### Step 4.1 — Detect dynamic key patterns

Run a focused scan to find all dynamic `t()` calls in the source:

```
Pattern: t(`...${...}...`)  or  t(variable)  or  t(someKey + suffix)
```

For each dynamic call, extract:
- The **static prefix** (e.g., `workflow.` from `` t(`workflow.${state}`) ``)
- The **possible suffix values** (look for the variable's assignment or union type)

Build a list of all key **prefixes** that are referenced dynamically. Any translation key whose path starts with one of these prefixes must be kept even if no literal `t('ns:key')` call exists.

### Step 4.2 — Cross-reference with component prop drilling

Some keys are passed as props (`label={t('ns:key')}` resolved at a parent level). The scanner catches these fine if the key is a literal. But keys stored in config arrays or maps (e.g., a `columns` array with `label: t('...')`) may be missed if defined outside JSX. Scan for:

```
Pattern: label:\s*t\(  |  header:\s*t\(  |  title:\s*t\(  |  name:\s*t\(
```

### Step 4.3 — Generate tiered output

After steps 3.1 and 3.2, re-classify the 830 keys into three buckets:

| Bucket | Criteria | Action |
|--------|----------|--------|
| **Definitely unused** | Not matched by any literal, dynamic prefix, or prop pattern | Delete |
| **Possibly used dynamically** | Key prefix matches a dynamic `t()` call | Keep — mark in JSON with comment or separate tracking file |
| **Needs manual check** | Ambiguous — short key, might be dynamic | Short list (target: <50 keys) |

Output: a markdown report `docs/plans/i18n-unused-keys-report.md` with all three buckets, total counts, and delete commands for the "Definitely unused" bucket.

### Step 4.4 — Delete confirmed unused keys

For each key in the **Definitely unused** bucket:
1. Remove the key from the EN locale JSON
2. Remove the same key from all other locale files
3. Run `npm run type-check` and Playwright smoke to confirm nothing broke

### Acceptance Criteria — Phase 4

- [ ] Automated script (`scripts/i18n/analyze-unused.py`) committed to repo and runnable
- [ ] "Definitely unused" keys deleted from all locale files
- [ ] "Needs manual check" bucket is ≤ 50 keys (short enough for a focused review)
- [ ] **Full unit test suite passes after deletion: `cd web-frontend && npm run test -- --run`** — any failure after Phase 4 means a key was wrongly classified as unused; restore it and re-analyse
- [ ] `npm run type-check` passes
- [ ] `npm run lint` passes
- [ ] Playwright smoke suite passes

---

## Implementation Notes

### Key naming conventions (going forward)

```
common:actions.*       — buttons and CTA labels (save, cancel, delete, edit, confirm, retry, close)
common:labels.*        — generic field labels (date, title, status, email, company)
common:filters.*       — filter panel labels and status values
common:filters.status.* — shared status values (all, active, inactive, pending, confirmed)
common:navigation.*    — nav items and tab labels
common:errors.*        — generic error messages
common:loading.*       — loading/saving states
```

### Running the hardcoded-text scanner

```bash
cd web-frontend
python3 scripts/i18n/scan-hardcoded.py  # (to be committed in Phase 2)
```

### Running the unused-key analyzer (Phase 4)

```bash
python3 scripts/i18n/analyze-unused.py  # (to be committed in Phase 4)
```

### Locale file sync rule

Whenever a key is added to EN, it must be added to all other locales in the same commit. Use `[MISSING]` prefix for untranslated values so they are findable by CI or grep:

```bash
grep -r '\[MISSING\]' web-frontend/public/locales/
```

---

## Reference Data

| Artifact | Location |
|----------|----------|
| Full unused keys list (830) | `/tmp/unused_keys.txt` |
| Full duplicates report | `/tmp/duplicates_report.txt` |
| Full hardcoded text detail | `/tmp/hardcoded_prod_detail.txt` |
| EN locale files | `web-frontend/public/locales/en/*.json` |
| All locale files | `web-frontend/public/locales/` |
| Frontend source | `web-frontend/src/` |
