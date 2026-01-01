/**
 * Event Management UI Types (Story 2.5.3)
 *
 * UI-specific types for Event Management Frontend.
 * Core API types are imported from generated OpenAPI types.
 *
 * This file contains ONLY:
 * - UI state types (not in API)
 * - Form state types
 * - Frontend-specific enums and helpers
 */

import type { components } from './generated/events-api.types';

// ============================================================================
// Re-export Generated API Types
// ============================================================================

export type Event = components['schemas']['Event'];
export type EventDetail = components['schemas']['EventDetail'];
export type Venue = components['schemas']['Venue'];
export type Speaker = components['schemas']['Speaker'];
export type Session = components['schemas']['Session'];
export type SessionSpeaker = components['schemas']['SessionSpeaker'];
export type Registration = components['schemas']['Registration'];
export type EventAnalytics = components['schemas']['EventAnalytics'];
export type CreateEventRequest = components['schemas']['CreateEventRequest'];
export type UpdateEventRequest = components['schemas']['UpdateEventRequest'];
export type PatchEventRequest = components['schemas']['PatchEventRequest'];
export type CreateRegistrationRequest = components['schemas']['CreateRegistrationRequest'];

// ============================================================================
// UI-Extended Types (extends API types with frontend-only fields)
// ============================================================================

/**
 * UI-Extended Session Type
 * Adds frontend-specific fields to the base Session type from API
 */
export interface SessionUI extends Session {
  // UUID id for speaker pool matching (Story 5.6)
  id?: string;
  // UI-only fields for session management (Phase 2 features)
  slotNumber?: number; // Slot number in the agenda
  speaker?: {
    speakerSlug: string;
    name: string;
    company?: string; // Speaker's company name
    email?: string;
    profilePictureUrl?: string; // Speaker's profile picture
  };
  // Note: speakers is inherited from base Session type (SessionSpeaker[])
  // Note: materialsStatus is now defined in base Session type from OpenAPI spec
}

/**
 * UI-Extended Event Type
 * Adds frontend-specific fields to the base Event type from API
 */
export interface EventUI extends Event {
  // Aliases for convenience (map API fields to UI-friendly names)
  eventDate?: string; // Alias for 'date' field
  capacity?: number; // Alias for 'venueCapacity' field

  // UI-only fields (Phase 2 features - not in backend API yet)
  venueCode?: string; // Venue identifier for dropdown selection
  version?: number; // Version number for optimistic concurrency control
  createdBy?: string; // Username who created the event
  // Note: eventType and workflowState are now in base Event type from API
}

/**
 * UI-Extended EventDetail Type
 * Extends EventDetail with booking and catering information
 * Note: venue is already included in EventDetail from OpenAPI
 */
export interface EventDetailUI extends EventDetail {
  booking?: VenueBooking;
  catering?: CateringConfig;

  // Additional UI-only fields for dashboard and detail view (Phase 2 features)
  eventDate?: string; // Alias for 'date' field
  topics?: string[]; // List of topic IDs/names
  workflowStep?: number; // Current workflow step number
  confirmedSpeakersCount?: number;
  assignedTopicsCount?: number;
  pendingMaterialsCount?: number;
  budget?: {
    allocated?: number;
    spent?: number;
    currency?: string;
  };
  // Note: eventType and workflowState are now in base Event type from API
}

// ============================================================================
// UI-Specific Types (Not in API)
// ============================================================================

// Workflow UI State (not in OpenAPI - frontend visualization only)
export type WorkflowStep = 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13 | 14 | 15 | 16;

export interface WorkflowState {
  currentStep: WorkflowStep;
  totalSteps: 16;
  completionPercentage: number; // 0-100
  steps: WorkflowStepDetail[];
  blockers: WorkflowBlocker[];
}

export interface WorkflowStepDetail {
  stepNumber: WorkflowStep;
  name: string;
  status: 'pending' | 'in_progress' | 'completed' | 'blocked';
  completedAt?: string;
  completedBy?: string; // username
  isRequired: boolean;
}

export interface WorkflowBlocker {
  stepNumber: WorkflowStep;
  severity: 'warning' | 'critical';
  message: string;
  blockedSince: string;
}

// Critical Tasks (Dashboard UI - not in OpenAPI)
export type TaskPriority = 'warning' | 'critical';
export type TaskType =
  | 'overdue_materials'
  | 'venue_confirmation'
  | 'abstracts_moderation'
  | 'speaker_assignment'
  | 'deadline_approaching';

export interface CriticalTask {
  id: string;
  eventCode: string;
  type: TaskType;
  priority: TaskPriority;
  title: string;
  description: string;
  dueDate: string;
  assignedTo?: string; // username
  actions: TaskAction[];
  createdAt: string;
}

export interface TaskAction {
  id: string;
  label: string;
  type: 'contact' | 'extend_deadline' | 'confirm' | 'assign';
  requiresConfirmation: boolean;
}

// Team Activity Feed (Dashboard UI - not in OpenAPI)
export type ActivityType =
  | 'event_created'
  | 'event_updated'
  | 'speaker_assigned'
  | 'speaker_invited'
  | 'materials_uploaded'
  | 'workflow_advanced'
  | 'task_completed'
  | 'notification_sent'
  | 'event_published';

export interface TeamActivity {
  id: string;
  eventCode: string;
  type: ActivityType;
  actorUsername: string; // username or 'System'
  actorName: string;
  action: string;
  targetDescription: string;
  timestamp: string;
  metadata?: Record<string, unknown>;
}

// Form State (UI-only)
export interface EventFormData {
  title: string;
  description: string;
  eventDate: string;
  eventNumber: number;
  registrationDeadline: string;
  venueName: string;
  venueAddress: string;
  venueCapacity: number;
}

// Filter State (UI-only)
export interface EventFilters {
  workflowState?: string[];
  year?: number;
  search?: string;
}

export interface EventSortOption {
  field: 'date' | 'status' | 'workflowProgress' | 'title';
  direction: 'asc' | 'desc';
}

export interface PaginationParams {
  page: number;
  limit: number;
}

// Auto-Save State (UI-only)
export interface AutoSaveState {
  isSaving: boolean;
  lastSavedAt?: Date;
  error?: string;
  hasUnsavedChanges: boolean;
}

export interface ConcurrentEditConflict {
  currentVersion: number;
  serverVersion: number;
  conflictingFields: string[];
  message: string;
}

// Deletion Impact (UI dialog - not in OpenAPI)
export interface DeletionImpact {
  registrationCount: number;
  speakerCount: number;
  materialsCount: number;
  canDelete: boolean;
  reason?: string; // If canDelete is false, explain why
}

// Publishing Config (UI state - not fully in OpenAPI)
export type PublishingStrategy = 'progressive' | 'immediate';

export interface PublishingConfig {
  strategy: PublishingStrategy;
  currentPhase?: string;
  timeline: PublishingCheckpoint[];
  qualityChecks: QualityCheck[];
}

export interface PublishingCheckpoint {
  phase: string;
  targetDate: string;
  status: 'pending' | 'completed';
  completedAt?: string;
}

export interface QualityCheck {
  name: string;
  status: 'pending' | 'passed' | 'failed';
  message?: string;
}

// Notification Rules (UI state - not fully in OpenAPI)
export interface NotificationRules {
  activeAutomations: number;
  rules: NotificationRule[];
}

export interface NotificationRule {
  id: string;
  name: string;
  trigger: string;
  recipientRoles: string[];
  isActive: boolean;
}

// Team Assignments (UI state - not in OpenAPI)
export interface TeamAssignments {
  leadOrganizer: string; // username
  coOrganizers: string[]; // usernames
  moderator?: string; // username
  contentReviewer?: string; // username
}

// Topic Management (UI state - not in OpenAPI)
export interface Topic {
  id: string;
  title: string;
  description: string;
  lastUsedEvent?: string; // eventCode
  lastUsedDate?: string;
  partnerVotes: number;
  isBacklog: boolean;
}

// VenueUI (UI state - extended venue info not in OpenAPI yet)
export interface VenueUI {
  venueCode: string;
  venueName: string;
  venueAddress: string;
  venueCapacity: number;
  parking?: boolean;
  wheelchairAccess?: boolean;
  amenities?: string[];
}

// Booking Status (UI state - not in OpenAPI yet)
export interface VenueBooking {
  status: 'confirmed' | 'pending' | 'not_booked';
  confirmationNumber?: string;
  contact?: {
    name: string;
    email: string;
    phone: string;
  };
}

// Catering Config (UI state - not in OpenAPI yet)
export interface CateringConfig {
  provider?: string;
  menuConfigured?: boolean;
  dietaryRequirements?: {
    vegetarian?: number;
    vegan?: number;
    glutenFree?: number;
    other?: string;
  };
}

// API Response Wrappers (UI convenience types)
export interface EventListResponse {
  data: Event[];
  pagination: {
    page: number;
    limit: number;
    totalItems: number;
    totalPages: number;
    hasNext: boolean;
    hasPrev: boolean;
  };
}

export interface CriticalTasksResponse {
  data: CriticalTask[];
  total: number;
}

export interface TeamActivityResponse {
  data: TeamActivity[];
  pagination: {
    page: number;
    limit: number;
    totalItems: number;
    hasNext: boolean;
  };
}

// ============================================================================
// Slot Assignment & Publishing Types (Story 5.7)
// ============================================================================

/**
 * Request to assign timing to a session during slot assignment
 */
export interface SessionTimingRequest {
  startTime: string; // ISO 8601 date-time
  endTime: string; // ISO 8601 date-time
  room?: string;
  sessionType?:
    | 'keynote'
    | 'presentation'
    | 'workshop'
    | 'panel_discussion'
    | 'networking'
    | 'break'
    | 'lunch';
  changeReason?:
    | 'initial_assignment'
    | 'drag_drop_reassignment'
    | 'conflict_resolution'
    | 'preference_matching'
    | 'manual_adjustment';
  notes?: string;
}

/**
 * Bulk timing assignment request
 */
export interface BulkTimingRequest {
  assignments: Array<{
    sessionSlug: string;
    startTime: string;
    endTime: string;
    room?: string;
    sessionType?: string;
  }>;
  changeReason?: 'preference_matching' | 'manual_adjustment';
}

/**
 * Bulk timing assignment response
 */
export interface BulkTimingResponse {
  assignedCount: number;
  sessions: Session[];
}

/**
 * Timing conflict error response
 */
export interface TimingConflictError {
  error: string;
  message: string;
  conflicts: Array<{
    type: 'room_overlap' | 'speaker_double_booked' | 'speaker_unavailable';
    conflictingSessionSlug?: string | null;
    conflictingTimeRange: {
      start: string;
      end: string;
    };
    details: string;
  }>;
}

/**
 * Conflict analysis response
 */
export interface ConflictAnalysisResponse {
  hasConflicts: boolean;
  conflictCount: number;
  conflicts: Array<{
    sessionSlug: string;
    conflictType: 'room_overlap' | 'speaker_double_booked' | 'speaker_unavailable';
    severity: 'error' | 'warning';
    affectedSessions: string[];
    timeRange: {
      start: string;
      end: string;
    };
    resolution: string;
  }>;
}

// ============================================================================
// Progressive Publishing Types (Story 5.7 - Task 5b)
// ============================================================================

/**
 * Publishing phase type
 */
export type PublishingPhase = 'topic' | 'speakers' | 'agenda';

/**
 * Publishing mode
 */
export type PublishingMode = 'draft' | 'progressive' | 'complete';

/**
 * CDN invalidation status
 */
export type CDNInvalidationStatus = 'pending' | 'in_progress' | 'completed' | 'failed';

/**
 * Request options for publishing a phase
 */
export interface PublishRequest {
  mode?: PublishingMode;
  approvalOverride?: boolean;
  notifySubscribers?: boolean;
}

/**
 * Publishing version entity (for rollback capability)
 */
export interface PublishingVersion {
  id: string;
  eventCode: string;
  versionNumber: number;
  publishedPhase: string; // UPPERCASE in API
  publishedAt: string;
  publishedBy: string;
  cdnInvalidationId?: string;
  cdnInvalidationStatus?: string; // UPPERCASE in API
  contentSnapshot: Record<string, unknown>;
  isCurrent: boolean;
  rolledBackAt?: string | null;
  rolledBackBy?: string | null;
}

/**
 * Response from publishing a phase
 */
export type PublishPhaseResponse = PublishingVersion;

/**
 * Response from unpublishing a phase
 */
export interface UnpublishPhaseResponse {
  eventCode: string;
  unpublishedPhase: string; // UPPERCASE in API
  newCurrentPhase: string | null; // UPPERCASE in API
  unpublishedAt: string;
  unpublishedBy: string;
}

/**
 * Publish preview response
 */
export interface PublishPreviewResponse {
  eventCode: string;
  phase: string; // UPPERCASE in API
  mode: string; // UPPERCASE in API
  previewUrl: string;
  content: {
    topic?: {
      title: string;
      date: string;
      venue: string;
    };
    speakers?: Array<{
      displayName: string;
      companyName: string;
    }>;
    agenda?: Array<{
      sessionSlug: string;
      title: string;
      startTime: string;
      endTime: string;
      room: string;
    }>;
  };
  validation: {
    isValid: boolean;
    errors: Array<{
      field: string;
      message: string;
      requirement: string;
    }>;
  };
}

/**
 * Version history response (array of versions)
 */
export type VersionHistoryResponse = PublishingVersion[];

/**
 * Rollback request
 */
export interface RollbackRequest {
  reason: string; // 10-500 chars required
}

/**
 * Response from rolling back to previous version
 */
export type RollbackResponse = PublishingVersion;

/**
 * Change log entry
 */
export interface ChangeLogEntry {
  timestamp: string;
  changedBy: string;
  changeType: string; // UPPERCASE in API
  description: string;
  affectedPhase: string; // UPPERCASE in API
}

/**
 * Change log response
 */
export interface ChangeLogResponse {
  eventCode: string;
  changes: ChangeLogEntry[];
}

/**
 * Auto-publish schedule request
 */
export interface AutoPublishScheduleRequest {
  scheduledDate: string; // ISO 8601 date-time
  notifySubscribers?: boolean;
}

/**
 * Auto-publish schedule response
 */
export interface AutoPublishScheduleResponse {
  eventCode: string;
  phase: string; // UPPERCASE in API
  scheduledDate: string;
  isEnabled: boolean;
  ruleArn?: string; // AWS EventBridge rule ARN
}

/**
 * Cancel auto-publish response
 */
export interface CancelAutoPublishResponse {
  eventCode: string;
  phase: string; // UPPERCASE in API
  cancelledAt: string;
  cancelledBy: string;
}

/**
 * Publishing validation error (422 response)
 */
export interface PublishValidationError {
  error: string;
  message: string;
  phase: string; // UPPERCASE in API
  validationErrors: Array<{
    field: string;
    message: string;
    requirement: string;
  }>;
}
