/**
 * Speaker Pool UI Types (Story 5.2)
 *
 * Types for Speaker Brainstorming and Pool Management.
 */

// ============================================================================
// Speaker Pool Types
// ============================================================================

export interface SpeakerPoolEntry {
  id: string;
  eventId: string;
  speakerName: string;
  company?: string;
  expertise?: string;
  assignedOrganizerId?: string | null;
  status: SpeakerPoolStatus;
  notes?: string;
  createdAt: string;
}

export type SpeakerPoolStatus =
  | 'identified'
  | 'contacted'
  | 'ready'
  | 'accepted'
  | 'declined'
  | 'content_submitted'
  | 'quality_reviewed'
  | 'slot_assigned'
  | 'confirmed'
  | 'withdrew'
  | 'overflow';

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
  status?: SpeakerPoolStatus;
  assignedOrganizerId?: string;
}
