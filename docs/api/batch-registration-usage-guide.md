# Participant Import API Usage Guide

**Story**: BAT-12 (historical import), reshaped by Story 4.10c (2026-11)
**API Version**: v1
**Last Updated**: 2026-11-11

---

## Overview

Organizers import historical attendance **per event**. The import is the `people` mode of the one
participants create:

**Endpoint**: `POST /api/v1/events/{eventCode}/participants`

The same endpoint has two other modes, the single add (`username`) and the stakeholder enrolment
(`stakeholders: true`). A body carries exactly one mode; otherwise the answer is `400`. Contract:
`docs/api/event-registrations-api.openapi.yml` (`addParticipants`).

Until Story 4.10c the import was `POST /api/v1/events/batch_registrations`, one participant with
many events per call. That path is gone (no alias). The admin CSV import
(`ParticipantBatchImportModal`, `useParticipantBatchImport`) now regroups the CSV rows per event
and sends each event's people in chunks of 100, one request after the other.

---

## Authentication

**Required**: JWT Bearer token (AWS Cognito), role **ORGANIZER** (anonymous 401, other roles 403).

---

## Request

```json
{
  "people": [
    {
      "email": "hugo.muster@example.com",
      "firstName": "Hugo",
      "lastName": "Muster",
      "companyId": "musterag",
      "status": "ATTENDED"
    }
  ]
}
```

| Field | Required | Rule |
|-------|----------|------|
| `people` | yes | 1 to 100 entries |
| `email` | yes | one e-mail rule everywhere (#1102): ASCII local part, any domain, at most 254 characters |
| `firstName`, `lastName` | yes | 1 to 100 characters |
| `companyId` | no | company name, at most 100 characters |
| `status` | yes | `REGISTERED`, `ATTENDED` or `CANCELLED`; historical data uses `ATTENDED` |

---

## Behaviour

- Each person is looked up or created in CUMS by e-mail (`/internal/users/get-or-create`,
  `cognitoSync: false`, ADR-005: no login is created).
- A person already registered for the event is **skipped**; the existing row is not changed.
- Each person is saved on their own. A person whose lookup or save fails is reported `FAILED`
  (for example CUMS 409 `EMAIL_LINKED_TO_ACCOUNT`); the others stay.
- **No mail** is sent (the single add with `notify` is the only mode that mails).

---

## Response (200)

```json
{
  "enrolled": 1,
  "skipped": 1,
  "failed": 0,
  "results": [
    { "email": "hugo.muster@example.com", "username": "hugo.muster", "outcome": "REGISTERED" },
    { "email": "ines.muster@example.com", "username": "ines.muster", "outcome": "SKIPPED" }
  ]
}
```

`results` has one entry per person, in request order. `username` is absent when the user could not
be found or created; `reason` is present for `FAILED` only.

---

## Errors

| Status | When |
|--------|------|
| 400 | no mode or two modes, `stakeholders: false`, `force`/`notify` outside the single add, an empty list or more than 100 people, an invalid field |
| 401 / 403 | not signed in / not an organizer |
| 404 | unknown event |

---

## Testing

- EMS: `ParticipantsControllerIntegrationTest` (production security chain, CUMS as the
  `CumsContractHarness`, SES as the `SesRecorder`).
- Bruno: `bruno-tests/participants-api` (dev-only collection).
- Playwright: `e2e/organizer/participant-import.spec.ts` (local dev only).
