---
stepsCompleted: ["step-01-document-discovery", "step-02-prd-analysis", "step-03-epic-coverage-validation", "step-04-ux-alignment", "step-05-epic-quality-review", "step-06-final-assessment"]
story: "10-18"
documents:
  story: "_bmad-output/implementation-artifacts/10-18-event-archival-task-notification-cleanup.md"
  prd: "docs/prd/epic-10-additional-stories.md"
  architecture: "docs/architecture/ (notification-system, backend, data, state-machines)"
  ux: "N/A (backend-only story)"
---

# Implementation Readiness Assessment Report

**Date:** 2026-03-01
**Project:** BATbern
**Story:** 10.18 — Event Archival, Task & Notification Cleanup

## Document Inventory

| Document | File | Status |
|----------|------|--------|
| Story Artifact | `_bmad-output/implementation-artifacts/10-18-event-archival-task-notification-cleanup.md` | ✅ Found |
| PRD (Epic 10) | `docs/prd/epic-10-additional-stories.md` | ✅ Found |
| Architecture | `docs/architecture/` (notification-system, backend, data, state-machines) | ✅ Found |
| UX Design | N/A | ✅ Not required (backend-only) |

---

## PRD Analysis

### Functional Requirements

FR1: When an event transitions to `ARCHIVED` state, all non-completed tasks for that event are bulk-updated to `status = 'cancelled'`. Already-completed or cancelled tasks are skipped (idempotent).

FR2: Cancelled tasks receive `cancelledReason = "Event archived"` and `cancelledAt = now()` — **conditional** on whether these columns already exist in `EventTask` (pre-check T0 required before implementation).

FR3: `EventTaskRepository.findTasksDueForReminder()` query must explicitly exclude `status IN ('completed', 'cancelled')` so no task reminder emails fire for tasks belonging to archived events.

FR4: All registrations with `status = 'waitlist'` (exact string to be verified against DB) for the archived event are bulk-updated to `status = 'cancelled'`. Confirmed/registered registrations are preserved unchanged (historical data integrity).

FR5: All unread `Notification` records where `eventCode = archivedEventCode` are bulk-updated to `status = 'READ'` (or `read = true` depending on entity design — pre-check required). If no such records exist, step is skipped without error.

FR6: All 3 cleanup steps (task cancellation, waitlist cancellation, notification dismissal) execute within a single `@Transactional` method in a new `EventArchivalCleanupService`.

FR7: Steps 2 (waitlist cancellation) and 3 (notification dismissal) individually catch exceptions and log at WARN level but do **not** roll back step 1 (task cancellation is the primary/critical step).

FR8: Cleanup is idempotent — calling `cleanup()` twice on the same event produces the same final state with no errors or exceptions.

FR9: `EventWorkflowStateMachine.transitionToState()` calls `eventArchivalCleanupService.cleanup(eventCode)` when the new state is `ARCHIVED`.

FR10: TDD compliance — `EventArchivalCleanupServiceTest` written FIRST (RED phase) covering all paths (bulk cancel, waitlist cancel, notification dismiss, idempotency, partial failure). Integration test archives a real event and verifies DB state via Testcontainers PostgreSQL.

**Total FRs: 10**

---

### Non-Functional Requirements

NFR1: **Performance** — Bulk updates MUST use JPQL `@Modifying @Query` (not `findAll()` + iterate + `saveAll()`). Archiving a large event should not degrade.

NFR2: **Reliability/Idempotency** — The entire cleanup is safe to call multiple times with identical results.

NFR3: **Isolation** — No REST endpoints added; cleanup is fully internal, triggered only by the state machine.

NFR4: **Testability** — All integration tests extend `AbstractIntegrationTest` (PostgreSQL via Testcontainers). H2 is forbidden.

NFR5: **Code Quality** — Checkstyle passes; no violations in new or modified files.

NFR6: **Scope Containment** — No frontend changes, no OpenAPI spec changes, no TypeScript changes.

NFR7: **Observability** — Successful cleanup steps logged at INFO; best-effort step failures logged at WARN with message detail.

**Total NFRs: 7**

---

### Additional Requirements & Constraints

C1: **Pre-check T0 is mandatory** before writing any code — verify whether `EventTask.java` already has `cancelledReason` and `cancelledAt` fields. If absent, a conditional Flyway migration V76 is required. This is a branching decision point.

C2: **Waitlist status string must be verified** (`"waitlist"` vs `"waitlisted"`) via grep before implementing repository query. Wrong string = silent data integrity failure.

C3: **Notification.status type must be verified** (String vs `NotificationStatus` enum) before writing JPQL. Wrong type in JPQL = runtime exception.

C4: **No circular dependency**: `EventArchivalCleanupService` → repositories only; `EventWorkflowStateMachine` → `EventArchivalCleanupService` → no back-reference. Must be validated before injection.

C5: **Flyway version**: V76 is the next available only if stories 10.12–10.16 migrations (V73–V75) are already applied. Dev must verify current migration state.

---

### PRD Completeness Assessment

The PRD for story 10.18 is **well-defined** with clear scope and boundaries. The story artifact provides highly detailed implementation guidance (repository queries, service code templates, test structure).

**Minor PRD ↔ Story Artifact discrepancies identified:**
- PRD references `EventLifecycleService.java` or `EventStateTransitionHandler.java` as the hook point; story artifact uses `EventWorkflowStateMachine` — the story artifact is more specific and likely more accurate.
- PRD mentions `dismissedAt = now()` on notifications; story artifact does not — needs verification against actual `Notification` entity structure.
- PRD says "read = true"; story artifact says `status = 'READ'` — suggests Notification uses a status string, not a boolean. Pre-check (T6.1) resolves this.

These discrepancies do not block implementation — all are resolved by the pre-check tasks (T0, T6) built into the story.

---

## Epic Coverage Validation

### Coverage Matrix

| FR # | PRD Requirement (summary) | AC Coverage | Task Coverage | Status |
|------|--------------------------|-------------|---------------|--------|
| FR1 | Bulk-cancel open tasks on ARCHIVED transition | AC1 | T4 (`cancelOpenTasksForEvent`), T8 (Step 1) | ✅ Covered |
| FR2 | Set `cancelledReason`/`cancelledAt` on cancelled tasks (conditional) | AC1 (conditional note) | T0.1–T0.2 (pre-check), T1 (V76 migration), T2 (entity fields) | ✅ Covered |
| FR3 | `findTasksDueForReminder()` excludes cancelled tasks | AC2 | T3 (RED test), T4.2 (query update) | ✅ Covered |
| FR4 | Waitlist registrations bulk-cancelled; confirmed preserved | AC3 | T0.4 (waitlist string verify), T5 (repo query), T8 Step 2 | ✅ Covered |
| FR5 | Unread notifications bulk-dismissed for archived event | AC4 | T6 (NotificationRepository query), T8 Step 3 | ✅ Covered |
| FR6 | All 3 steps in single `@Transactional` method | AC5 | T8 (`EventArchivalCleanupService.cleanup()`) | ✅ Covered |
| FR7 | Steps 2–3 catch exceptions, log, don't roll back step 1 | AC5 | T7.7 (test partial failure), T8 (try/catch pattern) | ✅ Covered |
| FR8 | Idempotent — calling twice is safe | AC6 | T7.6 (unit test idempotency), T11.5 (integration test) | ✅ Covered |
| FR9 | State machine calls cleanup on ARCHIVED transition | AC7 | T9 (read state machine), T10 (inject + hook) | ✅ Covered |
| FR10 | TDD: tests written first (RED → GREEN → REFACTOR) | AC8 | T3 (repo RED), T7 (service RED), T11 (integration RED), T13 (full suite) | ✅ Covered |

### Missing Requirements

None. All 10 FRs have traceable coverage in the story's ACs and Tasks.

### Coverage Statistics

- Total PRD FRs: **10**
- FRs covered in story: **10**
- Coverage percentage: **100%**

---

## UX Alignment Assessment

### UX Document Status

**Not Found** — No UX design document exists for Story 10.18. This is expected and intentional.

### Justification

Story 10.18 is a **backend-only data integrity bug fix** with zero UI changes:
- PRD explicitly states: *"no TypeScript changes needed"*
- Story artifact Dev Notes: *"This is a backend-only bug fix with no frontend changes"*
- The UX benefit (clean task board, notification center) is a side-effect of backend cleanup — the organizer sees fewer stale items automatically, with no new UI elements required

### Alignment Issues

None — no UX/PRD/Architecture misalignment to report.

### Warnings

✅ **No warning** — The absence of UX documentation is correct for this backend-only story. Issuing a warning here would be a false positive.

---

## Epic Quality Review

### Epic Structure Validation

**Epic 10 ("Additional Stories"):**
- **User Value Focus:** ✅ The epic umbrella is a pragmatic grouping for independent enhancements and bug fixes. Story 10.18 delivers concrete organizer value: clean task boards and notification centers.
- **Epic Independence:** ✅ Story 10.18 explicitly has no prerequisites. It can be completed independently at any point in Epic 10.
- **Not a technical milestone:** ✅ The story is framed from an organizer's perspective with a clear "so that..." clause.

---

### Story Quality Assessment

#### User Value
✅ **Clear user value**: The user story follows proper format — *As an organizer, I want... so that my task board and notification center are not cluttered with stale items.*

#### Independence
✅ **Fully independent**: No prerequisite story. No dependency on other in-progress stories. Can be developed and deployed standalone.

#### Story Sizing
✅ **Appropriate**: 7 phases / 13 task groups for a brownfield bug fix with full TDD compliance. All phases are cohesive and address a single well-bounded concern.

---

### Acceptance Criteria Review

| AC | Description | BDD Testable | Error Conditions | Verdict |
|----|-------------|-------------|-----------------|---------|
| AC1 | Task bulk cancellation on ARCHIVED | ✅ | ✅ (idempotency case) | ✅ Pass |
| AC2 | Scheduler query excludes cancelled | ✅ | ✅ | ✅ Pass |
| AC3 | Waitlist registrations cancelled; confirmed preserved | ✅ | ✅ (preservation case explicit) | ✅ Pass |
| AC4 | Notifications dismissed; empty case skipped | ✅ | ✅ (no-op case explicit) | ✅ Pass |
| AC5 | Single @Transactional; steps 2-4 best-effort | ✅ | ✅ (fault isolation specified) | ⚠️ See note |
| AC6 | Idempotent | ✅ | ✅ | ✅ Pass |
| AC7 | Hooked into ARCHIVED transition | ✅ | n/a | ✅ Pass |
| AC8 | TDD: RED phase written first | ✅ (process AC) | n/a | ✅ Pass |

---

### Dependency Analysis

**Within-Epic Dependencies:** None — story 10.18 has no declared predecessors.

**Database/Entity Creation Timing:** ✅ Correct brownfield approach — Flyway migration V76 is conditional (only created if `EventTask` lacks the fields). Tables are modified only when needed.

**Forward Dependencies:** None found.

---

### Best Practices Compliance Checklist

- [x] Story delivers user value (organizer quality-of-life improvement)
- [x] Story can function independently (no prerequisites)
- [x] Stories appropriately sized (7 phases, coherent scope)
- [x] No forward dependencies
- [x] Database migration created only when needed (conditional V76)
- [x] Clear acceptance criteria with testable outcomes
- [x] Traceability to FRs maintained (10/10 FRs covered)

---

### Findings by Severity

#### 🔴 Critical Violations
None.

#### 🟠 Major Issues
None.

#### 🟡 Minor Concerns

**MC1 — AC5 "steps 2-4" numbering is misleading**
> AC5 states: *"Task cancellation (AC1) is most critical; steps 2-4 individually caught and logged..."*
> The cleanup method has only **3 steps** (task cancel, waitlist cancel, notification dismiss). "Steps 2-4" implies 3 non-critical steps but there are only 2 (waitlist and notifications). This should read "steps 2-3." The implementation code in the story is correct (only 2 try/catch blocks for steps 2 and 3); it's only the AC wording that misstates the count.
> **Recommendation**: Update AC5 to read "steps 2 and 3 individually caught and logged..."

**MC2 — Waitlist status string ambiguity not fully resolved in story**
> The story acknowledges two possible values (`"waitlist"` vs `"waitlisted"`) and defers resolution to T0.4 (grep pre-check). While this is pragmatic for a brownfield project, an IR-ready story ideally has this resolved. However, given the pre-check is explicitly modeled as Phase 0, this is acceptable.
> **Recommendation**: Low priority — the dev workflow handles it. No blocking concern.

**MC3 — Notification entity design uncertainty**
> The story notes: *"If `Notification` entity uses `NotificationStatus` enum instead of a String status, use `n.status != ch.batbern.events.notification.NotificationStatus.READ`."* This uncertainty is pre-checked (T6.1) but leaves two code branches unresolved at story-writing time. Not a blocker given T6.1 resolves it.
> **Recommendation**: Low priority — T6.1 is explicit. The story handles it correctly with a "check first" pattern.

---

## Summary and Recommendations

### Overall Readiness Status

## ✅ READY FOR DEV

Story 10.18 is implementation-ready. All functional and non-functional requirements are clearly defined, fully traceable, and appropriately covered by tasks. The story is self-contained with no unresolved blockers.

---

### Critical Issues Requiring Immediate Action

**None.** There are no critical or major issues blocking implementation.

---

### Minor Issues (Non-blocking)

| # | Issue | Severity | Recommendation |
|---|-------|----------|----------------|
| MC1 | AC5 says "steps 2-4" but only 2 non-critical steps exist (should be "steps 2-3") | 🟡 Minor | Update AC5 wording in story artifact before dev starts — takes 30 seconds |
| MC2 | Waitlist status string ('waitlist' vs 'waitlisted') unresolved at story authoring time | 🟡 Minor | T0.4 pre-check (mandatory Phase 0) resolves this — do not skip |
| MC3 | Notification.status type (String vs enum) unresolved at story authoring time | 🟡 Minor | T6.1 pre-check resolves this — do not skip |

---

### Recommended Next Steps

1. **(Optional, 1 min)** Fix AC5 wording in the story artifact: replace "steps 2-4" with "steps 2 and 3" to eliminate numbering confusion.

2. **(Mandatory, before any code)** Execute all Phase 0 pre-checks (T0.1–T0.4): read `EventTask.java`, verify cancellation fields, determine next Flyway version, and grep for exact waitlist status string. These gate all subsequent phases.

3. **(TDD — in order)** Implement phases sequentially: Phase 0 (pre-check) → Phase 1–2 (conditional migration + entity, if needed) → Phase 3 (repository queries, TDD RED) → Phase 4 (service, TDD RED) → Phase 5 (state machine hook) → Phase 6 (integration test, TDD RED) → Phase 7 (full suite + checkstyle validation).

---

### Assessment Summary

| Category | Result | Notes |
|----------|--------|-------|
| Document Coverage | ✅ Complete | Story artifact + PRD both present |
| FR Extraction | ✅ 10 FRs identified | All requirements explicit and testable |
| NFR Coverage | ✅ 7 NFRs identified | Performance, testability, code quality all addressed |
| Epic Coverage | ✅ 100% (10/10 FRs) | Full traceability from FR → AC → Task |
| UX Alignment | ✅ N/A (backend-only) | Correctly scoped |
| Story Quality | ✅ Excellent | Clear ACs, independence, good sizing |
| Critical Violations | ✅ 0 | None found |
| Major Issues | ✅ 0 | None found |
| Minor Concerns | ⚠️ 3 | All handled by built-in pre-check phases |

---

### Final Note

This assessment identified **3 minor concerns** across **1 category** (Story Quality / AC wording). All three are either trivially fixable or resolved by the explicit pre-check tasks already built into the story's Phase 0. No action is required before beginning development — the story can proceed to dev as-is, with the optional AC5 wording fix recommended.

---

*Assessment completed: 2026-03-01 | Assessor: Winston (Architect Agent) | Story: 10.18 — Event Archival Task & Notification Cleanup*
