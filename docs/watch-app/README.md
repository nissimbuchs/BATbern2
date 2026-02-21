# BATbern Watch - Documentation

All planning, design, and story artifacts for the BATbern Apple Watch companion app live here, **separate from the main platform documentation** in `docs/stories/` and `docs/architecture/`.

## Why Separate?

The main BATbern platform uses Epics 1-6 with story numbering like `6.3-content-submission.md`. The Watch app has its own 5-epic structure with a **W prefix** to avoid any collision:

| Namespace | Example | Location |
|---|---|---|
| Platform stories | `6.1a-magic-link-infrastructure.md` | `docs/stories/` |
| Watch stories | `W1.2-event-hero-screen.md` | `docs/watch-app/stories/` |
| Platform architecture | `01-system-overview.md` | `docs/architecture/` |
| Watch architecture | `architecture.md` | `docs/watch-app/` |

## Document Index

### Planning Artifacts
| Document | Description | Status |
|---|---|---|
| [product-brief.md](product-brief.md) | Initial product brief from brainstorming | Complete |
| [brainstorming-session.md](brainstorming-session.md) | Feature brainstorming results | Complete |
| [prd-batbern-watch.md](prd-batbern-watch.md) | **Product Requirements Document** (authoritative) | v1.0 |
| [architecture.md](architecture.md) | Architecture decisions (BMAD output, pre-public-view) | Needs update |
| [ux-design-specification.md](ux-design-specification.md) | UX design spec (BMAD output, pre-public-view) | Needs update |
| [ux-design-directions.html](ux-design-directions.html) | Visual design direction mockups | Complete |
| [epics.md](epics.md) | Epic breakdown with requirements (BMAD output, pre-public-view) | Needs update |

### Stories (W-prefixed)
Stories live in `stories/` and follow the naming convention `W{epic}.{story}-{description}.md`.

**Watch Epic 1: Public Event Companion**
- Stories TBD (Xcode setup, hero screen, session browsing, abstract/bio views, etc.)

**Watch Epic 2: Watch Pairing & Organizer Access**
- Stories TBD (pairing code flow, web UI, auto-auth, pre-event overview)

**Watch Epic 3: Live Countdown & Haptic Awareness**
- Stories TBD (countdown timer, haptic patterns, overtime tracking, etc.)

**Watch Epic 4: Session Control & Team Sync**
- Stories TBD (session controls, WebSocket sync, conflict resolution)

**Watch Epic 5: Offline Resilience**
- Stories TBD (SwiftData cache, local timers, sync recovery)

## Code Location

Watch app source code lives in `apps/BATbern-watch/` — a standalone Xcode/Swift project, completely independent of the Gradle/Java/TypeScript build system.

Backend changes required by Watch epics (pairing endpoints in Epic 2, WebSocket in Epic 4) are implemented in the existing platform services and referenced from Watch stories.

## Relationship to Main Platform

```
┌─────────────────────────────────────────────────┐
│  BATbern Monorepo                               │
│                                                   │
│  ┌──────────────────┐  ┌──────────────────────┐ │
│  │  PLATFORM         │  │  WATCH APP            │ │
│  │                    │  │                        │ │
│  │  Epics 1-6        │  │  Epics W1-W5           │ │
│  │  docs/stories/    │  │  docs/watch-app/       │ │
│  │  docs/architecture│  │  apps/BATbern-watch/   │ │
│  │  services/        │  │                        │ │
│  │  web-frontend/    │  │  Uses existing public  │ │
│  │  api-gateway/     │  │  API endpoints         │ │
│  │                    │  │                        │ │
│  └──────────────────┘  └──────────────────────┘ │
│           │                       │               │
│           └───── Shared ──────────┘               │
│           - Public API endpoints (no new work)    │
│           - Pairing endpoints (new, in services/) │
│           - WebSocket channel (new, in services/) │
│           - Web frontend pairing UI (in web-fe/)  │
└─────────────────────────────────────────────────┘
```

## Notes

- The **authoritative PRD** is `prd-batbern-watch.md` (v1.0, includes public companion + organizer views)
- Earlier BMAD artifacts (`architecture.md`, `ux-design-specification.md`, `epics.md`) were created before the public companion view was added — they need updating to reflect the 5-epic structure
- The originals remain in `_bmad-output/planning-artifacts/` for BMAD workflow tracking
