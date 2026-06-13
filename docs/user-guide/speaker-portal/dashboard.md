# Speaker Dashboard

> A personalised view of upcoming engagements, deadlines, past event history, profile, and company info

<span class="feature-status implemented">Implemented</span> — Epic 6.4 (QA score 98/100), Cognito auth refactor in Epic 11.E.3, company self-service in Epic 11.G.1

**Last Updated:** 2026-06-13

## Overview

The Speaker Dashboard is the personal hub for invited and confirmed speakers. It shows all upcoming BATbern events the speaker is involved in, highlights approaching deadlines, gives access to past event history and presentation materials, and lets the speaker manage their profile and their company's public footprint.

Access is via the speaker's **AWS Cognito session** — the same account used across the BATbern app. After signing in (email + password, or "Continue with Google") the speaker lands on `/speaker-portal/dashboard`. There is no separate speaker login and no magic-link token.

## Accessing the Dashboard

Speakers reach the dashboard by signing in to BATbern at www.batbern.ch:
- The **invitation email** delivers the login link + temporary password (first sign-in forces a password change).
- The **acceptance confirmation email** and **deadline reminder emails** (Tier 1, 2, 3) link to the same login page.
- Once signed in, the dashboard is the default landing page for the SPEAKER role and is always reachable from the navigation menu.

The dashboard route is simply:
```
https://www.batbern.ch/speaker-portal/dashboard
```

No token is appended to the URL. Per-event actions use the event's `eventCode` in the path (e.g. `/speaker-portal/events/{eventCode}/content`).

## Dashboard Layout

```
┌─────────────────────────────────────────────────────────────┐
│                 BATbern Speaker Portal                       │
│                                                             │
│  👤 Hans Müller                                             │
│     Senior Architect, Müller Architekten AG                 │
│     Profile: ████████░░ 80% complete  [Update Profile]     │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  UPCOMING EVENTS                                            │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  BATbern 57 — 15 March 2026                         │  │
│  │  📍 Bern, Switzerland                               │  │
│  │                                                      │  │
│  │  Session: Digital Transformation in Practice        │  │
│  │  Status:  🟡 Content Submission Needed              │  │
│  │                                                      │  │
│  │  ⚠️  Content deadline: 1 Mar 2026 (12 days away)    │  │
│  │                                                      │  │
│  │  Organiser: Sarah Keller  sarah.keller@batbern.ch   │  │
│  │                                                      │  │
│  │  [  Submit Content  ]  [  Update Profile  ]         │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                             │
│  PAST EVENTS                                                │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  BATbern 56 — 12 September 2025                     │  │
│  │  Session: Sustainable Urban Planning                 │  │
│  │  Materials: ✅ Available  [Download]                │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

The dashboard lists every event the authenticated speaker is associated with — the backend resolves them from the speaker's Cognito username (no `eventCode` needed for the dashboard itself).

## Upcoming Events Section

Shows all events where the speaker is in an active state (invited, accepted, content submitted, quality reviewed).

**Each event card displays:**

| Field | Description |
|-------|-------------|
| Event name & date | BATbern event name and full date |
| Location | Venue/city |
| Session title | Assigned session (if set) |
| Status | Current workflow state (see below) |
| Deadlines | Response and/or content deadline with urgency colour |
| Organiser | Name and email address (click to send email) |
| Quick actions | Contextual buttons based on current status |

Events are sorted by date ascending (soonest first).

### Workflow Status Labels (8-state model, ADR-009)

| Status Shown | Internal State | Meaning |
|--------------|---------------|---------|
| Invitation Pending | INVITED | Speaker hasn't responded yet |
| Accepted | ACCEPTED | Response received, content not yet submitted |
| Content Submission Needed | ACCEPTED + content not submitted | Prompt to submit |
| Content Under Review | CONTENT_SUBMITTED | Moderator is reviewing |
| Revision Requested | CONTENT_SUBMITTED + revision flag | Speaker must resubmit |
| Content Approved | QUALITY_REVIEWED | Approved, awaiting slot |
| Confirmed / Publishable | QUALITY_REVIEWED + slot assigned | Content reviewed AND a slot is assigned (`is_publishable`) |
| Declined | DECLINED | Terminal "not happening" state |

> The legacy `CONFIRMED` state was removed in Epic 11. "Confirmed / Publishable" is now the derived condition `QUALITY_REVIEWED ∧ slot assigned`.

### Deadline Urgency Colours

Deadlines are colour-coded based on how much time remains:

| Colour | Time Remaining | Meaning |
|--------|---------------|---------|
| 🟢 Green | More than 14 days | On track |
| 🟡 Amber | 7 – 14 days | Action soon |
| 🔴 Red | Under 7 days | Urgent |

### Quick-Action Buttons

The dashboard shows contextual action buttons based on the speaker's current status:

| Status | Button(s) Shown |
|--------|----------------|
| INVITED | Respond to Invitation |
| ACCEPTED (no content) | Submit Content, Update Profile |
| CONTENT_SUBMITTED | View Submission, Update Profile |
| CONTENT_SUBMITTED + revision flag | Revise and Resubmit |
| QUALITY_REVIEWED | View Submission, Update Profile |

Each button navigates to the event-scoped page (`/speaker-portal/events/{eventCode}/...`) under the same Cognito session — no re-authentication.

## Past Events Section

Shows all events where:
- The event date is in the past, **and**
- The speaker reached `ACCEPTED` or later

**Each past event card shows:**
- Event name and date
- Session title and topic
- Presentation materials availability
- Download link (if materials are published)

Events are sorted by date descending (most recent first).

## Profile Section

The dashboard header shows:
- Speaker photo (if uploaded)
- Name and company
- **Profile completeness percentage** — encourages speakers to fill in missing fields

Clicking **Update Profile** opens the profile page (`/speaker-portal/profile`), which reads and writes the speaker's identity via the standard CUMS `/users/me` endpoints (not per-event token endpoints). Speakers can edit:
- Bio (optional)
- Expertise areas
- LinkedIn URL
- Upload a professional photo (presigned-S3 upload)
- Upload a CV

## My Company Section (Speaker Company Self-Service)

<span class="feature-status implemented">Implemented</span> — Story 11.G.1

Speakers whose company logo, display name, and website appear on the public site beside their session can keep that footprint current themselves — without emailing an organizer. A **"My Company"** section on the profile page (`/speaker-portal/profile`) lets the speaker propose changes to three fields, with an organizer reviewing the change before it goes live.

**How it works:**

1. The section appears only when the speaker is linked to a company (`user.companyName` is set).
2. With no pending change, the speaker sees an inline edit form with exactly three fields — **Display name**, **Website**, and **Company logo** (uploaded via the existing presigned-S3 flow). At least one field must change to submit.
3. Submitting sends the change as a **proposal** (status `PENDING`) — it does **not** update the live company immediately. An organizer review task is created.
4. While a proposal is pending, the form is replaced by an amber banner ("You have a company-info update awaiting organizer review") plus an expandable "View what I submitted" diff (current → proposed). No resubmit is possible until the current one is decided (one pending proposal per company).
5. An organizer reviews the proposal on `/organizer/companies` (a "Pending updates" badge + filter chip + side-by-side review panel). On **Approve**, the live company fields update; on **Reject**, the company is untouched and the speaker receives a notification email (DE/EN) with the reason and a link back to `/speaker-portal/profile#my-company` to resubmit.

This keeps speakers self-sufficient for their public company footprint while preserving an organizer sign-off gate.

## Accessibility

The Speaker Dashboard is built to **WCAG 2.1 AA** standards (unchanged through the Epic 11 refactor):

- ✅ All interactive elements have ARIA labels
- ✅ Full keyboard navigation (Tab, Enter, Escape)
- ✅ Semantic HTML (header, nav, section, article)
- ✅ Minimum 44px touch targets on mobile
- ✅ Visible focus states on all focusable elements
- ✅ Colour contrast ratios verified
- ✅ Screen reader compatible (tested with VoiceOver & NVDA)

## Multi-Role Navigation

A speaker who is also an organizer or partner uses a **single Cognito session**. The navigation menu renders grouped sections — a "Speaker" section (dashboard, content, profile) and an "Organizer" (or "Partner") section — separated by dividers. Switching between portals is a normal nav click; there is no re-authentication and no separate per-role login.

## Language Support

All dashboard text and status labels are localised in all 10 supported locales; the speaker emails are authored in **German and English** (with English fallback). The language is determined by the speaker's locale preference or the browser's Accept-Language header.

## Troubleshooting

### "I can't sign in"

Use the standard BATbern login page. If the temporary password from the invitation email has expired or was never used, ask the organizer to resend the invitation (issues a fresh temporary password), or use Cognito's self-service "Forgot password" flow. "Continue with Google" works if the Google account matches the invited email.

### "My upcoming event is not showing"

Check that:
1. The speaker was promoted to at least `READY` (which provisions the account) and then `INVITED` — not just left at `IDENTIFIED` or `CONTACTED`
2. The event is in the future
3. The speaker is signed in with the correct account (the email the invitation was sent to)

### "I can't see the Submit Content button"

The **Submit Content** button only appears when:
- The speaker's status is `ACCEPTED`, and
- A session has been assigned (content cannot be submitted without a session)

Ask the organiser to confirm that a session has been linked in Phase D: Slot Assignment.

### "I don't see the My Company section"

The section is hidden when the speaker isn't linked to a company. Ask an organizer to set your company assignment first.

## Related

- [Invitation & Response →](invitation-response.md) — Accepting or declining an invitation
- [Content Submission →](content-submission.md) — Submitting presentation title, abstract, and file
- [Phase B: Outreach →](../workflow/phase-b-outreach.md) — Organiser management of speaker pipeline
- [Phase C: Quality Review →](../workflow/phase-c-quality.md) — How content is reviewed after submission
