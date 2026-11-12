---
title: 'CUMS owns the user-reference merge contract, EMS and PCS implement it'
type: 'refactor'
ticket: '7b'
created: '2026-11-11'
status: 'done'
baseline_revision: 'c295ff4242945bf5aa5cc22c9c783b84e5712661'
route: 'full'
route_source: 'auto'
risk: 'high'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/initiative-architecture-hardening/tickets/epic-one-service-direction/tickets/story-ems-pulls-mail-center-requests-from-cums-plan.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

Approved by the owner 2026-11-11 ("B for all": this plan is already the upstream-owned contract pattern).

**Problem:** CUMS calls EMS by name (`EventManagementUserMergeClient`: merge preview, merge, primary-email), the last CUMS→EMS edge, and PCS is not part of a merge at all: after a merge, PCS rows (topic suggestions, partner notes, partner meetings) still name a username that no longer exists.

**Approach:** CUMS specifies `/internal/user-references/{preview,merge,primary-email}` in `cums-internal.openapi.yml` as its own contract. The holders are listed in CUMS configuration and called through one CUMS client on `S2sHeaders` that names no service. EMS moves its `usermerge` endpoints onto the contract; PCS implements it for its three username columns (the ticket's fourth, `partner_contacts.username`, is gone: PCS V6 dropped the table; production has the three). The EMS-named client pair goes. Before PCS joins, a read-only production query counts PCS rows that already name a former merge source.

## Boundaries & Constraints

**Always:** Branch `feature/17-7b-user-reference-contract` (from 3.7), commit locally, no push or PR without owner go. Local dev only for verification; the production query is read-only, counts only, and runs only with the owner's go. One heavy job at a time on rack. Spec first. Tests on real PostgreSQL; holders at the wire via `ContractServer`; no own-code mocks in touched tests. New migrations only. Public `users-api` shape unchanged (holder counts join the flat `table.column` maps). New i18n labels in all 10 locales.

**Never:** No change to `mailcenter.notify-instead-of-forward`, no real mail. No production data change in this story without a separate owner decision. No new upstream→downstream call besides the contract.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Preview | two profiles, rows in EMS and PCS | `moves` lists EMS and PCS `table.column` counts | — |
| Merge | as above | every holder rewrites the source username; CUMS deletes the source | — |
| Holder refuses | EMS preview has a blocker | merge refused (409 `BLOCKED`) before any holder merges | — |
| Holder down | PCS unreachable | merge fails (502 as today), the profile step does not run; a retry is idempotent | WARN |
| Primary email | make-primary | EMS moves upcoming registrations at once (synchronous); PCS answers with nothing to move | failure as today |

</frozen-after-approval>

## Code Map

- CUMS `client/EventManagementUserMergeClient.java`, `HttpEventManagementUserMergeClient.java` (+ `EventManagementContract`, `HttpEventManagementUserMergeClientContractTest`) -- replace by `client/UserReferenceContract.java` (+ `HttpUserReferenceContract`) on shared-kernel `ContractCalls` (built in the 7 rework): one call per configured holder, same timeouts, 409 → `MergeBlockedException`, else `MergeCallFailedException`.
- CUMS `service/UserMergeService.java:56-141` -- preview sums all holders' maps; merge first previews all holders (any blocker → refuse), then merges each. `service/PrimaryEmailService.java:73` -- calls every holder.
- CUMS `application.yml:107` -- add `batbern.contracts.user-references.implementers` (EMS and PCS base URLs).
- CUMS `archunit_store/e75ed5e2-…` -- 2 lines, must end empty.
- EMS `usermerge/InternalUserMergeController.java`, `UserMergeService.java` -- paths become `/internal/user-references/*`; logic unchanged; `InternalUserMergeIntegrationTest` follows.
- PCS new `usermerge/` (controller + service, JDBC): `topic_suggestions.suggested_by`, `partner_notes.author_username`, `partner_meetings.created_by`; no unique constraint on them, so no conflicts or blockers; PCS writes no merge log (EMS's `user_merge_log` is the record).
- `docs/api/internal/cums-internal.openapi.yml` (contract), `ems-internal.openapi.yml` (3 operations out); EMS and PCS `S2sProviderContractIntegrationTest` validate against the cums contract.
- Frontend `MergeProfileDialog.tsx:145` labels by table: add `merge.tables.{topic_suggestions,partner_notes,partner_meetings}` in 10 locales.

## Tasks & Acceptance

**Execution:**
- [x] Specs: contract in `cums-internal`, removal in `ems-internal`; regenerate types if touched.
- [x] CUMS: `UserReferenceContract` + config; `UserMergeService`, `PrimaryEmailService`; delete the EMS client pair; `UserMergeIntegrationTest` with two `ContractServer` holders, one refusing; contract test per operation.
- [x] EMS: move the three endpoints; provider contract test entries.
- [x] PCS: `usermerge` controller + service + integration test (preview, merge, idempotent rerun); provider contract test entries; `EndpointAuthorizationGuardTest` stays green.
- [x] Frontend: 4 labels × 10 locales; `MergeProfileDialog.test.tsx` covers a PCS key.
- [x] Docs: ADR-016 decision 5 (done), `module-map.md` (one upstream→downstream call: `/internal/user-references/*`; CUMS store count gone), `06-backend-architecture.md`, `06b-user-lifecycle-sync.md`, changelog, doc-drift mappings.

**Acceptance Criteria:**
- Given local dev and two profiles with EMS and PCS rows, when an organizer previews and merges, then the preview lists EMS and PCS counts and PCS rows carry the target username afterwards.
- Given local dev, when a primary email changes, then an upcoming registration moves at once.
- Given the CUMS, EMS and PCS suites, then they pass and the CUMS archunit store is empty.

Pass 1 (quick lens), 2026-11-11: 0 high, 2 medium, 6 low, 0 false.

| # | Finding | Verdict | Route | Evidence / action |
|---|---------|---------|-------|-------------------|
| 1 | Primary-email stops at the first failing holder, later holders never told | low | patch | Verified (`postForAnswers` throws at first failure). Fix: call every holder, collect failures. |
| 2 | AC 2 not run end to end on local dev | low | reject | Verified, but make-primary moves a login in the shared Cognito pool, which serves production; EMS's endpoint was run directly and `PrimaryEmailIntegrationTest` covers the CUMS path. Stated in the morning summary. |
| 3 | `check:api-types` result not recorded | low | (verification) | Run by the orchestrator after the patch commit. |
| 4 | A retry after a later holder failed writes a second `user_merge_log` row; PCS keeps no undo record | medium | patch + defer | Verified (EMS insert not idempotent). Patch: skip an empty repeat. PCS undo record deferred (deferred-work entry). |
| 5 | users-api profile-claims/confirm text names EMS and lacks the 502; `ProfileMergeRequestService` comment names EMS | low | patch | Verified at the cited lines. |
| 6 | An empty holder list lets a merge delete the source and orphan rows | medium | patch | Verified: only a WARN at construction. Fix: refuse the merge with 502 when no holder is configured. |
| 7 | Doc-drift mappings miss the contract caller and `PrimaryEmailService` | low | patch | Verified. |
| 8 | CUMS has no test that the retry after a holder failure completes | low | patch | Verified. Add the repeat to the holder-down test. |

## Design Notes

**Architecture note.** Modules: CUMS `client`, `service`, config; EMS `usermerge`; PCS new `usermerge`; web-frontend locales. New dependency: none. New endpoints: `/internal/user-references/{preview,merge,primary-email}` on EMS (renamed from `/internal/users/merge-preview|merge|primary-email`, which cannot stay: their spec belongs to EMS, the contract to CUMS) and on PCS (new). Reused: `S2sHeaders`, `ContractServer`, provider contract test, the EMS merge logic. Deviation: none; ADR-016 decision 5 as amended 2026-11-11.

Merge order: preview all holders, refuse on any blocker, then merge each. A holder failing mid-way leaves earlier holders merged and the source profile in place; every holder merge is idempotent, so the retry completes it. Same guarantee as today with one holder.

**Production query (owner go 2026-11-11, run by agent, read-only transaction, counts only).** PCS rows naming a `user_merge_log.source_username` (267 merges, 2026-11-04 to 2026-11-08): `topic_suggestions.suggested_by` 0 (of 43), `partner_notes.author_username` 0 (of 2), `partner_meetings.created_by` 0 (of 2). No repair needed. `partner_contacts` does not exist in production (PCS V6).

**Epic 17 rule (C15).** Start (`820c6803`): CUMS archunit store 2 lines (end 0); `ServiceDirectionTest` holds no other CUMS line. Also fixed: PCS rows orphaned by merges.

## Verification

**Commands:**
- `./gradlew :services:company-user-management-service:test`, then EMS, then PCS (`--max-workers=4`, one at a time) -- green.
- `cd web-frontend && npx vitest run src/components/organizer/UserManagement --maxWorkers=4 && npm run type-check && npm run check:api-types` -- green.
- `make check-test-ratchet` -- no RAISED line.

**Manual checks:**
- Local dev: merge preview and merge of two test profiles with a PCS partner note; make-primary on a profile with an upcoming registration.

## Implementation Notes

Built 2026-11-11 on `feature/17-7b-user-reference-contract`, committed locally, no push, no PR. Not reviewed yet.

- **Contract** in `cums-internal.openapi.yml`, tagged `contract`: `GET /internal/user-references/preview` (`UserReferencePreview`), `POST .../merge` (`UserReferenceMerge` -> `UserReferenceMergeResult`, 409 `UserReferenceError` MERGE_BLOCKED), `POST .../primary-email` (`UserPrimaryEmailChange` -> `{updated}`), each with a `nothingHeld` example. The three operations and their six schemas are gone from `ems-internal`; its description points at the cums spec. `.spectral.yaml` comment example updated.
- **shared-kernel.** `ContractCalls.postForAnswers(path, body, type, uriVariables...)` returns every implementer's answer (merge needs per-holder counts); `post` delegates to it. `ContractServer.Expectation.atHost(host)` so one `RestTemplate` with two implementers can be answered per holder. Tests: `ContractCallsTest` +1, `ContractServerTest` +1.
- **CUMS.** `client/UserReferenceContract` + `HttpUserReferenceContract` (2 s / 30 s as before; 409 -> `MergeBlockedException`, other failures -> `MergeCallFailedException`). `UserMergeService`: preview sums all holders (`sum`, counts added per key, blockers concatenated); merge previews all holders first and refuses with `MergeNotAllowedException(BLOCKED)` (409 `MERGE_NOT_ALLOWED`, reason `BLOCKED`, as the users-api text already said) before any holder merges, then merges each and sums `moved`/`removed`. Before, a blocked merge got 409 `MERGE_BLOCKED` from EMS; a holder that refuses between preview and merge still gets that. `PrimaryEmailService` tells every holder (best effort, WARN as before). 502 message no longer says "Event data". Config `batbern.contracts.user-references.implementers: ${EVENT_MANAGEMENT_SERVICE_URL...},${PARTNER_COORDINATION_SERVICE_URL...}`; `event-management-service.base-url` removed (no other user); `docker-compose.yml` passes the PCS URL to CUMS. Production needs no infra change: `http://partner-coordination:8080` is the Service Connect name, ingress 8080 from the VPC CIDR.
- **CUMS tests.** EMS-named client pair, `EventManagementContract` and its contract test deleted; `HttpUserReferenceContractTest` (9) and `UserReferenceHolders` helper. `UserMergeIntegrationTest` and `ProfileMergeRequestIntegrationTest` lost their `@MockitoBean` (ratchet `be-mock-s2s-client` -2, baseline updated) and answer two holders at the wire; new test: a holder blocker refuses before any merge; holder down -> 502, source stays. `AccountMergeIntegrationTest`, `PrimaryEmailIntegrationTest` on the new caller. Provider example rows for the three operations. Archunit store `e75ed5e2-...` now empty (0 bytes).
- **EMS.** `InternalUserMergeController` paths -> `/internal/user-references/*`, logic unchanged; `InternalUserMergeIntegrationTest` follows. Provider test: the user-reference operations run against the cums spec with and without a forwarded JWT, requests validated against the spec first; the 409 test moved to the contract section.
- **PCS.** New `usermerge/UserReferenceService` (JDBC, three columns, no conflicts or blockers, no log, idempotent) and `InternalUserReferenceController` (primary-email answers `updated: 0`; `/internal/**` is exempt in `EndpointAuthorizationGuardTest` like the RSVP endpoint). `UserReferenceIntegrationTest` (4: preview, merge + idempotent rerun, same-username 400, primary email). Provider test against the cums spec, with and without JWT.
- **users-api.** Merge and preview texts name all holders; merge documents its 502 `MERGE_EVENT_SERVICE_FAILED` (always answered, never declared). Types regenerated.
- **Frontend.** `merge.tables.{topic_suggestions,partner_notes,partner_meetings}` in 10 locales (three keys: the ticket's fourth, `partner_contacts`, no longer exists). `MergeProfileDialog.test.tsx` checks a PCS key's label.
- **Docs.** `module-map.md` (contract paths, CUMS clients 0), `06-backend-architecture.md` (internal table, contracts paragraph, merge section and sequence diagram), `06b` (order, PCS moves, primary email, 409 code, production count), `01-system-overview.md` diagram label, ADR-016 cost line (merge 502, primary email WARN), changelog, doc-drift mappings for EMS and PCS `usermerge` and CUMS `UserMergeService`. Not touched: `docs/prd/epic-17-architecture-hardening.md`.
- **Verification (2026-11-11).** shared-kernel 652, CUMS 981, EMS 2774, PCS 287 tests, 0 failures; checkstyle main+test of the four modules; `lint:api-internal`, `lint:api`, `check:i18n-keys`, `check:s2s-client-urls`, `check:api-paths`, ratchet (after `--update`, 2 lines fell), `type-check`, UserManagement Vitest (16 files, 134) green. `check:api-types` run after the commit.
- **Local dev, 21:38 UTC.** CUMS, EMS, PCS restarted on the new build; old EMS path answers 404. Two login-less profiles `mergeseven.test` / `.test.2` with an upcoming BATbern61 registration, a partner note and a topic suggestion under the source (SQL). Preview through the gateway as `batbern.organizer`: `registrations.attendee_username` 1, `topic_suggestions.suggested_by` 1, `partner_notes.author_username` 1, canMerge. Merge: same three moved, source deleted, all three rows on the target, `user_merge_log` row ORGANIZER. PCS stopped: merge of a third profile -> 502, source kept (the preview already failed); PCS restarted, same merge succeeded. Primary email: `POST /internal/user-references/primary-email` on EMS moved the BATbern61 registration at once (`updated` 1), PCS answered 0. Make-primary itself not run on local dev: it needs a logged-in profile and would move a login in the shared Cognito pool; covered by `PrimaryEmailIntegrationTest`. Test rows removed afterwards.

- **Review fixes (2026-11-11).** `ContractCalls.postToEvery` (every implementer called, each failure logged, one `ContractCallFailedException` at the end) used for primary-email, so a down EMS no longer keeps PCS from being told; EMS writes no second `user_merge_log` row on a repeat that moves nothing; preview and merge fail (502) when no holder is configured; `UserMergeIntegrationTest` repeats the failed merge with both holders up and it completes; users-api confirm text names the holders and declares its 502 (types regenerated); `ProfileMergeRequestService` comment; doc-drift mappings for the CUMS contract caller and `PrimaryEmailService`. Tests run: `ContractCallsTest` (12), `HttpUserReferenceContractTest` (11), `UserMergeIntegrationTest` (16), `PrimaryEmailIntegrationTest` (6), EMS `InternalUserMergeIntegrationTest` (8), checkstyle of the three modules; all green.
