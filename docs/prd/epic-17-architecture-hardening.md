# Epic 17 — Architecture hardening

**Author:** John (PM), from Winston's architecture review · **Date:** 2026-11-07 · **Status:** 📋 backlog, managed as BMAD v7 initiative `_bmad-output/initiative-architecture-hardening/` (five epics, 2026-11-07). Scope decided by the owner 2026-11-07; each story still needs the owner's go before it starts.
**Source:** [`docs/plans/architecture-review-2026-11-actions.md`](../plans/architecture-review-2026-11-actions.md) (action list, owner decisions, measured baseline). Report: https://claude.ai/artifact/L4gaZoqWMDUnV7vLGcsdbh (private). Stories 17.0a–d: [`docs/plans/test-fidelity-proposal-2026-11.md`](../plans/test-fidelity-proposal-2026-11.md) (Winston, 57 production bugs since 2026-08 classified; added on owner go 2026-11-07).
**Architecture:** [`module-map.md`](../architecture/module-map.md), [ADR-016](../architecture/ADR-016-service-directions-and-service-to-service-calls.md) (new), ADR-003 amendment 2026-11 (UUIDs in paths), ADR-002 decision 2026-11-07 (one upload contract), ADR-014 decision 2026-11-07 (stubs and bus removed), ADR-008 update.

## Epic goal

The next feature lands in the right place without anyone having to remember where that is. The
platform stops carrying code and infrastructure nobody calls, the three services call each other in
one direction over one transport, the public API follows one set of conventions, and EMS gets walls
that a build can check. Every rule this epic introduces is enforced by a gate (ArchUnit, OpenAPI lint,
contract check or test), not by a document.

## Problem (measured 2026-11-07, develop `89b3618f` after the rebase)

- **Dead surface.** Two reference-only specs (`speakers-api`, `attendees-api`), 6 endpoints with no
  frontend, Watch or Bruno caller, and a frontend service method (`getPublishPreview`) calling a path
  that exists nowhere. Two ECS stacks run or exist for services with no routes (SpeakerCoordination
  desired 1 / running 1, AttendeeExperience 0 / 0). An EventBridge bus with 0 rules receives from five
  publishers.
- **Cycles and two transports.** EMS↔CUMS and EMS↔PCS call each other both ways. 9 client URLs go to
  `/internal`, 12 go through the public API with the user's JWT, which forces machine-only endpoints
  into the public specs.
- **Conventions drift.** 342 operations, 58 with a UUID in the path. Verb endpoints, twin endpoints
  for `me` and `{username}`, a snake_case path, three different speaker path keys, near-duplicates
  added in the last week (`/newsletter/my-subscription` and `/my-subscriptions`; logo requests under
  `/users`).
- **Uploads.** 7 presign/confirm pairs, 3 upload-from-url endpoints and 8 upload service classes for
  one mechanism. EMS and CUMS both write the one `logos` table in the shared database.
- **EMS has no walls.** 64,376 LOC (74% of the backend), 138 migrations. Module check against the
  target layering: 52 allowed pairs, 28 violating pairs, 100 references. `EventController` is
  2,697 lines and implements 4 generated interfaces plus 14 hand-mapped registration endpoints.
- **Tests replace the part of reality that holds the defect** (test-fidelity proposal). Of 57 production
  bugs since 2026-08: a mock hid the defect in 15, the test environment differed from production in 13,
  production-shaped data was missing in 12, a cross-service contract drifted in 11. 329 of 441 frontend
  test files use `vi.mock` (177 mock our own `@/services`), 2 use MSW; EMS has 38 `@MockitoBean
  UserApiClient`; no contract tooling. Bruno and Playwright run real code only after the production
  deploy. Without a harness, the S2S moves (17.6–17.8) and breaking API changes (17.9–17.11) stay
  green when the caller is wrong.
- **No gate exists for any of this.** No ArchUnit and no Spectral in the repo (checked 2026-11-07).
  ShedLock is in CUMS and EMS only, not in PCS.

## Decisions taken (owner, 2026-11-07)

1. Stop and remove the SpeakerCoordination and AttendeeExperience services and stacks in production.
2. Remove the EventBridge bus and its publishers. Keep in-process Spring events
   (`ApplicationEventPublisher`; 2 `@EventListener`s consume them).
3. `POST /events/{c}/slides-online/send` goes now. That clears the way for 16.8c.
4. **API conventions are enforced with breaking changes.** The frontend changes in the same story.
   No deprecation period (one production, one client).
5. S2S token rule (ADR-016): forward the user's JWT when present, never require it, `/internal`
   authorizes by operation.
6. ADR-003 UUID rule as amended.
7. Uploads: one contract `/api/v1/uploads/{purpose}` (ADR-002 decision 2026-11-07).
8. Test fidelity first where it protects later stories: 17.0a–d at the head of the epic; 17.6 waits for
   17.0a + 17.0b, 17.9 and 17.11 for 17.0c + 17.0d (17.11 confirmed by Winston 2026-11-07) (owner go 2026-11-07, via Winston). The proposal's P5
   (real-code gate before production) is **not** a story: owner decision pending, design under revision.

## Rules for every story in this epic

- **Architecture note.** Each story file starts with the 5-line note from `module-map.md`: modules
  touched · new cross-module or cross-service dependencies · new or changed endpoints, each with the
  nearest existing one · components reused · deviations from the map and why. The drafts below are
  the starting point; `bmad-build` refines and confirms them in the plan's Design Notes before the
  first test (`_bmad/custom/bmad-build.toml`).
- **Named guard.** Every acceptance criterion that states an architectural property names the gate
  that proves it. A property without a gate is not done.
- **Re-measure.** A story that claims to remove violations re-runs
  `scripts/architecture/ems-module-deps.py` and `ems-module-target.py` and records before/after
  numbers in its Dev Agent Record.
- **Breaking API changes** update the spec, the generated types, the frontend callers, Bruno and
  Playwright in the same PR. The FE path contract check (17.3) proves nothing was missed.
- **Deploys.** A PR to `develop` deploys to production. One story = one PR = one deploy; batch the
  small ones (17.1 + 17.3, 17.4 + 17.5) where the owner agrees. Infrastructure stories (17.2) do not
  land on an event day.
- Cut branches from `origin/develop`. Push, then ask the owner before opening the PR.

## Story order

Protective and cheap first, then decoupling, then restructuring.

| # | Story | Depends on | Kind |
|---|---|---|---|
| 17.0a | Test rules and ratchet | – | rules + gate |
| 17.0b | S2S contract harness and production setup in tests | 17.0a | test infra |
| 17.0c | Frontend MSW harness | 17.0a, 17.3 (shared spec matcher) | test infra |
| 17.0d | Production-shaped fixtures | 17.0a | test infra |
| 17.1 | Dead API surface | – | dev |
| 17.2 | Dormant infrastructure: stubs and event bus | – | dev + prod infra |
| 17.3 | Frontend path contract check | 17.1 | gate |
| 17.4 | OpenAPI lint and spec-diff report | 17.1 | gate |
| 17.5 | ArchUnit freeze | – | gate |
| *16.8c* | *Mail Center consolidation (stays in Epic 16)* | *17.1* | *dev* |
| 17.6 | Service-to-service calls via `/internal` | 17.4, **17.0a, 17.0b** | dev |
| 17.7 | User-reference contract and request pull | 17.6 | dev |
| 17.8 | EMS↔PCS direction | 17.6 | dev |
| 17.9 | API conventions I (breaking) | 17.4, **17.0c, 17.0d** | dev |
| 17.10 | API conventions II (breaking) | 17.9 | dev |
| 17.11 | One upload contract, first purpose `company-logo` | 17.4, **17.0c, 17.0d** | dev |
| 17.12 | EMS modularization design | 17.5 | **design (Winston)** |
| 17.13 | Registrations out of `EventController` | 17.12 | dev |
| 17.14 | Domain exception base | 17.12 | dev |
| 17.15 | Mail core / mail center split | 17.12, 16.8c | dev |
| 17.16 | Event-core extension points | 17.12 | dev |

**20 stories** (17.0a–d, 17.1–17.16); 17.12 is a design task. 17.1–17.5 do not wait for 17.0a–d and
can run alongside them; 17.1, 17.2, 17.5 and 17.0a have no dependencies. 17.6 to 17.11 can run in
parallel once their dependencies are in, as long as only one PR is open at a time. Winston's estimate
(not measured): 17.0a–d are about 5–6 stories of work, so 17.0b and 17.0c may each split into two
PRs (as 16.8a/b did).

**Not stories yet:** the five remaining upload purposes after 17.11 (profile-picture, event-photo,
teaser-image, session-material incl. speaker portal, mail-attachment; one story each, created after
17.11 proves the pattern), the remaining EMS module moves (planning, publishing, ai) after 17.12, and
the follow-up reviews (database, frontend, schema, Spring gateway, Watch API; action list item 9).

---

## Stories

### Story 17.0a — Test rules and ratchet (proposal P1, P3, P4)

**As** the owner, **I want** one written rule for what a test may replace, and mock counts that can
only go down, **so that** new tests stop encoding the developer's belief and the 10,000 existing tests
improve without a rewrite.

**Scope:**
- **Mocking rule** in `docs/architecture/coding-standards.md` and in Amelia's dev workflow
  (`_bmad/custom/bmad-build.toml` / `bmad-agent-dev.toml` overrides, not the skill files): *mock only
  at the process boundary* (SES, S3, Cognito, OpenAI, SQS, the clock, another service's HTTP API at the
  wire, never its Java client). Layer table from proposal P1.
- **Assertion rules** (P3): absence needs a positive control; assert the effect, not the call; a fallback
  must be distinguishable from success; no silent catch in `@Async`/schedulers/listeners without a
  metric; wiring test at the parent for every callback prop that triggers a mutation. Also the
  P2.4 rules that need no infrastructure: cache tests read, mutate, read again; no test copies of
  production resources on the test classpath.
- **Touch rule** (P4): a story that changes a screen or service method moves that code's tests to the
  harness once it exists.
- Remove the contradiction in `_bmad-output/project-context.md`: `:201` says use MSW instead of mocking
  service modules, `:459` says mock the service layer (checked 2026-11-07). `:459` goes.
- **Ratchet** (P4): a CI check with a committed baseline counting, per file, (1) `vi.mock` of own
  `@/services`, `@/hooks` and child components; (2) `@MockitoBean` of in-process beans and of
  `UserApiClient`; (3) class-level `@Transactional` on write ITs. Fails when a count rises, and on stale
  baseline entries; a falling count updates the baseline in the same PR.

**Acceptance criteria:**
1. The rule and assertion rules are in `coding-standards.md`, the `bmad-build` override and project-context;
   the contradiction is gone. Guard: grep shows no "mock the service layer" guidance.
2. The ratchet runs in a required CI job. Guard: a test of the check with fixtures (count up fails,
   count down passes with baseline update, stale entry fails).
3. Baseline numbers recorded and compared with the proposal's measurement (329/177/112/87 frontend
   files, 106 `@MockitoBean` incl. 38 `UserApiClient`, 85 class-level `@Transactional` ITs in EMS).

---

### Story 17.0b — S2S contract harness and production setup in tests (P2.1, P2.4a–c)

**As** a developer moving a service-to-service call, **I want** the real client tested against the
provider's contract, **so that** a wrong path or parameter fails before production (#1072, #1159/#1181).

**Scope:**
- One contract test per client: `UserApiClientImpl`, `CompanyApiClient`, the PCS clients, the CUMS→EMS
  pushes (`HttpEventManagementMailCenterClient`, `HttpEventManagementUserMergeClient`). Real client
  against `MockRestServiceServer`; every request validated against the **provider's** OpenAPI (path
  exists, parameters within limits); responses taken from the provider spec examples.
- Provider side: a test per provider asserting its spec examples are valid responses of the real
  controller.
- Production security chain guard test in every service (as `ProductionSecurityFilterChainTest` in the
  gateway).
- Real `EmailService` against a mocked `SesClient` in tests; address validation no longer sits behind
  `sesClient == null` (also fixes the local-dev blind spot).
- Write paths with DB constraints: a non-`@Transactional` IT or `flush()` before the assertion.
- Start with `UserApiClientImpl` (38 mocks). Converting the existing `@MockitoBean UserApiClient`
  usages is gradual under the 17.0a touch rule, not this story.

**Acceptance criteria:**
1. Every S2S client has a contract test; a request to a path or parameter outside the provider spec
   fails it. Guard: a fixture reproducing #1072 (`limit=1000` against a spec max of 100) fails.
2. Every service has a production-chain guard test (anonymous write → 401, attendee on organizer
   endpoint → 403).
3. A joined `To:` line (#1176) fails a test without SES credentials. Guard: `EmailService` unit test
   with mocked `SesClient`.
4. Ratchet baseline (17.0a) does not rise.

---

### Story 17.0c — Frontend MSW harness (P2.2)

**As** a frontend developer, **I want** default MSW handlers for every API operation and a validator
against the spec, **so that** component tests render real screens with real hooks and services and fail
on a wrong path or shape.

**Scope:**
- `web-frontend/src/test/msw/`: a default handler per operation in `docs/api/*.openapi.yml`, data built
  through the generated types, a request validator that fails the test on a path or parameter not in
  the spec. The validator **imports 17.3's matcher** (`src/test/contract/specMatcher.ts`): one set of
  matching rules for CI and tests.
- Real i18next with the `en` locale files as the test default, replacing per-file `react-i18next`
  stubs for converted files.
- Convert the 10 worst mock-heavy test files, e.g. `EventPage.test.tsx` (13 mocked modules). The
  list is chosen from the 17.0a baseline.

**Acceptance criteria:**
1. A test calling a path not in the spec fails with the validator's message. Guard: a test of the
   harness itself.
2. The 10 converted files mock no own `@/services`/`@/hooks`/child components; the 17.0a baseline
   falls accordingly.
3. Handler data is typed from `src/types/generated/`; `npm run type-check` catches a shape drift.

---

### Story 17.0d — Production-shaped fixtures (P2.3)

**As** a developer writing a test, **I want** realistic data by default, **so that** unicode names,
case, namesakes and limits are exercised without anyone remembering to.

**Scope:** one shared fixture set per side (frontend builders, backend test fixtures), used by default:
`Hông Ân`, `O’Neill`, `王`, mixed-case e-mails, IDN domains, two namesakes, more rows than the page
limit, ties in the sort key, past and future events, every registration status. An ASCII-only builder
is the exception. Fixtures pass the real DB constraints (e.g. `chk_username_format`) and the generated
API types.

**Acceptance criteria:**
1. The fixture sets exist and are the default in the 17.0c harness and the backend test base.
   Guard: a test asserting each listed shape is present.
2. Regression tests for #1034 (umlaut search, >20 namesakes) and #1104 (`Hông Ân` username) run on the
   shared fixtures.

---

### Story 17.1 — Dead API surface

**As** a developer or agent searching the API, **I want** only endpoints that someone calls, **so
that** I extend the real one instead of a dead twin.

**Scope:**
- Delete `docs/api/speakers-api.openapi.yml` and `docs/api/attendees-api.openapi.yml`. Remove the
  commented-out generator blocks in both services' `build.gradle` and the entries in
  `.github/doc-drift-mappings.yml`.
- Delete 6 orphan endpoints (no frontend, Watch or Bruno caller, grep 2026-11-07), spec and code:
  `POST /logos/fetch-from-url`, `POST /events/{c}/speakers/invite-batch`,
  `POST /admin/backfill-speaker-names`, `POST /events/{c}/slides-online/send`,
  `POST /events/{c}/sessions/{s}/materials/upload-from-url`, `GET /events/{c}/publish/preview`.
- `slides-online/send`: two Bruno files mention `slides-online`
  (`tasks-api/01-list-task-templates.bru`, `events/mails-preview-invalid.bru`). Check whether they
  call the endpoint or only name a template or mail type before deleting.
- Frontend: delete `publishingService.getPublishPreview` and the `usePublishing` preview chain (it calls
  `GET /events/{c}/publish/{phase}/preview`, which does not exist). Correction at story creation:
  `LivePreview` references `fetchPreview`, but only from a retry button that can never render.
- Correction at story creation: `speakers-api` still generates `speakers-api.types.ts`, imported by 4
  frontend files. They move to `event-speakers-api.types` (identical shapes). The two specs also leave
  the ZAP matrix in `security-scan.yml`, or `check-zap-spec-coverage.py` fails.
- Regenerate frontend types.

Story file (BMAD v7): `_bmad-output/initiative-architecture-hardening/tickets/epic-dead-surface-and-gates/tickets/story-dead-api-surface-removed.md`.

**Architecture note (draft):** modules: EMS publishing, speakers, sessions/media, mail center; CUMS
logos · new dependencies: none · endpoints: 6 removed, none added · reused: n/a · deviations: none.

**Acceptance criteria:**
1. The 2 specs and 6 operations are gone from `docs/api/`; their controller methods, services only
   they used, and their tests are deleted. Guard: `EndpointAuthorizationGuardTest` and the build
   still pass; grep for each path in `services/`, `web-frontend/src`, `bruno-tests`, Watch returns 0.
2. `getPublishPreview` and `fetchPreview` are deleted. Guard: `npm run type-check` and Vitest pass.
3. Bruno run shows no `Skipping invalid file` and no failure from a removed path.
4. Operation count drops by 6 (from 342 at `89b3618f`, adjusted for whatever merged since); recorded
   in the story.

---

### Story 17.2 — Dormant infrastructure: stubs and event bus (closes #1216, bus part of #1220)

**As** the owner, **I want** to pay for and maintain only what runs, **so that** the infrastructure
matches ADR-014 and a dead path cannot fail a deploy.

**Scope:**
- Stop and remove the SpeakerCoordination and AttendeeExperience ECS services and CDK stacks, their
  build-matrix and deploy entries, and the Spring gateway route `/api/v1/content/**`.
- Remove the service modules `services/speaker-coordination-service` and
  `services/attendee-experience-service` from the Gradle build, Makefile, dev-native scripts and
  docker-compose files (the CDK and CI parts are the production change; check every reference with
  grep before deleting).
- Remove the EventBridge bus `batbern-staging` and, in shared-kernel, `EventBridgeEventPublisher`
  and `EventBridgeConfig` (+ their test config). Checked 2026-11-07: the bus is reached only through
  that class, which implements `DomainEventPublisher`; it is wired in `AwsConfig` / `LocalAwsConfig`
  of CUMS, EMS and PCS, and about 12 classes call the interface (CUMS `UserService`,
  `CompanyService`, `JITUserProvisioningInterceptor`; PCS `PartnerService`; EMS
  `EventWorkflowStateMachine`, `SpeakerWorkflowService`, `EventController` and others). Smallest
  change: keep `DomainEventPublisher` and replace its implementation with one that only calls
  `ApplicationEventPublisher`, so callers stay untouched and the 2 existing `@EventListener`s still
  fire. Whether the interface itself should go is a 17.12 question.
- Update `CLAUDE.md`, `docs/architecture/source-tree.md` and the service tables that list the stubs.

**Architecture note (draft):** modules: infrastructure (CDK stacks, CI), shared-kernel events, AWS
config of CUMS, EMS, PCS, Spring gateway routing · new dependencies: none · endpoints: gateway route
`/api/v1/content/**` removed · reused: `DomainEventPublisher`, Spring `ApplicationEventPublisher` ·
deviations: none.

**Acceptance criteria:**
1. After deploy, `aws ecs list-services --cluster batbern-staging` lists neither service and
   CloudFormation has no `BATbern-staging-SpeakerCoordination*` / `*AttendeeExperience*` stack
   (checked live, output in the story).
2. `aws events list-event-buses` no longer lists `batbern-staging`; no code references
   `EventBridgeClient` or `PutEvents`. Guard: CDK snapshot/assertion test that no `AWS::Events::EventBus`
   is declared; grep in CI or an ArchUnit rule "no class depends on `software.amazon.awssdk.services.eventbridge`".
3. The 2 `@EventListener`s still receive their events. Guard: an integration test per listener that
   triggers the publishing action and asserts the listener's effect.
4. `make dev-native-up` starts the remaining services; the docs list three services.
5. Not deployed on an event day. The PR description names the date and the owner's go. Deploy-clean:
   no CFN resource collision (see memory on CDK resource moves).

---

### Story 17.3 — Frontend path contract check

**As** a developer, **I want** CI to fail when the frontend calls a path no spec defines, **so that**
drift like `getPublishPreview` cannot ship again, and breaking API stories (17.6–17.11) prove the
frontend followed.

**Scope:** a script (reuse the regex scan from the review) that extracts every request path from
`web-frontend/src/services/**`, normalizes parameters, and matches it against the paths in
`docs/api/*.openapi.yml`. Runs in CI on every PR (frontend job) and locally via `make`.
Explicit allow-list file for paths outside the specs (e.g. Cognito, S3 presigned URLs), each with a
reason.
Refined at story creation: the spec matcher is a reusable TypeScript module
(`web-frontend/src/test/contract/specMatcher.ts`, method-aware, path parameters as wildcards, query
parameters checked) with its own unit tests, imported by the CI script and by 17.0c's MSW validator
(Winston 2026-11-07). The check scans all of `web-frontend/src` (15 files outside `services/` make HTTP
calls; frozen as a baseline), method-aware, and `frontend-changes` also triggers on `docs/api/`. Six
calls with no spec and no backend were found by a prototype and are resolved in the story.
Story file (BMAD v7): `_bmad-output/initiative-architecture-hardening/tickets/epic-dead-surface-and-gates/tickets/story-frontend-path-contract-check-in-ci.md`.

**Architecture note (draft):** modules: CI, `scripts/` · new dependencies: none · endpoints: none ·
reused: review scan regex, `docs/api/` · deviations: none.

**Acceptance criteria:**
1. Run against develop before 17.1 the check reports `GET /events/{c}/publish/{phase}/preview`
   (fixture test), and after 17.1 it passes with an allow-list of documented exceptions only.
2. A test case with a fabricated unknown path fails the check; a parameterized path matching a spec
   path passes.
3. The check is a step in a required CI job, so a PR cannot merge with a violation.

---

### Story 17.4 — OpenAPI lint and spec-diff report

**As** a reviewer (human or agent), **I want** the API rules checked by a linter and every new
operation shown next to its nearest existing one, **so that** conventions hold without anyone
remembering them and duplicates are visible before merge.

**Scope:**
- Spectral with a repo ruleset, run in CI on `docs/api/**`. Rules: kebab-case path segments; UUID path
  parameters only below a meaningful parent (ADR-003 amendment); no `/internal` path in a public spec
  (ADR-016); no verb endpoints (`/complete`, `/reassign`, `/send` etc., list in the ruleset); no new
  literal segment directly under `/events/`.
- Existing violations are recorded in a baseline file (path + rule), so the lint fails only on new
  ones. Stories 17.6, 17.9, 17.10 shrink the baseline.
- PR comment: for every added operation, the 3 most similar existing operations (path and summary
  similarity). Informative, not blocking.

**Architecture note (draft):** modules: CI, `docs/api/` · new dependencies: Spectral (npm, dev only) ·
endpoints: none · reused: `docs/api/` · deviations: none.

**Acceptance criteria:**
1. Lint runs in a required CI job. A fixture spec breaking each rule fails it; the baseline makes the
   current develop pass.
2. The baseline lists every current violation with its rule; its size is recorded (input for 17.6,
   17.9, 17.10). The known cases are in it: `/events/batch_registrations`,
   `/speakers/{speakerId}/ai/analyze-abstract`, the task verb endpoints,
   `/users/company-logo-requests/{id}/approve|decline`, `/users/merge-requests/{id}/approve|decline`.
3. A test PR adding one operation gets the similarity comment.

---

### Story 17.5 — ArchUnit freeze

**As** the owner, **I want** the EMS layering and the service directions checked by the build,
**so that** the 100 known violations can only shrink.

**Scope:**
- Add ArchUnit (test scope) to all three services.
- EMS: `FreezingArchRule` for the 5 vertical packages that already exist as packages (the review names
  them; 17.12 extends this to the full layering). Frozen store committed.
- All services: "no new client toward a downstream service" (ADR-016): CUMS has no client package
  toward EMS or PCS except the frozen merge and mail-center clients; EMS none toward PCS except frozen
  ones.

**Architecture note (draft):** modules: test sources of CUMS, EMS, PCS · new dependencies: ArchUnit
(test) · endpoints: none · reused: `scripts/architecture/*` numbers as cross-check · deviations: none.

**Acceptance criteria:**
1. The rules run in each service's `test` task. Adding a dependency that violates a rule fails the build
   (fixture test or documented manual check in the story).
2. Removing a frozen violation shrinks the store; the store is committed and reviewed like code.
3. The frozen count matches the script measurement for the same packages (difference explained in the
   story).

---

### Story 16.8c (Epic 16) — sits here in the order

No change to its scope (fold newsletter, registrant-notice, venue send/preview/retry/history into
`/events/{c}/mails`). Its slides-online part is done by 17.1. Still gated on the owner's go.

---

### Story 17.6 — Service-to-service calls via `/internal`

**As** the owner, **I want** machine calls out of the public API, **so that** the public specs only
contain what users call and a machine endpoint never needs a user role.

**Scope:**
- Move the 12 public S2S URLs to `/internal`, among them `POST /users/provision`,
  `POST /users/{u}/issue-invitation-credentials`, `PATCH /users/{u}/profile` (EMS→CUMS) and
  `GET /events/attendance-summary` (PCS→EMS). The story lists all 12 from a fresh grep of the client
  classes.
- Remove them from the public specs; describe them in internal specs (or the internal section the
  architecture uses).
- Token rule in one shared-kernel client helper: forward the user's JWT when present, otherwise call
  without one; the receiver records `system:<job>`. All clients use the helper.
- `/internal` endpoints authorize by operation; the role checks stay on the public endpoint where the
  user entered.

**Architecture note (draft):** modules: shared-kernel HTTP client helper; EMS, PCS clients; CUMS, EMS
internal controllers · new dependencies: none (moves existing ones) · endpoints: 12 moved to
`/internal`, nearest existing: current `/internal/**` controllers · reused: `UserApiClient` pattern ·
deviations: none.

**Acceptance criteria:**
1. No client class in any service calls an `/api/v1` URL of another service. Guard: ArchUnit or a
   source-scan test over client packages.
2. No `/internal` path in a public spec and the moved operations are gone from them. Guard: 17.4 lint,
   baseline shrinks accordingly.
3. Each moved call works with and without a user JWT. Guard: integration test per client, both cases;
   the receiver records the actor or `system:<job>`.
4. The Spring gateway still denies `/internal/**` from outside. Guard: `ProductionSecurityFilterChainTest`
   and the access-control DAST after deploy.

---

### Story 17.7 — User-reference contract and request pull

**As** the owner, **I want** CUMS to own the profile-merge contract and stop pushing into EMS,
**so that** CUMS no longer depends on EMS by name and every service that stores usernames takes part
in a merge.

**Scope:**
- CUMS defines `/internal/user-references/preview` and `/merge`. Holders are listed in CUMS
  configuration. EMS implements it (replaces the current internal merge endpoints), PCS implements it
  for `topic_suggestions.suggested_by`, notes `author_username`, meetings `created_by` and
  `partner_contacts.username`.
- The Mail Center pulls open and closed requests from CUMS with a ShedLock job, for **both** request
  kinds that CUMS pushes today: merge requests and company logo requests (#1246 added the second;
  `HttpEventManagementMailCenterClient`). Drop `/internal/mail-center/merge-requests*` and
  `/internal/mail-center/logo-requests*`.
- Before PCS joins: query production read-only for PCS rows referencing a username that was a merge
  source. The review believes there are none; record the result.

**Architecture note (draft):** modules: CUMS usermerge and requests, EMS usermerge and mail center
(L6), PCS (new usermerge holder) · new dependencies: EMS→CUMS feed (upstream, allowed); CUMS→holders
via its own contract (the one allowed upstream→downstream call, ADR-016 §5) · endpoints:
`/internal/user-references/*` new, nearest: `/internal/users/merge*`; CUMS request feed new, nearest:
none; 4 `/internal/mail-center/*` removed · reused: ShedLock, `RequestConversations` · deviations: none.

**Acceptance criteria:**
1. A merge preview shows counts from EMS and PCS; a merge rewrites PCS usernames. Guard: integration
   test per holder plus a CUMS test with two configured holders, one refusing.
2. CUMS has no class referencing EMS by name. Guard: 17.5 ArchUnit rule, frozen store shrinks.
3. A new merge request and a new logo request appear in the Mail Center within one poll interval,
   and close when decided. Guard: EMS integration test against a stubbed feed; local-dev check.
4. The 4 `/internal/mail-center/*-requests*` endpoints and their CUMS client are gone.

---

### Story 17.8 — EMS↔PCS direction

**As** the owner, **I want** EMS to stop calling PCS, **so that** EMS is upstream of PCS as ADR-016
says.

**Scope:**
- Topic canvas: the frontend loads partner topics from `/partners/topics` itself. EMS keeps the
  cluster classification and offers a batch classify endpoint. `TopicSessionDataService` drops its
  PCS call.
- Calendar RSVPs from partner meeting invites: `InboundEmailRouter` stores them in EMS and exposes an
  `/internal` RSVP feed (`?since=cursor`). PCS pulls it with a ShedLock job (add ShedLock to PCS).
- PCS→EMS (event summary, attendance-summary) stays, via `/internal` after 17.6.

**Architecture note (draft):** modules: EMS topics (L3), mail center inbound (L6); PCS meetings;
frontend topic canvas · new dependencies: PCS→EMS feed (upstream, allowed); frontend→PCS read ·
endpoints: EMS `/internal` RSVP feed new, nearest: none; batch classify, nearest: current classify
call · reused: ShedLock, `InboundEmailRouter` · deviations: none.

**Acceptance criteria:**
1. EMS has no PCS client. Guard: 17.5 ArchUnit rule, frozen store shrinks to zero for EMS→PCS.
2. The topic canvas shows partner topics with clusters as before. Guard: Vitest for the hook,
   Playwright on the canvas.
3. A calendar accept/decline reaches the PCS meeting within one poll interval. Guard: PCS integration
   test against a stubbed feed, EMS feed integration test, local-dev check with a sample iMIP reply.

---

### Story 17.9 — API conventions I (breaking)

**As** a developer, **I want** state changes and settings to have one way in, **so that** there is
one endpoint to extend.

**Scope:**
- Tasks: one `PATCH /tasks/{id}` replaces `PUT …/complete`, `…/reassign`, `…/status`.
- Settings: one app-settings resource (`AdminSettingsService`); one user settings resource merging
  `users/me/settings` and `/preferences`.
- Nest `POST /speakers/{speakerId}/ai/analyze-abstract` under the event (ADR-003 amendment).
- Frontend, Bruno, Playwright changed in the same PR.

**Architecture note (draft):** modules: EMS tasks (L3), ai (L6); CUMS user settings; admin settings ·
new dependencies: none · endpoints: 3 task verbs → 1 PATCH; 2 user settings resources → 1; ai path
moved · reused: `AdminSettingsService` · deviations: none.

**Acceptance criteria:**
1. The old operations are gone; the 17.4 baseline shrinks by their entries. Guard: Spectral.
2. The frontend calls only the new paths. Guard: 17.3 contract check.
3. Task, settings and AI flows work end to end. Guard: integration tests per new operation, Bruno,
   Playwright for task completion and settings.
4. The nested AI operation loads the speaker scoped to the event (`findByIdAnd{Parent}`). Guard:
   scoping test (a speaker id from another event returns 404).

---

### Story 17.10 — API conventions II (breaking)

**Scope:**
- `me` as alias of `{username}` for pictures and profile: one endpoint set with an ownership rule,
  the twins removed.
- One speaker path key (today `{speakerId}`, `{speakerPoolId}` under `/speaker-pool/`, `{username}`).
  The story picks one per the ADR-003 amendment and records why.
- Fold `/events/batch_registrations` and `enroll-stakeholders` into `participants`.
- Fold `/newsletter/my-subscription` (`GET|PATCH`) into `/newsletter/my-subscriptions` (`GET|PUT`,
  per address); the frontend calls both today.

**Architecture note (draft):** modules: CUMS users; EMS speakers (L4), registrations (L3), newsletter
(L6) · new dependencies: none · endpoints: twins removed, listed per case with the survivor · reused:
existing `participants` resource · deviations: none.

**Acceptance criteria:**
1. No `/users/me/*` and `/users/{username}/*` twin remains for pictures and profile; ownership is
   enforced by one `@PreAuthorize` rule. Guard: integration test (own = allowed, other = 403,
   organizer = allowed where it was).
2. One speaker path key across all specs. Guard: Spectral rule added for the parameter name.
3. `batch_registrations`, `enroll-stakeholders` and `my-subscription` are gone. Guard: Spectral
   baseline shrinks, 17.3 contract check passes.
4. Registration, enrolment and newsletter self-service work end to end. Guard: Bruno, Playwright.

---

### Story 17.11 — One upload contract, first purpose `company-logo`

**As** a developer, **I want** one upload mechanism, **so that** a new file type is a small handler,
not new presign code and endpoints.

**Scope (ADR-002 decision 2026-11-07):**
- Shared-kernel upload component: key layout, presign, size and type rules, PENDING → CONFIRMED →
  ASSOCIATED, expiry cleanup. Each service gets its own `uploads` table.
- `POST /api/v1/uploads/{purpose}`, `/{uploadId}/confirm` (associates when a `target` is given),
  `/import` (through `ImageUrlFetcher`). The Spring gateway routes by purpose prefix.
- First purpose `company-logo` in CUMS, including the company logo request flow (#1246). Remove the
  EMS use of the shared `logos` table and rename the EMS `GenericLogoService`.
- Frontend: one upload hook; company logo uses it. Old logo endpoints deleted.

**Architecture note (draft):** modules: shared-kernel (new upload component), CUMS companies, EMS
(remove `logos` use), Spring gateway routing, frontend upload hook · new dependencies: services →
shared-kernel component · endpoints: 3 `/uploads/{purpose}` operations new, nearest: current
`/logos/*` presign/confirm (removed) · reused: `ImageUrlFetcher`, presign code of the current logo
flow · deviations: none.

**Acceptance criteria:**
1. Company logo upload (create form and existing company) and logo request work through
   `/uploads/company-logo`. Guard: CUMS integration tests per path, Playwright on company create.
2. The old `/logos/*` upload operations are gone. Guard: Spectral baseline, 17.3 contract check.
3. EMS no longer reads or writes `logos`. Guard: test or ArchUnit rule that no EMS repository maps
   `logos`; migration that drops nothing in EMS without the owner's go (the table belongs to CUMS).
4. The shared-kernel component has unit tests for the state machine and expiry cleanup.
5. The story file lists the five follow-on purposes as backlog stories.

---

### Story 17.12 — EMS modularization design (Winston)

**Design task, not a dev story.** Output: a short tech design `docs/architecture/ems-modularization.md`
building on `docs/plans/ems-modularization-extension-points.md`.

**Contents:** target package per class for the modules in `module-map.md`; the order of package moves
(each move is a pure refactor PR); how the 17.5 freeze extends to `layeredArchitecture()` over the
full layering; release slicing for 17.13–17.16; what the move costs in migration and merge conflicts
with open work.

**Acceptance criteria:** the owner accepts the design; 17.13–17.16 are refined against it before
they start; the freeze baseline for the full layering is defined (count and how to measure).

---

### Story 17.13 — Registrations out of `EventController`

**Scope:** a `RegistrationController` implementing the generated registrations interface; the 14
hand-mapped registration endpoints move there with their tests. No path changes.

**Acceptance criteria:**
1. `EventController` has no registration endpoints and implements no registrations interface.
   Guard: ArchUnit rule; script re-run shows the `EventController` violations reduced (was 27).
2. All registration paths behave as before. Guard: existing integration tests, Bruno, Playwright
   registration wizard; contract check unchanged.

---

### Story 17.14 — Domain exception base

**Scope:** a base exception class in platform (L0) carrying HTTP status and error code; domain
exceptions extend it; `GlobalExceptionHandler` maps the base class and imports no domain exception.

**Acceptance criteria:**
1. `GlobalExceptionHandler` imports no class from a domain module. Guard: ArchUnit rule; script
   re-run shows its 16 violations gone.
2. Every existing exception keeps its status and error code. Guard: `ClientErrorContract.verify`
   plus a parameterized test over all domain exceptions.

---

### Story 17.15 — Mail core / mail center split

**Scope:** delivery status becomes a projection owned by the mail center; mail core (`MailPipeline`,
delivery) publishes in-process events, the mail center listens. `MailDeliveryStatusService`,
`MailPipeline`, `ModeratorReplyAddress` stop depending on mail-center classes.

**Acceptance criteria:**
1. Mail core has no dependency on mail center. Guard: ArchUnit, script re-run (11 violations).
2. Delivery status, "What went out" and moderator Reply-To behave as before. Guard: existing 16.1b
   and 16.0 tests, local-dev send check (`/dev/mails`).

---

### Story 17.16 — Event-core extension points

**Scope:** workflow guards as an interface in event core, implemented by sessions and speakers;
`EventTimeResolver` and `EventWorkflowStateMachine` stop depending on higher modules;
`Event→Registration` JPA association one-directional.

**Acceptance criteria:**
1. Event core depends on no L3+ module. Guard: ArchUnit, script re-run (5 violations plus the
   association).
2. All workflow transitions behave as before. Guard: existing state machine tests plus one test per
   guard implementation.
