---
stepsCompleted:
  - step-01-document-discovery
  - step-02-prd-analysis
  - step-03-epic-coverage-validation
  - step-04-ux-alignment
  - step-05-epic-quality-review
  - step-06-final-assessment
story: "10.12 — Self-Service Deregistration"
date: "2026-03-01"
---

# Implementation Readiness Assessment Report

**Date:** 2026-03-01
**Project:** BATbern
**Story:** 10.12 — Self-Service Deregistration

---

## Step 1: Document Inventory

### PRD Documents
- `docs/prd/epic-10-additional-stories.md` — lines 829–934 (Story 10.12 section)

### Architecture Documents
- `docs/architecture/ADR-003-meaningful-identifiers-public-apis.md`
- `docs/architecture/ADR-006-openapi-contract-first-code-generation.md`
- `docs/architecture/06-backend-architecture.md`
- `docs/architecture/06d-notification-system.md`

### Story Spec
- `_bmad-output/implementation-artifacts/10-12-self-service-deregistration.md` — status: ready-for-dev

### UX Documents
- ⚠️ None found for Story 10.12

---

## Step 2: PRD Analysis

**Source:** `docs/prd/epic-10-additional-stories.md` — lines 829–934

### Functional Requirements

FR1 — Flyway V74: `deregistration_token` column (UUID, NOT NULL, UNIQUE, DEFAULT gen_random_uuid()) on `registrations`; backfill existing rows; unique index
FR2 — `RegistrationService.createRegistration()`: generate and persist UUID deregistration token for every new registration
FR3 — `GET /api/v1/registrations/deregister/verify?token={uuid}` → 200 with `{registrationCode, eventCode, eventTitle, eventDate, attendeeFirstName}`; 404 for invalid/already-cancelled
FR4 — `POST /api/v1/registrations/deregister` (body: `{token}`) → 200 + fires waitlist promotion; 404 invalid token; 409 already cancelled
FR5 — `POST /api/v1/registrations/deregister/by-email` (body: `{email, eventCode}`) → 200 always (anti-enumeration); sends email if found; silent no-op if not
FR6 — `deregistration-link-de.html` + `deregistration-link-en.html` email templates (batbern-default layout); variables: `recipientName`, `eventTitle`, `eventCode`, `eventDate`, `deregistrationLink`
FR7 — Embed deregistration link in existing registration confirmation email ("To cancel: [Cancel Registration]")
FR8 — Public `/deregister?token=` page (no auth); states: verifying → ready → confirmed | invalid | alreadyCancelled; mirrors `UnsubscribePage.tsx`
FR9 — `DeregistrationByEmailModal.tsx` on `HomePage.tsx` as secondary "Cancel your registration" link when event in AGENDA_PUBLISHED / AGENDA_FINALIZED / EVENT_LIVE
FR10 — `DeregistrationByEmailModal.tsx` on `RegistrationWizard.tsx` status guard ("Cancel my registration" button when already registered)
FR11 — Organizer attendees tab: cancelled registrations visible with grey CANCELLED chip
FR12 — Organizer cancel endpoint updated to call `cancelRegistration()` (soft cancel, not hard-delete) so waitlist promotion fires
FR13 — `cancelRegistration()`: transitions `status = "cancelled"` AND calls `waitlistPromotionService.promoteFromWaitlist(eventId)`
FR14 — SecurityConfig: both `event-management-service` AND `api-gateway` have `permitAll` for all 3 `/api/v1/registrations/deregister/**` paths
FR15 — OpenAPI spec updated FIRST (ADR-006)
FR16 — TDD: `DeregistrationServiceTest` + `DeregistrationControllerIntegrationTest` written first (RED phase)
FR17 — i18n: `deregistration.*` keys in de/en locale files
FR18 — Deregistration email templates seeded by `EmailTemplateSeedService` — editable in admin Email Templates tab

**Total FRs: 18**

### Non-Functional Requirements

NFR1 — Anti-enumeration: `POST /by-email` MUST return 200 regardless of outcome; never discloses whether an email is registered
NFR2 — Non-expiring, never-rotated deregistration token (security design decision)
NFR3 — No auth required for all 3 public deregistration endpoints — token IS the authentication mechanism
NFR4 — TDD mandate: tests written first; integration tests use PostgreSQL via Testcontainers (AbstractIntegrationTest)
NFR5 — ADR-006: contract-first OpenAPI
NFR6 — ADR-003: `deregistrationToken` is a UUID security token, NOT a business identifier — not exposed as path segment
NFR7 — ADR-005 (anonymous registration): deregistration works for both anonymous and Cognito-authenticated registrants

**Total NFRs: 7**

### Additional Requirements / Constraints

- Soft-cancel behaviour change: existing organizer flow currently hard-deletes registrations; this story changes it to soft-cancel (status=cancelled). **Breaking behaviour change** — confirmed intentional (cancelled rows must remain visible).
- `app.base-url` config property needed for constructing `deregistrationLink`; must exist in `application.yml`
- Story 10.11 must be merged first (`WaitlistPromotionService` dependency)

### PRD Completeness Assessment

PRD is clear and well-structured. Key design decisions (anti-enumeration, token-as-auth, non-expiring) are explicitly stated. No formal UX wireframes but `UnsubscribePage.tsx` mirror pattern is a sufficient reference.

---

## Step 3: Epic Coverage Validation

| FR | Requirement Summary | Story Spec Coverage | Status |
|----|--------------------|--------------------|--------|
| FR1 | V74 migration — deregistration_token column + backfill + index | AC1, T2 | ✅ Covered |
| FR2 | Generate token in createRegistration() | AC2, T7 | ✅ Covered |
| FR3 | GET /deregister/verify endpoint | AC3, T11.3 | ✅ Covered |
| FR4 | POST /deregister endpoint + waitlist promotion | AC3, AC5, T11.4 | ✅ Covered |
| FR5 | POST /deregister/by-email anti-enumeration | AC3, T11.5 | ✅ Covered |
| FR6 | deregistration-link email templates | AC6, T10.1–T10.4 | ✅ Covered |
| FR7 | Deregistration link in confirmation email | AC7, T9.4–T9.5, T10.5 | ✅ Covered |
| FR8 | DeregistrationPage.tsx public route | AC8, T15, T17 | ✅ Covered |
| FR9 | DeregistrationByEmailModal from HomePage | AC9, T16, T18 | ✅ Covered |
| FR10 | DeregistrationByEmailModal from RegistrationWizard | AC9, T19 | ✅ Covered |
| FR11 | Cancelled rows in organizer attendees tab | AC11 | ✅ Covered (AC only, no Task) |
| FR12 | Organizer cancel → soft cancel not hard-delete | AC11, T6.2 | ✅ Covered |
| FR13 | cancelRegistration() soft-cancel + waitlist promotion | AC4, AC5, T6.1 | ✅ Covered |
| FR14 | SecurityConfig permitAll both services | AC10, T12 | ✅ Covered |
| FR15 | OpenAPI spec first (ADR-006) | T1 | ✅ Covered |
| FR16 | TDD — tests written first | AC12, T5 | ✅ Covered |
| FR17 | i18n deregistration.* keys de/en | AC13, T20 | ✅ Covered |
| FR18 | Templates seeded by EmailTemplateSeedService | AC6 (implied) | ⚠️ **PARTIAL — see gap** |

**Coverage: 17/18 fully covered · 1 flagged**

### Missing / Partial Coverage Detail

**FR18 — EmailTemplateSeedService category mapping (gap):**
AC6 states templates are "seeded by `EmailTemplateSeedService`" and the DoD requires "editable in admin Email Templates tab." However, no task exists to verify or extend `EmailTemplateSeedService.determineCategory()` to handle the `deregistration-` filename prefix. In Story 10.11 the Dev Notes explicitly called this out for the `waitlist-` prefix. If `deregistration-` does not map to the REGISTRATION category, templates will not be seeded and the DoD item fails silently at deploy time.

**Coverage Statistics**
- Total PRD FRs: 18
- FRs fully covered: 17
- FRs with gaps/flags: 1 (FR18)
- Coverage: 94%

---

## Step 4: UX Alignment Assessment

### UX Document Status

**Not found** — No formal wireframe or UX spec for Story 10.12.

### Assessment

`UnsubscribePage.tsx` is explicitly named as the pattern model (AC8, Dev Notes). This is a strong reference — it means the developer has a concrete existing component to mirror, reducing ambiguity significantly. The modal pattern reference (`SpeakerDeclineModal.tsx` or similar) is also named.

### Alignment Issues

**Minor — `DeregistrationByEmailModal` form validation not specified:**
T16.3 says the email field "validates email format" but doesn't specify:
- Whether validation is on-blur, on-submit, or real-time
- Whether the error message is i18n (no `deregistration.modal.emailError` key is in T20.1)
This is minor — consistent with how other forms are built in the project, but worth noting.

**Minor — `RegistrationWizard.tsx` status guard location:**
T19.1 says "In the status guard screen (Story 10.10, T11 — shown when user is already registered)". Story 10.10 and 10.11 are now both done. Dev must read the current state of `RegistrationWizard.tsx` carefully — the status guard may have evolved. No wireframe to anchor against.

### Warnings

- ⚠️ No formal wireframe — `UnsubscribePage.tsx` mirror is sufficient for `DeregistrationPage`; modal and status guard integration points need careful reading of existing code before modification

---

## Step 5: Epic Quality Review

### User Value Check

✅ **Clear, direct user value:** attendees can self-cancel without organizer involvement. Two access paths (link in email + email-input form) cover both cases (I have the email vs I need to request it).

### Story Independence

⚠️ **Hard dependency on Story 10.11:**
`cancelRegistration()` calls `waitlistPromotionService.promoteFromWaitlist(eventId)`. The `WaitlistPromotionService` bean is created in Story 10.11. If 10.11 is not merged, the Spring context will fail to start (injection failure). Dev Notes acknowledge this and suggest a null-guard workaround — but correctly note it's a code smell and 10.11 should be implemented first.

This is an explicit, documented dependency on prior work (not forward work) — acceptable.

### Acceptance Criteria Quality

| AC | Assessment |
|----|-----------|
| AC1 — V74 migration | ✅ Backfill strategy explicitly specified (2-step: add nullable → backfill → NOT NULL) |
| AC2 — Token on creation | ✅ Clear |
| AC3 — 3 endpoints | ✅ Response shapes + error codes fully specified |
| AC4 — Soft cancel | ✅ Behaviour change explicitly called out |
| AC5 — Waitlist promotion on cancel | ✅ All 3 callers listed (token, by-email, organizer) |
| AC6 — Email templates | ✅ File names, category, variables all specified |
| AC7 — Confirmation email update | ✅ Existing template extended |
| AC8 — DeregistrationPage | ✅ All 5 states specified with text; pattern reference given |
| AC9 — Modal entry points | ✅ Both surfaces (HomePage + Wizard) specified |
| AC10 — Security config | ✅ Both services explicitly named |
| AC11 — Organiser tab | ⚠️ Visual spec for CANCELLED chip is in AC ("grey CANCELLED chip") but no Task covers rendering it — see gap below |
| AC12 — TDD | ✅ Specific class names given |
| AC13 — i18n | ✅ |

### Critical Findings

**🟠 AC11 has no implementing Task:**
AC11 says "Cancelled registrations visible in the attendees table with grey `CANCELLED` chip." There is no frontend task that adds this rendering. The attendees table presumably renders a status chip already — but the CANCELLED state might not have a grey chip variant. T6.2 updates the backend (organizer cancel endpoint → soft cancel), but nothing in Phase 5 Frontend tasks addresses the `CANCELLED` chip in `EventParticipantList.tsx`. If the chip is not already rendered for CANCELLED status, this AC will silently fail.

**🟠 No integration test verifies waitlist promotion fires on cancellation:**
T5.2 integration tests confirm `POST /deregister` → status = `cancelled`. But there is no integration test that: registers 2 people (2nd on waitlist), deregisters the 1st, and asserts the 2nd is promoted. This is the core Story 10.11 + 10.12 integration point and should be tested end-to-end.

**🟠 T1.4 — deregistrationToken schema segregation unspecified:**
T1.4 says add `deregistrationToken` to Registration schema "only for organizer-scoped responses, NOT for public registration response." This is a security requirement but the OpenAPI task doesn't specify HOW to achieve schema segregation. Options:
1. Separate schemas: `RegistrationResponse` (public) vs `RegistrationAdminResponse` (organizer)
2. Single schema with a note and rely on `@JsonIgnore` / serializer exclusion for public endpoints

If option 1: the OpenAPI spec needs two distinct schemas. If option 2: there's a risk of accidental token exposure. No decision is documented. A developer could implement either and both would "pass" the task as written.

**🟡 No rate limiting on `by-email` endpoint:**
`POST /deregister/by-email` is public, accepts any email address, and sends an email if a match is found. No rate limiting per IP or per email is specified. This could be abused to spam attendees with deregistration link emails. Story 10.17 adds rate limiting for inbound email processing but that's a different concern. This is a security consideration worth flagging.

**🟡 T6.3 — Old JWT cancel flow is conditional:**
"The existing confirm/cancel JWT flow... should also call `cancelRegistration()` (not delete). Update if still doing hard-delete." This is a conditional dev note, not an AC. If the developer forgets to check this flow, it could still hard-delete registrations from the old email flow — bypassing waitlist promotion. There is no test coverage for this old path in Story 10.12.

**🟡 emailError key missing from T20:**
`DeregistrationByEmailModal` validates email format (T16.3) but the i18n key for the email validation error (`deregistration.modal.emailError`) is not in the T20 key list.

---

## Summary and Recommendations

### Overall Readiness Status

**🟡 READY WITH RESERVATIONS** — Story is well-specified overall with strong pattern references, but 5 issues should be addressed before or during implementation.

### Issues by Severity

#### 🟠 Major (3 — address before dev starts)

1. **FR18 / EmailTemplateSeedService category mapping missing**
   - No task verifies `deregistration-` prefix → REGISTRATION category in `EmailTemplateSeedService`
   - DoD item "templates editable in admin" will fail silently if unmapped
   - **Action**: Add subtask to T10: "Verify `EmailTemplateSeedService.determineCategory()` handles `deregistration-` prefix → REGISTRATION; add mapping if absent"

2. **AC11 — CANCELLED chip has no frontend task**
   - AC requires grey CANCELLED chip in the attendees table; no Task implements it
   - **Action**: Add T22 (or subtask to T16 area): "In `EventParticipantList.tsx` (or status chip component), add CANCELLED status variant with grey color. Verify existing status chip pattern supports this — add if missing."

3. **T1.4 — deregistrationToken schema segregation approach unspecified**
   - "Only for organizer-scoped responses" — no decision on how (two schemas vs serializer exclusion)
   - Security risk: if implementation uses single shared schema + no serializer guard, token leaks publicly
   - **Action**: Decide explicitly. Recommend: add `deregistrationToken` to a new `RegistrationAdminResponse` schema (separate from the public `RegistrationResponse`). Document the approach in T1.4.

#### 🟡 Minor (3 — can be addressed during dev)

4. **No integration test for waitlist promotion on cancel (end-to-end)**
   - T5.2 tests status=cancelled but not that the waitlisted person gets promoted
   - **Action**: Add one test case to T5.2: "Register 2 attendees on a full event (2nd on waitlist), deregister the 1st via token → assert 2nd is now status=registered and promotion email sent"

5. **`by-email` endpoint has no rate limiting**
   - Any IP can spam attendees with deregistration emails; no rate limit specified
   - **Action**: Add a note to T11.5 to use Spring's `RateLimiter` or bucket4j if available in the project; or document that this is accepted risk for MVP with low traffic

6. **`deregistration.modal.emailError` i18n key missing from T20**
   - Modal validates email format but no error key is defined
   - **Action**: Add `"emailError": "Please enter a valid email address"` to T20.1 `deregistration.modal.*` block

### Recommended Next Steps

1. Add EmailTemplateSeedService verification subtask to T10
2. Add CANCELLED chip frontend task (T22 or T16 subtask)
3. Clarify OpenAPI schema segregation in T1.4 (two schemas)
4. Add waitlist-promotion integration test case to T5.2
5. Add `emailError` i18n key to T20.1
6. Proceed to implementation — all 18 FRs are traceable; core logic is solid

### Final Note

This assessment identified **6 issues** across **3 categories**. The story has excellent pattern references (`UnsubscribePage.tsx` mirror, explicit endpoint specs, anti-enumeration design documented). The issues are all additive — no redesign required. The most important fix before dev starts is the EmailTemplateSeedService gap (FR18) which would cause a silent DoD failure at deploy time.

---
*Assessed by Winston — BATbern Architect | 2026-03-01*
