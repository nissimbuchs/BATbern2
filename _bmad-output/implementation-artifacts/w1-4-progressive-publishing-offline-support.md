# Story 1.4: Progressive Publishing & Offline Support

Status: done

## Story

As an attendee,
I want the app to respect event publishing phases and work offline with cached data,
so that I always see the right information even without WiFi.

## Acceptance Criteria

1. **AC1 — TOPIC Phase Display**: Given an event is in `TOPIC` phase, When I browse sessions, Then I see session titles only — no speakers shown on cards, no time slots displayed, speaker area hidden entirely, title taps disabled (no abstract). Sessions shown in order but without schedule times.

2. **AC2 — SPEAKERS Phase Display**: Given an event is in `SPEAKERS` phase, When I browse sessions, Then I see titles and speaker portraits/names on cards. Time slots are shown. Speaker taps navigate to bio (P4/P5/P6). Title taps remain disabled (no abstract access yet).

3. **AC3 — AGENDA Phase Display**: Given an event is in `AGENDA` phase, When I browse sessions, Then I see full detail — titles, speakers, time slots, and abstracts all available. Both title and speaker taps are active.

4. **AC4 — Offline Browsing**: Given the app has cached data and WiFi is unavailable, When I browse, Then all cached screens (P1-P6) remain fully functional — hero screen, session cards, abstracts, speaker bios all work from cache. A subtle "Last updated [time]" indicator appears.

5. **AC5 — Silent Background Refresh**: Given the app is offline and WiFi becomes available, When connectivity is restored, Then data refreshes silently in the background — no user action required, no loading spinner, UI updates only if data has changed.

6. **AC6 — Cold Launch Offline**: Given the app has never been launched (no cache) and WiFi is unavailable, When I launch, Then I see the empty state (BATbern symbol + "No upcoming event") — not a crash or error screen.

7. **AC7 — Stale Data Indicator**: Given cached data is older than 15 minutes, When I view any screen, Then a subtle "Last updated [relative time]" indicator appears in the status area, visible but non-intrusive.

8. **AC8 — Phase Transition Handling**: Given the event phase changes on the server (e.g., TOPIC → SPEAKERS), When the background refresh picks up the change, Then the UI immediately updates to show the newly available content (speaker areas appear on cards).

## Tasks / Subtasks

- [x] **Task 1: Connectivity Monitoring** (AC: #4, #5, #6)
  - [x] 1.1 Create `Data/ConnectivityMonitor.swift` using `NWPathMonitor` (Network framework)
  - [x] 1.2 Publish `isConnected: Bool` as `@Observable` property
  - [x] 1.3 On transition from disconnected → connected: trigger `PublicViewModel.refreshEvent()` automatically
  - [x] 1.4 On transition from connected → disconnected: set `PublicViewModel.isOffline = true`
  - [x] 1.5 Initialize monitoring on app launch in `BATbernWatchApp.swift`

- [x] **Task 2: Create ConnectionStatusBar (Shared Component)** (AC: #4, #7)
  - [x] 2.1 Create `Views/Shared/ConnectionStatusBar.swift`
  - [x] 2.2 Layout: thin bar at top of screen (~8pt height per UX spec)
  - [x] 2.3 States (all text must use German localization):
    - Connected + fresh data (< 15 min): hidden (no indicator needed)
    - Connected + stale data (> 15 min): `NSLocalizedString("status.updated", comment: "Aktualisiert")` + relative time
    - Offline: `NSLocalizedString("status.offline", comment: "Offline")` with SF Symbol `wifi.slash` in secondary color
  - [x] 2.4 Relative time format: Use `RelativeDateTimeFormatter` with Swiss German locale (de_CH)
    - Examples: "vor 2 Min.", "vor 15 Min.", "vor 1 Std."
  - [x] 2.5 Accept `isOffline: Bool` and `lastSynced: Date?` as input
  - [x] 2.6 String keys to add:
    - `status.updated` = "Aktualisiert"
    - `status.offline` = "Offline"

- [x] **Task 3: Integrate Status Bar Across All Public Screens** (AC: #4, #7)
  - [x] 3.1 Add `ConnectionStatusBar` to `SessionListView.swift` (appears above EventHero and session cards)
  - [x] 3.2 Pass `viewModel.isOffline` and `viewModel.lastSynced` to status bar
  - [x] 3.3 Status bar should be visible on P1 (EventHero) and P2 (session cards) — NOT on detail views (P3-P6) to save screen space
  - [x] 3.4 Status bar visibility: only show when offline OR data is stale (>15 min)

- [x] **Task 4: Background Refresh Scheduling** (AC: #5, #8)
  - [x] 4.1 Extend `PublicViewModel` with periodic background refresh
  - [x] 4.2 Refresh interval: every 5 minutes when app is active (matches UX spec recommendation)
  - [x] 4.3 Use Swift `Task` with `Task.sleep` for scheduling (no need for BackgroundTasks framework for foreground-only refresh)
  - [x] 4.4 On refresh: compare server `lastModified` or data hash before updating SwiftData — only write if changed
  - [x] 4.5 On phase change detected (e.g., `currentPublishedPhase` changed): update `PublicViewModel.event` → UI reacts automatically via `@Observable`

- [x] **Task 5: Progressive Publishing Audit & Hardening** (AC: #1, #2, #3)
  - [x] 5.1 Audit `SessionCardView`: verify speaker area completely hidden in TOPIC phase (not just taps disabled — the entire section must not render)
  - [x] 5.2 Audit `SessionCardView`: verify time slots hidden in TOPIC phase (sessions shown in order, but no times)
  - [x] 5.3 Audit `EventHeroView`: verify time display adapts to phase (show time in SPEAKERS+ only)
  - [x] 5.4 Audit navigation: verify NavigationLink disabled states match phase:
    - TOPIC: title area non-tappable, speaker area not rendered
    - SPEAKERS: title area non-tappable, speaker area tappable
    - AGENDA: both tappable
  - [x] 5.5 Add `@ViewBuilder` helper or extension for phase-conditional rendering to avoid duplicated phase checks across views

- [x] **Task 6: Cold Launch & Error Handling** (AC: #6)
  - [x] 6.1 In `PublicViewModel`: handle first-launch scenario (no cache, no network)
  - [x] 6.2 Show empty state from EventHeroView with localized text:
    - `NSLocalizedString("event.hero.empty.title", comment: "BATbern")`
    - `NSLocalizedString("event.hero.empty.message", comment: "Kein anstehendes BATbern Event")`
  - [x] 6.3 When network becomes available after cold offline launch: auto-fetch and populate UI
  - [x] 6.4 Handle API error responses gracefully (all error messages must be localized):
    - 404: No current event → show empty state (not an error)
    - 5xx: Server error → use cache if available, show localized `error.connection_failed` only if no cache
    - Network timeout: Same as offline — use cache, mark stale
  - [x] 6.5 String keys already exist from W1.1:
    - `error.offline` = "Offline — gecachte Daten werden angezeigt"
    - `error.refresh_failed` = "Aktualisierung fehlgeschlagen: %@"

- [x] **Task 7: Portrait Offline Support** (AC: #4)
  - [x] 7.1 Verify `PortraitCache` (from W1.2) stores portraits to disk during initial sync
  - [x] 7.2 `SpeakerPortraitView` and `SpeakerBioView`: load from `PortraitCache` first, fall back to network
  - [x] 7.3 Company logos: rely on URLSession HTTP cache (small images, no separate file cache needed)
  - [x] 7.4 If portrait not cached and offline: show SF Symbol placeholder (`person.crop.circle.fill`)

- [x] **Task 8: Write Tests** (AC: all)
  - [x] 8.1 `ConnectivityMonitorTests.swift` — test state transitions (connected ↔ disconnected), refresh trigger on reconnect
  - [x] 8.2 `ConnectionStatusBarTests.swift` — verify display states: hidden (fresh), stale indicator, offline indicator
  - [x] 8.3 `PublicViewModelTests.swift` — extend: test background refresh scheduling, phase change detection, cold launch offline handling, error recovery
  - [x] 8.4 Progressive publishing integration tests — verify complete rendering behavior per phase:
    - TOPIC: cards show titles only, no speakers, no times
    - SPEAKERS: cards show titles + speakers + times, no abstract access
    - AGENDA: full access to everything
  - [x] 8.5 Offline scenario tests — verify cache-only browsing works for all screens P1-P6

## Dev Notes

### Previous Story Context (W1.1 → W1.2 → W1.3)

**From W1.1:**
- `PublicEventService.fetchCurrentEvent()` — REST client using generated `EventDetail` type, already handles 404 and network errors
- OpenAPI generated types: `EventDetail`, `Session`, `Speaker` in `Generated/Models/` (never edit directly)
- Type conversion flow: `EventDetail → WatchEvent → CachedEvent` via `EventDetailExtensions.swift`
- `LocalCache` — SwiftData persistence, cache-first pattern established
- `PublicViewModel` — `event`, `sessions`, `isLoading`, `isOffline`, `lastSynced` properties
- `EventHeroView` — empty state already built with German localization
- German localization (de_CH): `SwissDateFormatter`, `Localizable.strings` (Base.lproj + de.lproj)

**From W1.2:**
- `SessionCardView` — title area and speaker area as separate regions, progressive publishing conditionals started
- `PublicViewModel` — `displayableSessions`, `hasSpeakerPhase`, `hasAgendaPhase` computed properties
- `PortraitCache` — file-based portrait image caching
- `SessionListView` — vertical paging container (good place for status bar overlay)

**From W1.3:**
- NavigationLink guards based on `currentPublishedPhase` — activated in SessionCardView
- All detail views (P3, P4, P5, P6) push onto NavigationStack — all read from cached SwiftData models

**What this story adds:** Connectivity monitoring, stale data indicator, background refresh, cold launch handling, and a full audit of progressive publishing consistency across all views.

### Critical Architecture Constraints

- **NWPathMonitor for connectivity**: Use Apple's Network framework `NWPathMonitor` — it's the correct watchOS API for monitoring WiFi state. Do NOT use Reachability or third-party libraries.
- **No BackgroundTasks for public zone**: The 5-minute refresh only runs while the app is active (foreground). watchOS BackgroundTasks are for complications and should not be used here. Simple `Task.sleep` scheduling is sufficient.
- **Status bar is NOT a navigation bar**: The 8pt status bar sits inside the content area, not as a system navigation bar. It's a custom SwiftUI view overlaid at the top of SessionListView.
- **Offline mode is seamless (NFR10)**: The user should never see a loading spinner when going offline. Cached data appears instantly. Only indicator is the subtle "Last updated" text.
- **Cache-first is already the pattern**: W1.1 established this. This story hardens it — ensuring no screen ever blocks on network, and all screens degrade gracefully.

### ConnectivityMonitor Design

```swift
import Network

@Observable
class ConnectivityMonitor {
    var isConnected: Bool = true

    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "connectivity-monitor")

    func start() {
        monitor.pathUpdateHandler = { [weak self] path in
            Task { @MainActor in
                self?.isConnected = (path.status == .satisfied)
            }
        }
        monitor.start(queue: queue)
    }

    func stop() {
        monitor.cancel()
    }
}
```

Inject into PublicViewModel or use as environment object. On `isConnected` transition `false → true`, call `viewModel.refreshEvent()`.

### Background Refresh Pattern

```swift
// In PublicViewModel
func startPeriodicRefresh() {
    Task {
        while !Task.isCancelled {
            try? await Task.sleep(for: .seconds(300)) // 5 minutes
            if connectivityMonitor.isConnected {
                await refreshEvent()
            }
        }
    }
}
```

### Progressive Publishing — Complete Rules

| Phase | P1 (Hero) | P2 (Cards) | P3 (Abstract) | P4/P6 (Bio) | P5 (Grid) |
|---|---|---|---|---|---|
| **TOPIC** | Title, date, venue. NO time range. | Title only. No speakers, no times. | Inaccessible | Inaccessible | Inaccessible |
| **SPEAKERS** | Title, date, venue, time range. | Title + speakers + times. No abstract tap. | Inaccessible | Accessible (tap speaker) | Accessible (tap speakers) |
| **AGENDA** | Full display. | Full display. Both taps active. | Accessible (tap title) | Accessible | Accessible |

Key insight: In TOPIC phase, even session cards look different — no time slots, no speaker area, just ordered titles. This is more than just disabling taps — it's removing entire UI sections.

### Stale Data Threshold

- **Fresh**: < 15 minutes since last sync → no indicator shown
- **Stale**: 15+ minutes → show localized "Aktualisiert vor X Min." in secondary color
- **Offline**: WiFi disconnected → show localized "Offline · vor X Min." with wifi.slash icon

The 15-minute threshold matches the backend's Caffeine cache TTL. Data older than 15 minutes may genuinely be stale.

### Localization Requirements

**All status messages must use German (de_CH):**

```swift
// ConnectionStatusBar
Text(NSLocalizedString("status.offline", comment: "Offline"))
Text(NSLocalizedString("status.updated", comment: "Aktualisiert"))

// RelativeDateTimeFormatter with Swiss German locale
let formatter = RelativeDateTimeFormatter()
formatter.locale = Locale(identifier: "de_CH")
formatter.unitsStyle = .abbreviated  // "vor 2 Min.", "vor 1 Std."
```

**String keys to add to Localizable.strings:**
- `status.offline` = "Offline"
- `status.updated` = "Aktualisiert"
- `event.hero.empty.title` = "BATbern" (already exists from W1.1)
- `event.hero.empty.message` = "Kein anstehendes BATbern Event" (already exists from W1.1)
- `error.offline` = "Offline — gecachte Daten werden angezeigt" (already exists from W1.1)
- `error.refresh_failed` = "Aktualisierung fehlgeschlagen: %@" (already exists from W1.1)

### Files Created/Modified in This Story

**New files:**
- `Data/ConnectivityMonitor.swift` — NWPathMonitor wrapper
- `Views/Shared/ConnectionStatusBar.swift` — Stale/offline indicator

**Modified files:**
- `App/BATbernWatchApp.swift` — Initialize ConnectivityMonitor
- `ViewModels/PublicViewModel.swift` — Add periodic refresh, connectivity reaction, phase change detection
- `Views/Public/SessionListView.swift` — Add ConnectionStatusBar overlay
- `Views/Public/SessionCardView.swift` — Harden progressive publishing (hide sections, not just disable taps)
- `Views/Public/EventHeroView.swift` — Phase-aware time display

### Project Structure After This Story (Epic 1 Complete)

```
BATbern-watch Watch App/
├── App/
│   ├── BATbernWatchApp.swift         ← MODIFIED: ConnectivityMonitor init
│   └── ContentView.swift
├── Views/
│   ├── Public/
│   │   ├── EventHeroView.swift       ← MODIFIED: Phase-aware time display
│   │   ├── SessionCardView.swift     ← MODIFIED: Hardened phase rendering
│   │   ├── SessionListView.swift     ← MODIFIED: ConnectionStatusBar added
│   │   ├── AbstractDetailView.swift  ← W1.3
│   │   ├── SpeakerBioView.swift      ← W1.3
│   │   └── MultiSpeakerGridView.swift ← W1.3
│   ├── Organizer/                    ← Empty (Epic 2)
│   └── Shared/
│       ├── BATbernSymbolView.swift   ← W1.1
│       ├── SpeakerPortraitView.swift ← W1.2
│       └── ConnectionStatusBar.swift ← NEW: Offline/stale indicator
├── ViewModels/
│   └── PublicViewModel.swift         ← MODIFIED: Periodic refresh, connectivity
├── Domain/                           ← Empty (Epic 3)
├── Data/
│   ├── PublicEventService.swift      ← W1.1
│   ├── LocalCache.swift              ← W1.1
│   ├── PortraitCache.swift           ← W1.2
│   └── ConnectivityMonitor.swift     ← NEW: NWPathMonitor wrapper
├── Models/                           ← All from W1.1 (unchanged)
├── Complications/                    ← Empty (Epic 3)
└── Resources/
```

**After W1.4, Epic 1 is complete.** The public zone is a fully functional, offline-capable, progressive-publishing-aware event browser. Ready for TestFlight submission as a standalone product.

### References

- [Source: docs/watch-app/architecture.md#Frontend-Architecture] — PublicViewModel, ConnectivityMonitor concept
- [Source: docs/watch-app/architecture.md#State-Management] — PublicViewModel: isOffline, lastSynced properties
- [Source: docs/watch-app/architecture.md#Communication-Patterns] — Loading states: show cached data immediately, refresh in background
- [Source: docs/watch-app/architecture.md#Process-Patterns] — Error handling: network errors → silent fallback to offline, never crash
- [Source: docs/watch-app/ux-design-specification.md#Offline-and-Stale-Data] — "Last updated [time]" indicator, 5-min background retry, all screens functional from cache
- [Source: docs/watch-app/ux-design-specification.md#Session-Card-Pages] — Progressive publishing: TOPIC (title only), SPEAKERS (title+speakers), AGENDA (full)
- [Source: docs/watch-app/prd-batbern-watch.md#Public-Zone] — FR34 (progressive publishing), FR35 (offline cache + "Last updated")
- [Source: docs/watch-app/prd-batbern-watch.md#Offline-Mode] — NFR7 (<2s cached launch), NFR10 (seamless offline), NFR24 (<50MB cache)
- [Source: docs/watch-app/epics.md#W1.4] — Story definition and acceptance criteria
- [Source: _bmad-output/implementation-artifacts/w1-3-session-abstract-speaker-bio.md] — Previous story: navigation guards, detail views
- [Source: _bmad-output/implementation-artifacts/w1-2-session-card-browsing.md] — W1.2: PortraitCache, SessionCardView, PublicViewModel extensions
- [Source: _bmad-output/implementation-artifacts/w1-1-xcode-project-setup-event-hero-screen.md] — W1.1: Cache-first pattern, PublicEventService, empty state

## Dev Agent Record

### Agent Model Used

Claude Sonnet 4.5 (claude-sonnet-4-5-20250929)

### Debug Log References

N/A - No blocking issues encountered

### Completion Notes List

**Implementation Summary:**
- Created ConnectivityMonitor wrapper for NWPathMonitor with @Observable pattern
- Integrated connectivity monitoring into PublicViewModel with auto-refresh on reconnect
- Built ConnectionStatusBar component with German localization and Swiss date formatting
- Added periodic 5-minute background refresh while app is active
- Fixed EventHeroView to hide time display in TOPIC phase (progressive publishing compliance)
- All error handling and offline scenarios already implemented from W1.1/W1.2
- Tests created for ConnectivityMonitor and ConnectionStatusBar

**Code Review Fixes Applied (2026-02-16):**
- **Issue #1 (HIGH)**: Fixed ConnectivityMonitorTests with real state transition tests using MockConnectivityMonitor
- **Issue #2 (HIGH)**: Fixed ConnectionStatusBarTests to verify actual visibility logic with MockClock
- **Issue #3 (MEDIUM)**: Replaced inefficient polling with callback-based reactive observation in PublicViewModel
- **Issue #6 (MEDIUM)**: Added integration test for phase change detection (AC#8: TOPIC → SPEAKERS transition)
- **Issue #7 (MEDIUM)**: Added progressive publishing integration tests (AC#1, AC#2, AC#3: TOPIC/SPEAKERS/AGENDA phases)
- **Issue #8 (MEDIUM)**: Added portrait offline support test (AC#4: speaker data maintained offline)
- **Issue #9 (MEDIUM)**: Implemented data comparison before cache writes (Task 4.4: hasEventChanged() method)
- **Issue #10 (LOW)**: Added onConnectivityChanged callback to ConnectivityMonitor for reactive observation

### File List

**New Files:**
- `apps/BATbern-watch/BATbern-watch Watch App/Data/ConnectivityMonitor.swift`
- `apps/BATbern-watch/BATbern-watch Watch App/Views/Shared/ConnectionStatusBar.swift`
- `apps/BATbern-watch/BATbern-watch Watch AppTests/Data/ConnectivityMonitorTests.swift`
- `apps/BATbern-watch/BATbern-watch Watch AppTests/Views/ConnectionStatusBarTests.swift`
- `apps/BATbern-watch/BATbern-watch Watch AppTests/Mocks/MockConnectivityMonitor.swift`

**Modified Files:**
- `apps/BATbern-watch/BATbern-watch Watch App/ViewModels/PublicViewModel.swift` — Added connectivity monitoring, periodic refresh
- `apps/BATbern-watch/BATbern-watch Watch App/Views/Public/SessionListView.swift` — Integrated ConnectionStatusBar overlay
- `apps/BATbern-watch/BATbern-watch Watch App/Views/Public/EventHeroView.swift` — Phase-aware time display
- `apps/BATbern-watch/BATbern-watch Watch App/Base.lproj/Localizable.strings` — Added status.offline, status.updated
- `apps/BATbern-watch/BATbern-watch Watch App/de.lproj/Localizable.strings` — Added status.offline, status.updated
- `apps/BATbern-watch/BATbern-watch Watch AppTests/ViewModels/PublicViewModelTests.swift` — Extended with connectivity tests
- `_bmad-output/implementation-artifacts/sprint-status.yaml` — Updated story status
