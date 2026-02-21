# Story W3.4: Session Schedule & Next Session Preview

Status: done

## Story

As an organizer,
I want to scroll through the full schedule and see what's next with status indicators,
so that I can plan transitions and introductions.

## Approach: Reuse Public View + Badges

**Decision**: Rather than building a separate O7 SessionScheduleView, the organizer
simply swipes left from the organizer zone to the existing public zone (SessionListView).
The full schedule is already there. The only addition is status badges on each session
card when the organizer is paired and the event is live.

This eliminates Tasks 1, 3 and ACs 3, 4, 6 (all O7-specific). AC2 and AC5 were fully
implemented in W3.1 (nextSessionCard in LiveCountdownView, hidden when nil).

## Acceptance Criteria

1. **AC1 — Session Status Badges**: Given I'm paired as organizer and the event is live,
   When I swipe to the public zone (SessionListView), Then each session card shows a
   status badge: "Done" (gray), "Active" (teal), or "Upcoming" (secondary/gray).

2. **AC2 — Next Session Preview in O3**: ✅ **DONE in W3.1** — `LiveCountdownView`
   shows `nextSessionCard()` below the countdown ring when `viewModel.nextSession != nil`.
   `LiveCountdownViewModel.findNextSession()` returns the first non-break session after
   the active session's end time.

3. ~~AC3 — Crown Back Navigation from O7~~: N/A — organizer navigates via tab swipe.

4. ~~AC4 — Completed Session Duration in O7~~: Out of scope — simplified approach does
   not show actual duration in the card.

5. **AC5 — No Next Session Handling**: ✅ **DONE in W3.1** — `nextSessionCard` is
   wrapped in `if let next = viewModel.nextSession { ... }` in LiveCountdownView:42.

6. ~~AC6 — Crown-Scrollable O7~~: N/A — existing vertical paging in SessionListView
   already provides Crown scrolling.

## Tasks / Subtasks

- [x] **Task 1: Verify AC2 / AC5 already done (W3.1)** (AC: 2, 5)
  - [x] 1.1 Confirm `LiveCountdownView.nextSessionCard()` renders next session
  - [x] 1.2 Confirm it is hidden when `viewModel.nextSession == nil`
  - [x] 1.3 Confirm `LiveCountdownViewModel.findNextSession()` skips breaks

- [x] **Task 2: Add status badges to SessionCardView** (AC: 1)
  - [x] 2.1 Add `SessionBadgeStatus` enum with `.completed`, `.active`, `.upcoming` +
        static `status(for:at:)` pure function (testable without SwiftUI)
  - [x] 2.2 Add `showStatusBadge: Bool = false` parameter to `SessionCardView`
  - [x] 2.3 In `presentationCardLayout` and `breakCardLayout`, convert the time-slot
        `Text` into an `HStack` — time on left, badge capsule on right
  - [x] 2.4 Badge only rendered when `showStatusBadge == true` and status is non-nil
  - [x] 2.5 In `SessionListView`, add `@Environment(AuthManager.self)` and
        `@Environment(EventStateManager.self)`, pass
        `showStatusBadge: authManager.isPaired && eventState.isLive` to each card

- [x] **Task 3: Tests** (AC: 1)
  - [x] 3.1 In `SessionCardViewTests.swift`, add badge status tests using
        `SessionBadgeStatus.status(for:at:)` — no SwiftUI needed
  - [x] 3.2 Test: session with `endTime < now` → `.completed` / "Done" / gray
  - [x] 3.3 Test: session with `startTime <= now <= endTime` → `.active` / "Active" / teal
  - [x] 3.4 Test: session with `startTime > now` → `.upcoming` / "Upcoming" / secondary
  - [x] 3.5 Test: session with nil `startTime` → `nil` (no badge)
  - [x] 3.6 Test: session with nil `endTime` → `nil` (no badge)

## Dev Notes

### What Changed from Original Story

| Original | Simplified |
|---|---|
| New `SessionScheduleView` (O7) | Reuse existing `SessionListView` |
| `NavigationStack` + "Schedule" button in O3 | Tab swipe to public zone |
| Compact List showing 3-4 rows | Full-screen vertical paging |
| AC4: actual duration label | Dropped |
| AC6: List Crown scrolling | Existing paging Crown scrolling |

### SessionBadgeStatus — Pure Testable Function

```swift
// Test without SwiftUI — just call the static function:
let past   = SessionBadgeStatus.status(for: session, at: Date())  // .completed
let active = SessionBadgeStatus.status(for: session, at: midpoint) // .active
let future = SessionBadgeStatus.status(for: session, at: before)   // .upcoming
```

### Badge Placement in SessionCardView

In `presentationCardLayout` and `breakCardLayout`, the time-slot row becomes:
```swift
HStack {
    Text("HH:mm – HH:mm")  // existing
    Spacer()
    if showStatusBadge, let status = SessionBadgeStatus.status(for: session, at: Date()) {
        Text(status.label)
            .font(.system(size: 9, weight: .semibold))
            .foregroundStyle(status.color)
            .padding(.horizontal, 5)
            .padding(.vertical, 2)
            .background(status.color.opacity(0.15), in: Capsule())
    }
}
.padding(.top, timeSlotTopPadding)
```

### SessionListView Environment Access

`SessionListView` needs two new environments (already injected via `ContentView`):
```swift
@Environment(AuthManager.self) private var authManager
@Environment(EventStateManager.self) private var eventState
```

Pass to cards:
```swift
SessionCardView(
    session: session,
    phase: vm.event?.currentPublishedPhase,
    statusBarVisible: statusBarVisible(vm: vm),
    showStatusBadge: authManager.isPaired && eventState.isLive
)
```

### References

- [Source: _bmad-output/implementation-artifacts/w3-1-live-countdown-display.md] — AC2/AC5 done
- [Source: apps/BATbern-watch/BATbern-watch Watch App/Views/Organizer/LiveCountdownView.swift:42-44] — nextSessionCard guard
- [Source: apps/BATbern-watch/BATbern-watch Watch App/ViewModels/LiveCountdownViewModel.swift:182-188] — findNextSession

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Completion Notes List

- AC2 / AC5: Verified fully implemented in W3.1 (LiveCountdownView:42-44, LiveCountdownViewModel:182-188)
- Approach: status badges on existing public SessionCardView instead of new O7 screen
- Added `SessionBadgeStatus: Equatable` enum with pure static `status(for:at:)` — no SwiftUI dependency, fully unit-testable
- `showStatusBadge` flag propagated from `SessionListView` via `authManager.isPaired && eventState.isLive`
- Badge rendered in `HStack` alongside time slot in both `presentationCardLayout` and `breakCardLayout`
- 6 new W3.4 badge tests all pass; all 12 `SessionCardViewTests` pass, no regressions

### Change Log

- 2026-02-18: W3.4 implementation — added session status badges to public view for organizer context; simplified approach eliminates new O7 screen
- 2026-02-18: Code review fixes — M1: badge decoupled from showTimeSlots guard (now shows in all phases); M2: badge tests use fixed anchor date per CLAUDE.md; M3: added showStatusBadge:false suppression tests; L1: added upcoming.color assertion; L2: upcoming color changed from Color(white:0.55) to .secondary

### File List

- `apps/BATbern-watch/BATbern-watch Watch App/Views/Public/SessionCardView.swift`
- `apps/BATbern-watch/BATbern-watch Watch App/Views/Public/SessionListView.swift`
- `apps/BATbern-watch/BATbern-watch Watch AppTests/Views/SessionCardViewTests.swift`
