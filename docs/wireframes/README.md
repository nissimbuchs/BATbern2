# BATbern Wireframes Documentation

## Overview

This directory contains all wireframe documentation for the BATbern platform, organized by user role and story.

**Status**: ✅ Complete - All wireframes have been sharded into story-specific files

> **⚠️ UPDATE (2025-10-01):** FR13 and FR16 have been removed from MVP scope.
> - `story-5.1-content-discovery.md` - Superseded (AI features removed)
> - `story-5.2-personal-dashboard.md` - Partial (remove community sections)
> - Story 7.4 and 7.5 wireframes - Not created (features removed)

---

## Organization Structure

### Index Files (High-Level)
These files provide overview, navigation structure, and links to detailed wireframes:

- `wireframes-organizer.md` - Organizer Portal Index
- `wireframes-speaker.md` - Speaker Portal Index
- `wireframes-attendee.md` - Attendee Portal Index
- `wireframes-partner.md` - Partner Portal Index
- `wireframes-coverage-report.md` - FR Coverage Analysis

### Detailed Wireframe Files (Story-Specific)
32 individual wireframe files, each containing complete ASCII wireframes for one screen:

#### Organizer Screens (8 files)
- `story-1.16-event-management-dashboard.md`
- `story-1.16-workflow-visualization.md`
- `story-2.2-topic-backlog-management.md`
- `story-3.1-speaker-matching-interface.md`
- `story-2.3-basic-publishing-engine.md`
- `story-4.3-progressive-publishing.md`
- `story-4.4-logistics-coordination.md`
- `story-1.20-notification-center.md`

#### Speaker Screens (8 files)
- `story-3.3-speaker-dashboard.md`
- `story-3.2-invitation-response.md`
- `story-3.3-material-submission-wizard.md`
- `story-7.1-speaker-profile-management.md`
- `story-3.5-event-timeline.md`
- `story-7.3-communication-hub.md`
- `story-3.3-presentation-upload.md`
- `story-7.1-speaker-community.md`

#### Attendee Screens (8 files)
- `story-2.4-current-event-landing.md`
- `story-5.1-content-discovery.md`
- `story-2.4-event-registration.md`
- `story-5.2-personal-dashboard.md` ⚠️ Contains FR16 features (to be removed)
- `story-1.18-historical-archive.md`
- ~~`story-7.4-community-features.md`~~ ❌ REMOVED (FR16)
- `story-5.3-mobile-pwa.md`
- `story-5.3-offline-content.md`

#### Partner Screens (8 files)
- `story-6.1-partner-analytics-dashboard.md`
- `story-6.1-employee-analytics.md`
- `story-6.2-brand-exposure.md`
- `story-6.4-topic-voting.md`
- `story-6.3-custom-report-builder.md`
- `story-6.4-strategic-planning.md`
- `story-6.5-partner-meetings.md`
- `story-6.3-budget-management.md`

---

## Naming Convention

Wireframe files follow this naming pattern:
```
story-{epic}.{story}-{feature-name}.md
```

Examples:
- `story-1.16-event-management-dashboard.md` → Epic 1, Story 16
- `story-3.2-invitation-response.md` → Epic 3, Story 2
- `story-6.1-partner-analytics-dashboard.md` → Epic 6, Story 1

---

## File Structure

Each story-specific wireframe file contains:

1. **Header**
   - Story number and title
   - User role
   - Related functional requirements

2. **Wireframe Content**
   - Complete ASCII art wireframe(s)
   - Screen states and variations

3. **Key Interactive Elements**
   - Buttons, controls, and interactions
   - Hover states and click behaviors

4. **Functional Requirements Met**
   - Specific FRs addressed by this screen

5. **User Interactions**
   - Step-by-step interaction flows
   - Expected user behaviors

6. **Technical Notes**
   - Implementation considerations
   - State management requirements
   - Performance considerations

---

## Usage Guidelines

### For Product Managers
- Start with index files for high-level understanding
- Review `wireframes-coverage-report.md` for FR coverage
- Use story-specific files for detailed feature discussions

### For Designers
- Story files contain detailed ASCII wireframes
- Each file is self-contained for independent review
- Technical notes guide UI/UX decisions

### For Developers
- Use story-specific files during sprint planning
- Files align with PRD stories in `docs/prd/`
- Technical notes provide implementation guidance

### For QA/Testing
- Each wireframe defines expected behaviors
- User interaction sections guide test case creation
- Functional requirements section links to test coverage

---

## Relationship to Other Documentation

### PRD Documents (`docs/prd/`)
- Wireframes implement screens described in PRD stories
- Story numbers match between wireframes and PRD
- Example: `story-3.2-invitation-response.md` implements Story 3.2 from `epic-3-speaker-management-stories.md`

### Epic Timeline
Wireframes are organized by epic for phased development:
- **Epic 1**: Foundation (Stories 1.x)
- **Epic 2**: Event Creation (Stories 2.x)
- **Epic 3**: Speaker Management (Stories 3.x)
- **Epic 4**: Event Finalization (Stories 4.x)
- **Epic 5**: Attendee Experience (Stories 5.x)
- **Epic 6**: Partner Analytics (Stories 6.x)
- **Epic 7**: Enhanced Features (Stories 7.x)

---

## Functional Requirements Coverage

All 21 functional requirements have corresponding wireframes:

✅ **FR1**: Role-Based Authentication (all portals)
✅ **FR2**: 16-Step Event Workflow (Story 1.16)
✅ **FR3**: Automated Speaker Workflows (Stories 3.x)
✅ **FR4**: Partner Analytics (Stories 6.1-6.2)
✅ **FR5**: Progressive Publishing (Stories 2.3, 4.3)
✅ **FR6**: Prominent Current Event (Story 2.4)
✅ **FR7**: Email Notifications (Story 1.20)
✅ **FR8**: Partner Topic Voting (Story 6.4)
✅ **FR9**: Automated Reports (Story 6.3)
✅ **FR10**: Speaker Self-Service (Stories 3.x)
✅ **FR11**: Event Archive (Story 1.18)
✅ **FR12**: Multi-Year Planning (Story 4.4)
✅ **FR13**: Content Discovery (Story 5.1)
✅ **FR14**: Personal Engagement (Story 5.2)
✅ **FR15**: Mobile PWA (Story 5.3)
✅ **FR16**: Community Features (Story 7.4)
✅ **FR17**: Speaker Matching (Story 3.1)
✅ **FR18**: Topic Backlog (Story 2.2)
✅ **FR19**: Publishing Engine (Stories 2.3, 4.3)
✅ **FR20**: Intelligent Notifications (Story 1.20)
✅ **FR21**: Long-Term Planning (Story 4.4)

See `wireframes-coverage-report.md` for detailed analysis.

---

## Change History

### 2025-01-XX: Sharding Complete
- Extracted all individual screens from consolidated files
- Created 32 story-specific wireframe files
- Updated index files to serve as navigation
- Maintained all general chapters and documentation
- Created progress tracking file (`.sharding-progress.md`)

### Previous
- Original consolidated wireframe files with all screens

---

## Progress Tracking

See `.sharding-progress.md` for detailed completion status of the sharding process.

---

## Questions or Issues?

For questions about:
- **Wireframe content**: Check the relevant story file
- **Coverage gaps**: See `wireframes-coverage-report.md`
- **Navigation**: Start with index files (wireframes-{role}.md)
- **Implementation**: Reference technical notes in story files