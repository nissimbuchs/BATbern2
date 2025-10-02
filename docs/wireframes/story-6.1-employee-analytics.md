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

### Chart & Table Interactions

5. **GET /api/v1/partners/{partnerId}/employees/analytics/events/{eventId}/details**
    - Returns: Detailed event analytics, attendee list (anonymized), feedback, engagement metrics
    - Used for: Navigate to event detail view when clicking on event row

6. **GET /api/v1/partners/{partnerId}/employees/analytics/funnel/drilldown**
    - Query params: stage (invited|registered|attended|engaged|advocates), dateRange
    - Returns: Detailed data for specific funnel stage, employee lists, drop-off analysis
    - Used for: Drill down into specific funnel stage

7. **GET /api/v1/partners/{partnerId}/employees/analytics/content/{contentId}/engagement**
    - Returns: Detailed engagement metrics for specific content piece, employee interactions
    - Used for: View engagement details for specific content

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

### Secondary Navigation (Data Interactions)

7. **Engagement funnel stage click** → Opens drill-down modal
    - Detailed data for that stage
    - Employee lists for that stage
    - Drop-off analysis
    - Conversion improvement suggestions

8. **Content engagement metric click** → Opens content engagement details modal
    - Top viewed content
    - Most downloaded presentations
    - Most shared resources
    - Content performance by topic

### Event-Driven Navigation

9. **Date range change** → Refreshes all data
    - Updates all metrics for new date range
    - Re-renders charts and tables
    - Updates comparisons
    - No screen navigation

10. **Filter change** → Refreshes filtered data
    - Updates all panels with filtered data
    - Adjusts metrics and visualizations
    - No screen navigation

11. **Export generation complete** → Shows notification with download link
    - Download available notification
    - Click to download file
    - Option to generate another export

12. **New employee registration during session** → Updates metrics in real-time
    - Increments attendance counts
    - Updates funnel if applicable
    - Subtle animation on changed values
    - No screen navigation

13. **Industry benchmark update** → Shows info notification
    - New benchmark data available
    - Updated comparisons displayed
    - Highlights changes in performance
    - No screen navigation

---
