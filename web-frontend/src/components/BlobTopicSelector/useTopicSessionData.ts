/**
 * React Query hook for topic session data
 * Story 10.4: Blob Topic Selector (Task 8)
 */

import { useQuery } from '@tanstack/react-query';
import { blobTopicService } from '@/services/blobTopicService';
import type { TopicSessionData } from './types';

export function useTopicSessionData(eventCode: string) {
  return useQuery<TopicSessionData>({
    queryKey: ['topicSessionData', eventCode],
    queryFn: () => blobTopicService.getSessionData(eventCode),
    staleTime: Infinity, // Fetch once per session
    enabled: Boolean(eventCode),
  });
}
