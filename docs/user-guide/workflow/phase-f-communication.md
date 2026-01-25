# Phase F: Communication (Steps 13-16)

> Execute event and archive for historical reference

<div class="workflow-phase phase-f">
<strong>Phase F: Task Management & Communications</strong><br>
Status: <span class="feature-status implemented">Implemented</span><br>
Duration: Throughout event lifecycle<br>
Task System: Auto-creating tasks based on event state transitions
</div>

## Overview

Phase F represents the **Task System** - a separate workflow from event states and speaker states. Tasks are assignable work items that auto-create when events reach specific states and must be completed by organizers.

**Key Concept**: Tasks are NOT workflow states. They are parallel work items managed via a Kanban-style task board.

**Key Deliverable**: Completed tasks ensuring smooth event execution (newsletters, venue booking, catering, moderator assignment, etc.)

## Task System Architecture

<span class="feature-status implemented">Implemented</span>

### Default System Tasks (7 Auto-Creating Tasks)

BATbern includes 7 pre-configured tasks that automatically create when events reach specific workflow states:

**Triggered at TOPIC_SELECTION**:
1. **Venue Booking** - Due: 90 days before event
2. **Partner Meeting** - Due: event day
3. **Moderator Assignment** - Due: 14 days before event
4. **Newsletter: Topic** - Due: immediately

**Triggered at AGENDA_PUBLISHED**:
5. **Newsletter: Speakers** - Due: 30 days before event

**Triggered at AGENDA_FINALIZED**:
6. **Newsletter: Final** - Due: 14 days before event
7. **Catering** - Due: 30 days before event

### Task States

All tasks progress through 4 states:

- **TODO** - Not started (overdue tasks highlighted in red)
- **IN_PROGRESS** - Currently working on
- **COMPLETED** - Finished with completion notes
- **CANCELLED** - Not needed (event cancelled or task no longer relevant)

### Task Dashboard

<span class="feature-status implemented">Implemented</span>

Access tasks via the Task Board (Kanban-style interface):

**Columns**:
- TODO (with overdue indicator)
- IN_PROGRESS
- COMPLETED

**Filters**:
- My Tasks (assigned to me)
- All Tasks (team-wide view)
- By Event (tasks for specific event)
- By Due Date (this week, next week, overdue)

**Actions**:
- Drag-and-drop between states
- Assign/reassign tasks to organizers
- Add completion notes
- Create custom tasks

## Step 13: Newsletter Distribution

<span class="feature-status implemented">Implemented via Task System</span>

### Purpose

Send targeted email communications to attendees, speakers, and partners at key milestones.

### Auto-Created Newsletter Tasks

The following newsletter tasks auto-create as the event progresses:

**Newsletter: Topic** - Auto-created when event reaches TOPIC_SELECTION
- Due: Immediately
- Purpose: Announce event topics to build interest
- Recipients: Mailing list subscribers, past attendees

**Newsletter: Speakers** - Auto-created when event reaches AGENDA_PUBLISHED
- Due: 30 days before event
- Purpose: Announce confirmed speakers
- Recipients: Registered attendees, mailing list
- **Note**: Works with auto-publishing feature (speakers auto-publish @ 30 days)

**Newsletter: Final** - Auto-created when event reaches AGENDA_FINALIZED
- Due: 14 days before event
- Purpose: Send final details, logistics, full schedule
- Recipients: Registered attendees, speakers, partners
- **Note**: Works with auto-publishing feature (agenda auto-publishes @ 14 days)

### How Newsletters Work with Tasks

### Newsletter Types

**Pre-Event Countdown** (3 days before):
```
Subject: 3 Days Until BATbern 2025! 🎉

Dear [Attendee Name],

BATbern 2025 is this Saturday! Here's what you need to know:

📅 Date: March 15, 2025
⏰ Time: 9:00 AM - 6:00 PM
📍 Location: Kursaal Bern, Kornhausstrasse 3

What to Bring:
✅ Your registration QR code (attached)
✅ Business cards for networking
✅ Notebook for sessions

Top Sessions:
• 09:00 - Hans Müller: Sustainable Materials
• 10:00 - Anna Schmidt: Digital Transformation
• 11:00 - Peter Weber: Urban Planning

[View Full Schedule]

Parking & Transportation:
- Public transit: Bus #12 to "Kursaal"
- Parking: Kursaal parking garage (CHF 2/hour)

See you Saturday!
BATbern Team

[Download Event App] [View Schedule] [Get Directions]
```

**Day-Before Reminder** (1 day before):
```
Subject: Tomorrow: BATbern 2025! Final Details

Hi [Name],

Just 24 hours away! Quick reminders:

📱 Download the BATbern app for live updates
🍽️ Lunch provided (dietary preferences noted)
🤝 Networking reception: 5:00-6:00 PM

Weather Forecast: Sunny, 18°C
Dress Code: Business casual

Questions? Call our hotline: +41 31 123 4567

Ready to learn!
BATbern Team
```

**Day-Of Welcome** (event morning):
```
Subject: Good Morning! BATbern 2025 Starts at 9:00

Good morning!

Doors open at 8:30 AM for registration and coffee.

Opening session starts promptly at 9:00 AM.

Live schedule updates: [App Link]

Have a great day of learning!
BATbern Team
```

**Post-Event Thank You** (1 day after):
```
Subject: Thank You for Attending BATbern 2025!

Dear [Name],

Thank you for joining us at BATbern 2025!

We hope you enjoyed the sessions and connected with fellow
architects.

Help Us Improve:
[Take 5-Minute Survey] (Deadline: March 22)

Event Highlights:
• 300+ attendees
• 12 expert speakers
• 15+ hours of networking

Session Recordings:
Available March 20 at: [Link]

Stay Connected:
- LinkedIn: BATbern Professional Network
- Newsletter: Monthly architecture insights
- Save the Date: BATbern 2026 - March 14, 2026

Thank you and see you next year!
BATbern Team

[Survey] [Recordings] [Photos]
```

### How to Complete

<div class="step" data-step="1">

**Prepare Newsletter Templates**

Review and customize pre-built templates:

```
Newsletter Manager - BATbern 2025
────────────────────────────────────────
Templates:
✅ Pre-Event Countdown (ready)
✅ Day-Before Reminder (ready)
✅ Day-Of Welcome (ready)
✅ Post-Event Thank You (draft)

[Customize Templates]
```
</div>

<div class="step" data-step="2">

**Segment Recipients**

Create targeted distribution lists:

```
Recipient Lists
────────────────────────────────────────
Attendees: 287 (all registrations)
Speakers: 12 (confirmed speakers)
Partners: 8 (Diamond, Platinum, Gold)
Organizers: 5 (internal team)
Waitlist: 23 (not registered)

[Manage Lists]
```
</div>

<div class="step" data-step="3">

**Schedule Delivery**

Set automated send times:

```
Scheduled Newsletters
────────────────────────────────────────
✅ Pre-Event (March 12, 10:00 AM)
   → 287 attendees, 12 speakers, 8 partners

✅ Day-Before (March 14, 6:00 PM)
   → 287 attendees, 12 speakers

✅ Day-Of (March 15, 7:00 AM)
   → 287 attendees

⏸️ Post-Event (March 16, 2:00 PM)
   → 287 attendees, 12 speakers, 8 partners
   [Schedule after event completion]

[Edit Schedule]
```
</div>

<div class="step" data-step="4">

**Send and Track**

Monitor newsletter performance:

```
Newsletter Metrics
────────────────────────────────────────
Pre-Event Countdown:
Sent: 307 | Delivered: 305 | Opened: 267 (87%)
Clicked: 198 (65%)

Top Links:
- View Schedule: 145 clicks
- Get Directions: 89 clicks
- Download App: 67 clicks

[View Full Report]
```
</div>

## Step 14: Moderator Assignment

<span class="feature-status implemented">Implemented via Task System</span>

### Auto-Created Task

**Moderator Assignment** task auto-creates when event reaches TOPIC_SELECTION state:
- Due: 14 days before event
- Trigger state: TOPIC_SELECTION
- Purpose: Assign moderators to all sessions

### Purpose

Assign session moderators to introduce speakers, manage Q&A, and keep sessions on schedule.

### Acceptance Criteria

- ✅ All sessions have assigned moderators
- ✅ Moderators briefed on responsibilities
- ✅ Backup moderators identified

### Moderator Responsibilities

**Pre-Session** (10 minutes before):
- Greet speaker, confirm tech setup
- Review session timing and Q&A format
- Test microphones and clickers

**Introduction** (2 minutes):
- Introduce speaker with bio
- State session topic and learning objectives
- Mention Q&A format

**During Session**:
- Keep time (signal at 5 minutes remaining)
- Monitor audience engagement
- Prepare Q&A questions if needed

**Q&A Facilitation** (10-15 minutes):
- Invite audience questions
- Repeat questions for recording
- Manage time, cut off politely if needed

**Wrap-Up** (1 minute):
- Thank speaker
- Remind attendees of next session
- Direct to networking areas

### How to Complete

<div class="step" data-step="1">

**Identify Moderators**

Select moderators from organizers, partners, or experienced attendees:

```
Moderator Pool
────────────────────────────────────────
Available: 8 people

Anna Schmidt (Organizer) - Experienced
Peter Meier (Partner) - New moderator
Lisa Weber (Past speaker) - Experienced
... (5 more)

Sessions Needing Moderators: 12
Moderators Needed: 6 (2 sessions each)

[Assign Moderators]
```
</div>

<div class="step" data-step="2">

**Assign to Sessions**

Match moderators to sessions:

```
Session Assignments
────────────────────────────────────────
09:00-09:45 Track A: Sustainable Materials
Speaker: Hans Müller
Moderator: [▼ Anna Schmidt] ✅

09:00-09:45 Track B: Digital Transformation
Speaker: Anna Schmidt
Moderator: [▼ Peter Meier] ✅

... (10 more sessions)

[Save Assignments]
```
</div>

<div class="step" data-step="3">

**Send Moderator Brief**

Email moderators with instructions:

```
To: anna.schmidt@batbern.ch
Subject: Moderator Assignment - BATbern 2025

Hi Anna,

You're moderating 2 sessions at BATbern 2025:

Session 1: Sustainable Materials (09:00-09:45 Track A)
Speaker: Hans Müller
Room: Kursaal Hall A

Session 2: Heritage Reuse (13:00-13:45 Track B)
Speaker: Martin Fischer
Room: Kursaal Hall B

Moderator Guide: [PDF Link]

Key Points:
- Arrive 10 minutes early
- 2-minute introduction
- Keep time (45 min total: 30 talk + 15 Q&A)
- Facilitate Q&A

Questions? Contact: events@batbern.ch

Thank you!
BATbern Team
```
</div>

## Step 15: Catering Coordination

<span class="feature-status implemented">Implemented via Task System</span>

### Auto-Created Task

**Catering** task auto-creates when event reaches AGENDA_FINALIZED state:
- Due: 30 days before event
- Trigger state: AGENDA_FINALIZED
- Purpose: Coordinate catering with vendor

### Purpose

Coordinate meal service, dietary accommodations, and refreshment schedules.

### Acceptance Criteria

- ✅ Catering vendor confirmed
- ✅ Headcount finalized
- ✅ Dietary restrictions accommodated
- ✅ Service schedule aligned with agenda

### Catering Requirements

**Full-Day Event**:
- Morning coffee (8:30-9:00, before opening)
- Coffee break (10:45-11:00)
- Lunch buffet (12:00-13:00)
- Afternoon coffee (15:00-15:15)
- Networking reception (17:00-18:00)

**Dietary Accommodations**:
- Vegetarian: 45 attendees
- Vegan: 18 attendees
- Gluten-free: 12 attendees
- Nut allergy: 3 attendees
- Halal: 7 attendees

### How to Complete

<div class="step" data-step="1">

**Finalize Headcount**

Confirm numbers with caterer (3 days before):

```
Final Headcount - BATbern 2025
────────────────────────────────────────
Registered: 287 attendees
Speakers: 12
Partners: 15 (booth staff)
Organizers: 5
Total: 319

Buffer (+10%): 351 portions

[Confirm with Caterer]
```
</div>

<div class="step" data-step="2">

**Review Menu**

Ensure dietary needs met:

```
Menu Review
────────────────────────────────────────
Lunch Buffet:
- Grilled chicken (200 portions)
- Vegetarian lasagna (60 portions)
- Vegan curry (30 portions)
- Gluten-free pasta (20 portions)
- Halal beef (15 portions)

Sides:
- Mixed salads (all dietary options)
- Fresh fruit
- Bread rolls (gluten-free available)

Beverages:
- Coffee, tea, water (unlimited)
- Juices (apple, orange)

[Approve Menu]
```
</div>

<div class="step" data-step="3">

**Coordinate Timing**

Align service with event schedule:

```
Catering Schedule
────────────────────────────────────────
08:30  Setup morning coffee station
09:00  Open coffee station (self-service)
10:45  Refresh coffee, add pastries
12:00  Serve lunch buffet (Hall C)
13:00  Clear lunch, setup afternoon coffee
15:00  Open afternoon coffee station
17:00  Setup networking reception
18:00  End service, cleanup begins

[Share Schedule with Vendor]
```
</div>

## Custom Tasks

<span class="feature-status implemented">Implemented</span>

### Purpose

Organizers can create custom tasks for organization-specific needs beyond the 7 default system tasks.

### Creating Custom Tasks

<div class="step" data-step="1">

**Navigate to Task Board**

Click Task Board in the sidebar.

</div>

<div class="step" data-step="2">

**Create Custom Task**

Click **+ Create Custom Task**

Fill in task details:

```
Task Creation Form
────────────────────────────────────────
Task Name *
[Photographer Booking]

Event *
[▼ BATbern 2025]

Trigger State *
[▼ TOPIC_SELECTION]
When event reaches this state, task will be created

Due Date *
○ Immediately when created
○ Relative to event date: [30] days before
● Absolute date: [2025-02-15]

Assigned To
[▼ Anna Schmidt (Organizer)]

Description (optional)
[Book professional photographer for event day.
Budget: CHF 1,500 for full-day coverage.]

[Create Task] [Cancel]
```

</div>

<div class="step" data-step="3">

**Manage Task**

Task appears in Task Board when event reaches trigger state:

- Drag between TODO → IN_PROGRESS → COMPLETED columns
- Add completion notes when marking COMPLETED
- Reassign to different organizer if needed
- Update due date if timeline changes

</div>

### Task Management Best Practices

**Assign Ownership**:
- Every task should have a specific assignee
- Don't leave tasks unassigned (work falls through cracks)
- Reassign if original assignee unavailable

**Set Realistic Due Dates**:
- Use relative dates ("14 days before event") for flexibility
- Add buffer for unexpected delays
- Adjust if event date changes

**Track Progress**:
- Update task state as work progresses (don't leave in TODO if started)
- Add completion notes for historical reference
- Mark CANCELLED if task no longer needed

**Review Daily**:
- Check "My Tasks" filter daily during active planning
- Sort by due date to prioritize urgent items
- Address overdue tasks immediately

## Phase F Completion

### Success Criteria

- ✅ All auto-created tasks addressed (7 default tasks)
- ✅ Custom tasks created as needed
- ✅ All tasks assigned to organizers
- ✅ Overdue tasks completed or rescheduled
- ✅ Task board reflects current work status

### Task System Benefits

**Clear Ownership**:
- Each task has specific assignee
- No confusion about who's responsible
- Easy to track individual workloads

**Automated Creation**:
- Tasks auto-create at right workflow moments
- No manual checklist needed
- Never forget critical steps

**Flexible Due Dates**:
- Relative dates adjust if event date changes
- Absolute dates for fixed deadlines
- Immediate tasks for urgent work

**Historical Record**:
- Completion notes document what was done
- Track time spent on different task types
- Inform planning for future events

### What Happens Next

**Continue Event Execution**:
- Monitor task board for upcoming deadlines
- Complete tasks as event approaches
- Event automatically transitions through lifecycle (AGENDA_FINALIZED → EVENT_LIVE → EVENT_COMPLETED)
- Archive event after completion (see Phase E)

**For Task Management**: This is an ongoing process throughout the event lifecycle, not a single phase.

## Troubleshooting Task System

### "Tasks not auto-creating"

**Problem**: Event transitioned to TOPIC_SELECTION but no tasks created.

**Solution**:
- Verify 7 default task templates exist in system
- Check task templates have correct trigger_state configured
- Review application event logs for task creation errors
- Manually create missing tasks as workaround

### "Task overdue but can't complete"

**Problem**: Task deadline passed but work blocked.

**Solution**:
- Update due date to new realistic deadline
- Add notes explaining delay
- Reassign to different organizer if needed
- Mark CANCELLED if task no longer needed (e.g., event cancelled)

### "Too many tasks for one organizer"

**Problem**: One person assigned to most tasks, creating bottleneck.

**Solution**:
- Review task board by assignee
- Reassign tasks to balance workload
- Create custom tasks to break large tasks into smaller pieces
- Assign to different team members based on expertise

## Related Topics

- [Workflow Overview →](README.md) - Understanding the 3 workflow systems
- [Phase A: Setup →](phase-a-setup.md) - Where tasks first auto-create
- [Phase E: Archival →](phase-e-publishing.md) - Event lifecycle completion
- [Event Management →](../entity-management/events.md) - Event configuration

## API Reference

```
GET    /api/tasks                           List all tasks (filterable by event, assignee, status)
POST   /api/tasks                           Create custom task
GET    /api/tasks/{id}                      Get task details
PUT    /api/tasks/{id}                      Update task (status, assignee, due date)
PUT    /api/tasks/{id}/status               Update task status (TODO/IN_PROGRESS/COMPLETED/CANCELLED)
DELETE /api/tasks/{id}                      Delete custom task
GET    /api/tasks/templates                 Get default task templates (7 system tasks)
POST   /api/tasks/templates                 Create custom task template
```

See [API Documentation](../../api/) for complete specifications.
