# Event Management API

**Last Updated**: 2026-11-20

Everything around an event, served by `event-management-service` (EMS): the event and its
8-state workflow, sessions and agenda, registrations and waitlist, tasks, the Mail Center,
newsletter, notifications, photos, AI assist, analytics, topics and the on-site Watch view.
Speaker and attendee operations are also EMS; see
[04-api-speaker-coordination.md](./04-api-speaker-coordination.md) and
[04-api-attendee-experience.md](./04-api-attendee-experience.md).

The specs are the source of truth. Shared conventions: [04-api-core.md](./04-api-core.md).

| Spec | Covers |
|---|---|
| [`events-core-api.openapi.yml`](../api/events-core-api.openapi.yml) | Event CRUD, public event reads, workflow status and transitions, event types, teaser images, phase publishing, thanks, event Q&A switch, admin settings, attendee-portal dashboard |
| [`event-sessions-api.openapi.yml`](../api/event-sessions-api.openapi.yml) | Sessions, agenda configuration, timetable, slot assignment and timing, session speakers, session materials, per-session Q&A |
| [`event-speakers-api.openapi.yml`](../api/event-speakers-api.openapi.yml) | Speaker pool, nominations, status workflow, review, invitations, outreach, speaker portal |
| [`event-registrations-api.openapi.yml`](../api/event-registrations-api.openapi.yml) | Registrations, participants and their XLSX/DOCX exports, waitlist promotion, distribution lists, deregistration |
| [`event-tasks-api.openapi.yml`](../api/event-tasks-api.openapi.yml) | Task templates and event tasks |
| [`event-mail-api.openapi.yml`](../api/event-mail-api.openapi.yml) | Mail Center: outbound mail log, conversations, replies, the shared composer |
| [`event-newsletter-api.openapi.yml`](../api/event-newsletter-api.openapi.yml) | Newsletter subscription and subscribers, email templates |
| [`event-notifications-api.openapi.yml`](../api/event-notifications-api.openapi.yml) | The caller's in-app notification inbox |
| [`event-media-api.openapi.yml`](../api/event-media-api.openapi.yml) | Event photo gallery (uploads go through `uploads-api`) |
| [`event-ai-api.openapi.yml`](../api/event-ai-api.openapi.yml) | AI assist (description, theme image, abstract analysis) and AI prompt administration |
| [`event-analytics-api.openapi.yml`](../api/event-analytics-api.openapi.yml) | Attendance, company, topic and per-event analytics |
| [`event-app-settings-api.openapi.yml`](../api/event-app-settings-api.openapi.yml) | Public runtime feature flags and moderator presentation settings |
| [`event-watch-api.openapi.yml`](../api/event-watch-api.openapi.yml) | Live timing and speaker arrivals for the Watch view |
| [`topics-api.openapi.yml`](../api/topics-api.openapi.yml) | Topics, similarity, usage history, event topics |
| [`uploads-api.openapi.yml`](../api/uploads-api.openapi.yml) | Upload contract; EMS owns the purposes `event-theme-image`, `event-photo`, `teaser-image`, `session-material` |
| [`internal/ems-internal.openapi.yml`](../api/internal/ems-internal.openapi.yml) | Service-to-service: event schedule, attendance summary, mail recording, calendar replies |

The event workflow states and transitions: [06a-workflow-state-machines.md](./06a-workflow-state-machines.md).
