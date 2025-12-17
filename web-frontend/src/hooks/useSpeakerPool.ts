/**
 * Speaker Pool React Query Hooks (Story 5.2 - Task 7b)
 *
 * Custom hooks for speaker pool data fetching and mutations
 * Features:
 * - React Query for server state caching
 * - Automatic cache invalidation on updates
 * - Per-event caching strategy
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { speakerPoolService } from '@/services/speakerPoolService';
import type { AddSpeakerToPoolRequest } from '@/types/speakerPool.types';

/**
 * Query key factory for speaker pool
 * Centralizes query key management for cache invalidation
 */
export const speakerPoolKeys = {
  all: ['speakerPool'] as const,
  lists: () => [...speakerPoolKeys.all, 'list'] as const,
  list: (eventCode: string) => [...speakerPoolKeys.lists(), eventCode] as const,
};

/**
 * Hook to fetch speaker pool for an event
 *
 * @param eventCode Event code (e.g., "BATbern56")
 * @returns Query result with list of speaker pool entries
 * @example
 * const { data: speakerPool, isLoading } = useSpeakerPool('BATbern56');
 */
export function useSpeakerPool(eventCode: string) {
  return useQuery({
    queryKey: speakerPoolKeys.list(eventCode),
    queryFn: () => speakerPoolService.getSpeakerPool(eventCode),
    enabled: !!eventCode,
    staleTime: 1000 * 60 * 2, // 2 minutes - pool changes frequently during brainstorming
  });
}

/**
 * Hook to add speaker to event pool (AC9-12, ORGANIZER only)
 *
 * @returns Mutation object with mutate function
 * @example
 * const addMutation = useAddSpeakerToPool();
 * addMutation.mutate({
 *   eventCode: 'BATbern56',
 *   request: { speakerName: 'John Doe', company: 'ACME', expertise: 'Cloud', ... }
 * });
 */
export function useAddSpeakerToPool() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ eventCode, request }: { eventCode: string; request: AddSpeakerToPoolRequest }) =>
      speakerPoolService.addSpeakerToPool(eventCode, request),
    onSuccess: (_data, variables) => {
      // Invalidate speaker pool list for this event to refetch
      queryClient.invalidateQueries({
        queryKey: speakerPoolKeys.list(variables.eventCode),
      });
    },
  });
}

/**
 * Hook to delete speaker from event pool (ORGANIZER only)
 *
 * @returns Mutation object with mutate function
 * @example
 * const deleteMutation = useDeleteSpeakerFromPool();
 * deleteMutation.mutate({ eventCode: 'BATbern56', speakerId: 'uuid-here' });
 */
export function useDeleteSpeakerFromPool() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ eventCode, speakerId }: { eventCode: string; speakerId: string }) =>
      speakerPoolService.deleteSpeakerFromPool(eventCode, speakerId),
    onSuccess: (_data, variables) => {
      // Invalidate speaker pool list for this event to refetch
      queryClient.invalidateQueries({
        queryKey: speakerPoolKeys.list(variables.eventCode),
      });
    },
  });
}
