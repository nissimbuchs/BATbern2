# Media & Content `[IMPLEMENTED]`

> **Last Updated:** 2026-06-13

## Overview

Beyond logos and speaker materials, the platform provides a set of media and content-creation features delivered in Epic 10. These let organizers enrich each event with real photos, teaser imagery, AI-assisted copy, and a live moderator presentation — all from the event detail page.

## Implementation Status

| Feature | Status | Details |
|---------|--------|---------|
| Event Photos Gallery (Story 10.21) | ✅ `[IMPLEMENTED]` | Per-event photo upload/management; real photos on the homepage + archive |
| Event Teaser Images (Story 10.22) | ✅ `[IMPLEMENTED]` | Teaser images shown as full-screen slides on the moderator presentation page |
| AI-Assisted Event Content (Story 10.16) | ✅ `[IMPLEMENTED]` | AI event description, theme image, and abstract analysis (feature-flagged) |
| Moderator Presentation Page (Story 10.8a/b) | ✅ `[IMPLEMENTED]` | Fullscreen, remote-driven presenter page with live data and animations |

---

## Event Photos Gallery (Story 10.21)

Organizers upload and manage photos for each event directly from the event detail page's **Photos** tab. Uploads use the standard 3-phase presigned-URL flow (request URL → PUT to S3 → confirm), and any photo can be deleted (DB record and S3 object are both removed after a confirmation dialog).

The photos are also surfaced publicly:

- **Homepage marquee** — the homepage shows real, recent event photos (sampled from the last few events), replacing the previous placeholder testimonials; it falls back to testimonials if too few photos exist.
- **Archive pages** — each archived event's detail page shows that event's own photos in a marquee below the sessions (hidden if the event has no photos).

Public photo listing endpoints (`GET /api/v1/events/{eventCode}/photos` and `GET /api/v1/events/recent-photos`) require no authentication.

## Event Teaser Images (Story 10.22)

Organizers upload one or more teaser images for an event from the event settings tab (presigned-URL upload, with a thumbnail gallery and per-image delete; **max 10 images** per event). On the [moderator presentation page](#moderator-presentation-page-story-108ab), each teaser image renders as its own full-screen slide, inserted in order between the topic-reveal slide and the agenda-preview slide — a visual mood-setter sequence. When an event has no teaser images, the presentation renders exactly as before (no blank slides).

## AI-Assisted Event Content (Story 10.16)

When enabled (gated by the `AI_ENABLED` feature flag plus a valid OpenAI key), organizers get AI assistance for producing event content in minutes:

- **Event description generation** — a polished German event description (≈150–200 words) from the topic title.
- **Theme image generation** — a themed event image generated and uploaded to S3.
- **Abstract analysis** — scores a speaker's abstract on two BATbern-specific criteria (absence of product promotion; presence of real-world lessons learned), with feedback and an optional shortened abstract.

When the flag is off (the default), all AI endpoints return `503` and the AI buttons are hidden; if an AI call fails, the UI prompts the organizer to write the content manually. Generation results are cached to avoid duplicate API calls, and each attempt is logged for cost monitoring.

## Moderator Presentation Page (Story 10.8a/b)

A single fullscreen webpage (e.g., `/present/BATbern57`) that a moderator opens on the projector laptop and drives with a presentation remote — guiding the audience through the evening without ever touching PowerPoint, with content served live from the platform.

- **No authentication** — loads all event data publicly; navigate with `→` / `←` / `PageDown` / `PageUp` / `Space`; `F` (or `F11`) toggles fullscreen.
- **Sections** — Welcome, About (Verein purpose + partner count), Committee (active organizers), Topic Reveal, teaser-image slides, Agenda Preview, per-speaker sections, Break, Agenda Recap, Upcoming Events, and Apéro. Structural sessions (`moderation`, `networking`) are excluded.
- **Sidebar** — an agenda sidebar appears for the speaker sections and tracks the moderator's navigation position.
- **Animations (Story 10.8b)** — a full Framer Motion layer adds the agenda↔sidebar FLIP transition, section slide transitions, a Ken Burns background zoom on the topic image, committee-card stagger, and break/apéro visual effects.

---

## Related Features

- **[File Uploads](file-uploads.md)** — The presigned-URL upload mechanism behind photos and teaser images
- **[Notification System](notifications.md)** — Newsletter & email content, also managed via admin email templates
- **[Event Management](../entity-management/events.md)** — Event detail page that hosts these tabs

---

**Back to Features**: Return to [Features Overview](README.md) →
