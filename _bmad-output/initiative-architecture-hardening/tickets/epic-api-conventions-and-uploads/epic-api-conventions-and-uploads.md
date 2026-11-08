---
type: epic
title: "One way in per resource, one upload contract"
parent: initiative-architecture-hardening
covers: [R14, R15, R16, R22, R23, R24]
sources: []
companions: []
after: []
refined: false
risk: high
---

# One way in per resource, one upload contract

## Description

Enforces the API conventions with breaking changes and no deprecation period: tasks and settings (17.9), one transition idiom for the event lifecycle (9b), then the me/username twins, the speaker path key, registrations and newsletter subscriptions (17.10). Builds the one upload contract, with `company-logo` as its first purpose (17.11). Then the remaining verb, namespace and personal-data paths that no other story owns (10b, API conventions III), and four more upload purposes on the contract: profile-picture, event-photo, teaser-image, session-material (11a to 11d), each removing its old presign, confirm and upload-from-url operations.

## Outcome

There is one endpoint to extend per resource, and one upload mechanism. The signal is that the Spectral baseline shrinks by these stories' entries and the FE path contract check passes; after 11d no baseline line of this epic's resources is left without an owning story or an owner-accepted exception.

## Done when

- The old operations are gone, and the frontend, Bruno and Playwright call only the new ones.
- Company logo upload and logo requests run on `/uploads/company-logo` in production, and EMS no longer touches `logos`.
- Profile pictures, event photos, teaser images and session materials (organizer and speaker portal) upload through `/uploads/{purpose}` in production, and their old presign, confirm and upload-from-url operations are gone.
- The event lifecycle has one transition idiom in the specs, and publishing a phase still reaches the public site.
- No path in a public spec carries an e-mail address, and the 20 baseline lines owned by 10b and 11a to 11d are gone.
- Each story ends with a smaller Spectral baseline than at its start, and the frontend callers it touches sit in `web-frontend/src/services/` (the layer baseline stays empty after 3b).
- Each story reached production through its PR and was verified end to end.

## Boundaries

- Out: the `mail-attachment` upload purpose (no baseline line; a story only after 11a to 11d); the Watch API operations `POST /watch/pair` and `POST /watch/authenticate` (owner-accepted exception 2026-11-08: credential exchange, no resource); the §4.2 storage design of `prd-enhanced.md`.
- Waits on epic 3 for the `/internal` move of the profile endpoint (17.6) and for the removal of the logo-request push (17.7). Entries 10 and 11 name the whole epic until epic 3 is planned; then they point at 3.6 and 3.7.
- In: each story fixes the convention violations of the resources it touches, not only the ones the PRD lists, and moves misplaced code it meets to the module the map assigns (C15). A violation left in the baseline for a touched resource names why.

## References

- parent — `_bmad-output/initiative-architecture-hardening/initiative-architecture-hardening.md`, section Requirements (R14, R15, R16), Constraints, Notes (Decision lines)
- prd — `docs/prd/epic-17-architecture-hardening.md`, Rules for every story, Story order
- reference — `docs/architecture/ADR-002-generic-file-upload-service.md`
- reference — `docs/architecture/ADR-003-meaningful-identifiers-public-apis.md` (Amendment 2026-11, case table)
- reference — `docs/architecture/ADR-013-rest-api-crud-conventions.md` (sections 1 and 2)
- baseline — `web-frontend/scripts/api-lint-baseline.txt` (17.4)

## Notes

- Decision: 2026-11-08, owner. Epic 17 exists to make the architecture cleaner, not to document or freeze it (initiative C15). Since 3b (epic 1) moves the frontend logo and picture upload calls (`useFileUpload`, `useCompanyBatchImport`, `useSpeakerBatchImport`, `UserCreateEditModal`) into `services/`, 17.10 and 17.11 change one caller per operation. Apply the rule when the epic is refined.
- Decision: 2026-11-08, owner. Every line of the 17.4 Spectral baseline names its owning story or an owner-accepted exception; all proposed exception classes are accepted. The 22 lines no story owned: 20 go to new backlog entries in this epic, 2 (`POST /watch/pair`, `POST /watch/authenticate`) are an owner-accepted exception (credential exchange, no resource), no story. 11a profile-picture owns 2 lines, 11b event-photo 1, 11c teaser-image 1, 11d session-material 3, 10b API conventions III 13. Each upload purpose follows 17.11 (it proves the contract); 10b follows 17.10. `DELETE /users/me/additional-emails/{email}` has no baseline line but is in 10b's scope (personal data in a path, ADR-003 amendment). `mail-attachment`, the fifth purpose in the PRD, is not created: it owns no baseline line.
- Decision: 2026-11-08, Sofia. The breakdown's ids keep the 17.x number; follow-ups take a letter (10b, 11a to 11d), as 3b does in epic 1. Entries 9, 10 and 11 are thin entries from the PRD, added so that the follow-ups can name them in `after`; refining the epic revises them in place. Initiative requirements R22 (upload purposes) and R23 (API conventions III) added for them to cover.
- Assumption: the uuid-path-param line of `PUT /notifications/{id}/read` (not counted in the 13) is owned by the accepted exception "own notifications, unnamed top-level aggregate" (ADR-003 amendment). It leaves with its operation in 10b, and the replacement `PATCH /notifications/{id}` takes a line under the same exception. Likewise the additional-e-mail id that replaces `{email}` is the accepted "natural key is personal data, UUID required" case.
- Open question: refining this epic decides whether it closes with a refactor sweep (the slicing rule proposes one for more than three entries); not added with this backlog change.
- Decision: 2026-11-08, owner. New backlog story 9b "One transition idiom for the event lifecycle": ADR-013 §1 (one transition idiom per service) is still violated in EMS by `POST /events/{eventCode}/publish`, `/publish/{phase}`, `/unpublish/{phase}`, `/publish/schedule` and `/workflow/transition`; the story picks one action-sub-resource idiom (ADR-013 row 4) and folds the others in. After 17.9, hitl false, risk medium (production publishing and CDN). Initiative requirement R24 added.
- Decision: 2026-11-08, Sofia, dependency check. Shared code becomes an `after`: 10 waits on 9b (both change `EventController`); 11a and 11b wait on 10b (11a shares `UserController` and `users-api`, 11b shares `EventPhotoController` with the recent-photos move); 11c and 11d wait on 11b, the first EMS purpose, which creates the EMS uploads table they reuse.
- Note (owner 2026-11-08, relayed): the Spectral `no-verb-segment` rule changes to ADR-013's shape (`POST /…/{instance}/{verb}` allowed for any verb). Some baseline lines 9b, 10b and 11a to 11d cite may then disappear as non-violations; their builds reconcile the counts in their verify lines.
- Decision: 2026-11-08, owner. `no-verb-segment` follows ADR-013 §1: `POST /{instance}/{verb}` is no finding. Lines that left the baseline that way (e.g. `enroll-stakeholders`, `POST /events/{c}/publish`, `issue-invitation-credentials`) are not part of any story's "baseline lines gone" check; the operations still change as each story says. Owners per line: `web-frontend/scripts/api-lint-owners.txt`.
