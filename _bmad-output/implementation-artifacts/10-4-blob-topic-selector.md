# Story 10.4: Blob Topic Selector

Status: ready-for-dev

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

### Task 1: Backend — `topic_selection_note` field on Event (AC: 34)
- [ ] 1.1 Write failing integration test: PATCH event with `topicSelectionNote` field
- [ ] 1.2 Flyway `V64__add_topic_selection_note.sql`: `ALTER TABLE events ADD COLUMN topic_selection_note TEXT`
- [ ] 1.3 Add `topicSelectionNote` to `EventUpdateRequest` DTO, `Event` entity, `EventResponse` DTO
- [ ] 1.4 Verify existing PATCH event tests still pass (non-regression)

### Task 2: Backend — BatbernTopicClusterService (AC: 31, 33)
- [ ] 2.1 Write unit tests for cluster lookup for all 57 known event numbers
- [ ] 2.2 Create `BatbernTopicClusterService` in event-management-service with hardcoded cluster map (see Dev Notes)
- [ ] 2.3 Method `getCluster(int eventNumber) → ClusterEnum` for known events
- [ ] 2.4 Method `matchCluster(String topicText) → ClusterEnum` — exact/substring match first, then embedding API, then keyword fallback

### Task 3: Backend — Topic Similarity endpoint (AC: 32–33)
- [ ] 3.1 Write failing integration test for `POST /api/v1/events/{eventCode}/topic-similarity`
- [ ] 3.2 Create `TopicSimilarityController` + `TopicSimilarityService`
- [ ] 3.3 `TopicSimilarityRequest { topic: String }`, `TopicSimilarityResponse { cluster, similarityScore, relatedPastEventNumbers[] }`
- [ ] 3.4 Embed pre-computed cluster centroid text strings; use OpenAI embeddings for novel topics (cosine similarity)
- [ ] 3.5 Add to SecurityConfig: ORGANIZER role required. Add `permitAll` override in gateway.
- [ ] 3.6 Verify fallback to keyword matching when OpenAI unavailable

### Task 4: Backend — Topic Session Data endpoint (AC: 29–31)
- [ ] 4.1 Write failing integration test for `GET /api/v1/events/{eventCode}/topic-session-data`
- [ ] 4.2 Create `TopicSessionDataController` + `TopicSessionDataService`
- [ ] 4.3 Aggregate partner topics: cross-service call to partner-coordination-service (or shared DB read — check existing cross-service patterns in codebase)
- [ ] 4.4 Aggregate past events: query events table, map through `BatbernTopicClusterService` for cluster field
- [ ] 4.5 Aggregate organizer backlog: query existing `topics` table, `status = 'AVAILABLE'`, return `title[]` (max 50)
- [ ] 4.6 Create `TrendingTopicsService`: OpenAI Chat Completions API call (prompt in Dev Notes). Cache result 1 hour in-process (`ConcurrentHashMap` + timestamp). Hardcoded fallback list on error.
- [ ] 4.7 Add to SecurityConfig: ORGANIZER role required
- [ ] 4.8 Verify all integration tests pass

### Task 5: Frontend — Route + BlobTopicSelectorPage shell (AC: 3–4)
- [ ] 5.1 Create `web-frontend/src/pages/organizer/BlobTopicSelectorPage.tsx` — full-viewport container
- [ ] 5.2 Register route in `App.tsx`: `<Route path="/organizer/events/:eventCode/topic-blob" element={<ProtectedRoute><BlobTopicSelectorPage /></ProtectedRoute>} />`
- [ ] 5.3 Page renders WITHOUT `AuthLayout` (no sidebar, no nav). Use a bare `ProtectedRoute` wrapper only.
- [ ] 5.4 Fixed back button (top-left, `position: fixed, top: 16px, left: 16px, zIndex: 1000`): "← Back to Topic List". On click: show MUI `Dialog` "Unsaved session — all changes will be lost. Go back?" Confirm → `navigate(\`/organizer/topics?eventCode=${eventCode}\`)`. Cancel → close dialog.
- [ ] 5.5 Route reads `:eventCode` from `useParams()`.

### Task 6: Frontend — "Blob Selector" button in TopicManagementPage (AC: 1–2)
- [ ] 6.1 In `TopicManagementPage.tsx`, when `eventCode` is present, add a `<Button variant="outlined" startIcon={<BubbleChartIcon />} onClick={() => navigate(\`/organizer/events/${eventCode}/topic-blob\`)}` button
- [ ] 6.2 Place the button in the header area — next to (or above) the existing `TopicBacklogManager` component, clearly separated from the existing ToggleButtonGroup (which lives inside `TopicBacklogManager` — do NOT touch that component)
- [ ] 6.3 Add i18n key: `events:navigation.blobSelector` → "Blob Selector" (en) / "Blob-Auswahl" (de)

### Task 7: Frontend — D3 canvas setup + zoom (AC: 5–7)
- [ ] 7.1 Install D3: `npm install d3 @types/d3` (D3 v7, ES module, Vite handles it natively)
- [ ] 7.2 Create `web-frontend/src/components/BlobTopicSelector/` directory with: `BlobTopicSelector.tsx`, `useBlobSimulation.ts`, `useTopicSessionData.ts`, `types.ts`, `OnboardingOverlay.tsx`, `AcceptTopicDialog.tsx`
- [ ] 7.3 `BlobTopicSelector` renders full-viewport dark-navy SVG (`#0d1b2a`, `width: 100vw, height: 100vh`)
- [ ] 7.4 Initialize `d3.zoom()` on SVG, scale extent `[0.1, 4]`. Zoom/pan updates a `<g>` transform.
- [ ] 7.5 Initialize force simulation: `d3.forceSimulation()` + `forceManyBody(-30)` + `forceCollide(radius + 5)` + `forceCenter(width/2, height/2)`
- [ ] 7.6 "Fit All" button: zoom to bounding box of all active nodes. "Snap to Active" button: zoom to bounding box of blue blobs only.

### Task 8: Frontend — Session data hook + bootstrap (AC: 8–9)
- [ ] 8.1 Create `useTopicSessionData(eventCode)` hook calling `blobTopicService.getSessionData(eventCode)` via React Query (`queryKey: ['topicSessionData', eventCode]`, `staleTime: Infinity` — fetched once per session)
- [ ] 8.2 On data load: initialize ghost nodes for backlog + partner + trend topics; initialize red star nodes for past events; initialize green blobs for partner interest topics
- [ ] 8.3 Add all nodes to D3 simulation; position ghosts/reds spread across canvas using `d3.forceSimulation` initial positions

### Task 9: Frontend — Blue blob summon (AC: 10–12)
- [ ] 9.1 Global `keydown` on canvas container: any printable character opens floating `<input>` at viewport center
- [ ] 9.2 On `Enter`: add new blue blob node at right edge (`x: width + 50`), add initial velocity leftward. Fire async `POST /topic-similarity`. `Escape` → dismiss input.
- [ ] 9.3 `Ctrl+Z` listener: remove last blue blob from simulation nodes array + re-heat simulation
- [ ] 9.4 Similarity response → update `forceLink` strengths for all green nodes toward this blue blob; activate matching red stars (set `isActive = true`, update their force)

### Task 10: Frontend — Green blobs + partner logos (AC: 13–15)
- [ ] 10.1 Render green blobs as SVG `<circle>` (fill `#2e7d32`) with `<image>` logo centered inside (min 32px)
- [ ] 10.2 `forceLink` array: after similarity response, add/update links from each green blob to its closest blue blob. Link strength = `similarityScore`.
- [ ] 10.3 Absorption: in simulation tick, if `distance(green, blue) < green.r + blue.r - 10`, set `green.absorbed = true` (hidden), render logo inside blue blob `<g>`

### Task 11: Frontend — Red star blobs (AC: 16–18)
- [ ] 11.1 Render red stars as SVG `<polygon>` with star path (10-point star, bumpy outline), dormant opacity `0.15`
- [ ] 11.2 On blue blob similarity response, activate red stars: filter `node.eventNumber` is in `relatedPastEventNumbers` AND within 6 of `mostRecentEventNumber`. Set `isActive = true`.
- [ ] 11.3 Active red stars: `opacity → 1.0`, add SVG `<filter id="red-glow">` (feGaussianBlur + feColorMatrix red), custom repulsion `d3.forceRadial` pushing away from blue blob position. Render event number + topic short label.

### Task 12: Frontend — Ghost candidates (AC: 19–21)
- [ ] 12.1 Render ghosts as SVG `<circle>` with appropriate fill/opacity per type (see AC 19)
- [ ] 12.2 CSS `@keyframes blobPulse`: scale 0.95↔1.05, 3s infinite, applied to ghost group `<g>`
- [ ] 12.3 Click handler: remove ghost node, add blue blob at ghost's current `{x, y}` position. `Space` key while cursor within ghost radius → same.

### Task 13: Frontend — Drag, merge, orbit (AC: 22–25)
- [ ] 13.1 `d3.drag()` on all blob nodes: `dragstarted` → `node.fx = node.x; node.fy = node.y`. `dragged` → `node.fx = event.x; node.fy = event.y`. `dragended` → `node.fx = null; node.fy = null`.
- [ ] 13.2 Proximity timer: in simulation tick, for dragged node, if distance to compatible node < 30px for > 1.5s → set `showMergeHalo = true`. On drag end with halo: merge (remove smaller, increase larger radius, copy absorbed logos).
- [ ] 13.3 Red star dropped near blue blob: set `red.orbiting = blue.id`. In simulation tick, compute orbit angle (increment per tick), set `red.x/y` along orbit circle. Render dashed SVG orbit path.

### Task 14: Frontend — Accept dialog + session note (AC: 26–27)
- [ ] 14.1 `AcceptTopicDialog.tsx`: MUI Dialog, shows topic name, absorbed partner logos, last-covered text (from `relatedPastEventNumbers` + current event number), optional `TextField` for override reason (if `red.orbiting` nodes exist for this blob)
- [ ] 14.2 On confirm: call `blobTopicService.acceptTopic(eventCode, topicCode, note)` → `PATCH /api/v1/events/{eventCode}` with `{ topicCode, topicSelectionNote }`. On success: navigate to `/organizer/events/${eventCode}?tab=speakers`.
- [ ] 14.3 Add i18n keys under `organizer:blobSelector.*` (see Dev Notes)

### Task 15: Frontend — Onboarding overlay (AC: 28)
- [ ] 15.1 `OnboardingOverlay.tsx`: check `localStorage.getItem('batbern_blob_onboarding_seen')`. If absent, render a full-viewport overlay above the canvas.
- [ ] 15.2 Scripted D3 animation sequence (10s total): 0–2s ghost pulses → 2–4s ghost awakens (becomes blue) → 4–6s green blob drifts toward blue → 6–8s red star ignites and glows → 8–10s everything settles. No text.
- [ ] 15.3 On animation end: set `localStorage.setItem('batbern_blob_onboarding_seen', 'true')`, unmount overlay.

### Task 16: Frontend — Service layer additions (AC: all)
- [ ] 16.1 Create `web-frontend/src/services/blobTopicService.ts`:
  ```typescript
  export const blobTopicService = {
    getSessionData: (eventCode: string) =>
      apiClient.get<TopicSessionData>(`/api/v1/events/${eventCode}/topic-session-data`),
    getSimilarity: (eventCode: string, topic: string) =>
      apiClient.post<TopicSimilarityResponse>(`/api/v1/events/${eventCode}/topic-similarity`, { topic }),
    acceptTopic: (eventCode: string, topicCode: string, note: string) =>
      apiClient.patch(`/api/v1/events/${eventCode}`, { topicCode, topicSelectionNote: note }),
  };
  ```
- [ ] 16.2 Add TypeScript types: `TopicSessionData`, `TopicSimilarityResponse`, `BlobNode`, `GhostNode`, `RedStarNode` in `BlobTopicSelector/types.ts`

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

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

### File List
