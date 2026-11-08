---
type: epic
title: "Dead surface removed, gates in place"
parent: initiative-architecture-hardening
covers: [R5, R6, R7, R8, R9, R10]
sources: []
companions: []
after: []
refined: true
risk: high
---

# Dead surface removed, gates in place

## Description

Removes what nothing calls and puts the gates in place that every later epic measures against. The two reference specs, six orphan operations and the frontend publish-preview chain go (17.1). The two stub services and the EventBridge bus leave production (17.2). The old compose endpoints of the Mail Center go (16.8c). The FE path contract check (17.3), Spectral with a baseline (17.4) and the ArchUnit freeze (17.5) run in required CI jobs. The gate stories also clean up: 17.5 moves the EMS classes that sit outside their module, and the frontend calls that 17.3 froze move into the service layer (3b).

## Outcome

The owner and every later story get a smaller platform, a cleaner EMS and frontend, and three gates with committed baselines. The signal is that the operation count, the running ECS services and the event buses fall as measured, that each gate fails a fixture violation in CI, and that the ArchUnit store and the frontend layer baseline end smaller than their first freeze.

## Requirements

- R1 (parent R5): only called endpoints remain. The `speakers-api` and `attendees-api` specs and 6 orphan operations are removed in spec and code, and so is the frontend publish-preview chain. The operation count drops by 6.
- R2 (parent R6): the SpeakerCoordination and AttendeeExperience services and stacks and the EventBridge bus are gone from production and the repo (checked live). The 2 in-process `@EventListener`s still receive their events.
- R3 (parent R7): a required CI check fails when frontend code calls a method and path that no spec defines. One importable spec matcher serves CI and the 17.0c harness. Frontend HTTP calls sit in the service layer: the baseline of files outside `web-frontend/src/services/` that call HTTP ends empty, or each remaining line says why.
- R4 (parent R8): Spectral, with a repo ruleset and a violation baseline, fails a PR only on new convention violations. Every added operation gets a comment naming its 3 nearest existing operations.
- R5 (parent R9): ArchUnit runs in the test task of CUMS and EMS (not PCS, owner 2026-11-08). The EMS package rules and the no-downstream-client rule are frozen, and the store can only shrink. Violations that need no 17.12 design or cross-service change are fixed in 17.5, and every group left in the store names the story that removes it.
- R6 (parent R10): the old compose components and the old preview, send and history endpoints and specs are deleted, including newsletter status and retry. Every organizer send goes through `/events/{c}/mails`.

## Constraints

- The parent's C1 (named guard), C2 (architecture note), C5 (a PR deploys to production), C6 (owner go per story) and C13 (migrations) bind every story.
- C4: a removed operation changes the spec, generated types, frontend callers, Bruno and Playwright in the same PR.
- C7 and C11 bind 17.2: no deploy on an event day, deploy-clean, and in-process events stay.
- C12 binds 16.8c: no real mail from any test, and send paths are verified on local dev.
- C3 binds 17.5: the frozen count is cross-checked against the module scripts.
- C15 (leave the architecture cleaner) binds every story, gate stories included: a baseline or store committed at the end of a story is smaller than at its start, or the story says why.

## Done when

- The 2 reference specs, the 6 orphan operations and the 16.8c endpoints are gone from `docs/api/` and the code, and they answer 404 in production. The operation count before and after is recorded.
- `aws ecs list-services` and `aws events list-event-buses` in production list neither stub service nor the `batbern-staging` bus. The 2 in-process listeners still fire.
- The FE path contract check, Spectral and ArchUnit each run in a required CI job and fail on a fixture violation. Their baselines are committed, with their sizes recorded.
- The EMS ArchUnit store after 17.5 is smaller than its first freeze, with before and after per rule, and holds only the groups whose owning story is named: `NewsletterEmailService` and `SlidesOnlineEmailService` (16.8c, 17.15), the EMS PCS clients (17.8), the CUMS clients (17.6, 17.7).
- The frontend layer baseline is empty after 3b, and the allow-list is no larger than after 17.3.
- Each story reached production through its own PR (or a batch the owner agreed) and was verified there.

## Boundaries

- In: every gate story fixes what its gate shows when the fix lies inside one service and needs no later design. 17.5 moves the inbound mail chain, the retention schedulers and the dev mail controller into `forwarding` / `mailcenter`, `JwtStompInterceptor` into `security`, `ModeratorReplyAddress` into `mailcenter/render`, and the mail-center exception handlers out of `GlobalExceptionHandler`. 3b moves the frozen frontend calls into `services/`.
- Out: violations whose fix needs a later story's design or a cross-service change: the mail-core split (17.12, 17.15), the EMS PCS clients (17.8), the CUMS clients (17.6, 17.7), and Spectral entries whose fix is a breaking API change owned by epics 3 and 4 (C4). The mock ratchet is epic 2.
- Out: the CDK and ECS parts of the stubs in 17.1; they belong to 17.2.
- Hands on: 17.3's spec matcher to epic 2 (17.0c); the remaining Spectral baseline and ArchUnit store groups to the stories named for them in epics 3, 4 and 5.

## References

- parent — `_bmad-output/initiative-architecture-hardening/initiative-architecture-hardening.md`, section Requirements (R5, R6, R7, R8, R9, R10), Constraints, Notes (Decision lines)
- prd — `docs/prd/epic-17-architecture-hardening.md`, Rules for every story, Story order, Stories 17.1 to 17.5
- story — `_bmad-output/implementation-artifacts/16-8-shared-composer.md`, Part 16.8c, AC18, Dev Notes "Endpoints retired in 16.8c" (v6 archive, covers 16.8a to c)

## Notes

- Decision: 2026-11-07. The epic needs nothing beyond the initiative's lines, the PRD and the existing story files, so it was refined from them without a separate pass.
- Decision: 2026-11-07. The tracer bullet is 17.1: the thinnest change through spec, backend, frontend, Bruno and production. 17.3 follows it and ships in the same PR if the owner agrees. 17.2 is next as the least certain story (production infrastructure).
- Decision: 2026-11-07. These waits are code collisions, not ordering: 17.2 waits on 17.1 (17.1 edits the stub modules' `build.gradle` and the ZAP matrix, which 17.2 deletes); 17.4 waits on 17.3 (both widen the `docs/api/` CI trigger and add CI steps).
- Decision: 2026-11-07, owner. 17.1 and 17.3 were converted from v6 story files into v7 story files. Their code-level findings sit in a notes file beside each story, and the v6 files were deleted.
- Decision: 2026-11-07, owner. 17.1 and 17.3 ship together in one PR.
- Decision: 2026-11-07, set check. 16.8c waits on 17.3 and 17.4. It is a breaking API change, so the contract check guards it, and its removed verb endpoints shrink the Spectral baseline like every later story's.
- Decision: 2026-11-07, set check. 17.5 gets no wait on 17.1 or 16.8c: deleting a controller only removes frozen violations, which the freeze allows. 17.2's `build.yml` edits touch other hunks than 17.3 and 17.4, and PRs merge one at a time onto an up-to-date `develop`, so no wait was added.
- Source conflict: 17.5's client rule. The action list (item 17.5) says "no new client toward an upstream service"; the PRD and ADR-016 say downstream.
- Decision: 2026-11-07. ADR-016 decides it: services call only upstream, so the frozen rule is "no new client toward a downstream service". The action list's wording is a slip.
- Decision: 2026-11-07, owner. 17.4's similarity comment needs no separate proof: no test PR, and no end-to-end check of the comment. It is informative, not blocking. The story's ordinary unit tests cover the script, and the first real PR that adds an operation shows it. This drops the PRD's 17.4 AC3 test-PR guard.
- Decision: 2026-11-07, owner. 17.2 ships in one branch and one PR with 17.0a (epic 2); the PR deploys on a date the owner picks that is not an event day.
- Decision: 2026-11-07, owner. 17.2 splits into entry 2 (stub services) and 2b (event bus), built in that order into the 17.0a PR. Production steps, each announced with the owner's go: empty the two stub ECR repos before the PR, remove the two required checks before merge, delete the three orphaned stacks after the deploy. `flyway_schema_history_speaker_coordination` stays in the database. 2b adds an integration test for each of the 4 in-process listeners (the PRD counted 2).
- Decision: 2026-11-08, owner. Epic 17 exists to make the architecture cleaner, not to document or freeze it: "With every story it should get better." Gate stories freeze only what needs a later story's design or a cross-service change, name that story per frozen group, and fix the rest in the same story. A baseline or store committed at the end of a story is smaller than at its start, or the story says why. This replaces the earlier Boundaries line that left all shrinking to epics 3, 4 and 5. Source: `docs/prd/epic-17-architecture-hardening.md`, Rules for every story, "Leave the architecture cleaner"; Story 17.5 renamed "ArchUnit freeze and first wall fixes" with AC4.
- Decision: 2026-11-08, owner. The 13 frontend files that 17.3 froze in `web-frontend/scripts/api-layer-baseline.txt` (15 counted by grep, 13 by the scan) get their calls moved into the service layer, baseline to zero where possible. New entry 3b, built right after 17.5. 17.3's own Boundaries stay as built.
- Assumption: 2026-11-08. The new entry's id is `3b`, after the `2b` precedent and the initiative's rule that ids keep the 17.x number, rather than the script's counter `169`.
- Assumption: 2026-11-08. 3b waits only on 17.3 (`after = [3]`); its place after 17.5 in the table is build order, not a code dependency.
- Decision: 2026-11-08, owner. Every line of the 17.4 Spectral baseline names its owning story or is an owner-accepted ADR-003 exception, enforced by `api-lint` through an owner file beside the baseline; groups no story covers become backlog stories.
- Decision: 2026-11-08, owner. R5 (parent R9) narrowed to CUMS and EMS: PCS is the most downstream service and has nothing to freeze.
- Decision: 2026-11-08, set check. 3b gets no wait on 2.0a: the ratchet only forbids new own-code mocks, and 3b's entry says its tests stub HTTP at the boundary. If 0a merges first, 3b's PR runs under it; if not, 0a's baseline is generated after 3b and is no larger.
