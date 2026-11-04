# Task Templates

> <span class="feature-status implemented">Implemented</span> — Tab 2 of the Administration page (Story 10.1). Activation rule and the Namensschilder / Wein templates since the organizer cockpit change of 24 October 2026 (#1105). Task deadline reminder emails (Story 10.3) are live.

**Last Updated:** 2026-11-04

## Overview

The **Task Templates** tab manages the templates from which every event gets its task list. Each
task appears on the event's [Cockpit](../features/cockpit-and-event-day.md), on the **Task Board**
(tasks icon in the top bar) and in **Details → Tasks** of the [event page](../entity-management/event-page.md).

**Navigation**: Administration → Tab 2 (Task Templates), `/organizer/admin?tab=2`

The tab has two lists: **Default Templates** and **Custom Templates** (**+ Add Template**). Each row
shows the template's name, its due date and its trigger state.

---

## Default Templates

There are ten default templates. They cannot be deleted, and their settings can only be changed by a
database migration.

| Template | Trigger | Due | Note |
|----------|---------|-----|------|
| Venue Booking | `TOPIC_SELECTION` | 90 days before the event | |
| Partner Meeting Coordination | `TOPIC_SELECTION` | event day | |
| Moderator Assignment | `TOPIC_SELECTION` | 14 days before the event | |
| Newsletter: Topic Announcement | `TOPIC_SELECTION` | when the task is created | Cancelled automatically once the speakers or the agenda are public |
| Newsletter: Speaker Lineup | speakers published | 30 days before the event | Cancelled automatically once the agenda is public |
| Newsletter: Final Agenda | `AGENDA_PUBLISHED` | 14 days before the event | |
| Catering Coordination | `AGENDA_PUBLISHED` | 5 days before the event | |
| Namensschilder | `AGENDA_PUBLISHED` | 2 days before the event | Name badges, see [Registrations](../entity-management/event-page.md#name-badges) |
| Wein | `AGENDA_PUBLISHED` | event day | |
| Newsletter: Slides Are Online | `EVENT_COMPLETED` | 1 day after the event | Only by its trigger, never earlier (see below) |

**"Speakers published"** is not an event state: it is reached when the **Speakers** phase is
published on the Publishing tab, or at the latest when the event reaches `AGENDA_PUBLISHED`.

Sources: migrations V22 (first seven templates), V84 (Final Agenda and Catering moved from the
removed "agenda finalized" state to `AGENDA_PUBLISHED`), V110 (Slides Are Online) and V125 (Speaker
Lineup trigger, Catering due date, Namensschilder, Wein, activation rule).

> The **Newsletter: Slides Are Online** task prompts the organizer to send the "slides are online"
> email to the event's active registrants. See [Slides-Online Email](../attendee-experience/slides-online-email.md).

### Newsletters that are no longer needed

A newsletter announcement is obsolete once a later one has gone out. The platform cancels the open
task by itself:

- **Newsletter: Topic Announcement** once the speakers or the agenda are public;
- **Newsletter: Speaker Lineup** once the agenda is public.

The rule is tied to these two default templates, not to their names, so renaming them does not break it.

---

## When a task appears

Tasks are created from the ticked templates when the event is created (see
[Creating an Event](../entity-management/events.md#creating-an-event)). A task starts as **Pending**
and is not shown as open work yet. It becomes **To Do** at whichever comes first:

1. the event reaches the task's trigger state, or any later state (a skipped state counts as
   reached);
2. **14 days before the task's due date.**

Tasks triggered at or after the event (`EVENT_LIVE`, `EVENT_COMPLETED`, `ARCHIVED`, for example
**Newsletter: Slides Are Online**) only activate by their trigger, so they never show up before the
event.

If the event is already past a task's trigger when the task is created, the task starts as **To Do**
directly.

The state change activates tasks immediately. In addition, a daily job early in the morning
(Europe/Zurich time) re-checks every pending task, so a missed transition or the 14-day rule still
takes effect. On the Cockpit, a pending task whose due date has passed is shown anyway, marked
**"Trigger not reached yet"**.

When an event is archived, its open tasks are cancelled.

### Events created before 24 October 2026

The **Namensschilder** and **Wein** templates were added on 24 October 2026 (#1105, migration V125).
Tasks are only created from templates when an event is created, so events created before that date
do not have these two tasks.

To add them: open the event → **Details → Tasks**, tick **Namensschilder** and **Wein**. The tasks
are created immediately, with the due date computed from the event date.

The other changes of that day (activation 14 days before the due date, the new Speaker Lineup
trigger) were applied to pending tasks of existing events as well. The new Catering due date (5 days
before, previously 30) applies only to tasks created after that day.

---

## Custom Templates

Custom templates are your own additions. They appear in the template list of the event form and in
**Details → Tasks**, but are not ticked by default when an event is created.

- **Create**: **+ Add Template**
- **Edit**: the pencil icon on the row
- **Delete**: the bin icon on the row

| Field | Description |
|-------|-------------|
| **Name** | Shown on the Cockpit card and the Task Board |
| **Trigger State** | The event state that activates the task |
| **Due Date Type** | `immediate` (due when the task is created) or `relative_to_event`. `absolute` can be chosen but is not supported for templates: the task is then due when it is created. |
| **Offset Days** | For `relative_to_event`: days relative to the event date, negative = before |

New custom templates get the same activation rule as the default ones: 14 days before the due date,
except for triggers at or after the event.

<div class="alert warning">
⚠️ <strong>Trigger State lists are out of date.</strong> The create dialog offers some old state
names (<em>Speaker Brainstorming</em>, <em>Speaker Outreach</em>, <em>Post Event Wrap-Up</em>) that
the platform rejects when you save; use <em>Topic Selection</em>, <em>Agenda Published</em>,
<em>Event Live</em> or <em>Archived</em>. The edit dialog does not list the real event states at
all, so change only the name or due date there; to change a trigger, delete the template and create
it again.
</div>

---

## Task Deadline Reminder Emails

✅ **Already live** (Story 10.3, delivered 2026-02-24)

Organisers receive an automatic email reminder **the day before** any task they are assigned to is due.

**Behaviour**:
- A scheduler runs once a day
- ShedLock prevents duplicate sends across ECS instances
- Non-completed tasks with an assigned organiser and a due date falling tomorrow are selected
- Email subject (EN): `"Task Reminder: {taskName} due tomorrow"`
- Email subject (DE): `"Aufgabenerinnerung: {taskName} fällig morgen"`
- Send failures are logged but do not interrupt the scheduler

Only tasks with an assigned organizer get a reminder. Assign the tasks in **Details → Tasks** of the
event page.

**Template**: `task-reminder-de/en` — editable in the [Email Templates](email-templates.md) tab under the **Task Reminders** category.

---

## Related

- **[Cockpit and Event Day](../features/cockpit-and-event-day.md)** — how tasks appear as attention cards
- **[The Event Page](../entity-management/event-page.md)** — Details → Tasks
- **[Workflow System](../workflow/README.md)** — how task templates interact with event states
- **[Email Templates](email-templates.md)** — edit the task reminder email content
- **[Administration Overview](README.md)** — back to admin hub
