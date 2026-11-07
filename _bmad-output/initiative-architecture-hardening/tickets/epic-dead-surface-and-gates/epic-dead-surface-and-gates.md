---
type: epic
title: "Dead surface removed, gates in place"
parent: initiative-architecture-hardening
covers: [R5, R6, R7, R8, R9, R10]
sources: []
companions: []
after: []
refined: false
risk: high
---

# Dead surface removed, gates in place

## Description

Removes what nothing calls and puts the gates in place that every later epic measures against. The two reference specs, six orphan operations and the frontend publish-preview chain go (17.1). The two stub services and the EventBridge bus leave production (17.2). The old compose endpoints of the Mail Center go (16.8c). The FE path contract check (17.3), Spectral with a baseline (17.4) and the ArchUnit freeze (17.5) run in required CI jobs.

## Outcome

The owner and every later story get a smaller platform and three gates with committed baselines. The signal is that the operation count, the running ECS services and the event buses fall as measured, and that each gate fails a fixture violation in CI.

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
- prd — `docs/prd/epic-17-architecture-hardening.md`, Rules for every story, Story order
