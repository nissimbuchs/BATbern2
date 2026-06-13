# Topics From the Floor

> Logged-in attendees suggest future event topics — turning the 200 practitioners in the room into a sensing network for what BATbern should cover next

> **Last Updated**: 2026-06-13

<span class="feature-status implemented">Implemented</span> — Epic 7.1

## Overview

Partners have always had a say in future-event content through [Topic Voting](../partner-portal/topic-voting.md). **Topics From the Floor** opens the same channel to the wider community: any logged-in attendee can suggest a topic (a title and a short rationale) for a future event.

Crucially, this is **login-gated** by design. The point is not to restrict — it is to give you a **return channel**. When an attendee suggests a topic, you know who they are and can ask them *why*, or tell them when "their" topic becomes the next event.

## How it works

```
Logged-in attendee  →  "Suggest a topic" form (title + rationale)
        │  POST  (ATTENDEE role, login-gated)
        ▼
partner-coordination-service
        │  writes a topic_suggestions row, tagged source = community,
        │  with the suggester's username recorded
        ▼
Existing organizer topic-suggestion admin UI
        community + partner topics side by side, source-tagged
```

Attendee suggestions flow into the **existing** `topic_suggestions` pool — the very same pool partners suggest into and vote on. They are tagged **`source = community`** (partner suggestions are `source = partner`) so you can tell at a glance where each topic came from.

## For organizers

You triage community topics in the **existing topic-suggestion admin UI** — there is no new admin surface and no separate community-topic queue. Community-sourced suggestions appear alongside partner ones, distinguished by a **community badge** (`source = community`), and you handle them exactly as you handle partner suggestions: review, select, or decline.

Existing partner-suggestion behaviour is unchanged.

<div class="alert info">
ℹ️ <strong>One shared pool.</strong> Because community and partner topics live in the same pool, partners vote on community-sourced topics too. See <a href="../partner-portal/topic-voting.md">Topic Voting</a> for the voting and status mechanics.
</div>

## What's in scope (and what isn't)

| In scope | Not in scope |
|----------|--------------|
| Logged-in attendees suggest a topic (title + rationale) | Attendee *voting* on topics |
| Suggestions land in the existing pool, `source = community` | A separate community-topic moderation queue |
| Triage in the existing admin UI with a community badge | Notifying an attendee when their topic becomes an event |

## Behaviour notes

- **Login-gated.** An anonymous (not-logged-in) attempt to submit a topic is rejected with **401** — enforced in both the api-gateway and the owning service's security configuration.
- **Validation.** An empty title, or a rationale exceeding the maximum length, returns **400** and no row is created.

## Related

- [Topic Voting →](../partner-portal/topic-voting.md) — the shared topic pool and partner voting
- [Speaker Self-Nomination →](speaker-self-nomination.md) — the companion contribution surface
- [Attendee Experience Overview →](README.md)
