/**
 * Publishing Service
 * Story 5.7 - Task 5b: Progressive Publishing Frontend
 *
 * API client for progressive publishing operations:
 * - Publish/unpublish phases (topic → speakers → agenda)
 * - Publishing status with the validation of every phase
 * - Auto-publish scheduling
 *
 * Request and response types are the generated ones of events-core-api (17.0c2).
 */

import apiClient from '@/services/api/apiClient';
import type {
  PublishingPhase,
  PublishPhaseResponse,
  UnpublishPhaseResponse,
  PublishingStatusResponse,
  AutoPublishScheduleRequest,
  AutoPublishScheduleResponse,
} from '@/types/event.types';

/**
 * Publish a phase of event content
 *
 * @param eventCode - Event code (e.g., "BATbern142")
 * @param phase - Publishing phase ('topic' | 'speakers' | 'agenda')
 * @returns The published phase and whether the CDN was invalidated
 * @throws 422 if content validation fails (`PublishValidationError`)
 * @throws 401 if not authenticated
 * @throws 403 if insufficient permissions
 */
async function publishPhase(
  eventCode: string,
  phase: PublishingPhase
): Promise<PublishPhaseResponse> {
  const response = await apiClient.post<PublishPhaseResponse>(
    `/events/${eventCode}/publish/${phase}`
  );
  return response.data;
}

/**
 * Unpublish a phase (revert to previous phase)
 *
 * @param eventCode - Event code
 * @param phase - Phase to unpublish
 * @returns The unpublished phase and whether the CDN was invalidated
 */
async function unpublishPhase(
  eventCode: string,
  phase: PublishingPhase
): Promise<UnpublishPhaseResponse> {
  const response = await apiClient.post<UnpublishPhaseResponse>(
    `/events/${eventCode}/unpublish/${phase}`
  );
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
  const response = await apiClient.get<PublishingStatusResponse>(
    `/events/${eventCode}/publish/status`
  );
  return response.data;
}

/**
 * Configure the auto-publish schedule of an event: whether and how many days before the event
 * the speakers (phase 2) and agenda (phase 3) are published. It used to send
 * `{ scheduledDate }`, a body EMS does not read (found by the MSW harness, 17.0c2).
 *
 * @param eventCode - Event code
 * @param request - `{ phase2Enabled, phase2DaysBeforeEvent, phase3Enabled, phase3DaysBeforeEvent }`
 * @returns The configured schedule with its trigger dates
 */
async function scheduleAutoPublish(
  eventCode: string,
  request: AutoPublishScheduleRequest
): Promise<AutoPublishScheduleResponse> {
  const response = await apiClient.post<AutoPublishScheduleResponse>(
    `/events/${eventCode}/publish/schedule`,
    request
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
  getPublishingStatus,
  scheduleAutoPublish,
};

export { publishPhase, unpublishPhase, getPublishingStatus, scheduleAutoPublish };
