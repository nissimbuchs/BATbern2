# Epic 4 Reuse Map — Architectural Audit

**Date:** 2026-02-18
**Author:** Winston (Architect)
**Status:** ⛔ GATE G1 — Required before SM creates any W4.x story
**Audit basis:** Walking the actual codebase (Epics 1–3 as shipped), not the architecture doc

---

## Purpose

This document is the output of Gate G1 from the Epic 3 retrospective. It maps every
existing Epic 1–3 component that Epic 4 stories must **extend** rather than duplicate.

Nissim's concern (verbatim from retro): *"I am afraid that in Epic 4 we will encounter
similar problems — things already implemented in public view and the danger of reimplementing
them in Epic 4 for organizer views."*

The four risk areas identified in the retro are each addressed below with:
- What exists (exact file + line references)
- The duplication trap — what a greenfield design would wrongly create
- The extension mandate — what the story must constrain Dev to do instead

SM rule: every W4.x story must cite the relevant section(s) of this document in its
**Pre-Implementation Review** section, confirming compliance before Dev starts.

---

## Area 1 — O6 Transition View (W4.2) vs `LiveCountdownView.nextSessionCard()`

### What exists

| Component | File | Lines | Delivers |
|-----------|------|-------|---------|
| `nextSessionCard(next:)` | `Views/Organizer/LiveCountdownView.swift` | 152–178 | Renders "NEXT" peek card: speaker first-name-or-title + HH:mm start time; dimmed, 0.6 opacity |
| `LiveCountdownViewModel.nextSession` | `ViewModels/LiveCountdownViewModel.swift` | 34 | Published `WatchSession?` — already the computed "what comes next" |
| `findNextSession(after:in:)` | `ViewModels/LiveCountdownViewModel.swift` | 182–188 | Finds first **non-break** session after the active one ends; `eventState.currentEvent` is the input |
| `LiveCountdownViewModel.eventState` | `ViewModels/LiveCountdownViewModel.swift` | 44 | `EventStateManagerProtocol` — the single data source feeding session discovery |
| `portraitCircle` | `Views/Organizer/LiveCountdownView.swift` | 124–147 | Renders speaker portrait with `PortraitCache` backing and initials gradient fallback |

### What O6 (W4.2) needs

O6 is the **full-screen transition view** that appears after "Done" is tapped and a regular
session follows. It shows: next speaker portrait (prominent), full name, and talk title.

The *data* O6 needs is identical to what `nextSessionCard()` already computes:
`LiveCountdownViewModel.nextSession`. The difference is presentation only (full-screen vs
compact peek card).

### Duplication trap

A greenfield W4.2 would:
- Create a new `TransitionViewModel` that re-discovers the next session independently
- Duplicate `findNextSession()` logic in the new ViewModel
- Write a new portrait-loading path instead of reusing `PortraitCache`

### Extension mandate

**W4.2 MUST:**

1. **Consume `LiveCountdownViewModel.nextSession`** — the same `@Observable` instance
   that `LiveCountdownView` already holds. O6 is not a new navigation destination with its
   own ViewModel; it is a presentation layer over existing state.

2. **Not reimplement `findNextSession()`** in any new type. If O6 needs next-session data,
   the path is: `EventStateManager.currentEvent` → `LiveCountdownViewModel.findNextSession()`
   → `LiveCountdownViewModel.nextSession` → O6 view reads from the shared ViewModel.

3. **Reuse `PortraitCache.shared`** for portrait loading (same pattern as `loadPortrait()` in
   `LiveCountdownView` lines 196–207). Do not write a new portrait fetch path.

4. **Refactor `nextSessionCard()` into a shared sub-view** (e.g., `NextSessionPeekView`) so
   both the compact peek (bottom of O3) and the full-screen O6 share the same layout
   component with a `style: .compact / .prominent` parameter. This is a view extraction, not
   a duplication.

5. If a break follows the completed session, O6 must **not** be shown — W4.4 (O5) handles
   that case. The routing condition is `!nextSession.isBreak`, which `findNextSession()`
   already enforces (`!$0.isBreak` at line 188).

---

## Area 2 — O5 Break View (W4.4) vs `SessionCardView` break cards + `LiveCountdownViewModel` break gong routing

### What exists

| Component | File | Lines | Delivers |
|-----------|------|-------|---------|
| `breakCardLayout` | `Views/Public/SessionCardView.swift` | 138–175 | Break card visual: SF Symbol icon (`cup.and.saucer`, `person.2`), title, optional time slot + status badge |
| `breakIcon` computed property | `Views/Public/SessionCardView.swift` | 70–82 | Maps `.breakTime`/`.lunch` → `cup.and.saucer.fill`, `.networking` → `person.2.fill` |
| `isBreakSession` computed property | `Views/Public/SessionCardView.swift` | 49–52 | `sessionType == .breakTime || .lunch || .networking` |
| `LiveCountdownViewModel.refreshState()` break routing | `ViewModels/LiveCountdownViewModel.swift` | 113–116 | Already routes break sessions to `evaluateBreakGong()` vs talk sessions to `evaluate()` |
| `HapticScheduler.evaluateBreakGong()` | `Domain/HapticScheduler.swift` | 72–82 | Fires `.gongReminder` when `remaining <= 60s && remaining > 0`; deduplicates |
| `WatchHapticService.play(.gongReminder)` | `Data/WatchHapticService.swift` | 85–101 | Triple-tap haptic pattern for gong |
| `SessionBadgeStatus` | `Views/Public/SessionCardView.swift` | 14–39 | `.completed`, `.active`, `.upcoming` with colors; already shown when `showStatusBadge: true` |

### What O5 (W4.4) needs

O5 (Break Management) shows during a break: break countdown, gong timer, and next speaker
preview. It triggers the gong haptic at the configured threshold.

**All gong logic is already shipped.** `LiveCountdownViewModel.refreshState()` detects break
sessions and calls `scheduler.evaluateBreakGong()` on every timer tick. W4.4 needs only to
**display** the break countdown — the haptic already fires correctly in the current code.

### Duplication trap

A greenfield W4.4 would:
- Create a new `BreakView` with its own break detection logic (re-checking `isBreak`)
- Implement a new gong countdown timer independent of `HapticScheduler`
- Redraw the break icon/title layout without referencing `breakCardLayout`
- Write a new break session → next speaker lookup, duplicating `findNextSession()`

### Extension mandate

**W4.4 MUST:**

1. **Reuse `LiveCountdownViewModel`** — it already detects break sessions as `activeSession`
   and routes to `evaluateBreakGong()`. W4.4 does NOT get its own break-detection ViewModel.

2. **Extract `breakCardLayout` from `SessionCardView`** into a shared `BreakCardLayout`
   view (or `@ViewBuilder` function) before W4.4 builds O5. O5 uses this shared layout, not
   a hand-rolled break card. This extraction is a prerequisite subtask of W4.4, not an
   Epic 4 backlog item.

3. **Display the countdown from `LiveCountdownViewModel.formattedTime`** — the same time
   string already shown in `compactRing`. O5 is a different *visual* of the same timer
   state, not a second timer running in parallel.

4. **Use `LiveCountdownViewModel.nextSession`** for the "next speaker" preview at the bottom
   of O5 — same data source as Area 1 above.

5. **Do not reimplement gong haptic logic.** `HapticScheduler.evaluateBreakGong()` is
   already called on every timer tick when a break is active. The only W4.4 haptic work is
   the **visual gong reminder overlay** — the haptic itself fires automatically.

6. `WatchHapticService.schedule()` (the stub, technical debt D1) is NOT required for W4.4.
   The break gong fires via `evaluateBreakGong()` on the 1-second tick loop, not via the
   scheduled queue. D1 is only needed for server-triggered pre-scheduling in W4.1.

---

## Area 3 — W4.1 WebSocket Connectivity State vs `ConnectionStatusBar` + `EventDataController`

### What exists

| Component | File | Lines | Delivers |
|-----------|------|-------|---------|
| `ConnectionStatusBar` | `Views/Shared/ConnectionStatusBar.swift` | 1–71 | Shared visual bar: wifi icon (orange=offline, teal=online), last-synced capsule; `isOffline` + `lastSynced` + `clock` inputs |
| `EventDataController.isOffline` | `Data/EventDataController.swift` | 29 | `Bool` — set to `true` on REST sync network failure |
| `EventDataController.lastSynced` | `Data/EventDataController.swift` | 28 | `Date?` — timestamp of last successful REST sync |
| `ConnectivityMonitor` | `Data/ConnectivityMonitor.swift` | 1–72 | `NWPathMonitor` wrapper; publishes `isConnected: Bool`; drives `EventDataController` on change |
| `WebSocketClientProtocol.isConnected` | `Protocols/WebSocketClientProtocol.swift` | 6 | Already in the protocol — `Bool` connection state |
| `MockWebSocketClient` | `Tests/Mocks/MockWebSocketClient.swift` | — | Already exists — W4.1 tests can use it immediately |
| `ConnectionStatusBarTests` | `Tests/Views/ConnectionStatusBarTests.swift` | — | `ConnectionStatusBar` already has test coverage |

### W4.1 connectivity state requirements

W4.1 establishes a persistent STOMP WebSocket connection and must show:
- Reconnection state (exponential backoff)
- Presence indicator (connected organizer count, FR20)
- Connection lost haptic (`.failure` via `WatchHapticService.play(.connectionLost)`)

### Duplication trap

A greenfield W4.1 would:
- Create a new `WebSocketStatusView` alongside (or replacing) `ConnectionStatusBar`
- Track WebSocket `isConnected` in a new "OrganizerConnectionState" object separate from
  `EventDataController.isOffline`
- Wire connectivity lost to a new haptic call, duplicating `HapticAlert.connectionLost`

### Extension mandate

**W4.1 MUST:**

1. **Use `ConnectionStatusBar` (already in `Views/Shared/`) as the visual component** for
   all connectivity state display. This component is already injected with `isOffline` and
   `lastSynced` from `EventDataController`. W4.1 feeds its WebSocket connection state into
   `EventDataController`, which then propagates to `ConnectionStatusBar` — not a new view.

2. **Route WebSocket disconnection through `EventDataController.isOffline`**. When the
   WebSocket drops (and REST sync is also unavailable), `EventDataController.isOffline`
   should become `true`. There must be ONE `isOffline` flag in the environment, not a REST
   one and a WebSocket one.

3. **The presence indicator (FR20 — connected organizer count) is a SEPARATE, ADDITIVE
   component** — a small `PresenceIndicatorView` added to `LiveCountdownView` alongside
   `ConnectionStatusBar`. It is NOT a replacement. It receives presence count from the
   WebSocket service.

4. **Use `HapticAlert.connectionLost` and `WatchHapticService.play(.connectionLost)` as-is**
   for the connection-lost haptic. This pattern is already defined in `HapticAlert` and
   implemented in `WatchHapticService`. Do not add a new haptic path.

5. **The concrete `WebSocketClient` implementation delivers the protocol already defined in
   `WebSocketClientProtocol.swift`** — `connect()`, `disconnect()`, `sendAction()`,
   `stateUpdates()`, `arrivalUpdates()`. These contracts are fixed. `MockWebSocketClient`
   already exists for tests.

6. **`WatchHapticService.schedule()` (D1) must be implemented before W4.1 starts.** The
   stub at `Data/WatchHapticService.swift:117–123` appends to `scheduledQueue` but never
   fires. Server-triggered haptic scheduling (for synchronized multi-watch alerts, FR16)
   depends on this. D1 is a hard dependency for W4.1, not an optional improvement.

---

## Area 4 — `EventStateManager` as Single Source of Truth

### What exists

| Component | File | Lines | Delivers |
|-----------|------|-------|---------|
| `EventStateManager` | `Domain/EventStateManager.swift` | 1–122 | Derives `isLive`, `isPreEvent`, `hasActiveEvent`, `timeUntilEventStart` from `EventDataController.currentEvent`; clock-injectable |
| `EventDataController.currentEvent` | `Data/EventDataController.swift` | 25 | `CachedEvent?` — SwiftData-backed, single write path for all event data |
| `CachedSession.actualStartTime` | `Models/CachedSession.swift` | 21 | Already in schema — `Date?` |
| `CachedSession.actualEndTime` | `Models/CachedSession.swift` | 22 | Already in schema — `Date?` |
| `CachedSession.overrunMinutes` | `Models/CachedSession.swift` | 23 | Already in schema — `Int?` |
| `CachedSession.completedByUsername` | `Models/CachedSession.swift` | 24 | Already in schema — `String?` |
| `OrganizerZoneView` routing | `Views/OrganizerZoneView.swift` | 34–43 | Routes to O3 (`isLive`), O2 (`isPreEvent`), or `EventPreviewView` — state-driven |
| `EventStateMessageType` | `Protocols/WebSocketClientProtocol.swift` | 44–50 | `.sessionStarted`, `.sessionEnded`, `.sessionExtended`, `.sessionSkipped`, `.heartbeat` |
| `WatchAction` enum | `Protocols/WebSocketClientProtocol.swift` | 26–31 | `startSession`, `endSession`, `skipSession`, `extendSession(minutes:)` — Epic 4 actions already typed |

### The risk

Epic 4 introduces session state changes driven by WebSocket messages (server authoritative).
There is a strong temptation to create a new "OrganizerSessionManager" or
"LiveEventController" that holds parallel session state — the current session slug, which
session is active, overrun minutes — separately from `EventDataController`.

If this happens:
- `EventStateManager.isLive` would drift from the parallel state
- `LiveCountdownViewModel.activeSession` and `findNextSession()` would use stale data
- `OrganizerZoneView` routing would fight with the parallel state manager
- Two clocks, two session lists, two sources of truth

This is the **highest structural risk** in Epic 4.

### Extension mandate

**ALL W4.x stories MUST:**

1. **Route every WebSocket state update through `EventDataController`**. When the Watch
   receives a `SESSION_STARTED` or `SESSION_ENDED` message, the concrete WebSocket service
   calls an `EventDataController` method (e.g., `applyServerState(_:)`) that updates
   `currentEvent`. `EventStateManager` then recalculates its derivatives automatically.

2. **Never create an `OrganizerStateManager`, `LiveEventController`, or any class that
   holds a `currentSession` property independent of `EventDataController.currentEvent`**.
   All session state flows through `EventDataController` → `EventStateManager` →
   `LiveCountdownViewModel`.

3. **Populate the existing `CachedSession` fields when the server confirms an action:**
   - `completedByUsername` — set when `SESSION_ENDED` received with `initiatedBy`
   - `actualEndTime` — set from the server state message timestamp
   - `overrunMinutes` — derived from `actualEndTime - scheduledEndTime` in minutes

   These fields exist in the schema. Epic 4 does not add new `CachedSession` columns — it
   **populates** the ones already there.

4. **`EventStateManager.isLive` is the only gate for showing `LiveCountdownView` (O3).**
   W4.2 does not add its own "event is active" check. When all sessions complete and the
   event transitions to COMPLETED, `EventStateManager.isLive` becomes `false`, and
   `OrganizerZoneView` routes away from O3 automatically — W4.4 gets this for free.

5. **The cascade (W4.3) recalculates `startTime` / `endTime` on all downstream sessions.**
   The updated session schedule flows from the server via WebSocket broadcast → into
   `EventDataController` → updates all `CachedSession` records. `EventStateManager` and
   `LiveCountdownViewModel` recalculate from the new schedule without any additional
   "cascade state" variable.

---

## Additional Pre-Existing Assets — Not in Retro Scope but Audited

These components are ready for Epic 4 with zero changes:

| Component | File | Ready for |
|-----------|------|-----------|
| `WebSocketClientProtocol` (full contract) | `Protocols/WebSocketClientProtocol.swift` | W4.1 concrete implementation |
| `MockWebSocketClient` (test double) | `Tests/Mocks/MockWebSocketClient.swift` | All W4.x unit tests |
| `WatchAction` enum (all actions typed) | `Protocols/WebSocketClientProtocol.swift` | W4.2 Done, W4.3 cascade, W4.4 session advance |
| `HapticAlert.actionConfirm` | `Models/HapticAlert.swift` | W4.2 Done tap success haptic |
| `HapticAlert.connectionLost` | `Models/HapticAlert.swift` | W4.1 WebSocket disconnect haptic |
| `SessionBadgeStatus` (.completed) | `Views/Public/SessionCardView.swift` | W4.2 "Completed by [name]" in O7 |
| `SessionListView` passing `showStatusBadge` | `Views/Public/SessionListView.swift` | W4.2 session timeline O7 already shows badges when organizer + live |
| `TestDataFactory` | `Tests/Factories/TestDataFactory.swift` | All W4.x test data |

---

## Technical Debt D1 — Blocking Constraint

`WatchHapticService.schedule()` at `Data/WatchHapticService.swift:117–123` appends to
`scheduledQueue` but never fires the scheduled alert.

**D1 blocks W4.1.** Server-synchronized haptics (FR16 — all watches fire simultaneously)
require the server to push a `fireAt` timestamp via WebSocket, which the Watch receives and
schedules via `schedule(_ alert:, at:)`. Until this method fires, synchronized haptics
cannot be implemented.

**D1 does not block W4.2, W4.3, or W4.4** — these rely on the existing `play()` path via
`HapticScheduler`, which is fully functional.

---

## Summary — Extension Rules for SM Story Creation

When Bob creates W4.x stories, each story must enforce:

| W4.x Story | Must NOT create | Must USE instead |
|------------|----------------|-----------------|
| W4.1 | New connectivity indicator view | `ConnectionStatusBar` (Shared) |
| W4.1 | Separate WebSocket `isOffline` state | `EventDataController.isOffline` |
| W4.2 | New `findNextSession()` implementation | `LiveCountdownViewModel.nextSession` |
| W4.2 | New portrait loading path | `PortraitCache.shared` (same as LiveCountdownView) |
| W4.2 | New O6 without refactoring `nextSessionCard()` | Extract `NextSessionPeekView` first |
| W4.3 | "Cascade state" variable outside EventDataController | Update `CachedSession` fields via `EventDataController` |
| W4.4 | New break detection logic | `LiveCountdownViewModel.activeSession?.isBreak` |
| W4.4 | New gong haptic implementation | `HapticScheduler.evaluateBreakGong()` (already called) |
| W4.4 | New break card visual | Extract `BreakCardLayout` from `SessionCardView.breakCardLayout` |
| All | `OrganizerStateManager` or `LiveEventController` | `EventStateManager` + `EventDataController` |
| All | Parallel `currentSession` state | `EventDataController.currentEvent` sessions |

---

## Gate Status

| Gate | Condition | Status |
|------|-----------|--------|
| G1 | This document produced and reviewed by SM | ✅ Produced — awaiting SM review |
| D1 | `WatchHapticService.schedule()` firing logic implemented | ⏳ Pending (Amelia) |
| A1 | Pre-implementation review section in every W4.x story file | ⏳ Pending (Bob) |
| A2 | Design direction block at top of every W4.x story with UI | ⏳ Pending (Bob) |

**SM (Bob): review this document. Once reviewed, confirm in sprint-status.yaml that G1 is
satisfied and W4.1 story creation is unblocked. D1 must be confirmed complete before W4.1
Dev assignment.**

---

*Architectural audit by Winston (Architect) | BATbern Watch Project | 2026-02-18*
