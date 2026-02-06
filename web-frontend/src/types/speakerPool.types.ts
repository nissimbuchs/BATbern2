/**
 * Speaker Pool UI Types (Story 5.2)
 *
 * Types for Speaker Brainstorming and Pool Management.
 */

import type { components } from '@/types/generated/speakers-api.types';

// Use OpenAPI generated workflow state type, extended with INVITED state from Story 6.1b
// The generated types from speakers-api.types.ts may not include all states
export type SpeakerWorkflowState =
  | components['schemas']['SpeakerWorkflowState']
  | 'INVITED'
  | 'SLOT_ASSIGNED'
  | 'WITHDREW'
  | 'OVERFLOW';

// ============================================================================
// Speaker Pool Types
// ============================================================================

export interface SpeakerPoolEntry {
  id: string;
  eventId: string;
  speakerName: string;
  company?: string;
  expertise?: string;
  email?: string; // Speaker email - required for sending invitations (Story 6.1c)
  assignedOrganizerId?: string | null;
  status: SpeakerWorkflowState;
  sessionId?: string; // Session UUID - set when speaker submits content (Story 5.5)
  notes?: string;
  createdAt: string;
  updatedAt?: string;

  // Story 6.1b: Speaker Invitation System fields
  username?: string;
  invitedAt?: string;
  responseDeadline?: string;
  contentDeadline?: string;

  // Story 6.2a: Speaker Response Portal fields
  acceptedAt?: string;
  declinedAt?: string;
  declineReason?: string;
  isTentative?: boolean;
  tentativeReason?: string;
  preferredTimeSlot?: string;
  travelRequirements?: string;
  technicalRequirements?: string;
  initialPresentationTitle?: string;
  preferenceComments?: string;

  // Story 6.3: Speaker Content Submission Portal fields
  contentStatus?: string; // PENDING, SUBMITTED, APPROVED, REVISION_NEEDED
  contentSubmittedAt?: string;
  submittedTitle?: string;
  submittedAbstract?: string;
}

// ============================================================================
// Request/Response DTOs
// ============================================================================

export interface AddSpeakerToPoolRequest {
  speakerName: string;
  company?: string;
  expertise?: string;
  assignedOrganizerId?: string;
  notes?: string;
}

export type SpeakerPoolResponse = SpeakerPoolEntry;

// ============================================================================
// UI State Types
// ============================================================================

export interface SpeakerPoolUI extends SpeakerPoolEntry {
  assignedOrganizerName?: string; // Resolved from organizerId
}

export interface SpeakerPoolFilters {
  status?: SpeakerWorkflowState;
  assignedOrganizerId?: string;
}

// ============================================================================
// Send Invitation Types (Story 6.1c)
// ============================================================================

/**
 * Request body for sending speaker invitation
 */
export interface SendInvitationRequest {
  /** Response deadline (ISO date, required, must be in the future) */
  responseDeadline: string;
  /** Content deadline (ISO date, optional, must be after responseDeadline) */
  contentDeadline?: string;
  /** Preferred language for the email (optional, defaults to German) */
  locale?: string;
  /** Email address (for speakers without email in database) */
  email?: string;
}

/**
 * Response from send invitation endpoint
 */
export interface SendInvitationResponse {
  /** Magic link token (for testing/debugging) */
  token: string;
  /** New workflow state (should be INVITED) */
  workflowState: string;
  /** Timestamp when invitation was sent */
  invitedAt: string;
  /** Email address invitation was sent to */
  email: string;
}

// ============================================================================
// Send Reminder Types (Story 6.5)
// ============================================================================

/**
 * Request body for sending speaker reminder
 */
export interface SendReminderRequest {
  /** Type of reminder: RESPONSE (for invited speakers) or CONTENT (for accepted speakers) */
  reminderType: 'RESPONSE' | 'CONTENT';
  /** Optional tier override (TIER_1, TIER_2, TIER_3) - auto-detected if omitted */
  tier?: string;
}

/**
 * Response from send reminder endpoint
 */
export interface SendReminderResponse {
  /** Success message */
  message: string;
  /** Tier used for the reminder */
  tier: string;
  /** Email address reminder was sent to */
  emailAddress: string;
}
