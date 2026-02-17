# Story W3.3: Watch Face Complications

Status: ready-for-dev

## Story

As an organizer,
I want to see countdown and speaker info on my Watch face without opening the app,
so that the complication is my primary interface during events.

## Acceptance Criteria

1. **AC1 — Three Complication Types**: Given I'm on my Watch face with BATbern complications installed, When a session is active, Then:
   - **C1 (`.accessoryCircular`)**: shows a circular progress ring with remaining minutes as a number in the center
   - **C2 (`.accessoryRectangular`)**: shows speaker name (top), countdown MM:SS (middle bold), progress bar (bottom)
   - **C3 (`.accessoryCorner`)**: shows countdown digits only (MM:SS), no other content

2. **AC2 — Sub-Second Update**: Given the complication is active, When the session countdown changes (each minute boundary, or on session state change), Then the complication updates within 1 second (NFR1) via `WidgetCenter.shared.reloadAllTimelines()`.

3. **AC3 — Always-On Display**: Given the Watch is in always-on display mode (wrist down), When I don't raise my wrist, Then the complication shows a dimmed countdown (reduced luminance, no color animation) — implemented via `.isLuminanceReduced` environment variable.

4. **AC4 — Deep Link to O3**: Given I tap the complication, When an event is active, Then the app opens directly to O3 (LiveCountdownView) in the organizer zone — via `widgetURL` link.

5. **AC5 — No Active Session Fallback**: Given no active session exists (event hasn't started or is complete), When the complication is visible, Then it shows the BATbern logo or app name (no countdown).

## Tasks / Subtasks

- [ ] **Task 1: Add Widget Extension Target in Xcode** (AC: 1, 2, 3, 4)
  - [ ] 1.1 Open `apps/BATbern-watch/BATbern-watch.xcodeproj` in Xcode
  - [ ] 1.2 File → New → Target → Widget Extension (watchOS)
  - [ ] 1.3 Product Name: `BATbern-watch Complications`
  - [ ] 1.4 **Uncheck** "Include Configuration Intent" (no user-configurable options needed)
  - [ ] 1.5 Embed the extension in: `BATbern-watch Watch App` target (not iPhone target if present)
  - [ ] 1.6 Add **App Group** capability to BOTH targets:
    - `BATbern-watch Watch App`: Signing & Capabilities → + → App Groups → `group.ch.batbern.watch`
    - `BATbern-watch Complications`: same group `group.ch.batbern.watch`
  - [ ] 1.7 Verify the `.appex` bundle is embedded in the Watch app's build phases
  - [ ] 1.8 Set `WATCHOS_DEPLOYMENT_TARGET = 11.0` in Widget Extension target settings

- [ ] **Task 2: Shared Data Layer (App Group Store)** (AC: 1, 2, 5)
  - [ ] 2.1 Create `apps/BATbern-watch/BATbern-watch Watch App/Data/ComplicationDataStore.swift`
  - [ ] 2.2 `struct ComplicationSnapshot: Codable` — data the complication reads:
    ```swift
    struct ComplicationSnapshot: Codable {
        let sessionTitle: String?
        let speakerNames: String?        // e.g. "Anna Meier" or "Meier, Müller"
        let scheduledEndTime: Date?      // used to compute remaining on each timeline entry
        let sessionDuration: TimeInterval? // for progress ring: (elapsed / duration)
        let scheduledStartTime: Date?
        let isLive: Bool                  // false → show fallback logo
        let urgencyLevel: String          // "normal" | "caution" | "warning" | "overtime"
        let updatedAt: Date
    }
    ```
  - [ ] 2.3 `enum ComplicationDataStore` with static read/write:
    ```swift
    enum ComplicationDataStore {
        static let appGroupID = "group.ch.batbern.watch"
        static let snapshotKey = "complication_snapshot"

        static func write(_ snapshot: ComplicationSnapshot) {
            guard let defaults = UserDefaults(suiteName: appGroupID),
                  let data = try? JSONEncoder().encode(snapshot) else { return }
            defaults.set(data, forKey: snapshotKey)
            // Notify complication to reload
            WidgetCenter.shared.reloadAllTimelines()
        }

        static func read() -> ComplicationSnapshot? {
            guard let defaults = UserDefaults(suiteName: appGroupID),
                  let data = defaults.data(forKey: snapshotKey),
                  let snapshot = try? JSONDecoder().decode(ComplicationSnapshot.self, from: data)
            else { return nil }
            return snapshot
        }
    }
    ```
  - [ ] 2.4 Add `import WidgetKit` to `ComplicationDataStore.swift` (needed for `WidgetCenter`)
  - [ ] 2.5 In `LiveCountdownViewModel.refreshState()` (from W3.1), after updating timer state, call:
    ```swift
    ComplicationDataStore.write(ComplicationSnapshot(
        sessionTitle: activeSession?.title,
        speakerNames: formattedSpeakerNames,
        scheduledEndTime: activeSession?.endTime,
        sessionDuration: activeSession?.duration,
        scheduledStartTime: activeSession?.startTime,
        isLive: activeSession != nil,
        urgencyLevel: urgencyLevel.rawValue,
        updatedAt: Date()
    ))
    ```

- [ ] **Task 3: Complication Types in Widget Extension** (AC: 1, 3, 4, 5)
  - [ ] 3.1 Create `apps/BATbern-watch/BATbern-watch Complications/BATbernComplicationsBundle.swift`:
    ```swift
    import WidgetKit
    import SwiftUI

    @main
    struct BATbernComplicationsBundle: WidgetBundle {
        var body: some Widget {
            CircularComplication()
            RectangularComplication()
            CornerComplication()
        }
    }
    ```
  - [ ] 3.2 Create `ComplicationEntry.swift` — shared timeline entry:
    ```swift
    struct ComplicationEntry: TimelineEntry {
        let date: Date
        let snapshot: ComplicationSnapshot?
        // Computed: remaining seconds at entry.date
        var remainingSeconds: TimeInterval? {
            guard let end = snapshot?.scheduledEndTime else { return nil }
            return max(0, end.timeIntervalSince(date))
        }
        var formattedCountdown: String {
            guard let remaining = remainingSeconds else { return "--:--" }
            let mins = Int(remaining) / 60
            let secs = Int(remaining) % 60
            return String(format: "%02d:%02d", mins, secs)
        }
        var progress: Double {
            guard let snapshot, let duration = snapshot.sessionDuration,
                  let start = snapshot.scheduledStartTime, duration > 0 else { return 0 }
            let elapsed = date.timeIntervalSince(start)
            return min(1.0, max(0.0, elapsed / duration))
        }
        var remainingMinutes: Int {
            Int((remainingSeconds ?? 0) / 60)
        }
    }
    ```
  - [ ] 3.3 Create `ComplicationProvider.swift` — `TimelineProvider`:
    ```swift
    struct ComplicationProvider: TimelineProvider {
        func placeholder(in context: Context) -> ComplicationEntry {
            ComplicationEntry(date: Date(), snapshot: nil)
        }

        func getSnapshot(in context: Context, completion: @escaping (ComplicationEntry) -> Void) {
            let snapshot = ComplicationDataStore.read()
            completion(ComplicationEntry(date: Date(), snapshot: snapshot))
        }

        func getTimeline(in context: Context, completion: @escaping (Timeline<ComplicationEntry>) -> Void) {
            let snapshot = ComplicationDataStore.read()
            guard let endTime = snapshot?.scheduledEndTime, endTime > Date() else {
                // No active session — single entry, reload when app signals
                let entry = ComplicationEntry(date: Date(), snapshot: snapshot)
                let timeline = Timeline(entries: [entry], policy: .never)
                completion(timeline)
                return
            }
            // Generate 1-minute entries until session end
            var entries: [ComplicationEntry] = []
            var current = Date()
            while current < endTime {
                entries.append(ComplicationEntry(date: current, snapshot: snapshot))
                current = current.addingTimeInterval(60) // 1-minute granularity
            }
            // Add overtime entry
            entries.append(ComplicationEntry(date: endTime, snapshot: snapshot))
            let timeline = Timeline(entries: entries, policy: .never)
            completion(timeline)
        }
    }
    ```
  - [ ] 3.4 Create `CircularComplication.swift` — C1 (`.accessoryCircular`):
    ```swift
    struct CircularComplication: Widget {
        let kind = "BATbernCircular"
        var body: some WidgetConfiguration {
            StaticConfiguration(kind: kind, provider: ComplicationProvider()) { entry in
                CircularView(entry: entry)
                    .widgetURL(URL(string: "batbern-watch://organizer/live"))
                    .containerBackground(.fill.tertiary, for: .widget)
            }
            .configurationDisplayName("BATbern Countdown")
            .description("Circular progress ring with remaining minutes.")
            .supportedFamilies([.accessoryCircular])
        }
    }

    struct CircularView: View {
        let entry: ComplicationEntry
        @Environment(\.isLuminanceReduced) private var isLuminanceReduced

        var body: some View {
            ZStack {
                if entry.snapshot?.isLive == true {
                    // Progress ring
                    ProgressView(value: entry.progress)
                        .progressViewStyle(.circular)
                        .tint(isLuminanceReduced ? .gray : urgencyColor)
                    Text("\(entry.remainingMinutes)")
                        .font(.system(size: 14, weight: .bold, design: .monospaced))
                        .foregroundStyle(isLuminanceReduced ? .gray : urgencyColor)
                } else {
                    // Fallback: BATbern icon
                    Image(systemName: "calendar.badge.clock")
                        .foregroundStyle(.secondary)
                }
            }
        }

        private var urgencyColor: Color {
            switch entry.snapshot?.urgencyLevel {
            case "caution": return .yellow
            case "warning", "critical": return .orange
            case "overtime": return .red
            default: return .teal
            }
        }
    }
    ```
  - [ ] 3.5 Create `RectangularComplication.swift` — C2 (`.accessoryRectangular`):
    - Top line: speaker name (`.caption`, truncated to 1 line)
    - Middle: countdown in MM:SS (`.title3` monospaced bold), colored by urgency
    - Bottom: `ProgressView(value: entry.progress)` linear bar
    - `.isLuminanceReduced` → gray tint, no color
  - [ ] 3.6 Create `CornerComplication.swift` — C3 (`.accessoryCorner`):
    - Single countdown text: MM:SS (`.title2` monospaced bold)
    - `.isLuminanceReduced` → gray
    - Note: `.accessoryCorner` has very limited space — digits only

- [ ] **Task 4: Deep Link Handling (O3 on complication tap)** (AC: 4)
  - [ ] 4.1 Register URL scheme `batbern-watch://` in `Info.plist` for the Watch app target:
    ```xml
    <key>CFBundleURLTypes</key>
    <array>
        <dict>
            <key>CFBundleURLSchemes</key>
            <array><string>batbern-watch</string></array>
        </dict>
    </array>
    ```
  - [ ] 4.2 Handle deep link in `BATbernWatchApp.swift` body:
    ```swift
    WindowGroup {
        ContentView()
            // ...
            .onOpenURL { url in
                if url.host == "organizer" && url.path == "/live" {
                    // Signal ContentView to select organizer zone
                    NotificationCenter.default.post(name: .openOrganizerZone, object: nil)
                }
            }
    }
    ```
  - [ ] 4.3 In `ContentView.swift`, listen for the notification and set `selectedZone = .organizer`

- [ ] **Task 5: Tests** (AC: 1, 2, 5)
  - [ ] 5.1 Create `apps/BATbern-watch/BATbern-watch Watch AppTests/Data/ComplicationDataStoreTests.swift`
  - [ ] 5.2 Test: `write()` then `read()` returns same `ComplicationSnapshot` (round-trip encode/decode)
  - [ ] 5.3 Test: `read()` returns nil when no data written
  - [ ] 5.4 Test: `ComplicationEntry.formattedCountdown` returns `"25:00"` for 1500s remaining
  - [ ] 5.5 Test: `ComplicationEntry.progress` returns `0.5` when halfway through session
  - [ ] 5.6 Test: `ComplicationEntry.progress` clamps to `[0.0, 1.0]`
  - [ ] 5.7 Test: `ComplicationProvider.getTimeline` generates 1-minute entries for a 45-min session (45 entries + overtime)
  - [ ] 5.8 Test: when `isLive == false`, timeline policy is `.never` (no wasted wake-ups)
  - [ ] 5.9 **Note:** WidgetKit views cannot be unit-tested with XCTest directly — visual verification only via Xcode Canvas previews and device testing

## Dev Notes

### What Already Exists — DO NOT Reinvent

| File | Status | Relation to W3.3 |
|---|---|---|
| `Domain/SessionTimerEngine.swift` | ✅ COMPLETE | Countdown math already done — DO NOT duplicate in complication. The complication computes `scheduledEndTime - entry.date` directly (same wall-clock principle) |
| `Domain/EventStateManager.swift` | ✅ COMPLETE | `isLive`, `currentEvent` — read from this to decide when to write to App Group store |
| `Models/WatchModels.swift` | ✅ COMPLETE | `UrgencyLevel` — use `.rawValue` for App Group serialization |
| `ViewModels/LiveCountdownViewModel.swift` | From W3.1 | **This is the write site** — call `ComplicationDataStore.write()` in `refreshState()` |
| `Complications/` folder | ⚠️ EMPTY | All complication Swift files are new |

### Xcode Project Setup — Cannot Be Done in Code

**Task 1 must be done manually in Xcode** — there is no way to add a Widget Extension target by editing `.pbxproj` directly (too error-prone). Steps:
1. Open `apps/BATbern-watch/BATbern-watch.xcodeproj`
2. Select the project in navigator → Add a target → Widget Extension
3. Check that the extension is added to the "Copy Bundle Resources" build phase of the Watch app target
4. Set App Group capability on both targets — Signing & Capabilities → + App Groups

**App Group provisioning note:** If using physical device, the App Group must be registered in the Apple Developer portal. For Simulator testing, App Groups work without portal registration.

### WidgetKit Families for watchOS 11

```
.accessoryCircular   → C1: 38x38pt watchOS space — ring + number
.accessoryRectangular → C2: larger horizontal space — speaker + countdown + bar
.accessoryCorner     → C3: small corner widget — digits only
.accessoryInline     → text-only (optional, not required by story)
```

Note: `.accessoryCorner` is only available on Apple Watch with watchOS 7+ (Series 4+). It may not appear on all watch faces. `C3` support is best-effort.

### Why Complication Uses Its Own Time Math (Not SessionTimerEngine)

`SessionTimerEngine` runs in the **main app process** via a 1-second timer. The complication extension runs in a **separate process** with no access to the main app's live state. Instead:
- Main app writes a snapshot (including `scheduledEndTime`) to the App Group store on each timer tick
- The complication `TimelineProvider` generates entries at 1-minute intervals, each computing `endTime - entry.date`
- On state change (new session, session end), main app calls `WidgetCenter.shared.reloadAllTimelines()` which triggers `getTimeline()` in the extension

This is the standard WidgetKit design — the extension is stateless and computes display from durable snapshot data.

### Timeline Policy `.never` vs `.atEnd`

- While a session is active: generate 1-minute entries up to `endTime`, policy `.never` (reload triggered by app via `WidgetCenter`)
- When no session: policy `.never` — don't wake up the extension until app signals
- Do NOT use `.atEnd` or time-based `.after()` reload — let the main app drive reloads via `reloadAllTimelines()` to preserve battery (NFR21-22)

### Always-On Display (AC3)

watchOS dims complications when the wrist is lowered (`isLuminanceReduced == true`). Handle by:
```swift
@Environment(\.isLuminanceReduced) var isLuminanceReduced
// Use .gray tint and remove animated effects when true
.tint(isLuminanceReduced ? .gray : urgencyColor)
```
Do not show color transitions or animations in reduced luminance mode — it would be annoying when the watch face dims.

### Deep Link Design Decision

Use a simple URL scheme for tap-to-open. `widgetURL(URL(string: "batbern-watch://organizer/live"))` passes the URL to the Watch app's `onOpenURL` modifier. The app then:
1. Sets `selectedZone = .organizer` in `ContentView`
2. The `OrganizerZoneView` routing logic (`isLive == true` → `LiveCountdownView`) handles the rest — no additional routing needed

This is simpler than `UserInfo` dictionaries or custom intent-based routing.

### WidgetCenter.shared.reloadAllTimelines() — Threading

Must be called from the **main app process only**, not from the widget extension. Call site is `LiveCountdownViewModel.refreshState()` (main actor). `WidgetCenter` is always safe to call from `@MainActor`.

### Complication Update Latency (NFR1 < 1 second)

`reloadAllTimelines()` requests a refresh — watchOS may delay by up to a few seconds under battery pressure. NFR1 is a best-effort target. The wall-clock timeline approach (pre-computed entries at 1-minute intervals) ensures the displayed number is always accurate even without a fresh reload, since each entry knows the end time and computes remaining at its own `date`.

For the *exact* second transitions (MM:SS), complications only update at entry boundaries — so a 1-minute granularity timeline shows correct minutes but the seconds counter only updates when `reloadAllTimelines()` is called (from the main app's 1-second timer). This is acceptable per architecture: the complication shows minutes, the app screen shows seconds.

### Speaker Names Formatting

```swift
// In LiveCountdownViewModel:
var formattedSpeakerNames: String {
    guard let session = activeSession else { return "" }
    let names = session.speakers.map { $0.lastName }
    return names.prefix(2).joined(separator: ", ")
}
```
`C2` (rectangular) is narrow — last names only for 2+ speakers. Single speaker: full name.

### Architecture Constraints

[Source: docs/watch-app/architecture.md#Enforcement-Guidelines]
- Never expose UUIDs in URLs — `batbern-watch://organizer/live` uses semantic path, not IDs
- WidgetKit timeline + Extended Runtime for complication updates < 1s
- Public zone data never in complication (organizer zone only)
- App Group identifier: `group.ch.batbern.watch` — use this exact string consistently

### Project Structure for New Files

```
apps/BATbern-watch/
├── BATbern-watch Watch App/
│   └── Data/
│       └── ComplicationDataStore.swift          ← NEW (main app target)
├── BATbern-watch Complications/                 ← NEW Widget Extension target
│   ├── BATbernComplicationsBundle.swift
│   ├── ComplicationEntry.swift
│   ├── ComplicationProvider.swift
│   ├── CircularComplication.swift               ← C1
│   ├── RectangularComplication.swift            ← C2
│   └── CornerComplication.swift                 ← C3
└── BATbern-watch Watch AppTests/
    └── Data/
        └── ComplicationDataStoreTests.swift     ← NEW
```

### References

- [Source: docs/watch-app/epics.md#W3.3] — AC definitions, FR1, FR2, FR5
- [Source: docs/watch-app/epics.md#NonFunctional] — NFR1 (complication < 1s), NFR21-22 (battery), NFR5 (launch time)
- [Source: docs/watch-app/architecture.md#Frontend-Architecture] — C1/C2/C3 layer diagram, WidgetKit timeline strategy
- [Source: docs/watch-app/architecture.md#Enforcement-Guidelines] — ADR-003, no UUIDs in URLs
- [Source: apps/BATbern-watch/CLAUDE.md#Architecture-Overview] — Complications/ empty placeholder, WidgetKit decision
- [Source: apps/BATbern-watch/CLAUDE.md#Key-Technical-Decisions] — Complication-first architecture rationale

## Dev Agent Record

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

### File List
