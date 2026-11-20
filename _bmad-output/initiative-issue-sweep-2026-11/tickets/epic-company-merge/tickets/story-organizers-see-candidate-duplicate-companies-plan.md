---
title: 'Organizers see candidate duplicate companies'
type: 'feature'
ticket: '5'
created: '2026-11-20'
status: 'built'
baseline_revision: '8577ad35d9a1b785deed96677923d1284a87714d'
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

**Problem:** Organizers cannot see which companies are probably the same ("Unic" / "Unic AG", 26 same-display-name groups measured in #1251), so the merge of story 4 has nothing to start from.

**Approach:** `GET /companies/duplicate-candidates` (ORGANIZER) computes candidate pairs live by four rules, shows both sides with the counts the merge decision needs (users from CUMS; registrations and the partner flag from the company-reference holders through a new read operation of CUMS's contract) and suggests the target; `POST /companies/duplicate-candidates/dismissals` hides a pair for good. Mirrors `/users/duplicate-candidates`.

## Boundaries & Constraints

**Always:** Rules, a pair listed once under its first rule: `SAME_WEBSITE` (host lower case, scheme, `www.`, port and path removed; HIGH), `SAME_DISPLAY_NAME` (display name, else key: lower case, umlauts as ae/oe/ue, ß as ss, accents removed, hyphen/underscore/slash as space, other punctuation removed, spaces collapsed; HIGH), `SAME_NAME_WITHOUT_LEGAL_FORM` (as before, then the words ag, gmbh, sa, sarl, ltd, inc, llc, kg removed and a trailing `co`; generic words such as Kanton, Uni, Bern, Schweiz kept; an empty result never matches; MEDIUM), `SLUG_PREFIX` (one key starts with the other, shorter >= 4; LOW). Each side: display name, key, logo URL, website, user count, registration count, partner flag, created at/by. Suggested target: partner, else with logo, else more users, else older, else smaller key. `organizerCanMerge` false when both are partners. Dismissals in `company_duplicate_dismissals` (smaller company id first, cascade on delete of either). Counts from EMS and PCS only through the CUMS-owned company-references contract (ADR-016), one call per holder for all keys of the list. Spec first, internal contract first.

**Never:** No stored candidate table, no scheduler, no mail. No reads of EMS/PCS tables from CUMS. No Bruno request that writes.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Legal form | "Unic" (unic), "Unic AG" (unicag) | one pair `SAME_NAME_WITHOUT_LEGAL_FORM`, MEDIUM (not also `SLUG_PREFIX`) | |
| Same display name | "Die Post" (diepost), "Die Post" (post); "SBB CFF FFS" (sbbcffffs), "SBB CFF FFS" (sbb) | `SAME_DISPLAY_NAME`, HIGH | |
| Prefix | puzzle / puzzleitc ("Puzzle ITC") | `SLUG_PREFIX`, LOW | |
| Short prefix | ibm (3) / ibmswitzerla | no `SLUG_PREFIX` pair | |
| Generic words | "Kanton Bern" / "Kanton Zürich" | no pair | |
| Website | `https://www.x.ch/de` and `http://x.ch` | `SAME_WEBSITE`, HIGH | |
| Sides | pair with users, registrations, a partner | counts per side; partner is target; `organizerCanMerge` true | |
| Both partners | | `organizerCanMerge` false | |
| Dismiss | POST pair (either order) | 204; pair gone from the list; repeat 204 | unknown company 404; same company 400 |
| Holder down | counts call fails | 502 `DUPLICATE_COUNTS_INCOMPLETE` | |
| Auth | anonymous / non-organizer | 401 / 403 | |

</frozen-after-approval>

## Code Map

- `docs/api/internal/cums-internal.openapi.yml` -- company-reference contract (`previewCompanyReferenceMove` ~l.530): add `POST /internal/company-references/counts` `{keys: [..]}` → `{counts: {key: {"table.column": n}}}` (keys without rows may be left out), examples for ContractServer.
- CUMS `client/CompanyReferenceContract.java`, `HttpCompanyReferenceContract.java` (+ `HttpCompanyReferenceContractTest`, `CompanyReferenceHolders`) -- `counts(keys)` summed over holders.
- EMS `events/usermerge/CompanyReferenceService.java`, `InternalCompanyReferenceController.java`, tests `usermerge/CompanyReferenceIntegrationTest`, `contract/S2sProviderContractIntegrationTest` (~l.328) -- registrations per key.
- PCS `partners/usermerge/CompanyReferenceService.java`, `InternalCompanyReferenceController.java`, same two tests -- partners, topic_suggestions, topic_votes per key.
- CUMS model: `service/DuplicateCandidateService.java`, `controller/UserMergeController.java` (list/dismiss), `V26__user_duplicate_dismissals.sql`, `controller/DuplicateCandidatesIntegrationTest.java`. New: `service/CompanyDuplicateRules.java` (pure, unit tested), `service/CompanyDuplicateCandidateService.java`, endpoints on `CompanyMergeController` (tag Company Merge), `V38__company_duplicate_dismissals.sql`.
- Spec `docs/api/companies-api.openapi.yml` -- `/companies/duplicate-candidates`, `/companies/duplicate-candidates/dismissals`, schemas `CompanyDuplicateCandidate`, `CompanyDuplicateSide`, `CompanyDuplicateDismissal` (`nameA`, `nameB`).
- Bruno `bruno-tests/companies-api/` -- read-only GET of the list (and a same-company merge preview of the collection's own company, no holder call); comments in `docs { }`.
- Docs: 06b "Merging two companies" (finding the pairs), 03 (table), module-map contract line, changelog.

## Tasks & Acceptance

**Execution:**
- [x] Internal spec + EMS and PCS counts (tests first, provider contract tests) -- holders.
- [x] CUMS contract client counts + client test.
- [x] `CompanyDuplicateRulesTest` (red first) -- every rule row of the matrix.
- [x] Public spec; `CompanyDuplicateCandidatesIntegrationTest` (red first) -- sides, target, dismiss, 502, 401/403.
- [x] `CompanyDuplicateRules`, `CompanyDuplicateCandidateService`, controller, V38.
- [x] Bruno read-only requests, regenerate types, docs.

**Acceptance Criteria:**
- Given the companies of the matrix, when an organizer lists candidates, then exactly the expected pairs appear, each once, with both sides' counts and the suggested target.
- CUMS, EMS, PCS builds green (one after another); archunit stores, ratchet, response-conformance baselines do not grow.

## Implementation Notes

- Contract: `POST /internal/company-references/counts` (`countCompanyReferences`), `keys` 1..2000; EMS counts `registrations.attendee_company_id`, PCS `partners`, `topic_suggestions`, `topic_votes` per key (keys and columns without rows left out). CUMS client `counts(keys)` dedupes, chunks at 2000, sums per key and column; `REGISTRATIONS` and `PARTNERS` column names are constants on `CompanyReferenceContract`.
- `CompanyDuplicateRules` compares every pair (n^2 over all companies). Website host also drops user info and trailing dots; a missing `createdAt` ranks after a known one in the target choice.
- Dismissal errors: unknown company 404 (`CompanyNotFoundException`), same company 400 `INVALID_DISMISSAL` (handler on `CompanyMergeController`). Holder failure wraps `CallFailedException` in `CountsIncompleteException` so the existing `MERGE_PREVIEW_INCOMPLETE` handler stays untouched.
- Bruno: `companies-api/11a-list-duplicate-candidates.bru` (shape only) and `11b-merge-preview-same-company.bru` (SAME_COMPANY, no holder call); parse-checked with @usebruno/lang, not run against a live service.
- Store sizes at end: archunit stores, ratchet baseline and response-conformance baselines unchanged.

## Plan Change Log

## Review Triage Log

Pass 1 (quick lens): 7 findings. high 0, medium 0, low 5, false 1, maybe-false 1.

| # | Finding | Verdict | Route | Evidence / action |
|---|---|---|---|---|
| 1 | IT for "older wins" passes on the key tiebreak alone | low | patch | True (older also had the smaller key); test data changed so only age decides. |
| 2 | Summing one column from two holders untested | low | patch | True; client test now answers the same column at both holders. |
| 3 | Website rule: placeholders and shared platforms make false HIGH pairs | maybe-false | patch (placeholders) / recorded (platforms) | Not measured (no local DB running). Placeholders: a host without a dot never matches (direct guard). Shared platforms (linkedin.com/company/...) stay possible; organizers dismiss them; to measure on the anonymized snapshot before story 6 ships. |
| 4 | Bruno 11b test name claims no holder was asked | low | patch | Renamed to what it asserts. |
| 5 | `counts` Javadoc "no keys asks nobody" vs `requireHolders()` first | low | patch | Empty return moved first. |
| 6 | Ticket says "smaller key first", code uses smaller id | false | reject | The frozen plan says id (survives a rename); the ticket is not edited by a build. |
| 7 | Dismissal example `kantonzueric` implies a non-existent pair; 400 has no `INVALID_DISMISSAL` example | low | patch (example) / reject (400) | Example now `unic`/`unicag`; the generic 400 mirrors the users' dismissal. |

## Design Notes

**Architecture note.** Modules: CUMS company; EMS and PCS `usermerge` (contract holders). New dependency: none (a new operation of CUMS's own company-references contract, ADR-016 d4/d5). Endpoints: `GET /companies/duplicate-candidates`, `POST /companies/duplicate-candidates/dismissals` (nearest `/users/duplicate-candidates[/dismissals]`, other resource, mirrored); `POST /internal/company-references/counts` (nearest `preview`: one pair per call, would need two calls per pair per holder). Reused: contract caller, `ContractServer`, dismissal table pattern. Deviations: none.

**Decisions taken (no human available, sensible default):**
1. Counts through a new contract operation instead of a preview per pair (a preview counts only the source and refuses the same key).
2. Rules computed in Java over all companies (about 700; pure function, unit tested) instead of SQL like the users' list.
3. A holder that does not answer fails the list (502), not a list without counts: the suggested target depends on the partner flag.
4. Bruno: read-only list and same-company preview only.

**Store sizes at start:** CUMS archunit 0 violations; ratchet baseline 528 lines; response-conformance baselines unchanged.

## Verification

**Commands:**
- `./gradlew :services:event-management-service:test --tests '*CompanyReference*' --tests '*S2sProviderContract*' --max-workers=4`, then the same for `:services:partner-coordination-service` -- green
- `./gradlew :services:company-user-management-service:test --tests '*CompanyDuplicate*' --tests '*CompanyReferenceContract*' --tests '*CompanyMerge*' --max-workers=4` -- green
- Full builds, one after another: CUMS, EMS, PCS `build --max-workers=4` -- green
- `cd web-frontend && npm run generate:api-types && npm run type-check`; `make check-test-ratchet`; `make check-frontend-api-paths` -- clean
