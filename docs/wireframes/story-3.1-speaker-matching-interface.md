# Story 3.1: Intelligent Speaker Matching Interface - Wireframe

**Story**: Epic 3, Story 3.1 - Speaker Management Service
**Screen**: Intelligent Speaker Matching Interface
**User Role**: Organizer
**Related FR**: FR17 (AI-Powered Speaker Matching)

---

## Intelligent Speaker Matching Interface (FR17)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back        Speaker Management - Spring Conference 2025              [Import CSV] │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  Event: Cloud Native Architecture          Slots: 8          Confirmed: 5/8         │
│                                                                                       │
│  ┌─── SPEAKER PIPELINE ──────────────────────────────────────────────────────────┐  │
│  │                                                                               │  │
│  │  Open     Contacted    Ready    Declined    Accepted    Assigned    Final     │  │
│  │   (12)       (8)        (3)       (2)         (5)         (5)        (0)      │  │
│  │                                                                               │  │
│  │  ┌─────┐   ┌─────┐   ┌─────┐   ┌─────┐    ┌─────┐    ┌─────┐    ┌─────┐    │  │
│  │  │John │   │Anna │   │Marc │   │Lisa │    │Peter│    │Peter│    │     │    │  │
│  │  │Smith│──▶│Wong │──▶│Baum │──▶│Chen │───▶│Mull │───▶│Slot2│───▶│     │    │  │
│  │  │Erik │   │Paul │   │Nina │   │Hans │    │Sara │    │Sara │    │     │    │  │
│  │  │+9...│   │+5...│   │     │   │     │    │Kim  │    │Slot1│    │     │    │  │
│  │  └─────┘   └─────┘   └─────┘   └─────┘    └─────┘    └─────┘    └─────┘    │  │
│  │                                                                               │  │
│  │  [Bulk Move →] [Send Reminder] [View Waitlist (3)]                          │  │
│  └───────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                       │
│  ┌─── SMART MATCHING ────────────────────┬─── SLOT ASSIGNMENT ─────────────────┐   │
│  │                                       │                                      │   │
│  │  For: Cloud Native Architecture       │  Morning (Technical)                │   │
│  │                                       │  ┌────────────────────────────┐     │   │
│  │  ┌── Best Matches ─────────────┐      │  │ 09:00 │ Sara Kim - Docker  │     │   │
│  │  │                              │      │  ├────────────────────────────┤     │   │
│  │  │ 95% Dr. Thomas Weber          │      │  │ 09:45 │ Peter Muller - K8s │     │   │
│  │  │     • 5 yrs K8s experience   │      │  ├────────────────────────────┤     │   │
│  │  │     • Swiss Re, Architecture │      │  │ 10:30 │ [Empty - Assign]   │     │   │
│  │  │     • Available ✓            │      │  ├────────────────────────────┤     │   │
│  │  │     [Invite] [View Profile]  │      │  │ 11:15 │ [Empty - Assign]   │     │   │
│  │  │                              │      │  └────────────────────────────┘     │   │
│  │  │ 89% Maria Santos             │      │                                      │   │
│  │  │     • CNCF Ambassador        │      │  Afternoon (Practical)              │   │
│  │  │     • 50+ conferences        │      │  ┌────────────────────────────┐     │   │
│  │  │     • Travel required ✈️     │      │  │ 13:30 │ [Empty - Assign]   │     │   │
│  │  │     [Invite] [View Profile]  │      │  ├────────────────────────────┤     │   │
│  │  │                              │      │  │ 14:15 │ [Empty - Assign]   │     │   │
│  │  │ 82% Local: Hans Gerber      │      │  ├────────────────────────────┤     │   │
│  │  │     • Swisscom, DevOps Lead │      │  │ 15:00 │ [Empty - Assign]   │     │   │
│  │  │     • First time speaker 🆕  │      │  ├────────────────────────────┤     │   │
│  │  │     [Invite] [View Profile]  │      │  │ 15:45 │ [Empty - Assign]   │     │   │
│  │  └──────────────────────────────┘      │  └────────────────────────────┘     │   │
│  │                                       │                                      │   │
│  │  Filter by:                           │  ⚠️ Overflow: 2 accepted speakers    │   │
│  │  □ Local speakers only                │     need slots                       │   │
│  │  ☑ Industry experience                │     [Start Voting] [Auto-Assign]    │   │
│  │  □ First-time speakers                │                                      │   │
│  └───────────────────────────────────────┴──────────────────────────────────────┘   │
│                                                                                       │
│  ┌─── TECHNICAL REQUIREMENTS ────────┬─── TEAM COLLABORATION ──────────────────┐   │
│  │                                   │                                          │   │
│  │ Speaker          Requirements     │  @sally: Thomas Weber looks perfect!    │   │
│  │ Sara Kim         ✓ HDMI, ✓ Mic   │  @mark: Agreed, sending invite now      │   │
│  │ Peter Muller     ✓ All standard   │  @anna: Maria needs travel budget       │   │
│  │ Marc Baum        ⚠️ Mac adapter    │  @system: 2 speakers auto-reminded      │   │
│  │                  ⚠️ Remote option  │                                          │   │
│  └───────────────────────────────────┴──────────────────────────────────────────┘   │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## Key Interactive Elements

- **Speaker Pipeline**: Kanban-style view of speaker progression
- **Match Scoring**: AI-calculated match percentage with explanation
- **Drag-and-Drop**: Move speakers between pipeline stages
- **Slot Assignment**: Visual agenda with drag-to-assign capability
- **Smart Matching**: ML-powered speaker recommendations
- **Team Chat**: Real-time collaboration on speaker selection

## Functional Requirements Met

- **FR17**: AI-powered intelligent speaker matching based on topic, experience, availability
- **Pipeline Management**: Visual tracking of speaker recruitment process
- **Slot Assignment**: Interactive agenda planning with conflict detection
- **Technical Requirements**: Track and manage speaker needs
- **Team Collaboration**: Real-time communication on speaker decisions
- **Waitlist Management**: Handle overflow speakers

## User Interactions

1. **View Matches**: See AI-ranked speaker recommendations for topic
2. **Invite Speaker**: Send invitation with one click
3. **Drag Speaker**: Move through pipeline or assign to agenda slot
4. **Filter Matches**: Apply criteria (local, experience, first-time)
5. **Check Availability**: See speaker calendar and constraints
6. **Collaborate**: Discuss speaker selection with team in real-time

## Technical Notes

- ML matching algorithm considers topic expertise, speaking experience, availability, location
- Real-time WebSocket updates for team collaboration
- Integration with speaker profiles and historical data
- Automated email sequences for speaker outreach (FR20)
- Calendar integration for availability checking
- Conflict detection for double-booking prevention

---

## API Requirements

APIs needed to load and display data for this screen:

### Initial Page Load

1. **GET /api/v1/events/{eventId}**
   - Retrieve event details (title, topic, date, status)
   - Used for: Header display showing event name and basic info

2. **GET /api/v1/events/{eventId}/slots**
   - Retrieve all event slots with type and assignment status
   - Query params: None (get all slots)
   - Used for: Slot assignment panel showing morning/afternoon agenda

3. **GET /api/v1/events/{eventId}/speakers/pipeline**
   - Retrieve all speakers associated with event grouped by pipeline stage
   - Response includes: speaker details, stage, count per stage
   - Used for: Speaker pipeline Kanban visualization

4. **GET /api/v1/events/{eventId}/speakers/matches**
   - Retrieve AI-matched speaker recommendations for event topic
   - Query params: `limit`, optional filters (local, firstTime, industry)
   - Response includes: match score, expertise, availability, profile highlights
   - Used for: Smart matching panel with ranked recommendations

5. **GET /api/v1/events/{eventId}/waitlist**
   - Retrieve waitlisted speakers (overflow)
   - Used for: Overflow warning banner count

6. **GET /api/v1/events/{eventId}/speakers/requirements**
   - Retrieve technical requirements for all assigned speakers
   - Used for: Technical requirements panel

7. **GET /api/v1/events/{eventId}/collaboration/messages**
   - Retrieve recent team collaboration messages
   - Query params: `limit=50`
   - Used for: Team collaboration chat panel

### WebSocket Connections

8. **WS /api/v1/events/{eventId}/collaboration**
   - Real-time collaboration updates
   - Events: new messages, speaker moves, slot assignments
   - Used for: Live updates across all panels

---

## Action APIs

APIs called by user interactions and actions:

### Speaker Invitation & Management

1. **POST /api/v1/speakers/invite**
   - Triggered by: [Invite] button in smart matching panel
   - Payload: `{ eventId, speakerId, sessionDetails }`
   - Response: Invitation created, moves speaker to "Contacted" stage
   - Side effect: Triggers email via AWS SES (FR20)

2. **PUT /api/v1/events/{eventId}/speakers/{speakerId}/stage**
   - Triggered by: Drag-and-drop speaker between pipeline stages
   - Payload: `{ stage: "contacted|ready|declined|accepted|assigned|final" }`
   - Response: Updated speaker pipeline position

3. **GET /api/v1/speakers/{speakerId}/profile**
   - Triggered by: [View Profile] button in matches or pipeline
   - Response: Full speaker profile with history, expertise, availability
   - Opens: Speaker profile modal/page

### Slot Assignment

4. **POST /api/v1/events/{eventId}/slots/{slotId}/assign**
   - Triggered by: Drag speaker to empty slot or [Auto-Assign] button
   - Payload: `{ speakerId, sessionDetails }`
   - Response: Slot assignment created, speaker moved to "Assigned" stage
   - Validation: Check conflicts, speaker availability

5. **DELETE /api/v1/events/{eventId}/slots/{slotId}/assign**
   - Triggered by: Remove speaker from assigned slot
   - Response: Slot cleared, speaker moved back to "Accepted" stage

### Bulk Operations

6. **POST /api/v1/events/{eventId}/speakers/bulk-move**
   - Triggered by: [Bulk Move →] button with selected speakers
   - Payload: `{ speakerIds: [], targetStage }`
   - Response: Multiple speakers moved to new stage

7. **POST /api/v1/events/{eventId}/speakers/send-reminders**
   - Triggered by: [Send Reminder] button
   - Payload: `{ speakerIds: [], stage: "contacted" }`
   - Response: Reminder emails sent via AWS SES
   - Side effect: Updates last_contacted timestamp

### Filtering & Search

8. **GET /api/v1/events/{eventId}/speakers/matches**
   - Triggered by: Filter checkboxes (local, industry, first-time)
   - Query params: `localOnly=true`, `industryExperience=true`, `firstTime=true`
   - Response: Filtered and re-ranked speaker matches

### Waitlist & Overflow

9. **GET /api/v1/events/{eventId}/waitlist**
   - Triggered by: [View Waitlist (3)] button
   - Response: Full waitlist with speaker details
   - Opens: Waitlist modal/panel

10. **POST /api/v1/events/{eventId}/overflow/vote**
    - Triggered by: [Start Voting] button for overflow speakers
    - Opens: Voting interface for team to select speakers
    - Related: POST /api/v1/events/{eventId}/overflow with vote payload

### Import & Export

11. **POST /api/v1/events/{eventId}/speakers/import**
    - Triggered by: [Import CSV] button
    - Payload: CSV file with speaker data
    - Response: Bulk speaker creation/invitation

### Team Collaboration

12. **POST /api/v1/events/{eventId}/collaboration/messages**
    - Triggered by: Sending message in team chat
    - Payload: `{ message, userId, timestamp }`
    - Response: Message created
    - Broadcasts: Via WebSocket to all connected organizers

---

## Navigation Map

Screen transitions triggered by actions and events:

### Primary Navigation

1. **← Back Button**
   - **Target**: Event Management Dashboard (Story 1.16)
   - **Context**: Returns to main event overview

2. **[View Profile]** (from Smart Matching or Pipeline)
   - **Target**: Speaker Profile Page (Story 7.1)
   - **Type**: Modal or new page
   - **Context**: View full speaker details, history, expertise

3. **[Import CSV]**
   - **Target**: CSV Import Modal/Wizard
   - **Type**: Modal overlay
   - **Context**: Bulk speaker import workflow

### Secondary Navigation

4. **[View Waitlist (3)]**
   - **Target**: Waitlist Management Panel (inline expansion or modal)
   - **Type**: Modal or panel expansion
   - **Context**: Manage overflow speakers, see waitlist details

5. **[Start Voting]**
   - **Target**: Overflow Voting Interface (Story 6.4 - Topic Voting pattern)
   - **Type**: Modal or dedicated page
   - **Context**: Team voting on speaker selection for overflow

6. **Speaker Card Click** (in pipeline)
   - **Target**: Speaker Details Panel (inline or modal)
   - **Type**: Side panel or modal
   - **Context**: Quick view of speaker info, requirements, communications

### Event-Driven Navigation

7. **On Speaker Invite Sent**
   - **No Navigation**: Remains on current screen
   - **Feedback**: Success toast, speaker moved to "Contacted" stage
   - **Update**: Real-time WebSocket update to pipeline

8. **On Slot Assignment Complete**
   - **No Navigation**: Remains on current screen
   - **Feedback**: Success toast, visual update to agenda and pipeline
   - **Update**: Speaker moved to "Assigned" stage

9. **On All Slots Filled**
   - **Suggested Action**: Show success message with option to navigate to:
     - Event Timeline (Story 3.5) - Review full event agenda
     - Publishing Engine (Story 2.3) - Begin event promotion

10. **On Import Complete**
    - **No Navigation**: Remains on current screen
    - **Feedback**: Success message with import summary
    - **Update**: Pipeline refreshed with new speakers

### Error States & Redirects

11. **On Event Not Found**
    - **Target**: Event Management Dashboard (Story 1.16)
    - **Feedback**: Error message "Event not found"

12. **On Unauthorized Access**
    - **Target**: Login page or Permission Denied page
    - **Feedback**: "Organizer access required"

13. **On Slot Conflict Detected**
    - **No Navigation**: Remains on current screen
    - **Feedback**: Error modal showing conflict details with resolution options

### Cross-Feature Navigation

14. **Technical Requirements Link** (speaker row)
    - **Target**: Logistics Coordination (Story 4.4)
    - **Type**: Modal or dedicated page
    - **Context**: Manage speaker technical requirements and logistics

15. **Team Chat User @mention**
    - **Target**: Communication Hub (Story 7.3) or User Profile
    - **Type**: Inline action or modal
    - **Context**: Direct communication with team member