---
title: 'A material downloads only through its own event and session, and a non-public one only for its people'
type: 'feature'
ticket: '3'
created: '2026-11-20'
status: 'built'
baseline_revision: '4c4a68624868e4904a4a8794100a0772ed73a38d'
route: 'full'
route_source: 'auto'
risk: 'high'
review: 'quick'
review_source: 'pinned'
lenses_ran: [quick]
review_loop_iteration: 0
context:
  - '{project-root}/docs/architecture/module-map.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `GET …/materials/{materialId}/download` is anonymous and looks the material up by id alone: it ignores `eventCode` and `sessionSlug` and hands a presigned URL for any material, a non-public PowerPoint included, to anyone who has or guesses its id (#1260 AC2).

**Approach:** Resolve the material within its event and session (404 otherwise); a non-public material answers 404 to everyone the story-2 rule (`SessionMaterialAccess`) does not admit, so organizers and the session's speakers still get the file. Public materials keep today's anonymous download.

## Boundaries & Constraints

**Always:** One decision, `SessionMaterialAccess.mayUseNonPublic`; the material loaded with `findByIdAndSession_Id` after `SessionMaterialUploadHandler.session(eventCode, slug)` (ADR-003). A refused non-public material answers exactly like an unknown id (404, same error shape), so the answer does not reveal that it exists. The endpoint stays `permitAll`; `scripts/ci/access-control-public.txt` says the public answer covers public materials only. Spec description first. Integration tests on PostgreSQL through the real security chain; S3 presigning is `TestAwsConfig`'s presigner (the boundary).

**Never:** No change to S3 keys, the CDN prefix or to files under it (owner 2026-11-15). No 403 for the non-public case. No upload to production from Bruno.

## I/O & Edge-Case Matrix

Fixture: event E, session S (speaker `sp`) with public PDF P and non-public PPTX X; session T of E; event F with session U.

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Public, anonymous | `GET /events/E/sessions/S/materials/P/download` | 200, presigned URL for P's key | |
| Non-public, organizer / `sp` | `…/S/materials/X/download` | 200, URL for X's key | |
| Non-public, anonymous / attendee / speaker of T | same | | 404, same body as an unknown id |
| Wrong session | `…/E/sessions/T/materials/P/download` | | 404 |
| Wrong event | `…/F/sessions/S/materials/P/download` | | 404 |
| Unknown id | random UUID under S | | 404 |

</frozen-after-approval>

## Code Map

- `controller/SessionMaterialsController.getMaterialDownloadUrl` -- passes only `materialId` today; pass event and slug.
- `service/SessionMaterialsService.generateDownloadUrl(UUID)` -- `findById`; becomes `(eventCode, slug, id)`: `materialUploads.session`, private `materialIn`, then `materialAccess.mayUseNonPublic(session)` for a non-public one, else `MaterialNotFoundException` (404). Keep `S3Links.downloadUrl` and `downloadName`.
- `service/SessionMaterialAccess.java` -- Javadoc names the download as a user.
- `config/SecurityConfig.java:117` -- comment of the `permitAll`.
- `scripts/ci/access-control-public.txt:21` -- reason column.
- Spec `docs/api/event-sessions-api.openapi.yml` `getMaterialDownloadUrl` description and 404.
- Frontend callers (`SessionCards`, `EventProgram`, `SessionEditModal`) already pass the material's own event and slug: no change.
- Tests: new `controller/SessionMaterialDownloadIntegrationTest.java` (harness of `SessionMaterialAudienceIntegrationTest`); the existing organizer download in `SessionMaterialUploadIntegrationTest` stays.
- Bruno `bruno-tests/events/public-event-materials-download-*.bru`: read-only, see Design Notes.
- Docs: `06-backend-architecture.md` materials bullets, `changelog.md`.

## Tasks & Acceptance

**Execution:**
- [ ] Spec, access-control list, security comment.
- [ ] IT red for every matrix row.
- [ ] Service + controller.
- [ ] Bruno; regenerate types, `type-check`, `lint:api`, `check-test-ratchet`; docs.
- [ ] Full EMS suite once.

**Acceptance Criteria:**
- Given X is non-public, when any caller other than an organizer or a speaker of S asks for its download through any path, then the answer is 404 and carries no URL.
- Full EMS suite green; archunit store, ratchet and response-conformance baselines do not grow; `access-control-check` list matches the spec.

## Implementation Notes

- Implemented directly by the build session (coordinator brief: no coding subagent on rack, one read-only reviewer); the checkpoint approval was the brief.
- Spec first: `getMaterialDownloadUrl` description and its own 404 description; `access-control-public.txt` reason ("public materials only; a non-public one 404"), `SecurityConfig` comment; `access-control-check.py --check-allowlist`: 40 entries, 0 stale.
- `SessionMaterialsService.generateDownloadUrl(eventCode, slug, id)`: `materialUploads.session` (404), `materialIn` (`findByIdAndSession_Id`, 404), then a non-public material needs `SessionMaterialAccess.mayUseNonPublic`, else the same `MaterialNotFoundException` as an unknown id. The controller passes the path's event and slug. The old `findById` lookup is gone.
- Tests: `SessionMaterialDownloadIntegrationTest` (4 tests covering every matrix row) red first (2 failed: outsiders got 200 for the PPTX, wrong session/event got 200), then green; the existing organizer download in `SessionMaterialUploadIntegrationTest` still passes.
- Frontend callers already pass the material's own event and slug (`SessionCards`, `EventProgram`, `SessionEditModal`); no change. Generated types: comment changes only.
- Bruno (seq 41-43, `events/public-event-materials-download-*.bru`): organizer read of the event the story-2 request found (read-only), anonymous download of a non-public material (404) and of a public one (200, positive control); no material of a kind: passes with nothing to check. Run with the story-2 pair against a throwaway local server: 9/9 on a conforming server, the 404 test fails when the server leaks. Not run against local dev or production (see story 2).

## Plan Change Log

## Review Triage Log

Pass 1 (quick lens, one read-only context-free reviewer). 2 findings: high 0, medium 0, low 2, false 0.

| # | Finding | Verdict | Route | Evidence / action |
|---|---|---|---|---|
| 1 | The "same answer as an unknown id" test compared only `status` and `error`, so a differently worded not-found would pass | low | patch | Real gap in the test, code correct today (same exception). The test now compares the whole error body after replacing the id and dropping per-request fields. |
| 2 | The 404 of `getMaterialDownloadUrl` kept the generic `NotFound` description | low | patch | Real. The operation's 404 now has its own description of the four cases, same `ErrorResponse` schema. |

## Design Notes

**Architecture note.** Modules: EMS sessions (materials). New dependencies: none (`SessionMaterialsService` already holds `SessionMaterialAccess`). Endpoints: none new; `getMaterialDownloadUrl` narrows. Reused: `SessionMaterialAccess`, `SessionMaterialUploadHandler.session`, `findByIdAndSession_Id`, `MaterialNotFoundException`. Deviation: none.

**404, not 403.** The id of a non-public material never reaches an outsider (story 2), and a 403 would confirm it exists.

**Bruno.** No production fixture is known to hold a non-public material, and an outsider cannot learn one by design. The check reads, as organizer (read-only), the archived event the story-2 request found, picks a non-public and a public material if there are any, and asks for both downloads anonymously: non-public 404, public 200 (positive control; presigning makes no S3 call). Without a non-public material it passes with nothing to check.

**Store sizes at start:** EMS archunit store 10 lines; ratchet baseline 524 lines; EMS response-conformance baseline 3 lines.
**Store sizes at end:** unchanged (no STALE line in the full run or the ratchet). Not smaller: the touched code had no entry in any store. Cleaner on the way: the download no longer loads a material by id alone (ADR-003 parent scoping).

## Verification

Run 2026-11-20 (logs `~/.cache/batbern-test-logs/epic5-s23-*.log`): new IT red first (2 of 4 failed), then green; full EMS suite once, after the implementation and before the review patches: 3112 tests, 0 failed, 6 skipped (`epic5-s23-full-ems.log`); after the review patches (a test assertion and a spec description) the download IT again green; `generate:api-types`, `type-check`, `lint:api` (80, unchanged), `check-test-ratchet`, `access-control-check.py --check-allowlist` (40 entries, 0 stale) clean. Bruno: logic-checked against a local fake server only.

**Commands:**
- `./gradlew :services:event-management-service:test --tests '*SessionMaterial*' --max-workers=4` -- green
- `./gradlew :services:event-management-service:test --max-workers=4` -- green (full suite, once)
- `cd web-frontend && npm run generate:api-types && npm run type-check && npm run lint:api`; `make check-test-ratchet` -- clean
