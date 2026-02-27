# Speaker Self-Service Portal

> Enable speakers to respond to invitations, submit content, and track their participation — no account required

<span class="feature-status implemented">Implemented</span> — Epic 6 (Stories 6.0–6.5, 2026-02-16)

## Overview

The Speaker Self-Service Portal lets invited speakers manage their participation entirely through **magic links** — no password or account creation required. Speakers receive a personalised email with direct action links, and can accept/decline invitations, submit their presentation content, upload materials, and view their upcoming and past engagements from a single dashboard.

**For organizers**, the portal automates the most time-consuming parts of speaker coordination: chasing responses, collecting abstracts, and sending deadline reminders.

## How It Works

```
Organizer sends invitation
         ↓
Speaker receives email with two direct links:
  [✅ Accept]   [❌ Decline]
         ↓
Speaker clicks Accept
         ↓
Confirmation email sent automatically with:
  • Content submission deadline
  • [Update Profile] link
  • [Submit Content] link
         ↓
Speaker submits title, abstract, and materials
         ↓
Organizer reviews content (Phase C Quality Review)
         ↓
Speaker confirmed once quality reviewed + slot assigned
```

## Magic Links Explained

Every link sent to a speaker is a **magic link** — a secure, time-limited token embedded in the email URL. Speakers never need to create or remember a password.

| Token Type | Valid For | Reusable? | Used For |
|------------|-----------|-----------|----------|
| **RESPOND** | 30 days | No (single-use) | Accept / Decline invitation |
| **VIEW** | 30 days | Yes | Dashboard, profile updates, content submission |

**Security**: Tokens are cryptographically generated (32-byte SecureRandom) and only their SHA-256 hash is stored in the database — the token itself is never persisted. Rate limiting: 5 requests/minute per IP.

## Speaker Portal Sections

| Section | What Speakers Can Do |
|---------|---------------------|
| [Invitation & Response](invitation-response.md) | Accept or decline an invitation |
| [Content Submission](content-submission.md) | Submit title, abstract, presentation file |
| [Speaker Dashboard](dashboard.md) | View upcoming engagements, deadlines, past events |

## What Organizers Set Up

Before speakers can use the portal, organizers must:

1. **Add the speaker** to the event's speaker pool (Phase A: Setup)
2. **Send the invitation** — triggers the invitation email with magic links (Phase B: Outreach)
3. **Set deadlines** — response deadline and content submission deadline (configured when sending)

Once sent, the portal handles the rest automatically. See [Phase B: Outreach](../workflow/phase-b-outreach.md) for the organizer workflow.

## Automated Deadline Reminders

The system sends automatic reminder emails when speaker deadlines approach:

| Tier | Timing | Tone |
|------|--------|------|
| **Tier 1 — Friendly** | 14 days before deadline | Informational |
| **Tier 2 — Urgent** | 7 days before deadline | Action required |
| **Tier 3 — Final** | 3 days before deadline | Final warning |

Reminders are skipped if the speaker has already responded or submitted content. After Tier 3, an in-app notification is created for the organizer.

Organizers can disable automated reminders per speaker, or trigger a manual reminder at any time from the Phase B Kanban board.

## Accessibility & Language

- **WCAG 2.1 AA** compliant (keyboard navigation, ARIA labels, colour contrast, 44px touch targets)
- **German and English** — all portal screens and emails available in both languages
- **Mobile responsive** — minimum 375px viewport

## Related

- [Phase B: Outreach →](../workflow/phase-b-outreach.md) — How organizers manage the speaker pool
- [Phase C: Quality Review →](../workflow/phase-c-quality.md) — Reviewing submitted content
- [Speaker Management →](../entity-management/speakers.md) — Organizer view of speaker profiles
