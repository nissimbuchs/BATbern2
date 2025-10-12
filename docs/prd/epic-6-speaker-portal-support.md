# Epic 6: Speaker Portal & Support - DEFERRED

**Status:** 📦 **DEFERRED TO PHASE 2** (Week 26+)

**Reorganization Note:** This epic was formerly "Epic 3: Core Speaker Management" and now focuses on the self-service speaker portal and advanced features. Basic speaker CRUD is in Epic 2.

**Phase 1 Priority:** Epic 2 provides speaker entity CRUD. This epic adds invitation workflows, material submission portal, and communication hub.

---

## Epic Overview

**Epic Goal**: Deliver comprehensive self-service speaker portal with invitation workflows, material submission, performance dashboards, and communication hub, enabling speakers to manage their BATbern participation independently.

**Deliverable**: Complete speaker invitation, response tracking, and material collection workflow enabling organizers to manage speakers end-to-end.

**Architecture Context**:
- **Core Service**: Speaker Coordination Service (Java 21 + Spring Boot 3.2)
- **Integration**: Event Management Service for speaker assignments
- **Storage**: AWS S3 for speaker materials (presentations, photos, CVs)
- **Communication**: AWS SES for invitation and reminder emails
- **Frontend**: React components for organizer dashboard and basic speaker portal

**Duration**: 12 weeks (Weeks 21-32) - includes 2 weeks for Story 3.6 Notification Infrastructure

---

## Story 3.1: Speaker Invitation System (Workflow Step 3)

**User Story:**
As an **organizer**, I want to invite speakers efficiently through an automated system that handles bulk invitations and tracks responses, so that I can build event agendas quickly.

**Architecture Integration:**
- **Service**: Speaker Coordination Service
- **Email**: AWS SES with template management
- **Database**: PostgreSQL invitations and response tracking
- **Frontend**: React invitation management interface

**Wireframe Context:**

### Wireframe References
**From docs/wireframes/sitemap.md:**
- **Main Screen:** `docs/wireframes/story-3.1-speaker-matching-interface.md` ✅
  - Speaker Matching Interface with AI-powered recommendations
  - Speaker search and filtering capabilities
  - Invitation management and tracking
  - Bulk invitation operations

### UI Components
**Key interface elements:**
- **Speaker Search**: Search bar with autocomplete for speaker names, companies, expertise
- **Filter Panel**: Multi-criteria filters (expertise, company, past participation, availability)
- **Speaker Cards**: Grid/list view with speaker photos, names, companies, expertise tags
- **AI Matching Score**: Visual indicators showing speaker-topic fit percentage
- **Selection Controls**: Checkboxes for bulk selection, "Select All" option
- **Invitation Panel**: Right sidebar showing selected speakers for invitation
- **[Send Invitations] Button**: Triggers bulk invitation workflow
- **Status Indicators**: Visual badges showing invitation status (sent/opened/accepted/declined)
- **Speaker Profile Preview**: Quick view panel with speaker bio, speaking history
- **Company Affiliation**: Company logos and partner status badges

### Wireframe Status
- ✅ **EXISTS**: Speaker Matching Interface wireframe fully documented
  - Complete speaker discovery and selection interface
  - AI-powered matching recommendations
  - Bulk invitation management
  - Status tracking dashboard

### Navigation
**Key navigation paths from this screen:**
- → Speaker Profile Detail View (click speaker card)
- → Invitation Management Screen (manage sent invitations)
- → Company Management Screen (click company logo)
- → Event Detail/Edit (back to event being planned)
- ⤴ Event Management Dashboard

**Acceptance Criteria:**

**Invitation Management:**
1. **Invitation Creation**: Create invitations with event context and requirements
2. **Bulk Operations**: Send invitations to multiple speakers simultaneously
3. **Template System**: Reusable invitation templates with personalization
4. **Tracking**: Monitor invitation status (sent/opened/responded)

**Technical Implementation:**
5. **Invitation Entity**: Create Invitation aggregate with status tracking
6. **Email Integration**: SES templates with tracking pixels
7. **REST API**: POST /api/speakers/invitations endpoint
8. **React Component**: InvitationManager with bulk selection

**Definition of Done:**
- [ ] Bulk invitation system handles 50+ speakers
- [ ] Email templates personalized with speaker names
- [ ] Response tracking accurately captures status
- [ ] API supports batch operations efficiently
- [ ] Frontend provides intuitive invitation interface
- [ ] Integration test verifies end-to-end flow

---

## Story 3.2: Speaker Response Management (Workflow Step 4)

**User Story:**
As a **speaker**, I want to easily respond to invitations with my availability and any constraints, so that organizers can plan the event effectively.

**Architecture Integration:**
- **Frontend**: React response form (no authentication required)
- **Backend**: Speaker Coordination Service response processing
- **Database**: PostgreSQL speaker responses and constraints
- **Notifications**: Real-time updates to organizers

**Wireframe Context:**

### Wireframe References
**From docs/wireframes/sitemap.md:**
- **Main Screen:** `docs/wireframes/story-3.2-invitation-response.md` ✅
  - Invitation Response form accessible via unique link (no auth required)
  - Invitation details display (event, date, topic, organizer)
  - Response options (Accept/Decline/Tentative)
  - Availability and constraints form
  - Alternative date suggestions

### UI Components
**Key interface elements:**
- **Invitation Summary**: Event title, date, location, topic description
- **Response Options**: Large, clear radio buttons or cards for Accept/Decline/Tentative
- **Availability Form** (if Accept/Tentative):
  - Preferred date/time slots
  - Scheduling constraints
  - Technical requirements (AV, equipment)
  - Dietary restrictions (for catering)
- **Decline Form** (if Decline):
  - Optional reason for declining
  - Alternative speaker suggestions
  - Alternative date preferences
- **Contact Information**: Speaker can update contact details
- **Comments Box**: Free-form text for additional information
- **[Submit Response] Button**: Primary action to submit
- **Confirmation Message**: Success feedback after submission
- **Email Confirmation**: Notice that confirmation email sent

### Wireframe Status
- ✅ **EXISTS**: Invitation Response wireframe fully documented
  - Complete response form with all accept/decline/tentative paths
  - No-authentication unique link access
  - Real-time status updates to organizer
  - Constraint collection forms

### Navigation
**Key navigation paths from this screen:**
- → Confirmation Page (after submit)
- → Help/FAQ (if link provided)
- No navigation required (standalone unique link page)

**Acceptance Criteria:**

**Response Interface:**
1. **Response Form**: Simple form accessible via unique link
2. **Availability Options**: Accept/Decline/Tentative responses
3. **Constraint Collection**: Capture scheduling preferences, technical needs
4. **Confirmation**: Immediate confirmation of response submission

**Response Processing:**
5. **Status Updates**: Automatic status updates in organizer dashboard
6. **Tentative Handling**: Support for tentative responses with follow-up
7. **Decline Reasons**: Optional collection of decline reasons
8. **Alternative Suggestions**: Allow speakers to suggest alternatives

**Definition of Done:**
- [ ] Response form works without authentication
- [ ] All response types properly handled
- [ ] Organizers notified of responses in real-time
- [ ] Constraints properly stored and displayed
- [ ] Mobile-responsive response interface
- [ ] Response rate tracking implemented

---

## Story 3.3: Basic Material Submission Portal (Workflow Step 5)

**User Story:**
As a **speaker**, I want to submit my presentation materials through a simple portal, so that organizers have all required content for the event.

**Architecture Integration:**
- **Storage**: AWS S3 with presigned URLs for uploads
- **Backend**: Speaker Coordination Service validation
- **Database**: PostgreSQL submission tracking
- **Frontend**: React upload interface

**Wireframe Context:**

### Wireframe References
**From docs/wireframes/sitemap.md:**

1. **Material Submission Wizard:** `docs/wireframes/story-3.3-material-submission-wizard.md` ✅
   - Multi-step submission wizard guiding speakers through material upload
   - Step-by-step progress indicator
   - Required vs optional fields clearly marked
   - Metadata entry forms (title, abstract, bio, tags)
   - Draft auto-save functionality
   - Review and submit final step

2. **Presentation Upload:** `docs/wireframes/story-3.3-presentation-upload.md` ✅
   - Drag-and-drop file upload interface
   - Upload progress bar with percentage and ETA
   - File validation (type, size, format)
   - Thumbnail/preview generation
   - Version management (replace previous upload)
   - File metadata editor

### UI Components
**Key interface elements:**
- **Multi-Step Wizard**:
  - Step 1: Speaker Bio & Photo
  - Step 2: Presentation Details (title, abstract, learning objectives)
  - Step 3: File Upload (presentation, supporting materials)
  - Step 4: Review & Submit
  - Progress indicator showing current step
- **File Upload Panel**:
  - Drag-and-drop zone with visual feedback
  - Browse file button alternative
  - Upload progress bar (percentage, MB uploaded, ETA)
  - File validation messages (✓ Valid format, ⚠ File too large)
  - Preview thumbnail after upload
- **Form Fields**:
  - Title (required, max 200 chars)
  - Abstract (required, max 1000 chars, character counter)
  - Learning objectives (optional, bullet points)
  - Tags/keywords (optional, autocomplete)
  - Biography (required if not on file, rich text editor)
  - Professional photo upload (required, face detection validation)
- **Action Buttons**:
  - [Save Draft] - Auto-save with manual trigger
  - [Previous]/[Next] - Step navigation
  - [Preview] - See how materials will appear
  - [Submit for Review] - Final submission to moderator queue

### Wireframe Status
- ✅ **EXISTS**: Both wireframes fully documented and ready for implementation
  - Material Submission Wizard (multi-step guided workflow)
  - Presentation Upload (advanced file upload with progress tracking)
  - Integration with moderator review queue (Story 4.1)

### Navigation
**Key navigation paths from these screens:**
- Material Submission Wizard →
  - → Presentation Upload (step 3 of wizard)
  - → Preview Modal (click [Preview])
  - → Content Detail/Edit (view submitted materials)
  - → Speaker Dashboard (after submission)
- Presentation Upload →
  - Part of Material Submission Wizard Step 3
  - → Metadata Editor (after upload completes)
  - → Version History (if replacing file)

**Acceptance Criteria:**

**Submission Requirements:**
1. **Required Materials**:
   - Title and abstract (max 1000 chars)
   - CV/biography
   - Professional photo
   - Presentation file (PDF/PPTX)
2. **Validation**: Enforce all requirements before submission
3. **File Upload**: Support for large files (up to 100MB)
4. **Progress Tracking**: Show upload progress for large files

**Portal Features:**
5. **Draft Saving**: Auto-save progress to prevent data loss
6. **Preview**: Preview uploaded materials before final submission
7. **Edit Capability**: Update submissions until deadline
8. **Confirmation**: Email confirmation of successful submission

**Definition of Done:**
- [ ] All required materials can be uploaded
- [ ] Abstract validation enforces 1000 char limit
- [ ] Large file uploads work reliably
- [ ] Draft auto-save prevents data loss
- [ ] S3 integration secure with presigned URLs

---

## Story 3.4: Speaker Brainstorming & Assignment (Workflow Step 6)

**User Story:**
As an **organizer**, I want to brainstorm potential speakers and assign contact responsibilities, so that we can efficiently build a strong speaker lineup.

**Architecture Integration:**
- **Service**: Speaker Coordination Service
- **Database**: PostgreSQL speaker profiles and assignments
- **Cache**: Redis for speaker search and suggestions
- **Frontend**: React collaborative brainstorming interface

**Acceptance Criteria:**

**Brainstorming Tools:**
1. **Speaker Database**: Access to 500+ historical speaker profiles
2. **Expertise Matching**: Match speakers to event topics
3. **Collaborative Interface**: Multi-organizer brainstorming support
4. **Research Notes**: Shared notes about potential speakers

**Assignment Management:**
5. **Contact Assignment**: Assign organizers to specific speakers
6. **Workload Balancing**: Distribute contacts evenly
7. **Assignment Tracking**: Monitor who's contacting whom
8. **Handoff Process**: Support reassignment when needed

**Definition of Done:**
- [ ] Speaker database searchable and filterable
- [ ] Expertise matching provides relevant suggestions
- [ ] Multiple organizers can collaborate in real-time
- [ ] Assignments prevent duplicate contacts
- [ ] Workload visibility for all organizers
- [ ] Assignment history tracked for accountability

---

## Story 3.5: Speaker Outreach & Status Tracking (Workflow Step 6 continued)

**User Story:**
As an **organizer**, I want to track speaker outreach with automated reminders and status updates, so that I can ensure timely speaker confirmations and material collection.

**Architecture Integration:**
- **Workflow**: Spring State Machine for status transitions
- **Communication**: AWS SES for automated follow-ups
- **Database**: PostgreSQL status tracking with audit trail
- **Frontend**: React pipeline visualization

**Wireframe Context:**

### Wireframe References
**From docs/wireframes/sitemap.md:**
- **Main Screen:** `docs/wireframes/story-3.5-event-timeline.md` ✅
  - Event Timeline view showing key dates and milestones
  - Task list with speaker-specific deadlines
  - Status indicators for each timeline phase
  - Automated reminder schedule visualization

### UI Components
**Key interface elements:**
- **Timeline Visualization**: Gantt-style timeline showing event lifecycle
  - Key dates & milestones (invitation sent, response due, materials due, event date)
  - Current position indicator ("You are here")
  - Color-coded phases (planning, outreach, preparation, execution)
  - Countdown timers for upcoming deadlines
- **Task List Panel**: Speaker-specific tasks with status
  - [ ] Respond to invitation (due: 2 weeks)
  - [ ] Submit presentation materials (due: 4 weeks before event)
  - [ ] Confirm technical requirements (due: 2 weeks before event)
  - [ ] Complete pre-event checklist (due: 3 days before event)
  - Task status badges (pending, overdue, completed)
- **Status Dashboard**: Current speaker status summary
  - Overall status badge (On Track / At Risk / Overdue)
  - Completion percentage (e.g., "3/5 tasks completed")
  - Next action required with deadline
  - Risk indicators (⚠ Materials due in 3 days)
- **Communication Log**: History of automated reminders
  - Reminder sent dates
  - Email open/click tracking
  - Response timestamps
  - Manual follow-up notes
- **Event Details Panel**: Quick reference info
  - Event date, location, topic
  - Session time slot
  - Contact information for organizers

### Wireframe Status
- ✅ **EXISTS**: Event Timeline wireframe fully documented
  - Complete timeline visualization for speakers
  - Task list with deadlines and status tracking
  - Automated reminder integration
  - Risk detection and escalation indicators

### Navigation
**Key navigation paths from this screen:**
- → Material Submission Wizard (click task to complete)
- → Invitation Response (if not yet responded)
- → Event Details (speaker view)
- → Session Details (assigned session info)
- → Communication Hub (view all messages)
- ⤴ Speaker Dashboard

**Acceptance Criteria:**

**Status Management:**
1. **Speaker States**:
   - Open → Contacted → Ready → Accepted/Declined
   - Accepted → Slot Assigned → Final Agenda
2. **State Transitions**: Validated transitions with business rules
3. **Real-time Updates**: WebSocket updates for team visibility
4. **Audit Trail**: Complete history of all status changes

**Automation Features:**
5. **Follow-up Reminders**: Automated reminders for non-responses
6. **Material Deadlines**: Reminders 1 month before event
7. **Risk Detection**: Flag speakers at risk of missing deadlines
8. **Escalation**: Automatic escalation for overdue items

**Definition of Done:**
- [ ] Status tracking covers complete speaker lifecycle
- [ ] State transitions enforce business rules
- [ ] Real-time updates keep team synchronized
- [ ] Automated reminders reduce manual follow-up
- [ ] Risk detection identifies issues early
- [ ] Complete audit trail for compliance

---

## Story 3.6: Speaker Notification Infrastructure

**User Story:**
As an **organizer**, I want automated notifications for speaker invitations and deadline tracking with escalation rules, so that speaker coordination is efficient and deadlines are never missed.

**Architecture Integration:**
- **Service**: Notification Service (new microservice)
- **Email**: AWS SES for email delivery
- **Database**: PostgreSQL notification_log, email_templates, escalation_rules
- **Scheduler**: Spring @Scheduled tasks for deadline monitoring
- **Frontend**: React notification preference UI

**Acceptance Criteria:**

**Email Template Management:**
1. **Template CRUD**: Create, read, update, delete email templates
2. **Template Types**: Support for speaker_invitation, deadline_reminder_48h, deadline_reminder_24h, deadline_critical, material_received_confirmation
3. **Variable Substitution**: Support template variables ({{speakerName}}, {{eventTitle}}, {{deadline}}, etc.)
4. **Multilingual**: Templates in German and English
5. **Version Control**: Track template versions with rollback capability

**Deadline Monitoring & Escalation:**
6. **Deadline Tracking**: Monitor all speaker deadlines (invitation response, material submission)
7. **Escalation Tiers**: Tier 1 (48h before), Tier 2 (24h before), Tier 3 (deadline passed + escalate to backup organizer)
8. **Automatic Escalation**: If no response within threshold, escalate to backup organizer
9. **Escalation Dashboard**: Organizers see all active escalations with status

**Notification Delivery:**
10. **AWS SES Integration**: Send via SES with bounce/complaint handling
11. **Delivery Tracking**: Log all notifications with delivery status
12. **Retry Logic**: Retry failed deliveries with exponential backoff
13. **Rate Limiting**: Respect SES sending limits

**User Preferences:**
14. **Preference Management**: Users control notification frequency and channels
15. **Quiet Hours**: Respect user-configured quiet hours
16. **Opt-out**: GDPR-compliant opt-out with audit trail

**Definition of Done:**
- [ ] Email template CRUD operations functional
- [ ] AWS SES integration sending emails successfully
- [ ] Deadline monitoring detecting and triggering escalations
- [ ] All three escalation tiers functioning correctly
- [ ] Notification preferences respected for all users
- [ ] Delivery tracking and logging operational
- [ ] Template variables substituting correctly
- [ ] Multilingual templates working (German/English)
- [ ] Bounce and complaint handling implemented
- [ ] Escalation dashboard displaying real-time status
- [ ] >98% email delivery rate achieved
- [ ] Unit tests cover all notification scenarios
- [ ] Integration tests verify end-to-end notification flow

**Estimated Effort:** 2 weeks

**Dependencies:**
- AWS SES account configured and verified
- Database migrations for notification tables
- Email templates designed and approved

---

## Epic 3 Success Metrics

**Functional Success:**
- ✅ Complete speaker workflow from invitation to materials
- ✅ 90% speaker response rate to invitations
- ✅ Materials collected 1 month before events
- ✅ Support for 50+ speakers per event

**Technical Performance:**
- **Invitation Delivery**: 100% delivery rate via SES
- **Response Time**: <2 seconds for status updates
- **Upload Speed**: 10MB/s for material uploads
- **System Availability**: >99.5% uptime

**Business Value:**
- **Efficiency**: 70% reduction in speaker coordination time
- **Quality**: All materials validated against requirements
- **Transparency**: Real-time visibility for all organizers
- **Speaker Satisfaction**: Simple, efficient submission process

This epic provides the core speaker management functionality needed to run events, without requiring the full speaker portal features that come later in Epic 7.