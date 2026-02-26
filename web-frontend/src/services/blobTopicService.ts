/**
 * Blob Topic Selector API Service
 * Story 10.4: Blob Topic Selector (Task 16)
 */

import apiClient from '@/services/api/apiClient';
import type {
  TopicSessionData,
  TopicSimilarityResponse,
} from '@/components/BlobTopicSelector/types';

export const blobTopicService = {
  getSessionData: async (eventCode: string): Promise<TopicSessionData> => {
    const response = await apiClient.get<TopicSessionData>(
      `/events/${eventCode}/topic-session-data`
    );
    return response.data;
  },

  getSimilarity: async (eventCode: string, topic: string): Promise<TopicSimilarityResponse> => {
    const response = await apiClient.post<TopicSimilarityResponse>(
      `/events/${eventCode}/topic-similarity`,
      { topic }
    );
    return response.data;
  },

  acceptTopic: async (eventCode: string, note: string): Promise<void> => {
    await apiClient.patch(`/events/${eventCode}`, {
      topicSelectionNote: note,
    });
  },
};
