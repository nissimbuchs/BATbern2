# Meeting Coordination

> How BATbern organises the annual partner meetings and sends calendar invites

<span class="feature-status implemented">Implemented</span> — Epic 8.3

## Overview

BATbern holds **two partner meetings per year** — typically a Spring meeting tied to the Spring BATbern event and an Autumn meeting tied to the Autumn event. These are working lunches where organizers and partner representatives discuss upcoming events, topics, and the partnership.

The platform automates the calendar invite process: organisers create the meeting record, click **Send Invite**, and all partner contacts receive a single `.ics` calendar file containing both the partner lunch and the BATbern event itself.

## What Partners Receive

When an organiser sends a meeting invite, all contacts listed on the partner record receive an email with an attached `.ics` calendar file. The file contains **two calendar entries**:

| Calendar Entry | What It Contains |
|---------------|-----------------|
| **Partner Lunch** | Date, start/end time, location, agenda (free text) |
| **BATbern Event** | Full event date, time, venue — so attendees can block the whole day |

Partners can open the `.ics` file with any calendar application (Outlook, Apple Calendar, Google Calendar, etc.) and both events are added automatically.

### Example email

```
Subject: Einladung: BATbern Partner-Meeting + BATbern 57

Dear Partner,

Please find attached a calendar invite for the upcoming
BATbern partner meeting and conference.

Partner Lunch:    15 March 2026, 12:00–14:00
Venue:            Restaurant Schwellenmätteli, Bern
BATbern Event:    15 March 2026, 14:30–19:00

[Attachment: partner-meeting.ics]
```

### Example calendar entries (from the .ics file)

```
BATbern Partner Meeting (SPRING)
When: Monday, 15 March 2026  12:00–14:00
Where: Restaurant Schwellenmätteli, Bern
Notes: Agenda:
       1. Review of 2025 partnership
       2. Preview of 2026 event topics
       3. Partnership renewal discussion

BATbern 57
When: Monday, 15 March 2026  14:30–19:00
Where: Volkshaus Bern
Notes: BATbern Event — you are registered as a partner
```

## For Organisers — Creating a Meeting

<div class="step" data-step="1">

**Navigate to Partner Meetings**

Go to **Partners → Meetings** in the organiser navigation, or open a partner's detail page and click the **Meetings** tab.
</div>

<div class="step" data-step="2">

**Click "Create Meeting"**

The meeting creation form opens.
</div>

<div class="step" data-step="3">

**Fill in the meeting details**

| Field | Required | Notes |
|-------|----------|-------|
| **Linked Event** | Yes | Select from upcoming BATbern events; date auto-fills from the event |
| **Meeting Type** | Yes | Spring or Autumn |
| **Start Time** | Yes | Time of partner lunch (e.g., 12:00) |
| **End Time** | Yes | End of lunch before the main event (e.g., 14:00) |
| **Location** | Yes | Venue name and/or address |
| **Agenda** | No | Free text — included in the calendar invite description |

The meeting date is automatically set to the linked BATbern event date.
</div>

<div class="step" data-step="4">

**Save the meeting**

Click **Save** to create the meeting record. The invite has **not been sent yet** at this point.
</div>

## For Organisers — Sending the Invite

<div class="step" data-step="1">

**Open the meeting record**

Find the meeting in the list (status: invite not sent) and open it.
</div>

<div class="step" data-step="2">

**Review the details**

Confirm that the location, agenda, and linked event are correct before sending. Once sent, partners will have these details in their calendars.
</div>

<div class="step" data-step="3">

**Click "Send Calendar Invite"**

The system sends the `.ics` file asynchronously to all contacts on all partner records. The API responds with **202 Accepted** immediately — the actual email delivery happens in the background (typically within a few seconds).

```
┌─────────────────────────────────────────────┐
│  ✅ Invite sending in progress               │
│                                              │
│  Calendar invites are being sent to all     │
│  partner contacts via email.                │
│                                              │
│  The "Invite Sent" timestamp will appear    │
│  once delivery is confirmed.                │
└─────────────────────────────────────────────┘
```
</div>

<div class="step" data-step="4">

**Confirm sent status**

The meeting list shows an **Invite Sent** timestamp once the emails have been dispatched.
</div>

## For Organisers — Adding Meeting Notes

After the meeting takes place, organisers can record notes directly on the meeting record:

<div class="step" data-step="1">

**Open the completed meeting**

Navigate to **Partners → Meetings** and open the past meeting.
</div>

<div class="step" data-step="2">

**Click "Edit" and add notes**

The **Notes** field accepts free text. Common contents:
- Summary of discussion
- Action items with owners
- Follow-up decisions
- Attendance (who came from each partner)
</div>

<div class="step" data-step="3">

**Save**

Notes are saved and visible to all organisers. They are **not visible to partners**.
</div>

<div class="alert info">
ℹ️ <strong>Note:</strong> Post-meeting notes are organiser-internal only. Partners cannot see meeting notes — they only receive the initial calendar invite.
</div>

## Meeting List View

The meeting list shows past and upcoming meetings:

```
┌─────────────────────────────────────────────────────────────────────┐
│  Partner Meetings                              [+ Create Meeting]    │
│                                                                      │
│  Upcoming                                                           │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  BATbern 57 Partner Lunch — SPRING                         │   │
│  │  15 March 2026  ·  12:00–14:00  ·  Restaurant Schwellen... │   │
│  │  Invite: ✅ Sent on 1 Feb 2026                             │   │
│  │                                  [Edit]  [View Details]    │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  Past                                                               │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  BATbern 56 Partner Lunch — AUTUMN                         │   │
│  │  12 September 2025  ·  12:00–14:00  ·  Hotel Bellevue      │   │
│  │  Invite: ✅ Sent on 20 Aug 2025  ·  Notes: ✅ Added        │   │
│  │                                  [Edit]  [View Details]    │   │
│  └─────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

## Technical Details

The `.ics` file follows **RFC 5545** (iCalendar specification) and uses `METHOD:REQUEST` so the invite appears as an actionable calendar event in supported email clients. The calendar file contains exactly two `VEVENT` blocks — the partner lunch and the BATbern event. Times are stored in UTC (converted from Europe/Zurich).

Email delivery uses **AWS SES** with the `.ics` file attached as `text/calendar; method=REQUEST`.

## Troubleshooting

### "A partner says they didn't receive the invite"

1. Verify the partner has at least one contact with a valid email address on their partner record
2. Ask the partner to check their spam folder (calendar invites from unfamiliar senders can be filtered)
3. The invite can be resent by editing the meeting and clicking **Send Calendar Invite** again — partners who already added it to their calendar will see an update

### "The agenda in the invite is wrong"

Update the meeting record (edit the Agenda field) and resend the invite. Partners' calendars will update when they accept the updated invite.

### "The meeting date is wrong"

The meeting date is derived from the linked BATbern event. If the event date changed, update the linked event first, then re-save the meeting record and resend the invite.

## Related

- [Partner Management →](../entity-management/partners.md) — Managing partner records and contacts
- [Partner Portal Overview →](README.md)
