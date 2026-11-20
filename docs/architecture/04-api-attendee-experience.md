# Attendee Experience API

**Last Updated**: 2026-11-20

Registration, the attendee portal, the public event pages, per-session Q&A, thanks to the
organizers, newsletter subscription and topic suggestions from the floor.

**Served by `event-management-service`.** The attendee logic lives in EMS (ADR-014); the former
`attendee-experience-service` stub was removed by Story 17.2 and its stack deleted in
production on 2026-11-10. One exception: attendee topic suggestions
(`POST /attendees/topics`) are served by `partner-coordination-service`, which owns the topic
backlog.

The specs are the source of truth. Shared conventions: [04-api-core.md](./04-api-core.md).

| Spec | Covers |
|---|---|
| [`event-registrations-api.openapi.yml`](../api/event-registrations-api.openapi.yml) | Registration, confirmation, own registration, waitlist, deregistration |
| [`events-core-api.openapi.yml`](../api/events-core-api.openapi.yml) | Public event reads, attendee-portal dashboard, thanks, featured thanks |
| [`event-sessions-api.openapi.yml`](../api/event-sessions-api.openapi.yml) | Per-session Q&A posts |
| [`event-newsletter-api.openapi.yml`](../api/event-newsletter-api.openapi.yml) | Newsletter subscription and unsubscribe |
| [`event-media-api.openapi.yml`](../api/event-media-api.openapi.yml) | Public event photos |
| [`partners-api.openapi.yml`](../api/partners-api.openapi.yml) | `POST /attendees/topics` (served by PCS) |
