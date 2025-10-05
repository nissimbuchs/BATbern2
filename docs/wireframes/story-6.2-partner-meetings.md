# Story: Partner Meeting Coordination - Wireframe

**Story**: Epic 6, Story 5
**Screen**: Partner Meeting Coordination
**User Role**: Partner
**Related FR**: FR8 (Meetings)

---

## 7. Partner Meeting Coordination

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back                      Partner Meeting Hub                          [Calendar]  │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌──── UPCOMING PARTNER MEETINGS ──────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Q2 2025 Partner Planning Session                    April 15, 2025 • 14:00     │ │
│  │  Location: UBS Conference Center, Zurich            Your Status: ✓ Confirmed    │ │
│  │                                                                                  │ │
│  │  ┌────────────────────────────────────────────────────────────────────────────┐ │ │
│  │  │ AGENDA                                            PARTICIPANTS (12/15)      │ │ │
│  │  │                                                                             │ │ │
│  │  │ 14:00  Welcome & Q1 Review                       ✓ UBS (You)               │ │ │
│  │  │ 14:30  Topic Voting Results                      ✓ Swiss Re               │ │ │
│  │  │ 15:00  Budget Planning 2025/26                   ✓ Credit Suisse          │ │ │
│  │  │ 15:30  Coffee Break                              ✓ Swisscom               │ │ │
│  │  │ 15:45  Innovation Initiatives                    ✓ SBB                    │ │ │
│  │  │ 16:30  Event Calendar Review                     ✓ PostFinance            │ │ │
│  │  │ 17:00  Networking Apéro                          ? Zurich Insurance       │ │ │
│  │  │                                                   [View All]               │ │ │
│  │  └────────────────────────────────────────────────────────────────────────────┘ │ │
│  │                                                                                  │ │
│  │  Your Action Items:                          Meeting Materials:                 │ │
│  │  ☐ Submit topic votes (Due: Apr 10)         📎 Q1 Report.pdf                  │ │
│  │  ☐ Review budget proposal                   📎 2025 Calendar.xlsx             │ │
│  │  ☑ Confirm attendance                       📎 Topic Analysis.pptx            │ │
│  │                                                                                  │ │
│  │  [Add to Calendar] [View Materials] [Submit Questions] [Propose Agenda Item]    │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## API Requirements

### Initial Page Load APIs

**Note**: This wireframe has been updated to use the consolidated Partners API from Story 1.18 (109 → 20 endpoints, 82% reduction).

When the Partner Meeting Hub screen loads, the following APIs are called to provide the necessary data:

1. **GET /api/v1/partners/{partnerId}/meetings?filter={"upcoming":true}&page=1&limit=5**
   - Consolidates: Former `/meetings/upcoming` endpoint using standard filtering
   - Query params: filter (JSON filter for upcoming meetings), page, limit, sort (-scheduledDate)
   - Returns: List of upcoming partner meetings with dates, times, locations, meeting types, RSVP status
   - Used for: Populate upcoming partner meetings section
   - **Consolidation Benefit**: Standard filtering pattern consistent with other list endpoints

2. **GET /api/v1/partners/{partnerId}/meetings/{meetingId}?include=agenda,participants,materials,actionItems**
   - Consolidates: Former separate calls for meeting details, action items, and materials
   - Query params: include (comma-separated list of embedded resources)
   - Returns: Complete meeting entity including agenda with times, participants with RSVP status, materials list, action items assigned to partner, meeting host
   - Used for: Display all meeting information in single request
   - **Consolidation Benefit**: Include parameter replaces 3 separate API calls with single request

---

## Action APIs

### Meeting RSVP & Attendance

1. **PUT /api/v1/partners/{partnerId}/meetings/{meetingId}**
   - Consolidates: Former `/meetings/{meetingId}/rsvp` into standard update pattern
   - Payload: `{ rsvpStatus: "confirmed|declined|tentative", attendees: [{ name, email, role }], dietaryRestrictions, specialRequests }`
   - Response: Updated meeting with RSVP status, calendar invite generated
   - Used for: Confirm or update meeting attendance
   - **Consolidation Benefit**: Standard PUT for resource updates instead of specialized RSVP endpoint

2. **GET /api/v1/partners/{partnerId}/meetings/{meetingId}/calendar.ics**
   - Consolidates: Calendar export using resource representation with file extension
   - Response: iCal file content for calendar integration
   - Used for: Download meeting as calendar file
   - **Consolidation Benefit**: RESTful file representation pattern

### Meeting Materials & Preparation

3. **GET /api/v1/partners/{partnerId}/meetings/{meetingId}/materials/{materialId}**
   - Consolidates: Former `/materials/{materialId}/download` using content negotiation
   - Headers: `Accept: application/octet-stream` for download
   - Returns: File content with download headers OR material metadata (JSON)
   - Used for: Download or view individual meeting material
   - **Consolidation Benefit**: Single endpoint serves both metadata and file download via content negotiation

4. **POST /api/v1/partners/{partnerId}/meetings/{meetingId}/materials/export**
   - Consolidates: Former `/materials/download-all` using standard export pattern
   - Payload: `{ format: "zip", materialIds: [] }` (empty array = all materials)
   - Response: Export task ID, zip archive URL when ready
   - Used for: Download all or selected meeting materials as archive
   - **Consolidation Benefit**: Consistent export pattern across platform

5. **POST /api/v1/partners/{partnerId}/meetings/{meetingId}/materials**
   - Consolidates: Former `/materials/upload` using standard POST for creation
   - Payload: File upload (multipart/form-data) with metadata
   - Response: Created material ID, material object, upload confirmation
   - Used for: Upload additional materials to share with meeting participants
   - **Consolidation Benefit**: Standard RESTful POST pattern for creating materials

### Questions & Discussion

6. **POST /api/v1/partners/{partnerId}/meetings/{meetingId}/questions**
   - Consolidates: Simplified creation endpoint (removed `/submit` suffix)
   - Payload: `{ question, relatedAgendaItem, anonymous: boolean }`
   - Response: Created question ID, confirmation
   - **Consolidation Benefit**: Standard RESTful POST pattern

7. **GET /api/v1/partners/{partnerId}/meetings/{meetingId}/questions?filter={}**
   - Maintains standard list endpoint with filtering
   - Returns: List of submitted questions, answers if available
   - **Consolidation Benefit**: Standard filtering for own vs. all questions

### Agenda Management (Embedded in Meeting Resource)

8. **POST /api/v1/partners/{partnerId}/meetings/{meetingId}/agenda**
   - Consolidates: Former `/agenda/propose` simplified
   - Payload: `{ title, description, estimatedDuration, priority: "high|medium|low", supportingMaterials: [] }`
   - Response: Proposal ID, review status
   - **Consolidation Benefit**: Standard sub-resource creation pattern

9. **GET /api/v1/partners/{partnerId}/meetings/{meetingId}/agenda?status=proposed**
    - Consolidates: Former separate proposals endpoint
    - Returns: Agenda items filtered by status (proposed, approved, completed)
    - **Consolidation Benefit**: Standard filtering replaces specialized endpoint

10. **PATCH /api/v1/partners/{partnerId}/meetings/{meetingId}/agenda/{itemId}**
    - Consolidates: Support voting via PATCH operation
    - Payload: `{ supportedBy: ["partnerId"], supportCount: increment }`
    - **Consolidation Benefit**: Standard PATCH for partial updates

### Action Items (Already in Meeting Include)

11. **PATCH /api/v1/partners/{partnerId}/meetings/{meetingId}/action-items/{itemId}**
    - Consolidates: Status updates via PATCH
    - Payload: `{ status: "completed|in-progress|blocked", notes, completionDate, evidence: fileUrl }`
    - Response: Updated action item
    - **Consolidation Benefit**: Standard PATCH pattern; evidence included in same request

### Meeting Scheduling & Requests

12. **POST /api/v1/partners/{partnerId}/meetings**
    - Consolidates: Standard POST for creating meetings (includes requests)
    - Payload: `{ type: "planning|review|strategic|ad-hoc", proposedDates: [], attendees: [], agenda, objectives, requestStatus: "pending" }`
    - Response: Created meeting ID, review status
    - **Consolidation Benefit**: Single endpoint for both scheduled meetings and requests

13. **GET /api/v1/partners/{partnerId}/meetings?filter={"scheduledDate":{"$gte":"2025-05-01","$lte":"2025-05-31"}}**
    - Consolidates: Calendar functionality via date filtering
    - Query params: filter (date range), view metadata
    - Returns: Meetings within date range for calendar rendering
    - **Consolidation Benefit**: Standard filtering eliminates specialized calendar endpoint

---

## Navigation Map

### Primary Navigation Actions

1. **← Back button** → Navigate back to `Partner Analytics Dashboard`
   - Returns to main partner dashboard

2. **[Calendar] button** → Navigate to `Meeting Calendar View`
   - Full calendar interface
   - All partner meetings
   - Meeting types color-coded
   - Filter and search capabilities

3. **Meeting title click** → Navigate to `Meeting Details Screen`
   - Comprehensive meeting information
   - Full agenda with descriptions
   - All participants
   - Materials and resources
   - Historical context

4. **Agenda item click** → Opens agenda item details modal
   - Full description
   - Supporting materials
   - Presenter information
   - Expected outcomes
   - No screen navigation

5. **Participant name click** → Shows participant info tooltip
   - Partner company
   - Role/title
   - RSVP status
   - Contact information
   - No screen navigation

6. **[View All] participants button** → Opens participants list modal
   - Complete list of participants
   - RSVP statuses
   - Dietary restrictions (for organizer)
   - Contact details
   - No screen navigation

7. **Action item checkbox** → Toggles completion status
   - Marks item as complete/incomplete
   - Updates status via API
   - Shows completion timestamp
   - No screen navigation

8. **Action item click** → Opens action item details modal
   - Full description
   - Assignee information
   - Due date
   - Related materials
   - Evidence upload option
   - No screen navigation

9. **Material file click** → Triggers download
   - Downloads document
   - Opens in browser or saves locally
   - No screen navigation

10. **[Add to Calendar] button** → Triggers calendar export
    - Generates iCal file
    - Downloads calendar invite
    - Includes all meeting details
    - No screen navigation

11. **[View Materials] button** → Navigate to `Meeting Materials Library Screen`
    - All materials organized by topic
    - Preview capabilities
    - Batch download options
    - Upload interface

12. **[Submit Questions] button** → Opens question submission modal
    - Question form
    - Agenda item selector
    - Anonymous option
    - Preview submitted questions
    - No screen navigation after submit

13. **[Propose Agenda Item] button** → Navigate to `Agenda Proposal Screen`
    - Agenda item form
    - Duration estimation
    - Supporting materials upload
    - Priority selection

### Secondary Navigation (Data Interactions)

14. **RSVP status indicator** → Opens RSVP modification modal
    - Change attendance status
    - Update attendee list
    - Add special requests
    - No screen navigation after update

15. **Meeting location link** → Opens location details modal
    - Full address
    - Map integration
    - Parking/directions
    - Virtual meeting link if applicable
    - No screen navigation

### Event-Driven Navigation

16. **Meeting invitation received** → Shows notification
    - Meeting details preview
    - Quick RSVP options
    - Links to meeting details
    - No automatic navigation

17. **RSVP confirmed** → Shows confirmation notification
    - Calendar invite sent
    - Confirmation email sent
    - Action items reminder
    - No automatic navigation

18. **New agenda item proposed** → Shows notification
    - Proposed item preview
    - Support/vote option
    - Links to proposal details
    - No automatic navigation

19. **Question answered** → Shows notification
    - Answer preview
    - Links to full Q&A
    - Notification of who answered
    - No automatic navigation

20. **New material uploaded** → Shows notification
    - Material name and type
    - Uploaded by information
    - Download link
    - No automatic navigation

21. **Action item due date approaching** → Shows reminder notification
    - Days until due
    - Action item description
    - Quick status update option
    - Links to action item details

22. **Meeting materials available** → Shows notification
    - Materials ready for download
    - List of new materials
    - Download all option
    - No automatic navigation

23. **Meeting reminder (24 hours before)** → Shows reminder notification
    - Meeting time and location
    - Action items status check
    - Links to meeting details

24. **Meeting starting soon (1 hour)** → Shows urgent reminder
    - Join virtual meeting link (if applicable)
    - Last-minute material access
    - Quick attendance confirmation
    - No automatic navigation

---
