/**
 * usePublishing Hook (Story 5.7 - Task 5b GREEN Phase)
 *
 * State management hook for progressive publishing functionality
 * Features:
 * - Publish/unpublish phases (topic → speakers → agenda)
 * - Auto-publish schedule, part of the publishing resource (Story 4.9b)
 * - Real-time updates via React Query
 */

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { publishingService } from '@/services/publishingService/publishingService';
import type {
  PublishingPhase,
  PublishingStatusResponse,
  PatchPublishingRequest,
  PublishValidationDetail,
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
  validationErrors: PublishValidationDetail[];

  // Auto-publish schedule (one per event: the phase 2 and phase 3 triggers), partial update
  updateSchedule: (request: PatchPublishingRequest) => Promise<PublishingStatusResponse>;
  isScheduling: boolean;
  scheduleError: Error | null;
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
    queryFn: () => publishingService.getPublishing(eventCode),
    staleTime: 10000, // 10 seconds - validation can change frequently
  });

  // Publish phase mutation
  const publishPhaseMutation = useMutation({
    mutationFn: (phase: PublishingPhase) => publishingService.publishPhase(eventCode, phase),
    onSuccess: (_response, phase) => {
      queryClient.invalidateQueries({ queryKey: statusKey });
      // Publishing the agenda can move the event into AGENDA_PUBLISHED (Story 4.9b)
      if (phase === 'agenda') {
        queryClient.invalidateQueries({ queryKey: ['event', eventCode] });
      }
    },
  });

  // Unpublish phase mutation
  const unpublishPhaseMutation = useMutation({
    mutationFn: (phase: PublishingPhase) => publishingService.unpublishPhase(eventCode, phase),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: statusKey });
    },
  });

  // Auto-publish schedule: PATCH answers the whole resource, which replaces the cached status
  const scheduleMutation = useMutation({
    mutationFn: (request: PatchPublishingRequest) =>
      publishingService.patchPublishing(eventCode, request),
    onSuccess: (publishing) => {
      queryClient.setQueryData(statusKey, publishing);
    },
  });

  // Extract validation errors from publish error (422 response)
  const validationErrors: PublishValidationDetail[] =
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

    // Auto-publish schedule
    updateSchedule: (request) => scheduleMutation.mutateAsync(request),
    isScheduling: scheduleMutation.isPending,
    scheduleError: scheduleMutation.error,
  };
};
