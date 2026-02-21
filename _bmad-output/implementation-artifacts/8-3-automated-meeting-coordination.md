# Story 8.3: Partner Meeting Coordination

Status: ready-for-dev

## Story

As an **organizer**,
I want to manage the Spring and Autumn partner meetings,
so that I can send calendar invites with the agenda and keep notes from each meeting.

## Context

Partner meetings always happen on the same day as the BATbern event itself, at lunch:
**Lunch (partner meeting) → Partner Meeting → BATbern Event**

The meeting date is therefore derived from the linked BATbern event — no scheduling logic needed.

## Acceptance Criteria

1. **AC1 - Meeting Record**: Organizer creates a partner meeting record linked to a BATbern event (by event code). Fields: date (auto-filled from event), start/end time, location, meeting type (Spring/Autumn).

2. **AC2 - Agenda**: Organizer writes the agenda as free text. The agenda is included in the calendar invite description.

3. **AC3 - Calendar Invite (ICS)**: Organizer clicks "Send Calendar Invite" — the system generates a standard `.ics` file (RFC 5545) containing **two VEVENTs**:
   - The partner lunch meeting (with agenda in description)
   - The BATbern event itself (as a reminder, read from event-management-service)

   The `.ics` is sent via email (AWS SES) to all partner contacts on record. No Outlook/Microsoft Graph integration — standard iCalendar format that any calendar app can import.

4. **AC4 - Meeting Notes**: After the meeting, organizer writes free-text notes in the meeting record. Notes are saved and visible to all organizers.

5. **AC5 - Meeting List**: Organizer sees a list of all partner meetings (past and upcoming) with their linked event, agenda preview, notes preview, and whether the invite was sent.

6. **AC6 - Role-Based Access**: Only organizers can create/edit meetings and send invites. Partners do not interact with this screen.

7. **AC7 - i18n**: All UI text in German (primary) and English (secondary).

8. **AC8 - Performance**: Page loads in <3 seconds. Invite email sent asynchronously (user sees confirmation immediately, email dispatched in background).

## Prerequisites & Integration with Story 8.0

**Story 8.0 (Partner Portal Shell) must be complete before this story.**

Story 8.3 is organizer-only — partners do not get a meetings page in the portal. However, Story 8.0's `PartnerDetailScreen` reuses `PartnerMeetingsTab`, which calls `usePartnerMeetings(companyName)`. That hook is currently a stub returning `[]`. **This story implements the real data** that populates that tab.

Integration points:
- **`usePartnerMeetings` hook** (already exists at `src/hooks/usePartnerMeetings.ts`): currently returns empty array — Task 2 of this story makes it call the real API endpoint, so meetings appear in both the organizer detail view (`/organizer/partners/:companyName` → Meetings tab) and the partner's own company detail view (`/partners/company` → Meetings tab)
- **Organizer portal page**: new route `/organizer/partner-meetings` → `PartnerMeetingsPage` — add to organizer nav alongside other organizer management screens
- Partners see upcoming meetings read-only in their company detail Meetings tab; they do not manage or create meetings

## What was deliberately cut

| Removed | Reason |
|---|---|
| Automated Spring/Autumn scheduling | Date comes from the BATbern event — no scheduling needed |
| Microsoft Graph / Outlook integration | Standard ICS works with any calendar app |
| RSVP tracking (attending/not attending) | Not needed |
| Automated reminder emails | Not needed |
| Attendance prediction | Not needed |
| Cancellation and rescheduling handling | Not needed |
| Pre-meeting pack compilation | Not needed |
| Material distribution | Not needed |
| Post-meeting follow-up email | Not needed |
| EventBridge scheduled rules | Not needed |
| meeting_rsvps table | Not needed |
| meeting_materials table | Not needed |
| Epic 5 topic capture integration | Not needed |

## Tasks / Subtasks

### Task 1: Check existing schema + DB migration (AC: 1, 2, 4)

- [ ] Check if `partner_meetings` table already exists from Story 2.7 migration
- [ ] If missing or schema differs, create `V8.3.1__create_partner_meetings_table.sql`:

```sql
CREATE TABLE IF NOT EXISTS partner_meetings (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    event_code      VARCHAR(100) NOT NULL,       -- ADR-003: links to BATbern event by code
    meeting_type    VARCHAR(50)  NOT NULL,        -- SPRING | AUTUMN
    meeting_date    DATE         NOT NULL,        -- same day as the BATbern event
    start_time      TIME         NOT NULL,        -- e.g. 12:00 (lunch start)
    end_time        TIME         NOT NULL,        -- e.g. 14:00 (before BATbern event)
    location        VARCHAR(500),                 -- venue (usually same as BATbern event)
    agenda          TEXT,                         -- free text, organizer writes this
    notes           TEXT,                         -- post-meeting notes, filled in after
    invite_sent_at  TIMESTAMP WITH TIME ZONE,     -- null until first invite sent
    created_by      VARCHAR(100) NOT NULL,        -- organizer username (ADR-003)
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_partner_meetings_event ON partner_meetings(event_code);
CREATE INDEX IF NOT EXISTS idx_partner_meetings_type  ON partner_meetings(meeting_type);
```

### Task 1b: Implement usePartnerMeetings hook (Story 8.0 integration)

- [ ] In `src/hooks/usePartnerMeetings.ts`: replace the stub empty-array return with a real API call to `GET /api/v1/partner-meetings?eventCode={eventCode}` or `GET /api/v1/partner-meetings` filtered by a company name lookup
  - The meetings list in `PartnerMeetingsTab` (used by both organizer detail view and partner portal company view via Story 8.0) will automatically show real data once this hook is wired
  - Cache: 5 minutes (read-only for partners)

### Task 2: OpenAPI Specification (AC: ALL — ADR-006)

- [ ] Add partner meeting endpoints to `docs/api/partner-analytics-api.openapi.yml` (or create `partner-meetings-api.openapi.yml`)
- [ ] Endpoints:
  - `GET  /api/v1/partner-meetings` — list all meetings (ORGANIZER)
  - `POST /api/v1/partner-meetings` — create meeting (ORGANIZER)
  - `GET  /api/v1/partner-meetings/{meetingId}` — get single meeting (ORGANIZER)
  - `PATCH /api/v1/partner-meetings/{meetingId}` — update agenda or notes (ORGANIZER)
  - `POST /api/v1/partner-meetings/{meetingId}/send-invite` — generate ICS and email it (ORGANIZER)
- [ ] DTOs: `PartnerMeetingDTO`, `CreateMeetingRequest`, `UpdateMeetingRequest`
- [ ] Generate TypeScript types: `npm run generate:api-types:partners`

### Task 3: ICS Generator (AC: 3)

- [ ] Create `IcsGeneratorService.java` — pure utility, no external dependencies needed (RFC 5545 is plain text)
- [ ] `generateMeetingInvite(PartnerMeeting meeting, EventSummaryDTO batbernEvent): byte[]`
- [ ] Output: `.ics` file with two VEVENTs:

```
BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//BATbern//Partner Meeting//EN
METHOD:REQUEST

BEGIN:VEVENT
UID:{meeting.id}@batbern.ch
DTSTART:{meeting.date}T{meeting.startTime}:00
DTEND:{meeting.date}T{meeting.endTime}:00
SUMMARY:BATbern Partner Meeting ({meeting.meetingType})
DESCRIPTION:{meeting.agenda}
LOCATION:{meeting.location}
END:VEVENT

BEGIN:VEVENT
UID:{batbernEvent.eventCode}-main@batbern.ch
DTSTART:{batbernEvent.eventDate}T{batbernEvent.startTime}:00
DTEND:{batbernEvent.eventDate}T{batbernEvent.endTime}:00
SUMMARY:{batbernEvent.title}
DESCRIPTION:BATbern Event - you are registered as a partner
LOCATION:{batbernEvent.venue}
END:VEVENT

END:VCALENDAR
```

- [ ] Encode timestamps in UTC (convert from Europe/Zurich)
- [ ] Unit test: `IcsGeneratorServiceTest.java` — verify output parses as valid iCalendar

### Task 4: EventManagementClient (for event details) (AC: 3)

- [ ] Reuse or extend existing `EventManagementClient.java` (introduced in Story 8.1)
- [ ] Add method: `getEventSummary(String eventCode): EventSummaryDTO`
  - Returns: title, eventDate, startTime, endTime, venue
- [ ] Cache result (Caffeine, 1 hour TTL) — event details don't change often

### Task 5: PartnerMeetingService (AC: 1–5)

- [ ] Create `PartnerMeetingService.java`
- [ ] `createMeeting(CreateMeetingRequest req, String organizerUsername)`
- [ ] `updateMeeting(UUID meetingId, UpdateMeetingRequest req)` — update agenda or notes
- [ ] `getMeetings(): List<PartnerMeetingDTO>` — all meetings, sorted by date descending
- [ ] `getMeeting(UUID meetingId): PartnerMeetingDTO`
- [ ] `sendInvite(UUID meetingId)`:
  - Load meeting + fetch BATbern event details via EventManagementClient
  - Generate ICS via IcsGeneratorService
  - Load all partner contact emails from `partner_contacts` table (already exists)
  - Send email via SES (async) with ICS attachment
  - Update `invite_sent_at` timestamp

### Task 6: Email sending (AC: 3, 8)

- [ ] Reuse existing `SesEmailService` or create `PartnerInviteEmailService.java`
- [ ] `sendCalendarInvite(List<String> recipientEmails, String subject, String body, byte[] icsContent)`
  - Subject (DE): `"Einladung: BATbern Partner-Meeting + {eventTitle}"`
  - Body: short text with meeting details (plain text, no complex template)
  - Attachment: `partner-meeting.ics` with `Content-Type: text/calendar`
- [ ] Send asynchronously: `@Async` on the method, return immediately to controller
- [ ] SES configuration already exists in the project (used in Story 6.5)

### Task 7: PartnerMeetingController + SecurityConfig (AC: 6, 8)

- [ ] Create `PartnerMeetingController.java`
- [ ] All endpoints: `@PreAuthorize("hasRole('ORGANIZER')")`
- [ ] `POST /send-invite` returns `202 Accepted` immediately (async send)
- [ ] Add to `SecurityConfig.java`: `/api/v1/partner-meetings/**` → ORGANIZER

### Task 8: i18n Keys (AC: 7)

- [ ] Add keys to `public/locales/de/partner.json` and `en/partner.json`:
  - `partner.meetings.title`, `.create`, `.edit`
  - `partner.meetings.fields.agenda`, `.notes`, `.location`, `.type.spring`, `.type.autumn`
  - `partner.meetings.sendInvite`, `.inviteSent`, `.inviteNotSent`
  - `partner.meetings.inviteSentOn`, `partner.meetings.noMeetings`

### Task 8b: Wire organizer route into App.tsx

- [ ] Add `/organizer/partner-meetings` route (OrganizerRoute) → renders `PartnerMeetingsPage`
- [ ] Add "Partner Meetings" entry to the organizer navigation (wherever other organizer management items live — check existing organizer nav component)

### Task 9: Meeting List + Form (Organizer UI) (AC: 1, 2, 4, 5, 7, 8)

- [ ] Create `src/components/organizer/PartnerMeetingsPage.tsx`
- [ ] Meeting list: MUI Table — columns: Event, Type, Date, Location, Invite Sent, Actions
- [ ] "Create Meeting" button → opens `CreateMeetingDialog.tsx`
  - Fields: event code (linked BATbern event, dropdown or text), type (Spring/Autumn), start time, end time, location
  - Date auto-populated from selected event's date
- [ ] Click meeting row → expands inline or opens `MeetingDetailPanel.tsx`:
  - Agenda textarea (editable, auto-save on blur)
  - Notes textarea (editable, auto-save on blur)
  - "Send Calendar Invite" button with confirmation dialog
  - If `invite_sent_at` set: show "Invite sent on {date}" chip

### Task 10: API Client (AC: ALL)

- [ ] Create `src/services/api/partnerMeetingsApi.ts`
- [ ] `getMeetings()` — React Query, staleTime 5 minutes
- [ ] `createMeeting(req)` — mutation, invalidates list
- [ ] `updateMeeting(meetingId, req)` — mutation (agenda/notes), optimistic update
- [ ] `sendInvite(meetingId)` — mutation, shows success toast on 202

### Task 11: Backend Integration Tests (AC: 1–6)

- [ ] `PartnerMeetingControllerIntegrationTest.java` (extends `AbstractIntegrationTest`)
- [ ] Create meeting → verify persisted correctly
- [ ] Update agenda → verify saved
- [ ] Update notes → verify saved
- [ ] `POST /send-invite` → returns 202, verify `invite_sent_at` updated (mock SES)
- [ ] ICS content verified: contains both VEVENTs with correct times
- [ ] Non-organizer → 403 on all endpoints

### Task 12: Frontend Tests (AC: 7, 8)

- [ ] `PartnerMeetingsPage.test.tsx`
- [ ] Renders meeting list with mocked data
- [ ] Create meeting form validation
- [ ] "Send invite" button shows correct state (sent/not sent)
- [ ] i18n DE/EN

### Task 13: E2E Test (AC: 1, 2, 3, 4)

- [ ] `e2e/organizer/partner-meetings.spec.ts`
- [ ] Organizer creates meeting linked to event → appears in list
- [ ] Organizer writes agenda → saved on reload
- [ ] Organizer sends invite → success toast, "Invite sent on..." chip appears
- [ ] Organizer writes post-meeting notes → saved on reload

## Dev Notes

### ICS format — no library needed

RFC 5545 (iCalendar) is plain text. A simple `StringBuilder` in `IcsGeneratorService` is sufficient:

```java
public byte[] generate(PartnerMeeting meeting, EventSummaryDTO event) {
    ZoneId zurich = ZoneId.of("Europe/Zurich");
    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    String ics = "BEGIN:VCALENDAR\r\n" +
        "VERSION:2.0\r\n" +
        "PRODID:-//BATbern//Partner Meeting//EN\r\n" +
        "METHOD:REQUEST\r\n" +
        buildVEvent(
            meeting.getId() + "@batbern.ch",
            toUtc(meeting.getMeetingDate(), meeting.getStartTime(), zurich),
            toUtc(meeting.getMeetingDate(), meeting.getEndTime(), zurich),
            "BATbern Partner Meeting (" + meeting.getMeetingType() + ")",
            meeting.getAgenda(),
            meeting.getLocation(),
            fmt
        ) +
        buildVEvent(
            event.getEventCode() + "-main@batbern.ch",
            toUtc(event.getEventDate(), event.getStartTime(), zurich),
            toUtc(event.getEventDate(), event.getEndTime(), zurich),
            event.getTitle(),
            "BATbern Event",
            event.getVenue(),
            fmt
        ) +
        "END:VCALENDAR\r\n";

    return ics.getBytes(StandardCharsets.UTF_8);
}
```

### Email attachment (SES)

SES supports raw email with attachments via `SendRawEmailRequest`. The ICS file is attached with:
```
Content-Type: text/calendar; charset=utf-8; method=REQUEST
Content-Transfer-Encoding: base64
Content-Disposition: attachment; filename="partner-meeting.ics"
```

SES raw email sending is already used in the project (Story 6.5 reminder emails) — reuse the same pattern.

### Partner contact emails

Partner contacts are stored in `partner_contacts.username` (ADR-003 meaningful ID). To get emails, call `UserServiceClient.getUser(username).email()` — already implemented and cached from Story 2.7.

Or query directly: if `partner_contacts` stores email as a denormalized field, use that to avoid the HTTP call. Check existing schema.

### ADR Compliance

- **ADR-003**: `partner_meetings.event_code` (String), `created_by` (username) — no UUIDs across service boundaries
- **ADR-006**: OpenAPI spec created before implementation

### Project Structure

```
services/partner-coordination-service/
├── controller/
│   └── PartnerMeetingController.java
├── service/
│   ├── PartnerMeetingService.java
│   ├── IcsGeneratorService.java
│   └── PartnerInviteEmailService.java
├── domain/
│   ├── PartnerMeeting.java
│   └── MeetingType.java  (enum: SPRING, AUTUMN)
├── dto/
│   ├── PartnerMeetingDTO.java
│   ├── CreateMeetingRequest.java
│   └── UpdateMeetingRequest.java
└── repository/
    └── PartnerMeetingRepository.java

web-frontend/src/components/organizer/
├── PartnerMeetingsPage.tsx
├── CreateMeetingDialog.tsx
└── MeetingDetailPanel.tsx

web-frontend/src/services/api/
└── partnerMeetingsApi.ts
```

### Performance

| Metric | Target |
|--------|--------|
| Meeting list load (P95) | <3s |
| Create/update meeting | <500ms |
| Send invite (202 response) | <200ms (async) |
| ICS generation | <50ms |

### References

- [Source: docs/prd/epic-8-partner-coordination.md#Story-8.3]
- [Source: docs/architecture/ADR-003-meaningful-identifiers.md]
- [Source: docs/architecture/ADR-006-openapi-contract-first.md]
- [RFC 5545 — iCalendar format](https://datatracker.ietf.org/doc/html/rfc5545)
- [Source: services/partner-coordination-service — partner_contacts schema]
- [Source: Story 6.5 — existing SES email pattern]

## Dev Agent Record

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

### File List
