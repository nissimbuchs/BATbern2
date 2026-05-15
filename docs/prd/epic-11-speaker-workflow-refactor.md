---
stepsCompleted: [step-01-validate-prerequisites, step-02-design-epics, step-03-create-stories, step-04-final-validation]
inputDocuments:
  - docs/plans/speaker-workflow-refactor.md
  - docs/architecture/ADR-009-unified-speaker-workflow.md
  - docs/prd/epic-9-speaker-authentication.md  # context — to be rewritten in Phase A
  - docs/architecture/06a-workflow-state-machines.md  # context — to be rewritten in Phase A
  - docs/architecture/03-data-architecture.md  # context — to be rewritten in Phase A
  - docs/architecture/04-api-design.md  # context — to be rewritten in Phase A
relatedADRs: [ADR-003, ADR-004, ADR-007, ADR-009]
---

# Epic 11: Unified Speaker Workflow Refactor

## Overview

This document decomposes the speaker-workflow refactor (per `docs/plans/speaker-workflow-refactor.md` and `ADR-009`) into implementable stories. The refactor unifies two parallel speaker-coordination flows (organizer-led, speaker-led) onto a single state machine, replaces three magic-link auth mechanisms with standard Cognito + forced password change, and deletes the redundant `Speaker` entity in favour of `User + SPEAKER role` per ADR-004.

**Scope boundary:** No new product capability is being added. Net feature surface stays the same; the *implementation* converges, the state model shrinks (12 → 8 states), and the auth model collapses (3 mechanisms → 1).

## Requirements Inventory

### Functional Requirements

These are the *behavioural* requirements the refactored system must satisfy. They are derived from the target model in §0 of the plan and the side-effect hooks in §2.3.

```
FR1:  Speaker workflow has exactly 8 states — IDENTIFIED, CONTACTED, READY, INVITED,
      ACCEPTED, CONTENT_SUBMITTED, QUALITY_REVIEWED, DECLINED — with DECLINED reachable
      from every non-terminal state.

FR2:  All mutations to `speaker_pool.status` go through `SpeakerWorkflowService.transition()`.
      No service may call `setStatus` directly.

FR3:  CONTACTED → READY is the speaker-provisioning gate. It REQUIRES `email` and triggers:
      User lookup-or-create, Cognito user provisioning with FORCE_CHANGE_PASSWORD,
      SPEAKER role grant, and persistence of `username` on `speaker_pool`. Atomic
      (all-or-nothing) and idempotent on retry.

FR4:  READY → INVITED is blocked when `count(ACCEPTED) + count(INVITED) >= max_slots`
      for the event. Rejection surfaces a clear domain error.

FR5:  Speakers respond only ACCEPT or DECLINE. TENTATIVE is removed from
      `SpeakerResponseType`, the API contract, and the database.

FR6:  A speaker who accepted and later drops out transitions to DECLINED. The previous
      state and reason are recorded in the status-history row. No separate WITHDREW state.

FR7:  Organizer-on-behalf and speaker-self content submission share a single backend
      service (`ContentSubmissionService`). Both flows perform: persist title/abstract
      to `content_submissions`; update `User.bio` / `User.profile_picture_url` via
      `UserApiClient` when CV/photo provided; trigger workflow transition to
      CONTENT_SUBMITTED. Audit trail records the authenticated principal in
      `changed_by_username`.

FR8:  Speaker portal endpoints (`/api/v1/speaker-portal/**`) require Cognito Bearer
      authentication with SPEAKER role. `permitAll()` is removed. No `?token=` /
      `?jwt=` query-parameter handling.

FR9:  Speaker first login uses standard Cognito FORCE_CHANGE_PASSWORD flow. The
      invitation email carries a portal login link and a generated temporary password.
      No Custom Auth Lambdas, no OTP.

FR10: Domain events emitted at state transitions: SpeakerPromotedToReadyEvent (new) at
      CONTACTED → READY; SpeakerInvitationSentEvent at READY → INVITED;
      SpeakerResponseReceivedEvent at INVITED → ACCEPTED|DECLINED;
      SpeakerAcceptedEvent at INVITED → ACCEPTED.

FR11: Derived flags computed (not persisted): `is_slot_assigned :=
      session.start_time IS NOT NULL`; `is_publishable := quality_reviewed AND
      slot_assigned`. `EventWorkflowStateMachine.validateAllSpeakersConfirmed` uses
      `is_publishable` over all accepted speakers as the gate for AGENDA_PUBLISHED.

FR12: Speaker pool entries in IDENTIFIED / CONTACTED have `username = NULL`. The email
      field is not accepted on `POST /events/{code}/speakers/pool` for these states.
      A new endpoint `POST /events/{code}/speakers/{speakerId}/promote` is the only
      path that sets `username`.

FR13: Existing legacy `speaker_pool.status` values are migrated per §2.2:
      SLOT_ASSIGNED → ACCEPTED (rely on derived `is_slot_assigned`);
      CONFIRMED → QUALITY_REVIEWED (rely on derived `is_publishable`);
      WITHDREW → DECLINED with reason "Withdrew after acceptance (legacy)";
      OVERFLOW → READY (organizer may re-invite if a slot opens).
```

### Non-Functional Requirements

```
NFR1: Clean cutover — no backward-compatibility shims for in-flight magic-link
      sessions. Per Confirmed Decision §6.4, there are no in-flight speakers.

NFR2: No new Cognito Lambda triggers required. Speaker provisioning uses
      `AdminCreateUser` + `AdminAddUserToGroup` + `AdminSetUserPassword` exclusively.

NFR3: Idempotency — speaker provisioning (`CONTACTED → READY`) is retry-safe. Re-running
      the transition for an already-provisioned speaker is a no-op, not an error.

NFR4: Audit-trail integrity — every state transition (including legacy migrations)
      produces a `speaker_status_history` row with `changed_by_username`, previous state,
      new state, and (for DECLINED) reason text. No silent mutations.

NFR5: Least-privilege IAM — the speaker-provisioning service principal is granted
      ONLY `cognito-idp:AdminCreateUser`, `AdminAddUserToGroup`, `AdminSetUserPassword`,
      `AdminInitiateAuth`, `AdminGetUser`. The temp-password encryption-key secret
      from `feature/epic-6` is intentionally NOT carried forward (passwords are
      embedded once in email, never stored).

NFR6: Test parity — every refactored state transition has a Testcontainers-backed
      integration test demonstrating the transition + side effects. Magic-link
      teardown stories include a test that verifies the deleted endpoints return 404.

NFR7: Documentation must be updated in the same phase as the code (Phase A docs land
      first, code Phases B–F land against the updated docs). Per CLAUDE.md doc-drift-
      prevention policy.

NFR8: No regression for non-speaker workflows. EventWorkflowStateMachine,
      partner-coordination, attendee-experience flows remain behaviourally identical.

NFR9: Cognito User Pool password policy must accept the backend-generated temporary
      password (length, character classes). Policy adjusted if necessary, not the
      generator.

NFR10: All 9 locales (de, en, fr, it, rm, es, fi, nl, ja) + gsw-BE updated for new
       state labels and UI copy. No locale lags behind.
```

### Additional Requirements (Architecture)

Derived from §2 (data-model + state-machine), §3 (per-service component changes), §4 (API surface), and §9 (branch strategy).

```
shared-kernel
- AR1:  SpeakerWorkflowState enum reduced to 8 values; SLOT_ASSIGNED, CONFIRMED,
        OVERFLOW, WITHDREW removed.
- AR2:  SpeakerResponseType.TENTATIVE removed.
- AR3:  New domain event SpeakerPromotedToReadyEvent added.

event-management-service
- AR4:  Delete StatusTransitionValidator (duplicate validator).
- AR5:  Refactor SpeakerWorkflowService into the single state-writer with side-effect
        hooks (provisioning on READY, slot-gate + email on INVITED, emails on ACCEPTED
        and DECLINED-from-INVITED+).
- AR6:  Refactor SpeakerStatusService.updateStatus to delegate to
        SpeakerWorkflowService; retain only history-row + cache-eviction.
- AR7:  Refactor SpeakerResponseService — Cognito principal instead of token; delegate
        state transition to SpeakerWorkflowService; delete processTentativeResponse.
- AR8:  Split SpeakerInvitationService into promote-to-READY (provisioning) and
        send-invitation (READY → INVITED + email).
- AR9:  Delete MagicLinkService, SpeakerPortalTokenController,
        SpeakerMagicLoginController, JwtConfig (speaker-JWT one).
- AR10: SpeakerPortalResponseController, SpeakerPortalContentController,
        SpeakerPortalMaterialsService become Cognito-secured, principal-driven.
- AR11: SpeakerPoolService.addSpeakerToPool tightens — no email accepted in
        IDENTIFIED / CONTACTED states.

company-user-management-service
- AR12: user_profiles is NOT extended. bio + profile_picture_url cover speaker CV +
        portrait per ADR-004.
- AR13: UserApiClient gains an idempotent "provision user with role and Cognito"
        operation, called at CONTACTED → READY.
- AR14: UserApiClient gains a "patch user bio / profile picture" operation accepting
        ORGANIZER or SPEAKER principal.
- AR15: Cognito provisioning logic added — AdminCreateUser with FORCE_CHANGE_PASSWORD.

speaker-coordination-service
- AR16: speakers table deleted. Per Confirmed Decision §6.2, the service is kept as a
        thin layer for future speaker-focused capabilities (not collapsed).

Database migrations (per §2.2 table)
- AR17: Drop `speakers` table (no backfill — legacy speaker-only columns intentionally
        dropped per §0.3).
- AR18: Migrate existing speaker_pool.status values per the mapping in FR13.
- AR19: Drop overflow tables: speaker_selection_votes and related artefacts.
- AR20: Drop columns speaker_pool.is_tentative, speaker_pool.tentative_reason.
- AR21: Drop magic_link_tokens table and related infrastructure.

API surface (§4)
- AR22: New POST /api/v1/events/{code}/speakers/{speakerId}/promote endpoint.
- AR23: Existing POST /events/{code}/speakers/pool tightened — reject email payload.
- AR24: PUT /events/{code}/speakers/{speakerId}/status tightened — restricted to 8
        states; SLOT_ASSIGNED / CONFIRMED / OVERFLOW / WITHDREW / TENTATIVE rejected;
        cannot reach READY (must use promote endpoint).
- AR25: Speaker-portal endpoints — Cognito Bearer auth; remove ?token= handling.
- AR26: Remove POST /api/v1/auth/speaker-magic-login.
- AR27: Remove POST /api/v1/speaker-portal/validate-token.
- AR28: Both content-submission endpoints (organizer + speaker) delegate to one
        ContentSubmissionService.

Infrastructure (CDK)
- AR29: App Client gains ALLOW_ADMIN_USER_PASSWORD_AUTH flow (cherry-picked from
        feature/epic-6 d5cf0fcc).
- AR30: IAM perms AdminCreateUser, AdminAddUserToGroup, AdminSetUserPassword,
        AdminInitiateAuth, AdminGetUser added to speaker-provisioning principal.
- AR31: Remove magic-link JWT key infrastructure (Secrets Manager entries, env-var
        wiring for speaker JWT).

Documentation alignment (Phase A, §1)
- AR32: Rewrite docs/prd/epic-9-speaker-authentication.md in place per §9.3.
- AR33: Rewrite docs/architecture/06a-workflow-state-machines.md speaker section.
- AR34: Delete Speaker entity section + speakers table SQL from
        docs/architecture/03-data-architecture.md.
- AR35: Update docs/architecture/04-api-design.md speaker-portal API spec.
- AR36: Update docs/architecture/06-backend-architecture.md auth section.
- AR37: Update docs/architecture/06b-user-lifecycle-sync.md (if relevant).
- AR38: Update docs/prd-enhanced.md FR2/FR3/FR17 to remove magic-link terminology.
- AR39: Update CLAUDE.md Epic 9 status line and speaker-workflow summary.
- AR40: Update ADR-004 with a note that SPEAKER role does not require additional User
        columns.

Branch strategy (§9)
- AR41: Cherry-pick Story 9.5 multi-role nav from feature/speaker-account-creation
        (NavigationMenu, AppHeader, MobileDrawer, UserMenuDropdown, navigationConfig,
        AuthContext multi-role additions, role-based tests, de/en common.json keys).
        Skip SpeakerLoginPage and ProtectedRoute speaker-JWT branch.
- AR42: Cherry-pick d5cf0fcc Story 7.1 Cognito IAM + auth flow from feature/epic-6.
        Skip the COGNITO_PASSWORD_ENCRYPTION_KEY secret.
- AR43: Delete feature/speaker-account-creation and feature/epic-6 branches after
        cherry-picks land.
```

### UX Design Requirements

Derived from §8 (Organizer Kanban UX) and §3.4 (web-frontend changes). Per §8.9, the architect has already proposed a 3-story breakdown for the kanban; UX-DRs preserve that granularity so each can map cleanly to a story.

```
Organizer kanban — card + cleanup (≈ §8.9 story 1)
- UX-DR1: Each speaker card gets a single state-aware primary-action button along
          the bottom edge (full-width). Labels per §8.2:
            IDENTIFIED          → "Log outreach"
            CONTACTED           → "Promote to speaker"
            READY               → "Send invitation"   (disabled when capacity reached)
            INVITED             → "View response status"
            ACCEPTED            → "Enter content"
            CONTENT_SUBMITTED   → "Review content"
            QUALITY_REVIEWED    → "Assign session slot"  OR  read-only "Publishable ✓"
            DECLINED            → "View details"
- UX-DR2: Time-in-state chip moved to organizer-row, right-aligned.
- UX-DR3: Existing "pending" indicator / badge removed from cards.
- UX-DR4: `⋯` secondary menu hosts less-common actions (reassign organizer, edit
          details, override state, decline with reason). Nice-to-have, not required
          for first iteration.

Organizer kanban — column triage + colour coding (≈ §8.9 story 2)
- UX-DR5: Column headers gain a "needs attention" sub-line:
            CONTACTED          → "⚠ N stale (>14 days)"
            READY              → "⚠ slot capacity reached" when capacity blocks invite
            INVITED            → "⏰ N approaching deadline", "⏰ N past deadline"
            ACCEPTED           → "📝 N awaiting content"
            CONTENT_SUBMITTED  → "👀 N awaiting moderator review"
            QUALITY_REVIEWED   → "🪑 N awaiting slot"
- UX-DR6: Click the sub-line to filter the column to the triggering subset.
- UX-DR7: Time-in-state chip colour-coded per state-specific thresholds (§8.7).
          Thresholds configurable in event settings; the table in §8.7 is the
          default set.

Organizer kanban — guided drag + drawer + slot-gate (≈ §8.9 story 3)
- UX-DR8:  Drag start — valid destination columns get a green halo; invalid
           columns are dimmed with a lock icon.
- UX-DR9:  Drop on invalid column — toast with state-machine explanation.
- UX-DR10: Drop on transition-with-input opens the same modal as the primary-action
           button, pre-filled where possible. Drag is a shortcut, not a separate
           code path.
- UX-DR11: Drop on DECLINED requires reason in confirmation modal.
- UX-DR12: "Send invitation" button + drag-to-INVITED disabled when slot capacity
           reached; tooltip explains; column header surfaces the same gate.
- UX-DR13: Detail drawer redesigned — primary-action button at top; secondary
           actions listed below; status + outreach history unified into one
           "History" panel; state-specific sub-tabs (Content, Materials, Notes).

Organizer drawer — on-behalf content form
- UX-DR14: Drawer exposes full content-submission form (title, abstract, optional
           CV → User.bio, optional portrait → User.profile_picture_url, optional
           presentation upload) when speaker is READY+. Functionally equivalent
           to the speaker-self portal form (same backend payload), but built as a
           separate React component in the organizer admin-app visual language.

Speaker brainstorming UI
- UX-DR15: No email field on the IDENTIFIED / CONTACTED form on the brainstorming
           panel.
- UX-DR16: "Promote to speaker" modal captures email (+ optional firstName /
           lastName) and triggers CONTACTED → READY via the new promote endpoint.

Speaker portal (public-website surface)
- UX-DR17: All web-frontend/src/pages/speaker/** pages use the standard Cognito
           session (same useAuth hook as the rest of the app). No ?token= or
           ?jwt= parsing/storage.
- UX-DR18: Delete SpeakerMagicLoginPage and magic-link entry-points.
- UX-DR19: "Mark as tentative" button + form path removed from the response page.

Cross-cutting frontend
- UX-DR20: Multi-role navigation cherry-picked from Story 9.5 (NavigationMenu,
           AppHeader, MobileDrawer, UserMenuDropdown, navigationConfig,
           AuthContext multi-role additions). Speaker portal accessible alongside
           organizer/attendee/partner portals for users with multiple roles.
- UX-DR21: Email templates updated: invitation email contains portal login link
           + generated temporary password. Confirmation, reminder, escalation
           templates simplified (no tentative-response language).
- UX-DR22: Translations for new state labels + UI copy added to all 10 locales
           (de, en, fr, it, rm, es, fi, nl, ja, gsw-BE).
- UX-DR23: Optional collapsible "What does this mean?" right-side sidebar with
           per-state guidance. Collapses to a thin tab once organizer is fluent.
```

### FR Coverage Map

All requirements roll up into a single Epic 11. The mapping below pins each FR to the
in-epic Phase cluster where it lands.

```
FR1  → Epic 11 / Phase B — Reduce SpeakerWorkflowState enum to 8 values
FR2  → Epic 11 / Phase B — SpeakerWorkflowService becomes sole status writer
FR3  → Epic 11 / Phase D (User+role) + Phase E (Cognito provisioning)
FR4  → Epic 11 / Phase D — Slot-capacity precondition on READY → INVITED
FR5  → Epic 11 / Phase B — Remove TENTATIVE response (enum, columns, UI, handler)
FR6  → Epic 11 / Phase B — Collapse WITHDREW into DECLINED with reason
FR7  → Epic 11 / Phase C (service consolidation) + Phase D (on-behalf drawer form)
FR8  → Epic 11 / Phase E — Speaker-portal endpoints require Cognito SPEAKER role
FR9  → Epic 11 / Phase E — Cognito FORCE_CHANGE_PASSWORD on first login
FR10 → Epic 11 / Phase B — Domain events incl. new SpeakerPromotedToReadyEvent
FR11 → Epic 11 / Phase B — Derived flags is_slot_assigned, is_publishable
FR12 → Epic 11 / Phase D — POST /speakers/{id}/promote is sole READY-setter
FR13 → Epic 11 / Phase C (data-migration mapping) + Phase F (cutover hygiene)
```

NFRs, ARs, and UX-DRs are covered by Epic 11 across phases A–F per the cluster
blocks in the Epic List section below.

## Epic List

### Epic 11: Unified Speaker Workflow Refactor

**Goal:** Converge two parallel speaker-coordination flows onto a single state machine
(8 states, single writer), replace three magic-link auth mechanisms with standard
Cognito + forced password change, and delete the redundant `Speaker` entity in favour
of `User + SPEAKER role` per ADR-004. Net product surface unchanged; risk reduction
and codebase simplification.

**FRs covered:**   FR1–FR13
**NFRs covered:**  NFR1–NFR10
**ARs covered:**   AR1–AR43
**UX-DRs covered:** UX-DR1–UX-DR23

**Story clustering follows Phases A–F from `docs/plans/speaker-workflow-refactor.md` §5.**
Stories within each phase will be drafted in step-03; the cluster targets below define
the requirements scope and the seams along which the cluster will split into stories.

```
Phase A — Documentation alignment
  Stories:          1 story (per Phase A decision: doc-only work lands as one PR)
  FR/AR targets:    AR32–AR40, NFR7
  Dependencies:     Independent. Do first so subsequent code reviews have a target.
  Output:           Rewrites of docs/prd-enhanced.md, docs/prd/epic-9-speaker-
                    authentication.md, docs/architecture/03-data-architecture.md,
                    docs/architecture/06a-workflow-state-machines.md,
                    docs/architecture/04-api-design.md,
                    docs/architecture/06-backend-architecture.md,
                    docs/architecture/06b-user-lifecycle-sync.md (if relevant),
                    docs/architecture/index.md, CLAUDE.md, plus ADR-004 note.

Phase B — State-machine consolidation
  Stories:          ~2–3 stories along natural seams (enum + validator delete /
                    workflow-service single-writer + side-effect hooks / derived
                    flags + legacy-status migration + API tighten).
  FR/AR targets:    FR1, FR2, FR5, FR6, FR10, FR11, AR1, AR2, AR3, AR4, AR5, AR6,
                    AR7, AR8, AR11, AR19, AR20, AR24, NFR6
  Dependencies:     Independent of Phase A. May begin in parallel.

Phase C — Entity model simplification
  Stories:          ~2 stories along natural seams (drop speakers table + verify
                    user_profiles needs no extension / UserApiClient provisioning
                    + patch operations / data migration of legacy speaker_pool
                    values per §2.2 mapping).
  FR/AR targets:    FR7, FR13 (data part), AR12, AR13, AR14, AR16, AR17, AR18, NFR4
  Dependencies:     Has DB migration; may run partly parallel with Phase B.
                    Migration drill + rollback plan required.

Phase D — Workflow semantics update + organizer UX
  Stories:          ~4 stories along natural seams (promote-to-READY endpoint
                    + slot-capacity gate + brainstorm-panel UI tightening /
                    organizer drawer on-behalf content form / §8.9 story 1 card
                    primary-action button + cleanup / §8.9 story 2 column
                    triage + time-in-state colour coding / §8.9 story 3
                    guided-drag + drawer + slot-gate).
                    Refinement during step-03 may split or merge these.
  FR/AR targets:    FR3 (User part), FR4, FR7 (drawer form), FR12, AR22, AR23,
                    UX-DR1–UX-DR16
  Dependencies:     Depends on Phase B (state-machine single-writer must exist).

Phase E — Cognito with forced password change
  Stories:          ~3 stories along natural seams (CDK + IAM prereq cherry-pick
                    AR42 / Cognito provisioning at READY transition + email
                    template update + locale work / speaker-portal endpoint auth
                    refactor + frontend Cognito session + multi-role nav AR41).
  FR/AR targets:    FR3 (Cognito part), FR8, FR9, AR15, AR25, AR29, AR30, AR41,
                    AR42, UX-DR17, UX-DR20, UX-DR21, UX-DR22, NFR2, NFR3, NFR5,
                    NFR9, NFR10
  Dependencies:     Depends on Phase C (entity model in place) and Phase D
                    (promote-to-READY endpoint exists). Cherry-pick AR42 (Cognito
                    IAM) lands at start of Phase E.

Phase F — Magic-link teardown
  Stories:          ~1–2 stories (backend service + endpoint + table deletes /
                    frontend ?token= handling + SpeakerMagicLoginPage delete +
                    tentative-button delete + Secrets Manager + branch deletion).
  FR/AR targets:    FR13 (cutover hygiene), AR9, AR10, AR21, AR26, AR27, AR31,
                    AR43, UX-DR18, UX-DR19, NFR1, NFR8
  Dependencies:     Depends on Phase E proving Cognito path stable in production.
                    Final cleanup, only after Phase E observation window.
```

**Total story estimate:** ~13–15 stories (Phase A: 1, Phase B: 2–3, Phase C: 2,
Phase D: ~4, Phase E: ~3, Phase F: 1–2). Final count confirmed in step-03.

---

## Epic 11 Stories

> **Note on framing.** This epic is a structural refactor, not a new product capability.
> Many stories serve a developer or maintainer audience (clean state model, single auth
> path, doc/code alignment) rather than an end-user audience. Where a story has direct
> end-user value (organizer kanban, speaker portal auth UX), the user perspective is
> primary. The "As a … I want … so that …" framing is honest about this — sometimes the
> beneficiary is the future maintainer of `SpeakerWorkflowService`, and that is OK.

---

### Phase A — Documentation alignment

---

#### Story 11.A.1: Align speaker-workflow documentation to ADR-009

**As a** developer reviewing or extending speaker-workflow code,
**I want** every PRD, architecture doc, and ADR-cross-reference to describe the unified
8-state Cognito-based model from ADR-009,
**So that** code reviews can verify code against a coherent target instead of three
conflicting documents.

**Phase:** A  ·  **Requirements covered:** AR32–AR40, NFR7  ·  **Dependencies:** none.

**Acceptance Criteria:**

**Given** the speaker-workflow refactor branch is checked out,
**When** I read `docs/prd-enhanced.md`,
**Then** the "Speaker Workflow (Parallel Per-Speaker Progression)" block in FR2 describes
the 8-state model from ADR-009 §0.1,
**And** FR3 and FR17 no longer mention "magic link" — they describe Cognito with forced
password change,
**And** no reference to `CONFIRMED`, `SLOT_ASSIGNED`, `OVERFLOW`, `WITHDREW`, or
`TENTATIVE` remains.

**Given** I read `docs/prd/epic-9-speaker-authentication.md`,
**Then** the file has been rewritten in place per the plan §9.3,
**And** the top of the file carries a `Supersedes prior Epic 9 plan per ADR-009` note,
**And** the new scope describes: speaker Cognito provisioning at `CONTACTED → READY`,
Cognito-secured speaker portal, multi-role navigation, magic-link teardown,
**And** the old Story 9.1–9.4 implementation stories are removed from the body.

**Given** I read `docs/architecture/03-data-architecture.md`,
**Then** the entire `Speaker` entity section is deleted,
**And** the `speakers` table SQL block under "Speaker Coordination Service Database
Schema" is deleted,
**And** the `User` section gains a paragraph explaining that SPEAKER is a role
(`user_roles.role = 'SPEAKER'`) and that `bio` / `profile_picture_url` cover the short
CV and portrait needs per ADR-004.

**Given** I read `docs/architecture/06a-workflow-state-machines.md`,
**Then** the "Speaker Workflow Management" section is rewritten with the new state list,
new transition diagram, the `CONTACTED → READY` provisioning-gate semantics, and a
"Derived flags" section for `slot_assigned` and `publishable`,
**And** the parallel-quality / slot-CONFIRMED auto-confirmation discussion is removed,
**And** the `SLOT_ASSIGNED` enum-rejection note is removed.

**Given** I read `docs/architecture/04-api-design.md`,
**Then** the speaker-portal API spec uses Cognito Bearer auth — no `?token=` query
parameters,
**And** `POST /api/v1/auth/speaker-magic-login` and
`POST /api/v1/speaker-portal/validate-token` are removed from the spec,
**And** the new `POST /api/v1/events/{code}/speakers/{speakerId}/promote` endpoint is
documented with a payload of `{ email, firstName?, lastName? }`.

**Given** I read `docs/architecture/06-backend-architecture.md`,
**Then** the auth section reflects a single Cognito flow for all roles (no dual-auth
model),
**And** I read `docs/architecture/06b-user-lifecycle-sync.md` (if it exists),
**Then** the new lifecycle is documented: User created at `CONTACTED → READY`, Cognito
user provisioned at the same moment, SPEAKER role granted.

**Given** I read `docs/architecture/ADR-004-factor-user-fields-from-domain-entities.md`,
**Then** an explicit note is appended: SPEAKER role requires no additional User columns;
`User.bio` serves as short CV and `User.profile_picture_url` serves as portrait.

**Given** I read `CLAUDE.md`,
**Then** the Epic 9 status line and speaker-workflow summary are updated to reflect the
ADR-009 model,
**And** the index references in `docs/architecture/index.md` are updated to point to
the rewritten files.

**Given** the PR is opened,
**Then** the commit message contains `[no-doc]` per the doc-drift policy (this PR is
the doc update itself),
**And** the PR description lists each updated file with a one-line "what changed"
summary so reviewers can verify coverage,
**And** no code files are modified in this PR.

---

### Phase B — State-machine consolidation

---

#### Story 11.B.1: Reduce `SpeakerWorkflowState` enum to 8 states; remove `TENTATIVE` response

**As a** developer working with the speaker workflow,
**I want** the shared-kernel enums to expose exactly the 8 states and 2 response types
that ADR-009 endorses,
**So that** downstream services cannot accidentally reference removed states and the
public API rejects deprecated values at the contract boundary.

**Phase:** B  ·  **Requirements covered:** FR1 (enum part), FR5 (enum part), FR10
(payload review), AR1, AR2, AR3, NFR6  ·  **Dependencies:** none — purely shared-kernel.

**Acceptance Criteria:**

**Given** the shared-kernel module is built,
**When** I inspect `SpeakerWorkflowState`,
**Then** the enum has exactly 8 values: `IDENTIFIED`, `CONTACTED`, `READY`, `INVITED`,
`ACCEPTED`, `CONTENT_SUBMITTED`, `QUALITY_REVIEWED`, `DECLINED`,
**And** `SLOT_ASSIGNED`, `CONFIRMED`, `OVERFLOW`, `WITHDREW` are absent,
**And** the Javadoc on `CONTACTED` describes "still brainstorming" semantics and the
Javadoc on `READY` describes "real speaker chosen — provisioning gate."

**Given** I inspect `SpeakerResponseType`,
**Then** the enum has exactly 2 values: `ACCEPT`, `DECLINE`,
**And** `TENTATIVE` is absent.

**Given** I search the shared-kernel `events` package,
**When** I look for speaker-related domain events,
**Then** `SpeakerInvitationSentEvent`, `SpeakerResponseReceivedEvent`,
`SpeakerAcceptedEvent` exist with payloads compatible with the new model,
**And** a new event `SpeakerPromotedToReadyEvent` exists with at minimum
`{ eventCode, speakerPoolId, username, promotedAt, promotedByUsername }`.

**Given** the shared-kernel is published to Maven Local,
**When** dependent services build,
**Then** any code path that references the four removed enum constants surfaces as a
compile error in a downstream service (signal that the next story has work to do),
**And** the shared-kernel test suite asserts the enum value count and event payload
shape via Testcontainers-backed JSON round-trip tests.

**Given** the shared-kernel changelog,
**Then** an entry documents the removed enum values and points to ADR-009.

---

#### Story 11.B.2: Make `SpeakerWorkflowService` the sole status writer with side-effect hooks

**As a** developer maintaining the speaker workflow,
**I want** every mutation to `speaker_pool.status` to flow through one validated entry
point with explicit side-effect hooks,
**So that** drift between three validators (`StatusTransitionValidator`,
`SpeakerWorkflowService.isValidTransition`, direct `setStatus` in `SpeakerResponseService`)
becomes structurally impossible.

**Phase:** B  ·  **Requirements covered:** FR2, FR6 (collapse logic), FR10 (event
emission), AR4, AR5, AR6, AR7 (partial — Tentative handler deletion + delegate), AR8
(partial — split logic), AR11, NFR4, NFR6  ·  **Dependencies:** Story 11.B.1
(shared-kernel enum reduction must be in place).

**Acceptance Criteria:**

**Given** the event-management-service is built,
**When** I search for classes that write to `speaker_pool.status`,
**Then** only `SpeakerWorkflowService.transition(speakerId, toState, actor, payload)`
performs the mutation,
**And** `StatusTransitionValidator` is deleted from the codebase,
**And** `SpeakerStatusService.updateStatus` delegates the state transition to
`SpeakerWorkflowService.transition()` and keeps only history-row writes + cache
eviction,
**And** `SpeakerResponseService.processAcceptResponse` and `processDeclineResponse`
delegate to `SpeakerWorkflowService.transition()` with the authenticated principal as
`actor` (no direct `setStatus`),
**And** `SpeakerResponseService.processTentativeResponse` is deleted.

**Given** I call `SpeakerWorkflowService.transition(speakerId, READY, actor, ...)` for a
speaker currently in `CONTACTED`,
**When** the transition runs,
**Then** the provisioning side-effect hook is invoked (lookup-or-create User + grant
SPEAKER role + capture username on `speaker_pool`) — Cognito provisioning is wired in
Phase E; this story stubs the hook so it can be implemented later without re-changing
`SpeakerWorkflowService`,
**And** a `SpeakerPromotedToReadyEvent` is published.

**Given** I call `SpeakerWorkflowService.transition(speakerId, INVITED, actor, ...)` for
a speaker in `READY`,
**When** the transition runs,
**Then** the slot-capacity precondition is evaluated and the transition fails with a
clear domain error if `count(ACCEPTED) + count(INVITED) >= max_slots` for the event,
**And** on success the invitation-email side-effect is invoked.

**Given** I call `SpeakerWorkflowService.transition(speakerId, DECLINED, actor,
{ reason })` from any non-terminal state,
**When** the transition runs,
**Then** a `speaker_status_history` row is written with the previous state, new state
(`DECLINED`), `changed_by_username = actor`, and `reason`,
**And** for transitions from `INVITED+` the "notify organizer" side-effect is invoked,
**And** for transitions from `IDENTIFIED`/`CONTACTED` no organizer notification fires
(lead simply didn't pan out).

**Given** I call `SpeakerWorkflowService.transition` for any state pair not in the
ADR-009 §0.2 allow-list,
**When** the transition runs,
**Then** it throws a domain exception with a message naming the source state, target
state, and the reason rejected,
**And** no row is written to `speaker_pool` or `speaker_status_history`.

**Given** Testcontainers PostgreSQL integration tests run,
**Then** every legal transition in ADR-009 §0.2 has a green-path test,
**And** every illegal transition has a rejection test,
**And** the `CONTACTED → READY` provisioning hook test asserts that the User-creation
seam is invoked even though Cognito provisioning is stubbed (Phase E will replace the
stub with the real call).

---

#### Story 11.B.3: Migrate legacy `speaker_pool.status` values; drop tentative columns; tighten status API; add derived flags

**As an** organizer with existing speaker_pool entries from before this refactor,
**I want** legacy state values to map cleanly into the 8-state model and `is_tentative`
data to drop without losing audit information,
**So that** no card in my kanban becomes orphaned or invisible after the migration
ships.

**Phase:** B  ·  **Requirements covered:** FR1 (legacy data part), FR5 (column drop),
FR6 (mapping), FR10, FR11, FR13 (mapping rules), AR11 (API tighten),
AR19 (overflow tables), AR20 (tentative columns), AR24 (PUT status API tighten), NFR4
 ·  **Dependencies:** Story 11.B.2 (single-writer service must exist so migrations
don't race against in-flight updates).

**Acceptance Criteria:**

**Given** a Flyway migration runs against a database containing legacy
`speaker_pool.status` values,
**When** the migration completes,
**Then** every row with `status = 'slot_assigned'` is updated to `status = 'accepted'`,
**And** every row with `status = 'confirmed'` is updated to `status = 'quality_reviewed'`,
**And** every row with `status = 'withdrew'` is updated to `status = 'declined'` and a
`speaker_status_history` row is inserted recording the previous state and the reason
`"Withdrew after acceptance (legacy)"`,
**And** every row with `status = 'overflow'` is updated to `status = 'ready'` (organizer
may re-invite if a slot opens),
**And** the migration is idempotent — re-running against an already-migrated database
is a no-op.

**Given** the same migration runs,
**When** it completes,
**Then** `speaker_pool.is_tentative` and `speaker_pool.tentative_reason` columns are
dropped,
**And** `speaker_selection_votes` table and any related overflow artefacts are dropped,
**And** the migration produces zero rows in any orphan-checking query against the new
8-state model.

**Given** the event-management-service exposes derived speaker fields,
**When** I query a speaker DTO,
**Then** `is_slot_assigned` is computed as `session.start_time IS NOT NULL` at read time
(no persisted column),
**And** `is_publishable` is computed as `status = 'quality_reviewed' AND is_slot_assigned`
at read time,
**And** `EventWorkflowStateMachine.validateAllSpeakersConfirmed` evaluates against
`is_publishable` over all accepted speakers (not against the removed `CONFIRMED` state)
as the precondition for `AGENDA_PUBLISHED`.

**Given** I `PUT /api/v1/events/{code}/speakers/{speakerId}/status` with one of
`SLOT_ASSIGNED`, `CONFIRMED`, `OVERFLOW`, `WITHDREW`, `TENTATIVE`,
**When** the request is processed,
**Then** the API returns `400 Bad Request` with a body identifying the rejected value
and the accepted set of 8 states,
**And** the speaker is not modified.

**Given** I `PUT /api/v1/events/{code}/speakers/{speakerId}/status` with `newStatus =
READY`,
**When** the request is processed,
**Then** the API returns `400 Bad Request` with a body explaining that `READY` is only
reachable via `POST /events/{code}/speakers/{speakerId}/promote` (because that endpoint
captures the required `email` payload).

**Given** I `PUT /api/v1/events/{code}/speakers/{speakerId}/status` with a legal status
value,
**When** the request is processed,
**Then** the controller delegates to `SpeakerWorkflowService.transition()` (does not
mutate `status` directly),
**And** the response reflects the post-transition derived flags.

---

### Phase C — Entity model simplification

---

#### Story 11.C.1: Drop the `speakers` table and remove speaker-coordination service references

**As a** developer maintaining the speaker domain,
**I want** a single source of truth for speaker identity (User + SPEAKER role),
**So that** the duplicated `Speaker` entity stops drifting from `user_profiles` and the
legacy speaker-only columns that nobody uses stop bloating the schema.

**Phase:** C  ·  **Requirements covered:** AR12 (verify user_profiles unchanged), AR16
(speaker-coordination remains thin), AR17 (drop table), NFR4  ·  **Dependencies:**
Stories 11.B.1–11.B.3 (state-machine refactor must be complete so deletes do not race
with workflow logic).

**Acceptance Criteria:**

**Given** a Flyway migration runs against a database with a populated `speakers` table,
**When** the migration completes,
**Then** the `speakers` table no longer exists,
**And** the legacy speaker-only columns (`availability`, `expertise_areas`,
`speaking_topics`, `languages_spoken`, `certifications`, `linkedin_url`,
`twitter_handle`, `speaking_history`, `communication_preferences`) are intentionally
**not** backfilled anywhere — they are dropped per ADR-009 §0.3,
**And** `user_profiles` is unchanged (no new columns added).

**Given** the speaker-coordination-service is built,
**When** I look at its package layout,
**Then** the `Speaker` JPA entity, `SpeakerRepository`, and any service code that
queried the `speakers` table are deleted,
**And** the service remains buildable and runnable as a thin shell (per Confirmed
Decision §6.2) for future speaker-focused read-side APIs,
**And** Bruno API contract tests in `bruno-tests/` for any deleted speaker-coordination
endpoints are removed from the collection.

**Given** the company-user-management-service is built,
**When** I inspect its code,
**Then** no `Speaker` entity, `SpeakerRepository`, or speaker-table references exist
inside this service (per AR15: this service holds Cognito provisioning logic only).

**Given** the event-management-service is built,
**When** I look at how speaker identity is referenced from `speaker_pool`,
**Then** `speaker_pool.username` (the meaningful cross-service ID per ADR-003) is the
sole reference,
**And** there is no foreign-key constraint to any service that previously held a
`speakers` table.

**Given** Testcontainers integration tests run for all three services,
**Then** all green — no test relies on the existence of a `Speaker` entity or `speakers`
table.

---

#### Story 11.C.2: `UserApiClient` provisioning + patch operations; `ContentSubmissionService` as shared write path

**As an** organizer entering content on behalf of a speaker (and as a speaker
submitting their own content),
**I want** both flows to land in the same backend service and produce identical
downstream effects,
**So that** the audit trail, validation rules, and side effects are consistent
regardless of which UI submitted the content.

**Phase:** C  ·  **Requirements covered:** FR7, AR13, AR14, NFR3 (idempotency), NFR4
 ·  **Dependencies:** Story 11.B.2 (workflow service must accept actor-driven
transitions).

**Acceptance Criteria:**

**Given** the event-management-service calls
`userApiClient.provisionUserWithRole(username, email, firstName, lastName, role)`,
**When** the user does not exist,
**Then** the company-user-management-service creates a `User` row,
**And** grants the requested role (`SPEAKER` in this story's use case),
**And** Cognito user creation is stubbed for Phase E to fill in (this story implements
the operation contract, not the Cognito call itself),
**And** the operation returns the canonical `username`.

**Given** the same call is repeated for an already-provisioned user,
**When** the operation runs,
**Then** it is a no-op (idempotent — NFR3),
**And** returns the same `username` without mutating User or role assignments.

**Given** an organizer or speaker submits CV text and/or a portrait image upload
reference via `ContentSubmissionService`,
**When** the service processes the submission,
**Then** it calls `userApiClient.patchUserProfile(username, { bio?, profilePictureUrl? })`,
**And** the company-user-management-service accepts the call from either an ORGANIZER
or SPEAKER principal (an organizer can update on behalf; a speaker can update their
own),
**And** `User.bio` and `User.profile_picture_url` are overwritten in place per
Confirmed Decision §6.7 (no per-event snapshot).

**Given** `ContentSubmissionService.submit(speakerPoolId, payload, principal)` is called,
**When** the call runs,
**Then** it persists `title` and `abstract` to `content_submissions`,
**And** calls `userApiClient.patchUserProfile` if `payload.bio` or
`payload.profilePictureUrl` are present,
**And** invokes `SpeakerWorkflowService.transition(speakerPoolId, CONTENT_SUBMITTED,
principal)`,
**And** the workflow service records `changed_by_username = principal.username` in
`speaker_status_history`,
**And** the same service is the only writer used by both
`POST /events/{code}/speakers/{speakerId}/content` (organizer-on-behalf, ORGANIZER auth)
and `POST /speaker-portal/content/submit` (speaker-self, SPEAKER auth).

**Given** the organizer endpoint is invoked with a SPEAKER token, or the speaker
endpoint is invoked with an ORGANIZER token,
**When** the request reaches the controller,
**Then** Spring Security rejects with `403 Forbidden`,
**And** no call reaches `ContentSubmissionService` (role separation per API §4).

**Given** Testcontainers integration tests run,
**Then** an organizer flow and a speaker flow both submit the same payload,
**And** the resulting database state (`content_submissions` row, `user_profiles`
update, `speaker_status_history` row, `SpeakerContentSubmittedEvent` emission) is
byte-identical except for `changed_by_username`.

---

### Phase D — Workflow semantics update + organizer UX

---

#### Story 11.D.1: Promote-to-READY endpoint + brainstorm-panel tightening + slot-capacity gate

**As an** organizer running the speaker-coordination kanban,
**I want** a clear "this is a real, invitable speaker" moment that captures the email
and provisions the User, **and** I want the system to stop me from inviting more
speakers than I have slots,
**So that** brainstorming and concrete-invitation states stay distinct and I never
oversubscribe.

**Phase:** D  ·  **Requirements covered:** FR3 (User part — Cognito comes in Phase E),
FR4, FR12, AR22, AR23, UX-DR15, UX-DR16  ·  **Dependencies:** Story 11.C.2
(`UserApiClient.provisionUserWithRole`), Story 11.B.2 (workflow service slot-capacity
hook).

**Acceptance Criteria:**

**Given** I `POST /api/v1/events/{code}/speakers/{speakerId}/promote` with
`{ email, firstName?, lastName? }` for a speaker currently in `CONTACTED`,
**When** the request is processed,
**Then** the workflow service runs `CONTACTED → READY` with `email` as a transition
payload,
**And** `UserApiClient.provisionUserWithRole` is called (User created if missing,
SPEAKER role granted),
**And** `speaker_pool.username` is populated with the returned username,
**And** the response returns `200 OK` with the updated speaker DTO (status `READY`,
populated `username`),
**And** a `SpeakerPromotedToReadyEvent` is emitted.

**Given** the same endpoint is called with no `email` in the body,
**When** the request is processed,
**Then** the API returns `400 Bad Request` identifying `email` as required,
**And** the speaker is not modified.

**Given** the same endpoint is called for a speaker already in `READY` or beyond,
**When** the request is processed,
**Then** the API returns `409 Conflict` with a body explaining the speaker has already
been promoted (and reporting the current state).

**Given** I `POST /api/v1/events/{code}/speakers/pool` with `{ status: 'identified',
email: '...' }` or `{ status: 'contacted', email: '...' }`,
**When** the request is processed,
**Then** the API returns `400 Bad Request` rejecting the `email` payload (per AR23),
**And** the suggested-action message in the body names the promote endpoint.

**Given** I `POST /api/v1/events/{code}/speakers/{speakerId}/invite` for a speaker in
`READY` when `count(ACCEPTED) + count(INVITED) >= max_slots` for the event,
**When** the request is processed,
**Then** the workflow service rejects the `READY → INVITED` transition with a domain
error,
**And** the API returns `409 Conflict` with a body that names the current acceptance +
invitation counts and the configured `max_slots`,
**And** the speaker is not modified, no email is sent.

**Given** I `POST .../invite` when capacity is available,
**When** the request is processed,
**Then** the transition succeeds, an invitation email is sent (with the existing
magic-link template — Phase E rewrites the email content), and the speaker moves to
`INVITED`.

**Given** the brainstorming panel in `web-frontend/src/pages/organizer/**`,
**When** I view the form for a speaker in `IDENTIFIED` or `CONTACTED`,
**Then** the email input is not rendered (per UX-DR15),
**And** the panel exposes a "Promote to speaker" button that opens a modal capturing
`{ email, firstName?, lastName? }` (per UX-DR16),
**And** on modal submit the frontend calls the promote endpoint and the card visually
moves to the `READY` lane on the kanban (no manual refresh).

**Given** Playwright `organizer` project tests run,
**Then** the brainstorming-to-promote flow has end-to-end coverage,
**And** the slot-capacity rejection produces the documented `409` response with the
expected message body.

---

#### Story 11.D.2: Kanban card primary-action button + cleanup

**As an** organizer scanning the kanban,
**I want** every speaker card to surface its single most likely next action as a
visible button,
**So that** I don't have to remember the workflow rules or open a drawer to act on a
card.

**Phase:** D  ·  **Requirements covered:** UX-DR1, UX-DR2, UX-DR3, UX-DR4 (the `⋯`
secondary menu — included as nice-to-have if scope permits)  ·  **Dependencies:** none
backend; depends on Story 11.D.1 for the promote-to-READY modal that the `CONTACTED`
button opens.

**Acceptance Criteria:**

**Given** the organizer kanban is rendered,
**When** I view a speaker card in `IDENTIFIED`,
**Then** the card has a single full-width primary-action button along its bottom edge
labelled "Log outreach",
**And** clicking it opens the outreach form modal,
**And** submitting transitions the speaker to `CONTACTED`.

**Given** the same card is in `CONTACTED`,
**Then** the button label is "Promote to speaker",
**And** clicking it opens the same promote modal from Story 11.D.1.

**Given** the same card is in `READY`,
**Then** the button label is "Send invitation",
**And** when slot capacity is reached the button is disabled with a tooltip ("Slot
capacity reached. N invitations outstanding + M acceptances for K slots. Wait or
decline an accepted speaker to free a slot."),
**And** the tooltip text is i18n-keyed across all 10 locales.

**Given** the same card is in `INVITED`,
**Then** the button label is "View response status",
**And** clicking it opens the drawer.

**Given** the same card is in `ACCEPTED`,
**Then** the button label is "Enter content",
**And** clicking it opens the on-behalf content form (delivered in Story 11.D.4 —
this story stubs the click handler so the button is wired but the modal opens an empty
shell until 11.D.4).

**Given** the same card is in `CONTENT_SUBMITTED`,
**Then** the button label is "Review content",
**And** clicking it opens the moderator review form.

**Given** the same card is in `QUALITY_REVIEWED`,
**Then** the button is "Assign session slot" if no slot is assigned,
**And** the button is a read-only "Publishable ✓" info-chip when a slot is assigned
(no click action).

**Given** the same card is in `DECLINED`,
**Then** the button label is "View details" and opens the drawer in read-only mode.

**Given** I look at any card across all states,
**Then** the existing "pending" indicator/badge is removed (per UX-DR3),
**And** the time-in-state chip is right-aligned on the organizer-row (per UX-DR2),
**And** existing relative-time formatting (already used elsewhere in the app) is
reused for the chip.

**Given** the card has any state's primary-action button,
**Then** a `⋯` secondary menu next to the button hosts (reassign organizer, edit
details, override state, decline with reason) when in `INVITED+` states — this menu is
nice-to-have for first iteration; story passes if the four actions remain reachable via
the existing drawer.

**Given** Playwright `organizer` project tests run,
**Then** primary-action click handlers for every state are covered,
**And** the disabled-state tooltip on `READY → INVITED` capacity-reached scenarios is
asserted.

---

#### Story 11.D.3: Kanban column triage + time-in-state colour coding

**As an** organizer running a backlog of brainstormed speakers,
**I want** column headers to surface what needs my attention and cards to age visibly,
**So that** I can triage the kanban at a glance instead of opening each card.

**Phase:** D  ·  **Requirements covered:** UX-DR5, UX-DR6, UX-DR7  ·
**Dependencies:** Story 11.D.2 (card + chip layout exists).

**Acceptance Criteria:**

**Given** the kanban renders any column,
**When** I look at the column header,
**Then** three lines are shown: state name, card count, "needs attention" sub-line
(per UX-DR5),
**And** the "needs attention" sub-line text is per the §8.3 table —
  - `IDENTIFIED`: count only (no sub-line),
  - `CONTACTED`: "⚠ N stale (>14 days)" when applicable,
  - `READY`: "⚠ slot capacity reached" when the slot-capacity gate fires,
  - `INVITED`: "⏰ N approaching deadline", "⏰ N past deadline",
  - `ACCEPTED`: "📝 N awaiting content",
  - `CONTENT_SUBMITTED`: "👀 N awaiting moderator review",
  - `QUALITY_REVIEWED`: "🪑 N awaiting slot",
  - `DECLINED`: count only.

**Given** the sub-line shows a non-zero count,
**When** I click the sub-line,
**Then** the column is filtered to the subset of cards that triggered the count
(per UX-DR6),
**And** clicking again removes the filter.

**Given** a card has been in its current state for the threshold time,
**When** I view the time-in-state chip,
**Then** the chip is rendered in yellow when the yellow threshold is crossed and red
when the red threshold is crossed,
**And** the thresholds match the §8.7 table by default —
  - `IDENTIFIED`: 30 / 60 days, `CONTACTED`: 7 / 14, `READY`: 3 / 7,
  - `INVITED`: response deadline − 3 / past response deadline,
  - `ACCEPTED`: 14 days without content / content deadline passed,
  - `CONTENT_SUBMITTED`: 3 / 7 days awaiting review,
  - `QUALITY_REVIEWED`: 30 days before event without slot / 14 days before event
    without slot,
  - `DECLINED`: no colour change (terminal),
**And** thresholds are read from event settings (overridable per event), defaulting
to the §8.7 values.

**Given** the column-header sub-line and the card chips share the same threshold logic,
**When** an organizer changes a threshold in event settings,
**Then** both the sub-line counts and the card chips update consistently on the next
render.

**Given** the aggregation can be derived from already-loaded card data in the page,
**When** the column header counts are computed,
**Then** the implementation prefers in-page derivation over a new aggregation endpoint
(per Architect §8.9 story-2 note); a read-only aggregation endpoint is added only if
in-page derivation is impractical (justify in the PR description).

**Given** Vitest + Playwright tests run,
**Then** sub-line render logic is unit-tested for each state,
**And** an end-to-end Playwright test asserts the click-to-filter and threshold-driven
colour changes.

---

#### Story 11.D.4: Kanban guided-drag + unified drawer (with on-behalf content form) + slot-gate UX

**As an** organizer who uses drag-drop and the detail drawer interchangeably,
**I want** drag-drop to refuse illegal transitions clearly, the drawer to expose every
action a card supports (including the full content-submission form when applicable),
and the slot-capacity gate to surface in every entry point,
**So that** the kanban gives me one mental model whether I click, drag, or open the
drawer.

**Phase:** D  ·  **Requirements covered:** FR7 (drawer surface for on-behalf), UX-DR8,
UX-DR9, UX-DR10, UX-DR11, UX-DR12, UX-DR13, UX-DR14  ·  **Dependencies:** Stories
11.D.1, 11.D.2, 11.D.3 (modals, buttons, and column-header gate must exist so drag and
drawer can reuse them); Story 11.C.2 (`ContentSubmissionService` shared write path).

**Acceptance Criteria:**

**Given** I start dragging a speaker card,
**When** the drag begins,
**Then** valid destination columns gain a green halo per the state-machine allow-list
in ADR-009 §0.2,
**And** invalid destination columns are dimmed and show a small lock icon (per
UX-DR8).

**Given** I drop a card on an invalid destination,
**When** the drop completes,
**Then** a toast appears with the state-machine explanation (per UX-DR9), e.g.
"`IDENTIFIED → ACCEPTED` not allowed — promote to `READY` first to provision the
speaker",
**And** the card visually returns to its origin column.

**Given** I drop a card on a destination that requires input (e.g. drag `CONTACTED →
READY`, drag `READY → INVITED`),
**When** the drop completes,
**Then** the same modal as the primary-action button opens, pre-filled where possible
(per UX-DR10),
**And** if the modal is cancelled, the card returns to its origin column,
**And** if the modal submits successfully, the card moves to the new column.

**Given** I drop a card on `DECLINED`,
**When** the drop completes,
**Then** a confirmation modal opens with a required reason text field (per UX-DR11),
**And** the transition only completes when the reason is provided and the modal
submitted; otherwise the card returns to origin.

**Given** I drop a `READY` card into `INVITED` when slot capacity is reached,
**When** the drop completes,
**Then** the same explanation surfaced by the disabled button and the column-header
gate is shown via a toast (per UX-DR12; consistent message text),
**And** the card returns to the `READY` column.

**Given** I click a card to open the detail drawer,
**When** the drawer renders,
**Then** the same primary-action button from the card is rendered at the top of the
drawer, larger and prominent (per UX-DR13),
**And** secondary actions (reassign organizer, edit details, override state, decline)
are listed below,
**And** the status history and outreach history are unified into one chronological
"History" panel,
**And** state-specific sub-tabs (Content, Materials, Notes) appear when relevant.

**Given** a card is in `READY`, `ACCEPTED`, `CONTENT_SUBMITTED`, or `QUALITY_REVIEWED`
and I open the drawer,
**When** I navigate to the Content sub-tab,
**Then** the full on-behalf content-submission form is rendered (title, abstract,
optional CV text → `User.bio`, optional portrait upload → `User.profile_picture_url`,
optional presentation upload) per UX-DR14,
**And** on submit the form calls the organizer-on-behalf endpoint, which delegates
to `ContentSubmissionService` (proves Story 11.C.2 wiring end-to-end),
**And** the form is **a distinct React component** from the speaker portal's content
form — different layout shell and chrome (per §0.4 shared-vs-not-shared boundary),
even if low-level design-system primitives (input components, validators) are reused.

**Given** Playwright `organizer` project tests run,
**Then** the green/dim drag affordance, invalid-drop toast, modal-on-drop pre-fill,
DECLINED reason requirement, slot-gate consistency message, and unified drawer history
are all covered,
**And** an organizer-on-behalf content-submission Playwright path asserts that
`content_submissions`, `user_profiles.bio`/`profile_picture_url`, and
`speaker_status_history.changed_by_username` are written exactly as in the speaker-self
flow (byte-identical except for `changed_by_username`).

---

### Phase E — Cognito with forced password change

---

#### Story 11.E.1: CDK + IAM prereq — App Client auth flow and Cognito admin permissions

**As a** security and platform maintainer,
**I want** the Cognito User Pool configured for admin-driven user creation and the
backend service principal granted exactly the IAM perms it needs,
**So that** Phase E provisioning can run with least privilege and the App Client
supports the `AdminInitiateAuth` flow used by the temporary-password login experience.

**Phase:** E  ·  **Requirements covered:** AR29, AR30, AR42 (cherry-pick Story 7.1 IAM
+ auth flow, skip encryption key), NFR2, NFR5  ·  **Dependencies:** none in the
refactor branch; depends on the `feature/epic-6` cherry-pick succeeding (`d5cf0fcc`).

**Acceptance Criteria:**

**Given** the `feature/epic-6` branch exists with commit `d5cf0fcc`,
**When** the cherry-pick is performed onto `feature/speaker-workflow-refactor`,
**Then** the App Client `ALLOW_ADMIN_USER_PASSWORD_AUTH` flow is enabled in CDK,
**And** the IAM perms `cognito-idp:AdminCreateUser`, `AdminAddUserToGroup`,
`AdminSetUserPassword`, `AdminInitiateAuth`, `AdminGetUser` are granted to the
event-management-service task-role principal,
**And** the related CDK unit tests from `d5cf0fcc` land on the branch and pass,
**And** the `COGNITO_PASSWORD_ENCRYPTION_KEY` secret is **not** cherry-picked (per
§9.2 reasoning — the temp password is generated, emailed once, never stored),
**And** the cherry-pick commit message notes the source commit and the deliberate
skip.

**Given** the CDK Cognito User Pool config is reviewed,
**When** I inspect the password policy,
**Then** the policy admits the backend-generated temporary password length and
character classes (per NFR9),
**And** if policy adjustment is required, it is made in CDK (not by weakening the
generator).

**Given** CDK `npm run test` runs in `infrastructure/`,
**Then** all infrastructure unit tests pass including the cherry-picked tests.

**Given** the `feature/epic-6` branch is reviewed after the cherry-pick,
**When** the team decides it is fully drained,
**Then** the branch is deleted (`git push origin --delete feature/epic-6`) — execution
of the delete is **not** part of this story's acceptance (the delete decision lives
with the team / Phase F operator); the cherry-pick coverage is what this story owns.

**Given** Bruno API contract tests run after deployment,
**Then** a smoke test in `bruno-tests/auth/` confirms `AdminInitiateAuth` succeeds with
a freshly-created admin-flow user (covered by an existing or newly-added test).

---

#### Story 11.E.2: Cognito provisioning at READY + invitation-email rewrite + 10-locale i18n

**As a** speaker who has just been promoted from "lead" to "real invitee",
**I want** to receive a clear invitation email with my login link and a temporary
password I can change on first login,
**So that** I can access the portal with a standard Cognito experience, no magic links.

**Phase:** E  ·  **Requirements covered:** FR3 (Cognito part), FR9, AR15 (CUMS
provisioning logic), UX-DR21 (email rewrite), UX-DR22 (i18n),
NFR3 (idempotency), NFR9 (password policy), NFR10 (locale parity)  ·  **Dependencies:**
Story 11.E.1 (IAM + App Client flow), Story 11.B.2 (provisioning hook seam in
workflow service), Story 11.C.2 (`UserApiClient.provisionUserWithRole` contract).

**Acceptance Criteria:**

**Given** the company-user-management-service receives a
`UserApiClient.provisionUserWithRole(username, email, firstName, lastName, role)` call
for a non-existent user,
**When** the call runs,
**Then** the service generates a strong random temporary password (meeting the User
Pool policy from Story 11.E.1),
**And** calls `cognito-idp:AdminCreateUser` with status `FORCE_CHANGE_PASSWORD` and
the temp password,
**And** calls `cognito-idp:AdminAddUserToGroup` (or equivalent role-grant) to assign
the SPEAKER role group,
**And** persists the User row,
**And** returns `{ username, temporaryPassword }` to the caller (the temp password is
returned **once** — never written to any local database or log; embedded in the
invitation email and immediately discarded from memory).

**Given** the same call is made for an already-provisioned user,
**When** the call runs,
**Then** the operation is a no-op (NFR3),
**And** returns `{ username, temporaryPassword: null }` (no new temp password — the
Cognito user already exists),
**And** the caller knows to NOT re-send an invitation email containing a credential.

**Given** the event-management-service runs the `CONTACTED → READY` transition via
`SpeakerWorkflowService`,
**When** the provisioning side-effect hook executes,
**Then** it calls `UserApiClient.provisionUserWithRole` and captures
`{ username, temporaryPassword }`,
**And** persists `username` on `speaker_pool`,
**And** dispatches a `SpeakerPromotedToReadyEvent`.

**Given** the `READY → INVITED` transition runs in the workflow service,
**When** the invitation-email side-effect executes,
**Then** the email body contains a portal login link, the speaker's email address as
the username, and the temporary password from provisioning,
**And** a short note explains "you will be asked to set your own password on first
login,"
**And** no `?token=` or `?jwt=` query parameter appears in the link,
**And** if the speaker was already provisioned (re-invite of an existing Cognito user),
the email omits the temporary-password line and includes a "use your existing password
or reset via the login page" note.

**Given** the email template is rendered,
**Then** all 10 locales (de, en, fr, it, rm, es, fi, nl, ja, gsw-BE) have the
rewritten invitation template,
**And** the confirmation, reminder, and escalation templates in all 10 locales are
simplified to drop tentative-response language (per UX-DR21).

**Given** Testcontainers integration tests run,
**Then** the provisioning hook end-to-end (Cognito `AdminCreateUser` mocked at the AWS
SDK boundary) is covered for both the create and idempotent-skip paths,
**And** an email-rendering test asserts the temp password and login link appear in the
right locale.

**Given** a real Cognito provisioning is run in the staging environment after deploy,
**When** a manual smoke test promotes a test speaker through the kanban,
**Then** the speaker receives the invitation email, logs in with the temp password, is
forced through password change on first login, and lands on the speaker portal home —
documented in the PR description as the manual verification step.

---

#### Story 11.E.3: Speaker-portal Cognito auth + frontend session refactor + multi-role nav cherry-pick

**As a** speaker (and as a speaker who is also an attendee or partner),
**I want** to access the speaker portal with the same Cognito session as the rest of
the app and navigate naturally between my multiple portals,
**So that** I never juggle magic-link tokens and the portal feels like part of one
coherent app.

**Phase:** E  ·  **Requirements covered:** FR8, AR10 (controllers Cognito-secured),
AR25 (speaker-portal auth changes), AR41 (multi-role nav cherry-pick), UX-DR17 (portal
pages on Cognito session), UX-DR20 (multi-role nav)  ·  **Dependencies:** Story 11.E.1
(App Client + IAM), Story 11.E.2 (provisioning gives speakers a real Cognito identity).

**Acceptance Criteria:**

**Given** the event-management-service builds,
**When** I inspect `SpeakerPortalResponseController`, `SpeakerPortalContentController`,
and `SpeakerPortalMaterialsService`,
**Then** every endpoint is annotated `@PreAuthorize("hasRole('SPEAKER')")`,
**And** no `permitAll()` remains on `/api/v1/speaker-portal/**`,
**And** no controller method accepts a `?token=` or `?jwt=` query parameter
(removed signatures, not just ignored params),
**And** the authenticated principal is read from `SecurityContextHolder` and forwarded
to `SpeakerWorkflowService.transition` / `ContentSubmissionService` as the `actor`.

**Given** I call any `/api/v1/speaker-portal/**` endpoint with no auth, an ORGANIZER
token, or a PARTNER token,
**When** the request is processed,
**Then** Spring Security returns `401` or `403` per the standard auth chain,
**And** no business logic is invoked.

**Given** I cherry-pick commit `73d94688` (Story 9.5 multi-role nav) and `396a9045`
(null-safe `user.roles` guard) from `feature/speaker-account-creation`,
**When** the cherry-pick lands,
**Then** the Material-UI components `NavigationMenu.tsx`, `AppHeader.tsx`,
`MobileDrawer.tsx`, `UserMenuDropdown.tsx`, the `navigationConfig.ts`, the
`AuthContext` multi-role-state additions, the role-based test files, and the
DE/EN `common.json` i18n keys are present,
**And** `SpeakerLoginPage.tsx` is **not** included (no separate speaker login under
ADR-009 — same Cognito login as everyone else),
**And** the `ProtectedRoute` speaker-JWT branch is **not** included (no speaker JWT
exists under ADR-009),
**And** the `feature/speaker-account-creation` branch is deleted after the cherry-pick
lands and is verified working.

**Given** all `web-frontend/src/pages/speaker/**` pages,
**When** I inspect their auth usage,
**Then** they consume the same `useAuth` hook as the rest of the app (Cognito session
from `aws-amplify`),
**And** no `?token=` or `?jwt=` parsing or local-storage / cookie token persistence
exists in the speaker pages,
**And** the `SpeakerMagicLoginPage` is removed from the route table (deletion of the
file itself is Phase F; this story disconnects it from the router).

**Given** a user with multiple roles (SPEAKER + ATTENDEE, or SPEAKER + ORGANIZER) is
logged in,
**When** they open the nav menu,
**Then** all portals they have access to appear as nav entries,
**And** they can switch between portals without re-authenticating.

**Given** Playwright tests run with the `speaker` project (activated by
`SPEAKER_AUTH_TOKEN` env var per CLAUDE.md auth setup),
**Then** speaker portal flows pass against the Cognito-only auth path,
**And** a multi-role test covers the cross-portal navigation case.

---

### Phase F — Magic-link teardown

---

#### Story 11.F.1: Magic-link teardown and branch deletion

**As a** security and platform maintainer,
**I want** every remnant of the magic-link auth model removed from the codebase,
infrastructure, and database,
**So that** no attack surface, dead code, or developer confusion outlives the Cognito
migration.

**Phase:** F  ·  **Requirements covered:** FR13 (cutover hygiene), AR9, AR10 (file
deletes — the auth annotations landed in 11.E.3; this story deletes the files),
AR21, AR26, AR27, AR31, AR43, UX-DR18, UX-DR19, NFR1, NFR8  ·  **Dependencies:**
Phase E fully landed and observed stable in production for an agreed window (team
decision). Pre-condition: zero traffic to the magic-link endpoints for at least 1 week
(verified by CloudWatch metrics).

**Acceptance Criteria:**

**Given** the event-management-service is built,
**When** I search for magic-link types in the codebase,
**Then** `MagicLinkService.java`, `JwtConfig.java` (the speaker-JWT one — confirmed
distinct from the API Gateway's general JWT validation config), `SpeakerMagicLoginController.java`,
and `SpeakerPortalTokenController.java` are deleted,
**And** any remaining `?token=` or `?jwt=` parameter handling in any service is
deleted.

**Given** I call `POST /api/v1/auth/speaker-magic-login` or
`POST /api/v1/speaker-portal/validate-token`,
**When** the request is processed,
**Then** the API gateway returns `404 Not Found` (route does not exist),
**And** a regression test asserts the 404 explicitly so the endpoints cannot be
silently revived.

**Given** a Flyway migration runs,
**When** the migration completes,
**Then** the `magic_link_tokens` table no longer exists,
**And** the `speaker_jwt` HTTP-only cookie is no longer set by any server response
(audited via Playwright network capture in a teardown test).

**Given** the web-frontend is built,
**When** I search for magic-link UI,
**Then** `SpeakerMagicLoginPage.tsx` is deleted,
**And** the magic-link-token parsing/storage code paths in any speaker-portal page are
gone (per UX-DR18),
**And** the "Mark as tentative" button and its form handler are deleted from the
speaker portal response page (per UX-DR19),
**And** the route table no longer contains a magic-login route.

**Given** the infrastructure CDK is built,
**When** I inspect Secrets Manager configuration,
**Then** the magic-link JWT key secrets and the env-var wiring for the speaker JWT are
removed (per AR31),
**And** any IAM policies or permission boundaries that referenced the magic-link auth
endpoints are cleaned up,
**And** CDK unit tests pass with no orphaned secret references.

**Given** the `feature/speaker-account-creation` and `feature/epic-6` branches
**When** all cherry-picks (from Stories 11.E.1 and 11.E.3) have landed and the
refactor branch is verified working,
**Then** both source branches are deleted from origin
(`git push origin --delete feature/speaker-account-creation`,
`git push origin --delete feature/epic-6`),
**And** the deletion is recorded in the PR description with the cherry-pick commit
SHAs that landed on the refactor branch.

**Given** a full Bruno + Playwright test run executes after deployment,
**Then** every test passes,
**And** the speaker-portal flow is exercised end-to-end via Cognito with no fallback
to magic-link auth at any point.

**Given** the team agrees Phase E has run stably for the agreed observation window,
**When** Phase F is merged,
**Then** the CLAUDE.md and any sprint-status YAML entries for Epic 11 are updated to
"done,"
**And** the speaker-workflow-refactor branch is merged to `develop` per the team's
merge policy (per-phase chunk merge or end-of-refactor merge, decided per §9.1).

---

## Coverage Verification

**FR Coverage** — all 13 FRs landed in stories:

```
FR1  → 11.B.1 (enum) + 11.B.3 (legacy data migration)
FR2  → 11.B.2 (single writer)
FR3  → 11.D.1 (User part) + 11.E.2 (Cognito part)
FR4  → 11.B.2 (slot-gate in workflow service) + 11.D.1 (API surface) +
       11.D.2/D.4 (UI surface)
FR5  → 11.B.1 (enum) + 11.B.3 (columns)
FR6  → 11.B.2 (workflow logic) + 11.B.3 (legacy data migration)
FR7  → 11.C.2 (shared write path) + 11.D.4 (drawer form)
FR8  → 11.E.3 (Cognito-secured endpoints)
FR9  → 11.E.2 (provisioning + first-login flow)
FR10 → 11.B.1 (event added) + 11.B.2 (event emission)
FR11 → 11.B.3 (derived flags)
FR12 → 11.D.1 (promote endpoint)
FR13 → 11.B.3 + 11.C.1 (data migration) + 11.F.1 (cutover hygiene)
```

**NFR Coverage** — every NFR has at least one story-level AC:
NFR1 (11.F.1), NFR2 (11.E.1), NFR3 (11.C.2, 11.E.2), NFR4 (11.B.2, 11.C.1, 11.C.2),
NFR5 (11.E.1), NFR6 (11.B.1, 11.B.2), NFR7 (11.A.1), NFR8 (11.F.1),
NFR9 (11.E.1, 11.E.2), NFR10 (11.E.2).

**AR Coverage** — all 43 ARs landed (verified by phase blocks above; AR41 + AR42
specifically land via cherry-pick stories 11.E.3 + 11.E.1).

**UX-DR Coverage** — all 23 UX-DRs landed:
DR1–DR4 (11.D.2), DR5–DR7 (11.D.3), DR8–DR14 (11.D.4), DR15–DR16 (11.D.1),
DR17 + DR20 (11.E.3), DR18–DR19 (11.F.1), DR21–DR22 (11.E.2), DR23 (optional, not
explicitly scoped to a story — flag for the team if you want it as a follow-up).


