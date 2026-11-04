# Event Types Configuration

> <span class="feature-status implemented">Implemented</span> — Tab 0 of the Administration page (Story 10.1).

**Last Updated:** 2026-11-04 — template changes reach every event without its own copy (#805)

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
4. Save changes — these apply to every event of this type that has **no per-event copy**, existing events included. Events whose organizers used **Edit event type** on the slot assignment page (see [Phase D](../workflow/phase-d-assignment.md)) keep their own copy and are not affected.

---

## How Event Types Are Used

When an organiser creates a new event (Phase A — Setup), they select an event type. The platform pre-populates:

- The expected number of approved speakers needed (quality threshold)
- The slot template for the slot assignment drag-and-drop interface (Phase D)
- Default deadlines and timeline for the workflow

Changes to event type configuration take effect for every event of that type without a per-event copy, including events already created: their computed slot times, auto-assign and slot capacity follow the template (#805). If speakers are already assigned at fixed times, review the agenda of upcoming events after changing a template. An event gets its own copy the first time an organizer saves **Edit event type** for it; from then on, template changes no longer reach that event.

---

## Related

- **[Phase A: Setup](../workflow/phase-a-setup.md)** — where event type is selected
- **[Phase D: Slot Assignment](../workflow/phase-d-assignment.md)** — where slots are filled
- **[Administration Overview](README.md)** — back to admin hub
