# Invitation & Response

> How speakers receive credentials, sign in, and respond to BATbern speaking invitations

<span class="feature-status implemented">Implemented</span> — Epic 6.1b & 6.2a, refactored to Cognito auth in Epic 11.E.3

**Last Updated:** 2026-06-13

## Overview

When an organizer promotes a speaker to `READY` and sends the invitation, the speaker receives an email with a **login link and a temporary password**. The speaker signs in to BATbern with their Cognito account (email + password, or "Continue with Google"), lands on their dashboard, and accepts or declines the invitation from there. There is no magic link — the portal is behind the standard logged-in session.

## The Invitation Email

The speaker receives an HTML email (DE/EN) containing:

- Event name, date, and location
- Session topic and title (if assigned)
- Response deadline with a countdown
- Organiser name and contact email
- **A login link** to www.batbern.ch
- **A temporary password** for first sign-in

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
│  Your login:    hans.mueller@example.com     │
│  Temp password: ••••••••••                   │
│                                              │
│  [  Log in to BATbern  ]                     │
└─────────────────────────────────────────────┘
```

<div class="alert info">
ℹ️ <strong>Note for organizers:</strong> The invitation is sent from the Phase B: Outreach screen by clicking <strong>Send Invitation</strong> on the speaker's kanban card (status moves to <code>INVITED</code>). The Cognito account was already provisioned when the speaker was promoted to <code>READY</code>; the invitation email simply delivers the credentials. The email is generated automatically using the configured template (DE/EN).
</div>

## Signing In

<div class="step" data-step="1">

**Speaker clicks "Log in to BATbern"**

The link opens the standard BATbern login page (the same one every role uses). The speaker enters their email and the temporary password — or chooses **"Continue with Google"** if their Google account matches the invited email.
</div>

<div class="step" data-step="2">

**First-login password change**

On first sign-in with a temporary password, Cognito requires the speaker to set a new password (`FORCE_CHANGE_PASSWORD → CONFIRMED`). Google sign-in skips this step.
</div>

<div class="step" data-step="3">

**Speaker lands on their dashboard**

After authenticating, the speaker is taken to `/speaker-portal/dashboard`, which lists every event they're involved in. Pending invitations show a **Respond to Invitation** action.
</div>

## Accepting an Invitation

<div class="step" data-step="1">

**Speaker opens the invitation**

From the dashboard, the speaker clicks **Respond to Invitation** on the relevant event card. The response page targets that specific event via its `eventCode` in the URL path (e.g. `/speaker-portal/events/BATbern57/respond`) — no token is involved.
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
- A **confirmation email** is sent automatically to the speaker with the content submission deadline and a link to sign in and submit content
- The organiser is notified in-app (async)
- The acceptance is recorded in `speaker_status_history` with `changed_by_username` = the speaker's Cognito username
</div>

<div class="step" data-step="4">

**Success page**

The speaker sees a confirmation message with next-step links back into the portal:

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

**Speaker chooses to decline**

From the response page for that event, the speaker selects **Decline**.
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

- Speaker status transitions: **INVITED → DECLINED** (`DECLINED` is the single terminal "not happening" state, reachable from any non-terminal state)
- The decline reason is stored in `speaker_status_history`
- The organiser is notified in-app
- The decline timestamp is recorded
</div>

## Already Responded

If a speaker returns to the response page for an event they've already responded to, the page shows their previous response and offers next-step options:

```
┌─────────────────────────────────────────────┐
│  You've already responded                    │
│                                              │
│  Response: ✅ Accepted on 15 Jan 2026       │
│                                              │
│  [  Go to Dashboard  ]  [  Contact Organiser ]│
└─────────────────────────────────────────────┘
```

## Authorization

Because the portal is Cognito-secured, a speaker can only act on events they were invited to:

- The backend resolves the speaker's pool entry from `(authenticated username, eventCode)`.
- A request for an `eventCode` the speaker has no pool row for returns **403 Forbidden** (not 404).
- Unauthenticated requests return **401 Unauthorized**; an ORGANIZER- or PARTNER-only token (no SPEAKER role) returns **403**.

## What Organizers See

After a speaker responds, the organiser sees:

- **Kanban card** moves to the ACCEPTED or DECLINED column automatically
- **Contact history** shows the response event with timestamp and any message
- **Status history** records the transition with `changed_by_username` = the speaker's Cognito username
- **In-app notification** appears in the organiser's notification centre

See [Phase B: Outreach →](../workflow/phase-b-outreach.md) for how to manage the speaker pool after responses.

## Troubleshooting

### Speaker says they didn't receive the invitation email

1. Check the speaker's email address on their profile (must be correct)
2. Ask the speaker to check their spam folder
3. Resend the invitation from the Phase B Kanban board (re-sends the login credentials)
4. If the email is incorrect, update the speaker profile and resend

### Speaker can't sign in / forgot their password

The speaker uses the standard **"Forgot password"** flow on the BATbern login page (Cognito self-service reset) — no organizer action needed. If the temporary password was never used and expired, resend the invitation from Phase B to issue a fresh temporary password.

### Speaker accepted in error and wants to decline

The speaker can move from `ACCEPTED → DECLINED` themselves (decline is reachable from any non-terminal state). Alternatively, organizers with the ORGANIZER role can update speaker status directly from the speaker edit modal.
