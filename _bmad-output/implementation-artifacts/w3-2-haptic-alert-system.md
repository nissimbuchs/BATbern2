# Story W3.2: Haptic Alert System

Status: ready-for-dev

## Story

As an organizer,
I want to feel distinct haptic patterns at key moments,
so that I know the time state without looking at my Watch.

## Acceptance Criteria

1. **AC1 — 5-Minute Warning**: Given 5 minutes remain in a session, When the threshold is reached, Then I feel a single firm haptic tap (NFR2: within 1 second of threshold crossing).

2. **AC2 — 2-Minute Warning**: Given 2 minutes remain, When the threshold is reached, Then I feel a double haptic tap — distinctly different from the 5-min pattern.

3. **AC3 — Time's Up**: Given 0:00 is reached, When time expires, Then I feel a triple haptic tap — distinctly different from the 2-min pattern.

4. **AC4 — Overrun Pulse**: Given a session is overrunning, When each additional minute passes (every 30s in `HapticScheduler.Thresholds.overtimeInterval`), Then I feel a rhythmic pulse pattern.

5. **AC5 — Break Gong**: Given a break is active, When the configured pre-end time is reached (default: 60s before break ends), Then I feel the gong reminder haptic (triple tap — "last call" feel).

6. **AC6 — Background Delivery**: Given the app is in the background (NFR9), When a haptic threshold is reached, Then the haptic still fires via Extended Runtime session.

7. **AC7 — Simultaneous Delivery**: Given all 4 organizer watches have the same cached `scheduledEndTime`, When a threshold is crossed, Then all watches calculate the same threshold moment independently from wall-clock time and fire within the same 1-second window (FR16 — simultaneity without network coordination, inherent to wall-clock approach).

## Tasks / Subtasks

- [ ] **Task 1: Create WatchHapticService (production implementation)** (AC: 1, 2, 3, 4, 5, 6)
  - [ ] 1.1 Create `apps/BATbern-watch/BATbern-watch Watch App/Data/WatchHapticService.swift`
  - [ ] 1.2 `@MainActor final class WatchHapticService: NSObject, HapticServiceProtocol, WKExtendedRuntimeSessionDelegate`
  - [ ] 1.3 Implement `play(_ alert: HapticAlert)` with `WKHapticType` mapping:
    ```swift
    // Single firm tap
    case .fiveMinuteWarning:
        WKInterfaceDevice.current().play(.notification)

    // Double tap — play twice with 200ms gap
    case .twoMinuteWarning:
        WKInterfaceDevice.current().play(.notification)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
            WKInterfaceDevice.current().play(.notification)
        }

    // Triple tap — play three times with 150ms gaps
    case .timesUp, .gongReminder:
        WKInterfaceDevice.current().play(.notification)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
            WKInterfaceDevice.current().play(.notification)
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.30) {
            WKInterfaceDevice.current().play(.notification)
        }

    // Rhythmic pulse
    case .overtimePulse:
        WKInterfaceDevice.current().play(.retry)

    // Confirm / error
    case .actionConfirm:  WKInterfaceDevice.current().play(.success)
    case .connectionLost: WKInterfaceDevice.current().play(.failure)
    ```
  - [ ] 1.4 Implement `schedule(_ alert: HapticAlert, at date: Date)` — store in `[(alert, date)]` array; fired on next `play()` call or by a background-safe timer
  - [ ] 1.5 Implement `cancelAll()` and `cancel(_ alert:)` — remove from scheduled queue
  - [ ] 1.6 Add `startEventSession()` — creates and starts a `WKExtendedRuntimeSession` (keeps app alive in background for haptic delivery, NFR9)
  - [ ] 1.7 Add `stopEventSession()` — invalidates the extended runtime session
  - [ ] 1.8 Implement `WKExtendedRuntimeSessionDelegate`:
    - `extendedRuntimeSessionDidStart()` — log session active
    - `extendedRuntimeSession(_:didInvalidateWith:error:)` — log + clear reference
    - `extendedRuntimeSessionWillExpire()` — attempt to restart session if event still live

- [ ] **Task 2: Wire into LiveCountdownViewModel** (AC: 1, 2, 3, 4, 5, 6)
  - [ ] 2.1 In `LiveCountdownViewModel.swift` (created in W3.1), ensure `hapticService: HapticServiceProtocol` is an injected dependency — it should already be injectable per W3.1 design
  - [ ] 2.2 Update the production instantiation site (likely `BATbernWatchApp.swift` or `OrganizerZoneView.swift`) to inject `WatchHapticService()` instead of any stub
  - [ ] 2.3 Call `watchHapticService.startEventSession()` when entering `LiveCountdownView.onAppear`
  - [ ] 2.4 Call `watchHapticService.stopEventSession()` when leaving `LiveCountdownView.onDisappear`
  - [ ] 2.5 Verify `HapticScheduler.evaluateBreakGong()` is called on the active break session during the 1s timer tick in `LiveCountdownViewModel` (break sessions: `sessionType == .breakTime || .lunch`)

- [ ] **Task 3: Extended Runtime Lifecycle Management** (AC: 6)
  - [ ] 3.1 Verify `WKExtendedRuntimeSession` is imported from `WatchKit` (not `Foundation`) — `import WatchKit` required in `WatchHapticService.swift`
  - [ ] 3.2 Ensure `Info.plist` has `WKBackgroundModes` entry if required by watchOS 11 for extended runtime (check: watchOS 11 may not require explicit plist entry for extended runtime sessions)
  - [ ] 3.3 Verify session lifetime covers full 3-hour event — `WKExtendedRuntimeSession` has no time limit but can expire; handle `extendedRuntimeSessionWillExpire()` by restarting

- [ ] **Task 4: Tests** (AC: 1, 2, 3, 4, 5)
  - [ ] 4.1 **DO NOT** rewrite `HapticSchedulerTests.swift` — it already covers all threshold logic with `MockHapticService` ✅
  - [ ] 4.2 Create `apps/BATbern-watch/BATbern-watch Watch AppTests/Data/WatchHapticServiceTests.swift`
  - [ ] 4.3 Test: `WatchHapticService` conforms to `HapticServiceProtocol` (compile-time check)
  - [ ] 4.4 Test: `cancel()` removes the correct alert from scheduled queue
  - [ ] 4.5 Test: `cancelAll()` clears all scheduled alerts
  - [ ] 4.6 Test: scheduling same alert twice results in two entries (no dedup in service — dedup is `HapticScheduler`'s job)
  - [ ] 4.7 **Integration test** (manual / on-device verification): All 5 alert types feel distinct — 5-min (single), 2-min (double), time's up (triple), overrun (pulse), gong (triple with "gong" feel). **Cannot be automated** — haptics don't work in Simulator.
  - [ ] 4.8 Test: full pipeline — inject `WatchHapticService` into `HapticScheduler`, fire threshold, verify `hapticService.play()` was called (not possible without WatchKit in unit tests — use `MockHapticService` for this integration path instead)

## Dev Notes

### What Already Exists — DO NOT Reinvent

| File | Status | Role in W3.2 |
|---|---|---|
| `Protocols/HapticServiceProtocol.swift` | ✅ COMPLETE | Protocol to implement — `play()`, `schedule()`, `cancelAll()`, `cancel()` |
| `Mocks/MockHapticService.swift` | ✅ COMPLETE | Records invocations — use in ALL unit tests (never `WatchHapticService` in tests) |
| `Domain/HapticScheduler.swift` | ✅ COMPLETE | Threshold logic + deduplication — DO NOT MODIFY |
| `Domain/HapticScheduler.Thresholds` | ✅ COMPLETE | `fiveMinute: 300`, `twoMinute: 120`, `timesUp: 0`, `overtimeInterval: 30`, `gongLeadTime: 60` |
| `Models/HapticAlert.swift` | ✅ COMPLETE | All 7 types defined — do not add new types |
| `BATbern-watch Watch AppTests/Domain/HapticSchedulerTests.swift` | ✅ COMPLETE | 11 tests covering full lifecycle, custom thresholds, break gong — DO NOT REWRITE |

### Why HapticSchedulerTests Already Cover the Logic

`HapticScheduler` calls `hapticService.play()` — and the tests assert on `hapticService.playedAlerts`. The scheduler uses `MockHapticService` in tests. The production `WatchHapticService` is simply a different implementation of the same protocol that calls `WKInterfaceDevice` instead of recording to an array. The scheduler logic is 100% covered. W3.2's new test surface is the `WatchHapticService` implementation itself (queue management) — not re-testing scheduler thresholds.

### WKHapticType Available Values (watchOS 11)

```swift
// From WatchKit — these are the available patterns:
.notification    // Standard notification buzz
.directionUp     // Upward scroll feel
.directionDown   // Downward scroll feel
.success         // Success confirmation
.failure         // Error/failure
.retry           // Retry prompt
.start           // Start of activity
.stop            // Stop of activity
.click           // Subtle click
```

**Mapping rationale:**
- `.notification` for time-based alerts (consistent tactile language for "time event")
- Multiple rapid plays (200ms/150ms gaps) create double/triple tap feel
- `.retry` for overrun pulse — conveys "keep going, it's still happening"
- `.success` / `.failure` for action confirm / connection lost — semantic match

**Distinctiveness test:** The 3 main alerts must feel different in the dark:
- 5-min: 1 buzz (single `.notification`)
- 2-min: 2 buzzes (double `.notification` with 200ms gap)
- Time's up: 3 buzzes (triple `.notification` with 150ms gap)
- Organizers learn: 1 = warning, 2 = urgent, 3 = done

### Extended Runtime Session — Critical Details

```swift
import WatchKit

@MainActor
final class WatchHapticService: NSObject, HapticServiceProtocol, WKExtendedRuntimeSessionDelegate {
    private var extendedSession: WKExtendedRuntimeSession?

    func startEventSession() {
        guard extendedSession?.state != .running else { return }
        let session = WKExtendedRuntimeSession()
        session.delegate = self
        session.start()
        extendedSession = session
    }

    func stopEventSession() {
        extendedSession?.invalidate()
        extendedSession = nil
    }

    // MARK: - WKExtendedRuntimeSessionDelegate

    func extendedRuntimeSessionDidStart(_ extendedRuntimeSession: WKExtendedRuntimeSession) {
        // Session is running — haptics will fire in background
    }

    func extendedRuntimeSession(
        _ extendedRuntimeSession: WKExtendedRuntimeSession,
        didInvalidateWith reason: WKExtendedRuntimeSessionInvalidationReason,
        error: (any Error)?
    ) {
        extendedSession = nil
        // Log reason — .error, .expired, .suppressedBySystem, .resignedFrontmost
    }

    func extendedRuntimeSessionWillExpire(_ extendedRuntimeSession: WKExtendedRuntimeSession) {
        // Session nearing expiry — restart to maintain coverage for rest of event
        extendedRuntimeSession.invalidate()
        startEventSession()
    }
}
```

**watchOS 11 Note:** `WKExtendedRuntimeSession` does NOT require special `Info.plist` entries in watchOS 8+ for workout/mindfulness types. For the general extended runtime type, no plist changes are needed. Verify this assumption against Xcode build — if linker errors appear, check `WKBackgroundModes`.

### AC7 — How Simultaneity Works Without WebSocket (W4.1)

For W3.2 (pre-WebSocket), all 4 watches achieve near-simultaneous haptics through:
1. W2.3 synced the same `scheduledEndTime` (UTC) to all watches
2. Each watch uses `clock.now` (wall-clock) to calculate remaining time
3. All watches reach the 300s threshold within ~1s of each other (wall-clock drift between devices is negligible)
4. **No network coordination needed** — the threshold moment is computed locally from a shared truth (session end time)

The 1-second NFR2 applies per device. Real simultaneous sync (server broadcasts haptic command) is an Epic 4 enhancement, not required here.

### Break Session Detection in Timer Loop

The `LiveCountdownViewModel` timer loop (from W3.1) calls `scheduler.evaluate(session:)` for talk sessions. For W3.2's AC5, it must also call `scheduler.evaluateBreakGong(breakSession:)` when the active session is a break:

```swift
// In LiveCountdownViewModel.refreshState():
if let session = activeSession {
    engine.recalculate()
    if session.isBreak {
        scheduler.evaluateBreakGong(breakSession: session)
    } else {
        scheduler.evaluate(session: session)
    }
}
```

`WatchSession.isBreak` is already defined in `WatchModels.swift`:
```swift
var isBreak: Bool {
    sessionType == .breakTime || sessionType == .lunch
}
```

### `schedule()` Implementation Approach

The `schedule()` method on `HapticServiceProtocol` stores haptics for future delivery. `WatchHapticService` can implement it simply:
```swift
private var scheduledQueue: [(alert: HapticAlert, at: Date)] = []

func schedule(_ alert: HapticAlert, at date: Date) {
    scheduledQueue.append((alert, date))
}
```
A timer in the Extended Runtime session fires any scheduled alerts whose `date <= clock.now`. Alternatively, for W3.2 scope, `schedule()` can be a no-op stub (the timer-based approach in `LiveCountdownViewModel` already handles threshold firing without pre-scheduling). Mark it `// TODO: W4 - used for server-triggered pre-scheduling` if not implementing now.

### Simulator Limitation

`WKInterfaceDevice.current().play()` does nothing in the Simulator. Haptic patterns **must be verified on a physical Apple Watch**. This is a known limitation — document in PR that haptic distinctiveness was verified on device.

### Architecture Placement

`WatchHapticService` belongs in `Data/` (it's an I/O service, not pure domain logic). `HapticScheduler` belongs in `Domain/` (it's pure logic with no hardware deps). The distinction:
- `HapticScheduler` knows **when** to fire (threshold logic) — no WatchKit
- `WatchHapticService` knows **how** to fire (hardware) — needs WatchKit

[Source: docs/watch-app/architecture.md#Structure-Patterns]

### Project Structure Notes

New files:
- `apps/BATbern-watch/BATbern-watch Watch App/Data/WatchHapticService.swift` ← new

Modified files:
- `apps/BATbern-watch/BATbern-watch Watch App/ViewModels/LiveCountdownViewModel.swift` — inject `WatchHapticService` in production; call `startEventSession()`/`stopEventSession()`

Potentially modified:
- `apps/BATbern-watch/BATbern-watch Watch App/App/BATbernWatchApp.swift` — if `WatchHapticService` is created at app level and passed down

### References

- [Source: docs/watch-app/epics.md#W3.2] — AC definitions, FR11-FR16
- [Source: docs/watch-app/epics.md#NonFunctional] — NFR2 (haptic within 1s), NFR9 (background haptics), NFR13 (zero missed haptic alerts per event)
- [Source: docs/watch-app/architecture.md#Implementation-Patterns] — haptic vocabulary (7 patterns)
- [Source: apps/BATbern-watch/CLAUDE.md#Haptics-7-Pattern-Vocabulary] — WKHapticType mapping reference
- [Source: apps/BATbern-watch/CLAUDE.md#Testing-Strategy] — P0 priority for HapticScheduler
- [Source: apps/BATbern-watch/BATbern-watch Watch AppTests/Domain/HapticSchedulerTests.swift] — existing tests (11 cases, covers all thresholds + lifecycle)

## Dev Agent Record

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

### File List
