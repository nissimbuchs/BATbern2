# D1 — WatchHapticService.schedule() Firing Logic

**Type:** Technical debt (Epic 3 retro)
**Owner:** Amelia (Dev)
**Blocks:** W4.1 (server-triggered synchronized haptics, FR16)
**Does not block:** W4.2, W4.3, W4.4

---

## Problem

`WatchHapticService.schedule(_ alert:, at:)` is a stub. It appends to `scheduledQueue` but
the scheduled alert never fires. W4.1 requires the server to push a `fireAt` timestamp via
WebSocket so all organizer watches fire the same haptic simultaneously (FR16). That only
works if `schedule()` actually fires.

**Current code** (`Data/WatchHapticService.swift:117–123`):
```swift
func schedule(_ alert: HapticAlert, at date: Date) {
    // TODO: Epic W4 — server-triggered pre-scheduling.
    scheduledQueue.append((alert, date))
}
```

---

## What to change

**One method** in `Data/WatchHapticService.swift`. Nothing else.

Replace the body of `schedule(_ alert:, at:)` with:

```swift
func schedule(_ alert: HapticAlert, at date: Date) {
    let delay = max(0, date.timeIntervalSince(Date()))
    let task = Task { @MainActor [weak self] in
        do {
            try await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
            guard let self else { return }
            // Guard: if cancel() removed this entry while we slept, suppress play.
            // cancelAll() / stopEventSession() suppress via Task cancellation (catch below).
            guard self.scheduledQueue.contains(where: { $0.alert == alert && $0.at == date }) else {
                return
            }
            self.scheduledQueue.removeAll { $0.alert == alert && $0.at == date }
            self.play(alert)
        } catch {
            // Cancelled by stopEventSession() or cancelAll() — alert suppressed intentionally.
        }
    }
    scheduledQueue.append((alert: alert, at: date))
    pendingHapticTasks.append(task)
}
```

Remove the old TODO comment — it is now implemented.

---

## Why this design

**Task-based, same pattern as the existing double/triple tap tasks.** No new data structures,
no new threading model. The existing `pendingHapticTasks` array is already cleaned up by
`stopEventSession()` and `cancelAll()` — scheduled tasks ride along for free.

**Two cancellation paths, both already correct after this change:**

| Caller | Mechanism | Result |
|--------|-----------|--------|
| `cancelAll()` | Cancels all `pendingHapticTasks` → `catch` runs | Suppressed |
| `stopEventSession()` | Same — cancels all `pendingHapticTasks` | Suppressed |
| `cancel(_ alert:)` | Removes from `scheduledQueue`; task wakes, guard fails | Suppressed |

`cancel(_ alert:)` does NOT cancel the `Task` directly (no alert→task mapping exists).
Instead, the guard check after the sleep detects the queue removal and returns without
calling `play()`. This is correct and intentional — do not refactor `cancel()`.

**Past dates fire immediately.** `max(0, ...)` clamps negative delays to zero, so a
`fireAt` timestamp that is already past fires on the next runloop turn. This is the correct
behaviour for a watch that reconnects mid-event and receives stale haptic commands.

**`@MainActor` threading invariant preserved.** The comment at line 28–35 of the current
file documents this invariant. The new Task uses `@MainActor` and `[weak self]`, matching
the existing `doubleTap`/`tripleTap` tasks exactly.

---

## Existing tests — all still pass unchanged

Verify this before writing new tests:

| Test | Why it still passes |
|------|-------------------|
| `cancelRemovesCorrectAlert` | Schedules 60s in the future; test completes before tasks fire |
| `cancelAllClearsQueue` | `cancelAll()` cancels tasks AND clears queue — both empty |
| `cancelRemovesAllOfType` | Same as above — 60s future; guard prevents play on wakeup |
| `scheduleSameAlertTwiceYieldsTwoEntries` | Tasks haven't fired; immediate count check ✓ |
| `schedulePreservesOrder` | Same — immediate check ✓ |
| `protocolConformance` | Smoke test; no timing dependency |

---

## New tests to add

Add to `WatchHapticServiceTests.swift` after the existing `4.6` block.

```swift
// MARK: - D1 Scheduled Firing

@Test("schedule() removes the entry from the queue after the delay elapses")
func scheduledAlertRemovesFromQueueAfterDelay() async {
    let service = WatchHapticService()

    service.schedule(.fiveMinuteWarning, at: Date(timeIntervalSinceNow: 0.05))
    #expect(service.scheduledQueue.count == 1)  // Queued immediately

    try? await Task.sleep(nanoseconds: 150_000_000)  // Wait 0.15s — past the 0.05s fire time

    #expect(service.scheduledQueue.isEmpty)  // Task fired, entry removed
}

@Test("schedule() with a past date fires immediately on next runloop turn")
func schedulePastDateFiresImmediately() async {
    let service = WatchHapticService()

    service.schedule(.timesUp, at: Date(timeIntervalSinceNow: -5))  // 5s ago

    try? await Task.sleep(nanoseconds: 100_000_000)  // Small yield

    #expect(service.scheduledQueue.isEmpty)
}

@Test("cancelAll() before scheduled time prevents the entry from re-appearing after delay")
func cancelAllBeforeFireTimePreventsPlay() async {
    let service = WatchHapticService()

    service.schedule(.fiveMinuteWarning, at: Date(timeIntervalSinceNow: 0.1))
    service.cancelAll()

    #expect(service.scheduledQueue.isEmpty)  // Cleared immediately

    try? await Task.sleep(nanoseconds: 200_000_000)  // Wait past fire time

    #expect(service.scheduledQueue.isEmpty)  // Did not re-add on wakeup
}

@Test("cancel() before fire time suppresses that alert via the queue guard")
func cancelBeforeFireTimeSuppressesViaGuard() async {
    let service = WatchHapticService()
    let fireAt = Date(timeIntervalSinceNow: 0.1)

    service.schedule(.fiveMinuteWarning, at: fireAt)
    service.schedule(.twoMinuteWarning, at: fireAt)

    service.cancel(.fiveMinuteWarning)

    #expect(service.scheduledQueue.count == 1)
    #expect(service.scheduledQueue[0].alert == .twoMinuteWarning)

    try? await Task.sleep(nanoseconds: 200_000_000)  // Wait past fire time

    // twoMinuteWarning fired and was removed; fiveMinuteWarning guard prevented play
    #expect(service.scheduledQueue.isEmpty)
}

@Test("stopEventSession() before fire time cancels the scheduled task")
func stopEventSessionCancelsScheduledTask() async {
    let service = WatchHapticService()

    service.schedule(.gongReminder, at: Date(timeIntervalSinceNow: 0.1))
    service.stopEventSession()

    #expect(service.scheduledQueue.isEmpty)  // cancelAll() inside stop clears queue

    try? await Task.sleep(nanoseconds: 200_000_000)

    #expect(service.scheduledQueue.isEmpty)  // Task was cancelled — nothing re-added
}
```

**Note on play() verification:** `WKInterfaceDevice.play()` is a no-op in Simulator (same
constraint as all existing tests). The tests assert on `scheduledQueue` state only.
`play()` invocation is verified indirectly — the queue entry is only removed immediately
before `play()` is called, so an empty queue proves the play path was reached.

---

## Definition of done

- [ ] `schedule()` body replaced as above — TODO comment removed
- [ ] All 5 existing `WatchHapticServiceTests` pass unchanged
- [ ] All 5 new tests pass
- [ ] Manual verification on physical Apple Watch: schedule an alert 3 seconds out, confirm
      the haptic fires at the right moment (triple-tap for `.gongReminder`, single for
      `.fiveMinuteWarning`) — Simulator cannot verify haptic delivery

---

## Files changed

| File | Change |
|------|--------|
| `Data/WatchHapticService.swift` | Replace `schedule()` body (~5 lines → ~15 lines) |
| `Tests/Data/WatchHapticServiceTests.swift` | Add 5 new test cases in new `D1` section |

No other files. No new protocols. No migration. No interface changes.
