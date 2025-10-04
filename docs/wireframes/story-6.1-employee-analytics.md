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

**Note**: This wireframe has been updated to use the consolidated Partners API from Story 1.18 (109 → 20 endpoints, 82% reduction).

When the Detailed Employee Analytics screen loads, the following APIs are called to provide the necessary data:

1. **GET /api/v1/partners/{partnerId}/analytics?metrics=employees,attendance,engagement&timeframe=year&breakdown=events**
   - Consolidates: Former separate calls for overview, events, funnel, and content engagement
   - Query params: metrics (comma-separated: employees, attendance, engagement), timeframe (year), breakdown (events for event-level details), comparePeriod (previous)
   - Returns: Comprehensive employee analytics object with:
     - Overview: Total unique employees, events attended, average per event, repeat attendance rate, period comparisons
     - Events: List of events with attendance data, new vs returning attendees, ratings
     - Funnel: Engagement funnel data (invited, registered, attended, engaged, advocates), conversion rates, benchmarks
     - Content: Content views, downloads, shares, questions, feedback
   - Used for: Populate all analytics panels with single request
   - **Consolidation Benefit**: Single analytics call with flexible metrics parameter replaces 4 separate API calls (75% reduction)


---

## Action APIs

### Filtering & Date Range Selection

1. **GET /api/v1/partners/{partnerId}/analytics?metrics=employees,attendance,engagement&timeframe=custom&startDate=2025-01-01&endDate=2025-03-31**
   - Consolidates: Date range changes use same analytics endpoint with different timeframe
   - Query params: metrics (same as initial), timeframe (custom|last3months|last6months|year), startDate, endDate, comparePeriod, filter (JSON)
   - Returns: Updated analytics for selected date range and filters
   - Used for: Update all panels when date range or filters change
   - **Consolidation Benefit**: Same endpoint handles all date range and filter variations

### Export & Reporting

2. **POST /api/v1/partners/{partnerId}/export**
   - Consolidates: Analytics export using unified export endpoint
   - Payload: `{ exportType: "analytics", format: "excel|csv|pdf", dateRange, sections: ["overview", "events", "funnel", "employees"], includeCharts: boolean }`
   - Response: Export task ID, estimated completion time
   - Used for: Generate comprehensive employee analytics export
   - **Consolidation Benefit**: Same export endpoint used for all partner data types

3. **GET /api/v1/partners/{partnerId}/reports?type=export&taskId={taskId}**
   - Consolidates: Export download via reports endpoint
   - Query params: type=export, taskId (task identifier)
   - Returns: Download URL, expiration timestamp, generation status
   - Used for: Download generated export file
   - **Consolidation Benefit**: Unified reports endpoint for all downloadable content

### Chart & Table Interactions

4. **GET /api/v1/partners/{partnerId}/meetings/{eventId}?include=analytics,attendees,feedback**
    - Consolidates: Event details with analytics via include parameter
    - Query params: include (analytics, attendees, feedback)
    - Returns: Event details with analytics, anonymized attendee list, feedback, engagement metrics
    - Used for: Navigate to event detail view when clicking on event row
    - **Consolidation Benefit**: Meeting/event resource with analytics included

5. **GET /api/v1/partners/{partnerId}/analytics?metrics=employees&breakdown=funnel&stage=attended**
    - Consolidates: Funnel drilldown using analytics endpoint with stage parameter
    - Query params: metrics=employees, breakdown=funnel, stage (invited|registered|attended|engaged|advocates), timeframe
    - Returns: Detailed funnel stage data, employee lists, drop-off analysis
    - Used for: Drill down into specific funnel stage
    - **Consolidation Benefit**: Funnel details available via same analytics endpoint with breakdown parameter

6. **GET /api/v1/partners/{partnerId}/analytics?metrics=engagement&breakdown=content&contentId={id}**
    - Consolidates: Content engagement via analytics endpoint with content breakdown
    - Query params: metrics=engagement, breakdown=content, contentId (specific content piece)
    - Returns: Detailed content engagement metrics, employee interactions
    - Used for: View engagement details for specific content
    - **Consolidation Benefit**: Content analytics accessible via same flexible analytics endpoint

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
