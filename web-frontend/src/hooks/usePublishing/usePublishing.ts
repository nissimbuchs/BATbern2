/**
 * usePublishing Hook (Story 5.7 - Task 5b GREEN Phase)
 *
 * State management hook for progressive publishing functionality
 * Features:
 * - Publish/unpublish phases (topic → speakers → agenda)
 * - Version history and rollback
 * - Publishing preview
 * - Auto-publish scheduling
 * - Change log tracking
 * - Real-time updates via React Query
 */

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { publishingService } from '@/services/publishingService/publishingService';
import type {
  PublishingPhase,
  PublishingMode,
  PublishRequest,
  PublishPreviewResponse,
  PublishingStatusResponse,
  VersionHistoryResponse,
  RollbackRequest,
  ChangeLogResponse,
  AutoPublishScheduleRequest,
  PublishValidationError,
} from '@/types/event.types';

export interface UsePublishingReturn {
  // Publishing status (real-time validation data)
  publishingStatus: PublishingStatusResponse | undefined;
  isLoadingStatus: boolean;

  // Publish/unpublish mutations
  publishPhase: (phase: PublishingPhase, options?: PublishRequest) => void;
  unpublishPhase: (phase: PublishingPhase) => void;
  isPublishing: boolean;
  isUnpublishing: boolean;
  publishError: Error | null;
  validationErrors: Array<{ field: string; message: string; requirement: string }>;

  // Version control
  versionHistory: VersionHistoryResponse | undefined;
  isLoadingVersions: boolean;
  rollbackVersion: (versionNumber: number, options: RollbackRequest) => void;
  isRollingBack: boolean;

  // Preview
  preview: PublishPreviewResponse | null;
  fetchPreview: (phase: PublishingPhase, mode: PublishingMode) => void;
  isLoadingPreview: boolean;
  previewError: Error | null;

  // Change log
  changeLog: ChangeLogResponse | undefined;
  isLoadingChangeLog: boolean;

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
  const versionHistoryKey = ['publishing', 'versions', eventCode];
  const changeLogKey = ['publishing', 'changeLog', eventCode];
  const previewKey = ['publishing', 'preview', eventCode];

  // Publishing status query (auto-fetched)
  const { data: publishingStatus, isLoading: isLoadingStatus } = useQuery({
    queryKey: statusKey,
    queryFn: () => publishingService.getPublishingStatus(eventCode),
    staleTime: 10000, // 10 seconds - validation can change frequently
  });

  // Version history query (auto-fetched)
  const { data: versionHistory, isLoading: isLoadingVersions } = useQuery({
    queryKey: versionHistoryKey,
    queryFn: () => publishingService.getVersionHistory(eventCode),
    staleTime: 30000, // 30 seconds
  });

  // Change log query (auto-fetched)
  const { data: changeLog, isLoading: isLoadingChangeLog } = useQuery({
    queryKey: changeLogKey,
    queryFn: () => publishingService.getChangeLog(eventCode),
    staleTime: 30000, // 30 seconds
  });

  // Preview query (manually triggered via fetchPreview)
  const { data: preview } = useQuery({
    queryKey: previewKey,
    queryFn: () => null as PublishPreviewResponse | null, // Placeholder, actual call via mutation
    enabled: false, // Don't auto-fetch, controlled by fetchPreview
  });

  // Publish phase mutation
  const publishPhaseMutation = useMutation({
    mutationFn: ({ phase, options }: { phase: PublishingPhase; options?: PublishRequest }) =>
      publishingService.publishPhase(eventCode, phase, options),
    onSuccess: () => {
      // Invalidate status, version history and change log to refetch
      queryClient.invalidateQueries({ queryKey: statusKey });
      queryClient.invalidateQueries({ queryKey: versionHistoryKey });
      queryClient.invalidateQueries({ queryKey: changeLogKey });
    },
  });

  // Unpublish phase mutation
  const unpublishPhaseMutation = useMutation({
    mutationFn: (phase: PublishingPhase) => publishingService.unpublishPhase(eventCode, phase),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: statusKey });
      queryClient.invalidateQueries({ queryKey: versionHistoryKey });
      queryClient.invalidateQueries({ queryKey: changeLogKey });
    },
  });

  // Rollback version mutation
  const rollbackVersionMutation = useMutation({
    mutationFn: ({ versionNumber, options }: { versionNumber: number; options: RollbackRequest }) =>
      publishingService.rollbackVersion(eventCode, versionNumber, options),
    onSuccess: () => {
      // Rollback changes the published phase, so invalidate status too
      queryClient.invalidateQueries({ queryKey: statusKey });
      queryClient.invalidateQueries({ queryKey: versionHistoryKey });
      queryClient.invalidateQueries({ queryKey: changeLogKey });
    },
  });

  // Fetch preview mutation (manual trigger)
  const fetchPreviewMutation = useMutation({
    mutationFn: ({ phase, mode }: { phase: PublishingPhase; mode: PublishingMode }) =>
      publishingService.getPublishPreview(eventCode, phase, mode),
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
    publishPhase: (phase, options) => publishPhaseMutation.mutate({ phase, options }),
    unpublishPhase: (phase) => unpublishPhaseMutation.mutate(phase),
    isPublishing: publishPhaseMutation.isPending,
    isUnpublishing: unpublishPhaseMutation.isPending,
    publishError: publishPhaseMutation.error,
    validationErrors,

    // Version control
    versionHistory,
    isLoadingVersions,
    rollbackVersion: (versionNumber, options) =>
      rollbackVersionMutation.mutate({ versionNumber, options }),
    isRollingBack: rollbackVersionMutation.isPending,

    // Preview
    preview: preview || null,
    fetchPreview: (phase, mode) => fetchPreviewMutation.mutate({ phase, mode }),
    isLoadingPreview: fetchPreviewMutation.isPending,
    previewError: fetchPreviewMutation.error,

    // Change log
    changeLog,
    isLoadingChangeLog,

    // Auto-publish scheduling
    scheduleAutoPublish: (phase, options) => scheduleAutoPublishMutation.mutate({ phase, options }),
    cancelAutoPublish: (phase) => cancelAutoPublishMutation.mutate(phase),
    isScheduling: scheduleAutoPublishMutation.isPending,
    isCancelling: cancelAutoPublishMutation.isPending,
  };
};
