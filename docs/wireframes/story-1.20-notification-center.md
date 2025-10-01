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
│  Unread: 3     Total: 47     Active Rules: 12     Next Scheduled: 15:30            │
│                                                                                       │
│  ┌─── INBOX ────────────────────────┬─── AUTOMATION RULES ───────────────────────┐  │
│  │                                  │                                             │  │
│  │ [All] [Unread] [Critical] [Team] │  Active Rules (12)                         │  │
│  │                                  │                                             │  │
│  │ ┌────────────────────────────┐   │  ┌─────────────────────────────────────┐   │  │
│  │ │ 🔴 CRITICAL - 14:45        │   │  │ Speaker Deadline Reminder          │   │  │
│  │ │ Speaker dropout detected   │   │  │ Trigger: 72h before deadline       │   │  │
│  │ │ Dr. Weber cancelled talk   │   │  │ Action: Email + Platform notify    │   │  │
│  │ │ [Find Replacement]         │   │  │ Status: ✓ Active                   │   │  │
│  │ │ [Contact Waitlist]         │   │  │ Last run: Today 09:00              │   │  │
│  │ └────────────────────────────┘   │  │ [Edit] [Disable] [Test]            │   │  │
│  │                                  │  ├─────────────────────────────────────┤   │  │
│  │ ┌────────────────────────────┐   │  │ Abstract Quality Alert             │   │  │
│  │ │ ⚠️ WARNING - 14:30         │   │  │ Trigger: Failed validation         │   │  │
│  │ │ Abstract needs revision    │   │  │ Action: Notify moderator + speaker │   │  │
│  │ │ Marc B. - Too long (1250)  │   │  │ Status: ✓ Active                   │   │  │
│  │ │ [Review] [Contact Speaker] │   │  │ Fired: 23 times this month         │   │  │
│  │ └────────────────────────────┘   │  ├─────────────────────────────────────┤   │  │
│  │                                  │  │ Venue Confirmation Check           │   │  │
│  │ ┌────────────────────────────┐   │  │ Trigger: 30 days before event      │   │  │
│  │ │ ℹ️ INFO - 13:15            │   │  │ Action: Check status, escalate     │   │  │
│  │ │ Publishing milestone hit   │   │  │ Status: ✓ Active                   │   │  │
│  │ │ 5 speakers now confirmed   │   │  │ Next: March 15, 09:00              │   │  │
│  │ │ Ready for partial publish  │   │  ├─────────────────────────────────────┤   │  │
│  │ │ [Publish Now] [Review]     │   │  │ [+ Create New Rule]                │   │  │
│  │ └────────────────────────────┘   │  └─────────────────────────────────────┘   │  │
│  │                                  │                                             │  │
│  │ View older notifications...      │                                             │  │
│  └──────────────────────────────────┴─────────────────────────────────────────────┘ │
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
│  ┌─── NOTIFICATION PREFERENCES ──────────────────────────────────────────────────┐  │
│  │                                                                                │   │
│  │  My Preferences:              Team Preferences:          System Defaults:     │   │
│  │  ☑ Platform notifications     ☑ Daily digest 09:00      ☑ Critical: Always   │   │
│  │  ☑ Email (important only)    ☑ Weekly report Mon        ☑ Warnings: Batched  │   │
│  │  ☐ SMS (critical only)       ☐ Slack integration        ☑ Info: Platform     │   │
│  │  ☑ Browser push              ☑ @mentions immediate      ☐ Marketing: Never   │   │
│  │                                                                                │   │
│  │  Quiet Hours: 20:00 - 08:00  [Edit Preferences]                              │   │
│  └────────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## Key Interactive Elements

- **Notification Inbox**: Prioritized notifications with severity levels
- **Automation Rules**: Configure triggers and actions for automatic notifications
- **Escalation Workflows**: Multi-step escalation paths with time-based progression
- **Preference Controls**: Granular control over notification channels and timing
- **Rule Management**: Create, edit, test, and disable notification rules
- **Team Settings**: Configure team-wide notification preferences

## Functional Requirements Met

- **FR20**: Intelligent automated notifications across workflow
- **Multi-Channel**: Platform, email, SMS, browser push, Slack
- **Priority Levels**: Critical, warning, info classifications
- **Escalation Paths**: Automated escalation for unresolved issues
- **Rule Engine**: Flexible trigger-action automation system
- **Team Collaboration**: Team-wide notification settings

## User Interactions

1. **View Notifications**: Filter by type, priority, and status
2. **Take Action**: Inline buttons for common responses
3. **Create Rules**: Define triggers and automated actions
4. **Configure Escalations**: Set up multi-step escalation workflows
5. **Manage Preferences**: Control when, how, and what notifications received
6. **Test Rules**: Verify notification rules work correctly

## Technical Notes

- Event-driven architecture with notification service
- Multi-channel delivery system (email, SMS, push, Slack)
- Rule engine with flexible trigger conditions
- Escalation scheduler with time-based progression
- User preference management with quiet hours
- Notification history and audit trail
- Integration with workflow steps for automatic triggering
- Rate limiting to prevent notification spam

---

## API Requirements

### Initial Page Load APIs

When the Notification Center screen loads, the following APIs are called to provide the necessary data:

1. **GET /api/v1/organizers/{organizerId}/notifications**
   - Query params: filter (all|unread|critical|team), limit (20), offset
   - Returns: Notifications with severity, timestamp, title, description, action links, read status, related entity
   - Used for: Populate notification inbox

2. **GET /api/v1/organizers/{organizerId}/notifications/summary**
   - Returns: Notification counts (unread, total, critical), active rules count, next scheduled notification time
   - Used for: Display summary statistics at top of page

3. **GET /api/v1/organizers/{organizerId}/notifications/rules**
   - Query params: status (active|disabled|all)
   - Returns: Automation rules with trigger conditions, actions, status, last run time, fire count
   - Used for: Populate automation rules section

4. **GET /api/v1/organizers/{organizerId}/notifications/escalations**
   - Query params: status (active|completed|all)
   - Returns: Active escalation workflows with current step, affected entities, timeline, next action
   - Used for: Display escalation workflows section

5. **GET /api/v1/organizers/{organizerId}/notifications/preferences**
   - Returns: User preferences, team preferences, system defaults for notification channels, quiet hours, frequency
   - Used for: Populate notification preferences section

6. **GET /api/v1/organizers/{organizerId}/notifications/schedule**
   - Query params: upcoming (true), limit (5)
   - Returns: Upcoming scheduled notifications with timing, recipients, rules
   - Used for: Display next scheduled notification information

7. **WebSocket /ws/organizers/{organizerId}/notifications**
   - Real-time updates: New notifications, rule triggers, escalation progress, status changes
   - Used for: Live notification delivery and updates

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

### Automation Rules

6. **POST /api/v1/organizers/{organizerId}/notifications/rules**
   - Payload: `{ name, triggerType, triggerConditions: {}, actions: [], schedule, priority: "critical|warning|info" }`
   - Response: Rule created, rule ID, activation confirmation
   - Used for: Create new automation rule from [+ Create New Rule]

7. **PUT /api/v1/organizers/{organizerId}/notifications/rules/{ruleId}**
   - Payload: `{ name, triggerType, triggerConditions, actions, schedule, priority }`
   - Response: Rule updated, revalidated, next run time
   - Used for: Update automation rule from [Edit] button

8. **PUT /api/v1/organizers/{organizerId}/notifications/rules/{ruleId}/toggle**
   - Payload: `{ enabled: boolean }`
   - Response: Rule status updated, active rules count
   - Used for: Enable/disable rule from [Disable] button

9. **DELETE /api/v1/organizers/{organizerId}/notifications/rules/{ruleId}**
   - Payload: `{ deletionReason (optional) }`
   - Response: Rule deleted, confirmation
   - Used for: Delete automation rule

10. **POST /api/v1/organizers/{organizerId}/notifications/rules/{ruleId}/test**
    - Payload: `{ testContext: {} }`
    - Response: Test execution results, simulated notifications, validation errors
    - Used for: Test rule from [Test] button

11. **GET /api/v1/organizers/{organizerId}/notifications/rules/{ruleId}/history**
    - Query params: limit (20), startDate, endDate
    - Returns: Rule execution history with trigger times, actions taken, results
    - Used for: View rule execution history

### Escalation Workflows

12. **POST /api/v1/organizers/{organizerId}/notifications/escalations**
    - Payload: `{ name, steps: [{ delay, action, recipients: [] }], triggerType, entityType }`
    - Response: Escalation workflow created, workflow ID
    - Used for: Create new escalation workflow

13. **PUT /api/v1/organizers/{organizerId}/notifications/escalations/{escalationId}**
    - Payload: `{ steps, enabled: boolean }`
    - Response: Escalation updated, affected instances
    - Used for: Update escalation workflow configuration

14. **PUT /api/v1/organizers/{organizerId}/notifications/escalations/{escalationId}/pause**
    - Payload: `{ pauseDuration: "1-hour|1-day|indefinite" }`
    - Response: Escalation paused, resume time
    - Used for: Pause escalation workflow

15. **POST /api/v1/organizers/{organizerId}/notifications/escalations/{instanceId}/resolve**
    - Payload: `{ resolution, notes }`
    - Response: Escalation instance resolved, removed from active
    - Used for: Manually resolve escalation

16. **GET /api/v1/organizers/{organizerId}/notifications/escalations/history**
    - Query params: limit (50), entityType, status, dateRange
    - Returns: Historical escalations with resolution details, duration, outcome
    - Used for: View escalation history from [View History]

### Preferences Management

17. **PUT /api/v1/organizers/{organizerId}/notifications/preferences**
    - Payload: `{ channels: { platform: boolean, email: "all|important|critical", sms: "critical-only|off", push: boolean }, quietHours: { start, end }, frequency: { digest: boolean, digestTime } }`
    - Response: Preferences updated, confirmation
    - Used for: Update notification preferences from [Edit Preferences]

18. **PUT /api/v1/organizers/{organizerId}/notifications/preferences/team**
    - Payload: `{ teamSettings: { dailyDigest, weeklyReport, slackIntegration, mentionPolicy } }`
    - Response: Team preferences updated, affected members count
    - Used for: Update team-wide notification settings

19. **PUT /api/v1/organizers/{organizerId}/notifications/preferences/quiet-hours**
    - Payload: `{ start: "20:00", end: "08:00", timezone, exceptions: ["critical"] }`
    - Response: Quiet hours updated
    - Used for: Configure quiet hours

20. **PUT /api/v1/organizers/{organizerId}/notifications/pause-all**
    - Payload: `{ pauseDuration: "1-hour|6-hours|24-hours", reason }`
    - Response: All notifications paused, resume time
    - Used for: Pause all notifications from [Pause All]

### Notification History & Analytics

21. **GET /api/v1/organizers/{organizerId}/notifications/history**
    - Query params: startDate, endDate, severity, readStatus, limit (100)
    - Returns: Historical notifications with metadata, actions taken, resolution time
    - Used for: View notification history

22. **GET /api/v1/organizers/{organizerId}/notifications/analytics**
    - Query params: period (week|month|quarter)
    - Returns: Notification statistics (count by type, average resolution time, most fired rules, escalation rates)
    - Used for: View notification analytics

23. **GET /api/v1/organizers/{organizerId}/notifications/export**
    - Query params: format (csv|pdf|json), dateRange, filter
    - Returns: Download URL for notification history export
    - Used for: Export notification data

### Integration & Channels

24. **POST /api/v1/organizers/{organizerId}/notifications/integrations/slack**
    - Payload: `{ workspaceUrl, channelId, webhookUrl, eventTypes: [] }`
    - Response: Integration created, test notification sent
    - Used for: Configure Slack integration

25. **POST /api/v1/organizers/{organizerId}/notifications/test**
    - Payload: `{ channel: "email|sms|push|slack", message }`
    - Response: Test notification sent, delivery status
    - Used for: Test notification channel

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

### Automation Rules Actions

11. **Rule card click** → Expands rule details
    - Shows full configuration
    - Displays execution history
    - Reveals management buttons
    - No screen navigation

12. **[Edit] button (rule)** → Opens rule editor modal
    - Rule configuration form
    - Trigger condition builder
    - Action selector
    - Saves via API
    - No screen navigation

13. **[Disable] button (rule)** → Toggles rule status
    - Confirms disabling
    - Updates rule status
    - Shows confirmation
    - No screen navigation

14. **[Test] button (rule)** → Executes test run
    - Simulates trigger
    - Shows test results
    - Validates configuration
    - No screen navigation

15. **[+ Create New Rule] button** → Opens rule creation modal
    - Rule setup wizard
    - Template selection
    - Configuration form
    - Creates rule
    - No screen navigation

### Escalation Workflow Actions

16. **Escalation workflow item click** → Shows escalation details
    - Full timeline
    - Current step highlighted
    - Next actions
    - No screen navigation

17. **[Configure Escalations] button** → Opens escalation editor
    - Workflow designer
    - Step configuration
    - Timing settings
    - No screen navigation

18. **[View History] button** → Opens escalation history modal
    - Historical escalations
    - Resolution details
    - Statistics
    - No screen navigation

19. **[Pause All] button** → Pauses all escalations
    - Confirmation dialog
    - Duration selector
    - Pauses workflows
    - No screen navigation

### Notification Preferences Actions

20. **Preference checkbox** → Toggles preference
    - Updates immediately
    - Saves via API
    - Shows confirmation
    - No screen navigation

21. **[Edit Preferences] button** → Opens preferences editor
    - Detailed settings
    - Channel configuration
    - Schedule settings
    - No screen navigation

22. **Quiet hours time selector** → Opens time picker
    - Set start/end times
    - Timezone selection
    - Saves settings
    - No screen navigation

### Secondary Navigation (Rule Components)

23. **Rule trigger condition** → Shows condition details tooltip
    - Condition explanation
    - Example values
    - Documentation link
    - No navigation

24. **Rule action** → Shows action details tooltip
    - Action type description
    - Recipients
    - Template preview
    - No navigation

25. **Last run time** → Shows execution details
    - Execution log
    - Success/failure status
    - Notifications sent
    - No navigation

26. **Fired count** → Shows firing history
    - Timeline of executions
    - Frequency analysis
    - Recent triggers
    - No navigation

### Event-Driven Navigation

27. **New notification received (WebSocket)** → Adds to inbox
    - Appears at top of list
    - Increments unread count
    - Shows toast notification
    - May play sound
    - No automatic navigation

28. **Rule triggered (WebSocket)** → Updates rule status
    - Updates last run time
    - Increments fire count
    - May create notification
    - No automatic navigation

29. **Escalation advanced (WebSocket)** → Updates escalation display
    - Moves to next step
    - Updates timeline
    - Shows notification
    - No automatic navigation

30. **Notification read** → Updates UI
    - Decrements unread count
    - Changes visual state
    - May hide from Unread filter
    - No automatic navigation

31. **Rule disabled** → Updates rule display
    - Changes status indicator
    - Decrements active rules count
    - Shows confirmation
    - No automatic navigation

32. **Preferences saved** → Shows confirmation
    - Success message
    - Updates take effect
    - No automatic navigation

33. **Quiet hours active** → Shows quiet mode indicator
    - Header badge
    - Paused notifications
    - Critical bypass indicator
    - No automatic navigation

---