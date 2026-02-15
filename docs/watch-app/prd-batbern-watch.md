# PRD: BATbern Apple Watch Companion App

**Version:** 1.0
**Date:** 2026-02-15
**Status:** Draft
**Author:** PM (AI-assisted)

---

## 1. Executive Summary

The BATbern Apple Watch app serves **two distinct audiences on one wrist**: a **public event companion** for all attendees and an **organizer command center** for the 4 BATbern organizers. Separated by a swipe gesture, the left zone lets anyone browse tonight's event — sessions, speakers, abstracts — while the right zone gives organizers live countdown control, haptic alerts, and team-synchronized session management.

The app is distributed via the App Store, making it available to all ~200 BATbern attendees. No login is required for the public view. Organizers pair their Watch once via a simple code and never touch a password again.

---

## 2. Problem Statement

### For Attendees
BATbern attendees currently rely on printed programs or pulling out their phones to check what session is next. A glance at their wrist would be faster, more natural, and less disruptive during talks.

### For Organizers
The 4 BATbern organizers currently use stopwatch apps, printed schedules, and verbal coordination to manage real-time session flow. This leads to:
- **Timing drift**: No authoritative countdown visible to the moderator
- **Missed transitions**: Reliance on memory for 5-min/1-min warnings
- **Team blind spots**: Other organizers don't know the current session state
- **Manual friction**: Constantly checking phones during a live event

### Why Apple Watch
- 38mm screen forces focus — one piece of information at a time
- Haptic Taptic Engine provides silent, personal alerts without disturbing the audience
- Digital Crown enables natural scrolling through sessions
- Always-on display means the countdown is visible at a wrist glance
- Direct WiFi capability enables phone-free operation

---

## 3. Product Vision

**"Everything you need to know about BATbern tonight — on your wrist."**

For attendees: a conference companion that shows what's on, who's speaking, and what's next.
For organizers: a command center that counts down, alerts, and keeps the whole team in sync.

---

## 4. User Personas

### Attendee (Ana)
- Architect at a Bern firm, attends BATbern regularly
- Wants to quickly check the next session without pulling out her phone
- Doesn't want to create an account or log in
- Tech-comfortable but not a power user

### Organizer (Olivier)
- One of 4 BATbern organizers
- Manages session flow from the room: introduces speakers, manages Q&A timing
- Needs silent alerts that only he can feel (not audible in the room)
- Wants to know what session other organizers are managing in parallel rooms

---

## 5. Navigation Architecture

The app uses a **horizontal paging model** with two zones:

```
┌─────────────────────────────────────────────────┐
│                                                   │
│   ◄── SWIPE LEFT          SWIPE RIGHT ──►         │
│                                                   │
│  ┌─────────────┐         ┌──────────────┐         │
│  │  PUBLIC      │         │  ORGANIZER    │         │
│  │  EVENT VIEW  │  ◄───► │  COMMAND      │         │
│  │             │         │  CENTER       │         │
│  │  (no login)  │         │  (paired)     │         │
│  └─────────────┘         └──────────────┘         │
│                                                   │
│  Crown scroll:            Crown scroll:            │
│  Sessions ↕               Session detail ↕         │
│                                                   │
│  Tap title: Abstract       Tap: Session control    │
│  Tap speaker: Bio                                  │
│                                                   │
└─────────────────────────────────────────────────┘
```

### 5.1 Public Zone (Left — Default on Launch)

The app launches into the public zone. No login required.

**Screen 1: Event Hero**
- Event theme image as full-bleed background
- Event title overlay
- Bottom bar: date, time, venue name
- Visual: `themeImageUrl` from `GET /api/v1/events/current`

**Crown Scroll Down → Session Pages (one per session)**

Each session page is a vertically-split card:

```
┌──────────────────────┐
│  09:00 – 09:45       │  ← Time slot
│  ─────────────────── │
│                       │
│  Cloud Native         │  ← Title (tappable → abstract)
│  Security in 2026     │
│                       │
│  ─────────────────── │
│  ┌────┐  ┌────┐      │  ← Speaker area (tappable → bio)
│  │ 📷 │  │ 📷 │      │     Portrait + company logo grid
│  │John│  │Jane│      │
│  │ACME│  │Corp│      │
│  └────┘  └────┘      │
└──────────────────────┘
```

**Tap Interactions:**
- **Tap upper area (title)**: Push to abstract detail screen showing `session.description`
- **Tap lower area (speakers)**: Push to speaker detail screen
  - Single speaker: Full portrait, name, company, `speaker.bio`
  - Multiple speakers: Grid of portraits + company logos; tap individual speaker for bio

**Multi-Speaker Grid Layout:**
```
┌──────────────────────┐
│  ┌──────┐ ┌──────┐   │
│  │  📷  │ │  📷  │   │   2 speakers: side by side
│  │ Name │ │ Name │   │
│  │ Logo │ │ Logo │   │
│  └──────┘ └──────┘   │
│                       │
│  ┌────┐┌────┐┌────┐  │
│  │ 📷 ││ 📷 ││ 📷 │  │   3+ speakers: compact grid
│  │Name││Name││Name│  │
│  └────┘└────┘└────┘  │
└──────────────────────┘
```

**Crown continues → next session → ... → last session**

Sessions are ordered by `startTime`. Break/lunch sessions are shown as simple cards without speaker areas.

### 5.2 Organizer Zone (Right — Swipe Right from Any Public Screen)

Swiping right from any public screen transitions to the organizer zone.

**If not paired:** Shows pairing screen (see Section 6.2).

**If paired but >1 hour before event:** Shows speaker portrait overview — a scrollable grid of all confirmed speakers for tonight, so the organizer can recognize faces before the event.

**If paired and within event window:** Shows the live countdown and session control interface (Epics 3-4).

### 5.3 Navigation Summary

| Gesture | Action |
|---|---|
| **Launch** | Public zone → Event hero screen |
| **Crown scroll down** | Next session page |
| **Crown scroll up** | Previous session page |
| **Tap title area** | Push → Abstract detail |
| **Tap speaker area** | Push → Speaker bio (or multi-speaker grid → tap → individual bio) |
| **Swipe right** | Switch to organizer zone |
| **Swipe left** | Return to public zone |
| **Digital Crown press** | Return to current/active session (organizer zone) |

---

## 6. Feature Requirements

### 6.1 Public Event Companion (Epic 1)

#### FR-P1: Current Event Discovery
- On launch, fetch current event via `GET /api/v1/events/current?expand=sessions,speakers`
- Display event hero screen with theme image, title, date/time, venue
- If no current event, show "No upcoming event" with next event date if available

#### FR-P2: Session Browsing
- Display one session per page, scrollable via Digital Crown
- Show: time slot (`startTime`–`endTime`), title, session type badge
- Sessions ordered by `startTime`
- Break/lunch sessions shown as simple dividers (type badge, time, no speakers)
- Session types: keynote, presentation, workshop, panel_discussion, networking, break, lunch

#### FR-P3: Session Abstract
- Tap on title area pushes to abstract detail screen
- Display `session.description` (the talk abstract)
- Scrollable via Digital Crown for long abstracts
- Back navigation via swipe right or system back

#### FR-P4: Speaker Display
- Single speaker: Show portrait (`profilePictureUrl`), full name, company name, company logo (`logoUrl` from company endpoint)
- Multiple speakers: Grid layout — portrait + company logo per speaker, side by side
- Tap individual speaker → push to bio detail

#### FR-P5: Speaker Bio Detail
- Full portrait image (as large as Watch allows)
- Name, company, speaker role badge
- Bio text (`speaker.bio`), scrollable via Crown
- Company logo below bio

#### FR-P6: Progressive Publishing Respect
- Respect `currentPublishedPhase` from API:
  - `TOPIC`: Show hero screen only, no sessions
  - `SPEAKERS`: Show session cards with speakers, no times
  - `AGENDA`: Show full timetable with times
- Graceful degradation for missing data

#### FR-P7: Offline Cache
- Cache last-fetched event data in SwiftData
- If network unavailable on launch, display cached data with "Last updated" indicator
- Background refresh when connectivity restored

#### FR-P8: Theme Image Display
- Fetch `themeImageUrl` (CloudFront CDN)
- Display as full-bleed background on hero screen
- Apply dark gradient overlay for text readability
- Cache image locally after first download

### 6.2 Watch Pairing & Organizer Access (Epic 2)

#### FR-A1: Pairing Code Generation
- On first swipe-right to organizer zone, Watch generates a 6-character alphanumeric pairing code (e.g., `BAT-7K92`)
- Code displayed prominently on Watch screen
- Code persists until explicitly re-generated or pairing succeeds

#### FR-A2: Web Frontend Pairing UI
- New section in organizer's profile page: "Watch Pairing"
- Input field for pairing code
- On submit: backend validates code and associates Watch device with organizer's user account
- Shows paired status with "Unpair" option

#### FR-A3: Pairing Code Backend
- New endpoint: `POST /api/v1/users/{username}/watch-pairing`
  - Request: `{ "pairingCode": "BAT-7K92" }`
  - Response: `{ "pairingToken": "<JWT>", "organizer": { "username": "...", "firstName": "..." } }`
- New endpoint: `DELETE /api/v1/users/{username}/watch-pairing` (unpair)
- New endpoint: `POST /api/v1/watch/authenticate`
  - Request: `{ "pairingToken": "<stored-token>" }`
  - Response: `{ "accessToken": "<short-lived-JWT>", "expiresIn": 3600 }`
- Pairing tokens stored in user profile, never expire (until explicitly unpaired)
- Max 2 paired watches per organizer (primary + backup)

#### FR-A4: Auto-Authentication Flow
- Watch stores pairing token in Keychain after successful pairing
- On swipe-right to organizer zone:
  1. Send stored pairing token to `POST /api/v1/watch/authenticate`
  2. Receive short-lived access JWT (1 hour)
  3. Use JWT for all organizer API calls
  4. Auto-refresh before expiry
- If pairing token rejected (unpaired): show pairing screen again

#### FR-A5: Pre-Event Speaker Overview
- When paired and >1 hour before event start:
  - Display grid of confirmed speaker portraits
  - Name and company below each portrait
  - Scrollable via Crown
  - Purpose: "know the faces before the event"
- Transition to live mode at T-60 minutes automatically

#### FR-A6: Pairing Status Indicator
- Small icon in organizer zone corner: paired (checkmark) or unpaired (link icon)
- Organizer's first name shown after successful auth: "Hi, Olivier"

### 6.3 Live Countdown & Haptic Awareness (Epic 3)

#### FR-C1: Active Session Display
- Show current session with prominent countdown timer (MM:SS)
- Session title, speaker name(s), session type
- Progress ring around the Watch face showing elapsed percentage
- Color transitions: Green (>10min left) → Yellow (5-10min) → Orange (2-5min) → Red (<2min)

#### FR-C2: Next Session Preview
- Below active session (Crown scroll down): upcoming session preview
- "Up Next" label with title and speaker
- Time until next session starts

#### FR-C3: Haptic Alert Schedule
Each alert uses a **distinct haptic pattern** so the organizer learns to recognize them by feel:

| Moment | Haptic Pattern | Visual |
|---|---|---|
| Session start | 3× strong taps (`.notification`) | Green pulse |
| Halfway mark | 2× medium taps (`.directionUp`) | — |
| 10 min remaining | 1× gentle tap (`.click`) | Yellow transition |
| 5 min remaining | 2× firm taps (`.directionDown`) | Orange transition |
| 2 min remaining | 3× rapid taps (`.retry`) | Red flash |
| 1 min remaining | Long press (`.success`) | Red pulse |
| Time's up | 5× strong taps (`.failure`) | Red solid |
| Overtime 1 min | Continuous pulse every 15s | Red + overtime counter |

#### FR-C4: Overtime Tracking
- When session exceeds planned `endTime`, switch to overtime counter (+MM:SS in red)
- Haptic pulse every 15 seconds during overtime
- Show impact: "Next session delayed by X min"

#### FR-C5: Always-On Display
- Countdown timer visible in always-on (dimmed) mode
- Simplified view: time remaining + color indicator only
- Full detail on wrist raise

#### FR-C6: Session Timeline View
- Crown scroll through all sessions of the day
- Current session highlighted
- Past sessions dimmed with checkmark
- Future sessions with scheduled times
- Tap any session to view its details

### 6.4 Session Control & Team Sync (Epic 4)

#### FR-S1: Session State Transitions
- **Start Session**: Tap "Start" button — begins countdown for this session
- **Extend Session**: Tap "+" to add 5 minutes (with confirmation)
- **End Session Early**: Swipe up on active session → "End Now" confirmation
- **Skip to Next**: Long press on "Next" → confirms skip

#### FR-S2: Multi-Organizer Sync
- When one organizer starts/extends/ends a session, ALL paired organizer watches update within 2 seconds
- Sync mechanism: WebSocket connection to event management service
- Conflict resolution: First action wins, others see updated state
- Visual indicator of who initiated the action: "Started by Olivier"

#### FR-S3: WebSocket Real-Time Channel
- Endpoint: `wss://api.batbern.ch/ws/events/{eventCode}/live`
- Authentication: JWT from pairing token flow
- Message types:
  - `SESSION_STARTED { sessionSlug, startedBy, startTime }`
  - `SESSION_EXTENDED { sessionSlug, extendedBy, newEndTime, minutesAdded }`
  - `SESSION_ENDED { sessionSlug, endedBy, endTime }`
  - `SESSION_SKIPPED { sessionSlug, skippedBy }`
  - `HEARTBEAT { timestamp }`
- Reconnection with exponential backoff on disconnect

#### FR-S4: Complication Support
- Watch face complication showing:
  - Current session name (truncated)
  - Time remaining
  - Color-coded urgency
- Complication updates every minute via Timeline

#### FR-S5: Quick Actions from Notification
- When haptic fires, tappable notification allows:
  - "Extend +5 min" (at 2-min and 1-min warnings)
  - "View Session" (at any alert)
  - "End Now" (at time's-up alert)

### 6.5 Offline Resilience (Epic 5)

#### FR-O1: Full Offline Operation
- Complete event schedule cached in SwiftData before event starts
- Countdown timers run locally — no network dependency
- Haptic schedule computed locally from session times

#### FR-O2: Sync Recovery
- When connectivity lost: "Offline" indicator, local operation continues
- When connectivity restored:
  - Pull latest session states from server
  - Reconcile local timer with server time
  - Push any queued local actions (extend, end)
- Conflict resolution: Server state wins, local adjustments applied

#### FR-O3: Direct WiFi
- Watch connects directly to venue WiFi (no iPhone dependency)
- Fallback chain: WiFi → Bluetooth (via iPhone) → Offline mode
- Connection status indicator in organizer zone

---

## 7. Data Architecture

### 7.1 Public Data (No Auth Required)

All data sourced from existing public API endpoints:

| Data | Endpoint | Key Fields |
|---|---|---|
| Current event | `GET /api/v1/events/current?expand=sessions,speakers` | eventCode, title, date, themeImageUrl, venueName, typicalStartTime/EndTime |
| Event sessions | Included in expand | sessionSlug, title, description, sessionType, startTime, endTime, speakers[] |
| Speaker info | Included in session speakers[] | firstName, lastName, company, profilePictureUrl, bio, speakerRole |
| Company logos | `GET /api/v1/companies/{companyName}?expand=logo` | logoUrl |

**No new backend endpoints needed for Epic 1.**

### 7.2 Pairing & Auth Data (New Endpoints)

| Endpoint | Method | Auth | Purpose |
|---|---|---|---|
| `/api/v1/watch/pair` | POST | None (code validates) | Exchange pairing code for pairing token |
| `/api/v1/watch/authenticate` | POST | Pairing token | Exchange pairing token for access JWT |
| `/api/v1/users/{username}/watch-pairing` | POST | Organizer JWT | Register pairing code from web frontend |
| `/api/v1/users/{username}/watch-pairing` | DELETE | Organizer JWT | Unpair watch |
| `/api/v1/users/{username}/watch-pairing` | GET | Organizer JWT | Check pairing status |

### 7.3 Live Session Data (New WebSocket)

| Channel | Auth | Purpose |
|---|---|---|
| `wss://.../ws/events/{eventCode}/live` | Access JWT | Real-time session state sync |

### 7.4 Local Storage (SwiftData)

```swift
@Model class CachedEvent {
    var eventCode: String
    var title: String
    var date: Date
    var themeImageUrl: String?
    var venueName: String
    var startTime: String
    var endTime: String
    var sessions: [CachedSession]
    var lastSynced: Date
}

@Model class CachedSession {
    var sessionSlug: String
    var title: String
    var abstract: String?
    var sessionType: String
    var startTime: Date?
    var endTime: Date?
    var speakers: [CachedSpeaker]
    var state: SessionState  // scheduled, active, completed, skipped
}

@Model class CachedSpeaker {
    var username: String
    var firstName: String
    var lastName: String
    var company: String?
    var companyLogoUrl: String?
    var profilePictureUrl: String?
    var bio: String?
    var speakerRole: String
}

@Model class PairingInfo {
    var pairingToken: String  // Stored in Keychain, referenced here
    var organizerUsername: String
    var organizerFirstName: String
    var pairedAt: Date
}
```

---

## 8. Epic Breakdown

### Epic 1: Public Event Companion
**Goal:** Anyone with an Apple Watch can browse tonight's BATbern event — theme, schedule, session details, speaker bios and portraits — by scrolling with the Digital Crown. No login required.

**Scope:** FR-P1 through FR-P8

**Backend work:** None — uses existing public endpoints.

**Key deliverables:**
- Xcode project setup (watchOS 11, Swift, SwiftUI)
- Event data fetching and SwiftData caching
- Hero screen with theme image background
- Session page carousel (Crown scrollable)
- Abstract detail view (tap title)
- Speaker bio detail view (tap speakers)
- Multi-speaker grid layout
- Progressive publishing support
- Offline cache with stale indicator

**App Store:** Yes — this epic alone is a shippable product. Submit for TestFlight after Epic 1.

---

### Epic 2: Watch Pairing & Organizer Access
**Goal:** Organizer pairs their Watch once via a simple code, then swipes right to enter the organizer zone — automatically authenticated, no passwords ever.

**Scope:** FR-A1 through FR-A6

**Backend work:**
- Pairing code endpoints (company-user-management-service)
- Watch authentication token exchange
- Web frontend profile extension ("Watch Pairing" section)

**Key deliverables:**
- Horizontal paging navigation (public ↔ organizer zones)
- Pairing code generation and display
- Web frontend "Watch Pairing" UI in organizer profile
- Backend pairing code → token exchange
- Auto-authentication on swipe-right
- Pre-event speaker portrait overview
- Keychain storage for pairing token

---

### Epic 3: Live Countdown & Haptic Awareness
**Goal:** During the event, the organizer's wrist counts down, shows speaker info, and fires distinct haptic alerts at every critical moment.

**Scope:** FR-C1 through FR-C6

**Backend work:** Session timing state endpoint (or derive from existing event data).

**Key deliverables:**
- Active session countdown (MM:SS) with progress ring
- Color-coded urgency transitions
- 8 distinct haptic patterns for timing milestones
- Overtime tracking with delay impact
- Next session preview
- Always-on display support
- Session timeline view
- Watch face complication

---

### Epic 4: Session Control & Team Sync
**Goal:** One tap advances the event for ALL organizer watches simultaneously.

**Scope:** FR-S1 through FR-S5

**Backend work:**
- WebSocket endpoint for real-time session state
- Session state machine (start, extend, end, skip)
- Multi-device conflict resolution

**Key deliverables:**
- Session start/extend/end/skip controls
- WebSocket real-time sync across all organizer watches
- Conflict resolution (first action wins)
- "Started by [name]" attribution
- Quick actions from haptic notifications

---

### Epic 5: Offline Resilience
**Goal:** The Watch never fails, even when WiFi drops.

**Scope:** FR-O1 through FR-O3

**Backend work:** None (resilience is client-side).

**Key deliverables:**
- Pre-event full schedule sync to SwiftData
- Local countdown timer (no network dependency)
- Local haptic schedule computation
- Connectivity monitoring and status indicator
- Sync recovery with server reconciliation
- Action queue for offline session control operations
- Direct WiFi configuration

---

## 9. Non-Functional Requirements

### Performance
| Metric | Target |
|---|---|
| App launch to hero screen | <2 seconds (cached), <4 seconds (cold) |
| Crown scroll between sessions | <100ms per page transition |
| Haptic timing accuracy | ±1 second of scheduled moment |
| WebSocket message delivery | <2 seconds organizer-to-organizer |
| Battery impact during event | <15% for 4-hour event (organizer mode) |

### Compatibility
- watchOS 11+ (Apple Watch Series 8, SE 2nd gen, Ultra, and later)
- No iPhone app required (standalone watchOS app)
- Direct WiFi support required (Series 6+ have this)

### Accessibility
- VoiceOver support for all screens
- Dynamic Type support for text scaling
- High contrast mode for countdown colors
- Haptic-only alerts (no audio in default mode)

### Security
- Pairing tokens stored in Keychain (not UserDefaults)
- Short-lived JWTs (1 hour) for organizer API calls
- Pairing codes expire after 24 hours if unused
- Max 2 watches per organizer account
- Unpair from web frontend removes Watch access immediately

### Localization
- German (primary), English, French
- Matches existing web frontend i18n keys where applicable

---

## 10. Success Metrics

### Attendee Engagement (Epic 1)
- **Installs**: 30+ attendees install the app within first 2 events
- **Session views**: Average 5+ sessions browsed per attendee per event
- **Retention**: 60%+ of installers use it at the next event

### Organizer Efficiency (Epics 2-4)
- **Timing accuracy**: Sessions end within ±2 minutes of planned time (vs. ±5-10 min today)
- **Transition speed**: <30 seconds between sessions (measured from end to next start)
- **Adoption**: All 4 organizers using Watch within 2 events

### Reliability (Epic 5)
- **Uptime**: Zero missed haptic alerts per event
- **Offline**: Watch remains functional for full event if WiFi drops at start

---

## 11. Out of Scope (v1)

- iPhone companion app (watchOS standalone only)
- Attendee voting or feedback from Watch
- Live Q&A display on Watch
- Speaker-facing Watch features (speakers don't get special Watch access)
- Push notifications for event reminders (rely on calendar/web notifications)
- Partner/sponsor display on Watch
- Registration from Watch (use phone/web)
- Multiple events on same day (BATbern runs one event at a time)

---

## 12. Open Questions

| # | Question | Status |
|---|---|---|
| 1 | Should the app be free on App Store or bundled with BATbern registration? | **Leaning: Free** — maximize adoption |
| 2 | Do we need a privacy policy page for App Store submission? | **Yes** — required by Apple, minimal since no PII collected in public mode |
| 3 | Should speaker portraits be pre-cached at a specific resolution for Watch? | To discuss — current CloudFront URLs serve full-size images |
| 4 | WebSocket infrastructure: add to existing API Gateway or separate lightweight service? | To discuss in architecture phase |
| 5 | Should the pairing code be numeric-only (easier to read on small screen) or alphanumeric? | **Leaning: Numeric 6-digit** for readability |

---

## 13. Dependencies

| Dependency | Status | Notes |
|---|---|---|
| Public event API endpoints | ✅ Available | All needed endpoints exist and are production-ready |
| Speaker/session data in API | ✅ Available | Full data including bios, portraits, abstracts |
| Company logo endpoint | ✅ Available | `GET /api/v1/companies/{name}?expand=logo` |
| CloudFront CDN for images | ✅ Available | Theme images, speaker photos, company logos |
| Organizer profile page | ✅ Available | Extend with "Watch Pairing" section |
| WebSocket infrastructure | ❌ New | Required for Epic 4 (team sync) |
| Pairing code backend | ❌ New | Required for Epic 2 |
| Apple Developer account | ❓ Needed | For App Store / TestFlight distribution |
| Xcode + watchOS SDK | ❓ Needed | Development environment for native Watch app |

---

## 14. Appendix: API Endpoint Reference

### Existing Public Endpoints Used

```
GET  /api/v1/events/current?expand=sessions,speakers    → EventDetail with sessions & speakers
GET  /api/v1/events/{eventCode}?expand=sessions,speakers → EventDetail by code
GET  /api/v1/events/{eventCode}/sessions                 → Session list
GET  /api/v1/events/{eventCode}/sessions/{slug}          → Session detail
GET  /api/v1/companies/{companyName}?expand=logo         → Company with logo URL
GET  /api/v1/config                                      → Frontend config (API URLs, feature flags)
```

### New Endpoints Required

```
POST   /api/v1/watch/pair              → Exchange pairing code for pairing token
POST   /api/v1/watch/authenticate      → Exchange pairing token for access JWT
POST   /api/v1/users/{username}/watch-pairing   → Register pairing code (web frontend)
GET    /api/v1/users/{username}/watch-pairing   → Get pairing status
DELETE /api/v1/users/{username}/watch-pairing   → Unpair watch
WSS    /ws/events/{eventCode}/live              → Real-time session state channel
```

### Data Structures (from existing API)

**EventDetail** (subset relevant to Watch):
- `eventCode`, `title`, `date`, `themeImageUrl`, `venueName`, `venueAddress`
- `typicalStartTime`, `typicalEndTime`, `currentPublishedPhase`
- `sessions[]` (when expanded)

**Session** (subset relevant to Watch):
- `sessionSlug`, `title`, `description` (abstract), `sessionType`
- `startTime`, `endTime`, `room`
- `speakers[]` (when expanded)

**SessionSpeaker** (subset relevant to Watch):
- `firstName`, `lastName`, `company`, `profilePictureUrl`, `bio`, `speakerRole`

---

## 15. Repository Structure & Artifact Separation

The Watch app lives alongside the main BATbern platform in the same monorepo but is **completely independent** of the Gradle/Java/TypeScript build system.

### Code Location

```
apps/BATbern-watch/           # Xcode project (Swift, SwiftUI, watchOS 11)
  BATbernWatch/               # Main Watch app target
  BATbernWatchTests/          # Unit tests
  BATbernWatch.xcodeproj/     # Xcode project file
```

The `apps/` folder already contains legacy/auxiliary projects (`BATspa-old`, `BATbern-comming-soon`, etc.). The Watch app fits naturally here.

### Documentation Location

All Watch planning artifacts are in `docs/watch-app/`, separate from the platform's `docs/stories/` and `docs/architecture/`:

```
docs/watch-app/               # All Watch docs
  prd-batbern-watch.md        # This PRD (authoritative)
  architecture.md             # Architecture decisions
  ux-design-specification.md  # UX design spec
  ux-design-directions.html   # Visual design mockups
  product-brief.md            # Initial product brief
  epics.md                    # Epic breakdown
  stories/                    # Watch stories (W-prefixed)
    W1.1-xcode-project-setup.md
    W1.2-event-hero-screen.md
    W2.1-pairing-code-flow.md
    ...
```

### Story Naming Convention

Watch stories use a **W prefix** to avoid collision with platform story numbers:

| Convention | Example | Meaning |
|---|---|---|
| `W{epic}.{story}` | `W1.2` | Watch Epic 1, Story 2 |
| Platform | `6.3` | Platform Epic 6, Story 3 |

This ensures `grep`, file listings, and CI workflows never confuse Watch and platform stories.

### Cross-Cutting Backend Work

Some Watch epics require changes to existing platform services:

| Watch Epic | Platform Service Affected | What Changes |
|---|---|---|
| W2 (Pairing) | `company-user-management-service` | New pairing endpoints, user profile extension |
| W2 (Pairing) | `web-frontend` | "Watch Pairing" section in organizer profile |
| W4 (Team Sync) | `event-management-service` | WebSocket endpoint, session state machine |

These backend changes are **implemented in the platform codebase** (existing services) but **tracked in Watch stories**. Each Watch story that requires backend work will note which service and files are affected.

### Build System Independence

| Concern | Platform | Watch App |
|---|---|---|
| Build tool | Gradle + npm | Xcode |
| Language | Java 21 + TypeScript | Swift |
| `make build` | Builds platform | Does NOT touch Watch |
| `make test` | Tests platform | Does NOT touch Watch |
| CI/CD | GitHub Actions → ECS | Separate workflow → TestFlight |
| Deploy | AWS CDK | App Store Connect |
