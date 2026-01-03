/**
 * Event API Service
 * Story 3.2 (BAT-13): Type-safe API client wrappers for event endpoints
 *
 * Endpoints:
 * - POST /events/batch_registrations - Batch register participants for events
 */

import apiClient from './apiClient';
import type {
  BatchRegistrationRequest,
  BatchRegistrationResponse,
} from '@/types/participantImport.types';

/**
 * Batch register a participant for multiple events
 * @param request - Participant and event registration details
 * @returns Batch registration result with success/failure details
 */
export const batchRegisterParticipant = async (
  request: BatchRegistrationRequest
): Promise<BatchRegistrationResponse> => {
  const response = await apiClient.post<BatchRegistrationResponse>(
    '/events/batch_registrations',
    request
  );
  return response.data;
};
