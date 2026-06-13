# Administration

> <span class="feature-status implemented">Implemented</span> — The Administration page and all its tabs are live (Epic 10). Email-template management (Story 10.2), task deadline reminder emails (Story 10.3), and legacy BAT-format export/import (Story 10.20) are all delivered.

**Last Updated:** 2026-06-13

## Overview

The **Event Management Administration** page consolidates platform configuration into a single, organised hub accessible from the user menu.

**Navigation**: User menu (top-right) → **Administration** → `/organizer/admin`

This replaces scattered configuration entry points that were previously spread across the event management dashboard, company management screen, and user list.

---

## The Tabs

| Tab | Name | Contents | Status |
|-----|------|----------|--------|
| 0 | **Event Types** | Configure slot templates for FULL_DAY, AFTERNOON, EVENING | <span class="feature-status implemented">Implemented</span> |
| 1 | **Import Data** | Batch import modals for all 5 entity types | <span class="feature-status implemented">Implemented</span> |
| 2 | **Task Templates** | Manage default and custom task templates | <span class="feature-status implemented">Implemented</span> |
| 3 | **Email Templates** | Edit email subjects and content without a code deploy | <span class="feature-status implemented">Implemented</span> |
| 4 | **Export / Import** | Legacy BAT-format JSON export and import (data + assets) | <span class="feature-status implemented">Implemented</span> |

---

## Access & Permissions

The Administration page is available to users with the **ORGANIZER** or **ADMIN** role. Partners, speakers, and attendees do not have access.

The menu item appears as **Administration** (DE: *Administration*) in the user menu dropdown. The page enforces an ORGANIZER role guard, and the active tab is persisted in the URL query param (`?tab=N`).

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

Manage the task templates that drive the event workflow. Previously only accessible from within the event form or task board — now available as a standalone management interface. This is also where automated tasks (state-transition seeded) and the deadline reminder emails are explained.

See [Task Templates](task-templates.md) for full details.

### Tab 3 — Email Templates

View and edit the email templates the platform sends — speaker invitations, deadline reminders, registration confirmations, partner calendar invites, task reminders, newsletters — without a code deployment. Content templates use a **TinyMCE WYSIWYG** editor; layout templates use a **Monaco** source editor. Templates are authored in **German and English**.

See [Email Templates](email-templates.md) for full details.

### Tab 4 — Export / Import (Legacy BAT Format)

Export the full BATbern data set in the legacy BAT JSON format (and an accompanying asset manifest), and import data in that same format. Used to migrate data between system versions and to interoperate with the old BATspa platform.

See [Import Data](import-data.md#legacy-bat-format-export--import) for full details.

---

## Related Organizer Admin Surfaces (outside this page)

Several organizer-only administration surfaces from Epic 10 live elsewhere in the UI rather than on the Administration page. They are documented here for completeness:

### Newsletter Subscriber Management

<span class="feature-status implemented">Implemented</span> (Story 10.28)

A dedicated **top-level navigation item** — **Newsletter Subscribers** (`/organizer/newsletter-subscribers`, organizer-only) — provides a paginated, searchable, sortable list of every newsletter subscriber. From it you can **unsubscribe**, **re-subscribe**, or **delete** a subscriber without touching the database. Search matches email or first name; filters cover active / unsubscribed / all. List hygiene is reinforced by SES bounce processing (Story 10.29), which auto-unsubscribes hard-bounced addresses.

Newsletter *sending* and *template selection* (Story 10.14) happen per-event from the event's Newsletter tab, using the **Newsletter** category in [Email Templates](email-templates.md).

### Registration, Waitlist & Deregistration Administration

<span class="feature-status implemented">Implemented</span> (Stories 10.10, 10.11, 10.12)

These surfaces live on the **event's Participants/Attendees tab** (and on the public homepage for attendees), not on the Administration page:

- **Registration status indicator** (10.10) — attendees see their current registration state; organizers see status chips in the attendees table.
- **Venue capacity enforcement + waitlist** (10.11) — set a `registrationCapacity` per event; once full, new registrations join a **waitlist**, and cancellations auto-promote the next waitlisted attendee. The public homepage hero shows a "X spots remaining" / "Full — join waitlist" badge.
- **Self-service deregistration** (10.12) — attendees cancel via a token link in their confirmation email or by entering their email on the event page; cancelled registrations appear with a grey `CANCELLED` chip in the organizer attendees table and trigger waitlist promotion.

Email-reply-based unsubscribe/deregistration (Story 10.17) and SES forwarding / distribution lists (Story 10.26) further reduce manual organizer effort.

---

## Related Features

- **[Workflow System](../workflow/README.md)** — how tasks and event states interact
- **[Speaker Portal](../speaker-portal/README.md)** — speaker emails triggered by workflow
- **[Partner Portal](../partner-portal/README.md)** — partner meeting emails
- **[Notification System](../features/notifications.md)** — email delivery overview
