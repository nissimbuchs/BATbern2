/**
 * Session API Client (Story BAT-17)
 *
 * HTTP client for Session management APIs in Event Management Service
 * Features:
 * - Update session details (title, description, duration)
 */

import apiClient from '@/services/api/apiClient';
import { AxiosError } from 'axios';
import type { Session } from '@/types/event.types';

// API base path for session endpoints
const SESSION_API_PATH = '/events';

export interface SessionUpdateRequest {
  title?: string;
  description?: string;
  durationMinutes?: number;
}

/**
 * Session API Client Class
 *
 * Handles all HTTP requests to the Session APIs
 */
class SessionApiClient {
  /**
   * Update session details (title, description, duration)
   *
   * PATCH /api/v1/events/{eventCode}/sessions/{sessionSlug}
   *
   * @param eventCode - Event code (e.g., "BATbern142")
   * @param sessionSlug - Session slug identifier
   * @param updates - Session update request
   * @returns Updated session
   */
  async updateSession(
    eventCode: string,
    sessionSlug: string,
    updates: SessionUpdateRequest
  ): Promise<Session> {
    try {
      const response = await apiClient.patch<Session>(
        `${SESSION_API_PATH}/${eventCode}/sessions/${sessionSlug}`,
        updates
      );
      return response.data;
    } catch (error) {
      // Re-throw AxiosError to preserve 401/403 auth errors
      if (
        error instanceof AxiosError &&
        (error.response?.status === 401 || error.response?.status === 403)
      ) {
        throw error;
      }
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

      // Handle specific error cases
      if (error.response?.status === 404) {
        if (message.includes('Event')) {
          return new Error('Event not found');
        }
        if (message.includes('Session')) {
          return new Error('Session not found');
        }
      }

      // Include correlation ID if available
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
