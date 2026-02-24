# Epic 6: Speaker Self-Service Portal (Enhancement Layer)

**Status:** ✅ **100% COMPLETE** (All Stories Deployed to Staging)

**Implementation Complete:** All 6 stories (6.0-6.5) have been implemented and deployed to staging. The speaker self-service portal is now fully operational with complete automation and accessibility compliance.

**Deployment Timeline:**
- **2026-02-06:** Stories 6.0-6.3, 6.5 deployed (PR #430, Build Pipeline #21559151095)
- **2026-02-16:** Story 6.4 implementation complete with WCAG 2.1 AA accessibility (QA review)

**Architecture:** Speaker portal functionality implemented in `event-management-service` with magic link authentication, invitation workflow, response handling, content submission, dashboard, and automated reminders.

---

## Epic Overview

**Epic Goal**: Provide self-service portal for speakers to reduce organizer workload by enabling speakers to independently respond to invitations, submit materials, and manage their participation.

**Deliverable**: Optional speaker self-service portal that enhances the organizer-driven workflow (Epic 5) with speaker autonomy, reducing manual organizer tasks by ~40%.

**Architecture Context**:
- **Core Service**: Speaker Coordination Service (Java 21 + Spring Boot 3.2)
- **Integration**: Enhances Epic 5 workflows (Stories 5.3-5.5)
- **Storage**: AWS S3 for speaker materials (presentations, photos, CVs)
- **Communication**: AWS SES for invitation and reminder emails
- **Frontend**: React speaker portal components

**Duration**: 10.5 weeks (Weeks 42-52.5, after Epic 5 complete) - includes Story 6.0 prerequisite (2.5 weeks)

**What Changed:**
- **Epic 5 First**: Organizer-driven workflow operational before Epic 6
- **Enhancement Layer**: Epic 6 adds self-service options, not core functionality
- **Backward Compatible**: Organizers can still upload materials on behalf of speakers
- **Reduced Scope**: Focus on high-value self-service features only

---

## Why Epic 6 is Optional

### Epic 5 Provides Complete Workflow Without Epic 6

| Workflow Step | Epic 5 (Organizer-Driven) | Epic 6 Enhancement (Optional) |
|---------------|---------------------------|-------------------------------|
| **Step 4: Speaker Outreach** | Organizer manually contacts speaker, records contact | Speaker receives automated invitation email |
| **Step 5: Speaker Status** | Organizer manually updates status after conversation | Speaker self-updates status via response link |
| **Step 6: Content Collection** | Organizer uploads CV, photo, abstract on speaker's behalf | Speaker self-submits via portal |

### Value Proposition of Epic 6

**Without Epic 6 (Epic 5 Only):**
- ✅ Organizers manually handle all speaker coordination
- ✅ Organizers collect materials via email/shared drives
- ✅ Organizers upload materials on behalf of speakers
- ❌ Higher organizer workload
- ❌ More manual data entry
- ❌ Slower material collection

**With Epic 6 Enhancement:**
- ✅ Speakers self-respond to invitations (reduces organizer follow-up)
- ✅ Speakers self-submit materials (reduces organizer data entry)
- ✅ Automated reminders reduce organizer coordination time
- ✅ ~40% reduction in organizer workload
- ✅ Faster material collection (1 week average vs 3 weeks manual)

### Implementation Decision

**Decision (2026-02-03):** Epic 6 Phase 1 & 2 implemented and deployed. Core speaker self-service functionality is now live on staging.

---

## Epic 6 Stories (Enhancement Features)

### Story 6.0: Speaker Coordination Service Foundation + API Consolidation (PREREQUISITE)
**(Moved from Epic 2, Story 2.3 - formerly Story 1.19, includes 1.15a.3 Speakers API Consolidation)**

**Status:** ✅ Complete (deployed to staging 2026-02-03)

**User Story:**
As an **organizer**, I want the foundational Speaker Coordination Service with consolidated RESTful APIs, so that I can manage speaker profiles efficiently and enable self-service features in Epic 6.

**Why This is Story 6.0:**
Speaker Coordination Service was moved from Epic 2 to Epic 6 because:
1. **Better Cohesion**: All speaker-related functionality consolidated in Epic 6
2. **Epic 5 Independence**: Epic 5 organizer workflows don't require speaker service (organizers manage speakers manually in notes/spreadsheets)
3. **Direct Dependency**: Stories 6.1-6.5 (speaker self-service) all depend on this foundation
4. **Reduced Epic 2 Scope**: Epic 2 focuses on core operational entities (Company, User, Event, Partner)

**Architecture Integration:**
- **Service**: `speaker-coordination-service/` (Java 21 + Spring Boot 3.2)
- **Database**: PostgreSQL with speaker domain schema
- **Storage**: S3 for speaker photos and CVs
- **Cache**: Caffeine for speaker session data (10min TTL for expanded resources)
- **API Foundation**: Uses Story 1.15a utilities for filtering, sorting, pagination
- **HTTP Clients**: Integration with User/Company services (ADR-003 pattern)

**Key Functionality:**
1. Speaker CRUD operations with consolidated API patterns
2. Speaker profile management (name, bio, expertise, company affiliation)
3. Speaker-event associations tracking
4. Speaker photo and CV storage with S3 presigned URLs
5. **Resource Expansion**: `?include=events,sessions,companies` for speaker history in single call
6. **Advanced Search**: Filter speakers by expertise, company, past participation
7. **Performance**: Speaker detail with history <300ms P95
8. **Domain Events**: SpeakerCreatedEvent, SpeakerUpdatedEvent, SpeakerInvitedEvent

**Acceptance Criteria:**

**Backend Service:**
- [ ] Speaker aggregate with DDD patterns (Speaker entity, SpeakerRepository)
- [ ] SpeakerController with CRUD endpoints
- [ ] SpeakerService with business logic
- [ ] **Consolidated REST API** implementing Story 1.15a.3 patterns:
  - `GET /api/v1/speakers?filter={}&include=events,sessions,companies&sort=name&page=1&size=20`
  - `GET /api/v1/speakers/{speakerId}?include=events,sessions,companies`
  - `POST /api/v1/speakers` (create speaker profile)
  - `PATCH /api/v1/speakers/{speakerId}` (update speaker)
  - `DELETE /api/v1/speakers/{speakerId}` (soft delete)
  - `GET /api/v1/speakers/search?q={query}` (autocomplete search)
- [ ] OpenAPI documentation for all endpoints
- [ ] Database schema with Flyway migrations (speakers, speaker_events, speaker_materials tables)
- [ ] S3 integration for photos/CVs with presigned URL upload pattern (ADR-002)
- [ ] **HTTP Client Integration**: UserServiceClient, CompanyServiceClient for enrichment (ADR-003)
- [ ] **API Consolidation**: Support `?include=events,sessions,companies,topics,materials` for resource expansion
- [ ] **Advanced Search**: Filter by expertise, company, availability, past_events with JSON filter syntax
- [ ] **Performance**: List <100ms, detail <150ms, detail+includes <300ms (all P95)
- [ ] Domain events publishing to EventBridge (SpeakerCreatedEvent, SpeakerUpdatedEvent)
- [ ] Integration tests covering all workflows including consolidated APIs
- [ ] Extends `ch.batbern.shared.test.AbstractIntegrationTest` from testFixtures
- [ ] @MockBean pattern for HTTP clients (NOT WireMock)

**Architecture Compliance:**
- [ ] OpenAPI spec imports shared-kernel types (NO local ErrorResponse/PaginationMetadata)
- [ ] Controllers implement generated API interfaces from openApiGenerate task
- [ ] Exceptions extend shared-kernel hierarchy (SpeakerNotFoundException extends NotFoundException)
- [ ] GlobalExceptionHandler uses ch.batbern.shared.dto.ErrorResponse
- [ ] Inject DomainEventPublisher from shared-kernel
- [ ] Use FilterParser, SortParser, IncludeParser, FieldSelector from shared-kernel
- [ ] NO cross-service database joins (stores username/companyName, not UUIDs)

**Testing Requirements:**
- [ ] Unit tests with >85% coverage
- [ ] Integration tests with PostgreSQL via Testcontainers
- [ ] HTTP client mocking via @MockBean with Mockito
- [ ] Performance tests verify <300ms P95 for detail+includes
- [ ] OpenAPI contract tests verify spec matches implementation

**Estimated Duration:** 2.5 weeks (includes API consolidation implementation + HTTP client integration)

**References:**
- Original story: Epic 2, Story 2.3
- Core functionality: `docs/prd/epic-1-foundation-stories.md` Story 1.19
- API consolidation: `docs/stories/1.15a.3.speakers-api-consolidation.md`
- ADR-003: `docs/architecture/ADR-003-meaningful-identifiers-public-apis.md`
- Event-management-service: Reference implementation for HTTP clients with JWT propagation
- Partner-coordination-service: Reference implementation for HTTP enrichment pattern

**Important:** This story must be completed BEFORE Stories 6.1-6.5, as they all depend on the Speaker Coordination Service foundation.

---

### Story 6.1: Automated Speaker Invitation System ✅

**Status:** ✅ Complete (deployed to staging 2026-02-03)
- `SpeakerInvitationController.java` - send invitation emails with magic links

**User Story:**
As an **organizer**, I want to send automated speaker invitations with unique response links, so that speakers can self-respond without manual follow-up.

**Enhancement Over Epic 5:**
- Epic 5: Organizer manually contacts speaker and records response
- Epic 6: Automated email with unique link for self-service response

**Architecture Integration:**
- **Service**: Speaker Coordination Service
- **Email**: AWS SES with template management
- **Database**: PostgreSQL invitations and response tracking
- **Frontend**: React invitation management + speaker response interface

**Acceptance Criteria:**

**Automated Invitations:**
1. **Template System**: Pre-defined invitation email templates with variable substitution
2. **Unique Links**: Generate unique response link per speaker (no authentication required)
3. **Bulk Operations**: Send invitations to multiple speakers simultaneously
4. **Tracking**: Monitor invitation status (sent/opened/responded)
5. **Personalization**: Include speaker name, event details, deadlines

**Integration with Epic 5:**
6. **Hybrid Workflow**: Organizer can still manually record responses (Epic 5) if speaker doesn't use portal
7. **Status Sync**: Self-service responses update same status tracking as Epic 5 manual updates
8. **Backward Compatible**: System works with or without speaker self-service

**Definition of Done:**
- [ ] Automated email invitations sent via AWS SES
- [ ] Unique response links work without authentication
- [ ] Organizer can still manually record responses (Epic 5 workflow)
- [ ] Status tracking syncs between self-service and manual updates
- [ ] Bulk invitation system handles 50+ speakers
- [ ] Integration test verifies end-to-end flow

**Estimated Duration:** 2 weeks

---

### Story 6.2: Speaker Self-Service Response Portal ✅

**Status:** ✅ Complete (deployed to staging 2026-02-03)
- `SpeakerPortalTokenController.java` - magic link validation
- `SpeakerPortalResponseController.java` - accept/decline/tentative responses

**User Story:**
As a **speaker**, I want to respond to invitations via a simple web form, so that I can confirm my participation without email back-and-forth with organizers.

**Enhancement Over Epic 5:**
- Epic 5: Speaker responds via email/phone, organizer manually updates status
- Epic 6: Speaker self-updates status via web form

**Architecture Integration:**
- **Frontend**: React response form (no authentication required, accessed via unique link)
- **Backend**: Speaker Coordination Service response processing
- **Database**: PostgreSQL speaker responses and constraints
- **Notifications**: Real-time updates to organizers

**Wireframe Reference:**
- `docs/wireframes/story-3.2-invitation-response.md` ✅

**Acceptance Criteria:**

**Response Interface:**
1. **Response Form**: Simple form accessible via unique link from invitation email
2. **Availability Options**: Accept/Decline responses *(Tentative removed from UI 2026-02-11; API still supports for backward compat)*
3. **Constraint Collection**: Optional message to organizer *(scheduling preferences, technical needs simplified out of UI 2026-02-11)*
4. **Confirmation**: Immediate confirmation of response submission

**Response Processing:**
5. **Auto Status Update**: Response automatically updates speaker status in Epic 5 workflow
6. **Organizer Notification**: Organizers notified in real-time of speaker responses
7. ~~**Tentative Handling**: Support for tentative responses with follow-up~~ *Deferred from UI 2026-02-11; backend supports tentative for future re-enablement*
8. **Decline Reasons**: Optional collection of decline reasons

**Hybrid Operation:**
9. **Manual Override**: Organizer can still manually update status if speaker doesn't use portal
10. **Conflict Resolution**: If speaker self-responds after organizer manual update, show warning

**Definition of Done:**
- [ ] Response form works without authentication via unique link
- [ ] All response types (Accept/Decline) properly handled in UI *(Tentative: backend only)*
- [ ] Status automatically updates in Epic 5 speaker status dashboard
- [ ] Organizers notified of responses in real-time
- [ ] Organizer can override speaker response if needed
- [ ] Mobile-responsive response interface
- [ ] Integration test verifies status sync between self-service and manual

**Estimated Duration:** 1.5 weeks

---

### Story 6.3: Speaker Material Self-Submission Portal ✅

**Status:** ✅ Complete (deployed to staging 2026-02-03)
- `SpeakerPortalContentController.java` - title, abstract, material submission
- `SpeakerPortalProfileController.java` - profile updates (bio, photo, CV)

**User Story:**
As a **speaker**, I want to upload my presentation materials through a self-service portal, so that organizers don't have to manually collect and upload my content.

**Enhancement Over Epic 5:**
- Epic 5: Speaker emails materials to organizer, organizer uploads to system
- Epic 6: Speaker directly uploads materials to system via portal

**Architecture Integration:**
- **Storage**: AWS S3 with presigned URLs for uploads
- **Backend**: Speaker Coordination Service validation
- **Database**: PostgreSQL submission tracking
- **Frontend**: React upload interface (accessed via unique link or speaker dashboard)

**Wireframe References:**
- `docs/wireframes/story-3.3-material-submission-wizard.md` ✅
- `docs/wireframes/story-3.3-presentation-upload.md` ✅

**Acceptance Criteria:**

**Self-Service Upload:**
1. **Material Wizard**: Multi-step wizard for title, abstract, CV, photo, presentation upload
2. **File Upload**: Drag-and-drop interface with progress tracking
3. **Validation**: Enforce abstract length (1000 char max), file size, format requirements
4. **Preview**: Speaker can preview how materials will appear
5. **Draft Saving**: Auto-save progress to prevent data loss

**Integration with Epic 5:**
6. **Hybrid Workflow**: Organizer can still upload materials on speaker's behalf (Epic 5)
7. **Review Queue**: Materials flow to same moderator review queue as Epic 5 (Story 5.6)
8. **Organizer Notification**: Notify organizer when speaker submits materials
9. **Approval Workflow**: Organizer reviews and approves before publication (same as Epic 5)

**Definition of Done:**
- [ ] Speaker can self-submit all required materials (title, abstract, CV, photo, presentation)
- [ ] Materials upload to S3 using presigned URLs
- [ ] Abstract validation enforces 1000 char limit
- [ ] Organizer can still upload materials on speaker's behalf (Epic 5 workflow)
- [ ] Submitted materials appear in moderator review queue (Story 5.6)
- [ ] Organizer notified when speaker submits materials
- [ ] Draft auto-save prevents data loss
- [ ] Integration test verifies hybrid workflow (both self-service and organizer upload)

**Estimated Duration:** 2.5 weeks

---

### Story 6.4: Speaker Dashboard (View-Only) ✅

**Status:** ✅ Complete (implementation complete 2026-02-16, QA PASSED — 98/100 quality score)

**User Story:**
As a **speaker**, I want to view my upcoming and past BATbern presentations, so that I can see my speaking history and event details.

**Architecture Integration:**
- **Authentication**: Simple email-based authentication (no account creation)
- **Backend**: Speaker Coordination Service
- **Frontend**: React speaker dashboard

**Acceptance Criteria:**

**Dashboard Features:**
1. **Upcoming Events**: List of events speaker is confirmed for with session details
2. **Past Events**: Historical speaking engagements with event dates, topics
3. **Material Status**: Show submission status for upcoming events
4. **Event Details**: View event date, location, session time, topic
5. **Contact Information**: Organizer contact details for questions

**Authentication:**
6. **Email-Based Access**: Magic link authentication (no password required)
7. **Session Management**: 30-day session expiration
8. **No Account Creation**: Speaker doesn't need to create account

**Definition of Done:**
- [x] Speaker can access dashboard via magic link (email-based auth)
- [x] Dashboard shows upcoming and past events
- [x] Material submission status displayed
- [x] Speaker can view but not edit event details
- [x] 30-day session management working
- [x] Integration test verifies dashboard functionality
- [x] WCAG 2.1 AA accessibility compliance (ARIA labels, keyboard navigation, semantic HTML)
- [x] i18n complete (EN/DE)
- [x] QA gate file created with PASS status (98/100 quality score)

**Estimated Duration:** 1.5 weeks

---

### Story 6.5: Automated Deadline Reminders ✅

**Status:** ✅ Complete (deployed to staging 2026-02-06)

**User Story:**
As an **organizer**, I want automated deadline reminders sent to speakers, so that I don't have to manually follow up on material submission deadlines.

**Enhancement Over Epic 5:**
- Epic 5: Organizer manually reminds speakers about deadlines
- Epic 6: Automated email reminders sent at configurable intervals

**Architecture Integration:**
- **Scheduler**: Spring @Scheduled tasks for deadline monitoring
- **Email**: AWS SES for reminder delivery
- **Database**: PostgreSQL reminder_log, reminder_rules
- **Frontend**: React reminder configuration UI

**Acceptance Criteria:**

**Reminder Configuration:**
1. **Reminder Rules**: Configure reminder schedule (e.g., 1 month before, 2 weeks before, 3 days before)
2. **Template Management**: Pre-defined reminder email templates
3. **Escalation Tiers**: Tier 1 (friendly reminder), Tier 2 (urgent), Tier 3 (escalate to organizer)
4. **Per-Event Configuration**: Different rules for different event types

**Automated Sending:**
5. **Deadline Detection**: Monitor all speaker deadlines (material submission, response deadlines)
6. **Auto-Send**: Automatically send reminders based on configured rules
7. **Tracking**: Log all reminders sent with delivery status
8. **Deduplication**: Don't send reminder if materials already submitted

**Integration with Epic 5:**
9. **Manual Override**: Organizer can disable automated reminders for specific speakers
10. **Manual Reminders**: Organizer can still manually contact speaker (Epic 5 workflow)
11. **Unified Tracking**: Automated and manual reminders both logged in communication history

**Definition of Done:**
- [ ] Reminder rules configurable per event type
- [ ] Automated reminders sent based on deadline proximity
- [ ] Reminders not sent if materials already submitted
- [ ] Organizer can disable automated reminders for specific speakers
- [ ] All reminders logged in communication history
- [ ] Integration test verifies reminder scheduling and delivery

**Estimated Duration:** 1.5 weeks

---

## Epic 6 Success Metrics

**Adoption Metrics:**
- **Self-Service Rate**: 70%+ of speakers use self-service portal
- **Response Time**: Speakers respond 2x faster via portal vs email (3 days vs 7 days average)
- **Material Submission**: Materials submitted 1 week faster on average (1 week vs 3 weeks)

**Organizer Efficiency:**
- **Workload Reduction**: 40% reduction in manual speaker coordination tasks
- **Time Savings**: 3 hours saved per event on average
- **Data Entry**: 80% reduction in manual data entry

**Technical Performance:**
- **Invitation Delivery**: 100% delivery rate via SES
- **Response Time**: <2 seconds for status updates
- **Upload Speed**: 10MB/s for material uploads
- **System Availability**: >99.5% uptime

**Business Value:**
- **Speaker Satisfaction**: Simplified submission process increases speaker participation
- **Quality**: All materials validated against requirements (same as Epic 5)
- **Transparency**: Real-time visibility for all organizers (same as Epic 5)
- **Scalability**: System handles increased event frequency without proportional organizer workload increase

---

## Implementation Considerations

### Prerequisites

**Must Complete First:**
- ✅ Epic 5 complete and operational (all 16 workflow steps working with organizer-driven approach)
- ✅ Organizer feedback collected on Epic 5 workflows
- ✅ ROI analysis confirms Epic 6 value justifies development cost
- ⚠️ **Story 6.0 (Speaker Coordination Service)** must be complete before Stories 6.1-6.5

### Backward Compatibility

**Critical Requirement:** Epic 6 must not break Epic 5 organizer-driven workflows.

- Organizers can still manually handle all tasks even if Epic 6 deployed
- Hybrid mode: Some speakers use self-service, others use organizer-driven
- No forced migration: Organizers choose when/if to enable self-service per speaker

### Rollout Strategy

**Recommended Approach:**
1. **Phase 0**: Implement Story 6.0 - Speaker Coordination Service Foundation (Week 42-44.5)
2. **Phase 1**: Deploy Stories 6.1-6.5 to single event as pilot (Week 45-46)
3. **Phase 2**: Gather feedback, iterate on UX (Week 47)
4. **Phase 3**: Gradual rollout to all events (Week 48-52.5)
5. **Phase 4**: Make self-service default, organizer-driven fallback (Phase 3+)

---

## Relationship to Epic 5

| Aspect | Epic 5 (Organizer-Driven) | Epic 6 (Self-Service Enhancement) |
|--------|---------------------------|-----------------------------------|
| **Status** | Required - Core Workflow | Optional - Efficiency Enhancement |
| **Timeline** | Weeks 27-41 | Weeks 42-49 (after Epic 5) |
| **Dependency** | Independent | Depends on Epic 5 complete |
| **Speaker Outreach** | Manual contact by organizer | Automated invitation emails |
| **Response Tracking** | Organizer records status manually | Speaker self-updates via portal |
| **Content Collection** | Organizer uploads on behalf | Speaker self-submits |
| **Deadline Management** | Manual organizer follow-up | Automated reminders |
| **Workload** | Higher organizer burden | 40% reduction via self-service |
| **Fallback** | N/A | Epic 5 workflows if portal not used |

---

## Decision Point: Build Epic 6?

**After Epic 5 Complete, Evaluate:**

**Build Epic 6 If:**
- ✅ Organizers report high workload coordinating speakers
- ✅ Event frequency increasing (more events = more coordination time)
- ✅ Speaker feedback requests self-service option
- ✅ Development resources available for 8-week project

**Defer Epic 6 If:**
- ❌ Epic 5 organizer-driven workflow sufficient
- ❌ Low event frequency (limited workload savings)
- ❌ Other features higher priority
- ❌ Limited development resources

**Recommendation:** Gather data from Epic 5 operation (3-6 months) before committing to Epic 6.
