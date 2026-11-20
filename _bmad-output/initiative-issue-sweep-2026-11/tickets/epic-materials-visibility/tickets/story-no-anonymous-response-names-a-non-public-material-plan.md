---
title: 'No anonymous response names a non-public material'
type: 'feature'
ticket: '2'
created: '2026-11-20'
status: 'built'
baseline_revision: 'f190d78b01a9b940196705920d2aa11817cad811'
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

**Problem:** Story 1 stored `publiclyVisible`, but every read still returns every material: an anonymous visitor gets the id and `cloudFrontUrl` of a speaker's PowerPoint (#1260 AC4), the counts include it, and each material names its uploader's username.

**Approach:** One component decides who may see non-public materials (organizer, or a speaker of that session); one method in `SessionMaterialsService` builds a session's material list for a given audience and blanks the internal fields for the public one. Every route that returns materials or their counts goes through it; the public read model always gets the public audience.

## Boundaries & Constraints

**Always:** Privileged = ORGANIZER, or a `session_users` row of that session with the caller's username, decided in `SessionMaterialAccess` and nowhere else; `checkMayChangeMaterials` uses it too. The public read model (`/public/events*`) and the public view of `/events/current` are shaped by publishing state, never by caller (their own rule, CDN- and Watch-shared): they get public materials only, for organizers and speakers too. Route A (`GET …/sessions/{slug}`), route H (`GET …/materials`) and the sessions list decide per caller and session. In the public projection `uploadedBy`, `s3Key` and `uploadId` are empty strings (see Design Notes). `materialsCount`/`materialsStatus` count what the response may show. Spec descriptions first. Integration tests on PostgreSQL through the real security chain; absence asserted by raw-body string search with a positive control in the same response.

**Never:** No download guard (story 3), no UI (story 4). No change to S3 keys, CDN prefix or DB trigger columns. No per-caller entries in a shared cache. No EventBridge, bus or polling (C1).

## I/O & Edge-Case Matrix

Fixture: published event, session S with speaker `sp`, public PDF P and non-public PPTX X; session T with only a non-public PPTX.

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| A anonymous / attendee / other speaker | `GET /events/{ec}/sessions/S` | P only, `materialsCount` 1, `COMPLETE`; P's `uploadedBy`, `s3Key`, `uploadId` empty; X's id and URL nowhere in the body | |
| A organizer / `sp` | same | P and X, full fields, count 2 | |
| B anonymous / organizer | `GET /public/events/{ec}`, `/public/events/current` | P only; T `materialsCount` 0, `NONE` | |
| B list | `GET /public/events?scope=archive` | lean sessions: S `COMPLETE`, T `NONE`; X nowhere | |
| C anonymous / attendee | `GET /events/current?include=sessions` | P only | |
| C organizer | same | P and X | |
| H attendee / other speaker | `GET …/sessions/S/materials` | P only, blanked | anonymous 401 (unchanged) |
| H organizer / `sp` | same | P and X | |
| H wrong event | session of another event | | 404 |
| Sessions list anonymous | `GET /events/{ec}/sessions` | S count 1 `COMPLETE`, T 0 `NONE` | |
| Sessions list organizer / `sp` | same | S 2; T 1 for organizer, T 0 for `sp` (not on T) | |

</frozen-after-approval>

## Code Map

- `service/SessionMaterialUploadHandler.java` -- `checkMayChangeMaterials` (organizer or `sessionUserRepository.existsBySessionIdAndUsername`), `session(eventCode, slug)` (404 outside the event). Keep `session`; move the rule.
- new `service/SessionMaterialAccess.java` -- `mayUseNonPublic(Session)`: `CallerRoles.isOrganizer()` or session_users row of `securityContextHelper.getCurrentUsernameOrNull()`.
- `service/SessionMaterialsService.java` -- `getMaterialsBySession(slug)` (ignores event, all materials), `toResponse`. New: `materials(Session, boolean includeNonPublic)`, `materialsForCaller(Session)`, `getMaterialsBySession(eventCode, slug)` (H), `toPublicResponse`.
- `service/SessionService.java` -- `toSessionResponse(...)` builds materials + count/status from the list: caller-decided by default; overload with `publicView`. New `toListItem(Session)` for the sessions list (count/status of what the caller may see).
- `service/SessionEventExpansion.java` -- `expandSessions` (View.PUBLIC → public view), `buildSessionMapBatch` (filter by view; counts from the filtered list).
- `controller/SessionMaterialsController.getSessionMaterials`, `controller/SessionController.listSessions` (mapper counts from trigger columns) and `getSession` (route A, unchanged path through `toSessionResponse`).
- Not changed: `EventReadService` (organizer detail cache key per audience; `/events/{code}` gives non-organizers no sessions), `PublicEventShaper`. `SpeakerMetricsExpansion`: changed after review (see Review Triage Log).
- Spec: `docs/api/event-sessions-api.openapi.yml` (`SessionMaterialResponse` uploadedBy/s3Key/uploadId, `materialsCount`, `getSessionMaterials`, `getSession`, `listSessions`), `docs/api/events-core-api.openapi.yml` / public events spec where sessions carry materials.
- Tests: new `controller/SessionMaterialAudienceIntegrationTest.java` (matrix); `EventControllerCaptureIntegrationTest` fixture gets a public PDF and a non-public PPTX on the archived session, raw-body check on every non-organizer row; `SessionMaterialsServiceTest` tests 2.5-2.7 (mocked `getMaterialsBySession`) removed, covered by the IT (touch rule).
- Bruno: `bruno-tests/events/public-event-materials-anonymous*.bru` read-only.
- Docs: `docs/architecture/06-backend-architecture.md` (materials bullet), `docs/user-guide/appendix/changelog.md`.

## Tasks & Acceptance

**Execution:**
- [ ] Specs -- descriptions of the changed behaviour.
- [ ] ITs red: matrix rows; capture fixture + raw-body check, goldens regenerated after green.
- [ ] `SessionMaterialAccess`, service, session service, expansion, controllers; `checkMayChangeMaterials` delegates.
- [ ] Remove mocked tests 2.5-2.7; Bruno pair; regenerate frontend types, `type-check`, `lint:api`, `make check-test-ratchet`.
- [ ] Docs + changelog.

**Acceptance Criteria:**
- Given the fixture, when any non-privileged caller reads A, B, C, H or the sessions list, then X's id and `cloudFrontUrl` appear in no body and P appears in each body that carries materials.
- Targeted ITs, capture test and `EndpointAuthorizationGuardTest` green; archunit store, ratchet and response-conformance baselines do not grow.

## Implementation Notes

- Implemented directly by the build session (the coordinator's brief allows no coding subagent on rack; one read-only reviewer); the checkpoint approval was the brief.
- Spec first: descriptions of `SessionMaterialResponse.uploadId/s3Key/uploadedBy`, `materialsCount`, `getSession`, `listSessions`, `getSessionMaterials` (event-sessions) and `EventDetail.sessions` (events-core). No schema change; generated TS types differ in comments only.
- `SessionMaterialAccess.mayUseNonPublic(Session)` is the one decision; `SessionMaterialUploadHandler.checkMayChangeMaterials` now delegates to it (it no longer needs `SessionUserRepository`).
- `SessionMaterialsService.materialsFor(session, publicView)` and the static `visible(materials, includeNonPublic)` filter every list; `toPublicResponse` blanks the three fields; `countFor(session)` feeds the sessions list (`SessionService.toListItem`, which replaced the bare mapper in `SessionController.listSessions`). `getMaterialsBySession(eventCode, slug)` now resolves the session within the event (route H answered for a session of another event before).
- `SessionEventExpansion`: the detail path passes `view == PUBLIC` to `toSessionResponse(..., publicView)`; the batch path filters with `visible(..., mayShowNonPublic(s, view == PUBLIC))` before building the lean map, so count and status follow.
- Tests: `SessionMaterialAudienceIntegrationTest` (9 tests) red first (7 failed for the right reasons, the 2 privileged controls passed), then green. One test-data correction on the way: the "other speaker" is the speaker of the internal-only session, so they rightly see its PPTX; the test now asserts that as a per-session positive control instead of an absence.
- Capture: the archived talk carries a public PDF and a non-public PPTX; `read()` string-searches every non-organizer and every public-model row for the PPTX's id and URL; positive controls: the organizer detail names the PPTX, the anonymous public detail names the PDF. 20 goldens changed, each diff checked (public views: PDF only, blanked; organizer detail: both; lean lists: `materialsStatus` COMPLETE).
- `SessionMaterialsServiceTest` (only the three mocked `getMaterialsBySession` tests were left) deleted; success, empty and wrong-event cases run in the IT (touch rule).
- Bruno `events/public-event-materials-find-anonymous.bru` + `public-event-materials-anonymous.bru` (seq 39, 40): read-only, picks the most recent archived event whose lean sessions say COMPLETE, then asserts every material of its public detail is public and blanked, with a positive control; passes with nothing to check where no such event exists. Parsed by `bru` 4.0.0 and run against a throwaway local server with a conforming body (6/6) and a leaking one (fails the projection test). Not run against local dev (its EMS did not answer and belongs to another checkout) or production (old code until deploy).

## Plan Change Log

## Review Triage Log

Pass 1 (quick lens, one read-only context-free reviewer). 5 findings: high 1, medium 0, low 4, false 0.

| # | Finding | Verdict | Route | Evidence / action |
|---|---|---|---|---|
| 1 | `SpeakerMetricsExpansion` counts `sessionsWithMaterialsCount` through the caller-decided `toSessionResponse`, ignores `view`, and the `/events/{code}` detail caches one `_public` entry for all non-organizers: a session speaker reading first would publish a count that includes their non-public files; `/events/current` public view would vary by caller | high | patch | Confirmed: anonymous goldens `get-event_metrics_anonymous` / `current-event_metrics_anonymous` carry the field. Test first (`should_countPublicSessions_when_metricsAreReadByNonOrganizer`, red: 2 instead of 1, speaker read first), fix: `publicView = view != View.ALL`. Green. |
| 2 | Blanking `s3Key`/`uploadId` hides nothing, the `cloudFrontUrl` path is the key and contains the upload id; docs claim they are withheld | low | patch | Confirmed for both create paths. Docs now say only the uploader is hidden and why the fields are still blanked; spec wording describes the field values, which stay true. |
| 3 | `SessionMaterialAccess` Javadoc names the download as a user, which is story 3 | low | patch | Wording corrected; story 3 adds the download. |
| 4 | Audience IT C/B-current assume no other completed event lies between −2 days and today | low | patch | Real order dependence. The event is now dated one minute before today's midnight in Bern, the latest date `findCurrentEvent` accepts, so only an event in the same minute could displace it. |
| 5 | Sessions list: up to two queries per session for non-organizers (N+1) | low | reject | Anonymous callers skip the membership query (no username), so one count per session on a programme of a few dozen sessions; the website reads `/public/events*`, not this list. The fix needs a grouped count and a membership batch query, complexity the everyday use does not justify. |

## Design Notes

**Architecture note.** Modules: EMS sessions (materials, session reads), publishing untouched (it receives the public view through `View.PUBLIC`). New dependencies: none across modules (`SessionMaterialAccess` uses sessions' `SessionUserRepository` and platform `CallerRoles`/`SecurityContextHelper`). Endpoints: none new; behaviour of A, B, C, H and the sessions list narrows. Reused: `CallerRoles`, `SessionMaterialUploadHandler.session`, `EventResponseContributor.View`. Deviation: none.

**Public view by publishing state.** `/public/events*` is the "never by caller" read model the Watch and the CDN share; a speaker sees their own non-public files on A, H and the portal. Putting speaker-specific content there would need per-caller cache and CDN keys.

**Blank, not absent.** `uploadedBy`, `s3Key` and `uploadId` stay in the schema's `required`: the shipped Apple Watch decodes `SessionMaterialResponse` from `/public/events/current` with non-optional `String` for all three (`apps/BATbern-watch/.../Generated/Models/SessionMaterialResponse.swift`), so a missing or null value would fail the whole event decode on installed watches. The public projection sends `""`.

**Store sizes at start:** EMS archunit store 10 lines; ratchet baseline 524 lines; EMS response-conformance baseline 3 lines.
**Store sizes at end:** unchanged (archunit store untouched, `make check-test-ratchet` clean with no STALE line, response-conformance baseline untouched). Not smaller: the touched code had no archunit or ratchet entry; the deleted Mockito test file had no ratchet line. Cleaner on the way: one access rule instead of a copy in the handler; route H now honours the event in its path.

## Verification

Run 2026-11-20 (logs `~/.cache/batbern-test-logs/epic5-s23-s2-*.log`): new IT red first (7 of 9 failed), then green; after review the metrics test red then green; final targeted run 91 tests, 0 failed (`*SessionMaterial*`, capture, `EndpointAuthorizationGuard`, `*Metrics*`); capture goldens: 20 changed, each diff read; checkstyle EMS clean; `generate:api-types`, `type-check`, `lint:api` (80 findings, unchanged), `check-test-ratchet` clean. Full EMS suite runs once after story 3 (coordinator brief). Bruno: parsed and logic-checked against a local fake server only.

**Commands:**
- `./gradlew :services:event-management-service:test --tests '*SessionMaterial*' --tests '*EventControllerCapture*' --tests '*SessionController*' --tests '*EndpointAuthorizationGuard*' --max-workers=4` -- green
- `cd web-frontend && npm run generate:api-types && npm run type-check && npm run lint:api` -- clean; `make check-test-ratchet` -- clean
