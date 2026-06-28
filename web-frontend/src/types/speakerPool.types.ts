/**
 * Speaker Pool UI Types (Story 5.2)
 *
 * Types for Speaker Brainstorming and Pool Management.
 */

import type { components } from '@/types/generated/event-speakers-api.types';

// Use OpenAPI-generated workflow state type — the 8 ADR-009 §0.1 states (UPPER_CASE,
// matching the SpeakerWorkflowState enum name serialized on the wire).
// Legacy widenings ('SLOT_ASSIGNED' | 'WITHDREW' | 'OVERFLOW') dropped per Story 11.E.4 AC1
// (Phase B residue from Story 11.B.1 that 11.D.2 / 11.D.4 reviews flagged for cleanup).
// Derived from SpeakerPoolResponse.status: event-speakers-api does not define a standalone
// SpeakerWorkflowState schema, and this is its single source of truth on this surface.
export type SpeakerWorkflowState = components['schemas']['SpeakerPoolResponse']['status'];

// ============================================================================
// Speaker Pool Types
// ============================================================================

// API-consolidation Phase 7 (2026-06-28): SpeakerPoolEntry is now the OpenAPI-generated
// SpeakerPoolResponse (event-speakers-api). The former hand-written interface and the
// generated schema had drifted: 4 dead fields (isPublishable, contentStatus,
// materialCloudFrontUrl, remindersDisabled) + the borderline materialFileName were trimmed
// (FE reads materialFileName off content/speaker-portal types, never off the pool entry),
// and the status enum was corrected from the stale 11-value set to the 8 ADR-009 states.
export type SpeakerPoolEntry = components['schemas']['SpeakerPoolResponse'];

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

export interface PatchSpeakerPoolRequest {
  speakerName?: string;
  company?: string;
  expertise?: string;
  assignedOrganizerId?: string;
  notes?: string;
}

// Story 11.D.1: POST /speakers/{speakerId}/promote — drives CONTACTED → READY transition
// and provisions the User + SPEAKER role server-side.
// Story 11.E.4 AC4: firstName + lastName tightened from optional to REQUIRED (PM decision
// 2026-05-18). They populate the Cognito user's given_name / family_name attributes; the
// fallback path with literal placeholders ("Speaker" / "Unknown") on the backend is removed.
export interface PromoteSpeakerRequest {
  email: string;
  firstName: string;
  lastName: string;
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
