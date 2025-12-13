/**
 * Topic Management UI Types (Story 5.2)
 *
 * Types for Topic Selection and Backlog Management.
 * Extends generated OpenAPI types with UI-specific fields.
 */

// ============================================================================
// Topic Types (for API responses)
// ============================================================================

export interface Topic {
  id: string;
  title: string;
  description: string;
  category: string;
  createdDate?: string;
  lastUsedDate?: string | null;
  usageCount?: number;
  stalenessScore: number; // 0-100, higher = safer to reuse
  colorZone: 'red' | 'yellow' | 'green' | 'gray'; // Color coding for freshness
  status: 'available' | 'caution' | 'unavailable'; // Topic availability status
  similarityScores: SimilarityScore[];
  active?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface SimilarityScore {
  topicId: string;
  score: number; // 0-1, cosine similarity score
}

export interface TopicUsageHistory {
  eventId: string;
  usedDate: string;
  attendance: number;
  engagementScore: number; // 0-1
}

// ============================================================================
// Request/Response DTOs
// ============================================================================

export interface TopicListResponse {
  data: Topic[];
  pagination: PaginationMetadata;
}

export interface PaginationMetadata {
  page: number;
  limit: number;
  total: number;
}

export interface CreateTopicRequest {
  title: string;
  description?: string;
  category: string;
  keywords?: string[];
  relatedTopics?: string[];
}

export interface OverrideStalenesRequest {
  stalenessScore: number;
  justification: string;
}

export interface SelectTopicForEventRequest {
  topicId: string;
  justification?: string;
}

// ============================================================================
// UI State Types (not in API)
// ============================================================================

export interface TopicFilters {
  category?: string;
  status?: 'available' | 'caution' | 'unavailable';
  lastUsedDateRange?: { start: Date; end: Date };
  partnerInterest?: 'high' | 'medium' | 'low';
  sort?: string; // e.g., "-stalenessScore" for descending
  page?: number;
  limit?: number;
  include?: string; // e.g., "similarity,history"
}

export interface TopicDetailsUI extends Topic {
  // UI-specific fields for enhanced display
  usageHistory?: TopicUsageHistory[];
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
