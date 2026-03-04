---
stepsCompleted:
  - step-01-document-discovery
  - step-02-prd-analysis
  - step-03-epic-coverage-validation
  - step-04-ux-alignment
  - step-05-epic-quality-review
  - step-06-final-assessment
story: "10.11 — Venue Capacity Enforcement & Waitlist Management"
date: "2026-03-01"
---

# Implementation Readiness Assessment Report

**Date:** 2026-03-01
**Project:** BATbern
**Story:** 10.11 — Venue Capacity Enforcement & Waitlist Management

---

## Step 1: Document Inventory

### PRD Documents
- `docs/prd/epic-10-additional-stories.md` — primary Epic 10 PRD
- `docs/prd-enhanced.md` — full platform PRD

### Architecture Documents
- `docs/architecture/01-system-overview.md`
- `docs/architecture/03-data-architecture.md`
- `docs/architecture/04-api-event-management.md`
- `docs/architecture/06-backend-architecture.md`
- `docs/architecture/06a-workflow-state-machines.md`
- `docs/architecture/06d-notification-system.md`
- `docs/architecture/ADR-003-meaningful-identifiers-public-apis.md`
- `docs/architecture/ADR-004-factor-user-fields-from-domain-entities.md`
- `docs/architecture/ADR-006-openapi-contract-first-code-generation.md`

### Story Spec
- `_bmad-output/implementation-artifacts/10-11-venue-capacity-enforcement-waitlist-management.md` — status: ready-for-dev

### UX Documents
- ⚠️ None found for Story 10.11

---

## Step 2: PRD Analysis

**Source:** `docs/prd/epic-10-additional-stories.md` — lines 735–826

### Functional Requirements

FR1 — Flyway V73: Add `waitlist_position` nullable INTEGER column to `registrations`; add `registration_capacity` nullable INTEGER to `events` (NULL = unlimited, backward-compatible)
FR2 — `RegistrationService.createRegistration()`: count active regs (registered+confirmed); if capacity NULL or count < capacity → status=`registered`; if count >= capacity → status=`waitlist` with sequential `waitlistPosition`
FR3 — `nextWaitlistPosition(eventId)`: `COALESCE(MAX(waitlist_position), 0) + 1` — positions are 1-based (1, 2, 3…)
FR4 — `WaitlistPromotionService.promoteFromWaitlist(eventId)`: find registration with lowest `waitlistPosition`, set status=`registered`, clear `waitlist_position`, send promotion email
FR5 — Auto-promotion triggered by `RegistrationService.cancelRegistration()` after every successful cancellation (dependency on Story 10.12 for the cancellation path)
FR6 — `WaitlistPromotionEmailService`: sends locale-resolved `waitlist-promotion-{locale}.html` template via `EmailTemplateSeedService`
FR7 — `PATCH /api/v1/events/{eventCode}`: extend request DTO with `registrationCapacity: Integer` (nullable; null clears/sets unlimited)
FR8 — `GET /api/v1/events/{eventCode}`: extend response with `registrationCapacity`, `confirmedCount`, `waitlistCount`, `spotsRemaining` (computed; null when unlimited)
FR9 — Public read restricted: `spotsRemaining` + `waitlistCount` only (no PII); full attendee list organizer-only
FR10 — `EventParticipantsTab.tsx`: capacity bar at top (`[███░░░] 42/60 confirmed · 3 on waitlist`) + collapsible "Waitlist" section with `waitlistPosition` column
FR11 — `RegistrationActionsMenu` on waitlist rows: "Promote to Registered" (manual bypass) + "Remove from Waitlist"
FR12 — Event settings: "Registration Capacity" numeric field (blank = unlimited); disabled when event is `ARCHIVED`
FR13 — `RegistrationWizard.tsx`: if `spotsRemaining === 0`, show info alert before submission; user must acknowledge before proceeding
FR14 — `RegistrationStatusBanner.tsx` (Story 10.10): display waitlist position "You are #N on the waitlist"
FR15 — `HomePage.tsx`: capacity badge — "X spots remaining" or "Full — join waitlist" when capacity is set and event accepts registrations
FR16 — 4 new email template classpath content fragments: `waitlist-promotion-de/en.html`, `waitlist-confirmation-de/en.html` — all use `batbern-default` layout
FR17 — OpenAPI spec `docs/api/events.openapi.yml` updated FIRST before any backend implementation (ADR-006)
FR18 — i18n: `waitlist.*` keys added to `de/en` locale files for events and registration namespaces
FR19 — TDD: `WaitlistPromotionServiceTest` and `RegistrationCapacityIntegrationTest` written before implementation (RED→GREEN→REFACTOR)

**Total FRs: 19**

### Non-Functional Requirements

NFR1 — Backward compatibility: existing events with no `registrationCapacity` (NULL) must behave identically to current (unlimited registrations accepted)
NFR2 — PII protection: public endpoints must NOT expose attendee names/emails; only aggregate counts (`spotsRemaining`, `waitlistCount`) are public
NFR3 — TDD mandate: unit + integration tests written first; integration tests extend `AbstractIntegrationTest` (PostgreSQL via Testcontainers, never H2)
NFR4 — Contract-first (ADR-006): OpenAPI spec committed before any implementation code
NFR5 — Code quality: Type-check passes; Checkstyle passes; `npm run lint` passes
NFR6 — i18n completeness: de/en full translations; 8 other locales get `[MISSING]` placeholders

**Total NFRs: 6**

### Additional Requirements / Constraints

- **ADR-003**: Internal service methods use `UUID eventId`; all public API paths use `eventCode` (e.g., `BATbern58`)
- **ADR-004**: Do NOT duplicate `preferredLanguage` on Registration entity — resolve user locale via `UserApiClient` at email send-time only
- **Concurrency note**: `MAX(waitlist_position) + 1` is safe for BATbern's low concurrency; add unique constraint + retry only if race conditions observed in production
- **`EventController` line limit**: Check line count; if > 2,400, extract registration endpoints to new `RegistrationController.java`
- **Story 10.12 dependency**: Auto-promotion from waitlist depends on Story 10.12 (`cancelRegistration()`) — the `WaitlistPromotionService` is called from 10.12's cancellation path. Story 10.11 must be implemented before 10.12.

### PRD Completeness Assessment

PRD is well-written and complete for backend and most frontend concerns. Minor ambiguity on email template file naming (PRD uses `waitlist-confirmation-*`; Story 10.13 references `registration-waitlist-confirmation-*`) — resolved in story spec.

---

## Step 3: Epic Coverage Validation

Story spec (`10-11-*.md`) serves as the implementation plan. Tracing each PRD FR against ACs and Tasks:

| FR | Requirement Summary | Story Spec Coverage | Status |
|----|--------------------|--------------------|--------|
| FR1 | V73 migration — `waitlist_position` + `registration_capacity` columns + index | AC1, T2 | ✅ Covered |
| FR2 | Capacity enforcement in `createRegistration()` | AC2, T10 | ✅ Covered |
| FR3 | `nextWaitlistPosition` — 1-based sequential | T5.4 | ✅ Covered |
| FR4 | `WaitlistPromotionService.promoteFromWaitlist()` | AC3, T7 | ✅ Covered |
| FR5 | Auto-promotion triggered by `cancelRegistration()` | AC3 + Story 10.12 scope | ⚠️ Cross-story dependency (by design) |
| FR6 | `WaitlistPromotionEmailService` with locale resolution | AC3, T8 | ✅ Covered |
| FR7 | PATCH extend with `registrationCapacity` | AC4, T11.1–T11.2 | ✅ Covered |
| FR8 | GET extend with `confirmedCount`, `waitlistCount`, `spotsRemaining` | AC4, T11.3 | ✅ Covered |
| FR9 | Public read: `spotsRemaining` + `waitlistCount` only (no PII) | AC4 text | ✅ Covered |
| FR10 | `EventParticipantsTab` capacity bar + collapsible waitlist section | AC5, T16, T14 | ✅ Covered |
| FR11 | RegistrationActionsMenu "Promote" + "Remove" for waitlist rows | AC5, T14.5–T14.6 | ✅ Covered |
| FR12 | Event settings "Registration Capacity" field, disabled when ARCHIVED | AC6, T17 | ✅ Covered |
| FR13 | RegistrationWizard waitlist acknowledgment when `spotsRemaining === 0` | AC8, T19 | ✅ Covered |
| FR14 | `RegistrationStatusBanner` waitlist position display ("You are #N…") | Dev Notes only — no AC, no Task | ⚠️ **MISSING from Tasks** |
| FR15 | `HomePage` capacity badge | AC7, T18 | ✅ Covered |
| FR16 | 4 email templates seeded | AC9, T9 | ⚠️ **Naming discrepancy** |
| FR17 | OpenAPI spec first (ADR-006) | AC12, T1 | ✅ Covered |
| FR18 | i18n `waitlist.*` + `capacity.*` keys de/en | AC11, T20 | ✅ Covered |
| FR19 | TDD — tests written first | AC10, T6 | ✅ Covered |

**Coverage: 17/19 fully covered · 2 flagged**

### Missing / Partial Coverage Detail

**FR5 — Auto-promotion on cancellation (cross-story):**
Story 10.11 provides `WaitlistPromotionService`; Story 10.12 calls it from `cancelRegistration()`. This is the correct design — 10.11 can be deployed independently with manual promotion working. The full automated loop requires 10.12. Not a defect, but must be noted as an integration dependency.

**FR14 — RegistrationStatusBanner waitlist position (gap):**
PRD explicitly requires Story 10.11 to add "You are #3 on the waitlist" display to `RegistrationStatusBanner.tsx`. Story 10.10 (which introduced the banner) is already done (`feat(10.10)` committed). Story spec mentions this only in Dev Notes as optional ("Story 10.10 can pick it up") — but 10.10 is already closed. There is no AC or Task for this display integration. **This is a gap that will leave the banner showing "Waitlist" status without a position number.**

**FR16 — Email template naming discrepancy:**
PRD: `waitlist-confirmation-de/en.html`
Story spec (AC9, T9): `registration-waitlist-confirmation-de/en.html`
Story 10.13 also references `registration-waitlist-confirmation-*`. The story spec naming is more consistent with the `EmailTemplateSeedService` `registration-` category detection pattern. The PRD naming is informal. Dev must use the story spec names, not the PRD names.

### Coverage Statistics

- Total PRD FRs: 19
- FRs fully covered: 17
- FRs with gaps/flags: 2 (FR14 gap, FR16 naming)
- Coverage: 89% with 1 actionable gap

---

## Step 4: UX Alignment Assessment

### UX Document Status

**Not found** — No formal wireframe or UX spec exists for Story 10.11.

### Assessment: UX is strongly implied

Story 10.11 introduces significant new UI components:
1. `CapacityIndicator.tsx` — new public component with badge variants
2. `WaitlistSection.tsx` — new organizer component with accordion + table + action menu
3. `EventParticipantsTab.tsx` changes — MUI LinearProgress capacity bar
4. `RegistrationWizard.tsx` changes — waitlist acknowledgment flow + conditional success message
5. Event Settings — new numeric field
6. `HomePage.tsx` — capacity badge in event hero

### Alignment Issues

The story spec ACs and Tasks contain inline UX specs sufficient for implementation (progress bar format, table columns, MUI component choices, alert text). This is acceptable given BATbern's pattern of embedding UX detail in story specs without formal wireframes.

**However, one UX ambiguity remains:**

⚠️ **AC8 / T19.3 — Waitlist acknowledgment UX is underspecified:**
- AC8 says "User must acknowledge before proceeding" and T19.3 says "check `waitlistAcknowledged` state before submit button is active (or show acknowledge button)" — these are two different patterns that would produce meaningfully different user experiences:
  - Pattern A: checkbox the user checks → button activates
  - Pattern B: separate "I understand" button → enables submit button
- Both are valid but which to implement is ambiguous. A developer could implement either; reviewer expectations may differ.

### Warnings

- ⚠️ No formal wireframe — inline specs are sufficient but AC8 waitlist acknowledgment UX needs clarification before dev begins
- ℹ️ `CapacityIndicator` props and appearance are well-specified in T13; no wireframe needed

---

## Step 5: Epic Quality Review

### User Value Check

✅ **Story delivers clear user value on two axes:**
- Organizer: can now prevent venue overbooking by setting a capacity ceiling
- Attendee: gains automatic promotion when spots open, avoiding manual checking

This is NOT a technical milestone — it's a directly user-facing capability.

### Story Independence

⚠️ **Partial forward dependency on Story 10.12:**
- The full automated promotion-on-cancel loop requires Story 10.12 (`cancelRegistration()` calls `promoteFromWaitlist()`)
- Story 10.11 IS independently completable: manual promotion, capacity enforcement, waitlist email, and all UI work can be done and tested without 10.12
- The story correctly notes this as a known dependency
- **Verdict**: Acceptable incremental delivery. Not a blocking violation.

### Story Sizing

The story is large (21 tasks across 6 phases) but represents a coherent, vertically-sliced capability. Each phase builds on the previous in a logical dependency chain. No phase can be meaningfully split out without breaking the slice.

### Acceptance Criteria Quality

| AC | Assessment |
|----|-----------|
| AC1 — V73 migration | ✅ Specific, testable, includes index |
| AC2 — Capacity enforcement | ✅ Clear logic with edge cases (NULL = unlimited) |
| AC3 — WaitlistPromotionService | ✅ Behavior + email + manual path specified |
| AC4 — API extension | ✅ Specific field names + nullability + access control |
| AC5 — Organizer Attendees Tab UI | ✅ Visual format + column names + action labels |
| AC6 — Event Settings field | ✅ Disabled state condition specified |
| AC7 — Public homepage badge | ✅ Both variants ("X spots" + "Full—join waitlist") |
| AC8 — Wizard acknowledgment | ⚠️ "Must acknowledge" is ambiguous — see UX concern |
| AC9 — Email templates seeded | ✅ 4 templates, layout, variables all specified |
| AC10 — TDD compliance | ✅ Specific test class names + RED phase command |
| AC11 — i18n | ✅ Namespace + file specified + `[MISSING]` for 8 locales |
| AC12 — OpenAPI spec first | ✅ Explicit ADR-006 reference |

### Dependency Analysis

✅ Database created story-scoped (V73 is this story's migration, not pre-created)
✅ No circular dependencies
✅ Backward: Story 10.10 (recommended, not required) — correctly marked
⚠️ Forward: Story 10.12 cancellation path — acceptable by design

### Implicit Behaviors Without ACs (Quality Concerns)

🟠 **T10.4 — Duplicate waitlist registration guard not in any AC:**
Task T10.4 adds logic: "if existing registration status is 'waitlist' → do NOT create duplicate, return existing and resend waitlist-confirmation email." This is a non-trivial business rule (extending the duplicate check from Story 10.10) that has no AC. If a developer misses T10.4, there's no failing test to catch it unless the integration tests in T6.2 cover this case — they currently don't.

🟠 **T7.5 — `manuallyPromote(String registrationCode)` has no error-case AC:**
The method is specified in T7.5 but there's no AC or test for what happens when `manuallyPromote()` is called with a registration that is NOT in `waitlist` status. Should it throw an exception? Return silently? This could cause a confusing organizer experience.

🟡 **T15 — Promote endpoint error cases unspecified:**
T15.2 adds the `POST /promote` endpoint but doesn't specify HTTP status for invalid `registrationCode` (404?) or non-waitlist registration (409? 422?). These should be in the OpenAPI spec and tested.

---

## Summary and Recommendations

### Overall Readiness Status

**🟡 READY WITH RESERVATIONS** — Story is well-specified and can be implemented as-is, but 4 items should be addressed before or during dev to prevent rework.

### Issues by Severity

#### 🟠 Major Issues (address before or during dev)

1. **FR14 gap — RegistrationStatusBanner waitlist position display is unimplemented**
   - PRD requires "You are #3 on the waitlist" in the banner
   - Story 10.10 is already done; this display is orphaned
   - **Action**: Add one AC and Task to Story 10.11: "Update `RegistrationStatusBanner.tsx` to show `waitlistPosition` when status is WAITLIST"
   - Effort: ~30 min (MyRegistrationResponse DTO already has the field per Dev Notes)

2. **AC8 ambiguity — Waitlist acknowledgment UX interaction type**
   - "User must acknowledge" could be a checkbox or a secondary button
   - **Action**: Clarify before T19 implementation. Recommend: MUI `Alert` + checkbox pattern for inline form consistency with existing wizard steps.

3. **T10.4 — Duplicate waitlist guard not backed by AC/test**
   - Business rule exists only in a task comment; no failing test will catch if omitted
   - **Action**: Add test case to `RegistrationCapacityIntegrationTest` (T6.2): "register when already on waitlist → 200, no duplicate created, waitlist-confirmation email resent"

4. **T7.5 / T15 — `manuallyPromote()` error handling unspecified**
   - No AC for invalid `registrationCode` or non-waitlist promotion attempts
   - **Action**: Add to OpenAPI spec: `404 Not Found` for invalid code; `409 Conflict` for non-waitlist status. Add test in T15.3.

#### 🟡 Minor Concerns (can be addressed during dev)

5. **FR16 — Email template filename discrepancy**
   - PRD uses `waitlist-confirmation-*`; story spec (and Story 10.13) use `registration-waitlist-confirmation-*`
   - **Action**: Use story spec names (`registration-waitlist-confirmation-de/en.html`). No change needed in story spec — dev must NOT use the PRD names.

6. **T11.4 — Performance note on confirmedCount / waitlistCount queries**
   - Two extra DB queries per event response noted as acceptable
   - **Action**: Monitor in staging; add `@Cacheable` if event list pages show latency. This is already noted in the task.

### Recommended Next Steps

1. Add one AC + Task to story spec for RegistrationStatusBanner waitlist position display (FR14 gap)
2. Clarify AC8 acknowledgment interaction pattern before frontend dev begins
3. Add duplicate-waitlist-registration test case to T6.2 test plan
4. Add error-case ACs to T15 for the promote endpoint
5. Confirm email template filenames use `registration-waitlist-confirmation-*` naming (not PRD `waitlist-confirmation-*`)
6. Proceed to implementation after above are resolved — all other 17 FRs are fully covered and ready

### Final Note

This assessment identified **6 issues** across **3 categories** (1 FR gap, 2 UX/AC ambiguities, 3 missing error handling specs). The story is architecturally sound, ADR-compliant, and has solid TDD coverage for the happy path. The issues are targeted and do not require story redesign — they are additive clarifications. The story can proceed to implementation once the major issues (especially FR14 and AC8) are resolved.

---
*Assessed by Winston — BATbern Architect | 2026-03-01*
