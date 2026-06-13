# Speaker Self-Service Portal

> Enable speakers to respond to invitations, submit content, and track their participation — using their BATbern account

<span class="feature-status implemented">Implemented</span> — Epic 6 (Stories 6.0–6.5) + Epic 11 Cognito auth refactor (Stories 11.E.3, 11.F.1, 11.G.1)

**Last Updated:** 2026-06-13

## Overview

The Speaker Self-Service Portal lets invited speakers manage their participation through the **same AWS Cognito account** they use for the rest of the BATbern app — there is no separate speaker login, and no magic-link tokens. Speakers sign in with **email + password** or **"Continue with Google"**, then accept/decline invitations, submit their presentation content, upload materials, manage their company's public footprint, and view their upcoming and past engagements from a single dashboard.

**For organizers**, the portal automates the most time-consuming parts of speaker coordination: chasing responses, collecting abstracts, and sending deadline reminders.

<div class="alert info">
ℹ️ <strong>Authentication changed in Epic 11.</strong> The original magic-link login (Epic 6) was fully removed (Story 11.F.1). Speakers now authenticate via AWS Cognito like every other role. A Cognito account is auto-provisioned the moment an organizer promotes a speaker to the <code>READY</code> state, and an invitation email with login credentials (DE/EN) is sent. Any older description of "magic links" is stale.
</div>

## How It Works

```
Organizer identifies the real speaker and promotes them to READY
         ↓
Cognito account auto-provisioned (SPEAKER role granted)
         ↓
Organizer sends the invitation  (status → INVITED)
         ↓
Speaker receives email with:
  • A login link to www.batbern.ch
  • A temporary password (first-login password change required)
         ↓
Speaker signs in (email + password, OR "Continue with Google")
         ↓
Speaker lands on /speaker-portal/dashboard, sees their event(s)
         ↓
Speaker Accepts the invitation         (status → ACCEPTED)
         ↓
Speaker submits title, abstract, materials  (status → CONTENT_SUBMITTED)
         ↓
Moderator reviews content (Phase C Quality Review)  (status → QUALITY_REVIEWED)
         ↓
Speaker is publishable once content is reviewed AND a slot is assigned
```

## Authentication (Cognito)

Speakers log in exactly like organizers, partners, and attendees:

| Method | Notes |
|--------|-------|
| **Email + password** | Credentials issued in the invitation email. Cognito forces a password change on first login (`FORCE_CHANGE_PASSWORD → CONFIRMED`). |
| **"Continue with Google"** | If the speaker's Google account matches their invited email, federated login (Epic 12) links transparently. |

- **Account provisioning happens at `CONTACTED → READY`** — the `/promote` transition creates (or links) the User, provisions the Cognito user, grants the `SPEAKER` role, and creates the primary session row.
- **No tokens in URLs.** Portal pages live behind the standard logged-in session (`<SpeakerRoute>` guard). The event a page acts on is identified by an `eventCode` in the URL path (e.g. `/speaker-portal/events/{eventCode}/content`), never a `?token=` query parameter.
- **Multi-role users** (e.g. speaker who is also an organizer) get a single session with grouped navigation — the nav menu shows a "Speaker" section and an "Organizer" section, and switching between portals requires no re-authentication.

## Speaker Portal Sections

| Section | What Speakers Can Do |
|---------|---------------------|
| [Invitation & Response](invitation-response.md) | Accept or decline an invitation |
| [Content Submission](content-submission.md) | Submit title, abstract, presentation file |
| [Speaker Dashboard](dashboard.md) | View upcoming engagements, deadlines, past events; manage profile + company info |

## The 8-State Speaker Workflow

The portal surfaces the speaker's current state in the unified **8-state workflow** (ADR-009). Organizers drive the early states; speakers act on the portal-facing states.

| State | Meaning | Who acts |
|-------|---------|----------|
| `IDENTIFIED` | Name on the brainstorm list; no account yet | Organizer |
| `CONTACTED` | Organizer reaching out to figure out who will actually speak | Organizer |
| `READY` | Real speaker identified — Cognito account + SPEAKER role provisioned here | Organizer (`/promote`) |
| `INVITED` | Invitation email (login link + temp password) sent | Organizer → Speaker |
| `ACCEPTED` | Speaker committed via the portal | **Speaker** |
| `CONTENT_SUBMITTED` | Title + abstract submitted | **Speaker** (or organizer on behalf) |
| `QUALITY_REVIEWED` | Moderator approved content (quasi-terminal happy state) | Moderator |
| `DECLINED` | Single terminal "not happening" state, reachable from any non-terminal state | Speaker or Organizer |

> **Note on "Confirmed":** the legacy `CONFIRMED` state was removed in Epic 11. A speaker is **publishable** when `QUALITY_REVIEWED` AND a slot is assigned (`is_publishable`). The dashboard surfaces this as "Confirmed / Publishable".

## What Organizers Set Up

Before speakers can use the portal, organizers must:

1. **Promote the speaker to `READY`** — provisions the Cognito account and grants the SPEAKER role (Phase A/B).
2. **Send the invitation** — triggers the invitation email with login credentials (status → `INVITED`, Phase B: Outreach).
3. **Set deadlines** — response deadline and content submission deadline (configured when sending).

Once sent, the portal handles the rest. See [Phase B: Outreach](../workflow/phase-b-outreach.md) for the organizer workflow.

## Automated Deadline Reminders

The system sends automatic reminder emails when speaker deadlines approach:

| Tier | Timing | Tone |
|------|--------|------|
| **Tier 1 — Friendly** | 14 days before deadline | Informational |
| **Tier 2 — Urgent** | 7 days before deadline | Action required |
| **Tier 3 — Final** | 3 days before deadline | Final warning |

Reminders are skipped if the speaker has already responded or submitted content. After Tier 3, an in-app notification is created for the organizer.

Organizers can disable automated reminders per speaker, or trigger a manual reminder at any time from the Phase B Kanban board. Reminder emails link to the standard login page — the speaker signs in with their existing Cognito account.

## Accessibility & Language

- **WCAG 2.1 AA** compliant (keyboard navigation, ARIA labels, colour contrast, 44px touch targets) — the dashboard passed QA at 98/100.
- **German and English** — all portal screens and emails available in both languages (the full UI is localised in all 10 supported locales).
- **Mobile responsive** — minimum 375px viewport.

## Related

- [Phase B: Outreach →](../workflow/phase-b-outreach.md) — How organizers manage the speaker pool
- [Phase C: Quality Review →](../workflow/phase-c-quality.md) — Reviewing submitted content
- [Speaker Management →](../entity-management/speakers.md) — Organizer view of speaker profiles
