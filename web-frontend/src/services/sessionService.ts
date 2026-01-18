/**
 * Session API Client
 *
 * HTTP client for Session Management APIs in Event Management Service
 * Features:
 * - JWT authentication via interceptors
 * - Error handling with correlation IDs
 * - Batch import support for historical sessions
 */

import apiClient from '@/services/api/apiClient';
import { AxiosError } from 'axios';
import type {
  BatchImportSessionRequest,
  SessionBatchImportResult,
} from '@/types/sessionImport.types';

// API base path for session endpoints
const SESSION_API_PATH = '/events';

/**
 * Session API Client Class
 *
 * Handles all HTTP requests to the Session Management APIs
 */
class SessionApiClient {
  /**
   * Batch import sessions for a specific event
   *
   * POST /api/v1/events/{eventCode}/sessions/batch-import
   *
   * @param eventCode - Event code (e.g., "BATbern142")
   * @param sessions - Array of session import requests
   * @returns Batch import result with success/skip/fail counts
   */
  async batchImportSessions(
    eventCode: string,
    sessions: BatchImportSessionRequest[]
  ): Promise<SessionBatchImportResult> {
    try {
      // Use extended timeout for batch import (120s) to handle large PDF downloads
      const response = await apiClient.post<SessionBatchImportResult>(
        `${SESSION_API_PATH}/${eventCode}/sessions/batch-import`,
        sessions,
        {
          timeout: 120000, // 2 minutes - needed for downloading large PDFs from CDN
        }
      );
      return response.data;
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Update session details (title, description, duration)
   *
   * PATCH /api/v1/events/{eventCode}/sessions/{sessionSlug}
   *
   * @param eventCode - Event code (e.g., "BATbern142")
   * @param sessionSlug - Session slug identifier
   * @param updates - Session update data (title, description, durationMinutes)
   * @returns Updated session data
   */
  async updateSession(
    eventCode: string,
    sessionSlug: string,
    updates: {
      title?: string;
      description?: string;
      durationMinutes?: number;
    }
  ): Promise<void> {
    try {
      await apiClient.patch(`${SESSION_API_PATH}/${eventCode}/sessions/${sessionSlug}`, updates);
    } catch (error) {
      throw this.transformError(error);
    }
  }

  /**
   * Transform Axios errors into user-friendly error messages
   */
  private transformError(error: unknown): Error {
    if (error instanceof AxiosError) {
      // Extract error message from response
      const message =
        error.response?.data?.message ||
        error.response?.data?.error ||
        error.message ||
        'An unexpected error occurred';

      // Include correlation ID if available (Story 1.9)
      const correlationId = error.response?.headers?.['x-correlation-id'];
      const errorMessage = correlationId
        ? `${message} (Correlation ID: ${correlationId})`
        : message;

      return new Error(errorMessage);
    }

    return error instanceof Error ? error : new Error('An unexpected error occurred');
  }
}

// Export singleton instance
export const sessionApiClient = new SessionApiClient();

// Export class for testing
export { SessionApiClient };
