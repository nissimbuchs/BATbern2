---
type: epic
title: "EMS has walls a build checks"
parent: initiative-architecture-hardening
covers: [R17, R18, R19, R20, R21]
sources: []
companions: []
after: []
refined: false
risk: high
---

# EMS has walls a build checks

## Description

Gives EMS module boundaries that ArchUnit enforces. Winston designs the target packages and the move order (17.12, accepted by the owner). Then registrations leave `EventController` (17.13), domain exceptions get a base class (17.14), mail core splits from the mail center (17.15), and event core gets extension points (17.16).

## Outcome

The next EMS feature has an obvious module and a build that rejects a wrong dependency. The signal is that the module script shows fewer violating pairs and references than the 2026-11-07 baseline (28 and 100).

## Done when

- The owner has accepted `docs/architecture/ems-modularization.md`, with a defined baseline for the full-layering freeze.
- ArchUnit rules prove each move, and the module script's before and after numbers are recorded.
- Registration, mail and workflow behaviour is unchanged (existing tests, Bruno, Playwright).
- Each story reached production through its PR.

## Boundaries

- Out: the remaining module moves (planning, publishing, ai) after 17.12.
- 17.12 is a design task that needs only 17.5. The other stories wait as the initiative's Decision lines say.

## References

- parent — `_bmad-output/initiative-architecture-hardening/initiative-architecture-hardening.md`, section Requirements (R17, R18, R19, R20, R21), Constraints, Notes (Decision lines)
- prd — `docs/prd/epic-17-architecture-hardening.md`, Rules for every story, Story order
- reference — `docs/plans/ems-modularization-extension-points.md`
- reference — `docs/architecture/module-map.md`
