---
stepsCompleted: ["step-01-document-discovery", "step-02-prd-analysis", "step-03-epic-coverage-validation", "step-04-ux-architecture-validation", "step-05-final-assessment"]
story: "10.14"
date: "2026-03-01"
documents:
  story_artifact: "_bmad-output/implementation-artifacts/10-14-newsletter-sending-template-selection.md"
  prd: "docs/prd/epic-10-additional-stories.md"
  architecture:
    - "docs/architecture/05-frontend-architecture.md"
    - "docs/architecture/06-backend-architecture.md"
    - "docs/architecture/03-data-architecture.md"
  ux: null
verdict: "READY ✅"
---

# Implementation Readiness Assessment Report

**Date:** 2026-03-01
**Project:** BATbern
**Story:** 10.14 — Newsletter Sending with Template Selection
**Verdict:** ✅ READY FOR DEVELOPMENT

---

## PRD Analysis

### Functional Requirements

FR1: `NewsletterSendRequest` DTO/schema gains an optional `templateKey: string` field (nullable). If provided → use that template from DB; if omitted/null → use default `newsletter-event` (backward compatible).

FR2: `NewsletterEmailService.preview()` and `sendNewsletter()` accept an optional `templateKey` param. A private `resolveTemplateKey()` helper resolves `null`/blank to the `TEMPLATE_KEY` constant. All four internal methods (`preview`, `sendNewsletter`, `renderContent`, `buildSubject`) must propagate the effective key. `createSendAuditRecord()` must store the **effective** key.

FR3: `EventNewsletterTab.tsx` adds a "Template" select dropdown before the Send/Preview buttons. The dropdown is populated by `GET /api/v1/email-templates?category=NEWSLETTER` filtered to `locale === selectedLocale`. Default selection: `newsletter-event`.

FR4: When the organizer changes locale (DE↔EN), the template dropdown re-filters to show only templates for the newly selected locale. The `selectedTemplateKey` state resets to the default key, or the first available if the default is absent.

FR5: When "Preview" is clicked, `NewsletterSendRequest` includes the `templateKey` from the dropdown selection.

FR6: The send confirmation dialog reads: "Send newsletter using '**[Template Name]**' to N subscribers?" — using the selected template's key as the display name.

FR7: A "Create new template ↗" link below the dropdown navigates to `/organizer/admin?tab=email-templates`.

FR8: All existing Story 10.7 tests (`NewsletterEmailServiceTest`, `NewsletterControllerIntegrationTest`, `EventNewsletterTab` snapshots) must continue to pass without modification to test assertions.

FR9 (i18n): `newsletter.templateSelect.label` and `newsletter.templateSelect.createNew` keys added to `de/organizer.json` and `en/organizer.json`. `[MISSING]` prefix variants in 8 other locales. `eventPage.newsletter.confirmSendBody` updated to include `{{templateKey}}` in all 10 locales.

FR10 (ADR-006): `docs/api/events-api.openapi.yml` `NewsletterSendRequest` schema updated with optional `templateKey` **before** backend implementation.

**Total FRs: 10**

### Non-Functional Requirements

NFR1 (Contract-First / ADR-006): OpenAPI spec updated first, before any backend DTO changes.

NFR2 (TDD): RED phase — write failing unit tests in `NewsletterEmailServiceTest` targeting the new 5-param `preview()` signature before implementing.

NFR3 (Backward Compatibility): Requests without `templateKey` (existing callers, existing integration tests) must behave identically to Story 10.7 behavior. No breaking changes.

NFR4 (Audit Accuracy): `newsletter_sends.template_key` column must store the **actually used** key (the resolved effective key), not the hardcoded constant, to preserve accurate audit records when custom templates are used.

NFR5 (Code Quality): `npm run type-check` and `npm run lint` pass. No new hooks, no new backend endpoints — all reuse existing infrastructure.

NFR6 (No Type Regeneration): `newsletterService.ts::NewsletterSendRequest` is **manually defined** — do NOT run `npm run generate:api-types` for this change.

**Total NFRs: 6**

### Additional Requirements / Constraints

- Story 10.2 (`done`) and Story 10.7 (`done`) are confirmed prerequisites
- `GET /api/v1/email-templates?category=NEWSLETTER` already works via `EmailTemplateController` — no new endpoint needed
- `useEmailTemplates({ category: 'NEWSLETTER' })` hook already supports `category` param via `ListEmailTemplatesParams`
- `newsletterService.ts` functions `previewNewsletter()` and `sendNewsletter()` pass through `NewsletterSendRequest` as-is — adding `templateKey?` automatically includes it in the request body
- `useTranslation(['events', 'organizer'])` multi-namespace pattern confirmed working from Story 10.13
- 10 total locales: de, en, fr, it, rm, es, fi, nl, ja, gsw-BE

---

## Epic & Story Coverage Validation

### Prerequisites Confirmed

| Prerequisite | Status | Evidence |
|---|---|---|
| Story 10.2 — Email Template Management | ✅ done | `sprint-status.yaml:145` |
| Story 10.7 — Newsletter Subscription & Sending | ✅ done | `sprint-status.yaml:150` |
| `EmailTemplateController` with `category` filter | ✅ exists | Confirmed by `useEmailTemplates` hook |
| `useEmailTemplates(params)` hook | ✅ exists | `web-frontend/src/hooks/useEmailTemplates.ts:18` |
| `emailTemplateService.listTemplates({ category })` | ✅ exists | `emailTemplateService.ts:26` |

### AC → Task Traceability

| AC | Task(s) | Covered? |
|---|---|---|
| AC1: Optional `templateKey` on backend | T1 (OpenAPI), T3 (DTO), T5 (Controller) | ✅ |
| AC2: Service respects `templateKey` | T2 (tests RED), T4 (service impl) | ✅ |
| AC3: Template dropdown in UI | T7 (service type), T8 (component) | ✅ |
| AC4: Locale toggle re-filters templates | T8.6–T8.7 | ✅ |
| AC5: Preview uses selected template | T8.8 | ✅ |
| AC6: Confirmation shows template name | T8.11, T10.2/T9.2 | ✅ |
| AC7: "Create new template" link | T8.10 | ✅ |
| AC8: Existing 10.7 tests pass | T6 verification + note on existing tests | ✅ |
| AC9: i18n keys | T9 (EN), T10 (DE), T11 (8 others) | ✅ |
| AC10: OpenAPI first | T1 (Phase 1 starts with this) | ✅ |

---

## Architecture & Source File Validation

### All Referenced Files — Existence Check

| File | Exists? | Current State |
|---|---|---|
| `docs/api/events-api.openapi.yml` | ✅ | `NewsletterSendRequest` at line 4372: has `isReminder`, `locale` — missing `templateKey` → needs T1 |
| `services/.../dto/NewsletterSendRequest.java` | ✅ | Has `isReminder`, `locale` only → needs T3 |
| `services/.../service/NewsletterEmailService.java` | ✅ | 3-param `preview()`, 4-param `sendNewsletter()`, hardcoded `TEMPLATE_KEY` → needs T4 |
| `services/.../controller/NewsletterController.java` | ✅ | Passes 3-param to `preview()`, 4-param to `sendNewsletter()` → needs T5 |
| `services/.../service/NewsletterEmailServiceTest.java` | ✅ | Tests `buildVariables`, `buildSpeakersSection` — does NOT call `preview()` or `sendNewsletter()` directly → new tests T2 are pure additions |
| `services/.../controller/NewsletterControllerIntegrationTest.java` | ✅ | Tests via HTTP — unaffected by service method signature changes |
| `web-frontend/src/services/newsletterService.ts` | ✅ | `NewsletterSendRequest` has `isReminder`, `locale` only → needs T7 |
| `web-frontend/src/components/organizer/EventPage/EventNewsletterTab.tsx` | ✅ | Single namespace `useTranslation('events')`, no template state → needs T8 |
| `web-frontend/src/hooks/useEmailTemplates.ts` | ✅ | `useEmailTemplates(params?)` accepts `{ category }` → reuse directly, no changes |
| `web-frontend/public/locales/en/organizer.json` | ✅ | No `newsletter` key → needs T9 |
| `web-frontend/public/locales/de/organizer.json` | ✅ | No `newsletter` key → needs T10 |
| `web-frontend/public/locales/en/events.json` | ✅ | `confirmSendBody` exists at line 109, missing `{{templateKey}}` → needs T9.2 |

### Architecture Compliance

- **ADR-006 (Contract-First)**: Phase 1 task T1 explicitly updates `events-api.openapi.yml` first. ✅
- **No direct HTTP calls in frontend**: Uses `useEmailTemplates` hook → `emailTemplateService.listTemplates()` → `apiClient`. ✅
- **No `process.env` direct access**: N/A for this story. ✅
- **TDD**: T2 writes failing tests before T4 implements. ✅
- **PostgreSQL/Testcontainers**: No new DB migrations — `newsletter_sends.template_key` column already exists (used in `createSendAuditRecord` writing `TEMPLATE_KEY`). ✅
- **Shared Kernel**: No new shared types needed. ✅

---

## UX / Design Validation

No UX specification document for Epic 10. The story provides sufficient UI specification inline:
- Template `<Select>` with `<InputLabel>` (MUI FormControl pattern) — consistent with existing form controls in the tab
- "Create new template ↗" as a `<Link variant="caption">` — lightweight secondary action
- `data-testid="newsletter-template-select"` for test targeting
- No new navigation patterns; uses existing MUI components

This matches the patterns established in `EventParticipantsTab.tsx` (Story 10.13). ✅

---

## Issues & Risks

### ⚠️ Notes (Non-Blocking)

1. **`NewsletterRecipientRepository` mock missing in test setup** — The story's T2 test examples for `preview()` don't mock `NewsletterRecipientRepository`, which is fine since `preview()` doesn't write recipients. However, dev should double-check `@InjectMocks` is satisfied — `NewsletterEmailServiceTest` already has `@Mock NewsletterSendRepository sendRepository` which is injected. The new `NewsletterRecipientRepository` mock is NOT present in current test. For `preview()` tests this is safe (preview doesn't use it), but confirm `@InjectMocks` constructor injection works with the missing mock (Mockito fills with `null` for unused mocks in `@InjectMocks`). **Recommended**: add `@Mock private NewsletterRecipientRepository recipientRepository;` to the test class alongside the new tests to stay consistent and prevent future test failures if someone adds recipient logic to preview.

2. **`confirmSendBody` change is cross-cutting** — This key is used in all 10 locales' `events.json`. The story handles this with `[MISSING]` prefixes for non-DE/EN locales. Dev must update all 10 `events.json` files (not just organizer.json). T9.2, T10.2, T11.3 cover this. Verify gsw-BE is included (9th locale, often forgotten).

3. **`Link` MUI import** — `EventNewsletterTab.tsx` may or may not already have `Link` in its `@mui/material` import destructure. Dev should check line 1 of the component file before T8.10 to add if missing. Story note correctly flags this.

4. **Generated `NewsletterSendRequest.java`** — A generated version exists at `services/event-management-service/build/generated/src/main/java/ch/batbern/events/dto/generated/NewsletterSendRequest.java`. This is in the `build/` folder and NOT the one used in production code. The handwritten `dto/NewsletterSendRequest.java` is the one to modify. No risk of confusion as long as dev opens the correct file per the path in T3.1.

### ✅ No Blocking Issues Found

---

## Final Assessment

### Readiness Score

| Dimension | Score | Notes |
|---|---|---|
| PRD Completeness | ✅ 10/10 | All FRs and NFRs clearly specified |
| Task Granularity | ✅ 10/10 | 12 tasks, fine-grained subtasks, exact code snippets provided |
| Source File Existence | ✅ 12/12 | All referenced files confirmed in codebase |
| Prerequisite Completion | ✅ 2/2 | Stories 10.2 and 10.7 both `done` |
| Architecture Compliance | ✅ | ADR-006, TDD, no proxy uploads, no direct env access |
| AC → Task Traceability | ✅ 10/10 ACs covered | Every AC maps to at least one task |
| Backward Compatibility | ✅ | Null templateKey → existing behavior; no breaking API change |
| i18n Coverage | ✅ | 10 locales covered; `[MISSING]` pattern used correctly |

### Verdict: ✅ READY FOR DEVELOPMENT

Story 10.14 is fully specified and ready to implement. All prerequisite stories are complete, all source files exist, the implementation path is clear and well-bounded, and no architectural ambiguities remain.

**Recommended implementation order**: T1 → T2 → T3 → T4 → T5 → T6 → T7 → T8 → T9 → T10 → T11 → T12 (as written in the story artifact — Phase 1 through Phase 6).

**Estimated scope**: Small–medium. Backend changes are additive (new param + helper). Frontend changes are localized to one component + one service type + i18n files.
