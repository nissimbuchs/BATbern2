# partner-meetings-api

Bruno API contract tests for the PCS partner-meeting coordination endpoints
(`/api/v1/partner-meetings`, Story 8.3 / 10.27). Written from scratch in PR 13
(plan `docs/plans/bruno-staging-hardening.md` §D.11) — this collection did not
exist before.

## What it covers

| Seq | File | Exercises |
|-----|------|-----------|
| 0 | `00-pretest-cleanup.bru` | Sweep leftover `BRUNO-TEST-*` events from EMS (the fixture event's blast radius). |
| 1 | `01-create-fixture-event.bru` | Create the parent BATbern event in EMS (meeting date is auto-filled from it). |
| 2 | `02-create-meeting.bru` | `POST /partner-meetings` (SPRING) → 201; capture `createdMeetingId`. |
| 3 | `03-get-meeting.bru` | `GET /partner-meetings/{id}` → 200. |
| 4 | `04-get-meeting-not-found.bru` | `GET` random UUID → 404. |
| 5 | `05-list-meetings.bru` | `GET /partner-meetings` → 200, list contains the created meeting. |
| 6 | `06-update-meeting.bru` | `PATCH /partner-meetings/{id}` agenda + notes → 200. |
| 7 | `07-create-second-meeting.bru` | `POST` (AUTUMN) → 201; capture `secondMeetingId` (left undeleted to prove the posttest allowlist sweep). |
| 9 | `09-delete-meeting.bru` | `DELETE /partner-meetings/{id}` → 204. |
| 9.5 | `09a-verify-meeting-deleted.bru` | `GET` the deleted meeting → 404 (audit-step 4). |
| 10 | `10-unauthorized.bru` | `GET` with no auth → 401. |
| 11 | `11-forbidden-non-organizer.bru` | `POST` with a speaker JWT → 403. |
| 98 | `98-posttest-cleanup-meetings.bru` | PCS `meetings` cleanup with the captured id allowlist. |
| 99 | `99-delete-fixture-event.bru` | `DELETE` the EMS fixture event → 204/404. |

## ⚠️ Safety — never send real partner communications

`POST /partner-meetings/{id}/send-invite` is **deliberately NOT exercised** by this
collection. It generates an ICS and dispatches **real calendar invitations via SES to
every partner contact + organizer**, and deleting a meeting whose invite was sent fires
a **real METHOD:CANCEL cancellation** to the same recipients. Bruno runs against staging
— which **is** the production account (`api.batbern.ch`) — so exercising send-invite
would email real partners on every CI run. That must never happen.

send-invite (and the cancellation branch of delete) are covered at the unit/integration
layer instead, where the email service is mocked
(`PartnerMeetingService` tests, Story 8.3 / 10.27). **Rule: no Bruno/E2E test may trigger
real outbound email, invitations, or notifications against staging/production.**

## Cleanup model — id allowlist, not prefix (plan §B2 option 1)

`partner_meetings` is a **standalone** table with no Bruno-identifying column —
meetings are top-level events, not prefixable like `brtest*` partners or
`BRUNO-TEST-*` events. So this collection cannot use the prefix-sweep pattern the
other collections use, and there is **no `00-pretest` meetings sweep**: a prefix
sweep is impossible, and an id-allowlist sweep can only target IDs created *in
the current run* (Bruno env vars do not persist across separate `bru run`
invocations).

Instead, teardown is the explicit `09-delete-meeting.bru` DELETE plus the
`98-posttest-cleanup-meetings.bru` safety net, which posts the run's captured
meeting IDs to `POST /admin/test-fixtures/pcs/cleanup` with
`entityType=meetings`. A meeting only leaks if **both** its explicit DELETE and
the posttest sweep fail in the same run — at which point the ID is lost and no
future run can reclaim it. This is the documented trade-off of the allowlist
approach (chosen over a `created_at` window, which is unsafe on the shared
production account). `partner_meeting_attendance` + `partner_meeting_rsvps`
cascade-delete from `partner_meetings(id)` (V2 + V9 ON DELETE CASCADE).

## Auth

All endpoints require `ROLE_ORGANIZER` (`{{authToken}}`, the collection default).
`10-unauthorized` overrides to `auth: none`; `11-forbidden-non-organizer` uses
`{{speakerAuthToken}}`.
