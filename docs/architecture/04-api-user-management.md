# User Management API

**Last Updated**: 2026-11-20

User profiles, addressed by username (ADR-003, one profile per person per ADR-007): own profile
and settings, search, roles, profile picture, additional email addresses, duplicate candidates,
profile merge and self-claims, company logo requests, Cognito reconciliation, and Watch pairing.
Served by `company-user-management-service` (CUMS).

The specs are the source of truth. Shared conventions: [04-api-core.md](./04-api-core.md).

| Spec | Covers |
|---|---|
| [`users-api.openapi.yml`](../api/users-api.openapi.yml) | `/users/me` and settings, users by username, search, roles, picture, additional emails, merge preview and merge, profile claims and merge requests, company logo requests, admin reconcile and sync status, public user and organizer reads, Watch pairing and authentication |
| [`uploads-api.openapi.yml`](../api/uploads-api.openapi.yml) | Profile picture upload (purpose `profile-picture`) |
| [`internal/cums-internal.openapi.yml`](../api/internal/cums-internal.openapi.yml) | Service-to-service: user reads, get-or-create, speaker provisioning and invitation credentials, email owners, Mail Center requests, the user-reference contract (preview, merge, primary email) |

How users, Cognito and the database stay in step: [06b-user-lifecycle-sync.md](./06b-user-lifecycle-sync.md).
