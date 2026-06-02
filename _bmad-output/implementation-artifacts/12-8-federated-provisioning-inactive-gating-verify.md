# Story 12.8: Federated Provisioning + Inactive-Gating (VERIFY-ONLY — built in PR 1)

Status: ready-for-dev

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

> **Renumbered 2026-06-02:** this verify-only story was **Story 12.7** and is now **12.8**, swapped with the frontend-callback story (now 12.7). The swap means the `/auth/callback` route + `signInWithFederated` (now Story 12.7) land **before** this verification, so a real federated token can be obtained through the actual app login flow rather than by hand-driving the hosted-UI OAuth code exchange (resolves readiness finding DEV-3).

## Story

As a **platform engineer rolling out Google SSO federation onto the single real Cognito pool (staging IS production)**,
I want **to verify — against a real, throwaway federated Google identity — that (a) a brand-new Google user is provisioned a correct `user_profiles` row (canonical JIT, default ATTENDEE, names captured) on its first authenticated API call, and (b) a deactivated account is blocked from federated access by the API-Gateway `is_active` gate, NOT by PreAuthentication (which never fires for federated logins)**,
so that **the two guarantees federated logins cannot inherit from Cognito triggers (DB-row creation via PostConfirmation, inactive-block via PreAuthentication) are confirmed to be carried — on provider-agnostic, request-time paths — by the components PR 1 already built, before the SSO button is dark-launched (Story 12.9).**

This is **Phase 3** of Epic 12 (SSO / OIDC Federation). It is **VERIFY-ONLY — it builds NO code.** PR 1 (Story 12.2 gateway `is_active` gate + Story 12.3 canonical JIT) already moved both guarantees onto provider-agnostic paths, which is precisely why this phase "evaporates" into verification. If either check **fails, the fix lands back in Story 12.2's or 12.3's components — NOT in this story.** Source: `docs/plans/sso-oidc-federation.md` §5 "Phase 3 — Federated provisioning + inactive-gating (VERIFY-ONLY — built in PR 1)" + §6 "Testing strategy → Verification (Phase 3)"; decision record: `docs/architecture/ADR-010-federated-identity-via-cognito.md` §D5 (provisioning + inactive-gating move to provider-agnostic paths).

**Prerequisites (must be merged + deployed to the single real pool BEFORE this verification runs):**
- **Story 12.3 (canonical JIT)** — PR 1 Part B. `JITUserProvisioningInterceptor` reads names/language from `custom:preferences` exactly as `post-confirmation.ts` did, so *any* authenticated identity (native or federated) provisions a correct `user_profiles` row + default ATTENDEE on first request. *This is the component AC1 verifies.*
- **Story 12.2 (gateway `is_active` gate)** — PR 1 Part A. The `OncePerRequestFilter` in `api-gateway` (`security.active-gate.*`) resolves caller status via `UserServiceClient` → `UserResponse.active`, Caffeine-cached (~60s TTL), returns `403 ACCOUNT_DEACTIVATED` for inactive accounts, fail-open on CUMS error, kill-switch `security.active-gate.enabled`. *This is the component AC2 verifies, and it must be deployed with `enabled=true`.*
- **Story 12.6 (account-linking `PreSignUp_ExternalProvider` trigger)** — Phase 2. A real federated identity is only safely testable once the linking trigger exists (per the Phase 1 §5 warning: a brand-new Google user signing in before the linking trigger hits the §3 gotchas). The trigger also sets `autoConfirmUser`/`autoVerifyEmail` so the federated user is immediately usable. *Without 12.6, there is no real federated identity to verify against.*
- Implied: Story 12.5 (Google IdP + attribute mapping) must be live so a Google sign-in is possible at all and `custom:preferences` carries the mapped Google name claims.
- **Story 12.7 (frontend `/auth/callback` + `signInWithFederated`)** — *recommended-available* (sequenced before this story by the 2026-06-02 renumber). Not a hard prerequisite, but with it deployed the federated token is obtained by driving the **real app login flow** (button-less: navigate the hosted-UI authorize URL → land on `/auth/callback` → session settles), avoiding a hand-built OAuth code exchange. If 12.7 is not yet deployed, fall back to the manual hosted-UI exchange.

## Acceptance Criteria

> These ACs are **pass/fail observations against a real (throwaway) federated identity**, not code to write. Each AC is satisfied by executing the matching Task and recording the observed result in the Dev Agent Record. A FAIL on any AC is a defect in the named PR 1 / prereq component and is fixed there (12.2 / 12.3), then this verification is re-run — **no code is added in this story.**

1. **(Federated JIT — brand-new identity provisions correctly.)** After a brand-new, throwaway Google identity (an email with **no** pre-existing `user_profiles` row and **no** matching Cognito native user) completes hosted-UI federation and makes **one** authenticated `GET /api/v1/users/me` call, a `user_profiles` row exists with `cognito_user_id = <the federated Cognito sub>`, exactly one `role_assignments` row resolving to **ATTENDEE** (the JIT default — `JITUserProvisioningInterceptor` defaults to `Role.ATTENDEE` when the JWT carries no role), and `first_name` / `last_name` populated from the Google name claims as mapped into `custom:preferences` (Story 12.5 mapping → read by `JITUserProvisioningInterceptor.extractNamesFromPreferences`). The row is **not** a duplicate-without-names (the 2026-05-18 incident class). **No SSO-specific create code exists** — the row was created by the same canonical JIT path a native first-request would use.

2. **(Inactive-gating — deactivated account blocked by the gateway gate, not PreAuthentication.)** When the verification user's `user_profiles.is_active` is toggled to `false` and that user then makes an authenticated `/api/v1/...` call **using a federated (Google-issued) token**, the API Gateway returns **`403` with error code `ACCOUNT_DEACTIVATED`** (from the Story 12.2 `OncePerRequestFilter`). The block is observed to come from the **gateway filter** (gateway log line / 403 + `ACCOUNT_DEACTIVATED` body), **NOT** from a Cognito PreAuthentication trigger — confirming PreAuthentication's non-firing for federated logins (§3 gotcha #2) is fully compensated. Re-activating (`is_active = true`) and waiting past the cache TTL (~60s) restores access — confirming the gate, not a hard lockout, is the mechanism.

3. **(Provider-agnostic confirmation — same paths as native.)** The provisioning in AC1 and the block in AC2 are observed to be produced by the **provider-agnostic** components (canonical JIT interceptor; gateway `is_active` filter) — i.e. there is no federated-specific provisioning branch and no federated-specific gating branch. This is confirmed by inspecting the relevant service/gateway logs during the run (JIT log line `JIT provisioning completed successfully` for the federated sub; active-gate `403 ACCOUNT_DEACTIVATED` for the federated principal) — the *same* log signatures a native identity would produce.

4. **(Failure routing — fixes land upstream, not here.)** If AC1 fails (no row / wrong role / empty names), the defect is recorded against **Story 12.3** (canonical JIT — likely a `custom:preferences` mapping or name-extraction gap vs. Story 12.5's mapping) and fixed there. If AC2 fails (no 403, wrong code, or 401 instead of 403), the defect is recorded against **Story 12.2** (gateway gate — likely the gate not running on the federated principal, wrong status code, or kill-switch left off). This story produces **no fix code**; it produces the verification record and a defect handoff.

5. **(Throwaway identity + no real outbound comms — staging IS production.)** All verification uses **throwaway / disposable Google test identities** (e.g. a `+suffix` alias on a controlled Google account, or a dedicated test Google account) whose emails do **not** belong to any real BATbern user. Because staging IS production (§4), the verification **must not** trigger any real outbound communication (no real invitations, confirmations, reminders, cancellations) and must not deactivate or mutate a real user's account. DB inspection on the production-serving pool is **read-only** except for the single deliberate `is_active` toggle on the throwaway verification user (AC2), which is reverted afterward. After the run, the throwaway `user_profiles` + Cognito user are cleaned up (deleted) so no orphan test identity persists. *(For an early dry-run, the same checks may be exercised in local-dev against `batbern-dev-postgres`, but the authoritative pass is against the real pool since that is where 12.2/12.3/12.5/12.6 are deployed.)*

6. **(No deploy, no doc change of its own.)** This phase deploys **nothing** (verification only; §5 Phase 3 "Deploy: none. Risk: none"). It also owns **no doc-drift update**: the `06b-user-lifecycle-sync.md` changes for federated sign-in + the corrected PreAuthentication/PostConfirmation-do-not-fire-for-federated note are owned by Stories 12.2 / 12.3 / 12.6 (per plan §7 and each story's same-commit doc-drift obligation). Therefore `[no-doc]` **is** appropriate for any commit produced by this story (e.g. recording the verification result) — this is a verification/test-execution story that changes no business logic, contract, scheduler, or state machine. *(If the verification record is captured only in this story file + the PR description, no commit touching `services/`/`api-gateway/`/`docs/` is produced at all.)*

## Tasks / Subtasks

> Tasks are **verification procedures**, not implementation steps. Execute against the real pool **after** 12.2 + 12.3 + 12.5 + 12.6 are merged and deployed (gate `enabled=true`). Capture every observed result (row dumps, status codes, log lines) into the Dev Agent Record. Run nothing that emits real comms.

- [ ] **Task 0 — Confirm prerequisites are deployed (gate: do not proceed until green)**
  - [ ] Confirm Story 12.3 (canonical JIT) is merged + deployed: `JITUserProvisioningInterceptor` reads names from `custom:preferences` (the `extractNamesFromPreferences` path) — grep the deployed CUMS image / source at the verified commit.
  - [ ] Confirm Story 12.2 (gateway `is_active` gate) is merged + deployed **with `security.active-gate.enabled=true`** (per 12.2's kill-switch rollout: deploy `false`, verify a test deactivation, then flip `true`). Verify the flag value on the running gateway.
  - [ ] Confirm Story 12.6 (account-linking `PreSignUp_ExternalProvider` trigger) + Story 12.5 (Google IdP + `custom:preferences` attribute mapping) are live so a real Google sign-in is possible and carries the mapped name claims.
  - [ ] If any prereq is not deployed, **stop** — this verification is not runnable yet.

- [ ] **Task 1 — Provision a brand-new throwaway federated identity (AC: 1, 5)**
  - [ ] Use a **throwaway** Google identity whose email matches **no** existing BATbern user (verify in advance: `SELECT * FROM user_profiles WHERE email = '<throwaway>'` returns 0 rows; no matching Cognito native user). Document which throwaway account is used.
  - [ ] Drive hosted-UI federation directly (no frontend button needed — Story 12.9 not yet shipped): navigate the authorize URL `https://batbern-staging-auth.auth.eu-central-1.amazoncognito.com/oauth2/authorize?identity_provider=Google&response_type=code&client_id=<spa-client-id>&scope=openid+email+profile&redirect_uri=<registered /auth/callback>` and complete the Google consent with the throwaway account. (Domain prefix from `infrastructure/lib/stacks/cognito-stack.ts:279` `batbern-${envName}-auth`.)
  - [ ] Obtain the resulting Cognito-issued (federated) ID token (from the redirect / token exchange) so an authenticated API call can be made with it.

- [ ] **Task 2 — Verify canonical JIT provisioning on first call (AC: 1, 3)**
  - [ ] With the federated token, make **one** authenticated `GET /api/v1/users/me` call (this triggers `JITUserProvisioningInterceptor.preHandle` in CUMS).
  - [ ] Inspect the DB (read-only). On the real pool prefer a careful read-only query via the staging DB tunnel; for a local dry-run: `docker exec batbern-dev-postgres psql -U postgres -d batbern_development -c "SELECT id, cognito_user_id, username, email, first_name, last_name, is_active FROM user_profiles WHERE email = '<throwaway>';"`.
  - [ ] **Record PASS/FAIL** that: (a) exactly one `user_profiles` row exists with `cognito_user_id = <federated sub>`; (b) the role resolves to **ATTENDEE** (query `role_assignments` for that user — exactly one, ATTENDEE); (c) `first_name`/`last_name` are populated (from Google name claims via `custom:preferences`), i.e. NOT empty (the 2026-05-18 duplicate-without-names anti-pattern is absent).
  - [ ] Grep the CUMS log for the provisioning signature: `grep "JIT provisioning completed successfully" /tmp/batbern-1-company-user-management.log` (local) or the equivalent CloudWatch `/aws/ecs/BATbern-staging/company-user-management` query — confirm it fired for the federated sub (AC3: same log signature a native identity produces).

- [ ] **Task 3 — Verify the gateway `is_active` gate blocks the deactivated federated account (AC: 2, 3)**
  - [ ] Toggle the throwaway user inactive with the **single deliberate** mutation: `UPDATE user_profiles SET is_active = false WHERE email = '<throwaway>';` (the only write this verification performs on the pool; there is no dedicated deactivate REST endpoint — deactivation is otherwise produced by reconciliation `UserReconciliationService` setting `user.setActive(false)`, but a direct toggle is the deterministic verification lever). **Do not** deactivate any real user.
  - [ ] Wait past the active-gate cache TTL (~60s; `security.active-gate.ttl-seconds`) so a cached `active=true` entry expires, then make an authenticated `/api/v1/...` call with the **federated** token.
  - [ ] **Record PASS/FAIL** that the API Gateway returns **`403`** with body error code **`ACCOUNT_DEACTIVATED`** (NOT `401` — a 401 would risk the FE token-refresh loop per the 12.2 design; NOT a Cognito PreAuthentication error, which never fires for federated).
  - [ ] Confirm the block originates at the **gateway filter**: grep the api-gateway log for the active-gate 403 / `ACCOUNT_DEACTIVATED` for this principal (`/tmp/batbern-1-api-gateway.log` local, or `/aws/ecs/BATbern-staging/api-gateway`). Confirm there is **no** PreAuthentication trigger invocation for the federated login.
  - [ ] Re-activate: `UPDATE user_profiles SET is_active = true WHERE email = '<throwaway>';`, wait past TTL (~60s), confirm the same call now succeeds — proving the gate (not a hard lockout) is the mechanism.

- [ ] **Task 4 — Failure routing + handoff (AC: 4)**
  - [ ] If AC1 failed → file the defect against **Story 12.3** (canonical JIT: name-extraction / `custom:preferences` mapping vs. Story 12.5). If AC2 failed → file against **Story 12.2** (gateway gate: gate not running on federated principal / wrong status code / kill-switch off). Record the routing decision in the Dev Agent Record. **Add no fix code in this story.**
  - [ ] Re-run Tasks 1–3 after the upstream fix is redeployed, until all ACs PASS.

- [ ] **Task 5 — Cleanup + record (AC: 5, 6)**
  - [ ] Delete the throwaway `user_profiles` row and the throwaway Cognito user so no orphan test identity persists on the production-serving pool. Confirm `is_active` was reverted to `true` before deletion (or the row deleted outright). Confirm **no** real outbound comms were emitted during the run.
  - [ ] Record the full verification result (PASS/FAIL per AC, observed status codes, row dumps with PII redacted, log signatures) in the Dev Agent Record and the PR description. No `services/`/`api-gateway/`/`docs/` code is changed; if a commit is produced, mark it `[no-doc]` (AC6).

## Dev Notes

### Why this phase is verify-only (the architectural intent)
PR 1 deliberately moved the two guarantees federated logins **cannot** inherit from Cognito triggers onto provider-agnostic, request-time paths (ADR-010 §D5; plan §5 PR 1 "Why this makes SSO simpler"):
- **Provisioning** (normally `PostConfirmation`, which does **not** fire for external-IdP sign-ins — §3 gotcha #1) → moved to the **canonical JIT interceptor** (Story 12.3 / PR 1 Part B). After PR 1B, *any* authenticated identity — native or federated — provisions a correct row + default ATTENDEE + names from `custom:preferences` on its first request, with **no SSO-specific create code**.
- **Inactive-gating** (normally `PreAuthentication`, which also does **not** fire for federated — §3 gotcha #2) → moved to the **API-Gateway `is_active` gate** (Story 12.2 / PR 1 Part A): a `OncePerRequestFilter` at the single front door, Caffeine-cached ~60s TTL, `403 ACCOUNT_DEACTIVATED`, fail-open on CUMS error, kill-switch flag.

Because both already exist and are provider-agnostic, Phase 3 "evaporates into verify-only" — there is nothing new to build; we only confirm the two paths actually catch a real federated identity. This is the explicit plan intent: "Phase 3 below becomes verify-only" (§5) and "Verification (Phase 3): real federated identity provisions correctly + deactivated account is blocked by the gateway gate (no new code — confirms PR 1)" (§6).

### Grounding — current source state these ACs verify against
- **Canonical JIT (AC1):** `services/company-user-management-service/.../interceptor/JITUserProvisioningInterceptor.java` — `preHandle` (line 68) skips if user exists by `cognito_user_id` (line 89), reads `given_name`/`family_name` then falls back to `extractNamesFromPreferences` reading `custom:preferences` JSON (lines 102–113, 181–198), links-by-email if a row already exists (lines 118–128), otherwise creates a new `User` with `isActive(true)` (line 148) and defaults to `Role.ATTENDEE` when authorities carry no role (`extractRolesFromAuthorities`, lines 274–277). The log signature to grep is `"JIT provisioning completed successfully"` (line 153). **Story 12.3 hardens the `custom:preferences` read to match `post-confirmation.ts` exactly** — AC1 verifies that hardening holds for a Google identity whose names arrive via the Story 12.5 attribute mapping.
- **Gateway `is_active` gate (AC2):** built by **Story 12.2** as a `OncePerRequestFilter` in `api-gateway` (`security.active-gate.enabled` / `.ttl-seconds`), resolving status via the existing `UserServiceClient` → `UserResponse.active` (the field is mapped at `services/company-user-management-service/.../service/UserResponseMapper.java:52` `.active(user.isActive())`, exposed in the generated FE type `web-frontend/src/types/generated/user-api.types.ts:947` `active?: boolean`). At trace time **this filter does not yet exist** in `api-gateway` — it is delivered by the 12.2 prerequisite; AC2 is unrunnable until 12.2 is deployed with the gate `enabled=true`.
- **Deactivation lever (Task 3):** there is **no dedicated deactivate REST endpoint** in `UserController` (`DELETE /{username}` is a GDPR hard-delete, line 470; `UserReconciliationService.java:235` sets `user.setActive(false)` for orphaned users). For deterministic verification the cleanest lever is a direct DB toggle of `user_profiles.is_active` on the **throwaway** user — never a real user.
- **Domain `User.isActive` / `is_active`:** `services/company-user-management-service/.../domain/User.java` (with `deactivation_reason`, lines 182–186); repo finder `UserRepository.findByIsActive` (line 150).

### Throwaway-identity + staging-IS-production discipline (AC5 — non-negotiable)
Per `docs/plans/sso-oidc-federation.md` §4 and project memory: there is exactly **one real user pool**; local dev points at it and the staging account (188701360969) **is** production. Therefore:
- Use **disposable** Google test identities only; never a real user's email.
- **No real outbound comms** (no invitations/confirmations/reminders/cancellations) may be triggered — this verification does not call any invite/notification path; it only authenticates and reads `/users/me`.
- The **only** write to the pool is the single `is_active` toggle on the throwaway user (reverted, then the test identity is deleted).
- Phase 1's warning (§5) — a brand-new Google user with **no linking trigger** hits the §3 gotchas — is why Story 12.6 (account-linking trigger) is a hard prereq: only then is a real federated identity safely testable.

### Verification environment notes
- **Authoritative run:** the real pool (where 12.2/12.3/12.5/12.6 are deployed). DB inspection via the staging DB tunnel, **read-only** except the AC2 toggle. Logs via CloudWatch (`/aws/ecs/BATbern-staging/{company-user-management,api-gateway}`).
- **Optional early dry-run:** local-dev (`make dev-native-up`) points at the staging Cognito pool; the local DB row inspection uses `docker exec batbern-dev-postgres psql -U postgres -d batbern_development` (db `batbern_development`, container `batbern-dev-postgres`, from `docker-compose-dev.yml`). Note that in local-dev the gate behaviour depends on the locally-running gateway build carrying 12.2's filter; treat local as a smoke, not the authoritative pass.
- Hosted-UI federation URL uses the domain prefix `batbern-staging-auth` (`cognito-stack.ts:279`) at `auth.eu-central-1.amazoncognito.com`, with `identity_provider=Google` and the registered `/auth/callback` redirect.

### Out of scope
- **Any code** — this story builds nothing (verify-only). Fixes for failures land in Story 12.2 / 12.3.
- The **"Continue with Google" button + `features.sso` flag** (Story 12.9) — federation here is driven by hitting the hosted-UI URL directly; no frontend entry point is required.
- **Building** the frontend `/auth/callback` route + `signInWithFederated` service method — that is **Story 12.7** (now sequenced before this story). This verification **uses** that route to acquire the federated token when available, but builds none of it; if 12.7 isn't deployed yet, the OAuth code exchange is performed against the hosted UI directly (see Prerequisites).
- **Apple / generic OIDC** (Story 12.10 / Phase 6) — deferred.
- The **trigger-retirement cleanup track** (retire PostAuthentication / PreAuthentication / PostConfirmation) — enabled by PR 1 but separate, optional PRs.

### References
- [Source: docs/plans/sso-oidc-federation.md#Phase 3 — Federated provisioning + inactive-gating (VERIFY-ONLY — built in PR 1)] (lines 219–227 — builds nothing; verifies JIT row + ATTENDEE + names, and gateway gate block; failures fix back in PR 1; Deploy none, Risk none)
- [Source: docs/plans/sso-oidc-federation.md#3 The two Cognito gotchas] (PostConfirmation + PreAuthentication do NOT fire for federated → provisioning + inactive-gating must move to provider-agnostic paths)
- [Source: docs/plans/sso-oidc-federation.md#PR 1 Part A + Part B] (the gateway `is_active` gate + canonical JIT this story verifies)
- [Source: docs/plans/sso-oidc-federation.md#4 Hard constraint: staging IS production] (single real pool; throwaway identities; no real comms)
- [Source: docs/plans/sso-oidc-federation.md#6 Testing strategy → Verification (Phase 3)] (real federated identity provisions correctly + deactivated blocked by gateway gate, no new code)
- [Source: docs/architecture/ADR-010-federated-identity-via-cognito.md#D5] (provisioning + inactive-gating move to provider-agnostic paths — JIT canonical + API-Gateway is_active gate replaces PreAuthentication)
- [Source: services/company-user-management-service/.../interceptor/JITUserProvisioningInterceptor.java:68,89,102-113,148,153,181-198,274-277] (JIT path AC1 verifies)
- [Source: services/company-user-management-service/.../service/UserResponseMapper.java:52 (`.active(user.isActive())`)] · [Source: web-frontend/src/types/generated/user-api.types.ts:947 (`active?: boolean`)]
- [Source: services/company-user-management-service/.../controller/UserController.java:470 (DELETE = GDPR hard-delete, NOT deactivate)] · [Source: services/company-user-management-service/.../service/UserReconciliationService.java:235 (`user.setActive(false)`)]
- [Source: infrastructure/lib/stacks/cognito-stack.ts:279 (hosted-UI domain prefix `batbern-${envName}-auth`)] · [Source: docker-compose-dev.yml:10-13 (container `batbern-dev-postgres`, db `batbern_development`)]
- Prereq stories: 12.2 (gateway is_active gate), 12.3 (canonical JIT), 12.5 (Google IdP + attribute mapping), 12.6 (account-linking PreSignUp trigger)

## Dev Agent Record

### Agent Model Used

_(to be filled by dev-story agent)_

### Debug Log References

_(to be filled — capture status codes, redacted row dumps, JIT + active-gate log signatures, federation URL used, throwaway identity used)_

### Completion Notes List

_(to be filled — PASS/FAIL per AC; any defect routed to 12.2 / 12.3 and re-run outcome; confirmation no real comms emitted + throwaway identity cleaned up)_

### File List

_(verify-only — expected empty; record the verification artifact location if any)_

### Change Log

| Date | Change |
|---|---|
| 2026-06-01 | Story drafted (Phase 3, VERIFY-ONLY — no code) as Story 12.7. Verifies canonical JIT provisioning (12.3) + gateway is_active gate (12.2) against a real throwaway federated Google identity; prereqs 12.2/12.3/12.5/12.6. Status → ready-for-dev. |
| 2026-06-02 | **Renumbered 12.7 → 12.8** (swapped with the frontend-callback story, now 12.7) so callback plumbing precedes this verification. Added Story 12.7 (callback route) as a recommended-available token-acquisition path (resolves readiness finding DEV-3); updated out-of-scope bullet accordingly. |
