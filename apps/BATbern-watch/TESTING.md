# BATbern Watch — Test Framework

**Framework**: Swift Testing (unit) + XCTest (UI)
**Scaffolded**: 2026-02-15
**Coverage Target**: 80%+ business logic

## Directory Structure

```
BATbern-watch Watch AppTests/          # Unit tests (Swift Testing)
├── Domain/                            # Pure domain logic tests
│   ├── SessionTimerEngineTests.swift  # Wall-clock countdown (HIGH RISK)
│   └── HapticSchedulerTests.swift     # Threshold-based alert scheduling
├── Mocks/                             # Protocol-backed test doubles
│   ├── MockClock.swift                # Injectable time control
│   ├── MockAPIClient.swift            # REST client stub
│   ├── MockWebSocketClient.swift      # STOMP client stub with emit()
│   └── MockHapticService.swift        # Records haptic invocations
├── Factories/                         # Test data builders
│   └── TestDataFactory.swift          # Event, Session, Speaker factories
├── Helpers/                           # Test utilities
│   └── AsyncTestHelpers.swift         # Async wait, error assertions
└── BATbern_watch_Watch_AppTests.swift # Infrastructure smoke tests

BATbern-watch Watch AppUITests/        # UI tests (XCTest)
├── BATbern_watch_Watch_AppUITests.swift
└── BATbern_watch_Watch_AppUITestsLaunchTests.swift
```

## Xcode Setup (Required)

After scaffolding, add new files to the Xcode project:

1. Open `BATbern-watch.xcodeproj` in Xcode
2. For **app target** files (`Protocols/`, `Models/`, `Domain/`):
   - Right-click "BATbern-watch Watch App" group → **Add Files to...**
   - Select all folders: `Protocols/`, `Models/`, `Domain/`
   - Ensure target membership is **BATbern-watch Watch App**
3. For **test target** files (`Mocks/`, `Factories/`, `Helpers/`, `Domain/`):
   - Right-click "BATbern-watch Watch AppTests" group → **Add Files to...**
   - Select all folders under the test directory
   - Ensure target membership is **BATbern-watch Watch AppTests**
4. Build (`Cmd+B`) to verify no compilation errors

## Running Tests

```bash
# All tests
xcodebuild test -scheme "BATbern-watch Watch App" \
  -destination 'platform=watchOS Simulator,name=Apple Watch Series 9 (45mm)'

# Unit tests only
xcodebuild test -scheme "BATbern-watch Watch App" \
  -destination 'platform=watchOS Simulator,name=Apple Watch Series 9 (45mm)' \
  -only-testing:BATbern-watch_Watch_AppTests

# Specific test suite
xcodebuild test -scheme "BATbern-watch Watch App" \
  -destination 'platform=watchOS Simulator,name=Apple Watch Series 9 (45mm)' \
  -only-testing:BATbern-watch_Watch_AppTests/SessionTimerEngineTests

# With coverage
xcodebuild test -scheme "BATbern-watch Watch App" \
  -destination 'platform=watchOS Simulator,name=Apple Watch Series 9 (45mm)' \
  -enableCodeCoverage YES
```

Or in Xcode: `Cmd+U` (all tests), or click the diamond next to a test.

## Architecture: Protocol-Based Testability

Every external dependency has a protocol, enabling complete isolation in tests:

| Protocol | Production | Mock | Tests |
|---|---|---|---|
| `ClockProtocol` | `SystemClock` | `MockClock` | Timer accuracy, suspension survival |
| `APIClientProtocol` | `BATbernAPIClient` | `MockAPIClient` | API calls, error handling |
| `WebSocketClientProtocol` | `WebSocketService` | `MockWebSocketClient` | Sync, reconnection, offline queue |
| `HapticServiceProtocol` | `HapticEngine` | `MockHapticService` | Alert scheduling, deduplication |

### Pattern: Injectable Clock

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

### Pattern: Recording Mocks

Mocks record every invocation for assertion:

```swift
let haptics = MockHapticService()
// ... run code that triggers haptics ...
#expect(haptics.playedAlerts == [.fiveMinuteWarning, .twoMinuteWarning])
```

### Pattern: Configurable Results

API mocks use `Result<T, Error>` for deterministic test scenarios:

```swift
let api = MockAPIClient()
api.fetchCurrentEventResult = .success(TestData.event())
// or
api.fetchCurrentEventResult = .failure(MockError.unauthorized)
```

## Test Data Factories

All test data flows through `TestData` — a single source of realistic BATbern defaults:

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

## Risk-Ordered Testing Priority

Tests should be written in this order, matching risk profile:

| Priority | Component | Risk | Pattern |
|---|---|---|---|
| P0 | `SessionTimerEngine` | Timer drift on watchOS suspension | MockClock + wall-clock assertions |
| P0 | `HapticScheduler` | Missed/duplicate alerts during live event | MockClock + MockHapticService |
| P1 | WebSocket sync | 4-device state divergence | MockWebSocketClient + emit() |
| P1 | Offline `ActionQueue` | Lost actions on app restart | Persistence tests |
| P2 | `AuthManager` | JWT expiry mid-event | MockClock + token lifecycle |
| P2 | Public zone data loading | Stale cache, progressive publishing | MockAPIClient |
| P3 | UI navigation | Swipe zones, Crown scroll | XCTest UI tests |

## Adding New Tests

### 1. New Domain Test

```swift
import Testing
import Foundation
@testable import BATbern_watch_Watch_App

@Suite("MyNewComponent")
struct MyNewComponentTests {
    let clock: MockClock

    init() {
        clock = MockClock(fixedDate: Date(timeIntervalSince1970: 1_000_000))
    }

    @Test("Describes expected behavior")
    func behaviorUnderTest() {
        // Given
        let component = MyComponent(clock: clock)

        // When
        component.doSomething()

        // Then
        #expect(component.state == .expected)
    }
}
```

### 2. New Mock

Create a protocol in the app target, then implement the mock in tests:

```swift
// App target: Protocols/MyServiceProtocol.swift
protocol MyServiceProtocol: Sendable {
    func doWork() async throws -> Result
}

// Test target: Mocks/MockMyService.swift
final class MockMyService: MyServiceProtocol, @unchecked Sendable {
    var doWorkResult: Result<MyResult, Error> = .failure(MockError.notConfigured)
    private(set) var doWorkCallCount = 0

    func doWork() async throws -> MyResult {
        doWorkCallCount += 1
        return try doWorkResult.get()
    }
}
```

### 3. New Factory Data

Add to `TestDataFactory.swift`:

```swift
extension TestData {
    static func myModel(
        field: String = "default",
        // ... more fields with defaults
    ) -> MyModel {
        MyModel(field: field)
    }
}
```

## Key Testing Rules

1. **NEVER use `Date()` in production domain code** — always inject `ClockProtocol`
2. **NEVER use decrementing counters for timers** — always calculate from wall clock
3. **Every external dependency gets a protocol** — no concrete dependencies in domain/ViewModel layer
4. **Mocks record invocations** — assert on call count and arguments, not just return values
5. **Use `TestData.fixedSession(start:end:)` for timer tests** — relative dates cause flaky tests
6. **Reset mock state between tests** — Swift Testing creates fresh instances per `@Suite` init
