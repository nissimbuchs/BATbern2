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

export type AddSpeakerToPoolRequest = components['schemas']['AddSpeakerToPoolRequest'];
export type PatchSpeakerPoolRequest = components['schemas']['PatchSpeakerPoolRequest'];

// Story 11.D.1: POST /speakers/{speakerId}/promote — drives CONTACTED → READY transition
// and provisions the User + SPEAKER role server-side.
// Story 11.E.4 AC4: firstName + lastName tightened from optional to REQUIRED (PM decision
// 2026-05-18). They populate the Cognito user's given_name / family_name attributes; the
// fallback path with literal placeholders ("Speaker" / "Unknown") on the backend is removed.
export type PromoteSpeakerRequest = components['schemas']['PromoteSpeakerRequest'];

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
 * Request body for sending speaker invitation: `responseDeadline` required and in the future,
 * `contentDeadline` after it, `locale` defaults to German, `email` for speakers without one.
 */
export type SendInvitationRequest = components['schemas']['SendInvitationRequest'];

/**
 * Response from send invitation endpoint
 */
export type SendInvitationResponse = components['schemas']['SendInvitationResponse'];

// ============================================================================
// Send Reminder Types (Story 6.5)
// ============================================================================

/**
 * Request body for sending speaker reminder: `reminderType` RESPONSE (invited speakers) or
 * CONTENT (accepted speakers); `tier` (TIER_1..TIER_3) is auto-detected if omitted.
 */
export type SendReminderRequest = components['schemas']['SendReminderRequest'];

/**
 * Response from send reminder endpoint
 */
export type SendReminderResponse = components['schemas']['SendReminderResponse'];
