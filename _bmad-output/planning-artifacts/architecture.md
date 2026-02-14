# BATbern Watch — Architecture Document

> **Status:** Draft
> **Last Updated:** 2026-02-14
> **Audience:** Developers, Architect, Product Owner
> **Depends On:** [PRD](prd.md) · [UX Spec](ux-design-specification.md) · [Design Directions](ux-design-directions.html)

---

## 1. System Context

BATbern Watch is a **standalone watchOS app** that connects directly to the existing BATbern backend over WiFi. It extends the platform with real-time event orchestration for 4 organizers wearing Apple Watches during a live conference.

### 1.1 System Context Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        Venue WiFi Network                       │
│                                                                 │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐       │
│  │ Watch 1  │  │ Watch 2  │  │ Watch 3  │  │ Watch 4  │       │
│  │ (Marco)  │  │ (Sarah)  │  │ (Nissim) │  │ (Thomas) │       │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘       │
│       │              │              │              │             │
│       └──────────────┴──────┬───────┴──────────────┘             │
│                             │ HTTPS + WSS                        │
└─────────────────────────────┼───────────────────────────────────┘
                              │
                              ▼
                 ┌────────────────────────┐
                 │    AWS ALB (HTTPS)     │
                 └───────────┬────────────┘
                             │
              ┌──────────────┴──────────────┐
              ▼                              ▼
   ┌─────────────────┐          ┌─────────────────────────┐
   │   API Gateway    │          │  Event Management Svc   │
   │   (REST auth)    │          │  (WebSocket + REST)     │
   │   Port 8000      │          │  Port 8002              │
   └─────────────────┘          └──────────┬──────────────┘
                                           │
                                    ┌──────┴──────┐
                                    │ PostgreSQL  │
                                    │   (RDS)     │
                                    └─────────────┘
```

### 1.2 Key Architectural Decisions

| Decision | Choice | Rationale |
|---|---|---|
| **WebSocket protocol** | STOMP over WebSocket (existing) | Already configured in Event Management Service; SockJS fallback not needed on watchOS |
| **Connection target** | Direct to Event Management Service | WebSocket bypasses API Gateway (existing pattern); authentication handled at STOMP level |
| **Authentication** | JWT token in STOMP CONNECT headers | Cognito tokens reused; validated server-side before connection accepted |
| **State model** | Server-authoritative with local cache | Backend is source of truth; Watch caches for offline resilience |
| **Offline strategy** | Local countdown + action queue | Timer runs from cached schedule; actions queued and replayed on reconnect |
| **Conflict resolution** | Last-write-wins with server timestamp | Simple; conflicts unlikely with 4 users and distinct action types |
| **Message broker** | Spring simple in-memory broker (existing) | Sufficient for 4 concurrent connections; no external MQ needed |

---

## 2. watchOS App Architecture

### 2.1 Architecture Pattern

**MVVM + Repository** with SwiftUI lifecycle:

```
┌─────────────────────────────────────────────────────────┐
│                     watchOS App                          │
│                                                         │
│  ┌─────────────────────────────────────────────────┐    │
│  │              Presentation Layer                  │    │
│  │  ┌───────────┐ ┌───────────┐ ┌──────────────┐  │    │
│  │  │ Session   │ │ Schedule  │ │ Complication  │  │    │
│  │  │ View      │ │ List View │ │ Controller    │  │    │
│  │  └─────┬─────┘ └─────┬─────┘ └──────┬───────┘  │    │
│  │        │              │              │           │    │
│  │  ┌─────┴──────────────┴──────────────┴───────┐  │    │
│  │  │           EventViewModel                   │  │    │
│  │  │  @Published currentSession                 │  │    │
│  │  │  @Published timeRemaining                  │  │    │
│  │  │  @Published connectionState                │  │    │
│  │  │  @Published connectedOrganizers            │  │    │
│  │  └───────────────────┬───────────────────────┘  │    │
│  └──────────────────────┼──────────────────────────┘    │
│                         │                                │
│  ┌──────────────────────┼──────────────────────────┐    │
│  │              Domain Layer                        │    │
│  │  ┌───────────┐ ┌────┴──────┐ ┌──────────────┐  │    │
│  │  │ Event     │ │ Session   │ │ Haptic       │  │    │
│  │  │ State     │ │ Timer     │ │ Scheduler    │  │    │
│  │  │ Machine   │ │ Engine    │ │              │  │    │
│  │  └───────────┘ └───────────┘ └──────────────┘  │    │
│  └──────────────────────┬──────────────────────────┘    │
│                         │                                │
│  ┌──────────────────────┼──────────────────────────┐    │
│  │              Data Layer                          │    │
│  │  ┌───────────┐ ┌────┴──────┐ ┌──────────────┐  │    │
│  │  │ WebSocket │ │ Local     │ │ Action       │  │    │
│  │  │ Client    │ │ Cache     │ │ Queue        │  │    │
│  │  │ (STOMP)   │ │ (SwiftDa.)│ │ (Offline)    │  │    │
│  │  └───────────┘ └───────────┘ └──────────────┘  │    │
│  └─────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────┘
```

### 2.2 Module Breakdown

#### Presentation Layer

| Module | Responsibility |
|---|---|
| `SessionView` | Progress ring + card stack (chosen direction). Displays countdown, speaker card, next-up card. |
| `ScheduleListView` | Digital Crown scrollable list of all sessions. |
| `CascadeSheet` | Modal for schedule shift selection after overrun. |
| `BreakView` | Break countdown with gong reminder. |
| `TransitionView` | Next speaker introduction screen. |
| `SetupView` | Event selection and WiFi connection status. |
| `ComplicationController` | WidgetKit timeline provider for watch face complications. |

#### Domain Layer

| Module | Responsibility |
|---|---|
| `EventStateMachine` | Manages session lifecycle: `SCHEDULED → LIVE → COMPLETE`. Handles overrun detection, cascade logic. |
| `SessionTimerEngine` | High-precision countdown timer. Fires at 1-second intervals. Calculates remaining time from `scheduledEndTime` and device clock. Works fully offline. |
| `HapticScheduler` | Schedules haptic alerts at threshold times (5 min, 2 min, 0:00, overrun pulses, gong). Uses `WKInterfaceDevice` haptic types. Recalculates on cascade. |

#### Data Layer

| Module | Responsibility |
|---|---|
| `WebSocketClient` | STOMP client over WebSocket. Connects to backend, sends actions, receives state updates. Handles reconnect with exponential backoff. |
| `LocalCache` | SwiftData store for event schedule, sessions, speaker portraits. Persists across app restarts. Source of truth when offline. |
| `ActionQueue` | Queues organizer actions (session complete, cascade) when offline. Replays in order on reconnect. Persisted to disk. |
| `AuthManager` | Manages Cognito JWT tokens. Handles initial login and token refresh. Stores credentials in Keychain. |

### 2.3 Key Data Types

```swift
// Core domain models (mirroring backend)
struct WatchEvent: Codable, Identifiable {
    let eventCode: String          // e.g., "BATbern56"
    let title: String
    let eventDate: Date
    var sessions: [WatchSession]
    var currentSessionIndex: Int
    var connectedOrganizers: [OrganizerPresence]
}

struct WatchSession: Codable, Identifiable {
    let id: String                 // Internal UUID
    let sessionSlug: String        // ADR-003 meaningful ID
    let speakerUsername: String
    let speakerName: String
    let speakerPortraitURL: URL?
    let talkTitle: String
    let sessionType: SessionType   // .talk, .break, .networking
    let durationMinutes: Int
    var scheduledStartTime: Date
    var scheduledEndTime: Date
    var actualStartTime: Date?
    var actualEndTime: Date?
    var status: SessionStatus      // .scheduled, .live, .complete
    var overrunMinutes: Int
}

enum SessionStatus: String, Codable {
    case scheduled, live, complete
}

enum SessionType: String, Codable {
    case talk, keynote, panel, workshop, networking, breakTime = "break"
}

struct OrganizerPresence: Codable {
    let username: String
    let displayName: String
    var connected: Bool
    var lastSeen: Date
}

// Connection state
enum ConnectionState {
    case disconnected
    case connecting
    case connected(organizerCount: Int)
    case offline(since: Date)
    case reconnecting(attempt: Int)
}

// Timer state for UI binding
enum TimerState {
    case onTrack(remaining: TimeInterval)      // > 5 min
    case warning(remaining: TimeInterval)       // 5-2 min
    case urgent(remaining: TimeInterval)        // < 2 min
    case timesUp                                // 0:00
    case overrun(elapsed: TimeInterval)         // +N:NN
    case breakActive(remaining: TimeInterval)   // Break countdown
}
```

---

## 3. Real-Time Sync Architecture

### 3.1 Connection Lifecycle

```
┌──────────────────────────────────────────────────────────────┐
│                   Watch Connection Lifecycle                  │
│                                                              │
│  ┌─────────┐   JWT    ┌───────────┐  STOMP   ┌──────────┐  │
│  │  Auth   │────────▶│  Connect  │────────▶ │ Subscribe │  │
│  │ (Login) │         │  (WSS)    │  CONNECT │ to event  │  │
│  └─────────┘         └───────────┘          └─────┬─────┘  │
│                                                    │         │
│                     ┌──────────────────────────────┘         │
│                     ▼                                        │
│              ┌──────────────┐                                │
│              │  Connected   │◀──── State updates from server │
│              │  (Active)    │────▶ Actions to server         │
│              └──────┬───────┘                                │
│                     │                                        │
│           WiFi loss │                                        │
│                     ▼                                        │
│              ┌──────────────┐                                │
│              │   Offline    │  Timer continues locally       │
│              │   (Cached)   │  Actions queued                │
│              └──────┬───────┘                                │
│                     │                                        │
│           WiFi back │                                        │
│                     ▼                                        │
│              ┌──────────────┐                                │
│              │  Reconnect   │  Replay queued actions         │
│              │  + Reconcile │  Reconcile state with server   │
│              └──────────────┘                                │
└──────────────────────────────────────────────────────────────┘
```

### 3.2 STOMP Topic Structure

Leveraging the existing WebSocket infrastructure in Event Management Service:

```
# Server → Watch (subscriptions)
/topic/events/{eventCode}/state       # Full event state updates
/user/queue/watch/ack                 # Per-user action acknowledgements
/user/queue/watch/errors              # Per-user error messages

# Watch → Server (send destinations)
/app/watch/events/{eventCode}/join    # Organizer joins event
/app/watch/events/{eventCode}/leave   # Organizer leaves event
/app/watch/events/{eventCode}/action  # Session complete, cascade, etc.
```

### 3.3 Message Schemas

#### Server → Watch: State Update

Sent on initial connection and after every state change:

```json
{
  "type": "STATE_UPDATE",
  "eventCode": "BATbern56",
  "currentSessionIndex": 2,
  "sessions": [
    {
      "sessionSlug": "cloud-native-pitfalls",
      "speakerUsername": "anna.meier",
      "speakerName": "Anna Meier",
      "talkTitle": "Cloud-Native Pitfalls",
      "sessionType": "talk",
      "durationMinutes": 25,
      "scheduledStartTime": "2026-02-14T18:00:00Z",
      "scheduledEndTime": "2026-02-14T18:25:00Z",
      "actualEndTime": "2026-02-14T18:29:00Z",
      "status": "COMPLETE",
      "overrunMinutes": 4
    }
  ],
  "connectedOrganizers": [
    { "username": "marco.organizer", "connected": true },
    { "username": "sarah.organizer", "connected": true },
    { "username": "nissim.organizer", "connected": false },
    { "username": "thomas.organizer", "connected": true }
  ],
  "serverTimestamp": "2026-02-14T18:35:01Z"
}
```

#### Watch → Server: Session Complete

```json
{
  "action": "SESSION_COMPLETE",
  "sessionSlug": "cloud-native-pitfalls",
  "organizerUsername": "marco.organizer",
  "overrunMinutes": 4,
  "clientTimestamp": "2026-02-14T18:29:00Z"
}
```

#### Watch → Server: Schedule Cascade

```json
{
  "action": "SCHEDULE_CASCADE",
  "sessionSlug": "cloud-native-pitfalls",
  "organizerUsername": "marco.organizer",
  "shiftMinutes": 5,
  "clientTimestamp": "2026-02-14T18:30:00Z"
}
```

#### Server → Watch: Action Acknowledgement

```json
{
  "type": "ACTION_ACK",
  "action": "SESSION_COMPLETE",
  "sessionSlug": "cloud-native-pitfalls",
  "success": true,
  "serverTimestamp": "2026-02-14T18:29:01Z"
}
```

### 3.4 Sync Guarantees

| Guarantee | Target | Mechanism |
|---|---|---|
| State propagation | < 3 seconds | WebSocket broadcast to `/topic/events/{eventCode}/state` |
| Action delivery | At-least-once | Watch retries until ACK received or queues offline |
| Conflict resolution | Last-write-wins | Server timestamps; simultaneous "Done" taps are idempotent |
| Offline resilience | Full countdown + haptics | `SessionTimerEngine` runs from cached `scheduledEndTime` |
| Reconnect recovery | Automatic | Exponential backoff (1s, 2s, 4s, 8s, max 30s) |
| State reconciliation | On reconnect | Server sends full state; Watch replaces local state, replays queued actions |

### 3.5 Conflict Scenarios

| Scenario | Resolution |
|---|---|
| Two organizers tap "Done" simultaneously | Idempotent: second tap is no-op; both receive same state update |
| Organizer offline taps "Done", another online taps "Done" | Online action processed first; offline action replayed on reconnect, detected as duplicate (idempotent) |
| Two organizers choose different cascade shift | First-processed wins; second receives updated state showing cascade already applied |
| Watch reconnects with stale state | Full state sync on reconnect replaces local cache |

---

## 4. Backend Extensions

### 4.1 Changes to Event Management Service

The existing WebSocket infrastructure (`WebSocketConfig.java`) needs these additions:

#### New Components

```
ch.batbern.events.watch/
├── WatchWebSocketController.java      # STOMP message handlers
├── WatchConnectionManager.java        # Track connected organizers
├── WatchEventStateService.java        # Build state snapshots
├── WatchSessionActionService.java     # Process Watch actions
└── dto/
    ├── WatchStateUpdate.java          # State broadcast DTO
    ├── WatchAction.java               # Incoming action DTO
    └── WatchActionAck.java            # Action acknowledgement DTO
```

#### WebSocket Security Enhancement

```java
// Extend existing WebSocketConfig to add JWT auth interceptor
@Override
public void configureClientInboundChannel(ChannelRegistration reg) {
    reg.interceptors(new JwtStompInterceptor(jwtDecoder));
}
```

The `JwtStompInterceptor` validates the JWT token from the STOMP `CONNECT` frame's `Authorization` header before allowing the connection.

#### New REST Endpoints

For initial setup, offline sync, and fallback:

```
GET  /api/v1/watch/events/{eventCode}/state
     → Full event state snapshot (same as WebSocket initial state)
     → Used for: polling fallback, offline cache refresh

GET  /api/v1/watch/events/{eventCode}/speakers/{username}/portrait
     → Redirects to S3 presigned URL for portrait image
     → Used for: pre-caching speaker photos

POST /api/v1/watch/events/{eventCode}/actions
     → Process offline-queued actions (same schema as STOMP messages)
     → Used for: offline action replay when WebSocket unavailable

GET  /api/v1/watch/organizers/me/active-events
     → List events where current user is an organizer
     → Used for: event selection on Watch setup screen
```

### 4.2 Session Model Extensions

New fields on the existing `Session` entity:

```java
// Additions to Session.java
private Instant actualStartTime;   // Set when session goes LIVE
private Instant actualEndTime;     // Set when organizer taps "Done"
private Integer overrunMinutes;    // Calculated: actual vs scheduled
private String completedByUsername; // Which organizer tapped "Done"
```

New database migration:

```sql
-- V{next}__add_watch_session_fields.sql
ALTER TABLE sessions ADD COLUMN actual_start_time TIMESTAMP;
ALTER TABLE sessions ADD COLUMN actual_end_time TIMESTAMP;
ALTER TABLE sessions ADD COLUMN overrun_minutes INTEGER DEFAULT 0;
ALTER TABLE sessions ADD COLUMN completed_by_username VARCHAR(100);
```

### 4.3 Organizer Presence Tracking

In-memory tracking (no database needed for 4 connections):

```java
@Component
public class WatchConnectionManager {
    // ConcurrentHashMap<eventCode, Map<username, ConnectionInfo>>
    // Updated on STOMP CONNECT/DISCONNECT events
    // Broadcast presence changes to /topic/events/{eventCode}/state
}
```

### 4.4 Infrastructure Changes

#### ALB Configuration

WebSocket connections require the ALB to support connection upgrades:

```typescript
// In ecs-service.ts — already supported by default on ALB
// Ensure target group has stickiness enabled for WebSocket
targetGroup.setAttribute('stickiness.enabled', 'true');
targetGroup.setAttribute('stickiness.type', 'lb_cookie');
targetGroup.setAttribute('stickiness.lb_cookie.duration_seconds', '86400');

// Increase idle timeout for long-lived WebSocket connections
loadBalancer.setAttribute('idle_timeout.timeout_seconds', '3600');
```

#### ECS Task Configuration

WebSocket connections are long-lived; rolling deployments must drain gracefully:

```typescript
// Deregistration delay gives WebSocket clients time to reconnect
targetGroup.setAttribute('deregistration_delay.timeout_seconds', '30');
```

### 4.5 No New AWS Services Required

The Watch integration uses only existing infrastructure:
- **ECS Fargate**: Existing Event Management Service handles WebSocket
- **ALB**: Already supports WebSocket upgrade (HTTP/1.1 → WebSocket)
- **RDS PostgreSQL**: Schema extension only
- **Cognito**: Existing JWT tokens reused
- **S3/CloudFront**: Existing portrait storage reused

No APNs (push notifications) needed for MVP — WebSocket is sufficient for 4 always-connected watches on venue WiFi. APNs can be added in Phase 2 as a reliability enhancement.

---

## 5. Offline Architecture

### 5.1 Cache Strategy

```
┌──────────────────────────────────────────────┐
│              Local Cache (SwiftData)          │
│                                              │
│  ┌────────────────────────────────────────┐  │
│  │  EventCache                            │  │
│  │  - eventCode, title, date              │  │
│  │  - sessions[] (full schedule)          │  │
│  │  - lastSyncTimestamp                   │  │
│  └────────────────────────────────────────┘  │
│                                              │
│  ┌────────────────────────────────────────┐  │
│  │  PortraitCache                         │  │
│  │  - speakerUsername → imageData          │  │
│  │  - ~100KB per portrait × 8 speakers    │  │
│  │  - Total: < 1MB                        │  │
│  └────────────────────────────────────────┘  │
│                                              │
│  ┌────────────────────────────────────────┐  │
│  │  ActionQueue                           │  │
│  │  - Ordered list of pending actions     │  │
│  │  - Persisted to disk                   │  │
│  │  - Replayed on reconnect              │  │
│  └────────────────────────────────────────┘  │
│                                              │
│  Total storage: < 5MB per event              │
└──────────────────────────────────────────────┘
```

### 5.2 Offline Behavior Matrix

| Feature | Online | Offline |
|---|---|---|
| Countdown timer | Synced with server clock | Runs from cached `scheduledEndTime` |
| Haptic alerts | Synced delivery | Fires from local timer |
| Speaker info | Live from server | From cache |
| "Done" tap | Immediate broadcast | Queued, synced on reconnect |
| Cascade | Immediate propagation | Queued, synced on reconnect |
| Organizer count | Live presence | Shows last known state + "Offline" badge |
| Schedule updates | Real-time | Stale until reconnect |
| Complication | Live updates | Updates from local timer |

### 5.3 Reconnect Protocol

```
1. WiFi restored → WebSocket reconnect (exponential backoff)
2. STOMP CONNECT with JWT token
3. Server sends full STATE_UPDATE
4. Watch compares server state vs local state
5. Watch replays ActionQueue in order:
   a. For each queued action:
      - Send to server
      - Wait for ACK
      - If ACK.success → dequeue
      - If ACK.conflict → discard (server state is authoritative)
6. Replace local cache with server state
7. Recalculate haptic schedule from new state
8. Update complication timeline
```

---

## 6. Timer & Haptic Architecture

### 6.1 Timer Engine

The `SessionTimerEngine` is the most critical component — it must be accurate within 1 second.

```swift
// Timer runs independently of network state
class SessionTimerEngine: ObservableObject {
    @Published var timerState: TimerState = .onTrack(remaining: 0)

    // Uses scheduledEndTime from cache, not a running counter
    // This avoids drift — recalculates from wall clock each tick
    func tick() {
        let remaining = session.scheduledEndTime.timeIntervalSinceNow
        switch remaining {
        case let r where r > 300:  timerState = .onTrack(remaining: r)
        case let r where r > 120:  timerState = .warning(remaining: r)
        case let r where r > 0:    timerState = .urgent(remaining: r)
        case 0:                    timerState = .timesUp
        default:                   timerState = .overrun(elapsed: -remaining)
        }
    }
}
```

**Key design**: Timer calculates from `Date()` vs `scheduledEndTime`, not a decrementing counter. This ensures accuracy even if the app is suspended/resumed by watchOS.

### 6.2 Haptic Patterns

```swift
enum HapticAlert {
    case fiveMinWarning     // .notification + .success (single firm tap)
    case twoMinWarning      // .notification + .directionUp (double tap)
    case timesUp            // .notification + .failure (triple tap)
    case overrunPulse       // .start (rhythmic pulse, every 2 min)
    case gongReminder       // .notification + .retry (distinct pattern)
    case actionConfirm      // .success (brief confirmation)
    case connectionLost     // .failure (single warning)
}
```

### 6.3 Haptic Scheduling

```
Session Start ──────────────────────────────── Session End
│                                              │
│  [Normal]           [Warning]  [Urgent]      │ [Overrun]
│  > 5 min            5 min      2 min    0:00 │ +2m  +4m  ...
│                       │          │       │   │  │     │
│                    haptic     haptic   haptic │ pulse pulse
│                    (single)  (double) (triple)│ (rhythmic)
```

On schedule cascade, all pending haptic alerts are recalculated from the new `scheduledEndTime`.

---

## 7. Complication Architecture

### 7.1 WidgetKit Timeline

The complication uses `TimelineProvider` to show countdown without the app being active:

```swift
struct BATbernComplicationProvider: TimelineProvider {
    func timeline(for complication: CLKComplication) -> Timeline<Entry> {
        // Generate entries at key moments:
        // 1. Every minute for normal countdown
        // 2. Every 15 seconds when < 5 min remaining
        // 3. At exact threshold times (5:00, 2:00, 0:00)
        // 4. Session transitions
    }
}
```

### 7.2 Complication Families Supported

| Family | Content | Priority |
|---|---|---|
| `circularSmall` | Progress ring + minutes remaining | Primary |
| `rectangularLarge` | Speaker name + countdown + progress bar | Primary |
| `cornerCircular` | Minutes remaining (number only) | Secondary |
| `graphicExtraLarge` | Full progress ring + countdown + speaker | Stretch |

### 7.3 Complication Updates

- On state change (WebSocket message) → `WidgetCenter.shared.reloadTimelines()`
- Background refresh budget: watchOS grants ~4 refreshes/hour; use Extended Runtime session during active event
- Extended Runtime: Request `.self` session type for active event monitoring

---

## 8. Authentication Flow

### 8.1 Login Flow

```
┌──────────┐    ┌──────────────┐    ┌─────────────┐
│  Watch   │    │   Cognito    │    │   Backend   │
│  App     │    │   (AWS)      │    │   (EMS)     │
└────┬─────┘    └──────┬───────┘    └──────┬──────┘
     │                 │                    │
     │  1. Login       │                    │
     │  (username +    │                    │
     │   password)     │                    │
     ├────────────────▶│                    │
     │                 │                    │
     │  2. JWT tokens  │                    │
     │  (access +      │                    │
     │   refresh)      │                    │
     │◀────────────────┤                    │
     │                 │                    │
     │  3. STOMP CONNECT with Bearer token  │
     ├─────────────────────────────────────▶│
     │                 │                    │
     │  4. Validate JWT│                    │
     │                 │◀───────────────────┤
     │                 │  token valid       │
     │                 │───────────────────▶│
     │                 │                    │
     │  5. CONNECTED + initial state        │
     │◀─────────────────────────────────────┤
     │                 │                    │
```

### 8.2 Token Management

- **Access token**: Short-lived (1 hour). Stored in Keychain.
- **Refresh token**: Long-lived (30 days). Stored in Keychain.
- **Event duration**: ~3 hours. One token refresh mid-event.
- **Refresh strategy**: Proactive refresh 10 minutes before expiry.
- **STOMP reconnect**: Uses refreshed token automatically.

### 8.3 Authorization

Only users with `ORGANIZER` role (from Cognito `custom:role` claim) can:
- Connect to Watch WebSocket
- Send session actions
- View organizer presence

This is enforced server-side in `JwtStompInterceptor`.

---

## 9. Battery & Performance

### 9.1 Battery Budget (3-hour event)

| Component | Strategy | Impact |
|---|---|---|
| WebSocket | Single persistent connection (no polling) | Low — one TCP connection |
| Display | Always-on with dimmed colors (OLED) | Medium — mitigated by system |
| Timer | 1-second `Timer.publish` | Minimal — CPU wake only |
| Haptics | ~20 haptic events per event | Negligible |
| Network | Batch portrait downloads on join | One-time cost |
| Complication | Timeline-based (no active refresh) | Minimal |

**Target**: > 30% battery at end of 3-hour event.

### 9.2 Adaptive Behavior

| Battery Level | Behavior |
|---|---|
| > 50% | Full feature set |
| 20-50% | Reduce complication update frequency |
| < 20% | WebSocket maintained; complication updates at transition points only |
| < 10% | Alert user; maintain core countdown + haptics only |

### 9.3 Memory Budget

| Data | Size | Notes |
|---|---|---|
| Event schedule | ~10 KB | JSON, ~8 sessions |
| Speaker portraits | ~800 KB | ~100KB × 8, cached as compressed JPEG |
| App binary | ~5 MB | SwiftUI app |
| Runtime heap | ~20 MB | ViewModel + WebSocket buffer |
| **Total** | **< 30 MB** | Well within watchOS limits |

---

## 10. Testing Strategy

### 10.1 Test Pyramid

```
         ┌──────────┐
         │  Manual   │  Pilot at live BATbern event
         │  (1 event)│
         └──────────┘
        ┌────────────┐
        │ Integration │  WebSocket sync tests (4 simulators)
        │  (E2E)     │  Backend + Watch app together
        └────────────┘
      ┌──────────────────┐
      │    UI Tests       │  SwiftUI snapshot tests
      │  (XCTest + Xcode) │  Complication rendering
      └──────────────────┘
    ┌────────────────────────┐
    │     Unit Tests          │  Timer accuracy, state machine,
    │   (XCTest + Quick)      │  offline queue, conflict resolution
    └────────────────────────┘
```

### 10.2 Critical Test Scenarios

| Scenario | Test Type | What to Verify |
|---|---|---|
| Countdown accuracy over 30 min | Unit | Timer drift < 1 second |
| Simultaneous "Done" from 2 watches | Integration | Idempotent, both get same state |
| WiFi disconnect mid-session | Integration | Timer continues, actions queue |
| WiFi reconnect with stale state | Integration | State reconciled, queue replayed |
| Schedule cascade propagation | Integration | All 4 watches updated < 3 seconds |
| Haptic fires at exact threshold | Unit | Within 1 second of target time |
| Token expires mid-event | Integration | Transparent refresh, no disruption |
| App backgrounded by watchOS | Unit | Timer accurate on resume |
| 3-hour continuous session | Manual | Battery > 30%, no crashes |

### 10.3 Backend Test Additions

```java
// New integration tests in Event Management Service
class WatchWebSocketIntegrationTest extends AbstractIntegrationTest {
    // Uses Testcontainers PostgreSQL (per CLAUDE.md standards)
    // Tests: connect, subscribe, send action, receive state update
    // Tests: concurrent actions, cascade propagation
    // Tests: JWT auth on STOMP CONNECT
}
```

---

## 11. Deployment & Rollout

### 11.1 Backend Deployment

Backend changes deploy through existing CI/CD pipeline:
1. Schema migration runs on deploy (Flyway)
2. New WebSocket handlers are backward-compatible (no breaking changes to existing APIs)
3. Deploy to staging first, test with Watch simulators

### 11.2 watchOS App Distribution

- **Development**: Direct install via Xcode to 4 organizer watches
- **TestFlight**: Internal testing distribution
- **App Store**: Public distribution (Phase 2+, if desired)
- **MVP**: Xcode direct install is sufficient for 4 users

### 11.3 Rollout Plan

| Phase | Scope | Goal |
|---|---|---|
| 1. Backend deploy | Staging | WebSocket endpoints + schema migration |
| 2. Simulator testing | 4 Xcode simulators | Multi-watch sync validation |
| 3. Device testing | 1 organizer watch | Single-device end-to-end |
| 4. Dress rehearsal | 4 watches, mock event | Full team coordination test |
| 5. Live pilot | 1 real BATbern event | Production validation |

---

## 12. Risk Register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Venue WiFi unreliable | Medium | High | Offline mode with local countdown + action queue; consider mobile hotspot backup |
| WebSocket drops during event | Medium | Medium | Auto-reconnect with backoff; timer/haptics work offline |
| watchOS suspends app | Low | Medium | Extended Runtime session; WidgetKit timeline as fallback |
| Battery drains before event ends | Low | High | Adaptive polling; test 3-hour sessions in advance |
| Two organizers conflict on cascade | Low | Low | First-wins; second sees result immediately |
| ECS rolling deploy during event | Low | High | Schedule deploys outside event hours; ALB draining handles gracefully |
| Cognito token refresh fails | Low | Medium | Cache refresh token in Keychain; graceful degradation to offline mode |

---

## 13. Phase 2 Extensions

Features deferred from MVP, designed to be additive:

| Feature | Architecture Impact |
|---|---|
| **APNs backup channel** | Add push notification for critical state changes (session advance) as WebSocket reliability backup |
| **Speaker arrival tracking** | New STOMP topic `/topic/events/{eventCode}/speakers`; new backend endpoint |
| **Quick ping between organizers** | New action type `PING` with haptic on receiving watch |
| **Speaker time signal** | Would require separate speaker-facing channel or companion app |
| **Multi-event support** | Watch switches between events; minor UI/cache changes |
| **Analytics dashboard** | Backend stores session timing data (already captured in `actualStartTime`/`actualEndTime`) |

---

## Appendix A: Technology Stack Summary

| Layer | Technology | Version |
|---|---|---|
| **watchOS App** | SwiftUI | watchOS 10+ |
| **Language** | Swift | 5.9+ |
| **Persistence** | SwiftData | watchOS 10+ |
| **Networking** | URLSession + StompClientLib | — |
| **Complications** | WidgetKit (ClockKit deprecated) | watchOS 10+ |
| **Auth** | AWS Amplify (Cognito) or direct HTTPS | — |
| **Backend** | Spring Boot + Spring WebSocket | 3.x |
| **Runtime** | Java | 21 LTS |
| **Database** | PostgreSQL | 15+ |
| **Infrastructure** | AWS CDK (ECS Fargate, ALB, RDS) | 2.x |
| **Message Broker** | Spring Simple Broker (in-memory) | — |

## Appendix B: File Impact Summary

### New Files (watchOS)

```
BATbernWatch/
├── BATbernWatchApp.swift
├── Views/
│   ├── SessionView.swift           # Progress ring + card stack
│   ├── ScheduleListView.swift      # Digital Crown scrollable
│   ├── CascadeSheet.swift          # Shift schedule modal
│   ├── BreakView.swift             # Break countdown
│   ├── TransitionView.swift        # Next speaker intro
│   └── SetupView.swift             # Event selection + login
├── ViewModels/
│   └── EventViewModel.swift        # Main observable state
├── Domain/
│   ├── EventStateMachine.swift     # Session lifecycle
│   ├── SessionTimerEngine.swift    # Countdown timer
│   └── HapticScheduler.swift       # Alert scheduling
├── Data/
│   ├── WebSocketClient.swift       # STOMP client
│   ├── LocalCache.swift            # SwiftData models
│   ├── ActionQueue.swift           # Offline queue
│   └── AuthManager.swift           # JWT management
├── Complications/
│   └── ComplicationProvider.swift  # WidgetKit timeline
└── Models/
    ├── WatchEvent.swift
    ├── WatchSession.swift
    └── DTOs.swift                  # Network DTOs
```

### Modified Files (Backend)

```
services/event-management-service/
├── src/main/java/ch/batbern/events/
│   ├── config/
│   │   └── WebSocketConfig.java            # Add JWT interceptor
│   ├── watch/                              # NEW package
│   │   ├── WatchWebSocketController.java
│   │   ├── WatchConnectionManager.java
│   │   ├── WatchEventStateService.java
│   │   ├── WatchSessionActionService.java
│   │   └── dto/
│   │       ├── WatchStateUpdate.java
│   │       ├── WatchAction.java
│   │       └── WatchActionAck.java
│   └── domain/
│       └── Session.java                    # Add actual_start/end, overrun fields
├── src/main/resources/db/migration/
│   └── V{next}__add_watch_session_fields.sql
└── src/test/java/ch/batbern/events/watch/
    └── WatchWebSocketIntegrationTest.java

infrastructure/
└── lib/constructs/ecs-service.ts           # ALB idle timeout + stickiness
```
