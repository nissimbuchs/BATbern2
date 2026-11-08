---
type: epic
title: "One way in per resource, one upload contract"
parent: initiative-architecture-hardening
covers: [R14, R15, R16]
sources: []
companions: []
after: []
refined: false
risk: high
---

# One way in per resource, one upload contract

## Description

Enforces the API conventions with breaking changes and no deprecation period: tasks and settings (17.9), then the me/username twins, the speaker path key, registrations and newsletter subscriptions (17.10). Builds the one upload contract, with `company-logo` as its first purpose (17.11).

## Outcome

There is one endpoint to extend per resource, and one upload mechanism. The signal is that the Spectral baseline shrinks by these stories' entries and the FE path contract check passes.

## Done when

- The old operations are gone, and the frontend, Bruno and Playwright call only the new ones.
- Company logo upload and logo requests run on `/uploads/company-logo` in production, and EMS no longer touches `logos`.
- Each story ends with a smaller Spectral baseline than at its start, and the frontend callers it touches sit in `web-frontend/src/services/` (the layer baseline stays empty after 3b).
- Each story reached production through its PR and was verified end to end.

## Boundaries

- Out: the five upload purposes after `company-logo` (backlog stories that 17.11 lists); the §4.2 storage design of `prd-enhanced.md`.
- Waits on epic 3 for the `/internal` move of the profile endpoint (17.6) and for the removal of the logo-request push (17.7).
- In: each story fixes the convention violations of the resources it touches, not only the ones the PRD lists, and moves misplaced code it meets to the module the map assigns (C15). A violation left in the baseline for a touched resource names why.

## References

- parent — `_bmad-output/initiative-architecture-hardening/initiative-architecture-hardening.md`, section Requirements (R14, R15, R16), Constraints, Notes (Decision lines)
- prd — `docs/prd/epic-17-architecture-hardening.md`, Rules for every story, Story order
- reference — `docs/architecture/ADR-002-generic-file-upload-service.md`
- reference — `docs/architecture/ADR-003-meaningful-identifiers-public-apis.md`

## Notes

- Decision: 2026-11-08, owner. Epic 17 exists to make the architecture cleaner, not to document or freeze it (initiative C15). Since 3b (epic 1) moves the frontend logo and picture upload calls (`useFileUpload`, `useCompanyBatchImport`, `useSpeakerBatchImport`, `UserCreateEditModal`) into `services/`, 17.10 and 17.11 change one caller per operation. Apply the rule when the epic is refined.
