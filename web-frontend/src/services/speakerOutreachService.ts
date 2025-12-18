/**
 * Speaker Outreach Service (Story 5.3)
 *
 * HTTP client for Speaker Outreach Tracking APIs
 * Features:
 * - Record speaker outreach attempt (POST /api/v1/events/{eventCode}/speakers/{speakerId}/outreach)
 * - Get speaker outreach history (GET /api/v1/events/{eventCode}/speakers/{speakerId}/outreach)
 * - Bulk record outreach for multiple speakers
 * - JWT authentication via interceptors
 * - Error handling with correlation IDs
 */

import apiClient from '@/services/api/apiClient';
import type {
  OutreachHistory,
  RecordOutreachRequest,
  OutreachHistoryResponse,
} from '@/types/speakerOutreach.types';

// API base path
const EVENTS_API_PATH = '/events';

/**
 * Speaker Outreach Service
 *
 * Handles all HTTP requests for speaker outreach tracking
 */
class SpeakerOutreachService {
  /**
   * Record speaker outreach attempt (AC2-3)
   *
   * @param eventCode Event code (e.g., "BATbern56")
   * @param speakerId Speaker UUID
   * @param request Outreach data (contact method, date, notes)
   * @returns Created outreach history entry
   * @throws Error if speaker not found, invalid state, or unauthorized
   */
  async recordOutreach(
    eventCode: string,
    speakerId: string,
    request: RecordOutreachRequest
  ): Promise<OutreachHistoryResponse> {
    const response = await apiClient.post<OutreachHistoryResponse>(
      `${EVENTS_API_PATH}/${eventCode}/speakers/${speakerId}/outreach`,
      request
    );

    return response.data;
  }

  /**
   * Get speaker outreach history (AC4)
   *
   * @param eventCode Event code (e.g., "BATbern56")
   * @param speakerId Speaker UUID
   * @returns Chronological list of outreach attempts (most recent first)
   * @throws Error if speaker not found or unauthorized
   */
  async getOutreachHistory(eventCode: string, speakerId: string): Promise<OutreachHistory[]> {
    const response = await apiClient.get<OutreachHistory[]>(
      `${EVENTS_API_PATH}/${eventCode}/speakers/${speakerId}/outreach`
    );

    return response.data;
  }

  /**
   * Bulk record outreach for multiple speakers (AC6)
   *
   * @param eventCode Event code (e.g., "BATbern56")
   * @param speakerIds Array of speaker UUIDs
   * @param request Outreach data (same contact method/date/notes for all)
   * @throws Error if any speaker not found or in invalid state
   */
  async bulkRecordOutreach(
    eventCode: string,
    speakerIds: string[],
    request: RecordOutreachRequest
  ): Promise<void> {
    await Promise.all(
      speakerIds.map((speakerId) => this.recordOutreach(eventCode, speakerId, request))
    );
  }
}

// Export singleton instance
export const speakerOutreachService = new SpeakerOutreachService();

// Export class for testing
export default SpeakerOutreachService;
