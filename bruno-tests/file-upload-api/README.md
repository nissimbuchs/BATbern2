# file-upload-api collection

Bruno API contract tests for the one upload contract (`docs/api/uploads-api.openapi.yml`, Story
17.11, ADR-002 decision 2026-11-07), purpose `company-logo`. The `/logos/*` operations it replaced
are gone; test 06 pins the old path at 404.

| Test | What |
|------|------|
| 01 | `POST /uploads/company-logo` reserves an upload: 201, presigned PUT URL, signed Content-Type |
| 02 | PUT of 4 bytes to the presigned URL (S3, MinIO locally); the size is signed into the URL |
| 03 | `POST /uploads/company-logo/{uploadId}/confirm` without target: 200 CONFIRMED, no file URL |
| 04 | invalid type (PDF): 400 |
| 05 | larger than 5 MB: 400 |
| 06 | removed `POST /logos/presigned-url`: 404 |
| 07 | unknown purpose (`mail-attachment`): 404 at the gateway |

Not here on purpose: a confirm with a `target` (it would replace a real company's logo; covered by
`UploadsControllerIntegrationTest` in CUMS and EMS) and the import from a URL (it fetches a third-party
URL; covered by the same tests for its refusals).

## Cleanup approach

No cleanup hook. An upload that no company takes expires on its own: the nightly upload expiry job
(`UploadExpiryJob`, 02:00 in CUMS) deletes PENDING rows after 24 h and CONFIRMED rows after 7 days,
with their S3 object. A Bruno run leaves at most one CONFIRMED row and one 4-byte temp object.

The per-service cleanup endpoint (`POST /api/v1/admin/test-fixtures/cums/cleanup`, entity type
`companies`) removes the uploads attached to test companies (`company_user_uploads.target_id`).
