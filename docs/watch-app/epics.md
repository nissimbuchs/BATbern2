---
stepsCompleted: [step-01-validate-prerequisites, step-02-design-epics, step-03-create-stories, step-04-final-validation]
inputDocuments:
  - docs/watch-app/prd-batbern-watch.md
  - docs/watch-app/architecture.md
  - docs/watch-app/ux-design-specification.md
---

# BATbern Watch - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for BATbern Watch, decomposing the requirements from the PRD v2.0, Architecture v2.0, and UX Design Specification v2.0 into implementable stories.

## Requirements Inventory

### Functional Requirements

FR1: Organizer can view the current session's speaker name, talk title, and remaining time on the Watch face complication
FR2: Organizer can view the next upcoming session (speaker name and talk title) at a glance
FR3: Organizer can scroll through the full remaining event schedule using the Digital Crown
FR4: Organizer can view a speaker's portrait photo alongside their session information
FR5: Organizer can see the current event state on the always-on display without raising their wrist
FR6: Organizer can mark the current session as complete to advance the schedule to the next item
FR7: System detects when a session runs past its allocated time and displays overrun duration
FR8: Organizer can initiate a schedule cascade when a session overruns, shifting all remaining items by a chosen increment
FR9: System automatically recalculates all downstream session times and break durations after a cascade
FR10: Organizer can view whether the event is on time, ahead, or behind schedule
FR11: Organizer receives a haptic alert when 5 minutes remain in the current session
FR12: Organizer receives a distinct haptic alert when 2 minutes remain in the current session
FR13: Organizer receives a distinct haptic alert when the session time reaches zero
FR14: Organizer receives escalating haptic alerts at intervals when a session runs over time
FR15: Organizer receives a haptic alert at a configured time before a break ends (gong reminder)
FR16: All connected organizers receive haptic alerts simultaneously for the same timing events
FR17: All connected organizer watches display identical event state in real time
FR18: When one organizer advances the schedule, all other watches update within 3 seconds
FR19: When one organizer triggers a schedule cascade, all other watches reflect the new times within 3 seconds
FR20: Organizer can see how many fellow organizers are currently connected to the event
FR21: Organizer can authenticate with the BATbern platform directly from the Watch via pairing code
FR22: Organizer can select and join an active event from the Watch
FR23: System syncs the full event schedule (sessions, speakers, times, portraits) to the Watch on join
FR24: Watch connects directly to the BATbern backend over WiFi without requiring an iPhone
FR25: System caches the complete event schedule locally after initial sync
FR26: Countdown timer and haptic alerts continue functioning when WiFi connectivity is lost
FR27: Actions taken offline (session advance, cascade) queue locally and sync when connectivity is restored
FR28: System indicates connectivity status to the organizer (connected / offline)
FR29: Attendee can view the current event's theme image, title, date, time, and venue on launch (no login)
FR30: Attendee can browse all sessions via Digital Crown scrolling
FR31: Attendee can tap on a session title to view the full abstract
FR32: Attendee can tap on a speaker area to view speaker bio, portrait, and company info
FR33: Multi-speaker sessions display a grid of speaker portraits with company logos
FR34: Public zone respects progressive publishing phases (TOPIC / SPEAKERS / AGENDA)
FR35: Public zone caches event data and works offline with "Last updated" indicator
FR36: Organizer can view all speakers with portraits in a pre-event overview (available within 1 hour before event start)
FR37: Organizer can tap a speaker's portrait to confirm their arrival, marking them with a visible green checkmark badge
FR38: Speaker arrival confirmations sync to all connected organizer watches within 3 seconds
FR39: Pre-event overview shows arrival count ("3 of 5 arrived") updated in real time across all watches

### NonFunctional Requirements

NFR1: Complication updates within 1 second of state change
NFR2: Haptic alerts fire within 1 second of scheduled time
NFR3: Schedule cascade propagates to all connected watches within 3 seconds
NFR4: Event schedule initial sync completes within 5 seconds on venue WiFi
NFR5: App launch to usable state within 3 seconds
NFR6: Crown scroll between sessions: <100ms per page transition
NFR7: Public zone: App launch to hero screen <2 seconds (cached), <4 seconds (cold)
NFR8: App must not crash during a live event (3-hour continuous session)
NFR9: Haptic alerts must fire even if the app moves to background
NFR10: Offline mode activates seamlessly on WiFi loss — no user action required
NFR11: Queued offline actions must not be lost on app restart
NFR12: System must handle conflicting actions from multiple organizers gracefully
NFR13: Zero missed haptic alerts per event
NFR14: Organizer authentication via pairing code flow (no password entry on Watch)
NFR15: Pairing tokens stored in Keychain (not UserDefaults)
NFR16: Short-lived JWTs (1 hour) for organizer API calls with auto-refresh
NFR17: Only authenticated organizers assigned to an event can join that event's Watch session
NFR18: Communication with backend encrypted via TLS
NFR19: Max 2 watches per organizer account
NFR20: Pairing codes expire after 24 hours if unused
NFR21: Full 3-hour event operation on a single charge (Watch battery > 30% remaining at event end)
NFR22: Battery impact during event: <15% for 4-hour event (organizer mode with active sync)
NFR23: Network polling frequency adapts to battery level (reduce frequency below 20% battery)
NFR24: Cached data storage under 50MB per event (schedule + portraits)
NFR25: watchOS 11+ (Apple Watch Series 8, SE 2nd gen, Ultra, and later)
NFR26: No iPhone app required (standalone watchOS app)
NFR27: Direct WiFi support required (Series 6+ have this capability)
NFR28: VoiceOver support for all screens
NFR29: Dynamic Type support for text scaling
NFR30: High contrast mode for countdown colors
NFR31: Haptic-only alerts (no audio in default mode)
NFR32: German (primary), English, French
NFR33: Matches existing web frontend i18n keys where applicable

### Additional Requirements

**From Architecture:**
- Xcode watchOS App template as starter (standalone Watch app, SwiftUI, SwiftData, watchOS 11+)
- Swift 6.0, Xcode 16+, Swift Package Manager for dependencies
- Backend extension of two existing services:
  - event-management-service: new `ch.batbern.events.watch` package (WebSocket handlers, session state, speaker arrival tracking)
  - company-user-management-service: new `ch.batbern.companyuser.watch` package (pairing code management)
- Flyway schema migrations:
  - V{next}__add_watch_session_fields.sql: 4 new columns on `sessions` table (actual_start_time, actual_end_time, overrun_minutes, completed_by_username)
  - V{next+1}__add_speaker_arrival_tracking.sql: new `speaker_arrivals` table
  - V{next+2}__add_watch_pairing.sql: new `watch_pairings` table (in company-user-management-service)
- STOMP over WebSocket for real-time sync (extending existing WebSocketConfig.java)
- JWT authentication via pairing code flow: pairing code → pairing token → JWT exchange
- 9 new REST endpoints (5 pairing, 4 watch operations)
- ALB configuration changes: stickiness enabled, idle timeout 3600s, deregistration delay 30s
- In-memory organizer presence tracking (no external message broker needed for 4 users)
- Server-authoritative state model with full-state broadcasts and last-write-wins conflict resolution
- SwiftData for local cache, Keychain for pairing tokens and JWTs
- MVVM + Repository pattern on watchOS (Views → ViewModel → Domain → Data)
- Wall-clock timer calculation (not decrementing counter) to prevent drift across watchOS suspensions
- Dual-zone horizontal paging navigation: public zone (left, default) + organizer zone (right)
- 13 screens mapped to UX spec IDs: P1-P6 (public), O1-O7 (organizer)
- 3 complication types: C1 (circular), C2 (rectangular), C3 (corner)
- Web frontend extension: WatchPairingSection in organizer profile
- Project location: `apps/BATbern-watch/` in monorepo
- Story naming: W-prefix (W1.1, W1.2, etc.) to avoid collision with platform stories
- ADR-003 compliance: meaningful identifiers, never UUIDs in URLs
- Testcontainers PostgreSQL for all backend integration tests (never H2)

**From UX Design:**
- Complication-first architecture: Watch face complication IS the primary interface (90% of usage)
- Context-aware display: different content based on event state (during talk / between talks / during break)
- Haptic vocabulary: distinct patterns for 5-min, 2-min, time's up, overrun pulse, gong reminder, action confirm, connection lost
- Color-coded urgency: BATbern Blue (>5min) → yellow (5min) → orange (2min) → red (0:00/overrun)
- Layout zones: status bar (8pt) → countdown dominant (~80pt) → speaker info (~20pt) → action button
- Typography: SF Mono ~40pt bold for countdown, SF Pro Rounded 16pt semibold for names, SF Pro 13pt for titles
- Accessibility: Dynamic Type, VoiceOver, Reduce Motion, Bold Text, visual flash with every haptic
- Anti-patterns to avoid: no multi-level navigation during events, no confirmation on "Done" tap (only on cascade), no text input ever
- Crown-scroll digit picker for pairing code entry on Watch
- Speaker arrival confirmation flow: tap portrait → "Has [Name] arrived?" → confirm → green checkmark badge syncs to all watches
- Horizontal paging between zones (SwiftUI TabView with .page style)
- State-dependent organizer zone entry: O1 (not paired) → O2 (<1h before event) → O3 (event active)
- Break/networking sessions shown as simple cards without speaker areas
- "Invisible until essential" design principle — Watch disappears during talks, surfaces at transitions

### FR Coverage Map

| FR | Epic | Description |
|---|---|---|
| FR1 | Epic 3 | Current session on complication |
| FR2 | Epic 3 | Next session at a glance |
| FR3 | Epic 3 | Crown scroll through schedule |
| FR4 | Epic 3 | Speaker portrait alongside session |
| FR5 | Epic 3 | Always-on display state |
| FR6 | Epic 4 | Mark session complete |
| FR7 | Epic 4 | Overrun detection + display |
| FR8 | Epic 4 | Schedule cascade initiation |
| FR9 | Epic 4 | Downstream time recalculation |
| FR10 | Epic 4 | On-time / behind schedule indicator |
| FR11 | Epic 3 | 5-min haptic alert |
| FR12 | Epic 3 | 2-min haptic alert |
| FR13 | Epic 3 | Time's up haptic alert |
| FR14 | Epic 3 | Overrun escalating haptics |
| FR15 | Epic 3 | Break gong reminder haptic |
| FR16 | Epic 3 | Simultaneous haptics all watches |
| FR17 | Epic 4 | Identical real-time state |
| FR18 | Epic 4 | Schedule advance sync <3s |
| FR19 | Epic 4 | Cascade sync <3s |
| FR20 | Epic 4 | Connected organizer count |
| FR21 | Epic 2 | Watch pairing authentication |
| FR22 | Epic 2 | Select and join event |
| FR23 | Epic 2 | Full schedule sync on join |
| FR24 | Epic 2 | Direct WiFi, no iPhone |
| FR25 | Epic 5 | Local schedule cache |
| FR26 | Epic 5 | Offline countdown + haptics |
| FR27 | Epic 5 | Offline action queue + sync |
| FR28 | Epic 5 | Connectivity status indicator |
| FR29 | Epic 1 | Event hero screen (no login) |
| FR30 | Epic 1 | Crown scroll sessions |
| FR31 | Epic 1 | Tap for abstract |
| FR32 | Epic 1 | Tap for speaker bio |
| FR33 | Epic 1 | Multi-speaker portrait grid |
| FR34 | Epic 1 | Progressive publishing phases |
| FR35 | Epic 1 | Public zone offline cache |
| FR36 | Epic 2 | Pre-event speaker portrait overview |
| FR37 | Epic 2 | Tap to confirm speaker arrival |
| FR38 | Epic 2 | Arrival sync <3s |
| FR39 | Epic 2 | Arrival count real-time |

## Epic List

### Epic 1: Public Event Companion
Anyone with an Apple Watch can browse tonight's BATbern event — theme, schedule, session details, speaker bios and portraits — by scrolling with the Digital Crown. No login required.
**FRs covered:** FR29, FR30, FR31, FR32, FR33, FR34, FR35
**Backend work:** None — uses existing public endpoints.
**Standalone:** Yes — shippable to TestFlight after this epic alone.

### Epic 2: Watch Pairing & Organizer Access
Organizer pairs their Watch once via a simple 6-digit code, swipes right to enter the organizer zone — automatically authenticated, no passwords ever. Pre-event speaker arrival tracking with real-time sync across all organizer watches.
**FRs covered:** FR21, FR22, FR23, FR24, FR36, FR37, FR38, FR39
**Backend work:** Pairing endpoints (company-user-management-service), arrival tracking (event-management-service), web frontend pairing UI.
**Standalone:** Yes — organizer zone accessible, event data synced, arrivals tracked.

### Epic 3: Live Countdown & Haptic Awareness
During the event, the organizer's wrist counts down, shows speaker info and portraits, and fires distinct haptic alerts at every critical moment — 5 min, 2 min, time's up, overrun, and break gong.
**FRs covered:** FR1, FR2, FR3, FR4, FR5, FR11, FR12, FR13, FR14, FR15, FR16
**Backend work:** Minor — session timing state endpoint (mostly client-side).
**Standalone:** Yes — countdown and haptics work locally. Builds on Epic 2's auth and sync.

### Epic 4: Session Control & Team Sync
One tap advances the event for ALL organizer watches simultaneously. Schedule cascade when speakers overrun — one tap shifts everything downstream.
**FRs covered:** FR6, FR7, FR8, FR9, FR10, FR17, FR18, FR19, FR20
**Backend work:** WebSocket endpoint (event-management-service), session state machine, conflict resolution, presence tracking.
**Standalone:** Yes — session control and team sync work end-to-end. Builds on Epic 2 (auth) + Epic 3 (countdown).

### Epic 5: Offline Resilience
The Watch never fails, even when WiFi drops. Countdown, haptics, and session control continue offline. Actions queue locally and sync when connectivity returns.
**FRs covered:** FR25, FR26, FR27, FR28
**Backend work:** Offline queue replay endpoint.
**Standalone:** Yes — hardens everything from Epics 1-4.

## Story List

### Epic 1: Public Event Companion (4 stories)

#### W1.1: Xcode Project Setup & Event Hero Screen

As an attendee, I want to see the current BATbern event's theme image, title, date, and venue when I launch the app, so that I immediately know what event is happening tonight.

**AC:**
- Given a current BATbern event exists When I launch the Watch app Then I see the event hero screen (P1) with theme image background, event title, date/time, and venue name
- Given no current event exists When I launch the app Then I see "No upcoming BATbern event" with the BATbern logo
- Given the app has been launched before When I launch again Then cached event data displays in <2s while a background refresh occurs

#### W1.2: Session Card Browsing

As an attendee, I want to scroll through all sessions using the Digital Crown, so that I can see the full event program on my wrist.

**AC:**
- Given I'm on the event hero screen When I scroll down with the Digital Crown Then I see session cards (P2), one per page, ordered by start time
- Given a session is a presentation/keynote/workshop/panel When I view its card Then I see time slot, title, and speaker area with portrait thumbnails and names
- Given a session is a break/networking/lunch When I view its card Then I see time slot, title, and an icon — no speaker area
- Given I scroll When transition between cards occurs Then the transition completes in <100ms (NFR6)

#### W1.3: Session Abstract & Speaker Bio

As an attendee, I want to tap a session title to read its abstract and tap speakers to see their bio and portrait, so that I can learn about talks and speakers.

**AC:**
- Given I'm on a session card (P2) When I tap the title area Then I see the abstract detail screen (P3) with full session description, Crown-scrollable
- Given a session has one speaker When I tap the speaker area Then I see the speaker bio screen (P4) with large portrait, name, company with logo, and bio
- Given a session has 2+ speakers When I tap the speaker area Then I see the multi-speaker grid (P5) with portrait thumbnails and company logos
- Given I'm on the multi-speaker grid (P5) When I tap an individual portrait Then I see that speaker's bio screen (P6)
- Given I'm on any detail screen (P3/P4/P5/P6) When I swipe back or tap back Then I return to the previous session card

#### W1.4: Progressive Publishing & Offline Support

As an attendee, I want the app to respect event publishing phases and work offline with cached data, so that I always see the right information even without WiFi.

**AC:**
- Given an event is in TOPIC phase When I browse sessions Then I see titles only — no speakers or abstracts shown
- Given an event is in SPEAKERS phase When I browse sessions Then I see titles and speakers — abstracts are hidden
- Given an event is in AGENDA phase When I browse sessions Then I see full detail — titles, speakers, and abstracts available
- Given the app has cached data and WiFi is unavailable When I browse Then all cached screens remain fully functional with a "Last updated [time]" indicator
- Given the app is offline When WiFi becomes available Then data refreshes silently in the background

---

### Epic 2: Watch Pairing & Organizer Access (4 stories)

#### W2.1: Pairing Code Backend & Web Frontend

As an organizer, I want to generate a Watch pairing code from my BATbern web profile, so that I can pair my Apple Watch without typing a password on the tiny screen.

**AC:**
- Given I'm on my organizer profile page When I click "Pair Apple Watch" Then a 6-digit numeric code appears with a 24-hour expiry countdown
- Given I already have 2 watches paired (NFR19) When I try to generate a new code Then I see an error: "Maximum 2 watches paired. Unpair a device first."
- Given a pairing code exists When 24 hours pass without use (NFR20) Then the code expires and is no longer valid
- Given I have a paired watch When I click "Unpair" on my profile Then the watch pairing is removed and the Watch shows the pairing screen on next organizer zone access

#### W2.2: Watch Pairing Flow & Organizer Zone Navigation

As an organizer, I want to enter a pairing code on my Watch and swipe right to access the organizer zone, so that authentication is invisible after the one-time setup.

**AC:**
- Given my Watch is not paired When I swipe right from any public screen Then I see the pairing screen (O1) with a Crown-scroll digit picker for 6 digits
- Given I enter a valid pairing code on O1 When I tap "Pair" Then I receive a success haptic, see "Paired as [Name]", and the organizer zone loads
- Given I enter an invalid or expired code When I tap "Pair" Then I see an error message and can retry
- Given my Watch is paired When I swipe right Then I go directly to the organizer zone (O2 or O3 depending on event state) — no re-authentication
- Given I'm in the organizer zone When I swipe left Then I return to the public zone

#### W2.3: Event Join & Schedule Sync

As an organizer, I want to join an active event and have the full schedule with speaker portraits synced to my Watch, so that I have everything needed for the event.

**AC:**
- Given I'm paired and there's an active event When I enter the organizer zone Then the full event schedule syncs within 5 seconds (NFR4) including sessions, speakers, times, and portraits
- Given syncing is in progress When I'm waiting Then I see a "Connecting to event..." spinner
- Given sync completes When I view the organizer zone Then all speaker portraits are displayed at Watch-optimized resolution
- Given no active event exists When I enter the organizer zone Then I see "No active event" with event preview if scheduled (title, date, start time)
- Given the event is >1h away When I enter the organizer zone Then I see the event preview (not speaker arrival)

#### W2.4: Speaker Arrival Tracking

As an organizer, I want to see all speakers before the event and tap to confirm their arrival, with the status syncing to all organizer watches, so that the whole team knows who's here.

**AC:**
- Given I'm paired and the event is <1 hour away When I enter the organizer zone Then I see the speaker portrait overview (O2) with all speakers and an arrival counter ("0 of N arrived")
- Given I'm on O2 When I tap a speaker's portrait Then I see a confirmation prompt: "Has [Name] arrived?" with "Arrived" and "Not yet" buttons
- Given I confirm a speaker's arrival When I tap "Arrived" Then a green checkmark badge appears on their portrait and syncs to all organizer watches within 3 seconds (FR38)
- Given another organizer confirms an arrival on their Watch When the sync arrives Then my arrival counter and badges update in real time (FR39)
- Given a speaker is already confirmed When I tap their portrait Then I see "Confirmed by [organizer name]" — no duplicate confirmation

---

### Epic 3: Live Countdown & Haptic Awareness (4 stories)

#### W3.1: Live Countdown Display

As an organizer, I want to see a countdown timer with speaker info and a progress ring during talks, so that I always know how much time remains without doing mental math.

**AC:**
- Given a session is active When I view the organizer zone Then I see O3 with countdown (MM:SS in SF Mono ~40pt), progress ring, speaker name, and talk title
- Given >5 minutes remain When I glance at O3 Then the countdown displays in BATbern Blue / green accent
- Given 5 minutes remain When the threshold is crossed Then the countdown transitions to yellow
- Given 2 minutes remain When the threshold is crossed Then the countdown transitions to orange
- Given 0:00 is reached When time expires Then the countdown switches to red "+0:00 over" and begins counting up
- Given the timer is running When the app is suspended/resumed by watchOS Then the countdown recalculates from wall clock (no drift)

#### W3.2: Haptic Alert System

As an organizer, I want to feel distinct haptic patterns at key moments, so that I know the time state without looking at my Watch.

**AC:**
- Given 5 minutes remain in a session When the threshold is reached Then I feel a single firm haptic tap (NFR2: within 1 second)
- Given 2 minutes remain When the threshold is reached Then I feel a double haptic tap — distinctly different from 5-min
- Given 0:00 is reached When time expires Then I feel a triple haptic tap — distinctly different from 2-min
- Given a session is overrunning When each additional minute passes Then I feel a rhythmic pulse pattern
- Given a break is active When the configured pre-end time is reached Then I feel the gong reminder haptic
- Given the app is in background (NFR9) When a haptic threshold is reached Then the haptic still fires via Extended Runtime session
- Given haptic alerts are configured When all 4 organizer watches are connected Then all watches fire the same haptic simultaneously (FR16)

#### W3.3: Watch Face Complications

As an organizer, I want to see countdown and speaker info on my Watch face without opening the app, so that the complication is my primary interface.

**AC:**
- Given I'm on my Watch face with BATbern complications installed When a session is active Then C1 (circular) shows progress ring + remaining minutes, C2 (rectangular) shows speaker name + countdown + progress bar, C3 (corner) shows countdown digits
- Given the complication is active When state changes (session advance, countdown) Then the complication updates within 1 second (NFR1)
- Given the Watch is in always-on display mode When I don't raise my wrist Then the complication shows a dimmed countdown (FR5)
- Given I tap the complication When an event is active Then the app opens directly to O3 (Live Countdown)

#### W3.4: Session Schedule & Next Session Preview

As an organizer, I want to scroll through the full schedule in the organizer zone and see what's next, so that I can plan transitions and introductions.

**AC:**
- Given I'm in the organizer zone during an event When I scroll with the Digital Crown Then I see the session timeline (O7) showing all sessions with status (completed / active / upcoming)
- Given the current session is active When I view O3 Then I see the next session preview below: speaker portrait, name, talk title
- Given I'm on O7 When I press the Digital Crown Then I return to the active session view (O3)
- Given a session is completed When I view it in O7 Then it shows "Completed" status with actual duration

---

### Epic 4: Session Control & Team Sync (4 stories)

> **⛔ GATE G1 — Architectural Audit Required**
> Read `docs/watch-app/epic-4-reuse-map.md` in full before creating any W4.x story.
> That document is the binding architectural contract for this entire epic.
> All extension mandates in it are non-negotiable — story ACs and tasks must enforce them.
>
> **Gate checklist (SM must confirm before creating W4.1):**
> - [ ] G1: `epic-4-reuse-map.md` reviewed — extension mandates understood
> - [ ] D1: `WatchHapticService.schedule()` firing logic implemented and tested (blocks W4.1 only)
> - [ ] A1: Pre-implementation adversarial review section added to story template
> - [ ] A2: Design direction block added to story template
>
> **Hard rule for all W4.x stories:** `EventStateManager` + `EventDataController` are the
> single source of truth. No parallel organizer state manager. No duplicate session discovery.
> WebSocket state updates flow through `EventDataController`, never around it.

#### W4.1: WebSocket Real-Time Infrastructure

As an organizer, I want my Watch to maintain a real-time connection to the backend, so that all state changes sync instantly across all organizer watches.

**AC:**
- Given I join an event When my Watch connects Then a STOMP WebSocket connection is established with JWT in CONNECT headers
- Given I'm connected When the server broadcasts a STATE_UPDATE Then my Watch receives and applies the full state within 3 seconds (NFR3)
- Given my WebSocket connection drops When WiFi is still available Then the client reconnects with exponential backoff
- Given multiple organizers are connected When I view the organizer zone Then I see a presence indicator showing connected organizer count (FR20)
- Given the backend validates my JWT When my token is expired Then the Watch refreshes via pairing token and reconnects transparently

**Architectural constraints (reuse-map Area 1, 3, 4):**
- **D1 hard dependency:** `WatchHapticService.schedule()` must fire scheduled alerts before this story is Dev-assigned. Confirm D1 complete in Pre-Implementation Review.
- **Implement `WebSocketClientProtocol`** — the full contract is already defined in `Protocols/WebSocketClientProtocol.swift`. `MockWebSocketClient` already exists for tests. Do not redefine the protocol.
- **WebSocket state flows through `EventDataController`** — when `SESSION_STARTED` / `SESSION_ENDED` / `SESSION_EXTENDED` messages arrive, the concrete WebSocket service calls an `EventDataController` method (e.g., `applyServerState(_:)`) to update `currentEvent`. `EventStateManager` recalculates derivatives automatically. Do not create an `OrganizerStateManager`.
- **Connectivity indicator: use `ConnectionStatusBar`** (already in `Views/Shared/`). Route WebSocket disconnection into `EventDataController.isOffline`. One flag, one visual component.
- **Presence indicator (FR20) is a separate additive view** — a `PresenceIndicatorView` added alongside `ConnectionStatusBar` in `LiveCountdownView`. It does not replace `ConnectionStatusBar`.
- **`HapticAlert.connectionLost` + `WatchHapticService.play(.connectionLost)`** — use as-is for the disconnect haptic. No new haptic path.

#### W4.2: Session Advance & Transition

As an organizer, I want to tap "Done" to advance the schedule for all organizer watches and see the next speaker info, so that transitions are seamless.

**AC:**
- Given a session is at or past 0:00 When the "Done" button appears and I tap it Then the session is marked complete and all watches advance within 3 seconds (FR18)
- Given I tap "Done" and the session ended on time When the action succeeds Then I see the transition view (O6) with next speaker portrait, name, and talk title
- Given I tap "Done" When the action succeeds Then I feel a success haptic confirmation
- Given another organizer taps "Done" first When I see the result Then I see "Completed by [name]" attribution — no duplicate action needed (idempotent)
- Given a break follows the completed session When Done is tapped Then the view transitions to O5 (Break + Gong)

**Architectural constraints (reuse-map Area 1, 4):**
- **O6 data source: `LiveCountdownViewModel.nextSession`** — the next session is already computed by `findNextSession()` in the existing ViewModel. O6 reads from the same `@Observable` instance as `LiveCountdownView`. Do not reimplement next-session discovery.
- **Prerequisite refactor:** extract `nextSessionCard()` from `LiveCountdownView` into a shared `NextSessionPeekView` (compact/prominent modes). This is a required subtask — Dev cannot build O6 without it.
- **Portrait loading: `PortraitCache.shared`** — same pattern as `loadPortrait()` in `LiveCountdownView`. No new portrait fetch path.
- **"Completed by [name]" in O7:** use `SessionBadgeStatus.completed` and `CachedSession.completedByUsername` (already in schema). No new status types.
- **`WatchAction.endSession(sessionSlug:)`** — already defined. Use it. Do not add new action types for this story.
- **`HapticAlert.actionConfirm`** — already defined. Use for the Done-tap success haptic.
- **Server response updates `CachedSession` fields via `EventDataController`:** `completedByUsername`, `actualEndTime`. These columns exist in the schema. No migration needed.

#### W4.3: Overrun Detection & Schedule Cascade

As an organizer, I want the Watch to detect overruns and let me shift the entire remaining schedule with one tap, so that one speaker running late doesn't cascade into chaos.

**AC:**
- Given a session runs past 0:00 When I tap "Done" while overrun Then I see the cascade prompt (O4): "Session ran +N min over. Shift remaining schedule?"
- Given I'm on the cascade prompt (O4) When I choose a shift increment (+5 min / +10 min / absorb in break) Then all downstream sessions recalculate times (FR9) and all watches update within 3 seconds (FR19)
- Given I'm on O3 during a session When the overrun grows Then I see "+N:NN over" in red with a delay impact indicator (FR10)
- Given two organizers try to cascade simultaneously When the first action is processed Then the second sees the already-shifted schedule (idempotent, first-wins)

**Architectural constraints (reuse-map Area 4):**
- **Overrun detection: `LiveCountdownViewModel.urgencyLevel == .overtime`** — already computed by `SessionTimerEngine`. O3 already shows red "+MM:SS" in overtime. W4.3 adds the "Done" button and O4 prompt on top of existing state. Do not add a parallel overrun flag.
- **`WatchAction.extendSession(sessionSlug:minutes:)`** — already defined for the cascade action. Use it.
- **Cascade result flows through `EventDataController`** — the server recalculates all downstream session times and broadcasts the updated schedule. The WebSocket service calls `EventDataController.applyServerState(_:)` with the new schedule. `LiveCountdownViewModel` recalculates from updated `CachedSession.startTime`/`endTime`. No "cascade state" variable anywhere in the client.
- **`CachedSession.overrunMinutes`** — already in schema. Populate from server response. No migration needed.
- **Conflict resolution is server-side (first-wins).** Client reconciles to server-authoritative state after broadcast. No client-side merge logic.

#### W4.4: Break Management & Event Lifecycle

As an organizer, I want to see break countdowns with gong timers and have the event flow managed end-to-end, so that the entire evening runs smoothly.

**AC:**
- Given a break starts When the view auto-transitions to O5 Then I see the break countdown, gong timer, and next speaker preview
- Given the gong reminder time arrives When the threshold is reached Then I feel the gong haptic and see a visual reminder
- Given the break ends When the next session starts Then the view auto-transitions to O3 (countdown for next talk)
- Given the final session completes When I tap "Done" Then the event state transitions to COMPLETED across all watches

**Architectural constraints (reuse-map Area 2, 4):**
- **Gong haptic: already implemented.** `LiveCountdownViewModel.refreshState()` already calls `HapticScheduler.evaluateBreakGong()` on every tick when `activeSession.isBreak`. The haptic fires automatically. W4.4 adds only the **visual gong reminder overlay** — no new haptic logic.
- **Break countdown: `LiveCountdownViewModel.formattedTime` + `urgencyLevel`** — the same timer state used by O3. O5 is a different visual presentation of the same running timer. Do not start a second timer.
- **Prerequisite refactor:** extract `breakCardLayout` from `SessionCardView` into a shared `BreakCardLayout` component. This is a required subtask — Dev cannot build O5 without it. The break icon mapping (`breakIcon`) and session-type detection (`isBreakSession`) move to the shared component.
- **Next speaker in O5: `LiveCountdownViewModel.nextSession`** — same source as Area 1. Do not re-query sessions.
- **COMPLETED transition: `EventStateManager.isLive` becomes `false`** when the server broadcasts the event-completed state. `OrganizerZoneView` re-routes automatically. No new "event completed" flag needed on the client.
- **`WatchHapticService.schedule()` (D1) is NOT required for W4.4.** The break gong fires via `evaluateBreakGong()` on the tick loop, not via the scheduled queue.

---

### Epic 5: Offline Resilience (3 stories)

#### W5.1: Offline Timer & Haptics

As an organizer, I want countdown and haptics to continue working when WiFi drops, so that I never lose time awareness during a live event.

**AC:**
- Given the countdown is running and WiFi drops When the connection is lost Then the countdown continues using wall-clock calculation with zero interruption
- Given I'm offline When a haptic threshold is reached (5 min, 2 min, 0:00) Then the haptic fires on schedule based on locally cached session times
- Given WiFi has been down for >30 seconds When I glance at the Watch Then I see a connectivity status indicator (FR28) but all timers/haptics continue normally

#### W5.2: Action Queue & Sync Recovery

As an organizer, I want my offline actions to queue locally and sync when connectivity returns, so that no actions are lost even during WiFi outages.

**AC:**
- Given I'm offline When I tap "Done" or trigger a cascade Then the action is queued persistently to disk (NFR11: survives app restart)
- Given queued actions exist When WiFi connectivity is restored Then all queued actions replay to the backend in order within 5 seconds
- Given a queued action conflicts with server state When replay occurs Then the server resolves idempotently and the Watch reconciles to server-authoritative state
- Given the app restarts while offline When I reopen the app Then the cached schedule, countdown state, and action queue are all preserved

#### W5.3: Connectivity Monitoring & Adaptive Behavior

As an organizer, I want the app to adapt its behavior based on connectivity and battery, so that it's always reliable and battery-efficient.

**AC:**
- Given WiFi drops When the transition occurs Then the app seamlessly enters offline mode with no user action required (NFR10)
- Given WiFi returns When the transition occurs Then the app reconnects WebSocket, syncs state from server, replays queued actions — all automatically
- Given battery drops below 20% When the threshold is crossed Then network polling frequency reduces (NFR23) to preserve battery for haptics and display
- Given the app has been running for 3 hours When I check battery Then battery is >30% remaining (NFR21)
