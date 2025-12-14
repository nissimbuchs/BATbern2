/**
 * Speaker Outreach UI Types (Story 5.3)
 *
 * Types for Speaker Outreach Tracking and Contact History.
 */

// ============================================================================
// Outreach History Types
// ============================================================================

export interface OutreachHistory {
  id: string;
  speakerPoolId: string;
  contactDate: string;
  contactMethod: ContactMethod;
  notes?: string;
  organizerUsername: string;
  createdAt: string;
}

export type ContactMethod = 'email' | 'phone' | 'in_person';

// ============================================================================
// Request/Response DTOs
// ============================================================================

export interface RecordOutreachRequest {
  contactMethod: ContactMethod;
  contactDate: string;
  notes?: string;
}

export type OutreachHistoryResponse = OutreachHistory;

// ============================================================================
// UI State Types
// ============================================================================

export interface SpeakerWithOutreach {
  id: string;
  speakerName: string;
  company?: string;
  expertise?: string;
  assignedOrganizerId?: string | null;
  status: string;
  outreachHistory: OutreachHistory[];
  lastContactDate?: string;
  daysSinceAssignment: number;
}

export interface OutreachFilters {
  assignedOrganizerId?: string;
  status?: string;
}
