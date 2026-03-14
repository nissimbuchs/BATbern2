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
  PublishPhaseResponse,
  UnpublishPhaseResponse,
  PublishPreviewResponse,
  PublishingStatusResponse,
  AutoPublishScheduleRequest,
  AutoPublishScheduleResponse,
  CancelAutoPublishResponse,
} from '@/types/event.types';

/**
 * Publish a phase of event content
 *
 * @param eventCode - Event code (e.g., "BATbern142")
 * @param phase - Publishing phase ('topic' | 'speakers' | 'agenda')
 * @returns Publishing version with CDN invalidation status
 * @throws 422 if content validation fails
 * @throws 401 if not authenticated
 * @throws 403 if insufficient permissions
 */
async function publishPhase(
  eventCode: string,
  phase: PublishingPhase
): Promise<PublishPhaseResponse> {
  const response = await apiClient.post(`/events/${eventCode}/publish/${phase}`);
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
  const response = await apiClient.post(`/events/${eventCode}/unpublish/${phase}`);
  return response.data;
}

/**
 * Get publishing status including validation for all phases
 * Story BAT-11: Used by EventPublishingTab to display real validation data
 *
 * @param eventCode - Event code (e.g., "BATbern142")
 * @returns Publishing status with validation for all phases
 */
async function getPublishingStatus(eventCode: string): Promise<PublishingStatusResponse> {
  const response = await apiClient.get(`/events/${eventCode}/publish/status`);
  return response.data;
}

/**
 * Get publishing preview for a phase
 *
 * @param eventCode - Event code
 * @param phase - Phase to preview
 * @returns Preview with content and validation status
 */
async function getPublishPreview(
  eventCode: string,
  phase: PublishingPhase
): Promise<PublishPreviewResponse> {
  const response = await apiClient.get(`/events/${eventCode}/publish/${phase}/preview`);
  return response.data;
}

/**
 * Schedule auto-publish for a phase
 *
 * @param eventCode - Event code
 * @param _phase - Phase to auto-publish (reserved for future use)
 * @param options - Schedule options (scheduledDate, notifySubscribers)
 * @returns Auto-publish schedule details with AWS EventBridge rule ARN
 */
async function scheduleAutoPublish(
  eventCode: string,
  _phase: PublishingPhase,
  options: AutoPublishScheduleRequest
): Promise<AutoPublishScheduleResponse> {
  const response = await apiClient.post(`/events/${eventCode}/publish/schedule`, options);
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
  // TODO: Backend will use phase parameter when implementing phase-specific schedules
  console.debug('cancelAutoPublish called for phase:', phase);
  const response = await apiClient.delete(`/events/${eventCode}/publish/schedule`);
  return response.data;
}

/**
 * Publishing Service
 * Exported as singleton for consistent usage across components
 */
export const publishingService = {
  publishPhase,
  unpublishPhase,
  getPublishingStatus,
  getPublishPreview,
  scheduleAutoPublish,
  cancelAutoPublish,
};

// Also export individual functions for easier testing/mocking
export {
  publishPhase,
  unpublishPhase,
  getPublishingStatus,
  getPublishPreview,
  scheduleAutoPublish,
  cancelAutoPublish,
};
