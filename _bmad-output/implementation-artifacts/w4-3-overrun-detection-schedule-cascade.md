# Story W4.3: Overrun Detection & Schedule Cascade

Status: ready-for-dev

---

## Design Direction (A2 — Gate Requirement)

**Epic 4 core principle:** One state, one path. All server-driven state flows through `EventDataController` → `EventStateManager` → `LiveCountdownViewModel`. No parallel state object is permitted.

**W4.3 visible changes:**
- `LiveCountdownView` (O3) — when session is past 0:00, the Done button tap **routes to O4** (`CascadePromptView`) instead of sending `endSession` directly. The red "+MM:SS over" display is already rendered by W4.2; W4.3 adds a **delay impact indicator** below it (e.g., "~5 min delay").
- **O4 `CascadePromptView`** — sheet/overlay that appears on Done tap when `overrunSeconds > 0`:
  - Header: "Session ran +N min over."
  - Sub-header: "Shift remaining schedule?"
  - Three buttons: "+5 min" / "+10 min" / "Absorb in break"
  - Each button sends `WatchAction.extendSession(sessionSlug:minutes:N)` (N=5, 10, or 0)
  - On send: `HapticAlert.actionConfirm` immediately
- **After cascade**: server ends the current session + shifts all downstream `scheduledStartTime`/`scheduledEndTime` by N minutes. Server broadcasts `STATE_UPDATE` with `trigger: "SCHEDULE_CASCADED"`. `EventDataController.applyServerState` applies the new times → `LiveCountdownViewModel.refreshState()` recalculates → Done button disappears → O6 (W4.2) transitions to next speaker.
- **Idempotency**: two organizers tapping Done simultaneously → first-wins on server. Second sees already-shifted schedule via broadcast. No O4 needed on the second Watch (session already completed).

**Implementation style:** User-visible action story — O4 is the only new screen. No new ViewModels. No new state managers. Cascade state lives exclusively in `CachedSession.scheduledStartTime`/`scheduledEndTime` after `applyServerState` runs.

---

## Pre-Implementation Review (A1 — Gate Requirement)

**Before writing a single line of code, Dev must confirm the following from `docs/watch-app/epic-4-reuse-map.md`:**

| Check | Mandate | Confirmed |
|---|---|---|
| Area 4 | `WatchAction.extendSession(sessionSlug:minutes:)` used as-is — no new cascade action type | [ ] |
| Area 4 | Cascade result flows through `EventDataController.applyServerState()` only — no `CascadeStateManager` | [ ] |
| Area 4 | `CachedSession.overrunMinutes` populated from server — no new schema field | [ ] |
| Area 4 | Downstream session `scheduledStartTime`/`scheduledEndTime` updated via `applyServerState` — `LiveCountdownViewModel` recalculates automatically | [ ] |
| Area 4 | Conflict resolution is server-side (first-wins) — no client-side merge logic | [ ] |
| Area 1 | Overrun state (`urgencyLevel == .overtime`) already computed by `SessionTimerEngine` — W4.3 does NOT add a parallel overrun flag | [ ] |
| W4.1 | `WebSocketService.sendAction(_:)` confirmed available (W4.1+W4.2 prerequisite) | [ ] |
| W4.2 | `canMarkDone` property confirmed on `LiveCountdownViewModel` (W4.2 prerequisite) | [ ] |
| W4.2 | `WebSocketService.sessionEndedEvent` signal confirmed available — W4.3 cascade ends the session, W4.2 O6 transition fires automatically | [ ] |
| W4.2 | `HapticAlert.actionConfirm` used for cascade-tap success haptic — no new haptic path | [ ] |

---

## Story

As an organizer,
I want the Watch to detect overruns and let me shift the entire remaining schedule with one tap,
so that one speaker running late doesn't cascade into chaos.

## Acceptance Criteria

1. **AC1 — Cascade Prompt on Done While Overrun**: Given a session runs past 0:00, When `LiveCountdownViewModel.overrunSeconds > 0` and I tap the "Done" button, Then **O4 `CascadePromptView`** appears (as a sheet) showing the overrun duration and three cascade options: "+5 min", "+10 min", and "Absorb in break". The prompt does NOT appear when `overrunSeconds == 0` (session ended exactly on time — W4.2's direct `endSession` path is used instead).

2. **AC2 — Cascade + Downstream Recalculation Synced <3s**: Given I'm on O4, When I choose a shift increment (+5 min or +10 min or absorb), Then:
   - `WatchAction.extendSession(sessionSlug:minutes:N)` is sent via `WebSocketService.sendAction(_:)`
   - The server ends the current session AND shifts all downstream sessions' `scheduledStartTime`/`scheduledEndTime` by N minutes (FR9)
   - All watches receive the updated STATE_UPDATE broadcast with `trigger: "SCHEDULE_CASCADED"` within 3 seconds (NFR3, FR19)
   - `EventDataController.applyServerState` applies the new scheduled times → `LiveCountdownViewModel` recalculates from updated `CachedSession` data → all watches show new countdown times for remaining sessions
   - O4 dismisses and the Watch flows into the W4.2 O6 `SessionTransitionView` (if next session is a talk)

3. **AC3 — Overrun Display + Delay Impact Indicator**: Given I'm on O3 during a session, When `overrunSeconds > 0`, Then:
   - The existing red "+MM:SS over" countdown is visible (already provided by W4.2 overtime display)
   - A **delay impact indicator** appears below the red countdown: "~N min delay" where N = `ceil(overrunSeconds / 60)` (FR10)
   - The delay impact indicator updates every second as overrun grows

4. **AC4 — Idempotent First-Wins Conflict Resolution**: Given two organizers tap Done simultaneously while overrunning, When the first cascade action is processed by the server, Then:
   - The first organizer's chosen cascade is applied and broadcast to all watches
   - The second organizer's Watch receives the STATE_UPDATE showing the already-shifted schedule (and already-completed session)
   - The second organizer's Watch does NOT show O4 (Done button gone — session already advanced)
   - No duplicate cascade is possible

---

## Tasks / Subtasks

### watchOS — Extend Server State Protocol for Cascade

- [ ] **Task 0: Extend `SessionStateUpdate` to carry new scheduled times** (AC: 2 — Area 4 mandate)
  - [ ] 0.1 In `WebSocketClientProtocol.swift`, add to `SessionStateUpdate`:
    ```swift
    /// W4.3: new scheduled times after cascade — nil if not cascaded
    let newScheduledStartTime: Date?
    let newScheduledEndTime: Date?
    ```
  - [ ] 0.2 Add `EventStateMessageType.scheduleCascaded = "SCHEDULE_CASCADED"` to the `EventStateMessageType` enum in `WebSocketClientProtocol.swift`
  - [ ] 0.3 In `EventDataController.applyServerState(_:)`, update the session write loop to also apply new scheduled times:
    ```swift
    if let newStart = sessionUpdate.newScheduledStartTime {
        session.scheduledStartTime = newStart
    }
    if let newEnd = sessionUpdate.newScheduledEndTime {
        session.scheduledEndTime = newEnd
    }
    ```
    These lines are added AFTER the existing writes (`actualStartTime`, `actualEndTime`, `overrunMinutes`, `completedByUsername`).
  - [ ] 0.4 Unit tests in `EventDataControllerApplyServerStateTests.swift` (existing file from W4.1):
    - Cascade broadcast: verify `scheduledStartTime` and `scheduledEndTime` updated on downstream sessions
    - Non-cascade broadcast: verify `scheduledStartTime`/`scheduledEndTime` unchanged when `newScheduledStartTime == nil`

---

### watchOS — Overrun Seconds in LiveCountdownViewModel

- [ ] **Task 1: `LiveCountdownViewModel.overrunSeconds` + `shouldShowCascadePrompt`** (AC: 1, 3)
  - [ ] 1.1 Add `private(set) var overrunSeconds: Int = 0` to `LiveCountdownViewModel.swift`
  - [ ] 1.2 In `refreshState()`, after `urgencyLevel` is assigned, add:
    ```swift
    if urgencyLevel == .overtime, let activeSession = activeSession {
        overrunSeconds = max(0, Int(clock.now.timeIntervalSince(activeSession.scheduledEndTime ?? clock.now)))
    } else {
        overrunSeconds = 0
    }
    ```
  - [ ] 1.3 Add `var shouldShowCascadePrompt: Bool { canMarkDone && overrunSeconds > 0 }` — this is the O4 trigger (overrun Done path)
  - [ ] 1.4 Add `var delayImpactMinutes: Int { Int(ceil(Double(overrunSeconds) / 60.0)) }` — used in O3 delay impact text
  - [ ] 1.5 Unit tests in `LiveCountdownViewModelTests.swift`:
    - `overrunSeconds == 0` when session on time
    - `overrunSeconds > 0` when `clock.now > session.scheduledEndTime` (advance clock past end)
    - `shouldShowCascadePrompt == true` only when `overrunSeconds > 0` (not at exactly 0:00)
    - `shouldShowCascadePrompt == false` when `overrunSeconds == 0` (W4.2 direct endSession path)
    - `delayImpactMinutes` rounds up correctly (30s overrun → 1 min, 61s → 2 min)

---

### watchOS — O4 CascadePromptView

- [ ] **Task 2: Create O4 `CascadePromptView`** (AC: 1, 2, 4)
  - [ ] 2.1 Create `apps/BATbern-watch/BATbern-watch Watch App/Views/Organizer/CascadePromptView.swift`
  - [ ] 2.2 Inputs: `sessionSlug: String`, `overrunMinutes: Int`, `onCascade: (Int) -> Void` (called with chosen cascade minutes), `onDismiss: () -> Void`
  - [ ] 2.3 Layout (vertical scroll, compact Watch typography):
    ```
    "+N min over"              ← red, SF Mono ~20pt bold (overrunMinutes value)
    "Shift remaining schedule?"  ← SF Pro Rounded 13pt secondary
    ─────────────────
    [+5 min]                   ← .borderedProminent, tint .orange
    [+10 min]                  ← .borderedProminent, tint .orange
    [Absorb in break]          ← .bordered, tint .secondary
    ```
  - [ ] 2.4 On any cascade button tap:
    - Call `hapticService.play(.actionConfirm)` immediately (optimistic feedback)
    - Set `isSending = true` to disable buttons (prevent double-tap)
    - Call `onCascade(N)` with the chosen minutes (5, 10, or 0)
  - [ ] 2.5 `hapticService` injection: `@State private var hapticService: HapticServiceProtocol = WatchHapticService()` — injectable for tests (same pattern as W4.2 `LiveCountdownView`)
  - [ ] 2.6 The view does NOT contain `WebSocketService` or send actions itself — action sending is done by the parent `LiveCountdownView` via the `onCascade` closure
  - [ ] 2.7 Unit tests in `CascadePromptViewTests.swift`:
    - Shows correct `overrunMinutes` in header
    - "+5 min" tap calls `onCascade(5)`
    - "+10 min" tap calls `onCascade(10)`
    - "Absorb in break" tap calls `onCascade(0)`
    - Buttons disabled after first tap (`isSending == true`)
    - `HapticAlert.actionConfirm` fired on every cascade button tap

---

### watchOS — Delay Impact Indicator on O3

- [ ] **Task 3: Add delay impact indicator to `LiveCountdownView`** (AC: 3)
  - [ ] 3.1 In `LiveCountdownView.swift`, in the `countdownContent` section where the red "+MM:SS over" text is displayed (the overtime display introduced by W4.2):
    - Below the red overtime text, add a delay impact capsule:
      ```swift
      if viewModel.overrunSeconds > 0 {
          Text("~\(viewModel.delayImpactMinutes) min delay")
              .font(.system(size: 10, weight: .semibold))
              .foregroundStyle(.orange)
              .padding(.horizontal, 6)
              .background(Color.orange.opacity(0.15), in: Capsule())
      }
      ```
  - [ ] 3.2 The delay impact text updates on every timer tick (every second) since `delayImpactMinutes` is derived from `overrunSeconds` which is set in `refreshState()` — no extra wiring needed
  - [ ] 3.3 Unit tests in `LiveCountdownViewTests.swift`:
    - Delay indicator visible when `overrunSeconds > 0`
    - Delay indicator hidden when `overrunSeconds == 0`
    - Text shows correct minute value from `delayImpactMinutes`

---

### watchOS — Modified Done Button Flow + O4 Presentation

- [ ] **Task 4: Modify Done button in `LiveCountdownView` to route to O4 when overrunning** (AC: 1, 2, 4)
  - [ ] 4.1 In `LiveCountdownView.swift`, add `@State private var showCascadePrompt: Bool = false`
  - [ ] 4.2 Modify the `doneButton` action (from W4.2 Task 2.4):
    - **Before (W4.2 — sends endSession directly):**
      ```swift
      // On tap: haptic + sendAction(.endSession)
      ```
    - **After (W4.3 — route to O4 if overrunning):**
      ```swift
      Button("Done") {
          if viewModel.shouldShowCascadePrompt {
              // Route to O4: show cascade prompt
              showCascadePrompt = true
          } else {
              // W4.2 path: session ended on time, send directly
              hapticService.play(.actionConfirm)
              isSendingDone = true
              Task {
                  try? await webSocketService.sendAction(.endSession(sessionSlug: slug))
                  isSendingDone = false
              }
          }
      }
      .disabled(isSendingDone)
      ```
  - [ ] 4.3 Present O4 as a `.sheet(isPresented: $showCascadePrompt)`:
    ```swift
    .sheet(isPresented: $showCascadePrompt) {
        if let slug = viewModel.activeSession?.slug {
            CascadePromptView(
                sessionSlug: slug,
                overrunMinutes: viewModel.delayImpactMinutes,
                onCascade: { minutes in
                    showCascadePrompt = false
                    isSendingDone = true
                    Task {
                        try? await webSocketService.sendAction(
                            .extendSession(sessionSlug: slug, minutes: minutes)
                        )
                        isSendingDone = false
                    }
                },
                onDismiss: { showCascadePrompt = false }
            )
        }
    }
    ```
  - [ ] 4.4 After `extendSession` action is processed by server:
    - Server broadcasts `STATE_UPDATE` with `trigger: "SESSION_ENDED"` or `trigger: "SCHEDULE_CASCADED"`
    - `WebSocketService` receives it → calls `EventDataController.applyServerState` → session completes + downstream times updated
    - W4.2's `sessionEndedEvent` signal fires → `showTransition = true` → O6 appears (if next session is a talk)
    - No extra wiring needed in W4.3 — the W4.2 machinery handles O6 transition
  - [ ] 4.5 Idempotency: if `viewModel.canMarkDone == false` (session already completed by another organizer), `showCascadePrompt` cannot be opened (button is gone)
  - [ ] 4.6 Unit tests in `LiveCountdownViewTests.swift`:
    - When `shouldShowCascadePrompt == true`: Done tap shows O4, does NOT send `endSession`
    - When `shouldShowCascadePrompt == false`: Done tap sends `endSession` directly (W4.2 path)
    - `sendAction(.extendSession(sessionSlug:minutes:5))` called when "+5 min" chosen in O4
    - `sendAction(.extendSession(sessionSlug:minutes:0))` called when "Absorb in break" chosen
    - `isSendingDone == true` while action is in-flight (buttons disabled)

---

### watchOS — WebSocket Cascade Signal

- [ ] **Task 5: `WebSocketService` — cascade signal for UI feedback** (AC: 2)
  - [ ] 5.1 Add `private(set) var sessionCascadedEvent: SessionCascadedEvent? = nil` to `WebSocketService`, where:
    ```swift
    struct SessionCascadedEvent {
        let sessionSlug: String
        let cascadeMinutes: Int
        let timestamp: Date
    }
    ```
  - [ ] 5.2 In `startStateConsumer`, after calling `eventDataController.applyServerState(update)`, check `message.type == .scheduleCascaded`:
    ```swift
    if message.type == .scheduleCascaded, let slug = message.sessionSlug {
        sessionCascadedEvent = SessionCascadedEvent(
            sessionSlug: slug,
            cascadeMinutes: 0,  // not critical for client display
            timestamp: message.timestamp
        )
    }
    ```
  - [ ] 5.3 **Note:** `sessionCascadedEvent` is optional — `LiveCountdownView` does NOT need to observe it for basic cascade flow. The cascade ends the current session, which fires the existing `sessionEndedEvent` (W4.2) and causes O6 to appear. `sessionCascadedEvent` is available for future toast/confirmation display if needed. Do not wire it up in W4.3 Views unless AC2 feedback requires it.
  - [ ] 5.4 Unit tests in `WebSocketServiceTests.swift`:
    - Emit `SCHEDULE_CASCADED` message via `MockWebSocketClient` → verify `sessionCascadedEvent` is set
    - Verify `applyServerState` still called on cascade broadcast (Area 4 mandate)

---

### Backend — Cascade Handler + Session Shifting

- [ ] **Task 6: `WatchWebSocketController.handleAction()` — add EXTEND_SESSION dispatch** (AC: 2, 4)
  - [ ] 6.1 In `WatchWebSocketController.java`, add to the `handleAction` switch (from W4.2 Task 6.1):
    ```java
    case "EXTEND_SESSION" -> watchSessionService.cascadeSession(
        eventCode,
        action.getSessionSlug(),
        action.getCascadeMinutes(),
        principal.getName()
    );
    ```
  - [ ] 6.2 Add `cascadeMinutes` field to `WatchActionMessage.java` DTO:
    ```java
    private Integer cascadeMinutes;  // null for non-cascade actions
    ```
  - [ ] 6.3 Confirm `sessionSlug` is already in `WatchActionMessage` (added in W4.2 Task 6.2)

- [ ] **Task 7: `WatchSessionService.cascadeSession()`** (AC: 2, 4)
  - [ ] 7.1 Add `cascadeSession(String eventCode, String sessionSlug, int cascadeMinutes, String completedByUsername)` to `WatchSessionService` (alongside existing `endSession()`):
    - **Step 1 — End current session (same as `endSession`, idempotent guard):**
      ```java
      Session session = sessionRepository.findByEventCodeAndSlug(eventCode, sessionSlug)
          .orElseThrow(() -> new SessionNotFoundException(sessionSlug));
      if (session.getCompletedByUsername() != null) {
          // Already completed — idempotency: re-broadcast current state and return
          watchPresenceService.buildAndBroadcastState(eventCode, "SCHEDULE_CASCADED", sessionSlug, completedByUsername);
          return;
      }
      session.setActualEndTime(Instant.now());
      long overrunMins = max(0, ChronoUnit.MINUTES.between(
          session.getScheduledEndTime(), session.getActualEndTime()));
      session.setOverrunMinutes((int) overrunMins);
      session.setCompletedByUsername(completedByUsername);
      sessionRepository.save(session);
      ```
    - **Step 2 — Shift downstream sessions (if cascadeMinutes > 0):**
      ```java
      if (cascadeMinutes > 0) {
          List<Session> downstream = sessionRepository
              .findByEventCodeAndScheduledStartTimeAfterOrderByScheduledStartTime(
                  eventCode, session.getScheduledEndTime());
          for (Session downstream : downstreamSessions) {
              downstream.setScheduledStartTime(
                  downstream.getScheduledStartTime().plusSeconds(cascadeMinutes * 60L));
              downstream.setScheduledEndTime(
                  downstream.getScheduledEndTime().plusSeconds(cascadeMinutes * 60L));
          }
          sessionRepository.saveAll(downstreamSessions);
      }
      ```
    - **Step 3 — Broadcast full updated state:**
      ```java
      watchPresenceService.buildAndBroadcastState(eventCode, "SCHEDULE_CASCADED", sessionSlug, completedByUsername);
      ```
  - [ ] 7.2 Idempotency: second call with same `sessionSlug` after completion → re-broadcast current state, no second write
  - [ ] 7.3 `cascadeMinutes == 0` ("Absorb in break"): only end the session, no shifting — same as `endSession()` but broadcast trigger is `"SCHEDULE_CASCADED"` not `"SESSION_ENDED"` so Watch knows to NOT show O4 again
  - [ ] 7.4 Integration test `WatchCascadeIntegrationTest` extends `AbstractIntegrationTest`:
    - `cascadeSession(eventCode, slug, 5, username)` → current session has `completedByUsername` set + downstream sessions shifted by 5 min
    - `cascadeSession(eventCode, slug, 0, username)` → current session completed, NO downstream shift
    - Second call with same slug → idempotent, no second write, state re-broadcast
    - `SessionNotFoundException` thrown for unknown slug

- [ ] **Task 8: `SessionRepository` — downstream session query** (AC: 2)
  - [ ] 8.1 Add to `SessionRepository.java`:
    ```java
    List<Session> findByEventCodeAndScheduledStartTimeAfterOrderByScheduledStartTime(
        String eventCode, Instant after);
    ```
    (Check if a similar method exists from earlier stories before adding)
  - [ ] 8.2 Integration test: verify correct sessions returned in order by start time

- [ ] **Task 9: `WatchStateUpdateMessage` — new scheduled times in session updates** (AC: 2)
  - [ ] 9.1 Add to `SessionStateUpdateDto.java` (the per-session DTO inside `WatchStateUpdateMessage`):
    ```java
    private Instant newScheduledStartTime;   // populated for cascaded sessions (null otherwise)
    private Instant newScheduledEndTime;     // populated for cascaded sessions (null otherwise)
    ```
  - [ ] 9.2 In `WatchPresenceService.buildStateUpdate()`, when building `SessionStateUpdateDto` for each session: populate `newScheduledStartTime` and `newScheduledEndTime` from `session.getScheduledStartTime()` and `session.getScheduledEndTime()`. These are always populated (not just on cascade) — Watch ignores them if unchanged.
  - [ ] 9.3 Verify the Watch-side `SessionStateUpdate` struct (`WebSocketClientProtocol.swift`) decodes `newScheduledStartTime` and `newScheduledEndTime` correctly (Task 0.1 must be complete)

---

### Backend — `WatchPresenceService.buildStateUpdate` Extension

- [ ] **Task 10: `WatchPresenceService.buildStateUpdate` — include SCHEDULE_CASCADED trigger and per-session scheduled times** (AC: 2)
  - [ ] 10.1 Verify `buildAndBroadcastState(eventCode, trigger, sessionSlug, initiatedBy)` method was added in W4.2 Task 7.3 — if not, add it now (same signature as W4.2 requires)
  - [ ] 10.2 Ensure `trigger: "SCHEDULE_CASCADED"` is properly set in the broadcast message for cascade events
  - [ ] 10.3 Verify `SessionStateUpdateDto` includes `scheduledStartTime` and `scheduledEndTime` fields for ALL sessions in the broadcast (not just the cascaded ones) — this ensures the Watch always has the authoritative scheduled times after each broadcast

---

## Dev Notes

### Architecture Guardrails (reuse-map compliance)

**NO PARALLEL OVERRUN STATE:**
```swift
// ✅ Correct — overrun derived from existing SessionTimerEngine state
overrunSeconds = max(0, Int(clock.now.timeIntervalSince(session.scheduledEndTime ?? clock.now)))

// ❌ Wrong — do NOT add a separate isOverrun flag or OverrunMonitor class
var isSessionOverrunning: Bool  // FORBIDDEN — urgencyLevel == .overtime already covers this
```

**O4 ROUTE FROM DONE BUTTON — NOT A NEW SCREEN:**
```swift
// ✅ Correct — Done button decides which path based on overrunSeconds
Button("Done") {
    if viewModel.shouldShowCascadePrompt {
        showCascadePrompt = true      // O4 path
    } else {
        // W4.2 direct endSession path
    }
}

// ❌ Wrong — separate "Cascade" button
Button("Cascade") { ... }   // Organizer should not see two separate buttons
```

**CASCADE RESULT = SCHEDULED TIMES UPDATE ON CACKEDSESSION:**
```swift
// ✅ Correct — cascade flows through applyServerState
// Server returns updated scheduledStartTime/scheduledEndTime for downstream sessions
// EventDataController.applyServerState writes them to CachedSession
// LiveCountdownViewModel.refreshState() recalculates from new CachedSession.scheduledEndTime automatically

// ❌ Wrong — cascade state stored separately
var cascadedMinutes: Int  // FORBIDDEN — no cascade state variable on client
var isCascadeApplied: Bool  // FORBIDDEN
```

**ABSORB IN BREAK = 0-MINUTE CASCADE:**
```swift
// ✅ Correct — absorb is extendSession with 0 minutes
webSocketService.sendAction(.extendSession(sessionSlug: slug, minutes: 0))

// The server treats cascadeMinutes == 0 as "end session only, no downstream shift"
// The break after the session absorbs the overrun naturally (break duration effectively shrinks)
```

**W4.2 O6 TRANSITION FIRES AUTOMATICALLY AFTER CASCADE:**
```swift
// W4.3 does NOT need to wire O6 separately.
// After cascade: server broadcasts SESSION_ENDED (or SCHEDULE_CASCADED) which sets
// WebSocketService.sessionEndedEvent → W4.2's .onChange handler fires → showTransition = true
// O6 appears exactly as it does for non-cascade session end.
```

### O4 Overrun Amount Display

The cascade prompt shows the overrun in whole minutes:
```swift
// CascadePromptView header — integer overrun minutes
Text("+\(overrunMinutes) min over")
    .font(.system(size: 20, weight: .bold, design: .monospaced))
    .foregroundStyle(.red)
```
`overrunMinutes` passed from `viewModel.delayImpactMinutes` (ceil of overrunSeconds/60). This gives the organizer a clean integer they can communicate to the room ("We ran 4 minutes over, shifting by 5").

### Cascade Broadcast JSON Shape (Server → Watch)

After cascade, server broadcasts:
```json
{
  "type": "STATE_UPDATE",
  "trigger": "SCHEDULE_CASCADED",
  "sessionSlug": "cloud-native-pitfalls",
  "initiatedBy": "marco.organizer",
  "eventCode": "BATbern56",
  "sessions": [
    {
      "sessionSlug": "cloud-native-pitfalls",
      "status": "COMPLETED",
      "actualEndTime": "2026-02-14T18:49:00Z",
      "overrunMinutes": 4,
      "completedBy": "marco.organizer",
      "newScheduledStartTime": "2026-02-14T18:00:00Z",
      "newScheduledEndTime": "2026-02-14T18:45:00Z"
    },
    {
      "sessionSlug": "microservices-mistakes",
      "status": "SCHEDULED",
      "actualEndTime": null,
      "overrunMinutes": 0,
      "completedBy": null,
      "newScheduledStartTime": "2026-02-14T18:54:00Z",
      "newScheduledEndTime": "2026-02-14T19:39:00Z"
    }
  ],
  "connectedOrganizers": [...],
  "serverTimestamp": "2026-02-14T18:49:01Z"
}
```

Watch receives this → `applyServerState()`:
- Session "cloud-native-pitfalls": `completedByUsername = "marco.organizer"`, `actualEndTime = ...`, `overrunMinutes = 4`
- Session "microservices-mistakes": `scheduledStartTime = 18:54`, `scheduledEndTime = 19:39` (shifted +5 min from 18:49 / 19:34)
- `LiveCountdownViewModel.refreshState()` runs → new `activeSession` is "break" or next talk → countdown resets to new `scheduledEndTime`
- W4.2 `sessionEndedEvent` set → O6 fires with new next session

### WatchAction Encoding for Cascade

`WatchAction.extendSession(sessionSlug:minutes:)` is defined in `WebSocketClientProtocol.swift` line 30. The concrete `WebSocketClient` (W4.1 Task 1.6) encodes it as:
```json
{
  "type": "EXTEND_SESSION",
  "sessionSlug": "cloud-native-pitfalls",
  "cascadeMinutes": 5
}
```

`WatchActionMessage.java` must accept `type`, `sessionSlug`, and `cascadeMinutes` fields.

### Dependency on W4.2

W4.3 has a hard dependency on W4.2:
- `LiveCountdownViewModel.canMarkDone` (Task 1.1 in W4.2) must exist — W4.3 uses it in `shouldShowCascadePrompt`
- `WebSocketService.sendAction(_:)` (Task 5.1 in W4.2) must exist — W4.3 uses it to send `extendSession`
- `WebSocketService.sessionEndedEvent` (Task 5.2 in W4.2) must exist — cascade ends the session, O6 fires automatically via this signal
- `WatchSessionService.java` (Task 7 in W4.2) must exist — W4.3 adds `cascadeSession` to the same service

Confirm all W4.2 prerequisites are implemented before starting W4.3.

### Project Structure Notes

```
New files (watchOS):
apps/BATbern-watch/BATbern-watch Watch App/Views/Organizer/CascadePromptView.swift

Modified files (watchOS):
apps/BATbern-watch/BATbern-watch Watch App/Protocols/WebSocketClientProtocol.swift
  (add SessionStateUpdate.newScheduledStartTime/newScheduledEndTime, add .scheduleCascaded enum case)
apps/BATbern-watch/BATbern-watch Watch App/Data/EventDataController.swift
  (applyServerState: apply new scheduled times)
apps/BATbern-watch/BATbern-watch Watch App/Data/WebSocketService.swift
  (add sessionCascadedEvent signal + .scheduleCascaded handling in consumer)
apps/BATbern-watch/BATbern-watch Watch App/ViewModels/LiveCountdownViewModel.swift
  (add overrunSeconds, shouldShowCascadePrompt, delayImpactMinutes)
apps/BATbern-watch/BATbern-watch Watch App/Views/Organizer/LiveCountdownView.swift
  (modify Done button for O4 routing, add delay impact indicator, present CascadePromptView)

New backend files:
(none — WatchSessionService.java exists from W4.2)

Modified backend files:
services/event-management-service/src/main/java/ch/batbern/events/watch/WatchSessionService.java
  (add cascadeSession() method)
services/event-management-service/src/main/java/ch/batbern/events/watch/WatchWebSocketController.java
  (add EXTEND_SESSION dispatch in handleAction)
services/event-management-service/src/main/java/ch/batbern/events/watch/dto/WatchActionMessage.java
  (add cascadeMinutes field)
services/event-management-service/src/main/java/ch/batbern/events/watch/dto/SessionStateUpdateDto.java
  (add newScheduledStartTime, newScheduledEndTime fields)
services/event-management-service/src/main/java/ch/batbern/events/watch/WatchPresenceService.java
  (ensure buildStateUpdate includes scheduledStartTime/scheduledEndTime in session DTOs)
services/event-management-service/src/main/java/ch/batbern/events/domain/SessionRepository.java
  (add findByEventCodeAndScheduledStartTimeAfterOrderByScheduledStartTime)

New test files:
apps/BATbern-watch/BATbern-watch Watch AppTests/Views/CascadePromptViewTests.swift
services/event-management-service/src/test/java/ch/batbern/events/watch/WatchCascadeIntegrationTest.java
```

### Testing Standards

- watchOS: Swift Testing (`@Test`, `#expect`) — pure unit tests, no simulator needed
- Backend: JUnit 5 + Testcontainers PostgreSQL (extends `AbstractIntegrationTest`) — NEVER H2
- `MockWebSocketClient.emit()` is the primary driver for watchOS WebSocket cascade tests
- Run watchOS tests: `xcodebuild test -scheme "BATbern-watch Watch App" -destination 'platform=watchOS Simulator,name=Apple Watch Series 9 (45mm)'`

### Previous Story Intelligence (W4.1, W4.2)

- **W4.1 WebSocket infrastructure required**: `WebSocketClient`, `WebSocketService`, `EventDataController.applyServerState` must be fully operational before starting W4.3.
- **W4.2 Done button required**: `canMarkDone`, `WebSocketService.sendAction(_:)`, `sessionEndedEvent` from W4.2 are prerequisites. W4.3 modifies the Done button's routing logic — it does NOT replace it.
- **JWT refresh anti-pattern**: Never call `authManager.refreshJWT()` from `WebSocketService` (see MEMORY.md and W4.1 dev notes).
- **`MockWebSocketClient`** is at `Tests/Mocks/MockWebSocketClient.swift`. Use `emit(EventStateMessage(type: .scheduleCascaded, ...))` to simulate cascade broadcasts in tests.
- **D1 complete** (commit `5c49e211`): `WatchHapticService.schedule()` fires correctly. W4.3 uses `play(.actionConfirm)` (not `schedule`) — no D1 dependency.
- **`TestDataFactory`** at `Tests/Factories/TestDataFactory.swift` — use for building test sessions with `scheduledEndTime` in the past to test overrun state.

### References

- [Source: docs/watch-app/epic-4-reuse-map.md#Area-4] — WatchAction.extendSession mandate, no cascade state variable, CachedSession field population
- [Source: docs/watch-app/epic-4-reuse-map.md#Area-1] — overrun detection from urgencyLevel, no parallel overrun flag
- [Source: apps/BATbern-watch/BATbern-watch Watch App/Protocols/WebSocketClientProtocol.swift:30] — `WatchAction.extendSession(sessionSlug:minutes:)`
- [Source: apps/BATbern-watch/BATbern-watch Watch App/Protocols/WebSocketClientProtocol.swift:44–50] — `EventStateMessageType` enum
- [Source: apps/BATbern-watch/BATbern-watch Watch App/Protocols/WebSocketClientProtocol.swift:88–102] — `WatchStateUpdate`, `SessionStateUpdate` structs (to be extended in Task 0)
- [Source: apps/BATbern-watch/BATbern-watch Watch App/Data/EventDataController.swift:121–137] — `applyServerState` (to be extended in Task 0.3)
- [Source: apps/BATbern-watch/BATbern-watch Watch App/Data/WebSocketService.swift] — W4.1 service; `sendAction` confirmed from W4.2
- [Source: apps/BATbern-watch/BATbern-watch Watch App/Models/HapticAlert.swift] — `HapticAlert.actionConfirm`
- [Source: apps/BATbern-watch/BATbern-watch Watch App/Models/CachedSession.swift:22–23] — `overrunMinutes`, `scheduledEndTime`
- [Source: _bmad-output/implementation-artifacts/w4-1-websocket-real-time-infrastructure.md] — W4.1 WebSocket patterns
- [Source: _bmad-output/implementation-artifacts/w4-2-session-advance-transition.md] — W4.2 Done button, sessionEndedEvent, WatchSessionService
- [Source: docs/watch-app/architecture.md#API-Communication-Patterns] — STOMP action schema, cascade broadcast shape
- [Source: docs/watch-app/epics.md#W4.3] — Story ACs and architectural constraints

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

### File List
