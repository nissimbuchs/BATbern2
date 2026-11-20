# Company Management API

**Last Updated**: 2026-11-20

Companies, addressed by name (ADR-003): CRUD, search, verification, duplicate candidates,
company merge, get-or-create, and the company logo. Served by
`company-user-management-service` (CUMS).

The specs are the source of truth. Shared conventions: [04-api-core.md](./04-api-core.md).

| Spec | Covers |
|---|---|
| [`companies-api.openapi.yml`](../api/companies-api.openapi.yml) | Company CRUD on `/companies/{name}`, search, verify, merge preview and merge, duplicate candidates and dismissals, get-or-create |
| [`uploads-api.openapi.yml`](../api/uploads-api.openapi.yml) | Company logo upload (purpose `company-logo`); the former `/logos/*` operations are gone |
| [`internal/cums-internal.openapi.yml`](../api/internal/cums-internal.openapi.yml) | Service-to-service: company reads and search, the company-reference contract (preview, merge, counts) |
