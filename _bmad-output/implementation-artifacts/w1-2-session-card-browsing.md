# Story 1.2: Session Card Browsing

Status: ready-for-dev

## Story

As an attendee,
I want to scroll through all sessions using the Digital Crown,
so that I can see the full event program on my wrist.

## Acceptance Criteria

1. **AC1 — Crown Scroll Navigation**: Given I'm on the event hero screen (P1), When I scroll down with the Digital Crown, Then I see session cards (P2) — one per page, ordered by start time — as vertical paging within the public zone NavigationStack.

2. **AC2 — Presentation Card Layout**: Given a session is a presentation/keynote/workshop/panel, When I view its card, Then I see time slot (top, secondary color), title (blue-tinted, tappable area for W1.3), and speaker area (bottom, with portrait thumbnails, speaker names, and company names).

3. **AC3 — Break Card Layout**: Given a session is a break/networking/lunch, When I view its card, Then I see time slot, an appropriate SF Symbol icon (e.g., `cup.and.saucer.fill` for break/lunch, `person.2.fill` for networking), and the session title — no speaker area displayed.

4. **AC4 — Scroll Performance**: Given I scroll between session cards, When transitions occur, Then each page transition completes in <100ms (NFR6), using native SwiftUI vertical paging for smooth Crown response.

5. **AC5 — Speaker Portrait Display**: Given a session has speakers, When I view the speaker area, Then I see circular portrait thumbnails (AsyncImage from CDN profilePictureUrl), speaker names, and company names. For 1 speaker: single centered portrait. For 2+ speakers: side-by-side grid layout.

6. **AC6 — Progressive Publishing Respect**: Given the event's `currentPublishedPhase` is `TOPIC`, When I browse sessions, Then I see titles only — no speakers, no time slots (just order). Given `SPEAKERS` phase, I see titles + speakers but no abstract access. Given `AGENDA` phase, I see full detail.

7. **AC7 — Placeholder Session Handling**: Given a session has null `sessionType` or null `startTime`/`endTime` (placeholder sessions from agenda planning), When I browse, Then these sessions are excluded from the card list.

## Tasks / Subtasks

- [ ] **Task 1: Create SessionCardView (P2)** (AC: #2, #3)
  - [ ] 1.1 Create `Views/Public/SessionCardView.swift`
  - [ ] 1.2 Layout for presentation/keynote/workshop/panel sessions:
    ```
    ┌──────────────────────┐
    │  18:00 – 18:45       │  ← Time slot (system gray)
    │  ─────────────────── │
    │                       │
    │  Cloud Native         │  ← Title (BATbern Blue tint, tappable)
    │  Security in 2026     │
    │                       │
    │  ─────────────────── │
    │  ┌────┐  ┌────┐      │  ← Speaker area (tappable)
    │  │ 📷 │  │ 📷 │      │     Circular portraits + names
    │  │Anna│  │Tom │      │
    │  │ACME│  │Corp│      │
    │  └────┘  └────┘      │
    └──────────────────────┘
    ```
  - [ ] 1.3 Layout for break/networking/lunch sessions:
    ```
    ┌──────────────────────┐
    │  19:00 – 19:20       │
    │                       │
    │         ☕            │  ← SF Symbol icon
    │    Coffee Break       │
    │                       │
    └──────────────────────┘
    ```
  - [ ] 1.4 Map sessionType to SF Symbol: `break`/`lunch` → `cup.and.saucer.fill`, `networking` → `person.2.fill`
  - [ ] 1.5 Separate title and speaker areas as distinct tappable regions (prepare for W1.3 navigation)

- [ ] **Task 2: Create SpeakerPortraitView (Shared Component)** (AC: #5)
  - [ ] 2.1 Create `Views/Shared/SpeakerPortraitView.swift` — reusable circular portrait thumbnail
  - [ ] 2.2 AsyncImage loading from CDN `profilePictureUrl` with placeholder (SF Symbol `person.crop.circle.fill`)
  - [ ] 2.3 Circular clip shape, Watch-optimized size (~40pt diameter for card, configurable)
  - [ ] 2.4 Speaker name (SF Pro ~11pt) below portrait
  - [ ] 2.5 Company name (SF Pro ~9pt, secondary color) below speaker name
  - [ ] 2.6 Handle missing portrait gracefully (show initials or SF Symbol placeholder)

- [ ] **Task 3: Build Vertical Paging Container** (AC: #1, #4)
  - [ ] 3.1 Create `Views/Public/SessionListView.swift` — vertical paging container
  - [ ] 3.2 Use SwiftUI `TabView` with `.tabViewStyle(.verticalPage)` for Crown-driven paging
  - [ ] 3.3 P1 (EventHeroView) as first page, then SessionCardView for each session
  - [ ] 3.4 Sessions ordered by `startTime` (ascending)
  - [ ] 3.5 Filter out placeholder sessions (null sessionType or null startTime/endTime)
  - [ ] 3.6 Ensure <100ms transitions (native SwiftUI paging handles this)

- [ ] **Task 4: Integrate with Public Zone Navigation** (AC: #1)
  - [ ] 4.1 Update `ContentView.swift` → Replace standalone EventHeroView with SessionListView as the public zone root
  - [ ] 4.2 Wrap SessionListView in NavigationStack (required for W1.3 push navigation)
  - [ ] 4.3 Ensure horizontal paging between zones still works (TabView nesting: horizontal zones → vertical sessions)

- [ ] **Task 5: Extend PublicViewModel for Session Data** (AC: #1, #6, #7)
  - [ ] 5.1 Add computed property `displayableSessions: [CachedSession]` — filters out placeholders, sorted by startTime
  - [ ] 5.2 Add computed property `hasSpeakerPhase: Bool` — true when phase is SPEAKERS or AGENDA
  - [ ] 5.3 Add computed property `hasAgendaPhase: Bool` — true when phase is AGENDA
  - [ ] 5.4 Add helper `isBreakSession(_ session: CachedSession) -> Bool` — checks sessionType

- [ ] **Task 6: Implement Progressive Publishing Filter** (AC: #6)
  - [ ] 6.1 In SessionCardView: conditionally hide speaker area when phase is `TOPIC`
  - [ ] 6.2 In SessionCardView: show time slots only in `SPEAKERS` and `AGENDA` phases (TOPIC shows order only)
  - [ ] 6.3 Title tap area is visually distinct in AGENDA phase (preparing for W1.3 abstract access)
  - [ ] 6.4 Speaker area tap is active only in SPEAKERS and AGENDA phases

- [ ] **Task 7: Portrait Image Caching** (AC: #5)
  - [ ] 7.1 Create `Data/PortraitCache.swift` — file-based image cache for speaker portraits
  - [ ] 7.2 Download portraits from CDN profilePictureUrl on event sync
  - [ ] 7.3 Store locally as files (~100KB each, max ~1MB per event)
  - [ ] 7.4 AsyncImage should first check local cache, then fall back to network

- [ ] **Task 8: Write Tests** (AC: all)
  - [ ] 8.1 `SessionCardViewTests.swift` — verify presentation vs. break card layouts render correctly
  - [ ] 8.2 `SpeakerPortraitViewTests.swift` — verify portrait rendering with/without image URL
  - [ ] 8.3 `PublicViewModelTests.swift` — extend: test displayableSessions filtering, progressive publishing logic, placeholder exclusion
  - [ ] 8.4 `PortraitCacheTests.swift` — test file-based caching, cache miss/hit behavior
  - [ ] 8.5 Verify all views in Xcode Previews with sample data

## Dev Notes

### Previous Story Context (W1.1)

W1.1 established the complete foundation this story builds upon:
- **Project structure**: `App/`, `Views/Public/`, `Views/Shared/`, `ViewModels/`, `Data/`, `Models/` directories
- **SwiftData models**: `CachedEvent`, `CachedSession`, `CachedSpeaker` with all fields — reuse directly
- **PublicViewModel**: `@Observable` class with `event`, `sessions`, `isLoading`, `isOffline` — extend with computed properties
- **PublicEventService**: REST client fetching from `GET /api/v1/events/current?include=topics,venue,sessions` — already parses sessions and speakers
- **OpenAPI Generated Types**: `EventDetail`, `Session`, `Speaker` types auto-generated from API spec (located in `Generated/Models/`)
- **Type Conversion Flow**: `EventDetail` (generated) → `WatchEvent` (domain) → `CachedEvent` (SwiftData)
- **LocalCache**: SwiftData persistence wrapper — already caches session data
- **ContentView**: Horizontal `TabView(.page)` with public zone (Tab 0) and organizer placeholder (Tab 1)
- **EventHeroView (P1)**: First screen in public zone — this story adds session cards below it
- **Localization**: German (de_CH) as primary language, `SwissDateFormatter` for all date/time formatting

### Critical Architecture Constraints

- **MVVM pattern**: SessionCardView reads from PublicViewModel only, never from Data layer directly
- **Vertical paging for Crown scroll**: Use `TabView(.verticalPage)` — this is the watchOS-native Crown scroll mechanism. Do NOT use ScrollView with Digital Crown binding (less smooth).
- **Nested TabViews**: The app has horizontal paging (zones) containing vertical paging (sessions). SwiftUI handles this natively — horizontal outer TabView wraps NavigationStack containing vertical inner TabView.
- **No navigation from this story**: Title and speaker taps are prepared (distinct tappable areas) but actual push navigation to P3/P4/P5 is implemented in W1.3. Keep tap targets defined but non-functional until W1.3.
- **Progressive publishing is critical**: The Watch MUST respect `currentPublishedPhase`. Showing speakers before the SPEAKERS phase or abstracts before AGENDA phase would violate event policy.

### Session Types and Icons

| sessionType | Card Type | SF Symbol | Notes |
|---|---|---|---|
| `keynote` | Presentation | n/a (speaker area) | Main talk |
| `presentation` | Presentation | n/a (speaker area) | Standard talk |
| `workshop` | Presentation | n/a (speaker area) | Hands-on session |
| `panel_discussion` | Presentation | n/a (speaker area) | Multi-speaker |
| `networking` | Break | `person.2.fill` | Social time |
| `break` | Break | `cup.and.saucer.fill` | Coffee/tea break |
| `lunch` | Break | `cup.and.saucer.fill` | Lunch break |
| `null` | EXCLUDED | — | Placeholder (filter out) |

### Speaker Area Layout Rules

**1 speaker:**
```
  ┌──────┐
  │  📷  │     Single portrait centered
  │ Anna │
  │ ACME │
  └──────┘
```

**2 speakers:**
```
  ┌────┐  ┌────┐
  │ 📷 │  │ 📷 │     Side by side
  │Anna│  │Tom │
  │ACME│  │Corp│
  └────┘  └────┘
```

**3+ speakers:**
```
  ┌────┐  ┌────┐
  │ 📷 │  │ 📷 │     2-column grid
  │Anna│  │Tom │
  └────┘  └────┘
  ┌────┐
  │ 📷 │              Shows count badge if >4: "+2 more"
  │Bob │
  └────┘
```

On the 44mm Watch (~168pt width), max 2 portraits per row. If >4 speakers, show first 3 + "+N more" badge. This keeps the card scannable at a glance.

### Portrait Image Approach

- CDN URLs from `profilePictureUrl` field serve full-size images
- Watch needs small portraits (~80x80pt max, @2x = 160x160px)
- Use `AsyncImage` with `.resizable()` and `.frame(width: 40, height: 40)` + `.clipShape(Circle())`
- Consider downloading on event sync for offline availability (PortraitCache)
- Total portrait storage: ~100KB x 8 speakers = <1MB per event (well within NFR24's 50MB limit)
- Placeholder: SF Symbol `person.crop.circle.fill` in secondary color

### Vertical Paging Architecture

```swift
// Inside public zone NavigationStack
TabView(selection: $selectedSessionIndex) {
    // Page 0: Event Hero
    EventHeroView()
        .tag(0)

    // Pages 1..N: Session Cards
    ForEach(Array(viewModel.displayableSessions.enumerated()), id: \.element.sessionSlug) { index, session in
        SessionCardView(session: session, phase: viewModel.event?.currentPublishedPhase)
            .tag(index + 1)
    }
}
.tabViewStyle(.verticalPage)
```

This gives smooth Crown-driven paging through EventHero → Session 1 → Session 2 → ... → Session N.

### Files Created/Modified in This Story

**New files:**
- `Views/Public/SessionCardView.swift` — P2 session card
- `Views/Public/SessionListView.swift` — Vertical paging container
- `Views/Shared/SpeakerPortraitView.swift` — Reusable portrait component
- `Data/PortraitCache.swift` — File-based portrait image cache

**Modified files:**
- `App/ContentView.swift` — Replace EventHeroView with SessionListView as public zone root
- `ViewModels/PublicViewModel.swift` — Add displayableSessions, phase helpers

### Localization & Internationalization

**All user-facing text must use German localization:**
- Use `NSLocalizedString()` for all UI text
- Reference strings from `Base.lproj/Localizable.strings` and `de.lproj/Localizable.strings`
- Examples:
  - Speaker count badge: `NSLocalizedString("session.speakers.more", comment: "+N more speakers")`
  - Placeholder text: `NSLocalizedString("session.no_speakers", comment: "No speakers")`

**Time Formatting — Swiss German (de_CH):**
- Use `SwissDateFormatter.formatTimeString()` for session times
- Format: `18:00 – 18:45` (24-hour, no AM/PM)
- Example usage:
  ```swift
  Text("\(SwissDateFormatter.formatTimeString(session.startTime)) – \(SwissDateFormatter.formatTimeString(session.endTime))")
  ```
- Handle nil startTime/endTime gracefully (filter out via `displayableSessions`, but defensive coding)

### Project Structure After This Story

```
BATbern-watch Watch App/
├── App/
│   ├── BATbernWatchApp.swift
│   └── ContentView.swift              ← MODIFIED: SessionListView replaces EventHeroView
├── Views/
│   ├── Public/
│   │   ├── EventHeroView.swift        ← From W1.1
│   │   ├── SessionCardView.swift      ← NEW: P2 session card
│   │   └── SessionListView.swift      ← NEW: Vertical paging container
│   ├── Organizer/                     ← Empty (Epic 2+)
│   └── Shared/
│       ├── BATbernSymbolView.swift    ← From W1.1
│       └── SpeakerPortraitView.swift  ← NEW: Reusable portrait
├── ViewModels/
│   └── PublicViewModel.swift          ← MODIFIED: Added computed properties
├── Domain/                            ← Empty (Epic 3+)
├── Data/
│   ├── PublicEventService.swift       ← From W1.1
│   ├── LocalCache.swift               ← From W1.1
│   └── PortraitCache.swift            ← NEW: File-based image cache
├── Models/                            ← All from W1.1 (unchanged)
├── Complications/                     ← Empty (Epic 3)
└── Resources/
```

### References

- [Source: docs/watch-app/architecture.md#Frontend-Architecture] — NavigationStack + TabView paging, MVVM layers
- [Source: docs/watch-app/architecture.md#Structure-Patterns] — SessionCardView.swift in Views/Public/, SpeakerPortraitView.swift in Views/Shared/
- [Source: docs/watch-app/architecture.md#Data-Architecture] — CachedSession fields: sessionSlug, title, sessionType, speakers[]
- [Source: docs/watch-app/ux-design-specification.md#Session-Card-Pages] — P2 layout, presentation vs. break cards
- [Source: docs/watch-app/ux-design-specification.md#Navigation-Rules] — Crown scroll from P1 to P2, <100ms transition
- [Source: docs/watch-app/ux-design-specification.md#Design-System-Foundation] — Typography (SF Pro 13pt titles, 16pt names), colors
- [Source: docs/watch-app/prd-batbern-watch.md#Public-Zone] — FR30 (Crown scroll sessions), FR33 (multi-speaker grid), FR34 (progressive publishing)
- [Source: docs/watch-app/epics.md#W1.2] — Story definition and acceptance criteria
- [Source: _bmad-output/implementation-artifacts/w1-1-xcode-project-setup-event-hero-screen.md] — Previous story: project structure, SwiftData models, PublicViewModel, API integration

## Dev Agent Record

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

### File List
