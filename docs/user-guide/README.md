# BATbern Organizer Guide

> Comprehensive user documentation for event organizers managing Berner Architekten Treffen conferences

## Welcome

Welcome to the BATbern Organizer Guide. This documentation provides complete coverage of all features available to event organizers, from basic entity management to the sophisticated 16-step workflow for planning and executing successful architecture conferences in Bern, Switzerland.

**Target Audience**: Event organizers and coordinators responsible for planning, executing, and managing BATbern conferences.

**Platform Version**: Development (Epics 1-5)

## Platform Status

| Epic | Status | Progress | Key Features |
|------|--------|----------|--------------|
| **Epic 1: Foundation** | ✅ Complete | 100% | Infrastructure, Authentication, API Gateway, Monitoring |
| **Epic 2: Entity CRUD** | ✅ Complete | 100% | Company, User, Event, Partner Management |
| **Epic 3: Data Migration** | 🔄 In Progress | 33% | Historical data import |
| **Epic 4: Public Website** | 🔄 In Progress | ~85% | Landing pages, Registration flow |
| **Epic 5: Organizer Workflows** | 🔄 In Progress | 25% | 16-step event workflow (Phase A complete) |

## Quick Navigation

### 🚀 Getting Started
New to BATbern? Start here:
- [Platform Overview](getting-started/README.md) - Understand the BATbern platform
- [Login & Authentication](getting-started/login.md) - Access your organizer account
- [Dashboard Navigation](getting-started/dashboard.md) - Navigate the organizer interface
- [UI Conventions](getting-started/navigation.md) - Common patterns and shortcuts

### 📊 Entity Management
Manage core platform entities:
- [Companies](entity-management/companies.md) <span class="feature-status implemented">Implemented</span>
  - Swiss UID validation, logo upload, search/autocomplete
- [Users](entity-management/users.md) <span class="feature-status implemented">Implemented</span>
  - 4 roles (Admin, Organizer, Speaker, Attendee), GDPR compliance
- [Events](entity-management/events.md) <span class="feature-status implemented">Implemented</span>
  - Full-day, afternoon, evening formats, timeline management
- [Partners](entity-management/partners.md) <span class="feature-status implemented">Implemented</span>
  - Directory with tier badges, meeting coordination
- [Speakers](entity-management/speakers.md) <span class="feature-status in-progress">In Progress</span>
  - Profile management, status tracking

### 🔄 16-Step Workflow
Complete event lifecycle management:

**Phase A: Setup** <span class="feature-status implemented">Implemented</span>
- [Step 1-3: Event Setup](workflow/phase-a-setup.md) - Event type, topic selection with heat map, speaker brainstorming

**Phase B: Outreach** <span class="feature-status in-progress">In Progress</span>
- [Step 4-6: Speaker Outreach](workflow/phase-b-outreach.md) - Outreach tracking, status management, content collection

**Phase C: Quality Control** <span class="feature-status planned">Planned</span>
- [Step 7-8: Quality Review](workflow/phase-c-quality.md) - Review workflow, minimum threshold validation

**Phase D: Assignment** <span class="feature-status planned">Planned</span>
- [Step 9-10: Slot Assignment](workflow/phase-d-assignment.md) - Overflow voting, drag-and-drop assignment

**Phase E: Publishing** <span class="feature-status planned">Planned</span>
- [Step 11-12: Progressive Publishing](workflow/phase-e-publishing.md) - Topic → Speakers → Agenda, finalization

**Phase F: Communication** <span class="feature-status planned">Planned</span>
- [Step 13-16: Post-Event](workflow/phase-f-communication.md) - Newsletters, moderation, catering, archival

### ✨ Advanced Features
Powerful tools for organizers:
- [Topic Heat Map](features/heat-maps.md) <span class="feature-status implemented">Implemented</span>
  - 20+ years historical visualization
- [Notification System](features/notifications.md) <span class="feature-status planned">Planned</span>
  - In-app, email, escalation rules
- [File Uploads](features/file-uploads.md) <span class="feature-status implemented">Implemented</span>
  - Presigned S3 URLs, validation
- [Analytics & Reporting](features/analytics.md) <span class="feature-status planned">Planned</span>
  - Event metrics, speaker engagement

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
- **Security Concerns**: security@berner-architekten-treffen.ch
- **General Questions**: info@berner-architekten-treffen.ch

## Copyright

© 2025 Berner Architekten Treffen (BATbern). All rights reserved.

---

**Ready to get started?** Begin with [Platform Overview](getting-started/README.md) or jump directly to [Entity Management](entity-management/README.md).
