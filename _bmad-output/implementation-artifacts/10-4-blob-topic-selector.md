# Story 10.4: Blob Topic Selector

Status: done

## Story

As an **organizer**,
I want a full-screen physics-based blob visualization for topic selection, accessible via a button on the topic management page,
so that during our Teams meeting I can intuitively feel how well a proposed topic aligns with partner interests and event history — without reading a single number.

## Context & Background

During Teams calls, BATbern organizers discuss what topic to pick for the next event. The current heatmap (`TopicManagementPage` → Heat Map view) is static and not interactive enough for live discussion. This story adds:

1. A **"Blob Selector" button** to `TopicManagementPage` (shown only when `eventCode` is present) that opens a **new full-screen page** at `/organizer/events/:eventCode/topic-blob`
2. The full-screen `BlobTopicSelectorPage` with a small back button (top-left, with unsaved-changes warning) and a physics canvas taking the full browser viewport

**Existing infrastructure to reuse (do NOT reinvent):**
- Topic backlog: already exists at `GET /api/v1/topics` — 5 fixed categories: `technical`, `management`, `soft_skills`, `industry_trends`, `tools_platforms`. Topic model has `category`, `stalenessScore`, `status` (AVAILABLE/CAUTION/UNAVAILABLE), `title`, `topicCode`.
- Partner topics: already available via existing partner coordination service
- Past events: already fetched in `TopicBacklogManager` via `useEvents({ page: 1, limit: 1000 })` — provides `eventNumber` + `title`
- `TopicBacklogManager` 3-view toggle (Heat Map / List / Board): **leave completely untouched**
- Route `/organizer/topics`: **leave completely untouched** — just add the blob button when `eventCode` is present

**Full design spec:** `_bmad-output/implementation-artifacts/blob-topic-selector-spec.md`
**Brainstorm session:** `_bmad-output/brainstorming/brainstorming-session-2026-02-24.md`

## Acceptance Criteria

### Entry Point
1. `TopicManagementPage` shows a "Blob Selector" button (BubbleChart icon, `variant="outlined"`) in the header area **only when `eventCode` is present in query params**. It sits alongside the existing view toggle / create button row.
2. Clicking the button navigates to `/organizer/events/{eventCode}/topic-blob`.
3. The route `/organizer/events/:eventCode/topic-blob` is registered in `App.tsx` with `ProtectedRoute` + `AuthLayout` wrapping a new `BlobTopicSelectorPage` component. It renders **without the standard sidebar/nav** — full viewport only (use a layout different from `AuthLayout` if needed, or suppress the nav — see Dev Notes).
4. The page renders a small back button (top-left, `position: fixed`, z-index above canvas) with label "← Back to Topic List". Clicking it shows a MUI `Dialog` warning: "Unsaved session — all changes will be lost. Go back?" → Confirm / Cancel.

### Canvas & Physics
5. Canvas fills the entire browser viewport (`width: 100vw`, `height: 100vh`) as a dark-navy SVG (`#0d1b2a`). No scrollbars.
6. D3 force simulation runs with `forceManyBody` (general repulsion), `forceCollide` (no overlaps), `forceCenter` (soft pull to canvas center). Performance smooth with ≤90 simultaneous nodes.
7. D3 zoom on the SVG — infinite canvas, scale range `[0.1, 4]`. "Fit All" button and "Snap to Active" button always visible (top-right, `position: fixed`).

### Session Bootstrap
8. On mount, `GET /api/v1/events/{eventCode}/topic-session-data` returns all canvas data in one call. Backend aggregates: partner topics, past events with cluster, organizer backlog topics (from existing topics table), trending topics (LLM-fetched, cached).
9. If the LLM call for trending topics fails, the endpoint returns a hardcoded fallback list (see Dev Notes). Never return an error for this — trending topics are optional enhancement.

### Blue Blobs — Proposed Topics
10. Typing anywhere on the canvas (any printable key) opens a floating text input centered on screen. `Enter` spawns a blue blob (`#1976d2`) sliding in from the right edge. `Escape` cancels. `Ctrl+Z` removes last blue blob.
11. Blue blob displays its topic name as a centered SVG text label. Radius = `Math.max(40, Math.min(100, name.length * 5))`.
12. Blob spawns immediately on Enter (no API wait). A `POST /api/v1/events/{eventCode}/topic-similarity` call fires async; when the response arrives (~500ms), force strengths update. This visible "settling" is intentional.

### Green Blobs — Partner Interests
13. One green blob per partner topic (`partnerTopics[]` from session data). Max 20 green blobs. Partner company logo displayed inside (min 32px `<image>` element). Logo remains visible when absorbed.
14. Green blobs use `forceLink` toward the most similar blue blob (strength = `similarityScore`). If equally attracted to two blue blobs, green blob oscillates between them.
15. When distance between green and blue blob < sum of their radii, green is visually absorbed: green blob `opacity → 0`, logo floats inside the blue blob SVG group.

### Red Star Blobs — Past Events
16. 57+ past event blobs render as SVG star polygons (bumpy, spiky outline) in dormant state (`opacity: 0.15`, no forces). They form a faint background constellation.
17. When a blue blob is summoned, red stars with matching cluster AND within 6 event-numbers of the most recent event ignite: `opacity → 1.0`, SVG glow filter applied, custom repulsion force added.
18. Repulsion strength = `Math.max(0, 1 - ((mostRecentEventNum - pastEventNum) / 6)) * 150`. Events exactly 6 numbers ago = neutral. Each red star shows event number + short topic label.

### Ghost Candidates
19. Three ghost types render as semi-transparent drifting blobs on load:
    - **Backlog ghosts** (white `#ffffff`, `opacity: 0.25`) — from `organizerBacklog[]` (existing topics in the topics table, returned by session data endpoint as topic titles)
    - **Partner ghosts** (green tint `rgba(144, 238, 144, 0.3)`) — partner-submitted topic names
    - **Trend ghosts** (gold shimmer `rgba(255, 215, 0, 0.3)`) — AI-fetched trending topics
20. Ghosts pulse gently (CSS `@keyframes` scale 0.95↔1.05, 3s infinite).
21. Click ghost → ghost becomes a full blue blob at its current position. `Space` while hovering → same.

### Manual Override (Drag)
22. D3 drag handler on all blobs. Dragging a blob overrides the simulation for that node.
23. Holding a dragged blob within 30px of a compatible blob for ≥1.5s shows a merge invitation glow (SVG animated ring).
24. On release with halo active → blobs merge (larger absorbs smaller). Session-only, no API call.
25. Red star dragged close to blue blob → becomes an **orbiting satellite** (dashed SVG circle orbit path, animated). Signals "acknowledged warning."

### Accept Topic
26. Double-click a blue blob → opens a MUI Dialog: topic name, absorbed partner logos, "last covered" text. Optional free-text "override reason" field shown only if any red satellites are currently orbiting.
27. On confirm: `PATCH /api/v1/events/{eventCode}` with `topicSelectionNote` (format per spec) + `topicCode` field. Navigate to `/organizer/events/{eventCode}?tab=speakers`.

### Onboarding
28. First open (localStorage key `batbern_blob_onboarding_seen`): 10-second scripted D3 animation — ghost pulses → awakens → green drifts → red star ignites → settle. No text. Auto-dismisses. Sets localStorage flag.

### Backend: Session Data Endpoint (AC: 8–9)
29. New endpoint `GET /api/v1/events/{eventCode}/topic-session-data` in event-management-service. Returns:
    ```json
    {
      "partnerTopics": [{ "companyName": "Swisscom", "logoUrl": "...", "topics": ["AI in Operations"] }],
      "pastEvents": [{ "eventNumber": 57, "topicName": "Zero Trust", "cluster": "SECURITY" }],
      "organizerBacklog": ["Quantum Computing", "Green IT"],
      "trendingTopics": ["AI Agents", "Platform Engineering", ...]
    }
    ```
30. `organizerBacklog` comes from existing topics table — query topics where `category IN ('technical', 'industry_trends', 'tools_platforms', 'management', 'soft_skills')` limited to `status = 'AVAILABLE'`, return `title[]`. Do NOT create a new table or endpoint for backlog management.
31. `pastEvents` includes a `cluster` field from `BatbernTopicClusterService` (hardcoded map, see Dev Notes).

### Backend: Similarity Endpoint (AC: 12)
32. `POST /api/v1/events/{eventCode}/topic-similarity` → `{ topic: string }` → `{ cluster, similarityScore, relatedPastEventNumbers[] }`.
33. Known clusters pre-hardcoded in `BatbernTopicClusterService`. Novel topics → OpenAI embeddings API. Fallback: keyword match against cluster name strings.

### Event Model Extension (AC: 27)
34. Flyway migration adds `topic_selection_note TEXT` to `events` table. `topicSelectionNote` added to `EventUpdateRequest` DTO and `EventResponse` DTO in event-management-service.

---

## Tasks / Subtasks

### Task 1: Backend — `topic_selection_note` field on Event (AC: 34) ✅ DONE
- [x] 1.1 Write failing integration test: PATCH event with `topicSelectionNote` field
- [x] 1.2 Flyway `V66__add_topic_selection_note.sql`: `ALTER TABLE events ADD COLUMN topic_selection_note TEXT`
- [x] 1.3 Add `topicSelectionNote` to `PatchEventRequest` DTO, `Event` entity, `EventResponse` DTO, `EventMapper`, `EventController.applyPatchUpdates()`
- [x] 1.4 All tests pass (2 new tests green)

### Task 2: Backend — BatbernTopicClusterService (AC: 31, 33) ✅ DONE
- [x] 2.1 Write unit tests (55 tests) for cluster lookup + keyword matching
- [x] 2.2 Created `BatbernCluster.java` enum + `BatbernTopicClusterService` in `ch.batbern.events.service`
- [x] 2.3 `getCluster(int eventNumber) → BatbernCluster` — hardcoded map, defaults BUSINESS_OTHER
- [x] 2.4 `matchCluster(String topicText) → BatbernCluster` — word-boundary keyword matching + BUSINESS_OTHER fallback
- [x] 2.5 `getEventNumbersForCluster(BatbernCluster) → List<Integer>` — used by similarity service
- [x] All 55 tests green

### Task 3: Backend — Topic Similarity endpoint (AC: 32–33) ✅ DONE
- [x] 3.1 Integration test `TopicSimilarityControllerIntegrationTest` (6 tests) written + passing
- [x] 3.2 Created `TopicSimilarityController` + `TopicSimilarityService`
- [x] 3.3 `TopicSimilarityRequest { topic: String }`, `TopicSimilarityResponse { cluster, similarityScore, relatedPastEventNumbers[] }`
- [x] 3.4 Keyword matching → score 0.85; BUSINESS_OTHER fallback → score 0.50
- [x] 3.5 `@PreAuthorize("hasRole('ORGANIZER')")` on controller; 403 test green
- [x] 3.6 OpenAI key blank in tests → keyword fallback works; all 6 tests green

### Task 4: Backend — Topic Session Data endpoint (AC: 29–31) ✅ DONE
- [x] 4.1 Integration test `TopicSessionDataControllerIntegrationTest` (4 tests) written + passing
- [x] 4.2 Created `TopicSessionDataController` + `TopicSessionDataService`
- [x] 4.3 `PartnerApiClient` interface + `PartnerApiClientImpl` (RestTemplate, JWT propagation, graceful fallback)
- [x] 4.4 Past events from DB → enriched with cluster via `BatbernTopicClusterService`
- [x] 4.5 Organizer backlog: `topicRepository.findByStalenessScoreGreaterThanEqual(83)`, limit 50
- [x] 4.6 `TrendingTopicsService`: OpenAI gpt-4o-mini + 1-hour ConcurrentHashMap cache + hardcoded fallback
- [x] 4.7 `@PreAuthorize("hasRole('ORGANIZER')")` on controller
- [x] 4.8 All 4 integration tests green; added `openai.*` + `partner-service.*` config to application.yml

### Task 5: Frontend — Route + BlobTopicSelectorPage shell (AC: 3–4) ✅ DONE
- [x] 5.1 Create `web-frontend/src/pages/organizer/BlobTopicSelectorPage.tsx` — full-viewport container
- [x] 5.2 Register route in `App.tsx`: `<Route path="/organizer/events/:eventCode/topic-blob" element={<ProtectedRoute><BlobTopicSelectorPage /></ProtectedRoute>} />`
- [x] 5.3 Page renders WITHOUT `AuthLayout` (no sidebar, no nav). Use a bare `ProtectedRoute` wrapper only.
- [x] 5.4 Fixed back button (top-left, `position: fixed, top: 16px, left: 16px, zIndex: 1000`): "← Back to Topic List". On click: show MUI `Dialog` "Unsaved session — all changes will be lost. Go back?" Confirm → `navigate(\`/organizer/topics?eventCode=${eventCode}\`)`. Cancel → close dialog.
- [x] 5.5 Route reads `:eventCode` from `useParams()`.

### Task 6: Frontend — "Blob Selector" button in TopicManagementPage (AC: 1–2) ✅ DONE
- [x] 6.1 In `TopicManagementPage.tsx`, when `eventCode` is present, add a `<Button variant="outlined" startIcon={<BubbleChartIcon />} onClick={() => navigate(\`/organizer/events/${eventCode}/topic-blob\`)}` button
- [x] 6.2 Place the button in the header area — above the `TopicBacklogManager`, clearly separated (TopicBacklogManager untouched)
- [x] 6.3 Add i18n key: `events:navigation.blobSelector` → "Blob Selector" (en) / "Blob-Auswahl" (de)

### Task 7: Frontend — D3 canvas setup + zoom (AC: 5–7) ✅ DONE
- [x] 7.1 Installed D3: `npm install d3 @types/d3` (D3 v7, ES module)
- [x] 7.2 Created `web-frontend/src/components/BlobTopicSelector/` directory with all component files
- [x] 7.3 `BlobTopicSelector` renders full-viewport dark-navy SVG (`#0d1b2a`, `width: 100vw, height: 100vh`)
- [x] 7.4 Initialize `d3.zoom()` on SVG, scale extent `[0.1, 4]`. Zoom/pan updates a `<g>` transform.
- [x] 7.5 Initialize force simulation: `d3.forceSimulation()` + `forceManyBody(-30)` + `forceCollide(radius + 5)` + `forceCenter(width/2, height/2)`
- [x] 7.6 "Fit All" button: zoom to bounding box of all nodes. "Snap to Active" button: zoom to blue blobs only.

### Task 8: Frontend — Session data hook + bootstrap (AC: 8–9) ✅ DONE
- [x] 8.1 Created `useTopicSessionData(eventCode)` hook with React Query (`staleTime: Infinity`)
- [x] 8.2 On data load: initialize ghost nodes (backlog/partner/trend), red star nodes for past events, green blobs for partner interest
- [x] 8.3 All nodes added to D3 simulation with random spread across canvas

### Task 9: Frontend — Blue blob summon (AC: 10–12) ✅ DONE
- [x] 9.1 Global `keydown` on document: any printable character opens floating `<TextField>` at viewport center
- [x] 9.2 On `Enter`: add blue blob at right edge (`x: width + 50`), fire async `POST /topic-similarity`; `Escape` → dismiss
- [x] 9.3 `Ctrl+Z` listener: remove last blue blob + update links + re-heat simulation
- [x] 9.4 Similarity response → update `forceLink` strengths for green nodes; activate matching red stars

### Task 10: Frontend — Green blobs + partner logos (AC: 13–15) ✅ DONE
- [x] 10.1 Green blobs rendered as SVG `<circle>` (`#2e7d32`) with `<image>` logo centered inside (32px)
- [x] 10.2 `forceLink` array updated after similarity response; link strength = `similarityScore`
- [x] 10.3 Absorption in tick: distance check → `green.absorbed = true`, logo floats inside blue blob `<g>`

### Task 11: Frontend — Red star blobs (AC: 16–18) ✅ DONE
- [x] 11.1 Red stars rendered as SVG `<polygon>` (5-point star), dormant opacity `0.15`
- [x] 11.2 On similarity response: activate red stars in `relatedPastEventNumbers` within 6 events of max
- [x] 11.3 Active: `opacity → 1.0`, SVG feGaussianBlur red glow filter, custom repulsion force in tick; event number label rendered

### Task 12: Frontend — Ghost candidates (AC: 19–21) ✅ DONE
- [x] 12.1 Ghosts rendered as `<circle>` — white/0.25 (backlog), green-tint/0.3 (partner), gold/0.3 (trend)
- [x] 12.2 CSS `@keyframes blobPulse` scale 0.95↔1.05, 3s infinite, applied to `.ghost-group`
- [x] 12.3 Click handler: remove ghost, spawn blue blob at ghost's position

### Task 13: Frontend — Drag, merge, orbit (AC: 22–25) ✅ DONE
- [x] 13.1 `d3.drag()` on all nodes: `fx/fy` set on dragstart/drag, cleared on dragend
- [x] 13.2 Proximity timer: 100ms debounce → merge halo stroke on compatible nearby nodes; on dragend with halo → merge (remove smaller, grow larger, absorb logos)
- [x] 13.3 Red star dragged near blue: `red.orbiting = blue.id` → orbit computed per tick (angle += 0.02, fx/fy set along orbit circle)

### Task 14: Frontend — Accept dialog + session note (AC: 26–27) ✅ DONE
- [x] 14.1 `AcceptTopicDialog.tsx`: MUI Dialog with topic name, absorbed partner logos (Avatar list), last-covered text, optional override reason TextField (shown only when orbiting red exists)
- [x] 14.2 On confirm: `blobTopicService.acceptTopic()` → `PATCH /events/{eventCode}`; navigate to `?tab=speakers`
- [x] 14.3 Added i18n keys under `organizer:blobSelector.*` (en + de)

### Task 15: Frontend — Onboarding overlay (AC: 28) ✅ DONE
- [x] 15.1 `OnboardingOverlay.tsx`: checks `localStorage.getItem('batbern_blob_onboarding_seen')`; renders full-viewport overlay
- [x] 15.2 10s scripted D3 sequence: 0-2s ghost pulse → 2-4s awakens to blue → 4-6s green drifts → 6-8s red star ignites → 8-10s settle
- [x] 15.3 On complete: `localStorage.setItem(ONBOARDING_KEY, 'true')`, unmounts overlay

### Task 16: Frontend — Service layer additions (AC: all) ✅ DONE
- [x] 16.1 Created `web-frontend/src/services/blobTopicService.ts` with `getSessionData`, `getSimilarity`, `acceptTopic`
- [x] 16.2 TypeScript types: `TopicSessionData`, `TopicSimilarityResponse`, `BlueBlobNode`, `GreenBlobNode`, `GhostNode`, `RedStarNode`, `SimLink`, `AbsorbedLogo` in `BlobTopicSelector/types.ts`

---

## Dev Notes

### Layout: Full-Screen Without Nav

The existing `AuthLayout` renders sidebar navigation. `BlobTopicSelectorPage` must NOT use it. In `App.tsx`, wrap the route with `ProtectedRoute` only — no `AuthLayout`:

```tsx
// App.tsx — add after existing /organizer/topics route
<Route
  path="/organizer/events/:eventCode/topic-blob"
  element={
    <ProtectedRoute>
      <BlobTopicSelectorPage />
    </ProtectedRoute>
  }
/>
```

The back button uses `position: fixed` so it overlays the canvas regardless of scroll/zoom state.

### Button Placement in TopicManagementPage

The `TopicManagementPage` only renders `<TopicBacklogManager>`. The button belongs in `TopicManagementPage`, not inside `TopicBacklogManager`. Add above the `<TopicBacklogManager />` call:

```tsx
// TopicManagementPage.tsx
{eventCode && (
  <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 2, px: 3 }}>
    <Button
      variant="outlined"
      startIcon={<BubbleChartIcon />}
      onClick={() => navigate(`/organizer/events/${eventCode}/topic-blob`)}
    >
      {t('navigation.blobSelector', 'Blob Selector')}
    </Button>
  </Box>
)}
```

Import: `import { BubbleChart as BubbleChartIcon } from '@mui/icons-material'`

### Existing Topic Backlog (Do NOT Create New CRUD)

Topics are already managed at `/organizer/topics` with a full CRUD UI. The 5 existing categories:
- `technical` — Technical
- `management` — Management
- `soft_skills` — Soft Skills
- `industry_trends` — Industry Trends
- `tools_platforms` — Tools & Platforms

For ghost backlog candidates in the blob view, the backend session data endpoint queries the **existing topics table** (`status = 'AVAILABLE'`), returns `title[]` — no new tables, no new management UI needed. Organizers already manage their topic backlog via the existing page.

### BatbernTopicClusterService — Hardcoded Cluster Map

```java
public enum BatbernCluster { AI_ML, SECURITY, ARCHITECTURE, DATA, CLOUD_INFRA, MOBILE, BUSINESS_OTHER }

private static final Map<Integer, BatbernCluster> EVENT_CLUSTERS = Map.ofEntries(
  Map.entry(40, AI_ML), Map.entry(44, AI_ML), Map.entry(49, AI_ML),
  Map.entry(56, AI_ML), Map.entry(58, AI_ML),
  Map.entry(16, SECURITY), Map.entry(27, SECURITY), Map.entry(38, SECURITY),
  Map.entry(48, SECURITY), Map.entry(57, SECURITY),
  Map.entry(2, ARCHITECTURE), Map.entry(3, ARCHITECTURE), Map.entry(12, ARCHITECTURE),
  Map.entry(13, ARCHITECTURE), Map.entry(30, ARCHITECTURE), Map.entry(41, ARCHITECTURE),
  Map.entry(43, ARCHITECTURE), Map.entry(55, ARCHITECTURE),
  Map.entry(15, DATA), Map.entry(18, DATA), Map.entry(29, DATA),
  Map.entry(33, DATA), Map.entry(52, DATA), Map.entry(53, DATA),
  Map.entry(36, CLOUD_INFRA), Map.entry(39, CLOUD_INFRA),
  Map.entry(51, CLOUD_INFRA), Map.entry(54, CLOUD_INFRA),
  Map.entry(22, MOBILE), Map.entry(26, MOBILE)
  // all others → BUSINESS_OTHER via default
);
```

**Recency**: Query `MAX(event_number)` from events table at request time. Repulsion applies to events where `(maxEventNum - pastEventNum) <= 6`.

### OpenAI Integration

Use Spring `RestClient` (already available in Spring Boot 3.x — no new dependency):
```java
// application.yml
openai:
  api-key: ${OPENAI_API_KEY}
  base-url: https://api.openai.com/v1

// application-test.yml — use a mock or skip with @ConditionalOnProperty
```

**Trending topics prompt** (GPT-4o-mini, `temperature: 0.3`):
```
List exactly 10 currently trending IT architecture and software engineering topics relevant to Swiss enterprise teams. Return ONLY a JSON array of short topic strings, max 4 words each. Example: ["AI Agents","Platform Engineering"]
```

**Hardcoded fallback (always return if OpenAI fails — never return error):**
```java
List.of("AI Agents", "Platform Engineering", "FinOps", "Rust Language",
        "WebAssembly", "Cybersecurity Mesh", "Sovereign Cloud",
        "Digital Twin", "Edge AI", "Developer Experience")
```

**Embedding model:** `text-embedding-3-small` — cheap (~$0.00002/call), fast.

### D3 v7 Setup

```bash
npm install d3 @types/d3
```

D3 v7 is ESM. Vite handles it without config changes. Use `import * as d3 from 'd3'`.

Key APIs: `d3.forceSimulation`, `d3.forceManyBody`, `d3.forceLink`, `d3.forceCollide`, `d3.forceCenter`, `d3.zoom`, `d3.drag`, `d3.select`.

**React + D3 pattern for this project:** Use `useRef` for the SVG element. D3 manages node positions via simulation. React re-renders only when blobs are added/removed (not on every tick). For tick updates, D3 directly mutates DOM via `d3.select()` — do NOT use React state for x/y positions.

### Flyway Migration

**File:** `services/event-management-service/src/main/resources/db/migration/V64__add_topic_selection_note.sql`

```sql
ALTER TABLE events ADD COLUMN topic_selection_note TEXT;
```

Run: `./gradlew :services:event-management-service:flywayMigrate`
Verify: `./gradlew :services:event-management-service:flywayInfo`
Follow: `docs/guides/flyway-migration-guide.md`

### Session Note Format (for AcceptTopicDialog)

```
✅ Selected Topic: Platform Engineering
   Partner alignment: Swisscom, UBS (2/9 partners)
   Last covered: BATbern #54 (4 events ago — within caution zone)
   Override reason: Strong partner demand outweighs recency
   Competing candidates: Cloud Native, DevOps Culture
```

### i18n Keys to Add (organizer namespace: `en.json` + `de.json`)

```json
{
  "navigation": {
    "blobSelector": "Blob Selector"
  },
  "blobSelector": {
    "typeTopic": "Type a topic and press Enter...",
    "fitAll": "Fit All",
    "snapToActive": "Snap to Active",
    "backButton": "← Back to Topic List",
    "unsavedWarning": "Unsaved session — all changes will be lost. Go back?",
    "accept": {
      "title": "Select Topic: {{topic}}",
      "partnerAlignment": "{{count}} of 9 partners aligned",
      "lastCovered": "Last covered: BATbern #{{num}} ({{count}} events ago)",
      "overrideReason": "Why are we revisiting this topic? (optional)",
      "confirm": "Select this topic",
      "cancel": "Keep exploring"
    }
  }
}
```

### Testing

- **Backend unit tests:** `BatbernTopicClusterService` (all 57 events map correctly), `TrendingTopicsService` (fallback when API fails), `TopicSimilarityService` (cluster match + fallback)
- **Backend integration tests:** `TopicSessionDataControllerTest extends AbstractIntegrationTest`, `TopicSimilarityControllerTest extends AbstractIntegrationTest` (mock OpenAI with `@MockBean`)
- **Frontend E2E:** `e2e/organizer/blob-topic-selector.spec.ts` — verify blob button appears on `/organizer/topics?eventCode=BATbern57`, clicking navigates to `/organizer/events/BATbern57/topic-blob`, canvas renders, back button triggers warning dialog
- **Do NOT** unit test D3 simulation x/y positions — test integration via E2E only for canvas behavior

### Architecture Compliance (CLAUDE.md)

- Never use `process.env` → use `config` object for any env values in frontend
- Never use `fetch()` directly → use `blobTopicService` (via `apiClient`)
- All backend integration tests → extend `AbstractIntegrationTest` (PostgreSQL via Testcontainers, never H2)
- TDD: write failing tests first for all backend tasks

## Change Log

| Date | Author | Change |
|---|---|---|
| 2026-02-24 | claude-sonnet-4-6 | Tasks 1–4 backend complete (67 tests green) |
| 2026-02-24 | claude-sonnet-4-6 | Tasks 5–16 frontend complete (3633 tests green, TS 0 errors) |
| 2026-02-24 | claude-sonnet-4-6 | Status → review; deviation noted: simulation logic embedded in BlobTopicSelector.tsx (no useBlobSimulation.ts extraction) |
| 2026-02-24 | claude-sonnet-4-6 | Code review fixes: H1 logoUrl (PartnerApiClientImpl fetches company logos via /partners?include=company), H2 merge halo 100ms→1500ms (AC 23), M1 removed console.log from TopicManagementPage, M3 RestClient as singleton @PostConstruct field, M4 Space-while-hovering ghost (hoveredGhostRef + mouseover/mouseout), M5 window resize handler. All tests green (67 EMS + 3633 frontend). |

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- TypeScript: 0 errors (tsc --noEmit exit 0)
- Frontend tests: 3633 passed, 0 failed (255 test files)

### Completion Notes List

**Tasks 1–4 (Backend):** Already complete before this session.

**Tasks 5–16 (Frontend):**
- Installed D3 v7 (`d3` + `@types/d3`) — Vite handles ESM natively, no config changes needed
- React+D3 pattern: D3 owns all SVG content via `useEffect`/`useRef`; React state only for structural changes (add/remove blobs, dialogs)
- `BlobTopicSelectorPage` wraps in `ProtectedRoute` only (no `AuthLayout`) — satisfies full-screen requirement
- Ghost pulse implemented via CSS `@keyframes blobPulse` on `.ghost-group` class
- Merge halo uses SVG `stroke` attribute toggled by D3; mergeHaloNodesRef tracks halo state without React re-renders
- Orbiting red stars: `fx/fy` locked per tick to orbit circle; `orbitAngle` increments 0.02 rad/tick
- Green absorption checked every tick; logos transferred to blue blob group via D3 data join
- `forceLink` updated on each similarity response — strength = `similarityScore`
- Red glow: `feGaussianBlur` + `feMerge` filter defined in SVG `<defs>` at init
- Onboarding: localStorage key `batbern_blob_onboarding_seen`; 10s D3-scripted animation, no text
- All i18n keys added to `events` (navigation.blobSelector) and `organizer` (blobSelector.*) namespaces in en + de

### File List

**New files:**
- `web-frontend/src/components/BlobTopicSelector/types.ts`
- `web-frontend/src/components/BlobTopicSelector/BlobTopicSelector.tsx`
- `web-frontend/src/components/BlobTopicSelector/useTopicSessionData.ts`
- `web-frontend/src/components/BlobTopicSelector/AcceptTopicDialog.tsx`
- `web-frontend/src/components/BlobTopicSelector/OnboardingOverlay.tsx`
- `web-frontend/src/services/blobTopicService.ts`
- `web-frontend/src/pages/organizer/BlobTopicSelectorPage.tsx`
- `web-frontend/e2e/organizer/blob-topic-selector.spec.ts`

**Modified files:**
- `web-frontend/src/App.tsx` — added BlobTopicSelectorPage lazy import + route
- `web-frontend/src/pages/organizer/TopicManagementPage.tsx` — added Blob Selector button
- `web-frontend/public/locales/en/events.json` — added `navigation.blobSelector`
- `web-frontend/public/locales/de/events.json` — added `navigation.blobSelector`
- `web-frontend/public/locales/en/organizer.json` — added `blobSelector.*` keys
- `web-frontend/public/locales/de/organizer.json` — added `blobSelector.*` keys
- `web-frontend/package.json` — added `d3` + `@types/d3` dependencies

**Modified by code review fixes:**
- `services/event-management-service/src/main/java/ch/batbern/events/client/impl/PartnerApiClientImpl.java` — logoUrl via fetchLogoMap()
- `services/event-management-service/src/main/java/ch/batbern/events/service/TrendingTopicsService.java` — RestClient as @PostConstruct singleton

**Already present (Tasks 1–4):**
- `services/event-management-service/src/main/resources/db/migration/V66__add_topic_selection_note.sql`
- `services/event-management-service/src/main/java/ch/batbern/events/service/BatbernCluster.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/BatbernTopicClusterService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/controller/TopicSimilarityController.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/TopicSimilarityService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/dto/TopicSimilarityRequest.java`
- `services/event-management-service/src/main/java/ch/batbern/events/dto/TopicSimilarityResponse.java`
- `services/event-management-service/src/main/java/ch/batbern/events/controller/TopicSessionDataController.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/TopicSessionDataService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/dto/TopicSessionDataResponse.java`
- `services/event-management-service/src/main/java/ch/batbern/events/client/PartnerApiClient.java`
- `services/event-management-service/src/main/java/ch/batbern/events/client/impl/PartnerApiClientImpl.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/TrendingTopicsService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/domain/Event.java`
- `services/event-management-service/src/main/java/ch/batbern/events/dto/EventResponse.java`
- `services/event-management-service/src/main/java/ch/batbern/events/dto/PatchEventRequest.java`
- `services/event-management-service/src/main/java/ch/batbern/events/mapper/EventMapper.java`
- `services/event-management-service/src/main/java/ch/batbern/events/controller/EventController.java`
- `services/event-management-service/src/main/resources/application.yml`
- `services/event-management-service/src/test/java/ch/batbern/events/controller/EventControllerIntegrationTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/controller/TopicSimilarityControllerIntegrationTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/controller/TopicSessionDataControllerIntegrationTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/service/BatbernTopicClusterServiceTest.java`
- `services/event-management-service/src/test/resources/application-test.properties`
