---
type: epic
title: "Services call in one direction over /internal"
parent: initiative-architecture-hardening
covers: [R11, R12, R13]
sources: []
companions: []
after: []
refined: false
risk: high
---

# Services call in one direction over /internal

## Description

Takes machine calls out of the public API and removes the two cycles. The 12 public S2S URLs move to `/internal`, behind one shared token helper (17.6). CUMS owns a user-reference merge contract, and the Mail Center pulls requests from CUMS (17.7). EMS stops calling PCS (17.8). ADR-016 is the rule.

## Outcome

The public specs carry only what users call, and no service depends on a downstream one. The signal is that the ArchUnit store holds zero CUMS→EMS and EMS→PCS clients, and the Spectral baseline loses the moved operations.

## Done when

- No client calls another service's `/api/v1` URL, and the moved operations are gone from the public specs.
- The merge preview counts EMS and PCS rows, and a merge rewrites both.
- The topic canvas and calendar RSVPs behave as before.
- Each story reached production, and the access-control DAST passes after deploy.

## Boundaries

- Out: API convention changes to the same operations (epic 4, which waits on this epic).

## References

- parent — `_bmad-output/initiative-architecture-hardening/initiative-architecture-hardening.md`, section Requirements (R11, R12, R13), Constraints, Notes (Decision lines)
- prd — `docs/prd/epic-17-architecture-hardening.md`, Rules for every story, Story order
- reference — `docs/architecture/ADR-016-service-directions-and-service-to-service-calls.md`
