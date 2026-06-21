# Story 15.1: Replace WebSockets with REST polling for live agenda control

Status: ready-for-dev

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As an **organizer running a live event (and the presenter/public screens reflecting it)**,
I want **live agenda timing changes delivered over plain REST polling instead of STOMP/WebSockets**,
so that **session end/extend/delay always reconcile correctly across multiple Fargate tasks — no reconnect desync, no lost in-memory state — and the most fragile surface of a live event becomes stateless and debuggable**.

**Item #2** from the Event #2 feedback (`docs/specs/event-2-feedback-quick-spec.md`). Architectural decision (Nissim): **drop WebSockets entirely** (web + watch); deliver live updates via REST polling. Forward-only — no WS/REST dual-protocol kept long term.

**Why now:** in-memory STOMP `SimpleBroker` + no ALB sticky sessions + multi-task Fargate ⇒ a client whose poll/reconnect lands on a different task sees stale or missing state. The presenter already half-polls (`usePresentationData.ts` polls `GET /events/{eventCode}` every 60 s with a WS layer only for cache-busting), so polling is the proven-in-prod path; this story finishes the job and removes the STOMP layer.

## Acceptance Criteria

1. **AC1 — action recompute & version bump.** GIVEN a LIVE event, WHEN an organizer ends/extends/delays a session via `POST /api/v1/events/{eventCode}/live-timing/actions`, THEN the server runs the existing cascade (recomputes downstream scheduled offsets, sets actual times, detects event completion) and bumps a monotonic `version`; a subsequent `GET /api/v1/events/{eventCode}/live-timing` reflects the change within one poll interval.
2. **AC2 — conditional GET returns 304.** GIVEN no change since the last poll, WHEN a consumer polls `GET …/live-timing` with `If-None-Match: "<etag>"` (etag derived from the monotonic `version`), THEN it receives `304 Not Modified` with no body.
3. **AC3 — task-independent (the core fix).** GIVEN two Fargate tasks, WHEN a consumer's successive polls hit different tasks, THEN results are byte-identical: the `version`, session timing state, and the organizer-presence flag are all read from persistent storage (DB), never from per-task in-memory state (no `SimpleBroker`, no `ConcurrentHashMap`).
4. **AC4 — consumers work without STOMP.** WHEN the WS endpoints are removed (P3), THEN presenter (`usePresentationData`), live-control (`useLiveSessionControl`), the public live view, AND the watch app still reflect live changes via polling only — no STOMP client remains in `web-frontend` and `sockjs-client` / `@stomp/stompjs` are removed from `package.json`.
5. **AC5 — behaviour parity, no regression.** The three actions (END_SESSION, EXTEND_SESSION, DELAY_TO_PREVIOUS) produce identical DB mutations and identical downstream-cascade results as the current `WatchSessionService` path (same idempotency guards, same auto-start-next-session, same event-completion transition, same speaker-arrival count semantics). Existing Watch* integration tests continue to pass against the extracted service.
6. **AC6 — adaptive polling.** Web consumers poll adaptively at the SAME interval (presenter + live-control): 3–5 s while a session is currently active ("LIVE"), back off when the tab is hidden/idle (per the existing `onAppear`/visibility guard rule), and send `If-None-Match` so unchanged polls cost a 304.
7. **AC7 — authorization preserved.** `POST …/live-timing/actions` requires an authenticated organizer (same gate the STOMP `join`/`action` path enforced today via presence + JWT); `GET …/live-timing` is readable by the presenter/public surfaces exactly where the current `/topic/events/{eventCode}/state` data was visible (anonymous-readable for the presenter view).

## Tasks / Subtasks

> **Phasing (each phase independently prod-deployable; staging IS prod).**
> **P1** = add REST endpoints + persist version/presence + migrate the two web consumers off STOMP (verify on `beta.batbern.ch`).
> **P2** = migrate the watch app (separate App Store release train, separate repo — tracked here for completeness, NOT shipped by this story's web PRs).
> **P3** = delete WebSocket code/deps — only after BOTH web and watch are confirmed off WS.

### P1 — Backend: REST endpoints + persistent version/presence (additive, WS untouched) (AC: 1,2,3,5,7) — ✅ DONE

- [x] **Contract-first**: added to `docs/api/events-api.openapi.yml`:
  - [x] `GET /api/v1/events/{eventCode}/live-timing` → `200` `LiveTimingResponse` + `304` + `ETag` header + `If-None-Match` request header.
  - [x] `POST /api/v1/events/{eventCode}/live-timing/actions` → `LiveTimingActionRequest` → `200` `LiveTimingResponse` (bumped `version`). 400/401/403/404 documented.
  - [x] Reused the existing `WatchSessionDetail` schema for the sessions array (keeps `sessionSlug/scheduledStartTime/scheduledEndTime/actualStartTime/actualEndTime/overrunMinutes/completedBy/status`).
- [x] ~~Regenerate backend interfaces (`openApiGenerate`)~~ **DEVIATION (intentional):** the watch subsystem is hand-written (`@RestController` + plain records in `watch/dto`), NOT generated from OpenAPI. Live-timing follows that established convention — hand-written `LiveTimingController` + records (`LiveTimingResponse`, `LiveTimingActionRequest`) — and the OpenAPI spec is still authored for frontend type-gen + contract docs. (Wiring a generated `*Api` only for live-timing would diverge from every other watch endpoint.)
- [x] **Persistent monotonic version (AC3)**: `V118__add_live_timing_version.sql` adds `events.live_timing_version BIGINT NOT NULL DEFAULT 0`; bumped atomically via `EventRepository.incrementLiveTimingVersion` (`@Modifying` `UPDATE … +1`, flush/clear). ETag = `"evt-<eventCode>-<version>"`. (Schema head was V117 → V118/V119.)
- [x] **Persistent organizer presence (AC3)**: `V119__create_live_timing_presence.sql` + `LiveTimingPresence` entity + `LiveTimingPresenceRepository` (native `INSERT … ON CONFLICT` upsert; `existsActivePresence` within 30 s TTL). Implicit upsert on the authenticated organizer's GET; anonymous presenter polls do NOT upsert. `WatchPresenceService` left intact (deleted in P3).
- [x] **Reuse the cascade service**: `LiveTimingService.applyAction` calls the SAME `WatchSessionService.endSession/extendSession/delayToPreviousSession` (REQUIRED propagation → same tx), then bumps the version. WS `WatchWebSocketController` untouched (still delegates + broadcasts in P1).
- [x] **GET assembler**: `LiveTimingService.getLiveTiming` builds `LiveTimingResponse` from `SessionRepository` + arrival counts + version + presence, `@Transactional(readOnly=true)`, with speaker enrichment (UserApiClient + company-portrait join, incl. Story 15.5 logos).
- [x] **Conditional GET (AC2)**: `LiveTimingController` computes the version ETag, returns `304` on matching `If-None-Match` (net-new — no prior ETag/304 in EMS).
- [x] **Security (AC7)**: `GET …/live-timing` permitAll in BOTH EMS `SecurityConfig` and api-gateway `SecurityConfig`; `POST …/actions` `@PreAuthorize("hasRole('ORGANIZER')")`.
- [x] **Tests (Testcontainers PostgreSQL)**: `LiveTimingServiceIntegrationTest` (8) — GET shape, current-session derivation, EXTEND/END cascade + version bump, monotonic version read from DB, presence TTL, not-found; `LiveTimingControllerIntegrationTest` (5) — anonymous GET + ETag, 304 on match, 200 on stale, POST 403 unauth/attendee, POST organizer applies + bumps. **13/13 green.**

### P1 — Frontend: migrate presenter + live-control off STOMP onto polling (AC: 4,6) — ✅ DONE

- [x] Added `web-frontend/src/services/liveTimingService.ts` — `getLiveTiming(eventCode, etag?, skipAuth?)` (sends `If-None-Match`, surfaces 304) + `postLiveTimingAction(eventCode, request)`.
- [x] Regenerated frontend types (`npm run generate:api-types`) — `LiveTimingResponse`/`LiveTimingActionRequest` in committed `src/types/generated/events-api.types.ts`.
- [x] **`useLiveSessionControl`**: STOMP/SockJS removed; adaptive `setTimeout` polling loop (4 s active / 15 s idle / 30 s hidden) with ETag passthrough; `sendExtend`/`sendDelay` POST via the service then apply the returned snapshot; preserved derived status, 1 s countdown ticker, button-visibility, and the offline-action-queue (queue on failed POST → flush on next successful poll). Public interface unchanged (no consumer edits needed). (Used a manual loop rather than TanStack `useQuery` to keep the existing imperative interface + offline-queue semantics intact.)
- [x] **`usePresentationData`**: dropped the anonymous STOMP cache-invalidation layer; added a cheap anonymous live-timing poll (5 s active / 30 s hidden, `If-None-Match`) that invalidates `['presentation-event']` only when the version advances — same effect the WS had. Other queries unchanged; event safety-net poll kept at 60 s.
- [x] **No other public live surface to migrate** (confirmed): presenter + live-control only; `CountdownTimer` untouched.
- [x] **Tests**: rewrote `useLiveSessionControl.test.ts` (20, polling + actions + derived state + offline-queue entry) and `usePresentationData.test.ts` (17, anonymous poll, version-change invalidation, no-invalidate-when-unchanged, unmount stops polling) off STOMP mocks. **37/37 green.** Consuming components (LiveControl, presenter) **89/89 green**.
- [ ] **Verify on beta** — PENDING (manual): publish to `beta.batbern.ch` and click through live-control + presenter against real data before/after promote. Not run autonomously (touches prod CloudFront). Local-dev smoke covered instead.

### P2 — Watch app migration (separate App Store release train; NOT in this PR) (AC: 4) — ⏳ DEFERRED

- [ ] Migrate the watch app's `WebSocketClient`/`WebSocketService` to poll `GET …/live-timing` and POST actions; reuse `OfflineActionQueue`. Separate watch repo / App Store release train. Backend P1 endpoints are ready for it.

### P3 — Teardown: delete WebSocket code + deps (only after web AND watch are off WS) (AC: 4) — ⏳ BLOCKED on P2

- [ ] Backend: remove `WebSocketConfig.java`, `/ws`, `/api/v1/watch/ws`, `JwtStompInterceptor(.java + Test)`, the WS parts of `WatchWebSocketController`, `WatchWebSocketDisconnectListener`, the in-memory `WatchPresenceService` map. Keep `WatchSessionService`, `WatchSpeakerArrivalService`, REST arrivals, live-timing.
- [ ] Remove WS `permitAll` (`/ws`, `/api/v1/watch/ws`) from EMS + api-gateway `SecurityConfig`.
- [ ] Frontend: delete `notificationWebSocketClient.ts`, `useNotificationWebSocket.ts`; remove `@stomp/stompjs`, `sockjs-client`, `@types/sockjs-client`; ensure `useNotifications` polls (cloud already lacks WS — parity-preserving).
- [ ] Delete WS-only tests (`Watch*WebSocket*IntegrationTest`, `JwtStompInterceptorTest`, `WatchWebSocketDisconnectListenerTest`).

## Dev Notes

### Chosen approach (and why)

- **Reuse, don't rewrite, the cascade.** The expensive/risky part (END/EXTEND/DELAY downstream recompute, idempotency, auto-start-next, event-completion) already lives in `WatchSessionService` as a plain transactional service — the STOMP controller is just a thin delegate + broadcaster. The new REST controller calls the SAME service methods, so AC5 parity is structural, not best-effort. This keeps the existing `Watch*IntegrationTest` suite as the parity oracle.
- **Persistence is the whole point (AC3).** The desync bug is in-memory state on a multi-task Fargate service with no sticky sessions. So two things MUST move to the DB: (1) the monotonic `version` (for ETag/304 + ordering), (2) organizer presence (today a per-task `ConcurrentHashMap`). Everything else (session timing) is already in `sessions`.
- **No `@Version` exists on `Session`** — the monotonic value is per-event (the resource is the event's live-timing), so put `live_timing_version` on `events` and bump it inside each action transaction. ETag = `"evt-<eventCode>-<version>"`. This also gives a cheap 304 path (AC2) without hashing the body.
- **Polling is already proven in prod.** `usePresentationData` polls `GET /events/{eventCode}` every 60 s today; the WS layer there is only cache-busting. We're tightening the interval (3–5 s while LIVE) and adding conditional requests, not introducing a new mechanism.
- **Forward-only / additive-then-cutover.** P1 ships REST alongside live WS (zero risk). P3 deletes WS only after both consumers are confirmed migrated. Each phase is independently deployable to prod.

### Current-state facts pinned during analysis (cite when implementing)

- **Cascade service** — `services/event-management-service/src/main/java/ch/batbern/events/watch/WatchSessionService.java`:
  - `END_SESSION`: sets `actualEndTime`, `completedByUsername`, `overrunMinutes`; **auto-starts next** session (sets its `actualStartTime`); transitions event to `EVENT_COMPLETED` when all completeable sessions have `completedByUsername`. No scheduled-time shift.
  - `EXTEND_SESSION`: truncates `endTime` to minute, adds `minutes*60s`; cascade query `findByEventCodeAndScheduledStartTimeGreaterThanEqualOrderByScheduledStartTime(eventCode, oldEnd)` shifts every downstream `startTime`+`endTime` by the same delta; evicts `EVENT_WITH_INCLUDES` cache.
  - `DELAY_TO_PREVIOUS`: resets current to SCHEDULED (`actualStartTime=null`), re-activates previous (extend `endTime`, clear `actualEndTime`/`completedByUsername`/`overrunMinutes`), shifts current+downstream forward. Carries `previousSessionSlug`.
  - All three are **idempotent** (re-check completed/active before writing).
- **Session entity** — `…/events/domain/Session.java`: `sessionSlug` (public id, ADR-003), scheduled `startTime`/`endTime` (l.104/107), `actualStartTime` (l.144), `actualEndTime` (l.147), `overrunMinutes` (l.149), `completedByUsername` (l.153). **No `@Version`.** Table `sessions`. `eventCode` is a denormalized persistent column (no join through Event for cascade queries).
- **In-memory presence (the AC3 violator)** — `…/watch/WatchPresenceService.java` l.36-37: `ConcurrentHashMap<String, Set<OrganizerPresence>> presenceByEvent`. Lost on task restart/scale. Used as the gate for session actions (`isOrganizerPresent`).
- **WS config & auth** — `…/config/WebSocketConfig.java` (SimpleBroker), `…/watch/JwtStompInterceptor.java` (validates Bearer on STOMP CONNECT, multi-issuer: Cognito RS256 / Watch HS256 by `iss`), `…/config/SecurityConfig.java` (`/ws` + `/api/v1/watch/ws` permitAll at HTTP).
- **WS controller & broadcasts** — `…/watch/WatchWebSocketController.java`: STOMP `/app/watch/events/{eventCode}/{join|leave|action|speaker-arrived}`; broadcasts `WatchStateUpdateMessage` to `/topic/events/{eventCode}/state` and `SpeakerArrivalBroadcast` to `/topic/events/{eventCode}/arrivals`.
- **Speaker arrivals already have REST** — `WatchEventController` `GET/POST /api/v1/watch/events/{eventCode}/arrivals` (OpenAPI ~l.4947) + `WatchSpeakerArrivalService.confirmArrival()` (idempotent insert on `speaker_arrivals` UNIQUE(event_code, speaker_username); server-authoritative counts). Fold the arrival summary count into `LiveTimingResponse` for one-poll efficiency; keep the existing arrivals list endpoint.
- **Frontend live-control** — `web-frontend/src/hooks/useLiveSessionControl/useLiveSessionControl.ts`: REST bootstrap `GET /watch/organizers/me/active-events` (Cognito JWT), then **direct STOMP to EMS** (bypasses API Gateway, which can't proxy WS upgrades): `onConnect` publishes `…/join`, subscribes `/topic/events/{eventCode}/state`, publishes actions to `…/action`. Has a `pendingActionRef` offline queue, 1-second countdown ticker, client-derived status, button-visibility (`shouldShowDelay` = active AND elapsed<10 min). Components: `web-frontend/src/components/organizer/LiveControl/{ActiveSessionCard,AgendaList,ExtendSessionSheet,DelaySessionSheet}.tsx`, page `web-frontend/src/pages/LiveControlPage/LiveControlPage.tsx`.
- **Frontend presenter** — `web-frontend/src/hooks/usePresentationData.ts`: anonymous WS to `/topic/events/{eventCode}/state` for **cache invalidation only** (l.63-93) + `useQuery(['presentation-event', eventCode], GET /events/{eventCode}?include=…, { refetchInterval:60000, refetchIntervalInBackground:true, staleTime:30000 })` (l.97-104). Service `web-frontend/src/services/presentationService.ts`.
- **Notification WS is local-only & separate** — `web-frontend/src/services/notificationWebSocketClient.ts` + `useNotificationWebSocket.ts` throw outside localhost; cloud already runs notifications over REST. Relevant only to the P3 cleanup note (ensure dashboard `useNotifications` polls).
- **No ETag/If-None-Match/304 anywhere in EMS today** — the conditional-request handling is net-new.

### Project Structure Notes

- Backend layered architecture (project-context): Controller (implements generated `*Api`) → Service (business logic, returns generated DTOs) → Repository → Entity. New `LiveTimingController` + `LiveTimingService` follow this; the controller must NOT add mapping annotations (they come from the generated interface).
- ADR-003: public path uses `eventCode` + `sessionSlug`, never UUIDs. ✅ already the convention here.
- Contract-first (ADR-006): edit `docs/api/events-api.openapi.yml` BEFORE implementing; regenerate backend (`openApiGenerate`) and frontend (`npm run generate:api-types`) and commit generated frontend types.
- Flyway: NEVER edit an applied migration. Add new `V{next}__*.sql` — **verify the current EMS schema head first** (`ls services/event-management-service/src/main/resources/db/migration/` and take the max). Both new columns/tables are additive (no behaviour change until first action).
- Doc-drift: this changes API contracts + (eventual) WS teardown → update associated docs in the same commit per `.github/doc-drift-mappings.yml` (live-event/watch architecture docs), or tag `[no-doc]` if truly none.

### Testing standards summary

- TDD red-green-refactor. Integration tests extend `AbstractIntegrationTest` (Testcontainers PostgreSQL — never H2), annotate `@Transactional`. Names: `should_<behavior>_when_<condition>`.
- Parity (AC5): assert the new REST action path yields the same `sessions` state the existing `WatchSessionServiceIntegrationTest` / `WatchExtendSessionIntegrationTest` / `WatchDelayToPreviousIntegrationTest` / `WatchEventCompletionIntegrationTest` assert for the WS path.
- AC3: a test that reads `version` from the DB after an action (not from a service field) and asserts strict monotonic increment; a test that a second "reader" (fresh service instance / fresh query) sees the same version+state.
- AC2: GET then GET-with-matching-`If-None-Match` → `304` no body; after an action, the same ETag → `200` with new body.
- Frontend: Vitest + RTL, `msw` for HTTP. Update STOMP-mocked tests to poll-based. Coverage gates: unit ≥90% business logic, integration ≥80% APIs.
- Bruno: add `.bru` contract tests for GET/304/POST under the events collection (prose in `docs{}` only; add cleanup; no real outbound comms — staging IS prod).
- Pipe gradle/make output through `tee /tmp/<name>.log` (with `set -o pipefail`) and grep the file.

### References

- [Source: docs/prd/epic-15-post-event-2-hardening.md#Story 15.1] — scope, ACs, phasing P1/P2/P3.
- [Source: docs/specs/event-2-feedback-quick-spec.md] — item #2 (drop WebSockets, REST polling).
- [Source: services/event-management-service/src/main/java/ch/batbern/events/watch/WatchSessionService.java] — cascade logic to reuse (END/EXTEND/DELAY).
- [Source: services/event-management-service/src/main/java/ch/batbern/events/domain/Session.java#L104-L153] — timing fields; no `@Version`.
- [Source: services/event-management-service/src/main/java/ch/batbern/events/watch/WatchPresenceService.java#L36] — in-memory presence to replace with DB.
- [Source: services/event-management-service/src/main/java/ch/batbern/events/config/WebSocketConfig.java] + [JwtStompInterceptor.java] + [WatchWebSocketController.java] — P3 teardown targets.
- [Source: web-frontend/src/hooks/useLiveSessionControl/useLiveSessionControl.ts] — STOMP→poll migration target.
- [Source: web-frontend/src/hooks/usePresentationData.ts#L63-L104] — drop WS cache-bust; tighten poll + add ETag.
- [Source: docs/api/events-api.openapi.yml#L4877] — where the new live-timing paths go (next to `/watch/...`).
- [Source: _bmad-output/project-context.md] — ADR-003/004/006, layered arch, Flyway never-edit-applied, no-MUI-public, Testcontainers, beta-canary, i18n 10-locale.
- [Source: CLAUDE.md#Beta Frontend Canary] — `scripts/deploy/publish-beta-frontend.sh` for P1 verification.

## Resolved Decisions

- **Drop WebSockets entirely (web + watch), no long-term dual protocol.** REST polling is the single transport. (Nissim, Event #2 feedback.)
- **Monotonic version lives on `events.live_timing_version`** (per-event), bumped in the action transaction; ETag derived from it. Chosen over `@Version` on `Session` because the polled resource is the event's whole live-timing snapshot, and over body-hash ETags because the counter gives a cheap 304 + ordering guarantee.
- **Organizer presence moves to DB (`live_timing_presence`, last-seen TTL).** Required for AC3 (task-independence) and to keep the action-authorization gate working without per-task memory.
- **Reuse `WatchSessionService` cascade methods from the new REST controller** rather than re-implementing — guarantees parity and reuses the existing integration-test oracle.
- **Phase strictly P1(web)→P2(watch)→P3(delete).** P3 must wait for a live watch build that has dropped STOMP.
- **(Resolved 2026-06-20) One long-lived story.** P2 (watch) and P3 (WS teardown) stay under story key 15.1 — 15.1 stays open until all three phases are live (accepts being blocked on the watch App Store review cycle for P3). Do NOT split into 15.1b/15.1c.
- **(Resolved 2026-06-20) Poll cadence: "LIVE" = a current/active session exists right now, both consumers same rate (~3–5 s).** Anonymous presenter and organizer live-control poll at the same interval; no separate slower presenter rate. Back off when the tab is hidden/idle (AC6). Conditional GET (304) keeps the steady-state cost cheap even for the public presenter screen.
- **(Resolved 2026-06-20) Presence is implicit via the GET poll, TTL ≈ 30 s.** The organizer's authenticated `GET …/live-timing` poll upserts `live_timing_presence.last_seen_at`; `organizerPresent = EXISTS(active row within TTL)`. No separate heartbeat/join endpoint. This functionally matches the old join/disconnect tracking. (Anonymous presenter polls do NOT count as presence — only authenticated organizer polls upsert.)
- **(Resolved 2026-06-20) Presenter + live-control are the ONLY web live consumers.** No other public live agenda/countdown subscribes to `/topic/events/{eventCode}/state`; `CountdownTimer` is static calendar-day and unaffected. No extra public surface to migrate.

## Open Questions

_All open questions resolved 2026-06-20 (Nissim) — see the last four entries under Resolved Decisions._

## Review Findings (adversarial code review, 2026-06-20)

5 findings; 4 fixed, 1 documented-as-pre-existing.
- **F1 (MED) — ETag/`version` covers only timing state, not `organizerPresent`/`arrivedSpeakerCount`/clock-derived `status`.** Resolved by **contract**: the version is deliberately timing-action-only (gating presenter refresh on presence/clock churn would force needless full-event refetches; the two web consumers read only timing via the conditional poll). Documented in the OpenAPI `version` description + `LiveTimingService.getLiveTiming` Javadoc. A future presence/arrival consumer (P2 watch) must not rely on `If-None-Match` for those advisory fields.
- **F2 (MED) — `useLiveSessionControl.deriveStatus` classifies an over-running, not-yet-ended session (`actualStartTime` set, past scheduled end) as COMPLETED, hiding Extend/Delay.** **NOT changed** — this is pre-existing, intentional "schedule-based view" behavior copied verbatim from the old hook; altering status-derivation semantics under a transport-only story risks a watch/web interaction regression. Tracked as a separate follow-up if the product wants overtime-active on web.
- **F3 (LOW) — `minutes` null for EXTEND/DELAY silently became a 0-min no-op that still bumped the version.** **Fixed:** `LiveTimingService.applyAction` now rejects null `minutes` for EXTEND/DELAY → 400 (`IllegalArgumentException`). Test `should_return400_when_extendActionMissingMinutes`.
- **F4 (LOW) — presence upsert ran before the event-existence check (junk rows for a bogus eventCode).** **Fixed:** `LiveTimingController.getLiveTiming` now calls `getVersion` (404 if missing) before `recordOrganizerPoll`. Test `should_return404_when_unknownEvent`.
- **F5 (LOW) — presence-row test hygiene.** **Fixed:** `LiveTimingControllerIntegrationTest.setUp` now also `presenceRepository.deleteAll()`.

## Dev Agent Record

### Agent Model Used

Amelia (claude-opus-4-8[1m]) — BMad dev-story.

### Debug Log References

- EMS live-timing tests: 15/15 green (`LiveTimingServiceIntegrationTest` 8, `LiveTimingControllerIntegrationTest` 7).
- Full EMS + api-gateway regression: BUILD SUCCESSFUL, 0 failures.
- web-frontend hook tests: 37/37; consuming components 89/89.
- Local-dev smoke (EMS booted, V118/V119 applied to populated DB): GET 200 + `ETag:"evt-BATbern55-0"`, matching `If-None-Match` → 304, gateway anonymous GET 200, unauthenticated POST 401.

### Completion Notes List

- **P1 complete and merged-ready.** Backend endpoints are additive + inert (WS untouched); frontend consumers migrated off STOMP. Each layer independently deployable.
- **Deviation (intentional):** hand-written controller/records (not a generated `*Api`) to match the existing hand-written watch subsystem; OpenAPI still authored for frontend type-gen.
- **Adversarial review:** 4 findings fixed, F2 (pre-existing over-running-status display) deferred — see Review Findings.
- **P2 (watch) + P3 (WS teardown)** remain deferred under this story key (per resolved decision to keep one long story); P3 is blocked until a watch build drops STOMP. Beta verification pending (manual, touches prod CloudFront).

### File List

**Backend (event-management-service):**
- `docs/api/events-api.openapi.yml` (M) — live-timing paths + `LiveTimingResponse`/`LiveTimingActionRequest` schemas
- `src/main/resources/db/migration/V118__add_live_timing_version.sql` (A)
- `src/main/resources/db/migration/V119__create_live_timing_presence.sql` (A)
- `src/main/java/ch/batbern/events/domain/Event.java` (M) — `liveTimingVersion`
- `src/main/java/ch/batbern/events/domain/LiveTimingPresence.java` (A)
- `src/main/java/ch/batbern/events/repository/EventRepository.java` (M) — increment/read version
- `src/main/java/ch/batbern/events/repository/LiveTimingPresenceRepository.java` (A)
- `src/main/java/ch/batbern/events/watch/LiveTimingService.java` (A)
- `src/main/java/ch/batbern/events/watch/LiveTimingController.java` (A)
- `src/main/java/ch/batbern/events/watch/dto/LiveTimingResponse.java` (A)
- `src/main/java/ch/batbern/events/watch/dto/LiveTimingActionRequest.java` (A)
- `src/main/java/ch/batbern/events/config/SecurityConfig.java` (M) — GET permitAll
- `src/test/java/ch/batbern/events/watch/LiveTimingServiceIntegrationTest.java` (A)
- `src/test/java/ch/batbern/events/watch/LiveTimingControllerIntegrationTest.java` (A)

**api-gateway:**
- `src/main/java/ch/batbern/gateway/config/SecurityConfig.java` (M) — GET permitAll (+ comment condensing to stay under method-length cap)

**web-frontend:**
- `src/services/liveTimingService.ts` (A)
- `src/hooks/useLiveSessionControl/useLiveSessionControl.ts` (M) — STOMP → polling
- `src/hooks/useLiveSessionControl/useLiveSessionControl.test.ts` (M) — rewritten
- `src/hooks/usePresentationData.ts` (M) — STOMP → polling
- `src/hooks/usePresentationData.test.ts` (M) — rewritten
- `src/types/generated/events-api.types.ts` (M) — regenerated

**BMad artifacts:**
- `_bmad-output/implementation-artifacts/15-1-websockets-to-rest-polling-live-agenda.md` (A)
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (M)

### Change Log

- 2026-06-20 — Story created (ready-for-dev), open questions resolved.
- 2026-06-20 — P1 implemented (backend endpoints + version/presence persistence + web migration off STOMP), 15 EMS IT + 37 hook tests green (commit `4781c4d9`).
- 2026-06-20 — Addressed code-review findings (F1/F3/F4/F5; F2 deferred), commit `7b94f66c`. Pushed; PR #800 → develop. Status → review.
