# Event Types Configuration

> 🔨 **IN PROGRESS** — Part of Story 10.1 (Administration Page). This documentation describes the intended functionality once delivered.

## Overview

The **Event Types** tab lets you define the slot templates used when creating events of each type. This is a pure configuration screen — it controls how many presentation slots, break slots, and time blocks are offered when an organiser creates a new event of a given type.

**Navigation**: Administration → Tab 0 (Event Types)

> **Route change**: The previous standalone page at `/organizer/event-types` now redirects to `/organizer/admin?tab=0`. Deep links to the old route continue to work.

---

## Event Types

BATbern supports three event formats, each with a different default slot structure:

| Type | Format | Typical Duration | Typical Slot Count |
|------|--------|------------------|--------------------|
| `FULL_DAY` | Full-day conference | 8–10 hours | 8–12 presentation slots |
| `AFTERNOON` | Afternoon session | 4–5 hours | 4–6 presentation slots |
| `EVENING` | Evening meetup | 2–3 hours | 2–4 presentation slots |

---

## Configuring Slot Templates

Each event type has a configurable slot template that defines:

- **Number of presentation slots** — how many speaker sessions to schedule
- **Break slots** — coffee breaks, lunch, networking time
- **Time block defaults** — typical start time and slot duration for the format

### How to Edit

1. Navigate to **Administration → Event Types**
2. Click the event type you want to configure (FULL_DAY, AFTERNOON, or EVENING)
3. Adjust slot counts and timing defaults
4. Save changes — these apply to all **future** events created with this type (existing events are not affected)

---

## How Event Types Are Used

When an organiser creates a new event (Phase A — Setup), they select an event type. The platform pre-populates:

- The expected number of approved speakers needed (quality threshold)
- The slot template for the slot assignment drag-and-drop interface (Phase D)
- Default deadlines and timeline for the workflow

Changes to event type configuration take effect for new events only. Previously created events retain the slot structure they were created with.

---

## Related

- **[Phase A: Setup](../workflow/phase-a-setup.md)** — where event type is selected
- **[Phase D: Slot Assignment](../workflow/phase-d-assignment.md)** — where slots are filled
- **[Administration Overview](README.md)** — back to admin hub
