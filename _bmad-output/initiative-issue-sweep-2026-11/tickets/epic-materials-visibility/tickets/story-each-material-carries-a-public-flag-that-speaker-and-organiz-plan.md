---
title: 'Each material carries a public flag that speaker and organizer set'
type: 'feature'
ticket: '1'
created: '2026-11-20'
status: 'in-progress'
baseline_revision: 'a19df539fb73d05e9fde30664ce850ecb8480517'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: ''
review_source: ''
lenses_ran: []
review_loop_iteration: 0
context:
  - '{project-root}/docs/architecture/module-map.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Every session material is published after the event; speakers who upload their PowerPoint next to the PDF have no way to keep it internal (#1260). Stories 2 to 4 filter, guard and show; they all need a stored flag first.

**Approach:** `session_materials.publicly_visible` (V159), defaulted by type on every create path (PDF true, else false), overridable on the confirm with a target and on the association, changed by `PATCH /events/{eventCode}/sessions/{sessionSlug}/materials/{materialId}`, and carried by `SessionMaterialResponse`.

## Boundaries & Constraints

**Always:** Spec first. PDF = `file_extension` `pdf` or `mime_type` `application/pdf`, case-insensitive. The migration raises one NOTICE per event with its count of non-PDF rows (and a total). PATCH: `@PreAuthorize("hasRole('ORGANIZER') or hasRole('SPEAKER')")` plus `checkMayChangeMaterials`; the material is loaded scoped to its session (ADR-003); a change clears `eventWithIncludes` after the commit, as an upload does. Integration tests on PostgreSQL through the real security chain; S3 is `TestAwsConfig`, a batch-import file comes from a local HTTP server (the wire).

**Never:** No filtering of any response, no download guard, no frontend UI (stories 2, 3, 4). No change to S3 keys or the CDN prefix. No EventBridge, bus or polling (C1). Never edit an applied migration.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Migration | rows pdf/`application/pdf`, pptx, `PDF` upper case | pdf and PDF true, pptx false; NOTICE per event with non-PDF count | none |
| Confirm with target | pdf / pptx, no flag | true / false | none |
| Confirm with target, flag | pptx + `publiclyVisible: true`; pdf + `false` | true / false | none |
| Associate | items pdf, pptx, pptx with `publiclyVisible: true` | true, false, true | none |
| Batch import from URL | `.pdf`, `.pptx` material URLs | true / false | none |
| PATCH | organizer; speaker of the session | 200, body `publiclyVisible` changed, list shows it | none |
| PATCH forbidden | speaker of another session; attendee | 403, unchanged | |
| PATCH anonymous | no auth | 401 | |
| PATCH not here | material of another session of the event; other event; unknown id | 404 | |
| PATCH bad body | `{}` or non-boolean | 400 | |
| Cached detail | event detail fetched (cached), then PATCH | next detail read shows the new value | |

</frozen-after-approval>

## Code Map

- `services/event-management-service/src/main/resources/db/migration/V159__session_materials_publicly_visible.sql` -- new: add column, backfill, NOT NULL, DEFAULT false, NOTICE per event (`sessions.event_code`).
- `domain/SessionMaterial.java` -- field `publiclyVisible`; `@PrePersist` sets the type default when null (one place for all three create paths); static `isPdf(ext, mime)`.
- `service/SessionMaterialUploadHandler.java` -- `createMaterial(...)` gains `Boolean publiclyVisible`; `attach` reads it from the confirm (see Design Notes); reuse `session(...)`, `checkMayChangeMaterials`, `published` cache clear.
- `service/SessionMaterialsService.java` -- associate passes `item.getPubliclyVisible()`; new `updateVisibility(eventCode, slug, id, value)`; new private `materialIn(eventCode, slug, id)` (404) used by PATCH and delete; `toResponse` maps the flag. `uploadMaterialFromUrl` relies on `@PrePersist`.
- `repository/SessionMaterialsRepository.java` -- `findByIdAndSession_Id`.
- `controller/SessionMaterialsController.java` -- `updateSessionMaterial` (generated operation).
- `controller/UploadsController.java` + shared-kernel `upload/UploadService.confirm`, `UploadPurposeHandler.attach` -- carry the confirm's attach options to the handler.
- Specs: `docs/api/event-sessions-api.openapi.yml` (PATCH op, `SessionMaterialUpdateRequest`, `publiclyVisible` on `SessionMaterialResponse` required and on `MaterialUploadItem` optional), `docs/api/uploads-api.openapi.yml` (`ConfirmUploadRequest.publiclyVisible`).
- Lint: `web-frontend/scripts/api-lint-baseline.txt` + `api-lint-owners.txt` group X1 (PATCH `{materialId}` is the same owned child as the DELETE).
- Tests: new `controller/SessionMaterialVisibilityIntegrationTest.java` (harness of `SessionMaterialUploadIntegrationTest`), new `migration/V159SessionMaterialsPubliclyVisibleMigrationIntegrationTest.java` (model V127 test). `service/SessionMaterialsServiceTest.java`: delete tests move to the IT (touch rule).
- Docs: `docs/architecture/03-data-architecture.md` (column), `06-backend-architecture.md` (materials), `docs/user-guide/appendix/changelog.md`.

## Tasks & Acceptance

**Execution:**
- [ ] Specs + lint baseline -- first.
- [ ] ITs red: every matrix row.
- [ ] V159 + entity + repository + handler/service/controller + shared-kernel option.
- [ ] Defect (own commit, test first): DELETE of a material of another session of the same event answers 400 (`IllegalArgumentException`); must be 404 through `materialIn`.
- [ ] Regenerate frontend types, `npm run type-check`, `npm run lint:api`, `make check-test-ratchet`.
- [ ] Docs + changelog.

**Acceptance Criteria:**
- Given V159 applied on production data, then no row has a NULL flag and the deploy log lists the non-PDF count per event.
- EMS full test suite green; archunit store and ratchet baseline do not grow; response-conformance baseline does not grow; `EndpointAuthorizationGuardTest` green.

## Implementation Notes

- Implemented directly by the build session (the coordinator's brief allowed no subagents on rack); the checkpoint approvals were the coordinator's brief.
- Spec first: `updateSessionMaterial` (PATCH), `SessionMaterialUpdateRequest` (required boolean, `additionalProperties: false`), `publiclyVisible` required on `SessionMaterialResponse`, optional on `MaterialUploadItem` and `ConfirmUploadRequest`. Lint: PATCH line added to the existing owner exception X1 (owned UUID child, as the DELETE on the same path), baseline +1 line (80); name it in the PR.
- The type default sits in `SessionMaterial.@PrePersist` (`isPdf`), so the batch import needs no code; the handler and the association pass the explicit value through.
- Shared-kernel: `UploadService.confirm(..., attachOptions)` overload and `UploadPurposeHandler.attach(upload, targetId, options)` default; the old signatures stay (CUMS unchanged).
- The V159 migration was written before its test (the test then passed on the first run); the ITs were red first (response conformance and the batch default), the cache assertion was checked by a mutant (clear removed: test fails).
- Test usernames must match the CUMS shape (`CumsContractHarness`), since the event detail reads speakers from CUMS.
- Defect fixed in its own commit (C9): DELETE of a material of another session of the same event answered 400 (`IllegalArgumentException`); now 404 through the same scoped lookup as the PATCH. The three mock-based delete tests in `SessionMaterialsServiceTest` were removed (touch rule); success, unknown id and wrong session are covered by `SessionMaterialUploadIntegrationTest` and `SessionMaterialsControllerIntegrationTest`.
- Not done (other stories): no response filter, no download guard, no UI; the hand-written `SessionMaterial` type in `web-frontend/src/types/event.types.ts` is left for story 4. The list path's batch session map (`SessionEventExpansion`) does not carry the flag; story 2 owns that view.

## Plan Change Log

## Review Triage Log

## Design Notes

**Architecture note.** Modules: EMS sessions (materials), shared-kernel upload. New dependencies: none. Endpoint: `PATCH /events/{eventCode}/sessions/{sessionSlug}/materials/{materialId}`; nearest is the `DELETE` on the same path (no body, other method) and `PATCH /events/{eventCode}/teaser-images/{imageId}` (same shape on another resource); the module map asks state changes as PATCH on the resource. Reused: `checkMayChangeMaterials`, `session(...)`, `CacheConfig.clearAfterCommit`, upload contract. Deviation: none; the confirm option crosses shared-kernel because `UploadService.confirm` calls the handler.

**Confirm option.** `ConfirmUploadRequest.publiclyVisible` (optional boolean, read by `session-material` with a target only; other purposes and a confirm without a target ignore it, the association carries its own). Shared-kernel: `UploadService.confirm(purpose, id, target, Map<String,Object> attachOptions)` (old signature delegates with an empty map) and `UploadPurposeHandler.attach(upload, targetId, options)` default → `attach(upload, targetId)`. Generic, so no purpose name enters shared-kernel.

**DB default false.** A row inserted outside the entity (raw SQL) stays non-public: the safe side for #1260.

**Store sizes at start:** EMS archunit store 10 lines; ratchet baseline 524 lines; EMS response-conformance baseline 3 lines.

## Verification

**Commands:**
- `./gradlew :services:event-management-service:test --tests '*SessionMaterial*' --tests '*V159*' --tests '*EndpointAuthorizationGuard*' --max-workers=4` -- green
- `./gradlew :services:event-management-service:test --max-workers=4` -- green
- `./gradlew :shared-kernel:test --max-workers=4` -- green
- `cd web-frontend && npm run generate:api-types && npm run type-check && npm run lint:api` -- clean; `make check-test-ratchet` -- clean
