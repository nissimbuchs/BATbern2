# Story W4.2: Session Advance & Transition

Status: ready-for-dev

---

## Design Direction (A2 — Gate Requirement)

**Epic 4 core principle:** One state, one path. All server-driven state flows through `EventDataController` → `EventStateManager` → `LiveCountdownViewModel`. No parallel state object is permitted.

**W4.2 visible changes:**
- `LiveCountdownView` (O3) gains a **"Done" button** that appears when `urgencyLevel == .overtime` (session at or past 0:00). Tapping it sends `WatchAction.endSession(sessionSlug:)` via `WebSocketService`.
- After Done is confirmed by the server (SESSION_ENDED broadcast received), the organizer sees either:
  - **O6 `SessionTransitionView`** — full-screen view with next speaker portrait, name, and talk title (when next session is a talk)
  - **O5 routing** (W4.4) — when next session is a break (done via `findNextSession()` already excluding breaks)
- **O7 `SessionListView`** shows `SessionBadgeStatus.completed` with "Done by [name]" annotation when `CachedSession.completedByUsername` is populated.
- A new shared component `NextSessionPeekView` (`.compact` / `.prominent` modes) replaces the inline `nextSessionCard()` in `LiveCountdownView` — prerequisite refactor before Dev builds O6.

**Implementation style:** User-visible action story — the Done button is the only new interaction. O6 is a passive full-screen display of existing state. No new ViewModels, no new state managers.

---

## Pre-Implementation Review (A1 — Gate Requirement)

**Before writing a single line of code, Dev must confirm the following from `docs/watch-app/epic-4-reuse-map.md`:**

| Check | Mandate | Confirmed |
|---|---|---|
| Area 1 | `LiveCountdownViewModel.nextSession` (line 34) is the ONLY source for O6 next-speaker data | [ ] |
| Area 1 | `findNextSession()` (lines 182–188) is NOT reimplemented anywhere | [ ] |
| Area 1 | `nextSessionCard()` (lines 152–178) extracted into `NextSessionPeekView` BEFORE O6 is built | [ ] |
| Area 1 | Portrait loading uses `PortraitCache.shared` (same as `loadPortrait()`, lines 196–207) | [ ] |
| Area 4 | `WatchAction.endSession(sessionSlug:)` used as-is — no new action types | [ ] |
| Area 4 | Server SESSION_ENDED response flows through `EventDataController.applyServerState()` only | [ ] |
| Area 4 | `CachedSession.completedByUsername` + `actualEndTime` populated from server response — no new CachedSession fields | [ ] |
| Area 4 | No `TransitionViewModel`, no `SessionAdvanceController`, no parallel session state | [ ] |
| W4.1 | `WebSocketService.sendAction(_:)` confirmed available (W4.1 prerequisite) | [ ] |
| W4.1 | `HapticAlert.actionConfirm` used for Done-tap success haptic — no new haptic path | [ ] |

---

## Story

As an organizer,
I want to tap "Done" to advance the schedule for all organizer watches and see the next speaker info,
so that transitions are seamless.

## Acceptance Criteria

1. **AC1 — Done Button at/past 0:00**: Given a session is at or past 0:00 (i.e., `LiveCountdownViewModel.urgencyLevel == .overtime`), When I view O3 (`LiveCountdownView`), Then a "Done" button appears in the action area. The button does NOT appear when time remains.

2. **AC2 — Advance + O6 Transition View**: Given I tap "Done" and the session ended on time (no overrun or overrun handled), When the server confirms the action with a `SESSION_ENDED` broadcast and the next session is a talk (not a break), Then all watches advance to the next session and I see the O6 `SessionTransitionView` with:
   - Prominent next speaker portrait (loaded via `PortraitCache.shared`)
   - Full speaker name
   - Talk title
   The O6 view auto-dismisses after 5 seconds, returning to O3 for the new active session.

3. **AC3 — Success Haptic**: Given I tap "Done", When the tap registers (before server confirmation), Then I feel `HapticAlert.actionConfirm` immediately as optimistic feedback.

4. **AC4 — Idempotent "Completed by [name]"**: Given another organizer already tapped "Done" first, When the `SESSION_ENDED` broadcast arrives on my Watch, Then:
   - The Done button is no longer visible (session has advanced)
   - In O7 (`SessionListView`), the completed session shows "Done by [firstName]" using `CachedSession.completedByUsername`
   - No duplicate action is possible (button gone after first broadcast)

5. **AC5 — Break Routing**: Given a break follows the completed session, When Done is tapped and confirmed, Then the view does NOT show O6 — it transitions naturally to what W4.4 will implement (O5). In this story, the routing condition `nextSession == nil` (because `findNextSession()` excludes breaks) means O6 is skipped and O3 shows idle state.

---

## Tasks / Subtasks

### Prerequisite: Extract `NextSessionPeekView` (Required Before O6)

- [ ] **Task 0: Extract `nextSessionCard()` into shared `NextSessionPeekView`** (Blocks: Task 3)
  - [ ] 0.1 Create `apps/BATbern-watch/BATbern-watch Watch App/Views/Shared/NextSessionPeekView.swift`
  - [ ] 0.2 Define `enum NextSessionPeekStyle { case compact, prominent }` in the same file
  - [ ] 0.3 Move the body of `nextSessionCard(next:)` from `LiveCountdownView.swift` (lines 152–178) into `NextSessionPeekView` as the `.compact` layout:
    - `.compact`: current dimmed peek card — "NEXT" label, speaker first-name-or-title, HH:mm start time, 0.6 opacity
    - `.prominent`: full-screen layout for O6 — large portrait circle (40pt), full speaker name, talk title, no dimming
  - [ ] 0.4 Portrait in `.prominent` mode: use `PortraitCache.shared` with the same `loadPortrait()` async pattern as `LiveCountdownView.loadPortrait()` (lines 196–207) — inject `url` from `WatchSession.speakers.first?.profilePictureUrl`
  - [ ] 0.5 In `LiveCountdownView.swift`, replace the inline `nextSessionCard(next: next)` call (line 43) with `NextSessionPeekView(session: next, style: .compact)`
  - [ ] 0.6 Unit tests in `NextSessionPeekViewTests.swift`:
    - `.compact` renders speaker name + start time string; hidden at opacity 0.6
    - `.prominent` renders portrait placeholder when no URL; renders name + title
    - Both styles receive identical `WatchSession` input (no separate data fetching)

---

### watchOS — "Done" Button + O6 Transition

- [ ] **Task 1: `LiveCountdownViewModel.canMarkDone` computed property** (AC: 1)
  - [ ] 1.1 Add `private(set) var canMarkDone: Bool = false` to `LiveCountdownViewModel.swift`
  - [ ] 1.2 Set `canMarkDone = (urgencyLevel == .overtime || engine.remainingSeconds <= 0)` at the end of `refreshState()`, after `urgencyLevel` is assigned
  - [ ] 1.3 Unit tests in `LiveCountdownViewModelTests.swift`:
    - `canMarkDone` is `false` when `remainingSeconds > 0`
    - `canMarkDone` is `true` when `engine.urgencyLevel == .overtime` (advance clock past `endTime`)
    - `canMarkDone` becomes `false` again when session advances to new session (reset on session change)

- [ ] **Task 2: "Done" button in `LiveCountdownView`** (AC: 1, 3)
  - [ ] 2.1 In `LiveCountdownView.swift`, add a `@Environment(WebSocketService.self) private var webSocketService` reference
  - [ ] 2.2 Add `@State private var showTransition: Bool = false` and `@State private var isSendingDone: Bool = false` to `LiveCountdownView`
  - [ ] 2.3 In `countdownContent`, add a `doneButton` view below the session cards, shown only when `viewModel.canMarkDone`:
    ```swift
    if viewModel.canMarkDone {
        doneButton
    }
    ```
  - [ ] 2.4 Implement `doneButton` as a `Button("Done")` styled with `.borderedProminent` tint `.teal`:
    - On tap: call `hapticService.play(.actionConfirm)` immediately (optimistic feedback)
    - Then: `isSendingDone = true`, call `webSocketService.sendAction(.endSession(sessionSlug: sessionSlug))` via `Task { }` — where `sessionSlug` comes from `viewModel.activeSession?.slug`
    - Reset `isSendingDone = false` after send completes (success or failure)
    - Disabled when `isSendingDone == true` (prevent double-tap)
  - [ ] 2.5 Note: `hapticService` reference — `LiveCountdownView` currently does not hold a `hapticService` reference (it's internal to `LiveCountdownViewModel`). Add `@State private var hapticService: HapticServiceProtocol = WatchHapticService()` to the view, injectable for tests. Alternatively, expose a `triggerActionConfirm()` method on `LiveCountdownViewModel` that calls its internal `hapticService.play(.actionConfirm)` — the latter is preferred (keeps haptic logic in ViewModel).
  - [ ] 2.6 Unit tests in `LiveCountdownViewTests.swift`:
    - Done button visible when `canMarkDone == true`
    - Done button hidden when `canMarkDone == false`
    - `sendAction(.endSession)` called on button tap with correct `sessionSlug`

- [ ] **Task 3: O6 `SessionTransitionView`** (AC: 2)
  - [ ] 3.1 Create `apps/BATbern-watch/BATbern-watch Watch App/Views/Organizer/SessionTransitionView.swift`
  - [ ] 3.2 Inputs: `nextSession: WatchSession` — read from `viewModel.nextSession` (the same `@Observable` instance held by `LiveCountdownView`)
  - [ ] 3.3 Layout: `NextSessionPeekView(session: nextSession, style: .prominent)` centered vertically, with "UP NEXT" label at top and a 5-second auto-dismiss countdown (thin progress bar at bottom)
  - [ ] 3.4 Auto-dismiss after 5 seconds: use `.task { try? await Task.sleep(nanoseconds: 5_000_000_000); showTransition = false }` in the parent
  - [ ] 3.5 Trigger: in `LiveCountdownView`, observe `webSocketService.lastCompletedSessionSlug` (see Task 5.2); when it changes and `viewModel.nextSession != nil`, set `showTransition = true`; present `SessionTransitionView` as `.sheet` or full-screen overlay
  - [ ] 3.6 AC5 (break routing): if `viewModel.nextSession == nil` when Done confirmed, do NOT show O6 — leave `showTransition = false`
  - [ ] 3.7 Unit tests in `SessionTransitionViewTests.swift`:
    - View renders `NextSessionPeekView` in `.prominent` style with the correct `nextSession`
    - View receives auto-dismiss signal after 5s (use `MockClock` pattern for async timing test)

- [ ] **Task 4: O7 "Completed by" attribution** (AC: 4)
  - [ ] 4.1 `SessionBadgeStatus.completed.label` already returns `"Done"` (`SessionCardView.swift` line 19) — verify this also renders `completedByUsername` in the O7 view
  - [ ] 4.2 In `SessionListView.swift`, where `showStatusBadge: true` is passed to `SessionCardView`: ensure the session's `completedByUsername` is displayed below the "Done" badge as a secondary caption: `"by \(session.completedByUsername ?? "")"` when not nil
  - [ ] 4.3 This is a conditional addition to `SessionCardView.swift`'s badge section — only shown when `session.completedByUsername != nil && badgeStatus == .completed`
  - [ ] 4.4 Unit tests in `SessionCardViewTests.swift`:
    - Badge shows "Done" + "by Marco" when `completedByUsername = "Marco"`
    - Badge shows only "Done" when `completedByUsername == nil`

---

### watchOS — WebSocket Send + Transition Trigger

- [ ] **Task 5: `WebSocketService` — send endSession action + expose completion signal** (AC: 2, 3)
  - [ ] 5.1 `WebSocketService.sendAction(_ action: WatchAction)` (from W4.1 Task 3.6): verify this method exists and passes `WatchAction.endSession(sessionSlug:)` to `webSocketClient.sendAction(_:)` correctly
  - [ ] 5.2 Add `private(set) var lastCompletedSessionSlug: String? = nil` to `WebSocketService`. When `applyServerState` receives a `SESSION_ENDED` message (from `EventDataController` via the state update), set this to the `sessionSlug` from the message. This signal is observed by `LiveCountdownView` to trigger O6.
  - [ ] 5.3 Alternative cleaner approach: add a `@Published var sessionEndedEvent: SessionEndedEvent? = nil` (where `SessionEndedEvent` is a small struct: `slug`, `completedBy`, `timestamp`) — reset to nil after LiveCountdownView consumes it. This avoids a persistent `lastCompletedSessionSlug` that leaks across session changes.
  - [ ] 5.4 Unit tests: `WebSocketServiceTests.swift` — emit `SESSION_ENDED` from `MockWebSocketClient`, verify `sessionEndedEvent` is set; verify it flows through `EventDataController.applyServerState` correctly

---

### Backend — Session End Handler + Broadcast

- [ ] **Task 6: Implement `endSession` in `WatchWebSocketController.handleAction()`** (AC: 1, 2, 4)
  - [ ] 6.1 In `WatchWebSocketController.java`, replace the W4.1 stub's action handling with a dispatch:
    ```java
    @MessageMapping("/watch/events/{eventCode}/action")
    public void handleAction(
        @DestinationVariable String eventCode,
        @Payload WatchActionMessage action,
        Principal principal
    ) {
        switch (action.getType()) {
            case "END_SESSION" -> watchSessionService.endSession(
                eventCode, action.getSessionSlug(), principal.getName()
            );
            default -> log.warn("Unknown action type: {}", action.getType());
        }
    }
    ```
  - [ ] 6.2 Add `sessionSlug` field to `WatchActionMessage.java` DTO (it may already exist from W4.1 — verify)

- [ ] **Task 7: Create `WatchSessionService.endSession()`** (AC: 2, 4)
  - [ ] 7.1 Create `ch.batbern.events.watch.WatchSessionService` (Spring `@Service`)
  - [ ] 7.2 `endSession(String eventCode, String sessionSlug, String completedByUsername)`:
    - Load session from `SessionRepository.findByEventCodeAndSlug(eventCode, sessionSlug)` — throw `SessionNotFoundException` if not found
    - Guard: if `session.getCompletedByUsername() != null`, the session is already completed — log idempotency skip and re-broadcast current state (AC4)
    - Set: `session.setActualEndTime(Instant.now())`
    - Calculate: `overrunMinutes = max(0, ChronoUnit.MINUTES.between(session.getScheduledEndTime(), session.getActualEndTime()))`
    - Set: `session.setOverrunMinutes((int) overrunMinutes)`, `session.setCompletedByUsername(completedByUsername)`
    - Save: `sessionRepository.save(session)`
    - Broadcast: call `watchPresenceService.buildAndBroadcastState(eventCode, "SESSION_ENDED", sessionSlug, completedByUsername)`
  - [ ] 7.3 `WatchPresenceService.buildAndBroadcastState()` extension: add `trigger` and `sessionSlug` + `initiatedBy` fields to `WatchStateUpdateMessage` so the Watch can identify which session was completed and who did it
  - [ ] 7.4 Integration test `WatchSessionServiceTest` extends `AbstractIntegrationTest`:
    - `endSession()` sets `actualEndTime`, `completedByUsername`, `overrunMinutes` on the session
    - Second call with same slug is idempotent (no second write, same result)
    - Session not found → `SessionNotFoundException` thrown

- [ ] **Task 8: `WatchStateUpdateMessage` — SESSION_ENDED trigger fields** (AC: 2, 4)
  - [ ] 8.1 Add to `WatchStateUpdateMessage.java`:
    ```java
    private String trigger;         // "SESSION_ENDED", "ORGANIZER_JOINED", etc.
    private String sessionSlug;     // slug of the session that triggered the update
    private String initiatedBy;     // username of organizer who took the action (firstName only for display)
    ```
  - [ ] 8.2 On the Watch side (`EventDataController.applyServerState()`): map `initiatedBy` into `CachedSession.completedByUsername` (already in `WatchStateUpdate` struct from W4.1 — confirm field exists; if `completedBy` is the field name in `SessionStateUpdate`, verify it's mapped correctly)

- [ ] **Task 9: `SessionRepository` — findByEventCodeAndSlug** (AC: 2)
  - [ ] 9.1 Add `Optional<Session> findByEventCodeAndSlug(String eventCode, String slug)` to `SessionRepository.java` if not already present (check existing methods first — W2.4 arrival tracking may have added event lookups)
  - [ ] 9.2 Integration test: verify correct session returned by event code + slug

---

### watchOS — Wire Up in App

- [ ] **Task 10: Wire `WebSocketService` into `LiveCountdownView`** (AC: 1, 2, 3)
  - [ ] 10.1 Add `@Environment(WebSocketService.self) private var webSocketService` to `LiveCountdownView` (already done in W4.1 Task 3.10–3.11 if W4.1 is implemented)
  - [ ] 10.2 Add `.onChange(of: webSocketService.sessionEndedEvent)` in `LiveCountdownView.body`:
    - When `sessionEndedEvent != nil` and `viewModel.nextSession != nil`: set `showTransition = true`
    - When `sessionEndedEvent != nil` and `viewModel.nextSession == nil`: no O6 (AC5 — break follows)
    - After handling: reset `webSocketService.sessionEndedEvent = nil`
  - [ ] 10.3 Present `SessionTransitionView` via `.fullScreenCover(isPresented: $showTransition)`:
    ```swift
    .fullScreenCover(isPresented: $showTransition) {
        if let next = viewModel.nextSession {
            SessionTransitionView(nextSession: next, onDismiss: { showTransition = false })
        }
    }
    ```
  - [ ] 10.4 Integration test (mock-based): inject `MockWebSocketClient`, emit `SESSION_ENDED`, verify `showTransition` becomes `true` when `nextSession != nil`

---

## Dev Notes

### Architecture Guardrails (reuse-map compliance)

**REQUIRED REFACTOR FIRST — `NextSessionPeekView`:**
```swift
// ❌ Current state (W3.x) — inline in LiveCountdownView.swift lines 152–178:
private func nextSessionCard(next: WatchSession) -> some View { ... }

// ✅ W4.2 — extracted shared component:
// apps/.../Views/Shared/NextSessionPeekView.swift
struct NextSessionPeekView: View {
    let session: WatchSession
    let style: NextSessionPeekStyle  // .compact (O3 peek) | .prominent (O6 full-screen)
    // ...
}

// Usage in LiveCountdownView (compact):
NextSessionPeekView(session: next, style: .compact)

// Usage in SessionTransitionView (prominent):
NextSessionPeekView(session: nextSession, style: .prominent)
```

**NO NEW NEXT-SESSION DISCOVERY:**
```swift
// ✅ Correct — O6 reads from the shared ViewModel
struct SessionTransitionView: View {
    let nextSession: WatchSession  // Passed in from LiveCountdownView's viewModel.nextSession
    // No ViewModel, no findNextSession() call here
}

// ❌ Wrong — do NOT create a TransitionViewModel
class TransitionViewModel {
    func loadNextSession() { ... }  // FORBIDDEN — duplicates findNextSession()
}
```

**DONE BUTTON ONLY WHEN OVERTIME:**
```swift
// ✅ Correct — driven by LiveCountdownViewModel.canMarkDone
if viewModel.canMarkDone {
    doneButton
}

// ❌ Wrong — checking time directly in the view
if viewModel.formattedTime == "00:00" { ... }  // Race condition, fragile
```

**SERVER-AUTHORITATIVE TRANSITION TRIGGER:**
```swift
// ✅ Correct — O6 appears ONLY when SESSION_ENDED broadcast received
.onChange(of: webSocketService.sessionEndedEvent) { _, event in
    guard event != nil, viewModel.nextSession != nil else { return }
    showTransition = true
    webSocketService.sessionEndedEvent = nil
}

// ❌ Wrong — showing O6 immediately on button tap (before server confirmation)
Button("Done") {
    showTransition = true  // NOT server-confirmed yet — other watch may not advance
}
```

**JWT REFRESH RULE (from MEMORY.md — carry forward from W4.1):**
```swift
// WebSocketService NEVER calls authManager.refreshJWT() on any error
// JWT refresh is handled by AuthManager's refresh timer only
// This prevents the 401 → refreshJWT → onChange → connect → 401 loop
```

### "Done" Button Appearance Logic

From `SessionTimerEngine`: `urgencyLevel == .overtime` means `session.endTime < clock.now` (i.e., session has ended). The Done button maps to this state:

```swift
// LiveCountdownViewModel.refreshState() addition:
canMarkDone = urgencyLevel == .overtime
```

The button is NOT shown during `.critical` (2 min remaining). It only appears AFTER 0:00.

### Backend SESSION_ENDED Broadcast Shape

```json
{
  "type": "STATE_UPDATE",
  "trigger": "SESSION_ENDED",
  "sessionSlug": "cloud-native-pitfalls",
  "initiatedBy": "marco.organizer",
  "eventCode": "BATbern56",
  "sessions": [
    {
      "sessionSlug": "cloud-native-pitfalls",
      "status": "COMPLETED",
      "actualStartTime": "2026-02-14T18:00:00Z",
      "actualEndTime": "2026-02-14T18:47:00Z",
      "overrunMinutes": 2,
      "completedBy": "marco.organizer"
    },
    {
      "sessionSlug": "microservices-mistakes",
      "status": "SCHEDULED",
      "actualStartTime": null,
      "actualEndTime": null,
      "overrunMinutes": 0,
      "completedBy": null
    }
  ],
  "connectedOrganizers": [...],
  "serverTimestamp": "2026-02-14T18:47:00Z"
}
```

Watch receives this → `applyServerState()` updates `CachedSession.completedByUsername = "marco.organizer"` and `actualEndTime` for the completed session → `LiveCountdownViewModel.refreshState()` discovers the next session as the new `activeSession` → Done button disappears → O7 shows "Done by marco.organizer" badge.

### O7 Session Timeline Attribution Display

```swift
// SessionCardView.swift — badge section addition (AC4):
if showStatusBadge, let badgeStatus = SessionBadgeStatus.status(for: session, at: now) {
    VStack(alignment: .trailing, spacing: 1) {
        Text(badgeStatus.label)
            .font(.caption2)
            .foregroundStyle(badgeStatus.color)
        // W4.2 addition: completedBy attribution
        if badgeStatus == .completed, let completedBy = session.completedByUsername {
            Text("by \(completedBy)")
                .font(.system(size: 8))
                .foregroundStyle(.tertiary)
        }
    }
}
```

### CachedSession Fields (confirm before starting — from reuse-map Area 4)

These exist at `Models/CachedSession.swift`:
```swift
var actualStartTime: Date?          // [Source: CachedSession.swift:21]
var actualEndTime: Date?            // [Source: CachedSession.swift:22]
var overrunMinutes: Int?            // [Source: CachedSession.swift:23]
var completedByUsername: String?    // [Source: CachedSession.swift:24]
```

**W4.2 does NOT add new `CachedSession` columns** — it populates the ones that already exist via `EventDataController.applyServerState()`.

### WatchAction Encoding to Backend

`WatchAction.endSession(sessionSlug:)` is already defined in `WebSocketClientProtocol.swift` (line 28). The concrete `WebSocketClient` (W4.1 Task 1.6) encodes it as:
```json
{
  "type": "END_SESSION",
  "sessionSlug": "cloud-native-pitfalls"
}
```

The `WatchActionMessage` Java DTO must accept `type` and `sessionSlug` fields (Task 6.2).

### SessionListView O7 Integration

`SessionListView.swift` already passes `showStatusBadge: Bool` to `SessionCardView`. During a live event, the organizer zone's O7 (`SessionListView` with `showStatusBadge: true`) will automatically show the completed badge. Task 4 only adds the "by [name]" sub-caption — no structural changes to `SessionListView`.

### Project Structure Notes

```
New files (watchOS):
apps/BATbern-watch/BATbern-watch Watch App/Views/Shared/NextSessionPeekView.swift
apps/BATbern-watch/BATbern-watch Watch App/Views/Organizer/SessionTransitionView.swift

Modified files (watchOS):
apps/BATbern-watch/BATbern-watch Watch App/Views/Organizer/LiveCountdownView.swift
  (refactor nextSessionCard → NextSessionPeekView, add Done button, observe sessionEndedEvent)
apps/BATbern-watch/BATbern-watch Watch App/ViewModels/LiveCountdownViewModel.swift
  (add canMarkDone property)
apps/BATbern-watch/BATbern-watch Watch App/Views/Public/SessionCardView.swift
  (add "by [name]" sub-caption to .completed badge)

Modified files (watchOS — W4.1 prerequisites, confirm exist):
apps/BATbern-watch/BATbern-watch Watch App/Data/WebSocketService.swift
  (add sessionEndedEvent signal, verify sendAction exists)

New files (backend):
services/event-management-service/src/main/java/ch/batbern/events/watch/WatchSessionService.java

Modified files (backend):
services/event-management-service/src/main/java/ch/batbern/events/watch/WatchWebSocketController.java
  (implement endSession dispatch in handleAction)
services/event-management-service/src/main/java/ch/batbern/events/watch/WatchPresenceService.java
  (add trigger/sessionSlug/initiatedBy to broadcast)
services/event-management-service/src/main/java/ch/batbern/events/watch/dto/WatchStateUpdateMessage.java
  (add trigger, sessionSlug, initiatedBy fields)
services/event-management-service/src/main/java/ch/batbern/events/watch/dto/WatchActionMessage.java
  (confirm sessionSlug field present)
services/event-management-service/src/main/java/ch/batbern/events/domain/SessionRepository.java
  (add findByEventCodeAndSlug if missing)
```

### Testing Standards

- watchOS: Swift Testing (`@Test`, `#expect`) — pure unit tests, no simulator needed
- Backend: JUnit 5 + Testcontainers PostgreSQL (extends `AbstractIntegrationTest`) — NEVER H2
- `MockWebSocketClient.emit()` is the primary driver for watchOS WebSocket integration tests
- Run watchOS tests: `xcodebuild test -scheme "BATbern-watch Watch App" -destination 'platform=watchOS Simulator,name=Apple Watch Series 9 (45mm)'`

### Previous Story Intelligence (W4.1)

From W4.1 story and git context:
- **WebSocket infrastructure required**: W4.2 is blocked until W4.1 is implemented. Confirm `WebSocketService.swift` exists and `sendAction(_:)` works before starting Task 2.
- **`MockWebSocketClient`** is at `Tests/Mocks/MockWebSocketClient.swift` — use `emit()` to simulate `SESSION_ENDED` messages in tests.
- **`EventDataController.applyServerState(_:)`** was added in W4.1 Task 2. Verify the `completedByUsername` field in `SessionStateUpdate` struct is populated from the server message's `completedBy` field.
- **JWT refresh anti-pattern**: Never call `authManager.refreshJWT()` from within `WebSocketService` (causes infinite loop — see MEMORY.md).
- **D1 complete** (commit `5c49e211`): `WatchHapticService.schedule()` fires correctly. W4.2 uses `play(.actionConfirm)` (not `schedule`) — no D1 dependency.

### References

- [Source: docs/watch-app/epic-4-reuse-map.md#Area-1] — O6 data source mandate, NextSessionPeekView extraction
- [Source: docs/watch-app/epic-4-reuse-map.md#Area-4] — EventStateManager single source of truth, CachedSession field population
- [Source: apps/BATbern-watch/BATbern-watch Watch App/ViewModels/LiveCountdownViewModel.swift:34] — `nextSession: WatchSession?`
- [Source: apps/BATbern-watch/BATbern-watch Watch App/ViewModels/LiveCountdownViewModel.swift:182–188] — `findNextSession()` (must NOT be reimplemented)
- [Source: apps/BATbern-watch/BATbern-watch Watch App/Views/Organizer/LiveCountdownView.swift:152–178] — `nextSessionCard()` to be extracted
- [Source: apps/BATbern-watch/BATbern-watch Watch App/Views/Organizer/LiveCountdownView.swift:196–207] — `loadPortrait()` pattern for `PortraitCache.shared`
- [Source: apps/BATbern-watch/BATbern-watch Watch App/Protocols/WebSocketClientProtocol.swift:28] — `WatchAction.endSession(sessionSlug:)`
- [Source: apps/BATbern-watch/BATbern-watch Watch App/Models/HapticAlert.swift] — `HapticAlert.actionConfirm`
- [Source: apps/BATbern-watch/BATbern-watch Watch App/Views/Public/SessionCardView.swift:14–40] — `SessionBadgeStatus.completed`
- [Source: apps/BATbern-watch/BATbern-watch Watch App/Models/CachedSession.swift:22–24] — `actualEndTime`, `overrunMinutes`, `completedByUsername`
- [Source: services/event-management-service/src/main/java/ch/batbern/events/watch/WatchWebSocketController.java] — handleAction stub from W4.1
- [Source: _bmad-output/implementation-artifacts/w4-1-websocket-real-time-infrastructure.md] — W4.1 WebSocket infrastructure patterns

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

### File List
