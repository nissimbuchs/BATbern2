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

When the Partner Analytics Dashboard screen loads, the following APIs are called to provide the necessary data:

1. **GET /api/v1/partners/{partnerId}/analytics/summary**
   - Query params: year (2025)
   - Returns: Investment amount, employee attendance, speaker count, downloads, engagement rate, ROI ratio, year-over-year changes, key insights
   - Used for: Populate executive summary panel with all key metrics

2. **GET /api/v1/partners/{partnerId}/analytics/attendance-trends**
   - Query params: period (12months), groupBy (month)
   - Returns: Time-series data of employee attendance by month, trend lines, growth indicators
   - Used for: Display attendance over time chart

3. **GET /api/v1/partners/{partnerId}/analytics/employee-breakdown**
   - Query params: dimensions (level, topics)
   - Returns: Seniority level breakdown, topic interests with percentages
   - Used for: Populate level distribution and topics of interest panels

4. **GET /api/v1/partners/{partnerId}/events/upcoming**
   - Query params: limit (5), includeRegistrations (true)
   - Returns: Upcoming events with dates, employee registration counts, promotion status
   - Used for: Display upcoming events panel with registration information

5. **GET /api/v1/partners/{partnerId}/profile**
   - Returns: Partner company name, contact person name, logo, tier, contract details
   - Used for: Display partner information in header

6. **GET /api/v1/partners/{partnerId}/notifications/unread-count**
   - Returns: Count of unread notifications
   - Used for: Display notification badge

7. **GET /api/v1/partners/{partnerId}/quick-actions/available**
   - Returns: List of available quick actions with permissions and statuses
   - Used for: Populate quick actions panel

---

## Action APIs

### Analytics & Reporting

1. **GET /api/v1/partners/{partnerId}/analytics/detailed**
   - Query params: metrics (attendance, engagement, downloads), dateRange, format (json)
   - Returns: Comprehensive analytics data with drill-down capabilities
   - Used for: Navigate to detailed analytics view with all metrics

2. **POST /api/v1/partners/{partnerId}/reports/generate**
   - Payload: `{ reportType: "quarterly|annual|custom", dateRange, metrics: [], format: "pdf|excel|pptx" }`
   - Response: Report generation task ID, estimated completion time
   - Used for: Generate comprehensive analytics report

3. **GET /api/v1/partners/{partnerId}/reports/{reportId}/download**
   - Returns: Downloadable report file URL, expiration timestamp
   - Used for: Download generated report

4. **POST /api/v1/partners/{partnerId}/analytics/export**
   - Payload: `{ dataType: "attendance|engagement|downloads", format: "csv|excel", dateRange }`
   - Response: Export task ID, download URL (when ready)
   - Used for: Export raw analytics data for external analysis

5. **POST /api/v1/partners/{partnerId}/analytics/share**
   - Payload: `{ recipients: ["email@company.com"], message, dashboardUrl, accessLevel: "view|edit" }`
   - Response: Share confirmation, access tokens
   - Used for: Share analytics dashboard with team members

### Topic & Content Management

6. **POST /api/v1/partners/{partnerId}/topics/vote**
   - Payload: `{ topicId, votes: number, priority: "high|medium|low" }`
   - Response: Updated vote count, topic ranking
   - Used for: Cast votes on proposed topics

7. **POST /api/v1/partners/{partnerId}/topics/suggest**
   - Payload: `{ title, description, relevance, targetAudience, estimatedInterest }`
   - Response: Topic suggestion ID, review status
   - Used for: Suggest new topic for upcoming events

8. **GET /api/v1/partners/{partnerId}/topics/voting-status**
   - Returns: Active voting sessions, partner's votes, remaining votes, deadlines
   - Used for: Navigate to topic voting interface

### Event & Meeting Management

9. **GET /api/v1/partners/{partnerId}/events/{eventId}/details**
    - Returns: Full event details, registrations, promotion materials, internal communication tools
    - Used for: Navigate to event detail view

10. **POST /api/v1/partners/{partnerId}/events/{eventId}/promote**
    - Payload: `{ channels: ["email", "intranet"], customMessage, targetDepartments: [] }`
    - Response: Promotion campaign ID, reach estimate
    - Used for: Launch internal promotion campaign for event

11. **POST /api/v1/partners/{partnerId}/meetings/schedule**
    - Payload: `{ type: "quarterly|planning|review", proposedDates: [], attendees: [], agenda }`
    - Response: Meeting request ID, availability conflicts
    - Used for: Schedule partner planning meeting

12. **PUT /api/v1/partners/{partnerId}/meetings/{meetingId}/rsvp**
    - Payload: `{ response: "accept|decline|tentative", attendees: [] }`
    - Response: Updated meeting status, calendar invite
    - Used for: RSVP to partner meeting

13. **POST /api/v1/partners/{partnerId}/meetings/{meetingId}/calendar**
    - Response: iCal file URL for calendar integration
    - Used for: Add meeting to calendar

### Employee Management

14. **GET /api/v1/partners/{partnerId}/employees/list**
    - Query params: filters (department, level), sortBy (attendance), limit, offset
    - Returns: List of employees with attendance data, engagement metrics, interests
    - Used for: Navigate to employee list view

### Notifications & Settings

15. **GET /api/v1/partners/{partnerId}/notifications**
    - Query params: limit (20), includeRead (false)
    - Returns: List of notifications with types, timestamps, action links
    - Used for: Display notifications panel

16. **PUT /api/v1/partners/{partnerId}/notifications/{notificationId}/read**
    - Response: Updated notification status
    - Used for: Mark notification as read

17. **GET /api/v1/partners/{partnerId}/settings**
    - Returns: Partner portal settings, notification preferences, reporting preferences
    - Used for: Navigate to settings screen

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
