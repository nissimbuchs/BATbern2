# BATbern Workflow System

> Event lifecycle management through state machines and task coordination

> **Last Updated:** 2026-06-13 — Speaker workflow aligned to the unified 8-state model (ADR-009 / Epic 11). The legacy lowercase free-form speaker states (`overflow`, `withdrew`, `confirmed`, `slot_assigned`, `tentative`) are removed.

## 🎥 Video Tutorials

**Complete Workflow Demonstration** (12 minutes each):

📹 **[German Version - Event-Workflow Schulungsvideo](/assets/user-guide/assets/videos/workflow/event-workflow-schulung-de.mp4)**

📹 **[English Version - Event Workflow Training Video](/assets/user-guide/assets/videos/workflow/event-workflow-schulung-en.mp4)**

These screencasts demonstrate the complete event lifecycle from creation to archival, showing all workflow phases in action:

- Phase A: Event setup, topic selection, speaker brainstorming
- Phase B: Speaker outreach with Kanban board
- Phase C: Quality review and content approval
- Phase D: Slot assignment and agenda publishing
- Phase E: Auto-publishing, lifecycle automation, archival

**Features**: Full HD (1920x1080), professional narration in both languages, dual subtitle tracks (German + English), 36 workflow steps demonstrated in real-time.

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

> **Note**: TOPIC_SELECTION is not a mandatory stop from CREATED. When a topic is confirmed from CREATED state, the event transitions directly to **SPEAKER_IDENTIFICATION**. CREATED → SPEAKER_IDENTIFICATION is also a valid direct transition (e.g., when re-creating an event with a pre-known topic).

### State Definitions

| State                      | Description                             | When Reached                      | Exit Condition                       |
| -------------------------- | --------------------------------------- | --------------------------------- | ------------------------------------ |
| **CREATED**                | Event created, ready for setup          | Event creation form submitted     | Topic selected (→ TOPIC_SELECTION or directly to SPEAKER_IDENTIFICATION) |
| **TOPIC_SELECTION**        | Topics selected, ready for speakers     | Minimum 1 topic selected          | Minimum speakers in pool             |
| **SPEAKER_IDENTIFICATION** | Building speaker pool, outreach ongoing | Min speaker candidates identified | All slots filled                     |
| **SLOT_ASSIGNMENT**        | Assigning speakers to time slots        | Publishable speakers assigned     | Agenda published                     |
| **AGENDA_PUBLISHED**       | Public agenda, accepting registrations  | Publish agenda action             | Manual finalization (2 weeks before) |
| **AGENDA_FINALIZED**       | Agenda locked for printing              | Finalize agenda action            | Event day arrives                    |
| **EVENT_LIVE**             | Event currently happening               | Automatic when event start time passed (hourly check) | Automatic when event end time passed |
| **EVENT_COMPLETED**        | Event finished, post-processing         | All completeable sessions ended via Watch mode (organizer calls endSession() for every keynote/presentation/workshop/panel) | Manual archival |
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
- UX: redesigned **kanban board** (guided drag with valid-transition highlighting, a unified speaker drawer, primary-action buttons, and time-in-state colour coding) per Story 11.D.x and the speaker-drawer redesign (Story 10-30)
- [Learn more →](phase-b-outreach.md)

**Phase C: Quality** <span class="feature-status implemented">Implemented</span>

- States: SPEAKER_IDENTIFICATION (quality review happening in speaker workflow)
- Actions: Review submitted content, approve/request revisions
- [Learn more →](phase-c-quality.md)

**Phase D: Assignment** <span class="feature-status implemented">Implemented</span>

- States: SLOT_ASSIGNMENT → AGENDA_PUBLISHED
- Actions: Assign presentations to time slots, publish agenda
- [Learn more →](phase-d-assignment.md)

**Phase E: Publishing & Lifecycle** <span class="feature-status implemented">Implemented</span>

- States: AGENDA_FINALIZED → EVENT_LIVE → EVENT_COMPLETED → ARCHIVED
- Actions: Auto-publish speakers (30 days before) and agenda (14 days before) via CloudFront CDN; automated EVENT_LIVE and EVENT_COMPLETED transitions; manual archival
- [Learn more →](phase-e-publishing.md)

**Phase F: Communication** <span class="feature-status implemented">Implemented</span>

- States: Tasks triggered at various event states
- Actions: Send newsletters, assign moderators, coordinate logistics, auto-publishing
- [Learn more →](phase-f-communication.md)

---

## 2. Speaker Workflow (8-State Machine — ADR-009)

**Critical Concept**: Each speaker progresses through their own workflow **independently and in parallel**. Quality review and slot assignment can happen in any order.

Per **ADR-009 (Unified Speaker Workflow)** delivered in **Epic 11**, the speaker workflow is a single **8-state** machine. States are stored UPPER_CASE in code/JSON (lowercase_snake_case in the database). `SpeakerWorkflowService` is the **sole writer** of speaker status — every other service delegates to it.

### State Progression

```
IDENTIFIED → CONTACTED → READY → INVITED → ACCEPTED → CONTENT_SUBMITTED → QUALITY_REVIEWED
     │           │         │        │          │              │                  │
     └───────────┴─────────┴────────┴──────────┴──────────────┴──────────────────┴──→ DECLINED
                       (DECLINED is reachable from every non-terminal state)
```

- **CONTACTED → READY** is the **provisioning gate** — promoting a speaker to `READY` looks up or creates the User, grants the SPEAKER role, and provisions the AWS Cognito account. This step is **organizer-only**, via the promote action (`POST /api/v1/events/{code}/speakers/{speakerId}/promote`).
- **READY → INVITED** sends the formal invitation email (login link + temporary password). It is blocked by a **slot-capacity gate**: `READY → INVITED` is rejected when `count(ACCEPTED) + count(INVITED) >= max_slots` (this replaces the removed `overflow` parking state).
- **DECLINED** is the single terminal "not happening" state, reachable from any non-terminal state. It covers a lead that didn't pan out, a refusal to an invitation, and a speaker who accepted then dropped out (the previous state and reason are recorded in `speaker_status_history`). This replaces the removed `withdrew` state.

### State Definitions

| State                 | Description                                                                                          | How to Reach                                                       |
| --------------------- | ---------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------ |
| **IDENTIFIED**        | Name on the brainstorm list (candidate, lead, or contact). No User, no Cognito account yet.          | Brainstormed in Phase A, or dropped in via attendee self-nomination (Epic 7.2) |
| **CONTACTED**         | Still brainstorming — organizer is reaching out to figure out who will actually speak. No User yet.  | Mark as contacted on the Kanban board                              |
| **READY**             | Provisioning gate — the real speaker is chosen; User + SPEAKER role + Cognito account provisioned.   | Organizer **promotes** the candidate (organizer-only)              |
| **INVITED**           | Formal invitation email sent (login link + temporary password). Speaker can authenticate via Cognito. | Organizer sends invitation (subject to slot-capacity gate)         |
| **ACCEPTED**          | Speaker committed via the portal.                                                                    | Speaker accepts, or organizer records acceptance                   |
| **CONTENT_SUBMITTED** | Title + abstract submitted to `content_submissions`.                                                 | Speaker submits via portal, or organizer submits on their behalf   |
| **QUALITY_REVIEWED**  | Moderator approved content. Happy end-state of the content lifecycle.                                | Organizer approves in the quality review drawer                    |
| **DECLINED**          | Terminal "not happening" state, reachable from any non-terminal state; reason recorded.              | Speaker declines, or organizer records a decline/dropout           |

### Derived Flags (computed at read time — not persisted)

The removed `confirmed` and `slot_assigned` states are now **derived flags**, computed on read:

- **`is_slot_assigned`** := `session.start_time IS NOT NULL` — true once the speaker's session has a time slot.
- **`is_publishable`** := `QUALITY_REVIEWED AND is_slot_assigned` — true when the speaker is content-approved **and** scheduled. This replaces the old `confirmed` state and is the gate the event workflow checks before `AGENDA_PUBLISHED`.

### Parallel Workflow Feature

**Quality review and slot assignment are independent:**

- Scenario 1: Quality review first → slot assigned later → `is_publishable` becomes true once the slot is assigned
- Scenario 2: Slot assigned first → quality review later → `is_publishable` becomes true once content is approved
- Order doesn't matter: a speaker is publishable when BOTH `QUALITY_REVIEWED` and `is_slot_assigned` hold

**Data Storage:**

- **speaker_pool table**: Tracks the speaker workflow state (`status`)
- **sessions table**: Stores presentation details and timing (startTime, endTime, room)
- **session_users table**: Links speakers to sessions; the `PRIMARY_SPEAKER` row carries the canonical username post-`READY`
- Session timing (`start_time` set) drives the derived `is_slot_assigned` / `is_publishable` flags

> **How Epic 7 attendee features touch this workflow:**
> - **Speaker self-nomination (Story 7.2)** — once an event's topic is set and published, a logged-in attendee can self-nominate ("I could speak on that"). This creates a `speaker_pool` entry at **`IDENTIFIED`** for the organizer to triage through the normal 8-state workflow. No Cognito account or SPEAKER role is created at nomination — provisioning still happens only at the organizer-driven `READY` promotion.
> - **Topics from the floor (Story 7.1)** — logged-in attendees suggest future topics into the existing topic-suggestion pool, tagged `source = community`. These feed Phase A topic selection (organizers triage them with a community badge); they do not enter the speaker workflow directly.

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

Tasks appear in the task list with four statuses:

- **TODO**: Not started (overdue highlighted in red; tasks due within 3 calendar days are highlighted as critical)
- **IN_PROGRESS**: Currently working on
- **COMPLETED**: Finished with completion notes
- **CANCELLED**: Not needed (event cancelled or task no longer relevant)

> **Internal note**: Tasks also have a **PENDING** pre-activation status that is not shown on the task dashboard. See [Task Auto-Creation](#task-auto-creation) below.

### Task Auto-Creation

Tasks use a **two-phase lifecycle**:

1. **Pre-created as `pending`** — All default task templates are silently pre-created with `status = "pending"` when the event is first created. They do not yet appear on the task dashboard.
2. **Activated to `todo`** — When the event transitions to a task's configured trigger state, the matching pending tasks are activated to `status = "todo"` and become visible in the task dashboard.

Activation happens at:

- Event reaches TOPIC_SELECTION → activates Venue Booking, Partner Meeting, Moderator Assignment, Newsletter: Topic tasks
- Event reaches AGENDA_PUBLISHED → activates Newsletter: Speakers task
- Event reaches AGENDA_FINALIZED → activates Newsletter: Final, Catering tasks

---

## Auto-Publishing & Lifecycle Automation

The platform automates two critical parts of the event lifecycle, removing the need for manual monitoring.

### Auto-Publishing Schedule

| Content | When | Gate Condition |
|---------|------|----------------|
| Speaker profiles | 30 days before event date | `currentPublishedPhase` has not yet reached "speakers" (speakers not yet published), regardless of event workflow state |
| Full agenda | 14 days before event date | `currentPublishedPhase = "speakers"` (speakers already published) **and** all sessions have timing; auto-publish transitions event to AGENDA_PUBLISHED |

Both run via a daily cron job (00:00 UTC). If the event has already passed the 30-day or 14-day mark without publishing, the job publishes on the next run (catch-up behaviour).

**Manual override**: Available from the event's Publishing tab — "Publish Speakers Now" / "Publish Agenda Now". Manual publish prevents duplicate auto-publishing (checked via `publishedAt` timestamps).

**CDN delivery**: Published content is served through **AWS CloudFront** — changes reach the public website within seconds of the publish job completing.

### Automatic State Transitions

| Transition | Trigger | Check Frequency |
|------------|---------|----------------|
| `AGENDA_FINALIZED` → `EVENT_LIVE` | Event start date/time reached | Hourly |
| `EVENT_LIVE` → `EVENT_COMPLETED` | Organizer ends all completeable sessions via Watch mode | On each endSession() call |

The `AGENDA_FINALIZED` → `EVENT_LIVE` transition requires no organizer action (hourly cron). The `EVENT_LIVE` → `EVENT_COMPLETED` transition is driven by the **Watch feature**: when an organizer calls `endSession()` for every completeable session (keynote, presentation, workshop, panel_discussion), the event automatically transitions to `EVENT_COMPLETED`. Break, lunch, and networking sessions are excluded from this check. The event then moves to `ARCHIVED` via a manual organizer action.

See [Phase E: Publishing & Lifecycle →](phase-e-publishing.md) for full details including dropout handling and CDN configuration.

---

## Workflow Architecture Benefits

### Clear Separation of Concerns

**Event State**: High-level event lifecycle progression

- Example: "Where is the event in its planning lifecycle?"
- Answer: TOPIC_SELECTION, AGENDA_PUBLISHED, etc.

**Speaker State**: Individual speaker progress

- Example: "Is this speaker ready to present?"
- Answer: Each speaker has their own state (`ACCEPTED`, `QUALITY_REVIEWED`, etc.) plus the derived `is_publishable` flag

**Tasks**: Actionable work items

- Example: "What do I need to do today?"
- Answer: Task list shows assigned tasks with due dates

### Parallel Progression

**Event progresses while speakers progress independently:**

- Event can be in SPEAKER_IDENTIFICATION state
- Speaker A is `IDENTIFIED`, Speaker B is `CONTACTED`, Speaker C is `CONTENT_SUBMITTED`
- All happening simultaneously

**Quality review and slot assignment are flexible:**

- No rigid order - whichever completes first
- A speaker becomes `is_publishable` when both `QUALITY_REVIEWED` and a slot assignment hold
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
   - Speakers created in `IDENTIFIED` state (also where attendee self-nominations land, per Epic 7.2)

### Managing Speaker Outreach

4. **Contact & Promote Speakers** (Phase B)
   - Event state: Still SPEAKER_IDENTIFICATION
   - Update speaker states individually: `IDENTIFIED → CONTACTED`, then **promote** the chosen speaker to `READY` (provisions Cognito), then `INVITED → ACCEPTED`
   - Some speakers at `CONTACTED`, others at `ACCEPTED`, others still `IDENTIFIED`

5. **Collect Content** (Phase B)
   - Event state: Still SPEAKER_IDENTIFICATION
   - Speakers submit content: `ACCEPTED → CONTENT_SUBMITTED`

### Quality and Assignment

6. **Review Content** (Phase C)
   - Event state: Still SPEAKER_IDENTIFICATION
   - Review each speaker: `CONTENT_SUBMITTED → QUALITY_REVIEWED`
   - Can happen before OR after slot assignment

7. **Assign Slots** (Phase D)
   - Event state: SPEAKER_IDENTIFICATION → SLOT_ASSIGNMENT
   - Assign presentations to time slots (sets session.startTime)
   - A speaker becomes `is_publishable` once `QUALITY_REVIEWED` AND `session.start_time` is set

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

- Mark speakers as `CONTACTED` immediately after outreach
- Update to `ACCEPTED` / `DECLINED` as responses come in
- Keeps Kanban board accurate

**Parallel Quality Review and Slot Assignment**:

- Review content as soon as submitted (don't wait for all speakers)
- Assign slots whenever ready (don't wait for all quality reviews)
- A speaker is publishable once both `QUALITY_REVIEWED` and a slot assignment hold

**Handle Dropouts Quickly**:

- Move the speaker to `DECLINED` immediately (the previous state and reason are recorded in `speaker_status_history`)
- Promote and invite a backup candidate — freeing a slot lets the slot-capacity gate admit another `READY → INVITED`
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

### Complete Speaker State List (8 states — ADR-009)

1. **IDENTIFIED** - On the brainstorm list; no User / Cognito account yet
2. **CONTACTED** - Outreach recorded; still brainstorming who will actually speak
3. **READY** - Provisioning gate: User + SPEAKER role + Cognito account provisioned (organizer-only promote)
4. **INVITED** - Invitation email dispatched (login link + temp password); subject to slot-capacity gate
5. **ACCEPTED** - Committed to presenting
6. **CONTENT_SUBMITTED** - Title + abstract received
7. **QUALITY_REVIEWED** - Content approved (happy end-state of the content lifecycle)
8. **DECLINED** - Terminal "not happening" state; reachable from any non-terminal state (replaces the removed `withdrew`)

**Derived flags** (read-time, not states): `is_slot_assigned := session.start_time IS NOT NULL`; `is_publishable := QUALITY_REVIEWED AND is_slot_assigned` (replaces the removed `confirmed`).

> **Removed per ADR-009 / Epic 11:** `slot_assigned` and `confirmed` (now derived flags), `overflow` (replaced by the slot-capacity gate on `READY → INVITED`), `withdrew` (collapsed into `DECLINED`), and the `tentative` speaker response (responses are now `ACCEPT` or `DECLINE` only).

---

## Troubleshooting

### "Event stuck in SPEAKER_IDENTIFICATION"

**Problem**: Can't advance to SLOT_ASSIGNMENT.

**Solution**:

- Check if minimum speakers are publishable (`QUALITY_REVIEWED` + slot assigned)
- Verify all slots have publishable speakers assigned
- System validates the publishable-speaker count before allowing transition

### "Speaker not showing as publishable"

**Problem**: Speaker has `QUALITY_REVIEWED` AND a slot assigned but `is_publishable` is still false.

**Solution**:

- Check `session.start_time` is set (not just the session created) — `is_slot_assigned` derives from it
- Verify speaker status is exactly `QUALITY_REVIEWED`
- Check `speaker_pool.session_id` links to the correct session

> **Note:** There is no longer a `confirmed` state. Publish-readiness is the derived `is_publishable` flag (`QUALITY_REVIEWED AND is_slot_assigned`), recomputed on read — there is no stored state to "get stuck".

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
- [Phase E: Publishing & Lifecycle →](phase-e-publishing.md) - Event archival

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
5. **Publishing & Lifecycle**: Complete with [Phase E: Publishing & Lifecycle →](phase-e-publishing.md)
