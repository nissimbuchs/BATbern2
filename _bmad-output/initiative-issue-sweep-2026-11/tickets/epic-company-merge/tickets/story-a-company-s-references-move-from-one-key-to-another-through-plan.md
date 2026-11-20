---
title: "A company's references move from one key to another through a CUMS contract"
type: 'feature'
ticket: '1'
created: '2026-11-20'
status: 'done'
baseline_revision: '774ec3b8ae315fa79bf4f71d4fc0c6e84399d4e4'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: 'quick'
review_source: 'pinned'
lenses_ran: [quick]
review_loop_iteration: 0
context:
  - '{project-root}/docs/architecture/module-map.md'
  - '{project-root}/docs/architecture/ADR-016-service-directions-and-service-to-service-calls.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A company merge (story 4) and a rename (story 3) must move every reference to a company key held by EMS and PCS, but CUMS may reach them only through an `/internal` contract it owns (ADR-016 decisions 4, 5), and none exists for companies.

**Approach:** CUMS specifies a `company-references` contract (`GET /internal/company-references/preview`, `POST /internal/company-references/merge`) in its internal spec, calls it through `ContractCalls` from a new `CompanyReferenceContract` / `HttpCompanyReferenceContract`, and EMS and PCS implement it, modelled exactly on `user-references`.

## Boundaries & Constraints

**Always:** Spec first. Implementers only in `batbern.contracts.company-references.implementers`; CUMS names no downstream in code. Each provider moves its rows in one transaction, is idempotent, and clears its caches after commit. Integration tests on Testcontainers PostgreSQL; mock only at the wire (ContractServer). ADR-016 decision 5 and module-map list the contract in the same commit.

**Never:** No CUMS public endpoint, no CUMS-side orchestration (stories 3, 4). `speaker_pool.company` (free text) is not touched. No Flyway migration (none needed). No event bus, no polling.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Preview | rows under source | `moves` per `table.column`, `conflicts` per table, `blockers` | changes nothing |
| Merge | rows under source | rows now under target; `moved`, `removed` counts and `movedKeys`/`removedKeys` | one transaction |
| Repeat merge | already merged | all counts 0 | 200 |
| Vote collision (PCS) | source and target voted the same topic | target's vote kept, source's deleted and logged, counted in `removed` | 200 |
| Source partner only (PCS) | partner row under source | partner's `company_name` becomes target | 200 |
| Both partners (PCS) | partner rows under both | preview blocker `MERGE_BLOCKED_BOTH_PARTNERS`; merge moves nothing | 409 `{error: MERGE_BLOCKED_BOTH_PARTNERS}` |
| Invalid | source = target, missing field, target not `^[a-zA-Z0-9]{1,12}$` | nothing moves | 400 |
| No implementer (CUMS) | empty config | preview/merge fail | `CallFailedException` |

</frozen-after-approval>

## Code Map

- `docs/api/internal/cums-internal.openapi.yml` -- add the two `contract`-tagged paths after `/internal/user-references/primary-email`; schemas `CompanyReferencePreview` (reuse `UserReferenceCounts`), `CompanyReferenceMerge`, `CompanyReferenceMergeResult`, `CompanyReferenceError` (enum `MERGE_BLOCKED_BOTH_PARTNERS`, `INVALID_MERGE`); update info description's contract list.
- `services/company-user-management-service/.../client/UserReferenceContract.java`, `HttpUserReferenceContract.java` -- the model; copy structure (package-private test ctor with holder list, `restTemplate()` for the harness, `requireHolders`, 409→Blocked, other→CallFailed).
- `.../src/main/resources/application.yml` (CUMS) l.107-114 -- add `company-references` implementers (EMS, PCS URLs); `src/test/resources/application-test.yml` l.52-58 -- two `.invalid` hosts.
- `.../test/.../client/UserReferenceHolders.java`, `HttpUserReferenceContractTest.java` -- model for `CompanyReferenceHolders` and the call-shape unit test.
- CUMS `contract/S2sProviderContractIntegrationTest` -- add the two paths to the example-schema `@CsvSource`.
- EMS `usermerge/InternalUserMergeController.java`, `UserMergeService.java` -- model for EMS provider; `config/CacheConfig.clearAfterCommit` for eviction. Column: `registrations.attendee_company_id` VARCHAR(100) (V35, V150 confirm it is EMS's only company key).
- PCS `usermerge/InternalUserReferenceController.java`, `UserReferenceService.java` -- model. Columns (V2, V4, V10, V11): `partners.company_name` VARCHAR(12) UNIQUE, `topic_suggestions.company_name` (nullable), `topic_votes.company_name` with PK `(topic_id, company_name)`. PCS `CacheConfig` caches `partners`, `companyApiCache`, `usersByCompanyRoleCache`, `partnerAttendanceCache` are keyed by company.
- EMS / PCS `contract/S2sProviderContractIntegrationTest` -- add a company-references section validating requests and answers against the CUMS spec.
- `scripts/architecture/ems-modules.json` -- package `usermerge` maps to module `usermerge` (L6 edge); no change.
- `.github/doc-drift-mappings.yml` -- add the new CUMS contract files.

## Tasks & Acceptance

**Execution:**
- [x] `docs/api/internal/cums-internal.openapi.yml` -- spec the contract with examples (clean, blocked, nothingHeld; merged, nothingHeld; 409) -- spec first.
- [x] CUMS `client/CompanyReferenceContract.java` + `HttpCompanyReferenceContract.java` + test `CompanyReferenceHolders.java`, `HttpCompanyReferenceContractTest.java` (written first) -- call shape: query encoding, body, order, 409, down holder, no holder, no JWT without user.
- [x] CUMS config (main + test), provider-test CsvSource -- implementers by config.
- [x] EMS `usermerge/CompanyReferenceService.java` + `InternalCompanyReferenceController.java` + `CompanyReferenceIntegrationTest.java` (first) -- preview/merge/idempotent/400/cache cleared after commit.
- [x] PCS same trio -- includes vote collision, source-only partner, both-partners 409 (preview blocker), 400 on invalid target.
- [x] EMS + PCS `S2sProviderContractIntegrationTest` -- company-references against CUMS spec, with and without JWT.
- [x] EMS `UserMergeService.evictCaches` -- side fix, own commit, test first: clear after commit via `CacheConfig.clearAfterCommit` (today it clears inside the transaction, the 17.11c race).
- [x] Docs: ADR-016 decision 5, module-map contract list and the "CUMS calls downstream only through its two contracts" count, 06-backend-architecture contract paragraph, doc-drift mappings.

**Acceptance Criteria:**
- Given the CUMS configuration lists two implementers, when CUMS previews or merges, then each is called in order with requests that validate against the CUMS spec, and a 409 surfaces as a blocked exception with the implementer's message.
- Given EMS and PCS run, when the contract is called as CUMS calls it, then every answer validates against the CUMS spec (with and without a forwarded JWT).
- Given a merge at EMS or PCS, when a company cache held an entry, then it is empty after commit.
- `EndpointAuthorizationGuardTest`, `EmsModuleWallsTest`, `FrozenRuleKeysTest` stay green; archunit stores do not grow.

## Implementation Notes

- Implemented directly in this session, not by a fresh coding subagent: this session runs as the dedicated build agent and the machine allows one heavy Gradle run at a time.
- CUMS: `CompanyReferenceContract` (exceptions `CallFailedException`, `MoveBlockedException`, enum `Trigger`), `HttpCompanyReferenceContract`; tests `HttpCompanyReferenceContractTest` (8, call shape), `CompanyReferenceContractWiringIntegrationTest` (bean calls the configured holders), spec examples added to the CUMS provider test.
- EMS and PCS: `usermerge/CompanyReferenceService` + `InternalCompanyReferenceController`; PCS has no `AfterCommit` helper, so its service registers the after-commit clear itself.
- Spec: `CompanyReferenceKeys` schema added for `movedKeys`/`removedKeys`; counts reuse `UserReferenceCounts`.
- Docs also touched: 01-system-overview (PCS interfaces), the two provider-test Javadocs.

## Plan Change Log

## Review Triage Log

Pass 1 (quick lens): 5 findings. high 0, medium 1, low 3, false 1, maybe-false 0.

| # | Finding | Verdict | Route | Evidence / action |
|---|---|---|---|---|
| 1 | Provider S2S tests send the merge without a forwarded JWT; AC 2 names merge answers with and without | low | patch | True: `user` reached only the preview. Merge calls now carry the JWT when `user` is true (EMS `contractCall`, PCS `postContract` overloads). |
| 2 | Side fix not in its own commit | false | reject | Nothing was committed yet at review time; the side fix is committed on its own (`fix(ems)` commit). |
| 3 | Implementer order EMS, PCS: a 409 at PCS after EMS moved leaves EMS rows on the target while the source survives, and a rerun is refused again | medium | patch | True: `ContractCalls` stops at the first failure in configured order; PCS is the only holder that refuses. PCS is now listed first, with the reason in `application.yml`, and the spec says a refusing holder goes first. |
| 4 | Preview `target` parameter lacks the company-key pattern the providers enforce | low | patch | True: merge schema has it, preview parameter did not. Pattern added. |
| 5 | Provider classes not mapped to ADR-016 / module-map in doc-drift mappings | low | patch | True: `usermerge/` mappings point to user-merge docs only. Added entries for EMS and PCS `CompanyReferenceService` / `InternalCompanyReferenceController`. |

## Design Notes

**Architecture note.** Modules: CUMS `client` (contract caller), EMS `usermerge` (L6 edge, SQL on `registrations`), PCS `usermerge`. New dependency: none in code; CUMS→EMS/PCS only through its own contract (ADR-016 d4). New endpoints: `/internal/company-references/{preview,merge}`; nearest `/internal/user-references/{preview,merge}`, not extended because the key is a company key, the validation (12-char target, partner rule) and the refusal differ. Reused: `ContractCalls`, `ContractServer`, `ProviderContract`, `CacheConfig.clearAfterCommit`, the user-reference controller/service shape. Deviations: none.

**Decisions taken (no human available, sensible default):**
1. Request `{source, target, performedBy, trigger}` with trigger `MERGE|RENAME` (stories 3 and 4 both call it; providers log it).
2. Result carries `movedKeys`/`removedKeys` (row ids per `table.column`; topic_votes by `topic_id`) besides counts, so story 4's `company_merge_log` can record "moved keys per table" for an undo by hand; no provider-side log table, no migration.
3. Preview reports both-partners as a blocker (as user-references does); merge answers 409 `MERGE_BLOCKED_BOTH_PARTNERS`.
4. Providers clear all their caches after commit (merges are rare; user caches hold `companyId` too) instead of picking company caches by name.
5. EMS provider lives in package `usermerge` (module "usermerge", the merge edge), avoiding a new module rule.

**Store sizes at start:** EMS archunit store 2 lines, CUMS 0; ratchet baseline 529 lines. End must not be larger.

**Store sizes at end:** EMS 2, CUMS 0, ratchet baseline unchanged (`make check-test-ratchet` clean). Not smaller: the two frozen EMS lines are mail core (owner-accepted, no owning story) and none sits in the code this story touches; the violation this story exposed and fixed instead is the in-transaction cache clear of EMS `UserMergeService`.

## Verification

**Commands:**
- `./gradlew :services:company-user-management-service:test --tests '*CompanyReference*' --tests '*S2sProviderContract*' --max-workers=4` -- green
- `./gradlew :services:event-management-service:test --tests '*CompanyReference*' --tests '*S2sProviderContract*' --tests '*UserMerge*' --max-workers=4` -- green
- `./gradlew :services:partner-coordination-service:test --tests '*CompanyReference*' --tests '*S2sProviderContract*' --max-workers=4` -- green
- Full suites with checkstyle, one at a time: `./gradlew :services:<svc>:build --max-workers=4` for CUMS, EMS, PCS -- green
- `make check-test-ratchet` -- no RAISED
- `cd web-frontend && npm run lint:api-internal` -- clean
