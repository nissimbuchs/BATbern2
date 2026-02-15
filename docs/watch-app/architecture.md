---
stepsCompleted: [1, 2, 3, 4, 5, 6, 7, 8]
inputDocuments:
  - prd-batbern-watch.md
  - ux-design-specification.md
  - product-brief-BATbern-2026-02-14.md
  - brainstorming-session-2026-02-14.md
  - ux-design-directions.html
  - docs/architecture/index.md
  - docs/architecture/01-system-overview.md
  - docs/architecture/04-api-event-management.md
  - docs/architecture/06-backend-architecture.md
  - docs/architecture/06d-notification-system.md
  - docs/architecture/tech-stack.md
workflowType: 'architecture'
project_name: 'BATbern Watch'
user_name: 'Nissim'
date: '2026-02-15'
---

# Architecture Decision Document

_BATbern Watch — Standalone watchOS companion app with dual-zone architecture: public event browsing for all attendees + real-time event orchestration for organizers._

> **Status:** READY FOR IMPLEMENTATION
> **Last Updated:** 2026-02-15 (v2.0 — aligned with PRD v2.0 + UX Spec v2.0)
> **Audience:** Developers, Architect, Product Owner
> **Depends On:** [PRD](prd-batbern-watch.md) · [UX Spec](ux-design-specification.md) · [Design Directions](ux-design-directions.html)

---

## Project Context Analysis

### Requirements Overview

**Functional Requirements — Architectural Implications:**

| FR Group | Count | Architectural Impact |
|---|---|---|
| Schedule display & awareness (FR1-5) | 5 | Local cache, WidgetKit complications, SwiftData persistence |
| Session lifecycle (FR6-10) | 5 | Event state machine, STOMP actions, cascade algorithm |
| Time alerting & haptics (FR11-16) | 6 | HapticScheduler, timer engine, Extended Runtime session |
| Team synchronization (FR17-20) | 4 | WebSocket broadcast, presence tracking, conflict resolution |
| Event setup & connection (FR21-24) | 4 | Pairing code auth, REST endpoints, WiFi-direct connectivity |
| Offline resilience (FR25-28) | 4 | ActionQueue, SwiftData cache, connectivity state machine |
| Public zone — attendee browsing (FR29-35) | 7 | Existing public REST endpoints, SwiftData cache, progressive publishing |
| Pre-event — speaker arrival tracking (FR36-39) | 4 | WebSocket SPEAKER_ARRIVED message, arrival state in DB, portrait grid UI |

**Non-Functional Requirements — Driving Decisions:**

| NFR | Target | Architecture Driver |
|---|---|---|
| Complication update latency (NFR1) | < 1 second | WidgetKit timeline + Extended Runtime |
| Haptic delivery accuracy (NFR2) | < 1 second | Wall-clock timer (not decrementing counter) |
| Schedule cascade propagation (NFR3) | < 3 seconds | WebSocket broadcast to all subscribers |
| Initial sync (NFR4) | < 5 seconds | Single REST GET + portrait batch download |
| App launch to usable (NFR5) | < 3 seconds | SwiftData cached state, lazy portrait load |
| Crown scroll latency (NFR6) | < 100ms | Native SwiftUI TabView paging |
| Public zone launch (NFR7) | < 2s cached, < 4s cold | SwiftData cache-first, async network refresh |
| Battery at end of event (NFR21-22) | > 30%, < 15% drain/4h | Single persistent WebSocket, adaptive polling |
| Offline transition (NFR10) | Seamless | No user action; automatic state machine |
| Max watches per organizer (NFR19) | 2 | Server-side pairing limit enforcement |
| Pairing code expiry (NFR20) | 24 hours | Server-side TTL on pairing codes |
| Localization (NFR32-33) | DE, EN, FR | String catalogs, i18n key alignment with web frontend |
| Accessibility (NFR28-31) | VoiceOver, Dynamic Type, High Contrast | Native SwiftUI accessibility support |
| Continuous reliability (NFR8) | Zero crashes in 3 hours | Extended Runtime, defensive error boundaries |

### Scale & Complexity

- **Primary domain:** Dual-zone event companion — public browsing (200+ attendees) + real-time team coordination (4 organizers)
- **Complexity level:** Medium-high (real-time sync, offline resilience, multi-device coordination, dual-zone navigation)
- **Users:** 200+ concurrent (public zone, read-only) + 4 concurrent organizers (read-write)
- **Estimated architectural components:** watchOS app (13 screens, 3 complication types, 3 domain modules, 5 data modules) + backend extensions (2 services modified, 9 new REST endpoints, WebSocket handlers)

### Technical Constraints & Dependencies

| Constraint | Impact |
|---|---|
| watchOS 11+ minimum | SwiftUI lifecycle, WidgetKit (not ClockKit), SwiftData, latest navigation APIs |
| Standalone (no iPhone) | Direct WiFi to backend; no WatchConnectivity framework |
| Existing STOMP WebSocket | Must extend, not replace, `WebSocketConfig.java` in event-management-service |
| Pairing code authentication | No direct Cognito on Watch; pairing code → token exchange via backend |
| Existing PostgreSQL schema | Flyway migrations for session fields + pairing tables + arrival tracking |
| Venue WiFi dependency | Offline mode is mandatory, not optional |
| 4-user organizer scale | In-memory broker sufficient; no Kafka/RabbitMQ needed |
| 200+ public users | Read-only; existing public API endpoints handle this load already |
| App Store distribution | Single app binary serves both public and organizer zones |

### Cross-Cutting Concerns Identified

1. **Authentication** — Pairing code flow for organizers; no auth for public zone. JWT flows through REST and STOMP; token refresh mid-event
2. **Dual-zone navigation** — Horizontal paging separates public (left) and organizer (right) zones; state-dependent organizer entry screen
3. **Offline resilience** — Affects every layer (timer, haptics, actions, UI state); public zone works fully offline with cached data
4. **State synchronization** — Server-authoritative model with local cache reconciliation; full-state broadcasts on every change
5. **Error handling** — Graceful degradation, never crash during live event
6. **Battery management** — Adaptive behavior across all components based on battery level
7. **Progressive publishing** — Public zone respects event publishing phases (TOPIC / SPEAKERS / AGENDA)

---

## Starter Template Evaluation

### Primary Technology Domain

**Dual-stack project:**
- **watchOS client:** Native Swift/SwiftUI app (no cross-platform option for watchOS)
- **Backend extensions:** Java/Spring Boot additions to existing monorepo (two services)

### Starter Options Considered

| Option | Verdict | Reason |
|---|---|---|
| Xcode watchOS App template | **Selected** | Standard Apple template for standalone Watch apps |
| SwiftUI multiplatform | Rejected | Unnecessary complexity; Watch-only app |
| React Native / Flutter | Rejected | No watchOS support for standalone apps |
| Existing BATbern monorepo structure | **Extended** | Backend changes go into existing service packages |

### Selected Starters

**watchOS Client:**
```
Xcode → File → New → Project → watchOS → App
Product Name: BATbern-watch
Interface: SwiftUI
Language: Swift
Storage: SwiftData
Target: watchOS 11+
```

**Backend Extensions:**
```
Two services extended:
1. event-management-service — WebSocket handlers, session state, speaker arrival
   New package: ch.batbern.events.watch
2. company-user-management-service — Pairing code management
   New package: ch.batbern.companyuser.watch
```

**Architectural Decisions Provided by Starters:**

| Decision | Value |
|---|---|
| Language & Runtime | Swift 6.0 / watchOS 11+ (client) · Java 21 / Spring Boot 3.x (backend) |
| UI Framework | SwiftUI with watchOS app lifecycle |
| Persistence | SwiftData (client) · PostgreSQL 15+ (backend) |
| Networking | URLSession + StompClientLib (client) · Spring WebSocket (backend) |
| Build Tooling | Xcode 16+ / Swift Package Manager (client) · Gradle (backend) |
| Testing Framework | XCTest + Swift Testing (client) · JUnit 5 + Testcontainers (backend) |
| Code Organization | MVVM + Repository (client) · Layered DDD (backend) |

---

## Core Architectural Decisions

### Decision Priority Analysis

**Critical Decisions (Block Implementation):**
1. Dual-zone navigation architecture (horizontal paging, state-dependent organizer entry)
2. Pairing code authentication flow (no passwords on Watch)
3. Real-time sync protocol (WebSocket/STOMP)
4. State authority model (server-authoritative)
5. Offline resilience strategy (local cache + action queue)

**Important Decisions (Shape Architecture):**
6. Timer engine design (wall-clock calculation)
7. Haptic scheduling approach
8. Complication timeline strategy
9. Conflict resolution (idempotent actions)
10. Speaker arrival tracking (WebSocket + persistent state)
11. Public zone data flow (existing endpoints + SwiftData cache)

**Deferred Decisions (Post-MVP):**
12. APNs backup channel
13. Analytics dashboard
14. Speaker time signal / flash

### Data Architecture

**Database:** Existing PostgreSQL 15+ (RDS) — schema extension across two services

**New Columns on `sessions` Table (event-management-service):**

```sql
-- Flyway: V{next}__add_watch_session_fields.sql
ALTER TABLE sessions ADD COLUMN actual_start_time TIMESTAMP;
ALTER TABLE sessions ADD COLUMN actual_end_time TIMESTAMP;
ALTER TABLE sessions ADD COLUMN overrun_minutes INTEGER DEFAULT 0;
ALTER TABLE sessions ADD COLUMN completed_by_username VARCHAR(100);
```

**New Table for Speaker Arrival Tracking (event-management-service):**

```sql
-- Flyway: V{next}__add_speaker_arrival_tracking.sql
CREATE TABLE speaker_arrivals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_code VARCHAR(50) NOT NULL,
    speaker_username VARCHAR(100) NOT NULL,
    confirmed_by_username VARCHAR(100) NOT NULL,
    arrived_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (event_code, speaker_username)
);
CREATE INDEX idx_speaker_arrivals_event ON speaker_arrivals(event_code);
```

**New Table for Watch Pairing (company-user-management-service):**

```sql
-- Flyway: V{next}__add_watch_pairing.sql
CREATE TABLE watch_pairings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(100) NOT NULL,
    pairing_code VARCHAR(6),
    pairing_code_expires_at TIMESTAMP,
    pairing_token VARCHAR(256) UNIQUE,
    device_name VARCHAR(100),
    paired_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_watch_user FOREIGN KEY (username) REFERENCES users(username)
);
CREATE INDEX idx_watch_pairings_username ON watch_pairings(username);
CREATE INDEX idx_watch_pairings_code ON watch_pairings(pairing_code) WHERE pairing_code IS NOT NULL;
-- Enforce max 2 watches per organizer
CREATE UNIQUE INDEX idx_watch_pairings_limit ON watch_pairings(username, paired_at)
    WHERE paired_at IS NOT NULL;
```

**Client-Side Persistence:** SwiftData (aligned with PRD data models)

```swift
@Model class CachedEvent {
    var eventCode: String
    var title: String
    var eventDate: Date
    var themeImageUrl: String?
    var venueName: String
    var typicalStartTime: String
    var typicalEndTime: String
    var currentPublishedPhase: String?  // TOPIC, SPEAKERS, AGENDA
    var sessions: [CachedSession]
    var lastSyncTimestamp: Date
}

@Model class CachedSession {
    var sessionSlug: String
    var title: String
    var abstract: String?               // session.description
    var sessionType: String             // keynote, presentation, workshop, panel_discussion, networking, break, lunch
    var scheduledStartTime: Date?
    var scheduledEndTime: Date?
    var speakers: [CachedSpeaker]
    var state: SessionState             // scheduled, active, completed, skipped (organizer zone only)
    var actualStartTime: Date?          // organizer zone only
    var overrunMinutes: Int?            // organizer zone only
}

@Model class CachedSpeaker {
    var username: String
    var firstName: String
    var lastName: String
    var company: String?
    var companyLogoUrl: String?
    var profilePictureUrl: String?
    var bio: String?
    var speakerRole: String             // keynote_speaker, panelist, moderator
    var arrived: Bool                   // organizer zone: confirmed present at venue
    var arrivedConfirmedBy: String?     // username of confirming organizer
    var arrivedAt: Date?                // timestamp of arrival confirmation
}

@Model class PairingInfo {
    var pairingToken: String            // Reference — actual token in Keychain
    var organizerUsername: String
    var organizerFirstName: String
    var pairedAt: Date
}

enum SessionState: String, Codable {
    case scheduled, active, completed, skipped
}
```

**Portrait Cache:** File-based, ~100KB per speaker x 8 speakers = < 1MB per event. Watch-optimized resolution (max 200x200px).

**Validation Strategy:** Server validates all actions; client validates UI constraints only (e.g., can't tap "Done" before session starts). Max 2 watches per organizer enforced server-side.

**Migration Approach:** Flyway (existing pattern) — separate migrations per service.

### Authentication & Security

**Authentication Method:** Pairing Code Flow (no passwords on Watch)

The Watch never interacts with Cognito directly. Instead, organizers pair their Watch once via a 6-digit numeric code generated from the web frontend. This avoids any password entry on the tiny Watch screen.

**Pairing Flow:**

```
Step 1: Web Frontend (organizer profile)
  Organizer clicks "Pair Apple Watch" → POST /api/v1/users/{username}/watch-pairing
  → Backend generates 6-digit numeric code with 24-hour expiry
  → Code displayed in organizer's profile page

Step 2: Watch (swipe right → pairing screen O1)
  Organizer enters 6-digit code using Crown-scroll digit picker
  → POST /api/v1/watch/pair { code: "482715" }
  → Backend validates code, returns pairing token
  → Pairing token stored in Watch Keychain

Step 3: Ongoing authentication
  On each app launch / WebSocket connect:
  → POST /api/v1/watch/authenticate { pairingToken: "..." }
  → Backend validates pairing token, returns short-lived JWT (1 hour)
  → JWT used for all subsequent REST calls and STOMP CONNECT
```

**Token Management:**
- Pairing token: Long-lived (until explicitly unpaired), stored in Keychain
- Access JWT: 1 hour, stored in memory
- JWT refresh: Watch calls `/api/v1/watch/authenticate` with pairing token to get new JWT
- Event duration (~3 hours): 2-3 JWT refreshes, proactive at 10 minutes before expiry

**Security Rules:**
- Pairing codes: 6-digit numeric, expire after 24 hours, single-use
- Max 2 watches per organizer account (enforced server-side)
- Only `ORGANIZER` role permitted to pair
- Unpairing: Only from web frontend (prevents accidental unpairing during events)
- All communication encrypted via TLS (HTTPS + WSS)
- No PII stored on Watch beyond speaker names/portraits and organizer first name

**Security Middleware:** `JwtStompInterceptor` added to WebSocket channel interceptors — validates JWT on STOMP CONNECT.

**Public Zone:** No authentication whatsoever. All public data served from existing unauthenticated endpoints.

### API & Communication Patterns

#### Public Zone Data Flow (No New Backend)

The public zone uses existing public API endpoints — no new backend work:

```
GET /api/v1/events/current?expand=sessions,speakers    → Full event with sessions & speakers
GET /api/v1/companies/{companyName}?expand=logo         → Company logo URL
```

**Data flow:** App launch → check SwiftData cache → display cached data immediately → async REST fetch → update cache → refresh UI if changed. Cache-first, network-second.

**Progressive publishing:** Watch respects `currentPublishedPhase`:
- `TOPIC`: Show session titles only, hide speakers
- `SPEAKERS`: Show titles + speakers, hide abstracts
- `AGENDA`: Full detail (titles, speakers, abstracts, times)

#### Type Generation from OpenAPI Specification

**Decision:** Generate Swift types from OpenAPI spec using `openapi-generator` CLI instead of manually maintaining DTOs.

**Rationale:**
- **Type safety:** Compile-time validation that types match backend API contract
- **Zero maintenance:** Types auto-update when OpenAPI spec changes
- **Reduced errors:** Eliminated field name/type mismatches (e.g., missing optional `typicalStartTime` caught immediately)
- **DRY principle:** Single source of truth (OpenAPI spec) instead of duplicating type definitions

**Implementation:**

1. **Code Generation Script** (`apps/BATbern-watch/scripts/generate-types.sh`):
   ```bash
   # Generates Swift types from docs/api/events-api.openapi.yml
   openapi-generator generate \
     -i ../../docs/api/events-api.openapi.yml \
     -g swift5 \
     -o "BATbern-watch Watch App/Generated/temp" \
     --additional-properties=library=urlsession,responseAs=Codable \
     --global-property=models

   # Copies only essential types (EventDetail, Session, Speaker, etc.)
   # Removes admin/batch types not needed for Watch app
   ```

2. **Generated Types** (`BATbern-watch Watch App/Generated/Models/`):
   - `EventDetail.swift` — Full event response with sessions, speakers, venue
   - `Event.swift` — Base event model
   - `Session.swift` — Session with timing, type, speakers
   - `SessionSpeaker.swift` — Speaker role assignment
   - `Speaker.swift` — Speaker profile data
   - `Venue.swift`, `EventType.swift`, `EventWorkflowState.swift` — Supporting types

3. **Utility Helpers** (`BATbern-watch Watch App/Generated/OpenAPIUtilities.swift`):
   - `JSONEncodable` protocol — For encoding to JSON
   - `StringRule`, `NumericRule`, `ArrayRule` — Validation metadata (used by generated code)
   - `AnyCodable` — Dynamic JSON support

4. **Type Mapping Extensions** (`BATbern-watch Watch App/Generated/EventDetailExtensions.swift`):
   - `EventDetail → WatchEvent` — Generated API type → domain model
   - `EventDetail → CachedEvent` — Generated API type → SwiftData persistence model
   - `Session.SessionType → SessionType` — Maps generated enum to domain enum
   - `SessionSpeaker.SpeakerRole → SpeakerRole` — Maps `PRIMARY_SPEAKER` (backend) → `keynoteSpeaker` (domain)

**Type Flow:**
```
API JSON → EventDetail (generated) → WatchEvent (domain) → CachedEvent (SwiftData)
                                  ↓
                            PublicViewModel
                                  ↓
                            EventHeroView
```

**Regeneration Workflow:**
```bash
# After OpenAPI spec changes in docs/api/events-api.openapi.yml
cd apps/BATbern-watch
./scripts/generate-types.sh

# Xcode automatically detects new/changed files (PBXFileSystemSynchronizedRootGroup)
# Build to verify — compilation errors indicate breaking API changes
```

**Trade-offs:**
- ✅ **Pros:** Always in sync, zero manual maintenance, catches API changes at compile time
- ⚠️ **Cons:** Slightly verbose generated code, some unused types generated (but removed by script)
- ⚠️ **Limitation:** Apple's `swift-openapi-generator` doesn't support watchOS runtime (we use standalone CLI `openapi-generator` instead)

**Files managed:**
- ✅ **Generated (do not edit):** `Generated/Models/*.swift`, `Generated/OpenAPIUtilities.swift`
- ✅ **Manual (edit for domain logic):** `Generated/EventDetailExtensions.swift`
- ✅ **Version controlled:** All generated files committed to ensure build reproducibility

#### Pairing Endpoints (company-user-management-service)

```
POST   /api/v1/users/{username}/watch-pairing           # Generate pairing code (Organizer JWT)
GET    /api/v1/users/{username}/watch-pairing           # Check pairing status (Organizer JWT)
DELETE /api/v1/users/{username}/watch-pairing/{deviceId} # Unpair specific watch (Organizer JWT)
POST   /api/v1/watch/pair                               # Exchange code for pairing token (no auth)
POST   /api/v1/watch/authenticate                       # Exchange pairing token for JWT (pairing token)
```

#### Organizer Real-Time: WebSocket (STOMP)

```
# Server → Watch (subscriptions)
/topic/events/{eventCode}/state              # Full event state broadcasts
/topic/events/{eventCode}/arrivals           # Speaker arrival updates
/user/queue/watch/ack                        # Per-user action ACKs
/user/queue/watch/errors                     # Per-user errors

# Watch → Server (send destinations)
/app/watch/events/{eventCode}/join           # Organizer joins event
/app/watch/events/{eventCode}/leave          # Organizer leaves event
/app/watch/events/{eventCode}/action         # Session actions
/app/watch/events/{eventCode}/speaker-arrived  # Confirm speaker arrival
```

#### Organizer REST (setup + fallback)

```
GET  /api/v1/watch/events/{eventCode}/state                           # Polling fallback
GET  /api/v1/watch/events/{eventCode}/speakers/{username}/portrait    # Portrait presigned URL
POST /api/v1/watch/events/{eventCode}/actions                         # Offline queue replay
GET  /api/v1/watch/organizers/me/active-events                        # Event selection
GET  /api/v1/watch/events/{eventCode}/arrivals                        # Speaker arrival status
POST /api/v1/watch/events/{eventCode}/arrivals                        # Record speaker arrival (REST fallback)
```

#### Message Schemas

**State Update (Server → all Watch subscribers):**
```json
{
  "type": "STATE_UPDATE",
  "trigger": "SESSION_ENDED",
  "eventCode": "BATbern56",
  "currentSessionIndex": 2,
  "sessions": [
    {
      "sessionSlug": "cloud-native-pitfalls",
      "title": "Cloud-Native Pitfalls",
      "sessionType": "presentation",
      "scheduledStartTime": "2026-02-14T18:00:00Z",
      "scheduledEndTime": "2026-02-14T18:45:00Z",
      "status": "COMPLETED",
      "actualStartTime": "2026-02-14T18:00:00Z",
      "actualEndTime": "2026-02-14T18:49:00Z",
      "overrunMinutes": 4,
      "completedBy": "marco.organizer",
      "speakers": [{ "username": "anna.meier", "firstName": "Anna", "lastName": "Meier" }]
    }
  ],
  "connectedOrganizers": [
    { "username": "marco.organizer", "firstName": "Marco", "connected": true }
  ],
  "serverTimestamp": "2026-02-14T18:49:01Z"
}
```

The `trigger` field indicates what action caused the state update, corresponding to PRD message types: `SESSION_STARTED`, `SESSION_EXTENDED`, `SESSION_ENDED`, `SESSION_SKIPPED`, `SCHEDULE_CASCADED`, `HEARTBEAT`.

**Speaker Arrival (Server → all Watch subscribers):**
```json
{
  "type": "SPEAKER_ARRIVED",
  "eventCode": "BATbern56",
  "speakerUsername": "anna.meier",
  "speakerFirstName": "Anna",
  "speakerLastName": "Meier",
  "confirmedBy": "sarah.organizer",
  "arrivedAt": "2026-02-14T17:35:00Z",
  "arrivalCount": { "arrived": 3, "total": 5 }
}
```

**Action (Watch → Server):**
```json
{
  "action": "SESSION_ENDED",
  "sessionSlug": "cloud-native-pitfalls",
  "organizerUsername": "marco.organizer",
  "overrunMinutes": 4,
  "cascadeMinutes": 5,
  "clientTimestamp": "2026-02-14T18:49:00Z"
}
```

**Action types:** `SESSION_STARTED`, `SESSION_ENDED`, `SESSION_EXTENDED`, `SESSION_SKIPPED`, `SCHEDULE_CASCADE`, `SPEAKER_ARRIVED`

**Error Handling:** ACTION_ACK with `success: false` + error reason. Watch displays error toast and retains action in queue for retry.

### Frontend Architecture (watchOS)

**Architecture Pattern:** MVVM + Repository with dual-zone navigation

#### Dual-Zone Navigation

The app uses **horizontal paging** (SwiftUI `TabView` with `.page` style) as the top-level navigation:

```
TabView (horizontal paging)
├── Tab 0: Public Zone (Left — default on launch)
│   └── NavigationStack
│       ├── P1: EventHeroView (root)
│       ├── P2: SessionCardView (Crown scroll — vertical paging)
│       ├── P3: AbstractDetailView (push from P2 title tap)
│       ├── P4: SpeakerBioView (push from P2 single speaker tap)
│       ├── P5: MultiSpeakerGridView (push from P2 multi-speaker tap)
│       └── P6: IndividualSpeakerBioView (push from P5 portrait tap)
│
└── Tab 1: Organizer Zone (Right — swipe right)
    └── NavigationStack
        ├── O1: PairingView (if not paired)
        ├── O2: SpeakerArrivalView (if paired, <1h before event)
        ├── O3: LiveCountdownView (if paired, event active)
        ├── O4: CascadePromptView (sheet from O3 on overrun Done tap)
        ├── O5: BreakGongView (auto-transition during break)
        ├── O6: TransitionView (auto-transition between sessions)
        └── O7: SessionTimelineView (Crown scroll in organizer zone)
```

**Organizer Zone Entry Logic:**
```swift
// O1/O2/O3 selection based on state
var organizerEntryView: some View {
    if !authManager.isPaired {
        PairingView()                    // O1
    } else if eventState.isPreEvent {
        SpeakerArrivalView()             // O2 (<1h before event)
    } else if eventState.isLive {
        LiveCountdownView()              // O3
    } else {
        EventPreviewView()               // No active event
    }
}
```

#### Complete Layer Architecture

```
Presentation Layer (13 screens + 3 complications)
├── Public Zone
│   ├── EventHeroView.swift              (P1: Theme image, title, date/venue)
│   ├── SessionCardView.swift            (P2: Time, title, speakers — Crown scrollable)
│   ├── AbstractDetailView.swift         (P3: Full session abstract)
│   ├── SpeakerBioView.swift             (P4: Portrait, name, company, bio)
│   ├── MultiSpeakerGridView.swift       (P5: Portrait grid for multi-speaker sessions)
│   └── IndividualSpeakerBioView.swift   (P6: Same as P4, pushed from P5)
├── Organizer Zone
│   ├── PairingView.swift                (O1: 6-digit code entry via Crown)
│   ├── SpeakerArrivalView.swift         (O2: Portrait grid with arrival tracking)
│   ├── LiveCountdownView.swift          (O3: Progress ring + countdown + speaker card)
│   ├── CascadePromptView.swift          (O4: Shift options modal)
│   ├── BreakGongView.swift              (O5: Break countdown + gong timer)
│   ├── TransitionView.swift             (O6: Next speaker portrait + intro info)
│   └── SessionTimelineView.swift        (O7: All sessions with status)
├── Complications
│   ├── CircularComplication.swift        (C1: Progress ring + countdown minutes)
│   ├── RectangularComplication.swift     (C2: Speaker name + countdown + progress bar)
│   └── CornerComplication.swift          (C3: Countdown digits only)
└── Root
    ├── ContentView.swift                (TabView horizontal paging — zone container)
    └── BATbernWatchApp.swift            (App entry point)

Domain Layer
├── EventStateMachine.swift              (scheduled → active → completed/skipped)
├── SessionTimerEngine.swift             (Wall-clock countdown, 1s ticks)
├── HapticScheduler.swift                (Threshold-based alert scheduling)
└── ArrivalTracker.swift                 (Speaker arrival state management)

Data Layer
├── WebSocketClient.swift                (STOMP over WebSocket)
├── PublicEventService.swift             (REST client for public API endpoints)
├── LocalCache.swift                     (SwiftData persistence)
├── ActionQueue.swift                    (Offline action buffer, persisted to disk)
└── AuthManager.swift                    (Pairing token in Keychain + JWT management)
```

#### State Management

Two view models for the two zones:

**PublicViewModel** (`@Observable`):
- `event: CachedEvent?` — current event data
- `sessions: [CachedSession]` — ordered session list for Crown scroll
- `isOffline: Bool` — connectivity indicator
- `lastSynced: Date?` — "Last updated" timestamp

**OrganizerViewModel** (`@Observable`):
- `currentSession: CachedSession?` — active session
- `timerState: TimerState` — countdown / overrun state
- `connectionState: ConnectionState` — WebSocket status
- `connectedOrganizers: [OrganizerPresence]` — who's online
- `allSessions: [CachedSession]` — full schedule with live state
- `speakerArrivals: [SpeakerArrival]` — arrival tracking state
- `isPaired: Bool` — pairing status

Both view models read from the shared `LocalCache` (SwiftData) and are updated via `WebSocketClient` (organizer) or `PublicEventService` (public).

**Timer Design Decision:** Calculate remaining time from wall clock vs `scheduledEndTime` each tick (not a decrementing counter). This prevents drift across watchOS app suspensions.

#### Internationalization & Localization

**Primary Locale:** Swiss German (`de_CH`)

**Decision:** All user-facing text uses German with Swiss German date/time formatting. While the app targets Swiss German speakers (Bern conference), the localization infrastructure supports future expansion to additional languages.

**Implementation:**

1. **String Localization** (`BATbern-watch Watch App/de.lproj/Localizable.strings`):
   ```swift
   // Event Hero Screen
   "event.hero.scroll_hint" = "▼ Für Programm scrollen";
   "event.hero.empty.title" = "BATbern";
   "event.hero.empty.message" = "Kein anstehendes BATbern Event";

   // Error Messages
   "error.offline" = "Offline — gecachte Daten werden angezeigt";
   "error.refresh_failed" = "Aktualisierung fehlgeschlagen: %@";
   ```

2. **Swiss Date/Time Formatting** (`Utilities/SwissDateFormatter.swift`):
   ```swift
   enum SwissDateFormatter {
       static let swissLocale = Locale(identifier: "de_CH")

       // Event dates: "15. Februar 2026"
       static let eventDateFormatter: DateFormatter = {
           let formatter = DateFormatter()
           formatter.locale = swissLocale
           formatter.dateStyle = .long
           formatter.timeStyle = .none
           return formatter
       }()

       // Event times: "14:00" (24-hour format)
       static let eventTimeFormatter: DateFormatter = {
           let formatter = DateFormatter()
           formatter.locale = swissLocale
           formatter.dateFormat = "HH:mm"
           return formatter
       }()
   }
   ```

3. **Usage in Views:**
   ```swift
   // Localized strings
   Text(NSLocalizedString("event.hero.scroll_hint", comment: "Scroll hint"))

   // Swiss date formatting
   Text(SwissDateFormatter.formatEventDate(event.eventDate))  // "15. Februar 2026"
   Text(SwissDateFormatter.formatTimeString(event.typicalStartTime))  // "14:00"
   ```

**Swiss German Conventions:**
- Dates: Long format with full month name (e.g., "15. Februar 2026")
- Times: 24-hour format without AM/PM (e.g., "14:00", not "2:00 PM")
- Decimal separator: `.` (period, not comma as in Germany)
- Date separator: `.` (period: `15.02.2026`)

**Hero Page Visual Design:**
- Logo size: 50pt (larger than elsewhere for visibility)
- Logo includes both BATbern symbol arrows and text
- All UI text in German
- Bottom info bar uses Swiss date/time formatting

**Future Extensions:**
- Additional language support: Add `en.lproj/Localizable.strings`, `fr.lproj/Localizable.strings`
- User language selection: Detect from device settings or allow manual override
- Date/time formatting: Extend `SwissDateFormatter` with locale-aware formatters for EN/FR

### Infrastructure & Deployment

**Hosting:** Existing AWS ECS Fargate (ARM64) — no new services

**ALB Changes (for WebSocket support):**
```typescript
targetGroup.setAttribute('stickiness.enabled', 'true');
targetGroup.setAttribute('stickiness.lb_cookie.duration_seconds', '86400');
loadBalancer.setAttribute('idle_timeout.timeout_seconds', '3600');
targetGroup.setAttribute('deregistration_delay.timeout_seconds', '30');
```

**CI/CD:**
- Backend: Existing GitHub Actions pipeline
- watchOS: Separate GitHub Actions workflow → TestFlight via Xcode Cloud or Fastlane
- Epic 1 (public zone only) → submit to TestFlight immediately as standalone shippable product

**Monitoring:** Existing CloudWatch logs at `/aws/ecs/BATbern-{env}/event-management` and `/aws/ecs/BATbern-{env}/company-user-management`

**Scaling:** Not applicable for organizer zone — fixed 4 users, in-memory broker sufficient. Public zone uses existing REST endpoints already scaled for web frontend load.

### Decision Impact Analysis

**Epic-Aligned Implementation Sequence:**

| Phase | Epic | Scope | Backend Work |
|---|---|---|---|
| 1 | **Epic 1: Public Event Companion** | P1-P6 screens, SwiftData cache, offline, progressive publishing | None — uses existing public endpoints |
| 2 | **Epic 2: Watch Pairing & Organizer Access** | O1-O2 screens, pairing flow, speaker arrival tracking | Pairing endpoints (CUMS), arrival tracking (EMS), web frontend pairing UI |
| 3 | **Epic 3: Live Countdown & Haptic Awareness** | O3 screen, complications C1-C3, haptic patterns, always-on display | Session timing state endpoint (minor) |
| 4 | **Epic 4: Session Control & Team Sync** | O3-O7 screens, WebSocket sync, cascade, presence | WebSocket endpoint (EMS), session state machine, conflict resolution |
| 5 | **Epic 5: Offline Resilience** | ActionQueue, connectivity monitoring, sync recovery, offline haptics | Offline queue replay endpoint |

This sequence enables **incremental delivery**: Epic 1 alone is a shippable App Store product. Each subsequent epic adds organizer capabilities.

**Cross-Component Dependencies:**
- PublicViewModel depends on LocalCache + PublicEventService (Data → Presentation)
- OrganizerViewModel depends on LocalCache + WebSocketClient + AuthManager (Data → Presentation)
- Timer engine depends on cached session data (Data → Domain)
- Haptic scheduler depends on timer state transitions (Domain → Domain)
- Complications depend on timer state (Domain → Presentation)
- WebSocket client updates local cache (Data → Data)
- ActionQueue depends on connection state (Data → Data)
- SpeakerArrivalView depends on WebSocket arrivals topic (Data → Presentation)

---

## Implementation Patterns & Consistency Rules

### Naming Patterns

**Database Naming (PostgreSQL — existing conventions):**
- Tables: `snake_case` plural (`sessions`, `watch_pairings`, `speaker_arrivals`)
- Columns: `snake_case` (`actual_start_time`, `pairing_code_expires_at`)
- Indexes: `idx_{table}_{column}` (`idx_speaker_arrivals_event`)
- Migrations: `V{number}__{description}.sql`

**API Naming (REST — ADR-003 compliance):**
- Endpoints: `/api/v1/watch/...` and `/api/v1/users/{username}/watch-pairing`
- Path params: `camelCase` meaningful IDs (`eventCode`, `sessionSlug`, `username`)
- Never expose UUIDs in URLs

**STOMP Topic Naming:**
- Topics: `/topic/events/{eventCode}/state`, `/topic/events/{eventCode}/arrivals`
- User queues: `/user/queue/watch/{purpose}`
- App destinations: `/app/watch/events/{eventCode}/{action}`

**Swift Code Naming:**
- Types: `PascalCase` (`CachedSession`, `OrganizerViewModel`, `SessionTimerEngine`)
- Properties: `camelCase` (`scheduledEndTime`, `connectedOrganizers`)
- Enums: `PascalCase` type, `camelCase` cases (`SessionState.active`)
- Files: Match primary type name (`LiveCountdownView.swift`, `OrganizerViewModel.swift`)
- Views: Named by screen ID from UX spec (`EventHeroView` = P1, `PairingView` = O1)

**Java Code Naming (existing conventions):**
- Packages: `ch.batbern.events.watch`, `ch.batbern.companyuser.watch`
- Classes: `PascalCase` (`WatchWebSocketController`, `WatchPairingService`)
- Methods: `camelCase` (`handleSessionEnded`, `broadcastStateUpdate`)

### Structure Patterns

**watchOS Project Organization:**
```
BATbern-watch Watch App/
├── App/                    # App entry point + ContentView (zone container)
├── Views/
│   ├── Public/             # P1-P6 screens
│   ├── Organizer/          # O1-O7 screens
│   └── Complications/      # C1-C3 WidgetKit providers
├── ViewModels/             # PublicViewModel, OrganizerViewModel
├── Domain/                 # Business logic (state machine, timer, haptics, arrival tracker)
├── Data/                   # Network, persistence, auth
├── Models/                 # SwiftData models, DTOs, enums
└── Resources/              # Assets, localization string catalogs
```

**Backend Extension Organization (event-management-service):**
```
ch.batbern.events.watch/
├── WatchWebSocketController.java       # STOMP message handlers
├── WatchConnectionManager.java         # Presence tracking (in-memory)
├── WatchEventStateService.java         # State snapshot builder
├── WatchSessionActionService.java      # Action processor (session lifecycle)
├── WatchSpeakerArrivalService.java     # Speaker arrival tracking
├── WatchRestController.java            # REST fallback + arrivals
├── JwtStompInterceptor.java            # WebSocket auth
└── dto/
    ├── WatchStateUpdate.java
    ├── WatchAction.java
    ├── WatchActionAck.java
    └── SpeakerArrivalUpdate.java
```

**Backend Extension Organization (company-user-management-service):**
```
ch.batbern.companyuser.watch/
├── WatchPairingController.java         # Pairing REST endpoints
├── WatchPairingService.java            # Pairing business logic
├── WatchAuthController.java            # Code exchange + JWT issuance
└── dto/
    ├── PairingCodeResponse.java
    ├── PairingRequest.java
    └── WatchAuthResponse.java

domain/
└── WatchPairing.java                   # JPA entity
```

**Test Organization:**
- Swift: `BATbern-watch Watch AppTests/` mirroring source structure
- Java (EMS): `src/test/java/ch/batbern/events/watch/` (Testcontainers PostgreSQL)
- Java (CUMS): `src/test/java/ch/batbern/companyuser/watch/` (Testcontainers PostgreSQL)

### Format Patterns

**Date Format:** ISO 8601 UTC (`2026-02-14T18:00:00Z`) — all timestamps in UTC, client converts to local

**Session Status Codes:** `SCHEDULED`, `ACTIVE`, `COMPLETED`, `SKIPPED` (uppercase, matching backend enum)

**Action Names:** `SESSION_STARTED`, `SESSION_ENDED`, `SESSION_EXTENDED`, `SESSION_SKIPPED`, `SCHEDULE_CASCADE`, `SPEAKER_ARRIVED` (uppercase snake_case)

**Session Types:** `keynote`, `presentation`, `workshop`, `panel_discussion`, `networking`, `break`, `lunch` (lowercase, matching existing API)

### Communication Patterns

**WebSocket State Updates:**
- Server broadcasts full state on every change (no deltas)
- Watch replaces local organizer state entirely on each update
- `trigger` field in state update identifies what changed (maps to PRD message types)
- This simplifies reconciliation at the cost of slightly larger payloads (~3KB per update — negligible for 4 users)

**Speaker Arrival Updates:**
- Separate topic (`/topic/events/{eventCode}/arrivals`) for lightweight arrival broadcasts
- Watch merges arrival data into local speaker cache
- Arrival count included in each message for display without local computation

**Action-ACK Pattern:**
- Watch sends action → waits for ACK (3s timeout)
- If ACK received with `success: true`: local state already updated via broadcast
- If ACK with `success: false`: display error toast, retain in queue
- If timeout: queue action, switch to offline mode, retry on reconnect

**Idempotency:**
- `SESSION_ENDED` is idempotent (ending an already-ended session is a no-op)
- `SCHEDULE_CASCADE` checks if cascade already applied (based on server state)
- `SPEAKER_ARRIVED` is idempotent (confirming an already-arrived speaker is a no-op)

### Process Patterns

**Error Handling:**
- Network errors → silent fallback to offline mode (no user alert unless persistent)
- Action errors → toast notification + retain in queue
- Auth errors → attempt JWT refresh via pairing token; if that fails, show pairing screen
- Never crash — wrap all WebSocket handlers in do/catch

**Loading States:**
- Initial sync: Full-screen spinner with "Connecting to event..."
- Reconnecting: Status bar shows "Reconnecting..." with attempt count
- Public zone: Show cached data immediately, refresh in background
- Normal operation: No loading indicators (seamless)

### Enforcement Guidelines

**All AI Agents MUST:**
1. Use ADR-003 meaningful identifiers (never UUIDs in URLs or STOMP topics)
2. Follow existing `WebSocketConfig.java` patterns when extending
3. Use Testcontainers PostgreSQL for all integration tests (never H2)
4. Use Flyway for all schema changes (never manual SQL)
5. Follow MVVM pattern on watchOS (Views → ViewModel → Domain → Data)
6. Calculate timer from wall clock, never use decrementing counter
7. Queue offline actions to disk, replay on reconnect
8. Validate JWT on STOMP CONNECT (never allow unauthenticated WebSocket)
9. Implement dual-zone navigation as horizontal TabView paging (public left, organizer right)
10. Use pairing code flow for Watch auth (never direct Cognito password entry on Watch)
11. Respect progressive publishing phases in public zone (TOPIC / SPEAKERS / AGENDA)
12. Place pairing endpoints in company-user-management-service, session/WebSocket in event-management-service

---

## Project Structure & Boundaries

### Complete Project Directory Structure

**New watchOS Project (in monorepo `apps/` directory):**
```
apps/BATbern-watch/
├── BATbern-watch.xcodeproj/
├── BATbern-watch Watch App/
│   ├── BATbernWatchApp.swift
│   ├── ContentView.swift                    # TabView horizontal paging (zone container)
│   ├── Views/
│   │   ├── Public/
│   │   │   ├── EventHeroView.swift          # P1: Theme image, title, date/venue
│   │   │   ├── SessionCardView.swift        # P2: Time slot, title, speakers
│   │   │   ├── AbstractDetailView.swift     # P3: Session abstract (Crown scrollable)
│   │   │   ├── SpeakerBioView.swift         # P4/P6: Portrait, name, company, bio
│   │   │   └── MultiSpeakerGridView.swift   # P5: Portrait grid (tappable → P6)
│   │   ├── Organizer/
│   │   │   ├── PairingView.swift            # O1: 6-digit code entry
│   │   │   ├── SpeakerArrivalView.swift     # O2: Portrait grid + arrival tracking
│   │   │   ├── LiveCountdownView.swift      # O3: Progress ring + countdown + speaker
│   │   │   ├── CascadePromptView.swift      # O4: Shift schedule options
│   │   │   ├── BreakGongView.swift          # O5: Break countdown + gong timer
│   │   │   ├── TransitionView.swift         # O6: Next speaker intro
│   │   │   └── SessionTimelineView.swift    # O7: Full session list with status
│   │   └── Shared/
│   │       ├── SpeakerPortraitView.swift    # Reusable portrait thumbnail
│   │       └── ConnectionStatusBar.swift    # Connectivity indicator
│   ├── ViewModels/
│   │   ├── PublicViewModel.swift            # Public zone state
│   │   └── OrganizerViewModel.swift         # Organizer zone state
│   ├── Domain/
│   │   ├── EventStateMachine.swift          # Session lifecycle
│   │   ├── SessionTimerEngine.swift         # Wall-clock countdown
│   │   ├── HapticScheduler.swift            # Alert scheduling
│   │   └── ArrivalTracker.swift             # Speaker arrival logic
│   ├── Data/
│   │   ├── WebSocketClient.swift            # STOMP client
│   │   ├── PublicEventService.swift         # REST client for public endpoints
│   │   ├── LocalCache.swift                 # SwiftData models + queries
│   │   ├── ActionQueue.swift                # Offline action buffer
│   │   ├── AuthManager.swift                # Pairing token + JWT management
│   │   └── PortraitCache.swift              # Image file cache
│   ├── Models/
│   │   ├── CachedEvent.swift                # SwiftData event model
│   │   ├── CachedSession.swift              # SwiftData session model
│   │   ├── CachedSpeaker.swift              # SwiftData speaker model
│   │   ├── PairingInfo.swift                # SwiftData pairing model
│   │   ├── OrganizerPresence.swift          # Presence data type
│   │   └── DTOs.swift                       # Network message types
│   ├── Complications/
│   │   ├── CircularComplication.swift        # C1: Progress ring + minutes
│   │   ├── RectangularComplication.swift     # C2: Speaker + countdown + bar
│   │   └── CornerComplication.swift          # C3: Digits only
│   └── Resources/
│       ├── Assets.xcassets/
│       └── Localizable.xcstrings            # DE, EN, FR
├── BATbern-watch Watch AppTests/
│   ├── Domain/
│   │   ├── SessionTimerEngineTests.swift
│   │   ├── EventStateMachineTests.swift
│   │   ├── HapticSchedulerTests.swift
│   │   └── ArrivalTrackerTests.swift
│   ├── Data/
│   │   ├── ActionQueueTests.swift
│   │   ├── LocalCacheTests.swift
│   │   └── AuthManagerTests.swift
│   └── ViewModels/
│       ├── PublicViewModelTests.swift
│       └── OrganizerViewModelTests.swift
├── BATbern-watch Watch AppUITests/
│   └── NavigationUITests.swift
├── CLAUDE.md                                # Watch app development guide
└── README.md
```

**Backend Extensions (existing monorepo):**
```
services/event-management-service/
├── src/main/java/ch/batbern/events/
│   ├── config/
│   │   └── WebSocketConfig.java                    # MODIFIED: Add JWT interceptor
│   ├── watch/                                      # NEW package
│   │   ├── WatchWebSocketController.java
│   │   ├── WatchConnectionManager.java
│   │   ├── WatchEventStateService.java
│   │   ├── WatchSessionActionService.java
│   │   ├── WatchSpeakerArrivalService.java         # NEW: arrival tracking
│   │   ├── WatchRestController.java
│   │   ├── JwtStompInterceptor.java
│   │   └── dto/
│   │       ├── WatchStateUpdate.java
│   │       ├── WatchAction.java
│   │       ├── WatchActionAck.java
│   │       └── SpeakerArrivalUpdate.java           # NEW
│   └── domain/
│       ├── Session.java                            # MODIFIED: Add 4 watch fields
│       └── SpeakerArrival.java                     # NEW: arrival entity
├── src/main/resources/db/migration/
│   ├── V{next}__add_watch_session_fields.sql       # NEW
│   └── V{next+1}__add_speaker_arrival_tracking.sql # NEW
└── src/test/java/ch/batbern/events/watch/
    ├── WatchWebSocketIntegrationTest.java          # NEW
    └── WatchSpeakerArrivalIntegrationTest.java     # NEW

services/company-user-management-service/
├── src/main/java/ch/batbern/companyuser/
│   └── watch/                                      # NEW package
│       ├── WatchPairingController.java
│       ├── WatchPairingService.java
│       ├── WatchAuthController.java
│       └── dto/
│           ├── PairingCodeResponse.java
│           ├── PairingRequest.java
│           └── WatchAuthResponse.java
├── src/main/java/ch/batbern/companyuser/domain/
│   └── WatchPairing.java                           # NEW: JPA entity
├── src/main/resources/db/migration/
│   └── V{next}__add_watch_pairing.sql              # NEW
└── src/test/java/ch/batbern/companyuser/watch/
    └── WatchPairingIntegrationTest.java            # NEW

web-frontend/
└── src/
    └── features/
        └── profile/
            └── WatchPairingSection.tsx              # NEW: "Pair Apple Watch" UI

infrastructure/
└── lib/constructs/ecs-service.ts                   # MODIFIED: ALB timeout + stickiness
```

**Documentation (already established):**
```
docs/watch-app/
├── prd-batbern-watch.md                # PRD v2.0 (authoritative)
├── architecture.md                     # This document
├── ux-design-specification.md          # UX Spec v2.0
├── ux-design-directions.html           # Visual mockups
├── product-brief.md                    # Initial product brief
├── brainstorming-session.md            # Feature discovery session
├── epics.md                            # Epic breakdown
└── stories/                            # Watch stories (W-prefixed)
    ├── W1.1-xcode-project-setup.md
    ├── W1.2-event-hero-screen.md
    └── ...
```

### Architectural Boundaries

**API Boundaries:**
- Watch (public zone) ↔ Backend: HTTPS (REST) through API Gateway — existing public endpoints
- Watch (organizer zone) ↔ Backend: WSS (STOMP) + HTTPS (REST) through ALB
- Watch ↔ company-user-management: HTTPS (pairing endpoints) through API Gateway
- WebSocket connects direct to event-management-service on port 8002 (existing pattern)
- REST endpoints go through API Gateway for auth middleware

**Component Boundaries (watchOS):**
- Views never access Data layer directly — always through ViewModels
- Domain layer is pure logic — no UI or network dependencies
- Data layer handles all I/O (network, disk, Keychain)
- Models are shared across all layers (value types, Codable)
- Public and Organizer ViewModels share the same LocalCache but have separate network clients

**Service Boundaries (Backend):**
- `event-management-service/watch/` — session state, WebSocket, arrival tracking (owns session and event data)
- `company-user-management-service/watch/` — pairing code management (owns user identity data)
- No new cross-service calls from Watch handlers — pairing and session services operate independently
- Presence tracking is in-memory only (no database persistence needed for 4 users)
- Speaker arrival tracking IS persistent (database — survives service restarts during pre-event phase)

**Data Boundaries:**
- Watch local cache is a read-through cache of server state (organizer zone)
- Watch local cache is a periodic refresh cache of public API data (public zone)
- Server state is always authoritative
- Public zone data is strictly read-only — no state mutations from public users

### Integration Points

**Internal Communication:**
- WebSocket (STOMP): Primary real-time channel between Watch and EMS (organizer zone)
- REST (public): Watch → API Gateway → existing public endpoints (public zone)
- REST (pairing): Watch → API Gateway → CUMS (pairing flow)
- REST (fallback): Watch → API Gateway → EMS (offline replay, polling)
- SwiftData ↔ Domain: Local cache feeds timer engine and views

**External Integrations:**
- S3/CloudFront: Speaker portrait images, company logos, event theme images
- No direct Cognito interaction from Watch (pairing code abstracts this)
- No other external services for MVP

**Data Flow — Public Zone:**
```
App Launch → SwiftData Cache → PublicViewModel → Views (immediate)
             PublicEventService → REST GET /events/current → SwiftData Cache → PublicViewModel → Views (refresh)
```

**Data Flow — Organizer Zone:**
```
Pairing: Watch → POST /watch/pair → PairingToken → Keychain
Auth:    PairingToken → POST /watch/authenticate → JWT → Memory
Connect: JWT → STOMP CONNECT → EMS WebSocket → Subscribe /topic/events/{code}/state
Sync:    EMS → STATE_UPDATE → WebSocketClient → LocalCache → OrganizerViewModel → Views
Action:  Watch → STOMP SEND /action → EMS → Process → Broadcast STATE_UPDATE → All Watches
```

---

## Architecture Validation Results

### Coherence Validation

- **Decision Compatibility:** All decisions work together. Pairing code flow avoids password entry on Watch while still producing JWTs for STOMP auth. STOMP/WebSocket + JWT auth + server-authoritative state form a coherent real-time sync model. SwiftData + wall-clock timer + offline queue form a coherent offline resilience model. Dual-zone navigation cleanly separates public (read-only, no auth) from organizer (read-write, paired auth).
- **Pattern Consistency:** Naming patterns align with existing BATbern conventions (ADR-003, snake_case SQL, camelCase Java/Swift). STOMP topics follow consistent `/topic/events/{eventCode}/...` pattern. View names match UX spec screen IDs (P1-P6, O1-O7, C1-C3).
- **Structure Alignment:** watchOS MVVM layers cleanly separate concerns. Backend changes distributed correctly: identity/pairing in CUMS, session state in EMS.

### Requirements Coverage Validation

| Requirement Group | Coverage | Implementation |
|---|---|---|
| FR1-5 (Schedule display) | Covered | LiveCountdownView (O3), SessionTimelineView (O7), Complications (C1-C3) |
| FR6-10 (Session lifecycle) | Covered | EventStateMachine, WatchSessionActionService |
| FR11-16 (Time alerting) | Covered | HapticScheduler, SessionTimerEngine |
| FR17-20 (Team sync) | Covered | WebSocket broadcast, WatchConnectionManager |
| FR21-24 (Setup & connection) | Covered | AuthManager, PairingView (O1), pairing endpoints |
| FR25-28 (Offline resilience) | Covered | LocalCache, ActionQueue, ConnectionState |
| FR29-35 (Public zone) | Covered | EventHeroView (P1), SessionCardView (P2), AbstractDetailView (P3), SpeakerBioView (P4), MultiSpeakerGridView (P5), PublicEventService |
| FR36-39 (Speaker arrival) | Covered | SpeakerArrivalView (O2), WatchSpeakerArrivalService, SPEAKER_ARRIVED message |
| NFR1-6: Performance | Covered | Wall-clock timer, native SwiftUI, SwiftData cache |
| NFR7: Public zone speed | Covered | Cache-first loading, async refresh |
| NFR8-13: Reliability | Covered | Extended Runtime, offline mode, error boundaries |
| NFR14-20: Security | Covered | Pairing code flow, Keychain, JWT, TLS, max 2 watches |
| NFR21-24: Battery | Covered | Single WebSocket, adaptive polling, file cache |
| NFR25-31: Compatibility | Covered | watchOS 11+, VoiceOver, Dynamic Type, High Contrast |
| NFR32-33: Localization | Covered | String catalogs (DE, EN, FR) |

### Implementation Readiness Validation

- **Decision Completeness:** All critical and important decisions documented with specific technologies and versions
- **Structure Completeness:** Full directory tree for watchOS project (13 screens, 3 complications) and backend extensions (2 services)
- **Pattern Completeness:** Naming, structure, format, communication, and process patterns defined
- **Epic Alignment:** Implementation sequence follows PRD epic structure for incremental delivery

### Gap Analysis Results

| Priority | Gap | Mitigation |
|---|---|---|
| Nice-to-have | APNs backup channel | Deferred to Phase 2; WebSocket sufficient for venue WiFi |
| Nice-to-have | Quick ping between organizers | Phase 2 feature — new action type `PING` |
| Nice-to-have | Speaker time signal / flash | Phase 2 feature — separate speaker-facing channel |
| Low | Multi-instance ECS WebSocket routing | Stickiness handles this; only 4 connections |

### Architecture Completeness Checklist

- [x] Requirements Analysis — all 39 FRs and 33 NFRs mapped
- [x] Architectural Decisions — 11 critical/important decisions documented
- [x] Dual-Zone Architecture — public (left) + organizer (right) with state-dependent entry
- [x] Authentication — pairing code flow, no Watch passwords, JWT for API/STOMP
- [x] Implementation Patterns — naming, structure, format, communication, process
- [x] Project Structure — full directory tree with file purposes (13 screens, 3 complications)
- [x] Integration Points — WebSocket, REST (public + pairing + fallback), S3/CloudFront
- [x] Data Architecture — 3 schema migrations (2 services), SwiftData models, portrait cache
- [x] Security — Pairing code auth, JWT, STOMP interceptor, TLS, role-based access
- [x] Backend Service Boundaries — pairing in CUMS, session/WebSocket/arrival in EMS
- [x] Epic-Aligned Sequence — 5 epics with incremental delivery (Epic 1 alone is shippable)
- [x] Risk Register — 8 risks with mitigations

### Architecture Readiness Assessment

- **Overall Status:** READY FOR IMPLEMENTATION
- **Confidence Level:** High
- **Key Strengths:**
  - Dual-zone architecture enables App Store distribution (200+ attendees) while protecting organizer features
  - Pairing code flow eliminates password entry on Watch — seamless one-time setup
  - Extends existing proven infrastructure (no greenfield backend)
  - Epic 1 (public zone) requires zero backend work — fastest path to TestFlight
  - WebSocket/STOMP already configured — just needs auth + Watch handlers
  - Offline resilience is first-class (not bolted on)
  - Timer design prevents drift across watchOS app suspensions
  - Idempotent actions eliminate multi-user conflict complexity
  - Speaker arrival tracking integrated into MVP — completes pre-event workflow
- **Areas for Future Enhancement:**
  - APNs backup channel for reliability beyond venue WiFi
  - Analytics from actual session timing data
  - Speaker-facing features (time signals)

---

## Architecture Completion Summary

### Workflow Completion

- **Status:** COMPLETED (v2.0 update)
- **Total Steps Completed:** 8 (initial) + alignment review
- **Date Completed:** 2026-02-15
- **Document Location:** `docs/watch-app/architecture.md`
- **Change Summary (v2.0):** Aligned with PRD v2.0 and UX Spec v2.0 — added public zone, pairing code auth, speaker arrival tracking, dual-zone navigation, complete screen catalog, correct service boundaries

### Final Architecture Deliverables

- **Complete Architecture Document:** All decisions, patterns, structure, validation
- **Implementation Ready Foundation:**
  - 11 architectural decisions documented
  - 5 pattern categories defined
  - 30+ source files specified with purposes
  - Full directory trees for watchOS and backend (2 services)
  - 13 screens + 3 complications mapped to UX spec IDs
- **AI Agent Implementation Guide:**
  - Swift 6.0 / SwiftUI / watchOS 11+ / SwiftData (client)
  - Java 21 / Spring Boot 3.x / Spring WebSocket / PostgreSQL 15+ (backend)
  - 12 mandatory enforcement rules for AI agents
  - ADR-003 compliance required throughout

### Implementation Handoff

**For AI Agents:** This document contains all decisions needed to implement BATbern Watch. Follow the enforcement guidelines in "Implementation Patterns" exactly. When in doubt: the server is authoritative, timers use wall-clock calculation, all actions are idempotent, pairing code flow for auth (never Cognito direct), and public zone uses existing endpoints only.

**Epic Delivery Sequence:**
1. **Epic 1: Public Event Companion** — Xcode project, P1-P6 screens, SwiftData cache, offline support. Zero backend work. Submit to TestFlight.
2. **Epic 2: Watch Pairing & Organizer Access** — O1-O2 screens, pairing endpoints (CUMS), arrival tracking (EMS), web frontend pairing UI.
3. **Epic 3: Live Countdown & Haptic Awareness** — O3 screen, C1-C3 complications, haptic patterns, always-on display.
4. **Epic 4: Session Control & Team Sync** — O3-O7 screens, WebSocket endpoint (EMS), session state machine, cascade, presence.
5. **Epic 5: Offline Resilience** — ActionQueue, connectivity monitoring, sync recovery, offline haptics, queue replay endpoint.

### Risk Register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Venue WiFi unreliable | Medium | High | Offline mode with local countdown + action queue; mobile hotspot backup |
| WebSocket drops during event | Medium | Medium | Auto-reconnect with backoff; timer/haptics work offline |
| watchOS suspends app | Low | Medium | Extended Runtime session; WidgetKit timeline fallback |
| Battery drains before event ends | Low | High | Adaptive behavior; test 3-hour sessions in advance |
| Two organizers conflict on cascade | Low | Low | First-wins; second sees result immediately |
| ECS rolling deploy during event | Low | High | Schedule deploys outside event hours; ALB draining |
| Pairing token compromised | Low | Medium | Max 2 watches enforced; unpair from web; pairing tokens are per-device |
| Speaker arrival state lost on restart | Low | Medium | Persistent database storage; recovers on service restart |

### Phase 2 Extensions

| Feature | Architecture Impact |
|---|---|
| **APNs backup channel** | Push notifications for critical state changes as WebSocket reliability backup |
| **Quick ping between organizers** | New action type `PING` with haptic on receiving Watch |
| **Speaker time signal** | Separate speaker-facing channel or companion app |
| **Multi-event support** | Watch switches between events; minor UI/cache changes |
| **Analytics dashboard** | Backend stores timing data (already captured in `actualStartTime`/`actualEndTime`) |
| **Attendee count pulse** | Live check-in count on wrist — requires registration service integration |

---

**Architecture Status: READY FOR IMPLEMENTATION**
