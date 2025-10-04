# Story: Partner Analytics Dashboard - Wireframe

**Story**: Epic 6, Story 1
**Screen**: Partner Analytics Dashboard
**User Role**: Partner
**Related FR**: FR4 (Analytics)

---

## 1. Partner Analytics Dashboard (Main Landing)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ BATbern Partner Portal                      UBS - Thomas Mueller ▼  [🔔] [User Settings] │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  Welcome back, Thomas! Here's your sponsorship ROI overview.                         │
│                                                                                       │
│  ┌──── EXECUTIVE SUMMARY ──────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  2025 Sponsorship Performance                     Investment: CHF 25,000        │ │
│  │                                                                                  │ │
│  │  ┌─────────────┬─────────────┬─────────────┬─────────────┬─────────────┐      │ │
│  │  │    247      │    42       │   3,421     │    89%      │   4.2:1     │      │ │
│  │  │  EMPLOYEES  │  SPEAKERS   │  DOWNLOADS  │ ENGAGEMENT  │    ROI      │      │ │
│  │  │  ATTENDED   │  FROM UBS   │  BY UBS     │    RATE     │   RATIO     │      │ │
│  │  │    ⬆ 15%    │    ⬆ 8%     │    ⬆ 22%    │    ⬆ 5%     │    ⬆ 0.3    │      │ │
│  │  └─────────────┴─────────────┴─────────────┴─────────────┴─────────────┘      │ │
│  │                                                                                  │ │
│  │  Key Insight: Strong employee engagement with growing attendance trends         │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── EMPLOYEE ATTENDANCE TRENDS ─────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Attendance Over Time (Last 12 Months)                    [Export Data]         │ │
│  │                                                                                  │ │
│  │  80 ┤ ╭─────────────────────────────────╮                                      │ │
│  │  60 ┤ │                      ╭──────────╯                                      │ │
│  │  40 ┤ │           ╭─────────╯                                                  │ │
│  │  20 ┤ ╰──────────╯                                                             │ │
│  │   0 └──┬────┬────┬────┬────┬────┬────┬────┬────┬────┬────┬────┬──            │ │
│  │      Jan  Feb  Mar  Apr  May  Jun  Jul  Aug  Sep  Oct  Nov  Dec               │ │
│  │                                                                                  │ │
│  │  Level Distribution                   Topics of Interest                          │ │
│  │  ┌──────────────┐                  ┌─────────────────┐                           │ │
│  │  │ Senior: 35%  │                  │ Cloud: 42%  ███ │                           │ │
│  │  │ Mid: 45%     │                  │ DevOps: 28% ██  │                           │ │
│  │  │ Junior: 20%  │                  │ Security: 30% ██│                           │ │
│  │  └──────────────┘                  └─────────────────┘                           │ │
│  │                                                                                  │ │
│  │  [View Detailed Analytics] [Download Report] [Share with Team]                  │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── QUICK ACTIONS ────────────┬──── UPCOMING EVENTS ─────────────────────────┐   │
│  │                                │                                               │   │
│  │  [📊 Generate Report]          │  Spring Conference 2025                      │   │
│  │  [🗳️ Vote on Topics]           │  May 15 • 12 employees registered           │   │
│  │  [💡 Suggest Topic]            │  [View Details] [Promote Internally]        │   │
│  │  [📅 Partner Meeting]          │                                               │   │
│  │  [👥 Employee List]            │  Partner Planning Meeting                   │   │
│  │                                │  Apr 15 • Quarterly Review                   │   │
│  │                                │  [RSVP] [Add to Calendar]                   │   │
│  └────────────────────────────────┴───────────────────────────────────────────────┘  │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### Key Features:
- **Executive summary** with key metrics at a glance
- **ROI calculation** clearly displayed
- **Employee attendance** tracking with trends
- **Comparative analysis** with industry benchmarks
- **Quick access** to common partner actions

---

## API Requirements

### Initial Page Load APIs

**Note**: This wireframe has been updated to use the consolidated Partners API from Story 1.18 (109 → 20 endpoints, 82% reduction).

When the Partner Analytics Dashboard screen loads, the following APIs are called to provide the necessary data:

1. **GET /api/v1/partners/{partnerId}?include=analytics,meetings,settings,employees**
   - Consolidates: Former separate calls for profile, settings, and related data
   - Returns: Complete partner entity including company name, contact person, logo, tier, contract details, embedded analytics summary, upcoming meetings, employee summary
   - Used for: Populate partner header, quick stats, and initial data for all dashboard panels
   - **Consolidation Benefit**: Single request replaces 5+ separate API calls, reducing initial page load time

2. **GET /api/v1/partners/{partnerId}/analytics?metrics=attendance,employees,engagement&timeframe=year**
   - Consolidates: Former `/analytics/summary`, `/analytics/attendance-trends`, `/analytics/employee-breakdown` endpoints
   - Query params: metrics (comma-separated: attendance, employees, engagement, downloads, roi), timeframe (month|quarter|year), groupBy (month for trends)
   - Returns: Comprehensive analytics object with:
     - Summary: Investment amount, speaker count, engagement rate, ROI ratio, year-over-year changes, key insights
     - Attendance trends: Time-series data by month, trend lines, growth indicators
     - Employee breakdown: Seniority level distribution, topic interests with percentages
   - Used for: Populate executive summary panel, attendance chart, employee distribution, and topics of interest
   - **Consolidation Benefit**: Flexible metrics selection eliminates need for multiple specialized analytics endpoints

3. **GET /api/v1/partners/{partnerId}/meetings?filter={"upcoming":true}&page=1&limit=5**
   - Consolidates: Former `/events/upcoming` endpoint with partner context
   - Query params: filter (JSON filter for upcoming meetings), limit (5)
   - Returns: Upcoming meetings/events with dates, employee registration counts, promotion status, RSVP status
   - Used for: Display upcoming events panel with registration information
   - **Consolidation Benefit**: Standard filtering pattern consistent across all resource endpoints

4. **GET /api/v1/partners/{partnerId}/notifications?filter={"read":false}**
   - Consolidates: Former `/notifications/unread-count` into flexible notifications endpoint
   - Query params: filter (JSON filter for unread notifications)
   - Returns: List of unread notifications with count, types, timestamps, action links
   - Used for: Display notification badge count and notification panel
   - **Consolidation Benefit**: Returns both count and notification details in single call

---

## Action APIs

### Analytics & Reporting

1. **GET /api/v1/partners/{partnerId}/analytics?metrics=attendance,engagement,downloads,roi&timeframe=year**
   - Consolidates: Former `/analytics/detailed` endpoint
   - Query params: metrics (flexible selection), timeframe (month|quarter|year), breakdown (role, department), format (json)
   - Returns: Comprehensive analytics data with drill-down capabilities for selected metrics
   - Used for: Navigate to detailed analytics view with all metrics
   - **Consolidation Benefit**: Same endpoint as initial load, just with different metrics parameter

2. **POST /api/v1/partners/{partnerId}/reports**
   - Consolidates: Former `/reports/generate` endpoint
   - Payload: `{ type: "roi|engagement|quarterly|annual|custom", dateRange, metrics: [], format: "pdf|excel|pptx" }`
   - Response: Report ID, generation task ID, estimated completion time
   - Used for: Generate comprehensive analytics report
   - **Consolidation Benefit**: Unified reports endpoint with type parameter

3. **GET /api/v1/partners/{partnerId}/reports?type=roi**
   - Consolidates: Former `/reports/{reportId}/download` into list/detail pattern
   - Query params: type (filter by report type), limit, page
   - Returns: List of generated reports with download URLs, expiration timestamps, generation status
   - Used for: View available reports and download links
   - **Consolidation Benefit**: RESTful pattern, single endpoint for report listing and access

4. **POST /api/v1/partners/{partnerId}/analytics/export**
   - Maintains existing endpoint (already consolidated in Story 1.18)
   - Payload: `{ metrics: ["attendance","engagement","downloads"], format: "csv|excel", dateRange }`
   - Response: Export task ID, download URL (when ready)
   - Used for: Export raw analytics data for external analysis
   - **Consolidation Benefit**: Accepts flexible metrics array instead of single dataType

5. **POST /api/v1/partners/{partnerId}/export**
   - Consolidates: Analytics sharing and general partner data export
   - Payload: `{ exportType: "analytics|contacts|meetings|all", format: "json|csv|excel", includeCharts: boolean, recipients: ["email@company.com"], message, accessLevel: "view|edit" }`
   - Response: Export confirmation, share tokens if recipients specified, download URL
   - Used for: Export/share partner data and analytics with team members
   - **Consolidation Benefit**: Unified export endpoint for all partner data types

### Topic & Content Management

6. **PUT /api/v1/partners/{partnerId}/topics/votes**
   - Consolidates: Former individual `/topics/vote` endpoint into batch operation
   - Payload: `{ sessionId, votes: [{ topicId, votes: number, priority: "high|medium|low" }] }`
   - Response: Updated vote counts for all topics, partner's updated rankings, influence score
   - Used for: Cast or update votes on multiple topics in single request
   - **Consolidation Benefit**: Batch voting eliminates need for N individual API calls when voting on multiple topics

7. **POST /api/v1/partners/{partnerId}/topics/suggest**
   - Maintains existing endpoint (already RESTful in Story 1.18)
   - Payload: `{ title, description, relevance, targetAudience, estimatedInterest }`
   - Response: Topic suggestion ID, review status
   - Used for: Suggest new topic for upcoming events

8. **GET /api/v1/partners/{partnerId}/topics/votes?sessionId={current}**
   - Consolidates: Former `/topics/voting-status` endpoint
   - Query params: sessionId (filter to active session)
   - Returns: Voting session details, partner's current votes, remaining vote allocation, deadlines, topic rankings
   - Used for: Navigate to topic voting interface with current voting state
   - **Consolidation Benefit**: Standard list/detail pattern replaces specialized status endpoint

### Event & Meeting Management

9. **GET /api/v1/partners/{partnerId}/meetings/{meetingId}?include=agenda,attendees,materials**
    - Consolidates: Former `/events/{eventId}/details` with expanded include parameter
    - Query params: include (comma-separated: agenda, attendees, materials, promotion)
    - Returns: Full meeting/event details, registrations, promotion materials, internal communication tools in single response
    - Used for: Navigate to event detail view with all related data
    - **Consolidation Benefit**: Include parameter eliminates need for separate detail API calls

10. **POST /api/v1/partners/{partnerId}/meetings/{meetingId}/promote**
    - Maintains existing endpoint (already RESTful in Story 1.18)
    - Payload: `{ channels: ["email", "intranet"], customMessage, targetDepartments: [] }`
    - Response: Promotion campaign ID, reach estimate
    - Used for: Launch internal promotion campaign for event

11. **POST /api/v1/partners/{partnerId}/meetings**
    - Consolidates: Former `/meetings/schedule` endpoint using standard POST for creation
    - Payload: `{ type: "quarterly|planning|review", proposedDates: [], attendees: [], agenda }`
    - Response: Created meeting ID, availability conflicts, meeting request status
    - Used for: Schedule partner planning meeting
    - **Consolidation Benefit**: Standard RESTful POST pattern for creating meetings

12. **PUT /api/v1/partners/{partnerId}/meetings/{meetingId}**
    - Consolidates: Former `/meetings/{meetingId}/rsvp` into standard update endpoint
    - Payload: `{ rsvpResponse: "accept|decline|tentative", attendees: [], notes }`
    - Response: Updated meeting with RSVP status, calendar invite
    - Used for: Update meeting RSVP status and attendee list
    - **Consolidation Benefit**: Uses standard PUT for updates instead of specialized RSVP endpoint

13. **GET /api/v1/partners/{partnerId}/meetings/{meetingId}/calendar.ics**
    - Consolidates: Calendar export using standard resource extension pattern
    - Returns: iCal file content for calendar integration
    - Used for: Download meeting as calendar file
    - **Consolidation Benefit**: RESTful resource representation with .ics extension

### Employee Management

14. **GET /api/v1/partners/{partnerId}/employees?filter={"department":"IT"}&page=1&limit=50**
    - Consolidates: Former `/employees/list` endpoint using standard filtering
    - Query params: filter (JSON filter object), sort (attendance|-attendance), page, limit
    - Returns: Paginated list of employees with attendance data, engagement metrics, interests
    - Used for: Navigate to employee list view with filtering
    - **Consolidation Benefit**: Consistent JSON filter pattern across all endpoints

### Notifications & Settings

15. **GET /api/v1/partners/{partnerId}/notifications?filter={"read":false}&limit=20**
    - Already updated in Initial Page Load section (consolidated endpoint)
    - Query params: filter (JSON filter), limit, page
    - Returns: List of notifications with types, timestamps, action links, read status
    - Used for: Display notifications panel

16. **PUT /api/v1/partners/{partnerId}/notifications/read**
    - Consolidates: Batch mark-as-read instead of individual notification updates
    - Payload: `{ notificationIds: ["id1", "id2"] }` or `{ markAllRead: true }`
    - Response: Count of notifications marked as read
    - Used for: Mark single or multiple notifications as read
    - **Consolidation Benefit**: Batch operation eliminates N API calls for marking multiple notifications

17. **GET /api/v1/partners/{partnerId}?include=settings**
    - Consolidates: Settings embedded in main partner resource with include parameter
    - Returns: Partner entity with settings object (notification preferences, reporting preferences, portal configuration)
    - Used for: Navigate to settings screen with current preferences
    - **Consolidation Benefit**: Settings available via include parameter on main resource

---

## Navigation Map

### Primary Navigation Actions

1. **Partner name dropdown menu** → Navigate to:
   - `Partner Profile Screen` - View/edit company profile
   - `Switch Partner Account Screen` - For users managing multiple partners
   - `Logout` - End session

2. **[🔔] Notifications icon** → Navigate to `Notifications Panel` (overlay)
   - Shows recent notifications
   - Links to relevant screens
   - Mark as read functionality

3. **[User Settings] button** → Navigate to `Partner Settings Screen`
   - Notification preferences
   - Reporting configuration
   - User management
   - Integration settings

4. **[Export Data] button (attendance chart)** → Triggers export flow
   - Shows format selection modal
   - Generates CSV/Excel file
   - Provides download link
   - No screen navigation

5. **[View Detailed Analytics] button** → Navigate to `Detailed Analytics Screen`
   - Deep-dive charts and tables
   - Custom date range selection
   - Metric drill-downs
   - Comparative analysis tools

6. **[Download Report] button** → Navigate to `Report Builder Screen`
   - Select report type and metrics
   - Choose date range and format
   - Customize branding and content
   - Generate and download report

7. **[Share with Team] button** → Opens share modal
   - Enter team member emails
   - Set access permissions
   - Send dashboard link
   - No screen navigation after share

8. **[📊 Generate Report] quick action** → Navigate to `Report Builder Screen`
   - Pre-filled with common report type
   - Quick generation workflow

9. **[🗳️ Vote on Topics] quick action** → Navigate to `Topic Voting Screen`
   - Active voting sessions
   - Partner vote allocation
   - Topic details and relevance scores

10. **[💡 Suggest Topic] quick action** → Navigate to `Topic Suggestion Form Screen`
    - Topic submission wizard
    - Relevance assessment
    - Interest estimation

11. **[📅 Partner Meeting] quick action** → Navigate to `Meeting Scheduler Screen`
    - Calendar view
    - Meeting type selection
    - Availability checking
    - Agenda builder

12. **[📈 Compare with Peers] quick action** → Navigate to `Peer Comparison Screen`
    - Anonymized industry benchmarks
    - Percentile rankings
    - Best practices insights

13. **[💰 Budget Planning] quick action** → Navigate to `Budget Planning Screen`
    - Investment overview
    - ROI projections
    - Cost breakdown
    - Multi-year planning

14. **[👥 Employee List] quick action** → Navigate to `Employee Analytics Screen`
    - Searchable/filterable employee list
    - Individual attendance records
    - Engagement scores
    - Department analytics

15. **Upcoming event [View Details] button** → Navigate to `Event Details Screen`
    - Full event information
    - Registration list
    - Promotion tools
    - Materials preview

16. **Upcoming event [Promote Internally] button** → Navigate to `Internal Promotion Campaign Screen`
    - Communication template builder
    - Channel selection (email, intranet)
    - Target department selection
    - Campaign tracking

17. **Partner meeting [RSVP] button** → Opens RSVP modal
    - Accept/decline/tentative options
    - Attendee selection
    - Message to organizer
    - Updates meeting status inline

18. **Partner meeting [Add to Calendar] button** → Triggers calendar download
    - Generates iCal file
    - Includes meeting details
    - Downloads immediately
    - No screen navigation

### Secondary Navigation (Data Interactions)

19. **Click on metric card (employees/speakers/downloads)** → Navigate to metric-specific detail view
    - Time-series chart for selected metric
    - Breakdown by dimensions
    - Export options

20. **Click on chart data point** → Opens drill-down modal
    - Detailed data for selected time period
    - Employee list for that period
    - Contextual insights

21. **Click on department in breakdown** → Navigate to `Department Analytics Screen`
    - Department-specific metrics
    - Employee list filtered by department
    - Trend analysis

22. **Click on level in distribution** → Navigate to `Level Analytics Screen`
    - Seniority-level metrics
    - Career development insights
    - Topic preferences by level

23. **Click on topic in interests** → Navigate to `Topic Performance Screen`
    - Content related to topic
    - Employee engagement with topic
    - Speaker expertise in topic
    - Related events

### Event-Driven Navigation

24. **Metric updates** → Requires manual page reload
    - User must refresh page to see updated metrics
    - No automatic updates

25. **New employee registration notification** → Shows toast notification
    - Links to event details
    - Updates registration count
    - No automatic navigation

26. **Report generation complete** → Shows notification
    - Download link available
    - Option to view online
    - Can navigate to report viewer

27. **Meeting invitation received** → Shows notification badge
    - Links to meeting details
    - RSVP action available
    - Adds to upcoming events section

28. **Topic voting deadline approaching** → Shows alert banner
    - Links to voting screen
    - Shows remaining time
    - Displays uncast votes

29. **Quarterly review due** → Shows reminder card
    - Links to report builder
    - Pre-fills quarterly report
    - Shows previous quarter comparison

30. **Employee engagement milestone** → Shows celebration notification
    - Highlights achievement in dashboard
    - Suggests recognition actions
    - Links to employee list

31. **ROI threshold exceeded** → Shows success notification
    - Highlights ROI metric
    - Suggests case study creation
    - Links to detailed analytics

32. **Budget utilization alert** → Shows info banner
    - Links to budget planning
    - Shows spending forecast
    - Suggests optimization opportunities

---
