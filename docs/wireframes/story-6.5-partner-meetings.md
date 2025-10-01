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
│  ┌──── MEETING PREPARATION ──────────────────────────────────────────────────────────┐│
│  │                                                                                  ││
│  │  Your Talking Points                         Pre-Read Documents                 ││
│  │                                                                                  ││
│  │  1. Skills gap in Kubernetes/Cloud           • Q1 Analytics Report              ││
│  │  2. Request for Security workshops           • Topic Voting Summary             ││
│  │  3. Innovation lab proposal                  • Budget Projections               ││
│  │  4. Increase in employee engagement          • Success Stories                  ││
│  │                                                                                  ││
│  │  [Edit Talking Points]                       [Download All Documents]          ││
│  │                                                                                  ││
│  │  Discussion Topics from Others:                                                 ││
│  │  • Swiss Re: "AI/ML practical applications"                                    ││
│  │  • Swisscom: "5G edge computing opportunities"                                  ││
│  │  • Credit Suisse: "Quantum computing readiness"                                ││
│  └──────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                       │
│  ┌──── HISTORICAL MEETINGS ──────────────────────────────────────────────────────────┐│
│  │                                                                                  ││
│  │  Past Meetings & Outcomes                                                       ││
│  │                                                                                  ││
│  │  Q4 2024 - October 15, 2024                                                     ││
│  │  Key Decisions:                              Your Commitments:                  ││
│  │  • Approved 2025 budget: CHF 380K           • Sponsor 2 workshops ✓            ││
│  │  • Selected Q1 topics ✓                     • Provide 3 speakers ✓             ││
│  │  • Innovation program launched              • Increase attendance by 20% ⏳     ││
│  │                                                                                  ││
│  │  [View Meeting Minutes] [Download Presentation] [Action Items Status]           ││
│  └──────────────────────────────────────────────────────────────────────────────────┘│
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## API Requirements

### Initial Page Load APIs

When the Partner Meeting Hub screen loads, the following APIs are called to provide the necessary data:

1. **GET /api/v1/partners/{partnerId}/meetings/upcoming**
   - Query params: limit (5)
   - Returns: List of upcoming partner meetings with dates, times, locations, meeting types, attendance status
   - Used for: Populate upcoming partner meetings section

2. **GET /api/v1/partners/{partnerId}/meetings/{meetingId}/details**
   - Returns: Complete meeting details (agenda with times, participants with RSVP status, action items, materials), meeting host
   - Used for: Display detailed meeting information for the next upcoming meeting

3. **GET /api/v1/partners/{partnerId}/meetings/{meetingId}/action-items**
   - Returns: Action items assigned to partner with due dates, completion status, priorities
   - Used for: Display "Your Action Items" section

4. **GET /api/v1/partners/{partnerId}/meetings/{meetingId}/materials**
   - Returns: List of meeting materials with file names, types, sizes, upload dates
   - Used for: Display meeting materials section

5. **GET /api/v1/partners/{partnerId}/meetings/{meetingId}/talking-points**
   - Returns: Partner's saved talking points for the meeting
   - Used for: Populate "Your Talking Points" section

6. **GET /api/v1/partners/{partnerId}/meetings/{meetingId}/pre-read**
   - Returns: Required pre-read documents with descriptions, reading time estimates
   - Used for: Display "Pre-Read Documents" section

7. **GET /api/v1/meetings/{meetingId}/discussion-topics**
   - Returns: Discussion topics submitted by other partners (anonymized or attributed), topic descriptions
   - Used for: Display "Discussion Topics from Others" section

8. **GET /api/v1/partners/{partnerId}/meetings/historical**
   - Query params: limit (10), offset
   - Returns: Past meetings with dates, key decisions, partner commitments, completion status
   - Used for: Populate historical meetings section

---

## Action APIs

### Meeting RSVP & Attendance

1. **PUT /api/v1/partners/{partnerId}/meetings/{meetingId}/rsvp**
   - Payload: `{ status: "confirmed|declined|tentative", attendees: [{ name, email, role }], dietaryRestrictions, specialRequests }`
   - Response: Updated RSVP status, calendar invite
   - Used for: Confirm or update meeting attendance

2. **POST /api/v1/partners/{partnerId}/meetings/{meetingId}/calendar**
   - Response: iCal file URL for calendar integration
   - Used for: Add meeting to calendar

### Meeting Materials & Preparation

3. **GET /api/v1/partners/{partnerId}/meetings/{meetingId}/materials/{materialId}/download**
   - Returns: Download URL, expiration timestamp
   - Used for: Download individual meeting material

4. **POST /api/v1/partners/{partnerId}/meetings/{meetingId}/materials/download-all**
   - Response: Zip archive URL with all materials
   - Used for: Download all meeting materials as archive

5. **POST /api/v1/partners/{partnerId}/meetings/{meetingId}/materials/upload**
   - Payload: File upload (multipart/form-data)
   - Response: Material ID, upload confirmation
   - Used for: Upload additional materials to share with meeting participants

### Talking Points & Discussion Topics

6. **PUT /api/v1/partners/{partnerId}/meetings/{meetingId}/talking-points**
   - Payload: `{ talkingPoints: [{ text, priority, relatedAgendaItem }] }`
   - Response: Update confirmation, auto-save timestamp
   - Used for: Save or update talking points for meeting

7. **POST /api/v1/partners/{partnerId}/meetings/{meetingId}/questions/submit**
   - Payload: `{ question, relatedAgendaItem, anonymous: boolean }`
   - Response: Question submission confirmation, question ID
   - Used for: Submit questions in advance of meeting

8. **GET /api/v1/partners/{partnerId}/meetings/{meetingId}/questions**
   - Returns: List of submitted questions (own and from others if not anonymous), answers if available
   - Used for: View submitted questions and answers

### Agenda Management

9. **POST /api/v1/partners/{partnerId}/meetings/{meetingId}/agenda/propose**
   - Payload: `{ title, description, estimatedDuration, priority: "high|medium|low", supportingMaterials: [] }`
   - Response: Proposal ID, review status
   - Used for: Propose new agenda item for meeting

10. **GET /api/v1/meetings/{meetingId}/agenda/proposals**
    - Returns: All proposed agenda items from partners, voting/support status
    - Used for: View proposed agenda items from all partners

11. **POST /api/v1/partners/{partnerId}/meetings/{meetingId}/agenda/proposals/{proposalId}/support**
    - Response: Updated support count
    - Used for: Express support for proposed agenda item

### Action Items Management

12. **PUT /api/v1/partners/{partnerId}/meetings/{meetingId}/action-items/{actionItemId}/status**
    - Payload: `{ status: "completed|in-progress|blocked", notes, completionDate }`
    - Response: Updated action item status
    - Used for: Mark action item as complete or update status

13. **POST /api/v1/partners/{partnerId}/meetings/{meetingId}/action-items/{actionItemId}/evidence**
    - Payload: File upload or link to evidence of completion
    - Response: Evidence upload confirmation
    - Used for: Provide evidence of action item completion

### Historical Meetings & Minutes

14. **GET /api/v1/partners/{partnerId}/meetings/{meetingId}/minutes**
    - Returns: Meeting minutes document, key decisions, action items assigned, next steps
    - Used for: View meeting minutes

15. **GET /api/v1/partners/{partnerId}/meetings/{meetingId}/minutes/download**
    - Query params: format (pdf, docx)
    - Returns: Download URL for meeting minutes
    - Used for: Download meeting minutes

16. **GET /api/v1/partners/{partnerId}/meetings/{meetingId}/presentation**
    - Returns: Meeting presentation file URL
    - Used for: Download meeting presentation

17. **GET /api/v1/partners/{partnerId}/meetings/{meetingId}/action-items/status**
    - Returns: Status report of all action items from that meeting, completion rates, overdue items
    - Used for: View action items status report

### Meeting Scheduling & Requests

18. **POST /api/v1/partners/{partnerId}/meetings/request**
    - Payload: `{ type: "planning|review|strategic|ad-hoc", proposedDates: [], attendees: [], agenda, objectives }`
    - Response: Meeting request ID, review status
    - Used for: Request new partner meeting

19. **GET /api/v1/partners/{partnerId}/meetings/availability**
    - Query params: dateRange
    - Returns: Available meeting slots, participant availability, recommended dates
    - Used for: Check availability for meeting scheduling

### Calendar & Overview

20. **GET /api/v1/partners/{partnerId}/meetings/calendar**
    - Query params: year (2025), view (month, quarter, year)
    - Returns: Calendar view of all partner meetings, key dates, milestones
    - Used for: Navigate to calendar view of all meetings

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

14. **[Edit Talking Points] button** → Opens talking points editor
    - Text editor for each point
    - Priority selection
    - Link to agenda items
    - Auto-save functionality
    - No screen navigation

15. **Pre-read document click** → Opens document viewer
    - In-app document viewer
    - Download option
    - Mark as read tracking
    - Can navigate to full document

16. **[Download All Documents] button** → Triggers bulk download
    - Generates zip archive
    - Downloads all pre-read materials
    - No screen navigation

17. **Discussion topic click** → Navigate to `Topic Discussion Screen`
    - Full topic description
    - Submitted by information
    - Related materials
    - Comment/support options

18. **Historical meeting card click** → Navigate to `Past Meeting Details Screen`
    - Complete meeting recap
    - Decisions made
    - Action items with status
    - Materials archive

19. **[View Meeting Minutes] button** → Opens minutes viewer
    - Navigate to meeting minutes document
    - Searchable content
    - Download options
    - Related action items

20. **[Download Presentation] button** → Triggers presentation download
    - Downloads presentation file
    - Multiple format options if available
    - No screen navigation

21. **[Action Items Status] button** → Navigate to `Action Items Dashboard`
    - All action items from that meeting
    - Status tracking
    - Completion trends
    - Team accountability view

### Secondary Navigation (Data Interactions)

22. **RSVP status indicator** → Opens RSVP modification modal
    - Change attendance status
    - Update attendee list
    - Add special requests
    - No screen navigation after update

23. **Meeting location link** → Opens location details modal
    - Full address
    - Map integration
    - Parking/directions
    - Virtual meeting link if applicable
    - No screen navigation

24. **Commitment status indicator** → Opens commitment details modal
    - Full commitment description
    - Progress tracking
    - Evidence of completion
    - Extension request option
    - No screen navigation

### Event-Driven Navigation

25. **Meeting invitation received** → Shows notification
    - Meeting details preview
    - Quick RSVP options
    - Links to meeting details
    - No automatic navigation

26. **RSVP confirmed** → Shows confirmation notification
    - Calendar invite sent
    - Confirmation email sent
    - Action items reminder
    - No automatic navigation

27. **New agenda item proposed** → Shows notification
    - Proposed item preview
    - Support/vote option
    - Links to proposal details
    - No automatic navigation

28. **Question answered** → Shows notification
    - Answer preview
    - Links to full Q&A
    - Notification of who answered
    - No automatic navigation

29. **New material uploaded** → Shows notification
    - Material name and type
    - Uploaded by information
    - Download link
    - No automatic navigation

30. **Action item due date approaching** → Shows reminder notification
    - Days until due
    - Action item description
    - Quick status update option
    - Links to action item details

31. **Meeting materials available** → Shows notification
    - Materials ready for download
    - List of new materials
    - Download all option
    - No automatic navigation

32. **Meeting minutes published** → Shows notification
    - Minutes available
    - Key decisions highlighted
    - Action items assigned
    - Links to minutes

33. **Meeting reminder (24 hours before)** → Shows reminder notification
    - Meeting time and location
    - Action items status check
    - Talking points reminder
    - Links to meeting details

34. **Meeting starting soon (1 hour)** → Shows urgent reminder
    - Join virtual meeting link (if applicable)
    - Last-minute material access
    - Quick attendance confirmation
    - No automatic navigation

---
