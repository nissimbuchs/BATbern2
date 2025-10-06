# Story 3.1: Intelligent Speaker Matching Interface - Wireframe

**Story**: Epic 3, Story 3.1 - Speaker Management Service
**Screen**: Intelligent Speaker Matching Interface
**User Role**: Organizer
**Related FR**: FR17 (AI-Powered Speaker Matching)

---

## Intelligent Speaker Matching Interface (FR17)

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back        Speaker Management - Spring Conference 2025                            │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  Event: Cloud Native Architecture          Slots: 8          Confirmed: 5/8         │
│                                                                                       │
│  ┌─── ACCEPTED SPEAKERS ────────────────────┬─── SLOT ASSIGNMENT ─────────────────┐ │
│  │                                           │                                      │ │
│  │  ┌───────────────────────────────────┐   │  Morning (Technical)                │ │
│  │  │ Peter Muller                      │   │  ┌────────────────────────────┐     │ │
│  │  │ Kubernetes Expert • Swiss Re      │   │  │ 09:00 │ Sara Kim - Docker  │     │ │
│  │  │ [View Profile] [Contact]          │   │  ├────────────────────────────┤     │ │
│  │  └───────────────────────────────────┘   │  │ 09:45 │ Peter Muller - K8s │     │ │
│  │            ↓ Drag to assign →            │  ├────────────────────────────┤     │ │
│  │  ┌───────────────────────────────────┐   │  │ 10:30 │ [Empty - Drop here]│     │ │
│  │  │ Sara Kim                          │   │  ├────────────────────────────┤     │ │
│  │  │ Docker & Containers • ZKB         │   │  │ 11:15 │ [Empty - Drop here]│     │ │
│  │  │ [View Profile] [Contact]          │   │  └────────────────────────────┘     │ │
│  │  └───────────────────────────────────┘   │                                      │ │
│  │            ↓ Drag to assign →            │  Afternoon (Practical)              │ │
│  │  ┌───────────────────────────────────┐   │  ┌────────────────────────────┐     │ │
│  │  │ Marc Baum                         │   │  │ 13:30 │ [Empty - Drop here]│     │ │
│  │  │ Cloud Architecture • PostFinance  │   │  ├────────────────────────────┤     │ │
│  │  │ [View Profile] [Contact]          │   │  │ 14:15 │ [Empty - Drop here]│     │ │
│  │  └───────────────────────────────────┘   │  ├────────────────────────────┤     │ │
│  │            ↓ Drag to assign →            │  │ 15:00 │ [Empty - Drop here]│     │ │
│  │  ┌───────────────────────────────────┐   │  ├────────────────────────────┤     │ │
│  │  │ Thomas Weber                      │   │  │ 15:45 │ [Empty - Drop here]│     │ │
│  │  │ DevOps Lead • Swisscom            │   │  └────────────────────────────┘     │ │
│  │  │ [View Profile] [Contact]          │   │                                      │ │
│  │  └───────────────────────────────────┘   │  ← Drag speakers from left          │ │
│  │            ↓ Drag to assign →            │    to assign to time slots          │ │
│  │  ┌───────────────────────────────────┐   │                                      │ │
│  │  │ Anna Meyer                        │   │                                      │ │
│  │  │ Cloud Native • Credit Suisse      │   │                                      │ │
│  │  │ [View Profile] [Contact]          │   │                                      │ │
│  │  └───────────────────────────────────┘   │                                      │ │
│  │                                           │                                      │ │
│  └───────────────────────────────────────────┴──────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

## Key Interactive Elements

- **Accepted Speakers List**: Left panel showing all speakers who have accepted invitations
- **Drag-and-Drop Assignment**: Drag speaker cards from left panel to time slots on right
- **Slot Assignment Panel**: Right panel showing morning/afternoon agenda with drop zones
- **Speaker Actions**: View profile or contact speaker directly from speaker card

## Functional Requirements Met

- **Speaker Management**: Display accepted speakers for event assignment
- **Slot Assignment**: Interactive agenda planning with manual speaker assignment

## User Interactions

1. **View Accepted Speakers**: Browse list of speakers in left panel who have accepted invitations
2. **Drag Speaker Card**: Drag speaker from left panel to time slot on right
3. **Drop on Slot**: Drop speaker card into empty time slot to assign
4. **View Profile**: Click to view detailed speaker information and expertise
5. **Contact Speaker**: Click to send direct communication to speaker
6. **Remove Assignment**: Drag speaker out of slot back to left panel to unassign

## Technical Notes

- Drag-and-drop UI using HTML5 drag API or library like react-beautiful-dnd
- Integration with speaker profiles and speaker status data
- Slot assignment with conflict detection for double-booking prevention
- Real-time visual feedback during drag operations
- Optimistic UI updates for immediate feedback

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

3. **GET /api/v1/speakers?filter={"eventId":"{eventId}","status":"accepted"}&include=profile**
   - **Consolidated API**: Replaces `/api/v1/events/{eventId}/speakers/accepted`
   - Retrieve all speakers in accepted state for this event with profile data included
   - Response includes: speaker details, expertise, company, bio
   - Used for: Accepted speakers list display
   - **Consolidation benefit**: Single endpoint with field inclusion eliminates need for separate profile calls

---

## Action APIs

APIs called by user interactions and actions:

### Speaker Management

1. **GET /api/v1/speakers/{speakerId}?include=profile,events,availability**
   - **Consolidated API**: Replaces `/api/v1/speakers/{speakerId}/profile`
   - Triggered by: [View Profile] button in accepted speakers list
   - Response: Full speaker profile with history, expertise, availability, and events
   - Opens: Speaker profile modal/page
   - **Consolidation benefit**: Single call retrieves all profile data including availability and event history

2. **POST /api/v1/organizers/{organizerId}/messages/send**
   - Triggered by: [Contact] button for speaker
   - Payload: `{ recipientId, recipientType: "speaker", subject, message }`
   - Response: Message sent confirmation
   - Side effect: Email sent to speaker

### Slot Assignment

3. **POST /api/v1/events/{eventId}/slots/{slotId}/assign**
   - Triggered by: Drop speaker card on time slot (drag-and-drop)
   - Payload: `{ speakerId, sessionDetails }`
   - Response: Slot assignment created
   - Validation: Check conflicts, speaker availability

4. **DELETE /api/v1/events/{eventId}/slots/{slotId}/assign**
   - Triggered by: Drag speaker out of assigned slot
   - Response: Slot cleared, speaker returned to available list

---

## Navigation Map

Screen transitions triggered by actions and events:

### Primary Navigation

1. **← Back Button**
   - **Target**: Event Management Dashboard (Story 1.16)
   - **Context**: Returns to main event overview

2. **[View Profile]** (from accepted speakers list)
   - **Target**: Speaker Profile Page (Story 7.1)
   - **Type**: Modal or new page
   - **Context**: View full speaker details, history, expertise

3. **[Contact]** (from accepted speakers list)
   - **Target**: Message composer modal
   - **Type**: Modal overlay
   - **Context**: Send message to speaker

4. **Drag-and-Drop Speaker to Slot**
   - **Target**: No navigation, inline interaction
   - **Type**: Drag-and-drop operation
   - **Context**: Drag speaker card from left panel to time slot on right panel
   - **Feedback**: Visual drag preview, drop zone highlighting, success animation

### Event-Driven Navigation

5. **On Slot Assignment Complete**
   - **No Navigation**: Remains on current screen
   - **Feedback**: Success toast, visual update to agenda
   - **Update**: Slot assignment updated in display

6. **On All Slots Filled**
   - **Suggested Action**: Show success message with option to navigate to:
     - Event Timeline (Story 3.5) - Review full event agenda
     - Publishing Engine (Story 2.3) - Begin event promotion

### Error States & Redirects

7. **On Event Not Found**
   - **Target**: Event Management Dashboard (Story 1.16)
   - **Feedback**: Error message "Event not found"

8. **On Unauthorized Access**
   - **Target**: Login page or Permission Denied page
   - **Feedback**: "Organizer access required"

9. **On Slot Conflict Detected**
   - **No Navigation**: Remains on current screen
   - **Feedback**: Error modal showing conflict details with resolution options