# Post-Event Q&A — "The Apéro Continues"

> A time-boxed per-session Q&A after each event — the digital afterglow that freezes into the archive forever

> **Last Updated**: 2026-06-13

<span class="feature-status implemented">Implemented</span> — Epic 7.5

## Overview

The open questions that the apéro never had time for currently have nowhere to live. **The Apéro Continues** gives each session a time-boxed Q&A after the event: logged-in attendees post open questions and answer one another asynchronously, and when the window closes the whole thread **freezes into that session's archive page permanently**.

<div class="alert info">
ℹ️ <strong>This is the digital afterglow — not the apéro itself.</strong> It explicitly does <em>not</em> digitize or intrude on the physical apéro, which stays unstructured. It is also <em>not</em> an always-on forum — the time-box is the point; an always-on feed would die at this cadence.
</div>

## The window

The Q&A window **opens automatically when an event completes** and stays open for a **default of 14 days**. You can **extend it or close it early** if you wish. The close is handled automatically by the platform's scheduler — there is nothing to remember.

```
Event completes (EVENT_COMPLETED)
        │  → a Q&A window opens for each session  (closes_at = +14 days default)
        ▼
Window OPEN
        │  logged-in attendees post questions / answers (attendees + speakers)
        │  organizers: take down a post, extend, or close early
        ▼
Scheduler closes the window at closes_at
        │  → status FROZEN (read-only)
        ▼
Frozen Q&A attached to the session's public archive page — permanent
```

## Who can do what

| Action | Who | When |
|--------|-----|------|
| Post a question / answer | Logged-in attendees and speakers | While the window is **open** |
| Read the Q&A | **Anyone** (public) — including the frozen archive | Always |
| Take down a post | Organizers | While open — removed posts are dropped entirely from the thread |
| Extend / close early | Organizers | While open |

- **Posting is login-gated.** An anonymous attempt to post a question or answer is rejected with **401**.
- **Reading is public.** Browsing the frozen Q&A in the archive needs no login.

## Moderation

The Q&A ships on a deliberately simple moderation floor: **login accountability + organizer takedown**. Every post is attributable to a logged-in user, and you can **remove any post**. Removed posts are dropped entirely from the thread (not shown as tombstones); removing a question also drops its answers. Posts stay soft-deleted in the database for audit. Changes (takedown, extend, close-early) take effect immediately. Agent-assisted curation is a possible later layer — it is not a launch dependency.

## When the window closes

When the close time passes, the scheduler **freezes the Q&A to read-only** — no further posts are accepted — and the thread is **attached permanently to that session's archive page**, enriching the public record of the event.

## What's in scope (and what isn't)

| In scope | Not in scope |
|----------|--------------|
| ~14-day per-session window, organizer-overridable | An always-on forum |
| Logged-in post/answer; public read; organizer takedown | Anything touching/digitizing the in-person apéro |
| Auto open on completion, auto close via scheduler | Agent-assisted moderation / summarisation (a later layer) |
| Frozen thread attached to the session archive | |

## Related

- [Thank the Organizers →](thank-the-organizers.md) — the companion post-event touchpoint
- [Attendee Experience Overview →](README.md)
