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
A single BATbern gathering (e.g., "BATbern #45"). Each event has a unique event number, date, venue, and follows the 16-step workflow from planning to completion.

**Types**:
- **Full-Day Event**: 8+ hour event (typically 9 AM - 6 PM) with 10+ speakers
- **Afternoon Event**: 4-hour event (typically 1 PM - 5 PM) with 6-8 speakers
- **Evening Event**: 2-3 hour event (typically 6 PM - 9 PM) with 4-6 speakers

### Event Number
Unique sequential identifier for events (e.g., Event #45). Used throughout platform to reference specific events.

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
User with ORGANIZER role who plans and manages events. Can:
- Create and edit events (assigned to them)
- Advance workflow through 16 steps
- Manage speakers and partners
- Publish event details
- Cannot: Create users, delete events (ADMIN only)

### Admin
User with ADMIN role who has full platform access. Can:
- Everything ORGANIZER can do
- Create and manage users
- Delete any entity
- Override workflow validations
- Access all events (not just assigned)
- Modify system settings

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
16-step process for planning and executing events. Organized into 6 phases (A-F):
- **Phase A**: Setup (Steps 1-3)
- **Phase B**: Outreach (Steps 4-6)
- **Phase C**: Quality (Steps 7-8)
- **Phase D**: Assignment (Steps 9-10)
- **Phase E**: Publishing (Steps 11-12)
- **Phase F**: Communication (Steps 13-16)

See [Workflow Documentation](../workflow/) for complete details.

### Step
Individual stage within the workflow. Each step has:
- **Number** (1-16)
- **Name** (e.g., "Topic Selection")
- **Validation criteria** (requirements to advance)
- **Deadline** (optional, configurable)

### State / Status
Current condition of an entity. Examples:

**Event States**:
- `DRAFT` - Created but not started
- `IN_PROGRESS` - Active, progressing through workflow
- `PUBLISHED` - Public-facing information released
- `COMPLETED` - Event occurred, workflow finished
- `ARCHIVED` - Historical record, read-only

**Speaker States**:
- `IDENTIFIED` - Added to brainstorm list
- `INVITED` - Invitation sent
- `ACCEPTED` - Confirmed participation
- `REJECTED` - Declined invitation
- `CONTENT_SUBMITTED` - Materials uploaded
- `APPROVED` - Passed quality review
- `PUBLISHED` - Profile visible to attendees
- `WITHDRAWN` - Dropped out after accepting

### Validation
Rule that must be satisfied before advancing to next workflow step. Examples:
- "Minimum 8 speakers identified" (Step 3 → Step 4)
- "All invited speakers responded" (Step 5 → Step 6)
- "At least 10 speakers approved" (Step 8 → Step 9)

### Override
Admin action to bypass validation and advance workflow despite unmet criteria. Requires:
- Justification (reason for override)
- Admin role
- Audit trail (logged for review)

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

## Workflow Steps Reference

Quick reference for all 16 steps:

| Step | Name | Phase | Key Actions |
|------|------|-------|-------------|
| 1 | Event Setup | A - Setup | Define event type, date, venue |
| 2 | Topic Selection | A - Setup | Choose topics using heat map |
| 3 | Speaker Brainstorming | A - Setup | Identify 8-10+ potential speakers |
| 4 | Outreach Initiated | B - Outreach | Send speaker invitations |
| 5 | Status Management | B - Outreach | Track responses (Accepted/Rejected/Pending) |
| 6 | Content Collection | B - Outreach | Gather presentation materials |
| 7 | Quality Review | C - Quality | Evaluate speaker content |
| 8 | Threshold Validation | C - Quality | Ensure minimum approved speakers |
| 9 | Overflow Management | D - Assignment | Handle excess speakers (voting) |
| 10 | Slot Assignment | D - Assignment | Drag-and-drop speakers to time slots |
| 11 | Topic Publishing | E - Publishing | Publish event topics publicly |
| 12 | Speaker Publishing | E - Publishing | Publish speaker profiles publicly |
| 13 | Newsletter Creation | F - Communication | Draft attendee communications |
| 14 | Moderator Assignment | F - Communication | Assign session moderators |
| 15 | Catering Coordination | F - Communication | Finalize food/beverage |
| 16 | Partner Meetings | F - Communication | Coordinate sponsor logistics |

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
