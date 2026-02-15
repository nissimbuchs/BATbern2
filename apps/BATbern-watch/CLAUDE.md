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

### MVVM + Repository Pattern

```
Views/               # SwiftUI views (no business logic)
  ├── Public/        # Unauthenticated screens
  └── Organizer/     # Authenticated organizer workflows

ViewModels/          # ObservableObject view models (presentation logic)
  ├── SessionViewModel.swift       # Countdown timer, state display
  ├── ScheduleViewModel.swift      # Full schedule navigation
  └── HapticViewModel.swift        # Alert coordination

Models/              # SwiftData models (local cache)
  ├── Event.swift
  ├── Session.swift
  └── Speaker.swift

Services/            # External dependencies and infrastructure
  ├── BATbernAPIClient.swift       # REST endpoints
  ├── WebSocketService.swift       # STOMP real-time sync
  ├── HapticEngine.swift           # WKHapticType wrappers
  └── OfflineQueue.swift           # Action buffering
```

### Critical Architectural Patterns

**1. Server-Authoritative State Model**
- Backend is the source of truth for event state
- Local SwiftData cache is read-only mirror
- Actions (advance session, trigger cascade) sent to server via STOMP
- Server broadcasts state changes to all connected devices
- Last-write-wins conflict resolution on server side

**2. Offline-First Design**
- All event data cached locally after initial sync (SwiftData)
- Countdown timer and haptics run locally (wall-clock based, not network-dependent)
- Actions queued locally when offline (`OfflineQueue.swift`)
- Automatic sync when connectivity restored
- No user action required for offline transition

**3. Real-Time Synchronization**
- STOMP over WebSocket for low-latency broadcasts
- JWT authentication in STOMP CONNECT headers
- Server broadcasts to `/topic/events/{eventCode}/state`
- Clients subscribe on join, single persistent connection
- REST fallback endpoints for reconnection scenarios

**4. Battery-Aware Behavior**
- Extended Runtime session for background haptics
- Adaptive polling frequency (reduce when battery < 20%)
- Single persistent WebSocket (no frequent reconnects)
- Lazy loading of speaker portraits (fetch on-demand, not bulk)
- Target: >30% battery remaining after 3-hour event

**5. Complication-First Architecture**
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

### Unit Tests (`BATbern-watch Watch AppTests/`)
- **ViewModels:** Countdown timer accuracy, state transitions, offline queue behavior
- **Services:** API client request construction, STOMP message parsing, haptic scheduling
- **Models:** SwiftData relationships, cascade recalculation logic
- **Coverage target:** 80%+ for business logic

### UI Tests (`BATbern-watch Watch AppUITests/`)
- **Critical paths:** Authentication flow, session advance, schedule cascade
- **Complication updates:** Verify content reflects state changes within 1 second
- **Offline scenarios:** Trigger airplane mode, verify timer continues, actions queue
- **Accessibility:** VoiceOver navigation, Dynamic Type, Reduce Motion

### Integration Testing
- **Mock backend:** Stub STOMP server for multi-device sync testing
- **Battery simulation:** Xcode Energy Log to validate power consumption
- **Network conditions:** Network Link Conditioner (slow WiFi, packet loss)
- **Extended Runtime:** Run 3-hour continuous session to verify NFR6 (zero crashes)

### Test Execution
```bash
# All tests
xcodebuild test -scheme "BATbern-watch Watch App" \
  -destination 'platform=watchOS Simulator,name=Apple Watch Series 9 (45mm)'

# UI tests only
xcodebuild test -scheme "BATbern-watch Watch App" \
  -destination 'platform=watchOS Simulator,name=Apple Watch Series 9 (45mm)' \
  -only-testing:BATbern-watch_Watch_AppUITests

# Generate coverage report
xcodebuild test -scheme "BATbern-watch Watch App" \
  -destination 'platform=watchOS Simulator,name=Apple Watch Series 9 (45mm)' \
  -enableCodeCoverage YES
```

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

## Code Conventions

### Swift Style
- **Naming:** SwiftLint defaults (camelCase, PascalCase for types)
- **ViewModels:** Suffix with `ViewModel` (e.g., `SessionViewModel`)
- **Services:** Suffix with `Service` or descriptive noun (e.g., `WebSocketService`, `HapticEngine`)
- **Models:** Match backend DTOs where possible (e.g., `Session`, `Event`)

### SwiftUI Patterns
- **Single responsibility:** Views display only, ViewModels contain logic
- **Environment objects:** Share `WebSocketService`, `BATbernAPIClient` via `.environmentObject()`
- **Combine:** Use `@Published` in ViewModels, `onReceive()` in Views
- **Previews:** Every View must have `#Preview` with mock data

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
