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

Removes what nothing calls and puts the gates in place that every later epic measures against. The two reference specs, six orphan operations and the frontend publish-preview chain go (17.1). The two stub services and the EventBridge bus leave production (17.2). The old compose endpoints of the Mail Center go (16.8c). The FE path contract check (17.3), Spectral with a baseline (17.4) and the ArchUnit freeze (17.5) run in required CI jobs.

## Outcome

The owner and every later story get a smaller platform and three gates with committed baselines. The signal is that the operation count, the running ECS services and the event buses fall as measured, and that each gate fails a fixture violation in CI.

## Requirements

- R1 (parent R5): only called endpoints remain. The `speakers-api` and `attendees-api` specs and 6 orphan operations are removed in spec and code, and so is the frontend publish-preview chain. The operation count drops by 6.
- R2 (parent R6): the SpeakerCoordination and AttendeeExperience services and stacks and the EventBridge bus are gone from production and the repo (checked live). The 2 in-process `@EventListener`s still receive their events.
- R3 (parent R7): a required CI check fails when frontend code calls a method and path that no spec defines. One importable spec matcher serves CI and the 17.0c harness.
- R4 (parent R8): Spectral, with a repo ruleset and a violation baseline, fails a PR only on new convention violations. Every added operation gets a comment naming its 3 nearest existing operations.
- R5 (parent R9): ArchUnit runs in the test task of CUMS, EMS and PCS. The EMS package rules and the no-downstream-client rule are frozen, and the store can only shrink.
- R6 (parent R10): the old compose components and the old preview, send and history endpoints and specs are deleted, including newsletter status and retry. Every organizer send goes through `/events/{c}/mails`.

## Constraints

- The parent's C1 (named guard), C2 (architecture note), C5 (a PR deploys to production), C6 (owner go per story) and C13 (migrations) bind every story.
- C4: a removed operation changes the spec, generated types, frontend callers, Bruno and Playwright in the same PR.
- C7 and C11 bind 17.2: no deploy on an event day, deploy-clean, and in-process events stay.
- C12 binds 16.8c: no real mail from any test, and send paths are verified on local dev.
- C3 binds 17.5: the frozen count is cross-checked against the module scripts.

## Done when

- The 2 reference specs, the 6 orphan operations and the 16.8c endpoints are gone from `docs/api/` and the code, and they answer 404 in production. The operation count before and after is recorded.
- `aws ecs list-services` and `aws events list-event-buses` in production list neither stub service nor the `batbern-staging` bus. The 2 in-process listeners still fire.
- The FE path contract check, Spectral and ArchUnit each run in a required CI job and fail on a fixture violation. Their baselines are committed, with their sizes recorded.
- Each story reached production through its own PR (or a batch the owner agreed) and was verified there.

## Boundaries

- Out: shrinking the Spectral baseline and the ArchUnit store (epics 3, 4, 5); the mock ratchet (epic 2).
- Out: the CDK and ECS parts of the stubs in 17.1; they belong to 17.2.
- Hands on: 17.3's spec matcher to epic 2 (17.0c); the Spectral baseline and the ArchUnit store to epics 3, 4 and 5.

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
