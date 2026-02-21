# Story 4.1: Moderator Review Queue - Wireframe

**Story**: Epic 4, Story 4.1 - Content Quality Review
**Screen**: Moderator Review Queue (Workflow Step 7)
**User Role**: Moderator (Organizer with Moderator Role)
**Related FR**: FR9 (Quality Control Standards)

**API Consolidation Update** (2025-10-04):
This wireframe has been updated to use consolidated APIs from Stories 1.17, 1.20, and 1.26:
- Review queue now uses `/api/v1/content?filter={"status":"pending_review"}` (Story 1.20)
- Content details use `/api/v1/content/{id}?include=session,speaker,event,materials` (Story 1.20)
- File operations unified under `/api/v1/content/{id}/download` and `/preview` (Story 1.20)
- Review submissions use `PATCH /api/v1/content/{id}` for status updates (Story 1.20)
- Bulk operations supported via array PATCH (Story 1.20)
- Notifications use `/api/v1/notifications` (Story 1.26)
- **API Reduction**: From 13 fragmented endpoints to 6 consolidated content APIs

---

## Visual Wireframe

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│ ← Back to Dashboard    Moderator Review Queue           [Filter ⏷]  [Sort ⏷]   │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                   │
│  ┌──── REVIEW QUEUE OVERVIEW ──────────────────────────────────────────────────┐  │
│  │                                                                              │  │
│  │  📊 Queue Status:  ⏳ Pending (8)  👁️ Under Review (3)  ✅ Approved (24)  │  │
│  │                   ⚠️ Requires Changes (2)  ❌ Rejected (1)                 │  │
│  │                                                                              │  │
│  │  ⏱️ Average Review Time: 1.5 hours  |  📈 This Week: 12 completed          │  │
│  │                                                                              │  │
│  └──────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                   │
│  ┌──── FILTERS & ACTIONS ───────────────────────────────────────────────────────┐  │
│  │                                                                              │  │
│  │  Event: [All Events ⏷]   Status: [Pending ⏷]   Speaker: [All ⏷]           │  │
│  │                                                                              │  │
│  │  Quick Filters:  [⚠️ Urgent (2)]  [📅 Due Soon (5)]  [🔄 Revisions (2)]   │  │
│  │                                                                              │  │
│  │  [ Bulk Approve Selected ]  [ Bulk Request Changes ]  [ Export Report ]    │  │
│  │                                                                              │  │
│  └──────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                   │
│  ┌──── PENDING REVIEWS ──────────────────────────────────────────────────────────┐  │
│  │                                                                              │  │
│  │  ┌────────────────────────────────────────────────────────────────────────┐ │  │
│  │  │  ⏳ PENDING             🔴 URGENT: Due in 6 hours                       │ │  │
│  │  │                                                                         │ │  │
│  │  │  Session: "Kubernetes at Scale: Production Best Practices"             │ │  │
│  │  │  Speaker: Peter Muller (TechCorp AG)                                   │ │  │
│  │  │  Event: Spring Conference 2025 (May 15, 2025)                          │ │  │
│  │  │                                                                         │ │  │
│  │  │  📝 Abstract: 847/1000 chars  |  📄 Presentation: uploaded.pdf (12MB)  │ │  │
│  │  │  👤 Speaker Photo: ✅  |  📋 Bio: ✅  |  🔧 Tech Req: ✅                │ │  │
│  │  │                                                                         │ │  │
│  │  │  Submitted: 2 days ago  |  Deadline: Today 17:00                       │ │  │
│  │  │                                                                         │ │  │
│  │  │  [Review Now] [Preview Abstract] [Download Materials] [Assign to Me]   │ │  │
│  │  └─────────────────────────────────────────────────────────────────────────┘ │  │
│  │                                                                              │  │
│  │  ┌────────────────────────────────────────────────────────────────────────┐ │  │
│  │  │  ⏳ PENDING             🟡 Due in 1 day                                 │ │  │
│  │  │                                                                         │ │  │
│  │  │  Session: "Zero Trust Security Architecture"                           │ │  │
│  │  │  Speaker: Thomas Weber (Swiss Re)                                      │ │  │
│  │  │  Event: Spring Conference 2025 (May 15, 2025)                          │ │  │
│  │  │                                                                         │ │  │
│  │  │  📝 Abstract: 1024/1000 chars ⚠️  |  📄 Presentation: not uploaded ⚠️  │ │  │
│  │  │  👤 Speaker Photo: ✅  |  📋 Bio: ✅  |  🔧 Tech Req: pending ⚠️       │ │  │
│  │  │                                                                         │ │  │
│  │  │  Submitted: 5 hours ago  |  Deadline: Tomorrow 17:00                   │ │  │
│  │  │                                                                         │ │  │
│  │  │  [Review Now] [Preview Abstract] [Request Missing Items] [Assign to Me]│ │  │
│  │  └─────────────────────────────────────────────────────────────────────────┘ │  │
│  │                                                                              │  │
│  │  ┌────────────────────────────────────────────────────────────────────────┐ │  │
│  │  │  👁️ UNDER REVIEW by Anna Lopez (me)    ⏱️ Started 45 minutes ago      │ │  │
│  │  │                                                                         │ │  │
│  │  │  Session: "GitOps Implementation Patterns"                              │ │  │
│  │  │  Speaker: Maria Schmidt (TechFlow GmbH)                                │ │  │
│  │  │  Event: Spring Conference 2025 (May 15, 2025)                          │ │  │
│  │  │                                                                         │ │  │
│  │  │  📝 Abstract: 923/1000 chars  |  📄 Presentation: gitops-slides.pdf    │ │  │
│  │  │  👤 Speaker Photo: ✅  |  📋 Bio: ✅  |  🔧 Tech Req: ✅                │ │  │
│  │  │                                                                         │ │  │
│  │  │  [Continue Review] [Preview Abstract] [Complete Review]                │ │  │
│  │  └─────────────────────────────────────────────────────────────────────────┘ │  │
│  │                                                                              │  │
│  │  ┌────────────────────────────────────────────────────────────────────────┐ │  │
│  │  │  🔄 REVISION REQUESTED    Waiting for speaker (deadline in 3 days)     │ │  │
│  │  │                                                                         │ │  │
│  │  │  Session: "Container Security Best Practices"                           │ │  │
│  │  │  Speaker: David Klein (SecureCloud Inc.)                               │ │  │
│  │  │  Event: Spring Conference 2025 (May 15, 2025)                          │ │  │
│  │  │                                                                         │ │  │
│  │  │  Initial Review: 3 days ago by Anna Lopez (me)                         │ │  │
│  │  │  Issues: Abstract exceeds limit, too promotional, missing tech reqs    │ │  │
│  │  │                                                                         │ │  │
│  │  │  [View Original Feedback] [Check Resubmission] [Send Reminder]         │ │  │
│  │  └─────────────────────────────────────────────────────────────────────────┘ │  │
│  │                                                                              │  │
│  │                        [Load More Reviews (4 more) ↓]                       │  │
│  │                                                                              │  │
│  └──────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                   │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Review Detail Modal

```
┌────────────────── CONTENT QUALITY REVIEW ───────────────────────────────────┐
│  [X]                                                                         │
│                                                                              │
│  Session: "Kubernetes at Scale: Production Best Practices"                  │
│  Speaker: Peter Muller (TechCorp AG) • peter.muller@techcorp.ch            │
│  Event: Spring Conference 2025 • May 15, 2025 • 14:00-14:45                │
│                                                                              │
│  ═══════════════════════════════════════════════════════════════════════    │
│                                                                              │
│  ┌─ ABSTRACT REVIEW ──────────────────────────────────────────────────────┐ │
│  │                                                                         │ │
│  │  Character Count: 847/1000 ✅  (Within limit)                          │ │
│  │                                                                         │ │
│  │  ┌──────────────────────────────────────────────────────────────────┐  │ │
│  │  │  Title: "Kubernetes at Scale: Production Best Practices"          │  │ │
│  │  │                                                                    │  │ │
│  │  │  Abstract:                                                         │  │ │
│  │  │  This session explores battle-tested strategies for running        │  │ │
│  │  │  Kubernetes in production at scale. We'll cover real-world        │  │ │
│  │  │  challenges we faced when scaling from 10 to 1000+ pods,          │  │ │
│  │  │  including resource management, monitoring, security hardening,    │  │ │
│  │  │  and cost optimization. Drawing from 3 years of production        │  │ │
│  │  │  experience at TechCorp, I'll share lessons learned, common       │  │ │
│  │  │  pitfalls to avoid, and architectural patterns that work.         │  │ │
│  │  │                                                                    │  │ │
│  │  │  Key Takeaways:                                                    │  │ │
│  │  │  • Auto-scaling strategies for unpredictable workloads            │  │ │
│  │  │  • Network policy implementation for multi-tenant clusters        │  │ │
│  │  │  • Observability stack design (Prometheus, Grafana, ELK)          │  │ │
│  │  │  • CI/CD pipeline integration with GitOps                         │  │ │
│  │  │  • Cost optimization techniques that saved us 40%                 │  │ │
│  │  └──────────────────────────────────────────────────────────────────┘  │ │
│  │                                                                         │ │
│  │  Quality Checks:                                                        │ │
│  │  ☑ Contains lessons learned / real-world experience                    │ │
│  │  ☑ Specific and actionable content                                     │ │
│  │  ☐ Appears to be product promotion ⚠️ (borderline - mentions specific tools) │
│  │  ☑ Appropriate technical level for audience                            │ │
│  │  ☑ Clear learning objectives                                           │ │
│  │                                                                         │ │
│  │  Abstract Rating: ⭐⭐⭐⭐☆ (4/5)                                        │ │
│  │                                                                         │ │
│  └─────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  ┌─ MATERIALS REVIEW ────────────────────────────────────────────────────┐  │
│  │                                                                        │  │
│  │  📄 Presentation: k8s-production-best-practices.pdf (12.3 MB)         │  │
│  │     Uploaded: 2 days ago  •  Format: PDF  •  Pages: 45                │  │
│  │     [Preview] [Download] [Request Revision]                           │  │
│  │                                                                        │  │
│  │  👤 Speaker Photo: peter-muller.jpg (2.1 MB) ✅                       │  │
│  │     Professional headshot, good quality                               │  │
│  │     [View Photo]                                                      │  │
│  │                                                                        │  │
│  │  📋 Biography: ✅ (342 characters)                                     │  │
│  │     "Peter Muller is a Principal Engineer at TechCorp AG with..."     │  │
│  │     [View Full Bio]                                                   │  │
│  │                                                                        │  │
│  │  🔧 Technical Requirements: ✅                                         │  │
│  │     • Projector: Required                                             │  │
│  │     • Microphone: Required                                            │  │
│  │     • Internet: Required (for live demos)                             │  │
│  │     • Special: Backup laptop with slides (in case of failure)         │  │
│  │                                                                        │  │
│  │  Completeness: ████████████████████ 100% Complete                     │  │
│  │                                                                        │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
│  ┌─ REVIEWER NOTES & FEEDBACK ───────────────────────────────────────────┐  │
│  │                                                                        │  │
│  │  [Feedback to Speaker:]                                                │  │
│  │  ┌──────────────────────────────────────────────────────────────────┐ │  │
│  │  │  Excellent abstract with clear learning objectives! The real-    │ │  │
│  │  │  world experience and specific metrics (40% cost savings) add    │ │  │
│  │  │  credibility. One minor note: please ensure your live demos have │ │  │
│  │  │  offline fallbacks in case of connectivity issues.               │ │  │
│  │  │                                                                   │ │  │
│  │  │  Your presentation looks comprehensive. Consider adding a final  │ │  │
│  │  │  "Resources & Further Reading" slide to maximize attendee value. │ │  │
│  │  │                                                                   │ │  │
│  │  └──────────────────────────────────────────────────────────────────┘ │  │
│  │                                                                        │  │
│  │  [Internal Notes (not visible to speaker):]                           │  │
│  │  ┌──────────────────────────────────────────────────────────────────┐ │  │
│  │  │  Strong submission. TechCorp is a Gold partner, good for          │ │  │
│  │  │  diversity. Peter spoke at Fall 2024 event (well-received).       │ │  │
│  │  │  Recommended approval.                                             │ │  │
│  │  └──────────────────────────────────────────────────────────────────┘ │  │
│  │                                                                        │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
│  ┌─ REVIEW DECISION ──────────────────────────────────────────────────────┐ │
│  │                                                                         │ │
│  │  Review Status: [Approve ✅] [Request Changes ⚠️] [Reject ❌]          │ │
│  │                                                                         │ │
│  │  If requesting changes:                                                 │ │
│  │  ☐ Abstract exceeds character limit                                    │ │
│  │  ☐ Abstract appears promotional                                        │ │
│  │  ☐ Abstract lacks lessons learned                                      │ │
│  │  ☐ Missing presentation file                                           │ │
│  │  ☐ Missing speaker photo                                               │ │
│  │  ☐ Missing biography                                                    │ │
│  │  ☐ Incomplete technical requirements                                   │ │
│  │  ☐ Other (specify in feedback)                                         │ │
│  │                                                                         │ │
│  │  Revision Deadline: [Select Date ⏷]  (Default: 5 days from now)       │ │
│  │                                                                         │ │
│  │  ☐ Send email notification to speaker                                  │ │
│  │  ☐ Copy organizers on decision                                         │ │
│  │                                                                         │ │
│  └─────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  [ Cancel ]                                  [Save Draft] [Submit Review]   │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## Key Interactive Elements

- **[← Back to Dashboard] button**: Return to Event Management Dashboard (story-1.16-event-management-dashboard.md)
- **[Filter ⏷] dropdown**: Filter by event, status, speaker, urgency
- **[Sort ⏷] dropdown**: Sort by deadline, submission date, event date, speaker name
- **Quick filter badges**: One-click filters for urgent, due soon, revisions
- **[ Bulk Approve Selected ] button**: Approve multiple selected reviews at once
- **[ Bulk Request Changes ] button**: Request changes for multiple submissions
- **[ Export Report ] button**: Download review queue report (CSV/PDF)
- **[Review Now] button**: Opens review detail modal for selected submission
- **[Preview Abstract] button**: Quick preview modal without opening full review
- **[Download Materials] button**: Download all submitted materials as ZIP
- **[Assign to Me] button**: Claim review assignment to prevent duplicate work
- **[Continue Review] button**: Resume in-progress review
- **[Complete Review] button**: Finalize and submit review decision
- **[View Original Feedback] button**: View feedback from previous review cycle
- **[Check Resubmission] button**: Check if speaker has resubmitted changes
- **[Send Reminder] button**: Send reminder email to speaker about pending revision
- **Review card click**: Opens full review detail modal
- **Status badges**: Visual indicators (⏳ Pending, 👁️ Under Review, ✅ Approved, ⚠️ Requires Changes, ❌ Rejected)
- **Urgency indicators**: Color-coded urgency (🔴 Urgent, 🟡 Due Soon, 🟢 On Track)
- **Checkbox selection**: Multi-select reviews for bulk actions

### Review Detail Modal Elements

- **[X] close button**: Close modal and return to queue
- **Abstract text area**: Display full abstract with character count
- **Quality check checkboxes**: Validate abstract against standards
- **Abstract rating**: 1-5 star rating system
- **[Preview] button**: Preview presentation in browser
- **[Download] button**: Download individual files
- **[Request Revision] button**: Request changes to specific material
- **[View Photo] button**: Full-screen speaker photo preview
- **[View Full Bio] button**: Expand biography in modal
- **Feedback text areas**: Public feedback and private notes
- **[Approve ✅] button**: Approve submission and advance to next workflow step
- **[Request Changes ⚠️] button**: Send back to speaker with feedback
- **[Reject ❌] button**: Reject submission (rare, requires justification)
- **Change request checkboxes**: Pre-defined common issues for feedback
- **Revision deadline picker**: Set deadline for speaker to address changes
- **Notification toggles**: Control email notifications
- **[Save Draft] button**: Save work in progress without submitting
- **[Submit Review] button**: Finalize and submit review decision

---

## Functional Requirements Met

- **FR9**: Quality control standards enforcement through systematic review process
- **Workflow Step 7**: Content quality review before slot assignment
- **FR19**: Multi-organizer coordination (moderator can see who's reviewing what)
- **Feedback Loop**: Structured feedback mechanism with revision workflow

---

## Technical Notes

- **Real-time Updates**: WebSocket connection for queue status updates when other moderators claim/complete reviews
- **Auto-assignment**: Prevent duplicate work by claiming reviews ("Assign to Me")
- **Draft Saving**: Auto-save review progress every 30 seconds to prevent data loss
- **File Preview**: In-browser PDF preview using PDF.js library
- **Character Counter**: Live character count for abstract validation
- **Bulk Operations**: Optimized bulk API calls for multi-select actions
- **Urgency Calculation**: Based on deadline proximity (< 12 hours = urgent, < 24 hours = due soon)
- **Review Timer**: Track how long moderator spends on each review
- **Notification System**: Email notifications to speakers on review decisions
- **Version Control**: Track revision cycles and feedback history

---

## API Requirements

### Initial Page Load APIs

1. **GET /api/v1/content**
   - Query params: `filter={"status": "pending_review", "moderatorId": "{userId}"}&sort=deadline&order=asc&page=1&limit=50&include=session,speaker,event,materials`
   - Returns: Array of content submissions with full context (session details, speaker info, event data, materials)
   - Used for: Display pending reviews queue with all required information
   - Source: Story 1.20 (Content API Consolidation)

2. **GET /api/v1/content/analytics**
   - Query params: `metrics=pending,underReview,approved,requiresChanges,rejected,averageReviewTime,weeklyCompleted&userId={moderatorId}`
   - Returns: Queue statistics (counts by status, performance metrics)
   - Used for: Display queue overview section with statistics
   - Source: Story 1.20 (Content API Consolidation)

3. **GET /api/v1/events**
   - Query params: `filter={"status": "active"}&fields=id,title,date&limit=100`
   - Returns: List of active events for filter dropdown
   - Used for: Populate event filter options
   - Source: Story 1.17 (Events API Consolidation)

### User Action APIs

4. **GET /api/v1/content/{contentId}**
   - Triggered by: User clicks [Review Now] on a submission
   - Query params: `include=session,speaker,event,materials,reviews,history`
   - Returns: Complete content object with full details (abstract, materials, speaker info, event details, submission history)
   - Used for: Display review detail modal with all information
   - Source: Story 1.20 (Content API Consolidation)

5. **PATCH /api/v1/content/{contentId}**
   - Triggered by: User clicks [Submit Review] after completing review
   - Payload: `{ "status": "approved" | "requires_changes" | "rejected", "reviewFeedback": "...", "abstractReview": {...}, "materialReview": {...}, "revisionRequested": true, "revisionDeadline": "2025-05-10T17:00:00Z", "internalNotes": "..." }`
   - Returns: Updated content object with review decision
   - Used for: Submit review decision, send notifications to speaker
   - Source: Story 1.20 (Content API Consolidation)

6. **PATCH /api/v1/content/{contentId}**
   - Triggered by: User clicks [Assign to Me]
   - Payload: `{ "status": "under_review", "reviewerId": "{userId}", "reviewStartedAt": "2025-01-15T10:00:00Z" }`
   - Returns: Updated content with assigned reviewer
   - Used for: Claim review to prevent duplicate work, update queue status
   - Source: Story 1.20 (Content API Consolidation)

7. **PATCH /api/v1/content/{contentId}**
   - Triggered by: Auto-save every 30 seconds or [Save Draft] click
   - Payload: `{ "draftReview": { "feedback": "...", "abstractReview": {...}, "materialReview": {...}, "internalNotes": "..." } }`
   - Returns: Confirmation of draft saved
   - Used for: Save work in progress without changing review status
   - Source: Story 1.20 (Content API Consolidation)

8. **GET /api/v1/content/{contentId}/download**
   - Triggered by: User clicks [Download] on presentation or materials
   - Query params: `materialId={}&type=presentation|document|attachment`
   - Returns: Presigned S3 download URL or direct file stream
   - Used for: Download submitted materials for review
   - Source: Story 1.20 (Content API Consolidation)

9. **GET /api/v1/content/{contentId}/preview**
   - Triggered by: User clicks [Preview] on presentation
   - Query params: `materialId={}`
   - Returns: Presigned S3 URL or base64-encoded content for in-browser viewing
   - Used for: In-browser PDF/document preview in modal
   - Source: Story 1.20 (Content API Consolidation)

10. **PATCH /api/v1/content**
    - Triggered by: User selects multiple reviews and clicks [Bulk Approve Selected] or [Bulk Request Changes]
    - Payload: `[{ "id": "content-1", "status": "approved", "reviewFeedback": "..." }, { "id": "content-2", "status": "approved", "reviewFeedback": "..." }]`
    - Returns: Batch operation result with success/failure status per item
    - Used for: Process multiple reviews simultaneously
    - Source: Story 1.20 (Content API Consolidation) - bulk operations

11. **POST /api/v1/content/export**
    - Triggered by: User clicks [Export Report]
    - Payload: `{ "format": "csv|pdf", "filter": {"status": "all|pending|approved", "dateRange": "last_week|last_month|custom"}, "moderatorId": "{userId}" }`
    - Returns: Download URL or file stream for report
    - Used for: Generate queue status report for management
    - Source: Story 1.20 (Content API Consolidation)

12. **POST /api/v1/notifications**
    - Triggered by: User clicks [Send Reminder] on revision-requested item
    - Payload: `{ "type": "revision_reminder", "recipientId": "{speakerId}", "contentId": "{contentId}", "customMessage": "..." }`
    - Returns: Notification sent confirmation
    - Used for: Send reminder email to speaker about pending revision
    - Source: Story 1.26 (Notifications API Consolidation)

13. **GET /api/v1/content/{contentId}/versions**
    - Triggered by: User clicks [View Original Feedback] on revision item
    - Query params: `include=reviews&page=1&limit=10`
    - Returns: Array of previous content versions with review cycles (reviewerId, reviewedAt, status, feedback, changes)
    - Used for: Display revision history and previous feedback
    - Source: Story 1.20 (Content API Consolidation)

---

## Navigation Map

### Primary Navigation Actions

1. **← Back button** → Navigate to `Event Management Dashboard` (story-1.16-event-management-dashboard.md)
   - Target: Organizer main dashboard
   - Context: Return from moderator review queue

2. **[Review Now] button** → Opens `Review Detail Modal`
   - Target: Modal overlay (same page) with full review interface
   - Context: sessionId, current review status, speaker info

3. **[Continue Review] button** → Opens `Review Detail Modal` with saved draft
   - Target: Modal overlay with work in progress loaded
   - Context: reviewId, saved draft data

### Secondary Navigation (Data Interactions)

4. **Review card click** → Opens `Review Detail Modal`
   - Target: Modal overlay with review details
   - Context: sessionId, submission details

5. **[Preview Abstract] button** → Opens `Abstract Preview Modal`
   - Target: Quick preview modal (lighter than full review)
   - Context: Abstract text, character count, quality checks

6. **[Download Materials] button** → Downloads ZIP file
   - Action: Generate and download ZIP of all materials
   - Target: File download
   - Context: sessionId, all material fileIds

7. **Speaker name link** → Navigate to `Speaker Profile Detail View` (story-7.1-speaker-profile-detail-view.md)
   - Target: Full speaker profile page
   - Context: speakerId, speaking history

8. **Event name link** → Navigate to `Event Detail/Edit Screen` (story-1.16-event-detail-edit.md)
   - Target: Event management page
   - Context: eventId, current event status

### Event-Driven Navigation

9. **[Submit Review] button** → Updates queue and may navigate
   - Action: Submit review decision
   - Effect: Update queue status, send notifications, advance workflow
   - Navigation: Modal closes, returns to updated queue
   - Context: Review decision triggers workflow progression

10. **[Bulk Approve Selected] button** → Batch operation
    - Action: Process multiple approvals
    - Effect: Update multiple review statuses, send batch notifications
    - Navigation: Queue refreshes with updated statuses
    - Context: selectedReviewIds[]

### WebSocket Real-Time Updates

11. **Review claimed by another moderator** → Update UI
    - Trigger: WebSocket message: `review_claimed`
    - Effect: Remove from pending queue or show "Under review by [Name]"
    - Context: reviewId, reviewerName

12. **Review completed by another moderator** → Update statistics
    - Trigger: WebSocket message: `review_completed`
    - Effect: Update queue overview statistics
    - Context: Updated counts for pending/approved/etc.

### Error States & Redirects

13. **Insufficient permissions** → Show error and redirect
    - Condition: User not assigned moderator role for event
    - Action: Display "You are not a moderator for this event" message
    - Target: Redirect to Event Management Dashboard
    - Context: Event moderator assignment required

14. **Review already claimed** → Show warning modal
    - Condition: Another moderator already started review
    - Action: Display "Review being processed by [Name]. Continue anyway?"
    - Options: [View Queue] or [Continue Anyway] (override)
    - Context: Prevent duplicate work with override option

15. **Session not found / deleted** → Remove from queue
    - Condition: Session deleted or speaker withdrew
    - Action: Show notification "Session no longer available"
    - Effect: Remove from queue, update statistics
    - Context: Graceful handling of stale queue items

16. **Review deadline passed** → Show escalation notice
    - Condition: Submission deadline passed without review
    - Action: Highlight in red, show escalation notice
    - Effect: Automatic notification to backup moderator
    - Context: Escalation workflow triggered

---

## Responsive Design Considerations

### Mobile Layout Changes

**Stacked Layout (320px - 768px):**
- Queue overview: Stacked stats cards with horizontal scroll
- Filters: Collapsible filter panel (slide-in from side)
- Quick filters: Horizontal scrollable chips
- Review cards: Full-width stacked layout (one per row)
  - Session title at top
  - Speaker info and event stacked
  - Material status icons in single row
  - Deadlines and urgency prominently displayed
  - Action buttons: Dropdown menu (⋮) to save space
- Review modal: Full-screen takeover (not floating modal)
- Bulk actions: Bottom sheet modal

**Tablet Layout (768px - 1024px):**
- Two-column layout for review cards (when possible)
- Side-by-side filters and search
- Review modal: Larger modal (80% width) but not full-screen

### Mobile-Specific Interactions

- **Swipe gestures**: Swipe review card right to approve, left to request changes
- **Pull-to-refresh**: Refresh queue and statistics
- **Bottom sheet**: Filters, bulk actions in bottom sheets
- **Touch targets**: 44px minimum for all interactive elements
- **Sticky header**: Queue overview stats sticky on scroll
- **Progressive disclosure**: Collapse less critical info on cards
- **Quick actions**: Long-press on review card for quick action menu
- **Floating action button (FAB)**: Quick access to filters/sort

---

## Accessibility Notes

- **Keyboard Navigation**: Full tab navigation with logical focus order through queue
- **ARIA Labels**:
  - `aria-label="Review submission for Kubernetes at Scale session"` on review cards
  - `aria-label="Approve submission"` on approve button
  - `aria-label="Request changes with feedback"` on request changes button
  - `role="queue"` on review list with `aria-labelledby` pointing to queue header
- **Screen Reader Announcements**:
  - Live region (`aria-live="polite"`) for queue status updates
  - Announcement when review claimed: "Review claimed by Anna Lopez"
  - Status changes announced: "Review approved. 7 reviews remaining."
- **Color Contrast**: WCAG 2.1 AA compliance
  - Urgency colors: Red (urgent), Yellow (due soon), Green (on track) with icon + text
  - Status badges: Color + icon for accessibility
- **Focus Indicators**: 2px solid outline on all focused elements
- **Alt Text**: All speaker photos have descriptive alt text
- **Semantic HTML**:
  - `<main>` for review queue section
  - `<article>` for each review card
  - `<section>` for queue overview, filters, review list
- **Skip Links**: "Skip to review queue" link at top
- **Form Labels**: All form fields properly labeled in review modal
- **Error Messages**: Associated with fields using `aria-describedby`

---

## State Management

### Local Component State

- `queueFilter`: Current filter settings { event: 'all', status: 'pending', speaker: 'all', urgency: 'all' }
- `sortBy`: Current sort field ('deadline', 'submissionDate', 'eventDate', 'speakerName')
- `sortOrder`: Sort direction ('asc' or 'desc')
- `selectedReviews`: Array of selected review IDs for bulk actions
- `activeReviewModal`: Currently open review modal data (or null)
- `draftReviewData`: Auto-saved draft data for current review
- `expandedCards`: Set of expanded review card IDs (for progressive disclosure)

### Global State (Zustand Store)

- `reviewQueue`: Array of pending review objects
- `queueStatistics`: Current queue statistics (counts, average time, etc.)
- `currentModerator`: Current user's moderator profile
- `eventList`: Available events for filtering
- `webSocketConnected`: Connection status for real-time updates

### Server State (React Query)

- `useReviewQueue(moderatorId, filters, sort)`: Review queue with 30-second refetch interval
- `useQueueStatistics(moderatorId)`: Statistics with 1-minute refetch
- `useReviewDetail(sessionId)`: Individual review details with 1-minute cache
- `useReviewHistory(sessionId)`: Revision history with 5-minute cache

### Real-Time Updates (WebSocket)

- **WebSocket Connection**: `/ws/moderators/{moderatorId}/queue`
  - Real-time notifications when reviews claimed by other moderators
  - Queue statistics updates when reviews completed
  - New submission notifications
  - Deadline approaching warnings
  - Speaker resubmission notifications

### Auto-Save Mechanism

- **Draft Auto-Save**: Every 30 seconds while review modal open
  - Saves feedback text, quality checks, ratings, internal notes
  - Survives browser refresh (stored in localStorage + server)
  - Restored when modal reopened
  - Cleared when review submitted

---

## Form Validation Rules

### Review Submission Validation

- **Required Fields**:
  - Review decision (approve/request changes/reject) - REQUIRED
  - Feedback text when requesting changes - REQUIRED (min 20 chars)
  - Feedback text when rejecting - REQUIRED (min 50 chars, must include justification)
  - At least one change request checkbox when requesting changes - REQUIRED

- **Optional Fields**:
  - Feedback text when approving - OPTIONAL (but recommended)
  - Abstract rating - OPTIONAL (defaults to no rating)
  - Internal notes - OPTIONAL
  - Revision deadline - OPTIONAL (defaults to 5 days from now if not specified)

- **Field-Specific Validation**:
  - Feedback text: Max 2000 characters
  - Internal notes: Max 1000 characters
  - Revision deadline: Must be future date, max 30 days from now
  - Abstract rating: 1-5 stars only

### Error Messages

- Missing feedback: "Please provide feedback when requesting changes"
- Insufficient feedback: "Feedback must be at least 20 characters"
- No change requested: "Please select at least one issue to address"
- Invalid deadline: "Revision deadline must be between tomorrow and 30 days from now"
- Rejection without justification: "Please provide detailed justification for rejection (min 50 characters)"

---

## Edge Cases & Error Handling

- **Empty Queue**:
  - Show "No pending reviews" message
  - Display congratulations badge: "Great job! Queue is empty 🎉"
  - Suggest: "Check approved reviews" or "View upcoming submissions"

- **Loading State**:
  - Display skeleton cards for review queue (4-6 skeleton items)
  - Show loading spinner in queue overview
  - Disable filters and actions while loading

- **Error State (API Failure)**:
  - Show error banner: "Unable to load review queue. Please try again."
  - Provide [Retry] button to refetch data
  - Maintain last successful queue data if available (with "showing cached data" indicator)

- **Review Already Claimed**:
  - Show warning modal: "This review is currently being processed by [Moderator Name]"
  - Options: [View Other Reviews] or [Continue Anyway] (with warning about duplicate work)
  - Track who's reviewing what via WebSocket

- **Concurrent Review Conflict**:
  - Detect when another moderator completes review while current user has modal open
  - Show warning: "This review was just completed by [Name]. Your changes cannot be saved."
  - Options: [View Their Feedback] or [Close Modal]

- **Session Withdrawn/Deleted**:
  - Show notice: "Speaker has withdrawn from this session"
  - Automatically remove from queue
  - Log withdrawal for audit trail

- **Materials Missing After Initial Submission**:
  - Show warning: "Some materials are no longer available"
  - Options: [Request Reupload] or [Review With Available Materials]
  - Track file availability status

- **Deadline Passed During Review**:
  - Show urgent banner: "⚠️ Deadline has passed while reviewing"
  - Auto-escalate to backup moderator
  - Still allow review completion with note added

- **Bulk Operation Partial Failure**:
  - Show results: "3 of 5 reviews processed successfully"
  - List failed items with reasons
  - Option to retry failed items individually

- **Network Offline (PWA)**:
  - Show "You're offline" banner
  - Disable submission but allow review draft to continue
  - Auto-sync when connection restored
  - Queue drafts locally until online

- **Draft Recovery**:
  - On browser crash/close: Restore draft on next session
  - Show notification: "Recovered unsaved review from previous session"
  - Option to [Continue] or [Discard]

- **Revision Resubmission**:
  - Highlight in queue: "🔄 Resubmitted - Ready for re-review"
  - Display diff from previous version
  - Show original feedback for context
  - Track revision cycle count

---

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2025-10-04 | 1.0 | Initial wireframe creation for Moderator Review Queue Screen | ux-expert |

---

## Review Notes

### Stakeholder Feedback

_To be added during review_

### Design Iterations

_To be documented as design evolves_

### Open Questions

1. **Review Time Limits**: Should there be a maximum time limit for a moderator to hold a review?
   - Recommendation: 4 hours max, then auto-release back to queue
   - Prevents bottlenecks from abandoned reviews
   - Decision needed from process team

2. **Bulk Approval Safeguards**: What safeguards prevent accidental bulk approvals?
   - Recommendation: Confirmation modal for bulk actions affecting >5 items
   - Require at least brief feedback for bulk approvals
   - Decision needed from quality team

3. **Review Metrics Tracking**: Should we track individual moderator performance metrics?
   - Metrics could include: average review time, approval rate, speaker satisfaction
   - Privacy concern: metrics shouldn't create pressure to approve quickly
   - Decision needed from HR/management

4. **Rejection Appeal Process**: Can speakers appeal rejected submissions?
   - Current design: Rejection is final (very rare, usually "requires changes" instead)
   - Alternative: Appeal to senior organizer or event moderator
   - Decision needed from governance team

5. **Automated Quality Checks**: Should AI/ML pre-screen abstracts for common issues?
   - Could flag: excessive length, promotional language, missing key elements
   - Recommendation: Advisory only, not blocking (moderator has final say)
   - Technical feasibility assessment needed

6. **Multi-Event Moderators**: How to handle moderators assigned to multiple events?
   - Current design: Filter by event or view all
   - Alternative: Separate queues per event
   - UX testing needed for optimal workflow
