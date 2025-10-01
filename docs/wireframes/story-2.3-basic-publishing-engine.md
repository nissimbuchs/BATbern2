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
│  │  │                         │      │  Auto-publish rules:                     │  │
│  │  │ May 15, 2025           │      │  ☑ Publish when validation >= 80%        │  │
│  │  │ Kursaal Bern           │      │  ☑ Update hourly if changes              │  │
│  │  │                         │      │  ☐ Require manual approval               │  │
│  │  │ Speakers:              │      │                                           │  │
│  │  │ • Sara Kim - Docker    │      │  Version Control:                        │  │
│  │  │ • Peter Muller - K8s   │      │  Current: v3 (Feb 28, 14:30)            │  │
│  │  │ • [3 more confirmed]   │      │  [View History] [Rollback]               │  │
│  │  │ • [3 slots available]  │      │                                           │  │
│  │  │                         │      │  Actions:                                │  │
│  │  │ [Register Now]         │      │  [Publish Now] [Schedule] [Preview]      │  │
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
- **Auto-publish Rules**: Configure automated publishing logic
- **Version Control**: Track and rollback content versions

## Functional Requirements Met

- **FR19**: Progressive publishing with validation checks
- **FR6**: Ensures current event prominence on public site
- **Content Validation**: Multi-criteria checking before publication
- **Version Control**: Track all content changes with rollback capability
- **Preview Modes**: Test on desktop, mobile, and print layouts
- **Automated Publishing**: Rules-based content release

## User Interactions

1. **Review Validation**: Check all content requirements before publishing
2. **Fix Issues**: Click action buttons to resolve validation errors
3. **Preview Content**: See live preview of public-facing page
4. **Set Publishing Mode**: Choose draft, progressive, or complete visibility
5. **Configure Rules**: Set auto-publish thresholds and schedules
6. **Publish/Schedule**: Manually publish or schedule for future release

## Technical Notes

- Real-time validation engine checks all content criteria
- Preview iframe shows actual public site rendering
- Version control system tracks all content changes
- Automated publishing based on configurable rules
- Integration with workflow Step 11 (Publish Progress)
- Responsive preview for mobile/desktop testing

---

## API Requirements

### Initial Page Load APIs

When the Publishing Control Center screen loads, the following APIs are called to provide the necessary data:

1. **GET /api/v1/events/{eventId}**
   - Returns: Event details (eventNumber, title, description, eventDate, status, venue)
   - Used for: Display event identity in timeline and preview, calculate timeline milestones and publishing phases

2. **GET /api/v1/events/{eventId}/workflow**
   - Returns: Current workflow state (currentPhase, phaseHistory, nextMilestone, publishingReadiness)
   - Used for: Display current phase in timeline, show completed phases, calculate days until next phase, overall readiness percentage

3. **GET /api/v1/events/{eventId}/validation**
   - Returns: Content validation status (overallReadiness, validationItems with status, validationRules, actionUrl)
   - Used for: Display readiness percentage and progress bar, populate validation dashboard with component statuses and sub-validations

4. **GET /api/v1/events/{eventId}/sessions**
   - Returns: Session and speaker data (sessions with speaker assignments, confirmation status, abstract submission status)
   - Used for: Speaker List validation (5/8 confirmed display), Abstracts validation row

5. **GET /api/v1/events/{eventId}/publishing/preview**
   - Returns: Rendered preview content (htmlContent, publishedComponents, currentMode)
   - Used for: Render in preview iframe, show which sections are visible in current publishing mode

6. **GET /api/v1/events/{eventId}/publishing/versions**
   - Returns: Version history (currentVersion, versionHistory, publishedVersions)
   - Used for: Display current version number and timestamp, populate version history modal, enable rollback functionality

7. **GET /api/v1/events/{eventId}/publishing/config**
   - Returns: Publishing configuration (autoPublishEnabled, publishThreshold, updateFrequency, requiresApproval)
   - Used for: Set checkbox states for auto-publish rules, display percentage threshold, show update frequency setting

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

10. **POST /api/v1/events/{eventId}/publishing/schedule**
    - Triggered by: User clicks [Schedule] button
    - Payload: `{ scheduledTime: ISO8601, mode: "progressive|complete" }`
    - Returns: Scheduled publication details
    - Used for: Creates scheduled task for future publication

11. **PUT /api/v1/events/{eventId}/publishing/config**
    - Triggered by: User changes publishing mode radio buttons or auto-publish checkboxes
    - Payload: `{ mode: "draft|progressive|complete", autoPublishRules: {...} }`
    - Returns: Updated configuration
    - Used for: Changes content visibility rules and auto-publish behavior

12. **POST /api/v1/events/{eventId}/publishing/versions/{versionId}/rollback**
    - Triggered by: User clicks [Rollback] button in version control
    - Payload: `{ versionId: uuid, reason: string }`
    - Returns: Rolled back version details
    - Used for: Restores previous content version, creates new version entry

13. **GET /api/v1/events/{eventId}/publishing/preview?mode={mode}&device={device}**
    - Triggered by: User clicks [Desktop], [Mobile], or [Print] preview buttons
    - Query params: mode (current publishing mode), device (desktop|mobile|print)
    - Returns: Rendered preview HTML
    - Used for: Updates preview pane with device-specific rendering

14. **PUT /api/v1/sessions/{sessionId}/quality-review**
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
