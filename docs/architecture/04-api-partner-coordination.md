# Partner Coordination API

**Last Updated**: 2026-11-20

Partners, addressed by company name (ADR-003): partnership, contacts, organizer notes, partner
analytics and XLSX export, the topic backlog with partner votes, attendee topic suggestions,
and partner meetings with ICS invitations and RSVPs. Served by
`partner-coordination-service` (PCS).

The specs are the source of truth. Shared conventions: [04-api-core.md](./04-api-core.md).

| Spec | Covers |
|---|---|
| [`partners-api.openapi.yml`](../api/partners-api.openapi.yml) | Partner CRUD and reactivation, contacts, `/partners/me`, statistics, notes, analytics dashboard and export, topic backlog (suggest, vote, status), `POST /attendees/topics` |
| [`partner-meetings-api.openapi.yml`](../api/partner-meetings-api.openapi.yml) | Partner meetings, sending the invitation, RSVPs |
| [`internal/pcs-internal.openapi.yml`](../api/internal/pcs-internal.openapi.yml) | No operation of its own; PCS implements the calendar-reply contract (`ems-internal`) and the user- and company-reference contracts (`cums-internal`) |
