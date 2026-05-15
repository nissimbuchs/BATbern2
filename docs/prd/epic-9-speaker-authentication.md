# Epic 9: Speaker Authentication & Account Integration

> **Supersedes prior Epic 9 plan per ADR-009.**
>
> The original Epic 9 plan (Stories 9.1–9.5) layered JWT magic-link authentication and a "dual-auth" migration on top of the existing token-based speaker portal. That direction was reversed by ADR-009 (Unified Speaker Workflow) which adopts standard AWS Cognito with `FORCE_CHANGE_PASSWORD` on first login as the sole speaker authentication mechanism. The implementation work for unified speaker identity is now tracked in **Epic 11** (`docs/prd/epic-11-speaker-workflow-refactor.md`) — specifically Stories 11.E.1, 11.E.2, 11.E.3, and 11.F.1. This document is retained because the Epic Goal (one Cognito identity per person, zero duplicate accounts for multi-role users) remains valid; only the implementation is moved.

**Status:** 📦 **SUPERSEDED BY EPIC 11** — implementation now tracked in Epic 11 Phases E and F (Stories 11.E.1, 11.E.2, 11.E.3, 11.F.1). This document is retained for the Epic Goal narrative; do not plan new work against it.

**Epic Goal**: Unify authentication architecture so that a person who is both a speaker and an attendee has a single Cognito identity. A speaker is a User with the SPEAKER role (per ADR-004 / ADR-009); there are no separate speaker accounts, no parallel auth stack, and no duplicate identities for multi-role users.

**Deliverable**: A speaker portal that authenticates the same way every other portal authenticates — a standard AWS Cognito session. The login link in the invitation email lands on the standard Cognito login page; the temporary password from that email satisfies the first-login `FORCE_CHANGE_PASSWORD` challenge; subsequent logins are normal Cognito sessions. Multi-role users see all their roles' navigation entries from a single session.

**Architecture Context** (per ADR-009):
- **Identity store**: AWS Cognito (sole authentication system).
- **Role grant**: SPEAKER role is granted in `role_assignments` (database) at the `CONTACTED → READY` workflow transition, by the same `UserApiClient.provisionUserWithRole(...)` flow used elsewhere. There is no Cognito group sync (per `docs/architecture/06b-user-lifecycle-sync.md` — roles live exclusively in `role_assignments` and are added to the JWT via the PreTokenGeneration Lambda at login).
- **Provisioning point**: Cognito user creation happens at `SpeakerWorkflowService.transition(CONTACTED → READY)` — before the invitation email is sent — via `AdminCreateUser` with `MessageAction=SUPPRESS` and `FORCE_CHANGE_PASSWORD` status.
- **Invitation email**: Delivered on the `READY → INVITED` transition; contains the login URL + the temporary password generated at provisioning time. The temporary password is never persisted on the BATbern side after the email is dispatched.
- **Portal protection**: All `/api/v1/speaker-portal/**` endpoints are `@PreAuthorize("hasRole('SPEAKER')")`. The previous `permitAll()` policy and the `?token=` query auth path are removed.
- **Magic-link teardown**: `MagicLinkService`, `SpeakerPortalTokenController`, `SpeakerMagicLoginController`, the speaker-side `JwtConfig`, the `magic_link_tokens` table, and the `speaker_jwt` cookie are deleted. There is no grace period (per ADR-009 / refactor plan §6 decision 4 — no in-flight magic-link sessions need to be preserved).

**Duration**: Tracked under Epic 11 Phases E + F (no separate Epic 9 timeline).

**Dependencies**:
- ADR-009 (Unified Speaker Workflow) — accepted; this Epic implements its Decision 3 (Cognito-only auth).
- Epic 11 Phase B (state machine consolidation) — required because Cognito provisioning is wired into `SpeakerWorkflowService.transition(..., READY, ...)`.
- Epic 11 Phase C (entity simplification) — required because the SPEAKER role grant + `username` persistence depend on the unified `provisionUserWithRole` flow.

---

## Why this Epic was re-scoped

The original Epic 9 plan inherited the magic-link mental model from Epic 6 and tried to upgrade it to a JWT-based magic-link plus a parallel password-login path. ADR-009 reviewed the costs of that direction and rejected it:

- **Two identity systems to maintain forever.** The previous plan kept opaque RESPOND/VIEW tokens + JWT magic-link + the standard Cognito session running side-by-side, with `permitAll()` exposure on half of `/api/v1/speaker-portal/**`. This is twice the security review surface, twice the key-rotation work, and twice the audit complexity, for no operational benefit.
- **Indeterminate state machine.** The previous plan layered Cognito on top of three coexisting state-machine validators (`StatusTransitionValidator`, `SpeakerWorkflowService.isValidTransition`, the direct-mutation paths in `SpeakerResponseService`) that disagreed on which transitions were legal. Bolting more auth on top of that did not address the root problem.
- **Asymmetric data invariants.** The previous plan tolerated speaker-led `ACCEPTED` producing a `User` row while organizer-led `ACCEPTED` did not. Downstream code (reporting, agenda publishing, notifications) had to defensively check for both shapes.

ADR-009's Decision 3 deletes the magic-link stack entirely. The Epic Goal — single identity per person, zero duplicate accounts — is preserved; the path to it is standard Cognito invitation, not a layered JWT scheme.

---

## Scope (under Epic 11)

The work originally planned as Stories 9.1–9.5 is consolidated into four work blocks owned by Epic 11, one block per Epic 11 story. The cross-references below are the authoritative source; this section is a roll-up for readers landing here from prior Epic 9 references.

### Block 0 — Infrastructure prerequisite: CDK App Client + Cognito admin IAM
**Epic 11 reference**: Story **11.E.1** (`docs/prd/epic-11-speaker-workflow-refactor.md`, Phase E).

What this block delivers (must land before Block 1):
- CDK changes to the Cognito User Pool App Client to enable the auth flow required for `AdminCreateUser` + `FORCE_CHANGE_PASSWORD` first-login.
- IAM policy on the `company-user-management-service` task role granting the Cognito admin permissions needed for `AdminCreateUser`, `AdminAddUserToGroup`, and `AdminSetUserPassword`.
- User Pool policy alignment (password complexity, MFA stance) consistent with the rest of the BATbern Cognito setup.

This is pure infrastructure — no application code changes. Blocks 1, 2, 3 depend on it.

### Block 1 — Speaker Cognito provisioning at `CONTACTED → READY`
**Epic 11 reference**: Story **11.E.2** (`docs/prd/epic-11-speaker-workflow-refactor.md`, Phase E).

What this block delivers:
- `UserApiClient.provisionUserWithRole(username, email, firstName, lastName, SPEAKER)` invoked from `SpeakerWorkflowService.transition()`'s `CONTACTED → READY` hook.
- `company-user-management-service` performs `AdminCreateUser` with `MessageAction=SUPPRESS` and a strong random temporary password, grants the SPEAKER role in `role_assignments`, and returns `{ username, temporaryPassword }` to the caller. Re-calling for an already-provisioned user is idempotent and returns `temporaryPassword: null`.
- Invitation email (`READY → INVITED` hook) embeds the login URL + the temporary password in all 10 supported locales (incl. gsw-BE).
- The temporary password is discarded from memory immediately after email dispatch; never persisted.

### Block 2 — Cognito-secured speaker portal + multi-role navigation
**Epic 11 reference**: Story **11.E.3** (`docs/prd/epic-11-speaker-workflow-refactor.md`, Phase E).

What this block delivers (Story 11.E.3 spans three work streams; collected here for narrative clarity):

**Backend — portal Cognito auth:**
- `@PreAuthorize("hasRole('SPEAKER')")` on every `/api/v1/speaker-portal/**` endpoint; removal of `permitAll()` from the speaker-portal `SecurityFilterChain` config.

**Frontend — session refactor:**
- `web-frontend/src/pages/speaker/**` consumes the standard Cognito session via `useAuth()` (same pattern as organizer/partner portals); no `?token=` URL parsing, no `?jwt=` URL parsing, no second cookie.
- Cherry-pick commits `73d94688` + `396a9045` (the multi-role nav components) from `feature/speaker-account-creation`, but **skip** that branch's `SpeakerLoginPage` and the `ProtectedRoute` speaker-JWT branch — those are magic-link-era artifacts.

**Frontend — multi-role nav (the surviving portion of the original Story 9.5):**
- Navigation reads `roles` from the Cognito ID token (already populated by the PreTokenGeneration Lambda).
- Users with multiple roles see one navigation entry per role (e.g., a person with `ATTENDEE + SPEAKER` sees both "Attendee Portal" and "Speaker Portal").
- Active portal is visually distinguished.

### Block 3 — Magic-link teardown
**Epic 11 reference**: Story **11.F.1** (`docs/prd/epic-11-speaker-workflow-refactor.md`, Phase F).

What this block delivers:
- Delete: `MagicLinkService`, `JwtConfig` (speaker-side), `SpeakerMagicLoginController`, `SpeakerPortalTokenController`, the `magic_link_tokens` Flyway table, the `speaker_jwt` HTTP-only cookie + its server-side handling, `?token=` and `?jwt=` URL handling in `pages/speaker/**`, `SpeakerMagicLoginPage`, the "Mark as tentative" UI.
- Remove magic-link secrets from AWS Secrets Manager.
- Delete the feature branches `feature/speaker-account-creation` and `feature/epic-6` once Phase E has been stable in production for ≥1 week (verified by CloudWatch showing zero magic-link traffic).

**No grace period is implemented** — per refactor plan §6 decision 4, there are no in-flight magic-link sessions that need to be preserved across the cutover.

---

## What is intentionally NOT in scope (compared to the original Epic 9 plan)

Several items from the prior Epic 9 plan are explicitly rejected by ADR-009 and are not delivered by this Epic:

- **Dual authentication path (magic link OR email/password).** ADR-009 §3 / refactor plan §6 decision 4 — there is one auth path: standard Cognito with `FORCE_CHANGE_PASSWORD` on first login. The temporary password from the invitation email satisfies the first-login challenge; from that point on the speaker uses their own password.
- **Migration script for Epic 6 staging users (original Story 9.4).** No in-flight magic-link sessions exist that require migration — the existing staging users will receive a new Cognito invitation email when the new system goes live. This is operational, not a story-level deliverable.
- **JWT-based magic-link (original Story 9.1, implemented but now deprecated).** The work that landed under Story 9.1 (`MagicLinkService.generateJwtToken`, `SpeakerMagicLoginController`, the RSA key pair in `JwtConfig`, the embedded JWT in invitation emails, `SpeakerMagicLoginPage`) is reverted by Epic 11 Story 11.F.1. The historical implementation remains in git history if it is ever needed for reference, but it is not on the production path after Phase F lands.
- **Cognito custom-auth Lambdas / email OTPs.** Considered (ADR-009 Alternative 5) and rejected — standard Cognito invitation flow is sufficient and requires no Lambda triggers.

---

## Success Criteria (Epic level)

Inherited from the original Epic Goal, unchanged:
- **Single identity per person.** A person who is both a speaker and an attendee has exactly one Cognito user and one `users` row. Verified by `users.cognito_sub` uniqueness + the unique constraint on `users.email`.
- **Zero duplicate accounts.** Idempotency of `UserApiClient.provisionUserWithRole` ensures re-running the `CONTACTED → READY` transition for an existing user does not create a second Cognito user.
- **Single session covers all roles.** A multi-role user logs in once and sees the navigation entries for every role granted in their `role_assignments`.

New criteria added by ADR-009:
- **No `permitAll()` on speaker-portal endpoints.** Every `/api/v1/speaker-portal/**` endpoint requires a Cognito Bearer token AND the SPEAKER role.
- **No parallel auth surface.** `MagicLinkService`, `magic_link_tokens`, `speaker_jwt` cookie, `?token=` / `?jwt=` URL params — all deleted post-Phase F.

---

## Related Documents

- **ADR-009 (Unified Speaker Workflow)**: `docs/architecture/ADR-009-unified-speaker-workflow.md` — Decision 3 is the authoritative spec for the auth model.
- **Epic 11 (Unified Speaker Workflow Refactor)**: `docs/prd/epic-11-speaker-workflow-refactor.md` — owns Stories 11.E.1, 11.E.2, 11.E.3, 11.F.1 that implement this Epic's scope.
- **Refactor plan**: `docs/plans/speaker-workflow-refactor.md` §0.5 (auth model), §6 (decisions), and §9.3 (Epic 9 rewrite scope).
- **ADR-004 (Factor User Fields from Domain Entities)** + **ADR-007 (Unified User Profile)**: User-as-identity model that this Epic relies on.
- **`docs/architecture/06b-user-lifecycle-sync.md`**: canonical specification for role storage — database-only role assignments; SPEAKER role lives in `role_assignments`, not Cognito groups; JWT claim populated at login by the PreTokenGeneration Lambda.
