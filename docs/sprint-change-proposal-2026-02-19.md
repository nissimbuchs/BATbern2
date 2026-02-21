# Sprint Change Proposal — BATbern Watch
**Date:** 2026-02-19
**Prepared by:** John (PM Agent)
**Triggered by:** `docs/watch-app/watch-correct-course.md`
**Sprint state at time of change:** W4.3 in-progress (not yet coded), W4.4 ready-for-dev, Epic 5 backlog

---

## Section 1: Issue Summary

Three distinct design refinements were identified by the product owner (Nissim) before implementation of W4.3 begins. The timing is ideal — W4.3 exists as a ready-for-dev story but has zero tasks checked (no code written). W4.2 (done) requires surgical modification.

### Change A — Auto-Advance Replaces Done Button (W4.2/W4.3 core)
**Problem:** The current design requires the organizer to tap a "Done" button to advance the session. The new requirement is that sessions auto-advance when time expires, with proactive controls for extending time *before* the session ends.

**Evidence:** Stakeholder document section "session overrun" — "the organizer session view should move directly to the next session, when one session ends."

**Category:** New requirement from stakeholder.

### Change B — Complications Context-Awareness (W3.3)
**Problem:** The current complications show a uniform countdown/progress ring regardless of whether the event is days away, hours away, or currently running. The required behavior is context-aware: different display logic for 4 distinct states.

**Evidence:** Stakeholder document section "details on complications."

**Category:** New requirement from stakeholder.

### Change C — Current Event API / No-Event Handling (W1.1)
**Problem:** The current "no event" state shows static text. The "TBD" event state is not handled. The event selection semantics (first future/same-day non-archived) need to be explicitly implemented.

**Evidence:** Stakeholder document section "general: handling of current event."

**Category:** New requirement from stakeholder.

---

## Section 2: Impact Analysis

### 2.1 Epic Impact

| Epic / Story | Status | Impact | Scope |
|---|---|---|---|
| **W1.1** (Xcode + Hero Screen) | done | AC3 changes: no-event → animation + "coming soon"; add TBD title handling | Minor |
| **W3.3** (Complications) | done | All 3 complication types need context-aware display logic | Moderate |
| **W4.2** (Session Advance) | done | Remove Done button; add auto-advance trigger; add Extend button (T-10min); add Delayed button (first 10min) | Moderate |
| **W4.3** (Overrun/Cascade) | in-progress, not coded | Completely rewrite: Extend and Delayed button semantics replace cascade-prompt-on-overrun | Major rewrite (but zero code exists) |
| **W4.4** (Break Management) | ready-for-dev | Auto-advance into break still works; no Done button for final session → auto-advance handles it | Minor |
| **Epic 5** | backlog | Offline queue must support new action types (`EXTEND_SESSION`, `DELAY_TO_PREVIOUS`) | Minor (additive) |

### 2.2 Story-Level Conflict Details

#### W4.2 — Conflicts (done story, needs surgical changes)

| Section | OLD | NEW |
|---|---|---|
| AC1 | Done button appears at/past 0:00 (`urgencyLevel == .overtime`) | **Remove Done button.** Session auto-advances when `urgencyLevel == .overtime` — client sends `endSession` automatically |
| AC2 | Done tap → server confirms → O6 appears | **O6 still appears** — triggered by `sessionEndedEvent` exactly as before, but the event is fired by auto-send, not user tap |
| AC3 | Done tap → `HapticAlert.actionConfirm` | Auto-advance fires `HapticAlert.actionConfirm` at moment of auto-send |
| NEW AC6 | — | Extend button appears when `remainingSeconds <= 600` (last 10 min). Options: +5/+10/+15/+20 min. Sends `WatchAction.extendSession(slug, minutes)` → pushes out session `scheduledEndTime`, shifts downstream sessions. Timer resets to new end time. |
| NEW AC7 | — | Delayed button appears when session has been running < 10 min (`activeSession.actualStartTime` within last 10 min). Options: +5/+10/+15/+20 min. Sends new `WatchAction.delayToPrevious(currentSlug, minutes)`. Previous session re-activates with extended time; all downstream shifts; view switches back to previous session. |

#### W4.3 — Complete Story Rewrite (no code exists)

The existing W4.3 story design (cascade prompt O4 triggered by Done button while overrunning) is obsolete. The rewrite covers:

**Extend Button (replaces O4 cascade prompt):**
- Appears in `LiveCountdownView` when `remainingSeconds <= 600`
- Triggers a sheet: `ExtendSessionView` with options +5/+10/+15/+20 min
- `WatchAction.extendSession(sessionSlug:minutes:)` **reused** but semantics change: extends `scheduledEndTime` of ACTIVE session (does NOT end it). All downstream sessions shift.
- Countdown timer resets to new `scheduledEndTime` automatically via `applyServerState`
- Backend: `watchSessionService.extendSession(eventCode, sessionSlug, minutes, username)` sets new `scheduledEndTime = old + N min`, cascades downstream

**Delayed Button (new concept):**
- Appears in `LiveCountdownView` when session has been active for < 10 minutes
- Triggers a sheet: `DelayedSessionView` with same options
- New `WatchAction.delayToPrevious(currentSlug:minutes:)`
- Backend new method `watchSessionService.delayToPrevious(eventCode, currentSlug, minutes, username)`:
  1. Finds previous session
  2. Extends previous session `scheduledEndTime` += N minutes
  3. Resets current session to SCHEDULED (removes `actualStartTime`, status = SCHEDULED)
  4. Shifts all sessions after the previous session by N minutes
  5. Sets previous session back to ACTIVE
  6. Broadcasts STATE_UPDATE with trigger `SESSION_DELAYED`
- All watches switch view back to previous session (now active with extended time)

**AC1 (new):** Given `remainingSeconds <= 600`, When I view O3, Then an Extend button appears. It does NOT appear before the 10-min threshold.
**AC2 (new):** Given I tap Extend and choose N minutes, When confirmed, Then current session `scheduledEndTime` shifts by N, all downstream sessions shift by N, countdown resets to new time. All watches update within 3s.
**AC3 (new):** Given session has been active < 10 min, When I view O3, Then a Delayed button appears. It disappears after the first 10 min.
**AC4 (new):** Given I tap Delayed and choose N minutes, When confirmed, Then previous session gets N more minutes + becomes active again; current session resets to scheduled; all watches switch to previous session view; full schedule shifts by N min.

#### W3.3 — Complications Context-Awareness

Four display contexts need to be implemented as an enum in `ComplicationDataSource` or `OrganizerViewModel`:

```
.noEvent           → BATbern logo only (no ring, no countdown)
.eventFar          → dd.MM date in center, no progress ring
.eventDayPreSession(hoursUntil, progress)
                   → "Xh" countdown in center, ring = elapsed-since-midnight / midnight-to-session-start
.sessionRunning(minutesLeft, fractionRemaining)
                   → "Xm" in center, ring = fractionRemaining (15min/45min = 33%)
.eventComplete     → BATbern logo only
```

Ring semantics:
- Pre-session: COUNT-UP (shows elapsed progress toward first session)
- Session running: COUNT-DOWN (shows fraction of time remaining in session)

| Complication | Change |
|---|---|
| `CircularComplication` (C1) | Add `ComplicationContext` switch; show ring only for `.eventDayPreSession` and `.sessionRunning` |
| `RectangularComplication` (C2) | Same context switch; date format `dd.MM` for `.eventFar` |
| `CornerComplication` (C3) | Same context switch |
| `OrganizerViewModel` / shared source | Add `complicationContext: ComplicationContext` computed property |

#### W1.1 — Current Event / No-Event State

| AC | OLD | NEW |
|---|---|---|
| AC3 | No current event → "No upcoming BATbern event" + logo | No current event → BATbern animation + "coming soon" text |
| NEW AC | — | Event with title == nil or "TBD" (and no topic/session defined) → show date + venue only (no speakers, no session cards) |
| Definition | — | Current event = first event where `date >= today` (not archived) |

**Hero screen changes:**
- `EventHeroView`: detect `event.title == nil || event.title == "TBD"` → show date + venue only
- `PublicViewModel`: add `isNoEvent: Bool` and `isTBD: Bool` computed properties
- No-event state: show BATbern animation (existing logo assets) + "Bald wieder" / "Coming soon" text

**Complications (no-event state):**
- All 3 complications: when `complicationContext == .noEvent` → show BATbern logo only, no ring

### 2.3 PRD Conflicts

| FR | Conflict | Required Change |
|---|---|---|
| FR6 | "Organizer can mark the current session as complete" | Change to "System auto-advances session when time expires" |
| FR8 | "Organizer can initiate a schedule cascade when a session overruns" | Replace with: "Organizer can extend current session time (Extend button, last 10 min) or restore time to previous session (Delayed button, first 10 min of next session)" |
| LIVE-8 | "Tap 'Done' to advance schedule" | Change to "Auto-advances; Extend/Delayed for time control" |
| Journey 1 | "Marco taps Done on his Watch" | Update to auto-advance language |
| Journey 3 | "Taps Done → cascade prompt" | Update to Extend/Delayed button flow |
| FR29 area | No explicit "TBD" or "no event" state | Add FR for TBD handling + coming-soon state |

MVP scope: **unchanged or enhanced** — the new model gives organizers proactive time control (Extend before overrun) rather than reactive control (cascade after overrun). This is strictly better UX.

### 2.4 Architecture Conflicts

| Component | Conflict | Required Change |
|---|---|---|
| `EventStateMachine.swift` | No auto-advance trigger | Add: when `urgencyLevel` transitions to `.overtime`, auto-send `endSession` via `WebSocketService` |
| `LiveCountdownView.swift` | Has Done button (W4.2) | Remove Done button; add Extend button (T≤10min); add Delayed button (first 10min) |
| `LiveCountdownViewModel.swift` | `canMarkDone` / `shouldShowCascadePrompt` | Replace with `shouldShowExtend: Bool`, `shouldShowDelayed: Bool`, `overrunSeconds`, `sessionActiveSeconds` |
| `CascadePromptView` (O4) | Being designed for cascade-on-overrun | Rename/replace with `ExtendSessionView` (last 10min extend prompt) and `DelayedSessionView` (delayed prompt) |
| `WatchAction` enum | Has `extendSession(slug:minutes:)` | Keep but redefine semantics: extends active session end time (not end+cascade). Add new case: `delayToPrevious(currentSlug:minutes:)` |
| `WatchSessionService.java` | `endSession` + `cascadeSession` planned | Keep `endSession`. Rewrite `cascadeSession` → rename to `extendSession` with new semantics. Add new method `delayToPreviousSession`. |
| `WatchStateUpdateMessage.java` | No `SESSION_DELAYED` trigger | Add `"SESSION_DELAYED"` trigger string constant. Add `previousSessionSlug` field for client routing. |
| Complications (C1–C3) | Uniform display regardless of event state | Add `ComplicationContext` enum; context-aware display in all 3 complications |
| `PublicViewModel.swift` | Simple event/no-event handling | Add TBD detection; "coming soon" no-event state |
| `EventHeroView.swift` | No TBD title handling | Add TBD branch: show date + venue only |
| `SessionRepository.java` | No `findPreviousSession` | Add `findByEventCodeAndScheduledStartTimeBeforeOrderByScheduledStartTimeDesc` |

---

## Section 3: Recommended Approach

**Selected: Direct Adjustment** (hybrid across 4 stories + PRD + Architecture + epics.md)

### Rationale

1. **W4.3 has zero code** — it's the cheapest possible moment to change this story. The story rewrite is a document change with no code rework.
2. **W4.2 infrastructure is reusable** — `SessionTransitionView` (O6), `NextSessionPeekView`, `WebSocketService.sendAction`, `sessionEndedEvent`, `WatchSessionService.endSession` all survive unchanged. The only removal is the Done button. The Extend/Delayed buttons reuse the same WebSocket/send/haptic patterns.
3. **W3.3 and W1.1 are isolated changes** — complication context logic and hero screen TBD handling are self-contained additions that don't affect other stories.
4. **No rollback needed** — W4.2's Done button removal is cleaner than reverting the whole story.
5. **Auto-advance is technically simpler** — removing a button and adding a `.onChange` trigger is less code than the existing Done button implementation.

### Effort Assessment

| Change | Effort | Risk |
|---|---|---|
| W4.2 Done → auto-advance + Extend + Delayed buttons | Medium | Medium (done story, regression risk) |
| W4.3 story rewrite (doc only, no code exists) | Low | Low |
| W3.3 complication context-awareness | Low-Medium | Low |
| W1.1 no-event / TBD handling | Low | Low |
| PRD + Architecture + epics.md doc updates | Low | Low |

---

## Section 4: Detailed Change Proposals

### Proposal 1 — W4.2 (Done story): Remove Done Button + Add Auto-Advance + Extend/Delayed Buttons

**Artifact:** `_bmad-output/implementation-artifacts/w4-2-session-advance-transition.md`

**Section: Acceptance Criteria**

```
AC1 — REMOVED (Done Button at/past 0:00)

NEW AC1 — Auto-Advance:
Given a session timer reaches 0:00 (urgencyLevel transitions to .overtime),
When the transition is detected in LiveCountdownViewModel,
Then the client automatically sends WatchAction.endSession(sessionSlug:) via
WebSocketService — no user interaction required.
The success haptic (HapticAlert.actionConfirm) fires at the moment of auto-send.

AC2 — unchanged: O6 transition still appears after SESSION_ENDED broadcast
AC3 — changed: haptic fires on auto-send, not user tap
AC4 — unchanged: idempotent "Completed by [name]" in O7
AC5 — unchanged: break routing when nextSession == nil

NEW AC6 — Extend Button:
Given a session has <= 10 minutes remaining (remainingSeconds <= 600),
When I view O3 (LiveCountdownView),
Then an "Extend" button appears in the action area.
Tapping it shows ExtendSessionView sheet with options: +5 / +10 / +15 / +20 min.
Choosing an option sends WatchAction.extendSession(sessionSlug:minutes:N).
The server extends the session's scheduledEndTime by N minutes, shifts all
downstream sessions, broadcasts STATE_UPDATE with trigger SESSION_EXTENDED.
The countdown resets to the new end time. All watches update within 3s.
The Extend button persists (available to extend again if needed).

NEW AC7 — Delayed Button:
Given the current session has been active for < 10 minutes
(clock.now - activeSession.actualStartTime < 600 seconds),
When I view O3,
Then a "Delayed" button appears alongside the Extend button.
Tapping it shows DelayedSessionView sheet with options: +5 / +10 / +15 / +20 min.
Choosing N sends WatchAction.delayToPrevious(currentSlug:minutes:N).
The server: (a) resets current session to SCHEDULED, (b) extends previous session
scheduledEndTime by N min and re-activates it, (c) shifts all downstream sessions
by N min, (d) broadcasts STATE_UPDATE with trigger SESSION_DELAYED and
previousSessionSlug field.
All watches route back to the previous session view (now active, extended).
The Delayed button disappears after 10 minutes of session activity.
```

**Section: Tasks (additions to existing task list)**

```
MODIFY Task 1: LiveCountdownViewModel — remove canMarkDone, add:
- shouldAutoAdvance: Bool = (urgencyLevel == .overtime)
- shouldShowExtend: Bool = (remainingSeconds <= 600 && urgencyLevel != .overtime)
- shouldShowDelayed: Bool = (sessionActiveSeconds < 600)  // first 10min
- sessionActiveSeconds: Int  // computed from clock.now - actualStartTime

MODIFY Task 2: LiveCountdownView — replace doneButton with:
- Auto-advance: .onChange(of: viewModel.shouldAutoAdvance) { if $0 { autoAdvance() } }
- autoAdvance() sends endSession action + fires actionConfirm haptic
- Add extendButton (shown when shouldShowExtend)
- Add delayedButton (shown when shouldShowDelayed)

NEW Task 11: ExtendSessionView (sheet)
- Inputs: sessionSlug, onExtend: (Int) -> Void
- Layout: "+5 min" / "+10 min" / "+15 min" / "+20 min" buttons
- On tap: haptic + onExtend(N) → parent sends extendSession action

NEW Task 12: DelayedSessionView (sheet)
- Inputs: currentSlug, onDelay: (Int) -> Void
- Layout: "+5 min" / "+10 min" / "+15 min" / "+20 min" buttons
- Header: "Give previous session more time?"
- On tap: haptic + onDelay(N) → parent sends delayToPrevious action

NEW Task 13: Backend — WatchSessionService.extendSession()
- Extends active session scheduledEndTime += N min
- Cascades downstream sessions += N min
- Broadcasts STATE_UPDATE trigger: SESSION_EXTENDED

NEW Task 14: Backend — WatchSessionService.delayToPreviousSession()
- Finds previous session by eventCode + current session startTime
- Extends previous scheduledEndTime += N min
- Resets current session to SCHEDULED (clear actualStartTime, status = SCHEDULED)
- Re-activates previous session (status = ACTIVE, set actualStartTime = original)
- Shifts all sessions after previous by N min
- Broadcasts STATE_UPDATE trigger: SESSION_DELAYED with previousSessionSlug field

NEW Task 15: Client-side SESSION_DELAYED routing
- WebSocketService: add sessionDelayedEvent: SessionDelayedEvent?
- LiveCountdownView .onChange(of: webSocketService.sessionDelayedEvent):
  EventDataController.applyServerState already handles the time updates;
  OrganizerZoneView reads activeSession from EventStateManager which will
  automatically route to the re-activated previous session.
```

**Rationale:** Sessions auto-advancing is simpler and more reliable than requiring a tap (moderator on stage may be distracted). Extend button gives proactive control before overrun. Delayed button handles the "previous speaker needed more time" edge case that the original Done+cascade model missed entirely.

---

### Proposal 2 — W4.3 (ready-for-dev story): Complete Rewrite

**Artifact:** `_bmad-output/implementation-artifacts/w4-3-overrun-detection-schedule-cascade.md`

**New Story Title:** W4.3: Extend & Delayed Session Controls

**New Story Statement:**
As an organizer,
I want to extend the current session's time when I see it running short, or restore time to the previous session if the transition happened too early,
so that the schedule stays accurate and the audience experience is smooth.

**New ACs:**
See Proposal 1 AC6 and AC7 above (these formally live in W4.2 but the backend work and the new session-prompt views are W4.3 scope since W4.2 is already done).

**Allocation:**
- W4.2 amendment handles: watchOS UI changes (Extend/Delayed buttons, auto-advance trigger, remove Done button)
- W4.3 rewrite handles: backend `extendSession` (new semantics) + `delayToPreviousSession` (new method) + watchOS `ExtendSessionView` + `DelayedSessionView` + `SessionDelayedEvent` routing

**Note:** W4.3's pre-implementation review checklist must be fully reset. All `[ ]` checkboxes are correct (nothing confirmed yet).

---

### Proposal 3 — W3.3 (done story): Complication Context-Awareness

**Artifact:** `_bmad-output/implementation-artifacts/w3-3-watch-face-complications.md` (if it exists)

**Section: Additional ACs to add**

```
NEW AC4 — Event > 1 day away:
Given the current event is more than 1 calendar day away,
When the complications are displayed,
Then C1/C2/C3 show the event date in dd.MM format in the center.
No progress ring is displayed.

NEW AC5 — Event day, pre-first-session:
Given today is event day and no session has started,
When complications display,
Then the center shows hours until first session ("Xh" format).
The ring shows count-up progress: elapsed_minutes_since_midnight /
total_minutes_from_midnight_to_first_session_start.
Example: session at 16:00, current time 08:00 → ring 8h/16h = 50% filled.

NEW AC6 — Event day, session running:
Given a session is currently active,
When complications display,
Then the center shows minutes remaining in the session ("Xm").
The ring shows remaining fraction: remaining_minutes / total_session_minutes.
Example: 45-min session, 15 min remain → ring 15/45 = 33%.

NEW AC7 — Event day, post-last-session:
Given all sessions are completed,
When complications display,
Then only the BATbern logo is shown. No ring, no countdown text.

NEW AC8 — No current event:
Given no current event exists (null from API),
When complications display,
Then only the BATbern logo is shown.
```

**Code changes:**
- Add `enum ComplicationContext` to `OrganizerViewModel` or shared `ComplicationDataSource`
- All 3 complication files switch on `ComplicationContext`
- Ring progress formula differs between pre-session (count-up) and in-session (count-down)

---

### Proposal 4 — W1.1 (done story): No-Event / TBD Event Handling

**Artifact:** `_bmad-output/implementation-artifacts/w1-1-xcode-project-setup-event-hero-screen.md` (if it exists)

**Section: AC changes**

```
MODIFY AC3:
OLD: "Given no current event exists When I launch the app Then I see 'No upcoming
     BATbern event' with the BATbern logo"
NEW: "Given no current event exists When I launch the app Then I see the BATbern
     animation (logo) with 'Bald wieder' (coming soon) text centered on screen"

NEW AC4 — TBD Event:
Given a current event exists but its title is null or "TBD" AND no sessions
are defined, When I view the event hero screen,
Then I see the event date and venue name only — no speaker portraits, no session
cards, no abstract navigation.
The BATbern logo and scroll hint are not shown.
```

**Code changes:**
- `PublicViewModel`: add `isTBDEvent: Bool` computed property
- `EventHeroView`: add TBD branch rendering date + venue only
- No-event state: replace static text with BATbern logo + animated "coming soon" text

---

### Proposal 5 — PRD Updates

**Artifact:** `docs/watch-app/prd-batbern-watch.md`

```
FR6 OLD: "Organizer can mark the current session as complete to advance the schedule"
FR6 NEW: "System auto-advances the session when time expires; no manual 'Done' action
          required"

FR8 OLD: "Organizer can initiate a schedule cascade when a session overruns, shifting
          all remaining items by a chosen increment"
FR8 NEW: "Organizer can extend the current session's remaining time via an Extend button
          (visible in the last 10 minutes), or restore time to the previous session via a
          Delayed button (visible in the first 10 minutes of a new session), both shifting
          all remaining items by the chosen increment"

LIVE-8 OLD: "Session complete — Tap 'Done' to advance schedule for all organizers"
LIVE-8 NEW: "Session auto-advances; Extend button (last 10 min) and Delayed button
             (first 10 min) for time control"

Journey 1 (Marco happy path):
- Replace "Marco taps Done" with "The session time expires → all watches advance automatically"

Journey 3 (overrun):
- Replace "Taps Done → cascade prompt" with:
  "At 10 minutes remaining, an Extend button appears. Marco taps it and chooses '+5 min'.
  The session timer resets to new end time. All watches update instantly.
  The audience notices nothing. If the transition happened too quickly, any organizer
  can tap Delayed in the first 10 minutes of the next session to add time back."

Add new section or FR for:
- FR-NEW-A: "Public zone shows 'coming soon' state when no current event exists"
- FR-NEW-B: "Public zone shows date + venue only when event title is TBD and no sessions defined"
```

---

### Proposal 6 — Architecture Updates

**Artifact:** `docs/watch-app/architecture.md`

```
Section: Frontend Architecture — Organizer Zone screens
- O4 CascadePromptView: REPLACED by two sheets:
  - ExtendSessionView (launched from Extend button, last 10 min)
  - DelayedSessionView (launched from Delayed button, first 10 min)

Section: Message Schemas — Action (Watch → Server)
ADD:
{
  "action": "EXTEND_SESSION",   // extends active session end time; shifts downstream
  "sessionSlug": "...",
  "minutesAdded": 10,
  "clientTimestamp": "..."
}
{
  "action": "DELAY_TO_PREVIOUS",  // NEW
  "currentSessionSlug": "...",
  "minutesAdded": 5,
  "clientTimestamp": "..."
}

Section: Message Schemas — State Update (Server → Watch)
ADD trigger values:
- "SESSION_EXTENDED": current session's end time extended, downstream shifted
- "SESSION_DELAYED": previous session re-activated, current reset to scheduled

Section: WatchAction enum
- extendSession(sessionSlug:minutes:) — SEMANTICS CHANGE: extends active session
  end time, does NOT end the session
- NEW: delayToPrevious(currentSlug:minutes:) — extends previous session time,
  resets current to scheduled

Section: Domain Layer
ADD LiveCountdownViewModel computed properties:
- shouldShowExtend: Bool (remainingSeconds <= 600)
- shouldShowDelayed: Bool (sessionActiveSeconds < 600)
- sessionActiveSeconds: Int

Section: Backend — WatchSessionService
- extendSession(): extend active session scheduledEndTime + cascade downstream
- delayToPreviousSession(): new method (see Proposal 1 Task 14 above)

Section: Complications
UPDATE description:
"Context-aware display based on event state:
- noEvent / eventComplete: BATbern logo only
- eventFar (>1 day): date dd.MM, no ring
- eventDayPreSession: hours countdown, ring = count-up toward session start
- sessionRunning: minutes remaining, ring = fraction remaining in session"
```

---

### Proposal 7 — epics.md Updates

**Artifact:** `docs/watch-app/epics.md`

```
W4.2 ACs:
- REPLACE AC1 (Done button) with auto-advance AC
- REPLACE AC2 (Done tap → O6) with: O6 fires automatically on session expiry
- ADD AC6 (Extend button)
- ADD AC7 (Delayed button)
- REMOVE note about "Done button first appears"

W4.3 Story:
- REPLACE entire story with: "W4.3: Extend & Delayed Session Controls"
  (as described in Proposal 2 above)
  ACs = Extend button (server-side) + Delayed button (server-side) + backend methods

W3.3 ACs:
- ADD AC4–AC8 (complication context-awareness)

W1.1 ACs:
- MODIFY AC3 (coming soon animation)
- ADD AC4 (TBD event)
```

---

## Section 5: Implementation Handoff

### Change Scope Classification

| Change | Scope | Route to |
|---|---|---|
| W4.2 amendment | **Moderate** — done story needs code changes | Dev agent |
| W4.3 rewrite | **Minor** (doc only — no code exists) | SM agent → create new W4.3 story |
| W3.3 amendment | **Minor** — done story needs additive logic | Dev agent |
| W1.1 amendment | **Minor** — done story needs additive handling | Dev agent |
| PRD + Arch + epics.md doc updates | **Minor** — document changes | PM / Tech Writer |

### Implementation Sequence

```
1. [PM] Update PRD + Architecture + epics.md (doc changes, unblock everything)
2. [SM] Rewrite W4.3 story using new Extend/Delayed semantics
3. [Dev] Amend W4.2: remove Done button, add auto-advance trigger, add Extend/Delayed buttons + prompts
4. [Dev] Implement W4.3 (new): backend extendSession (new semantics) + delayToPreviousSession + client routing
5. [Dev] Amend W3.3: add ComplicationContext + context-aware display
6. [Dev] Amend W1.1: TBD event + coming-soon no-event state
7. [Dev] Continue W4.4 (break management — minimal changes needed)
8. [Dev] Epic 5 — add new action types to offline queue
```

### Success Criteria for Implementation Team

- [ ] No "Done" button in `LiveCountdownView` — sessions auto-advance at 0:00
- [ ] Extend button visible exactly when `remainingSeconds <= 600` (and session not yet overtime)
- [ ] Delayed button visible exactly in first 10 min of a session, disappears after
- [ ] Extend sends session time extension without ending session; countdown resets
- [ ] Delayed reactivates previous session on all watches
- [ ] All 3 complications show correct context: far/pre-session/running/done/no-event
- [ ] No-event hero shows animation + "coming soon" text
- [ ] TBD event shows date + venue only
- [ ] All watches sync within 3 seconds on any action (NFR3 unchanged)
- [ ] Existing W4.2 tests updated for auto-advance (not Done tap)
- [ ] New W4.3 integration tests: extendSession + delayToPreviousSession

---

## Approval

**Status:** Awaiting explicit approval from Nissim.

**Options:**
- **[yes]** — Approve this proposal; route to implementation as described in Section 5
- **[no/revise]** — Return to analysis with specific feedback

---

*Sprint Change Proposal generated by PM Agent (John) — BATbern Watch CC Workflow — 2026-02-19*
