# Administration

> <span class="feature-status implemented">Implemented</span> — The Administration page and all its tabs are live (Epic 10, extended since). Email-template management (Story 10.2) and task deadline reminder emails (Story 10.3) are delivered.

**Last Updated:** 2026-11-04

## Overview

The **Event Management Administration** page consolidates platform configuration into a single, organised hub accessible from the user menu.

**Navigation**: User menu (top-right) → **Administration** → `/organizer/admin`

This replaces scattered configuration entry points that were previously spread across the event management dashboard, company management screen, and user list.

---

## The Tabs

| Tab | Name | Contents |
|-----|------|----------|
| 0 | **Event Types** | Slot templates for FULL_DAY, AFTERNOON, EVENING |
| 1 | **Import Data** | Batch import for events, sessions, companies, speakers, participants |
| 2 | **Task Templates** | Default and custom task templates |
| 3 | **Email Templates** | Email subjects and content, without a code deploy |
| 4 | **Presentation** | About text and partner count shown on the moderator presentation (`/present/{eventCode}`) |
| 5 | **AI Prompts** | The prompts for the AI event description, theme image and abstract quality review |
| 6 | **Settings** | Recipients of support@batbern.ch, and the Mail Center switch (notice instead of forwarding) |
| 7 | **Global Images** | Teaser images shown as slides in every event's presentation |
| 8 | **Venue & Catering** | Venue contact, catering contact and venue coordinator, used for the venue and catering mails |

The tab is kept in the URL: `/organizer/admin?tab=N`.

---

## Access & Permissions

The Administration page is for organizers only. The menu item **Administration** appears in the user menu (avatar, top right) for users with the organizer role; anyone else who opens `/organizer/admin` sees *"Access Denied"*.

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

### Tab 4 — Presentation

The **About Text** and the **Partner Count** shown on the moderator presentation page. See
[Media & Content](../features/media-and-content.md).

### Tab 5 — AI Prompts

The three prompts the AI features use: event description, theme image, and abstract quality review.
Each can be edited and saved, or reset to its default.

### Tab 6 — Settings

- **Email Forwarding**: the recipients of mail to `support@batbern.ch`. Empty means all organizers.
- **Mail Center: notice instead of forwarding**: one checkbox per address group. Checked groups are
  answered in the Mail Center instead of being forwarded. See
  [Event mail page](../features/event-mail.md).

### Tab 7 — Global Images

Teaser images shown as full-screen slides on **all** event presentations. Images for one event only
are added in that event's **Details → Settings → Teaser Images**.

### Tab 8 — Venue & Catering

The venue contact, the catering contact (salutation, name, email each) and the venue coordinator,
an organizer whose address is the Reply-To of the venue and catering mails. They are the same for
every event.

The legacy BAT-format export/import tab (Story 10.20) no longer exists.

---

## Related Organizer Admin Surfaces (outside this page)

Several organizer-only administration surfaces from Epic 10 live elsewhere in the UI rather than on the Administration page. They are documented here for completeness:

### Newsletter Subscriber Management

<span class="feature-status implemented">Implemented</span> (Story 10.28)

A dedicated **top-level navigation item** — **Newsletter Subscribers** (`/organizer/newsletter-subscribers`, organizer-only) — provides a paginated, searchable, sortable list of every newsletter subscriber. From it you can **unsubscribe**, **re-subscribe**, or **delete** a subscriber without touching the database. Search matches email or first name; filters cover active / unsubscribed / all. List hygiene is reinforced by SES bounce processing (Story 10.29), which auto-unsubscribes hard-bounced addresses.

Newsletters are sent per event from the event page: **Communications → + New mail → Newsletter** (see [Event mail page](../features/event-mail.md)). The templates are in the **Newsletter** category of [Email Templates](email-templates.md).

### Registration, Waitlist & Deregistration Administration

<span class="feature-status implemented">Implemented</span> (Stories 10.10, 10.11, 10.12)

These live on the **Registrations** tab of the event page (and on the public website for attendees), not on the Administration page. See [The Event Page → Registrations](../entity-management/event-page.md#registrations).

- **Registration status** (10.10) — attendees see their current registration state; organizers see status chips in the participant list.
- **Capacity and waitlist** (10.11) — the **Registration Capacity** is set per event in **Details → Settings**. Once it is reached, new sign-ups join the **waitlist**. Free places are filled from the waitlist in order, with a mail to each person who moves up: when the capacity is saved (every free place at once), and when a registration is cancelled. Organizers can also promote a person by hand with **Promote to Registered**. Details: [Waitlist and capacity](../entity-management/event-page.md#waitlist-and-capacity).
- **Self-service deregistration** (10.12) — attendees cancel via a token link in their confirmation email or by entering their email on the event page; cancelled registrations appear with a grey **Cancelled** chip in the participant list and free their place for the waitlist.

Email-reply-based unsubscribe/deregistration (Story 10.17) and SES forwarding / distribution lists (Story 10.26) further reduce manual organizer effort.

---

## Related Features

- **[Workflow System](../workflow/README.md)** — how tasks and event states interact
- **[Speaker Portal](../speaker-portal/README.md)** — speaker emails triggered by workflow
- **[Partner Portal](../partner-portal/README.md)** — partner meeting emails
- **[Notification System](../features/notifications.md)** — email delivery overview
