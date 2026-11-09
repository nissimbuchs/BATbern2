/**
 * Partner Topics API Client
 * Story 8.2: Topic Suggestions & Voting — AC1–6
 *
 * All endpoints served by partner-coordination-service via API Gateway.
 * CRITICAL: Path is relative to baseURL (which already includes /api/v1).
 */

import apiClient from '@/services/api/apiClient';
import type { components } from '@/types/generated/partner-api.types';

// ─── Types ───────────────────────────────────────────────────────────────────

/** `suggestedByCompany` is null for COMMUNITY (attendee) suggestions, Story 7.1. */
export type TopicDTO = components['schemas']['TopicDTO'];
/** `companyName` is organizer-only: submit on behalf of a specific partner company. */
export type TopicSuggestionRequest = components['schemas']['TopicSuggestionRequest'];
export type TopicStatusUpdateRequest = components['schemas']['TopicStatusUpdateRequest'];

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

/**
 * Submit a topic suggestion as a logged-in ATTENDEE ("Topics From the Floor", Story 7.1).
 * Flows into the same topic pool tagged source=COMMUNITY. Submit-only — there is no
 * attendee read endpoint.
 */
export const suggestTopicAsAttendee = async (req: TopicSuggestionRequest): Promise<TopicDTO> => {
  const response = await apiClient.post<TopicDTO>('/attendees/topics', req);
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

/** Update a topic's title/description — own company's topics only (PARTNER). */
export const updateTopic = async (
  topicId: string,
  req: TopicSuggestionRequest
): Promise<TopicDTO> => {
  const response = await apiClient.patch<TopicDTO>(`/partners/topics/${topicId}`, req);
  return response.data;
};

/** Delete a topic — own company's topics only (PARTNER). */
export const deleteTopic = async (topicId: string): Promise<void> => {
  await apiClient.delete(`/partners/topics/${topicId}`);
};

/** Update topic status (ORGANIZER only). */
export const updateTopicStatus = async (
  topicId: string,
  req: TopicStatusUpdateRequest
): Promise<TopicDTO> => {
  const response = await apiClient.patch<TopicDTO>(`/partners/topics/${topicId}/status`, req);
  return response.data;
};
