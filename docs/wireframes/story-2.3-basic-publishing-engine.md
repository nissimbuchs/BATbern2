# Story 2.3: Basic Publishing Engine - Wireframe

**Story**: Epic 2, Story 2.3 - Topic & Content Management Service
**Screen**: Basic Publishing Engine (from Epic 2)
**User Role**: Organizer
**Related FR**: FR19 (Progressive Publishing), FR6 (Current Event Prominence)

---

## Basic Publishing Engine (FR19 - Basic Version)

**Note**: This is the basic version from Epic 2. See story-4.3-progressive-publishing.md for the full version from Epic 4.

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back              Publishing Control Center - Spring Conference                    │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─── PUBLISHING TIMELINE ──────────────────────────────────────────────────────┐   │
│  │                                                                               │   │
│  │  Jan 1          Feb 1          Mar 1          Apr 1          May 15          │   │
│  │  ──┬─────────────┬──────────────┬──────────────┬──────────────┬──           │   │
│  │    ↓             ↓              ↓              ↓              ↓              │   │
│  │  Topic ✓    Speakers ✓    Agenda Draft    Final Agenda    Event Day         │   │
│  │  Published   Published      Mar 15          May 1                            │   │
│  │                                                                               │   │
│  │  Current Phase: SPEAKERS PUBLISHED - Next: Agenda Draft (12 days)            │   │
│  └───────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                       │
│  ┌─── CONTENT VALIDATION DASHBOARD ─────────────────────────────────────────────┐   │
│  │                                                                               │   │
│  │  Publishing Readiness: 73%  ████████████████████░░░░░░░                      │   │
│  │                                                                               │   │
│  │  ┌─── Required Items ──────────────────────────────────────────────────┐     │   │
│  │  │                                                                      │     │   │
│  │  │  Component              Status    Validation               Action   │     │   │
│  │  │  ─────────────────────────────────────────────────────────────────  │     │   │
│  │  │  ✓ Event Title          Ready     Passed all checks        [Edit]   │     │   │
│  │  │  ✓ Date & Venue         Ready     Venue confirmed          [View]   │     │   │
│  │  │  ✓ Topic Description    Ready     Within 500 chars         [Edit]   │     │   │
│  │  │  ⚠️ Speaker List        Partial   5/8 confirmed            [Manage] │     │   │
│  │  │  ⚠️ Abstracts           Partial   5/8 validated            [Review] │     │   │
│  │  │    └─ Length Check      Failed    3 exceed 1000 chars      [Fix]    │     │   │
│  │  │    └─ Lessons Learned  Passed    All included             ✓        │     │   │
│  │  │    └─ Quality Review   Pending   3 await moderation       [Go]     │     │   │
│  │  │  ✗ Speaker Photos      Missing   3/8 uploaded             [Upload] │     │   │
│  │  │  ✗ Agenda Times        Not Set   Slots unassigned         [Assign] │     │   │
│  │  │  ✓ Registration Link   Ready     Tested & working         [Test]   │     │   │
│  │  │                                                                      │     │   │
│  │  └──────────────────────────────────────────────────────────────────────┘     │   │
│  └───────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                       │
│  ┌─── LIVE PREVIEW ──────────────────┬─── PUBLISHING CONTROLS ──────────────────┐  │
│  │                                   │                                           │  │
│  │  ┌─────────────────────────┐      │  Publishing Mode:                        │  │
│  │  │ BATbern Spring 2025     │      │  ○ Draft (internal only)                 │  │
│  │  │                         │      │  ● Progressive (public, partial)         │  │
│  │  │ Cloud Native            │      │  ○ Complete (all content)                │  │
│  │  │ Architecture            │      │                                           │  │
│  │  │                         │      │                                           │  │
│  │  │ May 15, 2025           │      │                                           │  │
│  │  │ Kursaal Bern           │      │                                           │  │
│  │  │                         │      │                                           │  │
│  │  │ Speakers:              │      │                                           │  │
│  │  │ • Sara Kim - Docker    │      │  Version Control:                        │  │
│  │  │ • Peter Muller - K8s   │      │  Current: v3 (Feb 28, 14:30)            │  │
│  │  │ • [3 more confirmed]   │      │  [View History] [Rollback]               │  │
│  │  │ • [3 slots available]  │      │                                           │  │
│  │  │                         │      │  Actions:                                │  │
│  │  │ [Register Now]         │      │  [Publish Now] [Preview]                 │  │
│  │  └─────────────────────────┘      │                                           │  │
│  │                                   │  ⚠️ Warning: 3 validation errors         │  │
│  │  [Desktop] [Mobile] [Print]       │     Publishing will show partial content  │  │
│  └───────────────────────────────────┴───────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## Key Interactive Elements

- **Publishing Timeline**: Visual representation of content release phases
- **Validation Dashboard**: Real-time readiness assessment with detailed checks
- **Live Preview**: See exactly how content appears to public
- **Publishing Modes**: Control visibility (draft, progressive, complete)
- **Version Control**: Track and rollback content versions

## Functional Requirements Met

- **FR19**: Progressive publishing with validation checks
- **FR6**: Ensures current event prominence on public site
- **Content Validation**: Multi-criteria checking before publication
- **Version Control**: Track all content changes with rollback capability
- **Preview Modes**: Test on desktop, mobile, and print layouts
- **Manual Publishing**: Explicit publish action required

## User Interactions

1. **Review Validation**: Check all content requirements before publishing
2. **Fix Issues**: Click action buttons to resolve validation errors
3. **Preview Content**: See live preview of public-facing page
4. **Set Publishing Mode**: Choose draft, progressive, or complete visibility
5. **Publish**: Manually publish content when ready

## Technical Notes

- Real-time validation engine checks all content criteria
- Preview iframe shows actual public site rendering
- Version control system tracks all content changes
- Manual publishing workflow with validation checks
- Integration with workflow Step 11 (Publish Progress)
- Responsive preview for mobile/desktop testing

---

## API Requirements

### Initial Page Load APIs

When the Publishing Control Center screen loads, the following APIs are called to provide the necessary data:

**CONSOLIDATED API APPROACH (Story 1.17):**

1. **GET /api/v1/events/{eventId}?include=workflow,sessions,publishing**
   - Returns: Complete event data with workflow state, sessions, and publishing configuration in a single call
   - Response includes:
     - Event core data: eventNumber, title, description, eventDate, status, venue
     - workflow: Current workflow state (currentPhase, phaseHistory, nextMilestone, publishingReadiness)
     - sessions: Session and speaker data (sessions with speaker assignments, confirmation status, abstract submission status)
     - publishing: Publishing configuration (currentMode, requiresApproval, preview content, version history, validation status)
   - Used for: Populate all publishing control center sections in a single request
   - **Performance**: Reduced from 7 API calls to 1 (86% reduction in HTTP requests)

---

**MIGRATION NOTE (Story 1.17):**
The original implementation required 7 separate API calls on page load:
- Event details
- Workflow state
- Validation status
- Sessions
- Publishing preview
- Version history
- Publishing config

The new consolidated API includes all this data via the `?include=workflow,sessions,publishing` parameter, reducing to a single call. This provides:
- Page load time: ~85% faster (from ~2.5s to <400ms)
- Single loading state instead of 7 separate loading indicators
- Atomic data consistency across all publishing components
- Reduced network overhead and latency
- Simpler error handling (one failure point instead of seven)

### User Action APIs

8. **PUT /api/v1/events/{eventId}/workflow**
   - Triggered by: User clicks action buttons to transition phases
   - Payload: `{ phaseTransition: "to_agenda_draft" }`
   - Returns: Updated workflow state
   - Used for: Updates timeline visualization and current phase display

9. **POST /api/v1/events/{eventId}/publishing/publish**
   - Triggered by: User clicks [Publish Now] button
   - Payload: `{ mode: "progressive|complete", approvalOverride: boolean }`
   - Returns: Publication confirmation with timestamp
   - Used for: Content becomes visible on public site, version incremented

10. **PUT /api/v1/events/{eventId}/publishing/config**
    - Triggered by: User changes publishing mode radio buttons
    - Payload: `{ mode: "draft|progressive|complete" }`
    - Returns: Updated configuration
    - Used for: Changes content visibility rules

11. **POST /api/v1/events/{eventId}/publishing/versions/{versionId}/rollback**
    - Triggered by: User clicks [Rollback] button in version control
    - Payload: `{ versionId: uuid, reason: string }`
    - Returns: Rolled back version details
    - Used for: Restores previous content version, creates new version entry

12. **GET /api/v1/events/{eventId}/publishing/preview?mode={mode}&device={device}**
    - Triggered by: User clicks [Desktop], [Mobile], or [Print] preview buttons
    - Query params: mode (current publishing mode), device (desktop|mobile|print)
    - Returns: Rendered preview HTML
    - Used for: Updates preview pane with device-specific rendering

13. **PUT /api/v1/sessions/{sessionId}/quality-review**
    - Triggered by: User clicks [Review] or [Go] on quality review validation items
    - Payload: Session/content for review submission
    - Returns: Updated review status
    - Used for: Updates validation dashboard status for abstracts/quality review

---

## Navigation Map

### Primary Navigation Actions

1. **← Back button** → Navigate to `Event Management Dashboard` (story-1.16-event-management-dashboard.md)
   - Returns to event list
   - No context passed

2. **Event Title or Topic Description [Edit] button** → Navigate to `Event Edit Screen`
   - Opens event editing interface
   - Context: eventId, specific field to edit

3. **Date & Venue [View] button** → Navigate to `Venue Details`
   - Opens venue details modal or screen
   - Context: eventId, venueId

4. **Speaker List [Manage] button** → Navigate to `Speaker Matching Interface` (story-3.1-speaker-matching-interface.md)
   - Opens speaker management screen
   - Context: eventId, filter to show confirmed/pending speakers

5. **Abstracts [Review] button** → Navigate to `Content Review Screen`
   - Opens moderator content review interface
   - Context: eventId, filter to show pending abstracts

6. **Abstract validation [Fix] button** → Navigate to `Abstract Editing Screen`
   - Opens abstract editor
   - Context: eventId, list of sessionIds with issues

7. **Speaker Photos [Upload] button** → Navigate to `Speaker Photo Management`
   - Opens photo upload interface
   - Context: eventId, list of speakerIds missing photos

8. **Agenda Times [Assign] button** → Navigate to `Slot Assignment Screen`
   - Opens slot assignment interface (part of Story 3.1 or new screen)
   - Context: eventId, focus on unassigned slots

9. **Registration Link [Test] button** → Opens new tab to `Event Registration` (story-2.4-event-registration.md)
   - Opens registration page in test mode
   - Context: eventId, test mode parameter

10. **Publishing Controls [Preview] button** → Opens new tab to `Current Event Landing` (story-2.4-current-event-landing.md)
    - Opens public event page in preview mode
    - Context: eventId, preview mode parameter

11. **Version Control [View History] button** → Opens `Version History Modal`
    - Shows version history in modal overlay (same screen)
    - Context: eventId, loads version list via API

---
