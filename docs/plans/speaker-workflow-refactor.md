# Speaker Workflow Refactor — Architecture Plan

**Author:** Winston (Architect agent)
**Date:** 2026-05-15
**Status:** Draft — pending team review and Scrum Master story breakdown

## Context

The BATbern platform currently implements two parallel speaker coordination flows — an
organizer-led flow (manual data entry and status updates) and a speaker-led self-service
flow (magic-link authentication, accept/decline portal, content submission). An
architectural review of the code in `services/event-management-service/` and the
documentation in `docs/architecture/06a-workflow-state-machines.md` and
`docs/architecture/03-data-architecture.md` revealed deep structural problems:

- Three different state-machine validators coexist with conflicting rules
  (`StatusTransitionValidator`, `SpeakerWorkflowService.isValidTransition`, and the direct
  mutation paths in `SpeakerResponseService`).
- The speaker-led flow bypasses the state machine entirely (direct `setStatus` calls).
- Both flows reach the same state (`ACCEPTED`) with different side effects (User entity
  created in one path but not the other).
- A phantom `INVITED` state exists in the enum and one validator but not the other.
- A `TENTATIVE` response is encoded as side-channel boolean fields invisible to the state
  machine.
- A `Speaker` table holds a duplicate `workflowState` that drifts from
  `speaker_pool.status`.
- Three parallel auth/token mechanisms (`RESPOND` opaque tokens, `VIEW` opaque tokens,
  Story 9.1 JWT magic-login) layer on the speaker portal.
- Architecture documentation lists 10 speaker states; the enum has 12; the docs say
  `SLOT_ASSIGNED` is rejected while one validator accepts it.

This plan defines the corrected target architecture and the changes required to reach it.
Implementation details are intentionally left out; the Scrum Master will derive stories
from this document.

## 0. The Target Model

### 0.1 Speaker workflow states

| State | Meaning | `speaker_pool.username` | Cognito user |
|---|---|---|---|
| `IDENTIFIED` | Name on the brainstorm list. May be a candidate, a lead, or a contact the organizer plans to ask. | NULL | none |
| `CONTACTED` | Organizer is reaching out — to the candidate, to partners, to network contacts — to figure out who will actually speak. **Still brainstorming.** All conversations logged via `OutreachHistory`. | NULL | none |
| `READY` | The real speaker has been identified. Organizer has a name + email and has committed to inviting this specific person. **User provisioning happens at the transition into this state.** | populated | created if missing |
| `INVITED` | Formal invitation sent. Speaker can authenticate via Cognito passwordless. | populated | exists, SPEAKER role |
| `ACCEPTED` | Speaker committed via the portal. | populated | exists |
| `CONTENT_SUBMITTED` | Title + abstract submitted. | populated | exists |
| `QUALITY_REVIEWED` | Moderator approved content. **Terminal happy state.** | populated | exists |
| `DECLINED` | The single terminal "not happening" state. Reachable from **any** non-terminal state — covers a lead that didn't pan out (from `IDENTIFIED`/`CONTACTED`), a refusal to an invitation (from `INVITED`), and a speaker who accepted then dropped out (from `ACCEPTED`/`CONTENT_SUBMITTED`/`QUALITY_REVIEWED`). The status-history row records the previous state and reason. | NULL if from `IDENTIFIED`/`CONTACTED`, populated otherwise | maybe |

**Removed states:** `SLOT_ASSIGNED`, `CONFIRMED`. Both become **derived/computed**:

- `slot_assigned := session.start_time IS NOT NULL`
- `publishable := quality_reviewed AND slot_assigned`

### 0.2 Critical transition rules

- `CONTACTED → READY` is the **provisioning gate**. It REQUIRES `email` to be present,
  and triggers: User lookup-or-create + Cognito user provisioning + SPEAKER role grant +
  persisting `username` on `speaker_pool`.
- `IDENTIFIED → DECLINED` and `CONTACTED → DECLINED` are valid (lead didn't pan out — no
  real person ever existed to invite).
- `INVITED → ACCEPTED/DECLINED` happens via the speaker's Cognito-authenticated portal
  action.
- `ACCEPTED → DECLINED`, `CONTENT_SUBMITTED → DECLINED`, `QUALITY_REVIEWED → DECLINED`
  are all valid. A speaker who accepts and then drops out moves to `DECLINED` with a
  reason recorded in status history (e.g., "withdrew after acceptance — schedule
  conflict"). There is no separate `WITHDREW` state.
- **Slot-capacity gate at invitation.** `READY → INVITED` is blocked when
  `(count(ACCEPTED) + count(INVITED)) >= max_slots` for the event. Organizers cannot
  oversubscribe. If a slot opens up (someone declines), the next speaker in `READY` can
  be invited. There is no overflow / parking-lane state.
- No emails to anyone in `IDENTIFIED` or `CONTACTED`. The "send formal invitation" UI is
  disabled until the speaker is in `READY`.

### 0.3 Speaker as User (no `Speaker` table, no `user_profiles` extension)

- The `speakers` table is deleted.
- `user_profiles` is **NOT extended.** No new columns for speaker-specific attributes.
- The legacy `Speaker` attributes that were never essential — `availability`,
  `expertise_areas`, `speaking_topics`, `languages_spoken`, `certifications`,
  `linkedin_url`, `twitter_handle`, `speaking_history`, `communication_preferences` —
  are **dropped**, not migrated. They are not used by any production code path that the
  platform depends on, and the team has decided not to carry them forward.
- The few user-level attributes BATbern actually needs for a speaker are already on
  `user_profiles` per ADR-004:
  - **Short CV / bio** → existing `user_profiles.bio` column
  - **Speaker portrait** → existing `user_profiles.profile_picture_url` column
- "Is a speaker" is answered by `user_roles.role = 'SPEAKER'`.
- All per-event speaker data already lives on `speaker_pool` (slot preferences, content
  deadline, etc.) and `content_submissions` (title, abstract, quality-review feedback).
  Nothing moves there; the existing structure is sufficient.
- **Company logo** is not a speaker attribute. It lives on `companies` (managed via the
  generic logo upload service, ADR-002). The organizer or speaker submitting it goes
  through the Companies API, not the speaker workflow.

### 0.4 Two data-entry flows, one service layer

Both organizer-on-behalf and speaker-self are **first-class peers**. The fix is to make
them traverse the same service layer, not to eliminate either.

| Aspect | Organizer-on-behalf | Speaker-self |
|---|---|---|
| Authentication | ORGANIZER Cognito session | SPEAKER Cognito session (passwordless) |
| Entry point | Organizer event-management UI (Kanban, drawer, on-behalf form) | Speaker portal pages |
| Data captured | title, abstract, optional bio/CV, optional portrait, optional presentation, optional company logo | same |
| Audit trail | `changed_by_username` = the organizer | `changed_by_username` = the speaker |
| State transitions | All go through `SpeakerWorkflowService.transition()` | All go through `SpeakerWorkflowService.transition()` |
| Side effects (emails, history rows, domain events) | Identical | Identical |

The two flows differ in **who the authenticated principal is** and **which UI surface
the request originated from**. Backend logic — state machine, validators, side-effect
hooks, persistence — is shared. Organizers can complete the entire workflow for a
speaker without the speaker ever logging in. Conversely, speakers can complete the entire
workflow themselves without organizer intervention after `INVITED`. The two paths
converge on the same data and the same audit trail.

**Crucial architectural point — what "shared" means and does not mean.** The two flows
share the *backend*, not the *frontend*:

- **Shared:** the service layer (`ContentSubmissionService`, `SpeakerWorkflowService`),
  the data model, the API endpoint **shape** (the two endpoints accept equivalent payloads),
  the validation rules, the domain events, the audit-trail rules. At most, the frontend
  may share **low-level design-system primitives** (input components, validators) at the
  component-library level.
- **Not shared:** the high-level UI components and the visual chrome. The organizer
  surface is an admin app in Material-UI — a slide-in drawer on the kanban with dense
  organizer-app controls. The speaker surface is a public-website-styled set of
  dedicated screens — different layout shell, different navigation, different feel.
  The organizer "Enter content" drawer-form and the speaker `/speaker-portal/content`
  page are **two distinct React components**, independently styled, independently
  routed. They submit equivalent payloads to the same backend, but they are built and
  maintained as separate UI surfaces.

This separation is intentional. The organizer is doing dense admin work in a workflow
context; the speaker is a guest visiting a public-feeling portal once. Forcing them
into one UI would compromise both.

**Implication for User provisioning:** the `CONTACTED → READY` gate still creates a
Cognito user (because we still email the speaker for invitations, reminders, and
confirmations even if the organizer enters all the data). The speaker simply may never
exercise the login. The Cognito user is provisioned regardless.

### 0.5 Authentication

- Magic-link auth (RESPOND / VIEW / JWT-login) is replaced by **standard Cognito
  registration with a forced password change on first login.** This is the simplest
  off-the-shelf Cognito flow — no custom-auth Lambdas, no OTP infrastructure.
- The Cognito user is created at `CONTACTED → READY`, before any email goes out. The
  backend generates a strong random temporary password and assigns the user status
  `FORCE_CHANGE_PASSWORD`. The SPEAKER role is granted at the same time.
- The invitation email contains:
  - A link to the speaker portal login page.
  - The temporary password.
  - A short note explaining that the speaker will be prompted to set their own password
    on first login.
- On first login the speaker enters email + temporary password; Cognito challenges them
  to set a new password; from that point on they log in like any other Cognito user.
- All `/api/v1/speaker-portal/**` endpoints are `@PreAuthorize("hasRole('SPEAKER')")`;
  `permitAll()` is removed.

This variant trades the "click-only" UX of an OTP magic link for a slightly heavier
flow (the speaker types a one-time password), in exchange for radically simpler
infrastructure — no Custom Auth Challenge Lambdas, no second email, fully covered by
existing Cognito tooling and documentation. For an audience of professionals who already
expect this pattern (it's how most enterprise SaaS invites a new user), the tradeoff is
worthwhile.

### 0.6 TENTATIVE response removed

Speakers respond **ACCEPT** or **DECLINE**. The `TENTATIVE` response that today is
encoded as side-channel boolean fields (`is_tentative`, `tentative_reason`) on
`speaker_pool` is removed in its entirety:

- `SpeakerResponseType` enum value `TENTATIVE` is removed from the shared kernel and
  API contract.
- `SpeakerResponseService.processTentativeResponse` is deleted.
- `speaker_pool.is_tentative` and `speaker_pool.tentative_reason` columns are dropped.
- The "Mark as tentative" button is removed from the speaker portal UI.
- Email templates that mention tentative response are simplified.

A speaker who is unsure simply doesn't respond yet; the existing reminder / escalation
machinery already handles delayed responses. A speaker who has already pressed ACCEPT
but later changes their mind transitions through `DECLINED` (see §0.7).

### 0.7 OVERFLOW and WITHDREW states removed

The workflow has a single terminal "not happening" state: `DECLINED`. The two
state-machine artefacts that previously represented variations on this — `OVERFLOW`
("accepted but no slot available") and `WITHDREW` ("accepted but dropped out later") —
are both removed:

- **`SpeakerWorkflowState.OVERFLOW`** is dropped from the enum and from all
  transitions. Capacity is now controlled at the invitation step (see §0.2's slot-
  capacity gate). Organizers can never invite more speakers than slots, so an
  "overflow parking lane" is unnecessary. If too many speakers accept (e.g., because
  capacity was reduced after invitations went out), the organizer manually moves the
  excess to `DECLINED` with a clear reason.
- **`SpeakerWorkflowState.WITHDREW`** is collapsed into `DECLINED`. A speaker who
  accepts and later drops out is transitioned to `DECLINED` with a reason recorded
  in the status-history row. The audit trail (previous state = `ACCEPTED`/`CONTENT_SUBMITTED`/`QUALITY_REVIEWED`,
  new state = `DECLINED`, reason = free-text) preserves exactly the information that
  a separate `WITHDREW` state used to encode.
- `OverflowManagementService`, `SpeakerSelectionVoteRepository`, overflow voting tables,
  and any related UI are removed. (Overflow management was already deprecated from MVP
  scope per the architecture doc; this finishes the cleanup.)

Net effect: the speaker workflow shrinks to **8 states** —
`IDENTIFIED → CONTACTED → READY → INVITED → ACCEPTED → CONTENT_SUBMITTED → QUALITY_REVIEWED`,
plus `DECLINED` reachable from any non-terminal state.

## 1. Documentation Changes

| Doc | Change |
|---|---|
| `docs/prd-enhanced.md` | Rewrite the "Speaker Workflow (Parallel Per-Speaker Progression)" block in FR2. Update FR3 (automated invitation) and FR17 (intelligent speaker matching) to remove "magic link" terminology in favor of "Cognito with forced password change." Remove `CONFIRMED` and `SLOT_ASSIGNED` from any state lists. |
| `docs/prd/epic-9-speaker-authentication.md` | **Rewrite in place** against ADR-009. The Epic 9 goal (unified Cognito identity for speakers who are also attendees, zero duplicate accounts) survives; the implementation stories (9.1 JWT magic link, 9.2 on-acceptance account creation, 9.3 dual auth, 9.4 Epic 6 migration) are obsolete. New scope: speaker Cognito provisioning at `CONTACTED → READY`, Cognito-secured speaker portal, multi-role navigation, magic-link teardown. Add a "Supersedes prior Epic 9 plan per ADR-009" note at the top. See §9.3 for the rewrite scope; new story shapes are for Product to write. |
| `docs/architecture/03-data-architecture.md` | **Delete** the entire `Speaker` entity section. Delete the `speakers` table SQL block in "Speaker Coordination Service Database Schema." Add a paragraph in the `User` section explaining that SPEAKER is a role (`user_roles.role = 'SPEAKER'`) and that the only user-level attributes speakers need (`bio` for short CV, `profile_picture_url` for portrait) are existing User fields per ADR-004 — no schema extension required. |
| `docs/architecture/06a-workflow-state-machines.md` | Rewrite the "Speaker Workflow Management" section with the new state list, new transition diagram, new semantics for `CONTACTED`, and the explicit `CONTACTED → READY` provisioning gate. Delete the parallel-quality/slot-CONFIRMED auto-confirmation discussion. Add a "Derived flags" section for `slot_assigned` and `publishable`. Remove the `SLOT_ASSIGNED` enum-rejection note (it's gone entirely). |
| `docs/architecture/04-api-design.md` | Update the speaker-portal API spec: remove `?token=` query parameters; document Cognito Bearer auth. Remove `POST /api/v1/auth/speaker-magic-login` and `POST /api/v1/speaker-portal/validate-token`. Add or repurpose an endpoint to perform the `CONTACTED → READY` promotion (with `email` payload). |
| `docs/architecture/06-backend-architecture.md` | Update auth section: remove the dual-auth model. Speaker portal now uses the same Cognito flow as other roles, with the passwordless variant. |
| `docs/architecture/06b-user-lifecycle-sync.md` *(if relevant)* | Document the new lifecycle: User created at `CONTACTED → READY`, Cognito user provisioned at the same moment, SPEAKER role granted. |
| `docs/architecture/index.md` | Update any index references. |
| **New ADR** `docs/architecture/ADR-009-unified-speaker-workflow.md` | Single ADR codifying the unified speaker-workflow architecture. Covers: (a) `SpeakerWorkflowService.transition()` is the sole writer of `speaker_pool.status`; (b) speaker is a User with the SPEAKER role, not a separate entity (no `speakers` table); (c) speaker authentication uses standard Cognito registration with a forced password change on first login, replacing all magic-link mechanisms. Other docs cite this ADR. |
| **Update ADR-004** | Add a note: SPEAKER role does not require additional User columns; `User.bio` serves as short CV, `User.profile_picture_url` serves as speaker portrait. |
| **Supersede / deprecate** Epic 6 and Story 9.1 docs that document the magic-link flow | Add a deprecation banner pointing to ADR-009. |
| **Refile Epic 9 stories 9.2–9.5** | They were planned as "dual-auth migration." Replace with: "Provision Cognito user at READY transition," "Backfill Cognito users for existing speakers," "Frontend Cognito-only speaker portal," and a deprecated-magic-link-removal story. |
| `CLAUDE.md` | Update the "Epic 9" status line and the speaker-workflow summary. |

## 2. Architecture / Data-Model Changes

### 2.1 `shared-kernel`

- `SpeakerWorkflowState` enum: remove `SLOT_ASSIGNED`, `CONFIRMED`, `OVERFLOW`,
  `WITHDREW`. The remaining 8 states are `IDENTIFIED`, `CONTACTED`, `READY`, `INVITED`,
  `ACCEPTED`, `CONTENT_SUBMITTED`, `QUALITY_REVIEWED`, `DECLINED`. Javadoc updated to
  describe the new semantics of `CONTACTED` (still brainstorming) and `READY` (real
  speaker chosen).
- Domain events: review `SpeakerInvitationSentEvent`, `SpeakerResponseReceivedEvent`,
  `SpeakerAcceptedEvent`. Names stay; payloads may need adjustment now that User
  provisioning is upstream. Add a new event `SpeakerPromotedToReadyEvent` to signal the
  provisioning moment to other services.

### 2.2 Data-model migrations

No backward-compatibility migration of in-flight speakers is required — there are no
in-flight magic-link sessions to preserve. Cutover is therefore a clean swap.

> **Implementation status:** the "Drop `speakers` table" row below was implemented in
> Story 11.C.1 (`V94__drop_speakers_table.sql`). The "Migrate `speaker_pool.status`"
> + "Drop overflow tables" + "Drop `is_tentative` columns" rows were implemented in
> Story 11.B.3 (`V93__migrate_legacy_speaker_states.sql`). Magic-link tables and
> Cognito configuration remain Phase E / F.

| Migration | Purpose |
|---|---|
| Drop `speakers` table | No backfill into `user_profiles`. The legacy speaker-only attributes (linkedin, expertise, etc.) are intentionally dropped — they are not used by production code paths the platform depends on. `user_profiles.bio` and `user_profiles.profile_picture_url` already exist and cover short CV and portrait. |
| Migrate existing `speaker_pool.status` values | Map removed states to the new model: `SLOT_ASSIGNED → ACCEPTED` (rely on derived `slot_assigned`); `CONFIRMED → QUALITY_REVIEWED` (rely on derived `publishable`); `WITHDREW → DECLINED` with reason backfilled as "Withdrew after acceptance (legacy)"; `OVERFLOW → READY` so the organizer can choose to re-invite if a slot opens. |
| Drop overflow-management tables | `speaker_selection_votes`, related artefacts — overflow workflow is removed entirely (§0.7) |
| Drop `speaker_pool.is_tentative` and `speaker_pool.tentative_reason` columns | TENTATIVE response is removed from the model entirely (see §0.6). |
| Drop magic-link tables | `magic_link_tokens` and associated infrastructure |
| Cognito User Pool config | No new Lambda triggers required. Standard Cognito flow with `FORCE_CHANGE_PASSWORD` on user creation is sufficient. |

### 2.3 State-machine consolidation

- **Delete** `StatusTransitionValidator` (the duplicate validator in
  `event-management-service`).
- `SpeakerWorkflowService.transition(speakerId, toState, actor, payload)` becomes the
  single entry point. Every state mutation goes through it. The validator returns to a
  single allow-list of transitions.
- `SpeakerWorkflowService` gains transition **side-effect hooks** (kept in the service,
  not in controllers):
  - On `CONTACTED → READY`: provisioning (User lookup-or-create + Cognito provisioning +
    SPEAKER role grant).
  - On `READY → INVITED`: **precondition** — slot-capacity check (`accepted + invited < max_slots`); reject the transition with a clear error if violated. **Action** — send invitation email.
  - On `INVITED → ACCEPTED`: send confirmation email.
  - On `... → DECLINED` from `INVITED+`: notify organizer.
  - No overflow side-effect — overflow detection is removed (§0.7).

### 2.4 Removal of asymmetric paths

- `SpeakerResponseService` no longer mutates `speaker_pool.status` directly. It only
  calls `SpeakerWorkflowService.transition(speakerId, ACCEPTED|DECLINED, principal)`. All
  entity-creation side effects (User, Cognito) are already done by the time the speaker
  can respond, because `READY` happened upstream.
- `SpeakerStatusService.updateStatus` likewise delegates the state change to
  `SpeakerWorkflowService` and keeps only the history-row and cache-eviction
  responsibilities.

### 2.5 Derived flags

- `is_slot_assigned` and `is_publishable` are exposed via repository projection or DTO
  computation. No persisted columns.
- `EventWorkflowStateMachine.validateAllSpeakersConfirmed` (the gate for
  `AGENDA_PUBLISHED`) changes its predicate to "all accepted speakers are publishable."

## 3. Component Changes by Service

### 3.1 `event-management-service`

> **Story 11.C.1 note:** Story 10.20's legacy BAT-format export/import
> (`LegacyExportService`, `LegacyImportService`, `AdminExportImportController`,
> `dto/export/*`) is retired alongside the `Speaker` entity. The one-shot
> historical import has already run against production and is not re-executable
> against a database that lacks `speakers`. No further re-execution path is
> required.

| Change | Component |
|---|---|
| Delete | `StatusTransitionValidator`, `MagicLinkService`, `SpeakerPortalTokenController`, `SpeakerMagicLoginController`, `JwtConfig` (the speaker-JWT one) |
| Refactor | `SpeakerWorkflowService` — single writer, with side-effect hooks |
| Refactor | `SpeakerStatusService.updateStatus` — delegate to workflow service |
| Refactor | `SpeakerResponseService` — Cognito principal instead of token; delegate to workflow service. **Delete** the `processTentativeResponse` branch entirely (see §0.6). |
| Refactor | `SpeakerInvitationService` — split into "promote to READY" (provisioning) and "send invitation" (transitions `READY → INVITED`, sends email). Currently both happen in `sendInvitation()`. |
| Refactor | `SpeakerInvitationEmailService`, `SpeakerAcceptanceEmailService` — emails now embed a Cognito-OTP-login URL, not a token query param |
| Refactor | `SpeakerPortalResponseController`, `SpeakerPortalContentController`, `SpeakerPortalMaterialsService` — Cognito-secured, principal-driven |
| Keep | `SpeakerOutreachService`, `OutreachHistory` — heart of the `CONTACTED` phase, used heavily |
| Keep | `SpeakerPoolService` — but `addSpeakerToPool` semantics tighten: no `email` accepted in `IDENTIFIED` / `CONTACTED`, only in `promote-to-READY` |
| Refactor | `ContentSubmissionService` — promoted to **the** shared write-path for content. Called from both the organizer-on-behalf endpoint and the speaker-self endpoint. Orchestrates: persist title/abstract to `content_submissions`; update `User.bio` and `User.profile_picture_url` via `UserApiClient` if CV/photo provided; trigger workflow transition to `CONTENT_SUBMITTED`. The audit trail records the authenticated principal (organizer or speaker) so both flows leave a clear paper trail. |

### 3.2 `company-user-management-service`

| Change | Component |
|---|---|
| No schema change | `user_profiles` is **not** extended. Existing columns (`bio`, `profile_picture_url`) cover the short-CV and portrait needs for speakers. |
| Extend | `UserApiClient` (and the underlying service) with a "provision user with role and Cognito" operation, idempotent, called from `event-management-service` at the `CONTACTED → READY` transition |
| Extend | `UserApiClient` with a "patch user bio / profile picture" operation invoked by `ContentSubmissionService` when an organizer or speaker submits CV/photo as part of content submission. The endpoint must accept either ORGANIZER or SPEAKER principals (an organizer can update on behalf; a speaker can update their own). |
| Delete | Any local `Speaker` entity, `SpeakerRepository`, or speaker-table references |
| Add | Cognito provisioning logic — create user with custom-auth-only flag, no password |

### 3.3 `speaker-coordination-service`

- The main artifact owned by this service is the `speakers` table. With it deleted,
  there is little left. Recommend folding remaining endpoints into
  `event-management-service` (which already owns `speaker_pool`). **Open decision** —
  left to the team; the plan works either way.

### 3.4 `web-frontend`

| Change | Area |
|---|---|
| Delete | `SpeakerMagicLoginPage`, magic-link token parsing/storage, the `?token=` query-param handling on all speaker portal pages |
| Refactor | All `web-frontend/src/pages/speaker/**` to use the standard Cognito session (same `useAuth` hook as the rest of the app) |
| Refactor | Kanban / status-lanes UI: relabel `CONTACTED` lane as "Brainstorming / Outreach" with sub-info ("conversations logged: N"); add `READY` lane with prominent "Send formal invitation" button |
| Refactor | Speaker brainstorming panel: no email field on the `IDENTIFIED` / `CONTACTED` form. Add a "Promote to speaker" modal that captures email + triggers `CONTACTED → READY` |
| Refactor | Disable the "send invitation" action on any speaker not in `READY` |
| Delete | The "Mark as tentative" button and form path on the speaker portal response page (see §0.6) |
| Refactor | **Organizer drawer** (`web-frontend/src/pages/organizer/**`, Material-UI slide-in on the kanban): surface "lead vs real speaker" status clearly; outreach history is the primary panel during `CONTACTED`. Once the speaker is in `READY` or beyond, the drawer must expose the full content-submission form (title, abstract, CV, portrait, presentation upload) so the organizer can complete every field on behalf of the speaker without leaving the page. **Functionally equivalent** to the speaker-self portal form, but built as a separate React component in the organizer admin-app visual language. See §0.4 for the shared-vs-not-shared boundary. |
| Keep & refactor | **Speaker portal pages** (`web-frontend/src/pages/speaker/**`, public-website look and feel): remain the speaker-self entry point. They are independent React components from the organizer drawer — different layout shell, different chrome, different navigation. They use Cognito auth (no more `?token=` params) and call the same backend endpoints described in §4. |
| Decide (team) | How much to share between the organizer drawer-form and the speaker portal-form at the component level. Two viable approaches: (a) a single design-system "ContentSubmissionForm" primitive that is restyled per surface (DRY but requires careful design-system work); (b) two independent forms that share only field-level primitives (less DRY, more flexibility). Either is fine. This is not a backend decision and does not block the rest of the plan. |
| Update | Translations across all 9 locales for new state labels and UI copy |

### 3.5 `infrastructure` (CDK)

- **No new Cognito Lambda triggers required.** The standard Cognito flow with
  `AdminCreateUser` and `FORCE_CHANGE_PASSWORD` covers the entire speaker onboarding UX.
- Confirm the Cognito User Pool's password policy is acceptable for the temporary
  password the backend will generate (length, character classes). Adjust if needed.
- Add backend IAM permissions for the speaker provisioning service (company-user-
  management-service task role) to call `cognito-idp:AdminCreateUser`,
  `AdminSetUserPassword`, `AdminInitiateAuth`, `AdminGetUser`. `AdminAddUserToGroup` is
  intentionally NOT granted — roles live in PostgreSQL `user_roles` per ADR-001; no
  Cognito groups exist (Story 11.E.1 Resolved Q#1).
- Remove magic-link JWT key infrastructure (Secrets Manager entries, env-var wiring for
  the speaker JWT).
- Update IAM policies / permission boundaries that referenced the magic-link auth
  endpoints.

## 4. API Surface Changes

| Endpoint | Change |
|---|---|
| `POST /api/v1/auth/speaker-magic-login` | **Remove** |
| `POST /api/v1/speaker-portal/validate-token` | **Remove** |
| `POST /api/v1/speaker-portal/respond` | **Auth changes** — Cognito Bearer, no `token` param. Request body accepts only `ACCEPT` and `DECLINE`; `TENTATIVE` is removed from the enum and rejected. |
| `GET /api/v1/speaker-portal/content` | **Auth changes** |
| `POST /api/v1/speaker-portal/content/draft` | **Auth changes** |
| `POST /api/v1/speaker-portal/content/submit` | **Auth changes** |
| `POST /api/v1/speaker-portal/materials/presigned-url` | **Auth changes** |
| `POST /api/v1/speaker-portal/materials/confirm` | **Auth changes** |
| `POST /api/v1/events/{code}/speakers/pool` | **Tighten** — reject `email` payload; pool entries are only `IDENTIFIED` |
| `PATCH /api/v1/events/{code}/speakers/pool/{speakerId}` | **Tighten** — `email` may be updated only as part of the promote-to-READY transition |
| **New** `POST /api/v1/events/{code}/speakers/{speakerId}/promote` | Performs `CONTACTED → READY`. Body: `{ email, firstName?, lastName? }`. Triggers User + Cognito provisioning. Returns updated speaker_pool with `username`. |
| `POST /api/v1/events/{code}/speakers/{speakerId}/invite` | **Existing** — preconditions tighten: requires `READY`. Now only sends the email and transitions to `INVITED`. |
| `PUT /api/v1/events/{code}/speakers/{speakerId}/status` | **Tighten** — no longer allows reaching `READY` (must use the promote endpoint). No longer allows skipping `CONTACTED`. The accepted values for `newStatus` are restricted to the 8-state set; `SLOT_ASSIGNED`, `CONFIRMED`, `OVERFLOW`, `WITHDREW`, `TENTATIVE` are all rejected. |
| `POST /api/v1/events/{code}/speakers/{speakerId}/content` | **Organizer-on-behalf content submission** — kept. Accepts title, abstract, optional CV text (→ `User.bio`), optional portrait upload reference (→ `User.profile_picture_url`), optional presentation upload reference. Delegates to the shared `ContentSubmissionService`. Authenticated as ORGANIZER. Audit trail records the organizer's username. |
| `POST /api/v1/speaker-portal/content/submit` | **Speaker-self content submission** — same payload shape as the organizer endpoint, same `ContentSubmissionService` backend, same downstream effects. Authenticated as SPEAKER. Audit trail records the speaker's username. |
| Cognito sign-in / change-password endpoints | **Reused** — standard AWS Cognito endpoints. No new endpoints required on the BATbern backend. |

The two content-submission endpoints (organizer and speaker) MUST share a single backend
implementation. They are intentionally maintained as two endpoints to preserve a clean
role separation in the API surface and to avoid auth-scope confusion (a SPEAKER must
never be able to invoke the organizer endpoint, and vice versa), but every byte of
business logic — validation, persistence, User-field updates, state transition, history
records, notifications — lives in `ContentSubmissionService`.

## 5. Suggested Phasing

| Phase | Theme | Notes |
|---|---|---|
| **A. Documentation alignment** | Update PRD, architecture docs, rewrite the Epic 9 PRD per §9.3, write ADR-009 | Independent. Do first so subsequent code reviews have a target. |
| **B. State-machine consolidation** | Delete duplicate validator; make `SpeakerWorkflowService` sole writer; remove `SLOT_ASSIGNED` / `CONFIRMED`; introduce derived `publishable` | No UX change. Pure cleanup. Reduces drift risk before bigger changes. |
| **C. Entity model simplification** | Migrate `speakers` → `user_profiles`; delete table; update repositories | Has DB migration; needs migration drill + rollback plan. |
| **D. Workflow semantics update** | Introduce promote-to-READY endpoint; tighten brainstorm-phase API; relabel UI lanes; require email at the READY gate | Brings the new model live for organizers. Speakers still authenticate via magic links at this point. |
| **E. Cognito with forced password change** | Auto-provision Cognito user at `CONTACTED → READY`; backend generates temp password and sets `FORCE_CHANGE_PASSWORD`; invitation email carries the temp password; refactor speaker-portal endpoints to Cognito Bearer auth; refactor frontend speaker pages to standard Cognito login. **Prerequisite:** Story 7.1 IAM/auth-flow cherry-pick from `feature/epic-6` (see §9.2). | Largest change. End-to-end testing of the invitation → first-login → password-change flow required. No Cognito Lambda triggers needed. |
| **F. Magic-link removal** | Delete `MagicLinkService`, magic-login controllers, JWT key infra, `speaker_jwt` cookie; drop magic-link DB tables; clean up frontend `?token` handling | Final cleanup, only after Phase E proves stable. |

Phases A–C can run partly in parallel. D depends on B. E depends on C and D. F depends on E.

## 6. Confirmed Decisions

The open questions from the initial draft have all been resolved by the team. They are
recorded here for traceability and to anchor the Scrum Master's story breakdown.

1. **TENTATIVE response — REMOVED.** Speakers respond ACCEPT or DECLINE only.
   `SpeakerResponseType.TENTATIVE`, `speaker_pool.is_tentative`,
   `speaker_pool.tentative_reason`, the related UI button, and the
   `processTentativeResponse` handler are all deleted. A speaker who is unsure simply
   doesn't respond yet (reminder/escalation handles delays); a speaker who changes their
   mind after accepting uses the existing `WITHDREW` path. See §0.6.

2. **`speaker-coordination-service` — KEEP AS THIN LAYER.** The service stays as a
   container for future speaker-focused capabilities. After the `speakers` table is
   deleted, it has minimal content, but is not collapsed into
   `event-management-service`. Future stories may add read-side APIs (e.g., aggregated
   "all events I spoke at" views).

3. **Cognito UX — STANDARD REGISTRATION + FORCED PASSWORD CHANGE.** No OTP, no Custom
   Auth Lambdas. Backend creates the Cognito user with a generated temporary password
   and `FORCE_CHANGE_PASSWORD` status. Invitation email contains the login link and the
   temporary password. Speaker changes the password on first login via the standard
   Cognito flow. See §0.5.

4. **In-flight speakers — NONE.** No backward-compatibility migration is required. The
   cutover is a clean swap of the auth model and data shape.

5. **DECLINED from `IDENTIFIED` / `CONTACTED` — VALID.** A pool entry can transition
   directly to `DECLINED` from the brainstorm states when a lead doesn't pan out. The
   row is retained for audit (no `username` populated). The state machine allowance is
   explicit; see §0.2.

6. **Epic 9 re-scoping — APPROVED.** Stories 9.2–9.5 will be re-issued by Product to
   reflect the new model: "Speaker Cognito provisioning at READY," "Frontend
   Cognito-only speaker portal," "Magic-link teardown." (No legacy-speaker migration
   story — see decision 4.)

7. **CV / portrait — OVERWRITE GLOBALLY.** `User.bio` and `User.profile_picture_url`
   are the single source of truth. Submitting a new CV for event N+1 overwrites what was
   used for event N. No per-event snapshot. Consistent with ADR-004.

8. **OVERFLOW state — REMOVED.** Capacity is enforced at the invitation step
   (`READY → INVITED` is blocked when `accepted + invited >= max_slots`). No parking-
   lane state. `OverflowManagementService` and the voting tables are deleted. See §0.7.

9. **WITHDREW state — COLLAPSED INTO DECLINED.** A speaker who accepts and later drops
   out is moved to `DECLINED` with a reason recorded in status history. Audit
   information is preserved. The workflow's only terminal state is `DECLINED`. See §0.7.

## 7. Recommended Next Steps

1. **ADR-009 drafted and accepted.** `docs/architecture/ADR-009-unified-speaker-workflow.md`
   exists in `Accepted` status. Other stories cite it as the contract.
2. **Create the refactor feature branch** off `develop` (e.g.
   `feature/speaker-workflow-refactor`). See §9.1.
3. **Cherry-pick the salvageable bits** from `feature/speaker-account-creation` and
   `feature/epic-6` onto the new branch. Delete those two branches. See §9.2.
4. **Rewrite the Epic 9 PRD** in place per §9.3 (Phase A docs work).
5. Hand the plan to the Scrum Master for story breakdown, starting with Phase A
   (documentation alignment) on the refactor branch.

## 8. Organizer Kanban UX — Making the Next Action Obvious

**Scope of this section.** Everything in §8 is about the **organizer-side kanban view**
inside the Material-UI admin app at `web-frontend/src/pages/organizer/**`. The
speaker-self surface (the public-portal pages at `web-frontend/src/pages/speaker/**`)
is a separate UI with its own design language and is **not** affected by this section.
The two surfaces share the backend service layer per §0.4; they do not share the
high-level UI components described below.

The kanban stays. The state machine stays. The change is purely **additive**: every
speaker card surfaces a single state-aware "next action" button, and the surrounding
chrome (column headers, drag affordances, drawer) is tightened to guide the organizer
without taking power-user options away.

### 8.1 Card changes — additive only

The existing card layout is preserved. Three surgical changes:

1. **ADD: primary-action button along the bottom edge of the card.** Single button,
   full-width, state-aware label (see §8.2). Clicking it opens the same modal that the
   detail drawer's primary action opens.
2. **MOVE: time-in-state indicator (e.g. "2 days", "1 week").** Right-aligned on the
   row that already shows the assigned organizer. Uses the app's existing relative-time
   formatting. Colour-coded per §8.7.
3. **REMOVE: the existing "pending" indicator / badge on cards.** Its meaning is not
   clear to organizers and it adds visual noise. The card's column position and state
   badge already convey the state.

A `⋯` secondary menu next to the primary button hosts less-common actions (reassign
organizer, edit details, override state, decline with reason). Nice-to-have, not
required for the first iteration.

### 8.2 Primary action per state

| State | Primary action button | Outcome |
|---|---|---|
| `IDENTIFIED` | **Log outreach** | Opens outreach form; on submit, transitions to `CONTACTED` |
| `CONTACTED` | **Promote to speaker** | Opens promote-to-`READY` modal (captures email, optional name); on submit provisions Cognito user and transitions to `READY` |
| `READY` | **Send invitation** | Opens invitation modal; sends email; transitions to `INVITED`. **Disabled** when `accepted + invited >= max_slots` (§8.6) |
| `INVITED` | **View response status** | Opens drawer showing deadline, last reminder, "Resend invitation", "Respond on behalf" |
| `ACCEPTED` | **Enter content** | Opens the content form shared with the speaker portal (title, abstract, optional CV → `User.bio`, optional portrait → `User.profile_picture_url`, optional presentation upload); on submit transitions to `CONTENT_SUBMITTED` |
| `CONTENT_SUBMITTED` | **Review content** | Opens moderator review form (approve / request revisions) |
| `QUALITY_REVIEWED` | **Assign session slot** if no slot assigned, otherwise a read-only **"Publishable ✓"** info-chip | Slot picker; or confirmation that the speaker is ready for agenda publication |
| `DECLINED` | **View details** | Read-only drawer |

The primary action is **the obvious next thing**. Power users can still drag, click into
the drawer, or use the `⋯` menu — but the default path is one click on a button labelled
in plain language.

### 8.3 Column headers — surface what needs attention

Each column header shows three things on three lines:

1. State name (existing).
2. Card count (existing).
3. **"Needs attention" sub-line** (new), e.g.:
   - `IDENTIFIED`: count only.
   - `CONTACTED`: "⚠ N stale (>14 days)" when applicable.
   - `READY`: "⚠ slot capacity reached" when invitations are blocked (§8.6).
   - `INVITED`: "⏰ N approaching deadline", "⏰ N past deadline".
   - `ACCEPTED`: "📝 N awaiting content".
   - `CONTENT_SUBMITTED`: "👀 N awaiting moderator review".
   - `QUALITY_REVIEWED`: "🪑 N awaiting slot".
   - `DECLINED`: count only.

Clicking the sub-line filters the column to the subset that triggered it.

### 8.4 Drag-drop, guided

_Implemented in Story 11.D.4 (`web-frontend/src/components/organizer/SpeakerStatus/speakerTransitions.ts` + the dispatcher rewrite of `handleDragEnd` in `SpeakerStatusLanes.tsx`)._

Drag-drop is preserved for power users. To make it self-correcting:

- **Drag start**: valid destination columns get a green halo; invalid columns are
  dimmed with a small lock icon. The state machine knows which transitions are legal.
- **Drop on an invalid column**: a toast explains why ("`IDENTIFIED → ACCEPTED` not
  allowed — promote to `READY` first to provision the speaker").
- **Drop on a transition that requires input** (e.g. promote-to-`READY` needs an email,
  invitation needs a deadline): the **same modal** as the primary-action button opens,
  pre-filled where possible. Drag is a shortcut, not a separate code path.
- **Drop on `DECLINED`**: confirmation modal with a required reason field. Never silent.
- **Slot-capacity-blocked drop** (drag a `READY` card into `INVITED` when slots are
  full): rejected with the same explanation the disabled button gives (§8.6).

### 8.5 Detail drawer

_Implemented in Story 11.D.4 (drawer redesign: `SpeakerDetailDrawer.tsx` + new `PrimaryActionSurface.tsx` + new `UnifiedHistoryPanel.tsx`; `OverviewTabPanel.tsx` deleted; 3-tab → 2-tab collapse). Materials and Notes sub-tabs deferred per Resolved Q#6._

Clicking a card opens the drawer. The drawer:

- Leads with the **same primary-action button** at the top, big and prominent.
- Shows secondary actions in a clear list below (reassign organizer, edit details,
  override state, decline).
- Unifies status history and outreach history into a single chronological "History"
  panel.
- Adds state-specific sub-tabs where useful (Content, Materials, Notes).

Within the organizer kanban surface, card button, drawer button, and drag-drop converge
on identical modals — one mental model for the organizer. (As noted at the top of §8,
the speaker portal's pages are not part of this convergence; they are a separate UI.)

### 8.6 Slot-capacity gating — replaces overflow management

_Drag-drop slot-gate consistency implemented in Story 11.D.4 (the same `organizer:speakerCard.slotCapacityTooltip` i18n key surfaces the disabled-button tooltip, the column-header sub-line, and the invalid-drop toast — three surfaces converge)._

The `OVERFLOW` state is gone (§0.7). Capacity is controlled at the invitation step,
which is the only place where invitations originate. The rule:

- **"Send invitation" is disabled when `count(ACCEPTED) + count(INVITED) >= max_slots`
  for the event.**

When the gate fires:
- The button shows a tooltip: "Slot capacity reached. You have N invitations outstanding
  plus M acceptances for K slots. Wait for a response, or decline an accepted speaker
  to free a slot."
- The `READY` column header surfaces "⚠ slot capacity reached" so the gate is visible
  globally, not just per card.
- Dragging a `READY` card onto `INVITED` triggers the same explanation.

This converts an after-the-fact "we have an overflow problem" state into a before-the-
fact "you can't oversubscribe" gate. No parking lane needed.

### 8.7 Time-in-state colour coding

A silent-aging card is a problem. Each state has a yellow / red threshold. The
time-in-state chip on the card changes colour accordingly; the column-header
"needs attention" sub-line counts the yellow + red cards.

| State | Yellow at | Red at |
|---|---|---|
| `IDENTIFIED` | 30 days | 60 days |
| `CONTACTED` | 7 days | 14 days |
| `READY` | 3 days | 7 days |
| `INVITED` | response deadline − 3 days | past response deadline |
| `ACCEPTED` | 14 days without content | content deadline passed |
| `CONTENT_SUBMITTED` | 3 days awaiting review | 7 days awaiting review |
| `QUALITY_REVIEWED` | 30 days before event without slot | 14 days before event without slot |
| `DECLINED` | n/a (terminal) | n/a |

Thresholds should be configurable in event settings; defaults are listed.

### 8.8 "What does this mean?" sidebar

A collapsible right-side panel. For each state, one sentence describing what it means,
one sentence naming the typical next action, one sentence describing what "stuck" looks
like. Collapses to a thin tab once an organizer is fluent.

### 8.9 Suggested story breakdown for the UX layer

The frontend changes split naturally into three stories the Scrum Master can scope
independently:

1. **State-aware card + cleanup** — Add the primary-action button, move time-in-state
   onto the organizer row, remove the "pending" indicator. Wire each button to the
   corresponding modal. No backend changes required beyond existing endpoints.
2. **Column-header awareness + time-in-state colour coding** — Sub-line "needs
   attention" counts, click-to-filter, threshold-driven colour. Requires read-only
   aggregation endpoints (or in-page derivation from already-loaded cards).
3. **Guided drag-drop + drawer redesign + slot-capacity gating** — Valid-state
   halo, reasoned-rejection toasts, slot-capacity check on the `READY → INVITED`
   transition (frontend disable + backend precondition), unified drawer history.

Story 1 alone closes 80% of the UX gap — primary-action button on every card. Stories
2 and 3 layer triage and safety on top.

## 9. Branch Strategy and Existing Work Reconciliation

This refactor does not start on a clean slate. Two related feature branches already
exist in the repository, magic-link code has already been merged to `develop`, and an
Epic 9 PRD describes a now-obsolete plan. This section defines how the team handles
each of those, and where the new work itself lives.

### 9.1 Dedicated feature branch for this refactor

All work in this plan lands on a dedicated long-lived feature branch off `develop`,
conventionally named `feature/speaker-workflow-refactor` (or `feature/adr-009`). The
branch supports the six phases of §5, lives until Phase F completes, and is merged back
to `develop` in chunks (per phase) or all at once at the end — that scheduling is for
the team to decide based on review capacity.

Rationale: the changes are deep (state machine + auth + entity model) and span
backend / frontend / infrastructure / database. A dedicated branch isolates
work-in-progress from `develop` and gives Phase B–F room to settle before being
integrated.

### 9.2 Existing branches: cherry-pick the few useful bits, then delete

Two feature branches exist in the repository and contain work that predates ADR-009.
Most of their content is now obsolete; a small subset of each contains code that
ADR-009 actively needs and should be salvaged before the branch is deleted.

#### `feature/speaker-account-creation`

10 commits beyond `develop`, all implementing the old Epic 9 (JWT magic link + dual
auth + magic-link migration). Disposition:

| Commit | Verdict |
|---|---|
| `296b6f87` — Story 9.1 JWT magic link | **Drop.** Phase F deletes magic-link auth entirely. |
| `c6c3da75` + `045499c6` — Story 9.2 account creation | **Drop.** ADR-009 provisions at `CONTACTED → READY` (before invitation), not on speaker acceptance. Useful as reference reading, not as code to merge. |
| `a747b94e` — Story 9.4 migration script | **Drop.** No in-flight magic-link speakers to migrate. |
| `73d94688` — Story 9.5 multi-role navigation UI | **Cherry-pick** the Material-UI navigation components (`NavigationMenu.tsx`, `AppHeader.tsx`, `MobileDrawer.tsx`, `UserMenuDropdown.tsx`), `navigationConfig.ts`, the `AuthContext` multi-role-state additions, the role-based test files, and the German/English `common.json` i18n keys. **Skip** `SpeakerLoginPage.tsx` (no separate speaker login under ADR-009 — same Cognito login as everyone else) and the `ProtectedRoute` speaker-JWT branch (no speaker JWT exists under ADR-009). |
| `396a9045` — null-safe `user.roles` guard in `UserMenuDropdown` | **Cherry-pick** alongside the Story 9.5 UI. |
| `7ec0e571` — PRD "all stories complete" | **Drop.** PRD is rewritten per §9.3. |

After cherry-picking, the branch is **deleted**
(`git push origin --delete feature/speaker-account-creation`). It does not merge cleanly
with ADR-009.

#### `feature/epic-6`

3 commits beyond `develop`. Two are a self-cancelling merge-then-revert pair. The
substantive commit is:

| Commit | Verdict |
|---|---|
| `d5cf0fcc` — Story 7.1 Cognito admin permissions + encryption key | **Cherry-pick the Cognito IAM additions and the app-client auth flow.** Specifically: `ALLOW_ADMIN_USER_PASSWORD_AUTH` on the App Client (required by `AdminInitiateAuth` for the dummy-password login flow), the IAM perms `AdminCreateUser`, `AdminSetUserPassword`, `AdminInitiateAuth`, `AdminGetUser`, and the related CDK unit tests. **Skip** the `COGNITO_PASSWORD_ENCRYPTION_KEY` secret — under ADR-009 the temp password is generated, embedded once in the invitation email, and never stored at rest; nothing to encrypt. |

Naming wrinkle: the commit message references "Epic 7 Unified Speaker Identity" but the
branch is named `feature/epic-6`. Inconsequential for the cherry-pick. Note it in the
cherry-pick commit message so the trail stays readable.

After cherry-picking, the branch is **deleted**.

#### Timing of the cherry-picks

The cherry-picks are not all needed at the same point in the phasing:

- **Story 7.1 Cognito IAM** (from `feature/epic-6`) is a **prerequisite for Phase E**.
  It can land early on the refactor branch — anywhere from Phase A through the start
  of Phase E.
- **Story 9.5 multi-role nav UI** (from `feature/speaker-account-creation`) is
  independent of the auth model and can land any time. Natural home is alongside the
  Phase E / F frontend work, but it can land earlier without harm.

Cherry-picking both shortly after the refactor branch is created is the simplest
sequencing.

### 9.3 Epic 9 PRD — rewrite in place

`docs/prd/epic-9-speaker-authentication.md` describes the old plan (JWT magic link,
dual auth, Epic 6 migration). The high-level **goal** (speakers who are also attendees
access both portals with one Cognito session, zero duplicate accounts) survives under
ADR-009 — but the implementation stories (9.1–9.4) are now wrong, and Story 9.5 needs
re-scoping.

**Recommendation: rewrite in place.** Replace the body of
`epic-9-speaker-authentication.md` with the new scope, bump the date, and add a
`Supersedes prior Epic 9 plan per ADR-009` note at the top. Keeps the URL and the
Epic-9 identifier stable; the historical pre-ADR-009 version remains in git history.

Alternative if Product prefers a clean break: archive the current doc to
`docs/prd/archived/epic-9-speaker-authentication-pre-adr-009.md` with a deprecation
banner and create a new file with the rewritten scope.

Rewritten story shapes (suggested — Product owns the final wording):

| Old | Replaced by |
|---|---|
| Story 9.1 — JWT magic link | **Deleted.** No magic link under ADR-009. |
| Story 9.2 — Account creation on acceptance | **9.1' — Speaker Cognito provisioning at READY.** Backend creates Cognito user with `FORCE_CHANGE_PASSWORD` at the `CONTACTED → READY` transition; grants SPEAKER role. |
| Story 9.3 — Dual authentication (magic link + password) | **9.2' — Cognito-secured speaker portal.** Speaker portal endpoints move to `@PreAuthorize("hasRole('SPEAKER')")`; `permitAll()` is removed; frontend uses standard Cognito login. |
| Story 9.5 — Frontend multi-role navigation | **9.3' — Multi-role navigation.** Re-scoped from the salvageable Story 9.5 cherry-pick. Same goal, no speaker-JWT branch. |
| Story 9.4 — Migration of Epic 6 magic-link users | **9.4' — Magic-link teardown.** Delete `MagicLinkService`, `JwtConfig`, `SpeakerMagicLoginController`, `SpeakerPortalTokenController`, the `?token=` / `?jwt=` query-param handling, the `magic_link_tokens` table, and the `speaker_jwt` cookie. (Phase F of this plan.) |

These story shapes are illustrative, not prescriptive. Product writes the final stories.

### 9.4 Magic-link code already on `develop`

Commit `296b6f87` (Story 9.1 JWT magic link) was merged to `develop` before this
refactor began. The following code currently lives on `develop` and is **scheduled for
deletion in Phase F**:

- `services/event-management-service/.../service/MagicLinkService.java`
- `services/event-management-service/.../config/JwtConfig.java` — the speaker-specific
  JWT config. Confirm it is distinct from the API Gateway's general JWT validation
  config before deletion.
- `services/event-management-service/.../controller/SpeakerMagicLoginController.java`
- `services/event-management-service/.../controller/SpeakerPortalTokenController.java`
- The `?token=` and `?jwt=` query-param handling on speaker-portal frontend pages
- The `magic_link_tokens` Flyway-managed table
- The `speaker_jwt` HTTP-only cookie and its server-side handling

This is **not a prerequisite for Phases A–E.** The new Cognito-based path is added
before the old path is removed; they coexist on `develop` (or on the refactor branch)
through Phases B–E. Phase F removes the magic-link code only once the Cognito path
is proven stable.

### 9.5 Summary of disposition

| Item | Action | When |
|---|---|---|
| New work on this refactor | Lives on `feature/speaker-workflow-refactor` (or similar) | From Phase A onward |
| `feature/speaker-account-creation` branch | Cherry-pick Story 9.5 multi-role-nav UI; delete the rest of the branch | Cherry-pick early on the refactor branch; delete after merge |
| `feature/epic-6` branch | Cherry-pick `d5cf0fcc` Story 7.1 IAM + auth flow (skip encryption key); delete | Cherry-pick by start of Phase E |
| `docs/prd/epic-9-speaker-authentication.md` | Rewrite in place per §9.3 | Phase A |
| Magic-link code on `develop` | Delete in Phase F | Phase F (after Phase E proves Cognito path stable) |
