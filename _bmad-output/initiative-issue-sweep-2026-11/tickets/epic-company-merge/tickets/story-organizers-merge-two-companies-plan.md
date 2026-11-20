---
title: 'Organizers merge two companies'
type: 'feature'
ticket: '4'
created: '2026-11-20'
status: 'built'
baseline_revision: '70bb43d3c53befbcb508ef5a9fec92463a001d1f'
route: 'full'
route_source: 'auto'
risk: 'high'
review: 'quick'
review_source: 'pinned'
lenses_ran: [quick]
review_loop_iteration: 0
context:
  - '{project-root}/docs/architecture/module-map.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Duplicate companies ("Unic" / "Unic AG") split users, registrations and partner references; organizers have no way to fold one into the other (#1251).

**Approach:** `GET /companies/{target}/merge-preview?source=` and `POST /companies/{target}/merge {source}` (ORGANIZER), served by a CUMS `CompanyMergeService` that mirrors `UserMergeService` and moves references through the one path a rename uses (`CompanyKeyMover`), then fills empty target fields, keeps the source key and display name as aliases, deletes the source and writes `company_merge_log`.

## Boundaries & Constraints

**Always:** Order: lock both (key order), validate, contract preview (blocker refuses before any holder moves), contract merge (trigger MERGE), CUMS rows, fill target, re-point the source's aliases, delete source, alias source key + display name, log; all CUMS steps in one transaction; caches cleared after commit. A failure after the holders moved leaves the source (502), and the same merge again finishes it. Target display name stays. Spec first; peer services only at the wire (ContractServer).

**Never:** No mail. No Bruno request that merges. No change to S3 keys. No merge of two partners. No Swiss UID (column dropped by V22, #1119).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Preview | target, source with users, logo request | 200: both summaries (incl. user count, logo), `moves` = holders + CUMS counts, `conflicts`, `blockers`, `fills`, `canMerge`, `reasons`, `canSwap` | none |
| Merge | as above, target without website/logo | 200 `{targetName, sourceName, moved, removed, filled}`; users, logo requests, uploads, activity on target; empty target fields filled, set ones kept; source gone; aliases for source key + display name; one `company_merge_log` row | none |
| Source had aliases | source carries alias `x` | `x` now leads to target | none |
| Both partners | holder preview blocker `MERGE_BLOCKED_BOTH_PARTNERS` | preview: `canMerge` false, reason `BOTH_PARTNERS`; merge 409 `MERGE_BLOCKED_BOTH_PARTNERS`, no holder merge, nothing moved | |
| Holder refuses at merge (race) | merge 409 | 409 `MERGE_BLOCKED` | |
| Same company | source = target | preview `canMerge` false `SAME_COMPANY`; merge 400 | |
| Target key does not fit `user_profiles.company_id` | target key > 12 / not slug shape, source fits | preview reason `TARGET_KEY_TOO_LONG`, `canSwap` true; merge 409 `MERGE_NOT_ALLOWED`, details reasons + canSwap | |
| Holder down | merge 5xx / no answer | 502 `MERGE_INCOMPLETE`, source and CUMS rows unchanged | retry merges all |
| Unknown company | either key | 404 | |
| Auth | anonymous / non-organizer | 401 / 403 | |

</frozen-after-approval>

## Code Map

- `service/CompanyKeyMover.java` -- reuse `move(...)` (Trigger.MERGE). Extend: CUMS row ids via `UPDATE ... RETURNING` (`Moved.cumsKeys`), `countCums(key)` for the preview (one table list), blockers carried on `MoveBlockedException`.
- `client/CompanyReferenceContract.java` -- `MoveBlockedException` gains `blockers()` (empty on a merge-time 409).
- `service/UserMergeService.java`, `controller/UserMergeController.java` -- model for names, order, records `Preview`/`Result`, `MergeNotAllowedException`, handler style.
- `service/CompanyAliasService.java` -- `record(...)` (refuses a live key: after delete); add `repoint(fromId, toId)`.
- `repository/CompanyRepository.java` -- `findByNameForUpdate`; add user count query (`UserRepository` / jdbc `count`).
- `domain/Company.java` -- fields website, industry, description, logoUrl/logoS3Key/logoFileId, createdAt/By.
- New: `service/CompanyMergeService.java`, `controller/CompanyMergeController.java` (implements generated `CompanyMergeApi`, tag "Company Merge"), `db/migration/V37__create_company_merge_log.sql`.
- Spec: `docs/api/companies-api.openapi.yml` (new tag, 2 paths, schemas `CompanyMergeSide`, `CompanyMergePreview`, `CompanyMergeRequest`, `CompanyMergeResult`). Responses are validated by `ResponseConformance` (baseline must not grow).
- Tests: `integration/CompanyRenameIntegrationTest.java` is the harness model (`CompanyReferenceHolders`, examples `clean`, `blocked`, `nothingHeld`, `merged`).
- Docs: `docs/architecture/06b-user-lifecycle-sync.md` new "Merging two companies" after "Renaming a company"; `06-backend-architecture.md`, `03-data-architecture.md` (table), `docs/user-guide/appendix/changelog.md`.

## Tasks & Acceptance

**Execution:**
- [x] `docs/api/companies-api.openapi.yml` -- tag, paths, schemas, examples, 400/409/502 -- spec first.
- [x] `integration/CompanyMergeIntegrationTest.java` -- red first: every matrix row.
- [x] `V37__create_company_merge_log.sql` -- id, source_key, target_key, performed_by, merged_at, moved_keys jsonb, removed_keys jsonb, filled_fields jsonb, source_snapshot jsonb.
- [x] `CompanyKeyMover`, `CompanyReferenceContract`, `CompanyAliasService` -- extensions above; rename keeps working.
- [x] `CompanyMergeService`, `CompanyMergeController` -- as matrix.
- [x] Regenerate frontend types; docs; changelog.

**Acceptance Criteria:**
- Given a completed merge, then no CUMS row and no holder keeps the source key, the source key and display name resolve to the target, and the log holds every moved id and the source snapshot.
- CUMS full build green incl. checkstyle; archunit store, ratchet and response-conformance baseline do not grow; `EndpointAuthorizationGuardTest` green.

## Implementation Notes

- Built by the implementation subagent, review fixes by the same agent; verification by the build session.
- New beyond the plan: `CompanyMergeLog` (COMPLETED in the merge transaction, INCOMPLETE in its own); `HttpCompanyReferenceContract.merge` calls the holders one by one so a failure carries the answers of the holders before it (`CallFailedException.completed()`).
- Known gap, accepted: a later holder refusing the merge call itself (409 after a clean preview) leaves the rows of earlier holders unlogged; PCS, the only holder that refuses, is called first.
- Not covered by a test: two opposite merges at once (lock in key order).

**Store sizes at end:** CUMS archunit store 0; ratchet baseline unchanged (528 lines, check clean); response-conformance baseline empty.

- Spec first: tag Company Merge, `getCompanyMergePreview`, `mergeCompany`, schemas as planned. `GET .../merge-preview` trips the api lint rule `no-verb-segment` like `/users/{username}/merge-preview`; added to the existing owner exception group X8 ("dry run without side effect") in `web-frontend/scripts/api-lint-owners.txt`, baseline +1 line (state it in the PR).
- `CompanyKeyMover`: one list `CUMS_HOLDERS` (update with `RETURNING id`, count); `Moved` gained `cumsKeys` and `cumsClosed`; `countCums(key)`, `countCumsConflicts(old, new)`. The preview blocker exception carries the blockers.
- `CompanyAliasService.repoint(fromId, toId)`; `UserRepository.countByCompanyId`.
- Preview 502 when a holder does not answer: `MERGE_PREVIEW_INCOMPLETE` (merge: `MERGE_INCOMPLETE`).
- 15 ITs in `CompanyMergeIntegrationTest` (every matrix row, plus repeat-404, search/get-or-create after merge, set fields kept, logo request collision).

## Plan Change Log

- Review pass 1, finding 1 (undo record lost on a retry). Strictly a plan gap (bad_plan): the plan named one log row per merge. Amended without a revert, because the fix adds to the log path only and every other part of the code is unaffected: an attempt that fails after a holder moved writes an `INCOMPLETE` `company_merge_log` row in its own transaction; the completing run writes `COMPLETED`. KEEP: the whole implementation as built (service, controller, mover extensions, logo request closing, spec, tests).
- Implementation deviations accepted (coordinator, 2026-11-20): a person's open logo request at both companies closes the source's (DECLINED, row kept, id logged, preview `conflicts`); 502 `MERGE_PREVIEW_INCOMPLETE` when a holder does not answer the preview; `merge-preview` listed under owner exception X8 in `web-frontend/scripts/api-lint-owners.txt` (to be named in the PR). The 409 `SOURCE_KEY_TOO_LONG` guard was removed again by review finding 2.

- 2026-11-20, found while building: `uq_company_logo_requests_open` (one open logo request per person and company) makes the CUMS move fail when the same person has an open request at source and target; the merge would 502 on every retry. The mover now closes the source's colliding open request (DECLINED, reason names the target, row kept and moved) first; preview shows it as `conflicts.company_logo_requests`, the merge as `removed["company_logo_requests.status"]` and in `removed_keys`. Never fires on a rename (the new key holds no rows).
- 2026-11-20: added reason `SOURCE_KEY_TOO_LONG` (409 `MERGE_NOT_ALLOWED`): a source key outside the key shape cannot become an alias (`company_aliases.alias_key` CHECK), which would otherwise fail after the holders moved. Since V28 no such key should exist; it is a guard. `canSwap` = different companies and the source key has the key shape.

## Review Triage Log

Pass 1 (quick lens): 5 findings. high 0, medium 2, low 3, false 0, maybe-false 0.

| # | Finding | Verdict | Route | Evidence / action |
|---|---|---|---|---|
| 1 | A retry after a 502 logs no ids of what the first attempt moved at the holders; holders keep no log, so the undo record is incomplete | medium | patch (amended, see change log) | True: CUMS rolls back the attempt, idempotent holders answer empty keys on the retry. Fix: holder answers collected one by one, attached to `CallFailedException`; an attempt that fails after a holder moved writes an `INCOMPLETE` log row in its own transaction (`outcome` column, V37 unshipped). |
| 2 | `canSwap` true for `TARGET_KEY_TOO_LONG`, but the swapped merge is refused with `SOURCE_KEY_TOO_LONG` | medium | patch | True: the swapped source is the non-key target. `SOURCE_KEY_TOO_LONG` removed; a non-key source merges without a key alias. Test runs the swapped merge. |
| 3 | Snapshot keeps only alias keys | low | patch | True; direct correction: full alias rows and `updatedAt` in the snapshot. |
| 4 | Spec says SAME_COMPANY preview carries CUMS counts; code returns empty | low | patch | True; text corrected. |
| 5 | Result `removed` counts closed logo requests that were kept and moved | low | patch | True; left out of the response, documented; log unchanged. |

## Design Notes

**Architecture note.** Modules: CUMS company. New dependencies: none (the merge uses CUMS's own contract through `CompanyKeyMover`). Endpoints: `GET /companies/{name}/merge-preview`, `POST /companies/{name}/merge`; nearest `/users/{username}/merge-preview` and `/merge` (other resource, so mirrored, not extended); `PATCH /companies/{name}` cannot carry a merge (it edits one company). Reused: `CompanyKeyMover`, `CompanyAliasService`, contract harness, `UserMergeService` shape. Deviations: none.

**Decisions taken (no human available, sensible default):**
1. Swiss UID is not filled: the column was dropped (V22, #1119).
2. Locks in key order (not target-then-source) so two opposite merges cannot deadlock.
3. Same company: preview 200 with reason `SAME_COMPANY`; merge 400 `MERGE_NOT_ALLOWED`.
4. Error codes: 409 `MERGE_NOT_ALLOWED` (reasons), 409 `MERGE_BLOCKED_BOTH_PARTNERS`, 409 `MERGE_BLOCKED`, 502 `MERGE_INCOMPLETE` (as `RENAME_INCOMPLETE`).
5. The source's logo upload rows move to the target key even when the target keeps its own logo (the mover moves all; the file stays reachable for undo); `logo*` fields copied only when the target has none.
6. A completed merge repeated answers 404 (source gone), as the profile merge.
7. Partner flag per side is not in the preview: only the source's shows (`moves[partners.company_name]`); the both-partners refusal is the reason. Story 5 decides how per-company partner and registration counts are read.

**Store sizes at start:** CUMS archunit store 0 violations; ratchet baseline 528 lines; response-conformance baseline empty.

## Verification

**Commands:**
- `./gradlew :services:company-user-management-service:test --tests '*CompanyMerge*' --tests '*CompanyRename*' --tests '*CompanyAlias*' --tests '*EndpointAuthorizationGuard*' --max-workers=4` -- green
- `./gradlew :services:company-user-management-service:build --max-workers=4` -- green (checkstyle, all tests)
- `cd web-frontend && npm run generate:api-types && npm run type-check`; `make check-test-ratchet` -- clean
