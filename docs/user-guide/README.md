# BATbern Organizer Guide

> Comprehensive user documentation for event organizers managing Berner Architekten Treffen conferences

## Welcome

Welcome to the BATbern Organizer Guide. This documentation provides complete coverage of all features available to event organizers, from basic entity management to the 3 workflow systems (Event, Speaker, Task) for planning and executing successful architecture conferences in Bern, Switzerland.

**Target Audience**: Event organizers and coordinators responsible for planning, executing, and managing BATbern conferences.

**Platform Version**: Production Ready — Epics 1–6, 8, 10, 11, 12 Complete; Epic 7 (Attendee Experience) in review

## Platform Status

| Epic                                       | Status            | Key Features                                                                                          |
| ------------------------------------------ | ----------------- | ----------------------------------------------------------------------------------------------------- |
| **Epic 1: Foundation**                     | ✅ Complete        | Infrastructure, Authentication, API Gateway, Monitoring                                               |
| **Epic 2: Entity CRUD**                    | ✅ Complete        | Company, User, Event, Partner Management                                                               |
| **Epic 3: Data Migration**                 | ✅ Complete        | Historical data import tooling (production import pending)                                             |
| **Epic 4: Public Website**                 | ✅ Complete        | Landing pages, Registration flow, Archive Browsing, Content Search, SEO                               |
| **Epic 5: Organizer Workflows**            | ✅ Complete        | 9-state event workflow, Speaker workflow, Task system, Auto-publishing, Lifecycle automation          |
| **Epic 6: Speaker Portal**                 | ✅ Complete        | Cognito-authenticated invitations, self-service response/content submission, dashboard (WCAG 2.1 AA)  |
| **Epic 7: Attendee Experience**            | 🔨 In review       | Topics from the floor, speaker self-nomination, slides-online mail, thank-the-organizers, post-event Q&A, event history |
| **Epic 8: Partner Coordination**           | ✅ Complete        | Attendance analytics (XLSX export), topic voting, meeting coordination (ICS), partner notes, iCal RSVP |
| **Epic 9: Speaker Auth (original plan)**   | ⛔ Superseded      | Replaced by Epic 11 (unified workflow) + Epic 12 (SSO) — speaker auth is now Cognito-based             |
| **Epic 10: Admin, Newsletter & Public**    | ✅ Complete        | Administration page, newsletter system, registration lifecycle/waitlist, photo gallery, moderator page, Turnstile, legacy export/import |
| **Epic 11: Unified Speaker Workflow**      | ✅ Complete        | 8-state workflow (ADR-009), Cognito speaker auth (magic-link removed), kanban redesign, company self-service |
| **Epic 12: "Continue with Google" SSO**    | ✅ Complete        | Federated login with account linking, JIT provisioning, ToS consent, avatar import (Apple OIDC deferred) |
| **Epic 13: Spring Boot 4 Migration**       | 📋 Planned         | Staged per-module upgrade (SB 3.5 OSS support ends 2026-06-30)                                         |

## Quick Navigation

### 🚀 Getting Started

New to BATbern? Start here:

- 🎥 **Video Tutorials** - Complete workflow demonstration from event creation to archival
  - **[German Version (12 min)](/assets/user-guide/assets/videos/workflow/event-workflow-schulung-de.mp4)** - Full workflow with German narration
  - **[English Version (12 min)](/assets/user-guide/assets/videos/workflow/event-workflow-schulung-en.mp4)** - Full workflow with English narration
- [Platform Overview](getting-started/README.md) - Understand the BATbern platform
- [Login & Authentication](getting-started/login.md) - Access your organizer account
- [Dashboard Navigation](getting-started/dashboard.md) - Navigate the organizer interface
- [UI Conventions](getting-started/navigation.md) - Common patterns and shortcuts

### 📊 Entity Management

Manage core platform entities:

- [Companies](entity-management/companies.md) <span class="feature-status implemented">Implemented</span>
  - Swiss UID validation, logo upload, search/autocomplete
- [Users](entity-management/users.md) <span class="feature-status implemented">Implemented</span>
  - 4 roles (Organizer, Speaker, Attendee, Partner), GDPR compliance
- [Events](entity-management/events.md) <span class="feature-status implemented">Implemented</span>
  - Full-day, afternoon, evening formats, timeline management
- [Partners](entity-management/partners.md) <span class="feature-status implemented">Implemented</span>
  - Directory with tier badges, meeting coordination
- [Speakers](entity-management/speakers.md) <span class="feature-status implemented">Implemented</span>
  - Profile management, per-speaker workflow state tracking

### 🔄 Workflow System

Three independent workflow systems for event management:

**[Workflow Overview](workflow/README.md)** - Understanding the 3 workflow systems

**Phase A: Setup** <span class="feature-status implemented">Implemented</span>

- [Event Configuration](workflow/phase-a-setup.md) - Event creation, topic selection with heat map, speaker brainstorming
- Event states: CREATED → TOPIC_SELECTION → SPEAKER_IDENTIFICATION

**Phase B: Outreach** <span class="feature-status implemented">Implemented</span>

- [Speaker Engagement](workflow/phase-b-outreach.md) - Kanban board, outreach tracking, content collection
- Speaker states: identified → contacted → accepted → content_submitted

**Phase C: Quality** <span class="feature-status implemented">Implemented</span>

- [Content Review](workflow/phase-c-quality.md) - Quality review workflow, content approval
- Speaker states: content_submitted → quality_reviewed

**Phase D: Assignment & Publishing** <span class="feature-status implemented">Implemented</span>

- [Slot Assignment & Agenda Publishing](workflow/phase-d-assignment.md) - Auto-assign to slots, publish agenda
- Event states: SLOT_ASSIGNMENT → AGENDA_PUBLISHED
- Speaker states: quality_reviewed + session.startTime → confirmed

**Phase E: Archival** <span class="feature-status implemented">Implemented</span>

- [Event Archival](workflow/phase-e-publishing.md) - Archive completed events, preserve historical data
- Event state: Any state → ARCHIVED

**Phase F: Communication** <span class="feature-status implemented">Implemented</span>

- [Task Management](workflow/phase-f-communication.md) - Newsletters, moderation, catering (auto-created by event state transitions), auto-publishing, event lifecycle

### 🎤 Speaker & 🤝 Partner Portals

- [Speaker Portal](speaker-portal/README.md) <span class="feature-status implemented">Implemented</span> - Cognito-authenticated invitation/response, content submission, dashboard, company self-service
- [Partner Portal](partner-portal/README.md) <span class="feature-status implemented">Implemented</span> - Attendance analytics, topic voting, meeting coordination + RSVP tracking, partner notes

### 🙋 Attendee Experience <span class="feature-status implemented">Implemented</span>

Right-sized contribution & touchpoint features (Epic 7) — give attendees a voice and a return channel:

- [Overview](attendee-experience/README.md) - The contribution-loop model
- [Topics From the Floor](attendee-experience/topics-from-the-floor.md) - Attendee topic suggestions into the existing pool
- [Speaker Self-Nomination](attendee-experience/speaker-self-nomination.md) - "I Could Speak on That"
- [The "Slides Are Online" Mail](attendee-experience/slides-online-email.md) - Event-triggered post-event email
- [Thank-the-Organizers](attendee-experience/thank-the-organizers.md) - One-click appreciation
- [Post-Event Q&A](attendee-experience/post-event-qna.md) - Time-boxed per-session Q&A that freezes into the archive
- [Attendee Event History](attendee-experience/event-history.md) - Logged-in dashboard of past/registered events

### ⚙️ Administration

- [Administration Page](administration/README.md) <span class="feature-status implemented">Implemented</span> - Event types, data import/export, task templates, email-template management

### ✨ Advanced Features

Powerful tools for organizers:

- [Topic Heat Map](features/heat-maps.md) <span class="feature-status implemented">Implemented</span>
  - 20+ years historical visualization
- [Notification System](features/notifications.md) <span class="feature-status in-progress">In Progress</span>
  - Email notifications implemented (task reminders, speaker invites, reminders, calendar invites); in-app center planned
- [File Uploads](features/file-uploads.md) <span class="feature-status implemented">Implemented</span>
  - Presigned S3 URLs, validation
- [Analytics & Reporting](features/analytics.md) <span class="feature-status in-progress">In Progress</span>
  - Partner attendance analytics with XLSX export (Epic 8 complete); organizer event metrics planned

### 🔧 Troubleshooting

Common issues and solutions:

- [Authentication Issues](troubleshooting/authentication.md)
- [File Upload Errors](troubleshooting/uploads.md)
- [Workflow Blockers](troubleshooting/workflow.md)
- [Common Error Messages](troubleshooting/README.md)

### 📚 Reference

- [Glossary](appendix/glossary.md) - Platform terminology
- [Keyboard Shortcuts](appendix/keyboard-shortcuts.md) - Quick commands
- [Feature Status](appendix/feature-status.md) - Implementation roadmap
- [Changelog](appendix/changelog.md) - Version history

## Feature Status Legend

Throughout this documentation, you'll see feature status badges indicating implementation state:

- <span class="feature-status implemented">Implemented</span> - Working and available now
- <span class="feature-status in-progress">In Progress</span> - Partially complete (details in section)
- <span class="feature-status planned">Planned</span> - Designed but not yet built

## Documentation Conventions

### Icons

- 🚀 Getting Started
- 📊 Entity Management
- 🔄 Workflow Steps
- ✨ Advanced Features
- 🔧 Troubleshooting
- 📚 Reference

### Code Examples

```bash
# Authentication token retrieval
./scripts/auth/get-token.sh staging your-email@example.com
```

### Workflow Phases

Workflow documentation uses color-coded phases:

<div class="workflow-phase phase-a">
<strong>Phase A: Setup</strong> - Initial event configuration
</div>

<div class="workflow-phase phase-b">
<strong>Phase B: Outreach</strong> - Speaker identification and outreach
</div>

<div class="workflow-phase phase-c">
<strong>Phase C: Quality Control</strong> - Content review and validation
</div>

## Getting Help

- **Technical Issues**: GitHub Issues at [BATbern2 Repository](https://github.com/nissimbuchs/BATbern2/issues)
- **Security Concerns**: security@batbern.ch
- **General Questions**: info@batbern.ch

## Credits — Swiss German speech synthesis & subtitles

The training videos carry narration in German and English and subtitles in 10 languages, plus an
experimental **Bernese Swiss German (Bärndütsch)** narration track. The Swiss German work builds
on research and tools from the Swiss NLP community — with thanks to:

- **ETH Zürich — Media Technology Center, "SwissVoice"** — text-to-speech for Swiss German
  dialects (High German → dialect audio, 8 dialects incl. Bern):
  <https://mtc.ethz.ch/research/natural-language-processing/swiss-voice.html>
- **SwissDial** — annotated multi-dialect Swiss German corpus (ETH Zürich), paper:
  <https://arxiv.org/abs/2103.11401>
- **FHNW — Swiss German TTS demo (STT4SG)** — used to synthesize the Bärndütsch track:
  demo <https://stt4sg.fhnw.ch/tts/> · project
  <https://www.fhnw.ch/en/about-fhnw/schools/school-of-engineering/institutes/research-projects/speech-recognition-for-swiss-german>
- **STT4SG-350** — Swiss German speech corpus for all dialect regions (ZHAW · FHNW · UZH):
  <https://arxiv.org/abs/2305.18855>
- **ZHAW — "Text-to-Speech Pipeline for Swiss German: A comparison"** (Stucki, Deriu, Cieliebak):
  <https://arxiv.org/abs/2305.19750>
- **ZHAW — "Voice Adaptation for Swiss German"**: <https://arxiv.org/abs/2505.22054>
- **SwissDial-TTS** — open ESPnet model on HuggingFace (self-hostable):
  <https://huggingface.co/swordi/SwissDial-TTS>

German and English narration voices: **ElevenLabs** (<https://elevenlabs.io>).

> The Bärndütsch track is an experimental, research-grade proof of concept (16 kHz, machine
> translation) — not production quality. See the screencast pipeline README for details.

## Copyright

© 2025 Berner Architekten Treffen (BATbern). All rights reserved.

---

**Ready to get started?** Begin with [Platform Overview](getting-started/README.md) or jump directly to [Entity Management](entity-management/README.md).
