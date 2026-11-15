# Feature Spec: Blob Topic Selector

**Status:** Ready for implementation
**Origin:** Brainstorming session 2026-02-24 (Mary, Sally, John, Winston)
**Epic:** New feature — Topic Selection Enhancement

---

## Story

As an **organizer**,
I want a physics-based, interactive topic selection canvas,
so that during our team meeting I can intuitively feel how well a proposed topic aligns with partner interests and our event history — without reading a single number.

---

## Background & Context

The existing topic heatmap is static and not helpful during live organizer meetings. It shows historical categorization but gives no sense of:
- Which topics our partners are currently interested in
- How recently we covered similar territory
- What ideas are waiting in the backlog

This feature replaces the heatmap with a blob-physics canvas — inspired by browser games like agar.io — where three forces interact to make topic fit visceral and immediate.

The tool is used by **one driver sharing their screen** on a Teams call, with other organizers watching and discussing verbally.

---

## Acceptance Criteria

### Canvas & Physics
1. Canvas is an infinite dark-navy void, zoomable via scroll wheel / pinch. A "fit all" button and a "snap to active" button are always visible.
2. Physics simulation runs via D3 force simulation. Blobs collide, attract, and repel based on configured forces. Performance is smooth with up to 90 simultaneous blobs.
3. A general repulsion force keeps all blobs spread across the canvas (no stacking).

### Blue Blobs — Proposed Topics
4. Typing anywhere on the canvas opens a text input. Pressing `Enter` spawns a new blue blob that slides in from the right edge with natural momentum.
5. `Escape` cancels the input. `Ctrl+Z` removes the last blue blob.
6. Blue blobs display their topic name as a label inside the blob.

### Green Blobs — Partner Interests
7. On session load, one green blob per partner topic is placed in the canvas (up to 20, from existing partner coordination data).
8. Each green blob displays the partner company logo (minimum 32px). The logo remains visible when the blob is absorbed into a blue blob.
9. Green blobs drift toward the blue blob whose topic is most semantically similar. If equally similar to two blue blobs, the green blob oscillates between them (this is intentional — it communicates ambiguity).
10. When a green blob drifts close enough to a blue blob, it is visually absorbed — the logo floats inside the blue blob.

### Red Star Blobs — Past BATbern Events
11. On session load, all 57 past BATbern event blobs are loaded as dormant red star shapes (bumpy outline). They are displayed as a faint background constellation — not interactive at rest.
12. When a blue blob is summoned, red stars whose topic cluster matches the blue blob AND whose event number is within the last 6 events (≈ 2 years at 3 events/year) ignite: they become visible, glow, and apply a repulsion force to the blue blob.
13. The more recent the event, the stronger and brighter the repulsion. No57 (last event) should be maximally aggressive; No52 (6 events ago) is the boundary — neutral or barely active.
14. Red stars beyond the 6-event window remain in the background constellation — visible but not active.
15. Red stars display the event number and topic abbreviation as a label.

### Ghost Candidates
16. On session load, three types of ghost candidates are rendered as semi-transparent blobs drifting slowly in the void:
    - **Backlog ghosts** (neutral white) — from the organizer topic backlog (see data requirements)
    - **Partner ghosts** (faint green tint) — topics submitted by partners via the partner portal
    - **Trend ghosts** (faint gold shimmer) — 10 AI-fetched currently trending IT topics, fetched once at session start with a hardcoded fallback list
17. Clicking a ghost awakens it as a full blue blob (slides in, physics activates).
18. `Space` while hovering a ghost also awakens it.

### Manual Override (Drag)
19. Any blob can be dragged by the driver.
20. Holding a dragged blob close to another blob of the same or compatible type for 1.5 seconds shows a merge invitation (glow/halo effect).
21. Confirming the merge combines the blobs. This is session-only — not persisted to the backend.
22. Dragging a red star close to a blue blob does NOT merge them. Instead, the red star becomes an orbiting satellite around the blue blob — visually indicating "acknowledged warning." The driver explicitly chose to ignore the recency penalty.

### Accept Topic & Session Note
23. Double-clicking a blue blob opens a confirmation dialog: "Select [topic name] as the topic for [event name]?"
24. An optional free-text "override reason" field is shown if any red stars are currently orbiting the blob (i.e., a recency warning was acknowledged).
25. On confirmation, a session note is written to the event record (`topicSelectionNote` field) containing:
    - Selected topic name
    - Partner alignment: list of company names whose blobs were absorbed + count (e.g., "Swisscom, UBS (2/9 partners)")
    - Last covered: event number and count of events ago (e.g., "BATbern No54 — 4 events ago")
    - Override reason (if provided)
    - Competing candidates: names of other blue blobs present at time of decision
26. After confirmation, the event's topic field is updated and the user is redirected to the event detail page.

### Onboarding
27. First time an organizer opens the tool (per browser, localStorage flag), a 10-second animation plays: one ghost awakens → one green drifts → one red star ignites. No text. Auto-dismisses.

---

## Data Requirements

### Existing data (no changes needed)
- Partner topics — already stored in partner coordination feature
- Past event topics — already stored in event management (57 events, topic name + event number)
- Partner company names + logos — already in company management

### New data: Organizer Topic Backlog
- A simple list of topic strings, per-organization (not per-event)
- Add as a new tab/section to the existing partner coordination / organizer area
- CRUD: add topic string, delete, reorder (optional)
- Suggested location: extend the existing "Partner Topics" page with an "Organizer Backlog" section

---

## API Contract

### New Endpoints (Event Management Service)

#### GET `/api/v1/events/{eventCode}/topic-session-data`
Bootstrap call — returns everything the frontend needs to initialize the canvas.

**Response:**
```json
{
  "partnerTopics": [
    { "companyName": "Swisscom", "logoUrl": "...", "topics": ["AI in Operations", "Platform Engineering"] }
  ],
  "pastEvents": [
    { "eventNumber": 57, "eventCode": "BATbern57", "topicName": "Zero Trust", "cluster": "Security" }
  ],
  "organizerBacklog": ["Quantum Computing", "Green IT", "Observability"],
  "trendingTopics": ["AI Agents", "Platform Engineering", "FinOps", "Rust", "WebAssembly", "Cybersecurity Mesh", "Sovereign Cloud", "Digital Twin", "Edge AI", "Developer Experience"]
}
```

**Notes:**
- `trendingTopics` is fetched from an LLM (one call, cached for the session). If the LLM call fails, use the hardcoded fallback list above.
- `pastEvents` includes the pre-computed `cluster` field (see similarity section).

#### POST `/api/v1/events/{eventCode}/topic-similarity`
Called when a new blue blob is summoned (user typed a new topic).

**Request:**
```json
{ "topic": "LLM Observability" }
```

**Response:**
```json
{
  "cluster": "AI/ML",
  "similarityScore": 0.87,
  "relatedPastEventNumbers": [40, 44, 49, 56, 58]
}
```

**Notes:**
- For topics matching a known BATbern cluster (see taxonomy below), return the pre-computed cluster assignment (no API call needed, ~50ms).
- For novel topics, call OpenAI embeddings API, compare against cluster centroids, return result (~300–500ms).
- The frontend should spawn the blue blob immediately on Enter (before the API response). The similarity response updates force strengths when it arrives (~500ms later). This "thinking" delay is intentional — feels considered, not broken.

#### PATCH `/api/v1/events/{eventCode}` *(existing endpoint)*
Add `topicSelectionNote: String` field to the event update DTO.

### New Endpoints (Event Management Service — Organizer Backlog)

#### GET `/api/v1/topic-backlog`
Returns the organizer topic backlog (global, not per-event).

**Response:** `["Quantum Computing", "Green IT", "Observability"]`

#### POST `/api/v1/topic-backlog`
Add topic to backlog. Body: `{ "topic": "Green IT" }`

#### DELETE `/api/v1/topic-backlog/{topic}`
Remove topic from backlog.

---

## Semantic Similarity — Topic Taxonomy

Pre-computed cluster assignments for all 57 known BATbern events. New topics are matched against these cluster centroids:

| Cluster ID | Cluster Name | Event Numbers |
|---|---|---|
| `AI_ML` | AI / Machine Learning | 40, 44, 49, 56, 58 |
| `SECURITY` | Security & Cryptography | 16, 27, 38, 48, 57 |
| `ARCHITECTURE` | Software Architecture | 2, 3, 12, 13, 30, 41, 43, 55 |
| `DATA` | Data Management | 15, 18, 29, 33, 52, 53 |
| `CLOUD_INFRA` | Cloud & Infrastructure | 36, 39, 51, 54 |
| `MOBILE` | Mobile | 22, 26 |
| `BUSINESS_OTHER` | Business / Other | All remaining |

**Recency penalty rule:** A past event repels a same-cluster blue blob if `(currentEventNumber - pastEventNumber) <= 6`. Repulsion force = `1 - ((currentEventNumber - pastEventNumber) / 6)`. Linear fade to zero at 6 events distance.

---

## Frontend Component Design

### Route
`/events/:eventCode/topic-selector` — accessible to ORGANIZER role only

### Technology
- **Physics:** `d3-force` (v3) — nodes with variable radius, custom force functions
- **Canvas:** SVG rendered by D3, wrapped in a React component
- **Zoom/Pan:** `d3-zoom` — infinite canvas, `[0.1, 4]` scale range
- **Animations:** D3 transitions for blob spawning, ignition, absorption
- **State:** React component state only — no Redux/Zustand needed. Session-only override state.

### Component Structure
```
TopicSelectorPage
├── TopicCanvas (D3 SVG)
│   ├── GhostLayer (semi-transparent blobs)
│   ├── RedStarLayer (past events, dormant/active)
│   ├── GreenBlobLayer (partner interests)
│   ├── BlueBlobLayer (proposed topics)
│   └── TopicInput (floating text input)
├── CanvasControls (fit-all, snap-to-active buttons)
├── AcceptDialog (confirmation + session note preview)
└── OnboardingOverlay (first-visit animation, localStorage-gated)
```

### Performance Notes
- D3 force simulation tick runs at 60fps for ≤90 nodes. Reduce alpha decay if performance issues arise on older hardware.
- Logos loaded lazily; use company logo URLs already in BATbern (no new image infrastructure).
- SVG preferred over Canvas API for accessibility and ease of label rendering.

---

## Access Control
- Route is ORGANIZER-only (existing role-based routing)
- `GET /topic-session-data` — ORGANIZER role required
- `POST /topic-similarity` — ORGANIZER role required
- `GET/POST/DELETE /topic-backlog` — ORGANIZER role required
- `PATCH /events/{code}` — existing authorization (unchanged)

---

## Out of Scope

- Swiss IT conference external overlap signal (no data source)
- Cross-session override persistence (session-only is sufficient)
- Multi-driver real-time collaboration
- Tooltips (screen-share hostile)
- Mobile/tablet optimized layout (desktop Teams call use case only)
- Export / screenshot of the canvas (browser screenshot is sufficient)

---

## Implementation Notes

- The AI similarity endpoint should have a hardcoded fallback: if the embeddings API call fails, match by simple keyword against the cluster name strings. Better to have a slightly imprecise response than an error.
- The trending topics LLM prompt: *"List exactly 10 currently trending IT architecture and software engineering topics relevant to Swiss enterprise teams. Return a JSON array of short topic strings, max 4 words each."*
- The `topicSelectionNote` field on the event can be a plain `TEXT` column — no schema complexity needed.
- Organizer backlog is global (shared across all organizers), not per-event and not per-user.
- When the session bootstrap call is made, the backend should pre-compute which past events fall within the 6-event recency window for each cluster and return that information directly — avoiding the need for the frontend to do event-number arithmetic.
