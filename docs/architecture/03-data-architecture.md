# Data Architecture

This document outlines the comprehensive data model and database design for the BATbern Event Management Platform.

**Database Architecture** (CRITICAL):
- **Single PostgreSQL database**: `batbern` (dev: `batbern_development`)
- **Single shared schema**: `public` (no schema-per-service isolation)
- **Table ownership by convention**: Services own specific tables (not enforced by database)
- **ADR-003 compliance**: Cross-service references use meaningful IDs (username, event_code), NOT foreign keys
- **Flyway separation**: Each service has own `flyway_schema_history_<service>` table for independent migrations

**Design Pattern**: Domain-Driven Design with microservices sharing a single database schema.

## Data Models

### Company

**Purpose:** Centralized company entity for speakers, partners, and attendees with logo management.

**Key Attributes:**
- id: UUID - Internal database primary key (NOT exposed in API)
- name: string - Meaningful public identifier (e.g., "GoogleZH") - Story 1.16.2: Used in URLs and API responses, max 12 chars, alphanumeric
- displayName: string - Full official company name for UI display (e.g., "Google Zürich AG")
- logo: CompanyLogo - Uploaded logo with metadata
- website: string - Company website URL
- industry: string - Industry sector classification
- headquarters: Address - Primary company location

**Story 1.16.2 - Meaningful IDs:**
- Database uses UUID as primary key (performance, immutability)
- API exposes `name` as the public identifier (pattern: `^[a-zA-Z0-9]{1,12}$`)
- Name is case-insensitive for lookups (LOWER(name) index)
- URLs use company name: `/companies/GoogleZH` instead of `/companies/{uuid}`
- displayName provides full company name for UI rendering

**Note:** User-company relationships are managed by the User Management Service via `User.companyId` field (stores company name). To query employees of a company, use the User Service endpoint: `GET /api/v1/users?company={companyName}`

#### TypeScript Interface
```typescript
// Story 1.16.2: Company with meaningful ID (name)
// ADR-002: Logo storage via Generic File Upload Service
interface Company {
  name: string;  // Same as id - kept for backwards compatibility
  displayName: string;
  website?: string;
  industry?: string;
  description?: string;
  logoUrl?: string;      // CloudFront CDN URL (from logos.cloudfront_url)
  logoS3Key?: string;    // S3 key reference
  logoFileId?: string;   // References logos.upload_id (ADR-002)
  isVerified: boolean;
  createdAt: Date;
  updatedAt: Date;
  createdBy: string; // User ID who created this company
  // Note: employee relationships managed by User Service via User.companyId
  // Note: Partnership status managed by Partner Coordination Service via Partnership.companyId
  // Note: Logo storage uses Generic File Upload Service (ADR-002)
}

// ADR-002: Generic Logo entity (supports all entity types)
interface Logo {
  uploadId: string;              // Public identifier for tracking
  s3Key: string;                 // Current S3 key (temp or final)
  cloudFrontUrl?: string;        // CDN URL for access
  fileExtension: string;         // png, jpg, jpeg, svg
  fileSize: number;              // Size in bytes
  mimeType: string;              // image/png, etc.
  checksum?: string;             // SHA-256 for integrity
  status: LogoStatus;            // State machine
  associatedEntityType?: AssociatedEntityType;
  associatedEntityId?: string;   // Entity's identifier
  createdAt: Date;
  updatedAt: Date;
  expiresAt?: Date;              // For automatic cleanup
}

enum LogoStatus {
  PENDING = 'PENDING',           // Upload initiated, file may not exist in S3 yet
  CONFIRMED = 'CONFIRMED',       // File successfully uploaded to S3 and verified
  ASSOCIATED = 'ASSOCIATED'      // Linked to entity, file in final location
}

enum AssociatedEntityType {
  COMPANY = 'COMPANY',
  USER = 'USER',
  EVENT = 'EVENT',
  PARTNER = 'PARTNER',
  SPEAKER = 'SPEAKER',
  ATTENDEE = 'ATTENDEE'
}
```

#### Relationships
- **One-to-Many:** Company → Users (users belong to companies via `User.companyId` in User Service)
- **One-to-One:** Company ↔ Partner (partner companies have additional partner data via `Partner.companyId`)

### Partner

**Purpose:** Partnership-specific data for companies that sponsor BATbern events, with consistent participation across all events. Partners are company-centric (not person-centric).

**ADR-004 Reference Pattern:**
- Partner is company-centric: primary relationship is Partner → Company
- PartnerContact references User entity via `userId` (UUID foreign key, internal)
- PartnerContact does NOT duplicate user fields (email, name, photo, company)
- User fields accessed via join to User entity

**Key Attributes:**
- id: UUID - Unique partner identifier
- companyId: UUID - Reference to company entity (primary relationship)
- partnershipLevel: PartnershipTier - Sponsorship level and benefits
- partnershipStartDate: Date - When partnership began
- partnershipEndDate: Date - When partnership ends (optional)
- isActive: boolean - Current partnership status
- contacts: PartnerContact[] - Multiple contact persons (each references User)
- topicVotes: TopicVote[] - Historical voting records
- topicSuggestions: TopicSuggestion[] - Partner-submitted topic ideas
- meetingAttendance: PartnerMeetingAttendance[] - Meeting participation history
- benefits: PartnershipBenefits - Benefits based on partnership tier

#### TypeScript Interface
```typescript
// Partner is company-centric (per ADR-004)
interface Partner {
  id: string;
  companyId: string;                       // Primary relationship (FK to Company)
  partnershipLevel: PartnershipTier;
  partnershipStartDate: Date;
  partnershipEndDate?: Date;
  isActive: boolean;
  contacts: PartnerContact[];              // References User entities
  topicVotes: TopicVote[];
  topicSuggestions: TopicSuggestion[];
  meetingAttendance: PartnerMeetingAttendance[];
  benefits: PartnershipBenefits;
  createdAt: Date;
  updatedAt: Date;
}

// PartnerContact references User (per ADR-004)
interface PartnerContact {
  id: string;
  partnerId: string;                       // FK to Partner
  userId: string;                          // FK to User.id (NOT duplicating user fields)
  contactRole: ContactRole;                // Primary, Billing, Technical
  isPrimary: boolean;

  // API responses include User fields by joining:
  // username: string;                     // From User (API identifier)
  // email: string;                        // From User
  // firstName: string;                    // From User
  // lastName: string;                     // From User
  // profilePictureUrl: string;            // From User
}

enum ContactRole {
  PRIMARY = 'primary',
  BILLING = 'billing',
  TECHNICAL = 'technical',
  MARKETING = 'marketing'
}

interface PartnerAnalytics {
  totalEmployeeAttendanceAllEvents: number;
  averageAttendancePerEvent: number;
  contentEngagementScore: number;
  brandExposureMetrics: BrandExposure;
  roiCalculations: ROIMetrics;
  topicInfluenceScore: number;
}

enum PartnershipTier {
  BRONZE = 'bronze',
  SILVER = 'silver',
  GOLD = 'gold',
  PLATINUM = 'platinum',
  STRATEGIC = 'strategic'
}
```

#### Relationships
- **One-to-One:** Partner → Company (partner data extends company via `Partner.companyId`)
- **One-to-Many:** Partner → PartnerContacts (multiple contact persons)
- **Many-to-One:** PartnerContact → User (via `PartnerContact.userId` FK to `User.id`)
- **Cross-Service:** Partner analytics track employee attendance by querying User Service and Event Registration Service

### User

**Purpose:** Core user entity with role-based access control, company affiliations, and comprehensive profile management. Serves as the foundation for authentication and authorization across all platform features.

**Key Attributes:**
- id: UUID - Internal database primary key (NOT exposed in API)
- username: string - Meaningful public identifier (e.g., "john.doe") - Story 1.16.2: Used in URLs and API responses
- cognitoUserId: string - AWS Cognito user ID for authentication sync
- email: string - User email address (unique)
- firstName: string - User's first name
- lastName: string - User's last name
- bio: string - User biography and professional summary (ADR-004: single source of truth)
- companyId: string - Reference to company name (e.g., "GoogleZH") - Story 1.16.2: Not UUID
- roles: Role[] - User roles (ORGANIZER, SPEAKER, PARTNER, ATTENDEE)
- preferences: UserPreferences - User preferences (theme, language, notifications)
- settings: UserSettings - Account settings and privacy controls
- profilePictureUrl: string - CloudFront CDN URL (from logos.cloudfront_url, ADR-002)
- profilePictureS3Key: string - S3 key reference
- profilePictureFileId: string - References logos.upload_id (ADR-002)
- activityHistory: ActivityHistory[] - User activity tracking
- isActive: boolean - Account active status
- lastLoginAt: Date - Last successful login timestamp

**Story 1.16.2 - Meaningful IDs:**
- Database uses UUID as primary key (performance, immutability)
- API exposes `username` as the public identifier (pattern: `^[a-z]+\.[a-z]+(\.[0-9]+)?$`)
- Username auto-generated from firstName.lastName with German character conversion (ä→ae, ö→oe, ü→ue)
- Collision handling: appends .2, .3, etc. if username exists
- URLs use username: `/users/john.doe` instead of `/users/{uuid}`

**Note:** User entity owns the user-company relationship via `User.companyId` field. This is a one-way dependency from User Service → Company Service. To query users by company, use: `GET /api/v1/users?company={companyId}`

#### TypeScript Interface
```typescript
// Story 1.16.2: User with meaningful ID (username)
// ADR-002: Profile picture via Generic File Upload Service
// ADR-004: User is single source of truth for email, name, bio, photo
interface User {
  id: string;  // Story 1.16.2: username (e.g., "john.doe"), not UUID
  cognitoUserId: string;
  email: string;
  firstName: string;
  lastName: string;
  bio?: string;  // ADR-004: Single source of truth (used by Speaker, Attendee, etc.)
  companyId?: string;  // Story 1.16.2: company name (e.g., "GoogleZH"), not UUID
  roles: Role[];
  preferences: UserPreferences;
  settings: UserSettings;
  profilePictureUrl?: string;     // CloudFront URL (from logos.cloudfront_url, ADR-002)
  profilePictureS3Key?: string;   // S3 key reference
  profilePictureFileId?: string;  // References logos.upload_id (ADR-002)
  isActive: boolean;
  createdAt: Date;
  updatedAt: Date;
  lastLoginAt?: Date;
}

interface UserPreferences {
  theme: 'light' | 'dark' | 'auto';
  language: 'de' | 'en' | 'fr' | 'it';
  emailNotifications: boolean;
  inAppNotifications: boolean;
  pushNotifications: boolean;
  notificationFrequency: 'immediate' | 'daily_digest' | 'weekly_digest';
  quietHoursStart?: string; // HH:mm format
  quietHoursEnd?: string;   // HH:mm format
}

interface UserSettings {
  profileVisibility: 'public' | 'members_only' | 'private';
  showEmail: boolean;
  showCompany: boolean;
  showActivityHistory: boolean;
  allowMessaging: boolean;
  allowCalendarSync: boolean;
  timezone: string;
  twoFactorEnabled: boolean;
}

enum Role {
  ORGANIZER = 'ORGANIZER',
  SPEAKER = 'SPEAKER',
  PARTNER = 'PARTNER',
  ATTENDEE = 'ATTENDEE'
}

interface ActivityHistory {
  id: string;
  userId: string;
  activityType: string; // event_registered, session_attended, topic_voted, etc.
  entityType: string;   // event, session, topic, etc.
  entityId: string;
  description: string;
  metadata: Record<string, any>;
  timestamp: Date;
}
```

#### SPEAKER role (per ADR-009)

Per ADR-009 (Unified Speaker Workflow), **SPEAKER is a role on `User`, not a separate domain entity**. There is no `Speaker` entity, no `speakers` table, no `SpeakerRepository`. "Is this user a speaker?" is answered by `role_assignments.role = 'SPEAKER'` on the same User row (the role grant happens at the `CONTACTED → READY` speaker-workflow transition; see `06a-workflow-state-machines.md`).

The two user-level attributes BATbern needs for a speaker are already existing columns on `user_profiles` per ADR-004 and ADR-007:

- **Short CV / speaker bio** → `user_profiles.bio` (single source of truth, used by Speaker, Attendee, Partner contexts alike).
- **Speaker portrait** → `user_profiles.profile_picture_url` (managed by the generic logo upload service per ADR-002).

No `user_profiles` schema extension is required. The legacy speaker-only attributes that previously lived on a separate `speakers` table — `availability`, `expertise_areas`, `speaking_topics`, `languages_spoken`, `certifications`, `linkedin_url`, `twitter_handle`, `speaking_history`, `communication_preferences` — are **dropped per ADR-009 §0.3** (not migrated, not retained). They were not used by any production code path the platform depends on. Per-event speaker data (slot preferences, content deadlines, workflow state) lives on `speaker_pool` in event-management-service. Per-event content (title, abstract, presentation, quality-review feedback) is canonicalised on `sessions` (current) + `session_content_history` (versioned audit trail, renamed from `speaker_content_submissions` in Story 11.E.8 V99). `session_users` is the junction; the legacy `speaker_pool.{content_status, content_submitted_at, initial_presentation_title}` and `session_users.presentation_title` columns were dropped in V102 — `contentStatus` is derived at read time via `ContentStatusDeriver` (PENDING / SUBMITTED / REVISION_NEEDED / APPROVED).

#### Relationships
- **Many-to-One:** User → Company (via `User.companyId`, managed by User Service)
- **One-to-Many:** User → ActivityHistory (activity tracking)
- **One-to-Many:** User → role_assignments (a user may hold the SPEAKER, ORGANIZER, PARTNER, ATTENDEE roles in any combination)
- **Cross-Service Integration:**
  - Partner Service: Associates partner contacts with user accounts
  - Event Service: Associates event registrations with users
  - Attendee Service: Associates attendee profiles with users
  - Speaker workflow (`speaker_pool` in event-management-service): references User via `username` (meaningful ID per ADR-003); SPEAKER role grant happens at `CONTACTED → READY` per ADR-009

#### Business Rules
- **Minimum Organizers:** System must maintain at least 2 active ORGANIZER role users
- **Role Management:** ORGANIZER role required to modify user roles
- **Email Uniqueness:** Email addresses must be unique across all users
- **Cognito Sync:** User updates synchronize with AWS Cognito custom attributes
- **GDPR Compliance:** User deletion cascades across all domain services with audit logging

#### Caching Strategy
- **User Search:** Caffeine in-memory cache, 10-minute TTL
- **Current User Profile:** Session-based caching for fast authentication lookups
- **Cache Invalidation:** Automatic on user create/update/delete operations

### Session

**Purpose:** Individual agenda items with multiple speaker support and comprehensive material management.

**Key Attributes:**
- id: UUID - Unique session identifier
- eventId: UUID - Parent event reference
- sessionUsers: SessionUser[] - Multiple speakers with roles (ADR-004: references User via userId)
- schedule: SessionSchedule - Timing and location details
- materials: SessionMaterials - Presentation files and resources

**Implementation Note (Story 4.1.4):**
- Sessions reference Users directly via `session_users` junction table (not Speakers)
- Reduces cross-service dependency (event-management-service → company-user-management-service only)
- Speaker-specific workflow data can be added later in speaker-coordination-service independently

#### TypeScript Interface
```typescript
interface Session {
  id: string;
  eventId: string;
  title: string;
  description: string;
  sessionType: SessionType;
  sessionUsers: SessionUser[]; // Multiple speakers (references User, not Speaker)
  startTime: Date;
  endTime: Date;
  room?: string;
  capacity?: number;
  materials: SessionMaterials;
  tags: string[];
  prerequisites?: string[];
  targetAudience: ExperienceLevel[];
  language: string;
  createdAt: Date;
  updatedAt: Date;
}

// ADR-004 pattern: Junction entity references User via userId
interface SessionUser {
  id: string;
  userId: string;          // FK to User.id (ADR-004 reference pattern)
  speakerRole: SpeakerRole;
  presentationTitle?: string; // Speaker-specific title if different
  isConfirmed: boolean;
  invitedAt: Date;
  confirmedAt?: Date;
  declinedAt?: Date;
  declineReason?: string;

  // API responses include User fields by joining:
  // username: string;        // From User
  // firstName: string;       // From User
  // lastName: string;        // From User
  // company: string;         // From User.companyId
  // profilePictureUrl: string; // From User
}

enum SessionType {
  KEYNOTE = 'keynote',
  PRESENTATION = 'presentation',
  WORKSHOP = 'workshop',
  PANEL_DISCUSSION = 'panel_discussion',
  NETWORKING = 'networking',
  BREAK = 'break',
  LUNCH = 'lunch'
}

enum SpeakerRole {
  PRIMARY_SPEAKER = 'primary_speaker',
  CO_SPEAKER = 'co_speaker',
  MODERATOR = 'moderator',
  PANELIST = 'panelist'
}
```

#### Relationships
- **Many-to-One:** Session → Event (session belongs to event)
- **Many-to-Many:** Session ↔ Users via SessionUser junction (multiple speakers per session) - Story 4.1.4
- **One-to-Many:** Session → SessionUsers (speaker assignments)
- **Many-to-One:** SessionUser → User (via userId FK, ADR-004 pattern)
- **One-to-Many:** Session → AttendeeRatings (feedback and ratings)
- **One-to-Many:** Session → Materials (uploaded files and resources)

### Event

**Purpose:** Conference events with attendee registrations and session management, no direct speaker relationships.

**Key Attributes:**
- id: UUID - Internal database primary key (NOT exposed in API)
- eventCode: string - Meaningful public identifier (e.g., "BATbern56") - Story 1.16.2: Used in URLs and API responses
- eventNumber: number - Sequential event number
- organizerId: string - Organizer username (e.g., "john.doe") - Story 1.16.2: Not UUID

**Story 1.16.2 - Meaningful IDs:**
- Database uses UUID as primary key (performance, immutability)
- API exposes `eventCode` as the public identifier (pattern: `^BATbern[0-9]+$`)
- EventCode auto-generated from eventNumber: `BATbern{eventNumber}`
- URLs use eventCode: `/events/BATbern56` instead of `/events/{uuid}`

#### TypeScript Interface
```typescript
// Story 1.16.2: Event with meaningful ID (eventCode)
interface Event {
  id: string;  // Story 1.16.2: eventCode (e.g., "BATbern56"), not UUID
  eventNumber: number;
  title: string;
  description: string;
  eventDate: Date;
  registrationDeadline: Date;
  eventType: EventType;
  slotConfiguration: EventSlotConfiguration;
  venue: Venue;
  workflowState: EventWorkflowState;  // V17 migration: removed legacy 'status' field
  organizerId: string;  // Story 1.16.2: organizer username (e.g., "john.doe"), not UUID
  capacity: number;
  currentAttendeeCount: number;
  topics: Topic[];
  sessions: Session[]; // Sessions contain speaker relationships
  slots: EventSlot[];
  overflowManagement: OverflowManagement;
  qualityReview: EventQualityReview;
  createdAt: Date;
  updatedAt: Date;
  publishedAt?: Date;
  metadata: EventMetadata;
}

// EventStatus enum removed in V17 migration (2025-12-15)
// Replaced by EventWorkflowState (9-state workflow, see 06a-workflow-state-machines.md)

enum EventType {
  FULL_DAY = 'full_day',        // 6-8 slots
  AFTERNOON = 'afternoon',      // 6-8 slots
  EVENING = 'evening'           // 3-4 slots
}

// Epic 5: 9-state Event Workflow (Story 5.1a)
// Introduced in V12 migration, became the sole state field in V17 migration
// See 06a-workflow-state-machines.md for complete workflow documentation
enum EventWorkflowState {
  CREATED = 'CREATED',
  TOPIC_SELECTION = 'TOPIC_SELECTION',
  SPEAKER_BRAINSTORMING = 'SPEAKER_BRAINSTORMING',
  SPEAKER_OUTREACH = 'SPEAKER_OUTREACH',
  SPEAKER_CONFIRMATION = 'SPEAKER_CONFIRMATION',
  CONTENT_COLLECTION = 'CONTENT_COLLECTION',
  QUALITY_REVIEW = 'QUALITY_REVIEW',
  THRESHOLD_CHECK = 'THRESHOLD_CHECK',
  OVERFLOW_MANAGEMENT = 'OVERFLOW_MANAGEMENT',
  SLOT_ASSIGNMENT = 'SLOT_ASSIGNMENT',
  AGENDA_PUBLISHED = 'AGENDA_PUBLISHED',
  AGENDA_FINALIZED = 'AGENDA_FINALIZED',
  NEWSLETTER_SENT = 'NEWSLETTER_SENT',
  EVENT_READY = 'EVENT_READY',
  PARTNER_MEETING_COMPLETE = 'PARTNER_MEETING_COMPLETE',
  ARCHIVED = 'ARCHIVED'
}

interface EventSlotConfiguration {
  eventType: EventType;
  minSlots: number;
  maxSlots: number;
  slotDuration: number; // minutes
  theoreticalSlotsAM: boolean; // theoretical presentations in morning
  breakSlots: number;
  lunchSlots: number;
  // Story 15.2 — apéro knobs (added to the shared template AND the per-event override).
  // afternoon/evening templates default aperitifSlots=1 @ 90 min, position=end; full_day=0.
  aperitifSlots: number; // 0 = off, >=1 = on
  aperitifDuration: number; // minutes
  aperitifPosition: 'start' | 'end'; // after moderation-start | after moderation-end (default)
}

// Story 15.2 — per-event copy-on-edit override of the event-type template.
// One row per event (table `event_agenda_config`, FK event_id → events.id). Created only
// when an organizer first edits the event type in slot assignment; the shared `event_types`
// template is never mutated. Resolution (AgendaConfigResolver): per-event override if a row
// exists, else the shared template. Mirrors every EventSlotConfiguration knob + the apéro knobs.
interface EventAgendaConfig extends EventSlotConfiguration {
  eventId: string;
}

interface EventSlot {
  id: string;
  eventId: string;
  slotNumber: number;
  startTime: Date;
  endTime: Date;
  slotType: SlotType;
  assignedSessionId?: string;
  assignedSpeakerId?: string;
  isFlexible: boolean;
  technicalRequirements: TechnicalRequirement[];
}

enum SlotType {
  THEORETICAL = 'theoretical',
  PRACTICAL = 'practical',
  KEYNOTE = 'keynote',
  WORKSHOP = 'workshop',
  BREAK = 'break',
  LUNCH = 'lunch',
  NETWORKING = 'networking'
}

interface OverflowManagement {
  eventId: string;
  overflowSpeakers: OverflowSpeaker[];
  selectionVotes: SpeakerSelectionVote[];
  isVotingComplete: boolean;
  votingDeadline?: Date;
}

interface OverflowSpeaker {
  speakerId: string;
  sessionId: string;
  votes: number;
  topicFitScore: number;
  isSelected: boolean;
  declinedAt?: Date;
  declineReason?: string;
}

interface SpeakerSelectionVote {
  organizerId: string;
  speakerId: string;
  vote: VoteType;
  reason?: string;
  votedAt: Date;
}

enum VoteType {
  APPROVE = 'approve',
  REJECT = 'reject',
  ABSTAIN = 'abstain'
}

interface EventQualityReview {
  eventId: string;
  moderatorId: string;
  overallStatus: QualityReviewStatus;
  sessionReviews: ContentQualityReview[];
  reviewDeadline: Date;
  completedAt?: Date;
}
```

### Topic

**Purpose:** Manages event topics with usage tracking, similarity scoring, and staleness detection for intelligent topic selection during event planning.

#### TypeScript Interface

```typescript
interface Topic {
  id: string;
  title: string;
  description: string;
  category: TopicCategory;
  createdDate: Date;
  lastUsedDate?: Date;
  usageCount: number;
  usageHistory: TopicUsageRecord[];
  stalenessScore: number; // 0-100, higher = safer to reuse
  calculatedWaitPeriod?: number; // in months
  partnerInfluenceScore?: number;
  isActive: boolean;
}

interface TopicUsageRecord {
  eventId: string;
  usedDate: Date;
  attendeeCount: number;
  feedbackScore?: number;
}

enum TopicCategory {
  TECHNICAL = 'technical',
  MANAGEMENT = 'management',
  SOFT_SKILLS = 'soft_skills',
  INDUSTRY_TRENDS = 'industry_trends',
  TOOLS_PLATFORMS = 'tools_platforms'
}
```

#### Relationships

- **One-to-Many**: Topic → TopicUsageRecord (one topic has many usage records)
- **Many-to-Many**: Topic ↔ Event (topics can be used in multiple events, events can have multiple topics)
- **Reference**: Topic ← PartnerCoordination (partners vote on topics)

### Enhanced Speaker & Session Entities

```typescript
interface SpeakerSlotPreferences {
  speakerId: string;
  eventId: string;
  preferredTimeSlots: PreferredTimeSlot[];
  technicalRequirements: TechnicalRequirement[];
  cannotPresentAfter?: string; // time constraint (e.g., "16:00")
  ownLaptopRequired: boolean;
  specialEquipmentNeeds?: string;
  accessibilityRequirements?: string;
  submittedAt: Date;
  lastUpdatedAt: Date;
}

interface PreferredTimeSlot {
  startTime: string; // time format "09:00"
  endTime: string;   // time format "10:30"
  preference: PreferenceLevel;
}

enum PreferenceLevel {
  STRONGLY_PREFERRED = 'strongly_preferred',
  PREFERRED = 'preferred',
  ACCEPTABLE = 'acceptable',
  NOT_PREFERRED = 'not_preferred',
  UNAVAILABLE = 'unavailable'
}

interface TechnicalRequirement {
  id: string;
  requirement: string;
  isRequired: boolean;
  description?: string;
}

interface SlotAssignment {
  slotId: string;
  assignedAt: Date;
  assignedBy: string; // organizer ID
  automaticallyAssigned: boolean;
  manualOverride: boolean;
  conflictResolution?: string;
}

interface ContentQualityReview {
  id: string;
  sessionId: string;
  speakerId: string;
  reviewerId: string; // moderator ID
  abstractReview: AbstractReview;
  materialReview: MaterialReview;
  status: QualityReviewStatus;
  submittedAt: Date;
  reviewedAt?: Date;
  feedback?: string;
  revisionRequested: boolean;
  revisionDeadline?: Date;
}

interface AbstractReview {
  content: string;
  characterCount: number;
  hasLessonsLearned: boolean;
  hasProductPromotion: boolean;
  meetsStandards: boolean;
  issues: string[];
}

interface MaterialReview {
  bioReview: BiographyReview;
  photoReview: PhotoReview;
  additionalMaterials: MaterialItem[];
}

interface BiographyReview {
  content: string;
  characterCount: number;
  isAppropriate: boolean;
  issues: string[];
}

interface PhotoReview {
  photoUrl: string;
  isAppropriate: boolean;
  meetsTechnicalStandards: boolean;
  issues: string[];
}

interface MaterialItem {
  fileName: string;
  fileType: string;
  fileSize: number;
  uploadedAt: Date;
  isApproved: boolean;
  issues: string[];
}

enum QualityReviewStatus {
  PENDING = 'pending',
  IN_REVIEW = 'in_review',
  APPROVED = 'approved',
  REQUIRES_CHANGES = 'requires_changes',
  REJECTED = 'rejected',
  REVISION_SUBMITTED = 'revision_submitted'
}
```

### Content Storage & File Management

**Important (ADR-002)**: File uploads use a **dual-table pattern**:
- **`logos` table**: Generic file upload service for profile images (company logos, user photos, event banners)
  - Uses 3-phase upload pattern (PENDING → CONFIRMED → ASSOCIATED)
  - Entity-agnostic with generic association
  - Automatic cleanup of orphaned uploads
  - See ADR-002 for complete architecture

- **`content_metadata` table**: Session content and materials (presentations, documents, videos)
  - Used for event-related content only
  - Tied to sessions and events
  - No state machine (simpler workflow)

#### Logo Files (ADR-002 - Generic File Upload Service)

**Purpose:** Profile images and branding for all entity types. See `logos` table schema in Company Management Service section and ADR-002 for complete documentation.

**Supported Entity Types**: Company logos, User profile pictures, Event banners, Partner logos, Speaker photos

**Three-Phase Upload Pattern**:
1. **PENDING**: Upload initiated via `POST /logos/presigned-url` (no entity required)
2. **CONFIRMED**: File uploaded to S3 and verified via `POST /logos/{uploadId}/confirm`
3. **ASSOCIATED**: Linked to entity during creation (e.g., `POST /companies` with `logoUploadId`)

**S3 Key Strategy**:
- Temporary: `logos/temp/{uploadId}/logo-{fileId}.{ext}` (PENDING/CONFIRMED)
- Final: `logos/{year}/{entity-type}/{entity-name}/logo-{fileId}.{ext}` (ASSOCIATED)

**Cleanup**: Automated job removes PENDING > 24h, CONFIRMED > 7 days

#### Content Metadata (Session Materials Only)

**Purpose:** Track session-related content: presentations, handouts, videos, and archive materials. NOT used for profile images (see logos table above).

```typescript
interface ContentMetadata {
  fileId: string; // UUID
  s3Bucket: string; // Bucket name
  s3Key: string; // Full S3 object key
  originalFilename: string;
  fileSizeBytes: number;
  mimeType: string;
  checksum: string; // SHA-256 hash
  contentType: ContentType;
  uploadStatus: UploadStatus;
  uploadedBy: string; // User ID
  uploadedAt?: Date;
  cloudFrontUrl?: string; // CDN URL if published (cdn.batbern.ch or cdn.staging.batbern.ch)
  metadata: Record<string, string>; // Custom metadata
  createdAt: Date;
  updatedAt: Date;
}

enum ContentType {
  PRESENTATION = 'presentation',        // Session presentations
  SPEAKER_CV = 'speaker_cv',           // Speaker CVs
  EVENT_PHOTO = 'event_photo',         // Event photography
  ARCHIVE_MATERIAL = 'archive_material' // Historical content
  // NOTE: LOGO and SPEAKER_PHOTO removed - use logos table (ADR-002)
}

enum UploadStatus {
  PENDING = 'pending',
  UPLOADING = 'uploading',
  COMPLETED = 'completed',
  FAILED = 'failed',
  VIRUS_DETECTED = 'virus_detected',
  DELETED = 'deleted'
}
```

#### Session Materials (Enhanced)

**Purpose:** Extended session materials model with comprehensive file tracking and presentation management.

```typescript
interface SessionMaterials {
  sessionId: string;
  presentationFiles: PresentationFile[];
  supplementaryMaterials: SupplementaryMaterial[];
  recordingUrl?: string;
  photosGallery?: string[]; // Array of ContentMetadata fileIds
  materialsPublishedAt?: Date;
}

interface PresentationFile {
  fileId: string; // References ContentMetadata
  title: string;
  description?: string;
  isPrimary: boolean;
  uploadedBy: string; // Speaker ID
  uploadedAt: Date;
  downloadCount: number;
  fileUrl: string; // CloudFront CDN URL (cdn.batbern.ch or cdn.staging.batbern.ch)
  lastAccessedAt?: Date;
}

interface SupplementaryMaterial {
  fileId: string; // References ContentMetadata
  title: string;
  description?: string;
  materialType: 'code_sample' | 'slides' | 'handout' | 'resource_link' | 'demo_video';
  uploadedBy: string;
  uploadedAt: Date;
  downloadCount: number;
}
```

#### Storage Quota Management

**Purpose:** Track and enforce storage quotas per user role with usage monitoring and alerts.

```typescript
interface StorageQuota {
  userId: string;
  userRole: UserRole;
  quotaLimitBytes: number; // -1 for unlimited (organizers)
  currentUsageBytes: number;
  fileCount: number;
  lastUpdated: Date;
  quotaWarningIssued: boolean;
  quotaExceededAt?: Date;
}

interface StorageUsageLog {
  id: string;
  userId: string;
  fileId: string;
  action: 'upload' | 'delete';
  fileSizeBytes: number;
  timestamp: Date;
  newTotalUsageBytes: number;
}

interface StorageQuotaInfo {
  quotaLimitBytes: number;
  currentUsageBytes: number;
  fileCount: number;
  percentageUsed: number;
  warningThresholdPercentage: number; // 80%
  availableBytes: number;
}
```

#### File Upload/Download DTOs

**Purpose:** Data transfer objects for file upload/download workflows with presigned URLs.

```typescript
interface PresignedUploadUrl {
  uploadUrl: string;
  fileId: string;
  expiresIn: number; // seconds
  requiredHeaders: Record<string, string>;
}

interface PresignedDownloadUrl {
  downloadUrl: string;
  filename: string;
  fileSizeBytes: number;
  mimeType: string;
  expiresIn: number; // seconds
}

interface FileUploadRequest {
  filename: string;
  contentType: ContentType;
  fileSizeBytes: number;
  mimeType: string;
}

interface FileUploadConfirmation {
  fileId: string;
  checksum: string; // SHA-256 hash
}
```

#### Content Metadata Database Schema

**Note (ADR-002)**: Profile images (logos, user photos) use the `logos` table. This table is for session content only.

```sql
-- Content metadata table (session content only - NOT profile images)
CREATE TABLE content_metadata (
    file_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    s3_bucket VARCHAR(255) NOT NULL,
    s3_key VARCHAR(1000) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    checksum VARCHAR(64), -- SHA-256 hash
    content_type VARCHAR(50) NOT NULL CHECK (content_type IN (
        'presentation',      -- Session presentations
        'speaker_cv',        -- Speaker CVs
        'event_photo',       -- Event photography
        'archive_material'   -- Historical content
        -- NOTE: 'logo' and 'speaker_photo' removed - use logos table (ADR-002)
    )),
    upload_status VARCHAR(20) NOT NULL CHECK (upload_status IN (
        'pending', 'uploading', 'completed', 'failed', 'virus_detected', 'deleted'
    )),
    uploaded_by UUID NOT NULL REFERENCES users(id),
    uploaded_at TIMESTAMP,
    cloudfront_url VARCHAR(500),
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT unique_s3_key UNIQUE (s3_bucket, s3_key)
);

CREATE INDEX idx_content_metadata_uploaded_by ON content_metadata(uploaded_by);
CREATE INDEX idx_content_metadata_content_type ON content_metadata(content_type);
CREATE INDEX idx_content_metadata_upload_status ON content_metadata(upload_status);
CREATE INDEX idx_content_metadata_created_at ON content_metadata(created_at DESC);

-- Storage quota table
CREATE TABLE storage_quota (
    user_id UUID PRIMARY KEY REFERENCES users(id),
    user_role VARCHAR(50) NOT NULL,
    quota_limit_bytes BIGINT NOT NULL, -- -1 for unlimited
    current_usage_bytes BIGINT NOT NULL DEFAULT 0,
    file_count INTEGER NOT NULL DEFAULT 0,
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    quota_warning_issued BOOLEAN NOT NULL DEFAULT false,
    quota_exceeded_at TIMESTAMP
);

CREATE INDEX idx_storage_quota_usage ON storage_quota(current_usage_bytes DESC);
CREATE INDEX idx_storage_quota_warning ON storage_quota(quota_warning_issued) WHERE quota_warning_issued = true;

-- Storage usage log table for audit trail
CREATE TABLE storage_usage_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    file_id UUID REFERENCES content_metadata(file_id),
    action VARCHAR(10) NOT NULL CHECK (action IN ('upload', 'delete')),
    file_size_bytes BIGINT NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    new_total_usage_bytes BIGINT NOT NULL
);

CREATE INDEX idx_storage_usage_log_user_id ON storage_usage_log(user_id, timestamp DESC);
CREATE INDEX idx_storage_usage_log_timestamp ON storage_usage_log(timestamp DESC);

-- Presentation files table (links content to sessions)
CREATE TABLE presentation_files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    file_id UUID NOT NULL REFERENCES content_metadata(file_id),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    is_primary BOOLEAN NOT NULL DEFAULT false,
    uploaded_by UUID NOT NULL REFERENCES users(id),
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    download_count INTEGER NOT NULL DEFAULT 0,
    last_accessed_at TIMESTAMP,

    CONSTRAINT unique_session_primary_file UNIQUE (session_id, is_primary) WHERE is_primary = true
);

CREATE INDEX idx_presentation_files_session_id ON presentation_files(session_id);
CREATE INDEX idx_presentation_files_file_id ON presentation_files(file_id);
CREATE INDEX idx_presentation_files_download_count ON presentation_files(download_count DESC);

-- Supplementary materials table
CREATE TABLE supplementary_materials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    file_id UUID NOT NULL REFERENCES content_metadata(file_id),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    material_type VARCHAR(50) NOT NULL CHECK (material_type IN (
        'code_sample', 'slides', 'handout', 'resource_link', 'demo_video'
    )),
    uploaded_by UUID NOT NULL REFERENCES users(id),
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    download_count INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_supplementary_materials_session_id ON supplementary_materials(session_id);
CREATE INDEX idx_supplementary_materials_file_id ON supplementary_materials(file_id);
```

#### Content Management Triggers

```sql
-- Trigger to update storage quota on file upload
CREATE OR REPLACE FUNCTION update_quota_on_upload()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.upload_status = 'completed' AND
       (OLD.upload_status IS NULL OR OLD.upload_status != 'completed') THEN

        UPDATE storage_quota
        SET current_usage_bytes = current_usage_bytes + NEW.file_size_bytes,
            file_count = file_count + 1,
            last_updated = CURRENT_TIMESTAMP
        WHERE user_id = NEW.uploaded_by;

        -- Check if quota exceeded
        UPDATE storage_quota
        SET quota_exceeded_at = CURRENT_TIMESTAMP
        WHERE user_id = NEW.uploaded_by
            AND quota_limit_bytes > 0
            AND current_usage_bytes > quota_limit_bytes
            AND quota_exceeded_at IS NULL;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_quota_on_upload
    AFTER INSERT OR UPDATE ON content_metadata
    FOR EACH ROW
    EXECUTE FUNCTION update_quota_on_upload();

-- Trigger to update storage quota on file deletion
CREATE OR REPLACE FUNCTION update_quota_on_delete()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.upload_status = 'deleted' AND OLD.upload_status = 'completed' THEN
        UPDATE storage_quota
        SET current_usage_bytes = GREATEST(0, current_usage_bytes - NEW.file_size_bytes),
            file_count = GREATEST(0, file_count - 1),
            last_updated = CURRENT_TIMESTAMP,
            quota_warning_issued = false
        WHERE user_id = NEW.uploaded_by;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_quota_on_delete
    AFTER UPDATE ON content_metadata
    FOR EACH ROW
    WHEN (NEW.upload_status = 'deleted')
    EXECUTE FUNCTION update_quota_on_delete();

-- Trigger to increment download count
CREATE OR REPLACE FUNCTION increment_download_count()
RETURNS TRIGGER AS $$
BEGIN
    NEW.download_count = OLD.download_count + 1;
    NEW.last_accessed_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

#### Content Storage Relationships
- **One-to-Many:** User → ContentMetadata (user uploads many files)
- **One-to-Many:** Session → PresentationFiles (session has multiple presentations)
- **One-to-Many:** Session → SupplementaryMaterials (session has multiple supplementary materials)
- **One-to-One:** User → StorageQuota (each user has one quota record)
- **One-to-Many:** User → StorageUsageLog (audit trail of all storage operations)
- **Many-to-One:** PresentationFile → ContentMetadata (file reference)
- **Many-to-One:** SupplementaryMaterial → ContentMetadata (file reference)

#### Relationships
- **One-to-Many:** Event → Sessions (event contains multiple sessions)
- **One-to-Many:** Event → EventSlots (event contains multiple time slots)
- **One-to-One:** Event → OverflowManagement (overflow handling per event)
- **One-to-One:** Event → EventQualityReview (quality review per event)
- **Many-to-Many:** Event ↔ Attendees (through EventRegistration)
- **No direct relationship to Speakers** (speakers connected via sessions)
- **No direct relationship to Partners** (partners participate in all events)

### Attendee

**Purpose:** Conference participants with event registration and engagement tracking. Attendee profile extends User entity per ADR-004.

**ADR-004 Reference Pattern:**
- Attendee references User entity via `userId` (UUID foreign key, internal)
- API uses `username` as public identifier (e.g., `GET /attendees/john.doe`)
- User fields (email, name, bio, photo, company) stored in User entity (NEVER duplicated)
- Attendee entity contains ONLY domain-specific fields (registrations, engagement, preferences)

**Key Attributes:**
- id: UUID - Unique attendee identifier
- userId: UUID - Foreign key to User entity (internal, NOT exposed in API)
- eventRegistrations: EventRegistration[] - Many-to-many with events
- engagementHistory: AttendeeEngagement - Content engagement tracking
- contentPreferences: ContentPreferences - Topics of interest (different from User.preferences)
- newsletterSubscription: boolean - Newsletter opt-in status
- gdprConsent: GDPRConsent - GDPR consent tracking with timestamp and IP

**Fields Stored in User Entity (NOT in Attendee):**
- ❌ email, firstName, lastName (from User.email, User.firstName, User.lastName)
- ❌ companyId (from User.companyId)
- ❌ position (removed entirely per ADR-004)

#### TypeScript Interface
```typescript
// Attendee entity references User (per ADR-004)
interface Attendee {
  id: string;                              // UUID (internal)
  userId: string;                          // UUID FK to User.id (internal)

  // API responses include User fields by joining:
  // username: string;                     // From User (API identifier)
  // email: string;                        // From User
  // firstName: string;                    // From User
  // lastName: string;                     // From User
  // companyId?: string;                   // From User (optional)

  // Attendee-specific fields:
  eventRegistrations: EventRegistration[]; // Many-to-many with events
  engagementHistory: AttendeeEngagement;   // Content engagement tracking
  contentPreferences: ContentPreferences;  // Different from User.preferences
  newsletterSubscription: boolean;
  gdprConsent: GDPRConsent;
  createdAt: Date;
  updatedAt: Date;
}

interface EventRegistration {
  id: string;                          // UUID
  eventId: string;                     // FK to Event

  // Authenticated registration (existing pattern)
  attendeeId?: string;                 // UUID FK to Attendee (nullable for anonymous registrations)

  // Anonymous registration (new pattern - ADR-010)
  anonymousEmail?: string;             // Email for anonymous registration
  anonymousFirstName?: string;         // First name
  anonymousLastName?: string;          // Last name
  anonymousCompany?: string;           // Company
  anonymousRole?: string;              // Job title/role

  // Account linking (when anonymous user creates account)
  claimedByUserId?: string;            // User who claimed this registration
  claimedAt?: Date;                    // When registration was claimed

  // Registration metadata
  confirmationCode: string;            // Format: BAT-YYYY-NNNNNN (e.g., BAT-2025-000123)
  registrationDate: Date;
  status: RegistrationStatus;          // 'registered' | 'waitlisted' | 'confirmed' | 'cancelled' | 'attended'
  specialRequests?: string;
  attendanceConfirmed?: boolean;
  actualAttendance: boolean;
  sessionPreferences?: string[];       // Event-level registration, sessions are preferences only

  // Communication preferences
  newsletterSubscribed?: boolean;
  eventReminders?: boolean;
}

interface GDPRConsent {
  consentGiven: boolean;
  consentDate: Date;
  ipAddress: string;
  consentVersion: string;
}
```

#### Relationships
- **One-to-One:** Attendee → User (via `Attendee.userId` FK to `User.id`)
- **Many-to-One:** Attendee → Company (via `User.companyId`, transitive through User)
- **Many-to-Many:** Attendee ↔ Events (through EventRegistration)
- **One-to-Many:** Attendee → EngagementHistory (content interaction tracking)

### Anonymous Registration Account Linking

**Pattern**: Unified user_profiles with nullable cognito_user_id (ADR-007)

**Use Case**: A user registers anonymously for an event, then later creates a Cognito account with the same email address.

#### Workflow

**1. Anonymous Registration (Story 4.1.5)**

User visits `/register/BAT-025` and completes registration wizard without login.

Backend creates user_profile (in Company User Management Service):
```sql
-- user_profiles table (cognito_user_id is NULL for anonymous users)
INSERT INTO user_profiles (
    id, username, email, first_name, last_name, company_id, cognito_user_id
) VALUES (
    gen_random_uuid(),
    'john.doe',              -- Generated username
    'john@example.com',
    'John',
    'Doe',
    'AcmeCorp',              -- FK to companies
    NULL                     -- NULL = anonymous user (ADR-007)
);
```

Backend creates registration (in Event Management Service):
```sql
-- registrations reference user by username (ADR-004: no duplicated user fields)
INSERT INTO registrations (
    id, event_code, attendee_username, registration_code, status
) VALUES (
    gen_random_uuid(),
    'BATbern25',             -- eventCode per ADR-003
    'john.doe',              -- References user_profiles.username (cross-service)
    'BAT-2025-000123',
    'confirmed'
);
```

User receives confirmation email with QR code and can access registration via confirmation code.

**2. Account Creation (Later)**

User decides to create a Cognito account with email `john@example.com`.

Backend (on account creation via `UserCreatedEvent` handler):
```sql
-- Simply update the existing user_profile with the Cognito ID
UPDATE user_profiles SET
    cognito_user_id = 'cognito-sub-12345',
    claimed_at = NOW()
WHERE email = 'john@example.com'
  AND cognito_user_id IS NULL;

-- All registrations are ALREADY linked via attendee_username!
-- No migration, no copying, no clearing required.
```

**3. Result**

User can now:
- View all past registrations in authenticated dashboard (linked via username)
- Register for future events as authenticated user
- Access all registrations via account (not just confirmation codes)

#### Duplicate Prevention

```sql
-- Unique email prevents duplicate anonymous users
CREATE UNIQUE INDEX idx_user_profiles_email ON user_profiles(email);

-- Unique (event, attendee) prevents duplicate registrations
CREATE UNIQUE INDEX idx_registrations_event_attendee
    ON registrations(event_code, attendee_username);
```

#### Edge Cases

**User already has account, registers anonymously by mistake:**
- Lookup finds existing user_profile by email
- Use existing username for registration (no new profile created)

**Email typo in anonymous registration:**
- User contacts support with confirmation code
- Support can update email in user_profiles

#### Implementation Notes

**Auto-linking trigger point:**
- Execute in `UserService.createUser()` after Cognito account created
- Find existing anonymous user_profile with matching email
- Set `cognito_user_id` and `claimed_at` (3 lines of code)
- All registrations automatically linked via `attendee_username`

**Security considerations:**
- Email verification via confirmation email
- Confirmation code acts as secret token for viewing registration
- Rate limiting on registration endpoint

**Privacy considerations:**
- User data in single location (user_profiles) - simple GDPR deletion
- No duplicate data to synchronize or clear

### Notification Domain Models

#### Email Templates

**Purpose:** Manage email templates for all notification types with versioning and multilingual support.

**Key Attributes:**
- id: UUID - Unique template identifier
- templateType: TemplateType - Type of notification (speaker_invitation, deadline_reminder, etc.)
- language: string - Template language (de, en)
- subject: string - Email subject line with variable placeholders
- htmlBody: string - HTML version of email body
- textBody: string - Plain text version of email body
- variables: string[] - List of available template variables
- version: number - Template version number
- isActive: boolean - Whether template is currently active

#### TypeScript Interface
```typescript
interface EmailTemplate {
  id: string;
  templateType: TemplateType;
  language: 'de' | 'en';
  subject: string;
  htmlBody: string;
  textBody: string;
  variables: string[]; // e.g., ['speakerName', 'eventTitle', 'deadline']
  version: number;
  isActive: boolean;
  createdBy: string;
  createdAt: Date;
  updatedAt: Date;
}

enum TemplateType {
  SPEAKER_INVITATION = 'speaker_invitation',
  DEADLINE_REMINDER_48H = 'deadline_reminder_48h',
  DEADLINE_REMINDER_24H = 'deadline_reminder_24h',
  DEADLINE_CRITICAL = 'deadline_critical',
  MATERIAL_RECEIVED = 'material_received_confirmation',
  EVENT_PUBLISHED = 'event_published',
  NEWSLETTER_PROGRESSIVE = 'newsletter_progressive',
  NEWSLETTER_FINAL = 'newsletter_final',
  AGENDA_UPDATE = 'agenda_update'
}
```

#### Notification Preferences

**Purpose:** Store user-specific notification preferences across channels and types.

**Key Attributes:**
- id: UUID - Unique preference identifier
- userId: UUID - Reference to user
- channel: NotificationChannel - Delivery channel
- notificationType: string - Specific notification type
- isEnabled: boolean - Whether notifications are enabled
- frequency: FrequencyType - Notification frequency
- quietHoursStart: Time - Start of quiet hours
- quietHoursEnd: Time - End of quiet hours

#### TypeScript Interface
```typescript
interface NotificationPreferences {
  id: string;
  userId: string;
  channel: NotificationChannel;
  notificationType: string;
  isEnabled: boolean;
  frequency: FrequencyType;
  quietHoursStart?: string; // HH:MM format
  quietHoursEnd?: string; // HH:MM format
  updatedAt: Date;
}

enum NotificationChannel {
  EMAIL = 'email',
  IN_APP = 'in_app',
  PUSH = 'push',
  SMS = 'sms'
}

enum FrequencyType {
  IMMEDIATE = 'immediate',
  DAILY_DIGEST = 'daily_digest',
  WEEKLY_DIGEST = 'weekly_digest'
}
```

#### Escalation Rules

**Purpose:** Define multi-tier escalation workflows for deadline management.

**Key Attributes:**
- id: UUID - Unique rule identifier
- eventId: UUID - Event this rule applies to
- ruleType: string - Type of escalation (speaker_deadline, content_review, etc.)
- tier1HoursBefore: number - Hours before deadline for reminder
- tier2HoursBefore: number - Hours before deadline for warning
- tier3HoursBefore: number - Hours before deadline for critical
- escalationThreshold: number - Minutes after critical to escalate
- backupOrganizerIds: UUID[] - Backup organizers for escalation
- isActive: boolean - Whether rule is active

#### TypeScript Interface
```typescript
interface EscalationRule {
  id: string;
  eventId: string;
  ruleType: string; // e.g., 'speaker_deadline', 'content_review'
  tier1HoursBefore: number; // e.g., 48 hours
  tier2HoursBefore: number; // e.g., 24 hours
  tier3HoursBefore: number; // e.g., 0 hours (deadline)
  escalationThreshold?: number; // Minutes after deadline to escalate
  backupOrganizerIds: string[];
  isActive: boolean;
  createdAt: Date;
  updatedAt: Date;
}
```

#### Notification Log

**Purpose:** Comprehensive audit trail of all sent notifications with delivery tracking.

**Key Attributes:**
- id: UUID - Unique log entry identifier
- userId: UUID - Recipient user
- notificationType: string - Type of notification sent
- channel: NotificationChannel - Delivery channel used
- templateId: UUID - Template used (if applicable)
- subject: string - Actual subject sent
- sentAt: Date - When notification was sent
- deliveryStatus: DeliveryStatus - Current delivery status
- sesMessageId: string - AWS SES message ID
- errorMessage: string - Error details if failed
- metadata: JSON - Additional context data

#### TypeScript Interface
```typescript
interface NotificationLog {
  id: string;
  userId: string;
  notificationType: string;
  channel: NotificationChannel;
  templateId?: string;
  subject: string;
  sentAt: Date;
  deliveryStatus: DeliveryStatus;
  sesMessageId?: string;
  errorMessage?: string;
  metadata: Record<string, any>;
}

enum DeliveryStatus {
  SENT = 'sent',
  DELIVERED = 'delivered',
  BOUNCED = 'bounced',
  FAILED = 'failed',
  COMPLAINED = 'complained'
}
```

## Database Schema

**Source of truth: the Flyway migrations**, not this document. This section is an inventory to
find your way; for columns, constraints and indexes read the migration that introduced the table
and every later migration that alters it.

| Service | Migrations | Flyway history table |
|---|---|---|
| event-management-service | `services/event-management-service/src/main/resources/db/migration/` (V1 to V144) | `flyway_schema_history_event_management` |
| company-user-management-service | `services/company-user-management-service/src/main/resources/db/migration/` (V1 to V22) | `flyway_schema_history_company_user_management` |
| partner-coordination-service | `services/partner-coordination-service/src/main/resources/db/migration/` (V1 to V10) | `flyway_schema_history_partner_coordination` |
| speaker-coordination-service | none of its own; only the `shared-kernel` baseline `V1__Initial_baseline.sql` (extension + `update_updated_at_column()` trigger function) | `flyway_schema_history_speaker_coordination` |
| attendee-experience-service | none; the service has no datasource and no JPA entities | n/a |

All services connect to the same PostgreSQL database (`batbern`); each keeps its own Flyway
history table. Two tables are declared by two services with `CREATE TABLE IF NOT EXISTS`:
`logos` (CUMS V10, EMS V5) and `shedlock` (CUMS V12, EMS V31).

The inventory below was derived by scanning every migration for `CREATE TABLE`, `DROP TABLE`
and `ALTER TABLE ... RENAME TO` (as of develop c4593449). Tables created and later dropped are
listed separately so that nobody goes looking for them. To regenerate it, scan the same
statements again; do not edit it by hand from memory.

### Event Management Service (EMS)

| Table | Purpose | Introduced |
|---|---|---|
| `events` | Event aggregate root (event code, dates, venue, `workflow_state`, capacity) | V2 |
| `sessions` | Agenda slots and talks of an event; canonical current title and abstract | V2 |
| `registrations` | Attendee registrations, referenced by `attendee_username` (ADR-003) | V2 |
| `logos` | Generic file upload state machine PENDING, CONFIRMED, ASSOCIATED (ADR-002); shared with CUMS | V5 |
| `session_users` | Junction session to speaker username, with `speaker_role` (primary speaker, co-speaker, moderator, panelist) | V7 |
| `event_types` | Event type templates (slot configuration, timings) | V10 |
| `topics` | Topic backlog with usage and staleness tracking | V14 |
| `topic_usage_history` | Which topic was used for which event, feeds staleness | V14 |
| `speaker_pool` | Per-event speaker workflow state machine (ADR-009); no talk content | V14 |
| `speaker_outreach_history` | Organizer contact attempts and automated reminders per speaker | V16 |
| `task_templates` | Reusable event planning task templates | V22 |
| `event_tasks` | Tasks per event assigned to organizers | V22 |
| `speaker_status_history` | Audit trail of speaker workflow transitions | V23 |
| `session_timing_history` | Audit trail of session time changes during slot assignment | V28 |
| `speaker_slot_preferences` | Speaker preferences for agenda slots | V28 |
| `publishing_config` | Per-event auto-publishing settings (speakers, agenda) | V28 |
| `workflow_state_migration_log` | Audit log of the V30 consolidation from 16 to 9 event states | V30 |
| `shedlock` | ShedLock distributed scheduler locks; shared with CUMS | V31 |
| `notifications` | Notification delivery audit trail and in-app notifications | V33 |
| `session_materials` | Uploaded slides, documents and videos per session | V41 |
| `session_content_history` | Versioned title and abstract submissions per session, keyed by `(session_id, submission_version)` | V53 (as `speaker_content_submissions`, renamed in V99) |
| `speaker_reminder_log` | Sent deadline reminders, for deduplication and audit | V54 |
| `speaker_arrivals` | Speaker arrival confirmations on the event day | V55 |
| `email_templates` | DB-backed email templates, seeded from the classpath, editable by organizers | V62 |
| `newsletter_subscribers` | Newsletter subscriptions | V67 |
| `newsletter_sends` | One row per newsletter send | V67 |
| `newsletter_recipients` | Per-recipient delivery status of a newsletter send | V67 |
| `ai_generation_log` | Cost and usage log of AI generation calls | V77 |
| `event_photos` | Event photo gallery (S3 keys, CDN URLs) | V79 |
| `event_teaser_images` | Teaser images for the moderator presentation page | V80 |
| `ai_prompts` | Organizer-editable AI prompts with their defaults | V82 |
| `app_settings` | Key-value application settings (for example forwarding recipients, Mail Center switch) | V88 |
| `session_proposals` | Talk proposals from attendee self-nominations (Story 7.2, ADR-012) | V109 |
| `organizer_thanks` | Post-event "thank the organizers" messages (Story 7.4) | V111 |
| `session_qna_window` | One time-boxed post-event Q&A window per session (Story 7.5) | V112 |
| `session_qna_post` | Questions and answers in a Q&A window | V112 |
| `session_qna_notification` | Per-recipient digest state of Q&A notifications (Story 15.7) | V117 |
| `live_timing_presence` | Organizer presence for the live-timing poll (Story 15.1) | V119 |
| `event_agenda_config` | Per-event copy-on-edit override of the event type template (Story 15.2) | V121 |
| `email_forwarding_log` | One row per inbound mail S3 object, idempotent alias forwarding; no addresses or bodies | V128 |
| `mail_message` | Every outbound and inbound mail (Mail Center, Epic 16) | V135 |
| `mail_recipient` | One row per recipient address of a `mail_message` | V135 |
| `mail_conversation` | Inbound replies grouped into conversations with status and assignee | V139 |
| `mail_automatic_event` | Counts of automatic mail (out-of-office, bounces, loops), without content | V139 |
| `user_merge_log` | One row per merge of two profiles of the same person (#1183) | V144 |

Dropped and no longer present: `publishing_versions` (created V28, dropped V89), `speakers`
(created V37, dropped V94, see ADR-009), `speaker_invitation_tokens` (magic-link tokens, created
V43, dropped V104).

### Company User Management Service (CUMS)

| Table | Purpose | Introduced |
|---|---|---|
| `companies` | Companies with Swiss business data and logo reference | V3 |
| `user_profiles` | Users: UUID primary key internally, `username` as public identifier | V4 |
| `role_assignments` | Roles per user (ORGANIZER, SPEAKER, PARTNER, ATTENDEE) | V5 |
| `activity_history` | User activity tracking | V6 |
| `logos` | Generic file upload state machine; shared with EMS | V10 |
| `shedlock` | ShedLock distributed scheduler locks; shared with EMS | V12 |
| `watch_pairings` | Pairing codes for the Apple Watch organizer app | V13 |
| `presentation_settings` | Single-row settings for the moderator presentation page | V14 |
| `user_additional_emails` | Additional email addresses per user profile (Story 10.32) | V16 |

### Partner Coordination Service (PCS)

| Table | Purpose | Introduced |
|---|---|---|
| `partners` | Partner companies, referenced by `company_name` (ADR-003) | V2 |
| `partner_meetings` | Partner meetings | V2 |
| `partner_meeting_attendance` | Attendance per partner meeting | V2 |
| `topic_suggestions` | Topic suggestions by partners | V4 (V2 version dropped and recreated in V4) |
| `topic_votes` | Partner votes on topic suggestions | V4 (V2 version dropped and recreated in V4) |
| `partner_notes` | Organizer notes per partner | V7 |
| `partner_meeting_rsvps` | Calendar RSVP replies to partner meeting invitations | V9 |

Dropped and no longer present: `partner_contacts` (created V2, dropped V6).

### Speaker Coordination Service

Per **ADR-009 (Unified Speaker Workflow)** and the **Story 11.E.8 consolidation**, the
`speakers` table is gone (EMS V94) and `speaker-coordination-service` owns no entity. SPEAKER is
a role on `User` (`role_assignments.role = 'SPEAKER'`). Per-event speaker workflow state lives
on `speaker_pool` in EMS. Per-event content lives on `sessions` (canonical current title and
description) plus `session_content_history`. `session_users` is the junction; sessions reference
users by `username` (ADR-003). The denormalised columns `speaker_pool.content_status`,
`speaker_pool.content_submitted_at`, `speaker_pool.initial_presentation_title` and
`session_users.presentation_title` were dropped in V102; the content status is derived by
`ContentStatusDeriver`.

### Business Rule Enforcement

The "at least two organizers" rule is enforced in application code, not by a database trigger:
`RoleService` in CUMS (`MINIMUM_ORGANIZERS = 2`) throws `MinimumOrganizersException` when a role
change would leave fewer than two organizers.

**See also:** [06d-notification-system.md](./06d-notification-system.md) for the notification
architecture, and [Story 1.15a.10: Notifications API Consolidation](../stories/archived/epic-1/1.15a.10.notifications-api-consolidation.md).

## Data Consistency and Cross-Service Communication

### Domain Events

```typescript
// Shared domain events for data consistency
interface DomainEvent {
  id: string;
  aggregateId: string;
  aggregateType: string;
  eventType: string;
  eventVersion: number;
  occurredAt: Date;
  data: Record<string, any>;
}

// Example events
interface CompanyCreatedEvent extends DomainEvent {
  eventType: 'CompanyCreated';
  data: {
    companyId: string;
    name: string;
    isVerified: boolean;
  };
}

interface SpeakerInvitedEvent extends DomainEvent {
  eventType: 'SpeakerInvited';
  data: {
    speakerId: string;
    sessionId: string;
    eventId: string;
    invitedAt: Date;
  };
}
```

### Data Synchronization Strategy

1. **Eventual Consistency:** Services maintain local copies of essential data from other domains
2. **Event-Driven Updates:** Domain events propagate changes across service boundaries
3. **Saga Pattern:** Complex cross-service transactions use choreographed sagas
4. **Read Replicas:** Frequently accessed cross-domain data is cached locally