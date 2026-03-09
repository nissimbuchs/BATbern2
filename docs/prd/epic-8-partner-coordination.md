# Epic 8: Partner Coordination

**Status:** ✅ **COMPLETE** — Stories 8.0–8.4 done (deployed to staging 2026-02-22; Story 8.4 Partner Notes added 2026-02-23)

**Simplified (2026-02-21):** Epic 8 was originally scoped with QuickSight, Microsoft Graph, materialized views, and tier-based voting. These were cut as massively over-engineered for a community of ~5–10 partners and ~3 events/year. The three stories below reflect what was actually decided and built.

---

## Epic Overview

**Epic Goal**: Give partners visibility into their sponsorship value and give organizers lightweight tooling to coordinate partner meetings.

**Deliverable**: Three focused features in `partner-coordination-service` — a simple attendance dashboard, a topic suggestion and voting system, and a meeting coordination tool with calendar invites.

**Architecture Context**:
- **Core Service**: `partner-coordination-service` (Java 21 + Spring Boot 3.2) — ✅ **DONE in Story 2.7**
- **Database**: PostgreSQL — existing `topic_suggestions` and `topic_votes` tables from Story 2.7; `partner_meetings` table added in Story 8.3
- **Analytics data source**: `event-management-service` (`registrations` table) — queried on demand, 15-min Caffeine cache
- **Calendar invites**: RFC 5545 `.ics` file (no Microsoft Graph, no Outlook dependency)
- **Email**: AWS SES — reuses existing pattern from Story 6.5

**Prerequisites:**
- ✅ Story 2.7 (Partner Coordination Service Foundation) — backend complete
- ✅ Story 2.8 (Partner Management Frontend) — basic CRUD complete
- ✅ Story 5.15 (Partner Meeting Coordination) — basic meetings operational

---

## Epic 8 Stories

### Story 8.1: Partner Attendance Dashboard

**Story file**: `_bmad-output/implementation-artifacts/8-1-partner-analytics-dashboard.md`
**Status**: ready-for-dev

**User Story:**
As a **partner**, I want to see how many of my company's employees attended each BATbern event, so that I can justify our sponsorship internally.

**Scope (what it is):**
- Attendance table: event name, date, company attendees, total attendees, percentage — sorted by date descending
- Default view: last 5 years (~15 events); toggle to full history (~60 events)
- Single KPI: cost-per-attendee (partnership cost ÷ total company attendees)
- Excel (XLSX) export via Apache POI
- Data queried on demand; Caffeine-cached 15 minutes — no nightly job, no materialized views
- Partners see only their own company's data (enforced at API level)
- Desktop layout only

**What was cut:**
- AWS QuickSight — massively over-engineered for this data volume
- Materialized views and nightly batch jobs — not needed (max ~60 events ever)
- Charts (Recharts) — a table is sufficient
- Department breakdown, comparative analysis vs other partners, individual attendee tracking
- Mobile responsive layout

**Architecture:**
```
Partner opens dashboard
       │
       ▼
partner-coordination-service
  PartnerAnalyticsController
       │
       ├─ HTTP GET event-management-service:
       │    /api/v1/events/attendance-summary
       │      ?companyName={name}&fromYear={year}
       │
       ▼
  Caffeine cache (15 min)
  + cost-per-attendee computed locally
       │
       ▼
  PartnerDashboardDTO → frontend
```

**Key endpoints:**
- `GET /api/v1/partners/{companyName}/analytics/dashboard?fromYear={year}` (PARTNER, ORGANIZER)
- `GET /api/v1/partners/{companyName}/analytics/export` → XLSX download (PARTNER, ORGANIZER)
- `GET /api/v1/events/attendance-summary?companyName={name}&fromYear={year}` on event-management-service (internal)

**Performance targets:** Dashboard P95 < 5s | Cached response < 50ms | Cold response < 500ms

---

### Story 8.2: Topic Suggestions & Voting

**Story file**: `_bmad-output/implementation-artifacts/8-2-topic-voting-system.md`
**Status**: ready-for-dev

**User Story:**
As a **partner**, I want to suggest topics for future BATbern events and vote on other partners' suggestions, so that events cover subjects my company cares about.

**Scope (what it is):**
- Topic list: all proposed topics with title, description, suggesting company, vote count — sorted by votes descending
- Simple toggle vote: one vote per partner per topic (on/off), no weighting, no allocation
- Topic suggestion: title (required, minimum 5 characters) + short description (optional) — no mandatory justification
- Organizer review: set status to **Selected** or **Declined**, with optional "planned for event" free text
- Status visibility: all partners see **Proposed / Selected / Declined** + planned event if set
- Role-based: partners suggest and vote; only organizers change status
- Organizers may suggest topics on behalf of a partner company by supplying `companyName` in the request body; omitting `companyName` as an organizer returns 400
- Organizers may NOT cast votes (403 Forbidden); only PARTNER users may vote
- For PARTNER users, `companyName` in the request body is ignored; the company is always resolved from the authenticated user's JWT. Only ORGANIZER users may specify `companyName` explicitly.

**What was cut:**
- Tier-based vote weighting and 30% influence cap
- Drag-and-drop priority ranking
- Voting deadline + countdown timer
- Historical voting trends, impact metrics
- EventBridge integration (organizer reads the ranked list directly)
- Business justification + strategic alignment fields
- `UNDER_REVIEW` status (just Proposed → Selected/Declined)

**Data model:**
```sql
-- topic_suggestions: one row per topic
-- topic_votes: one row per (topic_id, company_name) — PRIMARY KEY enforces one vote
```
All data lives in `partner-coordination-service` DB. No cross-service calls needed.

**Key endpoints:**
- `GET  /api/v1/partners/topics` (PARTNER, ORGANIZER)
- `POST /api/v1/partners/topics` (PARTNER)
- `POST /api/v1/partners/topics/{topicId}/vote` (PARTNER)
- `DELETE /api/v1/partners/topics/{topicId}/vote` (PARTNER)
- `PATCH /api/v1/partners/topics/{topicId}/status` (ORGANIZER)

**Performance targets:** Topic list P95 < 3s | Vote toggle < 500ms

---

### Story 8.3: Partner Meeting Coordination

**Story file**: `_bmad-output/implementation-artifacts/8-3-automated-meeting-coordination.md`
**Status**: ready-for-dev

**User Story:**
As an **organizer**, I want to manage the Spring and Autumn partner meetings, so that I can send calendar invites with the agenda and keep notes from each meeting.

**Context:** Partner meetings always happen on the same day as the BATbern event at lunch. The meeting date is derived from the linked BATbern event — no scheduling logic needed.

**Scope (what it is):**
- Meeting record linked to a BATbern event by event code; fields: date (auto-filled), start/end time, location, type (Spring/Autumn)
- Agenda as free text — included in the calendar invite description
- Calendar invite: standard `.ics` file (RFC 5545) with two VEVENTs (partner lunch + BATbern event itself), sent via AWS SES to all partner contacts
- Post-meeting notes: free-text, visible to all organizers
- Meeting list: all past and upcoming meetings with status (invite sent / not sent)
- Organizer-only access (partners do not interact with this screen)

**What was cut:**
- Microsoft Graph / Outlook API integration — standard ICS works with any calendar app
- Automated Spring/Autumn scheduling — date comes from the BATbern event
- RSVP tracking (attending / not attending / tentative)
- Automated reminder emails
- Attendance prediction
- Cancellation and rescheduling handling
- Pre-meeting pack compilation, material distribution
- Post-meeting follow-up email
- EventBridge scheduled rules
- `meeting_rsvps` and `meeting_materials` tables

**Architecture:**
```
Organizer clicks "Send Calendar Invite"
       │
       ▼
PartnerMeetingController (202 Accepted immediately)
       │
       ├─ EventManagementClient.getEventSummary(eventCode)
       │    (Caffeine cache 1h)
       ├─ IcsGeneratorService.generate() → byte[] (pure RFC 5545, no library)
       └─ @Async: SesEmailService.sendCalendarInvite(recipients, ics)
              → sends to all partner contacts on record
```

**Key endpoints:**
- `GET  /api/v1/partner-meetings` (ORGANIZER)
- `POST /api/v1/partner-meetings` (ORGANIZER)
- `GET  /api/v1/partner-meetings/{meetingId}` (ORGANIZER)
- `PATCH /api/v1/partner-meetings/{meetingId}` — agenda or notes update (ORGANIZER)
- `POST /api/v1/partner-meetings/{meetingId}/send-invite` → 202 Accepted (ORGANIZER)

**Performance targets:** Meeting list P95 < 3s | Invite 202 response < 200ms (async send)

---

### Story 8.4: Partner Notes

**Story file**: `_bmad-output/implementation-artifacts/8-4-partner-notes.md`
**Status**: ready-for-dev

**User Story:**
As an **organizer**, I want to record and manage private notes about a partner company, so that I can track relationship history and internal context that should not be visible to the partner.

**Scope (what it is):**
- Organizer CRUD for notes scoped to a partner company: title (required) + content (required, free-text)
- Title has a 500-character maximum length
- Author username auto-captured from JWT
- Notes tab completely hidden for PARTNER users (not just read-only — invisible)
- Notes list sorted by creation date descending
- Note PATCH/DELETE validate that the noteId belongs to the companyName in the URL path; cross-partner access returns 404

**What is cut:**
- Partner-visible notes, note pinning, attachments, categories, @-mentions, full-text search

**Key endpoints:**
- `GET  /api/v1/partners/{companyName}/notes` (ORGANIZER)
- `POST /api/v1/partners/{companyName}/notes` (ORGANIZER)
- `PATCH /api/v1/partners/{companyName}/notes/{noteId}` (ORGANIZER)
- `DELETE /api/v1/partners/{companyName}/notes/{noteId}` (ORGANIZER)

---

## What Was Cut from the Original Epic 8 Scope

| Feature | Reason |
|---------|--------|
| AWS QuickSight embedded dashboards | Over-engineered; a table suffices for 3 events/year |
| PostgreSQL materialized views + nightly batch | Not needed for this data volume |
| Microsoft Graph / Outlook calendar integration | Standard ICS (RFC 5545) works everywhere |
| Tier-based vote weighting + influence cap algorithm | 5–10 partners; simple count is fair |
| Drag-and-drop topic ranking (react-beautiful-dnd) | Sort by vote count is enough |
| RSVP tracking | Not needed |
| Automated Spring/Autumn scheduling | Date comes from the BATbern event |
| EventBridge scheduled rules | Not needed |
| Automated reminder emails, attendance prediction | Not needed |
| Pre-meeting pack, material distribution | Not needed |
| `meeting_rsvps` + `meeting_materials` tables | Not needed |
| Mobile-responsive analytics layout | Desktop only for partner portal |
| Department breakdown, comparative analytics | No department data tracked; anonymized comparison not needed |
| Individual attendee tracking | Not allowed (GDPR) |

---

## Success Metrics

| Metric | Target |
|--------|--------|
| Analytics dashboard load (P95) | < 5s |
| Topic list load (P95) | < 3s |
| Meeting list load (P95) | < 3s |
| Calendar invite delivery | < 30s (async) |
| Role-based data access enforcement | 100% (integration tested) |

---

## Technical Compliance

All three stories follow existing BATbern ADRs:
- **ADR-003**: Meaningful string identifiers (`companyName`, `event_code`, `created_by`) — no UUID foreign keys across service boundaries
- **ADR-006**: OpenAPI spec created before implementation for all new endpoints
- **ADR-008**: Backend `SecurityConfig` controls access — no frontend-only access guards

---

## Files Modified by Epic 8

- `services/partner-coordination-service/` — `PartnerAnalyticsController`, `TopicController`, `PartnerMeetingController`, `IcsGeneratorService`, `PartnerInviteEmailService`
- `services/event-management-service/` — new `GET /api/v1/events/attendance-summary` endpoint
- `web-frontend/src/components/partner/` — `PartnerAttendanceDashboard`, `TopicListPage`, `TopicSuggestionForm`
- `web-frontend/src/components/organizer/` — `TopicStatusPanel`, `PartnerMeetingsPage`, `CreateMeetingDialog`
- `docs/api/` — `partner-analytics-api.openapi.yml`, `event-management-api.openapi.yml` (new endpoint)
- `public/locales/de/partner.json`, `public/locales/en/partner.json` — i18n keys
