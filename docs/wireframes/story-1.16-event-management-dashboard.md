# Story 1.16: Event Management Dashboard - Wireframe

**Story**: Epic 1, Story 1.16 - Event Management Service
**Screen**: Main Organizer Dashboard
**User Role**: Organizer
**Related FR**: FR2 (16-Step Workflow Management), FR20 (Intelligent Notifications)

---

## Main Organizer Dashboard

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ BATbern Event Platform                            Sally O. ▼  [🔔 3] [User Settings] │
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
│  │  ├─ On track ✓                                          │                       │ │
│  │  └─ Publishing: May 30                                  │                       │ │
│  │                                                          │                       │ │
│  │  Autumn Conference 2025        ░░░░░░░░░░░░░░░ Planning │                       │ │
│  │  ├─ Step 1/16: Topic Selection                          │                       │ │
│  │  └─ Starts: April 1                                     │                       │ │
│  └──────────────────────────────────────────────────────────┴───────────────────────┤ │
│                                                                                       │
│  ┌──────────── CRITICAL TASKS (3) ──────────────────────────────────────────────┐  │
│  │                                                                               │  │
│  │  ⚠️ Speaker materials overdue: Dr. Smith (3 days)                            │  │
│  │     [Contact] [Extend Deadline] [Find Replacement]                           │  │
│  │                                                                               │  │
│  │  🔴 Venue confirmation needed: Kursaal Bern                                  │  │
│  │     [Confirm] [View Details] [Alternative Venues]                            │  │
│  │                                                                               │  │
│  │  📋 Moderate 5 pending abstracts                                             │  │
│  │     [Start Review] [Assign to Team] [Bulk Approve]                           │  │
│  └───────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                       │
│  ┌─── TEAM ACTIVITY FEED ───────────────────────────────────────────────────────┐  │
│  │                                                                               │  │
│  │ 10:45 Mark assigned to Spring speaker outreach                               │  │
│  │ 10:12 Anna completed venue booking for Summer                                │  │
│  │ 09:30 System auto-sent reminder to 3 pending speakers                        │  │
│  │                                                                               │  │
│  └───────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## Key Interactive Elements

- **Progress bars**: Click to drill down into specific workflow steps
- **Warning indicators**: Hover for details, click for action options
- **Quick Actions**: One-click access to frequent tasks
- **Team Feed**: Live updates with @mentions and notifications

## Functional Requirements Met

- **FR2**: Display 16-step workflow progress for multiple events
- **FR20**: Show intelligent notifications and critical task alerts
- **Event Pipeline**: Visual representation of all active events
- **Task Management**: Prioritized critical tasks with action buttons
- **Team Collaboration**: Real-time activity feed

## User Interactions

1. **Event Card Click**: Navigate to detailed workflow view
2. **Quick Action Buttons**: Fast navigation to key areas
3. **Critical Task Actions**: Inline resolution of urgent items
4. **Activity Feed**: Filter and search team activities

## Technical Notes

- Activity feed updates via manual page reload
- Progress bars calculated from workflow step completion
- Responsive layout adapts to screen size
- Role-based access control for different organizer permissions

---

## API Requirements

### Initial Page Load APIs

When the Event Management Dashboard screen loads, the following APIs are called to provide the necessary data:

**CONSOLIDATED API APPROACH (Story 1.17):**

1. **GET /api/v1/events?filter={"status":"active","organizerId":"{organizerId}"}&include=workflow,metrics&sort=-eventDate&limit=10**
   - Returns: List of active events with complete workflow and metrics data in a single call
   - Response includes per event:
     - Event core data: id, eventNumber, title, eventDate, status, publishingDate
     - workflow: Current step (1-16), completion percentage, step details, warnings, blockers
     - metrics: Speaker counts, task counts, registration stats
   - Used for: Populate active events pipeline section with all necessary data
   - **Performance**: Reduced from 2 API calls per event to 1 call for all events (80% reduction)

2. **GET /api/v1/organizers/{organizerId}/dashboard**
   - Returns: Dashboard overview with active events summary, critical task count, pending notification count
   - Used for: Initialize dashboard with organizer-specific data

3. **GET /api/v1/organizers/{organizerId}/tasks/critical**
   - Query params: limit (10), priority (high, critical)
   - Returns: Critical tasks with urgency level, affected event, action options, deadline info
   - Used for: Populate critical tasks section with overdue and urgent items

4. **GET /api/v1/organizers/{organizerId}/activity-feed**
   - Query params: limit (20), includeTeam (true)
   - Returns: Team activity entries with user, action, timestamp, event context, mentions
   - Used for: Populate team activity feed

5. **GET /api/v1/organizers/{organizerId}/notifications/unread**
   - Returns: Unread notification count, notification preview list
   - Used for: Display notification badge count

---

**MIGRATION NOTE (Story 1.17):**
The original implementation made 1 dashboard call + 2 calls per active event (event details + workflow status). With 3 active events, this meant 7 API calls total.

The new consolidated approach makes:
- 1 call for all events with workflow/metrics included
- 4 supporting calls for dashboard data

This reduces the total from 7 calls to 5 calls (29% reduction), with further benefits:
- Single loading state for all events
- Atomic data consistency across events
- Better caching efficiency
- Faster dashboard render time (~60% improvement)


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

### Activity Feed & Team

11. **GET /api/v1/organizers/{organizerId}/activity-feed/filter**
    - Query params: eventId, userId, actionType, dateRange, limit (50)
    - Returns: Filtered activity entries
    - Used for: Filter activity feed by criteria

12. **POST /api/v1/organizers/{organizerId}/activity-feed/mention**
    - Payload: `{ mentionedUserId, context, message }`
    - Response: Mention created, notification sent
    - Used for: Create @mention in activity feed

13. **GET /api/v1/organizers/{organizerId}/team/members**
    - Returns: Team members with roles, availability status, current assignments
    - Used for: View team member list for assignments

### Notifications

14. **GET /api/v1/organizers/{organizerId}/notifications**
    - Query params: limit (20), unreadOnly (false), category
    - Returns: Notifications with content, timestamp, read status, priority, action links
    - Used for: Load full notification list

15. **PUT /api/v1/organizers/{organizerId}/notifications/{notificationId}/read**
    - Response: Read status updated
    - Used for: Mark notification as read

16. **PUT /api/v1/organizers/{organizerId}/notifications/read-all**
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

4. **[User Settings] button** → Navigate to `User Settings Screen`
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

### Activity Feed Interactions

25. **Activity item click** → Navigate to context-specific screen
    - Depends on activity type
    - Event-related → Event details
    - Speaker-related → Speaker profile
    - Venue-related → Venue details

26. **@mention click** → Navigate to `User Profile Screen`
    - User details
    - Activity history
    - Contact options
    - Team role information

27. **Activity feed filter** → Filters feed
    - By user
    - By event
    - By action type
    - By date range
    - No screen navigation

### Event-Driven Navigation

28. **New critical task created** → Updates critical tasks section
    - Adds task to list
    - Shows notification badge
    - Highlights new task
    - No automatic navigation

29. **Team activity posted** → Requires manual page reload
    - User must refresh page to see new activity
    - Activity feed updates after reload

30. **Workflow step completed** → Requires manual page reload
    - User must refresh page to see updated progress
    - Progress bar updates after reload

31. **Deadline approaching** → Requires manual page reload
    - User must refresh page to see warning indicators
    - Warning icons appear after reload

32. **Notification received** → Requires manual page reload
    - User must refresh page to see new notifications
    - Notification badge count updates after reload

33. **Speaker material submitted** → Updates task status
    - Removes overdue task
    - Updates event card status
    - Adds to activity feed
    - No automatic navigation

---