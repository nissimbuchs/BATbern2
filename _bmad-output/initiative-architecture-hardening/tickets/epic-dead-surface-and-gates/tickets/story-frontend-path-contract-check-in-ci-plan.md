---
title: 'Frontend path contract check in CI (17.3)'
type: 'feature'
ticket: '3'
created: '2026-11-07'
status: 'built'
baseline_revision: 'd4fb35d7c0acdc8af82538c0b2815e88c6083359'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/initiative-architecture-hardening/tickets/epic-dead-surface-and-gates/tickets/story-frontend-path-contract-check-in-ci.md'
  - '{project-root}/_bmad-output/initiative-architecture-hardening/tickets/epic-dead-surface-and-gates/tickets/notes-frontend-path-contract-check.md'
  - '{project-root}/docs/architecture/module-map.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Nothing checks that the frontend calls only paths the OpenAPI specs define. `getPublishPreview` called a path that never existed, and the breaking API stories 17.6 to 17.11 need a guard that proves the frontend followed.

**Approach:** A plain-TypeScript spec matcher, reusable by 17.0c's MSW validator, plus a Node scan of `web-frontend/src` that resolves every HTTP call and matches it. The check runs in the job behind the required `build-frontend` check and on spec-only PRs, with an allow-list for unresolvable calls and a frozen baseline of files that call HTTP outside the service layer. The six calls found by the prototype are resolved, not allow-listed away.

## Boundaries & Constraints

**Always:**
- The story file's 8 criteria are the acceptance criteria.
- Matcher and scan are TDD with fixtures. Only one smoke test reads the real `docs/api/`.
- `.ts` import extensions, and no TS-only runtime syntax, so that Node 22 runs the script with type stripping.
- Each finding is fixed with its own test.

**Never:**
- Add or change a backend endpoint.
- Move calls into the service layer (only freeze them).
- Resolve params objects statically (that is 17.0c).
- Allow-list a finding that has a live caller and no endpoint without a GitHub issue. More than about 15 allow-list entries means stop and ask the owner.
- Push or open a PR.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Spec match | `GET /events/BATbern1/registrations` | matched (file, operationId) | – |
| Wildcard literal | `GET /users/me` against `/users/{username}` | matched | – |
| Wrong method | `DELETE` on a path with only `GET` | NOT IN SPEC, exit 1 | reason names the method |
| Unknown path | `/events/${c}/publish/${phase}/preview` (getPublishPreview repro) | NOT IN SPEC, exit 1 | – |
| Unknown query | literal `?foo=1` not declared | mismatch, exit 1 | reason names the parameter |
| Exceeded maximum | `limit=1000`, spec maximum 100 (#1072) | mismatch, exit 1 | – |
| Unresolvable | call whose path the scan cannot resolve, not allow-listed | UNRESOLVED, exit 1 | – |
| Stale allow-list | entry that matches no call | STALE, exit 1 | – |
| Ignored | absolute URL or presigned `uploadUrl` | not reported | – |
| New out-of-layer file | HTTP call in a new file outside `services/` | fails, exit 1 | – |
| Stale layer baseline | baseline file no longer calls HTTP | STALE, exit 1 | – |
| Clean develop | the repo after fixes | exit 0 + summary line | – |

</frozen-after-approval>

## Code Map

Full inventory (file names, CI line numbers, the 15 out-of-layer files, the six findings, prototype numbers, design choice): the notes file in `context`. Re-checked on `d4fb35d7`:

- `.github/workflows/build.yml:486` -- `frontend-changes` greps only `^web-frontend/`; extend it to `^docs/api/`.
- `.github/workflows/build.yml:534-598` -- `frontend-static` (`npm ci`, API types, `lint:ci`, prettier, build). Add the two Vitest files and `npm run check:api-paths` after `npm ci`.
- `.github/workflows/build.yml:600` -- `build-frontend` needs `frontend-static` and is the required check; leave it unchanged.
- `web-frontend/package.json` -- add `yaml` as an explicit devDependency (2.9.0 today, transitive only) and the `check:api-paths` script.
- New: `web-frontend/src/test/contract/specMatcher.ts` (+ `specMatcher.test.ts`, `__fixtures__/`), `web-frontend/scripts/check-api-paths.ts` (+ `check-api-paths.test.ts`), `web-frontend/scripts/api-paths-allowlist.txt`, `web-frontend/scripts/api-layer-baseline.txt`.
- Root `Makefile` -- add a target `check-frontend-api-paths` near `check-generated-types` (line 501).
- Findings, still present on `d4fb35d7`:
  - `publishingService.cancelAutoPublish` ← `usePublishing`;
  - `userAccountApi.getUserActivity` ← `useUserAccount`;
  - `authApi.logout`;
  - `topicService.recalculateStaleness`;
  - `topicService.overrideStaleness` ← `useTopics`;
  - `notificationsApi.markNotificationsAsRead` + `hooks/useNotifications/useNotifications.ts`.
- Docs:
  - `docs/architecture/module-map.md` (API rules);
  - `CLAUDE.md` (API Development);
  - `docs/deployment/cicd-pipeline-guide.md`.
- The CI workflow file has been hard to push via git before (HTTP 500); the Contents API is the fallback. That only matters at push time, which is not in this build.

## Tasks & Acceptance

**Execution:**
- [x] RED: matcher and scan tests with fixtures, covering every I/O row -- criteria 3, 4
- [x] GREEN: `specMatcher.ts`, then `check-api-paths.ts`, the npm script and the `yaml` devDependency -- criteria 1, 2, 3
- [x] Run on the repo: record the "before" summary line; classify and fix each of the six findings with a test (dead chain → delete; live, endpoint elsewhere → repoint; live, no endpoint, visible failure → issue + allow-list + note for the owner); write the allow-list with reasons -- criteria 7, 8
- [x] Layer baseline: generate it, review it by hand -- criterion 6
- [x] CI + Make: `frontend-static` steps, the `frontend-changes` filter, the Makefile target -- criterion 5
- [x] Docs -- module map, CLAUDE.md, CI/CD guide
- [x] Verify (below), including the mutation check

**Acceptance Criteria:** criteria 1 to 8 of the story file, as written there.

## Implementation Notes

**Counts (criterion 8).**
- Before (first run, `d4fb35d7` + 17.1, no allow-list, no baseline): `Frontend API calls: 310 resolved, 302 match the spec, 0 allow-listed.` 8 calls NOT IN SPEC (the six findings, at 8 call sites), 2 UNRESOLVED, 14 OUT OF LAYER files. 314 calls found in 2 + 310 + 2 (2 ignored: presigned S3 PUTs through bare axios).
- After: `Frontend API calls: 298 resolved, 298 match the spec, 2 allow-listed.` Exit 0.
- Allow-list: 2 entries (both `useFileUpload.ts`, endpoint passed as a hook option). Layer baseline: 13 files.
- The notes expected `url` in `eventApiClient` and `companyApi` on the allow-list. The scan resolves both branches of a `?:`, so they resolve and match. 15 → 13 out-of-layer files: `hooks/useNotifications/useNotifications.ts` was deleted (finding 6) and `useErrorToast.ts` only mentions `apiClient.get` in a comment.

**Findings (criterion 7).** No backend mapping for any of them (controllers implement generated interfaces; grep of `services/*/src/main/java` and `api-gateway` found none). Each chain was followed to a rendered component; none reaches one, so all six are dead chains, deleted with their tests. No repoint, no issue, no allow-list line.

| Call | Chain | Action |
|---|---|---|
| `DELETE /events/{c}/publish/schedule` | `publishingService.cancelAutoPublish` ← `usePublishing().cancelAutoPublish`; no component reads it (`PublishingControls` takes only publish/unpublish) | deleted service fn, hook mutation + `isCancelling`, `CancelAutoPublishResponse`, tests |
| `GET /users/me/activity` | `userAccountApi.getUserActivity` ← `useUserActivity`; no caller. The profile page's activity comes from `getUserProfile` | deleted fn, hook, `userAccountKeys.activity`, tests |
| `POST /auth/logout` | `services/api/authApi.ts`; only re-exported by `services/api/index.ts`, which nothing imports | deleted file + test + barrel line |
| `POST /topics/recalculate-staleness` | `topicService.recalculateStaleness`; no caller | deleted method + tests |
| `PUT /topics/{id}/override-staleness` | `topicService.overrideStaleness` ← `useOverrideStaleness`; no component | deleted method, hook, `OverrideStalenessRequest` (+ typo alias), tests |
| `PUT /notifications/read` | `notificationsApi.markNotificationsAsRead` (whole module only re-exported by the unused barrel) and `hooks/useNotifications/` (shadowed: `@/hooks/useNotifications` resolves to `hooks/useNotifications.ts`) | deleted `notificationsApi.ts` + test, the `hooks/useNotifications/` directory, barrel line |

"Each finding fixed with its own test": for a deletion the test is the check itself, which reported each call before and passes after. No new unit test per deletion.

**Design as built.**
- `specMatcher.ts`: `loadSpecIndex(dir)` / `buildSpecIndex(docs)` / `matchOperation(index, method, url)`. `{*}` is the unresolved-value token. Local `$ref`s for parameters and schemas are resolved; a cross-file `$ref` is unconstrained. Path parameter `pattern`s are not checked (a literal `_global` must match `{eventCode}`). A free-form `type: object` query parameter accepts any name. `minimum` is checked along with `maximum`.
- Scan rules beyond the plan: `?:` resolves to both branches (both must match); `+` concatenation; class fields read as `this.X`; helper locals and parameters are wildcards; a template or concatenation whose first part is unknown is UNRESOLVED (unknown base). Bare `axios` with a computed argument is ignored as presigned/external.
- Allow-list entries cover UNRESOLVED and NOT IN SPEC calls; STALE means an entry matches no failing call.

**Deviations.**
- TDD order: `specMatcher.ts` was written before `specMatcher.test.ts`; the scan tests were written before running the scan on the repo. All rules are covered by fixture tests.
- `.prettierignore` gained `.vitest`: `npm run format:check` failed locally on the Vitest HTML report (gitignored by 2fdc2470, not prettier-ignored). Local-only failure; CI disables the HTML reporter.
- `yaml` pinned in the lockfile by hand (`^2.9.0`, the `optional` flag removed): `npm install -D yaml` on rack (npm 10.9.9) bumped yaml to 2.9.1 and stripped `libc` fields from unrelated entries. `npm ci` accepts the edited lock.
- CI: extending `frontend-changes` to `docs/api/` also runs the three Vitest shards on a spec-only PR, not only `frontend-static`. Accepted: the shared `run` output gates all frontend jobs; splitting it would change `build-frontend`'s logic.

**Verification run (rack, 2026-11-07, logs in `~/.cache/batbern-test-logs/173-*.log`).**
- `npx vitest run src/test/contract scripts`: 2 files, 29 tests pass.
- `npm run check:api-paths` and `make check-frontend-api-paths`: exit 0, `Frontend API calls: 298 resolved, 298 match the spec, 2 allow-listed.`
- `type-check`, `lint`, `format:check`: pass (format:check after the `.vitest` ignore).
- Full Vitest: 441 files, 5752 passed, 45 skipped, 20 todo.
- `npm ci` with the edited lockfile: succeeds.
- Mutation: `/publish/status` → `/publish/statsu` in `publishingService.ts` gave NOT IN SPEC and exit 1; reverted, exit 0.
- Not verified: the CI run itself (no push in this build). On the PR, confirm with `gh run view` that `frontend-static` ran both new steps on Node 22, and that a docs/api-only change triggers it.

- Coordinator verification after the implementer (2026-11-07):
  - Re-confirmed that none of the six removed calls has a backend mapping (`git grep` over services and api-gateway; `publish/schedule` exists only as a POST in the spec).
  - Mutation check: `/publish/status` → `/publish/statsu` fails with NOT IN SPEC, exit 1; the file restored byte-identical, exit 0.
- Review patches applied (pass 1, triage log #3–#7, #9, #10):
  - `tsconfig.scripts.json` runs from `type-check`, and `lint`/`lint:ci` cover the script. An injected type error in `check-api-paths.ts` fails `tsc -p tsconfig.scripts.json` (exit 2).
  - Node ≥ 22.18 guard on `check:api-paths`.
  - Bare axios is ignored only for upload/presigned names.
  - A glued `{*}` inside a segment is UNRESOLVED.
  - A cross-file `$ref` query parameter makes the query unconstrained.
  - The enum check splits on commas only for array parameters.
  - Docs wording: module map and CI guide. CLAUDE.md is deferred.
- Final verification:
  - contract tests 31 passed;
  - `check:api-paths` and `make check-frontend-api-paths` exit 0 (298 resolved, 298 match, 2 allow-listed);
  - `type-check`, `lint`, `lint:ci` and `format:check` pass;
  - Vitest 441 files, 5754 passed.
  - Logs: `~/.cache/batbern-test-logs/173-v2-*.log`.

## Plan Change Log

## Review Triage Log

### Pass 1 (quick lens), 2026-11-07: 1 medium, 9 low; 6 patch, 1 defer, 3 rejected

| # | Finding | Verdict | Route | Evidence |
|---|---|---|---|---|
| 1 | `specMatcher.ts` written before its tests (TDD order) | low | reject | Process deviation the implementer reported. The 16 matcher tests cover every matcher rule and ran green. Redoing the red phase adds no protection |
| 2 | "Each finding is fixed with its own test": the six deletions have no unit test | low | reject | Each deleted call was flagged by the check before and is gone after, and the check fails in CI if any returns, so that is its regression test. Type-check proves no caller remains. A unit test of absent code guards nothing |
| 3 | The gate's own code is not type-checked (`scripts/`, `src/test/contract/` outside tsconfig) or linted (`eslint src` misses `scripts/`) | medium | patch | Confirmed: `tsconfig.json` excludes `src/test/**` and does not include `scripts/`; `lint` runs `eslint src`. A type error in the gate would never fail CI |
| 4 | `check:api-paths` needs Node ≥ 22.18 (type stripping); an older Node fails with an unclear error | low | patch | CI uses the latest 22.x, which is fine. Locally CLAUDE.md says "Node.js 20+". Direct fix: a version guard with a clear message |
| 5 | Bare `axios` with any computed argument is ignored, which is wider than the "presigned `uploadUrl`" row | low | patch | Measured: only `useEventTeaserImages.ts:46` (`urlResponse.uploadUrl`) and `eventApiClient.ts:481` (`uploadUrl`) are ignored today. Narrowing the ignore to upload/presigned names is a one-line fix |
| 6 | `fetch` calls are not scanned. `runtime-config.ts:59` fetches `/api/v1/config`, which no spec defines. The new docs say "every frontend HTTP call" | low | patch (module-map, CI guide wording) + defer (CLAUDE.md wording; `/config` missing from the specs) | Story criterion 2 names only the API client, axios and `*Client` receivers, so the docs overstate it. `/config` is served by the gateway's `ConfigController`: a pre-existing spec gap |
| 7 | An unresolved suffix glued inside a segment (`/events{*}`) matches any sub-path | low | patch | Measured: 0 such calls today. Direct fix: the scan treats a glued wildcard as UNRESOLVED, so it must be allow-listed with a reason |
| 8 | Call-site scope ignores enclosing function parameters; `let` reassignments are ignored | low | reject | No current instance: every bare-identifier argument is a unique per-file constant or a runtime option already allow-listed. The fix adds scope analysis to a regex scan, which the design says to avoid (allow-list instead) |
| 9 | A cross-file `$ref` query parameter is dropped, making the matcher stricter than intended | low | patch | Confirmed in `buildSpecIndex`: `resolveRef` returns `{}` and the parameter vanishes. No current spec does this, but 17.0c reuses the matcher at runtime. Direct fix: an unresolvable parameter makes the operation's query unconstrained |
| 10 | The enum check splits on commas for scalar parameters too | low | patch | Confirmed in `checkQuery`. Direct fix: split only for `type: array` parameters |

## Design Notes

**Architecture note:**
- **Modules:** frontend test infrastructure (`src/test/contract/`), a frontend CI script, CI (`build.yml`, the root `Makefile`), plus the fixes for the findings in frontend `services/` and `hooks/`.
- **New dependencies:** `yaml` as an explicit devDependency. No TypeScript runner, since Node 22 strips types.
- **Endpoints:** none added or changed.
- **Reused:** the missing-plus-stale pattern of `scripts/ci/check-zap-spec-coverage.py`; Vitest.
- **Deviations from the map:** none. The check makes the rule "API calls only in `services/`" visible.

Port the regex rules of the 2026-11-07 prototype, which resolved 306 of 316 calls. Paths in code are relative to `/api/v1`, and path constants are per file, so same-file resolution is enough.

## Verification

**Rack memory rules** apply to every run:
- one heavy job at a time;
- Vitest `--maxWorkers=4`;
- logs on disk, never in the `/tmp` tmpfs: `set -o pipefail; … 2>&1 | tee ~/.cache/batbern-test-logs/173-<name>.log`;
- check the `anon` line of `/sys/fs/cgroup/dev.slice/memory.stat` before each run, and stop above 12 GB.

**Commands:**
- `cd web-frontend && npx vitest run src/test/contract scripts --maxWorkers=4` -- expected: all pass
- `cd web-frontend && npm run check:api-paths` -- expected: exit 0 and the summary line
- `make check-frontend-api-paths` -- expected: the same
- `cd web-frontend && npm run type-check && npm run lint && npm run format:check` -- expected: pass
- `cd web-frontend && npx vitest run --maxWorkers=4` -- expected: all pass (finding fixes touched services and hooks)
- Mutation: change one service path to a typo, run `npm run check:api-paths` and expect exit 1 with NOT IN SPEC, then revert

**Manual checks:**
- Read the allow-list and the layer baseline: every line has a reason, and no finding is hidden.
