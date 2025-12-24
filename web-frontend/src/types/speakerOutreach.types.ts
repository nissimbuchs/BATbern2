/**
 * Speaker Outreach UI Types (Story 5.3)
 *
 * UI-specific types for Speaker Outreach Tracking and Contact History.
 * Core API types are imported from generated OpenAPI types.
 *
 * IMPORTANT: For backend API types, import directly from './generated/speakers-api.types'
 * This file contains ONLY frontend-specific types and extensions.
 */

import type { components } from './generated/speakers-api.types';

// ============================================================================
// Re-export Generated API Types
// ============================================================================

export type OutreachHistory = components['schemas']['OutreachHistory'];
export type ContactMethod = components['schemas']['ContactMethod'];
export type RecordOutreachRequest = components['schemas']['RecordOutreachRequest'];

// Backward compatibility alias
export type OutreachHistoryResponse = OutreachHistory;

// Re-export components for direct access
export type { components } from './generated/speakers-api.types';

// ============================================================================
// UI State Types (not in API)
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
