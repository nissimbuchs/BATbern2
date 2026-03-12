# Story 10.27: iCal RSVP Tracking for Partner Meeting Invites

Status: done

<!-- Prerequisites:
  - Story 8.3 (Partner Meeting Coordination) MUST be complete — PartnerMeeting, IcsGeneratorService,
    PartnerMeetingService, PartnerInviteEmailService must exist in their current form.
  - Story 10.17 (Inbound Email Handler) MUST be complete — InboundEmailListenerService,
    InboundEmailRouter, and the SES→S3→SQS pipeline must be deployed and operational.
-->

## Story

As an **organizer**,
I want to see which partners and organizers have accepted, declined, or not yet responded to the
partner meeting calendar invite,
so that I can plan logistics and follow up with non-responders.

## Acceptance Criteria

1. **AC1 — ATTENDEE fields in outgoing ICS**: The partner meeting VEVENT in the generated ICS
   includes one `ATTENDEE;PARTSTAT=NEEDS-ACTION;RSVP=TRUE:mailto:{email}` line per recipient.
   Without ATTENDEE fields, calendar clients do not send RSVP replies.

2. **AC2 — ORGANIZER uses monitored address**: The `ORGANIZER` property in the partner meeting
   VEVENT is `ORGANIZER:mailto:replies@batbern.ch` (from `app.email.reply-to` config, not
   `app.email.from`). Calendar clients send RSVP replies to the ORGANIZER address.

3. **AC3 — iCal REPLY detection in `InboundEmailListenerService`**: When the raw email contains a
   MIME part with content type `text/calendar` and the iCal body contains `METHOD:REPLY`,
   the service extracts:
   - `UID` — the raw UID value (e.g. `11111111-...@batbern.ch`)
   - `ATTENDEE` email — the address after `mailto:` on the ATTENDEE line
   - `PARTSTAT` — one of `ACCEPTED`, `DECLINED`, `TENTATIVE`
   and calls `router.routeIcsReply(IcsReply)` instead of the existing `router.route(ParsedEmail)`.
   If the iCal cannot be parsed or required fields are missing, log WARN and discard silently.
   The existing plain-text routing path is unchanged.

4. **AC4 — `InboundEmailRouter.routeIcsReply()` routes to partner RSVP service**: A new
   `routeIcsReply(IcsReply icsReply)` method on `InboundEmailRouter` calls
   `partnerMeetingRsvpClient.recordRsvp(meetingId, attendeeEmail, partStat)` where
   `meetingId` is the UUID parsed from the UID (`{uuid}@batbern.ch` → take the prefix before `@`).
   If the UID does not match the `@batbern.ch` pattern or the UUID is malformed, log WARN and
   discard. Rate limiter is applied before routing (same as plain-text path).

5. **AC5 — `partner_meeting_rsvps` table**: A V9 Flyway migration in partner-coordination-service
   creates:
   ```sql
   CREATE TABLE partner_meeting_rsvps (
       id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
       meeting_id      UUID NOT NULL REFERENCES partner_meetings(id) ON DELETE CASCADE,
       attendee_email  VARCHAR(255) NOT NULL,
       status          VARCHAR(20)  NOT NULL
                           CHECK (status IN ('ACCEPTED', 'DECLINED', 'TENTATIVE')),
       responded_at    TIMESTAMP WITH TIME ZONE NOT NULL,
       created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
       UNIQUE (meeting_id, attendee_email)
   );
   CREATE INDEX idx_partner_meeting_rsvps_meeting ON partner_meeting_rsvps(meeting_id);
   ```
   The `UNIQUE(meeting_id, attendee_email)` constraint enables upsert: if an attendee re-responds
   (e.g. TENTATIVE → ACCEPTED after a calendar update), their record is updated in-place.

6. **AC6 — Internal RSVP endpoint in partner-coordination-service**:
   `POST /internal/partner-meetings/rsvps` accepts:
   ```json
   { "meetingId": "uuid", "attendeeEmail": "user@example.com", "partStat": "ACCEPTED" }
   ```
   Upserts into `partner_meeting_rsvps`. Returns `200 OK` with the upserted record.
   Returns `404` if `meetingId` does not exist. Returns `400` if `partStat` is unknown.
   The endpoint is `permitAll()` — secured by VPC/Service Connect (not publicly routable),
   consistent with the email forwarder Lambda pattern.

7. **AC7 — GET RSVP list for organizers**:
   `GET /api/v1/partner-meetings/{id}/rsvps` requires `ORGANIZER` role and returns:
   ```json
   {
     "meetingId": "uuid",
     "inviteSentAt": "2026-05-01T10:00:00Z",
     "rsvps": [
       { "attendeeEmail": "alice@partner.com", "status": "ACCEPTED",  "respondedAt": "..." },
       { "attendeeEmail": "bob@partner.com",   "status": "DECLINED",  "respondedAt": "..." },
       { "attendeeEmail": "carl@partner.com",  "status": "TENTATIVE", "respondedAt": "..." }
     ],
     "summary": { "accepted": 1, "declined": 1, "tentative": 1 }
   }
   ```
   Returns `404` if the meeting does not exist. Returns `403` if caller is not ORGANIZER.

8. **AC8 — Frontend: RSVP status panel in partner meeting detail**:
   The partner meeting detail view (organizer-only) shows an "Attendee Responses" section below
   the invite details, visible only after `inviteSentAt` is non-null. It displays:
   - A summary row: "X Accepted · Y Declined · Z Tentative"
   - A grouped list with colour-coded chips: green (ACCEPTED), red (DECLINED), amber (TENTATIVE)
   - Each row shows the attendee email and response timestamp
   The section auto-refreshes when the organizer triggers a send-invite.

9. **AC9 — TDD compliance**: All new logic is covered by tests before implementation:
   - `IcsGeneratorServiceTest`: ATTENDEE fields present for each recipient; ORGANIZER uses
     `replies@batbern.ch`
   - `InboundEmailListenerServiceTest`: iCal REPLY MIME part detected and parsed correctly;
     plain-text path unchanged; malformed iCal discarded with WARN
   - `InboundEmailRouterTest`: `routeIcsReply()` routes to `PartnerMeetingRsvpClient`; unknown
     UID prefix discarded; rate limiter respected
   - `PartnerMeetingRsvpControllerIntegrationTest`: internal upsert endpoint; GET list endpoint;
     role-based access
   - Frontend: `PartnerMeetingRsvpPanel.test.tsx` covers RSVP panel rendering

---

## Tasks / Subtasks

### Phase 1: ICS changes — partner-coordination-service (TDD First)

- [x] **T1 — Update `IcsGeneratorServiceTest` (RED phase)** (AC: #1, #2, #9)
  - [x] T1.1 — Add test: generated ICS contains `ATTENDEE;PARTSTAT=NEEDS-ACTION;RSVP=TRUE:mailto:contact@partner.com` for each recipient passed in
  - [x] T1.2 — Add test: generated ICS contains `ORGANIZER:mailto:replies@batbern.ch`
  - [x] T1.3 — Add test: no recipients → no ATTENDEE lines (empty list is valid)
  - [x] T1.4 — Run to confirm RED: `./gradlew :services:partner-coordination-service:test --tests "*.IcsGeneratorServiceTest" 2>&1 | tee /tmp/test-10-27-ics-red.log`

- [x] **T2 — Update `IcsGeneratorService.generate()` signature and VEVENT** (AC: #1, #2)
  - [x] T2.1 — Change `generate(PartnerMeeting, EventSummaryDTO)` to `generate(PartnerMeeting, EventSummaryDTO, List<String> recipientEmails)`
  - [x] T2.2 — Change injected property for ORGANIZER from `${app.email.from:noreply@batbern.ch}` to `${app.email.reply-to:replies@batbern.ch}`
  - [x] T2.3 — In `buildVEvent` extraLines for the partner meeting VEVENT, add one `ATTENDEE;PARTSTAT=NEEDS-ACTION;RSVP=TRUE:mailto:{email}` line per recipient (after ORGANIZER line)
  - [x] T2.4 — Run tests GREEN: `./gradlew :services:partner-coordination-service:test --tests "*.IcsGeneratorServiceTest" 2>&1 | tee /tmp/test-10-27-ics-green.log`

- [x] **T3 — Update `PartnerMeetingService.sendInvite()` — collect emails before ICS generation** (AC: #1)
  - [x] T3.1 — Move `collectInviteRecipientEmails()` call to BEFORE `icsGeneratorService.generate()` (currently it is called after)
  - [x] T3.2 — Pass the collected emails list to `icsGeneratorService.generate(meeting, event, emails)`
  - [x] T3.3 — Run full partner-coordination-service test suite: `./gradlew :services:partner-coordination-service:test 2>&1 | tee /tmp/test-10-27-pcs-full.log && grep -E "PASSED|FAILED|BUILD" /tmp/test-10-27-pcs-full.log | tail -10`

### Phase 2: DB schema + API — partner-coordination-service (TDD First)

- [x] **T4 — Write `PartnerMeetingRsvpControllerIntegrationTest` (RED phase)** (AC: #5, #6, #7, #9)
  - [x] T4.1 — Create `services/partner-coordination-service/src/test/java/ch/batbern/partners/controller/PartnerMeetingRsvpControllerIntegrationTest.java`
    extending `AbstractIntegrationTest`
  - [x] T4.2 — Test (AC6): `POST /internal/partner-meetings/rsvps` with valid meetingId + attendeeEmail + ACCEPTED → 200, record persisted
  - [x] T4.3 — Test (AC6): same attendee posts TENTATIVE after ACCEPTED → status updated (upsert)
  - [x] T4.4 — Test (AC6): meetingId not found → 404
  - [x] T4.5 — Test (AC6): unknown partStat → 400
  - [x] T4.6 — Test (AC7): `GET /api/v1/partner-meetings/{id}/rsvps` as ORGANIZER after two RSVPs → returns both in response
  - [x] T4.7 — Test (AC7): as PARTNER → 403
  - [x] T4.8 — Run to confirm RED: compile error (classes not yet created)

- [x] **T5 — V9 Flyway migration** (AC: #5)
  - [x] T5.1 — Create `services/partner-coordination-service/src/main/resources/db/migration/V9__create_partner_meeting_rsvps.sql`
  - [x] T5.2 — Table: `partner_meeting_rsvps` per schema in AC5
  - [x] T5.3 — Index: `idx_partner_meeting_rsvps_meeting` on `meeting_id`

- [x] **T6 — `PartnerMeetingRsvp` entity + `PartnerMeetingRsvpRepository`** (AC: #5)
  - [x] T6.1 — Create `ch.batbern.partners.domain.PartnerMeetingRsvp` (`@Entity`, `@Table("partner_meeting_rsvps")`)
  - [x] T6.2 — Create `RsvpStatus` enum: `ACCEPTED`, `DECLINED`, `TENTATIVE`
  - [x] T6.3 — Create `PartnerMeetingRsvpRepository extends JpaRepository<PartnerMeetingRsvp, UUID>`

- [x] **T7 — `PartnerMeetingRsvpService`** (AC: #6, #7)
  - [x] T7.1 — Create `ch.batbern.partners.service.PartnerMeetingRsvpService`
  - [x] T7.2 — `upsertRsvp()` with find-then-save upsert pattern
  - [x] T7.3 — `getRsvps(UUID meetingId)`

- [x] **T8 — DTOs** (AC: #6, #7)
  - [x] T8.1 — `RecordRsvpRequest`
  - [x] T8.2 — `RsvpDTO`
  - [x] T8.3 — `MeetingRsvpListResponse`
  - [x] T8.4 — `RsvpSummary`

- [x] **T9 — `PartnerMeetingRsvpController`** (AC: #6, #7)
  - [x] T9.1 — Create controller
  - [x] T9.2 — `POST /internal/partner-meetings/rsvps` with 400 on unknown partStat
  - [x] T9.3 — `GET /api/v1/partner-meetings/{id}/rsvps` with ORGANIZER role

- [x] **T10 — Security config: permit `/internal/**`** (AC: #6)
  - [x] T10.1 — Located `SecurityConfig.java` in PCS
  - [x] T10.2 — Added `.requestMatchers("/internal/**").permitAll()` in production filter chain

- [x] **T11 — Run integration tests GREEN** (AC: #9)
  - [x] T11.1 — `PartnerMeetingRsvpControllerIntegrationTest`: 7/7 tests PASSED
  - [x] T11.2 — Full PCS suite: BUILD SUCCESSFUL

### Phase 3: EMS — iCal REPLY routing (TDD First)

- [x] **T12 — Write `InboundEmailListenerServiceTest` additions (RED phase)** (AC: #3, #9)
  - [x] T12.1 — Add test: MIME message containing `text/calendar; method=REPLY` part with valid UID + ATTENDEE:PARTSTAT=ACCEPTED
    → `router.routeIcsReply(icsReply)` called with correct `attendeeEmail`, `meetingUid`, `partStat=ACCEPTED`; `router.route()` NOT called
  - [x] T12.2 — Add test: MIME with `text/calendar; method=REPLY` but no `ATTENDEE` line → WARN logged, router NOT called
  - [x] T12.3 — Add test: MIME with `text/calendar; method=REPLY` but no `UID` → WARN logged, router NOT called
  - [x] T12.4 — Add test: plain-text email (no calendar part) → existing `router.route()` path unchanged
  - [x] T12.5 — Run to confirm RED: `routeIcsReply` method does not exist yet

- [x] **T13 — Write `InboundEmailRouterTest` additions (RED phase)** (AC: #4, #9)
  - [x] T13.1 — Add test: `routeIcsReply()` with valid UID `abc123-...-uuid@batbern.ch`, ACCEPTED
    → `partnerMeetingRsvpClient.recordRsvp(uuid, email, ACCEPTED)` called
  - [x] T13.2 — Add test: UID does not end with `@batbern.ch` → WARN, client NOT called
  - [x] T13.3 — Add test: UID prefix is not a valid UUID → WARN, client NOT called
  - [x] T13.4 — Add test: rate limiter blocks → client NOT called
  - [x] T13.5 — Run to confirm RED: `PartnerMeetingRsvpClient` and `routeIcsReply` do not exist yet

- [x] **T14 — Add `PartnerMeetingRsvpClient` to EMS** (AC: #4)
  - [x] T14.1 — Create `services/event-management-service/src/main/java/ch/batbern/events/client/PartnerMeetingRsvpClient.java` (interface)
    - `void recordRsvp(UUID meetingId, String attendeeEmail, String partStat)`
  - [x] T14.2 — Create `PartnerMeetingRsvpClientImpl` using `RestTemplate`
  - [x] T14.3 — `partner-coordination-service.base-url` already configured in `application.yml`
  - [x] T14.4 — Client is mocked in router tests

- [x] **T15 — Add `IcsReply` record and `routeIcsReply()` to `InboundEmailRouter`** (AC: #4)
  - [x] T15.1 — Added `public record IcsReply(String meetingUid, String attendeeEmail, String partStat) {}`
  - [x] T15.2 — Added `private final PartnerMeetingRsvpClient partnerMeetingRsvpClient;`
  - [x] T15.3 — Implemented `routeIcsReply()` with rate limit, UID validation, UUID parse, recordRsvp call

- [x] **T16 — Update `InboundEmailListenerService` to detect iCal REPLY MIME parts** (AC: #3)
  - [x] T16.1 — Added `findCalendarReplyText()` walking MIME tree
  - [x] T16.2 — In `handleS3Notification()`: calls `findCalendarReplyText()`, routes to `routeIcsReply()` or plain-text
  - [x] T16.3 — RFC 5545 line unfolding via `replaceAll("\r\n[ \t]", "")`
  - [x] T16.4 — `PARTSTAT` extracted via regex `PARTSTAT=([A-Z\-]+)`

- [x] **T17 — Run EMS tests GREEN** (AC: #9)
  - [x] T17.1 — `InboundEmailListenerServiceTest`: 8/8 tests PASSED
  - [x] T17.2 — `InboundEmailRouterTest`: 18/18 tests PASSED
  - [x] T17.3 — Full EMS suite: BUILD SUCCESSFUL

### Phase 4: Frontend (TDD First)

- [x] **T18 — Add `getRsvps()` to `partnerMeetingsApi.ts`** (AC: #7)
  - [x] T18.1 — Added `getRsvps(meetingId: string): Promise<MeetingRsvpListResponse>`
  - [x] T18.2 — Added TypeScript types: `RsvpDTO`, `MeetingRsvpListResponse`, `RsvpSummary`
  - [x] T18.3 — Added 3 unit tests to `partnerMeetingsApi.test.ts` — 14/14 PASSED

- [x] **T19 — `PartnerMeetingRsvpPanel` component** (AC: #8)
  - [x] T19.1 — Created `web-frontend/src/components/organizer/PartnerMeetingRsvpPanel.tsx`
  - [x] T19.2 — Props: `meetingId`, `inviteSentAt`, `refreshKey?`
  - [x] T19.3 — Returns null when `inviteSentAt` is null
  - [x] T19.4 — useQuery with queryKey `[meetingRsvps, meetingId, refreshKey]`
  - [x] T19.5 — Loading spinner + error Alert
  - [x] T19.6 — Chips: success/error/warning for ACCEPTED/DECLINED/TENTATIVE
  - [x] T19.7 — refreshKey in queryKey triggers re-fetch

- [x] **T20 — Write `PartnerMeetingRsvpPanel.test.tsx` (RED then GREEN)** (AC: #9)
  - [x] T20.1 — inviteSentAt null → empty DOM
  - [x] T20.2 — renders summary with counts
  - [x] T20.3 — ACCEPTED chip has colorSuccess class
  - [x] T20.4 — loading spinner visible while fetching
  - [x] All 4/4 PASSED

- [x] **T21 — Integrate `PartnerMeetingRsvpPanel` into `MeetingDetailPanel.tsx`** (AC: #8)
  - [x] T21.1 — Integrated into `MeetingDetailPanel.tsx`
  - [x] T21.2 — Added `<PartnerMeetingRsvpPanel>` below Dialog
  - [x] T21.3 — Added `rsvpRefreshKey` state
  - [x] T21.4 — `inviteMutation.onSuccess` increments `rsvpRefreshKey`

### Phase 5: Full Validation

- [x] **T22 — Checkstyle + full build** (AC: #9)
  - [x] T22.1 — PCS checkstyleMain + checkstyleTest: BUILD SUCCESSFUL (removed unused Arrays import)
  - [x] T22.2 — EMS checkstyleMain + checkstyleTest: BUILD SUCCESSFUL
  - [x] T22.3 — TypeScript type-check: clean (0 errors)
  - [x] T22.4 — All tests pass: PCS 7/7 integration, EMS BUILD SUCCESSFUL, FE 18/18

---

## Architecture Notes

### Data Flow

```
Recipient accepts invite in calendar client
  → Calendar client sends email to ORGANIZER address (replies@batbern.ch)
  → SES receipt rule (Story 10.17) → S3 bucket → SQS queue
  → InboundEmailListenerService.handleS3Notification()
  → extractIcsReply(message) detects text/calendar; method=REPLY
  → router.routeIcsReply(IcsReply)
  → PartnerMeetingRsvpClient.recordRsvp()
  → POST /internal/partner-meetings/rsvps (partner-coordination-service)
  → PartnerMeetingRsvpService.upsertRsvp()
  → partner_meeting_rsvps table (upsert on meeting_id + attendee_email)

Organizer views partner meeting detail
  → GET /api/v1/partner-meetings/{id}/rsvps
  → PartnerMeetingRsvpPanel shows grouped list
```

### Why ATTENDEE fields are required

RFC 5545 §3.8.4.1: `ATTENDEE` is required in a `METHOD:REQUEST` VEVENT for calendar clients to
send back a `METHOD:REPLY`. Without it:
- Outlook: shows Accept/Decline buttons but does NOT send a reply email
- Gmail: processes the invite silently, no reply sent
- macOS Calendar: shows the invite but does not send a reply

### ORGANIZER address change

`ORGANIZER:mailto:replies@batbern.ch` (not `noreply@batbern.ch`). RFC 5545 specifies that
`METHOD:REPLY` emails are sent to the `ORGANIZER` address, not to the email `Reply-To` header.
The inbound pipeline already monitors `replies@batbern.ch` (Story 10.17 AC1).

In `IcsGeneratorService`, change:
```java
// Before
@Value("${app.email.from:noreply@batbern.ch}")
private String organizerEmail;

// After
@Value("${app.email.reply-to:replies@batbern.ch}")
private String organizerEmail;
```

### ICS ATTENDEE line format

```
ATTENDEE;CN=Partner Name;PARTSTAT=NEEDS-ACTION;RSVP=TRUE:mailto:partner@example.com
```

Minimal form sufficient for calendar client compliance:
```
ATTENDEE;PARTSTAT=NEEDS-ACTION;RSVP=TRUE:mailto:partner@example.com
```

`RSVP=TRUE` instructs the client to send a reply. `PARTSTAT=NEEDS-ACTION` is the initial state.

### Checkstyle: parameter count

`buildVEvent()` in `IcsGeneratorService` is currently at the 7-parameter Checkstyle limit.
ATTENDEE lines go into the existing `List<String> extraLines` parameter — no signature change needed.

### iCal REPLY parsing — key properties to extract

```
BEGIN:VCALENDAR
METHOD:REPLY
BEGIN:VEVENT
 UID:11111111-1111-1111-1111-111111111111@batbern.ch
ATTENDEE;PARTSTAT=ACCEPTED;CN=Alice:mailto:alice@partner.com
DTSTART:20260514T100000Z
END:VEVENT
END:VCALENDAR
```

Properties to extract:
- `UID:` → value is the raw UID string
- `ATTENDEE` → `PARTSTAT=([A-Z-]+)` via regex for status; `mailto:(.+)$` for email
- `METHOD:` → must equal `REPLY` (discard if not)

Handle RFC 5545 line folding before parsing: replace `\r\n\s` (CRLF followed by a space or tab)
with empty string.

### Cross-service dependency

EMS gains a new dependency on partner-coordination-service (one-way: EMS → partner-coordination).
This is consistent with existing cross-service HTTP calls in the project (ADR-003).
The internal endpoint is not JWT-protected — it is only accessible within the ECS Service Connect
private DNS namespace (`http://partner-coordination:8080`), not via the public API Gateway.

### Upsert behaviour

If an attendee re-responds (e.g. changes TENTATIVE → ACCEPTED after a calendar update when the
organizer resends with a higher SEQUENCE), the `UNIQUE(meeting_id, attendee_email)` constraint
enables an upsert. Use Spring Data's `@Modifying` + `@Query` with `ON CONFLICT DO UPDATE` or
find-then-save pattern in `PartnerMeetingRsvpService.upsertRsvp()`.

---

## Dev Notes

### 🚨 Critical Gotchas — Read Before Starting

#### 1. NO JWT in SQS/async context
`InboundEmailRouter.routeIcsReply()` is called from `InboundEmailListenerService`, which runs in
a Spring Cloud AWS `@SqsListener` — an **asynchronous** background thread with no HTTP request
and no JWT in `SecurityContextHolder`.

**DO NOT** use `createHeadersWithJwtToken()` in `PartnerMeetingRsvpClientImpl`. The
`SecurityContextHolder.getContext().getAuthentication()` will be null or anonymous. Instead,
send the POST **without** an Authorization header — the `/internal/**` endpoint is `permitAll()`.

Contrast with `PartnerApiClientImpl` (which runs in an HTTP request context and CAN propagate
the JWT). The new RSVP client is structurally different.

#### 2. `partner-coordination-service.base-url` ALREADY configured in EMS
Check `services/event-management-service/src/main/resources/application.yml` line 95:
```yaml
partner-coordination-service:
  base-url: ${PARTNER_COORDINATION_SERVICE_URL:http://partner-coordination:8080}
```
**T14.3 is pre-done.** The `PartnerMeetingRsvpClientImpl` should inject:
```java
@Value("${partner-coordination-service.base-url:http://partner-coordination:8080}")
private String partnerCoordinationBaseUrl;
```
Note: `PartnerApiClientImpl` uses a DIFFERENT key (`partner-service.base-url`) — do NOT reuse
that field. Use the separate `partner-coordination-service.base-url`.

#### 3. Latest Flyway migration in PCS is V8
`V8__add_invite_sequence.sql` (adds `invite_sequence` column). Next migration MUST be `V9__create_partner_meeting_rsvps.sql`.

#### 4. `buildVEvent()` parameter limit is exactly 7 (Checkstyle)
Do NOT add a new parameter for recipient emails. Per the existing architecture note:
ATTENDEE lines go into the existing `List<String> extraLines` parameter.
Only the `generate()` method signature changes (adds `List<String> recipientEmails`); inside
`generate()`, build the ATTENDEE lines and append them to the `extraLines` list before calling
`buildVEvent()`.

#### 5. Frontend target: `MeetingDetailPanel.tsx`, NOT a detail page
There is no `PartnerMeetingDetailPage.tsx`. The meeting detail is an **expandable row panel**:
- `PartnerMeetingsPage.tsx` — table of meetings with expandable rows
- `MeetingDetailPanel.tsx` — the expanded panel with agenda, notes, send-invite

The RSVP panel integrates into `MeetingDetailPanel.tsx`. It already has `inviteSuccess` state
and `inviteMutation` — use these to trigger the refresh.

#### 6. `inviteSentAt` already exists on `PartnerMeetingDTO`
Both the Java DTO (`ch.batbern.partners.dto.PartnerMeetingDTO`) and the generated TypeScript type
(`partner-meetings-api.types.ts` line 123: `inviteSentAt?: string | null`) already have this
field. No schema changes needed to show/hide the RSVP panel.

### Project Structure

```
services/partner-coordination-service/
├── src/main/java/ch/batbern/partners/
│   ├── controller/PartnerMeetingRsvpController.java         ← NEW
│   ├── domain/PartnerMeetingRsvp.java                       ← NEW
│   ├── domain/RsvpStatus.java                               ← NEW (enum)
│   ├── dto/RecordRsvpRequest.java                           ← NEW
│   ├── dto/RsvpDTO.java                                     ← NEW
│   ├── dto/MeetingRsvpListResponse.java                     ← NEW
│   ├── dto/RsvpSummary.java                                 ← NEW
│   ├── repository/PartnerMeetingRsvpRepository.java         ← NEW
│   └── service/PartnerMeetingRsvpService.java               ← NEW
├── src/main/resources/db/migration/
│   └── V9__create_partner_meeting_rsvps.sql                 ← NEW
└── src/test/java/ch/batbern/partners/controller/
    └── PartnerMeetingRsvpControllerIntegrationTest.java     ← NEW

services/event-management-service/
└── src/main/java/ch/batbern/events/client/
    ├── PartnerMeetingRsvpClient.java                        ← NEW (interface)
    └── impl/PartnerMeetingRsvpClientImpl.java               ← NEW

web-frontend/src/components/organizer/
├── PartnerMeetingRsvpPanel.tsx                              ← NEW
└── PartnerMeetingRsvpPanel.test.tsx                         ← NEW
```

### Modified Files

```
services/partner-coordination-service/
├── src/main/java/ch/batbern/partners/service/IcsGeneratorService.java
│     organizerEmail → app.email.reply-to; generate() adds List<String> recipientEmails param
├── src/main/java/ch/batbern/partners/service/PartnerMeetingService.java
│     sendInvite(): move email collection BEFORE ICS generation; pass emails to generate()
├── src/main/java/ch/batbern/partners/config/SecurityConfig.java
│     add permitAll() for /internal/** in the !local & !test profile
└── src/test/java/ch/batbern/partners/service/IcsGeneratorServiceTest.java
      new ATTENDEE + ORGANIZER tests

services/event-management-service/
├── src/main/java/ch/batbern/events/service/InboundEmailRouter.java
│     add IcsReply record + routeIcsReply() method + PartnerMeetingRsvpClient dependency
├── src/main/java/ch/batbern/events/service/InboundEmailListenerService.java
│     add extractIcsReply(); call routeIcsReply() when iCal REPLY detected
├── src/test/java/ch/batbern/events/service/InboundEmailRouterTest.java
│     new routeIcsReply() tests (add @Mock PartnerMeetingRsvpClient to the mock list)
└── src/test/java/ch/batbern/events/service/InboundEmailListenerServiceTest.java
      new iCal REPLY detection tests

web-frontend/
├── src/services/api/partnerMeetingsApi.ts    ← add getRsvps() + TypeScript types
└── src/components/organizer/MeetingDetailPanel.tsx
      integrate PartnerMeetingRsvpPanel + rsvpRefreshKey state
```

### PCS Integration Test Pattern

Follow `PartnerMeetingControllerIntegrationTest.java` exactly:
```java
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestAwsConfig.class, TestSecurityConfig.class})
@Transactional
class PartnerMeetingRsvpControllerIntegrationTest extends AbstractIntegrationTest {
    // AbstractIntegrationTest is from shared-kernel: ch.batbern.shared.test.AbstractIntegrationTest
    // @MockitoBean for EventManagementClient and UserServiceClient (external services)
```

The `TestSecurityConfig` provides ORGANIZER/PARTNER roles for tests. Check
`PartnerMeetingControllerIntegrationTest` for how the test sets `@WithMockUser(roles = "ORGANIZER")`.

### EMS HTTP Client Pattern

Follow `PartnerApiClientImpl` for structure but WITHOUT JWT propagation:
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class PartnerMeetingRsvpClientImpl implements PartnerMeetingRsvpClient {

    private final RestTemplate restTemplate;

    @Value("${partner-coordination-service.base-url:http://partner-coordination:8080}")
    private String partnerCoordinationBaseUrl;

    @Override
    public void recordRsvp(UUID meetingId, String attendeeEmail, String partStat) {
        try {
            // NO JWT header — /internal/** is permitAll(), SQS context has no SecurityContext
            Map<String, Object> body = Map.of(
                "meetingId", meetingId,
                "attendeeEmail", attendeeEmail,
                "partStat", partStat
            );
            restTemplate.postForEntity(
                partnerCoordinationBaseUrl + "/internal/partner-meetings/rsvps",
                body,
                Void.class
            );
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Partner meeting {} not found in PCS — discarding RSVP", meetingId);
        } catch (Exception e) {
            log.error("Failed to record RSVP for meeting {} — discarding: {}", meetingId, e.getMessage());
            // Never rethrow — inbound processing must not fail
        }
    }
}
```

### IcsGeneratorService: How to add ATTENDEE lines

The `buildVEvent()` method accepts `List<String> extraLines` as its last parameter. This list
already contains the `ORGANIZER:mailto:...` line. **Append** ATTENDEE lines to it:

```java
// In generate(PartnerMeeting meeting, EventSummaryDTO event, List<String> recipientEmails)
List<String> extraLines = new ArrayList<>();
extraLines.add("ORGANIZER:mailto:" + organizerEmail);  // existing
for (String email : recipientEmails) {
    extraLines.add("ATTENDEE;PARTSTAT=NEEDS-ACTION;RSVP=TRUE:mailto:" + email);
}
// then pass extraLines to buildVEvent(...)
```

Use `IcsGeneratorServiceTest.java` pattern with `ReflectionTestUtils` to inject
`organizerEmail = "replies@batbern.ch"` in the new tests.

### Frontend: MeetingDetailPanel integration

`MeetingDetailPanel.tsx` already has:
- `inviteMutation` with `onSuccess` callback
- `inviteSuccess` state

Integration:
```tsx
const [rsvpRefreshKey, setRsvpRefreshKey] = useState(0);

// In inviteMutation.onSuccess:
onSuccess: () => {
  setInviteSuccess(true);
  setRsvpRefreshKey(k => k + 1);  // ← add this
  void queryClient.invalidateQueries(...)
}

// Below the send-invite section:
<PartnerMeetingRsvpPanel
  meetingId={meeting.id}
  inviteSentAt={meeting.inviteSentAt ?? null}
  refreshKey={rsvpRefreshKey}
/>
```

### Frontend: API service pattern

Follow the existing exports in `partnerMeetingsApi.ts` — use `apiClient` (Axios):
```ts
export type MeetingRsvpListResponse = {
  meetingId: string;
  inviteSentAt: string | null;
  rsvps: RsvpDTO[];
  summary: RsvpSummary;
};

export const getRsvps = async (meetingId: string): Promise<MeetingRsvpListResponse> => {
  const response = await apiClient.get<MeetingRsvpListResponse>(`/partner-meetings/${meetingId}/rsvps`);
  return response.data;
};
```
Note: `apiClient` already has `/api/v1` as baseURL — do NOT duplicate it in the path.

### iCal MIME content-type detection

When walking MIME parts, the content type will be something like:
`text/calendar; charset="utf-8"; method=REPLY`

Use `MimePart.getContentType()` (returns the full content-type string). Check:
1. Content type starts with `text/calendar` (case-insensitive)
2. Content type contains `method=REPLY` OR the iCal body contains `METHOD:REPLY`

The body check is more reliable (content-type params can be missing on some clients).

### PCS SecurityConfig: `/internal/**` permit

In `SecurityConfig.java` (partner-coordination-service), inside the `@Profile("!local & !test")` security bean:
```java
.requestMatchers("/internal/**").permitAll()  // VPC-internal only (Service Connect)
// ... before the authenticated rules
```
This mirrors what was done for CUMS in Story 10.26 (`GET /api/v1/users` permitAll).

### References

- `IcsGeneratorService.java` [Source: services/partner-coordination-service/src/main/java/ch/batbern/partners/service/IcsGeneratorService.java]
- `PartnerMeetingService.java` [Source: services/partner-coordination-service/src/main/java/ch/batbern/partners/service/PartnerMeetingService.java]
- `IcsGeneratorServiceTest.java` [Source: services/partner-coordination-service/src/test/java/ch/batbern/partners/service/IcsGeneratorServiceTest.java] — use as test pattern
- `InboundEmailListenerService.java` [Source: services/event-management-service/src/main/java/ch/batbern/events/service/InboundEmailListenerService.java]
- `InboundEmailRouter.java` [Source: services/event-management-service/src/main/java/ch/batbern/events/service/InboundEmailRouter.java] — add `IcsReply` record and `routeIcsReply()` here
- `PartnerApiClientImpl.java` [Source: services/event-management-service/src/main/java/ch/batbern/events/client/impl/PartnerApiClientImpl.java] — structural template for `PartnerMeetingRsvpClientImpl` (but no JWT!)
- `PartnerMeetingControllerIntegrationTest.java` [Source: services/partner-coordination-service/src/test/java/ch/batbern/partners/controller/PartnerMeetingControllerIntegrationTest.java] — PCS integration test pattern
- `SecurityConfig.java` (PCS) [Source: services/partner-coordination-service/src/main/java/ch/batbern/partners/config/SecurityConfig.java] — add `/internal/**` permitAll
- `MeetingDetailPanel.tsx` [Source: web-frontend/src/components/organizer/MeetingDetailPanel.tsx] — integration target
- `partnerMeetingsApi.ts` [Source: web-frontend/src/services/api/partnerMeetingsApi.ts] — add `getRsvps()` here
- `application.yml` (EMS) [Source: services/event-management-service/src/main/resources/application.yml, line 95] — `partner-coordination-service.base-url` already configured
- `V8__add_invite_sequence.sql` [Source: services/partner-coordination-service/src/main/resources/db/migration/V8__add_invite_sequence.sql] — latest migration, next must be V9
- Story 10.17 (done) [Source: _bmad-output/implementation-artifacts/10-17-email-reply-based-unsubscribe-deregistration.md] — InboundEmail infrastructure
- Story 10.26 (done) [Source: _bmad-output/implementation-artifacts/10-26-ses-email-forwarding-distribution-lists.md] — permitAll pattern for VPC-internal endpoints

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

### File List

**Phase 1–5 (Initial Implementation):**
- `services/partner-coordination-service/src/main/resources/db/migration/V9__partner_meeting_rsvps.sql`
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/domain/RsvpStatus.java`
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/domain/PartnerMeetingRsvp.java`
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/repository/PartnerMeetingRsvpRepository.java`
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/dto/RsvpDTO.java`
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/dto/RsvpSummary.java`
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/dto/MeetingRsvpListResponse.java`
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/dto/RecordRsvpRequest.java`
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/service/PartnerMeetingRsvpService.java`
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/controller/PartnerMeetingRsvpController.java`
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/service/IcsGeneratorService.java` (ATTENDEE+ORGANIZER lines)
- `services/partner-coordination-service/src/test/java/ch/batbern/partners/controller/PartnerMeetingRsvpControllerIntegrationTest.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/InboundEmailListenerService.java` (iCal REPLY detection)
- `services/event-management-service/src/main/java/ch/batbern/events/service/InboundEmailRouter.java` (routeIcsReply)
- `services/event-management-service/src/main/java/ch/batbern/events/client/PartnerMeetingRsvpClient.java`
- `services/event-management-service/src/test/java/ch/batbern/events/service/InboundEmailListenerServiceTest.java`
- `web-frontend/src/api/partnerMeetingsApi.ts` (RsvpDTO, MeetingRsvpListResponse, getRsvps)
- `web-frontend/src/api/partnerMeetingsApi.test.ts`
- `web-frontend/src/components/partners/PartnerMeetingRsvpPanel.tsx` (new)
- `web-frontend/src/components/partners/PartnerMeetingRsvpPanel.test.tsx` (new)
- `web-frontend/src/components/partners/MeetingDetailPanel.tsx` (rsvpRefreshKey + panel mount)
- `web-frontend/public/locales/en/partners.json`
- `web-frontend/public/locales/de/partners.json`
- `web-frontend/public/locales/fr/partners.json`
- `web-frontend/public/locales/es/partners.json`
- `web-frontend/public/locales/it/partners.json`
- `web-frontend/public/locales/nl/partners.json`
- `web-frontend/public/locales/fi/partners.json`
- `web-frontend/public/locales/ja/partners.json`
- `web-frontend/public/locales/rm/partners.json`
- `web-frontend/public/locales/gsw-BE/partners.json`

**Code Review Fixes (CR 10-27):**
- M1: `PartnerMeetingRsvpService.java` — `DataIntegrityViolationException` catch + retry (`doUpsert()`) for concurrent SQS delivery race condition
- M2: `IcsGeneratorService.java` — applied `foldLine()` to all `extraLines` (ATTENDEE/ORGANIZER) per RFC 5545 §3.1
- M3: `InboundEmailListenerService.java` — case-insensitive `METHOD:REPLY` check (`toUpperCase()`) per RFC 5545 §3.2
- M4: `PartnerMeetingRsvpController.java` — removed direct `PartnerMeetingRepository` injection (layer violation); `PartnerMeetingRsvpService.getMeetingRsvpResponse()` now owns all meeting+RSVP assembly
- M5: All 8 non-EN/DE locale files (`fr`, `es`, `it`, `nl`, `fi`, `ja`, `rm`, `gsw-BE`) — added `meetings.rsvp` i18n keys

### Completion Notes List

- All 7 PCS RSVP integration tests pass after M1+M4 refactoring
- EMS: 1595 tests pass (Gradle XML write error on 2 nested test classes is pre-existing infra issue, not a test failure)
- RFC 5545 line folding now applied uniformly to all VEVENT properties including ATTENDEE lines
- Race condition window closed: duplicate SQS deliveries for same attendee are handled via UNIQUE constraint + retry
