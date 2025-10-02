# BATbern Organizer Portal - Wireframes Index

## Overview
This document serves as an index for all organizer interface wireframes, covering the complete 16-step workflow and intelligent features (FR17-FR21).

**Individual wireframe files have been separated by story for better organization and development workflow alignment.**

---

## Purpose

The organizer portal is the command center for event management, providing:
- **Event Pipeline Management**: Track multiple events through 16-step workflow
- **Intelligent Automation**: AI-powered suggestions and smart matching
- **Team Collaboration**: Real-time coordination and task management
- **Strategic Planning**: Multi-year venue booking and partner coordination
- **Content Quality**: Progressive publishing with validation gates

---

## Wireframe Files by Story

### Epic 1: Foundation & Infrastructure

**Story 1.16 - Event Management Service**
- `story-1.16-event-management-dashboard.md` - Main Organizer Dashboard
- `story-1.16-workflow-visualization.md` - 16-Step Workflow Visualization

**Story 1.20 - Notification Center**
- `story-1.20-notification-center.md` - Automated Notification Center

### Epic 2: Event Creation & Publishing

**Story 2.2 - Topic Selection System**
- `story-2.2-topic-backlog-management.md` - Smart Topic Backlog Management

**Story 2.3 - Basic Publishing**
- `story-2.3-basic-publishing-engine.md` - Basic Publishing Engine

### Epic 3: Speaker Management

**Story 3.1 - Speaker Invitation**
- `story-3.1-speaker-matching-interface.md` - Intelligent Speaker Matching

### Epic 4: Event Finalization

**Story 4.3 - Full Publishing**
- `story-4.3-progressive-publishing.md` - Full Progressive Publishing Engine

**Story 4.4 - Logistics Coordination**
- `story-4.4-logistics-coordination.md` - Multi-Year Planning & Logistics

---

## Key Features Covered

### FR2: 16-Step Event Workflow Management
Complete workflow visualization and management across all event phases.

### FR17: Intelligent Speaker Matching
AI-powered speaker-topic matching with workflow state tracking and overflow management.

### FR18: Smart Topic Backlog
Historical usage tracking, partner influence integration, and intelligent topic suggestions.

### FR19: Progressive Publishing Engine
Phased content publishing with automated validation and quality control.

### FR20: Intelligent Notifications
Context-aware alerts with role-based routing and escalation workflows.

### FR21: Long-Term Planning
Multi-year venue booking, partner meeting coordination, and strategic planning tools.

---

## User Roles & Permissions

### Lead Organizer
- Full access to all features
- Event creation and configuration
- Team member management
- Publishing controls

### Event Coordinator
- Event workflow management
- Speaker coordination
- Content review
- Limited publishing

### Content Moderator
- Abstract review and approval
- Quality control
- Content validation
- No publishing access

### Venue Coordinator
- Venue booking management
- Logistics coordination
- Catering management
- No content access

---

## Design Principles

1. **Task-Oriented Interface**: Actions prioritized by urgency and impact
2. **Progressive Disclosure**: Complex features revealed as needed
3. **Real-Time Collaboration**: Live updates and team activity feeds
4. **Intelligent Automation**: AI suggestions reduce manual work
5. **Workflow Clarity**: Visual progress tracking at all times

---

## Navigation Structure

```
Organizer Portal
├── Dashboard (Story 1.16)
│   ├── Active Events Pipeline
│   ├── Critical Tasks
│   ├── Team Activity
│   └── Performance Metrics
│
├── Event Management
│   ├── Workflow View (Story 1.16)
│   ├── Topic Backlog (Story 2.2)
│   └── Speaker Matching (Story 3.1)
│
├── Publishing
│   ├── Basic Publishing (Story 2.3)
│   └── Progressive Publishing (Story 4.3)
│
├── Planning
│   ├── Multi-Year Calendar (Story 4.4)
│   └── Logistics Coordination (Story 4.4)
│
└── Notifications (Story 1.20)
    ├── Alert Center
    └── Team Communication
```

---

## Technical Considerations

### Real-Time Updates
- WebSocket connections for live activity feeds
- Server-Sent Events for workflow notifications
- Optimistic UI updates for immediate feedback

### State Management
- Complex workflow state machines
- Multi-step form wizards
- Undo/redo capabilities

### Performance
- Virtual scrolling for large speaker lists
- Lazy loading for historical data
- Debounced search and filtering

### Accessibility
- Keyboard shortcuts for power users
- Screen reader support for all features
- High contrast mode for dashboards

---

## Related Documentation

- **PRD**: See `docs/prd/epic-1-foundation-stories.md` through `epic-4-event-finalization-stories.md`
- **User Stories**: Individual story files in PRD folder
- **Coverage Report**: `wireframes-coverage-report.md`

---

## Notes

All detailed wireframes have been extracted into individual story-specific files for:
- Better alignment with development workflow
- Easier version control and reviews
- Clearer traceability to user stories
- Independent updates per feature

Each wireframe file is self-contained with complete ASCII art, interaction descriptions, and technical notes.