/**
 * Topics React Query Hooks (Story 5.2 - Task 5b)
 *
 * Custom hooks for topic data fetching and mutations
 * Features:
 * - React Query for server state caching
 * - Automatic cache invalidation on updates
 * - 5 minute stale time for topic list (frequent updates)
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { topicService } from '@/services/topicService';
import type {
  CreateTopicRequest,
  OverrideStalenesRequest,
  TopicFilters,
  SelectTopicForEventRequest,
} from '@/types/topic.types';

/**
 * Query key factory for topics
 * Centralizes query key management for cache invalidation
 */
export const topicKeys = {
  all: ['topics'] as const,
  lists: () => [...topicKeys.all, 'list'] as const,
  list: (filters?: TopicFilters) => [...topicKeys.lists(), filters] as const,
  details: () => [...topicKeys.all, 'detail'] as const,
  detail: (id: string) => [...topicKeys.details(), id] as const,
  similar: (id: string) => [...topicKeys.all, 'similar', id] as const,
  usageHistory: (id: string) => [...topicKeys.all, 'usage-history', id] as const,
};

/**
 * Hook to fetch topics with filtering and pagination (AC1)
 *
 * @param filters Optional filters for category, status, pagination, etc.
 * @returns Query result with paginated topic list
 * @example
 * const { data, isLoading } = useTopics({ category: 'Cloud Native', page: 1, limit: 20 });
 */
export function useTopics(filters?: TopicFilters) {
  return useQuery({
    queryKey: topicKeys.list(filters),
    queryFn: () => topicService.getTopics(filters),
    staleTime: 1000 * 60 * 5, // 5 minutes - topics change moderately
  });
}

/**
 * Hook to fetch specific topic by ID
 *
 * @param id Topic UUID
 * @param include Optional comma-separated resources to include
 * @returns Query result with topic details
 * @example
 * const { data: topic } = useTopic('uuid', 'similarity,history');
 */
export function useTopic(id: string, include?: string) {
  return useQuery({
    queryKey: topicKeys.detail(id),
    queryFn: () => topicService.getTopicById(id, include),
    enabled: !!id,
    staleTime: 1000 * 60 * 5, // 5 minutes
  });
}

/**
 * Hook to get similar topics for duplicate detection (AC5)
 *
 * @param id Topic UUID
 * @returns Query result with list of similar topics
 * @example
 * const { data: similarTopics } = useSimilarTopics('uuid');
 */
export function useSimilarTopics(id: string) {
  return useQuery({
    queryKey: topicKeys.similar(id),
    queryFn: () => topicService.getSimilarTopics(id),
    enabled: !!id,
    staleTime: 1000 * 60 * 60, // 1 hour - similarity scores change rarely
  });
}

/**
 * Hook to fetch topic usage history for heat map visualization (AC2)
 *
 * @param id Topic UUID
 * @returns Query result with array of usage history records
 * @example
 * const { data: usageHistory } = useTopicUsageHistory('uuid');
 */
export function useTopicUsageHistory(id: string) {
  return useQuery({
    queryKey: topicKeys.usageHistory(id),
    queryFn: () => topicService.getTopicUsageHistory(id),
    enabled: !!id,
    staleTime: 1000 * 60 * 60, // 1 hour - usage history changes rarely
  });
}

/**
 * Hook to create new topic (AC8, ORGANIZER only)
 *
 * @returns Mutation object with mutate function
 * @example
 * const createMutation = useCreateTopic();
 * createMutation.mutate({ title: 'New Topic', category: 'Technical', description: '...' });
 */
export function useCreateTopic() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: CreateTopicRequest) => topicService.createTopic(request),
    onSuccess: () => {
      // Invalidate all topic list queries to refetch fresh data
      queryClient.invalidateQueries({ queryKey: topicKeys.lists() });
    },
  });
}

/**
 * Hook to override staleness score with justification (AC7, ORGANIZER only)
 *
 * @returns Mutation object with mutate function
 * @example
 * const overrideMutation = useOverrideStaleness();
 * overrideMutation.mutate({ id: 'uuid', request: { stalenessScore: 80, justification: '...' } });
 */
export function useOverrideStaleness() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: OverrideStalenesRequest }) =>
      topicService.overrideStaleness(id, request),
    onSuccess: (_data, variables) => {
      // Invalidate specific topic and all lists
      queryClient.invalidateQueries({ queryKey: topicKeys.detail(variables.id) });
      queryClient.invalidateQueries({ queryKey: topicKeys.lists() });
    },
  });
}

/**
 * Hook to select topic for event and transition workflow state (AC14, ORGANIZER only)
 *
 * @returns Mutation object with mutate function
 * @example
 * const selectMutation = useSelectTopicForEvent();
 * selectMutation.mutate({ eventCode: 'BATbern56', topicId: 'uuid', justification: '...' });
 */
export function useSelectTopicForEvent() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      eventCode,
      request,
    }: {
      eventCode: string;
      request: SelectTopicForEventRequest;
    }) => topicService.selectTopicForEvent(eventCode, request),
    onSuccess: (_data, variables) => {
      // Invalidate event detail query to refetch updated event with new topicId
      queryClient.invalidateQueries({ queryKey: ['event', variables.eventCode] });
      // Invalidate events list query to update list view
      queryClient.invalidateQueries({ queryKey: ['events'] });
      // Update topic status if applicable
      queryClient.invalidateQueries({ queryKey: topicKeys.detail(variables.request.topicId) });
    },
  });
}
