/**
 * Target schema types for BATbern microservices
 * Based on Flyway migrations (source of truth) and architecture documentation
 */

/**
 * Company entity (Company User Management Service)
 * Schema source: services/company-user-management-service/src/main/resources/db/migration/V3__Create_companies_schema.sql
 */
export interface Company {
  name: string;                   // VARCHAR(255) NOT NULL UNIQUE - meaningful ID, public identifier
  displayName: string;            // VARCHAR(255) - full official name
  website?: string;               // VARCHAR(500) - company website URL
  logoUrl?: string;               // VARCHAR(1000) - CloudFront CDN URL
  logoS3Key?: string;             // VARCHAR(500) - S3 storage key
  logoFileId?: string;            // VARCHAR(100) - file identifier
  isVerified: boolean;            // BOOLEAN NOT NULL DEFAULT FALSE
}

/**
 * User profile entity (Company User Management Service)
 * Schema source: services/company-user-management-service/src/main/resources/db/migration/V4__Create_user_profiles_table.sql
 */
export interface User {
  id: string;                     // UUID PRIMARY KEY (internal database key, NOT exposed in API)
  username: string;               // VARCHAR(100) NOT NULL UNIQUE (public API identifier per Story 1.16.2)
  firstName?: string;             // VARCHAR(100)
  lastName?: string;              // VARCHAR(100)
  email?: string;                 // VARCHAR(255)
  bio?: string;                   // TEXT (single source of truth per ADR-004)
  companyId: string;              // VARCHAR(12) FK to companies.name (NOT UUID per Story 1.16.2)
  profilePictureUrl?: string;     // VARCHAR(2048) - CloudFront CDN URL
  profilePictureS3Key?: string;   // VARCHAR(500) - S3 storage key
  profilePictureFileId?: string;  // VARCHAR(100) - file identifier
}

/**
 * Event entity (Event Management Service)
 * Schema source: services/event-management-service/src/main/resources/db/migration/V2__Create_events_schema.sql
 */
export interface Event {
  id: string;                     // UUID PRIMARY KEY (internal database key)
  eventCode: string;              // VARCHAR(50) - "BATbern56" (public API identifier)
  eventNumber: number;            // INTEGER UNIQUE NOT NULL (sequential number)
  title: string;                  // VARCHAR(255) NOT NULL
  eventDate: Date;                // TIMESTAMP WITH TIME ZONE NOT NULL
  eventType: EventType;           // VARCHAR(50) - CHECK constraint
  status: EventStatus;            // VARCHAR(50) - CHECK constraint
  workflowState: EventWorkflowState; // VARCHAR(50) - CHECK constraint
  organizerId?: string;           // UUID - organizer user ID
}

/**
 * Session entity (Event Management Service)
 * Schema source: services/event-management-service/src/main/resources/db/migration/V2__Create_events_schema.sql
 */
export interface Session {
  id: string;                     // UUID PRIMARY KEY
  eventId: string;                // UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE
  title: string;                  // VARCHAR(255) NOT NULL
  description?: string;           // TEXT
  sessionType: SessionType;       // VARCHAR(50) - CHECK constraint
}

/**
 * SessionUser junction table (Event Management Service)
 * Schema source: services/event-management-service/src/main/resources/db/migration/V7__Add_session_users_junction_table.sql
 */
export interface SessionUser {
  id: string;                     // UUID PRIMARY KEY
  sessionId: string;              // UUID NOT NULL REFERENCES sessions(id) ON DELETE CASCADE
  userId: string;                 // UUID NOT NULL (FK to user_profiles.id in company-user-management-service)
  speakerRole: SpeakerRole;       // VARCHAR(50) - primary_speaker, co_speaker, moderator, panelist
  isConfirmed: boolean;           // BOOLEAN NOT NULL DEFAULT TRUE
}

/**
 * Speaker entity (Speaker Coordination Service)
 * Schema source: services/speaker-coordination-service (ADR-004 pattern)
 */
export interface Speaker {
  id: string;                     // UUID PRIMARY KEY
  userId: string;                 // UUID NOT NULL (FK to user_profiles.id, cross-service)
  availability: SpeakerAvailability;     // VARCHAR(50)
  workflowState: SpeakerWorkflowState;   // VARCHAR(50)
  expertiseAreas: string[];       // JSONB
  speakingTopics: string[];       // JSONB
  // NOTE: bio, profilePictureUrl, companyId stored in User (NOT duplicated here per ADR-004)
}

/**
 * Event type enum (per Event Management Service schema)
 */
export enum EventType {
  FULL_DAY = 'full_day',
  AFTERNOON = 'afternoon',
  EVENING = 'evening'
}

/**
 * Event status enum (per Event Management Service schema)
 */
export enum EventStatus {
  DRAFT = 'draft',
  PUBLISHED = 'published',
  ARCHIVED = 'archived',
  CANCELLED = 'cancelled'
}

/**
 * Event workflow state enum (per Event Management Service schema)
 */
export enum EventWorkflowState {
  PLANNING = 'planning',
  SPEAKER_INVITATION = 'speaker_invitation',
  CONTENT_REVIEW = 'content_review',
  PUBLISHED = 'published',
  COMPLETED = 'completed'
}

/**
 * Session type enum (per Event Management Service schema)
 */
export enum SessionType {
  PRESENTATION = 'presentation',
  PANEL_DISCUSSION = 'panel_discussion',
  WORKSHOP = 'workshop',
  KEYNOTE = 'keynote',
  NETWORKING = 'networking'
}

/**
 * Speaker role enum (per SessionUser schema)
 */
export enum SpeakerRole {
  PRIMARY_SPEAKER = 'primary_speaker',
  CO_SPEAKER = 'co_speaker',
  MODERATOR = 'moderator',
  PANELIST = 'panelist'
}

/**
 * Speaker availability enum (per Speaker Coordination Service)
 */
export enum SpeakerAvailability {
  AVAILABLE = 'available',
  LIMITED = 'limited',
  UNAVAILABLE = 'unavailable'
}

/**
 * Speaker workflow state enum (per Speaker Coordination Service)
 */
export enum SpeakerWorkflowState {
  OPEN = 'open',
  IN_REVIEW = 'in_review',
  APPROVED = 'approved',
  ARCHIVED = 'archived'
}

/**
 * S3 file mapping result
 */
export interface S3FileMapping {
  s3Key: string;                  // S3 object key
  cloudfrontUrl: string;          // CloudFront CDN URL
  fileId: string;                 // Unique file identifier (UUID)
  originalFilename: string;       // Original filename from legacy system
}
