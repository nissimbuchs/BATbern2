# Data Architecture

This document outlines the comprehensive data model and database design for the BATbern Event Management Platform, following Domain-Driven Design principles with separate databases per bounded context.

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
  swissUID?: string;  // Swiss business ID (CHE-XXX.XXX.XXX)
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

#### Relationships
- **Many-to-One:** User → Company (via `User.companyId`, managed by User Service)
- **One-to-Many:** User → ActivityHistory (activity tracking)
- **Cross-Service Integration:**
  - Speaker Service: Associates speakers with user accounts
  - Partner Service: Associates partner contacts with user accounts
  - Event Service: Associates event registrations with users
  - Attendee Service: Associates attendee profiles with users

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

### Speaker

**Purpose:** Individual speakers with speaking-specific workflow and expertise data. Speaker profile extends User entity per ADR-004.

**ADR-004 Reference Pattern:**
- Speaker references User entity via `userId` (UUID foreign key, internal)
- API uses `username` as public identifier (e.g., `GET /speakers/john.doe`)
- User fields (email, name, bio, photo, company) stored in User entity (NEVER duplicated)
- Speaker entity contains ONLY domain-specific fields (availability, expertise, speaking history)

**Key Attributes:**
- id: UUID - Unique speaker identifier
- userId: UUID - Foreign key to User entity (internal, NOT exposed in API)
- availability: SpeakerAvailability - Current availability status
- workflowState: SpeakerWorkflowState - Speaker coordination workflow state
- expertiseAreas: string[] - Areas of technical expertise
- speakingTopics: string[] - Topics the speaker can present
- speakingHistory: SpeakingEngagement[] - Past session participation
- linkedInUrl: string - LinkedIn profile (speaker-specific social media)
- twitterHandle: string - Twitter/X handle (speaker-specific social media)
- certifications: string[] - Professional certifications
- languages: string[] - Languages speaker can present in
- slotPreferences: SpeakerSlotPreferences - Time slot preferences per event

**Fields Stored in User Entity (NOT in Speaker):**
- ❌ email, firstName, lastName (from User.email, User.firstName, User.lastName)
- ❌ bio (from User.bio - single source of truth)
- ❌ profilePhotoUrl (from User.profilePictureUrl)
- ❌ companyId (from User.companyId)
- ❌ position (removed entirely per ADR-004)

#### TypeScript Interface
```typescript
// Speaker entity references User (per ADR-004)
interface Speaker {
  id: string;                              // UUID (internal)
  userId: string;                          // UUID FK to User.id (internal)

  // API responses include User fields by joining:
  // username: string;                     // From User (API identifier)
  // email: string;                        // From User
  // firstName: string;                    // From User
  // lastName: string;                     // From User
  // bio: string;                          // From User (single bio)
  // profilePictureUrl: string;            // From User
  // companyId: string;                    // From User

  // Speaker-specific fields:
  availability: SpeakerAvailability;
  workflowState: SpeakerWorkflowState;
  expertiseAreas: string[];
  speakingTopics: string[];
  linkedInUrl?: string;
  twitterHandle?: string;
  certifications: string[];
  languages: string[];
  speakingHistory: SpeakingEngagement[];
  slotPreferences: SpeakerSlotPreferences;
  qualityReview: QualityReviewStatus;
  communicationPreferences: ContactPreferences;
  createdAt: Date;
  updatedAt: Date;
}

enum SpeakerWorkflowState {
  OPEN = 'open',
  CONTACTED = 'contacted',
  READY = 'ready',
  DECLINED = 'declined',
  ACCEPTED = 'accepted',
  SLOT_ASSIGNED = 'slot_assigned',
  QUALITY_REVIEWED = 'quality_reviewed',
  FINAL_AGENDA = 'final_agenda'
}

enum SpeakerAvailability {
  AVAILABLE = 'available',
  BUSY = 'busy',
  UNAVAILABLE = 'unavailable'
}
```

#### Relationships
- **One-to-One:** Speaker → User (via `Speaker.userId` FK to `User.id`)
- **Many-to-One:** Speaker → Company (via `User.companyId`, transitive through User)
- **Many-to-Many:** Speaker ↔ Sessions (speakers can present multiple sessions)
- **One-to-Many:** Speaker → SpeakingEngagements (historical session participation)

#### API Pattern (ADR-003 + ADR-004)
```http
# Public API uses username identifier
GET /api/v1/speakers/john.doe

# Response combines User + Speaker via JPQL join
{
  "username": "john.doe",           // From User (public ID)
  "email": "john@example.com",      // From User
  "firstName": "John",               // From User
  "lastName": "Doe",                 // From User
  "bio": "Experienced architect",    // From User
  "profilePictureUrl": "https://...",// From User
  "company": "GoogleZH",             // From User.companyId
  "availability": "available",       // From Speaker
  "expertiseAreas": ["Security"],    // From Speaker
  "speakingTopics": ["Blockchain"]   // From Speaker
}
```

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
  status: EventStatus;
  workflowState: EventWorkflowState;
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

enum EventStatus {
  PLANNING = 'planning',
  TOPIC_DEFINED = 'topic_defined',
  SPEAKERS_INVITED = 'speakers_invited',
  AGENDA_DRAFT = 'agenda_draft',
  PUBLISHED = 'published',
  REGISTRATION_OPEN = 'registration_open',
  REGISTRATION_CLOSED = 'registration_closed',
  IN_PROGRESS = 'in_progress',
  COMPLETED = 'completed',
  ARCHIVED = 'archived'
}

enum EventType {
  FULL_DAY = 'full_day',        // 6-8 slots
  AFTERNOON = 'afternoon',      // 6-8 slots
  EVENING = 'evening'           // 3-4 slots
}

enum EventWorkflowState {
  TOPIC_SELECTION = 'topic_selection',
  SPEAKER_BRAINSTORMING = 'speaker_brainstorming',
  SPEAKER_ASSIGNMENT = 'speaker_assignment',
  SPEAKER_OUTREACH = 'speaker_outreach',
  CONTENT_COLLECTION = 'content_collection',
  QUALITY_REVIEW = 'quality_review',
  SLOT_ASSIGNMENT = 'slot_assignment',
  AGENDA_FINALIZATION = 'agenda_finalization',
  PUBLISHED = 'published'
}

interface EventSlotConfiguration {
  eventType: EventType;
  minSlots: number;
  maxSlots: number;
  slotDuration: number; // minutes
  theoreticalSlotsAM: boolean; // theoretical presentations in morning
  breakSlots: number;
  lunchSlots: number;
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
  eventId: string;
  registrationDate: Date;
  status: RegistrationStatus;
  sessionPreferences: string[];
  specialRequests?: string;
  attendanceConfirmed?: boolean;
  actualAttendance: boolean;
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

### Event Management Service Database Schema

```sql
-- Events table (aggregate root)
CREATE TABLE events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_number INTEGER UNIQUE NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    event_date TIMESTAMP WITH TIME ZONE NOT NULL,
    registration_deadline TIMESTAMP WITH TIME ZONE NOT NULL,
    venue_name VARCHAR(255) NOT NULL,
    venue_address TEXT NOT NULL,
    venue_capacity INTEGER NOT NULL,
    status VARCHAR(50) NOT NULL CHECK (status IN (
        'planning', 'topic_defined', 'speakers_invited', 'agenda_draft',
        'published', 'registration_open', 'registration_closed',
        'in_progress', 'completed', 'archived'
    )),
    organizer_id UUID NOT NULL,
    current_attendee_count INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    published_at TIMESTAMP WITH TIME ZONE,
    metadata JSONB DEFAULT '{}'
);

-- Sessions table
CREATE TABLE sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    session_type VARCHAR(50) NOT NULL CHECK (session_type IN (
        'keynote', 'presentation', 'workshop', 'panel_discussion',
        'networking', 'break', 'lunch'
    )),
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    room VARCHAR(100),
    capacity INTEGER,
    language VARCHAR(10) DEFAULT 'de',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Session-User junction table (many-to-many speakers)
-- Story 4.1.4: ADR-004 pattern - references User via user_id FK
CREATE TABLE session_users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id UUID NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    user_id UUID NOT NULL, -- FK to user_profiles.id in company-user-management-service
    speaker_role VARCHAR(50) NOT NULL CHECK (speaker_role IN (
        'primary_speaker', 'co_speaker', 'moderator', 'panelist'
    )),
    presentation_title VARCHAR(255),
    is_confirmed BOOLEAN DEFAULT FALSE,
    invited_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    confirmed_at TIMESTAMP WITH TIME ZONE,
    declined_at TIMESTAMP WITH TIME ZONE,
    decline_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(session_id, user_id)
);

-- Topics table with usage tracking
CREATE TABLE topics (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title VARCHAR(500) NOT NULL,
    description TEXT,
    category VARCHAR(50) NOT NULL CHECK (category IN (
        'technical', 'management', 'soft_skills',
        'industry_trends', 'tools_platforms'
    )),
    created_date TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    last_used_date TIMESTAMP WITH TIME ZONE,
    usage_count INTEGER DEFAULT 0,
    staleness_score NUMERIC(5,2) DEFAULT 100.00,
    calculated_wait_period INTEGER, -- in months
    partner_influence_score NUMERIC(5,2),
    is_active BOOLEAN DEFAULT TRUE,
    title_vector tsvector GENERATED ALWAYS AS (to_tsvector('english', title)) STORED,
    description_vector tsvector GENERATED ALWAYS AS (to_tsvector('english', coalesce(description, ''))) STORED,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Topic usage history
CREATE TABLE topic_usage_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    topic_id UUID NOT NULL REFERENCES topics(id) ON DELETE CASCADE,
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    used_date TIMESTAMP WITH TIME ZONE NOT NULL,
    attendee_count INTEGER,
    feedback_score NUMERIC(3,2),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Indexes for performance
CREATE INDEX idx_events_status ON events(status);
CREATE INDEX idx_events_event_date ON events(event_date);
CREATE INDEX idx_sessions_event_id ON sessions(event_id);
CREATE INDEX idx_session_users_session_id ON session_users(session_id);
CREATE INDEX idx_session_users_user_id ON session_users(user_id);
CREATE INDEX idx_session_users_confirmed ON session_users(is_confirmed);
CREATE INDEX idx_topics_title_vector ON topics USING GIN(title_vector);
CREATE INDEX idx_topics_description_vector ON topics USING GIN(description_vector);
CREATE INDEX idx_topics_last_used ON topics(last_used_date);
CREATE INDEX idx_topics_staleness ON topics(staleness_score);
CREATE INDEX idx_topics_active ON topics(is_active);
CREATE INDEX idx_topic_usage_history_topic_id ON topic_usage_history(topic_id);
CREATE INDEX idx_topic_usage_history_used_date ON topic_usage_history(used_date DESC);
```

### Speaker Coordination Service Database Schema

```sql
-- Speakers table (ADR-004: References User, no duplicated fields)
CREATE TABLE speakers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE,  -- FK to users.id (one-to-one)

    -- Domain-specific fields only (NO email, name, bio, photo, company, position)
    availability VARCHAR(50) NOT NULL CHECK (availability IN (
        'available', 'busy', 'unavailable'
    )) DEFAULT 'available',
    workflow_state VARCHAR(50) NOT NULL CHECK (workflow_state IN (
        'open', 'contacted', 'ready', 'declined', 'accepted',
        'slot_assigned', 'quality_reviewed', 'final_agenda'
    )) DEFAULT 'open',
    expertise_areas TEXT[] DEFAULT '{}',
    speaking_topics TEXT[] DEFAULT '{}',
    linkedin_url VARCHAR(255),
    twitter_handle VARCHAR(100),
    certifications TEXT[] DEFAULT '{}',
    languages VARCHAR(10)[] DEFAULT '{}',
    speaking_history JSONB DEFAULT '[]',

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    -- Foreign key constraint with cascade delete
    CONSTRAINT fk_speaker_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);

-- Indexes
CREATE UNIQUE INDEX idx_speakers_user_id ON speakers(user_id);
CREATE INDEX idx_speakers_availability ON speakers(availability);
CREATE INDEX idx_speakers_workflow_state ON speakers(workflow_state);
CREATE INDEX idx_speakers_expertise_areas ON speakers USING GIN(expertise_areas);

-- Session speaker assignments (many-to-many with roles)
CREATE TABLE session_speakers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL, -- References event service
    speaker_id UUID NOT NULL REFERENCES speakers(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL CHECK (role IN (
        'primary_speaker', 'co_speaker', 'moderator', 'panelist'
    )),
    presentation_title VARCHAR(255),
    is_confirmed BOOLEAN DEFAULT FALSE,
    invited_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    confirmed_at TIMESTAMP WITH TIME ZONE,
    declined_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(session_id, speaker_id)
);

-- Indexes
CREATE INDEX idx_session_speakers_session_id ON session_speakers(session_id);
CREATE INDEX idx_session_speakers_speaker_id ON session_speakers(speaker_id);

-- Example query: Get speaker with user data (JPQL constructor projection)
-- SELECT new SpeakerResponse(
--     u.username, u.email, u.firstName, u.lastName, u.bio, u.profilePictureUrl, c.name,
--     s.availability, s.workflowState, s.expertiseAreas, s.speakingTopics
-- )
-- FROM Speaker s
-- INNER JOIN User u ON s.userId = u.id
-- LEFT JOIN Company c ON u.companyId = c.id
-- WHERE u.username = :username
```

### Company Management Service Database Schema

```sql
-- Companies table
CREATE TABLE companies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    swiss_uid VARCHAR(20),
    website VARCHAR(500),
    industry VARCHAR(100),
    description TEXT,
    logo_url VARCHAR(1000),      -- CloudFront URL (populated after logo association)
    logo_s3_key VARCHAR(500),     -- S3 key reference
    logo_file_id VARCHAR(255),    -- References logos.upload_id (ADR-002)
    is_verified BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_by UUID NOT NULL
);

-- Note: User-company relationships are managed in User Management Service
-- Query employee count: SELECT COUNT(*) FROM users WHERE company_id = '{companyId}'
-- Note: Partnership status managed in Partner Coordination Service via Partnership.companyId
-- Note: Logo storage uses Generic File Upload Service (ADR-002)

-- Generic Logos Table (ADR-002: Generic File Upload Service)
-- Supports all entity types: COMPANY, USER, EVENT, PARTNER, etc.
CREATE TABLE logos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    upload_id VARCHAR(100) UNIQUE NOT NULL,  -- Public identifier for tracking
    s3_key VARCHAR(500) NOT NULL,             -- Current S3 key (temp or final)
    cloudfront_url VARCHAR(1000),             -- CDN URL for access
    file_extension VARCHAR(10) NOT NULL,      -- png, jpg, jpeg, svg
    file_size BIGINT NOT NULL,                -- Size in bytes
    mime_type VARCHAR(100) NOT NULL,          -- image/png, etc.
    checksum VARCHAR(100),                    -- SHA-256 for integrity

    -- State machine (ADR-002 three-phase upload pattern)
    status VARCHAR(20) NOT NULL CHECK (status IN (
        'PENDING',      -- Upload initiated, file may not exist in S3 yet
        'CONFIRMED',    -- File successfully uploaded to S3 and verified
        'ASSOCIATED'    -- Linked to entity, file in final location
    )),

    -- Entity association (populated when ASSOCIATED)
    associated_entity_type VARCHAR(50) CHECK (associated_entity_type IN (
        'COMPANY', 'USER', 'EVENT', 'PARTNER', 'SPEAKER', 'ATTENDEE'
    )),
    associated_entity_id VARCHAR(255),        -- Entity's identifier (name or username)

    -- Lifecycle timestamps
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE,      -- For automatic cleanup

    -- Constraints
    CONSTRAINT valid_association CHECK (
        (status = 'ASSOCIATED' AND associated_entity_type IS NOT NULL AND associated_entity_id IS NOT NULL) OR
        (status != 'ASSOCIATED')
    )
);

-- Indexes
CREATE INDEX idx_companies_name ON companies(name);
CREATE INDEX idx_companies_swiss_uid ON companies(swiss_uid);
CREATE INDEX idx_companies_is_verified ON companies(is_verified);
CREATE INDEX idx_companies_logo_file_id ON companies(logo_file_id);

-- Logos table indexes (ADR-002)
CREATE UNIQUE INDEX idx_logos_upload_id ON logos(upload_id);
CREATE INDEX idx_logos_status ON logos(status);
CREATE INDEX idx_logos_status_expires ON logos(status, expires_at) WHERE status != 'ASSOCIATED';
CREATE INDEX idx_logos_entity_association ON logos(associated_entity_type, associated_entity_id) WHERE status = 'ASSOCIATED';

-- ADR-002: Three-Phase Upload Pattern
-- Phase 1: POST /logos/presigned-url → Creates logo with status=PENDING, temp S3 key
-- Phase 2: Confirm upload → Updates status=CONFIRMED
-- Phase 3: Entity creation → Copies to final S3 location, status=ASSOCIATED, associates with entity
--
-- Example S3 key progression:
--   PENDING/CONFIRMED: logos/temp/{uploadId}/logo-{fileId}.png
--   ASSOCIATED:        logos/2025/companies/GoogleZH/logo-{fileId}.png
--
-- Cleanup: Scheduled job deletes PENDING > 24h, CONFIRMED > 7 days
```

### Partner Coordination Service Database Schema

```sql
-- Partners table
CREATE TABLE partners (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    company_id UUID NOT NULL UNIQUE, -- References company service
    partnership_level VARCHAR(50) NOT NULL CHECK (partnership_level IN (
        'bronze', 'silver', 'gold', 'platinum', 'strategic'
    )),
    partnership_start_date DATE NOT NULL,
    partnership_end_date DATE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Topic voting
CREATE TABLE topic_votes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    topic_id UUID NOT NULL, -- References topic in Event Management Service
    partner_id UUID NOT NULL REFERENCES partners(id) ON DELETE CASCADE,
    vote_weight INTEGER DEFAULT 1, -- Based on partnership_level
    vote_value INTEGER NOT NULL CHECK (vote_value BETWEEN 1 AND 5),
    voted_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(topic_id, partner_id)
);

-- Partner topic suggestions
CREATE TABLE topic_suggestions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    partner_id UUID NOT NULL REFERENCES partners(id) ON DELETE CASCADE,
    suggested_topic VARCHAR(500) NOT NULL,
    description TEXT,
    business_justification TEXT,
    suggested_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    status VARCHAR(50) NOT NULL CHECK (status IN (
        'submitted', 'under_review', 'accepted', 'rejected', 'implemented'
    )) DEFAULT 'submitted',
    reviewed_at TIMESTAMP WITH TIME ZONE,
    reviewed_by UUID -- References organizer
);

-- Partner meetings
CREATE TABLE partner_meetings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    meeting_type VARCHAR(50) NOT NULL CHECK (meeting_type IN ('spring', 'autumn', 'ad_hoc')),
    scheduled_date DATE NOT NULL,
    location VARCHAR(255),
    agenda TEXT,
    materials_generated BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Partner meeting attendance
CREATE TABLE partner_meeting_attendance (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    meeting_id UUID NOT NULL REFERENCES partner_meetings(id) ON DELETE CASCADE,
    partner_id UUID NOT NULL REFERENCES partners(id) ON DELETE CASCADE,
    rsvp_status VARCHAR(50) NOT NULL CHECK (rsvp_status IN (
        'invited', 'accepted', 'declined', 'tentative', 'attended'
    )) DEFAULT 'invited',
    rsvp_at TIMESTAMP WITH TIME ZONE,
    attended BOOLEAN DEFAULT FALSE,
    UNIQUE(meeting_id, partner_id)
);

-- Indexes
CREATE INDEX idx_partners_company_id ON partners(company_id);
CREATE INDEX idx_partners_active ON partners(is_active);
CREATE INDEX idx_topic_votes_partner_id ON topic_votes(partner_id);
CREATE INDEX idx_topic_votes_topic_id ON topic_votes(topic_id);
CREATE INDEX idx_topic_suggestions_partner_id ON topic_suggestions(partner_id);
CREATE INDEX idx_topic_suggestions_status ON topic_suggestions(status);
CREATE INDEX idx_partner_meetings_date ON partner_meetings(scheduled_date);
CREATE INDEX idx_partner_meeting_attendance_meeting_id ON partner_meeting_attendance(meeting_id);
CREATE INDEX idx_partner_meeting_attendance_partner_id ON partner_meeting_attendance(partner_id);
```

### Attendee Experience Service Database Schema

```sql
-- Attendees table (ADR-004: References User, no duplicated fields)
CREATE TABLE attendees (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE,  -- FK to users.id (one-to-one)

    -- Domain-specific fields only (NO email, name, company, position)
    newsletter_subscription BOOLEAN DEFAULT FALSE,
    content_preferences JSONB DEFAULT '{}', -- Topics of interest (different from User.preferences)

    -- GDPR consent tracking
    gdpr_consent_given BOOLEAN DEFAULT FALSE,
    gdpr_consent_date TIMESTAMP WITH TIME ZONE,
    gdpr_consent_ip VARCHAR(45), -- IPv4 or IPv6
    gdpr_consent_version VARCHAR(20),

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    -- Foreign key constraint with cascade delete
    CONSTRAINT fk_attendee_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);

-- Indexes
CREATE UNIQUE INDEX idx_attendees_user_id ON attendees(user_id);
CREATE INDEX idx_attendees_newsletter ON attendees(newsletter_subscription) WHERE newsletter_subscription = true;

-- Event registrations (many-to-many)
CREATE TABLE event_registrations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL, -- References event service
    attendee_id UUID NOT NULL REFERENCES attendees(id) ON DELETE CASCADE,
    registration_date TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    status VARCHAR(50) NOT NULL CHECK (status IN (
        'registered', 'waitlisted', 'confirmed', 'cancelled', 'attended'
    )) DEFAULT 'registered',
    special_requests TEXT,
    attendance_confirmed BOOLEAN DEFAULT FALSE,
    actual_attendance BOOLEAN DEFAULT FALSE,
    session_preferences TEXT[] DEFAULT '{}',
    UNIQUE(event_id, attendee_id)
);

-- Content engagement tracking
CREATE TABLE content_engagement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    attendee_id UUID NOT NULL REFERENCES attendees(id) ON DELETE CASCADE,
    content_type VARCHAR(50) NOT NULL, -- 'session', 'document', 'video', etc.
    content_id UUID NOT NULL,
    engagement_type VARCHAR(50) NOT NULL, -- 'view', 'download', 'rating', etc.
    engagement_value DECIMAL(5,2), -- rating score or duration
    engaged_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_event_registrations_event_id ON event_registrations(event_id);
CREATE INDEX idx_event_registrations_attendee_id ON event_registrations(attendee_id);
CREATE INDEX idx_event_registrations_status ON event_registrations(status);
CREATE INDEX idx_content_engagement_attendee_id ON content_engagement(attendee_id);
CREATE INDEX idx_content_engagement_content ON content_engagement(content_type, content_id);

-- Example query: Get attendee with user data (JPQL constructor projection)
-- SELECT new AttendeeResponse(
--     u.username, u.email, u.firstName, u.lastName, c.name,
--     a.newsletterSubscription, a.contentPreferences, a.gdprConsent
-- )
-- FROM Attendee a
-- INNER JOIN User u ON a.userId = u.id
-- LEFT JOIN Company c ON u.companyId = c.id
-- WHERE u.username = :username
```

## User Role Management Tables

```sql
-- User Roles with History
CREATE TABLE user_roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    role VARCHAR(50) NOT NULL CHECK (role IN ('ORGANIZER', 'SPEAKER', 'PARTNER', 'ATTENDEE')),
    event_id UUID REFERENCES events(id), -- Optional: role scoped to specific event
    granted_by UUID NOT NULL, -- References users
    granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP,
    revoked_by UUID, -- References users
    reason TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT unique_active_user_role UNIQUE (user_id, role, event_id) WHERE is_active = true,
    CONSTRAINT fk_granted_by FOREIGN KEY (granted_by) REFERENCES users(id),
    CONSTRAINT fk_revoked_by FOREIGN KEY (revoked_by) REFERENCES users(id)
);

CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX idx_user_roles_active ON user_roles(is_active, user_id);
CREATE INDEX idx_user_roles_event ON user_roles(event_id) WHERE event_id IS NOT NULL;

-- Role Change Requests (for approval workflows)
CREATE TABLE role_change_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    current_role VARCHAR(50) NOT NULL,
    requested_role VARCHAR(50) NOT NULL,
    requested_by UUID NOT NULL, -- Organizer initiating change
    requires_approval_from UUID, -- User who must approve (for organizer demotions)
    reason TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP + INTERVAL '7 days'),

    CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_requested_by FOREIGN KEY (requested_by) REFERENCES users(id),
    CONSTRAINT fk_approval_from FOREIGN KEY (requires_approval_from) REFERENCES users(id)
);

CREATE INDEX idx_role_requests_user ON role_change_requests(user_id);
CREATE INDEX idx_role_requests_status ON role_change_requests(status, expires_at);
CREATE INDEX idx_role_requests_approver ON role_change_requests(requires_approval_from) WHERE requires_approval_from IS NOT NULL;

-- Role Change Approvals
CREATE TABLE role_change_approvals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_change_request_id UUID NOT NULL,
    approved_by UUID NOT NULL, -- User providing approval
    approved BOOLEAN NOT NULL,
    comments TEXT,
    approved_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_role_change_request FOREIGN KEY (role_change_request_id) REFERENCES role_change_requests(id),
    CONSTRAINT fk_approved_by FOREIGN KEY (approved_by) REFERENCES users(id),
    CONSTRAINT unique_approval_per_request UNIQUE (role_change_request_id, approved_by)
);

CREATE INDEX idx_approvals_request ON role_change_approvals(role_change_request_id);
```

### Notification Service Database Schema

```sql
-- Email Templates Table
CREATE TABLE email_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_type VARCHAR(50) NOT NULL, -- speaker_invitation, deadline_reminder, newsletter, etc.
    language VARCHAR(2) NOT NULL DEFAULT 'de',
    subject VARCHAR(200) NOT NULL,
    html_body TEXT NOT NULL,
    text_body TEXT NOT NULL,
    variables JSONB NOT NULL, -- List of available template variables
    version INTEGER NOT NULL DEFAULT 1,
    is_active BOOLEAN DEFAULT true,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(template_type, language, version)
);

CREATE INDEX idx_email_templates_type_lang ON email_templates(template_type, language) WHERE is_active = true;

-- Notification Preferences Table
CREATE TABLE notification_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    channel VARCHAR(20) NOT NULL, -- email, in_app, push, sms
    notification_type VARCHAR(50) NOT NULL, -- event_update, speaker_deadline, etc.
    is_enabled BOOLEAN DEFAULT true,
    frequency VARCHAR(20) DEFAULT 'immediate', -- immediate, daily_digest, weekly_digest
    quiet_hours_start TIME,
    quiet_hours_end TIME,
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id, channel, notification_type)
);

CREATE INDEX idx_notification_prefs_user ON notification_preferences(user_id);

-- Escalation Rules Table
CREATE TABLE escalation_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID REFERENCES events(id),
    rule_type VARCHAR(50) NOT NULL, -- speaker_deadline, content_review, etc.
    tier_1_hours_before INTEGER NOT NULL, -- Hours before deadline for reminder
    tier_2_hours_before INTEGER NOT NULL, -- Hours before deadline for warning
    tier_3_hours_before INTEGER NOT NULL, -- Hours before deadline for critical alert
    escalation_threshold INTEGER, -- Minutes after critical with no action
    backup_organizer_ids UUID[], -- Array of backup organizer IDs
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT NOW(),
    CONSTRAINT valid_tiers CHECK (tier_1_hours_before > tier_2_hours_before AND tier_2_hours_before > tier_3_hours_before)
);

CREATE INDEX idx_escalation_rules_event ON escalation_rules(event_id) WHERE is_active = true;

-- Notification Log Table
CREATE TABLE notification_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    notification_type VARCHAR(50) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    template_id UUID REFERENCES email_templates(id),
    subject VARCHAR(200),
    sent_at TIMESTAMP DEFAULT NOW(),
    delivery_status VARCHAR(20) DEFAULT 'sent', -- sent, delivered, bounced, failed
    ses_message_id VARCHAR(100),
    error_message TEXT,
    metadata JSONB
);

CREATE INDEX idx_notification_log_user_sent ON notification_log(user_id, sent_at DESC);
CREATE INDEX idx_notification_log_ses_message ON notification_log(ses_message_id);
CREATE INDEX idx_notification_log_delivery_status ON notification_log(delivery_status, sent_at DESC);
```

### Business Rule Enforcement

```sql
-- Function to enforce minimum 2 organizers rule
CREATE OR REPLACE FUNCTION enforce_minimum_organizers()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.role = 'ORGANIZER' AND NEW.is_active = false THEN
        IF (SELECT COUNT(*) FROM user_roles
            WHERE role = 'ORGANIZER'
            AND is_active = true
            AND (event_id = NEW.event_id OR (event_id IS NULL AND NEW.event_id IS NULL))
            AND id != NEW.id) < 2 THEN
            RAISE EXCEPTION 'Cannot demote organizer: minimum 2 organizers required';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER check_minimum_organizers
    BEFORE UPDATE ON user_roles
    FOR EACH ROW
    WHEN (OLD.is_active = true AND NEW.is_active = false)
    EXECUTE FUNCTION enforce_minimum_organizers();
```

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
    swissUID?: string;
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