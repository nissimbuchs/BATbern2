/**
 * Publishing Service
 * Story 5.7 - Task 5b: Progressive Publishing Frontend
 *
 * API client for progressive publishing operations (Story 4.9b, one transition idiom):
 * - Publish/unpublish phases (topic → speakers → agenda): `POST /events/{code}/phases/{phase}/…`
 * - The publishing resource, `GET`/`PATCH /events/{code}/publishing`: status, the validation of
 *   every phase and the auto-publish schedule
 *
 * Request and response types are the generated ones of events-core-api (17.0c2).
 */

import apiClient from '@/services/api/apiClient';
import type {
  PublishingPhase,
  PublishPhaseResponse,
  UnpublishPhaseResponse,
  PublishingStatusResponse,
  PatchPublishingRequest,
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
    `/events/${eventCode}/phases/${phase}/publish`
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
    `/events/${eventCode}/phases/${phase}/unpublish`
  );
  return response.data;
}

/**
 * The publishing resource: published phases, the validation of every phase and the auto-publish
 * schedule (defaults when none is stored).
 *
 * @param eventCode - Event code (e.g., "BATbern142")
 */
async function getPublishing(eventCode: string): Promise<PublishingStatusResponse> {
  const response = await apiClient.get<PublishingStatusResponse>(`/events/${eventCode}/publishing`);
  return response.data;
}

/**
 * Set the auto-publish schedule: whether and how many days before the event the speakers
 * (phase 2) and the agenda (phase 3) are published by the daily job. Partial: only the fields
 * sent change.
 *
 * @param eventCode - Event code
 * @param request - any of `{ phase2Enabled, phase2DaysBeforeEvent, phase3Enabled, phase3DaysBeforeEvent }`
 * @returns The whole publishing resource with the stored schedule and its trigger dates
 */
async function patchPublishing(
  eventCode: string,
  request: PatchPublishingRequest
): Promise<PublishingStatusResponse> {
  const response = await apiClient.patch<PublishingStatusResponse>(
    `/events/${eventCode}/publishing`,
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
  getPublishing,
  patchPublishing,
};

export { publishPhase, unpublishPhase, getPublishing, patchPublishing };
