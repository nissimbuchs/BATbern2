# The Slides Are Online — Post-Event Email

> The one post-event email attendees will actually open — sent from an auto-created task once the materials are up

> **Last Updated**: 2026-06-13

<span class="feature-status implemented">Implemented</span> — Epic 7.3

## Overview

After an event, the slides go online — but the people who registered have no reason to come back and look. The **slides-online email** is the gentle nudge that turns three website visits a year into six, without asking any more commitment of the attendee. It is the single post-event message worth sending.

This is deliberately **event-anchored, never a recurring digest** — it fires once per event, when there is something concrete (the materials) to point at.

## How it works

An organizer task is **auto-created** with every event's task set:

> **Newsletter: Slides Are Online** — due ~1 day after the event (about two weeks out in the seeded template).

This uses the existing task-template mechanism — no engine change. When you open that task, you send the email in one step.

```
Event task set auto-created  →  "Newsletter: Slides Are Online" task
        │  (existing task-template seed — no engine change)
        ▼
Organizer opens the task  →  sends the slides-online email
        │  recipients = this event's active registrants
        ▼
Existing newsletter sender (paged, throttled, audited, double-send guarded)
        │  per recipient: skip opt-outs; pick locale
        ▼
AWS SES  →  DE / EN "slides-online" template
```

## For organizers

1. Open the **"Newsletter: Slides Are Online"** task on the event.
2. Send. The email goes to the event's **active registrants** — those with status `registered`, `confirmed`, or `attended` (the mail fires after the event, so attendees are included; waitlisted and cancelled registrants are not). It is not sent to the global newsletter-subscriber pool; the recipients are this event's registrants only.
3. The send reuses the existing newsletter-send infrastructure, so it is paged, throttled, and audited like any other newsletter.

<div class="alert info">
ℹ️ <strong>Send-once, guarded.</strong> A double-send guard prevents a second slides-online send for the same event. If you open the task again after sending, the guard blocks a repeat send.
</div>

## Localization and opt-out

| Recipient's language preference | Email language |
|---------------------------------|----------------|
| Starts with `de` (e.g. `de`, `gsw-BE`) | German |
| `en` | English |
| Anything else | German (fallback) |

The template exists in **German and English only** — consistent with the platform's email-template localization rule. Recipients who have **globally opted out** of email are excluded from the send.

## What's in scope (and what isn't)

| In scope | Not in scope |
|----------|--------------|
| Auto-created task + one-click manual send | An automated materials-publish trigger (none exists) |
| Send to this event's active registrants | Sending to the global newsletter-subscriber pool |
| DE/EN template, per-recipient locale, opt-out respected | Per-session granular notifications |
| Double-send guard | A recurring post-event digest |

## Behaviour notes

- **Transient SES failures** for one recipient are logged/marked failed without blocking the rest of the send.

## Related

- [Task Templates →](../administration/task-templates.md) — where the slides-online task is seeded into the event task set
- [Email Templates →](../administration/email-templates.md) — managing the DE/EN template content
- [Attendee Experience Overview →](README.md)
