# Story W5.3: Connectivity Monitoring & Adaptive Behavior

Status: ready-for-dev

---

## Story

As an organizer,
I want the app to adapt its behavior based on connectivity and battery,
so that it's always reliable and battery-efficient.

## Acceptance Criteria

1. **AC1 — Seamless Offline Mode on WiFi Loss**: Given WiFi drops, When the transition occurs, Then the app enters offline mode automatically with no user action required (NFR10). Countdown, haptics, and cached schedule remain fully functional. The WebSocket client begins reconnect attempts immediately (existing exponential backoff).

2. **AC2 — Auto-Reconnect, State Sync, and Queue Replay**: Given the app is offline with queued actions (from W5.2), When WiFi connectivity is restored, Then: (a) the WebSocket reconnects automatically, (b) queued actions are replayed to the backend in order, (c) fresh server state is synced via `syncIfNeeded()` — all without any user action. The Watch reconciles to server-authoritative state.

3. **AC3 — Adaptive Polling Below 20% Battery**: Given the battery level drops below 20%, When the periodic background refresh timer fires, Then the polling interval extends from 5 minutes to 15 minutes, reducing background network activity to preserve battery for haptics and display (NFR23).

4. **AC4 — Battery Above 30% After 3 Hours**: Given the app runs continuously for a 3-hour event with active WebSocket, haptics, and display, When I check battery at event end, Then battery is above 30% remaining (NFR21, NFR22: <15% battery impact for 4-hour event).

## Tasks / Subtasks

- [ ] **Task 1: Verify Seamless Offline Mode (No Code Change Expected)** (AC: #1)
  - [ ] 1.1 Confirm `ConnectivityMonitor` uses `NWPathMonitor` and fires callbacks on path change with no user action
  - [ ] 1.2 Confirm `EventDataController.handleConnectivityChange(isConnected: false)` sets `isOffline = true` correctly (after W5.1's 30s debounce for the UI, but WebSocket reconnect must start immediately — verify these are separate paths)
  - [ ] 1.3 Confirm `WebSocketClient.scheduleReconnect()` fires immediately on disconnect (independent of the W5.1 UI debounce)
  - [ ] 1.4 Document "verified, no change" or fix any gap found

- [ ] **Task 2: Verify Auto-Reconnect + State Sync (Depends on W5.2)** (AC: #2)
  - [ ] 2.1 Confirm `WebSocketClient.scheduleReconnect()` uses exponential backoff (2s, 4s, 8s … 60s max) and reconnects automatically when WiFi returns
  - [ ] 2.2 Confirm `EventDataController.handleConnectivityChange(isConnected: true)` (after W5.2's changes) calls `replayPendingActions()` then `syncIfNeeded()` in order
  - [ ] 2.3 If W5.2 is not yet merged, stub the test with `// TODO: depends on W5.2`
  - [ ] 2.4 Manual smoke test: disconnect WiFi mid-event, perform an action (expect queue), restore WiFi, confirm replay within 5s

- [ ] **Task 3: Create `BatteryMonitor`** (AC: #3)
  - [ ] 3.1 Create `apps/BATbern-watch/BATbern-watch Watch App/Data/BatteryMonitor.swift`
  - [ ] 3.2 Enable battery monitoring on init: `WKInterfaceDevice.current().isBatteryMonitoringEnabled = true`
  - [ ] 3.3 Expose `var batteryLevel: Float { WKInterfaceDevice.current().batteryLevel }` — returns 0.0–1.0 (or -1.0 if unknown)
  - [ ] 3.4 Expose `var isLowBattery: Bool { batteryLevel >= 0 && batteryLevel < 0.20 }` — false if level unknown (-1.0)
  - [ ] 3.5 Publish battery changes via `NotificationCenter` observer on `WKApplicationDidBecomeActiveNotification` (or use a timer-based check every 60s to avoid excessive callbacks)
  - [ ] 3.6 Protocol-back for testability: define `BatteryMonitorProtocol` with `var isLowBattery: Bool` and `var batteryLevel: Float`; `BatteryMonitor` conforms; `MockBatteryMonitor` in test target

- [ ] **Task 4: Wire Adaptive Polling into `EventDataController`** (AC: #3)
  - [ ] 4.1 In `Data/EventDataController.swift`, inject `BatteryMonitorProtocol` via initializer
  - [ ] 4.2 In `startPeriodicRefresh()` (line 272): replace the fixed 5-minute interval with an adaptive interval:
    ```swift
    private var refreshInterval: Duration {
        batteryMonitor.isLowBattery ? .seconds(15 * 60) : .seconds(5 * 60)
    }
    ```
  - [ ] 4.3 Re-evaluate the interval on each timer fire (not just on start) so battery state is always current
  - [ ] 4.4 Log the chosen interval when it changes (debug build only)

- [ ] **Task 5: Write Tests** (AC: #3)
  - [ ] 5.1 Create `BatteryMonitorTests.swift`:
    - `isLowBattery` returns `true` when `batteryLevel = 0.15`
    - `isLowBattery` returns `false` when `batteryLevel = 0.50`
    - `isLowBattery` returns `false` when `batteryLevel = -1.0` (unknown — fail-safe: do NOT reduce polling when level is unknown)
  - [ ] 5.2 In `EventDataControllerTests.swift` (extend existing):
    - With `MockBatteryMonitor(isLowBattery: true)` → refresh interval is 15 min
    - With `MockBatteryMonitor(isLowBattery: false)` → refresh interval is 5 min
    - Interval re-evaluates per tick (inject monitor that changes state mid-run)

- [ ] **Task 6: Add `MockBatteryMonitor` to Test Target** (AC: #3, #5)
  - [ ] 6.1 Create `apps/BATbern-watch/BATbern-watch Watch AppTests/Mocks/MockBatteryMonitor.swift`
  - [ ] 6.2 Conform to `BatteryMonitorProtocol` with settable `isLowBattery` and `batteryLevel`
  - [ ] 6.3 Follow existing mock pattern (`MockClock`, `MockAPIClient`)

## Dev Notes

### What Already Works — Do NOT Reimplement

**`ConnectivityMonitor` (seamless offline):**
- File: `apps/BATbern-watch/BATbern-watch Watch App/Data/ConnectivityMonitor.swift`
- Uses `NWPathMonitor` — automatic, no user action required (NFR10 satisfied already)
- Fires `onConnectivityChanged` on path changes

**`WebSocketClient.scheduleReconnect()` (auto-reconnect):**
- File: `apps/BATbern-watch/BATbern-watch Watch App/Data/WebSocketClient.swift`
- Exponential backoff: `min(pow(2.0, Double(reconnectAttempt)), 60.0)` seconds (2s → 4s → 8s → … → 60s max)
- Fires immediately on disconnect — independent of the W5.1 UI debounce

**`EventDataController.handleConnectivityChange()` (auto-sync on reconnect):**
- File: `apps/BATbern-watch/BATbern-watch Watch App/Data/EventDataController.swift` (line 284)
- After W5.2: calls `replayPendingActions()` then `syncIfNeeded()` — fully automatic

**`EventDataController.startPeriodicRefresh()` (line 272):**
- Current: fixed 5-minute interval, skips when offline
- Task 4 adds adaptive interval based on battery — minimal change

### Architecture Constraints

- **`BatteryMonitorProtocol` for testability** — `WKInterfaceDevice` is not injectable in tests. The protocol + mock pattern (same as `ClockProtocol` / `MockClock`) is mandatory.
- **Fail-safe on unknown battery level** — when `batteryLevel == -1.0` (Simulator, or monitoring not yet available), `isLowBattery` MUST return `false`. Do not reduce polling when battery state is unknown.
- **WebSocket reconnect is NOT adaptive** — exponential backoff already balances speed and battery. Do NOT introduce battery-level gating on reconnect attempts.
- **Haptics are never throttled** — haptic delivery is safety-critical for event management. `HapticScheduler` must never check battery state.
- **No second timer** — adaptive polling modifies the interval of the existing `startPeriodicRefresh()` timer. Do NOT create a parallel refresh timer.
- **W5.2 dependency for AC2** — AC2 (queue replay on reconnect) is implemented in W5.2. If W5.3 ships before W5.2, AC2 is not testable end-to-end; document the dependency and mark the smoke test as blocked.

### Dependency on W5.2

AC2 (replay queued actions on reconnect) requires `OfflineActionQueue` and the `replayPendingActions()` method from W5.2. The `EventDataController` injection point (Task 2) is shared. If running stories in parallel, coordinate on `EventDataController` changes to avoid merge conflicts.

### References

- [Source: docs/watch-app/epics.md#W5.3] — Story AC definitions (NFR10, NFR21, NFR22, NFR23)
- [Source: apps/BATbern-watch/BATbern-watch Watch App/Data/ConnectivityMonitor.swift] — `NWPathMonitor` wrapper (Task 1 verification)
- [Source: apps/BATbern-watch/BATbern-watch Watch App/Data/WebSocketClient.swift] — `scheduleReconnect()`, exponential backoff (Task 2 verification)
- [Source: apps/BATbern-watch/BATbern-watch Watch App/Data/EventDataController.swift] — `startPeriodicRefresh()` (line 272), `handleConnectivityChange()` (line 284)
- [Source: apps/BATbern-watch/BATbern-watch Watch App/Protocols/ClockProtocol.swift] — Protocol pattern to follow for `BatteryMonitorProtocol`
- [Source: apps/BATbern-watch/BATbern-watch Watch AppTests/Mocks/MockClock.swift] — Mock pattern to follow for `MockBatteryMonitor`
- [Source: w5-2-action-queue-sync-recovery.md] — W5.2 dependency for AC2

## Dev Agent Record

### Agent Model Used

_To be filled by Dev agent_

### Debug Log References

_To be filled by Dev agent_

### Completion Notes List

_To be filled by Dev agent_

### File List

_To be filled by Dev agent_
