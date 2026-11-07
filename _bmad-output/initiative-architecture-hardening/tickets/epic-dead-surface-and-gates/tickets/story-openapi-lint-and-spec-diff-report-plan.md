---
title: 'OpenAPI lint and spec-diff report (17.4)'
type: 'feature'
ticket: '4'
created: '2026-11-08'
status: 'built'
baseline_revision: 'c2410c8a6aeaee61f8b2d07aac672edd67d8977b'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context:
  - '{project-root}/docs/architecture/ADR-003-meaningful-identifiers-public-apis.md'
  - '{project-root}/docs/architecture/ADR-013-rest-api-crud-conventions.md'
  - '{project-root}/docs/architecture/ADR-016-service-directions-and-service-to-service-calls.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The API conventions (ADR-003 amendment, ADR-013, ADR-016) live only in documents, so new operations break them unnoticed and near-duplicates of existing operations are only found by chance.

**Approach:** Spectral with a repo ruleset over `docs/api/*.openapi.yml`, wrapped by a script that compares findings with a committed baseline (operation + rule, not line numbers) so only new violations fail, in the required frontend CI check. A non-blocking PR comment lists, for every added operation, the 3 most similar existing operations.

## Boundaries & Constraints

**Always:** baseline semantics as the 17.0a ratchet: a new violation fails (NEW), a fixed one fails until the baseline is updated (STALE), `--update` rewrites it, never used to accept a new violation without a stated reason in the PR; findings are keyed by spec file + HTTP method + path + rule so spec edits elsewhere do not move them; the similarity comment never fails a build.

**Never:** no `spectral:oas` recommended ruleset (only the convention rules below); no spec or code change to fix existing violations (17.6, 17.9, 17.10 shrink the baseline); no PR-comment end-to-end proof (owner dropped 17.4 AC3 on 2026-11-07).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| develop | current specs + baseline | exit 0, summary per rule | none |
| kebab | fixture path `/events/{eventCode}/speakerSlots` | NEW `path-kebab-case` | exit 1 |
| verb | `/tasks/{id}/complete` (list in ruleset) | NEW `no-verb-segment` | exit 1 |
| internal | `/internal/foo` in a public spec | NEW `no-internal-path` | exit 1 |
| events literal | `/events/batch-things` (literal directly under `/events/`) | NEW `no-literal-under-events` | exit 1 |
| uuid | path param with `format: uuid` | NEW `uuid-path-param` (review against the ADR-003 amendment) | exit 1 |
| fixed | a baselined violation removed from the spec | STALE, says to run `--update` | exit 1 |
| similarity | PR adds `GET /events/{eventCode}/foo-bars` | comment lists 3 nearest existing operations | never fails |

</frozen-after-approval>

## Code Map

- `docs/api/*.openapi.yml` -- 20 public specs (`_shared.openapi.yml` holds components only, skip it). A future `docs/api/internal/` (0b2) is out of scope for `no-internal-path` and the similarity report.
- `web-frontend/scripts/check-api-paths.ts`, `test-ratchet.ts` -- patterns to copy: Node type-stripped script, `__fixtures__/`, baseline beside the script, `package.json` script with Node guard, `--update`, NEW/STALE wording.
- `web-frontend/src/test/contract/specMatcher.ts` -- `loadSpecDocuments` (reuse for the similarity report's parsing).
- `.github/workflows/build.yml` `frontend-static` (l.~535) -- runs on `web-frontend/**` and `docs/api/**` changes and is required through `build-frontend`: add the lint test and lint steps there.
- Known current violations that must land in the baseline (PRD AC2): `/events/batch_registrations`, `/speakers/{speakerId}/ai/analyze-abstract`, the task verb endpoints, `/users/company-logo-requests/{id}/approve|decline`, `/users/merge-requests/{id}/approve|decline`.

## Tasks & Acceptance

**Execution:**
- [x] `web-frontend/package.json` -- devDependency `@stoplight/spectral-cli@6.17.0` (exact), script `lint:api` (Node guard) and `report:api-similarity`.
- [x] `docs/api/.spectral.yaml` + `docs/api/spectral-functions/*.js` if needed -- the five rules of the matrix; verb list: `complete, reassign, send, approve, decline, publish, unpublish, cancel, retry, resend, analyze-abstract, preview` plus any verb segment found in the specs, documented in the ruleset.
- [x] `web-frontend/scripts/api-lint.ts` + `api-lint-baseline.txt` + tests and fixture specs in `scripts/__fixtures__/api-lint/` -- every matrix row; baseline generated with `--update`.
- [x] `web-frontend/scripts/api-similarity.ts` + test -- added operations = head minus base (method + normalized path); score = path-segment and summary-word similarity; Markdown output.
- [x] `.github/workflows/build.yml` -- lint tests and lint in `frontend-static`; new job `api-similarity` on `pull_request` when `docs/api/**` changed (`permissions: pull-requests: write`, `continue-on-error: true`), posting or updating one marked comment.
- [x] `Makefile` `lint-api`; `docs/architecture/coding-standards.md` (API section: the rules and the baseline policy), `docs/deployment/cicd-pipeline-guide.md`.

**Acceptance Criteria:**
- Given develop's specs, when `npm run lint:api` runs, then it passes, and the baseline size per rule is recorded in Implementation Notes, each PRD AC2 case present.
- Given the PR, when CI runs, then `frontend-static` runs the lint and fails on a NEW finding.

## Design Notes

Architecture note: modules: CI, `docs/api/`, web-frontend scripts · new dependencies: `@stoplight/spectral-cli` (dev only) · endpoints: none · reused: the 17.3/17.0a script pattern, `specMatcher.loadSpecDocuments` · deviations: none.

`uuid-path-param` cannot decide "has no natural name" (ADR-003 amendment), so it flags every UUID path parameter; the 58 existing ones are baselined and a new one needs its ADR-003 case stated in the PR when the baseline grows. Path params count as kebab-exempt; only literal segments are checked.

## Verification

Rack rules for every run, including any implementation subagent: one heavy job at a time; `./gradlew --stop` before Node work; Vitest `--maxWorkers=4`; logs to `~/.cache/batbern-test-logs/` (never `/tmp`); before each run check `grep anon /sys/fs/cgroup/dev.slice/memory.stat` and stop above 12 GB.

**Commands:**
- `cd web-frontend && npx vitest run --maxWorkers=4 scripts/api-lint.test.ts scripts/api-similarity.test.ts 2>&1 | tee ~/.cache/batbern-test-logs/174-tests.log` -- expected: all pass.
- `cd web-frontend && npm run lint:api` -- expected: exit 0; then rename one path segment to camelCase in a spec copy, expect NEW, revert.
- `cd web-frontend && npm run report:api-similarity -- --base origin/develop` -- expected: prints "no added operations" on this branch.
- `cd web-frontend && npm run type-check && npm run lint && npm run format:check && npm run check:test-ratchet` -- expected: clean.

## Implementation Notes

Plan approved by the coordinator under the owner's standing go for autonomous overnight work (2026-11-07); no open questions.

Implementation 2026-11-07 (measured on this branch, `npm run lint:api -- --update`):

- Baseline `web-frontend/scripts/api-lint-baseline.txt`: **150 findings** over 320 operations in 20
  public specs. Per rule: `no-verb-segment` 82, `uuid-path-param` 58, `no-literal-under-events` 8,
  `path-kebab-case` 2, `no-internal-path` 0. (First cut was 143: 78 verb, 55 uuid. Review fixes
  added write-operation verbs before a parameter or qualifier, +4: `publish/{phase}`,
  `unpublish/{phase}`, `publish/schedule`, `deregister/by-email`; and unformatted string `*Id`
  params, +3: the `uploadId` operations.)
- PRD AC2 cases all present (asserted in `api-lint.test.ts`): `POST /events/batch_registrations`
  (kebab + events literal), `POST /speakers/{speakerId}/ai/analyze-abstract`, `PUT /tasks/{taskId}/complete`
  and `/reassign`, `POST /users/company-logo-requests/{requestId}/approve|decline`,
  `POST /users/merge-requests/{requestId}/approve|decline`.
- `no-verb-segment`: every literal segment of a write operation, only the last segment of a read
  (`GET /publish/status`, `GET /logos/cleanup/statistics` stay legal). `uuid-path-param`:
  `format: uuid` or a string `id`/`*Id` without a format; operation parameters replace path-level
  ones by name + in. Limit: a UUID under another name without a format is not seen.
  Verb list = the plan's 12 plus 42 found in the specs (54); `search`, `get-or-create`, `vote` left out
  because ADR-013 sanctions them. Kebab allows an `.ext` suffix (`export.xlsx`).
- `report:api-similarity -- --base origin/develop` on this branch lists `GET /config` (added by
  1c3bbde1, not yet on develop), not "no added operations"; against `HEAD` it prints
  "API similarity: no added operations."


## Plan Change Log

## Review Triage Log

Pass 1 (quick lens), 2026-11-08: high 0, medium 2, low 2, false 1. Patched 4, rejected 1.

| # | Finding | Verdict | Route | Evidence / action |
|---|---|---|---|---|
| 1 | Verb segment not in last position escapes `no-verb-segment` | medium | patch | Write ops check every segment, reads only the last; 4 operations added to the baseline. |
| 2 | UUID param without `format: uuid` (`uploadId`) not flagged | medium | patch | `id`/`*Id` string params without format flagged; 3 added; remaining limit documented. |
| 3 | Path- and operation-level params concatenated, no override | low | patch | Merged by name+in; test. |
| 4 | Similarity score could reach 1.05 | low | patch | Weights 0.55/0.4/0.05; self-score test = 1. |
| 5 | `--base origin/develop` lists `GET /config` | false | rejected | 1c3bbde1 is on the first PR's branch, which this branch stacks on; after that PR merges and this branch rebases, `GET /config` is on develop. |

Source conflict for the owner: ADR-013 allows action endpoints (`POST /{id}/{verb}`), the PRD's 17.4 rule lists verb endpoints as violations. The lint follows the PRD; ADR-013 or the rule should be aligned.
