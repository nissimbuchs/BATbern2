# Attendee Experience

> Give attendees a voice and a return channel — right-sized contribution features that compose onto machinery you already run

> **Last Updated**: 2026-06-13

<span class="feature-status implemented">Implemented</span> — Epic 7 (Stories 7.1–7.6) — Story 7.7 (curated thank-you marquee) in development.

## Overview

For a community of ~200 practitioners that meets ~3 times a year, the old idea of personal dashboards, bookmarks, a recommendation engine, and a mobile PWA was always too much machinery for too little recurring engagement. **Epic 7 is the right-sized replacement.** It adds a small set of organizer-voted features whose single thesis is this:

> The login was never about gating *consumption* — content is, and stays, free to everyone. The login exists to gate *participation*, and to make a contributor **reachable** so a suggestion can become a dialogue.

Anonymous visitors lose nothing. Logged-in attendees gain a **voice** (they can shape future topics and even put themselves on stage) and a **return channel** (you, the organizer, can reply, follow up, and close the loop).

## The contribution loop

Everything in this section follows the same low-effort shape:

```
Attendees contribute  →  Organizers triage with near-zero extra effort  →  the loop compounds
```

Attendees suggest topics, raise their hand to speak, ask the questions the apéro didn't have time for, and thank the people who run BATbern. You review and approve at most — there is **no new standing task** and **no always-on feed** to keep alive. Each feature self-runs once built, and every contribution lands in a surface you already use (the topic-suggestion admin UI, the speaker kanban, the event task list, the public archive).

<div class="alert info">
ℹ️ <strong>No new backend service.</strong> All six features compose onto existing services (<code>event-management-service</code> and <code>partner-coordination-service</code>) and existing infrastructure (auto-publishing, the SES newsletter sender, the ShedLock scheduler, the public archive). Nothing new to operate.
</div>

## How attendees sign in

Contributions are **login-gated** and ride on the existing **"Continue with Google"** sign-in (Epic 12). A first-time contributor is provisioned just-in-time with the default **`ATTENDEE`** role — which is sufficient for every contribution endpoint in this section. No invitation, no separate account, no per-role login.

The one deliberate exception is **Thank-the-Organizers**, which is anonymous-allowed (friction hurts most there) and protected by a Turnstile + rate-limit abuse guard instead of a login.

## Guardrails

These features were chosen against three guardrails the organizer committee set, and they stay inside them:

- **The physical apéro stays sacred.** The post-event Q&A (7.5) is the *digital afterglow* — it never digitizes or intrudes on the in-person ritual.
- **Cadence-match.** Only event-triggered communications — no always-on feed that a thrice-yearly rhythm would make look abandoned.
- **Login = reachability.** Gating is there to *enable* dialogue, never to restrict access to content.

## Features

| Feature | Story | What attendees do | Where you triage |
|---------|-------|-------------------|------------------|
| [Topics From the Floor](topics-from-the-floor.md) | 7.1 | Suggest a future event topic (title + rationale) | Existing topic-suggestion admin UI (community badge) |
| [Speaker Self-Nomination](speaker-self-nomination.md) | 7.2 | Raise a hand with a session title + abstract | Existing 8-state speaker workflow (kanban) |
| [Slides-Online Email](slides-online-email.md) | 7.3 | (Receive) the one post-event email they'll open | Auto-created event task → one-click send |
| [Thank-the-Organizers](thank-the-organizers.md) | 7.4 | One-click thank-you, optional note | Appreciation surface (count + notes) |
| [Post-Event Q&A](post-event-qna.md) | 7.5 | Ask & answer per-session questions for ~14 days | Takedown / extend / close-early; freezes into archive |
| [Event History](event-history.md) | 7.6 | See the events they registered for / attended | (Attendee self-service — no triage) |

## Related

- [Topic Voting →](../partner-portal/topic-voting.md) — community topics share the partner topic pool
- [Phase B: Outreach →](../workflow/phase-b-outreach.md) — where self-nominated speakers are triaged
- [Task Templates →](../administration/task-templates.md) — where the slides-online task is seeded
- [Workflow System Overview →](../workflow/README.md)
