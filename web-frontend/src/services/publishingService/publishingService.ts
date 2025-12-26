/**
 * Publishing Service
 * Story 5.7 - Task 5b: Progressive Publishing Frontend
 *
 * API client for progressive publishing operations:
 * - Publish/unpublish phases (topic → speakers → agenda)
 * - Version control and rollback
 * - Publishing preview
 * - Auto-publish scheduling
 * - Change log tracking
 */

import apiClient from '@/services/api/apiClient';
import type {
  PublishingPhase,
  PublishingMode,
  PublishRequest,
  PublishPhaseResponse,
  UnpublishPhaseResponse,
  PublishPreviewResponse,
  VersionHistoryResponse,
  RollbackRequest,
  RollbackResponse,
  ChangeLogResponse,
  AutoPublishScheduleRequest,
  AutoPublishScheduleResponse,
  CancelAutoPublishResponse,
} from '@/types/event.types';

/**
 * Publish a phase of event content
 *
 * @param eventCode - Event code (e.g., "BATbern142")
 * @param phase - Publishing phase ('topic' | 'speakers' | 'agenda')
 * @param options - Publishing options (mode, notifySubscribers, approvalOverride)
 * @returns Publishing version with CDN invalidation status
 * @throws 422 if content validation fails
 * @throws 401 if not authenticated
 * @throws 403 if insufficient permissions
 */
async function publishPhase(
  eventCode: string,
  phase: PublishingPhase,
  options?: PublishRequest
): Promise<PublishPhaseResponse> {
  const response = await apiClient.post(
    `/api/v1/events/${eventCode}/publish/${phase}`,
    options || {}
  );
  return response.data;
}

/**
 * Unpublish a phase (revert to previous phase)
 *
 * @param eventCode - Event code
 * @param phase - Phase to unpublish
 * @returns Unpublish response with new current phase
 */
async function unpublishPhase(
  eventCode: string,
  phase: PublishingPhase
): Promise<UnpublishPhaseResponse> {
  const response = await apiClient.post(`/api/v1/events/${eventCode}/unpublish/${phase}`);
  return response.data;
}

/**
 * Get publishing preview for a phase
 *
 * @param eventCode - Event code
 * @param phase - Phase to preview
 * @param mode - Publishing mode
 * @returns Preview with content and validation status
 */
async function getPublishPreview(
  eventCode: string,
  phase: PublishingPhase,
  mode: PublishingMode
): Promise<PublishPreviewResponse> {
  const response = await apiClient.get(`/api/v1/events/${eventCode}/publish/${phase}/preview`, {
    params: { mode },
  });
  return response.data;
}

/**
 * Get version history for an event
 *
 * @param eventCode - Event code
 * @returns Array of publishing versions (newest first)
 */
async function getVersionHistory(eventCode: string): Promise<VersionHistoryResponse> {
  const response = await apiClient.get(`/api/v1/events/${eventCode}/publishing/versions`);
  return response.data;
}

/**
 * Rollback to a previous publishing version
 *
 * @param eventCode - Event code
 * @param versionNumber - Version number to rollback to
 * @param options - Rollback options (reason required)
 * @returns New version created by rollback
 * @throws 404 if version not found
 */
async function rollbackVersion(
  eventCode: string,
  versionNumber: number,
  options: RollbackRequest
): Promise<RollbackResponse> {
  const response = await apiClient.post(
    `/api/v1/events/${eventCode}/publishing/versions/${versionNumber}/rollback`,
    options
  );
  return response.data;
}

/**
 * Get change log for published content
 *
 * @param eventCode - Event code
 * @returns Change log entries
 */
async function getChangeLog(eventCode: string): Promise<ChangeLogResponse> {
  const response = await apiClient.get(`/api/v1/events/${eventCode}/publishing/changelog`);
  return response.data;
}

/**
 * Schedule auto-publish for a phase
 *
 * @param eventCode - Event code
 * @param phase - Phase to auto-publish
 * @param options - Schedule options (scheduledDate, notifySubscribers)
 * @returns Auto-publish schedule details with AWS EventBridge rule ARN
 */
async function scheduleAutoPublish(
  eventCode: string,
  phase: PublishingPhase,
  options: AutoPublishScheduleRequest
): Promise<AutoPublishScheduleResponse> {
  const response = await apiClient.post(
    `/api/v1/events/${eventCode}/publishing/schedule/${phase}`,
    options
  );
  return response.data;
}

/**
 * Cancel auto-publish schedule for a phase
 *
 * @param eventCode - Event code
 * @param phase - Phase to cancel auto-publish
 * @returns Cancellation confirmation
 */
async function cancelAutoPublish(
  eventCode: string,
  phase: PublishingPhase
): Promise<CancelAutoPublishResponse> {
  const response = await apiClient.delete(
    `/api/v1/events/${eventCode}/publishing/schedule/${phase}`
  );
  return response.data;
}

/**
 * Publishing Service
 * Exported as singleton for consistent usage across components
 */
export const publishingService = {
  publishPhase,
  unpublishPhase,
  getPublishPreview,
  getVersionHistory,
  rollbackVersion,
  getChangeLog,
  scheduleAutoPublish,
  cancelAutoPublish,
};

// Also export individual functions for easier testing/mocking
export {
  publishPhase,
  unpublishPhase,
  getPublishPreview,
  getVersionHistory,
  rollbackVersion,
  getChangeLog,
  scheduleAutoPublish,
  cancelAutoPublish,
};
