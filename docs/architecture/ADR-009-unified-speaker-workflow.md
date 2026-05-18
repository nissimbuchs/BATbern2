# ADR-009: Unified Speaker Workflow — Single State Machine, Role-Based Identity, Cognito Authentication

**Status**: Accepted
**Date**: 2026-05-15
**Decision Makers**: Architecture team
**Related ADRs**: ADR-003 (Meaningful Identifiers), ADR-004 (Factor User Fields from Domain Entities), ADR-007 (Unified User Profile)
**Related Plan**: `docs/plans/speaker-workflow-refactor.md`

## Context

The BATbern platform supports two parallel speaker coordination flows that have evolved
independently across Epics 5, 6, and 9:

- **Organizer-led flow** — organizer adds candidates to the speaker pool, logs outreach,
  manually moves cards through workflow states, and may enter title/abstract/CV/photo on
  behalf of the speaker.
- **Speaker-led flow** — once the speaker is identified, all interaction happens via
  email and a magic-link self-service portal: accept/decline, submit content, view a
  speaker dashboard.

An architectural review of `services/event-management-service/` (`SpeakerWorkflowService`,
`SpeakerStatusService`, `SpeakerResponseService`, `SpeakerInvitationService`,
`MagicLinkService`, `SpeakerPortalTokenController`, `SpeakerMagicLoginController`),
`services/speaker-coordination-service/` (`speakers` table), `services/company-user-management-service/`
(`user_profiles`, `user_roles`), and `web-frontend/src/pages/speaker/**` revealed that
the two flows have diverged in three structural ways. Each is documented in the refactor
plan; this ADR codifies the corrective design decisions.

### Current State

**1. Three state-machine validators coexist with conflicting rules.**

- `services/event-management-service/.../validator/StatusTransitionValidator.java`
  (used by `SpeakerStatusService.updateStatus`) — accepts `INVITED`, `SLOT_ASSIGNED`,
  `ACCEPTED → CONFIRMED` direct, and `CONFIRMED → DECLINED`.
- `services/event-management-service/.../service/SpeakerWorkflowService.isValidTransition`
  — rejects `INVITED` (falls through to `default false`), explicitly rejects
  `SLOT_ASSIGNED` ("Should not be used"), treats `CONFIRMED` as terminal, but allows
  `WITHDREW → ACCEPTED`.
- `SpeakerResponseService.processAcceptResponse` (line 213) calls
  `speaker.setStatus(SpeakerWorkflowState.ACCEPTED)` directly — no validator invoked at
  all.

The same database column (`speaker_pool.status`) has three different opinions about what
transitions are legal, and the speaker-led path bypasses validation entirely. Drift is
guaranteed.

**2. Asymmetric entity provisioning.**

Reaching `ACCEPTED` via the organizer Kanban produces a `SpeakerPool` row only. Reaching
`ACCEPTED` via the magic-link portal produces a `SpeakerPool` row AND a `User` row AND a
`Speaker` row. Downstream code that assumes "ACCEPTED ⇒ has User" fails randomly
depending on which UI button was pressed. The `Speaker` entity additionally duplicates
`workflowState` from `speaker_pool.status`, which drifts.

**3. Parallel authentication stack.**

Speaker authentication is implemented outside Cognito as a layered token system:

- `RESPOND` single-use opaque tokens (accept/decline page).
- `VIEW` multi-use opaque 30-day tokens (dashboard, profile, content).
- JWT magic-login tokens (Story 9.1) with RSA keys managed in `JwtConfig`.
- An HTTP-only `speaker_jwt` cookie separate from the Cognito session cookie.
- Half of `/api/v1/speaker-portal/**` endpoints are mounted on `permitAll()` because
  they trust the token, not the Spring Security principal.

Epic 9 stories 9.2–9.5 as currently filed plan to bolt Cognito on top of this for a
"dual-auth" mode — adding a second parallel auth system rather than replacing the first.

### Problem Statement

Three structural problems flow from the current state:

1. **Indeterminacy of valid states and transitions.** Because three validators disagree,
   the answer to "can a speaker go from state X to state Y?" depends on which code path
   you ask through. Bug-fixing one validator does not fix the others.

2. **Asymmetric data.** A speaker pool row with `status = ACCEPTED` may or may not have
   a corresponding `User` row, may or may not have a corresponding `Speaker` row, and
   may or may not have a populated `username`. Reporting, agenda publishing, and
   notifications all hit edge cases.

3. **Two identity systems to maintain forever.** Magic-link auth is a real auth stack
   (key rotation, token revocation, session management, audit) layered on a public-
   facing surface. Maintaining it alongside Cognito doubles the security review
   surface. Epic 9's dual-auth direction multiplies, not consolidates.

### Explored Alternatives

#### Alternative 1: Patch the validators in place

Reconcile `StatusTransitionValidator` and `SpeakerWorkflowService.isValidTransition`
into a single rule set, while leaving the direct-mutation path in `SpeakerResponseService`
alone.

**Rejected because:** It fixes (1) but not (2) or (3). The speaker-led path still
mutates state without going through any validator, so the consolidated rules are still
bypassable. Entity provisioning is still asymmetric.

#### Alternative 2: Two state machines, one per flow

Codify the divergence by formally splitting the workflow into "organizer side" and
"speaker side" state machines, with an explicit handoff at `INVITED`.

**Rejected because:** The two flows must produce the same downstream artefacts (a
publishable session, a confirmed speaker). Splitting the state machine doubles
side-effect logic (emails, history rows, domain events) without solving the underlying
asymmetry. It also forecloses the organizer-on-behalf path that this team explicitly
wants to keep.

#### Alternative 3: Keep `Speaker` table; remove only the `workflowState` field

Address (2) partially by deleting only the duplicated `workflowState` column from
`speakers`, while preserving the table itself for the legacy speaker-only attributes
(linkedin, expertise, certifications, etc.).

**Rejected because:** The retained fields are either (a) duplicates of `User` profile
fields covered by ADR-004, or (b) never used by production code paths the platform
depends on. Keeping the table preserves the asymmetric provisioning problem
(`Speaker` row created on speaker-led `ACCEPTED` but not on organizer-led `ACCEPTED`)
without commensurate value.

#### Alternative 4: Keep magic links, add Cognito as a "secondary" auth

The path Epic 9 stories 9.2–9.5 are currently on: speakers can authenticate via either
magic link or Cognito, with a migration period.

**Rejected because:** It doubles the auth surface permanently. There is no operational
benefit to maintaining magic links once Cognito provisioning exists. The team confirmed
no in-flight magic-link sessions need to be preserved (refactor plan §6 decision 4).

#### Alternative 5: Cognito Custom Auth Challenge (email OTP) instead of forced password change

Use Cognito's custom auth flow to mint email-OTPs — the speaker types a one-time code
each time they log in, never sets a password.

**Rejected because:** It requires three Lambda triggers (`DefineAuthChallenge`,
`CreateAuthChallenge`, `VerifyAuthChallengeResponse`) and bespoke email infrastructure
for the OTPs. The "click link → enter password" pattern of standard Cognito invitation
is well understood by enterprise users (BATbern's audience), needs no Lambda triggers,
and is fully documented by AWS. Refactor plan §6 decision 3 confirms this choice.

## Decision

We adopt a unified speaker-workflow architecture with three coupled decisions. Each
decision is small in isolation; together they remove the asymmetries that the parallel
flows have accumulated.

### Decision 1: `SpeakerWorkflowService.transition()` is the sole writer of `speaker_pool.status`

Every change to `speaker_pool.status` goes through one entry point:

```java
@Service
public class SpeakerWorkflowService {
    @Transactional
    public SpeakerPool transition(
        UUID speakerPoolId,
        SpeakerWorkflowState targetState,
        SecurityPrincipal actor,
        TransitionPayload payload
    ) {
        // 1. Load speaker_pool row
        // 2. Validate (current, target) is in the allow-list
        // 3. Enforce state-specific preconditions
        //    (e.g., READY → INVITED requires slot capacity check)
        // 4. Run state-specific side-effect hooks
        //    (provisioning at CONTACTED → READY, email at READY → INVITED, ...)
        // 5. Persist new status
        // 6. Write status-history row attributing the change to `actor`
        // 7. Publish SpeakerWorkflowStateChangeEvent
        // 8. Return updated SpeakerPool
    }
}
```

**Allow-list (the only legal transitions).** The workflow has **eight states**:

```
IDENTIFIED → CONTACTED → READY → INVITED → ACCEPTED → CONTENT_SUBMITTED → QUALITY_REVIEWED
                                                                          (terminal happy)

Any non-terminal state → DECLINED  (terminal "not happening")
```

Removed from the enum: `SLOT_ASSIGNED`, `CONFIRMED`, `OVERFLOW`, `WITHDREW`, `TENTATIVE`.

- `SLOT_ASSIGNED` and `CONFIRMED` are replaced by **derived flags**:
  - `slot_assigned := session.start_time IS NOT NULL`
  - `publishable := quality_reviewed AND slot_assigned`
- `OVERFLOW` is replaced by a **slot-capacity gate** at `READY → INVITED`:
  `(count(ACCEPTED) + count(INVITED)) < max_slots` must hold.
- `WITHDREW` is **collapsed into `DECLINED`**: a speaker who accepts then drops out
  transitions to `DECLINED` with a reason recorded in status history. The audit trail
  (previous status + reason) preserves the information a separate state used to encode.
- `TENTATIVE` (and its side-channel `is_tentative` / `tentative_reason` columns) is
  **removed**: speakers respond ACCEPT or DECLINE only.

**Side-effect hooks.** State-transition side effects live in
`SpeakerWorkflowService.transition()`, not in controllers and not in response handlers:

| Transition | Side effects |
|---|---|
| `CONTACTED → READY` | Provision User (lookup-or-create) + Cognito user + grant SPEAKER role + persist `username` on `speaker_pool` |
| `READY → INVITED` | **Precondition**: slot-capacity gate. **Action**: send invitation email containing login URL + temporary password |
| `INVITED → ACCEPTED` | Send confirmation email |
| `(any state) → DECLINED` from `INVITED` or later | Notify organizer |

**Callers.** All paths that previously mutated `speaker_pool.status` directly are
refactored to call `SpeakerWorkflowService.transition()`:

- `SpeakerStatusService.updateStatus` (organizer Kanban) → delegates state change,
  keeps audit/cache responsibilities.
- `SpeakerResponseService.processAcceptResponse` / `processDeclineResponse` (speaker
  portal) → delegates state change. The direct `speaker.setStatus(...)` calls are
  deleted. The `processTentativeResponse` branch is deleted entirely.
- `SpeakerInvitationService.sendInvitation` → split into `promoteToReady` (the provisioning
  transition) and `sendInvitation` (the `READY → INVITED` transition).

`StatusTransitionValidator` is **deleted**. `SpeakerWorkflowService` owns the allow-list.

### Decision 2: Speaker is a User with the SPEAKER role, not a separate entity

The `speakers` table is **deleted**. There is no parallel "speaker profile" entity.

| Field | Where it lives |
|---|---|
| `username`, `email`, `firstName`, `lastName`, `companyId` | `user_profiles` (already, per ADR-004) |
| `bio` (used as "short CV") | `user_profiles.bio` (already, per ADR-004) |
| `profilePictureUrl` (used as "speaker portrait") | `user_profiles.profile_picture_url` (already, per ADR-004) |
| `availability`, `expertiseAreas`, `speakingTopics`, `languages`, `certifications`, `linkedInUrl`, `twitterHandle`, `speakingHistory`, `communicationPreferences` | **DROPPED.** Not migrated to `user_profiles`. Not retained anywhere. These fields are not used by production code paths the platform depends on. |
| Per-event slot preferences, content deadline, response deadline, etc. | `speaker_pool` (already) |
| Per-event content (title, abstract, presentation file, quality-review feedback) | `content_submissions` + `session_users` (already) |

"Is this user a speaker?" is answered by `user_roles.role = 'SPEAKER'`.

**`user_profiles` is NOT extended.** No new columns. The two user-level attributes
speakers need (`bio` and `profile_picture_url`) already exist per ADR-004. Submitting a
new CV via the speaker portal or via the organizer drawer overwrites `User.bio`
globally — there is no per-event snapshot. This matches ADR-007's single-source-of-truth
principle (refactor plan §6 decision 7).

Implications:

- **No `Speaker` entity, no `SpeakerRepository`.** `SpeakerResponseService.createSpeakerIfNeeded()`
  is deleted. Asymmetric provisioning disappears: `ACCEPTED` always implies "has User
  and Cognito user," reached via Decision 1's `CONTACTED → READY` hook regardless of
  which flow triggered it.
- **`speaker-coordination-service` becomes thin** (refactor plan §6 decision 2). It is
  kept as a container for future speaker-related capabilities but contains no entity
  after this ADR lands.

### Decision 3: Standard Cognito registration with forced password change on first login

The magic-link auth stack (RESPOND tokens, VIEW tokens, JWT magic-login, `speaker_jwt`
cookie) is **deleted** and replaced by standard Cognito registration:

1. **Provisioning happens at `CONTACTED → READY`** (Decision 1, side-effect hook):
   - Backend calls `cognito-idp:AdminCreateUser` for the speaker's email.
   - Backend generates a strong random temporary password (passes Cognito password
     policy).
   - User is created with status `FORCE_CHANGE_PASSWORD`.
   - SPEAKER role is granted via a row insert into PostgreSQL `user_roles` (per ADR-001
     database-centric role storage). No Cognito group operations are performed — no
     Cognito groups exist on this user pool (see `cognito-stack.ts` "REMOVED: Cognito
     Groups (ADR-001)" note). `cognito-idp:AdminAddUserToGroup` is intentionally NOT
     granted to the provisioning service.

2. **The invitation email** (sent on `READY → INVITED`) contains:
   - A link to the speaker portal login page.
   - The temporary password.
   - A short note explaining that the speaker will set their own password on first
     login.

3. **First login** uses the standard Cognito hosted/SDK flow:
   - Speaker enters email + temporary password.
   - Cognito challenges them with `NEW_PASSWORD_REQUIRED`.
   - Speaker sets a new password. From then on they are a regular Cognito user.

4. **All `/api/v1/speaker-portal/**` endpoints** are `@PreAuthorize("hasRole('SPEAKER')")`.
   `permitAll()` is removed. The speaker portal pages obtain a normal Cognito session
   and call backend endpoints with a Bearer token — identical to how the organizer
   admin app works.

**No Lambda triggers required.** No `DefineAuthChallenge`, no `CreateAuthChallenge`, no
`VerifyAuthChallengeResponse`. Standard Cognito tooling covers the entire flow.

**Components deleted:**

- `MagicLinkService`, `SpeakerPortalTokenController`, `SpeakerMagicLoginController`,
  `JwtConfig` (the speaker-JWT one).
- The `magic_link_tokens` database table.
- `speaker_jwt` HTTP-only cookie and its server-side handling.
- The `?token=` query param across all speaker-portal frontend pages.

**No backward-compat migration is required** (refactor plan §6 decision 4) — there are
no in-flight magic-link sessions to preserve. Cutover is a clean swap.

### Cross-cutting: two data-entry flows, one service layer

The two coordination flows are preserved as **first-class peers** at the API and UI
levels, sharing the backend service layer:

- **Organizer-on-behalf**: `POST /api/v1/events/{code}/speakers/{speakerId}/content` —
  authenticated as ORGANIZER, audit trail records the organizer.
- **Speaker-self**: `POST /api/v1/speaker-portal/content/submit` — authenticated as
  SPEAKER, audit trail records the speaker.

Both endpoints call the same `ContentSubmissionService`. Validation, persistence,
`SpeakerWorkflowService.transition(..., CONTENT_SUBMITTED, ...)`, history records,
notifications — all identical. The two endpoints exist so role scoping stays clean in
the API surface; a SPEAKER must never be able to invoke the organizer endpoint and vice
versa.

**Frontend surfaces are NOT shared at the component level.** The organizer drawer
(Material-UI admin app at `web-frontend/src/pages/organizer/**`) and the speaker portal
(public-website style at `web-frontend/src/pages/speaker/**`) are two distinct React
component trees. They may reuse low-level design-system primitives (inputs, validators)
but no high-level form or page is shared. This is intentional — the two audiences have
different visual contexts (dense admin work vs. one-off public-facing form).

## Consequences

### Positive

1. **Single source of truth for speaker state.** `SpeakerWorkflowService.transition()`
   is the only writer of `speaker_pool.status`. Bug-fixing one validator fixes the
   system.

2. **Symmetric data invariants.** `ACCEPTED` always implies User + Cognito user +
   SPEAKER role. Asymmetric paths are gone. Downstream reporting, agenda publishing,
   and notifications can rely on the invariant.

3. **Smaller workflow surface.** 8 states instead of 12; one terminal `DECLINED`
   instead of three terminal states (`DECLINED`, `WITHDREW`, terminal interpretation
   of `CONFIRMED`); zero side-channel boolean states (`is_tentative`, `is_overflow`).

4. **Single identity system.** Cognito for everyone. No parallel JWT key infrastructure,
   no opaque token tables, no `permitAll()` on production endpoints. Standard Spring
   Security model.

5. **Smaller security review surface.** Magic-link auth is one less custom-built
   auth path to audit. SPEAKER role enforcement uses the same `@PreAuthorize` pattern
   as ORGANIZER / PARTNER.

6. **No `user_profiles` schema extension.** Per ADR-004 / ADR-007, `User.bio` and
   `User.profile_picture_url` are the single source of truth for those fields. SPEAKER
   role does not require additional User columns.

7. **Capacity is enforced before invitation, not after acceptance.** Organizers cannot
   oversubscribe a slot count. `OVERFLOW` parking-lane management is replaced by a
   simple precondition.

8. **Aligns with existing ADRs.** Decision 2 extends ADR-004 (User as single source of
   truth) and ADR-007 (unified user profile) with explicit speaker treatment.
   Decision 1 expresses ADR-003's microservice-isolation principle at the state-machine
   layer (one service owns the state field).

### Negative

1. **Coordinated cutover required.** Three changes (state machine consolidation, table
   deletion, auth replacement) must land together for the asymmetry to fully resolve.
   The refactor plan §5 sequences this into six phases (A–F) to keep each step
   deployable, but the end state is what delivers the value — phases B–D alone leave
   magic links in place.

2. **Loss of historical bio-per-event.** `User.bio` is overwritten on each content
   submission. Old agenda pages will show the speaker's *current* bio, not the bio
   used when the event happened. Refactor plan §6 decision 7 explicitly accepts this
   tradeoff because no product requirement on file requires historical bio
   preservation.

3. **Drop of unused speaker fields.** `expertise_areas`, `speaking_topics`,
   `linkedin_url`, `twitter_handle`, `certifications`, `languages`,
   `speaking_history`, `communication_preferences`, `availability` — all dropped
   without migration. The platform does not use them in any production flow. If a
   future story needs any of these, they can be reintroduced as User columns at that
   point.

4. **No standalone speaker-only profile API.** Anything that returned speaker-only
   profile data now returns User profile data with `SPEAKER` role context. API
   consumers that rely on the legacy speaker endpoints' shape must update.
   **Mitigation:** Most call sites already use the User-enriched response per ADR-004's
   HTTP enrichment pattern.

5. **Cognito provisioning happens earlier in the workflow than today.** Previously a
   Cognito user only appeared if the speaker logged into the magic-link portal. Now it
   appears at `CONTACTED → READY`, before any email goes out. Operationally this
   means more Cognito users in the pool, including users who may decline. **Mitigation:**
   Cognito user records are cheap; the SPEAKER role grant means they have no access to
   anything until they log in; declined-speaker cleanup can run as a periodic job if
   the user-count growth becomes a concern.

6. **Slightly more friction at first login.** Standard Cognito invitation requires the
   speaker to enter a temporary password (typed or pasted from the invitation email).
   A one-tap magic link is fractionally faster. Refactor plan §6 decision 3 accepts
   this tradeoff in exchange for not maintaining a parallel auth system.

### Technical Debt

**Eliminated:** the three-validator drift, the asymmetric entity provisioning, the
parallel auth stack, the `Speaker.workflowState` duplicate of `speaker_pool.status`,
the side-channel `is_tentative` / `tentative_reason` columns, the `permitAll()` speaker
endpoints, the magic-link key infrastructure.

**Created:** none.

## Implementation Guidelines

### `SpeakerWorkflowService.transition()` skeleton

```java
public enum SpeakerWorkflowState {
    IDENTIFIED, CONTACTED, READY, INVITED, ACCEPTED,
    CONTENT_SUBMITTED, QUALITY_REVIEWED, DECLINED
    // SLOT_ASSIGNED, CONFIRMED, OVERFLOW, WITHDREW, TENTATIVE — REMOVED
}

@Service
public class SpeakerWorkflowService {

    private static final Map<SpeakerWorkflowState, Set<SpeakerWorkflowState>> ALLOWED =
        Map.ofEntries(
            Map.entry(IDENTIFIED,        Set.of(CONTACTED, DECLINED)),
            Map.entry(CONTACTED,         Set.of(READY, DECLINED)),
            Map.entry(READY,             Set.of(INVITED, DECLINED)),
            Map.entry(INVITED,           Set.of(ACCEPTED, DECLINED)),
            Map.entry(ACCEPTED,          Set.of(CONTENT_SUBMITTED, DECLINED)),
            Map.entry(CONTENT_SUBMITTED, Set.of(QUALITY_REVIEWED, DECLINED)),
            Map.entry(QUALITY_REVIEWED,  Set.of(DECLINED))
            // DECLINED is terminal — no transitions out
        );

    @Transactional
    public SpeakerPool transition(
        UUID speakerPoolId,
        SpeakerWorkflowState target,
        SecurityPrincipal actor,
        TransitionPayload payload
    ) {
        SpeakerPool sp = speakerPoolRepository.findById(speakerPoolId)
            .orElseThrow(() -> new EntityNotFoundException(...));
        SpeakerWorkflowState current = sp.getStatus();

        // 1. Allow-list
        if (!ALLOWED.getOrDefault(current, Set.of()).contains(target)) {
            throw new InvalidStateTransitionException(current, target);
        }

        // 2. State-specific preconditions
        switch (target) {
            case READY     -> requireEmail(sp, payload);
            case INVITED   -> enforceSlotCapacity(sp.getEventId());
            // ...
        }

        // 3. State-specific side effects
        switch (target) {
            case READY     -> provisionUserAndCognito(sp, payload);
            case INVITED   -> sendInvitationEmail(sp, payload);
            case ACCEPTED  -> sendAcceptanceConfirmation(sp);
            case DECLINED  -> notifyOrganizerIfPostInvitation(sp, current, payload);
            // ...
        }

        // 4. Persist + audit + publish event
        sp.setStatus(target);
        speakerPoolRepository.save(sp);
        statusHistoryRepository.save(buildHistoryRow(sp, current, target, actor, payload));
        eventPublisher.publish(new SpeakerWorkflowStateChangeEvent(...));

        return sp;
    }

    private void enforceSlotCapacity(UUID eventId) {
        long accepted = speakerPoolRepository.countByEventIdAndStatus(eventId, ACCEPTED);
        long invited  = speakerPoolRepository.countByEventIdAndStatus(eventId, INVITED);
        int maxSlots  = eventSlotService.getMaxSlots(eventId);
        if (accepted + invited >= maxSlots) {
            throw new SlotCapacityReachedException(eventId, accepted, invited, maxSlots);
        }
    }
}
```

### Cognito provisioning topology (two-endpoint design per Story 11.E.2 Q#1 Variant B)

Cognito provisioning is split across **two** CUMS endpoints. The temporary password is
generated at the moment it is needed and consumed before the call returns — it is never
stored anywhere between READY and INVITED.

```java
// Endpoint 1: POST /api/v1/users/provision
// Called at CONTACTED → READY by SpeakerWorkflowService.runReadyHook.
@Service
public class UserService {

    @Transactional
    public ProvisionUserResponse provisionUserWithRole(ProvisionUserRequest req) {
        // 1. Lookup or create User (existing logic; idempotent).
        // ... existing-user branch returns { username, created=false } and does NOT call Cognito.

        // 2. New-user branch only: create Cognito shell.
        String throwaway = passwordGenerator.generate();   // strong, policy-compliant
        cognitoIntegrationService.adminCreateUserSilently(
            email,
            throwaway,
            user.getUsername()     // MessageAction=SUPPRESS — we send our own email
        );
        // 3. Discard the throwaway: speaker never sees it. The real temp password is
        //    issued at INVITED time via AdminSetUserPassword (Endpoint 2).

        // 4. Grant SPEAKER role — PostgreSQL user_roles insert (ADR-001),
        //    NOT cognito-idp:AdminAddUserToGroup (no Cognito groups exist).
        roleService.addRole(user.getUsername(), Role.SPEAKER);

        // 5. temporaryPassword field was REMOVED from ProvisionUserResponse in Story 11.E.2.
        return new ProvisionUserResponse().username(user.getUsername()).created(true);
    }

    // Endpoint 2: POST /api/v1/users/{username}/issue-invitation-credentials
    // Called at READY → INVITED by SpeakerWorkflowService.runInvitedHook.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)   // no DB write; mutates Cognito only
    public InvitationCredentialsResponse issueInvitationCredentials(String username) {
        var user = userRepository.findByUsername(username).orElseThrow(...);
        var status = cognitoIntegrationService.getUserStatus(user.getEmail());  // AdminGetUser
        return switch (status) {
            case FORCE_CHANGE_PASSWORD, RESET_REQUIRED -> {
                String fresh = passwordGenerator.generate();
                cognitoIntegrationService.adminSetTemporaryPassword(user.getEmail(), fresh);  // Permanent=false
                yield new InvitationCredentialsResponse(ActionEnum.FRESH_TEMP_PASSWORD)
                        .temporaryPassword(fresh);
            }
            case CONFIRMED -> new InvitationCredentialsResponse(ActionEnum.USE_EXISTING_PASSWORD);
            case UNCONFIRMED -> /* defensive: treat as FRESH */ ...;
            case ARCHIVED, COMPROMISED -> throw new UnprocessableInvitationStateException(...);  // 422
        };
    }
}
```

EMS captures the response in `SpeakerWorkflowService.runInvitedHook` and forwards it
to `SpeakerInvitationEmailService.sendInvitationEmail(speaker, event, loginUrl,
credentials, locale)`. The email template renders the FRESH or USE_EXISTING block
based on `credentials.action()`. The temp password value leaves the workflow service's
scope as soon as the email-send returns; it is never written to any database or log.

### Migration to the new state set

```sql
-- Map removed states to the new model
UPDATE speaker_pool SET status = 'ACCEPTED'          WHERE status = 'SLOT_ASSIGNED';
UPDATE speaker_pool SET status = 'QUALITY_REVIEWED'  WHERE status = 'CONFIRMED';
UPDATE speaker_pool SET status = 'READY'             WHERE status = 'OVERFLOW';
UPDATE speaker_pool
    SET status = 'DECLINED',
        decline_reason = COALESCE(decline_reason, 'Withdrew after acceptance (legacy)')
    WHERE status = 'WITHDREW';

-- Drop TENTATIVE side-channel columns
ALTER TABLE speaker_pool DROP COLUMN is_tentative;
ALTER TABLE speaker_pool DROP COLUMN tentative_reason;

-- Drop the speakers table after verifying no production code references it
DROP TABLE IF EXISTS speakers CASCADE;

-- Drop magic-link infrastructure
DROP TABLE IF EXISTS magic_link_tokens;
DROP TABLE IF EXISTS speaker_selection_votes;     -- overflow voting
```

## Related Documents

- **Refactor plan**: `docs/plans/speaker-workflow-refactor.md` — sequenced six-phase
  implementation (A: docs, B: state-machine consolidation, C: entity model, D: workflow
  semantics, E: Cognito, F: magic-link teardown).
- **ADR-003**: Meaningful Identifiers in Public APIs — Decision 1's username/principal
  model follows this.
- **ADR-004**: Factor User Fields from Domain Entities — Decision 2 extends this with
  the explicit SPEAKER-role treatment and the no-extension confirmation.
- **ADR-007**: Unified User Profile — Decision 2 aligns with this.
- **Architecture docs to update**: `06a-workflow-state-machines.md` (new state list and
  transition diagram), `03-data-architecture.md` (delete `Speaker` section),
  `04-api-design.md` (remove magic-link endpoints, update auth scheme),
  `06-backend-architecture.md` (single-auth model).
- **Stories**: Epic 9 stories 9.2–9.5 to be re-scoped per refactor plan §6 decision 6.

## Revision History

| Date | Version | Changes | Author |
|------|---------|---------|--------|
| 2026-05-15 | 1.0 | Initial ADR (replaces the three placeholder ADRs 013/014/015 from the refactor plan into a single document). | Winston (Architect Agent) |
| 2026-05-16 | 1.1 | Story 11.C.1 implementation landed: V94 migration (`services/event-management-service/src/main/resources/db/migration/V94__drop_speakers_table.sql`) drops the `speakers` table; `Speaker` entity + `SpeakerRepository` + `SpeakerService` + `SpeakerController` + `SpeakerPortalProfileController` + `LegacyExportService` / `LegacyImportService` deleted; watch services migrated to `UserApiClient`; public portrait lookup mirrored as `GET /api/v1/public/users/{username}` in CUMS (`PublicUserController`). | Amelia (Dev Agent) |
| 2026-05-16 | 1.2 | Story 11.C.2 implementation landed: (a) `UserApiClient` extended with `provisionUserWithRole` (AR13, calls new `POST /api/v1/users/provision`) and `patchUserProfile` (AR14, calls new `PATCH /api/v1/users/{username}/profile`); legacy `updateUser` / `updateUserProfilePicture` / `UserUpdateDto` deleted (Resolved Decision §1). (b) `SpeakerContentSubmissionService` deleted; consolidated `ContentSubmissionService.submit(speakerPoolId, eventCode, payload, principal)` becomes the shared backend write path for both `POST /api/v1/events/{code}/speakers/{speakerId}/content` (organizer) and `POST /api/v1/speaker-portal/content/submit` (speaker portal). (c) OpenAPI specs `docs/api/users-api.openapi.yml` + `docs/api/speakers-api.openapi.yml` updated; new request schemas use `additionalProperties: false` per Resolved Decision §3. (d) Profile patch is idempotent + scoped: ORGANIZER/ADMIN patch any user, SPEAKER may patch only their own profile (403 otherwise). | Amelia (Dev Agent) |
| 2026-05-17 | 1.3 | Story 11.E.1 PM-resolved Q#1: dropped `cognito-idp:AdminAddUserToGroup` from Decision 3 + Implementation Guidelines skeleton. SPEAKER role grant uses PostgreSQL `user_roles` row insert per ADR-001 database-centric role storage (no Cognito groups exist on the user pool). Also updated PRD AR30 / NFR2 / NFR5 / Story 11.E.1 AC / Story 11.E.2 AC to match. CDK IAM policy in `company-management-stack.ts` lists only the four actually-called admin actions (AdminCreateUser, AdminSetUserPassword, AdminInitiateAuth, AdminGetUser). | Nissim (PM) |
| 2026-05-17 | 1.4 | Story 11.E.2 PM-resolved Q#1-Q#4 + implementation: (Q#1) two-endpoint Cognito design — `AdminCreateUser` silently at READY via `/users/provision`; `AdminGetUser` + conditional `AdminSetUserPassword(Permanent=false)` at INVITED via the new `/users/{username}/issue-invitation-credentials` endpoint. `ProvisionUserResponse.temporaryPassword` field **removed** from OpenAPI (no longer used by any consumer). (Q#2) Email-template locale scope narrowed to `de` + `en` per CLAUDE.md §Localization. (Q#3) `.txt` template parity dropped — HTML-only emails. (Q#4) UNCONFIRMED defensively treated as FRESH; ARCHIVED/COMPROMISED → HTTP 422 (`UnprocessableInvitationStateException`). Implementation Guidelines § rewritten to show the two-endpoint shape. | Nissim (PM) |
