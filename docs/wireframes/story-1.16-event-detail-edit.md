# Story 1.16: Event Detail/Edit Screen - Wireframe

**Story**: Epic 1, Story 1.16 - Event Management Service
**Screen**: Event Detail/Edit Screen
**User Role**: Organizer
**Related FR**: FR2 (16-Step Workflow Management), FR5 (Progressive Publishing), FR12 (Logistics Management), FR17 (Speaker Management), FR19 (Progressive Publishing Engine)

---

## Event Detail/Edit Screen

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back to Dashboard    Event Details                      [Save Changes] [Event Settings] │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─── EVENT INFORMATION ─────────────────────────────────────────────────────────┐  │
│  │                                                                                │  │
│  │  Title: [Spring Conference 2025                                         ] ✎   │  │
│  │                                                                                │  │
│  │  Description:                                                                  │  │
│  │  [A comprehensive Spring conference covering advanced microservices    ] ✎   │  │
│  │  [architecture patterns, cloud-native development, and lessons learned]       │  │
│  │  [from production deployments.                                         ]      │  │
│  │                                                                                │  │
│  │  ┌─────────┬──────────────┬────────────┬─────────────┬──────────────────────┐ │  │
│  │  │ Event # │ Event Date   │ Event Type │ Status      │ Publishing Date      │ │  │
│  │  ├─────────┼──────────────┼────────────┼─────────────┼──────────────────────┤ │  │
│  │  │   54    │ Mar 15, 2025 │ Full Day ▼ │ Published ● │ Jan 15, 2025         │ │  │
│  │  └─────────┴──────────────┴────────────┴─────────────┴──────────────────────┘ │  │
│  │                                                                                │  │
│  │  Theme: [Cloud-Native Architecture                                      ] ✎   │  │
│  │                                                                                │  │
│  │  Registration Deadline: [Mar 10, 2025  📅]    Capacity: [150] Current: 87    │  │
│  │                                                                                │  │
│  └────────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                       │
│  ┌─── WORKFLOW PROGRESS ────────┬─── KEY METRICS ──────────────────────────────┐  │
│  │                               │                                              │  │
│  │  ████████████████░░░░ 80%     │  📊 Speakers: 12/12 confirmed               │  │
│  │                               │  ✓ Topics: 3 assigned                        │  │
│  │  Step 12/16: Agenda Final.   │  ⚠️ Materials: 2 pending                    │  │
│  │                               │  📝 Registrations: 87/150                   │  │
│  │  [View Workflow Details]      │  💰 Budget: CHF 15,000                      │  │
│  │                               │                                              │  │
│  └───────────────────────────────┴──────────────────────────────────────────────┘  │
│                                                                                       │
│  ┌─── VENUE & LOGISTICS ────────────────────────────────────────────────────────┐  │
│  │                                                                               │  │
│  │  Venue: [Kursaal Bern ▼]                             [Change Venue]          │  │
│  │                                                                               │  │
│  │  Address: Kornhausstrasse 3, 3013 Bern                                       │  │
│  │  Capacity: 200 | Parking: Available | Wheelchair Access: Yes                │  │
│  │                                                                               │  │
│  │  Booking Status: ✓ Confirmed (Confirmation #: KB-2025-03-001)               │  │
│  │  Contact: Anna Schmidt (anna.schmidt@kursaal-bern.ch, +41 31 339 55 00)     │  │
│  │                                                                               │  │
│  │  ┌─ Catering ────────────────────────────────────────────────┐              │  │
│  │  │ Provider: [Select Caterer ▼]           [+ Add Caterer]    │              │  │
│  │  │ Menu: Not configured                 [Configure Menu]      │              │  │
│  │  │ Dietary Requirements: 5 vegetarian, 2 vegan, 3 gluten-free │              │  │
│  │  └────────────────────────────────────────────────────────────┘              │  │
│  │                                                                               │  │
│  │  [View Full Logistics Details]                                               │  │
│  │                                                                               │  │
│  └───────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                       │
│  ┌─── ASSIGNED TOPICS (3) ──────────────────────────────────────────────────────┐  │
│  │                                                                               │  │
│  │  ● Microservices Architecture Patterns                      [View] [Remove]  │  │
│  │    Last used: BATbern #48 (2023) | Partner votes: 18                         │  │
│  │                                                                               │  │
│  │  ● Cloud-Native Development Best Practices                  [View] [Remove]  │  │
│  │    Last used: Never | Partner votes: 12                                      │  │
│  │                                                                               │  │
│  │  ● Production Deployment Lessons Learned                    [View] [Remove]  │  │
│  │    Last used: BATbern #51 (2024) | Partner votes: 15                         │  │
│  │                                                                               │  │
│  │  [+ Add Topic from Backlog]                                                  │  │
│  │                                                                               │  │
│  └───────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                       │
│  ┌─── SPEAKERS & SESSIONS (12) ─────────────────────────────────────────────────┐  │
│  │                                                                               │  │
│  │  Slot 1 | 09:00-10:00 | Dr. Sarah Miller (Accenture)               ✓        │  │
│  │     "Microservices: From Theory to Practice"                                 │  │
│  │     [View Details] [Edit Slot] [Materials: Complete ✓]                       │  │
│  │                                                                               │  │
│  │  Slot 2 | 10:15-11:15 | Prof. James Wilson (University of Bern)     ✓        │  │
│  │     "Cloud Security Architecture"                                            │  │
│  │     [View Details] [Edit Slot] [Materials: Pending ⚠️]                      │  │
│  │                                                                               │  │
│  │  Slot 3 | 11:30-12:30 | Anna Schmidt (SwissRe)                       ✓        │  │
│  │     "Kubernetes in Production: Real-World Experience"                        │  │
│  │     [View Details] [Edit Slot] [Materials: Pending ⚠️]                      │  │
│  │                                                                               │  │
│  │  ... (9 more slots)                                                          │  │
│  │                                                                               │  │
│  │  [View Full Agenda] [Manage Speaker Assignments] [Auto-Assign Speakers]      │  │
│  │                                                                               │  │
│  └───────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                       │
│  ┌─── TEAM ASSIGNMENTS ──────────────────────────────────────────────────────────┐  │
│  │                                                                               │  │
│  │  Lead Organizer: Sally Organizer (sally@batbern.ch)                          │  │
│  │  Co-Organizers: Mark Thompson, Anna Weber                                    │  │
│  │  Moderator: [Not assigned ▼]                               [Assign]          │  │
│  │  Content Reviewer: Dr. Peter Lang                          [Change]          │  │
│  │                                                                               │  │
│  │  [Manage Team Assignments]                                                   │  │
│  │                                                                               │  │
│  └───────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                       │
│  ┌─── PUBLISHING CONFIGURATION ──────────────────────────────────────────────────┐  │
│  │                                                                               │  │
│  │  Publishing Strategy: Progressive ●                                           │  │
│  │  Current Phase: Agenda Published                                             │  │
│  │                                                                               │  │
│  │  Publishing Timeline:                                                         │  │
│  │    ✓ Topic Published (Immediate) - Jan 5, 2025                              │  │
│  │    ✓ Speakers Published (1 month prior) - Feb 15, 2025                      │  │
│  │    ✓ Final Agenda Published (2 weeks prior) - Mar 1, 2025                   │  │
│  │    ○ Post-Event Materials (After event) - Not yet available                 │  │
│  │                                                                               │  │
│  │  Quality Checkpoints:                                                         │  │
│  │    ✓ Abstract length validation (max 1000 chars)                            │  │
│  │    ✓ Lessons learned requirement met                                        │  │
│  │    ✓ All materials submitted                                                │  │
│  │    ✓ Moderator review complete                                              │  │
│  │                                                                               │  │
│  │  [Configure Publishing] [Republish Event] [Preview Public Page]              │  │
│  │                                                                               │  │
│  └───────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                       │
│  ┌─── NOTIFICATIONS & REMINDERS ────────────────────────────────────────────────┐  │
│  │                                                                               │  │
│  │  Active Automations: 8                                                        │  │
│  │                                                                               │  │
│  │  ● Speaker deadline reminders (3 days before) - Active                       │  │
│  │  ● Registration confirmation emails - Active                                 │  │
│  │  ● Final agenda distribution - Scheduled for Mar 1, 2025                     │  │
│  │  ● Event day check-in reminders - Scheduled for Mar 15, 2025                │  │
│  │                                                                               │  │
│  │  [Manage Notifications] [Send Manual Notification]                           │  │
│  │                                                                               │  │
│  └───────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                       │
│  ┌─── ACTION BUTTONS ───────────────────────────────────────────────────────────┐  │
│  │                                                                               │  │
│  │  [Cancel Changes]  [Save as Draft]  [Save & Continue]  [Delete Event]        │  │
│  │                                                                               │  │
│  └───────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## Key Interactive Elements

- **Edit Fields (✎)**: Click to edit event title, description, theme inline
- **Dropdown Selectors (▼)**: Select event type, venue, caterer from predefined lists
- **Date Picker (📅)**: Interactive calendar for selecting registration deadline
- **Progress Bar**: Shows workflow completion percentage, click to view details
- **Topic Cards**: View topic details, remove topics, add new topics from backlog
- **Speaker/Session Rows**: Expandable rows showing session details with edit/view actions
- **Status Indicators**: Visual feedback for materials status, booking confirmation
- **Action Buttons**: Context-specific actions for each section (manage, configure, assign)
- **Auto-Assign**: Intelligent algorithm to automatically assign speakers to optimal slots
- **Publishing Timeline**: Visual representation of publishing phases with checkpoints
- **Team Assignment Dropdowns**: Select team members for various roles
- **Notification Management**: Configure automated notification rules and send manual notifications

## Functional Requirements Met

- **FR2**: Complete 16-step workflow visibility with current step and progress percentage
- **FR5**: Progressive event publishing configuration with automated content updates
- **FR12**: Multi-year venue reservations, catering coordination, and logistics management integrated
- **FR17**: Intelligent speaker matching and assignment tracking with slot management
- **FR19**: Progressive publishing engine with content validation and quality checkpoints
- **FR20**: Intelligent notification system with role-based alerts and automated escalation

## User Interactions

1. **Edit Event Information**: Click edit icon (✎) to modify title, description, or theme
2. **Change Event Date**: Click date field to open calendar picker and select new date
3. **Update Event Type**: Select from dropdown (Full Day, Afternoon, Evening) to reconfigure slots
4. **View Workflow Details**: Click workflow section to navigate to detailed 16-step visualization
5. **Change Venue**: Click [Change Venue] to open venue selection dialog
6. **Configure Catering**: Select caterer, configure menu options, specify dietary requirements
7. **Manage Topics**: View topic details, remove topics, or add from backlog
8. **View Speaker Details**: Click [View Details] to see full speaker profile and session information
9. **Edit Slot Assignment**: Reassign speakers to different time slots via drag-and-drop or manual selection
10. **Auto-Assign Speakers**: Use AI algorithm to automatically optimize speaker slot assignments
11. **Assign Team Members**: Select organizers, moderators, and reviewers from team member list
12. **Configure Publishing**: Set up progressive publishing timeline and quality checkpoints
13. **Manage Notifications**: Enable/disable automated notifications, send manual messages
14. **Save Changes**: Save draft or publish changes immediately
15. **Delete Event**: Permanently remove event (with confirmation dialog)

## Technical Notes

- Form auto-save to prevent data loss (save every 30 seconds)
- Optimistic UI updates for immediate feedback
- Real-time validation for required fields and constraints
- Confirmation dialogs for destructive actions (delete event, remove speakers)
- Rich text editor for event description with formatting options
- Inline editing for quick updates without page navigation
- Responsive layout adapts to tablet and mobile devices
- Role-based field visibility (some fields only for lead organizer)
- Audit trail for all changes (who changed what and when)
- Integration with workflow state machine for status transitions
- Optimistic concurrency control for concurrent editing detection
- Data persistence uses Event aggregate root pattern (DDD)

---

## API Requirements

### Initial Page Load APIs

When the Event Detail/Edit Screen loads, the following APIs are called to provide the necessary data:

1. **GET /api/v1/events/{eventId}**
   - Returns: Complete event entity including id, eventNumber, title, description, eventDate, eventType, status, workflowState, theme, registrationDeadline, capacity, currentAttendeeCount, publishedAt, metadata
   - Used for: Populate all event information fields

2. **GET /api/v1/events/{eventId}/venue**
   - Returns: Venue entity with id, name, address, capacity, amenities (parking, wheelchair access), bookingStatus, confirmationNumber, contactPerson (name, email, phone)
   - Used for: Display venue information and logistics section

3. **GET /api/v1/events/{eventId}/catering**
   - Returns: Catering configuration with providerId, providerName, menuStatus, dietaryRequirements (breakdown by type)
   - Used for: Populate catering section with current configuration

4. **GET /api/v1/events/{eventId}/workflow**
   - Returns: Workflow state with currentStep (1-16), completionPercentage, stepName, nextSteps, blockers, warnings
   - Used for: Display workflow progress bar and current step information

5. **GET /api/v1/events/{eventId}/metrics**
   - Returns: Key metrics including confirmedSpeakersCount, totalSpeakersCount, assignedTopicsCount, pendingMaterialsCount, registrationCount, registrationCapacity, budgetAmount
   - Used for: Populate key metrics section with real-time statistics

6. **GET /api/v1/events/{eventId}/topics**
   - Returns: Array of assigned topics with id, title, lastUsedEvent (eventNumber, year), partnerVotes count, usageHistory
   - Used for: Display assigned topics with historical usage data

7. **GET /api/v1/events/{eventId}/sessions**
   - Query params: includeDetails=true
   - Returns: Array of sessions with id, slotNumber, startTime, endTime, speaker (id, name, company), title, materialsStatus (complete/pending/missing)
   - Used for: Populate speakers & sessions section with slot assignments

8. **GET /api/v1/events/{eventId}/team**
   - Returns: Team assignments with leadOrganizerId, leadOrganizerName, coOrganizers array (id, name), moderatorId (optional), contentReviewerId
   - Used for: Display team assignments section

9. **GET /api/v1/events/{eventId}/publishing**
   - Returns: Publishing configuration with strategy (progressive/immediate), currentPhase, timeline (array of phases with name, date, status), qualityCheckpoints (array with name, status)
   - Used for: Display publishing configuration and timeline

10. **GET /api/v1/events/{eventId}/notifications**
    - Returns: Active notification automations with type, description, status (active/paused), scheduledDate
    - Used for: Show active notification rules and automations

11. **GET /api/v1/venues**
    - Query params: available=true, includeMetadata=true
    - Returns: Array of available venues for dropdown selection with id, name, capacity, location
    - Used for: Populate venue selection dropdown

12. **GET /api/v1/organizers/{organizerId}/team-members**
    - Returns: Team members available for assignment with id, name, role, availability
    - Used for: Populate team member selection dropdowns

---

## Action APIs

### Event Information Updates

1. **PUT /api/v1/events/{eventId}**
   - Triggered by: [Save Changes] button or auto-save (every 30 seconds)
   - Payload: `{ title?, description?, eventDate?, eventType?, theme?, registrationDeadline?, capacity? }`
   - Response: Updated event entity with new updatedAt timestamp, validation results
   - Used for: Save all event information changes

2. **PATCH /api/v1/events/{eventId}/title**
   - Triggered by: Inline title edit field blur
   - Payload: `{ title: string }`
   - Response: Confirmation with updated title, timestamp
   - Used for: Quick inline update of event title

3. **PATCH /api/v1/events/{eventId}/description**
   - Triggered by: Inline description edit field blur
   - Payload: `{ description: string }`
   - Response: Confirmation with updated description, timestamp
   - Used for: Quick inline update of event description

4. **PUT /api/v1/events/{eventId}/event-type**
   - Triggered by: Event Type dropdown change
   - Payload: `{ eventType: "full_day" | "afternoon" | "evening" }`
   - Response: Updated event type, reconfigured slot configuration (minSlots, maxSlots, slotDuration)
   - Used for: Change event type and automatically reconfigure time slots

### Workflow Management

5. **POST /api/v1/events/{eventId}/workflow/advance**
   - Triggered by: [View Workflow Details] → Advance Step button
   - Payload: `{ targetStep: number, notes?: string }`
   - Response: Updated workflow state with new currentStep, completionPercentage, validation results
   - Used for: Manually advance workflow to next step

6. **GET /api/v1/events/{eventId}/workflow/validation**
   - Triggered by: Attempting to advance workflow step
   - Returns: Validation results with canAdvance (boolean), blockers array, warnings array
   - Used for: Validate if workflow can advance to next step

### Venue & Logistics Management

7. **PUT /api/v1/events/{eventId}/venue**
   - Triggered by: [Change Venue] → Select new venue → Confirm
   - Payload: `{ venueId: string, bookingNotes?: string }`
   - Response: Updated venue assignment, booking status, confirmation needed flag
   - Used for: Assign or change event venue

8. **POST /api/v1/events/{eventId}/venue/booking/confirm**
   - Triggered by: Confirm venue booking dialog
   - Payload: `{ confirmationNumber: string, contactPerson: { name, email, phone }, specialRequirements?: string }`
   - Response: Confirmed booking with status, confirmation details, next steps
   - Used for: Confirm venue booking with confirmation number

9. **PUT /api/v1/events/{eventId}/catering**
   - Triggered by: [Configure Menu] → Save catering configuration
   - Payload: `{ catererId: string, menuConfiguration: object, dietaryRequirements: object, attendeeCount: number }`
   - Response: Updated catering configuration, cost estimate, confirmation status
   - Used for: Configure or update catering provider and menu

10. **POST /api/v1/caterers**
    - Triggered by: [+ Add Caterer] in catering section
    - Payload: `{ name: string, contactPerson: string, email: string, phone: string, specialties: string[] }`
    - Response: Created caterer entity with id, confirmation
    - Used for: Add new caterer to system database

### Topic Management

11. **POST /api/v1/events/{eventId}/topics**
    - Triggered by: [+ Add Topic from Backlog] → Select topic → Add
    - Payload: `{ topicId: string }`
    - Response: Topic assignment confirmation, updated topics array
    - Used for: Assign topic from backlog to event

12. **DELETE /api/v1/events/{eventId}/topics/{topicId}**
    - Triggered by: [Remove] button on topic card
    - Response: Confirmation of topic removal, updated topics array
    - Used for: Remove assigned topic from event

13. **GET /api/v1/topics/{topicId}**
    - Triggered by: [View] button on topic card
    - Returns: Complete topic details with title, description, usageHistory (array of events), partnerVotes, duplicateCheck
    - Used for: Display full topic information in modal or detail view

### Speaker & Session Management

14. **GET /api/v1/events/{eventId}/sessions/{sessionId}**
    - Triggered by: [View Details] button on speaker/session row
    - Returns: Full session details with speakers array, materials (files with status), schedule, requirements
    - Used for: Display complete session information in expanded view or modal

15. **PUT /api/v1/events/{eventId}/sessions/{sessionId}/slot**
    - Triggered by: [Edit Slot] → Change time slot → Save
    - Payload: `{ slotId: string, startTime: Date, endTime: Date }`
    - Response: Updated slot assignment, conflict warnings (if any), schedule updated confirmation
    - Used for: Reassign session to different time slot

16. **POST /api/v1/events/{eventId}/speakers/auto-assign**
    - Triggered by: [Auto-Assign Speakers] button
    - Payload: `{ considerPreferences: boolean, optimizationCriteria: string[] }`
    - Response: Proposed slot assignments with algorithm reasoning, confidence scores, conflicts
    - Used for: Use AI algorithm to automatically optimize speaker slot assignments

17. **GET /api/v1/events/{eventId}/agenda**
    - Triggered by: [View Full Agenda] button
    - Returns: Complete agenda with all sessions sorted by time, speaker details, materials status
    - Used for: Navigate to full agenda view screen

18. **GET /api/v1/events/{eventId}/speakers/assignments**
    - Triggered by: [Manage Speaker Assignments] button
    - Returns: Speaker assignment interface data with available speakers, assigned speakers, slots
    - Used for: Navigate to speaker matching/assignment interface

### Team Management

19. **PUT /api/v1/events/{eventId}/team/moderator**
    - Triggered by: [Assign] button in moderator field → Select moderator → Confirm
    - Payload: `{ moderatorId: string }`
    - Response: Moderator assignment confirmation, notification sent status
    - Used for: Assign or change event moderator

20. **PUT /api/v1/events/{eventId}/team/reviewer**
    - Triggered by: [Change] button in content reviewer field → Select reviewer → Confirm
    - Payload: `{ reviewerId: string }`
    - Response: Reviewer assignment confirmation, notification sent status
    - Used for: Change content reviewer for quality control

21. **GET /api/v1/events/{eventId}/team/assignments**
    - Triggered by: [Manage Team Assignments] button
    - Returns: Complete team assignment interface data with roles, assignments, permissions
    - Used for: Navigate to team management screen

### Publishing Management

22. **PUT /api/v1/events/{eventId}/publishing/configuration**
    - Triggered by: [Configure Publishing] → Update settings → Save
    - Payload: `{ strategy: "progressive" | "immediate", timeline: object[], qualityCheckpoints: string[] }`
    - Response: Updated publishing configuration, next publication date, validation results
    - Used for: Configure or update publishing strategy and timeline

23. **POST /api/v1/events/{eventId}/publishing/republish**
    - Triggered by: [Republish Event] button
    - Payload: `{ reason: string, notifyAttendees: boolean }`
    - Response: Republication confirmation, updated publishedAt timestamp, notification count
    - Used for: Force republish event to public page (for updates after initial publication)

24. **GET /api/v1/events/{eventId}/public-preview**
    - Triggered by: [Preview Public Page] button
    - Returns: Public page preview URL with authentication token for preview access
    - Used for: Open public event landing page in preview mode

### Notification Management

25. **GET /api/v1/events/{eventId}/notifications/rules**
    - Triggered by: [Manage Notifications] button
    - Returns: All notification rules with id, type, trigger conditions, recipients, status
    - Used for: Display notification management interface

26. **POST /api/v1/events/{eventId}/notifications/send**
    - Triggered by: [Send Manual Notification] → Compose message → Send
    - Payload: `{ recipientType: "speakers" | "attendees" | "partners" | "team", subject: string, message: string, urgency: "normal" | "high" }`
    - Response: Notification sent confirmation, recipient count, delivery status
    - Used for: Send manual notification to specific audience

27. **PUT /api/v1/events/{eventId}/notifications/rules/{ruleId}/toggle**
    - Triggered by: Toggle notification rule on/off
    - Payload: `{ enabled: boolean }`
    - Response: Rule status updated, confirmation
    - Used for: Enable or disable automated notification rules

### Save & Delete Actions

28. **POST /api/v1/events/{eventId}/draft**
    - Triggered by: [Save as Draft] button
    - Payload: Current form state as draft
    - Response: Draft saved confirmation with timestamp, auto-saved status
    - Used for: Save incomplete changes as draft without validation

29. **DELETE /api/v1/events/{eventId}**
    - Triggered by: [Delete Event] → Confirmation dialog → Confirm Delete
    - Response: Deletion confirmation, cleanup status (associated data removed)
    - Used for: Permanently delete event (with cascade delete of related data)

30. **GET /api/v1/events/{eventId}/deletion-impact**
    - Triggered by: [Delete Event] button click (before confirmation)
    - Returns: Impact analysis with registrationCount, confirmedSpeakers, associatedMaterials, dependentData
    - Used for: Show deletion impact in confirmation dialog

---

## Navigation Map

### Primary Navigation Actions

1. **← Back to Dashboard button** → Navigate to `Event Management Dashboard` (story-1.16-event-management-dashboard.md)
   - Returns to organizer main dashboard
   - Unsaved changes prompt if modifications exist
   - Context: Navigates back to event list view

2. **[Event Settings] button** → Navigate to `Event Settings Screen` (Missing Wireframe - HIGH PRIORITY)
   - Advanced event configuration
   - Integration settings
   - Notification preferences
   - Access permissions management
   - Context: Passes eventId for settings configuration

3. **BATbern logo/header** → Navigate to `Event Management Dashboard`
   - Returns to home dashboard
   - Confirms save/discard changes if modified
   - Context: Global navigation to dashboard

### Section-Specific Navigation

4. **[View Workflow Details] button** → Navigate to `Workflow Visualization` (story-1.16-workflow-visualization.md)
   - Detailed 16-step workflow view
   - Step-by-step progress tracking
   - Context: Passes eventId to show workflow for this specific event

5. **Workflow progress bar click** → Navigate to `Workflow Visualization` (story-1.16-workflow-visualization.md)
   - Same as [View Workflow Details]
   - Focuses on current workflow step
   - Context: eventId with currentStep highlighted

6. **[Change Venue] button** → Opens `Venue Selection Dialog` (Modal)
   - Searchable venue list
   - Venue comparison
   - Booking availability check
   - Context: Returns selected venueId to update event

7. **[View Full Logistics Details] button** → Navigate to `Logistics Coordination` (story-4.4-logistics-coordination.md)
   - Complete logistics management
   - Venue booking details
   - Catering coordination
   - Context: Passes eventId for logistics view

8. **[Configure Menu] button** → Opens `Catering Configuration Dialog` (Modal)
   - Menu selection interface
   - Dietary requirements form
   - Cost estimation
   - Context: Returns catering configuration object

9. **[+ Add Caterer] button** → Opens `Add Caterer Form` (Modal)
   - New caterer creation form
   - Contact information input
   - Specialty selection
   - Context: Returns new catererId on creation

10. **[View] button on Topic card** → Opens `Topic Detail Modal` (Modal)
    - Full topic description
    - Usage history across events
    - Partner vote breakdown
    - Duplicate topic warnings
    - Context: Displays topicId details

11. **[+ Add Topic from Backlog] button** → Navigate to `Topic Backlog Management` (story-2.2-topic-backlog-management.md)
    - Topic search and selection
    - Historical usage tracking
    - Partner voting influence
    - Context: Returns with selected topicId(s) to add to event

12. **[View Details] button on Speaker/Session row** → Opens `Session Detail Dialog` (Modal)
    - Full session information
    - Speaker profile
    - Material submission status
    - Quality review feedback
    - Context: Displays sessionId and speakerId details

13. **[Edit Slot] button on Speaker/Session row** → Opens `Slot Assignment Editor` (Modal)
    - Time slot selection
    - Conflict checking
    - Speaker preferences display
    - Technical requirements
    - Context: Allows slot reassignment for sessionId

14. **[Materials: Pending ⚠️] link** → Opens `Material Submission Status` (Modal)
    - Missing materials checklist
    - Submission deadlines
    - Contact speaker action
    - Material review status
    - Context: Shows submission status for sessionId

15. **[View Full Agenda] button** → Navigate to `Event Timeline View` (story-3.5-event-timeline.md)
    - Complete day timeline
    - All sessions chronologically
    - Visual schedule representation
    - Context: Passes eventId for timeline view

16. **[Manage Speaker Assignments] button** → Navigate to `Speaker Matching Interface` (story-3.1-speaker-matching-interface.md)
    - Intelligent speaker matching
    - Slot assignment management
    - Overflow handling
    - Context: Passes eventId for speaker management

17. **[Auto-Assign Speakers] button** → Opens `Auto-Assignment Preview Dialog` (Modal)
    - AI-generated slot assignments
    - Reasoning explanation
    - Conflict warnings
    - Accept/reject assignment options
    - Context: Shows proposed assignments for eventId

18. **[Manage Team Assignments] button** → Navigate to `Team Management Screen` (Missing Wireframe)
    - Team member roles
    - Permission assignments
    - Workload distribution
    - Context: Passes eventId for team configuration

19. **[Configure Publishing] button** → Opens `Publishing Configuration Dialog` (Modal)
    - Progressive publishing timeline
    - Quality checkpoint selection
    - Phase date configuration
    - Content validation rules
    - Context: Returns publishing configuration object

20. **[Republish Event] button** → Opens `Republish Confirmation Dialog` (Modal)
    - Republication reason input
    - Attendee notification option
    - Impact summary
    - Confirm/cancel actions
    - Context: Triggers republish for eventId

21. **[Preview Public Page] button** → Opens in new tab/window
    - Public event landing page
    - Preview mode with edit indicator
    - Organizer view of attendee experience
    - Context: Opens public URL for eventId with preview token

22. **[Manage Notifications] button** → Navigate to `Notification Center` (story-1.20-notification-center.md)
    - All notification rules
    - Automation configuration
    - Delivery status tracking
    - Context: Filtered to eventId notifications

23. **[Send Manual Notification] button** → Opens `Compose Notification Dialog` (Modal)
    - Recipient selection
    - Message composition
    - Template selection
    - Send immediately or schedule
    - Context: Pre-populates eventId context

### Save & Delete Actions Navigation

24. **[Save Changes] button** → Saves and stays on current screen
    - Validates all form fields
    - Shows success/error toast notification
    - Updates timestamp
    - No navigation

25. **[Save as Draft] button** → Saves and stays on current screen
    - Saves without validation
    - Draft indicator displayed
    - Success toast notification
    - No navigation

26. **[Save & Continue] button** → Saves and advances workflow
    - Validates form
    - Advances to next workflow step
    - Navigation depends on next step (may navigate to different screen)
    - Context: May navigate to workflow-specific screen

27. **[Delete Event] button** → Opens `Delete Confirmation Dialog` (Modal)
    - Deletion impact summary
    - Cascading deletion warnings
    - Confirm/cancel options
    - On confirm: Navigate to `Event Management Dashboard`

28. **[Cancel Changes] button** → Discards changes and returns to `Event Management Dashboard`
    - Confirmation dialog if unsaved changes
    - Reverts all modifications
    - Context: Returns to dashboard

### Error States & Redirects

29. **Unauthorized Access** → Redirect to `Login Screen`
    - User not authenticated
    - Session expired
    - Context: Returns to event detail after successful login

30. **Insufficient Permissions** → Redirect to `Event Management Dashboard` with error message
    - User lacks organizer role
    - Not assigned to this event
    - Context: Shows permission denied message

31. **Event Not Found** → Redirect to `Event Management Dashboard` with error message
    - Invalid eventId
    - Event deleted by another user
    - Context: Shows "Event not found" error

32. **Concurrent Edit Conflict** → Shows `Conflict Resolution Dialog` (Modal)
    - Another organizer modified event
    - Shows conflicting changes
    - Options: reload, force save, merge changes
    - Context: Allows user to resolve conflict

## Responsive Design Considerations

### Mobile Layout Changes

- Event information section becomes vertically stacked single column
- Workflow progress and key metrics stack vertically instead of side-by-side
- Speaker/session rows show condensed view with expand icon
- Action buttons collapse into hamburger menu or bottom sheet
- Inline edit fields may open in modal dialogs for better touch interaction
- Date pickers use mobile-optimized calendar widgets

### Tablet Layout Changes

- Two-column layout maintained but with adjusted widths
- Speaker/session section shows fewer rows with scroll
- Side-by-side sections (workflow/metrics) remain but with reduced padding
- Action buttons remain visible but with smaller font size

### Mobile-Specific Interactions

- Swipe left on speaker/session row to reveal quick actions (edit, delete, view)
- Pull-to-refresh to reload event data
- Bottom sheet for action menus instead of dropdowns
- Touch-optimized hit targets (minimum 44px)
- Floating action button (FAB) for primary save action

## Accessibility Notes

- All form inputs have associated labels with proper for/id attributes
- ARIA labels on all interactive elements (buttons, links, dropdowns)
- Keyboard navigation: Tab through all form fields, Escape to close modals
- Focus indicators clearly visible with high contrast outline
- Error messages announced via ARIA live regions
- Color contrast meets WCAG 2.1 AA standards (4.5:1 for normal text, 3:1 for large text)
- Status indicators use both color and icon for color-blind users
- Form field validation errors announced to screen readers
- Heading hierarchy properly structured (h1 for page title, h2 for sections)
- Skip navigation link for keyboard users to bypass header

## State Management

### Local Component State

- Form field values (title, description, date, etc.) managed locally
- Inline edit mode flags (isEditingTitle, isEditingDescription)
- Validation errors per field
- Dirty/modified flags to track unsaved changes
- Modal open/close states (venueSelectionOpen, cateringConfigOpen)
- Expanded/collapsed states for speaker/session rows
- Auto-save timer state

### Global State (Zustand Store)

- Current user and role (organizerId, permissions)
- Active event ID (for deep linking and navigation context)
- Notification count for header badge
- Recent events list for quick navigation
- Team member cache (for assignment dropdowns)

### Server State (React Query)

- Event entity data (cached 5 minutes)
- Venue list (cached 1 hour)
- Team members (cached 30 minutes)
- Topics (cached 10 minutes)
- Sessions (cached 5 minutes, invalidated on updates)
- Publishing configuration (cached 10 minutes)
- Stale-while-revalidate pattern for fresh data
- Automatic refetch on window focus
- Optimistic updates for immediate UI feedback

### Concurrent Editing

- Version-based conflict detection (optimistic concurrency control)
- Save conflicts detected via version number mismatch
- Show conflict resolution dialog when save fails due to concurrent edit
- User must manually reload to see other users' changes
- No real-time presence indicators

## Form Validation Rules

### Field-Level Validations

- **Title**: Required, min 10 characters, max 200 characters
- **Description**: Required, min 50 characters, max 2000 characters
- **Event Date**: Required, must be future date (at least 30 days from now)
- **Event Type**: Required, must be one of [full_day, afternoon, evening]
- **Registration Deadline**: Required, must be before event date
- **Capacity**: Required, positive integer, min 20, max 1000
- **Theme**: Optional, max 100 characters
- **Venue**: Required, must reference existing venue
- **Budget**: Optional, positive number

### Form-Level Validations

- Registration deadline must be at least 7 days before event date
- Capacity cannot be less than current registration count
- At least 1 topic must be assigned before publishing
- Minimum speaker count must meet event type requirements (6-8 for full day/afternoon, 3-4 for evening)
- All speakers must have confirmed status before finalizing agenda
- Venue must be confirmed before advancing to publishing stage
- Publishing configuration required before marking event as published
- Cannot delete event if registrations exist (must cancel event instead)

## Edge Cases & Error Handling

- **Empty State (New Event)**: Show guided setup wizard instead of full form
- **Loading State**: Display skeleton screens for all sections during initial data fetch
- **Error State (API Failure)**: Show error message with [Retry] button and support contact
- **No Topics Assigned**: Show prominent call-to-action to add topics from backlog
- **No Speakers Assigned**: Display warning banner with [Invite Speakers] quick action
- **Concurrent Edit Conflict**: Show conflict resolution dialog with side-by-side comparison
- **Venue Unavailable**: Show warning badge and suggest alternative venues
- **Budget Exceeded**: Display warning indicator if estimated costs exceed budget
- **Missing Materials (Speaker)**: Highlight pending materials with deadline countdown
- **Registration Full**: Show waitlist option and disable new registrations
- **Past Event Date**: Display read-only archive mode with limited editing capabilities
- **Insufficient Permissions**: Hide edit controls, show read-only view
- **Network Offline**: Cache form data locally, show offline indicator, sync when online
- **Auto-Save Failure**: Show persistent warning banner until successful save
- **Validation Errors on Save**: Scroll to first error field, highlight all errors, show summary message

## Change Log

| Date       | Version | Description                            | Author     |
|------------|---------|----------------------------------------|------------|
| 2025-10-01 | 1.0     | Initial wireframe creation             | Sally (UX) |

## Review Notes

### Stakeholder Feedback

- (To be collected during review sessions)

### Design Iterations

- v1.0: Initial comprehensive design with all functional sections

### Open Questions

1. Should auto-save be configurable per user preference?
2. How should we handle timezone display for international speakers?
3. Should we support event templates for faster event creation?
4. Should deletion require admin approval or just organizer confirmation?
5. Should concurrent editing show real-time cursors/presence indicators?
