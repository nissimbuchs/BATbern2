# Task Templates

> <span class="feature-status implemented">Implemented</span> — Tab 2 of the Administration page (Story 10.1). Task deadline reminder emails (Story 10.3) are live, and event-state transitions auto-create tasks from the matching templates.

**Last Updated:** 2026-06-13

## Overview

The **Task Templates** tab provides a standalone interface for managing the task templates that drive the event workflow. Previously, templates were only accessible from within an individual event's task board or event form — they are now manageable independently here.

**Navigation**: Administration → Tab 2 (Task Templates)

---

## Template Types

### Default Templates (Read-Only)

Default templates are seeded by the platform and cannot be deleted. They represent the standard workflow for every BATbern event. You can view their configuration but not delete them.

| Template | Trigger State | Typical Due |
|----------|--------------|-------------|
| Venue Booking | `CREATED` | 12 weeks before event |
| Partner Meeting | `TOPIC_SELECTION` | 8 weeks before event |
| Moderator Assignment | `SPEAKER_OUTREACH` | 6 weeks before event |
| Newsletter — Topic Announcement | `TOPIC_SELECTION` | After topic confirmed |
| Newsletter — Speaker Announcement | `AGENDA_FINALIZED` | 14 days before event |
| Newsletter — Event Reminder | `SPEAKERS_PUBLISHED` | 7 days before event |
| Catering | `AGENDA_FINALIZED` | 2 weeks before event |
| Newsletter — Slides Are Online | `EVENT_COMPLETED` | ~1 day after event |

> The **Newsletter — Slides Are Online** task is auto-seeded by Epic 7 (Story 7.3, *"The Slides Are Online" mail*). About a day after an event completes, this task prompts the organizer to send the "slides are online" email to the event's active registrants (DE + EN), honouring email opt-out, with a double-send guard. It is created automatically — no manual setup required.

### Custom Templates

Custom templates are organisation-specific additions. You have full CRUD control:

- **Create** — define a new template with name, description, trigger state, due offset, and assignee
- **Edit** — modify any custom template at any time
- **Delete** — remove custom templates that are no longer needed

Custom templates appear in the template picker when creating or editing events.

---

## Template Fields

| Field | Description | Required |
|-------|-------------|----------|
| **Name** | Short descriptive label shown in task board | ✅ |
| **Description** | Longer notes for the assigned organiser | |
| **Trigger State** | The event state that activates the template | ✅ |
| **Due Date Offset** | Days before/after event date the task is due | ✅ |
| **Default Assignee** | Pre-assigned organiser role or specific user | |
| **Is Default** | System template flag (read-only) | — |

---

## Trigger States

Templates are activated when an event enters a specific state in the 9-state workflow machine:

| State | When it occurs |
|-------|---------------|
| `CREATED` | Event first created |
| `TOPIC_SELECTION` | Event moves into topic brainstorming |
| `SPEAKER_OUTREACH` | Speaker invitation phase begins |
| `QUALITY_REVIEW` | Content review underway |
| `SLOT_ASSIGNMENT` | Speakers assigned to time slots |
| `SPEAKERS_PUBLISHED` | Speaker profiles published to public site |
| `AGENDA_FINALIZED` | Full agenda confirmed and published |
| `EVENT_LIVE` | Day of the event |
| `EVENT_COMPLETED` | Event finished |

When an event transitions into a trigger state, all active templates with that trigger are instantiated as tasks on the event's task board and assigned to the designated organiser.

---

## Automatic Task Creation by State Transitions

<span class="feature-status implemented">Implemented</span>

Tasks are **created automatically** as an event moves through the workflow — organizers do not hand-create the standard tasks. When an event enters a trigger state, the platform instantiates every active template (default + custom) whose trigger matches that state, computes each task's due date from its offset relative to the event date, and assigns it to the configured organiser.

This is how the standard checklist (venue booking, partner meeting, moderator assignment, newsletters, catering, post-event slides mail) appears on the task board at the right moment without manual effort. Some tasks are also seeded by Epic 7 contribution features (see the *Newsletter — Slides Are Online* row above).

Tasks for an event are cleaned up when the event is archived (Story 10.18), so completed events do not leave stale reminders behind.

---

## Task Deadline Reminder Emails

✅ **Already live** (Story 10.3, delivered 2026-02-24)

Organisers receive an automatic email reminder **the day before** any task they are assigned to is due.

**Behaviour**:
- Scheduler runs daily at **8:00 AM Swiss time** (`Europe/Zurich`)
- ShedLock prevents duplicate sends across ECS instances
- Non-completed tasks with an assigned organiser and a due date falling tomorrow are selected
- Email subject (EN): `"Task Reminder: {taskName} due tomorrow"`
- Email subject (DE): `"Aufgabenerinnerung: {taskName} fällig morgen"`
- Send failures are logged but do not interrupt the scheduler

**Template**: `task-reminder-de/en` — editable in the [Email Templates](email-templates.md) tab under the **Task Reminders** category.

---

## Creating a Custom Template

1. Navigate to **Administration → Task Templates**
2. Click **Add Custom Template**
3. Fill in the template fields (name, description, trigger state, due offset)
4. Optionally assign a default organiser
5. Click **Save**

The template is immediately available in the event form's task template picker.

---

## Related

- **[Workflow System](../workflow/README.md)** — how task templates interact with event states
- **[Phase F: Task Management](../workflow/phase-f-communication.md)** — task board in practice
- **[Email Templates](email-templates.md)** — edit the task reminder email content
- **[Administration Overview](README.md)** — back to admin hub
