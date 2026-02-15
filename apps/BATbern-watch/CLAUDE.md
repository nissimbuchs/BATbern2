# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

BATbern Watch is a standalone watchOS companion app for real-time event orchestration at BATbern conferences. It operates in two modes:

- **Public zone** (unauthenticated): Browse event sessions, speaker bios, and abstracts via Digital Crown
- **Organizer zone** (authenticated): Live countdown timers, synchronized haptic alerts, and team-coordinated session control

**Critical Context:** This app is **independent** of the main BATbern Gradle/Java/TypeScript monorepo build system. It's a native Swift/SwiftUI watchOS app that consumes the BATbern REST API and WebSocket services.

## Build & Development Commands

### Prerequisites
- Xcode 16+
- watchOS 11+ SDK
- Apple Watch Simulator or physical device

### Essential Commands

```bash
# Open project in Xcode
open BATbern-watch.xcodeproj

# Build for Watch Simulator
xcodebuild -scheme "BATbern-watch Watch App" \
  -destination 'platform=watchOS Simulator,name=Apple Watch Series 9 (45mm)'

# Run all tests
xcodebuild test -scheme "BATbern-watch Watch App" \
  -destination 'platform=watchOS Simulator,name=Apple Watch Series 9 (45mm)'

# Run specific test class
xcodebuild test -scheme "BATbern-watch Watch App" \
  -destination 'platform=watchOS Simulator,name=Apple Watch Series 9 (45mm)' \
  -only-testing:BATbern-watch_Watch_AppTests/SessionViewModelTests

# Run single test method
xcodebuild test -scheme "BATbern-watch Watch App" \
  -destination 'platform=watchOS Simulator,name=Apple Watch Series 9 (45mm)' \
  -only-testing:BATbern-watch_Watch_AppTests/SessionViewModelTests/testCountdownTimerAccuracy

# Build for physical device (requires code signing)
xcodebuild -scheme "BATbern-watch Watch App" \
  -destination 'generic/platform=watchOS'

# Regenerate types from OpenAPI spec (after API changes)
./scripts/generate-types.sh

# Then manually add new files to Xcode project:
# 1. Open Xcode: open BATbern-watch.xcodeproj
# 2. Right-click "BATbern-watch Watch App" → Add Files...
# 3. Select Generated/Models/ folder
# 4. Ensure target membership is checked
```

### Xcode Development

**Recommended workflow:** Use Xcode for development, not command-line builds.

```bash
# Launch and auto-run in simulator
open BATbern-watch.xcodeproj
# Then: Cmd+R to build and run
```

**Scheme:** "BATbern-watch Watch App" (primary and only runnable scheme)

## Architecture Overview

### MVVM + Repository Pattern + Protocol-Based Dependency Injection

```
Views/               # SwiftUI views (no business logic)
  ├── Public/        # Unauthenticated screens
  ├── Organizer/     # Authenticated organizer workflows (planned)
  └── Shared/        # Reusable components (BATbernSymbolView, SpeakerPortraitView)

ViewModels/          # ObservableObject view models (presentation logic)
  └── PublicViewModel.swift        # Event browsing, session cards

Models/              # SwiftData models (local cache) + domain models
  ├── CachedEvent.swift            # Local cache entity
  ├── CachedSession.swift          # Session with computed state
  ├── CachedSpeaker.swift          # Speaker metadata
  ├── SessionState.swift           # Session lifecycle enum
  ├── HapticAlert.swift            # Haptic pattern definitions
  ├── PairingInfo.swift            # Organizer pairing data
  └── WatchModels.swift            # Shared domain types

Data/                # Repository pattern (network, caching)
  ├── PublicEventService.swift     # REST API client
  ├── LocalCache.swift             # SwiftData persistence
  └── PortraitCache.swift          # Speaker image caching

Domain/              # Pure business logic (no dependencies)
  ├── SessionTimerEngine.swift     # Wall-clock countdown timer
  └── HapticScheduler.swift        # Alert threshold calculation

Protocols/           # Dependency injection contracts (testability)
  ├── ClockProtocol.swift          # Injectable time (CRITICAL for testing)
  ├── APIClientProtocol.swift      # Network abstraction
  ├── WebSocketClientProtocol.swift # Real-time sync abstraction
  └── HapticServiceProtocol.swift  # Haptic engine abstraction

Generated/           # OpenAPI-generated types (auto-generated, do not edit)
  ├── Models/        # Event, Session, Speaker DTOs from events-api.openapi.yml
  └── OpenAPIUtilities.swift

Utilities/           # Helper functions
  └── SwissDateFormatter.swift     # Swiss German date/time formatting

Complications/       # Watch face complications (empty, planned for Epic W5)

Resources/           # Non-code assets

App/                 # App entry point
  ├── BATbernWatchApp.swift        # @main entry point
  └── ContentView.swift            # Root navigation view
```

### Critical Architectural Patterns

**1. Protocol-Based Dependency Injection**

**CRITICAL FOR TESTABILITY:** Every external dependency has a protocol interface to enable test mocking.

| Protocol | Production Impl | Test Mock | Purpose |
|---|---|---|---|
| `ClockProtocol` | `SystemClock` | `MockClock` | Injectable time control (timer accuracy) |
| `APIClientProtocol` | `PublicEventService` | `MockAPIClient` | Network requests |
| `WebSocketClientProtocol` | (TBD in Epic W4) | `MockWebSocketClient` | Real-time sync |
| `HapticServiceProtocol` | (TBD in Epic W3) | `MockHapticService` | Haptic feedback |

**Key Rule:** NEVER call `Date()` directly in production domain code — always use `clock.now` for testability. This pattern is essential for testing timer accuracy without flaky time-dependent tests.

**Example:**
```swift
// ❌ Wrong - untestable
class SessionTimer {
    func checkRemaining() -> TimeInterval {
        return session.endTime.timeIntervalSince(Date()) // Can't control time in tests
    }
}

// ✅ Correct - testable
class SessionTimerEngine {
    let clock: ClockProtocol

    func checkRemaining() -> TimeInterval {
        return session.endTime.timeIntervalSince(clock.now) // Tests inject MockClock
    }
}
```

**2. Server-Authoritative State Model**
- Backend is the source of truth for event state
- Local SwiftData cache is read-only mirror
- Actions (advance session, trigger cascade) sent to server via STOMP
- Server broadcasts state changes to all connected devices
- Last-write-wins conflict resolution on server side

**3. Offline-First Design**
- All event data cached locally after initial sync (SwiftData)
- Countdown timer and haptics run locally (wall-clock based, not network-dependent)
- Actions queued locally when offline (`OfflineQueue.swift`)
- Automatic sync when connectivity restored
- No user action required for offline transition

**4. Real-Time Synchronization**
- STOMP over WebSocket for low-latency broadcasts
- JWT authentication in STOMP CONNECT headers
- Server broadcasts to `/topic/events/{eventCode}/state`
- Clients subscribe on join, single persistent connection
- REST fallback endpoints for reconnection scenarios

**5. Battery-Aware Behavior**
- Extended Runtime session for background haptics
- Adaptive polling frequency (reduce when battery < 20%)
- Single persistent WebSocket (no frequent reconnects)
- Lazy loading of speaker portraits (fetch on-demand, not bulk)
- Target: >30% battery remaining after 3-hour event

**6. Complication-First Architecture**
- Watch face complication is primary interface (90% usage)
- App itself is secondary (settings, auth, manual schedule override)
- WidgetKit timeline updates on state changes (<1 second latency)
- Always-on display shows current session without wrist raise
- Context-aware content: different displays during talk/break/between sessions

## Key Technical Decisions

### Persistence: SwiftData
- **Why not Core Data:** SwiftData is modern Swift-first API (no Objective-C bridging)
- **Schema:** `Event`, `Session`, `Speaker` models with cascade delete
- **Cache strategy:** Full event schedule + speaker metadata synced on join
- **Storage budget:** <50MB per event (enforced via portrait compression)

### Networking: URLSession + STOMP
- **REST API:** `URLSession` for initial sync, portrait downloads, auth
- **Real-time sync:** STOMP protocol over `URLSessionWebSocketTask`
- **Library:** StompClientLib (Swift Package Manager dependency)
- **Auth flow:** Cognito JWT in both REST headers and STOMP CONNECT frame

### Haptics: 7-Pattern Vocabulary
- **5-min warning:** `.notification` + short vibration
- **2-min warning:** `.notification` + double pulse
- **Time's up:** `.notification` + long sustained
- **Overrun pulse:** `.start` repeating every 30 seconds (escalating intensity)
- **Gong reminder:** `.notification` + triple tap (break ending)
- **Action confirm:** `.success` (session advanced, cascade triggered)
- **Connection lost:** `.failure` + visual banner

**Implementation:** `WKInterfaceDevice.current().play(_:)` with Extended Runtime session.

### Authentication: Keychain + Cognito
- **Flow:** Organizer logs in via Watch keyboard/Scribble → Cognito JWT returned
- **Storage:** Access token in Keychain (`kSecAttrAccessibleAfterFirstUnlock`)
- **Refresh:** Background token refresh before expiry (no mid-event re-auth)
- **Authorization:** JWT validates both REST calls and STOMP subscription

### Error Handling: Graceful Degradation
- **Never crash during live event** (NFR6)
- Defensive error boundaries in all network code
- Silent fallback to cached state on sync failure
- User-visible connectivity indicator only (no error alerts mid-event)
- Comprehensive logging to OSLog for post-event debugging

## Testing Strategy

**Framework:** Swift Testing (unit tests) + XCTest (UI tests)
**Coverage Target:** 80%+ for business logic
**Documentation:** See `TESTING.md` for comprehensive test framework guide

### Test Directory Structure

```
BATbern-watch Watch AppTests/          # Unit tests (Swift Testing)
├── Domain/                            # Pure domain logic tests
│   ├── SessionTimerEngineTests.swift  # Wall-clock countdown (HIGH RISK)
│   └── HapticSchedulerTests.swift     # Threshold-based alert scheduling
├── Data/                              # Repository layer tests
│   ├── LocalCacheTests.swift          # SwiftData persistence
│   └── PublicEventServiceTests.swift  # API client
├── ViewModels/                        # Presentation logic tests
│   └── PublicViewModelTests.swift     # Event browsing, session cards
├── Views/                             # View component tests
│   └── SessionCardViewTests.swift     # SwiftUI view rendering
├── Mocks/                             # Protocol-backed test doubles
│   ├── MockClock.swift                # Injectable time control
│   ├── MockAPIClient.swift            # REST client stub
│   ├── MockWebSocketClient.swift      # STOMP client stub
│   └── MockHapticService.swift        # Records haptic invocations
├── Factories/                         # Test data builders
│   └── TestDataFactory.swift          # Event, Session, Speaker factories
└── Helpers/                           # Test utilities
    └── AsyncTestHelpers.swift         # Async wait, error assertions
```

### Risk-Ordered Testing Priority

**Write tests in this order, matching risk profile:**

| Priority | Component | Risk | Pattern |
|---|---|---|---|
| **P0** | `SessionTimerEngine` | Timer drift on watchOS suspension | MockClock + wall-clock assertions |
| **P0** | `HapticScheduler` | Missed/duplicate alerts during live event | MockClock + MockHapticService |
| **P1** | WebSocket sync | Multi-device state divergence | MockWebSocketClient + emit() |
| **P1** | Offline `ActionQueue` | Lost actions on app restart | Persistence tests |
| **P2** | `AuthManager` | JWT expiry mid-event | MockClock + token lifecycle |
| **P2** | Public zone data loading | Stale cache, progressive publishing | MockAPIClient |
| **P3** | UI navigation | Swipe zones, Crown scroll | XCTest UI tests |

### Test Data Factory Pattern

All test data flows through `TestDataFactory.swift` — a single source of realistic BATbern defaults:

```swift
// Simple — uses all defaults
let event = TestData.event()

// Override specific fields
let speaker = TestData.speaker(firstName: "Marco", arrived: true)

// Fixed dates for timer tests (not relative to now)
let session = TestData.fixedSession(start: clock.now, end: clock.now.addingTimeInterval(2700))

// Full evening schedule
let event = TestData.eveningEvent(baseTime: referenceDate)
```

### Key Testing Rules

**CRITICAL - Follow these rules strictly:**

1. **NEVER use `Date()` in production domain code** — always inject `ClockProtocol`
2. **NEVER use decrementing counters for timers** — always calculate from wall clock
3. **Every external dependency gets a protocol** — no concrete dependencies in domain/ViewModel layer
4. **Mocks record invocations** — assert on call count and arguments, not just return values
5. **Use `TestData.fixedSession(start:end:)` for timer tests** — relative dates cause flaky tests
6. **Reset mock state between tests** — Swift Testing creates fresh instances per `@Suite` init

### Injectable Clock Pattern (Critical!)

The most critical testability pattern. The `SessionTimerEngine` NEVER calls `Date()` directly — it uses `clock.now`. This lets tests control time precisely:

```swift
let clock = MockClock(fixedDate: someDate)
let engine = SessionTimerEngine(clock: clock)

engine.setActiveSession(session)
clock.advance(by: 300) // Simulate 5 minutes passing
engine.recalculate()

#expect(engine.remainingSeconds == expectedValue)
```

This pattern proves the timer survives watchOS app suspension (where a decrementing counter would drift).

### Recording Mocks Pattern

Mocks record every invocation for assertion:

```swift
let haptics = MockHapticService()
// ... run code that triggers haptics ...
#expect(haptics.playedAlerts == [.fiveMinuteWarning, .twoMinuteWarning])
```

### Test Execution

```bash
# All tests
xcodebuild test -scheme "BATbern-watch Watch App" \
  -destination 'platform=watchOS Simulator,name=Apple Watch Series 9 (45mm)'

# Unit tests only
xcodebuild test -scheme "BATbern-watch Watch App" \
  -destination 'platform=watchOS Simulator,name=Apple Watch Series 9 (45mm)' \
  -only-testing:BATbern-watch_Watch_AppTests

# UI tests only
xcodebuild test -scheme "BATbern-watch Watch App" \
  -destination 'platform=watchOS Simulator,name=Apple Watch Series 9 (45mm)' \
  -only-testing:BATbern-watch_Watch_AppUITests

# Specific test suite
xcodebuild test -scheme "BATbern-watch Watch App" \
  -destination 'platform=watchOS Simulator,name=Apple Watch Series 9 (45mm)' \
  -only-testing:BATbern-watch_Watch_AppTests/SessionTimerEngineTests

# Generate coverage report
xcodebuild test -scheme "BATbern-watch Watch App" \
  -destination 'platform=watchOS Simulator,name=Apple Watch Series 9 (45mm)' \
  -enableCodeCoverage YES
```

Or in Xcode: `Cmd+U` (all tests), or click the diamond next to a test.

## Backend Integration

### API Endpoints (Consumed)

**Public data (no auth):**
- `GET /api/v1/events/current?expand=sessions,speakers` — Event schedule

**Organizer endpoints (JWT required):**
- `POST /api/v1/watch/pair` — Authenticate and join event (Epic W2)
- `GET /api/v1/watch/events/{eventCode}/state` — Full state snapshot
- `GET /api/v1/watch/speakers/{speakerId}/portrait` — Download portrait
- `GET /api/v1/watch/events/active` — List events available to join

**WebSocket:**
- `wss://{backend}/ws/events/{eventCode}/live` — Real-time state sync (Epic W4)
- Subscribe: `/topic/events/{eventCode}/state`
- Publish: `/app/events/{eventCode}/action` (session advance, cascade)

**Backend changes:** All implemented in existing `event-management-service` (new package: `ch.batbern.events.watch`). See main platform docs for backend development.

### Flyway Migrations

**New columns on `sessions` table** (Epic W1):
- `actual_start_time TIMESTAMP` — Real start time (if overrun from previous)
- `actual_end_time TIMESTAMP` — Real end time (if advanced early)
- `overrun_minutes INTEGER` — Minutes over allocated time
- `completed_by_username VARCHAR(255)` — Organizer who advanced session

These migrations are owned by the backend team, not the Watch app.

## Documentation

### Comprehensive Planning Docs

All design and architecture documentation lives in `../../docs/watch-app/`:

- **[prd-batbern-watch.md](../../docs/watch-app/prd-batbern-watch.md)** — Functional and non-functional requirements
- **[architecture.md](../../docs/watch-app/architecture.md)** — Technical decisions, component design, API contracts
- **[ux-design-specification.md](../../docs/watch-app/ux-design-specification.md)** — Layouts, typography, color system, haptic vocabulary
- **[epics.md](../../docs/watch-app/epics.md)** — Epic breakdown and story decomposition

**When implementing new features:** Always consult these docs first. They define requirements, NFRs, and architectural constraints.

## OpenAPI Type Generation

### Workflow

Types are automatically generated from the BATbern OpenAPI specification:

```bash
# Regenerate types after API spec changes
./scripts/generate-types.sh

# Source: ../../docs/api/events-api.openapi.yml
# Output: BATbern-watch Watch App/Generated/Models/*.swift
```

**After regeneration:**
1. Open Xcode: `open BATbern-watch.xcodeproj`
2. Right-click "BATbern-watch Watch App" → **Add Files to...**
3. Select `Generated/Models/` folder
4. Ensure target membership is **BATbern-watch Watch App**
5. Build to verify: `Cmd+B`

### Generated vs Manual Models

- **Generated types** (`Generated/Models/`): API DTOs from OpenAPI spec (Event, Session, Speaker, etc.)
- **Manual models** (`Models/`): Local cache entities (CachedEvent, CachedSession), domain models (SessionState, HapticAlert)

**Rule:** Use generated types for API communication, manual types for local SwiftData persistence and domain logic.

See `OPENAPI_GENERATOR_GUIDE.md` for detailed migration guide.

## Localization

- **Base language:** English (`Base.lproj/Localizable.strings`)
- **Supported:** German (`de.lproj/Localizable.strings`)
- **Date formatting:** Swiss German conventions via `SwissDateFormatter`
- **Convention:** All user-facing strings use `NSLocalizedString("key", comment: "context")`

## Code Conventions

### Swift Style
- **Naming:** SwiftLint defaults (camelCase, PascalCase for types)
- **ViewModels:** Suffix with `ViewModel` (e.g., `PublicViewModel`)
- **Protocols:** Suffix with `Protocol` for dependency injection (e.g., `ClockProtocol`, `APIClientProtocol`)
- **Models:** Manual models in `Models/`, generated DTOs in `Generated/Models/`
- **Mocks:** Prefix with `Mock` in test target (e.g., `MockClock`, `MockAPIClient`)

### SwiftUI Patterns
- **Single responsibility:** Views display only, ViewModels contain logic
- **Environment objects:** Share `WebSocketService`, `BATbernAPIClient` via `.environmentObject()`
- **Combine:** Use `@Published` in ViewModels, `onReceive()` in Views
- **Previews:** Every View must have `#Preview` with mock data

### Dependency Injection Pattern

```swift
// ✅ Correct — protocol-based dependency injection
class SessionTimerEngine {
    let clock: ClockProtocol  // Injected dependency

    init(clock: ClockProtocol) {
        self.clock = clock
    }

    func checkRemaining() -> TimeInterval {
        return session.endTime.timeIntervalSince(clock.now)  // Uses protocol
    }
}

// Production: inject SystemClock
let engine = SessionTimerEngine(clock: SystemClock())

// Tests: inject MockClock
let mockClock = MockClock(fixedDate: testDate)
let engine = SessionTimerEngine(clock: mockClock)

// ❌ Wrong — direct dependency, untestable
class SessionTimerEngine {
    func checkRemaining() -> TimeInterval {
        return session.endTime.timeIntervalSince(Date())  // Hard-coded, can't control in tests
    }
}
```

### Error Handling
```swift
// ✅ Correct — graceful degradation
do {
    let state = try await apiClient.fetchEventState(eventCode)
    updateLocalCache(state)
} catch {
    logger.error("Failed to sync state: \(error.localizedDescription)")
    // Continue with cached state, show connectivity indicator
}

// ❌ Wrong — crashing on network error
let state = try! await apiClient.fetchEventState(eventCode)
```

### Async/Await
- **Use async/await** for all network calls (not completion handlers)
- **Task management:** Cancel tasks in `onDisappear()` to prevent leaks
- **Main actor:** All UI updates must be `@MainActor` or `await MainActor.run {}`

## Development Workflow

### Branch Strategy
- `main` — Stable releases (tagged with version)
- `develop` — Integration branch (matches main platform)
- `feature/W{epic}-{description}` — Feature branches (e.g., `feature/W1-xcode-scaffold`)

### Commit Messages
Follow main platform conventions:
```
feat(organizer): add session advance action
fix(haptics): prevent duplicate 5-min alerts
test(offline): verify action queue persistence
```

### Deployment
Watch apps ship via App Store Connect (separate from backend deployments):
1. Increment `CFBundleShortVersionString` in `Info.plist`
2. Archive in Xcode: Product → Archive
3. Upload to App Store Connect
4. Submit for TestFlight or App Store review

**Versioning:** Semantic versioning (e.g., `1.0.0`, `1.1.0`, `2.0.0`)

## Troubleshooting

### Simulator Issues
```bash
# Reset simulator
xcrun simctl erase all

# Boot specific Watch simulator
xcrun simctl boot "Apple Watch Series 9 (45mm)"

# Install app manually
xcodebuild install -scheme "BATbern-watch Watch App" \
  -destination 'platform=watchOS Simulator,name=Apple Watch Series 9 (45mm)'
```

### WebSocket Connection Failures
- **Check backend logs:** Ensure `event-management-service` is running
- **Verify JWT:** Decode token at jwt.io, check `exp` claim
- **ALB settings:** Confirm idle timeout is 3600s (not default 60s)
- **Simulator networking:** Disable Mac firewall or add Xcode exception

### Haptics Not Firing
- **Extended Runtime:** Verify `WKExtension.shared().isFrontmostTimeoutExtended == true`
- **Background modes:** Check `Info.plist` includes `WKBackgroundModes` array
- **Simulator limitation:** Haptics don't work in Simulator, test on device only

### SwiftData Migrations
- **Schema changes:** Delete app and reinstall to reset database
- **Production migration:** Use `VersionedSchema` and `MigrationPlan`
- **Debug queries:** Enable `-com.apple.CoreData.SQLDebug 1` launch argument

## Performance Benchmarks (NFRs)

When implementing features, ensure these targets are met:

| Metric | Target | How to Measure |
|--------|--------|----------------|
| Complication update latency | <1 second | Xcode Instruments: Time Profiler |
| Haptic delivery accuracy | <1 second | Wall-clock comparison (stopwatch) |
| Schedule cascade propagation | <3 seconds | Multi-device test with network logger |
| Initial sync time | <5 seconds | Time from join to first UI update |
| App launch to usable | <3 seconds | Time from tap to interactive UI |
| Battery after 3-hour event | >30% remaining | Xcode Energy Log over full session |

## Related Systems

**Main BATbern Platform:**
- Monorepo root: `../../`
- Backend services: `../../services/event-management-service/`
- API specs: `../../docs/api/`
- Local dev setup: `../../docs/guides/local-development-setup.md`

**Watch app is standalone** — changes here do NOT require rebuilding main platform (and vice versa).
