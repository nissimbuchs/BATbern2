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
- [Portals](#portals)
- [Abbreviations & Acronyms](#abbreviations--acronyms)
- [Swiss-Specific Terms](#swiss-specific-terms)

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

### Waitlist
A queue for an event whose registrations have reached venue [capacity](#venue). Once capacity is enforced, further sign-ups join the waitlist instead of registering directly; if a spot frees up (e.g. through self-service deregistration), waitlisted attendees can be moved into the event. Introduced by the Epic 10 registration-lifecycle work.

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
Individual who presents at events. Speakers log in to the platform via **AWS Cognito** (email/password or "Continue with Google") to upload materials, view their schedule, and update their bio. Organizers can also manage speaker data on their behalf.

**Note**: Earlier releases used a passwordless **magic link** for the speaker portal. Epic 11 retired magic-link login entirely; speaker authentication is now Cognito-based, and a Cognito account is provisioned for a speaker at the `READY` workflow transition.

See also: [Speaker Portal](#speaker-portal), [SSO / "Continue with Google"](#sso--continue-with-google)

### Partner
Organization collaborating with or sponsoring BATbern events. Partners can log in to view their own attendance analytics, vote on session topics, and access meeting coordination. See [Partner Portal](#partner-portal).

### Attendee
Individual who registers for and attends events. The default platform role (`ATTENDEE`) — including users created via ["Continue with Google"](#sso--continue-with-google) — and sufficient for all Epic 7 contribution features. May use a platform account for:
- Registration management and self-service deregistration
- [Attendee event history](#workflow) (events registered for / attended)
- [Topics from the floor](#topics-from-the-floor--community-sourced-topic) and [speaker self-nomination](#speaker-self-nomination-i-could-speak-on-that)
- [Thank-the-organizers](#thank-the-organizers--appreciation-counter) and [post-event Q&A](#post-event-qa-window--freeze-the-apéro-continues)
- Event materials access

### Newsletter Subscriber
A person who has opted in to receive BATbern newsletters. Subscribers need not have a full platform account. The Epic 10 newsletter feature manages the subscriber list (subscribe/unsubscribe, including email-reply unsubscribe), template selection and sending, and list hygiene driven by SES bounce processing.

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

2. **Speaker Workflow** (8 states per speaker — see [8-State Speaker Workflow](#8-state-speaker-workflow)):
   - the unified 8-state model introduced by Epic 11; `SpeakerWorkflowService` is the sole status writer

3. **Task System** (4 states):
   - TODO → IN_PROGRESS → COMPLETED/CANCELLED

See [Workflow Documentation](../workflow/) for complete details on how these systems interact.

### Progressive Publishing
Staged release of event content to the public website, managed automatically:
1. **Speaker names** auto-published 30 days before event
2. **Full agenda** (sessions + slots) auto-published 14 days before event
3. Organizers can override and publish manually at any point

Triggered by the `AGENDA_FINALIZED` state; run by a scheduled job in the event-management-service.

### Phase
Conceptual grouping of workflow activities for documentation purposes:
- **Phase A**: Setup (Event creation, topic selection, speaker brainstorming)
- **Phase B**: Outreach (Speaker engagement, content collection)
- **Phase C**: Quality (Content review, quality scoring)
- **Phase D**: Assignment (Slot assignment, agenda finalization)
- **Phase E**: Lifecycle (Auto-publishing, auto-transitions, archival)
- **Phase F**: Tasks (Parallel task management system)

**Note**: Phases are documentation constructs, not workflow states. Events don't "transition between phases" - they transition between states.

### 8-State Speaker Workflow
The unified per-speaker lifecycle introduced by **Epic 11** (Unified Speaker Workflow Refactor, ADR-009), which reduced an earlier, larger state set to an **8-state model**. Key properties:
- `SpeakerWorkflowService` is the **single source of truth** and the only writer of speaker status.
- The workflow is unified inside the **event-management service** — the standalone `speakers` table and the separate speaker-coordination service references were dropped.
- A **Cognito account is provisioned at the `READY` transition**, replacing the retired [magic-link](#magic-link-retired) flow.
- Organizers drive transitions through the redesigned kanban drawer (guided drag).

See [Workflow](#workflow) for how the speaker workflow runs in parallel with the event workflow and task system.

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

**Speaker States** — see [8-State Speaker Workflow](#8-state-speaker-workflow). Epic 11 (ADR-009) consolidated the speaker lifecycle to an 8-state model; `SpeakerWorkflowService` is the sole writer of speaker status. The longer list of lowercase states below reflects the pre-Epic-11 model and is retained for historical reference:
- `identified` - Added to brainstorm list
- `contacted` - Invitation sent
- `ready` - Ready to accept/decline (Cognito account provisioned at this transition)
- `accepted` - Committed to presenting
- `declined` - Not available
- `content_submitted` - Materials uploaded
- `quality_reviewed` - Content approved
- `confirmed` - Quality reviewed AND slot assigned (auto-state)

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

### Cost-per-Attendee
Key performance metric in the Partner Analytics Dashboard. Calculated as:

```
Cost-per-Attendee = Total Partnership Cost ÷ Total Company Attendees
```

Partners see this metric across all historical events. Helps partners evaluate the ROI of their BATbern sponsorship relative to employee engagement.

### Engagement Metric
Measurement of partner interaction with BATbern platform:
- **Meeting Count** - Number of coordination meetings
- **Event Participation** - Events where partner had company attendees
- **Sponsorship Value** - Total financial contribution
- **Cost-per-Attendee** - Partnership cost divided by total employee attendees

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

### Topic Staleness Score
Computed metric indicating how long since a topic was last presented. Used in the Topic Heat Map to highlight topics that haven't appeared recently and may be due for revisiting.

**Calculation**: Based on recency and frequency of past use — a topic presented 5 years ago with no repeats scores higher than one presented last year.

### Content Collection
Phase of workflow (Step 6) where speakers upload presentation materials. Deadline-driven with reminders and escalations.

### Quality Review
Phase C of the organizer workflow where organizers evaluate speaker content against standards:
- **Technical Accuracy** - Content is factually correct
- **Relevance** - Aligns with event theme and audience
- **Quality** - Professional, well-structured, engaging
- **Length** - Fits time slot (typically 30-45 min presentation)

### Topics From the Floor / Community-Sourced Topic
A future-event topic suggested by a logged-in attendee (title + rationale) rather than by an organizer or partner. Such suggestions land in the existing topic-suggestion pool tagged `source = community` and surface with a **community badge** in the organizer triage UI. Introduced by Epic 7 (Story 7.1).

### Speaker Self-Nomination ("I Could Speak on That")
An Epic 7 (Story 7.2) feature: once an event's topic is set and the event is published, a logged-in attendee can nominate themselves to speak by submitting a session title + abstract. This creates a `speaker_pool` entry at the `IDENTIFIED` state for the organizer to triage through the normal [Speaker Workflow](#workflow). No Cognito account or SPEAKER role is provisioned at nomination time.

### Thank-the-Organizers / Appreciation Counter
An Epic 7 (Story 7.4) post-event feature letting attendees send a one-click thank-you, optionally with a note. Anonymous thank-yous are allowed (protected by [Turnstile](#turnstile) and rate-limiting); logged-in users are deduplicated to one per event. The aggregate count is shown publicly as an **appreciation counter**; free-text notes are visible to organizers.

### Post-Event Q&A Window / Freeze ("The Apéro Continues")
An Epic 7 (Story 7.5) per-session, time-boxed (~14-day, organizer-overridable) question-and-answer space that opens after an event. Logged-in attendees post questions and answers; organizers can take down posts and extend or close the window early. When the window closes, the Q&A **freezes** read-only and attaches permanently to the session's archive page. Reading frozen Q&A is public; posting is login-gated.

---

## Technical Terms

### Magic Link `[RETIRED]`
Historical: a passwordless authentication URL emailed to speakers (RS256-signed JWT, 30-day reusable session). Clicking the link logged the speaker into their portal without a username or password.

**Status**: Fully **retired** by Epic 11. Speaker authentication is now [AWS Cognito](#authentication--auth)-based (email/password or ["Continue with Google"](#sso--continue-with-google)). Old magic-link emails no longer authenticate. Retained here only to explain references in older releases.

### SSO / "Continue with Google"
**Single Sign-On** via Google. Users can sign in with their Google account through OIDC federation at `auth.batbern.ch` instead of an email/password. Delivered by Epic 12 (ADR-010).

**Capabilities**:
- **Transparent account linking** — a Google identity is linked to an existing account by matching email
- **[JIT provisioning](#jit-provisioning--just-in-time-provisioning)** — a new account is created on first SSO login
- **Terms-of-Service consent gate** — federated onboarding completion collects consent / company / newsletter preference
- **Avatar import** — the user's Google profile photo is imported
- **Runtime kill-switch** — `FEATURES_SSO_ENABLED` can disable SSO without redeploy

Apple / generic OIDC providers are deferred (Story 12-10).

### Federated Identity
An authentication model where a user's identity is asserted by an external identity provider (e.g. Google) and trusted by BATbern, rather than BATbern holding the credentials directly. See [SSO / "Continue with Google"](#sso--continue-with-google).

### JIT Provisioning / Just-in-Time Provisioning
Automatic creation of a BATbern user account at the moment a person first signs in via [SSO](#sso--continue-with-google), using the verified profile data from the identity provider. Avoids a separate manual account-creation step.

### Turnstile
Cloudflare Turnstile — a privacy-friendly, CAPTCHA-style bot-protection challenge. BATbern uses Turnstile to protect public submit flows (e.g. anonymous [thank-the-organizers](#thank-the-organizers--appreciation-counter)). The gateway can fail-open if Turnstile is disabled in config.

### ICS / Calendar Invite (RFC 5545)
Standard iCalendar file format (`.ics`) for calendar invitations, defined by RFC 5545. BATbern generates ICS files for partner meeting coordination.

**Each ICS file contains**:
- `VEVENT` for the partner coordination meeting (date, time, location, agenda)
- `VEVENT` for the linked BATbern event (if scheduled)

**Delivery**: Sent asynchronously via AWS SES to all partner contacts on record. Compatible with all major calendar clients (Outlook, Google Calendar, Apple Calendar).

### Layout Template
HTML email shell defining the brand frame around email content (header, footer, logo, typography, colors). Edited via Monaco (code editor) by administrators. Changes affect all emails that reference the template.

See also: [Content Template](#content-template)

### Content Template
Per-email WYSIWYG template defining the body text for a specific notification (e.g., "Speaker Invitation", "Registration Confirmation"). Edited via TinyMCE rich-text editor. 24 system templates seeded on startup; custom templates support full CRUD.

See also: [Layout Template](#layout-template)

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
- Email/password login
- ["Continue with Google"](#sso--continue-with-google) federated sign-on (Epic 12)
- Password reset
- Session management (8-hour sessions)
- MFA (multi-factor authentication) `[BACKLOG]`

### Authorization / Permissions
Rules determining what actions a user can perform. Based on:
- **Role** (ORGANIZER, ADMIN, SPEAKER, PARTNER, ATTENDEE)
- **Ownership** (assigned to specific event)
- **Resource** (which entities/features can be accessed)

**Role summary**:
- `ORGANIZER` — full platform access; manages all events, entities, and workflows
- `ADMIN` — user and company management; cannot create events
- `SPEAKER` — own portal only: respond to invitations, submit materials, view schedule
- `PARTNER` — own company data only: attendance analytics, topic voting, meeting coordination
- `ATTENDEE` — registration, materials download (post-event)

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

**Speaker Workflow** (parallel per speaker) — Epic 11 consolidated this to an [8-state model](#8-state-speaker-workflow); the table below retains the longer pre-Epic-11 state names for historical reference:

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

---

## Portals

### Speaker Portal
Self-service web interface for speakers, accessed with an **AWS Cognito** login (email/password or ["Continue with Google"](#sso--continue-with-google)). A Cognito account is provisioned for the speaker at the `READY` transition of the [8-state speaker workflow](#8-state-speaker-workflow). (Earlier releases used a passwordless [magic link](#magic-link-retired), now retired.)

**Capabilities**:
- Accept or decline speaking invitations
- Submit presentation materials (title, abstract, CV, photo, slides)
- View upcoming and past speaking engagements
- Track material submission status and deadlines

**Access**: `https://www.batbern.ch/speaker/` after Cognito login
**Languages**: English and German (i18n)
**Accessibility**: WCAG 2.1 AA compliant

See [Speaker Portal documentation](../speaker-portal/README.md) for full details.

### Partner Portal
Web interface for partner company users, accessible with standard Cognito login (PARTNER role).

**Capabilities**:
- Attendance Analytics Dashboard (per-event employee attendance + XLSX export)
- Topic Voting (suggest and vote for session topics)
- Meeting Coordination (access meeting details, receive ICS calendar invites)

**Scope**: Partners see only their own company's data.

See [Partner Portal documentation](../partner-portal/README.md) for full details.

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
| **ICS** | iCalendar | Calendar invite file format (RFC 5545) |
| **JWT** | JSON Web Token | Authentication token format (used for magic links) |
| **MFA** | Multi-Factor Authentication | Enhanced security (planned) |
| **MVP** | Minimum Viable Product | Initial feature release |
| **NPS** | Net Promoter Score | Recommendation metric (-100 to +100) |
| **OAuth** | Open Authorization | Authentication protocol |
| **OIDC** | OpenID Connect | Identity layer over OAuth 2.0 (used for Google SSO) |
| **JIT** | Just-in-Time (provisioning) | Auto-create account on first SSO login |
| **PDF** | Portable Document Format | Document standard |
| **PPTX** | PowerPoint XML | Presentation file format |
| **QA** | Quality Assurance | Testing and validation |
| **REST** | Representational State Transfer | API architecture style |
| **ROI** | Return on Investment | Value/cost metric |
| **S3** | Simple Storage Service | AWS file storage |
| **SPA** | Single-Page Application | Frontend architecture (React) |
| **SSO** | Single Sign-On | "Continue with Google" federated login (Epic 12, live) |
| **TDD** | Test-Driven Development | Development methodology |
| **UI/UX** | User Interface / User Experience | Design domains |
| **UID** | Unique Identifier | Swiss business ID number |
| **URL** | Uniform Resource Locator | Web address |
| **UUID** | Universally Unique Identifier | Database ID format |
| **WCAG** | Web Content Accessibility Guidelines | Accessibility standard (2.1 AA) |
| **WYSIWYG** | What You See Is What You Get | Rich-text editor style (TinyMCE) |
| **XLSX** | Excel Open XML Spreadsheet | Export format for analytics data |

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
