# Story 1.20: Automated Notification Center - Wireframe

**Story**: Epic 1, Story 1.20 - Event Management Service
**Screen**: Automated Notification Center
**User Role**: Organizer
**Related FR**: FR20 (Intelligent Notifications)

---

## Automated Notification Center

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back                     Notification Center                         [Settings]    │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  Unread: 3     Total: 47                                                             │
│                                                                                       │
│  ┌─── INBOX ────────────────────────────────────────────────────────────────────────┐ │
│  │                                                                                   │ │
│  │ [All] [Unread] [Critical] [Team]                                                 │ │
│  │                                                                                   │ │
│  │ ┌────────────────────────────┐                                                   │ │
│  │ │ 🔴 CRITICAL - 14:45        │                                                   │ │
│  │ │ Speaker dropout detected   │                                                   │ │
│  │ │ Dr. Weber cancelled talk   │                                                   │ │
│  │ │ [Find Replacement]         │                                                   │ │
│  │ │ [Contact Waitlist]         │                                                   │ │
│  │ └────────────────────────────┘                                                   │ │
│  │                                                                                   │ │
│  │ ┌────────────────────────────┐                                                   │ │
│  │ │ ⚠️ WARNING - 14:30         │                                                   │ │
│  │ │ Abstract needs revision    │                                                   │ │
│  │ │ Marc B. - Too long (1250)  │                                                   │ │
│  │ │ [Review] [Contact Speaker] │                                                   │ │
│  │ └────────────────────────────┘                                                   │ │
│  │                                                                                   │ │
│  │ ┌────────────────────────────┐                                                   │ │
│  │ │ ℹ️ INFO - 13:15            │                                                   │ │
│  │ │ Publishing milestone hit   │                                                   │ │
│  │ │ 5 speakers now confirmed   │                                                   │ │
│  │ │ Ready for partial publish  │                                                   │ │
│  │ │ [Publish Now] [Review]     │                                                   │ │
│  │ └────────────────────────────┘                                                   │ │
│  │                                                                                   │ │
│  │ View older notifications...                                                       │ │
│  └───────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── ESCALATION WORKFLOWS ─────────────────────────────────────────────────────┐   │
│  │                                                                                │   │
│  │  Active Escalations:                                                          │   │
│  │                                                                                │   │
│  │  Speaker Non-Response → Day 1: Email → Day 3: Call → Day 5: Replace          │   │
│  │  ├─ Peter M: Day 3 (calling today)                                           │   │
│  │  └─ Lisa C: Day 1 (email sent)                                               │   │
│  │                                                                                │   │
│  │  Venue Issues → Hour 1: Notify team → Hour 4: Call venue → Day 1: Escalate   │   │
│  │  └─ All clear ✓                                                               │   │
│  │                                                                                │   │
│  │  Quality Problems → Immediate: Speaker → Day 1: Moderator → Day 2: Lead      │   │
│  │  └─ Marc B. abstract: Moderator notified                                     │   │
│  │                                                                                │   │
│  │  [Configure Escalations] [View History] [Pause All]                          │   │
│  └────────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## Key Interactive Elements

- **Notification Inbox**: Prioritized notifications with severity levels
- **Escalation Workflows**: Multi-step escalation paths with time-based progression

## Functional Requirements Met

- **FR20**: Intelligent automated notifications across workflow
- **Multi-Channel**: Platform, email notifications
- **Priority Levels**: Critical, warning, info classifications
- **Escalation Paths**: Automated escalation for unresolved issues

## User Interactions

1. **View Notifications**: Filter by type, priority, and status
2. **Take Action**: Inline buttons for common responses
3. **Configure Escalations**: Set up multi-step escalation workflows

## Technical Notes

- Event-driven architecture with notification service
- Multi-channel delivery system (email, platform notifications)
- Escalation scheduler with time-based progression
- Notification history and audit trail
- Integration with workflow steps for automatic triggering

---

## API Requirements

### Initial Page Load APIs

When the Notification Center screen loads, the following APIs are called to provide the necessary data:

1. **GET /api/v1/organizers/{organizerId}/notifications**
   - Query params: filter (all|unread|critical|team), limit (20), offset
   - Returns: Notifications with severity, timestamp, title, description, action links, read status, related entity
   - Used for: Populate notification inbox

2. **GET /api/v1/organizers/{organizerId}/notifications/summary**
   - Returns: Notification counts (unread, total, critical)
   - Used for: Display summary statistics at top of page

3. **GET /api/v1/organizers/{organizerId}/notifications/escalations**
   - Query params: status (active|completed|all)
   - Returns: Active escalation workflows with current step, affected entities, timeline, next action
   - Used for: Display escalation workflows section

---

## Action APIs

### Notification Management

1. **PUT /api/v1/organizers/{organizerId}/notifications/{notificationId}/read**
   - Response: Read status updated, unread count decremented
   - Used for: Mark notification as read

2. **PUT /api/v1/organizers/{organizerId}/notifications/read-all**
   - Payload: `{ filter: "all|critical|team" (optional) }`
   - Response: Bulk read confirmation, new unread count
   - Used for: Mark multiple notifications as read

3. **DELETE /api/v1/organizers/{organizerId}/notifications/{notificationId}**
   - Response: Notification deleted, updated counts
   - Used for: Dismiss/delete notification

4. **POST /api/v1/organizers/{organizerId}/notifications/{notificationId}/snooze**
   - Payload: `{ snoozeUntil: timestamp, reason }`
   - Response: Snooze confirmation, reminder scheduled
   - Used for: Snooze notification for later

5. **POST /api/v1/organizers/{organizerId}/notifications/{notificationId}/action**
   - Payload: `{ actionType, actionData }`
   - Response: Action executed, notification updated
   - Used for: Execute inline action from notification (e.g., Find Replacement, Review)

### Escalation Workflows

6. **POST /api/v1/organizers/{organizerId}/notifications/escalations**
    - Payload: `{ name, steps: [{ delay, action, recipients: [] }], triggerType, entityType }`
    - Response: Escalation workflow created, workflow ID
    - Used for: Create new escalation workflow

7. **PUT /api/v1/organizers/{organizerId}/notifications/escalations/{escalationId}**
    - Payload: `{ steps, enabled: boolean }`
    - Response: Escalation updated, affected instances
    - Used for: Update escalation workflow configuration

8. **PUT /api/v1/organizers/{organizerId}/notifications/escalations/{escalationId}/pause**
    - Payload: `{ pauseDuration: "1-hour|1-day|indefinite" }`
    - Response: Escalation paused, resume time
    - Used for: Pause escalation workflow

9. **POST /api/v1/organizers/{organizerId}/notifications/escalations/{instanceId}/resolve**
    - Payload: `{ resolution, notes }`
    - Response: Escalation instance resolved, removed from active
    - Used for: Manually resolve escalation

10. **GET /api/v1/organizers/{organizerId}/notifications/escalations/history**
    - Query params: limit (50), entityType, status, dateRange
    - Returns: Historical escalations with resolution details, duration, outcome
    - Used for: View escalation history from [View History]

### Notification History & Analytics

11. **GET /api/v1/organizers/{organizerId}/notifications/history**
    - Query params: startDate, endDate, severity, readStatus, limit (100)
    - Returns: Historical notifications with metadata, actions taken, resolution time
    - Used for: View notification history

12. **GET /api/v1/organizers/{organizerId}/notifications/analytics**
    - Query params: period (week|month|quarter)
    - Returns: Notification statistics (count by type, average resolution time, escalation rates)
    - Used for: View notification analytics

13. **GET /api/v1/organizers/{organizerId}/notifications/export**
    - Query params: format (csv|pdf|json), dateRange, filter
    - Returns: Download URL for notification history export
    - Used for: Export notification data

---

## Navigation Map

### Primary Navigation Actions

1. **← Back button** → Navigate to `Event Management Dashboard` (story-1.16-event-management-dashboard.md)
   - Returns to dashboard
   - Preserves notification state

2. **[Settings] button** → Scrolls to preferences section
   - Shows notification preferences
   - In-page navigation
   - No screen change

3. **Notification inbox filter tabs** → Filters notification list
   - [All], [Unread], [Critical], [Team]
   - Updates displayed notifications
   - No screen navigation

### Notification Inbox Actions

4. **Notification card click** → Expands notification details
   - Shows full message
   - Reveals all action buttons
   - Marks as read
   - No screen navigation

5. **[Find Replacement] button (speaker dropout)** → Navigate to `Speaker Matching Interface` (story-3.1-speaker-matching-interface.md)
   - Pre-filled search criteria
   - Speaker selection
   - Invitation workflow

6. **[Contact Waitlist] button** → Navigate to `Waitlist Management Screen`
   - Waitlist viewer
   - Bulk invite options
   - Priority ranking

7. **[Review] button (abstract)** → Navigate to `Abstract Review Screen`
   - Review interface
   - Quality scoring
   - Approval workflow

8. **[Contact Speaker] button** → Opens message composer
   - Pre-filled recipient
   - Template suggestions
   - Sends message
   - No screen navigation

9. **[Publish Now] button** → Navigate to `Publishing Control Center` (story-2.3-basic-publishing-engine.md)
   - Publishing interface
   - Content review
   - Publish confirmation

10. **View older notifications...** → Loads more notifications
    - Pagination
    - Infinite scroll
    - No screen navigation

### Escalation Workflow Actions

11. **Escalation workflow item click** → Shows escalation details
    - Full timeline
    - Current step highlighted
    - Next actions
    - No screen navigation

12. **[Configure Escalations] button** → Opens escalation editor
    - Workflow designer
    - Step configuration
    - Timing settings
    - No screen navigation

13. **[View History] button** → Opens escalation history modal
    - Historical escalations
    - Resolution details
    - Statistics
    - No screen navigation

14. **[Pause All] button** → Pauses all escalations
    - Confirmation dialog
    - Duration selector
    - Pauses workflows
    - No screen navigation

### Event-Driven Navigation

15. **New notification received** → Adds to inbox
    - Appears at top of list
    - Increments unread count
    - Shows toast notification
    - May play sound
    - No automatic navigation

16. **Escalation advanced** → Updates escalation display
    - Moves to next step
    - Updates timeline
    - Shows notification
    - No automatic navigation

17. **Notification read** → Updates UI
    - Decrements unread count
    - Changes visual state
    - May hide from Unread filter
    - No automatic navigation

---