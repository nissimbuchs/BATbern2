# Story: Detailed Employee Analytics - Wireframe

**Story**: Epic 6, Story 1
**Screen**: Detailed Employee Analytics
**User Role**: Partner
**Related FR**: FR4 (Employee Tracking)

---

## 2. Detailed Employee Analytics (FR4)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back to Dashboard              Employee Engagement Analytics           [Export]    │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  Date Range: [Last 12 Months ▼]   Compare: [Previous Period ▼]   Filter: [All ▼]   │
│                                                                                       │
│  ┌──── ATTENDANCE OVERVIEW ────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Total Unique Employees: 247        Events Attended: 8                         │ │
│  │  Average per Event: 31              Repeat Attendance: 67%                     │ │
│  │                                                                                  │ │
│  │  ┌────────────────────────────────────────────────────────────────────────────┐ │ │
│  │  │ Event                          Date      Attendees  New    Return  Rating  │ │ │
│  │  │ ─────────────────────────────────────────────────────────────────────────  │ │ │
│  │  │ Spring Conference 2024         Mar 15    42         12     30      4.8/5   │ │ │
│  │  │ Cloud Workshop                 Apr 20    28         8      20      4.7/5   │ │ │
│  │  │ Security Summit                Jun 10    35         15     20      4.6/5   │ │ │
│  │  │ Autumn Conference 2024         Sep 15    38         10     28      4.9/5   │ │ │
│  │  │ AI/ML Workshop                 Oct 25    31         18     13      4.5/5   │ │ │
│  │  │ Year-End Tech Talk            Dec 10    45         5      40      4.7/5   │ │ │
│  │  │ New Year Kickoff              Jan 15    28         7      21      4.6/5   │ │ │
│  │  │                                                                             │ │ │
│  │  │ Total                                    247        75     172     4.7/5   │ │ │
│  │  └────────────────────────────────────────────────────────────────────────────┘ │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── EMPLOYEE JOURNEY MAP ────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Engagement Funnel                         Content Engagement                   │ │
│  │                                                                                  │ │
│  │  Invited        ████████████ 450          Views      █████████ 3,421           │ │
│  │  Registered     ████████ 320              Downloads  ██████ 2,103              │ │
│  │  Attended       ██████ 247                Shares     ███ 567                   │ │
│  │  Engaged        █████ 220                 Questions  ██ 134                    │ │
│  │  Advocates      ██ 89                     Feedback   █ 89                      │ │
│  │                                                                                  │ │
│  │  Conversion Rate: 55% (Industry Avg: 42%)                                       │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── DEPARTMENT DEEP DIVE ────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Select Department: [IT Infrastructure ▼]                                       │ │
│  │                                                                                  │ │
│  │  ┌────────────────────────────────────────────────────────────────────────────┐ │ │
│  │  │ Team              Manager         Size   Attended  Rate   Top Interest      │ │ │
│  │  │ ──────────────────────────────────────────────────────────────────────────  │ │ │
│  │  │ Cloud Platform    Sarah Chen      12     10        83%    Kubernetes        │ │ │
│  │  │ Security Ops      Mark Weber      8      7         88%    Zero Trust        │ │ │
│  │  │ Network Team      Anna Lopez      15     11        73%    Service Mesh     │ │ │
│  │  │ Database Admin    John Smith      6      4         67%    Cloud DB          │ │ │
│  │  │ DevOps            Peter Muller    10     9         90%    CI/CD             │ │ │
│  │  └────────────────────────────────────────────────────────────────────────────┘ │ │
│  │                                                                                  │ │
│  │  Key Insights:                                                                   │ │
│  │  • DevOps team has highest engagement (90%)                                     │ │
│  │  • Security topics draw cross-team attendance                                   │ │
│  │  • Database team needs more targeted content                                    │ │
│  │                                                                                  │ │
│  │  [Email Team Report] [Schedule Team Session] [Request Custom Content]           │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌──── INDIVIDUAL EMPLOYEE TRACKING ─────────────────────────────────────────────────┐│
│  │                                                                                  ││
│  │  Search Employee: [🔍 Name or ID...]                    Privacy Mode: ON 🔒     ││
│  │                                                                                  ││
│  │  Top Attendees (Anonymized)           Learning Progress                         ││
│  │  1. Employee_4821 - 8 events          ████████████░░░░ 75% K8s Path           ││
│  │  2. Employee_9234 - 7 events          ██████░░░░░░░░░░ 40% Security Path      ││
│  │  3. Employee_3421 - 7 events          █████████░░░░░░░ 60% DevOps Path        ││
│  │                                                                                  ││
│  │  Note: Individual data is anonymized for privacy compliance                     ││
│  └──────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## API Requirements

### Initial Page Load APIs

When the Detailed Employee Analytics screen loads, the following APIs are called to provide the necessary data:

1. **GET /api/v1/partners/{partnerId}/employees/analytics/overview**
   - Query params: dateRange (last12months), comparePeriod (previousPeriod)
   - Returns: Total unique employees, events attended, average per event, repeat attendance rate, period comparisons
   - Used for: Populate attendance overview panel with key metrics

2. **GET /api/v1/partners/{partnerId}/employees/analytics/events**
   - Query params: dateRange, sortBy (date), limit, offset
   - Returns: List of events with attendance data, new vs returning attendees, ratings
   - Used for: Display event attendance table with detailed breakdown

3. **GET /api/v1/partners/{partnerId}/employees/analytics/funnel**
   - Query params: dateRange
   - Returns: Engagement funnel data (invited, registered, attended, engaged, advocates), conversion rates, industry benchmarks
   - Used for: Populate engagement funnel visualization

4. **GET /api/v1/partners/{partnerId}/employees/analytics/content-engagement**
   - Query params: dateRange
   - Returns: Content views, downloads, shares, questions asked, feedback provided
   - Used for: Display content engagement metrics in employee journey map

5. **GET /api/v1/partners/{partnerId}/departments**
   - Returns: List of departments with employee counts
   - Used for: Populate department selection dropdown

6. **GET /api/v1/partners/{partnerId}/departments/{departmentId}/teams**
   - Query params: includeMetrics (true)
   - Returns: Team breakdown with managers, sizes, attendance rates, top interests, engagement metrics
   - Used for: Populate department deep dive table (initial load shows IT Infrastructure)

7. **GET /api/v1/partners/{partnerId}/departments/{departmentId}/insights**
   - Returns: AI-generated insights about department engagement patterns, recommendations
   - Used for: Display key insights section with actionable recommendations

8. **GET /api/v1/partners/{partnerId}/employees/analytics/top-attendees**
   - Query params: limit (10), anonymize (true)
   - Returns: Anonymized list of top attendees with event counts, learning path progress
   - Used for: Display top attendees with privacy-compliant anonymization

9. **GET /api/v1/partners/{partnerId}/settings/privacy**
   - Returns: Privacy mode settings, data access controls, anonymization level
   - Used for: Display privacy mode indicator

---

## Action APIs

### Filtering & Date Range Selection

1. **GET /api/v1/partners/{partnerId}/employees/analytics/overview**
   - Query params: dateRange (custom|last3months|last6months|last12months), comparePeriod, filters
   - Returns: Updated metrics for selected date range
   - Used for: Update all panels when date range changes

2. **GET /api/v1/partners/{partnerId}/employees/analytics/filters**
   - Returns: Available filter options (departments, event types, engagement levels)
   - Used for: Populate filter dropdown options

### Export & Reporting

3. **POST /api/v1/partners/{partnerId}/employees/analytics/export**
   - Payload: `{ format: "excel|csv|pdf", dateRange, sections: ["overview", "events", "funnel", "departments"], includeCharts: boolean }`
   - Response: Export task ID, estimated completion time
   - Used for: Generate comprehensive analytics export

4. **GET /api/v1/partners/{partnerId}/employees/analytics/export/{taskId}/download**
   - Returns: Download URL, expiration timestamp
   - Used for: Download generated export file

### Department & Team Actions

5. **GET /api/v1/partners/{partnerId}/departments/{departmentId}/teams**
   - Query params: includeMetrics (true), dateRange
   - Returns: Team data for selected department
   - Used for: Update department deep dive when department selection changes

6. **POST /api/v1/partners/{partnerId}/departments/{departmentId}/report/email**
   - Payload: `{ recipients: [], customMessage, includeRecommendations: boolean }`
   - Response: Email sent confirmation, delivery status
   - Used for: Email team report to managers

7. **POST /api/v1/partners/{partnerId}/departments/{departmentId}/sessions/request**
   - Payload: `{ teamId, proposedDates: [], topics: [], format: "workshop|presentation|lunch-and-learn" }`
   - Response: Session request ID, review status
   - Used for: Schedule custom team session

8. **POST /api/v1/partners/{partnerId}/departments/{departmentId}/content/request**
   - Payload: `{ topics: [], targetAudience, urgency: "high|medium|low", description }`
   - Response: Content request ID, estimated timeline
   - Used for: Request custom content for specific team needs

### Individual Employee Tracking

9. **GET /api/v1/partners/{partnerId}/employees/search**
   - Query params: query (name or ID), anonymize (based on privacy mode)
   - Returns: Search results with employee data (anonymized if privacy mode ON)
   - Used for: Search employee by name or ID

10. **GET /api/v1/partners/{partnerId}/employees/{employeeId}/details**
    - Query params: anonymize (based on privacy mode)
    - Returns: Employee attendance history, learning progress, interests, engagement metrics
    - Used for: View individual employee analytics (if privacy allows)

11. **GET /api/v1/partners/{partnerId}/employees/{employeeId}/learning-paths**
    - Returns: Active learning paths, progress percentages, completed modules, upcoming content
    - Used for: Display learning progress details for individual employee

### Chart & Table Interactions

12. **GET /api/v1/partners/{partnerId}/employees/analytics/events/{eventId}/details**
    - Returns: Detailed event analytics, attendee list (anonymized), feedback, engagement metrics
    - Used for: Navigate to event detail view when clicking on event row

13. **GET /api/v1/partners/{partnerId}/employees/analytics/funnel/drilldown**
    - Query params: stage (invited|registered|attended|engaged|advocates), dateRange
    - Returns: Detailed data for specific funnel stage, employee lists, drop-off analysis
    - Used for: Drill down into specific funnel stage

14. **GET /api/v1/partners/{partnerId}/employees/analytics/content/{contentId}/engagement**
    - Returns: Detailed engagement metrics for specific content piece, employee interactions
    - Used for: View engagement details for specific content

### Team Manager Actions

15. **POST /api/v1/partners/{partnerId}/teams/{teamId}/managers/contact**
    - Payload: `{ subject, message, includeData: boolean }`
    - Response: Contact request sent confirmation
    - Used for: Contact team manager about engagement

### Privacy & Settings

16. **PUT /api/v1/partners/{partnerId}/settings/privacy**
    - Payload: `{ privacyMode: "on|off", anonymizationLevel: "full|partial|none" }`
    - Response: Updated privacy settings
    - Used for: Toggle privacy mode and anonymization settings

---

## Navigation Map

### Primary Navigation Actions

1. **← Back to Dashboard button** → Navigate back to `Partner Analytics Dashboard`
   - Returns to main partner dashboard
   - Preserves any active filters or date ranges

2. **[Export] button** → Triggers export flow
   - Opens export options modal
   - Select format and sections
   - Generates file for download
   - No screen navigation

3. **Date Range dropdown** → Updates analytics data
   - Changes date range for all panels
   - Refreshes all metrics and charts
   - No screen navigation

4. **Compare dropdown** → Updates comparison data
   - Changes comparison period
   - Shows year-over-year or period-over-period changes
   - No screen navigation

5. **Filter dropdown** → Updates filtered view
   - Filters by department, event type, or engagement level
   - Refreshes all panels with filtered data
   - No screen navigation

6. **Event row click in attendance table** → Navigate to `Event Details Screen`
   - Shows full event information
   - Attendee list (anonymized if privacy mode ON)
   - Feedback and ratings
   - Content engagement for that event

7. **Department dropdown (Deep Dive section)** → Updates department view
   - Loads teams for selected department
   - Updates insights for that department
   - No screen navigation

8. **Team row click** → Navigate to `Team Analytics Screen`
   - Team-specific metrics
   - Individual team member engagement (anonymized)
   - Manager contact info
   - Team learning paths

9. **[Email Team Report] button** → Opens email modal
   - Enter recipient emails
   - Customize message
   - Sends report
   - No screen navigation after send

10. **[Schedule Team Session] button** → Navigate to `Team Session Request Screen`
    - Custom session builder
    - Topic selection
    - Date/time picker
    - Format selection (workshop/presentation)

11. **[Request Custom Content] button** → Navigate to `Content Request Form Screen`
    - Topic specification
    - Audience targeting
    - Urgency selection
    - Learning objectives

12. **Top attendee click** → Navigate to `Employee Detail Screen` (if privacy allows)
    - Individual attendance history
    - Learning path progress
    - Content interactions
    - May show anonymized view based on privacy settings

13. **Learning path progress bar click** → Navigate to `Learning Path Details Screen`
    - Path curriculum
    - Completion status
    - Recommended next steps
    - Related upcoming events

### Secondary Navigation (Data Interactions)

14. **Engagement funnel stage click** → Opens drill-down modal
    - Detailed data for that stage
    - Employee lists for that stage
    - Drop-off analysis
    - Conversion improvement suggestions

15. **Content engagement metric click** → Opens content engagement details modal
    - Top viewed content
    - Most downloaded presentations
    - Most shared resources
    - Content performance by topic

16. **Manager name click** → Opens manager contact modal
    - Manager contact information
    - Option to send message
    - Team roster preview

17. **Top Interest click** → Navigate to `Topic Analytics Screen`
    - Content related to topic
    - Employee interest trends
    - Upcoming events for topic
    - Content recommendations

18. **Privacy Mode toggle** → Updates data display
    - Switches between identified and anonymized views
    - Re-renders employee data
    - Updates API calls with anonymization parameters
    - No screen navigation

### Event-Driven Navigation

19. **Date range change** → Refreshes all data
    - Updates all metrics for new date range
    - Re-renders charts and tables
    - Updates comparisons
    - No screen navigation

20. **Filter change** → Refreshes filtered data
    - Updates all panels with filtered data
    - Adjusts metrics and visualizations
    - No screen navigation

21. **Export generation complete** → Shows notification with download link
    - Download available notification
    - Click to download file
    - Option to generate another export

22. **New employee registration during session** → Updates metrics in real-time
    - Increments attendance counts
    - Updates funnel if applicable
    - Subtle animation on changed values
    - No screen navigation

23. **Team session request submitted** → Shows confirmation notification
    - Request ID provided
    - Expected response timeline
    - Links to request tracking
    - No automatic navigation

24. **Content request submitted** → Shows confirmation notification
    - Request ID provided
    - Estimated delivery timeline
    - Links to request status
    - No automatic navigation

25. **Email report sent** → Shows success notification
    - Delivery confirmation
    - List of recipients
    - No screen navigation

26. **Manager contact sent** → Shows success notification
    - Message delivered confirmation
    - Expected response time
    - No screen navigation

27. **Privacy settings changed** → Updates all employee data displays
    - Re-anonymizes or de-anonymizes data
    - Refreshes employee tracking section
    - Updates search results
    - No screen navigation

28. **Industry benchmark update** → Shows info notification
    - New benchmark data available
    - Updated comparisons displayed
    - Highlights changes in performance
    - No screen navigation

---
