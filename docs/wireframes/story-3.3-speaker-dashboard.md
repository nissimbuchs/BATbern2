# Story 3.3: Speaker Portal Dashboard - Wireframe

**Story**: Epic 3, Story 3.3 - Speaker Management Service
**Screen**: Speaker Portal Dashboard (Main Landing)
**User Role**: Speaker
**Related FR**: FR3 (Speaker Self-Service), FR20 (Notifications)

---

## Speaker Portal Dashboard (Main Landing)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ BATbern Speaker Portal                            Peter Muller ▼  [🔔 2] [Profile]  │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─────────────────────────────────────────────────────────────────────────────┐    │
│  │ Welcome back, Peter! Here's your speaker dashboard                          │    │
│  └─────────────────────────────────────────────────────────────────────────────┘    │
│                                                                                       │
│  ┌──────────────── MY EVENTS ──────────────────┬──── QUICK ACTIONS ─────────────┐   │
│  │                                              │                                 │   │
│  │  ┌─────────────────────────────────────┐     │  [📝 Update Profile]           │   │
│  │  │ UPCOMING                             │     │  [📊 View Past Talks]          │   │
│  │  │                                      │     │  [📧 Contact Organizer]        │   │
│  │  │ Spring Conference 2025               │     │  [📚 Resource Center]          │   │
│  │  │ May 15, 2025 • Kursaal Bern         │     │                                 │   │
│  │  │                                      │     │  ──────────────────            │   │
│  │  │ Status: ✓ Confirmed                 │     │                                 │   │
│  │  │ Slot: 09:45 - 10:30 (45 min)       │     │                                 │   │
│  │  │ Topic: Kubernetes Best Practices     │     │  NOTIFICATIONS                  │   │
│  │  │                                      │     │                                 │   │
│  │  │ Tasks Due:                          │     │  🔴 Abstract revision needed    │   │
│  │  │ ⚠️ Final slides - Due Apr 30       │     │      Due in 2 days             │   │
│  │  │ ✓ Abstract submitted                │     │      [Review Feedback]          │   │
│  │  │ ✓ Bio & photo uploaded              │     │                                 │   │
│  │  │                                      │     │  ℹ️ Event schedule published    │   │
│  │  │ [View Event] [Submit Materials]     │     │      Your talk: 09:45 AM       │   │
│  │  └─────────────────────────────────────┘     │      [View Schedule]           │   │
│  │                                              │                                 │   │
│  │  ┌─────────────────────────────────────┐     └─────────────────────────────────┘ │
│  │  │ INVITATIONS                          │                                        │
│  │  │                                      │                                        │
│  │  │ 🆕 Autumn Conference 2025            │                                        │
│  │  │ Nov 20, 2025 • Tech Park Zurich     │                                        │
│  │  │                                      │                                        │
│  │  │ Topic: Cloud Security Trends         │                                        │
│  │  │ Response needed by: Mar 15          │                                        │
│  │  │                                      │                                        │
│  │  │ [Accept] [Decline] [Need More Info] │                                        │
│  │  └─────────────────────────────────────┘                                        │
│  └──────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                       │
│  ┌─── YOUR IMPACT ───────────────────┬─── UPCOMING DEADLINES ───────────────────┐   │
│  │                                   │                                            │   │
│  │  Total Talks: 12                  │  Mar 10  Autumn Conference response       │   │
│  │  Total Attendees: 2,847           │  Apr 30  Spring Conference final slides   │   │
│  │  Avg Rating: 4.7/5                │  May 01  Speaker dinner RSVP              │   │
│  │  Downloads: 3,421                 │  May 10  Tech check appointment           │   │
│  │                                   │                                            │   │
│  │  Top Topics:                      │  [Sync to Calendar] [Email Reminders]     │   │
│  │  • Kubernetes (5 talks)           │                                            │   │
│  │  • DevOps (4 talks)               │                                            │   │
│  │  • Cloud Native (3 talks)         │                                            │   │
│  └───────────────────────────────────┴────────────────────────────────────────────┘  │
│                                                                                       │
│  ┌─── RECENT ACTIVITY ───────────────────────────────────────────────────────────┐   │
│  │                                                                                │   │
│  │  Yesterday   Organizer reviewed your abstract - 1 revision requested          │   │
│  │  Mar 5       You uploaded speaker photo                                       │   │
│  │  Mar 3       You accepted Spring Conference invitation                        │   │
│  │  Mar 1       New invitation received for Autumn Conference                    │   │
│  └────────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## Key Interactive Elements

- **Event Cards**: Click to expand full event details and requirements
- **Status Indicators**: Visual progress of submission tasks (✓ Done, ⚠️ Pending, 🔴 Overdue)
- **Quick Actions**: One-click access to common speaker tasks
- **Notifications Panel**: Prioritized alerts with direct action buttons
- **Calendar Sync**: Export deadlines to personal calendar
- **Activity Feed**: Chronological log of all speaker activities

## Functional Requirements Met

- **FR3**: Complete speaker self-service capabilities
- **FR20**: Intelligent notifications with priority levels
- **Event Management**: Track upcoming and past speaking engagements
- **Task Tracking**: Clear visibility of required submissions and deadlines
- **Impact Metrics**: Showcase speaker's contribution and reach
- **Invitation Management**: Accept/decline new speaking opportunities

## User Interactions

1. **View Event Details**: Click event card to see complete information
2. **Submit Materials**: Direct link to material submission workflow
3. **Respond to Invitations**: Accept, decline, or request more information
4. **Update Profile**: Manage speaker bio, photo, expertise
5. **Calendar Integration**: Export deadlines to Google/Outlook/iCal
6. **View Notifications**: Act on urgent items from notification panel

## Technical Notes

- Real-time updates via WebSocket for new invitations and notifications
- Progress tracking integrated with workflow states
- Calendar export using iCal format
- Responsive design for mobile access
- Push notification support for urgent items
- Integration with speaker profile and historical talk data

---

## API Requirements

APIs needed to load and display data for this screen:

### Initial Page Load

1. **GET /api/v1/speakers/{speakerId}/dashboard**
   - Retrieve comprehensive dashboard data in a single call
   - Response includes: upcoming events, invitations, notifications, impact metrics
   - Used for: Populating entire dashboard on load
   - Optimization: Aggregated endpoint to reduce multiple API calls

2. **GET /api/v1/speakers/{speakerId}/events**
   - Retrieve speaker's upcoming and past events
   - Query params: `status=upcoming|past`, `limit=10`
   - Response includes: event details, slot info, task status
   - Used for: "My Events" section with upcoming/past tabs

3. **GET /api/v1/speakers/{speakerId}/invitations**
   - Retrieve pending invitations
   - Query params: `status=pending`, `limit=5`
   - Response includes: event details, response deadline, invitation message
   - Used for: "Invitations" panel showing new speaking opportunities

4. **GET /api/v1/speakers/{speakerId}/notifications**
   - Retrieve prioritized notifications
   - Query params: `unread=true`, `priority=high|medium|low`, `limit=10`
   - Response includes: notification type, message, action links, timestamp
   - Used for: Notification panel and bell icon badge count

5. **GET /api/v1/speakers/{speakerId}/tasks**
   - Retrieve pending tasks for upcoming events
   - Query params: `status=pending|overdue`, `eventId` (optional)
   - Response includes: task type, deadline, event association, completion status
   - Used for: Task tracking in event cards

6. **GET /api/v1/speakers/{speakerId}/analytics**
   - Retrieve speaker's impact metrics
   - Response includes: total talks, attendees, ratings, download counts, top topics
   - Used for: "Your Impact" panel

7. **GET /api/v1/speakers/{speakerId}/deadlines**
   - Retrieve upcoming deadlines across all events
   - Query params: `fromDate`, `toDate`, `limit=10`
   - Response includes: deadline date, type, event association, description
   - Used for: "Upcoming Deadlines" panel

8. **GET /api/v1/speakers/{speakerId}/activity**
   - Retrieve recent activity feed
   - Query params: `limit=20`, `offset=0`
   - Response includes: activity type, timestamp, description, related entities
   - Used for: "Recent Activity" feed

### WebSocket Connections

9. **WS /api/v1/speakers/{speakerId}/updates**
   - Real-time updates for speaker
   - Events: new invitations, notifications, organizer messages, task updates
   - Used for: Live dashboard updates without page refresh

---

## Action APIs

APIs called by user interactions and actions:

### Event Management

1. **GET /api/v1/events/{eventId}/details**
   - Triggered by: [View Event] button on event card
   - Response: Full event details including agenda, venue, logistics
   - Opens: Event details page or modal

2. **POST /api/v1/speakers/{speakerId}/events/{eventId}/materials**
   - Triggered by: [Submit Materials] button
   - Opens: Material submission workflow (Story 3.3 - Material Submission Wizard)
   - Redirects to: Material submission interface

### Invitation Response

3. **POST /api/v1/invitations/{invitationId}/respond**
   - Triggered by: [Accept], [Decline], or [Need More Info] buttons
   - Payload: `{ response: "accepted|declined|need_more_info", comments: "string" }`
   - Response: Updated invitation status
   - Side effects:
     - Accept: Creates event entry, moves to "My Events"
     - Decline: Archives invitation
     - Need More Info: Opens communication channel
   - Refresh: Dashboard reloads invitation and event sections

### Profile & Settings

4. **GET /api/v1/speakers/{speakerId}/profile**
   - Triggered by: [📝 Update Profile] button
   - Opens: Speaker Profile Management (Story 7.1)
   - Type: Navigate to profile page

5. **PUT /api/v1/speakers/{speakerId}/profile**
   - Triggered by: Save on profile page
   - Payload: Updated profile data
   - Response: Updated speaker profile

### Communication

6. **POST /api/v1/messages/organizer**
   - Triggered by: [📧 Contact Organizer] button
   - Payload: `{ eventId, recipientRole: "organizer", message, subject }`
   - Opens: Communication interface or sends message
   - Integrates with: Communication Hub (Story 7.3)

### Notifications

7. **PUT /api/v1/speakers/{speakerId}/notifications/{notificationId}/read**
   - Triggered by: Clicking notification or [Mark as Read]
   - Payload: `{ read: true }`
   - Response: Updated notification status
   - Updates: Bell icon badge count

8. **GET /api/v1/notifications/{notificationId}/action**
   - Triggered by: Clicking action button in notification (e.g., [Review Feedback])
   - Response: Redirects to appropriate screen or opens modal
   - Navigation: Context-specific based on notification type

### Calendar Integration

9. **GET /api/v1/speakers/{speakerId}/calendar/export**
   - Triggered by: [Sync to Calendar] button
   - Query params: `format=ical|google|outlook`
   - Response: iCal file or calendar integration link
   - Downloads: .ics file or redirects to calendar service

10. **POST /api/v1/speakers/{speakerId}/reminders/email**
    - Triggered by: [Email Reminders] toggle
    - Payload: `{ enabled: true, frequency: "daily|weekly" }`
    - Response: Reminder preferences updated
    - Side effect: Schedules email reminders via AWS SES

### Past Talks & Analytics

11. **GET /api/v1/speakers/{speakerId}/talks/history**
    - Triggered by: [📊 View Past Talks] button
    - Query params: `limit=50`, `includeAnalytics=true`
    - Response: Historical talks with ratings, attendance, downloads
    - Opens: Past talks page or modal

12. **GET /api/v1/speakers/{speakerId}/analytics/detailed**
    - Triggered by: Clicking on "Your Impact" panel
    - Response: Detailed analytics breakdown, trends, top-rated talks
    - Opens: Detailed analytics dashboard

### Resources & Community

13. **GET /api/v1/resources/speaker**
    - Triggered by: [📚 Resource Center] button
    - Response: Speaker resources, templates, guidelines
    - Opens: Resource center page

---

## Navigation Map

Screen transitions triggered by actions and events:

### Primary Navigation

1. **User Dropdown Menu (Peter Muller ▼)**
   - **Options**:
     - My Profile → Speaker Profile Management (Story 7.1)
     - Settings → User settings page
     - Help → Help/documentation
     - Logout → Logout and redirect to homepage
   - **Type**: Dropdown menu

2. **Bell Icon [🔔 2]**
   - **Target**: Notification Center (Story 1.20)
   - **Type**: Dropdown panel or dedicated page
   - **Context**: Show all notifications with filtering

3. **[Profile] Button**
   - **Target**: Speaker Profile Management (Story 7.1)
   - **Type**: Direct navigation
   - **Context**: Edit profile, bio, photo, expertise

### Event Management Navigation

4. **[View Event]** (on event card)
   - **Target**: Event Timeline (Story 3.5)
   - **Type**: Full event details page
   - **Context**: View complete event info, agenda, logistics

5. **[Submit Materials]** (on event card)
   - **Target**: Material Submission Wizard (Story 3.3)
   - **Type**: Multi-step wizard
   - **Context**: Upload presentation, handouts, bio

6. **Event Card Click** (entire card)
   - **Target**: Expanded event view (inline or modal)
   - **Type**: Inline expansion or modal overlay
   - **Context**: Quick view of event details and tasks

### Invitation Response Navigation

7. **[Accept]** on invitation
   - **Action**: POST invitation response
   - **Target**: Remains on dashboard
   - **Feedback**: Success toast, invitation moves to "My Events"
   - **Optional**: Show "Complete Your Profile" prompt if profile incomplete

8. **[Decline]** on invitation
   - **Action**: POST invitation response
   - **Target**: Remains on dashboard
   - **Feedback**: Confirmation dialog, then success toast
   - **Update**: Invitation removed from dashboard

9. **[Need More Info]** on invitation
   - **Target**: Communication interface (modal or inline)
   - **Type**: Modal overlay or inline chat
   - **Context**: Send questions to organizer

### Quick Actions Navigation

10. **[📝 Update Profile]**
    - **Target**: Speaker Profile Management (Story 7.1)
    - **Type**: Full page navigation
    - **Context**: Edit profile information

11. **[📊 View Past Talks]**
    - **Target**: Past Talks History page
    - **Type**: Full page or modal
    - **Context**: View all historical speaking engagements with analytics

12. **[📧 Contact Organizer]**
    - **Target**: Communication Hub (Story 7.3) or Message composer
    - **Type**: Modal or navigation to messages
    - **Context**: Requires event selection if multiple active events

13. **[📚 Resource Center]**
    - **Target**: Speaker Resource Library page
    - **Type**: Full page navigation
    - **Context**: Access templates, guides, best practices

### Notification Actions

14. **[Review Feedback]** (in notification)
    - **Target**: Material review page or submission wizard
    - **Type**: Direct to specific submission requiring revision
    - **Context**: Depends on notification type

15. **[View Schedule]** (in notification)
    - **Target**: Event Timeline (Story 3.5)
    - **Type**: Full event schedule
    - **Context**: Published event agenda

### Deadline Actions

16. **[Sync to Calendar]**
    - **Action**: Downloads .ics file or opens calendar integration
    - **No Navigation**: Remains on dashboard
    - **Feedback**: "Calendar file downloaded" or "Redirecting to calendar"

17. **[Email Reminders]**
    - **Action**: Toggles email reminder preferences
    - **Type**: Modal for configuration
    - **Context**: Set reminder frequency and timing

### Impact Metrics Navigation

18. **"Your Impact" Panel Click**
    - **Target**: Detailed Analytics Dashboard
    - **Type**: Full page or modal
    - **Context**: Deep dive into speaker metrics, trends, comparisons

19. **Topic Link** (in "Top Topics")
    - **Target**: Filtered view of talks by topic
    - **Type**: Past talks page with filter applied
    - **Context**: Show all talks on selected topic

### Activity Feed Navigation

20. **Activity Item Click**
    - **Target**: Context-specific based on activity type
    - **Examples**:
      - "Abstract reviewed" → Submission wizard with feedback
      - "Photo uploaded" → Profile page
      - "Invitation received" → Invitation details
    - **Type**: Dynamic routing based on activity

### Event-Driven Navigation

21. **On New Invitation (WebSocket)**
    - **No Navigation**: Remains on dashboard
    - **Feedback**: Toast notification "New invitation received"
    - **Update**: Invitation panel refreshes, bell icon badge increments

22. **On Task Overdue**
    - **Feedback**: Red highlight on task, notification added
    - **Suggested Action**: Modal prompt to complete overdue task
    - **Navigation**: Option to go directly to task (e.g., submit materials)

23. **On Event Approaching (7 days before)**
    - **Feedback**: Highlight event card with "Coming Soon" badge
    - **Notification**: "Your talk at [Event] is in 7 days"
    - **Action**: Prompt to complete any pending tasks

### Error States

24. **On Session Timeout**
    - **Target**: Login page
    - **Context**: Preserve return URL to redirect back after login

25. **On Network Error**
    - **No Navigation**: Remains on dashboard
    - **Feedback**: "Unable to load data" banner
    - **Action**: Retry button to reload dashboard

### Mobile-Specific

26. **Bottom Navigation** (mobile only)
    - **Tabs**: Dashboard, Events, Notifications, Profile
    - **Context**: Simplified navigation for small screens