---
type: epic
title: "Every service knows when the agent acts, and for whom"
parent: initiative-agentic-interaction
covers: ["R1"]
sources:
  - _bmad-output/initiative-agentic-interaction/initiative-agentic-interaction.md
  - _bmad-output/initiative-agentic-interaction/architecture-agentic-interaction/architecture-agentic-interaction.md
companions:
  - docs/architecture/module-map.md
after: []
refined: true
risk: high
---

# Every service knows when the agent acts, and for whom

## Description

One shared-kernel decoder accepts Cognito, Watch and agent tokens and records agent:<run> for <username>; CUMS mints RS256 delegated tokens against run grants and owns the switch, the allowlist and the organizer delegate; gateway, CUMS, EMS and PCS adopt it; audience sends in EMS and PCS refuse agent tokens; the module map names the exceptions. Today gateway, CUMS and EMS each carry their own copy of a Cognito-plus-Watch decoder and PCS accepts Cognito only, so the decoder replaces three copies. Live, switched off.

## Outcome

One shared-kernel decoder accepts Cognito, Watch and agent tokens and records agent:<run> for <username>. It is live in production and switched off until the owner switches it on.

## Requirements

- R1 (parent R1, AD-3): one shared-kernel decoder accepts Cognito, Watch and agent tokens in gateway, CUMS, EMS (incl. STOMP) and PCS, each service enabling its token kinds by configuration; existing Cognito and Watch logins work unchanged, shown by each service's production-chain tests and the Watch pairing test.
- R2 (parent R1, AD-3): a request carrying an agent token records the actor agent:<run> for <username> in audit fields through one shared resolver, while ownership checks keep using the username; jobs record system:<job>.
- R3 (parent R1, AD-3): CUMS mints an RS256 token (iss=batbern-agent, sub = Cognito sub, custom:username, act={run, principal}, exp at most 5 min, no role claim) only against a valid run grant; verifiers hold only the public key, and a token so minted is accepted through the gateway by CUMS, EMS and PCS.
- R4 (parent R1, AD-3, AD-15): CUMS issues a run grant bound to one run and one principal (requester, organizer delegate, organizer), living at most 1 h, only for a verified trigger: an EMS handoff carrying EMS's caller secret, an organizer's forwarded live Cognito login, or a delegate request from agent-service's caller secret for a linked run or a named scheduler job; CUMS answers agent-eligibility for a username.
- R5 (parent R1, AD-10): with the switch off, or the principal not on the allowlist, CUMS refuses every grant, token and eligibility request, and an expired or unknown grant mints nothing; each refusal path has its own integration test.
- R6 (parent R1, AD-10): only organizers change the switch, the allowlist and the organizer delegate, through a CUMS API with no UI that refuses any token carrying act; the switch is off by default.
- R7 (parent R1, AD-15): audience sends refuse any token carrying act with 403: EMS Mail Center composer send and retry, and PCS partner meeting invite.
- R8 (parent R1, AD-2): docs/architecture/module-map.md names the /internal/agent-tokens exception, the CUMS agent settings store beside EMS app_settings, and the agent attribution convention.

## Constraints

- C1 (parent C1): the core does not change beyond AD-2's named exception; roles keep coming from role_assignments via JwtRolesConverter's lookup by sub.
- C2 (parent C2): epic 0 is one PR and one production deploy, shipped with the switch off.
- C3 (parent C8, CLAUDE.md PII rule): only organizers go on the allowlist, and allowlist usernames and pilot addresses never land in git (no migration seeds them).

## Done when

- A delegated token minted by CUMS is accepted by the gateway, CUMS, EMS and PCS through the one shared-kernel decoder, and each records the actor as agent:<run> for <username> (R1-R3).
- CUMS issues a run grant for each verified trigger and mints against it; with the switch off, the user not on the allowlist, or the grant expired or unknown, it refuses every grant and token, proven by an integration test per path (R4-R6).
- Audience sends in EMS and PCS refuse any token carrying act (R7).
- The module map names the exceptions and the attribution convention (R8).
- Deployed to production with the switch off; existing logins and the Watch app work unchanged.

## Boundaries

- Out: agent-service itself (epic-agent-runtime), the inbound alias and the EMS handoff that requests grants (epic-speaker-mail-agent), the console login that requests grants (epic-oversight-console), the live switch-off demo (epic-pilot-run).
- Out: key rotation during the pilot; a UI for the agent settings.
- Must not change: Cognito and Watch token validation for existing users; Watch tokens stay off in PCS.

## References

- parent — _bmad-output/initiative-agentic-interaction/initiative-agentic-interaction.md, Requirements R1, Constraints C1, C2, C8
- architecture — _bmad-output/initiative-agentic-interaction/architecture-agentic-interaction/architecture-agentic-interaction.md, AD-2, AD-3, AD-10, AD-15
- architecture — docs/architecture/module-map.md, the /internal rule (lines 11-16, 27-29) and the reuse table (Admin setting, Mail to an audience)

## Notes

- Source conflict: AD-10 (CUMS owns the switch and allowlist) vs module-map.md:121 (EMS app_settings is the one settings store); CUMS cannot call EMS. Resolved by the decision below.
- Source conflict: AD-15 names EMS audience sends only vs PCS sendPartnerMeetingInvite, which mails every partner contact. Resolved by the decision below.
- Source conflict: spine Deferred leaves the signing key's storage to the infrastructure story of epic-agent-runtime vs CUMS needing the key in this epic. Resolved by the decision below.
- Decision (2026-11-21): the switch, allowlist and delegate live in a new CUMS store, recorded as a module-map exception.
- Decision (2026-11-21): there is no owner; organizers are the owners. Any organizer changes the agent settings, through an API with no UI (AD-13) that refuses agent tokens, so a delegate run cannot flip its own switch. Deviates from AD-10's "only the owner".
- Decision (2026-11-21): signing key is an RSA pair; private key in Secrets Manager read only by CUMS, public key as plain config on all four services; the owner generates it once by script; no rotation during the pilot. KMS declined: a KMS call before every write.
- Decision (2026-11-21): audit fields record agent:<run> for <username>, ownership reads the username; the bare system strings in EMS and PCS become system:<job> in the same story.
- Decision (2026-11-21): the PCS partner meeting invite gets the audience guard too.
- Decision (2026-11-21): PCS enables Cognito and agent tokens, not Watch.
- Decision (2026-11-21): run grants are CUMS rows with an opaque secret stored hashed, one run and one principal, at most 1 h.
- Decision (2026-11-21): grant triggers are verified by caller secrets: EMS and agent-service each hold their own secret in Secrets Manager, checked only on /internal/agent-*; an organizer grant also needs that organizer's forwarded live Cognito JWT; a delegate grant only for a linked run or a named scheduler job.
- Decision (2026-11-21): Nissim is the organizer delegate (AD-15).
- Decision (2026-11-21): the audience guard detects act through story 2's resolver.
- Decision (2026-11-21): declined: moving story 3's post-deploy check into Done when; eligibility as 200 false (it stays a refusal, R5).
- Decision (2026-11-21): tracer bullet is story 1, the shared decoder adopted in all four services, because it is the riskiest path: a fault there breaks every login.
- Decision (2026-11-21): a refactor sweep closes the epic; no closing end-to-end suite, since story 6 verifies the cross-service path.
- Decision (2026-11-22, was an open question of story 5): scheduler-job grants stay limited to `agent.grants.scheduler-jobs`; every scheduled job the initiative plans is listed now, which today is `morning-brief` (epic-brief-and-chat). A new job is added there in the story that builds it.
- Decision (2026-11-22, was an open question of story 5): both readings hold: the organizer delegate must be on the allowlist before a linked-run or scheduler grant, and an organizer-login grant needs agent-service's caller secret as well as the organizer's live JWT.
- Decision (2026-11-22, was an open question of story 6): `act.principal` carries the principal kind in lower case (`requester`, `organizer`, `organizer_delegate`).
- Decision (2026-11-22, was an open question of story 7): cancelling an event is guarded: a transition to CANCELLED with a token carrying act answers 403 before the event is read, so no registration is cancelled and nobody is mailed. Saving a higher capacity, which mails the people promoted from the waitlist, stays open to agent tokens.
- Decision (2026-11-22, was an open question of the sweep): a user merge drops the source's allowlist row and delegate; no new story.
- Decision (2026-11-22, was an open question of the sweep): the Watch REST paths keep passing the JWT `sub`; ignored for now.
- Decision (2026-11-22): the module map's `/internal/agent-*` wording stays; AD-2 in the spine is amended to match (agent-service calls `/internal/agent-grants` and `/internal/agent-tokens`).
- Recorded (refactor sweep, 2026-11-22): `actorOr(job)` differs per service (CUMS: JWT principal only; EMS and PCS: any signed-in, non-anonymous principal). Documented in 06-backend-architecture.md, not aligned.
- Recorded (refactor sweep, 2026-11-22): the filter's 401 on `/internal/agent-*` for a bearer the decoder does not accept carries Spring's `WWW-Authenticate` with an `error_description`. Documented in the spec and 06; a custom entry point would be a behaviour change.
- Recorded (refactor sweep, 2026-11-22): a body Spring cannot read (malformed JSON, wrong type, wrong `Content-Type`) answers 400 or 415 before the caller-secret check on `/internal/agent-*`. Documented; binding the body raw would be a behaviour change.
- Recorded (refactor sweep, 2026-11-22): a mint refusal is logged twice (service INFO with run id and principal kind, handler WARN). Kept: the service line carries context the handler lacks.
- Recorded (refactor sweep, 2026-11-22): the story-number scheme in Javadoc is mixed (0.N and N). Cosmetic, left.
- Recorded (refactor sweep, 2026-11-22): EMS and PCS `getCurrentActor` and `carriesAgentAct` are called from tests only. Kept for the uniform helper API the module map names.
- Recorded (refactor sweep, 2026-11-22): the public constants on `BatbernJwtDecoder` and `Actors` were not narrowed: shared-kernel public API, no gain.
- Recorded (refactor sweep, 2026-11-22): the CUMS IT helpers (`user`, `perform`, `switchOff`) repeated in three test classes were not consolidated: CUMS-only churn with no defect.
- Recorded (refactor sweep, 2026-11-22): Spring's `Jwt` refuses `iat >= exp` when the decoder builds it (`BadJwtException`, 401), so an agent token with `iat == exp` was already refused before the sweep; the sweep made `AgentTokenValidator`'s own rule strict (`iat` before `exp`) so the validator is complete on its own, and pinned the decoder behaviour in `AgentTokenValidatorTest`.
