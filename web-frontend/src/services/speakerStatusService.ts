/**
 * Speaker Status Service (Story 5.4)
 *
 * HTTP client for Speaker Status Management APIs
 * Features:
 * - Update speaker status with workflow validation
 * - Get speaker status history with audit trail
 * - Get status dashboard summary with acceptance metrics
 * - JWT authentication via interceptors
 * - Error handling with correlation IDs
 */

import apiClient from '@/services/api/apiClient';
import type { components } from '@/types/generated/speakers-api.types';

// Type aliases for generated types
type UpdateStatusRequest = components['schemas']['UpdateStatusRequest'];
type SpeakerStatusResponse = components['schemas']['SpeakerStatusResponse'];
type StatusHistoryItem = components['schemas']['StatusHistoryItem'];
type StatusSummaryResponse = components['schemas']['StatusSummaryResponse'];
type SpeakerWorkflowState = components['schemas']['SpeakerWorkflowState'];

// API base path
const EVENTS_API_PATH = '/events';

/**
 * Speaker Status Service
 *
 * Handles all HTTP requests for speaker status management
 */
class SpeakerStatusService {
  /**
   * Update speaker status (AC1-2, AC10-12)
   *
   * Valid state transitions:
   * - OPEN → CONTACTED
   * - CONTACTED → READY, DECLINED
   * - READY → ACCEPTED, DECLINED
   * - ACCEPTED → SLOT_ASSIGNED (cannot go back to DECLINED)
   * - DECLINED is terminal (cannot transition out)
   *
   * @param eventCode Event code (e.g., "BATbern56")
   * @param speakerId Speaker pool UUID
   * @param newStatus New workflow state
   * @param reason Optional reason for status change (max 2000 characters)
   * @returns Updated speaker status with change details
   * @throws Error if validation fails (400), speaker not found (404), or invalid transition (422)
   */
  async updateStatus(
    eventCode: string,
    speakerId: string,
    newStatus: SpeakerWorkflowState,
    reason?: string
  ): Promise<SpeakerStatusResponse> {
    const request: UpdateStatusRequest = {
      newStatus,
      ...(reason && { reason }),
    };

    const response = await apiClient.put<SpeakerStatusResponse>(
      `${EVENTS_API_PATH}/${eventCode}/speakers/${speakerId}/status`,
      request
    );

    return response.data;
  }

  /**
   * Get speaker status history (AC3-4, AC15)
   *
   * Retrieves complete status change history with timestamps, organizers, and reasons
   *
   * @param eventCode Event code (e.g., "BATbern56")
   * @param speakerId Speaker pool UUID
   * @returns Array of status history items ordered by changedAt (newest first)
   * @throws Error if speaker not found (404) or unauthorized (401, 403)
   */
  async getStatusHistory(eventCode: string, speakerId: string): Promise<StatusHistoryItem[]> {
    const response = await apiClient.get<StatusHistoryItem[]>(
      `${EVENTS_API_PATH}/${eventCode}/speakers/${speakerId}/status/history`
    );

    return response.data;
  }

  /**
   * Get status dashboard summary (AC5-6, AC13)
   *
   * Returns aggregated speaker status metrics including:
   * - Status counts by workflow state
   * - Acceptance rate percentage
   * - Threshold status (min/max slots)
   * - Overflow detection
   *
   * @param eventCode Event code (e.g., "BATbern56")
   * @returns Status summary with counts, rates, and threshold indicators
   * @throws Error if event not found (404) or unauthorized (401, 403)
   */
  async getStatusSummary(eventCode: string): Promise<StatusSummaryResponse> {
    const response = await apiClient.get<StatusSummaryResponse>(
      `${EVENTS_API_PATH}/${eventCode}/speakers/status-summary`
    );

    return response.data;
  }
}

// Export singleton instance
export const speakerStatusService = new SpeakerStatusService();

// Export class for testing
export default SpeakerStatusService;
