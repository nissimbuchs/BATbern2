---
title: 'Exhaustive search (17.17)'
type: 'bugfix'
ticket: '17'
created: '2026-11-08'
status: 'built'
baseline_revision: '8671ced678e8ebfa56f8e9180ba717bbc93ae67a'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/initiative-architecture-hardening/backlog/story-exhaustive-search.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Partner and topic search filter only the rows the browser loaded, company search cuts an unordered result and fakes paging, event search matches whole words and throws on `(`, and no search folds accents, umlaut spellings or word order (audit 2026-11-08, ticket table).

**Approach:** One SQL fold function (`batbern_fold`, `unaccent`-based) plus one shared-kernel term parser; every search becomes "each word of the input is a substring of at least one searchable field, after folding", evaluated in SQL before paging. Partner and topic search move to the server; company list gets real paging; event search drops raw `to_tsquery`.

## Boundaries & Constraints

**Always:** fold both sides identically (lower, `unaccent`, `ae/oe/ue`→`a/o/u`, `-'_`→space, whitespace collapsed); escape `%` `_` `\` in input; blank input = no search filter; existing filters (role, company, status, level, topic codes, workflow state) combine with search unchanged; filter before paging, `totalItems` counts all matches; contract-first for every new parameter, types regenerated; tests mock only at the process boundary, ratchet ends ≤ start.

**Never:** `pg_trgm` or typo tolerance; new public endpoints (search is a parameter of existing list operations); EMS reads of `user_profiles`; editing applied migrations; Mail Center filters.

**Decisions (owner go 2026-11-08 plus investigation):** newsletter last name is matched through the subscriber's `username` column (1,978 of 1,978 subscribers with a profile carry the last name in it, measured locally), no new EMS→CUMS call; event lists keep their explicit date sort, no relevance rank (substring matches are a superset of the old word matches); partner search resolves display names through a new CUMS `GET /internal/companies?search=` (names only, unlimited), because `partners.company_name` is a 12-char slug; it is a thin route onto the same `CompanyQueryService` search as the public list, chosen over reusing the public `GET /companies` because S2S calls go to `/internal` only (owner, option A, 2026-11-08). One plan, not split (owner 2026-11-08).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Umlaut spellings | "Müller", "Mueller", "Muller" | each finds all three spellings | – |
| Word order, middle name | "muster hans", "peter" vs "Hans Peter Muster" | found | – |
| Additional e-mail | user list / picker, "alt@x.ch" | owner of that additional address found | – |
| Substring in events | "Archi" | events titled "Architektur" | – |
| Event code | "56" / "BATbern56" | BATbern56 found | – |
| Beyond page 1 | 25 partners, match on #23 | returned, `totalItems` exact | – |
| Exact company in public picker | "Bundes", 13 matches, limit 10 | "Bundes" first | – |
| Hostile input | `(test`, `a  b`, `100%`, `'`, `C++` | 2xx, `%` literal | no 5xx |
| Blank | "", "   " | same result as without search | – |

</frozen-after-approval>

## Code Map

- `shared-kernel/.../search/` (new) -- `SearchTerms.parse(String)`: tokens (split on whitespace `-'_`, max 8, LIKE-escaped); `SearchPredicates.allTermsInAnyField(cb, terms, fields...)` using `cb.function("batbern_fold", String.class, x)` and `cb.like(..., '\\')`; canonical `db/search/batbern_fold.sql`; testFixture `SearchFoldContract` asserting a service migration contains the canonical function byte-identically.
- CUMS: `specification/UserSpecification.matchesSearch:19` (add additional e-mails EXISTS via `UserAdditionalEmail.user`); `repository/UserRepository.searchByNameOrEmail*:~180-238` → Specification + rank (exact full name, exact field, prefix, substring), limit in DB; `service/UserSearchServiceImpl:61-73` caller; `repository/CompanyRepository.searchByNameOrDisplayName:~54` → ranked, limited in DB; `service/CompanySearchService:39,48-98` drop Java `.limit`; `service/CompanyQueryService:51-93` + `CompanySpecification` add `search`; `controller/InternalUserController` pattern for new `InternalCompanyController` `GET /internal/companies?search=` → names; migration `V27__search_fold.sql`. Dead: `UserService.listUsers:282` (no callers) -- delete.
- EMS: `service/EventSearchService.buildFullTextSearchPredicate:411-497` → terms over event title, description, `eventCode`, session title/description (EXISTS), `SessionUser.speakerFirstName/LastName` (EXISTS); `CONTAINS/STARTS_WITH/ENDS_WITH:323-344` escape; `config/HibernateConfig` unused `ts_match_*` registrations -- delete; `specification/RegistrationSpecification.buildSpecification:88` (+ `attendeeCompanyId`), dead `filterByEventAndUsernameSearch:54` -- delete; `repository/NewsletterSubscriberRepository:81-113` → Specification (email, firstName, username); `service/NewsletterSubscriberService:351,376`; `controller/TopicController.listTopics:85` + `TopicService.getAllTopics:80-137` + `TopicRepository.findByFilters:80` add `search` (title, description); migration `V147__search_fold.sql`.
- PCS: `controller/PartnerController:43-75` (fake totals), `service/PartnerService:68-111` (findAll + Java sort), `repository/PartnerRepository:42-60` → Specification + `Pageable`, sort `companyName`; `search` → `CompanyServiceClient.findCompanyNames(search)` (new, `/internal/companies`) → `companyName IN`; no PCS migration.
- Specs: `docs/api/partners-api.openapi.yml:59-85`, `topics-api.openapi.yml:62-94`, `companies-api.openapi.yml:239` (list) add `search`; regenerate `web-frontend/src/types/generated/`.
- Frontend: `PartnerList.tsx:35-43` drop client filter, `partnerApi.ts:67-101` send `search`, `usePartners` key; `TopicSelectionOverlay.tsx:94-99,156` send `search`; `topicService.ts:43-75`; `companyApi.ts:42-60,142-185` list search via `GET /companies?search&page&limit`, delete `searchCompaniesWithPagination`.
- Tests to extend: CUMS `UserControllerIntegrationTest:310,1061`, `CompanySearchIntegrationTest`, `CompanyControllerIntegrationTest`; EMS `EventControllerIntegrationTest:2343`, `PublicSessionVisibilityIntegrationTest:273`, `RegistrationControllerIntegrationTest:496`, `NewsletterControllerIntegrationTest:522`, `TopicControllerIntegrationTest`; PCS `PartnerControllerIntegrationTest:84`. Frontend: per-test `setupServer` (MSW 2.15, as `MailComposer.test.tsx`), shared harness not yet present. Bruno: `companies-api/05`, `users-api/07*,18`.

## Tasks & Acceptance

**Execution:**
- [x] `shared-kernel/.../search/*` + `SearchFoldContract` + unit tests -- parser, escaping, predicate builder -- one implementation for three services
- [x] CUMS `V27__search_fold.sql`, EMS `V147__search_fold.sql` -- `CREATE EXTENSION IF NOT EXISTS unaccent`; canonical function -- fold lives in SQL, both sides
- [x] CUMS users: spec, picker, delete `listUsers` -- additional e-mails, word order, folding
- [x] CUMS companies: ranked limited search, list `search`, `/internal/companies` -- "Bundes" case, real paging
- [x] EMS events, registrations, newsletter, topics -- per Code Map; delete dead code
- [x] PCS partner list -- DB paging, `search` via CUMS names
- [x] Specs + generated types -- contract-first
- [x] Frontend partner, topic, company list -- server search, tests with MSW asserting the request carries `search`
- [x] Integration tests per I/O row and per field (Testcontainers), fold contract test per service
- [x] Bruno: partners search, events substring search, company list search paging
- [x] Docs: doc-drift mappings for touched paths; ticket findings resolved

**Acceptance Criteria:**
- Given production-shaped seed data, when each I/O row runs against each applicable search, then it holds (integration tests green).
- Given the canonical fold, when a service migration diverges by one byte, then that service's contract test fails.
- Given `make check-test-ratchet`, `check-frontend-api-paths` and Spectral, when run, then all pass and the ratchet baseline is not higher.
- Given the anonymized local snapshot, when each search runs, then its duration is recorded below; an index is added only above 100 ms.

## Implementation Notes

- **Fold migration lock**: V27 (CUMS) and V147 (EMS) start with `SELECT pg_advisory_xact_lock(hashtext('batbern_fold'))`. Both services boot at the same time on a deploy and Flyway locks per history table, so without it the two `CREATE EXTENSION` / `CREATE OR REPLACE FUNCTION` could collide (unique violation, "tuple concurrently updated"). Outside the canonical body, so the contract test is unaffected.
- **Ranking without LIKE escaping**: prefix rank uses `locate(fold(field), fold(phrase)) = 1`; exact rank `fold(field) = fold(phrase)`. The phrase is the whole input, unescaped.
- **Ranked specs and count queries**: the ranking `orderBy` is applied only when the query's result type is not `Long`, so Spring Data's count query stays clean.
- **Partner filters**: tier and status now combine (before, a filter with both applied only the tier). PCS sorts by `companyName` + id via `DeterministicPaging`. `findByPartnershipLevel` / `findActivePartners` / `findInactivePartners` became dead and were replaced by `Specification`s on `PartnerRepository`.
- **Newsletter status**: unknown status values still match nothing (as the former JPQL), `all`/null unrestricted.
- **EMS entities**: the unmapped-in-code `tsvector` fields (`Event.titleVector/descriptionVector`, `Session.*Vector`, `SessionUser.speakerNameVector`) were removed from the entities together with `HibernateConfig` and its `META-INF/services` registration; the DB columns stay.
- **Also cleaner**: `RegistrationSpecification.filterByEventAndStatus` (no callers) deleted too; `CompanySpecification` `$contains/$startsWith/$endsWith` escape `% _ \` like EMS.
- **Known fold side effect**: `ae/oe/ue -> a/o/u` also folds non-umlaut pairs, so a term starting with `e` after an `a`, `o`, `u` in the field no longer matches as a substring ("el" misses "Manuel", "uel" finds it). Accepted by the canonical fold; noted for the owner.

**Durations** (anonymized local snapshot, 2,595 users, 712 companies, 8,190 registrations, 2,114 subscribers, 66 events, 19 topics, 9 partners; `EXPLAIN ANALYZE`, function created inside a rolled-back transaction, warm single run, 2026-11-08):

| Search | Query | ms |
|---|---|---|
| User list / picker | "muster hans", 6 fields + additional-email EXISTS, LIMIT 20 | 17.4 |
| Company picker | "ag" (most matches), ranked, LIMIT 10 | 2.4 |
| Participants | "mueller", largest event | 2.0 |
| Participants, no event filter (worst case) | "mueller", all 8,190 | 23.1 |
| Newsletter | "mueller", LIMIT 20 | 5.3 |
| Events / archive | "archi", sessions + speakers EXISTS | 13.9 |
| Topics | "cloud" | 0.1 |
| `/internal/companies` | "mobiliar" | 1.3 |

All below 100 ms: no index added.

**Ratchet**: start = end: be-mock-in-process 41/59, be-mock-s2s-client 52/63, be-transactional-it 129/129, fe-mock-components 76/175, fe-mock-hooks 121/248, fe-mock-services 193/263. New tests mock nothing in-process (new frontend tests use MSW; the PCS integration test stubs its existing `CompanyServiceClient` bean, the client itself is tested against `MockRestServiceServer`).

- Review patches (pass 1) applied: JWT forwarded on `/internal/companies`, Bruno `users-api/18` per-user field check dropped, public archive search test (8 cases), build.gradle comment, story references removed from source. Full Verification rerun green (shared-kernel 543, CUMS 864, EMS 2579, PCS 240 tests, 0 failures; Vitest 1711 passed; ratchet unchanged; 300/300 API paths). Bruno on local dev: users 155, companies 56, partners 105, events-crud 55 checks passed, 0 failed, 0 skipped files.
- Local dev only: V147 and V27 had been applied to the local DB before the header-comment edit, so EMS failed with a checksum mismatch; fixed with `flywayRepair` against `localhost:5432/batbern_development`. Never applied to a shared environment.

## Plan Change Log

## Review Triage Log

Pass 1 (quick lens + main session), 2026-11-08: high 0, medium 3, low 6, false 0 (1 rejected as unfounded), maybe-false 0.

| # | Finding | Verdict | Route | Evidence / action |
|---|---|---|---|---|
| 1 | `CompanyServiceClientImpl.findCompanyNames` forwards no JWT | medium | patch | `exchange(uri, GET, null, …)`; module-map: forward when present; fix: `createHeadersWithJwtToken()` + test |
| 2 | Bruno `users-api/18` per-user assertion requires raw `test` in five fields | medium | patch | additional e-mails and folded spellings now match; drop per-user field check, check 07* |
| 3 | Public archive search (`/public/events?scope=archive&search=`) untested for new matching | medium | patch | only organizer `GET /events?filter=` tested; add parameterized public-archive test |
| 4 | Ticket text (R3 newsletter via CUMS, R6 rank, `/companies/search` paging) differs from code | false | reject | owner approved both deviations 2026-11-08 (checkpoint Q2) and paging on `GET /companies`; build never writes the ticket |
| 5 | Newsletter list counts twice (Page count + `countSubscribers`) | low | reject | extra COUNT over ~2k rows, invisible to users; fix changes method signatures |
| 6 | `searchRanked` (users, companies) runs an unused COUNT when the page is full | low | reject | ms on ≤2.6k rows; fix needs a fluent/limit query, more than a correction |
| 7 | `shared-kernel/build.gradle:98` names nonexistent `BatbernFoldSqlTest` | low | patch | it is `SearchPredicatesSqlTest` |
| 8 | "Story 17.17" references in production code, migrations, specs (main session) | low | patch | agent rule: no story references in source; deletion only |
| 9 | Fold reads `ae/oe/ue` as `a/o/u` in all words, so "el" misses "Manuel", "eller" misses "Mueller" (main session) | low | reject | consequence of the approved fold; only partial terms starting inside the pair; a dual-fold fix adds a second function and an OR per field; reported to owner |

## Design Notes

Architecture note: modules touched: shared-kernel (new `search`), CUMS users/companies, EMS event core search, registrations, newsletter, topics, PCS partners, frontend partner/topic/company · new dependencies: PCS→CUMS `/internal/companies` (downstream→upstream, ADR-016) · endpoints: no new public ones; `search` param on `listPartners`, `listTopics`, `listCompanies` (nearest: `listUsers`' `search`); internal `GET /internal/companies` (nearest `GET /internal/users`, which returns users not names and has no company search) · reused: `UserSpecification`, `RegistrationSpecification`, `DeterministicPaging`, `CompanyServiceClient` · deviations: none.

Fold (canonical, identical in every service migration):
```sql
CREATE OR REPLACE FUNCTION batbern_fold(input text) RETURNS text
LANGUAGE sql STABLE PARALLEL SAFE AS $$
  SELECT regexp_replace(replace(replace(replace(translate(lower(public.unaccent(coalesce(input, ''))),
         '-''_', '   '), 'ae', 'a'), 'oe', 'o'), 'ue', 'u'), '\s+', ' ', 'g')
$$;
```
Terms are folded in SQL too (`batbern_fold(col) LIKE '%' || batbern_fold(:term) || '%' ESCAPE '\'`), so Java needs no fold. One shared database: both migrations `CREATE OR REPLACE` the same body; the contract test keeps them equal.

Cleaner: deleted dead `UserService.listUsers`, `RegistrationSpecification.filterByEventAndUsernameSearch`, `HibernateConfig` `ts_match_*`, `buildFullTextSearchPredicate`, `searchCompaniesWithPagination`; PCS partner list stops loading the whole table. Left: the tsvector columns and V39 functions stay in the DB (dropping them needs a migration and gains nothing now); PCS per-partner company enrichment N+1 (17.6/17.7). ArchUnit stores and ratchet: record start and end counts here.

## Verification

**Commands:**
- `set -o pipefail; ./gradlew :shared-kernel:test :shared-kernel:publishToMavenLocal --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/17-17-sk.log` -- BUILD SUCCESSFUL
- `set -o pipefail; ./gradlew :services:company-user-management-service:test --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/17-17-cums.log` -- green (then EMS, PCS, one at a time)
- `cd web-frontend && npx vitest run --maxWorkers=4 src/components/organizer/PartnerManagement src/components/organizer/EventPage/topicOverlay src/services/api src/components/shared/Company` -- green
- `make check-test-ratchet && make check-frontend-api-paths && (cd web-frontend && npm run check:api-types && npm run type-check && npm run lint:ci)` -- pass
- Bruno against local dev (`make dev-native-up`), new and touched search requests -- pass, no `Skipping invalid file`

**Manual checks (if no CLI):**
- Local dev UI: partner, topic, company, user, participant, newsletter and archive search with "mueller", "muster hans", "Archi", `(test`.
