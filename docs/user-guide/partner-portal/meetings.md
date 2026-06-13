# Meeting Coordination

> How BATbern organises the annual partner meetings and sends calendar invites

> **Last Updated**: 2026-06-13

<span class="feature-status implemented">Implemented</span> — Epic 8.3 (calendar invites) · Story 10-27 (iCal RSVP tracking)

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
| **Location** | No | Venue name and/or address (shown in .ics invite if provided) |
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

The system sends the `.ics` file asynchronously to all contacts on all partner records. Each contact receives the invite at both their **primary** email AND every **additional email** they have registered in their profile (Story 10.32) — addresses are deduplicated case-insensitively so a contact who appears in two roles or shares an address across roles gets a single copy per inbox. The API responds with **202 Accepted** immediately — the actual email delivery happens in the background (typically within a few seconds).

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

## For Organisers — Tracking RSVPs

Once an invite has been sent, the platform tracks who has **accepted**, **declined**, or marked themselves **tentative** — directly from the recipients' own calendar clients. No extra action is needed from partners: when they click Accept or Decline in Outlook, Apple Calendar, Gmail, etc., their calendar client emails an RSVP reply back to BATbern, and the response appears in the meeting detail.

### How it works

The calendar invite is sent as a proper RFC 5545 `METHOD:REQUEST` with one `ATTENDEE` line per recipient and an `ORGANIZER` address (`replies@batbern.ch`) that BATbern monitors. When a recipient responds, their calendar client sends a `METHOD:REPLY` email to that address; BATbern's inbound-email pipeline parses it and records the response against the meeting.

- Each attendee email gets a single tracked response; re-responding (e.g. changing **Tentative** to **Accepted**) updates the existing record in place.
- Responses are matched to the meeting via the invite's calendar `UID`, so they attach to the correct meeting automatically.

### The Attendee Responses panel

An **Attendee Responses** section appears in the meeting detail panel once the invite has been sent (it stays hidden before then). It shows a summary and a colour-coded list:

```
┌─────────────────────────────────────────────────────────────────────┐
│  Attendee Responses                                                  │
│  2 Accepted · 1 Declined · 1 Tentative                              │
│                                                                      │
│  🟢 ACCEPTED   alice@partner.com      responded 2 Feb 2026, 09:14   │
│  🟢 ACCEPTED   dan@partner.com        responded 2 Feb 2026, 11:02   │
│  🔴 DECLINED   bob@partner.com        responded 3 Feb 2026, 08:40   │
│  🟡 TENTATIVE  carl@partner.com       responded 3 Feb 2026, 15:21   │
└─────────────────────────────────────────────────────────────────────┘
```

| Chip | Status | Meaning |
|------|--------|---------|
| 🟢 **ACCEPTED** | green | Attendee accepted the invite in their calendar |
| 🔴 **DECLINED** | red | Attendee declined |
| 🟡 **TENTATIVE** | amber | Attendee marked the invite as tentative |

The panel refreshes automatically when you (re)send the invite. RSVP tracking is **organiser-only** — partners see their own Accept/Decline buttons in their calendar client but do not see the aggregated response list.

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

<div class="alert warning">
⚠️ <strong>Note ownership:</strong> Notes are scoped to their partner company. Attempting to update or delete a note via another partner company's URL returns <code>404 Not Found</code>.
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

For RSVP tracking (Story 10-27), the partner-lunch `VEVENT` includes an `ORGANIZER:mailto:replies@batbern.ch` line plus one `ATTENDEE;PARTSTAT=NEEDS-ACTION;RSVP=TRUE:mailto:{email}` line per recipient — these are what tell calendar clients to send a reply. Replies arrive as `METHOD:REPLY` emails at the monitored `replies@batbern.ch` address, are parsed by the inbound-email pipeline, and are matched back to the meeting by the calendar `UID`.

## Troubleshooting

### "A partner says they didn't receive the invite"

1. Verify the partner has at least one contact with a valid email address on their partner record
2. Ask the partner to check their spam folder (calendar invites from unfamiliar senders can be filtered)
3. If the partner expected the invite at an additional email they registered on their profile, confirm that address is listed under Settings → Account → Additional emails (Story 10.32)
4. The invite can be resent by editing the meeting and clicking **Send Calendar Invite** again — partners who already added it to their calendar will see an update

### "The agenda in the invite is wrong"

Update the meeting record (edit the Agenda field) and resend the invite. Partners' calendars will update when they accept the updated invite.

### "The meeting date is wrong"

The meeting date is derived from the linked BATbern event. If the event date changed, update the linked event first, then re-save the meeting record and resend the invite.

### "A partner accepted but their RSVP isn't showing"

RSVP tracking depends on the recipient's calendar client actually sending a reply email back to `replies@batbern.ch`:

1. Some clients (or some configurations) accept an invite silently without sending a reply. The response can only be recorded if a `METHOD:REPLY` is sent.
2. The reply must come from the same address the invite was addressed to (the one listed as an `ATTENDEE`). A response sent from a different alias may not match.
3. Replies are processed asynchronously through the inbound-email pipeline — allow a few minutes after the partner responds, then reopen the meeting detail (the panel refetches on open and on re-send).
4. If the panel is missing entirely, confirm the invite has actually been sent — the **Attendee Responses** section only appears once **Invite Sent** is set.

## Related

- [Partner Management →](../entity-management/partners.md) — Managing partner records and contacts
- [Partner Portal Overview →](README.md)
