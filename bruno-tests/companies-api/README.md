# companies-api collection

Bruno API contract tests for `CompanyController` (CUMS) — covers full CRUD
plus search, Swiss UID validation, and PUT/PATCH idempotency.

## Audit pass (PR 4, plan §D.2)

This collection went through the light-audit checklist from
[`docs/plans/bruno-staging-hardening.md`](../../docs/plans/bruno-staging-hardening.md)
§D in PR 4:

| Audit step | Status |
|------------|--------|
| Verbs vs OpenAPI | ✅ POST, GET-by-name, GET-list, PATCH, PUT, DELETE, search, UID-validate, verify all covered. |
| Normalize prefix | ✅ Company `name` field moved from `bruno-test-company-{{$timestamp}}` to canonical `BRUNOTESTCO{{$timestamp}}` per B1. Search query `bruno` → `BRUNOTESTCO`. Assertions updated to match. `website` URLs left as-is (not used for cleanup matching). |
| 00-/99- cleanup hooks | ✅ Added. Both call `POST /api/v1/admin/test-fixtures/cums/cleanup` with `entityType=companies, prefix=BRUNOTESTCO`. Status assertion `oneOf([200, 204, 404])` — never fails the run, gracefully no-ops if the endpoint isn't deployed yet (pre-PR-1 staging). |
| DELETE-then-GET-404 | ✅ Already present — `12-delete-company` (seq 15) is followed by `13-verify-deletion` (seq 16) which asserts 404 on the deleted name. |
| Run twice | ⏳ Pending — see commit message; will validate before opening the PR. |

## Cleanup approach

The CUMS cleanup endpoint sweeps any `companies.name LIKE 'BRUNOTESTCO%'` row
plus the soft-FK `logos.associated_entity_id LIKE 'BRUNOTESTCO%'` rows in the
same transaction. The 99-posttest-cleanup hook makes the collection
idempotent: even if a test mid-flow crashes, the next run starts clean.

## Test order

- `00-pretest-cleanup` — sweep leftover `BRUNOTESTCO*` companies (seq 0).
- `01-create-company` — POST `/companies`, stores `testCompanyName`.
- `02-get-company-by-id` — GET `/companies/{name}` (note: name, not UUID).
- `03-list-companies` — GET `/companies`.
- `04-update-company` — PATCH `/companies/{name}`.
- `05-search-companies` — GET `/companies/search?query=BRUNOTESTCO`.
- `08-validate-uid-valid` — POST `/companies/validate-uid` happy path.
- `09-validate-uid-invalid` — POST `/companies/validate-uid` rejection.
- `10-verify-company` — verification flow.
- `11-put-update-company` — PUT `/companies/{name}` (full replacement).
- `12-delete-company` — DELETE `/companies/{name}` (seq 15).
- `13-verify-deletion` — GET asserts 404 (seq 16).
- `99-posttest-cleanup` — final sweep (seq 99).

Numbering gaps (06, 07) are historical — tests were removed earlier and the
file numbers weren't backfilled.
