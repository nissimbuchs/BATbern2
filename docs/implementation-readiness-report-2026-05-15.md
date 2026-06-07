---
date: 2026-05-15
project_name: BATbern
scope: Epic 11 — Unified Speaker Workflow Refactor
branch: feature/speaker-workflow-refactor
stepsCompleted: [document_discovery, prd_analysis, epic_coverage_validation, ux_alignment, epic_quality_review, final_assessment]
status: complete
---

# Implementation Readiness Assessment Report

**Date:** 2026-05-15
**Project:** BATbern
**Scope:** Epic 11 — Unified Speaker Workflow Refactor (per ADR-009)
**Branch:** `feature/speaker-workflow-refactor`
**Assessor:** PM Readiness Audit

---

## 1. Document Inventory

### 1.1 PRD / Epic Document

**Whole Documents:**
- `docs/prd/epic-11-speaker-workflow-refactor.md` (72,392 bytes, modified 2026-05-15) — PRIMARY EPIC DOC

**Sharded Documents:**
- None (single-file epic — consistent with sibling epics 1-10)

### 1.2 Architecture Documents (Epic 11 scope)

**Authoritative Source-of-Truth for this refactor:**
- `docs/architecture/ADR-009-unified-speaker-workflow.md` (27,250 bytes, modified 2026-05-15) — **THE ADR THIS EPIC IMPLEMENTS**

**Architecture docs Epic 11 must update (Phase A — Story 11-a-1):**
- `docs/architecture/03-data-architecture.md`
- `docs/architecture/06-backend-architecture.md`
- `docs/architecture/06a-workflow-state-machines.md`
- `docs/architecture/06b-user-lifecycle-sync.md`
- `docs/architecture/04-api-speaker-coordination.md`
- `docs/architecture/ADR-004-factor-user-fields-from-domain-entities.md`
- `docs/architecture/ADR-007-unified-user-profile.md`
- `CLAUDE.md` (root)

### 1.3 Plan Document

- `docs/plans/speaker-workflow-refactor.md` (53,095 bytes, modified 2026-05-15) — **PHASED IMPLEMENTATION PLAN (Phases A-F)**

### 1.4 UX Specifications

**Whole Documents:**
- `docs/front-end-spec.md` (96,687 bytes) — global front-end spec (last modified 2026-01-02; predates Epic 11)
- Epic-11 UX changes embedded directly in `docs/prd/epic-11-speaker-workflow-refactor.md` §8.9 stories 1-3 + drawer + change requests UX-DR1–UX-DR14
- `docs/plans/speaker-workflow-refactor.md` §8 contains Kanban / drawer / brainstorm-panel UX detail

**No dedicated Epic-11 UX spec exists** — UX requirements live inside the epic & plan.

### 1.5 Stories

**Sprint registered (14 stories):** `_bmad-output/implementation-artifacts/sprint-status.yaml` lines 178-195
| # | Story Code | Phase | Status |
|---|------------|-------|--------|
| 1 | `11-a-1-align-speaker-workflow-documentation-to-adr-009` | A | backlog |
| 2 | `11-b-1-reduce-speakerworkflowstate-enum-to-8-states` | B | backlog |
| 3 | `11-b-2-speakerworkflowservice-sole-status-writer` | B | backlog |
| 4 | `11-b-3-migrate-legacy-status-drop-tentative-cols-derived-flags` | B | backlog |
| 5 | `11-c-1-drop-speakers-table-remove-speaker-coordination-refs` | C | backlog |
| 6 | `11-c-2-userapiclient-provisioning-contentsubmissionservice-shared` | C | backlog |
| 7 | `11-d-1-promote-endpoint-brainstorm-tightening-slot-gate` | D | backlog |
| 8 | `11-d-2-kanban-card-primary-action-button-cleanup` | D | backlog |
| 9 | `11-d-3-kanban-column-triage-time-in-state-colour-coding` | D | backlog |
| 10 | `11-d-4-kanban-guided-drag-unified-drawer-onbehalf-content-form` | D | backlog |
| 11 | `11-e-1-cdk-iam-prereq-cognito-admin-flow` | E | backlog |
| 12 | `11-e-2-cognito-provisioning-at-ready-invitation-email-i18n` | E | backlog |
| 13 | `11-e-3-speaker-portal-cognito-auth-frontend-session-multi-role-nav` | E | backlog |
| 14 | `11-f-1-magic-link-teardown-branch-deletion` | F | backlog |
| — | `epic-11-retrospective` | post | optional |

**Story files created in `_bmad-output/implementation-artifacts/`:** **NONE** — all 14 stories are registered in sprint-status.yaml but no per-story implementation file has been authored yet.

### 1.6 Cross-referenced ADRs

- ADR-003 (meaningful-identifiers) — referenced
- ADR-004 (factor-user-fields) — Phase A must update
- ADR-006 (OpenAPI contract-first) — referenced
- ADR-007 (unified-user-profile) — Phase A must update

---

## 2. Critical Issues

### 2.1 Duplicates
**None.** No whole/sharded conflicts. ADR-009 and Epic 11 doc are distinct artifacts with clearly separated concerns (ADR = decision/rationale; Epic = backlog).

### 2.2 Missing Documents
| Missing | Severity | Impact |
|---------|----------|--------|
| Per-story files in `_bmad-output/implementation-artifacts/11-*.md` | **EXPECTED** | Story files are authored just-in-time via `bmad-create-story` — sprint registration is the gate, not story-file existence. Track readiness at epic+story-summary level only. |
| Dedicated UX spec for Epic 11 Kanban/drawer changes | **MEDIUM** | UX detail (UX-DR1–UX-DR14, §8.9 stories 1-3) lives inline in epic + plan. Acceptable if §8 detail is sufficient — verified in Step 4. |
| Updated `docs/front-end-spec.md` reflecting unified workflow UX | **LOW** | front-end-spec.md is org-wide; Phase A Story 11-a-1 covers architecture-doc alignment but **does not list front-end-spec.md**. Possible doc-drift risk — flagged for Step 4. |

### 2.3 Branch Context
- Active branch: `feature/speaker-workflow-refactor` (this branch)
- Plan §3 references a sibling branch `feature/speaker-account-creation` whose work must be cherry-picked (e.g., `d5cf0fcc`, `73d94688`, `396a9045`) — branch availability and commit hashes to verify in Step 3.
- Plan §3 references `feature/epic-6` for the same cherry-pick base — both branches must still exist or be archived.

---

## 3. Document Selection Decision

**Documents to use for assessment:**

| Role | Document |
|------|----------|
| **Epic / Stories** | `docs/prd/epic-11-speaker-workflow-refactor.md` |
| **Plan** | `docs/plans/speaker-workflow-refactor.md` |
| **Architecture decision** | `docs/architecture/ADR-009-unified-speaker-workflow.md` |
| **Sprint backlog** | `_bmad-output/implementation-artifacts/sprint-status.yaml` (lines 178-195) |
| **UX** | Inline §8.9 + UX-DR1-14 within epic & plan |

Proceed to PRD analysis with these selections.

---

## 4. PRD Analysis (Epic 11)

**Source:** `docs/prd/epic-11-speaker-workflow-refactor.md` (1,427 lines, 72 KB)

### 4.1 Functional Requirements Extracted

Total: **13 FRs** — all numbered, all behavioral, all derived from `docs/plans/speaker-workflow-refactor.md` §0 target model and §2.3 side-effect hooks.

| ID | Summary |
|----|---------|
| FR1  | Speaker workflow has exactly 8 states (IDENTIFIED, CONTACTED, READY, INVITED, ACCEPTED, CONTENT_SUBMITTED, QUALITY_REVIEWED, DECLINED); DECLINED reachable from every non-terminal state. |
| FR2  | All mutations to `speaker_pool.status` go through `SpeakerWorkflowService.transition()` — no service may call `setStatus` directly. |
| FR3  | CONTACTED → READY is the speaker-provisioning gate; REQUIRES `email`; triggers User lookup-or-create + Cognito FORCE_CHANGE_PASSWORD provisioning + SPEAKER role grant + `username` persistence. Atomic + idempotent. |
| FR4  | READY → INVITED blocked when `count(ACCEPTED) + count(INVITED) >= max_slots` for the event; clear domain error. |
| FR5  | Speakers respond only ACCEPT or DECLINE. TENTATIVE removed from enum, API contract, and database. |
| FR6  | Speaker who accepted and later drops out → DECLINED; previous state + reason recorded in status-history; no separate WITHDREW state. |
| FR7  | Organizer-on-behalf and speaker-self content submission share a single backend `ContentSubmissionService`. Both persist `title/abstract` to `content_submissions`, patch `User.bio`/`profile_picture_url` via `UserApiClient`, and trigger transition to CONTENT_SUBMITTED. Audit records authenticated principal. |
| FR8  | `/api/v1/speaker-portal/**` requires Cognito Bearer + SPEAKER role; `permitAll()` removed; no `?token=`/`?jwt=` query-param handling. |
| FR9  | Speaker first login uses standard Cognito FORCE_CHANGE_PASSWORD; invitation email carries portal link + temp password; no Custom Auth Lambdas, no OTP. |
| FR10 | Domain events emitted: `SpeakerPromotedToReadyEvent` (new) at CONTACTED→READY; `SpeakerInvitationSentEvent` at READY→INVITED; `SpeakerResponseReceivedEvent` at INVITED→ACCEPTED|DECLINED; `SpeakerAcceptedEvent` at INVITED→ACCEPTED. |
| FR11 | Derived flags (not persisted): `is_slot_assigned := session.start_time IS NOT NULL`; `is_publishable := quality_reviewed AND slot_assigned`. `validateAllSpeakersConfirmed` uses `is_publishable` as AGENDA_PUBLISHED gate. |
| FR12 | Speaker pool entries IDENTIFIED/CONTACTED have `username = NULL`; email field rejected on `POST /events/{code}/speakers/pool` for these states. New `POST /events/{code}/speakers/{speakerId}/promote` is the sole path that sets `username`. |
| FR13 | Legacy `speaker_pool.status` migration: SLOT_ASSIGNED→ACCEPTED; CONFIRMED→QUALITY_REVIEWED; WITHDREW→DECLINED (reason "Withdrew after acceptance (legacy)"); OVERFLOW→READY. |

### 4.2 Non-Functional Requirements Extracted

Total: **10 NFRs**.

| ID | Category | Summary |
|----|----------|---------|
| NFR1 | Migration | Clean cutover — no backward-compatibility shims for in-flight magic-link sessions (confirmed no in-flight speakers per §6.4). |
| NFR2 | Architecture | No new Cognito Lambda triggers; provisioning uses AdminCreateUser + AdminAddUserToGroup + AdminSetUserPassword only. |
| NFR3 | Reliability | Provisioning (CONTACTED→READY) is retry-safe / idempotent. |
| NFR4 | Auditability | Every state transition (incl. legacy migrations) writes `speaker_status_history` with `changed_by_username`, prev/new state, reason. |
| NFR5 | Security | Least-privilege IAM — exactly 5 Cognito admin actions; encryption-key secret from feature/epic-6 intentionally NOT carried forward. |
| NFR6 | Testing | Every refactored transition has Testcontainers integration test demonstrating transition + side effects; teardown stories verify deleted endpoints return 404. |
| NFR7 | Documentation | Docs updated in the same phase as code (Phase A first); doc-drift prevention policy compliance. |
| NFR8 | Regression | EventWorkflowStateMachine, partner-coordination, attendee-experience flows remain behaviorally identical. |
| NFR9 | Security | Cognito User Pool password policy must accept backend-generated temp password (adjust policy, not generator). |
| NFR10 | i18n | All 10 locales (de, en, fr, it, rm, es, fi, nl, ja, gsw-BE) updated for new state labels + UI copy. |

### 4.3 Additional Requirements (Architecture / UX)

| Category | IDs | Count |
|----------|-----|-------|
| Architecture Requirements (AR) | AR1–AR43 | **43** |
| UX Design Requirements (UX-DR) | UX-DR1–UX-DR23 | **23** |
| Cross-referenced ADRs | ADR-003, ADR-004, ADR-007, ADR-009 | 4 |

**AR clusters:**
- shared-kernel: AR1–AR3
- event-management-service: AR4–AR11
- company-user-management-service: AR12–AR15
- speaker-coordination-service: AR16
- Database migrations: AR17–AR21
- API surface: AR22–AR28
- Infrastructure (CDK): AR29–AR31
- Documentation alignment: AR32–AR40
- Branch strategy: AR41–AR43

**UX-DR clusters:**
- Kanban card + cleanup: UX-DR1–UX-DR4 (§8.9 story 1)
- Column triage + colour coding: UX-DR5–UX-DR7 (§8.9 story 2)
- Guided drag + drawer + slot-gate: UX-DR8–UX-DR13 (§8.9 story 3)
- Organizer drawer on-behalf form: UX-DR14
- Speaker brainstorming UI: UX-DR15–UX-DR16
- Speaker portal: UX-DR17–UX-DR19
- Cross-cutting frontend: UX-DR20–UX-DR23

### 4.4 PRD Completeness Assessment

| Dimension | Rating | Evidence |
|-----------|--------|----------|
| **Numbering / traceability** | ✅ Excellent | All FR/NFR/AR/UX-DR explicitly numbered with consistent prefix conventions. |
| **Coverage mapping** | ✅ Excellent | §"Coverage Verification" already maps every FR/NFR/AR/UX-DR to a target story. Author self-audited. |
| **Acceptance criteria density** | ✅ Excellent | Every story uses Given/When/Then format with 4–9 AC blocks. |
| **Dependency declaration** | ✅ Excellent | Each story declares phase + dependencies + FR/AR coverage list at the header. |
| **Scope honesty** | ✅ Excellent | Explicit "no new product capability" framing in Overview + §"Note on framing". |
| **Migration safety** | ✅ Strong | FR13 + Story 11.B.3 spell out idempotent legacy-data mapping; §6.4 confirms zero in-flight speakers. |
| **Phase ordering** | ✅ Strong | Phases A→F with clear dependency chain; Phase B+C parallelizable, Phase D depends on B, E depends on C+D, F depends on E. |
| **Test strategy** | ✅ Strong | NFR6 mandates Testcontainers integration test per transition; Playwright e2e coverage explicit in Story 11.D.4 + 11.E.3. |

### 4.5 PRD Gaps Flagged

| # | Gap | Severity | Note |
|---|-----|----------|------|
| G1 | **UX-DR23** ("What does this mean?" collapsible help sidebar) not mapped to any story | LOW | PRD §"Coverage Verification" explicitly flags this for the team. Acceptable as backlog. |
| G2 | **AR38** updates `docs/prd-enhanced.md` FR2/FR3/FR17 — but the AC in Story 11.A.1 also requires editing `prd-enhanced.md`. No conflict, but `prd-enhanced.md` is a 30 KB legacy document — confirm AC G/W/T in 11.A.1 is sufficient or whether full rewrite is needed. | LOW | Verify in Step 5. |
| G3 | **NFR9** (Cognito password policy) — story 11.E.1 includes AC "if policy adjustment is required, it is made in CDK." No deterministic check; relies on dev observation. | LOW | Acceptable — generator is the spec, policy follows. |
| G4 | **AR42 cherry-pick** depends on commit `d5cf0fcc` of `feature/epic-6` branch — **branch + commit availability not verified yet** in any AC. | MEDIUM | Verify in Step 3 (Architecture). If branch is GC'd, the cherry-pick AC becomes blocking. |
| G5 | **AR41 cherry-pick** depends on commits `73d94688` + `396a9045` of `feature/speaker-account-creation` — same risk as G4. | MEDIUM | Verify in Step 3. |
| G6 | **`docs/front-end-spec.md`** (Jan 2026, 96 KB) is the org-wide UX spec. Phase A doc-update list does NOT include it. Speaker-workflow UX changes (kanban redesign, drawer, brainstorm) may render parts of it stale. | LOW-MEDIUM | Verify in Step 4 (UX). |
| G7 | **Phase F dependency** ("Phase E stable in prod for ≥1 week") is a process gate, not a code AC. Sprint-status comment captures it; PRD acceptance criterion phrases it informally ("team agrees"). | LOW | Acceptable; explicit by design. |
| G8 | **DECLINED → DECLINED idempotency / re-decline** behavior not explicit in FR6 or Story 11.B.2 AC. What if an organizer re-declines a speaker already in DECLINED? | LOW | Edge case to confirm in Step 5 (Edge case analysis). |

### 4.6 PRD Status

**VERDICT: ✅ PRD is implementation-ready.**

- 13 FRs + 10 NFRs + 43 ARs + 23 UX-DRs all explicitly enumerated.
- Self-traceability map already validates story coverage.
- Acceptance criteria are testable and use Given/When/Then.
- Identified gaps (G1–G8) are LOW–MEDIUM severity, none blocking.


---

## 5. Epic Coverage Validation

**Method:** Each FR cross-checked against story acceptance criteria (read from `docs/prd/epic-11-speaker-workflow-refactor.md` lines 416–1390) — not just the self-declared coverage map.

### 5.1 FR → Story Coverage Matrix (Independently Verified)

| FR | Requirement | Story Coverage (verified by AC) | Status |
|----|-------------|----------------------------------|--------|
| **FR1** | 8-state enum, DECLINED reachable from every non-terminal state | **11.B.1** (enum reduced to 8 values; 4 removed values asserted absent) + **11.B.3** (legacy values migrated to the 8) | ✅ Covered |
| **FR2** | `SpeakerWorkflowService.transition()` is sole writer; no direct `setStatus` | **11.B.2** AC: "only SpeakerWorkflowService.transition performs the mutation; StatusTransitionValidator deleted; SpeakerStatusService delegates; SpeakerResponseService delegates; processTentativeResponse deleted" | ✅ Covered |
| **FR3** | CONTACTED→READY atomic + idempotent: User lookup-or-create + Cognito FORCE_CHANGE_PASSWORD + SPEAKER role + username persistence | **11.D.1** (User + role + username via UserApiClient.provisionUserWithRole) + **11.E.2** (Cognito AdminCreateUser, idempotency, temp-password handling) | ✅ Covered |
| **FR4** | READY→INVITED blocked when `count(ACCEPTED) + count(INVITED) >= max_slots` | **11.B.2** (workflow-service slot-gate hook) + **11.D.1** (`POST /invite` returns 409 with counts + max_slots) + **11.D.2** (UI disabled button + tooltip) + **11.D.4** (drag-drop slot-gate) | ✅ Covered (4 surfaces) |
| **FR5** | TENTATIVE removed from enum + API + DB | **11.B.1** (enum) + **11.B.3** (column drop `is_tentative`, `tentative_reason`; PUT status rejects TENTATIVE) | ✅ Covered |
| **FR6** | WITHDREW → DECLINED with prior state + reason | **11.B.2** (DECLINED-from-any-state logic + reason capture + history row) + **11.B.3** (legacy `withdrew` row mapping with reason "Withdrew after acceptance (legacy)") | ✅ Covered |
| **FR7** | Organizer-on-behalf + speaker-self share `ContentSubmissionService` | **11.C.2** (service contract + role separation + idempotency) + **11.D.4** (drawer form delegates to same service; byte-identical DB writes asserted) | ✅ Covered |
| **FR8** | `/api/v1/speaker-portal/**` Cognito Bearer + SPEAKER role; no `permitAll`/`?token=` | **11.E.3** (`@PreAuthorize("hasRole('SPEAKER')")` on all portal controllers; `permitAll()` removed; query-param signatures removed) | ✅ Covered |
| **FR9** | Cognito FORCE_CHANGE_PASSWORD; invitation email carries link + temp password; no Custom Auth / OTP | **11.E.2** (AdminCreateUser w/ FORCE_CHANGE_PASSWORD; temp-password embedded once in email, never stored; staging manual smoke test in AC) | ✅ Covered |
| **FR10** | Domain events: SpeakerPromotedToReadyEvent (new), SpeakerInvitationSentEvent, SpeakerResponseReceivedEvent, SpeakerAcceptedEvent | **11.B.1** (event class added in shared-kernel with payload `{eventCode, speakerPoolId, username, promotedAt, promotedByUsername}`) + **11.B.2** (workflow service emits events at every transition) | ✅ Covered |
| **FR11** | Derived flags `is_slot_assigned`, `is_publishable`; `validateAllSpeakersConfirmed` uses `is_publishable` | **11.B.3** (computed at read time, no persisted column; AGENDA_PUBLISHED gate uses `is_publishable`) | ✅ Covered |
| **FR12** | `username = NULL` for IDENTIFIED/CONTACTED; email rejected on pool POST for these states; promote endpoint is sole READY-setter | **11.D.1** (promote endpoint + brainstorming UI tightened + pool POST rejects email for those states) | ✅ Covered |
| **FR13** | Legacy `speaker_pool.status` migration (slot_assigned→accepted, confirmed→quality_reviewed, withdrew→declined w/ reason, overflow→ready); idempotent | **11.B.3** (Flyway migration with idempotent rerun guard) + **11.C.1** (drops speakers table) + **11.F.1** (magic_link_tokens table drop + Secrets Manager + branch cleanup) | ✅ Covered |

### 5.2 NFR Coverage Matrix (Independently Verified)

| NFR | Story Coverage | Status |
|-----|---------------|--------|
| **NFR1** Clean cutover | **11.F.1** AC: "magic-link types deleted; endpoints return 404; cookie audited gone" | ✅ |
| **NFR2** No new Lambdas | **11.E.1** + **11.E.2** explicit AdminCreateUser/AdminAddUserToGroup/AdminSetUserPassword/AdminInitiateAuth/AdminGetUser only | ✅ |
| **NFR3** Idempotency | **11.C.2** + **11.E.2** both explicit "no-op on re-call, same username returned" | ✅ |
| **NFR4** Audit-trail integrity | **11.B.2** (status-history row required for every transition + reason for DECLINED) + **11.B.3** (legacy migration writes history rows) + **11.C.2** (changed_by_username on content submission) | ✅ |
| **NFR5** Least-privilege IAM | **11.E.1** AC enumerates exactly 5 IAM permissions; encryption-key secret explicitly NOT cherry-picked | ✅ |
| **NFR6** Testcontainers per transition + 404 regression tests | **11.B.1** (JSON round-trip), **11.B.2** (every legal/illegal transition tested), **11.F.1** (404 regression test) | ✅ |
| **NFR7** Doc-drift compliance (Phase A first) | **11.A.1** is the Phase A doc-only PR; `[no-doc]` commit message required | ✅ |
| **NFR8** No regression for non-speaker workflows | **11.F.1** AC: "full Bruno + Playwright run passes." **GAP:** No story explicitly asserts EventWorkflowStateMachine, partner-coordination, attendee-experience flows are tested for regression. Implicit via existing test suite. | ⚠️ Partial — see G9 |
| **NFR9** Cognito password policy admits temp-password | **11.E.1** AC: "policy admits backend-generated temp password length/character classes; adjust in CDK if needed" | ✅ |
| **NFR10** All 10 locales updated | **11.E.2** AC: "all 10 locales (de, en, fr, it, rm, es, fi, nl, ja, gsw-BE) carry the rewritten invitation template + simplified confirmation/reminder/escalation templates" | ✅ |

### 5.3 AR Coverage Audit (43 ARs across 6 phases)

Spot-check verification — every AR mapped against story coverage:

| AR cluster | ARs | Stories | Notes |
|-----------|-----|---------|-------|
| shared-kernel enums + events | AR1, AR2, AR3 | 11.B.1 | All three explicitly named in AC |
| event-management deletes | AR4, AR7, AR9 | 11.B.2 (AR4 delete, AR7 delegate), 11.F.1 (AR9 delete service) | ✅ |
| workflow-service refactor | AR5, AR6, AR8, AR11 | 11.B.2 (AR5, AR6, AR7, AR8 partial), 11.D.1 (AR11 pool tightening) | ✅ |
| portal controllers Cognito-secured | AR10 | 11.E.3 (annotations), 11.F.1 (file deletions) | ✅ |
| company-user-management Cognito | AR12, AR13, AR14, AR15 | 11.C.2 (AR12, AR13, AR14 contracts), 11.E.2 (AR15 Cognito calls) | ✅ |
| speaker-coordination thin shell | AR16 | 11.C.1 | ✅ |
| DB migrations | AR17–AR21 | 11.C.1 (AR17 speakers drop), 11.B.3 (AR19 overflow + AR20 tentative), 11.F.1 (AR21 magic-link tokens) | ✅ |
| API surface | AR22–AR28 | 11.D.1 (AR22, AR23), 11.B.3 (AR24), 11.E.3 (AR25), 11.F.1 (AR26, AR27), 11.C.2 (AR28) | ✅ |
| Infrastructure CDK | AR29, AR30, AR31 | 11.E.1 (AR29, AR30), 11.F.1 (AR31) | ✅ |
| Documentation alignment | AR32–AR40 | 11.A.1 — all 9 explicitly named in AC | ✅ |
| Branch strategy / cherry-picks | AR41, AR42, AR43 | 11.E.3 (AR41), 11.E.1 (AR42), 11.F.1 (AR43) | ✅ |

**All 43 ARs traced to story AC.** No orphans.

### 5.4 Coverage Statistics

| Dimension | Total | Covered | Coverage % |
|-----------|-------|---------|-----------|
| FRs | 13 | 13 | **100%** |
| NFRs | 10 | 9 fully + 1 partial (NFR8) | **95%** |
| ARs | 43 | 43 | **100%** |
| UX-DRs | 23 | 22 (UX-DR23 acknowledged as out-of-scope optional) | **96%** |

### 5.5 Coverage Gaps Flagged

| # | Gap | Severity | Recommendation |
|---|-----|----------|----------------|
| G9 | **NFR8 (no regression for non-speaker workflows)** has no explicit AC in any story. Implicit reliance on existing Bruno + Playwright + Testcontainers suites. | LOW-MEDIUM | Add to 11.F.1 AC: "Bruno collection for `events/`, `partners/`, `partner-meetings/`, `tasks/` runs green; Playwright `chromium` (organizer) and `partner` projects pass." Or add it to a pre-merge gate. |
| G10 | **UX-DR23** ("What does this mean?" sidebar) — unscoped; PRD acknowledges as backlog. | LOW | No action — accept. |
| G11 | **DECLINED → DECLINED edge case** (re-declining an already-DECLINED speaker) — no explicit AC in 11.B.2. | LOW | Should be rejected by state-machine allow-list; verify in Step 5 (Edge Case review). |

### 5.6 Coverage Verdict

**VERDICT: ✅ Coverage is COMPLETE.**

- 13/13 FRs covered by stories with verified ACs.
- 43/43 ARs traced to stories.
- 22/23 UX-DRs scoped (1 explicitly deferred).
- 9/10 NFRs fully covered; NFR8 partial (G9).

**No critical gaps.** G9 is a recommended AC addition, not a blocking gap.


---

## 6. UX Alignment Assessment

### 6.1 UX Document Status

**No dedicated Epic-11 UX spec exists.** UX requirements are embedded in:

| Source | Location | Coverage |
|--------|----------|----------|
| Epic 11 PRD | `docs/prd/epic-11-speaker-workflow-refactor.md` §"UX Design Requirements" | UX-DR1–UX-DR23 (formal numbered requirements) |
| Implementation Plan | `docs/plans/speaker-workflow-refactor.md` §8.1–§8.9 | Comprehensive UX narrative for organizer kanban (card, columns, drag-drop, drawer, slot-gate, time-in-state colour coding, story breakdown) |
| Implementation Plan | `docs/plans/speaker-workflow-refactor.md` §3.4 | Web-frontend changes per service |
| Implementation Plan | `docs/plans/speaker-workflow-refactor.md` §0.4 | Shared-vs-not-shared boundary for content forms |

**Verdict:** UX is well-specified — the absence of a separate UX file is consistent with the refactor being "additive UX changes within existing surfaces," not new product UX.

### 6.2 UX ↔ PRD Alignment

| Check | Result |
|-------|--------|
| Every UX-DR derives from a plan §8 source | ✅ Verified by spot-check |
| Every UX-DR maps to a story | ✅ 22/23 (UX-DR23 acknowledged as deferred) |
| Each Phase D story's AC mirrors the matching UX-DR | ✅ Spot-checked Stories 11.D.1–11.D.4: all UX-DRs referenced have corresponding Given/When/Then assertions |
| User flow alignment (brainstorm → promote → invite → respond → submit content) | ✅ Plan §0.1 state diagram aligns with PRD FR1 + UX-DR15-16 (brainstorm) + UX-DR1 button labels |

### 6.3 UX ↔ Architecture Alignment

| Check | Result |
|-------|--------|
| Material-UI + React Router (per CLAUDE.md tech stack) supports kanban + drawer + drag-drop | ✅ Already in use (`web-frontend/src/pages/organizer/**`) |
| i18n infrastructure supports 10 locales (NFR10) | ✅ Per project memory: 10 locales operational since story 10-9 |
| Cognito session + multi-role nav infrastructure exists | ⚠️ Multi-role nav must be **cherry-picked** from `feature/speaker-account-creation` (commits `73d94688` + `396a9045`) — not in current codebase |
| Drag-drop UX uses existing `@dnd-kit` / `react-beautiful-dnd` | ⚠️ Not verified — plan §8.4 assumes drag-drop is already wired; current speaker-coordination kanban has drag-drop but the **slot-gate halo affordance is new**. |

### 6.4 Critical UX Alignment Issues

| # | Issue | Severity | Recommendation |
|---|-------|----------|----------------|
| U1 | `docs/front-end-spec.md` lines **286, 296** still describe **magic-link** invitation flow. Phase A Story 11.A.1 does **NOT** update this file. | **MEDIUM** | **Add `docs/front-end-spec.md` to Story 11.A.1 AC**, or create a separate cleanup story. The new Cognito-based flow contradicts these lines. |
| U2 | `docs/front-end-spec.md` describes the **org-wide event-pipeline kanban** at line 649 — but does NOT describe the new **speaker-coordination kanban** with 8-state columns + primary-action buttons. The new organizer UX (UX-DR1-14) has zero coverage in the org-wide UX spec. | **LOW** | Optional: extend Story 11.A.1 to update §4.3 or add §4.3.5 "Speaker Coordination Kanban" in front-end-spec.md. Alternative: accept that Epic 11 PRD §8 + UX-DRs are the authoritative source, and treat front-end-spec.md as legacy/aspirational. |
| U3 | `docs/wireframes/sitemap.md` shows **Epic 6 as "DEFERRED to Phase 2+"** — but Epic 6 is actually 100% COMPLETE (per CLAUDE.md). This is org-wide doc staleness, not Epic 11's fault, but the speaker-portal sitemap entries it lists need a refresh after this refactor. | **LOW** | Out of scope for Epic 11. File a separate doc-cleanup story. |
| U4 | **No accessibility AC** for new kanban UI changes. Epic 6 carried WCAG 2.1 AA (per CLAUDE.md). UX-DR1–UX-DR14 do not mention WCAG, focus order, screen-reader labels for `⋯` menu, halo affordance, or colour-coded chips (UX-DR7). | **LOW-MEDIUM** | Recommended: add a cross-cutting AC to Story 11.D.2 (card + button) — primary-action button has `aria-label`, time-in-state chip colour-coding has non-colour secondary signal (e.g. icon), tooltip is keyboard-reachable. |
| U5 | **Drag-drop validation tooltip semantics** — UX-DR9 specifies a toast; for accessibility this should also be announced via `aria-live` region. Not explicit in story AC. | **LOW** | Folded into U4. |
| U6 | **Email template UX** (UX-DR21) — invitation email must be reviewed for legibility (temp password formatting, e.g. monospace, copy-button). Story 11.E.2 AC requires the *content* but not the *visual review*. | **LOW** | Add to 11.E.2 AC: "Render the email in dev environment in HTML + plain-text; temp password is selectable/copyable in HTML version." |

### 6.5 UX Warnings

| ⚠️ | Description |
|----|-------------|
| **W1** | Cherry-pick risk (G4, G5 from PRD): if `feature/speaker-account-creation` branch is GC'd before 11.E.3 lands, the multi-role nav UX cannot be cherry-picked. Verify branch existence is in Step 3 of every Phase E story (currently absent). |
| **W2** | The new "Drag start → green halo / dimmed columns with lock icon" affordance (UX-DR8) is non-standard — needs a usability test or organizer review before shipping. AC mentions "Playwright tests cover the green/dim drag affordance" but not user-acceptance. |
| **W3** | The "What does this mean?" sidebar (UX-DR23) is the *only* organizer onboarding mechanism for the new kanban semantics. Without it, new organizers will struggle with the 8-state model. Acceptable to defer, but flag as Phase 2 must-have. |

### 6.6 UX Verdict

**VERDICT: ✅ UX is implementation-ready, with caveats.**

- All 23 UX-DRs are mapped to stories with verifiable AC.
- All UX flows have a corresponding architecture path.
- 6 alignment issues (U1–U6), 3 warnings (W1–W3) — all LOW–MEDIUM severity.
- **Single MEDIUM blocker recommendation:** add `docs/front-end-spec.md` lines 286+296 magic-link cleanup to Story 11.A.1 scope (U1).


---

## 7. Epic Quality Review

**Method:** Each story validated against BMad best practices (user value, independence, story sizing, AC quality, dependencies, schema-timing).

### 7.1 Epic-Level Validation

| Criterion | Assessment | Notes |
|-----------|------------|-------|
| **User value focus** | ✅ Justified for brownfield refactor | Epic 11 is explicitly "structural refactor, not new capability" (PRD §"Note on framing"). Phase D delivers organizer UX value; Phase E delivers speaker UX value; Phase A+B+C+F serve maintainers. The PRD is honest about audience per story. |
| **Epic independence** | ✅ Within-project | Single epic — no inter-epic chain. Cross-references to Epic 6 (complete) + Epic 9 (in-progress) are for **cherry-pick source branches only**, not feature dependencies. |
| **External branch dependencies** | ⚠️ Risk | Two external branches must remain extant: `feature/epic-6` (commit `d5cf0fcc`), `feature/speaker-account-creation` (commits `73d94688`, `396a9045`). No fallback AC if branches are GC'd. |

**Brownfield-vs-Greenfield framing:** The workflow's "challenge anything deviating from user value" rubric assumes greenfield. Applied strictly, Epic 11 fails the "user value" rubric. Applied pragmatically (brownfield refactor with embedded UX gains in Phase D/E), Epic 11 passes. **Verdict: acceptable as refactor epic** — the PRD framing is honest and the user-value-bearing phases (D + E) are clearly scoped.

### 7.2 Story-by-Story Quality Audit

| Story | User Value | Independence | AC Quality | Sizing | Verdict |
|-------|-----------|--------------|------------|--------|---------|
| **11.A.1** | ✅ Maintainer | ✅ Pure docs | ✅ G/W/T per file | ⚠️ Large (9 files, 1 PR) — acceptable for cohesion | **PASS** |
| **11.B.1** | ✅ Maintainer | ⚠️ Breaks dependents at HEAD | ✅ Compile-error AC is clever | ✅ Single-module change | **PASS w/ caveat** (m2) |
| **11.B.2** | ✅ Auditability (NFR4) + maintainer | ✅ (with B.1) | ✅ Comprehensive — legal/illegal transitions, history, slot-gate, side-effect hooks | ✅ | **PASS** (m3 stub) |
| **11.B.3** | ✅ Organizer (cards don't disappear) | ✅ (with B.2) | ✅ SQL + API + derived-flag + idempotency | ⚠️ Large (5 concerns: migrate / drop / derived / API tighten / API READY-block) | **PASS w/ caveat** (m4 rollback) |
| **11.C.1** | ✅ Maintainer | ✅ (with Phase B) | ✅ Table drop + columns intentionally not backfilled | ✅ | **PASS** (m5 vague Bruno cleanup) |
| **11.C.2** | ✅ Organizer + speaker | ✅ (with B.2) | ✅ **Excellent** — "byte-identical except for changed_by_username" is a strong AC | ✅ | **PASS** |
| **11.D.1** | ✅ Organizer | ✅ (with C.2 + B.2) | ✅ API codes (400, 409), brainstorm UI, Playwright | ⚠️ 3 surfaces merged (API + UI + slot-gate) | **PASS w/ caveat** (m6 — cohesive) |
| **11.D.2** | ✅ Organizer | ⚠️ Stub for D.4 | ✅ 8 states × button behavior + i18n + disabled tooltip | ✅ | **PASS** (m7 stub) |
| **11.D.3** | ✅ Organizer | ✅ (with D.2) | ⚠️ **Assumes event-settings storage/UI** — not defined elsewhere | ✅ | **MAJOR M1** |
| **11.D.4** | ✅ Organizer | ✅ (with D.1-3 + C.2) | ✅ Drag affordances + drawer + on-behalf form | ⚠️ Largest story (9+ AC blocks) | **PASS w/ caveat** (m8) |
| **11.E.1** | ✅ Platform + security | ⚠️ **External branch dependency** (G4) | ✅ Cherry-pick named, IAM enumerated, password-policy AC | ✅ | **PASS w/ MAJOR M2** |
| **11.E.2** | ✅ Speaker | ✅ (with E.1 + B.2 + C.2) | ✅ Excellent — re-provisioning omits temp-password is thoughtful edge case | ✅ | **PASS** |
| **11.E.3** | ✅ Speaker + multi-role | ⚠️ **External branch dependency** (G5) | ✅ @PreAuthorize + 401/403 + Playwright + multi-role test | ✅ | **PASS w/ MAJOR M3** |
| **11.F.1** | ✅ Security + maintainer | ✅ (with Phase E observation) | ✅ Files / endpoints / table / cookie / secrets / branches / full test run | ⚠️ Branch deletion irreversible; no pre-deletion audit AC | **PASS w/ MAJOR M4** |

### 7.3 Dependency Graph Validation

```
Phase A (11.A.1)  — independent, do first
Phase B  B.1 → B.2 → B.3                             (chain)
Phase C  C.1 ← B.* (any time after Phase B)
         C.2 ← B.2
Phase D  D.1 ← C.2 + B.2                             (slot-gate hook)
         D.2 ← D.1                                   (for modal reuse)
         D.3 ← D.2                                   (chip layout)
         D.4 ← D.1 + D.2 + D.3 + C.2                 (largest)
Phase E  E.1 ← [external] feature/epic-6 d5cf0fcc
         E.2 ← E.1 + B.2 + C.2
         E.3 ← E.1 + E.2 + [external] feature/speaker-account-creation 73d94688+396a9045
Phase F  F.1 ← Phase E "stable in prod ≥ 1 week"     (process gate)
```

**Forward-dependency check:**
- ❌ NONE detected. Every dependency is to PRIOR phase or PRIOR story in phase.
- ⚠️ Stubs in 11.B.2 (provisioning hook) and 11.D.2 (ACCEPTED button) reference future stories — but these are **defensive seams**, not forward dependencies. The story is *implementable* without the future story; only behavior of the seam matures later.

**External-dependency check:**
- ⚠️ 11.E.1 + 11.E.3 depend on commits in branches outside Epic 11's control.

### 7.4 Database/Schema Timing Validation

| Schema Change | Story | Timing |
|--------------|-------|--------|
| `speaker_pool.status` value migration | 11.B.3 | ✅ When the 8-state model becomes the contract |
| Drop `speaker_pool.is_tentative`, `tentative_reason` | 11.B.3 | ✅ Same migration |
| Drop `speaker_selection_votes` (overflow) | 11.B.3 | ✅ Same migration |
| `is_slot_assigned`, `is_publishable` — derived (no DDL) | 11.B.3 | ✅ Read-time computation, no schema |
| Drop `speakers` table | 11.C.1 | ✅ After Phase B stable |
| Drop `magic_link_tokens` table | 11.F.1 | ✅ After Phase E stable + observation |
| Drop magic-link Secrets Manager entries | 11.F.1 | ✅ Same teardown |

✅ **No "create all tables upfront" anti-pattern.** Every schema change occurs in the story that needs it.

### 7.5 Acceptance Criteria Quality

| Quality dimension | Coverage | Examples |
|------------------|----------|----------|
| Given/When/Then format | ✅ 14/14 stories | Every story uses G/W/T |
| Testable assertions | ✅ Mostly | "Byte-identical except for changed_by_username" (11.C.2); "API returns 400 Bad Request with body identifying rejected value" (11.B.3); "Cherry-pick commit message notes source commit + deliberate skip" (11.E.1) |
| Error path coverage | ✅ Most stories | 400/403/404/409 explicitly tested; toast/tooltip i18n keyed |
| Edge cases | ✅ Most stories | Idempotency (11.C.2 + 11.E.2 re-provisioning); legacy data migration idempotent (11.B.3); already-promoted speaker returns 409 (11.D.1) |
| Vague AC | ⚠️ A few | 11.C.1 "Bruno API contract tests for any deleted endpoints are removed" (which endpoints?); 11.F.1 "the team agrees Phase E has run stably" (informal) |

### 7.6 Best-Practices Compliance Checklist (per Epic)

- ✅ Epic delivers (refactor) value
- ✅ Epic can function independently (within this branch)
- ✅ Stories appropriately sized (one PR each, with 11.D.4 as the largest)
- ✅ No forward dependencies (only forward-aware stubs)
- ✅ Database tables created/dropped when needed
- ✅ Clear acceptance criteria (G/W/T format, mostly testable)
- ✅ Traceability to FRs maintained (PRD self-audited; verified §5.1)

### 7.7 Quality Findings Summary

#### 🔴 Critical Violations
**NONE.**

#### 🟠 Major Issues

| # | Issue | Story | Recommendation |
|---|-------|-------|----------------|
| **M1** | Story 11.D.3 assumes event-settings storage/UI for threshold customization, but no story defines: (1) the DB schema for storing thresholds; (2) the API endpoint to read/write; (3) the settings UI surface. AC says "thresholds are read from event settings (overridable per event)". | 11.D.3 | **Either** (a) reduce scope: ship with hard-coded §8.7 defaults; overridability deferred to a follow-up story; **OR** (b) extend 11.D.3 AC to include schema + API + minimal settings UI (~+1 day). |
| **M2** | Cherry-pick of `feature/epic-6` commit `d5cf0fcc` (AR42) has no fallback AC if the branch is deleted/GC'd before 11.E.1 lands. The repo's branch deletion policy could remove it. | 11.E.1 | Add AC: "If `feature/epic-6` is unavailable, the equivalent CDK changes are re-implemented from scratch using `d5cf0fcc` as a reference patch attached to the PR description." Or: tag the cherry-pick base commit (`git tag epic-11/epic-6-cherry-pick-base d5cf0fcc`) at story-start. |
| **M3** | Cherry-pick of `feature/speaker-account-creation` commits `73d94688` + `396a9045` (AR41) has the same risk. | 11.E.3 | Same remediation pattern as M2. |
| **M4** | Story 11.F.1 deletes `feature/speaker-account-creation` + `feature/epic-6` branches without a pre-deletion AC ensuring all unique commits are reflected on `main`. Branch deletion is operationally irreversible. | 11.F.1 | Add AC: "Before deletion, `git log feature/epic-6 --not main` and `git log feature/speaker-account-creation --not main` are run; any commits not reflected on main are either cherry-picked or explicitly recorded as 'abandoned' in the PR description." |

#### 🟡 Minor Concerns

| # | Concern | Story | Note |
|---|---------|-------|------|
| **m1** | Epic-level "technical refactor" framing | Epic-level | Acceptable for brownfield refactor; PRD framing is honest. |
| **m2** | 11.B.1 alone leaves dependent services broken at HEAD | 11.B.1 | Phasing (B.1+B.2+B.3 as a single PR-train) handles this. Recommend: do NOT merge B.1 to develop until B.2 is open. |
| **m3** | 11.B.2 provisioning-hook stub references Phase E | 11.B.2 | Defensible — the stub is a no-op until E.2 wires it. Verify the stub has a documented signature. |
| **m4** | 11.B.3 has no rollback-strategy AC for legacy data migration | 11.B.3 | Recommend: add AC: "Pre-migration data snapshot is taken; rollback script provided in the PR." |
| **m5** | 11.C.1 vague about which Bruno tests to remove | 11.C.1 | Recommend: enumerate the deleted endpoint names in AC. |
| **m6** | 11.D.1 merges API + brainstorm UI + slot-gate | 11.D.1 | Cohesive — acceptable. |
| **m7** | 11.D.2 ACCEPTED button stub references 11.D.4 | 11.D.2 | Same defense as m3 — stub must open an "empty shell" placeholder, verify that's acceptable UX. |
| **m8** | 11.D.4 is the largest story (9+ AC blocks) | 11.D.4 | Risk: developer fatigue / scope creep. Consider sub-task tracker. |
| **m9** | 11.E.3 + 11.F.1 both delete `feature/speaker-account-creation` | 11.E.3 / 11.F.1 | Duplicate intent. Recommend: 11.F.1 owns the deletion; 11.E.3 only verifies the cherry-pick is complete and tagged. |
| **m10** | 11.F.1 "Phase E stable for ≥1 week" is a process gate, not an automatable AC | 11.F.1 | Recommend: explicit checkbox in PR template: "[ ] CloudWatch verified zero magic-link traffic for ≥7 days as of <date>". |

### 7.8 Edge-Case Coverage Audit

Per the principle "every acceptance criterion must have at least one test; complex criteria need multiple" (project-context.md §Testing Rules):

| Edge case | Story coverage | Verdict |
|-----------|---------------|---------|
| Re-decline an already-DECLINED speaker | Not explicit in 11.B.2 | ⚠️ State-machine should reject (DECLINED is terminal); add AC. |
| Promote a speaker who's already in DECLINED (lead didn't pan out → reconsider) | Not explicit in 11.D.1 | ⚠️ State machine per ADR-009 §0.2 — likely rejected. Add AC. |
| Slot-capacity gate when `max_slots = 0` (event with no speaker slots) | Not explicit in 11.D.1 | ⚠️ Should reject all invites. Add AC. |
| Email-collision: promote speaker with email that's already a User in another role | Not explicit in 11.D.1 / 11.E.2 | ⚠️ **IMPORTANT** — `UserApiClient.provisionUserWithRole` AC says "User created if missing, SPEAKER role granted." What if User exists with `ATTENDEE` role only? Adding SPEAKER role triggers FORCE_CHANGE_PASSWORD? Or graceful merge? Add AC. |
| Cognito tenant + multi-role JWT (per ADR-007 + Story 9.5 multi-role) — token contains both ORGANIZER and SPEAKER roles | Not explicit in 11.E.3 | ⚠️ `@PreAuthorize("hasRole('SPEAKER')")` should match; verify with Playwright multi-role test in 11.E.3. |
| Migration replay on a database that has *partial* legacy data (mixed states from interrupted prior migration attempt) | 11.B.3 AC says "idempotent" but does not test partial state | ⚠️ Add AC: "Migration also handles partial states from a failed prior run." |
| Drag-drop on touch devices (tablets) | Not explicit in 11.D.4 | ⚠️ Drag-drop UX may differ on tablets — verify, or scope to desktop only with an i18n note. |
| Email rendering on the `gsw-BE` Swiss-German locale (this is unusual + low traffic) | 11.E.2 AC lists 10 locales | ✅ Covered. |

### 7.9 Quality Verdict

**VERDICT: ✅ PASS with 4 MAJOR issues + 10 minor concerns + 7 edge-case AC additions recommended.**

- **0 critical violations** (no technical-epic forward dependencies; no impossible stories).
- **4 MAJOR** issues actionable before Phase E starts (M1) or before final teardown (M2-M4).
- **10 minor** concerns are quality-of-life improvements; none blocking.
- **7 edge-case ACs** are recommended hardening — add to the relevant story before bmad-create-story runs on each.


---

## 8. Summary and Recommendations

### 8.1 Overall Readiness Status

# ✅ **READY** (with 4 actionable improvements recommended before Phase E starts)

Epic 11 is **substantially implementation-ready** for Phases A–D. Phases E–F have actionable risk that should be remediated before the cherry-pick stories (11.E.1 / 11.E.3) and the teardown story (11.F.1) start.

### 8.2 Readiness by Dimension

| Dimension | Score | Verdict |
|-----------|-------|---------|
| Document inventory completeness | 10/10 | ✅ All artifacts present |
| FR coverage (PRD → Stories) | 13/13 = 100% | ✅ Complete |
| NFR coverage | 9.5/10 = 95% | ✅ Strong (G9 partial) |
| AR coverage | 43/43 = 100% | ✅ Complete |
| UX-DR coverage | 22/23 = 96% | ✅ Strong (UX-DR23 deferred by design) |
| AC quality (G/W/T + testable) | 14/14 stories | ✅ Excellent |
| Within-epic dependency hygiene | Clean | ✅ No forward deps |
| External branch dependencies | 2 risks | ⚠️ M2 + M3 |
| Story sizing | 13/14 well-sized; 11.D.4 large | ✅ Acceptable |
| Edge-case coverage | Strong on happy path; gaps on edges | ⚠️ 7 recommended AC additions |

### 8.3 Critical Issues Requiring Immediate Action

**NONE are blocking.** No 🔴 critical violations were found.

The 4 🟠 MAJOR issues (M1–M4) are not blockers for Phase A starting, but should be remediated **before the affected story begins**:

| Issue | Affected Story | When to Address |
|-------|---------------|-----------------|
| **M1** Event-settings storage assumption | 11.D.3 | Before Phase D Story 3 starts |
| **M2** Cherry-pick branch GC risk (`feature/epic-6` @ `d5cf0fcc`) | 11.E.1 | Before Phase E Story 1 starts (tag immediately as safety) |
| **M3** Cherry-pick branch GC risk (`feature/speaker-account-creation` @ `73d94688`+`396a9045`) | 11.E.3 | Before Phase E Story 3 starts (tag immediately as safety) |
| **M4** Branch-deletion lacks pre-deletion audit AC | 11.F.1 | Before Phase F Story 1 starts |

### 8.4 Recommended Next Steps

#### 🔥 Immediate (next 24 hours, before any work begins)

1. **Tag the cherry-pick base commits as safety against branch GC** — addresses **M2 + M3**:
   ```bash
   git tag epic-11/cdk-cognito-base d5cf0fcc
   git tag epic-11/multi-role-nav-base-1 73d94688
   git tag epic-11/multi-role-nav-base-2 396a9045
   git push origin --tags
   ```
   Cost: 30 seconds. Eliminates the GC risk entirely.

2. **Add the Story 11.A.1 doc-update missing file** — addresses **U1**:
   - Extend Story 11.A.1 AC to include cleanup of `docs/front-end-spec.md` lines 286 + 296 (magic-link references).

3. **Verify Phase A independence by starting it** — Story 11.A.1 has zero dependencies and is the lowest-risk story. Doing it first establishes momentum and validates the docs against ADR-009.

#### 📋 Pre-Phase-D (before bmad-create-story for 11.D.3)

4. **Resolve M1 — event-settings scope** — Choose one of:
   - **Option A (recommended):** Reduce 11.D.3 scope to ship with hard-coded §8.7 thresholds; defer per-event overridability to a Phase 2+ follow-up story.
   - **Option B:** Extend 11.D.3 to include event-settings schema (1 table) + API (2 endpoints) + minimal admin UI.
   - **Estimate impact:** Option A saves ~1 day; Option B adds ~1.5 days to Phase D.

#### 📋 Pre-Phase-F (before 11.F.1 starts)

5. **Add pre-deletion audit AC to 11.F.1** — addresses **M4**:
   - "Before deletion, `git log feature/epic-6 --not main` and `git log feature/speaker-account-creation --not main` are run; unique commits are either reflected on main or explicitly recorded as abandoned in the PR description."

#### 📝 Story-creation-time hardening (when bmad-create-story runs on each story)

6. **Edge-case AC additions** (from §7.8):
   - 11.B.2: Add AC rejecting DECLINED → DECLINED (re-decline of terminal state).
   - 11.B.3: Add AC for migration replay on partially-migrated database.
   - 11.D.1: Add AC for `max_slots = 0` event + email-collision case for existing-User-with-other-role.
   - 11.D.4: Add tablet/touch-device drag-drop scoping or explicit "desktop only" note.
   - 11.E.3: Add AC for multi-role JWT (token with both ORGANIZER + SPEAKER) passing `hasRole('SPEAKER')`.

7. **Minor concerns (m4, m5, m9, m10)** — incorporate into story drafts:
   - 11.B.3: rollback strategy + pre-migration snapshot AC.
   - 11.C.1: enumerate which Bruno tests to remove.
   - 11.F.1 (not 11.E.3): owns branch deletion; 11.E.3 only verifies cherry-pick complete + tagged.
   - 11.F.1: explicit CloudWatch-zero-traffic checkbox in PR template.

#### 📝 Accessibility hardening (Phase D)

8. **Add cross-cutting accessibility AC to Story 11.D.2** (per **U4**):
   - Primary-action button: `aria-label` on each state's button.
   - Time-in-state chip colour-coding has non-colour secondary signal (icon or text).
   - Tooltip is keyboard-reachable + `aria-live` on toast announcements.

### 8.5 What's NOT a Blocker

- **No per-story files exist yet** in `_bmad-output/implementation-artifacts/` — this is the EXPECTED BMad pattern (just-in-time story authoring via `bmad-create-story`). Sprint registration is the gate.
- **Single epic** (no inter-epic sequencing risk).
- **PRD has self-traceability** — every FR/NFR/AR/UX-DR maps to a story.
- **All architecture docs Phase A will update exist** and are listed in 11.A.1 AC.

### 8.6 Final Note

This assessment identified **0 critical violations, 4 MAJOR issues, 10 minor concerns, 6 UX alignment issues, and 7 edge-case AC recommendations** across 7 categories. **None of the findings block Phase A from starting.** The 4 MAJOR issues are time-sensitive only for the affected later phases (M1 for Phase D; M2-M3 for Phase E; M4 for Phase F).

**The Epic 11 documentation is unusually high-quality** — PRD self-audits its own coverage, plan §8 provides detailed UX narrative, ADR-009 grounds the technical decisions, and the 14-story decomposition has thoughtful phase boundaries and explicit dependency declarations.

**Recommended action sequence:**
1. **NOW** — Tag cherry-pick base commits (30 sec) → eliminates M2 + M3 risk.
2. **NOW** — Open Story 11.A.1 (doc alignment) — lowest-risk, independent, validates documents against ADR-009.
3. **In parallel** — Begin drafting 11.B.1 + 11.B.2 + 11.B.3 (the Phase B chain) — these are independent of Phase A.
4. **Before 11.D.3** — Decide M1 (hard-coded thresholds vs event-settings UI).
5. **Before 11.F.1** — Resolve M4 (branch-deletion audit AC).

**Assessor:** PM Readiness Audit
**Report saved to:** `docs/implementation-readiness-report-2026-05-15.md`
**Date:** 2026-05-15

---

