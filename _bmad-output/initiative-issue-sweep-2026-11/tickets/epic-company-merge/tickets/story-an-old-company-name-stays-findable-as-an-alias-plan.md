---
title: 'An old company name stays findable as an alias'
type: 'feature'
ticket: '2'
created: '2026-11-20'
status: 'built'
baseline_revision: '503975fd19b4fbb99294879574f8c9e691462e31'
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

**Problem:** After a merge (story 4) or a rename (story 3) the old company key and display name vanish, so the registration typeahead no longer finds what people type and `POST /companies/get-or-create` creates the duplicate again the next day.

**Approach:** CUMS stores aliases in a new `company_aliases` table (alias key and alias display name pointing to one company); search, typeahead and get-or-create resolve an alias to its company; a `CompanyAliasService` records aliases for stories 3 and 4. Deleting a company removes its aliases.

## Boundaries & Constraints

**Always:** Migration is the next free CUMS version (V36). The alias row points at the company by its CUMS UUID (same service, ADR-003), `ON DELETE CASCADE`. Search and typeahead return the company (its key and display name), never the alias. Recording an alias clears the `companySearch` cache. Integration tests on Testcontainers PostgreSQL, committing, cleaned by creator.

**Never:** No public endpoint to create or list aliases (stories 3, 4 record them in code; story 6 UI). No change to the response schemas. No reads of another service's tables. No producer of aliases in this story besides the service method.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Typeahead by alias key | alias `oldkey` → company `newkey` | `GET /companies/search?query=oldkey` lists `newkey` once, with its display name | none |
| Typeahead by alias display name, other case | alias "Old Name AG" | query `old name ag` lists the target | none |
| Paged list search | same | `GET /companies?search=old name` contains the target | none |
| Get-or-create by alias display name | typed "OLD NAME AG" | 200, the target, no new company | none |
| Get-or-create whose slug is an alias key | typed name slugs to `oldkey` | 200, the target | none |
| Get-or-create by existing display name, other slug | company `post` "Die Post"; typed "die post" | 200, `post`, no `diepost` created | none (side fix) |
| POST /companies with an alias display name | "Old Name AG" | refused like a duplicate | 409 |
| POST /companies whose slug is an alias key | slug `oldkey` | key gets the next free suffix | none |
| Delete company | company with aliases | aliases gone | none |
| Record alias of a key held by a live company | `record(target, "livekey", ...)` | refused | IllegalArgumentException |
| Record an alias key again | existing alias → other company | alias now points to the new company | none (upsert) |

</frozen-after-approval>

## Code Map

- `services/company-user-management-service/src/main/resources/db/migration/` -- last is V35 (HEAD and origin/develop): new `V36__create_company_aliases.sql`. Keys `^[a-zA-Z0-9]{1,12}$` (V28, `SlugGenerationService.COMPANY_NAME_PATTERN`).
- `.../domain/Company.java` -- UUID `id`, unique `name`, `displayName`. New `domain/CompanyAlias.java` (id = `aliasKey`, `companyId` UUID, `aliasDisplayName`, `reason` MERGE|RENAME, `createdAt`, `createdBy`).
- `.../repository/CompanyRepository.java` -- `findByName`, `existsByName`, `existsByDisplayNameIgnoreCase`, `searchRanked`; add `findFirstByDisplayNameIgnoreCaseOrderByCreatedAtAsc`. New `repository/CompanyAliasRepository.java`.
- `.../specification/CompanySpecification.java` -- `matchesSearch` (list, typeahead, `GET /internal/companies`) and `matchesSearchRanked`: add an `EXISTS` subquery on `CompanyAlias` with `SearchPredicates.allTermsInAnyField(aliasKey, aliasDisplayName)`; rank an exact/prefix alias match like a name match.
- `.../service/CompanyService.java` -- `resolveOrCreate` (get-or-create): today key only; `createCompany`: display-name 409 + `ensureUniqueCompanyName(baseKey, existsByName)`. `deleteCompany`: JPA delete (DB cascade removes aliases).
- `.../service/CompanySearchService.java` -- `invalidateCache()` clears `companySearch`.
- `.../security/SecurityContextHelper.getCurrentUsername()` -- `createdBy` of an alias.
- Tests: `integration/CompanyGetOrCreateIntegrationTest.java` (model: commits, `DELETE FROM companies WHERE created_by = ?`), `integration/CompanySearchIntegrationTest.java`.
- `docs/api/companies-api.openapi.yml` -- descriptions of `listCompanies.search` (l.86), `searchCompanies` (l.420), `getOrCreateCompany` (l.571), `createCompany` 409.
- Docs: `docs/architecture/06-backend-architecture.md` l.1745 field-set table; `docs/architecture/03-data-architecture.md` l.1374 CUMS table list; `docs/user-guide/entity-management/companies.md` l.152; changelog "Unreleased".

## Tasks & Acceptance

**Execution:**
- [ ] `CompanyGetOrCreateIntegrationTest` -- test first: typed display name of an existing company with another key returns it (200), no new row -- side fix, own commit, with `CompanyService.resolveOrCreate` matching the display name ignoring case after the key.
- [ ] `integration/CompanyAliasIntegrationTest.java` (first, red) -- every matrix row except the side fix, through MockMvc and `CompanyAliasService`.
- [ ] `V36__create_company_aliases.sql` -- table, key CHECK, FK cascade, index on `company_id` and `lower(alias_display_name)`.
- [ ] `CompanyAlias`, `CompanyAliasRepository`, `service/CompanyAliasService.java` -- `record(company, aliasKey, aliasDisplayName, reason)` (upsert, refuses a live key, clears search cache), `resolve(typedName)`, `isAliasKey(key)`.
- [ ] `CompanySpecification` -- alias subquery in match and rank.
- [ ] `CompanyService` -- get-or-create: key, display name, alias key, alias display name, then create; create: alias display name 409, alias keys taken.
- [ ] Spec descriptions, `npm run generate:api-types`, docs, changelog.

**Acceptance Criteria:**
- Given an alias, when the typeahead, the paged list and `GET /internal/companies?search=` search by its key or display name, then each answers the target company and no alias row.
- Given a company with aliases is deleted, then `company_aliases` holds none of them.
- CUMS full build (tests + checkstyle) green; archunit store and ratchet do not grow.

## Implementation Notes

- Implemented directly in this session (no coding subagent): this session is the dedicated build agent and the machine allows one heavy Gradle run at a time.
- Side fix committed first on its own (`fix(company): get-or-create returns the company whose display name was typed`), test first (red, then green).
- `CompanyAliasService.record(companyKey, aliasKey, aliasDisplayName, reason, performedBy)`: `performedBy` is a parameter (stories 3 and 4 pass the contract's `performedBy`), so no security context is needed; the search cache is cleared after commit.
- Display names (company and alias) are matched with `batbern_fold` equality (native queries), as the search reads them; `batbern_fold` is STABLE, so no expression index: the alias table is small.
- Touch rule: the 9 mock-based `createCompany`/`getOrCreateCompany` unit tests of `CompanyServiceTest` were removed; their behaviour runs against PostgreSQL in `CompanyControllerIntegrationTest`, `CompanyGetOrCreateIntegrationTest`, `CompanyAliasIntegrationTest`, `EventPublishingIntegrationTest` (new: get-or-create publishes `CompanyCreated` once) and `SpecPatternsAcceptProducedValuesIntegrationTest` (anonymous creator).
- Rename to an alias key or alias display name (`PATCH /companies/{name}`) is not guarded here: story 3 rebuilds the rename and takes it (review finding 2).

## Plan Change Log

## Review Triage Log

Pass 1 (quick lens): 8 findings. high 0, medium 3, low 3, false 2, maybe-false 0.

| # | Finding | Verdict | Route | Evidence / action |
|---|---|---|---|---|
| 1 | Alias prefix match not ranked as prefix | medium | patch | True: rank 1 checked name/displayName only; a limit could cut the company. Alias `foldedStartsWith` added; test `should_rankAliasPrefixAsPrefix_when_typeaheadLimited` (red before). |
| 2 | `PATCH` can rename a key onto an alias key / alias display name | medium | patch (story 3) | True, `updateCompany` checks `existsByName` only. Story 3 rebuilds the rename in this same PR and adds the guard there (no deferred-work, epic C9). |
| 3 | Re-pointing an alias overwrites reason/createdBy/createdAt; dead `@PrePersist` | low | patch (part) | The row describes the current mapping by design (decision 2); history goes to story 4's merge log. Dead `@PrePersist` removed. |
| 4 | `lower(alias_display_name)` index unused by `upper()` IgnoreCase queries | low | patch | True. Lookups now use `batbern_fold` (finding 8); fold is STABLE and not indexable, so the index is dropped from the not yet applied V36. |
| 5 | Removed unit tests: get-or-create event and anonymous creator uncovered | low | patch (part) | Event: true, new `EventPublishingIntegrationTest` case. Anonymous creator: false, `SpecPatternsAcceptProducedValuesIntegrationTest.anonymouslyCreatedCompany_matchesSpec` asserts `createdBy anonymous`. |
| 6 | Dead `createRequest` fixture in `CompanyServiceTest` | low | patch | Removed with its import. |
| 7 | User guide describes aliases no one can create yet | false | reject | Stories 3 and 4 create aliases in the same epic PR; nothing ships before them. |
| 8 | Get-or-create matches display names by case only, the typeahead folds umlauts/accents | medium | patch | True: "Mowe" vs "Möwe" slugs differ, so a duplicate was created. Company and alias display names now match under `batbern_fold`; tests in both ITs (red before). |

## Design Notes

**Architecture note.** Modules: CUMS company (domain, repository, specification, service). New dependencies: none. New endpoints: none; get-or-create, search and list change behaviour only (nearest: themselves). Reused: `SearchPredicates`, `SlugGenerationService`, `CompanySearchService.invalidateCache`. Deviations: none.

**Decisions taken (no human available, sensible default):**
1. Get-or-create matching an existing company's display name (ignoring case) is fixed here as a side bug: it is the `diepost`/`post` duplicate of #1251, and `POST /companies` already treats it as a duplicate.
2. Alias key is the primary key (one alias belongs to one company); recording it again re-points it (a merge chain A→B→C ends at C).
3. `POST /companies` with an alias display name answers 409 (as a duplicate company); a slug equal to an alias key gets the next free suffix, so a live key never shadows an alias.
4. `reason` (MERGE, RENAME) and `created_by` are stored for audit; story 4's merge log keeps the detail.

**Store sizes at start:** CUMS archunit store 0 violation lines, ratchet baseline 529 lines.

**Store sizes at end:** CUMS archunit 0, ratchet baseline 529 (`make check-test-ratchet` clean). Not smaller: `CompanyServiceTest` mocks own repositories, a category the ratchet does not count; 9 of its mock-based tests were removed under the touch rule. The violation exposed and fixed is the get-or-create duplicate (side fix).

## Verification

**Commands:**
- `./gradlew :services:company-user-management-service:test --tests '*CompanyAlias*' --tests '*GetOrCreate*' --tests '*CompanySearch*' --tests '*CompanySpecification*' --max-workers=4` -- green
- `./gradlew :services:company-user-management-service:build --max-workers=4` -- green (full suite + checkstyle)
- `cd web-frontend && npm run generate:api-types && npm run type-check` -- clean
- `make check-test-ratchet` -- no RAISED
