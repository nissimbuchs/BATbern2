# Speaker Coordination API

**Last Updated**: 2026-11-20

Speaker pool per event, the 8-state speaker workflow (ADR-009), promotion to READY with account
provisioning, invitations, outreach history, content review and the speaker portal.

**Served by `event-management-service`.** The speaker logic lives in EMS (ADR-014); the former
`speaker-coordination-service` stub was removed by Story 17.2 and its stack deleted in
production on 2026-11-10. A speaker is a user with the SPEAKER role (ADR-009, ADR-004); every
speaker-portal operation needs a Cognito Bearer token and `hasRole('SPEAKER')`.

The specs are the source of truth. Shared conventions: [04-api-core.md](./04-api-core.md).

| Spec | Covers |
|---|---|
| [`event-speakers-api.openapi.yml`](../api/event-speakers-api.openapi.yml) | Speaker pool, self-nomination, promote, status and history, content review queue, invitations, outreach, speaker portal (`/speaker-portal/**`) |
| [`event-sessions-api.openapi.yml`](../api/event-sessions-api.openapi.yml) | Assigning speakers to sessions, confirm and decline, session materials |
| [`event-ai-api.openapi.yml`](../api/event-ai-api.openapi.yml) | Abstract analysis of a speaker's submission |
| [`uploads-api.openapi.yml`](../api/uploads-api.openapi.yml) | Upload of session material (purpose `session-material`) |

Workflow states and transitions: [ADR-009](./ADR-009-unified-speaker-workflow.md) and
[06a-workflow-state-machines.md](./06a-workflow-state-machines.md).
