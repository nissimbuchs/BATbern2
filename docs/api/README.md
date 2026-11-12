# docs/api

OpenAPI specs, one per API area, contract-first: the services and the frontend generate their types
from them.

- `*.openapi.yml`: the public API under `/api/v1`, routed by the API gateway. `_shared.openapi.yml`
  holds shared components only. Linted by `npm run lint:api` against its baseline.
- `uploads-api.openapi.yml`: the one upload contract (ADR-002 decision 2026-11-07, Story 17.11),
  `POST /uploads/{purpose}`, `/{uploadId}/confirm`, `/import`. Several services implement it, each
  for the purposes it owns; the gateway routes by the purpose prefix (`company-…` CUMS, `event-…`
  EMS). A new purpose adds its value to `UploadPurpose` and a handler in the owning service, no new
  operation. The former `/logos/*` operations of `companies-api` are gone.
- `internal/`: the service-to-service operations under `/internal/**` (Story 17.0b2), one spec per
  provider (`cums-`, `ems-`, `pcs-internal.openapi.yml`). Not public: the gateway denies
  `/internal/**`, and the public tooling (frontend path check, type generation, API similarity, ZAP
  coverage, access-control probe, `lint:api`) does not read this folder. The S2S contract harness
  tests both sides against these specs; `npm run lint:api-internal` lints them with
  `internal/.spectral.yaml`.
