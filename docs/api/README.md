# docs/api

OpenAPI specs, one per API area, contract-first: the services and the frontend generate their types
from them.

- `*.openapi.yml`: the public API under `/api/v1`, routed by the API gateway. `_shared.openapi.yml`
  holds shared components only. Linted by `npm run lint:api` against its baseline.
- `internal/`: the service-to-service operations under `/internal/**` (Story 17.0b2), one spec per
  provider (`cums-`, `ems-`, `pcs-internal.openapi.yml`). Not public: the gateway denies
  `/internal/**`, and the public tooling (frontend path check, type generation, API similarity, ZAP
  coverage, access-control probe, `lint:api`) does not read this folder. The S2S contract harness
  tests both sides against these specs; `npm run lint:api-internal` lints them with
  `internal/.spectral.yaml`.
