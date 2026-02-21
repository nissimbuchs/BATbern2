# Glossary

## Overview

Comprehensive glossary of terms used throughout the BATbern platform. Terms are organized alphabetically within categories for easy reference.

**Quick Jump**:
- [Events & Sessions](#events--sessions)
- [Users & Roles](#users--roles)
- [Workflow & States](#workflow--states)
- [Partners & Sponsors](#partners--sponsors)
- [Content & Topics](#content--topics)
- [Technical Terms](#technical-terms)

---

## Events & Sessions

### Event
A single BATbern gathering (e.g., "BATbern 2025"). Each event has a unique eventCode, date, venue, and progresses through a 9-state workflow from creation to archival.

**Types**:
- **FULL_DAY**: 8+ hour event (typically 9 AM - 6 PM) with 8-12 sessions
- **AFTERNOON**: 3-4 hour event (typically 2 PM - 6 PM) with 2-4 sessions
- **EVENING**: 2-3 hour event (typically 6 PM - 9 PM) with 1-2 sessions

### Event Code
Auto-generated public identifier for events (e.g., "BAT-2025-001"). Used in URLs, QR codes, and public-facing materials. Different from internal UUID.

### Session
Individual presentation or activity within an event. Each session has:
- **Speaker** (who presents)
- **Topic** (what is covered)
- **Time Slot** (when it occurs)
- **Duration** (typically 30-45 minutes for presentations)

### Slot
Specific time block within an event schedule. Speakers are assigned to slots during Step 10 (Slot Assignment).

**Example**:
```
Slot 1: 9:00 AM - 9:45 AM (45 min)
Slot 2: 9:45 AM - 10:30 AM (45 min)
Break: 10:30 AM - 10:45 AM
Slot 3: 10:45 AM - 11:30 AM (45 min)
```

### Venue
Physical location where event takes place. Includes:
- **Name** (e.g., "Kornhausforum Bern")
- **Address** (street, city, postal code)
- **Capacity** (maximum attendee count)
- **Facilities** (WiFi, projectors, accessibility)

---

## Users & Roles

### User
Individual with an account in the BATbern platform. Each user has:
- Email address (login ID)
- Full name
- Role (determines permissions)
- Company association (optional)

### Organizer
User with ORGANIZER role who plans and manages events. Has full system access including:
- Create and edit all events
- Manage all entities (users, companies, events, speakers, partners)
- Advance events through workflow states
- Manage speaker outreach and content review
- Assign time slots and publish agendas
- Create and manage tasks

**Note**: In BATbern, Organizers have complete administrative access to ensure operational flexibility.

### Speaker
Individual who presents at events. May or may not have platform user account.

**With Account**: Can log in to upload materials, view schedule, update bio
**Without Account**: Organizers manage all data on their behalf

### Attendee
Individual who registers for and attends events. May have platform account for:
- Registration management
- Event materials access
- Networking features `[PLANNED]`

### Company
Organization associated with users, speakers, or partners. Includes:
- **Name** (official company name)
- **Swiss UID** (Swiss business identifier, if applicable)
- **Logo** (brand image)
- **Address** (headquarters location)

---

## Workflow & States

### Workflow
BATbern uses **3 independent workflow systems** that operate in parallel:

1. **Event Workflow** (9 states):
   - CREATED → TOPIC_SELECTION → SPEAKER_IDENTIFICATION → SLOT_ASSIGNMENT → AGENDA_PUBLISHED → AGENDA_FINALIZED → EVENT_LIVE → EVENT_COMPLETED → ARCHIVED

2. **Speaker Workflow** (11 states per speaker):
   - identified → contacted → ready → accepted/declined → content_submitted → quality_reviewed → confirmed → overflow/withdrew

3. **Task System** (4 states):
   - TODO → IN_PROGRESS → COMPLETED/CANCELLED

See [Workflow Documentation](../workflow/) for complete details on how these systems interact.

### Phase
Conceptual grouping of workflow activities for documentation purposes:
- **Phase A**: Setup (Event creation, topic selection, speaker brainstorming)
- **Phase B**: Outreach (Speaker engagement, content collection)
- **Phase C**: Quality (Content review, quality scoring)
- **Phase D**: Assignment (Slot assignment, agenda finalization)
- **Phase E**: Lifecycle (Auto-publishing, auto-transitions, archival)
- **Phase F**: Tasks (Parallel task management system)

**Note**: Phases are documentation constructs, not workflow states. Events don't "transition between phases" - they transition between states.

### State / Status
Current condition of an entity. Uses lowercase snake_case in database, UPPERCASE in some UI displays.

**Event States** (9 states):
- `CREATED` - Event configured, ready for topic selection
- `TOPIC_SELECTION` - Topics being selected
- `SPEAKER_IDENTIFICATION` - Speakers being identified and progressing through their own workflows
- `SLOT_ASSIGNMENT` - Quality-reviewed speakers being assigned to time slots
- `AGENDA_PUBLISHED` - Agenda ready for finalization
- `AGENDA_FINALIZED` - Locked agenda, ready for event execution
- `EVENT_LIVE` - Event currently happening (auto-transition on event day)
- `EVENT_COMPLETED` - Event finished (auto-transition after event ends)
- `ARCHIVED` - Historical record, read-only

**Speaker States** (11 states):
- `identified` - Added to brainstorm list
- `contacted` - Invitation sent
- `ready` - Ready to accept/decline
- `accepted` - Committed to presenting
- `declined` - Not available
- `content_submitted` - Materials uploaded
- `quality_reviewed` - Content approved
- `confirmed` - Quality reviewed AND slot assigned (auto-state)
- `overflow` - Accepted but no slot available
- `withdrew` - Dropped out after accepting

**Task States** (4 states):
- `TODO` - Not started
- `IN_PROGRESS` - Currently working
- `COMPLETED` - Finished
- `CANCELLED` - No longer needed

### Validation
Business logic rules enforced during state transitions. Examples:
- Event must have selected topics before advancing from TOPIC_SELECTION to SPEAKER_IDENTIFICATION
- Speaker must have content_submitted before advancing to quality_reviewed
- Tasks must have assignees before moving to IN_PROGRESS

### Override
Organizer action to bypass validation constraints. Common use cases:
- Archive event in any state (use "Override Workflow Validation" checkbox)
- Manually change speaker state outside normal workflow (for exceptional cases)
- Skip certain validation steps when business requirements change

**Note**: All overrides are logged for audit purposes.

---

## Partners & Sponsors

### Partner
Organization that collaborates with or sponsors BATbern events. Types:
- **Platinum Partner** - Highest tier (>CHF 50,000/year)
- **Gold Partner** - Mid-high tier (CHF 20,000-50,000/year)
- **Silver Partner** - Mid tier (CHF 10,000-20,000/year)
- **Bronze Partner** - Entry tier (CHF 5,000-10,000/year)
- **Community Partner** - Non-financial collaboration

### Partner Directory
Centralized list of all partners with:
- Tier badges (visual indicators)
- Company logos
- Contact information
- Engagement history
- ROI metrics

### Partner Contact
Individual associated with partner organization. Roles:
- **Primary Contact** - Main point of communication
- **Billing Contact** - Handles invoicing and payments
- **Event Contact** - Coordinates event-specific logistics

### Engagement Metric
Measurement of partner interaction with BATbern platform:
- **Meeting Count** - Number of coordination meetings
- **Event Participation** - Events where partner had booth/presence
- **Sponsorship Value** - Total financial contribution
- **ROI Score** - Calculated return on investment

---

## Content & Topics

### Topic
Subject area covered in a presentation. Examples:
- "Sustainable Building Materials"
- "BIM Implementation Strategies"
- "Timber Construction Innovation"

Topics are standardized (controlled vocabulary) for consistency and analytics.

### Topic Backlog
Collection of all possible topics for consideration. Used during Step 2 (Topic Selection) to choose topics for upcoming event.

### Topic Heat Map
Data visualization showing historical topic frequency and recency. Helps organizers:
- Identify overused topics (avoid repetition)
- Discover underrepresented areas (coverage gaps)
- Make data-driven topic selection decisions

See [Heat Maps Feature](../features/heat-maps.md) for details.

### Content
Materials associated with a session or speaker:
- **Presentation Slides** (PDF, PPTX)
- **Handouts** (supplementary documents)
- **Speaker Bio** (professional background)
- **Headshot** (speaker photo)

### Content Collection
Phase of workflow (Step 6) where speakers upload presentation materials. Deadline-driven with reminders and escalations.

### Quality Review
Phase of workflow (Steps 7-8) where organizers evaluate speaker content against standards:
- **Technical Accuracy** - Content is factually correct
- **Relevance** - Aligns with event theme and audience
- **Quality** - Professional, well-structured, engaging
- **Length** - Fits time slot (typically 30-45 min presentation)

---

## Technical Terms

### Presigned URL
Time-limited, permission-scoped URL for direct file uploads to AWS S3. Provides:
- Security (15-minute expiration)
- Performance (no backend proxy)
- Scalability (handles high concurrent upload volume)

See [File Uploads](../features/file-uploads.md) for details.

### S3 / AWS S3
Amazon Simple Storage Service - cloud storage for files (logos, presentations, documents). Provides:
- Durability (99.999999999% - eleven nines)
- Availability (99.99%)
- Scalability (unlimited storage)
- CDN integration (fast global delivery)

### CDN / CloudFront
Content Delivery Network - distributes files geographically for fast access worldwide. BATbern uses AWS CloudFront for:
- Logo delivery (<50ms latency)
- Presentation downloads
- Event images

### Authentication / Auth
Process of verifying user identity. BATbern uses AWS Cognito for:
- Username/password login
- Password reset
- Session management (8-hour sessions)
- MFA (multi-factor authentication) `[PLANNED]`

### Authorization / Permissions
Rules determining what actions a user can perform. Based on:
- **Role** (ORGANIZER, ADMIN, SPEAKER, ATTENDEE)
- **Ownership** (assigned to specific event)
- **Resource** (which entities/features can be accessed)

### Session / Session Token
Cryptographic token proving user is authenticated. Properties:
- **Duration**: 8 hours with activity, 2 hours idle timeout
- **Storage**: Browser (secure httpOnly cookie)
- **Scope**: Includes user ID, role, permissions
- **Expiry**: Automatic logout, requires re-authentication

### API / REST API
Application Programming Interface - how frontend communicates with backend. BATbern uses RESTful APIs:
- `GET /api/events` - Retrieve event list
- `POST /api/speakers` - Create new speaker
- `PUT /api/companies/:id` - Update company
- `DELETE /api/users/:id` - Delete user

### OpenAPI / Swagger
Standardized format for documenting APIs. BATbern maintains OpenAPI specifications at `/docs/api/*.openapi.yml`.

### Migration / Database Migration
Versioned database schema changes. Managed by Flyway:
- Sequential version numbers (V001, V002, ...)
- Applied automatically on deployment
- Reversible (rollback capability)

### Environment
Isolated platform instance. BATbern has:
- **Local** - Developer machines (Docker or native)
- **Staging** - Pre-production testing (matches production config)
- **Production** - Live platform (app.batbern.ch)

### Deployment
Process of releasing new code to an environment:
- **CI/CD** - Continuous Integration / Continuous Deployment (automated)
- **Rollback** - Revert to previous version if issues found
- **Blue/Green** - Zero-downtime deployment strategy

---

## Workflow State Reference

Quick reference for all workflow states:

**Event Workflow** (9 states - linear progression):

| State | Key Activities | Next State |
|-------|----------------|------------|
| CREATED | Event configured with basic info | TOPIC_SELECTION |
| TOPIC_SELECTION | Select topics using heat map, auto-creates 4 tasks | SPEAKER_IDENTIFICATION |
| SPEAKER_IDENTIFICATION | Speakers progress through their own workflow (identified → confirmed) | SLOT_ASSIGNMENT |
| SLOT_ASSIGNMENT | Assign quality-reviewed speakers to time slots | AGENDA_PUBLISHED |
| AGENDA_PUBLISHED | Agenda ready, auto-creates "Newsletter: Speakers" task | AGENDA_FINALIZED |
| AGENDA_FINALIZED | Finalized agenda, auto-creates 2 tasks, triggers 14-day auto-publish | EVENT_LIVE |
| EVENT_LIVE | Event currently executing (auto-transition on event day) | EVENT_COMPLETED |
| EVENT_COMPLETED | Event finished (auto-transition after event ends) | ARCHIVED |
| ARCHIVED | Historical record, read-only | Terminal |

**Speaker Workflow** (11 states - parallel per speaker):

| State | Meaning | Common Transitions |
|-------|---------|-------------------|
| identified | Added to brainstorm list | contacted |
| contacted | Invitation sent | ready |
| ready | Can accept/decline | accepted, declined |
| accepted | Committed to presenting | content_submitted |
| declined | Not available | Terminal |
| content_submitted | Materials uploaded | quality_reviewed |
| quality_reviewed | Content approved | confirmed (when slot assigned) |
| confirmed | Quality + slot both done | Terminal (success) |
| overflow | Accepted but no slot | May become confirmed if slot opens |
| withdrew | Dropped out | Terminal |

**Task System** (4 states):

| State | Meaning | Transition |
|-------|---------|------------|
| TODO | Not started, may be overdue (red) | IN_PROGRESS |
| IN_PROGRESS | Currently working | COMPLETED, CANCELLED |
| COMPLETED | Finished with notes | Terminal |
| CANCELLED | No longer needed | Terminal |

---

## Abbreviations & Acronyms

| Abbreviation | Full Term | Context |
|--------------|-----------|---------|
| **AC** | Acceptance Criteria | User story requirements |
| **API** | Application Programming Interface | Backend communication |
| **AWS** | Amazon Web Services | Cloud infrastructure |
| **BATbern** | Berner Architekten Treffen | Event organization name |
| **CDN** | Content Delivery Network | File distribution (CloudFront) |
| **CDK** | Cloud Development Kit | Infrastructure as Code (AWS) |
| **CI/CD** | Continuous Integration/Deployment | Automated deployment pipeline |
| **CORS** | Cross-Origin Resource Sharing | Browser security policy |
| **CRUD** | Create, Read, Update, Delete | Basic data operations |
| **CSAT** | Customer Satisfaction Score | Survey metric (0-5 scale) |
| **CSV** | Comma-Separated Values | Data export format |
| **DDD** | Domain-Driven Design | Software architecture pattern |
| **E2E** | End-to-End | Full workflow testing |
| **GDPR** | General Data Protection Regulation | Privacy compliance |
| **JWT** | JSON Web Token | Authentication token format |
| **MFA** | Multi-Factor Authentication | Enhanced security (planned) |
| **MVP** | Minimum Viable Product | Initial feature release |
| **NPS** | Net Promoter Score | Recommendation metric (-100 to +100) |
| **OAuth** | Open Authorization | Authentication protocol |
| **PDF** | Portable Document Format | Document standard |
| **PPTX** | PowerPoint XML | Presentation file format |
| **QA** | Quality Assurance | Testing and validation |
| **REST** | Representational State Transfer | API architecture style |
| **ROI** | Return on Investment | Value/cost metric |
| **S3** | Simple Storage Service | AWS file storage |
| **SPA** | Single-Page Application | Frontend architecture (React) |
| **SSO** | Single Sign-On | Enterprise authentication |
| **TDD** | Test-Driven Development | Development methodology |
| **UI/UX** | User Interface / User Experience | Design domains |
| **UID** | Unique Identifier | Swiss business ID number |
| **URL** | Uniform Resource Locator | Web address |
| **UUID** | Universally Unique Identifier | Database ID format |

---

## Swiss-Specific Terms

### Swiss UID (Unternehmens-Identifikationsnummer)
Swiss business identification number. Format: `CHE-XXX.XXX.XXX`

**Validation Rules**:
- Must start with "CHE-"
- Followed by 9 digits (3 groups of 3, separated by periods)
- Last digit is check digit (validated by algorithm)

**Example**: CHE-123.456.788 (788 is check digit)

### Berner Architekten Treffen (BATbern)
"Bern Architects' Meeting" - quarterly professional development events for architects, engineers, and building professionals in Bern region.

### Kornhausforum
Common BATbern event venue in Bern, Switzerland. Historical building with modern conference facilities.

---

## Related Resources

- **[Feature Status](feature-status.md)** - Implementation status of platform features
- **[Keyboard Shortcuts](keyboard-shortcuts.md)** - Quick command reference
- **[Changelog](changelog.md)** - Platform version history

---

**Back to Main**: Return to [Documentation Home](../README.md) →
