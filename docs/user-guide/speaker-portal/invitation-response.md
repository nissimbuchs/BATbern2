# Invitation & Response

> How speakers receive and respond to BATbern speaking invitations

<span class="feature-status implemented">Implemented</span> — Epic 6.1b & 6.2a

## Overview

When an organizer sends a speaker invitation, the speaker receives a personalised email with two direct-action buttons. Clicking either button opens a pre-authenticated portal page — no login required.

## The Invitation Email

The speaker receives an HTML email containing:

- Event name, date, and location
- Session topic and title (if assigned)
- Response deadline with a countdown
- Organiser name and contact email
- Two direct-action buttons:

```
┌─────────────────────────────────────────────┐
│  You've been invited to speak at BATbern 57  │
│                                              │
│  Event:    BATbern 57                        │
│  Date:     15 March 2026                     │
│  Topic:    Digital Transformation            │
│                                              │
│  Please respond by: 1 February 2026         │
│                                              │
│  [  ✅ Accept Invitation  ]                  │
│  [  ❌ Decline Invitation ]                  │
└─────────────────────────────────────────────┘
```

Each button contains a unique **RESPOND magic link** — valid for 30 days from the time of sending.

<div class="alert info">
ℹ️ <strong>Note for organizers:</strong> The invitation is sent from the Phase B: Outreach screen by clicking <strong>Send Invitation</strong> on the speaker's kanban card. The email is generated automatically using the configured email template (DE/EN).
</div>

## Accepting an Invitation

<div class="step" data-step="1">

**Speaker clicks "Accept Invitation"**

The magic link opens the BATbern speaker portal in the browser. The speaker is automatically authenticated — no password needed.
</div>

<div class="step" data-step="2">

**Response confirmation screen**

The speaker sees a summary of the event details plus an optional message field:

```
┌─────────────────────────────────────────────┐
│  Confirm Acceptance                          │
│                                              │
│  Event:    BATbern 57 — 15 March 2026       │
│  Session:  Digital Transformation            │
│                                              │
│  Message to organiser (optional):           │
│  [                                         ] │
│                                              │
│  [  Confirm Acceptance  ]                    │
└─────────────────────────────────────────────┘
```
</div>

<div class="step" data-step="3">

**Automatic actions on acceptance**

When the speaker confirms:
- Speaker status transitions: **INVITED → ACCEPTED**
- A **confirmation email** is sent automatically to the speaker with:
  - Event and session details
  - Content submission deadline
  - Link to update their profile
  - Link to submit presentation content
- The organiser is notified in-app (async)
- The acceptance timestamp is recorded in the contact history
</div>

<div class="step" data-step="4">

**Success page**

The speaker sees a confirmation message with next-step links:

```
┌─────────────────────────────────────────────┐
│  ✅ You're confirmed for BATbern 57!         │
│                                              │
│  Next steps:                                 │
│  • Update your speaker profile               │
│  • Submit your presentation details          │
│                                              │
│  Content submission deadline:                │
│  1 March 2026 (28 days away)                │
│                                              │
│  [  Update Profile  ]  [  Submit Content  ] │
└─────────────────────────────────────────────┘
```
</div>

## Declining an Invitation

<div class="step" data-step="1">

**Speaker clicks "Decline Invitation"**

The magic link opens the decline form.
</div>

<div class="step" data-step="2">

**Decline reason (required)**

The speaker must provide a reason:

```
┌─────────────────────────────────────────────┐
│  Decline Invitation                          │
│                                              │
│  We're sorry you can't join us this time.   │
│                                              │
│  Reason for declining *:                    │
│  [                                         ] │
│                                              │
│  [  Confirm Decline  ]                       │
└─────────────────────────────────────────────┘
```
</div>

<div class="step" data-step="3">

**Automatic actions on decline**

- Speaker status transitions: **INVITED → DECLINED**
- The decline reason is stored in the contact history
- The organiser is notified in-app
- The decline timestamp is recorded
</div>

## Already Responded

If a speaker returns to the portal after having already responded (using an expired single-use token or a bookmark), the page shows their previous response and offers recovery options:

```
┌─────────────────────────────────────────────┐
│  You've already responded                    │
│                                              │
│  Response: ✅ Accepted on 15 Jan 2026       │
│                                              │
│  [  Go to Dashboard  ]  [  Contact Organiser ]│
└─────────────────────────────────────────────┘
```

## Token Expiry

RESPOND tokens are valid for **30 days** from the invitation send date and are **single-use** — they become invalid after the first click. If a token has expired:

```
┌─────────────────────────────────────────────┐
│  ⚠️  This invitation link has expired        │
│                                              │
│  Please contact the organiser to request    │
│  a new invitation link.                     │
│                                              │
│  Contact: hans.keller@batbern.ch            │
└─────────────────────────────────────────────┘
```

The organiser can resend an invitation from the Phase B Kanban board, which generates a new token.

## What Organizers See

After a speaker responds, the organiser sees:

- **Kanban card** moves to the ACCEPTED or DECLINED column automatically
- **Contact history** shows the response event with timestamp and any message
- **Status history** records the transition with `change_reason = 'SPEAKER_PORTAL_RESPONSE'`
- **In-app notification** appears in the organiser's notification centre

See [Phase B: Outreach →](../workflow/phase-b-outreach.md) for how to manage the speaker pool after responses.

## Troubleshooting

### Speaker says they didn't receive the invitation email

1. Check the speaker's email address on their profile (must be correct)
2. Ask the speaker to check their spam folder
3. Resend the invitation from the Phase B Kanban board (generates a fresh token)
4. If the email is incorrect, update the speaker profile and resend

### Speaker's link shows "expired"

Resend the invitation from Phase B. The old token is invalidated and a new 30-day token is generated.

### Speaker accepted in error and wants to decline

Contact your BATbern platform administrator to manually override the speaker status. Organizers with the ORGANIZER role can update speaker status directly from the speaker edit modal.
