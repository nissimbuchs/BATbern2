# BATbern Event Management Platform - Enhanced PRD

## 1. Project Analysis and Context

### Enhancement Type
✅ **Complete Platform Rewrite** + ✅ New Feature Addition + ✅ UI/UX Overhaul

### Enhancement Description
Complete rewrite of BATbern as a comprehensive event management platform using React frontend with Java/Spring Boot backend, transforming from static conference website into dynamic event ecosystem with automated workflows, real-time analytics, and intelligent content management.

### Impact Assessment
✅ **Revolutionary Impact** (complete architectural transformation)

### Goals
- Automate the complex 16-step event planning workflow
- Eliminate manual speaker coordination and material collection
- Provide real-time ROI analytics for partners
- Provide searchable content archive across 20+ years of conference content
- Transform static website into dynamic event management platform

### Strategic Decisions
- **Technology Stack**: React frontend + Java/Spring Boot backend
- **Approach**: Complete platform rewrite (revolutionary transformation)
- **Implementation**: Custom solution for all components
- **Sequence**: Foundation → Event Timeline → Speaker Portal → Partner Analytics

## 2. Requirements

### Functional Requirements

**FR1**: The platform shall provide role-based authentication with distinct interfaces for organizers, speakers, partners, and attendees

**FR2**: Event organizers shall manage the complete 16-step event workflow through intelligent task sequencing, automated deadline tracking, stakeholder coordination, and real-time progress dashboards with cross-role visibility

**16-Step Detailed Event Workflow:**
1. **Topic Selection & Event Type Definition** - Select topic from backlog, define event type (Full-day: 6-8 slots, Afternoon: 6-8 slots, Evening: 3-4 slots)
2. **Speaker Brainstorming & Research** - Identify potential speakers for topic
3. **Speaker Assignment & Contact Strategy** - Define organizer-to-speaker contact distribution
4. **Speaker Outreach & Initial Contact** - Send invitations with context, deadlines, requirements
5. **Speaker Status Tracking** - Track: open → contacted → ready → declined/accepted → slot-assigned → final agenda
6. **Speaker Content Collection** - Collect title, abstract (max 1000 chars), CV, photo (1 month before)
7. **Content Quality Review** - Moderator reviews abstracts/materials for standards compliance
8. **Minimum Threshold Check** - Wait until minimum slots filled before assignment
9. **Speaker Selection & Overflow Management** - Organizer voting on topic fit when more speakers than slots
10. **Speaker-to-Slot Assignment** - Assign speakers considering preferences, technical needs, topic flow optimization
11. **Progressive Publishing Engine** - Topic immediate, speakers 1 month before with continuous updates
12. **Agenda Finalization** - 2 weeks before: finalize agenda, handle dropouts via overflow speaker list
13. **Newsletter Distribution** - Send progressive then final agenda to mailing list
14. **Moderation Assignment** - Assign event moderator to the event
15. **Catering Coordination** - Adds a task to contact Caterer for a menue 1 month before
16. **Partner Meeting Coordination** - Organizers organize Spring and Autumn partner meetings at lunchtime on same day as BATbern event to discuss BATbern budgets, event statistics and brainstorm on topics for upcoming events, that are then stored on a backlog for voting and topic selection by the organizers.

**FR3**: The system shall provide automated speaker invitation, submission, and material collection workflows with real-time status updates

**FR4**: Partners shall access analytics dashboards showing employee attendance of their company for the past events

**FR5**: The platform shall support progressive event publishing with automatic content updates from topic definition through final agenda publication

**FR6**: Attendees shall access a prominent current event landing page featuring the upcoming BATbern event with complete logistics (date, location, free attendance), speaker lineup, agenda details, and registration functionality

**FR7**: The system shall integrate email notification workflows for event updates, speaker deadlines, and newsletter distribution, supported by a comprehensive email template management system that allows organizers to create, customize, and version email templates with variable substitution, multilingual support, and A/B testing capabilities for different stakeholder groups (speakers, attendees, partners, organizers)

**FR8**: Partner users shall vote on topic priorities and submit strategic topic suggestions outside formal partner meetings

**FR10**: Speaker users shall access self-service portal for submission management, agenda viewing, and presentation upload

**FR11**: The system shall maintain complete event archive with presentation downloads, speaker profiles, and photo galleries accessible via secondary navigation

**FR12**: Event organizers shall manage multi-year venue reservations, catering coordination, partner meeting scheduling, and logistics through integrated workflow tools with automated reminders and stakeholder notifications

**FR13**: [REMOVED - Strategic refocus per Sprint Change Proposal 2025-10-01]

**FR14**: Attendees shall manage personal engagement through newsletter subscriptions, content bookmarking, and presentation downloads with personalized preferences, including granular notification preference controls allowing users to opt in/out of specific notification types (event updates, speaker announcements, partner communications, system alerts) across multiple channels (email, in-app, push notifications) with frequency management and quiet hours settings

**FR15**: The platform shall provide mobile-optimized attendee experience with offline content access, event check-in capabilities, and progressive web app functionality

**FR16**: [REMOVED - Strategic refocus per Sprint Change Proposal 2025-10-01]

**FR17**: The system shall provide intelligent speaker matching and assignment tracking with automated workflow states (open → contacted → ready → declined/accepted → slot-assigned → final agenda) and real-time organizer collaboration including slot preference collection, technical requirement tracking, and overflow management with organizer voting mechanisms and separate overflow speaker tracking

**FR18**: Event organizers shall access smart topic backlog management with visual heat map representation showing topic usage frequency over time, ML-powered similarity scoring to identify duplicate or similar topics with automated avoidance warnings, staleness detection algorithms that calculate recommended wait periods before topic reuse based on historical patterns and partner influence metrics, and intelligent duplicate avoidance that prevents organizers from selecting recently used or semantically similar topics

**FR19**: The platform shall implement progressive publishing engine that automatically validates content readiness (moderator quality review, abstract length limits) and publishes event information in phases (topic immediate → speakers 1 month prior → progressive agenda updates → final agenda → post-event materials) with quality control checkpoints and automated content standards enforcement

**FR20**: Event organizers shall receive intelligent notification system with role-based alerts, cross-stakeholder visibility, and automated escalation workflows for deadline management and task coordination, implementing multi-tier escalation rules (reminder → warning → critical → escalation to backup organizer) with configurable timing thresholds, automatic fallback notification paths, priority-based delivery guarantees, and real-time escalation status dashboards with audit trails

**FR21**: The system shall support long-term planning capabilities including multi-year venue booking workflows, seasonal partner meeting coordination, and strategic budget planning with automated scheduling and reminder systems

**FR22**: Event organizers shall manage user roles with capabilities to promote users to speaker or organizer roles, demote users with approval workflows for organizer demotions, and enforce business rules requiring minimum 2 organizers per event, maintaining complete audit trails of all role changes

### Non-Functional Requirements

**NFR1**: Platform shall provide responsive design optimized for mobile, tablet, and desktop access

**NFR2**: Database shall support full-text search across all historical content with sub-second response times

**NFR3**: System shall integrate with external services (email, payment processing, file storage) through configurable APIs

**NFR4**: Platform shall support multi-language content (German, English) with internationalization framework

### Email & Notification Infrastructure Requirements

**EIR1**: The system shall integrate AWS Simple Email Service (SES) for transactional and marketing email delivery with the following specifications:
- **Configuration**: Production account with verified domain (batbern.ch) and DKIM/SPF/DMARC setup
- **Throughput**: Support for 50,000 emails/day with burst capacity to 200,000/day
- **Deliverability**: Maintain >98% delivery rate with bounce and complaint handling
- **Templates**: Support for SES template versioning and variable substitution
- **Monitoring**: CloudWatch metrics for delivery, bounces, complaints, and reputation
- **Compliance**: GDPR-compliant unsubscribe handling and data retention policies

**EIR2**: The notification system shall provide multi-channel delivery infrastructure supporting email (via AWS SES), in-app notifications (via WebSocket), and future extensibility for SMS (via AWS SNS) and push notifications (via Firebase Cloud Messaging)

**EIR3**: Email template management system shall support HTML/text dual-format emails, inline CSS processing, responsive design testing tools, preview functionality across email clients, and version control with rollback capabilities

### Compatibility Requirements

**CR1**: New platform shall migrate all existing event data (54+ events, presentations, speaker profiles) without data loss

**CR2**: System shall maintain existing external integrations (Slideshare, Twitter, venue websites) during transition period

**CR3**: Platform shall preserve existing URL structure for SEO and bookmark compatibility where feasible

**CR4**: New system shall export data in standard formats for partner analytics and reporting tools

## 3. Technical Architecture and Implementation

### Architecture Overview
The BATbern Event Management Platform follows a comprehensive **Domain-Driven Design microservices architecture** as detailed in our [Architecture Documentation](./architecture/index.md).

### Technology Stack
- **Frontend**: React 18.2+ with TypeScript, Material-UI, Zustand + React Query
- **Backend**: Java 21 LTS with Spring Boot 3.2+, PostgreSQL, Redis
- **Infrastructure**: AWS (ECS Fargate, Cognito, S3, CloudFront)
- **Development**: Gradle, Vite, GitHub Actions, AWS CDK

### Architectural Components
The platform is organized into four distinct bounded contexts with dedicated services:

1. **Event Management Domain** - Organizer workflows and automation
2. **Speaker Coordination Domain** - Speaker portal and workflows
3. **Partner Analytics Domain** - ROI tracking and strategic input
4. **Attendee Experience Domain** - Content discovery and registration

### Detailed Architecture References
For comprehensive technical implementation details, refer to the following architecture documents:

- **[System Overview](./architecture/01-system-overview.md)** - C4 model and component relationships
- **[Infrastructure & Deployment](./architecture/02-infrastructure-deployment.md)** - AWS deployment strategy and CI/CD
- **[Data Architecture](./architecture/03-data-architecture.md)** - Domain models and database schemas
- **[API Design](./architecture/04-api-design.md)** - REST API specifications and workflows
- **[Frontend Architecture](./architecture/05-frontend-architecture.md)** - React component patterns and PWA
- **[Backend Architecture](./architecture/06-backend-architecture.md)** - Service patterns and security
- **[Development Standards](./architecture/07-development-standards.md)** - Coding standards and setup
- **[Operations & Security](./architecture/08-operations-security.md)** - Security and performance requirements

### Integration Constraints
- **Data Migration**: Complete migration of 54+ historical events from existing JSON structure
- **External Integrations**: Maintain compatibility with Slideshare, venue websites, and partner systems
- **Performance**: Sub-second search response across 20+ years of content
- **Security**: Role-based access control with AWS Cognito integration
- **Scalability**: Multi-tenant architecture supporting concurrent user sessions

## 4. Epic Structure and Sprint Planning

### Epic Approach
**Vertical Slice Strategy**: Seven epics delivering end-to-end functionality, each providing demonstrable value to users while building progressively on the platform foundation.

### Sprint Planning Overview

**Setup Phase**: Sprint 0 (2 weeks)
- Project setup, environment configuration, team alignment
- Architecture finalization with architect
- Development environment preparation

**Epic Timeline (REVISED - CRUD-First Strategy)**:
- **Epic 1**: Weeks 1-9 (Foundation & Essential Infrastructure)
- **Epic 2**: Weeks 10-17 (Entity CRUD & Domain Services)
- **Epic 3**: Weeks 18-20 (Historical Data Migration)
- **Epic 4**: Weeks 21-25 (Public Website & Content Discovery)
- **Epic 5**: Weeks 26+ (Enhanced Organizer Workflows - DEFERRED)
- **Epic 6**: Weeks 26+ (Speaker Portal & Support - DEFERRED)
- **Epic 7**: Weeks 26+ (Attendee Experience Enhancements - DEFERRED)
- **Epic 8**: Weeks 26+ (Partner Coordination - DEFERRED)

**Reorganization Rationale**: Epic structure revised to prioritize functional delivery (CRUD, data migration, public website) before workflow automation. This accelerates public website launch from Week 38+ to Week 25 while preserving all functional requirements.

### 4.2 Content Management & Storage Architecture

The BATbern platform manages diverse content types including company logos, speaker photos and CVs, event presentations, and historical archives. This section defines the storage architecture, CDN strategy, and content policies.

#### AWS S3 Storage Strategy

**Bucket Architecture:**
- **Environment Separation**: Separate S3 buckets per environment (development, staging, production)
- **Bucket Naming**: `batbern-{environment}-{content-type}` (e.g., `batbern-prod-presentations`, `batbern-prod-logos`)
- **Content Types**:
  - `presentations`: Speaker presentation files (PDF, PPTX)
  - `logos`: Company and partner logos (PNG, JPG, SVG)
  - `profiles`: Speaker photos and CVs (JPG, PNG, PDF)
  - `archives`: Historical event materials and photo galleries

**S3 Key Structure:**
```
/{content-type}/{year}/{entity-id}/{filename-with-uuid}
Examples:
  /presentations/2024/evt-123/speaker-456-presentation-a7b3c9d2.pdf
  /logos/2024/company-789/logo-f3e8d1a4.png
  /profiles/2024/speaker-456/photo-b2c9e3f1.jpg
```

**Lifecycle Policies:**
- **Active Content**: S3 Standard storage class for content < 1 year old
- **Historical Content**: Transition to S3 Standard-IA after 1 year
- **Archive Content**: Transition to S3 Glacier after 3 years
- **Retention**: Indefinite retention for all event-related content

**Security & Access Control:**
- **Private Buckets**: All buckets private by default, no public access
- **Presigned URLs**: Time-limited presigned URLs for downloads (15-minute expiration)
- **Upload Authentication**: All uploads require valid JWT with appropriate role
- **Server-Side Encryption**: AES-256 encryption at rest (S3-SSE)
- **Versioning**: Enabled for accidental deletion protection

#### CloudFront CDN Configuration

**Distribution Strategy:**
- **Global CDN**: CloudFront distribution per environment for fast worldwide delivery
- **Origin**: S3 buckets as CloudFront origins with Origin Access Identity (OAI)
- **Caching**: Aggressive caching for static content (TTL: 7 days)
- **Custom Domain**: `cdn.batbern.ch`, `cdn-staging.batbern.ch`, `cdn-dev.batbern.ch`

**Performance Optimization:**
- **Edge Locations**: AWS global edge network for low-latency delivery
- **Compression**: Automatic gzip/brotli compression for text-based files
- **HTTP/2**: Enabled for multiplexing and reduced latency
- **Image Optimization**: Lambda@Edge for automatic image resizing and format conversion

**Security Headers:**
- **HTTPS Only**: Redirect HTTP to HTTPS
- **Security Headers**: CORS, CSP, X-Frame-Options, X-Content-Type-Options
- **Signed URLs**: Optional signed URLs for sensitive content

#### File Size and Format Constraints

**Per Content Type:**

| Content Type | Max Size | Allowed Formats | Validation |
|--------------|----------|-----------------|------------|
| Company Logo | 5 MB | PNG, JPG, SVG | Dimensions: 500x500 to 2000x2000 |
| Speaker Photo | 10 MB | JPG, PNG | Dimensions: min 800x800, aspect ratio 1:1 or 4:3 |
| Speaker CV | 5 MB | PDF | Max 10 pages |
| Presentation | 100 MB | PDF, PPTX | Virus scan required |
| Event Photo | 20 MB | JPG, PNG | Max 4000x4000 |

**Upload Requirements:**
- **Multipart Upload**: Files > 10 MB use multipart upload for reliability
- **Progress Tracking**: Real-time upload progress for files > 1 MB
- **Virus Scanning**: ClamAV integration for all uploaded files
- **Checksum Verification**: SHA-256 checksum validation on upload completion

#### Storage Quota Policies Per Role

**Role-Based Quotas:**

| Role | Total Storage Quota | Max Files | Max File Size | Notes |
|------|---------------------|-----------|---------------|-------|
| Organizer | Unlimited | Unlimited | 100 MB | Full platform administration |
| Speaker | 200 MB | 20 files | 100 MB | Presentation + supporting materials |
| Partner | 50 MB | 5 files | 5 MB | Company logo + marketing materials |
| Attendee | 10 MB | 5 files | 5 MB | Profile photo + bookmarked content |

**Quota Enforcement:**
- **Pre-Upload Check**: Validate available quota before upload
- **Soft Limits**: Warning at 80% quota usage
- **Hard Limits**: Upload rejection at 100% quota
- **Quota Increase**: Manual approval process for speakers exceeding limits

#### Backup and Disaster Recovery

**Backup Strategy:**
- **Cross-Region Replication**: Replicate production bucket to secondary region (eu-west-1 → eu-central-1)
- **Versioning**: S3 versioning enabled for 30-day rollback capability
- **Daily Snapshots**: S3 bucket inventory reports for disaster recovery planning
- **Backup Testing**: Quarterly restore drills to validate backup integrity

**Disaster Recovery:**
- **RTO (Recovery Time Objective)**: 4 hours for full content restoration
- **RPO (Recovery Point Objective)**: 1 hour maximum data loss
- **Failover**: Automatic CloudFront failover to secondary region on origin failure
- **Restoration Process**: Documented restoration procedures with runbooks

**Data Integrity:**
- **Checksum Validation**: SHA-256 checksums stored in database for integrity verification
- **Periodic Audits**: Monthly automated integrity checks comparing S3 and database records
- **Orphan Detection**: Identify and cleanup orphaned S3 objects without database references

### Detailed Epic Implementation

The comprehensive epic breakdown follows a CRUD-first approach, prioritizing functional entity management and public website delivery before workflow automation:

**Phase 1: Foundation & Core Functionality (Weeks 1-25)**
- **[Epic 1: Foundation & Essential Infrastructure](./prd/epic-1-foundation-stories.md)** - Platform foundation with authentication, monitoring, security (9 weeks)
- **[Epic 2: Entity CRUD & Domain Services](./prd/epic-2-entity-crud-domain-services.md)** - End-to-end CRUD for Event, Speaker, Company, User entities (8 weeks)
- **[Epic 3: Historical Data Migration](./prd/epic-3-historical-data-migration.md)** - Migrate 20+ years of BATbern event data (3 weeks)
- **[Epic 4: Public Website & Content Discovery](./prd/epic-4-public-website-content-discovery.md)** - Public event pages, registration, historical archive browsing (5 weeks)

**Phase 2: Enhanced Workflows & Advanced Features (Weeks 26+, DEFERRED)**
- **[Epic 5: Enhanced Organizer Workflows](./prd/epic-5-enhanced-organizer-workflows.md)** - 16-step event planning automation with intelligent features (18 weeks, DEFERRED)
- **[Epic 6: Speaker Portal & Support](./prd/epic-6-speaker-portal-support.md)** - Self-service speaker portal with advanced material management (8 weeks, DEFERRED)
- **[Epic 7: Attendee Experience Enhancements](./prd/epic-7-attendee-experience-enhancements.md)** - Personal engagement, mobile PWA, offline access (6 weeks, DEFERRED)
- **[Epic 8: Partner Coordination](./prd/epic-8-partner-coordination.md)** - Analytics dashboards, topic voting, meeting coordination (4 weeks, DEFERRED)

Each epic document contains:
- Detailed user stories with architecture alignment
- Technical acceptance criteria
- Integration requirements
- Performance metrics
- Definition of done criteria
- Clear deliverables demonstrating value

---

## Success Metrics

### Phase 1: Foundation & Core Functionality (Weeks 1-25)

**Epic 1 Success (Foundation - Week 9)**
- User authentication operational for all roles (Organizer, Speaker, Partner, Attendee)
- API Gateway routing to all microservices with <50ms overhead
- CI/CD pipeline with automated deployments and <10min build time
- Infrastructure monitoring with <5min MTTD (Mean Time To Detection)
- Essential security controls and GDPR compliance operational
- Platform foundation ready for feature development

**Epic 2 Success (Entity CRUD - Week 17)**
- All entity CRUD operations functional with <200ms P95 response time
- Company management with Swiss UID validation operational
- Event CRUD with type support (full-day, afternoon, evening)
- Speaker profiles and basic coordination working
- User role management with promotion/demotion workflows
- React frontend with role-adaptive navigation deployed

**Epic 3 Success (Data Migration - Week 20)**
- 100% historical data migrated successfully (54+ events)
- All speaker profiles and presentations migrated to S3
- Company relationships established in Company Management Service
- Data integrity validation 100% pass rate
- Search indexes built for content discovery
- Migration completed in <4 hours total time

**Epic 4 Success (Public Website - Week 25)**
- Public event landing page live with current/upcoming events
- Event registration flow functional (3-step wizard with QR code)
- Historical archive browsing operational (20+ years of content)
- Full-text search across events, speakers, presentations working
- Mobile-responsive design with <2.5s Largest Contentful Paint
- >99.5% uptime for public-facing pages

### Phase 2: Enhanced Workflows & Advanced Features (Weeks 26+, DEFERRED)

**Epic 5 Success (Enhanced Organizer Workflows)**
- Event creation to publication in <30 minutes
- 16-step workflow automation operational with intelligent task sequencing
- Progressive publishing engine with automated content updates
- Topic selection with ML-powered duplicate detection
- Automated deadline tracking and escalation

**Epic 6 Success (Speaker Portal & Support)**
- 90% speaker invitation acceptance rate
- Materials collected 1 month before events via self-service portal
- Speaker dashboard with performance metrics operational
- Communication hub with real-time messaging functional
- Advanced material management with version control

**Epic 7 Success (Attendee Experience Enhancements)**
- Personal engagement dashboard with bookmarks and preferences
- Mobile PWA with offline capabilities functional
- Granular notification preferences respected across all channels
- Content recommendation engine providing relevant suggestions
- User satisfaction scores >4.5/5 for attendee experience

**Epic 8 Success (Partner Coordination)**
- Partner topic voting adoption by 100% of partners
- Analytics dashboards showing employee attendance and engagement
- Efficient meeting coordination with automated scheduling
- Topic suggestions influencing event planning decisions
- Partner satisfaction with strategic influence mechanisms

### Overall Platform Success
- Successful migration of 20+ years of event content
- Adoption by all stakeholder groups (organizers, speakers, partners, attendees)
- Measurable improvement in event planning efficiency
- Enhanced partner engagement and satisfaction
- Increased community participation and content discovery

## Next Steps

1. **Architecture Review**: Finalize technical approach with architect
2. **Sprint 0 Planning**: Setup development environment and project structure
3. **Team Formation**: Assemble development team with Java/Spring Boot and React expertise
4. **Stakeholder Alignment**: Validate approach with organizers, key speakers, and partners
5. **MVP Planning**: Detailed story breakdown and sprint planning for Epic 1