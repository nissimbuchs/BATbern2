# Story 10.9: i18n Cleanup — Deduplication, Hardcoded Text, Test Resilience & Unused Key Removal

Status: ready-for-dev

## Story

As a **frontend developer and future contributor**,
I want the translation system to be clean, consistent, and test-resilient,
so that adding a new language or renaming a translation value never causes surprises in tests or the UI.

> **Source plan:** `docs/plans/i18n-cleanup-plan.md` — read this file first.
> **Phases must be executed in order** — Phase 1 establishes canonical keys; Phase 2 finishes adding all keys; Phase 3 makes tests language-agnostic (which also makes Phase 4's scanner more accurate); Phase 4 then deletes what remains unused.

---

## Acceptance Criteria

### Phase 1 — Consolidate Duplicate Translations
1. All 89 cross-namespace duplicate keys that already have a canonical `common` key are removed from non-`common` namespaces; all `t()` callers updated to the `common:` path.
2. All 80 cross-namespace duplicate values not yet in `common` are added to `common.json` under the `common:labels.*`, `common:filters.*`, `common:actions.*`, etc. hierarchy; duplicates removed from other namespaces; callers updated.
3. The 6 `t_call_in_test` usages in unit tests updated to reference the new `common:` key paths.
4. All other locale files (`de, fr, it, rm, es, fi, nl, ja`) updated in sync — same keys removed, same new keys added.
5. Full unit test suite passes with 0 new failures: `cd web-frontend && npm run test -- --run`
6. `npm run type-check` and `npm run lint` pass.
7. Playwright `chromium` project passes.
8. No `i18next: missing key` warnings in browser console for EN locale.

### Phase 2 — Translate Hardcoded UI Text
9. 0 hardcoded user-visible English strings in the 78 scoped files (re-run analysis script to verify).
10. Every new translation key added to all 9 locale files; non-EN values prefixed `[MISSING]` until translated.
11. EN translation value for each new key is a **character-for-character copy** of the hardcoded string it replaces (same casing, punctuation, whitespace).
12. Full unit test suite passes with 0 new failures vs Phase 1 baseline.
13. High-risk test files explicitly verified green (see Dev Notes).
14. `npm run type-check` and `npm run lint` pass.
15. Playwright `chromium`, `speaker`, and `partner` projects pass.
16. No `i18next: missing key` warnings in browser console for EN locale.

### Phase 3 — Translation-Independent Tests
17. Zero `button:has-text('...')` patterns remaining in any E2E spec file.
18. Zero `text=/translated string/i` locators remaining (except data-content assertions like event titles, e.g., `text=BATbern57`).
19. Zero `locator('h1')` / `locator('h2')` / `locator('label')` / `locator('tbody tr')` remaining — replaced with `getByRole` or `getByTestId`.
20. Zero `getByText('...')` calls with EN UI labels in unit tests — replaced with `getByRole(..., { name: /regex/i })` or `getByTestId`.
21. All new `data-testid` attributes follow the `[feature]-[component]-[element]` kebab-case convention.
22. Full unit test suite passes; full Playwright suite passes (all 3 projects).
23. No `data-testid` added where a unique ARIA role already exists.

### Phase 4 — Unused Key Analysis & Removal
24. Automated script `scripts/i18n/analyze-unused.py` committed and runnable.
25. "Definitely unused" keys deleted from all locale files.
26. "Needs manual check" bucket ≤ 50 keys.
27. Full unit test suite passes after deletion; `npm run type-check` and `npm run lint` pass.
28. Playwright smoke suite passes.
29. Output report committed to `docs/plans/i18n-unused-keys-report.md`.

---

## Tasks / Subtasks

### Phase 1: Consolidate Duplicate Translations (AC: #1–8)

- [ ] **1.1 — Redirect callers to existing `common` keys** (AC: #1, #3, #4)
  - [ ] For each entry in the Phase 1 Sub-task 1.1 table (plan line ~99-121), delete the duplicate key from its namespace JSON and update every `t()` call to use the `common:` path
  - [ ] Rename awkward `common` key paths to semantic generics during this pass (e.g., `common:company.batchImport.columns.status` → `common:labels.status`)
  - [ ] Update all other locales in the same commit for each key removed
  - [ ] Run `cd web-frontend && npm run test -- --run` after each file-batch; fix any failures before continuing
  - [ ] Update the 6 `t_call_in_test` usages to reference new canonical paths

- [ ] **1.2 — Add missing keys to `common` and consolidate** (AC: #2, #4)
  - [ ] Add all 80 missing values to `common.json` under the correct semantic hierarchy
  - [ ] Priority top-10 first (plan line ~132-143: `common:labels.company`, `common:filters.status.confirmed`, `common:labels.email`, `common:actions.confirm`, `common:labels.dueDate`, `common:labels.emailAddress`, `common:actions.clearFilters`, `common:filters.status.active/inactive/all`)
  - [ ] Full list at `/tmp/duplicates_report.txt`
  - [ ] Remove duplicate keys from other namespaces; update callers
  - [ ] Add all new keys to all 8 non-EN locale files (use English value as placeholder if not yet translated)
  - [ ] `npm run test -- --run` green; `npm run type-check` green; `npm run lint` green

- [ ] **1.3 — Phase 1 acceptance check** (AC: #5–8)
  - [ ] Re-run analysis to confirm 0 cross-namespace duplicates remain
  - [ ] Playwright `chromium` project passes
  - [ ] Verify no `i18next: missing key` in browser console (EN locale)

---

### Phase 2: Translate Hardcoded UI Text (AC: #9–16)

Work through groups A → B → C → D in order. After each file (or small batch): run `npm run test -- --run` and fix failures before continuing.

- [ ] **2.A — Public pages** (41 hits, 4 files) (AC: #9–12)
  - [ ] `pages/public/ConfirmRegistrationPage.tsx` (17 hits)
  - [ ] `pages/public/RegistrationConfirmationPage.tsx` (14 hits)
  - [ ] `pages/public/RegistrationSuccessPage.tsx` (9 hits)
  - [ ] `pages/public/RegistrationPage.tsx` (1 hit)
  - [ ] For each: check if key already exists in `common` (Phase 1) or own namespace; add if not; replace hardcode with `t()`; add to all 9 locales
  - [ ] `npm run test -- --run` green after this group

- [ ] **2.B — Speaker portal** (50 hits, 7 files) (AC: #9–12)
  - [ ] `pages/speaker-portal/InvitationResponsePage.tsx` (15 hits)
  - [ ] `pages/speaker-portal/ContentSubmissionPage.tsx` (12 hits)
  - [ ] `pages/speaker-portal/ProfileUpdatePage.tsx` (12 hits)
  - [ ] `pages/speaker-portal/SpeakerMagicLoginPage.tsx` (1 hit)
  - [ ] `components/speaker-portal/ProfilePhotoUpload.tsx` (5 hits)
  - [ ] `components/speaker-portal/PresentationUpload.tsx` (5 hits)
  - [ ] `npm run test -- --run` green; verify `InvitationResponsePage.test.tsx` (91 assertions) explicitly

- [ ] **2.C — Organizer components** (56 hits, ~20 files) (AC: #9–12)
  - [ ] `components/organizer/PartnerManagement/PartnerList.tsx` (10 hits)
  - [ ] `components/organizer/UserManagement/ParticipantBatchImportModal.tsx` (7 hits)
  - [ ] `components/Publishing/LivePreview/LivePreview.tsx` (8 hits)
  - [ ] Remaining organizer files (≤4 hits each — see plan for full list)
  - [ ] `npm run test -- --run` green; verify `ParticipantBatchImportModal.test.tsx` (74 assertions) and `PartnerFilters.test.tsx` (70 assertions)

- [ ] **2.D — Shared & user components** (45 hits, ~20 files) (AC: #9–12)
  - [ ] `components/user/UserSettingsTab/UserSettingsTab.tsx` (10 hits)
  - [ ] `components/public/Registration/PersonalDetailsStep.tsx` (8 hits)
  - [ ] `components/user/UserProfileTab/UserProfileTab.tsx` (5 hits)
  - [ ] `components/shared/Event/EventBatchImportModal.tsx` (5 hits)
  - [ ] `main.tsx`, `ErrorBoundary.tsx`, `components/shared/ErrorBoundary/ErrorBoundary.tsx`
  - [ ] Remaining shared/user files
  - [ ] `npm run test -- --run` green; verify `RegistrationWizard.test.tsx` (197 assertions), `FilterSidebar.test.tsx` (61), `CompanyAutocomplete.test.tsx` (63)

- [ ] **2.E — Phase 2 acceptance check** (AC: #13–16)
  - [ ] Re-run `python3 scripts/i18n/scan-hardcoded.py` to confirm 0 hits in the 78 scoped files
  - [ ] Playwright `chromium`, `speaker`, `partner` projects all pass
  - [ ] Verify no `i18next: missing key` in browser console

---

### Phase 3: Translation-Independent Tests (AC: #17–23)

3A (add `data-testid` to components) and 3B (refactor test assertions) can be done by different people concurrently, but 3B is blocked on 3A for elements that need new testid anchors. Elements with clear ARIA roles can go directly to 3B.

- [ ] **3A — Add `data-testid` to components** (AC: #21, #23)
  - [ ] Speaker onboarding flow — add testids to all action buttons
  - [ ] `invitation-error-invalid` / `invitation-error-expired` to speaker portal response error states
  - [ ] Organizer/speaker-outreach — testids to action buttons; use `getByRole('heading')` for h2
  - [ ] Slot assignment dialog — testids to Confirm/Cancel buttons
  - [ ] User management — `data-testid="user-table-row"`, testids to delete confirmation buttons
  - [ ] Event type selection — use `getByRole('heading', { level: 1 })` for h1; testids to type selection buttons
  - [ ] Speaker status tracking — `getByRole('heading')` for h2; testids to status change buttons
  - [ ] Registration flow — testids to wizard navigation buttons (Next, Back, Submit)
  - [ ] Archive filtering — testids to filter controls
  - [ ] Also add to: dialog confirm/cancel pairs, table action buttons, tab navigation items, status badges, pagination controls

- [ ] **3B — Refactor unit tests (Vitest)** (AC: #20, #22)
  - [ ] Replace `screen.getByText('...')` with `getByRole(..., { name: /regex/i })` or `getByTestId`
  - [ ] Replace `screen.getByRole('button', { name: 'Exact String' })` with regex name
  - [ ] Replace `screen.getByPlaceholderText('...')` / `screen.getByLabelText('...')` with role-based equivalents
  - [ ] Priority: 180 files with text assertions — start with the high-risk files listed in plan
  - [ ] `npm run test -- --run` green after each file batch

- [ ] **3C — Refactor E2E tests (Playwright)** (AC: #17–19, #22)
  - [ ] `e2e/speaker-onboarding-flow.spec.ts` (48 brittle patterns) — P1
  - [ ] `e2e/speaker-portal-response.spec.ts` (42 brittle patterns) — P1
  - [ ] `e2e/organizer/speaker-outreach.spec.ts` (32) — P1
  - [ ] `e2e/workflows/progressive-publishing/progressive-publishing-workflow.spec.ts` (24) — P1
  - [ ] `e2e/registration-flow.spec.ts` (19) — P1
  - [ ] Remaining P2 files (event-type-selection, speaker-status-tracking, etc.)
  - [ ] Replace `button:has-text('...')` → `getByTestId` or `getByRole`
  - [ ] Replace `text=/Archiviert|Archived/i` → `getByTestId('event-status-archived')`
  - [ ] Replace `text=/error|fehler|ungültig/i` → `getByTestId('form-error-message')` or `getByRole('alert')`
  - [ ] Keep `text=BATbern57` style assertions (data content, not UI chrome)
  - [ ] `npx playwright test` (all 3 projects) green after all E2E refactors

---

### Phase 4: Unused Key Analysis & Removal (AC: #24–29)

- [ ] **4.1 — Detect dynamic key patterns**
  - [ ] Scan for `` t(`...${...}...`) ``, `t(variable)`, `t(someKey + suffix)` patterns
  - [ ] Extract static prefixes; build prefix exclusion list

- [ ] **4.2 — Cross-reference prop drilling patterns**
  - [ ] Scan for `label:\s*t\(`, `header:\s*t\(`, `title:\s*t\(`, `name:\s*t\(` in config arrays

- [ ] **4.3 — Generate tiered output**
  - [ ] Classify 830 flagged keys into: Definitely unused / Possibly used dynamically / Needs manual check
  - [ ] Write `docs/plans/i18n-unused-keys-report.md` with all three buckets + delete commands

- [ ] **4.4 — Delete confirmed unused keys**
  - [ ] Remove "Definitely unused" keys from EN locale; remove same keys from all other locales
  - [ ] `npm run test -- --run` green; `npm run type-check` green; Playwright smoke green
  - [ ] Commit `scripts/i18n/analyze-unused.py`

---

## Dev Notes

### Critical: How Tests See Translations

Tests run with real i18n (NOT mocks). `src/test/setup.ts` imports `src/i18n/config` and calls `i18n.changeLanguage('en')`. Tests assert on **rendered English text**, not key names.

**Consequence for Phase 2:** The EN value for every new `t()` key MUST be a character-for-character copy of the hardcoded string it replaces. Even one character difference breaks co-located tests.

**When a test breaks:**
1. Check if the EN translation value differs from the hardcoded string → fix the translation value
2. Check if `useTranslation` namespace declaration is missing/wrong in the component → fix it
3. Only after fixing the component, update the test assertion
4. **Never** write a test that asserts on a translation key path (`events:form.cancel`) — that indicates broken component setup

### i18n Configuration Facts (don't reinvent)

- Config: `web-frontend/src/i18n/config.ts`
- All 9 locales bundled (not lazy-loaded): `de, en, fr, it, rm, es, fi, nl, ja`
- 10 namespaces: `common, auth, validation, userManagement, events, partners, organizer, about, registration`
- `fallbackLng: 'de'` (not `en` — important for missing key debugging)
- `defaultNS: 'common'`
- Language stored in `localStorage` key `'batbern-language'`
- Detection order: `['localStorage', 'htmlTag']` — **do NOT add `navigator`** (known bug)
- Do **NOT** use `lng:` option alongside LanguageDetector in the `init()` call — it overrides detection

### Locale File Structure

```
web-frontend/public/locales/
  {lang}/          # de, en, fr, it, rm, es, fi, nl, ja
    about.json
    auth.json
    common.json    # canonical home for generic tokens
    events.json
    organizer.json
    partners.json
    registration.json
    userManagement.json
    validation.json
```

All edits to EN locale files must be mirrored in all 8 other locale directories in the same commit. For untranslated new keys: use the English value prefixed with `[MISSING]`.

Find all missing values: `grep -r '\[MISSING\]' web-frontend/public/locales/`

### Key Naming Hierarchy (canonical)

```
common:actions.*           — buttons/CTAs (save, cancel, delete, edit, confirm, retry, close)
common:labels.*            — generic field labels (date, title, status, email, company, dueDate)
common:filters.*           — filter panel labels
common:filters.status.*    — shared status values (all, active, inactive, pending, confirmed)
common:navigation.*        — nav items and tab labels
common:errors.*            — generic error messages
common:loading.*           — loading/saving states
```

**Awkward existing `common` keys to rename in Phase 1:**
- `common:company.batchImport.columns.status` → `common:labels.status`
- `common:event.batchImport.columns.title` → `common:labels.title`
- `common:event.batchImport.columns.date` → `common:labels.date`
- `common:company.backToList` → `common:actions.back`
- `common:company.detail.tabs.overview` → `common:labels.overview` (or `common:navigation.overview`)
- `common:archive.sort.label` → `common:labels.sortBy`
- `common:speakerPortal.dashboard.actions` → `common:labels.actions`
- `common:company.batchImport.status.pending` → `common:filters.status.pending`
- `common:company.batchImport.status.error` → `common:filters.status.error`

When renaming these, update all callers — including any already in `common` that reference these paths.

### `data-testid` Naming Convention (Phase 3)

```
[feature]-[component]-[element]

Examples:
  event-list-create-btn
  speaker-invitation-accept-btn
  participant-table-row
  partner-filter-status-select
  slot-assignment-confirm-dialog
  invitation-error-invalid
  invitation-error-expired
  event-status-archived
  form-error-message
```

Rules:
- Kebab-case only
- Prefix with feature/page area
- Suffix with type for interactive elements (`-btn`, `-input`, `-select`, `-dialog`, `-tab`, `-panel`)
- Do NOT add `data-testid` to elements that already have a unique ARIA role (button, heading, dialog, tab, alert, etc.)
- 169 components already use `data-testid` — check what already exists before adding

### Selector Priority in Tests (Phase 3)

1. `getByRole('button', { name: /regex/i })` — when role + name is distinctive
2. `getByLabel(/label/i)` or `getByRole('textbox', { name: /label/i })` — form fields
3. `getByTestId('feature-component-element')` — when no distinctive role/label
4. `toContainText('BATbern57')` — for data content assertions (keep these as-is)

Use **regex with case-insensitive flag** in `name:` — never exact strings. Regex survives minor EN value tweaks.

### Phase 2 — Excluded Files

These two files are intentionally kept in German and excluded from Phase 2 scope:
- `pages/public/PrivacyPage.tsx`
- `pages/public/SupportPage.tsx`

### High-Risk Test Files (most assertions — verify explicitly after Phase 2)

| Test file | Assertions | Related component |
|-----------|-----------|-------------------|
| `components/public/Registration/__tests__/RegistrationWizard.test.tsx` | 197 | Registration flow |
| `pages/speaker-portal/__tests__/InvitationResponsePage.test.tsx` | 91 | Speaker portal |
| `components/organizer/UserManagement/ParticipantBatchImportModal.test.tsx` | 74 | Organizer |
| `components/organizer/PartnerManagement/__tests__/PartnerFilters.test.tsx` | 70 | Organizer |
| `components/public/__tests__/FilterSidebar.test.tsx` | 61 | Public |
| `components/public/Registration/__tests__/CompanyAutocomplete.test.tsx` | 63 | Registration flow |

### High-Priority E2E Spec Files (Phase 3)

| Spec file | Brittle patterns |
|-----------|-----------------|
| `e2e/speaker-onboarding-flow.spec.ts` | 48 |
| `e2e/speaker-portal-response.spec.ts` | 42 |
| `e2e/organizer/speaker-outreach.spec.ts` | 32 |
| `e2e/workflows/progressive-publishing/progressive-publishing-workflow.spec.ts` | 24 |
| `e2e/registration-flow.spec.ts` | 19 |

### Reference Data

| Artifact | Location |
|----------|----------|
| Full unused keys list (830) | `/tmp/unused_keys.txt` |
| Full duplicates report | `/tmp/duplicates_report.txt` |
| Full hardcoded text detail | `/tmp/hardcoded_prod_detail.txt` |
| Plan document | `docs/plans/i18n-cleanup-plan.md` |
| EN locale files | `web-frontend/public/locales/en/*.json` |
| All locale files | `web-frontend/public/locales/` |
| i18n config | `web-frontend/src/i18n/config.ts` |
| Test setup | `web-frontend/src/test/setup.ts` |

### Test Commands

```bash
# Unit tests (run after every file batch in Phases 1–4)
cd web-frontend && npm run test -- --run

# Type check
cd web-frontend && npm run type-check

# Lint
cd web-frontend && npm run lint

# E2E — organizer (Phase 3, Phase 4)
cd web-frontend && npx playwright test --project=chromium

# E2E — speaker
cd web-frontend && SPEAKER_AUTH_TOKEN=$(jq -r .idToken ~/.batbern/staging-speaker.json) npx playwright test --project=speaker

# E2E — partner
cd web-frontend && PARTNER_AUTH_TOKEN=$(jq -r .idToken ~/.batbern/staging-partner.json) npx playwright test --project=partner

# Phase 2 hardcoded scanner
cd web-frontend && python3 scripts/i18n/scan-hardcoded.py

# Phase 4 unused key analyzer
cd web-frontend && python3 scripts/i18n/analyze-unused.py
```

### Anti-Pattern Prevention

- **Do NOT** write `t('some:key')` directly in test assertions — this means the component setup is broken
- **Do NOT** mock `i18n` in tests — real i18n is already initialized via `src/test/setup.ts`
- **Do NOT** change the EN translation value to differ from the hardcoded string it replaces — this breaks tests silently
- **Do NOT** add a new `common:` key with a namespace-specific path name (e.g., `common:events.form.cancel` is wrong; correct is `common:actions.cancel`)
- **Do NOT** edit the `fallbackLng` in `i18n/config.ts` — it is `de` intentionally
- **Do NOT** add translations only to EN and forget all 8 other locales — always add to all 9 in the same commit
- **Do NOT** delete a key from Phase 4 if it has a prefix that matches a dynamic `t()` call pattern

### Project Structure Notes

All translation JSON edits are **pure JSON changes** — no build step, no codegen, no DB migration needed.

The i18n config (`src/i18n/config.ts`) bundles all locale files at compile time via static imports. Adding new keys to existing JSON files does not require changes to `config.ts`.

If a new **namespace** were needed (not required for this story), it would require adding imports + resources entries to `config.ts`. That is NOT needed for this story — work within the 10 existing namespaces.

### References

- [Source: docs/plans/i18n-cleanup-plan.md] — full plan with all lists, tables, and Sub-task 1.1/1.2 entries
- [Source: web-frontend/src/i18n/config.ts] — i18n initialization, locale bundles, namespace declarations
- [Source: web-frontend/src/test/setup.ts] — test i18n initialization (real i18n, `changeLanguage('en')`)
- [Source: web-frontend/public/locales/] — all locale JSON files for all 9 languages

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

### File List
