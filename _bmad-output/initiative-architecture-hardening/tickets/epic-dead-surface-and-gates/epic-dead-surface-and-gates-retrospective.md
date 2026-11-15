---
epic: epic-dead-surface-and-gates
date: 2026-11-15
verdict: accepted-with-open-items
criteria: declared
headless: true
---

# Retrospective: Dead surface removed, gates in place (epic 1)

## Epic summary

Epic file: `epic-dead-surface-and-gates.md` (Requirements R1 to R7, 7 Done-when lines). `tickets.py status` (run 2026-11-15): 10 of 10 tickets `done`, `pending_tickets` empty, none at `built`.

| Ref | Ticket | Plan baseline | Shipped in (squash on `develop`) |
|---|---|---|---|
| 1.1 | Dead API surface removed (17.1) | `1de313f1` | #1250 (`2b42b840`) |
| 1.3 | Frontend path contract check (17.3) | `d4fb35d7` | #1250, owner-agreed pair (epic Notes, 2026-11-07) |
| 1.2 | Stub services gone (17.2 A) | `061b3411` | #1258 (`75a2d315`), with 17.0a (owner-agreed) |
| 1.2b | Event bus gone (17.2 B) | `9786d3c9` | #1258 |
| 1.4 | Spectral lint + similarity report (17.4) | `c2410c8a` | #1258 |
| 1.5 | ArchUnit freeze + first wall fixes (17.5) | `eb341346` | #1258 |
| 1.3b | Frontend HTTP calls into services | `7820a614` | #1258 |
| 1.3c | Generated-type copy gate | `67c38447` | #1279 (`8212fadd`) |
| 1.168c | Mail Center composer clean-up (16.8c) | `b13922c2` | #1281 (`bb367d49`), with 4.9 |
| 1.sweep | Refactor sweep | `cde40896` | #1289 (`4254d93b`), with 3.6, 3.6b, 3.8, 4.10, 4.10b to d |

Production steps outside a PR: the three orphaned stacks (`BATbern-staging-SpeakerCoordination`, `-AttendeeExperience`, `-EventBus`) and the two stub ECR repos were deleted by the owner on 2026-11-10 (plan 1.2, Production verification).

**Ranges.** The plan baselines sit on feature branches that were squash-merged, so they do not chain: `d4fb35d7 -> 061b3411`, `7820a614 -> 67c38447`, `67c38447 -> b13922c2`, `b13922c2 -> cde40896` are not ancestors (I checked with `git merge-base --is-ancestor`). I therefore read the epic's diff as the five squash commits on `develop` (`<parent>..<merge>` for #1250, #1258, #1279, #1281, #1289; one commit each), and used two feature-branch ranges for ticket provenance (`1de313f1..d4fb35d7`, 2 commits, 1.1; `061b3411..7820a614`, 8 commits, 1.2 to 1.3b). Narrowing: #1258, #1279, #1281 and #1289 also carry tickets of epics 2 to 4, so their churn is not attributable to this epic alone; I used it only to find files touched by two or more of the epic's PRs (337 files, 83 code files in three or more).

**Evidence inventory.** Read in full: epic file, all 10 plans (Intent, Implementation Notes, Review Triage Log, Verification, Production verification), story files 1.1 and 1.3 (criteria), PR bodies #1250, #1258, #1289 and the review comment on #1250. Read for counts: the current gate baselines and owner files, both ArchUnit stores, `module-map.md` Gates, `build.yml` `frontend-static` / `build-frontend` / similarity job, `deferred-work.md`, branch protection of `develop` (read-only `gh api`). Not read: the notes files beyond what the plans cite. Missing: session logs (not available to this run, so no process lessons from session history); no run folders exist in the epic folder.

## Findings

Each finding: source, disposition for this instance, prevention lesson.

### Architecture delta

- **F1. Platform shrank as measured.** ECS services: gateway, EMS, CUMS, PCS only; event buses: `default` only (plan 1.2 and 1.2b, Production verification, live check 2026-11-10, recorded by the build, not re-checked). The domain events stay in process through `SpringDomainEventPublisher`; a log-based alarm `{Service}-Event-Listener-Failures` covers swallowed listener failures (`infrastructure/lib/constructs/ecs-service-alarms.ts:256-280`, I checked). Disposition: accept. Prevention: none needed.
- **F2. ArchUnit stores ended far below their first freeze.** First committed EMS freeze after rework: 79 lines (mail center wall 73, watch 2, EMS PCS clients 4); after 17.5: 9 (plan 1.5, Fix part and Follow-up). Today, I checked: EMS store `c1e3ecec` 2 lines (mail core: `MailPipeline.render -> compose.MailRenderer`, `MailPipeline.renderNotice -> compose.PlaceholderScanner`), the five other EMS rule files 0 lines; CUMS `e75ed5e2` 0 lines (was 4). Disposition: accept. Prevention: none.
- **F3. Frontend layer baseline and allow-list are empty.** `web-frontend/scripts/api-layer-baseline.txt` and `api-paths-allowlist.txt` hold header comments only, 0 entries (I checked). History: 13 files frozen by 1.3, emptied by 1.3b; allow-list 2 -> 0 (plan 1.3b, Sizes). Disposition: accept.

### Spec-to-implementation reconciliation

- **F4. The 2 remaining ArchUnit lines name a dropped story.** Done-when line 4 and R5 require that every group left in the store names the story that removes it. The 2 mail-core lines are owned by 17.15, which the owner dropped on 2026-11-14 (`initiative-architecture-hardening.md:88`, "stay frozen with no owning story"). `docs/architecture/module-map.md:87-88` still says "(17.15)"; it is the only remaining reference to 17.12, 17.15 or 17.16 in the owner files, module map, coding standards and ArchUnit config (I checked by grep). Disposition: accept the deviation (owner decision), fix now the module-map wording and reconcile the epic's Done-when line. Prevention: when a story is dropped, grep every gate owner file and the module map for its id in the same change.
- **F5. Production 404 of the 6 orphan operations was never recorded.** Done-when line 1 and story 1.1's Verify line ("each removed path answers 404 in production, 405 where a sibling shares the path") require a production check. Plan 1.1 has no Production verification section, and #1250's body lists local verification only (EMS IT, Bruno against local dev). By contrast 16.8c records "production checked: the retired operations answer 404" (plan 1.168c, last line). The status has also moved: `invite-batch` answers 405 since Story 4.10 (`RemovedEndpointsIntegrationTest.java:50-51`). Disposition: defer to one read-only production probe of the 5 EMS paths and `POST /logos/fetch-from-url`, with the owner's go; the ITs pin the expected status per path. Prevention: a plan whose ticket Verify line names production does not reach `done` without a Production verification section.
- **F6. "The 2 in-process listeners still fire" is proven by test, not in production.** `DomainEventListenersIntegrationTest` covers 4 listeners through the real publisher, with a mutation check (plan 1.2b, Implementation Notes); the alarm of F1 would fire on a swallowed failure. No live observation is recorded. Disposition: accept (IT plus alarm is the stronger guard). Prevention: none.
- **F7. Gates run in required CI jobs and fail fixture violations.** I checked: `develop` requires `build-frontend` and `build-services (event-management-service | company-user-management-service)`; `build-frontend` needs `frontend-static` (`build.yml:769-770`), which runs the path check, the type-copy check and Spectral, each after its fixture tests (`build.yml:555-597`); frontend jobs also run on a `docs/api/`-only change (`build.yml:485`); the similarity report is `continue-on-error` and needed by nothing (`build.yml:706-711`). ArchUnit runs in each service's `test` task, with `EmsModuleRulesFixtureTest` and `FrozenRuleKeysTest` (plan 1.5; `module-map.md` Gates). Disposition: accept.
- **F8. Spectral baseline is fully owned.** I checked: 78 lines (first freeze 150, plan 1.4), each matching exactly one of 11 groups in `api-lint-owners.txt`: 10 owner exceptions with reasons (X1 22, X4 16, X2 8, X3 8, X6 8, X7 5, X5 3, X9 3, X8 2, N7 2) and 1 issue (#1070, `GET /events/current`). No story-owned group is left. Type-copy baseline: 0 entries (start 72, plan 1.3c; 2 -> 0 by 16.8c). Disposition: accept. Note: 77 of 78 lines are permanent exceptions, so this baseline will not shrink further without an owner decision.
- **F9. PR batching is only partly recorded as owner-agreed.** Done-when line 7 allows a batch "the owner agreed". Recorded: 1.1 + 1.3 in one PR, 1.2 with 17.0a (epic Notes 2026-11-07). Not recorded in the epic, `tickets.toml` or the initiative: 1.4, 1.5 and 1.3b riding #1258, 1.3c in #1279, 168c in #1281, sweep in #1289 (I searched). C5 requires the owner's go before any PR is opened, so agreement is likely, but the tree does not show it. Disposition: accept. Prevention: record each batch as a Decision line in the epic when the PR is opened.

### Duplication map

- **F10. The owned-baseline logic exists three times.** `parseOwners` / `checkOwners` in `web-frontend/scripts/api-lint.ts:273,312`, `check-api-type-copies.ts:255,294`, `check-s2s-client-urls.ts:264,298`; a diff of the first two shows only field names and messages differ (I checked). `parseBaseline` exists in 5 scripts. 1.3c already extracted `listSourceFiles` / `EXCLUDED` into `scripts/lib/source-files.ts`, so the pattern for sharing exists. Disposition: defer to a backlog story (extract `scripts/lib/owned-baseline.ts`, keep each gate's key format). Prevention: the third copy of a helper is extracted in the story that writes it.

### God-class / size growth

- **F11. Gate scripts are large but not god-classes.** `web-frontend/scripts`: `check-api-paths.ts` 621 lines (touched by #1250, #1258, #1279), `test-ratchet.ts` 612, `check-i18n-keys.ts` 528, `api-lint.ts` 410; 5404 lines with tests and lib (I checked with `wc -l`). Each has one job. Disposition: accept; F10 removes part of the bulk. Prevention: none beyond F10.

### Pattern divergence

- **F12. Two removed-path ITs with different conventions.** `security/RemovedEndpointsIntegrationTest.java` uses a non-existent event and asserts the "No endpoint" body for 404 rows, but nothing beyond the status for 405 rows (lines 81-84: the ternary repeats `status().is(expected)`). `mailcenter/RetiredComposePathsIntegrationTest.java` uses a real event and asserts no SES mail and no state change, status only. Disposition: accept (both pin the contract). Prevention: none.
- **F13. Gate scripts written before their tests.** 1.3 (`specMatcher.ts` before its tests, plan 1.3 Review #1) and 1.3c (gate before tests, plan 1.3c Review Triage) both deviated from TDD; both reviews rejected a retroactive fix. Disposition: accept. Prevention: the build override for gate stories names the fixture test as the first commit.
- **F14. API JSON comes from a hand-built ObjectMapper.** 1.2b found that `eventBridgeObjectMapper` was the HTTP mapper of all three services and moved it to `SharedObjectMapperConfig` unchanged (`INDENT_OUTPUT` at line 30; ADR-014:134). Issue #1254 is open. Disposition: defer (tracked by #1254, owner decision).

### Diff-scope review (inline lenses, narrowed)

`bmad-review` was not invoked (orchestrator rule). Lenses run inline over: files touched by two or more of the epic's PRs, and the items the plans' review logs left deferred. Results:

- **Adversarial.** The 1.3 -> 1.3b boundary (layer baseline, allow-list) and the 1.3c -> 168c boundary (newsletter type copies): both ended at 0 entries (F3, F8). The 1.5 -> 168c boundary (mail center wall store): 168c moved `OwnText` to mail core and the wall store went 2 -> 0 (plan 168c, Counts; store file 0 lines, I checked). No defect found.
- **Edge case.** Review-deferred items of this epic in `deferred-work.md`: the `infrastructure/CLAUDE.md` numbering gap (resolved, `ac1692fe`), listener failure metric (resolved, `edf8aa4a`, plus the log alarm of F1), hand-written type copies (moved to 1.3c). None open.
- **Verification gap.** F5 (production 404) and F6 (listeners in production). 1.5 records "no live STOMP check" after moving `JwtStompInterceptor` (plan 1.5, Fix part), and 1.3b records no full Vitest, Playwright or click-through; #1258's body records a green pre-push gate with full Vitest and all service suites on the rebased branch, so only the live STOMP path stays unobserved. Disposition: accept.

## Behavior verification

Runtime behavior was not exercised by this retro. Production verification recorded by the builds, not re-checked: ECS services and event buses after the stack deletion (plan 1.2, 2026-11-10); 16.8c retired operations answering 404 (plan 1.168c); gates running green as required checks in #1281 and #1286 (plans 1.4, 1.5, 1.3b). Not recorded anywhere: the 17.1 removed operations in production (F5).

## Previous-retro follow-through

No previous retrospective file existed: this is one of five retrospectives of the initiative written in one batch, and none of its epics had one before. Nothing to follow through on.

## Action items

Proposed, not applied. Owner of every item: Nissim (decides and assigns); spec reconciliations go through Sofia (`bmad-agent-po`).

1. **Fix now (doc).** `docs/architecture/module-map.md:87-88`: replace "(17.15)" with "owner-accepted, no owning story (EMS modularization dropped 2026-11-14)". Source F4.
2. **Spec reconciliation.** Epic Done-when line 4 and R5: record that the 2 mail-core lines stay frozen by owner decision 2026-11-14, so the epic stops failing its own wording. Source F4.
3. **Defer, owner go.** One read-only production probe of the 6 removed 17.1 operations, compared with the statuses `RemovedEndpointsIntegrationTest` and `LogoUrlIntegrationTest` pin; record it in plan 1.1. Source F5.
4. **Defer, backlog story.** Extract the baseline and owner-file parsing of `api-lint.ts`, `check-api-type-copies.ts`, `check-s2s-client-urls.ts` into one `scripts/lib` module. Source F10.
5. **Process.** `_bmad/custom/bmad-build.toml`: a plan whose ticket names production in Verify or Done-when keeps a Production verification section, filled before `done`. Source F5.
6. **Process.** When a story is dropped, grep its id over gate owner files, the module map and ArchUnit config in the same change. Source F4.
7. **Process.** Record every multi-story PR as a Decision line in each affected epic when the PR is opened. Source F9.

## Decision records

Proposed only (headless, none written):

- `adr-architecture-gates-freeze-only-what-a-later-story-owns.md` (system-wide, `docs/architecture/`): gates commit a baseline that only shrinks, every line has an owner story or a reasoned exception, and the gate story fixes the rest itself. Source: epic Notes "Decision: 2026-11-08, owner"; plans 1.4, 1.5, 1.3c; commit `7820a614`. Why (from the epic Notes): "With every story it should get better." Today only `coding-standards.md` and `module-map.md` state it, not an ADR.
- `adr-mail-core-excepted-from-the-mail-center-wall.md` (beside `module-map.md`): `mailcenter.render` and `mailcenter.variables` are treated as mail core and excepted from the wall, with a separate frozen rule against mail core using the mail center. Source: plan 1.5, "Deviation, needs owner/architect eyes" and "Follow-up in step 3"; shipped in #1258. Why (from the plan): the literal rule froze 397 lines and would have failed every new mail-sending method against the module map's reuse table. Surprising without context, and permanent since 17.15 was dropped.

Already recorded, not proposed: event bus removal and the shared ObjectMapper (ADR-014), service direction (ADR-016), action endpoints for the verb rule (ADR-013).

## Acceptance verdict

**accepted-with-open-items** (criteria declared, 7 Done-when lines).

- Line 1 (specs, orphan operations, 16.8c endpoints gone, count recorded): met in repo (325 -> 319, plan 1.1); 16.8c verified in production; the 17.1 production 404 check is not recorded (F5, open).
- Line 2 (no stub service, no bus, listeners fire): met live 2026-11-10 (F1); listeners proven by IT and alarm (F6).
- Line 3 (gates in required jobs, fixture failures, baselines committed with sizes): met (F7, F8).
- Line 4 (EMS store smaller, only owned groups): smaller, met; ownership deviates by owner decision (F4, open doc item).
- Line 5 (layer baseline empty, allow-list not larger): met (F3).
- Line 6 (3c in build-frontend, fixture copy fails, baseline smaller and owned): met (72 -> 0, F8).
- Line 7 (own PR or agreed batch, verified there): met for all tickets; batch agreement partly unrecorded (F9).

No ticket is unfinished, no blocking finding stands. Open items: F4 (action items 1, 2), F5 (item 3), F10 (item 4).

## Open questions

- Does the owner want the 6 removed 17.1 operations probed in production (F5), or is the IT pinning enough?
- Should the 77 exception lines of the Spectral baseline (F8) stay permanent, or should some exception classes be revisited?

## Assumptions

- The epic resolved from the folder given by the orchestrator; `pending_tickets` was empty.
- Machine verdict `accepted-with-open-items`, rendered without a human decision.
- Diff evidence read from the five squash commits on `develop` because the plan baselines do not chain (see Ranges); per-ticket churn was not attributed.
- Previous retro: none existed. Session logs: not available.
- All 7 action items and both decision records are proposals; no file other than this one was written.
