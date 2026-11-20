# BATbern Watch - Documentation

All planning, design, and story artifacts for the BATbern Apple Watch companion app live here, **separate from the main platform documentation** in `docs/stories/` and `docs/architecture/`.

## Why Separate?

The main BATbern platform uses Epics 1-6 with story numbering like `6.3-content-submission.md`. The Watch app has its own 5-epic structure with a **W prefix** to avoid any collision:

| Namespace | Example | Location |
|---|---|---|
| Platform stories | `6.1a-magic-link-infrastructure.md` | `docs/stories/archived/`, `_bmad-output/implementation-artifacts/archive/` |
| Watch stories | `w1-2-session-card-browsing.md` | `_bmad-output/implementation-artifacts/archive/` |
| Platform architecture | `01-system-overview.md` | `docs/architecture/` |
| Watch architecture | `architecture.md` | `docs/watch-app/` |

## Document Index

### Planning Artifacts
| Document | Description | Status |
|---|---|---|
| [brainstorming-session.md](brainstorming-session.md) | Feature brainstorming results | Complete |
| [prd-batbern-watch.md](prd-batbern-watch.md) | **Product Requirements Document** (authoritative) | v1.0 |
| [architecture.md](architecture.md) | Architecture decisions (BMAD output, pre-public-view) | Needs update |
| [ux-design-specification.md](ux-design-specification.md) | UX design spec (BMAD output, pre-public-view) | Needs update |
| [ux-design-directions.html](ux-design-directions.html) | Visual design direction mockups | Complete |
| [epics.md](../archive/watch-app/epics.md) | Epic breakdown with requirements (BMAD output, pre-public-view) | Needs update |

### Stories

The watch app is delivered (epics W1 to W5). Its 20 story files are archived in
`_bmad-output/implementation-artifacts/archive/` as `w{epic}-{story}-*.md` (for example
`w2-1-pairing-code-backend-web-frontend.md`). There is no `docs/watch-app/stories/` folder.

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
