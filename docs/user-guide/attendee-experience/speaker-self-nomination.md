# Speaker Self-Nomination — "I Could Speak on That"

> Once an event's topic is set and published, logged-in attendees can raise a hand with a session title and abstract — turning the speaker pipeline from organizer-sourced-only into pull-and-push

> **Last Updated**: 2026-06-13

<span class="feature-status implemented">Implemented</span> — Epic 7.2

## Overview

When the next event's topic is known and published, a logged-in attendee can put themselves forward as a speaker: a **session title** and an **abstract**. They don't need to know an organizer — the pipeline now pulls candidates in as well as pushing invitations out.

Self-nominations land in your speaker pool **raw** (no agent pre-screen) and are triaged exactly like organizer-sourced candidates through the [8-state speaker workflow](../workflow/phase-b-outreach.md).

## When the window is open

The self-nomination window **opens as soon as the event's topic is set and the event is published** — the earliest public signal, which maximizes the time attendees have to raise a hand. The window **closes when the event starts**.

Attempting to self-nominate for an event whose topic is not yet set, or which is not published, is rejected (409/422) and creates no entry.

## How it works

```
Logged-in attendee (event topic set + published)
        │  POST  { sessionTitle, abstract }   (ATTENDEE role, login-gated)
        ▼
event-management-service
        │  creates a speaker_pool entry at status IDENTIFIED
        │  via SpeakerWorkflowService.transition
        │  tagged source = self_nomination, proposer's username recorded
        │  abstract stored raw (no agent pre-screen)
        ▼
Existing organizer speaker pool / kanban
        self-nomination chip shown; triaged like any candidate
```

## A self-nomination enters at IDENTIFIED — never READY

This is the most important integrity rule. A self-nomination creates a `speaker_pool` entry at the **`IDENTIFIED`** state — the entry point of the workflow. It is written **only** via `SpeakerWorkflowService.transition`, the sole status writer (ADR-009).

It does **not**:

- create a Cognito user,
- grant the `SPEAKER` role, or
- create a `session_users` row.

All of that provisioning happens **only when you, an organizer, promote the candidate to `READY`** — the same provisioning gate, reached via the same `promote` path, that every organizer-sourced candidate goes through. Nothing about the existing workflow changes, and there is no new state in the machine.

## For organizers

In the **speaker pool / kanban**, self-nominations appear alongside organizer-sourced candidates, marked with a **self-nomination chip** so you can see their provenance at a glance. You triage them through the existing 8-state workflow — review the abstract, advance or drop the candidate, and promote to `READY` (with provisioning) only when you decide to put them on stage.

See [Phase B: Outreach →](../workflow/phase-b-outreach.md) for the speaker workflow and the promote path.

## What's in scope (and what isn't)

| In scope | Not in scope |
|----------|--------------|
| Logged-in attendee submits session title + abstract | Any provisioning at nomination time (Cognito / SPEAKER / `session_users`) |
| Entry at `IDENTIFIED`, `source = self_nomination` | Reserving a slot or guaranteeing stage time |
| Triage via the existing 8-state workflow | A new state in the workflow machine |
| Self-nomination chip in the kanban | An agent pre-screen of the abstract (a possible later layer) |

## Behaviour notes

- **Login-gated.** An anonymous attempt to self-nominate is rejected with **401**.
- **Raw triage.** The abstract is stored as-is for the MVP; agent scoring may be added later only if nomination volume warrants it.

## Related

- [Phase B: Outreach →](../workflow/phase-b-outreach.md) — the 8-state speaker workflow and promotion
- [Topics From the Floor →](topics-from-the-floor.md) — the companion contribution surface
- [Attendee Experience Overview →](README.md)
