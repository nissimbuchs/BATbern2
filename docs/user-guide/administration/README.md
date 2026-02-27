# Administration

> 🔨 **IN PROGRESS** — Stories 10.1 and 10.2 are under active development. This documentation describes the intended functionality. Story 10.3 (task deadline reminder emails) is already live.

## Overview

The **Event Management Administration** page consolidates all platform configuration into a single, organised hub accessible from the user menu.

**Navigation**: User menu (top-right) → **Administration** → `/organizer/admin`

This replaces scattered configuration entry points that were previously spread across the event management dashboard, company management screen, and user list.

---

## The Four Tabs

| Tab | Name | Contents |
|-----|------|----------|
| 0 | **Event Types** | Configure slot templates for FULL_DAY, AFTERNOON, EVENING |
| 1 | **Import Data** | Batch import modals for all 5 entity types |
| 2 | **Task Templates** | Manage default and custom task templates |
| 3 | **Email Templates** | Edit email subjects and content without a code deploy |

---

## Access & Permissions

The Administration page is available to users with the **ORGANIZER** or **ADMIN** role. Partners and speakers do not have access.

The menu item appears as **Administration** (DE: *Administration*) in the user menu dropdown.

---

## Tab Descriptions

### Tab 0 — Event Types

Configure the slot templates used when creating events of each type. Replaces the previous standalone page at `/organizer/event-types` — that route now redirects to `/organizer/admin?tab=0`.

See [Event Types](event-types.md) for full details.

### Tab 1 — Import Data

Five batch import modals consolidated in one place:

- **Events** — import historical event records
- **Sessions** — import session/presentation data
- **Companies** — import company records
- **Speakers** — import speaker profiles
- **Participants / Attendees** — import historical attendance data

Import buttons have been **removed** from their previous locations (event management dashboard, company management screen, user list) and now live exclusively here.

See [Import Data](import-data.md) for full details.

### Tab 2 — Task Templates

Manage the task templates that drive the event workflow. Previously only accessible from within the event form or task board — now available as a standalone management interface.

See [Task Templates](task-templates.md) for full details.

### Tab 3 — Email Templates

Edit the email templates used for speaker invitations, deadline reminders, registration confirmations, partner calendar invites, and task reminders — all without requiring a code deployment.

See [Email Templates](email-templates.md) for full details.

---

## Related Features

- **[Workflow System](../workflow/README.md)** — how tasks and event states interact
- **[Speaker Portal](../speaker-portal/README.md)** — speaker emails triggered by workflow
- **[Partner Portal](../partner-portal/README.md)** — partner meeting emails
- **[Notification System](../features/notifications.md)** — email delivery overview
