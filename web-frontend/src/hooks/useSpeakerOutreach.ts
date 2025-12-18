/**
 * Speaker Outreach React Query Hooks (Story 5.3 - Task 4b)
 *
 * Custom hooks for speaker outreach data fetching and mutations
 * Features:
 * - React Query for server state caching
 * - Automatic cache invalidation on updates
 * - Per-speaker outreach history caching
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { speakerOutreachService } from '@/services/speakerOutreachService';
import { speakerPoolKeys } from './useSpeakerPool';
import type { RecordOutreachRequest } from '@/types/speakerOutreach.types';

/**
 * Query key factory for speaker outreach
 * Centralizes query key management for cache invalidation
 */
export const speakerOutreachKeys = {
  all: ['speakerOutreach'] as const,
  histories: () => [...speakerOutreachKeys.all, 'history'] as const,
  history: (eventCode: string, speakerId: string) =>
    [...speakerOutreachKeys.histories(), eventCode, speakerId] as const,
};

/**
 * Hook to fetch outreach history for a speaker (AC4)
 *
 * @param eventCode Event code (e.g., "BATbern56")
 * @param speakerId Speaker UUID
 * @returns Query result with list of outreach attempts (most recent first)
 * @example
 * const { data: history, isLoading } = useSpeakerOutreachHistory('BATbern56', 'speaker-123');
 */
export function useSpeakerOutreachHistory(eventCode: string, speakerId: string) {
  return useQuery({
    queryKey: speakerOutreachKeys.history(eventCode, speakerId),
    queryFn: () => speakerOutreachService.getOutreachHistory(eventCode, speakerId),
    enabled: !!eventCode && !!speakerId,
    staleTime: 1000 * 60 * 2, // 2 minutes - outreach data updated frequently during campaigns
  });
}

/**
 * Hook to record speaker outreach attempt (AC2-3, ORGANIZER only)
 *
 * @returns Mutation object with mutate function
 * @example
 * const recordMutation = useRecordOutreach();
 * recordMutation.mutate({
 *   eventCode: 'BATbern56',
 *   speakerId: 'speaker-123',
 *   request: { contactMethod: 'email', contactDate: '2025-12-14T10:00:00Z', notes: '...' }
 * });
 */
export function useRecordOutreach() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      eventCode,
      speakerId,
      request,
    }: {
      eventCode: string;
      speakerId: string;
      request: RecordOutreachRequest;
    }) => speakerOutreachService.recordOutreach(eventCode, speakerId, request),
    onSuccess: (_data, variables) => {
      // Invalidate outreach history for this specific speaker to refetch
      queryClient.invalidateQueries({
        queryKey: speakerOutreachKeys.history(variables.eventCode, variables.speakerId),
      });

      // Invalidate speaker pool to update status in the dashboard
      queryClient.invalidateQueries({
        queryKey: speakerPoolKeys.list(variables.eventCode),
      });
    },
  });
}

/**
 * Hook to bulk record outreach for multiple speakers (AC6, ORGANIZER only)
 *
 * @returns Mutation object with mutate function
 * @example
 * const bulkMutation = useBulkRecordOutreach();
 * bulkMutation.mutate({
 *   eventCode: 'BATbern56',
 *   speakerIds: ['speaker-1', 'speaker-2', 'speaker-3'],
 *   request: { contactMethod: 'email', contactDate: '2025-12-14T10:00:00Z', notes: '...' }
 * });
 */
export function useBulkRecordOutreach() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      eventCode,
      speakerIds,
      request,
    }: {
      eventCode: string;
      speakerIds: string[];
      request: RecordOutreachRequest;
    }) => speakerOutreachService.bulkRecordOutreach(eventCode, speakerIds, request),
    onSuccess: () => {
      // Invalidate all outreach histories since we don't know which speakers were updated
      queryClient.invalidateQueries({
        queryKey: speakerOutreachKeys.histories(),
      });
    },
  });
}
