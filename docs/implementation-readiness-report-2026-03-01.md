---
stepsCompleted:
  - step-01-document-discovery
  - step-02-prd-analysis
  - step-03-epic-coverage-validation
  - step-04-ux-alignment
  - step-05-epic-quality-review
scope: story-10.10
documentsInventoried:
  prd: docs/prd/epic-10-additional-stories.md
  architecture: docs/architecture/ (sharded, index.md present)
  storyFile: _bmad-output/implementation-artifacts/10-10-registration-status-indicator.md
  ux: NOT FOUND
---

# Implementation Readiness Assessment Report

**Date:** 2026-03-01
**Project:** BATbern
**Scope:** Story 10.10 — Registration Status Indicator

---

## Document Inventory

| Type | Format | Location | Status |
|------|--------|----------|--------|
| PRD (Epic 10) | Whole file | `docs/prd/epic-10-additional-stories.md` | ✅ Found |
| Architecture | Sharded folder | `docs/architecture/` (index.md present, 20+ files) | ✅ Found |
| Story File | Whole file | `_bmad-output/implementation-artifacts/10-10-registration-status-indicator.md` | ✅ Found |
| UX Design | — | Not found | ⚠️ Missing |

---

## PRD Analysis

### Functional Requirements

FR1: New read-only API endpoint — `GET /api/v1/events/{eventCode}/my-registration` returns the authenticated user's registration status for that event.

FR2: Response DTO `MyRegistrationResponse` — fields: `registrationCode`, `eventCode`, `status` (enum: `REGISTERED`, `CONFIRMED`, `WAITLIST`, `CANCELLED`), `registrationDate` (ISO-8601).

FR3: Returns `404` when the user has no registration for that event; returns `401` for unauthenticated requests.

FR4: Homepage status banner — when authenticated user visits the homepage and the "current event" is in state `AGENDA_PUBLISHED`, `AGENDA_FINALIZED`, or `EVENT_LIVE`, a coloured banner appears below the hero section mapping status → colour/icon/i18n text.

FR5: Banner "Manage Registration" link navigates to `/register/{eventCode}`. For `CANCELLED` status, link text is "Register again" (`registrationStatusBanner.registerAgain`).

FR6: Loading skeleton — while the `GET my-registration` call is in-flight, a skeleton placeholder (same dimensions as the banner) is shown. No cumulative layout shift after resolution.

FR7: EventCard status chip — `EventCard` accepts optional `myRegistrationStatus?: string` prop; when provided, renders a small badge chip in the top-right corner with colour matching status. Parent components pass this prop only for events in the past 12 months.

FR8: Registration Wizard guard — on wizard mount, `useMyRegistration` is called. If status is `REGISTERED`, `CONFIRMED`, or `WAITLIST`, the wizard shows a guard screen instead of step 1 (current status + "Done, go back" button). If `CANCELLED`, shows guard with "Register again" button that proceeds to step 1.

FR9: Cache invalidation — `my-registration` query is invalidated when a new event registration is successfully created (`useEventRegistration` mutation success callback calls `queryClient.invalidateQueries(['my-registration', eventCode])`).

FR10: Anonymous users — when `isAuthenticated === false`, no API call is made. No banner, no chip, no guard screen.

FR11: TDD compliance — `RegistrationStatusIntegrationTest` written first (RED phase) covering: `200 REGISTERED`, `200 CONFIRMED`, `200 WAITLIST`, `200 CANCELLED`, `404 not-registered`, `401 unauthenticated`. `useMyRegistration.test.ts` covers all states.

FR12: i18n — all visible strings use i18n keys. Keys added to `en/registration.json` and `de/registration.json` for banner and chip texts. Other 8 locales get `[MISSING]` prefix placeholders.

**Total FRs: 12**

---

### Non-Functional Requirements

NFR1 (Performance): Banner must resolve and render within ~500ms of page load on staging.

NFR2 (UX/Stability): No cumulative layout shift (CLS = 0) after the registration status banner resolves.

NFR3 (Caching): Client-side TanStack Query `staleTime: 5 * 60 * 1000` (5 minutes). No server-side Caffeine cache in this story (deferred to follow-up if needed).

NFR4 (ADR-006 Contract-First): OpenAPI spec in `docs/api/events.openapi.yml` MUST be updated and committed BEFORE any backend implementation begins.

NFR5 (ADR-003 Meaningful Identifiers): Endpoint path uses `{eventCode}` (e.g., `BATbern58`), not UUID. Response uses `registrationCode`, not UUID.

NFR6 (ADR-004 No User Field Duplication): `MyRegistrationResponse` must NOT include `firstName`, `lastName`, `email`, or any user profile field.

NFR7 (Quality Gates): `npm run type-check` passes; `npm run lint` passes; Checkstyle passes.

NFR8 (Test Coverage): Backend integration tests via Testcontainers/PostgreSQL (never H2). Frontend unit tests co-located with components.

**Total NFRs: 8**

---

### Additional Requirements / Constraints

- **No new DB migration** needed — reads from existing `registrations` table.
- **EventController line count guard** — if `EventController.java` exceeds 2,400 lines, the new endpoint must be extracted to `RegistrationController.java`.
- **Story 10.12 dependency** (soft): Cache invalidation note in PRD mentions Story 10.12 (deregistration) must also invalidate the `my-registration` query. Story 10.12 is not a hard prerequisite.
- **Story 10.11 soft dependency**: 10.11 PRD references `RegistrationStatusBanner.tsx` for waitlist position display — the component must be designed with extensibility in mind.

---

### PRD Completeness Assessment — Initial

The PRD section for story 10.10 is well-structured with clear scope, user story, and Definition of Done. The story artifact file elaborates substantially on the PRD. However, **notable divergences exist between the PRD and the story file** — flagged below for resolution before implementation.

⚠️ **Divergence D1 — Server-side cache**: PRD specifies "5-minute Caffeine cache keyed by `(eventCode, username)` — invalidated on registration mutation events". Story file explicitly says "no server-side cache in this story". **Story file is more conservative and pragmatic — recommend following story file.**

⚠️ **Divergence D2 — Response DTO fields**: PRD has `{ registrationCode, status, registrationDate }`. Story file has `{ registrationCode, eventCode, status, registrationDate }` (adds `eventCode`). **Story file is the authoritative spec — follow it.**

⚠️ **Divergence D3 — i18n key namespace**: PRD says `registrationStatus.*` prefix. Story file uses `registrationStatusBanner.*`, `registrationStatusGuard.*`, `eventCard.statusChip.*` (more granular). **Story file is more specific and implementation-ready — follow it.**

⚠️ **Divergence D4 — Integration test count**: PRD DoD lists 5 test cases (no CANCELLED 200 test). Story AC9 lists 6 (adds `200 CANCELLED`). **Story file is more complete — follow it.**

---

## Epic Coverage Validation

### Coverage Matrix

| FR | Requirement (Summary) | Story Task(s) | Status |
|----|----------------------|---------------|--------|
| FR1 | New read-only API endpoint `GET /events/{eventCode}/my-registration` | T1 (OpenAPI), T2 (repo query), T3 (service), T4 (controller) | ✅ Covered |
| FR2 | Response DTO with `registrationCode`, `eventCode`, `status`, `registrationDate` | T1.2 (schema definition) | ✅ Covered |
| FR3 | 404 when not registered; 401 for unauthenticated | T1.3 (security spec), T4.4, T4.5 | ✅ Covered |
| FR4 | Homepage status banner (4 status variants, correct states only) | T8 (component), T9 (HomePage integration) | ✅ Covered |
| FR5 | Banner "Manage Registration" / "Register again" link | T8.6 | ✅ Covered |
| FR6 | Loading skeleton, no CLS | T8.3 (Skeleton component) | ✅ Covered |
| FR7 | EventCard status chip — parent components batch-fetch (past 12 months only) | T10 (chip), T10.5 (parents) | ⚠️ Partial — see gap G1 |
| FR8 | Registration Wizard guard (REGISTERED/CONFIRMED/WAITLIST → "Done, go back"; CANCELLED → "Register again") | T11 | ✅ Covered |
| FR9 | Cache invalidation on registration creation | T6.4 (staleTime set) — **no explicit task for mutation callback** | ⚠️ Partial — see gap G2 |
| FR10 | Anonymous users: no API call made | T6.3 (`!isAuthenticated` guard in hook) | ✅ Covered |
| FR11 | TDD: integration tests written first (6 test cases) | T5 (backend), T6.7 (frontend unit tests), T13 (full run) | ✅ Covered |
| FR12 | i18n: all strings keyed, en+de complete, 8 locales `[MISSING]` | T12 | ✅ Covered |

**Coverage: 10/12 FRs fully covered · 2/12 partially covered · 0/12 missing**

---

### Missing / Incomplete FR Coverage

#### ⚠️ GAP G1 — FR7: EventCard parent batch-fetch strategy undefined

T10.5 states: *"Parent components (e.g., `UpcomingEventsSection`, `ArchivePage`) must batch-fetch user registrations and pass the prop. Only for events in the last 12 months."*

**Problem**: The story only defines a single-event endpoint `GET /events/{eventCode}/my-registration`. If a user has registrations across multiple events in the past 12 months, calling this endpoint per-card IS an N+1 pattern. T10.5 acknowledges N+1 concern but does not resolve it — it only bounds the scope to 12 months.

**No task defines how batch-fetch works.** Options not chosen between:
- a) Call per-event endpoint N times (bounded N+1 — acceptable if few events)
- b) New batch endpoint `GET /my-registrations?eventCodes=code1,code2,...`
- c) Include `myRegistrationStatus` in the events list API response

**Impact**: Could result in multiple parallel API calls on pages with event lists. Needs a decision before implementation starts.

**Recommendation**: Clarify in the story — if option (a) is chosen, add a note on the expected maximum N. If option (b), add it to T7 and T1.

---

#### ⚠️ GAP G2 — FR9: No task for `useEventRegistration` mutation cache invalidation

AC7 states: *"The `useEventRegistration` mutation success callback must call `queryClient.invalidateQueries(['my-registration', eventCode])`."*

**Problem**: There is no task in the story that modifies the existing `useEventRegistration` hook (or equivalent) to add this invalidation. The file is not listed in "Key Files to Modify". A dev reading only the task list could complete all tasks and miss the cache invalidation wiring.

**Impact**: Registration status banner would show stale data immediately after a successful registration, requiring a page refresh.

**Recommendation**: Add a subtask — e.g., *"T6.8 — In the event registration creation mutation (hook or component that calls `POST /api/v1/events/{eventCode}/registrations`), add `queryClient.invalidateQueries(['my-registration', eventCode])` to the `onSuccess` callback."*

---

### Coverage Statistics

- Total PRD FRs: 12
- FRs fully covered in story tasks: 10
- FRs partially covered (gap exists): 2
- FRs not covered: 0
- **Coverage: 83% full · 100% partial**



---

## UX Alignment Assessment

### UX Document Status

**Not Found** — no standalone UX design document exists for story 10.10.

However, the story is not UX-blind. Detailed UX specifications are **embedded within the acceptance criteria and task list**:

| UX Element | Specified In | Detail Level |
|------------|-------------|--------------|
| Status banner colours/icons | AC2, T8.5 | Full — MUI Alert `severity`, icon components named |
| Banner skeleton dimensions | AC4, T8.3 | Full — MUI Skeleton `height="56px"`, no CLS |
| Banner link text + routing | AC3, T8.6 | Full — i18n keys + `/register/{eventCode}` |
| EventCard chip positioning | AC5, T10.2 | Full — top-right corner, Tailwind colour classes specified |
| Registration Wizard guard layout | AC6, T11 | Partial — described functionally, no pixel layout |
| Loading states | AC4 | Full — skeleton before resolve |
| Empty state (not registered) | AC8 | Full — no render (`return null`) |

**Assessment**: The embedded UX specs in the story are sufficient for component-level implementation. No wireframes are strictly needed. This is a common and acceptable pattern for backend-driven status-display UI — there is no novel interaction design involved.

---

### Alignment Issues

No UX ↔ PRD misalignment found. The story AC descriptions are consistent with the PRD scope section.

One **UX concern not addressed anywhere**:

⚠️ **UX-G1 — Wizard guard "Register again" backend behaviour undefined**

AC6 states: *"for `CANCELLED` → 'Register again' button that proceeds to step 1 of the wizard, clears existing cancelled registration via a new re-registration endpoint or allows the backend to detect the cancelled status and create a new registration."*

T11.4 says: *"verify this behaviour or file it as a follow-up"*

**Problem**: The backend behaviour for re-registration of a cancelled user is explicitly unresolved. If `createRegistration()` throws `IllegalStateException` on duplicate regardless of status, the "Register again" flow will silently fail at submit. This is a UX cliff edge.

**Impact**: Medium-high. The wizard guard is specifically added to improve UX over the current error-on-submit problem. If the "Register again" path hits the same backend error, the story's main usability goal is only half-delivered.

**Recommendation**: Resolve before implementation — either:
- a) Confirm that `RegistrationService.createRegistration()` already handles `CANCELLED` → creates new registration (check existing service code)
- b) Or add a task to update `createRegistration()` to allow re-registration for `CANCELLED` users

---

### Warnings

⚠️ No formal wireframes or UX design document for story 10.10. **Acceptable** for this story given embedded UX specs in ACs. If the visual design deviates significantly in implementation, there is no reference to validate against.

⚠️ WCAG / accessibility requirements not explicitly stated for the new components (`RegistrationStatusBanner`, `RegistrationStatusGuard`, status chip). MUI Alert components are inherently accessible (use ARIA roles), but the chip in `EventCard.tsx` uses Tailwind-only styling — confirm `aria-label` or visually-hidden text is included for screen readers (not currently mentioned in T10).


---

## Epic Quality Review

### User Value Focus

✅ **Story is user-centric.** Title and user story clearly describe attendee benefit ("never accidentally register twice", "always know status at a glance"). This is not a technical milestone — it delivers visible UX value.

✅ **Independent story.** PRD states: "Prerequisite: None (independent — uses existing registration + auth APIs)." No hard dependency on any other in-progress story.

---

### Story Sizing Assessment

⚠️ **Story is large.** 13 major tasks with ~60 subtasks spanning:
- Backend: API spec, repository, service, controller, 7 integration tests
- Frontend: hook, service, banner component, EventCard chip, wizard guard, i18n (10 locales)
- Test: unit tests (2 suites), type-check, lint, Playwright E2E

This is arguably 2 stories (backend + frontend) merged into one. In isolation it's implementable, but represents significant scope for a single delivery unit. **No story point estimate is present** — sprint planning impact is unclear.

**Recommendation (minor)**: Consider noting an estimate (e.g., 8 SP) or splitting into 10.10a (backend endpoint + TDD) and 10.10b (frontend components). Not blocking.

---

### Acceptance Criteria Review

| AC | Format | Testable | Complete | Verdict |
|----|--------|----------|----------|---------|
| AC1 | Specification | ✅ | ✅ Status codes, fields, auth all specified | ✅ |
| AC2 | Specification | ✅ | ✅ All 4 states, i18n keys, conditions | ✅ |
| AC3 | Specification | ✅ | ✅ Both link variants specified | ✅ |
| AC4 | Specification | ✅ | ✅ Skeleton dimensions, no-CLS requirement | ✅ |
| AC5 | Specification | ✅ | ✅ Tailwind classes specified, 12-month scope | ✅ |
| AC6 | Specification | ⚠️ | ❌ **Ambiguous backend behaviour** — see EQ-1 | ⚠️ |
| AC7 | Specification | ✅ | ⚠️ Invalidation stated but no task covers it — Gap G2 | ⚠️ |
| AC8 | Specification | ✅ | ✅ | ✅ |
| AC9 | Specification | ✅ | ✅ 6 test cases enumerated | ✅ |
| AC10 | Specification | ✅ | ✅ Lint + type-check as gates | ✅ |

---

### Dependency Analysis

**Within-story dependencies** (sequential tasks, valid):
- T1 (OpenAPI spec) → T4 (controller) + T6/T7 (frontend types) — correct ADR-006 ordering ✅
- T5 (integration tests written first) → T2/T3/T4 (RED phase TDD) ✅

**Forward-story dependencies (soft):**
- Story 10.12 (deregistration) must also invalidate `my-registration` query. If 10.12 is not implemented, deregistering a user leaves the status banner showing the old status for up to 5 minutes. **Acceptable known limitation** — low risk.
- Story 10.11 (capacity/waitlist) extends `RegistrationStatusBanner.tsx` with waitlist position. Component must be designed for extension — not mentioned in T8. **Minor forward-coupling concern** — the story should note this.

---

### Best Practices Checklist

| Check | Result |
|-------|--------|
| Story delivers user value | ✅ |
| Story is independent (no hard prerequisites) | ✅ |
| No forward dependencies (hard) | ✅ |
| Acceptance criteria are testable | ✅ (9/10 clean, 1 ambiguous) |
| DB migration only when needed | ✅ (no new migration required) |
| FR traceability maintained | ✅ |
| Story estimate present | ❌ Missing |
| No ambiguous implementation decisions | ❌ AC6 CANCELLED re-registration path |

---

### 🔴 Critical Violations

None.

---

### 🟠 Major Issues

**EQ-1 — AC6: CANCELLED re-registration path is ambiguous**

AC6 uses "OR" for a backend behaviour decision: *"clears existing cancelled registration via a new re-registration endpoint **OR** allows the backend to detect the cancelled status and create a new registration."*

This is an implementation decision delegated to the dev agent. If `createRegistration()` currently throws `IllegalStateException` for any existing registration regardless of status, the "Register again" UI path will silently fail. This could require a backend fix not accounted for in the task list.

**Remediation**: Before implementation, run: `grep -n "IllegalStateException\|CANCELLED\|cancelled" services/event-management-service/src/main/java/ch/batbern/events/service/RegistrationService.java` to confirm whether `CANCELLED` users can re-register. If not, add a task to handle it.

---

### 🟡 Minor Concerns

**EQ-2 — No story point estimate** — makes sprint planning opaque. Recommend adding estimate (suggested: 8 SP based on scope).

**EQ-3 — `RegistrationStatusGuard.tsx` is listed in "New Files" but has no dedicated task** — it's implied within T11 ("add guard screen") but splitting the task would prevent it being missed.

**EQ-4 — EventCard chip accessibility gap** — T10 does not specify `aria-label` or visually-hidden text for the status chip. Screen readers would read only the Tailwind-styled text. Needs explicit mention.

**EQ-5 — T10.5 parent component batch-fetch is vague** (also flagged as G1) — "e.g., `UpcomingEventsSection`, `ArchivePage`" is non-exhaustive. A dev could complete T10 correctly but miss pages that render events.


---

## Summary and Recommendations

### Overall Readiness Status

## 🟡 NEEDS WORK — Conditional Go

Story 10.10 is well-specified and clearly valuable. The architecture is sound, TDD requirements are explicit, and ADR compliance is mandated. However, **3 items need resolution before a dev agent begins** — they are quick to resolve (minutes to hours) and none require redesigning the story.

---

### Issue Summary

| ID | Severity | Category | Issue |
|----|----------|----------|-------|
| EQ-1 | 🟠 Major | Story Quality | AC6: CANCELLED re-registration backend path is ambiguous ("or") — could require unplanned backend changes |
| G1 | 🟠 Major | Coverage Gap | FR7: EventCard parent batch-fetch strategy not specified — could become N+1 at scale or require a new endpoint |
| G2 | 🟠 Major | Coverage Gap | FR9/AC7: No task covers wiring cache invalidation into the existing registration creation mutation |
| D1 | 🟡 Minor | PRD Divergence | Server-side Caffeine cache: PRD says yes, story says no — follow story file |
| D2 | 🟡 Minor | PRD Divergence | Response DTO: PRD omits `eventCode` field — follow story file |
| D3 | 🟡 Minor | PRD Divergence | i18n key prefix differs — follow story file (more granular keys) |
| D4 | 🟡 Minor | PRD Divergence | Integration test count: PRD=5, story=6 (CANCELLED case missing from PRD) — follow story file |
| EQ-2 | 🟡 Minor | Story Quality | No story point estimate — sprint planning opaque |
| EQ-3 | 🟡 Minor | Story Quality | `RegistrationStatusGuard.tsx` has no dedicated task — implied in T11 |
| EQ-4 | 🟡 Minor | Story Quality | EventCard status chip missing `aria-label` / accessible text requirement |
| UX-G1 | 🟠 Major | UX Gap | Same as EQ-1 — CANCELLED "Register again" UX path relies on unverified backend behaviour |

**Total: 3 major · 7 minor · 0 critical**

---

### Critical Issues Requiring Immediate Action (Before Dev Starts)

**Action 1 — Resolve AC6 CANCELLED re-registration (EQ-1 / UX-G1)**

Run this before writing a single line of code:
```bash
grep -n "IllegalStateException\|cancelled\|CANCELLED" \
  services/event-management-service/src/main/java/ch/batbern/events/service/RegistrationService.java
```
Then either:
- Confirm `createRegistration()` allows new registration when existing is `CANCELLED` → note it in the story
- Or add a task: *"T4.6 — Update `createRegistration()` to allow re-registration for CANCELLED status"*

**Action 2 — Add missing cache invalidation task (G2)**

Add to story file under "Frontend" tasks:
> **T6.8 — Wire cache invalidation** (AC: #7)
> In the mutation hook / component that calls `POST /api/v1/events/{eventCode}/registrations`, add `queryClient.invalidateQueries(['my-registration', eventCode])` to the `onSuccess` callback. Also add this file to "Key Files to Modify".

**Action 3 — Clarify EventCard parent batch-fetch strategy (G1)**

Decide and document one of:
- a) N+1 per-card calls bounded to ≤12 months of events (acceptable if typical event count is <10)
- b) New batch endpoint `GET /my-registrations?eventCodes=...` (more work, needed if archive shows >20 events)

Add the decision to T10.5 so the dev agent has a clear path.

---

### Recommended Next Steps

1. **Nissim resolves Action 1-3** in the story file (estimated 30 min) — then story is dev-ready
2. **Dev agent proceeds with T1 first** (OpenAPI spec) as mandated by ADR-006
3. **PRD divergences D1-D4 do not need fixing** — the story file is authoritative; optionally update the PRD section to sync
4. **EQ-4 (accessibility)**: Add `aria-label` requirement to T10.2 before implementation

---

### Final Note

This assessment identified **10 issues across 5 categories** for story 10.10. The 3 major issues are pre-implementation decisions that take ~30 minutes to resolve. Once resolved, the story has clear, well-specified acceptance criteria, strong ADR compliance requirements, explicit TDD requirements, and sufficient architectural context to proceed directly to implementation. No redesign is needed.

**Report:** `docs/implementation-readiness-report-2026-03-01.md`

**Assessor:** Winston (BATbern Architect Agent) · 2026-03-01

