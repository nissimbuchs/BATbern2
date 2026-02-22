/**
 * Partner Topics API Client
 * Story 8.2: Topic Suggestions & Voting — AC1–6
 *
 * All endpoints served by partner-coordination-service via API Gateway.
 * CRITICAL: Path is relative to baseURL (which already includes /api/v1).
 */

import apiClient from '@/services/api/apiClient';

// ─── Types ───────────────────────────────────────────────────────────────────

export interface TopicDTO {
  id: string;
  title: string;
  description?: string | null;
  suggestedByCompany: string;
  voteCount: number;
  currentPartnerHasVoted: boolean;
  status: 'PROPOSED' | 'SELECTED' | 'DECLINED';
  plannedEvent?: string | null;
  createdAt: string;
}

export interface TopicSuggestionRequest {
  title: string;
  description?: string | null;
}

export interface TopicStatusUpdateRequest {
  status: 'SELECTED' | 'DECLINED';
  plannedEvent?: string | null;
}

// ─── API functions ────────────────────────────────────────────────────────────

/** List all topic suggestions sorted by vote count descending (PARTNER + ORGANIZER). */
export const getTopics = async (): Promise<TopicDTO[]> => {
  const response = await apiClient.get<TopicDTO[]>('/partners/topics');
  return response.data;
};

/** Submit a new topic suggestion (PARTNER only). */
export const suggestTopic = async (req: TopicSuggestionRequest): Promise<TopicDTO> => {
  const response = await apiClient.post<TopicDTO>('/partners/topics', req);
  return response.data;
};

/** Cast a vote on a topic — toggle on (PARTNER only). Idempotent. */
export const castVote = async (topicId: string): Promise<void> => {
  await apiClient.post(`/partners/topics/${topicId}/vote`);
};

/** Remove a vote from a topic — toggle off (PARTNER only). Idempotent. */
export const removeVote = async (topicId: string): Promise<void> => {
  await apiClient.delete(`/partners/topics/${topicId}/vote`);
};

/** Update topic status (ORGANIZER only). */
export const updateTopicStatus = async (
  topicId: string,
  req: TopicStatusUpdateRequest
): Promise<TopicDTO> => {
  const response = await apiClient.patch<TopicDTO>(`/partners/topics/${topicId}/status`, req);
  return response.data;
};
