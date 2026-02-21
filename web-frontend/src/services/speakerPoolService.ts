/**
 * Speaker Pool Service (Story 5.2)
 *
 * HTTP client for Speaker Pool Management APIs
 * Features:
 * - Add speaker to event pool (POST /api/v1/events/{eventCode}/speakers/pool)
 * - Get speaker pool for event (GET /api/v1/events/{eventCode}/speakers/pool)
 * - JWT authentication via interceptors
 * - Error handling with correlation IDs
 */

import apiClient from '@/services/api/apiClient';
import type {
  SpeakerPoolEntry,
  AddSpeakerToPoolRequest,
  SpeakerPoolResponse,
  SendInvitationRequest,
  SendInvitationResponse,
  SendReminderRequest,
  SendReminderResponse,
} from '@/types/speakerPool.types';

// API base path
const EVENTS_API_PATH = '/events';

/**
 * Speaker Pool Service
 *
 * Handles all HTTP requests for speaker pool management
 */
class SpeakerPoolService {
  /**
   * Add speaker to event speaker pool (AC9-12)
   *
   * @param eventCode Event code (e.g., "BATbern56")
   * @param request Speaker data (name, company, expertise, assigned organizer, notes)
   * @returns Created speaker pool entry
   * @throws Error if event not found, validation fails, or unauthorized
   */
  async addSpeakerToPool(
    eventCode: string,
    request: AddSpeakerToPoolRequest
  ): Promise<SpeakerPoolResponse> {
    const response = await apiClient.post<SpeakerPoolResponse>(
      `${EVENTS_API_PATH}/${eventCode}/speakers/pool`,
      request
    );

    return response.data;
  }

  /**
   * Get speaker pool for event
   *
   * @param eventCode Event code (e.g., "BATbern56")
   * @returns List of speaker pool entries
   * @throws Error if event not found or unauthorized
   */
  async getSpeakerPool(eventCode: string): Promise<SpeakerPoolEntry[]> {
    const response = await apiClient.get<SpeakerPoolEntry[]>(
      `${EVENTS_API_PATH}/${eventCode}/speakers/pool`
    );

    return response.data;
  }

  /**
   * Delete speaker from event speaker pool
   *
   * @param eventCode Event code (e.g., "BATbern56")
   * @param speakerId Speaker UUID
   * @throws Error if event or speaker not found, or unauthorized
   */
  async deleteSpeakerFromPool(eventCode: string, speakerId: string): Promise<void> {
    await apiClient.delete(`${EVENTS_API_PATH}/${eventCode}/speakers/pool/${speakerId}`);
  }

  /**
   * Send invitation to speaker (Story 6.1c)
   *
   * Transitions speaker from IDENTIFIED to INVITED state and sends invitation email
   * with magic link for speaker portal access.
   *
   * @param eventCode Event code (e.g., "BATbern56")
   * @param username Speaker UUID or username
   * @param options Optional invitation options (deadline, personal message)
   * @returns Response with magic link token, new state, and email sent to
   * @throws Error if speaker not found, already invited, or in invalid state
   */
  async sendInvitation(
    eventCode: string,
    username: string,
    options?: SendInvitationRequest
  ): Promise<SendInvitationResponse> {
    const response = await apiClient.post<SendInvitationResponse>(
      `${EVENTS_API_PATH}/${eventCode}/speakers/${username}/send-invitation`,
      options || {}
    );

    return response.data;
  }

  /**
   * Send reminder to speaker (Story 6.5)
   *
   * Sends a deadline reminder email to an invited or accepted speaker.
   *
   * @param eventCode Event code (e.g., "BATbern56")
   * @param speakerPoolId Speaker pool entry UUID
   * @param request Reminder type and optional tier override
   * @returns Response with tier used and email sent to
   * @throws Error if speaker not found, no email, or reminder cannot be sent
   */
  async sendReminder(
    eventCode: string,
    speakerPoolId: string,
    request: SendReminderRequest
  ): Promise<SendReminderResponse> {
    const response = await apiClient.post<SendReminderResponse>(
      `${EVENTS_API_PATH}/${eventCode}/speaker-pool/${speakerPoolId}/send-reminder`,
      request
    );

    return response.data;
  }
}

// Export singleton instance
export const speakerPoolService = new SpeakerPoolService();

// Export class for testing
export default SpeakerPoolService;
