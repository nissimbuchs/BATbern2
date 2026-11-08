---
id: 17
type: story
title: "Exhaustive search"
parent: initiative-architecture-hardening
covers: []
after: []
refined: true
hitl: false
risk: medium
---

# Exhaustive search

## Description

17.17 (owner go 2026-11-08, from Amelia's search audit the same day). Every search in the
organizer and public UI runs in the backend over the whole table, matches a word anywhere in any
of the record's searchable fields regardless of case, accents, umlaut spelling and word order,
and never fails on what the user typed. Today two searches filter only the page the browser
loaded, the company search cuts an unordered result, and the event search matches whole words
only and throws on some inputs.

Architecture note (draft, `bmad-build` confirms it in the plan): modules touched: CUMS user and
company search, EMS registrations, newsletter, event search, topics; PCS partner list; frontend
partner and topic lists, company list paging · new dependencies: one shared SQL fold function
per service schema, created identically by each service's Flyway; the newsletter last-name match
goes EMS→CUMS via `/internal` (downstream→upstream, allowed), never via a read of
`user_profiles` · endpoints: no new ones; `search` parameters added to the existing partner and
topic list operations and real pagination on `GET /companies/search` (nearest: the user list's
`search`) · reused: `UserRepository.searchByNameOrEmail` ranking, `UserSpecification`,
`RegistrationSpecification`, `PersonAddresses` for additional e-mails · deviations: none planned.

## Audit findings (2026-11-08, code read, local anonymized DB measured)

| Search | Runs | Defect |
|---|---|---|
| Partners (`PartnerList.tsx:35`) | browser, over the 20 loaded rows | misses partners on other pages |
| Topic picker (`TopicSelectionOverlay.tsx:157`) | browser, over the first 50 | misses topics beyond 50 |
| Companies (`CompanySearchService`, `companyApi.ts:141`) | SQL, no ORDER BY, cut to 20 (10 public) in Java; `totalPages: 1` | "ag" matches 174, 20 arbitrary shown, no page 2; exact "Bundes" at position 12 of 13, never in the public picker |
| Events and archive (`EventSearchService.buildFullTextSearchPredicate`) | `to_tsquery('german', raw input)` | whole words only ("Archi" 0 hits, substring 7); "a  b", "(test" raise `syntax error in tsquery` |
| User list (`UserSpecification.matchesSearch`) | SQL | no full name, no additional e-mails |
| User picker (`UserRepository.searchByNameOrEmail`) | SQL, ranked | "first last" only, not "last first"; no additional e-mails |
| Participants (`RegistrationSpecification.buildSpecification`) | SQL | no full name, no company |
| Newsletter (`NewsletterSubscriberRepository.findFiltered`) | SQL | e-mail and first name only; 1,989 of 2,114 have a profile with a last name |
| All of the above | | no accent or umlaut folding; `%` and `_` in input act as wildcards |

## Requirements

Verify: on a Testcontainers database seeded with production-shaped names, every search below
finds "Müller", "Mueller" and "Muller" for each of those three inputs, finds "Hans Peter Muster"
for "muster hans", "peter" and "hans muster", finds a record beyond the first page, and answers
2xx for `(test`, `a  b`, `100%`, `'` and an empty string.

1. **One fold** (R1)
   **Given** any searchable text and any search input
   **When** they are compared
   **Then** both sides are folded the same way: lower case, diacritics removed (`unaccent`),
   `ä/ae`, `ö/oe`, `ü/ue` treated as the same letter, `ß` as `ss`, hyphen, apostrophe and
   underscore as a space, whitespace collapsed
   **And** the fold is one SQL function created by a new Flyway migration in each service that
   searches (with `CREATE EXTENSION IF NOT EXISTS unaccent`), byte-identical across services.
   Guard: a test that compares the function bodies across the services' migrations, plus
   integration tests per service. No `pg_trgm`, no typo tolerance.

2. **Words in any order, anywhere** (R2)
   **Given** an input of several words
   **When** a search runs
   **Then** a record matches when every word occurs as a substring of at least one of its
   searchable fields, in any order
   **And** `%`, `_` and `\` in the input match literally
   **And** a blank input means no search filter. Guard: integration tests per search.

3. **Field sets** (R3)
   - Users (list and picker): username, first name, last name, primary e-mail, verified and
     unverified additional e-mails, company id.
   - Participants: username, first name, last name, e-mail, company id.
   - Newsletter subscribers: e-mail, first name, and the last name of a linked profile, resolved
     through CUMS `/internal` (no EMS read of `user_profiles`, ADR-016).
   - Companies: slug, display name.
   - Partners: company slug and company display name.
   - Topics: title and description.
   - Events (organizer list and public archive): event title and description, event number
     (`56` and `BATbern56` find BATbern56), session titles and abstracts, speaker names.
   Guard: one integration test per field.

4. **Server-side, whole table, real pages** (R4)
   **Given** more matching rows than one page
   **When** the partner list, topic picker or company list searches
   **Then** the backend filters before paging, `totalItems` counts all matches, and page 2 shows
   the next matches
   **And** the frontend no longer filters search results itself (`PartnerList.tsx`,
   `TopicSelectionOverlay.tsx`). Guard: integration tests with a row beyond the first page, and
   the frontend tests assert that the search term reaches the service call.

5. **Ranked before cut** (R5)
   **Given** an autocomplete that returns at most N rows (user picker, company pickers)
   **When** more than N match
   **Then** rows are ordered in SQL before the limit: exact match, then prefix, then substring,
   with a stable tie-break
   **And** an exact company name is always in the first 10. Guard: the "Bundes" case as a test.

6. **Events keep relevance and never fail** (R6)
   **Given** any input
   **When** the event or archive search runs
   **Then** substring matches are found ("Archi" finds "Architektur"), whole-word full-text
   matches still rank first, and no input produces a 5xx (no `to_tsquery` on raw input).
   Guard: integration tests with "Archi", `(test`, `a  b`, `C++`, `'`.

7. **Contract-first** (R7)
   **Given** the new `search` parameters and the company search pagination
   **When** the specs in `docs/api/` change
   **Then** generated types are regenerated and committed, the FE path contract check (17.3)
   and Spectral (17.4) pass, and Bruno covers one search per collection that has one.

8. **Measured** (R8)
   **Given** production-size data (anonymized local snapshot: 2,595 users, 712 companies, 8,190
   registrations, 2,114 subscribers)
   **When** each search runs
   **Then** its duration is recorded in the plan, and an index is added only for a search that
   measures over 100 ms.

## Boundaries

- Out: `pg_trgm`, typo tolerance, similarity ranking (owner, 2026-11-08).
- Out: Mail Center filters (no free-text search today).
- Out: new endpoints. Search is a parameter of the existing list operations.
- Must not change: what each search returns for a blank input, and the existing filters
  (role, status, company, topic codes) combined with search.
- Branch: built on `origin/feature/17-4-17-5-gates`, then rebased onto `develop` before the PR
  (owner, 2026-11-08).

## References

- Audit in the session of 2026-11-08 (findings table above).
- `docs/architecture/module-map.md`, ADR-003, ADR-004, ADR-016.
- Prior fix of the same class: `UserRepository.searchByNameOrEmail` Javadoc (2026-09-09), #1153.
