/**
 * Speaker Pool UI Types (Story 5.2)
 *
 * Types for Speaker Brainstorming and Pool Management.
 */

import type { components } from '@/types/generated/speakers-api.types';

// Use OpenAPI generated workflow state type
export type SpeakerWorkflowState = components['schemas']['SpeakerWorkflowState'];

// ============================================================================
// Speaker Pool Types
// ============================================================================

export interface SpeakerPoolEntry {
  id: string;
  eventId: string;
  username?: string; // Username for linked/authenticated speakers (Story 6.3)
  speakerName: string;
  company?: string;
  expertise?: string;
  assignedOrganizerId?: string | null;
  status: SpeakerWorkflowState;
  sessionId?: string; // Session UUID - set when speaker submits content (Story 5.5)
  notes?: string;
  email?: string; // Email address for contacting the speaker
  phone?: string; // Phone number for contacting the speaker
  proposedPresentationTitle?: string; // Title proposed by speaker when accepting invitation
  commentsForOrganizer?: string; // Additional comments from speaker to organizer
  createdAt: string;
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
  email?: string;
  phone?: string;
}

export interface UpdateSpeakerPoolRequest {
  speakerName: string;
  company?: string;
  expertise?: string;
  assignedOrganizerId?: string;
  notes?: string;
  email?: string;
  phone?: string;
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
