# Task Templates

> 🔨 **IN PROGRESS** — Tab 2 of the Administration Page (Story 10.1). Task deadline reminder emails (Story 10.3) are already live.

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
