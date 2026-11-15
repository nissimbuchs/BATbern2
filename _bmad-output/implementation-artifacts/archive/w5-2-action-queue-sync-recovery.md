# Story W5.2: Action Queue & Sync Recovery

Status: done

---

## Story

As an organizer,
I want my offline actions to queue locally and sync when connectivity returns,
so that no actions are lost even during WiFi outages.

## Acceptance Criteria

1. **AC1 — Actions Queue When Offline**: Given I'm offline (WebSocket disconnected), When I tap "Done", trigger an extend, or trigger a delayed-session action, Then the action is enqueued in a persistent local queue instead of being dropped or throwing an error. The UI responds optimistically (same as online — action confirm haptic, view update) while the action awaits connectivity.

2. **AC2 — Queue Survives App Restart**: Given queued actions exist and the app is killed or crashes, When the organizer reopens the app, Then all previously queued actions are still present and will replay when WiFi returns. (NFR11)

3. **AC3 — Replay on Connectivity Restored**: Given one or more actions are queued, When WiFi connectivity is restored and the WebSocket reconnects, Then all queued actions are replayed to the backend in the order they were queued, within 5 seconds of reconnect. After successful replay, the queue is cleared.

4. **AC4 — Server Reconciles Idempotently**: Given a queued action conflicts with server state (e.g., another organizer already advanced the session), When the action is replayed, Then the server resolves the conflict idempotently (last-write-wins or ignore-stale, per existing backend contract). The Watch then reconciles to the authoritative server state received in the `STATE_UPDATE` broadcast — no client-side merge logic.

5. **AC5 — Cached Schedule Preserved on Restart**: Given the app is offline and the organizer restarts the app, When the app relaunches, Then the cached schedule and current session state are restored from SwiftData — the organizer sees the same schedule they had before the restart.

## Tasks / Subtasks

- [x] **Task 1: Create `OfflineAction` SwiftData Model** (AC: #2)
  - [x] 1.1 Create `apps/BATbern-watch/BATbern-watch Watch App/Models/OfflineAction.swift`
  - [x] 1.2 Define `@Model class OfflineAction` with fields:
    - `id: UUID` (auto-generated, `@Attribute(.unique)`)
    - `actionType: String` (e.g. "endSession", "extendSession", "delayToPrevious", "speakerArrived")
    - `payload: Data` (JSON-encoded `WatchActionDto` — same encoding as `WebSocketClient.sendAction`)
    - `enqueuedAt: Date`
    - `attemptCount: Int` (default 0)
  - [x] 1.3 Add `OfflineAction` to the `ModelContainer` in `BATbernWatchApp.swift`

- [x] **Task 2: Create `OfflineActionQueue` Service** (AC: #1, #2, #3)
  - [x] 2.1 Create `apps/BATbern-watch/BATbern-watch Watch App/Data/OfflineActionQueue.swift`
  - [x] 2.2 Inject `ModelContext` via initializer for SwiftData persistence
  - [x] 2.3 Implement `enqueue(_ action: WatchAction)`:
    - Encode `action` to JSON `Data` (reuse the `WatchActionDto` encoder already in `WebSocketClient`)
    - Insert an `OfflineAction` record into SwiftData and save
  - [x] 2.4 Implement `pendingActions() -> [OfflineAction]`: fetch all `OfflineAction` records sorted by `enqueuedAt` ascending
  - [x] 2.5 Implement `remove(_ action: OfflineAction)`: delete a single record after successful replay
  - [x] 2.6 Implement `clearAll()`: delete all records (used after full successful replay)

- [x] **Task 3: Wire Queue into `WebSocketClient.sendAction()`** (AC: #1)
  - [x] 3.1 In `Data/WebSocketClient.swift`, inject `OfflineActionQueue` via initializer
  - [x] 3.2 In `sendAction(_ action: WatchAction)`: if `!isConnected`, call `offlineActionQueue.enqueue(action)` instead of throwing — return normally (no error surface to caller)
  - [x] 3.3 When online and sending succeeds, do NOT enqueue (current path unchanged)
  - [x] 3.4 Keep the existing `guard isConnected else { throw }` path replaced by the queue path — callers (ViewModels) do not need to change

- [x] **Task 4: Wire Replay into `EventDataController.handleConnectivityChange()`** (AC: #3, #4)
  - [x] 4.1 In `Data/EventDataController.swift`, inject `OfflineActionQueue` via initializer
  - [x] 4.2 In `handleConnectivityChange(isConnected: true)` branch: replay BEFORE sync (Dev Notes mandate: replay first, then get fresh state)
  - [x] 4.3 Implement `replayPendingActions()` as a private `async` method:
    - Fetch `offlineActionQueue.pendingActions()`
    - For each action in order: decode via `WatchActionDto.toWatchAction()`, send via `webSocketClient.sendAction()`
    - On success: call `offlineActionQueue.remove(action)`
    - On failure: `queue.markFailed(action)` — returns true at attempt 3 → also remove
    - After drain: call `syncIfNeeded()` to reconcile with server state

- [x] **Task 5: Verify Cached Schedule Survives Restart** (AC: #5)
  - [x] 5.1 Confirm `LocalCache.swift` uses SwiftData `@Model` persistence (not in-memory only)
  - [x] 5.2 Confirm `EventDataController` loads from `LocalCache` on init before first network call
  - [x] 5.3 Verified, no change needed — `loadCachedData()` is called at init time before any network sync

- [x] **Task 6: Write Tests** (AC: all)
  - [x] 6.1 `OfflineActionQueueTests.swift` — 11 tests covering enqueue/persist/reload/order/remove/clearAll/markFailed/WatchActionDto round-trips
  - [x] 6.2 `WebSocketClientTests.swift` (extended) — 3 offline sendAction tests: enqueues, preserves order, no crash without queue
  - [x] 6.3 `EventDataControllerOfflineTests.swift` — 6 tests: replays queued actions, queue cleared after replay, action dropped at 3 attempts, skipped when WS not connected, empty queue no-op, syncIfNeeded called after drain

### Review Follow-ups (AI) — v1.1

- [x] [AI-Review][HIGH] H2: `clearAll()` was dead production code despite Task 2.6 saying "used after full successful replay". Updated docstrings on `OfflineActionQueueProtocol.clearAll()` and `OfflineActionQueue.clearAll()` to clarify it is an admin/reset utility — the replay path intentionally uses per-action `remove()` for safer partial drain handling [OfflineActionQueueProtocol.swift:24, OfflineActionQueue.swift:80]
- [x] [AI-Review][MEDIUM] M1: `replayPendingActions()` called `syncIfNeeded()` internally AND `handleConnectivityChange()` called it again immediately after — double-sync silently no-op'd by 60s cooldown for non-empty queue case. Removed internal `syncIfNeeded()` call from `replayPendingActions()`; caller (`handleConnectivityChange`) is now the single sync trigger for both empty and non-empty queue paths [EventDataController.swift:335-385]
- [x] [AI-Review][MEDIUM] M2: AC3 "within 5 seconds" timing guarantee had no test. Added `connectivityRestored_replayCompletesWithin5Seconds` test with elapsed-time assertion [EventDataControllerOfflineTests.swift]
- [x] [AI-Review][MEDIUM] M3: If WebSocket disconnects mid-replay, `sendAction()` silently re-enqueues the action to the same queue (offline path), then `queue.remove(action)` on the original fires — producing a duplicate with reset `attemptCount`. Added per-iteration `guard webSocket.isConnected else { return }` inside the replay loop; bail out early, leaving unprocessed actions for next connectivity-restore event [EventDataController.swift:352-356]

## Dev Notes

### What Already Exists — Do NOT Reimplement

**`WatchAction` enum + encoding:**
- File: `apps/BATbern-watch/BATbern-watch Watch App/Protocols/WebSocketClientProtocol.swift`
- Action types already defined: `startSession`, `endSession`, `skipSession`, `extendSession`, `delayToPrevious`, `speakerArrived`
- `WatchActionDto` JSON encoding already in `WebSocketClient.swift` (line ~510)
- Reuse this encoding directly in `OfflineActionQueue.enqueue()` — do not define a new encoding format

**`WebSocketClient.sendAction()` (line 188):**
- File: `apps/BATbern-watch/BATbern-watch Watch App/Data/WebSocketClient.swift`
- Current guard: `guard let task = webSocketTask, isConnected else { throw }`
- Replace the `throw` branch only — online path is unchanged

**`EventDataController.handleConnectivityChange()` (line 284):**
- File: `apps/BATbern-watch/BATbern-watch Watch App/Data/EventDataController.swift`
- Currently calls `syncIfNeeded()` when `isConnected && wasOffline`
- Add `replayPendingActions()` call BEFORE `syncIfNeeded()` — replay first, then get fresh state

**`EventDataController.applyServerState()` (line 124):**
- Already server-authoritative — `STATE_UPDATE` broadcasts after replay will automatically reconcile the Watch to server state. No client-side merge logic needed.

**`LocalCache.swift` (SwiftData):**
- File: `apps/BATbern-watch/BATbern-watch Watch App/Data/LocalCache.swift`
- Already uses SwiftData persistence. `CachedEvent`, `CachedSession`, `CachedSpeaker` survive app restart.
- Task 5 is verification only.

### Architecture Constraints

- **Persistent queue only** — `OfflineAction` MUST use `@Model` / SwiftData, NOT `UserDefaults` or in-memory array. NFR11 mandates app-restart durability.
- **Ordered replay** — actions must replay in `enqueuedAt` ascending order. Ensure `pendingActions()` sorts by `enqueuedAt`.
- **No client-side conflict resolution** — if the server returns an error or a conflicting `STATE_UPDATE`, the Watch accepts the server's version unconditionally. `applyServerState()` already handles this.
- **Optimistic UI** — when an action is enqueued offline, the UI should respond exactly as it would online (haptic + view update). The ViewModel must NOT wait for server confirmation before updating local state.
- **3-attempt cap** — stale actions (attempt ≥ 3) should be dropped silently. A fresh `syncIfNeeded()` after replay drain will bring the Watch to the correct state.
- **Injection pattern** — `OfflineActionQueue` is injected into both `WebSocketClient` and `EventDataController` via initializer, consistent with the existing protocol-based DI pattern throughout the codebase.

### References

- [Source: docs/watch-app/epics.md#W5.2] — Story AC definitions (NFR11: survives app restart)
- [Source: apps/BATbern-watch/BATbern-watch Watch App/Data/WebSocketClient.swift] — `sendAction()` target (line 188), `WatchActionDto` encoding (line ~510)
- [Source: apps/BATbern-watch/BATbern-watch Watch App/Data/EventDataController.swift] — `handleConnectivityChange()` (line 284), `applyServerState()` (line 124), `syncIfNeeded()`
- [Source: apps/BATbern-watch/BATbern-watch Watch App/Protocols/WebSocketClientProtocol.swift] — `WatchAction` enum, all action types
- [Source: apps/BATbern-watch/BATbern-watch Watch App/Data/LocalCache.swift] — SwiftData persistence (Task 5 verification)
- [Source: apps/BATbern-watch/BATbern-watch Watch App/App/BATbernWatchApp.swift] — `ModelContainer` configuration (add `OfflineAction`)

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- `/tmp/w5-2-full-suite-v2.log` — Final full test run (312 tests, all pass)

### Completion Notes List

1. **`WatchActionDto` promoted from `private Encodable` to `internal Codable`** — required for offline replay decode path. Added `toWatchAction() -> WatchAction?` for round-trip decode.
2. **`OfflineActionQueueProtocol` created** — DI contract injected into both `WebSocketClient` and `EventDataController` via initializer, consistent with existing protocol-based DI pattern.
3. **`sendAction()` protocol signature unchanged** — offline path returns normally instead of throwing; callers (ViewModels) require no changes.
4. **`markFailed()` returns Bool** — simpler API: `true` means drop, caller removes. No need for a separate `shouldDrop` computed property.
5. **Replay BEFORE sync in `handleConnectivityChange()`** — per Dev Notes mandate; `replayPendingActions()` called first, then `syncIfNeeded()`.
6. **Task 5 verified, no changes needed** — `loadCachedData()` already called at init time before first network call; `LocalCache` already uses file-backed SwiftData.
7. **SwiftData test crash fix** — `EventDataControllerOfflineTests` used two separate in-memory `ModelContainer`s (one for EDC, one for queue). This caused `EXC_BREAKPOINT (SIGTRAP)` in `modelContext.insert()`. Fix: single combined container with all model types + `ModelContext(container)` (not `mainContext`), matching the pattern used by other passing test suites.
8. **`failedReplay_actionDroppedAt3Attempts` test redesigned** — original 3-cycle connect/disconnect simulation caused race conditions. Replaced with pre-populating `attemptCount = 2` via two direct `markFailed()` calls before a single reconnect cycle (tests same AC#4 invariant, deterministically).
9. **Code review v1.1 fixes** — (a) removed double `syncIfNeeded()` from `replayPendingActions()` (caller owns sync); (b) added per-iteration `guard webSocket.isConnected` inside replay loop to prevent mid-replay double-enqueue; (c) added AC3 timing test (`< 5s`); (d) updated `clearAll()` docstrings to clarify it is an admin utility, not the replay drain mechanism.

### File List

**New files:**
- `apps/BATbern-watch/BATbern-watch Watch App/Models/OfflineAction.swift`
- `apps/BATbern-watch/BATbern-watch Watch App/Protocols/OfflineActionQueueProtocol.swift`
- `apps/BATbern-watch/BATbern-watch Watch App/Data/OfflineActionQueue.swift`
- `apps/BATbern-watch/BATbern-watch Watch AppTests/Mocks/MockOfflineActionQueue.swift`
- `apps/BATbern-watch/BATbern-watch Watch AppTests/Data/OfflineActionQueueTests.swift`
- `apps/BATbern-watch/BATbern-watch Watch AppTests/Data/EventDataControllerOfflineTests.swift`

**Modified files:**
- `apps/BATbern-watch/BATbern-watch Watch App/Data/WebSocketClient.swift` — offline queue injection + sendAction offline path + WatchActionDto promoted to Codable with toWatchAction()
- `apps/BATbern-watch/BATbern-watch Watch App/Data/EventDataController.swift` — offline queue + webSocketClient injection + replayPendingActions() + handleConnectivityChange() updated
- `apps/BATbern-watch/BATbern-watch Watch App/App/BATbernWatchApp.swift` — OfflineAction added to schema + OfflineActionQueue wired into WebSocketClient and EventDataController
- `apps/BATbern-watch/BATbern-watch Watch AppTests/Data/WebSocketClientTests.swift` — 3 offline sendAction tests added

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2026-02-20 | 1.0 | Initial implementation — all tasks complete, 312/312 tests pass | claude-sonnet-4-6 |
| 2026-02-20 | 1.1 | Code review fixes: double-sync removed, mid-replay disconnect guard, AC3 timing test, clearAll docs | claude-sonnet-4-6 |
