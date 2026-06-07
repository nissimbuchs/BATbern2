/**
 * usePublishing Hook (Story 5.7 - Task 5b GREEN Phase)
 *
 * State management hook for progressive publishing functionality
 * Features:
 * - Publish/unpublish phases (topic → speakers → agenda)
 * - Publishing preview
 * - Auto-publish scheduling
 * - Real-time updates via React Query
 */

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { publishingService } from '@/services/publishingService/publishingService';
import type {
  PublishingPhase,
  PublishPreviewResponse,
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

  // Preview
  preview: PublishPreviewResponse | null;
  fetchPreview: (phase: PublishingPhase) => void;
  isLoadingPreview: boolean;
  previewError: Error | null;

  // Auto-publish scheduling
  scheduleAutoPublish: (phase: PublishingPhase, options: AutoPublishScheduleRequest) => void;
  cancelAutoPublish: (phase: PublishingPhase) => void;
  isScheduling: boolean;
  isCancelling: boolean;
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
  const previewKey = ['publishing', 'preview', eventCode];

  // Publishing status query (auto-fetched)
  const { data: publishingStatus, isLoading: isLoadingStatus } = useQuery({
    queryKey: statusKey,
    queryFn: () => publishingService.getPublishingStatus(eventCode),
    staleTime: 10000, // 10 seconds - validation can change frequently
  });

  // Preview query (manually triggered via fetchPreview)
  const { data: preview } = useQuery({
    queryKey: previewKey,
    queryFn: () => null as PublishPreviewResponse | null, // Placeholder, actual call via mutation
    enabled: false, // Don't auto-fetch, controlled by fetchPreview
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

  // Fetch preview mutation (manual trigger)
  const fetchPreviewMutation = useMutation({
    mutationFn: (phase: PublishingPhase) => publishingService.getPublishPreview(eventCode, phase),
    onSuccess: (data) => {
      // Update preview query data manually
      queryClient.setQueryData(previewKey, data);
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

  // Cancel auto-publish mutation
  const cancelAutoPublishMutation = useMutation({
    mutationFn: (phase: PublishingPhase) => publishingService.cancelAutoPublish(eventCode, phase),
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

    // Preview
    preview: preview || null,
    fetchPreview: (phase) => fetchPreviewMutation.mutate(phase),
    isLoadingPreview: fetchPreviewMutation.isPending,
    previewError: fetchPreviewMutation.error,

    // Auto-publish scheduling
    scheduleAutoPublish: (phase, options) => scheduleAutoPublishMutation.mutate({ phase, options }),
    cancelAutoPublish: (phase) => cancelAutoPublishMutation.mutate(phase),
    isScheduling: scheduleAutoPublishMutation.isPending,
    isCancelling: cancelAutoPublishMutation.isPending,
  };
};
