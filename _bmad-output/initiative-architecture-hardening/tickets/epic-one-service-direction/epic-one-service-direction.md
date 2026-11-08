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
- The ArchUnit store loses the 4 CUMS client lines (17.6, 17.7) and the 4 EMS PCS client lines including `InboundEmailRouter`'s `PartnerMeetingRsvpClient` (17.8), and the Spectral baseline loses every operation that moves to `/internal`; before and after are recorded per story.
- Each story reached production, and the access-control DAST passes after deploy.

## Boundaries

- In: each story fixes the in-service violations its change exposes (C15): a client or listener it touches moves to the package the module map assigns, and an operation it moves to `/internal` leaves the Spectral baseline with it.
- Out: API convention changes to operations that stay public (epic 4, which waits on this epic); they are breaking changes with their own C4 scope, not in-service fixes.

## References

- parent — `_bmad-output/initiative-architecture-hardening/initiative-architecture-hardening.md`, section Requirements (R11, R12, R13), Constraints, Notes (Decision lines)
- prd — `docs/prd/epic-17-architecture-hardening.md`, Rules for every story, Story order
- reference — `docs/architecture/ADR-016-service-directions-and-service-to-service-calls.md`

## Notes

- Decision: 2026-11-08, owner. Epic 17 exists to make the architecture cleaner, not to document or freeze it (initiative C15). This epic's stories remove the cross-service groups 17.5 left frozen: CUMS clients toward EMS and PCS (17.6, 17.7) and EMS clients toward PCS (17.8). Apply the rule when the epic is refined.
