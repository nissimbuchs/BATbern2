/**
 * usePublishing Hook (Story 5.7 - Task 5b GREEN Phase)
 *
 * State management hook for progressive publishing functionality
 * Features:
 * - Publish/unpublish phases (topic → speakers → agenda)
 * - Auto-publish scheduling
 * - Real-time updates via React Query
 */

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { publishingService } from '@/services/publishingService/publishingService';
import type {
  PublishingPhase,
  PublishingStatusResponse,
  AutoPublishScheduleRequest,
  PublishValidationError,
} from '@/types/event.types';

export interface UsePublishingReturn {
  // Publishing status (real-time validation data)
  publishingStatus: PublishingStatusResponse | undefined;
  isLoadingStatus: boolean;

  // Publish/unpublish mutations
  publishPhase: (phase: PublishingPhase) => void;
  unpublishPhase: (phase: PublishingPhase) => void;
  isPublishing: boolean;
  isUnpublishing: boolean;
  publishError: Error | null;
  validationErrors: Array<{ field: string; message: string; requirement: string }>;

  // Auto-publish scheduling
  scheduleAutoPublish: (phase: PublishingPhase, options: AutoPublishScheduleRequest) => void;
  isScheduling: boolean;
}

/**
 * Hook for managing progressive publishing state and operations
 *
 * @param eventCode - Event code (e.g., "BATbern142")
 * @returns Publishing state and operations
 */
export const usePublishing = (eventCode: string): UsePublishingReturn => {
  const queryClient = useQueryClient();

  // Query keys
  const statusKey = ['publishing', 'status', eventCode];

  // Publishing status query (auto-fetched)
  const { data: publishingStatus, isLoading: isLoadingStatus } = useQuery({
    queryKey: statusKey,
    queryFn: () => publishingService.getPublishingStatus(eventCode),
    staleTime: 10000, // 10 seconds - validation can change frequently
  });

  // Publish phase mutation
  const publishPhaseMutation = useMutation({
    mutationFn: (phase: PublishingPhase) => publishingService.publishPhase(eventCode, phase),
    onSuccess: () => {
      // Invalidate status, version history and change log to refetch
      queryClient.invalidateQueries({ queryKey: statusKey });
    },
  });

  // Unpublish phase mutation
  const unpublishPhaseMutation = useMutation({
    mutationFn: (phase: PublishingPhase) => publishingService.unpublishPhase(eventCode, phase),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: statusKey });
    },
  });

  // Schedule auto-publish mutation
  const scheduleAutoPublishMutation = useMutation({
    mutationFn: ({
      phase,
      options,
    }: {
      phase: PublishingPhase;
      options: AutoPublishScheduleRequest;
    }) => publishingService.scheduleAutoPublish(eventCode, phase, options),
  });

  // Extract validation errors from publish error (422 response)
  const validationErrors: Array<{ field: string; message: string; requirement: string }> =
    publishPhaseMutation.error &&
    typeof publishPhaseMutation.error === 'object' &&
    'response' in publishPhaseMutation.error &&
    publishPhaseMutation.error.response &&
    typeof publishPhaseMutation.error.response === 'object' &&
    'status' in publishPhaseMutation.error.response &&
    publishPhaseMutation.error.response.status === 422 &&
    'data' in publishPhaseMutation.error.response
      ? (publishPhaseMutation.error.response.data as PublishValidationError)?.validationErrors || []
      : [];

  return {
    // Publishing status
    publishingStatus,
    isLoadingStatus,

    // Publish/unpublish
    publishPhase: (phase) => publishPhaseMutation.mutate(phase),
    unpublishPhase: (phase) => unpublishPhaseMutation.mutate(phase),
    isPublishing: publishPhaseMutation.isPending,
    isUnpublishing: unpublishPhaseMutation.isPending,
    publishError: publishPhaseMutation.error,
    validationErrors,

    // Auto-publish scheduling
    scheduleAutoPublish: (phase, options) => scheduleAutoPublishMutation.mutate({ phase, options }),
    isScheduling: scheduleAutoPublishMutation.isPending,
  };
};
