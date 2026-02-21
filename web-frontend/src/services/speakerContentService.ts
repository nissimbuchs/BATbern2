/**
 * Speaker Content Service (Story 5.5)
 *
 * HTTP client for Speaker Content Submission APIs
 * Features:
 * - Submit speaker presentation content (title, abstract)
 * - Get speaker content details
 * - JWT authentication via interceptors
 * - Error handling with correlation IDs
 */

import apiClient from '@/services/api/apiClient';
import type { SpeakerPoolEntry } from '@/types/speakerPool.types';

// API base path
const EVENTS_API_PATH = '/events';

/**
 * Submit Content Request DTO
 */
export interface SubmitContentRequest {
  username: string;
  presentationTitle: string;
  presentationAbstract: string;
}

/**
 * Speaker Content Response DTO
 */
export interface SpeakerContentResponse {
  poolId: string;
  sessionId?: string;
  status: string;
  presentationTitle?: string;
  presentationAbstract?: string;
  username?: string;
  warning?: string;
  // Material fields (AC7)
  hasMaterial?: boolean;
  materialUrl?: string;
  materialFileName?: string;
}

/**
 * Quality Review Request DTO
 */
export interface ReviewRequest {
  action: 'APPROVE' | 'REJECT';
  feedback?: string;
}

/**
 * Speaker Content Service
 *
 * Handles all HTTP requests for speaker content submission
 */
class SpeakerContentService {
  /**
   * Submit speaker content (AC6-10)
   *
   * Creates session + session_users junction and updates speaker_pool.status to CONTENT_SUBMITTED
   *
   * @param eventCode Event code (e.g., "BATbern56")
   * @param poolId Speaker pool UUID
   * @param request Content submission data (username, title, abstract)
   * @returns Created content details with session ID and updated status
   * @throws Error if validation fails (400), speaker not found (404), or invalid state (422)
   */
  async submitContent(
    eventCode: string,
    poolId: string,
    request: SubmitContentRequest
  ): Promise<SpeakerContentResponse> {
    const response = await apiClient.post<SpeakerContentResponse>(
      `${EVENTS_API_PATH}/${eventCode}/speakers/${poolId}/content`,
      request
    );

    return response.data;
  }

  /**
   * Get speaker content (AC34)
   *
   * Retrieves presentation details for a speaker
   * Handles orphaned session FK (session deleted) gracefully
   *
   * @param eventCode Event code (e.g., "BATbern56")
   * @param poolId Speaker pool UUID
   * @returns Content details or warning if content lost
   * @throws Error if speaker not found (404) or unauthorized (401, 403)
   */
  async getSpeakerContent(eventCode: string, poolId: string): Promise<SpeakerContentResponse> {
    const response = await apiClient.get<SpeakerContentResponse>(
      `${EVENTS_API_PATH}/${eventCode}/speakers/${poolId}/content`
    );

    return response.data;
  }

  /**
   * Get review queue (AC11)
   *
   * Retrieves all speakers pending quality review (status=CONTENT_SUBMITTED)
   *
   * @param eventCode Event code (e.g., "BATbern56")
   * @returns List of speakers pending review
   * @throws Error if unauthorized (401, 403)
   */
  async getReviewQueue(eventCode: string): Promise<SpeakerPoolEntry[]> {
    const response = await apiClient.get<SpeakerPoolEntry[]>(
      `${EVENTS_API_PATH}/${eventCode}/speakers/review-queue`
    );

    return response.data;
  }

  /**
   * Review content (approve or reject) (AC13-14)
   *
   * Approves or rejects speaker content quality review
   *
   * @param eventCode Event code (e.g., "BATbern56")
   * @param poolId Speaker pool UUID
   * @param request Review request (approve/reject with optional feedback)
   * @returns void (204 No Content)
   * @throws Error if validation fails (400), speaker not found (404), or unauthorized (401, 403)
   */
  async reviewContent(eventCode: string, poolId: string, request: ReviewRequest): Promise<void> {
    await apiClient.post(`${EVENTS_API_PATH}/${eventCode}/speakers/${poolId}/review`, request);
  }
}

// Export singleton instance
export const speakerContentService = new SpeakerContentService();

// Export class for testing
export default SpeakerContentService;
