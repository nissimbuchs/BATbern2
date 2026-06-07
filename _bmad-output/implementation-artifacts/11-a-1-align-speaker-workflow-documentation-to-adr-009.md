# Story 11.A.1: Align speaker-workflow documentation to ADR-009

Status: done

<!-- Validation is optional — run validate-create-story for quality check before dev-story. -->

## Story

**As a** developer reviewing or extending speaker-workflow code,
**I want** every PRD, architecture doc, ADR cross-reference, and front-end spec to describe the unified 8-state Cognito-based model from ADR-009,
**So that** code reviews can verify code against a coherent target instead of three conflicting documents.

**Phase:** A (Documentation alignment) — must land BEFORE Phases B–F so subsequent code reviews have an aligned target.
**Dependencies:** None. Pure documentation work, no code touched.
**Scope:** This is a **doc-only PR**. The commit MUST contain `[no-doc]` in the message per the doc-drift policy (this PR *is* the doc update).

---

## Acceptance Criteria

The AC are pinned to ADR-009 §0 (target model), ADR-009 §1 (decision rewrites), and the plan's §1 documentation-changes table (`docs/plans/speaker-workflow-refactor.md` lines 212-228). Each file gets explicit Given/When/Then so the reviewer can verify coverage in a single pass.

### AC1 — `docs/prd-enhanced.md`

**Given** the refactor branch is checked out,
**When** I read `docs/prd-enhanced.md` lines 52-56 (the "Speaker Workflow (Parallel Per-Speaker Progression)" block),
**Then** it describes the 8-state model from ADR-009 §0.1 — `IDENTIFIED → CONTACTED → READY → INVITED → ACCEPTED → CONTENT_SUBMITTED → QUALITY_REVIEWED`, plus `DECLINED` reachable from every non-terminal state,
**And** no reference to `CONFIRMED`, `SLOT_ASSIGNED`, `OVERFLOW`, `WITHDREW`, or `TENTATIVE` remains anywhere in the file,
**And** lines 53-56 (the parallel-quality / slot-CONFIRMED auto-confirmation discussion) is removed.

**Given** I read FR3 (line 75),
**Then** the wording no longer mentions "magic link"; it describes Cognito with FORCE_CHANGE_PASSWORD per ADR-009 §0.5.

**Given** I read FR17 (line 101),
**Then** the state-list in parentheses is the 8-state model from ADR-009 §0.1,
**And** "overflow management with organizer voting mechanisms and automatic promotion from overflow when slots become available" is rewritten to describe the **slot-capacity gate at invitation** per ADR-009 §0.2 (no overflow; no voting; `READY → INVITED` is blocked when `(count(ACCEPTED) + count(INVITED)) >= max_slots`).

**Given** I read line 209 (Epic 9 phase line) and line 403 (Automated speaker invitation system bullet),
**Then** "magic link authentication" no longer appears.

### AC2 — `docs/prd/epic-9-speaker-authentication.md`

**Given** the file is rewritten in place per plan §9.3,
**When** I read the top of the file,
**Then** a banner `> **Supersedes prior Epic 9 plan per ADR-009.**` appears before any other content,
**And** the Status line (current line 3 — "IN PROGRESS — Story 9.1 complete; Stories 9.2–9.5 planned") is replaced with the new Epic 11-aligned status (in-progress, see Epic 11 stories 11.E.1–11.F.1).

**Given** I read the body,
**Then** the old Story 9.1–9.5 sections (current lines 95-247) are **removed**,
**And** the file's new scope describes the four work blocks Epic 11 now owns:
  1. Speaker Cognito provisioning at `CONTACTED → READY` (Epic 11 Story 11.E.2)
  2. Cognito-secured speaker portal (Epic 11 Story 11.E.3)
  3. Multi-role navigation cherry-pick (Epic 11 Story 11.E.3)
  4. Magic-link teardown (Epic 11 Story 11.F.1)
**And** every reference to `MagicLinkService`, `speaker_tokens`, `?token=`, JWT-based magic-link auth, dual-auth, and "Story 9.X" is removed.

**Given** the Epic Goal at the top of the file,
**Then** it is preserved (unified Cognito identity for speakers who are also attendees, zero duplicate accounts) — only the implementation stories are obsolete.

### AC3 — `docs/architecture/03-data-architecture.md`

**Given** I read the file,
**Then** the entire `Speaker` entity section (current lines 306-451, including Fields, TypeScript interface, `enum SpeakerWorkflowState`, `enum SpeakerAvailability`, Relationships, Response example, `SpeakerService` Java snippet) is **deleted**,
**And** the `speakers` table SQL block in the "Speaker Coordination Service Database Schema" subsection is **deleted**,
**And** the `User` section (around lines 187-289) gains a new paragraph explaining:
  - `SPEAKER` is a role stored in `role_assignments.role = 'SPEAKER'` (NOT a separate entity)
  - The two user-level attributes BATbern needs for a speaker — short CV (`bio`) and portrait (`profile_picture_url`) — are already existing fields on `user_profiles` per ADR-004
  - No schema extension is required; legacy speaker-only attributes (`availability`, `expertise_areas`, `speaking_topics`, `languages_spoken`, `certifications`, `linkedin_url`, `twitter_handle`, `speaking_history`, `communication_preferences`) are dropped per ADR-009 §0.3
**And** the bullet at line 289 (`Speaker Service: Associates speakers with user accounts`) is removed or rewritten to reflect that there is no Speaker Service entity.

### AC4 — `docs/architecture/06a-workflow-state-machines.md`

**Given** I read the "Speaker Workflow Management (Per Speaker - Parallel)" section (current lines 189+),
**Then** it is rewritten with:
  - The 8 states from ADR-009 §0.1 with descriptions matching the table in §0.1.
  - A new transition diagram (replacing the current one) showing all valid transitions per ADR-009 §0.2 — specifically: every non-terminal state can transition to `DECLINED`; `CONTACTED → READY` is the provisioning gate; `READY → INVITED` has the slot-capacity precondition; `INVITED → ACCEPTED|DECLINED`; `ACCEPTED → CONTENT_SUBMITTED`; `CONTENT_SUBMITTED → QUALITY_REVIEWED` (terminal happy state).
  - Explicit semantics for `CONTACTED` (still brainstorming — no User, no email) and `READY` (real speaker chosen — User + Cognito provisioned).
  - A new "Derived flags" subsection documenting `is_slot_assigned := session.start_time IS NOT NULL` and `is_publishable := quality_reviewed AND is_slot_assigned` — both computed at read time, no persisted column.
  - Documentation that `EventWorkflowStateMachine.validateAllSpeakersConfirmed` (currently around lines 172-182) uses the derived `is_publishable` predicate over all accepted speakers as the gate for `AGENDA_PUBLISHED`.

**Given** the rewritten section,
**Then** the parallel-quality / slot-CONFIRMED auto-confirmation discussion (current lines 215-246 around the `confirmed` state with `Auto-confirmed when quality_reviewed AND session.startTime exists`) is **deleted**,
**And** the `SLOT_ASSIGNED` enum-rejection note (anywhere it appears) is removed — the value is gone entirely from the enum,
**And** the `withdrew` state row in the state table (current line 224) is **deleted** (collapsed into `DECLINED` per ADR-009 §0.7),
**And** the `confirmed` state row (current line 222) is **deleted**,
**And** the `SpeakerWorkflowService.updateSpeakerWorkflowState` example (current lines 251+) is replaced with a snippet matching ADR-009 §"`SpeakerWorkflowService.transition()` skeleton" (lines 406-481 of ADR-009): single `transition(speakerId, toState, actor, payload)` entry point + side-effect hook list.

### AC5 — `docs/architecture/04-api-design.md`

**Given** I read the file,
**Then** the speaker-portal API description in §3 ("Speaker Coordination API") uses Cognito Bearer auth — no `?token=` query parameters anywhere,
**And** `POST /api/v1/auth/speaker-magic-login` and `POST /api/v1/speaker-portal/validate-token` are removed from any endpoint list,
**And** a new endpoint is documented: `POST /api/v1/events/{eventCode}/speakers/{speakerId}/promote` with payload `{ email, firstName?, lastName? }`, behavior "promotes speaker from `CONTACTED` to `READY`, provisions Cognito user + SPEAKER role + persists `username`," response 200 OK (already promoted → 409 Conflict; no email → 400 Bad Request).

**Note for dev:** Speaker-portal endpoints are documented in detail in `docs/architecture/04-api-speaker-coordination.md` (the linked sub-doc). Update that file in the same PR — see AC9 below.

### AC6 — `docs/architecture/06-backend-architecture.md`

**Given** I read the "Authentication and Authorization" section (current lines 33+),
**Then** the auth section reflects a single Cognito flow for all roles (no dual-auth model — no speaker-JWT, no magic-link, no `?token=` query handling),
**And** the "Public Endpoints" subsection (currently around line 61) is updated so that `/api/v1/speaker-portal/**` is **removed** from any public/`permitAll` list — those endpoints are `@PreAuthorize("hasRole('SPEAKER')")`,
**And** the new auth section describes: `CONTACTED → READY` provisions a Cognito user with `FORCE_CHANGE_PASSWORD`; SPEAKER role granted in `role_assignments`; first login uses temporary password from invitation email; subsequent logins are standard Cognito.

### AC7 — `docs/architecture/06b-user-lifecycle-sync.md`

**Given** I read the file,
**Then** a new section is added (after "Pattern 1: PostConfirmation Lambda - User Creation") titled "Pattern N: Speaker Provisioning at CONTACTED → READY,"
**And** it documents the new lifecycle: at the `CONTACTED → READY` transition, the backend calls `UserApiClient.provisionUserWithRole(username, email, firstName, lastName, SPEAKER)`. The company-user-management-service then performs (in this order): (a) `AdminCreateUser` in Cognito with `FORCE_CHANGE_PASSWORD` + a strong random temp password; (b) `AdminAddUserToGroup` (or equivalent role-grant via `role_assignments`); (c) `INSERT INTO user_profiles` (idempotent on existing user); (d) return `{ username, temporaryPassword }` to the caller. The temp password is embedded in the invitation email and immediately discarded from memory — never persisted in any local database or log.
**And** the section notes idempotency: re-calling `provisionUserWithRole` for an already-provisioned user is a no-op and returns `{ username, temporaryPassword: null }` so the caller knows not to re-send a credential.
**And** the existing PostConfirmation Lambda + PreTokenGeneration Lambda sections are preserved unchanged — they remain the standard self-registration path (Story 1.2.3).

### AC8 — `docs/architecture/ADR-004-factor-user-fields-from-domain-entities.md`

**Given** I read the file (currently 803 lines),
**When** I scroll to the "Revision History" section near the bottom,
**Then** a new revision entry is added with date `2026-05-15` (or the actual PR-merge date) noting:
  > "Per ADR-009 (Unified Speaker Workflow): SPEAKER role does not require additional User columns. `User.bio` serves as short CV and `User.profile_picture_url` serves as speaker portrait. The `speakers` table is deleted; `user_profiles` is not extended. See ADR-009 §0.3."

**And** in the "Domain Entity Design Patterns" section (around line 282), a short note is appended:
  > **Speaker (per ADR-009):** SPEAKER is a role on User (`role_assignments.role = 'SPEAKER'`), not a domain entity. The `Speaker` table has been deleted. Per-event speaker data lives on `speaker_pool` (cross-service reference via `username` per ADR-003); content lives on `content_submissions`.

### AC9 — `docs/architecture/04-api-speaker-coordination.md`

**Given** I read the file,
**Then** any speaker-portal endpoint documentation that uses `?token=` query auth is updated to Cognito Bearer auth,
**And** the `POST /api/v1/auth/speaker-magic-login` and `POST /api/v1/speaker-portal/validate-token` endpoints are removed from this file,
**And** the new `POST /api/v1/events/{eventCode}/speakers/{speakerId}/promote` endpoint is documented in full (path params, request body, response body, error codes 400/404/409, auth required: ORGANIZER role),
**And** the `PUT /api/v1/events/{eventCode}/speakers/{speakerId}/status` endpoint documentation is updated to reflect the 8-state allow-list — `SLOT_ASSIGNED`, `CONFIRMED`, `OVERFLOW`, `WITHDREW`, `TENTATIVE` are explicitly listed as rejected values returning 400, and `READY` is rejected with a body pointing to the new promote endpoint.

> **Note**: This file is the speaker-coordination API sub-doc; the parent index is `04-api-design.md` (AC5). Both must be updated for internal consistency.

### AC10 — `docs/architecture/index.md`

**Given** I read the index file,
**Then** every section reference is verified to point to a file that still exists,
**And** if any reference points to deleted content (e.g., the `Speaker` entity section in 03-data-architecture.md), the index entry is updated to reflect the new section name or removed,
**And** a new entry is added to "Key Architectural Decisions" pointing to ADR-009 (Unified Speaker Workflow).

### AC11 — `CLAUDE.md` (repo root)

**Given** I read the file's MVP Status section (current lines 7-23),
**Then** the Epic 9 line (currently `🔨 Epic 9: Speaker Authentication & Account Integration - IN PROGRESS (Story 9.1 JWT magic link done; 9.2-9.5 planned)`) is replaced with the Epic 11 alignment:
  > `🔨 Epic 11: Unified Speaker Workflow Refactor - IN PROGRESS (Phase A doc alignment landing; Phases B–F per docs/plans/speaker-workflow-refactor.md and ADR-009)`
**And** the line at 75 (`Epic 9: Stories 9.2-9.5 next (Cognito account creation, dual auth, migration, multi-role nav)`) is replaced with:
  > `Epic 11: Phases B–F (state-machine consolidation, entity simplification, organizer kanban UX, Cognito provisioning, magic-link teardown)`
**And** the line at 27 (`Complete event workflow (9-state machine + speaker coordination + task system)`) is reviewed — the **EVENT** workflow has 9 states (unchanged); only the **SPEAKER** workflow is being changed (from 12 → 8 states). Leave the line accurate; if any speaker-state count is mentioned elsewhere in CLAUDE.md, update it.
**And** line 22 + 64 (the Epic 9 status — same text appears twice in the file) are both updated consistently.

### AC12 — `docs/front-end-spec.md`

**Given** I read the file (G6+U1 from readiness review),
**When** I scroll to line 286 (`Email invitation link → Direct to invitation response page (magic link authentication)`),
**Then** the parenthetical is changed to `(Cognito session with forced password change on first login)`,
**And** line 296 (the Mermaid diagram node `A[Email Invitation with Magic Link]`) has its label updated to `A[Email Invitation with Login Link + Temp Password]`,
**And** any other occurrence of "magic link" in the file is identified by grep and corrected (run `grep -n "magic link\|magic-link" docs/front-end-spec.md` after edits — should return zero hits).

### AC13 — Doc-drift policy compliance

**Given** the PR is opened,
**Then** the commit message contains `[no-doc]` per CLAUDE.md doc-drift policy (this PR *is* the doc update — there is no code change to drift from),
**And** the PR description lists each updated file with a one-line "what changed" summary so reviewers can verify coverage in one pass,
**And** **no code files are modified** in this PR — only `*.md` (and possibly the architecture markdown sub-files referenced above),
**And** Story 11.A.1 is marked `done` in `_bmad-output/implementation-artifacts/sprint-status.yaml` upon merge.

### AC14 — Cross-reference integrity

**Given** every doc above has been updated,
**When** I run `grep -rn "SLOT_ASSIGNED\|CONFIRMED.*speaker\|withdrew.*speaker\|magic.link\|speaker.magic\|?token=\|speaker_tokens\|MagicLinkService\|TENTATIVE\|tentative_response\|OVERFLOW" docs/` excluding files that legitimately reference these terms as **removed/legacy** (i.e., excluding ADR-009 itself, Epic 11 PRD, plan, and this story file),
**Then** all hits are either inside a "Legacy / removed (per ADR-009)" annotation **or** in `archive/` folders **or** are part of historical comments — there are zero hits that present these terms as the current target.

> **Dev tip:** Use `grep -rn "TERM" docs/ --include="*.md" | grep -v "archive\|ADR-009\|epic-11\|implementation-readiness\|speaker-workflow-refactor.md\|prd-enhanced.md.*REMOVED\|FR13.*REMOVED"` for a quick sweep. Manual inspection still required.

### AC15 — Sitemap staleness (Epic 6 + Epic 8 marked deferred but are actually DONE)

**Context:** Per CLAUDE.md MVP Status section, Epic 6 (Speaker Self-Service Portal) and Epic 8 (Partner Coordination) are both **100% COMPLETE**, but `docs/wireframes/sitemap.md` and `docs/wireframes/sitemap-mermaid.md` still mark them as "DEFERRED TO PHASE 2+". Epic 7 (Attendee Experience Enhancements) remains genuinely deferred — only Epic 6 + Epic 8 need flipping.

**Scope discipline:** Update **top-level status banners only**. Do NOT re-audit every individual screen under each epic — the wireframes were aspirational, and a screen-by-screen status reconciliation is a separate effort. Add a disclaimer so future readers know to consult the actual implementation for screen-level truth.

#### AC15.1 — `docs/wireframes/sitemap.md`

**Given** I read line 6,
**Then** the "Update" line reads: `Reflects actual MVP implementation (Epics 1-5 100% COMPLETE, Epic 6 + Epic 8 100% COMPLETE), and Epic 7 deferral to Phase 3`.

**Given** I read lines 15-17,
**Then**:
- Line 15 (Epic 6): label changed from `📦 [EPIC 6 - DEFERRED TO PHASE 2+] - Speaker Self-Service Portal (optional enhancement)` to `✅ [EPIC 6 - 100% COMPLETE] - Speaker Self-Service Portal (delivered 2026-02-16)`.
- Line 16 (Epic 7): label changed from `📦 [EPIC 7 - DEFERRED TO PHASE 2+] - Attendee Experience Enhancements (personal dashboards, PWA)` to `📦 [EPIC 7 - DEFERRED TO PHASE 3] - Attendee Experience Enhancements (personal dashboards, PWA)`.
- Line 17 (Epic 8): label changed from `📦 [EPIC 8 - DEFERRED TO PHASE 2+] - Advanced Partner Analytics & Voting (optional enhancement)` to `✅ [EPIC 8 - 100% COMPLETE] - Partner Coordination (analytics + topic voting + meeting coordination; delivered 2026-02-22)`.

**Given** I read lines 45-47,
**Then**:
- Line 45 (Partner Portal): label changed from `Partner Portal 🔄 BASIC (Epic 8 deferred)` to `Partner Portal ✅ COMPLETE (Epic 8)`.
- Line 46 (Speaker Portal): label changed from `Speaker Portal 📦 DEFERRED (Epic 6)` to `Speaker Portal ✅ COMPLETE (Epic 6)`.
- Line 47 (Attendee Portal): unchanged (still public-only; Epic 7 deferred to Phase 3).

**Given** I read sections **4.5 Speaker Management** (around line 358), **5 Partner Portal** (around line 467), **5.2 Epic 8 Features DEFERRED** (around line 493), and **6 Speaker Portal** (around line 544),
**Then** each section heading is updated:
- "🔄 PARTIAL (Epic 6 Deferred)" → "✅ COMPLETE (Epic 6)"
- "📦 DEFERRED TO EPIC 6 (Phase 2+)" → "✅ COMPLETE (Epic 6)"
- "🔄 BASIC (Epic 8 Deferred)" → "✅ COMPLETE (Epic 8)"
- "Epic 8 Features DEFERRED TO PHASE 2+" → "Epic 8 Features (delivered; screen-level details in implementation, this section is historical wireframe context)"
- Same disclaimer pattern for Epic 6.

**Given** I read **section 8 Sitemap Legend** (around lines 1041-1052),
**Then** the `📦 DEFERRED` legend entry is updated so it applies only to Epic 7 (and any other genuinely deferred features), and a new `✅ COMPLETE` entry is added for delivered epics.

**Given** I read **section 9.2 User Journeys DEFERRED TO PHASE 2+** (around line 807),
**Then** the Partner Journey (line 809) is moved out of "DEFERRED" into a "Completed Journeys" subsection (or relabelled in place to ✅), the Speaker Journey (line 818) is similarly moved/relabelled, and the Attendee Journey (line 828) remains deferred.

**Given** I read **section 10.2 Phase 2+ Deferral Status** (around line 873-911),
**Then** the Epic 6 block (line 875: "0% IMPLEMENTED") is replaced with "100% IMPLEMENTED — see `_bmad-output/implementation-artifacts/8-*.md` and Epic 6 docs",
**And** the Epic 8 block (around line 895) is similarly flipped to 100% IMPLEMENTED with reference,
**And** the Epic 7 block (line 883: "20% IMPLEMENTED Public Only") stays as-is,
**And** the summary at line 908-916 (Phase 2+ DEFERRED, totals) is recalculated: only Epic 7 remains deferred; the screen-counts are revised down accordingly. **OR** if the recalculation is non-trivial, replace the summary table with a single line: "Phase 3 deferred work: Epic 7 only. See `CLAUDE.md` MVP Status for authoritative epic-level status."

**Given** I read **section 11.2 What Was Deferred (Scope Reductions)** (line 954-965),
**Then** entries 1 ("Speaker Self-Service Epic 6") and 3 ("Partner Analytics & Voting Epic 8" if present) are relabelled to "Delivered" with a brief outcome note,
**And** "Attendee Personal Features Epic 7" remains as deferred.

**Given** I read **section 12.2 Epic 6-8 Implementation (Optional)** (line 1007-1014),
**Then** the section heading is "Epic 6 and Epic 8: Delivered. Epic 7: Phase 3.",
**And** the Epic 6 / Epic 8 entries are summarised as delivered with links to the relevant Epic docs (`docs/prd/epic-6-speaker-portal-support.md`, `docs/prd/epic-8-partner-coordination.md`),
**And** the Epic 7 entry remains as Phase 3 planning.

**Given** the entire file is updated,
**Then** a banner is added at the top (right after line 1, before line 5):
> **⚠️ Note (2026-05-15):** This sitemap was originally authored when Epics 6, 7, 8 were all deferred. Epic 6 (Speaker Portal) and Epic 8 (Partner Coordination) are now 100% complete. Top-level epic-status labels have been refreshed; **screen-level details below each Epic 6 / Epic 8 section may diverge from actual implementation** (the wireframes were aspirational). Consult `_bmad-output/implementation-artifacts/` and the corresponding Epic PRDs for authoritative implementation status.

#### AC15.2 — `docs/wireframes/sitemap-mermaid.md`

**Given** I read line 5,
**Then** the Purpose line reads: `Visual representation of actual MVP implementation (Epics 1-5, 6, 8) and Phase 3 deferral (Epic 7)`.

**Given** I read lines 13-16 (the colour legend),
**Then**:
- Line 14: change from `🟡 Yellow - 📦 DEFERRED TO EPIC 6 (Speaker Self-Service Portal)` to `🟡 Yellow - ✅ DELIVERED (Epic 6: Speaker Self-Service Portal)`.
- Line 15 (Epic 7 orange): unchanged.
- Line 16: change from `🟣 Purple - 📦 DEFERRED TO EPIC 8 (Partner Analytics & Voting)` to `🟣 Purple - ✅ DELIVERED (Epic 8: Partner Coordination)`.

**Given** I read lines 38-40 (top-level portal status nodes in the root Mermaid diagram),
**Then**:
- Line 38 (Partner): change node label from `Partner Portal<br/>🔄 10% BASIC ONLY<br/>📦 Epic 8 Deferred` to `Partner Portal<br/>✅ COMPLETE<br/>Epic 8 Delivered`.
- Line 39 (Speaker): change from `Speaker Portal<br/>📦 DEFERRED Epic 6` to `Speaker Portal<br/>✅ COMPLETE<br/>Epic 6 Delivered`.
- Line 40 (Attendee): unchanged.

**Given** I read **section 5 Partner Portal** (line 327), **section 5.2 What Was Deferred (Epic 8)** (line 359), and **section 6 Speaker Portal** (line 388),
**Then** the section headings are updated:
- Section 5: `Partner Portal (🔄 10% BASIC - Epic 8 Deferred)` → `Partner Portal (✅ COMPLETE - Epic 8)`
- Section 5.2: `What Was Deferred (Epic 8)` → `Epic 8 — Delivered (wireframes in this section show planned scope; implementation in production)`. Keep the Mermaid subgraph block intact but update its title from `📦 DEFERRED TO EPIC 8 - Partner Analytics & Voting` to `✅ DELIVERED - Epic 8: Partner Coordination`.
- Section 6: `Speaker Portal (📦 DEFERRED TO EPIC 6)` → `Speaker Portal (✅ COMPLETE - Epic 6)`
- Section 6.1: `All Speaker Features Deferred` → `Epic 6 — Delivered (wireframes in this section show planned scope; implementation in production)`. Subgraph title flipped similarly.

**Given** I read **section 8.4 Deferred User Journeys (Epic 6-8)** (around line 608),
**Then** the section heading becomes "Deferred User Journeys (Epic 7)",
**And** the Partner Journey subgraph (line 612) is moved into a new "Delivered User Journeys" section or relabelled to `✅ DELIVERED`,
**And** the Speaker Journey subgraph (line 619) is moved/relabelled similarly,
**And** the Attendee Journey subgraph (line 627) stays as deferred to Phase 3.

**Given** I read **section 9.3 Deferred Features by Epic** (line 681-687) with the quadrant chart x-axis,
**Then** the x-axis label `["Epic 6 (Speaker)", "Epic 7 (Attendee)", "Epic 8 (Partner)", "Low Priority MVP"]` is updated to `["Epic 7 (Attendee)", "Low Priority MVP"]` (Epic 6 and Epic 8 removed),
**AND** the chart's title is updated to "Deferred Features (Phase 3 — Epic 7 + Low-Priority)".
> If updating the quadrant chart values is non-trivial, replace the chart with a single statement: "Phase 3 deferred work: Epic 7 (Attendee Experience) + low-priority MVP enhancements. See CLAUDE.md for authoritative status."

**Given** I read **section 10.2 What Was Deferred (Scope Reductions)** (line 727-734),
**Then** the `SpeakerSelfService` node (line 732) and `PartnerAnalytics` (if present) are removed from the "📦 DEFERRED - OPTIONAL ENHANCEMENTS" subgraph or relabelled,
**And** the `AttendeePersonal` node remains as Phase 3 deferred.

**Given** I read **section 13.2 Phase 2+ Deferred Epics** (lines 858-892) — the timeline/gantt chart,
**Then** the title changes from `Phase 2+ Optional Enhancements (Epics 6-8)` to `Phase 3 Deferred Work (Epic 7 + Low-Priority Enhancements)`,
**And** the "Epic 6: Speaker Portal" section (line 865) is removed from the gantt or moved to a "Delivered (Phase 2)" lane.
**And** the "Epic 7: Attendee Experience" section (line 872) remains.

**Given** the colour legend at the bottom (lines 891-893),
**Then** the entries for Yellow (Epic 6) and Purple (Epic 8) are updated to reflect "✅ DELIVERED" rather than "📦 DEFERRED".

**Given** the entire file is updated,
**Then** the same banner from AC15.1 is added at the top (after line 1, before line 5), adapted for the mermaid file.

#### AC15.3 — Disclaimer scope guard

**Given** AC15.1 and AC15.2 are applied,
**Then** the dev does NOT attempt to re-audit individual screen-level wireframe details under each Epic 6 / Epic 8 subsection. The screens listed (Speaker Dashboard, Material Submission Wizard, Topic Voting Screen, etc.) may or may not exist in the actual implementation; the wireframes capture the *planned* design, not the *delivered* design. The disclaimer banner makes this clear.
**And** the only changes are: **top-level status banners**, **section headings**, **legend entries**, **chart titles**, and a **disclaimer banner**. All other narrative content remains untouched.

---

## Tasks / Subtasks

- [x] **Task 1 — Read context** (AC: all)
  - [x] 1.1 Read `docs/architecture/ADR-009-unified-speaker-workflow.md` in full — this is the target spec.
  - [x] 1.2 Read `docs/plans/speaker-workflow-refactor.md` §0 (target model) and §1 (doc change list).
  - [x] 1.3 Read `docs/prd/epic-11-speaker-workflow-refactor.md` Story 11.A.1 section (lines 431-505) — AC come from there.
  - [x] 1.4 Run the cross-reference grep (AC14) BEFORE starting edits to establish the baseline hit count.

- [x] **Task 2 — `docs/prd-enhanced.md`** (AC1)
  - [x] 2.1 Rewrite the "Speaker Workflow (Parallel Per-Speaker Progression)" block at lines 52-56.
  - [x] 2.2 Update FR3 at line 75 — remove "magic link" terminology; describe Cognito + FORCE_CHANGE_PASSWORD.
  - [x] 2.3 Update FR17 at line 101 — replace state list and overflow description.
  - [x] 2.4 Update line 209 (Epic 9 phase line) — point to Epic 11.
  - [x] 2.5 Update line 403 (magic-link mention in delivered-capabilities bullet).
  - [x] 2.6 Update line 56 (`overflow` and `withdrew` states bullet).

- [x] **Task 3 — `docs/prd/epic-9-speaker-authentication.md`** (AC2)
  - [x] 3.1 Add `> **Supersedes prior Epic 9 plan per ADR-009.**` banner at top.
  - [x] 3.2 Replace Status line (line 3) with Epic 11-aligned status.
  - [x] 3.3 Delete the old Story 9.1-9.5 sections (~lines 95-247).
  - [x] 3.4 Rewrite scope as four work blocks pointing to Epic 11 Stories 11.E.2, 11.E.3, 11.F.1.
  - [x] 3.5 Preserve the Epic Goal (unified Cognito identity, zero duplicate accounts).

- [x] **Task 4 — `docs/architecture/03-data-architecture.md`** (AC3)
  - [x] 4.1 Delete the entire `Speaker` entity section (lines ~306-451).
  - [x] 4.2 Delete the `speakers` table SQL block.
  - [x] 4.3 Add User-section paragraph explaining SPEAKER role + reuse of `bio` / `profile_picture_url`.
  - [x] 4.4 Remove or rewrite the line at 289 (Speaker Service bullet).

- [x] **Task 5 — `docs/architecture/06a-workflow-state-machines.md`** (AC4)
  - [x] 5.1 Rewrite "Speaker Workflow Management (Per Speaker - Parallel)" section starting around line 189.
  - [x] 5.2 New state table with 8 states matching ADR-009 §0.1.
  - [x] 5.3 New transition diagram matching ADR-009 §0.2 (Mermaid stateDiagram-v2).
  - [x] 5.4 New "Derived flags" subsection — `is_slot_assigned`, `is_publishable` (no persisted columns).
  - [x] 5.5 Delete the `confirmed` state row, the `withdrew` row, parallel-quality/slot-confirmed discussion.
  - [x] 5.6 Replace `SpeakerWorkflowService.updateSpeakerWorkflowState` snippet with `SpeakerWorkflowService.transition(speakerId, toState, actor, payload)` per ADR-009 §"`SpeakerWorkflowService.transition()` skeleton".
  - [x] 5.7 Update `validateAllSpeakersConfirmed` + `validateAllSlotsHaveSpeakers` to use the derived `is_publishable` predicate via `countPublishableByEventId(eventId)`.
  - [x] 5.8 Add a "REMOVED per ADR-009" banner to the legacy "Overflow Management & Voting System" section.
  - [x] 5.9 Update Quality Review snippet (`QualityReviewService`) to call `transition()` instead of `updateSpeakerWorkflowState()`.

- [x] **Task 6 — `docs/architecture/04-api-design.md`** (AC5)
  - [x] 6.1 Rewrite §3 Speaker Coordination API summary — Cognito Bearer auth; 8-state machine; removed endpoints list.
  - [x] 6.2 Add `POST /api/v1/events/{eventCode}/speakers/{speakerId}/promote` to the API summary.
  - [x] 6.3 Remove "Overflow management and voting" from the Event Management API capabilities list.

- [x] **Task 7 — `docs/architecture/04-api-speaker-coordination.md`** (AC9)
  - [x] 7.1 Update doc header (Last Updated, ADR-009 reference, Important note) — User-as-identity, Cognito Bearer, HTTP enrichment (not JPQL joins).
  - [x] 7.2 Add `POST /api/v1/events/{eventCode}/speakers/{speakerId}/promote` with full path params, request body, responses, error codes 400/404/409, ORGANIZER role.
  - [x] 7.3 Add `PUT /api/v1/events/{eventCode}/speakers/{speakerId}/status` with the 8-state allow-list and explicit 400 reject list (SLOT_ASSIGNED, CONFIRMED, OVERFLOW, WITHDREW, TENTATIVE) and 400 reject for READY (→ point to /promote).
  - [x] 7.4 Add explicit "Removed endpoints" callout for `POST /api/v1/auth/speaker-magic-login` and `POST /api/v1/speaker-portal/validate-token`.
  - [x] 7.5 Rewrite `Speaker` schema → `SpeakerPool` schema (drop legacy speaker-only fields, add derived `isSlotAssigned`/`isPublishable`).
  - [x] 7.6 Rewrite `SpeakerWorkflowState` enum to the 8-state model with descriptions and "REMOVED states" callout.
  - [x] 7.7 Rewrite the "Speaker Workflow States" + "Overflow Handling" prose sections at the bottom — capacity gate replaces overflow.

- [x] **Task 8 — `docs/architecture/06-backend-architecture.md`** (AC6)
  - [x] 8.1 Add ADR-009 callout at top of Authentication section — single Cognito flow, no parallel speaker auth.
  - [x] 8.2 Add new "Speaker authentication (ADR-009)" subsection — provisioning lifecycle, side-effect hook, components NOT present, link to 06b.
  - [x] 8.3 Verify `/api/v1/speaker-portal/**` is NOT in any `permitAll`/public endpoint list — confirmed clean.

- [x] **Task 9 — `docs/architecture/06b-user-lifecycle-sync.md`** (AC7)
  - [x] 9.1 Added new section "Pattern N: Speaker Provisioning at CONTACTED → READY" after Pattern 1 — full sequence diagram, steps, idempotency contract, failure modes, no-persistence guarantee, pattern relationship to Patterns 1/2/3.
  - [x] 9.2 Pattern 1 (PostConfirmation) and Patterns 2/3 preserved unchanged.

- [x] **Task 10 — `docs/architecture/ADR-004-factor-user-fields-from-domain-entities.md`** (AC8)
  - [x] 10.1 Added Revision History entry (2026-05-15, v1.3) per ADR-009.
  - [x] 10.2 Added a Speaker note + banner in "Domain Entity Design Patterns" section, then re-cast the legacy "Speaker Entity" subsection as historical context.

- [x] **Task 11 — `docs/architecture/index.md`** (AC10)
  - [x] 11.1 Added a new "Architecture Decision Records (ADRs)" subsection to "Key Architectural Decisions" with ADR-002, 003, 004, 007, 009 entries.
  - [x] 11.2 Verified existing section references still resolve (the 9-section list was not affected by the deletions).

- [x] **Task 12 — `CLAUDE.md`** (AC11)
  - [x] 12.1 Updated Epic 9 status line at line 22 → Epic 11 (Unified Speaker Workflow Refactor).
  - [x] 12.2 Updated second Epic 9 status line at line 64 → Epic 11.
  - [x] 12.3 Updated "When Adding New Features" Epic 9 bullet at line 75 → Epic 11.
  - [x] 12.4 Updated the milestone line and current-phase line to reflect Epic 11 supersession.
  - [x] 12.5 Verified the EVENT 9-state machine reference (line 27) is unchanged — only the SPEAKER workflow shrinks (per scope).

- [x] **Task 13 — `docs/front-end-spec.md`** (AC12)
  - [x] 13.1 Updated line 286 prose — "magic link authentication" → "Cognito session with forced password change on first login, per ADR-009".
  - [x] 13.2 Updated line 296 Mermaid node label → "Email Invitation with Login Link + Temp Password".
  - [x] 13.3 Final grep `magic link\|magic-link` returns 0 hits.

- [x] **Task 14 — Cross-reference integrity sweep** (AC14)
  - [x] 14.1 Ran the comprehensive AC14 grep across `docs/`.
  - [x] 14.2 Classified hits: legitimate "removed/legacy" annotations in updated docs (✅); historical user-guide / Epic 6 / QA assessment docs (out of scope per story Open Question #1); doc-audit archive (historical).
  - [x] 14.3 Added a one-line banner to `docs/prd/epic-5-enhanced-organizer-workflows.md` Speaker Workflow section (the only forward-facing PRD with stale references that the story scope reaches).
  - [x] 14.4 User-guide docs (`docs/user-guide/**`) are intentionally not updated — they document the *current live* magic-link behaviour, which is reverted only after Epic 11 Phase F lands (per story Open Question #1, "out of scope for this story").

- [x] **Task 15 — Sitemap staleness (Epic 6 + Epic 8 done)** (AC15)
  - [x] 15.1 Confirmed authoritative status from CLAUDE.md (Epic 6 ✅, Epic 7 Phase 3, Epic 8 ✅).
  - [x] 15.2 `docs/wireframes/sitemap.md` — disclaimer banner; version 2.1 line; status indicator legend flipped; root portal labels flipped; section 4.5 / 5 / 5.2 / 6 headings flipped; section 9.2 journeys flipped; section 10.2 replaced with pointer to CLAUDE.md per the AC15.1 simplification option; section 11.2 rewritten as "Delivered Post-MVP + Phase 3 Deferrals"; section 12.2 rewritten.
  - [x] 15.3 `docs/wireframes/sitemap-mermaid.md` — disclaimer banner; legend flipped (Yellow/Purple → DELIVERED); root Mermaid portal nodes flipped; sections 5 / 5.2 / 6 / 6.1 headings flipped; section 8.4 → Epic 7 only; section 9.3 quadrant chart simplified to Epic 7 + Low-Priority; section 10.2 diagram pared down to deferred-only nodes; section 13.2 gantt simplified to Epic 7 only; bottom colour legend flipped.
  - [x] 15.4 Sweep grep `DEFERRED.*Epic 6\|DEFERRED.*Epic 8` against the two sitemap files — only the disclaimer-banner-protected "originally deferred" mentions remain.

- [ ] **Task 16 — PR hygiene** (AC13)
  - [ ] 16.1 Commit message starts with `docs(epic-11):` and ends with `[no-doc]`. _(left for the PR creation step — out of dev-story scope)_
  - [ ] 16.2 PR description lists each updated file with one-line summary. _(left for the PR creation step)_
  - [x] 16.3 Verified `git status --short` shows only `*.md` files (and the in-scope `_bmad-output/implementation-artifacts/sprint-status.yaml`) — no code files.
  - [ ] 16.4 Open PR; await review. _(post-review step)_

### Review Findings

_Adversarial code review run 2026-05-15 (Blind Hunter + Edge Case Hunter + Acceptance Auditor). 27 raw findings → 19 retained after triage (8 dismissed as false positives or cosmetic; OVERFLOW_MANAGEMENT hits in event-workflow enums are legitimately preserved per story scope)._

**Decisions resolved (3 → patches; see P14–P17 below):**

- [x] [Review][Decision→Patch] **D1: Story 11.E.1 referenced but no block describes it** — verified against Epic 11 PRD: Story 11.E.1 is "CDK + IAM prereq — App Client auth flow and Cognito admin permissions". Resolution: add a **Block 0** describing it, so blocks align 1:1 with stories (E.1, E.2, E.3, F.1). Becomes patch **P14**.
- [x] [Review][Decision→Patch] **D2: Block 2 and Block 3 both map to Story 11.E.3** — verified against Epic 11 PRD: Story 11.E.3 legitimately covers "Speaker-portal Cognito auth + frontend session refactor + multi-role nav cherry-pick" as one story. Resolution: **merge Blocks 2 + 3** into a single block, yielding four blocks aligned 1:1 with the four stories. Becomes patch **P15**.
- [x] [Review][Decision→Patch] **D3: Out-of-scope arch files with unannotated legacy speaker terms** — user chose "annotate now" (consistent with the epic-5 precedent the dev already set). Becomes patches **P16** (`01-system-overview.md`) and **P17** (`06d-notification-system.md`).

**Patch findings (17 — 15 applied this session, 1 dismissed as false positive, 1 deferred as broader than original finding):**

- [x] [Review][Patch] **(HIGH) `GET /api/v1/speakers` still references deleted Speaker + SpeakerAvailability schemas** [`docs/architecture/04-api-speaker-coordination.md:30-56`] — APPLIED: endpoint rewritten to drop `availability` query param + `Speaker`/`SpeakerAvailability` `$ref`s; response now returns an inline User+SPEAKER role projection (`bio`, `profilePictureUrl` per ADR-004/ADR-009); description notes the shape is provisional pending Epic 11 Phase C.

- [x] [Review][Patch] **(MEDIUM) PUT /status "MUST be one of" list includes IDENTIFIED** [`docs/architecture/04-api-speaker-coordination.md:257`] — APPLIED: IDENTIFIED removed from the description list; description now clarifies IDENTIFIED is the initial state and never a transition target, and READY uses POST /promote.

- [x] [Review][Patch] **(MEDIUM) `SpeakerWorkflowState` OpenAPI enum schema includes READY despite PUT /status rejecting it** [`docs/architecture/04-api-speaker-coordination.md` ~738-756] — APPLIED: enum kept as 8-state (all valid as read/response values); schema description now distinguishes read-state from write-`targetStatus` semantics and points to the PUT /status allow-list.

- [x] [Review][Patch] **(MEDIUM) Sitemap header flipped but "🔄 PARTIAL - PLACEHOLDER" subline remains** [`docs/wireframes/sitemap.md` ~360-365] — APPLIED: §4.5 Speaker Management Screen note rewritten to clarify the organizer-side screen is a placeholder while speaker self-service is delivered via §6 Speaker Portal (Epic 6).

- [x] [Review][Patch] **(MEDIUM) §5.2 "Epic 8 Features (delivered)" header followed by stale "DEFERRED TO EPIC 8" subtitle** [`docs/wireframes/sitemap.md` ~495-497] — APPLIED: subtitle changed from "📦 DEFERRED TO EPIC 8" to "Originally planned for Epic 8 (per wireframes; ✅ DELIVERED as Epic 8)". Individual `📦 [EPIC 8 - DEFERRED]` item tags left in place per AC15.3 scope guard (global disclaimer banner covers screen-level details).

- [x] [Review][Patch] **(MEDIUM) MVP Status note still says "Advanced analytics and voting deferred to Epic 8"** [`docs/wireframes/sitemap.md` ~471`] — APPLIED: MVP Status rewritten to acknowledge Epic 8 delivered (2026-02-22) with attendance analytics, topic voting, and meeting coordination.

- [x] [Review][Patch] **(MEDIUM) Epic-9 Status field still says "IN PROGRESS" despite Supersedes banner** [`docs/prd/epic-9-speaker-authentication.md:7`] — APPLIED: changed to "📦 SUPERSEDED BY EPIC 11 — implementation now tracked in Epic 11 Phases E and F".

- [x] [Review][Patch] **(MEDIUM) Index says "legacy 12-state model"; other docs say 10-state** [`docs/architecture/index.md:123`] — APPLIED: changed "12-state" to "10-state" for consistency with the rest of the docs.

- [x] [Review][Patch] **(MEDIUM) `prd-enhanced.md` reorganization rationale still pitches "Epic 9 planned for unified speaker/attendee JWT authentication"** [`docs/prd-enhanced.md:213`] — APPLIED: replaced with "the prior Epic 9 (JWT magic-link speaker authentication) is superseded by **Epic 11** per ADR-009, which adopts standard AWS Cognito with `FORCE_CHANGE_PASSWORD` first-login as the sole speaker auth path".

- [x] [Review][Patch] **(MEDIUM) ADR-004 supersedes annotation scope too narrow** [`docs/architecture/ADR-004-factor-user-fields-from-domain-entities.md:402, 425, 655`] — APPLIED: added explicit "Superseded by ADR-009 for SPEAKER role" callout at top of §User Role Management; strike-through annotations on the Speaker entity creation workflow + `/speakers/me` examples; ADR-004:655 Implementation Status item annotated as WITHDRAWN per ADR-009.

- [x] [Review][Patch] **(MEDIUM) Reference to "Authentication & Roles section in CLAUDE.md" points to wrong file** [`docs/prd/epic-9-speaker-authentication.md:15, 113`, `docs/architecture/06b-user-lifecycle-sync.md:218`] — APPLIED: all 3 references rewritten to point to canonical architecture docs (`06b-user-lifecycle-sync.md`) instead of the nonexistent CLAUDE.md section.

- [x] [Review][Patch] **(MEDIUM, P14 from D1) Add Block 0 — CDK + IAM prereq for Story 11.E.1** [`docs/prd/epic-9-speaker-authentication.md`] — APPLIED: inserted Block 0 describing Story 11.E.1's CDK App Client + Cognito admin IAM work as infrastructure prerequisite.

- [x] [Review][Patch] **(MEDIUM, P15 from D2) Merge Block 2 + Block 3 into a single block for Story 11.E.3** [`docs/prd/epic-9-speaker-authentication.md`] — APPLIED: merged into a single "Block 2 — Cognito-secured speaker portal + multi-role navigation" with three work-stream sub-sections (backend portal auth, frontend session refactor, multi-role nav). Block 4 renumbered to Block 3.

- [x] [Review][Patch] **(MEDIUM, P16 from D3) Annotate `01-system-overview.md` Speaker Coordination Service section** [`docs/architecture/01-system-overview.md:333-354`] — APPLIED: added "⚠️ Superseded by ADR-009 (Unified Speaker Workflow) — see Epic 11 Phases B/C" banner; the 11-state description retained as historical context.

- [x] [Review][Patch] **(MEDIUM, P17 from D3) Annotate `06d-notification-system.md` legacy notification types** [`docs/architecture/06d-notification-system.md:233, 236`] — APPLIED: inline comments added to `SLOT_ASSIGNED` (derived from session.start_time) and `OVERFLOW_DETECTED` (replaced by slot-capacity gate).

- [x] [Review][Patch][DISMISSED-on-verification] **(LOW) Sitemap-mermaid Yellow legend orphaned after §9.3 chart rewrite** [`docs/wireframes/sitemap-mermaid.md`] — verified false positive: Yellow `#FFD700` is actively used in many charts throughout the file (lines 50, 413-419, 643-648), not just §9.3.

- [x] [Review][Patch][DEFERRED-broader-than-finding] **(LOW) ADR-009 cite drift: §0.3 vs §0.7** — verified the drift is intentional and consistent (§0.3 for removed fields, §0.7 for removed states), BUT ADR-009 has no `§0` numbered subsections at all. All 26 such citations across the docs point to nonexistent sections. Deferred to follow-up; recorded in `_bmad-output/implementation-artifacts/deferred-work.md`.

**Deferred (3 — checked off, pre-existing or out-of-scope):**

- [x] [Review][Defer] **Mermaid `<br/>` inside stateDiagram-v2 edge labels may not render in older renderers** [`docs/architecture/06a-workflow-state-machines.md:994-1012`] — needs a visual smoke test; cosmetic.
- [x] [Review][Defer] **Speaker-coordination-service ownership ambiguity** — `/speaker-portal/**` host vs `speaker_pool` vs workflow ownership not spelled out in one place. Architectural question for Phase B.
- [x] [Review][Defer] **`session_speakers` narrative mixes target-tense and current-tense** [`docs/architecture/03-data-architecture.md:303`, `docs/architecture/06a-workflow-state-machines.md:1112`] — minor cosmetic; doc is "what we plan to do" so saying "There is no" is premature for a doc-only Phase A.

**Dismissed (8 — noise / false positives):**

- "8 states" count framing (count is correct; "7+1" reads slightly off but is accurate).
- Stale "9-state" near speaker section in `04-api-design.md:363, 371` — refers to the EVENT workflow, not speaker; legitimately preserved.
- `languages` field ambiguity in SpeakerPool removed-fields callout — clear from context.
- Sitemap §10.3 totals — cleanup was correct.
- `epic-5-enhanced-organizer-workflows.md` banner placement — banner is scoped to Speaker Workflow subsection only; reader-error risk is acceptable.
- `countPublishableByEventId` referenced as if it exists — forward-looking spec snippet; prescriptive for Phase B implementation.
- `OVERFLOW_MANAGEMENT` in `03-data-architecture.md:467` and `04-api-event-management.md:1494` — these are values of `EventWorkflowState` (the 9-state EVENT workflow the story explicitly preserves), not speaker states.

---

## Dev Notes

### Why this story exists (and why it goes first)

Epic 11 is a 6-phase refactor (A–F) consolidating two parallel speaker-coordination flows onto a single state machine, replacing three magic-link auth mechanisms with standard Cognito FORCE_CHANGE_PASSWORD, and deleting the redundant `Speaker` entity in favour of `User + SPEAKER role` per ADR-004. **Phase A lands the documentation alignment FIRST** so that:

1. Subsequent code reviews (Phases B–F) have a single coherent target to verify against, not three conflicting documents.
2. The doc-drift-prevention policy (CLAUDE.md §"Doc Drift Prevention") is satisfied: code changes from Phase B onward will land with `[no-doc]` markers because Phase A already aligned the docs.
3. The legacy magic-link / 12-state / `Speaker` entity model is removed from the developer-facing surface BEFORE any code is changed — eliminating the risk that a downstream dev reads stale docs and reverts the refactor inadvertently.

### Files being modified — current state summary

I read each file before drafting these AC. Here's what's there today:

| File | Current state | What this story changes |
|------|---------------|-------------------------|
| `docs/prd-enhanced.md` | Lines 52-56 list the 12-state speaker workflow incl. `overflow`, `withdrew`, `confirmed`, `slot_assigned`. FR3 (line 75) mentions "magic link"-ish wording. FR17 (line 101) lists overflow management with voting. Lines 209+403 mention magic-link. | Replace with 8-state model; FR3/FR17 reworded; magic-link references removed. |
| `docs/prd/epic-9-speaker-authentication.md` | 510 lines. Status "IN PROGRESS Story 9.1 done; 9.2-9.5 planned." Stories 9.1 (JWT magic link), 9.2 (account creation), 9.3 (dual auth), 9.4 (migration), 9.5 (multi-role nav) all detailed. | Rewrite in place: add Supersedes banner; remove Stories 9.1-9.5 content; replace with 4 work blocks pointing to Epic 11 Stories 11.E.2/11.E.3/11.F.1; preserve Epic Goal. |
| `docs/architecture/03-data-architecture.md` | Has full `Speaker` entity section (lines 306-451) with TypeScript interface, JPA repository pattern, `enum SpeakerWorkflowState` (12 values), `enum SpeakerAvailability`, relationships, response example, Java service snippet. Also has `speakers` table SQL block. Line 289 mentions Speaker Service. | Delete Speaker entity section + speakers SQL; add SPEAKER-role paragraph to User section. |
| `docs/architecture/06a-workflow-state-machines.md` | "Speaker Workflow Management (Per Speaker - Parallel)" section starts at line 189. Has 12-state table with `confirmed`, `withdrew`, parallel-quality/slot-confirmed discussion. `SpeakerWorkflowService.updateSpeakerWorkflowState` Java snippet at line 251+. | Rewrite section: 8-state table, new transition diagram, Derived flags subsection, `SpeakerWorkflowService.transition()` skeleton replacing updateSpeakerWorkflowState. |
| `docs/architecture/04-api-design.md` | Index file pointing to sub-docs (04-api-core, 04-api-event-management, 04-api-speaker-coordination, etc.). No magic-link endpoints listed at top level. | Light touch: ensure §3 Speaker Coordination API summary uses Cognito Bearer; add promote endpoint to summary. |
| `docs/architecture/04-api-speaker-coordination.md` | Detailed speaker-coordination endpoints (sub-doc). Likely has magic-link endpoints and `?token=` query auth on speaker-portal routes. | Heavy edit: remove magic-link endpoints; add promote endpoint; tighten PUT status. |
| `docs/architecture/06-backend-architecture.md` | Section "Authentication and Authorization" (lines 33+). Public endpoints list includes some `permitAll`. JWT validation at API Gateway. No mention of speaker-magic-login currently in line 75 grep but speaker-portal `permitAll` may exist. | Single Cognito flow language; ensure no speaker-portal `permitAll`. |
| `docs/architecture/06b-user-lifecycle-sync.md` | Pattern 1: PostConfirmation Lambda (creates user on Cognito email verification, role ATTENDEE). No speaker-provisioning section. | Add new "Pattern N: Speaker Provisioning at CONTACTED → READY" section. |
| `docs/architecture/ADR-004-factor-user-fields-from-domain-entities.md` | 803 lines. Has Revision History section near end. Domain Entity Design Patterns at line 282. | Append Revision History entry + Speaker note in Patterns section. |
| `docs/architecture/index.md` | TOC of 9 architecture docs + Key Architectural Decisions section. No ADR-009 reference yet. | Add ADR-009 to Key Decisions; verify all section refs still resolve. |
| `CLAUDE.md` | Lines 22, 64, 75 reference Epic 9 status. Line 27 mentions "9-state machine" (EVENT machine — correct, unchanged). | Update Epic 9 lines → Epic 11. |
| `docs/front-end-spec.md` | Lines 286, 296 reference magic-link. | Update both lines. |
| `docs/wireframes/sitemap.md` | Marks Epic 6 + 8 as DEFERRED TO PHASE 2+ throughout (lines 6, 15-17, 45-47, sections 4.5 / 5 / 5.2 / 6 / 9.2 / 10.2 / 11.2 / 12.2 + Legend section 8). Epic 6 + 8 are actually 100% complete per CLAUDE.md. | Flip top-level status banners + section headings only. Add disclaimer banner. Do not re-audit screen-level wireframe details. |
| `docs/wireframes/sitemap-mermaid.md` | Same staleness — line 5 Purpose, lines 13-16 colour legend, lines 38-40 root portal nodes, sections 5 / 5.2 / 6 / 6.1 / 8.4 / 9.3 / 10.2 / 13.2 + bottom colour legend. | Flip top-level Mermaid node labels + section headings + chart titles. Add disclaimer banner. Quadrant chart x-axis simplified or replaced with pointer. |

### Critical "what NOT to break"

Per the doc-alignment scope:

- ❌ **Do NOT modify any code file** (`.java`, `.ts`, `.tsx`, `.sql`, `.yml`, `.yaml`, etc.). This is doc-only.
- ❌ **Do NOT touch the EVENT workflow state machine** in `docs/architecture/06a-workflow-state-machines.md` lines 24-188 — that's the 9-state EVENT machine, unchanged by Epic 11.
- ❌ **Do NOT delete the `OutreachHistory` discussion** in any doc — outreach history is the heart of the `CONTACTED` phase and is preserved (per plan §3.1).
- ❌ **Do NOT remove ADR-004's existing patterns** — ADR-004 stays in force; this story only **appends** a note about SPEAKER.
- ❌ **Do NOT alter the PostConfirmation / PreTokenGeneration Lambda sections** in 06b — they remain the standard self-registration path. The new Pattern N is **additional**.
- ❌ **Do NOT rename or delete `docs/prd/epic-9-speaker-authentication.md`** — the rewrite is **in place**. The file path is referenced by sprint-status.yaml and the readiness report.
- ❌ **Do NOT remove TENTATIVE / OVERFLOW / WITHDREW mentions from ADR-009 itself or from `docs/plans/speaker-workflow-refactor.md`** — those files document why the terms are gone; they must keep the terms in a "removed/legacy" context.

### Decision points the dev does NOT need to make

These are pre-decided in ADR-009 and the plan:

- **Auth flow**: Standard Cognito FORCE_CHANGE_PASSWORD on first login (ADR-009 §0.5). Not OTP. Not Custom Auth Lambdas. Not magic link.
- **8 states only**: `IDENTIFIED → CONTACTED → READY → INVITED → ACCEPTED → CONTENT_SUBMITTED → QUALITY_REVIEWED` + `DECLINED` from any non-terminal state. No `SLOT_ASSIGNED`, no `CONFIRMED`, no `OVERFLOW`, no `WITHDREW`.
- **Derived flags**: `is_slot_assigned` and `is_publishable` are READ-TIME computed, NOT persisted columns.
- **TENTATIVE response**: Gone. ACCEPT or DECLINE only.
- **`speakers` table**: Deleted. `user_profiles` not extended. SPEAKER is a role in `role_assignments`.
- **`bio`** is the short CV column; **`profile_picture_url`** is the portrait column. Both already exist per ADR-004.

### Style notes

- This is enterprise architecture documentation — keep prose tight, factual, no marketing tone.
- When rewriting state diagrams, use Mermaid `stateDiagram-v2` syntax (consistent with the rest of 06a).
- Code snippets in 06a should match the actual Java target — `transition(speakerId, toState, actor, payload)` signature, NOT placeholder pseudocode.
- For deletions of large blocks, do NOT leave a `<!-- TODO: removed -->` comment. Just delete cleanly.

### Reading order recommendation

1. **First**, read `docs/architecture/ADR-009-unified-speaker-workflow.md` end to end. This is the spec.
2. **Then**, read `docs/plans/speaker-workflow-refactor.md` §0 (target model) and §1 (doc change list, lines 212-228).
3. **Then**, read `docs/prd/epic-11-speaker-workflow-refactor.md` Story 11.A.1 section (lines 431-505). The AC there are the authoritative source for what this story must accomplish.
4. **Then** start editing in dependency order: prd-enhanced → epic-9 → architecture/03 → 06a → 04-api-design → 04-api-speaker-coordination → 06 → 06b → ADR-004 → index → CLAUDE → front-end-spec.

### Project Structure Notes

- All files are under `docs/` or root (`CLAUDE.md`). No `web-frontend/`, `services/`, or `infrastructure/` changes.
- Output artifact: this story file. Sprint-status.yaml will be updated by the workflow.

### References

- [Source: docs/architecture/ADR-009-unified-speaker-workflow.md §0 (target model)] — authoritative spec for new state machine, auth flow, entity model.
- [Source: docs/architecture/ADR-009-unified-speaker-workflow.md §1 (Documentation Changes)] — the same doc-change table that grounds the AC in this story.
- [Source: docs/plans/speaker-workflow-refactor.md §1 lines 212-228] — plan's doc-change table.
- [Source: docs/prd/epic-11-speaker-workflow-refactor.md Story 11.A.1 lines 431-505] — original AC; this story extends with explicit line pointers.
- [Source: docs/implementation-readiness-report-2026-05-15.md U1 + AC12] — adds `docs/front-end-spec.md` to scope (not in original 11.A.1 but recommended in readiness audit).
- [Source: docs/architecture/ADR-004-factor-user-fields-from-domain-entities.md] — referenced for User-centric design pattern; appended-to in AC8.
- [Source: docs/architecture/ADR-003-meaningful-identifiers-public-apis.md] — referenced indirectly; speaker_pool uses `username` (meaningful ID), not UUID FK.
- [Source: CLAUDE.md §"Doc Drift Prevention" lines ~530-540] — `[no-doc]` commit message policy for doc-only PRs.
- [Source: CLAUDE.md §"MVP Status" lines 7-23] — authoritative epic-level status; the source of truth for the sitemap-staleness fix in AC15. Epic 6 ✅ complete, Epic 7 deferred to Phase 3, Epic 8 ✅ complete.
- [Source: docs/wireframes/sitemap.md (current state — to be updated per AC15.1)] — top-level status banners and section headings flipped for Epic 6 + 8.
- [Source: docs/wireframes/sitemap-mermaid.md (current state — to be updated per AC15.2)] — Mermaid root diagram, subgraph titles, quadrant chart, gantt all updated for Epic 6 + 8 delivery.

### Testing Standards (for a doc-only story)

This is **documentation work, not code**. The testable artifacts are:

1. **Grep sweep (AC14)** — automated check that legacy terms are absent or annotated as removed.
2. **Cross-reference integrity (AC10 + AC14)** — every link in `docs/architecture/index.md` resolves; no broken cross-doc references.
3. **PR review by a second engineer** — verify every AC's "Then" clause matches the actual file content.

No unit tests, no integration tests, no Playwright. The acceptance signal is **a clean PR review + a successful merge to feature/speaker-workflow-refactor**.

---

## Dev Agent Record

### Agent Model Used

claude-opus-4-7 (1M context)

### Debug Log References

- Baseline AC14 grep (pre-edit): 523 hits across `docs/**/*.md` for the legacy-term pattern. Captured to `/tmp/baseline-grep-1.log`.
- Post-edit AC14 grep (excluding ADR-009, Epic 11, plan, this story, implementation-readiness, prd-enhanced REMOVED markers): 232 hits. Captured to `/tmp/post-grep-1.log`.
- Per-file post-edit hit classification:
  - `docs/architecture/04-api-speaker-coordination.md` (7 hits), `docs/architecture/06a-workflow-state-machines.md` (3 hits), `docs/prd/epic-9-speaker-authentication.md` (11 hits) — all inside "REMOVED/legacy per ADR-009" annotations. ✅ AC14 compliant.
  - `docs/plans/doc-audit/*` (45+ hits) — historical audit reports; legitimately reference legacy terms as findings. ✅ Historical.
  - `docs/qa/assessments/6.1*` + `docs/qa-gates/6.2*` (~22 hits) — Epic 6 historical QA assessments. ✅ Per story Open Question #3 ("Epic 6 docs out of scope").
  - `docs/user-guide/**` (~30+ hits) — documents the *currently live* magic-link behaviour, which is reverted only after Epic 11 Phase F lands. ✅ Per story Open Question #1.
  - `docs/architecture/epic-6-speaker-onboarding-plan.md` (20 hits) — Epic 6 completed-epic plan. ✅ Per story Open Question #3.
  - `docs/prd/epic-5-enhanced-organizer-workflows.md` — added a one-line "Superseded by ADR-009" banner to the Speaker Workflow section. ✅
  - `docs/prd/epic-4-public-website-content-discovery.md` + `docs/prd/epic-10-additional-stories.md` `?token=` hits — newsletter unsubscribe / registration deregistration tokens, NOT speaker auth. Out of scope for ADR-009.

### Completion Notes List

- **Net-zero code change.** Only `*.md` files plus `_bmad-output/implementation-artifacts/sprint-status.yaml` modified. Verified via `git status --short`.
- **Scope discipline.** Did not touch: any `.java` / `.ts` / `.tsx` / `.sql` / `.yml` (other than sprint-status), the EVENT workflow state machine in `06a-workflow-state-machines.md` lines 24-188, the PostConfirmation / PreTokenGeneration Lambda sections in `06b-user-lifecycle-sync.md`, ADR-004's existing patterns (only appended), the Epic 6 docs, the user-guide docs (per Open Question #1).
- **Epic 5 PRD banner added** (AC14 implication, not in AC list) — the only forward-facing PRD with stale state-machine references that the story scope reaches. A one-line "Superseded by ADR-009" banner with link to the target architecture is the minimum needed to satisfy AC14's "zero hits that present these terms as the current target".
- **Sitemap simplification.** Per AC15.3 scope guard: top-level status banners + section headings + legend entries + chart titles + a disclaimer banner. Did NOT re-audit individual screen-level wireframe details under Epic 6 / Epic 8 subsections. The screen-count totals in `sitemap.md` §10.2 were replaced with a pointer to CLAUDE.md per the AC15.1 simplification option ("if the recalculation is non-trivial, replace the summary table with a single line").
- **Quadrant chart in `sitemap-mermaid.md` §9.3** simplified from 4 axes to 2 (Epic 7 + Low Priority) — per AC15.2 the chart values needed updating; chose the simpler-of-two options offered by the AC.
- **Pattern N section in `06b`** is comprehensive — includes a Mermaid sequence diagram, ordered steps, idempotency contract, failure modes, no-persistence guarantee, and explicit relationship to Patterns 1/2/3.
- **`SpeakerWorkflowState` enum and `SpeakerPool` schema in `04-api-speaker-coordination.md`** are now the canonical OpenAPI-style references for the 8-state model and the derived flags. The legacy `Speaker` schema is replaced (not just deprecated) because the entity is deleted per ADR-009.

### File List

Modified markdown:
- `CLAUDE.md` (AC11)
- `docs/prd-enhanced.md` (AC1)
- `docs/prd/epic-9-speaker-authentication.md` (AC2 — in-place rewrite)
- `docs/prd/epic-5-enhanced-organizer-workflows.md` (AC14 — added supersession banner to Speaker Workflow section)
- `docs/architecture/03-data-architecture.md` (AC3 — deleted Speaker entity + speakers SQL; added SPEAKER-role paragraph)
- `docs/architecture/04-api-design.md` (AC5 — rewrote §3 Speaker Coordination API summary; removed overflow voting from §2 capabilities)
- `docs/architecture/04-api-speaker-coordination.md` (AC9 — added /promote + /status endpoints; replaced Speaker schema with SpeakerPool; updated workflow state enum and prose sections)
- `docs/architecture/06-backend-architecture.md` (AC6 — added ADR-009 callout + Speaker authentication subsection)
- `docs/architecture/06a-workflow-state-machines.md` (AC4 — rewrote speaker workflow section; updated validateAllSpeakersConfirmed predicates; legacy Overflow Management banner)
- `docs/architecture/06b-user-lifecycle-sync.md` (AC7 — added Pattern N: Speaker Provisioning at CONTACTED → READY)
- `docs/architecture/ADR-004-factor-user-fields-from-domain-entities.md` (AC8 — Revision History entry + Speaker note + historical re-cast)
- `docs/architecture/index.md` (AC10 — added ADRs subsection)
- `docs/front-end-spec.md` (AC12 — replaced both magic-link mentions)
- `docs/wireframes/sitemap.md` (AC15.1 — top-level Epic 6 + 8 status flip + banner + section refresh)
- `docs/wireframes/sitemap-mermaid.md` (AC15.2 — top-level Epic 6 + 8 status flip + banner + diagram updates)

Modified non-markdown (in-scope per the story's File List):
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (status flip `11-a-1-…: ready-for-dev → review`)

### Change Log

| Date | Change |
|------|--------|
| 2026-05-15 | Story 11.A.1 implemented per ADR-009. 15 docs aligned + 1 sprint-status update. Net-zero code change. Ready for review. |

---

## Open Questions (for clarification before merge)

These were surfaced during story drafting; the dev agent may proceed with the inferred decision but should flag in PR description if a different call is preferred.

1. **`docs/front-end-spec.md` (Story-stated absent vs readiness-recommended)** — The original 11.A.1 AC in Epic 11 PRD does NOT list `front-end-spec.md`. The readiness audit (U1) added it as an extension because lines 286+296 contain stale magic-link references. **Inferred decision**: include it (additive cleanup, low effort, prevents an immediate post-merge doc-drift PR). Flag if scope creep concern.

2. **`docs/architecture/04-api-speaker-coordination.md` (AC9 — not in original PRD list)** — The plan §1 lists `04-api-design.md` as the file to update. But that file is a top-level index pointing to sub-docs; the actual speaker-portal endpoint detail lives in `04-api-speaker-coordination.md`. **Inferred decision**: update both — index (light touch) and sub-doc (heavy edit) — for internal consistency. Flag if a single-file scope was intended.

3. **Story 9.1 docs and Epic 6 deprecation banners** — Plan §1 line 226 says "Add a deprecation banner pointing to ADR-009" to "Epic 6 and Story 9.1 docs that document the magic-link flow." **Inferred decision**: Out of scope for this story — the Epic 6 docs are part of a completed epic (per CLAUDE.md), and Story 9.1 is being subsumed via the AC2 rewrite of `docs/prd/epic-9-speaker-authentication.md`. The plan §1 line 226 phrasing is ambiguous about which specific files; recommend a follow-up story if needed.

4. **`docs/wireframes/sitemap.md` and `docs/wireframes/sitemap-mermaid.md`** — Sitemap originally marked Epic 6 + 8 as DEFERRED to Phase 2+, but both are actually 100% complete per CLAUDE.md. **Decision (confirmed by Nissim 2026-05-15)**: included in scope via AC15. Top-level status banners and section headings flipped to ✅ DELIVERED; Epic 7 remains deferred (now to Phase 3, not "Phase 2+"). Screen-level wireframe details left untouched — a disclaimer banner is added directing readers to `_bmad-output/implementation-artifacts/` for authoritative implementation status. A separate screen-level audit is not part of this story.

5. **Story 9.1 implementation under `feature/speaker-account-creation` branch** — Per the readiness report Phase E cherry-pick risk: Story 9.1 (JWT magic link) was the first story of Epic 9 and is referenced as already deployed. The new Epic-9 rewrite (AC2) removes the Story 9.1 content from the PRD. **Inferred decision**: The PRD rewrite is "what we plan to do now" — the historical Story 9.1 implementation is recorded in git history and the prior PR; no need to preserve it in the PRD doc. Flag if a "historical record" subsection is desired.

---

_Story created via `bmad-create-story` skill on 2026-05-15. Authored by PM (Nissim) with comprehensive context-engine analysis. Ready for `bmad-dev-story` execution._
