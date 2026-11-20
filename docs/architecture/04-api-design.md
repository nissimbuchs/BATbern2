# API Design - Index

**Last Updated**: 2026-11-20

The BATbern API contract is the set of OpenAPI specs in `docs/api/`. They are the source of
truth: services and frontend generate their types from them (ADR-006), and CI checks the
frontend's API-client calls against them (`check-api-paths.ts`). The former narrative pages had drifted from the code and were
retired by owner decision 2026-11-15 (#1212); each page below is now a short index of the specs
for its area. Git history holds the old text.

## Pages

| Page | Area |
|---|---|
| [04-api-core.md](./04-api-core.md) | Conventions every API shares: base URL, auth, headers, rate limits, errors, pagination |
| [04-api-event-management.md](./04-api-event-management.md) | Events, workflow, sessions, registrations, tasks, mail, newsletter, media, AI, analytics, topics |
| [04-api-speaker-coordination.md](./04-api-speaker-coordination.md) | Speaker pool, invitations, speaker portal (served by EMS, ADR-014) |
| [04-api-attendee-experience.md](./04-api-attendee-experience.md) | Registration, attendee portal, Q&A, thanks (served by EMS, ADR-014) |
| [04-api-company-management.md](./04-api-company-management.md) | Companies, logos, company merge (CUMS) |
| [04-api-user-management.md](./04-api-user-management.md) | Users, roles, additional emails, profile merge, Watch pairing (CUMS) |
| [04-api-partner-coordination.md](./04-api-partner-coordination.md) | Partners, contacts, notes, analytics, topic backlog, partner meetings (PCS) |

## Specs by serving service

Taken from the OpenAPI generator tasks in each service's `build.gradle`. A service that only
consumes another service's spec (for its client types) is not listed.

| Service | Specs |
|---|---|
| event-management-service (EMS) | `events-core-api`, `event-sessions-api`, `event-speakers-api`, `event-registrations-api`, `event-tasks-api`, `event-mail-api`, `event-newsletter-api`, `event-notifications-api`, `event-media-api`, `event-ai-api`, `event-analytics-api`, `event-app-settings-api`, `event-watch-api`, `topics-api`, `uploads-api` (its purposes), `internal/ems-internal` |
| company-user-management-service (CUMS) | `companies-api`, `users-api`, `uploads-api` (its purposes), `internal/cums-internal` |
| partner-coordination-service (PCS) | `partners-api`, `partner-meetings-api`, `internal/pcs-internal` (no operation of its own) |
| API gateway | `gateway-config-api` (`GET /config`, runtime configuration for the frontend) |

`_shared.openapi.yml` holds the shared components (`ErrorResponse`, pagination). How the folder
is organised, linted and routed: [`docs/api/README.md`](../api/README.md).
