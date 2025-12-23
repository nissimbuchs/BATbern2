/**
 * Topic Management UI Types (Story 5.2)
 *
 * UI-specific types for Topic Selection and Backlog Management.
 * Core API types are imported from generated OpenAPI types.
 *
 * IMPORTANT: For backend API types, import directly from './generated/topics-api.types'
 * This file contains ONLY frontend-specific types and extensions.
 */

import type { components } from './generated/topics-api.types';

// ============================================================================
// Re-export Generated API Types
// ============================================================================

export type Topic = components['schemas']['Topic'];
export type TopicColorZone = components['schemas']['TopicColorZone'];
export type TopicStatus = components['schemas']['TopicStatus'];
export type SimilarityScore = components['schemas']['SimilarityScore'];
export type TopicUsageHistory = components['schemas']['TopicUsageHistory'];
export type TopicListResponse = components['schemas']['TopicListResponse'];
export type PaginationMetadata = components['schemas']['PaginationMetadata'];
export type CreateTopicRequest = components['schemas']['CreateTopicRequest'];
export type OverrideStalenessRequest = components['schemas']['OverrideStalenessRequest'];
export type SelectTopicForEventRequest = components['schemas']['SelectTopicForEventRequest'];
export type TopicSelectionResponse = components['schemas']['TopicSelectionResponse'];

// Backward compatibility alias (fix typo in old code)
export type OverrideStalenesRequest = OverrideStalenessRequest;

// Re-export components for direct access
export type { components } from './generated/topics-api.types';

// ============================================================================
// UI State Types (not in API)
// ============================================================================

export interface TopicFilters {
  category?: string;
  status?: TopicStatus;
  lastUsedDateRange?: { start: Date; end: Date };
  partnerInterest?: 'high' | 'medium' | 'low';
  sort?: string; // e.g., "-stalenessScore" for descending
  page?: number;
  limit?: number;
  include?: string; // e.g., "similarity,history"
}

export interface TopicDetailsUI extends Topic {
  // UI-specific fields for enhanced display
  partnerVotes?: PartnerVote[];
  metrics?: TopicMetrics;
  insights?: TopicInsights;
}

export interface PartnerVote {
  partnerId: string;
  partnerName: string;
  interest: 'high' | 'medium' | 'low';
  votedAt: string;
  notes?: string;
}

export interface TopicMetrics {
  averageAttendance: number;
  averageEngagement: number;
  totalUsageCount: number;
  lastEventDate?: string;
}

export interface TopicInsights {
  recommendedForReuse: boolean;
  reuseReason?: string;
  similarTopicsCount: number;
  trendDirection: 'rising' | 'stable' | 'declining';
}
