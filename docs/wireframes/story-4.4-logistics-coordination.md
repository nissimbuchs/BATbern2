# Story 4.4: Multi-Year Planning & Logistics Coordination - Wireframe

**Story**: Epic 4, Story 4.4 - Enhanced Content & Publishing
**Screen**: Multi-Year Planning Dashboard
**User Role**: Organizer
**Related FR**: FR21 (Multi-Year Planning)

---

## Multi-Year Planning Dashboard (FR21)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back                    Strategic Planning Dashboard                    [Export]   │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  View: [● Calendar] [○ Timeline] [○ Budget]    Years: [2024 ▼] [2025 ▼] [2026 ▼]   │
│                                                                                       │
│  ┌─────────────────────────────────────────────────────────────────────────────────┐ │
│  │                              2025 Planning Calendar                              │ │
│  │                                                                                   │ │
│  │     January          February         March            April            May       │ │
│  │  Mo Tu We Th Fr   Mo Tu We Th Fr   Mo Tu We Th Fr   Mo Tu We Th Fr   Mo Tu We  │ │
│  │        1  2  3                1    31  1  2  3  4       1  2  3  4             │ │
│  │   6  7  8  9 10    3  4  5  6  7    5  6  7  8  9    7  8  9 10 11    5  6  7  │ │
│  │  13 14 15 16 17   10 11 12 13 14   12 13 14 15 16   14 [P] 16 17 18   12 13 14  │ │
│  │  20 21 22 23 24   17 18 19 20 21   19 20 21 22 23   21 22 23 24 25   19 [E] 21 │ │
│  │  27 28 29 30 31   24 25 26 27 28   26 27 28 29 30   28 29 30         26 27 28  │ │
│  │                                                                                   │ │
│  │  Legend: [E] Event  [P] Partner Meeting  [V] Venue Booking  [D] Planning Deadline│ │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── VENUE RESERVATIONS ────────────┬─── PARTNER MEETINGS ──────────────────────┐  │
│  │                                   │                                            │  │
│  │  2025 Bookings:                   │  2025 Schedule:                           │  │
│  │  ┌─────────────────────────────┐  │  ┌──────────────────────────────────┐     │  │
│  │  │ Q1: Kursaal Bern      ✓     │  │  │ Apr 15: Spring Planning         │     │  │
│  │  │     Mar 20 - Confirmed      │  │  │   Attendees: 12 partners        │     │  │
│  │  │     Capacity: 250           │  │  │   Agenda: 2026 topics           │     │  │
│  │  │                             │  │  │   Status: Room booked ✓         │     │  │
│  │  │ Q2: UniS Bern         ⚠️    │  │  │                                  │     │  │
│  │  │     May 20 - Tentative     │  │  │ Oct 10: Autumn Review           │     │  │
│  │  │     Awaiting confirmation  │  │  │   Attendees: TBD                │     │  │
│  │  │                             │  │  │   Agenda: Budget & metrics      │     │  │
│  │  │ Q3: Tech Park         ○     │  │  │   Status: Planning              │     │  │
│  │  │     Sep - Not booked       │  │  └──────────────────────────────────┘     │  │
│  │  │     [Check Availability]   │  │                                            │  │
│  │  │                             │  │  Recurring Tasks:                         │  │
│  │  │ Q4: Casino Bern       ✓     │  │  • Quarterly budget review               │  │
│  │  │     Nov 15 - Confirmed     │  │  • Topic voting (30 days before)         │  │
│  │  └─────────────────────────────┘  │  • Sponsor reports (14 days before)      │  │
│  │                                   │                                            │  │
│  │  2026 Advance Bookings:           │  [Schedule Meeting] [View 2024 History]  │  │
│  │  • Q1: Negotiating with Kursaal   │                                            │  │
│  │  • Q2-Q4: Open for planning       │                                            │  │
│  └───────────────────────────────────┴────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── BUDGET TRACKING ────────────────────────────────────────────────────────────┐  │
│  │                                                                                 │  │
│  │  2025 Annual Budget: CHF 120,000        Allocated: CHF 89,000 (74%)           │  │
│  │                                                                                 │  │
│  │  Q1: CHF 30,000  ████████████████████████████░░░░░░  85% allocated            │  │
│  │  Q2: CHF 30,000  ████████████████████░░░░░░░░░░░░░░  60% allocated            │  │
│  │  Q3: CHF 30,000  ████████████░░░░░░░░░░░░░░░░░░░░░░  40% planning             │  │
│  │  Q4: CHF 30,000  ████████░░░░░░░░░░░░░░░░░░░░░░░░░░  25% committed            │  │
│  │                                                                                 │  │
│  │  Major Expenses:           Sponsors Committed:                                 │  │
│  │  • Venues: CHF 40,000     • UBS: CHF 25,000 ✓                                 │  │
│  │  • Catering: CHF 35,000   • Swiss Re: CHF 20,000 ✓                            │  │
│  │  • Speakers: CHF 15,000   • Credit Suisse: Pending                            │  │
│  │  • Marketing: CHF 10,000  • Swisscom: CHF 15,000 ✓                            │  │
│  │                                                                                 │  │
│  │  [Download Report] [Update Budget] [Forecast 2026]                            │  │
│  └─────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## Key Interactive Elements

- **Multi-Year Calendar**: View and plan events across multiple years
- **Venue Tracking**: Manage venue reservations with status indicators
- **Partner Meetings**: Schedule and coordinate quarterly partner meetings
- **Budget Dashboard**: Track expenses and sponsorships across quarters
- **Advance Booking**: Plan venue reservations 12-18 months ahead
- **Recurring Tasks**: Automated reminders for regular activities

## Functional Requirements Met

- **FR21**: Multi-year planning and logistics coordination
- **Venue Management**: Track bookings across multiple venues and years
- **Budget Planning**: Quarterly and annual budget allocation and tracking
- **Partner Coordination**: Schedule regular partner meetings
- **Long-term Planning**: 2-3 year advance planning capabilities
- **Financial Forecasting**: Predict future budgets and sponsorships

## User Interactions

1. **Switch Years**: Toggle between current, next, and following year
2. **Book Venue**: Check availability and make advance reservations
3. **Schedule Meeting**: Coordinate partner meetings with calendar integration
4. **Track Budget**: Monitor spending and commitments by quarter
5. **View Calendar**: See all events, meetings, and deadlines at a glance
6. **Export Data**: Generate reports for board or partner meetings

## Technical Notes

- Calendar integration with venue booking systems
- Multi-year data model with historical tracking
- Budget forecasting based on historical spending patterns
- Automated reminders for recurring tasks
- Integration with partner portal for meeting coordination
- Conflict detection for venue double-booking
- Financial reporting and export capabilities

---

## API Requirements

APIs needed to load and display data for this screen:

### Initial Page Load

1. **GET /api/v1/planning/dashboard**
   - Retrieve multi-year planning overview
   - Query params: `years=[2024,2025,2026]`
   - Response includes: events calendar, venue bookings, partner meetings, budget summary
   - Used for: Complete dashboard initialization
   - Aggregated: Single call for multi-year planning data

2. **GET /api/v1/planning/calendar**
   - Retrieve planning calendar for selected years
   - Query params: `years=[2025,2026]`, `view=calendar|timeline|budget`
   - Response includes: all events, meetings, deadlines, venue bookings
   - Used for: Calendar visualization at top

3. **GET /api/v1/venues/reservations**
   - Retrieve venue reservations by year
   - Query params: `year=2025`, `includeAdvance=true`
   - Response includes: booking status, venue details, capacity, confirmation status
   - Used for: Venue reservations panel

4. **GET /api/v1/partners/meetings**
   - Retrieve partner meeting schedule
   - Query params: `year=2025`, `includeRecurring=true`
   - Response includes: meeting dates, attendees, agendas, room bookings, status
   - Used for: Partner meetings panel

5. **GET /api/v1/budget/annual**
   - Retrieve annual budget data
   - Query params: `year=2025`, `breakdown=quarterly`
   - Response includes: total budget, allocated amounts, major expenses, sponsors
   - Used for: Budget tracking panel

6. **GET /api/v1/budget/forecast**
   - Retrieve budget forecast for future years
   - Query params: `targetYear=2026`, `basedOnYear=2025`
   - Response includes: projected budget, historical trends, recommendations
   - Used for: Budget forecasting

---

## Action APIs

APIs called by user interactions and actions:

### View Control

1. **PUT /api/v1/planning/view-preference**
   - Triggered by: View toggle buttons (Calendar, Timeline, Budget)
   - Payload: `{ view: "calendar|timeline|budget" }`
   - Response: View preference saved
   - Updates: Main display area re-renders with selected view

2. **GET /api/v1/planning/timeline**
   - Triggered by: [○ Timeline] view selection
   - Query params: `years=[2025,2026]`
   - Response: Timeline data optimized for Gantt-style display
   - Used for: Timeline view rendering

### Year Selection

3. **GET /api/v1/planning/dashboard**
   - Triggered by: Year dropdown selection changes
   - Query params: `years=[selected years]`
   - Response: Updated data for selected years
   - Updates: All panels refresh with new year data

### Venue Management

4. **GET /api/v1/venues/availability**
   - Triggered by: [Check Availability] button
   - Query params: `venueId`, `quarter=Q3`, `year=2025`
   - Response: Available dates, capacity, pricing
   - Opens: Availability calendar modal

5. **POST /api/v1/venues/reservations**
   - Triggered by: Making new venue reservation
   - Payload: `{ venueId, eventDate, capacity, status: "tentative|confirmed" }`
   - Response: Reservation created, booking ID
   - Side effects:
     - Sends confirmation email
     - Updates calendar
     - Creates budget line item
     - Checks for conflicts

6. **PUT /api/v1/venues/reservations/{reservationId}**
   - Triggered by: Updating venue reservation status
   - Payload: `{ status: "tentative|confirmed|cancelled" }`
   - Response: Updated reservation details
   - Side effects:
     - Sends status update email
     - Updates budget if cancelled
     - Releases hold if cancelled

7. **GET /api/v1/venues/{venueId}/details**
   - Triggered by: Clicking on venue name in booking
   - Response: Full venue details, amenities, contacts, past events
   - Opens: Venue details modal

### Partner Meeting Management

8. **POST /api/v1/partners/meetings**
   - Triggered by: [Schedule Meeting] button
   - Opens: Meeting scheduling modal
   - Payload: `{ date, attendees: [], agenda, roomBooking }`
   - Response: Meeting created, calendar invites sent
   - Side effects:
     - Sends calendar invites to partners
     - Books meeting room
     - Creates agenda document

9. **PUT /api/v1/partners/meetings/{meetingId}**
   - Triggered by: Updating meeting details
   - Payload: `{ date, attendees, agenda, status }`
   - Response: Updated meeting details
   - Side effect: Sends update emails to attendees

10. **GET /api/v1/partners/meetings/history**
    - Triggered by: [View 2024 History] link
    - Query params: `year=2024`
    - Response: Historical meeting data, attendance, outcomes
    - Opens: Meeting history modal or page

11. **POST /api/v1/partners/meetings/{meetingId}/notes**
    - Triggered by: Adding meeting notes/outcomes
    - Payload: `{ notes, decisions: [], actionItems: [] }`
    - Response: Meeting notes saved
    - Used for: Documenting meeting outcomes

### Budget Management

12. **PUT /api/v1/budget/annual**
    - Triggered by: [Update Budget] button
    - Opens: Budget editing modal
    - Payload: `{ year, totalBudget, quarterlyAllocations: [], expenses: [], sponsors: [] }`
    - Response: Budget updated
    - Validation: Ensures allocations sum to total

13. **PUT /api/v1/budget/quarterly/{quarter}**
    - Triggered by: Editing quarterly budget allocation
    - Payload: `{ year, quarter, amount, notes }`
    - Response: Quarterly budget updated
    - Updates: Progress bars, allocation percentages

14. **POST /api/v1/budget/expenses**
    - Triggered by: Adding expense line item
    - Payload: `{ category, amount, quarter, year, vendor }`
    - Response: Expense created
    - Updates: Budget allocation and remaining amount

15. **PUT /api/v1/budget/sponsors/{sponsorId}**
    - Triggered by: Updating sponsor commitment status
    - Payload: `{ amount, status: "committed|pending|received", paymentDate }`
    - Response: Sponsor commitment updated
    - Updates: Committed sponsors list, budget balance

### Forecasting

16. **POST /api/v1/budget/forecast/generate**
    - Triggered by: [Forecast 2026] button
    - Payload: `{ targetYear: 2026, basedOnYears: [2024, 2025] }`
    - Response: Forecast data with projections
    - Processing: ML-based forecasting using historical patterns
    - Opens: Forecast report modal

17. **GET /api/v1/budget/forecast/{year}**
    - Triggered by: Viewing forecast for specific year
    - Response: Forecast details, assumptions, confidence levels
    - Opens: Detailed forecast modal

### Export & Reporting

18. **GET /api/v1/planning/export**
    - Triggered by: [Export] button (top-right)
    - Query params: `format=pdf|excel|csv`, `years=[2025,2026]`, `sections=all`
    - Response: Download URL or file stream
    - Downloads: Comprehensive planning report

19. **GET /api/v1/budget/report**
    - Triggered by: [Download Report] button in budget section
    - Query params: `year=2025`, `format=pdf|excel`
    - Response: Budget report file
    - Downloads: Detailed financial report

### Recurring Tasks

20. **GET /api/v1/planning/recurring-tasks**
    - Triggered by: Auto-load on page load
    - Response: List of recurring tasks with next due dates
    - Used for: Displaying recurring tasks list

21. **POST /api/v1/planning/recurring-tasks/{taskId}/complete**
    - Triggered by: Marking recurring task as complete
    - Payload: `{ completedDate, notes }`
    - Response: Task marked complete, next occurrence scheduled
    - Updates: Task list, next due date

22. **POST /api/v1/planning/recurring-tasks**
    - Triggered by: Creating new recurring task
    - Payload: `{ name, frequency: "quarterly|monthly|annually", startDate, reminder }`
    - Response: Recurring task created
    - Side effect: Schedules reminder notifications

---

## Navigation Map

Screen transitions triggered by actions and events:

### Primary Navigation

1. **← Back Button**
   - **Target**: Event Management Dashboard (Story 1.16)
   - **Context**: Return to main dashboard

2. **[Export]** (top-right)
   - **Action**: Downloads planning report
   - **No Navigation**: Remains on screen
   - **Options**: PDF, Excel, CSV formats
   - **Content**: Multi-year planning data, budgets, schedules

### View Switching

3. **[● Calendar] / [○ Timeline] / [○ Budget]** toggle
   - **No Navigation**: Remains on screen
   - **Updates**: Main display area changes view
   - **Persistence**: Preference saved for next visit

4. **Year Dropdowns**
   - **Action**: Changes displayed year range
   - **No Navigation**: Remains on screen
   - **Updates**: All panels refresh with selected year data
   - **Range**: Current year -1 to +2 years

### Calendar Interaction

5. **Calendar Date Click**
   - **If Event**: Opens event details modal
   - **If Empty**: Opens "Create Event" or "Book Venue" modal
   - **Type**: Modal overlay
   - **Context**: Date pre-filled in form

6. **Legend Item Click** ([E], [P], [V], [D])
   - **Action**: Filters calendar to show only that type
   - **No Navigation**: Remains on screen
   - **Updates**: Calendar highlights filtered items

### Venue Management Navigation

7. **[Check Availability]**
   - **Target**: Venue availability modal
   - **Type**: Modal overlay with calendar picker
   - **Content**: Available dates, capacity, pricing for selected venue
   - **Actions**: [Book Tentative], [Book Confirmed], [Get Quote]

8. **Venue Booking Click** (e.g., "Q1: Kursaal Bern")
   - **Target**: Venue booking details modal
   - **Type**: Modal overlay
   - **Content**: Full booking details, venue info, edit options
   - **Actions**: [Edit Booking], [Confirm], [Cancel], [View Contract]

9. **Venue Name Link**
   - **Target**: Venue details page or modal
   - **Type**: Modal or full page
   - **Content**: Venue info, amenities, past events, contacts
   - **Actions**: [Contact Venue], [View on Map], [Check Other Dates]

### Partner Meeting Navigation

10. **[Schedule Meeting]**
    - **Target**: Meeting scheduling modal
    - **Type**: Modal overlay
    - **Content**: Date picker, attendee selector, agenda builder
    - **Submit**: Creates meeting, sends invites
    - **Integration**: Calendar sync to partner accounts

11. **Partner Meeting Card Click** (e.g., "Apr 15: Spring Planning")
    - **Target**: Meeting details modal
    - **Type**: Modal overlay
    - **Content**: Full meeting details, attendees, agenda, room booking
    - **Actions**: [Edit], [Cancel], [Send Reminder], [Add Notes]

12. **[View 2024 History]**
    - **Target**: Meeting history page or modal
    - **Type**: Full page or modal
    - **Content**: Past meetings with outcomes, decisions, attendance
    - **Features**: Search, filter by quarter, export

### Budget Management Navigation

13. **[Update Budget]**
    - **Target**: Budget editing modal
    - **Type**: Modal overlay with form
    - **Content**: Editable budget fields, quarterly breakdowns
    - **Validation**: Real-time validation, allocation warnings
    - **Submit**: Saves budget changes

14. **Quarterly Budget Bar Click** (e.g., "Q1: CHF 30,000")
    - **Target**: Quarterly budget details modal
    - **Type**: Modal overlay
    - **Content**: Detailed expenses, commitments, remaining budget
    - **Actions**: [Add Expense], [View Transactions], [Adjust Allocation]

15. **Expense Category Click** (e.g., "Venues: CHF 40,000")
    - **Target**: Expense category details modal
    - **Type**: Modal overlay
    - **Content**: All expenses in category, invoices, vendors
    - **Actions**: [Add Expense], [View Invoice], [Mark Paid]

16. **Sponsor Status Click** (e.g., "UBS: CHF 25,000 ✓")
    - **Target**: Sponsor details modal
    - **Type**: Modal overlay
    - **Content**: Sponsor agreement, payment schedule, benefits delivered
    - **Actions**: [Update Status], [Send Invoice], [View Contract]

### Forecasting Navigation

17. **[Forecast 2026]**
    - **Target**: Budget forecast modal
    - **Type**: Modal overlay with charts
    - **Content**: Projected budget, confidence intervals, assumptions
    - **Methodology**: ML-based on historical patterns
    - **Actions**: [Adjust Assumptions], [Export Forecast], [Apply to Budget]

18. **Forecast Result Click**
    - **Target**: Detailed forecast breakdown modal
    - **Type**: Modal with detailed analytics
    - **Content**: Line-item projections, scenarios, risk factors
    - **Actions**: [Download Report], [Share with Board]

### Export & Reporting Navigation

19. **[Download Report]** (budget section)
    - **Action**: Generates and downloads budget report
    - **Format Options**: PDF, Excel
    - **No Navigation**: Remains on screen
    - **Content**: Financial statements, expense details, sponsor list

20. **Recurring Task Click**
    - **Target**: Task details modal
    - **Type**: Modal overlay
    - **Content**: Task description, history, next occurrence
    - **Actions**: [Mark Complete], [Edit], [Skip Occurrence], [Delete]

### Event-Driven Navigation

21. **On Venue Booking Confirmed**
    - **No Navigation**: Remains on screen
    - **Feedback**: Success toast "Venue confirmed for Q2"
    - **Updates**: Calendar updated, budget allocated, status changed

22. **On Meeting Scheduled**
    - **No Navigation**: Remains on screen
    - **Feedback**: Success toast with calendar link
    - **Updates**: Calendar shows new meeting
    - **Notification**: Attendees receive calendar invites

23. **On Budget Updated**
    - **No Navigation**: Remains on screen
    - **Feedback**: "Budget updated" toast
    - **Updates**: All budget visualizations refresh
    - **Validation**: Warns if over-allocated

24. **On Venue Conflict Detected**
    - **Feedback**: Warning modal "Venue already booked for this date"
    - **Options**: [Choose Different Date], [Override], [Contact Venue]
    - **No Auto-Save**: Prevents double-booking

25. **On Budget Threshold Exceeded**
    - **Feedback**: Warning banner "Q2 budget 95% allocated"
    - **Action**: [Review Budget], [Request Increase]
    - **Highlight**: Affected quarter in budget panel

26. **On Sponsor Commitment Received**
    - **Notification**: Dashboard notification or email
    - **Entry**: Link opens budget section
    - **Updates**: Sponsor status changed to ✓, budget updated

27. **On Recurring Task Due**
    - **Notification**: Email reminder or dashboard notification
    - **Entry**: Link opens task details
    - **Action**: [Complete Task] button prominent

### Advanced Actions

28. **Timeline View** (when selected)
    - **Display**: Gantt-style timeline with events, venues, meetings
    - **Interaction**: Drag to reschedule (with validation)
    - **Click**: Opens event/booking details
    - **Zoom**: Adjust timeline scale (day/week/month)

29. **Budget View** (when selected)
    - **Display**: Detailed financial dashboard
    - **Features**: Charts, expense tables, cash flow
    - **Interaction**: Click categories for drill-down
    - **Export**: Enhanced export options for financial data

### Error States

30. **On Venue Unavailable**
    - **Feedback**: "Venue not available for selected dates"
    - **Suggestions**: Alternative dates or venues
    - **Actions**: [View Alternatives], [Contact Venue], [Cancel]

31. **On Budget Calculation Error**
    - **Feedback**: Error message with details
    - **Action**: [Recalculate], [Check Allocations], [Contact Support]

32. **On Export Failure**
    - **Feedback**: "Unable to generate report"
    - **Actions**: [Retry], [Try Different Format], [Contact Support]

### Mobile-Specific

33. **Mobile Calendar View**
    - **Layout**: List view instead of grid
    - **Interaction**: Tap to expand date details
    - **Simplified**: Key info only

34. **Mobile Budget View**
    - **Layout**: Stacked cards per quarter
    - **Scrollable**: Vertical scroll through quarters
    - **Quick Actions**: Tap for details, swipe for actions

---