# Data Architecture

This document outlines the comprehensive data model and database design for the BATbern Event Management Platform, following Domain-Driven Design principles with separate databases per bounded context.

## Data Models

### Company

**Purpose:** Centralized company entity for speakers, partners, and attendees with logo management and partner company recognition.

**Key Attributes:**
- id: UUID - Unique company identifier
- name: string - Official company name
- isPartner: boolean - Whether company is a BATbern partner
- logo: CompanyLogo - Uploaded logo with metadata
- website: string - Company website URL
- industry: string - Industry sector classification
- employeeCount: number - Approximate employee count
- headquarters: Address - Primary company location

#### TypeScript Interface
```typescript
interface Company {
  id: string;
  name: string;
  displayName: string;
  isPartner: boolean;
  logo?: CompanyLogo;
  website?: string;
  industry: string;
  employeeCount?: number;
  headquarters?: Address;
  description?: string;
  socialLinks: SocialLinks;
  createdAt: Date;
  updatedAt: Date;
  createdBy: string; // User ID who created this company
}

interface CompanyLogo {
  fileId: string;
  fileName: string;
  mimeType: string;
  fileSizeBytes: number;
  s3Key: string;
  uploadedAt: Date;
  uploadedBy: string;
}
```

#### Relationships
- **One-to-Many:** Company → Speakers (speakers belong to companies)
- **One-to-One:** Company ↔ Partner (partner companies have additional partner data)
- **One-to-Many:** Company → Attendees (attendees work for companies)

### Partner

**Purpose:** Partnership-specific data for companies that sponsor BATbern events, with consistent participation across all events.

**Key Attributes:**
- id: UUID - Unique partner identifier
- companyId: UUID - Reference to company entity
- partnershipLevel: PartnershipTier - Sponsorship level and benefits
- partnershipStartDate: Date - When partnership began
- topicVotes: TopicVote[] - Historical voting records
- topicSuggestions: TopicSuggestion[] - Partner-submitted topic ideas
- meetingAttendance: PartnerMeetingAttendance[] - Meeting participation history
- contacts: PartnerContact[] - Multiple contact persons

#### TypeScript Interface
```typescript
interface Partner {
  id: string;
  companyId: string;
  partnershipLevel: PartnershipTier;
  partnershipStartDate: Date;
  partnershipEndDate?: Date;
  isActive: boolean;
  contacts: PartnerContact[];
  topicVotes: TopicVote[];
  topicSuggestions: TopicSuggestion[];
  meetingAttendance: PartnerMeetingAttendance[];
  benefits: PartnershipBenefits;
  createdAt: Date;
  updatedAt: Date;
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
- **One-to-One:** Partner → Company (partner data extends company)
- **One-to-Many:** Partner → PartnerContacts (multiple contact persons)
- **One-to-Many:** Partner → EmployeeAttendanceRecords (cross-event attendance tracking)

### Speaker

**Purpose:** Individual speakers with company affiliations and session assignments across multiple events.

**Key Attributes:**
- id: UUID - Unique speaker identifier
- companyId: UUID - Reference to company entity
- profile: SpeakerProfile - Bio, expertise, contact information
- speakingHistory: SpeakingEngagement[] - Past session participation
- availability: SpeakerAvailability - Current availability status

#### TypeScript Interface
```typescript
interface Speaker {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  companyId: string;
  position: string;
  profile: SpeakerProfile;
  availability: SpeakerAvailability;
  workflowState: SpeakerWorkflowState;
  slotPreferences: SpeakerSlotPreferences;
  qualityReview: QualityReviewStatus;
  communicationPreferences: ContactPreferences;
  speakingHistory: SpeakingEngagement[];
  createdAt: Date;
  updatedAt: Date;
}

interface SpeakerProfile {
  shortBio: string;
  detailedBio: string;
  expertiseAreas: string[];
  profilePhotoUrl?: string;
  linkedInUrl?: string;
  twitterHandle?: string;
  certifications: string[];
  languages: string[];
  speakingTopics: string[];
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
- **Many-to-One:** Speaker → Company (speaker belongs to company)
- **Many-to-Many:** Speaker ↔ Sessions (speakers can present multiple sessions)
- **One-to-Many:** Speaker → SpeakingEngagements (historical session participation)

### Session

**Purpose:** Individual agenda items with multiple speaker support and comprehensive material management.

**Key Attributes:**
- id: UUID - Unique session identifier
- eventId: UUID - Parent event reference
- speakers: SessionSpeaker[] - Multiple speakers with roles
- schedule: SessionSchedule - Timing and location details
- materials: SessionMaterials - Presentation files and resources

#### TypeScript Interface
```typescript
interface Session {
  id: string;
  eventId: string;
  title: string;
  description: string;
  sessionType: SessionType;
  speakers: SessionSpeaker[]; // Multiple speakers
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

interface SessionSpeaker {
  speakerId: string;
  role: SpeakerRole;
  presentationTitle?: string; // Speaker-specific title if different
  workflowState: SpeakerWorkflowState;
  isConfirmed: boolean;
  invitedAt: Date;
  confirmedAt?: Date;
  declinedAt?: Date;
  declineReason?: string;
  slotAssignment?: SlotAssignment;
  qualityReview?: ContentQualityReview;
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
- **Many-to-Many:** Session ↔ Speakers (multiple speakers per session)
- **One-to-Many:** Session → AttendeeRatings (feedback and ratings)
- **One-to-Many:** Session → Materials (uploaded files and resources)

### Event

**Purpose:** Conference events with attendee registrations and session management, no direct speaker relationships.

#### TypeScript Interface
```typescript
interface Event {
  id: string;
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
  organizerId: string;
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

#### Content Metadata

**Purpose:** Track all uploaded files with S3 references, checksums, and quota management.

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
  cloudFrontUrl?: string; // CDN URL if published
  metadata: Record<string, string>; // Custom metadata
  createdAt: Date;
  updatedAt: Date;
}

enum ContentType {
  PRESENTATION = 'presentation',
  LOGO = 'logo',
  SPEAKER_PHOTO = 'speaker_photo',
  SPEAKER_CV = 'speaker_cv',
  EVENT_PHOTO = 'event_photo',
  ARCHIVE_MATERIAL = 'archive_material'
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
  fileUrl: string; // CloudFront CDN URL
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

```sql
-- Content metadata table
CREATE TABLE content_metadata (
    file_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    s3_bucket VARCHAR(255) NOT NULL,
    s3_key VARCHAR(1000) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    checksum VARCHAR(64), -- SHA-256 hash
    content_type VARCHAR(50) NOT NULL CHECK (content_type IN (
        'presentation', 'logo', 'speaker_photo', 'speaker_cv',
        'event_photo', 'archive_material'
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

**Purpose:** Conference participants with company affiliations and multi-event participation tracking.

#### TypeScript Interface
```typescript
interface Attendee {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  companyId?: string; // Optional company affiliation
  position?: string;
  profile: AttendeeProfile;
  preferences: ContentPreferences;
  engagementHistory: AttendeeEngagement;
  eventRegistrations: EventRegistration[]; // Many-to-many with events
  newsletterSubscription: boolean;
  gdprConsent: GDPRConsent;
  createdAt: Date;
  updatedAt: Date;
  lastLoginAt?: Date;
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
```

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
-- Speakers table
CREATE TABLE speakers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) UNIQUE NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    company_id UUID NOT NULL, -- References company service
    position VARCHAR(255),
    short_bio TEXT,
    detailed_bio TEXT,
    expertise_areas TEXT[] DEFAULT '{}',
    availability VARCHAR(50) NOT NULL CHECK (availability IN (
        'available', 'busy', 'unavailable', 'invited', 'confirmed', 'declined'
    )) DEFAULT 'available',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Session speaker assignments (many-to-many with roles)
CREATE TABLE session_speakers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
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
CREATE INDEX idx_speakers_email ON speakers(email);
CREATE INDEX idx_speakers_company_id ON speakers(company_id);
CREATE INDEX idx_session_speakers_session_id ON session_speakers(session_id);
```

### Company Management Service Database Schema

```sql
-- Companies table
CREATE TABLE companies (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    is_partner BOOLEAN DEFAULT FALSE,
    website VARCHAR(500),
    industry VARCHAR(100),
    employee_count INTEGER,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_by UUID NOT NULL
);

-- Company logos (simplified)
CREATE TABLE company_logos (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    file_id VARCHAR(255) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    s3_key VARCHAR(500) NOT NULL,
    uploaded_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    uploaded_by UUID NOT NULL,
    is_current BOOLEAN DEFAULT TRUE
);

-- Indexes
CREATE INDEX idx_companies_name ON companies(name);
CREATE INDEX idx_companies_is_partner ON companies(is_partner);
CREATE UNIQUE INDEX idx_company_current_logo ON company_logos(company_id) WHERE is_current = TRUE;
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
-- Attendees table
CREATE TABLE attendees (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) UNIQUE NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    company_id UUID, -- References company service (optional)
    position VARCHAR(255),
    newsletter_subscription BOOLEAN DEFAULT FALSE,
    gdpr_consent_given BOOLEAN DEFAULT FALSE,
    gdpr_consent_date TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    last_login_at TIMESTAMP WITH TIME ZONE
);

-- Event registrations (many-to-many)
CREATE TABLE event_registrations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_id UUID NOT NULL, -- References event service
    attendee_id UUID NOT NULL REFERENCES attendees(id) ON DELETE CASCADE,
    registration_date TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    status VARCHAR(50) NOT NULL CHECK (status IN (
        'registered', 'waitlisted', 'confirmed', 'cancelled', 'attended'
    )) DEFAULT 'registered',
    special_requests TEXT,
    attendance_confirmed BOOLEAN DEFAULT FALSE,
    actual_attendance BOOLEAN DEFAULT FALSE,
    UNIQUE(event_id, attendee_id)
);

-- Content engagement tracking
CREATE TABLE content_engagement (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    attendee_id UUID NOT NULL REFERENCES attendees(id) ON DELETE CASCADE,
    content_type VARCHAR(50) NOT NULL, -- 'session', 'document', 'video', etc.
    content_id UUID NOT NULL,
    engagement_type VARCHAR(50) NOT NULL, -- 'view', 'download', 'rating', etc.
    engagement_value DECIMAL(5,2), -- rating score or duration
    engaged_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_attendees_email ON attendees(email);
CREATE INDEX idx_attendees_company_id ON attendees(company_id);
CREATE INDEX idx_event_registrations_event_id ON event_registrations(event_id);
CREATE INDEX idx_event_registrations_attendee_id ON event_registrations(attendee_id);
CREATE INDEX idx_content_engagement_attendee_id ON content_engagement(attendee_id);
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
    isPartner: boolean;
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