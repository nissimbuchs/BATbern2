# Story W4.1: WebSocket Real-Time Infrastructure

Status: in-progress

---

## Design Direction (A2 — Gate Requirement)

**Epic 4 core principle:** One state, one path. All server-driven state flows through `EventDataController` → `EventStateManager` → `LiveCountdownViewModel`. No parallel state object is permitted.

**W4.1 visible changes:**
- `LiveCountdownView` gains a small `PresenceIndicatorView` alongside the existing `ConnectionStatusBar` (additive, not replacing)
- WebSocket connection/disconnection is silent from the organizer's perspective unless connectivity is lost (`.connectionLost` haptic + `ConnectionStatusBar` turns orange)
- No new screens. No navigation changes.

**Implementation style:** Infrastructure story — invisible to the organizer if working correctly. Errors surface only via `ConnectionStatusBar` (existing component).

---

## Pre-Implementation Review (A1 — Gate Requirement)

**Before writing a single line of code, Dev must confirm the following from `docs/watch-app/epic-4-reuse-map.md`:**

| Check | Mandate | Confirmed |
|---|---|---|
| Area 3 | `ConnectionStatusBar` is the ONLY visual for connectivity state — no new view | [x] |
| Area 3 | WebSocket disconnect feeds `EventDataController.isOffline` (one flag, not two) | [x] |
| Area 3 | `HapticAlert.connectionLost` + `WatchHapticService.play(.connectionLost)` used as-is | [x] |
| Area 3 | `PresenceIndicatorView` is ADDITIVE alongside `ConnectionStatusBar`, never a replacement | [x] |
| Area 4 | State updates from server flow through `EventDataController.applyServerState()` only | [x] |
| Area 4 | No `OrganizerStateManager`, no `LiveEventController`, no parallel session list | [x] |
| D1 | `WatchHapticService.schedule()` confirmed firing (commit `5c49e211`) | [x] |

---

## Story

As an organizer,
I want my Watch to maintain a real-time connection to the backend,
so that all state changes sync instantly across all organizer watches.

## Acceptance Criteria

1. **AC1 — STOMP Connection with JWT**: Given I join an event, When my Watch connects, Then a STOMP WebSocket connection is established with the current JWT in the STOMP CONNECT frame headers.

2. **AC2 — STATE_UPDATE Applied Within 3s**: Given I am connected, When the server broadcasts a `STATE_UPDATE`, Then my Watch receives it, calls `EventDataController.applyServerState()`, and the updated session state is visible in `LiveCountdownViewModel` within 3 seconds (NFR3).

3. **AC3 — Exponential Backoff Reconnection**: Given my WebSocket connection drops unexpectedly, When WiFi is still available, Then the client reconnects automatically with exponential backoff (2s, 4s, 8s, …, max 60s). `ConnectionStatusBar` shows `isOffline = true` during backoff. Once reconnected, `isOffline = false`.

4. **AC4 — Presence Indicator (FR20)**: Given multiple organizers are connected, When I view `LiveCountdownView` (O3), Then I see `PresenceIndicatorView` showing the count of connected organizers (e.g., "2 online"). The count updates in real time as organizers join or leave.

5. **AC5 — Transparent JWT Refresh + Reconnect**: Given my JWT expires mid-event (1-hour lifespan), When the `AuthManager` refresh timer fires (10 min before expiry), Then `WebSocketService` reconnects with the new JWT without any visible interruption to the organizer.

---

## Tasks / Subtasks

### watchOS — Client Infrastructure

- [x] **Task 1: Concrete `WebSocketClient` implementation** (AC: 1, 3)
  - [x] 1.1 Create `apps/BATbern-watch/BATbern-watch Watch App/Data/WebSocketClient.swift` implementing `WebSocketClientProtocol`
  - [x] 1.2 Connect via `URLSessionWebSocketTask` to `{baseURL}/api/v1/watch/ws` (raw WebSocket, no SockJS); send STOMP CONNECT frame with `{ "Authorization": "Bearer {jwt}" }` in headers
  - [x] 1.3 After CONNECT ACK: subscribe to `/topic/events/{eventCode}/state` and `/topic/events/{eventCode}/arrivals`; send join action to `/app/watch/events/{eventCode}/join`
  - [x] 1.4 `stateUpdates()` returns AsyncStream; yield decoded `EventStateMessage` on each STOMP MESSAGE from state topic
  - [x] 1.5 `arrivalUpdates()` returns AsyncStream; yield decoded `SpeakerArrivalMessage` from arrivals topic (existing protocol contract — no new types)
  - [x] 1.6 `sendAction(_:)` — STOMP SEND to `/app/watch/events/{eventCode}/action` with JSON-encoded `WatchAction`; encode each action case to a `WatchActionDto` record with `type`, `sessionSlug`, `minutes`, etc.
  - [x] 1.7 `disconnect()` — STOMP DISCONNECT frame + close WebSocket task; finish AsyncStream continuations
  - [x] 1.8 Exponential backoff: private `reconnectTask`; on unexpected STOMP ERROR or WebSocket close, schedule retry with `2^attempt` seconds (cap at 60s); reset counter on successful connect
  - [x] 1.9 Unit tests in `BATbern-watch Watch AppTests/Data/WebSocketClientTests.swift` using `MockWebSocketClient.emit()` to verify stream delivery

- [x] **Task 2: `EventDataController.applyServerState(_:)`** (AC: 2, Area 4 mandate)
  - [x] 2.1 Add `applyServerState(_ update: WatchStateUpdate)` method to `EventDataController.swift`; signature: `@MainActor func applyServerState(_ update: WatchStateUpdate)`
  - [x] 2.2 `WatchStateUpdate` is a new `Sendable` struct with `sessions: [SessionStateUpdate]`, `connectedOrganizers: [ConnectedOrganizer]`, `serverTimestamp: Date`; `SessionStateUpdate` carries `sessionSlug`, `status`, `actualStartTime?`, `actualEndTime?`, `overrunMinutes?`, `completedByUsername?`
  - [x] 2.3 For each `SessionStateUpdate`, find the matching `CachedSession` in `currentEvent.sessions` by `sessionSlug` and write: `state`, `actualStartTime`, `actualEndTime`, `overrunMinutes`, `completedByUsername` (use existing fields per reuse-map Area 4)
  - [x] 2.4 After applying: set `isOffline = false`, `lastSynced = clock.now`; call `try? modelContext.save()`
  - [x] 2.5 Unit tests: verify `currentEvent` mutations and `lastSynced` update via `MockModelContext` or in-memory SwiftData

- [x] **Task 3: `WebSocketService` — orchestration layer** (AC: 1, 2, 3, 4, 5)
  - [x] 3.1 Create `apps/BATbern-watch/BATbern-watch Watch App/Data/WebSocketService.swift`; `@Observable @MainActor final class WebSocketService`
  - [x] 3.2 Dependencies: `webSocketClient: WebSocketClientProtocol`, `eventDataController: EventDataController`, `authManager: AuthManagerProtocol`, `hapticService: HapticServiceProtocol`; injectable for tests
  - [x] 3.3 `connect(eventCode: String)`: guard `authManager.currentJWT != nil`; call `webSocketClient.connect(eventCode: eventCode, accessToken: jwt)`; start consuming `stateUpdates()` + `arrivalUpdates()` streams in background Tasks
  - [x] 3.4 `stateUpdates()` consumer: decode incoming `EventStateMessage` into `WatchStateUpdate`; call `eventDataController.applyServerState(update)`; update `connectedOrganizers` + `presenceCount` from broadcast
  - [x] 3.5 On disconnect (STOMP ERROR / WebSocket close): `eventDataController.isOffline = true`; `hapticService.play(.connectionLost)` (exactly once per disconnect event, not per retry); trigger reconnect loop
  - [x] 3.6 On successful reconnect: `eventDataController.isOffline = false`; `lastSynced` updated by `applyServerState`
  - [x] 3.7 JWT refresh integration: `WebSocketService` observes `authManager.currentJWT` via `withObservationTracking`; when JWT changes to a non-nil value while connected, call `webSocketClient.disconnect()` then `connect(eventCode:)` with new token — NEVER call `authManager.refreshJWT()` from within WebSocketService (infinite loop prevention, see MEMORY.md)
  - [x] 3.8 `@Published var presenceCount: Int = 0`; `@Published var connectedOrganizers: [ConnectedOrganizer] = []` — updated from state broadcasts
  - [x] 3.9 `disconnect()`: cancel stream consumer Tasks; call `webSocketClient.disconnect()`; reset presence state
  - [x] 3.10 Inject `WebSocketService` into `ContentView.swift` environment alongside `EventDataController`
  - [x] 3.11 `LiveCountdownView`: on `.task {}`, call `webSocketService.connect(eventCode: eventState.currentEvent?.eventCode ?? "")`; on `.onDisappear`, call `webSocketService.disconnect()`
  - [x] 3.12 Unit tests in `WebSocketServiceTests.swift`: use `MockWebSocketClient.emit()` + `MockHapticService` + verify `EventDataController.applyServerState` called; test disconnect haptic fires once; test reconnect does NOT call `refreshJWT`

- [x] **Task 4: `PresenceIndicatorView`** (AC: 4)
  - [x] 4.1 Create `apps/BATbern-watch/BATbern-watch Watch App/Views/Shared/PresenceIndicatorView.swift`
  - [x] 4.2 Input: `presenceCount: Int`; shows "N" with a person.2.fill SF Symbol when `presenceCount > 1`, "1" with person.fill when `presenceCount == 1`; hidden when `presenceCount == 0`
  - [x] 4.3 Styling: same 12pt semibold font as `ConnectionStatusBar`; color: `.teal` (online) / `.orange` (connection lost) — receives `isConnected: Bool` from `WebSocketService.webSocketClient.isConnected`
  - [x] 4.4 Add `PresenceIndicatorView` to `LiveCountdownView.swift` in the status bar HStack alongside existing `ConnectionStatusBar` — DO NOT remove or replace `ConnectionStatusBar`
  - [x] 4.5 Unit tests in `PresenceIndicatorViewTests.swift`: verify view renders correct count text and hides at 0

---

### Backend — STOMP Endpoint + Presence + State Broadcast

- [ ] **Task 5: Flyway migration V56 — watch session fields** (AC: 2)
  - [ ] 5.1 Create `V56__add_watch_session_fields.sql`:
    ```sql
    ALTER TABLE sessions ADD COLUMN actual_start_time TIMESTAMP;
    ALTER TABLE sessions ADD COLUMN actual_end_time TIMESTAMP;
    ALTER TABLE sessions ADD COLUMN overrun_minutes INTEGER DEFAULT 0;
    ALTER TABLE sessions ADD COLUMN completed_by_username VARCHAR(100);
    ```
  - [ ] 5.2 Add corresponding fields to `Session.java` entity (`@Column actualStartTime`, `actualEndTime`, `overrunMinutes`, `completedByUsername`)

- [ ] **Task 6: Raw WebSocket endpoint for Watch** (AC: 1)
  - [ ] 6.1 Add to `WebSocketConfig.java`:
    ```java
    registry.addEndpoint("/api/v1/watch/ws")
        .setAllowedOriginPatterns("*");  // Raw WebSocket, no SockJS — for Watch clients
    ```
  - [ ] 6.2 Existing `/ws` + SockJS endpoint unchanged (web frontend unaffected)

- [ ] **Task 7: `JwtStompInterceptor`** (AC: 1, 5)
  - [ ] 7.1 Create `ch.batbern.events.watch.JwtStompInterceptor` implementing `ChannelInterceptor`
  - [ ] 7.2 In `preSend()`: for `StompCommand.CONNECT`, extract `Authorization: Bearer {jwt}` header; validate JWT signature + expiry using the existing JWT validation bean (same decoder as REST security); reject with STOMP ERROR if invalid
  - [ ] 7.3 Extract username from JWT `sub` claim; wrap in `UsernamePasswordAuthenticationToken` with `ROLE_ORGANIZER`; set on `MessageHeaderAccessor` as principal — this becomes `Principal` in `@MessageMapping` handlers
  - [ ] 7.4 Register interceptor in `WebSocketConfig.java`: `@Override public void configureClientInboundChannel(ChannelRegistration reg) { reg.interceptors(jwtStompInterceptor); }`
  - [ ] 7.5 Integration test: `JwtStompInterceptorTest` — verify valid JWT passes, expired JWT rejected with STOMP ERROR frame

- [ ] **Task 8: `WatchPresenceService`** (AC: 4)
  - [ ] 8.1 Create `ch.batbern.events.watch.WatchPresenceService` (Spring `@Service`)
  - [ ] 8.2 In-memory `ConcurrentHashMap<String, Set<OrganizerPresence>> presenceByEvent` where `OrganizerPresence` is a record with `username`, `firstName`; thread-safe
  - [ ] 8.3 `joinEvent(eventCode, username, firstName)` — add to set; broadcast updated state via `SimpMessagingTemplate` to `/topic/events/{eventCode}/state`
  - [ ] 8.4 `leaveEvent(eventCode, username)` — remove from set; broadcast updated state
  - [ ] 8.5 `buildStateUpdate(eventCode)` — returns `WatchStateUpdateMessage` with full session list (from `SessionRepository`) + `connectedOrganizers` list; sessions include `actualStartTime`, `actualEndTime`, `overrunMinutes`, `completedByUsername` (now populated from V56 columns)
  - [ ] 8.6 Unit test: `WatchPresenceServiceTest` — join/leave updates presence map correctly; broadcast message shape is correct

- [ ] **Task 9: `WatchWebSocketController` extension** (AC: 1, 2, 4)
  - [ ] 9.1 Add join handler to `WatchWebSocketController.java`:
    ```java
    @MessageMapping("/watch/events/{eventCode}/join")
    public void handleJoin(@DestinationVariable String eventCode, Principal principal)
    ```
    → calls `presenceService.joinEvent(eventCode, principal.getName(), firstName)` → sends full state snapshot to the joining Watch via `SimpMessagingTemplate.convertAndSendToUser(username, "/queue/watch/state", stateUpdate)`
  - [ ] 9.2 Add leave handler:
    ```java
    @MessageMapping("/watch/events/{eventCode}/leave")
    public void handleLeave(@DestinationVariable String eventCode, Principal principal)
    ```
    → calls `presenceService.leaveEvent(eventCode, principal.getName())`
  - [ ] 9.3 Add action stub (W4.2+ will add actual dispatch):
    ```java
    @MessageMapping("/watch/events/{eventCode}/action")
    public void handleAction(@DestinationVariable String eventCode, @Payload WatchActionMessage action, Principal principal)
    ```
    → for W4.1: log the action + echo back an ACK; no state mutation yet (AC2 only tests receiving STATE_UPDATE, not triggering one)
  - [ ] 9.4 Integration test: connect with valid JWT → join → receive initial full state snapshot

- [ ] **Task 10: Disconnect cleanup via `SessionDisconnectEvent`** (AC: 3)
  - [ ] 10.1 Add `@EventListener(SessionDisconnectEvent.class)` in `WatchPresenceService` or a new `WatchWebSocketDisconnectListener`; on disconnect, call `leaveEvent(eventCode, username)` for all events this principal was in
  - [ ] 10.2 Unit test: simulate disconnect event → verify presence updated + state broadcast sent

---

### watchOS — Wire Concrete Client into ArrivalTracker

- [x] **Task 11: Wire `WebSocketClient` into `ArrivalTracker` (replaces 5-second REST polling fallback)** (AC: 2, 4)
  - [x] 11.1 In `BATbernWatchApp.init()`, after creating the concrete `WebSocketClient` instance (as part of Task 3), store it as a local `let webSocketClient = WebSocketClient()` before initialising `WebSocketService` and `ArrivalTracker`
  - [x] 11.2 Update the `ArrivalTracker` init in `BATbernWatchApp.init()` (currently line 62-65):
    ```swift
    // Before (W2.4 — polling fallback active):
    _arrivalTracker = State(wrappedValue: ArrivalTracker(
        authManager: auth,
        modelContext: container.mainContext
        // webSocketClient: nil  ← falls back to 5-second REST polling
    ))

    // After (W4.1 — real WebSocket):
    _arrivalTracker = State(wrappedValue: ArrivalTracker(
        authManager: auth,
        modelContext: container.mainContext,
        webSocketClient: webSocketClient   // ← concrete WebSocketClient instance
    ))
    ```
  - [x] 11.3 Pass the SAME concrete `WebSocketClient` instance to `WebSocketService` init so both share one connection (state stream → `WebSocketService`; arrival stream → `ArrivalTracker`)
  - [x] 11.4 Confirm `ArrivalTracker.startWebSocketListener()` code path is exercised when `webSocketClient != nil` (see `ArrivalTracker.swift` line ~195 comment: "No WebSocket client wired up yet (production WebSocket client pending W4)…")
  - [ ] 11.5 Unit test: `ArrivalTrackerTests` — inject `MockWebSocketClient`, call `startListening(eventCode:)`, emit `mockWebSocketClient.emitArrival(...)`, assert SwiftData speaker `arrived = true` is set and the REST-polling timer did NOT fire (verify `MockWebSocketClient.connectCallCount == 1`, not a polling loop)

---

## Dev Notes

### Architecture Guardrails (reuse-map compliance)

**SINGLE OFFLINE FLAG:**
```swift
// ✅ Correct — EventDataController.isOffline is the one flag
eventDataController.isOffline = true  // set from WebSocketService on disconnect

// ❌ Wrong — do NOT add wsIsOffline or presenceIsOffline alongside it
```

**STATE FLOWS THROUGH EventDataController:**
```swift
// ✅ Correct path for all server state
webSocketService.stateUpdates() → EventDataController.applyServerState() → currentEvent updated → EventStateManager recalculates → LiveCountdownViewModel refreshState()

// ❌ Wrong — never write session state anywhere else
var currentSession: CachedSession?  // DO NOT create this in WebSocketService
```

**PRESENCE INDICATOR IS ADDITIVE:**
```swift
// ✅ Correct — both views in the status bar area
HStack {
    ConnectionStatusBar(isOffline: dataController.isOffline, lastSynced: dataController.lastSynced)
    Spacer()
    PresenceIndicatorView(presenceCount: webSocketService.presenceCount, isConnected: webSocketService.isConnected)
}

// ❌ Wrong — replacing ConnectionStatusBar
PresenceIndicatorView(...)  // alone, no ConnectionStatusBar
```

**JWT REFRESH RULE (from MEMORY.md):**
```swift
// ✅ Correct — observe JWT change, reconnect with new token
.onChange(of: authManager.currentJWT) { _, newJWT in
    guard let jwt = newJWT, webSocketService.isConnected else { return }
    Task { await webSocketService.reconnect(with: jwt) }
}

// ❌ Wrong — NEVER call refreshJWT() from WebSocketService on 401
// This triggers currentJWT update → onChange → new connect → 401 → loop
```

### StompClientLib Integration

Library: `StompClientLib` via Swift Package Manager (already in Package.swift).

```swift
import StompClientLib

final class WebSocketClient: NSObject, WebSocketClientProtocol {
    private var socketClient = StompClientLib()

    func connect(eventCode: String, accessToken: String) async throws {
        let url = URL(string: "\(AppConfig.baseURL)/api/v1/watch/ws")!
        let headers = ["Authorization": "Bearer \(accessToken)"]
        socketClient.openSocketWithURLRequest(
            request: NSURLRequest(url: url),
            delegate: self,
            connectionHeaders: headers
        )
        // Store eventCode for subscription after CONNECTED
        self.pendingEventCode = eventCode
    }
}
```

After receiving STOMP CONNECTED callback, subscribe to topics and send JOIN action.

### Backend: WatchStateUpdateMessage JSON Shape

```json
{
  "type": "STATE_UPDATE",
  "trigger": "ORGANIZER_JOINED",
  "eventCode": "BATbern56",
  "sessions": [
    {
      "sessionSlug": "cloud-native-pitfalls",
      "title": "Cloud-Native Pitfalls",
      "sessionType": "presentation",
      "scheduledStartTime": "2026-02-14T18:00:00Z",
      "scheduledEndTime": "2026-02-14T18:45:00Z",
      "status": "SCHEDULED",
      "actualStartTime": null,
      "actualEndTime": null,
      "overrunMinutes": 0,
      "completedBy": null
    }
  ],
  "connectedOrganizers": [
    { "username": "marco.organizer", "firstName": "Marco", "connected": true }
  ],
  "serverTimestamp": "2026-02-14T18:00:00Z"
}
```

Map `trigger` field to `EventStateMessageType` via existing enum (e.g., `ORGANIZER_JOINED` → `.heartbeat` for W4.1; W4.2+ will add `SESSION_ENDED`).

### CachedSession Fields (confirm before starting)

From reuse-map Area 4 — these exist in `Models/CachedSession.swift`:
```swift
var actualStartTime: Date?          // [Source: CachedSession.swift:21]
var actualEndTime: Date?            // [Source: CachedSession.swift:22]
var overrunMinutes: Int?            // [Source: CachedSession.swift:23]
var completedByUsername: String?    // [Source: CachedSession.swift:24]
```

If any field is missing, add it before starting Task 2 (these are in the architecture contract).

### Exponential Backoff Implementation

```swift
private var reconnectAttempt = 0
private var reconnectTask: Task<Void, Never>?

private func scheduleReconnect(eventCode: String, jwt: String) {
    let delay = min(pow(2.0, Double(reconnectAttempt)), 60.0)  // 2, 4, 8, ..., 60
    reconnectAttempt += 1
    reconnectTask = Task { @MainActor in
        try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
        guard !Task.isCancelled else { return }
        try? await webSocketClient.connect(eventCode: eventCode, accessToken: jwt)
    }
}

// Reset on successful connect:
private func onConnected() {
    reconnectAttempt = 0
}
```

### Project Structure Notes

```
New files:
apps/BATbern-watch/BATbern-watch Watch App/Data/WebSocketClient.swift
apps/BATbern-watch/BATbern-watch Watch App/Data/WebSocketService.swift
apps/BATbern-watch/BATbern-watch Watch App/Views/Shared/PresenceIndicatorView.swift

Modified files:
apps/BATbern-watch/BATbern-watch Watch App/Data/EventDataController.swift  (applyServerState)
apps/BATbern-watch/BATbern-watch Watch App/Views/Organizer/LiveCountdownView.swift  (add PresenceIndicatorView, connect/disconnect)
apps/BATbern-watch/BATbern-watch Watch App/App/ContentView.swift  (inject WebSocketService env)
apps/BATbern-watch/BATbern-watch Watch App/App/BATbernWatchApp.swift  (Task 11: pass concrete WebSocketClient to ArrivalTracker + WebSocketService init)

Backend new files:
services/event-management-service/src/main/resources/db/migration/V56__add_watch_session_fields.sql
services/event-management-service/src/main/java/ch/batbern/events/watch/JwtStompInterceptor.java
services/event-management-service/src/main/java/ch/batbern/events/watch/WatchPresenceService.java
services/event-management-service/src/main/java/ch/batbern/events/watch/WatchWebSocketDisconnectListener.java
services/event-management-service/src/main/java/ch/batbern/events/watch/dto/WatchStateUpdateMessage.java
services/event-management-service/src/main/java/ch/batbern/events/watch/dto/ConnectedOrganizerDto.java
services/event-management-service/src/main/java/ch/batbern/events/watch/dto/WatchActionMessage.java

Backend modified files:
services/event-management-service/src/main/java/ch/batbern/events/config/WebSocketConfig.java  (add raw WS endpoint + interceptor)
services/event-management-service/src/main/java/ch/batbern/events/watch/WatchWebSocketController.java  (join/leave/action)
services/event-management-service/src/main/java/ch/batbern/events/watch/WatchEventController.java  (populate W4 session fields)
services/event-management-service/src/main/java/ch/batbern/events/domain/Session.java  (add W4 columns)
```

### Testing Standards

- watchOS: Swift Testing (`@Test`, `#expect`) — pure unit tests, no simulator needed
- Backend: JUnit 5 + Testcontainers PostgreSQL (extends `AbstractIntegrationTest`)
- Do NOT use H2 / in-memory databases for backend tests
- `MockWebSocketClient.emit()` is the primary driver for watchOS WebSocket tests
- Run watchOS tests: `xcodebuild test -scheme "BATbern-watch Watch App" -destination 'platform=watchOS Simulator,name=Apple Watch Series 9 (45mm)'`

### References

- [Source: docs/watch-app/epic-4-reuse-map.md#Area-3] — ConnectionStatusBar, WebSocket connectivity mandate
- [Source: docs/watch-app/epic-4-reuse-map.md#Area-4] — EventStateManager single source of truth
- [Source: docs/watch-app/architecture.md#Organizer-Real-Time-WebSocket] — STOMP topics + action destinations
- [Source: docs/watch-app/architecture.md#Authentication-Security] — JWT pairing flow + token refresh
- [Source: apps/BATbern-watch/BATbern-watch Watch App/Protocols/WebSocketClientProtocol.swift] — Full contract
- [Source: apps/BATbern-watch/BATbern-watch Watch AppTests/Mocks/MockWebSocketClient.swift] — Test double ready to use
- [Source: apps/BATbern-watch/BATbern-watch Watch App/Data/EventDataController.swift:29] — isOffline flag
- [Source: apps/BATbern-watch/BATbern-watch Watch App/Views/Shared/ConnectionStatusBar.swift] — Existing connectivity view
- [Source: services/event-management-service/src/main/java/ch/batbern/events/config/WebSocketConfig.java] — Existing STOMP config to extend
- [Source: services/event-management-service/src/main/java/ch/batbern/events/watch/WatchWebSocketController.java] — Existing speaker arrival handler to extend
- [Source: _bmad-output/implementation-artifacts/w3-4-session-schedule-next-session-preview.md] — Previous story learnings

---

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- StompClientLib NOT in Package.swift — implemented STOMP 1.2 manually using URLSessionWebSocketTask
- `EventStateMessage` extended with optional `stateUpdate: WatchStateUpdate?` (default nil) to carry full server state without breaking existing callers
- `EventDataController.isOffline` changed from `private(set)` to settable for WebSocketService Task 3.5
- `EventDataController.currentEvent` changed from `private(set)` to `var` for test seeding

### Completion Notes List

- Tasks 1–4, 11.1–11.4 complete (watchOS client + wiring)
- Tasks 5–10 and Task 11.5 pending (backend + ArrivalTrackerTests)
- All new Swift files must be added to Xcode project manually (right-click → Add Files)

### File List

**New watchOS files:**
- `apps/BATbern-watch/BATbern-watch Watch App/Data/WebSocketClient.swift`
- `apps/BATbern-watch/BATbern-watch Watch App/Data/WebSocketService.swift`
- `apps/BATbern-watch/BATbern-watch Watch App/Views/Shared/PresenceIndicatorView.swift`
- `apps/BATbern-watch/BATbern-watch Watch AppTests/Data/WebSocketClientTests.swift`
- `apps/BATbern-watch/BATbern-watch Watch AppTests/Data/WebSocketServiceTests.swift`
- `apps/BATbern-watch/BATbern-watch Watch AppTests/Views/PresenceIndicatorViewTests.swift`
- `apps/BATbern-watch/BATbern-watch Watch AppTests/Data/EventDataControllerApplyServerStateTests.swift`

**Modified watchOS files:**
- `apps/BATbern-watch/BATbern-watch Watch App/Protocols/WebSocketClientProtocol.swift` (added WatchStateUpdate, SessionStateUpdate, ConnectedOrganizer; extended EventStateMessage)
- `apps/BATbern-watch/BATbern-watch Watch App/Data/EventDataController.swift` (applyServerState, isOffline/currentEvent visibility)
- `apps/BATbern-watch/BATbern-watch Watch App/Views/Organizer/LiveCountdownView.swift` (PresenceIndicatorView, WebSocket lifecycle)
- `apps/BATbern-watch/BATbern-watch Watch App/App/ContentView.swift` (WebSocketService environment)
- `apps/BATbern-watch/BATbern-watch Watch App/App/BATbernWatchApp.swift` (shared WebSocketClient, WebSocketService env)
