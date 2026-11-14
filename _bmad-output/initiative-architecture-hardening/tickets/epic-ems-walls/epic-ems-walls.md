---
type: epic
title: "EMS has walls a build checks"
parent: initiative-architecture-hardening
covers: [R18, R19]
sources: []
companions: []
after: []
refined: true
risk: medium
---

# EMS has walls a build checks

## Description

Splits the two largest EMS files, each a refactor with unchanged behaviour. Registrations leave `EventController` (2334 lines) for their own controller (17.13). Domain exceptions get a base class carrying their HTTP status and error code, so `GlobalExceptionHandler` (1486 lines) maps the base class instead of each domain exception (17.14). Narrowed by the owner on 2026-11-14: the EMS modularization (17.12 design, 17.15 mail core split, 17.16 extension points, Spring Modulith, package moves) is out.

## Outcome

A change to registrations or to an exception no longer edits `EventController` or `GlobalExceptionHandler`. The signal is an ArchUnit rule per story that fails when a registration endpoint returns to `EventController` or a domain exception is mapped in `GlobalExceptionHandler`, and the EMS module script showing the `EventController -> registrations` (10 references) and `GlobalExceptionHandler` (13 references) violations gone.

## Requirements

- R1 (parent R18): `EventController` holds no registration endpoints, and all registration paths behave as before.
- R2 (parent R19): `GlobalExceptionHandler` imports no domain class, and every exception keeps its status and error code.

## Done when

- `EventController` has no registration endpoint and implements no registrations interface; an ArchUnit rule holds it.
- `GlobalExceptionHandler` maps no domain exception class; an ArchUnit rule holds it, and every exception keeps its status and error code.
- The EMS module script, re-run, records the before and after numbers of each story (2026-11-14: 26 violating pairs, 92 references).
- Registration paths, the registration wizard and every error response behave as before (integration tests, Bruno, Playwright).
- Each story reached production through its own PR and was verified there.

## Boundaries

- Out (owner 2026-11-14): 17.12 (modularization design), 17.15 (mail core split), 17.16 (event-core extension points), Spring Modulith, module package moves, onion rings inside modules. The 2 frozen mail-core lines in the ArchUnit store stay without an owning story.
- Out: path or payload changes. Both stories are refactors; the specs and the 17.3 contract check do not change.
- In: each story fixes what its move exposes in the classes it touches (C15).

## References

- parent — `_bmad-output/initiative-architecture-hardening/initiative-architecture-hardening.md`, Requirements R18, R19, Constraints, Notes (Decision 2026-11-14)
- prd — `docs/prd/epic-17-architecture-hardening.md`, Stories 17.13 and 17.14
- reference — `docs/architecture/module-map.md`, "EMS modules" (no new endpoints in `EventController`, exceptions in their own module)
- record — `_bmad-output/initiative-architecture-hardening/tickets/epic-ems-walls/architecture-ems-walls/.memlog.md` (Modulith spike, measurements, the 2026-11-14 decision)

## Notes

- Decision: 2026-11-08, owner. Epic 17 exists to make the architecture cleaner, not to document or freeze it (initiative C15). 17.5 now fixes the no-design EMS violations, so this epic's stories start from a smaller store. Left frozen after 17.5: `NewsletterEmailService` and `SlidesOnlineEmailService` (16.8c, 17.15), the EMS PCS clients (17.8), the CUMS clients (17.6, 17.7).
- Decision: 2026-11-08, owner (housekeeping). The PRD now carries the post-17.5 figures, measured with `scripts/architecture/ems-module-*.py` on `eb341346` and the 17.5 working tree: `GlobalExceptionHandler` 16 -> 14 (17.14), mail core -> mail center 11 -> 9 (17.15; `ModeratorReplyAddress` is a mail-core interface implemented in `forwarding`); all violations 28 edges / 99 refs -> 26 / 94. 17.5 adds the frozen rule "mail core uses no mail center class" (2 lines, `MailPipeline` -> `compose`), which 17.15 empties.
- Decision: 2026-11-14, owner. Option 2 of the Winston session: no modularization; the epic keeps 17.13 and 17.14 as targeted refactors. R1 and R2 are the parent's R18 and R19; the epic was refined from those lines alone.
- Decision: 2026-11-14, Sofia. Two stories, no refactor sweep (three entries or fewer). 13 first: it is the larger file and the clearer cost; 14 after it, because 13 may add registration exception handling to the new controller's module that 14 then moves to the base class.
