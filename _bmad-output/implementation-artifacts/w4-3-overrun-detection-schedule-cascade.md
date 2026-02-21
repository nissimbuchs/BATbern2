# Story W4.3: Extend & Delayed Session Controls

Status: done

---

> **📋 REWRITTEN PER SPRINT CHANGE PROPOSAL (2026-02-19)**
> Previous story ("Overrun Detection & Schedule Cascade") replaced.
> See `docs/sprint-change-proposal-2026-02-19.md` for full rationale.
> Previous story file archived at the bottom of this document (Change Log section).

---

## Design Direction (A2 — Gate Requirement)

**Epic 4 core principle:** One state, one path. All server-driven state flows through `EventDataController` → `EventStateManager` → `LiveCountdownViewModel`. No parallel state object is permitted.

**W4.3 visible changes:**
- `LiveCountdownView` (O3) gains two new proactive control buttons:
  - **Extend button**: appears when `remainingSeconds <= 600` (last 10 minutes of session, not yet overtime). Tapping opens `ExtendSessionView` sheet with options: +5 / +10 / +15 / +20 min. Sends `WatchAction.extendSession(sessionSlug:minutes:)` — extends current session's `scheduledEndTime` WITHOUT ending the session. Countdown resets to new end time on all watches.
  - **Delayed button**: appears when `sessionActiveSeconds < 600` (first 10 minutes of current session). Tapping opens `DelayedSessionView` sheet. Sends new `WatchAction.delayToPrevious(currentSlug:minutes:)` — re-activates the previous session with extra time, resets current to SCHEDULED.
- **Sessions auto-advance via W4.2 amendment** — the Done button (W4.2 original) was removed; `LiveCountdownView` auto-sends `endSession` when `urgencyLevel == .overtime`. W4.3 builds on this foundation: Extend is proactive (before overtime), Delayed is early-transition correction (first 10 min of session).
- **`ExtendSessionView`** (new sheet): 4 buttons (+5/+10/+15/+20 min). On tap: haptic + sends `extendSession`. Session stays active, countdown resets.
- **`DelayedSessionView`** (new sheet): 4 buttons. On tap: haptic + sends `delayToPrevious`. Previous session reactivates on all watches.

**Implementation style:** User-visible action story — two new sheets, no new ViewModels, no new state managers.

---

## Pre-Implementation Review (A1 — Gate Requirement)

**Before writing a single line of code, Dev must confirm:**

| Check | Mandate | Confirmed |
|---|---|---|
| W4.2 amend | Done button removed from `LiveCountdownView` — auto-advance `.onChange(of: viewModel.shouldAutoAdvance)` implemented | [x] |
| W4.2 amend | `LiveCountdownViewModel.shouldAutoAdvance: Bool` (= `urgencyLevel == .overtime`) present | [x] |
| W4.1/W4.2 | `WebSocketService.sendAction(_:)` confirmed available | [x] |
| W4.2 | `WebSocketService.sessionEndedEvent` signal confirmed (auto-advance triggers O6 via this) | [x] |
| Area 4 | `WatchAction.extendSession(sessionSlug:minutes:)` reused with new semantics: extends `scheduledEndTime` of ACTIVE session (does NOT end it) | [x] |
| Area 4 | NEW `WatchAction.delayToPrevious(currentSlug:minutes:)` added to enum in `WebSocketClientProtocol.swift` | [x] |
| Area 4 | Extend/Delayed result flows through `EventDataController.applyServerState()` only — no new cascade state | [x] |
| Area 4 | Conflict resolution is server-side (first-wins) — no client-side merge logic | [x] |
| Area 1 | `shouldShowExtend` and `shouldShowDelayed` derived from existing timer state — no parallel overrun flag | [x] |
| W4.1 | `HapticAlert.actionConfirm` used for extend/delay tap success — no new haptic path | [x] |

---

## Story

As an organizer,
I want to extend the current session's time before it runs out, or restore time to the previous session if we transitioned too early,
so that the schedule stays accurate without the stress of reacting to overruns.

## Acceptance Criteria

1. **AC1 — Extend Button (Last 10 Minutes)**: Given a session has ≤ 10 minutes remaining (`shouldShowExtend == true`, `urgencyLevel != .overtime`), When I view O3 (`LiveCountdownView`), Then an "Extend" button appears in the action area. It does NOT appear when more than 10 minutes remain or when `urgencyLevel == .overtime` (session already auto-advancing).

2. **AC2 — Extend + Downstream Recalculation Synced <3s**: Given I tap Extend and choose N minutes, When confirmed, Then:
   - `WatchAction.extendSession(sessionSlug:minutes:N)` is sent via `WebSocketService.sendAction(_:)`
   - Server extends current session's `scheduledEndTime` by N minutes (session stays ACTIVE, does NOT complete)
   - All downstream sessions shift by N minutes
   - All watches receive STATE_UPDATE with `trigger: "SESSION_EXTENDED"` within 3 seconds (NFR3)
   - `EventDataController.applyServerState` applies new scheduled times → `LiveCountdownViewModel.refreshState()` recalculates from updated `CachedSession.scheduledEndTime` → countdown resets to new end time
   - The Extend button remains available (can extend again if needed)

3. **AC3 — Delayed Button (First 10 Minutes)**: Given the current session has been active for < 10 minutes (`shouldShowDelayed == true`), When I view O3, Then a "Delayed" button appears alongside the Extend button (if applicable). It disappears once the session has been active ≥ 10 minutes.

4. **AC4 — Delayed + Previous Session Re-Activation Synced <3s**: Given I tap Delayed and choose N minutes, When confirmed, Then:
   - `WatchAction.delayToPrevious(currentSlug:minutes:N)` is sent via `WebSocketService.sendAction(_:)`
   - Server: (a) resets current session to SCHEDULED (clears `actualStartTime`); (b) extends previous session `scheduledEndTime` by N min and re-activates it; (c) shifts all sessions from current onward by N min; (d) broadcasts STATE_UPDATE with `trigger: "SESSION_DELAYED"` and `previousSessionSlug` field
   - All watches update within 3 seconds
   - `EventDataController.applyServerState` applies changes → `EventStateManager` derives `activeSession` = previous session → `OrganizerZoneView` routes to previous session's view automatically
   - The Delayed button no longer shown (previous session is active, current is SCHEDULED)

5. **AC5 — Haptic Feedback**: Given I tap either Extend or Delayed, When the tap registers (before server confirmation), Then I feel `HapticAlert.actionConfirm` immediately as optimistic feedback.

6. **AC6 — Button Disable During In-Flight**: Given I tap Extend or Delayed, When the action is in-flight, Then both buttons are disabled to prevent double-sends. They re-enable after the STATE_UPDATE is processed (session slug changes or `scheduledEndTime` updates).

7. **AC7 — Idempotent Conflict Resolution**: Given two organizers simultaneously tap Extend or Delayed, When the first action is processed, Then:
   - First action applied and broadcast to all watches
   - Second organizer's Watch receives updated state via broadcast
   - No duplicate action possible (server first-wins; second action sees already-extended/reset session)

---

## Tasks / Subtasks

### watchOS — ViewModel Properties

- [x] **Task 1: `LiveCountdownViewModel` — add `shouldShowExtend`, `shouldShowDelayed`, `sessionActiveSeconds`** (AC: 1, 3)
  - [x] 1.1 Add `private(set) var shouldShowExtend: Bool = false` to `LiveCountdownViewModel.swift`
  - [x] 1.2 Add `private(set) var shouldShowDelayed: Bool = false` to `LiveCountdownViewModel.swift`
  - [x] 1.3 Add `private(set) var sessionActiveSeconds: Int = 0` — computed from `clock.now - activeSession.actualStartTime`
  - [x] 1.4 In `refreshState()`, after `urgencyLevel` is assigned:
    ```swift
    if let active = activeSession, urgencyLevel != .overtime {
        shouldShowExtend = remainingSeconds <= 600
        if let startTime = active.actualStartTime {
            sessionActiveSeconds = max(0, Int(clock.now.timeIntervalSince(startTime)))
            shouldShowDelayed = sessionActiveSeconds < 600
        } else {
            sessionActiveSeconds = 0
            shouldShowDelayed = false
        }
    } else {
        shouldShowExtend = false
        shouldShowDelayed = false
        sessionActiveSeconds = 0
    }
    ```
  - [x] 1.5 Reset all three to `false`/`0` on session change (existing reset logic in `refreshState` on session change)
  - [x] 1.6 Unit tests in `LiveCountdownViewModelTests.swift`:
    - `shouldShowExtend == false` when `remainingSeconds > 600`
    - `shouldShowExtend == true` when `remainingSeconds <= 600` and not overtime
    - `shouldShowExtend == false` when `urgencyLevel == .overtime`
    - `shouldShowDelayed == true` when `sessionActiveSeconds < 600` (advance clock 300s past `actualStartTime`)
    - `shouldShowDelayed == false` when `sessionActiveSeconds >= 600`
    - Both reset to false when session changes

---

### watchOS — ExtendSessionView Sheet

- [x] **Task 2: Create `ExtendSessionView`** (AC: 1, 2, 5, 6)
  - [x] 2.1 Create `apps/BATbern-watch/BATbern-watch Watch App/Views/Organizer/ExtendSessionView.swift`
  - [x] 2.2 Inputs: `sessionSlug: String`, `onExtend: (Int) -> Void`, `onDismiss: () -> Void`
  - [x] 2.3 Layout (vertical scroll, compact Watch typography):
    ```
    "Extend session?"           ← SF Pro Rounded 14pt secondary
    ──────────────────
    [+5 min]                    ← .borderedProminent, tint .blue
    [+10 min]                   ← .borderedProminent, tint .blue
    [+15 min]                   ← .borderedProminent, tint .blue
    [+20 min]                   ← .borderedProminent, tint .blue
    ```
  - [x] 2.4 On any button tap: `hapticService.play(.actionConfirm)` → `isSending = true` → `onExtend(N)`
  - [x] 2.5 `hapticService` injection: `@State private var hapticService: HapticServiceProtocol = WatchHapticService()` — injectable for tests
  - [x] 2.6 View does NOT contain `WebSocketService` — action sending done by parent `LiveCountdownView` via `onExtend` closure
  - [x] 2.7 Unit tests in `ExtendSessionViewTests.swift`:
    - "+5/+10/+15/+20" taps call `onExtend(5)`, `onExtend(10)`, `onExtend(15)`, `onExtend(20)` respectively
    - Buttons disabled after first tap (`isSending == true`)
    - `HapticAlert.actionConfirm` fired on every tap

---

### watchOS — DelayedSessionView Sheet

- [x] **Task 3: Create `DelayedSessionView`** (AC: 3, 4, 5, 6)
  - [x] 3.1 Create `apps/BATbern-watch/BATbern-watch Watch App/Views/Organizer/DelayedSessionView.swift`
  - [x] 3.2 Inputs: `currentSlug: String`, `onDelay: (Int) -> Void`, `onDismiss: () -> Void`
  - [x] 3.3 Layout:
    ```
    "Give prev session"         ← SF Pro Rounded 13pt secondary
    "more time?"
    ──────────────────
    [+5 min]                    ← .borderedProminent, tint .orange
    [+10 min]
    [+15 min]
    [+20 min]
    ```
  - [x] 3.4 On any button tap: `hapticService.play(.actionConfirm)` → `isSending = true` → `onDelay(N)`
  - [x] 3.5 Same `hapticService` injection pattern as `ExtendSessionView`
  - [x] 3.6 Unit tests in `DelayedSessionViewTests.swift`:
    - "+5/+10/+15/+20" buttons call `onDelay(N)` with correct values
    - Buttons disabled after first tap
    - Haptic fires on every tap

---

### watchOS — LiveCountdownView Wiring

- [x] **Task 4: Wire Extend and Delayed buttons in `LiveCountdownView`** (AC: 1, 2, 3, 4, 6)
  - [x] 4.1 Add `@State private var showExtendPrompt: Bool = false`
  - [x] 4.2 Add `@State private var showDelayedPrompt: Bool = false`
  - [x] 4.3 Add `@State private var isActionInFlight: Bool = false`
  - [x] 4.4 In `countdownContent`, add after existing content:
    ```swift
    if viewModel.shouldShowExtend {
        Button("Extend") { showExtendPrompt = true }
            .buttonStyle(.borderedProminent).tint(.blue)
            .disabled(isActionInFlight)
    }
    if viewModel.shouldShowDelayed {
        Button("Delayed") { showDelayedPrompt = true }
            .buttonStyle(.bordered).tint(.orange)
            .disabled(isActionInFlight)
    }
    ```
  - [x] 4.5 Present `ExtendSessionView` as `.sheet(isPresented: $showExtendPrompt)`:
    ```swift
    .sheet(isPresented: $showExtendPrompt) {
        if let slug = viewModel.activeSession?.slug {
            ExtendSessionView(
                sessionSlug: slug,
                onExtend: { minutes in
                    showExtendPrompt = false
                    isActionInFlight = true
                    Task { try? await webSocketService.sendAction(
                        .extendSession(sessionSlug: slug, minutes: minutes)) }
                },
                onDismiss: { showExtendPrompt = false }
            )
        }
    }
    ```
  - [x] 4.6 Present `DelayedSessionView` as `.sheet(isPresented: $showDelayedPrompt)`:
    ```swift
    .sheet(isPresented: $showDelayedPrompt) {
        if let slug = viewModel.activeSession?.slug {
            DelayedSessionView(
                currentSlug: slug,
                onDelay: { minutes in
                    showDelayedPrompt = false
                    isActionInFlight = true
                    Task { try? await webSocketService.sendAction(
                        .delayToPrevious(currentSlug: slug, minutes: minutes)) }
                },
                onDismiss: { showDelayedPrompt = false }
            )
        }
    }
    ```
  - [x] 4.7 Reset `isActionInFlight = false` via `.onChange(of: viewModel.activeSession?.id)` — session change = action processed
  - [x] 4.8 Unit tests in `LiveCountdownViewTests.swift`:
    - Extend button visible when `shouldShowExtend == true`; hidden when false
    - Delayed button visible when `shouldShowDelayed == true`; hidden when false
    - Extend tap: `sendAction(.extendSession(slug, 5))` called when "+5 min" chosen in sheet
    - Delayed tap: `sendAction(.delayToPrevious(slug, 10))` called when "+10 min" chosen
    - `isActionInFlight == true` while action in-flight (both buttons disabled)

---

### watchOS — WebSocket Protocol Extension

- [x] **Task 5: Add `WatchAction.delayToPrevious` + session event types** (AC: 4)
  - [x] 5.1 In `WebSocketClientProtocol.swift`, add to `WatchAction` enum:
    ```swift
    case delayToPrevious(currentSlug: String, minutes: Int)
    ```
  - [x] 5.2 In `WebSocketClient.swift`, add encoding for `delayToPrevious`:
    ```json
    { "type": "DELAY_TO_PREVIOUS", "sessionSlug": "...", "minutesAdded": N }
    ```
  - [x] 5.3 Add to `EventStateMessageType` enum:
    ```swift
    case sessionDelayed = "SESSION_DELAYED"
    case sessionExtended = "SESSION_EXTENDED"
    ```
  - [x] 5.4 Add `struct SessionDelayedEvent` to `WebSocketClientProtocol.swift`:
    ```swift
    struct SessionDelayedEvent {
        let previousSessionSlug: String
        let currentSessionSlug: String
        let timestamp: Date
    }
    ```
  - [x] 5.5 Add `private(set) var sessionDelayedEvent: SessionDelayedEvent? = nil` to `WebSocketService`
  - [x] 5.6 In `startStateConsumer`, detect `message.type == .sessionDelayed`:
    ```swift
    if message.type == .sessionDelayed, let prevSlug = message.previousSessionSlug {
        sessionDelayedEvent = SessionDelayedEvent(
            previousSessionSlug: prevSlug,
            currentSessionSlug: message.sessionSlug ?? "",
            timestamp: message.timestamp
        )
    }
    ```
    Note: `applyServerState` is called for ALL message types — the time updates are handled there. `sessionDelayedEvent` is the signal for optional future UI feedback.
  - [x] 5.7 Add `consumeSessionDelayedEvent()` method — returns and nils `sessionDelayedEvent` (same pattern as `consumeSessionEndedEvent()`)
  - [x] 5.8 Unit tests in `WebSocketServiceTests.swift`:
    - Emit SESSION_EXTENDED → `applyServerState` called
    - Emit SESSION_DELAYED → `applyServerState` called, `sessionDelayedEvent` set
    - `consumeSessionDelayedEvent()` returns event and sets to nil

---

### watchOS — applyServerState: New Scheduled Times

- [x] **Task 6: Update `EventDataController.applyServerState` + `SessionStateUpdate`** (AC: 2, 4)
  - [x] 6.1 In `WebSocketClientProtocol.swift`, add to `SessionStateUpdate` struct:
    ```swift
    let newScheduledStartTime: Date?   // nil if not changed
    let newScheduledEndTime: Date?     // nil if not changed
    ```
  - [x] 6.2 In `EventDataController.applyServerState(_:)`, after existing field writes, add:
    ```swift
    if let newStart = sessionUpdate.newScheduledStartTime {
        session.scheduledStartTime = newStart
    }
    if let newEnd = sessionUpdate.newScheduledEndTime {
        session.scheduledEndTime = newEnd
    }
    ```
  - [x] 6.3 Also clear `actualStartTime` on SCHEDULED sessions received from SESSION_DELAYED broadcast:
    ```swift
    if session.status == .scheduled, message.trigger == "SESSION_DELAYED" {
        session.actualStartTime = nil
    }
    ```
  - [x] 6.4 Unit tests in `EventDataControllerApplyServerStateTests.swift`:
    - SESSION_EXTENDED: `scheduledEndTime` updated on active + downstream sessions
    - SESSION_DELAYED: previous session `scheduledEndTime` extended; current session `actualStartTime` = nil, `status` = SCHEDULED
    - Non-cascade broadcast: `scheduledStartTime`/`scheduledEndTime` unchanged when `newScheduledStartTime == nil`

---

### Backend — Controller Dispatch

- [x] **Task 7: `WatchWebSocketController` — add EXTEND_SESSION + DELAY_TO_PREVIOUS dispatch** (AC: 2, 4)
  - [x] 7.1 Add to `handleAction` switch in `WatchWebSocketController.java`:
    ```java
    case "EXTEND_SESSION" -> watchSessionService.extendSession(
        eventCode, action.getSessionSlug(), action.getMinutesAdded(), principal.getName()
    );
    case "DELAY_TO_PREVIOUS" -> watchSessionService.delayToPreviousSession(
        eventCode, action.getSessionSlug(), action.getMinutesAdded(), principal.getName()
    );
    ```
  - [x] 7.2 Add `minutesAdded` field to `WatchActionMessage.java`:
    ```java
    private Integer minutesAdded;  // for EXTEND_SESSION and DELAY_TO_PREVIOUS
    ```
  - [x] 7.3 Add null guard: reject EXTEND_SESSION / DELAY_TO_PREVIOUS with null/blank `sessionSlug` or null/zero `minutesAdded` before reaching service (same pattern as M5 fix in W4.2)
  - [x] 7.4 Controller tests in `WatchPresenceControllerTest.java`:
    - EXTEND_SESSION → `watchSessionService.extendSession()` called with correct args
    - DELAY_TO_PREVIOUS → `watchSessionService.delayToPreviousSession()` called
    - Null sessionSlug or null minutesAdded → rejected, service NOT called

---

### Backend — extendSession()

- [x] **Task 8: `WatchSessionService.extendSession()`** (AC: 2, 7)
  - [x] 8.1 Add method to `WatchSessionService.java`:
    ```java
    @Transactional
    public void extendSession(String eventCode, String sessionSlug, int minutesAdded, String requestedBy) {
        Session session = sessionRepository.findByEventCodeAndSessionSlug(eventCode, sessionSlug)
            .orElseThrow(() -> new SessionNotFoundException(sessionSlug, eventCode));
        if (session.getCompletedByUsername() != null) {
            // Already completed (race with auto-advance) — idempotent: re-broadcast, return
            watchPresenceService.buildAndBroadcastState(eventCode, "SESSION_EXTENDED", sessionSlug, requestedBy);
            return;
        }
        // Extend current session end time
        Instant oldEnd = session.getScheduledEndTime();
        session.setScheduledEndTime(oldEnd.plusSeconds(minutesAdded * 60L));
        sessionRepository.save(session);
        // Cascade downstream
        List<Session> downstream = sessionRepository
            .findByEventCodeAndScheduledStartTimeAfterOrderByScheduledStartTime(eventCode, oldEnd);
        for (Session ds : downstream) {
            ds.setScheduledStartTime(ds.getScheduledStartTime().plusSeconds(minutesAdded * 60L));
            ds.setScheduledEndTime(ds.getScheduledEndTime().plusSeconds(minutesAdded * 60L));
        }
        if (!downstream.isEmpty()) sessionRepository.saveAll(downstream);
        // Broadcast
        watchPresenceService.buildAndBroadcastState(eventCode, "SESSION_EXTENDED", sessionSlug, requestedBy);
    }
    ```
  - [x] 8.2 Integration test `WatchExtendSessionTest extends AbstractIntegrationTest`:
    - `extendSession(eventCode, slug, 10, username)` → `scheduledEndTime` +10min, downstream +10min
    - `extendSession` on already-completed session → idempotent re-broadcast, no write
    - Unknown slug → `SessionNotFoundException`
    - Only sessions starting AFTER old `scheduledEndTime` are shifted (not sessions before it)

---

### Backend — delayToPreviousSession()

- [x] **Task 9: `WatchSessionService.delayToPreviousSession()`** (AC: 4, 7)
  - [x] 9.1 Add method to `WatchSessionService.java`:
    ```java
    @Transactional
    public void delayToPreviousSession(String eventCode, String currentSlug, int minutesAdded, String requestedBy) {
        Session current = sessionRepository.findByEventCodeAndSessionSlug(eventCode, currentSlug)
            .orElseThrow(() -> new SessionNotFoundException(currentSlug, eventCode));
        // Find previous session
        Session previous = sessionRepository
            .findFirstByEventCodeAndScheduledStartTimeBeforeOrderByScheduledStartTimeDesc(
                eventCode, current.getScheduledStartTime())
            .orElseThrow(() -> new IllegalStateException(
                "No previous session for '" + currentSlug + "' in event '" + eventCode + "'"));
        // Idempotency: if previous already ACTIVE, re-broadcast and return
        if (SessionStatus.ACTIVE.name().equals(previous.getStatus())) {
            watchPresenceService.buildAndBroadcastStateWithPreviousSlug(
                eventCode, "SESSION_DELAYED", currentSlug, requestedBy, previous.getSessionSlug());
            return;
        }
        // Reset current to SCHEDULED
        current.setStatus("SCHEDULED");
        current.setActualStartTime(null);
        sessionRepository.save(current);
        // Extend previous and re-activate
        previous.setScheduledEndTime(previous.getScheduledEndTime().plusSeconds(minutesAdded * 60L));
        previous.setStatus("ACTIVE");
        sessionRepository.save(previous);
        // Shift current + all downstream
        List<Session> toShift = sessionRepository
            .findByEventCodeAndScheduledStartTimeGreaterThanEqualOrderByScheduledStartTime(
                eventCode, current.getScheduledStartTime());
        for (Session s : toShift) {
            s.setScheduledStartTime(s.getScheduledStartTime().plusSeconds(minutesAdded * 60L));
            s.setScheduledEndTime(s.getScheduledEndTime().plusSeconds(minutesAdded * 60L));
        }
        sessionRepository.saveAll(toShift);
        // Broadcast with previousSessionSlug
        watchPresenceService.buildAndBroadcastStateWithPreviousSlug(
            eventCode, "SESSION_DELAYED", currentSlug, requestedBy, previous.getSessionSlug());
    }
    ```
  - [x] 9.2 Integration test `WatchDelayToPreviousTest extends AbstractIntegrationTest`:
    - `delayToPreviousSession(eventCode, currentSlug, 5, username)` → current SCHEDULED + `actualStartTime = null`; previous ACTIVE + `scheduledEndTime` +5min; all sessions from current onward shifted +5min
    - Second call with same slugs → idempotent (previous already ACTIVE), re-broadcast
    - `currentSlug` has no previous session → `IllegalStateException`
    - Verify `previousSessionSlug` in broadcast

---

### Backend — Repository + Broadcast Additions

- [x] **Task 10: `SessionRepository` — new queries** (AC: 2, 4)
  - [x] 10.1 Add to `SessionRepository.java`:
    ```java
    // For extendSession downstream cascade (already in W4.2 — confirm existence)
    List<Session> findByEventCodeAndScheduledStartTimeAfterOrderByScheduledStartTime(
        String eventCode, Instant after);

    // For delayToPreviousSession — find previous
    Optional<Session> findFirstByEventCodeAndScheduledStartTimeBeforeOrderByScheduledStartTimeDesc(
        String eventCode, Instant before);

    // For delayToPreviousSession — shift current + downstream
    List<Session> findByEventCodeAndScheduledStartTimeGreaterThanEqualOrderByScheduledStartTime(
        String eventCode, Instant startTime);
    ```
  - [x] 10.2 Integration test coverage via Tasks 8 and 9

- [x] **Task 11: `WatchStateUpdateMessage` + `WatchPresenceService` additions** (AC: 2, 4)
  - [x] 11.1 Add `String previousSessionSlug` field to `WatchStateUpdateMessage.java` (nullable — only for SESSION_DELAYED)
  - [x] 11.2 Add `buildAndBroadcastStateWithPreviousSlug(eventCode, trigger, sessionSlug, initiatedBy, previousSlug)` overload to `WatchPresenceService.java`
  - [x] 11.3 Ensure `SessionStateUpdateDto` always includes `newScheduledStartTime` and `newScheduledEndTime` for all sessions in every broadcast (authoritative scheduled times always sent)
  - [x] 11.4 Add `newScheduledStartTime` and `newScheduledEndTime` fields to `SessionStateUpdateDto.java` (populate from `session.getScheduledStartTime()` / `getScheduledEndTime()` for ALL sessions in every broadcast — Watch ignores them if unchanged)

---

## Dev Notes

### Architecture Guardrails

**EXTEND = EXTEND END TIME, NOT END SESSION:**
```swift
// ✅ Correct — session stays ACTIVE, end time pushed out
webSocketService.sendAction(.extendSession(sessionSlug: slug, minutes: 10))
// Server: session.scheduledEndTime += 10min; session status stays ACTIVE; countdown resets

// ❌ Wrong — do NOT send endSession for the Extend action
webSocketService.sendAction(.endSession(sessionSlug: slug))  // WRONG for Extend
```

**DELAYED BUTTON ONLY IN FIRST 10 MIN:**
```swift
// ✅ Correct
shouldShowDelayed = sessionActiveSeconds < 600  // ONLY first 10 minutes

// ❌ Wrong — always showing Delayed
shouldShowDelayed = true  // Too permissive — previous session state may be stale
```

**NO PARALLEL STATE FOR ROUTING:**
```swift
// ✅ Correct — after SESSION_DELAYED, EventStateManager derives activeSession from server state
// OrganizerZoneView reads activeSession → routes to previous session automatically
// No extra routing state needed in LiveCountdownView

// ❌ Wrong — manually routing
@State var showPreviousSession: Bool = true  // FORBIDDEN
```

**W4.2 AUTO-ADVANCE IS REQUIRED PREREQUISITE:**
```swift
// ✅ shouldShowExtend is only true when urgencyLevel != .overtime
// When urgencyLevel reaches .overtime, W4.2 auto-sends endSession automatically
// Extend is proactive (before overrun), never reactive (after 0:00)
shouldShowExtend = remainingSeconds <= 600 && urgencyLevel != .overtime  // ✅
```

### SESSION_EXTENDED Broadcast JSON

```json
Watch → Server:
{ "type": "EXTEND_SESSION", "sessionSlug": "cloud-native-pitfalls", "minutesAdded": 10 }

Server → All Watches:
{
  "type": "STATE_UPDATE", "trigger": "SESSION_EXTENDED",
  "sessionSlug": "cloud-native-pitfalls", "initiatedBy": "marco.organizer",
  "sessions": [
    { "sessionSlug": "cloud-native-pitfalls", "status": "ACTIVE",
      "newScheduledEndTime": "2026-02-14T18:55:00Z" },
    { "sessionSlug": "microservices-mistakes", "status": "SCHEDULED",
      "newScheduledStartTime": "2026-02-14T18:59:00Z",
      "newScheduledEndTime": "2026-02-14T19:44:00Z" }
  ]
}
```

### SESSION_DELAYED Broadcast JSON

```json
Watch → Server:
{ "type": "DELAY_TO_PREVIOUS", "sessionSlug": "microservices-mistakes", "minutesAdded": 5 }

Server → All Watches:
{
  "type": "STATE_UPDATE", "trigger": "SESSION_DELAYED",
  "sessionSlug": "microservices-mistakes",
  "previousSessionSlug": "cloud-native-pitfalls",
  "initiatedBy": "marco.organizer",
  "sessions": [
    { "sessionSlug": "cloud-native-pitfalls", "status": "ACTIVE",
      "newScheduledEndTime": "2026-02-14T18:50:00Z" },
    { "sessionSlug": "microservices-mistakes", "status": "SCHEDULED",
      "actualStartTime": null,
      "newScheduledStartTime": "2026-02-14T18:54:00Z",
      "newScheduledEndTime": "2026-02-14T19:39:00Z" }
  ]
}
```

### Project Structure

```
New files (watchOS):
apps/BATbern-watch/BATbern-watch Watch App/Views/Organizer/ExtendSessionView.swift
apps/BATbern-watch/BATbern-watch Watch App/Views/Organizer/DelayedSessionView.swift

Modified files (watchOS):
apps/BATbern-watch/BATbern-watch Watch App/ViewModels/LiveCountdownViewModel.swift
apps/BATbern-watch/BATbern-watch Watch App/Views/Organizer/LiveCountdownView.swift
apps/BATbern-watch/BATbern-watch Watch App/Data/WebSocketService.swift
apps/BATbern-watch/BATbern-watch Watch App/Protocols/WebSocketClientProtocol.swift
apps/BATbern-watch/BATbern-watch Watch App/Data/EventDataController.swift

New test files (watchOS):
apps/BATbern-watch/BATbern-watch Watch AppTests/Views/ExtendSessionViewTests.swift
apps/BATbern-watch/BATbern-watch Watch AppTests/Views/DelayedSessionViewTests.swift

Modified test files (watchOS):
apps/BATbern-watch/BATbern-watch Watch AppTests/ViewModels/LiveCountdownViewModelTests.swift
apps/BATbern-watch/BATbern-watch Watch AppTests/Views/LiveCountdownViewTests.swift
apps/BATbern-watch/BATbern-watch Watch AppTests/Data/WebSocketServiceTests.swift

New backend test files:
services/event-management-service/src/test/java/ch/batbern/events/watch/WatchExtendSessionTest.java
services/event-management-service/src/test/java/ch/batbern/events/watch/WatchDelayToPreviousTest.java

Modified backend files:
services/event-management-service/src/main/java/ch/batbern/events/watch/WatchSessionService.java
services/event-management-service/src/main/java/ch/batbern/events/watch/WatchWebSocketController.java
services/event-management-service/src/main/java/ch/batbern/events/watch/dto/WatchActionMessage.java
services/event-management-service/src/main/java/ch/batbern/events/watch/dto/WatchStateUpdateMessage.java
services/event-management-service/src/main/java/ch/batbern/events/watch/dto/SessionStateUpdateDto.java
services/event-management-service/src/main/java/ch/batbern/events/watch/WatchPresenceService.java
services/event-management-service/src/main/java/ch/batbern/events/domain/SessionRepository.java
```

### Testing Standards

- watchOS: Swift Testing (`@Test`, `#expect`) — pure unit tests, no simulator needed
- Backend: JUnit 5 + Testcontainers PostgreSQL (extends `AbstractIntegrationTest`) — NEVER H2
- Use `MockWebSocketClient.emit()` to simulate SESSION_EXTENDED and SESSION_DELAYED broadcasts
- Use `TestDataFactory` sessions with `actualStartTime` in past to test `shouldShowDelayed`

### Prerequisites from W4.1/W4.2

- W4.2 amendment (Done button removed, auto-advance added) MUST be complete
- `WebSocketService.sendAction(_:)` available (W4.1/W4.2)
- `WebSocketService.sessionEndedEvent` + `consumeSessionEndedEvent()` available (W4.2)
- `WatchSessionService.endSession()` + `SessionRepository.findByEventCodeAndSessionSlug` available (W4.2)
- JWT refresh anti-pattern: never call `authManager.refreshJWT()` from `WebSocketService`

### References

- [Source: docs/sprint-change-proposal-2026-02-19.md] — Approved design rationale
- [Source: docs/watch-app/epic-4-reuse-map.md#Area-4] — No cascade state, applyServerState only
- [Source: docs/watch-app/epic-4-reuse-map.md#Area-1] — No parallel overrun flag
- [Source: _bmad-output/implementation-artifacts/w4-2-session-advance-transition.md] — WebSocket patterns, WatchSessionService base

---

## Dev Agent Record

### Agent Model Used

Claude Opus 4.6

### Debug Log References

- /tmp/w43-task1-tests.log — LiveCountdownViewModel tests (27/27 passed)
- /tmp/w43-task2-tests.log — ExtendSessionView tests (3/3 passed)
- /tmp/w43-task3-tests.log — DelayedSessionView tests (3/3 passed)
- /tmp/w43-task4-tests.log — LiveCountdownView tests (7/7 passed)
- /tmp/w43-task5-tests.log — WebSocketService tests (15/15 passed)
- /tmp/w43-task6-tests.log — EventDataController applyServerState tests (9/9 passed)
- /tmp/w43-task7-tests.log — WatchPresenceControllerTest (14/14 passed — 7 existing + 7 new)
- /tmp/w43-task8-tests.log — WatchExtendSessionTest (4/4 passed)
- /tmp/w43-task9-tests.log — WatchDelayToPreviousTest (4/4 passed)
- /tmp/w43-full-backend-tests.log — Full event-management-service regression (1132/1132 passed)

### Completion Notes List

- **Task 1**: Added `shouldShowExtend`, `shouldShowDelayed`, `sessionActiveSeconds` to `LiveCountdownViewModel.refreshState()`. Reset on session change. 7 new tests.
- **Task 2**: Created `ExtendSessionView` — ScrollView with +5/+10/+15/+20 buttons, blue tint, haptic on tap, isSending disable. Injectable hapticService.
- **Task 3**: Created `DelayedSessionView` — same pattern, orange tint, two-line header "Give prev session / more time?".
- **Task 4**: Wired both sheets in `LiveCountdownView` — `.sheet` modifiers, `isActionInFlight` guard, reset via `.onChange(of: viewModel.activeSession?.id)`.
- **Task 5**: `delayToPrevious` encoding added to `WebSocketClient.swift`. `SessionDelayedEvent` struct added. `sessionDelayedEvent` + `consumeSessionDelayedEvent()` added to `WebSocketService`. SESSION_DELAYED detection in `startStateConsumer`. 3 new tests.
- **Task 6**: `applyServerState` now applies `newScheduledStartTime`/`newScheduledEndTime` from server broadcasts. 3 new tests.
- **Note**: `WatchAction.delayToPrevious`, `EventStateMessageType.sessionDelayed/.sessionExtended`, `SessionStateUpdate.newScheduledStartTime/.newScheduledEndTime`, and `EventStateMessage.previousSessionSlug` were already pre-wired in `WebSocketClientProtocol.swift`.
- **Task 7**: Added EXTEND_SESSION and DELAY_TO_PREVIOUS dispatch to `WatchWebSocketController.handleAction()`. Extracted `isValidSessionAction()` helper for null guard (sessionSlug + minutes validation). `WatchActionMessage.minutes` field already existed — matches Watch JSON key. 7 new unit tests.
- **Task 8**: Implemented `WatchSessionService.extendSession()` — extends `endTime`, cascades downstream sessions via `findByEventCodeAndScheduledStartTimeAfterOrderByScheduledStartTime`. Idempotent on completed sessions. 4 new integration tests.
- **Task 9**: Implemented `WatchSessionService.delayToPreviousSession()` — resets current session (`actualStartTime = null`), re-activates previous (clears `actualEndTime`/`completedByUsername`/`overrunMinutes`, extends `endTime`), shifts current + downstream. Idempotent when previous already active. 4 new integration tests.
- **Task 10**: Added 3 `@Query` methods to `SessionRepository`: downstream cascade, find-previous, and shift-current-and-downstream. Tested via Tasks 8 and 9.
- **Task 11**: Added `previousSessionSlug` to `WatchStateUpdateMessage` (backward-compatible constructor). Added `newScheduledStartTime`/`newScheduledEndTime` to `SessionStateDto` (populated from existing `startTime`/`endTime` for ALL sessions). Added `buildAndBroadcastState(4-param)` and `buildAndBroadcastStateWithPreviousSlug(5-param)` to `WatchPresenceService`.
- **Note**: Session entity uses `startTime`/`endTime` (not `scheduledStartTime`/`scheduledEndTime`). Story references adapted to match actual column names.

### File List

**New files (watchOS):**
- apps/BATbern-watch/BATbern-watch Watch App/Views/Organizer/ExtendSessionView.swift
- apps/BATbern-watch/BATbern-watch Watch App/Views/Organizer/DelayedSessionView.swift
- apps/BATbern-watch/BATbern-watch Watch AppTests/Views/ExtendSessionViewTests.swift
- apps/BATbern-watch/BATbern-watch Watch AppTests/Views/DelayedSessionViewTests.swift

**Modified files (watchOS):**
- apps/BATbern-watch/BATbern-watch Watch App/ViewModels/LiveCountdownViewModel.swift
- apps/BATbern-watch/BATbern-watch Watch App/Views/Organizer/LiveCountdownView.swift
- apps/BATbern-watch/BATbern-watch Watch App/Data/WebSocketService.swift
- apps/BATbern-watch/BATbern-watch Watch App/Data/WebSocketClient.swift
- apps/BATbern-watch/BATbern-watch Watch App/Data/EventDataController.swift
- apps/BATbern-watch/BATbern-watch Watch App/Protocols/WebSocketClientProtocol.swift

**Modified test files (watchOS):**
- apps/BATbern-watch/BATbern-watch Watch AppTests/ViewModels/LiveCountdownViewModelTests.swift
- apps/BATbern-watch/BATbern-watch Watch AppTests/Data/WebSocketServiceTests.swift
- apps/BATbern-watch/BATbern-watch Watch AppTests/Data/EventDataControllerApplyServerStateTests.swift

**New backend test files:**
- services/event-management-service/src/test/java/ch/batbern/events/watch/WatchExtendSessionTest.java
- services/event-management-service/src/test/java/ch/batbern/events/watch/WatchDelayToPreviousTest.java

**Modified backend files:**
- services/event-management-service/src/main/java/ch/batbern/events/watch/WatchWebSocketController.java
- services/event-management-service/src/main/java/ch/batbern/events/watch/WatchSessionService.java
- services/event-management-service/src/main/java/ch/batbern/events/watch/WatchPresenceService.java
- services/event-management-service/src/main/java/ch/batbern/events/watch/dto/WatchStateUpdateMessage.java
- services/event-management-service/src/main/java/ch/batbern/events/repository/SessionRepository.java

**Modified backend test files:**
- services/event-management-service/src/test/java/ch/batbern/events/watch/WatchPresenceControllerTest.java

---

## Change Log

- **2026-02-19** (SM — Bob): Story completely rewritten per approved Sprint Change Proposal (`docs/sprint-change-proposal-2026-02-19.md`). Approved by Nissim. Previous story title: "W4.3: Overrun Detection & Schedule Cascade". Previous design: cascade-prompt-on-overrun (O4 CascadePromptView) triggered by Done button tap. New design: Extend button (last 10 min) + Delayed button (first 10 min), sessions auto-advance, no cascade-on-overrun concept.
- **2026-02-19** (Dev — Amelia): All 11 tasks implemented (watchOS Tasks 1-6 + backend Tasks 7-11). 15 new tests added (7 controller unit + 4 extendSession integration + 4 delayToPrevious integration). Full regression green (1132 event-management tests + 189 api-gateway tests). Status → review.
