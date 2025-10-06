# Story 4.4: Multi-Year Planning & Logistics Coordination - Wireframe

**Story**: Epic 4, Story 4.4 - Enhanced Content & Publishing
**Screen**: Multi-Year Planning Dashboard
**User Role**: Organizer
**Related FR**: FR21 (Multi-Year Planning)

**API Consolidation Update** (2025-10-04):
This wireframe has been updated to use consolidated APIs from Stories 1.17, 1.18, and 1.27:
- Replaced `/planning/dashboard` with domain-specific endpoints (Events, Venues, Partners)
- Calendar views now use `/api/v1/events` with filtering (Story 1.17)
- Venue management uses standard `/api/v1/venues` CRUD (Story 1.27)
- Partner meetings use `/api/v1/partners/{id}/meetings` (Story 1.18)
- Planning tasks use `/api/v1/planning/milestones` (Story 1.27)
- **API Reduction**: From 15 fragmented endpoints to 4 consolidated domain APIs

---

## Multi-Year Planning Dashboard (FR21)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back                    Strategic Planning Dashboard                    [Export]   │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  View: [● Calendar] [○ Timeline]    Years: [2024 ▼] [2025 ▼] [2026 ▼]   │
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
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## Key Interactive Elements

- **Multi-Year Calendar**: View and plan events across multiple years
- **Venue Tracking**: Manage venue reservations with status indicators
- **Partner Meetings**: Schedule and coordinate quarterly partner meetings
- **Advance Booking**: Plan venue reservations 12-18 months ahead
- **Recurring Tasks**: Automated reminders for regular activities

## Functional Requirements Met

- **FR21**: Multi-year planning and logistics coordination
- **Venue Management**: Track bookings across multiple venues and years
- **Partner Coordination**: Schedule regular partner meetings
- **Long-term Planning**: 2-3 year advance planning capabilities

## User Interactions

1. **Switch Years**: Toggle between current, next, and following year
2. **Book Venue**: Check availability and make advance reservations
3. **Schedule Meeting**: Coordinate partner meetings with calendar integration
4. **View Calendar**: See all events, meetings, and deadlines at a glance
5. **Export Data**: Generate reports for board or partner meetings

## Technical Notes

- Calendar integration with venue booking systems
- Multi-year data model with historical tracking
- Automated reminders for recurring tasks
- Integration with partner portal for meeting coordination
- Conflict detection for venue double-booking

---

## API Requirements

APIs needed to load and display data for this screen:

### Initial Page Load

1. **GET /api/v1/events**
   - Retrieve events for calendar view across multiple years
   - Query params: `filter={"year": [2024,2025,2026]}&include=venue&fields=id,title,date,status,venue`
   - Response includes: event list with venue data for calendar display
   - Used for: Calendar visualization with event markers
   - Source: Story 1.17 (Events API Consolidation)

2. **GET /api/v1/venues**
   - Retrieve venue reservations by year
   - Query params: `filter={"year": 2025, "status": ["tentative", "confirmed"]}&page=1&limit=20`
   - Response includes: venue bookings with status, capacity, dates
   - Used for: Venue reservations panel
   - Source: Story 1.27 (Remaining Resources - Venues)

3. **GET /api/v1/partners/{partnerId}/meetings**
   - Retrieve partner meeting schedule
   - Query params: `filter={"year": 2025}&page=1&limit=50`
   - Response includes: meeting dates, attendees, agendas, room bookings, status
   - Used for: Partner meetings panel
   - Source: Story 1.18 (Partners API Consolidation)

4. **GET /api/v1/planning/timeline**
   - Retrieve planning timeline for timeline view
   - Query params: `eventId={}&years=[2025,2026]`
   - Response includes: Gantt-style timeline data for events, milestones, deadlines
   - Used for: Timeline view when toggled
   - Source: Story 1.27 (Remaining Resources - Planning)

---

## Action APIs

APIs called by user interactions and actions:

### View Control

1. **PATCH /api/v1/users/{userId}/preferences**
   - Triggered by: View toggle buttons (Calendar, Timeline)
   - Payload: `{ "planningView": "calendar|timeline" }`
   - Response: User preference saved
   - Updates: Main display area re-renders with selected view
   - Source: Standard user preferences pattern

2. **GET /api/v1/planning/timeline**
   - Triggered by: [○ Timeline] view selection
   - Query params: `eventId={}&years=[2025,2026]`
   - Response: Timeline data optimized for Gantt-style display
   - Used for: Timeline view rendering
   - Source: Story 1.27 (Remaining Resources - Planning)

### Year Selection

3. **GET /api/v1/events**
   - Triggered by: Year dropdown selection changes
   - Query params: `filter={"year": [selected years]}&include=venue&fields=id,title,date,status,venue`
   - Response: Updated event data for selected years
   - Updates: Calendar panel refreshes
   - Source: Story 1.17 (Events API Consolidation)

### Venue Management

4. **GET /api/v1/venues/{venueId}**
   - Triggered by: [Check Availability] button
   - Query params: `include=availability,bookings&filter={"quarter":"Q3","year":2025}`
   - Response: Venue details with available dates, capacity, pricing
   - Opens: Availability calendar modal
   - Source: Story 1.27 (Remaining Resources - Venues)

5. **POST /api/v1/venues**
   - Triggered by: Making new venue reservation
   - Payload: `{ "venueId": "ven-123", "eventDate": "2025-09-15", "capacity": 250, "status": "tentative|confirmed" }`
   - Response: Reservation created with booking ID
   - Side effects:
     - Sends confirmation email
     - Updates calendar
     - Creates budget line item
     - Checks for conflicts
   - Source: Story 1.27 (Remaining Resources - Venues)

6. **PUT /api/v1/venues/{venueId}**
   - Triggered by: Updating venue reservation status
   - Payload: `{ "status": "tentative|confirmed|cancelled", "bookingId": "..." }`
   - Response: Updated reservation details
   - Side effects:
     - Sends status update email
     - Updates budget if cancelled
     - Releases hold if cancelled
   - Source: Story 1.27 (Remaining Resources - Venues)

7. **GET /api/v1/venues/{venueId}**
   - Triggered by: Clicking on venue name in booking
   - Query params: `include=amenities,contacts,history`
   - Response: Full venue details, amenities, contacts, past events
   - Opens: Venue details modal
   - Source: Story 1.27 (Remaining Resources - Venues)

### Partner Meeting Management

8. **POST /api/v1/partners/{partnerId}/meetings**
   - Triggered by: [Schedule Meeting] button
   - Opens: Meeting scheduling modal
   - Payload: `{ "date": "2025-04-15T14:00:00Z", "attendees": ["partner-id-1", "partner-id-2"], "agenda": "...", "roomBooking": {...} }`
   - Response: Meeting created, calendar invites sent
   - Side effects:
     - Sends calendar invites to partners
     - Books meeting room
     - Creates agenda document
   - Source: Story 1.18 (Partners API Consolidation)

9. **PUT /api/v1/partners/{partnerId}/meetings/{meetingId}**
   - Triggered by: Updating meeting details
   - Payload: `{ "date": "...", "attendees": [...], "agenda": "...", "status": "scheduled|completed|cancelled" }`
   - Response: Updated meeting details
   - Side effect: Sends update emails to attendees
   - Source: Story 1.18 (Partners API Consolidation)

10. **GET /api/v1/partners/{partnerId}/meetings**
    - Triggered by: [View 2024 History] link
    - Query params: `filter={"year": 2024}&page=1&limit=50`
    - Response: Historical meeting data, attendance, outcomes
    - Opens: Meeting history modal or page
    - Source: Story 1.18 (Partners API Consolidation)

11. **PATCH /api/v1/partners/{partnerId}/meetings/{meetingId}**
    - Triggered by: Adding meeting notes/outcomes
    - Payload: `{ "notes": "...", "decisions": [...], "actionItems": [...] }`
    - Response: Meeting notes saved
    - Used for: Documenting meeting outcomes
    - Source: Story 1.18 (Partners API Consolidation)

### Export & Reporting

12. **POST /api/v1/events/export**
    - Triggered by: [Export] button (top-right)
    - Payload: `{ "format": "pdf|excel|csv", "filter": {"years": [2025,2026]}, "sections": "all" }`
    - Response: Download URL or file stream
    - Downloads: Comprehensive planning report
    - Source: Story 1.17 (Events API Consolidation)

### Recurring Tasks

13. **GET /api/v1/planning/milestones**
    - Triggered by: Auto-load on page load
    - Query params: `filter={"type": "recurring", "status": "active"}&page=1&limit=20`
    - Response: List of recurring tasks/milestones with next due dates
    - Used for: Displaying recurring tasks list
    - Source: Story 1.27 (Remaining Resources - Planning)

14. **PUT /api/v1/planning/milestones/{milestoneId}**
    - Triggered by: Marking recurring task as complete
    - Payload: `{ "status": "completed", "completedDate": "2025-01-15T10:00:00Z", "notes": "..." }`
    - Response: Milestone marked complete, next occurrence scheduled
    - Updates: Task list, next due date
    - Source: Story 1.27 (Remaining Resources - Planning)

15. **POST /api/v1/planning/milestones**
    - Triggered by: Creating new recurring task
    - Payload: `{ "name": "...", "type": "recurring", "frequency": "quarterly|monthly|annually", "startDate": "...", "reminder": {...} }`
    - Response: Recurring milestone created
    - Side effect: Schedules reminder notifications
    - Source: Story 1.27 (Remaining Resources - Planning)

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

3. **[● Calendar] / [○ Timeline]** toggle
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

### Export & Reporting Navigation

13. **Recurring Task Click**
    - **Target**: Task details modal
    - **Type**: Modal overlay
    - **Content**: Task description, history, next occurrence
    - **Actions**: [Mark Complete], [Edit], [Skip Occurrence], [Delete]

### Event-Driven Navigation

14. **On Venue Booking Confirmed**
    - **No Navigation**: Remains on screen
    - **Feedback**: Success toast "Venue confirmed for Q2"
    - **Updates**: Calendar updated, status changed

15. **On Meeting Scheduled**
    - **No Navigation**: Remains on screen
    - **Feedback**: Success toast with calendar link
    - **Updates**: Calendar shows new meeting
    - **Notification**: Attendees receive calendar invites

16. **On Venue Conflict Detected**
    - **Feedback**: Warning modal "Venue already booked for this date"
    - **Options**: [Choose Different Date], [Override], [Contact Venue]
    - **No Auto-Save**: Prevents double-booking

17. **On Recurring Task Due**
    - **Notification**: Email reminder or dashboard notification
    - **Entry**: Link opens task details
    - **Action**: [Complete Task] button prominent

### Advanced Actions

18. **Timeline View** (when selected)
    - **Display**: Gantt-style timeline with events, venues, meetings
    - **Interaction**: Drag to reschedule (with validation)
    - **Click**: Opens event/booking details
    - **Zoom**: Adjust timeline scale (day/week/month)

### Error States

19. **On Venue Unavailable**
    - **Feedback**: "Venue not available for selected dates"
    - **Suggestions**: Alternative dates or venues
    - **Actions**: [View Alternatives], [Contact Venue], [Cancel]

20. **On Export Failure**
    - **Feedback**: "Unable to generate report"
    - **Actions**: [Retry], [Try Different Format], [Contact Support]

### Mobile-Specific

21. **Mobile Calendar View**
    - **Layout**: List view instead of grid
    - **Interaction**: Tap to expand date details
    - **Simplified**: Key info only

---