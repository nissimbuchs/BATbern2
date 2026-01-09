# BATbern Workflow System

> Event lifecycle management through state machines and task coordination

## 🎥 Video Tutorial

**Complete Workflow Demonstration** (12 minutes, German with subtitles):

📹 **[Event-Workflow Schulungsvideo (Deutsch)](../assets/videos/workflow/event-workflow-schulung-de.mp4)**

This screencast demonstrates the complete event lifecycle from creation to archival, showing all workflow phases in action:

- Phase A: Event setup, topic selection, speaker brainstorming
- Phase B: Speaker outreach with Kanban board
- Phase C: Quality review and content approval
- Phase D: Slot assignment and agenda publishing
- Phase E: Event archival

**Features**: Full HD (1920x1080), German narration with toggleable subtitles, 36 workflow steps demonstrated in real-time.

---

## Overview

BATbern uses **three independent workflow systems** to manage event planning and execution:

1. **Event Workflow** - 9-state lifecycle for high-level event progression
2. **Speaker Workflow** - Per-speaker state management with parallel progression
3. **Task System** - Configurable assignable tasks (newsletters, catering, etc.)

**Key Insight**: The original "16-step linear workflow" was a misconception. The actual implementation uses state machines with flexible parallel progression and separate task management.

---

## 1. Event Workflow (9 States)

The **Event Workflow** tracks the high-level lifecycle of each event from creation to archival.

### State Progression

```
CREATED → TOPIC_SELECTION → SPEAKER_IDENTIFICATION → SLOT_ASSIGNMENT →
AGENDA_PUBLISHED → AGENDA_FINALIZED → EVENT_LIVE → EVENT_COMPLETED → ARCHIVED
```

### State Definitions

| State                      | Description                             | When Reached                      | Exit Condition                       |
| -------------------------- | --------------------------------------- | --------------------------------- | ------------------------------------ |
| **CREATED**                | Event created, ready for setup          | Event creation form submitted     | Topic selected                       |
| **TOPIC_SELECTION**        | Topics selected, ready for speakers     | Minimum 1 topic selected          | Minimum speakers in pool             |
| **SPEAKER_IDENTIFICATION** | Building speaker pool, outreach ongoing | Min speaker candidates identified | All slots filled                     |
| **SLOT_ASSIGNMENT**        | Assigning speakers to time slots        | All confirmed speakers assigned   | Agenda published                     |
| **AGENDA_PUBLISHED**       | Public agenda, accepting registrations  | Publish agenda action             | Manual finalization (2 weeks before) |
| **AGENDA_FINALIZED**       | Agenda locked for printing              | Finalize agenda action            | Event day arrives                    |
| **EVENT_LIVE**             | Event currently happening               | Event day                         | Manual transition after event        |
| **EVENT_COMPLETED**        | Event finished, post-processing         | Post-event trigger                | Manual archival                      |
| **ARCHIVED**               | Event archived for history              | Archival action                   | Terminal state                       |

### Workflow Phases (User Guide Organization)

For documentation purposes, we organize the 9 states into user-friendly phases:

**Phase A: Setup** <span class="feature-status implemented">Implemented</span>

- States: CREATED → TOPIC_SELECTION → SPEAKER_IDENTIFICATION
- Actions: Create event, select topics, brainstorm speaker candidates
- [Learn more →](phase-a-setup.md)

**Phase B: Outreach** <span class="feature-status implemented">Implemented</span>

- States: SPEAKER_IDENTIFICATION (speakers moving through their own workflow)
- Actions: Contact speakers, track responses, collect content submissions
- [Learn more →](phase-b-outreach.md)

**Phase C: Quality** <span class="feature-status implemented">Implemented</span>

- States: SPEAKER_IDENTIFICATION (quality review happening in speaker workflow)
- Actions: Review submitted content, approve/request revisions
- [Learn more →](phase-c-quality.md)

**Phase D: Assignment** <span class="feature-status implemented">Implemented</span>

- States: SLOT_ASSIGNMENT → AGENDA_PUBLISHED
- Actions: Assign presentations to time slots, publish agenda
- [Learn more →](phase-d-assignment.md)

**Phase E: Archival** <span class="feature-status implemented">Implemented</span>

- States: Any state → ARCHIVED
- Actions: Archive completed event, preserve historical data
- [Learn more →](phase-e-publishing.md)

**Phase F: Communication** <span class="feature-status planned">Planned</span>

- States: Tasks triggered at various event states
- Actions: Send newsletters, assign moderators, coordinate logistics
- [Learn more →](phase-f-communication.md)

---

## 2. Speaker Workflow (Per-Speaker State Machine)

**Critical Concept**: Each speaker progresses through their own workflow **independently and in parallel**. Quality review and slot assignment can happen in any order.

### State Progression

```
identified → contacted → ready → accepted/declined
                                    ↓ (if accepted)
                                content_submitted
                                    ↓
                                quality_reviewed
                                    ↓
                                confirmed
                    (auto-confirmed when quality_reviewed AND session.startTime exists)

Special states:
- overflow (backup speaker - accepted but no slots)
- withdrew (speaker drops out after accepting)
```

### State Definitions

| State                 | Description                     | How to Reach                                          |
| --------------------- | ------------------------------- | ----------------------------------------------------- |
| **identified**        | Added to speaker pool           | Brainstormed in Phase A                               |
| **contacted**         | Organizer recorded outreach     | Mark as contacted in Kanban board                     |
| **ready**             | Speaker ready to accept/decline | Speaker receives invitation                           |
| **accepted**          | Speaker accepted invitation     | Speaker accepts or organizer marks accepted           |
| **declined**          | Speaker declined invitation     | Speaker declines or organizer marks declined          |
| **content_submitted** | Title/abstract submitted        | Speaker submits via content form                      |
| **quality_reviewed**  | Content approved by organizer   | Organizer approves in quality review drawer           |
| **confirmed**         | Ready for publication           | Auto-set when quality_reviewed AND session has timing |
| **overflow**          | Backup (no slot available)      | Accepted when all slots filled                        |
| **withdrew**          | Dropped out after accepting     | Speaker cancels after acceptance                      |

### Parallel Workflow Feature

**Quality review and slot assignment are independent:**

- Scenario 1: Quality review first → slot assigned later → auto-confirms when slot assigned
- Scenario 2: Slot assigned first → quality review later → auto-confirms when quality approved
- Order doesn't matter: Confirmation happens when BOTH complete

**Data Storage:**

- **speaker_pool table**: Tracks speaker workflow state
- **sessions table**: Stores presentation details and timing (startTime, endTime, room)
- **session_users table**: Links speakers to sessions
- Session timing (startTime exists) triggers auto-confirmation check

---

## 3. Task System

**Key Principle**: Tasks are NOT workflow states. They are assignable work items with due dates that organizers complete during event planning.

### Task Types

**Default System Tasks (7):**

1. **Venue Booking** - Triggered: TOPIC_SELECTION, Due: 90 days before event
2. **Partner Meeting** - Triggered: TOPIC_SELECTION, Due: event day
3. **Moderator Assignment** - Triggered: TOPIC_SELECTION, Due: 14 days before event
4. **Newsletter: Topic** - Triggered: TOPIC_SELECTION, Due: immediately
5. **Newsletter: Speakers** - Triggered: AGENDA_PUBLISHED, Due: 30 days before event
6. **Newsletter: Final** - Triggered: AGENDA_FINALIZED, Due: 14 days before event
7. **Catering** - Triggered: AGENDA_FINALIZED, Due: 30 days before event

**Custom Tasks:**
Organizers can create custom tasks with:

- Custom task name
- Trigger state (which event state creates the task)
- Due date (immediate, relative to event date, or absolute date)
- Assigned organizer

### Task Dashboard

Tasks appear in the task list with three statuses:

- **TODO**: Not started (overdue highlighted in red)
- **IN_PROGRESS**: Currently working on
- **COMPLETED**: Finished with completion notes

### Task Auto-Creation

Tasks are automatically created when the event transitions to their trigger state:

- Event reaches TOPIC_SELECTION → creates Venue Booking, Partner Meeting, Moderator Assignment, Newsletter: Topic tasks
- Event reaches AGENDA_PUBLISHED → creates Newsletter: Speakers task
- Event reaches AGENDA_FINALIZED → creates Newsletter: Final, Catering tasks

---

## Workflow Architecture Benefits

### Clear Separation of Concerns

**Event State**: High-level event lifecycle progression

- Example: "Where is the event in its planning lifecycle?"
- Answer: TOPIC_SELECTION, AGENDA_PUBLISHED, etc.

**Speaker State**: Individual speaker progress

- Example: "Is this speaker ready to present?"
- Answer: Each speaker has their own state (accepted, quality_reviewed, confirmed, etc.)

**Tasks**: Actionable work items

- Example: "What do I need to do today?"
- Answer: Task list shows assigned tasks with due dates

### Parallel Progression

**Event progresses while speakers progress independently:**

- Event can be in SPEAKER_IDENTIFICATION state
- Speaker A is "identified", Speaker B is "contacted", Speaker C is "content_submitted"
- All happening simultaneously

**Quality review and slot assignment are flexible:**

- No rigid order - whichever completes first
- Auto-confirmation when both complete
- Supports real-world workflow variations

### Task Flexibility

**Tasks are triggered by events but managed separately:**

- Newsletter can be drafted before event reaches AGENDA_PUBLISHED
- Moderator assignment doesn't block event progression
- Custom tasks for organization-specific needs

---

## How to Use the Workflow System

### Starting a New Event

1. **Create Event** (Entity Management → Events)
   - Event state: CREATED
   - No tasks yet

2. **Select Topics** (Phase A → Step 2)
   - Event state: CREATED → TOPIC_SELECTION
   - Auto-creates: Venue Booking, Partner Meeting, Moderator Assignment, Newsletter: Topic tasks

3. **Identify Speakers** (Phase A → Step 3)
   - Event state: TOPIC_SELECTION → SPEAKER_IDENTIFICATION
   - Speakers created in "identified" state

### Managing Speaker Outreach

4. **Contact Speakers** (Phase B)
   - Event state: Still SPEAKER_IDENTIFICATION
   - Update speaker states individually: identified → contacted → accepted
   - Some speakers at "contacted", others at "accepted", others still "identified"

5. **Collect Content** (Phase B)
   - Event state: Still SPEAKER_IDENTIFICATION
   - Speakers submit content: accepted → content_submitted

### Quality and Assignment

6. **Review Content** (Phase C)
   - Event state: Still SPEAKER_IDENTIFICATION
   - Review each speaker: content_submitted → quality_reviewed
   - Can happen before OR after slot assignment

7. **Assign Slots** (Phase D)
   - Event state: SPEAKER_IDENTIFICATION → SLOT_ASSIGNMENT
   - Assign presentations to time slots (sets session.startTime)
   - Speakers auto-confirm when quality_reviewed AND session.startTime exists

### Publishing and Execution

8. **Publish Agenda** (Phase D)
   - Event state: SLOT_ASSIGNMENT → AGENDA_PUBLISHED
   - Auto-creates: Newsletter: Speakers task
   - Public agenda visible to attendees

9. **Finalize Agenda** (Phase E)
   - Event state: AGENDA_PUBLISHED → AGENDA_FINALIZED
   - Auto-creates: Newsletter: Final, Catering tasks
   - Agenda locked for printing

10. **Archive Event** (Phase E)
    - Event state: Any state → ARCHIVED
    - Historical data preserved
    - Event removed from active workflows

### Managing Tasks

**View Tasks**: Navigate to Task List

- Filter by status (TODO, IN_PROGRESS, COMPLETED)
- Filter by assigned organizer ("My Tasks" / "All Tasks")
- Sort by due date (overdue highlighted)

**Complete Task**:

- Click task → Update status to IN_PROGRESS
- Add completion notes
- Mark as COMPLETED when done

**Create Custom Task**:

- Click "Create Task"
- Enter task name, trigger state, due date
- Assign to organizer
- Save

---

## Best Practices

### Event Workflow

**Don't Rush State Transitions**:

- Each state has entry/exit criteria
- Ensure criteria met before advancing
- System validates transitions automatically

**Use Override Sparingly**:

- Workflow validation can be overridden for special cases
- Only use for cancelled events or exceptional circumstances
- Document reason in event notes

### Speaker Workflow

**Update States Promptly**:

- Mark speakers as "contacted" immediately after outreach
- Update to "accepted"/"declined" as responses come in
- Keeps Kanban board accurate

**Parallel Quality Review and Slot Assignment**:

- Review content as soon as submitted (don't wait for all speakers)
- Assign slots whenever ready (don't wait for all quality reviews)
- System auto-confirms when both complete

**Handle Dropouts Quickly**:

- Mark speaker as "withdrew" immediately
- Promote overflow speaker if available
- Update published agenda promptly

### Task Management

**Assign Tasks Early**:

- Assign tasks to specific organizers when created
- Clear ownership prevents work falling through cracks

**Set Realistic Due Dates**:

- Use relative due dates (e.g., "14 days before event")
- Adjust dates if timeline changes
- Add buffer for unexpected delays

**Track Progress**:

- Review task list daily during active planning
- Update status as work progresses
- Add completion notes for historical reference

---

## Workflow States Reference

### Complete Event State List

1. **CREATED** - Event created
2. **TOPIC_SELECTION** - Topics selected
3. **SPEAKER_IDENTIFICATION** - Building speaker pool
4. **SLOT_ASSIGNMENT** - Assigning to time slots
5. **AGENDA_PUBLISHED** - Public agenda live
6. **AGENDA_FINALIZED** - Agenda locked
7. **EVENT_LIVE** - Event happening now
8. **EVENT_COMPLETED** - Event finished
9. **ARCHIVED** - Historical record

### Complete Speaker State List

1. **identified** - In speaker pool
2. **contacted** - Outreach recorded
3. **ready** - Ready to accept/decline
4. **accepted** - Committed to presenting
5. **declined** - Not available
6. **content_submitted** - Content received
7. **quality_reviewed** - Content approved
8. **confirmed** - Ready for publication
9. **overflow** - Backup speaker
10. **withdrew** - Cancelled after accepting

---

## Troubleshooting

### "Event stuck in SPEAKER_IDENTIFICATION"

**Problem**: Can't advance to SLOT_ASSIGNMENT.

**Solution**:

- Check if minimum speakers are confirmed
- Verify all slots have confirmed speakers assigned
- System validates speaker count before allowing transition

### "Speaker not auto-confirming"

**Problem**: Speaker has quality_reviewed AND slot assigned but still not confirmed.

**Solution**:

- Check session.startTime is set (not just session created)
- Verify speaker status is exactly "quality_reviewed"
- Check speaker_pool.session_id links to correct session

### "Tasks not auto-creating"

**Problem**: Event transitioned to TOPIC_SELECTION but no tasks created.

**Solution**:

- Check task templates exist (7 default templates)
- Verify templates have correct trigger_state
- Review application event logs for errors

### "Can't archive event"

**Problem**: Workflow validation prevents archival.

**Solution**:

- Check override validation checkbox in edit modal
- Allows archival for cancelled events or special cases
- Documents that normal workflow wasn't completed

---

## Related Documentation

### Architecture

- [Workflow State Machines →](../../architecture/06a-workflow-state-machines.md) - Technical implementation details
- [Backend Architecture →](../../architecture/06-backend-architecture.md) - Overall system design

### User Guide

- [Phase A: Setup →](phase-a-setup.md) - Event creation and configuration
- [Phase B: Outreach →](phase-b-outreach.md) - Speaker engagement
- [Phase C: Quality →](phase-c-quality.md) - Content review
- [Phase D: Assignment →](phase-d-assignment.md) - Slot assignment and publishing
- [Phase E: Archival →](phase-e-publishing.md) - Event archival

### Entity Management

- [Event Management →](../entity-management/events.md) - Create and configure events
- [Speaker Management →](../entity-management/speakers.md) - Speaker profiles and status
- [Task Management →](../entity-management/tasks.md) - Task assignment and tracking

---

## What's Next?

**Choose your starting point:**

1. **New Event**: Start with [Phase A: Setup →](phase-a-setup.md)
2. **Speaker Outreach**: Continue with [Phase B: Outreach →](phase-b-outreach.md)
3. **Content Review**: Proceed to [Phase C: Quality →](phase-c-quality.md)
4. **Publishing**: Move to [Phase D: Assignment →](phase-d-assignment.md)
5. **Archival**: Complete with [Phase E: Archival →](phase-e-publishing.md)
