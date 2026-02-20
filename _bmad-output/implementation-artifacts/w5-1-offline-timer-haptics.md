# Story W5.1: Offline Timer & Haptics

Status: review

---

## Story

As an organizer,
I want countdown and haptics to continue working when WiFi drops,
so that I never lose time awareness during a live event.

## Acceptance Criteria

1. **AC1 — Countdown Continues Offline**: Given the countdown is running and WiFi drops, When the connection is lost, Then the countdown continues with zero interruption — no freeze, no reset, no drift — using wall-clock calculation against locally cached session end times.

2. **AC2 — Haptics Fire Offline**: Given I'm offline, When a haptic threshold is reached (5 min, 2 min, 0:00, overrun pulse, break gong), Then the haptic fires on schedule, calculated from the locally cached `CachedSession.scheduledEndTime`. No connectivity required for haptic delivery.

3. **AC3 — Connectivity Indicator Delayed 30s**: Given WiFi has been down for more than 30 seconds, When I glance at the Watch, Then the `ConnectionStatusBar` displays the offline indicator (orange WiFi icon). For transient drops under 30 seconds, the status bar does NOT show the offline state — minor glitches are tolerated silently.

## Tasks / Subtasks

- [x] **Task 1: Add 30s Debounce to Offline Indicator** (AC: #3)
  - [x] 1.1 In `Data/ConnectivityMonitor.swift`, add a `DispatchWorkItem` or `Task` that fires 30 seconds after a connectivity-lost event before calling `onConnectivityChanged(false)` downstream
  - [x] 1.2 Cancel the pending debounce work item immediately if connectivity is restored within 30s (WiFi flicker suppression)
  - [x] 1.3 Connectivity-restored transitions (`onConnectivityChanged(true)`) remain immediate — no debounce on reconnect
  - [x] 1.4 Verify `EventDataController.handleConnectivityChange(isConnected:)` (line 284) continues to receive the debounced signal correctly
  - [x] 1.5 Verify `ConnectionStatusBar` only shows orange when `EventDataController.isOffline == true` (no changes needed there — it already reads `isOffline`)

- [x] **Task 2: Verify Offline Timer (No Code Change Expected)** (AC: #1)
  - [x] 2.1 Read `Domain/SessionTimerEngine.swift` and confirm `recalculate()` computes remaining time from `clock.now` vs `session.scheduledEndTime` — not from a decrementing counter
  - [x] 2.2 Confirm no network call is made inside the timer tick path
  - [x] 2.3 If timer uses any online-only data source, fix it — otherwise document "verified no change needed"

- [x] **Task 3: Verify Offline Haptics (No Code Change Expected)** (AC: #2)
  - [x] 3.1 Read `Domain/HapticScheduler.swift` and confirm `evaluate(session:)` and `evaluateBreakGong(breakSession:)` read only from locally cached `WatchSession` fields (no network calls)
  - [x] 3.2 Confirm `WatchHapticService` (or equivalent) fires haptics regardless of connectivity state
  - [x] 3.3 If any haptic path touches network state, fix it — otherwise document "verified no change needed"

- [x] **Task 4: Write Tests** (AC: all)
  - [x] 4.1 In `ConnectivityMonitorTests.swift` (create if absent): test that `onConnectivityChanged(false)` is NOT called within 30s of connectivity loss
  - [x] 4.2 Test that `onConnectivityChanged(false)` IS called after 30s of sustained loss (use `MockClock` / `XCTestExpectation` with timeout)
  - [x] 4.3 Test that a connectivity-restored signal within 30s cancels the pending debounce (no false-positive offline event)
  - [x] 4.4 Test that `onConnectivityChanged(true)` fires immediately (no debounce delay on reconnect)

## Dev Notes

### What Already Works — Do NOT Reimplement

**`SessionTimerEngine` (wall-clock countdown):**
- File: `apps/BATbern-watch/BATbern-watch Watch App/Domain/SessionTimerEngine.swift`
- `recalculate()` computes `session.scheduledEndTime.timeIntervalSince(clock.now)` on every tick
- Result: countdown is inherently offline-capable — no network dependency whatsoever
- Tested by `SessionTimerEngineTests.swift` (~10 tests). Do not rewrite, do not duplicate.

**`HapticScheduler` (threshold alerts):**
- File: `apps/BATbern-watch/BATbern-watch Watch App/Domain/HapticScheduler.swift`
- `evaluate(session:)` reads `session.endTime` (locally cached) — no network calls
- `evaluateBreakGong(breakSession:)` reads `breakSession.endTime` — also purely local
- `fire(_:)` deduplicates so each alert fires at most once per session
- Already tested. Do not rewrite.

**`ConnectionStatusBar` (offline UI):**
- File: `apps/BATbern-watch/BATbern-watch Watch App/Views/Shared/ConnectionStatusBar.swift`
- Reads `EventDataController.isOffline` — no changes needed to the view itself
- Shows "Last updated" relative time already (Swiss German: "vor 2 Min.")

### What Needs Changing — The Only Real Work

**`ConnectivityMonitor` debounce (Task 1):**
- File: `apps/BATbern-watch/BATbern-watch Watch App/Data/ConnectivityMonitor.swift`
- Current behavior: calls `onConnectivityChanged(false)` immediately on path loss
- Required behavior: delay that call by 30s; cancel if path recovers before timer fires
- Pattern to use:
  ```swift
  private var offlineDebounceTask: Task<Void, Never>?

  // In the path-lost branch:
  offlineDebounceTask?.cancel()
  offlineDebounceTask = Task {
      try? await Task.sleep(for: .seconds(30))
      guard !Task.isCancelled else { return }
      await MainActor.run { onConnectivityChanged(false) }
  }

  // In the path-restored branch:
  offlineDebounceTask?.cancel()
  offlineDebounceTask = nil
  onConnectivityChanged(true)  // immediate
  ```
- This is the **entire scope of W5.1**. Everything else is verification, not new code.

### Architecture Constraints

- **Do NOT** set `EventDataController.isOffline` directly from `ConnectivityMonitor` — the signal flows through `handleConnectivityChange(isConnected:)` at line 284 of `EventDataController.swift`. The debounce belongs in `ConnectivityMonitor`, not in `EventDataController`.
- **Do NOT** add any connectivity check to `SessionTimerEngine` or `HapticScheduler` — they are intentionally connectivity-agnostic.
- **WebSocket reconnect is NOT affected** — `WebSocketClient.scheduleReconnect()` (exponential backoff) must still fire immediately on disconnect, before the 30s UI debounce. Only the UI status indicator is debounced.

### References

- [Source: docs/watch-app/epics.md#W5.1] — Story AC definitions
- [Source: docs/watch-app/architecture.md] — Wall-clock timer mandate (NFR10)
- [Source: apps/BATbern-watch/BATbern-watch Watch App/Data/ConnectivityMonitor.swift] — Target file for debounce
- [Source: apps/BATbern-watch/BATbern-watch Watch App/Data/EventDataController.swift] — `handleConnectivityChange()`, `isOffline`
- [Source: apps/BATbern-watch/BATbern-watch Watch App/Views/Shared/ConnectionStatusBar.swift] — Reads `isOffline`
- [Source: apps/BATbern-watch/BATbern-watch Watch App/Domain/SessionTimerEngine.swift] — Wall-clock countdown (already offline-safe)
- [Source: apps/BATbern-watch/BATbern-watch Watch App/Domain/HapticScheduler.swift] — Threshold alerts (already offline-safe)

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- `/tmp/w5-1-build.log` — Initial build (1 compile error: missing `self.` in OSLog autoclosure)
- `/tmp/w5-1-build2.log` — Build SUCCEEDED after fix
- `/tmp/w5-1-conn-tests4.log` — ConnectivityMonitor 9/9 tests passed
- `/tmp/w5-1-full-tests.log` — Full suite 291 tests; 1 pre-existing flaky ArrivalTracker failure (unrelated, passes in isolation)

### Completion Notes List

- **Task 1 (real work):** Added `processConnectivityChange(isConnected:)` @MainActor method to `ConnectivityMonitor` with a `Task`-based 30s debounce for offline transitions; online transitions remain immediate. Added `offlineDebounceSeconds: TimeInterval = 30` as injectable parameter for test speed. `stop()` now also cancels any pending debounce task. `start()` refactored to delegate to `processConnectivityChange`. OSLog autoclosure required explicit `self.` on `offlineDebounceSeconds` property reference — compiler error resolved.
- **Task 2 (verified, no change):** `SessionTimerEngine.recalculate()` line 52 uses `session.endTime.timeIntervalSince(clock.now)` — pure wall-clock, no network dependency. Confirmed offline-safe as designed.
- **Task 3 (verified, no change):** `HapticScheduler.evaluate()` and `evaluateBreakGong()` read only locally cached `WatchSession.endTime` — no network calls, no connectivity checks. Confirmed offline-safe as designed.
- **Task 4 (tests):** Added 4 new tests to `ConnectivityMonitorTests.swift` using `offlineDebounceSeconds = 0.2–0.3` for speed. All 4 pass. Also fixed 3 pre-existing compilation errors in test files (`BreakGongViewTests`, `EventCompletedViewTests`, `BreakCardLayoutTests`) caused by prior model changes that had not been updated in the tests.
- **Architecture constraint respected:** WebSocket reconnect path (exponential backoff) is untouched — only `onConnectivityChanged(false)` to `EventDataController.handleConnectivityChange` is debounced.

### File List

- `apps/BATbern-watch/BATbern-watch Watch App/Data/ConnectivityMonitor.swift` — Added 30s debounce via `processConnectivityChange(isConnected:)`, `offlineDebounceSeconds`, `offlineDebounceTask`
- `apps/BATbern-watch/BATbern-watch Watch AppTests/Data/ConnectivityMonitorTests.swift` — Added 4 debounce tests (4.1–4.4)
- `apps/BATbern-watch/BATbern-watch Watch AppTests/Views/BreakGongViewTests.swift` — Fixed pre-existing: added `typicalStartTime`/`typicalEndTime` to `CachedEvent` init calls
- `apps/BATbern-watch/BATbern-watch Watch AppTests/Views/EventCompletedViewTests.swift` — Fixed pre-existing: added `eventTitle` arg to `EventCompletedView` init
- `apps/BATbern-watch/BATbern-watch Watch AppTests/Views/BreakCardLayoutTests.swift` — Fixed pre-existing: added `import Foundation`

## Change Log

- 2026-02-20: W5.1 implemented — 30s offline debounce added to `ConnectivityMonitor`. Timer and haptics verified offline-safe (no changes needed). 4 new tests added. 3 pre-existing test compilation errors fixed.
