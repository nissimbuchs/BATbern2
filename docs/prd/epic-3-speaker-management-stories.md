# Epic 3: Core Speaker Management - Vertical Slice Stories

## Epic Overview

**Epic Goal**: Implement essential speaker workflow from invitation through material collection, providing the foundation for speaker coordination without requiring the full portal.

**Deliverable**: Complete speaker invitation, response tracking, and material collection workflow enabling organizers to manage speakers end-to-end.

**Architecture Context**:
- **Core Service**: Speaker Coordination Service (Java 21 + Spring Boot 3.2)
- **Integration**: Event Management Service for speaker assignments
- **Storage**: AWS S3 for speaker materials (presentations, photos, CVs)
- **Communication**: AWS SES for invitation and reminder emails
- **Frontend**: React components for organizer dashboard and basic speaker portal

**Duration**: 10 weeks (Weeks 21-30)

---

## Story 3.1: Speaker Invitation System (Workflow Step 3)

**User Story:**
As an **organizer**, I want to invite speakers efficiently through an automated system that handles bulk invitations and tracks responses, so that I can build event agendas quickly.

**Architecture Integration:**
- **Service**: Speaker Coordination Service
- **Email**: AWS SES with template management
- **Database**: PostgreSQL invitations and response tracking
- **Frontend**: React invitation management interface

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