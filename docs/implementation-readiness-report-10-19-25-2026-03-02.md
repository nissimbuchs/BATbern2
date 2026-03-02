---
stepsCompleted: ["step-01-document-discovery", "step-02-prd-analysis", "step-03-epic-coverage-validation", "step-04-ux-alignment", "step-05-epic-quality-review", "step-06-final-assessment"]
documentsUsed:
  prd: "docs/prd/epic-10-additional-stories.md"
  architecture:
    - "docs/architecture/05-frontend-architecture.md"
    - "docs/architecture/06-backend-architecture.md"
  epics: "docs/prd/epic-10-additional-stories.md"
  ux: null
scope: "Stories 10.19–10.25"
---

# Implementation Readiness Assessment Report

**Date:** 2026-03-02
**Project:** BATbern
**Scope:** Epic 10 — Stories 10.19–10.25

---

## PRD Analysis

### Functional Requirements

**Story 10.19 — Remove Organizer Email from Public Pages**
- FR1: Remove `mailto:` email block from `OrganizerDisplay.tsx` on `/about`
- FR2: Audit `PresentationPage.tsx` and child components for email renders; remove all public-facing email renders
- FR3: Retain general contact email `info@berner-architekten-treffen.ch` in About Contact section
- FR4: Organizer email field remains in DB and API — only UI render removed
- FR5: No new i18n keys needed; remove email-labeling key usages if solely used for removed email display

**Story 10.20 — Legacy BAT Format Data Export & Import**
- FR6: `GET /api/v1/admin/export/legacy` — returns legacy BAT JSON envelope; organizer-only (403 others); Content-Disposition download
- FR7: `GET /api/v1/admin/export/assets` — returns signed S3 ZIP URL or presigned URL manifest for portraits, logos, presentations
- FR8: `POST /api/v1/admin/import/legacy` — multipart; upsert by legacy ID/event code; returns `{ imported, skipped, errors }`; idempotent; 400 with structured errors on validation failure
- FR9: Attendee registrations imported as `registered` status (no waitlist logic during import)
- FR10: `POST /api/v1/admin/import/assets` — accepts ZIP; unpacks to S3 under `imports/{timestamp}/`; links by filename convention
- FR11: Frontend Admin Tab 5 "Export / Import" added to `EventManagementAdminPage.tsx`
- FR12: Export section: "Export All Data (JSON)" and "Export Assets" buttons
- FR13: Import section: JSON file picker + "Import" button with result summary; ZIP file picker; confirmation dialog before any import

**Story 10.21 — Event Photos Gallery**
- FR14: Flyway V74: `event_photos` table (`id UUID PK`, `event_code FK`, `s3_key TEXT NOT NULL`, `display_url TEXT NOT NULL`, `filename TEXT`, `uploaded_at TIMESTAMPTZ`, `uploaded_by VARCHAR`, `sort_order INT DEFAULT 0`)
- FR15: `GET /api/v1/events/{eventCode}/photos` — public, returns `List<EventPhotoResponse>`
- FR16: `POST /api/v1/events/{eventCode}/photos/upload-url` — organizer-only, presigned S3 URL + photoId
- FR17: `POST /api/v1/events/{eventCode}/photos/confirm` (body: photoId) — organizer-only, marks upload complete, stores display_url
- FR18: `DELETE /api/v1/events/{eventCode}/photos/{photoId}` — organizer-only, deletes DB record + S3 object
- FR19: `GET /api/v1/events/recent-photos?limit=20&lastNEvents=5` — public, random sample from last N events
- FR20: Frontend: new "Photos" tab in `EventPage.tsx` with `EventPhotosTab.tsx` — 3-4 col grid, upload flow, per-photo delete with confirmation
- FR21: Homepage `TestimonialSection.tsx` first marquee row replaced with real photos via `useRecentEventPhotos`; fallback to current testimonials if < 3 photos
- FR22: Archive event detail page: "Photos" marquee section showing event photos; hidden if 0 photos

**Story 10.22 — Event Teaser Image on Moderator Presentation Page**
- FR23: Flyway V75: `teaser_image_s3_key TEXT` and `teaser_image_url TEXT` (both nullable) added to `events` table
- FR24: `EventResponse` DTO: add `teaserImageUrl: String` (null if not set)
- FR25: `POST /api/v1/events/{eventCode}/teaser-image/upload-url` — organizer-only, presigned S3 PUT URL + expiry
- FR26: `POST /api/v1/events/{eventCode}/teaser-image/confirm` (body: `{ s3Key }`) — stores columns + CloudFront URL
- FR27: `DELETE /api/v1/events/{eventCode}/teaser-image` — clears DB columns + deletes S3 object
- FR28: Frontend: `EventSettingsTab.tsx` new "Teaser Image" subsection — thumbnail preview, upload flow, Remove button
- FR29: Frontend: `PresentationPage.tsx` inserts `TEASER_IMAGE` slide after topic intro, before first agenda slide; slide omitted when `teaserImageUrl` is null

**Story 10.23 — Event Description Section on Public Homepage**
- FR30: New `EventDescriptionSection.tsx` rendered in homepage after `HeroSection`, before speakers/agenda sections
- FR31: Section rendered only when `activeEvent.description` is non-null and non-empty; completely absent otherwise
- FR32: Description rendered with `white-space: pre-wrap` or `\n → <br/>` conversion
- FR33: Optional section heading `t('events.description.heading')` → "About This Event"
- FR34: Archive event detail page shows same description block below event metadata, above sessions list

**Story 10.24 — AWS Cognito User Provisioning from User Management**
- FR35: New `CognitoAdminService.java`: calls `AdminCreateUser` (`MessageAction = SUPPRESS`), `AdminAddUserToGroup`; sends custom welcome email; catches `UsernameExistsException` → 409
- FR36: On success, updates `users.cognito_user_id` with new Cognito `sub`
- FR37: `POST /api/v1/users/{userId}/provision-cognito` — organizer-only; body `{ language }`; returns `{ cognitoUsername, temporaryPasswordSent: true }`; 409 if Cognito conflict or already linked
- FR38: CDK IAM: ECS task role granted `cognito-idp:AdminCreateUser` + `cognito-idp:AdminAddUserToGroup` scoped to user pool ARN
- FR39: `UserResponse` adds `hasCognitoAccount: boolean` (raw `cognitoUserId` NOT exposed)
- FR40: `UserTable.tsx`: "Create AWS Account" action rendered only when `hasCognitoAccount === false`
- FR41: New `LinkCognitoDialog.tsx`: confirmation dialog with language selector, success auto-close (2s), error states (409 + generic)
- FR42: `UserCreateEditModal.tsx` create mode: "Also create AWS Cognito account" toggle with language selector; provision called after DB user creation; Cognito failure → warning toast, user still saved
- FR43: Welcome email templates `user-welcome-temp-password-de/en.html` seeded by `EmailTemplateSeedService`

**Story 10.25 — Partner Meeting iCal Auto-creation & Year-End Reminders**
- FR44: Event creation fires `EventCreatedEvent`; `PartnerMeetingAutoCreateListener` calls `PartnerCoordinationClient` to auto-create meeting (best-effort; failure logged, event creation not rolled back)
- FR45: Auto-created meeting: date from event, startTime=12:00, endTime=14:00, location=empty, type=SPRING, agenda/notes=null, `inviteSentAt`=null
- FR46: Auto-creation idempotent — `existsByEventCode()` guard; skip if meeting already exists
- FR47: `PartnerInviteEmailService.buildEmailBody()` replaced with DB-backed template loading; classpath fallback
- FR48: Templates `partner-meeting-invite-de/en.html` seeded in `partner-coordination-service`; appear in Admin → Email Templates under PARTNER category
- FR49: New `YearEndReminderScheduler.java` (ShedLock, cron `0 0 8 31 12 *`): creates 2 organizer tasks on 31 December; due 31 January next year; assigned to all ORGANIZER users; idempotent (checks title + year)

**Total FRs: 49**

---

### Non-Functional Requirements

- NFR1: (10.19) Type-check + lint must pass; no test changes required (pure UI removal)
- NFR2: (10.20) Import endpoint returns 400 with structured error list on validation failure
- NFR3: (10.20) TDD: `LegacyExportServiceTest` + `LegacyImportServiceTest`; integration round-trip test
- NFR4: (10.20) OpenAPI spec committed before implementation (ADR-006)
- NFR5: (10.20) i18n: `admin.exportImport.*` keys in de/en; Type-check + Checkstyle pass
- NFR6: (10.21) TDD: `EventPhotoServiceTest` covers upload confirmation, delete, recent-photos query
- NFR7: (10.21) OpenAPI spec committed before implementation (ADR-006)
- NFR8: (10.21) i18n: `photos.*` keys in de/en; Type-check + Checkstyle pass
- NFR9: (10.22) V75 runs cleanly; existing events unaffected (nullable columns)
- NFR10: (10.22) TDD: `EventTeaserImageServiceTest`; integration test verifies DB state
- NFR11: (10.22) OpenAPI spec committed before implementation (ADR-006)
- NFR12: (10.22) i18n: `teaserImage.*` keys in de/en; Type-check + Checkstyle pass
- NFR13: (10.23) No backend changes; no DB migrations
- NFR14: (10.23) i18n: `events.description.heading` in all 10 locales; Type-check + lint pass
- NFR15: (10.24) Backend uses ECS IAM task role — organizer Cognito JWT has zero AWS IAM rights
- NFR16: (10.24) 409 on `UsernameExistsException` — no duplicate created, DB unchanged
- NFR17: (10.24) TDD: `CognitoAdminServiceTest` covers success, UsernameExistsException, SDK error, already-linked guard
- NFR18: (10.24) OpenAPI spec (users-api) committed before implementation (ADR-006)
- NFR19: (10.24) i18n: `actions.createAwsAccount`, `linkCognito.*` keys in de/en userManagement namespace; Type-check + Checkstyle pass
- NFR20: (10.25) Auto-creation failure must not fail or roll back event creation; error only logged
- NFR21: (10.25) `YearEndReminderScheduler` ShedLock-protected (no duplicate runs in multi-node)
- NFR22: (10.25) TDD: `PartnerMeetingAutoCreateListenerTest`, `YearEndReminderSchedulerTest`, `PartnerInviteEmailServiceTest`
- NFR23: (10.25) No new public API endpoints — no OpenAPI changes needed
- NFR24: (10.25) Checkstyle + Type-check pass

**Total NFRs: 24**

---

### Additional Requirements / Constraints

- **10.19**: Prerequisite — none (independent)
- **10.20**: Prerequisite — Story 10.1 (Admin page must exist for Tab 5)
- **10.21**: Prerequisite — Story 10.1 (Admin page); independent otherwise
- **10.22**: Prerequisite — Story 10.8a (PresentationPage must exist)
- **10.23**: Prerequisite — none (description field already in DB/API)
- **10.24**: Prerequisite — Epic 9 Story 9.1 (Cognito integration patterns established)
- **10.25**: Prerequisites — Story 8.3 (partner meeting infrastructure) + Story 10.2 (DB-backed email templates)
- **ADR-006 compliance**: Stories 10.20, 10.21, 10.22, 10.24 require OpenAPI spec committed first
- **S3 presigned URL pattern**: Stories 10.21, 10.22 must follow same pattern as existing logo/presentation uploads
- **Flyway migration numbering**: V74 (10.21), V75 (10.22) — must not conflict with existing migrations

### PRD Completeness Assessment

The PRD is detailed and well-structured. Each story includes: user story, background, scope (backend + frontend), key files, and Definition of Done. The architectural clarification in 10.24 (IAM vs Cognito JWT) is particularly solid. Minor gaps: no explicit NFRs section — requirements are embedded in scope and DoD.

---

## Epic Coverage Validation

> **Note:** In this project, the PRD and the Epics/Stories document are the same file (`docs/prd/epic-10-additional-stories.md`). All FRs were extracted directly from the story scopes within that document. Epic coverage is therefore inherently 1:1; the validation focuses on prerequisite readiness and story assignment clarity.

### Coverage Matrix

| FR | Requirement Summary | Story | Status |
|---|---|---|---|
| FR1 | Remove mailto block from OrganizerDisplay.tsx | 10.19 | ✅ Covered |
| FR2 | Audit + remove email renders from PresentationPage.tsx | 10.19 | ✅ Covered |
| FR3 | Retain general contact email in About Contact section | 10.19 | ✅ Covered |
| FR4 | Organizer email stays in DB/API; UI-only removal | 10.19 | ✅ Covered |
| FR5 | Remove email-labeling i18n key usages if orphaned | 10.19 | ✅ Covered |
| FR6 | GET /api/v1/admin/export/legacy endpoint | 10.20 | ✅ Covered |
| FR7 | GET /api/v1/admin/export/assets endpoint | 10.20 | ✅ Covered |
| FR8 | POST /api/v1/admin/import/legacy upsert endpoint | 10.20 | ✅ Covered |
| FR9 | Attendees imported as `registered` status | 10.20 | ✅ Covered |
| FR10 | POST /api/v1/admin/import/assets ZIP endpoint | 10.20 | ✅ Covered |
| FR11 | Admin Tab 5 "Export / Import" in AdminPage.tsx | 10.20 | ✅ Covered |
| FR12 | Export section with JSON + Assets buttons | 10.20 | ✅ Covered |
| FR13 | Import section with file pickers + confirmation dialog | 10.20 | ✅ Covered |
| FR14 | Flyway V74: event_photos table | 10.21 | ✅ Covered |
| FR15 | GET /api/v1/events/{eventCode}/photos (public) | 10.21 | ✅ Covered |
| FR16 | POST photos/upload-url (organizer) | 10.21 | ✅ Covered |
| FR17 | POST photos/confirm (organizer) | 10.21 | ✅ Covered |
| FR18 | DELETE photos/{photoId} (organizer) | 10.21 | ✅ Covered |
| FR19 | GET /api/v1/events/recent-photos (public) | 10.21 | ✅ Covered |
| FR20 | Frontend EventPhotosTab in EventPage | 10.21 | ✅ Covered |
| FR21 | Homepage TestimonialSection replaced with real photos + fallback | 10.21 | ✅ Covered |
| FR22 | Archive event detail photos marquee section | 10.21 | ✅ Covered |
| FR23 | Flyway V75: teaser_image columns on events | 10.22 | ✅ Covered |
| FR24 | EventResponse: teaserImageUrl field | 10.22 | ✅ Covered |
| FR25 | POST teaser-image/upload-url (organizer) | 10.22 | ✅ Covered |
| FR26 | POST teaser-image/confirm (organizer) | 10.22 | ✅ Covered |
| FR27 | DELETE teaser-image (organizer) | 10.22 | ✅ Covered |
| FR28 | EventSettingsTab Teaser Image subsection | 10.22 | ✅ Covered |
| FR29 | PresentationPage TEASER_IMAGE slide | 10.22 | ✅ Covered |
| FR30 | EventDescriptionSection component on homepage | 10.23 | ✅ Covered |
| FR31 | Section absent when description null/empty | 10.23 | ✅ Covered |
| FR32 | white-space: pre-wrap / \n→br rendering | 10.23 | ✅ Covered |
| FR33 | Section heading i18n key | 10.23 | ✅ Covered |
| FR34 | Archive event detail also shows description | 10.23 | ✅ Covered |
| FR35 | CognitoAdminService: AdminCreateUser + AdminAddUserToGroup | 10.24 | ✅ Covered |
| FR36 | Update users.cognito_user_id on success | 10.24 | ✅ Covered |
| FR37 | POST /api/v1/users/{userId}/provision-cognito | 10.24 | ✅ Covered |
| FR38 | CDK IAM: ECS task role Cognito permissions | 10.24 | ✅ Covered |
| FR39 | UserResponse: hasCognitoAccount boolean | 10.24 | ✅ Covered |
| FR40 | UserTable: conditional "Create AWS Account" action | 10.24 | ✅ Covered |
| FR41 | LinkCognitoDialog with confirmation + states | 10.24 | ✅ Covered |
| FR42 | UserCreateEditModal create-mode toggle | 10.24 | ✅ Covered |
| FR43 | Welcome email templates seeded | 10.24 | ✅ Covered |
| FR44 | EventCreatedEvent → PartnerMeetingAutoCreateListener | 10.25 | ✅ Covered |
| FR45 | Auto-created meeting defaults (12:00–14:00, SPRING, etc.) | 10.25 | ✅ Covered |
| FR46 | Auto-creation idempotency guard | 10.25 | ✅ Covered |
| FR47 | Replace buildEmailBody() with DB-backed template | 10.25 | ✅ Covered |
| FR48 | Partner invite templates seeded under PARTNER category | 10.25 | ✅ Covered |
| FR49 | YearEndReminderScheduler (ShedLock, 31 Dec, 2 tasks) | 10.25 | ✅ Covered |

### Prerequisite Readiness

| Story | Prerequisite | Prerequisite Status |
|---|---|---|
| 10.19 | None | ✅ Independent |
| 10.20 | Story 10.1 (Admin page) | ⚠️ `ready-for-dev` — not yet delivered |
| 10.21 | Story 10.1 (Admin page) | ⚠️ `ready-for-dev` — not yet delivered |
| 10.22 | Story 10.8a (PresentationPage) | ⚠️ `ready-for-dev` — not yet delivered |
| 10.23 | None | ✅ Independent |
| 10.24 | Epic 9 Story 9.1 (Cognito JWT) | ✅ Done (per CLAUDE.md) |
| 10.25 | Story 8.3 (partner meeting infra) | ✅ Fully implemented (verified in story) |
| 10.25 | Story 10.2 (DB email templates) | ⚠️ `ready-for-dev` — not yet delivered |

### Missing Requirements

No FRs are missing — all 49 FRs are assigned to a story.

### Coverage Statistics

- Total PRD FRs: **49**
- FRs covered in stories: **49**
- Coverage percentage: **100%**
- Prerequisite blockers: **3** (10.1 blocks 10.20+10.21; 10.8a blocks 10.22; 10.2 blocks 10.25)

---

## UX Alignment Assessment

### UX Document Status

**Not Found** — No dedicated UX design document exists for Epic 10. This is consistent with the established project pattern: UX specifications are embedded inline within each story's Scope section (component names, layout descriptions, interaction flows, file paths).

### Inline UX Specification Quality per Story

| Story | UI Changes | UX Detail Level |
|---|---|---|
| 10.19 | Remove email renders from 2 components | ✅ Sufficient — pure deletion, no new UX |
| 10.20 | New Admin Tab 5 with export/import sections | ✅ Sufficient — component names, layout, interactions specified |
| 10.21 | Photo grid tab, homepage marquee replacement, archive section | ✅ Sufficient — grid columns, photo card spec, fallback behavior, hide-when-empty rule |
| 10.22 | Teaser image subsection in Settings; new slide in Presentation | ✅ Sufficient — thumbnail preview, full-screen slide behavior, slide omission rule |
| 10.23 | EventDescriptionSection between hero and speakers | ✅ Sufficient — rendering rule, white-space behavior, archive page inclusion |
| 10.24 | Row action menu, confirmation dialog, create-modal toggle | ✅ Sufficient — conditional rendering rules, dialog copy, success/error states all specified |
| 10.25 | No new frontend components | ✅ N/A — server-side only |

### Alignment Issues

None identified. The inline UX specifications in each story are consistent with the existing frontend architecture patterns (presigned URL upload flow, MUI dialogs, TailwindCSS/dark theme, InfiniteMarquee component reuse).

### Warnings

- ⚠️ **Story 10.21 — Fallback threshold**: The fallback rule "< 3 photos → show testimonial cards" is a product decision with no explicit user research backing. If the threshold is wrong, the homepage may show mixed content (2 real photos + testimonials) which could look inconsistent. Consider documenting this threshold as a configurable constant.
- ⚠️ **Story 10.22 — Slide ordering**: The `TEASER_IMAGE` slide is specified to appear "after topic introduction slide and before first agenda slot slide." This ordering is correct per the story, but the PresentationPage slide sequence is not formally documented — the developer must audit the existing slide array to insert at the correct index.
- ⚠️ **Story 10.24 — Dialog auto-close**: The 2-second auto-close after success is not explicitly tested in DoD. This is a UX micro-interaction that could regress silently.
- ℹ️ **No separate UX wireframes**: Acceptable for this epic given the admin/internal nature of most changes. Not flagged as a blocker.

---

## Epic Quality Review

### Epic Structure Validation

**Epic 10 — "Additional Stories"**
- **User-centric title?** ⚠️ No — "Additional Stories" is a holding category name, not a user outcome. This is a known structural compromise; the individual stories carry user value.
- **Epic goal clarity?** ✅ The epic overview and architecture context are well-specified.
- **Can function independently?** ✅ All stories within scope (10.19–10.25) are independent of future undelivered epics. Backward dependencies on completed epics (Epic 8, Epic 9.1) are appropriate.

---

### Story Quality Assessment

#### Story 10.19 — Remove Organizer Email from Public Pages

| Criterion | Result |
|---|---|
| User value | ✅ Privacy protection — removes PII from public pages |
| Independent | ✅ No prerequisites |
| Sizing | ✅ Small and well-scoped |
| ACs testable | ✅ Specific — about page, presentation page, contact email unchanged, DB unaffected |
| Forward dependencies | ✅ None |

**Verdict: ✅ PASS**

---

#### Story 10.20 — Legacy BAT Format Data Export & Import

| Criterion | Result |
|---|---|
| User value | ✅ Data portability for migration |
| Independent | ⚠️ Depends on 10.1 (backward — acceptable) |
| Sizing | 🟠 Large — 4 endpoints + 2 services + frontend tab + asset ZIP handling |
| ACs testable | ✅ 403 enforcement, idempotency, round-trip test, structured error return |
| Forward dependencies | ✅ None |

**Quality Issues:**
- 🟠 **Story too large**: Export (3 endpoints: JSON + assets) and Import (2 endpoints: JSON + assets) could reasonably be split into "10.20a: Export" and "10.20b: Import" to reduce delivery risk. The export is read-only and low risk; the import mutates data and is higher risk.
- 🟡 The `GET /api/v1/admin/export/assets` async ZIP path includes two options ("or triggers async ZIP creation") — this ambiguity should be resolved in the story before implementation starts.

**Verdict: 🟠 MINOR CONCERN — consider story split; resolve async ZIP ambiguity**

---

#### Story 10.21 — Event Photos Gallery

| Criterion | Result |
|---|---|
| User value | ✅ Event memory preservation; real photos replacing dummy content |
| Independent | ⚠️ Prerequisite listed as "Story 10.1" — see issue below |
| Sizing | 🟠 Large — new DB table, 5 endpoints, 3 distinct frontend changes |
| ACs testable | ✅ Migration, upload/delete flow, recent-photos sampling, fallback threshold, hide-when-empty |
| Forward dependencies | ✅ None |

**Quality Issues:**
- 🟠 **Incorrect prerequisite**: Story 10.21 lists "Prerequisite: Story 10.1 (Admin page exists)". However, `EventPhotosTab.tsx` is added to `EventPage.tsx` (the organizer event detail page), NOT to the Admin page. The actual prerequisite appears to be: the `EventPage` tab infrastructure (already exists). **The prerequisite should be removed or corrected to reflect that no actual dependency on 10.1 exists** — this matters for scheduling since it currently appears blocked by 10.1 unnecessarily.
- 🟡 **Optional feature in scope**: "Drag-to-reorder for sort_order (optional enhancement)" creates scope ambiguity. Optional features should be explicitly excluded from this story's DoD or broken into a separate story.
- 🟡 **Flyway V74**: If 10.21 and 10.22 are developed concurrently, V74 (10.21) and V75 (10.22) migration numbers need coordination to avoid conflicts.

**Verdict: 🟠 PREREQUISITE NEEDS CORRECTION — Story 10.21 may not require Story 10.1**

---

#### Story 10.22 — Event Teaser Image on Moderator Presentation Page

| Criterion | Result |
|---|---|
| User value | ✅ Visual mood-setter for moderator presentation |
| Independent | ✅ Prerequisite 10.8a is clear and valid |
| Sizing | ✅ Focused and well-scoped |
| ACs testable | ✅ Migration, upload/remove, slide presence/absence, regression check |
| Forward dependencies | ✅ None |

**Verdict: ✅ PASS**

---

#### Story 10.23 — Event Description Section on Public Homepage

| Criterion | Result |
|---|---|
| User value | ✅ Content discovery for visitors |
| Independent | ✅ No prerequisites (field already in DB/API) |
| Sizing | ✅ Small — frontend-only, new component |
| ACs testable | ✅ Presence/absence rules, mobile/desktop wrap, archive page, no backend changes |
| Forward dependencies | ✅ None |

**Verdict: ✅ PASS — cleanest story in this batch**

---

#### Story 10.24 — AWS Cognito User Provisioning from User Management

| Criterion | Result |
|---|---|
| User value | ✅ Organizer efficiency — create Cognito accounts without AWS console |
| Independent | ✅ Prerequisite 9.1 is done |
| Sizing | 🟠 Large — new service, CDK IAM changes, 3 frontend components modified |
| ACs testable | ✅ Excellent — IAM role verification, 409 cases, hasCognitoAccount, UI states, graceful failure |
| Forward dependencies | ✅ None |

**Quality Issues:**
- 🟡 **Cognito failure in create-modal not in DoD**: The graceful failure path ("user still saved, warning toast shown") is described in scope but the DoD checkbox only partially covers it. The DoD item "handles Cognito failure gracefully (user still saved, warning shown)" could be more specific about what "gracefully" means for the integration test.
- ℹ️ **Architectural clarity is excellent**: The IAM vs Cognito JWT distinction in the background section is exemplary and will prevent a common misimplementation.

**Verdict: ✅ PASS with minor DoD refinement suggestion**

---

#### Story 10.25 — Partner Meeting iCal Auto-creation & Year-End Reminders

| Criterion | Result |
|---|---|
| User value | ✅ Automation — reduces manual coordination overhead |
| Independent | ⚠️ Depends on 10.2 (ready-for-dev) → transitively blocked by 10.1 |
| Sizing | 🟠 Medium-large — spans 2 services, 3 distinct features |
| ACs testable | ✅ Idempotency, failure isolation, template swap, scheduler trigger/dedup |
| Forward dependencies | ✅ None |

**Quality Issues:**
- 🟠 **Three unrelated features in one story**: Auto-create meeting on event creation, editable email template, and year-end reminder scheduler are three separate behaviours bundled together. While they share the partner coordination domain, they have different risk profiles and could be split:
  - 10.25a: Auto-create partner meeting on event creation (service-to-service)
  - 10.25b: Replace hardcoded email body with DB template (template refactor)
  - 10.25c: Year-end reminder scheduler (new scheduler + ShedLock)
- 🟡 **Transitive block**: 10.25 → 10.2 → 10.1. If 10.1 is delayed, both 10.2 and 10.25 stall. This dependency chain should be acknowledged in sprint planning.
- 🟡 **ShedLock dependency**: The scheduler presumes ShedLock is already configured in `event-management-service`. The story doesn't explicitly confirm this. Verify ShedLock is already wired before implementing.

**Verdict: 🟠 CONSIDER STORY SPLIT for risk management**

---

### Best Practices Compliance Summary

| Story | User Value | Independent | No Fwd Dep | Clear ACs | Sized Right |
|---|---|---|---|---|---|
| 10.19 | ✅ | ✅ | ✅ | ✅ | ✅ |
| 10.20 | ✅ | ✅ | ✅ | ✅ | 🟠 Large |
| 10.21 | ✅ | 🟠 Wrong prereq | ✅ | ✅ | 🟠 Large |
| 10.22 | ✅ | ✅ | ✅ | ✅ | ✅ |
| 10.23 | ✅ | ✅ | ✅ | ✅ | ✅ |
| 10.24 | ✅ | ✅ | ✅ | ✅ | 🟠 Large |
| 10.25 | ✅ | 🟠 Chain block | ✅ | ✅ | 🟠 Bundled |

### Quality Findings by Severity

#### 🔴 Critical Violations
None.

#### 🟠 Major Issues (4)
1. **Story 10.21 — Incorrect prerequisite** (10.1 not actually required): Fix before sprint planning or developer will wait unnecessarily for 10.1 to complete.
2. **Story 10.20 — Story size**: Export + Import is high-risk as a single story. Consider splitting.
3. **Story 10.25 — Three features bundled**: Different risk profiles and test scopes. Consider splitting 10.25a/b/c.
4. **Story 10.20 — Asset export ambiguity**: "Async ZIP creation or presigned URL manifest" must be decided before implementation.

#### 🟡 Minor Concerns (4)
1. **Story 10.21 — Optional drag-to-reorder**: Mark explicitly out of scope in DoD to avoid scope creep.
2. **Story 10.21/10.22 — Flyway migration coordination**: Confirm V74 and V75 are unallocated before starting both in parallel.
3. **Story 10.25 — ShedLock presence**: Confirm ShedLock is already configured in event-management-service before implementation.
4. **Story 10.24 — DoD specificity on Cognito failure path**: Strengthen the integration test checkpoint for the graceful failure scenario.

---

## Summary and Recommendations

### Overall Readiness Status

## ✅ READY FOR DEV (with 4 pre-implementation fixes recommended)

All 7 stories deliver clear user value. 49/49 FRs are covered. No critical violations exist. The batch is implementation-ready with the following items to resolve before or during sprint planning.

---

### Critical Issues Requiring Immediate Action

> **None** — no showstoppers. The issues below are 🟠 Major (should fix) and 🟡 Minor (nice to fix).

### 🟠 Actions Before Sprint Planning

**1. Fix Story 10.21 prerequisite** *(30-min fix)*
- Remove "Prerequisite: Story 10.1" from Story 10.21. The `EventPhotosTab` goes on `EventPage.tsx`, not the Admin page. This unblocks 10.21 from 10.1 and could allow parallel delivery.

**2. Resolve asset export strategy in Story 10.20** *(product decision, 1 conversation)*
- Decide: presigned URL manifest (simpler, lower risk) vs async ZIP generation (richer UX, more complex). Write the decision into the story before dev starts. The current "or" creates implementation ambiguity.

**3. Mark drag-to-reorder as explicitly out-of-scope in Story 10.21 DoD** *(5-min edit)*
- Add to DoD: "❌ Drag-to-reorder is explicitly NOT in scope for this story" to prevent scope creep.

**4. Confirm ShedLock is configured in event-management-service** *(developer check, pre-implementation)*
- Before 10.25 starts: verify `net.javacrumbs.shedlock:shedlock-spring` is in EMS dependencies and a `shedlock` table migration exists. If not, add it as a sub-task.

### 🟡 Optional Improvements

**5. Consider splitting Story 10.20** (Export vs Import) — reduces risk surface per sprint
**6. Consider splitting Story 10.25** (auto-create / template swap / scheduler) — different risk profiles, easier to review and test independently
**7. Add Flyway migration coordination note** — confirm V74 and V75 are unallocated before 10.21 and 10.22 are started concurrently

---

### Recommended Implementation Sequence

Given prerequisites, the recommended delivery order is:

```
Sprint A (can start immediately — no prerequisites):
  10.19  Remove organizer email              [Small, independent]
  10.23  Event description on homepage       [Small, independent]
  10.24  Cognito provisioning (9.1 done)     [Large, Cognito ready]
  10.21  Event photos gallery *              [Large — after prereq fix]

Sprint B (after 10.1 and 10.8a delivered):
  10.22  Teaser image (needs 10.8a)          [Medium]
  10.20  Export/Import (needs 10.1)          [Large]

Sprint C (after 10.2 delivered):
  10.25  Partner meeting automation (needs 10.2) [Medium-large]
```

*Story 10.21 can move to Sprint A once the incorrect 10.1 prerequisite is removed.

---

### Final Note

This assessment identified **8 issues** across **4 categories** (coverage, UX warnings, quality, sequencing). No critical blockers exist. The 4 major issues are all quick to resolve and should be addressed before development starts. Stories 10.19, 10.22, 10.23, and 10.24 are clean and ready to implement without changes.

---

### Post-Assessment Fixes Applied (2026-03-02)

All 9 fixes applied directly to `docs/prd/epic-10-additional-stories.md`:

| # | Story | Fix |
|---|---|---|
| 1 | 10.21 | Prerequisite removed — EventPhotosTab is on EventPage, not Admin page |
| 2 | 10.21 | Flyway **V74 → V75** (V74 was already taken by `migrate_waitlisted_to_waitlist`) |
| 3 | 10.21 | Drag-to-reorder explicitly marked ❌ out of scope in DoD |
| 4 | 10.22 | Flyway **V75 → V76** (V75 now used by 10.21) |
| 5 | 10.22 | DoD updated to V76 |
| 6 | 10.20 | Asset export ambiguity resolved: **presigned URL manifest chosen** (async ZIP dropped) |
| 7 | 10.20 | DoD updated to reflect JSON manifest response shape |
| 8 | 10.25 | ShedLock confirmed already wired (V31 + dependency); transitive block noted |
| 9 | 10.24 | Graceful failure DoD checkpoint strengthened with specific assertions |

**Bonus finding during fix application:** V74 was already occupied in production — this would have caused a Flyway migration conflict at first deploy of Story 10.21. Caught and fixed here.

**Assessed by:** Winston (Architect Agent) — 2026-03-02
**Report:** `docs/implementation-readiness-report-10-19-25-2026-03-02.md`
