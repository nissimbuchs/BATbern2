---
epic: epic-test-fidelity
date: 2026-11-15
verdict: accepted-with-open-items
criteria: declared
headless: true
---

# Retrospective: epic-test-fidelity (epic 2, "Tests run against real contracts and real-shaped data")

## Epic summary

**Tickets** (`tickets.py status`, build order; all `state: done`, none at `built`, `pending_tickets` empty, I checked):

| Ref | Title | Shipped in | Story commit(s) in the PR |
|---|---|---|---|
| 2.0a | Test rules and ratchet | #1258 (merged 2026-11-08, `75a2d3156`) | `3e84de7ed`, review fix `ff96bb5d0` |
| 2.0b1 | S2S contract harness on the public-API clients | #1277 (merged 2026-11-09, `12b4e14d7`) | `182886c79` |
| 2.0b2 | /internal operations specified and contract-tested | #1277 | `f571218a3` |
| 2.0b3 | Production setup in tests | #1277 | `455439083` |
| 2.0c1 | Frontend MSW harness | #1277 | `3d0cd163c` |
| 2.0d | Production-shaped fixtures | #1277 | `4a0ae39e0`, `52b7c80e8` |
| 2.0c2 | Ten worst mock-heavy frontend tests converted | #1277 | `f17229c33`..`b6398d4aa` (14 commits) |
| 2.sweep | Refactor sweep | #1277 | `a2bc56225`..`885f629bf` (13 commits) |

Follow-ups on this epic's findings (owner decisions, null-vs-optional, i18n fills, response conformance) shipped in #1279 (`8212faddb`); cited where relevant, not counted as epic scope.

**Ranges.** The plan baselines sit on diverging feature branches (I checked: `18b2051a` is not an ancestor of `8671ced6`, `aab7770b` not of `5e7d04c1`, `5e7d04c1` not of `bf8e0226`), and develop holds one squash commit per PR. Per-plan baseline ranges are therefore unusable; I used the PR branches and attributed commits by subject:
- 0a: `18b2051a6..3ac083a00` (#1258 head): 22 commits, 417 files, +14,409/-8,947. Mixed with epic 1 (17.1 to 17.5) work.
- 0b1 to sweep: `75a2d3156..81f705486` (#1277 head): 79 commits, 0 merges, 591 files, +33,512/-15,073. Contains non-epic commits (17.17 `/internal/companies` `cb11242c9`, registration link `ed07b0a66`, organizer migration V151 `7492c3c87`, others).
- `git_evidence.py` output: scratchpad `ge-0a.json`, `ge-1277.json` (not committed).

**Evidence inventory.** Read in full: epic file, `tickets.toml`, all 8 plans, the epic's entries in `deferred-work.md` (including the orchestrator's uncommitted `moved_to` lines), PR #1258/#1277 commit lists and bodies, CI check results of both PRs. Missing: story files (none, plans only); run folders (none in the epic folder); companions (`companions: []`); session logs (not available, so process lessons come from plans and commits only); previous retrospective (none existed). The 0c1 plan has no Implementation Notes section and all 13 task boxes unchecked; the sweep plan has 7 unchecked boxes. Their as-built record is the commit messages and PR body.

## Findings

### Ratchet: baseline, CI wiring, movement (verification of the epic's core signal)

- **F1. The ratchet is in required CI and the baseline fell. Accept (win).** `test-ratchet` job (`.github/workflows/build.yml:666-690`) has no path filter, runs its own fixture tests then `check:test-ratchet`, and is in `build-frontend.needs` (`build.yml:770`), which fails on any failed need (`:774-778`). `build-frontend` is a required check on develop (I checked branch protection: 6 contexts, `build-frontend` among them). Sums I measured from `web-frontend/scripts/test-ratchet-baseline.txt`: 937 at #1258 merge, 740 own-code (850 with the new `fe-mock-i18n`) at #1277 merge, 584 (679 with i18n) at HEAD. Matches orchestrator-measured 2026-11-15 (937 to 584). Per category at #1277 merge vs frozen: be-mock-in-process 59 to 43, be-mock-s2s-client 63 to 59, be-transactional-it 129 to 127, fe-mock-components 175 to 124, fe-mock-hooks 248 to 183, fe-mock-services 263 to 204.
- **F2. "Each story ends lower or says why" held, but 0c1 says why only in its commit.** 0b1 rose 63 to 85 by visibility and says so (plan Implementation Notes "Ratchet"); 0b2 85 to 62; 0b3 in-process 55 to 47, transactional frozen by design; 0d and 0c2 fell (21 lines removed for the 11 files, I checked all 11 now have zero lines). 0c1 left own-code categories unchanged and added `fe-mock-i18n` (115); the reason is only in commit `3d0cd163c`, the plan has no notes. Disposition: accept. Prevention: a plan is not done until its Implementation Notes record the ratchet start and end.
- **F3. Blind spot: `new MockAdapter(apiClient)` bypasses the MSW validator and is not counted. Defer.** 11 test files at HEAD attach axios-mock-adapter to `apiClient` (e.g. `src/services/api/eventRegistrationService.test.ts:23`, `src/contexts/AuthContext.test.tsx:106`), so their requests never reach the spec matcher. The ratchet counts only `vi.mock`/`vi.doMock`/`vi.spyOn` (`test-ratchet.ts:14-21`). This is the exact mechanism that hid #1034 (`userManagementApi.search.test.ts:4-5`). Count fell 24 to 11 over Epic 17 (I checked at `75a2d3156` and HEAD), so it is shrinking, but nothing stops a new one. Prevention: add a `fe-mock-http-adapter` category.
- **F4. Blind spot: backend unit `@Mock` of own non-client types. Defer (extends #1318).** `scanJavaTest` counts `@Mock` fields only when the type ends in `Client` (`test-ratchet.ts:406-408`). Heuristic count I ran (regex over `src/test`, approximate boundary list): 280 `@Mock` fields of non-client, non-boundary types in 93 files; 49 files have more than 2, which the rules call the wrong test level (`coding-standards.md` table under "Mock at the process boundary"). The deferred `verify(emailService)` item is a subset: I count 12 files with `@Mock EmailService`, 8 of them with `verify(emailService` (the plan said 13; `deferred-work.md:82`). Issue #1318 (open) covers only EmailService.
- **F5. Blind spot: skipped tests are not counted. Defer, low.** 31 `.skip(` in `web-frontend/src`, 22 `@Disabled` in Java tests (I counted). A test can leave the ratchet by being skipped instead of converted. Prevention: a skip count in the same ratchet.
- **F6. #1258 PR body states a stale count. Accept.** "Ratchet fe-mock-services: 193 / 215 to 184 / 203" (#1258 body, "Measured") is true at `59d7bbada`; the later review fix `ff96bb5d0` (counts `vi.spyOn`) moved the merged baseline to 193/263. Lesson: regenerate "Measured" lines after the last commit.

### Spec-to-implementation reconciliation (epic Requirements and Done when)

- **F7. R2 evidence present (I checked the tests exist, not that they pass).** #1072 fixture `ContractServerTest.should_fail_when_limitExceedsSpecMaximum` (`shared-kernel/src/test/java/ch/batbern/shared/test/contract/ContractServerTest.java:80`); joined To: `EmailServiceTest.should_rejectJoinedTo_when_noSesClient` (`EmailServiceTest.java:92`, also `:104`, `:116`); `ProductionChainIntegrationTest` in EMS, PCS, CUMS plus gateway `ProductionSecurityFilterChainTest`. Name-based check of every main class using `RestTemplate`/`RestClient`: each S2S caller has a `*ContractTest`; the uncovered ones are OpenAI (boundary), gateway routing/health and the shared `ContractCalls` helper. No gate enforces this for a new client: defer, low.
- **F8. R4 evidence present.** #1034: `UserRepositoryTest.java:378`, `userManagementApi.search.test.ts`, `PromoteSpeakerSubView.test.tsx:95`; #1104: `UserControllerIntegrationTest.java:848`; fixture guards in `web-frontend/src/test/fixtures/` and shared-kernel `ProductionShapedPeople`. The 0d plan records the bite checks (AC1 proof, regression bite) in Implementation Notes.
- **F9. Epic constraint contradicted by three stories. Spec reconciliation.** `epic-test-fidelity.md:34` limits agent-context edits to 17.0a. 0b3 edited `CLAUDE.md` (+8) and `project-context.md` (`455439083`), 0c1 `project-context.md` (`3d0cd163c`), 0c2 too (`a1c90e315`), the latter citing "owner allows agent-context edits in 17.0 stories" (0c2 plan Tasks). I found no source for that allowance in the epic, initiative or PRD; 0d and the sweep obeyed the constraint. Disposition: owner confirms; Sofia records it on the epic.
- **F10. Product behavior decided without the owner. Accept, record.** The partner directory sort became a backend sort "as the brief asked (not an explicit owner decision)" (`deferred-work.md:33`). CLAUDE.md "Ownership" lists product behavior as an owner decision. Lesson: overnight briefs must not pre-decide owner items.

### Diff-scope lenses (adversarial, edge-case, verification gap), inline

Narrowing recorded: `bmad-review` was not invoked (orchestrator rule). I ran the lenses over the 81 files touched by two or more tickets (computed from the story commits above), focused on the shared harness, ratchet and fixture files, and on what the review logs deferred. Not reviewed hunk by hunk: the remaining 314 single-ticket files.

- **F11. Stale deferred item became a duplicate issue. Fix now.** `deferred-work.md:69` and issue #1317 (opened 2026-11-15) say the EventForm auto-save block is `describe.skip` and hangs. Commit `4cc8d1adf` (#1277) fixed the cause (an identity-mocked `useDebounce` plus debouncing a new object every render) and unskipped the block: `EventForm.test.tsx:463` is `describe('Auto-Save Functionality …')` with 5 `it(...)` on the real `useDebounce`. The sweep plan still says "its block stays skipped" (`story-refactor-sweep-plan.md:118`); review finding 5 (`:152`) was patched later without updating the record. What remains true: the file still mocks `@/services/eventApiClient` and `@/hooks/useAuth` (`EventForm.test.tsx:30,44`; ratchet lines services 1, hooks 1). Action: reword #1317 to that remainder.
- **F12. Cross-ticket reversal handled well. Accept (win).** 0b1 introduced `GeneratedCodeSemantics` rule 2 (any optional may be `null`); #1279 `7da3148ba` removed it after the specs marked real nulls `nullable`, red first, and found the validator also rejected `nullable` references (`GeneratedCodeSemantics.java:22-53`). The harness ends stricter than any single ticket left it.
- **F13. Fixture defaults vs corrected spec paging. Checked, clean.** 0d made `listRegistrations` answer from fixtures; 0c2 changed the spec to 1-indexed `page`, default `limit` 25. The 0c2 plan records `fixtureDefaults.test.ts` red then green on that change (Implementation Notes), and both files moved together (`fixtureDefaults.ts`, `fixtureDefaults.test.ts` in the 0c2/0d overlap).
- **F14. Shared CUMS `TestAwsConfig` across 0b3, 0d, sweep. Checked, clean.** 0d's constant Cognito stub return was corrected to `null`, then the sweep removed the mock for `InMemoryCognito` (`a2bc56225`, `7bc5a84a2`) and found a real defect (`114d636f0`, new speakers lost their Cognito `sub`).

### Aggregate views

- **Architecture delta.** New test-scope infrastructure only: `ch.batbern.shared.test.contract` (5 classes, 1,409 lines), `web-frontend/src/test/msw` (8 files, 1,328 lines), `docs/api/internal/` (3 specs with their own Spectral ruleset). New test dependencies `openapi-request-validator-core:3.0.0` (`testFixturesApi`) and `jaxb-api:2.3.1` (runtime) in `shared-kernel/build.gradle:124,127`. No production module dependency changed. Accept.
- **Duplication map. F15. Defer.** Four baseline gates each carry their own `formatBaseline`/`parseBaseline`/`compare`: `test-ratchet.ts:446-502` (0a), `check-i18n-keys.ts:423-461` (sweep), `check-api-type-copies.ts:172-210` (17.3c), `check-s2s-client-urls.ts:168-220` (epic 3). Same RAISED/STALE contract, four implementations. Prevention: `scripts/lib/baseline.ts` before a fifth gate.
- **Size growth.** Largest new files: `scripts/dev/capture-spec-examples.ts` 939 lines (one-off capture tool), `test-ratchet.ts` 612, `check-i18n-keys.ts` 528, `ContractServer.java` 416, `OpenApiContract.java` 417. None is a god class: each has one job. `UserControllerIntegrationTest.java` shrank to 1,546 lines. Accept.
- **Pattern divergence.** None found beyond F15. Every gate follows the 17.3 pattern (plain TS, Node type stripping, fixture tests in the same CI job).

## Behavior verification

Runtime behavior was not exercised by this retrospective (no Gradle, Vitest or Playwright run, per orchestrator rule). Recorded by the build, not re-checked: plan verification runs (0b2: shared-kernel 523/0, EMS 2539/0; 0b3 coordinator: EMS 2545/0; 0c2: full Vitest 465 files, 6012 passed; sweep: CUMS 923 green three times, full Vitest 6017 passed). I read the PR check results: #1258 and #1277 both passed `test-ratchet`, `build-frontend`, all `build-services`, deploy, post-deploy checks, Bruno and Playwright @smoke, with auto-rollback skipped.

## Previous-retro follow-through

No previous retrospective file existed (none of the initiative's epics had one before this batch). Nothing to follow through.

## Action items

All proposed, none applied.

| # | Action | Kind | Owner | Source |
|---|---|---|---|---|
| A1 | Reword issue #1317: the auto-save block runs since `4cc8d1adf`; what remains is the `eventApiClient` and `useAuth` mock in `EventForm.test.tsx`. Mark the `deferred-work.md:69` entry resolved by `4cc8d1adf` with that remainder. | fix now (record) | orchestrator / Sofia | F11 |
| A2 | Ratchet category for `new MockAdapter(apiClient)` (11 files), baseline at today's count. | remediation | dev loop, new story | F3 |
| A3 | Widen #1318 from EmailService to own-type `@Mock` fields in unit tests (count per file, or more than 2 per file), with my heuristic numbers as the starting point. | remediation | owner decides scope | F4 |
| A4 | Count `.skip` / `@Disabled` in the ratchet. | remediation | dev loop | F5 |
| A5 | Extract the shared baseline/compare logic of the four gates into `scripts/lib/baseline.ts`. | remediation | dev loop | F15 |
| A6 | Reconcile `epic-test-fidelity.md:34` with the agent-context edits of 0b3, 0c1, 0c2: owner confirms the allowance, Sofia records it. | spec reconciliation | owner, Sofia | F9 |
| A7 | Process: a plan reaches `done` only with Implementation Notes (ratchet start/end) and ticked tasks, and a review patch updates its `deferred-work` entry in the same commit. | process lesson | bmad-build override owner | F2, F11, inventory |
| A8 | Process: overnight briefs leave owner items (product behavior) open instead of pre-deciding them. | process lesson | owner | F10 |

## Decision records

Headless: proposed only, none written.
- `adr-mock-only-at-the-process-boundary-with-a-monotone-ratchet`: 0a plan Intent and Design Notes, commit `3e84de7ed`. Passes: binding on every future test, surprising without context (a Java scanner in `web-frontend/scripts`, wired into a frontend check), real trade-off (counts may only fall vs a one-time conversion). Today it lives only in `coding-standards.md`.
- `adr-flush-before-rollback-instead-of-converting-transactional-its`: 0b3 Boundaries ("Do not convert the 129 class-level `@Transactional` ITs; the listener covers them") and Design Notes, commit `455439083`. Passes: hard to reverse across 99 remaining ITs, surprising, a real trade-off.
- `docs/api/internal/` with its own reduced ruleset (owner, 2026-11-08, 0b2): not proposed, ADR-016 (`ADR-016-service-directions-and-service-to-service-calls.md:41`) already names the upstream's internal spec.

## Acceptance verdict

**accepted-with-open-items** (criteria declared, epic file Requirements R1 to R4 and Done when).
- Rules in all three places, contradicting line gone: met (I grepped: only quotes in the proposal and PRD remain; `bmad-build.toml:16`, `project-context.md:214`).
- Ratchet in a required job, baseline only falls, each story lower or says why: met (F1, F2).
- Every S2S client contract-tested, `/internal` included, every service has a production-chain test: met by name-based check (F7).
- MSW harness with real i18n and fixtures is the default, 10 worst files converted: met (`src/test/setup.ts:85` starts the server with `onUnhandledRequest: 'error'`; 11 files at zero ratchet lines).
- Each story reached production through its PR: met (#1258, #1277, checks passed).
- Open items: A1 to A6. None blocks: the blind spots (F3 to F5) limit how far the ratchet can be trusted as a signal, they do not undo the delivered criteria. No unfinished tickets.

## Open questions

- Does the owner want the ratchet to cover unit-test `@Mock` fields (A3)? It changes the denominator a lot (280 fields, heuristic) and the rule's "unit tests of pure functions stay as they are" exception needs a mechanical form first.
- Was the agent-context allowance (F9) given by the owner? A yes turns F9 into a record-only fix.

## Assumptions

- Epic resolved from the given folder `_bmad-output/initiative-architecture-hardening/tickets/epic-test-fidelity/`; `pending_tickets` was empty, so no forced rejection.
- Machine verdict rendered without a human decision.
- Ticket-to-PR mapping and story attribution are mine, from commit subjects in the PR commit lists (`gh pr view --json commits`).
- Counts labeled "heuristic" (F4, F5, F7) come from regex scans I wrote, not from the ratchet; they can miss multi-line or unusual declarations.
- Each action item and both decision records are proposals; none was applied, filed or written.
- The `moved_to` lines in `deferred-work.md` and issues #1317/#1318 are the orchestrator's uncommitted work of 2026-11-15; I read them as current.
