# Story 12.10: Apple + Generic OIDC, and the Cognito Trigger-Retirement Cleanup Track (SSO Phase 6 + Cleanup)

Status: backlog

> **DEFERRED — do NOT flip to `ready-for-dev` yet.** This story is the umbrella for two
> explicitly-deferred bodies of work in `docs/plans/sso-oidc-federation.md`: **Phase 6** (Apple
> Sign-in + generic corporate OIDC) and the **Cleanup track** (retiring the three now-redundant
> Cognito triggers). Per the plan, Phase 6 is *"documented, not scheduled,"* and the cleanup
> track's hard precondition is that the **gateway `is_active` gate (Story 12-2) and canonical JIT
> (Story 12-3) are verified in production** (Story 12-7). When that precondition is met, this
> umbrella should be **split into the independent PRs listed below** — each becomes its own small
> story — rather than implemented as one monolith. It is parked here as a single backlog artifact
> so nothing is lost.

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **platform engineer extending SSO beyond Google and shrinking the Cognito trigger surface**,
I want **(a) the deferred Apple / generic-OIDC federation work captured with its known hard
problems, and (b) the trigger-retirement cleanup track scoped into independent, individually-
revertible PRs that only run after the gateway gate + canonical JIT are proven in prod**,
so that **the auth surface can be reduced to three triggers (`PreSignUp`, `PreTokenGeneration`,
`CustomEmailSender`) without losing account-create or inactive-block guarantees, and a future
Apple/corporate-OIDC decision starts from a written analysis instead of a blank page.**

This is the **deferred tail of Epic 12**. Source: `docs/plans/sso-oidc-federation.md` §5 "Phase 6"
+ §5 "Cleanup track", §8 Q5 (PostConfirmation full removal). It has **no scheduled delivery date**
and is intentionally left `backlog`.

## Scope — split into independent PRs when scheduled

This umbrella decomposes into the following independent stories/PRs. **None are prerequisites for
the Google SSO flow (12-1…12-9).** Each is individually deployable and revertible.

### Part 6 — Apple Sign-in + generic OIDC *(deferred; own decision)*
The materially-harder federation cases the plan separates from Google:
- **Apple private-relay email.** Apple users may present a per-app relayed address
  (`…@privaterelay.appleid.com`), which **breaks the email-keyed account-linking** the Google
  trigger (Story 12-6) relies on. Linking must fall back to **provider-`sub` keying** for Apple.
- **Name returned only on first authorization.** Apple sends `name` *once*, on the first consent.
  A capture-or-lose path is required (persist on first auth; never re-fetchable).
- **Paid Apple developer account** required.
- **Signing key rotates every 6 months** → Secrets Manager rotation (vs. Google's long-lived secret).
- **Generic corporate OIDC** (`UserPoolIdentityProviderOidc`) is a smaller variant of the same
  shape (issuer URL + client id/secret + attribute mapping); the same `sub`-vs-email linking
  question applies.
- Builds on the same machinery as Google: the `cognito-stack.ts` IdP block (pattern from Story
  12-5) and the `PreSignUp_ExternalProvider` linking branch (Story 12-6) — extended for the
  relay-email / sub-keyed-linking case.

### Cleanup C1 — Retire `PostAuthentication` *(lowest risk; first)*
Its email-link behaviour is fully covered by canonical JIT (Story 12-3). Remove the trigger wiring
from `infrastructure/lib/constructs/cognito-user-sync-triggers.ts`; handler source stays in git
history for rollback. Update `06b` inventory. **Precondition:** 12-3 verified in prod (12-7).

### Cleanup C2 — Retire `PreAuthentication` *(after the gate is confirmed live)*
Replaced by the gateway `is_active` gate (Story 12-2), which *also* covers federated logins (where
`PreAuthentication` never fires) **and** the post-issuance token window. Remove the trigger wiring
only after the gate is confirmed live in prod (Story 12-7 AC2). Update `06b` (flip
`PreAuthentication` to "redundant / retired"). **Precondition:** 12-2 verified in prod.

### Cleanup C3 — Remove `PostConfirmation` *(§8 Q5: full removal; JIT is the sole create path)*
Accepts lazy (first-request) provisioning. **Hard prerequisites, in order:**
1. **12-3's `custom:preferences` parity must be in place** so JIT captures names/language (closes
   the 2026-05-18 duplicate-without-names divergence).
2. The **bootstrap organizer** path — which today manually invokes the `PostConfirmation` Lambda
   ARN to grant `ORGANIZER` (`BootstrapOrganizer` construct) — must be switched to a **direct
   role-insert** in the construct *before* the trigger is deleted.
Only then delete the trigger. Update `06b` inventory.

## Acceptance Criteria

> These are umbrella-level ACs. When this story is split, each Part/Cleanup PR carries its own
> grounded ACs (RED-GREEN-REFACTOR, file:line, handler tests per CLAUDE.md).

1. **(Gating.)** No cleanup PR (C1/C2/C3) is opened until Story 12-7 confirms the gateway
   `is_active` gate (12-2) and canonical JIT (12-3) are verified **in production**. This is a hard
   gate, not a soft preference (staging IS prod — a premature trigger removal could silently drop
   account-create or inactive-block guarantees).
2. **(Independence.)** Each Part/Cleanup is its own PR, independently deployable and revertible.
   Every trigger removal preserves the handler source in git history; rollback = re-add the
   construct wiring (handlers untouched).
3. **(Apple — linking correctness, when scheduled.)** Apple/OIDC linking keys on provider-`sub`
   when the presented email is a private-relay address, and captures `name` on first authorization
   (persist-or-lose). The native company-UUID validation (Story 12-6) remains preserved verbatim.
4. **(Handler tests mandatory.)** Per CLAUDE.md, any new/changed Lambda (Apple IdP linking branch;
   `PostConfirmation`/`PreAuthentication`/`PostAuthentication` wiring changes) keeps a handler-level
   module-load test; CDK changes keep `Template.fromStack` tests.
5. **(Doc-drift, same commit.)** Each PR updates `docs/architecture/06b-user-lifecycle-sync.md`
   inventory (trigger status: active → retired) and the `cognito-user-sync-triggers.ts` construct.
   ADR-010 gains an Apple/OIDC deferral → decision note when Part 6 is scheduled.
6. **(End state.)** After C1+C2+C3, the pool runs exactly three triggers: `PreSignUp`,
   `PreTokenGeneration`, `CustomEmailSender`.

## Tasks / Subtasks

- [ ] **When scheduling:** split this umbrella into ≥4 stories (Part 6-Apple, C1, C2, C3) and
      flip each to `ready-for-dev` individually with its own grounded ACs/Tasks. Do NOT implement
      as one PR.
- [ ] **C1 — Retire `PostAuthentication`** (after 12-7 prod verification): remove trigger wiring in
      `cognito-user-sync-triggers.ts`; `06b` inventory update; CDK test + (if a handler remains) its
      module-load test adjusted.
- [ ] **C2 — Retire `PreAuthentication`** (after gateway gate confirmed live in prod): remove
      wiring; `06b` flip to redundant/retired; tests adjusted.
- [ ] **C3 — Remove `PostConfirmation`** (after 12-3 preferences parity + bootstrap-organizer
      direct role-insert): switch `BootstrapOrganizer` to direct role-insert *first*; then delete
      the trigger; `06b` update; accept lazy first-request provisioning.
- [ ] **Part 6 — Apple/OIDC** (own decision, no date): write the IdP block (pattern: Story 12-5),
      extend the `PreSignUp_ExternalProvider` branch (Story 12-6) for sub-keyed linking + relay
      email + first-auth name capture; Secrets Manager key rotation for Apple's 6-month key; handler
      + CDK tests; ADR-010 decision note.

## Dev Notes

### Why this is parked, not scheduled
- **Apple** is a materially harder, separate decision (private-relay email + name-on-first-auth-
  only + 6-month signing-key rotation + paid developer account). The plan deliberately ships Google
  first and defers Apple/OIDC so the harder linking-by-`sub` path doesn't block the simpler
  email-keyed Google linking.
- **The cleanup track is enabled by, but not required for, SSO.** It reduces the auth surface once
  PR 1 (Stories 12-2 + 12-3) makes JIT canonical and the gateway gate live — but the Google flow
  works fully with the triggers still in place. Removing them early would be a guarantee-dropping
  risk against the one real production pool.

### Files in play (when scheduled — grounded against current source)
- `infrastructure/lib/constructs/cognito-user-sync-triggers.ts` — trigger wiring (the construct that
  attaches `PostAuthentication` / `PreAuthentication` / `PostConfirmation`); C1/C2/C3 remove entries
  here. (Verify exact `addTrigger` lines at scheduling time — the construct was confirmed present
  during Story 12-6 grounding.)
- `infrastructure/lib/lambda/triggers/` — handler sources (`post-confirmation.ts`,
  `pre-authentication.ts`, `post-authentication.ts`); deletions leave these in git history.
- `BootstrapOrganizer` construct — currently invokes the `PostConfirmation` ARN to grant ORGANIZER;
  C3 prerequisite is switching it to a direct `role_assignments` insert. (Locate exact construct at
  scheduling time.)
- `infrastructure/lib/stacks/cognito-stack.ts` — Part 6 adds `UserPoolIdentityProviderApple` /
  `UserPoolIdentityProviderOidc` (pattern: the Google IdP from Story 12-5).
- `docs/architecture/06b-user-lifecycle-sync.md` — trigger inventory; each PR flips a row.
- `docs/architecture/ADR-010-federated-identity-via-cognito.md` — Apple/OIDC decision note (Part 6).

### Testing standards (per CLAUDE.md)
- Lambda handler module-load tests are MANDATORY for any handler touched (a Lambda that fails
  module-load 503s all auth). CDK `Template.fromStack` tests for every wiring change.
- Cleanup PRs are largely *removals* — the test work is asserting the trigger is **no longer**
  attached (negative `Template` assertion) and that the covering path (gateway gate / canonical JIT)
  still holds (re-run the relevant 12-2 / 12-3 suites).
- Staging IS production: each removal deploys behind verification; rollback is re-adding wiring.

### References
- [Source: docs/plans/sso-oidc-federation.md#Phase 6 — (Deferred) Apple + generic OIDC]
- [Source: docs/plans/sso-oidc-federation.md#Cleanup track — trigger retirement]
- [Source: docs/plans/sso-oidc-federation.md#8. Resolved decisions — Q5 (PostConfirmation full removal) + Q1 (Google only, Apple+OIDC deferred)]
- [Source: docs/plans/sso-oidc-federation.md#3. The two Cognito gotchas] (why federated logins bypass PostConfirmation + PreAuthentication)
- ADR-010 (Federated Identity via Cognito) — broker model, Google-first, Apple deferral.
- Depends on: Story 12-2 (gateway gate) + 12-3 (canonical JIT) **verified in prod via 12-7**;
  Story 12-5 (IdP pattern) + 12-6 (PreSignUp_ExternalProvider linking branch) as templates.

### Open Questions (for when this is scheduled)
- **OQ-1:** Is Apple/corporate-OIDC actually wanted, and on what timeline? Currently "documented,
  not scheduled" (plan §8 Q1). Needs a product decision before Part 6 is sized.
- **OQ-2:** For C3, confirm the `BootstrapOrganizer` direct-role-insert is acceptable vs. keeping a
  one-off manual grant — and that lazy first-request provisioning is acceptable for *all* create
  paths (no remaining caller depends on synchronous PostConfirmation row-creation).

## Dev Agent Record

### Agent Model Used
_Not yet implemented — deferred backlog umbrella._

### Debug Log References
_None._

### Completion Notes List
_None._

### File List
- `_bmad-output/implementation-artifacts/12-10-apple-oidc-and-trigger-retirement-cleanup.md` (this umbrella story)

### Change Log

| Date | Change |
|---|---|
| 2026-06-01 | Story 12.10 created as a deferred backlog umbrella for SSO Phase 6 (Apple + generic OIDC) and the trigger-retirement cleanup track (C1 PostAuthentication, C2 PreAuthentication, C3 PostConfirmation). Kept `backlog` — split into independent PRs only after Stories 12-2 + 12-3 are verified in prod via 12-7. |
