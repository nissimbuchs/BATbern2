# Plan: Harden Bruno API tests to gate staging deploys with auto-rollback

## Current status

> **Where we are:** PR 1 in progress on `test-enhancement/bruno-staging-hardening-infra` — branch is now 11 commits ahead of origin (last update 2026-05-25). Done: A1 URL fix, A2 runner flags, A4 admin-cleanup-api wired first in runner, B1 README + plan, B2 cleanup endpoints on all 3 owning services (CUMS 13 tests, EMS 14 tests, PCS 11 tests — all green), one-time SQL pre-cleanup, gateway per-service routing for `/admin/test-fixtures/{cums,ems,pcs}/cleanup`, method-security flipped on in local profile (removes the trusted-localhost shortcut now that Pattern 3b makes it redundant), F2 admin-cleanup-api Bruno collection (9 tests passing locally). Side-fixes landed in the process: ResponseStatusException → 500 swallowing bug in EMS + PCS GlobalExceptionHandlers, Section G local pre-flight doc, partner_meetings cleanup-coverage TODO (deferred to PR 13), users-api 3-test latent failures captured for PR 5. **Remaining for PR 1:** A3 ECS deployment alarms (`infrastructure/lib/constructs/domain-service-construct.ts:299` circular-dep), A4 ECR `staging-stable` tag promotion, A5 deploy-staging.yml split into 4 jobs + Bruno-failure rollback wiring, the one-time `scripts/db/bruno-staging-pre-cleanup.sql` for the 17 legacy rows (decision made; script not yet written), `bruno-tests/environments/local.bru` if any audit collection ends up wanting it (not currently needed — `development.bru` covers local). After PR 1 merges: F1 deliberate-fail rollback exercise on a feature branch before the PR 14 gate flip.

Update this one line on every PR merge so anyone (including a fresh Claude session) can pick up the work without re-reading the whole plan.

## Progress log

This plan is **not** tracked as BMad stories — it's test infrastructure work + opportunistic bugfixes. The table below is the single source of truth for progress. Each PR's last commit before merge updates its row in this table.

| PR # | Branch | Scope | Status | Merged | Findings / bugs discovered |
|------|--------|-------|--------|--------|---------------------------|
| 1 | `test-enhancement/bruno-staging-hardening-infra` | Sections A + B: cleanup endpoints, runner flags, ECR `staging-stable` tag, deployment alarms, Bruno-failure rollback job, one-time legacy-junk cleanup script (17 rows). | 🟡 in progress | — | A1+A2 commits landed; companies `@Pattern` validation deferred — see audit-bugs section. Service ownership mapped: only 3 services (CUMS, EMS, PCS) own entity tables. **Discovered during impl** (2026-05-25): (1) **ResponseStatusException → 500 swallowing bug** in EMS + PCS GlobalExceptionHandlers — fixed in 5aaff459 (events) + 3a398e80 (partners) by adding explicit `@ExceptionHandler(ResponseStatusException.class)` before the catch-all `Exception.class` handler. Same gotcha class as the MethodArgumentNotValidException rule in `_bmad-output/project-context.md` — CUMS already had this handler; EMS/PCS didn't. (2) **API Gateway `/api/v1/admin/*` route fell through to EMS unconditionally** — made CUMS + PCS cleanup endpoints unreachable. Fixed in c36bc744 with per-service path discriminator: paths are now `/api/v1/admin/test-fixtures/{cums,ems,pcs}/cleanup` and `DomainRouter` matches each before the generic fallback. (3) **`@EnableMethodSecurity` was `@Profile("!local")`** — disabling method-level role checks in local dev. Flipped in 46c5dc75 across all 4 SecurityConfigs (CUMS, EMS, PCS, SCS); class renamed `ProductionMethodSecurityConfig` → `MethodSecurityConfig`. Safe now that Pattern 3b (Epic 11.E.7) DB-fallback handles locally-provisioned speakers; aligns dev with staging. (4) **CUMS Checkstyle indent** on `@ApiResponses` array closing parens — fixed in 7799b2ec (pre-commit hook only checks changed files so it slipped through in 806e0481). |
| 2 | `test-enhancement/bruno-events-api-split` | Section C: decompose events-api into 6 collections | ⬜ not started | — | — |
| 3 | `test-enhancement/bruno-audit-file-upload-api` | D.1: light audit | ⬜ not started | — | — |
| 4 | `test-enhancement/bruno-audit-companies-api` | D.2: light audit | ⬜ not started | — | — |
| 5 | `test-enhancement/bruno-audit-users-api` | D.3: light audit | ⬜ not started | — | 3 latent failures in `users-api` against `development` discovered 2026-05-25 — see "Local pre-flight findings — 2026-05-25 (users-api)" below. Test ordering bug (14 deletes the auth user), undefined `{{authUserEmail}}` var, public-user-by-username 404 cascading from #1. |
| 6 | `test-enhancement/bruno-audit-tasks-api` | D.4: light audit | ⬜ not started | — | — |
| 7 | `test-enhancement/bruno-audit-event-types-api` | D.5: light audit (new collection from PR 2) | ⬜ not started | — | — |
| 8 | `test-enhancement/bruno-audit-event-topics-api` | D.6: light audit (new collection from PR 2) | ⬜ not started | — | — |
| 9 | `test-enhancement/bruno-audit-events-crud-api` | D.7: light audit (new collection from PR 2) | ⬜ not started | — | — |
| 10 | `test-enhancement/bruno-audit-sessions-api` | D.8: light audit (new collection from PR 2) | ⬜ not started | — | — |
| 11 | `test-enhancement/bruno-audit-speaker-pool-api` | D.9: light audit (new collection from PR 2, cross-service cleanup) | ⬜ not started | — | — |
| 12 | `test-enhancement/bruno-audit-event-full-workflow-api` | D.10: light audit (new collection from PR 2) | ⬜ not started | — | — |
| 13 | `test-enhancement/bruno-audit-partners-api` | D.11: light audit | ⬜ not started | — | **TODO**: extend PCS cleanup endpoint with a `partner_meetings` entityType — discovered 2026-05-25 during PCS cleanup endpoint impl. `partner_meetings` is a standalone table (no FK to partners), so today's `partners` entityType doesn't reach it via cascade. Bruno's `partner-meetings-api` collection currently leaves meetings behind on every run. See "Deferred — partner_meetings cleanup coverage" below. |
| 14 | `test-enhancement/bruno-gate-flip` | Section E: remove `continue-on-error`, prove rollback path | ⬜ not started | — | — |

**Status legend:** ⬜ not started · 🟡 in progress · 🔵 in review · ✅ merged · 🔴 blocked

**PR description convention:** every PR includes `Refs: docs/plans/bruno-staging-hardening.md PR #<n>` and the final commit updates the row above (status, merged date, notable findings/bugs). When real bugs are discovered during a test audit, file them as separate fix-commits in the same PR and note the bug in "Findings / bugs discovered."

## Pre-PR-1 audit results — 2026-05-24

Query executed against staging RDS (`batbern-staging-postgres.c7qauya0ie7a.eu-central-1.rds.amazonaws.com`, db `batbern`) via SSM tunnel. Query: `/tmp/bruno-audit-query.sql`. Results: `/tmp/bruno-audit-results.log` + `/tmp/bruno-audit-extra.log`.

### Critical finding — naming-collision near-miss
**12 real users named "Bruno"** (`bruno.blumenthal@ruag.com`, `bruno.frey@astra.admin.ch`, `bruno.linder@sbb.ch`, etc.) match `LIKE 'bruno%'`. A naïve prefix-cleanup would have deleted real people. The dot-anchored regex `^bruno\.test\.[0-9]+$` from B1 is the only safe pattern — confirmed binding, not optional. The same applies to companies: `^[A-Za-z0-9]+$` OpenAPI pattern is **not enforced server-side** (confirmed below: company names with spaces and 30 chars persist) — so the cleanup-prefix regex must do all the safety work.

### Pre-existing test junk to remove on PR 1 deploy

| Table | Identifier | Count | Examples | Disposition |
|-------|-----------|-------|----------|-------------|
| `companies` | `name` | 1 | `bruno test company 1761143046` (2025-10-22) | Disposable — Bruno residue |
| `companies` | `name` | 6 | `E2E Test Company 1768749259021` … (Jan–Feb 2026) | Disposable — Playwright E2E residue |
| `companies` | `name` | 3 | `testag`, `testcompanya`, `testdeclinea` (Jan–Mar 2026) | Disposable — automated fixture residue |
| `partners` | `company_name` | 7 | `brtest117`, `brtest150`, `brtest288`, `brtest424`, `brtest675`, `brtest861`, `brtest899` | Disposable — current Bruno partner test residue (44% of partners table!) |
| `user_profiles` | `username` | 1 | `user.eetest@batbern-test.ch` | **KEEP — still in use** (user confirmed 2026-05-24). Exclude from cleanup script and from cleanup-endpoint regex match list. |
| `events` | `event_code` | 0 | — | Clean |
| `sessions` | `session_slug` | 0 | (1 real session about Test Automation at SBB matches `%test%` — KEEP) | Clean |
| `topics` | `topic_code` | 0 | — | Clean |
| `registrations` | `registration_code` | 0 | — | Clean |
| `logos` | `s3_key` | 0 | — | Clean (4 PENDING auto-purge in 24h) |
| `event_tasks` | (joined via `events`) | 0 | — | Clean |
| `speaker_pool` | (joined via `events`) | 0 | — | Clean |
| `user_profiles` (`email='%@e2e.batbern.invalid'`) | reserved test domain | 0 | — | Clean — reservation works |

**Total disposable rows: 17** (10 companies + 7 partners; `user.eetest` excluded — still in use). Partner cascade likely brings additional rows in `partner_meeting_*`, `topic_votes`, `partner_notes` etc. for the 7 `brtest*` partners — to be counted by the PR 1 cleanup script as a SELECT-then-DELETE before any destructive operation.

### Bugs confirmed by audit — dispositions

- **`companies.name` server-side validation NOT enforcing the OpenAPI pattern `^[A-Za-z0-9]+$`** — **but the deeper finding is that the OpenAPI spec is wrong, not the validation.** Initial reading suggested adding `@Pattern` enforcement to the DTO. Closer look at `services/company-user-management-service/src/test/java/.../integration/CompanyControllerIntegrationTest.java:62,84` reveals the existing integration test fixture uses `"Test Company"` and `"New Company"` **with spaces** and the tests pass. `CreateCompanyRequest.java` DTO has only `@Size(min=2, max=255)`. The DTO documentation example value is `"Swisscom AG"` (also with space). Production data shows 7+ rows with spaces are clearly real test/E2E residue but the broader pattern of "company name with space" is the documented intent of the code. **Conclusion: the OpenAPI spec's `^[A-Za-z0-9]+$` pattern was aspirational and doesn't reflect reality.** Adding `@Pattern` enforcement would break the existing test suite plus reject most legitimate Swiss company names. **Revised decision (2026-05-24): defer to a separate follow-up ticket.** That ticket needs a product/architecture call: either (a) relax the OpenAPI spec to match code reality, allowing spaces/periods/hyphens/ampersands, OR (b) tighten code + tests + data to enforce strict alphanumeric per the spec, which is a much bigger change. Neither belongs in PR 1.
- **`partners.company_name VARCHAR(12)` constraint confirmed enforced** — all 7 `brtest*` entries are ≤12 chars. Still the known mismatch with `companies.name VARCHAR(255)` from risk #7.1. **Decision (user, 2026-05-24): separate Linear ticket, not in PR 1.** Requires a migration to widen the column; out of scope for test infrastructure work.

### Cleanup approach for the historical junk — decided

**Decision (user, 2026-05-24): one-time SQL cleanup script in PR 1.** Targets the 17 specific rows by exact-match list (10 companies + 7 partners), not by regex. The cleanup endpoint's canonical regexes (B1) won't match these legacy patterns (`bruno test company 1761143046` has spaces; `E2E Test Company` is mixed case with spaces; `testag/testcompanya/testdeclinea` follow no canonical), and that's fine — historical patterns aren't worth supporting in the long-running endpoint.

PR 1 ships:
1. The cleanup endpoint (for ongoing canonical patterns going forward).
2. A one-shot SQL migration `V<n>__bruno_staging_pre_cleanup.sql` (or a dedicated `scripts/db/bruno-staging-pre-cleanup.sql`) that exact-match deletes the 17 rows + their cascade dependents (partner_meeting_*, topic_votes, partner_notes for the 7 partners). SELECT-then-DELETE pattern: SELECT first to log counts, then DELETE inside a transaction with a final COUNT verification.
3. Audit log entries in CloudWatch for each deletion.

The script runs exactly once when PR 1 deploys. Never re-runs.

## Local pre-flight findings — 2026-05-25 (users-api)

Ran `./scripts/ci/run-bruno-tests.sh development --collection users-api` after fix-commit `74b8cd8a` (which moved 3 `#`-prefixed comment blocks into `docs { ... }` blocks so Bruno's parser would stop skipping the files). Parser warnings are gone — but the now-parseable files surfaced 3 genuine test failures, plus the run surfaced 2 other pre-existing failures that the previously-skipped files were masking. **Result: 31 requests, 28 passed, 3 failed.** Full log: `/tmp/bruno-users-api.log` (locally — not committed).

These belong to PR 5 (`test-enhancement/bruno-audit-users-api`, D.3) and the auditor for that PR should fix them as part of the light audit pass.

### F1 — `users-api/18-add-additional-email-duplicate.bru` returns 400 instead of 409 (exposed by `74b8cd8a`)

Request body sent literally as `{"email": "{{authUserEmail}}", ...}` — Bruno is not interpolating `authUserEmail` because it's never defined. `bruno-tests/environments/development.bru:1-16` has no `authUserEmail` var; `grep -rn "authUserEmail" bruno-tests/` returns only the consumer site. The server rejects the literal `{{...}}` string with `Validation failed: email - must be a well-formed email address` → 400, instead of exercising the 409 duplicate-error branch the test intends.

**Why new:** before `74b8cd8a`, the parser skipped this file entirely. The docs-block fix made it parseable, which exposed the latent var gap. The test was effectively dead code.

**Fix options for the auditor (cheapest first):**
- Capture email from `01-get-current-user` via a `script:post-response { bru.setEnvVar("authUserEmail", res.getBody().email); }` and let `18-` consume it. Lines added: 1 in `01-`, 0 in `18-`.
- OR inline a known organizer email directly in the body (couples test to env's auth user — brittle but simplest).
- OR add `authUserEmail: {{process.env.AUTH_USER_EMAIL}}` to all three env files plus the corresponding GitHub secret (heaviest).

Recommended: the post-response capture in `01-`. Same pattern is already used elsewhere in the collection (`01-` stores `firstName`/`lastName`/`bio` to env vars for `03b-restore-current-user`).

### F2 — `users-api/17-delete-additional-email.bru` returns 401 instead of 204 (pre-existing)

DELETE `/users/me/additional-emails/{{encodedAdditionalEmail}}` returns 401. The pre-request precondition check passed (so `additionalEmail` env var was set by test 15). The request is unauthenticated at the server.

**Root cause:** test-ordering bug. Bruno runs files alphanumerically. `14-delete-test-user.bru` (`bruno-tests/users-api/14-delete-test-user.bru:8`) DELETEs `{{testUsername}}` which `development.bru:8` defines as `batbern.organizer` — the **current auth user**. `14` returns 204 (assertion `res.status: in [204, 403, 404]` accepts all three). Tests 15/16 still pass because PreTokenGen + Pattern 3b silently re-hydrate the user on the next authed call (see `docs/architecture/06b-user-lifecycle-sync.md` §"Pattern 3b"). But by the time `17-` (DELETE on encoded-email path) runs, the user's `user_additional_emails` row is gone (cascade from the user delete in `14-`), or auth-state is inconsistent enough that the gateway returns 401.

**Independent of PR 1's docs-block fix.** This test has been broken for a while; nobody noticed because runs against staging silently no-op'd (A1's broken baseUrl).

**Fix options for the auditor:**
- Point `14-delete-test-user.bru:8` at a disposable username (e.g. `bruno.test.<ts>` created by `04-create-user.bru` earlier in the run) instead of `{{testUsername}} = batbern.organizer`. **This is the right fix** — `14-` is clearly meant to exercise GDPR-delete, not to nuke the auth user.
- OR rename `14-` to `99-delete-test-user.bru` so it runs after every other test in the collection. Quick but doesn't fix the underlying "tests should not destroy their own auth user" problem.

This also implies updating `B1`'s canonical-naming rules to make explicit that `testUsername` in `development.bru` must NEVER be the same user whose JWT is in `AUTH_TOKEN` — a footgun worth pinning down before PR 1 ships its 3 cleanup endpoints.

### F3 — `users-api/20-public-user-by-username.bru` returns 404 instead of 200 (pre-existing, cascades from F2)

Anonymous `GET /public/users/batbern.organizer` returns 404 from CUMS. Same root cause: `14-delete-test-user.bru` deleted `batbern.organizer` earlier in the run, and the public projection endpoint legitimately can't find them. The 404 is correct given the state — the test is wrong about the precondition.

**Fix:** falls out automatically once F2 is fixed (auth user no longer being deleted mid-run). No separate change needed.

### Cross-cutting note for the PR 5 auditor

These three failures together demonstrate a class of test-design bug this plan should explicitly track: **implicit dependencies on test order + shared mutable state in dev DB**. Section D.5 already mandates "run twice" — but that catches *idempotency* bugs, not *self-destruction* bugs like `14-` deleting its own auth user. Two adjustments worth considering for the D template:

1. **Add a `00-` pre-flight that asserts the auth user exists** — fail-fast if a previous run left the user deleted, instead of cascading into 5 confusing failures.
2. **Forbid tests that mutate `{{testUsername}}` when it equals the auth user.** Either route them through a disposable `bruno.test.<ts>` created earlier in the run, OR move them to the final 99-block.

Neither is mandatory for PR 5 — they're nice-to-have hardening for D. Captured here so the PR 5 auditor (or whoever later notices similar patterns in another collection) doesn't have to re-derive it.

## Context

Staging deploys at `https://api.batbern.ch` (production account 188701360969 — "staging" is a CDK envName, not a separate environment) keep breaking after Epic 11 despite all CI tests being green. Root cause: the Bruno API contract tests in `bruno-tests/` are wired into `.github/workflows/deploy-staging.yml` as **post-deploy warnings only** (`continue-on-error: true` at line 1226). They don't gate the deploy, they don't trigger rollback, and `bruno-tests/environments/staging.bru` even points to a nonexistent host (`api.staging.batbern.ch` instead of `api.batbern.ch`) so most of them haven't actually executed against real staging in months.

**The existing per-entity collections are already there and mostly fine.** `file-upload-api`, `companies-api`, `users-api`, `tasks-api`, `partners-api`, `speaker-portal-api` already follow per-entity boundaries with timestamp-suffixed test data and inline cleanup tests. They need a light audit + small targeted patches — not a redesign. The one collection that genuinely needs splitting is the monolithic 67-test `events-api`, which today bundles events + topics + sessions + speakers + speaker-pool + reminders.

The actual fix is three things:
1. **New infra**: per-service cleanup endpoint, runner flags to iterate one folder at a time, Bruno-failure → rollback workflow, ECR `staging-stable` tag, fix the broken staging baseUrl.
2. **One structural change**: split `events-api` into 6 smaller collections in dependency order.
3. **Light audit pass** over every collection: standardize on canonical per-entity prefixes (see B1) where inconsistent, add a 2-file unconditional cleanup hook (`00-pretest-cleanup.bru` + `99-posttest-cleanup.bru`), tighten DELETE-then-GET-404 assertions. Most collections need 5-30 minutes of work, not a rewrite.

**Branch strategy:** Parent test-enhancement branch off `develop` (`test-enhancement/bruno-staging-hardening`), with **stacked PRs**: one infra PR, then 11 small per-entity PRs in dependency order, then a final gate-flip PR. The plan document itself lands as `docs/plans/bruno-staging-hardening.md` in the infra PR.

**Risk callout — this is production data.** Cleanup endpoint role-checked + prefix-regex-locked + audit-logged.

## Reality-check on the starting frame

Validated during exploration — important corrections:

- `circuitBreaker: { rollback: true }` **already exists** at `infrastructure/lib/constructs/domain-service-construct.ts:243-246`. It catches startup failures only — not "starts but returns 500s." Bruno fills that gap.
- `deploymentAlarms` are missing (line 299 has a circular-dep comment). Solvable.
- Existing collections **already** use timestamp suffixes (`bruno-test-{{$timestamp}}`), so they're already mostly idempotent. The gap is **inconsistent prefixes** (`bruno-test-`, `brtest`, none) and **conditional cleanup** (final `99-cleanup-*.bru` only runs if earlier tests passed). That's what the audit pass fixes.
- The legacy `deploy-production.yml:362-429` rollback job references the **decommissioned account 422940799530**. Dead code. Fix in same PR.
- `run-bruno-tests.sh:120` references a `speakers-api` collection that **doesn't exist on disk**. Silently skipped today.
- `speaker-portal-api` correctly stays dev-only — it depends on `E2ETestTokenController` which is gated by `@Profile({"dev","local","test","development"})` and is inactive in production-account services. Don't touch it.

## Branching & rollout

Parent branch: `test-enhancement/bruno-staging-hardening` off `develop`.

| # | Branch | Scope | Bruno gate state after merge |
|---|--------|-------|------------------------------|
| 1 | `test-enhancement/bruno-staging-hardening-infra` | A + B below. Cleanup endpoints, runner flags, ECR `staging-stable` tag, deployment alarms, Bruno-failure rollback job wired (still `continue-on-error: true`). One-time SQL pre-cleanup script for the 17 legacy rows. ~~Server-side `@Pattern` validation fix on `companies.name`~~ — deferred, see "Bugs confirmed by audit". Plan doc lands here. | warning |
| 2 | `test-enhancement/bruno-events-api-split` | C: decompose events-api into 6 collections | warning |
| 3-13 | `test-enhancement/bruno-audit-{entity}` | D: light audit pass on each collection in dependency order. One PR per collection. | warning |
| 14 | `test-enhancement/bruno-gate-flip` | E: remove `continue-on-error`. Prove rollback works with deliberate-fail test. | **blocking + auto-rollback** |

## A. Infrastructure foundation (PR 1)

### A1. Fix broken staging URL (one-line change, do first)
`bruno-tests/environments/staging.bru` line 2: change `https://api.staging.batbern.ch/api/v1` → `https://api.batbern.ch/api/v1`. Until this is fixed, every "staging" Bruno run silently hits DNS-NXDOMAIN and `continue-on-error: true` swallows it. **Pair this fix with B's cleanup endpoint** so the first real staging run after the fix doesn't leave junk.

### A2. Runner enhancements (`scripts/ci/run-bruno-tests.sh`)
- `--collection <name>` flag — iterate one folder at a time (`./run-bruno-tests.sh staging --collection companies-api`).
- `--cleanup-only` mode — runs only `99-posttest-cleanup.bru` files. Belt-and-suspenders after a failed collection.
- `--no-bail` — relax `set -e` around the collection loop so a failed test inside a collection doesn't orphan that collection's cleanup file. Track failure in a flag; still exit non-zero at the end.
- Remove the dead `speakers-api` reference at line 120.

### A3. ECS deployment alarms (resolves circular-dep at `domain-service-construct.ts:299`)
Before creating the `FargateService`, compute `const alarmName = \`${envName}-${serviceName}-5xx-rate\`` (string only, no service-attribute dependency). Create:
- CloudWatch alarm on API Gateway `5XXError` filtered to this service's route, > 5% over 2 min.
- Alarm on `RunningTaskCount < desiredCount` over 3 min.

Pass `deploymentAlarms: { alarmNames: [...], rollback: true }` to the service. Skip target-group alarms (Service Connect topology, no ALBs). Don't pursue CodeDeploy blue/green — circuit breaker + alarms is the right tool.

### A4. ECR last-known-good tag strategy
Three tags per ECR repo (`batbern/staging/{service}`):
- `<sha7>` — immutable; task definitions pin to this.
- `staging-current` — what was just deployed.
- `staging-stable` — last-known-good (last Bruno-green deploy).

Rollback resolves `staging-stable` → SHA → registers a task definition with that SHA. Mutable tags alone don't trigger ECS redeploys; pin to SHAs.

### A5. Bruno-failure rollback job (workflow refactor)
Split monolithic `deploy-staging.yml` into 4 jobs:
1. `deploy` (current CDK steps).
2. `bruno-tests` (needs `deploy`).
3. `tag-stable-on-success` (needs `[deploy, bruno-tests]`, runs on Bruno success). Promotes `staging-current` → `staging-stable` for all 5 ECR repos.
4. `rollback-on-bruno-failure` (needs `[deploy, bruno-tests]`, runs on Bruno failure). Invokes `scripts/ci/rollback-deployment.sh staging --yes`. Posts Slack notification.

`scripts/ci/rollback-deployment.sh`: replace interactive `sleep 10` at line 33 with `[ "$2" = "--yes" ] || sleep 10`. Switch image-tag lookup from `deployments[1]` to `staging-stable` ECR resolution. Add `--dry-run`.

**While in this file, fix `deploy-production.yml:362-429`** (decommissioned account 422940799530 reference) — same shape of fix, same PR.

## B. Test data isolation + cleanup (PR 1)

### B1. Canonical naming convention — must fit real PK/format constraints

Constraints verified against Flyway migrations + JPA + OpenAPI specs. **Several of the "obvious" patterns are illegal** under these constraints — the table below is binding, not aspirational.

| Entity | Source of constraint | Constraint | Canonical pattern | Filled length |
|--------|----------------------|-----------|-------------------|---------------|
| companyName | `V3__Create_companies_schema.sql:7` + `companies-api.openapi.yml` | VARCHAR(255), `^[A-Za-z0-9]+$` (existing tests use hyphens though — verify hyphen acceptance in PR 1) | `BRUNOTESTCO<13-digit-ts>` (alphanumeric, safest) **OR** `BRUNO-TEST-CO-<ts>` if hyphens confirmed | 24 / 30 chars |
| username | `V4__Create_user_profiles_table.sql:13,59-60` | VARCHAR(100), DB regex `^[a-z]+\.[a-z]+(\.[0-9]+)?$` — **dots required, no underscores, no hyphens** | `bruno.test.<13-digit-ts>` | 24 chars |
| email | `V4__Create_user_profiles_table.sql` | VARCHAR(255), RFC 5321 | `bruno-test-<ts>@e2e.batbern.invalid` (existing convention, reserved domain) | 36 chars |
| eventCode | `V3__Add_event_code_and_organizer_username.sql:6` | VARCHAR(50), `@Size(max=50)` | `BRUNO-TEST-<13-digit-ts>` | 24 chars |
| sessionSlug | `sessions.session_slug` | VARCHAR(200) | `bruno-test-session-<ts>` | 33 chars |
| topicCode | `V24__add_topic_code_column.sql:6` | VARCHAR(255), lowercase-hyphens convention | `bruno-test-topic-<ts>` | 31 chars |
| partnerCompanyName | `V2__create_partner_coordination_schema.sql:9` ⚠️ known bug | **VARCHAR(12) — extremely tight** | `brtest<6-digit-id>` (matches existing `brtest{{$randomInt}}`) | ≤12 chars |
| filename (file-upload) | `file-upload-api.openapi.yml` | maxLength 255 | `bruno-test-<ts>.png` | 30 chars |
| registrationCode | `registrations.registration_code` | VARCHAR(100) | `BRUNO-<ts>-<6char-rand>` | ≤28 chars |

**Prefix-detection regexes** (for cleanup endpoint validation — locked in service code):
- Uppercase alphanumeric (companies): `^BRUNOTESTCO[0-9]+$` and/or `^BRUNO-TEST-[A-Z0-9-]+$`
- Lowercase-dotted (usernames): `^bruno\.test\.[0-9]+$`
- Hyphenated lowercase (sessions, topics, files): `^bruno-test-[a-z0-9-]+$`
- Hyphenated mixed-case (eventCode, registrationCode): `^BRUNO-(TEST-)?[A-Z0-9-]+$`
- Short partner: `^brtest[0-9]+$`

Cleanup endpoint accepts a single canonical prefix string per call (one per entity type), validates against the entity-specific regex above before issuing DELETE. The "single BRUNO_TEST_ prefix everywhere" idea from the previous draft is incompatible with reality — entities use different conventions; cleanup must match each.

Document the full table once in new `bruno-tests/README.md`. **PR 1 must verify whether company name hyphens are accepted server-side** (the OpenAPI pattern says alphanumeric-only but existing tests use hyphens — one of those is wrong, and we need to know which before locking the pattern).

Most existing collections already use compatible prefixes (e.g. companies-api uses `bruno-test-company-*`, partners use `brtest*`). The audit pass (D) normalizes to the canonical patterns above and updates any prefix-detection that diverges.

### B2. Per-service cleanup endpoint
**Gating (per decision: ROLE_ORGANIZER + regex prefix):**
- `POST /api/v1/admin/test-fixtures/{cums|ems|pcs}/cleanup` — one path per owning service.
  The per-service path discriminator is required because `api-gateway`'s `DomainRouter` falls
  through `/api/v1/admin/*` → EMS by default (line ~103); a single shared path would make
  CUMS and PCS cleanup endpoints unreachable via the gateway. Each path prefix is matched
  explicitly in `DomainRouter.determineTargetService` BEFORE the generic `/admin` fallback.
  Discovered 2026-05-25 during F2 (admin-cleanup-api Bruno collection) authoring.
- `@PreAuthorize("hasRole('ORGANIZER')")`.
- Body specifies `entityType` (companies | users | events | sessions | topics | tasks | partners | registrations | speaker_pool | uploads) and `prefix`.
- **Server-side constant `Map<EntityType, Pattern>`** validates the prefix against the entity-specific regex from B1 — request body cannot supply the pattern, only the literal prefix value to match. Reject anything that doesn't match the bound pattern for the given entity type. This prevents `prefix=*` / `prefix=BAT` / SQL-injection-shaped inputs at the controller layer.
- Audit-log every invocation (caller username, entityType, prefix, deletion counts).
- Per-service (not gateway fan-out) for blast-radius control.

**Cleanup order inside the endpoint** (respects FK constraints):
1. registrations → 2. session-speakers → 3. sessions → 4. speaker-pool entries → 5. event-topics → 6. tasks → 7. events → 8. topics → 9. partner-topic-votes/contacts/meetings → 10. partners → 11. users (matching `bruno.test.*`) → 12. companies → 13. file uploads (S3 + DB metadata).

**Event state machine:** dedicated `TestFixtureRepository.hardDeleteByPrefix(...)` issues raw `DELETE FROM events WHERE event_code LIKE 'BRUNO-TEST-%'` with cascade. Bypasses state guards, only reachable from this endpoint.

**Startup invariant check:** at boot, count real entities matching the canonical patterns. Log `WARN` if > 0 — don't fail-fast (might be junk from a previous failed run that this cleanup will sweep), but operators should see it.

**Deferred — partner_meetings cleanup coverage** (discovered 2026-05-25, address in PR 13 or earlier):

PCS owns `partner_meetings` but the table has no FK to `partners` — meetings are standalone events that link to partners only via `partner_meeting_attendance` / `partner_meeting_rsvps` (which cascade from `partners`, not from the meeting itself). The PR 1 PCS cleanup endpoint only handles `entityType=partners`, so:

- Deleting a `brtest*` partner removes its attendance + RSVP rows but **leaves the underlying `partner_meetings` rows in place**.
- Bruno's `partner-meetings-api` collection creates ad-hoc meetings (`meeting_type='ad_hoc'`, `scheduled_date=…`) with no Bruno-identifying column — there's no `bruno-test-` prefix on a meeting row to match against.

Options for PR 13 (partners-api audit) or a sibling PR (partner-meetings-api audit, currently not in the rollout — would need to be added):

1. **Add a `meeting_id` allowlist parameter** to the cleanup endpoint — Bruno collects IDs from create-meeting tests via `bru.setVar` and posts them back to cleanup. Tighter than a prefix; safe even though meetings lack a Bruno-prefix column. Recommended.
2. **Add a marker column** to `partner_meetings` (e.g. `created_by_test_fixture BOOLEAN`) — schema change, larger blast radius, only do this if the allowlist proves too clunky.
3. **Constrain by `created_at` window** — Bruno cleanup deletes meetings created in the last N minutes by the cleanup caller. Risky on a shared staging account (could nuke a concurrent organizer's meeting).

Recommendation: option 1, scoped to a new `meetings` entityType on the PCS cleanup endpoint. Not blocking PR 1 because the staging audit (2026-05-24) found 0 stray partner_meetings rows — Bruno meetings-api isn't run in CI today against staging (deferred to its first audit).

### B3. Per-collection cleanup `.bru` files
Each collection gets:
- `00-pretest-cleanup.bru` — POST to cleanup endpoint with that entity's prefix. Status assertion `oneOf([200, 204, 404])` — never fails the run.
- `99-posttest-cleanup.bru` — same.

Bruno orders by filename → `00-` first, `99-` last. Two-layer defense: `run-bruno-tests.sh` ALSO invokes `--cleanup-only` after each collection regardless of pass/fail.

## C. events-api decomposition proposal (PR 2)

The only collection that genuinely needs splitting. Today `bruno-tests/events-api/` is 67 sequential tests covering 6 conceptually-distinct entity domains. Proposed split:

| New collection | Source tests | New tests needed | Auth |
|----------------|--------------|------------------|------|
| `event-types-api` | (none — currently untested) | All new: GET /types, idempotent PUT. **No create/delete** — API gap, not a test bug. Document the gap. | organizer |
| `event-topics-api` | 30, 30a, 31, 32, 33, 34, 67 | 00-/99- cleanup hooks. 30a (fixture event) becomes `00a-fixture-event.bru` | organizer |
| `events-crud-api` | 01, 03–07, 19, 29 | 00-/99- cleanup, DELETE-then-GET-404 | organizer |
| `sessions-api` | 08–11, 20–28 | 00-/99- cleanup, fixture event setup | organizer |
| `speaker-pool-api` | 35–56, 60–66 | 00-/99- cleanup. **Promote tests (43–48) provision real Users in CUMS** — cleanup must wipe `bruno.test.*` users via CUMS endpoint (cross-service) | organizer |
| `event-full-workflow-api` | 12, 13, 16 | Net-new tests advancing through all 8 states: CREATED → TOPIC_SELECTION → SPEAKER_IDENTIFICATION → SLOT_ASSIGNMENT → AGENDA_PUBLISHED → EVENT_LIVE → EVENT_COMPLETED → ARCHIVED + registrations CRUD | organizer |

`speaker-portal-api` stays unchanged and dev-only. After the split, `bruno-tests/events-api/` directory ceases to exist (no overlap with the new collections).

The `speaker-pool-api` collection is the hairy one — its cleanup spans events service (pool entries) + CUMS (provisioned Users). Make sure CUMS cleanup endpoint from PR 1 supports the cross-service prefix wipe before merging PR 2.

## D. Light audit pass on each collection (PRs 3–13)

**Most collections are already structurally fine — this is a tune-up, not a rewrite.** Per-collection checklist:

1. **Glance through verbs vs OpenAPI** — note any missing CRUD operations as known gaps (e.g., event-types has no DELETE). Don't try to add backend endpoints in this scope.
2. **Normalize prefix** — search-and-replace `bruno-test-` / `brtest` / inconsistent variants → canonical patterns per B1. Usually 1–5 line changes per collection.
3. **Add `00-pretest-cleanup.bru` + `99-posttest-cleanup.bru`** calling the cleanup endpoint with that entity's prefix.
4. **Audit DELETE assertions** — every DELETE test followed by a GET that asserts 404. Today most just trust the 204.
5. **Run twice** — `./scripts/ci/run-bruno-tests.sh staging --collection <name>` twice in a row. First run cleans + creates + cleans. Second run starts dirty (intentionally — to verify pretest-cleanup is real), then green. If green ×2 → commit, open PR.

**Order** (dependency-first, lowest-coupling-first):
1. `file-upload-api` — leaf, no cross-entity coupling
2. `companies-api`
3. `users-api` — depends on companies (optional companyName ref)
4. `tasks-api` — depends on events
5. `event-types-api` (from C split) — GET-mostly, light
6. `event-topics-api` (from C split)
7. `events-crud-api` (from C split)
8. `sessions-api` (from C split) — needs event + speaker
9. `speaker-pool-api` (from C split) — cross-service cleanup, highest risk
10. `event-full-workflow-api` (from C split) — longest collection
11. `partners-api` — depends on companies + users

`speaker-portal-api`: skip (dev-only, unchanged).

Expected size: 6 of these PRs probably 5-50 line diffs (existing collections are mostly fine). The 5 new-from-events-split PRs are larger.

## E. Gate flip (PR 14)

Only after every PR 2-13 has been merged AND passed staging CI green twice in a row:

1. Remove `continue-on-error: true` at `deploy-staging.yml:1226`.
2. Confirm the `rollback-on-bruno-failure` job from A5 is wired (`if: needs.bruno-tests.result == 'failure'`).
3. Run F1's deliberate-fail exercise (below) to prove the rollback path on a feature branch before merging this PR.

## F. Verification

### F1. Test the rollback path without breaking real users

Layered, increasingly real:

1. **Dry-run mode** — `scripts/ci/rollback-deployment.sh staging --dry-run` prints what it would do without calling `aws ecs update-service`. Verifies permissions, credentials, image-tag lookup.
2. **Deliberate-fail on a branch** — add one Bruno test asserting `expect(res.getStatus()).to.equal(999)`. Push branch → run `workflow_dispatch` of staging deploy from that branch → confirm: deploy succeeds → bruno-tests fails → rollback-on-bruno-failure runs → ECS task defs pin to `staging-stable` SHA → close branch and let `develop`'s normal deploy restore current. Pick a low-traffic window (BATbern ~3 events/year).
3. **Quarterly disaster drill** — separately, push a container that exits on startup; confirm circuit breaker rolls in-place within 5 min.

### F2. Verify cleanup endpoint authorization

Before PR 1 merges, exercise the endpoint in development for every entity type:
- Non-organizer JWT + any payload → 403
- Organizer + `entityType=companies` + `prefix=BRUNOTESTCO` → 200 with deletion count
- Organizer + `entityType=users` + `prefix=bruno.test.` → 200 with deletion count
- Organizer + `entityType=users` + `prefix=BRUNOTESTCO` → 400 (prefix doesn't match the username regex)
- Organizer + `entityType=companies` + `prefix=BAT` → 400 (would match real entities)
- Organizer + `entityType=companies` + `prefix=` (empty) → 400
- Organizer + `entityType=companies` + `prefix=BRUNOTESTCO'; DROP TABLE...` → 400 (regex enforcement at controller layer)
- Organizer + `entityType=foo_bar` (unknown) → 400
- Real-data tables: query rows matching each canonical regex before/after; confirm 0 unintended deletions.

Add as a new `bruno-tests/admin-cleanup-api/` collection that runs FIRST in `run-bruno-tests.sh`, so authorization regressions are caught before any test creates state to clean.

### F3. End-to-end on staging after PR 14

1. Push trivial change to `develop` → deploy-staging.yml runs.
2. Confirm `bruno-tests` job is now blocking.
3. Check ECR: `staging-stable` = previous SHA, `staging-current` = new SHA.
4. Trigger a deliberate-fail again on a branch → confirm rollback runs and Slack notification arrives.
5. Cleanup audit logs in CloudWatch show no anomalies.

## G. Local pre-flight (every PR)

`bruno-tests/environments/development.bru` already targets `http://localhost:8000/api/v1`, and `scripts/ci/run-bruno-tests.sh` accepts `development` as its env arg. **Every PR in this plan should run its Bruno tests against local dev once before pushing.** Same code, same Spring Boot config, same Postgres, same staging Cognito for JWT — local catches regex / route / config drift that Java integration tests miss (because they bypass the gateway and call the service directly), and surfaces wiring issues seconds after a save instead of minutes after a CI run.

### Coverage matrix — what local dev validates ahead of staging

| PR scope | Local-dev coverage |
|----------|--------------------|
| **B2 cleanup endpoints** (CUMS done; EMS + PCS upcoming) | ✅ Full — regex map, ROLE_ORGANIZER gate, cascade order, audit logging |
| **B3 per-collection `00-`/`99-` cleanup hooks** | ✅ Full — HTTP-level, env-agnostic |
| **F2 `admin-cleanup-api` auth matrix** | ✅ Full — develop the 8 cases locally first; same collection then runs against staging |
| **C events-api decomposition + 6 new collections** | ✅ Full — Bruno collection structure is env-agnostic |
| **D per-collection audit pass** (PRs 3-13) | ✅ Full — every D.5 "run twice" step runs locally first |
| **Cross-service cleanup order** (PCS → EMS → CUMS) | ✅ Full — end-to-end via Bruno |
| **A2 runner flags** (`--collection`, `--cleanup-only`, `--no-bail`) | ✅ Full — already exercised against `development` |
| **A5 `rollback-deployment.sh`** | 🟡 Partial — `--dry-run` verifies arg parsing + ECR tag-lookup; AWS calls skipped |
| **A3 ECS deployment alarms** | ❌ Staging-only — CloudWatch + ECS deployment controller has no local analogue |
| **A4 ECR `staging-stable` tag promotion** | ❌ Staging-only — ECR is AWS |
| **A5 `rollback-on-bruno-failure` workflow job** | ❌ Staging-only — fires only in GitHub Actions |
| **One-time `bruno-staging-pre-cleanup.sql`** | ❌ Staging-only — the 17 target rows only exist in the staging DB |
| **Real auto-rollback ECS task-def swap** | ❌ Staging-only — deferred to F1's deliberate-fail exercise |

### Prerequisite: Pattern 3b user mirroring

Staging-issued JWTs (from `~/.batbern/staging-organizer.json`) work locally because `shared-kernel/.../security/JwtRolesConverter` falls back to the local DB when `custom:role` is empty (Epic 11.E.7 Pattern 3b). **The `user_profiles` row must exist in the local DB.** If cleanup-endpoint calls return `403 Forbidden` locally but pass in staging:

```bash
./scripts/dev/sync-users-from-cognito.sh   # mirror staging users → local DB
./scripts/ci/run-bruno-tests.sh development --collection admin-cleanup-api
```

See `docs/architecture/06b-user-lifecycle-sync.md` §"Pattern 3b" for the full pattern.

### Per-PR workflow

1. Code + Java unit/integration tests green (TDD red-green-refactor).
2. **Local pre-flight**: `make dev-native-up` + the relevant `./scripts/ci/run-bruno-tests.sh development --collection <name>` run.
3. Commit + push.
4. Staging CI re-runs the same Bruno tests (still `continue-on-error: true` until PR 14).
5. After PR 14: staging Bruno is the deploy gate; local pre-flight remains the first signal.

For PR 1 specifically, the EMS and PCS cleanup-endpoint work is fully exercisable locally (Section B is ~90% of remaining PR 1 scope by line count) — only the workflow / alarm / ECR pieces of Section A fall through to staging-only verification.

## Critical files

| Path | Change |
|------|--------|
| `bruno-tests/environments/staging.bru` | Fix baseUrl line 2 |
| `bruno-tests/README.md` (new) | Document canonical naming convention |
| `bruno-tests/admin-cleanup-api/` (new) | Authorization test collection (F2) |
| `bruno-tests/{file-upload,companies,users,tasks,partners}-api/00-pretest-cleanup.bru` + `99-posttest-cleanup.bru` (new) | Per-collection cleanup hooks for existing collections |
| `bruno-tests/{event-types,event-topics,events-crud,sessions,speaker-pool,event-full-workflow}-api/` (new, ×6) | Split from events-api |
| `bruno-tests/events-api/` | Removed after PR 2 |
| `scripts/ci/run-bruno-tests.sh` | Add `--collection`, `--cleanup-only`, `--no-bail`; remove speakers-api ghost; relax `set -e` around collection loop |
| `scripts/ci/rollback-deployment.sh` | `--yes`, `--dry-run`, switch to `staging-stable` ECR tag |
| `.github/workflows/deploy-staging.yml` | Split into 4 jobs; ECR retag steps; flip `continue-on-error` in PR 14 |
| `.github/workflows/deploy-production.yml:362-429` | Fix dead account-422940799530 reference |
| `infrastructure/lib/constructs/domain-service-construct.ts:299` | Add `deploymentAlarms` with statically-named alarms |
| `services/*/src/main/java/.../controller/TestFixtureCleanupController.java` (new, ×5) | Per-service cleanup endpoint |
| `scripts/db/bruno-staging-pre-cleanup.sql` (new) | One-time exact-match deletion of 17 legacy test rows (10 companies + 7 partners, with cascade dependents). Runs once when PR 1 deploys. |
| `docs/plans/bruno-staging-hardening.md` (this file) | Checked-in plan artifact |

### Service ownership mapping (discovered 2026-05-24)

All 5 microservices share the `public` schema. Only **3 services** own entity tables:

| Service | Owns tables |
|---------|-------------|
| **CUMS** (company-user-management-service) | companies, user_profiles, role_assignments, user_additional_emails, logos |
| **EMS** (event-management-service) | events, event_types, event_tasks, event_photos, event_teaser_images, topics, topic_usage_history, sessions, session_users, session_materials, session_content_history, session_timing_history, registrations, speaker_pool, speaker_invitation_tokens, speaker_outreach_history, speaker_arrivals, speaker_slot_preferences, speaker_reminder_log, speaker_status_history |
| **PCS** (partner-coordination-service) | partners, partner_meetings, partner_meeting_attendance, partner_meeting_rsvps, partner_notes, topic_suggestions, topic_votes |

Speaker-coordination-service and attendee-experience-service own NO tables (still foundation phase) — no `TestFixtureCleanupController` needed there. PR 1 ships 3 cleanup controllers, not 5.

Cross-service cleanup order (Bruno cleanup .bru files orchestrate):
1. PCS (deletes partner-side rows first — they reference companies + users by name)
2. EMS (deletes events + cascades — they reference users by name)
3. CUMS (foundation — last to delete, deletes companies + users + logos)

## Reuse — existing functions/utilities

- `scripts/auth/get-token.sh` + `refresh-token.sh` — already handle multi-role staging tokens.
- `AbstractIntegrationTest` (shared-kernel) — base for Java tests covering the cleanup controller.
- `E2ETestTokenController.java` — pattern reference for `TestFixtureCleanupController.java`, but use `@ConditionalOnProperty` + `@PreAuthorize` instead of `@Profile`.
- Bruno's existing `bru.setEnvVar()` / `bru.setVar()` pattern — keep for identifier handoff within collections.
- Existing `circuitBreaker: { rollback: true }` at `domain-service-construct.ts:243-246` — stays; A3 only ADDS `deploymentAlarms`.
- Existing per-collection `99-cleanup-test-*.bru` files — keep them, the new `99-posttest-cleanup.bru` runs *after* them as a final defense.

## Risks to track

1. **Staging account = production.** Every "test against staging" is "test against production." Cleanup endpoint is the only thing standing between Bruno and real data — per-entity regex prefix lock + role check + audit log + startup invariant warn are all required.
2. **First real cleanup run after A1's URL fix will likely delete pre-existing test junk** in the production-account DB (months of accumulated leftovers from runs that hit DNS-NXDOMAIN). Pre-audit: query rows matching each entity's canonical regex from B1 before merging PR 1 and confirm with user that everything matched is disposable. _(Pre-audit results table at the top of this file.)_
3. **`deploy-production.yml:362-429` references decommissioned account 422940799530.** Same-PR fix in A5.
4. **`scripts/ci/run-bruno-tests.sh:4 set -e`** silently masks token-loading failures. Audit before relying on the script as a deploy gate.
5. **Circuit breaker only catches startup failures.** Until A3's deployment alarms ship, Bruno is the only safety net for "container starts but serves 500s." Ship A3 in PR 1 so both arrive together.
6. **`speaker-pool-api` cleanup is cross-service** (events + CUMS). PR 11 (its audit) needs PR 1's CUMS cleanup endpoint to exist — sequencing is built in already, but flag if anything slips.
7. **Constraint surprises to verify in PR 1 — these are likely bugs the test work uncovers**:
   - `partners.company_name VARCHAR(12)` vs `companies.name VARCHAR(255)` is a real bug (partners can only reference companies whose names are ≤12 chars). Don't fix in this plan's scope, but file as a separate ticket on first hit. Leave a comment in `V2__create_partner_coordination_schema.sql:9`.
   - `companies-api.openapi.yml` says company name regex is `^[A-Za-z0-9]+$` (alphanumeric only) but `companies-api/01-create-company.bru` uses hyphens and tests pass. Either the OpenAPI is wrong or server validation isn't enforced — PR 1 reproduces and reports. Whichever is wrong is a bug-fix commit in PR 1 or follow-up.
   - `users-api` test data may not match the DB regex `^[a-z]+\.[a-z]+(\.[0-9]+)?$` (existing tests use hyphens — verify in PR 5's audit). If existing tests pass despite hyphens, the regex isn't enforced server-side: another bug to file.
   - Any other "test passes but shouldn't" mismatches discovered during the audit pass land as inline fix-commits in the relevant PR (per user: "fixing some bugs we prob. find on the way").
