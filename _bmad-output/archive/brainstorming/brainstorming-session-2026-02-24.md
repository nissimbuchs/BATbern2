---
stepsCompleted: [1, 2, 3]
inputDocuments: ['_bmad/bmm/data/project-context-template.md']
session_topic: 'Blob Physics Topic Selector — intuitive UI for BATbern topic selection'
session_goals: 'Design a physics-based, game-inspired visualization that makes choosing the next BATbern event topic feel intuitive and immediate — replacing the static heatmap'
selected_approach: 'Party Mode — Mary (Analyst) + Sally (UX Designer) + John (PM) + Winston (Architect)'
techniques_used: ['structured requirement elicitation', 'UX interaction design', 'gamification design', 'technical architecture review']
ideas_generated: ['blob-physics force simulation', 'three-force model', 'ghost candidate layer', 'AI trend fetching', 'D3 force simulation', 'infinite canvas', 'partner logo absorption', 'red star proximity activation', 'acknowledged orbit mechanic', 'session note artifact', 'pre-clustered taxonomy similarity', 'single-driver Teams call model']
context_file: '_bmad/bmm/data/project-context-template.md'
---

# Brainstorming Session Results

**Facilitator:** Nissim
**Date:** 2026-02-24
**Agents:** Mary (Analyst 📊), Sally (UX Designer 🎨), John (PM 📋), Winston (Architect 🏗️)

---

## Session Overview

**Topic:** Blob Physics Topic Selector — a game-inspired, force-simulation UI for BATbern topic selection
**Goals:** Replace the static topic heatmap with something intuitive, dynamic, and fast — making the organizers' topic selection meeting feel like discovery rather than data review

### Context

The BATbern organizers hold regular meetings (on Teams) to select the topic for the next event. The current heatmap visualization is static, doesn't show partner interest, and doesn't give an intuitive sense of how "fresh" or "aligned" a topic is. The team wanted something more visceral — inspired by the browser game Blob/agar.io — where topics, partner interests, and event history interact through physics rather than percentages.

---

## The Core Concept

A physics-based canvas where **three types of blobs** interact through attraction and repulsion forces, giving organizers an immediate, intuitive feel for how well any proposed topic fits the moment.

### Blob Types

| Blob | Color | Count | Role |
|---|---|---|---|
| **Proposed topic** | 🔵 Blue | ~5–10, user-created | The topic being evaluated. Summoned by typing. |
| **Partner interest** | 🟢 Green | Up to 20 (9 partners × 1–2 topics each) | Drift toward semantically similar blue blobs. Partner logo always visible, even when absorbed. |
| **Past BATbern events** | ⭐ Red (star-shaped, bumpy) | 57 events | Dormant background stars. Ignite and repel when a blue blob is within 6-event proximity (~2 years). The more recent, the stronger the repulsion. |
| **Candidate ghosts** | 👻 Semi-transparent | Variable | Latent topic ideas. Click to awaken as a blue blob. |

### The Three Forces

1. **Attraction** — Green partner blobs drift toward the blue blob whose topic is semantically similar (AI-determined)
2. **Repulsion** — Red event stars ignite and push back against blue blobs covering similar territory within the last 6 events (~2 years at 3 events/year)
3. **Latency** — Ghosts hover quietly until clicked; when awakened they trigger the full physics response

---

## Ghost Candidates — Three Origins

All ghosts look similar (semi-transparent, gently pulsing) but carry a subtle visual provenance hint:

| Ghost Type | Source | Visual hint |
|---|---|---|
| **Backlog ghost** | Organizer-maintained topic backlog (new feature: extend partner coordination page) | Neutral white |
| **Partner ghost** | Topics submitted/voted by partners | Faint green tint |
| **Trend ghost** | AI-fetched current IT trends (one LLM call at session start, cached) | Faint gold shimmer |

A trend ghost that, when awakened, immediately pulls 4 partner blobs toward it is one of the strongest possible signals — visible in seconds, no spreadsheet needed.

---

## Interaction Language — Three Verbs

### ① Summon
Type anywhere on the canvas → text input appears → `Enter` → blue blob slides in from the **right edge** with momentum, slows as it enters the field. Physics activates immediately. Green blobs begin drifting. Red stars within proximity ignite.

*Keyboard shortcuts for the driver:*
- Type anywhere → spawn input
- `Enter` → summon blob
- `Escape` → cancel
- `Ctrl+Z` → remove last blob
- `Space` while hovering ghost → awaken it

### ② Awaken
Click a ghost → it materializes from translucent to full blue. Triggers the full physics response. This is *discovery mode* — recognizing an idea rather than generating one.

### ③ Override (Manual Drag)
Drag any blob to force a merge or proximity:
- **Green + Blue held close for 1.5s** → merge invitation appears (glow/halo) → confirm → they merge, indicating "treat these as related"
- **Red star dragged close to blue** → does NOT merge → becomes an **orbiting satellite** (acknowledged warning). Visible asterisk that says "yes, we know we just did this."
- **Blue + Blue** → merge → combine topics into a broader single proposal

Override state is **session-only** — not persisted. Intentional, not accidental (1.5s hold prevents fat-finger merges).

### ④ Accept
Double-click a blue blob → confirmation dialog → topic selected → session note generated → session ends.

---

## Visual Design

**Aesthetic:** Dark navy void (not pure black). High contrast. Screen-share friendly at 720p.

**At rest (no blue blobs):**
- Ghosts drift very slowly, barely visible
- Red stars form a faint constellation in the background — dormant, beautiful
- Peaceful. A night sky.

**When a blue blob is summoned:**
- Slides in from right with momentum
- Green blobs in the same semantic cluster begin slow gravitational drift
- Red stars within 6-event radius *ignite* — glow brighter, move faster. More recent = more aggressive.
- The field feels like a *weather system* forming around your idea.

**Blob physics visual rules:**
- Partner logos minimum 32px — visible always, including when absorbed into a blue blob
- No tooltips (screen-share kills hover states — everything must be legible in the blob itself)
- When a blue blob absorbs green partner blobs, logos float visibly inside. This is the *political document* — "Swisscom and UBS are in this blob."
- Split attraction: if a green blob is pulled toward two blue blobs simultaneously, it oscillates between them. This is information — "this partner is equally happy with either topic."

**Canvas:** Infinite, zoomable (D3 zoom). "Fit all" button zooms out to show everything. "Snap to active" recenters on current blue blobs.

**Onboarding:** First-time-open: a 10-second animation showing one ghost awakening, one green drifting, one red star igniting. No text. Just show the grammar once, then disappear.

---

## Session Flow (Typical Teams Call)

1. Organizer opens tool for the upcoming event → canvas loads with ghosts and dormant red stars
2. Team discusses verbally. Driver types proposed topics → blue blobs slide in from the right
3. Greens drift, reds ignite (within 2-year window), field settles into a picture
4. Team discusses what they see. Driver drag-merges, overrides as needed
5. One blue blob emerges dominant — visually, by partner absorption and lack of red opposition
6. Driver double-clicks to accept → confirmation → session note generated → topic locked

---

## Session Artifact

Generated automatically on topic acceptance, attached to the event record as `topicSelectionNote`:

```
✅ Selected Topic: [topic name]
   Partner alignment: [Company A, Company B, Company C] ([N]/9 partners)
   Last covered: BATbern No[XX] ([N] events ago — [within caution zone / clear])
   Override reason: [optional free-text, entered at acceptance]
   Competing candidates considered: [other blue blobs present at time of decision]
```

---

## The BATbern Topic Taxonomy (58 Events)

The AI similarity engine is grounded in this actual history — not a general-purpose model:

| Cluster | Events |
|---|---|
| **AI / ML** | 40_Analytics_AI, 44_Chatbots, 49_AIMLOps, 56_GenAI, 58_AI_in_der_Software_Entwicklung |
| **Security** | 16_Informatik_Security, 27_Sicherheitsarchitektur, 38_Kryptografie, 48_ZeroTrust, 57_Zero_Trust |
| **Architecture** | 2_Enterprise_Architektur, 3_SOA, 12_Architekturplanung, 13_SOAreloaded, 30_Agile_Architektur, 41_Microservices_API, 43_EventDrivenArchitecture, 55_Agile_Architektur |
| **Data** | 15_BusinessIntelligence_DataWarehouse, 18_Datenhaltung, 29_Big_Data_Information_Management, 33_NoSQL_Databases, 52_Data_Mesh, 53_Persistenz |
| **Cloud / Infrastructure** | 36_Container, 39_Cloud, 51_Serverless, 54_Platform_Engineering |
| **Mobile** | 22_MobileCommunication, 26_Mobile_Applikationen |
| **Other / Business** | 1, 4–11, 14, 17, 19–21, 23–25, 28, 31–32, 34–35, 37, 42, 45–47, 50 |

Notable: **Zero Trust** appeared at No48 AND No57 — only 9 events apart. The red repulsion for any new Zero Trust proposal would be very strong and very visible.

---

## Key Decisions Made

| Decision | Choice | Rationale |
|---|---|---|
| External conference signal | ❌ Dropped (no data source) | Simplifies model; 3 forces are sufficient |
| Red blob density | Progressive reveal — only activate near blue blobs | Prevents visual noise; 57 blobs at once = chaos |
| Override persistence | Session-only | No backend needed; simplifies build |
| Session mode | Single driver, shared screen | Teams call reality; one keyboard, many watchers |
| AI similarity scope | Pre-clustered taxonomy + on-demand embeddings for new topics | Trustworthy, domain-specific, cheap |
| Trending topics freshness | Fetch once at session start, cached, with fallback | Not a moving target during discussion |
| Partner blob color | All same green; logos identify company | Visual simplicity + clear company attribution |
| Blob spawning direction | Always from right edge | Natural LTR reading culture; consistent UX |

---

## Out of Scope (Explicit)

- Swiss IT conference external overlap signal (no data source; revisit if a feed becomes available)
- Cross-session override persistence (keep it simple)
- Multi-driver real-time collaboration (single driver sufficient for now)
- Tooltips (screen-share hostile)
