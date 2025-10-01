# Story 1.16: Event Management Dashboard - Wireframe

**Story**: Epic 1, Story 1.16 - Event Management Service
**Screen**: Main Organizer Dashboard
**User Role**: Organizer
**Related FR**: FR2 (16-Step Workflow Management), FR20 (Intelligent Notifications)

---

## Main Organizer Dashboard

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ BATbern Event Platform                            Sally O. ▼  [🔔 3] [?] [Settings] │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─────────────────────────────────────────────────────────────────────────────┐    │
│  │ Welcome back, Sally! Here's your event command center                       │    │
│  └─────────────────────────────────────────────────────────────────────────────┘    │
│                                                                                       │
│  ┌──────────────── ACTIVE EVENTS PIPELINE ─────────────────┬──── QUICK ACTIONS ───┐ │
│  │                                                          │                       │ │
│  │  Spring Conference 2025        ███████████░░░░░ 65%     │ [+ New Event]        │ │
│  │  ├─ Step 7/16: Content Review                           │ [📊 Analytics]       │ │
│  │  ├─ 3 speakers pending ⚠️                               │ [👥 Speakers]        │ │
│  │  └─ Publishing: March 15                                │ [🏢 Partners]        │ │
│  │                                                          │ [📅 Venues]          │ │
│  │  Summer Workshop 2025          ██░░░░░░░░░░░░░ 15%     │ [📧 Newsletter]      │ │
│  │  ├─ Step 2/16: Speaker Research                         │                       │ │
│  │  ├─ On track ✓                                          │ ─────────────────     │ │
│  │  └─ Publishing: May 30                                  │                       │ │
│  │                                                          │ AI SUGGESTIONS 🤖    │ │
│  │  Autumn Conference 2025        ░░░░░░░░░░░░░░░ Planning │                       │ │
│  │  ├─ Step 1/16: Topic Selection                          │ "Contact speaker     │ │
│  │  └─ Starts: April 1                                     │  John D. - overdue   │ │
│  └──────────────────────────────────────────────────────────┤  3 days"            │ │
│                                                              │                       │ │
│  ┌──────────── CRITICAL TASKS (3) ─────────────────────────┤ "Reserve venue for   │ │
│  │                                                          │  Q3 partner meeting" │ │
│  │  ⚠️ Speaker materials overdue: Dr. Smith (3 days)       │                       │ │
│  │     [Contact] [Extend Deadline] [Find Replacement]      │ "Review 5 pending    │ │
│  │                                                          │  abstracts"          │ │
│  │  🔴 Venue confirmation needed: Kursaal Bern             │                       │ │
│  │     [Confirm] [View Details] [Alternative Venues]       └───────────────────────┤ │
│  │                                                                                  │ │
│  │  📋 Moderate 5 pending abstracts                                                │ │
│  │     [Start Review] [Assign to Team] [Bulk Approve]                             │ │
│  └──────────────────────────────────────────────────────────────────────────────┘  │ │
│                                                                                       │
│  ┌─── TEAM ACTIVITY FEED ───────────┬─── PERFORMANCE METRICS ────────────────────┐  │
│  │                                   │                                             │  │
│  │ 10:45 Mark assigned to Spring    │  Avg. Planning Time    ⬇ 45% improved      │  │
│  │       speaker outreach            │  Speaker Accept Rate   ⬆ 78% (target: 80%) │  │
│  │ 10:12 Anna completed venue        │  Content Quality       ⬆ 92% compliance    │  │
│  │       booking for Summer          │  Publishing On-Time    ✓ 100% this quarter │  │
│  │ 09:30 System auto-sent reminder   │                                             │  │
│  │       to 3 pending speakers       │  [View Detailed Analytics →]                │  │
│  └───────────────────────────────────┴─────────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## Key Interactive Elements

- **Progress bars**: Click to drill down into specific workflow steps
- **Warning indicators**: Hover for details, click for action options
- **Quick Actions**: One-click access to frequent tasks
- **AI Suggestions**: Context-aware recommendations updated in real-time
- **Team Feed**: Live updates with @mentions and notifications
- **Metrics**: Click any metric for detailed breakdown

## Functional Requirements Met

- **FR2**: Display 16-step workflow progress for multiple events
- **FR20**: Show intelligent notifications and critical task alerts
- **Event Pipeline**: Visual representation of all active events
- **Task Management**: Prioritized critical tasks with action buttons
- **Team Collaboration**: Real-time activity feed
- **Performance Tracking**: Key metrics dashboard

## User Interactions

1. **Event Card Click**: Navigate to detailed workflow view
2. **Quick Action Buttons**: Fast navigation to key areas
3. **AI Suggestion Click**: Execute suggested action
4. **Critical Task Actions**: Inline resolution of urgent items
5. **Activity Feed**: Filter and search team activities
6. **Metrics Click**: Drill down into detailed analytics

## Technical Notes

- Real-time WebSocket updates for activity feed
- Progress bars calculated from workflow step completion
- AI suggestions generated from workflow analysis
- Responsive layout adapts to screen size
- Role-based access control for different organizer permissions

---

## API Requirements

### Initial Page Load APIs

When the Event Management Dashboard screen loads, the following APIs are called to provide the necessary data:

1. **GET /api/v1/organizers/{organizerId}/dashboard**
   - Returns: Dashboard overview with active events summary, critical task count, pending notification count
   - Used for: Initialize dashboard with organizer-specific data

2. **GET /api/v1/organizers/{organizerId}/events/active**
   - Query params: status (active, planning), includeMetrics (true)
   - Returns: List of active events with workflow progress, current step, completion percentage, warnings, publishing dates
   - Used for: Populate active events pipeline section

3. **GET /api/v1/events/{eventId}/workflow/status**
   - Returns: Current workflow step (1-16), completion percentage, step details, warnings, blockers
   - Used for: Display progress bars and step information for each event

4. **GET /api/v1/organizers/{organizerId}/tasks/critical**
   - Query params: limit (10), priority (high, critical)
   - Returns: Critical tasks with urgency level, affected event, action options, deadline info
   - Used for: Populate critical tasks section with overdue and urgent items

5. **GET /api/v1/organizers/{organizerId}/activity-feed**
   - Query params: limit (20), includeTeam (true)
   - Returns: Team activity entries with user, action, timestamp, event context, mentions
   - Used for: Populate team activity feed

6. **GET /api/v1/organizers/{organizerId}/metrics/dashboard**
   - Query params: period (current-quarter)
   - Returns: Performance metrics (avg planning time, speaker accept rate, content quality, on-time publishing), trends
   - Used for: Display performance metrics section

7. **GET /api/v1/organizers/{organizerId}/ai/suggestions**
   - Query params: limit (3), context (dashboard)
   - Returns: AI-generated action suggestions with priority, reasoning, estimated effort, success probability
   - Used for: Populate AI suggestions panel

8. **GET /api/v1/organizers/{organizerId}/notifications/unread**
   - Returns: Unread notification count, notification preview list
   - Used for: Display notification badge count

9. **WebSocket /ws/organizers/{organizerId}/dashboard**
   - Real-time updates: Team activity feed, task status changes, metric updates, new notifications
   - Used for: Live updates for activity feed and task changes

---

## Action APIs

### Event Management

1. **POST /api/v1/events**
   - Payload: `{ title, eventDate, venueId, eventType, description }`
   - Response: Event ID, initial workflow step, creation confirmation
   - Used for: Create new event from [+ New Event] button

2. **GET /api/v1/events/{eventId}/details**
   - Returns: Full event details, workflow status, team assignments, timeline
   - Used for: Navigate to event detail view when clicking event card

3. **PUT /api/v1/events/{eventId}/workflow/step**
   - Payload: `{ currentStep, action: "advance|rollback|skip", notes }`
   - Response: Updated workflow step, new completion percentage, next actions
   - Used for: Advance workflow to next step

### Critical Task Actions

4. **POST /api/v1/organizers/{organizerId}/messages/send**
   - Payload: `{ recipientId, recipientType: "speaker|partner|venue", subject, message, urgency: "normal|high", templateId (optional) }`
   - Response: Message ID, sent timestamp, delivery status
   - Used for: Send message to speaker/partner from [Contact] action

5. **PUT /api/v1/sessions/{sessionId}/deadline/extend**
   - Payload: `{ newDeadline, extensionReason, notifySpeaker: boolean }`
   - Response: Updated deadline, confirmation, notification sent status
   - Used for: Extend speaker submission deadline from [Extend Deadline] action

6. **PUT /api/v1/bookings/{bookingId}/confirm**
   - Payload: `{ confirmationDate, specialRequirements, contactPerson }`
   - Response: Confirmed booking details, confirmation number, next steps
   - Used for: Confirm venue booking from [Confirm] action

7. **POST /api/v1/reviews/assign**
   - Payload: `{ abstractIds: [], assigneeIds: [], dueDate }`
   - Response: Assignment confirmation, assigned count, notifications sent
   - Used for: Assign abstracts to team members from [Assign to Team] action

8. **POST /api/v1/reviews/bulk-action**
   - Payload: `{ abstractIds: [], action: "approve|reject|request-revision", notes }`
   - Response: Processed count, results per abstract, notification status
   - Used for: Bulk approve/reject abstracts from [Bulk Approve] action

9. **PUT /api/v1/organizers/{organizerId}/tasks/{taskId}/complete**
   - Payload: `{ resolution, notes }`
   - Response: Task completion confirmation, updated critical task count
   - Used for: Mark critical task as complete

10. **PUT /api/v1/organizers/{organizerId}/tasks/{taskId}/snooze**
    - Payload: `{ snoozeUntil, reason }`
    - Response: Snooze confirmation, new reminder time
    - Used for: Snooze/postpone critical task

### AI Suggestions

11. **POST /api/v1/organizers/{organizerId}/ai/suggestions/{suggestionId}/execute**
    - Response: Execution result, actions taken, updated state
    - Used for: Execute AI suggestion action

12. **DELETE /api/v1/organizers/{organizerId}/ai/suggestions/{suggestionId}**
    - Payload: `{ dismissalReason (optional) }`
    - Response: Dismissal confirmation, feedback recorded
    - Used for: Dismiss AI suggestion

13. **POST /api/v1/organizers/{organizerId}/ai/suggestions/{suggestionId}/feedback**
    - Payload: `{ helpful: boolean, executed: boolean, comments }`
    - Response: Feedback recorded confirmation
    - Used for: Provide feedback on AI suggestion quality

### Activity Feed & Team

14. **GET /api/v1/organizers/{organizerId}/activity-feed/filter**
    - Query params: eventId, userId, actionType, dateRange, limit (50)
    - Returns: Filtered activity entries
    - Used for: Filter activity feed by criteria

15. **POST /api/v1/organizers/{organizerId}/activity-feed/mention**
    - Payload: `{ mentionedUserId, context, message }`
    - Response: Mention created, notification sent
    - Used for: Create @mention in activity feed

16. **GET /api/v1/organizers/{organizerId}/team/members**
    - Returns: Team members with roles, availability status, current assignments
    - Used for: View team member list for assignments

### Metrics & Analytics

17. **GET /api/v1/organizers/{organizerId}/metrics/detailed**
    - Query params: metric (planning-time|speaker-rate|content-quality|publishing), period, breakdown (by-event|by-quarter)
    - Returns: Detailed metric data, historical trends, comparisons, insights
    - Used for: View detailed analytics breakdown from [View Detailed Analytics]

18. **GET /api/v1/organizers/{organizerId}/metrics/export**
    - Query params: format (csv|pdf|xlsx), metrics: [], period
    - Returns: Download URL, expiration timestamp
    - Used for: Export metrics data

### Notifications

19. **GET /api/v1/organizers/{organizerId}/notifications**
    - Query params: limit (20), unreadOnly (false), category
    - Returns: Notifications with content, timestamp, read status, priority, action links
    - Used for: Load full notification list

20. **PUT /api/v1/organizers/{organizerId}/notifications/{notificationId}/read**
    - Response: Read status updated
    - Used for: Mark notification as read

21. **PUT /api/v1/organizers/{organizerId}/notifications/read-all**
    - Response: All notifications marked read, updated count
    - Used for: Mark all notifications as read

---

## Navigation Map

### Primary Navigation Actions

1. **BATbern Event Platform logo** → Navigate to `Event Management Dashboard`
   - Refreshes dashboard
   - Returns to home view

2. **User dropdown (Sally O. ▼)** → Opens user menu
   - Profile settings option
   - Account preferences
   - Sign out option
   - No screen navigation until menu item selected

3. **[🔔 3] notification icon** → Navigate to `Notification Center` (story-1.20-notification-center.md)
   - Shows full notification list
   - Unread notifications highlighted
   - Action items available

4. **[?] help icon** → Opens help documentation
   - Context-sensitive help
   - User guide access
   - Support contact
   - Opens in modal or sidebar

5. **[Settings] button** → Navigate to `System Settings Screen`
   - Organization settings
   - User preferences
   - Team management
   - Integration configuration

6. **Event card click (Spring Conference 2025)** → Navigate to `Event Detail/Edit Screen`
   - Full event details
   - Complete workflow view
   - Team assignments
   - Timeline and milestones

7. **Event progress bar click** → Navigate to `Workflow Visualization` (story-1.16-workflow-visualization.md)
   - Visual workflow representation
   - Step-by-step progress
   - Bottleneck identification
   - Step details and actions

8. **Event step text (Step 7/16)** → Navigate to `Workflow Visualization` (story-1.16-workflow-visualization.md)
   - Same as progress bar click
   - Focuses on current step

9. **Warning indicator (⚠️)** → Shows warning details tooltip
   - Hover for details
   - Click for action options
   - Quick resolution actions
   - No screen navigation unless action selected

### Quick Actions Navigation

10. **[+ New Event] button** → Navigate to `New Event Creation Wizard`
    - Step-by-step event creation
    - Template selection
    - Initial configuration
    - Workflow initialization

11. **[📊 Analytics] button** → Navigate to `Analytics Dashboard`
    - Comprehensive analytics view
    - Multiple metric categories
    - Custom report builder
    - Data visualization

12. **[👥 Speakers] button** → Navigate to `Speaker Directory`
    - Complete speaker database
    - Filtering and search
    - Speaker profiles
    - Invitation management

13. **[🏢 Partners] button** → Navigate to `Partner Directory`
    - Partner organization list
    - Sponsorship levels
    - Contact management
    - Relationship tracking

14. **[📅 Venues] button** → Navigate to `Venue Management` (story-4.4-logistics-coordination.md)
    - Venue catalog
    - Booking management
    - Availability calendar
    - Logistics coordination

15. **[📧 Newsletter] button** → Navigate to `Newsletter Management`
    - Newsletter composition
    - Distribution lists
    - Template management
    - Send history

### Critical Task Actions

16. **[Contact] button (speaker overdue)** → Opens message composer
    - Pre-filled recipient (Dr. Smith)
    - Template suggestions
    - Sends message via API
    - Updates task status
    - No screen navigation

17. **[Extend Deadline] button** → Opens deadline extension modal
    - Date picker for new deadline
    - Reason input field
    - Notification toggle
    - Submits extension
    - No screen navigation

18. **[Find Replacement] button** → Navigate to `Speaker Matching Interface` (story-3.1-speaker-matching-interface.md)
    - Speaker search with criteria
    - Topic matching
    - Availability filtering
    - Invitation workflow

19. **[Confirm] button (venue)** → Opens confirmation dialog
    - Confirmation details form
    - Special requirements input
    - Contact person selection
    - Submits confirmation
    - Updates task status
    - No screen navigation

20. **[View Details] button (venue)** → Navigate to `Venue Details Screen`
    - Venue information
    - Booking details
    - Contract documents
    - Contact information

21. **[Alternative Venues] button** → Navigate to `Venue Search Screen` (story-4.4-logistics-coordination.md)
    - Venue search interface
    - Availability calendar
    - Comparison tools
    - Booking workflow

22. **[Start Review] button (abstracts)** → Navigate to `Abstract Review Queue`
    - Moderation interface
    - Review tools
    - Bulk actions
    - Quality scoring

23. **[Assign to Team] button** → Opens assignment modal
    - Team member selector
    - Due date picker
    - Load balancing suggestions
    - Submits assignments
    - No screen navigation

24. **[Bulk Approve] button** → Opens confirmation dialog
    - Lists selected abstracts
    - Confirmation prompt
    - Bulk action execution
    - Updates task count
    - No screen navigation

### AI Suggestions Interactions

25. **AI suggestion click** → Executes suggested action
    - Shows confirmation dialog
    - Executes action via API
    - Shows result notification
    - Updates dashboard state
    - No automatic navigation

26. **AI suggestion dismiss (X)** → Removes suggestion
    - Optional dismissal reason
    - Removes from panel
    - Updates AI learning
    - No screen navigation

### Activity Feed Interactions

27. **Activity item click** → Navigate to context-specific screen
    - Depends on activity type
    - Event-related → Event details
    - Speaker-related → Speaker profile
    - Venue-related → Venue details

28. **@mention click** → Navigate to `User Profile Screen`
    - User details
    - Activity history
    - Contact options
    - Team role information

29. **Activity feed filter** → Filters feed
    - By user
    - By event
    - By action type
    - By date range
    - No screen navigation

### Metrics Interactions

30. **Metric click (Avg. Planning Time)** → Navigate to `Metric Drill-down Screen`
    - Detailed metric breakdown
    - Historical trends
    - Event comparisons
    - Improvement suggestions

31. **[View Detailed Analytics →] button** → Navigate to `Full Analytics Dashboard`
    - Comprehensive analytics
    - Multiple metrics
    - Custom date ranges
    - Export options

32. **Trend indicator (⬇ 45%)** → Shows trend tooltip
    - Hover for trend details
    - Historical comparison
    - Contributing factors
    - No screen navigation

### Event-Driven Navigation

33. **New critical task created** → Updates critical tasks section
    - Adds task to list
    - Shows notification badge
    - Highlights new task
    - No automatic navigation

34. **Team activity posted (WebSocket)** → Updates activity feed
    - Adds activity to feed
    - Shows real-time indicator
    - May show notification for @mentions
    - No automatic navigation

35. **Workflow step completed** → Updates event progress
    - Updates progress bar
    - Changes step indicator
    - May remove from critical tasks
    - No automatic navigation

36. **Deadline approaching** → Shows warning indicator
    - Adds ⚠️ to event card
    - May create critical task
    - Shows notification
    - No automatic navigation

37. **Metric threshold crossed** → Updates metrics display
    - Highlights changed metric
    - Shows trend indicator
    - May trigger AI suggestion
    - No automatic navigation

38. **New AI suggestion generated** → Adds to suggestions panel
    - Appears in AI Suggestions section
    - Shows priority indicator
    - May show notification
    - No automatic navigation

39. **Notification received (WebSocket)** → Updates notification badge
    - Increments badge count
    - May show toast notification
    - Highlights notification icon
    - No automatic navigation

40. **Speaker material submitted** → Updates task status
    - Removes overdue task
    - Updates event card status
    - Adds to activity feed
    - No automatic navigation

---