# Story 6.3: Partner Detail Screen - Wireframe

**Story**: Story 2.8 (Epic 2) - Partner Management Frontend
**Screen**: Partner Detail Screen
**User Role**: Organizer (primary), Partner (self-view)
**Related FR**: FR4 (Partner Analytics - Backlog), FR8 (Partner Strategic Input), FR12 (Logistics & Partner Meetings)

**Implementation Scope:**
- ✅ **Story 2.7** (Epic 2): Partner Coordination Service Foundation (backend APIs)
  - Partner CRUD with meaningful IDs (`companyName`)
  - Contact Management (stores `username`)
  - Topic Voting & Suggestions
  - Basic Meeting coordination
- 📝 **Story 2.8** (Epic 2): Partner Management Frontend (THIS WIREFRAME)
  - Organizer UI for partner detail view with tabbed interface
  - Contact management UI
  - Read-only topic voting and meeting display
  - Notes system for organizer tracking
- 📦 **Epic 8** (Deferred to Phase 2): Advanced Partner Portal Features
  - Engagement score calculation & analytics dashboard (Story 6.1)
  - Interactive topic voting interface (Story 6.4)
  - Full meeting coordination with RSVP and materials (Story 6.2)

**IMPORTANT (ADR-003 Compliance):**
- All API endpoints use `{companyName}` (meaningful ID), NOT `{partnerId}` (UUID)
- Contact endpoints use `{username}` (meaningful ID), NOT `{contactId}` or `{userId}` (UUID)
- Example: `GET /api/v1/partners/GoogleZH` (NOT `/api/v1/partners/{uuid}`)

---

## Visual Wireframe

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│ ← Back to Partner Directory         Partner Profile                 [Edit Partner] │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                   │
│  ┌──── PARTNER HEADER ──────────────────────────────────────────────────────┐   │
│  │                                                                           │   │
│  │  ┌────────┐  🏆 STRATEGIC PARTNER                                        │   │
│  │  │ Logo   │  TechCorp AG (GoogleZH)           Engagement: ████████ 92%   │   │
│  │  │        │                                                               │   │
│  │  │ [150px]│  🏢 Software & IT Services                                   │   │
│  │  │        │  🌐 www.techcorp.ch                                           │   │
│  │  └────────┘  📍 Zurich, Switzerland                                      │   │
│  │                                                                           │   │
│  │  [📧 Send Email] [📅 Schedule Meeting] [📊 View Analytics] [📝 Add Note] │   │
│  │                                                                           │   │
│  └───────────────────────────────────────────────────────────────────────────┘   │
│                                                                                   │
│  ┌──── QUICK STATS ────────────────────────────────────────────────────────┐    │
│  │                                                                          │    │
│  │  Partner Since        Events Attended       Active Votes    Meetings    │    │
│  │  ────────────        ─────────────────     ────────────    ─────────    │    │
│  │  January 2022              24                  5              12        │    │
│  │  (3 years)                 (last: Spring 25)  (topics)       (seasonal) │    │
│  │                                                                          │    │
│  └──────────────────────────────────────────────────────────────────────────┘    │
│                                                                                   │
│  ┌──── TABS ────────────────────────────────────────────────────────────────┐   │
│  │                                                                           │   │
│  │  [Overview] [Contacts] [Meetings] [Activity] [Notes] [Settings]         │   │
│  │                                                                           │   │
│  └───────────────────────────────────────────────────────────────────────────┘   │
│                                                                                   │
│  ┌──── OVERVIEW TAB ────────────────────────────────────────────────────────┐   │
│  │                                                                           │   │
│  │  ┌── PARTNERSHIP DETAILS ──────────┬── ENGAGEMENT METRICS ──────────┐   │   │
│  │  │                                  │                                │   │   │
│  │  │  Partnership Tier: 🏆 Strategic │  Overall Score: 92% (Excellent)│   │   │
│  │  │  Tier Start Date: Jan 1, 2024   │  ⚠️ Epic 8 Feature             │   │   │
│  │  │  Previous Tier: 💎 Platinum     │  Score Breakdown:              │   │   │
│  │  │                                  │  • Event Attendance: ████░ 85% │   │   │
│  │  │  Benefits:                       │  • Topic Voting: █████ 95%    │   │   │
│  │  │  ✓ Logo placement on website    │  • Meeting Participation: 100%│   │   │
│  │  │  ✓ Newsletter mentions          │  • Content Interaction: ███░ 75%│  │   │
│  │  │  ✓ Priority event access        │                                │   │   │
│  │  │  ✓ Quarterly strategic meetings │  Trend: ↗ +8% (last quarter)  │   │   │
│  │  │  ✓ ROI analytics dashboard      │                                │   │   │
│  │  │                                  │  [View Full Analytics →]      │   │   │
│  │  │  Swiss UID: CHE-123.456.789     │                                │   │   │
│  │  │  Tax Status: ✓ Verified         │  ⚠️ Low engagement alert for  │   │   │
│  │  │                                  │     content interaction        │   │   │
│  │  │  [Change Tier]                   │                                │   │   │
│  │  │                                  │                                │   │   │
│  │  └──────────────────────────────────┴────────────────────────────────┘   │   │
│  │                                                                           │   │
│  │  ┌── RECENT ACTIVITY ───────────────────────────────────────────────┐   │   │
│  │  │                                                                    │   │   │
│  │  │  📊 Last Event: Spring 2025 BATbern (March 15, 2025)             │   │   │
│  │  │     • 15 employees attended (85% of invited)                      │   │   │
│  │  │     • 12 session registrations                                    │   │   │
│  │  │     • 8 content downloads post-event                              │   │   │
│  │  │                                                                    │   │   │
│  │  │  🗳️ Active Topic Votes: 5 votes cast                             │   │   │
│  │  │     • Cloud Migration Strategies (Priority: High)                 │   │   │
│  │  │     • AI in Business Operations (Priority: High)                  │   │   │
│  │  │     • Cybersecurity Best Practices (Priority: Medium)             │   │   │
│  │  │     • DevOps & CI/CD Pipelines (Priority: Medium)                 │   │   │
│  │  │     • Blockchain Applications (Priority: Low)                     │   │   │
│  │  │                                                                    │   │   │
│  │  │  📅 Next Meeting: May 20, 2025 (14:00 - 16:00)                   │   │   │
│  │  │     • Type: Spring Strategic Planning                             │   │   │
│  │  │     • Location: BATbern Headquarters                              │   │   │
│  │  │     • Status: Confirmed (RSVP: Maria Schmidt)                     │   │   │
│  │  │                                                                    │   │   │
│  │  │  [View All Activity →]                                            │   │   │
│  │  │                                                                    │   │   │
│  │  └────────────────────────────────────────────────────────────────────┘   │   │
│  │                                                                           │   │
│  │  ┌── EMPLOYEES ──────────────────────────────────────────────────────┐   │   │
│  │  │                                                                    │   │   │
│  │  │  Total Employees in System: 42                                    │   │   │
│  │  │  Active Event Participants: 28 (67%)                              │   │   │
│  │  │                                                                    │   │   │
│  │  │  Top Attendees:                                                    │   │   │
│  │  │  • Maria Schmidt (CTO) - 18 events                                │   │   │
│  │  │  • Thomas Weber (Lead Developer) - 15 events                      │   │   │
│  │  │  • Anna Müller (Product Manager) - 12 events                      │   │   │
│  │  │                                                                    │   │   │
│  │  │  [View All Employees →]                                           │   │   │
│  │  │                                                                    │   │   │
│  │  └────────────────────────────────────────────────────────────────────┘   │   │
│  │                                                                           │   │
│  └───────────────────────────────────────────────────────────────────────────┘   │
│                                                                                   │
│  ┌──── CONTACTS TAB ────────────────────────────────────────────────────────┐   │
│  │                                                                           │   │
│  │  ┌── PRIMARY CONTACT ──────────────────────────────────────────────┐    │   │
│  │  │                                                                  │    │   │
│  │  │  👤 Maria Schmidt                                                │    │   │
│  │  │     Title: Chief Technology Officer (CTO)                        │    │   │
│  │  │     📧 Email: m.schmidt@techcorp.ch                              │    │   │
│  │  │     📱 Phone: +41 44 123 45 67                                   │    │   │
│  │  │     💼 LinkedIn: linkedin.com/in/maria-schmidt                   │    │   │
│  │  │                                                                  │    │   │
│  │  │     Preferred Contact Method: Email                              │    │   │
│  │  │     Language: German, English                                    │    │   │
│  │  │                                                                  │    │   │
│  │  │     [Edit Contact] [Send Email] [View Profile]                  │    │   │
│  │  │                                                                  │    │   │
│  │  └──────────────────────────────────────────────────────────────────┘    │   │
│  │                                                                           │   │
│  │  ┌── SECONDARY CONTACTS ───────────────────────────────────────────┐    │   │
│  │  │                                                                  │    │   │
│  │  │  👤 Peter Müller - CFO                                          │    │   │
│  │  │     📧 p.mueller@techcorp.ch  |  📱 +41 44 123 45 68           │    │   │
│  │  │     Role: Budget & Sponsorship Decisions                         │    │   │
│  │  │                                                                  │    │   │
│  │  │  👤 Anna Weber - HR Manager                                     │    │   │
│  │  │     📧 a.weber@techcorp.ch  |  📱 +41 44 123 45 69              │    │   │
│  │  │     Role: Employee Registration & Attendance                     │    │   │
│  │  │                                                                  │    │   │
│  │  │  [+ Add Contact]                                                 │    │   │
│  │  │                                                                  │    │   │
│  │  └──────────────────────────────────────────────────────────────────┘    │   │
│  │                                                                           │   │
│  │  ┌── COMMUNICATION PREFERENCES ────────────────────────────────────┐    │   │
│  │  │                                                                  │    │   │
│  │  │  Email Notifications: ✓ Enabled                                 │    │   │
│  │  │  Newsletter: ✓ Subscribed                                       │    │   │
│  │  │  Event Announcements: ✓ Enabled                                 │    │   │
│  │  │  Meeting Reminders: ✓ Enabled (1 week + 1 day before)          │    │   │
│  │  │                                                                  │    │   │
│  │  │  Preferred Language: German                                      │    │   │
│  │  │  Time Zone: Europe/Zurich (CET)                                 │    │   │
│  │  │                                                                  │    │   │
│  │  └──────────────────────────────────────────────────────────────────┘    │   │
│  │                                                                           │   │
│  └───────────────────────────────────────────────────────────────────────────┘   │
│                                                                                   │
│  ┌──── MEETINGS TAB ────────────────────────────────────────────────────────┐   │
│  │                                                                           │   │
│  │  ┌── UPCOMING MEETINGS ────────────────────────────────────────────┐    │   │
│  │  │                                                                  │    │   │
│  │  │  📅 May 20, 2025 (14:00 - 16:00)                                │    │   │
│  │  │     Spring Strategic Planning Meeting                            │    │   │
│  │  │     Location: BATbern HQ, Conference Room A                     │    │   │
│  │  │     Attendees: Maria Schmidt, Peter Müller (Confirmed)          │    │   │
│  │  │     Agenda: Q2 topic voting results, Fall event planning        │    │   │
│  │  │                                                                  │    │   │
│  │  │     [View Agenda] [Edit Meeting] [Send Reminder]                │    │   │
│  │  │                                                                  │    │   │
│  │  │  📅 November 15, 2025 (14:00 - 16:00)                           │    │   │
│  │  │     Fall Strategic Planning Meeting                              │    │   │
│  │  │     Status: Scheduled (RSVP Pending)                             │    │   │
│  │  │                                                                  │    │   │
│  │  │     [View Details] [Reschedule]                                  │    │   │
│  │  │                                                                  │    │   │
│  │  └──────────────────────────────────────────────────────────────────┘    │   │
│  │                                                                           │   │
│  │  ┌── MEETING HISTORY ───────────────────────────────────────────────┐   │   │
│  │  │                                                                  │    │   │
│  │  │  📅 November 18, 2024 (14:00 - 16:00) - Completed               │    │   │
│  │  │     Fall Strategic Planning Meeting                              │    │   │
│  │  │     Attendees: Maria Schmidt, Peter Müller, Anna Weber          │    │   │
│  │  │     Minutes: ✓ Available  |  Action Items: 3 completed          │    │   │
│  │  │                                                                  │    │   │
│  │  │     [View Minutes] [View Action Items]                           │    │   │
│  │  │                                                                  │    │   │
│  │  │  📅 May 15, 2024 (14:00 - 16:00) - Completed                    │    │   │
│  │  │     Spring Strategic Planning Meeting                            │    │   │
│  │  │     Attendees: Maria Schmidt, Thomas Klein                       │    │   │
│  │  │     Minutes: ✓ Available  |  Action Items: 5 completed          │    │   │
│  │  │                                                                  │    │   │
│  │  │     [View Minutes] [View Action Items]                           │    │   │
│  │  │                                                                  │    │   │
│  │  │  [Load More Meetings ↓] (10 more)                               │    │   │
│  │  │                                                                  │    │   │
│  │  └──────────────────────────────────────────────────────────────────┘    │   │
│  │                                                                           │   │
│  │  [+ Schedule New Meeting]                                                 │   │
│  │                                                                           │   │
│  └───────────────────────────────────────────────────────────────────────────┘   │
│                                                                                   │
│  ┌──── ACTIVITY TAB ────────────────────────────────────────────────────────┐   │
│  │                                                                           │   │
│  │  Filters: [All Activity ⏷] [Date Range ⏷] [Activity Type ⏷]              │   │
│  │                                                                           │   │
│  │  ┌── ACTIVITY TIMELINE ────────────────────────────────────────────┐    │   │
│  │  │                                                                  │    │   │
│  │  │  🗳️ March 18, 2025 - Topic Voting                              │    │   │
│  │  │     Voted on 5 topics for Fall 2025 event                       │    │   │
│  │  │     Top Priority: "Cloud Migration Strategies"                   │    │   │
│  │  │                                                                  │    │   │
│  │  │  📊 March 15, 2025 - Event Attendance                           │    │   │
│  │  │     Spring 2025 BATbern: 15 employees attended                  │    │   │
│  │  │     Sessions: Cloud Computing, DevOps, AI in Business            │    │   │
│  │  │                                                                  │    │   │
│  │  │  📧 March 1, 2025 - Email Communication                         │    │   │
│  │  │     Organizer: Sent event invitation to Maria Schmidt            │    │   │
│  │  │     Subject: "Spring 2025 BATbern - Registration Open"          │    │   │
│  │  │     Status: ✓ Opened, ✓ Clicked                                 │    │   │
│  │  │                                                                  │    │   │
│  │  │  📝 February 20, 2025 - Note Added                              │    │   │
│  │  │     Organizer: Discussed potential sponsorship increase          │    │   │
│  │  │     Added by: John Organizer                                     │    │   │
│  │  │                                                                  │    │   │
│  │  │  🎯 January 10, 2025 - Tier Upgraded                            │    │   │
│  │  │     Tier changed from 🥇 Gold → ⭐ Premium                       │    │   │
│  │  │     Reason: Increased sponsorship and engagement                 │    │   │
│  │  │                                                                  │    │   │
│  │  │  [Load More Activity ↓] (25 more)                               │    │   │
│  │  │                                                                  │    │   │
│  │  └──────────────────────────────────────────────────────────────────┘    │   │
│  │                                                                           │   │
│  └───────────────────────────────────────────────────────────────────────────┘   │
│                                                                                   │
│  ┌──── NOTES TAB ───────────────────────────────────────────────────────────┐   │
│  │                                                                           │   │
│  │  [+ Add New Note]                                                         │   │
│  │                                                                           │   │
│  │  ┌── RECENT NOTES ──────────────────────────────────────────────────┐   │   │
│  │  │                                                                   │   │   │
│  │  │  📝 February 20, 2025 by John Organizer                          │   │   │
│  │  │     "Discussed potential sponsorship tier upgrade with Maria.    │   │   │
│  │  │      Very positive about Premium benefits, especially ROI        │   │   │
│  │  │      analytics. Follow up in Q2 2025."                           │   │   │
│  │  │                                                                   │   │   │
│  │  │     [Edit] [Delete]                                              │   │   │
│  │  │                                                                   │   │   │
│  │  │  📝 November 20, 2024 by Sarah Coordinator                       │   │   │
│  │  │     "Partner very engaged in topic voting. Strong interest in   │   │   │
│  │  │      cloud and AI topics. Great attendance at Fall meeting."     │   │   │
│  │  │                                                                   │   │   │
│  │  │     [Edit] [Delete]                                              │   │   │
│  │  │                                                                   │   │   │
│  │  │  [Load More Notes ↓] (8 more)                                   │   │   │
│  │  │                                                                   │   │   │
│  │  └───────────────────────────────────────────────────────────────────┘   │   │
│  │                                                                           │   │
│  └───────────────────────────────────────────────────────────────────────────┘   │
│                                                                                   │
│  ┌──── SETTINGS TAB ────────────────────────────────────────────────────────┐   │
│  │                                                                           │   │
│  │  ⚠️ This tab is only visible to organizers                               │   │
│  │                                                                           │   │
│  │  ┌── PARTNERSHIP MANAGEMENT ───────────────────────────────────────┐    │   │
│  │  │                                                                  │    │   │
│  │  │  Partnership Status: ● Active                                    │    │   │
│  │  │  [Deactivate Partnership] [End Partnership]                      │    │   │
│  │  │                                                                  │    │   │
│  │  │  Partnership Tier: ⭐ Premium                                    │    │   │
│  │  │  [Change Tier] → Opens tier selection modal                     │    │   │
│  │  │                                                                  │    │   │
│  │  │  Renewal Date: January 1, 2026                                   │    │   │
│  │  │  Auto-Renewal: ☑ Enabled                                        │    │   │
│  │  │                                                                  │    │   │
│  │  └──────────────────────────────────────────────────────────────────┘    │   │
│  │                                                                           │   │
│  │  ┌── ACCESS & PERMISSIONS ──────────────────────────────────────────┐   │   │
│  │  │                                                                  │    │   │
│  │  │  Analytics Dashboard Access: ☑ Enabled (Premium benefit)        │    │   │
│  │  │  Topic Voting Access: ☑ Enabled                                 │    │   │
│  │  │  Meeting Scheduling Access: ☑ Enabled                           │    │   │
│  │  │  Content Library Access: ☑ Full Access (Premium benefit)        │    │   │
│  │  │                                                                  │    │   │
│  │  └──────────────────────────────────────────────────────────────────┘    │   │
│  │                                                                           │   │
│  │  ┌── DATA & COMPLIANCE ─────────────────────────────────────────────┐   │   │
│  │  │                                                                  │    │   │
│  │  │  Data Retention: Standard (7 years)                              │    │   │
│  │  │  GDPR Compliance: ✓ Compliant                                   │    │   │
│  │  │  Data Processing Agreement: ✓ Signed (Jan 1, 2024)              │    │   │
│  │  │                                                                  │    │   │
│  │  │  [Export Partner Data] [View DPA] [Delete Partner Data]         │    │   │
│  │  │                                                                  │    │   │
│  │  └──────────────────────────────────────────────────────────────────┘    │   │
│  │                                                                           │   │
│  └───────────────────────────────────────────────────────────────────────────┘   │
│                                                                                   │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Key Interactive Elements

- **[← Back to Partner Directory] button**: Return to Partner Directory list screen
- **[Edit Partner] button**: Opens partner editing modal (organizer only)
- **[📧 Send Email] button**: Opens email composer with partner contact pre-filled
- **[📅 Schedule Meeting] button**: Opens meeting scheduler interface
- **[📊 View Analytics] button**: Navigate to Partner Analytics Dashboard (FR4 backlog)
- **[📝 Add Note] button**: Opens note creation modal
- **Tab navigation**: Switch between Overview, Contacts, Meetings, Activity, Notes, Settings tabs
- **[Change Tier] button**: Opens tier selection modal with tier change reason
- **[View Full Analytics →] link**: Navigate to comprehensive partner analytics (FR4 backlog)
- **[View All Activity →] link**: Navigate to Activity tab with all filters cleared
- **[View All Employees →] link**: Navigate to employee list for this partner
- **Engagement score bars**: Interactive hover tooltips showing detailed metrics
- **Topic vote items**: Click to view topic detail screen
- **Contact cards**: Click contact name to view full user profile
- **Meeting items**: Click to view meeting details, agenda, or minutes
- **Activity timeline items**: Click to expand full details
- **Note editing**: Inline editing for note content (organizer only)
- **[+ Add Contact] button**: Opens contact creation form
- **[+ Schedule New Meeting] button**: Opens meeting scheduling workflow
- **[+ Add New Note] button**: Opens note creation modal
- **[Export Partner Data] button**: Generates data export for GDPR compliance
- **Partnership status toggles**: Activate/deactivate partnership, enable/disable auto-renewal

---

## Functional Requirements Met

- **FR4 (Backlog)**: Partner analytics dashboard access (analytics features in backlog, UI prepared)
- **FR8**: Partner strategic input coordination (topic voting history and results displayed)
- **FR12**: Partner meeting coordination (meeting history, scheduling, agenda management)
- **FR21**: Long-term planning capabilities (multi-year partnership tracking, renewal management)
- **Partner Relationship Management**: Centralized partner information hub with full interaction history
- **Communication Tracking**: Complete log of all partner communications and interactions
- **Engagement Monitoring**: Real-time engagement score tracking with alerts

---

## Technical Notes

- **Tab State Management**: React Router or local state for tab navigation with URL hash support (#contacts, #meetings)
- **Lazy Loading**: Each tab loads data on-demand to improve initial page load performance
- **Real-time Updates**: WebSocket connection for engagement score updates when partner activities occur
- **Engagement Score Calculation**:
  - Event Attendance: 40% weight (based on employee attendance vs. invited)
  - Topic Voting: 30% weight (active votes vs. available topics)
  - Meeting Participation: 20% weight (RSVP confirmation and actual attendance)
  - Content Interaction: 10% weight (downloads, views of event materials)
- **Activity Timeline**: Infinite scroll pagination with virtualization for performance
- **Notes System**: Rich text editor (TinyMCE or Draft.js) with mention support (@organizer)
- **Contact Management**: Sync with external CRM systems via API webhooks
- **Meeting Integration**: Calendar sync with Outlook/Google Calendar via CalDAV/iCal
- **Data Export**: GDPR-compliant data export in JSON and CSV formats
- **Audit Log**: All partner data changes logged with timestamp and user attribution
- **Responsive Design**: Mobile-first approach with collapsible sections on small screens
- **Caching Strategy**: React Query with 2-minute cache for partner details, 30-second cache for engagement metrics

---

## API Requirements

### Initial Page Load APIs

**Note**: This wireframe uses the Partners API from Story 2.7 (Partner Coordination Service Foundation).

**IMPORTANT (ADR-003)**: All endpoints use `companyName` (meaningful ID), NOT UUID identifiers.

1. **GET /api/v1/partners/{companyName}?include=contacts,votes,meetings** ✅ **ADR-003 Compliant**
   - **ADR-003**: Path uses `companyName` (e.g., `/api/v1/partners/GoogleZH`)
   - Query params: include (comma-separated list of embedded resources)
   - Returns: Partner entity with embedded data from Story 2.7:
     - Partner: companyName, partnershipLevel (BRONZE/SILVER/GOLD/PLATINUM/STRATEGIC), startDate, endDate, isActive
     - Company: enriched via `GET /api/v1/companies/{companyName}` (HTTP call)
     - Contacts: enriched via `GET /api/v1/users/{username}` per contact (HTTP calls)
     - Votes: active topic votes with vote weight
     - Meetings: basic meeting data
   - Used for: Populate Overview and Contacts tabs
   - **Story 2.8**: Additional tabs (Activity, Notes, Settings) use separate endpoints
   - **Epic 8**: Analytics and engagement scores deferred (show placeholder in Story 2.8)

2. **GET /api/v1/partners/{companyName}/meetings?filter={"upcoming":true}&page=1&limit=10** ✅ **ADR-003**
   - **ADR-003**: Path uses `companyName` (e.g., `/api/v1/partners/GoogleZH/meetings`)
   - Query params: filter (JSON filter for upcoming/past), page, limit, sort
   - Returns: Paginated meetings list with basic details from Story 2.7
   - Used for: Load meetings when Meetings tab activated
   - **Story 2.7**: Basic meeting entity with type, dates, agenda
   - **Epic 8 (Story 6.2)**: Full meeting coordination with RSVP, materials, minutes

3. **GET /api/v1/partners/{companyName}/activity?page=1&limit=20&sort=-timestamp** ⚠️ **Story 2.8 Feature**
   - **ADR-003**: Path uses `companyName`
   - **Story 2.8**: Activity timeline endpoint to be implemented (basic audit log)
   - Query params: page, limit, sort, filter (optional activity type filter)
   - Returns: Paginated activity timeline
   - Used for: Load activity when Activity tab activated
   - **Implementation**: Can derive from domain events or create dedicated activity log

4. **GET /api/v1/partners/{companyName}/notes?page=1&limit=10&sort=-createdAt** ⚠️ **Story 2.8 Feature**
   - **ADR-003**: Path uses `companyName`
   - **Story 2.8**: Notes system endpoint to be implemented (organizer-only)
   - Query params: page, limit, sort
   - Returns: Paginated organizer notes
   - Used for: Load notes when Notes tab activated
   - **Implementation**: New PartnerNote entity with content, createdBy, createdAt

### User Action APIs

5. **PATCH /api/v1/partners/{companyName}** ✅ **ADR-003 Compliant**
    - **ADR-003**: Path uses `companyName` (e.g., `/api/v1/partners/GoogleZH`)
    - Triggered by: User clicks [Edit Partner] and saves changes
    - Payload: `{ partnershipLevel?: "GOLD", startDate?: "2025-01-01", endDate?: "2026-12-31" }`
    - Returns: Updated partner entity
    - Used for: Update partnership details (tier, dates)
    - **Note**: Company information (name, website, industry) updated via Company Service

6. **PATCH /api/v1/partners/{companyName}** ✅ **ADR-003 Compliant** (Same as #5)
    - **ADR-003**: Path uses `companyName`
    - Triggered by: User clicks [Change Tier] and confirms
    - Payload: `{ partnershipLevel: "STRATEGIC", startDate: "2025-05-01" }`
    - Returns: Updated partner with new partnershipLevel
    - Used for: Change partner tier level
    - **Note**: Tier change audit trail could be added to Activity log (Story 2.8)

7. **POST /api/v1/partners/{companyName}/contacts** ✅ **ADR-003 Compliant**
    - **ADR-003**: Path uses `companyName`, payload uses `username` (NOT userId UUID)
    - Triggered by: User clicks [+ Add Contact] in Contacts tab
    - Payload: `{ username: "m.schmidt", role: "PRIMARY|BILLING|TECHNICAL|MARKETING" }`
    - Returns: Created PartnerContact entity
    - Used for: Add contact person (must be existing user in User Service)
    - **Story 2.7**: Backend validates user exists via `GET /api/v1/users/{username}`

8. **PATCH /api/v1/partners/{companyName}/contacts/{username}** ✅ **ADR-003 Compliant**
    - **ADR-003**: Path uses `companyName` and `username` (NOT contactId UUID)
    - Triggered by: User clicks [Edit Contact] and saves
    - Payload: `{ role?: "PRIMARY|BILLING|TECHNICAL|MARKETING" }`
    - Returns: Updated PartnerContact entity
    - Used for: Update contact role
    - **Note**: Contact personal info (name, email, phone) updated via User Service, not here

9. **POST /api/v1/partners/{companyName}/notes** ⚠️ **Story 2.8 Feature**
    - **ADR-003**: Path uses `companyName`
    - **Story 2.8**: Notes endpoint to be implemented
    - Triggered by: User clicks [+ Add New Note] in Notes tab
    - Payload: `{ content: "string", isPrivate: boolean }`
    - Returns: Created PartnerNote entity
    - Used for: Add organizer note about partner

10. **PATCH /api/v1/partners/{companyName}/notes/{noteId}** ⚠️ **Story 2.8 Feature**
    - **ADR-003**: Path uses `companyName`
    - **Story 2.8**: Notes update endpoint to be implemented
    - Triggered by: User clicks [Edit] on note and saves
    - Payload: `{ content: "string", isPrivate: boolean }`
    - Returns: Updated PartnerNote entity
    - Used for: Edit existing note

11. **DELETE /api/v1/partners/{companyName}/notes/{noteId}** ⚠️ **Story 2.8 Feature**
    - **ADR-003**: Path uses `companyName`
    - **Story 2.8**: Notes delete endpoint to be implemented
    - Triggered by: User clicks [Delete] on note and confirms
    - Returns: 204 No Content
    - Used for: Delete note

12. **POST /api/v1/partners/{companyName}/meetings** ✅ **ADR-003 Compliant**
    - **ADR-003**: Path uses `companyName`
    - Triggered by: User clicks [📅 Schedule Meeting] in header or [+ Schedule New Meeting] in Meetings tab
    - Payload: `{ type: "strategic_planning|quarterly_review", proposedDates: [], agenda: "string" }`
    - Returns: Created meeting ID
    - Used for: Schedule new partner meeting
    - **Story 2.7**: Basic meeting creation
    - **Epic 8 (Story 6.2)**: Full meeting coordination with RSVP, calendar integration, materials

13. **POST /api/v1/partners/export**
    - Consolidates: Bulk email as export action with email type
    - Triggered by: User clicks [📧 Send Email] in header
    - Payload: `{ action: "email", companyNames: ["GoogleZH", "MicrosoftBE"], subject: "string", messageBody: "string", templateId?: "uuid" }`
    - Returns: Email task ID, sending status
    - Used for: Send email to partner contacts
    - **Consolidation Benefit**: Unified export endpoint handles both email and file exports

14. **PATCH /api/v1/partners/{companyName}**
    - Consolidates: Settings updates via PATCH on settings section
    - Triggered by: User updates settings in Settings tab (organizer only)
    - Payload: `{ settings: { partnershipStatus?: "active|inactive", autoRenewal?: boolean, renewalDate?: "2026-01-01", accessPermissions?: {...} } }`
    - Returns: Updated partner with new settings
    - Used for: Update partnership configuration
    - **Consolidation Benefit**: Settings managed via same PATCH endpoint as other partner updates

15. **POST /api/v1/partners/{companyName}/export**
    - Consolidates: Data export using unified export endpoint
    - Triggered by: User clicks [Export Partner Data] in Settings tab
    - Payload: `{ exportType: "all", format: "json|csv", includeActivity: true, includeNotes: true }`
    - Returns: Export task ID, download URL when ready
    - Used for: Export partner data for GDPR compliance or reporting
    - **Consolidation Benefit**: Same export endpoint used across all partner screens

16. **DELETE /api/v1/partners/{companyName}**
    - Consolidates: Data deletion using standard DELETE with options
    - Triggered by: User clicks [Delete Partner Data] in Settings tab and confirms
    - Payload: `{ deleteScope: "data-only|complete", gdprJustification: "string", confirmPassword: "string" }`
    - Security: Requires admin permission and GDPR justification
    - Returns: 204 No Content with deletion confirmation
    - Used for: Delete partner data (GDPR right to erasure)
    - **Consolidation Benefit**: Standard RESTful DELETE with scope parameter

17. **GET /api/v1/partners/{companyName}/analytics** ⚠️ **Deferred to Epic 8 (Story 6.1)**
    - **ADR-003**: Path uses `companyName`
    - Triggered by: User clicks [📊 View Analytics] or [View Full Analytics →]
    - Status: **NOT IMPLEMENTED in Story 2.7/2.8** - Analytics dashboard deferred to Epic 8
    - Story 2.8: Show "Coming soon" placeholder or disable button
    - Epic 8 (Story 6.1): Full analytics with ROI calculations, engagement metrics

18. **GET /api/v1/partners/{companyName}/activity?filter={"type":"meeting","date":{"$gte":"2025-01-01"}}&page=1** ⚠️ **Story 2.8**
    - **ADR-003**: Path uses `companyName`
    - **Story 2.8**: Activity filtering to be implemented
    - Triggered by: User applies filters in Activity tab
    - Query params: filter (JSON with type, date range), page, limit
    - Returns: Filtered and paginated activity timeline
    - Used for: Filter activity by type and date range

19. **GET /api/v1/partners/{companyName}/employees?page=1&limit=50**
    - Consolidates: Employees list with standard pagination
    - Triggered by: User clicks [View All Employees →] in Overview tab
    - Query params: page, limit, sort
    - Returns: Paginated employee list (userId, name, email, role, eventsAttended, lastEventDate)
    - Used for: Navigate to employee list screen
    - **Consolidation Benefit**: Standard list endpoint pattern

---

## Navigation Map

### Primary Navigation Actions

1. **← Back button** → Navigate to `Partner Directory` (story-6.3-partner-directory.md)
   - Target: Partner Directory list screen
   - Context: Return to partner list

2. **[Edit Partner] button** → Opens `Edit Partner Modal`
   - Target: Modal overlay for partner editing
   - Context: companyName, current partner data pre-filled

3. **[📊 View Analytics] button** → Navigate to `Partner Analytics Dashboard` (FR4 backlog)
   - Target: Partner-specific analytics dashboard (if FR4 restored post-MVP)
   - Context: companyName, default date range (last 12 months)

4. **[View Full Analytics →] link** → Navigate to `Partner Analytics Dashboard` (FR4 backlog)
   - Target: Partner analytics dashboard (Engagement tab)
   - Context: companyName, engagement metrics focus

5. **[View All Activity →] link** → Navigate to `Activity tab`
   - Target: Same screen, Activity tab activated
   - Context: All filters cleared

6. **[View All Employees →] link** → Navigate to `Employee List Screen` (MISSING)
   - Target: Dedicated employee list/management screen
   - Context: companyName, filter by company

### Secondary Navigation (Data Interactions)

7. **Topic vote item click** → Navigate to `Topic Detail Screen` (story-2.2-topic-detail-screen.md)
   - Target: Topic detail page
   - Context: topicId, show partner voting priority

8. **Contact name click** → Navigate to `User Profile Screen` (story-1.20-user-profile.md)
   - Target: Full user profile
   - Context: userId, contact details

9. **Employee name click** → Navigate to `User Profile Screen` (story-1.20-user-profile.md)
   - Target: Employee profile
   - Context: userId, event attendance history

10. **Meeting item click** → Navigate to `Meeting Details Screen` (MISSING)
    - Target: Full meeting details page
    - Context: meetingId, agenda, attendees, materials

11. **[View Agenda] button** → Opens `Agenda Viewer Modal`
    - Target: Modal overlay showing meeting agenda
    - Context: meetingId, agenda items

12. **[View Minutes] button** → Opens `Meeting Minutes Viewer Modal`
    - Target: Modal overlay showing meeting minutes
    - Context: meetingId, minutes content, action items

13. **Activity timeline item click** → Expands `Activity Detail`
    - Target: Inline expansion showing full activity details
    - Context: activityId, related entities

### Event-Driven Navigation

14. **[📧 Send Email] button** → Opens `Email Composer Modal`
    - Target: Email composition modal
    - Context: partner contacts pre-filled, email templates available

15. **[📅 Schedule Meeting] button** → Navigate to `Meeting Scheduler` (story-6.2-partner-meetings.md)
    - Target: Meeting scheduling interface
    - Context: companyName, partner availability, calendar integration

16. **[📝 Add Note] button** → Opens `Add Note Modal`
    - Target: Note creation modal with rich text editor
    - Context: companyName, author (current organizer)

17. **[+ Schedule New Meeting] button** → Navigate to `Meeting Scheduler`
    - Target: Meeting scheduling workflow
    - Context: companyName, meeting type selection

18. **[Change Tier] button** → Opens `Tier Change Modal`
    - Target: Modal for tier selection with reason input
    - Context: currentTier, availableTiers, effective date selector

19. **[Export Partner Data] button** → Downloads file
    - Action: Generate and download partner data export
    - Target: File download (JSON or CSV)
    - Context: companyName, data export format selection

### Error States & Redirects

20. **Partner not found** → Redirect to Partner Directory
    - Condition: Invalid companyName or deleted partner
    - Action: Show error notification and redirect
    - Context: "Partner not found" message

21. **Unauthorized access** → Redirect to login
    - Condition: User not authenticated or insufficient permissions
    - Target: Login screen with return URL
    - Context: Redirect back to partner detail after authentication

22. **API failure** → Show error banner
    - Condition: API request fails or times out
    - Action: Display error with [Retry] button
    - Context: Error message, cached data shown if available

23. **Analytics not available** → Show backlog notice
    - Condition: User clicks [📊 View Analytics] but FR4 is in backlog
    - Action: Display modal: "Analytics dashboard coming soon (post-MVP feature)"
    - Context: Link to backlog documentation

24. **Settings tab restricted** → Hide tab
    - Condition: User is partner role (not organizer)
    - Action: Settings tab not displayed for partner users
    - Context: Role-based UI rendering

---

## Responsive Design Considerations

### Mobile Layout Changes

**Stacked Layout (320px - 768px):**
- **Header**: Logo and company name stacked, tier badge below
- **Quick Stats**: Vertical stacked cards (2x2 grid on larger mobiles)
- **Tabs**: Horizontal scrollable tab bar
- **Action Buttons**: Dropdown menu (⋮) for space efficiency
  - [📧 Send Email], [📅 Schedule], [📊 Analytics], [📝 Note] → ⋮ More Actions
- **Overview Tab**:
  - Partnership Details and Engagement Metrics: Stacked vertically
  - Engagement chart: Simplified horizontal bar chart
  - Recent Activity: Cards instead of list (one per row)
- **Contacts Tab**:
  - Contact cards: Full-width, collapsible secondary contacts
- **Meetings Tab**:
  - Meeting cards: Full-width with expandable details
- **Activity Tab**:
  - Timeline items: Full-width cards with timestamps
- **Notes Tab**:
  - Notes: Full-width cards with truncated content (tap to expand)
- **Settings Tab**: Accordion-style collapsible sections

**Tablet Layout (768px - 1024px):**
- Two-column layout for Overview tab (Partnership Details | Engagement Metrics)
- Side-by-side action buttons (full labels visible)
- Tabs with full labels (no scrolling needed)
- Meeting and activity items: Two-column grid

### Mobile-Specific Interactions

- **Swipe gestures**: Swipe between tabs (left/right)
- **Pull-to-refresh**: Refresh partner data and engagement scores
- **Bottom sheet**: Action menus, modals slide up from bottom
- **Floating action button (FAB)**: [📅 Schedule Meeting] as FAB (most common action)
- **Touch targets**: 44px minimum for all interactive elements
- **Sticky header**: Company name and tier badge sticky on scroll
- **Infinite scroll**: Load more activity/notes on scroll (replaces "Load More" button)
- **Tap to expand**: Tap engagement score to expand detailed breakdown
- **Long press**: Long-press note to show edit/delete menu

---

## Accessibility Notes

- **Keyboard Navigation**: Full tab navigation with logical focus order across all tabs
- **ARIA Labels**:
  - `aria-label="Partner engagement score: 92 percent, excellent"` on engagement bars
  - `aria-label="Navigate to {tab name} tab"` on tab buttons
  - `role="tabpanel"` on tab content areas
  - `aria-expanded` on collapsible sections
  - `aria-current="page"` on active tab
- **Screen Reader Announcements**:
  - Live region (`aria-live="polite"`) for engagement score updates
  - Tab change announcements ("Now viewing Contacts tab")
  - Success/error notifications announced
  - Activity timeline updates announced
- **Color Contrast**: WCAG 2.1 AA compliance
  - Tier badges: Gold on dark background (8:1 ratio)
  - Engagement bars: Green gradients with percentage text overlay
  - Warning indicators: Orange/red with ⚠️ icon for redundancy
- **Focus Indicators**: 2px solid blue outline on all focused elements
- **Alt Text**: Company logo has descriptive alt text (`alt="TechCorp AG logo, Premium partner"`)
- **Semantic HTML**:
  - `<main>` for partner detail content
  - `<nav>` for tab navigation
  - `<section>` for each tab panel
  - `<article>` for notes and activity items
  - `<dl>` for partnership details (term/definition pairs)
- **Skip Links**: "Skip to tab content" link at top of screen
- **Heading Hierarchy**: Proper h1-h6 structure (h1: Partner Name, h2: Tab Names, h3: Section Titles)
- **Focus Management**: Focus moved to tab panel when tab activated
- **Keyboard Shortcuts**:
  - `Ctrl + 1-6`: Navigate to tabs 1-6 (Overview, Contacts, Meetings, Activity, Notes, Settings)
  - `Ctrl + E`: Open email composer
  - `Ctrl + M`: Schedule meeting
  - `Ctrl + N`: Add new note

---

## State Management

### Local Component State

- `activeTab`: Current active tab ('overview' | 'contacts' | 'meetings' | 'activity' | 'notes' | 'settings')
- `activityFilters`: Activity tab filters { activityType: 'all', startDate: null, endDate: null }
- `notesFilter`: Filter for notes (all/private/shared)
- `meetingView`: Meeting tab view ('upcoming' | 'history')
- `editingNote`: ID of note currently being edited (null if none)
- `expandedActivityItems`: Set of activity IDs that are expanded
- `showEmailComposer`: Boolean for email modal visibility
- `showMeetingScheduler`: Boolean for meeting scheduler modal visibility
- `showTierChangeModal`: Boolean for tier change modal visibility

### Global State (Zustand Store)

- `partnerDetails`: Complete partner entity from API
- `partnerEngagement`: Current engagement metrics
- `partnerContacts`: List of contact persons
- `partnerStatistics`: Aggregate statistics
- `currentUser`: Current logged-in user (for permission checks)

### Server State (React Query)

- `usePartnerDetails(companyName)`: Partner company data, 5-minute cache, refetch on window focus
- `usePartnerEngagement(companyName)`: Engagement metrics, 30-second cache, auto-refetch every minute
- `usePartnerContacts(companyName)`: Contact information, 10-minute cache
- `usePartnerMeetings(companyName, upcoming)`: Meetings list, 2-minute cache
- `usePartnerActivity(companyName, filters)`: Activity timeline, 1-minute cache
- `usePartnerNotes(companyName)`: Notes list, 2-minute cache, optimistic updates on create/edit/delete
- `usePartnerStatistics(companyName)`: Statistics, 5-minute cache
- `usePartnerSettings(companyName)`: Settings (organizer only), 10-minute cache

**Query Keys Structure:**
```typescript
['partner', companyName]
['partner', companyName, 'engagement']
['partner', companyName, 'contacts']
['partner', companyName, 'meetings', { upcoming: boolean }]
['partner', companyName, 'activity', { filters }]
['partner', companyName, 'notes']
['partner', companyName, 'statistics']
['partner', companyName, 'settings']
```

### Real-Time Updates

- **WebSocket Connection**: `/ws/partners/{companyName}/updates`
  - Real-time engagement score updates when partner activities occur (vote, attendance, meeting RSVP)
  - Activity timeline updates (new activity items pushed)
  - Meeting updates (RSVP status changes, agenda updates)
  - Note updates (collaborative editing notifications)
  - Updates partner engagement bars and activity feed without refresh
  - Fallback to polling every 1 minute if WebSocket unavailable

---

## Form Validation Rules

### Add/Edit Contact Form

**Field-Level Validations:**
- **Name**: Required, min 2 chars, max 100 chars, alphabetic + spaces + hyphens
- **Title**: Required, min 2 chars, max 100 chars
- **Email**: Required, valid email format, max 255 chars, unique within partner contacts
- **Phone**: Optional, valid phone format (international), E.164 format preferred
- **LinkedIn**: Optional, valid LinkedIn URL format
- **Role**: Required, max 200 chars (dropdown or free text)
- **Preferred Language**: Required, enum (de | en)
- **Preferred Contact Method**: Required, enum (email | phone | linkedin)

**Form-Level Validations:**
- Only one primary contact allowed per partner
- At least one contact must exist for active partnerships

### Add/Edit Note Form

**Field-Level Validations:**
- **Content**: Required, min 10 chars, max 5000 chars
- **isPrivate**: Boolean (default: false)

**Form-Level Validations:**
- Note content must not be empty after trimming whitespace

### Tier Change Form

**Field-Level Validations:**
- **New Tier**: Required, enum (premium | gold | silver | bronze), must differ from current tier
- **Reason**: Required, min 20 chars, max 500 chars (for audit trail)
- **Effective Date**: Required, must be today or future date, max 1 year in future

**Form-Level Validations:**
- Cannot downgrade tier without approval (shows warning)
- Tier change requires confirmation modal with reason

### Partnership Settings Form

**Field-Level Validations:**
- **Partnership Status**: Required, enum (active | inactive)
- **Renewal Date**: Required, must be future date
- **Auto-Renewal**: Boolean (default: true)

**Form-Level Validations:**
- Deactivating partnership requires confirmation
- Disabling auto-renewal shows warning modal

---

## Edge Cases & Error Handling

- **Empty State (No Contacts)**:
  - Show "No contacts added yet" message
  - Display [+ Add Contact] CTA prominently
  - Warn: "Partnership requires at least one contact person"

- **Empty State (No Meetings)**:
  - Show "No meetings scheduled" message
  - Display [+ Schedule New Meeting] CTA
  - Suggest: "Schedule a strategic planning meeting with this partner"

- **Empty State (No Activity)**:
  - Show "No recent activity" message
  - Display last activity date if available
  - Suggest: "Send an email or schedule a meeting to engage"

- **Empty State (No Notes)**:
  - Show "No notes yet" message
  - Display [+ Add New Note] CTA
  - Suggestion: "Add notes to track partnership discussions and decisions"

- **Loading State**:
  - Display skeleton screens for each section
  - Show loading spinner in quick stats
  - Tabs remain interactive (load data on tab activation)

- **Error State (API Failure)**:
  - Show error banner: "Unable to load partner data. Please try again."
  - Provide [Retry] button to refetch data
  - Cache last successful data if available (show with "showing cached data" indicator)

- **Low Engagement Warning**:
  - Highlight engagement score in orange/red if <50%
  - Show ⚠️ warning icon with tooltip: "Engagement below expected level"
  - Suggest actions: "Consider scheduling a meeting or sending personalized email"
  - Display specific low-engagement area (e.g., "Low content interaction")

- **Meeting Scheduling Conflict**:
  - Show availability conflict message with proposed dates
  - Suggest alternative dates based on partner availability
  - Allow manual date/time entry with calendar picker
  - Notify partner contacts of proposed meeting via email

- **Tier Change Approval**:
  - Tier upgrades: Immediate (no approval needed)
  - Tier downgrades: Require admin approval (pending state)
  - Show pending status with approval workflow
  - Notify partner of tier change via email

- **Contact Email Bounce**:
  - Mark email as bounced in contact info
  - Show ⚠️ warning: "Email delivery failed - please verify contact email"
  - Suggest: "Update contact email address or use secondary contact"

- **Analytics Not Available (FR4 Backlog)**:
  - [📊 View Analytics] button shows tooltip: "Analytics dashboard coming soon (post-MVP)"
  - Clicking shows modal: "Full analytics dashboard will be available in a future release"
  - Link to roadmap or backlog item

- **Settings Tab Unauthorized (Partner User)**:
  - Settings tab completely hidden for partner-role users
  - Only organizer role can access Settings tab
  - If partner tries to access directly (URL manipulation), show 403 error

- **Partner Deactivation**:
  - Show confirmation modal: "Are you sure you want to deactivate this partnership?"
  - Warn about consequences: "Partner will lose access to analytics, voting, and meeting scheduling"
  - Require reason for deactivation (audit trail)
  - Send notification email to partner contacts

- **Data Export Timeout**:
  - Show progress indicator for large data exports
  - If timeout occurs, offer email delivery option
  - "Your export is being prepared. We'll email you when it's ready."

- **WebSocket Disconnection**:
  - Fall back to polling (every 1 minute) if WebSocket unavailable
  - Show connection status indicator (subtle banner at top)
  - Attempt WebSocket reconnection every 30 seconds

- **Stale Data Warning**:
  - Show "Last updated: X minutes ago" timestamp
  - If data is >10 minutes old, show [Refresh] button
  - Auto-refresh engagement metrics every 5 minutes

- **Note Deletion Confirmation**:
  - Show confirmation modal: "Delete this note? This action cannot be undone."
  - Require confirmation click
  - Show undo toast for 5 seconds after deletion (allow recovery)

---

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2025-10-04 | 1.0 | Initial wireframe creation for Partner Detail Screen | ux-expert |

---

## Review Notes

### Stakeholder Feedback

_To be added during review_

### Design Iterations

_To be documented as design evolves_

### Open Questions

1. **Analytics Dashboard Integration (FR4)**: When FR4 is restored from backlog, should analytics be embedded in this screen or separate dashboard?
   - Option A: Embed analytics charts in Overview tab (quick insights)
   - Option B: Separate analytics dashboard (deep-dive analysis)
   - Recommendation: Option A for overview, Option B for detailed analytics
   - Decision needed from product team

2. **Employee Management**: Should partner detail screen include full employee management or just summary?
   - Current: Summary with "View All Employees" link
   - Alternative: Full employee list as a tab
   - Decision needed based on organizer workflow research

3. **Meeting Scheduling Integration**: Integrate with external calendar systems (Outlook, Google) or internal only?
   - External integration provides better UX but increases complexity
   - Internal-only is faster to implement
   - Decision needed from technical team (feasibility + timeline)

4. **Engagement Score Algorithm**: Confirm final weighting for engagement score calculation?
   - Proposed: Event Attendance (40%), Topic Voting (30%), Meeting Participation (20%), Content Interaction (10%)
   - Alternative: Equal weighting (25% each)
   - Decision needed from business analytics team

5. **Tier Change Workflow**: Should tier changes require approval workflow or be immediate?
   - Current: Upgrades immediate, downgrades require approval
   - Alternative: All tier changes immediate (simpler UX)
   - Decision needed from partnership management team

6. **Notes Visibility**: Should notes be shareable with partner users or organizer-only?
   - Current: Organizer-only (private notes)
   - Alternative: Public/private toggle (collaborative notes)
   - Decision needed from security and partnership teams

7. **Data Retention**: How long should activity timeline and notes be retained?
   - Current: Indefinite (7-year standard for compliance)
   - Alternative: Configurable retention period per partner
   - Decision needed from compliance team

8. **Real-time Updates**: Is WebSocket connection necessary or is polling sufficient?
   - WebSocket provides better UX for engagement updates
   - Polling is simpler but less responsive
   - Decision needed from technical team (infrastructure cost vs. UX benefit)
