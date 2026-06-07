# file-upload-api collection

Bruno API contract tests for the `LogoController` (CUMS) — covers the presigned-URL
upload lifecycle and the cleanup-job endpoints.

## Audit pass (PR 3, plan §D.1)

This collection went through the light-audit checklist from
[`docs/plans/bruno-staging-hardening.md`](../../docs/plans/bruno-staging-hardening.md)
§D in PR 3:

| Audit step | Status |
|------------|--------|
| Verbs vs OpenAPI | ✅ Positive case for presigned-URL + confirm + delete + statistics + trigger; negative cases for invalid file type and oversize file. Untested: 401 on missing JWT, 403 on cleanup endpoints (organizer-only) — known gaps, not added in this pass. |
| Normalize prefix | ✅ Filenames moved from `test-logo-…` / `huge-logo.png` / `document.pdf` to canonical `bruno-test-<ts>.<ext>` per B1. |
| 00-/99- cleanup hooks | ❌ Skipped — see "Cleanup approach" below. |
| DELETE-then-GET-404 | ✅ via `03a-verify-logo-deleted.bru` (re-DELETE returns 404 because there's no `GET /logos/{id}` endpoint to assert against). |
| Run twice | ✅ Verified locally against `development`. |

## Cleanup approach

**This collection does NOT use the per-service cleanup endpoint
(`POST /api/v1/admin/test-fixtures/cums/cleanup`) for sweeping its test data.**
Two reasons:

1. The cleanup endpoint has no `uploads` entityType in PR 1 — wiring one up is
   deferred (see plan §B2 "Deferred — partner_meetings cleanup coverage" for
   the same shape of follow-up; also applies here).

2. **The file-upload lifecycle already handles cleanup automatically.** The
   `logos` table has an `expires_at` column populated at row-creation time:
   `PENDING` rows expire after 24h, `CONFIRMED` rows expire after 7d. The
   `cleanup/trigger` endpoint (exercised by test 06 in this collection)
   sweeps expired rows on demand. So a Bruno run that crashes mid-flow
   leaves at most one orphaned PENDING/CONFIRMED row, which is reclaimed by
   the next scheduled cleanup job within 24h.

For per-test cleanup, the `03-delete-unused-logo` test explicitly removes the
row created in tests 01-02. `03a-verify-logo-deleted` then asserts the
DELETE was real (re-DELETE returns 404).

## Test order

1. `01-request-presigned-url` — POST `/logos/presigned-url`, stores `testUploadId`.
2. `02-confirm-upload` — POST `/logos/{id}/confirm` (PENDING → CONFIRMED).
3. `03-delete-unused-logo` — DELETE `/logos/{id}`, asserts 204.
4. `03a-verify-logo-deleted` — DELETE `/logos/{id}` again, asserts 404 (cleanup proof).
5. `04-get-cleanup-statistics` — GET `/logos/cleanup/statistics`.
6. `05-trigger-manual-cleanup` — POST `/logos/cleanup/trigger`.
7. `06-invalid-file-type` — POST `/logos/presigned-url` with `application/pdf` → 400.
8. `07-file-size-exceeded` — POST `/logos/presigned-url` with 10 MB png → 400.

Tests 04-06 are independent of 01-03a's state and would still pass even if the
upload-confirm-delete cycle didn't run cleanly.
