# Story 3.1: Invitation Management Screen - Wireframe

**Story**: Epic 3, Story 3.1 - Speaker Invitation System / 3.2 - Invitation Response Workflow
**Screen**: Invitation Management (Organizer View)
**User Role**: Organizer
**Related FR**: FR17 (AI-Powered Speaker Matching), FR19 (Multi-Organizer Coordination)

**API Consolidation**: Updated to use consolidated APIs from Stories 1.17 (Events), 1.19 (Speakers), and 1.27 (Invitations)
**API Count**: 10 endpoints (reduced from 14 original calls)

---

## Visual Wireframe

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│ ← Back to Event    Invitation Management - Spring Conference 2025   [+ Invite]  │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                   │
│  ┌──── INVITATION OVERVIEW ─────────────────────────────────────────────────────┐  │
│  │                                                                              │  │
│  │  📊 Status Summary:  📧 Sent (15)  📖 Opened (12)  ✅ Accepted (8)          │  │
│  │                     ❌ Declined (2)  ⏳ Pending (5)  ⏱️ Response Rate: 67%   │  │
│  │                                                                              │  │
│  │  ⚠️ Action Required: 2 invitations expiring in <24 hours                    │  │
│  │                                                                              │  │
│  └──────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                   │
│  ┌──── FILTERS & ACTIONS ────────────────────────────────────────────────────────┐ │
│  │                                                                              │  │
│  │  Status: [All Statuses ⏷]   Sent By: [All Organizers ⏷]   [Search...]      │  │
│  │                                                                              │  │
│  │  Quick Filters:  [⏳ Pending (5)]  [⏱️ Expiring Soon (2)]  [❌ Declined (2)] │  │
│  │                                                                              │  │
│  │  [ Select All ]  [ Resend Selected ]  [ Cancel Selected ]  [ Export CSV ]   │  │
│  │                                                                              │  │
│  └──────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                   │
│  ┌──── INVITATION LIST ──────────────────────────────────────────────────────────┐  │
│  │                                                                              │  │
│  │  Sort by: [Response Deadline ⏷]  View: [List ☰] [Grid ▦]                   │  │
│  │                                                                              │  │
│  │  ┌────────────────────────────────────────────────────────────────────────┐ │  │
│  │  │  ☑  ✅ ACCEPTED    Responded 2 days ago                                │ │  │
│  │  │                                                                         │ │  │
│  │  │  👤 Peter Muller (TechCorp AG)                                         │ │  │
│  │  │  📧 peter.muller@techcorp.ch • 🏢 Gold Partner                         │ │  │
│  │  │                                                                         │ │  │
│  │  │  Session: "Kubernetes at Scale: Production Best Practices"             │ │  │
│  │  │  Topic: Cloud Native Architecture • Slot: 14:00-14:45 (Afternoon)      │ │  │
│  │  │                                                                         │ │  │
│  │  │  Invited: Apr 1, 2025 by Anna Lopez                                    │ │  │
│  │  │  Opened: Apr 1, 2025 (12:34)  •  Accepted: Apr 2, 2025 (09:15)        │ │  │
│  │  │  Response Time: 21 hours                                               │ │  │
│  │  │                                                                         │ │  │
│  │  │  Preferences: Afternoon slot ✓, Mac adapter needed, No travel required │ │  │
│  │  │  Comments: "Happy to speak! Can bring live demo if needed."            │ │  │
│  │  │                                                                         │ │  │
│  │  │  [View Response] [Send Message] [View Profile] [Assign Slot]           │ │  │
│  │  └─────────────────────────────────────────────────────────────────────────┘ │  │
│  │                                                                              │  │
│  │  ┌────────────────────────────────────────────────────────────────────────┐ │  │
│  │  │  ☑  ⏳ PENDING     📖 Opened 3 hours ago  🔴 Expires in 18 hours       │ │  │
│  │  │                                                                         │ │  │
│  │  │  👤 Maria Schmidt (TechFlow GmbH)                                      │ │  │
│  │  │  📧 m.schmidt@techflow.ch • 🏢 Silver Partner                          │ │  │
│  │  │                                                                         │ │  │
│  │  │  Session: "GitOps Implementation Patterns"                              │ │  │
│  │  │  Topic: Cloud Native Architecture • Slot: TBD                          │ │  │
│  │  │                                                                         │ │  │
│  │  │  Invited: Apr 3, 2025 by Thomas Weber                                  │ │  │
│  │  │  Opened: Apr 4, 2025 (14:22)  •  Status: Reading invitation           │ │  │
│  │  │  Deadline: Apr 5, 2025 (09:00)  ⚠️                                     │ │  │
│  │  │                                                                         │ │  │
│  │  │  Email Tracking: Opened 3 times (last: 3 hours ago)                   │ │  │
│  │  │                                                                         │ │  │
│  │  │  [Send Reminder] [Send Message] [View Profile] [Extend Deadline]       │ │  │
│  │  └─────────────────────────────────────────────────────────────────────────┘ │  │
│  │                                                                              │  │
│  │  ┌────────────────────────────────────────────────────────────────────────┐ │  │
│  │  │  ☑  ⏳ PENDING     📧 Sent 5 days ago  🟡 Expires in 2 days            │ │  │
│  │  │                                                                         │ │  │
│  │  │  👤 Thomas Klein (StartupHub Bern)                                     │ │  │
│  │  │  📧 t.klein@startuphub.ch • 🏢 Bronze Partner                          │ │  │
│  │  │                                                                         │ │  │
│  │  │  Session: "Container Security Best Practices"                           │ │  │
│  │  │  Topic: Cloud Security • Slot: TBD                                     │ │  │
│  │  │                                                                         │ │  │
│  │  │  Invited: Mar 30, 2025 by Anna Lopez                                   │ │  │
│  │  │  Email Status: Not yet opened ⚠️                                       │ │  │
│  │  │  Deadline: Apr 7, 2025 (17:00)                                         │ │  │
│  │  │                                                                         │ │  │
│  │  │  Last Reminded: Apr 2, 2025 (2 days ago)                               │ │  │
│  │  │                                                                         │ │  │
│  │  │  [Send Reminder] [Call Speaker] [View Profile] [Cancel Invitation]     │ │  │
│  │  └─────────────────────────────────────────────────────────────────────────┘ │  │
│  │                                                                              │  │
│  │  ┌────────────────────────────────────────────────────────────────────────┐ │  │
│  │  │  ☑  ❌ DECLINED    Responded 1 week ago                                │ │  │
│  │  │                                                                         │ │  │
│  │  │  👤 David Bauer (CloudTech Solutions)                                  │ │  │
│  │  │  📧 d.bauer@cloudtech.ch                                               │ │  │
│  │  │                                                                         │ │  │
│  │  │  Session: "Service Mesh Architecture"                                   │ │  │
│  │  │  Topic: Cloud Native Architecture • Slot: 11:00-11:45 (Morning)        │ │  │
│  │  │                                                                         │ │  │
│  │  │  Invited: Mar 25, 2025 by Anna Lopez                                   │ │  │
│  │  │  Opened: Mar 25, 2025 (16:45)  •  Declined: Mar 26, 2025 (10:30)      │ │  │
│  │  │  Response Time: 18 hours                                               │ │  │
│  │  │                                                                         │ │  │
│  │  │  Decline Reason: "Schedule conflict - already committed to another event"│ │  │
│  │  │  Alternative Suggested: "Could recommend Anna Meier from my team"      │ │  │
│  │  │                                                                         │ │  │
│  │  │  [View Response] [Contact Alternative] [Find Replacement] [Archive]    │ │  │
│  │  └─────────────────────────────────────────────────────────────────────────┘ │  │
│  │                                                                              │  │
│  │  ┌────────────────────────────────────────────────────────────────────────┐ │  │
│  │  │  ☑  ❓ NEED INFO    Responded 3 days ago                               │ │  │
│  │  │                                                                         │ │  │
│  │  │  👤 Sarah Zimmermann (Bank AG)                                         │ │  │
│  │  │  📧 s.zimmermann@bank.ch • 🏢 Premium Partner                          │ │  │
│  │  │                                                                         │ │  │
│  │  │  Session: "Zero Trust Security Implementation"                         │ │  │
│  │  │  Topic: Cloud Security • Slot: 15:00-15:45 (Afternoon)                 │ │  │
│  │  │                                                                         │ │  │
│  │  │  Invited: Apr 1, 2025 by Thomas Weber                                  │ │  │
│  │  │  Opened: Apr 1, 2025 (11:20)  •  Requested Info: Apr 2, 2025 (08:45)  │ │  │
│  │  │  Deadline: Apr 8, 2025 (extended)                                      │ │  │
│  │  │                                                                         │ │  │
│  │  │  Questions: "Can I co-present with a colleague? Need virtual option."  │ │  │
│  │  │  Last Message: Apr 3, 2025 - Awaiting speaker response                │ │  │
│  │  │                                                                         │ │  │
│  │  │  [View Conversation] [Send Message] [Schedule Call] [Update Details]   │ │  │
│  │  └─────────────────────────────────────────────────────────────────────────┘ │  │
│  │                                                                              │  │
│  │                        [Load More Invitations (10 more) ↓]                  │  │
│  │                                                                              │  │
│  └──────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                   │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Invitation Detail Modal

```
┌────────────────── INVITATION DETAILS ───────────────────────────────────────┐
│  [X]                                                                         │
│                                                                              │
│  Invitation to: Maria Schmidt (TechFlow GmbH)                               │
│  Status: ⏳ PENDING • 📖 Opened 3 hours ago                                 │
│                                                                              │
│  ═══════════════════════════════════════════════════════════════════════    │
│                                                                              │
│  ┌─ INVITATION DETAILS ──────────────────────────────────────────────────┐  │
│  │                                                                        │  │
│  │  Event: Spring Conference 2025 • May 15, 2025                         │  │
│  │  Session: "GitOps Implementation Patterns"                             │  │
│  │  Topic: Cloud Native Architecture                                     │  │
│  │  Proposed Slot: Afternoon (13:00-17:00 preferred)                     │  │
│  │  Format: 45-minute presentation + 15-min Q&A                          │  │
│  │  Expected Audience: 200+ IT professionals                             │  │
│  │                                                                        │  │
│  │  Invited By: Thomas Weber                                             │  │
│  │  Invitation Sent: Apr 3, 2025 at 10:15                                │  │
│  │  Response Deadline: Apr 5, 2025 at 09:00  🔴 (18 hours remaining)    │  │
│  │                                                                        │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
│  ┌─ PERSONALIZED MESSAGE (Sent to Speaker) ──────────────────────────────┐  │
│  │                                                                        │  │
│  │  "Dear Maria,                                                          │  │
│  │                                                                        │  │
│  │  Your expertise in GitOps and your recent work on automated           │  │
│  │  deployment pipelines makes you the perfect speaker for this topic.   │  │
│  │  Your session at DevOps Zurich was highly rated (4.9/5) and we        │  │
│  │  believe our audience would greatly benefit from your insights.       │  │
│  │                                                                        │  │
│  │  We're particularly interested in hearing about your experience       │  │
│  │  implementing GitOps at scale in enterprise environments.             │  │
│  │                                                                        │  │
│  │  Looking forward to having you join us!                               │  │
│  │                                                                        │  │
│  │  Best regards,                                                         │  │
│  │  Thomas Weber"                                                         │  │
│  │                                                                        │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
│  ┌─ EMAIL TRACKING ──────────────────────────────────────────────────────┐  │
│  │                                                                        │  │
│  │  Delivery Status: ✅ Delivered                                         │  │
│  │  Delivered At: Apr 3, 2025 at 10:17                                   │  │
│  │                                                                        │  │
│  │  Email Opened: Yes (3 times)                                          │  │
│  │  First Opened: Apr 4, 2025 at 14:22                                   │  │
│  │  Last Opened: Apr 4, 2025 at 17:45 (3 hours ago)                      │  │
│  │                                                                        │  │
│  │  Links Clicked: 2 clicks                                              │  │
│  │  • Event details page: Apr 4, 2025 at 14:25                           │  │
│  │  • Speaker guidelines PDF: Apr 4, 2025 at 14:28                       │  │
│  │                                                                        │  │
│  │  Bounce/Spam: None                                                     │  │
│  │                                                                        │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
│  ┌─ SPEAKER ACTIVITY & ENGAGEMENT ───────────────────────────────────────┐  │
│  │                                                                        │  │
│  │  Login to Portal: Not yet registered                                  │  │
│  │  Invitation Page Views: 3 times (last: 3 hours ago)                   │  │
│  │  Time Spent on Page: ~8 minutes total                                 │  │
│  │                                                                        │  │
│  │  Engagement Score: 🟡 MODERATE                                         │  │
│  │  Likelihood to Accept: 65% (based on activity pattern)                │  │
│  │                                                                        │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
│  ┌─ COMMUNICATION HISTORY ───────────────────────────────────────────────┐  │
│  │                                                                        │  │
│  │  Apr 3, 2025 10:15 - Initial invitation sent by Thomas Weber          │  │
│  │  Apr 4, 2025 14:22 - Email opened by speaker                          │  │
│  │  Apr 4, 2025 14:25 - Speaker viewed event details                     │  │
│  │                                                                        │  │
│  │  No messages exchanged yet.                                            │  │
│  │                                                                        │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
│  ┌─ ACTIONS ─────────────────────────────────────────────────────────────┐  │
│  │                                                                        │  │
│  │  [ Send Reminder Email ]  [ Send Direct Message ]  [ Schedule Call ]   │  │
│  │                                                                        │  │
│  │  [ Extend Deadline ]  [ Update Invitation Details ]  [ Cancel ]        │  │
│  │                                                                        │  │
│  │  Advanced:                                                             │  │
│  │  [ Resend Invitation ]  [ Copy Invitation Link ]  [ View as Speaker ]  │  │
│  │                                                                        │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
│  [ Close ]                                                                   │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## Key Interactive Elements

- **[← Back to Event] button**: Return to Event Detail/Edit screen (story-1.16-event-detail-edit.md)
- **[+ Invite] button**: Navigate to Speaker Matching Interface to send new invitations (story-3.1-speaker-matching-interface.md)
- **Filter dropdowns**: Filter by status, organizer who sent invitation
- **Search field**: Search by speaker name, email, session title
- **Quick filter badges**: One-click filters for pending, expiring, declined
- **Bulk selection checkboxes**: Multi-select invitations for bulk actions
- **[ Select All ] button**: Select all visible invitations
- **[ Resend Selected ] button**: Resend invitations to selected speakers
- **[ Cancel Selected ] button**: Cancel selected pending invitations
- **[ Export CSV ] button**: Export invitation list with status and details
- **Sort dropdown**: Sort by deadline, sent date, response date, speaker name, status
- **View toggles**: Switch between list and grid view
- **Status badges**: Visual indicators (✅ Accepted, ❌ Declined, ⏳ Pending, ❓ Need Info)
- **Urgency indicators**: Color-coded deadline warnings (🔴 <24h, 🟡 <3 days, 🟢 >3 days)
- **Email tracking**: Shows opened/not opened status with timestamps
- **Invitation card click**: Opens invitation detail modal
- **[View Response] button**: View full speaker response (navigates to invitation response view)
- **[Send Message] button**: Opens message composer to speaker
- **[Send Reminder] button**: Send automated reminder email
- **[View Profile] button**: Navigate to Speaker Profile Detail View (story-7.1-speaker-profile-detail-view.md)
- **[Assign Slot] button**: Assign speaker to time slot (navigates to slot assignment)
- **[Extend Deadline] button**: Extend response deadline
- **[Cancel Invitation] button**: Cancel pending invitation
- **[Contact Alternative] button**: Contact alternative speaker suggested by declined speaker
- **[Find Replacement] button**: Search for replacement speaker (navigates to speaker matching)
- **[View Conversation] button**: View message thread with speaker

### Invitation Detail Modal Elements

- **[X] close button**: Close modal
- **Tracking timeline**: Visual timeline of invitation activity
- **[Send Reminder Email] button**: Send reminder with customizable message
- **[Send Direct Message] button**: Open message composer
- **[Schedule Call] button**: Create calendar event for call with speaker
- **[Extend Deadline] button**: Change response deadline
- **[Update Invitation Details] button**: Edit invitation details
- **[Cancel] button**: Cancel invitation
- **[Resend Invitation] button**: Resend email invitation
- **[Copy Invitation Link] button**: Copy unique invitation URL to clipboard
- **[View as Speaker] button**: Preview invitation as speaker would see it

---

## Functional Requirements Met

- **FR17**: AI-Powered Speaker Matching (invitation tracking for matched speakers)
- **FR19**: Multi-Organizer Coordination (see who sent which invitations)
- **FR3**: Speaker Self-Service (track speaker responses and engagement)
- **Email Tracking**: AWS SES tracking for delivery, opens, clicks
- **Workflow Integration**: Part of 16-step event workflow (Step 3-4)

---

## Technical Notes

- **Real-time Updates**: WebSocket connection for live status updates when speakers respond
- **Email Tracking**: AWS SES with tracking pixels for open/click metrics
- **Engagement Scoring**: Algorithm to predict acceptance likelihood based on activity
- **Deadline Monitoring**: Automated reminders at configurable intervals (e.g., 3 days, 1 day, 12 hours before deadline)
- **Bulk Operations**: Optimized API calls for multi-select actions
- **Export Functionality**: CSV generation with invitation details and status
- **Response Time Calculation**: Track time from sent to response
- **Message Threading**: Group messages by invitation for conversation view
- **Calendar Integration**: Create calendar events for speaker calls

---

## API Requirements

### Initial Page Load APIs

1. **GET /api/v1/invitations?filter={"eventId":"{eventId}"}&sort=deadline:asc&page=1&limit=50&include=speaker,session,event**
   - Query params:
     - `filter`: JSON filter for eventId and optional status filtering
     - `sort`: deadline:asc (or other sort fields: sentDate, responseDate, speakerName)
     - `page`, `limit`: Pagination parameters
     - `include`: speaker,session,event (includes related data to avoid separate calls)
   - Returns: Paginated array of invitations with embedded speaker, session, and event data
   - Response structure:
     ```json
     {
       "data": [
         {
           "id": "uuid",
           "status": "pending|accepted|declined|need_more_info",
           "sentAt": "timestamp",
           "sentBy": "organizerId",
           "deadline": "timestamp",
           "openedAt": "timestamp",
           "respondedAt": "timestamp",
           "preferences": {},
           "comments": "string",
           "speaker": { "id": "uuid", "name": "string", "email": "string", "company": "string" },
           "session": { "id": "uuid", "title": "string", "topic": "string" },
           "event": { "id": "uuid", "title": "string", "date": "timestamp" },
           "tracking": {
             "totalSent": 15,
             "opened": 12,
             "accepted": 8,
             "declined": 2,
             "pending": 5,
             "needInfo": 0,
             "responseRate": 0.67,
             "expiringCount": 2
           }
         }
       ],
       "meta": { "total": 15, "page": 1, "limit": 50 }
     }
     ```
   - Used for: Display invitation list, populate filters, calculate statistics (statistics included in response)
   - Note: Consolidates previous separate calls for invitations list, statistics, and event details

### User Action APIs

2. **GET /api/v1/invitations/{id}?include=speaker,session,event,tracking,communicationHistory**
   - Triggered by: User clicks invitation card to view details
   - Query params: `include` expands all related data for detail modal
   - Returns: Complete invitation object with:
     - Full speaker profile
     - Session details with timeline
     - Event details
     - Email tracking: delivered, openedCount, clicks, bounced, spam status
     - Speaker activity: pageViews, timeSpent, engagementScore, acceptanceLikelihood
     - Communication history array
   - Used for: Display invitation detail modal with all tracking and engagement data

3. **POST /api/v1/invitations**
   - Triggered by: User creates bulk reminders via array payload
   - Payload for bulk reminders:
     ```json
     {
       "invitationIds": ["uuid1", "uuid2"],
       "action": "send_reminder",
       "template": "standard|urgent|custom",
       "customMessage": "string"
     }
     ```
   - Returns: Batch operation result with success/failure status per invitation
   - Used for: Send reminders to multiple speakers (consolidates bulk-reminder endpoint)

4. **PATCH /api/v1/invitations/{id}**
   - Triggered by: User clicks [Extend Deadline] or updates invitation details
   - Payload:
     ```json
     {
       "deadline": "2025-04-10T17:00:00Z",
       "notifySpeaker": true,
       "reason": "string"
     }
     ```
   - Returns: Updated invitation with new deadline
   - Used for: Extend response deadline or update other invitation fields

5. **DELETE /api/v1/invitations/{id}**
   - Triggered by: User clicks [Cancel Invitation]
   - Query params: `reason=string&notifySpeaker=true`
   - Returns: Cancellation confirmation
   - Used for: Cancel pending invitation

6. **DELETE /api/v1/invitations?ids={comma-separated-ids}**
   - Triggered by: User selects multiple invitations and clicks [Cancel Selected]
   - Query params:
     - `ids`: Comma-separated list of invitation IDs
     - `reason`: Cancellation reason
     - `notifySpeakers`: Boolean flag
   - Returns: Batch cancellation result with success/failure per invitation
   - Used for: Cancel multiple invitations simultaneously

7. **GET /api/v1/invitations/export?filter={"eventId":"{eventId}"}&format=csv&fields=speakerName,email,status,sentDate,responseDate**
   - Triggered by: User clicks [Export CSV]
   - Query params:
     - `filter`: JSON filter for eventId and optional status
     - `format`: csv or xlsx
     - `fields`: Array of field names to include
   - Returns: Downloadable CSV/Excel file
   - Used for: Export invitation data for reporting

8. **POST /api/v1/invitations/{id}/resend**
   - Triggered by: User clicks [Resend Invitation]
   - Payload: `{ updateDetails: boolean, customMessage?: "string" }`
   - Returns: Resend confirmation with new deliveryId and tracking info
   - Used for: Resend invitation email (e.g., if speaker didn't receive original)

9. **GET /api/v1/invitations/{id}?format=preview**
   - Triggered by: User clicks [View as Speaker]
   - Query params: `format=preview` returns HTML rendering
   - Returns: HTML preview of invitation as speaker sees it
   - Used for: Preview invitation from speaker perspective

10. **POST /api/v1/events/{eventId}/sessions/{sessionId}**
    - Triggered by: User clicks [Assign Slot] for accepted speaker
    - Payload:
      ```json
      {
        "speakerId": "uuid",
        "timeSlot": "string",
        "invitationId": "uuid"
      }
      ```
    - Returns: Session assignment confirmation
    - Used for: Assign accepted speaker to time slot
    - Note: Uses consolidated Events API from Story 1.17

---

## Navigation Map

### Primary Navigation Actions

1. **← Back button** → Navigate to `Event Detail/Edit Screen` (story-1.16-event-detail-edit.md)
   - Target: Event management page
   - Context: Return to event with updated invitation status

2. **[+ Invite] button** → Navigate to `Speaker Matching Interface` (story-3.1-speaker-matching-interface.md)
   - Target: Speaker selection and invitation interface
   - Context: eventId, current invitation count

3. **Invitation card click** → Opens `Invitation Detail Modal`
   - Target: Modal overlay (same page)
   - Context: invitationId, full tracking and communication data

### Secondary Navigation (Data Interactions)

4. **[View Response] button** → Navigate to `Invitation Response (Organizer View)` (story-3.2-invitation-response.md)
   - Target: Full speaker response view
   - Context: invitationId, speaker response data, preferences

5. **[View Profile] button** → Navigate to `Speaker Profile Detail View` (story-7.1-speaker-profile-detail-view.md)
   - Target: Complete speaker profile
   - Context: speakerId, speaking history, expertise

6. **[Assign Slot] button** → Navigate to `Speaker Matching Interface` (slot assignment mode)
   - Target: Slot assignment interface
   - Context: speakerId, eventId, accepted speakers list

7. **[Find Replacement] button** → Navigate to `Speaker Matching Interface`
   - Target: Speaker search filtered by topic/expertise
   - Context: sessionId, topic, original speaker's expertise

8. **[Contact Alternative] button** → Creates message to alternative speaker
   - Action: Pre-fill message composer with context
   - Context: Alternative speaker info from decline response

### Event-Driven Navigation

9. **[Send Message] button** → Opens `Message Composer Modal`
   - Target: Modal with message composition form
   - Context: recipientId (speakerId), invitationId, suggested subject

10. **[Send Reminder] button** → Triggers email and shows confirmation
    - Action: Send reminder email via SES
    - Effect: Update invitation history, show toast confirmation
    - Navigation: Stays on page, updates invitation card

11. **[Export CSV] button** → Downloads file
    - Action: Generate and download CSV file
    - Target: File download
    - Context: Current filters applied, all selected invitations

### Real-Time Updates (WebSocket)

12. **Speaker responds** → Update UI
    - Trigger: WebSocket message: `invitation_response`
    - Effect: Update status badge, move to appropriate filter section
    - Context: invitationId, new status, response data

13. **Email opened** → Update tracking indicator
    - Trigger: WebSocket message: `invitation_opened`
    - Effect: Update "📖 Opened" badge, add timestamp
    - Context: invitationId, openedAt timestamp

14. **Deadline approaching** → Show warning
    - Trigger: WebSocket message: `deadline_warning`
    - Effect: Update urgency indicator to red, add to "Expiring Soon" filter
    - Context: invitationId, hours remaining

### Error States & Redirects

15. **Invitation not found** → Show error modal
    - Condition: invitationId invalid or deleted
    - Action: Display "Invitation not found or has been cancelled" message
    - Options: [Return to List]

16. **Speaker already responded** → Show info modal
    - Condition: Attempt to resend to speaker who already accepted/declined
    - Action: Display "Speaker has already responded" with current status
    - Options: [View Response] or [Send Follow-up Message]

17. **Email bounce** → Show bounce notification
    - Condition: AWS SES reports bounce
    - Action: Highlight invitation with ⚠️ warning, suggest verification
    - Options: [Update Email] or [Contact via Phone]

18. **Deadline passed** → Automatic status update
    - Condition: Current time > invitation deadline
    - Action: Mark as "Expired", send to organizer notification
    - Effect: Move to "Expired" filter section, suggest follow-up actions

19. **Insufficient permissions** → Redirect with error
    - Condition: User not organizer for this event
    - Action: Display "You don't have permission to manage invitations for this event"
    - Target: Redirect to Event Management Dashboard

---

## Responsive Design Considerations

### Mobile Layout Changes

**Stacked Layout (320px - 768px):**
- Invitation overview: Stacked stats cards, horizontal scroll
- Filters: Collapsible filter panel (slide-in drawer)
- Quick filters: Horizontal scrollable chips
- Invitation cards: Full-width stacked layout
  - Speaker name and photo at top
  - Session info and status stacked
  - Deadline and tracking info prominent
  - Action buttons: Dropdown menu (⋮) for space efficiency
- Detail modal: Full-screen takeover (not floating)
- Bulk actions: Bottom sheet modal

**Tablet Layout (768px - 1024px):**
- Two-column layout for invitation cards (when space allows)
- Side-by-side filters and stats
- Detail modal: Larger modal (90% width) but not full-screen

### Mobile-Specific Interactions

- **Swipe gestures**: Swipe invitation card right to send reminder, left to view details
- **Pull-to-refresh**: Refresh invitation list and update tracking status
- **Bottom sheet**: Filters, bulk actions, message composer in bottom sheets
- **Touch targets**: 44px minimum for all interactive elements
- **Sticky header**: Stats overview sticky on scroll
- **Progressive disclosure**: Collapse email tracking details until tapped
- **Quick actions**: Long-press on invitation card for quick action menu
- **Floating action button (FAB)**: [+ Invite] as FAB in bottom-right corner

---

## Accessibility Notes

- **Keyboard Navigation**: Full tab navigation through invitation list
- **ARIA Labels**:
  - `aria-label="Invitation to Peter Muller for Kubernetes session. Status: Accepted"` on cards
  - `aria-label="Send reminder email to speaker"` on reminder buttons
  - `aria-label="View full invitation response"` on view response links
  - `role="list"` on invitation list with `aria-labelledby` pointing to header
- **Screen Reader Announcements**:
  - Live region (`aria-live="polite"`) for status updates
  - Announcement when response received: "Maria Schmidt has accepted the invitation"
  - Deadline warnings announced: "2 invitations expiring in less than 24 hours"
- **Color Contrast**: WCAG 2.1 AA compliance
  - Status badges: Color + icon (✅ ❌ ⏳ ❓) for accessibility
  - Urgency colors: Red/Yellow/Green with icon + text label
- **Focus Indicators**: 2px solid outline on all focused elements
- **Alt Text**: All speaker photos have descriptive alt text
- **Semantic HTML**:
  - `<main>` for invitation list section
  - `<article>` for each invitation card
  - `<section>` for overview, filters, list
- **Skip Links**: "Skip to invitation list" link at top
- **Status Communication**: Status communicated via text, not just color

---

## State Management

### Local Component State

- `filterState`: Current filter settings { status: 'all', sentBy: 'all', searchQuery: '' }
- `sortBy`: Current sort field ('deadline', 'sentDate', 'responseDate', 'speakerName', 'status')
- `sortOrder`: Sort direction ('asc' or 'desc')
- `selectedInvitations`: Array of selected invitation IDs for bulk actions
- `activeDetailModal`: Currently open invitation detail modal (or null)
- `viewMode`: Current view mode ('list' or 'grid')

### Global State (Zustand Store)

- `invitationList`: Array of invitation objects
- `invitationStatistics`: Current statistics (counts, response rate, etc.)
- `eventDetails`: Current event information
- `webSocketConnected`: Connection status for real-time updates

### Server State (React Query)

- `useInvitations(eventId, filters, sort)`: Invitation list with 1-minute refetch interval
- `useInvitationStatistics(eventId)`: Statistics with 2-minute refetch
- `useInvitationDetail(invitationId)`: Individual invitation details with 30-second cache
- `useInvitationTracking(invitationId)`: Email tracking data with 15-second refetch for active invitations

### Real-Time Updates (WebSocket)

- **WebSocket Connection**: `/ws/events/{eventId}/invitations`
  - Real-time notifications when speakers respond to invitations
  - Email tracking updates (opened, clicked)
  - Deadline warnings for approaching deadlines
  - Other organizer actions (sent new invitation, cancelled invitation)

---

## Form Validation Rules

_No forms on main screen - filters and search are non-validated inputs_

**Invitation Detail Modal - Actions with Validation:**

- **Extend Deadline**:
  - New deadline must be future date
  - Max extension: 30 days from original deadline
  - Min extension: At least 1 day from current time

- **Send Reminder**:
  - Cannot send more than 3 reminders per invitation
  - Must wait at least 24 hours between reminders
  - Custom message: Max 500 characters

- **Cancel Invitation**:
  - Reason required for cancellation (min 10 characters)
  - Confirmation required if speaker has already opened invitation

---

## Edge Cases & Error Handling

- **Empty State (No Invitations)**:
  - Show "No invitations sent yet" message
  - Display [+ Invite Speakers] CTA prominently
  - Suggest: "Start by searching for speakers"

- **Loading State**:
  - Display skeleton cards for invitation list (4-6 items)
  - Show loading spinner in overview section
  - Disable filters and actions while loading

- **Error State (API Failure)**:
  - Show error banner: "Unable to load invitations. Please try again."
  - Provide [Retry] button
  - Display cached data if available with "showing cached data" indicator

- **All Invitations Expired**:
  - Filter automatically to show expired invitations
  - Show warning: "All pending invitations have expired"
  - Suggest: "Send reminders or find new speakers"

- **Email Bounce**:
  - Mark invitation with ⚠️ warning badge
  - Show bounce reason (hard bounce, soft bounce, invalid email)
  - Suggest: "Update speaker email address"
  - Provide [Contact via Phone] quick action

- **Email Marked as Spam**:
  - Alert organizer about spam complaint
  - Suggest alternative communication method
  - Log for deliverability monitoring

- **Speaker Email Not Opened (5+ days)**:
  - Automatic flag for attention
  - Suggest: "Email may not have been received - try phone contact"
  - Option to resend or use alternative contact

- **Multiple Organizers Send Same Invitation**:
  - Detect duplicate and warn second organizer
  - Show: "Peter already invited by Anna Lopez on Apr 3"
  - Option to view existing invitation or cancel duplicate

- **Concurrent Response While Viewing**:
  - WebSocket update shows speaker responded
  - Display notification: "Maria Schmidt just responded while you were viewing"
  - Offer [Refresh Details] to see response

- **Bulk Operation Partial Failure**:
  - Show results: "5 of 7 reminders sent successfully"
  - List failed items with reasons
  - Option to retry failed items

- **Network Offline (PWA)**:
  - Show "You're offline" banner
  - Display cached invitation data
  - Queue actions (reminders, messages) to send when online
  - Indicate queued actions with "pending send" badge

- **Speaker Already Assigned to Another Event**:
  - Show scheduling conflict warning
  - Display dates of conflicting event
  - Allow organizer to still send invitation with note

---

## API Consolidation Summary

This wireframe has been updated to use consolidated APIs from the following stories:

### Story 1.27: Invitations API Consolidation

**Key Changes:**
- **Unified listing**: Single `GET /api/v1/invitations?filter={}&include=speaker,session,event` replaces 3 separate endpoints
- **Embedded statistics**: Tracking stats now included in list response (no separate statistics endpoint needed)
- **Resource expansion**: `?include=` parameter loads speaker, session, and event data in one call
- **Filter-based queries**: JSON filter syntax for flexible querying (status, eventId, etc.)
- **Bulk operations**: Consolidated into array payloads on standard endpoints
- **Standardized actions**: Accept/decline use dedicated PUT endpoints

### Story 1.17: Events API Consolidation

**Key Changes:**
- **Session assignment**: `POST /api/v1/events/{eventId}/sessions/{sessionId}` for slot assignment
- **Nested resources**: Sessions managed under events endpoint hierarchy

### Story 1.19: Speakers API Consolidation

**Key Changes:**
- **Speaker profiles**: Embedded in invitation response via `?include=speaker`
- **Unified speaker data**: No separate speaker profile lookup needed

### Before vs After

**Before Consolidation (14 API calls):**
1. GET /api/v1/events/{eventId}/invitations (list)
2. GET /api/v1/events/{eventId}/invitations/statistics
3. GET /api/v1/events/{eventId} (event details)
4. GET /api/v1/invitations/{invitationId} (detail modal)
5. POST /api/v1/invitations/{invitationId}/reminder
6. PUT /api/v1/invitations/{invitationId}/deadline
7. DELETE /api/v1/invitations/{invitationId}
8. POST /api/v1/invitations/bulk-reminder
9. POST /api/v1/invitations/bulk-cancel
10. GET /api/v1/invitations/export
11. POST /api/v1/messages (direct messaging)
12. POST /api/v1/invitations/{invitationId}/resend
13. GET /api/v1/invitations/{invitationId}/preview
14. POST /api/v1/events/{eventId}/slots/{slotId}/assign

**After Consolidation (10 API calls):**
1. GET /api/v1/invitations?filter={}&include=speaker,session,event (includes statistics)
2. GET /api/v1/invitations/{id}?include=speaker,session,event,tracking,communicationHistory
3. POST /api/v1/invitations (bulk reminders via array payload)
4. PATCH /api/v1/invitations/{id} (extend deadline, update fields)
5. DELETE /api/v1/invitations/{id} (single cancellation)
6. DELETE /api/v1/invitations?ids={} (bulk cancellation)
7. GET /api/v1/invitations/export?filter={}
8. POST /api/v1/invitations/{id}/resend
9. GET /api/v1/invitations/{id}?format=preview
10. POST /api/v1/events/{eventId}/sessions/{sessionId} (session assignment)

**Improvements:**
- **28.6% reduction** in API calls (14 → 10)
- **3-in-1 page load**: Statistics, event details, and invitations list in single call with `?include=`
- **Simplified bulk operations**: Array-based payloads instead of separate bulk endpoints
- **Better filtering**: JSON filter syntax supports complex queries (status, date ranges, search)
- **Unified updates**: PATCH endpoint handles multiple update scenarios
- **Reduced complexity**: Fewer endpoints to maintain and document
- **Consistent patterns**: All APIs follow standard REST conventions from Story 1.16

---

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2025-10-04 | 1.0 | Initial wireframe creation for Invitation Management Screen | ux-expert |
| 2025-10-04 | 1.1 | Updated to use consolidated APIs from Stories 1.17, 1.19, 1.27 | Claude Code |

---

## Review Notes

### Stakeholder Feedback

_To be added during review_

### Design Iterations

_To be documented as design evolves_

### Open Questions

1. **Email Tracking Privacy**: Should we disclose email tracking to speakers in invitation?
   - Recommendation: Add disclaimer in email footer about tracking for logistics purposes
   - Legal/GDPR compliance review needed

2. **Reminder Frequency Limits**: What's the maximum number of reminders to send?
   - Current design: 3 reminders max, 24-hour minimum between reminders
   - Balance between persistence and avoiding spam
   - Decision needed from speaker relations team

3. **Acceptance Likelihood Algorithm**: How to calculate prediction accuracy?
   - Factors: Email opens, time spent, links clicked, past speaker behavior
   - Machine learning model or simple heuristic?
   - Data science team input needed

4. **Deadline Extensions**: Should speakers be able to request deadline extensions?
   - Current design: Organizer-initiated only
   - Alternative: Speaker can request extension via "Need More Info" response
   - User research needed

5. **Alternative Speaker Suggestions**: Should we auto-match alternatives when speaker declines?
   - Could use AI matching to suggest similar speakers
   - Risk: Over-automation might miss nuances
   - Product decision on level of automation

6. **Bulk Invitation Limits**: Maximum invitations in bulk operation?
   - Technical: AWS SES rate limits (14 emails/second in sandbox, higher in production)
   - UX: Too many at once may seem impersonal
   - Recommendation: 20 per batch, with rate limiting
