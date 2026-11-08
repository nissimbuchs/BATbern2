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

Gives EMS module boundaries that ArchUnit enforces. 17.5 (epic 1) has already moved the classes that needed no design: the inbound mail chain, the retention schedulers and the dev mail controller into `forwarding` / `mailcenter`, `JwtStompInterceptor` into `security`, `ModeratorReplyAddress` into `mailcenter/render`, and the mail-center exception handlers out of `GlobalExceptionHandler`. Winston designs the target packages and the move order (17.12, accepted by the owner). Then registrations leave `EventController` (17.13), domain exceptions get a base class (17.14), mail core splits from the mail center (17.15), and event core gets extension points (17.16).

## Outcome

The next EMS feature has an obvious module and a build that rejects a wrong dependency. The signal is that the module script shows fewer violating pairs and references than the 2026-11-07 baseline (28 and 100).

## Done when

- The owner has accepted `docs/architecture/ems-modularization.md`, with a defined baseline for the full-layering freeze.
- ArchUnit rules prove each move, the store is smaller after each story than before it, and the module script's before and after numbers are recorded.
- After 17.15 the ArchUnit mail-core exception (`mailcenter.render..`, `mailcenter.variables..` excepted from the mail-center wall by 17.5) is gone, because mail core has its own package, and the `NewsletterEmailService` and `SlidesOnlineEmailService` lines have left the store.
- Registration, mail and workflow behaviour is unchanged (existing tests, Bruno, Playwright).
- Each story reached production through its PR.

## Boundaries

- Out: the remaining module moves (planning, publishing, ai) after 17.12.
- 17.12 is a design task that needs only 17.5. It starts from the store and module-script numbers 17.5 leaves, not from the 2026-11-07 baseline. The other stories wait as the initiative's Decision lines say.
- In: each story fixes what its move exposes inside EMS (C15); a story is not a pure move when a split or redesign removes a violation it meets.

## References

- parent — `_bmad-output/initiative-architecture-hardening/initiative-architecture-hardening.md`, section Requirements (R17, R18, R19, R20, R21), Constraints, Notes (Decision lines)
- prd — `docs/prd/epic-17-architecture-hardening.md`, Rules for every story, Story order
- reference — `docs/plans/ems-modularization-extension-points.md`
- reference — `docs/architecture/module-map.md`

## Notes

- Decision: 2026-11-08, owner. Epic 17 exists to make the architecture cleaner, not to document or freeze it (initiative C15). 17.5 now fixes the no-design EMS violations, so this epic's stories start from a smaller store. Left frozen after 17.5: `NewsletterEmailService` and `SlidesOnlineEmailService` (16.8c, 17.15), the EMS PCS clients (17.8), the CUMS clients (17.6, 17.7).
- Decision: 2026-11-08, owner (housekeeping). The PRD now carries the post-17.5 figures, measured with `scripts/architecture/ems-module-*.py` on `eb341346` and the 17.5 working tree: `GlobalExceptionHandler` 16 -> 14 (17.14), mail core -> mail center 11 -> 9 (17.15; `ModeratorReplyAddress` is a mail-core interface implemented in `forwarding`); all violations 28 edges / 99 refs -> 26 / 94. 17.5 adds the frozen rule "mail core uses no mail center class" (2 lines, `MailPipeline` -> `compose`), which 17.15 empties.
