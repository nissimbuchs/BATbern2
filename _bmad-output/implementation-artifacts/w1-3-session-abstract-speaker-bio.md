# Story 1.3: Session Abstract & Speaker Bio

Status: ready-for-dev

## Story

As an attendee,
I want to tap a session title to read its abstract and tap speakers to see their bio and portrait,
so that I can learn about talks and speakers.

## Acceptance Criteria

1. **AC1 — Abstract Detail (P3)**: Given I'm on a session card (P2), When I tap the title area, Then I see the abstract detail screen (P3) with session title, full description text (Crown-scrollable for longer abstracts), and time slot at the bottom.

2. **AC2 — Single Speaker Bio (P4)**: Given a session has one speaker, When I tap the speaker area on P2, Then I see the speaker bio screen (P4) with large portrait (circular, ~80pt), full name, company name with company logo, and bio text (Crown-scrollable).

3. **AC3 — Multi-Speaker Grid (P5)**: Given a session has 2+ speakers, When I tap the speaker area on P2, Then I see the multi-speaker grid (P5) with a "Speakers (N)" header and a grid of portrait thumbnails with names and company logos. Each portrait is tappable.

4. **AC4 — Individual Speaker from Grid (P6)**: Given I'm on the multi-speaker grid (P5), When I tap an individual portrait, Then I see that speaker's bio screen (P6) — same layout as P4.

5. **AC5 — Back Navigation**: Given I'm on any detail screen (P3/P4/P5/P6), When I swipe back or tap the system back button, Then I return to the exact session card (P2) I navigated from — scroll position preserved.

6. **AC6 — Progressive Publishing Guard**: Given the event is in `TOPIC` phase, When I view session cards, Then title taps are disabled (no abstract available). Given `SPEAKERS` phase, title taps are disabled but speaker taps work. Given `AGENDA` phase, both title and speaker taps are active.

7. **AC7 — Company Logo Display**: Given a speaker has a company, When I view their bio (P4/P6), Then I see the company logo (loaded from CDN companyLogoUrl) next to the company name. If no logo available, show company name only.

## Tasks / Subtasks

- [ ] **Task 1: Create AbstractDetailView (P3)** (AC: #1)
  - [ ] 1.1 Create `Views/Public/AbstractDetailView.swift`
  - [ ] 1.2 Layout:
    ```
    ┌──────────────────────┐
    │  ◄ Back              │
    │                       │
    │  Cloud Native         │  ← Title (SF Pro Rounded ~16pt Semibold)
    │  Security in 2026     │
    │  ─────────────────── │
    │  Microservices gone   │  ← Abstract text (SF Pro ~13pt, Crown scrollable)
    │  wrong: lessons from  │
    │  3 years of produc... │
    │                       │
    │  18:00 – 18:45       │  ← Time slot (secondary color)
    └──────────────────────┘
    ```
  - [ ] 1.3 Use `ScrollView` with Crown scroll for long abstracts
  - [ ] 1.4 Accept `CachedSession` as input — display `session.abstract` (maps to API `description` field)
  - [ ] 1.5 Handle nil/empty abstract gracefully: show "No description available" in secondary color

- [ ] **Task 2: Create SpeakerBioView (P4/P6)** (AC: #2, #4, #7)
  - [ ] 2.1 Create `Views/Public/SpeakerBioView.swift` — used for both P4 (from session card) and P6 (from multi-speaker grid)
  - [ ] 2.2 Layout:
    ```
    ┌──────────────────────┐
    │  ◄ Back              │
    │                       │
    │      ┌──────┐        │  ← Large portrait (~80pt, circular)
    │      │  📷  │        │
    │      └──────┘        │
    │  Anna Meier          │  ← Name (SF Pro Rounded ~16pt Semibold)
    │  ACME Corp  [logo]   │  ← Company + logo (~20pt logo, inline)
    │  ─────────────────── │
    │  Senior architect     │  ← Bio (SF Pro ~13pt, Crown scrollable)
    │  specializing in...   │
    └──────────────────────┘
    ```
  - [ ] 2.3 Large circular portrait: reuse `SpeakerPortraitView` from W1.2 with larger size (~80pt)
  - [ ] 2.4 Company logo: `AsyncImage` from `companyLogoUrl`, ~20pt height, shown inline next to company name
  - [ ] 2.5 Bio text: `ScrollView` with Crown scroll for long bios (max 2000 chars from API)
  - [ ] 2.6 Accept `CachedSpeaker` as input
  - [ ] 2.7 Handle missing portrait (SF Symbol placeholder), missing company logo (text only), missing bio ("No bio available")

- [ ] **Task 3: Create MultiSpeakerGridView (P5)** (AC: #3)
  - [ ] 3.1 Create `Views/Public/MultiSpeakerGridView.swift`
  - [ ] 3.2 Layout:
    ```
    ┌──────────────────────┐
    │  ◄ Back              │
    │  Speakers (3)        │  ← Header with count
    │                       │
    │  ┌────┐  ┌────┐      │  ← 2-column portrait grid (tappable → P6)
    │  │ 📷 │  │ 📷 │      │
    │  │John│  │Jane│      │
    │  │ACME│  │Corp│      │
    │  └────┘  └────┘      │
    │       ┌────┐         │
    │       │ 📷 │         │
    │       │Bob │         │
    │       │Inc │         │
    │       └────┘         │
    └──────────────────────┘
    ```
  - [ ] 3.3 Use `LazyVGrid` with 2 columns for portrait layout
  - [ ] 3.4 Each portrait uses `SpeakerPortraitView` from W1.2 (~40pt)
  - [ ] 3.5 Each portrait is wrapped in `NavigationLink` → pushes to SpeakerBioView (P6)
  - [ ] 3.6 Crown-scrollable if grid exceeds screen height (>4 speakers)
  - [ ] 3.7 Accept `[CachedSpeaker]` array as input

- [ ] **Task 4: Wire Navigation in SessionCardView** (AC: #1, #2, #3, #5, #6)
  - [ ] 4.1 Modify `Views/Public/SessionCardView.swift` — activate the tap targets prepared in W1.2
  - [ ] 4.2 Title area: `NavigationLink` → pushes `AbstractDetailView(session:)` (only when AGENDA phase)
  - [ ] 4.3 Speaker area (1 speaker): `NavigationLink` → pushes `SpeakerBioView(speaker:)` (when SPEAKERS or AGENDA phase)
  - [ ] 4.4 Speaker area (2+ speakers): `NavigationLink` → pushes `MultiSpeakerGridView(speakers:)` (when SPEAKERS or AGENDA phase)
  - [ ] 4.5 Disable navigation links based on `currentPublishedPhase`:
    - `TOPIC`: All taps disabled
    - `SPEAKERS`: Speaker area taps active, title taps disabled
    - `AGENDA`: Both active
  - [ ] 4.6 Visual hint for tappable areas: subtle chevron or color tint in AGENDA phase

- [ ] **Task 5: Company Logo Integration** (AC: #7)
  - [ ] 5.1 Company logo URL is already in `CachedSpeaker.companyLogoUrl` (populated from API `company` expand)
  - [ ] 5.2 In SpeakerBioView: `AsyncImage` for company logo, inline with company name text
  - [ ] 5.3 Fallback: show company name as plain text if no logo URL
  - [ ] 5.4 Logo sizing: ~20pt height, aspect-ratio preserved, next to company text

- [ ] **Task 6: Write Tests** (AC: all)
  - [ ] 6.1 `AbstractDetailViewTests.swift` — verify layout with full abstract, empty abstract, long abstract Crown scroll
  - [ ] 6.2 `SpeakerBioViewTests.swift` — verify portrait, company logo, bio rendering; verify missing data fallbacks
  - [ ] 6.3 `MultiSpeakerGridViewTests.swift` — verify 2-column grid layout, portrait tap navigation
  - [ ] 6.4 Extend `SessionCardViewTests.swift` — verify navigation links activate/deactivate based on progressive publishing phase
  - [ ] 6.5 Verify all new views in Xcode Previews with sample data

## Dev Notes

### Previous Story Context (W1.1 + W1.2)

**From W1.1:**
- Project structure established (all directories)
- SwiftData models defined: `CachedEvent`, `CachedSession` (with `abstract: String?`), `CachedSpeaker` (with `bio: String?`, `companyLogoUrl: String?`)
- OpenAPI generated types: `EventDetail`, `Session`, `Speaker`, `SessionSpeaker` in `Generated/Models/`
- Type conversion extensions: `EventDetail → WatchEvent → CachedEvent` flow
- `PublicEventService` fetches event data including sessions and speakers (uses generated `EventDetail` type)
- `PublicViewModel` manages public zone state
- German localization (de_CH): `SwissDateFormatter`, `Localizable.strings`

**From W1.2:**
- `SessionCardView` (P2) has title area and speaker area as **separate tappable regions** — this story activates them
- `SpeakerPortraitView` exists as reusable shared component (~40pt circular portraits with name/company)
- `SessionListView` wraps EventHero + session cards in vertical paging
- `PortraitCache` handles file-based portrait caching
- `PublicViewModel` extended with `displayableSessions`, `hasSpeakerPhase`, `hasAgendaPhase` computed properties

### Critical Architecture Constraints

- **NavigationLink push navigation**: All detail views (P3, P4, P5, P6) push onto the NavigationStack inside the public zone. Do NOT use sheets or full-screen covers — use standard `NavigationLink` for system back button support.
- **P4 and P6 are the same view**: `SpeakerBioView` serves both P4 (from session card) and P6 (from multi-speaker grid). Single component, no duplication.
- **Crown-scrollable text**: Both AbstractDetailView and SpeakerBioView need `ScrollView` for long content. watchOS handles Crown scroll automatically when a `ScrollView` is the primary scrollable content.
- **Progressive publishing enforcement**: This is the story that makes publishing phases truly meaningful. In TOPIC phase, the app shows session titles but tapping does nothing (no abstract, no speakers). In SPEAKERS phase, speaker taps work but abstract taps don't. In AGENDA phase, everything is active.
- **No new API calls needed**: All data (abstracts, bios, company logos) is already fetched in W1.1's `PublicEventService` call to `GET /api/v1/events/current?include=topics,venue,sessions`. Speaker data includes bio and company info.

### Navigation Flow

```
SessionCardView (P2)
├── Tap title area (AGENDA phase only)
│   └── NavigationLink → AbstractDetailView (P3)
│       └── Back → P2
│
├── Tap speaker area (1 speaker, SPEAKERS+ phase)
│   └── NavigationLink → SpeakerBioView (P4)
│       └── Back → P2
│
└── Tap speaker area (2+ speakers, SPEAKERS+ phase)
    └── NavigationLink → MultiSpeakerGridView (P5)
        ├── Tap portrait → NavigationLink → SpeakerBioView (P6)
        │   └── Back → P5
        └── Back → P2
```

All navigation uses `NavigationLink` within the existing `NavigationStack` in ContentView's public zone tab. The system back button/swipe-back gesture works automatically.

### Data Field Mapping (OpenAPI → SwiftData → View)

**Type Flow:** `EventDetail` (generated from OpenAPI) → `WatchEvent` (domain) → `CachedEvent/CachedSession/CachedSpeaker` (SwiftData)

| View Field | SwiftData Field | OpenAPI Generated Type | API Source |
|---|---|---|---|
| Abstract text | `CachedSession.abstract` | `Session.description` | `session.description` |
| Speaker bio | `CachedSpeaker.bio` | `Speaker.bio` | `speaker.bio` (max 2000 chars) |
| Company name | `CachedSpeaker.company` | `Speaker.company` | `speaker.company` (max 12 chars) |
| Company logo | `CachedSpeaker.companyLogoUrl` | `Speaker.companyLogoUrl` | `speaker.companyLogoUrl` (CDN URL) |
| Portrait | `CachedSpeaker.profilePictureUrl` | `Speaker.profilePictureUrl` | `speaker.profilePictureUrl` (CDN URL) |
| Speaker name | `CachedSpeaker.firstName + lastName` | `Speaker.firstName`, `Speaker.lastName` | `speaker.firstName`, `speaker.lastName` |
| Speaker role | `CachedSpeaker.speakerRole` | `SessionSpeaker.speakerRole` | `speaker.speakerRole` (PRIMARY_SPEAKER, CO_SPEAKER, MODERATOR, PANELIST) |

**Note:** All types in `Generated/Models/` are auto-generated from `docs/api/events-api.openapi.yml` using `scripts/generate-types.sh`. Never edit generated files directly.

### Localization & Internationalization

**All user-facing text must use German localization (de_CH):**

```swift
// AbstractDetailView
Text(NSLocalizedString("session.no_description", comment: "No description available"))

// SpeakerBioView
Text(NSLocalizedString("speaker.no_bio", comment: "No bio available"))

// MultiSpeakerGridView
Text(String(format: NSLocalizedString("speakers.count", comment: "Speakers (%d)"), speakers.count))
```

**Time Formatting:**
- Use `SwissDateFormatter.formatTimeString()` for session times in AbstractDetailView
- Format: `18:00 – 18:45` (24-hour, Swiss German format)

**String Keys to Add:**
- `session.no_description` = "Keine Beschreibung verfügbar"
- `speaker.no_bio` = "Keine Biografie verfügbar"
- `speakers.count` = "Referenten (%d)"

### UX Design Details

**AbstractDetailView (P3):**
- Title: SF Pro Rounded ~16pt Semibold (same as session card)
- Abstract body: SF Pro ~13pt Regular, system white
- Time slot: SF Pro ~11pt, secondary color, bottom of view (formatted with `SwissDateFormatter`)
- Crown-scrollable — no max line limit
- No truncation — show full abstract
- Fallback: Localized "No description available" if abstract is nil/empty

**SpeakerBioView (P4/P6):**
- Large portrait: ~80pt diameter circular (reuse SpeakerPortraitView with larger frame)
- Name: SF Pro Rounded ~16pt Semibold, centered below portrait
- Company row: company logo (~20pt height) + company name (SF Pro ~13pt), centered
- Bio body: SF Pro ~13pt Regular, Crown-scrollable
- Speaker role NOT displayed (too noisy for Watch — kept in data for future use)
- Fallback: Localized "No bio available" if bio is nil/empty

**MultiSpeakerGridView (P5):**
- Header: Localized "Referenten (N)" — SF Pro ~13pt Medium
- Grid: LazyVGrid, 2 columns, ~40pt portraits (same size as session card)
- Each cell: SpeakerPortraitView + name + company
- Tappable: entire cell is NavigationLink

### Anti-Patterns to Avoid

- **Do NOT use `.sheet()` for detail views** — sheets on watchOS are dismissible with down-swipe which conflicts with Crown scroll. Use NavigationLink push.
- **Do NOT add a "Read More" truncation** to abstracts — Watch users expect to see full content after tapping. Show everything, let Crown scroll handle length.
- **Do NOT show speaker role** on the Watch — too much text for the screen. The role data is in the model for future use but not displayed.
- **Do NOT add text input anywhere** — UX spec explicitly forbids all text input on Watch.
- **Do NOT cache company logos separately** — they're small enough to load via AsyncImage with URLSession's built-in HTTP cache.

### Files Created/Modified in This Story

**New files:**
- `Views/Public/AbstractDetailView.swift` — P3: Session abstract detail
- `Views/Public/SpeakerBioView.swift` — P4/P6: Speaker bio with large portrait
- `Views/Public/MultiSpeakerGridView.swift` — P5: Multi-speaker portrait grid

**Modified files:**
- `Views/Public/SessionCardView.swift` — Activate NavigationLink taps for title and speaker areas
- `Views/Shared/SpeakerPortraitView.swift` — Potentially add configurable size parameter if not already present

### Project Structure After This Story

```
BATbern-watch Watch App/
├── App/
│   ├── BATbernWatchApp.swift
│   └── ContentView.swift
├── Views/
│   ├── Public/
│   │   ├── EventHeroView.swift           ← W1.1
│   │   ├── SessionCardView.swift         ← W1.2 (MODIFIED: NavigationLinks activated)
│   │   ├── SessionListView.swift         ← W1.2
│   │   ├── AbstractDetailView.swift      ← NEW: P3
│   │   ├── SpeakerBioView.swift          ← NEW: P4/P6
│   │   └── MultiSpeakerGridView.swift    ← NEW: P5
│   ├── Organizer/                        ← Empty (Epic 2+)
│   └── Shared/
│       ├── BATbernSymbolView.swift       ← W1.1
│       └── SpeakerPortraitView.swift     ← W1.2 (possibly MODIFIED: size param)
├── ViewModels/
│   └── PublicViewModel.swift             ← W1.2
├── Domain/                               ← Empty (Epic 3+)
├── Data/
│   ├── PublicEventService.swift          ← W1.1
│   ├── LocalCache.swift                  ← W1.1
│   └── PortraitCache.swift              ← W1.2
├── Models/                               ← All from W1.1 (unchanged)
├── Complications/                        ← Empty (Epic 3)
└── Resources/
```

### References

- [Source: docs/watch-app/architecture.md#Frontend-Architecture] — NavigationStack push navigation, MVVM layers
- [Source: docs/watch-app/architecture.md#Complete-Layer-Architecture] — AbstractDetailView (P3), SpeakerBioView (P4), MultiSpeakerGridView (P5), IndividualSpeakerBioView (P6 = same as P4)
- [Source: docs/watch-app/ux-design-specification.md#Abstract-Detail] — P3 layout: title, abstract, time, Crown-scrollable
- [Source: docs/watch-app/ux-design-specification.md#Speaker-Bio] — P4/P6 layout: large portrait, name, company+logo, bio
- [Source: docs/watch-app/ux-design-specification.md#Multi-Speaker-Grid] — P5 layout: "Speakers (N)" header, 2-column grid, tappable
- [Source: docs/watch-app/ux-design-specification.md#Navigation-Rules] — Tap title → P3, tap speaker (1) → P4, tap speaker (2+) → P5, tap portrait → P6
- [Source: docs/watch-app/ux-design-specification.md#Anti-Patterns-to-Avoid] — No multi-level navigation during events, no confirmation dialogs
- [Source: docs/watch-app/prd-batbern-watch.md#Public-Zone] — FR31 (tap for abstract), FR32 (tap for speaker bio), FR33 (multi-speaker grid)
- [Source: docs/watch-app/epics.md#W1.3] — Story definition and acceptance criteria
- [Source: _bmad-output/implementation-artifacts/w1-2-session-card-browsing.md] — Previous story: SessionCardView tap targets, SpeakerPortraitView, PublicViewModel extensions

## Dev Agent Record

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

### File List
