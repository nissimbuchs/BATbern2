---
title: 'Renaming a company moves its references'
type: 'feature'
ticket: '3'
created: '2026-11-20'
status: 'done'
baseline_revision: '5211712b838cae8e6abaf3f2417ef5fbfdb5f147'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: 'quick'
review_source: 'pinned'
lenses_ran: [quick]
review_loop_iteration: 0
context:
  - '{project-root}/docs/architecture/module-map.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `PATCH /companies/{name}` with a new `name` changes `companies.name` only: users, logo requests, EMS registrations and PCS partners, topics and votes keep the old key and lose their company, and the old key no longer finds anything (side finding 1 of #1251).

**Approach:** A rename moves every reference the merge (story 4) will move, through one reusable `CompanyKeyMover` (contract preview and merge for EMS and PCS with trigger RENAME, then the CUMS rows), renames the company, keeps the old key and display name as an alias (story 2) and clears the caches after commit.

## Boundaries & Constraints

**Always:** Same order and failure semantics as the merge: contract first (preview blockers refuse before any holder moves), then CUMS rows and the company key, in the CUMS transaction; a failure leaves the company under its old key, and renaming it again to the same key finishes the move (holders are idempotent). The mover is the one path story 4 reuses. Peer services only at the wire (ContractServer). Spec first for new error responses.

**Never:** No rename outside `PATCH /companies/{name}`. No mail. No change to S3 keys of existing logos. No event bus.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Rename | `oldkey` with users, a logo request, a logo upload | 200, `name` new; users, logo request, upload target, activity rows on `newkey`; contract preview + merge sent `{source oldkey, target newkey, trigger RENAME}` | none |
| Old key afterwards | after rename | search `oldkey` and get-or-create with old display name return `newkey` | none |
| Holder down | merge answers 5xx | 502, company still `oldkey`, CUMS rows unmoved | `RENAME_INCOMPLETE` |
| Retry | after the 502, same rename again | 200, everything on `newkey` | none |
| Holder blocks | preview blocker / merge 409 | 409, nothing moved in CUMS | `RENAME_BLOCKED` |
| New key is another company's alias | | 409, nothing called | `CompanyValidationException` |
| New key is its own alias (rename back) | | 200, that alias removed, the other key becomes the alias | none |
| Same key, display name only | | no contract call, no alias | none |
| Invalid / taken key | | 400 / 409 as today | |

</frozen-after-approval>

## Code Map

- `.../service/CompanyService.java` `updateCompany` (~l.270) -- today: pattern 400, `existsByName` 409, `company.setName`, `CompanyUpdatedEvent`. `applyLogo`/`applyRequestedLogo`/`logoChanged` also publish `CompanyUpdatedEvent`.
- `.../client/CompanyReferenceContract.java` -- `preview(source, target)`, `merge(MergeCall(source, target, performedBy, Trigger.RENAME))`, `CallFailedException`, `MoveBlockedException`. Test harness `client/CompanyReferenceHolders.bindTo(contract)`, hosts `HOLDER_A`, `HOLDER_B`; examples `clean`, `blocked`, `nothingHeld`, `merged` in `docs/api/internal/cums-internal.openapi.yml`.
- `.../service/UserMergeService.java` -- the model order (lock, preview blockers, merge, local rows, `JdbcTemplate` updates).
- `.../service/CompanyAliasService.java` (story 2) -- `record(companyKey, aliasKey, displayName, reason, performedBy)` refuses a live key, so rename the company first; add `forget(companyId, key)` / owner lookup.
- CUMS rows (V28 is the list): `user_profiles.company_id`, `company_logo_requests.company_name`, `activity_history.entity_id` where `entity_type='COMPANY'`, `company_user_uploads.target_id` where `target_type='COMPANY'` (`CompanyLogoUploadHandler.TARGET_TYPE`).
- Caches: `companySearch`, `userSearch` (`CacheConfig`); clear both after commit.
- `.../controller/CompanyController.java` -- add `@ExceptionHandler`s like `UserMergeController` (409 `RENAME_BLOCKED`, 502 `RENAME_INCOMPLETE`).
- `.../event/Company{Created,Updated,Deleted,Verified}Event.java` -- no listener anywhere (no `@EventListener` in CUMS; ADR-016: no bus); tested only by `EventPublishingIntegrationTest`.
- Docs: `docs/api/companies-api.openapi.yml` patchCompany (+ 409, 502, rename text, events lines), `docs/architecture/04-api-company-management.md` l.131-543, `03-data-architecture.md` l.1317, `06-backend-architecture.md` l.776, `06b-user-lifecycle-sync.md` (beside "Merging two profiles"), changelog.

## Tasks & Acceptance

**Execution:**
- [ ] Refactor commit first: delete the four `Company*Event` classes, their publishing and the company tests of `EventPublishingIntegrationTest`; docs lines -- dead code, own commit.
- [ ] `integration/CompanyRenameIntegrationTest.java` (red first) -- every matrix row; holders via `CompanyReferenceHolders`; request bodies read from the contract call.
- [ ] `service/CompanyKeyMover.java` -- `move(oldKey, newKey, performedBy, Trigger)` (preview blockers → `MoveBlockedException`, merge, CUMS rows; returns holder results + CUMS counts) and `keepAsAlias(...)`; caches after commit.
- [ ] `CompanyRepository` -- `findByNameForUpdate` (pessimistic write).
- [ ] `CompanyService.updateCompany` -- rename through the mover; alias guards.
- [ ] `CompanyController` handlers, spec, regenerate types, docs, changelog.

**Acceptance Criteria:**
- Given a rename, when it succeeds, then no CUMS row and no contract holder keeps the old key, and the old key resolves to the company.
- CUMS full build green; archunit store, ratchet, response-conformance baseline do not grow.

## Implementation Notes

- Implemented directly in this session (dedicated build agent; one heavy Gradle run at a time).
- Refactor committed first on its own: `refactor(company): drop the company domain events nobody consumes` (decision 1). `EventPublishingIntegrationTest` (only company events) deleted; the ratchet baseline lost its line.
- `CompanyKeyMover` (`move`, `keepAsAlias`, `MoveIncompleteException`) is the path story 4 reuses; it requires the caller's transaction (`MANDATORY`). `CompanyService.updateCompany` locks the row (`findByNameForUpdate`), validates, applies fields and logo under the old key, and renames last (`rename(...)`); a failure after the holders moved is wrapped as `MoveIncompleteException` (502).
- `CompanyAliasService` gained `ownerOfKey`, `ownerOfDisplayName`, `forget`.
- Touch rule: the 5 mock-based `updateCompany` unit tests of `CompanyServiceTest` were removed; update runs against PostgreSQL in `CompanyControllerIntegrationTest` and `CompanyRenameIntegrationTest` (13 tests).
- Two review-driven tests (`should_failBeforeTheMove_when_logoUploadUnknown`, `should_acceptUnchangedDisplayName_when_itIsAnotherCompanysAlias`) were written together with the fix; they were not run against the code before it.

## Plan Change Log

## Review Triage Log

Pass 1 (quick lens): 7 findings. high 0, medium 3, low 4, false 0, maybe-false 0.

| # | Finding | Verdict | Route | Evidence / action |
|---|---|---|---|---|
| 1 | A failure after the holders' merge (logo 404/409 in the same PATCH, concurrent key create, alias) answered 409/500 "nothing changed" | medium | patch | True: the move ran before logo and save. Rename is now the last step (fields and logo first, under the old key; the move carries the upload); a failure after the move is `MoveIncompleteException` → 502 `RENAME_INCOMPLETE`. Test: unknown logo upload fails with no holder called. |
| 2 | Display-name alias guard refuses an unchanged display name | medium | patch | True (batch import sends the full payload). Guard only when the display name changes. Test added. |
| 3 | Docs promise a retry always finishes; a key taken meanwhile strands holder rows | medium | patch | True. Spec 502 and 06b now state the case (rename to another key, key taken meanwhile → rows to move by hand). |
| 4 | PCS clears its caches at its own commit, before CUMS commits; a read in that window re-caches | low | reject | True in principle: `usersByCompanyRoleCache` could cache an empty list for the new key for up to 10 min (PCS TTL); a missing company is not cached (exception). Window is the EMS call plus the CUMS commit; a fix needs a new contract operation after commit. Rejected as negligible, recorded here. |
| 5 | Matrix rows "merge 409" and "5xx answer" not tested as stated | low | patch | Tests added: merge 409 → 409 `RENAME_BLOCKED`, next holder not called; 5xx → 502. |
| 6 | patchCompany 409 lost the `logoUploadNotReady` example | low | patch | Restored. |
| 7 | Doubled blank lines left in `CompanyService` | low | patch | Collapsed. |

## Design Notes

**Architecture note.** Modules: CUMS company service. New dependency: `CompanyService` → `CompanyReferenceContract` (CUMS's own contract, ADR-016 d4). Endpoints: none new; `patchCompany` gets 409/502 (nearest: `mergeUsers` errors). Reused: the contract, `CompanyAliasService`, the `UserMergeService` order. Deviations: none.

**Decisions taken (no human available, sensible default):**
1. All four `Company*Event` classes are deleted, not only `CompanyUpdatedEvent`: none has a consumer and the reason is the same (audit the class). User events are out of scope.
2. `activity_history` COMPANY rows and company-logo upload targets move too (V28 moved the same set).
3. A rename to a different key after a 502 is allowed; finishing needs the same key (documented in the spec).
4. Errors: 409 `RENAME_BLOCKED`, 502 `RENAME_INCOMPLETE`, as `UserMergeController`.

**Store sizes at start:** CUMS archunit 0, ratchet 529 (`be-transactional-it` counts `EventPublishingIntegrationTest`).

**Store sizes at end:** CUMS archunit 0; ratchet baseline one line smaller (`EventPublishingIntegrationTest` gone); response-conformance baseline empty.

## Verification

**Commands:**
- `./gradlew :services:company-user-management-service:test --tests '*CompanyRename*' --tests '*CompanyAlias*' --tests '*EventPublishing*' --tests '*CompanyController*' --max-workers=4` -- green
- `./gradlew :services:company-user-management-service:build --max-workers=4` -- green
- `cd web-frontend && npm run generate:api-types && npm run type-check`; `make check-test-ratchet` -- clean
