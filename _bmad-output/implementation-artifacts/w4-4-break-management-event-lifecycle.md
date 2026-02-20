# Story W4.4: Break Management & Event Lifecycle

Status: done

---

## Design Direction (A2 — Gate Requirement)

**Epic 4 core principle:** One state, one path. All server-driven state flows through `EventDataController` → `EventStateManager` → `LiveCountdownViewModel`. No parallel state object is permitted.

**W4.4 visible changes:**
- `LiveCountdownView` (O3) auto-transitions to **O5 `BreakGongView`** when `viewModel.activeSession?.isBreak == true`. The break view is a `.fullScreenCover` presented from `LiveCountdownView` — same pattern as O6 (W4.2). `LiveCountdownView` is the anchor for all live event sub-states.
- **O5 `BreakGongView`** shows: break icon (via `BreakCardLayout`), break countdown (same `formattedTime` as O3), next speaker preview (`NextSessionPeekView` in `.compact` mode from W4.2), and a **visual gong reminder overlay** when `viewModel.gongOverlayVisible == true`.
- **Gong visual overlay** — when `HapticScheduler.evaluateBreakGong()` fires the gong haptic (60s before break ends), `LiveCountdownViewModel.gongOverlayVisible` becomes `true` for 5 seconds, showing a banner on O5. No new haptic logic — the haptic already fires automatically.
- **Break end auto-transition O5 → O3** — when wall clock passes break end time, `LiveCountdownViewModel.activeSession` naturally becomes the next talk session → `isBreak == false` → `.onChange` in `LiveCountdownView` fires → `showBreak = false` → O5 dismisses automatically → O3 takes over with new session countdown.
- **Event COMPLETED** — when the final session is ended via "Done", backend detects all sessions complete, transitions event state to COMPLETED, broadcasts `trigger: "EVENT_COMPLETED"`. `applyServerState()` updates event state → no active session remains → `EventStateManager.isLive` becomes `false` → `OrganizerZoneView` routes to an "Event Complete" screen.

**Implementation style:** Two orthogonal additions — (1) break visual state (O5) surfacing existing timer + haptic logic, and (2) event completion detection (backend) + routing (client). No new ViewModels, no new state managers, no new haptic logic.

---

## Pre-Implementation Review (A1 — Gate Requirement)

**Before writing a single line of code, Dev must confirm the following from `docs/watch-app/epic-4-reuse-map.md`:**

| Check | Mandate | Confirmed |
|---|---|---|
| Area 2 | `breakCardLayout` extracted into `BreakCardLayout` BEFORE O5 is built — this is a required subtask, not optional | [x] |
| Area 2 | O5 `BreakGongView` displays `LiveCountdownViewModel.formattedTime` — no second timer started | [x] |
| Area 2 | O5 uses `LiveCountdownViewModel.nextSession` for next speaker preview — no re-query of sessions | [x] |
| Area 2 | Gong haptic NOT reimplemented — `HapticScheduler.evaluateBreakGong()` already fires on every tick; W4.4 adds only the visual overlay | [x] |
| Area 2 | `WatchHapticService.schedule()` (D1) is NOT required for W4.4 — break gong fires via `evaluateBreakGong()` on the tick loop | [x] |
| Area 4 | `EventStateManager.isLive` is the only gate for routing away from `LiveCountdownView` on event completion — no new `isCompleted` flag added | [x] |
| Area 4 | Event completion flows through `EventDataController.applyServerState()` → `EventStateManager` recalculates automatically | [x] |
| W4.1 | `WebSocketService.sendAction(_:)` confirmed available (final "Done" tap on last session uses existing W4.2 button) | [x] |
| W4.2 | `LiveCountdownViewModel.canMarkDone` confirmed — final session "Done" tap uses same property | [x] |
| W4.2 | `WebSocketService.sessionEndedEvent` confirmed — W4.4 event completion happens after W4.2's `endSession` path completes | [x] |
| W4.2 | `NextSessionPeekView` (`.compact` mode) confirmed available from W4.2 Task 0 — O5 reuses it for next speaker preview | [x] |

---

## Story

As an organizer,
I want to see break countdowns with gong timers and have the event flow managed end-to-end,
so that the entire evening runs smoothly.

## Acceptance Criteria

1. **AC1 — Auto-Transition to O5 on Break Start**: Given a session ends (via "Done" tap, W4.2 path) and the next item in the schedule is a break/networking/lunch session, When `LiveCountdownViewModel.activeSession` becomes the break session (`isBreak == true`), Then `LiveCountdownView` automatically presents O5 `BreakGongView` as a `.fullScreenCover`. The break view displays: the break icon (type-specific SF Symbol), "BREAK" label, break countdown using `viewModel.formattedTime` (same timer as O3), and next speaker preview via `NextSessionPeekView(session: next, style: .compact)` if `viewModel.nextSession != nil`.

2. **AC2 — Gong Visual Reminder**: Given a break is active and 60 seconds remain until the break ends, When `LiveCountdownViewModel.gongOverlayVisible` becomes `true` (synchronized with `HapticScheduler.evaluateBreakGong()` firing the gong haptic), Then a visual gong reminder overlay appears on O5 — a banner reading "Break ending soon" with orange background — and auto-dismisses after 5 seconds. The haptic pattern itself (`.gongReminder` triple-tap) fires automatically via `HapticScheduler.evaluateBreakGong()` with no additional haptic code in W4.4.

3. **AC3 — Auto-Transition Back to O3 When Break Ends**: Given O5 is displayed during a break, When the break's `scheduledEndTime` is reached (wall-clock based) and `LiveCountdownViewModel.activeSession` changes to the next talk session (`isBreak == false`), Then O5 automatically dismisses and O3 (`LiveCountdownView`) resumes showing the new session's countdown. No organizer action required — the transition is fully automatic.

4. **AC4 — Event COMPLETED Transition**: Given the final session in the event is active (no sessions remain after it), When the organizer taps "Done" and the server processes `endSession()`, Then:
   - The backend detects all sessions are now completed
   - The event's workflow state transitions to COMPLETED
   - A final `STATE_UPDATE` broadcast with `trigger: "EVENT_COMPLETED"` is sent to all watches
   - `EventDataController.applyServerState()` processes the broadcast, leaving no active session
   - `EventStateManager.isLive` becomes `false`
   - `OrganizerZoneView` routes to an "Event Complete" screen across all connected watches
   - This transition propagates to ALL organizer watches within 3 seconds (NFR3)

---

## Tasks / Subtasks

### Prerequisite: Extract `BreakCardLayout` from `SessionCardView` (Required Before O5)

- [x] **Task 0: Extract `SessionCardView.breakCardLayout` into shared `BreakCardLayout`** (Blocks: Task 2)
  - [x] 0.1 Create `apps/BATbern-watch/BATbern-watch Watch App/Views/Shared/BreakCardLayout.swift`
  - [x] 0.2 Move the body of `breakCardLayout` (lines 138–175 of `SessionCardView.swift`) into `BreakCardLayout` as a SwiftUI `View`:
    - Inputs: `session: CachedSession` (or `sessionType: String`, `title: String` — choose minimal interface that works for both public zone and O5)
    - Layout: SF Symbol icon (`cup.and.saucer` / `person.2`), session title, optional time slot + status badge
  - [x] 0.3 Move `breakIcon` computed property (lines 70–82 of `SessionCardView.swift`) as a `static func breakIcon(for sessionType: String) -> String` helper on `BreakCardLayout` or as an extension on `CachedSession`
  - [x] 0.4 Verify `isBreakSession` property (lines 49–52 of `SessionCardView.swift`) — if it's a `CachedSession` extension, it's already reusable. If it's on `SessionCardView` only, move it to a `CachedSession` extension: `var isBreak: Bool { sessionType == "break" || sessionType == "lunch" || sessionType == "networking" }`. O5's `.onChange` trigger uses `viewModel.activeSession?.isBreak`.
  - [x] 0.5 In `SessionCardView.swift`, replace the inline `breakCardLayout` section with `BreakCardLayout(session: session)` — all existing public zone break card behavior preserved
  - [x] 0.6 Unit tests in `BreakCardLayoutTests.swift` (Swift Testing, `@Test`):
    - `BreakCardLayout` renders `cup.and.saucer.fill` icon for `.break` and `.lunch` session types
    - `BreakCardLayout` renders `person.2.fill` icon for `.networking` session type
    - `BreakCardLayout` renders correct `title` text
    - `CachedSession.isBreak` extension returns `true` for break/lunch/networking, `false` for presentation/keynote

---

### watchOS — Gong Overlay Signal in LiveCountdownViewModel

- [x] **Task 1: `LiveCountdownViewModel.gongOverlayVisible` + deduplication** (AC: 2)
  - [x] 1.1 Add `private(set) var gongOverlayVisible: Bool = false` to `LiveCountdownViewModel.swift`
  - [x] 1.2 Add `private var gongFiredInCurrentBreak: Bool = false` for per-break deduplication
  - [x] 1.3 In `refreshState()`, after `evaluateBreakGong()` is called (which fires the haptic), add gong overlay detection:
    ```swift
    if let active = activeSession, active.isBreak {
        let breakRemaining = max(0, active.scheduledEndTime.flatMap {
            Int($0.timeIntervalSince(clock.now))
        } ?? 0)
        if breakRemaining <= 60 && breakRemaining > 0 && !gongFiredInCurrentBreak {
            gongFiredInCurrentBreak = true
            gongOverlayVisible = true
            Task { @MainActor in
                try? await Task.sleep(for: .seconds(5))
                gongOverlayVisible = false
            }
        }
    } else {
        // Leaving a break: reset deduplication flag for next break
        if gongFiredInCurrentBreak {
            gongFiredInCurrentBreak = false
        }
    }
    ```
  - [x] 1.4 Edge case: if app is suspended during the 5-second window, `gongOverlayVisible` may remain `true` on resume. On `refreshState()`, if `activeSession?.isBreak == false`, force `gongOverlayVisible = false` immediately.
  - [x] 1.5 Unit tests in `LiveCountdownViewModelTests.swift`:
    - `gongOverlayVisible` becomes `true` when break `scheduledEndTime` is 55s in the future (within 60s threshold)
    - `gongOverlayVisible` does NOT become `true` again in the same break (deduplication: `gongFiredInCurrentBreak` guards)
    - `gongFiredInCurrentBreak` resets to `false` when `activeSession` changes from break to talk (new break will re-arm the overlay)
    - `gongOverlayVisible` becomes `false` immediately when active session is not a break (guard in `refreshState()`)

---

### watchOS — O5 BreakGongView

- [x] **Task 2: Create O5 `BreakGongView`** (AC: 1, 2, 3)
  - [x] 2.1 Create `apps/BATbern-watch/BATbern-watch Watch App/Views/Organizer/BreakGongView.swift`
  - [x] 2.2 Input: `viewModel: LiveCountdownViewModel` — the same `@Observable` instance held by `LiveCountdownView` (passed in; no new ViewModel, no new data fetching)
  - [x] 2.3 Layout (full-screen, vertical):
    ```
    ┌─── gong overlay (top, conditional) ────────────────┐
    │ [🔔 Break ending soon]   ← orange banner, 5s visible  │
    └────────────────────────────────────────────────────┘

    BreakCardLayout(session: viewModel.activeSession!)
      ← reuses extracted shared component (icon + title)

    Countdown: viewModel.formattedTime
      ← same SF Mono ~28pt, color from urgencyLevel
      ← BATbern Blue → yellow → orange → red (same color logic as O3)

    ─── if viewModel.nextSession != nil ──────────────────
    "UP NEXT"   ← SF Pro 10pt, secondary
    NextSessionPeekView(session: next, style: .compact)
      ← W4.2 shared component, no portrait redundancy
    ```
  - [x] 2.4 Countdown colors: reuse same urgency-level color mapping as O3 (already in `LiveCountdownView` or `LiveCountdownViewModel.urgencyLevel`). At gong threshold (≤60s), urgency is approaching `.critical` or `.overtime` — color will naturally shift to orange/red.
  - [x] 2.5 Gong overlay ("Break ending soon" banner):
    - Presented as a `ZStack` overlay at the top of `BreakGongView`, shown when `viewModel.gongOverlayVisible == true`
    - Layout: HStack with `Image(systemName: "bell.fill")` + `Text("Break ending soon")` (localized key: `"break.gong.overlay.title"`)
    - Style: SF Pro Rounded 12pt semibold, white text, orange background (`.orange.opacity(0.90)`)
    - Animation: `.transition(.asymmetric(insertion: .move(edge: .top).combined(with: .opacity), removal: .opacity))`
    - Note: 5-second dismiss is handled by `LiveCountdownViewModel.gongOverlayVisible` (Task 1) — no timer in the view
  - [x] 2.6 `BreakGongView` does NOT contain any haptic calls (haptic fires via `HapticScheduler.evaluateBreakGong()` in the ViewModel's tick loop — never in the view)
  - [x] 2.7 Unit tests in `BreakGongViewTests.swift` (Swift Testing, `@Test`):
    - Break icon (`cup.and.saucer.fill`) displayed for a break-type session
    - `NextSessionPeekView` rendered when `viewModel.nextSession != nil`
    - `NextSessionPeekView` NOT rendered when `viewModel.nextSession == nil` (last session of the night is preceded by the final break)
    - Gong overlay visible when `viewModel.gongOverlayVisible == true`
    - Gong overlay NOT visible when `viewModel.gongOverlayVisible == false`
    - Countdown text matches `viewModel.formattedTime` value

---

### watchOS — O3 ↔ O5 Auto-Transition in `LiveCountdownView`

- [x] **Task 3: Wire O5 auto-transition in `LiveCountdownView`** (AC: 1, 3)
  - [x] 3.1 In `LiveCountdownView.swift`, add `@State private var showBreak: Bool = false`
  - [x] 3.2 Add `.onChange(of: viewModel.activeSession?.isBreak)` to `LiveCountdownView.body`:
    ```swift
    .onChange(of: viewModel.activeSession?.isBreak) { _, isBreak in
        withAnimation {
            showBreak = isBreak == true
        }
    }
    ```
  - [x] 3.3 Present O5 as `.fullScreenCover(isPresented: $showBreak)`:
    ```swift
    .fullScreenCover(isPresented: $showBreak) {
        BreakGongView(viewModel: viewModel)
    }
    ```
  - [x] 3.4 **O5 → O3 auto-dismiss**: when the break's `scheduledEndTime` is reached, `LiveCountdownViewModel.refreshState()` finds a new `activeSession` (the next talk) with `isBreak == false` → `.onChange` fires → `showBreak = false` → `.fullScreenCover` dismisses → O3 shows the new session's countdown. No organizer interaction required.
  - [x] 3.5 **Mutual exclusion with W4.2/W4.3**: `showBreak` operates independently of `showTransition` (W4.2) and `showCascadePrompt` (W4.3). In practice these are mutually exclusive by event design (cascade/transition happens on talk sessions; break happens during breaks). No special coordination needed — watchOS presents only one `.fullScreenCover` at a time.
  - [x] 3.6 **Initial state on connection**: if the organizer connects mid-break (joins event during a break), `viewModel.activeSession?.isBreak == true` immediately → `showBreak` is set to `true` in `.onAppear` guard. Add `onAppear { showBreak = viewModel.activeSession?.isBreak == true }` to handle this case.
  - [x] 3.7 Unit tests in `LiveCountdownViewTests.swift`:
    - `showBreak` becomes `true` when `viewModel.activeSession.isBreak == true`
    - `showBreak` becomes `false` when `viewModel.activeSession.isBreak == false` (break ends, next talk starts)
    - `showBreak == true` after `onAppear` when initial session is a break (mid-event join case)
    - `showBreak` and `showCascadePrompt` both `false` simultaneously in normal talk session (no interference)

---

### watchOS — Event Completed Routing in `OrganizerZoneView`

- [x] **Task 4: Add "Event Complete" screen to `OrganizerZoneView`** (AC: 4)
  - [x] 4.1 When `EventStateManager.isLive == false` and `EventStateManager.hasActiveEvent == true` and the event date is today (event just completed), `OrganizerZoneView` should show a distinct "Event Complete" state rather than the generic `EventPreviewView`.
  - [x] 4.2 Add detection in `OrganizerZoneView`:
    ```swift
    var organizerEntryView: some View {
        if !authManager.isPaired {
            PairingView()                            // O1
        } else if eventState.isPreEvent {
            SpeakerArrivalView()                     // O2 (<1h before event)
        } else if eventState.isLive {
            LiveCountdownView()                      // O3 (+ O5, O6 as fullScreenCover)
        } else if eventState.isEventCompletedToday { // NEW — event just finished
            EventCompletedView()                     // W4.4 — "Great event!" screen
        } else {
            EventPreviewView()                       // No active event / future event
        }
    }
    ```
  - [x] 4.3 Add computed property to `EventStateManager`:
    ```swift
    var isEventCompletedToday: Bool {
        guard let event = controller.currentEvent else { return false }
        guard !isLive && !isPreEvent else { return false }
        return Calendar.current.isDateInToday(event.eventDate)
    }
    ```
    This avoids a new "event completed" flag while using the existing `currentEvent` data — satisfying the reuse-map "no new flag" mandate.
  - [x] 4.4 Create `apps/BATbern-watch/BATbern-watch Watch App/Views/Organizer/EventCompletedView.swift`:
    - Display: BATbern logo/symbol, "Event Complete" title, event title, "Thanks for a great evening!" caption
    - Localized key: `"organizer.event.completed.title"` / `"organizer.event.completed.caption"`
    - No interactive elements (passive completion screen)
    - Simple, clean layout consistent with public zone hero screen
  - [x] 4.5 Unit tests in `EventStateManagerTests.swift`:
    - `isEventCompletedToday == true` when `isLive == false`, `hasActiveEvent == true`, `eventDate == today`
    - `isEventCompletedToday == false` when `isLive == true`
    - `isEventCompletedToday == false` when event date is not today (past archive or future event)
  - [x] 4.6 Unit tests in `EventCompletedViewTests.swift`:
    - View renders event title
    - "Event Complete" text displayed

---

### Backend — EVENT_COMPLETED Detection in `WatchSessionService`

- [x] **Task 5: Detect all-sessions-complete and transition event to COMPLETED** (AC: 4)
  - [x] 5.1 In `WatchSessionService.endSession()` (from W4.2 Task 7), after `sessionRepository.save(session)`, add all-complete detection:
    ```java
    // Check if all sessions in this event are now complete
    List<Session> allSessions = sessionRepository.findAllByEventCode(eventCode);
    boolean allComplete = allSessions.stream()
        .allMatch(s -> s.getCompletedByUsername() != null);

    if (allComplete) {
        // Transition event to COMPLETED via existing lifecycle service
        eventLifecycleService.completeEvent(eventCode, completedByUsername);
        watchPresenceService.buildAndBroadcastState(
            eventCode, "EVENT_COMPLETED", null, completedByUsername
        );
    } else {
        watchPresenceService.buildAndBroadcastState(
            eventCode, "SESSION_ENDED", sessionSlug, completedByUsername
        );
    }
    ```
  - [x] 5.2 **`eventLifecycleService.completeEvent()`**: Check if `EventLifecycleService` or `EventWorkflowService` already exists in the platform (the main BATbern platform has an event state machine with `EVENT_LIVE → EVENT_COMPLETED` transition). If a reusable transition method exists, call it. If not, directly update `event.setWorkflowState(EventWorkflowState.EVENT_COMPLETED)` and `eventRepository.save(event)`. Align with the existing 9-state machine patterns.
  - [x] 5.3 ~~Similarly update `WatchSessionService.cascadeSession()`~~ **N/A**: `cascadeSession()` was removed from W4.3 scope by Sprint Change Proposal 2026-02-19. The cascade path no longer exists; all-complete detection is handled exclusively in `endSession()`.
  - [x] 5.4 Idempotency: if `eventLifecycleService.completeEvent()` is called a second time (two organizers racing), it should be a no-op (event already COMPLETED). Verify the existing lifecycle service handles this.

- [x] **Task 6: `SessionRepository` — `findAllByEventCode`** (AC: 4)
  - [x] 6.1 Verify if `findAllByEventCode(String eventCode)` or an equivalent method already exists in `SessionRepository.java` (may have been added by W2.x or earlier epics for event validation)
  - [x] 6.2 If not present, add:
    ```java
    List<Session> findAllByEventCode(String eventCode);
    ```
  - [x] 6.3 Integration test: verify returns all sessions (talk, break, networking) for a given event code

- [x] **Task 7: `WatchStateUpdateMessage` — EVENT_COMPLETED trigger** (AC: 4)
  - [x] 7.1 `trigger` field already added in W4.2 Task 8.1 — verify `"EVENT_COMPLETED"` is a valid trigger string. No code change needed if the field is a free-form `String` (it is, per W4.2 implementation).
  - [x] 7.2 Verify `WatchPresenceService.buildAndBroadcastState(eventCode, "EVENT_COMPLETED", null, completedByUsername)` works when `sessionSlug == null` — the trigger is event-level, not session-level. Guard any `sessionSlug`-referencing code in `buildStateUpdate()` for nullity.
  - [x] 7.3 Include event-level status in the broadcast. Add `eventCompleted: Boolean` field to `WatchStateUpdateMessage.java`:
    ```java
    private boolean eventCompleted;  // true when trigger == "EVENT_COMPLETED"
    ```
    Set to `true` in `buildAndBroadcastState()` when trigger is `"EVENT_COMPLETED"`. This allows the Watch client to reliably detect completion without parsing the trigger string.

- [x] **Task 8: `EventDataController` — handle EVENT_COMPLETED broadcast** (AC: 4)
  - [x] 8.1 In `EventDataController.applyServerState(_:)`, handle `eventCompleted == true` in the incoming `WatchStateUpdate`:
    ```swift
    if update.eventCompleted {
        // Mark the cached event as completed so EventStateManager.isLive → false
        currentEvent?.workflowState = "EVENT_COMPLETED"
    }
    ```
  - [x] 8.2 Verify `EventStateManager.isLive` correctly returns `false` when `currentEvent?.workflowState == "EVENT_COMPLETED"`. If `isLive` is computed solely from active session presence (no active sessions after completion), then Step 8.1 may be optional but is good practice for explicitness and future-proofing.
  - [x] 8.3 Unit tests in `EventDataControllerApplyServerStateTests.swift` (existing file from W4.1):
    - `applyServerState` with `eventCompleted: true` → `currentEvent.workflowState == "EVENT_COMPLETED"`
    - `applyServerState` with `eventCompleted: false` → `currentEvent.workflowState` unchanged

---

### Backend — Integration Tests

- [x] **Task 9: `WatchEventCompletionIntegrationTest`** (AC: 4)
  - [x] 9.1 Create `services/event-management-service/src/test/java/ch/batbern/events/watch/WatchEventCompletionIntegrationTest.java` extending `AbstractIntegrationTest`
  - [x] 9.2 Test: `endSession()` on the **last** session of an event → all sessions `allMatch(completed)` → `eventLifecycleService.completeEvent()` called → broadcast trigger is `"EVENT_COMPLETED"` → `WatchStateUpdateMessage.eventCompleted == true`
  - [x] 9.3 Test: `endSession()` on a **non-last** session → regular `SESSION_ENDED` broadcast → `WatchStateUpdateMessage.eventCompleted == false`
  - [x] 9.4 ~~Test: `cascadeSession()` on the last session~~  **N/A**: `cascadeSession()` does not exist (removed by Sprint Change Proposal 2026-02-19 from W4.3 scope). This test was vacuous; `endSession()` is the only completion path.
  - [x] 9.5 Test: `eventLifecycleService.completeEvent()` called twice (second organizer races) → idempotent, no exception thrown, event remains in COMPLETED state

---

## Dev Notes

### Architecture Guardrails (reuse-map compliance)

**O5 IS NOT A SEPARATE LIVE EVENT FLOW:**
```swift
// ✅ Correct — O5 presented from LiveCountdownView, shares the same ViewModel
.fullScreenCover(isPresented: $showBreak) {
    BreakGongView(viewModel: viewModel)  // same @Observable instance
}

// ❌ Wrong — O5 as a new NavigationStack destination with its own ViewModel
NavigationLink(destination: BreakGongView()) { ... }  // FORBIDDEN — creates new data graph
```

**BREAK COUNTDOWN = SAME TIMER, DIFFERENT PRESENTATION:**
```swift
// ✅ Correct — O5 reads formattedTime from the existing ViewModel
Text(viewModel.formattedTime)  // Already MM:SS, already color-correct

// ❌ Wrong — starting a second countdown timer for the break
@State private var breakSecondsRemaining = 0
Timer.scheduledTimer(...)  // FORBIDDEN — second timer creates drift and duplication
```

**GONG HAPTIC FIRES AUTOMATICALLY — VISUAL ONLY:**
```swift
// ✅ Correct — haptic fires in HapticScheduler.evaluateBreakGong() on every tick
// W4.4 only tracks when it fires to show the visual:
if breakRemaining <= 60 && !gongFiredInCurrentBreak {
    gongFiredInCurrentBreak = true
    gongOverlayVisible = true
    // No hapticService.play() here — that's already done in HapticScheduler
}

// ❌ Wrong — calling haptic again in the view or ViewModel for W4.4
hapticService.play(.gongReminder)  // FORBIDDEN — would fire twice
```

**NO OrganizerStateManager:**
```swift
// ✅ Correct — break detection flows through LiveCountdownViewModel.activeSession
viewModel.activeSession?.isBreak  // Derived from EventDataController.currentEvent sessions

// ❌ Wrong — new break-tracking state object
class BreakStateManager {
    var isBreakActive: Bool  // FORBIDDEN — duplicates activeSession.isBreak
}
```

**EVENT_COMPLETED ROUTING IS AUTOMATIC:**
```swift
// ✅ Correct — EventStateManager.isEventCompletedToday uses existing currentEvent data
var isEventCompletedToday: Bool {
    guard let event = controller.currentEvent else { return false }
    return !isLive && Calendar.current.isDateInToday(event.eventDate)
}

// ❌ Wrong — new completion flag
var hasCompletedEvent: Bool = false  // FORBIDDEN — redundant with isLive + date check
```

### Break Session Detection — `CachedSession.isBreak`

The `isBreakSession` property in `SessionCardView` (lines 49–52) is a view-layer concern. For the ViewModel to check `activeSession?.isBreak`, we need this as a model-layer property. Confirm existence of (or add as extension on `CachedSession`):
```swift
extension CachedSession {
    var isBreak: Bool {
        sessionType == "break" || sessionType == "lunch" || sessionType == "networking"
    }
}
```
This extension is non-breaking — `SessionCardView.isBreakSession` can delegate to this extension.

### O5 → O3 Transition Timing

The break ends based on wall-clock calculation, not a server event. When `CachedSession.scheduledEndTime` for the break passes `clock.now`, `LiveCountdownViewModel.refreshState()` finds the break session no longer current and advances `activeSession` to the next scheduled talk.

The server does NOT explicitly broadcast "break ended" — it broadcasts `SESSION_STARTED` when an organizer taps "Done" on the preceding session, which starts the break. The break runs on wall-clock time until `scheduledEndTime`, then the next talk naturally becomes `activeSession`.

This means **breaks are passive** — no organizer action ends them, they end automatically. O5 dismisses without any tap.

### Event Completed Detection — Backend Strategy

After `endSession()` for the last session, `findAllByEventCode()` returns all sessions. The `allMatch(s -> s.getCompletedByUsername() != null)` check is the completion guard. This includes break sessions — **verify whether breaks are included in this check**.

Decision: **Exclude break/networking/lunch sessions from the completion check.** Only "completeable" sessions (keynote, presentation, workshop, panel_discussion) require a "Done" tap. Break sessions are time-bounded and self-completing.

```java
// ✅ Correct — only check completeable sessions
boolean allComplete = allSessions.stream()
    .filter(s -> !isBreakSession(s))
    .allMatch(s -> s.getCompletedByUsername() != null);

private boolean isBreakSession(Session s) {
    return Set.of("break", "lunch", "networking").contains(s.getSessionType());
}
```
This matches the UX design principle that breaks auto-complete without organizer intervention.

### Break End: Next Session Already in `LiveCountdownViewModel.nextSession`

`findNextSession()` (LiveCountdownViewModel, lines 182–188) already excludes breaks: `!$0.isBreak`. So `viewModel.nextSession` in O5 shows the NEXT TALK after the break — even if there are two breaks in a row, it skips to the first non-break session. This is the correct UX behavior.

### EVENT_COMPLETED Broadcast JSON Shape

After the final session ends, server broadcasts:
```json
{
  "type": "STATE_UPDATE",
  "trigger": "EVENT_COMPLETED",
  "sessionSlug": null,
  "initiatedBy": "marco.organizer",
  "eventCode": "BATbern56",
  "eventCompleted": true,
  "sessions": [
    {
      "sessionSlug": "closing-remarks",
      "status": "COMPLETED",
      "actualEndTime": "2026-02-14T21:30:00Z",
      "overrunMinutes": 0,
      "completedBy": "marco.organizer"
    }
  ],
  "connectedOrganizers": [
    { "username": "marco.organizer", "firstName": "Marco", "connected": true }
  ],
  "serverTimestamp": "2026-02-14T21:30:01Z"
}
```

Watch receives → `applyServerState()`:
- Updates session "closing-remarks" with `completedByUsername`, `actualEndTime`
- Detects `eventCompleted: true` → sets `currentEvent.workflowState = "EVENT_COMPLETED"`
- `EventStateManager.isLive` → `false` (no active session, event completed)
- `OrganizerZoneView` re-evaluates → `isEventCompletedToday == true` → `EventCompletedView` shown on all watches

### Dependency on W4.2 and W4.3

W4.4 has hard dependencies on W4.2 and W4.3:
- `LiveCountdownViewModel.canMarkDone` (W4.2 Task 1) — final session "Done" uses same button
- `WebSocketService.sendAction(_:)` (W4.1 Task 3.6) — final session end action
- `WebSocketService.sessionEndedEvent` (W4.2 Task 5.3) — event completion routing uses same chain
- `WatchSessionService.endSession()` (W4.2 Task 7) — W4.4 extends it with all-complete detection
- `WatchSessionService.cascadeSession()` (W4.3 Task 7) — W4.4 also extends this for cascade-triggered completion
- `NextSessionPeekView` (W4.2 Task 0) — O5 uses `.compact` mode for next speaker preview
- `WatchStateUpdateMessage.trigger` (W4.2 Task 8.1) — W4.4 adds `"EVENT_COMPLETED"` trigger value

Confirm all W4.2 and W4.3 prerequisites are implemented before starting W4.4.

### Project Structure Notes

```
New files (watchOS):
apps/BATbern-watch/BATbern-watch Watch App/Views/Shared/BreakCardLayout.swift
apps/BATbern-watch/BATbern-watch Watch App/Views/Organizer/BreakGongView.swift
apps/BATbern-watch/BATbern-watch Watch App/Views/Organizer/EventCompletedView.swift

Modified files (watchOS):
apps/BATbern-watch/BATbern-watch Watch App/Views/Public/SessionCardView.swift
  (replace inline breakCardLayout with BreakCardLayout component)
apps/BATbern-watch/BATbern-watch Watch App/Models/CachedSession.swift
  (add/verify isBreak extension)
apps/BATbern-watch/BATbern-watch Watch App/ViewModels/LiveCountdownViewModel.swift
  (add gongOverlayVisible, gongFiredInCurrentBreak)
apps/BATbern-watch/BATbern-watch Watch App/Views/Organizer/LiveCountdownView.swift
  (add showBreak @State, .onChange, .fullScreenCover for O5, onAppear guard)
apps/BATbern-watch/BATbern-watch Watch App/Domain/EventStateManager.swift
  (add isEventCompletedToday computed property)
apps/BATbern-watch/BATbern-watch Watch App/Views/OrganizerZoneView.swift
  (add EventCompletedView routing case)
apps/BATbern-watch/BATbern-watch Watch App/Data/EventDataController.swift
  (handle eventCompleted: true in applyServerState)
apps/BATbern-watch/BATbern-watch Watch App/Protocols/WebSocketClientProtocol.swift
  (add eventCompleted: Bool field to WatchStateUpdate struct)

New backend files:
services/event-management-service/src/test/java/ch/batbern/events/watch/WatchEventCompletionIntegrationTest.java

Modified backend files:
services/event-management-service/src/main/java/ch/batbern/events/watch/WatchSessionService.java
  (add all-complete detection + eventLifecycleService.completeEvent() in endSession + cascadeSession)
services/event-management-service/src/main/java/ch/batbern/events/watch/dto/WatchStateUpdateMessage.java
  (add eventCompleted: boolean field)
services/event-management-service/src/main/java/ch/batbern/events/watch/WatchPresenceService.java
  (handle null sessionSlug in buildAndBroadcastState for EVENT_COMPLETED; set eventCompleted field)
services/event-management-service/src/main/java/ch/batbern/events/domain/SessionRepository.java
  (verify/add findAllByEventCode)

New test files:
apps/BATbern-watch/BATbern-watch Watch AppTests/Views/BreakCardLayoutTests.swift
apps/BATbern-watch/BATbern-watch Watch AppTests/Views/BreakGongViewTests.swift
apps/BATbern-watch/BATbern-watch Watch AppTests/Views/EventCompletedViewTests.swift
services/event-management-service/src/test/java/ch/batbern/events/watch/WatchEventCompletionIntegrationTest.java
```

### Testing Standards

- watchOS: Swift Testing (`@Test`, `#expect`) — pure unit tests, no simulator needed
- Backend: JUnit 5 + Testcontainers PostgreSQL (extends `AbstractIntegrationTest`) — NEVER H2
- `MockWebSocketClient.emit()` is the primary driver for watchOS WebSocket integration tests
- Run watchOS tests: `xcodebuild test -scheme "BATbern-watch Watch App" -destination 'platform=watchOS Simulator,name=Apple Watch Series 9 (45mm)'`

### Previous Story Intelligence (W4.1, W4.2, W4.3)

- **W4.1 WebSocket infrastructure required**: `WebSocketClient`, `WebSocketService`, `EventDataController.applyServerState` must be fully operational.
- **W4.2 Done button + session-end flow required**: `canMarkDone`, `sendAction(.endSession)`, `sessionEndedEvent`, `WatchSessionService.endSession()`, `NextSessionPeekView` from W4.2 are all prerequisites for W4.4.
- **W4.3 cascade service required**: `WatchSessionService.cascadeSession()` must be extended with the all-complete check (Task 5.3 above).
- **JWT refresh anti-pattern**: Never call `authManager.refreshJWT()` from `WebSocketService` (causes infinite loop — see MEMORY.md + W4.1 dev notes).
- **`MockWebSocketClient`** at `Tests/Mocks/MockWebSocketClient.swift` — use `emit(EventStateMessage(eventCompleted: true, ...))` to test completion routing.
- **`TestDataFactory`** at `Tests/Factories/TestDataFactory.swift` — use for building sessions where all have `completedByUsername` set (for all-complete scenario).
- **Break sessions in public zone**: `SessionCardView.breakCardLayout` is already tested in the public zone (Epic 1). After Task 0 extraction, verify existing public zone tests pass without modification — the extraction is a zero-behavior-change refactor.
- **onAppear anti-pattern**: the `onAppear` guard in Task 3.6 (set `showBreak` from initial state) must NOT trigger a sync or network call — it's a pure state initialization. See MEMORY.md SwiftUI TabView + onAppear anti-pattern.

### References

- [Source: docs/watch-app/epic-4-reuse-map.md#Area-2] — O5 break view reuse mandates: BreakCardLayout extraction, formattedTime reuse, evaluateBreakGong already implemented, no duplicate gong haptic
- [Source: docs/watch-app/epic-4-reuse-map.md#Area-4] — EventStateManager as single source of truth, no parallel state
- [Source: apps/BATbern-watch/BATbern-watch Watch App/Views/Public/SessionCardView.swift:138–175] — `breakCardLayout` to be extracted into `BreakCardLayout`
- [Source: apps/BATbern-watch/BATbern-watch Watch App/Views/Public/SessionCardView.swift:70–82] — `breakIcon` property to move to `BreakCardLayout`
- [Source: apps/BATbern-watch/BATbern-watch Watch App/Views/Public/SessionCardView.swift:49–52] — `isBreakSession` to become `CachedSession.isBreak` extension
- [Source: apps/BATbern-watch/BATbern-watch Watch App/Domain/HapticScheduler.swift:72–82] — `evaluateBreakGong()` already fires `.gongReminder` — W4.4 does NOT add haptic logic
- [Source: apps/BATbern-watch/BATbern-watch Watch App/ViewModels/LiveCountdownViewModel.swift:113–116] — break routing in `refreshState()` (calls `evaluateBreakGong()` when `activeSession.isBreak`)
- [Source: apps/BATbern-watch/BATbern-watch Watch App/ViewModels/LiveCountdownViewModel.swift:34] — `nextSession: WatchSession?` — same source for O5 next speaker preview
- [Source: apps/BATbern-watch/BATbern-watch Watch App/Domain/EventStateManager.swift] — `isLive`, `isPreEvent`, `hasActiveEvent` — no new flags, extend with `isEventCompletedToday`
- [Source: apps/BATbern-watch/BATbern-watch Watch App/Protocols/WebSocketClientProtocol.swift:88–102] — `WatchStateUpdate` struct (add `eventCompleted: Bool`)
- [Source: apps/BATbern-watch/BATbern-watch Watch App/Data/EventDataController.swift:121–137] — `applyServerState` (add `eventCompleted` handling)
- [Source: _bmad-output/implementation-artifacts/w4-2-session-advance-transition.md] — W4.2 NextSessionPeekView, sessionEndedEvent, WatchSessionService.endSession patterns
- [Source: _bmad-output/implementation-artifacts/w4-3-overrun-detection-schedule-cascade.md] — W4.3 cascadeSession, all-session context
- [Source: docs/watch-app/epics.md#W4.4] — Story ACs and architectural constraints
- [Source: docs/watch-app/architecture.md#Frontend-Architecture] — OrganizerZoneView routing logic, break sessions in session type list

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

### File List

**New files (watchOS):**
- `apps/BATbern-watch/BATbern-watch Watch App/Views/Shared/BreakCardLayout.swift`
- `apps/BATbern-watch/BATbern-watch Watch App/Views/Organizer/BreakGongView.swift`
- `apps/BATbern-watch/BATbern-watch Watch App/Views/Organizer/EventCompletedView.swift`

**Modified files (watchOS):**
- `apps/BATbern-watch/BATbern-watch Watch App/Views/Public/SessionCardView.swift`
- `apps/BATbern-watch/BATbern-watch Watch App/Models/CachedSession.swift`
- `apps/BATbern-watch/BATbern-watch Watch App/ViewModels/LiveCountdownViewModel.swift`
- `apps/BATbern-watch/BATbern-watch Watch App/Views/Organizer/LiveCountdownView.swift`
- `apps/BATbern-watch/BATbern-watch Watch App/Domain/EventStateManager.swift`
- `apps/BATbern-watch/BATbern-watch Watch App/Views/OrganizerZoneView.swift`
- `apps/BATbern-watch/BATbern-watch Watch App/Data/EventDataController.swift`
- `apps/BATbern-watch/BATbern-watch Watch App/Protocols/WebSocketClientProtocol.swift`

**New backend files:**
- `services/event-management-service/src/test/java/ch/batbern/events/watch/WatchEventCompletionIntegrationTest.java`

**Modified backend files:**
- `services/event-management-service/src/main/java/ch/batbern/events/watch/WatchSessionService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/watch/dto/WatchStateUpdateMessage.java`
- `services/event-management-service/src/main/java/ch/batbern/events/watch/WatchPresenceService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/domain/SessionRepository.java`

**New test files:**
- `apps/BATbern-watch/BATbern-watch Watch AppTests/Views/BreakCardLayoutTests.swift`
- `apps/BATbern-watch/BATbern-watch Watch AppTests/Views/BreakGongViewTests.swift`
- `apps/BATbern-watch/BATbern-watch Watch AppTests/Views/EventCompletedViewTests.swift`
- `services/event-management-service/src/test/java/ch/batbern/events/watch/WatchEventCompletionIntegrationTest.java`
