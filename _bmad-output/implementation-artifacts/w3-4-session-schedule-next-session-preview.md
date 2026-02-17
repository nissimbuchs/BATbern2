# Story W3.4: Session Schedule & Next Session Preview

Status: ready-for-dev

## Story

As an organizer,
I want to scroll through the full schedule in the organizer zone and see what's next,
so that I can plan transitions and introductions.

## Acceptance Criteria

1. **AC1 — Session Timeline (O7)**: Given I'm in the organizer zone during an event, When I tap the "Schedule" button in O3, Then I see the session timeline screen (O7) with all sessions listed in order, each showing its status badge: "Completed" (gray), "Active" (blue/accent), or "Upcoming" (secondary).

2. **AC2 — Next Session Preview in O3**: Given the current session is active, When I view O3 (LiveCountdownView), Then I see a next session preview section below the countdown: small speaker portrait thumbnail, speaker name, and talk title — truncated to 1 line each.

3. **AC3 — Crown Back Navigation from O7**: Given I'm on O7 (SessionScheduleView), When I press the Digital Crown (or swipe left), Then I return to the active session view (O3) — standard watchOS NavigationStack back navigation.

4. **AC4 — Completed Session Duration in O7**: Given a session is completed, When I view it in O7, Then it shows "Completed" status badge and its actual duration (e.g., "42 min") using `actualEndTime - actualStartTime` if available, or `endTime - startTime` as fallback.

5. **AC5 — No Active Session Handling**: Given there is no next session (e.g., last session is active), When I view O3, Then the next session preview section is hidden (not shown).

6. **AC6 — Crown-Scrollable Schedule**: Given I'm on O7, When I rotate the Digital Crown, Then the session list scrolls smoothly — standard watchOS List / ScrollView Crown integration, <100ms per page (NFR6).

## Tasks / Subtasks

- [ ] **Task 1: Add "View Schedule" Navigation from LiveCountdownView (O3)** (AC: 1, 3)
  - [ ] 1.1 Wrap `LiveCountdownView` in a `NavigationStack` **in `OrganizerZoneView`** (currently it's a raw `LiveCountdownView()` call — add `NavigationStack { LiveCountdownView() }`)
  - [ ] 1.2 In `LiveCountdownView.swift`, add a toolbar or bottom button "Schedule" that navigates to `SessionScheduleView`:
    ```swift
    .toolbar {
        ToolbarItem(placement: .bottomBar) {
            NavigationLink("Schedule", destination: SessionScheduleView())
        }
    }
    ```
    Or if screen space is tight, use a chevron-right affordance below the next session preview (see Task 2).
  - [ ] 1.3 Verify `NavigationStack` wrapping in `OrganizerZoneView.swift` does NOT affect other zone branches (O1 PairingView, O2 SpeakerArrivalView, EventPreviewView) — wrap only the `isLive` branch, or use a common NavigationStack at the zone level
  - [ ] 1.4 **Recommended structure:**
    ```swift
    } else if eventState.isLive {
        NavigationStack {
            LiveCountdownView()
        }
    }
    ```

- [ ] **Task 2: Add Next Session Preview to LiveCountdownView (O3)** (AC: 2, 5)
  - [ ] 2.1 `LiveCountdownViewModel` already has `nextSession: WatchSession?` as a published property (from W3.1 spec, Task 1.9). Verify it's populated:
    ```swift
    // In refreshState() or dedicated computation:
    nextSession = event.sessions
        .compactMap { $0.toWatchSession() }
        .first(where: { $0.startTime > (activeSession?.endTime ?? clock.now) && $0.sessionType != .break_ && $0.sessionType != .networking })
    ```
  - [ ] 2.2 In `LiveCountdownView.swift`, add `NextSessionPreview` sub-view below the main countdown ZStack, shown only when `viewModel.nextSession != nil`:
    ```swift
    if let next = viewModel.nextSession {
        NextSessionPreviewView(session: next)
    }
    ```
  - [ ] 2.3 Create `apps/BATbern-watch/BATbern-watch Watch App/Views/Organizer/NextSessionPreviewView.swift`:
    ```swift
    struct NextSessionPreviewView: View {
        let session: WatchSession

        var body: some View {
            HStack(spacing: 6) {
                // Speaker portrait thumbnail (first speaker only)
                if let speaker = session.speakers.first {
                    SpeakerPortraitView(
                        speakerId: speaker.id,
                        size: 24  // small thumbnail
                    )
                    .clipShape(Circle())
                }
                VStack(alignment: .leading, spacing: 1) {
                    Text("Next")
                        .font(.system(size: 10))
                        .foregroundStyle(.secondary)
                    Text(session.speakers.first.map { "\($0.firstName) \($0.lastName)" } ?? "")
                        .font(.system(size: 11, weight: .semibold))
                        .lineLimit(1)
                    Text(session.title)
                        .font(.system(size: 10))
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
                Spacer()
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 8))
        }
    }
    ```
  - [ ] 2.4 Position `NextSessionPreviewView` at the bottom of `LiveCountdownView`'s VStack, below the progress ring, with a small tap affordance leading to `SessionScheduleView` (use as a `NavigationLink` wrapper):
    ```swift
    NavigationLink(destination: SessionScheduleView()) {
        NextSessionPreviewView(session: next)
    }
    .buttonStyle(.plain)
    ```
  - [ ] 2.5 `#Preview` update — add a session with a `nextSession` populated to verify layout renders correctly

- [ ] **Task 3: Create SessionScheduleView (O7)** (AC: 1, 3, 4, 6)
  - [ ] 3.1 Create `apps/BATbern-watch/BATbern-watch Watch App/Views/Organizer/SessionScheduleView.swift`
  - [ ] 3.2 View reads `@Environment(EventStateManager.self)` for sessions list
  - [ ] 3.3 Use a `List` for Digital Crown scrolling (native watchOS List = automatic Crown integration):
    ```swift
    struct SessionScheduleView: View {
        @Environment(EventStateManager.self) private var eventState

        var body: some View {
            List(sortedSessions, id: \.id) { session in
                ScheduleRowView(session: session, isActive: isActive(session))
            }
            .navigationTitle("Schedule")
            .navigationBarTitleDisplayMode(.inline)
        }

        private var sortedSessions: [CachedSession] {
            (eventState.currentEvent?.sessions ?? [])
                .sorted { ($0.startTime ?? .distantPast) < ($1.startTime ?? .distantPast) }
        }

        private func isActive(_ session: CachedSession) -> Bool {
            guard let start = session.startTime, let end = session.endTime else { return false }
            let now = Date()
            return now >= start && now <= end
        }
    }
    ```
  - [ ] 3.4 Create `ScheduleRowView` sub-view:
    ```swift
    struct ScheduleRowView: View {
        let session: CachedSession
        let isActive: Bool

        var body: some View {
            VStack(alignment: .leading, spacing: 2) {
                HStack {
                    // Time slot
                    Text(formattedTime)
                        .font(.system(size: 10, design: .monospaced))
                        .foregroundStyle(.secondary)
                    Spacer()
                    // Status badge
                    statusBadge
                }
                Text(session.title ?? "")
                    .font(.system(size: 12, weight: .semibold))
                    .lineLimit(2)
                // Speaker name (if not a break)
                if let speakerName = primarySpeakerName {
                    Text(speakerName)
                        .font(.system(size: 10))
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }
            .padding(.vertical, 2)
        }

        @ViewBuilder
        private var statusBadge: some View {
            let (label, color) = badgeInfo
            Text(label)
                .font(.system(size: 9, weight: .semibold))
                .foregroundStyle(color)
                .padding(.horizontal, 4)
                .padding(.vertical, 2)
                .background(color.opacity(0.15), in: Capsule())
        }

        private var badgeInfo: (String, Color) {
            if isCompleted {
                return (durationLabel, .gray)
            } else if isActive {
                return ("Active", .teal)
            } else {
                return ("Upcoming", .secondary)
            }
        }

        private var isCompleted: Bool {
            guard let end = session.endTime else { return false }
            return end < Date() && !isActive
        }

        private var formattedTime: String {
            guard let start = session.startTime else { return "--:--" }
            let formatter = DateFormatter()
            formatter.dateFormat = "HH:mm"
            return formatter.string(from: start)
        }

        private var primarySpeakerName: String? {
            session.speakers.first.map { "\($0.firstName ?? "") \($0.lastName ?? "")" }
        }

        // AC4: actual duration for completed sessions
        private var durationLabel: String {
            let start = session.actualStartTime ?? session.startTime
            let end = session.actualEndTime ?? session.endTime
            guard let s = start, let e = end, e > s else { return "Done" }
            let mins = Int(e.timeIntervalSince(s) / 60)
            return "\(mins) min"
        }
    }
    ```
  - [ ] 3.5 Scroll to active session on appear using `ScrollViewReader`:
    ```swift
    ScrollViewReader { proxy in
        List(sortedSessions, id: \.id) { session in
            ScheduleRowView(session: session, isActive: isActive(session))
                .id(session.sessionSlug)
        }
        .onAppear {
            if let activeId = sortedSessions.first(where: { isActive($0) })?.sessionSlug {
                proxy.scrollTo(activeId, anchor: .center)
            }
        }
    }
    ```
  - [ ] 3.6 Add `#Preview` showing a mix of completed, active, and upcoming sessions

- [ ] **Task 4: Tests** (AC: 1, 2, 4, 5)
  - [ ] 4.1 Create `apps/BATbern-watch/BATbern-watch Watch AppTests/Views/SessionScheduleViewTests.swift`
  - [ ] 4.2 Test: `ScheduleRowView` shows "Active" badge when session encompasses `now`
  - [ ] 4.3 Test: `ScheduleRowView` shows duration label "42 min" when `actualEndTime - actualStartTime = 2520s`
  - [ ] 4.4 Test: `ScheduleRowView` shows `endTime - startTime` duration fallback when `actualEndTime` is nil
  - [ ] 4.5 Test: `ScheduleRowView` shows "Upcoming" badge when session starts in the future
  - [ ] 4.6 Test: `ScheduleRowView` shows "Done" label when duration cannot be computed (nil times)
  - [ ] 4.7 Create `apps/BATbern-watch/BATbern-watch Watch AppTests/ViewModels/LiveCountdownViewModelTests+NextSession.swift`
    (or add to existing `LiveCountdownViewModelTests.swift`)
  - [ ] 4.8 Test: `nextSession` is nil when active session is the last session in the event
  - [ ] 4.9 Test: `nextSession` skips break/networking session types — only returns first non-break upcoming session
  - [ ] 4.10 Test: `nextSession` returns first session with `startTime > activeSession.endTime` that has `sessionType != .break_`
  - [ ] 4.11 Test: `nextSession` is nil when `activeSession` is nil (no session currently active)

## Dev Notes

### What Already Exists — DO NOT Reinvent

| File | Status | Relation to W3.4 |
|---|---|---|
| `Domain/SessionTimerEngine.swift` | ✅ COMPLETE | Not directly used in O7 — O7 uses wall-clock comparison with `Date()` for status badges |
| `Domain/EventStateManager.swift` | ✅ COMPLETE | `currentEvent.sessions` is the data source for O7 session list |
| `Models/WatchModels.swift` | ✅ COMPLETE | `WatchSession`, `SessionType` enum (use `.break_`, `.networking` to exclude from nextSession) |
| `Models/CachedSession.swift` | ✅ COMPLETE | Has `actualStartTime`, `actualEndTime` fields (added in Epic W1 migration) — use for AC4 completed duration |
| `ViewModels/LiveCountdownViewModel.swift` | From W3.1 | Already has `nextSession: WatchSession?` as a published property — verify it's computed in `refreshState()` |
| `Views/Shared/SpeakerPortraitView.swift` | ✅ CHECK | Used for next session portrait thumbnail in O3 — check if it accepts a `size` parameter |
| `Views/Organizer/LiveCountdownView.swift` | From W3.1 | Modify to add `NextSessionPreviewView` + NavigationLink affordance |
| `Views/OrganizerZoneView.swift` | ✅ COMPLETE | Add `NavigationStack` wrapper for the `isLive` branch only |
| `Utilities/SwissDateFormatter.swift` | ✅ COMPLETE | **Use for time formatting in ScheduleRowView** — HH:mm format already implemented. Do NOT use raw `DateFormatter` inline; use `SwissDateFormatter.timeString(from:)` if that helper exists |
| `Mocks/MockClock.swift` | ✅ COMPLETE | For `nextSession` tests — inject clock to control "now" |
| `Factories/TestDataFactory.swift` | ✅ COMPLETE | Use `TestData.fixedSession()` for timer-related tests |

### NavigationStack Placement — Critical Decision

Currently `OrganizerZoneView` shows `LiveCountdownView()` directly. To support navigation to O7, you must introduce a `NavigationStack`. **Do NOT add NavigationStack inside `LiveCountdownView` itself** — views should not own their navigation context.

**Correct placement** (in `OrganizerZoneView.swift`):
```swift
} else if eventState.isLive {
    NavigationStack {
        LiveCountdownView()
    }
}
```

This ensures:
1. O3 → O7 navigation works via `NavigationLink` in `LiveCountdownView`
2. Back navigation from O7 (Crown press / swipe) pops to O3
3. Other organizer zone states (O1, O2, EventPreview) are unaffected

**Do NOT add NavigationStack to O1 (PairingView) or O2 (SpeakerArrivalView)** — they don't need nested navigation in Epic W3.

### CachedSession `sessionType` Field

`CachedSession` has a `sessionType: String?` field (from the OpenAPI-generated data). The `WatchModels.swift` `SessionType` enum maps these:
```swift
enum SessionType: String {
    case presentation
    case keynote
    case workshop
    case panel
    case break_ = "break"      // Swift keyword workaround — use .break_ in code
    case networking
    case lunch
}
```

For `nextSession` computation, exclude breaks/networking/lunch:
```swift
nextSession = event.sessions
    .compactMap { $0.toWatchSession() }
    .filter { session in
        session.startTime > (activeSession?.endTime ?? clock.now)
        && session.sessionType != .break_
        && session.sessionType != .networking
        && session.sessionType != .lunch
    }
    .min(by: { $0.startTime < $1.startTime })
```

### CachedSession `actualStartTime` / `actualEndTime` Fields

These are the fields added in Epic W1 Flyway migration (`V{next}__add_watch_session_fields.sql`). They represent the real start/end times when organizers advance sessions early or late (Epic W4). In Epic W3, sessions complete by the clock, so these may be nil. The `durationLabel` must gracefully fall back:
```swift
// AC4: prefer actual times if available, fall back to scheduled
let start = session.actualStartTime ?? session.startTime
let end = session.actualEndTime ?? session.endTime
```

### `SpeakerPortraitView` Usage in NextSessionPreviewView

`SpeakerPortraitView` is a shared component in `Views/Shared/`. Check its current API:
- If it accepts a `size` parameter → use `SpeakerPortraitView(speakerId: id, size: 24)`
- If it has a fixed size → wrap in `.frame(width: 24, height: 24).clipped()` with `.scaledToFill()`

The next session preview uses a very small portrait (24pt circle). Do NOT create a new portrait fetching mechanism — `PortraitCache` (in `Data/PortraitCache.swift`) is already the caching layer used by `SpeakerPortraitView`.

### onAppear Anti-Pattern (Known from MEMORY)

`OrganizerZoneView.onAppear` already has the 60-second debounce guard. The new `NavigationStack` wrapper does NOT affect this — `onAppear` fires when the ORGANIZER ZONE appears, not on each navigation within the stack.

However: `SessionScheduleView.onAppear` must NOT trigger any sync or expensive operations — it only reads `eventState.currentEvent` which is already populated. Do NOT add sync logic to O7.

### SwissDateFormatter for Time Display

```swift
// In ScheduleRowView, use:
import Foundation  // SwissDateFormatter is in Utilities/

// If SwissDateFormatter has a timeString helper:
let timeStr = SwissDateFormatter.timeString(from: session.startTime)

// Otherwise, use HH:mm DateFormatter (Swiss 24-hour format):
let formatter = DateFormatter()
formatter.dateFormat = "HH:mm"
formatter.locale = Locale(identifier: "de_CH")
let timeStr = formatter.string(from: session.startTime)
```

**Do NOT use `.timeStyle = .short`** — this shows "3:45 PM" on en_US locales. Always use "HH:mm" for 24-hour Swiss format.

### O7 Screen — No Backend Work Required

W3.4 is entirely client-side, like W3.1–W3.3:
- All session data is already cached by W2.3's sync
- Status badges (completed/active/upcoming) are computed from wall-clock comparison with `Date()`
- `actualStartTime`/`actualEndTime` columns exist on `CachedSession` (from W1 migration) — may be nil until Epic W4

### Digital Crown Navigation Semantics

In watchOS, pressing the Digital Crown:
1. **From a full-screen view** → returns to the watch face
2. **From within a NavigationStack (not at root)** → pops back one level (same as swipe-left)

So AC3 ("pressing the Digital Crown returns to O3") maps to standard Navigation pop behavior — O7 pushed on `NavigationStack`, Crown press triggers the pop. **No special Crown gesture handling required.**

### Architecture Compliance

[Source: docs/watch-app/architecture.md#Implementation-Patterns]
- MVVM: `SessionScheduleView` can use `@Environment(EventStateManager.self)` directly — it's a display-only view with no domain logic. No separate ViewModel needed (the session list is just a sorted array with computed badge state).
- NEVER call `Date()` in production domain code — but `SessionScheduleView`'s `isActive()` helper calls `Date()` directly. This is acceptable in **view** code (badge is a display-time calculation, not domain timer logic). If testability is needed, inject `ClockProtocol` via environment.
- All UI updates `@MainActor` (views are implicitly `@MainActor`)
- Cancel tasks in `onDisappear` — O7 has no tasks to cancel

### Project Structure for New Files

```
apps/BATbern-watch/
├── BATbern-watch Watch App/
│   └── Views/
│       └── Organizer/
│           ├── LiveCountdownView.swift          ← MODIFY (add NextSessionPreviewView + NavigationLink)
│           ├── NextSessionPreviewView.swift      ← NEW
│           └── SessionScheduleView.swift         ← NEW (O7)
│       OrganizerZoneView.swift                  ← MODIFY (add NavigationStack for isLive branch)
└── BATbern-watch Watch AppTests/
    └── Views/
        └── SessionScheduleViewTests.swift        ← NEW
```

`LiveCountdownViewModel.swift` may need minor modification to verify `nextSession` is being computed (was defined in spec but confirm implementation).

### References

- [Source: docs/watch-app/epics.md#W3.4] — AC definitions, FR2, FR3, FR4 coverage
- [Source: docs/watch-app/epics.md#NonFunctional] — NFR6 (<100ms Crown scroll transitions)
- [Source: docs/watch-app/architecture.md#Frontend-Architecture] — O7 screen in organizer zone screen map
- [Source: apps/BATbern-watch/CLAUDE.md#Architecture-Overview] — OrganizerZoneView routing, MVVM pattern
- [Source: apps/BATbern-watch/CLAUDE.md#Critical-Architectural-Patterns] — NavigationStack placement, ClockProtocol
- [Source: _bmad-output/implementation-artifacts/w3-1-live-countdown-display.md] — LiveCountdownViewModel API, nextSession property spec
- [Source: _bmad-output/implementation-artifacts/w3-3-watch-face-complications.md] — CachedSession actualStartTime/actualEndTime fields, existing SpeakerPortraitView usage
- [Source: MEMORY.md#SwiftUI-TabView-onAppear-Anti-Pattern] — onAppear debounce already in OrganizerZoneView

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-5-20250929

### Debug Log References

### Completion Notes List

### File List
