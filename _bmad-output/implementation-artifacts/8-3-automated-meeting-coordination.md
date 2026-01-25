# Story 8.3: Automated Meeting Coordination

Status: ready-for-dev

## Story

As an **organizer**,
I want automated partner meeting scheduling with calendar integration,
so that seasonal meetings are coordinated efficiently without manual back-and-forth.

## Acceptance Criteria

### Automated Scheduling
1. **AC1 - Auto-Schedule**: Automatically schedule Spring (March) and Autumn (September) meetings 2+ months ahead
2. **AC2 - Calendar Integration**: Create calendar invites in organizer Outlook calendars via Microsoft Graph API
3. **AC3 - Availability Check**: Check organizer availability before scheduling
4. **AC4 - Conflict Resolution**: Detect and suggest alternatives for scheduling conflicts

### RSVP Management
5. **AC5 - RSVP Tracking**: Track partner RSVPs (ATTENDING, NOT_ATTENDING, TENTATIVE)
6. **AC6 - Reminder Emails**: Automated reminders at 2 weeks, 1 week, 3 days before meeting
7. **AC7 - Attendance Prediction**: Predict attendance based on past RSVP patterns
8. **AC8 - Last-Minute Changes**: Handle cancellations and rescheduling gracefully

### Meeting Materials
9. **AC9 - Automated Agenda**: Generate agenda from Story 8.1 analytics data
10. **AC10 - Pre-Meeting Pack**: Compile attendance stats, budget overview, voting results
11. **AC11 - Material Distribution**: Send materials to attendees 1 week before meeting
12. **AC12 - Post-Meeting Follow-Up**: Automated thank you and action item summary

### Integration with Epic 5
13. **AC13 - Hybrid Operation**: Epic 5 manual scheduling still works if Epic 8 automation unavailable
14. **AC14 - Meeting Notes Sync**: Automated meeting notes sync with Epic 5 Story 5.15 notes field
15. **AC15 - Topic Capture**: Topics from meeting auto-added to Epic 5 topic backlog

### Technical
16. **AC16 - ADR-003 Compliance**: Database uses meaningful IDs (companyName, username)
17. **AC17 - i18n Support**: All UI text translated (German/English)
18. **AC18 - Email Templates**: Separate de/en templates for all automated emails
19. **AC19 - Performance**: Meeting scheduling completes in <5 seconds

## Tasks / Subtasks

### Backend Tasks

- [ ] **Task 1: Update OpenAPI Specification** (AC: ALL - contract-first per ADR-006)
  - [ ] Update `docs/api/partners-api.openapi.yml` with meeting endpoints
  - [ ] Define `GET /api/v1/partner-meetings` endpoint
  - [ ] Define `POST /api/v1/partner-meetings` endpoint (auto-schedule)
  - [ ] Define `GET /api/v1/partner-meetings/{meetingId}` endpoint
  - [ ] Define `POST /api/v1/partner-meetings/{meetingId}/rsvp` endpoint
  - [ ] Define `POST /api/v1/partner-meetings/{meetingId}/materials` endpoint
  - [ ] Generate TypeScript types: `npm run generate:api-types:partners`

- [ ] **Task 2: Database Migrations** (AC: 16)
  - [ ] Create Flyway migration `V8.3.1__create_partner_meetings_table.sql`
    ```sql
    CREATE TABLE partner_meetings (
        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
        company_name VARCHAR(12) NOT NULL,
        meeting_type VARCHAR(50) NOT NULL,  -- SPRING, AUTUMN
        scheduled_at TIMESTAMP WITH TIME ZONE,
        organizer_username VARCHAR(100) NOT NULL,
        status VARCHAR(50) DEFAULT 'SCHEDULED',
        calendar_event_id VARCHAR(255),
        agenda_generated_at TIMESTAMP WITH TIME ZONE,
        materials_sent_at TIMESTAMP WITH TIME ZONE,
        created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
        updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
    );
    CREATE INDEX idx_meetings_company ON partner_meetings(company_name);
    CREATE INDEX idx_meetings_type ON partner_meetings(meeting_type);
    CREATE INDEX idx_meetings_scheduled ON partner_meetings(scheduled_at);
    ```
  - [ ] Create Flyway migration `V8.3.2__create_meeting_rsvps_table.sql`
    ```sql
    CREATE TABLE meeting_rsvps (
        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
        meeting_id UUID NOT NULL REFERENCES partner_meetings(id) ON DELETE CASCADE,
        attendee_username VARCHAR(100) NOT NULL,
        rsvp_status VARCHAR(50) NOT NULL,  -- ATTENDING, NOT_ATTENDING, TENTATIVE
        responded_at TIMESTAMP WITH TIME ZONE,
        reminder_sent_at TIMESTAMP WITH TIME ZONE,
        UNIQUE(meeting_id, attendee_username)
    );
    CREATE INDEX idx_rsvps_meeting ON meeting_rsvps(meeting_id);
    CREATE INDEX idx_rsvps_status ON meeting_rsvps(rsvp_status);
    ```
  - [ ] Create Flyway migration `V8.3.3__create_meeting_materials_table.sql`
    ```sql
    CREATE TABLE meeting_materials (
        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
        meeting_id UUID NOT NULL REFERENCES partner_meetings(id) ON DELETE CASCADE,
        material_type VARCHAR(50) NOT NULL,  -- AGENDA, ATTENDANCE_REPORT, VOTING_SUMMARY
        s3_key VARCHAR(500) NOT NULL,
        generated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
    );
    ```

- [ ] **Task 3: Microsoft Graph Integration** (AC: 2, 3, 4)
  - [ ] Create `MicrosoftGraphClient.java` for calendar API
  - [ ] Implement `createCalendarEvent(MeetingRequest request)`
  - [ ] Implement `checkAvailability(String username, LocalDateTime start, LocalDateTime end)`
  - [ ] Implement `updateCalendarEvent(String eventId, MeetingUpdate update)`
  - [ ] Implement `deleteCalendarEvent(String eventId)`
  - [ ] Configure Azure AD app registration for Graph API access

- [ ] **Task 4: Meeting Scheduling Service** (AC: 1, 3, 4, 8)
  - [ ] Create `PartnerMeetingSchedulingService.java`
  - [ ] Implement `scheduleSeasonalMeetings(MeetingType type)` - bulk scheduling
  - [ ] Implement `scheduleMeeting(String companyName, MeetingType type, LocalDateTime proposedTime)`
  - [ ] Implement `findAvailableSlots(String organizerUsername, LocalDate startDate, int daysToCheck)`
  - [ ] Implement `rescheduleMeeting(UUID meetingId, LocalDateTime newTime)`
  - [ ] Add EventBridge scheduled rule for auto-scheduling (January for Spring, July for Autumn)

- [ ] **Task 5: RSVP Service** (AC: 5, 6, 7)
  - [ ] Create `MeetingRsvpService.java`
  - [ ] Implement `recordRsvp(UUID meetingId, String username, RsvpStatus status)`
  - [ ] Implement `getRsvpSummary(UUID meetingId)` - counts by status
  - [ ] Implement `predictAttendance(UUID meetingId)` - based on historical patterns
  - [ ] Create EventBridge rule for reminder emails (2w, 1w, 3d triggers)

- [ ] **Task 6: Meeting Materials Service** (AC: 9, 10, 11, 12)
  - [ ] Create `MeetingMaterialsService.java`
  - [ ] Implement `generateAgenda(UUID meetingId)` - pulls from Story 8.1 analytics
  - [ ] Implement `compilePreMeetingPack(UUID meetingId)` - attendance, budget, voting
  - [ ] Implement `distributeMaterials(UUID meetingId)` - emails + S3 links
  - [ ] Implement `sendPostMeetingFollowUp(UUID meetingId, MeetingNotes notes)`

- [ ] **Task 7: Email Service with SES** (AC: 6, 11, 12, 18)
  - [ ] Create `MeetingEmailService.java`
  - [ ] Implement `sendMeetingInvitation(Meeting meeting, String language)`
  - [ ] Implement `sendRsvpReminder(Meeting meeting, List<String> recipients, String language)`
  - [ ] Implement `sendPreMeetingMaterials(Meeting meeting, String language)`
  - [ ] Implement `sendPostMeetingFollowUp(Meeting meeting, String language)`
  - [ ] Create SES email templates (see Task 12)

- [ ] **Task 8: REST Controller** (AC: 16, 19)
  - [ ] Create `PartnerMeetingController.java`
  - [ ] Implement all endpoints from OpenAPI spec
  - [ ] Add `@PreAuthorize` for role-based access
  - [ ] Add request timing metrics

- [ ] **Task 9: Epic 5 Integration** (AC: 13, 14, 15)
  - [ ] Create `Epic5IntegrationService.java`
  - [ ] Implement `syncMeetingNotesToEpic5(UUID meetingId, String notes)`
  - [ ] Implement `addTopicsToBacklog(UUID meetingId, List<String> topics)`
  - [ ] Publish events via EventBridge for Epic 5 consumption

- [ ] **Task 10: SecurityConfig Update** (AC: 16)
  - [ ] Add meeting endpoints to SecurityConfig
  - [ ] `GET /api/v1/partner-meetings` → PARTNER, ORGANIZER roles
  - [ ] `POST /api/v1/partner-meetings` → ORGANIZER role only
  - [ ] `POST /api/v1/partner-meetings/*/rsvp` → PARTNER role

### Frontend Tasks

- [ ] **Task 11: i18n Translation Keys** (AC: 17)
  - [ ] Add ~40 translation keys to `public/locales/de/partner.json`
  - [ ] Add ~40 translation keys to `public/locales/en/partner.json`

- [ ] **Task 12: Email Templates** (AC: 18)
  - [ ] Create `partner-meeting-invitation-de.html`
  - [ ] Create `partner-meeting-invitation-en.html`
  - [ ] Create `partner-meeting-reminder-de.html` (2w, 1w, 3d variants)
  - [ ] Create `partner-meeting-reminder-en.html`
  - [ ] Create `partner-meeting-materials-de.html`
  - [ ] Create `partner-meeting-materials-en.html`
  - [ ] Create `partner-meeting-followup-de.html`
  - [ ] Create `partner-meeting-followup-en.html`
  - [ ] Upload templates to AWS SES

- [ ] **Task 13: Meeting Dashboard Component** (AC: 1, 5)
  - [ ] Create `src/components/partner/PartnerMeetingsDashboard.tsx`
  - [ ] Display upcoming meetings with status
  - [ ] Show RSVP status and actions
  - [ ] Calendar view integration

- [ ] **Task 14: RSVP Component** (AC: 5, 8)
  - [ ] Create `src/components/partner/MeetingRsvpCard.tsx`
  - [ ] RSVP buttons (Attending, Not Attending, Tentative)
  - [ ] Show current RSVP status
  - [ ] Handle status changes with confirmation

- [ ] **Task 15: Meeting Materials Component** (AC: 9, 10)
  - [ ] Create `src/components/partner/MeetingMaterialsPanel.tsx`
  - [ ] Display agenda preview
  - [ ] Download links for pre-meeting pack
  - [ ] Show material distribution status

- [ ] **Task 16: Organizer Scheduling Component** (AC: 1, 3, 4)
  - [ ] Create `src/components/organizer/MeetingSchedulerPanel.tsx`
  - [ ] Bulk scheduling for seasonal meetings
  - [ ] Availability calendar view
  - [ ] Conflict resolution UI

- [ ] **Task 17: API Client Integration** (AC: ALL)
  - [ ] Create `src/services/api/partnerMeetingsApi.ts`
  - [ ] Implement `getMeetings(companyName)`
  - [ ] Implement `scheduleMeeting(request)`
  - [ ] Implement `submitRsvp(meetingId, status)`
  - [ ] Implement `getMeetingMaterials(meetingId)`
  - [ ] Use React Query with optimistic updates

### Testing Tasks

- [ ] **Task 18: Backend Integration Tests** (AC: 2, 5, 16)
  - [ ] Create `PartnerMeetingControllerIntegrationTest.java`
  - [ ] Test meeting creation and calendar sync (mock Graph API)
  - [ ] Test RSVP workflow
  - [ ] Test role-based access
  - [ ] Use PostgreSQL via Testcontainers

- [ ] **Task 19: Frontend Component Tests** (AC: 17)
  - [ ] Create `PartnerMeetingsDashboard.test.tsx`
  - [ ] Test RSVP submission
  - [ ] Test materials display
  - [ ] Test i18n language switching

- [ ] **Task 20: E2E Tests** (AC: 1, 5, 11)
  - [ ] Create `e2e/partner/meeting-coordination.spec.ts`
  - [ ] Test organizer schedules meeting → partner receives invite
  - [ ] Test partner RSVP → status updates
  - [ ] Test materials distribution workflow

## Dev Notes

### Architecture Compliance

**ADR-003 (Meaningful Identifiers):**
```sql
-- ✅ CORRECT: Meaningful IDs, no cross-service foreign keys
CREATE TABLE partner_meetings (
    company_name VARCHAR(12) NOT NULL,        -- Not partner_id UUID
    organizer_username VARCHAR(100) NOT NULL  -- Not user_id UUID
);

CREATE TABLE meeting_rsvps (
    attendee_username VARCHAR(100) NOT NULL   -- Not user_id UUID
);
```

**ADR-004 (HTTP Enrichment):**
```java
// Get partner contact info via HTTP for email distribution
@Cacheable("partner-contacts")
public List<PartnerContactDTO> getPartnerContacts(String companyName) {
    return partnerServiceClient.getPartnerContacts(companyName);
}
```

**ADR-006 (OpenAPI Contract-First):**
- Update `docs/api/partners-api.openapi.yml` BEFORE implementation
- Generate types: `npm run generate:api-types:partners`

### Microsoft Graph API Integration

```java
@Service
public class MicrosoftGraphClient {
    private final GraphServiceClient graphClient;

    public Event createCalendarEvent(MeetingRequest request) {
        Event event = new Event();
        event.subject = String.format("Partner Meeting: %s - %s",
            request.getCompanyName(),
            request.getMeetingType().getDisplayName());
        event.start = new DateTimeTimeZone();
        event.start.dateTime = request.getScheduledAt().toString();
        event.start.timeZone = "Europe/Zurich";
        event.end = new DateTimeTimeZone();
        event.end.dateTime = request.getScheduledAt().plusHours(1).toString();
        event.end.timeZone = "Europe/Zurich";

        // Add attendees
        List<Attendee> attendees = request.getAttendeeEmails().stream()
            .map(email -> {
                Attendee attendee = new Attendee();
                attendee.emailAddress = new EmailAddress();
                attendee.emailAddress.address = email;
                attendee.type = AttendeeType.REQUIRED;
                return attendee;
            })
            .collect(toList());
        event.attendees = attendees;

        return graphClient.users(request.getOrganizerEmail())
            .calendar()
            .events()
            .buildRequest()
            .post(event);
    }
}
```

**Required Azure AD Configuration:**
- App Registration with `Calendars.ReadWrite` permission
- Client credentials flow for daemon/service access
- Store credentials in AWS Secrets Manager

### EventBridge Scheduled Rules

```java
// Scheduled rule triggers (defined in CDK)
// January 15th at 9 AM - Schedule Spring meetings
// July 15th at 9 AM - Schedule Autumn meetings

@EventListener(condition = "#event.source == 'aws.scheduler' && #event.detail.ruleArn contains 'schedule-spring-meetings'")
public void handleSpringMeetingScheduling(ScheduledEvent event) {
    meetingSchedulingService.scheduleSeasonalMeetings(MeetingType.SPRING);
}

@EventListener(condition = "#event.source == 'aws.scheduler' && #event.detail.ruleArn contains 'schedule-autumn-meetings'")
public void handleAutumnMeetingScheduling(ScheduledEvent event) {
    meetingSchedulingService.scheduleSeasonalMeetings(MeetingType.AUTUMN);
}
```

### Reminder Email Schedule

```java
@Component
public class MeetingReminderScheduler {
    // EventBridge rules trigger at these intervals before meeting
    private static final List<Duration> REMINDER_INTERVALS = List.of(
        Duration.ofDays(14),  // 2 weeks
        Duration.ofDays(7),   // 1 week
        Duration.ofDays(3)    // 3 days
    );

    public void scheduleReminders(Meeting meeting) {
        for (Duration interval : REMINDER_INTERVALS) {
            LocalDateTime reminderTime = meeting.getScheduledAt()
                .minus(interval)
                .withHour(9)
                .withMinute(0);

            // Create EventBridge scheduled rule
            eventBridgeClient.putRule(PutRuleRequest.builder()
                .name(String.format("meeting-%s-reminder-%dd",
                    meeting.getId(), interval.toDays()))
                .scheduleExpression(String.format("at(%s)",
                    reminderTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
                .state(RuleState.ENABLED)
                .build());
        }
    }
}
```

### Project Structure Notes

**Backend Files:**
```
services/partner-coordination-service/src/main/java/ch/batbern/partners/
├── controller/
│   └── PartnerMeetingController.java
├── service/
│   ├── PartnerMeetingSchedulingService.java
│   ├── MeetingRsvpService.java
│   ├── MeetingMaterialsService.java
│   ├── MeetingEmailService.java
│   └── Epic5IntegrationService.java
├── client/
│   └── MicrosoftGraphClient.java
├── domain/
│   ├── PartnerMeeting.java
│   ├── MeetingRsvp.java
│   ├── MeetingMaterial.java
│   ├── MeetingType.java (enum: SPRING, AUTUMN)
│   └── RsvpStatus.java (enum: ATTENDING, NOT_ATTENDING, TENTATIVE)
├── dto/
│   ├── MeetingRequest.java
│   ├── MeetingResponse.java
│   ├── RsvpRequest.java
│   └── MaterialsResponse.java
└── repository/
    ├── PartnerMeetingRepository.java
    ├── MeetingRsvpRepository.java
    └── MeetingMaterialRepository.java
```

**Frontend Files:**
```
web-frontend/src/
├── components/partner/
│   ├── PartnerMeetingsDashboard.tsx
│   ├── MeetingRsvpCard.tsx
│   └── MeetingMaterialsPanel.tsx
├── components/organizer/
│   └── MeetingSchedulerPanel.tsx
├── services/api/
│   └── partnerMeetingsApi.ts
└── hooks/
    └── usePartnerMeetings.ts
```

**Email Templates (AWS SES):**
```
infrastructure/email-templates/
├── partner-meeting-invitation-de.html
├── partner-meeting-invitation-en.html
├── partner-meeting-reminder-de.html
├── partner-meeting-reminder-en.html
├── partner-meeting-materials-de.html
├── partner-meeting-materials-en.html
├── partner-meeting-followup-de.html
└── partner-meeting-followup-en.html
```

**Database Migrations:**
```
services/partner-coordination-service/src/main/resources/db/migration/
├── V8.3.1__create_partner_meetings_table.sql
├── V8.3.2__create_meeting_rsvps_table.sql
└── V8.3.3__create_meeting_materials_table.sql
```

### i18n Translation Keys

```json
{
  "meetings": {
    "dashboard": {
      "title": "Partner Meetings",
      "upcoming": "Upcoming Meetings",
      "past": "Past Meetings"
    },
    "schedule": "Schedule Meeting",
    "spring": "Spring Meeting",
    "autumn": "Autumn Meeting",
    "status": {
      "SCHEDULED": "Scheduled",
      "CONFIRMED": "Confirmed",
      "CANCELLED": "Cancelled",
      "COMPLETED": "Completed"
    },
    "rsvp": {
      "title": "RSVP",
      "attending": "I will attend",
      "notAttending": "I cannot attend",
      "tentative": "Tentative",
      "currentStatus": "Your RSVP: {{status}}",
      "deadline": "Please respond by {{date}}"
    },
    "agenda": {
      "title": "Meeting Agenda",
      "attendanceReview": "Attendance Review",
      "budgetDiscussion": "Budget Discussion",
      "topicVoting": "Topic Voting Results",
      "openDiscussion": "Open Discussion"
    },
    "materials": {
      "title": "Meeting Materials",
      "preMeeting": "Pre-Meeting Pack",
      "download": "Download Materials",
      "sentOn": "Sent on {{date}}"
    },
    "reminder": {
      "twoWeeks": "Meeting in 2 weeks",
      "oneWeek": "Meeting in 1 week",
      "threeDays": "Meeting in 3 days"
    },
    "followup": {
      "title": "Meeting Follow-up",
      "thankYou": "Thank you for attending",
      "actionItems": "Action Items",
      "nextMeeting": "Next Meeting"
    },
    "error": {
      "scheduleFailed": "Failed to schedule meeting",
      "rsvpFailed": "Failed to submit RSVP"
    }
  }
}
```

### Email Template Example (German)

```html
<!-- partner-meeting-invitation-de.html -->
<!DOCTYPE html>
<html lang="de">
<head>
    <meta charset="UTF-8">
    <title>Einladung zum Partner-Meeting</title>
</head>
<body>
    <h1>Einladung: {{meetingType}} Partner-Meeting</h1>
    <p>Sehr geehrte/r {{recipientName}},</p>
    <p>Wir laden Sie herzlich zum {{meetingType}} Partner-Meeting ein.</p>

    <table>
        <tr><td><strong>Datum:</strong></td><td>{{meetingDate}}</td></tr>
        <tr><td><strong>Zeit:</strong></td><td>{{meetingTime}} Uhr</td></tr>
        <tr><td><strong>Ort:</strong></td><td>{{location}}</td></tr>
    </table>

    <p>Bitte bestätigen Sie Ihre Teilnahme:</p>
    <a href="{{rsvpUrl}}?status=ATTENDING">Ich nehme teil</a> |
    <a href="{{rsvpUrl}}?status=NOT_ATTENDING">Ich kann nicht teilnehmen</a> |
    <a href="{{rsvpUrl}}?status=TENTATIVE">Unter Vorbehalt</a>

    <p>Mit freundlichen Grüssen,<br>Das BATbern Team</p>
</body>
</html>
```

### Performance Requirements

| Metric | Target | Measurement |
|--------|--------|-------------|
| Meeting Scheduling | <5s | Micrometer timer |
| RSVP Submission | <1s | API response |
| Materials Generation | <30s (async) | Background job |
| Email Delivery | <1min | SES metrics |

### References

- [Source: docs/prd/epic-8-partner-coordination.md#Story-8.3]
- [Source: docs/architecture/ADR-003-meaningful-identifiers.md]
- [Source: docs/architecture/05-frontend-architecture.md#i18n]
- [Source: docs/architecture/coding-standards.md#TDD-Workflow]
- [Microsoft Graph Calendar API](https://docs.microsoft.com/en-us/graph/api/resources/calendar)
- [AWS SES Templates](https://docs.aws.amazon.com/ses/latest/dg/send-personalized-email-api.html)

## Dev Agent Record

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

### File List

