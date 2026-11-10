---
title: 'Test rules and ratchet (17.0a)'
type: 'feature'
ticket: '0a'
created: '2026-11-07'
status: done
baseline_revision: '18b2051a6b1e0292cb36acd2d04824347b3fc97f'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context:
  - '{project-root}/docs/plans/test-fidelity-proposal-2026-11.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** No written rule says what a test may replace (`coding-standards.md` is silent, `project-context.md:201` says use MSW and `:459` says mock the service layer), so 329 of 441 frontend test files mock own code and EMS ITs mock `UserApiClient` 37 times; bugs pass the tests because the behaviour under test is mocked.

**Approach:** Write the mocking rule (P1), assertion rules (P3, P2.4d/e) and touch rule (P4) once in `coding-standards.md`, point project-context and the `bmad-build` override at it, and add a CI ratchet: a committed per-file baseline of own-code mock counts that may only fall, enforced in a job the required `build-frontend` check depends on.

## Boundaries & Constraints

**Always:** The ratchet is deterministic, has no npm dependencies (runs with plain `node` ≥ 22.18), and fails on any difference from the baseline: a rise and a stale entry (count fell or file gone without a baseline update) each with their own message naming file, category, baseline and actual. `--update` rewrites the baseline. The ratchet's own tests run in CI before it.

**Never:** No test file is converted or rewritten in this story (0c2 and the touch rule do that). No change to the bmad skill files themselves, only `_bmad/custom/*.toml`. No Gradle task for the ratchet.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Equal | counts equal baseline | exit 0, one summary line with totals per category | none |
| Rise | a file gains a `vi.mock('@/services/x')`, or a new file has a counted mock | exit 1, "raised" line per file/category | names the rule section in coding-standards.md |
| Fall without update | a count drops, baseline unchanged | exit 1, "stale" line, says to run `--update` | none |
| Fall with update | count drops, `--update` run and committed | exit 0 | none |
| File deleted | baseline names a missing file | exit 1, stale | none |
| Comment | `@MockitoBean` or `vi.mock(` inside a comment or Javadoc | not counted | none |
| Boundary mock | `@MockitoBean SesClient`, `vi.mock('aws-amplify')` | not counted | none |

</frozen-after-approval>

## Code Map

- `docs/architecture/coding-standards.md` -- "Testing Standards" (l.308ff) gets a "Test fidelity rules" subsection: P1 rule + layer table, P3 rules, P2.4(d)(e), P4 touch rule, ratchet usage. Source text: proposal P1, P3, P4.
- `_bmad-output/project-context.md:459-460` -- delete the "mock the service layer" gotcha; `:201` becomes a one-line pointer to the new section.
- `_bmad/custom/bmad-build.toml` -- append to `activation_steps_append`: tests follow the Test fidelity rules; a change to a screen or service method moves its tests off own-code mocks once the harness exists. `bmad-agent-dev.toml` already loads coding-standards.md: no change.
- `web-frontend/scripts/check-api-paths.ts`, `check-api-paths.test.ts`, `__fixtures__/` -- pattern to copy: Node type-stripped script, Vitest tests on fixtures, baseline file beside it, `package.json` script with Node-version guard.
- `.github/workflows/build.yml` -- `frontend-static` (l.535) runs the 17.3 test step; `build-frontend` (l.612) is required and fails when any `needs` failed (l.618). `frontend-changes` filter does not fire on Java-only PRs, so the ratchet needs its own unfiltered job.
- Frontend aliases seen in `vi.mock` specifiers: `@/services`, `@services`, `@/hooks`, `@hooks`, `@/components`, `@components`, relative paths (108).
- `services/*/src/test/java`, `api-gateway/src/test/java`, `shared-kernel/src/test*` -- `@MockitoBean` fields, some with args (`@MockitoBean(answers = …)`), many mentions in Javadoc.

## Tasks & Acceptance

**Execution:**
- [x] `web-frontend/scripts/test-ratchet.ts` -- scan and compare as in Design Notes; `--update` writes the baseline; exits 1 on any difference -- the gate.
- [x] `web-frontend/scripts/test-ratchet.test.ts` + `__fixtures__/test-ratchet/` -- every matrix row, alias forms, relative-path classification, comment stripping, annotation args -- proves the gate.
- [x] `web-frontend/scripts/test-ratchet-baseline.txt` -- generated with `--update` on the baseline revision.
- [x] `web-frontend/package.json` -- `check:test-ratchet` script with the Node guard; lint/type-check already cover `scripts/`.
- [x] `.github/workflows/build.yml` -- new job `test-ratchet` (no path filter, Node 22, sparse checkout acceptable): runs the ratchet tests, then the check; add it to `build-frontend.needs`.
- [x] `Makefile` -- `check-test-ratchet` target beside `check-frontend-api-paths`.
- [x] `docs/architecture/coding-standards.md`, `_bmad-output/project-context.md`, `_bmad/custom/bmad-build.toml` -- rules as in Code Map.
- [x] `docs/deployment/cicd-pipeline-guide.md` -- the new job and how to update the baseline.

**Acceptance Criteria:**
- Given develop's tests, when `npm run check:test-ratchet` runs, then it passes and prints totals per category, recorded in Implementation Notes beside the proposal's 177/112/87 files and 38 `UserApiClient`.
- Given the repo, when grepping `CLAUDE.md`, `docs/`, `_bmad/custom/` and `project-context.md` (archived stories excepted) for "mock the service layer", then nothing is found.
- Given a PR touching only Java tests, when CI runs, then the `test-ratchet` job runs and its failure fails `build-frontend`.

## Design Notes

Architecture note: modules touched: web-frontend scripts, CI, docs, BMAD overrides · new dependencies: none · new endpoints: none · components reused: the 17.3 script pattern · deviation: a Java-scanning check lives in `web-frontend/scripts/` to reuse its Node/Vitest tooling; the job is wired into `build-frontend` because that is the required check that always runs.

Categories (baseline line: `<category>\t<path>\t<count>`, sorted, zero counts omitted):
- `fe-mock-services`, `fe-mock-hooks`, `fe-mock-components`: `vi.mock(` calls in `src/**/*.test.ts(x)`. Relative specifiers resolve against the test file: under `src/services` → services; under `src/hooks` or basename `use[A-Z]…` → hooks; under `src/components`/`src/pages` or PascalCase basename → components; else uncounted.
- `be-mock-s2s-client`: `@MockitoBean` whose type ends in `Client` and is not an AWS SDK client (UserApiClient, UserServiceClient, CompanyServiceClient, …).
- `be-mock-in-process`: any other `@MockitoBean` except the boundary allowlist `SesClient, SesV2Client, S3Client, S3Presigner, CognitoIdentityProviderClient, SqsClient, EventBridgeClient, Clock, JwtDecoder`.
- `be-transactional-it`: class-level `@Transactional` on a test class extending `AbstractIntegrationTest` or named `*IntegrationTest`/`*IT`. Measured 115, of which 110 write; the five read-only ones are counted too, because a deterministic "write" test is not possible and the fix (drop class-level `@Transactional`) is the same.

## Verification

Rack rules for every run, including any implementation subagent: one heavy job at a time; `./gradlew --stop` before Node work; Vitest `--maxWorkers=4`; logs to `~/.cache/batbern-test-logs/` (never `/tmp`); before each run check `grep anon /sys/fs/cgroup/dev.slice/memory.stat` and stop above 12 GB.

**Commands:**
- `cd web-frontend && npx vitest run --maxWorkers=4 scripts/test-ratchet.test.ts 2>&1 | tee ~/.cache/batbern-test-logs/0a-ratchet-tests.log` -- expected: all pass.
- `cd web-frontend && npm run check:test-ratchet` -- expected: exit 0 on the branch; then add one `vi.mock('@/services/x')` to a test file, expect exit 1 "raised", revert.
- `cd web-frontend && npm run type-check && npm run lint && npm run format:check` -- expected: clean.
- `actionlint .github/workflows/build.yml` if installed, else YAML parse -- expected: clean.

## Implementation Notes

2026-11-07, on `feature/17-0a-17-2-rules-and-stubs` (uncommitted at hand-over).

**Baseline totals** (`npm run check:test-ratchet`, tree = baseline revision 18b2051a plus this story's changes, no test file touched):

| Category | Ratchet: files / occurrences | Proposal (grep of develop) |
|---|---|---|
| `fe-mock-services` | 193 / 215 | 177 files (`@/services` only) |
| `fe-mock-hooks` | 122 / 201 | 112 files (`@/hooks` only) |
| `fe-mock-components` | 76 / 175 | 87 files |
| `be-mock-s2s-client` | 52 / 63, of which `UserApiClient` 37 | 38 `UserApiClient` (EMS) |
| `be-mock-in-process` | 41 / 59 | (106 EMS `@MockitoBean` in total) |
| `be-transactional-it` | 131 / 131 (EMS 86, CUMS 34, PCS 11) | 85 EMS; plan said 115 overall |

Why they differ: the ratchet resolves relative specifiers and every alias (`@services/`, `@hooks/`, ...), so service and hook counts are higher than an `@/...` grep (an alias-only grep today gives 174 / 115 / 54 files). Components are counted by resolved path or PascalCase module name; a `@/components/.../useX` mock counts as a hook, which moves some of the proposal's 87 out. `@MockitoBean` totals: 127 declarations, 5 boundary (3 `S3Client`, 2 `S3Presigner`), 122 counted. `UserApiClient` 37, the same as the epic doc; the proposal's 38 is not reproduced (cause not checked). Class-level `@Transactional`: an independent `grep -l '^@Transactional'` over all `src/test*` gives the same 131 files, all extending `AbstractIntegrationTest`; the plan's 115 is not reproduced.

**Decisions taken in implementation:**
- The basename rules (`use[A-Z]` -> hooks, PascalCase -> components) apply to every resolved own-code path, alias or relative, so `@/contexts/useConfig` and `../contexts/useConfig` count the same. Stores, utils, types and contexts that are neither are not counted.
- Boundary types: the plan's allowlist plus any type imported from `software.amazon.awssdk.*`, since every AWS SDK client leaves the process.
- `maskComments` moved from `check-api-paths.ts` to `scripts/lib/mask-comments.ts`, because `check-api-paths.ts` imports `yaml` through the spec matcher and the ratchet must run without npm packages. `check-api-paths.ts` re-exports it; its tests pass unchanged.
- Fixture tree `scripts/__fixtures__/test-ratchet/repo` holds real-named `*.test.tsx` and `.java` files; Vitest (`vite.config.ts` exclude) and Prettier (`.prettierignore`) skip it.
- Two more contradictions fixed beyond `:459`: `project-context.md` told agents to put `@Transactional` on every IT class, and the IT examples in `coding-standards.md` and `CLAUDE.md` did the same. All three now say the opposite and point to the rules. The quotes of the old `:459` wording in the proposal and the epic doc stay as evidence (coordinator decision), so the AC grep matches only those quotes. The integration-test examples in `06c-testing-strategy.md`, `templates/backend/integration-test-pattern.md`, `microservices-http-clients.md`, `service-foundation-pattern.md` and `flyway-migration-guide.md` were aligned too (no class-level `@Transactional`, peer service at the wire).
- Review fix: `vi.doMock(` counts like `vi.mock(`, and `*.spec.ts(x)` files are scanned (none exist today). The baseline moved by one: `DetailsTabPanel.test.tsx` hooks 2 -> 3, so `fe-mock-hooks` is 122 files / 202.
- Owner request during the build (not plan scope, recorded here): `_bmad/custom/bmad-build.toml` loads `coding-standards.md`, `tech-stack.md`, `source-tree.md` as persistent facts (the v6 `devLoadAlwaysFiles`); `bmad-agent-dev.toml` adds `source-tree.md`.
- Coordinator fix: the `AbstractIntegrationTest` Javadoc (shared-kernel testFixtures) and its copy in `06c-testing-strategy.md` no longer show class-level `@Transactional`.

## Plan Change Log

## Review Triage Log

Pass 1 (quick lens), 2026-11-07: high 0, medium 2, low 4, false 0, maybe-false 0; 1 rejected-as-plan-edit.

| # | Finding | Verdict | Route | Evidence / action |
|---|---|---|---|---|
| 1 | AC grep still finds "mock the service layer" in `docs/` | low | rejected (fix is a plan edit) | The 3 hits are quotes of the old line in the proposal and PRD, kept as evidence; no guidance carries it. Notes corrected. |
| 2 | Five docs/guides still teach class-level `@Transactional` and mocked S2S clients | medium | patch | Verified in 06c, integration-test-pattern, microservices-http-clients, service-foundation-pattern, flyway guide; aligned by the implementer, Javadoc source by coordinator. |
| 3 | Inherited `@Transactional` on a base class not counted | low | rejected | No such base class exists (no `abstract class … extends AbstractIntegrationTest`, base carries none); fix adds inheritance resolution. |
| 4a | `vi.doMock` and `*.spec.ts(x)` not scanned | low | patch | One `vi.doMock` of an own hook existed; both added with tests, baseline +1. |
| 4b | `TestUserApiClientConfig` `@Primary` mock and `@MockitoSpyBean` not counted | medium | defer | Verified (10 files reference the config); deferred to 0b1 in deferred-work.md. |
| 5 | Docs claim strings are never counted; frontend scan counts them | low | patch | `maskComments` keeps strings; texts corrected to what the code does. |
| 6 | Inline fully qualified AWS SDK type counted | low | rejected | No such field in the repo; would need fqn plumbing. |
| 7 | `git mv` of a test needs owner go under the docs | medium | patch | RAISED+STALE pair confirmed from `compare`; docs now allow `--update` for an exact rename/move. |
| 8 | toml persistent_facts changes outside the plan, unrecorded | low | patch | Owner request; recorded in Implementation Notes. |
