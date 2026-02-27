# Speaker Dashboard

> A personalised view of upcoming engagements, deadlines, and past event history

<span class="feature-status implemented">Implemented</span> — Epic 6.4 (QA score 98/100, 2026-02-16)

## Overview

The Speaker Dashboard is a read-only personal hub for invited and confirmed speakers. It shows all upcoming BATbern events the speaker is involved in, highlights approaching deadlines, and gives access to past event history and presentation materials.

Access is via the **VIEW magic link** included in the acceptance confirmation email and reminder emails. The link is reusable and valid for 30 days. No password or account is required.

## Accessing the Dashboard

Speakers receive a dashboard link in:
- The **acceptance confirmation email** (after accepting an invitation)
- **Deadline reminder emails** (Tier 1, 2, and 3 automatic reminders)
- The success page after submitting content

The link looks like:
```
https://www.batbern.ch/speaker-portal?token=abc123…
```

Clicking it opens the dashboard directly in the browser — no login screen.

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

## Upcoming Events Section

Shows all events where the speaker is in an active state (invited, accepted, content submitted, quality reviewed, confirmed).

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

### Workflow Status Labels

| Status Shown | Internal State | Meaning |
|--------------|---------------|---------|
| Invitation Pending | INVITED | Speaker hasn't responded yet |
| Accepted | ACCEPTED | Response received, content not yet submitted |
| Content Submission Needed | ACCEPTED + content not submitted | Prompt to submit |
| Content Under Review | CONTENT_SUBMITTED | Organiser is reviewing |
| Revision Requested | CONTENT_SUBMITTED + revision flag | Speaker must resubmit |
| Content Approved | QUALITY_REVIEWED | Approved, awaiting slot |
| Confirmed | CONFIRMED | Quality reviewed AND slot assigned |

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
| REVISION_NEEDED | Revise and Resubmit |
| CONFIRMED | View Submission, Update Profile |

## Past Events Section

Shows all events where:
- The event date is in the past, **and**
- The speaker was CONFIRMED or ACCEPTED

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

Clicking **Update Profile** opens the profile update form where speakers can edit:
- Bio (optional)
- Expertise areas
- LinkedIn URL
- Upload a professional photo
- Upload a CV

## Session Management & Token Expiry

Dashboard access links use **30-day VIEW tokens**. If a speaker's link expires:
- They will see an expiry message with the organiser's email address
- The organiser can send a new reminder from Phase B: Outreach (generates a fresh token)

<div class="alert info">
ℹ️ <strong>Epic 9 Note:</strong> Story 9.1 (JWT magic link authentication) has been delivered, upgrading token handling to RS256 JWT with HTTP-only cookie storage. The user-facing experience is unchanged — speakers still click a link and are authenticated instantly.
</div>

## Accessibility

The Speaker Dashboard is built to **WCAG 2.1 AA** standards:

- ✅ All interactive elements have ARIA labels
- ✅ Full keyboard navigation (Tab, Enter, Escape)
- ✅ Semantic HTML (header, nav, section, article)
- ✅ Minimum 44px touch targets on mobile
- ✅ Visible focus states on all focusable elements
- ✅ Colour contrast ratios verified
- ✅ Screen reader compatible (tested with VoiceOver & NVDA)

## Language Support

All dashboard text, status labels, and email templates are available in **German and English**. The language is determined by the speaker's locale preference or the browser's Accept-Language header.

## Troubleshooting

### "My link is expired"

The organiser can send a fresh link from the Phase B Kanban board (Resend Invitation or Send Reminder). This generates a new 30-day token.

### "My upcoming event is not showing"

Check that:
1. The speaker was added to the event's speaker pool (not just the speakers directory)
2. The speaker's status is INVITED or later — not just IDENTIFIED
3. The event is in the future

### "I can't see the Submit Content button"

The **Submit Content** button only appears when:
- The speaker's status is ACCEPTED, and
- A session has been assigned (content cannot be submitted without a session)

Ask the organiser to confirm that a session has been linked in Phase D: Slot Assignment.

## Related

- [Invitation & Response →](invitation-response.md) — Accepting or declining an invitation
- [Content Submission →](content-submission.md) — Submitting presentation title, abstract, and file
- [Phase B: Outreach →](../workflow/phase-b-outreach.md) — Organiser management of speaker pipeline
- [Phase C: Quality Review →](../workflow/phase-c-quality.md) — How content is reviewed after submission
